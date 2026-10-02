// Testes da camada PROFESSIONAL: varredura, odômetro e relatório.
//
// Sem dependência de teste: o Node 22 remove os tipos do TypeScript sozinho, e
// `scripts/ts-resolve.mjs` (registrado por --import) resolve os imports sem extensão e o
// package.json que `src/lib/version.ts` importa.
//
// O teste mais importante deste arquivo não verifica comportamento: é o que confere que TODA
// chave citada em `pro/odometer.ts` existe no dump de 818. Uma tela de odômetro que cita uma
// chave inventada é pior do que uma tela vazia, e essa é a checagem que impede isso de passar.

let falhas = 0
let total = 0

function ok(cond, nome) {
  total += 1
  if (cond) return
  falhas += 1
  console.error(`  FALHOU: ${nome}`)
}

function eq(actual, expected, nome) {
  total += 1
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) return
  falhas += 1
  console.error(`  FALHOU: ${nome}\n    esperado: ${e}\n    obtido:   ${a}`)
}

// Os módulos de estado gravam em `localStorage`; o relatório não, mas importa tipos deles.
const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => void store.set(k, String(v)),
  removeItem: (k) => void store.delete(k),
  clear: () => store.clear(),
}

const { runScan, coveragePct, NOT_COVERED } = await import('../src/pro/scan.ts')
const { analyzeOdometer, ODOMETER_KEYS, ODOMETER_UNIT_KEY, FINDING_TEXT } = await import('../src/pro/odometer.ts')
const { buildReport, reportToHtml, reportToJson, MISSING } = await import('../src/lib/report.ts')
const { CONTROL_SERVICE, CONTROL_SERVICE_BY_KEY } = await import('../src/data/knowledge.ts')

const NOW = 1_757_000_000_000

// ------------------------------------------------------------------ procedência das chaves
console.log('procedência das chaves citadas pela camada PRO')
{
  for (const def of ODOMETER_KEYS) {
    ok(CONTROL_SERVICE_BY_KEY.has(def.key), `${def.key} existe no dump de ${CONTROL_SERVICE.meta.count} chaves`)
  }
  ok(CONTROL_SERVICE_BY_KEY.has(ODOMETER_UNIT_KEY), `${ODOMETER_UNIT_KEY} existe no dump`)
  ok(ODOMETER_KEYS.filter((k) => k.role === 'total').length >= 2, 'há ao menos duas chaves de total para comparar')
  ok(new Set(ODOMETER_KEYS.map((k) => k.key)).size === ODOMETER_KEYS.length, 'nenhuma chave repetida')
}

// ------------------------------------------------------------------ varredura
console.log('varredura')
{
  const conhecida1 = CONTROL_SERVICE.keys[0].key
  const conhecida2 = CONTROL_SERVICE.keys[1].key
  const values = {
    [conhecida1]: '1',
    [conhecida2]: '',
    'car.inventada.pelo_teste': '7',
  }
  const r = runScan({ values, source: 'live', carIp: '10.0.0.5', vin: 'ABC', now: NOW })

  eq(r.answered, 3, 'conta todas as chaves respondidas')
  eq(r.knownAnswered, 2, 'conta só as conhecidas como cobertura')
  eq(r.unknownAnswered, ['car.inventada.pelo_teste'], 'chave fora do dump é isolada')
  eq(r.emptyValues, 1, 'chave que respondeu vazio é contada à parte')
  eq(r.knownTotal, CONTROL_SERVICE.meta.count, 'o total conhecido vem da base, não de um literal')
  eq(r.knownSilent.length, CONTROL_SERVICE.meta.count - 2, 'as demais chaves do dump ficam como sem resposta')
  ok(!r.knownSilent.includes(conhecida1), 'chave respondida não aparece como silenciosa')
  eq(r.ts, NOW, 'a data é injetável para o teste')
  eq(r.vin, 'ABC', 'o VIN acompanha o resultado')

  const ns = r.namespaces.find((n) => n.namespace === 'car.inventada')
  eq(ns?.unknown, 1, 'o namespace da chave desconhecida aparece com contagem própria')
  eq(ns?.known, 0, 'e com zero chaves conhecidas')

  ok(r.namespaces.length >= Object.keys(CONTROL_SERVICE.meta.namespaces).length,
    'todo namespace do dump aparece, inclusive os que não responderam nada')

  const vazio = runScan({ values: {}, source: 'demo', carIp: '', vin: null, now: NOW })
  eq(vazio.answered, 0, 'leitura vazia não inventa cobertura')
  eq(coveragePct(vazio), 0, 'cobertura de leitura vazia é 0%')
  eq(vazio.unknownAnswered, [], 'leitura vazia não produz descoberta')

  ok(NOT_COVERED.length >= 4, 'a lista do que a varredura não cobre não está vazia')
  ok(NOT_COVERED.every((n) => n.what && n.why), 'toda limitação declara a razão')
}

