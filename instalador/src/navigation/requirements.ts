// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { Transport } from './routeMeta'
import type { ConnectionState, HeadUnitState } from '../state/VehicleSession'

export type Requirement = {
  /** Cabe num selo de menu. */
  short: string
  /** Explica o que falta e o que fazer — vai no aviso do topo da tela. */
  long: string
}

/**
 * O que falta para uma tela funcionar agora, a partir de como ela alcança o carro (`transport`)
 * e do que o monitor de conexão sabe. `null` = nada falta, ou ainda não dá para saber.
 *
 * "Ainda não dá para saber" também devolve `null` de propósito: na abertura do app o monitor leva
 * alguns segundos para responder, e um selo "sem conexão" que acende e apaga nesse intervalo em
 * todos os itens do menu é ruído, não informação.
 */
export function unmetRequirement(
  transport: Transport,
  connection: ConnectionState,
  headUnit: HeadUnitState,
): Requirement | null {
  if (connection === 'demo') return null
  if (transport === 'ip') {
    if (connection !== 'offline') return null
    return {
      short: 'Precisa do app aberto no carro',
      long:
        'Esta tela lê e escreve pelo app DeepGWT da central, que não está respondendo. Abra o app ' +
        'no carro (ou instale-o em "Instalar e manter") e confira o IP em Conexão.',
    }
  }
  if (transport === 'hotspot') {
    if (headUnit !== 'unreachable') return null
    return {
      short: 'Precisa do Wi-Fi do carro',
      long:
        'Esta ação fala direto com a central multimídia, que não foi encontrada na rede. Ligue o ' +
        'carro, ative o Wi-Fi (hotspot) da central e conecte este computador a ele.',
    }
  }
  return null
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
