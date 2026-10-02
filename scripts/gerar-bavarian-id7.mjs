// Geometria do Bavarian ID7 (Alpha 6.9): aro, faixa vermelha, marcações, números, guias dos
// ponteiros, tabelas valor->fração e mini-mostradores. Tudo sai daqui e é escrito no app.html,
// entre os marcadores GERADO. Mexeu num número abaixo, rode de novo:
//
//   node scripts/gerar-bavarian-id7.mjs              reescreve os dois blocos do app.html
//   node scripts/gerar-bavarian-id7.mjs --verificar  sai com 1 se o app.html não bate com o gerador
//   node scripts/gerar-bavarian-id7.mjs --relatorio  imprime as posições calculadas
//
// DE ONDE VÊM OS NÚMEROS
//
// O contorno (RIM) foi medido na foto de referência (painel BMW de 875x350 px) varrendo o pixel
// claro do aro e convertendo para 1920x720. O velocímetro e o mostrador da direita são o mesmo
// desenho espelhado (x -> 1920 - x), como na referência.
//
// A escala segue a regra do painel de referência, conferida marcação a marcação na foto: oito
// posições numeradas ao longo do aro, as MESMAS nos dois mostradores. O conta-giros da foto tem
// 0..7 nelas e o velocímetro tem 0/20/40/60/100/140/200/260 — por isso a escala de velocidade é
// desigual (larga embaixo, apertada em cima) e a de potência, 0..140 kW, é linear. As posições
// (NUMEROS_MEDIDOS) são a média do número do velocímetro com o do conta-giros espelhado, medidos
// na foto. Há marcação branca em cada número e no meio de cada trecho (10, 30, 50, 80, 120, 170,
// 230 km/h), e quatro traços apagados entre duas brancas.
//
// O ponteiro não gira em volta de um eixo fixo: o aro não é um círculo. A ponta externa corre
// sobre o guia (aro recuado 3 px) e o ponteiro aponta para o PIVÔ, que fica no miolo, atrás da
// velocidade digital. Na página isso é um único transform (translate + rotate) num grupo com
// will-change — o compositor move, nada é redesenhado.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const ARQUIVO = path.join(raiz, 'app/src/main/assets/PreinstalledThemes/bavarianid7/app.html')

// ── Medidas ─────────────────────────────────────────────────────────────────────────────────
// Aro do velocímetro, do fim da linha de base (direita) até a ponta de cima, em px de 1920x720.
const RIM = [
  [742, 614], [700, 614], [600, 614], [500, 614], [420, 614], [362, 613.5], [329, 613],
  [296, 609], [263, 595], [240, 574], [217, 551], [197, 527], [180, 502], [162, 477],
  [147, 453], [136, 428], [116, 391], [108, 370], [103, 350], [99, 329], [101, 309],
  [105, 288], [116, 267], [132, 247], [149, 226], [171, 206], [195, 185], [222, 165],
  [250, 144], [281, 123], [316, 103], [329, 97], [362, 84], [395, 76], [428, 70], [461, 66],
  [494, 62], [527, 58], [560, 56], [592, 51], [625, 49], [658, 47], [691, 45], [716, 44],
]
const ZERO_X = 381            // onde o ponteiro cruza a linha de base no zero
const REGEN_X = 531           // fim da faixa de regeneração (espelhado: 1389 no mostrador direito)
const PIVO = { x: 456, y: 347 }
const PONTEIRO = 175          // comprimento do ponteiro, da ponta no aro para dentro
const GUIA_RECUO = 3
// Centro dos números 0..7 na foto (média velocímetro / conta-giros espelhado), em 1920x720.
const NUMEROS_MEDIDOS = [[386, 564], [280, 534], [203, 438], [156, 331], [243, 221], [366, 143],
  [524, 108], [699, 95]]
const TRACOS_POR_DIVISAO = 5  // 4 traços apagados entre duas marcações brancas
const VELOCIDADES = [0, 20, 40, 60, 100, 140, 200, 260]
const POTENCIAS = [0, 20, 40, 60, 80, 100, 120, 140]
const REGEN_MAX = 100

// Recuo da faixa vermelha: mais junto do aro em cima, mais afastado nas laterais (medido na foto).
function recuoFaixa(n) { return 60 - 18 * Math.max(0, n[1]) - 15 * Math.max(0, -n[1]) }
const RECUO_NUMERO = 8        // além da faixa: o número senta logo para dentro dela

