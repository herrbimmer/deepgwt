// As zonas do canvas: o que o veículo desenha por cima do tema, e onde um elemento novo nasce.
//
// Ordem das camadas no cluster, de baixo para cima (`InstrumentProjector2.initNativeMaskViews`
// insere o contêiner de máscaras no índice 0 e o WebView depois):
//
//   cluster OEM  ->  máscaras nativas  ->  WebView do tema  ->  primeiro plano OEM
//
// Ou seja: o tema fica ACIMA das máscaras. A única coisa que cobre o tema é o primeiro plano
// composto pelo OEM, e o contrato só mapeia três indicadores. Era esta a confusão do editor
// antigo, que marcava a faixa central como proibida — aquilo não é telltale nenhuma, é a janela
// de app projetado, lida de `<AppDefaultPosition>`, e é ocupada só quando há app projetado.

import type { MaskId, MaskSpec, Rect, ThemeSpec, ZoneId } from './spec'
import { CANVAS_HEIGHT, CANVAS_WIDTH } from './spec'

export type ForbiddenZone = {
  id: string
  label: string
  /** O que o motorista realmente vê. Menor que a zona de segurança. */
  visible: Rect
  /** O que não pode receber conteúdo legível. É esta que bloqueia. */
  safe: Rect
  why: string
}

/**
 * Os três indicadores do OEM, compostos acima do WebView. Coordenadas conferidas no nosso mapa
 * de luzes do painel (`docs/operations/mapa-de-luzes-do-painel.md` e
 * `docs/operations/mapa-de-luzes/mapa-luzes-1920x720.json`), medido em fotos do carro.
 *
 * As três caem DENTRO do mostrador esquerdo de qualquer tema com velocímetro redondo grande. Isso
 * é normal e os dois temas de fábrica fazem assim: o miolo do mostrador é transparente e o
 * ponteiro é fino. O que não pode é marcação, número, ícone ou texto — ver `collision.ts`.
 */
export const OEM_FORBIDDEN: ForbiddenZone[] = [
  {
    id: 'ready',
    label: 'READY',
    visible: { x: 258, y: 335, width: 64, height: 20 },
    safe: { x: 238, y: 315, width: 104, height: 60 },
    why: 'O veículo escreve READY aqui, por cima do tema. Nada legível pode ficar embaixo.',
  },
  {
    id: 'trafficSign',
    label: 'Placa de velocidade',
    visible: { x: 247, y: 497, width: 46, height: 46 },
    safe: { x: 225, y: 475, width: 90, height: 90 },
    why: 'A placa de limite detectada pela câmera aparece aqui, acima de tudo que o tema desenha.',
  },
  {
    id: 'esp',
    label: 'ESP',
    visible: { x: 350.4, y: 466, width: 59.2, height: 48 },
    safe: { x: 330, y: 445, width: 100, height: 100 },
    why: 'O indicador de ESP é composto pelo veículo e o tema não consegue escondê-lo.',
  },
]

export type SoftZone = { id: string; label: string; rect: Rect; why: string }

/**
 * Avisos, nunca bloqueios.
 *
 * A janela de app sai do próprio tema em vez de uma constante: um tema que mova a janela move o
 * aviso junto. Era o erro do `NATIVE_RESERVED_RECT` antigo, fixo em `{640,90,640,630}`.
 */
export function softZonesOf(spec: ThemeSpec): SoftZone[] {
  return [
    {
      id: 'appWindow',
      label: 'Janela de app no painel',
      rect: spec.appDefaultPosition,
      why: 'Quando há app projetado no painel (mapa, Android Auto), ele ocupa esta área por cima do tema.',
    },
  ]
}

/**
 * Outras luzes-espia do OEM estão no mapa de luzes (`docs/operations/mapa-de-luzes-do-painel.md`);
 * aqui ficam só as três que bloqueiam conteúdo. Fingir que as três conhecidas são a lista completa seria o mesmo
 * tipo de mentira que as 47 chaves mortas do seletor antigo.
 */
export const UNMAPPED_TELLTALES_NOTE =
  'Outras luzes-espia do veículo ainda não tiveram a posição mapeada. As três zonas marcadas são as ' +
  'confirmadas; conteúdo crítico no resto do painel ainda pode encostar em algo não mapeado.'

/** Nome da máscara em `theme.xml` (chave) contra o nome que `setNativeMaskState` aceita (valor). */
export const MASK_RUNTIME_NAME: Record<MaskId, string> = {
  fuel: 'fuel',
  battery: 'battery',
  speed: 'speed',
  info: 'info',
  topCenter: 'topcenter',
}

/** Padrões do parser do app (`ThemeManager.parseThemeXml`), não os dos temas de fábrica. */
export const MASK_DEFAULTS: Record<MaskId, Rect> = {
  fuel: { x: 0, y: 520, width: 380, height: 200 },
  battery: { x: 1540, y: 520, width: 380, height: 200 },
  speed: { x: 0, y: 0, width: 600, height: 720 },
  info: { x: 1320, y: 0, width: 600, height: 720 },
  topCenter: { x: 710, y: 0, width: 500, height: 67 },
}

export const maskRect = (m: MaskSpec): Rect => ({ x: m.x, y: m.y, width: m.width, height: m.height })

export type PlacementZone = { id: ZoneId; label: string; rect: Rect }

/**
 * Onde um elemento novo nasce e para onde o arraste encaixa. Sai das máscaras declaradas pelo
 * próprio tema — um tema que não mascare a metade direita simplesmente não oferece aquela zona.
 */
export function placementZones(spec: ThemeSpec): PlacementZone[] {
  const zonas: PlacementZone[] = []
  const m = spec.nativeMasks
  if (m.speed.enabled) zonas.push({ id: 'left', label: 'Mostrador esquerdo', rect: maskRect(m.speed) })
  if (m.info.enabled) zonas.push({ id: 'right', label: 'Mostrador direito', rect: maskRect(m.info) })
  if (m.topCenter.enabled) zonas.push({ id: 'topCenter', label: 'Barra superior', rect: maskRect(m.topCenter) })
  zonas.push({ id: 'centre', label: 'Centro', rect: spec.appDefaultPosition })
  zonas.push({ id: 'free', label: 'Painel inteiro', rect: { x: 0, y: 0, width: CANVAS_WIDTH, height: CANVAS_HEIGHT } })
  return zonas
}

export function zoneRect(spec: ThemeSpec, id: ZoneId): Rect {
  const z = placementZones(spec).find((v) => v.id === id)
  return z ? z.rect : { x: 0, y: 0, width: CANVAS_WIDTH, height: CANVAS_HEIGHT }
}
