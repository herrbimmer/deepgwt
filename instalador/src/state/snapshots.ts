// Pontos de restauração — cópias datadas e nomeadas das configurações lidas do carro.
//
// O QUE ISTO SUBSTITUI: o Coding guardava UM ponto de restauração, sem nome e sem data, na chave
// `opengw_coding_baseline`. Ele era gravado na primeira leitura e nunca mais atualizado, o que
// produzia três problemas concretos:
//   1. não dava para ter um ponto "antes de mexer no ADAS" e outro "antes de mexer na luz";
//   2. limpar o `localStorage` apagava o único ponto existente sem aviso nenhum, e a pessoa só
//      descobria quando precisava voltar atrás;
//   3. como não tinha data, não havia como saber de quando era aquele "estado de fábrica".
//
// O ponto antigo NÃO é apagado: `migrateLegacyBaseline()` o converte em um ponto nomeado, e a
// chave original continua no disco (ver a política de chaves legadas em `prefs.ts`).
//
// LIMITE: um ponto de restauração é o que o carro RESPONDEU naquele instante. Não é um backup do
// carro e não alcança nada fora do ControlService — nem ECU, nem odômetro, nem chave codificada.
// Restaurar reescreve chave por chave, e uma chave que o carro se recusar a gravar continua
// diferente. O relatório de restauração diz quais foram.

import { STORAGE_KEYS, readRaw, writeRaw } from './storageKeys'

export type SnapshotKind =
  /** Criado pelo usuário, com nome. */
  | 'manual'
  /** Convertido do antigo `opengw_coding_baseline`, que não tinha nome nem data. */
  | 'migrated-baseline'

export type Snapshot = {
  id: string
  name: string
  /** Epoch ms da criação. Para o ponto migrado, é o instante da migração — ver `dateIsApproximate`. */
  ts: number
  vin: string | null
  carIp: string
  source: 'live' | 'demo'
  kind: SnapshotKind
  /**
   * `true` quando a data é a da migração, e não a da captura: o formato antigo não guardava
   * data nenhuma, e inventar uma seria pior do que admitir que ela se perdeu.
   */
  dateIsApproximate: boolean
  values: Record<string, string>
}

type SnapshotFile = { version: 1; snapshots: Snapshot[]; updatedAt: number }

/**
 * Teto de pontos guardados. Um snapshot de 818 chaves ocupa ~35 kB, então 20 pontos são ~700 kB —
 * dentro do orçamento típico de 5 MB do `localStorage`, com folga para temas e perfis. Ao
 * estourar, o mais antigo é removido, e a UI avisa antes de criar.
 */
export const MAX_SNAPSHOTS = 20

