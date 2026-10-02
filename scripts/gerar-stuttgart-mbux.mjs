// Geometria do tema Stuttgart MBUX Sport (assets/PreinstalledThemes/stuttgart-mbux).
//
// O túnel, as meias-luas, os anéis de pontos, as escalas e os dois círculos laterais saem daqui e
// são escritos no app.html entre os marcadores GERADO. O resto do tema (texto, ícones, lógica) é
// escrito à mão no próprio app.html.
//
//   node scripts/gerar-stuttgart-mbux.mjs              regrava os blocos GERADO
//   node scripts/gerar-stuttgart-mbux.mjs --verificar  falha se o app.html não bater com o gerador
//   node scripts/gerar-stuttgart-mbux.mjs --relatorio  imprime as posições principais
//
// As medidas vêm da foto de referência (painel MBUX no estilo Sport) ampliada para 1920x720, que
// tem praticamente a mesma proporção do painel (2,675 contra 2,667). Só a metade esquerda do túnel
// é calculada; a direita é a mesma geometria espelhada em x = 960 (matrix(-1 0 0 1 1920 0)).

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const arquivo = path.join(raiz, 'app', 'src', 'main', 'assets', 'PreinstalledThemes', 'stuttgart-mbux', 'app.html')

const r1 = (n) => Math.round(n * 10) / 10
const RAD = Math.PI / 180
const P = (cx, cy, r, deg) => [r1(cx + r * Math.cos(deg * RAD)), r1(cy + r * Math.sin(deg * RAD))]
const xy = (p) => p[0] + ' ' + p[1]

// Arco de a0 a a1 (graus, sentido da tela: ângulo crescente = horário).
function arco(cx, cy, r, a0, a1, continuar) {
  const p0 = P(cx, cy, r, a0)
  const p1 = P(cx, cy, r, a1)
  const varre = a1 > a0 ? 1 : 0
  const grande = Math.abs(a1 - a0) > 180 ? 1 : 0
  return (continuar ? '' : 'M ' + xy(p0) + ' ') + 'A ' + r + ' ' + r + ' 0 ' + grande + ' ' + varre + ' ' + xy(p1)
}
// x do lado esquerdo de um círculo numa altura y.
const xEsq = (c, y) => r1(c.cx - Math.sqrt(c.r * c.r - (y - c.cy) * (y - c.cy)))
// Ângulo (graus, 90..270) do ponto do lado esquerdo de um círculo numa altura y.
const angEsq = (c, y) => 180 - Math.asin((y - c.cy) / c.r) / RAD

// ── Medidas (metade esquerda) ────────────────────────────────────────────────────────────────
// Borda externa da meia-lua vermelha: varredura do vermelho saturado da foto, linha a linha,
// y 180..620 (x mínimo 488 em y ≈ 360).
const ARO = { cx: 981, cy: 360, r: 493 }
// Borda interna da meia-lua (x mínimo 567 em y ≈ 370).
const MIOLO = { cx: 917, cy: 370, r: 350 }
// O arco incandescente do túnel (núcleo branco, x mínimo 708 em y ≈ 362).
const QUENTE = { cx: 944, cy: 362, r: 236 }
// Onde a meia-lua cheia começa e termina na foto (o topo é uma linha fina de contorno).
const MEIA_LUA = { topoAro: 212, topoMiolo: 258, baseAro: 628, baseMiolo: 575 }
// A frente do enchimento gira em volta deste ponto, de INI (vazio) a FIM (cheio).
const CUNHA = { cx: 960, cy: 365, ini: 144, fim: 200 }
// Trilho das escalas laterais: arco suave, quase reto (x 383 nas pontas, 368 no meio).
const TRILHO = { cx: 1283, cy: 356, r: 915, yBase: 522, yTopo: 190 }
// Arco fino que separa as escalas do túnel.
const SEPARADOR = { cx: 981, cy: 380, r: 600 }
// Círculos laterais. Na foto: aro 131, centros em (178, 402) e (1742, 402). Encolhidos por K e
// com o centro mudado por causa das luzes do painel de fábrica, que ficam POR CIMA do tema
// (docs/operations/mapa-de-luzes-do-painel.md): o READY (x 247-328, y 341-362) e a placa de
// velocidade (x 240-290, y 492-544) caíam dentro do círculo da força G, e o título "Consumo e
// cabine" e o aro da direita ficavam embaixo da porta (x 1813-1852) e da coluna de luzes da borda
// (x >= 1863). Agora o esquerdo cabe entre a coluna de luzes da borda (x <= 55, mais 10 de folga) e
// a zona do READY (x >= 238), com a borda de fora (aro + 5) em x 68-238; o direito é o espelho.
const K = 80 / 131
const G = { cx: 153, cy: 420, aro: 80, alcance: r1(105 * K) }
const DADOS = { cx: 1767, cy: 420, aro: 80, trilho: r1(121 * K), arcoCabine: r1(118 * K) }
// Título acima da borda de fora, abaixo das luzes da diagonal (a do canto vai até y 250).
const TITULO_Y = G.cy - G.aro - 5 - 14
const ESCALA = [0, 25, 50, 75, 100]

