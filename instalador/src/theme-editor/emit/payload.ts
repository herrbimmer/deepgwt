// `ThemeSpec` -> o objeto que o motor recebe em `OPENGW.boot(...)`.
//
// Tudo que dá para resolver em tempo de geração é resolvido aqui: geometria, cores, pilhas de
// fonte, quais partes de cada estilo desenhar, quais chaves assinar. O motor não interpreta
// estilo nenhum — ele desenha o que o payload mandar. É o que permite acrescentar um estilo novo
// sem tocar no runtime, e é o que mantém os bytes do motor idênticos entre temas.

import type {
  BarElement, Binding, ChartElement, Color, DialElement, Element, FontRef, ImageElement, IndicatorElement,
  MediaElement, Paint, ReadoutElement, ShapeElement, TextStyle, ThemePage, ThemeSpec, ValueMap,
} from '../model/spec'
import { isToken, tokenName } from '../model/spec'
import { footprintFor, rectFor } from '../model/sizes'
import { TICK_OUTER_RATIO } from '../model/collision'
import { HINT_BY_KEY } from '../model/dataSources'
import { LIMIT_KEY, LIMIT_STATUS_KEY } from '../model/defaults'

export const FONT_STACKS: Record<FontRef, string> = {
  sysSans: '-apple-system, "Segoe UI", Roboto, Arial, sans-serif',
  sysCondensed: '"Roboto Condensed", "Arial Narrow", Roboto, Arial, sans-serif',
  sysSerif: 'Georgia, "Times New Roman", serif',
  sysMono: '"Roboto Mono", Menlo, Consolas, monospace',
  exoV: '"ExoV", -apple-system, Roboto, Arial, sans-serif',
  michroma: '"Michroma", -apple-system, Roboto, Arial, sans-serif',
}

/** Fontes que precisam de arquivo junto do tema. As de sistema não precisam de nada. */
export const FONT_FILES: Partial<Record<FontRef, { file: string; format: string }>> = {
  exoV: { file: 'ExoV.woff2', format: 'woff2' },
  michroma: { file: 'Michroma.ttf', format: 'truetype' },
}

/** Token vira referência de variável; literal vai como está. */
export const cssColor = (c: Color): string => (isToken(c) ? `var(--t-${tokenName(c)})` : c)

const paint = (p: Paint): unknown => {
  switch (p.fill) {
    case 'none': return { fill: 'none' }
    case 'solid': return { fill: 'solid', color: cssColor(p.color) }
    case 'linear': return { fill: 'linear', from: cssColor(p.from), to: cssColor(p.to), angleDeg: p.angleDeg }
    case 'radial': return { fill: 'radial', from: cssColor(p.from), to: cssColor(p.to), cx: p.cx, cy: p.cy, stopAt: p.stopAt }
    case 'hubRadial': return { fill: 'hubRadial', inner: cssColor(p.inner), mid: cssColor(p.mid), outer: cssColor(p.outer), midAt: p.midAt }
  }
}

/**
 * Um estilo de texto vira duas formas: `fs` para CSS de elemento HTML e `fsRaw` para atributo de
 * `<text>` do SVG, que não aceita `calc()`.
 */
const textStyle = (t: TextStyle, scale: number) => {
  const px = Math.round(t.sizePx * scale)
  return {
    ff: FONT_STACKS[t.font],
    fs: t.hostScaled ? `calc(${px}px * var(--gauge-num-scale, 1))` : `${px}px`,
    fsRaw: px,
    fw: t.weight,
    ls: t.tracking ? t.tracking * scale : 0,
    tt: t.transform === 'upper' ? 'uppercase' : '',
    tn: t.tabular,
    color: cssColor(t.color),
  }
}

