// Valores iniciais: a paleta padrão e a fábrica de elementos.
//
// Um elemento novo tem que nascer usável — ligado a uma chave que o carro entrega de verdade, com
// faixa coerente e legível no tamanho padrão. O editor antigo criava um mostrador de temperatura
// ligado a `car.basic.coolant_temp`, chave que o contrato do tema recusa: nascia morto.

import type {
  ArcConfig, BarStyle, Binding, Color, ColorRule, DecorStyle, DialStyle, Element, ElementKind,
  FaceConfig, FontRef, IndicatorStyle, MaskId, MaskSpec, MediaStyle, Paint, Palette, ReadoutStyle,
  ShapeKind, TextStyle, ThemeSpec, TickConfig, ValueMap, VisibilityRule, ZoneId, ZoomConfig, NeedleConfig,
} from './spec'
import { HINT_BY_KEY } from './dataSources'
import { MASK_DEFAULTS } from './zones'

let contador = 0
const novoId = (prefixo: string) =>
  `${prefixo}-${Date.now().toString(36)}-${(contador++).toString(36)}-${Math.floor(Math.random() * 1e4).toString(36)}`

export const DEFAULT_PALETTE: Palette = {
  headlightFromHostVars: true,
  tokens: {
    ink: { label: 'Texto principal', day: '#f2f4f6', night: '#ff3b30' },
    inkDim: { label: 'Texto secundário', day: 'rgba(242,244,246,.55)', night: 'rgba(255,59,48,.6)' },
    inkFaint: { label: 'Texto apagado', day: 'rgba(242,244,246,.28)', night: 'rgba(255,59,48,.3)' },
    accent: { label: 'Destaque', day: '#0a84ff', night: '#ff453a' },
    needle: { label: 'Ponteiro', day: '#f7fbf8', night: '#ff6a61' },
    needleGlow: { label: 'Brilho do ponteiro', day: 'rgba(0,0,0,.45)', night: 'rgba(255,59,48,.5)' },
    warn: { label: 'Alerta', day: '#ff453a', night: '#ff453a' },
    ok: { label: 'Positivo', day: '#34e871', night: '#34e871' },
    low: { label: 'Nível baixo', day: '#ff9500', night: '#ff9500' },
    face: { label: 'Fundo do mostrador', day: '#2a3038', night: '#1b1f24' },
    faceLow: { label: 'Borda do fundo', day: '#0e1117', night: '#06090b' },
    faceRing: { label: 'Anel do mostrador', day: 'rgba(255,255,255,.18)', night: 'rgba(255,59,48,.22)' },
    bezel: { label: 'Bisel', day: '#05060a', night: '#05060a' },
    panel: { label: 'Painel de fundo', day: 'rgba(255,255,255,.06)', night: 'rgba(255,59,48,.08)' },
    track: { label: 'Trilho da barra', day: 'rgba(255,255,255,.12)', night: 'rgba(255,59,48,.16)' },
  },
}

export function text(
  sizePx: number,
  weight = 500,
  color: Color = '$ink',
  extra: Partial<TextStyle> = {},
): TextStyle {
  return {
    font: 'sysSans', sizePx, weight, tracking: 0, transform: 'none',
    tabular: true, hostScaled: false, color, ...extra,
  }
}

export const solid = (color: Color): Paint => ({ fill: 'solid', color })

export const DEFAULT_ZOOM: ZoomConfig = {
  enabled: true,
  scale: 1.5,
  weight: 700,
  durationMs: 380,
  easing: 'cubic-bezier(.22,.61,.36,1)',
}

export const DEFAULT_TICKS = (majors: number[]): TickConfig => ({
  majors,
  subdivisions: 4,
  showLabels: true,
  labelStyle: text(24, 500, '$ink', { tracking: 0.2 }),
  labelInset: 40,
  len: { major: 18, minor: 11, micro: 7 },
  width: { major: 3, minor: 1.6, micro: 1.2 },
  zones: [],
})

export const DEFAULT_NEEDLE: NeedleConfig = {
  shape: 'line',
  innerR: 20,
  outerInset: 54,
  width: 5,
  paint: solid('$needle'),
  cap: 'ringDot',
  glow: true,
}

