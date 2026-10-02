// Colisão contra o que o veículo desenha por cima do tema.
//
// Testar a caixa do elemento seria o caminho óbvio e está errado. As três zonas OEM caem DENTRO
// do mostrador esquerdo de qualquer velocímetro redondo grande — o do Cupertino tem centro em
// (335,400) e raio 265, então a caixa dele vai de x 70 a 600 e engole as três. Com teste de
// caixa, clonar os temas de fábrica seria impossível, e o editor estaria proibindo exatamente o
// desenho que o veículo aceita há meses.
//
// O que o contrato proíbe é conteúdo LEGÍVEL embaixo dos indicadores: marcação, número, ícone,
// texto. O miolo transparente do mostrador e o ponteiro fino podem cruzar — os dois temas de
// fábrica cruzam. Então a colisão é por região de tinta, não por caixa.

import type { DialElement, Element, Rect, ThemeSpec } from './spec'
import { footprintFor, rectFor, rectsOverlap } from './sizes'
import { OEM_FORBIDDEN, zoneRect } from './zones'
import type { ForbiddenZone } from './zones'
import { modoLivreAtivo } from './freeMode'

export type Region =
  | ({ kind: 'rect' } & Rect)
  | { kind: 'annulus'; cx: number; cy: number; rIn: number; rOut: number; fromDeg: number; toDeg: number }

/** Proporção do raio externo em que ficam as pontas das marcações maiores. */
export const TICK_OUTER_RATIO = 0.94

export type DialGeometry = {
  cx: number
  cy: number
  /** Raio externo, bisel incluído. */
  R: number
  tickOuterR: number
  labelR: number
  labelHalf: number
  needleInnerR: number
  needleOuterR: number
  startDeg: number
  endDeg: number
  scale: number
}

/**
 * A geometria de um mostrador redondo, derivada só da pegada. É a MESMA função que o emissor usa
 * para montar o payload — se colisão e desenho divergissem, o editor estaria bloqueando um
 * desenho e gerando outro.
 */
export function dialGeometry(el: DialElement): DialGeometry {
  const { w } = footprintFor(el)
  const r = rectFor(el)
  const R = w / 2
  const s = el.place.scale
  const tickOuterR = R * TICK_OUTER_RATIO
  const labelR = tickOuterR - el.ticks.labelInset * s
  return {
    cx: r.x + r.width / 2,
    cy: r.y + r.height / 2,
    R,
    tickOuterR,
    labelR,
    labelHalf: el.ticks.labelStyle.sizePx * s * 0.62,
    needleInnerR: el.needle.innerR * s,
    needleOuterR: R - el.needle.outerInset * s,
    startDeg: el.sweep.startDeg,
    endDeg: el.sweep.endDeg,
    scale: s,
  }
}

/**
 * Retângulo da leitura central, quando existe. A largura é a extensão real do texto (cerca de
 * quatro caracteres na fonte do valor), não a caixa do elemento: um bloco de texto centralizado
 * ocupa uma faixa estreita no meio, e usar a caixa inteira proibiria posições perfeitamente boas.
 */
function centerRect(el: DialElement): Rect | null {
  if (!el.center) return null
  const g = dialGeometry(el)
  const h = (el.center.text.sizePx + el.center.labelStyle.sizePx * 1.8) * g.scale
  const w = el.center.text.sizePx * g.scale * 0.62 * 4
  const cy = g.cy + g.R * el.center.offsetY
  return { x: Math.round(g.cx - w / 2), y: Math.round(cy - h / 2), width: Math.round(w), height: Math.round(h) }
}

/**
 * A tinta do elemento, separada entre o que bloqueia e o que só avisa.
 *
 * O ponteiro é suave de propósito. Um traço de 5 px cruzando o READY durante uma fração da
 * varredura é o que os dois temas de fábrica já fazem e ninguém reclamou; bloquear isso proibiria
 * qualquer mostrador redondo na metade esquerda, que é justamente onde eles moram.
 */