// Mini-mostrador do combustível (o da bateria é o espelho): borda externa, de cima para baixo.
const BORDA_MINI = [[62, 417], [44, 418], [30, 431], [22, 451], [20, 472], [22, 494], [30, 510],
  [44, 527], [60, 548], [78, 575], [101, 596], [127, 608], [150, 609], [178, 609]]
// Lado de dentro do painel (junto do aro do velocímetro), de baixo para cima.
const PAINEL_DENTRO = [[186, 601], [176, 578], [165, 552], [154, 525], [145, 498], [138, 470],
  [132, 446], [128, 424]]
const NIVEL = { cheio: 424, vazio: 601 }

// ── Curvas ──────────────────────────────────────────────────────────────────────────────────
const sub = (a, b) => [a[0] - b[0], a[1] - b[1]]
const len = (v) => Math.hypot(v[0], v[1])

function catmullRom(P) {
  const segs = []
  for (let i = 0; i < P.length - 1; i++) {
    const p0 = P[Math.max(0, i - 1)], p1 = P[i], p2 = P[i + 1], p3 = P[Math.min(P.length - 1, i + 2)]
    segs.push([p1,
      [p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6],
      [p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6],
      p2])
  }
  return segs
}
function bezier(s, t) {
  const u = 1 - t
  return [0, 1].map((k) => u * u * u * s[0][k] + 3 * u * u * t * s[1][k] + 3 * u * t * t * s[2][k] + t * t * t * s[3][k])
}
function amostrar(segs, n = 60) {
  const out = [segs[0][0]]
  for (const s of segs) for (let i = 1; i <= n; i++) out.push(bezier(s, i / n))
  return out
}
function reamostrar(pts, passo) {
  const cum = [0]
  for (let i = 1; i < pts.length; i++) cum.push(cum[i - 1] + len(sub(pts[i], pts[i - 1])))
  const total = cum[cum.length - 1]
  const n = Math.max(1, Math.round(total / passo))
  const out = []
  let j = 0
  for (let k = 0; k <= n; k++) {
    const alvo = (total * k) / n
    while (j < pts.length - 2 && cum[j + 1] < alvo) j++
    const t = (alvo - cum[j]) / (cum[j + 1] - cum[j] || 1)
    out.push([pts[j][0] + (pts[j + 1][0] - pts[j][0]) * t, pts[j][1] + (pts[j + 1][1] - pts[j][1]) * t])
  }
  return out
}
function suavizar(pts, janela, vezes) {
  let p = pts
  for (let v = 0; v < vezes; v++) {
    const q = p.map((x) => x.slice())
    for (let i = 1; i < p.length - 1; i++) {
      const a = Math.max(0, i - janela), b = Math.min(p.length - 1, i + janela)
      let sx = 0, sy = 0
      for (let k = a; k <= b; k++) { sx += p[k][0]; sy += p[k][1] }
      q[i] = [sx / (b - a + 1), sy / (b - a + 1)]
    }
    p = q
  }
  return p
}
// Normal para dentro. `lado` = +1 quando o percurso deixa o miolo à esquerda de quem anda.
function normais(pts, lado) {
  return pts.map((_, i) => {
    const a = pts[Math.max(0, i - 3)], b = pts[Math.min(pts.length - 1, i + 3)]
    const t = sub(b, a), l = len(t) || 1
    return lado > 0 ? [-t[1] / l, t[0] / l] : [t[1] / l, -t[0] / l]
  })
}
const f1 = (v) => (Math.round(v * 10) / 10).toString()
function caminho(pts, passo = 10) {
  const P = reamostrar(pts, passo).map((p) => [Number(f1(p[0])), Number(f1(p[1]))])
  const segs = catmullRom(P)
  let d = `M ${f1(P[0][0])} ${f1(P[0][1])}`
  for (const s of segs) d += ` C ${f1(s[1][0])} ${f1(s[1][1])} ${f1(s[2][0])} ${f1(s[2][1])} ${f1(s[3][0])} ${f1(s[3][1])}`
  // O mesmo traçado, com os números JÁ arredondados como o navegador vai ler, para as frações.
  const lido = segs.map((s) => s.map((p) => [Number(f1(p[0])), Number(f1(p[1]))]))
  return { d, amostras: amostrar(lido, 60) }
}
function fracaoNoCaminho(amostras, alvo) {
  let cum = 0, melhor = Infinity, fr = 0
  const acum = [0]
  for (let i = 1; i < amostras.length; i++) acum.push(acum[i - 1] + len(sub(amostras[i], amostras[i - 1])))
  const total = acum[acum.length - 1]
  for (let i = 0; i < amostras.length; i++) {
    const d = len(sub(amostras[i], alvo))
    if (d < melhor) { melhor = d; cum = acum[i] }
  }
  fr = cum / total
  return { fr, erro: melhor }
}
const linhas = (segmentos) => segmentos.map(([a, b]) => `M ${f1(a[0])} ${f1(a[1])} L ${f1(b[0])} ${f1(b[1])}`).join(' ')

