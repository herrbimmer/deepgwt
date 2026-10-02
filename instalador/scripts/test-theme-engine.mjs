// Testes do motor de temas do cluster.
//
// Cada asserção aqui existe por causa de um defeito concreto que já apareceu neste projeto ou nos
// dois temas escritos à mão. Nenhuma é decorativa — o comentário de cada bloco diz qual era o
// problema.
//
// Sem dependência de teste: o Node 22 remove os tipos do TypeScript sozinho, e
// `scripts/ts-resolve.mjs` (registrado por --import) resolve os imports sem extensão.

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
  if (JSON.stringify(actual) === JSON.stringify(expected)) return
  falhas += 1
  console.error(`  FALHOU: ${nome}\n    esperado: ${JSON.stringify(expected)}\n    obtido:   ${JSON.stringify(actual)}`)
}

const { createTheme, createElement } = await import('../src/theme-editor/model/defaults.ts')
const { emitAppHtml, engineFingerprint } = await import('../src/theme-editor/emit/emitHtml.ts')
const { emitThemeXml, versionFrom } = await import('../src/theme-editor/emit/emitThemeXml.ts')
const { buildPayload } = await import('../src/theme-editor/emit/payload.ts')
const { isLegal, firstBlocker, movedTo, maxLegalScale } = await import('../src/theme-editor/model/collision.ts')
const { THEME_BRIDGE } = await import('../src/data/knowledge.ts')

const posicionar = (el, x, y, size = 'md', scale = 1) => ({
  ...el,
  place: { ...el.place, at: { x, y }, size, scale },
})

function temaBase(nome) {
  const t = createTheme(nome)
  const velocimetro = posicionar(
    createElement({ kind: 'dial', style: 'faceNeedle', key: 'car.basic.vehicle_speed', zone: 'left' }),
    335, 400, 'lg', 1,
  )
  const marcha = posicionar(
    createElement({ kind: 'readout', style: 'value', key: 'car.basic.gear_status', zone: 'topCenter' }),
    760, 45, 'xs', 1,
  )
  t.pages[0].elements = [velocimetro, marcha]
  return t
}

// -------------------------------------------------------------- motor idêntico entre temas
// O motor tem que ser byte a byte igual em todo tema gerado. É o que torna auditável um diff
// entre dois app.html: se o motor aparecer no diff, alguém mexeu nele sem querer.
{
  const a = temaBase('Tema A')
  const b = temaBase('Tema B')
  b.palette.tokens.ink.day = '#00ff00'
  b.pages[0].elements[0].zoom.scale = 1.9

  const htmlA = emitAppHtml(a, { backgroundCss: '#000' })
  const htmlB = emitAppHtml(b, { backgroundCss: '#111' })
  ok(htmlA !== htmlB, 'dois temas diferentes geram HTML diferente')

  const motor = engineFingerprint()
  ok(htmlA.includes(motor.split('\n')[2] ?? ''), 'o HTML contém o motor')
  ok(htmlA.split('OPENGW.boot(')[0].includes('function makeMapper'), 'o motor vem antes do payload')

  const recorte = (html) => html.slice(html.indexOf('<script>'), html.lastIndexOf('<script>'))
  eq(recorte(htmlA), recorte(htmlB), 'os bytes do motor são idênticos nos dois temas')
}