export function inkRegions(el: Element): { hard: Region[]; soft: Region[] } {
  // Forma é moldura e divisória, como a decoração: não é conteúdo legível, então não bloqueia.
  if (el.kind === 'decor' || el.kind === 'shape') return { hard: [], soft: [{ kind: 'rect', ...rectFor(el) }] }

  if (el.kind !== 'dial') return { hard: [{ kind: 'rect', ...rectFor(el) }], soft: [] }

  const g = dialGeometry(el)
  const hard: Region[] = []
  const soft: Region[] = []

  // O anel não desenha marcação: a tinta dele é o arco na borda, que é o ramo de baixo.
  const temMarcacoes = el.style !== 'minimalIndex' && el.style !== 'ring' && el.ticks.majors.length > 0
  if (temMarcacoes) {
    const rIn = el.ticks.showLabels ? Math.max(0, g.labelR - g.labelHalf) : g.tickOuterR - 20 * g.scale
    hard.push({ kind: 'annulus', cx: g.cx, cy: g.cy, rIn, rOut: g.R, fromDeg: g.startDeg, toDeg: g.endDeg })
  } else {
    hard.push({
      kind: 'annulus', cx: g.cx, cy: g.cy,
      rIn: g.tickOuterR - 24 * g.scale, rOut: g.R, fromDeg: g.startDeg, toDeg: g.endDeg,
    })
  }

  const cr = centerRect(el)
  if (cr) hard.push({ kind: 'rect', ...cr })

  if (el.needle.shape !== 'none') {
    soft.push({
      kind: 'annulus', cx: g.cx, cy: g.cy,
      rIn: g.needleInnerR, rOut: g.needleOuterR, fromDeg: g.startDeg, toDeg: g.endDeg,
    })
  }
  return { hard, soft }
}

// --- geometria pura ------------------------------------------------------------------------

const pointInRect = (x: number, y: number, r: Rect) =>
  x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height

function distPointRect(x: number, y: number, r: Rect): number {
  const dx = Math.max(r.x - x, 0, x - (r.x + r.width))
  const dy = Math.max(r.y - y, 0, y - (r.y + r.height))
  return Math.hypot(dx, dy)
}

function maxDistPointRect(x: number, y: number, r: Rect): number {
  const xs = [r.x, r.x + r.width]
  const ys = [r.y, r.y + r.height]
  let d = 0
  for (const px of xs) for (const py of ys) d = Math.max(d, Math.hypot(px - x, py - y))
  return d
}

/** 0° aponta para cima e cresce no sentido horário — a mesma convenção do motor. */
const polar = (cx: number, cy: number, r: number, deg: number) => {
  const rad = ((deg - 90) * Math.PI) / 180
  return { x: cx + r * Math.cos(rad), y: cy + r * Math.sin(rad) }
}

/**
 * Anel × retângulo. Rejeição radial exata primeiro, que descarta quase tudo de imediato; só
 * sobrando é que amostra o anel. Amostrar o ANEL e não o retângulo cobre também o caso em que o
 * retângulo é grande o bastante para conter um trecho inteiro do anel.
 */
function annulusHitsRect(a: Extract<Region, { kind: 'annulus' }>, r: Rect): boolean {
  const dMin = distPointRect(a.cx, a.cy, r)
  const dMax = maxDistPointRect(a.cx, a.cy, r)
  if (dMax < a.rIn || dMin > a.rOut) return false

  const span = Math.abs(a.toDeg - a.fromDeg)
  // Passo angular com comprimento de arco de no máximo 6 px na borda externa.
  const stepDeg = Math.max(0.5, Math.min(4, (6 / Math.max(1, a.rOut)) * (180 / Math.PI)))
  const passosAng = Math.ceil(span / stepDeg)
  // Passo radial de no máximo 8 px, para não pular um retângulo estreito no meio da faixa.
  const passosRad = Math.max(1, Math.ceil((a.rOut - a.rIn) / 8))

  for (let i = 0; i <= passosAng; i++) {
    const deg = a.fromDeg + (span * i) / passosAng
    for (let j = 0; j <= passosRad; j++) {
      const rad = a.rIn + ((a.rOut - a.rIn) * j) / passosRad
      const p = polar(a.cx, a.cy, rad, deg)
      if (pointInRect(p.x, p.y, r)) return true
    }
  }
  return false
}

