// Preferências do EDITOR, não do tema: ficam neste computador e nunca entram no `ThemeSpec`.
//
// Mesma regra do modo livre (`model/freeMode.ts`): um tema desenhado no modo Básico, com as zonas
// escondidas ou em 200% de zoom é um tema como outro qualquer. O carro não tem como saber, e não
// precisa.

import { useCallback, useSyncExternalStore } from 'react'

export type ModoDoEditor = 'basico' | 'avancado'
export type ZoomDoCanvas = 'ajustar' | 0.5 | 0.75 | 1

export type PreferenciasDoEditor = {
  modo: ModoDoEditor | null
  /** As áreas que o veículo desenha por cima: escondidas por padrão, aparecem durante o arraste. */
  mostrarZonas: boolean
  zoom: ZoomDoCanvas
  /** Coluna da esquerda recolhida num trilho de ícones. */
  esquerdaRecolhida: boolean
}

const CHAVE = 'opengw.themeEditor.prefs'
const PADRAO: PreferenciasDoEditor = { modo: null, mostrarZonas: false, zoom: 'ajustar', esquerdaRecolhida: false }

function ler(): PreferenciasDoEditor {
  try {
    const cru = JSON.parse(window.localStorage.getItem(CHAVE) ?? '{}')
    return { ...PADRAO, ...(cru && typeof cru === 'object' ? cru : {}) }
  } catch {
    return PADRAO
  }
}

let atual = typeof window === 'undefined' ? PADRAO : ler()
const ouvintes = new Set<() => void>()

export function definirPreferencias(parte: Partial<PreferenciasDoEditor>) {
  atual = { ...atual, ...parte }
  try {
    window.localStorage.setItem(CHAVE, JSON.stringify(atual))
  } catch {
    /* sem memória entre sessões; a sessão atual continua valendo */
  }
  ouvintes.forEach((f) => f())
}

const assinar = (f: () => void) => {
  ouvintes.add(f)
  return () => {
    ouvintes.delete(f)
  }
}

export function usePreferenciasDoEditor(): [PreferenciasDoEditor, (p: Partial<PreferenciasDoEditor>) => void] {
  const prefs = useSyncExternalStore(assinar, () => atual, () => PADRAO)
  const definir = useCallback((p: Partial<PreferenciasDoEditor>) => definirPreferencias(p), [])
  return [prefs, definir]
}
