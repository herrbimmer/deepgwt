// Histórico de alterações — tudo que este app escreveu no carro.
//
// O que existia antes: nada. A tela de Coding comparava o estado atual contra um "ponto de
// restauração" de nível único e chamava aquilo de histórico. Isso mostrava o DELTA acumulado,
// não os eventos: duas mudanças na mesma chave apareciam como uma, uma mudança revertida
// desaparecia sem deixar rastro, e nenhuma escrita feita fora do Coding (ADAS, ajustes rápidos,
// perfis, DVR) era registrada em lugar nenhum.
//
// Aqui cada escrita é um evento datado e imutável, com valor antes e depois, e com o resultado
// preenchido depois que o carro responde. Uma escrita que FALHOU continua no histórico marcada
// como falha — apagá-la esconderia exatamente o caso que a pessoa precisa investigar.
//
// Fica tudo no `localStorage` deste computador. Nada é enviado para lugar nenhum; veja o Privacy
// Center, que deriva esta afirmação da tabela de chaves em `storageKeys.ts`.

import { STORAGE_KEYS, readRaw, writeRaw } from './storageKeys'

export type ChangeResult = 'pending' | 'ok' | 'error'

export type ChangeEntry = {
  id: string
  /** Epoch ms do instante em que a escrita foi disparada. */
  ts: number
  /** VIN ativo no momento da escrita, quando conhecido. Serve para separar carros diferentes. */
  vin: string | null
  carIp: string
  key: string
  /** Valor lido antes da escrita. `null` = o carro nunca tinha reportado valor para esta chave. */
  from: string | null
  to: string
  /** `demo` marca escritas sintéticas: elas nunca tocaram um veículo. */
  source: 'live' | 'demo'
  result: ChangeResult
  error?: string
  /** Rota de onde a escrita partiu, para saber qual tela originou a mudança. */
  route: string
}

type LogFile = { version: 1; entries: ChangeEntry[]; updatedAt: number }

/**
 * Teto de eventos guardados. Um evento ocupa ~200 bytes, então 2000 eventos são ~400 kB — abaixo
 * do que o `localStorage` aguenta, e o suficiente para meses de uso. Ao estourar, os mais ANTIGOS
 * saem: o histórico recente é o que serve para investigar o que acabou de acontecer.
 */
const MAX_ENTRIES = 2000

const EMPTY: LogFile = { version: 1, entries: [], updatedAt: 0 }

function isEntry(v: unknown): v is ChangeEntry {
  const e = v as Partial<ChangeEntry>
  return (
    !!e &&
    typeof e.id === 'string' &&
    typeof e.ts === 'number' &&
    typeof e.key === 'string' &&
    typeof e.to === 'string'
  )
}

/** Normaliza um registro vindo do disco ou de um arquivo importado, descartando o que não é evento. */
function migrate(raw: unknown): LogFile {
  const r = (raw ?? {}) as Partial<LogFile>
  const entries = Array.isArray(r.entries) ? r.entries.filter(isEntry) : []
  return {
    version: 1,
    entries: entries.map((e) => ({
      ...e,
      vin: typeof e.vin === 'string' && e.vin ? e.vin : null,
      carIp: typeof e.carIp === 'string' ? e.carIp : '',
      from: typeof e.from === 'string' ? e.from : null,
      source: e.source === 'demo' ? 'demo' : 'live',
      result: e.result === 'ok' || e.result === 'error' ? e.result : 'pending',
      route: typeof e.route === 'string' ? e.route : '',
    })),
    updatedAt: typeof r.updatedAt === 'number' ? r.updatedAt : 0,
  }
}

export function loadChangeLog(): ChangeEntry[] {
  const raw = readRaw(STORAGE_KEYS.changeLog)
  if (!raw) return []
  try {
    return migrate(JSON.parse(raw)).entries
  } catch {
    // Registro corrompido: devolve vazio em vez de derrubar a tela. O arquivo cru continua no
    // disco — nada é apagado por causa de uma leitura que falhou.
    return EMPTY.entries
  }
}

function persist(entries: ChangeEntry[]): ChangeEntry[] {
  const trimmed = entries.length > MAX_ENTRIES ? entries.slice(entries.length - MAX_ENTRIES) : entries
  const file: LogFile = { version: 1, entries: trimmed, updatedAt: Date.now() }
  writeRaw(STORAGE_KEYS.changeLog, JSON.stringify(file))
  return trimmed
}

// `crypto.randomUUID` não existe em contexto inseguro nem em webview antiga. O id só precisa ser
// único dentro deste arquivo, então o par tempo+aleatório basta e nunca lança.
function newId(): string {
  try {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  } catch {
    // segue para o fallback
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export type NewChange = Omit<ChangeEntry, 'id' | 'ts' | 'result'>

/** Registra a INTENÇÃO de escrever. O resultado chega depois, por `settleChange`. */
export function appendChange(input: NewChange): string {
  const entry: ChangeEntry = { ...input, id: newId(), ts: Date.now(), result: 'pending' }
  persist([...loadChangeLog(), entry])
  notify()
  return entry.id
}

/** Fecha um evento com o que o carro respondeu. Sem isto o evento fica `pending` para sempre. */
export function settleChange(id: string, result: 'ok' | 'error', error?: string): void {
  const entries = loadChangeLog()
  const i = entries.findIndex((e) => e.id === id)
  if (i === -1) return
  entries[i] = { ...entries[i], result, ...(error ? { error } : {}) }
  persist(entries)
  notify()
}

export function clearChangeLog(): void {
  persist([])
  notify()
}

export function exportChangeLog(): string {
  const file: LogFile = { version: 1, entries: loadChangeLog(), updatedAt: Date.now() }
  return JSON.stringify(file, null, 2)
}

export type ImportReport = { added: number; duplicates: number; rejected: number }

/**
 * Importa um arquivo exportado, MESCLANDO em vez de substituir.
 *
 * Eventos com id já conhecido são ignorados — reimportar o mesmo arquivo duas vezes não duplica
 * o histórico. Substituir o histórico local pelo do arquivo apagaria eventos reais deste
 * computador, que é justamente o que o histórico existe para impedir.
 */
export function importChangeLog(json: string): ImportReport {
  const parsed = JSON.parse(json) as unknown
  const incoming = migrate(parsed).entries
  const rejected =
    (Array.isArray((parsed as Partial<LogFile>)?.entries) ? (parsed as LogFile).entries.length : 0) -
    incoming.length
  const current = loadChangeLog()
  const known = new Set(current.map((e) => e.id))
  const fresh = incoming.filter((e) => !known.has(e.id))
  const merged = [...current, ...fresh].sort((a, b) => a.ts - b.ts)
  persist(merged)
  notify()
  return { added: fresh.length, duplicates: incoming.length - fresh.length, rejected }
}

// Assinatura simples para as telas reagirem a uma escrita sem precisar de polling. O provider
// da sessão grava, a tela de histórico redesenha.
type Listener = () => void
const listeners = new Set<Listener>()

export function subscribeChangeLog(fn: Listener): () => void {
  listeners.add(fn)
  return () => {
    listeners.delete(fn)
  }
}

function notify() {
  for (const fn of listeners) fn()
}
