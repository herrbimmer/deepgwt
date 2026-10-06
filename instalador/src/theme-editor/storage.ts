// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Onde os temas ficam salvos neste computador, e como um rascunho antigo continua abrindo.
//
// São DUAS migrações encadeadas, cada uma do formato imediatamente anterior:
//   v0 -> v1: `elements` era um dicionário por região fixa (speedMask/infoMask/topCenterMask);
//             virou lista de páginas com elementos livres.
//   v1 -> v2: `ClusterElement` (x/y/largura/altura, `dataSource` cru) virou `ThemeSpec`.
//
// A segunda migração troca de chave de armazenamento em vez de reescrever a antiga por cima. Se
// algo der errado aqui, o rascunho original continua em `deepgwt_theme_drafts`, intacto — e o
// usuário pode ter passado semanas nele.

import type { Element, ElementKind, ThemeSpec } from './model/spec'
import { createElement, createTheme, folderNameFrom } from './model/defaults'
import { firstLegalSlot, isLegal, movedTo } from './model/collision'
import { HINT_BY_KEY } from './model/dataSources'

const CHAVE_V2 = 'deepgwt_themes_v2'
const CHAVE_V1 = 'deepgwt_theme_drafts'

// --- migração do formato antigo -------------------------------------------------------------

type ElementoAntigo = {
  id?: string
  kind?: string
  variant?: string
  label?: string
  dataSource?: string
  min?: number
  max?: number
  unit?: string
  x?: number
  y?: number
  width?: number
  height?: number
  zIndex?: number
}

/** O tipo antigo era sobre o QUE o mostrador media; o novo é sobre COMO ele desenha. */
function tipoNovo(antigo: ElementoAntigo): ElementKind {
  const v = antigo.variant
  if (v === 'bar') return 'barLinear'
  if (v === 'digital') return 'readout'
  return 'dial'
}

/**
 * A chave antiga só sobrevive se estiver no contrato do tema. As que não estão — e eram 47 das 57
 * opções do seletor antigo — assinavam e eram descartadas em silêncio pelo carro; trazê-las para
 * cá seria migrar um mostrador que nunca funcionou.
 */
function chaveNova(antiga: string | undefined, kind: ElementKind): string | undefined {
  if (!antiga) return undefined
  if (HINT_BY_KEY.has(antiga)) return antiga
  if (antiga === 'car.basic.coolant_temp') return 'car.basic.inside_temp'
  return kind === 'dial' ? 'car.basic.vehicle_speed' : undefined
}

function migrarElemento(antigo: ElementoAntigo, z: number): Element {
  const kind = tipoNovo(antigo)
  const el = createElement({
    kind,
    key: chaveNova(antigo.dataSource, kind),
    name: antigo.label || 'Elemento',
    zone: 'free',
  }, z)

  const largura = antigo.width ?? 300
  const altura = antigo.height ?? 300
  const centro = { x: Math.round((antigo.x ?? 0) + largura / 2), y: Math.round((antigo.y ?? 0) + altura / 2) }
  // O tamanho antigo era pixel livre; o novo é faixa. Escolhe a faixa cuja pegada mais se
  // aproxima da largura que o usuário tinha.
  const tamanho = largura < 200 ? 'sm' : largura < 320 ? 'md' : largura < 460 ? 'lg' : 'xl'

  const movido = {
    ...el,
    place: { ...el.place, anchor: 'center' as const, at: centro, size: tamanho, z: antigo.zIndex ?? z },
    binding: 'binding' in el && el.binding ? { ...el.binding, unit: antigo.unit ?? el.binding.unit } : (el as { binding?: null }).binding,
  } as Element

  if ('map' in movido && movido.map.kind === 'linear' && typeof antigo.min === 'number' && typeof antigo.max === 'number') {
    movido.map = { kind: 'linear', min: antigo.min, max: antigo.max }
  }
  return movido
}

