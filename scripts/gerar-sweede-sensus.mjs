// Geometria do tema Sweede SENSUS (assets/PreinstalledThemes/sweedesensus).
//
// Os dois mostradores redondos (aro, face, brilho do ponteiro, traços, rótulos, cubo), os
// marcadores de combustível e bateria, o fundo (um tom só e a aba de cima) e as
// tabelas que o JS usa saem daqui e são escritos no app.html entre os marcadores GERADO. O resto
// do tema (texto, ícones, lógica) é escrito à mão no próprio app.html.
//
//   node scripts/gerar-sweede-sensus.mjs              regrava os blocos GERADO
//   node scripts/gerar-sweede-sensus.mjs --verificar  falha se o app.html não bater com o gerador
//   node scripts/gerar-sweede-sensus.mjs --relatorio  imprime as posições principais
//
// As medidas vêm da foto de referência (painel no estilo Sensus, 2000x754 reduzida a 1920x724):
// no painel 1920x720 ela entra deslocada 4,5 px para a direita e 2 px para cima, o que deixa os
// dois mostradores simétricos em x = 960. Ângulos em graus matemáticos (0 = direita, 90 = cima),
// medidos a partir do centro de cada mostrador.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const arquivo = path.join(raiz, 'app', 'src', 'main', 'assets', 'PreinstalledThemes', 'sweedesensus', 'app.html')

const r1 = (n) => Math.round(n * 10) / 10
const r2 = (n) => Math.round(n * 100) / 100
const rad = (a) => (a * Math.PI) / 180

// ── Medidas ─────────────────────────────────────────────────────────────────────────────────
const CY = 366
const CX_E = 413
const CX_D = 1507
// Raios (px), de dentro para fora: borda do cubo, fim do enchimento (onde começa a faixa preta
// dos traços), fim da faixa preta (começo do aro), divisa das duas faixas do aro, borda do aro.
const R = { cubo: 160, enche: 289, faixa: 307, aroMeio: 328, aro: 355 }
// Traços: menor (a cada 2,25°), médio (dezenas) e maior (rótulos). [raio de dentro, de fora].
const TRACO = { menor: [293, 305], medio: [287, 306], maior: [281, 306] }
const PASSO = 2.25

// ── Tamanho no painel do carro ───────────────────────────────────────────────────────────────
// Tudo acima (e o resto deste arquivo) é a medida da foto, que enche a tela de cima a baixo. No
// carro o painel de fábrica desenha POR CIMA do tema as luzes dele
// (docs/operations/mapa-de-luzes-do-painel.md): de cada lado, uma diagonal (TPMS, freio, ABS,
// airbag... à esquerda; freio de mão, bateria, 12 V... à direita) que sobe até as luzes verdes do
// farol, uma coluna colada na borda e, à esquerda, o READY (x 247-329, y 341-363), a placa de
// velocidade (x 240-291, y 492-545) e o combustível (x 346-385, y 662-707).
//
// Alpha 8.9: os mostradores encolheram 0,8 e as luzes assentavam no aro. Alpha 9.2: encolheram a
// 0,58 perto do centro, com todas as luzes por fora; no carro ficaram pequenos demais.
//
// Alpha 9.3: o mostrador esquerdo fica onde o painel de fábrica põe o dele. A diagonal de luzes é
// um arco em volta de um ponto perto de (410, 450): o READY e a placa ficam dentro dele, e o
// combustível embaixo. Com o centro em CENTRO_E e 0,8 (o tamanho da 8.9), a diagonal, o farol e a
// lanterna ficam a ~30 px do aro, e o READY e a placa caem na face lisa, entre o cubo e os
// traços, cada um num vão entre os números (ver ROTULOS_E e CUBO). O combustível cai embaixo, no
// vão da escala, onde o aro se apaga (ver APAGA_ARO). O direito é o espelho em x = 960; o vão da
// projeção (x 772-1148) continua livre entre os dois.
const ESCALA = 0.8
const CENTRO_E = [426, 434]
const CENTRO_D = [1920 - CENTRO_E[0], CENTRO_E[1]]
// O cubo (anel escuro, velocidade e câmbio dentro) encolhe a 0,875 em volta do centro do mostrador:
// com o cubo da foto (raio 160) a ponta direita do READY passaria por cima do anel dele.
const CUBO = 0.875
const R_CUBO = R.cubo * CUBO
// O aro se apaga embaixo, no vão da escala: começa a escurecer no traço do 0 (225°) e no do 260
// (315°) e some de APAGA_ARO[0] a [1] graus. É onde fica a luz do combustível (x 346-385,
// y 662-707, entre 251° e 262° do centro esquerdo) e, no direito, a vaga espelhada. A foto já tem o
// aro mais escuro ali.
const APAGA_ARO = [247, 293]
const RAMPA_ARO = 22
function mostrador(centro, cxFoto, html) {
  const tx = r2(centro[0] - cxFoto * ESCALA), ty = r2(centro[1] - CY * ESCALA)
  return '<g transform="matrix(' + ESCALA + ' 0 0 ' + ESCALA + ' ' + tx + ' ' + ty + ')">' + html + '</g>'
}
const noE = (html) => mostrador(CENTRO_E, CX_E, html)
const noD = (html) => mostrador(CENTRO_D, CX_D, html)
// A aba de cima (temperatura, ícone e hora) encolhe inteira em volta do meio da borda de cima: na
// medida da foto o canto direito dela passava embaixo da luz do cinto (x 1261-1297, y 18-66) e o
// esquerdo, na vaga espelhada. Com 0,87 os dois cantos ficam a ~25 px das caixas.
const ESCALA_ABA = 0.87
const aba = (x, y) => [960 + (x - 960) * ESCALA_ABA, y * ESCALA_ABA]

