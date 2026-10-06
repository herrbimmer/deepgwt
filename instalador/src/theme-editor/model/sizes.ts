// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// De onde vem o tamanho de um elemento, agora que o editor não tem mais largura e altura.
//
// A troca não é perda de poder. O gerador antigo calculava `fontSize = height * 0.34` e
// `raio = min(largura, altura) * 0.16`: altura nunca foi dimensão de layout, era proxy de tamanho
// de fonte com razão mágica embutida. Uma tabela por tipo mais um ajuste fino diz a mesma coisa
// sem mentir, mantém todo mostrador proporcional, e reduz a colisão a um conjunto finito de
// pegadas por tipo — que é o que torna o bloqueio calculável a cada quadro de arraste.

import type { Anchor, Element, ElementKind, Rect, SizeToken } from './spec'

export const SCALE_MIN = 0.6
export const SCALE_MAX = 1.4

/**
 * Pegada base por tipo e tamanho, em pixels do canvas de 1920×720.
 *
 * `dial` guarda o DIÂMETRO EXTERNO, bisel incluído. O valor de `lg` é 530 porque é exatamente o
 * diâmetro do mostrador do Cupertino (raio de bisel 265) — o preset sai da tabela sem número
 * solto, e o Stuttgart, com bisel 273, cai em `lg` com ajuste fino 1,03.
 */
const TABLE: Record<ElementKind, Record<SizeToken, { w: number; h: number }>> = {
  dial: {
    xs: { w: 240, h: 240 }, sm: { w: 340, h: 340 }, md: { w: 440, h: 440 },
    lg: { w: 530, h: 530 }, xl: { w: 620, h: 620 },
  },
  barLinear: {
    xs: { w: 180, h: 26 }, sm: { w: 260, h: 34 }, md: { w: 340, h: 42 },
    lg: { w: 460, h: 52 }, xl: { w: 600, h: 64 },
  },
  readout: {
    xs: { w: 120, h: 44 }, sm: { w: 180, h: 64 }, md: { w: 260, h: 92 },
    lg: { w: 360, h: 124 }, xl: { w: 480, h: 160 },
  },
  indicator: {
    xs: { w: 64, h: 28 }, sm: { w: 88, h: 36 }, md: { w: 120, h: 48 },
    lg: { w: 160, h: 64 }, xl: { w: 210, h: 84 },
  },
  // Aumentado ~28% em 2026-09-21 a pedido: o que estava escrito na faixa "tocando agora" não se
  // lia de relance a 70 cm do painel, que é a distância de leitura real do cluster. A caixa cresce
  // junto com o texto (ver `defaults.ts`) porque só aumentar a fonte dentro da caixa antiga faria
  // o título cortar em reticências mais cedo, trocando um problema por outro.
  media: {
    xs: { w: 360, h: 104 }, sm: { w: 480, h: 140 }, md: { w: 600, h: 190 },
    lg: { w: 680, h: 366 }, xl: { w: 760, h: 500 },
  },
  decor: {
    xs: { w: 160, h: 60 }, sm: { w: 280, h: 100 }, md: { w: 400, h: 160 },
    lg: { w: 560, h: 280 }, xl: { w: 700, h: 460 },
  },
  // Imagem e forma: só o LADO MAIOR sai daqui (w = h); o outro lado sai da proporção guardada no
  // elemento. É o que deixa um logo largo e um selo quadrado caberem na mesma tabela sem largura e
  // altura soltas. O xl chega a 1008 px com o ajuste fino — uma divisória de meio painel.
  image: {
    xs: { w: 90, h: 90 }, sm: { w: 160, h: 160 }, md: { w: 260, h: 260 },
    lg: { w: 420, h: 420 }, xl: { w: 720, h: 720 },
  },
  shape: {
    xs: { w: 90, h: 90 }, sm: { w: 160, h: 160 }, md: { w: 260, h: 260 },
    lg: { w: 420, h: 420 }, xl: { w: 720, h: 720 },
  },
  chart: {
    xs: { w: 200, h: 64 }, sm: { w: 280, h: 86 }, md: { w: 360, h: 112 },
    lg: { w: 480, h: 140 }, xl: { w: 600, h: 176 },
  },
}

export const SIZE_ORDER: SizeToken[] = ['xs', 'sm', 'md', 'lg', 'xl']

/** Pegada já com o ajuste fino aplicado, arredondada ao pixel. */
export function footprintFor(el: Element): { w: number; h: number } {
  const base = TABLE[el.kind][el.place.size]
  let { w, h } = base

  // Um ícone é quadrado; um chip de texto ("D", "SPORT", "EV") é largo. O mesmo tipo, duas formas.
  if (el.kind === 'indicator' && el.style === 'icon') w = h
  // Barra vertical é a mesma barra deitada de lado.
  if (el.kind === 'barLinear' && el.orientation === 'v') { const t = w; w = h; h = t }
  if (el.kind === 'image' || el.kind === 'shape') {
    const a = Math.max(0.02, Math.min(50, el.aspect || 1))
    if (a >= 1) h = w / a
    else w = h * a
  }

  const s = el.place.scale
  // Dois pixels de piso: uma divisória fina ainda precisa de uma caixa que o mouse alcance.
  return { w: Math.max(2, Math.round(w * s)), h: Math.max(2, Math.round(h * s)) }
}

/** Desloca a pegada de modo que `anchor` caia exatamente sobre `at`. */
export function rectFromAnchor(anchor: Anchor, at: { x: number; y: number }, w: number, h: number): Rect {
  const dx = anchor.includes('Left') ? 0 : anchor.includes('Right') ? w : w / 2
  const dy = anchor.startsWith('top') ? 0 : anchor.startsWith('bottom') ? h : h / 2
  return { x: Math.round(at.x - dx), y: Math.round(at.y - dy), width: w, height: h }
}

/** Retângulo ocupado pelo elemento no canvas. */
export function rectFor(el: Element): Rect {
  const { w, h } = footprintFor(el)
  return rectFromAnchor(el.place.anchor, el.place.at, w, h)
}

/** Centro geométrico, que é o que interessa para um mostrador redondo. */
export function centerOf(el: Element): { cx: number; cy: number } {
  const r = rectFor(el)
  return { cx: r.x + r.width / 2, cy: r.y + r.height / 2 }
}

/** Raio externo de um mostrador redondo — metade da pegada. */
export function radiusOf(el: Element): number {
  return footprintFor(el).w / 2
}

export const rectsOverlap = (a: Rect, b: Rect) =>
  a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
