#!/usr/bin/env node
// Gera a Vehicle Knowledge Base a partir de `knowledge/raw/` (evidência imutável) e das fontes
// já versionadas no repo. Reprodutível e auditável: nenhum dado é digitado à mão aqui, tudo sai
// de um arquivo de origem citado em `sourceFile`.
//
//   node scripts/kb-build.mjs
//
// Saídas:
//   knowledge/controlservice/keys.json
//   knowledge/vhal/properties.json
//   knowledge/coding/definitions.json
//   instalador/src/data/generated/*.ts   (mesmo conteúdo, tipado, para o Vite importar)

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { buildSessionLayers, SESSION_DIR, CAPTURED_AT as SESSION_AT, VIN } from './kb-session.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const RAW = join(ROOT, 'knowledge', 'raw')
const GEN = join(ROOT, 'instalador', 'src', 'data', 'generated')

const read = (p) => readFileSync(p, 'utf8')
const write = (p, data) => { mkdirSync(dirname(p), { recursive: true }); writeFileSync(p, data) }
const writeJson = (p, obj) => write(p, JSON.stringify(obj, null, 2) + '\n')

// A data em que o dump foi tirado do veículo. Não é "hoje" — é a procedência da evidência.
const CAPTURED_AT = '2026-09-14'
const VEHICLE = 'Haval H6 GT (veículo do proprietário)'

// ---------------------------------------------------------------------------
// 1. ControlService — 818 chaves
// Fonte: o enum CarConstants.java, que é o dicionário enumerado do próprio ControlService
// do carro. Existência das chaves = FACT. O que cada uma FAZ não está aqui.
// ---------------------------------------------------------------------------
function buildControlService() {
  const file = 'app/src/main/java/br/com/opengw/models/CarConstants.java'
  const src = read(join(ROOT, file))
  const keys = []
  const re = /^\s*([A-Z0-9_]+)\("([^"]+)"\)/gm
  let m
  while ((m = re.exec(src)) !== null) {
    const [, enumName, key] = m
    keys.push({ enumName, key, namespace: key.split('.').slice(0, 2).join('.') })
  }

  // Estado no app (usada/oculta) e metadados de coding vêm do levantamento de 2026-09-14.
  const coding = read(join(RAW, 'OPENGW_coding_possibilidades.md'))
  const state = new Map()
  // tabelas do catálogo completo: | `chave` | usada|oculta |
  for (const line of coding.split('\n')) {
    const mm = line.match(/^\|\s*`([a-z0-9_.]+)`\s*\|\s*([^|]+?)\s*\|\s*$/)
    if (mm) state.set(mm[1], mm[2].trim())
  }

  const out = keys.map((k) => ({
    ...k,
    appState: state.get(k.key) ?? null,        // 'usada' | 'oculta' | null (fora do levantamento)
    existence: 'FACT',
    semantics: 'INFERRED',                      // o efeito foi deduzido do nome, nunca confirmado
  }))

  const byNamespace = {}
  for (const k of out) byNamespace[k.namespace] = (byNamespace[k.namespace] ?? 0) + 1

  return {
    meta: {
      layer: 'controlservice',
      count: out.length,
      capturedAt: CAPTURED_AT,
      vehicle: VEHICLE,
      method: 'Enumeração do dicionário do ControlService do veículo (AIDL IIntelligentVehicleControlService)',
      sourceFile: file,
      stateSourceFile: 'knowledge/raw/OPENGW_coding_possibilidades.md',
      confidence: {
        existence: 'FACT — cada chave existe no dicionário do carro',
        semantics: 'INFERRED — o efeito de cada chave foi deduzido, não confirmado no veículo',
        values: 'UNKNOWN — este arquivo não contém valores',
      },
      namespaces: byNamespace,
    },
    keys: out,
  }
}

