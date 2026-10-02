// Orçamento de desempenho do launcher 3D: teto de 24 fps e render a 90% da resolução.
//
// Roda com `node scripts/test-viewer-frame-budget.mjs`. A pasta do launcher é a mesma que
// sincronizar-apk.sh usa (VIEWER_SRC); sem ela, o teste se declara pulado em vez de falhar — o
// launcher não é compilado a partir deste repositório.
//
// POR QUE ESTE TESTE EXISTE: a primeira versão do limitador entregava 20 fps num painel de 60 Hz,
// não 24, porque contava o orçamento a partir do instante do quadro anterior em vez do alvo. Num
// carro ninguém mede isso a olho; aqui a conta aparece.

import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

// Depois da fusão dos dois apps o `assets/www` do launcher mora DENTRO do repositório
// (app/src/main/assets/www) e é ele que vai para o APK — então o padrão passou a ser esse, e
// estas verificações deixaram de ser opcionais. VIEWER_SRC continua valendo para apontar para
// uma pasta descompactada de fora, e o "PULADO" segue existindo para esse caso.
const viewerSrc = process.env.VIEWER_SRC || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main')
const clientPath = path.join(viewerSrc, 'assets', 'www', 'openGwClient.js')
if (!fs.existsSync(clientPath)) {
  console.log(`PULADO: launcher 3D não encontrado em ${viewerSrc}`)
  process.exit(0)
}

const src = fs.readFileSync(clientPath, 'utf8')
// Só o bloco de orçamento de quadros: o resto do arquivo fala com DOM e rede.
const start = src.indexOf('  var TARGET_FPS = 24;')
const end = src.indexOf('  installFrameBudget();')
const bloco = src.slice(start, end) + '\n installFrameBudget(); installRenderScale();'

// rAF falso rodando a 60 Hz (16.667 ms por quadro).
let clock = 0
let queue = []
const window = {
  devicePixelRatio: 1,
  requestAnimationFrame(cb) { const id = queue.length + 1; queue.push({ id, cb }); return id },
  cancelAnimationFrame(id) { queue = queue.filter((q) => q.id !== id) },
}
globalThis.window = window
globalThis.console = console
new Function('window', bloco)(window)

const chamadas = []
let ativo = true
function laco(t) {
  chamadas.push(t)
  if (ativo) window.requestAnimationFrame(laco)
}
window.requestAnimationFrame(laco)

// 60 quadros de hardware = 1 segundo.
for (let i = 0; i < 60; i++) {
  clock += 1000 / 60
  const atual = queue
  queue = []
  for (const q of atual) q.cb(clock)
}
ativo = false

let falhas = 0
const eq = (nome, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
  else console.log(`ok  ${nome}`)
}

// 60 quadros de hardware em 1 s devem render no máximo 24 chamadas, e não muito menos.
eq('não passa de 24 quadros por segundo', chamadas.length <= 24, true)
eq('média fecha em 24 (teto pedido)', chamadas.length, 24)
console.log(`    (${chamadas.length} quadros entregues em 1 s de relógio)`)

const intervalos = chamadas.slice(1).map((t, i) => t - chamadas[i])
const minimo = Math.min(...intervalos)
// Num painel de 60 Hz a cadência alterna 33/50 ms; o que importa é a MÉDIA respeitar o teto.
const media = intervalos.reduce((a, b) => a + b, 0) / intervalos.length
eq('média do intervalo >= orçamento de 24 fps', media >= 1000 / 24 - 0.6, true)
eq('nenhum intervalo mais curto que 2 quadros de hardware', minimo >= 1000 / 60 * 2 - 0.5, true)

// Cancelar tem que impedir a chamada, mesmo com o quadro re-armado esperando.
let chamou = false
const id = window.requestAnimationFrame(() => { chamou = true })
window.cancelAnimationFrame(id)
for (let i = 0; i < 10; i++) {
  clock += 1000 / 60
  const atual = queue
  queue = []
  for (const q of atual) q.cb(clock)
}
eq('cancelAnimationFrame cancela', chamou, false)

// DOIS CONSUMIDORES AO MESMO TEMPO — a regressão que o emulador pegou.
//
// A cadência é uma só para a página. O laço de render está sempre primeiro na fila do quadro,
// passa, empurra o alvo e re-arma; um segundo consumidor avaliado logo depois no MESMO quadro via
// a cadência recém-consumida e re-armava para sempre. No emulador isso deu 0 quadros entregues em
// 90 segundos enquanto o rAF nativo entregava 60 por segundo — uma animação de widget congelada
// sem nenhum erro no console. O teto é por consumidor, não para a página somada.
const primario = []
const secundario = []
let rodando = true
function lacoPrimario(t) { primario.push(t); if (rodando) window.requestAnimationFrame(lacoPrimario) }
function lacoSecundario(t) { secundario.push(t); if (rodando) window.requestAnimationFrame(lacoSecundario) }
// O primário entra primeiro de propósito: é ele que ganha a corrida da fila.
window.requestAnimationFrame(lacoPrimario)
window.requestAnimationFrame(lacoSecundario)
for (let i = 0; i < 120; i++) {
  clock += 1000 / 60
  const atual = queue
  queue = []
  for (const q of atual) q.cb(clock)
}
rodando = false
eq('consumidor primário continua recebendo quadros', primario.length >= 40, true)
// O orçamento libera QUADROS, não pedidos: quem estiver esperando entra no mesmo quadro. Então o
// segundo consumidor não recebe "algum" quadro — recebe exatamente os mesmos que o primeiro.
eq('consumidor secundário recebe os mesmos quadros que o primário', secundario.length, primario.length)
// `every` numa lista vazia devolve true — sem o length o teste passaria de graça justo no caso
// que ele existe para pegar.
eq('e nos mesmos instantes', secundario.length > 0 && secundario.every((t, i) => t === primario[i]), true)
const intervalosSec = secundario.slice(1).map((t, i) => t - secundario[i])
const piorEspera = intervalosSec.length ? Math.max(...intervalosSec) : Infinity
// Num painel de 60 Hz a cadência de 24 fps alterna 33/50 ms; nada pode esperar mais que isso.
eq('pior espera do secundário é a própria cadência', piorEspera <= 1000 / 60 * 3 + 0.5, true)

// devicePixelRatio reduzido em 10%.
eq('resolução a 90%', Math.round(window.devicePixelRatio * 1000) / 1000, 0.9)

console.log(falhas ? `\n${falhas} FALHA(S)` : '\nTodas passaram')
process.exit(falhas ? 1 : 0)
