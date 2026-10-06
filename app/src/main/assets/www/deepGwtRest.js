// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
/**
 * MODO DESCANSO — a central inteira preta, só um relógio analógico discreto no meio.
 *
 * COMO SE SAI DAQUI
 * Um toque em qualquer ponto da tela. Não há botão, não há canto certo, não há gesto para
 * decorar: a tela toda é a saída. Nos primeiros segundos uma linha apagada diz isso, e some —
 * depois disso a única coisa acesa é o relógio, que é o pedido.
 *
 * COMO SE ENTRA
 *  - pelo cartão "Modo Descanso" em CONFIGURAÇÕES DeepGWT (deepGwtPanel.js);
 *  - segurando o relógio de qualquer página por [HOLD_MS] (o atalho rápido: o widget que já
 *    mostra a hora vira a tela que só mostra a hora).
 *
 * POR QUE O LAÇO 3D NÃO É PAUSADO
 * Existe `__app._render3dPaused`, e ele parece a resposta óbvia para "consumir o mínimo
 * possível". Não é: o próprio app registra, no comentário de `_applyBlankDesktop`, que parar o
 * laço já travou a imagem nesta central — a WebView continuou compondo o último quadro que tinha
 * e a página não conseguiu mais se redesenhar. Numa tela cujo conteúdo é um relógio, isso
 * significaria hora congelada, que é pior do que gastar alguns ciclos.
 *
 * O que dá para economizar sem apostar nisso já está economizado: o laço do app só desenha
 * quando alguém pede quadro (`requestRender`), e atrás de uma camada opaca, com o carro parado,
 * ninguém pede — ele fica girando em vazio. Não há WebGL, não há widget, não há telemetria sendo
 * pintada.
 *
 * O PONTEIRO DE SEGUNDOS VARRE, NÃO PULA
 * Ele anda a 24 Hz (ver [startTicking]), que é a taxa de um relógio mecânico bom e o que foi
 * pedido. O custo NÃO é 24 vezes o de antes: a cada quadro só o ponteiro de segundos é reescrito —
 * um atributo, um nó. Hora, minuto e a deriva anti-retenção continuam sendo tocados uma vez por
 * segundo, porque é a frequência em que eles mudam.
 *
 * E o quadro vem do `requestAnimationFrame`, que nesta página já está limitado a 24 quadros por
 * segundo pelo orçamento instalado em deepGwtClient.js. Ou seja: o relógio não acrescenta um relógio
 * novo ao sistema, ele pega carona no que já existe — e com a página escondida o mesmo orçamento
 * derruba tudo para 2 Hz sozinho.
 *
 * A FAIXA NATIVA DE ÍCONES
 * Ela é desenhada fora da WebView, então nenhum z-index daqui a cobre — e enquanto o launcher era
 * um APK à parte não havia como escondê-la: dava para levantar a WebView com
 * `AppLauncherBridge.setChromeOnTop`, e o launcher trazia a faixa de volta 20 s depois sozinho.
 * Numa tela que fica ligada por horas, isso era o defeito, não um detalhe.
 *
 * Com os dois apps fundidos o código nativo é o mesmo APK, e a ponte ganhou
 * `AppLauncherBridge.setLauncherHidden(bool)`, que esconde a faixa de fato e não expira. O Modo
 * Descanso pede escondido ao entrar e devolve ao sair — pelo `window.requestLauncherHidden` de
 * appLauncherClient.js, que arbitra com a página de configurações quando as duas querem a mesma
 * coisa ao mesmo tempo. O `setChromeOnTop` continua sendo chamado
 * porque ele faz outra coisa útil: dispensa o popup de app que estiver aberto e mantém o toque de
 * saída chegando aqui.
 */