/** Fração do arco em que um valor cai — a mesma conta que o motor faz. */
export function valueToT(map: ValueMap, v: number): number {
  const c01 = (n: number) => Math.max(0, Math.min(1, n))
  if (map.kind === 'linear') return map.max === map.min ? 0 : c01((v - map.min) / (map.max - map.min))
  if (map.kind === 'dualRange') return c01((v - map.min) / (map.low - map.min))
  // Em tempo de geração não existe placa nenhuma, então a conta usa a VMAX sem placa — é a mesma
  // que o motor usa antes do primeiro valor do TSR chegar.
  if (map.kind === 'signLimit') return c01((v - map.min) / (map.fallbackMax - map.min))
  if (map.kind === 'piecewise') {
    const p = map.points
    if (v <= p[0].v) return p[0].t
    for (let i = 1; i < p.length; i++) {
      if (v <= p[i].v) {
        const f = (v - p[i - 1].v) / (p[i].v - p[i - 1].v)
        return c01(p[i - 1].t + f * (p[i].t - p[i - 1].t))
      }
    }
    return p[p.length - 1].t
  }
  return 0
}

type Parts = { face: boolean; ticks: boolean; labels: boolean; needle: boolean; arc: boolean; segments: number; index: boolean }

/**
 * O estilo vira partes. É a tradução inteira: o runtime não conhece nenhum nome de estilo.
 */
function partsFor(el: DialElement): Parts {
  const base: Parts = {
    face: el.face.enabled, ticks: true, labels: el.ticks.showLabels,
    needle: el.needle.shape !== 'none', arc: el.arc.enabled, segments: 0, index: false,
  }
  switch (el.style) {
    case 'faceNeedle': return { ...base, face: true }
    case 'ticksNeedle': return base
    case 'halfArc': return base
    case 'arcOnly': return { ...base, ticks: false, labels: false, needle: false, arc: true }
    case 'segmentRing': return { ...base, ticks: false, labels: false, needle: false, arc: false, segments: 40 }
    case 'minimalIndex': return { ...base, ticks: false, labels: false, needle: false, arc: false, index: true }
    case 'ring': return { ...base, face: false, ticks: false, labels: false, needle: false, arc: true }
  }
}

/** Os elementos que vão para o carro: o "olho" da lista de camadas tira um elemento do tema sem apagá-lo. */
export const elementosVisiveis = (page: ThemePage): Element[] => page.elements.filter((el) => !el.hidden)

const binding = (b: Binding | null) => (b ? {
  key: b.key,
  scale: b.transform?.scale,
  offset: b.transform?.offset,
  abs: b.transform?.abs,
  decimals: b.decimals,
  unit: b.unit,
  fallback: b.fallback ?? '--',
  // Só viaja quando vale alguma coisa: o motor testa `b.smoothMs > 0`, e um `smoothMs: 0` em
  // cada ligação de cada tema seria peso morto no payload de todo `app.html`.
  smoothMs: b.smoothMs && b.smoothMs > 0 ? b.smoothMs : undefined,
  smoothStep: b.smoothStep && b.smoothStep > 0 ? b.smoothStep : undefined,
} : null)