// ── Formas ───────────────────────────────────────────────────────────────────────────────────
const aT = [xEsq(ARO, MEIA_LUA.topoAro), MEIA_LUA.topoAro]
const aB = [xEsq(ARO, MEIA_LUA.baseAro), MEIA_LUA.baseAro]
const mB = [xEsq(MIOLO, MEIA_LUA.baseMiolo), MEIA_LUA.baseMiolo]
const mT = [xEsq(MIOLO, MEIA_LUA.topoMiolo), MEIA_LUA.topoMiolo]
const dMeiaLua = 'M ' + xy(aT) + ' A ' + ARO.r + ' ' + ARO.r + ' 0 0 0 ' + xy(aB) +
  ' L ' + xy(mB) + ' A ' + MIOLO.r + ' ' + MIOLO.r + ' 0 0 1 ' + xy(mT) + ' Z'

// Cunha que recorta o enchimento: setor de 100° que termina no ângulo 0 local; o app gira.
const RC = 820
const dCunha = 'M ' + CUNHA.cx + ' ' + CUNHA.cy + ' L ' + [-100, -66, -33, 0]
  .map((a) => xy(P(CUNHA.cx, CUNHA.cy, RC, a))).join(' L ') + ' Z'

// Contorno fino que continua a meia-lua para cima, pelo aro, até perto do topo da tela.
const angTopoAro = angEsq(ARO, MEIA_LUA.topoAro)
const dContornoAlto = arco(ARO.cx, ARO.cy, ARO.r, angTopoAro, 219)

// Trilho da escala: desenhado de baixo para cima (o traço cheio cresce do zero).
const angBase = angEsq(TRILHO, TRILHO.yBase)
const angTopo = angEsq(TRILHO, TRILHO.yTopo)
const dTrilho = arco(TRILHO.cx, TRILHO.cy, TRILHO.r, angBase, angTopo)
const yDaEscala = (v) => TRILHO.yBase - (v / 100) * (TRILHO.yBase - TRILHO.yTopo)
const xTrilhoEsq = (y) => xEsq(TRILHO, y)

// Anéis de pontos (perspectiva): raios crescendo em progressão, pontos a cada 2,3°.
const aneis = []
for (let r = 252, i = 0; r < 430; r *= 1.075, i++) {
  const t = Math.min(1, (r - 252) / 170)
  aneis.push({ r: r1(r), w: r1(2.2 + t * 2.6), gap: r1(r * 2.1 * RAD), op: r1(0.7 + t * 0.3) })
}

