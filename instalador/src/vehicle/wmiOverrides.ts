// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// WMI informados manualmente pelo usuário.
//
// A tabela oficial que liga um WMI a um fabricante é licenciada e não está no projeto, então o
// decodificador devolve "fabricante desconhecido" — de propósito. Aqui o usuário pode dizer, para
// o WMI do carro dele, de quem ele é. O registro guarda QUEM afirmou e QUANDO, e a tela nunca
// apresenta esse nome como verificado: sai sempre rotulado como informado por você.

import { STORAGE_KEYS, readRaw, writeRaw } from '../state/storageKeys'
import type { WmiOverrides } from './vin'

type Record_ = {
  version: 1
  /** WMI (3 caracteres) -> o que o usuário digitou. */
  entries: Record<string, { name: string; addedAt: number }>
}

const EMPTY: Record_ = { version: 1, entries: {} }

function load(): Record_ {
  const raw = readRaw(STORAGE_KEYS.wmiOverrides)
  if (!raw) return EMPTY
  try {
    const parsed = JSON.parse(raw) as Partial<Record_>
    const entries: Record_['entries'] = {}
    for (const [wmi, v] of Object.entries(parsed.entries ?? {})) {
      if (typeof v?.name === 'string' && v.name.trim()) {
        entries[wmi.toUpperCase()] = {
          name: v.name.trim(),
          addedAt: typeof v.addedAt === 'number' ? v.addedAt : 0,
        }
      }
    }
    return { version: 1, entries }
  } catch {
    return EMPTY
  }
}

export function loadWmiOverrides(): WmiOverrides {
  return Object.fromEntries(Object.entries(load().entries).map(([k, v]) => [k, v.name]))
}

export function listWmiOverrides(): Array<{ wmi: string; name: string; addedAt: number }> {
  return Object.entries(load().entries)
    .map(([wmi, v]) => ({ wmi, ...v }))
    .sort((a, b) => a.wmi.localeCompare(b.wmi))
}

export function setWmiOverride(wmi: string, name: string): void {
  const rec = load()
  const id = wmi.toUpperCase().trim()
  if (id.length !== 3) return
  if (name.trim()) rec.entries[id] = { name: name.trim(), addedAt: Date.now() }
  else delete rec.entries[id]
  writeRaw(STORAGE_KEYS.wmiOverrides, JSON.stringify(rec))
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