// Escalas: valor -> ângulo. Velocidade: 0 embaixo à esquerda (225°), linear até 200 no 3 horas
// (1,125°/km/h) e comprimida até 260 embaixo à direita. Potência: READY no 9 horas, 100 % no 3
// horas; a recarga desce do READY em direção ao OFF (225°), onde o ponteiro descansa desligado.
const ESCALA_E = [[0, 225], [200, 0], [260, -45]]
const ESCALA_D = [[-100, 220], [0, 180], [100, 0]]
const OFF_D = 225
const FIM_E = -45
const FIM_D = 0
// Traço maior a cada 20 km/h (e no 230), número a cada 40: sete números, um a cada 45°, do 0 ao
// 260. Até a 9.2 havia número a cada 20 (13 números, como na foto); com o mostrador no lugar do de
// fábrica, o READY cai entre o 80 e o 40 e a placa entre o 40 e o 0, e com número a cada 20 não
// sobra vão para nenhum dos dois.
const MAIORES_E = [0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 230, 260]
const ROTULOS_E = [0, 40, 80, 120, 160, 200, 260]
// Traços laranja da zona de carga (entre o READY e o OFF, como na foto).
const CARGA_D = [3, 9]   // índices dos traços, contados do OFF
// Arco azul (zona elétrica) por fora da faixa dos traços, do READY até a seta branca.
const ARCO_AZUL = { r0: 309.5, r1: 320.5, de: 180, ate: 102.3 }
// Combustível (embaixo, E à esquerda) e bateria (embaixo à direita, E embaixo): [ângulo E, ângulo F].
const COMB = { e: 240, f: 300, r0: 292, r1: 302 }
const BAT = { e: -51.5, f: -8, r0: 298, r1: 306 }

// Brilho que acompanha o ponteiro, medido na foto a r = 215 (o mesmo nos dois mostradores).
// Atrás do ponteiro (o enchimento): cor da foto a cada distância, em graus.
const TRAS = [[0, '#7c8c9a'], [2, '#7a8a98'], [4, '#758592'], [6, '#72808d'], [8, '#6d7a86'], [10, '#687681'],
  [12, '#616e7a'], [15, '#58636e'], [18, '#505a64'], [21, '#4a535c'], [24, '#444e56'], [27, '#3f4850'],
  [30, '#3b434b'], [36, '#353c42'], [42, '#30363c'], [48, '#2d3237'], [54, '#2a2e32'], [60, '#282c30'],
  [66, '#25292e'], [72, '#23272b'], [78, '#22262a'], [84, '#212529']]
// À frente (a face ainda vazia, iluminada pelo ponteiro).
const FRENTE = [[0, '#4e565c'], [4, '#4b5359'], [8, '#4b5359'], [12, '#4a5055'], [18, '#464c51'], [24, '#3f4347'],
  [30, '#353b3e'], [36, '#2e3437'], [42, '#25292c'], [48, '#1d1f22'], [54, '#18191c'], [60, '#151719'],
  [66, '#111315'], [72, '#0d0f10'], [78, '#070809'], [84, '#040506'], [88, '#000000']]
// Bordas das cunhas (mais finas perto do ponteiro, onde a cor muda mais depressa).
const faixaDe = (a, b, p) => { const o = []; for (let x = a; x < b - 1e-6; x += p) o.push(r2(x)); return o }
const BORDAS_TRAS = [...faixaDe(0, 24, 1), ...faixaDe(24, 48, 2), ...faixaDe(48, 84, 3), 84]
const BORDAS_FRENTE = [...faixaDe(0, 30, 1.5), ...faixaDe(30, 87, 3), 88]
// O enchimento de base (cor do fim do brilho) em fatias paradas de 9°: a borda da última fatia
// fica embaixo do começo opaco do brilho da frente, que cobre os primeiros 12°.
const FATIA = 9
const FRENTE_OPACA = 12
// A luz é dividida em duas: a de perto (até PERTO graus do ponteiro, com o ponteiro junto) gira a
// cada quadro; a de longe só acompanha quando o ponteiro se afasta LONGE_PASSO graus dela. O que
// gira invalida o retângulo em volta de si, e é tudo repintado embaixo: a luz inteira (±88°)
// repintava meio mostrador por quadro e o painel caía para ~24 quadros por segundo no emulador
// (os temas do carro ficam em 51-60 nas mesmas condições). A de longe começa LONGE_PASSO + 0,5°
// para dentro da de perto, então a borda dela nunca aparece; na emenda visível (PERTO graus) o
// degradê já é quase plano (1,4 nível por grau atrás, menos à frente), e o atraso de até
// LONGE_PASSO graus dá no máximo ~3 níveis de diferença.
const PERTO = 20
const LONGE_PASSO = 2

// Aro: luminância medida na foto a cada 10° (as duas faixas; iguais nos dois mostradores).
const ARO_DENTRO = [40, 31, 24, 38, 38, 34, 32, 34, 36, 39, 33, 37, 64, 65, 43, 34, 31, 25, 23, 10, 18, 38, 48, 39, 44, 49, 51, 54, 55, 60, 60, 58, 56, 53, 49, 43]
const ARO_FORA = [30, 38, 46, 83, 85, 76, 60, 48, 40, 33, 25, 31, 72, 99, 72, 55, 50, 40, 35, 25, 20, 50, 58, 38, 44, 42, 30, 25, 25, 38, 39, 33, 26, 19, 4, 22]
// Reflexo fixo da face, embaixo à direita: luminância por ângulo (medida a r 180..280).
const REFLEXO_FACE = [[262, 0], [270, 6], [275, 16], [280, 25], [285, 31], [290, 35], [300, 36], [310, 35],
  [315, 29], [320, 21], [325, 12], [330, 9], [335, 7], [342, 0]]

// ── Utilidades ───────────────────────────────────────────────────────────────────────────────
const P = (cx, r, a) => [cx + r * Math.cos(rad(a)), CY - r * Math.sin(rad(a))]
const pt = (p) => r1(p[0]) + ' ' + r1(p[1])
const pt2 = (p) => r2(p[0]) + ' ' + r2(p[1])

