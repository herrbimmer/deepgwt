// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Geometria do tema Bávaro ID8 (assets/PreinstalledThemes/bavaroid8).
//
// Os dois chevrons (face, aro, parede lateral, traços, rótulos das escalas, zona de recarga), a
// linha do horizonte, a grade triangular do centro e as caixas das camadas que se movem saem daqui
// e são escritos no app.html entre os marcadores GERADO. O resto do tema (texto, ícones, lógica)
// é escrito à mão no próprio app.html.
//
//   node scripts/gerar-bavaro-id8.mjs              regrava os blocos GERADO
//   node scripts/gerar-bavaro-id8.mjs --verificar  falha se o app.html não bater com o gerador
//   node scripts/gerar-bavaro-id8.mjs --relatorio  imprime as posições principais
//
// As medidas vêm da foto de referência (painel no estilo iDrive 8, 1920x1080): a faixa útil dela
// (y 240..880) cabe inteira no painel 1920x720 deslocada 196 px para cima, sem mudar de escala.
// Só o chevron esquerdo é medido; o direito é o mesmo desenho espelhado em x = 960.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const arquivo = path.join(raiz, 'app', 'src', 'main', 'assets', 'PreinstalledThemes', 'bavaroid8', 'app.html')

const r1 = (n) => Math.round(n * 10) / 10
const pt = (p) => r1(p[0]) + ',' + r1(p[1])
const espelho = (p) => [1920 - p[0], p[1]]

// ── Medidas (chevron esquerdo) ───────────────────────────────────────────────────────────────
// Borda externa do aro prateado: varredura dos pixels claros da foto, linha a linha. Os dois braços
// não têm a mesma inclinação (a peça está em perspectiva): o de cima sobe 0,944 px para a direita
// a cada px, o de baixo desce 0,646. Ponta aguda em (350, 273); o aro arredonda por cima dela.
const TOPO = 72
const BASE = 604
const PONTA = [350, 273]
const INCL_ALTO = 0.944
const INCL_BAIXO = 0.646
// Borda interna da face (onde começa a parede lateral). A face é mais larga na ponta (140 px) que
// no topo (100) e na base (113): é a perspectiva da foto, e é o que dá volume à peça.
const DENTRO = { topo: [642, TOPO], ponta: [490, 274], base: [663, BASE] }
const ARO = 12          // espessura do aro, perpendicular à borda
const FOLGA = 15        // o enchimento começa um pouco depois do aro (fresta escura da foto)
const PAREDE = 40       // largura horizontal da parede lateral (o lado do prisma)
const PAREDE_TOPO = 108
const PAREDE_BASE = 598
const LINHA_TOPO_ATE = 682   // a linha fina do topo passa por cima da parede
const REFLEXO = { y: 392, r: 170 }   // o reflexo claro na parede, abaixo da ponta

// Escalas: valor -> y. Os rótulos da foto não são equidistantes (a parte de cima da escala de
// velocidade é comprimida), então a escala é uma tabela, interpolada por partes.
const ESCALA_E = [[0, 594], [30, 519], [60, 440], [90, 359], [120, 276], [150, 204], [180, 136], [220, 74]]
const MENORES_E = [15, 45, 75, 105, 135, 165, 200]
const ESCALA_D = [[0, 474], [25, 373], [50, 274], [75, 171], [100, 74]]
const MENORES_D = [12.5, 37.5, 62.5, 87.5]
// A linha que separa a potência (em cima) da recarga (embaixo) no chevron direito.
const ZERO_D = 478
// Caixas do padrão de luz do enchimento (o ladrilho cobre a face inteira de cada lado).
const CAIXA_E = { x: 326, y: 60, w: 390, h: 552 }
const CAIXA_D = { x: 1920 - CAIXA_E.x - CAIXA_E.w, y: CAIXA_E.y, w: CAIXA_E.w, h: CAIXA_E.h }
// Grade triangular do centro: linhas verticais a cada GRADE px, as outras duas famílias a ±30°.
const GRADE = 56
// Setas da recarga (três "v" empilhados ao longo do braço de baixo do chevron direito).
const SETAS_Y = [520, 546, 572]
// ── Tamanho no painel do carro (Alpha 9.2) ─────────────────────────────────────────────────
// Tudo acima é a medida da foto, e o chevron ia do y 72 ao 604. No carro o painel de fábrica
// desenha por cima do tema, no alto de cada lado, as luzes verdes do farol (x 394-493, y 53-98, e
// as vagas espelhadas) e o cinto (x 1261-1297, y 18-66): o "220" ficava embaixo do farol e a
// linha do topo do chevron direito encostava no cinto (docs/operations/mapa-de-luzes-do-painel.md).
// Os dois chevrons inteiros (aro, face, parede, traços, rótulos, títulos, enchimento, zona de
// recarga) encolhem ESCALA em volta do meio da própria base: a base continua no horizonte e o topo
// desce para y ~106, abaixo das luzes. O horizonte, o chão e o centro não mudam.
const ESCALA = 0.936
const ANCORA_E = [610, 604]
const ANCORA_D = espelho(ANCORA_E)
const r2 = (n) => Math.round(n * 100) / 100
const lado = (ancora, html) =>
  '<g transform="matrix(' + ESCALA + ' 0 0 ' + ESCALA + ' ' + r2(ancora[0] * (1 - ESCALA)) + ' ' + r2(ancora[1] * (1 - ESCALA)) + ')">' + html + '</g>'