function dialPayload(el: DialElement) {
  const fp = footprintFor(el)
  const s = el.place.scale
  const R = fp.w / 2
  const geom = {
    cx: fp.w / 2, cy: fp.h / 2, R,
    tickOuterR: R * TICK_OUTER_RATIO,
    labelR: R * TICK_OUTER_RATIO - el.ticks.labelInset * s,
    needleInnerR: el.needle.innerR * s,
    needleOuterR: R - el.needle.outerInset * s,
    startDeg: el.sweep.startDeg,
    endDeg: el.sweep.endDeg,
  }
  return {
    geom,
    parts: partsFor(el),
    face: {
      enabled: el.face.enabled,
      paint: paint(el.face.paint),
      // Escala junto com o mostrador: um disco 3% maior desfocado 3% menos leria como outro
      // material. Só viaja quando vale alguma coisa — o motor testa `> 0`.
      blurPx: el.face.blurPx && el.face.blurPx > 0 ? Math.round(el.face.blurPx * s * 10) / 10 : undefined,
      ring: el.face.ring ? { width: el.face.ring.width * s, color: cssColor(el.face.ring.color) } : null,
      bezel: el.face.bezel ? { width: el.face.bezel.width * s, color: cssColor(el.face.bezel.color) } : null,
    },
    ticks: {
      majors: el.ticks.majors,
      subdivisions: el.ticks.subdivisions,
      labelStyle: textStyle(el.ticks.labelStyle, s),
      len: { major: el.ticks.len.major * s, minor: el.ticks.len.minor * s, micro: el.ticks.len.micro * s },
      width: { major: el.ticks.width.major, minor: el.ticks.width.minor, micro: el.ticks.width.micro },
      zones: el.ticks.zones.map((z) => ({ from: z.from, to: z.to, color: cssColor(z.token) })),
    },
    needle: {
      shape: el.needle.shape,
      width: el.needle.width * s,
      paint: paint(el.needle.paint),
      cap: el.needle.cap,
      glow: el.needle.glow,
    },
    // Zero re-rasteriza a sombra do arco a cada quadro. O piso fica aqui, não na interface:
    // é contrato de desempenho, e interface nenhuma deveria poder desligá-lo.
    arc: {
      radiusOffset: el.arc.radiusOffset * s,
      width: el.arc.width * s,
      quantizeDeg: Math.max(0.5, el.arc.quantizeDeg),
      split: el.arc.split ? true : false,
      splitT: el.arc.split ? valueToT(el.map, el.arc.split.at) : 0,
    },
    // Só viaja quando existe: todo mostrador anterior ao anel continua com o payload de antes.
    arcTrack: el.arc.track ? cssColor(el.arc.track) : undefined,
    arcColor: el.arc.split ? cssColor(el.arc.split.beforeToken) : cssColor('$accent'),
    arcColorAfter: el.arc.split ? cssColor(el.arc.split.afterToken) : cssColor('$warn'),
    // Cor das marcações acima do limite da via, quando o mostrador segue placa.
    overLimitColor: cssColor('$warn'),
    tickColor: cssColor('$ink'),
    tickColorDim: cssColor('$inkFaint'),
    needleColor: cssColor('$needle'),
    needleGlow: cssColor('$needleGlow'),
    hubRingColor: cssColor('$faceRing'),
    segColor: cssColor('$accent'),
    zoom: el.zoom.enabled ? {
      enabled: true, scale: el.zoom.scale, weight: el.zoom.weight,
      durationMs: el.zoom.durationMs, easing: el.zoom.easing,
    } : { enabled: false, scale: 1, weight: el.ticks.labelStyle.weight, durationMs: 0, easing: 'linear' },
    center: el.center ? {
      text: textStyle(el.center.text, s),
      label: el.center.label,
      labelStyle: textStyle(el.center.labelStyle, s),
      offsetPx: Math.round(R * el.center.offsetY),
    } : null,
  }
}

function barPayload(el: BarElement, s: number) {
  return {
    orientation: el.orientation,
    track: paint(el.track),
    fill: paint(el.fill),
    segments: el.segments,
    readout: el.readout ? {
      text: textStyle(el.readout.text, s),
      label: el.readout.label,
      labelStyle: textStyle(el.readout.labelStyle, s),
    } : null,
  }
}

function readoutPayload(el: ReadoutElement, s: number) {
  return {
    text: textStyle(el.text, s),
    label: el.label,
    labelStyle: textStyle(el.labelStyle, s),
    icon: el.icon,
    iconColor: cssColor(el.labelStyle.color),
  }
}

function indicatorPayload(el: IndicatorElement, s: number) {
  const on = el.onPaint.fill === 'solid' ? el.onPaint.color : '$ok'
  const off = el.offPaint.fill === 'solid' ? el.offPaint.color : '$inkFaint'
  return { icon: el.icon, onColor: cssColor(on), offColor: cssColor(off), text: textStyle(el.text, s) }
}