// ── Aro base (velocímetro) ──────────────────────────────────────────────────────────────────
const denso = reamostrar(amostrar(catmullRom(RIM), 40), 1)
const aro = reamostrar(suavizar(denso, 6, 3), 1)
const N = normais(aro, +1)
const acum = [0]
for (let i = 1; i < aro.length; i++) acum.push(acum[i - 1] + len(sub(aro[i], aro[i - 1])))
function indiceNaBase(x) { // primeiro ponto da linha de base com esse x (a base vem primeiro no percurso)
  let melhor = 0, d = Infinity
  for (let i = 0; i < aro.length; i++) {
    if (aro[i][1] < 600) break
    const e = Math.abs(aro[i][0] - x)
    if (e < d) { d = e; melhor = i }
  }
  return melhor
}
const i0 = indiceNaBase(ZERO_X)
const iRegen = indiceNaBase(REGEN_X)
const iFim = aro.length - 1
const s0 = acum[i0], sFim = acum[iFim]
function indiceEm(s) { let i = 0; while (i < acum.length - 1 && acum[i] < s) i++; return i }
const em = (i, d) => [aro[i][0] + N[i][0] * d, aro[i][1] + N[i][1] * d]
// Arco de cada número: o ponto da curva dos números mais perto da medida. O zero é onde o
// ponteiro cruza a base; o último é a ponta do aro.
const sNumeros = NUMEROS_MEDIDOS.map((alvo, k) => {
  if (k === 0) return s0
  if (k === NUMEROS_MEDIDOS.length - 1) return sFim
  let melhor = i0, d = Infinity
  for (let i = i0; i <= iFim; i++) {
    const e = len(sub(em(i, recuoFaixa(N[i]) + RECUO_NUMERO), alvo))
    if (e < d) { d = e; melhor = i }
  }
  return acum[melhor]
})
// Marcações brancas: cada número e o meio de cada trecho. Apagadas: 4 entre duas brancas.
const sBrancos = []
for (let k = 0; k < sNumeros.length - 1; k++) sBrancos.push(sNumeros[k], (sNumeros[k] + sNumeros[k + 1]) / 2)
sBrancos.push(sFim)
const indicesDivisao = sBrancos.map(indiceEm)
const indicesTracos = []
for (let k = 0; k < sBrancos.length - 1; k++) {
  for (let m = 1; m < TRACOS_POR_DIVISAO; m++) {
    indicesTracos.push(indiceEm(sBrancos[k] + ((sBrancos[k + 1] - sBrancos[k]) * m) / TRACOS_POR_DIVISAO))
  }
}
const esp = (p) => [1920 - p[0], p[1]]
const espD = (pts) => pts.map(esp)

// Faixa vermelha (do zero até o fim), guia do ponteiro e números — no velocímetro.
const faixa = []
for (let i = i0; i <= iFim; i++) faixa.push(em(i, recuoFaixa(N[i])))
const posNumero = (i) => em(i, recuoFaixa(N[i]) + RECUO_NUMERO)
const guiaVel = []
for (let i = i0; i <= iFim; i++) guiaVel.push(em(i, GUIA_RECUO))
// A potência começa antes do zero: a base, do lado de dentro dele, é a faixa de regeneração.
const guiaPot = []
for (let i = iRegen; i <= iFim; i++) guiaPot.push(esp(em(i, GUIA_RECUO)))

const tracoBranco = (i) => [em(i, 1.5), em(i, 16)]
const tracoApagado = (i) => [em(i, 3), em(i, 11)]

