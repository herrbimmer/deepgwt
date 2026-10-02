#!/usr/bin/env node
/**
 * O ponteiro de segundos do Modo Descanso VARRE a 24 Hz.
 *
 * QUAL ERA O PROBLEMA
 * O relógio acordava uma vez por segundo e escrevia o ângulo a partir de `getSeconds()`, que é um
 * degrau: o ponteiro pulava de traço em traço, como relógio de parede barato. O pedido é a
 * varredura contínua de um relógio mecânico bom.
 *
 * O QUE ESTE ARQUIVO FAZ
 * Não confere isso por expressão regular. Ele CARREGA o openGwRest.js num DOM de mentira, com um
 * `requestAnimationFrame` e um relógio que este teste controla, entra no Modo Descanso e conta o
 * que foi escrito em cada nó. É a única forma de provar as três coisas que importam ao mesmo
 * tempo:
 *
 *   1. o ponteiro de segundos se move em TODO quadro — 24 posições distintas por segundo,
 *      não uma;
 *   2. hora, minuto e a deriva anti-retenção continuam sendo escritos UMA vez por segundo — o
 *      custo de 24 Hz é um atributo por quadro, não o mostrador inteiro 24 vezes;
 *   3. sair do modo PARA o laço. Um laço de rAF que não é cancelado fica girando para sempre atrás
 *      de uma tela que o usuário já fechou, e numa central isso é bateria e calor de graça.
 *
 * A cadência de 24 Hz em si não é imposta aqui dentro: ela vem do orçamento de quadros instalado
 * em openGwClient.js, que já limita o `requestAnimationFrame` da página inteira. O que este
 * arquivo garante é que o relógio ANDE POR QUADRO, que é o que faz a varredura acompanhar esse
 * orçamento em vez de ignorá-lo.
 *
 * Uso: node scripts/test-relogio-descanso.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createContext, runInContext } from 'node:vm'

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const WWW = join(RAIZ, 'app', 'src', 'main', 'assets', 'www')

let falhas = 0
let total = 0

function ok(descricao, condicao, detalhe) {
  total++
  if (condicao) console.log('ok  ' + descricao)
  else {
    falhas++
    console.log('FALHOU  ' + descricao + (detalhe ? '\n        ' + detalhe : ''))
  }
}

const fonte = existsSync(join(WWW, 'openGwRest.js'))
  ? readFileSync(join(WWW, 'openGwRest.js'), 'utf8')
  : ''
if (!fonte) {
  console.log('openGwRest.js não encontrado.')
  process.exit(1)
}

// ── DOM de mentira ──────────────────────────────────────────────────────────────────────────
//
// Só o que o arquivo realmente toca. Cada nó guarda o histórico de `setAttribute` para que o
// teste possa contar escritas por atributo, que é a medida que interessa.

function montarAmbiente(inicioMs) {
  const escritas = []

  function criarNo(tag) {
    const no = {
      tag,
      filhos: [],
      atributos: {},
      style: {},
      classList: {
        _set: new Set(),
        add(c) { this._set.add(c) },
        remove(c) { this._set.delete(c) },
        contains(c) { return this._set.has(c) }
      },
      setAttribute(nome, valor) {
        this.atributos[nome] = String(valor)
        escritas.push({ no: this, nome, valor: String(valor) })
      },
      getAttribute(nome) { return this.atributos[nome] },
      appendChild(f) { this.filhos.push(f); return f },
      removeChild(f) { this.filhos = this.filhos.filter((x) => x !== f) },
      addEventListener() {},
      closest() { return null },
      remove() {}
    }
    return no
  }

  const doc = {
    readyState: 'complete',
    head: criarNo('head'),
    body: criarNo('body'),
    documentElement: criarNo('html'),
    createElement: (t) => criarNo(t),
    createElementNS: (_ns, t) => criarNo(t),
    getElementById: () => null,
    addEventListener() {},
    removeEventListener() {}
  }

  // Relógio controlado: `new Date()` dentro do arquivo tem de enxergar o tempo deste teste.
  let agora = inicioMs
  class DataFalsa extends Date {
    constructor(...args) {
      if (args.length === 0) super(agora)
      else super(...args)
    }
    static now() { return agora }
  }

  const quadros = []
  const win = {
    document: doc,
    Date: DataFalsa,
    Math,
    String,
    Number,
    console,
    setTimeout: () => 0,
    clearTimeout: () => {},
    setInterval: () => 0,
    clearInterval: () => {},
    requestAnimationFrame(cb) { quadros.push(cb); return quadros.length },
    cancelAnimationFrame(id) { quadros[id - 1] = null }
  }
  win.window = win

  const ctx = createContext(win)
  runInContext(fonte, ctx)

  return {
    api: () => win.OpenGwRest,
    escritas,
    /** Avança o relógio e entrega um quadro, como o navegador faria. */
    passarQuadro(deltaMs) {
      agora += deltaMs
      const pendentes = quadros.splice(0, quadros.length).filter(Boolean)
      pendentes.forEach((cb) => cb(agora))
      return pendentes.length
    },
    quadrosArmados: () => quadros.filter(Boolean).length
  }
}

