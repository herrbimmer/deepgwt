// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Um cenário tocando em laço, sem linha do tempo na tela: a prévia do assistente e das
// miniaturas não precisam de controle, só de movimento plausível.

import { useEffect, useMemo } from 'react'
import type { PaletteMode } from '../model/spec'
import { CENARIO_POR_ID, CENARIOS, valoresNoInstante } from './cenarios'
import { criarCanalDePrevia, type CanalDePrevia } from './canal'

export function usePreviaTocando(cenarioId = 'cidade', modo: PaletteMode = 'day'): CanalDePrevia {
  const cenario = CENARIO_POR_ID.get(cenarioId) ?? CENARIOS[1]
  const canal = useMemo(() => criarCanalDePrevia({ valores: valoresNoInstante(cenario, 0), modo }), [cenario, modo])
  useEffect(() => {
    const t0 = performance.now()
    const id = window.setInterval(() => {
      const t = ((performance.now() - t0) / 1000) % cenario.duracao
      canal.publicar({ valores: { ...valoresNoInstante(cenario, t), 'car.basic.head_light_status': modo === 'headlight' ? '1' : '0' }, modo })
    }, 150)
    return () => window.clearInterval(id)
  }, [canal, cenario, modo])
  return canal
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
