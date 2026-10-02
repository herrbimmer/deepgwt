// Testes do histórico de alterações, dos pontos de restauração e do modelo de chaves.
//
// Sem dependência de teste nenhuma: o Node 22 remove os tipos do TypeScript sozinho, então os
// módulos de `src/` são importados diretamente. Mesma abordagem de `test-vin.mjs`.
//
// Os módulos gravam em `localStorage`, que não existe no Node. O stub abaixo é um Map — e por
// isso os imports são dinâmicos: o stub precisa existir ANTES de o módulo ser avaliado.
//
// `scripts/ts-resolve.mjs` (registrado por --import) resolve os imports sem extensão do código
// do app, que o Vite entende e o Node não.

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

// ---------------------------------------------------------------- stub de localStorage
const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => void store.set(k, String(v)),
  removeItem: (k) => void store.delete(k),
  clear: () => store.clear(),
}
globalThis.window = { location: { pathname: '/teste' } }

const changeLog = await import('../src/state/changeLog.ts')
const snapshots = await import('../src/state/snapshots.ts')
const keyModel = await import('../src/coding/keyModel.ts')
const { STORAGE_KEYS } = await import('../src/state/storageKeys.ts')

// ================================================================= histórico de alterações
console.log('Histórico de alterações')
store.clear()

const base = { vin: 'LDG00DEM5R0000019', carIp: '192.168.0.9', source: 'live', route: '/x' }

const id1 = changeLog.appendChange({ ...base, key: 'car.configure.mood_lamp', from: '0', to: '1' })
eq(changeLog.loadChangeLog().length, 1, 'uma escrita registrada')
eq(changeLog.loadChangeLog()[0].result, 'pending', 'escrita nasce sem resposta do carro')
eq(changeLog.loadChangeLog()[0].from, '0', 'guarda o valor anterior')

changeLog.settleChange(id1, 'ok')
eq(changeLog.loadChangeLog()[0].result, 'ok', 'resposta do carro fecha o evento')

const id2 = changeLog.appendChange({ ...base, key: 'car.configure.seat_heating', from: null, to: '1' })
changeLog.settleChange(id2, 'error', 'sem resposta na porta 8766')
const falha = changeLog.loadChangeLog()[1]
eq(falha.result, 'error', 'escrita que falhou continua registrada')
eq(falha.error, 'sem resposta na porta 8766', 'o motivo da falha é preservado')
eq(falha.from, null, 'chave nunca lida guarda null, e não string vazia')

// `null` (nunca reportado) e `''` (reportado vazio) precisam continuar distinguíveis.
const id3 = changeLog.appendChange({ ...base, key: 'car.x.vazio', from: '', to: '1' })
changeLog.settleChange(id3, 'ok')
eq(changeLog.loadChangeLog()[2].from, '', 'valor vazio lido do carro não vira null')

// ---- exportar / importar
const exportado = changeLog.exportChangeLog()
const r1 = changeLog.importChangeLog(exportado)
eq(r1.added, 0, 'reimportar o próprio arquivo não adiciona nada')
eq(r1.duplicates, 3, 'os três eventos são reconhecidos como já conhecidos')
eq(changeLog.loadChangeLog().length, 3, 'o histórico não duplicou')

// Importar em um histórico vazio recupera tudo.
store.clear()
const r2 = changeLog.importChangeLog(exportado)
eq(r2.added, 3, 'importar num histórico vazio traz os três eventos')

// Importar um arquivo sujo descarta o lixo sem derrubar o resto.
store.clear()
const sujo = JSON.stringify({
  version: 1,
  entries: [
    { id: 'bom', ts: 1, key: 'car.a', to: '1' },
    { naoEhEvento: true },
    'nem isso',
  ],
})
const r3 = changeLog.importChangeLog(sujo)
eq(r3.added, 1, 'só o evento válido entra')
eq(r3.rejected, 2, 'os dois registros inválidos são contados como descartados')

// Um arquivo que não é JSON precisa estourar para a tela mostrar "arquivo inválido".
let estourou = false
try {
  changeLog.importChangeLog('isto não é json')
} catch {
  estourou = true
}
ok(estourou, 'arquivo que não é JSON lança em vez de gravar lixo')

// ---- limpar
changeLog.clearChangeLog()
eq(changeLog.loadChangeLog().length, 0, 'limpar esvazia o histórico')

// ---- assinatura
store.clear()
let avisos = 0
const unsub = changeLog.subscribeChangeLog(() => (avisos += 1))
changeLog.appendChange({ ...base, key: 'car.a', from: '0', to: '1' })
eq(avisos, 1, 'quem assina é avisado de uma escrita nova')
unsub()
changeLog.appendChange({ ...base, key: 'car.b', from: '0', to: '1' })
eq(avisos, 1, 'depois de cancelar a assinatura, não avisa mais')

// ================================================================= pontos de restauração
console.log('Pontos de restauração')
store.clear()

const agora = { 'car.a': '1', 'car.b': '0', 'car.c': '5' }
const snap = snapshots.createSnapshot({
  name: 'antes do ADAS',
  vin: 'LDG00DEM5R0000019',
  carIp: '192.168.0.9',
  source: 'live',
  values: agora,
})
eq(snapshots.loadSnapshots().length, 1, 'ponto criado')
eq(snap.kind, 'manual', 'ponto criado pelo usuário é manual')
eq(snap.dateIsApproximate, false, 'ponto criado agora tem data exata')

// Um ponto sem nome recebe um rótulo com data, nunca fica em branco.
const semNome = snapshots.createSnapshot({ name: '   ', vin: null, carIp: '', source: 'demo', values: agora })
ok(semNome.name.length > 0, 'ponto sem nome ganha um rótulo com data')

