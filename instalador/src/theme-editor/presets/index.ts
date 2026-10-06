// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A galeria de presets e a operação de clonar.
//
// Clonar recria o tema como rascunho editável: id novo, pasta nova, e todo elemento com id novo.
// O `origin` guarda de onde veio, só para a interface poder dizer "baseado em Cupertino Ultra".
// Nada fica somente-leitura depois — era esse o ponto de recriar os temas como dados em vez de
// copiar os bytes do `app.html` de fábrica.

import type { ThemeSpec } from '../model/spec'
import { folderNameFrom, novoId } from '../model/defaults'
import { CUPERTINO_ULTRA } from './cupertinoUltra'
import { STUTTGART_NTG55 } from './stuttgartNtg55'
import { MINIMALIST } from './minimalist'

export type Preset = {
  id: string
  name: string
  /** Frase curta para o cartão da galeria. A descrição longa vive no próprio `ThemeSpec`. */
  tagline: string
  /** Caminho servido pelo Vite a partir de `public/`. */
  thumbnail: string
  spec: ThemeSpec
}

export const PRESETS: Preset[] = [
  {
    id: CUPERTINO_ULTRA.id,
    name: CUPERTINO_ULTRA.name,
    tagline: 'Mostradores redondos com disco verde, arco de progresso e card de mídia no centro.',
    thumbnail: '/themes/cupertino-ultra/thumbnail.png',
    spec: CUPERTINO_ULTRA,
  },
  {
    id: STUTTGART_NTG55.id,
    name: STUTTGART_NTG55.name,
    tagline: 'Analógico clássico sobre papel de parede, medidor de potência e regeneração.',
    thumbnail: '/themes/stuttgart-ntg55/thumbnail.png',
    spec: STUTTGART_NTG55,
  },
  {
    id: MINIMALIST.id,
    name: MINIMALIST.name,
    tagline: 'Sem mostradores: número grande, barra de potência e faixa de status sobre preto.',
    thumbnail: '/themes/minimalista/thumbnail.png',
    spec: MINIMALIST,
  },
]

export const PRESET_BY_ID = new Map(PRESETS.map((p) => [p.id, p]))

const clonarFundo = (spec: ThemeSpec): ThemeSpec['background'] => ({ ...spec.background })

/**
 * Cópia profunda com identidade nova.
 *
 * Os ids dos elementos são regerados mesmo sendo únicos dentro de um tema: o usuário pode copiar
 * elementos entre dois temas clonados do mesmo preset, e dois `cup-dial-speed` na mesma página
 * gerariam dois nós com o mesmo `id` no HTML final.
 */
export function cloneSpec(base: ThemeSpec, nome?: string): ThemeSpec {
  const agora = Date.now()
  const titulo = nome ?? `${base.name} (cópia)`
  const copia = structuredClone(base) as ThemeSpec

  return {
    ...copia,
    id: novoId('theme'),
    folderName: folderNameFrom(titulo),
    name: titulo,
    version: '1.0.0',
    createdAt: agora,
    updatedAt: agora,
    origin: base.id,
    background: clonarFundo(copia),
    colorRules: copia.colorRules.map((r) => ({ ...r, id: novoId('rule') })),
    pages: copia.pages.map((p) => ({
      ...p,
      id: novoId('page'),
      elements: p.elements.map((el) => ({ ...el, id: novoId('el') })),
    })),
  }
}

export function clonePreset(presetId: string, nome?: string): ThemeSpec | null {
  const p = PRESET_BY_ID.get(presetId)
  return p ? cloneSpec(p.spec, nome ?? p.name) : null
}

export { CUPERTINO_ULTRA, STUTTGART_NTG55, MINIMALIST }
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
