// A conversa com o carro sobre o tema do HUD: `GET/POST /hud/tema` no GwLinkServer (Alpha 5.9).
//
// COMO SABER QUE O CARRO É ANTIGO
//
// Um OPEN GW anterior à 5.9 não tem a rota: o servidor cai no ramo padrão e devolve a telemetria,
// que é JSON sem `ok`. Tratar isso como "aplicado" mentiria para o usuário; tratar como erro de
// rede o mandaria procurar o problema no lugar errado. A mensagem diz o que fazer: atualizar o app.

import { invoke } from '@tauri-apps/api/core'
import { type HudTema, normalizarTemaHud, paraCarro } from './model'

export const CARRO_ANTIGO =
  'O OPEN GW deste carro é anterior à Alpha 5.9 e ainda não aceita temas de HUD. Atualize o app ' +
  'no carro (Instalar e manter → Atualizar) e tente de novo.'

function lerResposta(texto: string): Record<string, unknown> {
  let json: unknown
  try {
    json = JSON.parse(texto)
  } catch {
    throw new Error('O carro respondeu algo que não é JSON.')
  }
  if (!json || typeof json !== 'object' || !('ok' in json)) throw new Error(CARRO_ANTIGO)
  return json as Record<string, unknown>
}

export async function aplicarTemaNoCarro(carIp: string, tema: HudTema): Promise<void> {
  const texto = await invoke<string>('remote_hud_theme_apply', { carIp, temaJson: JSON.stringify(paraCarro(tema)) })
  const r = lerResposta(texto)
  if (r.ok !== true) throw new Error(typeof r.error === 'string' ? r.error : 'O carro recusou o tema.')
}

/** O HUD como está no carro agora, como tema. */
export async function lerTemaDoCarro(carIp: string): Promise<HudTema> {
  const texto = await invoke<string>('remote_hud_theme_get', { carIp })
  const r = lerResposta(texto)
  if (r.ok !== true) throw new Error(typeof r.error === 'string' ? r.error : 'O carro não devolveu o tema.')
  const tema = normalizarTemaHud(r.tema)
  return { ...tema, nome: tema.nome === 'Tema sem nome' ? 'HUD do carro' : tema.nome }
}
