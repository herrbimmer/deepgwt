// Consistência de odômetro.
//
// ANTES DE QUALQUER COISA, o que esta análise NÃO faz: ela não detecta fraude, e não tem como
// detectar. Uma verificação de adulteração séria compara módulos INDEPENDENTES — painel, ABS,
// BCM, gateway — lidos cada um por UDS. Este app lê tudo de UM lugar só: o ControlService da
// multimídia, por HTTP na porta 8766. Se esse caminho reportar um número errado, TODAS as chaves
// abaixo reportam o mesmo número errado, e a análise diria "coincidem" com a maior das confianças.
//
// Por isso o resultado tem três estados — coincidem / divergem / insuficiente — e nenhum deles é
// um veredito sobre o veículo. "Divergem" é um fato sobre os valores lidos, e o próximo passo é
// sempre uma inspeção fora deste app.
//
// O AGRUPAMENTO É PELO NOME DA CHAVE. Que `car.basic.total_odometer` e `car.ev_info.total_odometer`
// signifiquem a mesma grandeza é dedução pelo nome — o dump prova que as chaves existem, não o
// que elas medem.

import { CONTROL_SERVICE_BY_KEY } from '../data/knowledge'

export type OdometerRole =
  /** O nome da chave indica quilometragem acumulada do veículo. Entra na comparação. */
  | 'total'
  /** Parcial, autonomia restante, aviso de revisão. Contexto, fora da comparação. */
  | 'contexto'

export type OdometerKeyDef = {
  key: string
  /** Tradução do nome da chave. Não é interpretação do valor. */
  label: string
  role: OdometerRole
  /** O que o nome sugere. Sempre dedução. */
  reading: string
}

/**
 * As chaves de quilometragem que EXISTEM no dump de 818 (2026-09-14). Nenhuma foi inventada; um
 * teste confere cada uma contra o catálogo e falha se alguma sumir da base.
 */
export const ODOMETER_KEYS: OdometerKeyDef[] = [
  {
    key: 'car.basic.total_odometer',
    label: 'Odômetro total (car.basic)',
    role: 'total',
    reading: 'O nome indica a quilometragem acumulada do veículo.',
  },
  {
    key: 'car.ev_info.total_odometer',
    label: 'Odômetro total (car.ev_info)',
    role: 'total',
    reading: 'Mesmo nome, outro namespace — o conjunto de dados elétricos/híbridos.',
  },
  {
    key: 'car.basic.accumulated_odometer',
    label: 'Odômetro acumulado',
    role: 'total',
    reading: 'O nome indica acumulado. Se é o mesmo acumulado das chaves acima, ninguém verificou.',
  },
  {
    key: 'car.basic.cur_journey_odometer',
    label: 'Trajeto atual',
    role: 'contexto',
    reading: 'Parcial da viagem em curso. Não deve coincidir com o total.',
  },
  {
    key: 'car.basic.remain_odometer',
    label: 'Autonomia restante',
    role: 'contexto',
    reading: 'Distância estimada restante. Grandeza diferente do acumulado.',
  },
  {
    key: 'car.basic.maintenance_warning_mileage',
    label: 'Quilometragem para revisão',
    role: 'contexto',
    reading: 'Aviso de manutenção. Grandeza diferente do acumulado.',
  },
  {
    key: 'car.ev_info.last_charge.time_odometer',
    label: 'Odômetro na última recarga',
    role: 'contexto',
    reading: 'Marca histórica. Esperado ser menor ou igual ao total.',
  },
]

/** A chave que diz em que unidade o carro exibe distância. O app não decodifica o código. */
export const ODOMETER_UNIT_KEY = 'car.unit_setting.odometer'

export type OdometerReading = OdometerKeyDef & {
  /** Valor cru. `null` = o carro não respondeu esta chave. */
  value: string | null
  /** O mesmo valor como número, quando o texto é numérico. `null` quando não é. */
  numeric: number | null
  /** A chave está no dump de 818? Sempre `true` para as listadas aqui — conferido em teste. */
  inDump: boolean
}

export type OdometerFinding =
  /** Todos os totais coletados têm o mesmo valor. */
  | 'coincidem'
  /** Os totais coletados têm valores diferentes entre si. */
  | 'divergem'
  /** Menos de dois totais numéricos coletados — não há o que comparar. */
  | 'insuficiente'

export type OdometerAnalysis = {
  readings: OdometerReading[]
  totals: OdometerReading[]
  contexto: OdometerReading[]
  /** Totais que vieram como número e entraram na comparação. */
  comparable: OdometerReading[]
  finding: OdometerFinding
  /** Maior diferença entre os totais comparáveis. `null` quando não há comparação. */
  spread: number | null
  /** Valor cru da chave de unidade, sem decodificação. */
  unitRaw: string | null
  /** Quantas das chaves de quilometragem o carro respondeu. */
  collected: number
}

const toNumber = (raw: string | null): number | null => {
  if (raw === null || raw.trim() === '') return null
  // Aceita vírgula decimal: o carro já devolveu texto com vírgula em outras chaves numéricas.
  const n = Number(raw.trim().replace(',', '.'))
  return Number.isFinite(n) ? n : null
}

export function analyzeOdometer(values: Record<string, string>): OdometerAnalysis {
  const readings: OdometerReading[] = ODOMETER_KEYS.map((def) => {
    const raw = values[def.key]
    const value = raw === undefined || raw === '' ? null : raw
    return {
      ...def,
      value,
      numeric: toNumber(value),
      inDump: CONTROL_SERVICE_BY_KEY.has(def.key),
    }
  })

  const totals = readings.filter((r) => r.role === 'total')
  const contexto = readings.filter((r) => r.role === 'contexto')
  const comparable = totals.filter((r) => r.numeric !== null)

  let finding: OdometerFinding = 'insuficiente'
  let spread: number | null = null
  if (comparable.length >= 2) {
    const nums = comparable.map((r) => r.numeric as number)
    spread = Math.max(...nums) - Math.min(...nums)
    finding = spread === 0 ? 'coincidem' : 'divergem'
  }

  const unitRawValue = values[ODOMETER_UNIT_KEY]

  return {
    readings,
    totals,
    contexto,
    comparable,
    finding,
    spread,
    unitRaw: unitRawValue === undefined || unitRawValue === '' ? null : unitRawValue,
    collected: readings.filter((r) => r.value !== null).length,
  }
}

/** Texto do resultado. Descreve os VALORES LIDOS — nunca o veículo, nunca a intenção de ninguém. */
export const FINDING_TEXT: Record<OdometerFinding, { title: string; body: string }> = {
  coincidem: {
    title: 'Os valores coletados coincidem',
    body:
      'As chaves cujo nome indica quilometragem total responderam o mesmo número. Como todas vêm ' +
      'da mesma interface, isso mostra consistência interna dessa leitura — não é uma verificação ' +
      'de adulteração, que exigiria ler módulos independentes por UDS.',
  },
  divergem: {
    title: 'Os valores coletados divergem',
    body:
      'As chaves responderam números diferentes. Isso é um fato sobre os valores lidos, e nada ' +
      'além disso: pode ser arredondamento, unidade diferente, momento de atualização diferente ' +
      'ou grandezas que apenas se parecem pelo nome. Nenhuma conclusão sobre o veículo sai daqui.',
  },
  insuficiente: {
    title: 'Não há valores suficientes para comparar',
    body:
      'Menos de duas chaves de quilometragem total responderam com um número. Sem dois valores ' +
      'não existe comparação, e um resultado vazio é preferível a um resultado inventado.',
  },
}