// Setor de coroa de a0 até a1 no sentido horário (a0 > a1), entre os raios r0 e r1.
function setor(cx, r0, r1_, a0, a1, fmt = pt) {
  const grande = a0 - a1 > 180 ? 1 : 0
  return 'M' + fmt(P(cx, r1_, a0)) + ' A' + r1_ + ' ' + r1_ + ' 0 ' + grande + ' 1 ' + fmt(P(cx, r1_, a1)) +
    ' L' + fmt(P(cx, r0, a1)) + ' A' + r0 + ' ' + r0 + ' 0 ' + grande + ' 0 ' + fmt(P(cx, r0, a0)) + ' Z'
}
function linha(cx, r0, r1_, a) {
  const p = P(cx, r0, a), q = P(cx, r1_, a)
  return 'M' + pt2(p) + ' L' + pt2(q)
}
const hex = (s) => [1, 3, 5].map((i) => parseInt(s.slice(i, i + 2), 16))
const lum = (c) => 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
const cinza = (l) => { const v = Math.max(0, Math.min(255, Math.round(l))).toString(16).padStart(2, '0'); return '#' + v + v + v }
function interp(tab, x) {
  if (x <= tab[0][0]) return tab[0][1]
  for (let i = 1; i < tab.length; i++) {
    if (x <= tab[i][0]) {
      const [xa, ya] = tab[i - 1], [xb, yb] = tab[i]
      return ya + ((yb - ya) * (x - xa)) / (xb - xa)
    }
  }
  return tab[tab.length - 1][1]
}
const lumDe = (tab) => tab.map(([d, c]) => [d, lum(hex(c))])
const anguloDe = (tab, v) => interp(tab, v)

// Rótulo de escala: centro do texto a uma distância fixa da ponta de dentro do traço maior,
// descontada a meia-largura do texto na direção do raio (elipse da caixa do texto).
// Na 8.9 o "0" ficou só com o traço maior, porque a placa de velocidade do painel de fábrica
// (x 240-291, y 492-545) caía em cima dele. Desde a 9.2 ele voltou (a 9.3 põe a placa num vão entre
// os números). A lista fica para o caso de outra luz do carro cair sobre um número.
const SEM_ROTULO = []
const FONTE_ROTULO = 42
const DIGITO = 0.562 * FONTE_ROTULO
function posRotulo(cx, v, a) {
  const w = String(v).length * DIGITO, h = 30
  const ext = Math.hypot((Math.cos(rad(a)) * w) / 2, (Math.sin(rad(a)) * h) / 2)
  const c = P(cx, 269 - ext, a)
  // baseline = centro + meia altura do dígito (0,711 em / 2)
  return [c[0], c[1] + 0.3555 * FONTE_ROTULO]
}

// ── Fundo (#bg) ──────────────────────────────────────────────────────────────────────────────
function blocoFundo() {
  const s = []
  const x = (v) => r1(aba(v, 0)[0]), y = (v) => r1(aba(960, v)[1])
  s.push('      <defs>')
  s.push('        <linearGradient id="gAba" x1="' + x(628) + '" y1="0" x2="' + x(1292) + '" y2="0" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#1c1c1c"/><stop offset="0.5" stop-color="#303030"/><stop offset="1" stop-color="#1c1c1c"/></linearGradient>')
  s.push('        <linearGradient id="gAbaFio" x1="' + x(712) + '" y1="0" x2="' + x(1208) + '" y2="0" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#555" stop-opacity="0"/><stop offset="0.5" stop-color="#8a8e8e"/><stop offset="1" stop-color="#555" stop-opacity="0"/></linearGradient>')
  s.push('        <radialGradient id="gBrilhoAba" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#fff" stop-opacity="0.75"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>')
  s.push('      </defs>')
  // Um tom só (Alpha 9.2). Até a 9.1 havia a moldura cinza da foto (#161616), a "tela" preta em
  // volta dos mostradores e o painel do centro em degradê: no LCD do carro o preto e o cinza
  // quase preto viram manchas azuladas de brilho diferente, e os círculos da tela preta nem
  // acompanhavam mais os mostradores encolhidos. Tudo é o preto do #bg; os mostradores trazem o
  // próprio aro, e a aba de cima continua (é onde ficam a temperatura e a hora).
  s.push('      <rect width="1920" height="720" fill="#000"/>')
  // Aba de cima: temperatura, o ícone do meio e a hora (medidas da foto levadas por aba()).
  s.push('      <path d="M' + x(628) + ' 0 L' + x(1292) + ' 0 L' + x(1214) + ' ' + y(68) + ' Q' + x(1205) + ' ' + y(76) + ' ' + x(1193) + ' ' + y(76) +
    ' L' + x(727) + ' ' + y(76) + ' Q' + x(715) + ' ' + y(76) + ' ' + x(706) + ' ' + y(68) + ' Z" fill="url(#gAba)"/>')
  s.push('      <path d="M' + x(712) + ' ' + y(75) + ' L' + x(1208) + ' ' + y(75) + '" stroke="url(#gAbaFio)" stroke-width="1.6" fill="none"/>')
  s.push('      <ellipse cx="' + x(972) + '" cy="' + y(77) + '" rx="' + r1(60 * ESCALA_ABA) + '" ry="' + r1(3.5 * ESCALA_ABA) + '" fill="url(#gBrilhoAba)"/>')
  return s.join('\n')
}

