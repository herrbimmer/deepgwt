// Operações sobre VÁRIOS elementos: mover junto, alinhar, distribuir, agrupar, copiar e colar.
//
// Tudo aqui é função pura sobre a lista de elementos de uma página, para o teste conseguir
// conferir a conta sem montar a tela. A regra das zonas do veículo vale igual para um elemento e
// para vinte: sem modo livre, um elemento que cairia em cima do READY, da placa ou do ESP não vai
// — e a operação diz quantos ficaram para trás e por quê, em vez de empurrar em silêncio.

import type { Element, LayerGroup, Rect, ThemePage } from './spec'
import { CANVAS_HEIGHT, CANVAS_WIDTH } from './spec'
import { rectFor } from './sizes'
import { firstBlocker, isLegal, movedTo } from './collision'
import { novoId } from './defaults'

export type Movimento = { id: string; at: { x: number; y: number } }

/** O retângulo que envolve todos os elementos dados. */
export function limitesDe(els: Element[]): Rect | null {
  if (!els.length) return null
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity
  for (const el of els) {
    const r = rectFor(el)
    x0 = Math.min(x0, r.x)
    y0 = Math.min(y0, r.y)
    x1 = Math.max(x1, r.x + r.width)
    y1 = Math.max(y1, r.y + r.height)
  }
  return { x: x0, y: y0, width: x1 - x0, height: y1 - y0 }
}

export type Resultado = {
  elementos: Element[]
  /** Os que não puderam ir, com a zona do veículo que barrou. */
  barrados: { id: string; nome: string; zona: string }[]
}

/**
 * Aplica movimentos elemento a elemento. Um movimento que deixaria o elemento em cima de uma zona
 * do veículo é recusado sozinho: os outros vão. É o comportamento de alinhar e distribuir — ali
 * cada elemento tem destino próprio, e travar todos por causa de um seria pior.
 */
export function aplicarMovimentos(els: Element[], movimentos: Movimento[]): Resultado {
  const porId = new Map(movimentos.map((m) => [m.id, m.at]))
  const barrados: Resultado['barrados'] = []
  const elementos = els.map((el) => {
    const at = porId.get(el.id)
    if (!at) return el
    const movido = movedTo(el, { x: Math.round(at.x), y: Math.round(at.y) })
    if (isLegal(movido)) return movido
    barrados.push({ id: el.id, nome: el.name, zona: firstBlocker(movido)?.zone.label ?? 'zona do veículo' })
    return el
  })
  return { elementos, barrados }
}

/**
 * Desloca TODOS os escolhidos pelo mesmo vetor, ou nenhum. Diferente de alinhar: aqui o grupo se
 * move como um bloco, e deixar metade para trás desmontaria o que a pessoa montou.
 */
export function deslocarJuntos(els: Element[], ids: string[], dx: number, dy: number): Element[] | null {
  const alvo = new Set(ids)
  const saida: Element[] = []
  for (const el of els) {
    if (!alvo.has(el.id)) {
      saida.push(el)
      continue
    }
    const movido = movedTo(el, { x: Math.round(el.place.at.x + dx), y: Math.round(el.place.at.y + dy) })
    if (!isLegal(movido)) return null
    saida.push(movido)
  }
  return saida
}

export type Alinhamento = 'esquerda' | 'centroH' | 'direita' | 'topo' | 'centroV' | 'base'

/**
 * Alinha as bordas (ou os centros) dos escolhidos. Com um elemento só, a referência é o painel
 * inteiro — "alinhar à esquerda" um elemento sozinho quer dizer encostá-lo na borda do painel.
 */
export function alinhar(els: Element[], ids: string[], como: Alinhamento): Resultado {
  const escolhidos = els.filter((e) => ids.includes(e.id))
  const ref: Rect | null = escolhidos.length > 1
    ? limitesDe(escolhidos)
    : { x: 0, y: 0, width: CANVAS_WIDTH, height: CANVAS_HEIGHT }
  if (!ref) return { elementos: els, barrados: [] }
  const movimentos: Movimento[] = escolhidos.map((el) => {
    const r = rectFor(el)
    let dx = 0, dy = 0
    if (como === 'esquerda') dx = ref.x - r.x
    if (como === 'direita') dx = ref.x + ref.width - (r.x + r.width)
    if (como === 'centroH') dx = ref.x + ref.width / 2 - (r.x + r.width / 2)
    if (como === 'topo') dy = ref.y - r.y
    if (como === 'base') dy = ref.y + ref.height - (r.y + r.height)
    if (como === 'centroV') dy = ref.y + ref.height / 2 - (r.y + r.height / 2)
    return { id: el.id, at: { x: el.place.at.x + dx, y: el.place.at.y + dy } }
  })
  return aplicarMovimentos(els, movimentos)
}

