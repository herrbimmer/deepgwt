// Guardas da organização do instalador (6.3.1; níveis na 8.0): árvore de menus, níveis e cadeados,
// confirmação de risco, aviso de conexão, migração das preferências e o número de versão.
//
// A árvore de rotas é o que monta o Início, a barra lateral, cada menu, o breadcrumb e o botão
// voltar — então quase toda regra de usabilidade que importa vira uma regra sobre ela, e dá para
// verificar sem abrir janela nenhuma. Mesma abordagem dos outros testes: Node 22 remove os tipos
// sozinho e `scripts/ts-resolve.mjs` resolve os imports sem extensão.

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

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

const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => void store.set(k, String(v)),
  removeItem: (k) => void store.delete(k),
  clear: () => store.clear(),
}

const raiz = (rel) => fileURLToPath(new URL(`../${rel}`, import.meta.url))
const ler = (rel) => readFileSync(raiz(rel), 'utf8')

const nav = await import('../src/navigation/routeMeta.ts')
const { unmetRequirement } = await import('../src/navigation/requirements.ts')
const { splitByImplementation } = await import('../src/navigation/capabilities.ts')
const prefs = await import('../src/state/prefs.ts')
const { STORAGE_KEYS } = await import('../src/state/storageKeys.ts')
const niveis = await import('../src/tiers/niveis.ts')

const { ROUTE_TREE, ROUTE_INDEX, ALL_ROUTES, ROUTE_REDIRECTS, HUB_SECTIONS, childrenOf, sectionItems, pedeRisco } = nav
const nivelDe = (path) => niveis.nivelDoRecurso(ROUTE_INDEX.get(path).node.recurso)

/** Tudo que se alcança pelos menus, a partir do Início. */
function alcancavel() {
  const vistos = new Set(['/'])
  const fila = ['/']
  while (fila.length) {
    const atual = fila.shift()
    for (const filho of childrenOf(atual)) {
      if (vistos.has(filho.path)) continue
      vistos.add(filho.path)
      fila.push(filho.path)
    }
  }
  return vistos
}

// ================================================================= 1. forma da árvore
console.log('1. forma da árvore')
eq(ROUTE_TREE.length, 1, 'uma raiz só')
eq(ROUTE_TREE[0].path, '/', 'a raiz é "/"')
eq(ROUTE_TREE[0].title, 'Início', 'a raiz é o Início (não mais a tela de conectar)')
eq(new Set(ALL_ROUTES.map((r) => r.path)).size, ALL_ROUTES.length, 'nenhum caminho repetido')

for (const filho of ROUTE_TREE[0].children) {
  ok(typeof filho.section === 'string', `${filho.path} tem seção no Início`)
}
for (const [path, entry] of ROUTE_INDEX) {
  if (entry.parentPath === '/' || path === '/') continue
  ok(entry.node.section === undefined, `${path} não é filho do Início e não declara seção`)
}

// Todo recurso de rota existe na tabela do carro (a cópia em mock/licencaFixture.json).
const recursosDaTabela = new Set(niveis.TABELA_PADRAO.recursos.map((r) => r.id))
for (const r of ALL_ROUTES) ok(recursosDaTabela.has(r.recurso), `${r.path}: recurso "${r.recurso}" existe na tabela`)

// Um menu que pede mais que um filho dele trancaria o filho atrás de um nível que ele não exige.
for (const [path, entry] of ROUTE_INDEX) {
  if (!entry.parentPath) continue
  ok(
    niveis.nivelAlcanca(nivelDe(path), nivelDe(entry.parentPath)),
    `${path} (${nivelDe(path)}) não fica atrás de um menu de nível maior (${nivelDe(entry.parentPath)})`,
  )
}

// ================================================================= 2. níveis e cadeados
console.log('2. níveis e cadeados')
const todos = alcancavel()
const listaveis = ALL_ROUTES.filter((r) => ROUTE_INDEX.get(r.path).trail.every((n) => !n.hidden)).map((r) => r.path)
for (const p of listaveis) ok(todos.has(p), `os menus alcançam ${p} (nada some por nível: aparece com cadeado)`)