// ── Arte parada de baixo (#arte): o aro ──────────────────────────────────────────────────────
// As duas faixas do aro são anéis de cunhas de 2° com a cor da foto interpolada (cada uma 0,4° por
// baixo da seguinte). Anel, e não disco: a luz que gira invalida quase o quadrado inteiro do
// mostrador a cada quadro, e tudo o que estiver embaixo dela é repintado junto. Um disco com
// degradê cônico (e uma face preta por cima) custava duas vezes a área do quadrado em degradê; o
// anel só entra com os pedaços dos cantos. A face é o preto do #bg.
// Quanto do aro aparece em cada ângulo: 1 fora do trecho apagado (APAGA_ARO), 0 dentro dele, e um
// degradê de RAMPA_ARO graus em cada ponta.
function luzDoAro(a) {
  a = ((a % 360) + 360) % 360
  const [de, ate] = APAGA_ARO
  if (a <= de - RAMPA_ARO || a >= ate + RAMPA_ARO) return 1
  if (a >= de && a <= ate) return 0
  return a < de ? (de - a) / RAMPA_ARO : (a - ate) / RAMPA_ARO
}
function anel(cx, tab, r0, r1_) {
  const t = tab.map((v, i) => [i * 10, v]).concat([[360, tab[0]]])
  const s = []
  for (let a = 0; a < 360; a += 2) {
    const luz = luzDoAro(a + 1)
    if (luz <= 0) continue
    s.push('<path d="' + setor(cx, r0, r1_, a + 2.4, a) + '" fill="' + cinza(interp(t, a + 1) * luz) + '"/>')
  }
  return s.join('')
}
// O fio claro por dentro do aro também se apaga embaixo: um arco inteiro onde o aro aparece todo e
// pedaços de 1° com a opacidade caindo nos degradês. Os fios pretos ficam inteiros (preto no preto).
function fioClaro(cx, r) {
  const [de, ate] = APAGA_ARO
  const arco = (a0, a1) => 'M' + pt2(P(cx, r, a0)) + ' A' + r + ' ' + r + ' 0 ' + (a0 - a1 > 180 ? 1 : 0) + ' 1 ' + pt2(P(cx, r, a1))
  const s = ['<path d="' + arco(de - RAMPA_ARO, ate + RAMPA_ARO - 360) + '" stroke-opacity="0.35"/>']
  for (let i = 0; i < RAMPA_ARO; i++) {
    const op = (0.35 * (RAMPA_ARO - i - 0.5) / RAMPA_ARO).toFixed(3)
    s.push('<path d="' + arco(de - RAMPA_ARO + i + 1, de - RAMPA_ARO + i) + '" stroke-opacity="' + op + '"/>')
    s.push('<path d="' + arco(ate + RAMPA_ARO - i, ate + RAMPA_ARO - i - 1) + '" stroke-opacity="' + op + '"/>')
  }
  return '<g stroke="#9a9a9a" stroke-width="1.2">' + s.join('') + '</g>'
}
function blocoArte() {
  const s = []
  for (const [cx, no] of [[CX_E, noE], [CX_D, noD]]) {
    s.push('    ' + no('<g class="aro">' + anel(cx, ARO_FORA, R.aroMeio, R.aro - 1.2) + anel(cx, ARO_DENTRO, R.faixa, R.aroMeio + 0.3) + '</g>' +
      '<g class="aro-fios" fill="none">' +
      '<circle cx="' + cx + '" cy="' + CY + '" r="' + (R.aro - 1) + '" stroke="#000" stroke-opacity="0.55" stroke-width="2.2"/>' +
      '<circle cx="' + cx + '" cy="' + CY + '" r="' + r1(R.aroMeio) + '" stroke="#000" stroke-opacity="0.45" stroke-width="1.5"/>' +
      fioClaro(cx, r1(R.faixa + 0.8)) + '</g>'))
  }
  return s.join('\n')
}