(function () {
  'use strict';

  /** Tempo de pressão no relógio para abrir o Modo Descanso. */
  var HOLD_MS = 700;

  /** Quanto tempo a dica de saída fica visível antes de apagar. */
  var HINT_MS = 3500;

  /** Folga de dedo parado, em pixels, antes de a pressão longa ser considerada um arrasto. */
  var TOLERANCIA_PX = 12;

  /**
   * Deriva anti-retenção de imagem. A tela de uma central fica ligada por horas e um relógio
   * parado no mesmo pixel é exatamente o caso que marca painel. O deslocamento é minúsculo e os
   * dois períodos são diferentes de propósito (não fecham no mesmo ponto), então o desenho passeia
   * devagar por uma área pequena em vez de repetir um caminho curto. A 0,01 px/s ninguém vê
   * mexer — e não é um elemento a mais na tela, que é o que o pedido proíbe.
   */
  var DRIFT_PX = 7;

  /**
   * Menor giro do ponteiro de segundos que vale um `setAttribute`, em graus.
   *
   * A 24 Hz o passo natural é 0,25°. O corte está bem abaixo disso — ele não existe para ralear os
   * quadros, existe para o caso em que dois quadros caem dentro do mesmo milissegundo (e para o
   * quadro extra que o navegador às vezes entrega): aí o ângulo é idêntico e a escrita seria pura
   * perda.
   */
  var PASSO_MINIMO_GRAUS = 0.02;
  var DRIFT_X_PERIOD_S = 1013;
  var DRIFT_Y_PERIOD_S = 1367;

  var layer = null;
  var hintNode = null;
  var hands = null;
  var driftNode = null;
  var ticking = null;
  /** Segundo inteiro já desenhado por [drawSlow]. -1 força o primeiro quadro a desenhar tudo. */
  var ultimoSegundoDesenhado = -1;
  /** Último ângulo escrito no ponteiro de segundos, para não reescrever o mesmo valor. */
  var ultimoGrauDoSegundo = null;
  var hintTimer = null;
  var chromeWasOnTop = null;
  var on = false;

  var CSS = [
    // position:fixed e não absolute: o Modo Descanso não acompanha rolagem nem transform de
    // página nenhuma — ele é a tela.
    '.deepgwt-rest{position:fixed;top:0;right:0;bottom:0;left:0;z-index:9000;display:none;',
    '  background:#000;-webkit-tap-highlight-color:transparent;touch-action:none;}',
    '.deepgwt-rest.is-on{display:block;}',
    // O relógio é centralizado por translate e não por flex: o desenho do mostrador tem o próprio
    // transform de deriva, e empilhar os dois num elemento só faria um cancelar o outro.
    '.deepgwt-rest-face{position:absolute;top:50%;left:50%;width:260px;height:260px;',
    '  margin:-130px 0 0 -130px;}',
    '.deepgwt-rest-hint{position:absolute;left:0;right:0;bottom:38px;text-align:center;',
    '  font:400 13px/1.4 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;',
    '  letter-spacing:.18em;text-transform:uppercase;color:rgba(255,255,255,.26);',
    '  transition:opacity .9s ease;}',
    '.deepgwt-rest-hint.is-off{opacity:0;}'
  ].join('');

  /**
   * O mostrador. SVG e não canvas: são atributos de transform escritos direto nos nós — sem
   * contexto de desenho, sem limpar e repintar 260x260 px a cada quadro. É o que torna 24 Hz
   * barato: um `setAttribute` num elemento contra um repinte de área inteira.
   *
   * Sem números e sem marca de minuto: "discreto" e "sem elementos desnecessários" foi o pedido.
   * Os 12 traços de hora bastam para ler o relógio e são o que menos acende a tela.
   */
  function buildFace() {
    var svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 200 200');
    svg.setAttribute('width', '260');
    svg.setAttribute('height', '260');

    var drift = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    svg.appendChild(drift);

    var ring = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    ring.setAttribute('cx', '100');
    ring.setAttribute('cy', '100');
    ring.setAttribute('r', '92');
    ring.setAttribute('fill', 'none');
    ring.setAttribute('stroke', 'rgba(255,255,255,.10)');
    ring.setAttribute('stroke-width', '1');
    drift.appendChild(ring);

    for (var h = 0; h < 12; h++) {
      var tick = document.createElementNS('http://www.w3.org/2000/svg', 'line');
      tick.setAttribute('x1', '100');
      tick.setAttribute('y1', h % 3 === 0 ? '16' : '19');
      tick.setAttribute('x2', '100');
      tick.setAttribute('y2', '27');
      tick.setAttribute('stroke', h % 3 === 0 ? 'rgba(255,255,255,.42)' : 'rgba(255,255,255,.20)');
      tick.setAttribute('stroke-width', h % 3 === 0 ? '2.4' : '1.4');
      tick.setAttribute('stroke-linecap', 'round');
      tick.setAttribute('transform', 'rotate(' + h * 30 + ' 100 100)');
      drift.appendChild(tick);
    }

    var hour = line(drift, 100, 108, 100, 52, 'rgba(255,255,255,.62)', 4.2);
    var minute = line(drift, 100, 112, 100, 34, 'rgba(255,255,255,.62)', 2.8);
    var second = line(drift, 100, 118, 100, 30, 'rgba(120,200,215,.45)', 1.2);

    var pin = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    pin.setAttribute('cx', '100');
    pin.setAttribute('cy', '100');
    pin.setAttribute('r', '2.6');
    pin.setAttribute('fill', 'rgba(255,255,255,.62)');
    drift.appendChild(pin);

    driftNode = drift;
    hands = { hour: hour, minute: minute, second: second };
    return svg;
  }

  function line(parent, x1, y1, x2, y2, stroke, width) {
    var node = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    node.setAttribute('x1', x1);
    node.setAttribute('y1', y1);
    node.setAttribute('x2', x2);
    node.setAttribute('y2', y2);
    node.setAttribute('stroke', stroke);
    node.setAttribute('stroke-width', width);
    node.setAttribute('stroke-linecap', 'round');
    parent.appendChild(node);
    return node;
  }

  /**
   * O ponteiro de segundos, e só ele. Chamado a cada quadro.
   *
   * O ângulo sai dos milissegundos, não do segundo inteiro: `getSeconds()` devolve um degrau, e
   * desenhar um degrau 24 vezes por segundo custaria 24 quadros para mostrar 1 posição. O que faz
   * a varredura é a fração.
   *
   * A escrita é evitada quando o ângulo não mudou o suficiente para se ver. A 260 px de mostrador,
   * a ponta do ponteiro percorre ~0,02 px por centésimo de grau — abaixo de [PASSO_MINIMO_GRAUS] o
   * `setAttribute` mandaria o navegador reavaliar o transform para nada.
   */
  function drawSecond(now) {
    if (!hands) return;
    var ms = now.getSeconds() * 1000 + now.getMilliseconds();
    var graus = ms * 0.006; // 360° / 60 000 ms
    if (ultimoGrauDoSegundo !== null && Math.abs(graus - ultimoGrauDoSegundo) < PASSO_MINIMO_GRAUS) {
      return;
    }
    ultimoGrauDoSegundo = graus;
    hands.second.setAttribute('transform', 'rotate(' + graus.toFixed(2) + ' 100 100)');
  }

  /**
   * Hora, minuto e deriva. Chamado uma vez por segundo — é a frequência em que estes três mudam de
   * verdade, e não há motivo para reescrevê-los 24 vezes para produzir o mesmo pixel.
   *
   * Ponteiros de hora e minuto andam em fração, não em degrau: às 10h30 o ponteiro das horas tem
   * de estar entre o 10 e o 11, senão o relógio fica com cara de errado mesmo marcando certo.
   */
  function drawSlow(now) {
    if (!hands) return;
    var s = now.getSeconds();
    var m = now.getMinutes() + s / 60;
    var h = (now.getHours() % 12) + m / 60;
    hands.hour.setAttribute('transform', 'rotate(' + h * 30 + ' 100 100)');
    hands.minute.setAttribute('transform', 'rotate(' + m * 6 + ' 100 100)');
    if (driftNode) {
      var t = now.getTime() / 1000;
      var dx = Math.sin(2 * Math.PI * t / DRIFT_X_PERIOD_S) * DRIFT_PX;
      var dy = Math.cos(2 * Math.PI * t / DRIFT_Y_PERIOD_S) * DRIFT_PX;
      driftNode.setAttribute('transform', 'translate(' + dx.toFixed(2) + ' ' + dy.toFixed(2) + ')');
    }
  }

  /** Desenha o mostrador inteiro. Usado ao entrar, para a tela já nascer na hora certa. */
  function drawNow() {
    var now = new Date();
    drawSlow(now);
    drawSecond(now);
  }

  function injectCss() {
    if (document.getElementById('deepgwt-rest-css')) return;
    var style = document.createElement('style');
    style.id = 'deepgwt-rest-css';
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  /**
   * O host é o #hv-root e não o <body> pelo mesmo motivo que o painel: o root é `position:fixed`
   * e cria contexto de empilhamento, então qualquer coisa pendurada no body fica ATRÁS dos filhos
   * dele por mais z-index que se peça — e uma tela de descanso atrás do launcher não é uma tela
   * de descanso.
   */
  function host() {
    return (window.DeepGWT && window.DeepGWT.mountRoot && window.DeepGWT.mountRoot()) ||
      document.getElementById('hv-root') || document.body;
  }

  function ensureLayer() {
    if (layer && layer.parentNode) return layer;
    injectCss();
    layer = document.createElement('div');
    layer.className = 'deepgwt-rest';
    layer.id = 'deepgwt-rest';

    var face = document.createElement('div');
    face.className = 'deepgwt-rest-face';
    face.appendChild(buildFace());
    layer.appendChild(face);

    hintNode = document.createElement('div');
    hintNode.className = 'deepgwt-rest-hint';
    hintNode.textContent = 'Toque para sair';
    layer.appendChild(hintNode);

    // pointerdown e não click: sair tem de acontecer no encostar do dedo, sem esperar o
    // levantar e sem os 300ms que um clique sintetizado pode custar nesta WebView.
    layer.addEventListener('pointerdown', function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      exit();
    }, true);
    // Fallback para WebView sem eventos de ponteiro.
    layer.addEventListener('touchstart', function (ev) {
      ev.preventDefault();
      ev.stopPropagation();
      exit();
    }, true);

    host().appendChild(layer);
    return layer;
  }

  /**
   * Tira as superfícies nativas da frente, guardando o estado para devolver na saída.
   *
   * São dois pedidos com papéis diferentes: `setLauncherHidden` some com a faixa de ícones (o que
   * a tela preta pede) e `setChromeOnTop` levanta a WebView sobre o que sobrar, que é o que
   * garante o toque de saída chegando aqui mesmo com um app aberto num dos lados.
   */
  function raiseChrome() {
    try {
      var app = window.__app;
      chromeWasOnTop = !!(app && app.state && app.state.appsFabOpen);
      var bridge = window.AppLauncherBridge;
      if (bridge && typeof bridge.dismissOverlays === 'function') bridge.dismissOverlays();
      if (typeof window.requestLauncherHidden === 'function') window.requestLauncherHidden('rest', true);
      if (bridge && typeof bridge.setChromeOnTop === 'function') bridge.setChromeOnTop(true);
    } catch (e) {
      chromeWasOnTop = null;
    }
  }

  function restoreChrome() {
    try {
      var bridge = window.AppLauncherBridge;
      if (typeof window.requestLauncherHidden === 'function') window.requestLauncherHidden('rest', false);
      if (bridge && typeof bridge.setChromeOnTop === 'function') {
        bridge.setChromeOnTop(chromeWasOnTop === true);
      }
    } catch (e) {
      // Sem ponte, não há o que devolver.
    }
    chromeWasOnTop = null;
  }

  function enter() {
    if (on) return;
    on = true;
    ensureLayer();
    drawNow();
    layer.classList.add('is-on');
    if (hintNode) {
      hintNode.classList.remove('is-off');
      clearTimeout(hintTimer);
      hintTimer = setTimeout(function () {
        if (hintNode) hintNode.classList.add('is-off');
      }, HINT_MS);
    }
    raiseChrome();
    startTicking();
  }

  /**
   * O laço do mostrador.
   *
   * POR QUE `requestAnimationFrame` E NÃO UM `setInterval` DE 41 ms
   *
   * Um intervalo de 1000/24 nunca cai em cima do quadro: ele acorda quando quer, escreve o
   * atributo, e a composição só acontece no próximo quadro — que pode estar a 40 ms dali. O
   * resultado é o ponteiro andando em passos irregulares apesar da média certa, que é exatamente o
   * defeito que 24 Hz vem corrigir.
   *
   * `requestAnimationFrame` é o relógio da tela, e nesta página ele JÁ está limitado a 24 quadros
   * por segundo pelo orçamento de deepGwtClient.js. Então pedir quadro aqui não abre uma segunda
   * cadência competindo com a do launcher: é a mesma, e o orçamento também é quem derruba tudo
   * para 2 Hz quando a página fica escondida, de graça.
   *
   * A virada do SEGUNDO continua sendo respeitada: `drawSlow` só é chamado quando o segundo
   * inteiro muda, então hora e minuto viram no instante certo em vez de herdarem o momento em que
   * se entrou no modo.
   */
  function startTicking() {
    stopTicking();
    ultimoSegundoDesenhado = -1;
    ultimoGrauDoSegundo = null;
    drawNow();

    var passo = function () {
      if (!on) { ticking = null; return; }
      var now = new Date();
      var s = now.getSeconds();
      if (s !== ultimoSegundoDesenhado) {
        ultimoSegundoDesenhado = s;
        drawSlow(now);
      }
      drawSecond(now);
      ticking = window.requestAnimationFrame(passo);
    };
    ticking = window.requestAnimationFrame(passo);
  }

  function stopTicking() {
    if (ticking === null) return;
    if (typeof window.cancelAnimationFrame === 'function') window.cancelAnimationFrame(ticking);
    ticking = null;
    ultimoSegundoDesenhado = -1;
    ultimoGrauDoSegundo = null;
  }

  function exit() {
    if (!on) return;
    on = false;
    stopTicking();
    clearTimeout(hintTimer);
    hintTimer = null;
    if (layer) layer.classList.remove('is-on');
    restoreChrome();
    // O launcher ficou parado atrás de uma camada opaca; pede um punhado de quadros para ele
    // voltar pintado em vez de aparecer com o último quadro que tinha.
    try {
      var app = window.__app;
      if (app && app.requestRender) app.requestRender(12);
    } catch (e) {
      // Sem app, não há o que repintar.
    }
  }

  function toggle() {
    if (on) exit();
    else enter();
  }

  /**
   * Atalho: segurar o relógio de qualquer página.
   *
   * Delegado no documento e não ligado ao nó do widget porque o launcher recria a árvore de
   * widgets a cada troca de página — um listener no elemento morreria na primeira virada.
   *
   * O clique curto do widget (abrir a configuração do relógio) continua funcionando: só quando a
   * pressão completa [HOLD_MS] é que o clique seguinte é engolido na fase de captura.
   */
  function installClockHold() {
    var timer = null;
    var swallow = false;
    var origem = null;

    function cancel() {
      clearTimeout(timer);
      timer = null;
      origem = null;
    }

    document.addEventListener('pointerdown', function (ev) {
      if (on) return;
      var target = ev.target && ev.target.closest && ev.target.closest('h6-clock.hv-widget-clock');
      if (!target) return;
      cancel();
      origem = { x: ev.clientX, y: ev.clientY };
      timer = setTimeout(function () {
        timer = null;
        origem = null;
        swallow = true;
        enter();
      }, HOLD_MS);
    }, true);

    ['pointerup', 'pointercancel'].forEach(function (name) {
      document.addEventListener(name, cancel, true);
    });

    // Só cancela quando o dedo REALMENTE saiu do lugar. Um dedo parado num capacitivo entrega
    // pointermove o tempo todo por ruído do painel; cancelar no primeiro deles tornaria a pressão
    // longa impossível de completar.
    document.addEventListener('pointermove', function (ev) {
      if (!timer || !origem) return;
      if (Math.abs(ev.clientX - origem.x) > TOLERANCIA_PX ||
        Math.abs(ev.clientY - origem.y) > TOLERANCIA_PX) {
        cancel();
      }
    }, true);

    document.addEventListener('click', function (ev) {
      if (!swallow) return;
      swallow = false;
      ev.preventDefault();
      ev.stopPropagation();
    }, true);
  }

  window.DeepGwtRest = {
    enter: enter,
    exit: exit,
    toggle: toggle,
    isOn: function () { return on; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', installClockHold);
  } else {
    installClockHold();
  }
})();
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
