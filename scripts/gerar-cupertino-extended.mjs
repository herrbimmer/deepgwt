// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Tema Cupertino Extended (assets/PreinstalledThemes/cupertinoextended). Alpha 9.5; refeito na 9.6.
//
// Pedido do dono: um painel na linguagem visual da Apple, par do estilo "Cupertino" da interface do
// DeepGWT na central. A 9.5 foi reprovada ("ridículo"). O que estava errado nela:
//  • fundo em malha de azul, roxo, verde-água e rosa — papel de parede de celular, não instrumento;
//  • discos de "vidro" com brilho radial, que liam como bolas de boliche;
//  • arco grosso com um botão de controle deslizante no fim — gesto de tela de toque num medidor;
//  • capa roxa, anéis sem rótulo e três cartões disputando o centro;
//  • potência com o zero em cima e "−55%" em corpo gigante.
//
// O DESENHO DA 9.6 (sóbrio, como os mostradores do CarPlay e o relógio da Apple)
//
//  • Fundo preto. Faces em grafite quase preto, com o friso de vidro líquido na borda (luz no alto
//    à esquerda, reflexo embaixo à direita) e nada de brilho dentro.
//  • Escala fina: traço a cada 5 km/h, traço maior e número a cada 20; números por fora, na fonte
//    do sistema. Ponteiro curto em laranja (o laranja do ponteiro de segundos da Apple), só na
//    coroa, para não cruzar a leitura digital; uma varredura fina em branco acompanha o valor.
//  • Potência como a de fábrica: carga à esquerda (−100 a 0, em verde) e potência até 100. A
//    leitura diz "Potência" ou "Recarga" e mostra o número sem sinal.
//  • Marcha P R N D dentro do velocímetro, com a letra acesa numa cápsula de vidro; autonomias
//    elétrica e a combustível dentro do mostrador da direita.
//  • Em cima: temperatura, hora e modo de condução. No centro: "Tocando agora" como o do Apple
//    Music (capa, faixa, artista e barra; sem música, "Nada tocando" com a capa vazia em cinza) e
//    embaixo as cápsulas de bateria e combustível, como o widget de bateria do iOS. Hodômetro no pé.
//  • Nenhum roxo.
//
// SEM CAMADA COMPOSTA
//
// Nada de backdrop-filter nem de will-change (memória "temas sem camada composta": o WebView do
// carro desenha errado camadas compostas). O que gira é o ponteiro (um grupo pequeno, por
// transform, só quando o ângulo muda); as varreduras são fatias de 2,25° que só trocam de
// visibilidade quando o valor as cruza. A cápsula da marcha muda de lugar sem transição.
//
// AS LUZES DO CARRO
//
// Os mostradores ficam no lugar dos de fábrica, (426, 440) e (1494, 440), como no TEA 3.0 e no
// SENSUS 9.3. A face lisa vai até o raio 230: o READY (até 218 px do centro, com a folga de 10) e
// a placa (até 227) caem nela. Traços de 238 a 258 e números a 284, por dentro da diagonal de luzes
// (a mais perto, a 309 com a folga). A escala se abre embaixo (225° a 315°), onde acende a luz do
// combustível. O tema não desenha luz.
//
//   node scripts/gerar-cupertino-extended.mjs              grava app.html
//   node scripts/gerar-cupertino-extended.mjs --verificar  falha se o app.html não bater

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const arquivo = path.join(raiz, 'app', 'src', 'main', 'assets', 'PreinstalledThemes', 'cupertinoextended', 'app.html')

const r1 = (n) => Math.round(n * 10) / 10
const rad = (a) => (a * Math.PI) / 180
const pt = (cx, cy, r, a) => [r1(cx + r * Math.cos(rad(a))), r1(cy - r * Math.sin(rad(a)))]

const CY = 440
const CE = 426
const CD = 1494
const R = { face: 230, varredura: 233, largura: 4, tracoIn: 240, tracoMenor: 249, tracoOut: 258, rotulo: 284, agulhaIn: 168, agulhaOut: 263 }
const INI = 225
const FIM = -45

// Velocidade 0-240: 1,125° por km/h. Traço a cada 5, número a cada 20.
const ESC_VEL = [[0, INI], [240, FIM]]
const angVel = (v) => INI - (v / 240) * (INI - FIM)
// Potência como a de fábrica: carga de −100 a 0 em 45° (225° a 180°), potência de 0 a 100 em 225°.
const ESC_POT = [[-100, 225], [0, 180], [100, -45]]
const angPot = (p) => (p <= 0 ? 180 + (-p / 100) * 45 : 180 - (p / 100) * 225)