// ── Camada viva (#vivo): enchimento de base, brilho e ponteiro ─────────────────────────────────
// Tudo é desenhado com o ponteiro para cima (90°) e girado inteiro por transform.
// Cor de cada cunha: atrás, a mistura da base com o pico; à frente, do preto com a luz da face.
// A fração (data-a) sai da luminância da foto; o JS refaz as cores quando o tom muda. As cunhas
// são opacas e cada uma passa 0,35° por baixo da vizinha mais perto do ponteiro (desenhadas da
// mais longe para a mais perto), então não sobra emenda nem faixa dupla.
const mistura = (a, b, t) => '#' + hex(a).map((v, i) => Math.round(v + (hex(b)[i] - v) * t).toString(16).padStart(2, '0')).join('')
function cunhasDoBrilho(cx, parte) {
  // parte 'perto': as cunhas até PERTO graus; 'longe': da (PERTO - LONGE_PASSO - 0,5) para fora.
  const entra = (d0, d1) => parte === 'perto' ? d1 <= PERTO + 1e-6 : d1 > PERTO - LONGE_PASSO - 0.5
  const lt = lumDe(TRAS), lf = lumDe(FRENTE)
  const base = lt[lt.length - 1][1], picoT = lt[0][1], picoF = lf[0][1]
  const cBase = TRAS[TRAS.length - 1][1], cPicoT = TRAS[0][1], cPicoF = FRENTE[0][1]
  const tras = []
  for (let k = BORDAS_TRAS.length - 2; k >= 0; k--) {
    const d0 = BORDAS_TRAS[k], d1 = BORDAS_TRAS[k + 1]
    if (!entra(d0, d1)) continue
    const a = Math.max(0, Math.min(1, (interp(lt, (d0 + d1) / 2) - base) / (picoT - base)))
    const ini = k === 0 ? 90 : 90 + d0 - 0.35
    tras.push('<path class="gt" data-a="' + a.toFixed(3) + '" fill="' + mistura(cBase, cPicoT, a) + '" d="' + setor(cx, R_CUBO - 3, R.enche, 90 + d1, ini) + '"/>')
  }
  // À frente: opaca nos primeiros 12° (cobre a borda da última fatia acesa).
  const frente = []
  for (let k = BORDAS_FRENTE.length - 2; k >= 0; k--) {
    const d0 = BORDAS_FRENTE[k], d1 = BORDAS_FRENTE[k + 1]
    if (!entra(d0, d1)) continue
    let a = d1 <= FRENTE_OPACA ? 1 : interp(lf, (d0 + d1) / 2) / picoF
    a = Math.max(0, Math.min(1, a))
    const fim = k === 0 ? 90 : 90 - d0 + 0.35
    frente.push('<path class="gf" data-a="' + a.toFixed(3) + '" fill="' + mistura('#000000', cPicoF, a) + '" d="' + setor(cx, R_CUBO - 3, R.enche, fim, 90 - d1) + '"/>')
  }
  return '<g class="luz-frente">' + frente.join('') + '</g><g class="luz-tras">' + tras.join('') + '</g>'
}
function ponteiro(cx) {
  const poli = (r0, r1_, h0, h1) => [[cx - h0, CY - r0], [cx - h1, CY - r1_], [cx + h1, CY - r1_], [cx + h0, CY - r0]].map(pt2).join(' ')
  return '<polygon points="' + poli(R_CUBO - 10, 290, 6, 10) + '" fill="#fff" fill-opacity="0.07"/>' +
    '<polygon points="' + poli(R_CUBO - 10, 290, 3, 5) + '" fill="#fff" fill-opacity="0.2"/>' +
    '<polygon points="' + poli(R_CUBO - 10, 289, 1.1, 1.9) + '" fill="#fff"/>'
}
function fatias(cx, a0, a1) {
  // Fatias de FATIA graus do começo da escala (a0) até o fim (a1), no sentido horário; cada uma
  // passa 0,5° por baixo da seguinte para não aparecer a emenda.
  const n = Math.ceil((a0 - a1) / FATIA - 1e-9)
  const s = []
  for (let i = 0; i < n; i++) {
    const ini = a0 - i * FATIA
    const fim = Math.max(a1, ini - FATIA - 0.5)
    s.push('<path class="base" d="' + setor(cx, R_CUBO - 3, R.enche, ini, fim) + '" visibility="hidden"/>')
  }
  return { html: s.join(''), n }
}
const nFatias = (a0, a1) => Math.ceil((a0 - a1) / FATIA - 1e-9)
function blocoVivo() {
  const s = []
  s.push('    <defs>')
  s.push('      <clipPath id="cEscalaE"><path d="' + setor(CX_E, R_CUBO - 10, R.enche, 225, FIM_E) + '"/></clipPath>')
  s.push('      <clipPath id="cEscalaD"><path d="' + setor(CX_D, R_CUBO - 10, R.enche, OFF_D, FIM_D) + '"/></clipPath>')
  s.push('    </defs>')
  // O recorte fica dentro do grupo do mostrador: vale nas coordenadas da foto, como o giro da luz.
  for (const [lado, cx, a0, a1, no] of [['E', CX_E, 225, FIM_E, noE], ['D', CX_D, OFF_D, FIM_D, noD]]) {
    const giro = 'rotate(' + (90 - a0) + ' ' + cx + ' ' + CY + ')'
    s.push('    ' + no('<g id="fatias' + lado + '">' + fatias(cx, a0, a1).html + '</g>' +
      '<g clip-path="url(#cEscala' + lado + ')">' +
      '<g id="luzLonge' + lado + '" transform="' + giro + '">' + cunhasDoBrilho(cx, 'longe') + '</g>' +
      '<g id="luzPerto' + lado + '" transform="' + giro + '">' + cunhasDoBrilho(cx, 'perto') + ponteiro(cx) + '</g></g>'))
  }
  return s.join('\n')
}

