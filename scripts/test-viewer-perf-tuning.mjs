// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Ajustes de desempenho do launcher 3D que NÃO são o orçamento de quadros: dica de GPU na criação
// do contexto, freio com a página escondida e o carimbo de versão da camada.
//
// Roda com `node scripts/test-viewer-perf-tuning.mjs`. Mesma pasta que o teste do orçamento usa
// (VIEWER_SRC); sem ela, o teste se declara pulado — o launcher não é compilado a partir daqui.
//
// POR QUE ESTE TESTE EXISTE: os três são invisíveis. Um `powerPreference` que deixa de ser
// injetado, um `visibilitychange` que para de chegar ou um carimbo esquecido em Alpha 3.0 não
// mudam nada na tela — só aparecem meses depois, como "o carro ficou mais lento" ou "não sei qual
// versão está instalada".

import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

// Depois da fusão dos dois apps o `assets/www` do launcher mora DENTRO do repositório
// (app/src/main/assets/www) e é ele que vai para o APK — então o padrão passou a ser esse, e
// estas verificações deixaram de ser opcionais. VIEWER_SRC continua valendo para apontar para
// uma pasta descompactada de fora, e o "PULADO" segue existindo para esse caso.
const viewerSrc = process.env.VIEWER_SRC || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main')
const clientPath = path.join(viewerSrc, 'assets', 'www', 'deepGwtClient.js')
if (!fs.existsSync(clientPath)) {
  console.log(`PULADO: launcher 3D não encontrado em ${viewerSrc}`)
  process.exit(0)
}

const src = fs.readFileSync(clientPath, 'utf8')
// O bloco vai do carimbo até o primeiro ponto de chamada: só declarações e uma atribuição em
// `window`. O resto do arquivo fala com DOM e rede e não roda fora do WebView.
const start = src.indexOf('  var LAUNCHER_BUILD =')
const end = src.indexOf('  installGpuHints();')
if (start < 0 || end < 0) {
  console.log('FALHOU: não achei o bloco de desempenho em deepGwtClient.js')
  process.exit(1)
}
const bloco = src.slice(start, end)

let falhas = 0
const eq = (nome, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
  else console.log(`ok  ${nome}`)
}

// ── Ambiente falso: canvas, documento e janela ────────────────────────────────────────────────
let ultimoPedido = null
class FakeCanvas {
  getContext(tipo, atributos) { ultimoPedido = { tipo, atributos }; return { marcador: 'ctx' } }
}
const ouvintes = {}
const documento = {
  hidden: false,
  addEventListener(evento, fn) { (ouvintes[evento] ||= []).push(fn) },
}
const disparar = (evento) => (ouvintes[evento] || []).forEach((fn) => fn())

const window = {
  devicePixelRatio: 1,
  requestAnimationFrame(cb) { return 1 },
  cancelAnimationFrame() {},
}
globalThis.window = window
globalThis.document = documento
globalThis.HTMLCanvasElement = FakeCanvas
globalThis.console = console

const api = new Function(
  'window', 'document', 'HTMLCanvasElement',
  bloco + '\n return { installGpuHints, installHiddenThrottle, installVidroLeve, zerarClearcoatResidual, aliviarMemoria, CLASSE_VIDRO_LEVE, LAUNCHER_BUILD, MIN_FRAME_MS, HIDDEN_FRAME_MS, budget: () => frameBudgetMs };'
)(window, documento, FakeCanvas)

// ── Carimbo de versão ─────────────────────────────────────────────────────────────────────────
// Formato de VERSIONAMENTO.md: "Alpha <n>.<n>.<n> <DDMMAAAAHHMM>".
const formato = /^Alpha \d+\.\d+\.\d+ \d{12}$/
eq('o carimbo da camada segue o formato do versionamento', formato.test(api.LAUNCHER_BUILD), true)
eq('e ele é publicado em window', window.__deepGwtLauncherBuild, api.LAUNCHER_BUILD)
const [, dd, mm] = api.LAUNCHER_BUILD.match(/ (\d{2})(\d{2})\d{4}\d{4}$/) || []
eq('o dia do carimbo é um dia', Number(dd) >= 1 && Number(dd) <= 31, true)
eq('o mês do carimbo é um mês', Number(mm) >= 1 && Number(mm) <= 12, true)

// ── Dica de GPU ───────────────────────────────────────────────────────────────────────────────
api.installGpuHints()
const canvas = new FakeCanvas()

canvas.getContext('webgl2', { antialias: true, alpha: true, preserveDrawingBuffer: false })
eq('pede o perfil de desempenho', ultimoPedido.atributos.powerPreference, 'high-performance')
eq('não aceita cair para software', ultimoPedido.atributos.failIfMajorPerformanceCaveat, false)
eq('não mexe no antialias de quem chamou', ultimoPedido.atributos.antialias, true)
eq('nem no alpha', ultimoPedido.atributos.alpha, true)
eq('nem no preserveDrawingBuffer', ultimoPedido.atributos.preserveDrawingBuffer, false)

canvas.getContext('webgl', { powerPreference: 'low-power' })
eq('quem declarou de verdade continua mandando', ultimoPedido.atributos.powerPreference, 'low-power')