// ── Blocos ───────────────────────────────────────────────────────────────────────────────────
function blocoFundo() {
  const L = []
  const add = (s) => L.push('    ' + s)

  // Cordilheira: silhueta determinística (sem aleatório, para o --verificar ser estável).
  const cume = (x) => 338 - 13 * Math.sin(x / 97) - 8 * Math.sin(x / 41 + 1.3) - 4 * Math.sin(x / 17 + 0.7) -
    18 * Math.exp(-(((x - 390) / 120) ** 2)) - 14 * Math.exp(-(((x - 1585) / 140) ** 2))
  const serraFrente = []
  const serraFundo = []
  for (let x = 0; x <= 1920; x += 24) {
    serraFrente.push(x + ' ' + r1(cume(x)))
    serraFundo.push(x + ' ' + r1(cume(x * 0.83 + 60) - 12))
  }
  // Estrelas: gerador congruencial com semente fixa.
  let s = 20260925
  const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648 }
  const estrelas = []
  for (let i = 0; i < 46; i++) {
    const x = r1(rnd() * 1920)
    const y = r1(12 + rnd() * 290)
    // O miolo do túnel é escuro; estrela ali ficaria atrás do teto.
    if (x > 470 && x < 1450 && y < 250) { i--; continue }
    estrelas.push('M ' + x + ' ' + y + ' h 0.01')
  }

  add('<defs>')
  add('  <linearGradient id="gCeu" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="720">')
  add('    <stop offset="0" stop-color="#040306"/><stop offset="0.25" stop-color="#0a0710"/>')
  add('    <stop offset="0.39" stop-color="#1d1330"/><stop offset="0.455" stop-color="#3b2854"/>')
  add('    <stop offset="0.482" stop-color="#5d4378"/><stop offset="0.5" stop-color="#1e1429"/>')
  add('    <stop offset="0.6" stop-color="#0d0813"/><stop offset="1" stop-color="#050307"/>')
  add('  </linearGradient>')
  add('  <radialGradient id="gBrilhoLilas">')
  add('    <stop offset="0" stop-color="#c29ae6" stop-opacity="0.72"/><stop offset="0.5" stop-color="#7a4c9e" stop-opacity="0.26"/>')
  add('    <stop offset="1" stop-color="#3a2050" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <radialGradient id="gReflexo">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0.16"/><stop class="s-acc" offset="1" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <linearGradient id="gVinheta" x1="0" y1="0" x2="1" y2="0">')
  add('    <stop offset="0" stop-color="#000" stop-opacity="0.7"/><stop offset="0.12" stop-color="#000" stop-opacity="0"/>')
  add('    <stop offset="0.88" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity="0.7"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gInterior" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="720">')
  add('    <stop offset="0" stop-color="#020103" stop-opacity="0.97"/><stop offset="0.26" stop-color="#040207" stop-opacity="0.95"/>')
  add('    <stop offset="0.36" stop-color="#130b1d" stop-opacity="0.9"/><stop offset="0.44" stop-color="#2c2040" stop-opacity="0.75"/>')
  add('    <stop offset="0.489" stop-color="#3a2c52" stop-opacity="0.7"/><stop offset="0.49" stop-color="#140d1c" stop-opacity="0.95"/>')
  add('    <stop offset="0.62" stop-color="#08050b" stop-opacity="0.97"/><stop offset="1" stop-color="#030205" stop-opacity="0.98"/>')
  add('  </linearGradient>')
  add('  <radialGradient id="gLuzDoTunel">')
  add('    <stop offset="0" stop-color="#ffffff"/><stop offset="0.2" stop-color="#f6f0ff" stop-opacity="0.95"/>')
  add('    <stop offset="0.4" stop-color="#cdb8ea" stop-opacity="0.55"/><stop offset="0.68" stop-color="#6e4e92" stop-opacity="0.18"/>')
  add('    <stop offset="1" stop-color="#3a2552" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <linearGradient id="gTeto" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="250">')
  add('    <stop offset="0" stop-color="#000" stop-opacity="0.9"/><stop offset="0.75" stop-color="#000" stop-opacity="0.55"/>')
  add('    <stop offset="1" stop-color="#000" stop-opacity="0"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gHorizonte" x1="0" y1="0" x2="1" y2="0">')
  add('    <stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset="0.5" stop-color="#fff" stop-opacity="0.55"/>')
  add('    <stop offset="1" stop-color="#fff" stop-opacity="0"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gPontos" gradientUnits="userSpaceOnUse" x1="0" y1="120" x2="0" y2="640">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0"/><stop class="s-acc" offset="0.22" stop-opacity="0.5"/>')
  add('    <stop class="s-acc-hi" offset="0.48" stop-opacity="1"/><stop class="s-acc" offset="0.78" stop-opacity="0.75"/>')
  add('    <stop class="s-acc" offset="1" stop-opacity="0.05"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gQuente" gradientUnits="userSpaceOnUse" x1="0" y1="215" x2="0" y2="515">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0"/><stop class="s-acc-hi" offset="0.2" stop-opacity="0.95"/>')
  add('    <stop offset="0.5" stop-color="#fff4f0"/><stop class="s-acc-hi" offset="0.78" stop-opacity="0.95"/>')
  add('    <stop class="s-acc" offset="1" stop-opacity="0"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gNucleo" gradientUnits="userSpaceOnUse" x1="0" y1="235" x2="0" y2="470">')
  add('    <stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset="0.3" stop-color="#fff"/>')
  add('    <stop offset="0.62" stop-color="#fff"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>')
  add('  </linearGradient>')
  add('  <radialGradient id="gParede" gradientUnits="userSpaceOnUse" cx="' + QUENTE.cx + '" cy="' + QUENTE.cy + '" r="440">')
  add('    <stop class="s-acc" offset="' + r1(QUENTE.r / 440 * 100) / 100 + '" stop-opacity="0.6"/><stop class="s-acc" offset="0.72" stop-opacity="0.22"/>')
  add('    <stop class="s-acc" offset="1" stop-opacity="0.08"/>')
  add('  </radialGradient>')
  add('  <linearGradient id="gMascaraParede" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="720">')
  add('    <stop offset="0.2" stop-color="#000"/><stop offset="0.4" stop-color="#fff"/>')
  add('    <stop offset="0.66" stop-color="#fff"/><stop offset="0.86" stop-color="#000"/>')
  add('  </linearGradient>')
  add('  <mask id="mParede" maskUnits="userSpaceOnUse" x="0" y="0" width="1920" height="720"><rect width="1920" height="720" fill="url(#gMascaraParede)"/></mask>')
  add('  <linearGradient id="gTrilhoMeiaLua" gradientUnits="userSpaceOnUse" x1="0" y1="' + MEIA_LUA.topoAro + '" x2="0" y2="' + MEIA_LUA.baseAro + '">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0.26"/><stop class="s-acc" offset="1" stop-opacity="0.5"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gHalo" gradientUnits="userSpaceOnUse" x1="0" y1="190" x2="0" y2="560">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0"/><stop class="s-acc" offset="0.3" stop-opacity="0.3"/>')
  add('    <stop class="s-acc" offset="0.6" stop-opacity="0.3"/><stop class="s-acc" offset="1" stop-opacity="0"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gAroCinza" gradientUnits="userSpaceOnUse" x1="0" y1="0" x2="0" y2="720">')
  add('    <stop offset="0" stop-color="#d6d6e2" stop-opacity="0"/><stop offset="0.18" stop-color="#d6d6e2" stop-opacity="0.5"/>')
  add('    <stop offset="0.82" stop-color="#d6d6e2" stop-opacity="0.5"/><stop offset="1" stop-color="#d6d6e2" stop-opacity="0.05"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gContornoAlto" gradientUnits="userSpaceOnUse" x1="0" y1="40" x2="0" y2="215">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0"/><stop class="s-acc" offset="1" stop-opacity="0.85"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gLinhaVel" gradientUnits="userSpaceOnUse" x1="650" y1="0" x2="905" y2="0">')
  add('    <stop offset="0" stop-color="#e2e2ea" stop-opacity="0"/><stop offset="0.35" stop-color="#e2e2ea" stop-opacity="0.85"/>')
  add('    <stop offset="1" stop-color="#e2e2ea" stop-opacity="0.95"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gFrisoBase" gradientUnits="userSpaceOnUse" x1="600" y1="0" x2="840" y2="0">')
  add('    <stop class="s-acc" offset="0" stop-opacity="0.9"/><stop class="s-acc" offset="1" stop-opacity="0"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gAroCirculo" x1="0" y1="0" x2="1" y2="0">')
  add('    <stop offset="0" stop-color="#9a9aa6"/><stop offset="0.22" stop-color="#34343c"/>')
  add('    <stop offset="0.78" stop-color="#34343c"/><stop offset="1" stop-color="#9a9aa6"/>')
  add('  </linearGradient>')
  add('  <filter id="fBrilho" x="-50%" y="-20%" width="200%" height="140%"><feGaussianBlur stdDeviation="9"/></filter>')
  add('  <filter id="fBrilhoLargo" x="-80%" y="-30%" width="260%" height="160%"><feGaussianBlur stdDeviation="22"/></filter>')
  add('  <clipPath id="clipAroE"><circle cx="' + ARO.cx + '" cy="' + ARO.cy + '" r="' + ARO.r + '"/></clipPath>')
  add('  <clipPath id="clipAroD"><circle cx="' + (1920 - ARO.cx) + '" cy="' + ARO.cy + '" r="' + ARO.r + '"/></clipPath>')
  add('  <clipPath id="clipMiolo"><circle cx="' + MIOLO.cx + '" cy="' + MIOLO.cy + '" r="' + MIOLO.r + '"/></clipPath>')
  add('  <clipPath id="clipAcimaDoHorizonte"><rect x="0" y="0" width="1920" height="352"/></clipPath>')
  add('</defs>')

  // Céu, brilho do horizonte, serras, estrelas e chão.
  add('<rect width="1920" height="720" fill="url(#gCeu)"/>')
  add('<ellipse cx="400" cy="318" rx="520" ry="175" fill="url(#gBrilhoLilas)"/>')
  add('<ellipse cx="1520" cy="318" rx="520" ry="175" fill="url(#gBrilhoLilas)"/>')
  add('<path class="estrelas" d="' + estrelas.join(' ') + '"/>')
  add('<path d="M ' + serraFundo.join(' L ') + ' L 1920 380 L 0 380 Z" fill="#241833" fill-opacity="0.75"/>')
  add('<path d="M ' + serraFrente.join(' L ') + ' L 1920 392 L 0 392 Z" fill="#0c0812"/>')
  add('<rect y="390" width="1920" height="330" fill="#07040a"/>')
  add('<ellipse cx="960" cy="720" rx="760" ry="170" fill="url(#gReflexo)"/>')
  add('<rect width="1920" height="720" fill="url(#gVinheta)"/>')

  // Arco fino que separa as escalas do túnel (esquerda e espelho).
  const dSeparador = arco(SEPARADOR.cx, SEPARADOR.cy, SEPARADOR.r, 150, 210)
  add('<g class="separador">')
  add('  <path d="' + dSeparador + '"/>')
  add('  <path d="' + dSeparador + '" transform="matrix(-1 0 0 1 1920 0)"/>')
  add('</g>')

  // Interior do túnel: interseção dos dois aros.
  add('<g clip-path="url(#clipAroE)"><g clip-path="url(#clipAroD)">')
  add('  <rect x="440" width="1040" height="720" fill="url(#gInterior)"/>')
  add('  <g class="luz-do-tunel">')
  add('    <ellipse cx="960" cy="330" rx="340" ry="128" fill="url(#gLuzDoTunel)" clip-path="url(#clipAcimaDoHorizonte)"/>')
  add('    <ellipse cx="960" cy="352" rx="300" ry="40" fill="url(#gLuzDoTunel)" opacity="0.28"/>')
  add('  </g>')
  add('  <line x1="740" y1="352" x2="1180" y2="352" stroke="url(#gHorizonte)" stroke-width="1.5"/>')
  add('  <path class="chao" d="M 902 356 L 612 668 M 1018 356 L 1308 668 M 930 356 L 850 668 M 990 356 L 1070 668"/>')
  add('  <rect x="440" width="1040" height="250" fill="url(#gTeto)"/>')
  add('</g></g>')

  // Metade esquerda do túnel; a direita é a mesma, espelhada.
  add('<g id="tunelE">')
  add('  <g clip-path="url(#clipMiolo)">')
  add('    <path class="parede" mask="url(#mParede)" d="' + arco(QUENTE.cx, QUENTE.cy, QUENTE.r, 125, 235) + ' L ' +
    xy(P(QUENTE.cx, QUENTE.cy, 440, 235)) + ' ' + arco(QUENTE.cx, QUENTE.cy, 440, 235, 125, true) + ' Z"/>')
  for (const a of aneis) {
    add('    <path class="anel" d="' + arco(QUENTE.cx, QUENTE.cy, a.r, 128, 232) + '" stroke-width="' + a.w +
      '" stroke-dasharray="0.01 ' + a.gap + '" opacity="' + a.op + '"/>')
  }
  add('    <path class="anel-linha" d="' + arco(QUENTE.cx, QUENTE.cy, 300, 132, 228) + ' ' +
    arco(QUENTE.cx, QUENTE.cy, 345, 136, 224) + '"/>')
  add('  </g>')
  const dQuente = arco(QUENTE.cx, QUENTE.cy, QUENTE.r, 140, 220)
  add('  <path class="quente-halo-largo" d="' + dQuente + '" filter="url(#fBrilhoLargo)"/>')
  add('  <path class="quente-halo" d="' + dQuente + '" filter="url(#fBrilho)"/>')
  add('  <path class="quente-meio" d="' + dQuente + '"/>')
  add('  <path class="quente-nucleo" d="' + arco(QUENTE.cx, QUENTE.cy, QUENTE.r, 154, 206) + '"/>')
  add('  <path class="meia-lua-trilho" d="' + dMeiaLua + '"/>')
  add('  <path class="contorno-alto" d="' + dContornoAlto + '"/>')
  add('  <path class="aro-cinza" d="' + arco(ARO.cx, ARO.cy, ARO.r + 16, 128, 232) + '"/>')
  add('  <line class="linha-vel" x1="650" y1="187" x2="905" y2="187"/>')
  add('  <path class="friso-base" d="M 600 612 L 840 612"/>')
  add('  <path class="bisel-base" d="M 548 632 Q 620 664 760 664 L 960 664"/>')
  add('  <path class="trilho" d="' + dTrilho + '"/>')
  add('</g>')
  add('<use href="#tunelE" transform="matrix(-1 0 0 1 1920 0)"/>')

  // Rótulos das escalas: bateria à esquerda, potência à direita.
  add('<g class="escala-rotulos">')
  add('  <text class="escala-titulo" x="' + r1(xTrilhoEsq(TRILHO.yTopo) + 18) + '" y="' + (TRILHO.yTopo - 30) + '">BATERIA</text>')
  add('  <text class="escala-titulo" x="' + r1(1920 - xTrilhoEsq(TRILHO.yTopo) - 18) + '" y="' + (TRILHO.yTopo - 30) +
    '" text-anchor="end">POTÊNCIA</text>')
  for (const v of ESCALA) {
    const y = yDaEscala(v)
    const xe = r1(xTrilhoEsq(y) + 18)
    const xd = r1(1920 - xTrilhoEsq(y) - 18)
    const un = v === 0 ? '<tspan class="un" dx="8">%</tspan>' : ''
    add('  <text transform="translate(' + xe + ' ' + r1(y + 12) + ') skewX(-10)">' + v + un + '</text>')
    add('  <text transform="translate(' + xd + ' ' + r1(y + 12) + ') skewX(-10)" text-anchor="end">' + v + un + '</text>')
  }
  add('</g>')

  // Círculo da força G.
  add('<g class="circulo">')
  add('  <circle cx="' + G.cx + '" cy="' + G.cy + '" r="' + (G.aro + 5) + '" class="circulo-borda"/>')
  add('  <circle cx="' + G.cx + '" cy="' + G.cy + '" r="' + G.aro + '" class="circulo-disco"/>')
  add('  <circle cx="' + G.cx + '" cy="' + G.cy + '" r="' + G.aro + '" class="circulo-aro"/>')
  for (const [r, w] of [[113, 3.5], [79, 3], [44, 3]]) {
    add('  <circle class="g-anel" cx="' + G.cx + '" cy="' + G.cy + '" r="' + r1(r * K) + '" stroke-width="' + r1(w * 0.8) + '"/>')
  }
  const cruz = [0, 90, 180, 270].map((a) => 'M ' + xy(P(G.cx, G.cy, r1(103 * K), a)) + ' L ' + xy(P(G.cx, G.cy, r1(124 * K), a))).join(' ')
  add('  <path class="g-cruz" d="' + cruz + '"/>')
  add('  <text class="circulo-titulo" x="' + G.cx + '" y="' + TITULO_Y + '" text-anchor="middle">Força G</text>')
  add('</g>')

  // Círculo de dados: consumo no aro de cima, temperatura da cabine no arco de baixo.
  const D = DADOS
  add('<g class="circulo">')
  add('  <circle cx="' + D.cx + '" cy="' + D.cy + '" r="' + (D.aro + 5) + '" class="circulo-borda"/>')
  add('  <circle cx="' + D.cx + '" cy="' + D.cy + '" r="' + D.aro + '" class="circulo-disco"/>')
  add('  <circle cx="' + D.cx + '" cy="' + D.cy + '" r="' + D.aro + '" class="circulo-aro"/>')
  add('  <path class="dados-trilho" d="' + arco(D.cx, D.cy, D.trilho, 180, 360) + '"/>')
  add('  <path class="dados-traco" d="M ' + (D.cx - D.aro - 6) + ' ' + D.cy + ' h 12 M ' + (D.cx + D.aro - 6) + ' ' + D.cy + ' h 12"/>')
  add('  <text class="dados-lim" transform="translate(' + r1(D.cx - 92 * K) + ' ' + r1(D.cy + 10 * K) + ') skewX(-10)">0</text>')
  add('  <text class="dados-lim" transform="translate(' + r1(D.cx + 94 * K) + ' ' + r1(D.cy + 10 * K) + ') skewX(-10)" text-anchor="end">30</text>')
  add('  <path class="cabine-trilho" d="' + arco(D.cx, D.cy, D.arcoCabine, 150, 30) + '"/>')
  add('  <path class="cabine-quente" d="' + arco(D.cx, D.cy, D.arcoCabine, 40, 30) + '"/>')
  const l10 = P(D.cx, D.cy, r1(90 * K), 146)
  const l40 = P(D.cx, D.cy, r1(90 * K), 34)
  add('  <text class="dados-lim" transform="translate(' + xy(l10.map((n, i) => r1(n + (i ? 8 : 4) * K))) + ') skewX(-10)" text-anchor="middle">10</text>')
  add('  <text class="dados-lim" transform="translate(' + xy(l40.map((n, i) => r1(n + (i ? 8 : -4) * K))) + ') skewX(-10)" text-anchor="middle">40</text>')
  add('  <text class="circulo-titulo" x="' + D.cx + '" y="' + TITULO_Y + '" text-anchor="middle">Consumo e cabine</text>')
  add('</g>')
  return L.join('\n')
}

