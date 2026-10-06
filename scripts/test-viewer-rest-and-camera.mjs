#!/usr/bin/env node
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
/**
 * Trava o que foi feito no launcher 3D nesta rodada:
 *
 *  - Modo Descanso (deepGwtRest.js): tela preta, relógio analógico, saída por toque.
 *  - A câmera não volta para o ângulo padrão no meio de uma animação do modelo.
 *
 * Por que é teste de FONTE e não de comportamento: o launcher é um APK de terceiro de 1,9 MB de
 * HTML, que só roda dentro da WebView da central com as pontes nativas presentes. Não existe aqui
 * um jeito honesto de executá-lo. O comportamento do relógio e da saída por toque foi conferido ao
 * vivo no navegador (relógio batendo com a hora do sistema, entrar/sair, pressão longa no widget);
 * o que ESTE arquivo garante é que as mudanças continuem no lugar depois de um reempacotamento ou
 * de uma atualização do launcher — que é justamente quando elas somem sem ninguém ver.
 *
 * Uso: VIEWER_SRC=~/Downloads/haval-h6-viewer-... node scripts/test-viewer-rest-and-camera.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { homedir } from 'node:os'
import { fileURLToPath } from 'node:url'

// Depois da fusão dos dois apps o `assets/www` do launcher mora DENTRO do repositório
// (app/src/main/assets/www) e é ele que vai para o APK — então o padrão passou a ser esse, e
// estas verificações deixaram de ser opcionais. VIEWER_SRC continua valendo para apontar para
// uma pasta descompactada de fora, e o "PULADO" segue existindo para esse caso.
const VIEWER_SRC = process.env.VIEWER_SRC || resolve(dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main')
const WWW = join(VIEWER_SRC, 'assets', 'www')

let falhas = 0
let total = 0

function ok(descricao, condicao, detalhe) {
  total++
  if (condicao) {
    console.log('ok  ' + descricao)
  } else {
    falhas++
    console.log('FALHOU  ' + descricao + (detalhe ? '\n        ' + detalhe : ''))
  }
}

if (!existsSync(WWW)) {
  console.log('Launcher 3D não encontrado em ' + VIEWER_SRC + ' — nada a verificar.')
  process.exit(0)
}

const index = readFileSync(join(WWW, 'index.html'), 'utf8')
const restPath = join(WWW, 'deepGwtRest.js')
const rest = existsSync(restPath) ? readFileSync(restPath, 'utf8') : ''
const painel = readFileSync(join(WWW, 'deepGwtPanel.js'), 'utf8')

console.log('\nModo Descanso\n')

ok('deepGwtRest.js existe', rest.length > 0)
ok(
  'index.html carrega o deepGwtRest.js antes do deepGwtClient.js',
  /<script src="\.\/deepGwtRest\.js"><\/script>[\s\S]*<script src="\.\/deepGwtClient\.js"><\/script>/.test(index)
)
ok(
  'o fundo é preto sólido, não uma transparência',
  /\.deepgwt-rest\{[^}]*background:#000/.test(rest),
  'um véu translúcido deixaria o launcher aparecer por baixo — o pedido era 100% preto'
)
ok(
  'a camada cobre a tela inteira por position:fixed',
  /\.deepgwt-rest\{position:fixed;top:0;right:0;bottom:0;left:0/.test(rest)
)
ok(
  'sai da frente de tudo que o launcher desenha',
  /z-index:9000/.test(rest)
)
ok(
  'o nome está na lista de exceções da página em branco do launcher',
  index.includes(':not(.deepgwt-rest)'),
  'sem isso a regra #hv-root.hv-blank-page > *:not(...) esconderia a camada'
)
ok(
  'a saída é no encostar do dedo (pointerdown), não no clique',
  /addEventListener\('pointerdown'[\s\S]{0,200}exit\(\)/.test(rest)
)
ok(
  'tem fallback de touchstart para WebView sem eventos de ponteiro',
  /addEventListener\('touchstart'/.test(rest)
)
ok(
  'o relógio é SVG desenhado por atributo, não canvas repintado',
  rest.includes("createElementNS('http://www.w3.org/2000/svg'") && !rest.includes('getContext(')
)
ok(
  'ponteiros de hora e minuto andam em fração, não em degrau',
  /now\.getMinutes\(\) \+ s \/ 60/.test(rest) && /\(now\.getHours\(\) % 12\) \+ m \/ 60/.test(rest),
  'sem a fração, às 10h30 o ponteiro das horas aponta para o 10 cravado'
)
// ── O laço do relógio mudou de forma: 24 Hz no ponteiro de segundos ────────────────────────
//
// Estas três verificações exigiam `setInterval(drawNow, 1000)` e o alinhamento do primeiro tique
// com a virada do segundo. Era a forma certa enquanto o ponteiro andava de degrau em degrau; o
// pedido agora é varredura, e o laço passou a ser `requestAnimationFrame` — que nesta página já
// está limitado a 24 quadros por segundo pelo orçamento de deepGwtClient.js.
//
// O que aquelas verificações protegiam de verdade continua protegido, e melhor: quem prova a
// cadência agora é `scripts/test-relogio-descanso.mjs`, que EXECUTA o arquivo num DOM de mentira,
// entrega quadros com um relógio controlado e conta as escritas por nó. Aqui ficam só as amarras
// de fonte que a execução não alcança.
ok(
  'a virada do segundo continua sendo respeitada',
  /if \(s !== ultimoSegundoDesenhado\)/.test(rest),
  'hora e minuto têm de virar no segundo certo, não no instante em que se entrou no modo'
)
ok(
  'só o ponteiro de segundos anda por quadro; o resto continua a 1 Hz',
  /function drawSecond\(now\)/.test(rest) && /function drawSlow\(now\)/.test(rest) &&
    !/setInterval\(draw/.test(rest),
  '24 Hz não pode custar o mostrador inteiro 24 vezes — é um atributo por quadro'
)
ok(
  'sair para o relógio: nenhum laço fica vivo',
  /function exit\(\)[\s\S]{0,300}stopTicking\(\)/.test(rest) &&
    /function stopTicking\(\)[\s\S]{0,300}cancelAnimationFrame\(ticking\)/.test(rest),
  'um rAF que se rearma sozinho gira para sempre atrás de uma tela que o usuário já fechou'
)
ok(
  'o laço 3D NÃO é pausado',
  !/_render3dPaused\s*=\s*true/.test(rest),
  'parar o laço já travou a imagem nesta central; num relógio isso seria hora congelada'
)
ok(
  'a pressão longa no relógio é delegada no documento',
  /closest\('h6-clock\.hv-widget-clock'\)/.test(rest),
  'ligada ao nó, morreria na primeira troca de página'
)
ok(
  'a pressão longa tolera dedo trêmulo antes de cancelar',
  /TOLERANCIA_PX/.test(rest) && /pointermove/.test(rest)
)
ok(
  'o clique curto do widget continua passando',
  /if \(!swallow\) return;/.test(rest),
  'engolir todo clique tiraria a configuração do relógio do usuário'
)
ok(
  'o painel DeepGWT oferece o Modo Descanso',
  painel.includes('Modo Descanso') && painel.includes('window.DeepGwtRest'),
)
ok(
  'a deriva anti-retenção usa dois períodos diferentes',
  /DRIFT_X_PERIOD_S = (\d+)/.test(rest) &&
    /DRIFT_Y_PERIOD_S = (\d+)/.test(rest) &&
    rest.match(/DRIFT_X_PERIOD_S = (\d+)/)[1] !== rest.match(/DRIFT_Y_PERIOD_S = (\d+)/)[1],
  'períodos iguais fariam o desenho repetir o mesmo caminho curto'
)

console.log('\nCâmera durante animações\n')

ok(
  'o retorno ao ângulo padrão espera a animação terminar',
  /_returnCameraHome\(\)[\s\S]{0,900}if \(this\._eventOpeningsBusy\(\)\)[\s\S]{0,300}this\._armCamIdleReturn\(\);/.test(index),
  'sem isto a câmera volta 3 s depois do toque, no meio da porta abrindo'
)
ok(
  'o contador de 3 s recomeça depois da animação, em vez de ser descartado',
  /_armCamIdleReturn\(\) \{[\s\S]{0,260}\}, 3000\);/.test(index) &&
    /_eventOpeningsBusy\(\)[\s\S]{0,300}this\._armCamIdleReturn\(\);\s*\n\s*return;/.test(index)
)
ok(
  'a espera tem teto, para a câmera nunca ficar presa',
  /_camIdleAnimHoldCap\(\) \{\s*\n\s*return 2e4;/.test(index) &&
    /now - this\._camIdleAnimHoldSince < this\._camIdleAnimHoldCap\(\)/.test(index)
)
ok(
  'um novo arrasto zera a espera',
  /_cancelCamIdleReturn\(\);[\s\S]{0,220}this\._camIdleAnimHoldSince = 0;/.test(index)
)
ok(
  'esconder os pontos de toque também espera a animação',
  /_armHotspotHideTimer\(\) \{[\s\S]{0,900}this\._eventOpeningsBusy\(\)[\s\S]{0,300}this\._armHotspotHideTimer\(\);/.test(index),
  'esconder os pontos desfaz o close-up (→ _restoreHotspotCam) e tiraria a câmera da ação'
)
ok(
  'tocar no carro zera a espera dos pontos',
  /_pulseHotspots\(opts\) \{[\s\S]{0,260}this\._hsHideAnimHoldSince = 0;/.test(index)
)
ok(
  'a condição de "animando" é a que o próprio app já usava',
  /_eventOpeningsBusy\(\) \{[\s\S]{0,700}_roofEventBusy\(\);/.test(index),
  'portas, vidros por canto, porta-malas, retrovisores, teto e cortina'
)

console.log('')
if (falhas > 0) {
  console.log(`${total} verificações, ${falhas} falharam.`)
  process.exit(1)
}
console.log(`${total} verificações, todas passaram.`)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