// ── Arte parada de cima (#frente): traços, rótulos, marcadores, cubos ─────────────────────────
function tracosE() {
  const menor = [], medio = [], maior = []
  const n = Math.round((225 - FIM_E) / PASSO)
  for (let i = 0; i <= n; i++) {
    const a = 225 - i * PASSO
    const v = i <= 100 ? 2 * i : 200 + 3 * (i - 100)
    const eMaior = MAIORES_E.includes(v)
    const eMedio = !eMaior && v <= 200 && v % 10 === 0
    const t = eMaior ? TRACO.maior : eMedio ? TRACO.medio : TRACO.menor
    ;(eMaior ? maior : eMedio ? medio : menor).push(linha(CX_E, t[0], t[1], a))
  }
  return '<path class="traco-menor" d="' + menor.join(' ') + '"/><path class="traco-medio" d="' + medio.join(' ') +
    '"/><path class="traco-maior" d="' + maior.join(' ') + '"/>'
}
function tracosD() {
  const menor = [], maior = [], carga = []
  const n = Math.round((OFF_D - FIM_D) / PASSO)
  for (let i = 0; i <= n; i++) {
    const a = OFF_D - i * PASSO
    if (i <= 1 || Math.abs(a - 180) < 0.01 || i === n) maior.push(linha(CX_D, TRACO.maior[0], TRACO.maior[1] + 1, a))
    else if (i >= CARGA_D[0] && i <= CARGA_D[1]) carga.push(linha(CX_D, TRACO.menor[0], TRACO.menor[1], a))
    else menor.push(linha(CX_D, TRACO.menor[0], TRACO.menor[1], a))
  }
  return '<path class="traco-menor" d="' + menor.join(' ') + '"/><path class="traco-maior" d="' + maior.join(' ') +
    '"/><path id="carga" class="traco-carga" d="' + carga.join(' ') + '"/>'
}
// Marcador em arco segmentado (combustível e bateria): segmentos de 1° que acendem do E para o F,
// com os separadores brancos por cima.
function marcador(id, m, classe) {
  const s = []
  const passo = m.f > m.e ? 1 : -1
  const n = Math.round(Math.abs(m.f - m.e))
  const seg = []
  for (let i = 0; i < n; i++) {
    const a = m.e + passo * i
    const b = a + passo * 1.3
    const [a0, a1] = passo > 0 ? [Math.min(b, m.f), a] : [a, Math.max(b, m.f)]
    seg.push('<path d="' + setor(CX_D, m.r0, m.r1, a0, a1) + '" visibility="hidden"/>')
  }
  s.push('<g id="' + id + '" class="' + classe + '">' + seg.join('') + '</g>')
  // Separadores a cada 1/16 e maiores no E, meio e F.
  const sep = [], sepMaior = []
  for (let k = 0; k <= 16; k++) {
    const a = m.e + ((m.f - m.e) * k) / 16
    if (k % 8 === 0) sepMaior.push(linha(CX_D, m.r0 - 8, m.r1 + 2, a))
    else sep.push(linha(CX_D, m.r0 + 1, m.r1 - 1, a))
  }
  s.push('<path class="sep" d="' + sep.join(' ') + '"/><path class="sep-maior" d="' + sepMaior.join(' ') + '"/>')
  return { html: s.join(''), n }
}
function hub(cx) {
  const c = (r, extra) => '<circle cx="' + cx + '" cy="' + CY + '" r="' + r + '" ' + extra + '/>'
  // Medidas da foto, encolhidas por CUBO em volta do centro do mostrador.
  const tx = r2(cx * (1 - CUBO)), ty = r2(CY * (1 - CUBO))
  // Anéis por traço, não discos empilhados: menos área repintada a cada quadro.
  return '<g transform="matrix(' + CUBO + ' 0 0 ' + CUBO + ' ' + tx + ' ' + ty + ')">' +
    c(145, 'fill="none" stroke="url(#gCuboAnel)" stroke-width="30"') +
    c(R.cubo - 0.8, 'fill="none" stroke="url(#gCuboFio)" stroke-width="1.6"') +
    c(132, 'fill="none" stroke="#2c2c2c" stroke-width="1.2"') +
    c(124.5, 'fill="none" stroke="#000" stroke-width="13"') +
    c(118, 'fill="url(#gCuboMiolo)"') +
    c(118, 'fill="none" stroke="#2a2a2a" stroke-width="1"') + '</g>'
}
// Reflexo fixo da face, embaixo à direita: véu branco fraco (a luminância da foto sobre o preto),
// por cima da luz do ponteiro, que passa por baixo dele sem apagá-lo. Cunhas de 2° sem sobrepor
// (com opacidade tão baixa a emenda não aparece).
function reflexo(cx) {
  const s = []
  for (let a = REFLEXO_FACE[0][0]; a < REFLEXO_FACE[REFLEXO_FACE.length - 1][0]; a += 2) {
    const l = interp(REFLEXO_FACE, a + 1)
    if (l < 1) continue
    s.push('<path d="' + setor(cx, R_CUBO - 2, R.enche, a + 2, a) + '" fill-opacity="' + (l / 255).toFixed(3) + '"/>')
  }
  return '<g class="reflexo" fill="#fff">' + s.join('') + '</g>'
}
function blocoFrente() {
  const s = []
  s.push('    <defs>')
  s.push('      <linearGradient id="gCuboAnel" x1="0" y1="0" x2="0" y2="1" gradientUnits="objectBoundingBox"><stop offset="0" stop-color="#171717"/><stop offset="1" stop-color="#1f1f1f"/></linearGradient>')
  s.push('      <linearGradient id="gCuboFio" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#6a6a6a"/><stop offset="0.5" stop-color="#1c1c1c"/><stop offset="1" stop-color="#5a5a5a"/></linearGradient>')
  s.push('      <radialGradient id="gCuboMiolo" cx="0.5" cy="0.78" r="0.8"><stop offset="0" stop-color="#171717"/><stop offset="0.55" stop-color="#0d0d0d"/><stop offset="1" stop-color="#030303"/></radialGradient>')
  s.push('      <radialGradient id="gSinal" cx="0.5" cy="0.5" r="0.5"><stop offset="0" stop-color="#ffb347" stop-opacity="0.55"/><stop offset="1" stop-color="#ffb347" stop-opacity="0"/></radialGradient>')
  s.push('    </defs>')

  // Cada mostrador num grupo só (ver ESCALA); dentro dele a ordem é a de sempre, e os dois não se
  // encostam, então a ordem entre eles não importa.
  const e = s, d = []
  e.push('    ' + reflexo(CX_E))
  d.push('    ' + reflexo(CX_D))
  // Mostrador esquerdo: traços e rótulos da velocidade.
  e.push('    <g class="tracos">' + tracosE() + '</g>')
  const rot = ROTULOS_E.map((v, i) => {
    if (SEM_ROTULO.includes(v)) return ''
    const [x, y] = posRotulo(CX_E, v, anguloDe(ESCALA_E, v))
    return '<text id="rE' + i + '" x="' + r1(x) + '" y="' + r1(y) + '">' + v + '</text>'
  })
  e.push('    <g class="rotulos">' + rot.join('') + '</g>')
  // Marca do limite da via (TSR), desenhada no 12 horas e girada.
  e.push('    <g id="limite" transform="rotate(0 ' + CX_E + ' ' + CY + ')" visibility="hidden"><path d="' +
    setor(CX_E, R.enche - 6, R.faixa + 1, 91.3, 88.7) + '" class="limite"/></g>')

  // Mostrador direito: traços, arco azul, seta, ícones, rótulos, combustível e bateria.
  d.push('    <g class="tracos">' + tracosD() + '</g>')
  d.push('    <path class="arco-azul" d="' + setor(CX_D, ARCO_AZUL.r0, ARCO_AZUL.r1, ARCO_AZUL.de, ARCO_AZUL.ate) + '"/>')
  const a = ARCO_AZUL.ate
  const seta = [P(CX_D, 321, a + 1.2), P(CX_D, 321, a - 1.2), P(CX_D, 276, a - 0.1)]
  d.push('    <polygon class="seta-ev" points="' + seta.map(pt2).join(' ') + '"/>')
  const raio = P(CX_D, 255, 111), gota = P(CX_D, 253, 93.6)
  d.push('    <g id="icRaio" class="icone-fonte raio" transform="translate(' + r1(raio[0]) + ' ' + r1(raio[1]) + ')"><path d="M3 -18 L-8 3 L-1 3 L-4 18 L8 -4 L1 -4 L5 -18 Z"/></g>')
  d.push('    <g id="icGota" class="icone-fonte" transform="translate(' + r1(gota[0]) + ' ' + r1(gota[1]) + ')"><path d="M0 -16 C4 -8 10 -1 10 5 C10 11 5.5 15 0 15 C-5.5 15 -10 11 -10 5 C-10 -1 -4 -8 0 -16 Z"/></g>')
  const ready = [CX_D - 265, CY + 0.3555 * 27]
  d.push('    <text id="rotReady" class="rot-d" x="' + r1(ready[0]) + '" y="' + r1(ready[1]) + '">READY</text>')
  const off = P(CX_D, 251, 227.9)
  d.push('    <text id="rotOff" class="rot-d rot-off" x="' + r1(off[0] - 20) + '" y="' + r1(off[1] + 8.5) + '">OFF</text>')
  const comb = marcador('comb', COMB, 'marc-comb')
  const bat = marcador('bat', BAT, 'marc-bat')
  d.push('    ' + comb.html)
  d.push('    ' + bat.html)
  const fr = (x, y, t, cls) => '<text class="' + cls + '" x="' + r1(x) + '" y="' + r1(y) + '">' + t + '</text>'
  const meioC = P(CX_D, 265, 270), fC = P(CX_D, 274, -60), meioB = P(CX_D, 257, -30.2), fB = P(CX_D, 282, -10.6)
  d.push('    ' + fr(meioC[0], meioC[1] + 14, '½', 'rot-frac') + fr(fC[0], fC[1] + 14, '1', 'rot-frac') +
    fr(meioB[0], meioB[1] + 14, '½', 'rot-frac') + fr(fB[0], fB[1] + 14, '1', 'rot-frac'))
  const icC = P(CX_D, 263, 240.9), icB = P(CX_D, 268, -50)
  d.push('    <use href="#i-bomba" x="' + r1(icC[0] - 15) + '" y="' + r1(icC[1] - 17) + '" width="30" height="34" class="ic-marc"/>')
  d.push('    <path class="ic-marc-seta" d="M' + r1(icC[0] + 20) + ' ' + r1(icC[1] + 3) + ' l8 4.5 l-8 4.5 Z"/>')
  d.push('    <use href="#i-bat-vert" x="' + r1(icB[0] - 10) + '" y="' + r1(icB[1] - 16) + '" width="20" height="32" class="ic-marc"/>')

  // Cubos por cima de tudo (o ponteiro nasce por baixo deles).
  e.push('    ' + hub(CX_E))
  d.push('    ' + hub(CX_D))
  // e == s: as defs ficam fora dos grupos, o resto de cada lado entra no seu.
  const nDefs = s.indexOf('    </defs>') + 1
  const html = s.slice(0, nDefs).join('\n') + '\n' +
    '    ' + noE('\n' + s.slice(nDefs).join('\n') + '\n    ') + '\n' +
    '    ' + noD('\n' + d.join('\n') + '\n    ')
  return { html, comb: comb.n, bat: bat.n }
}