function mediaPayload(el: MediaElement, s: number) {
  return {
    showArtwork: el.showArtwork,
    title: textStyle(el.title, s),
    artist: textStyle(el.artist, s),
    panel: paint(el.panel),
  }
}

function imagePayload(el: ImageElement) {
  return { src: el.src, fit: el.fit, radius: el.radius }
}

function shapePayload(el: ShapeElement) {
  return {
    shape: el.shape,
    paint: paint(el.paint),
    stroke: el.stroke ? { width: el.stroke.width, color: cssColor(el.stroke.color) } : null,
    radius: el.radius,
  }
}

/**
 * O gráfico guarda no máximo 120 pontos. Uma janela maior que dois minutos amostra de N em N
 * segundos em vez de guardar mais pontos: o desenho fica do mesmo tamanho, e o custo também.
 */
function chartPayload(el: ChartElement, s: number) {
  const janela = Math.max(10, Math.min(1800, Math.round(el.windowSec)))
  const pontos = Math.min(120, janela)
  return {
    windowSec: janela,
    points: pontos,
    stepSec: Math.max(1, Math.round(janela / pontos)),
    lineColor: cssColor(el.line),
    area: el.area,
    showValue: el.showValue,
    text: textStyle(el.text, s),
    label: el.label,
    labelStyle: textStyle(el.labelStyle, s),
  }
}

/** As chaves que o elemento precisa, além da principal. */
function extraKeysOf(el: Element): string[] {
  if (el.kind === 'media') {
    const k = ['app.media.artist', 'app.media.state']
    if (el.showArtwork) k.push('app.media.album_art')
    return k
  }
  return []
}

function elementPayload(el: Element) {
  const r = rectFor(el)
  const s = el.place.scale
  const comum = {
    id: el.id,
    kind: el.kind,
    style: (el as { style?: string }).style ?? '',
    rect: { x: r.x, y: r.y, w: r.width, h: r.height },
    z: el.place.z,
    opacity: el.place.opacity,
    visible: el.visible,
    extraKeys: extraKeysOf(el),
  }

  switch (el.kind) {
    case 'dial':
      return { ...comum, binding: binding(el.binding), map: el.map, ...dialPayload(el) }
    case 'barLinear':
      return { ...comum, binding: binding(el.binding), map: el.map, ...barPayload(el, s) }
    case 'readout':
      return {
        ...comum,
        binding: el.style === 'clock' ? null : binding(el.binding),
        map: el.style === 'clock' ? { kind: 'text' } : el.map,
        ...readoutPayload(el, s),
      }
    case 'indicator':
      return { ...comum, binding: binding(el.binding), map: el.map, ...indicatorPayload(el, s) }
    case 'media':
      return {
        ...comum,
        binding: { key: 'app.media.title', fallback: '' },
        map: { kind: 'text' },
        ...mediaPayload(el, s),
      }
    case 'decor':
      return { ...comum, binding: null, map: { kind: 'text' }, paint: paint(el.paint), radius: el.radius, blur: el.blur }
    case 'image':
      return { ...comum, binding: null, map: { kind: 'text' }, ...imagePayload(el) }
    case 'shape':
      return { ...comum, binding: null, map: { kind: 'text' }, ...shapePayload(el) }
    case 'chart':
      return { ...comum, binding: binding(el.binding), map: el.map, ...chartPayload(el, s) }
  }
}

/**
 * Faixas para a simulação do preview. Saem do próprio tema, então o preview anima exatamente o
 * que o tema lê — e nunca uma chave que o tema não usa.
 */