function arco(cx, r, a0, a1) {
  const [x0, y0] = pt(cx, CY, r, a0)
  const [x1, y1] = pt(cx, CY, r, a1)
  const grande = Math.abs(a0 - a1) > 180 ? 1 : 0
  const horario = a0 > a1 ? 1 : 0
  return `M${x0} ${y0} A${r} ${r} 0 ${grande} ${horario} ${x1} ${y1}`
}
const menos = (v) => (v < 0 ? '−' + Math.abs(v) : String(v))

function traco(cx, a, maior, cor) {
  const [x0, y0] = pt(cx, CY, maior ? R.tracoIn : R.tracoMenor, a)
  const [x1, y1] = pt(cx, CY, R.tracoOut, a)
  return `<line x1="${x0}" y1="${y0}" x2="${x1}" y2="${y1}" stroke="${cor}" stroke-width="${maior ? 3 : 2}"/>`
}
function rotulo(cx, a, texto) {
  const [x, y] = pt(cx, CY, R.rotulo, a)
  return `<text class="rot" x="${x}" y="${r1(y + 8)}">${texto}</text>`
}

function escalaVel() {
  let s = ''
  for (let v = 0; v <= 240; v += 5) s += traco(CE, angVel(v), v % 20 === 0, v % 20 === 0 ? 'rgba(255,255,255,0.88)' : 'rgba(255,255,255,0.30)')
  for (let v = 0; v <= 240; v += 20) s += rotulo(CE, angVel(v), v)
  return s
}
function escalaPot() {
  let s = ''
  // Um traço a cada 11,25°: 25 % na carga, 5 % na potência. Maior em −100, 0 e a cada 20 %.
  for (let p = -100; p < 0; p += 25) s += traco(CD, angPot(p), p === -100, p === -100 ? 'rgba(255,255,255,0.88)' : 'rgba(48,209,88,0.55)')
  for (let p = 0; p <= 100; p += 5) s += traco(CD, angPot(p), p % 20 === 0, p % 20 === 0 ? 'rgba(255,255,255,0.88)' : 'rgba(255,255,255,0.30)')
  s += rotulo(CD, angPot(-100), menos(-100))
  for (let p = 0; p <= 100; p += 20) s += rotulo(CD, angPot(p), p)
  return s
}

// A face lisa e o friso de vidro na borda. O friso se abre embaixo junto com a escala (225° a
// 315°): ali acende a luz do combustível (247° a 262°, com a folga), e o aro não pode passar por
// baixo de luz. O miolo pode.
function face(cx) {
  return `<circle cx="${cx}" cy="${CY}" r="${R.face}" fill="url(#gFace)"/>
    <path d="${arco(cx, R.face, INI, FIM)}" fill="none" stroke="url(#gFriso)" stroke-width="1.5" stroke-linecap="round"/>`
}

const DEFS = `<defs>
  <radialGradient id="gFace" cx="0.5" cy="0.4" r="0.62">
    <stop offset="0" stop-color="#18181b"/><stop offset="1" stop-color="#0c0c0e"/>
  </radialGradient>
  <linearGradient id="gFriso" x1="0.15" y1="0" x2="0.85" y2="1">
    <stop offset="0" stop-color="#ffffff" stop-opacity="0.42"/><stop offset="0.38" stop-color="#ffffff" stop-opacity="0.08"/>
    <stop offset="0.72" stop-color="#ffffff" stop-opacity="0.05"/><stop offset="1" stop-color="#ffffff" stop-opacity="0.22"/>
  </linearGradient>
</defs>`