// ── Textos em HTML dos mostradores (#css) ──────────────────────────────────────────────────────
// Velocidade, câmbio e o que fica embaixo de cada mostrador são HTML, posicionados pela foto. O
// bloco GERADO:css os leva para o painel com a mesma ESCALA e o mesmo centro dos grupos do SVG,
// para que continuem no mesmo lugar do mostrador; os da aba ('aba') andam com a aba e os do cubo
// ('cubo') encolhem também por CUBO. O resto do estilo deles fica no CSS à mão.
// l/t/w/h: caixa na foto (px); f: fonte; lh: altura da linha; ls: espaçamento entre letras.
// Hodômetro e trecho: no vão de baixo da escala, entre o cubo e o aro (até a 9.2 ficavam em cima do
// aro, t 629 e 674; na 9.3 a luz do combustível do carro fica ali, à esquerda do meio).
const TEXTOS = [
  { sel: '#temp', lado: 'aba', l: 666, t: 14, w: 200, f: 42, lh: 42 },
  { sel: '#tempUn', lado: 'aba', f: 34 },
  { sel: '#relogio', lado: 'aba', l: 1046, t: 14, w: 200, f: 42, lh: 42 },
  { sel: '#emblema', lado: 'aba', l: 928, t: 4, w: 60, h: 60 },
  { sel: '#vel', lado: 'e', cubo: true, l: 263, t: 296, w: 300, f: 120, lh: 120, ls: -2 },
  { sel: '#velUn', lado: 'e', cubo: true, l: 353, t: 413, w: 120, f: 23, lh: 23 },
  { sel: '#odo', lado: 'e', l: 262, t: 556, w: 300, f: 31, lh: 31 },
  { sel: '#trecho', lado: 'e', l: 258, t: 598, w: 300, f: 31, lh: 31 },
  { sel: '#trecho .un', lado: 'e', f: 24 },
  { sel: '#marcha', lado: 'd', cubo: true, l: 1415, t: 291, w: 100, f: 105, lh: 105 },
  { sel: '#seletor', lado: 'd', cubo: true, l: 1508, t: 258, w: 2, h: 174 },
  { sel: '.pos', lado: 'd', cubo: true, l: 1514, w: 32, f: 36, lh: 36 },
  { sel: '#posP', lado: 'd', cubo: true, t: 252 }, { sel: '#posR', lado: 'd', cubo: true, t: 294 },
  { sel: '#posN', lado: 'd', cubo: true, t: 336 }, { sel: '#posD', lado: 'd', cubo: true, t: 378 },
  { sel: '#modoEnergia', lado: 'd', cubo: true, l: 1401, t: 446, w: 200, f: 27, lh: 27, ls: 1 },
  { sel: '.aut-ic', lado: 'd', l: 1206, w: 46, h: 26 },
  { sel: '#autEvIc', lado: 'd', t: 633 }, { sel: '#autCombIc', lado: 'd', t: 676 },
  { sel: '.aut-val', lado: 'd', l: 1222, w: 100, f: 31, lh: 31 },
  { sel: '#autEv', lado: 'd', t: 632 }, { sel: '#autComb', lado: 'd', t: 674 },
  { sel: '.aut-un', lado: 'd', l: 1330, f: 24, lh: 31 },
  { sel: '#autEvUn', lado: 'd', t: 632 }, { sel: '#autCombUn', lado: 'd', t: 674 },
  { sel: '#consumo', lado: 'd', l: 1532, t: 674, w: 260, f: 31, lh: 31 },
  { sel: '#consumo .un', lado: 'd', f: 24 },
]
function blocoCss() {
  const px = (n) => r1(n) + 'px'
  return TEXTOS.map((x) => {
    const naAba = x.lado === 'aba'
    const [centro, cxFoto] = x.lado === 'e' ? [CENTRO_E, CX_E] : [CENTRO_D, CX_D]
    const k = naAba ? ESCALA_ABA : x.cubo ? ESCALA * CUBO : ESCALA
    const p = naAba ? aba(x.l ?? 960, x.t ?? 0)
      : [centro[0] + ((x.l ?? cxFoto) - cxFoto) * k, centro[1] + ((x.t ?? CY) - CY) * k]
    const d = []
    if (x.l != null) d.push('left: ' + px(p[0]))
    if (x.t != null) d.push('top: ' + px(p[1]))
    if (x.w != null) d.push('width: ' + px(x.w * k))
    if (x.h != null) d.push('height: ' + px(x.h * k))
    if (x.f != null) d.push('font-size: ' + px(x.f * k))
    if (x.lh != null) d.push('line-height: ' + px(x.lh * k))
    if (x.ls != null) d.push('letter-spacing: ' + px(x.ls * k))
    return '  ' + x.sel + ' { ' + d.join('; ') + '; }'
  }).join('\n')
}

