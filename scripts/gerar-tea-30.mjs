// Tema TEA 3.0 (assets/PreinstalledThemes/tea30), Alpha 9.5.
//
// Inspirado no painel de fábrica da foto de referência do dono (30/09/2026): velocímetro redondo à
// esquerda (0 a 260, com 200-230-260 no fim), medidor de potência à direita (-100 a 100, carga em
// verde e potência em azul, "0 %" e "Potência" no meio), a estrada com o carro visto de trás no
// centro, temperatura, marcha e hora em cima, autonomias elétrica e a combustão embaixo à esquerda
// e modo de condução, modo de energia e hodômetro embaixo à direita.
//
// O QUE MUDA EM RELAÇÃO À FOTO, E POR QUÊ
//
//  • Os números das escalas ficam POR FORA do aro. O carro desenha por cima do tema o READY (25) e a
//    placa da via (26) dentro do mostrador esquerdo (docs/operations/mapa-de-luzes-do-painel.md);
//    com os números por dentro, o 60 e o 20 cairiam embaixo deles. Por fora, a 292 px do centro, eles
//    passam entre a diagonal de luzes e o aro.
//  • Sem READY, placa ou luz nenhuma desenhada pelo tema: são do carro (memória "luzes só do carro").
//  • Sem os outros carros da foto: o tema não recebe o que o ADAS detecta, e um carro inventado na
//    frente leria como detecção. Fica a estrada e o próprio carro.
//
// Os centros (426, 440) e (1494, 440) são o lugar dos mostradores de fábrica (o mesmo do SENSUS 9.3).
//
//   node scripts/gerar-tea-30.mjs              grava app.html
//   node scripts/gerar-tea-30.mjs --verificar  falha se o app.html não bater com este gerador

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const arquivo = path.join(raiz, 'app', 'src', 'main', 'assets', 'PreinstalledThemes', 'tea30', 'app.html')

const r1 = (n) => Math.round(n * 10) / 10
const rad = (a) => (a * Math.PI) / 180
const pt = (cx, cy, r, a) => [r1(cx + r * Math.cos(rad(a))), r1(cy - r * Math.sin(rad(a)))]

// ── Medidas ─────────────────────────────────────────────────────────────────────────────────
const CY = 440
const CE = 426
const CD = 1494
const R = { face: 258, aro: 262, trilho: 250, tracoFora: 244, tracoMaior: 226, tracoMenor: 236, rotulo: 293, cubo: 108 }
const INI = 225 // ângulo do começo da escala (graus matemáticos, 0 = direita, 90 = cima)
const FIM = -45

