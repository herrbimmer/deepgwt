// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O modelo de um tema de cluster. Substitui `theme-editor/types.ts`.
//
// Uma regra orienta tudo aqui: o que o editor grava tem que ser suficiente para o motor montar o
// tema sozinho, e nada além disso. Sem HTML por elemento, sem CSS solto, sem geometria implícita.
// O arquivo `app.html` gerado é o motor (idêntico em todo tema) mais este objeto serializado.
//
// O que o modelo precisa saber expressar foi decidido lendo os dois temas escritos à mão que o
// app instala hoje — Cupertino Ultra e Stuttgart NTG 5.5. Cada campo esquisito aqui existe porque
// um dos dois depende dele; os comentários dizem qual.

/**
 * Uma cor. Ou um token da paleta (`'$ink'`), que troca sozinho entre dia/noite/farol, ou um
 * literal CSS (`'#0a84ff'`, `'rgba(0,0,0,.4)'`), que não troca nunca.
 */
export type Color = string

export const isToken = (c: Color) => c.startsWith('$')
export const tokenName = (c: Color) => c.slice(1)

/**
 * Os três estados de cor de um painel automotivo.
 *
 * `headlight` é automático (telemetria do farol) e `night` é escolha manual do motorista. Quando
 * os dois valem ao mesmo tempo, o manual vence — é o que o Stuttgart faz, e é a ordem certa:
 * quem pediu vermelho explicitamente não quer âmbar porque acendeu o farol.
 */
export type PaletteMode = 'day' | 'night' | 'headlight'

export type PaletteToken = {
  label: string
  day: Color
  night: Color
  /** Ausente = herda o valor de `day` quando o farol acende. */
  headlight?: Color
}

export type Palette = {
  tokens: Record<string, PaletteToken>
  /**
   * Ler `--night-tint`/`--night-glow`, que o app injeta em `documentElement` conforme a
   * preferência "cor do modo noturno" (âmbar, vermelho ou misto). Com isto ligado, o tema
   * acompanha a escolha do usuário no carro em vez de fixar a própria cor.
   */
  headlightFromHostVars: boolean
}

/** Como uma superfície é pintada. */
export type Paint =
  | { fill: 'none' }
  | { fill: 'solid'; color: Color }
  | { fill: 'linear'; from: Color; to: Color; angleDeg: number }
  /** Disco do mostrador: `cx`/`cy` em 0..1 deslocam o brilho (Cupertino usa 0.34 / 0.28). */
  | { fill: 'radial'; from: Color; to: Color; cx: number; cy: number; stopAt: number }
  /**
   * Gradiente radial em `userSpaceOnUse` centrado no cubo do ponteiro. Como o centro do gradiente
   * é o eixo de rotação, girar o ponteiro não muda um pixel dele — o Stuttgart depende disso, e
   * trocar por um `radial` comum faz o gradiente escorrer junto com o ponteiro.
   */
  | { fill: 'hubRadial'; inner: Color; mid: Color; outer: Color; midAt: number }

/** Como o texto cru do barramento vira número, ângulo ou palavra. */
export type ValueMap =
  | { kind: 'linear'; min: number; max: number }
  /**
   * Faixa não-linear. `t` é a fração do arco (0..1). O medidor de potência do Stuttgart é
   * `[{v:-100,t:0},{v:0,t:1/6},{v:100,t:1}]`: o zero cai a um sexto do arco, então a regeneração
   * ganha 41,67° e a potência 208,33°.
   */
  | { kind: 'piecewise'; points: { v: number; t: number }[] }
  /**
   * Duas faixas com histerese, como o velocímetro do Cupertino: 0-120 até passar de `upAt`, aí
   * 0-260 até cair abaixo de `downAt`. As duas bordas diferentes são o que impede o mostrador de
   * piscar entre faixas a 120 km/h cravados.
   */
  | { kind: 'dualRange'; min: number; low: number; high: number; upAt: number; downAt: number; rescaleMs: number }
  /**
   * Escala que segue o limite da via lido pela câmera (TSR).
   *
   * Enquanto o motorista está dentro do limite, o fim do arco É o limite da via — um painel de 40
   * numa rua de 40. Ao passar, a escala abre até `overshootMax` e as marcações acima do limite
   * ficam na cor de alerta, com brilho.
   *
   * `fallbackMax` é o que vale quando não há placa válida; sem ele, uma via sem sinalização
   * deixaria o velocímetro com fim de escala zero.
   */
  | {
      kind: 'signLimit'
      min: number
      fallbackMax: number
      overshootMax: number
      rescaleMs: number
      limitKey: string
      statusKey: string | null
    }
  | { kind: 'enum'; labels: Record<string, string>; fallback: string }
  /** `'1' | 'true' | 'on' | 'yes' | 'ligado'`, sem diferenciar maiúsculas. */
  | { kind: 'boolish' }
  | { kind: 'text' }

