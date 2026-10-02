import type { Confidence, ControlServiceKey } from '../data/knowledgeTypes'
import { CONTROL_SERVICE_BY_KEY } from '../data/knowledge'

// Consulta de uma chave, uma de cada vez.
//
// A Varredura (`pro/scan.ts`) responde em massa: quanto do dicionário este carro respondeu. Aqui é
// a pergunta inversa e pontual, a que um engenheiro faz com um nome na mão vindo de um log, de um
// APK descompilado ou de um palpite: "e ESTA chave, o que é?".
//
// A RESPOSTA QUE MAIS IMPORTA É A QUARTA. Quando a chave não está no levantamento e o carro também
// não respondeu por ela, a tentação é escrever "não existe". Não é o que sabemos. O que sabemos é
// que nunca a vimos: o levantamento de 818 foi feito num carro, numa versão de software, num
// estado. `nao-encontrada` diz exatamente isso e nada além.
//
// E a terceira — chave fora do levantamento que o carro RESPONDEU — é descoberta nova, e descoberta
// nova nasce `REQUIRES_VALIDATION`. Ela não entra no catálogo por ter aparecido uma vez.

export type KeyVerdict =
  /** Está no levantamento e o carro respondeu nesta sessão. */
  | 'conhecida-respondida'
  /** Está no levantamento e o carro não devolveu valor nesta sessão. */
  | 'conhecida-silenciosa'
  /** Não está no levantamento e mesmo assim o carro respondeu. Descoberta. */
  | 'descoberta'
  /** Não está no levantamento e o carro não respondeu. Nunca vimos — não é o mesmo que não existir. */
  | 'nao-encontrada'

export type KeyLookup = {
  key: string
  verdict: KeyVerdict
  /** A entrada do levantamento, quando existe. */
  entry: ControlServiceKey | null
  /** O que o carro devolveu nesta sessão. `null` = respondeu vazio; `undefined` = não respondeu. */
  value: string | null | undefined
}

export function lookupKey(rawKey: string, values: Record<string, string>): KeyLookup {
  const key = rawKey.trim()
  const entry = CONTROL_SERVICE_BY_KEY.get(key) ?? null
  const answered = key in values
  const raw = answered ? values[key] : undefined
  const value = answered ? (raw === '' ? null : raw) : undefined

  const verdict: KeyVerdict = entry
    ? answered
      ? 'conhecida-respondida'
      : 'conhecida-silenciosa'
    : answered
      ? 'descoberta'
      : 'nao-encontrada'

  return { key, verdict, entry, value }
}

export const VERDICT_TEXT: Record<KeyVerdict, { title: string; body: string; confidence: Confidence }> = {
  'conhecida-respondida': {
    title: 'No levantamento, e o carro respondeu',
    body:
      'A chave existe no dicionário levantado deste modelo, e este carro devolveu valor para ela ' +
      'nesta sessão. O valor vale para este carro, neste momento. O que ele SIGNIFICA continua ' +
      'deduzido do nome — o app não tem a tabela de valores de nenhuma chave.',
    confidence: 'OBSERVED',
  },
  'conhecida-silenciosa': {
    title: 'No levantamento, sem resposta nesta sessão',
    body:
      'A chave existe no dicionário, mas este carro não devolveu valor para ela agora. Pode ser ' +
      'equipamento ausente, pode ser estado do veículo, pode ser chave que esta versão de software ' +
      'não publica. A tela não escolhe entre as três.',
    confidence: 'FACT',
  },
  descoberta: {
    title: 'Fora do levantamento — e o carro respondeu',
    body:
      'Esta chave não está nas 818 levantadas em 2026-09-14 e mesmo assim o carro devolveu valor ' +
      'para ela. Isso é descoberta nova. Ela não entra no catálogo por ter aparecido uma vez: ' +
      'precisa ser reproduzida e registrada com método e data antes de valer como fato.',
    confidence: 'REQUIRES_VALIDATION',
  },
  'nao-encontrada': {
    title: 'Nunca vista',
    body:
      'Esta chave não está no levantamento e este carro não respondeu por ela. Isso NÃO quer dizer ' +
      'que ela não existe: o levantamento foi feito em um carro, em uma versão de software, em um ' +
      'estado. Quer dizer que nós nunca a vimos.',
    confidence: 'UNKNOWN',
  },
}