// ---------------------------------------------------------------------------
// 2. VHAL — 1602 propriedades
// Espinha: o TSV do dumpsys car_service. Nomes legíveis: TSV + asset do app Android.
// Descrições: o relatório de diagnóstico da mesma sessão.
// ---------------------------------------------------------------------------
function buildVhal() {
  const tsvFile = 'knowledge/raw/OPENGW_vhal_catalogo_completo.txt'
  const lines = read(join(ROOT, tsvFile)).split('\n').filter(Boolean)
  const header = lines.shift()
  if (!header.startsWith('name\tproperty_id')) throw new Error(`cabeçalho inesperado: ${header}`)

  // Nomes legíveis adicionais do asset já versionado no app Android.
  const assetFile = 'app/src/main/assets/vhal_catalog.tsv'
  const nameById = new Map()
  if (existsSync(join(ROOT, assetFile))) {
    for (const l of read(join(ROOT, assetFile)).split('\n')) {
      const [name, id] = l.split('\t')
      if (name && id && !name.startsWith('0x')) nameById.set(id.toLowerCase(), name)
    }
  }

  // Descrições humanas. O relatório tem DUAS tabelas com as mesmas duas primeiras colunas:
  //   4 colunas -> | `NOME` | 0xid | **valor** | descrição |      (a que interessa)
  //   5 colunas -> | `NOME` | 0xid | INT32 | R | valor |          (catálogo cru, sem descrição)
  // Ancorar o fim da linha é o que separa as duas; sem isso a coluna `access` ("R") entrava
  // como se fosse descrição.
  const diagFile = 'knowledge/raw/OPENGW_diagnostico_dados.md'
  const descById = new Map()
  const DESC_ROW = /^\|\s*`([A-Za-z][A-Za-z0-9_]*)`\s*\|\s*(0x[0-9a-fA-F]+)\s*\|\s*([^|]*?)\s*\|\s*([^|]*?)\s*\|\s*$/
  for (const l of read(join(ROOT, diagFile)).split('\n')) {
    const mm = l.match(DESC_ROW)
    if (!mm) continue
    const [, name, id, , desc] = mm
    const clean = desc.replace(/\*\*/g, '').trim()
    if (clean) descById.set(id.toLowerCase(), { name, description: clean })
  }

  const properties = lines.map((l) => {
    const [rawName, id, type, access, liveValue] = l.split('\t')
    const idLc = (id ?? '').toLowerCase()
    const enriched = descById.get(idLc)
    const named = rawName && !rawName.startsWith('0x') ? rawName : nameById.get(idLc) ?? enriched?.name ?? null
    const hasLive = liveValue !== undefined && liveValue !== '' && liveValue !== 'n/d'
    return {
      name: named,
      id: idLc,
      type: type ?? null,
      access: access ?? null,
      // ECU/frame inferido do prefixo do nome (GW_FD1_*, ESP_FD2_*, TPMS2_*, ...)
      ecuPrefix: named ? named.split('_')[0] : null,
      liveValue: hasLive ? liveValue : null,
      description: enriched?.description ?? null,
      existence: 'FACT',
      value: hasLive ? 'OBSERVED' : 'UNKNOWN',
      semantics: enriched?.description ? 'INFERRED' : 'UNKNOWN',
    }
  })

  const observed = properties.filter((p) => p.liveValue !== null).length
  const byAccess = {}
  for (const p of properties) byAccess[p.access ?? '?'] = (byAccess[p.access ?? '?'] ?? 0) + 1

  return {
    meta: {
      layer: 'vhal',
      count: properties.length,
      named: properties.filter((p) => p.name).length,
      described: properties.filter((p) => p.description).length,
      observedValues: observed,
      capturedAt: CAPTURED_AT,
      vehicle: VEHICLE,
      method: 'dumpsys car_service via telnet root; leitura por CarPropertyManager.getProperty(propId, areaId)',
      sourceFiles: [tsvFile, diagFile, assetFile],
      confidence: {
        existence: 'FACT — id/tipo/acesso vieram do dump do próprio carro',
        value: `OBSERVED — ${observed} valores lidos ao vivo em ${CAPTURED_AT}; valem para aquele carro naquele estado`,
        semantics: 'INFERRED — descrições deduzidas do nome do sinal',
      },
      byAccess,
    },
    properties,
  }
}