// Desfazer é sempre livre: instalar, atualizar, reparar, remover, restaurar e o que é informação.
for (const p of [
  '/', '/monitor', '/veiculo', '/ajustes-open-gw', '/config-avancadas', '/config-avancadas/ajustes', '/alteracoes',
  '/opengw', '/install', '/install/opengw', '/install/local', '/update/opengw',
  '/restart-shizuku', '/reset-stealth-mode', '/uninstall', '/conectar', '/privacidade', '/sobre', '/licenca',
]) {
  eq(nivelDe(p), 'drive', `${p} é do Drive`)
}
eq(nivelDe('/espelhar'), 'sport', 'espelhar é Sport')
eq(nivelDe('/temas'), 'sport', 'temas (os que vêm no app) é Sport')
eq(nivelDe('/temas/xtra'), 'premium', 'catálogo XTRA é Premium')
eq(nivelDe('/temas/editor/:id'), 'premium', 'editor de temas é Premium')
eq(nivelDe('/temas/importar-html'), 'signature', 'importar HTML é Signature')
eq(nivelDe('/config-avancadas/adas'), 'premium', 'ADAS é Premium')
eq(nivelDe('/ajustes-guiados'), 'premium', 'funções de fábrica guiadas são Premium')
for (const p of ['/config-avancadas/coding', '/gwlink', '/uninstall-apps', '/pro', '/pro/dtc', '/expert', '/expert/uds']) {
  eq(nivelDe(p), 'signature', `${p} é Signature`)
}
eq(ROUTE_REDIRECTS['/dev-options/mirror'], '/espelhar', 'o endereço antigo do espelhamento leva ao novo')
eq(sectionItems('carro').map((n) => n.path), ['/monitor', '/veiculo', '/espelhar'],
  'Meu carro: Monitorar, Informações e Espelhar e controlar')

// Confirmação de risco: o que sobrou do modo Avançado, só nas ferramentas de escrita crua.
for (const p of [
  '/config-avancadas/coding', '/uninstall-apps', '/gwlink', '/ferramentas', '/pro', '/pro/varredura', '/expert',
  '/expert/vhal', '/dev-options/files', '/dev-options/backup',
]) {
  ok(pedeRisco(p), `${p} pede a confirmação de risco`)
}
for (const p of ['/', '/monitor', '/config-avancadas/adas', '/ajustes-guiados', '/temas', '/alteracoes', '/install', '/uninstall', '/licenca']) {
  ok(!pedeRisco(p), `${p} não pede a confirmação de risco`)
}

// No Drive, a única ação com cor de perigo é remover o próprio app do carro.
const doDrive = [...todos].filter((p) => nivelDe(p) === 'drive')
eq(doDrive.filter((p) => ROUTE_INDEX.get(p).node.tone === 'danger').sort(), ['/uninstall'],
  'no Drive só "Remover do carro" é vermelho')

// Tela sem conteúdo (not-implemented) nunca é do Drive.
{
  const { VALIDATION } = await import('../src/validation/status.ts')
  for (const p of doDrive) {
    const cap = ROUTE_INDEX.get(p).node.capability
    if (cap) ok(VALIDATION[cap]?.status !== 'not-implemented', `${p} (Drive) tem conteúdo`)
  }
}

// Seções do Início
eq(sectionItems('avancado').map((n) => n.path), ['/ferramentas', '/pro', '/expert'],
  'seção Avançado: Ferramentas, Oficina, Engenharia')
for (const s of HUB_SECTIONS) ok(sectionItems(s.id).length > 0, `seção "${s.title}" tem itens`)
eq(sectionItems('rodape').map((n) => n.path), ['/licenca', '/conectar', '/privacidade', '/sobre'],
  'rodapé: Nível e licença, Conexão, Privacidade, Sobre')
// Eram 7 até a 7.6; o oitavo é o espelhamento, pedido de volta pelo usuário.
ok(sectionItems('carro').length + sectionItems('personalizar').length + sectionItems('app').length <= 8,
  'o Início tem no máximo 8 destinos fora do Avançado')

// Instalar é UM clique a partir do Início (Instalar e manter -> Instalar no carro), e não o 5º
// item de uma lista de dez.
eq(childrenOf('/opengw')[0]?.path, '/install', 'instalar é o primeiro item de "Instalar e manter"')

// A casca é quem tranca: uma tela nova não esquece o cadeado nem a confirmação.
const casca = ler('src/navigation/TierShell.tsx')
ok(casca.includes('TelaBloqueadaPeloNivel') && casca.includes('ConfirmacaoDeRisco'), 'a casca mostra o cadeado e a confirmação')
ok(ler('src/navigation/RouteMenu.tsx').includes('SeloDoNivel'), 'os menus mostram o nível que libera')

// ================================================================= 3. nomes
console.log('3. nomes')
for (const r of ALL_ROUTES) {
  ok(!/\b(USER|PRO|EXPERT)\b/.test(r.title), `${r.path}: sem rótulo de nível em inglês`)
}
const header = ler('src/components/Cabecalho.tsx')
ok(!header.includes('TierSwitcher') && header.includes('NivelNoCabecalho'), 'cabeçalho mostra o nível do carro, sem seletor de modo')
eq(niveis.TABELA_PADRAO.niveis.map((n) => n.titulo), ['Essencial', 'Plus', 'Pro', 'Max'], 'os quatro níveis, na ordem')