export const DEFAULT_FACE: FaceConfig = {
  enabled: true,
  paint: { fill: 'radial', from: '$face', to: '$faceLow', cx: 0.34, cy: 0.28, stopAt: 0.78 },
  ring: { width: 9, color: '$faceRing' },
  bezel: { width: 10, color: '$bezel' },
}

export const DEFAULT_ARC: ArcConfig = {
  enabled: false,
  radiusOffset: 7,
  width: 7,
  quantizeDeg: 1,
  split: null,
}

/** Sugestão de faixa a partir do que `dataSources.ts` sabe da chave. */
export function mapForKey(key: string): { map: ValueMap; majors: number[]; binding: Binding } {
  const hint = HINT_BY_KEY.get(key)
  const binding: Binding = { key, unit: hint?.unit, fallback: '--' }
  if (!hint) return { map: { kind: 'linear', min: 0, max: 100 }, majors: [0, 50, 100], binding }
  if (hint.format === 'enum') {
    return {
      map: { kind: 'enum', labels: hint.enumLabels ?? {}, fallback: '--' },
      majors: [], binding,
    }
  }
  if (hint.format === 'boolish') return { map: { kind: 'boolish' }, majors: [], binding }
  if (hint.format === 'text' || hint.format === 'json' || hint.format === 'dataUrl') {
    return { map: { kind: 'text' }, majors: [], binding }
  }
  const s = hint.suggested ?? { min: 0, max: 100, majors: [0, 25, 50, 75, 100] }
  return { map: { kind: 'linear', min: s.min, max: s.max }, majors: s.majors, binding }
}

type NovoElemento = {
  kind: ElementKind
  style?: string
  key?: string
  zone?: ZoneId
  name?: string
}

/** Chave inicial por tipo. Todas dentro do contrato — é o ponto todo deste arquivo. */
const CHAVE_PADRAO: Record<ElementKind, string> = {
  dial: 'car.basic.vehicle_speed',
  barLinear: 'car.ev_info.cur_battery_power_percentage',
  readout: 'car.basic.total_odometer',
  indicator: 'car.basic.head_light_status',
  media: 'app.media.title',
  decor: 'car.basic.vehicle_speed',
  image: 'car.basic.vehicle_speed',
  shape: 'car.basic.vehicle_speed',
  // Potência e regeneração: a leitura que mais pede histórico — o número sozinho não diz se o
  // carro vinha recuperando energia ou gastando.
  chart: 'car.ev_info.energy_output_percentage',
}

/**
 * Imagem que um elemento novo mostra até o usuário escolher a dele. SVG pequeno e embutido: um
 * elemento de imagem sem imagem nenhuma seria uma caixa invisível no painel.
 */
export const IMAGEM_DE_EXEMPLO =
  'data:image/svg+xml;base64,' +
  (typeof btoa === 'function'
    ? btoa('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160"><rect x="4" y="4" width="152" height="152" rx="28" fill="none" stroke="#8aa0b4" stroke-width="6" stroke-dasharray="14 10"/><circle cx="58" cy="60" r="14" fill="#8aa0b4"/><path d="M24 128l40-44 26 28 18-18 28 34z" fill="#8aa0b4"/></svg>')
    : '')