// ---------------------------------------------------------------------------
// 3. Coding — catálogo curado (as 8 tabelas temáticas do levantamento)
// ---------------------------------------------------------------------------
function buildCoding() {
  const file = 'knowledge/raw/OPENGW_coding_possibilidades.md'
  const md = read(join(ROOT, file))
  const entries = []
  let category = null
  let inCurated = false
  for (const line of md.split('\n')) {
    if (line.startsWith('## ')) inCurated = line.includes('Codings mais interessantes')
    if (line.startsWith('### ')) category = line.replace(/^###\s*/, '').trim()
    if (!inCurated) continue
    const mm = line.match(/^\|\s*`([a-z0-9_.]+)`\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$/)
    if (!mm) continue
    const [, key, name, effect, risk, appState] = mm
    entries.push({
      key,
      name,
      effect,
      // "medio (hardware)" -> risk 'medio' + nota
      risk: risk.split(' ')[0],
      riskNote: risk.includes('(') ? risk.slice(risk.indexOf('(') + 1, risk.lastIndexOf(')')) : null,
      category,
      appState,
      existence: 'FACT',
      semantics: 'INFERRED',
      writeEffect: 'REQUIRES_VALIDATION', // escrever e observar o resultado no carro nunca foi feito
    })
  }
  return {
    meta: {
      layer: 'coding',
      count: entries.length,
      capturedAt: CAPTURED_AT,
      vehicle: VEHICLE,
      method: 'Curadoria manual das 818 chaves contra o que o app já expõe (201 usadas / 617 ocultas)',
      sourceFile: file,
      confidence: {
        existence: 'FACT — as chaves existem',
        semantics: 'INFERRED — nome/efeito deduzidos',
        writeEffect: 'REQUIRES_VALIDATION — nenhuma escrita foi testada no veículo',
      },
    },
    entries,
  }
}

// ---------------------------------------------------------------------------
// 4. Contrato do bridge de temas — o que o CARRO aceita de um tema do cluster.
//
// Isto NÃO é o dicionário de 818 chaves. É o recorte que `ThemeBridgeImpl.getAvailableKeys()`
// publica para o WebView do cluster, e a diferença é a razão de existir desta seção: um
// `Android.subscribe` de chave fora desta lista é descartado com um log e NADA mais — sem
// exceção, sem retorno, sem erro em JavaScript. O tema fica com o mostrador em `--` para sempre
// e nada no app tem como perceber.
//
// A existência da lista é FACT: é literalmente a lista que o host consulta para aceitar ou
// recusar a assinatura. O formato de cada valor não está neste arquivo.
// ---------------------------------------------------------------------------
const THEME_BRIDGE_FILE = 'app/src/main/java/br/com/opengw/bridge/ThemeBridgeImpl.kt'

// Só chegam por `window.control(chave, valor)`. `subscribe` as aceita (estão na allowlist) e
// depois as entrega a `ServiceManager.ensureKeysMonitored`, que não conhece nenhuma delas —
// então `onDataChanged` nunca dispara para estas seis.
const CONTROL_ONLY_KEYS = [
  'bsdLeft', 'bsdRight', 'carPlayInDash',
  'projectionMirrorInDash', 'projectionPreparingD3', 'projectionCardOverlayAllowed',
]
// Chegam pelos dois canais com tipos diferentes: string no empurrão inicial do `subscribe`,
// booleano de verdade via `control`. Um tema que compare com `=== 'true'` funciona num caminho e
// falha no outro.
const DUAL_CHANNEL_KEYS = ['warningActive', 'warningDismissed']

function buildThemeBridge(known) {
  const src = read(join(ROOT, THEME_BRIDGE_FILE))
  // Comentários PRIMEIRO. `getAvailableKeys()` tem uma string entre aspas dentro de um comentário
  // ("Blocked subscription to undeclared theme key"), e sem esta linha o parser conta 59 chaves
  // onde o carro expõe 58 — inventando uma chave que não existe.
  const semComentarios = (s) => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '')
  const fatia = (inicio, fim) => {
    const depois = src.split(inicio)[1]
    if (depois === undefined) throw new Error(`${THEME_BRIDGE_FILE}: trecho '${inicio}' sumiu`)
    const antes = depois.split(fim)[0]
    if (antes === depois) throw new Error(`${THEME_BRIDGE_FILE}: fim '${fim}' sumiu após '${inicio}'`)
    return semComentarios(antes)
  }
  const aspas = (s) => [...s.matchAll(/"([^"]+)"/g)].map((m) => m[1])

  const readable = aspas(fatia('fun getAvailableKeys', 'return JSONArray'))
  const writable = aspas(fatia('writableCarKeys = setOf(', 'readableContractKeys'))
  const maskNames = aspas(fatia('fun setNativeMaskState', 'Blocked unknown'))

  for (const k of [...CONTROL_ONLY_KEYS, ...DUAL_CHANNEL_KEYS]) {
    if (!readable.includes(k)) {
      throw new Error(`${k} saiu de getAvailableKeys() — reveja a classificação de canais`)
    }
  }

  const canal = (key) =>
    CONTROL_ONLY_KEYS.includes(key) ? 'control' : DUAL_CHANNEL_KEYS.includes(key) ? 'both' : 'subscribe'
  const entrada = (key) => ({
    key,
    channel: canal(key),
    writable: writable.includes(key),
    busKey: known.has(key),
  })

  // O carro aceita escrever e nunca devolve o valor. Ficam na lista de propósito: escondê-las
  // convida alguém a readicioná-las como fonte de leitura mais tarde.
  const writeOnly = writable
    .filter((k) => !readable.includes(k))
    .map((k) => ({ ...entrada(k), channel: 'writeOnly', writable: true }))

  const keys = [...readable.map(entrada), ...writeOnly]

  return {
    meta: {
      layer: 'themebridge',
      count: keys.length,
      capturedAt: CAPTURED_AT,
      vehicle: VEHICLE,
      method: 'Leitura direta de getAvailableKeys() e writableCarKeys no host do cluster',
      sourceFile: THEME_BRIDGE_FILE,
      counts: {
        readable: readable.length,
        writable: writable.length,
        controlOnly: CONTROL_ONLY_KEYS.length,
        writeOnly: writeOnly.length,
      },
      maskNames,
      confidence: {
        existence: 'FACT — é a lista que o host consulta para aceitar ou recusar o subscribe',
        channel: 'FACT — derivado do roteamento por prefixo em ThemeBridgeImpl.subscribe()',
        format: 'UNKNOWN — o formato bruto de cada valor não está neste arquivo',
      },
    },
    keys,
  }
}

