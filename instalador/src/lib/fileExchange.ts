// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Salvar e abrir arquivos do app (histórico, pontos de restauração, relatórios).
//
// Duas camadas, porque o app roda em dois contextos diferentes e isso não pode virar uma mentira
// na interface:
//   - dentro do Tauri: seletor nativo + gravação real em disco, via `write_local_file_b64`;
//   - no navegador (`npm run dev`, sem runtime Tauri): `invoke` não existe. Em vez de falhar
//     silenciosamente, caímos para download do navegador / área de transferência e DIZEMOS qual
//     caminho foi usado, para ninguém achar que salvou um arquivo que não existe.

import { invoke } from '@tauri-apps/api/core'
import { open, save } from '@tauri-apps/plugin-dialog'

export type SaveOutcome =
  | { ok: true; how: 'file'; path: string }
  | { ok: true; how: 'download'; path: string }
  | { ok: true; how: 'clipboard'; path: null }
  | { ok: false; how: 'cancelled' | 'failed'; error?: string }

/** `true` quando há runtime Tauri. No Vite puro, `__TAURI_INTERNALS__` não existe. */
export const hasTauri = () =>
  typeof window !== 'undefined' && '__TAURI_INTERNALS__' in (window as unknown as Record<string, unknown>)

// `btoa` quebra em qualquer caractere fora de latin-1, e os textos do app têm acento. O caminho
// correto é codificar para UTF-8 primeiro.
function toBase64(text: string): string {
  const bytes = new TextEncoder().encode(text)
  let binary = ''
  for (const b of bytes) binary += String.fromCharCode(b)
  return btoa(binary)
}

/** Tipos de arquivo que o app grava. O relatório de oficina é HTML; o resto é JSON. */
export type SaveKind = 'json' | 'html'

const KIND: Record<SaveKind, { mime: string; filterName: string; extensions: string[] }> = {
  json: { mime: 'application/json', filterName: 'JSON', extensions: ['json'] },
  html: { mime: 'text/html;charset=utf-8', filterName: 'Página HTML', extensions: ['html'] },
}

/**
 * Grava texto em disco. O relatório de oficina passou a precisar de HTML, e a versão anterior
 * desta função só sabia JSON — o tipo de arquivo virou parâmetro em vez de nascer uma segunda
 * cópia da mesma lógica de fallback.
 */
export async function saveText(
  suggestedName: string,
  text: string,
  kind: SaveKind = 'json',
): Promise<SaveOutcome> {
  const { mime, filterName, extensions } = KIND[kind]

  if (hasTauri()) {
    try {
      const path = await save({
        defaultPath: suggestedName,
        filters: [{ name: filterName, extensions }],
      })
      if (!path) return { ok: false, how: 'cancelled' }
      await invoke('write_local_file_b64', { path, dataB64: toBase64(text) })
      return { ok: true, how: 'file', path }
    } catch (e) {
      return { ok: false, how: 'failed', error: String(e) }
    }
  }

  // Navegador: download comum.
  try {
    const url = URL.createObjectURL(new Blob([text], { type: mime }))
    const a = document.createElement('a')
    a.href = url
    a.download = suggestedName
    a.click()
    URL.revokeObjectURL(url)
    return { ok: true, how: 'download', path: suggestedName }
  } catch {
    // segue para a área de transferência
  }
  try {
    await navigator.clipboard.writeText(text)
    return { ok: true, how: 'clipboard', path: null }
  } catch (e) {
    return { ok: false, how: 'failed', error: String(e) }
  }
}

export const saveJson = (suggestedName: string, json: string): Promise<SaveOutcome> =>
  saveText(suggestedName, json, 'json')

export type OpenOutcome =
  | { ok: true; json: string; path: string | null }
  | { ok: false; how: 'cancelled' | 'failed' | 'unavailable'; error?: string }

export async function openJson(): Promise<OpenOutcome> {
  if (hasTauri()) {
    try {
      const path = await open({ multiple: false, filters: [{ name: 'JSON', extensions: ['json'] }] })
      if (!path || typeof path !== 'string') return { ok: false, how: 'cancelled' }
      const b64 = await invoke<string>('read_local_file_b64', { path })
      const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0))
      return { ok: true, json: new TextDecoder().decode(bytes), path }
    } catch (e) {
      return { ok: false, how: 'failed', error: String(e) }
    }
  }
  // No navegador, quem abre o arquivo é o <input type="file"> da própria tela — este módulo não
  // tem como abrir um seletor sem um elemento no DOM.
  return { ok: false, how: 'unavailable' }
}

export const SAVE_MESSAGE: Record<'file' | 'download' | 'clipboard', (path: string | null) => string> = {
  file: (p) => `Salvo em ${p}`,
  download: (p) => `Baixado como ${p} pelo navegador (sem runtime Tauri nesta janela).`,
  clipboard: () => 'Copiado para a área de transferência — nenhum arquivo foi gravado em disco.',
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