function mostrador(espelhar) {
  const X = espelhar ? espD : (p) => p
  const P = espelhar ? esp : (p) => p
  const aroD = caminho(X(aro.slice(0, iFim + 1)), 12).d
  const faixaD = caminho(X(faixa), 10).d
  const brancos = linhas(indicesDivisao.map((i) => tracoBranco(i).map(P)))
  const apagados = linhas(indicesTracos.map((i) => tracoApagado(i).map(P)))
  // Gancho no fim da linha de base, virando para cima (como na referência).
  const g = [[742, 614], [752, 614], [758, 610], [761, 597]].map(P)
  const ganchoD = `M ${g[0][0]} ${g[0][1]} C ${g[1][0]} ${g[1][1]} ${g[2][0]} ${g[2][1]} ${g[3][0]} ${g[3][1]}`
  return { aroD, faixaD, brancos, apagados, ganchoD }
}
const vel = mostrador(false)
const pot = mostrador(true)
const guiaVelC = caminho(guiaVel, 10)
const guiaPotC = caminho(guiaPot, 10)

const tabela = (valores, amostras, guiaPts, idxs, espelhar) => valores.map((v, k) => {
  const alvo = espelhar ? esp(em(idxs[k], GUIA_RECUO)) : em(idxs[k], GUIA_RECUO)
  const { fr } = fracaoNoCaminho(amostras, alvo)
  return [v, Number(fr.toFixed(4))]
})
const idxNumeros = indicesDivisao.filter((_, k) => k % 2 === 0)
const SPEED_TABLE = tabela(VELOCIDADES, guiaVelC.amostras, guiaVel, idxNumeros, false)
SPEED_TABLE[0][1] = 0
SPEED_TABLE[SPEED_TABLE.length - 1][1] = 1
const POWER_TABLE = [[-REGEN_MAX, 0], ...tabela(POTENCIAS, guiaPotC.amostras, guiaPot, idxNumeros, true)]
POWER_TABLE[POWER_TABLE.length - 1][1] = 1

// Faixa de regeneração: barras verdes coladas no aro, na base, do lado de dentro do zero.
const regenPts = []
for (let i = iRegen + 2; i <= i0 - 4; i++) regenPts.push(esp(em(i, 7)))
const regenD = `M ${f1(regenPts[0][0])} ${f1(regenPts[0][1])} L ${f1(regenPts[regenPts.length - 1][0])} ${f1(regenPts[regenPts.length - 1][1])}`

// ── Mini-mostradores ────────────────────────────────────────────────────────────────────────
const bordaDensa = reamostrar(suavizar(reamostrar(amostrar(catmullRom(BORDA_MINI), 30), 1), 3, 2), 1)
const NB = normais(bordaDensa, -1)
const bordaEm = (d) => bordaDensa.map((p, i) => [p[0] + NB[i][0] * d, p[1] + NB[i][1] * d])
const bordaInterna = bordaEm(6).filter((p) => p[1] >= 422 && p[0] <= 170)
const painelFora = bordaEm(9).filter((p) => p[1] >= NIVEL.cheio && p[0] <= 186)
const painel = [...painelFora, ...PAINEL_DENTRO]
const reserva = bordaDensa.filter((p) => p[1] >= 586)
function mini(espelhar) {
  const X = espelhar ? espD : (p) => p
  const poli = (pts) => 'M ' + X(pts).map((p) => `${f1(p[0])} ${f1(p[1])}`).join(' L ') + ' Z'
  return {
    borda: caminho(X(bordaDensa), 6).d,
    interna: caminho(X(bordaInterna), 6).d,
    painel: poli(painel),
    reserva: caminho(X(reserva), 4).d,
    seps: [0.25, 0.5, 0.75].map((f) => NIVEL.vazio - f * (NIVEL.vazio - NIVEL.cheio))
      .map((y) => espelhar ? `M 1720 ${f1(y)} L 1920 ${f1(y)}` : `M 0 ${f1(y)} L 200 ${f1(y)}`).join(' '),
  }
}
const miniComb = mini(false)
const miniBat = mini(true)
// Gradiente do painel: claro junto da borda, some para dentro (perpendicular à borda média).
const gradComb = { x1: 40, y1: 520, x2: 140, y2: 478 }
const gradBat = { x1: 1880, y1: 520, x2: 1780, y2: 478 }

// ── Montagem ────────────────────────────────────────────────────────────────────────────────
// O último número fica na ponta do aro; recua um pouco ao longo da borda de cima para não passar dela.
const RECUO_ULTIMO = 16
const numero = (i, texto, espelhar, extra = '') => {
  let p = posNumero(i)
  if (i === iFim) p = [p[0] - RECUO_ULTIMO, p[1]]
  if (espelhar) p = esp(p)
  return `<text class="tick-label" x="${f1(p[0])}" y="${f1(p[1])}"${extra}>${texto}</text>`
}
const zeroVel = posNumero(idxNumeros[0])
const zeroPot = esp(posNumero(idxNumeros[0]))
const regenCentro = esp(em(Math.round((iRegen + i0) / 2), 26))
const pivoPot = { x: 1920 - PIVO.x, y: PIVO.y }

