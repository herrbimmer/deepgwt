// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Leitura ao vivo da codificação de fábrica, pelo shell do carro.
//
// A base de conhecimento guarda a captura de 2026-09-15. Isto lê de novo, agora, e compara. As
// duas coisas servem a perguntas diferentes: a captura diz o que o carro era quando foi lido; esta
// leitura diz o que ele é. Enquanto ninguém escrever nada, as duas têm de bater — e uma divergência
// é informação de verdade: alguém recodificou o carro, ou o veículo não é o mesmo da captura.
//
// O comando é `getprop`, que só lê. Nada aqui escreve no veículo.

import { invoke } from '@tauri-apps/api/core'
import { FACTORY_BY_NAME, FACTORY_CODING } from '../data/knowledge'

const PREFIX = 'persist.vendor.gwm.cfg.'

/** `getprop` imprime uma linha por propriedade, no formato `[chave]: [valor]`. */
const LINHA = /^\[([^\]]+)\]:\s*\[([^\]]*)\]\s*$/

export type LiveFlag = {
  name: string
  value: string
  equipped: boolean
  /** Valor da mesma flag na captura da base de conhecimento. `null` = flag nova, não catalogada. */
  knownValue: string | null
  /** `true` quando o carro respondeu algo diferente do que a captura registrou. */
  changed: boolean
}

export type LiveFactoryCoding = {
  flags: LiveFlag[]
  equippedCount: number
  /** Flags cujo valor difere da captura — o resultado que justifica reler. */
  changed: LiveFlag[]
  /** Flags que o carro respondeu e não existem na captura. */
  novas: LiveFlag[]
  /** Flags da captura que o carro não respondeu agora. */
  ausentes: string[]
  readAt: number
}

/**
 * Lê `persist.vendor.gwm.cfg.*` do carro por telnet e compara com a captura.
 *
 * Exige conexão telnet já estabelecida (`abrir_sessao_telnet`). Propaga o erro do transporte em vez
 * de devolver um resultado vazio: uma tabela vazia por falha de conexão seria lida como "o carro
 * não tem nada equipado", que é o oposto da verdade.
 */
export async function readFactoryCodingLive(): Promise<LiveFactoryCoding> {
  const saida = await invoke<string>('telnet_exec', {
    command: `getprop | grep '${PREFIX}'`,
    timeoutSecs: 60,
  })

  const flags: LiveFlag[] = []
  const vistas = new Set<string>()

  for (const linha of saida.split('\n')) {
    const m = LINHA.exec(linha.trim())
    if (!m) continue
    const [, prop, value] = m
    if (!prop.startsWith(PREFIX)) continue
    const name = prop.slice(PREFIX.length)
    // Checksum do blob de configuração, não um item de equipamento.
    if (name === 'bean_data_md5') continue

    const conhecida = FACTORY_BY_NAME.get(name)
    vistas.add(name)
    flags.push({
      name,
      value,
      equipped: value !== '0' && value !== '',
      knownValue: conhecida?.value ?? null,
      changed: conhecida !== undefined && conhecida.value !== value,
    })
  }

  flags.sort((a, b) => a.name.localeCompare(b.name))

  return {
    flags,
    equippedCount: flags.filter((f) => f.equipped).length,
    changed: flags.filter((f) => f.changed),
    novas: flags.filter((f) => f.knownValue === null),
    ausentes: FACTORY_CODING.flags.filter((f) => !vistas.has(f.name)).map((f) => f.name),
    readAt: Date.now(),
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
