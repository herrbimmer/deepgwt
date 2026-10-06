// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O canal entre quem decide os valores da prévia (cenário, linha do tempo, valores à mão, carro ao
// vivo) e quem os entrega ao iframe (o canvas).
//
// Um armazém externo, e não estado do React, por uma conta simples: a linha do tempo anda dez vezes
// por segundo. Guardar os valores em estado re-renderizaria o editor inteiro — inspetor, lista de
// camadas — a cada décimo de segundo por um número que só o iframe precisa ver. O canvas assina o
// canal num efeito e empurra para o iframe sem render nenhum.

import type { PaletteMode } from '../model/spec'

export type EstadoDaPrevia = {
  valores: Record<string, string>
  modo: PaletteMode
}

export type CanalDePrevia = {
  ler: () => EstadoDaPrevia
  publicar: (e: EstadoDaPrevia) => void
  assinar: (f: () => void) => () => void
}

export function criarCanalDePrevia(inicial: EstadoDaPrevia): CanalDePrevia {
  let atual = inicial
  const ouvintes = new Set<() => void>()
  return {
    ler: () => atual,
    publicar: (e) => {
      atual = e
      ouvintes.forEach((f) => f())
    },
    assinar: (f) => {
      ouvintes.add(f)
      return () => {
        ouvintes.delete(f)
      }
    },
  }
}

/** A janela do iframe da prévia, com a API que o motor expõe só para o editor. */
export type JanelaDaPrevia = Window & {
  DEEPGWT?: {
    preview?: {
      simulacao: (ligada: boolean) => void
      valores: (mapa: Record<string, string>) => void
      modo: (m: PaletteMode) => void
      pagina: (i: number) => void
    }
    snapshotSvg?: () => string
  }
}

/** As chaves que mudaram entre dois instantes. O iframe só recebe a diferença. */
export function diferenca(antes: Record<string, string>, depois: Record<string, string>): Record<string, string> {
  const d: Record<string, string> = {}
  for (const [k, v] of Object.entries(depois)) if (antes[k] !== v) d[k] = v
  return d
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