// O three.js SEMPRE preenche `powerPreference: 'default'` ao repassar os parâmetros. Enquanto
// 'default' era tratado como declaração, a dica nunca chegava ao driver — foi o que se mediu no
// contexto real do launcher antes desta correção.
canvas.getContext('webgl2', { powerPreference: 'default', antialias: true })
eq("'default' é silêncio, não escolha", ultimoPedido.atributos.powerPreference, 'high-performance')

canvas.getContext('2d', { alpha: false })
eq('contexto 2d passa intacto', ultimoPedido.atributos, { alpha: false })

api.installGpuHints()
canvas.getContext('webgl2', {})
eq('instalar duas vezes não empilha embrulhos', ultimoPedido.atributos.powerPreference, 'high-performance')

// ── Freio com a página escondida ──────────────────────────────────────────────────────────────
api.installHiddenThrottle()
eq('com a página visível vale o orçamento normal', api.budget(), api.MIN_FRAME_MS)
documento.hidden = true
disparar('visibilitychange')
eq('escondida, o laço desacelera', api.budget(), api.HIDDEN_FRAME_MS)
eq('e o freio é mais lento que o normal', api.HIDDEN_FRAME_MS > api.MIN_FRAME_MS, true)
documento.hidden = false
disparar('visibilitychange')
eq('voltando à vista, volta ao normal', api.budget(), api.MIN_FRAME_MS)

// ── Vidro sem desfoque (Alpha 8.9) ────────────────────────────────────────────────────────────
// Só no Android (`?android`, o mesmo critério do `_perfMobile` do bundle) e só o desfoque: a regra
// não pode tocar em cor de fundo, que vem das variáveis de tema.
const classes = new Set()
const anexados = []
documento.documentElement = { classList: { add: (c) => classes.add(c), contains: (c) => classes.has(c) }, appendChild: (n) => anexados.push(n) }
documento.head = { appendChild: (n) => anexados.push(n) }
documento.createElement = () => ({})
const armazenado = {}
globalThis.localStorage = { getItem: (k) => (k in armazenado ? armazenado[k] : null) }
window.localStorage = globalThis.localStorage

globalThis.location = { search: '', hash: '' }
api.installVidroLeve()
eq('fora do Android o desfoque fica', classes.has(api.CLASSE_VIDRO_LEVE), false)

globalThis.location = { search: '?android&assets=x', hash: '' }
armazenado.deepgwt_vidro_fosco = '1'
api.installVidroLeve()
eq('a chave de comparação devolve o vidro fosco', classes.has(api.CLASSE_VIDRO_LEVE), false)

delete armazenado.deepgwt_vidro_fosco
api.installVidroLeve()
eq('no Android o desfoque sai', classes.has(api.CLASSE_VIDRO_LEVE), true)
const regra = (anexados[anexados.length - 1] || {}).textContent || ''
eq('a regra desliga backdrop-filter', /backdrop-filter:none!important/.test(regra), true)
eq('e não mexe em cor de fundo', /background/.test(regra), false)

// ── Clearcoat invisível ───────────────────────────────────────────────────────────────────────
const mat = (name, clearcoat) => ({ name, clearcoat, isMeshPhysicalMaterial: true, needsUpdate: false })
const mats = { metal: mat('Brushed metal', 0.001), pintura: mat('Body', 1), freio: mat('Break', 0.25), padrao: { name: 'Plastic', clearcoat: 0.001 } }
const cena = { traverse(fn) { [mats.metal, mats.pintura, mats.freio, [mats.padrao, mats.metal]].forEach((m) => fn({ isMesh: true, material: m })) } }
const rel = { clearcoatZerado: [] }
api.zerarClearcoatResidual(cena, rel)
eq('clearcoat 0,001 vira 0', mats.metal.clearcoat, 0)
eq('e o material é recompilado', mats.metal.needsUpdate, true)
eq('a pintura continua com verniz', mats.pintura.clearcoat, 1)
eq('o disco de freio também', mats.freio.clearcoat, 0.25)
eq('material que não é físico não é tocado', mats.padrao.clearcoat, 0.001)
eq('cada material entra uma vez no relatório', rel.clearcoatZerado.length, 1)

// ── Alívio de memória ─────────────────────────────────────────────────────────────────────────
let miniaturasSoltas = 0, listasSoltas = 0
window.__app = { _thumbRenderer: {}, _disposeThumbRenderer() { miniaturasSoltas++; this._thumbRenderer = null } }
window.__renderer = { renderLists: { dispose() { listasSoltas++ } } }
window.__deepGwtPerf = { alivios: 0 }
api.aliviarMemoria(20)
api.aliviarMemoria(20)
eq('solta o renderizador das miniaturas quando ele existe', miniaturasSoltas, 1)
eq('e as listas de render a cada aviso', listasSoltas, 2)
eq('e conta os avisos no relatório', window.__deepGwtPerf.alivios, 2)
eq('e fica publicado para o lado nativo', typeof window.__deepGwtAliviarMemoria, 'function')

console.log(falhas ? `\n${falhas} FALHA(S)` : '\nTodas passaram')
process.exit(falhas ? 1 : 0)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