// -------------------------------------------------------------- contrato do bridge no HTML
// Cada um destes já custou um defeito real:
//  - onDataChanged sobrescrito apaga o handler que o host injeta antes do tema carregar;
//  - onCardChanged escondendo no card 0 deixa o tema invisível até mexerem no volante;
//  - sem stub de window.control, o host lança ReferenceError a cada evento, e cada erro vira um
//    salto JNI mais uma escrita em disco no log persistente;
//  - sem transform-box, o número que cresce na animação de zoom voa para fora do painel;
//  - comparar head_light_status com '1' na fé é o que os dois temas de fábrica fazem, e o guia
//    diz que o formato nunca foi confirmado.
{
  const html = emitAppHtml(temaBase('Contrato'), { backgroundCss: '#000' })
  ok(/var prevData = window\.onDataChanged/.test(html), 'onDataChanged é encadeado, não sobrescrito')
  ok(/window\.onCardChanged = function/.test(html), 'onCardChanged é implementado')
  ok(!/onCardChanged[\s\S]{0,200}classList\.(toggle|add)\('hidden'/.test(html), 'onCardChanged NÃO esconde o tema')
  ok(/var prevCtl = window\.control/.test(html), 'window.control é encadeado')
  ok(/setAppDefaultDimensions\(/.test(html), 'o tema informa a janela de app ao carro')
  ok(/function parseBoolish/.test(html), 'a leitura de booleano é defensiva')
  ok(/'1' \|\| s === 'true' \|\| s === 'on'/.test(html), 'parseBoolish aceita as variantes plausíveis')
  ok(/transform-box: fill-box/.test(html), 'as marcações SVG têm transform-box')
  ok(/transform-origin: center/.test(html), 'as marcações SVG têm transform-origin')
  ok(/function pinNeedle/.test(html) && /function rotateNeedle/.test(html), 'o ponteiro gira em vez de ser redesenhado')
  ok(/requestAnimationFrame\(flush\)/.test(html), 'o desenho é coalescido por quadro')
  ok(/setTimeout\(flush, 120\)/.test(html), 'existe a rede de 120 ms sob o requestAnimationFrame')
  ok(/window\.onKeyEvent = function/.test(html), 'as teclas do volante trocam de página')
  ok(/Android\.subscribe\(JSON\.stringify\(P\.subscribeKeys\)\)/.test(html), 'o tema assina as chaves do payload')

  // O motor não pode ter erro de sintaxe: aqui ele é compilado de verdade, sem executar.
  const script = html.slice(html.indexOf('<script>') + 8, html.indexOf('</script>', html.indexOf('<script>')))
  let compilou = true
  try { new Function(script) } catch (e) { compilou = false; console.error('    ' + e.message) }
  ok(compilou, 'o JavaScript do motor compila')
}

// -------------------------------------------------------------- toda chave dentro do contrato
// O defeito que motivou a reconstrução inteira: o editor oferecia 57 fontes de dado das quais o
// carro aceita 10. As outras 47 eram descartadas em silêncio.
{
  const contrato = new Set(THEME_BRIDGE.keys.map((k) => k.key))
  const t = temaBase('Chaves')
  t.pages[0].elements.push(
    posicionar(createElement({ kind: 'indicator', key: 'car.basic.head_light_status' }), 960, 660),
    posicionar(createElement({ kind: 'media', style: 'line' }), 960, 120),
  )
  const p = buildPayload(t)
  const citadas = new Set()
  for (const pg of p.pages) {
    for (const el of pg.elements) {
      if (el.binding?.key) citadas.add(el.binding.key)
      for (const k of el.extraKeys ?? []) citadas.add(k)
    }
  }
  for (const k of p.subscribeKeys) citadas.add(k)
  for (const k of p.controlKeys) citadas.add(k)

  const fora = [...citadas].filter((k) => !contrato.has(k))
  eq(fora, [], 'nenhuma chave do payload está fora do contrato do carro')
  ok(p.subscribeKeys.includes('car.basic.head_light_status'), 'o farol é sempre assinado')
  ok(p.subscribeKeys.includes('warningActive'), 'o aviso do veículo é sempre assinado')
}

// -------------------------------------------------------------- colisão com as zonas do OEM
// A regra que o usuário pediu, e o motivo de ela ser por região de tinta e não por caixa: as três
// zonas caem DENTRO do mostrador esquerdo, e os dois temas de fábrica desenham ali.
{
  const dial = posicionar(
    createElement({ kind: 'dial', style: 'faceNeedle', key: 'car.basic.vehicle_speed' }),
    335, 400, 'lg', 1,
  )
  // A propriedade que torna a clonagem possível: o anel de marcações de um mostrador do tamanho
  // do Cupertino passa POR FORA das três zonas. Testar a caixa em vez da tinta reprovaria isto e
  // proibiria qualquer mostrador redondo grande na metade esquerda.
  const semCentro = { ...dial, center: null }
  ok(isLegal(semCentro), 'o anel do velocímetro do Cupertino, em (335,400) tamanho G, é legal')

  // E a contrapartida honesta: uma leitura grande embaixo do cubo cai na zona da placa de
  // velocidade. O Cupertino de fábrica desenha ali mesmo assim; o editor recusa, que é o que foi
  // pedido. O preset resolve pondo a leitura como elemento próprio, em posição legal.
  eq(firstBlocker(dial)?.zone.id, 'trafficSign', 'a leitura no miolo do mostrador esquerdo é recusada')

  const leitura = posicionar(
    createElement({ kind: 'readout', key: 'car.basic.total_odometer' }),
    280, 345, 'sm', 1,
  )
  const bloqueio = firstBlocker(leitura)
  ok(bloqueio !== null, 'uma leitura sobre o READY é recusada')
  eq(bloqueio?.zone.id, 'ready', 'a zona que recusou é a do READY')

  const livre = movedTo(leitura, { x: 1600, y: 620 })
  ok(isLegal(livre), 'a mesma leitura longe das zonas é aceita')

  const teto = maxLegalScale(semCentro)
  ok(teto >= 0.6 && teto <= 1.4, 'o ajuste fino máximo fica dentro da faixa')
  if (teto < 1.4) {
    ok(!isLegal({ ...semCentro, place: { ...semCentro.place, scale: Math.min(1.4, teto + 0.05) } }),
      'passar do teto deixa ilegal')
  }
}

// -------------------------------------------------------------- theme.xml
// O parser do app casa tags pelo nome, globalmente. Geometria de máscara como elemento filho
// sobrescreveria o <width> do <AppDefaultPosition> e o tema sumiria da lista sem aviso.
{
  const t = temaBase('XML')
  const xml = emitThemeXml(t)
  ok(/<contractVersion>v1\.0<\/contractVersion>/.test(xml), 'declara o contrato v1.0')
  ok(/<mainFile>app\.html<\/mainFile>/.test(xml), 'declara o mainFile, sem depender do padrão index.html')
  ok(/<thumbnail>thumbnail\.png<\/thumbnail>/.test(xml), 'sempre declara miniatura')
  ok(/<minBridgeVersion>1\.0\.0<\/minBridgeVersion>/.test(xml), 'declara a versão mínima do bridge')
  ok(/speedMask enabled="true" x="\d+" y="\d+" width="\d+" height="\d+"/.test(xml), 'máscara sai como atributo')
  ok(!/<nativeMasks>[\s\S]*<width>/.test(xml), 'nenhuma máscara emite <width> como filho')
  ok((xml.match(/<name>/g) || []).length === 1, 'existe exatamente um <name> no arquivo')
  ok((xml.match(/<width>/g) || []).length === 1, 'existe exatamente um <width>, o da janela de app')

  const v1 = versionFrom(1789000000000)
  const v2 = versionFrom(1789000060000)
  ok(v1 !== v2, 'duas edições dão versões diferentes')
  const n = (v) => Number(v.split('.')[2])
  ok(n(v2) > n(v1), 'a versão cresce com o tempo de edição')
  ok(/^\d+\.\d+\.\d+$/.test(v1), 'a versão é só de dígitos e pontos, como o comparador do app espera')
}

// -------------------------------------------------------------- mapeamento não-linear
// O medidor de potência do Stuttgart: zero a um sexto do arco, não no meio.
{
  const { valueToT } = await import('../src/theme-editor/emit/payload.ts')
  const mapa = { kind: 'piecewise', points: [{ v: -100, t: 0 }, { v: 0, t: 1 / 6 }, { v: 100, t: 1 }] }
  ok(Math.abs(valueToT(mapa, -100) - 0) < 1e-6, 'o mínimo cai no início do arco')
  ok(Math.abs(valueToT(mapa, 0) - 1 / 6) < 1e-6, 'o zero cai a um sexto do arco')
  ok(Math.abs(valueToT(mapa, 100) - 1) < 1e-6, 'o máximo cai no fim do arco')
  ok(valueToT(mapa, -50) < valueToT(mapa, 0), 'a regeneração fica antes do zero')

  const dual = { kind: 'dualRange', min: 0, low: 120, high: 260, upAt: 120, downAt: 100, rescaleMs: 650 }
  ok(Math.abs(valueToT(dual, 60) - 0.5) < 1e-6, 'na faixa baixa, metade da escala é metade do arco')
}

// -------------------------------------------------------------- os dois presets de fábrica
// Cupertino Ultra e Stuttgart NTG 5.5 recriados como dados. São o teste mais duro que existe
// aqui: se o modelo não desse conta de expressá-los, a promessa de "clonar e editar" seria falsa.
{
  const { PRESETS, cloneSpec } = await import('../src/theme-editor/presets/index.ts')
  const { backgroundFileName } = await import('../src/theme-editor/emit/emitThemeXml.ts')
  const contrato = new Set(THEME_BRIDGE.keys.map((k) => k.key))

  eq(
    PRESETS.map((p) => p.name),
    ['Cupertino Ultra', 'Stuttgart NTG 5.5', 'Minimalist'],
    'a galeria traz os dois temas de fábrica e o Minimalist',
  )

  for (const preset of PRESETS) {
    const spec = preset.spec
    const elementos = spec.pages.flatMap((p) => p.elements)

    // Nenhum elemento do preset pode cobrir o que o veículo desenha. Se um cobrisse, o editor
    // estaria abrindo um tema que ele próprio se recusa a salvar.
    for (const el of elementos) {
      const b = firstBlocker(el)
      ok(b === null, `${preset.name}: "${el.name}" não invade zona do veículo${b ? ` (bate em ${b.zone.label})` : ''}`)
    }

    // Toda chave citada existe no contrato. A verificação de build já cobre o arquivo-fonte por
    // texto; esta cobre o objeto montado, inclusive o que vier de espalhamento.
    for (const el of elementos) {
      const k = el.binding && el.binding.key
      if (!k) continue
      ok(contrato.has(k), `${preset.name}: "${el.name}" lê ${k}, que está no contrato`)
    }
    for (const el of elementos) {
      if (el.visible.when === 'always') continue
      if (el.visible.when === 'overLimit') continue
      ok(contrato.has(el.visible.key), `${preset.name}: a regra de visibilidade de "${el.name}" usa chave do contrato`)
    }
    // As regras de cor leem do carro como qualquer elemento, e podem citar uma chave que nenhum
    // elemento usa — então elas precisam da mesma régua.
    for (const regra of spec.colorRules) {
      if (!('key' in regra.when)) continue
      ok(contrato.has(regra.when.key), `${preset.name}: a regra de cor "${regra.label}" usa chave do contrato`)
    }

    // O HTML gerado precisa ser JavaScript válido de verdade, não só texto plausível.
    const html = emitAppHtml(spec, { backgroundCss: '#000' })
    const script = html.slice(html.indexOf('<script>') + 8, html.indexOf('</script>'))
    let compilou = true
    try { new Function(script) } catch { compilou = false }
    ok(compilou, `${preset.name}: o motor emitido compila`)
    ok(html.includes('OPENGW.boot({'), `${preset.name}: o payload entra por OPENGW.boot`)
  }

  const specDe = (id) => PRESETS.find((p) => p.spec.id === id).spec
  const cupertino = specDe('preset-cupertino-ultra')
  const stuttgart = specDe('preset-stuttgart-ntg55')
  const minimalist = specDe('preset-minimalist')

  // O velocímetro é o caso que obrigou a colisão a ser por região de tinta: o anel de marcações
  // passa por fora das três zonas OEM, mas a caixa do mostrador engole as três.
  const velocimetro = cupertino.pages[0].elements.find((e) => e.id === 'cup-dial-speed')
  ok(isLegal(velocimetro), 'o velocímetro do Cupertino, sobre as três zonas OEM, é legal')
  // Descer o mesmo mostrador leva o anel de marcações para cima da zona do ESP. Subir NÃO o
  // torna ilegal, e isso não é falha: as três zonas ficam abaixo e ao centro, e o miolo vazio do
  // mostrador passa por cima delas sem escrever nada — que é exatamente a distinção que o
  // bloqueio por região de tinta existe para fazer.
  ok(!isLegal(movedTo(velocimetro, { x: 335, y: 690 })), 'descer o velocímetro até o pé do painel o torna ilegal')
  ok(isLegal(movedTo(velocimetro, { x: 335, y: 300 })), 'subir o velocímetro o mantém legal: só o miolo vazio cruza as zonas')

  // O Stuttgart depende de três coisas que o Cupertino não usa. Se alguma sumir do modelo, o
  // preset degrada em silêncio para "parecido".
  const potencia = stuttgart.pages[0].elements.find((e) => e.id === 'stu-dial-power')
  eq(potencia.map.kind, 'piecewise', 'o medidor de potência do Stuttgart é não-linear')
  eq(potencia.needle.paint.fill, 'hubRadial', 'o ponteiro do Stuttgart usa gradiente ancorado no eixo')
  // Era `face.enabled === false`: o mostrador era um buraco. Virou vidro a pedido, e o que a
  // regra tem de proteger não é mais o buraco — é a RAZÃO do buraco, que é o papel de parede
  // continuar aparecendo. Um disco opaco passaria por "tem vidro" e mataria o tema do mesmo
  // jeito, então a guarda mede a transparência da tinta.
  eq(potencia.face.enabled, true, 'o mostrador do Stuttgart tem fundo de vidro')
  ok(potencia.face.blurPx > 0, 'o fundo do mostrador do Stuttgart desfoca o que está atrás')
  {
    const alfa = (c) => Number(/rgba\([^)]*,\s*([\d.]+)\s*\)/.exec(c)?.[1] ?? 1)
    ok(alfa(potencia.face.paint.from) <= 0.15 && alfa(potencia.face.paint.to) <= 0.15,
      'a tinta do vidro é um filme, não um disco: o papel de parede continua atravessando')
  }
  {
    const velStuFace = stuttgart.pages[0].elements.find((e) => e.id === 'stu-dial-speed').face
    eq(velStuFace.blurPx, potencia.face.blurPx,
      'os dois mostradores são gêmeos: um desfoque diferente em cada um leria como defeito')
  }
  eq(stuttgart.fonts, ['exoV'], 'o Stuttgart empacota a Exo V')

  // O papel de parede do Stuttgart chama-se `car-bg.jpg` e contém bytes de PNG. Recodificar pela
  // extensão quebra o arquivo, então o nome original tem que sobreviver até o theme.xml.
  eq(backgroundFileName(stuttgart), 'car-bg.jpg', 'o papel de parede original mantém o nome de arquivo')
  ok(emitThemeXml(stuttgart).includes('<background>car-bg.jpg</background>'), 'o theme.xml declara o papel de parede original')
  eq(backgroundFileName(cupertino), null, 'um tema de cor sólida não declara papel de parede')

  // --- suavização da leitura ----------------------------------------------------------------
  //
  // Dois consertos diferentes, pelo mesmo mecanismo, e a diferença entre eles é o que não pode
  // se perder numa edição futura.
  //
  // POTÊNCIA: o dado chega em degraus de 5 a 12 pontos, espaçados. Ponteiro E número saltam
  // juntos, então os DOIS são suavizados com o MESMO tempo — tempos diferentes fariam o número
  // chegar antes do ponteiro que ele acompanha.
  //
  // VELOCIDADE: o dado chega de 1 em 1 km/h. Para o número isso é a leitura certa e um filtro só
  // atrasaria; para o ponteiro, um km/h vale 2,08° na escala fechada deste tema, e 2° de cada vez
  // é um ponteiro aos pulinhos. Por isso só a ligação do MOSTRADOR é suavizada, e ela é a única
  // do projeto que declara `smoothStep`.
  const velStu = stuttgart.pages[0].elements.find((e) => e.id === 'stu-speed-value')
  const dialVelStu = stuttgart.pages[0].elements.find((e) => e.id === 'stu-dial-speed')
  const potValor = stuttgart.pages[0].elements.find((e) => e.id === 'stu-power-value')
  ok(potencia.binding.smoothMs > 0, 'o mostrador de potência do Stuttgart suaviza a leitura')
  eq(potValor.binding.smoothMs, potencia.binding.smoothMs,
    'ponteiro e número usam o MESMO tempo: dois valores diferentes e o número chega antes do ponteiro')
  ok(!velStu.binding.smoothMs,
    'o NÚMERO da velocidade não é suavizado: o carro já publica de 1 em 1 km/h e o filtro ali só ' +
      'acrescentaria atraso à leitura que não pode atrasar')
  ok(dialVelStu.binding.smoothMs > 0 && dialVelStu.binding.smoothStep === 0.25,
    'o PONTEIRO da velocidade é suavizado com degrau de 0,25 km/h — quatro passos de 0,52° para ' +
      'cada km/h que o carro anuncia')
  eq(dialVelStu.binding.smoothMs, 190,
    'com o filtro criticamente amortecido, 190 ms dão o mesmo atraso que os 240 ms do filtro de ' +
      'primeira ordem anterior e metade do pior passo — não é um número escolhido a gosto')
  // O QUE O FILTRO ENTREGA, MEDIDO — e não a conta de guardanapo.
  //
  // A conta analítica subestima: o carro publica em degraus, espaçados, então o alvo do filtro já
  // chega em escada e o pico logo depois de um degrau é maior do que a fórmula contínua diz. Esta
  // simulação roda o MESMO filtro criticamente amortecido do motor, com relógio fixo de 60 Hz e a
  // mesma quantização do desenho, e converte para GRAUS pela geometria do próprio tema — que é a
  // unidade em que o olho julga "o ponteiro anda aos pulinhos".
  const simular = ({ smoothMs, smoothStep = 0, grau, fonte, segundos }) => {
    const omega = 4.744 / Math.max(0.001, smoothMs / 1000)
    const dt = 1000 / 60
    let x = null, taxa = 0, anterior = null
    const passos = [], atrasos = []
    for (let t = 0; t < segundos * 1000; t += dt) {
      const alvo = fonte(t)
      if (x === null) x = alvo
      const seg = dt / 1000
      const erro = x - alvo
      const c = taxa + omega * erro
      const amort = Math.exp(-omega * seg)
      x = alvo + (erro + c * seg) * amort
      taxa = (c - omega * erro - omega * c * seg) * amort
      const desenhado = smoothStep > 0 ? Math.round(x / smoothStep) * smoothStep : x
      const g = grau(desenhado)
      if (t > 1500) {
        if (anterior !== null && Math.abs(g - anterior) > 1e-9) passos.push(Math.abs(g - anterior))
        atrasos.push(Math.abs(grau(alvo) - g))
      }
      anterior = g
    }
    const pico = (v) => Math.max(...v)
    const mediana = (v) => [...v].sort((a, b) => a - b)[Math.floor(v.length / 2)]
    return { passoMax: pico(passos), passoMediano: mediana(passos), atrasoMediano: mediana(atrasos) }
  }

  // Geometria do próprio tema: 250° de curso nos dois mostradores.
  const grauVel = (v) => 250 * (v / 120)                    // escala fechada, 0..120
  const grauPot = (v) => 250 * (v >= 0 ? 1 / 6 + (v / 100) * (5 / 6) : (1 + v / 100) / 6)

  // VELOCÍMETRO, aceleração forte: 1 km/h a cada 100 ms.
  const vel = simular({
    ...dialVelStu.binding, grau: grauVel, segundos: 6,
    fonte: (t) => 40 + Math.floor(t / 100)
  })
  ok(vel.passoMax <= 0.53,
    'o ponteiro da velocidade nunca dá um passo maior que o degrau declarado: 0,52° em vez dos ' +
      '2,08° que um km/h vale nesta escala')
  ok(vel.atrasoMediano <= 1.6,
    'e fica ~0,75 km/h atrás do número nessa aceleração — o número em si não é filtrado')

  // POTÊNCIA: degraus de 5 a 12 pontos a cada 400 ms, que é como a chave chega no carro. É o
  // mostrador que motivou o filtro, e é aqui que a segunda ordem paga: com a primeira ordem o
  // pior passo de um quadro era 2,91°, do tamanho do pulinho que se queria tirar.
  const DEGRAUS_DE_POTENCIA = [7, 12, -5, 9, -11, 6, 10, -8, 5, 12, -6, 9]
  const pot = simular({
    ...potencia.binding, grau: grauPot, segundos: 12,
    fonte: (t) => {
      let v = 20
      for (let i = 0; i < Math.floor(t / 400); i++) {
        v = Math.max(-100, Math.min(100, v + DEGRAUS_DE_POTENCIA[i % DEGRAUS_DE_POTENCIA.length]))
      }
      return v
    }
  })
  ok(pot.passoMax <= 2.0,
    'o pior passo de um quadro no ponteiro da potência fica abaixo de 2° — com o filtro de ' +
      'primeira ordem que havia antes eram 2,91°')
  ok(pot.passoMediano <= 0.6,
    'e o passo típico fica na casa do meio grau')
  ok(pot.atrasoMediano <= 5.0,
    'o preço em atraso do mostrador de potência está declarado aqui: ~4,3° medianos, que num ' +
      'medidor de potência não se vê, e que sobe junto se alguém aumentar o smoothMs')

  // O payload não leva `smoothMs`/`smoothStep` onde eles valem zero: seria peso morto em toda
  // ligação de todo tema exportado.
  const htmlStu = emitAppHtml(stuttgart, { backgroundCss: '#000' })
  eq((htmlStu.match(/"smoothMs":/g) || []).length, 3,
    'as duas ligações de potência e a do ponteiro da velocidade levam smoothMs para o app.html')
  eq((htmlStu.match(/"smoothStep":/g) || []).length, 1,
    'só o ponteiro da velocidade declara degrau de desenho')
  // Mesma regra para o vidro: dois mostradores, dois `blurPx`. Um tema sem vidro não paga nada.
  eq((htmlStu.match(/"blurPx":/g) || []).length, 2,
    'só os dois mostradores levam blurPx para dentro do app.html')
  eq((emitAppHtml(cupertino, { backgroundCss: '#000' }).match(/"blurPx":/g) || []).length, 0,
    'um tema que não pediu vidro não carrega o campo: `blurPx: 0` em toda ligação seria peso morto')

  // --- capa do álbum no Stuttgart ------------------------------------------------------------
  // Pedido de 2026-09-22: a linha "tocando agora" só tinha texto. A capa entra à esquerda do
  // título, e o que se trava aqui é o que faz ela APARECER no carro: a chave assinada (sem ela o
  // app nunca empurra a capa) e o lado que cabe entre os selos do topo e o vão da projeção.
  {
    const midia = stuttgart.pages[0].elements.find((e) => e.id === 'stu-media-line')
    ok(midia.showArtwork === true, 'a linha de mídia do Stuttgart mostra a capa')
    const pStu = buildPayload(stuttgart)
    ok(pStu.subscribeKeys.includes('app.media.album_art'),
      'o Stuttgart assina app.media.album_art — sem isso o app nunca empurra a capa')
    const pl = pStu.pages[0].elements.find((e) => e.id === 'stu-media-line')
    ok(pl.extraKeys.includes('app.media.album_art'), 'a capa chega ao elemento de mídia')

    // Mesma conta do renderizador (lado = 54% da altura da caixa, centralizado na vertical).
    const lado = Math.round(pl.rect.h * 0.54)
    const topo = pl.rect.y + (pl.rect.h - lado) / 2
    const selos = pStu.pages[0].elements.filter((e) => e.id === 'stu-drive-mode' || e.id === 'stu-power-mode')
    const baseDosSelos = Math.max(...selos.map((e) => e.rect.y + e.rect.h))
    ok(lado >= 52, `a capa tem lado de capa, não de selo (${lado} px; os 46 fixos de antes eram menores que o texto)`)
    ok(topo >= baseDosSelos + 4, `a capa não encosta nos selos de modo (topo ${topo}, selos até ${baseDosSelos})`)

    const { readFileSync } = await import('node:fs')
    const { dirname, join } = await import('node:path')
    const { fileURLToPath } = await import('node:url')
    // Desde a 7.6 o NTG do motor não viaja mais no APK (o NTG que se escolhe é o clássico); o
    // app.html dele ficou como referência em scripts/referencia/ (ver o LEIA-ME de lá).
    const embarcado = readFileSync(join(dirname(fileURLToPath(import.meta.url)), '..', '..',
      'scripts/referencia/stuttgart-ntg55/app.html'), 'utf8')
    const caixa = embarcado.match(/var PROJ_BOX = \{ x: \d+, y: (\d+),/)
    ok(caixa && topo + lado <= Number(caixa[1]),
      `a capa termina antes do vão da projeção (${topo + lado} <= ${caixa && caixa[1]})`)

    // O app.html embarcado não é regerado por inteiro (o vão da projeção só existe nele), então o
    // payload e o renderizador de mídia dele são conferidos contra o gerador aqui.
    const bootEmb = embarcado.match(/^<script>OPENGW\.boot\((.*)\);<\/script>$/m)
    const bootGer = htmlStu.match(/^<script>OPENGW\.boot\((.*)\);<\/script>$/m)
    ok(bootEmb && bootGer && bootEmb[1] === bootGer[1],
      'o payload do Stuttgart embarcado é o mesmo que o preset gera')
    const bloco = (h) => { const a = h.indexOf('var MEDIA = {'); return a < 0 ? null : h.slice(a, h.indexOf('var DECOR = {', a)) }
    ok(bloco(embarcado) !== null && bloco(embarcado) === bloco(htmlStu),
      'o renderizador de mídia embarcado é o mesmo do motor')
    ok(embarcado.includes('.media.line.empty .media-art, .media.line.empty .media-art-ph { display: none; }'),
      'sem nada tocando a capa some junto com o texto, em vez de sobrar uma nota musical solta')
  }

  // --- tamanhos de texto do Stuttgart ------------------------------------------------------
  //
  // Os números cresceram a pedido. Dois tetos saíram de medição no navegador e não de gosto:
  const rotulos = dialVelStu.ticks.labelStyle.sizePx
  ok(rotulos <= 30,
    'os números do mostrador vão até 30 px: na escala de 260 eles ficam a 72 px entre centros e ' +
    'o zoom de 1,6 leva um rótulo de três dígitos a 83 px — medido, sobram 4,7 px para o vizinho')
  const marcha = stuttgart.pages[0].elements.find((e) => e.id === 'stu-gear')
  ok(marcha.text.sizePx <= 64,
    'a marcha vai até 64 px: a linha de mídia começa em y=68 e a marcha é centrada em y=32, ' +
    'então acima disso o "D" encosta no nome da faixa')
  ok(marcha.text.sizePx >= 48, 'a marcha é o maior item da barra superior, como foi pedido')

  // --- os dois números digitais moram no pé do próprio mostrador ---------------------------
  //
  // Estavam 30 px para fora do eixo cada um, e em alturas diferentes: 613 à esquerda, 583 à
  // direita. Com dois mostradores gêmeos lado a lado, isso lê como desalinhamento, não como
  // intenção. Centrados, a folga para o texto vizinho ainda MELHOROU nos dois lados, porque o
  // deslocamento de cada um apontava justamente para o vizinho: o consumo à esquerda e o
  // odômetro à direita.
  const dialPotStu = potencia
  eq(velStu.place.at.x, dialVelStu.place.at.x,
    'o número da velocidade fica no eixo do velocímetro')
  eq(potValor.place.at.x, dialPotStu.place.at.x,
    'o número da potência fica no eixo do medidor de potência')
  eq(velStu.place.at.y, potValor.place.at.y,
    'os dois números ficam na MESMA altura: são mostradores gêmeos')
  // O y não é escolha, é o que cabe. Medido no navegador a 100 px: o bloco ocupa 549..661; o
  // rótulo mais baixo das marcações termina em 541 e a borda do vidro, em 668.
  ok(velStu.place.at.y >= 610 && velStu.place.at.y <= 614,
    'a 100 px o número só cabe perto de y=613: subir encosta no rótulo de baixo das marcações, ' +
    'descer atravessa o bisel')
  eq(velStu.place.at.y - dialVelStu.place.at.y, 213,
    'o número fica no vão inferior do arco, 213 px abaixo do eixo do mostrador')

  // A mesma prova de motor idêntico, agora entre dois temas de verdade e bem diferentes.
  const hA = emitAppHtml(cupertino, { backgroundCss: '#000' })
  const hB = emitAppHtml(stuttgart, { backgroundCss: '#000' })
  const motor = engineFingerprint()
  ok(hA.includes(motor.split('\n')[2]) && hB.includes(motor.split('\n')[2]), 'os dois presets carregam o mesmo motor')

  // Clonar dá identidade nova em tudo que pode colidir, e mantém o desenho.
  const clone = cloneSpec(cupertino, 'Meu Cupertino')
  ok(clone.id !== cupertino.id, 'o clone recebe id próprio')
  ok(clone.folderName !== cupertino.folderName, 'o clone recebe pasta própria')
  eq(clone.origin, cupertino.id, 'o clone guarda de qual preset saiu')
  eq(clone.name, 'Meu Cupertino', 'o clone usa o nome escolhido')
  eq(clone.pages[0].elements.length, cupertino.pages[0].elements.length, 'o clone mantém todos os elementos')
  ok(clone.pages[0].elements.every((e, i) => e.id !== cupertino.pages[0].elements[i].id), 'todo elemento clonado recebe id novo')
  clone.pages[0].elements[0].place.at.x = 999
  eq(cupertino.pages[0].elements[0].place.at.x, 335, 'mexer no clone não altera o preset')

  // O Minimalist cobre o caminho que os outros dois não tocam: um tema SEM nenhum mostrador
  // redondo. `dialPayload` e `partsFor` nunca rodam, e o motor precisa montar a página assim
  // mesmo — foi por isso que ele entrou no teste e não só na galeria.
  ok(
    minimalist.pages[0].elements.every((e) => e.kind !== 'dial'),
    'o Minimalist não tem mostrador redondo nenhum',
  )

  // A coluna x 225-430 é do veículo (READY, placa e ESP). O bloco da velocidade inteiro tem que
  // ficar à direita dela: encostar ali é o único jeito de este tema virar ilegal.
  const numero = minimalist.pages[0].elements.find((e) => e.id === 'min-speed')
  ok(isLegal(numero), 'o número da velocidade do Minimalist é legal onde está')
  ok(!isLegal(movedTo(numero, { x: 430, y: 440 })), 'arrastar o número para cima da zona do ESP o torna ilegal')

  // Toda regra de cor tem que apontar para um token que existe. Um nome trocado não quebra nada
  // em tempo de build: o motor escreve `--t-<nome>` e ninguém lê, então a regra some em silêncio.
  for (const regra of minimalist.colorRules) {
    for (const t of regra.tokens) {
      ok(minimalist.palette.tokens[t] !== undefined, `Minimalist: a regra "${regra.label}" pinta o token ${t}, que existe na paleta`)
    }
  }

  // O combo de escala está desligado (não há escala para reescalonar), mas a regra "acima do
  // limite da via" depende das duas chaves do TSR. Se a assinatura por regra de cor regredisse, a
  // regra nunca dispararia no carro e o preview continuaria mostrando tudo funcionando.
  const pMin = buildPayload(minimalist)
  eq(pMin.speedScaleConfig, false, 'o Minimalist não oferece o combo de escala do velocímetro')
  ok(pMin.subscribeKeys.includes('car.map.tsr.nav_speed_limit'), 'mesmo sem o combo, o limite da via é assinado pela regra de cor')
  ok(pMin.subscribeKeys.includes('car.map.tsr.nav_speed_limit_sign_status'), 'a validade da placa também é assinada')
  ok(!emitThemeXml(minimalist).includes('<id>speed_scale</id>'), 'o theme.xml do Minimalist não declara o combo de escala')

  // --- Minimalist embarcado no APK ------------------------------------------------------------
  // Pedido de 2026-09-22: o Minimalist deixou de ser só predefinição do editor e viaja no APK
  // (`gerar-tema-preinstalado.mjs`). Diferente do Stuttgart, ele NÃO tem código feito à mão: o
  // arquivo embarcado tem de ser byte a byte o que o gerador produz, senão alguém editou o gerado.
  {
    const { readFileSync, readdirSync } = await import('node:fs')
    const { dirname, join } = await import('node:path')
    const { fileURLToPath } = await import('node:url')
    const { backgroundCssForCar } = await import('../src/theme-editor/resolveBackground.ts')
    const repo = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
    const assets = join(repo, 'app/src/main/assets/PreinstalledThemes')
    const pasta = join(assets, minimalist.folderName)
    const html = readFileSync(join(pasta, 'app.html'), 'utf8')
    ok(html === emitAppHtml(minimalist, { backgroundCss: backgroundCssForCar(minimalist.background) }),
      'o app.html do Minimalist embarcado é exatamente o que o gerador produz')
    const semVersao = (x) => x.replace(/<version>[^<]*<\/version>/, '<version/>')
    ok(semVersao(readFileSync(join(pasta, 'theme.xml'), 'utf8')) === semVersao(emitThemeXml(minimalist)),
      'o theme.xml do Minimalist embarcado é o que o gerador produz')
    ok(readdirSync(pasta).includes('thumbnail.png'), 'o Minimalist embarcado leva a miniatura para a lista de temas')

    // `pruneRetiredThemes()` roda ANTES do seed em toda abertura do app. Uma pasta embarcada com
    // um nome dessa lista seria apagada e recopiada a cada início — foi o que quase aconteceu com
    // 'minimalist', nome de um tema antigo aposentado.
    const tm = readFileSync(join(repo,
      'app/src/main/java/br/com/opengw/painel/TemasInstalados.kt'), 'utf8')
    const lista = tm.match(/val APOSENTADAS: List<String> = listOf\(([^)]*)\)/)
    const aposentadas = new Set(lista ? [...lista[1].matchAll(/"([^"]+)"/g)].map((m) => m[1].toLowerCase()) : [])
    ok(aposentadas.size > 0, 'a lista de pastas aposentadas do catálogo de temas foi encontrada')
    for (const p of readdirSync(assets)) {
      ok(!aposentadas.has(p.toLowerCase()), `a pasta embarcada "${p}" não está entre as aposentadas`)
    }
  }
}

// ---------------------------------------- escala pela placa e regras de cor
// As duas funções novas dependem de coisas que o editor não pode inventar: a chave do limite da
// via tem que estar no contrato do carro, e as chaves de uma regra de cor têm que ser assinadas
// mesmo quando nenhum elemento as usa.
{
  const { signLimitMap, createColorRule, LIMIT_KEY, LIMIT_STATUS_KEY, createTheme, createElement } =
    await import('../src/theme-editor/model/defaults.ts')
  const { valueToT } = await import('../src/theme-editor/emit/payload.ts')
  const { ENGINE_JS } = await import('../src/theme-editor/runtime/engineJs.ts')
  const contrato = new Set(THEME_BRIDGE.keys.map((k) => k.key))

  // Sem isto, a função inteira seria teatro: o carro descarta em silêncio um subscribe fora da
  // lista, e o velocímetro ficaria parado na VMAX de reserva para sempre.
  ok(contrato.has(LIMIT_KEY), 'a chave do limite da via está no contrato do tema')
  ok(contrato.has(LIMIT_STATUS_KEY), 'a chave de validade da placa está no contrato do tema')

  const mapa = signLimitMap(60, 200)
  eq(mapa.kind, 'signLimit', 'a escala pela placa tem tipo próprio')
  ok(Math.abs(valueToT(mapa, 30) - 0.5) < 1e-6, 'sem placa, metade da VMAX de reserva é metade do arco')

  const tema = temaBase('Com placa')
  tema.pages[0].elements[0].map = signLimitMap(60, 200)
  const p1 = buildPayload(tema)
  ok(p1.subscribeKeys.includes(LIMIT_KEY), 'um mostrador que segue a placa assina a chave do limite')
  ok(p1.subscribeKeys.includes(LIMIT_STATUS_KEY), 'e também a chave de validade da placa')
  for (const k of p1.subscribeKeys) ok(contrato.has(k), `a chave assinada ${k} está no contrato`)

  // Uma regra de cor pode depender de uma chave que nenhum elemento usa. Se o emissor não a
  // assinar, a condição nunca muda de estado e a regra fica congelada no valor inicial.
  const comRegra = temaBase('Com regra')
  const regra = createColorRule('re')
  comRegra.colorRules = [regra]
  const p2 = buildPayload(comRegra)
  ok(p2.subscribeKeys.includes('car.basic.gear_status'), 'a chave da regra de cor entra na assinatura')
  eq(p2.colorRules.length, 1, 'a regra chega ao payload')
  eq(p2.colorRules[0].tokens, ['ink'], 'a regra diz qual cor troca')

  const desligada = temaBase('Regra desligada')
  desligada.colorRules = [{ ...createColorRule('re'), enabled: false }]
  eq(buildPayload(desligada).colorRules.length, 0, 'uma regra desligada não é emitida')

  const acimaDaPlaca = temaBase('Acima da placa')
  acimaDaPlaca.colorRules = [createColorRule('acimaLimite')]
  const p3 = buildPayload(acimaDaPlaca)
  ok(p3.subscribeKeys.includes('car.basic.vehicle_speed'), 'a condição "acima da placa" assina a velocidade')
  ok(p3.subscribeKeys.includes(LIMIT_KEY), 'e assina o limite da via')

  // O motor precisa saber comparar sem apostar no formato: metade das chaves booleanas do
  // contrato tem formato NÃO confirmado no veículo.
  ok(/function condicaoVale/.test(ENGINE_JS), 'o motor traz um avaliador de condição único')
  ok(/v\.when === 'flag'/.test(ENGINE_JS) && /parseBoolish\(raw\) === !!v\.on/.test(ENGINE_JS),
    'a condição de ligado/desligado passa pelo parseBoolish, não por comparação com "1"')
  ok(/v\.when === 'overLimit'/.test(ENGINE_JS), 'o motor sabe comparar velocidade com o limite da via')
  ok(/over-limit/.test(ENGINE_JS), 'o motor marca as marcações acima do limite')
  ok(/aplicarRegrasDeCor/.test(ENGINE_JS), 'o motor aplica as regras de cor')
  ok(/document\.body\.style\.setProperty/.test(ENGINE_JS),
    'a regra escreve a cor no body, que vence os blocos de dia, noite e farol')

  // A escala fixa é o caso que o usuário pediu explicitamente: painel sempre mostrando a VMAX.
  const fixo = createElement({ kind: 'dial', key: 'car.basic.vehicle_speed' })
  fixo.map = { kind: 'linear', min: 0, max: 180 }
  ok(Math.abs(valueToT(fixo.map, 90) - 0.5) < 1e-6, 'na escala fixa, metade da VMAX é metade do arco')

  const semRegras = createTheme('Sem regras')
  eq(semRegras.colorRules, [], 'um tema novo nasce sem regra de cor nenhuma')
}

// ---------------------------------------- a escala escolhida DENTRO do carro
// Seguir a placa é decisão de quem dirige, não de quem desenhou o painel. O combo no theme.xml é
// o que permite trocar sem reenviar o tema do computador.
{
  const { ENGINE_JS } = await import('../src/theme-editor/runtime/engineJs.ts')
  const { LIMIT_KEY, LIMIT_STATUS_KEY } = await import('../src/theme-editor/model/defaults.ts')

  const comCombo = temaBase('Com combo')
  comCombo.speedScaleConfig = true
  const xml = emitThemeXml(comCombo)
  ok(/<id>speed_scale<\/id>/.test(xml), 'o theme.xml oferece a escala do velocímetro nos ajustes do carro')
  ok(/<stateVariable>speedScale<\/stateVariable>/.test(xml), 'o combo grava numa preferência que o tema lê')
  ok(/<options>Automática, Fixa, Seguir placa<\/options>/.test(xml), 'as três opções chegam ao carro')
  ok(/<id>display_mode<\/id>/.test(xml), 'o combo Dia/Noite continua ao lado dele')
  ok((xml.match(/<configurations>/g) || []).length === 1, 'os dois combos ficam num único bloco')

  // O parser do app casa nomes de tag GLOBALMENTE. Dois <label> soltos não colidem com nada do
  // tema, mas um <name> ou um <width> dentro de um combo sobrescreveria o do próprio tema.
  ok(!/<configurations>[\s\S]*<name>/.test(xml), 'nenhum combo emite <name>, que sobrescreveria o nome do tema')
  ok(!/<configurations>[\s\S]*<width>/.test(xml), 'nenhum combo emite <width>, que colidiria com a janela de app')

  const p = buildPayload(comCombo)
  ok(p.speedScaleConfig === true, 'o motor recebe que o combo está ligado')
  ok(p.subscribeKeys.includes(LIMIT_KEY) && p.subscribeKeys.includes(LIMIT_STATUS_KEY),
    'com o combo ligado, as chaves do TSR já vêm assinadas da carga')

  const semCombo = temaBase('Sem combo')
  semCombo.speedScaleConfig = false
  semCombo.displayModeConfig = false
  ok(!/<configurations>/.test(emitThemeXml(semCombo)), 'sem combo nenhum, o bloco não é emitido')

  ok(/getPreference\('speedScale'/.test(ENGINE_JS), 'o motor lê a preferência de escala do carro')
  ok(/mapOriginal/.test(ENGINE_JS), 'o motor guarda a escala original para voltar em "Automática"')
  ok(/inst\.map = makeMapper\(inst\.el\)/.test(ENGINE_JS),
    'trocar de escala recria o mapeador — sem isso o ponteiro continuaria na escala antiga')
}

// ------------------------------------------------- teto de compatibilidade do WebView do carro
//
// O defeito relatado: "os únicos temas que funcionam são ID7 e Default; Cupertino Ultra e
// NTG 5.5 ficam com elementos faltando". Comparando os quatro, a única diferença de implementação
// que não é cosmética é o CSS que cada um exige do WebView do painel:
//
//   piso PROVADO pelos dois que funcionam -> Chrome 84
//     Default (res/raw/app.html): `?.` e `??` (80), `clamp()` (79), `gap` em flex (84)
//     Bavarian ID7: `gap` em flex (84); usa `inset` uma vez, mas com width/height explícitos
//                   ao lado — funciona com ou sem suporte, então NÃO prova `inset`.
//
//   o que o motor gerado exigia ACIMA desse piso
//     `inset` (87)        -> #app, .page, .dial-center, .boot e o painel de decoração
//     `aspect-ratio` (88) -> a capa da mídia
//
// Num WebView entre 84 e 86 a declaração inteira é descartada em silêncio: caixas absolutas sem
// deslocamento ficam 0x0. Reproduzido no navegador removendo as duas propriedades do app.html
// gerado — #app e .page viram 0x0, a abertura vira uma caixa de 248x85 no canto, a capa fica com
// 66 px de altura em vez de 205, e os dois elementos de decoração do NTG 5.5 somem por completo.
//
// A lista abaixo é o teto. Nada aqui é estilo: cada entrada é uma propriedade que o carro pode
// não entender e que, quando não entende, apaga um elemento da tela.
{
  const PROIBIDO = [
    { re: /(^|[;{\s])inset\s*:/, nome: 'inset', desde: 'Chrome 87' },
    { re: /\.style\.inset\s*=/, nome: 'element.style.inset', desde: 'Chrome 87' },
    { re: /aspect-ratio\s*:/, nome: 'aspect-ratio', desde: 'Chrome 88' },
    { re: /color-mix\s*\(/, nome: 'color-mix()', desde: 'Chrome 111' },
    { re: /:has\s*\(/, nome: ':has()', desde: 'Chrome 105' },
    { re: /@container/, nome: '@container', desde: 'Chrome 105' },
    { re: /(^|[;{\s])(translate|rotate|scale)\s*:\s*[^;}]*[a-z0-9)]/, nome: 'translate/rotate/scale como propriedade', desde: 'Chrome 104' },
  ]

  const { ENGINE_JS } = await import('../src/theme-editor/runtime/engineJs.ts')
  const tema = temaBase('Teto do WebView')
  tema.pages[0].elements.push(
    posicionar(createElement({ kind: 'media', style: 'card', key: 'app.media.title', zone: 'center' }), 660, 165, 'lg', 1),
    posicionar(createElement({ kind: 'decor', style: 'panel', zone: 'center' }), 700, 40, 'sm', 1),
  )
  const html = emitAppHtml(tema, { backgroundCss: '#000' })

  for (const { re, nome, desde } of PROIBIDO) {
    ok(!re.test(html), `o app.html gerado não depende de ${nome} (${desde}), acima do WebView provado do carro`)
  }

  // O contrário do teto: o que o motor PRECISA ter no lugar dessas propriedades.
  ok(/#app \{ position: absolute; left: 0; top: 0; right: 0; bottom: 0;/.test(html),
    '#app recebe os quatro deslocamentos separados em vez de inset')
  ok(/\.page \{ position: absolute; left: 0; top: 0; right: 0; bottom: 0;/.test(html),
    '.page recebe os quatro deslocamentos separados em vez de inset')
  ok(/\.dial-center \{\s*position: absolute; left: 0; top: 0; right: 0; bottom: 0;/.test(html),
    'a leitura central do mostrador não colapsa: sem inset ela ia para o canto do disco')
  ok(/el\.style === 'line'\s*\? Math\.round\(el\.rect\.h \* 0\.54\)\s*: Math\.round\(el\.rect\.w \* 0\.34\)/.test(html),
    'o lado da capa sai em pixel do renderizador, que conhece a caixa do elemento (altura na linha, largura no card)')

  // A cortina de abertura não pode depender de um timer só: enquanto o painel está oculto o
  // Chromium estrangula setTimeout/rAF, e a cortina presa é o defeito "só o fundo aparece".
  ok(/document\.addEventListener\('visibilitychange'/.test(ENGINE_JS),
    'a abertura sai sozinha quando a página volta a ser exibida')
  ok(/function conferirPrazoDaAbertura\(\)/.test(ENGINE_JS) && /conferirPrazoDaAbertura\(\);/.test(ENGINE_JS),
    'o prazo da abertura é conferido por relógio de parede no intervalo de 2 s')
  ok(/agendarFimDaAbertura\(P\);[\s\S]{0,600}?try \{\s*initBridge\(P\);/.test(ENGINE_JS),
    'a cortina é armada antes do bridge, então um erro no bridge não deixa o painel preto')

  // Um elemento que estoura não pode levar o resto da página junto — era isso que transformava
  // "um renderizador falhou" em "o tema apareceu pela metade".
  ok(/try \{\s*var inst = buildElement\(el, node\);/.test(ENGINE_JS),
    'cada elemento é montado no seu próprio try')
  ok(/\[OPENGW\] elemento '/.test(ENGINE_JS),
    'quando um elemento falha, o console do painel diz qual')
}

// ------------------------------------------------------ Bavarian ID7 (tema escrito à mão)
//
// Este tema não passa pelo motor gerado: o app.html dele é escrito à mão e viaja no APK. O que se
// trava aqui é o que, se quebrar, quebra em silêncio.
//
// Desde a Alpha 6.9 a geometria dos mostradores (aro, faixa, marcações, números, guias dos
// ponteiros e as tabelas valor->fração) sai de scripts/gerar-bavarian-id7.mjs. O caso grave
// continua sendo o `.guide`: a posição do ponteiro sai de `getPointAtLength` sobre ele, e as
// tabelas foram calculadas contra esse traçado exato. Mexer no guia à mão, sem as tabelas, não
// quebra nada visível de imediato — o ponteiro só passa a apontar para o número errado. Por isso a
// trava é: o app.html tem de bater byte a byte com a saída do gerador.
{
  const { readFileSync } = await import('node:fs')
  const { join, dirname } = await import('node:path')
  const { fileURLToPath } = await import('node:url')
  const { spawnSync } = await import('node:child_process')
  const raiz = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
  const html = readFileSync(
    join(raiz, 'app/src/main/assets/PreinstalledThemes/bavarianid7/app.html'),
    'utf8',
  )

  const gerador = spawnSync(process.execPath, [join(raiz, 'scripts/gerar-bavarian-id7.mjs'), '--verificar'], { encoding: 'utf8' })
  ok(gerador.status === 0,
    'o app.html do Bavarian ID7 bate com o gerador — guia e tabelas saem juntos, nunca um sem o outro')

  const guias = [...html.matchAll(/<path class="guide" d="([^"]+)"/g)].map((m) => m[1])
  ok(guias.length === 2, 'os dois mostradores têm guia próprio')

  // As linhas externas continuam lisas: uma sequência longa de cúbicas, sem corda reta.
  const rims = [...html.matchAll(/<path class="rim" d="([^"]+)"/g)].map((m) => m[1])
  ok(rims.length === 2, 'os dois discos têm linha externa própria')
  for (const d of rims) {
    ok(!/\sL\s/.test(d), 'a linha externa não tem corda reta (era a aparência poligonal)')
    ok((d.match(/\sC\s/g) || []).length > 20, 'a linha externa é uma sequência longa de cúbicas')
  }

  // Pedido da 6.9: sem o zoom do número mais próximo; velocímetro até 260; o mostrador direito
  // continua potência/regeneração.
  ok(!/zoomed|destacarMaisProxima/.test(html), 'o zoom da marcação mais próxima está desligado')
  ok(/const SPEED_MAX = 260;/.test(html) && /\[260, 1\]\];/.test(html), 'a escala de velocidade vai até 260')
  ok(/const POWER_TABLE = \[\[-100, 0\]/.test(html) && /const POWER_MAX = 140;/.test(html),
    'o mostrador direito é potência (0-140) com regeneração (até -100)')

  // Os números pedidos como maiores em rodadas anteriores. Travados por valor porque "maior" sem
  // número vira opinião.
  const fonte = (seletor, prop = 'font-size') => {
    const bloco = html.match(new RegExp(`${seletor}\\s*\\{[^}]*\\}`))
    if (!bloco) return null
    const m = bloco[0].match(new RegExp(`${prop}:\\s*(\\d+)px`))
    return m ? Number(m[1]) : null
  }
  ok(fonte('\\.bottom-item \\.value') >= 26, 'os números de autonomia e bateria continuam grandes')
  ok(fonte('\\.speed-readout \\.value') >= 78, 'o número da velocidade continua grande')
  ok(fonte('\\.tick-label') >= 26, 'os números das marcações continuam grandes')
  ok(fonte('\\.power-readout \\.value') >= 50, 'o número da potência continua grande')
  ok(fonte('\\.rpm-readout \\.value') >= 40, 'o número da rotação continua grande')

  // O tema só chega ao carro se a versão do XML for MAIOR que a que está no disco
  // (ThemeManager.seedPreinstalledThemes). Uma correção com a versão parada não sai daqui.
  const xml = readFileSync(
    join(raiz, 'app/src/main/assets/PreinstalledThemes/bavarianid7/theme.xml'),
    'utf8',
  )
  const versao = xml.match(/<version>([^<]+)<\/version>/)?.[1]
  // 1.6.0 (Alpha 9.5): o vão da projeção da central entre os mostradores.
  ok(versao === '1.6.0',
    'a versão do Bavarian ID7 subiu — sem isso o carro fica com a cópia antiga')
}

console.log(falhas === 0 ? `\nMotor de temas: ${total} verificações, todas passaram.` : `\nMotor de temas: ${falhas} de ${total} falharam.`)
process.exitCode = falhas === 0 ? 0 : 1
