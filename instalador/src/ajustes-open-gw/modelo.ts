// Os ajustes do OPEN GW como o carro os descreve (GwLinkServer `/ajustes`, Alpha 6.4).
//
// O CATÁLOGO NÃO MORA AQUI
//
// Esta tela não sabe de antemão quais ajustes existem: o carro manda a lista, com rótulo, tipo,
// faixa, dependência e o padrão de cada um, e a tela só desenha. É o que faz um app novo no carro
// aparecer inteiro num instalador antigo — e o que impede um instalador novo de oferecer, num
// carro antigo, uma opção que o app de lá não tem.
//
// Este módulo é só lógica, sem React e sem Tauri, para os testes rodarem em Node puro.

export type TipoDeAjuste = 'interruptor' | 'numero' | 'opcoes' | 'texto' | 'cor' | 'hora'
export type Valor = boolean | number | string | null

/**
 * `recurso`, `nivelExigido` e `bloqueado` vêm de um carro 8.0 em diante: o nível que a seção ou o
 * ajuste pede e se o nível do carro libera. Carro mais antigo não manda — nada fica trancado.
 */
type DoNivel = { recurso?: string; nivelExigido?: string; bloqueado?: boolean }

export type Secao = { id: string; titulo: string; descricao: string } & DoNivel

export type Ajuste = {
  id: string
  secao: string
  rotulo: string
  tipo: TipoDeAjuste
  padrao: Valor
  efeito: string
  origem: 'preferencia' | 'veiculo' | 'painel'
  ajuda?: string
  min?: number
  max?: number
  passo?: number
  unidade?: string
  maxCaracteres?: number
  aviso?: string
  inteiro?: boolean
  avancado?: boolean
  soNoComputador?: boolean
  opcoes?: Array<{ id: string; rotulo: string }>
  visivelSe?: { id: string; valores: string[] }
} & DoNivel

export type Catalogo = {
  app: string
  formato: number
  secoes: Secao[]
  ajustes: Ajuste[]
}

export type Estado = {
  revisao: number
  valores: Record<string, Valor>
  indisponiveis: string[]
  faixas: Record<string, [number, number]>
}

export type Recusa = { id: string; motivo: string }

export type RespostaDeAplicacao = Estado & {
  tudoCerto: boolean
  aplicados: string[]
  recusados: Recusa[]
  falhas: string[]
  avisos: string[]
}

export const CARRO_ANTIGO =
  'O OPEN GW deste carro é anterior à Alpha 6.4 e ainda não deixa mudar os ajustes pelo computador. ' +
  'Atualize o app no carro (Instalar e manter → Atualizar) e abra esta tela de novo.'

/** Erro que a tela mostra como "atualize o app", e não como falha de rede. */
export class CarroAntigoError extends Error {
  constructor() {
    super(CARRO_ANTIGO)
    this.name = 'CarroAntigoError'
  }
}

/**
 * Lê uma resposta de `/ajustes`. Um carro anterior à 6.4 devolve a telemetria (JSON sem `ok`):
 * tratar isso como sucesso mentiria, e como erro de rede mandaria procurar o problema no lugar
 * errado.
 */
export function lerResposta(texto: string): Record<string, unknown> {
  let json: unknown
  try {
    json = JSON.parse(texto)
  } catch {
    throw new Error('O carro respondeu algo que não é JSON.')
  }
  if (!json || typeof json !== 'object' || !('ok' in json)) throw new CarroAntigoError()
  const r = json as Record<string, unknown>
  if (r.ok !== true) throw new Error(typeof r.error === 'string' ? r.error : 'O carro recusou o pedido.')
  return r
}

export function lerEstado(r: Record<string, unknown>): Estado {
  return {
    revisao: Number(r.revisao ?? 0),
    valores: (r.valores ?? {}) as Record<string, Valor>,
    indisponiveis: Array.isArray(r.indisponiveis) ? (r.indisponiveis as string[]) : [],
    faixas: (r.faixas ?? {}) as Record<string, [number, number]>,
  }
}

export function lerCatalogo(r: Record<string, unknown>): Catalogo {
  if (!Array.isArray(r.ajustes) || !Array.isArray(r.secoes)) throw new Error('O carro não mandou o catálogo.')
  return {
    app: String(r.app ?? ''),
    formato: Number(r.formato ?? 1),
    secoes: r.secoes as Secao[],
    ajustes: r.ajustes as Ajuste[],
  }
}

// ── Visibilidade ─────────────────────────────────────────────────────────────────────────────

/**
 * O primeiro ajuste, subindo a cadeia de dependências, cuja condição não está satisfeita —
 * `null` quando nada bloqueia. É o que a busca mostra ("ligue Barra inferior para usar").
 */