// ---------------------------------------------------------------------------
const banner = `// GERADO por scripts/kb-build.mjs — NÃO EDITAR À MÃO.\n// Fonte: knowledge/. Regenerar com \`node scripts/kb-build.mjs\`.\n\n`

const cs = buildControlService()
const vhal = buildVhal()
const coding = buildCoding()

writeJson(join(ROOT, 'knowledge/controlservice/keys.json'), cs)
writeJson(join(ROOT, 'knowledge/vhal/properties.json'), vhal)
writeJson(join(ROOT, 'knowledge/coding/definitions.json'), coding)

write(join(GEN, 'controlServiceKeys.ts'),
  banner + `import type { ControlServiceLayer } from '../knowledgeTypes'\n\n` +
  `export const CONTROL_SERVICE: ControlServiceLayer = ${JSON.stringify(cs)}\n`)
write(join(GEN, 'vhalProperties.ts'),
  banner + `import type { VhalLayer } from '../knowledgeTypes'\n\n` +
  `export const VHAL: VhalLayer = ${JSON.stringify(vhal)}\n`)
write(join(GEN, 'codingDefinitions.ts'),
  banner + `import type { CodingLayer } from '../knowledgeTypes'\n\n` +
  `export const CODING: CodingLayer = ${JSON.stringify(coding)}\n`)

// ---------------------------------------------------------------------------
// 4. Camadas da sessão com o veículo ligado (2026-09-15).
// Catálogo responde "que chaves existem"; estas respondem "o que ESTE carro disse".
const session = buildSessionLayers(ROOT)