function mockRanges(spec: ThemeSpec): Record<string, unknown> {
  const out: Record<string, unknown> = {}

  const simularChave = (key: string, map?: ValueMap) => {
    if (out[key]) return
    const hint = HINT_BY_KEY.get(key)
    if (hint?.format === 'boolish') { out[key] = { kind: 'boolish' }; return }
    if (hint?.format === 'enum') {
      const chavesEnum = Object.keys(hint.enumLabels ?? {})
      out[key] = { kind: 'enum', samples: chavesEnum.length ? chavesEnum : ['0'] }
      return
    }
    if (hint?.format === 'text' || hint?.format === 'json' || hint?.format === 'dataUrl') {
      out[key] = { kind: 'text', sample: '' }
      return
    }
    const s = hint?.suggested
    out[key] = map && map.kind === 'linear'
      ? { min: map.min, max: map.max }
      : { min: s?.min ?? 0, max: s?.max ?? 100 }
  }

  // Escala pela placa: o limite da via alterna entre valores urbanos plausíveis em vez de varrer
  // uma rampa. Uma placa que muda de 0 a 130 continuamente não existe em estrada nenhuma, e o
  // preview ficaria mentindo sobre como a função se comporta de verdade.
  for (const page of spec.pages) {
    for (const el of elementosVisiveis(page)) {
      const m = (el as { map?: ValueMap }).map
      if (m && m.kind === 'signLimit') {
        out[m.limitKey] = { kind: 'enum', samples: ['40', '60', '80', '110'] }
        if (m.statusKey) out[m.statusKey] = { kind: 'text', sample: '1' }
      }
    }
  }

  // As condições das regras de cor também precisam se mexer no preview, senão a regra fica
  // congelada no estado inicial e o usuário não vê o que acabou de configurar.
  for (const regra of spec.colorRules) {
    if (!regra.enabled) continue
    if ('key' in regra.when) simularChave(regra.when.key)
    if (regra.when.when === 'overLimit') {
      simularChave('car.basic.vehicle_speed')
      if (!out[LIMIT_KEY]) out[LIMIT_KEY] = { kind: 'enum', samples: ['40', '60', '80', '110'] }
      if (!out[LIMIT_STATUS_KEY]) out[LIMIT_STATUS_KEY] = { kind: 'text', sample: '1' }
    }
  }

  for (const page of spec.pages) {
    for (const el of elementosVisiveis(page)) {
      const chaves: string[] = []
      const b = (el as { binding?: Binding | null }).binding
      if (b?.key) chaves.push(b.key)
      chaves.push(...extraKeysOf(el))
      if (el.kind === 'media') chaves.push('app.media.title')

      for (const key of chaves) {
        if (out[key]) continue
        const hint = HINT_BY_KEY.get(key)
        const map = (el as { map?: ValueMap }).map
        if (hint?.format === 'boolish') { out[key] = { kind: 'boolish' } ; continue }
        if (hint?.format === 'enum') {
          const chavesEnum = Object.keys(hint.enumLabels ?? {})
          out[key] = { kind: 'enum', samples: chavesEnum.length ? chavesEnum : ['0'] }
          continue
        }
        if (hint?.format === 'text' || hint?.format === 'json' || hint?.format === 'dataUrl') {
          out[key] = { kind: 'text', sample: key === 'app.media.title' ? 'Faixa de demonstração' : key === 'app.media.artist' ? 'Artista' : '' }
          continue
        }
        if (map && map.kind === 'linear') out[key] = { min: map.min, max: map.max }
        else if (map && map.kind === 'dualRange') out[key] = { min: map.min, max: map.high }
        else if (map && map.kind === 'piecewise') out[key] = { min: map.points[0].v, max: map.points[map.points.length - 1].v }
        else out[key] = { min: 0, max: 100 }
      }
    }
  }
  return out
}

export type ThemePayload = ReturnType<typeof buildPayload>