export function bloqueadoPor(
  a: Ajuste,
  porId: Map<string, Ajuste>,
  valores: Record<string, Valor>,
): Ajuste | null {
  const vistos = new Set<string>([a.id])
  let atual: Ajuste = a
  while (atual.visivelSe) {
    const c = atual.visivelSe
    const alvo = porId.get(c.id)
    // Dependência de um ajuste que este carro não descreve: não esconde nada.
    if (!alvo || vistos.has(alvo.id)) return null
    if (!c.valores.includes(String(valores[c.id]))) return alvo
    vistos.add(alvo.id)
    atual = alvo
  }
  return null
}

export const visivel = (a: Ajuste, porId: Map<string, Ajuste>, valores: Record<string, Valor>) =>
  bloqueadoPor(a, porId, valores) === null

// ── Alterado ─────────────────────────────────────────────────────────────────────────────────

/** Diferente do padrão do OPEN GW. Ajuste sem padrão (o amplificador decide) nunca está. */
export function alterado(a: Ajuste, valor: Valor | undefined): boolean {
  if (a.padrao === null || valor === null || valor === undefined) return false
  if (typeof a.padrao === 'number' && typeof valor === 'number') return Math.abs(a.padrao - valor) > 1e-6
  if (a.tipo === 'cor' && typeof valor === 'string' && typeof a.padrao === 'string') {
    return valor.toUpperCase() !== a.padrao.toUpperCase()
  }
  return a.padrao !== valor
}

export function descreverValor(a: Ajuste, valor: Valor | undefined): string {
  if (valor === null || valor === undefined) return '—'
  if (a.tipo === 'interruptor') return valor ? 'ligado' : 'desligado'
  if (a.tipo === 'opcoes') return a.opcoes?.find((o) => o.id === String(valor))?.rotulo ?? String(valor)
  if (a.tipo === 'numero') return `${formatarNumero(a, Number(valor))}${a.unidade ? ` ${a.unidade}` : ''}`
  if (a.tipo === 'texto') return valor === '' ? '(vazio)' : `"${valor}"`
  return String(valor)
}

export function formatarNumero(a: Ajuste, n: number): string {
  if (a.inteiro) return String(Math.round(n))
  const casas = a.passo && a.passo < 1 ? Math.min(3, String(a.passo).split('.')[1]?.length ?? 2) : 1
  return n.toFixed(casas).replace(/\.?0+$/, '') || '0'
}

/** A faixa que vale: a do amplificador, quando o carro informa, senão a do catálogo. */
export function faixaDe(a: Ajuste, faixas: Record<string, [number, number]>): [number, number] {
  const f = faixas[a.id]
  if (f && f.length === 2) return [f[0], f[1]]
  return [a.min ?? 0, a.max ?? 100]
}

// ── Busca ────────────────────────────────────────────────────────────────────────────────────

const semAcento = (s: string) =>
  s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()

/** Todas as palavras da busca têm de aparecer no rótulo, na ajuda ou no nome da seção. */
export function combina(a: Ajuste, secao: Secao | undefined, busca: string): boolean {
  const termos = semAcento(busca).split(/\s+/).filter(Boolean)
  if (!termos.length) return true
  const alvo = semAcento([a.rotulo, a.ajuda ?? '', secao?.titulo ?? '', a.opcoes?.map((o) => o.rotulo).join(' ') ?? ''].join(' '))
  return termos.every((t) => alvo.includes(t))
}

// ── Mescla ───────────────────────────────────────────────────────────────────────────────────

/**
 * Junta o que o carro acabou de mandar com o que está na tela.
 *
 * O que o usuário mexeu e ainda não foi confirmado ([protegidos]) fica como está: sem isso, a
 * releitura disparada pela própria gravação faria o controle deslizante voltar um instante para
 * o valor anterior enquanto o dedo ainda está nele. Devolve também quais ajustes mudaram por
 * fora — a tela os destaca como "mudou no carro".
 */
export function mesclar(
  atuais: Record<string, Valor>,
  doCarro: Record<string, Valor>,
  protegidos: ReadonlySet<string>,
): { valores: Record<string, Valor>; mudaram: string[] } {
  const valores: Record<string, Valor> = { ...atuais }
  const mudaram: string[] = []
  for (const [id, v] of Object.entries(doCarro)) {
    if (protegidos.has(id)) continue
    if (id in atuais && !iguais(atuais[id], v)) mudaram.push(id)
    valores[id] = v
  }
  return { valores, mudaram }
}

const iguais = (a: Valor | undefined, b: Valor | undefined) =>
  typeof a === 'number' && typeof b === 'number' ? Math.abs(a - b) < 1e-6 : a === b