// ------------------------------------------------------------------ odômetro
console.log('consistência de odômetro')
{
  const iguais = analyzeOdometer({
    'car.basic.total_odometer': '12345',
    'car.ev_info.total_odometer': '12345',
  })
  eq(iguais.finding, 'coincidem', 'dois totais iguais coincidem')
  eq(iguais.spread, 0, 'diferença zero')
  eq(iguais.comparable.length, 2, 'ambos entram na comparação')

  const diferentes = analyzeOdometer({
    'car.basic.total_odometer': '12345',
    'car.ev_info.total_odometer': '12000',
  })
  eq(diferentes.finding, 'divergem', 'totais diferentes divergem')
  eq(diferentes.spread, 345, 'a diferença é a maior distância entre os valores')

  const um = analyzeOdometer({ 'car.basic.total_odometer': '12345' })
  eq(um.finding, 'insuficiente', 'um único total não permite comparação')
  eq(um.spread, null, 'sem comparação, sem diferença')

  const nenhum = analyzeOdometer({})
  eq(nenhum.finding, 'insuficiente', 'leitura vazia não gera resultado')
  eq(nenhum.collected, 0, 'nada coletado')
  ok(nenhum.readings.every((r) => r.value === null), 'toda medida sai como não coletada')
  ok(nenhum.readings.every((r) => r.inDump), 'toda medida citada existe no dump')

  const vazio = analyzeOdometer({ 'car.basic.total_odometer': '', 'car.ev_info.total_odometer': '1' })
  eq(vazio.comparable.length, 1, 'string vazia não é valor')
  eq(vazio.finding, 'insuficiente', 'e por isso não há comparação')

  const virgula = analyzeOdometer({
    'car.basic.total_odometer': '1234,5',
    'car.ev_info.total_odometer': '1234.5',
  })
  eq(virgula.finding, 'coincidem', 'vírgula decimal é lida como número')

  const texto = analyzeOdometer({
    'car.basic.total_odometer': 'N/A',
    'car.ev_info.total_odometer': '10',
  })
  eq(texto.comparable.length, 1, 'valor não numérico fica fora da comparação')
  ok(texto.readings.find((r) => r.key === 'car.basic.total_odometer').value === 'N/A',
    'mas o valor cru continua visível')

  const unidade = analyzeOdometer({ [ODOMETER_UNIT_KEY]: '0' })
  eq(unidade.unitRaw, '0', 'a unidade é transcrita crua')

  // O texto do resultado nunca pode acusar ninguém.
  //
  // "adulteração" não é palavra proibida: a frase mais importante da tela é justamente a que diz
  // que isto NÃO é uma verificação de adulteração. O que o teste exige é que toda ocorrência
  // esteja negada — e que as palavras que só servem para acusar não apareçam de jeito nenhum.
  for (const [nome, t] of Object.entries(FINDING_TEXT)) {
    const inteiro = `${t.title} ${t.body}`.toLowerCase()
    ok(!/fraude|suspeit|golpe|manipula(ç|c)|adulterado/.test(inteiro),
      `o texto de "${nome}" não usa vocabulário de acusação`)
    for (const m of inteiro.matchAll(/adultera\w*/g)) {
      const antes = inteiro.slice(Math.max(0, m.index - 60), m.index)
      ok(/\bnão\b/.test(antes), `em "${nome}", toda menção a adulteração está negada`)
    }
  }
}