// Varreduras em fatias de FATIA graus (o princípio do SENSUS): o JS só troca a visibilidade das
// fatias que o valor cruzou — mexer no dashoffset de um arco inteiro repintaria o mostrador inteiro
// a cada quadro.
const FATIA = 2.25
function fatias(id, cx, a0, a1, cor) {
  const n = Math.round(Math.abs(a1 - a0) / FATIA)
  const passo = (a1 - a0) / n
  let s = ''
  for (let i = 0; i < n; i++) {
    const b = a0 + passo * (i + 1) + Math.sign(passo) * (i < n - 1 ? 0.3 : 0)
    s += `<path d="${arco(cx, R.varredura, a0 + passo * i, b)}" visibility="hidden"/>`
  }
  return `<g id="${id}" fill="none" stroke="${cor}" stroke-width="${R.largura}">${s}</g>`
}
// Ponteiro curto, só na coroa: afina da base para a ponta. Desenhado apontando para 0° e girado.
function ponteiro(cx, id, ang) {
  const a = cx + R.agulhaIn
  const b = cx + R.agulhaOut
  return `<g id="${id}" transform="rotate(${-ang} ${cx} ${CY})">
    <path d="M${a} ${CY - 4.5} L${b} ${CY - 1.8} Q${b + 2} ${CY} ${b} ${CY + 1.8} L${a} ${CY + 4.5} Q${a - 3.5} ${CY} ${a} ${CY - 4.5} Z" fill="#ff9f0a" stroke="rgba(0,0,0,0.6)" stroke-width="1.5"/>
  </g>`
}

const SVG = `<svg id="arte" viewBox="0 0 1920 720" preserveAspectRatio="none" aria-hidden="true">
${DEFS}
<g id="mE">${face(CE)}${escalaVel()}</g>
<g id="mD">${face(CD)}${escalaPot()}</g>
${fatias('arcoVel', CE, INI, FIM, 'rgba(255,255,255,0.85)')}
${fatias('arcoPot', CD, 180, FIM, 'rgba(255,255,255,0.85)')}
${fatias('arcoRegen', CD, 180, INI, '#30d158')}
<g id="limite" visibility="hidden"><line x1="${CE + R.tracoIn - 4}" y1="${CY}" x2="${CE + R.tracoOut + 4}" y2="${CY}" stroke="#ff453a" stroke-width="5" stroke-linecap="round"/></g>
${ponteiro(CE, 'pontE', INI)}
${ponteiro(CD, 'pontD', 180)}
</svg>`

// Ícones pequenos, no traço dos SF Symbols.
const RAIO = `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M13 2 4.5 13.2h6L10 22l8.5-11.2h-6L13 2Z" fill="currentColor"/></svg>`
const BOMBA = `<svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16"/><path d="M3 21h12"/><path d="M4 10h10"/><path d="M14 8h2a2 2 0 0 1 2 2v6a1.5 1.5 0 0 0 3 0V9l-3-3"/></svg>`
const NOTA = `<svg viewBox="0 0 24 24" aria-hidden="true" fill="currentColor"><path d="M19 3.2v11.6a3.2 3.2 0 1 1-2-2.96V7.4L9 9.1v7.7a3.2 3.2 0 1 1-2-2.96V5.6c0-.47.33-.88.79-.98l10-2.2A1 1 0 0 1 19 3.2Z"/></svg>`

const TABELAS = `const ESC_VEL = ${JSON.stringify(ESC_VEL)};
  const ESC_POT = ${JSON.stringify(ESC_POT)};
  const GEO = { ce: ${CE}, cd: ${CD}, cy: ${CY}, ini: ${INI}, fim: ${FIM}, fatia: ${FATIA} };`

