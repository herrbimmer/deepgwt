// Varredura do veículo — a parte que o app REALMENTE consegue fazer hoje.
//
// O QUE ESTA VARREDURA É: um inventário do dicionário do ControlService. O app pede o snapshot
// inteiro pela porta 8766 e compara o que voltou contra as 818 chaves do dump de 2026-09-14.
// Isso responde três perguntas verificáveis — quantas chaves o carro respondeu, quais do dump
// ficaram mudas, e quais o carro respondeu que NÃO estão no dump.
//
// O QUE ESTA VARREDURA NÃO É: uma varredura de módulos. Não existe enumeração de ECU, leitura de
// DTC, acesso a CAN nem UDS neste app. Uma tela que chamasse isto de "scan completo" estaria
// mentindo, então `NOT_COVERED` abaixo é renderizado junto com o resultado, sempre.
//
// A terceira pergunta é a que importa para o futuro: uma chave que o carro responde e o dump não
// conhece é DESCOBERTA NOVA, e descoberta nova nasce como REQUIRES_VALIDATION — nunca como fato.

import { CONTROL_SERVICE, CONTROL_SERVICE_BY_KEY } from '../data/knowledge'
import { namespaceOf } from '../coding/keyModel'

export type NamespaceCoverage = {
  namespace: string
  /** Chaves deste namespace no dump de 818. */
  known: number
  /** Quantas dessas o carro respondeu nesta varredura. */
  answered: number
  /** Chaves respondidas que o dump não conhece. */
  unknown: number
}

export type ScanResult = {
  ts: number
  source: 'live' | 'demo'
  carIp: string
  vin: string | null
  /** Total de chaves que vieram na resposta do carro. */
  answered: number
  /** Dessas, quantas responderam com string vazia — presente, sem valor. */
  emptyValues: number
  /** Tamanho do dicionário conhecido (818, dump de 2026-09-14). */
  knownTotal: number
  /** Chaves do dump que o carro respondeu. */
  knownAnswered: number
  /** Chaves do dump que o carro NÃO respondeu nesta leitura. */
  knownSilent: string[]
  /** Chaves que o carro respondeu e o dump não conhece — descoberta nova, a validar. */
  unknownAnswered: string[]
  namespaces: NamespaceCoverage[]
}

/**
 * O que esta varredura deixa de fora, e por quê. Fica no módulo e não na tela porque o relatório
 * também precisa imprimir a mesma lista — a limitação não pode existir só no lugar onde é
 * desconfortável mostrá-la.
 */
export const NOT_COVERED: Array<{ what: string; why: string }> = [
  {
    what: 'Inventário de ECUs',
    why: 'Nenhuma interface do app enumera módulos. O que se lê aqui vem da multimídia, não do barramento.',
  },
  {
    what: 'Códigos de falha (DTC)',
    why: 'Exige UDS/OBD. O app não tem transporte de diagnóstico.',
  },
  {
    what: 'Rede CAN e topologia',
    why: 'Sem acesso ao barramento pelas interfaces atuais (telnet na multimídia e HTTP 8766).',
  },
  {
    what: 'Propriedades VHAL ao vivo',
    why: 'O catálogo de 1602 propriedades é navegável offline, mas ler valor exige uma ponte que o app ainda não tem.',
  },
  {
    what: 'Estado físico dos componentes',
    why: 'O carro declara configuração de fábrica. Declarado não é o mesmo que instalado e funcionando.',
  },
]

export type ScanInput = {
  values: Record<string, string>
  source: 'live' | 'demo'
  carIp: string
  vin: string | null
  /** Injetável para o teste conseguir uma data estável. */
  now?: number
}

export function runScan({ values, source, carIp, vin, now }: ScanInput): ScanResult {
  const answeredKeys = Object.keys(values)
  const knownSilent: string[] = []
  const unknownAnswered: string[] = []

  // Contagem por namespace: parte do dump (para que namespaces 100% mudos apareçam na lista com
  // zero, em vez de sumirem) e recebe o que a leitura trouxe.
  const byNamespace = new Map<string, NamespaceCoverage>()
  const bump = (ns: string): NamespaceCoverage => {
    let e = byNamespace.get(ns)
    if (!e) {
      e = { namespace: ns, known: 0, answered: 0, unknown: 0 }
      byNamespace.set(ns, e)
    }
    return e
  }

  for (const k of CONTROL_SERVICE.keys) {
    const e = bump(k.namespace)
    e.known += 1
    if (k.key in values) e.answered += 1
    else knownSilent.push(k.key)
  }

  let emptyValues = 0
  for (const key of answeredKeys) {
    if (values[key] === '') emptyValues += 1
    if (CONTROL_SERVICE_BY_KEY.has(key)) continue
    unknownAnswered.push(key)
    bump(namespaceOf(key)).unknown += 1
  }

  const namespaces = [...byNamespace.values()].sort((a, b) => a.namespace.localeCompare(b.namespace))

  return {
    ts: now ?? Date.now(),
    source,
    carIp,
    vin,
    answered: answeredKeys.length,
    emptyValues,
    knownTotal: CONTROL_SERVICE.meta.count,
    knownAnswered: answeredKeys.length - unknownAnswered.length,
    knownSilent: knownSilent.sort(),
    unknownAnswered: unknownAnswered.sort(),
    namespaces,
  }
}

/** Percentual de cobertura, arredondado. `0` quando não há dicionário (impossível, mas barato). */
export const coveragePct = (r: ScanResult): number =>
  r.knownTotal === 0 ? 0 : Math.round((r.knownAnswered / r.knownTotal) * 100)