// Velocidade: 12 intervalos iguais; os dois últimos valem 30 km/h (200-230-260), como na foto.
const VEL = [0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 230, 260]
const ESC_VEL = VEL.map((v, i) => [v, INI - (270 / 12) * i])
// Potência: -100 a 0 comprimido em 45° (a carga), 0 a 100 em 10 passos de 22,5°.
const POT = [-100, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
const ESC_POT = POT.map((v, i) => [v, i === 0 ? INI : 180 - 22.5 * (i - 1)])

function arco(cx, r, a0, a1) {
  const [x0, y0] = pt(cx, CY, r, a0)
  const [x1, y1] = pt(cx, CY, r, a1)
  const grande = Math.abs(a0 - a1) > 180 ? 1 : 0
  const horario = a0 > a1 ? 1 : 0
  return `M${x0} ${y0} A${r} ${r} 0 ${grande} ${horario} ${x1} ${y1}`
}

function tracos(cx, esc) {
  let s = ''
  for (let i = 0; i < esc.length; i++) {
    const a = esc[i][1]
    const [x0, y0] = pt(cx, CY, R.tracoMaior, a)
    const [x1, y1] = pt(cx, CY, R.tracoFora, a)
    s += `<line class="tm" x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}"/>`
    if (i < esc.length - 1) {
      const b = (a + esc[i + 1][1]) / 2
      const [x2, y2] = pt(cx, CY, R.tracoMenor, b)
      const [x3, y3] = pt(cx, CY, R.tracoFora, b)
      s += `<line class="tn" x1="${x2}" y1="${y2}" x2="${x3}" y2="${y3}"/>`
    }
  }
  return s
}

function rotulos(cx, esc) {
  return esc.map(([v, a]) => {
    const [x, y] = pt(cx, CY, R.rotulo, a)
    return `<text class="rot" x="${x}" y="${r1(y + 9)}">${v < 0 ? '−' + Math.abs(v) : v}</text>`
  }).join('')
}

// O aro se abre embaixo, no vão da escala (entre 247° e 293°): é ali que o carro acende a luz do
// combustível (12, x 346-384, y 662-707) no esquerdo, e a vaga espelhada fica no direito.
const VAO_ARO = [247, 293]
function mostrador(cx, id) {
  return `<g id="m${id}">
    <path d="${arco(cx, R.aro + 5, VAO_ARO[0], VAO_ARO[1] - 360)}" fill="none" stroke="rgba(120,165,220,0.10)" stroke-width="8" stroke-linecap="round"/>
    <circle cx="${cx}" cy="${CY}" r="${R.face}" fill="url(#gFace)"/>
    <path d="${arco(cx, R.aro, VAO_ARO[0], VAO_ARO[1] - 360)}" fill="none" stroke="url(#gAro)" stroke-width="3" stroke-linecap="round"/>
    <path d="${arco(cx, R.trilho, INI, FIM)}" fill="none" stroke="rgba(255,255,255,0.07)" stroke-width="6" stroke-linecap="round"/>
    <g>${tracos(cx, id === 'E' ? ESC_VEL : ESC_POT)}</g>
    <g>${rotulos(cx, id === 'E' ? ESC_VEL : ESC_POT)}</g>
  </g>`
}

// A estrada: ponto de fuga em (960, 172), pé em y = 640. Quatro linhas (três faixas); as de dentro
// tracejadas em perspectiva (os traços crescem para perto).
const FUGA = [960, 172]
const PE = 640
const PES_X = [630, 850, 1070, 1290]
function naLinha(xPe, t) { return [r1(FUGA[0] + (xPe - FUGA[0]) * t), r1(FUGA[1] + (PE - FUGA[1]) * t)] }
function estrada() {
  let s = ''
  // Superfície e grade.
  s += `<polygon points="${FUGA[0] - 6},${FUGA[1]} ${FUGA[0] + 6},${FUGA[1]} ${PES_X[3] + 40},${PE} ${PES_X[0] - 40},${PE}" fill="url(#gAsfalto)"/>`
  for (let k = 1; k <= 9; k++) {
    const t = Math.pow(k / 9, 1.9)
    const [xa, y] = naLinha(PES_X[0] - 40, t)
    const [xb] = naLinha(PES_X[3] + 40, t)
    s += `<line x1="${xa}" y1="${y}" x2="${xb}" y2="${y}" stroke="rgba(160,190,230,${r1(0.03 + 0.05 * t * 10) / 10})" stroke-width="1"/>`
  }
  // Bordas: faixa cheia que engrossa para perto.
  for (const xPe of [PES_X[0], PES_X[3]]) {
    const [xa, ya] = naLinha(xPe, 0.04)
    const larg = 5
    s += `<polygon points="${xa - 0.5},${ya} ${xa + 0.5},${ya} ${xPe + larg},${PE} ${xPe - larg},${PE}" fill="url(#gFaixa)"/>`
  }
  // Tracejadas.
  for (const xPe of [PES_X[1], PES_X[2]]) {
    const passo = 0.12
    for (let t = 0.06; t < 1; t += passo * (0.55 + t)) {
      const t2 = Math.min(1, t + passo * (0.55 + t) * 0.5)
      const [xa, ya] = naLinha(xPe, t)
      const [xb, yb] = naLinha(xPe, t2)
      const la = 0.4 + 3.2 * t, lb = 0.4 + 3.2 * t2
      s += `<polygon points="${r1(xa - la)},${ya} ${r1(xa + la)},${ya} ${r1(xb + lb)},${yb} ${r1(xb - lb)},${yb}" fill="rgba(235,242,250,${r1(0.18 + 0.5 * t)})"/>`
    }
  }
  return s
}

// O carro visto de trás (SUV branco), centro em x = 960, pé em y = 588.
const CARRO = `<g id="carro">
  <ellipse cx="960" cy="590" rx="104" ry="15" fill="rgba(0,0,0,0.75)"/>
  <ellipse cx="960" cy="592" rx="140" ry="24" fill="url(#gSombra)"/>
  <path d="M884 578 L882 548 Q880 520 896 506 L912 470 Q918 458 934 456 L986 456 Q1002 458 1008 470 L1024 506 Q1040 520 1038 548 L1036 578 Q1036 586 1026 586 L894 586 Q884 586 884 578 Z" fill="url(#gCarro)"/>
  <path d="M918 474 Q922 466 934 465 L986 465 Q998 466 1002 474 L1012 500 L908 500 Z" fill="url(#gVidro)"/>
  <path d="M894 520 L1026 520 L1030 532 L890 532 Z" fill="rgba(20,24,30,0.28)"/>
  <rect x="889" y="524" width="36" height="9" rx="3" fill="#ff3b3b"/>
  <rect x="995" y="524" width="36" height="9" rx="3" fill="#ff3b3b"/>
  <rect x="925" y="527" width="70" height="3" rx="1.5" fill="rgba(255,70,70,0.75)"/>
  <rect x="944" y="548" width="32" height="12" rx="2" fill="#e6e9ec" stroke="rgba(0,0,0,0.25)"/>
  <rect x="888" y="572" width="22" height="16" rx="3" fill="#15181c"/>
  <rect x="1010" y="572" width="22" height="16" rx="3" fill="#15181c"/>
  <path d="M900 510 L1020 510" stroke="rgba(255,255,255,0.55)" stroke-width="1.5"/>
</g>`

const DEFS = `<defs>
  <radialGradient id="gFace" cx="0.5" cy="0.45" r="0.55">
    <stop offset="0" stop-color="#121821"/><stop offset="0.8" stop-color="#090c11"/><stop offset="1" stop-color="#05070a"/>
  </radialGradient>
  <linearGradient id="gAro" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#d8dee5"/><stop offset="0.5" stop-color="#6d7783"/><stop offset="1" stop-color="#2a3038"/>
  </linearGradient>
  <linearGradient id="gAzul" gradientUnits="userSpaceOnUse" x1="${CE - 260}" y1="0" x2="${CE + 260}" y2="0">
    <stop offset="0" stop-color="#1c64ff"/><stop offset="1" stop-color="#8fd0ff"/>
  </linearGradient>
  <linearGradient id="gPot" gradientUnits="userSpaceOnUse" x1="${CD - 260}" y1="0" x2="${CD + 260}" y2="0">
    <stop offset="0" stop-color="#2a6dff"/><stop offset="1" stop-color="#7cc7ff"/>
  </linearGradient>
  <linearGradient id="gAsfalto" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#2a3a52" stop-opacity="0"/><stop offset="0.55" stop-color="#23324a" stop-opacity="0.35"/><stop offset="1" stop-color="#1b2638" stop-opacity="0.65"/>
  </linearGradient>
  <linearGradient id="gFaixa" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#dfe9f5" stop-opacity="0.05"/><stop offset="1" stop-color="#eef4fb" stop-opacity="0.8"/>
  </linearGradient>
  <radialGradient id="gHorizonte" cx="0.5" cy="0.5" r="0.5">
    <stop offset="0" stop-color="#4f8fe0" stop-opacity="0.32"/><stop offset="1" stop-color="#4f8fe0" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="gCarro" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#ffffff"/><stop offset="0.55" stop-color="#e3e8ee"/><stop offset="1" stop-color="#a9b2bc"/>
  </linearGradient>
  <linearGradient id="gVidro" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#3a4452"/><stop offset="1" stop-color="#11161d"/>
  </linearGradient>
  <radialGradient id="gSombra" cx="0.5" cy="0.5" r="0.5">
    <stop offset="0" stop-color="#6aa7ff" stop-opacity="0.22"/><stop offset="1" stop-color="#6aa7ff" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="gMascaraX" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#000"/><stop offset="0.13" stop-color="#fff"/><stop offset="0.87" stop-color="#fff"/><stop offset="1" stop-color="#000"/>
  </linearGradient>
  <mask id="mCena" maskUnits="userSpaceOnUse" x="600" y="100" width="720" height="560">
    <rect x="600" y="100" width="720" height="560" fill="url(#gMascaraX)"/>
  </mask>
</defs>`

// Arcos vivos em fatias de FATIA graus (o mesmo princípio do SENSUS): o JS só troca a visibilidade
// das fatias que o valor cruzou. Mexer no dashoffset de um arco inteiro repintaria o mostrador
// inteiro a cada quadro; uma fatia repinta só a caixinha dela. Sem camada composta (will-change).
const FATIA = 2.25
function fatias(id, cx, a0, a1, cor) {
  const n = Math.round(Math.abs(a1 - a0) / FATIA)
  const passo = (a1 - a0) / n
  let s = ''
  for (let i = 0; i < n; i++) {
    // Cada fatia avança 0,3° sobre a seguinte para não sobrar fresta entre elas.
    const b = a0 + passo * (i + 1) + Math.sign(passo) * (i < n - 1 ? 0.3 : 0)
    s += `<path d="${arco(cx, R.trilho, a0 + passo * i, b)}" visibility="hidden"/>`
  }
  return `<g id="${id}" fill="none" stroke="${cor}" stroke-width="6">${s}</g>`
}
const VIVO = `
  ${fatias('arcoVel', CE, INI, FIM, 'url(#gAzul)')}
  <path d="${arco(CD, R.trilho, INI, 180)}" fill="none" stroke="rgba(52,209,122,0.18)" stroke-width="6" stroke-linecap="round"/>
  ${fatias('arcoCarga', CD, 180, INI, '#34d17a')}
  ${fatias('arcoPot', CD, 180, FIM, 'url(#gPot)')}
  <g id="limite" visibility="hidden"><polygon points="${CE + R.trilho - 10},${CY} ${CE + R.trilho + 12},${CY - 7} ${CE + R.trilho + 12},${CY + 7}" fill="#ff3b30"/></g>
  <g id="pontE" transform="rotate(-225 ${CE} ${CY})">
    <line x1="${CE + 150}" y1="${CY}" x2="${CE + 244}" y2="${CY}" stroke="rgba(90,160,255,0.35)" stroke-width="12" stroke-linecap="round"/>
    <polygon points="${CE + 146},${CY - 3.5} ${CE + 246},${CY - 1} ${CE + 246},${CY + 1} ${CE + 146},${CY + 3.5}" fill="#f5f8fb"/>
  </g>
  <g id="pontD" transform="rotate(-180 ${CD} ${CY})">
    <line x1="${CD + 150}" y1="${CY}" x2="${CD + 244}" y2="${CY}" stroke="rgba(90,160,255,0.35)" stroke-width="12" stroke-linecap="round"/>
    <polygon points="${CD + 146},${CY - 3.5} ${CD + 246},${CY - 1} ${CD + 246},${CY + 1} ${CD + 146},${CY + 3.5}" fill="#f5f8fb"/>
  </g>
  <circle cx="${CE}" cy="${CY}" r="${R.cubo}" fill="#06080b" stroke="rgba(255,255,255,0.10)" stroke-width="2"/>
  <circle cx="${CD}" cy="${CY}" r="${R.cubo}" fill="#06080b" stroke="rgba(255,255,255,0.10)" stroke-width="2"/>`

// Embaixo do começo da escala, fora do caminho do ponteiro.
const [cargaX, cargaY] = pt(CD, CY, 200, 238)

const SVG = `<svg id="arte" viewBox="0 0 1920 720" preserveAspectRatio="none" aria-hidden="true">
${DEFS}
<g id="cena" mask="url(#mCena)">
  <ellipse cx="960" cy="176" rx="430" ry="70" fill="url(#gHorizonte)"/>
  ${estrada()}
  ${CARRO}
</g>
${mostrador(CE, 'E')}
${mostrador(CD, 'D')}
<text class="carga" x="${cargaX}" y="${cargaY}">CARGA</text>
${VIVO}
</svg>`

const TABELAS = `const ESC_VEL = ${JSON.stringify(ESC_VEL)};
  const ESC_POT = ${JSON.stringify(ESC_POT)};
  const GEO = { ce: ${CE}, cd: ${CD}, cy: ${CY}, ini: ${INI}, fim: ${FIM}, fatia: ${FATIA} };`

const html = `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=1920, height=720, initial-scale=1" />
<title>TEA 3.0</title>
<!--
  TEA 3.0 (OPEN GW, Alpha 9.5). Gerado por scripts/gerar-tea-30.mjs — edite lá e rode de novo.
  Contrato v1.0: só assina chaves de ThemeBridgeImpl.getAvailableKeys(); não desenha luz nenhuma
  (as do carro ficam por cima); declara o vão da projeção (setProjectionBox) e o abre no #bg.
-->
<style>
  :root { --ink: #f2f5f8; --dim: #8a96a3; --azul: #4f9bff; --verde: #34d17a; }
  html, body {
    margin: 0; padding: 0; width: 1920px; height: 720px; overflow: hidden; background: transparent;
    font-family: Roboto, 'Helvetica Neue', Arial, sans-serif; color: var(--ink);
    -webkit-font-smoothing: antialiased; user-select: none;
  }
  /* O fundo mora no #bg: é nele que a projeção abre o vão (aplicarVaoDaProjecao). */
  #bg {
    position: absolute; left: 0; top: 0; width: 1920px; height: 720px; z-index: 0;
    background: radial-gradient(ellipse 1100px 520px at 960px 300px, #0b1220 0%, #05070b 60%, #020305 100%);
  }
  body.yield-to-car #bg { opacity: 0; }
  #projscrim {
    position: absolute; display: none; z-index: 0; pointer-events: none;
    background: linear-gradient(to bottom, rgba(0,0,0,0) 0%, rgba(0,0,0,0) 62%, rgba(0,0,0,.7) 100%);
  }
  body.projetando #projscrim { display: block; }
  body.projetando #cena, body.projetando #midia { display: none; }
  /* Enquanto projeta: um anel fumê em volta de cada mostrador (onde ficam os números, por fora do
     aro) e contorno escuro nos números — a imagem passa por baixo e a escala continua legível. */
  #projfumo {
    position: absolute; left: 0; top: 0; width: 1920px; height: 720px; display: none; z-index: 0; pointer-events: none;
    background:
      radial-gradient(circle 350px at ${CE}px ${CY}px, rgba(3,5,8,0.62) 0, rgba(3,5,8,0.62) 312px, rgba(3,5,8,0) 350px),
      radial-gradient(circle 350px at ${CD}px ${CY}px, rgba(3,5,8,0.62) 0, rgba(3,5,8,0.62) 312px, rgba(3,5,8,0) 350px);
  }
  body.projetando #projfumo { display: block; }
  body.projetando .rot { paint-order: stroke; stroke: rgba(0,0,0,0.85); stroke-width: 6px; stroke-linejoin: round; }
  #noite { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; background: #000; opacity: 0; z-index: 3; pointer-events: none; }
  body.mode-headlight #noite { opacity: 0.18; }
  body.night-mode #noite { opacity: 0.38; }

  #app { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; z-index: 1; transition: opacity 0.25s ease; }
  #app.hidden { opacity: 0; pointer-events: none; }
  #arte { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; }

  .tm { stroke: #e9eef3; stroke-width: 2.5; stroke-linecap: round; }
  .tn { stroke: rgba(233,238,243,0.45); stroke-width: 1.5; stroke-linecap: round; }
  .rot { fill: #dfe5ea; font-size: 25px; font-weight: 500; text-anchor: middle; }
  .carga { fill: var(--verde); font-size: 15px; font-weight: 700; letter-spacing: 2px; text-anchor: middle; opacity: 0.85; }

  .cubo { position: absolute; width: 216px; text-align: center; top: 378px; }
  .cubo .n { font-size: 78px; font-weight: 300; line-height: 78px; letter-spacing: -1px; font-variant-numeric: tabular-nums; }
  .cubo .u { font-size: 20px; color: var(--dim); margin-top: 6px; }
  #vel { left: ${CE - 108}px; }
  #vel.acima .n { color: #ffb020; }
  #pot { left: ${CD - 108}px; }
  #pot .n small { font-size: 34px; font-weight: 400; margin-left: 2px; }
  #pot.desligado .n { color: var(--dim); }

  #topo { position: absolute; left: 700px; top: 22px; width: 520px; height: 80px; }
  #topo .t { position: absolute; top: 18px; font-size: 30px; font-weight: 400; color: #e6ebf0; font-variant-numeric: tabular-nums; }
  #temp { left: 40px; }
  #hora { right: 40px; }
  #marcha { position: absolute; left: 0; right: 0; top: 4px; text-align: center; font-size: 54px; font-weight: 600; line-height: 60px; }
  /* Modo de condução e de energia embaixo da marcha (o lugar do READY na foto: o READY é do carro). */
  #modos { position: absolute; left: 0; right: 0; top: 72px; text-align: center; font-size: 16px; font-weight: 500; letter-spacing: 3px; color: #cfd6de; white-space: nowrap; }
  #modos i { font-style: normal; color: #5d6874; margin: 0 8px; }
  #energia { color: var(--verde); }
  body.modo-sport #modo { color: #ff6b61; }
  body.modo-eco #modo { color: var(--verde); }

  .aut { position: absolute; top: 646px; height: 60px; }
  .aut svg.ic { position: absolute; left: 0; top: 10px; width: 30px; height: 30px; }
  .aut .v { position: absolute; left: 42px; top: 4px; font-size: 32px; font-weight: 400; white-space: nowrap; font-variant-numeric: tabular-nums; }
  .aut .v small { font-size: 18px; color: var(--dim); margin-left: 4px; }
  .aut .barra { position: absolute; left: 0; top: 50px; width: 150px; height: 4px; border-radius: 2px; background: rgba(255,255,255,0.12); overflow: hidden; }
  .aut .barra i { position: absolute; left: 0; top: 0; bottom: 0; width: 150px; transform-origin: 0 0; transform: scaleX(0); border-radius: 2px; }
  #autEv { left: 40px; }
  #autEv .barra i { background: var(--verde); }
  #autComb { left: 398px; }
  #autComb .v { font-size: 28px; top: 7px; }
  #autComb .barra i { background: var(--azul); }

  #odo { position: absolute; top: 656px; right: 40px; font-size: 28px; font-variant-numeric: tabular-nums; white-space: nowrap; }
  #odo small { font-size: 18px; color: var(--dim); margin-left: 4px; }
  #odo .r { font-size: 14px; letter-spacing: 2px; color: var(--dim); margin-right: 10px; }

  #midia {
    position: absolute; left: 770px; width: 380px; top: 640px; height: 60px; display: none; box-sizing: border-box;
    border-radius: 14px; background: rgba(12,18,28,0.82); box-shadow: inset 0 0 0 1px rgba(255,255,255,0.10);
  }
  body.com-midia #midia { display: block; }
  #midia .capa { position: absolute; left: 8px; top: 8px; width: 44px; height: 44px; border-radius: 8px; background: #1a2230 center/cover no-repeat; box-shadow: 0 0 0 1px rgba(255,255,255,0.12); }
  #midia .ti { position: absolute; left: 64px; right: 14px; top: 9px; font-size: 20px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  #midia .ar { position: absolute; left: 64px; right: 14px; top: 34px; font-size: 16px; color: var(--dim); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
</head>
<body>
<div id="bg"></div>
<div id="projscrim"></div>
<div id="projfumo"></div>
<div id="app">
  <!-- GERADO:arte:inicio -->
  ${SVG}
  <!-- GERADO:arte:fim -->
  <div id="vel" class="cubo"><div class="n" id="velValor">0</div><div class="u">km/h</div></div>
  <div id="pot" class="cubo"><div class="n"><span id="potValor">0</span><small>%</small></div><div class="u">Potência</div></div>
  <div id="topo">
    <div class="t" id="temp">--°C</div>
    <div id="marcha">P</div>
    <div id="modos"><span id="modo">NORMAL</span><i>·</i><span id="energia">HEV</span></div>
    <div class="t" id="hora">--:--</div>
  </div>
  <div id="autEv" class="aut">
    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="#e6ebf0" stroke-width="1.8" stroke-linejoin="round"><rect x="2.5" y="7" width="17" height="11" rx="2"/><path d="M19.5 10.5h2v4h-2"/><path d="M11.5 9 8.5 13h4l-3 4" stroke="#34d17a"/></svg>
    <div class="v"><span id="evKm">--</span><small>km</small></div>
    <div class="barra"><i id="evBarra"></i></div>
  </div>
  <div id="autComb" class="aut">
    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="#e6ebf0" stroke-width="1.8" stroke-linejoin="round"><path d="M4 21V5a2 2 0 0 1 2-2h7a2 2 0 0 1 2 2v16"/><path d="M3 21h13"/><path d="M6.5 8h6"/><path d="M15 10h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V9l-3-3"/></svg>
    <div class="v"><span id="combKm">---</span><small>km</small></div>
    <div class="barra"><i id="combBarra"></i></div>
  </div>
  <div id="midia"><div class="capa" id="capa"></div><div class="ti" id="titulo"></div><div class="ar" id="artista"></div></div>
  <div id="odo"><span class="r">ODO</span><span id="odoValor">---</span><small>km</small></div>
</div>
<div id="noite"></div>
<script>
(function () {
  'use strict';
  const $ = (id) => document.getElementById(id);
  const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  /* GERADO:tabelas:inicio */
  ${TABELAS}
  /* GERADO:tabelas:fim */

  const els = {
    app: $('app'), bg: $('bg'), vel: $('vel'), velValor: $('velValor'), pot: $('pot'), potValor: $('potValor'),
    pontE: $('pontE'), pontD: $('pontD'),
    limite: $('limite'), temp: $('temp'), hora: $('hora'), marcha: $('marcha'),
    evKm: $('evKm'), evBarra: $('evBarra'), combKm: $('combKm'), combBarra: $('combBarra'),
    modo: $('modo'), energia: $('energia'), odoValor: $('odoValor'),
    capa: $('capa'), titulo: $('titulo'), artista: $('artista'),
  };

  function numero(v) { const n = parseFloat(v); return Number.isFinite(n) ? n : null; }
  function ligado(v) {
    return v === true || ['1', 'true', 'on', 'yes', 'ligado'].indexOf(String(v == null ? '' : v).trim().toLowerCase()) >= 0;
  }
  function milhar(n) { return String(n).replace(/\\B(?=(\\d{3})+(?!\\d))/g, '.'); }
  const escrito = {};
  function mudou(chave, valor) { if (escrito[chave] === valor) return false; escrito[chave] = valor; return true; }

  // ----- Suavização (a mesma do SENSUS/MBUX/ID8): filtro criticamente amortecido no ângulo. -----
  function canal(ms, eps) { return { alvo: null, atual: null, taxa: 0, omega: 4.744 / Math.max(0.001, ms / 1000), eps: eps }; }
  function mirar(c, v) { if (c.atual === null) { c.atual = v; c.alvo = v; c.taxa = 0; return; } c.alvo = v; }
  function passo(c, dt) {
    if (c.atual === null || (c.atual === c.alvo && c.taxa === 0)) return false;
    if (dt > 0.4) { c.atual = c.alvo; c.taxa = 0; return false; }
    const e0 = c.atual - c.alvo, b = c.taxa + c.omega * e0, k = Math.exp(-c.omega * dt);
    c.atual = c.alvo + (e0 + b * dt) * k;
    c.taxa = (c.taxa - c.omega * b * dt) * k;
    if (Math.abs(c.alvo - c.atual) <= c.eps && Math.abs(c.taxa) <= c.omega * c.eps) { c.atual = c.alvo; c.taxa = 0; return false; }
    return true;
  }
  const C = { vel: canal(300, 0.02), pot: canal(320, 0.02) };
  function anguloDe(tab, v) {
    if (v <= tab[0][0]) return tab[0][1];
    for (let i = 1; i < tab.length; i++) {
      if (v <= tab[i][0]) { const a = tab[i - 1], b = tab[i]; return a[1] + (b[1] - a[1]) * (v - a[0]) / (b[0] - a[0]); }
    }
    return tab[tab.length - 1][1];
  }

  const est = { velocidade: 0, potencia: 0, pronto: null, prontoPeloReady: false, limite: null, limiteOk: false,
    tempExt: null, fahrenheit: false, modo: '0', modoEnergia: '0', bat: null, comb: null,
    midia: { titulo: '', artista: '', capa: '' } };

  // ----- Desenho: um quadro por vez, só enquanto os ponteiros andam. -----
  // Acende as primeiras k fatias do arco: só as que mudaram de lado são tocadas.
  function montarArco(id) { return { itens: Array.prototype.slice.call($(id).children), acesas: 0 }; }
  const ARCOS = { vel: montarArco('arcoVel'), pot: montarArco('arcoPot'), carga: montarArco('arcoCarga') };
  function acender(arco, fracao) {
    const k = clamp(Math.round(fracao * arco.itens.length), 0, arco.itens.length);
    if (k === arco.acesas) return;
    const a = Math.min(k, arco.acesas), b = Math.max(k, arco.acesas);
    for (let i = a; i < b; i++) arco.itens[i].setAttribute('visibility', i < k ? 'visible' : 'hidden');
    arco.acesas = k;
  }
  function desenharVel(a) {
    const tf = 'rotate(' + (-a).toFixed(2) + ' ' + GEO.ce + ' ' + GEO.cy + ')';
    if (mudou('pE', tf)) els.pontE.setAttribute('transform', tf);
    acender(ARCOS.vel, clamp((GEO.ini - a) / (GEO.ini - GEO.fim), 0, 1));
  }
  function desenharPot(a) {
    const tf = 'rotate(' + (-a).toFixed(2) + ' ' + GEO.cd + ' ' + GEO.cy + ')';
    if (mudou('pD', tf)) els.pontD.setAttribute('transform', tf);
    // Potência: de 180° para a direita; carga: de 180° para baixo (até 225°).
    acender(ARCOS.pot, a <= 180 ? clamp((180 - a) / (180 - GEO.fim), 0, 1) : 0);
    acender(ARCOS.carga, a > 180 ? clamp((a - 180) / (GEO.ini - 180), 0, 1) : 0);
  }
  let renderScheduled = false, renderFallbackTimer = 0, ultimoQuadro = 0;
  function flushRender(ts) {
    if (!renderScheduled) return;
    renderScheduled = false;
    clearTimeout(renderFallbackTimer);
    const agora = typeof ts === 'number' && ts > 0 ? ts : performance.now();
    const dt = ultimoQuadro ? (agora - ultimoQuadro) / 1000 : 0;
    ultimoQuadro = agora;
    const a = passo(C.vel, dt), b = passo(C.pot, dt);
    if (C.vel.atual !== null) desenharVel(C.vel.atual);
    if (C.pot.atual !== null) desenharPot(C.pot.atual);
    if (a || b) scheduleRender(); else ultimoQuadro = 0;
  }
  function scheduleRender() {
    if (renderScheduled) return;
    renderScheduled = true;
    requestAnimationFrame(flushRender);
    // Com o WebView oculto o rAF não dispara; os valores não podem congelar.
    renderFallbackTimer = setTimeout(flushRender, 120);
  }

  function mirarPotencia() {
    // Desligado: o ponteiro descansa no zero, apagado. Pronto (READY): mostra a potência.
    const desligado = est.pronto === false;
    if (mudou('desl', desligado)) els.pot.classList.toggle('desligado', desligado);
    const p = desligado ? 0 : est.potencia;
    const r = Math.round(p);
    const txt = (r < 0 ? '−' : '') + Math.abs(r);
    if (mudou('potTxt', txt)) els.potValor.textContent = txt;
    mirar(C.pot, anguloDe(ESC_POT, p));
    scheduleRender();
  }
  function atualizarLimite() {
    const ok = est.limiteOk && est.limite !== null && est.limite > 0;
    els.limite.setAttribute('visibility', ok ? 'visible' : 'hidden');
    if (ok) els.limite.setAttribute('transform', 'rotate(' + (-anguloDe(ESC_VEL, clamp(est.limite, 0, 260))).toFixed(2) + ' ' + GEO.ce + ' ' + GEO.cy + ')');
    const acima = ok && est.velocidade > est.limite + 3;
    if (mudou('acima', acima)) els.vel.classList.toggle('acima', acima);
  }
  function desenharTemp() {
    if (est.tempExt === null) return;
    const v = Math.round(est.fahrenheit ? est.tempExt * 9 / 5 + 32 : est.tempExt);
    els.temp.textContent = (v < 0 ? '−' : '') + Math.abs(v) + (est.fahrenheit ? '°F' : '°C');
  }
  function barra(el, pct) { el.style.transform = 'scaleX(' + (clamp(pct, 0, 100) / 100).toFixed(3) + ')'; }

  const GEAR_LABELS = { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' };
  const DRIVE_MODE_LABELS = { '0': 'NORMAL', '1': 'SPORT', '2': 'ECO', '3': 'NEVE', '4': 'AREIA', '5': 'LAMA', '11': 'AWD' };
  const EV_MODE_LABELS = { '0': 'HEV', '1': 'EV PRIOR.', '3': 'EV' };
  function atualizarModos() {
    els.modo.textContent = DRIVE_MODE_LABELS[est.modo] || 'NORMAL';
    els.energia.textContent = EV_MODE_LABELS[est.modoEnergia] || 'HEV';
    document.body.classList.toggle('modo-sport', est.modo === '1');
    document.body.classList.toggle('modo-eco', est.modo === '2');
  }
  function atualizarMidia() {
    const m = est.midia;
    const tem = m.titulo.trim().length > 0;
    document.body.classList.toggle('com-midia', tem);
    if (!tem) return;
    els.titulo.textContent = m.titulo;
    els.artista.textContent = m.artista;
    els.capa.style.backgroundImage = m.capa ? 'url("' + m.capa.replace(/"/g, '%22') + '")' : '';
  }

  function handleTelemetry(key, raw) {
    switch (key) {
      case 'car.basic.vehicle_speed': {
        const v = numero(raw);
        est.velocidade = v === null ? 0 : Math.max(0, v);
        const txt = String(Math.round(est.velocidade));
        if (mudou('vel', txt)) els.velValor.textContent = txt;
        mirar(C.vel, anguloDe(ESC_VEL, clamp(est.velocidade, 0, 260)));
        atualizarLimite();
        scheduleRender();
        break;
      }
      case 'car.ev_info.energy_output_percentage':
        est.potencia = clamp(numero(raw) || 0, -100, 100);
        mirarPotencia();
        break;
      // READY vem de driving_ready_state ("1" pronto); engine_state é ignição (11 em READY).
      case 'car.basic.driving_ready_state': {
        const s = String(raw).trim();
        est.prontoPeloReady = s !== '';
        est.pronto = s === '1' ? true : (s === '0' || s === '-1') ? false : null;
        mirarPotencia();
        break;
      }
      case 'car.basic.engine_state': {
        if (est.prontoPeloReady) break;
        const n = parseInt(raw, 10);
        est.pronto = Number.isFinite(n) ? (n === 11 || n === 13) : null;
        mirarPotencia();
        break;
      }
      case 'car.ev_info.cur_battery_power_percentage': {
        const p = numero(raw);
        if (p !== null) barra(els.evBarra, p);
        break;
      }
      case 'car.basic.remain_fuel_percentage': {
        const p = numero(raw);
        if (p !== null) barra(els.combBarra, p);
        break;
      }
      case 'car.ev_info.electric_mode_remain_odometer': {
        const km = numero(raw);
        els.evKm.textContent = km === null || km < 0 ? '--' : Math.round(km);
        break;
      }
      case 'car.ev_info.fuel_mode_remain_odometer': {
        const km = numero(raw);
        els.combKm.textContent = km === null || km < 0 ? '---' : Math.round(km);
        break;
      }
      case 'car.basic.total_odometer': {
        const km = numero(raw);
        if (km !== null && km >= 0) els.odoValor.textContent = milhar(Math.round(km));
        break;
      }
      case 'car.basic.gear_status': {
        const s = String(raw == null ? '' : raw).trim().toUpperCase();
        const letra = /^[PRND]$/.test(s) ? s : (GEAR_LABELS[String(parseInt(s, 10))] || 'N');
        if (mudou('marcha', letra)) els.marcha.textContent = letra;
        break;
      }
      case 'car.drive_setting.drive_mode': {
        const m = String(parseInt(raw, 10));
        est.modo = DRIVE_MODE_LABELS[m] ? m : '0';
        atualizarModos();
        break;
      }
      case 'car.ev_setting.power_model_config':
        est.modoEnergia = String(raw).trim();
        atualizarModos();
        break;
      case 'car.basic.outside_temp': {
        const t = numero(raw);
        if (t !== null) { est.tempExt = t; desenharTemp(); }
        break;
      }
      case 'car.configure.default_temp_unit':
        est.fahrenheit = String(raw).trim() === '1';
        desenharTemp();
        break;
      case 'car.basic.head_light_status':
        document.body.classList.toggle('mode-headlight', ligado(raw));
        break;
      case 'car.map.tsr.nav_speed_limit':
        est.limite = numero(raw);
        atualizarLimite();
        break;
      case 'car.map.tsr.nav_speed_limit_sign_status':
        est.limiteOk = ligado(raw);
        atualizarLimite();
        break;
      case 'app.media.title': est.midia.titulo = raw ? String(raw) : ''; atualizarMidia(); break;
      case 'app.media.artist': est.midia.artista = raw ? String(raw) : ''; atualizarMidia(); break;
      case 'app.media.album_art': est.midia.capa = raw ? String(raw).trim() : ''; atualizarMidia(); break;
      default: break;
    }
  }

  // ----- Ceder a tela: só projeção de verdade (CarPlay/Android Auto/espelhamento). -----
  const projectionState = { carPlayInDash: false, projectionMirrorInDash: false, projectionPreparingD3: false };
  function applyYieldToCar() {
    const yielding = projectionState.carPlayInDash || projectionState.projectionMirrorInDash || projectionState.projectionPreparingD3;
    els.app.classList.toggle('hidden', yielding);
    document.body.classList.toggle('yield-to-car', yielding);
  }

  // ----- Vão da projeção da central -----
  // De um centro de mostrador ao outro, como no NTG 5.5 (coffe3): as bordas caem embaixo das faces
  // (opacas), e a imagem ocupa o lugar da estrada, que sai da frente enquanto projeta.
  const PROJ_BOX = { x: 426, y: 118, w: 1068, h: 522 };
  const PROJ_PENA_X = 96;
  const PROJ_PENA_Y = 56;
  function sabeRecortarOFundo() {
    try {
      if (!window.CSS || typeof window.CSS.supports !== 'function') return false;
      const g = 'linear-gradient(to right, #000 0px, transparent 10px)';
      return window.CSS.supports('-webkit-mask-image', g) || window.CSS.supports('mask-image', g);
    } catch (e) { return false; }
  }
  let recortaOFundo = null;
  function declararVaoDaProjecao() {
    if (recortaOFundo === null) {
      recortaOFundo = sabeRecortarOFundo();
      if (!recortaOFundo) console.warn('[TEA 3.0] sem suporte a mascara: a projecao vai por cima do tema');
    }
    if (!recortaOFundo) return;
    try {
      if (window.Android && typeof window.Android.setProjectionBox === 'function') {
        window.Android.setProjectionBox(PROJ_BOX.x, PROJ_BOX.y, PROJ_BOX.w, PROJ_BOX.h);
      }
    } catch (e) { /* ponte antiga: sem declaração, o app volta a varrer o DOM */ }
  }
  function aplicarVaoDaProjecao(valor) {
    const bg = els.bg;
    const n = String(valor == null ? '' : valor).split(',');
    const x = parseInt(n[0], 10), y = parseInt(n[1], 10), l = parseInt(n[2], 10), a = parseInt(n[3], 10);
    if (n.length < 4 || !(l > 0) || !(a > 0)) {
      bg.style.webkitMaskImage = '';
      bg.style.maskImage = '';
      document.body.classList.remove('projetando');
      return;
    }
    const L = x, R = x + l, T = y, B = y + a;
    const px = Math.min(PROJ_PENA_X, Math.floor(l / 3)), py = Math.min(PROJ_PENA_Y, Math.floor(a / 3));
    const pe = L <= 0 ? 0 : px, pd = R >= 1920 ? 0 : px, pt = T <= 0 ? 0 : py, pb = B >= 720 ? 0 : py;
    const gx = 'linear-gradient(to right, #000 ' + L + 'px, transparent ' + (L + pe) + 'px, transparent ' + (R - pd) + 'px, #000 ' + R + 'px)';
    const gy = 'linear-gradient(to bottom, #000 ' + T + 'px, transparent ' + (T + pt) + 'px, transparent ' + (B - pb) + 'px, #000 ' + B + 'px)';
    const veu = $('projscrim');
    if (veu) {
      veu.style.left = L + 'px'; veu.style.top = T + 'px'; veu.style.width = l + 'px'; veu.style.height = a + 'px';
      const mv = 'linear-gradient(to right, transparent 0px, #000 ' + pe + 'px, #000 ' + (l - pd) + 'px, transparent ' + l + 'px)';
      veu.style.webkitMaskImage = mv;
      veu.style.maskImage = mv;
    }
    bg.style.webkitMaskImage = gx + ', ' + gy;
    bg.style.maskImage = gx + ', ' + gy;
    document.body.classList.add('projetando');
  }

  const SUBSCRIBE_KEYS = [
    'car.basic.vehicle_speed',
    'car.ev_info.energy_output_percentage',
    'car.basic.driving_ready_state',
    'car.basic.engine_state',
    'car.ev_info.cur_battery_power_percentage',
    'car.basic.remain_fuel_percentage',
    'car.ev_info.electric_mode_remain_odometer',
    'car.ev_info.fuel_mode_remain_odometer',
    'car.basic.total_odometer',
    'car.basic.gear_status',
    'car.drive_setting.drive_mode',
    'car.ev_setting.power_model_config',
    'car.basic.outside_temp',
    'car.configure.default_temp_unit',
    'car.basic.head_light_status',
    'car.map.tsr.nav_speed_limit',
    'car.map.tsr.nav_speed_limit_sign_status',
    'app.media.title',
    'app.media.artist',
    'app.media.album_art',
    'warningActive'
  ];

  function lerPreferencias() {
    try {
      if (!window.Android || typeof window.Android.getPreference !== 'function') return;
      const modo = String(window.Android.getPreference('displayMode', 'Dia') || '');
      document.body.classList.toggle('night-mode', modo === 'Noite');
    } catch (e) { /* ignore */ }
  }
  function tickClock() {
    const d = new Date();
    els.hora.textContent = String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
  }

  // ----- Simulação local (sem window.Android): ?vel=, ?pot=, ?off=1, ?proj=1, ?mode=Noite. -----
  function installMockBridge() {
    console.warn('[TEA 3.0] window.Android ausente — usando simulação local para preview.');
    const q = new URLSearchParams(location.search);
    window.Android = {
      getAvailableKeys: () => JSON.stringify(SUBSCRIBE_KEYS),
      subscribe: () => {}, unsubscribe: () => {}, getCarData: () => '0', updateCarData: () => {},
      getPreference: (k, d) => (k === 'displayMode' ? (q.get('mode') || d) : d),
      savePreference: () => {}, heartbeat: () => {}, triggerSystemAction: () => {},
      setAppDefaultDimensions: () => {}, setProjectionBox: () => {},
    };
    const seed = {
      'car.basic.vehicle_speed': q.get('vel') || '0',
      'car.ev_info.energy_output_percentage': q.get('pot') || '0',
      'car.basic.driving_ready_state': q.get('off') === '1' ? '0' : '1',
      'car.ev_info.cur_battery_power_percentage': '38',
      'car.basic.remain_fuel_percentage': '55',
      'car.ev_info.electric_mode_remain_odometer': '39',
      'car.ev_info.fuel_mode_remain_odometer': '219',
      'car.basic.total_odometer': '451',
      'car.basic.gear_status': '2',
      'car.drive_setting.drive_mode': '0',
      'car.ev_setting.power_model_config': '0',
      'car.basic.outside_temp': '24',
      'car.basic.head_light_status': '0',
    };
    Object.keys(seed).forEach((k) => handleTelemetry(k, seed[k]));
    if (q.get('midia') === '1') { handleTelemetry('app.media.title', 'Faixa de demonstração'); handleTelemetry('app.media.artist', 'Artista'); }
    if (q.get('proj') === '1') window.control('projectionWindow', PROJ_BOX.x + ',' + PROJ_BOX.y + ',' + PROJ_BOX.w + ',' + PROJ_BOX.h);
    if (q.get('anda') !== '1') return;
    let t = 0, vel = 0;
    setInterval(() => {
      t += 0.1;
      vel += clamp(110 + 100 * Math.sin(t / 6) - vel, -2.4, 1.8);
      handleTelemetry('car.basic.vehicle_speed', vel.toFixed(1));
      handleTelemetry('car.ev_info.energy_output_percentage', String(Math.round(70 * Math.sin(t / 2.2))));
    }, 100);
  }

  function installHandlers() {
    const prevOnDataChanged = window.onDataChanged;
    window.onDataChanged = function (key, value) {
      if (typeof prevOnDataChanged === 'function') { try { prevOnDataChanged(key, value); } catch (e) { console.error(e); } }
      handleTelemetry(key, value);
    };
    window.onCardChanged = function () {};
    const prevControl = window.control;
    window.control = function (key, value) {
      if (typeof prevControl === 'function') { try { prevControl(key, value); } catch (e) { /* ignore */ } }
      if (Object.prototype.hasOwnProperty.call(projectionState, key)) {
        projectionState[key] = (value === true || value === 'true');
        applyYieldToCar();
        return;
      }
      if (key === 'projectionWindow') { aplicarVaoDaProjecao(value); return; }
    };
  }

  function initBridge() {
    installHandlers();
    if (!window.Android || typeof window.Android.subscribe !== 'function') installMockBridge();
    try {
      if (window.Android && typeof window.Android.subscribe === 'function') window.Android.subscribe(JSON.stringify(SUBSCRIBE_KEYS));
      if (window.Android && typeof window.Android.setAppDefaultDimensions === 'function') window.Android.setAppDefaultDimensions(640, 110, 640, 520);
      declararVaoDaProjecao();
      lerPreferencias();
    } catch (e) { console.error('[TEA 3.0] bridge init failed:', e); }
    setInterval(() => {
      try { if (window.Android && typeof window.Android.heartbeat === 'function') window.Android.heartbeat(); } catch (e) { /* ignore */ }
      lerPreferencias();
      try { declararVaoDaProjecao(); } catch (e) { /* ignore */ }
    }, 2000);
  }

  tickClock();
  setInterval(tickClock, 5000);
  mirar(C.vel, GEO.ini);
  mirar(C.pot, 180);
  desenharVel(GEO.ini);
  desenharPot(180);
  atualizarModos();
  initBridge();
})();
</script>
</body>
</html>
`

if (process.argv.includes('--verificar')) {
  const atual = fs.existsSync(arquivo) ? fs.readFileSync(arquivo, 'utf8') : ''
  if (atual !== html) {
    console.error('FALHOU: o app.html do TEA 3.0 não bate com scripts/gerar-tea-30.mjs')
    process.exit(1)
  }
  console.log('ok: app.html do TEA 3.0 bate com o gerador')
} else {
  fs.mkdirSync(path.dirname(arquivo), { recursive: true })
  fs.writeFileSync(arquivo, html)
  console.log('gravado', path.relative(raiz, arquivo), (html.length / 1024).toFixed(1) + ' KB')
}