/**
 * Espaço igual entre as caixas, do primeiro ao último na ordem em que já estão. As duas pontas
 * não se mexem — é o que "distribuir" quer dizer em todo editor de desenho.
 */
export function distribuir(els: Element[], ids: string[], eixo: 'h' | 'v'): Resultado {
  const escolhidos = els.filter((e) => ids.includes(e.id))
  if (escolhidos.length < 3) return { elementos: els, barrados: [] }
  const comCaixa = escolhidos.map((el) => ({ el, r: rectFor(el) }))
  comCaixa.sort((a, b) => (eixo === 'h' ? a.r.x - b.r.x : a.r.y - b.r.y))
  const inicio = eixo === 'h' ? comCaixa[0].r.x : comCaixa[0].r.y
  const ultimo = comCaixa[comCaixa.length - 1].r
  const fim = eixo === 'h' ? ultimo.x + ultimo.width : ultimo.y + ultimo.height
  const ocupado = comCaixa.reduce((n, c) => n + (eixo === 'h' ? c.r.width : c.r.height), 0)
  const vao = (fim - inicio - ocupado) / (comCaixa.length - 1)
  let cursor = inicio
  const movimentos: Movimento[] = comCaixa.map(({ el, r }) => {
    const d = cursor - (eixo === 'h' ? r.x : r.y)
    cursor += (eixo === 'h' ? r.width : r.height) + vao
    return { id: el.id, at: eixo === 'h' ? { x: el.place.at.x + d, y: el.place.at.y } : { x: el.place.at.x, y: el.place.at.y + d } }
  })
  return aplicarMovimentos(els, movimentos)
}

// --- grupos ---------------------------------------------------------------------------------

export function agrupar(page: ThemePage, ids: string[], nome?: string): { page: ThemePage; grupo: LayerGroup } {
  const grupos = page.groups ?? []
  const grupo: LayerGroup = { id: novoId('grp'), name: nome ?? `Grupo ${grupos.length + 1}` }
  const alvo = new Set(ids)
  return {
    grupo,
    page: {
      ...page,
      groups: [...grupos, grupo],
      elements: page.elements.map((el) => (alvo.has(el.id) ? ({ ...el, groupId: grupo.id } as Element) : el)),
    },
  }
}

export function desagrupar(page: ThemePage, grupoId: string): ThemePage {
  return {
    ...page,
    groups: (page.groups ?? []).filter((g) => g.id !== grupoId),
    elements: page.elements.map((el) => {
      if (el.groupId !== grupoId) return el
      const { groupId: _fora, ...resto } = el
      return resto as Element
    }),
  }
}

/** Grupos sem nenhum elemento somem; elementos que apontam para grupo inexistente saem dele. */
export function limparGrupos(page: ThemePage): ThemePage {
  const usados = new Set(page.elements.map((e) => e.groupId).filter(Boolean))
  const grupos = (page.groups ?? []).filter((g) => usados.has(g.id))
  const validos = new Set(grupos.map((g) => g.id))
  return {
    ...page,
    groups: grupos,
    elements: page.elements.map((el) => {
      if (!el.groupId || validos.has(el.groupId)) return el
      const { groupId: _fora, ...resto } = el
      return resto as Element
    }),
  }
}

// --- copiar e colar -------------------------------------------------------------------------

/**
 * Cópias com identidade nova, já na camada de cima. Os ids são regerados porque viram o `id` do nó
 * no HTML — dois iguais numa página fariam o motor montar um por cima do outro. O grupo não viaja:
 * ele pertence à página de origem.
 */
export function copiasPara(page: ThemePage, origem: Element[], deslocar: number): Element[] {
  let z = page.elements.length ? Math.max(...page.elements.map((e) => e.place.z)) : 0
  return [...origem]
    .sort((a, b) => a.place.z - b.place.z)
    .map((el) => {
      const copia = structuredClone(el) as Element
      delete copia.groupId
      z += 1
      return {
        ...copia,
        id: novoId('el'),
        place: { ...copia.place, at: { x: copia.place.at.x + deslocar, y: copia.place.at.y + deslocar }, z },
      } as Element
    })
}