export function createElement(opts: NovoElemento, maxZ = 0): Element {
  // O anel é um mostrador de "quanto falta": nasce na carga da bateria, não na velocidade.
  const key = opts.key ?? (opts.kind === 'dial' && opts.style === 'ring' ? 'car.ev_info.cur_battery_power_percentage' : CHAVE_PADRAO[opts.kind])
  const { map, majors, binding } = mapForKey(key)
  const hint = HINT_BY_KEY.get(key)
  const nome = opts.name ?? hint?.label ?? 'Novo elemento'
  const base = {
    id: novoId('el'),
    name: nome,
    place: {
      zone: opts.zone ?? ('free' as ZoneId),
      anchor: 'center' as const,
      at: { x: 960, y: 360 },
      size: 'md' as const,
      scale: 1,
      z: maxZ + 1,
      opacity: 1,
    },
    visible: { when: 'always' as const },
  }

  switch (opts.kind) {
    case 'dial':
      if (opts.style === 'ring') {
        // Anel de progresso: o arco dá a volta inteira sobre um trilho apagado. Sem marcação, sem
        // ponteiro e sem zoom — é um mostrador de "quanto falta", não de "onde está o ponteiro".
        return {
          ...base, kind: 'dial',
          style: 'ring',
          sweep: { startDeg: -180, endDeg: 179.5 },
          binding: { ...binding, decimals: 0 }, map,
          face: { ...DEFAULT_FACE, enabled: false, ring: null, bezel: null },
          ticks: { ...DEFAULT_TICKS(majors.length ? majors : [0, 100]), showLabels: false },
          needle: { ...DEFAULT_NEEDLE, shape: 'none', glow: false, cap: 'none' },
          arc: { ...DEFAULT_ARC, enabled: true, radiusOffset: -14, width: 18, quantizeDeg: 1, track: '$track' },
          zoom: { ...DEFAULT_ZOOM, enabled: false },
          center: {
            text: text(56, 600, '$ink', { hostScaled: true, tracking: -1 }),
            label: nome,
            labelStyle: text(13, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
            offsetY: 0,
          },
        }
      }
      return {
        ...base, kind: 'dial',
        style: (opts.style as DialStyle) ?? 'faceNeedle',
        sweep: { startDeg: -125, endDeg: 125 },
        binding, map,
        face: { ...DEFAULT_FACE },
        ticks: DEFAULT_TICKS(majors.length ? majors : [0, 25, 50, 75, 100]),
        needle: { ...DEFAULT_NEEDLE },
        arc: { ...DEFAULT_ARC },
        zoom: { ...DEFAULT_ZOOM },
        center: {
          text: text(64, 500, '$ink', { hostScaled: true, tracking: -1 }),
          label: nome,
          labelStyle: text(14, 600, '$inkDim', { tracking: 3, transform: 'upper', tabular: false }),
          offsetY: 0.42,
        },
      }
    case 'barLinear':
      return {
        ...base, kind: 'barLinear',
        style: (opts.style as BarStyle) ?? 'fill',
        orientation: 'h',
        binding, map,
        track: solid('$track'),
        fill: solid('$accent'),
        segments: 12,
        // `offsetY` não faz sentido numa barra — a leitura fica na linha ao lado do trilho, não
        // sobre um cubo. Zero porque o tipo é compartilhado com a leitura central do mostrador.
        readout: { text: text(22, 600), label: nome, labelStyle: text(12, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }), offsetY: 0 },
      }
    case 'readout':
      if (opts.style === 'gear') {
        const marcha = mapForKey('car.basic.gear_status')
        return {
          ...base, kind: 'readout',
          name: opts.name ?? 'Marcha',
          style: 'gear',
          binding: marcha.binding,
          map: marcha.map,
          text: text(44, 700, '$ink', { tabular: false }),
          label: '',
          labelStyle: text(44, 500, '$inkFaint', { tabular: false }),
          icon: null,
        }
      }
      return {
        ...base, kind: 'readout',
        style: (opts.style as ReadoutStyle) ?? 'value',
        binding, map,
        text: text(40, 500, '$ink', { hostScaled: true }),
        label: nome,
        labelStyle: text(13, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
        icon: null,
      }
    case 'indicator':
      return {
        ...base, kind: 'indicator',
        style: (opts.style as IndicatorStyle) ?? 'icon',
        binding, map: map.kind === 'boolish' || map.kind === 'enum' ? map : { kind: 'boolish' },
        icon: 'headlight',
        onPaint: solid('$ok'),
        offPaint: solid('$inkFaint'),
        text: text(15, 700, '$ink', { tracking: 2, transform: 'upper', tabular: false }),
      }
    case 'media':
      return {
        ...base, kind: 'media',
        style: (opts.style as MediaStyle) ?? 'line',
        showArtwork: true,
        title: text(27, 600, '$ink', { tabular: false }),
        artist: text(20, 500, '$inkDim', { tabular: false }),
        panel: { fill: 'none' },
      }
    case 'decor':
      return {
        ...base, kind: 'decor',
        style: (opts.style as DecorStyle) ?? 'panel',
        paint: solid('$panel'),
        radius: 24,
        blur: 0,
      }
    case 'image':
      return {
        ...base, kind: 'image',
        name: opts.name ?? 'Imagem',
        place: { ...base.place, size: 'sm' },
        src: IMAGEM_DE_EXEMPLO,
        fileName: '',
        bytes: 0,
        aspect: 1,
        fit: 'contain',
        radius: 0,
      }
    case 'shape': {
      const forma = (opts.style as ShapeKind) ?? 'rect'
      return {
        ...base, kind: 'shape',
        name: opts.name ?? (forma === 'line' ? 'Divisória' : forma === 'circle' ? 'Círculo' : 'Moldura'),
        shape: forma,
        aspect: forma === 'line' ? 40 : forma === 'circle' ? 1 : 1.6,
        paint: forma === 'line' ? solid('$inkFaint') : { fill: 'none' },
        stroke: forma === 'line' ? null : { width: 2, color: '$inkFaint' },
        radius: forma === 'rect' ? 24 : 0,
      }
    }
    case 'chart':
      return {
        ...base, kind: 'chart',
        binding,
        map: map.kind === 'linear' ? map : { kind: 'linear', min: 0, max: 100 },
        windowSec: 120,
        line: '$accent',
        area: true,
        showValue: true,
        text: text(26, 600, '$ink', { hostScaled: true }),
        label: nome,
        labelStyle: text(12, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
      }
  }
}

const mask = (id: MaskId, enabled: boolean): MaskSpec => ({
  enabled, ...MASK_DEFAULTS[id], opacity: 1,
})

export function defaultMasks(): Record<MaskId, MaskSpec> {
  return {
    fuel: mask('fuel', false),
    battery: mask('battery', false),
    speed: { enabled: true, x: 0, y: 0, width: 640, height: 720, opacity: 1 },
    info: { enabled: true, x: 1280, y: 0, width: 640, height: 720, opacity: 1 },
    topCenter: { enabled: true, x: 660, y: 0, width: 600, height: 90, opacity: 1 },
  }
}

export function defaultBackground(): ThemeSpec['background'] {
  return {
    mode: 'gradient',
    presetId: 'midnight',
    solid: '#0b0c0e',
    gradientFrom: '#0b0c0e',
    gradientTo: '#1c2333',
    customPath: '',
    customDataUrl: '',
    passthrough: null,
  }
}

/**
 * Nome de pasta para o carro. Fixado uma vez e nunca mais alterado: não existe comando para
 * apagar a pasta antiga no veículo, então renomear deixaria um órfão para sempre.
 */
export function folderNameFrom(nome: string): string {
  const base = nome
    .trim().toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/(^-+|-+$)/g, '')
  return `${base || 'tema'}-${Date.now().toString(36)}`
}

/** A chave que entrega o limite da via, e a que diz se a placa ainda vale. */
export const LIMIT_KEY = 'car.map.tsr.nav_speed_limit'
export const LIMIT_STATUS_KEY = 'car.map.tsr.nav_speed_limit_sign_status'

/** Escala que segue a placa, com os padrões de um velocímetro urbano. */
export const signLimitMap = (vmaxPadrao = 120, teto = 260): ValueMap => ({
  kind: 'signLimit',
  min: 0,
  fallbackMax: vmaxPadrao,
  overshootMax: teto,
  rescaleMs: 650,
  limitKey: LIMIT_KEY,
  statusKey: LIMIT_STATUS_KEY,
})

/**
 * Condições prontas para as regras de cor. Todas saem de chaves que o carro entrega de verdade a
 * um tema — é a mesma exigência do resto do editor, e por isso a lista é esta e não uma maior.
 */
export const CONDICOES_PRONTAS: { id: string; label: string; dica: string; when: VisibilityRule }[] = [
  { id: 'farol', label: 'Farol aceso', dica: 'Telemetria do veículo, não a preferência de modo noturno.',
    when: { when: 'flag', key: 'car.basic.head_light_status', on: true } },
  { id: 'acimaLimite', label: 'Acima do limite da via', dica: 'Compara a velocidade com a placa lida pela câmera.',
    when: { when: 'overLimit', marginKmh: 0 } },
  { id: 'acimaDe', label: 'Acima de 120 km/h', dica: 'Limiar fixo, editável depois.',
    when: { when: 'threshold', key: 'car.basic.vehicle_speed', op: 'gt', value: 120 } },
  { id: 're', label: 'Marcha à ré', dica: 'Marcha igual a R.',
    when: { when: 'equals', key: 'car.basic.gear_status', value: '4' } },
  { id: 'estacionado', label: 'Estacionado (P)', dica: 'Marcha igual a P.',
    when: { when: 'equals', key: 'car.basic.gear_status', value: '3' } },
  { id: 'sport', label: 'Modo Sport', dica: 'Modo de condução do veículo.',
    when: { when: 'equals', key: 'car.drive_setting.drive_mode', value: '1' } },
  { id: 'eco', label: 'Modo Eco', dica: 'Modo de condução do veículo.',
    when: { when: 'equals', key: 'car.drive_setting.drive_mode', value: '2' } },
  { id: 'eletrico', label: 'Rodando em elétrico', dica: 'Modo de energia igual a EV.',
    when: { when: 'equals', key: 'car.ev_setting.power_model_config', value: '3' } },
  { id: 'bateriaBaixa', label: 'Bateria abaixo de 20%', dica: 'Carga da bateria de tração.',
    when: { when: 'threshold', key: 'car.ev_info.cur_battery_power_percentage', op: 'lt', value: 20 } },
  { id: 'combustivelBaixo', label: 'Combustível abaixo de 15%', dica: 'Nível de combustível restante.',
    when: { when: 'threshold', key: 'car.basic.remain_fuel_percentage', op: 'lt', value: 15 } },
  { id: 'regenerando', label: 'Regenerando', dica: 'Potência negativa: o carro está recuperando energia.',
    when: { when: 'threshold', key: 'car.ev_info.energy_output_percentage', op: 'lt', value: 0 } },
  { id: 'aviso', label: 'Aviso do veículo ativo', dica: 'O carro subiu um alerta por cima do painel.',
    when: { when: 'flag', key: 'warningActive', on: true } },
  { id: 'espDesligado', label: 'ESP desligado', dica: 'Controle de estabilidade desativado pelo motorista.',
    when: { when: 'flag', key: 'car.drive_setting.esp_enable', on: false } },
]

export function createColorRule(condicaoId = 'farol'): ColorRule {
  const pronta = CONDICOES_PRONTAS.find((c) => c.id === condicaoId) ?? CONDICOES_PRONTAS[0]
  return {
    id: novoId('rule'),
    enabled: true,
    label: pronta.label,
    tokens: ['ink'],
    color: '#ff9500',
    when: pronta.when,
  }
}

export function createTheme(nome: string, fontes: FontRef[] = ['sysSans']): ThemeSpec {
  const agora = Date.now()
  return {
    schema: 2,
    id: novoId('theme'),
    folderName: folderNameFrom(nome),
    name: nome,
    description: 'Tema criado no motor de temas do OPEN GW.',
    version: '1.0.0',
    createdAt: agora,
    updatedAt: agora,
    origin: null,
    canvas: { width: 1920, height: 720 },
    background: defaultBackground(),
    palette: { ...DEFAULT_PALETTE, tokens: { ...DEFAULT_PALETTE.tokens } },
    colorRules: [],
    fonts: fontes,
    displayModeConfig: true,
    speedScaleConfig: true,
    nativeMasks: defaultMasks(),
    appDefaultPosition: { x: 640, y: 95, width: 640, height: 560 },
    pages: [{ id: novoId('page'), name: 'Principal', elements: [] }],
    boot: { enabled: true, wordmark: nome.toUpperCase(), sweepMs: 1300 },
  }
}

export const newPage = (nome: string) => ({ id: novoId('page'), name: nome, elements: [] })
export { novoId }
