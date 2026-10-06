// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Compartilhar, lembrar e mostrar temas: o pacote `.deepgwt-tema`, o histórico de gravações e as
// miniaturas da lista.
//
// O PACOTE é um JSON com o `ThemeSpec` inteiro e uma miniatura. As imagens já viajam dentro do
// tema como `data:` URL (logo, fundo de página, fundo personalizado), então um arquivo só basta —
// não precisa de zip, e abrir o pacote num editor de texto mostra exatamente o que tem nele. Um
// `.json` exportado por versão anterior do editor também abre: é o `ThemeSpec` sem o envelope.
//
// O HISTÓRICO fica no IndexedDB e não no armazenamento local: vinte versões de um tema com papel
// de parede próprio passam fácil dos 5 MB que o armazenamento local aguenta, e estourar ali
// derrubaria também a gravação do tema.

import type { ThemeSpec } from './model/spec'
import { buildThumbnail, bytesToBase64 } from './exportTheme'
import { INSTALLER_VERSION } from '../lib/version'

// --- pacote ---------------------------------------------------------------------------------

export const EXTENSAO_DO_PACOTE = 'deepgwt-tema'
const FORMATO = 'deepgwt-tema'

export type PacoteDeTema = {
  formato: typeof FORMATO
  versao: 1
  criadoEm: number
  /** Versão do instalador que gerou — ajuda a entender um pacote que não abre. */
  instalador: string
  miniatura: string | null
  spec: ThemeSpec
}

export function empacotar(spec: ThemeSpec, miniatura: string | null): string {
  const pacote: PacoteDeTema = { formato: FORMATO, versao: 1, criadoEm: Date.now(), instalador: INSTALLER_VERSION, miniatura, spec }
  return JSON.stringify(pacote)
}

/** Aceita o pacote novo e o `.json` antigo (o `ThemeSpec` puro). Lança com uma mensagem legível. */
export function desempacotar(texto: string): { spec: ThemeSpec; miniatura: string | null } {
  let bruto: unknown
  try {
    bruto = JSON.parse(texto)
  } catch {
    throw new Error('O arquivo não é um tema: não é JSON.')
  }
  const o = bruto as Partial<PacoteDeTema> & Partial<ThemeSpec>
  const spec = o && o.formato === FORMATO ? o.spec : (o as ThemeSpec)
  if (!spec || spec.schema !== 2 || !Array.isArray(spec.pages) || !spec.pages.length) {
    throw new Error('O arquivo não é um tema deste editor (ou é de uma versão que este instalador não conhece).')
  }
  return { spec, miniatura: o.formato === FORMATO && typeof o.miniatura === 'string' ? o.miniatura : null }
}

/** Nome de arquivo sugerido: o nome do tema sem acento nem símbolo. */
export const nomeDoArquivo = (spec: ThemeSpec, extensao: string) =>
  `${spec.name.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '').toLowerCase() || 'tema'}.${extensao}`

// --- miniatura ------------------------------------------------------------------------------

/** Miniatura 480×180 em PNG, como `data:` URL — a mesma que vai para o carro. */
export async function gerarMiniatura(spec: ThemeSpec): Promise<string> {
  return `data:image/png;base64,${bytesToBase64(await buildThumbnail(spec))}`
}

const CHAVE_MINIATURAS = 'deepgwt_theme_thumbs_v1'

function lerMiniaturas(): Record<string, { src: string; em: number }> {
  try {
    const m = JSON.parse(localStorage.getItem(CHAVE_MINIATURAS) ?? '{}')
    return m && typeof m === 'object' ? m : {}
  } catch {
    return {}
  }
}

export const miniaturaDe = (temaId: string): string | null => lerMiniaturas()[temaId]?.src ?? null

export function guardarMiniatura(temaId: string, src: string) {
  const todas = lerMiniaturas()
  todas[temaId] = { src, em: Date.now() }
  try {
    localStorage.setItem(CHAVE_MINIATURAS, JSON.stringify(todas))
  } catch {
    // Cheio: fica só a deste tema. A lista mostra o fundo nos outros até serem salvos de novo.
    try {
      localStorage.setItem(CHAVE_MINIATURAS, JSON.stringify({ [temaId]: todas[temaId] }))
    } catch {
      /* sem armazenamento */
    }
  }
}

export function esquecerMiniatura(temaId: string) {
  const todas = lerMiniaturas()
  delete todas[temaId]
  try {
    localStorage.setItem(CHAVE_MINIATURAS, JSON.stringify(todas))
  } catch {
    /* nada */
  }
}

// --- histórico ------------------------------------------------------------------------------

export const VERSOES_POR_TEMA = 20

export type Versao = { chave: string; temaId: string; em: number; nome: string; elementos: number; paginas: number; spec: ThemeSpec }

const BANCO = 'deepgwt-editor-de-temas'
const LOJA = 'versoes'

function abrirBanco(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') return reject(new Error('Sem IndexedDB'))
    const pedido = indexedDB.open(BANCO, 1)
    pedido.onupgradeneeded = () => {
      const loja = pedido.result.createObjectStore(LOJA, { keyPath: 'chave' })
      loja.createIndex('temaId', 'temaId')
    }
    pedido.onsuccess = () => resolve(pedido.result)
    pedido.onerror = () => reject(pedido.error ?? new Error('IndexedDB indisponível'))
  })
}

function aguardar<T>(pedido: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    pedido.onsuccess = () => resolve(pedido.result)
    pedido.onerror = () => reject(pedido.error)
  })
}

export async function listarVersoes(temaId: string): Promise<Versao[]> {
  try {
    const banco = await abrirBanco()
    const todas = await aguardar(banco.transaction(LOJA).objectStore(LOJA).index('temaId').getAll(temaId) as IDBRequest<Versao[]>)
    banco.close()
    return todas.sort((a, b) => b.em - a.em)
  } catch {
    return []
  }
}

/** Guarda a gravação e apaga as mais antigas que as últimas [VERSOES_POR_TEMA]. */
export async function registrarVersao(spec: ThemeSpec): Promise<void> {
  try {
    const banco = await abrirBanco()
    const versao: Versao = {
      chave: `${spec.id}:${spec.updatedAt}`,
      temaId: spec.id,
      em: spec.updatedAt,
      nome: spec.name,
      elementos: spec.pages.reduce((n, p) => n + p.elements.length, 0),
      paginas: spec.pages.length,
      spec,
    }
    await aguardar(banco.transaction(LOJA, 'readwrite').objectStore(LOJA).put(versao))
    const todas = await aguardar(banco.transaction(LOJA).objectStore(LOJA).index('temaId').getAll(spec.id) as IDBRequest<Versao[]>)
    const sobrando = todas.sort((a, b) => b.em - a.em).slice(VERSOES_POR_TEMA)
    if (sobrando.length) {
      const loja = banco.transaction(LOJA, 'readwrite').objectStore(LOJA)
      for (const v of sobrando) loja.delete(v.chave)
    }
    banco.close()
  } catch {
    // Sem histórico nesta máquina: o tema continua salvo, só não dá para voltar a uma versão.
  }
}

export async function apagarHistorico(temaId: string): Promise<void> {
  try {
    const banco = await abrirBanco()
    const todas = await aguardar(banco.transaction(LOJA).objectStore(LOJA).index('temaId').getAll(temaId) as IDBRequest<Versao[]>)
    const loja = banco.transaction(LOJA, 'readwrite').objectStore(LOJA)
    for (const v of todas) loja.delete(v.chave)
    banco.close()
  } catch {
    /* nada */
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
