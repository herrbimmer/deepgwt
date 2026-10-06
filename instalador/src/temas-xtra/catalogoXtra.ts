// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O catálogo XTRA: os temas prontos que o instalador leva embutidos (public/temas-xtra, gerado por
// scripts/importar-temas-xtra.mjs) e o envio deles para o carro.
//
// No carro, todo tema que não veio no APK aparece na seção "Temas XTRA" (CastaDoTema no app), então
// enviar daqui é só gravar a pasta em files/themes — o mesmo /files/put do "Importar tema HTML".

import { invoke } from '@tauri-apps/api/core'
import { CAR_THEME_ROOT, bytesToBase64 } from '../theme-editor/exportTheme'
import type { TemaNoCarro } from '../theme-editor/carro/temasNoCarro'

export type TipoDeGrupo = 'estilos' | 'clusters' | 'marcas'
export type Fundo = 'escuro' | 'claro'
export type CorDeDestaque = 'vermelho' | 'ambar' | 'verde' | 'ciano' | 'azul' | 'roxo' | 'colorido' | 'mono'

/** fundo e cor saem da miniatura na importação (scripts/temas-xtra-cor.mjs). */
export type TemaXtra = { nome: string; versao: string; descricao: string; bytes: number; fundo: Fundo; cor: CorDeDestaque }
export type GrupoXtra = { id: string; nome: string; tipo: TipoDeGrupo; temas: string[] }
export type CatalogoXtra = { versao: string; total: number; grupos: GrupoXtra[]; temas: Record<string, TemaXtra> }

export type Situacao = 'ausente' | 'no-carro' | 'desatualizado'

export const RAIZ_DO_CATALOGO = '/temas-xtra'

/** theme.xml por último: o carro só lista uma pasta que tem theme.xml, então ela nunca aparece pela metade. */
export const ORDEM_DE_ENVIO = ['app.html', 'thumbnail.png', 'theme.xml'] as const

/** Acima disto o carrossel do carro começa a pesar (cada cartão é uma miniatura). */
export const LIMITE_CONFORTAVEL_NO_CARRO = 60

const PASTA_VALIDA = /^[a-z0-9][a-z0-9-]{0,63}$/

export const miniaturaDe = (pasta: string) => `${RAIZ_DO_CATALOGO}/${pasta}/thumbnail.png`

export async function carregarCatalogo(): Promise<CatalogoXtra> {
  const r = await fetch(`${RAIZ_DO_CATALOGO}/catalogo.json`)
  if (!r.ok) throw new Error(`O catálogo XTRA não veio com este instalador (HTTP ${r.status}).`)
  return (await r.json()) as CatalogoXtra
}

function compararVersoes(a: string, b: string): number {
  const pa = a.split('.').map((n) => parseInt(n, 10) || 0)
  const pb = b.split('.').map((n) => parseInt(n, 10) || 0)
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0)
    if (d) return Math.sign(d)
  }
  return 0
}

/** Onde o tema do catálogo está em relação ao carro. Sem leitura do carro, tudo é 'ausente'. */
export function situacaoNoCarro(pasta: string, tema: TemaXtra, noCarro: Map<string, TemaNoCarro> | null): Situacao {
  const t = noCarro?.get(pasta)
  if (!t) return 'ausente'
  return compararVersoes(tema.versao, t.versao) > 0 ? 'desatualizado' : 'no-carro'
}

export const grupoDoTema = (catalogo: CatalogoXtra, pasta: string) => catalogo.grupos.find((g) => g.temas.includes(pasta))

/**
 * Busca sem acento em pasta, nome, descrição e nome do grupo. Várias palavras: todas precisam
 * aparecer, em qualquer ordem ("ponteiro âmbar" acha o tema de ponteiro que fala em âmbar).
 */
export function filtrarTemas(catalogo: CatalogoXtra, pastas: string[], busca: string): string[] {
  const palavras = normalizar(busca).split(/\s+/).filter(Boolean)
  if (!palavras.length) return pastas
  return pastas.filter((p) => {
    const t = catalogo.temas[p]
    const texto = normalizar(`${p} ${t?.nome ?? ''} ${t?.descricao ?? ''} ${grupoDoTema(catalogo, p)?.nome ?? ''}`)
    return palavras.every((q) => texto.includes(q))
  })
}

export type FiltroNoCarro = 'todos' | 'ausentes' | 'no-carro' | 'desatualizado'
export type Filtros = {
  busca: string
  tipo: TipoDeGrupo | null
  grupo: string | null
  fundo: Fundo | null
  cor: CorDeDestaque | null
  noCarro: FiltroNoCarro
}
export const SEM_FILTROS: Filtros = { busca: '', tipo: null, grupo: null, fundo: null, cor: null, noCarro: 'todos' }

