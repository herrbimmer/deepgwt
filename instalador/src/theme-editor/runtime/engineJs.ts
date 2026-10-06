// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O motor. Emitido byte a byte idêntico em todo tema — o que muda entre dois temas exportados é
// só o payload e o bloco de tokens de cor. Isso é auditável: um `diff' entre dois 'app.html'
// gerados não deve tocar neste trecho.
//
// A estrutura não foi inventada. Os dois temas escritos à mão que o app instala hoje compartilham
// cerca de 60% do JavaScript byte a byte — o construtor de marcações, o registro dinâmico, a
// coalescência por quadro, o bloco de bridge. O motor é aquilo extraído e parametrizado.

import { ICONS_JS } from './iconsJs'
import { RENDERERS_JS } from './renderersJs'

export const ENGINE_JS = `(function () {
  'use strict';
  var NS = 'http://www.w3.org/2000/svg';
  var RM = false;
  try { RM = window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) {}

  var THEME = null;
  var PAGES = [];
  var PAGE_NODES = [];
  var ACTIVE_PAGE = 0;
  var BY_KEY = {};       // chave do carro -> [instância]
  var BUFFER = {};       // último valor bruto de cada chave, inclusive antes do DOM existir
  var PENDING = {};      // chaves esperando o próximo quadro
  var scheduled = false;
  var fallbackTimer = null;
  var CARD = 0;
  var warningActiveState = false;
  var PROJ = { carPlayInDash: false, projectionMirrorInDash: false, projectionPreparingD3: false };
  var MODE = 'day';
  var headlightsOn = false;
  var manualNight = false;

  // --- geometria ---------------------------------------------------------------------------
  function svgEl(tag, attrs) {
    var n = document.createElementNS(NS, tag);
    for (var k in attrs) if (Object.prototype.hasOwnProperty.call(attrs, k)) n.setAttribute(k, attrs[k]);
    return n;
  }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function clamp01(v) { return clamp(v, 0, 1); }

  // 0 grau aponta para cima e cresce no sentido horário.
  function polar(cx, cy, r, deg) {
    var rad = ((deg - 90) * Math.PI) / 180;
    return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) };
  }

  function describeArc(cx, cy, r, a0, a1) {
    if (a1 - a0 < 0.5) return '';
    var p1 = polar(cx, cy, r, a0), p2 = polar(cx, cy, r, a1);
    var large = a1 - a0 > 180 ? 1 : 0;
    return 'M ' + p1.x.toFixed(2) + ' ' + p1.y.toFixed(2) + ' A ' + r + ' ' + r + ' 0 ' + large + ' 1 ' + p2.x.toFixed(2) + ' ' + p2.y.toFixed(2);
  }

  function wedgePath(cx, cy, rIn, rOut, a0, a1) {
    var o1 = polar(cx, cy, rOut, a0), o2 = polar(cx, cy, rOut, a1);
    var i2 = polar(cx, cy, rIn, a1), i1 = polar(cx, cy, rIn, a0);
    var large = a1 - a0 > 180 ? 1 : 0;
    return 'M ' + o1.x.toFixed(2) + ' ' + o1.y.toFixed(2) +
      ' A ' + rOut + ' ' + rOut + ' 0 ' + large + ' 1 ' + o2.x.toFixed(2) + ' ' + o2.y.toFixed(2) +
      ' L ' + i2.x.toFixed(2) + ' ' + i2.y.toFixed(2) +
      ' A ' + rIn + ' ' + rIn + ' 0 ' + large + ' 0 ' + i1.x.toFixed(2) + ' ' + i1.y.toFixed(2) + ' Z';
  }

  // O ponteiro tem as coordenadas fixadas UMA vez e depois só gira. Reescrever x1/y1/x2/y2 a cada
  // quadro re-rasteriza o drop-shadow junto, e isso aparece como engasgo no hardware do carro.
  function pinNeedle(line, cx, cy, rIn, rOut, startDeg) {
    var a = polar(cx, cy, rIn, startDeg), b = polar(cx, cy, rOut, startDeg);
    line.setAttribute('x1', a.x.toFixed(2)); line.setAttribute('y1', a.y.toFixed(2));
    line.setAttribute('x2', b.x.toFixed(2)); line.setAttribute('y2', b.y.toFixed(2));
  }
  function rotateNeedle(g, deg, cx, cy, startDeg) {
    g.setAttribute('transform', 'rotate(' + (deg - startDeg).toFixed(2) + ' ' + cx + ' ' + cy + ')');
  }

  // --- mapeamento de valor -----------------------------------------------------------------
  // Limite da via lido pela camera. Mora fora do mapeador porque um mapeador so enxerga a chave do
  // proprio elemento, e o limite chega por OUTRA chave -- alimentar cada velocimetro exigiria que
  // ele assinasse a chave do vizinho.
  var LIMITE = { valor: null, valido: true };
  var LIMIT_KEY = 'car.map.tsr.nav_speed_limit';
  var LIMIT_STATUS_KEY = 'car.map.tsr.nav_speed_limit_sign_status';
  var SIGN_LIMIT_INSTS = [];
  // Mostradores cuja escala a preferencia do carro pode trocar: os de faixa simples.
  var DIAIS_DE_ESCALA = [];
  var REGRA_KEYS = {};
  var PENDING_REGRAS = false;

  function limiteAtual(map) {
    if (LIMITE.valido && LIMITE.valor !== null && isFinite(LIMITE.valor) && LIMITE.valor > 0) {
      return LIMITE.valor;
    }
    return map.fallbackMax;
  }

  function parseBoolish(v) {
    if (v === true) return true;
    if (v === false || v === null || v === undefined) return false;
    var s = String(v).trim().toLowerCase();
    return s === '1' || s === 'true' || s === 'on' || s === 'yes' || s === 'ligado';
  }

  function valueToT(map, v, curMax) {
    if (map.kind === 'linear') {
      if (map.max === map.min) return 0;
      return clamp01((v - map.min) / (map.max - map.min));
    }
    if (map.kind === 'dualRange') {
      var top = curMax || map.low;
      if (top === map.min) return 0;
      return clamp01((v - map.min) / (top - map.min));
    }
    if (map.kind === 'signLimit') {
      var topo = curMax || map.fallbackMax;
      if (topo === map.min) return 0;
      return clamp01((v - map.min) / (topo - map.min));
    }
    if (map.kind === 'piecewise') {
      var p = map.points;
      if (v <= p[0].v) return p[0].t;
      for (var i = 1; i < p.length; i++) {
        if (v <= p[i].v) {
          var f = (v - p[i - 1].v) / (p[i].v - p[i - 1].v);
          return clamp01(p[i - 1].t + f * (p[i].t - p[i - 1].t));
        }
      }
      return p[p.length - 1].t;
    }
    return 0;
  }

  function fmtNumber(n, decimals, unit) {
    var d = typeof decimals === 'number' ? decimals : (Math.abs(n) >= 100 ? 0 : 1);
    var s = n.toFixed(d);
    if (d > 0 && s.indexOf('.') >= 0) s = s.replace('.', ',');
    if (!unit) return s;
    // Grau e por cento colam no numero; qualquer outra unidade leva espaco. "24 °" em cima do
    // painel e exatamente o tipo de detalhe que denuncia um tema gerado.
    var cola = unit.charAt(0) === '\u00b0' || unit.charAt(0) === '%';
    return cola ? s + unit : s + ' ' + unit;
  }

  function makeMapper(el) {
    var map = el.map || { kind: 'text' };
    var b = el.binding || {};
    var state = {
      curMax: map.kind === 'dualRange' ? map.low : map.kind === 'signLimit' ? limiteAtual(map) : 0,
      limite: map.kind === 'signLimit' ? limiteAtual(map) : 0,
      scaleChanged: false,
    };

    return function (raw) {
      state.scaleChanged = false;
      if (map.kind === 'text') {
        var t = raw === null || raw === undefined ? '' : String(raw);
        return { t: 0, num: 0, text: t || (b.fallback || ''), raw: raw, on: !!t };
      }
      if (map.kind === 'boolish') {
        var on = parseBoolish(raw);
        return { t: on ? 1 : 0, num: on ? 1 : 0, text: on ? 'ON' : 'OFF', raw: raw, on: on };
      }
      if (map.kind === 'enum') {
        var key = String(raw === null || raw === undefined ? '' : raw).trim();
        var label = Object.prototype.hasOwnProperty.call(map.labels, key) ? map.labels[key] : map.fallback;
        return { t: 0, num: 0, text: label, raw: raw, on: true };
      }
      var n = parseFloat(raw);
      if (!isFinite(n)) return { t: 0, num: 0, text: b.fallback || '--', raw: raw, on: false };
      if (b.scale) n = n * b.scale;
      if (b.offset) n = n + b.offset;
      if (b.abs) n = Math.abs(n);

      if (map.kind === 'dualRange') {
        var alvo = state.curMax;
        if (n > map.upAt) alvo = map.high;
        else if (n < map.downAt) alvo = map.low;
        if (alvo !== state.curMax) { state.curMax = alvo; state.scaleChanged = true; }
      }

      if (map.kind === 'signLimit') {
        var limite = limiteAtual(map);
        // Dentro do limite, o fim do arco E o limite da via. Ao passar, abre ate o teto e so
        // fecha 2% abaixo do limite -- as duas bordas diferentes impedem a escala de piscar com o
        // carro cravado na velocidade da placa.
        var alvo2 = state.curMax || limite;
        if (n > limite * 1.02) alvo2 = Math.max(limite, map.overshootMax);
        else if (n < limite * 0.98) alvo2 = limite;
        if (alvo2 !== state.curMax) { state.curMax = alvo2; state.scaleChanged = true; }
        state.limite = limite;
      }
      return {
        t: valueToT(map, n, state.curMax),
        num: n,
        text: fmtNumber(n, b.decimals, b.unit),
        raw: raw,
        on: true,
        curMax: state.curMax,
        limite: state.limite,
        scaleChanged: state.scaleChanged,
      };
    };
  }

  // --- marcações ---------------------------------------------------------------------------
  function tickListFor(el) {
    var majors = el.ticks.majors, sub = Math.max(1, el.ticks.subdivisions), out = [];
    for (var i = 0; i < majors.length; i++) {
      out.push({ v: majors[i], level: 0 });
      if (i < majors.length - 1) {
        var passo = (majors[i + 1] - majors[i]) / sub;
        for (var j = 1; j < sub; j++) {
          out.push({ v: majors[i] + passo * j, level: j === sub / 2 ? 1 : 2 });
        }
      }
    }
    return out;
  }

  function buildTicks(inst, svg, el, g) {
    var lista = tickListFor(el);
    var grupo = svgEl('g', {});
    var labelGrupo = svgEl('g', {});
    for (var i = 0; i < lista.length; i++) {
      var it = lista[i];
      var len = it.level === 0 ? el.ticks.len.major : it.level === 1 ? el.ticks.len.minor : el.ticks.len.micro;
      var w = it.level === 0 ? el.ticks.width.major : it.level === 1 ? el.ticks.width.minor : el.ticks.width.micro;
      var cor = zoneTokenFor(el, it.v) || (it.level === 0 ? el.tickColor : el.tickColorDim);
      var line = svgEl('line', { class: 'tick', stroke: cor, 'stroke-width': w, 'stroke-linecap': 'round' });
      grupo.appendChild(line);
      var label = null;
      if (it.level === 0 && el.parts.labels) {
        label = svgEl('text', { class: 'tick-label' });
        svgText(label, el.ticks.labelStyle);
        label.setAttribute('fill', zoneTokenFor(el, it.v) || el.ticks.labelStyle.color);
        label.textContent = String(Math.round(it.v));
        labelGrupo.appendChild(label);
      }
      inst.ticks.push({
        v: it.v, level: it.level, len: len, line: line, label: label,
        corBase: cor,
        corLabelBase: label ? label.getAttribute('fill') : null,
        acima: false,
      });
    }
    svg.appendChild(grupo);
    svg.appendChild(labelGrupo);
    repositionTicks(
      inst, el, g,
      el.map.kind === 'dualRange' ? el.map.low : el.map.kind === 'signLimit' ? limiteAtual(el.map) : 0,
    );
  }

  function repositionTicks(inst, el, g, curMax) {
    var span = g.endDeg - g.startDeg;
    // Acima do limite da via, a marcação vira alerta com brilho. 'null' quando o mostrador não
    // segue placa nenhuma -- aí nada é "acima do limite" e nada muda de cor.
    var limite = el.map.kind === 'signLimit' ? limiteAtual(el.map) : null;
    for (var i = 0; i < inst.ticks.length; i++) {
      var t = inst.ticks[i];
      var visivel = true;
      var tt;
      if (el.map.kind === 'dualRange') {
        visivel = t.v <= curMax + 1e-6;
        tt = clamp01((t.v - el.map.min) / ((curMax || el.map.low) - el.map.min));
      } else if (el.map.kind === 'signLimit') {
        var topo = curMax || el.map.fallbackMax;
        visivel = t.v <= topo + 1e-6;
        tt = clamp01((t.v - el.map.min) / (topo - el.map.min));
      } else {
        tt = valueToT(el.map, t.v, curMax);
      }
      var acima = limite !== null && t.v > limite + 1e-6;
      if (t.acima !== acima) {
        t.acima = acima;
        t.line.setAttribute('stroke', acima ? el.overLimitColor : t.corBase);
        if (t.label) {
          t.label.setAttribute('fill', acima ? el.overLimitColor : t.corLabelBase);
          t.label.classList.toggle('over-limit', acima);
        }
      }
      var deg = g.startDeg + span * tt;
      var a = polar(g.cx, g.cy, g.tickOuterR, deg);
      var b = polar(g.cx, g.cy, g.tickOuterR - t.len, deg);
      t.line.setAttribute('x1', a.x.toFixed(2)); t.line.setAttribute('y1', a.y.toFixed(2));
      t.line.setAttribute('x2', b.x.toFixed(2)); t.line.setAttribute('y2', b.y.toFixed(2));
      t.line.setAttribute('class', visivel ? 'tick' : 'tick off');
      if (acima) t.line.classList.add('over-limit');
      if (t.label) {
        var p = polar(g.cx, g.cy, g.labelR, deg);
        t.label.setAttribute('x', p.x.toFixed(2));
        t.label.setAttribute('y', p.y.toFixed(2));
        t.label.style.opacity = visivel ? '' : '0';
      }
    }
  }

  // A marcação mais próxima do valor cresce. Trocar de classe é a única escrita por quadro aqui;
  // o resto é CSS.
  function highlightNearestTick(inst, num) {
    var melhor = -1, dist = Infinity;
    for (var i = 0; i < inst.ticks.length; i++) {
      if (inst.ticks[i].level !== 0 || !inst.ticks[i].label) continue;
      var d = Math.abs(inst.ticks[i].v - num);
      if (d < dist) { dist = d; melhor = i; }
    }
    if (melhor === inst.lastZoom) return;
    if (inst.lastZoom >= 0 && inst.ticks[inst.lastZoom].label) {
      inst.ticks[inst.lastZoom].label.classList.remove('zoomed');
    }
    if (melhor >= 0 && inst.ticks[melhor].label) inst.ticks[melhor].label.classList.add('zoomed');
    inst.lastZoom = melhor;
  }

  // O temporizador nao e redundancia: requestAnimationFrame NAO dispara enquanto o WebView esta
  // oculto (document.visibilityState === 'hidden') -- e o do cluster fica oculto sempre que uma
  // projecao ou um aviso do carro assume a tela. Sem esta rede, uma troca de escala iniciada nesse
  // instante congelava no valor inicial: ao voltar, o painel continuava com o fim de escala antigo
  // e as marcacoes na posicao errada, ate a proxima troca.
  function animateValue(from, to, ms, cb) {
    if (RM || ms <= 0) { cb(to); return; }
    var t0 = performance.now();
    var terminou = false;
    var rede = setTimeout(function () {
      if (terminou) return;
      terminou = true;
      cb(to);
    }, ms + 250);
    function passo(now) {
      if (terminou) return;
      var f = clamp01((now - t0) / ms);
      var e = f < 0.5 ? 2 * f * f : -1 + (4 - 2 * f) * f;
      cb(from + (to - from) * e);
      if (f < 1) { requestAnimationFrame(passo); return; }
      terminou = true;
      clearTimeout(rede);
    }
    requestAnimationFrame(passo);
  }

${ICONS_JS}
${RENDERERS_JS}

  // --- suavizacao de leitura ----------------------------------------------------------------
  //
  // Nem toda chave do carro chega com a mesma granularidade. A velocidade sobe de 1 em 1 km/h e o
  // ponteiro parece fluido; a porcentagem de potencia pula de 18 para 30 e fica parada, e o MESMO
  // ponteiro, no MESMO tema, parece travado. O problema nao e taxa de quadros -- e o tamanho do
  // degrau do dado.
  //
  // O filtro e exponencial de primeira ordem sobre o VALOR, antes de virar angulo e texto:
  //
  //   1. Sobre o valor, e nao no CSS: uma transicao de transform moveria o ponteiro e deixaria o
  //      numero digital saltando de 18 para 30 sozinho, que e metade do defeito.
  //   2. Exponencial, e nao animacao com inicio e fim: um valor novo no meio do caminho so muda o
  //      alvo. Nao ha animacao para cancelar, nem emenda onde a velocidade do ponteiro salta.
  //   3. Com o dt real: enquanto o WebView esta oculto o rAF para e so a rede de 120 ms corre.
  //
  // O filtro e criticamente amortecido de SEGUNDA ordem, e nao um exponencial de primeira. A
  // diferenca aparece justamente onde o defeito estava: um exponencial e mais rapido no INICIO,
  // entao logo depois de um degrau grande o ponteiro da um passo grande e vai freando. Medido no
  // mostrador de potencia deste tema, com os degraus de 5 a 12 pontos que o carro publica, o pior
  // passo de um quadro era 2,91 graus -- do tamanho do pulinho que se queria tirar. Comecando do
  // REPOUSO, o mesmo cenario cai para 1,79 graus, e o percentil 95 de 1,82 para 1,47.
  //
  // A segunda ordem tambem guarda a TAXA de um quadro para o outro. Um valor novo no meio do
  // caminho so muda o alvo: nao ha animacao para cancelar, nem o salto de velocidade que o
  // exponencial dava a cada leitura nova -- e a chave da potencia chega a cada ~400 ms.
  //
  // Custa um atraso da ordem de 2/omega sobre uma rampa. Por isso e declarado por LIGACAO e nunca
  // e padrao: num mostrador de potencia o atraso e invisivel, no NUMERO de um velocimetro seria
  // mentira.
  var SUAVES = [];

  // O valor declarado no tema continua sendo o tempo para cobrir ~95% de um degrau -- 4,744 e onde
  // (1 + wt)e^-wt cai a 5%. Manter o significado e o que dispensa re-afinar cada tema.
  function fazSuave(el) {
    if (RM) return null;
    var b = el.binding;
    if (!b || !b.key || !(b.smoothMs > 0)) return null;
    // Texto, enum e booleano nao tem meio-termo entre dois valores: nao ha o que interpolar.
    var k = el.map && el.map.kind;
    if (!k || k === 'text' || k === 'enum' || k === 'boolish') return null;
    var passo = b.smoothStep > 0 ? b.smoothStep : 0;
    return {
      chave: b.key,
      omega: 4.744 / Math.max(0.001, b.smoothMs / 1000),
      // O degrau do DESENHO, em unidades do dado. Sem ele o filtro desenha em precisao dupla e
      // reescreve o transform do ponteiro a cada quadro por um movimento que nao chega a um
      // pixel; com ele o ponteiro anda de smoothStep em smoothStep, que e como o velocimetro
      // troca "de 1 em 1 km/h" por "de 0,25 em 0,25".
      passo: passo,
      // Um vigesimo da menor casa que o texto mostra. Abaixo disso o numero nao muda e o ponteiro
      // nao anda meio grau: continuar iterando seria gastar quadro sem mover pixel. Com degrau
      // declarado quem manda e ele: meio degrau e o ponto onde o desenho ja nao muda mais.
      epsilon: passo > 0
        ? passo / 2
        : 0.05 * Math.pow(10, -(typeof b.decimals === 'number' ? b.decimals : 1)),
      atual: null,
      alvo: 0,
      // A taxa de variacao do valor, por segundo. E o estado a mais que a segunda ordem tem, e o
      // que faz o filtro sair do repouso em vez de partir a toda.
      taxa: 0,
      ultimo: 0,
      ativo: false,
    };
  }

  // O filtro chegou E parou? Estar perto do alvo nao basta: um ponteiro passando rapido tambem
  // fica perto por um quadro, e cravar ali guardaria a taxa velha para a proxima ativacao --
  // que e como uma mola ganha um empurrao vindo do nada.
  function paradoNoAlvo(s) {
    return Math.abs(s.alvo - s.atual) <= s.epsilon && Math.abs(s.taxa) <= s.omega * s.epsilon;
  }

  // O valor que vai ao desenho. Sem degrau declarado e o proprio valor do filtro -- e por isso
  // que nenhuma ligacao antiga muda de comportamento por causa desta funcao.
  function quantizar(s, v) {
    return s.passo > 0 ? Math.round(v / s.passo) * s.passo : v;
  }

  // Devolve true quando o filtro assumiu o desenho; false quando o elemento deve seguir o caminho
  // normal (primeiro valor, valor invalido, ou diferenca pequena demais para valer um caminho).
  function mirarSuave(inst, raw) {
    var s = inst.suave;
    var n = parseFloat(raw);
    if (!isFinite(n)) {
      // Leitura vazia ou invalida: o elemento mostra o proprio fallback e o filtro esquece onde
      // estava, para o proximo numero valido entrar cravado em vez de vir de um valor velho.
      s.atual = null;
      s.taxa = 0;
      s.ativo = false;
      return false;
    }
    s.alvo = n;
    if (s.atual === null) { s.atual = n; s.taxa = 0; return false; }
    if (paradoNoAlvo(s)) { s.atual = n; s.taxa = 0; return false; }
    if (!s.ativo) { s.ativo = true; s.ultimo = performance.now(); SUAVES.push(inst); }
    return true;
  }

  function passoDeSuavizacao() {
    var agora = performance.now();
    var restantes = [];
    for (var i = 0; i < SUAVES.length; i++) {
      var inst = SUAVES[i], s = inst.suave;
      var dt = agora - s.ultimo;
      s.ultimo = agora;
      if (!(dt > 0)) dt = 1;
      // Intervalo longo so acontece quando ninguem estava vendo: WebView oculto para o rAF e
      // afina o temporizador. Interpolar a partir dai mostraria, na volta, um valor velho
      // caminhando ate o atual. Cravar e o certo -- o salto acontece fora da tela.
      if (dt > 400) { s.atual = s.alvo; s.taxa = 0; }
      else {
        // Solucao exata do sistema criticamente amortecido no intervalo, com a posicao E a taxa
        // vindas do quadro anterior: e(t) = (e0 + c t) e^-wt, com c = taxa0 + w e0.
        var seg = dt / 1000;
        var erro = s.atual - s.alvo;
        var c = s.taxa + s.omega * erro;
        var amort = Math.exp(-s.omega * seg);
        s.atual = s.alvo + (erro + c * seg) * amort;
        s.taxa = (c - s.omega * erro - s.omega * c * seg) * amort;
      }
      if (paradoNoAlvo(s)) { s.atual = s.alvo; s.taxa = 0; s.ativo = false; }
      else restantes.push(inst);
      try {
        var r = inst.map(quantizar(s, s.atual));
        if (r.scaleChanged && inst.onScaleChange) inst.onScaleChange(r.curMax);
        REND[inst.el.kind].update(inst, r, s.chave);
      } catch (e) { console.error('[DEEPGWT] suavizacao ' + s.chave, e); }
    }
    SUAVES = restantes;
    // Enquanto houver filtro andando, o proximo quadro e pedido aqui: nenhuma telemetria nova
    // precisa chegar para o ponteiro terminar o caminho.
    if (SUAVES.length) scheduleRender();
  }

  // --- despacho ----------------------------------------------------------------------------
  function flush() {
    scheduled = false;
    if (fallbackTimer) { clearTimeout(fallbackTimer); fallbackTimer = null; }
    for (var key in PENDING) {
      if (!Object.prototype.hasOwnProperty.call(PENDING, key)) continue;
      var raw = PENDING[key];
      delete PENDING[key];
      var lista = BY_KEY[key];
      if (!lista) continue;
      for (var i = 0; i < lista.length; i++) {
        var inst = lista[i];
        // Durante a varredura de boas-vindas o mostrador pertence a ela. O valor nao se perde:
        // fica no BUFFER e e reaplicado quando a varredura termina.
        if (VARRENDO && inst.el.kind === 'dial') continue;
        try {
          // Chave suavizada NAO vai direto ao desenho: ela move o alvo do filtro, e quem desenha
          // e passoDeSuavizacao(). A visibilidade nao passa pelo filtro -- um elemento que so
          // aparece acima de um limiar tem de aparecer na hora em que o carro disse.
          if (inst.suave && key === inst.suave.chave && mirarSuave(inst, raw)) {
            applyVisibility(inst);
            continue;
          }
          var r = inst.map(raw);
          if (r.scaleChanged && inst.onScaleChange) inst.onScaleChange(r.curMax);
          REND[inst.el.kind].update(inst, r, key);
          applyVisibility(inst);
        } catch (e) { console.error('[DEEPGWT]', key, e); }
      }
    }
    if (SUAVES.length) passoDeSuavizacao();
    // As regras de cor so sao varridas quando alguma chave DELAS mudou. Antes a varredura
    // corria em todo flush -- ou seja, sessenta vezes por segundo so porque a velocidade mudou.
    // A varredura nao escrevia nada a toa (a escrita ja e comparada em aplicarRegrasDeCor), mas
    // montava um objeto novo e reavaliava condicaoVale() de cada regra a cada quadro, e isso e
    // CPU e lixo de memoria num laco de 60 Hz.
    //
    // Para isto ser correto, dispatch() precisa marcar PENDING_REGRAS para QUALQUER chave de
    // regra -- inclusive as que tambem alimentam um elemento. Antes so marcava as que NAO
    // alimentavam nenhum; as outras funcionavam por acidente, porque a varredura era incondicional.
    if (PENDING_REGRAS) { PENDING_REGRAS = false; aplicarRegrasDeCor(); }
    if (PENDING_PAGINAS) { PENDING_PAGINAS = false; avaliarPaginasAutomaticas(); }
  }

  // requestAnimationFrame é a batida certa, mas uma janela sem foco pode afinar a cadência a
  // ponto de o painel parecer travado. O temporizador de 120 ms é a rede embaixo.
  function scheduleRender() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(flush);
    fallbackTimer = setTimeout(flush, 120);
  }

  function dispatch(key, raw) {
    BUFFER[key] = raw;
    // Antes do desvio por BY_KEY: uma chave pode alimentar um elemento E uma regra de cor.
    if (REGRA_KEYS[key]) PENDING_REGRAS = true;
    if (PAGINA_KEYS[key]) PENDING_PAGINAS = true;
    if (key === 'car.basic.head_light_status') setHeadlights(parseBoolish(raw));
    if (key === 'warningActive') { warningActiveState = parseBoolish(raw); applyYieldToCar(); }

    // O limite da via nao pertence a elemento nenhum: mudou o limite, TODO mostrador que segue
    // placa precisa reescalonar, mesmo que a velocidade nao tenha mudado um km/h.
    if (key === LIMIT_KEY) {
      var n = parseFloat(raw);
      LIMITE.valor = isFinite(n) ? n : null;
      reagirAoLimite();
    } else if (key === LIMIT_STATUS_KEY) {
      LIMITE.valido = parseBoolish(raw);
      reagirAoLimite();
    }

    if (!BY_KEY[key]) {
      // Uma chave sem elemento ainda pode disparar uma regra de cor ou abrir uma pagina.
      if (REGRA_KEYS[key] || PAGINA_KEYS[key]) scheduleRender();
      return;
    }
    PENDING[key] = raw;
    scheduleRender();
  }

  function reagirAoLimite() {
    for (var i = 0; i < SIGN_LIMIT_INSTS.length; i++) {
      var inst = SIGN_LIMIT_INSTS[i];
      var lim = limiteAtual(inst.el.map);
      if (inst.onScaleChange) inst.onScaleChange(lim);
      else repositionTicks(inst, inst.el, inst.el.geom, lim);
    }
    PENDING_REGRAS = true;
    scheduleRender();
  }

  function replayBuffer() {
    for (var k in BUFFER) {
      if (Object.prototype.hasOwnProperty.call(BUFFER, k) && BY_KEY[k]) {
        PENDING[k] = BUFFER[k];
      }
    }
    // Repor o buffer inteiro e repor tambem o estado das regras: a condicao de qualquer uma
    // delas pode ter mudado enquanto este painel estava fora da tela.
    PENDING_REGRAS = true;
    PENDING_PAGINAS = true;
    if (Object.keys(PENDING).length) flush(); else { aplicarRegrasDeCor(); avaliarPaginasAutomaticas(); }
  }

  /**
   * Uma condicao do veiculo vale agora?
   *
   * Le do BUFFER e nao do valor que acabou de chegar: a mesma funcao serve a visibilidade de um
   * elemento (disparada pela chave dele) e as regras de cor (que podem depender de duas chaves ao
   * mesmo tempo, como velocidade contra limite da via).
   */
  function condicaoVale(v) {
    if (!v || v.when === 'always') return true;
    if (v.when === 'overLimit') {
      var vel = parseFloat(BUFFER['car.basic.vehicle_speed']);
      var lim = LIMITE.valido && LIMITE.valor !== null ? LIMITE.valor : NaN;
      return isFinite(vel) && isFinite(lim) && lim > 0 && vel > lim + (v.marginKmh || 0);
    }
    var raw = BUFFER[v.key];
    if (v.when === 'nonEmpty') return String(raw == null ? '' : raw).trim() !== '';
    if (v.when === 'equals') return String(raw) === v.value;
    if (v.when === 'flag') return parseBoolish(raw) === !!v.on;
    if (v.when === 'threshold') {
      var n = parseFloat(raw);
      return isFinite(n) && (
        v.op === 'gt' ? n > v.value : v.op === 'lt' ? n < v.value :
        v.op === 'gte' ? n >= v.value : n <= v.value);
    }
    return true;
  }

  function applyVisibility(inst) {
    var v = inst.el.visible;
    if (!v || v.when === 'always') return;
    inst.host.classList.toggle('hidden-rule', !condicaoVale(v));
  }

  // Regras de cor: trocam tokens da paleta enquanto uma condicao vale.
  //
  // Escritas como propriedade inline no <body>. O bloco de tokens do tema vive em ':root' e em
  // 'body.mode-*'; um estilo inline no proprio <body> vence os dois, entao a regra funciona em
  // qualquer modo sem precisar de um quarto bloco de cores.
  var REGRAS_ATIVAS = {};
  function aplicarRegrasDeCor() {
    var regras = (THEME && THEME.colorRules) || [];
    var querido = {};
    for (var i = 0; i < regras.length; i++) {
      var r = regras[i];
      if (!r.enabled || !condicaoVale(r.when)) continue;
      // A ULTIMA regra ativa vence: a ordem da lista e a prioridade, e ela e editavel.
      for (var t = 0; t < r.tokens.length; t++) querido['--t-' + r.tokens[t]] = r.color;
    }
    for (var prop in REGRAS_ATIVAS) {
      if (!Object.prototype.hasOwnProperty.call(REGRAS_ATIVAS, prop)) continue;
      if (!Object.prototype.hasOwnProperty.call(querido, prop)) document.body.style.removeProperty(prop);
    }
    for (var prop2 in querido) {
      if (!Object.prototype.hasOwnProperty.call(querido, prop2)) continue;
      if (REGRAS_ATIVAS[prop2] !== querido[prop2]) document.body.style.setProperty(prop2, querido[prop2]);
    }
    REGRAS_ATIVAS = querido;
  }

  // Paginas que abrem sozinhas ('showWhen' da pagina).
  //
  // A troca acontece na BORDA da condicao, nunca por nivel: quando a condicao passa a valer, o
  // painel vai para a pagina e guarda de onde veio; quando deixa de valer, volta. Enquanto ela vale,
  // o volante continua trocando de pagina -- reavaliar por nivel prenderia o motorista na pagina
  // da re ate engatar outra marcha.
  var PAGINA_KEYS = {};
  var PENDING_PAGINAS = false;
  var PAGINAS_AUTO = [];
  var PAGINA_DE_VOLTA = -1;
  function avaliarPaginasAutomaticas() {
    for (var i = 0; i < PAGINAS_AUTO.length; i++) {
      var a = PAGINAS_AUTO[i];
      var vale = condicaoVale(a.when);
      if (vale === a.valia) continue;
      a.valia = vale;
      if (vale) {
        if (PAGINA_DE_VOLTA < 0) PAGINA_DE_VOLTA = ACTIVE_PAGE;
        showPage(a.indice);
      } else if (ACTIVE_PAGE === a.indice && PAGINA_DE_VOLTA >= 0) {
        var alguma = false;
        for (var j = 0; j < PAGINAS_AUTO.length; j++) if (PAGINAS_AUTO[j].valia) { alguma = true; showPage(PAGINAS_AUTO[j].indice); break; }
        if (!alguma) { showPage(PAGINA_DE_VOLTA); PAGINA_DE_VOLTA = -1; }
      }
    }
  }

  // --- sair da frente do carro -------------------------------------------------------------
  function applyYieldToCar() {
    var ceder = warningActiveState || PROJ.carPlayInDash || PROJ.projectionMirrorInDash || PROJ.projectionPreparingD3;
    document.body.classList.toggle('yield-to-car', ceder);
  }

  // --- paleta ------------------------------------------------------------------------------
  function applyMode() {
    // Escolha manual de "Noite" vence o âmbar automático do farol. Quem pediu vermelho não quer
    // âmbar só porque acendeu o farol.
    MODE = manualNight ? 'night' : headlightsOn ? 'headlight' : 'day';
    document.body.className = document.body.className
      .replace(/\\bmode-(day|night|headlight)\\b/g, '').trim() + ' mode-' + MODE;
    applyYieldToCar();
  }
  function setHeadlights(on) { if (on !== headlightsOn) { headlightsOn = on; applyMode(); } }
  function setManualNight(on) { if (on !== manualNight) { manualNight = on; applyMode(); } }

  function pollDisplayMode() {
    try {
      if (window.Android && typeof window.Android.getPreference === 'function') {
        var v = window.Android.getPreference('displayMode', 'Dia');
        setManualNight(String(v).toLowerCase().indexOf('noite') === 0);
        pollEscala();
      }
    } catch (e) {}
  }

  // --- escala do velocimetro escolhida DENTRO do carro -------------------------------------
  //
  // O tema ja nasce com uma escala definida no editor. Esta preferencia deixa o motorista trocar
  // no proprio carro, sem reenviar o tema -- inclusive para "seguir a placa", que e uma decisao
  // de quem esta dirigindo e nao de quem desenhou o painel.
  //
  // Guardamos a escala ORIGINAL de cada mostrador em mapOriginal: "Automatica" precisa saber
  // voltar para o que o tema pediu, e nao para um padrao inventado aqui.
  var escalaEscolhida = '';
  function pollEscala() {
    if (!THEME || !THEME.speedScaleConfig) return;
    var v = String(window.Android.getPreference('speedScale', 'Automática') || '').toLowerCase();
    var modo = v.indexOf('fixa') === 0 ? 'fixa' : v.indexOf('placa') >= 0 || v.indexOf('seguir') === 0 ? 'placa' : 'auto';
    if (modo === escalaEscolhida) return;
    escalaEscolhida = modo;
    aplicarEscala(modo);
  }

  function aplicarEscala(modo) {
    for (var i = 0; i < DIAIS_DE_ESCALA.length; i++) {
      var inst = DIAIS_DE_ESCALA[i];
      var orig = inst.mapOriginal;
      var novo = orig;
      if (modo === 'fixa') {
        novo = { kind: 'linear', min: tetoMin(orig), max: tetoMax(orig) };
      } else if (modo === 'placa') {
        novo = {
          kind: 'signLimit', min: tetoMin(orig),
          fallbackMax: orig.kind === 'dualRange' ? orig.low : tetoMax(orig),
          overshootMax: tetoMax(orig), rescaleMs: 650,
          limitKey: LIMIT_KEY, statusKey: LIMIT_STATUS_KEY,
        };
      }
      inst.el.map = novo;
      inst.map = makeMapper(inst.el);
      inst.curMax = novo.kind === 'dualRange' ? novo.low : novo.kind === 'signLimit' ? limiteAtual(novo) : tetoMax(novo);
      var jaTem = SIGN_LIMIT_INSTS.indexOf(inst) >= 0;
      if (novo.kind === 'signLimit' && !jaTem) SIGN_LIMIT_INSTS.push(inst);
      if (novo.kind !== 'signLimit' && jaTem) SIGN_LIMIT_INSTS.splice(SIGN_LIMIT_INSTS.indexOf(inst), 1);
      repositionTicks(inst, inst.el, inst.el.geom, inst.curMax);
      // Reaplica o ultimo valor conhecido: sem isto o ponteiro ficaria no angulo da escala
      // ANTIGA ate a proxima atualizacao do carro.
      var ultimo = BUFFER[inst.el.binding && inst.el.binding.key];
      if (ultimo !== undefined) dispatch(inst.el.binding.key, ultimo);
    }
  }

  function tetoMin(m) { return m.kind === 'linear' ? m.min : m.min; }
  function tetoMax(m) {
    if (m.kind === 'linear') return m.max;
    if (m.kind === 'dualRange') return m.high;
    if (m.kind === 'signLimit') return m.overshootMax;
    return 100;
  }

  // --- páginas -----------------------------------------------------------------------------
  function showPage(i) {
    if (!PAGE_NODES.length) return;
    ACTIVE_PAGE = (i + PAGE_NODES.length) % PAGE_NODES.length;
    for (var p = 0; p < PAGE_NODES.length; p++) {
      PAGE_NODES[p].classList.toggle('active', p === ACTIVE_PAGE);
    }
    var dots = document.querySelectorAll('.pager i');
    for (var d = 0; d < dots.length; d++) dots[d].classList.toggle('on', d === ACTIVE_PAGE);
  }

  // --- bridge ------------------------------------------------------------------------------
  function initBridge(P) {
    if (!window.Android || typeof window.Android.subscribe !== 'function') installMockBridge(P);

    // Encadear, nunca sobrescrever: o host injeta uma camada de compatibilidade antes do tema
    // carregar, e apagar o handler dela quebra temas legados no mesmo WebView.
    var prevData = window.onDataChanged;
    window.onDataChanged = function (k, v) {
      if (typeof prevData === 'function') { try { prevData(k, v); } catch (e) { console.error(e); } }
      dispatch(k, v);
    };

    // Obrigatório no contrato v1.0 — e aqui ele NÃO esconde o tema. O card 0 é o card de abertura;
    // esconder nele deixa o tema invisível até o motorista mexer no volante.
    window.onCardChanged = function (id) { CARD = Number(id) || 0; };

    // Sem este stub, o host lança ReferenceError a cada evento, e cada erro vira um salto JNI mais
    // uma escrita em disco no log persistente. Além disso, é o ÚNICO caminho de entrega das seis
    // chaves de projeção e do booleano de verdade de warningActive.
    var prevCtl = window.control;
    window.control = function (k, v) {
      if (typeof prevCtl === 'function') { try { prevCtl(k, v); } catch (e) {} }
      if (Object.prototype.hasOwnProperty.call(PROJ, k)) {
        PROJ[k] = (v === true || v === 'true');
        applyYieldToCar();
        return;
      }
      if (k === 'warningActive') { warningActiveState = (v === true || v === 'true'); applyYieldToCar(); return; }
      dispatch(k, v);
    };

    if (typeof window.updateWarning !== 'function') window.updateWarning = function () {};

    window.onKeyEvent = function (nome) {
      if (PAGE_NODES.length < 2) return;
      if (nome === 'UP') showPage(ACTIVE_PAGE - 1);
      else if (nome === 'DOWN') showPage(ACTIVE_PAGE + 1);
    };

    try {
      if (window.Android && typeof window.Android.subscribe === 'function') {
        window.Android.subscribe(JSON.stringify(P.subscribeKeys));
      }
      if (window.Android && typeof window.Android.setAppDefaultDimensions === 'function') {
        var a = P.appDefault;
        window.Android.setAppDefaultDimensions(a.x, a.y, a.width, a.height);
      }
    } catch (e) { console.error('[DEEPGWT] bridge:', e); }

    pollDisplayMode();
    setInterval(function () {
      pollDisplayMode();
      // Terceira garantia da abertura: este intervalo tambem fica estrangulado enquanto a pagina
      // esta oculta, mas volta a correr no primeiro quadro visivel -- e ai o prazo por relogio de
      // parede ja venceu e a cortina sai. Custa uma comparacao de numeros a cada 2 s.
      conferirPrazoDaAbertura();
      try { if (window.Android && typeof window.Android.heartbeat === 'function') window.Android.heartbeat(); } catch (e) {}
    }, 2000);
  }

  // Sem o carro, o preview do editor precisa se comportar igual. Os valores são simulados e o
  // aviso no console diz isso — nada aqui finge ser leitura de veículo.
  //
  // O editor desliga este sorteio assim que a pagina carrega (DEEPGWT.preview.simulacao(false)) e
  // passa a mandar os valores do cenario escolhido. O sorteio continua sendo o comportamento de
  // um app.html aberto sozinho num navegador.
  var MOCK_TIMER = null;
  var PREFS_DO_PREVIEW = {};
  function installMockBridge(P) {
    console.warn('[DEEPGWT] window.Android ausente: simulação local para preview.');
    window.Android = {
      getAvailableKeys: function () { return JSON.stringify(P.subscribeKeys); },
      subscribe: function () {}, unsubscribe: function () {},
      getCarData: function () { return ''; }, updateCarData: function () {},
      getPreference: function (k, d) {
        return Object.prototype.hasOwnProperty.call(PREFS_DO_PREVIEW, k) ? PREFS_DO_PREVIEW[k] : d;
      },
      savePreference: function () {},
      setAppDefaultDimensions: function () {}, heartbeat: function () {},
    };
    var estado = {}, dir = 1, tick = 0;
    var faixas = P.mockRanges || {};
    for (var k in faixas) if (Object.prototype.hasOwnProperty.call(faixas, k)) estado[k] = faixas[k].min;

    MOCK_TIMER = setInterval(function () {
      tick++;
      for (var key in faixas) {
        if (!Object.prototype.hasOwnProperty.call(faixas, key)) continue;
        var f = faixas[key];
        if (f.kind === 'boolish') {
          if (tick % 25 === 0) { estado[key] = estado[key] ? 0 : 1; dispatch(key, String(estado[key])); }
          continue;
        }
        if (f.kind === 'text') {
          if (tick === 1) dispatch(key, f.sample || '');
          continue;
        }
        if (f.kind === 'enum') {
          if (tick % 30 === 0) dispatch(key, f.samples[(tick / 30) % f.samples.length]);
          else if (tick === 1) dispatch(key, f.samples[0]);
          continue;
        }
        var passo = (f.max - f.min) * 0.015;
        estado[key] += dir * passo * (0.5 + Math.random());
        if (estado[key] >= f.max) { estado[key] = f.max; dir = -1; }
        if (estado[key] <= f.min) { estado[key] = f.min; dir = 1; }
        dispatch(key, String(Math.round(estado[key] * 10) / 10));
      }
    }, 320);
  }

  // --- montagem ----------------------------------------------------------------------------
  function buildElement(el, pageNode) {
    var host = document.createElement('div');
    // Prefixo obrigatorio. Sem ele o host ganharia a classe 'media' / 'readout' / 'indicator', as
    // MESMAS que o renderizador usa no no de dentro: o host virava contêiner flex e o card de
    // midia encolhia para o tamanho do texto em vez de ocupar a caixa.
    host.className = 'el k-' + el.kind;
    host.id = 'el-' + el.id;
    host.style.left = el.rect.x + 'px';
    host.style.top = el.rect.y + 'px';
    host.style.width = el.rect.w + 'px';
    host.style.height = el.rect.h + 'px';
    host.style.zIndex = String(el.z);
    if (el.opacity !== 1) host.style.opacity = String(el.opacity);
    if (el.zoom) {
      host.style.setProperty('--zoom-scale', String(el.zoom.scale));
      host.style.setProperty('--zoom-weight', String(el.zoom.weight));
      host.style.setProperty('--zoom-dur', el.zoom.durationMs + 'ms');
      host.style.setProperty('--zoom-ease', el.zoom.easing);
    }
    pageNode.appendChild(host);

    var inst = REND[el.kind].build(el, host);
    inst.el = el;
    inst.host = host;
    inst.map = makeMapper(el);
    inst.suave = fazSuave(el);
    if (el.kind === 'dial') DIAIS.push(inst);

    if (el.kind === 'dial' && (el.map.kind === 'linear' || el.map.kind === 'dualRange' || el.map.kind === 'signLimit')) {
      inst.mapOriginal = el.map;
      DIAIS_DE_ESCALA.push(inst);
    }

    if (el.kind === 'dial' && el.map.kind === 'signLimit') {
      SIGN_LIMIT_INSTS.push(inst);
      inst.curMax = limiteAtual(el.map);
      inst.onScaleChange = (function (i, e) {
        return function (novo) {
          var de = i.curMax || e.map.fallbackMax;
          i.curMax = novo;
          animateValue(de, novo, RM ? 0 : e.map.rescaleMs, function (v) {
            repositionTicks(i, e, e.geom, v);
          });
        };
      })(inst, el);
    }

    if (el.kind === 'dial' && el.map.kind === 'dualRange') {
      inst.onScaleChange = (function (i, e) {
        return function (novo) {
          var de = i.curMax || e.map.low;
          i.curMax = novo;
          animateValue(de, novo, RM ? 0 : e.map.rescaleMs, function (v) {
            repositionTicks(i, e, e.geom, v);
          });
        };
      })(inst, el);
    }

    var chaves = [];
    if (el.binding && el.binding.key) chaves.push(el.binding.key);
    if (el.extraKeys) chaves = chaves.concat(el.extraKeys);
    if (el.visible && el.visible.key) chaves.push(el.visible.key);
    // Um elemento pode citar a mesma chave duas vezes -- por exemplo um selo de marcha que LE a
    // marcha e so APARECE quando ela e P. Sem esta desduplicacao o elemento seria redesenhado
    // duas vezes por atualizacao dessa chave.
    for (var i = 0; i < chaves.length; i++) {
      var lista = (BY_KEY[chaves[i]] = BY_KEY[chaves[i]] || []);
      if (lista.indexOf(inst) < 0) lista.push(inst);
    }
    return inst;
  }

  // --- varredura de boas-vindas ------------------------------------------------------------
  //
  // Uma vez por carga: os ponteiros vao do minimo ao maximo e voltam, e so entao o valor do carro
  // assume. Comeca quando a cortina de abertura sai (ou na montagem, se nao ha cortina), para nao
  // acontecer escondida atras dela.
  var DIAIS = [];
  var VARRENDO = false;
  var VARREDURA_FEITA = false;
  var MONTADO = false;
  function iniciarVarredura() {
    if (VARREDURA_FEITA || !MONTADO) return;
    VARREDURA_FEITA = true;
    if (!THEME || !THEME.welcomeSweep || RM || !DIAIS.length) return;
    VARRENDO = true;
    var desenhar = function (t) {
      for (var i = 0; i < DIAIS.length; i++) {
        try { REND.dial.update(DIAIS[i], { t: t, num: 0, text: '', sweep: true }); } catch (e) {}
      }
    };
    animateValue(0, 1, 650, function (v) {
      desenhar(v);
      if (v < 1) return;
      animateValue(1, 0, 650, function (w) {
        desenhar(w);
        if (w > 0) return;
        VARRENDO = false;
        replayBuffer();
      });
    });
  }

  var RELOGIOS = [];
  function tickClocks() {
    var d = new Date();
    var hh = ('0' + d.getHours()).slice(-2), mm = ('0' + d.getMinutes()).slice(-2);
    for (var i = 0; i < RELOGIOS.length; i++) RELOGIOS[i].textContent = hh + ':' + mm;
  }

  /**
   * A CORTINA DE ABERTURA NAO PODE DEPENDER DE UM TIMER SO.
   *
   * '.boot' e uma caixa preta opaca cobrindo o painel inteiro em z-index 9999, e a unica coisa que
   * a tirava era um 'setTimeout(sweepMs)'. So que o painel do cluster passa boa parte do tempo
   * OCULTO: toda projecao (CarPlay/Android Auto), todo aviso do carro e toda carta nativa colocam a
   * WebView em INVISIBLE, e o Chromium estrangula setTimeout e requestAnimationFrame de um
   * documento escondido -- comportamento correto dele, nao defeito. Se a pagina carregar (ou for
   * recarregada pelo watchdog) enquanto esta oculta, aquele unico timer nao corre no tempo
   * esperado, e quando o painel volta a aparecer a cortina ainda esta la. Quem olha ve so o fundo.
   *
   * Tres garantias, nesta ordem, e nenhuma delas custa nada no caminho feliz:
   *
   *   1. o 'setTimeout' de sempre, que continua sendo quem encerra a abertura normal;
   *   2. 'visibilitychange': no instante em que a pagina VOLTA a ser exibida, a cortina sai. Nao
   *      depende de timer nenhum para acontecer -- e a mesma rede que os temas escritos a mao
   *      (Cupertino Ultra, Stuttgart NTG 5.5) ja tinham e o motor do editor nao;
   *   3. prazo por RELOGIO DE PAREDE, conferido no mesmo 'setInterval' de 2 s que ja manda o
   *      heartbeat: tempo escondido conta, entao um documento que ficou oculto alem do sweep
   *      reabre sem cortina em vez de esperar um timer que ja perdeu a hora.
   *
   * 'encerrarAbertura' e idempotente: quem chegar primeiro encerra, os outros nao fazem nada.
   */
  var FIM_DA_ABERTURA = 0;
  var ABERTURA_ENCERRADA = false;
  function encerrarAbertura() {
    if (ABERTURA_ENCERRADA) return;
    var no = document.getElementById('boot');
    if (!no) { ABERTURA_ENCERRADA = true; return; }
    ABERTURA_ENCERRADA = true;
    no.className = no.className.indexOf('done') >= 0 ? no.className : no.className + ' done';
    iniciarVarredura();
  }
  function conferirPrazoDaAbertura() {
    if (ABERTURA_ENCERRADA) return;
    if (FIM_DA_ABERTURA && Date.now() >= FIM_DA_ABERTURA) encerrarAbertura();
  }
  function agendarFimDaAbertura(P) {
    var no = document.getElementById('boot');
    if (!no) { ABERTURA_ENCERRADA = true; return; }
    if (!P.boot || !P.boot.enabled || RM) { encerrarAbertura(); return; }
    var sweep = P.boot.sweepMs;
    FIM_DA_ABERTURA = Date.now() + sweep;
    setTimeout(encerrarAbertura, sweep);
    document.addEventListener('visibilitychange', function () {
      if (document.visibilityState === 'visible') encerrarAbertura();
    });
  }

  function boot(P) {
    THEME = P;
    // Armada ANTES de qualquer outra coisa, de proposito: se 'initBridge' ou a montagem do DOM
    // estourar, a cortina ainda assim sai sozinha. Presa, ela esconde o tema inteiro e nao ha
    // nada na tela dizendo o que aconteceu; sem ela, um tema que falhou ao menos aparece pela
    // metade e o console diz onde. O custo do adiantamento e sub-milissegundo.
    agendarFimDaAbertura(P);
    // O bridge primeiro: qualquer valor que chegue antes do DOM existir cai no BUFFER e é
    // reaplicado logo depois. Nada se perde entre assinar e montar.
    //
    // Em try/catch porque ele fala com 'window.Android', que e uma ponte JNI: um throw ali
    // abortava 'boot()' inteiro e a pagina ficava VAZIA por baixo da cortina -- exatamente o
    // "so o fundo aparece" visto no carro, mas por um caminho que nenhum timer resolve.
    try {
      initBridge(P);
    } catch (erro) {
      console.error('[DEEPGWT] initBridge:', erro);
    }

    var app = document.getElementById('app');
    PAGES = P.pages;
    for (var p = 0; p < P.pages.length; p++) {
      var node = document.createElement('div');
      node.className = 'page' + (p === 0 ? ' active' : '');
      node.setAttribute('data-page', P.pages[p].id);
      app.appendChild(node);
      PAGE_NODES.push(node);
      if (P.pages[p].bg) {
        var fundoDaPagina = document.createElement('div');
        fundoDaPagina.className = 'page-bg';
        fundoDaPagina.style.backgroundImage = 'url("' + P.pages[p].bg + '")';
        node.appendChild(fundoDaPagina);
      }
      var sw = P.pages[p].showWhen;
      if (sw && sw.when && sw.when !== 'always') {
        PAGINAS_AUTO.push({ indice: p, when: sw, valia: false });
        if (sw.key) PAGINA_KEYS[sw.key] = true;
        if (sw.when === 'overLimit') { PAGINA_KEYS['car.basic.vehicle_speed'] = true; PAGINA_KEYS[LIMIT_KEY] = true; }
      }
      for (var e = 0; e < P.pages[p].elements.length; e++) {
        var el = P.pages[p].elements[e];
        // Um elemento por vez, cada um no seu try: sem isto, um unico renderizador que estourasse
        // (um icone que o WebView do carro nao aceita, um SVG que ele recusa) abortava o laco e
        // TODO o resto da pagina deixava de existir. O tema aparecia pela metade e nada no carro
        // dizia por que. Agora o que falha e um elemento, e o console diz qual.
        try {
          var inst = buildElement(el, node);
          if (el.kind === 'readout' && el.style === 'clock' && inst.value) RELOGIOS.push(inst.value);
        } catch (erro) {
          console.error('[DEEPGWT] elemento ' + el.id + ' (' + el.kind + '/' + el.style + '):', erro);
        }
      }
    }

    if (P.pages.length > 1) {
      var pager = document.createElement('div');
      pager.className = 'pager';
      for (var d = 0; d < P.pages.length; d++) {
        var dot = document.createElement('i');
        if (d === 0) dot.className = 'on';
        (function (idx, node2) { node2.addEventListener('click', function () { showPage(idx); }); })(d, dot);
        pager.appendChild(dot);
      }
      app.appendChild(pager);
    }

    if (RELOGIOS.length) { tickClocks(); setInterval(tickClocks, 15000); }
    // Um temporizador so para todos os graficos, e so quando existe algum.
    if (GRAFICOS.length) setInterval(amostrarGraficos, 1000);

    var regras = P.colorRules || [];
    for (var ri = 0; ri < regras.length; ri++) {
      var quando = regras[ri].when;
      if (quando && quando.key) REGRA_KEYS[quando.key] = true;
      if (quando && quando.when === 'overLimit') {
        REGRA_KEYS['car.basic.vehicle_speed'] = true;
        REGRA_KEYS[LIMIT_KEY] = true;
      }
    }

    replayBuffer();
    aplicarRegrasDeCor();
    applyMode();

    MONTADO = true;
    if (ABERTURA_ENCERRADA) iniciarVarredura();
  }

  window.DEEPGWT = {
    boot: boot,
    // Usado so pelo editor do instalador, com a pagina num iframe e o mock no lugar do carro. No
    // painel, window.Android e real, o mock nunca e instalado e ninguem chama nada disto.
    preview: {
      simulacao: function (ligada) {
        if (!ligada && MOCK_TIMER) { clearInterval(MOCK_TIMER); MOCK_TIMER = null; }
      },
      // Mesmo caminho da telemetria de verdade: dispatch, coalescido por quadro.
      valores: function (mapa) {
        for (var k in mapa) if (Object.prototype.hasOwnProperty.call(mapa, k)) dispatch(k, mapa[k]);
      },
      // Noite e escolha do motorista (preferencia do tema); farol e telemetria. O editor manda os
      // dois pelos mesmos caminhos que o carro usa.
      modo: function (m) {
        PREFS_DO_PREVIEW.displayMode = m === 'night' ? 'Noite' : 'Dia';
        setManualNight(m === 'night');
      },
      pagina: function (i) { showPage(i); },
    },
    // Usado pelo editor para gerar a miniatura de verdade a partir da geometria do próprio motor.
    // Um SVG COMPLETO e autocontido da pagina ativa, para virar a miniatura do tema.
    //
    // Autocontido de verdade: as variaveis de cor do tema sao lidas resolvidas e coladas no
    // proprio <svg>, senao cada 'var(--t-ink)' viraria preto ao abrir o SVG fora da pagina. Os
    // elementos de texto sao remontados como <text>, porque HTML nao atravessa a fronteira de
    // um <img src="data:image/svg+xml">.
    snapshotSvg: function () {
      var cs = getComputedStyle(document.documentElement);
      var nomes = (THEME && THEME.tokens) || [];
      var vars = [];
      for (var i = 0; i < nomes.length; i++) {
        var v = cs.getPropertyValue('--t-' + nomes[i]);
        if (v) vars.push('--t-' + nomes[i] + ':' + v.trim() + ';');
      }
      var fundo = getComputedStyle(document.body).backgroundColor || '#000';
      var partes = [];
      partes.push('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1920 720" width="1920" height="720">');
      partes.push('<style>svg{' + vars.join('') + '}</style>');
      partes.push('<rect x="0" y="0" width="1920" height="720" fill="' + fundo + '"/>');

      var pagina = document.querySelector('.page.active');
      if (!pagina) return partes.join('') + '</svg>';
      var base = pagina.getBoundingClientRect();
      var fp = pagina.querySelector('.page-bg');
      if (fp) {
        var mf = (getComputedStyle(fp).backgroundImage || '').match(/^url\\("?(data:[^")]+)"?\\)$/);
        if (mf) partes.push('<image x="0" y="0" width="1920" height="720" href="' + mf[1] + '" preserveAspectRatio="xMidYMid slice"/>');
      }
      var hosts = pagina.querySelectorAll('.el');
      for (var h = 0; h < hosts.length; h++) {
        var host = hosts[h];
        if (host.classList.contains('hidden-rule')) continue;
        var r = host.getBoundingClientRect();
        // Caixas pintadas: trilho e preenchimento de barra, espaco reservado da capa, painel de
        // decoracao. Sem esta passagem o SVG so trazia texto e desenho de mostrador, e um tema
        // feito de barras -- como o Minimalist -- gerava uma miniatura praticamente em branco.
        // Fundo em gradiente fica de fora de proposito: 'fill' de SVG nao aceita gradiente CSS, e
        // inventar uma cor media mentiria sobre o desenho.
        var caixas = host.querySelectorAll('*');
        for (var c = 0; c < caixas.length; c++) {
          var cn = caixas[c];
          if (cn.ownerSVGElement || cn.tagName === 'svg') continue;
          var cr = cn.getBoundingClientRect();
          if (cr.width < 1 || cr.height < 1) continue;
          var ccs = getComputedStyle(cn);
          if (parseFloat(ccs.opacity) === 0) continue;
          var raio = ccs.borderRadius || '0px';
          var rx = raio.indexOf('%') >= 0 ? Math.min(cr.width, cr.height) / 2 : parseFloat(raio) || 0;
          rx = Math.min(rx, Math.min(cr.width, cr.height) / 2);
          var caixa = '<rect x="' + (cr.left - base.left) + '" y="' + (cr.top - base.top) + '"' +
            ' width="' + cr.width + '" height="' + cr.height + '"' + (rx ? ' rx="' + rx + '"' : '');
          var opac = parseFloat(ccs.opacity) < 1 ? ' opacity="' + ccs.opacity + '"' : '';
          var bg = ccs.backgroundColor;
          if (bg && bg !== 'transparent' && bg.indexOf('rgba(0, 0, 0, 0)') !== 0) {
            partes.push(caixa + ' fill="' + bg + '"' + opac + '/>');
          }
          // Imagem de elemento e fundo de pagina: vem como url("data:...") e entra como <image>.
          // Um data: dentro de um SVG que vira <img src="data:..."> e aceito pelo navegador.
          var bi = ccs.backgroundImage || '';
          var mi = bi.match(/^url\\("?(data:[^")]+)"?\\)$/);
          if (mi) {
            partes.push(
              '<image x="' + (cr.left - base.left) + '" y="' + (cr.top - base.top) + '"' +
              ' width="' + cr.width + '" height="' + cr.height + '" href="' + mi[1] + '"' +
              ' preserveAspectRatio="xMidYMid ' + (ccs.backgroundSize === 'cover' ? 'slice' : 'meet') + '"' + opac + '/>');
          }
          // Contorno de forma (moldura). So a borda de cima e lida: as quatro sao iguais aqui.
          var bw = parseFloat(ccs.borderTopWidth);
          if (bw > 0 && ccs.borderTopStyle !== 'none') {
            partes.push(caixa + ' fill="none" stroke="' + ccs.borderTopColor + '" stroke-width="' + bw + '"' + opac + '/>');
          }
        }

        // Desenhos em SVG: o mostrador redondo inteiro, e tambem os icones de uma leitura ou de um
        // indicador. Cada um e posicionado pela PROPRIA caixa e escalado do viewBox para ela.
        //
        // A versao anterior tratava o primeiro <svg> como se fosse o elemento todo e pulava o
        // resto do host. Numa leitura com icone isso apagava o numero e escrevia o icone no canto
        // errado: a miniatura do Cupertino saia sem "858 km" e sem o farolete, e a de um tema so
        // de barras saia praticamente vazia.
        var svgs = host.querySelectorAll('svg');
        for (var q = 0; q < svgs.length; q++) {
          var sv = svgs[q];
          if (sv.ownerSVGElement) continue;
          var sr = sv.getBoundingClientRect();
          if (sr.width < 1 || sr.height < 1) continue;
          var vbp = (sv.getAttribute('viewBox') || '').split(',').join(' ').split(' ');
          var nums = [];
          for (var w = 0; w < vbp.length; w++) if (vbp[w] !== '') nums.push(parseFloat(vbp[w]));
          var vw = nums.length > 3 && nums[2] ? nums[2] : sr.width;
          var vh = nums.length > 3 && nums[3] ? nums[3] : sr.height;
          // As duas coisas que o <g> precisa herdar do <svg> de origem, porque so o innerHTML e
          // copiado: a cor corrente, para que currentColor resolva fora da pagina, e os atributos
          // pintura escritos na raiz. Os icones declaram fill="none" stroke="currentColor" ali;
          // sem copiar, cada tracado virava uma mancha preta preenchida. O <svg> do mostrador nao
          // declara nenhum dos dois, entao para ele nada muda.
          var atrs = ' color="' + getComputedStyle(sv).color + '"';
          var HERDA = ['fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'stroke-opacity', 'fill-opacity'];
          for (var a = 0; a < HERDA.length; a++) {
            var va = sv.getAttribute(HERDA[a]);
            if (va !== null) atrs += ' ' + HERDA[a] + '="' + va + '"';
          }
          partes.push(
            '<g transform="translate(' + (sr.left - base.left) + ',' + (sr.top - base.top) + ')' +
            ' scale(' + (sr.width / vw) + ',' + (sr.height / vh) + ')"' + atrs + '>');
          partes.push(sv.innerHTML);
          partes.push('</g>');
        }

        var textos = host.querySelectorAll('div, span');
        for (var t = 0; t < textos.length; t++) {
          var n = textos[t];
          if (n.children.length || !n.textContent) continue;
          var tr = n.getBoundingClientRect();
          if (!tr.width || !tr.height) continue;
          var ts = getComputedStyle(n);
          // text-transform e letter-spacing sao CSS e nao atravessam a fronteira do <img>: sem
          // aplicar aqui, "NORMAL" virava "Normal" e os rotulos espacados fechavam na miniatura.
          var conteudo = String(n.textContent);
          if (ts.textTransform === 'uppercase') conteudo = conteudo.toUpperCase();
          else if (ts.textTransform === 'lowercase') conteudo = conteudo.toLowerCase();
          var esp = parseFloat(ts.letterSpacing);
          partes.push(
            '<text x="' + (tr.left - base.left + tr.width / 2) + '" y="' + (tr.top - base.top + tr.height / 2) + '"' +
            ' text-anchor="middle" dominant-baseline="central"' +
            ' font-family="' + ts.fontFamily.replace(/"/g, '&apos;') + '"' +
            ' font-size="' + parseFloat(ts.fontSize) + '"' +
            ' font-weight="' + ts.fontWeight + '"' +
            (isFinite(esp) && esp ? ' letter-spacing="' + esp + '"' : '') +
            ' fill="' + ts.color + '">' +
            conteudo.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;') +
            '</text>');
        }
      }
      partes.push('</svg>');
      return partes.join('');
    },
  };
})()`
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