function dialSvg(nome, d, guia, idRot, idNeedle, numeros, extras) {
  return `    <g class="dial ${nome}">
      <path class="rim" d="${d.aroD}"></path>
      <path class="rim-hook" d="${d.ganchoD}"></path>
      <path class="band-glow" d="${d.faixaD}"></path>
      <path class="band" d="${d.faixaD}"></path>
      <path class="tick minor" d="${d.apagados}"></path>
      <path class="tick major" d="${d.brancos}"></path>
${extras.antes || ''}      <path class="guide" d="${guia}"></path>
      <g id="${idRot}" class="needle-rot">
        <ellipse class="needle-glow" cx="92" cy="0" rx="104" ry="78"></ellipse>
        <line class="needle-halo" x1="1" y1="0" x2="${PONTEIRO}" y2="0"></line>
        <line id="${idNeedle}" class="needle" x1="1" y1="0" x2="${PONTEIRO}" y2="0"></line>
      </g>
      <g class="tick-labels">
${numeros.map((t) => '        ' + t).join('\n')}
      </g>
${extras.depois || ''}    </g>`
}
function miniSvg(nome, m, grad, clipId, idClipRect, idMarcador, rotulos, icone) {
  const xRect = nome === 'fuel' ? 0 : 1720
  return `    <g class="side-gauge ${nome}">
      <defs>
        <linearGradient id="${nome}PanelGradient" gradientUnits="userSpaceOnUse" x1="${grad.x1}" y1="${grad.y1}" x2="${grad.x2}" y2="${grad.y2}">
          <stop offset="0%" style="stop-color: #c9ced6; stop-opacity: 0.42"></stop>
          <stop offset="100%" style="stop-color: #6d737d; stop-opacity: 0"></stop>
        </linearGradient>
        <clipPath id="${clipId}"><path d="${m.painel}"></path></clipPath>
        <clipPath id="${nome}LevelClip"><rect id="${idClipRect}" x="${xRect}" y="${NIVEL.cheio}" width="200" height="${NIVEL.vazio - NIVEL.cheio + 4}"></rect></clipPath>
      </defs>
      <path class="side-panel-empty" d="${m.painel}"></path>
      <g clip-path="url(#${nome}LevelClip)"><path class="side-panel" d="${m.painel}" fill="url(#${nome}PanelGradient)"></path></g>
      <path class="side-sep" clip-path="url(#${clipId})" d="${m.seps}"></path>
      <path class="side-border inner" d="${m.interna}"></path>
      <path class="side-border" d="${m.borda}"></path>
      <path class="side-reserve" d="${m.reserva}"></path>
      <g clip-path="url(#${clipId})"><g id="${idMarcador}" transform="translate(0 ${NIVEL.vazio})"><line class="side-marker-glow" x1="${xRect}" y1="0" x2="${xRect + 200}" y2="0"></line><line class="side-marker" x1="${xRect}" y1="0" x2="${xRect + 200}" y2="0"></line></g></g>
${rotulos.map((t) => '      ' + t).join('\n')}
${icone}
    </g>`
}

