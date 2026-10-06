// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A conversa com o carro sobre o tema do painel: `GET /temas`, `POST /temas/ativar` e
// `POST /temas/rascunho` no GwLinkServer (Alpha 6.5), mais o envio de arquivos que já existia.
//
// COMO SABER QUE O CARRO É ANTIGO — igual ao HUD e aos ajustes: um app anterior à 6.5 não tem a
// rota, cai no ramo padrão do servidor e devolve telemetria, que é JSON sem `ok`.

import { invoke } from '@tauri-apps/api/core'
import type { ThemeSpec } from '../model/spec'
import { ARQUIVO_DO_EDITOR, CAR_THEME_ROOT, PASTA_DO_RASCUNHO, buildThemeBundle, bytesToBase64 } from '../exportTheme'

export const CARRO_ANTIGO =
  'O DeepGWT deste carro é anterior à Alpha 6.5 e ainda não troca o tema pelo computador. Atualize ' +
  'o app no carro (Instalar e manter → Atualizar) e tente de novo.'

/**
 * `casta` chega do app 7.0 em diante: 'legacy' é o que veio no APK, 'xtra' é todo tema enviado
 * depois. Num app mais antigo ela falta — ver [castaDe].
 */
export type TemaNoCarro = { pasta: string; nome: string; versao: string; descricao: string; embutido: boolean; casta?: 'legacy' | 'xtra' }

/**
 * As pastas que o APK traz (a 7.4 somou o Stuttgart MBUX; a 7.6 tirou o Stuttgart NTG 5.5 do
 * motor, e o NTG que se escolhe é só o clássico, `coffe3`; a 8.6 somou o Bávaro ID8; a 9.5, o TEA 3.0 e o
 * Cupertino Extended). Decide a
 * casta só quando o carro ainda não a informa, e diz quais temas têm miniatura embutida no
 * instalador.
 */
const PASTAS_LEGACY = ['bavarianid7', 'bavaroid8', 'coffe3', 'cupertinoextended', 'cupertinoultra', 'minimalista', 'stuttgart-mbux', 'sweedesensus', 'tea30']

export const castaDe = (t: TemaNoCarro): 'legacy' | 'xtra' =>
  t.casta ?? (t.embutido || PASTAS_LEGACY.includes(t.pasta.toLowerCase()) ? 'legacy' : 'xtra')

/** Miniatura de um tema Legacy que o instalador leva (scripts/copiar-miniaturas-legacy.mjs), ou null. */
export const miniaturaLegacyDe = (pasta: string): string | null =>
  PASTAS_LEGACY.includes(pasta.toLowerCase()) ? `/temas-legacy/${pasta.toLowerCase()}.png` : null

export type EstadoDosTemas = {
  ativo: string
  rascunho: boolean
  temaCustomizado: boolean
  temas: TemaNoCarro[]
}

function lerResposta(texto: string): Record<string, unknown> {
  let json: unknown
  try {
    json = JSON.parse(texto)
  } catch {
    throw new Error('O carro respondeu algo que não é JSON.')
  }
  if (!json || typeof json !== 'object' || !('ok' in json)) throw new Error(CARRO_ANTIGO)
  const r = json as Record<string, unknown>
  if (r.ok !== true) throw new Error(typeof r.error === 'string' ? r.error : 'O carro recusou o pedido.')
  return r
}

function paraEstado(r: Record<string, unknown>): EstadoDosTemas {
  const temas = Array.isArray(r.temas) ? (r.temas as TemaNoCarro[]) : []
  return {
    ativo: typeof r.ativo === 'string' ? r.ativo : '',
    rascunho: r.rascunho === true,
    temaCustomizado: r.temaCustomizado === true,
    temas,
  }
}

export async function lerTemasDoCarro(carIp: string): Promise<EstadoDosTemas> {
  return paraEstado(lerResposta(await invoke<string>('remote_temas_listar', { carIp })))
}

export async function ativarTemaNoCarro(carIp: string, pasta: string): Promise<EstadoDosTemas> {
  return paraEstado(lerResposta(await invoke<string>('remote_temas_ativar', { carIp, pasta })))
}

export async function rascunhoNoCarro(carIp: string, ativar: boolean): Promise<EstadoDosTemas> {
  return paraEstado(lerResposta(await invoke<string>('remote_temas_rascunho', { carIp, ativar })))
}

/**
 * O tema que o criador de temas deixou junto da pasta (`theme.deepgwt.json`). `null` para tema
 * escrito à mão ou enviado por uma versão anterior do instalador — daí não há como voltar ao
 * tema editável.
 */
export async function baixarTemaEditavel(carIp: string, pasta: string): Promise<ThemeSpec | null> {
  try {
    const b64 = await invoke<string>('remote_read_file_b64', { carIp, remotePath: `${CAR_THEME_ROOT}/${pasta}/${ARQUIVO_DO_EDITOR}` })
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
    const spec = JSON.parse(new TextDecoder().decode(bytes)) as ThemeSpec
    return spec && spec.schema === 2 && Array.isArray(spec.pages) ? spec : null
  } catch {
    return null
  }
}

/** Um resumo barato dos bytes, para saber se um arquivo mudou desde o último envio. */
function impressao(bytes: Uint8Array): string {
  let h = 2166136261
  for (let i = 0; i < bytes.length; i++) {
    h ^= bytes[i]
    h = Math.imul(h, 16777619)
  }
  return `${bytes.length}:${(h >>> 0).toString(36)}`
}

/**
 * Envia um pacote para o carro, pulando os arquivos que não mudaram desde o último envio. No
 * "Ver no carro" isso é a diferença entre mandar só o `app.html` (dezenas de kB) e mandar também
 * o papel de parede e as fontes a cada tecla.
 */
export async function enviarPacote(
  carIp: string,
  spec: ThemeSpec,
  opcoes: { rascunho?: boolean; jaEnviados?: Map<string, string>; aoProgredir?: (arquivo: string) => void } = {},
): Promise<{ enviados: string[] }> {
  const pacote = await buildThemeBundle(spec, { rascunho: opcoes.rascunho })
  const base = `${CAR_THEME_ROOT}/${pacote.folderName}`
  const enviados: string[] = []
  for (const arquivo of pacote.files) {
    const marca = impressao(arquivo.bytes)
    if (opcoes.jaEnviados?.get(arquivo.name) === marca) continue
    opcoes.aoProgredir?.(arquivo.name)
    await invoke('remote_upload_bytes_b64', {
      carIp,
      remotePath: `${base}/${arquivo.name}`,
      dataB64: bytesToBase64(arquivo.bytes),
    })
    opcoes.jaEnviados?.set(arquivo.name, marca)
    enviados.push(arquivo.name)
  }
  return { enviados }
}

export { PASTA_DO_RASCUNHO }
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
