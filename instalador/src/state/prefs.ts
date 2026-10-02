// Preferências do instalador, no mesmo padrão do módulo mais maduro que já existia no projeto
// (`theme-editor/storage.ts`): registro versionado, migração na leitura, `updatedAt`, e nunca
// estourar quando o `localStorage` não estiver disponível.

import { STORAGE_KEYS, readRaw, writeRaw } from './storageKeys'
import { ehIpDoCarro } from '../lib/redeDoCarro'

export type SettingsSource = 'live' | 'demo'

export type Prefs = {
  version: 1
  /**
   * Quando o usuário confirmou o aviso das ferramentas que escrevem cru no carro. `null` = ainda
   * não confirmou. Era o aviso do "modo Avançado"; desde a Alpha 8.0 o nível vem do carro e o aviso
   * ficou sozinho (ver `tiers/NivelProvider`). O nome continua para o registro antigo valer.
   */
  advancedAcknowledgedAt: number | null
  source: SettingsSource
  carIp: string
  activeVin: string | null
  /** Se o VIN ativo foi lido do carro ou digitado à mão. Decide o rótulo de procedência na UI. */
  activeVinSource: 'vehicle' | 'manual' | null
  updatedAt: number
}

const DEFAULTS: Prefs = {
  version: 1,
  advancedAcknowledgedAt: null,
  source: 'live',
  carIp: '',
  activeVin: null,
  activeVinSource: null,
  updatedAt: 0,
}

/**
 * Registro como ele pode estar gravado: inclui o modo Básico/Avançado (6.3.1–7.9) e os campos dos
 * três níveis antigos (até a 6.3.0).
 */
type StoredPrefs = Omit<Partial<Prefs>, 'advancedAcknowledgedAt'> & {
  tier?: unknown
  advancedAcknowledgedAt?: unknown
  proAcknowledgedAt?: unknown
  expertAcknowledgedAt?: unknown
}

const num = (v: unknown): number | null => (typeof v === 'number' ? v : null)

/**
 * O aviso das ferramentas de risco, de qualquer registro antigo.
 *
 * Vale o do modo Avançado (6.3.1–7.9) ou, antes dele, o da antiga Engenharia ("escrever valores
 * desconhecidos pode desativar funções") — é o mesmo risco. O aviso da antiga Oficina era OUTRO
 * (telas não validadas) e não conta. O modo em si (`tier`) sai do registro: desde a 8.0 o nível
 * vem do carro, não de um seletor.
 */
export function migrateAck(r: StoredPrefs): number | null {
  return num(r.advancedAcknowledgedAt) ?? num(r.expertAcknowledgedAt)
}

export function migrate(raw: unknown): Prefs {
  const r = (raw ?? {}) as StoredPrefs
  return {
    version: 1,
    advancedAcknowledgedAt: migrateAck(r),
    source: r.source === 'demo' ? 'demo' : 'live',
    // Alpha 9.4: só o hotspot do carro vale. Um IP da rede de casa guardado por uma versão
    // anterior é descartado aqui, e a descoberta automática acha o do hotspot.
    carIp: typeof r.carIp === 'string' && ehIpDoCarro(r.carIp) ? r.carIp.trim() : DEFAULTS.carIp,
    activeVin: typeof r.activeVin === 'string' && r.activeVin ? r.activeVin : null,
    activeVinSource:
      r.activeVinSource === 'vehicle' || r.activeVinSource === 'manual' ? r.activeVinSource : null,
    updatedAt: typeof r.updatedAt === 'number' ? r.updatedAt : 0,
  }
}

export function loadPrefs(): Prefs {
  const raw = readRaw(STORAGE_KEYS.prefs)
  if (raw) {
    try {
      return migrate(JSON.parse(raw))
    } catch {
      // registro corrompido — cai no default em vez de travar o app
    }
  }
  // Primeira execução depois do update: herda o IP que as versões anteriores guardavam solto.
  const legacyIp = readRaw(STORAGE_KEYS.carIp)
  return { ...DEFAULTS, carIp: legacyIp && ehIpDoCarro(legacyIp) ? legacyIp.trim() : '' }
}

export function savePrefs(patch: Partial<Prefs>): Prefs {
  const next: Prefs = { ...loadPrefs(), ...patch, version: 1, updatedAt: Date.now() }
  writeRaw(STORAGE_KEYS.prefs, JSON.stringify(next))
  // Continua escrevendo a chave antiga: uma build anterior instalada em paralelo (ou um rollback)
  // ainda encontra o IP onde espera. As chaves legadas nunca são apagadas por migração.
  if (next.carIp) writeRaw(STORAGE_KEYS.carIp, next.carIp)
  return next
}