const naTela = (ancora, p) => [ancora[0] + (p[0] - ancora[0]) * ESCALA, ancora[1] + (p[1] - ancora[1]) * ESCALA]

// Altura de cada faixa do enchimento (px). A linha branca da frente (2 px) e o brilho logo abaixo
// dela cobrem o degrau.
const FAIXA = 3
// Luz do enchimento: medida na foto, ela é presa à BASE do braço (vinho escuro embaixo, branco a
// ~208 px dela), não à frente do enchimento. Distância da base -> cor (variáveis do tom).
const PERFIL = [[208, '#fff'], [184, 'var(--c4)'], [144, 'var(--c3)'], [96, 'var(--c2)'], [48, 'var(--c1)'], [0, 'var(--c0)']]

// ── Retas e deslocamentos ────────────────────────────────────────────────────────────────────
// Reta como x = x0 + k (y - y0).
const reta = (p, k) => ({ x0: p[0], y0: p[1], k })
const retaPor = (a, b) => reta(a, (b[0] - a[0]) / (b[1] - a[1]))
const xEm = (r, y) => r.x0 + r.k * (y - r.y0)
// Desloca a reta d px na perpendicular, para +x (d < 0 vai para -x).
const desloca = (r, d) => ({ x0: r.x0 + d * Math.sqrt(1 + r.k * r.k), y0: r.y0, k: r.k })
const cruza = (a, b) => {
  const y = (b.x0 - a.x0 + a.k * a.y0 - b.k * b.y0) / (a.k - b.k)
  return [xEm(a, y), y]
}

const fora = { alto: reta(PONTA, -INCL_ALTO), baixo: reta(PONTA, INCL_BAIXO) }
const dentro = { alto: retaPor(DENTRO.topo, DENTRO.ponta), baixo: retaPor(DENTRO.ponta, DENTRO.base) }

// Polilinha (topo, ponta, base) de um par de retas deslocado d, cortada em y0 e y1.
function poli(par, d, y0 = TOPO, y1 = BASE) {
  const a = desloca(par.alto, d)
  const b = desloca(par.baixo, d)
  const p = cruza(a, b)
  return { topo: [xEm(a, y0), y0], ponta: p, base: [xEm(b, y1), y1], alto: a, baixo: b }
}
// x de uma polilinha numa altura y (escolhe o braço pela ponta).
const xNa = (l, y) => (y <= l.ponta[1] ? xEm(l.alto, y) : xEm(l.baixo, y))

const aro0 = poli(fora, 0)
const aroMeio = poli(fora, ARO / 2)
const aroDentro = poli(fora, ARO)
const enchFora = poli(fora, FOLGA)
const face = poli(dentro, 0)
const enchDentro = poli(dentro, -3)

// y de um valor numa escala por partes.
function yDaEscala(tab, v) {
  if (v <= tab[0][0]) return tab[0][1]
  for (let i = 1; i < tab.length; i++) {
    if (v <= tab[i][0]) {
      const [v0, y0] = tab[i - 1]
      const [v1, y1] = tab[i]
      return y0 + (y1 - y0) * (v - v0) / (v1 - v0)
    }
  }
  return tab[tab.length - 1][1]
}