// ---- diff
const depois = { 'car.a': '0', 'car.c': '5', 'car.d': '9' }
const d = snapshots.diffSnapshot(snap, depois)
eq(d.changed, [{ key: 'car.a', from: '1', to: '0' }], 'só car.a mudou de valor')
eq(d.missingNow, ['car.b'], 'car.b estava no ponto e sumiu da leitura atual')
eq(d.newSinceSnapshot, ['car.d'], 'car.d apareceu depois do ponto')
eq(d.unchanged, 1, 'car.c continua igual')

// O diff não pode confundir "sumiu" com "mudou": restaurar uma chave ausente escreveria às cegas.
ok(!d.changed.some((c) => c.key === 'car.b'), 'chave ausente hoje não entra na lista de restauração')

// ---- migração do formato antigo
store.clear()
localStorage.setItem(STORAGE_KEYS.codingBaseline, JSON.stringify({ 'car.a': '1', 'car.b': '0' }))
const migrado = snapshots.migrateLegacyBaseline('LDG00DEM5R0000019', '192.168.0.9')
ok(migrado !== null, 'o ponto antigo do Coding é convertido')
eq(migrado.kind, 'migrated-baseline', 'o ponto convertido é marcado como tal')
eq(migrado.dateIsApproximate, true, 'a data do ponto convertido é declarada aproximada')
eq(Object.keys(migrado.values).length, 2, 'os valores do ponto antigo são preservados')
ok(localStorage.getItem(STORAGE_KEYS.codingBaseline) !== null, 'a chave antiga NÃO é apagada')

eq(snapshots.migrateLegacyBaseline(null, ''), null, 'a migração não roda duas vezes')
eq(snapshots.loadSnapshots().length, 1, 'e não cria um segundo ponto')

// Sem chave antiga, não há o que migrar.
store.clear()
eq(snapshots.migrateLegacyBaseline(null, ''), null, 'sem ponto antigo, a migração não faz nada')

// Um ponto antigo vazio não vira um ponto de restauração vazio (restaurar dele não faria nada).
store.clear()
localStorage.setItem(STORAGE_KEYS.codingBaseline, JSON.stringify({}))
eq(snapshots.migrateLegacyBaseline(null, ''), null, 'ponto antigo vazio é ignorado')

// ---- teto
store.clear()
for (let i = 0; i < snapshots.MAX_SNAPSHOTS + 5; i += 1) {
  snapshots.createSnapshot({ name: `p${i}`, vin: null, carIp: '', source: 'live', values: { 'car.a': String(i) } })
}
eq(snapshots.loadSnapshots().length, snapshots.MAX_SNAPSHOTS, 'o teto de pontos é respeitado')

// ---- exportar / importar
store.clear()
const alvo = snapshots.createSnapshot({ name: 'exportável', vin: null, carIp: '', source: 'live', values: agora })
const json = snapshots.exportSnapshot(alvo)
eq(snapshots.importSnapshots(json).added, 0, 'reimportar o mesmo ponto não duplica')
store.clear()
eq(snapshots.importSnapshots(json).added, 1, 'importar num app limpo traz o ponto')
eq(snapshots.loadSnapshots()[0].name, 'exportável', 'o nome sobrevive à ida e volta')

// ================================================================= modelo de chaves
console.log('Modelo de chaves')

eq(keyModel.riskOf('car.configure.sales_area'), 'alto', 'coding de mercado é risco alto')
eq(keyModel.riskOf('car.configure.drive_hand_type'), 'alto', 'lado da direção é risco alto')
eq(keyModel.riskOf('car.intelligent_driving_setting.qualquer'), 'alto', 'segurança ativa é risco alto')
eq(keyModel.riskOf('car.configure.mood_lamp'), 'baixo', 'luz ambiente é risco baixo (vem do catálogo)')
eq(keyModel.riskOf('car.configure.inventada_qualquer'), 'medio', 'car.configure desconhecida é risco médio')
eq(keyModel.riskOf('car.qualquer_outra.coisa'), 'baixo', 'chave fora dos padrões cai em risco baixo')

// Uma chave fora do catálogo NÃO pode ganhar um nome bonito: isso sugeriria que alguém sabe o
// que ela faz.
eq(keyModel.labelOf('car.configure.mood_lamp'), 'Mood lamp (luz ambiente)', 'chave curada tem nome')
eq(keyModel.labelOf('car.zzz.desconhecida'), 'car.zzz.desconhecida', 'chave não curada devolve a própria chave')
eq(keyModel.descriptionOf('car.zzz.desconhecida'), null, 'chave não curada não ganha descrição')

eq(keyModel.namespaceOf('car.configure.mood_lamp'), 'car.configure', 'namespace são os dois primeiros segmentos')

eq(keyModel.looksBoolean('1'), true, '"1" parece booleano')
eq(keyModel.looksBoolean('0'), true, '"0" parece booleano')
eq(keyModel.looksBoolean('3'), false, '"3" não parece booleano')
eq(keyModel.looksBoolean(undefined), false, 'chave não lida não parece booleana')

eq(keyModel.showValue(''), '∅', 'valor vazio aparece como símbolo, não como nada')
eq(keyModel.showValue(undefined), '∅', 'valor ausente aparece como símbolo')
eq(keyModel.showValue('0'), '0', '"0" é um valor, e não some da tela')

// =================================================================
console.log('')
if (falhas === 0) console.log(`${total} verificações. Todas passaram.`)
else {
  console.error(`${falhas} de ${total} verificações falharam.`)
  process.exitCode = 1
}