const html = `<!doctype html>
<html lang="pt-BR">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=1920, height=720, initial-scale=1" />
<title>Cupertino Extended</title>
<!--
  Cupertino Extended (DeepGWT, Alpha 9.6). Gerado por scripts/gerar-cupertino-extended.mjs — edite
  lá e rode de novo. Contrato v1.0: só chaves de ThemeBridgeImpl.getAvailableKeys(); nenhuma luz
  desenhada (as do carro ficam por cima); declara o vão da projeção e o abre no #bg.
-->
<style>
  :root { --ink: #f5f5f7; --dim: rgba(235,235,245,0.6); --fraco: rgba(235,235,245,0.3); --laranja: #ff9f0a; --verde: #30d158; --vermelho: #ff453a; }
  html, body {
    margin: 0; padding: 0; width: 1920px; height: 720px; overflow: hidden; background: transparent;
    font-family: -apple-system, 'SF Pro Display', 'Helvetica Neue', Roboto, Arial, sans-serif; color: var(--ink);
    -webkit-font-smoothing: antialiased; user-select: none;
  }
  /* Preto, como o painel de um carro com CarPlay. */
  #bg { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; z-index: 0; background: #000; }
  body.yield-to-car #bg { opacity: 0; }
  #projscrim {
    position: absolute; display: none; z-index: 0; pointer-events: none;
    background: linear-gradient(to bottom, rgba(0,0,0,0) 0%, rgba(0,0,0,0) 62%, rgba(0,0,0,.66) 100%);
  }
  body.projetando #projscrim { display: block; }
  /* Enquanto projeta: um anel fumê segura traços e números por cima do vídeo (a face já é opaca). */
  #projfumo {
    position: absolute; left: 0; top: 0; width: 1920px; height: 720px; display: none; z-index: 0; pointer-events: none;
    background:
      radial-gradient(circle 336px at ${CE}px ${CY}px, rgba(0,0,0,0.9) 0, rgba(0,0,0,0.9) 232px, rgba(0,0,0,0.7) 250px, rgba(0,0,0,0.7) 304px, rgba(0,0,0,0) 336px),
      radial-gradient(circle 336px at ${CD}px ${CY}px, rgba(0,0,0,0.9) 0, rgba(0,0,0,0.9) 232px, rgba(0,0,0,0.7) 250px, rgba(0,0,0,0.7) 304px, rgba(0,0,0,0) 336px);
  }
  body.projetando #projfumo { display: block; }
  body.projetando #centro { display: none; }
  body.projetando .rot { paint-order: stroke; stroke: rgba(0,0,0,0.85); stroke-width: 6px; stroke-linejoin: round; }
  #noite { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; background: #000; opacity: 0; z-index: 3; pointer-events: none; }
  body.mode-headlight #noite { opacity: 0.16; }
  body.night-mode #noite { opacity: 0.36; }

  #app { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; z-index: 1; transition: opacity 0.25s ease; }
  #app.hidden { opacity: 0; pointer-events: none; }
  #arte { position: absolute; left: 0; top: 0; width: 1920px; height: 720px; }
  .rot { fill: rgba(245,245,247,0.82); font-size: 22px; font-weight: 500; text-anchor: middle; letter-spacing: -0.2px; }
  svg { overflow: visible; }

  /* ── Leituras no eixo de cada mostrador ── */
  .cubo { position: absolute; width: 300px; text-align: center; top: 378px; }
  .cubo .n { font-size: 112px; font-weight: 600; line-height: 112px; letter-spacing: -3px; font-variant-numeric: tabular-nums; }
  .cubo .u { font-size: 22px; font-weight: 500; color: var(--dim); margin-top: 4px; }
  #vel { left: ${CE - 150}px; }
  #vel.acima .n { color: var(--laranja); }
  #pot { left: ${CD - 150}px; }
  #pot .n small { font-size: 46px; font-weight: 500; letter-spacing: -1px; margin-left: 3px; color: var(--dim); }
  #pot.desligado .n { color: var(--fraco); }
  #pot.recarga .u { color: var(--verde); }

  /* Marcha dentro do velocímetro: a letra acesa ganha uma cápsula de vidro líquido. */
  #marchas { position: absolute; left: ${CE - 88}px; top: 560px; width: 176px; height: 40px; }
  #marchas .capsula { position: absolute; top: 0; left: 0; width: 44px; height: 40px; border-radius: 20px; box-sizing: border-box;
    background: linear-gradient(180deg, rgba(255,255,255,0.22), rgba(255,255,255,0.1));
    box-shadow: inset 0 1px 0 rgba(255,255,255,0.5), inset 0 0 0 1px rgba(255,255,255,0.14), inset 0 -1px 0 rgba(255,255,255,0.1); }
  #marchas span { position: relative; float: left; width: 44px; height: 40px; line-height: 40px; text-align: center;
    font-size: 24px; font-weight: 600; color: var(--fraco); }
  #marchas span.on { color: var(--ink); }

  /* Autonomias dentro do mostrador da direita. */
  #autonomia { position: absolute; left: ${CD - 150}px; top: 566px; width: 300px; text-align: center; font-size: 21px; font-weight: 600;
    white-space: nowrap; font-variant-numeric: tabular-nums; }
  #autonomia .item { display: inline-block; margin: 0 10px; }
  #autonomia svg { width: 18px; height: 18px; vertical-align: -2px; margin-right: 6px; }
  #autonomia .ev svg { color: var(--verde); }
  #autonomia .comb svg { color: var(--dim); }
  #autonomia small { font-size: 17px; font-weight: 500; color: var(--dim); margin-left: 3px; }

  /* ── Barra de cima ── */
  #hora { position: absolute; left: 860px; top: 26px; width: 200px; text-align: center; font-size: 32px; font-weight: 600; letter-spacing: -0.4px; font-variant-numeric: tabular-nums; }
  #temp { position: absolute; left: 700px; top: 31px; width: 140px; text-align: right; font-size: 24px; font-weight: 500; color: var(--dim); font-variant-numeric: tabular-nums; }
  #modo { position: absolute; left: 1080px; top: 31px; width: 160px; text-align: left; font-size: 24px; font-weight: 500; color: var(--dim); white-space: nowrap; overflow: hidden; }

  /* ── Centro: tocando agora e energia ── */
  #centro { position: absolute; left: 744px; top: 96px; width: 432px; height: 544px; }
  #tocando { position: absolute; left: 0; top: 24px; width: 432px; height: 330px; text-align: center; }
  #tocando .capa { position: absolute; left: 116px; top: 0; width: 200px; height: 200px; border-radius: 18px; overflow: hidden;
    background: linear-gradient(180deg, #2c2c2e, #1c1c1e) center/cover no-repeat; color: rgba(235,235,245,0.32);
    box-shadow: inset 0 0 0 1px rgba(255,255,255,0.1), 0 14px 34px rgba(0,0,0,0.55); }
  #tocando .capa svg { position: absolute; left: 68px; top: 68px; width: 64px; height: 64px; }
  #tocando.com-capa .capa svg { display: none; }
  #tocando .ti { position: absolute; left: 16px; right: 16px; top: 222px; font-size: 27px; font-weight: 600; letter-spacing: -0.3px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  #tocando .ar { position: absolute; left: 16px; right: 16px; top: 258px; font-size: 21px; font-weight: 500; color: var(--dim); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  #tocando .barra { position: absolute; left: 46px; right: 46px; top: 302px; height: 5px; border-radius: 3px; background: rgba(255,255,255,0.16); overflow: hidden; }
  #tocando .barra i { position: absolute; left: 0; top: 0; bottom: 0; width: 100%; transform-origin: 0 0; transform: scaleX(0); background: rgba(255,255,255,0.85); border-radius: 3px; }
  #tocando .tempos { position: absolute; left: 46px; right: 46px; top: 314px; font-size: 14px; font-weight: 600; color: var(--dim); font-variant-numeric: tabular-nums; text-align: left; }
  #tocando .tempos b { float: right; font-weight: 600; }
  #tocando .vazio { display: none; position: absolute; left: 16px; right: 16px; top: 226px; font-size: 24px; font-weight: 500; color: var(--dim); }
  body:not(.com-midia) #tocando .ti, body:not(.com-midia) #tocando .ar, body:not(.com-midia) #tocando .barra,
  body:not(.com-midia) #tocando .tempos { display: none; }
  body:not(.com-midia) #tocando .vazio { display: block; }
  #tocando.sem-tempo .barra, #tocando.sem-tempo .tempos { display: none; }

  /* Cápsulas de bateria e combustível, como o widget de bateria do iOS. */
  #energia { position: absolute; left: 16px; top: 440px; width: 400px; height: 64px; }
  #energia .cap { position: absolute; top: 0; width: 180px; }
  #energia .cap.bat { left: 0; }
  #energia .cap.comb { right: 0; }
  #energia .rt { font-size: 17px; font-weight: 500; color: var(--dim); height: 26px; white-space: nowrap; }
  #energia .rt b { float: right; font-weight: 600; color: var(--ink); font-variant-numeric: tabular-nums; }
  #energia .trilho { position: relative; height: 10px; border-radius: 5px; background: rgba(255,255,255,0.14); overflow: hidden; margin-top: 6px; }
  #energia .trilho i { position: absolute; left: 0; top: 0; bottom: 0; width: 100%; transform-origin: 0 0; transform: scaleX(0); border-radius: 5px; }
  #energia .bat .trilho i { background: var(--verde); }
  #energia .comb .trilho i { background: rgba(245,245,247,0.85); }

  #odo { position: absolute; left: 810px; top: 662px; width: 300px; text-align: center; font-size: 18px; font-weight: 500; color: var(--dim); font-variant-numeric: tabular-nums; }
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
  <div id="pot" class="cubo"><div class="n"><span id="potValor">0</span><small>%</small></div><div class="u" id="potRotulo">Potência</div></div>
  <div id="marchas"><div class="capsula" id="capsula"></div><span id="gP">P</span><span id="gR">R</span><span id="gN">N</span><span id="gD">D</span></div>
  <div id="autonomia"><span class="item ev">${RAIO}<span id="evKm">--</span><small>km</small></span><span class="item comb">${BOMBA}<span id="combKm">---</span><small>km</small></span></div>
  <div id="temp">--°</div>
  <div id="hora">--:--</div>
  <div id="modo">Normal</div>
  <div id="centro">
    <div id="tocando" class="sem-tempo">
      <div class="capa" id="capa">${NOTA}</div>
      <div class="ti" id="titulo"></div>
      <div class="ar" id="artista"></div>
      <div class="vazio">Nada tocando</div>
      <div class="barra"><i id="progresso"></i></div>
      <div class="tempos"><span id="tPos">0:00</span><b id="tResta">-0:00</b></div>
    </div>
    <div id="energia">
      <div class="cap bat"><div class="rt">Bateria <b id="batPct">--%</b></div><div class="trilho"><i id="batBarra"></i></div></div>
      <div class="cap comb"><div class="rt">Combustível <b id="combPct">--%</b></div><div class="trilho"><i id="combBarra"></i></div></div>
    </div>
  </div>
  <div id="odo"><span id="odoValor">---</span> km</div>
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
    app: $('app'), bg: $('bg'), vel: $('vel'), velValor: $('velValor'), pot: $('pot'), potValor: $('potValor'), potRotulo: $('potRotulo'),
    pontE: $('pontE'), pontD: $('pontD'),
    limite: $('limite'), hora: $('hora'), temp: $('temp'), capsula: $('capsula'),
    g: { P: $('gP'), R: $('gR'), N: $('gN'), D: $('gD') },
    tocando: $('tocando'), capa: $('capa'), titulo: $('titulo'), artista: $('artista'), progresso: $('progresso'),
    tPos: $('tPos'), tResta: $('tResta'), evKm: $('evKm'), combKm: $('combKm'),
    batPct: $('batPct'), batBarra: $('batBarra'), combPct: $('combPct'), combBarra: $('combBarra'),
    modo: $('modo'), odoValor: $('odoValor'),
  };

  function numero(v) { const n = parseFloat(v); return Number.isFinite(n) ? n : null; }
  function ligado(v) {
    return v === true || ['1', 'true', 'on', 'yes', 'ligado'].indexOf(String(v == null ? '' : v).trim().toLowerCase()) >= 0;
  }
  function milhar(n) { return String(n).replace(/\\B(?=(\\d{3})+(?!\\d))/g, '.'); }
  const escrito = {};
  function mudou(chave, valor) { if (escrito[chave] === valor) return false; escrito[chave] = valor; return true; }

  // ----- Suavização (a mesma do SENSUS/MBUX/ID8/TEA): filtro criticamente amortecido no ângulo. -----
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
    tempExt: null, fahrenheit: false, modo: '0', modoEnergia: '0',
    midia: { titulo: '', artista: '', capa: '', posicao: null, duracao: null } };

  // Acende as primeiras k fatias da varredura: só as que mudaram de lado são tocadas.
  function montarArco(id) { return { itens: Array.prototype.slice.call($(id).children), acesas: 0 }; }
  const ARCOS = { vel: montarArco('arcoVel'), pot: montarArco('arcoPot'), regen: montarArco('arcoRegen') };
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
    // O zero fica a 180°: potência no sentido horário, recarga no anti-horário.
    acender(ARCOS.pot, a <= 180 ? clamp((180 - a) / (180 - GEO.fim), 0, 1) : 0);
    acender(ARCOS.regen, a > 180 ? clamp((a - 180) / (GEO.ini - 180), 0, 1) : 0);
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
    renderFallbackTimer = setTimeout(flushRender, 120);
  }

  function mirarPotencia() {
    const desligado = est.pronto === false;
    if (mudou('desl', desligado)) els.pot.classList.toggle('desligado', desligado);
    const p = desligado ? 0 : est.potencia;
    const r = Math.round(p);
    // Sem sinal na leitura: o rótulo diz se é potência ou recarga.
    const txt = String(Math.abs(r));
    if (mudou('potTxt', txt)) els.potValor.textContent = txt;
    const recarga = r < 0;
    if (mudou('recarga', recarga)) {
      els.pot.classList.toggle('recarga', recarga);
      els.potRotulo.textContent = recarga ? 'Recarga' : 'Potência';
    }
    mirar(C.pot, anguloDe(ESC_POT, p));
    scheduleRender();
  }
  function atualizarLimite() {
    const ok = est.limiteOk && est.limite !== null && est.limite > 0;
    els.limite.setAttribute('visibility', ok ? 'visible' : 'hidden');
    if (ok) els.limite.setAttribute('transform', 'rotate(' + (-anguloDe(ESC_VEL, clamp(est.limite, 0, 240))).toFixed(2) + ' ' + GEO.ce + ' ' + GEO.cy + ')');
    const acima = ok && est.velocidade > est.limite + 3;
    if (mudou('acima', acima)) els.vel.classList.toggle('acima', acima);
  }
  function desenharTemp() {
    if (est.tempExt === null) return;
    const v = Math.round(est.fahrenheit ? est.tempExt * 9 / 5 + 32 : est.tempExt);
    els.temp.textContent = (v < 0 ? '−' : '') + Math.abs(v) + '°';
  }
  function capsula(pctEl, barraEl, pct) {
    const p = clamp(pct, 0, 100);
    pctEl.textContent = Math.round(p) + '%';
    barraEl.style.transform = 'scaleX(' + (p / 100).toFixed(3) + ')';
  }

  const ORDEM = ['P', 'R', 'N', 'D'];
  const GEAR_LABELS = { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' };
  function desenharMarcha(letra) {
    if (!mudou('marcha', letra)) return;
    ORDEM.forEach((k) => els.g[k].classList.toggle('on', k === letra));
    els.capsula.style.left = (ORDEM.indexOf(letra) * 44) + 'px';
  }
  const DRIVE_MODE_LABELS = { '0': 'Normal', '1': 'Sport', '2': 'Eco', '3': 'Neve', '4': 'Areia', '5': 'Lama', '11': 'AWD' };
  const EV_MODE_LABELS = { '0': 'HEV', '1': 'EV+', '3': 'EV' };
  function atualizarModos() {
    const energia = EV_MODE_LABELS[est.modoEnergia] || 'HEV';
    els.modo.textContent = (DRIVE_MODE_LABELS[est.modo] || 'Normal') + ' · ' + energia;
  }
  function mmss(seg) { const s = Math.max(0, Math.round(seg)); return Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0'); }
  function atualizarMidia() {
    const m = est.midia;
    const tem = m.titulo.trim().length > 0;
    document.body.classList.toggle('com-midia', tem);
    els.capa.style.backgroundImage = tem && m.capa ? 'url("' + m.capa.replace(/"/g, '%22') + '")' : '';
    els.tocando.classList.toggle('com-capa', tem && !!m.capa);
    if (!tem) return;
    els.titulo.textContent = m.titulo;
    els.artista.textContent = m.artista;
    atualizarTempo();
  }
  // Posição e duração: a barra só aparece quando as duas vierem. Acima de 10 h é milissegundo.
  function atualizarTempo() {
    const m = est.midia;
    let dur = m.duracao, pos = m.posicao;
    const tem = dur !== null && dur > 0 && pos !== null && pos >= 0;
    els.tocando.classList.toggle('sem-tempo', !tem);
    if (!tem) return;
    if (dur > 36000) { dur /= 1000; pos /= 1000; }
    pos = Math.min(pos, dur);
    els.tPos.textContent = mmss(pos);
    els.tResta.textContent = '-' + mmss(dur - pos);
    els.progresso.style.transform = 'scaleX(' + (pos / dur).toFixed(4) + ')';
  }

  function handleTelemetry(key, raw) {
    switch (key) {
      case 'car.basic.vehicle_speed': {
        const v = numero(raw);
        est.velocidade = v === null ? 0 : Math.max(0, v);
        const txt = String(Math.round(est.velocidade));
        if (mudou('vel', txt)) els.velValor.textContent = txt;
        mirar(C.vel, anguloDe(ESC_VEL, clamp(est.velocidade, 0, 240)));
        atualizarLimite();
        scheduleRender();
        break;
      }
      case 'car.ev_info.energy_output_percentage':
        est.potencia = clamp(numero(raw) || 0, -100, 100);
        mirarPotencia();
        break;
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
      case 'car.ev_info.cur_battery_power_percentage': { const p = numero(raw); if (p !== null) capsula(els.batPct, els.batBarra, p); break; }
      case 'car.basic.remain_fuel_percentage': { const p = numero(raw); if (p !== null) capsula(els.combPct, els.combBarra, p); break; }
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
        desenharMarcha(/^[PRND]$/.test(s) ? s : (GEAR_LABELS[String(parseInt(s, 10))] || 'N'));
        break;
      }
      case 'car.drive_setting.drive_mode': {
        const m = String(parseInt(raw, 10));
        est.modo = DRIVE_MODE_LABELS[m] ? m : '0';
        atualizarModos();
        break;
      }
      case 'car.ev_setting.power_model_config': est.modoEnergia = String(raw).trim(); atualizarModos(); break;
      case 'car.basic.outside_temp': { const t = numero(raw); if (t !== null) { est.tempExt = t; desenharTemp(); } break; }
      case 'car.configure.default_temp_unit': est.fahrenheit = String(raw).trim() === '1'; desenharTemp(); break;
      case 'car.basic.head_light_status': document.body.classList.toggle('mode-headlight', ligado(raw)); break;
      case 'car.map.tsr.nav_speed_limit': est.limite = numero(raw); atualizarLimite(); break;
      case 'car.map.tsr.nav_speed_limit_sign_status': est.limiteOk = ligado(raw); atualizarLimite(); break;
      case 'app.media.title': est.midia.titulo = raw ? String(raw) : ''; atualizarMidia(); break;
      case 'app.media.artist': est.midia.artista = raw ? String(raw) : ''; atualizarMidia(); break;
      case 'app.media.album_art': est.midia.capa = raw ? String(raw).trim() : ''; atualizarMidia(); break;
      case 'app.media.position': est.midia.posicao = numero(raw); atualizarTempo(); break;
      case 'app.media.duration': est.midia.duracao = numero(raw); atualizarTempo(); break;
      default: break;
    }
  }

  const projectionState = { carPlayInDash: false, projectionMirrorInDash: false, projectionPreparingD3: false };
  function applyYieldToCar() {
    const yielding = projectionState.carPlayInDash || projectionState.projectionMirrorInDash || projectionState.projectionPreparingD3;
    els.app.classList.toggle('hidden', yielding);
    document.body.classList.toggle('yield-to-car', yielding);
  }

  // ----- Vão da projeção da central: de um centro de mostrador ao outro (como no NTG 5.5). -----
  const PROJ_BOX = { x: 426, y: 96, w: 1068, h: 544 };
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
      if (!recortaOFundo) console.warn('[CUPERTINO EXTENDED] sem suporte a mascara: a projecao vai por cima do tema');
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
    'app.media.position',
    'app.media.duration',
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

  // ----- Simulação local (sem window.Android): ?vel=, ?pot=, ?off=1, ?proj=1, ?midia=1, ?limite=, ?mode=Noite. -----
  function installMockBridge() {
    console.warn('[CUPERTINO EXTENDED] window.Android ausente — usando simulação local para preview.');
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
      'car.ev_info.cur_battery_power_percentage': '62',
      'car.basic.remain_fuel_percentage': '48',
      'car.ev_info.electric_mode_remain_odometer': '39',
      'car.ev_info.fuel_mode_remain_odometer': '219',
      'car.basic.total_odometer': '11500',
      'car.basic.gear_status': '2',
      'car.drive_setting.drive_mode': '0',
      'car.ev_setting.power_model_config': '0',
      'car.basic.outside_temp': '24',
      'car.basic.head_light_status': '0',
    };
    Object.keys(seed).forEach((k) => handleTelemetry(k, seed[k]));
    if (q.get('limite')) {
      handleTelemetry('car.map.tsr.nav_speed_limit', q.get('limite'));
      handleTelemetry('car.map.tsr.nav_speed_limit_sign_status', '1');
    }
    if (q.get('midia') === '1') {
      handleTelemetry('app.media.title', 'Faixa de demonstração');
      handleTelemetry('app.media.artist', 'Artista');
      handleTelemetry('app.media.duration', '232000');
      handleTelemetry('app.media.position', '71000');
    }
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
      if (window.Android && typeof window.Android.setAppDefaultDimensions === 'function') window.Android.setAppDefaultDimensions(744, 110, 432, 540);
      declararVaoDaProjecao();
      lerPreferencias();
    } catch (e) { console.error('[CUPERTINO EXTENDED] bridge init failed:', e); }
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
  desenharMarcha('P');
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
    console.error('FALHOU: o app.html do Cupertino Extended não bate com scripts/gerar-cupertino-extended.mjs')
    process.exit(1)
  }
  console.log('ok: app.html do Cupertino Extended bate com o gerador')
} else {
  fs.mkdirSync(path.dirname(arquivo), { recursive: true })
  fs.writeFileSync(arquivo, html)
  console.log('gravado', path.relative(raiz, arquivo), (html.length / 1024).toFixed(1) + ' KB')
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