// ── Polígonos ────────────────────────────────────────────────────────────────────────────────
// Face inteira (a textura escura por baixo), do meio do aro até a borda interna.
const polFace = [aroMeio.topo, face.topo, face.ponta, face.base, aroMeio.base, aroMeio.ponta]
// Onde o enchimento pode aparecer.
function polEnch(y1) {
  return [enchFora.topo, enchDentro.topo, enchDentro.ponta,
    [xEm(enchDentro.baixo, y1), y1], [xEm(enchFora.baixo, y1), y1], enchFora.ponta]
}
const polEnchE = polEnch(BASE)
const polEnchD = polEnch(ZERO_D - 1.5)
// Zona de recarga: o braço de baixo, da linha do zero até a base.
const yZona = ZERO_D + 1.5
const polZona = [[xEm(enchFora.baixo, yZona), yZona], [xEm(enchDentro.baixo, yZona), yZona],
  [xEm(enchDentro.baixo, BASE), BASE], [xEm(enchFora.baixo, BASE), BASE]]
// Parede lateral: da borda interna da face (com fresta de 2 px) até PAREDE px para dentro.
function polParede() {
  const a = desloca(dentro.alto, 2)
  const b = desloca(dentro.baixo, 2)
  const pa = cruza(a, b)
  const sh = (p) => [p[0] + PAREDE, p[1]]
  const t = [xEm(a, PAREDE_TOPO), PAREDE_TOPO]
  const bb = [xEm(b, PAREDE_BASE), PAREDE_BASE]
  return [t, sh(t), sh(pa), sh(bb), bb, pa]
}
const polParedeE = polParede()

const polTexto = (pts) => 'M ' + pts.map(pt).join(' L ') + ' Z'