writeJson(join(ROOT, 'knowledge/coding/factory.json'), session.factoryCoding)
writeJson(join(ROOT, 'knowledge/network/topology.json'), session.network)
writeJson(join(ROOT, 'knowledge/vhal/observations.json'), session.vhalLive)
writeJson(join(ROOT, 'knowledge/signals/trace.json'), session.liveTrace)
writeJson(join(ROOT, 'knowledge/ecu/platform.json'), session.platform)

const SESSION_TS = [
  ['factoryCoding.ts', 'FACTORY_CODING', 'FactoryCodingLayer', session.factoryCoding],
  ['vehicleNetwork.ts', 'VEHICLE_NETWORK', 'VehicleNetworkLayer', session.network],
  ['vhalObservations.ts', 'VHAL_LIVE', 'VhalLiveLayer', session.vhalLive],
  ['liveTrace.ts', 'LIVE_TRACE', 'LiveTraceLayer', session.liveTrace],
  ['platformIdentity.ts', 'PLATFORM', 'PlatformLayer', session.platform],
]
for (const [fileName, constName, typeName, data] of SESSION_TS) {
  write(join(GEN, fileName),
    banner + `import type { ${typeName} } from '../knowledgeTypes'\n\n` +
    `export const ${constName}: ${typeName} = ${JSON.stringify(data)}\n`)
}

console.log(`\nSessão ${SESSION_AT} — VIN ${VIN} (fonte: ${SESSION_DIR}/)`)
console.log(`Coding fábrica : ${session.factoryCoding.meta.count} flags | ${session.factoryCoding.meta.equippedCount} equipadas`)
console.log(`Rede           : ${session.network.meta.count} nós (${session.network.meta.identified} identificados) | ${session.network.meta.interfaceCount} interfaces`)
console.log(`VHAL ao vivo   : ${session.vhalLive.meta.count} sinais com valor (${session.vhalLive.meta.named} nomeados)`)
console.log(`Série temporal : ${session.liveTrace.meta.count} chaves | ${session.liveTrace.meta.samples} amostras`)
console.log(`Plataforma     : ${session.platform.meta.variantCount} variantes de OS em ${session.platform.meta.familyCount} famílias de build`)

console.log(`ControlService : ${cs.meta.count} chaves (${Object.keys(cs.meta.namespaces).length} namespaces)`)
console.log(`VHAL           : ${vhal.meta.count} props | ${vhal.meta.named} nomeadas | ${vhal.meta.described} descritas | ${vhal.meta.observedValues} valores OBSERVED`)
console.log(`Coding         : ${coding.meta.count} entradas curadas`)

// ---------------------------------------------------------------------------
// Garantia de procedência: nenhuma chave citada pela UI pode ter sido inventada.
// `src/vehicle/identityKeys.ts` afirma no cabeçalho que todos os seus nomes vêm do dump de
// ControlService — esta checagem é o que torna a afirmação verificável em vez de confiável.
const known = new Set(cs.keys.map((k) => k.key))

const themeBridge = buildThemeBridge(known)
writeJson(join(ROOT, 'knowledge/themes/bridge.json'), themeBridge)
write(join(GEN, 'themeBridgeKeys.ts'),
  banner + `import type { ThemeBridgeLayer } from '../knowledgeTypes'\n\n` +
  `export const THEME_BRIDGE: ThemeBridgeLayer = ${JSON.stringify(themeBridge)}\n`)

const tbc = themeBridge.meta.counts
console.log(`Bridge de temas: ${tbc.readable} legíveis | ${tbc.writable} graváveis | ${tbc.controlOnly} só-control | ${tbc.writeOnly} só-escrita`)

const identityFile = join(ROOT, 'instalador/src/vehicle/identityKeys.ts')
if (existsSync(identityFile)) {
  const declared = [...readFileSync(identityFile, 'utf8').matchAll(/key: '([^']+)'/g)].map((m) => m[1])
  const invented = [...new Set(declared)].filter((k) => !known.has(k))
  if (invented.length) {
    console.error(`\nERRO: chaves em identityKeys.ts que NÃO existem no dump do veículo:`)
    invented.forEach((k) => console.error(`  ${k}`))
    process.exitCode = 1
  } else {
    console.log(`Identidade     : ${new Set(declared).size} chaves, todas presentes no dump`)
  }
}