export type Binding = {
  key: string
  /** Aplicado antes de formatar. `scale: 0.001` transforma RPM em milhares. */
  transform?: { scale?: number; offset?: number; abs?: boolean }
  decimals?: number
  unit?: string
  /** O que aparece enquanto o carro não mandou nada. */
  fallback?: string
  /**
   * Suavização da LEITURA, em milissegundos, quando a chave chega em degraus grandes e espaçados.
   *
   * Não é uma animação: é um filtro exponencial de primeira ordem sobre o valor, aplicado antes
   * de mapear para ângulo e para texto. Por isso o ponteiro E o número acompanham juntos — uma
   * transição de CSS moveria só o desenho e deixaria o número saltando sozinho.
   *
   * O valor é o tempo para cobrir ~95% de um degrau. Custa esse mesmo tempo de atraso sobre o
   * dado bruto, então NÃO é padrão: só faz sentido onde o degrau do carro é maior do que o
   * movimento que o mostrador consegue mostrar.
   *
   * Ausente ou 0 desliga. `prefers-reduced-motion` também desliga.
   */
  smoothMs?: number

  /**
   * O degrau do DESENHO, nas unidades do dado, quando [smoothMs] está ligado.
   *
   * O filtro trabalha em precisão dupla; este campo diz de quanto em quanto o resultado dele
   * chega ao mostrador. `0.25` num velocímetro quer dizer que o ponteiro anda de quarto em quarto
   * de km/h entre duas leituras do carro, em vez de saltar o km/h inteiro de uma vez.
   *
   * Também é o que impede o filtro de reescrever o `transform` do ponteiro sessenta vezes por
   * segundo por um movimento que não chega a um pixel: abaixo de meio degrau o desenho não muda,
   * e o filtro para.
   *
   * Ausente ou 0 mantém o comportamento antigo — o desenho segue o filtro sem degrau.
   */
  smoothStep?: number
}

export type FontRef = 'sysSans' | 'sysCondensed' | 'sysSerif' | 'sysMono' | 'exoV' | 'michroma'

