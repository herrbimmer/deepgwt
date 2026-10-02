// Conferência do código de licença no instalador (Alpha 8.1; código curto na 8.2) — só LER, nunca
// emitir.
//
// O gerador de licenças (com a chave privada) é um programa à parte que nunca vai junto com o
// instalador. Isto aqui é a mesma conta de `licenciador/src/nucleo/codigo.ts` e de
// `licenca/CodigoDeLicenca.kt` no app: Base32 de Crockford em 3 linhas de 19 símbolos + 1 de
// conferência (60 caracteres). Serve para achar o código no que a pessoa colou (a mensagem inteira
// do WhatsApp serve) e apontar a linha com erro de digitação antes de mandar ao carro — quem decide
// se a licença vale (assinatura, VIN, série repetida) é o carro.
// Travado pelos vetores de `mock/licencaVetores.json` em `scripts/test-licenca.mjs`.

export const ALFABETO = '0123456789ABCDEFGHJKMNPQRSTVWXYZ'
export const LINHAS = 3
export const DADOS_POR_LINHA = 19
export const SIMBOLOS_POR_LINHA = DADOS_POR_LINHA + 1
export const TAMANHO_DO_CODIGO = LINHAS * SIMBOLOS_POR_LINHA

export function valorDoSimbolo(c: string): number {
  const u = c.toUpperCase()
  const n = u === 'O' ? '0' : u === 'I' || u === 'L' ? '1' : u
  return n.length === 1 ? ALFABETO.indexOf(n) : -1
}

export function conferencia(valores: number[], linha: number): number {
  let s = 7 * (linha + 1)
  for (let i = 0; i < valores.length; i++) s += (2 * i + 1) * valores[i]
  return s % 32
}

export function tamanhoDoCodigo(): number {
  return TAMANHO_DO_CODIGO
}

type Motivo = 'vazio' | 'caractere' | 'tamanho' | 'conferencia'

export type ConferenciaDoCodigo =
  /** `codigo` = o código achado, arrumado em 3 linhas (é o que vai ao carro). */
  | { ok: true; simbolos: number; codigo: string }
  | { ok: false; erro: string; linha?: number; simbolos: number; motivo: Motivo }

function primeiraLinhaErrada(valores: number[]): number {
  for (let l = 0; l * SIMBOLOS_POR_LINHA < valores.length; l++) {
    const linha = valores.slice(l * SIMBOLOS_POR_LINHA, (l + 1) * SIMBOLOS_POR_LINHA)
    if (linha.length < 2) return l + 1
    if (conferencia(linha.slice(0, -1), l) !== linha[linha.length - 1]) return l + 1
  }
  return 0
}

const SEP = '\\s\\-_.·–—'
const SEPARADOR = new RegExp(`[${SEP}]`)
const SEPARADORES = new RegExp(`[${SEP}]`, 'g')
const FORA_DO_CODIGO = new RegExp(`[^0-9A-Za-z${SEP}]+`)
const OUTRAS_COISAS = new RegExp(`[^0-9A-Za-z${SEP}]`)

function arrumar(valores: number[]): string {
  const linhas: string[] = []
  for (let l = 0; l < LINHAS; l++) {
    const s = valores.slice(l * SIMBOLOS_POR_LINHA, (l + 1) * SIMBOLOS_POR_LINHA).map((v) => ALFABETO[v]).join('')
    linhas.push(s.match(/.{1,5}/g)!.join('-'))
  }
  return linhas.join('\n')
}

function conferirEstrito(texto: string): ConferenciaDoCodigo {
  const limpo = texto.replace(SEPARADORES, '')
  if (!limpo) return { ok: false, motivo: 'vazio', erro: 'Cole o código ou a mensagem com a licença.', simbolos: 0 }
  const valores: number[] = []
  for (const c of limpo) {
    const v = valorDoSimbolo(c)
    if (v < 0) return { ok: false, motivo: 'caractere', erro: `"${c}" não faz parte de um código de licença.`, simbolos: valores.length }
    valores.push(v)
  }
  if (valores.length !== TAMANHO_DO_CODIGO) {
    const d = valores.length - TAMANHO_DO_CODIGO
    const errada = primeiraLinhaErrada(valores)
    const linha = errada <= LINHAS ? errada : 0
    const quanto =
      d === -1 ? 'Falta 1 caractere' : d < 0 ? `Faltam ${-d} caracteres` : d === 1 ? 'Sobra 1 caractere' : `Sobram ${d} caracteres`
    return {
      ok: false,
      motivo: 'tamanho',
      erro: linha > 0 ? `${quanto} — confira a linha ${linha}.` : `${quanto} no código.`,
      linha: linha > 0 ? linha : undefined,
      simbolos: valores.length,
    }
  }
  const linha = primeiraLinhaErrada(valores)
  if (linha > 0) return { ok: false, motivo: 'conferencia', erro: `A linha ${linha} tem um caractere errado.`, linha, simbolos: valores.length }
  return { ok: true, simbolos: valores.length, codigo: arrumar(valores) }
}

/** Palavras seguidas (só separadores entre elas), todas do alfabeto, somando 60 símbolos. */
function candidatos(texto: string): string[] {
  const achados: string[] = []
  for (const pedaco of texto.split(FORA_DO_CODIGO)) {
    const palavras = pedaco.split(SEPARADOR).filter(Boolean)
    for (let i = 0; i < palavras.length; i++) {
      let total = 0
      for (let j = i; j < palavras.length; j++) {
        if ([...palavras[j]].some((c) => valorDoSimbolo(c) < 0)) break
        total += palavras[j].length
        if (total === TAMANHO_DO_CODIGO) achados.push(palavras.slice(i, j + 1).join(''))
        if (total >= TAMANHO_DO_CODIGO) break
      }
    }
  }
  return achados
}

/**
 * Acha e confere o código em [texto] — só o código, ou a mensagem inteira que a pessoa recebeu.
 * `ok` não quer dizer que a licença vale — só que foi copiada inteira e sem erro de digitação.
 */
export function conferirCodigo(texto: string): ConferenciaDoCodigo {
  const direto = conferirEstrito(texto)
  if (direto.ok || direto.motivo === 'vazio') return direto
  const achados = candidatos(texto)
  for (const c of achados) {
    const r = conferirEstrito(c)
    if (r.ok) return r
  }
  if (achados.length > 0 && (direto.motivo === 'caractere' || direto.motivo === 'tamanho')) return conferirEstrito(achados[0])
  if (OUTRAS_COISAS.test(texto)) {
    return { ok: false, motivo: 'caractere', erro: 'Não achei o código no texto colado. Ele tem 60 caracteres, em 3 linhas.', simbolos: 0 }
  }
  return direto
}
