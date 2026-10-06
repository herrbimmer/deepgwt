// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Onde os temas de HUD ficam neste computador.
//
// Chave própria, separada da dos temas do painel (`deepgwt_themes_v2`): são arquivos de naturezas
// diferentes — um vira pasta com app.html no carro, o outro vira ajuste do HUD — e misturá-los
// numa lista só obrigaria cada leitor a adivinhar qual é qual.
//
// Cada leitura passa por `normalizarTemaHud`: um rascunho gravado por uma versão anterior do
// editor, ou editado à mão, abre corrigido em vez de derrubar a tela.

import { type HudTema, type HudTemaSalvo, normalizarTemaHud, novoIdHud } from './model'

const CHAVE = 'deepgwt_hud_themes_v1'

function lerTudo(): HudTemaSalvo[] {
  try {
    const cru = JSON.parse(localStorage.getItem(CHAVE) ?? '[]')
    if (!Array.isArray(cru)) return []
    const saida: HudTemaSalvo[] = []
    for (const item of cru) {
      try {
        const tema = normalizarTemaHud(item)
        saida.push({
          ...tema,
          nome: typeof item.nome === 'string' ? item.nome : tema.nome,
          id: typeof item.id === 'string' ? item.id : novoIdHud(),
          atualizadoEm: typeof item.atualizadoEm === 'number' ? item.atualizadoEm : Date.now(),
          origem: typeof item.origem === 'string' ? item.origem : undefined,
        })
      } catch {
        // Um item ilegível não derruba a lista inteira.
      }
    }
    return saida
  } catch {
    return []
  }
}

function gravarTudo(lista: HudTemaSalvo[]) {
  try {
    localStorage.setItem(CHAVE, JSON.stringify(lista))
  } catch {
    // Sem armazenamento (modo privado): o editor continua funcionando nesta sessão.
  }
}

export const listarTemasHud = (): HudTemaSalvo[] => lerTudo().sort((a, b) => b.atualizadoEm - a.atualizadoEm)

export const lerTemaHud = (id: string): HudTemaSalvo | null => lerTudo().find((t) => t.id === id) ?? null

export function salvarTemaHud(tema: HudTemaSalvo): HudTemaSalvo {
  const gravado = { ...tema, atualizadoEm: Date.now() }
  const lista = lerTudo().filter((t) => t.id !== tema.id)
  gravarTudo([gravado, ...lista])
  return gravado
}

export function apagarTemaHud(id: string) {
  gravarTudo(lerTudo().filter((t) => t.id !== id))
}

/** Uma cópia com id novo — duplicar um pronto, um seu, ou abrir um importado. */
export function novoTemaHudDe(tema: HudTema, nome = tema.nome, origem?: string): HudTemaSalvo {
  const limpo = normalizarTemaHud(structuredClone(tema))
  return { ...limpo, nome, id: novoIdHud(), atualizadoEm: Date.now(), origem }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