export type TextStyle = {
  font: FontRef
  sizePx: number
  weight: number
  tracking: number
  transform: 'none' | 'upper'
  /** `tabular-nums`. Obrigatório em qualquer número que muda, senão os dígitos dançam. */
  tabular: boolean
  /**
   * Emite `calc(Npx * var(--gauge-num-scale, 1))`, acompanhando o ajuste de tamanho de número que
   * o motorista faz no app (0,7 a 1,4). Ligado nas leituras grandes, desligado no resto.
   */
  hostScaled: boolean
  color: Color
  /**
   * Estilo compartilhado de onde este texto veio (`ThemeSpec.textStyles`). Só o editor lê: mudar o
   * estilo compartilhado reescreve todo texto que aponta para ele, então o payload continua levando
   * o estilo inteiro em cada elemento e o motor não precisa saber que isto existe.
   */
  shared?: string
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

export type SizeToken = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

export const SIZE_LABEL: Record<SizeToken, string> = {
  xs: 'PP', sm: 'P', md: 'M', lg: 'G', xl: 'GG',
}

/**
 * Onde o elemento mora. `zone` só orienta encaixe e onde um elemento novo nasce; não é uma
 * restrição — `at` é a única coordenada de verdade.
 */
export type ZoneId = 'left' | 'right' | 'topCenter' | 'centre' | 'free'

export type Anchor =
  | 'center' | 'topLeft' | 'topCenter' | 'topRight'
  | 'midLeft' | 'midRight' | 'bottomLeft' | 'bottomCenter' | 'bottomRight'

/**
 * Sem largura e sem altura de propósito. O tamanho sai da tabela por tipo mais um ajuste fino,
 * porque altura nunca foi dimensão de layout neste gerador — era proxy de tamanho de fonte.
 *
 * A âncora é um ponto, não um canto: mudar de tamanho nunca desloca o centro visual, o que
 * importa porque uma mudança de tamanho pode ser limitada por colisão com uma zona do veículo.
 */
export type Placement = {
  zone: ZoneId
  anchor: Anchor
  at: { x: number; y: number }
  size: SizeToken
  /** 0,60 a 1,40. O máximo real é calculado ao vivo pela colisão. */
  scale: number
  z: number
  opacity: number
}

export type TickConfig = {
  /** Podem ser irregulares: o Stuttgart usa `[-100,-50,0,20,40,60,80,100]`. */
  majors: number[]
  /** Marcas entre duas maiores. 4 nos dois temas de fábrica (3 traços intermediários). */
  subdivisions: number
  showLabels: boolean
  /** Estilo do número da marcação. É este texto que a animação de zoom faz crescer. */
  labelStyle: TextStyle
  /** Quanto o número fica para dentro da ponta da marcação maior. 40 nos dois temas de fábrica. */
  labelInset: number
  len: { major: number; minor: number; micro: number }
  width: { major: number; minor: number; micro: number }
  /** Faixa de corte, regeneração, o que for: pinta marca e rótulo dentro do intervalo. */
  zones: { from: number; to: number; token: string }[]
}

export type NeedleConfig = {
  shape: 'line' | 'taper' | 'none'
  innerR: number
  /** Quanto o ponteiro para antes da borda. 54 nos dois temas. */
  outerInset: number
  width: number
  paint: Paint
  cap: 'none' | 'dot' | 'ring' | 'ringDot'
  glow: boolean
}

export type FaceConfig = {
  /**
   * Desligado no Stuttgart, e não por esquecimento: o mostrador é transparente para o papel de
   * parede aparecer através dele. Ligar muda a identidade do tema, então o padrão respeita o
   * original e o editor deixa ligar quando o usuário quiser.
   */
  enabled: boolean
  paint: Paint
  /**
   * Vidro: o raio, em pixels, do desfoque aplicado ao que está ATRÁS do disco.
   *
   * O desfoque não vive no `<circle>` do SVG — `backdrop-filter` é propriedade de caixa CSS e o
   * Chromium a ignora, sem erro nenhum, num filho de SVG. O motor desenha um `<div>` redondo
   * atrás do SVG; `paint` continua sendo a tinta, pintada POR CIMA dele. É essa ordem que faz
   * vidro: desfoca o que está atrás, depois tinge.
   *
   * O que entra no desfoque é só o que está pintado abaixo do mostrador — papel de parede e
   * decorações de `z` menor. Ponteiro, marcações e números são desenhados depois e ficam de
   * fora, então girar o ponteiro não refaz o desfoque.
   *
   * Ausente ou 0 desliga, e é o padrão: desfocar é a coisa mais cara que um tema pode pedir ao
   * WebView do carro.
   */
  blurPx?: number
  ring: { width: number; color: Color } | null
  bezel: { width: number; color: Color } | null
}

export type ArcConfig = {
  enabled: boolean
  radiusOffset: number
  width: number
  /**
   * Passo mínimo, em graus, para reescrever o `d` do arco. Zero re-rasteriza o `drop-shadow` a
   * cada quadro — é contrato de desempenho, não estilo. O emissor força o mínimo de 0,5.
   */
  quantizeDeg: number
  /** Segundo arco a partir de `at`, para pintar a faixa de corte com outra cor. */
  split: { at: number; beforeToken: string; afterToken: string } | null
  /**
   * Trilho do anel: o arco inteiro, apagado, atrás do arco de valor. Só o anel de progresso usa;
   * ausente não desenha nada, que é o comportamento de todo mostrador anterior a ele.
   */
  track?: Color | null
}

/**
 * A animação de zoom: o número da marcação mais próxima do valor atual cresce.
 *
 * Não confundir com as outras duas coisas que parecem zoom neste sistema — o `--gauge-num-scale`,
 * que é um multiplicador estático vindo do app, e a troca de faixa do velocímetro, que reposiciona
 * as marcações. Só esta aqui liga e desliga.
 */
export type ZoomConfig = {
  enabled: boolean
  scale: number
  /** Trocado de uma vez, nunca interpolado: interpolar peso de fonte custa relayout por quadro. */
  weight: number
  durationMs: number
  easing: string
}

export type IconId =
  | 'headlight' | 'highBeam' | 'fogFront' | 'fogRear' | 'handbrake' | 'seatbelt' | 'epb'
  | 'espOff' | 'tpms' | 'charging' | 'engine' | 'turnLeft' | 'turnRight' | 'hazard'
  | 'wiper' | 'door' | 'key' | 'snowflake' | 'ready' | 'eco' | 'sport' | 'fuel' | 'battery'
  | 'music' | 'road' | 'clock'

export type DialStyle =
  | 'ticksNeedle' | 'faceNeedle' | 'arcOnly' | 'segmentRing' | 'halfArc' | 'minimalIndex'
  /** Anel de progresso: o arco dá a volta inteira sobre um trilho, sem marcação nem ponteiro. */
  | 'ring'
export type BarStyle = 'fill' | 'segments' | 'centreOut'
/** `gear`: a fila P R N D com a marcha atual acesa e as outras apagadas. */
export type ReadoutStyle = 'value' | 'stat' | 'text' | 'clock' | 'odometer' | 'gear'
export type IndicatorStyle = 'icon' | 'badge'
export type MediaStyle = 'card' | 'line'
export type DecorStyle = 'panel' | 'glow' | 'divider'
export type ShapeKind = 'rect' | 'circle' | 'line'

export type ElementKind =
  | 'dial' | 'barLinear' | 'readout' | 'indicator' | 'media' | 'decor'
  | 'image' | 'shape' | 'chart'

/**
 * Uma condição do veículo. Serve a duas coisas: quando um elemento aparece, e quando uma regra de
 * cor entra em vigor.
 *
 * `flag` existe separada de `equals` porque metade das chaves booleanas do contrato tem formato
 * NÃO CONFIRMADO no veículo: comparar com a string '1' na mão é a aposta que já deixou o ícone de
 * farol sem acender. `flag` passa pelo mesmo `parseBoolish` do resto do motor.
 *
 * `overLimit` compara a velocidade com o limite da via lido pela câmera, que é uma conta entre
 * duas chaves e não caberia em nenhuma das outras formas.
 */
export type VisibilityRule =
  | { when: 'always' }
  | { when: 'nonEmpty'; key: string }
  | { when: 'equals'; key: string; value: string }
  | { when: 'flag'; key: string; on: boolean }
  | { when: 'threshold'; key: string; op: 'gt' | 'lt' | 'gte' | 'lte'; value: number }
  | { when: 'overLimit'; marginKmh: number }

/**
 * Trocar a cor de um ou mais tokens da paleta enquanto uma condição do veículo vale.
 *
 * Fica acima dos três estados de cor (dia, noite, farol) em vez de ser um quarto estado: uma regra
 * pode valer em qualquer um deles, e várias podem valer ao mesmo tempo. A última regra ativa da
 * lista vence, então a ordem na lista é a prioridade — e ela é editável.
 */
export type ColorRule = {
  id: string
  enabled: boolean
  label: string
  /** Nomes de token da paleta que esta regra sobrescreve. */
  tokens: string[]
  /** Cor literal. Um token aqui não faria sentido: a regra existe para SAIR da paleta. */
  color: Color
  when: VisibilityRule
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

type Base = {
  id: string
  name: string
  place: Placement
  visible: VisibilityRule
  /** Grupo da lista de camadas (`ThemePage.groups`). Só organiza o editor; o motor não vê. */
  groupId?: string
  /** Travado: o canvas não arrasta nem seleciona por clique. Continua editável pela lista. */
  locked?: boolean
  /**
   * Oculto: fica no tema salvo, mas não vai para o `app.html`. É o "olho" da lista de camadas —
   * tirar um elemento do painel sem perder o que foi configurado nele.
   */
  hidden?: boolean
}

/**
 * A leitura dentro de um mostrador redondo. `offsetY` é a fração do raio pela qual o bloco desce
 * a partir do cubo — os dois temas de fábrica põem o número bem abaixo do centro, e centralizar
 * de verdade além de ficar estranho joga o número em cima das zonas do OEM.
 */
export type CenterText = { text: TextStyle; label: string; labelStyle: TextStyle; offsetY: number } | null

export type DialElement = Base & {
  kind: 'dial'
  style: DialStyle
  sweep: { startDeg: number; endDeg: number }
  binding: Binding
  map: ValueMap
  face: FaceConfig
  ticks: TickConfig
  needle: NeedleConfig
  arc: ArcConfig
  zoom: ZoomConfig
  center: CenterText
}

export type BarElement = Base & {
  kind: 'barLinear'
  style: BarStyle
  orientation: 'h' | 'v'
  binding: Binding
  map: ValueMap
  track: Paint
  fill: Paint
  /** Só em `segments`. */
  segments: number
  readout: CenterText
}

export type ReadoutElement = Base & {
  kind: 'readout'
  style: ReadoutStyle
  /** `null` no relógio, que não lê nada do carro. */
  binding: Binding | null
  map: ValueMap
  text: TextStyle
  label: string
  labelStyle: TextStyle
  icon: IconId | null
}

export type IndicatorElement = Base & {
  kind: 'indicator'
  style: IndicatorStyle
  binding: Binding
  map: ValueMap
  icon: IconId | null
  onPaint: Paint
  offPaint: Paint
  text: TextStyle
}

export type MediaElement = Base & {
  kind: 'media'
  style: MediaStyle
  showArtwork: boolean
  title: TextStyle
  artist: TextStyle
  panel: Paint
}

export type DecorElement = Base & {
  kind: 'decor'
  style: DecorStyle
  paint: Paint
  radius: number
  blur: number
}

/** Uma imagem do usuário (logo, selo). Vai embutida no `app.html` como `data:` URL. */
export type ImageElement = Base & {
  kind: 'image'
  /** `data:image/...;base64,...`. Embutida para o tema continuar sendo um arquivo que se basta. */
  src: string
  fileName: string
  bytes: number
  /** Largura ÷ altura da imagem original. É o que dá a forma da caixa sem largura e altura soltas. */
  aspect: number
  fit: 'contain' | 'cover'
  radius: number
}

/** Moldura, divisória ou disco — sem o truque de usar uma "decoração" para isso. */
export type ShapeElement = Base & {
  kind: 'shape'
  shape: ShapeKind
  /** Largura ÷ altura da caixa. Numa linha é o comprimento sobre a espessura. */
  aspect: number
  paint: Paint
  stroke: { width: number; color: Color } | null
  radius: number
}

/**
 * Mini-histórico: a linha dos últimos `windowSec` segundos de uma chave.
 *
 * O desenho é refeito no máximo uma vez por segundo, por um único temporizador do motor para todos
 * os gráficos — é essa a conta de custo que deixa um gráfico caber no WebView do painel.
 */
export type ChartElement = Base & {
  kind: 'chart'
  binding: Binding
  map: ValueMap
  windowSec: number
  line: Color
  area: boolean
  showValue: boolean
  text: TextStyle
  label: string
  labelStyle: TextStyle
}

export type Element =
  | DialElement | BarElement | ReadoutElement | IndicatorElement | MediaElement | DecorElement
  | ImageElement | ShapeElement | ChartElement

/** Um grupo da lista de camadas. Só organiza o editor: não existe no `app.html`. */
export type LayerGroup = { id: string; name: string; collapsed?: boolean }

export type ThemePage = {
  id: string
  name: string
  elements: Element[]
  groups?: LayerGroup[]
  /**
   * Página que abre sozinha: quando a condição passa a valer, o painel vai para esta página; quando
   * deixa de valer, volta para onde estava. A troca acontece na BORDA da condição, então o motorista
   * continua podendo mudar de página pelo volante enquanto ela vale.
   */
  showWhen?: VisibilityRule | null
  /** Imagem de fundo só desta página, por cima do fundo do tema. */
  background?: { src: string; fileName: string; bytes: number } | null
}

/** Um estilo de texto com nome, aplicado a vários elementos ao mesmo tempo. */
export type SharedTextStyle = { label: string; style: TextStyle }

/** Uma máscara nativa. Os nomes aqui são os do `theme.xml`; o runtime usa outros (ver zones.ts). */
export type MaskSpec = {
  enabled: boolean
  x: number
  y: number
  width: number
  height: number
  opacity: number
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

export type MaskId = 'fuel' | 'battery' | 'speed' | 'info' | 'topCenter'

export type Rect = { x: number; y: number; width: number; height: number }

export type BackgroundMode = 'preset' | 'solid' | 'gradient' | 'custom' | 'passthrough'

export type BackgroundConfig = {
  mode: BackgroundMode
  presetId: string
  solid: string
  gradientFrom: string
  gradientTo: string
  customPath: string
  /** `data:` URL montada na hora da escolha, para não depender do escopo de `asset://` do Tauri. */
  customDataUrl: string
  /**
   * Modo `passthrough`: bytes originais, nome original, sem passar por canvas.
   *
   * Existe por causa do `car-bg.jpg` do Stuttgart, que são 144.614 bytes de PNG com extensão
   * `.jpg`. Recodificar pela extensão quebra o arquivo, e rasterizar de novo um papel de parede
   * que já está pronto só perde qualidade.
   */
  passthrough: { fileName: string; sourceUrl: string } | null
}

export type ThemeSpec = {
  schema: 2
  id: string
  /**
   * Nome da pasta no carro. Fixado na criação e nunca renomeado: não existe como apagar uma pasta
   * antiga no veículo, então renomear deixaria órfãos acumulando.
   */
  folderName: string
  name: string
  description: string
  /** Precisa crescer a cada envio, senão o app ignora a cópia nova. Ver `emitThemeXml`. */
  version: string
  createdAt: number
  updatedAt: number
  /** De qual preset este tema saiu. Só informativo. */
  origin: string | null

  canvas: { width: 1920; height: 720 }
  background: BackgroundConfig
  palette: Palette
  /** Trocas de cor condicionais, aplicadas por cima da paleta. Ver `ColorRule`. */
  colorRules: ColorRule[]
  fonts: FontRef[]
  /** Emite o combo Dia/Noite em `<configurations>`, que aparece nos ajustes do tema no app. */
  displayModeConfig: boolean
  /**
   * Emite o combo "Escala do velocímetro" nos ajustes do tema DENTRO do carro.
   *
   * Existe porque seguir a placa é uma decisão de quem está dirigindo, não de quem desenhou o
   * painel: com isto ligado, o motorista troca entre a escala do tema, uma VMAX fixa e a escala que
   * acompanha a via sem precisar reenviar o tema do computador.
   */
  speedScaleConfig: boolean

  nativeMasks: Record<MaskId, MaskSpec>
  appDefaultPosition: Rect

  pages: ThemePage[]
  boot: { enabled: boolean; wordmark: string; sweepMs: number } | null
  /**
   * Varredura dos ponteiros ao carregar: do mínimo ao máximo e de volta, uma vez. Desligada por
   * `prefers-reduced-motion`, como qualquer outra animação do motor.
   */
  welcomeSweep?: boolean
  /** Estilos de texto nomeados. Ver `TextStyle.shared`. */
  textStyles?: Record<string, SharedTextStyle>
}

export const CANVAS_WIDTH = 1920
export const CANVAS_HEIGHT = 720
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
