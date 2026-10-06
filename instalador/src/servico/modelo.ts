// Histórico de serviço do carro (revisões e manutenção). O histórico mora no CARRO (app DeepGWT,
// GwLink `/servico`); o instalador só lista, registra e apaga por lá — sem cópia neste computador,
// para as duas pontas nunca divergirem.

export type TipoDeServico = {
  id: string
  rotulo: string
}

export type RegistroDeServico = {
  id: number
  tipo: string
  km: number
  /** ms desde a época. */
  data: number
  descricao: string
  oficina: string
  /** 0 = não informado. */
  custoCentavos: number
}

export type RespostaDeServico = {
  ok?: boolean
  error?: string
  registros?: RegistroDeServico[]
  tipos?: TipoDeServico[]
  odometro?: number
  proximaKm?: number
  proximaData?: number
}

/** Os tipos de um carro antigo demais para responder `tipos` (a lista é a mesma do app). */
export const TIPOS_PADRAO: TipoDeServico[] = [
  { id: 'compra', rotulo: 'Entrega do veículo' },
  { id: 'revisao', rotulo: 'Revisão programada' },
  { id: 'oleo', rotulo: 'Óleo e filtros' },
  { id: 'freios', rotulo: 'Freios' },
  { id: 'pneus', rotulo: 'Pneus e alinhamento' },
  { id: 'bateria', rotulo: 'Bateria' },
  { id: 'outro', rotulo: 'Outro serviço' },
]

export const lerRespostaDeServico = (bruto: string): RespostaDeServico => {
  const dados = JSON.parse(bruto) as RespostaDeServico
  // Carro com app antigo cai no `else` do servidor e devolve telemetria, sem `ok`.
  if (dados.ok === undefined) throw new Error('o app do carro é antigo demais: atualize o DeepGWT no carro')
  if (dados.ok === false) throw new Error(dados.error || 'o carro recusou o pedido')
  return dados
}

/** "123", "123,4", "1234.56" → centavos; vazio → 0; inválido → null. Mesma regra do app. */
export const centavosDe = (texto: string): number | null => {
  const t = texto.trim().replace(/^R\$/, '').trim()
  if (t === '') return 0
  const partes = t.replace(',', '.').split('.')
  if (partes.length > 2) return null
  const inteiro = partes[0] || '0'
  const fracao = partes[1] ?? ''
  if (fracao.length > 2 || !/^\d+$/.test(inteiro) || !/^\d*$/.test(fracao) || inteiro.length > 9) return null
  return Number(inteiro) * 100 + Number(fracao.padEnd(2, '0'))
}

export const reais = (centavos: number): string =>
  `R$ ${(centavos / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export const dataCurta = (ms: number): string =>
  new Date(ms).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' })

/** O `<input type="date">` entrega "AAAA-MM-DD"; vira meio-dia local (evita virar o dia por fuso). */
export const dataDoCampo = (iso: string): number | null => {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (!m) return null
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]), 12, 0, 0)
  return Number.isNaN(d.getTime()) ? null : d.getTime()
}

export const hojeNoCampo = (agora = new Date()): string =>
  `${agora.getFullYear()}-${String(agora.getMonth() + 1).padStart(2, '0')}-${String(agora.getDate()).padStart(2, '0')}`

export type SituacaoDaRevisao = 'sem-registro' | 'em-dia' | 'perto' | 'vencida'

/** Mesma regra do app: vencida passou de km ou de data; perto = 1.000 km ou 30 dias. */
export const situacaoDaRevisao = (
  proximaKm: number,
  proximaData: number,
  odometro: number,
  agora = Date.now(),
): SituacaoDaRevisao => {
  if (!proximaKm && !proximaData) return 'sem-registro'
  const kmRestantes = odometro > 0 && proximaKm > 0 ? proximaKm - odometro : null
  const dias = proximaData > 0 ? Math.floor((proximaData - agora) / 86_400_000) : null
  if ((kmRestantes !== null && kmRestantes <= 0) || (dias !== null && dias <= 0)) return 'vencida'
  if ((kmRestantes !== null && kmRestantes <= 1000) || (dias !== null && dias <= 30)) return 'perto'
  return 'em-dia'
}
