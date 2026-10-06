// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Todas as chaves de `localStorage` do instalador, em um lugar só.
//
// Antes disto cada módulo declarava a sua própria constante e fazia o seu próprio try/catch — o
// que tornava impossível dizer com honestidade "isto é tudo que guardamos neste computador".
// O Privacy Center deriva desta tabela, então uma chave nova só existe se aparecer aqui.

export const STORAGE_KEYS = {
  carIp: 'deepgwt_dev_car_ip',
  codingBaseline: 'deepgwt_coding_baseline',
  driverProfiles: 'deepgwt_driver_profiles',
  dvrStoragePath: 'deepgwt_dvr_storage_path',
  themeDrafts: 'deepgwt_theme_drafts',
  prefs: 'deepgwt_prefs_v1',
  changeLog: 'deepgwt_change_log_v1',
  snapshots: 'deepgwt_snapshots_v1',
  vehicles: 'deepgwt_vehicles_v1',
  wmiOverrides: 'deepgwt_vin_wmi_overrides_v1',
} as const

export type StorageKeyId = keyof typeof STORAGE_KEYS

export type StorageKeyDoc = {
  what: string
  whoWrites: string
  /** Guarda algo que identifica ou descreve o veículo do usuário (VIN, IP, valores lidos do carro). */
  containsVehicleData: boolean
  legacy?: boolean
}

export const STORAGE_KEY_DOCS: Record<StorageKeyId, StorageKeyDoc> = {
  carIp: {
    what: 'Endereço IP do carro na rede, digitado por você.',
    whoWrites: 'Tela Conexão e a detecção automática do carro',
    containsVehicleData: true,
    legacy: true,
  },
  codingBaseline: {
    what: 'Ponto de restauração antigo do Coding (formato de nível único).',
    whoWrites: 'Coding (versões anteriores) — migrado para `snapshots`',
    containsVehicleData: true,
    legacy: true,
  },
  driverProfiles: {
    what: 'Perfis de motorista salvos: nome + conjunto de configurações do carro.',
    whoWrites: 'Perfis de Motorista',
    containsVehicleData: true,
  },
  dvrStoragePath: {
    what: 'Último caminho de armazenamento do DVR usado no carro.',
    whoWrites: 'DVR',
    containsVehicleData: true,
  },
  themeDrafts: {
    what: 'Rascunhos de temas do cluster (layout, cores, imagens em base64).',
    whoWrites: 'Criação de Temas / Editor de Temas',
    containsVehicleData: false,
  },
  prefs: {
    what: 'Preferências do app: nível de acesso, modo demo, IP do carro, veículo ativo.',
    whoWrites: 'App (modo Básico/Avançado, conexão)',
    containsVehicleData: true,
  },
  changeLog: {
    what: 'Histórico de alterações feitas no carro por este app: chave, valor antes/depois, data.',
    whoWrites: 'Toda escrita do app, via a sessão do veículo',
    containsVehicleData: true,
  },
  snapshots: {
    what: 'Pontos de restauração: cópias completas das configurações lidas do carro.',
    whoWrites: 'Vehicle Scan, Coding, Restaurar',
    containsVehicleData: true,
  },
  vehicles: {
    what: 'Veículos conhecidos: VIN, rótulo e o resultado da decodificação do VIN.',
    whoWrites: 'Informações do Veículo',
    containsVehicleData: true,
  },
  wmiOverrides: {
    what: 'Códigos WMI que você informou manualmente (fabricante por prefixo de VIN).',
    whoWrites: 'Informações do Veículo',
    containsVehicleData: true,
  },
}

/** Leitura tolerante: `localStorage` pode estar indisponível (janela privativa, dados bloqueados). */
export function readRaw(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

export function writeRaw(key: string, value: string): boolean {
  try {
    localStorage.setItem(key, value)
    return true
  } catch {
    return false
  }
}

export function removeRaw(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // sem localStorage não há o que remover
  }
}

/** Tamanho real ocupado por cada chave, para o Privacy Center mostrar números, não promessas. */
export function measureLocalStorage(): Array<{
  id: StorageKeyId
  key: string
  bytes: number
  present: boolean
}> {
  return (Object.keys(STORAGE_KEYS) as StorageKeyId[]).map((id) => {
    const key = STORAGE_KEYS[id]
    const raw = readRaw(key)
    return { id, key, bytes: raw ? new Blob([raw]).size : 0, present: raw !== null }
  })
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