// ── Tabelas do JS ────────────────────────────────────────────────────────────────────────────
function blocoJs() {
  const geo = {
    cy: CY,
    e: { cx: CX_E, escala: ESCALA_E, ini: 225, fim: FIM_E, fatias: nFatias(225, FIM_E) },
    d: { cx: CX_D, escala: ESCALA_D, ini: OFF_D, fim: FIM_D, fatias: nFatias(OFF_D, FIM_D) },
    fatia: FATIA,
    comb: { e: COMB.e, f: COMB.f, n: Math.round(Math.abs(COMB.f - COMB.e)) },
    bat: { e: BAT.e, f: BAT.f, n: Math.round(Math.abs(BAT.f - BAT.e)) },
    rotulos: ROTULOS_E,
    // Cores da foto do brilho: base (fim do enchimento), pico atrás e pico à frente do ponteiro.
    tom: { base: TRAS[TRAS.length - 1][1], picoTras: TRAS[0][1], picoFrente: FRENTE[0][1] },
    longePasso: LONGE_PASSO,
  }
  return '  const GEO = ' + JSON.stringify(geo) + ';'
}

// ── Escrita ──────────────────────────────────────────────────────────────────────────────────
const MARCAS = [
  ['<!-- GERADO:fundo:inicio -->', '<!-- GERADO:fundo:fim -->', blocoFundo],
  ['<!-- GERADO:arte:inicio -->', '<!-- GERADO:arte:fim -->', blocoArte],
  ['<!-- GERADO:vivo:inicio -->', '<!-- GERADO:vivo:fim -->', blocoVivo],
  ['<!-- GERADO:frente:inicio -->', '<!-- GERADO:frente:fim -->', () => blocoFrente().html],
  ['/* GERADO:js:inicio */', '/* GERADO:js:fim */', blocoJs],
  ['/* GERADO:css:inicio */', '/* GERADO:css:fim */', blocoCss],
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
  console.log('centros:', CX_E, CX_D, 'cy', CY, 'raios', JSON.stringify(R))
  console.log('no painel: escala', ESCALA, 'centros', CENTRO_E.join(','), CENTRO_D.join(','), 'ponta dos traços a', r1(TRACO.maior[1] * ESCALA), 'aro até', r1(R.aro * ESCALA))
  console.log('velocidade:', ROTULOS_E.map((v) => v + '→' + r1(anguloDe(ESCALA_E, v)) + '°').join(', '))
  console.log('potência:', ESCALA_D.map(([v, a]) => v + '→' + a + '°').join(', '), 'OFF', OFF_D)
  console.log('fatias:', nFatias(225, FIM_E), nFatias(OFF_D, FIM_D), 'cunhas atrás', BORDAS_TRAS.length - 1, 'frente', BORDAS_FRENTE.length - 1)
  const f = blocoFrente()
  console.log('marcadores: combustível', f.comb, 'bateria', f.bat)
  process.exit(0)
}

const atual = fs.readFileSync(arquivo, 'utf8')
const novo = montar(atual)
if (process.argv.includes('--verificar')) {
  if (novo !== atual) {
    console.error('FALHOU: o app.html do Sweede SENSUS não bate com scripts/gerar-sweede-sensus.mjs')
    process.exit(1)
  }
  console.log('ok: app.html bate com o gerador')
} else {
  fs.writeFileSync(arquivo, novo)
  console.log('geometria gravada em ' + path.relative(raiz, arquivo))
}