const svg = `    <!-- Gerado por scripts/gerar-bavarian-id7.mjs: não edite à mão, mude o gerador e rode de novo. -->
${dialSvg('speed-dial', vel, guiaVelC.d, 'needleSpeedRot', 'needleSpeed',
  idxNumeros.map((i, k) => numero(i, VELOCIDADES[k], false)),
  { depois: `      <text class="scale-unit" x="${f1(zeroVel[0] + 24)}" y="${f1(zeroVel[1] + 2)}">km/h</text>\n` })}
${dialSvg('power-dial', pot, guiaPotC.d, 'needlePowerRot', 'needlePower',
  idxNumeros.map((i, k) => numero(i, POTENCIAS[k], true)),
  {
    antes: `      <path id="regenBars" class="regen-bars" d="${regenD}"></path>\n`,
    depois: `      <text class="scale-unit" x="${f1(zeroPot[0] - 24)}" y="${f1(zeroPot[1] + 2)}" text-anchor="end">kW</text>\n` +
      `      <text id="regenLabel" class="regen-label" x="${f1(regenCentro[0])}" y="${f1(regenCentro[1])}">REGEN</text>\n`,
  })}
${miniSvg('fuel', miniComb, gradComb, 'fuelPanelClip', 'fuelFillClipRect', 'fuelMarker', [
  `<text class="side-label" x="98" y="${NIVEL.cheio + 4}">1</text>`,
  `<text class="side-label" x="100" y="${f1((NIVEL.cheio + NIVEL.vazio) / 2 + 2)}">½</text>`,
  `<text class="side-label" x="200" y="584">0</text>`,
], `      <g class="side-symbol" transform="translate(12 566) scale(1.333333)"><path d="M1,12L5,9V15Z"></path><path d="M22,10V8a2,2,0,0,0-2-2h-3V4a2,2,0,0,0-2-2H9A2,2,0,0,0,7,4v16a2,2,0,0,0,2,2h8a2,2,0,0,0,2-2V12h1v4a2,2,0,0,0,4,0V10ZM9,4h8v6H9Zm8,16H9V12h8Z"></path></g>`)}
${miniSvg('battery', miniBat, gradBat, 'batteryPanelClip', 'batteryFillClipRect', 'batteryMarker', [
  `<text class="side-label" x="1822" y="${NIVEL.cheio + 4}">1</text>`,
  `<text class="side-label" x="1820" y="${f1((NIVEL.cheio + NIVEL.vazio) / 2 + 2)}">½</text>`,
  `<text class="side-label" x="1720" y="584">0</text>`,
], `      <g class="side-symbol" transform="translate(1876 566) scale(1.333333)"><path d="M3,6h18c1.1,0,2,0.9,2,2v10c0,1.1-0.9,2-2,2H3c-1.1,0-2-0.9-2-2V8C1,6.9,1.9,6,3,6Zm0,2v10h18V8H3Z"></path><rect x="5" y="3" width="4" height="3"></rect><rect x="15" y="3" width="4" height="3"></rect><rect x="6" y="12" width="4" height="3"></rect><path d="M16,10h-2v2h-2v2h2v2h2v-2h2v-2h-2V10Z"></path></g>`)}
`

const js = `  // Gerado por scripts/gerar-bavarian-id7.mjs (valor -> fração do guia; pivôs; nível dos mini).
  const PIVO_VEL = { x: ${PIVO.x}, y: ${PIVO.y} };
  const PIVO_POT = { x: ${pivoPot.x}, y: ${pivoPot.y} };
  const SPEED_TABLE = ${JSON.stringify(SPEED_TABLE).replace(/,/g, ', ')};
  const POWER_TABLE = ${JSON.stringify(POWER_TABLE).replace(/,/g, ', ')};
  const NIVEL = { cheio: ${NIVEL.cheio}, vazio: ${NIVEL.vazio} };
`

function substituir(html, inicio, fim, conteudo) {
  const a = html.indexOf(inicio), b = html.indexOf(fim)
  if (a < 0 || b < a) throw new Error('marcador não achado: ' + inicio)
  return html.slice(0, a + inicio.length) + '\n' + conteudo + html.slice(b)
}

const args = process.argv.slice(2)
if (args.includes('--relatorio')) {
  console.log('comprimento do arco da escala:', (sFim - s0).toFixed(1), 'px; trechos', sNumeros.slice(1).map((v, k) => (v - sNumeros[k]).toFixed(0)).join(' '))
  idxNumeros.forEach((i, k) => console.log('número', VELOCIDADES[k], 'aro', aro[i].map(f1).join(','), 'texto', posNumero(i).map(f1).join(',')))
  console.log('SPEED_TABLE', JSON.stringify(SPEED_TABLE))
  console.log('POWER_TABLE', JSON.stringify(POWER_TABLE))
  console.log('regen', regenD)
}
const atual = fs.readFileSync(ARQUIVO, 'utf8')
let novo = substituir(atual, '<!-- GERADO:inicio -->', '    <!-- GERADO:fim -->', svg)
novo = substituir(novo, '/* GERADO:inicio */', '  /* GERADO:fim */', js)
if (args.includes('--verificar')) {
  if (novo !== atual) { console.error('app.html do Bavarian ID7 não bate com o gerador'); process.exit(1) }
  console.log('app.html do Bavarian ID7 bate com o gerador')
} else if (!args.includes('--relatorio')) {
  fs.writeFileSync(ARQUIVO, novo)
  console.log('app.html do Bavarian ID7 reescrito')
}