// ── O teste ─────────────────────────────────────────────────────────────────────────────────

// Começa exatamente numa virada de segundo, para o primeiro quadro não cair num limite ambíguo.
const INICIO = Date.UTC(2026, 8, 17, 12, 0, 0, 0)
const PASSO = 1000 / 24

const amb = montarAmbiente(INICIO)
const api = amb.api()

ok('o Modo Descanso se expõe em window.OpenGwRest', !!api && typeof api.enter === 'function')

api.enter()
ok('entrar arma um quadro', amb.quadrosArmados() === 1, 'sem rAF armado o relógio não anda')

// Um segundo inteiro de quadros.
amb.escritas.length = 0
for (let i = 0; i < 24; i++) amb.passarQuadro(PASSO)

function transformesDe(indiceDoNo) {
  // Os ponteiros são criados em ordem: hora, minuto, segundo (ver buildFace).
  const nosComTransform = []
  for (const e of amb.escritas) {
    if (e.nome !== 'transform') continue
    if (!nosComTransform.includes(e.no)) nosComTransform.push(e.no)
  }
  return nosComTransform[indiceDoNo]
}

const porNo = new Map()
for (const e of amb.escritas) {
  if (e.nome !== 'transform') continue
  if (!porNo.has(e.no)) porNo.set(e.no, [])
  porNo.get(e.no).push(e.valor)
}

const series = [...porNo.values()]
const maisEscrito = series.reduce((a, b) => (a.length >= b.length ? a : b), [])
const valoresDistintos = new Set(maisEscrito).size

ok(
  'algum nó é reescrito a cada quadro',
  maisEscrito.length >= 24,
  `o nó mais escrito recebeu ${maisEscrito.length} transformes em 24 quadros — o ponteiro de segundos tem de andar em todos`
)
ok(
  'e cada quadro é uma POSIÇÃO diferente, não o mesmo ângulo repetido',
  valoresDistintos >= 24,
  `${valoresDistintos} posições distintas em 24 quadros: o ponteiro está pulando de degrau, não varrendo`
)

// Os ponteiros lentos: no máximo uma escrita por segundo cada.
const lentos = series.filter((s) => s !== maisEscrito)
ok(
  'hora, minuto e deriva continuam a 1 Hz',
  lentos.length > 0 && lentos.every((s) => s.length <= 2),
  `um nó lento recebeu ${Math.max(0, ...lentos.map((s) => s.length))} escritas num segundo — 24 Hz não pode custar o mostrador inteiro`
)

// A varredura anda no sentido certo e dá a volta completa.
const angulo = (t) => Number(String(t).match(/rotate\(([-\d.]+)/)[1])
ok(
  'a varredura avança no sentido do relógio',
  angulo(maisEscrito[1]) > angulo(maisEscrito[0]) &&
    angulo(maisEscrito[maisEscrito.length - 1]) > angulo(maisEscrito[0])
)
ok(
  'um segundo inteiro vale 6 graus, como num relógio',
  Math.abs((angulo(maisEscrito[maisEscrito.length - 1]) - angulo(maisEscrito[0])) - 6) < 0.6,
  'o ângulo tem de sair dos milissegundos; de getSeconds() ele daria saltos de 6 em 6'
)

// Sair tem de PARAR o laço.
api.exit()
amb.passarQuadro(PASSO)
ok(
  'sair do Modo Descanso para o laço',
  amb.quadrosArmados() === 0,
  'um rAF que continua rearmando depois de sair gira para sempre atrás de uma tela fechada'
)

// ── Verificações de fonte, para o que a execução não alcança ────────────────────────────────

ok(
  'o laço usa requestAnimationFrame, não um intervalo de 41 ms',
  /window\.requestAnimationFrame\(passo\)/.test(fonte) && !/setInterval\(drawNow/.test(fonte),
  'um intervalo solto não cai em cima do quadro e a varredura sai irregular apesar da média certa'
)
ok(
  'o ângulo do ponteiro de segundos sai dos milissegundos',
  /getMilliseconds\(\)/.test(fonte)
)
ok(
  'o desenho lento só acontece na virada do segundo',
  /if \(s !== ultimoSegundoDesenhado\)/.test(fonte)
)
ok(
  'o cancelamento passa pelo cancelAnimationFrame',
  /cancelAnimationFrame\(ticking\)/.test(fonte)
)

console.log('')
if (falhas > 0) {
  console.log(`${total} verificações, ${falhas} falharam.`)
  process.exit(1)
}
console.log(`${total} verificações, todas passaram.`)
