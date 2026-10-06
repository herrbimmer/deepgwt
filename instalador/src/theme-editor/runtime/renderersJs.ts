// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A tabela de desenho do motor: um par `build'/'update' por tipo de elemento.
//
// O que cada estilo desenha NÃO está espalhado em ramos aqui. O emissor traduz o estilo em um
// objeto 'parts' ('{face, ticks, labels, needle, arc, segments, index}') e o motor desenha o que
// 'parts' pedir. Adicionar um estilo novo passa a ser uma linha no emissor em vez de um ramo
// novo no runtime — é o que mantém os bytes do motor iguais em todo tema.
//
// Este arquivo é texto JavaScript, colado dentro do IIFE do motor. Sem crases e sem interpolação:
// o que estiver aqui vai literal para o 'app.html'.

export const RENDERERS_JS = `
  function applyText(node, st) {
    if (!st) return;
    node.style.fontFamily = st.ff;
    node.style.fontSize = st.fs;
    node.style.fontWeight = st.fw;
    if (st.ls) node.style.letterSpacing = st.ls + 'px';
    if (st.tt) node.style.textTransform = st.tt;
    if (st.tn) node.style.fontVariantNumeric = 'tabular-nums';
    node.style.color = st.color;
  }

  function svgText(node, st) {
    if (!st) return;
    node.setAttribute('font-family', st.ff);
    node.setAttribute('font-size', st.fsRaw);
    node.setAttribute('font-weight', st.fw);
    if (st.ls) node.setAttribute('letter-spacing', st.ls);
    node.setAttribute('fill', st.color);
    node.setAttribute('text-anchor', 'middle');
    node.setAttribute('dominant-baseline', 'central');
  }

  // Uma tinta vira ou uma cor CSS, ou um <defs> com gradiente e a referência url(#id).
  function paintSvg(defs, paint, id, geom) {
    if (!paint || paint.fill === 'none') return 'none';
    if (paint.fill === 'solid') return paint.color;
    if (paint.fill === 'linear') {
      var lg = svgEl('linearGradient', { id: id, gradientTransform: 'rotate(' + (paint.angleDeg || 0) + ' .5 .5)' });
      lg.appendChild(svgEl('stop', { offset: '0%', 'stop-color': paint.from }));
      lg.appendChild(svgEl('stop', { offset: '100%', 'stop-color': paint.to }));
      defs.appendChild(lg);
      return 'url(#' + id + ')';
    }
    if (paint.fill === 'radial') {
      var rg = svgEl('radialGradient', {
        id: id, cx: paint.cx, cy: paint.cy, r: 0.72,
      });
      rg.appendChild(svgEl('stop', { offset: '0%', 'stop-color': paint.from }));
      rg.appendChild(svgEl('stop', { offset: (Math.round((paint.stopAt || 0.78) * 100)) + '%', 'stop-color': paint.to }));
      defs.appendChild(rg);
      return 'url(#' + id + ')';
    }
    if (paint.fill === 'hubRadial') {
      // userSpaceOnUse centrado no cubo: como o centro do gradiente É o eixo de rotação, girar o
      // ponteiro não muda um pixel dele. Trocar por um radial comum faz o gradiente escorrer.
      var hg = svgEl('radialGradient', {
        id: id, gradientUnits: 'userSpaceOnUse',
        cx: geom.cx, cy: geom.cy, r: geom.R,
      });
      hg.appendChild(svgEl('stop', { offset: '0%', 'stop-color': paint.inner }));
      hg.appendChild(svgEl('stop', { offset: (Math.round((paint.midAt || 0.58) * 100)) + '%', 'stop-color': paint.mid }));
      hg.appendChild(svgEl('stop', { offset: '100%', 'stop-color': paint.outer }));
      defs.appendChild(hg);
      return 'url(#' + id + ')';
    }
    return 'none';
  }

  function paintCss(paint) {
    if (!paint || paint.fill === 'none') return 'transparent';
    if (paint.fill === 'solid') return paint.color;
    if (paint.fill === 'linear') return 'linear-gradient(' + (paint.angleDeg || 135) + 'deg, ' + paint.from + ', ' + paint.to + ')';
    if (paint.fill === 'radial') return 'radial-gradient(circle at ' + Math.round(paint.cx * 100) + '% ' + Math.round(paint.cy * 100) + '%, ' + paint.from + ' 0%, ' + paint.to + ' ' + Math.round((paint.stopAt || 0.78) * 100) + '%)';
    return 'transparent';
  }

  function zoneTokenFor(el, value) {
    var z = el.ticks && el.ticks.zones;
    if (!z) return null;
    for (var i = 0; i < z.length; i++) {
      if (value >= z[i].from && value <= z[i].to) return z[i].color;
    }
    return null;
  }

  // --- mostrador redondo -------------------------------------------------------------------
  var DIAL = {
    build: function (el, host) {
      var g = el.geom;
      var svg = svgEl('svg', { viewBox: '0 0 ' + el.rect.w + ' ' + el.rect.h });
      var defs = svgEl('defs', {});
      svg.appendChild(defs);

      var inst = { el: el, svg: svg, ticks: [], labels: [], lastZoom: -1, lastDeg: null, lastArc: null, segs: [] };

      if (el.parts.face && el.face.enabled) {
        var faceR = g.R - (el.face.bezel ? el.face.bezel.width : 0);

        // Vidro. Vai no host e ANTES do <svg> (que so e anexado no fim desta funcao), entao fica
        // atras de tudo que o mostrador desenha -- e por isso o que ele desfoca e o papel de
        // parede, nao o proprio ponteiro. Um <div>, e nao um <circle>: 'backdrop-filter' e
        // propriedade de caixa CSS e o Chromium a ignora, sem erro, num filho de SVG.
        if (el.face.blurPx > 0) {
          var vidro = document.createElement('div');
          vidro.className = 'dial-glass';
          vidro.style.left = (g.cx - faceR) + 'px';
          vidro.style.top = (g.cy - faceR) + 'px';
          vidro.style.width = (faceR * 2) + 'px';
          vidro.style.height = (faceR * 2) + 'px';
          vidro.style.setProperty('--glass-blur', el.face.blurPx + 'px');
          host.appendChild(vidro);
          inst.vidro = vidro;
        }

        var face = svgEl('circle', { class: 'dial-face', cx: g.cx, cy: g.cy, r: faceR });
        face.setAttribute('fill', paintSvg(defs, el.face.paint, el.id + '-face', g));
        svg.appendChild(face);
        if (el.face.ring) {
          svg.appendChild(svgEl('circle', {
            class: 'dial-ring', cx: g.cx, cy: g.cy, r: faceR + el.face.ring.width / 2,
            fill: 'none', stroke: el.face.ring.color, 'stroke-width': el.face.ring.width,
          }));
        }
      }
      if (el.face.bezel) {
        svg.appendChild(svgEl('circle', {
          class: 'dial-bezel', cx: g.cx, cy: g.cy, r: g.R - el.face.bezel.width / 2,
          fill: 'none', stroke: el.face.bezel.color, 'stroke-width': el.face.bezel.width,
        }));
      }

      if (el.parts.segments) {
        var n = el.parts.segments;
        var span = g.endDeg - g.startDeg;
        for (var s = 0; s < n; s++) {
          var a0 = g.startDeg + (span * s) / n + 1;
          var a1 = g.startDeg + (span * (s + 1)) / n - 1;
          var p = svgEl('path', {
            class: 'seg off',
            d: wedgePath(g.cx, g.cy, g.tickOuterR - 26, g.tickOuterR, a0, a1),
            fill: el.segColor,
          });
          svg.appendChild(p);
          inst.segs.push(p);
        }
      }

      if (el.parts.ticks) buildTicks(inst, svg, el, g);

      if (el.parts.arc) {
        // Trilho do anel: o arco inteiro, desenhado uma vez so e nunca mais tocado. Fica antes do
        // arco de valor para o valor pintar por cima dele.
        if (el.arcTrack) {
          svg.appendChild(svgEl('path', {
            class: 'arc-track', fill: 'none', stroke: el.arcTrack, 'stroke-width': el.arc.width,
            'stroke-linecap': 'round',
            d: describeArc(g.cx, g.cy, g.tickOuterR + el.arc.radiusOffset, g.startDeg, g.endDeg),
          }));
        }
        // fill='none' como ATRIBUTO e nao so pela regra .arc do CSS: a miniatura copia o SVG
        // sem folha de estilo, e sem o atributo o arco virava uma fatia preta preenchida por
        // cima do disco.
        inst.arc = svgEl('path', { class: 'arc', d: '', fill: 'none', stroke: el.arcColor, 'stroke-width': el.arc.width });
        svg.appendChild(inst.arc);
        if (el.arc.split) {
          inst.arc2 = svgEl('path', { class: 'arc', d: '', fill: 'none', stroke: el.arcColorAfter, 'stroke-width': el.arc.width });
          svg.appendChild(inst.arc2);
        }
      }

      if (el.parts.index) {
        inst.index = svgEl('g', {});
        inst.index.appendChild(svgEl('circle', { cx: g.cx, cy: g.cy - g.tickOuterR, r: Math.max(4, el.needle.width), fill: el.needleColor }));
        svg.appendChild(inst.index);
      }

      if (el.parts.needle) {
        // A classe 'needle-rot' só existe para o CSS poder promover este grupo a camada
        // própria. O ponteiro pode carregar sombra (el.needle.glow) e, sem a promoção, o
        // brilho é rasterizado de novo a cada grau girado.
        var ng = svgEl('g', { class: 'needle-rot' });
        var line = svgEl('line', { class: 'needle', 'stroke-width': el.needle.width, 'stroke-linecap': 'round' });
        line.setAttribute('stroke', paintSvg(defs, el.needle.paint, el.id + '-needle', g));
        if (el.needle.glow) line.style.filter = 'drop-shadow(0 0 8px ' + el.needleGlow + ')';
        pinNeedle(line, g.cx, g.cy, g.needleInnerR, g.needleOuterR, g.startDeg);
        ng.appendChild(line);
        svg.appendChild(ng);
        inst.needleG = ng;

        if (el.needle.cap === 'ring' || el.needle.cap === 'ringDot') {
          svg.appendChild(svgEl('circle', { class: 'needle-cap-ring', cx: g.cx, cy: g.cy, r: 11, fill: 'none', stroke: el.hubRingColor, 'stroke-width': 2 }));
        }
        if (el.needle.cap === 'dot' || el.needle.cap === 'ringDot') {
          svg.appendChild(svgEl('circle', { class: 'needle-cap', cx: g.cx, cy: g.cy, r: 7, fill: el.needleColor }));
        }
      }

      host.appendChild(svg);

      if (el.center) {
        var c = document.createElement('div');
        c.className = 'dial-center';
        if (el.center.offsetPx) c.style.transform = 'translateY(' + el.center.offsetPx + 'px)';
        var v = document.createElement('div');
        v.className = 'v';
        v.textContent = el.binding && el.binding.fallback ? el.binding.fallback : '--';
        applyText(v, el.center.text);
        c.appendChild(v);
        if (el.center.label) {
          var l = document.createElement('div');
          l.className = 'l';
          l.textContent = el.center.label;
          applyText(l, el.center.labelStyle);
          c.appendChild(l);
        }
        host.appendChild(c);
        inst.value = v;
      }
      return inst;
    },

    update: function (inst, r) {
      var el = inst.el, g = el.geom;
      var deg = g.startDeg + (g.endDeg - g.startDeg) * r.t;

      if (inst.needleG && deg !== inst.lastDeg) {
        rotateNeedle(inst.needleG, deg, g.cx, g.cy, g.startDeg);
        inst.lastDeg = deg;
      }
      if (inst.index) rotateNeedle(inst.index, deg, g.cx, g.cy, 0);

      if (inst.arc) {
        var q = Math.max(0.5, el.arc.quantizeDeg);
        var qd = Math.round(deg / q) * q;
        if (qd !== inst.lastArc) {
          inst.lastArc = qd;
          if (el.arc.split) {
            var splitDeg = g.startDeg + (g.endDeg - g.startDeg) * el.arc.splitT;
            inst.arc.setAttribute('d', describeArc(g.cx, g.cy, g.tickOuterR + el.arc.radiusOffset, g.startDeg, Math.min(qd, splitDeg)));
            inst.arc2.setAttribute('d', qd > splitDeg ? describeArc(g.cx, g.cy, g.tickOuterR + el.arc.radiusOffset, splitDeg, qd) : '');
          } else {
            inst.arc.setAttribute('d', describeArc(g.cx, g.cy, g.tickOuterR + el.arc.radiusOffset, g.startDeg, qd));
          }
        }
      }

      if (inst.segs.length) {
        var lit = Math.round(r.t * inst.segs.length);
        for (var i = 0; i < inst.segs.length; i++) {
          var on = i < lit;
          if (inst.segs[i].__on !== on) {
            inst.segs[i].__on = on;
            inst.segs[i].setAttribute('class', on ? 'seg' : 'seg off');
          }
        }
      }

      // A varredura de boas-vindas so move o desenho: o numero continua o do carro.
      if (r.sweep) return;
      if (inst.value) inst.value.textContent = r.text;
      if (el.zoom && el.zoom.enabled) highlightNearestTick(inst, r.num);
    },
  };

  // --- barra -------------------------------------------------------------------------------
  var BAR = {
    build: function (el, host) {
      var wrap = document.createElement('div');
      wrap.className = 'bar' + (el.style === 'centreOut' ? ' centre' : '') + (el.orientation === 'v' ? ' vertical' : '');
      var track = document.createElement('div');
      track.className = 'bar-track';
      track.style.background = paintCss(el.track);
      var inst = { el: el, segs: [] };

      if (el.style === 'segments') {
        var box = document.createElement('div');
        box.className = 'bar-segments';
        for (var i = 0; i < el.segments; i++) {
          var s = document.createElement('i');
          s.style.background = paintCss(el.fill);
          s.style.opacity = '.16';
          box.appendChild(s);
          inst.segs.push(s);
        }
        track.appendChild(box);
      } else {
        var fill = document.createElement('div');
        fill.className = 'bar-fill';
        fill.style.background = paintCss(el.fill);
        track.appendChild(fill);
        inst.fill = fill;
      }
      wrap.appendChild(track);

      if (el.readout) {
        var row = document.createElement('div');
        row.className = 'bar-row';
        var v = document.createElement('span');
        v.textContent = '--';
        applyText(v, el.readout.text);
        var l = document.createElement('span');
        l.className = 'l';
        l.textContent = el.readout.label;
        applyText(l, el.readout.labelStyle);
        row.appendChild(l);
        row.appendChild(v);
        wrap.appendChild(row);
        inst.value = v;
      }
      host.appendChild(wrap);
      return inst;
    },
    update: function (inst, r) {
      var el = inst.el;
      if (inst.fill) {
        if (el.style === 'centreOut') {
          var d = (r.t - 0.5) * 2;
          inst.fill.style.width = (Math.abs(d) * 50).toFixed(1) + '%';
          inst.fill.style.transform = d < 0 ? 'scaleX(-1)' : 'none';
        } else if (el.orientation === 'v') {
          inst.fill.style.height = (r.t * 100).toFixed(1) + '%';
        } else {
          inst.fill.style.width = (r.t * 100).toFixed(1) + '%';
        }
      }
      if (inst.segs.length) {
        var lit = Math.round(r.t * inst.segs.length);
        for (var i = 0; i < inst.segs.length; i++) inst.segs[i].style.opacity = i < lit ? '1' : '.16';
      }
      if (inst.value) inst.value.textContent = r.text;
    },
  };

  // --- leitura -----------------------------------------------------------------------------
  // A fila P R N D. A marcha atual acende e cresce; as outras ficam apagadas. A troca e so de
  // classe -- cor, opacidade e escala sao transicao de CSS, que o compositor faz sozinho.
  var MARCHAS = ['P', 'R', 'N', 'D'];

  var READOUT = {
    build: function (el, host) {
      var wrap = document.createElement('div');
      wrap.className = 'readout ' + el.style;
      var inst = { el: el };
      if (el.style === 'gear') {
        inst.letras = {};
        for (var gi = 0; gi < MARCHAS.length; gi++) {
          var sp = document.createElement('span');
          sp.textContent = MARCHAS[gi];
          applyText(sp, el.text);
          sp.style.color = el.labelStyle.color;
          wrap.appendChild(sp);
          inst.letras[MARCHAS[gi]] = sp;
        }
        host.appendChild(wrap);
        return inst;
      }
      if (el.icon) {
        var ic = document.createElement('div');
        ic.style.width = Math.round(el.rect.h * 0.55) + 'px';
        ic.style.flex = '0 0 auto';
        ic.innerHTML = ICONS[el.icon] || '';
        ic.style.color = el.iconColor;
        wrap.appendChild(ic);
      }
      var col = document.createElement('div');
      var v = document.createElement('div');
      v.className = 'v';
      v.textContent = el.binding && el.binding.fallback ? el.binding.fallback : '--';
      applyText(v, el.text);
      col.appendChild(v);
      if (el.label) {
        var l = document.createElement('div');
        l.className = 'l';
        l.textContent = el.label;
        applyText(l, el.labelStyle);
        col.appendChild(l);
      }
      wrap.appendChild(col);
      host.appendChild(wrap);
      inst.value = v;
      return inst;
    },
    update: function (inst, r) {
      if (inst.letras) {
        var atual = String(r.text || '').trim().toUpperCase();
        if (atual === inst.acesa) return;
        if (inst.acesa && inst.letras[inst.acesa]) {
          inst.letras[inst.acesa].className = '';
          inst.letras[inst.acesa].style.color = inst.el.labelStyle.color;
        }
        if (inst.letras[atual]) {
          inst.letras[atual].className = 'on';
          inst.letras[atual].style.color = inst.el.text.color;
        }
        inst.acesa = atual;
        return;
      }
      if (inst.value) inst.value.textContent = r.text;
    },
  };

  // --- indicador ---------------------------------------------------------------------------
  var INDICATOR = {
    build: function (el, host) {
      var wrap = document.createElement('div');
      wrap.className = 'indicator ' + el.style;
      var inst = { el: el, node: wrap };
      if (el.style === 'icon') {
        wrap.innerHTML = ICONS[el.icon] || ICONS.ready;
      } else {
        var t = document.createElement('span');
        t.textContent = '--';
        applyText(t, el.text);
        wrap.appendChild(t);
        inst.value = t;
      }
      wrap.style.color = el.offColor;
      host.appendChild(wrap);
      return inst;
    },
    update: function (inst, r) {
      var el = inst.el;
      if (inst.value) {
        inst.value.textContent = r.text;
        inst.node.style.color = el.onColor;
        return;
      }
      var on = r.on;
      inst.node.style.color = on ? el.onColor : el.offColor;
      inst.node.style.opacity = on ? '1' : '.35';
    },
  };

  // --- mídia -------------------------------------------------------------------------------
  var MEDIA = {
    build: function (el, host) {
      var wrap = document.createElement('div');
      wrap.className = 'media ' + el.style + ' empty';
      wrap.style.background = paintCss(el.panel);
      var inst = { el: el, node: wrap, title: '', artist: '' };
      if (el.showArtwork) {
        // Lado da capa EM PIXEL, medido aqui e nao por 'aspect-ratio' no CSS: a propriedade e
        // Chrome 88 e o WebView do painel so esta provado ate 84, onde a declaracao inteira e
        // descartada e a capa fica com altura zero -- some da tela sem erro nenhum.
        //
        // Na linha o lado sai da ALTURA da caixa, e nao de um numero fixo: 54% de 104 da 56 px,
        // que e a capa ficar um pouco mais alta que o bloco titulo + artista ao lado dela. Os 46 px
        // fixos de antes eram menores que o proprio texto -- um selo, nao uma capa.
        var lado = el.style === 'line'
          ? Math.round(el.rect.h * 0.54)
          : Math.round(el.rect.w * 0.34);
        var img = document.createElement('img');
        img.className = 'media-art';
        img.alt = '';
        img.style.width = lado + 'px';
        img.style.height = lado + 'px';
        wrap.appendChild(img);
        // Um <img> sem src desenha o icone de imagem quebrada do WebView. Enquanto nao chega capa
        // nenhuma quem aparece e este espaco reservado.
        var ph = document.createElement('div');
        ph.className = 'media-art-ph';
        ph.style.width = lado + 'px';
        ph.style.height = lado + 'px';
        ph.innerHTML = ICONS.music || '';
        wrap.appendChild(ph);
        inst.art = img;
      }
      var box = document.createElement('div');
      box.className = 'media-txt';
      var t = document.createElement('div');
      applyText(t, el.title);
      var a = document.createElement('div');
      applyText(a, el.artist);
      box.appendChild(t);
      box.appendChild(a);
      wrap.appendChild(box);
      host.appendChild(wrap);
      inst.t = t;
      inst.a = a;
      return inst;
    },
    update: function (inst, r, key) {
      if (key === 'app.media.title') inst.title = r.text === '--' ? '' : r.text;
      if (key === 'app.media.artist') inst.artist = r.text === '--' ? '' : r.text;
      if (key === 'app.media.album_art' && inst.art) {
        var src = r.raw || '';
        inst.art.src = src;
        inst.art.className = src ? 'media-art has-src' : 'media-art';
      }
      inst.t.textContent = inst.title;
      inst.a.textContent = inst.artist;
      inst.node.className = 'media ' + inst.el.style + (inst.title ? '' : ' empty');
    },
  };

  // --- decoração ---------------------------------------------------------------------------
  var DECOR = {
    build: function (el, host) {
      var d = document.createElement('div');
      d.className = 'decor ' + el.style;
      d.style.position = 'absolute';
      // Quatro propriedades e nao 'inset': 'inset' e Chrome 87, e num WebView abaixo disso a
      // atribuicao e um no-op silencioso -- o painel/brilho fica 0x0 e DESAPARECE. O Stuttgart
      // NTG 5.5 tem dois elementos de decoracao, e eram os dois que sumiam.
      d.style.left = '0';
      d.style.top = '0';
      d.style.right = '0';
      d.style.bottom = '0';
      d.style.background = paintCss(el.paint);
      d.style.setProperty('--decor-radius', el.radius + 'px');
      if (el.blur) d.style.filter = 'blur(' + el.blur + 'px)';
      host.appendChild(d);
      return { el: el };
    },
    update: function () {},
  };

  // --- imagem ------------------------------------------------------------------------------
  // Fundo de <div> e nao <img>: 'object-fit' e o mesmo 'contain/cover' que 'background-size' ja
  // faz, e um <img> sem src desenha o icone de imagem quebrada do WebView.
  var IMAGE = {
    build: function (el, host) {
      var d = document.createElement('div');
      d.className = 'img';
      d.style.backgroundImage = 'url("' + el.src + '")';
      d.style.backgroundSize = el.fit === 'cover' ? 'cover' : 'contain';
      if (el.radius) d.style.borderRadius = el.radius + 'px';
      host.appendChild(d);
      return { el: el };
    },
    update: function () {},
  };

  // --- forma -------------------------------------------------------------------------------
  var SHAPE = {
    build: function (el, host) {
      var d = document.createElement('div');
      d.className = 'shape ' + el.shape;
      d.style.background = paintCss(el.paint);
      if (el.stroke) d.style.border = el.stroke.width + 'px solid ' + el.stroke.color;
      if (el.shape === 'rect' && el.radius) d.style.borderRadius = el.radius + 'px';
      host.appendChild(d);
      return { el: el };
    },
    update: function () {},
  };

  // --- mini-historico ----------------------------------------------------------------------
  //
  // O valor que chega so fica guardado; quem desenha e amostrarGraficos(), uma vez por segundo,
  // para todos os graficos do tema. Um desenho por telemetria seria dezenas de reescritas de
  // 'points' por segundo por uma linha que o motorista le de relance.
  var CHART = {
    build: function (el, host) {
      var wrap = document.createElement('div');
      wrap.className = 'chart';
      var inst = { el: el, pontos: [], ultimo: null, passos: 0 };
      if (el.showValue || el.label) {
        var row = document.createElement('div');
        row.className = 'chart-row';
        var l = document.createElement('span');
        l.className = 'l';
        l.textContent = el.label || '';
        applyText(l, el.labelStyle);
        row.appendChild(l);
        if (el.showValue) {
          var v = document.createElement('span');
          v.textContent = el.binding && el.binding.fallback ? el.binding.fallback : '--';
          applyText(v, el.text);
          row.appendChild(v);
          inst.value = v;
        }
        wrap.appendChild(row);
      }
      var alto = el.showValue || el.label ? Math.round(el.rect.h * 0.62) : el.rect.h;
      var svg = svgEl('svg', { viewBox: '0 0 ' + el.rect.w + ' ' + alto, preserveAspectRatio: 'none' });
      svg.style.height = alto + 'px';
      if (el.area) {
        inst.area = svgEl('path', { d: '', fill: el.lineColor, 'fill-opacity': '0.16', stroke: 'none' });
        svg.appendChild(inst.area);
      }
      inst.linha = svgEl('polyline', {
        points: '', fill: 'none', stroke: el.lineColor, 'stroke-width': 3,
        'stroke-linejoin': 'round', 'stroke-linecap': 'round', 'vector-effect': 'non-scaling-stroke',
      });
      svg.appendChild(inst.linha);
      wrap.appendChild(svg);
      host.appendChild(wrap);
      inst.alto = alto;
      GRAFICOS.push(inst);
      return inst;
    },
    update: function (inst, r) {
      if (r.on) inst.ultimo = r.t;
      if (inst.value) inst.value.textContent = r.text;
    },
  };

  var GRAFICOS = [];
  function desenharGrafico(inst) {
    var n = inst.el.points, w = inst.el.rect.w, h = inst.alto, p = inst.pontos;
    if (!p.length) return;
    var passo = n > 1 ? w / (n - 1) : w;
    var x0 = w - (p.length - 1) * passo;
    var txt = '';
    for (var i = 0; i < p.length; i++) {
      txt += (x0 + i * passo).toFixed(1) + ',' + (h - 2 - p[i] * (h - 4)).toFixed(1) + ' ';
    }
    inst.linha.setAttribute('points', txt);
    if (inst.area) {
      inst.area.setAttribute('d', 'M' + x0.toFixed(1) + ',' + h + ' L' + txt.replace(/ $/, '').split(' ').join(' L') + ' L' + w + ',' + h + ' Z');
    }
  }
  function amostrarGraficos() {
    for (var i = 0; i < GRAFICOS.length; i++) {
      var inst = GRAFICOS[i];
      if (inst.ultimo === null) continue;
      inst.passos++;
      if (inst.passos < inst.el.stepSec) continue;
      inst.passos = 0;
      inst.pontos.push(inst.ultimo);
      if (inst.pontos.length > inst.el.points) inst.pontos.shift();
      desenharGrafico(inst);
    }
  }

  var REND = {
    dial: DIAL, barLinear: BAR, readout: READOUT,
    indicator: INDICATOR, media: MEDIA, decor: DECOR,
    image: IMAGE, shape: SHAPE, chart: CHART,
  };
`
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