const regionHitsRect = (region: Region, r: Rect): boolean =>
  region.kind === 'rect' ? rectsOverlap(region, r) : annulusHitsRect(region, r)

// --- a pergunta que o arraste faz ----------------------------------------------------------

export type Blocker = { zone: ForbiddenZone; region: Region }

/** A primeira zona proibida que a tinta dura do elemento invade, ou `null` se está tudo certo. */
export function firstBlocker(el: Element): Blocker | null {
  const { hard } = inkRegions(el)
  for (const zona of OEM_FORBIDDEN) {
    for (const region of hard) {
      if (regionHitsRect(region, zona.safe)) return { zone: zona, region }
    }
  }
  return null
}

/**
 * "Pode ficar aqui?" — a pergunta que o arraste, as setas e o ajuste fino fazem.
 *
 * `firstBlocker` continua respondendo a verdade a todo mundo (é dele que sai o rótulo vermelho no
 * canvas e o aviso no painel de posição). O que o modo livre muda é só quem decide o que fazer com
 * essa verdade: ligado, o editor informa e obedece; desligado, informa e recusa, como sempre fez.
 * Ver `freeMode.ts`.
 */
export const isLegal = (el: Element): boolean => modoLivreAtivo() || firstBlocker(el) === null

/** Zonas OEM que a tinta suave encosta — aviso, não bloqueio. */
export function softTouches(el: Element): ForbiddenZone[] {
  const { soft } = inkRegions(el)
  return OEM_FORBIDDEN.filter((z) => soft.some((r) => regionHitsRect(r, z.safe)))
}

/** O elemento com a âncora movida, sem alterar o original. */
export const movedTo = <T extends Element>(el: T, at: { x: number; y: number }): T =>
  ({ ...el, place: { ...el.place, at } }) as T

const scaled = <T extends Element>(el: T, scale: number): T =>
  ({ ...el, place: { ...el.place, scale } }) as T

/**
 * O maior ajuste fino que mantém o elemento legal onde ele está. É isto que trava o controle
 * deslizante em vez de deixar o usuário passar do limite e só depois descobrir.
 */
export function maxLegalScale(el: Element, teto = 1.4, piso = 0.6, passo = 0.01): number {
  // Em modo livre não há teto a calcular: o controle vai até o fim da faixa e o aviso, se houver,
  // aparece no painel. Sair cedo também poupa as ~80 avaliações de colisão da varredura abaixo,
  // que rodam a cada redesenho do painel de posição.
  if (modoLivreAtivo()) return teto
  if (!isLegal(scaled(el, piso))) return piso
  let melhor = piso
  for (let s = piso; s <= teto + 1e-9; s += passo) {
    const v = Math.round(s * 100) / 100
    if (isLegal(scaled(el, v))) melhor = v
    else break
  }
  return melhor
}

/**
 * A primeira posição legal dentro de uma zona, varrendo do centro para fora numa grade de 20 px.
 * Usada ao criar um elemento e ao trocar o tipo ou o tamanho de um existente, para que o editor
 * nunca produza algo ilegal e nunca precise dizer "não deu".
 */
export function firstLegalSlot(spec: ThemeSpec, el: Element): { x: number; y: number } | null {
  const zona = zoneRect(spec, el.place.zone)
  const cx = zona.x + zona.width / 2
  const cy = zona.y + zona.height / 2
  if (isLegal(movedTo(el, { x: cx, y: cy }))) return { x: cx, y: cy }

  const passo = 20
  const maxAnel = Math.ceil(Math.max(zona.width, zona.height) / 2 / passo)
  for (let anel = 1; anel <= maxAnel; anel++) {
    const d = anel * passo
    for (let ang = 0; ang < 360; ang += 15) {
      const rad = (ang * Math.PI) / 180
      const x = Math.round(cx + Math.cos(rad) * d)
      const y = Math.round(cy + Math.sin(rad) * d)
      if (x < zona.x || y < zona.y || x > zona.x + zona.width || y > zona.y + zona.height) continue
      if (isLegal(movedTo(el, { x, y }))) return { x, y }
    }
  }
  return null
}