export const filtrosAtivos = (f: Filtros) =>
  (f.busca.trim() ? 1 : 0) + (f.tipo ? 1 : 0) + (f.grupo ? 1 : 0) + (f.fundo ? 1 : 0) + (f.cor ? 1 : 0) + (f.noCarro !== 'todos' ? 1 : 0)

const passaNoCarro = (filtro: FiltroNoCarro, s: Situacao) =>
  filtro === 'todos' ||
  (filtro === 'ausentes' && s === 'ausente') ||
  (filtro === 'no-carro' && s !== 'ausente') ||
  (filtro === 'desatualizado' && s === 'desatualizado')

/**
 * Os temas que passam em todos os filtros, na ordem do catálogo. `ignorar` deixa um filtro de fora:
 * é assim que cada faceta conta quantos temas cada opção dela daria com os OUTROS filtros ativos.
 */
export function aplicarFiltros(
  catalogo: CatalogoXtra,
  f: Filtros,
  situacaoDe: (pasta: string) => Situacao,
  ignorar?: keyof Filtros,
): string[] {
  const grupos = catalogo.grupos.filter(
    (g) => (ignorar === 'tipo' || !f.tipo || g.tipo === f.tipo) && (ignorar === 'grupo' || !f.grupo || g.id === f.grupo),
  )
  const pastas = grupos.flatMap((g) => g.temas)
  return filtrarTemas(catalogo, pastas, ignorar === 'busca' ? '' : f.busca).filter((p) => {
    const t = catalogo.temas[p]
    return (
      (ignorar === 'fundo' || !f.fundo || t.fundo === f.fundo) &&
      (ignorar === 'cor' || !f.cor || t.cor === f.cor) &&
      (ignorar === 'noCarro' || passaNoCarro(f.noCarro, situacaoDe(p)))
    )
  })
}

export function contarPor<K>(pastas: string[], chave: (pasta: string) => K): Map<K, number> {
  const m = new Map<K, number>()
  for (const p of pastas) m.set(chave(p), (m.get(chave(p)) ?? 0) + 1)
  return m
}

export type Ordem = 'grupo' | 'nome' | 'leves' | 'pesados'

export function ordenarPastas(catalogo: CatalogoXtra, pastas: string[], ordem: Ordem): string[] {
  if (ordem === 'grupo') return pastas
  const t = catalogo.temas
  const lista = [...pastas]
  if (ordem === 'nome') return lista.sort((a, b) => t[a].nome.localeCompare(t[b].nome, 'pt-BR'))
  return lista.sort((a, b) => (ordem === 'leves' ? t[a].bytes - t[b].bytes : t[b].bytes - t[a].bytes))
}

const normalizar = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

export const formatarBytes = (n: number) =>
  n >= 1e6 ? `${(n / 1e6).toFixed(1).replace('.', ',')} MB` : `${Math.max(1, Math.round(n / 1e3))} kB`

/** Envia UM tema do catálogo para files/themes/<pasta> no carro. */
export async function enviarTemaXtra(carIp: string, catalogo: CatalogoXtra, pasta: string): Promise<void> {
  if (!PASTA_VALIDA.test(pasta) || !catalogo.temas[pasta]) throw new Error(`"${pasta}" não é um tema do catálogo.`)
  for (const arquivo of ORDEM_DE_ENVIO) {
    const r = await fetch(`${RAIZ_DO_CATALOGO}/${pasta}/${arquivo}`)
    if (!r.ok) throw new Error(`${pasta}/${arquivo} faltando no instalador (HTTP ${r.status}).`)
    const bytes = new Uint8Array(await r.arrayBuffer())
    await invoke('remote_upload_bytes_b64', {
      carIp,
      remotePath: `${CAR_THEME_ROOT}/${pasta}/${arquivo}`,
      dataB64: bytesToBase64(bytes),
    })
  }
}

export type ResultadoDoLote = { feitos: string[]; falhas: { pasta: string; erro: string }[] }

/**
 * Envia (ou apaga) uma lista, um tema de cada vez: a conexão é a do carro, e mandar em paralelo só
 * disputaria o mesmo Wi-Fi. Uma falha não para o lote; cancelar para antes do próximo.
 */
export async function emLote(
  pastas: string[],
  acao: (pasta: string) => Promise<void>,
  aoProgredir: (feitos: number, atual: string | null) => void,
  cancelado: () => boolean = () => false,
): Promise<ResultadoDoLote> {
  const res: ResultadoDoLote = { feitos: [], falhas: [] }
  for (let i = 0; i < pastas.length; i++) {
    if (cancelado()) break
    aoProgredir(i, pastas[i])
    try {
      await acao(pastas[i])
      res.feitos.push(pastas[i])
    } catch (e) {
      res.falhas.push({ pasta: pastas[i], erro: e instanceof Error ? e.message : String(e) })
    }
  }
  aoProgredir(res.feitos.length + res.falhas.length, null)
  return res
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