// O mesmo para o catálogo de coding. Ele é escrito à mão em `src/data/codingCatalog.ts`, mas as
// suas entradas vêm do mesmo levantamento que alimenta a KB — então as duas listas têm que bater.
//
// Esta checagem foi escrita depois de encontrar exatamente o problema que ela previne: o
// catálogo do app tinha 57 entradas e a KB, 58. A que faltava era
// `car.configure.default_temp_unit`, presente no documento de origem e no dump do carro, mas
// perdida na transcrição para o TypeScript. Uma descoberta real tinha sumido em silêncio.
const catalogFile = join(ROOT, 'instalador/src/data/codingCatalog.ts')
if (existsSync(catalogFile)) {
  const inApp = new Set(
    [...readFileSync(catalogFile, 'utf8').matchAll(/^\s*\{ key: '([^']+)'/gm)].map((m) => m[1]),
  )
  const inKb = new Set(coding.entries.map((e) => e.key))
  const faltando = [...inKb].filter((k) => !inApp.has(k))
  const sobrando = [...inApp].filter((k) => !inKb.has(k))
  const inventadas = [...inApp].filter((k) => !known.has(k))
  if (faltando.length || sobrando.length || inventadas.length) {
    console.error('\nERRO: codingCatalog.ts divergiu da Knowledge Base:')
    faltando.forEach((k) => console.error(`  falta no app (existe na KB): ${k}`))
    sobrando.forEach((k) => console.error(`  no app mas não na KB:       ${k}`))
    inventadas.forEach((k) => console.error(`  NÃO existe no dump do carro: ${k}`))
    process.exitCode = 1
  } else {
    console.log(`Coding no app  : ${inApp.size} chaves, idênticas à KB e presentes no dump`)
  }
}

// O vínculo ajuste -> equipamento cita flags da codificação de fábrica. Uma flag inventada faria
// o app afirmar "seu carro não tem isto" sobre um item que ele pode ter — o pior tipo de erro
// possível aqui, porque esconde função em vez de oferecer função inexistente.
const linksFile = join(ROOT, 'instalador/src/data/equipmentLinks.ts')
if (existsSync(linksFile)) {
  const texto = readFileSync(linksFile, 'utf8')
  const pares = [...texto.matchAll(/codingKey: '([^']+)',\s*factoryFlag: '([^']+)'/g)]
  const flagsDoCarro = new Set(session.factoryCoding.flags.map((f) => f.name))
  const codingKeys = new Set(coding.entries.map((e) => e.key))
  const flagsRuins = pares.filter(([, , flag]) => !flagsDoCarro.has(flag))
  const chavesRuins = pares.filter(([, key]) => !codingKeys.has(key) && !known.has(key))
  if (flagsRuins.length || chavesRuins.length) {
    console.error('\nERRO: equipmentLinks.ts cita algo que não existe:')
    flagsRuins.forEach(([, , f]) => console.error(`  flag ausente da codificação lida do carro: ${f}`))
    chavesRuins.forEach(([, k]) => console.error(`  chave fora do catálogo e do dump:           ${k}`))
    process.exitCode = 1
  } else {
    console.log(`Equipamento    : ${pares.length} vínculos, todas as flags presentes no carro`)
  }
}

// As demais listas de chaves escritas à mão no app. Elas não têm contraparte na KB (não são
// catálogos curados, são recortes que uma tela lê), mas toda chave citada precisa existir no
// dicionário do carro.
//
// Esta checagem também nasceu de um problema real, e este foi pior que o anterior: a lista de
// perfis de motorista citava `car.ev_setting.wade_mode_enable`, com sublinhado. O dicionário do
// carro tem `car.ev.setting.wade_mode_enable`, com ponto — a transcrição copiou o NOME do enum
// Java em vez do seu VALOR. A chave errada não existe em lugar nenhum, então a leitura voltava
// vazia e a escrita não fazia nada. Não havia erro na tela, não havia log: o modo vau
// simplesmente nunca funcionou, e nada no app tinha como perceber.
//
// Quem encontrou foi a Varredura do Veículo, na primeira vez que rodou.
const LISTAS_DO_APP = [
  { arquivo: 'instalador/src/data/driverProfileKeys.ts', rotulo: 'Perfis' },
  { arquivo: 'instalador/src/data/adasCatalog.ts', rotulo: 'ADAS' },
  { arquivo: 'instalador/src/pro/odometer.ts', rotulo: 'Odômetro' },
  { arquivo: 'instalador/src/screens/gwlinkParams.ts', rotulo: 'GWLink' },
]

for (const { arquivo, rotulo } of LISTAS_DO_APP) {
  const caminho = join(ROOT, arquivo)
  if (!existsSync(caminho)) continue
  const texto = readFileSync(caminho, 'utf8')
  // Qualquer literal com cara de chave do carro, venha de onde vier no arquivo.
  const citadas = [...new Set([...texto.matchAll(/'((?:car|sys)\.[a-z0-9_.]+)'/g)].map((m) => m[1]))]
  const inexistentes = citadas.filter((k) => !known.has(k))
  if (inexistentes.length) {
    console.error(`\nERRO: ${arquivo} cita chaves que NÃO existem no dump do veículo:`)
    inexistentes.forEach((k) => console.error(`  ${k}`))
    process.exitCode = 1
  } else {
    console.log(`${rotulo.padEnd(15)}: ${citadas.length} chaves, todas presentes no dump`)
  }
}

// O editor de temas é o caso invertido, e precisa da checagem OPOSTA. Validar contra o dicionário
// de 818 chaves (como tudo acima faz) deixaria passar 47 chaves que existem no carro e que o
// ThemeBridge recusa — que é exatamente o defeito que este projeto tinha: o seletor de fonte de
// dado do editor era populado com `gwlinkParams.ts`, a lista da tela GWLink. Das 57 opções
// oferecidas, 10 funcionavam. As outras 47 assinavam, eram descartadas em silêncio e o mostrador
// nunca saía de `--` — enquanto no preview do editor, alimentado por um mock, o ponteiro andava.
//
// Então aqui a régua é o contrato do tema, não o dicionário do carro.
const CONTRATO_DO_TEMA = new Set(themeBridge.keys.map((k) => k.key))
const ARQUIVOS_DE_TEMA = [
  { arquivo: 'instalador/src/theme-editor/model/dataSources.ts', rotulo: 'Fontes do editor' },
  { arquivo: 'instalador/src/theme-editor/presets/cupertinoUltra.ts', rotulo: 'Preset Cupertino' },
  { arquivo: 'instalador/src/theme-editor/presets/stuttgartNtg55.ts', rotulo: 'Preset Stuttgart' },
  { arquivo: 'instalador/src/theme-editor/presets/minimalist.ts', rotulo: 'Preset Minimalist' },
]

for (const { arquivo, rotulo } of ARQUIVOS_DE_TEMA) {
  const caminho = join(ROOT, arquivo)
  if (!existsSync(caminho)) continue
  const texto = readFileSync(caminho, 'utf8')
  // Chaves de barramento mais as pseudo-chaves do host, que não têm ponto no nome.
  // `projectionPreparingD3` termina em dígito — sem o `0-9` aqui, a única chave que o carro
  // entrega com número no nome escapava da validação em silêncio.
  const padrao = /'((?:car|sys|app)\.[A-Za-z0-9_.]+|bsd(?:Left|Right)|carPlayInDash|projection[A-Za-z0-9]+|warning(?:Active|Dismissed))'/g
  const citadas = [...new Set([...texto.matchAll(padrao)].map((m) => m[1]))]
  const foraDoContrato = citadas.filter((k) => !CONTRATO_DO_TEMA.has(k))
  if (foraDoContrato.length) {
    console.error(`\nERRO: ${arquivo} cita chaves que o CARRO RECUSA de um tema:`)
    foraDoContrato.forEach((k) => {
      const noDump = known.has(k)
      console.error(`  ${k}${noDump ? '  (existe no barramento, mas está fora de getAvailableKeys)' : '  (não existe em lugar nenhum)'}`)
    })
    process.exitCode = 1
  } else {
    console.log(`${rotulo.padEnd(15)}: ${citadas.length} chaves, todas dentro do contrato do tema`)
  }
}
