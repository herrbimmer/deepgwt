import type { ControlServiceKey, VhalProperty } from '../data/knowledgeTypes'

// Busca e recorte dos dois catálogos da Knowledge Base.
//
// POR QUE ISTO É CÓDIGO PURO, FORA DAS TELAS: o explorador de VHAL percorre 1602 propriedades e o
// de ControlService percorre 818 chaves a cada tecla digitada. A parte que decide o que casa com
// a busca é a parte que pode errar em silêncio — um filtro que descarta a propriedade certa não
// produz erro nenhum, produz uma tela que diz "nada encontrado" sobre um dado que existe. Aqui
// ela é testável sem navegador.
//
// O QUE ESTES CATÁLOGOS SÃO: o levantamento de 2026-09-14 do próprio veículo. Navegar neles não
// exige carro nenhum, porque é trabalho de catálogo — a existência de cada item já é FATO.
// O QUE ELES NÃO SÃO: a lista do que ESTE carro na frente do técnico tem ou responde. Um item no
// catálogo é um item que existiu naquele dump, e as telas dizem isso com essas palavras.

/** Teto de itens renderizados por vez. A contagem total continua sendo informada ao usuário. */
export const PAGE_SIZE = 200

const norm = (s: string) => s.toLowerCase().trim()

export type ControlServiceFilter = {
  query?: string
  /** Namespace exato (`car.basic`, `car.configure`…). */
  namespace?: string
  /** `'usada'` | `'oculta'` — como o levantamento classificou a exposição na UI de fábrica. */
  appState?: string
}

export function filterControlService(
  keys: readonly ControlServiceKey[],
  f: ControlServiceFilter,
): ControlServiceKey[] {
  const q = f.query ? norm(f.query) : ''
  return keys.filter((k) => {
    if (f.namespace && k.namespace !== f.namespace) return false
    if (f.appState && k.appState !== f.appState) return false
    if (!q) return true
    return norm(k.key).includes(q) || norm(k.enumName).includes(q)
  })
}

export type VhalScope = 'todas' | 'nomeadas' | 'descritas' | 'com-valor'

export type VhalFilter = {
  query?: string
  access?: string
  ecuPrefix?: string
  scope?: VhalScope
}

export function filterVhal(props: readonly VhalProperty[], f: VhalFilter): VhalProperty[] {
  const q = f.query ? norm(f.query) : ''
  const scope = f.scope ?? 'todas'
  return props.filter((p) => {
    if (f.access && p.access !== f.access) return false
    if (f.ecuPrefix && p.ecuPrefix !== f.ecuPrefix) return false
    if (scope === 'nomeadas' && !p.name) return false
    if (scope === 'descritas' && !p.description) return false
    if (scope === 'com-valor' && p.liveValue === null) return false
    if (!q) return true
    // O id entra na busca sem o `0x` também: é assim que ele costuma aparecer copiado de um log.
    return (
      (p.name !== null && norm(p.name).includes(q)) ||
      norm(p.id).includes(q) ||
      norm(p.id.replace(/^0x/, '')).includes(q) ||
      (p.description !== null && norm(p.description).includes(q)) ||
      (p.ecuPrefix !== null && norm(p.ecuPrefix).includes(q))
    )
  })
}

export type Facet = { value: string; count: number }

/** Contagem por campo, do mais numeroso para o menos — alimenta os seletores de filtro. */
export function facetsOf<T>(items: readonly T[], pick: (item: T) => string | null): Facet[] {
  const counts = new Map<string, number>()
  for (const item of items) {
    const v = pick(item)
    if (v === null) continue
    counts.set(v, (counts.get(v) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([value, count]) => ({ value, count }))
    .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value))
}

/** Recorte para render, preservando o total para que a tela nunca apresente 200 como se fosse tudo. */
export function page<T>(items: readonly T[], size = PAGE_SIZE): { shown: T[]; total: number; truncated: boolean } {
  return { shown: items.slice(0, size), total: items.length, truncated: items.length > size }
}