function blocoVivo() {
  const L = []
  const add = (s) => L.push('    ' + s)
  const D = DADOS
  add('<defs>')
  add('  <clipPath id="clipMeiaLua"><path d="' + dMeiaLua + '"/></clipPath>')
  add('  <clipPath id="clipCunha"><path id="cunha" d="' + dCunha + '" transform="rotate(' + CUNHA.ini + ' ' + CUNHA.cx + ' ' + CUNHA.cy + ')"/></clipPath>')
  add('  <linearGradient id="gEnchimento" gradientUnits="userSpaceOnUse" x1="' + r1(ARO.cx - ARO.r) + '" y1="0" x2="' + r1(MIOLO.cx - MIOLO.r + 70) + '" y2="0">')
  add('    <stop class="s-acc-lo" offset="0"/><stop class="s-acc" offset="0.3"/>')
  add('    <stop class="s-acc" offset="0.78"/><stop class="s-acc-hi" offset="1"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gBorda" gradientUnits="userSpaceOnUse" x1="' + (CUNHA.cx + 330) + '" y1="0" x2="' + (CUNHA.cx + 540) + '" y2="0">')
  add('    <stop offset="0" stop-color="#fff" stop-opacity="0"/><stop offset="0.45" stop-color="#fff" stop-opacity="0.9"/>')
  add('    <stop offset="1" stop-color="#fff" stop-opacity="0.2"/>')
  add('  </linearGradient>')
  add('  <radialGradient id="gPonta">')
  add('    <stop offset="0" stop-color="#fff"/><stop class="s-acc-hi" offset="0.35" stop-opacity="0.85"/>')
  add('    <stop class="s-acc" offset="1" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <radialGradient id="gPontaRegen">')
  add('    <stop offset="0" stop-color="#fff"/><stop offset="0.35" stop-color="#5cc8ff" stop-opacity="0.85"/>')
  add('    <stop offset="1" stop-color="#1e88ff" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <radialGradient id="gMarcador">')
  add('    <stop offset="0" stop-color="#fff"/><stop offset="0.35" stop-color="#ffb35c" stop-opacity="0.9"/>')
  add('    <stop offset="1" stop-color="#ff7a1a" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('</defs>')

  // Meias-luas: a mesma forma dos dois lados; o enchimento sobe com o giro.
  add('<g id="meiaLuaE" clip-path="url(#clipMeiaLua)">')
  add('  <path d="' + dMeiaLua + '" fill="url(#gEnchimento)" clip-path="url(#clipCunha)"/>')
  add('  <line id="bordaQuente" class="borda-quente" x1="' + (CUNHA.cx + 330) + '" y1="' + CUNHA.cy + '" x2="' + (CUNHA.cx + 540) +
    '" y2="' + CUNHA.cy + '" transform="rotate(' + CUNHA.ini + ' ' + CUNHA.cx + ' ' + CUNHA.cy + ')"/>')
  add('</g>')
  add('<use href="#meiaLuaE" transform="matrix(-1 0 0 1 1920 0)"/>')

  // Escalas: o traço cheio cresce de baixo para cima; a ponta corre pelo trilho.
  const ponta = (id) => [
    '<g id="' + id + '" class="ponta">',
    '  <ellipse rx="40" ry="1.4" fill="#fff" opacity="0.35"/>',
    '  <circle r="15" class="ponta-brilho"/>',
    '  <ellipse rx="22" ry="3" fill="#fff" opacity="0.95"/>',
    '</g>',
  ]
  add('<path id="escalaE" class="escala-cheia" d="' + dTrilho + '"/>')
  for (const s of ponta('pontaE')) add(s)
  add('<g transform="matrix(-1 0 0 1 1920 0)">')
  add('  <path id="escalaD" class="escala-cheia" d="' + dTrilho + '"/>')
  for (const s of ponta('pontaD')) add('  ' + s)
  add('</g>')

  // Força G: o ponto desce acelerando e sobe freando.
  add('<g id="pontoG" class="ponto-g">')
  add('  <circle cx="' + G.cx + '" cy="' + G.cy + '" r="' + r1(18 * K) + '" class="ponto-g-brilho"/>')
  add('  <circle cx="' + G.cx + '" cy="' + G.cy + '" r="' + r1(7.5 * K) + '" class="ponto-g-miolo"/>')
  add('</g>')

  // Consumo: o marcador corre pelo aro de cima, de 0 (esquerda) a 30 km/l (direita).
  add('<g id="marcadorConsumo" class="gira" transform="rotate(0 ' + D.cx + ' ' + D.cy + ')">')
  add('  <circle cx="' + r1(D.cx - D.trilho) + '" cy="' + D.cy + '" r="' + r1(15 * K) + '" fill="url(#gMarcador)"/>')
  add('  <line x1="' + r1(D.cx - D.trilho - 11 * K) + '" y1="' + D.cy + '" x2="' + r1(D.cx - D.trilho + 11 * K) + '" y2="' + D.cy + '" class="marcador-traco"/>')
  add('</g>')
  add('<path id="arcoCabine" class="cabine-cheia" d="' + arco(D.cx, D.cy, D.arcoCabine, 150, 30) + '"/>')
  return L.join('\n')
}

function blocoJs() {
  const geo = {
    cunha: CUNHA,
    g: { cx: G.cx, cy: G.cy, alcance: G.alcance },
    dados: { cx: DADOS.cx, cy: DADOS.cy },
    rpmMax: 6500,
    consumoMax: 30,
    cabine: { min: 10, max: 40 },
  }
  return '  const GEO = ' + JSON.stringify(geo) + ';'
}

// ── Escrita ──────────────────────────────────────────────────────────────────────────────────
const MARCAS = [
  ['<!-- GERADO:fundo:inicio -->', '<!-- GERADO:fundo:fim -->', blocoFundo],
  ['<!-- GERADO:vivo:inicio -->', '<!-- GERADO:vivo:fim -->', blocoVivo],
  ['/* GERADO:js:inicio */', '/* GERADO:js:fim */', blocoJs],
]

function montar(texto) {
  let out = texto
  for (const [ini, fim, gera] of MARCAS) {
    const a = out.indexOf(ini)
    const b = out.indexOf(fim)
    if (a < 0 || b < a) throw new Error('marcadores ausentes: ' + ini)
    out = out.slice(0, a + ini.length) + '\n' + gera() + '\n    ' + out.slice(b)
  }
  return out
}

if (process.argv.includes('--relatorio')) {
  console.log('meia-lua: topo aro', aT, 'topo miolo', mT, 'base aro', aB, 'base miolo', mB)
  console.log('trilho: base', [xTrilhoEsq(TRILHO.yBase), TRILHO.yBase], 'meio', [xTrilhoEsq(TRILHO.cy), TRILHO.cy])
  console.log('anéis:', aneis.map((a) => a.r).join(', '))
  console.log('escala y:', ESCALA.map((v) => v + '→' + r1(yDaEscala(v))).join(', '))
  process.exit(0)
}

const atual = fs.readFileSync(arquivo, 'utf8')
const novo = montar(atual)
if (process.argv.includes('--verificar')) {
  if (novo !== atual) {
    console.error('FALHOU: o app.html do Stuttgart MBUX não bate com scripts/gerar-stuttgart-mbux.mjs')
    process.exit(1)
  }
  console.log('ok: app.html bate com o gerador')
} else {
  fs.writeFileSync(arquivo, novo)
  console.log('geometria gravada em ' + path.relative(raiz, arquivo))
}
