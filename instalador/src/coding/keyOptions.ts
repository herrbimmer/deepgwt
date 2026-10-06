// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// De onde saem as opções que a aba Coding oferece para uma chave.
//
// A REGRA QUE ESTE ARQUIVO NÃO QUEBRA: nenhuma opção é inventada. Não existe em lugar nenhum —
// nem no carro, nem na documentação da GWM, nem neste projeto — uma tabela dizendo que
// `car.configure.energy_type` aceita 0..4 e que 3 significa PHEV. Escrever essa tabela aqui seria
// fabricar informação que o usuário tomaria por verificada.
//
// O que existe e é honesto oferecer:
//   1. `0` e `1` quando o carro respondeu exatamente "0" ou "1" — a chave se comporta como
//      liga/desliga, e isso é observação do valor, não suposição sobre a chave;
//   2. todo valor que ESTA chave já teve nos pontos de restauração gravados neste computador —
//      são valores que este carro de fato assumiu;
//   3. o valor atual.
//
// Cada opção sai rotulada com a sua origem, e a caixa de valor livre continua ali para tudo o que
// não está nesta lista.

import type { Snapshot } from '../state/snapshots'

export type KeyOptionOrigin = 'atual' | 'booleana' | 'ponto'

export type KeyOption = {
  value: string
  origin: KeyOptionOrigin
  /** Texto curto que a interface mostra para explicar de onde a opção veio. */
  note: string
}

export const ORIGIN_LABEL: Record<KeyOptionOrigin, string> = {
  atual: 'valor atual no carro',
  booleana: 'a chave respondeu 0/1, então se comporta como liga/desliga',
  ponto: 'valor que esta chave já teve num ponto de restauração',
}

/**
 * Opções conhecidas para uma chave, sem repetição e em ordem estável (numérica quando dá).
 *
 * `snapshots` são os pontos de restauração; cada um guarda o mapa inteiro de chaves lido do carro
 * naquele momento, então é o histórico real de valores desta chave neste veículo.
 */
export function optionsForKey(
  key: string,
  current: string | undefined,
  snapshots: Snapshot[],
): KeyOption[] {
  const found = new Map<string, KeyOption>()

  const add = (value: string, origin: KeyOptionOrigin) => {
    if (value === '' || found.has(value)) return
    found.set(value, { value, origin, note: ORIGIN_LABEL[origin] })
  }

  if (current !== undefined && current !== '') add(current, 'atual')
  // Só chamamos de booleana quando o próprio carro respondeu 0 ou 1 — ver `looksBoolean`.
  if (current === '0' || current === '1') {
    add('0', 'booleana')
    add('1', 'booleana')
  }
  for (const snap of snapshots) {
    const v = snap.values[key]
    if (v !== undefined) add(v, 'ponto')
  }

  return [...found.values()].sort((a, b) => {
    const na = Number(a.value)
    const nb = Number(b.value)
    if (Number.isFinite(na) && Number.isFinite(nb)) return na - nb
    return a.value.localeCompare(b.value)
  })
}

/** `car.configure.mood_lamp` -> `['car', 'configure', 'mood_lamp']`. */
export const segmentsOf = (key: string) => key.split('.')

export type TreeNode = {
  /** Caminho acumulado, ex.: `car.configure`. Serve de id e de rótulo do galho. */
  path: string
  /** Último segmento, que é o que a linha mostra. */
  label: string
  children: Map<string, TreeNode>
  /** Chaves completas que terminam exatamente aqui. */
  keys: string[]
  /** Quantas chaves existem neste galho e em tudo abaixo dele. */
  count: number
}

function emptyNode(path: string, label: string): TreeNode {
  return { path, label, children: new Map(), keys: [], count: 0 }
}

/**
 * Monta a árvore de namespaces a partir da lista crua de chaves.
 *
 * Profundidade `depth` = quantos segmentos viram galho antes de a chave virar folha. Dois
 * (`car` → `configure`) é o que espelha o dicionário do carro sem criar um galho por chave:
 * `car.configure.mood_lamp` fica como folha dentro de `car` → `configure`.
 */
export function buildKeyTree(keys: string[], depth = 2): TreeNode {
  const root = emptyNode('', '')
  for (const key of keys) {
    const parts = segmentsOf(key)
    let node = root
    node.count++
    for (let i = 0; i < Math.min(depth, parts.length - 1); i++) {
      const path = parts.slice(0, i + 1).join('.')
      let child = node.children.get(path)
      if (!child) {
        child = emptyNode(path, parts[i])
        node.children.set(path, child)
      }
      child.count++
      node = child
    }
    node.keys.push(key)
  }
  return root
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