// ── Blocos ───────────────────────────────────────────────────────────────────────────────────
function blocoFundo() {
  const L = []
  const add = (s) => L.push('      ' + s)
  const g = GRADE
  const h = r1(g / Math.cos(Math.PI / 6))   // lado do triângulo = passo vertical entre pontos
  add('<defs>')
  add('  <pattern id="pGrade" patternUnits="userSpaceOnUse" width="' + 2 * g + '" height="' + h + '" x="' + (960 - g) + '" y="12">')
  add('    <path d="M0 0 V' + h + ' M' + g + ' 0 V' + h + ' M0 0 L' + 2 * g + ' ' + h + ' M0 ' + h + ' L' + 2 * g + ' 0" class="grade"/>')
  add('  </pattern>')
  add('  <radialGradient id="gGradeMascara" cx="960" cy="332" r="470" gradientUnits="userSpaceOnUse" gradientTransform="translate(960 332) scale(1 0.64) translate(-960 -332)">')
  add('    <stop offset="0" stop-color="#fff"/><stop offset="0.62" stop-color="#fff" stop-opacity="0.7"/><stop offset="1" stop-color="#fff" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <mask id="mGrade" maskUnits="userSpaceOnUse" x="0" y="0" width="1920" height="720"><rect width="1920" height="720" fill="url(#gGradeMascara)"/></mask>')
  add('  <radialGradient id="gNevoa" cx="0.5" cy="0.5" r="0.5">')
  add('    <stop offset="0" stop-color="#3f93b4" stop-opacity="0.5"/><stop offset="0.55" stop-color="#23627e" stop-opacity="0.24"/><stop offset="1" stop-color="#0b2430" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <radialGradient id="gNevoaCentro" cx="0.5" cy="0.5" r="0.5">')
  add('    <stop offset="0" stop-color="#6d9fb6" stop-opacity="0.4"/><stop offset="1" stop-color="#10303e" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <linearGradient id="gChao" x1="0" y1="0" x2="0" y2="1">')
  add('    <stop offset="0" stop-color="#050a0e" stop-opacity="0"/><stop offset="0.5" stop-color="#03070a" stop-opacity="0.75"/><stop offset="1" stop-color="#010203"/>')
  add('  </linearGradient>')
  add('</defs>')
  add('<rect width="1920" height="720" fill="#000"/>')
  // Névoa azulada que sobe do horizonte, forte embaixo dos chevrons (como na foto).
  add('<ellipse cx="420" cy="566" rx="470" ry="118" fill="url(#gNevoa)"/>')
  add('<ellipse cx="1500" cy="566" rx="470" ry="118" fill="url(#gNevoa)"/>')
  add('<ellipse cx="960" cy="592" rx="560" ry="64" fill="url(#gNevoaCentro)"/>')
  // Abaixo do horizonte o chão escurece de novo.
  add('<rect x="0" y="592" width="1920" height="128" fill="url(#gChao)"/>')
  add('<rect x="480" y="40" width="960" height="600" fill="url(#pGrade)" mask="url(#mGrade)"/>')
  return L.join('\n')
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

function blocoArte() {
  const L = []
  const add = (s) => L.push('    ' + s)
  const E = []   // chevron esquerdo (o direito é o mesmo grupo espelhado)
  const e = (s) => E.push('      ' + s)

  add('<defs>')
  add('  <linearGradient id="gAro" gradientUnits="userSpaceOnUse" x1="0" y1="' + TOPO + '" x2="0" y2="' + BASE + '">')
  add('    <stop offset="0" stop-color="#9aa6b0"/><stop offset="0.36" stop-color="#eef2f5"/><stop offset="0.52" stop-color="#d9e0e5"/><stop offset="1" stop-color="#aab4bc"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gFace" gradientUnits="userSpaceOnUse" x1="0" y1="' + TOPO + '" x2="0" y2="' + BASE + '">')
  add('    <stop offset="0" stop-color="#0c131a"/><stop offset="0.28" stop-color="#1a2833"/><stop offset="0.4" stop-color="#2b3e4c"/><stop offset="0.6" stop-color="#18232d"/><stop offset="1" stop-color="#0e151c"/>')
  add('  </linearGradient>')
  add('  <radialGradient id="gLuzPonta" gradientUnits="userSpaceOnUse" cx="' + r1(face.ponta[0] - 40) + '" cy="' + r1(face.ponta[1] + 20) + '" r="120">')
  add('    <stop offset="0" stop-color="#dfe9f1" stop-opacity="0.22"/><stop offset="1" stop-color="#dfe9f1" stop-opacity="0"/>')
  add('  </radialGradient>')
  add('  <pattern id="pRiscoA" patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(45)"><rect width="1.4" height="6" fill="#a9c2d6" fill-opacity="0.16"/></pattern>')
  add('  <pattern id="pRiscoB" patternUnits="userSpaceOnUse" width="6" height="6" patternTransform="rotate(-45)"><rect width="1.4" height="6" fill="#a9c2d6" fill-opacity="0.16"/></pattern>')
  // A parede lateral: escura em cima, reflexo claro na altura da ponta e o tom do enchimento
  // refletido no braço de baixo (a cor vem do tom escolhido: classes s-parede-*).
  add('  <linearGradient id="gParede" gradientUnits="userSpaceOnUse" x1="0" y1="' + PAREDE_TOPO + '" x2="0" y2="' + PAREDE_BASE + '">')
  add('    <stop offset="0" stop-color="#07090c"/><stop offset="0.3" stop-color="#101317"/><stop offset="0.4" stop-color="#16171b"/><stop offset="0.62" class="s-parede-meio"/><stop offset="0.85" class="s-parede-fundo"/><stop offset="1" stop-color="#0c0908"/>')
  add('  </linearGradient>')
  {
    // Reflexo: uma faixa de luz na parede do braço de baixo, logo abaixo da ponta, inclinada
    // como o braço.
    const cy = REFLEXO.y
    const cx = r1(xEm(dentro.baixo, cy) + 2 + PAREDE * 0.45)
    const ang = r1(Math.atan(dentro.baixo.k) * 180 / Math.PI)
    add('  <radialGradient id="gReflexo" gradientUnits="userSpaceOnUse" cx="' + cx + '" cy="' + cy + '" r="' + REFLEXO.r + '" gradientTransform="translate(' + cx + ' ' + cy + ') rotate(' + -ang + ') scale(0.2 1) translate(' + -cx + ' ' + -cy + ')">')
    add('    <stop offset="0" class="s-parede-luz" stop-opacity="0.95"/><stop offset="0.45" class="s-parede-luz" stop-opacity="0.35"/><stop offset="1" class="s-parede-luz" stop-opacity="0"/>')
    add('  </radialGradient>')
  }
  add('  <linearGradient id="gFio" gradientUnits="userSpaceOnUse" x1="0" y1="' + PAREDE_TOPO + '" x2="0" y2="' + PAREDE_BASE + '">')
  add('    <stop offset="0" stop-color="#8e99a3" stop-opacity="0.2"/><stop offset="0.34" stop-color="#ffffff"/><stop offset="0.6" stop-color="#c9ced3" stop-opacity="0.7"/><stop offset="1" stop-color="#8e99a3" stop-opacity="0.25"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gHorizonteE" gradientUnits="userSpaceOnUse" x1="80" y1="0" x2="480" y2="0">')
  add('    <stop offset="0" stop-color="#9fb7c8" stop-opacity="0"/><stop offset="0.35" stop-color="#b6c8d6" stop-opacity="0.8"/><stop offset="1" stop-color="#d7e1e8"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gHorizonteD" gradientUnits="userSpaceOnUse" x1="1840" y1="0" x2="1440" y2="0">')
  add('    <stop offset="0" stop-color="#9fb7c8" stop-opacity="0"/><stop offset="0.35" stop-color="#b6c8d6" stop-opacity="0.8"/><stop offset="1" stop-color="#d7e1e8"/>')
  add('  </linearGradient>')
  add('  <linearGradient id="gZona" gradientUnits="userSpaceOnUse" x1="0" y1="' + ZERO_D + '" x2="0" y2="' + BASE + '">')
  add('    <stop offset="0" stop-color="#16263b"/><stop offset="1" stop-color="#0b1422"/>')
  add('  </linearGradient>')
  add('  <clipPath id="cFaceE"><path d="' + polTexto(polFace) + '"/></clipPath>')
  add('  <clipPath id="cFaixa"><rect x="0" y="' + TOPO + '" width="1920" height="' + (BASE - TOPO) + '"/></clipPath>')
  add('  <filter id="fSuave" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="5"/></filter>')
  add('</defs>')

  // Chevron (desenhado à esquerda; o grupo é reusado espelhado).
  e('<path d="' + polTexto(polParedeE) + '" fill="url(#gParede)"/>')
  e('<path d="' + polTexto(polParedeE) + '" fill="url(#gReflexo)"/>')
  {
    // Sombra na borda de dentro da parede: ela some no preto em vez de terminar num corte.
    const a = desloca(dentro.alto, 2)
    const b = desloca(dentro.baixo, 2)
    const pa = cruza(a, b)
    const sh = (p, dx) => [p[0] + dx, p[1]]
    const t = [xEm(a, PAREDE_TOPO), PAREDE_TOPO]
    const bb = [xEm(b, PAREDE_BASE), PAREDE_BASE]
    e('<path d="M ' + [sh(t, PAREDE - 6), sh(pa, PAREDE - 6), sh(bb, PAREDE - 6)].map(pt).join(' L ') + '" class="parede-sombra"/>')
    e('<path d="M ' + [sh(t, 1.5), sh(pa, 1.5), sh(bb, 1.5)].map(pt).join(' L ') + '" class="fio"/>')
  }
  e('<path d="' + polTexto(polFace) + '" fill="url(#gFace)"/>')
  e('<g clip-path="url(#cFaceE)">')
  e('  <rect x="300" y="60" width="420" height="' + r1(face.ponta[1] - 60) + '" fill="url(#pRiscoA)"/>')
  e('  <rect x="300" y="' + r1(face.ponta[1]) + '" width="420" height="' + r1(BASE - face.ponta[1] + 8) + '" fill="url(#pRiscoB)"/>')
  e('  <rect x="300" y="60" width="420" height="560" fill="url(#gLuzPonta)"/>')
  e('</g>')
  e('<path d="M ' + [aroMeio.topo, aroMeio.ponta, aroMeio.base].map(pt).join(' L ') + '" class="aro" clip-path="url(#cFaixa)"/>')
  e('<path d="M ' + pt([aro0.topo[0], TOPO]) + ' H ' + LINHA_TOPO_ATE + '" class="linha-topo"/>')
  const arte = E.join('\n')

  // Cada lado num grupo com a ESCALA; dentro dele, as medidas da foto.
  const esq = [], dir = []
  const ae = (s) => esq.push('      ' + s)
  const ad = (s) => dir.push('      ' + s)
  ae('<g id="chevronE">')
  esq.push(arte)
  ae('</g>')
  ad('<g transform="matrix(-1 0 0 1 1920 0)">')
  dir.push(arte)
  ad('</g>')

  // Zona de recarga (braço de baixo do chevron direito).
  const zona = polZona.map(espelho)
  ad('<path d="' + polTexto(zona) + '" fill="url(#gZona)"/>')
  ad('<path d="M ' + pt(zona[1]) + ' L ' + pt(zona[2]) + '" class="zona-fio"/>')
  ad('<path d="M ' + pt(espelho([xEm(enchFora.baixo, ZERO_D), ZERO_D])) + ' L ' + pt(espelho([xEm(enchDentro.baixo, ZERO_D), ZERO_D])) + '" class="zona-corte"/>')
  SETAS_Y.forEach((y, i) => {
    const cx = r1((xEm(enchFora.baixo, y) + xEm(enchDentro.baixo, y)) / 2)
    const x = 1920 - cx
    ad('<path id="seta' + i + '" class="seta" d="M ' + r1(x - 11) + ' ' + (y - 5) + ' L ' + x + ' ' + (y + 5) + ' L ' + r1(x + 11) + ' ' + (y - 5) + '"/>')
  })

  // Traços claros (aparecem onde o enchimento não chegou).
  ae('<g class="tracos-claros">')
  esq.push(tracos('E'))
  ae('</g>')
  ad('<g class="tracos-claros">')
  dir.push(tracos('D'))
  ad('</g>')

  // Rótulos das escalas, fora do aro, e os títulos (as unidades), espelhados em x = 960.
  ae('<g class="rotulos">')
  ESCALA_E.forEach(([v, y], i) => {
    ae('  <text id="rE' + i + '" x="' + r1(xNa(aro0, y) - 18) + '" y="' + r1(y + 13) + '" text-anchor="end">' + v + '</text>')
  })
  ae('  <text class="titulo-escala" x="580" y="' + (TOPO - 11) + '" text-anchor="middle">km/h</text>')
  ae('</g>')
  ad('<g class="rotulos">')
  ESCALA_D.forEach(([v, y], i) => {
    ad('  <text id="rD' + i + '" x="' + r1(1920 - (xNa(aro0, y) - 18)) + '" y="' + r1(y + 13) + '">' + v + '</text>')
  })
  ad('  <text class="titulo-escala" x="1340" y="' + (TOPO - 11) + '" text-anchor="middle">%</text>')
  ad('</g>')

  add(lado(ANCORA_E, '\n' + esq.join('\n') + '\n    '))
  add(lado(ANCORA_D, '\n' + dir.join('\n') + '\n    '))

  // Horizonte: linha alta, degrau e linha baixa, dos dois lados; a linha fina da base liga cada
  // chevron ao traço no tom do painel perto do centro.
  const horiz = (s) => s === 'E'
    ? 'M 84 576 H 340 L 382 604 H 478'
    : 'M 1836 576 H 1580 L 1538 604 H 1442'
  add('<path d="' + horiz('E') + '" class="horizonte" stroke="url(#gHorizonteE)"/>')
  add('<path d="' + horiz('D') + '" class="horizonte" stroke="url(#gHorizonteD)"/>')
  const baseNaTela = naTela(ANCORA_E, aro0.base)[0]
  add('<path d="M ' + r1(baseNaTela + 4) + ' ' + BASE + ' H 725 M ' + r1(1920 - baseNaTela - 4) + ' ' + BASE + ' H 1195" class="base-fina"/>')
  add('<path d="M 725 604 H 838 L 847 598 M 1195 604 H 1082 L 1073 598" class="acento-brilho" filter="url(#fSuave)"/>')
  add('<path d="M 725 604 H 838 L 847 598 M 1195 604 H 1082 L 1073 598" class="acento"/>')

  return L.join('\n')
}

// Traços de uma escala. Claros ficam no desenho estático; escuros ficam dentro do enchimento.
function tracos(lado) {
  const L = []
  const tab = lado === 'E' ? ESCALA_E : ESCALA_D
  const menores = lado === 'E' ? MENORES_E : MENORES_D
  const lista = tab.map(([v]) => [v, 44, 'maior']).concat(menores.map((v) => [v, 22, 'menor']))
  lista.sort((a, b) => a[0] - b[0])
  for (const [v, comp, cls] of lista) {
    const y = r1(yDaEscala(tab, v))
    if (y < TOPO + 5) continue
    if (lado === 'D' && y > ZERO_D - 3) continue
    const x0 = xNa(aroDentro, y) + 2
    const xa = lado === 'E' ? r1(x0) : r1(1920 - x0)
    const xb = lado === 'E' ? r1(x0 + comp) : r1(1920 - x0 - comp)
    L.push('      <line x1="' + xa + '" y1="' + y + '" x2="' + xb + '" y2="' + y + '" class="' + cls + '"/>')
  }
  return L.join('\n')
}

// ── O enchimento ────────────────────────────────────────────────────────────────────────────
// Tudo pintado, dentro do SVG, sem camada composta. No WebView do app a página abre num WebView
// ainda sem tamanho e só depois é encaixada no painel; camada composta (will-change) criada ali
// saía rasterizada na escala errada e desenhada menor e fora do lugar, e clip-path num contêiner
// com camada composta virava o retângulo em volta do polígono (os dois vistos no emulador, 8.6).
// Os temas que já rodam no carro também movem só grupos DENTRO do SVG.
//
// Para não repintar a face inteira a cada quadro, o enchimento é uma pilha de faixas horizontais
// PARADAS de FAIXA px, recortadas pelo chevron: quando o valor anda, só as faixas da borda trocam
// de visibilidade (a área repintada é a delas), e a linha branca da frente, pequena, anda por
// transform. A luz é um padrão preso ao espaço da página: branco no alto, o tom descendo até a
// base do braço, com os riscos claros por cima.
function defsDoEnchimento() {
  const L = []
  const tile = (lado, c, base) => {
    const y1 = base - c.y - PERFIL[0][0]
    const y2 = base - c.y
    const paradas = PERFIL.map(([d, cor]) => {
      const off = r1((PERFIL[0][0] - d) / PERFIL[0][0] * 1000) / 1000
      return cor === '#fff' ? '<stop offset="' + off + '" stop-color="#fff"/>' : '<stop offset="' + off + '" class="s-' + cor.slice(6, 8) + '"/>'
    }).join('')
    L.push('  <linearGradient id="gLuz' + lado + '" gradientUnits="userSpaceOnUse" x1="0" y1="' + y1 + '" x2="0" y2="' + y2 + '">' + paradas + '</linearGradient>')
    L.push('  <pattern id="pRiscoLuz' + lado + '" patternUnits="userSpaceOnUse" width="5" height="5" patternTransform="rotate(' + (lado === 'E' ? -45 : 45) + ')"><rect width="1" height="5" fill="#ffece0" fill-opacity="0.11"/></pattern>')
    L.push('  <pattern id="pCalor' + lado + '" patternUnits="userSpaceOnUse" x="' + c.x + '" y="' + c.y + '" width="' + c.w + '" height="' + c.h + '">' +
      '<rect width="' + c.w + '" height="' + c.h + '" fill="url(#gLuz' + lado + ')"/><rect width="' + c.w + '" height="' + c.h + '" fill="url(#pRiscoLuz' + lado + ')"/></pattern>')
  }
  tile('E', CAIXA_E, BASE)
  tile('D', CAIXA_D, ZERO_D)
  L.push('  <linearGradient id="gFrente" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="0.22" stop-color="#fff"/><stop offset="0.33" stop-color="#fff" stop-opacity="0.45"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></linearGradient>')
  L.push('  <linearGradient id="gZonaAzul" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#52a0ff" stop-opacity="0.95"/><stop offset="1" stop-color="#235fe1" stop-opacity="0.7"/></linearGradient>')
  L.push('  <clipPath id="cEnchE"><path d="' + polTexto(polEnchE) + '"/></clipPath>')
  L.push('  <clipPath id="cEnchD"><path d="' + polTexto(polEnchD.map(espelho)) + '"/></clipPath>')
  return L
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Faixas de um lado, de cima para baixo, todas escondidas (enchimento vazio).
function faixas(lado) {
  const c = lado === 'E' ? CAIXA_E : CAIXA_D
  const fim = lado === 'E' ? BASE : ZERO_D
  const L = []
  for (let y = TOPO; y < fim; y += FAIXA) {
    L.push('      <rect x="' + c.x + '" y="' + y + '" width="' + c.w + '" height="' + (FAIXA + 0.5) + '" fill="url(#pCalor' + lado + ')" visibility="hidden"/>')
  }
  return L
}
const nFaixas = (lado) => Math.ceil(((lado === 'E' ? BASE : ZERO_D) - TOPO) / FAIXA)

function blocoVivo() {
  const L = []
  const add = (s) => L.push('    ' + s)
  add('<defs>')
  defsDoEnchimento().forEach(add)
  add('</defs>')
  for (const qual of ['E', 'D']) {
    const c = qual === 'E' ? CAIXA_E : CAIXA_D
    const G = []
    const g = (s) => G.push('      ' + s)
    g('<g clip-path="url(#cEnch' + qual + ')">')
    g('  <g id="faixas' + qual + '">')
    faixas(qual).forEach((f) => G.push('  ' + f))
    g('  </g>')
    g('  <g id="tracos' + qual + '">')
    tracos(qual).split('\n').forEach((t) => G.push('  ' + t.replace(/<line (x1="[^"]*" y1="([^"]*)")/, '<line $1 data-y="$2"').replace(/class="(maior|menor)"/, 'class="$1 escuro" visibility="hidden"')))
    g('  </g>')
    g('  <g id="frente' + qual + '" transform="translate(0 -40)" visibility="hidden"><rect x="' + c.x + '" y="0" width="' + c.w + '" height="9" fill="url(#gFrente)"/></g>')
    g('</g>')
    if (qual === 'D') g('<path id="zonaAzul" d="' + polTexto(polZona.map(espelho)) + '" fill="url(#gZonaAzul)" style="opacity: 0"/>')
    add(lado(qual === 'E' ? ANCORA_E : ANCORA_D, '\n' + G.join('\n') + '\n    '))
  }
  return L.join('\n')
}

function blocoJs() {
  const reta3 = (r) => [r1(r.x0 * 1000) / 1000, r.y0, Math.round(r.k * 10000) / 10000]
  const geo = {
    escalaE: ESCALA_E,
    escalaD: ESCALA_D,
    zeroD: ZERO_D,
    topo: TOPO,
    base: BASE,
    // Faixas do enchimento: [y da primeira, quantas], todas de `faixa` px.
    faixa: FAIXA,
    faixasE: [TOPO, nFaixas('E')],
    faixasD: [TOPO, nFaixas('D')],
    // Retas [x0, y0, k] (x = x0 + k (y - y0)) do lado esquerdo; o direito é 1920 - x.
    aro: { ponta: r1(aro0.ponta[1]), alto: reta3(aro0.alto), baixo: reta3(aro0.baixo) },
    dentro: { ponta: r1(face.ponta[1]), alto: reta3(dentro.alto), baixo: reta3(dentro.baixo) },
    // Os dois lados encolhidos no SVG: k e o ponto fixo de cada um (ver ESCALA).
    escala: { k: ESCALA, e: ANCORA_E, d: ANCORA_D },
  }
  return '  const GEO = ' + JSON.stringify(geo) + ';'
}

// ── Escrita ──────────────────────────────────────────────────────────────────────────────────
const MARCAS = [
  ['<!-- GERADO:fundo:inicio -->', '<!-- GERADO:fundo:fim -->', blocoFundo],
  ['<!-- GERADO:arte:inicio -->', '<!-- GERADO:arte:fim -->', blocoArte],
  ['<!-- GERADO:vivo:inicio -->', '<!-- GERADO:vivo:fim -->', blocoVivo],
  ['/* GERADO:js:inicio */', '/* GERADO:js:fim */', blocoJs],
]

function montar(texto) {
  let out = texto
  for (const [ini, fim, gera] of MARCAS) {
    const a = out.indexOf(ini)
    const b = out.indexOf(fim)
    if (a < 0 || b < a) throw new Error('marcadores ausentes: ' + ini)
    out = out.slice(0, a + ini.length) + '\n' + gera() + '\n  ' + out.slice(b)
  }
  return out
}

if (process.argv.includes('--relatorio')) {
  console.log('aro: topo', pt(aro0.topo), 'ponta', pt(aro0.ponta), 'base', pt(aro0.base))
  console.log('face: topo', pt(face.topo), 'ponta', pt(face.ponta), 'base', pt(face.base))
  console.log('enchimento: fora', pt(enchFora.ponta), 'dentro', pt(enchDentro.ponta))
  console.log('escala E:', ESCALA_E.map(([v, y]) => v + '→' + y + ' x' + r1(xNa(aro0, y))).join(', '))
  console.log('escala D:', ESCALA_D.map(([v, y]) => v + '→' + y + ' x' + r1(1920 - xNa(aro0, y))).join(', '))
  console.log('caixas:', JSON.stringify(CAIXA_E), JSON.stringify(CAIXA_D), 'faixas:', nFaixas('E'), nFaixas('D'))
  console.log('no painel: escala', ESCALA, 'em volta de', ANCORA_E.join(','), '/', ANCORA_D.join(','), 'topo', r1(naTela(ANCORA_E, aro0.topo)[1]), 'ponta', pt(naTela(ANCORA_E, aro0.ponta)))
  process.exit(0)
}

const atual = fs.readFileSync(arquivo, 'utf8')
const novo = montar(atual)
if (process.argv.includes('--verificar')) {
  if (novo !== atual) {
    console.error('FALHOU: o app.html do Bávaro ID8 não bate com scripts/gerar-bavaro-id8.mjs')
    process.exit(1)
  }
  console.log('ok: app.html bate com o gerador')
} else {
  fs.writeFileSync(arquivo, novo)
  console.log('geometria gravada em ' + path.relative(raiz, arquivo))
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