// ------------------------------------------------------------------ relatório
console.log('relatório')
const baseInput = {
  values: {
    'car.basic.vin_code': 'LDG00DEM5R0000019',
    'car.configure.product_name': 'VEICULO <TESTE> & "CIA"',
    'car.basic.total_odometer': '12345',
  },
  source: 'live',
  carIp: '10.0.0.5',
  vin: 'LDG00DEM5R0000019',
  vinFields: [
    { label: 'Fabricante', value: null, confidence: 'UNKNOWN' },
    { label: 'Dígito verificador', value: 'confere', confidence: 'FACT' },
  ],
  scan: null,
  odometer: analyzeOdometer({ 'car.basic.total_odometer': '12345' }),
  changes: [],
  snapshots: [],
  notes: '',
  technician: '',
  now: NOW,
}
{
  const m = buildReport(baseInput)
  eq(m.generatedAt, NOW, 'a data do relatório é injetável')
  eq(m.vin, 'LDG00DEM5R0000019', 'o VIN entra no modelo')
  eq(m.configuration.length, 1, 'só as chaves car.configure.* respondidas entram na configuração')
  eq(m.configuration[0].key, 'car.configure.product_name', 'e com a chave original')
  ok(m.identity.length > 0, 'a identidade tem grupos')
  ok(m.identity.some((g) => g.rows.some((r) => r.value === null)),
    'campo de identidade não respondido fica null, não vazio')
  ok(m.notCollected.length > 0, 'o relatório carrega a lista do que não contém')
  eq(m.knowledge.controlServiceKeys, CONTROL_SERVICE.meta.count, 'a procedência cita números reais')

  const json = JSON.parse(reportToJson(m))
  eq(json.vin, m.vin, 'o JSON preserva o VIN')
  eq(json.appVersion, m.appVersion, 'o JSON preserva a versão do app')

  const html = reportToHtml(m)
  ok(html.startsWith('<!doctype html>'), 'o HTML é um documento completo')
  ok(html.includes(MISSING), 'campos sem dado saem escritos como não coletado')
  ok(html.includes('&lt;TESTE&gt;'), 'valor com < e > é escapado')
  ok(html.includes('&quot;CIA&quot;'), 'aspas são escapadas')
  ok(!html.includes('<TESTE>'), 'o valor cru não vaza como marcação')

  // Autossuficiência: nenhum recurso externo, nenhum script.
  ok(!/<script/i.test(html), 'o relatório não contém script')
  ok(!/src\s*=\s*["']https?:/i.test(html), 'nenhum recurso remoto')
  ok(!/<link\b/i.test(html), 'nenhuma folha de estilo externa')
  ok(!/@import/i.test(html), 'nenhum @import de CSS')
  ok(html.includes('<style>'), 'o estilo é embutido')

  // Nenhum veredito.
  const texto = html.toLowerCase()
  for (const palavra of ['fraude', 'aprovado', 'reprovado', 'suspeito']) {
    ok(!texto.includes(palavra), `o relatório não emite veredito ("${palavra}")`)
  }

  ok(!html.includes('RELATÓRIO DE DEMONSTRAÇÃO'), 'leitura ao vivo não leva tarja de demonstração')
}
{
  const demo = reportToHtml(buildReport({ ...baseInput, source: 'demo' }))
  ok(demo.includes('RELATÓRIO DE DEMONSTRAÇÃO'), 'modo demo marca o documento na cara')
  ok(demo.includes('nenhum veículo foi lido'), 'e diz que nenhum veículo foi lido')
}
{
  // Um relatório sem nada coletado continua sendo um documento válido — só que vazio e honesto.
  const vazio = buildReport({
    ...baseInput,
    values: {},
    vin: null,
    vinFields: [],
    odometer: analyzeOdometer({}),
  })
  const html = reportToHtml(vazio)
  eq(vazio.configuration.length, 0, 'sem leitura, sem configuração')
  ok(html.includes(MISSING), 'e o documento diz não coletado em vez de ficar em branco')
  ok(html.includes('Não há valores suficientes para comparar'),
    'o odômetro vazio declara insuficiência em vez de somar zero')
}
{
  // Escrita com falha precisa aparecer no documento — é o caso que mais interessa a quem lê.
  const comFalha = buildReport({
    ...baseInput,
    changes: [
      {
        id: 'a', ts: NOW, vin: 'X', carIp: '10.0.0.5', key: 'car.configure.mood_lamp',
        from: null, to: '0', source: 'live', result: 'error', error: 'sem resposta', route: '/x',
      },
    ],
  })
  const html = reportToHtml(comFalha)
  ok(html.includes('falhou'), 'escrita que falhou aparece marcada como falha')
  ok(html.includes('sem resposta'), 'com a mensagem de erro')
  ok(html.includes('nunca reportado'), 'valor anterior desconhecido não vira vazio')
}

{
  // A linha do tempo da partida do Shizuku. O relatório de campo de 2026-09-15 é o caso real que
  // motivou a leitura: BOOT_COMPLETED aos 20,6s e binder só aos 68,3s — 47,8s de espera. Os
  // números abaixo são exatamente os daquele /diag, reescritos no formato desde-o-boot.
  const { parseDiagReport } = await import('../src/pro/diagTrace.ts')

  const campo = parseDiagReport(JSON.stringify({
    uptimeMs: 5119641,
    shizukuPingBinderNow: true,
    bootTimelineSinceBootMs: {
      lockedBootReceiver: 14000,
      bootReceiver: 20572,
      onStartCommand: 67256,
      firstBootstrapAttempt: 20800,
      lastBootstrapAttempt: 67257,
      lastRebootstrap: null,
      lastRestart: 62241,
      shizukuBinderReceived: 68335,
    },
    lastBootstrapStartOutcome: 'SERVER_ALIVE_NO_BINDER',
    lastBootstrapApkArg: '/data/app/~~a==/br.com.opengw.shizuku-b==/base.apk',
    lastBootstrapServerLog: 'algo saiu no shizuku_auto.log',
  }))
  eq(campo.boot.bootReceiver, 20572, 'o BOOT_COMPLETED sai do relatório em ms desde o boot')
  eq(campo.boot.binderAfterBootMs, 47763, 'a espera é a diferença entre o binder e o BOOT_COMPLETED')
  eq(campo.boot.startOutcome, 'SERVER_ALIVE_NO_BINDER', 'o desfecho do start atravessa a leitura')
  ok(campo.boot.apkArg.endsWith('/base.apk'), 'e o --apk= usado no start também')

  // Relatório de uma build antiga, sem a linha do tempo: tudo nulo, e NENHUM zero inventado — a
  // tela esconde o bloco nesse caso, e só pode esconder se a ausência chegar como ausência.
  const antigo = parseDiagReport(JSON.stringify({ uptimeMs: 1000, shizukuPingBinderNow: true }))
  eq(antigo.boot.bootReceiver, null, 'relatório sem linha do tempo não vira 0')
  eq(antigo.boot.binderAfterBootMs, null, 'nem a espera')

  // Só uma das pontas não permite subtrair.
  const meio = parseDiagReport(JSON.stringify({
    bootTimelineSinceBootMs: { shizukuBinderReceived: 30000 },
  }))
  eq(meio.boot.binderAfterBootMs, null, 'binder sem BOOT_COMPLETED registrado não vira espera zero')

  eq(parseDiagReport('nada disso é json').boot.bootReceiver, null, 'relatório quebrado não explode')

  // A partida MORNA: o carro religou sem dar boot, então não há BOOT_COMPLETED nenhum nesta linha
  // do tempo. O que identifica o caso é o app ter encontrado o serviço de pé com o binder morto —
  // sem esse contador, um relatório assim é indistinguível de "nada aconteceu".
  const morna = parseDiagReport(JSON.stringify({
    uptimeMs: 240000,
    shizukuPingBinderNow: true,
    staleBinderOnAlreadyRunningCount: 2,
    bootTimelineSinceBootMs: {
      bootReceiver: null,
      onStartCommand: 31000,
      shizukuBinderReceived: 38000,
      lastStaleBinderOnAlreadyRunningStart: 31200,
    },
  }))
  eq(morna.boot.staleBinderOnAlreadyRunningCount, 2, 'a partida morna se identifica pelo binder morto com serviço de pé')
  eq(morna.boot.lastStaleBinderOnAlreadyRunningStart, 31200, 'e o momento em que isso foi detectado atravessa a leitura')
  eq(morna.boot.bootReceiver, null, 'partida morna não inventa um BOOT_COMPLETED que não houve')
  eq(morna.boot.binderAfterBootMs, null, 'e sem BOOT_COMPLETED não existe "depois do boot" a mostrar')
  eq(campo.boot.staleBinderOnAlreadyRunningCount, null,
    'relatório de build antiga não vira 0 no contador novo — a tela some com o bloco em vez de mentir')
}

console.log(`\n${total} verificações. ${falhas === 0 ? 'Todas passaram.' : `${falhas} falharam.`}`)
process.exit(falhas === 0 ? 0 : 1)