function migrarV1(raw: Record<string, unknown>): ThemeSpec {
  const nome = typeof raw.name === 'string' ? raw.name : 'Tema migrado'
  const spec = createTheme(nome)
  spec.id = typeof raw.id === 'string' ? raw.id : spec.id
  spec.folderName = folderNameFrom(nome)
  spec.createdAt = typeof raw.createdAt === 'number' ? raw.createdAt : Date.now()
  spec.updatedAt = Date.now()
  spec.description = 'Tema migrado do formato anterior do criador de temas.'

  const bgAntigo = raw.background as Record<string, unknown> | undefined
  if (bgAntigo && typeof bgAntigo.mode === 'string') {
    spec.background = {
      ...spec.background,
      mode: (['preset', 'solid', 'gradient', 'custom'].includes(bgAntigo.mode) ? bgAntigo.mode : 'gradient') as ThemeSpec['background']['mode'],
      presetId: typeof bgAntigo.presetId === 'string' ? bgAntigo.presetId : 'midnight',
      solid: typeof bgAntigo.solid === 'string' ? bgAntigo.solid : spec.background.solid,
      gradientFrom: typeof bgAntigo.gradientFrom === 'string' ? bgAntigo.gradientFrom : spec.background.gradientFrom,
      gradientTo: typeof bgAntigo.gradientTo === 'string' ? bgAntigo.gradientTo : spec.background.gradientTo,
      customPath: typeof bgAntigo.customPath === 'string' ? bgAntigo.customPath : '',
      customDataUrl: typeof bgAntigo.customDataUrl === 'string' ? bgAntigo.customDataUrl : '',
    }
  }

  const paginas = Array.isArray(raw.pages) ? (raw.pages as Record<string, unknown>[]) : []
  spec.pages = paginas.length
    ? paginas.map((p, i) => ({
        id: typeof p.id === 'string' ? p.id : `pg-${i}`,
        name: typeof p.name === 'string' ? p.name : `Página ${i + 1}`,
        elements: (Array.isArray(p.elements) ? (p.elements as ElementoAntigo[]) : []).map((el, j) => {
          const novo = migrarElemento(el, j + 1)
          // O layout antigo não conhecia as zonas do veículo. Um elemento que caia numa delas é
          // reposicionado para a vaga legal mais próxima, em vez de abrir o editor já em falta.
          if (isLegal(novo)) return novo
          const vaga = firstLegalSlot(spec, novo)
          return vaga ? movedTo(novo, vaga) : novo
        }),
      }))
    : spec.pages
  return spec
}

// --- leitura e escrita ----------------------------------------------------------------------

/**
 * Completa campos que entraram no modelo depois de o tema ter sido salvo.
 *
 * Um tema gravado antes das regras de cor não tem `colorRules`, e `spec.colorRules.filter(...)` no
 * emissor quebraria ao abrir. Preencher na leitura é mais barato que espalhar `?? []` por todo
 * caminho que toca o campo.
 */
function normalizar(t: ThemeSpec): ThemeSpec {
  return { ...t, colorRules: Array.isArray(t.colorRules) ? t.colorRules : [] }
}

function lerV2(): ThemeSpec[] {
  try {
    const raw = localStorage.getItem(CHAVE_V2)
    if (!raw) return []
    const lista = JSON.parse(raw)
    return Array.isArray(lista) ? (lista as ThemeSpec[]).map(normalizar) : []
  } catch {
    return []
  }
}

function lerV1(): ThemeSpec[] {
  try {
    const raw = localStorage.getItem(CHAVE_V1)
    if (!raw) return []
    const lista = JSON.parse(raw)
    if (!Array.isArray(lista)) return []
    return lista.map((d) => migrarV1(d as Record<string, unknown>))
  } catch {
    return []
  }
}

export function listThemes(): ThemeSpec[] {
  const v2 = lerV2()
  if (v2.length) return [...v2].sort((a, b) => b.updatedAt - a.updatedAt)

  // Primeira abertura depois da atualização: migra, grava na chave nova e deixa a antiga intacta.
  const migrados = lerV1()
  if (migrados.length) {
    localStorage.setItem(CHAVE_V2, JSON.stringify(migrados))
    return [...migrados].sort((a, b) => b.updatedAt - a.updatedAt)
  }
  return []
}

export const getTheme = (id: string): ThemeSpec | null => listThemes().find((t) => t.id === id) ?? null

export function saveTheme(spec: ThemeSpec) {
  const todos = listThemes()
  const atualizado = { ...spec, updatedAt: Date.now() }
  const i = todos.findIndex((t) => t.id === spec.id)
  if (i >= 0) todos[i] = atualizado
  else todos.push(atualizado)
  localStorage.setItem(CHAVE_V2, JSON.stringify(todos))
  return atualizado
}

export function deleteTheme(id: string) {
  localStorage.setItem(CHAVE_V2, JSON.stringify(listThemes().filter((t) => t.id !== id)))
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