export function buildPayload(spec: ThemeSpec, activePageId?: string) {
  const subscribe = new Set<string>()
  const control = new Set<string>()

  for (const page of spec.pages) {
    // A condição de uma página que abre sozinha lê do carro mesmo que nenhum elemento use a chave.
    const sw = page.showWhen
    if (sw && sw.when !== 'always') {
      if ('key' in sw) subscribe.add(sw.key)
      if (sw.when === 'overLimit') {
        subscribe.add('car.basic.vehicle_speed')
        subscribe.add(LIMIT_KEY)
        subscribe.add(LIMIT_STATUS_KEY)
      }
    }
    for (const el of elementosVisiveis(page)) {
      const chaves: string[] = []
      const b = (el as { binding?: Binding | null }).binding
      if (b?.key) chaves.push(b.key)
      chaves.push(...extraKeysOf(el))
      if (el.visible.when !== 'always' && 'key' in el.visible) chaves.push(el.visible.key)
      if (el.visible.when === 'overLimit') chaves.push('car.basic.vehicle_speed', LIMIT_KEY)
      // Um mostrador que segue a placa precisa das duas chaves do TSR, e não é a ligação dele que
      // as traz: a ligação dele é a velocidade.
      const mapa = (el as { map?: ValueMap }).map
      if (mapa && mapa.kind === 'signLimit') {
        chaves.push(mapa.limitKey)
        if (mapa.statusKey) chaves.push(mapa.statusKey)
      }
      if (el.kind === 'media') chaves.push('app.media.title')
      for (const k of chaves) {
        // As seis chaves de projeção nunca disparam `onDataChanged`; assiná-las não faz mal, mas
        // também não adianta — o valor real só chega por `window.control`.
        if (k === 'bsdLeft' || k === 'bsdRight' || k.startsWith('carPlayInDash') || k.startsWith('projection')) control.add(k)
        else subscribe.add(k)
      }
    }
  }
  // As regras de cor também leem do carro, e podem depender de uma chave que nenhum elemento usa.
  for (const regra of spec.colorRules) {
    if (!regra.enabled) continue
    if ('key' in regra.when) subscribe.add(regra.when.key)
    if (regra.when.when === 'overLimit') {
      subscribe.add('car.basic.vehicle_speed')
      subscribe.add(LIMIT_KEY)
      subscribe.add(LIMIT_STATUS_KEY)
    }
  }

  // Com o combo de escala ligado, o motorista pode escolher "Seguir placa" a qualquer momento
  // dentro do carro. As chaves do TSR precisam estar assinadas ANTES disso: assinar depois exigiria
  // um segundo `Android.subscribe` em tempo de execução, e o valor só chegaria na próxima mudança.
  if (spec.speedScaleConfig) {
    subscribe.add(LIMIT_KEY)
    subscribe.add(LIMIT_STATUS_KEY)
  }

  // O tema sempre acompanha o farol e o aviso do veículo, tenha ou não elemento ligado a eles.
  subscribe.add('car.basic.head_light_status')
  subscribe.add('warningActive')

  const paginas = spec.pages.map((p) => ({
    id: p.id,
    name: p.name,
    elements: [...elementosVisiveis(p)].sort((a, b2) => a.place.z - b2.place.z).map(elementPayload),
    // Só viajam quando existem, para um tema sem nada disso gerar o payload de sempre.
    showWhen: p.showWhen && p.showWhen.when !== 'always' ? p.showWhen : undefined,
    bg: p.background?.src || undefined,
  }))
  if (activePageId) {
    const i = paginas.findIndex((p) => p.id === activePageId)
    if (i > 0) paginas.unshift(paginas.splice(i, 1)[0])
  }

  return {
    schema: 2,
    // Os nomes dos tokens de cor, para o `snapshotSvg` conseguir ler as variáveis já resolvidas e
    // colar no SVG da miniatura. Sem esta lista o motor não teria como enumerá-las.
    tokens: Object.keys(spec.palette.tokens),
    speedScaleConfig: spec.speedScaleConfig,
    colorRules: spec.colorRules
      .filter((r) => r.enabled)
      .map((r) => ({ enabled: true, tokens: r.tokens, color: r.color, when: r.when })),
    pages: paginas,
    subscribeKeys: [...subscribe],
    controlKeys: [...control],
    appDefault: spec.appDefaultPosition,
    boot: spec.boot,
    welcomeSweep: spec.welcomeSweep ? true : undefined,
    mockRanges: mockRanges(spec),
  }
}