function newId(): string {
  try {
    if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  } catch {
    // segue para o fallback
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

function isSnapshot(v: unknown): v is Snapshot {
  const s = v as Partial<Snapshot>
  return !!s && typeof s.id === 'string' && typeof s.ts === 'number' && !!s.values && typeof s.values === 'object'
}

function migrate(raw: unknown): SnapshotFile {
  const r = (raw ?? {}) as Partial<SnapshotFile>
  const list = Array.isArray(r.snapshots) ? r.snapshots.filter(isSnapshot) : []
  return {
    version: 1,
    snapshots: list.map((s) => ({
      ...s,
      name: typeof s.name === 'string' && s.name ? s.name : 'Ponto sem nome',
      vin: typeof s.vin === 'string' && s.vin ? s.vin : null,
      carIp: typeof s.carIp === 'string' ? s.carIp : '',
      source: s.source === 'demo' ? 'demo' : 'live',
      kind: s.kind === 'migrated-baseline' ? 'migrated-baseline' : 'manual',
      dateIsApproximate: s.dateIsApproximate === true,
    })),
    updatedAt: typeof r.updatedAt === 'number' ? r.updatedAt : 0,
  }
}

export function loadSnapshots(): Snapshot[] {
  const raw = readRaw(STORAGE_KEYS.snapshots)
  if (!raw) return []
  try {
    return migrate(JSON.parse(raw)).snapshots.sort((a, b) => b.ts - a.ts)
  } catch {
    return []
  }
}

function persist(list: Snapshot[]): Snapshot[] {
  const sorted = [...list].sort((a, b) => b.ts - a.ts)
  // Ao estourar o teto, corta pelo fim (os mais antigos).
  const trimmed = sorted.slice(0, MAX_SNAPSHOTS)
  writeRaw(STORAGE_KEYS.snapshots, JSON.stringify({ version: 1, snapshots: trimmed, updatedAt: Date.now() }))
  notify()
  return trimmed
}

export type CreateSnapshotInput = {
  name: string
  vin: string | null
  carIp: string
  source: 'live' | 'demo'
  values: Record<string, string>
}

export function createSnapshot(input: CreateSnapshotInput): Snapshot {
  const snap: Snapshot = {
    id: newId(),
    name: input.name.trim() || `Ponto de ${new Date().toLocaleString('pt-BR')}`,
    ts: Date.now(),
    vin: input.vin,
    carIp: input.carIp,
    source: input.source,
    kind: 'manual',
    dateIsApproximate: false,
    values: { ...input.values },
  }
  persist([...loadSnapshots(), snap])
  return snap
}

export function deleteSnapshot(id: string): void {
  persist(loadSnapshots().filter((s) => s.id !== id))
}

export function renameSnapshot(id: string, name: string): void {
  persist(loadSnapshots().map((s) => (s.id === id ? { ...s, name: name.trim() || s.name } : s)))
}

/**
 * Converte o ponto de restauração antigo (`opengw_coding_baseline`) em um ponto nomeado.
 *
 * Roda uma vez: se já existe um ponto migrado, não faz nada. A chave antiga NÃO é apagada — uma
 * build anterior instalada em paralelo ainda a procura onde sempre esteve.
 */
export function migrateLegacyBaseline(vin: string | null, carIp: string): Snapshot | null {
  const existing = loadSnapshots()
  if (existing.some((s) => s.kind === 'migrated-baseline')) return null
  const raw = readRaw(STORAGE_KEYS.codingBaseline)
  if (!raw) return null
  let values: Record<string, string>
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    values = parsed as Record<string, string>
  } catch {
    return null
  }
  if (Object.keys(values).length === 0) return null
  const snap: Snapshot = {
    id: newId(),
    name: 'Ponto de restauração do Coding (versão anterior)',
    ts: Date.now(),
    vin,
    carIp,
    source: 'live',
    kind: 'migrated-baseline',
    // O formato antigo não guardava data. Esta é a data da MIGRAÇÃO, e a UI diz isso.
    dateIsApproximate: true,
    values,
  }
  persist([...existing, snap])
  return snap
}

export type SnapshotDiff = {
  /** Chave existe nos dois lados e o valor mudou. */
  changed: Array<{ key: string; from: string; to: string }>
  /** Estava no ponto e o carro não reporta mais. */
  missingNow: string[]
  /** O carro reporta hoje e não estava no ponto. */
  newSinceSnapshot: string[]
  /** Iguais nos dois lados. */
  unchanged: number
}

/** Compara um ponto com o que o carro responde agora. `from` = ponto, `to` = agora. */
export function diffSnapshot(snap: Snapshot, current: Record<string, string>): SnapshotDiff {
  const changed: SnapshotDiff['changed'] = []
  const missingNow: string[] = []
  let unchanged = 0
  for (const [key, was] of Object.entries(snap.values)) {
    if (!(key in current)) {
      missingNow.push(key)
      continue
    }
    const now = current[key] ?? ''
    if (now === was) unchanged += 1
    else changed.push({ key, from: was, to: now })
  }
  const newSinceSnapshot = Object.keys(current).filter((k) => !(k in snap.values))
  changed.sort((a, b) => a.key.localeCompare(b.key))
  return { changed, missingNow: missingNow.sort(), newSinceSnapshot: newSinceSnapshot.sort(), unchanged }
}

export function exportSnapshot(snap: Snapshot): string {
  return JSON.stringify({ version: 1, snapshots: [snap], updatedAt: Date.now() }, null, 2)
}

export type SnapshotImportReport = { added: number; duplicates: number; rejected: number }

export function importSnapshots(json: string): SnapshotImportReport {
  const parsed = JSON.parse(json) as unknown
  const incoming = migrate(parsed).snapshots
  const declared = Array.isArray((parsed as Partial<SnapshotFile>)?.snapshots)
    ? (parsed as SnapshotFile).snapshots.length
    : 0
  const current = loadSnapshots()
  const known = new Set(current.map((s) => s.id))
  const fresh = incoming.filter((s) => !known.has(s.id))
  persist([...current, ...fresh])
  return { added: fresh.length, duplicates: incoming.length - fresh.length, rejected: declared - incoming.length }
}

type Listener = () => void
const listeners = new Set<Listener>()

export function subscribeSnapshots(fn: Listener): () => void {
  listeners.add(fn)
  return () => {
    listeners.delete(fn)
  }
}

function notify() {
  for (const fn of listeners) fn()
}