// ================================================================= 4. rotas antigas e mapa de telas
console.log('4. rotas antigas e mapa de telas')
for (const [de, para] of Object.entries(ROUTE_REDIRECTS)) {
  ok(!ROUTE_INDEX.has(de), `${de} saiu da árvore (virou redirecionamento)`)
  ok(ROUTE_INDEX.has(para), `${de} redireciona para uma rota que existe (${para})`)
}
eq(ROUTE_REDIRECTS['/hub'], '/', '/hub antigo leva ao Início')
eq(ROUTE_REDIRECTS['/dev-options'], '/ferramentas', 'Opções de Desenvolvedor levam a Ferramentas')

const appRoutes = ler('src/navigation/AppRoutes.tsx')
const mapeadas = new Set([...appRoutes.matchAll(/^\s+'(\/[^']*)':/gm)].map((m) => m[1]))
for (const r of ALL_ROUTES) ok(mapeadas.has(r.path), `AppRoutes tem componente para ${r.path}`)
for (const p of mapeadas) ok(ROUTE_INDEX.has(p), `componente de ${p} tem rota na árvore`)
ok(appRoutes.includes('path="*"'), 'URL desconhecida volta ao Início')

// ================================================================= 5. aviso de conexão
console.log('5. aviso de conexão')
eq(unmetRequirement('offline', 'offline', 'unreachable'), null, 'tela offline nunca pede conexão')
eq(unmetRequirement('ip', 'online', 'unknown'), null, 'app respondendo: nada falta')
eq(unmetRequirement('ip', 'unknown', 'unknown'), null, 'ainda verificando: sem aviso (não pisca na abertura)')
eq(unmetRequirement('ip', 'probing', 'unknown'), null, 'testando: sem aviso')
ok(unmetRequirement('ip', 'offline', 'reachable')?.short.includes('app'), 'app sem resposta: pede o app aberto no carro')
eq(unmetRequirement('hotspot', 'offline', 'reachable'), null, 'central na rede: instalar funciona')
ok(unmetRequirement('hotspot', 'offline', 'unreachable')?.short.includes('Wi-Fi'), 'central fora da rede: pede o Wi-Fi do carro')
eq(unmetRequirement('ip', 'demo', 'unreachable'), null, 'demonstração: nada falta')
eq(unmetRequirement('hotspot', 'demo', 'unreachable'), null, 'demonstração: nada falta (hotspot)')

// ================================================================= 6. Oficina e Engenharia
console.log('6. Oficina e Engenharia')
const eng = splitByImplementation(childrenOf('/expert'))
ok(eng.pending.some((n) => n.path === '/expert/uds'), 'Console UDS fica em "Em desenvolvimento"')
ok(eng.ready.some((n) => n.path === '/expert/vhal'), 'Explorador VHAL fica na lista principal')
ok(eng.ready.length > 0 && eng.pending.length > 0, 'Engenharia tem as duas listas')
ok(!childrenOf('/expert').some((n) => n.path.endsWith('/sobre')), 'Sobre saiu da Engenharia')
const ofi = splitByImplementation(childrenOf('/pro'))
eq(ofi.pending.map((n) => n.path).sort(), ['/pro/arvore-de-falhas', '/pro/dtc'], 'Oficina: DTC e Árvore de Falhas recolhidas')

// ================================================================= 7. preferências antigas
console.log('7. preferências antigas (modos e três níveis -> aviso de risco)')
eq(prefs.migrateAck({}), null, 'sem registro: aviso não confirmado')
eq(prefs.migrateAck({ tier: 'user' }), null, 'USER: aviso não confirmado')
eq(prefs.migrateAck({ tier: 'expert', expertAcknowledgedAt: 5 }), 5, 'aviso da antiga Engenharia aproveitado')
eq(prefs.migrateAck({ tier: 'pro', proAcknowledgedAt: 5 }), null, 'aviso da antiga Oficina é outro e não conta')
eq(prefs.migrateAck({ tier: 'advanced', advancedAcknowledgedAt: 9 }), 9, 'aviso do modo Avançado aproveitado')
eq(prefs.migrateAck({ tier: 'basic', advancedAcknowledgedAt: 9 }), 9, 'voltar ao Básico não tinha apagado o aviso')
eq('tier' in prefs.migrate({ tier: 'advanced', advancedAcknowledgedAt: 1 }), false, 'o modo sai do registro')
eq(prefs.migrate('lixo').advancedAcknowledgedAt, null, 'registro corrompido: aviso não confirmado')

store.clear()
store.set(STORAGE_KEYS.prefs, JSON.stringify({ version: 1, tier: 'expert', expertAcknowledgedAt: 11, proAcknowledgedAt: 3, source: 'live', carIp: '192.168.33.1' }))
const lido = prefs.loadPrefs()
eq([lido.advancedAcknowledgedAt, lido.carIp], [11, '192.168.33.1'], 'registro da 6.3.0 carregado sem perder IP nem aviso')
const salvo = JSON.parse(prefs.savePrefs({ source: 'live' }) && store.get(STORAGE_KEYS.prefs))
ok(!('expertAcknowledgedAt' in salvo) && !('proAcknowledgedAt' in salvo) && !('tier' in salvo), 'ao salvar, os campos antigos saem do registro')
eq(salvo.advancedAcknowledgedAt, 11, 'o aviso já confirmado continua')

// ================================================================= 7b. resposta do carro
console.log('7b. resposta de /licenca')
{
  const fixture = JSON.parse(ler('src/mock/licencaFixture.json'))
  const r = niveis.lerRespostaDaLicenca(JSON.stringify(fixture))
  eq([r.nivel, r.cobranca, r.testeLiberado, r.nivelDeTeste], ['signature', false, false, null], 'a 8.0 responde Signature, sem cobrança')
  eq(r.tabela.recursos.length, fixture.recursos.length, 'tabela inteira')
  let antigo = null
  try { niveis.lerRespostaDaLicenca('{"car.basic.vehicle_speed":"0"}') } catch (e) { antigo = e }
  ok(antigo instanceof niveis.RespostaSemNiveis, 'telemetria sem ok = app anterior aos níveis')
  let ruim = null
  try { niveis.lerRespostaDaLicenca('{"ok":true,"nivel":"ouro"}') } catch (e) { ruim = e }
  ok(ruim && !(ruim instanceof niveis.RespostaSemNiveis), 'nível desconhecido é erro, não "carro antigo"')
  const drive = niveis.lerRespostaDaLicenca(JSON.stringify({ ...fixture, nivel: 'drive', nivelDeTeste: 'drive', testeLiberado: true }))
  ok(!niveis.permite(drive.nivel, 'asd', drive.tabela), 'Drive não libera o ASD')
  ok(niveis.permite(drive.nivel, 'barra', drive.tabela), 'Drive libera a barra')
  ok(niveis.permite('drive', 'recurso-que-nao-existe'), 'recurso desconhecido não tranca')
  // O carro manda na tabela: se ele muda um recurso de nível, vale o dele.
  const movido = { ...drive.tabela, recursos: drive.tabela.recursos.map((x) => (x.id === 'asd' ? { ...x, nivel: 'drive' } : x)) }
  ok(niveis.permite('drive', 'asd', movido), 'a tabela do carro vale mais que a cópia local')
  eq(niveis.estadoSemCarro('antigo').nivel, 'signature', 'app antigo: nada trancado')
  eq(niveis.estadoSemCarro('demo').nivel, 'signature', 'demonstração: tudo liberado')
  eq(niveis.estadoSemCarro('sem-carro', 'sport').nivel, 'sport', 'sem carro: fica o último nível lido')
}

// ================================================================= 8. janela e versão
console.log('8. janela e versão')
const tauri = JSON.parse(ler('src-tauri/tauri.conf.json'))
const janela = tauri.app.windows[0]
ok(janela.width >= 768 && janela.width >= janela.height,
  'a janela abre deitada e larga (>= 768) — é o que liga o layout de desktop com barra lateral')

// O instalador ficou para trás do APK três vezes (3.1-4.2, 4.7-5.x, 6.2-6.3). Estas verificações
// falham na hora em que o APK sobe e o instalador não.
const pkg = JSON.parse(ler('package.json'))
const cargo = ler('src-tauri/Cargo.toml').match(/^version = "([^"]+)"/m)?.[1]
eq([tauri.version, cargo], [pkg.version, pkg.version], 'package.json, tauri.conf.json e Cargo.toml com a mesma versão')
const gradle = readFileSync(fileURLToPath(new URL('../../gradle.properties', import.meta.url)), 'utf8')
const apk = gradle.match(/^appVersionName=Alpha (\d+\.\d+) (\d{12})$/m)
ok(apk !== null, 'gradle.properties tem appVersionName no formato "Alpha N.N DDMMAAAAHHMM"')
if (apk) {
  eq(pkg.version.split('.').slice(0, 2).join('.'), apk[1], 'o major.minor do instalador é o número do APK')
  const stamp = ler('src/lib/version.ts').match(/const BUILD_STAMP = '(\d{12})'/)?.[1]
  eq(stamp, apk[2], 'o BUILD_STAMP do instalador é o carimbo do APK')
}

// =================================================================
console.log('')
if (falhas === 0) console.log(`${total} verificações. Todas passaram.`)
else {
  console.error(`${falhas} de ${total} verificações falharam.`)
  process.exitCode = 1
}
