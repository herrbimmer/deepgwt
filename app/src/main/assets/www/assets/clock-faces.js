// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
(function (root) {
  'use strict';

  // One complete 224 x 124 face. Native must display a rasterization of this
  // SVG, never an independently drawn approximation of these coordinates.
  var names = { panorama: 'Orbit', meridian: 'Chronograph', split: 'Monogram', 'date-spine': 'Dashboard' };
  function escape(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' }[c];
    });
  }
  function color(value, fallback) { return /^#[0-9a-f]{6}$/i.test(value || '') ? value : fallback; }
  function render(face, snap, options) {
    options = options || {};
    face = names[face] ? face : 'panorama';
    var light = options.light === true;
    var ink = light ? '#152829' : '#f4f3ed';
    var muted = light ? '#4e6365' : '#aebdc2';
    var accent = color(options.accent, light ? '#087c70' : '#8cebc9');
    var ground = light ? '#e9f0ed' : '#10191d';
    var edge = light ? '#b8ccc8' : '#334449';
    var out = [];
    function rect(x, y, w, h, r, fill, stroke, width) {
      out.push('<rect x="' + x + '" y="' + y + '" width="' + w + '" height="' + h + '" rx="' + r + '" fill="' + fill + '"' + (stroke ? ' stroke="' + stroke + '" stroke-width="' + (width || 1) + '"' : '') + '/>');
    }
    function text(value, x, y, size, fill, weight, anchor, maxWidth, family) {
      out.push('<text x="' + x + '" y="' + y + '" fill="' + (fill || ink) + '" font-family="' + (family || 'Arial, sans-serif') + '" font-size="' + size + '" font-weight="' + (weight || 700) + '" text-anchor="' + (anchor || 'start') + '"' + (maxWidth ? ' textLength="' + maxWidth + '" lengthAdjust="spacingAndGlyphs"' : '') + '>' + escape(value) + '</text>');
    }
    // Labels keep their natural glyph proportions. textLength with
    // spacingAndGlyphs widened "MON 14 SEP" to fill its pill and read as
    // stretched type; a label that would overflow shrinks instead. Widths are
    // an Arial estimate (caps ~0.72 em, digits 0.556, space 0.278).
    function label(value, x, y, size, fill, weight, anchor, maxWidth) {
      var s = String(value == null ? '' : value);
      var em = 0;
      for (var k = 0; k < s.length; k++) {
        var ch = s.charAt(k);
        em += /[0-9]/.test(ch) ? 0.556 : (ch === ' ' ? 0.278 : (/[A-ZÀ-Ý]/.test(ch) ? 0.72 : 0.6));
      }
      var fitted = em > 0 && em * size > maxWidth ? Math.floor(maxWidth / em * 10) / 10 : size;
      text(s, x, y, fitted, fill, weight, anchor);
    }
    function line(x1, y1, x2, y2, stroke, width) {
      out.push('<line x1="' + x1 + '" y1="' + y1 + '" x2="' + x2 + '" y2="' + y2 + '" stroke="' + stroke + '" stroke-width="' + width + '" stroke-linecap="round"/>');
    }
    function circle(x, y, r, fill, stroke, width) {
      out.push('<circle cx="' + x + '" cy="' + y + '" r="' + r + '" fill="' + fill + '"' + (stroke ? ' stroke="' + stroke + '" stroke-width="' + (width || 1) + '"' : '') + '/>');
    }
    var dateLabel = [snap.weekdayShort, snap.day, snap.monthShort].join(' ').toUpperCase();
    var period = snap.dayPeriod || '';
    out.push('<svg xmlns="http://www.w3.org/2000/svg" width="448" height="248" viewBox="0 0 224 124" role="img" aria-label="' + escape(snap.accessible) + '"><defs>'
      + '<linearGradient id="base" x2="0.9" y2="1"><stop stop-color="' + (light ? '#f7faf8' : '#1c292e') + '"/><stop offset="1" stop-color="' + ground + '"/></linearGradient>'
      + '<radialGradient id="metal"><stop stop-color="' + (light ? '#c2d1cf' : '#3c494c') + '"/><stop offset="0.22" stop-color="' + ground + '"/><stop offset="0.66" stop-color="' + (light ? '#e6eeeb' : '#263237') + '"/><stop offset="1" stop-color="' + ground + '"/></radialGradient>'
      + '<radialGradient id="halo"><stop stop-color="' + accent + '" stop-opacity="0.18"/><stop offset="1" stop-color="' + accent + '" stop-opacity="0"/></radialGradient>'
      + '<clipPath id="bounds"><rect width="224" height="124" rx="16"/></clipPath></defs>'
      + '<g clip-path="url(#bounds)" font-family="Arial, sans-serif" font-variant="tabular-nums">');
    rect(0, 0, 224, 124, 16, 'url(#base)');

    if (face === 'meridian') {
      // The only hybrid: near full-height dial plus a clear digital readout.
      circle(58, 62, 54, 'url(#metal)', edge, 0.8);
      for (var ring = 18; ring < 49; ring += 1.8) circle(58, 62, ring, 'none', light ? '#b8c9c6' : '#344044', 0.25);
      var marks = options.dialMarks !== 'plain';
      for (var i = 0; i < 60; i++) {
        if (!marks && i % 15 !== 0) continue;
        var a = i * Math.PI / 30 - Math.PI / 2;
        var radius = i % 15 === 0 ? 41 : (i % 5 === 0 ? 44 : 47);
        line(58 + Math.cos(a) * radius, 62 + Math.sin(a) * radius, 58 + Math.cos(a) * 50, 62 + Math.sin(a) * 50, i % 5 === 0 ? ink : muted, i % 15 === 0 ? 2.2 : 0.8);
      }
      function hand(angle, length, width, fill) {
        var a = (angle - 90) * Math.PI / 180;
        line(58 - Math.cos(a) * 5, 62 - Math.sin(a) * 5, 58 + Math.cos(a) * length, 62 + Math.sin(a) * length, fill, width);
      }
      hand(snap.hourAngle, 28, 4.2, ink);
      hand(snap.minuteAngle, 41, 2.8, ink);
      circle(58, 62, 3.6, accent, ground, 1.5);
      text(snap.time, 119, 70, 34, ink, 700, 'start', 96);
      label(dateLabel, 119, 91, 9, muted, 600, 'start', 94);
      if (period) text(period, 215, 39, 10, accent, 700, 'end');
    } else if (face === 'panorama') {
      // The cyan perimeter is a calm seconds sweep. Its 16px radius follows the
      // native card's own 16dp corners rather than turning this wide face into
      // a pill inside a rounded card.
      out.push('<ellipse cx="112" cy="50" rx="108" ry="75" fill="url(#halo)"/>');
      rect(4, 4, 216, 116, 16, 'none', edge, 2.8);
      var hour24 = typeof options.hour24 === 'number' ? options.hour24 : Number(snap.hour);
      var dayProgress = Math.max(0, Math.min(1, (hour24 * 60 + Number(snap.minute)) / 1440));
      var secondProgress = Math.max(0, Math.min(1, Number(snap.second || 0) / 60));
      var outerLength = 2 * (216 + 116 - 4 * 16) + 2 * Math.PI * 16;
      var innerLength = 2 * (206 + 106 - 4 * 12) + 2 * Math.PI * 12;
      out.push('<rect x="4" y="4" width="216" height="116" rx="16" fill="none" stroke="' + accent + '" stroke-width="3" stroke-dasharray="' + (secondProgress * outerLength).toFixed(3) + ' ' + outerLength.toFixed(3) + '" stroke-linecap="round"/>'
        + '<rect x="9" y="9" width="206" height="106" rx="12" fill="none" stroke="' + accent + '" opacity="0.22" stroke-width="1.5" stroke-dasharray="' + (dayProgress * innerLength).toFixed(3) + ' ' + innerLength.toFixed(3) + '" stroke-linecap="round"/>');
      text(snap.time, 112, 76, 60, ink, 700, 'middle', 180);
      rect(55, 88, 114, 19, 9.5, ground, edge, 0.8);
      label(dateLabel, 112, 101, 10, muted, 600, 'middle', 94);
      if (period) text(period, 184, 99, 9, accent, 700);
    } else if (face === 'split') {
      // Bold stacked figures; quiet diagonal material facets behind them.
      out.push('<path d="M-30 124 L70 0 H108 L8 124 Z M75 124 L175 0 H200 L100 124 Z" fill="' + ink + '" opacity="0.028"/>');
      // Preserve the original heavy, extended numeral design and reduce the
      // complete block uniformly. A page-only web font would fall back when
      // the same SVG is decoded as an image for the native rail.
      // The 93.6px block is centered in the left column (center x=76).
      out.push('<g transform="translate(29.2 20) scale(0.8)">');
      text(snap.hour, 0, 48, 66, ink, 800, 'start', 117);
      text(snap.minute, 0, 103, 66, accent, 800, 'start', 117);
      out.push('</g>');
      line(142, 24, 142, 100, edge, 1);
      // Both columns share the vertical center at y=62. Metadata retains its
      // natural glyph proportions instead of stretching short labels.
      text(String(snap.weekdayShort).toUpperCase(), 181, 38, 11, muted, 600, 'middle');
      text(snap.day, 181, 77, 36, ink, 700, 'middle');
      text(String(snap.monthShort).toUpperCase(), 181, 98, 11, muted, 600, 'middle');
      if (period) text(period, 181, 114, 7, muted, 600, 'middle');
    } else {
      text(snap.time, 12, 68, 48, ink, 700, 'start', 143);
      // The weekday's cap top sits on the digits' top (68 - 48 x 0.716 = 33.6)
      // and the month pill ends on the AM baseline (78), so the date column
      // spans exactly the height of the time beside it.
      label(String(snap.weekdayShort).toUpperCase(), 186, 41, 10, muted, 600, 'middle', 47);
      rect(163, 54, 47, 24, 8, accent);
      label(String(snap.monthShort).toUpperCase(), 186.5, 70, 11, ground, 700, 'middle', 31);
      line(13, 81, 211, 81, edge, 0.8);
      // Calendar arithmetic uses the snapshot's local calendar date, not the
      // browser's timezone. Monday-first seven-day strip, including month edges.
      var p = String(snap.dateKey).split('-').map(Number);
      var today = new Date(Date.UTC(p[0], p[1] - 1, p[2]));
      var offset = (today.getUTCDay() + 6) % 7;
      for (var d = 0; d < 7; d++) {
        var cell = new Date(today.getTime() + (d - offset) * 86400000);
        var x = 15 + d * 28;
        if (d === offset) rect(x, 90, 25, 25, 8, accent);
        text(String(cell.getUTCDate()).padStart(2, '0'), x + 12.5, 107, 12, d === offset ? ground : muted, d === offset ? 700 : 500, 'middle');
      }
      if (period) text(period, 152, 78, 8, muted, 600, 'end');
    }
    out.push('</g><rect x="0.5" y="0.5" width="223" height="123" rx="15.5" fill="none" stroke="' + edge + '" stroke-width="0.8"/></svg>');
    return out.join('');
  }
  root.H6ClockFaces = { version: 1, width: 224, height: 124, names: names, render: render };
})(typeof window !== 'undefined' ? window : globalThis);
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
