// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Identidade do veículo: junta o que o carro respondeu com o que o usuário digitou, e mantém
// as duas coisas distinguíveis o tempo todo.
//
// Regra que atravessa o arquivo: um campo que o carro não respondeu sai como `null`, e a tela
// escreve "não coletado". Nunca é preenchido com um valor plausível.

import { useCallback, useEffect, useMemo, useState } from 'react'
import { usePrefs } from '../state/PrefsProvider'
import { useCarSettings } from '../state/VehicleSession'
import { decodeVin, normalizeVin, type VinDecodeResult, type VinSource } from './vin'
import { loadWmiOverrides } from './wmiOverrides'
import { ALL_IDENTITY_KEYS } from './identityKeys'

export type VehicleIdentityApi = {
  /** VIN em uso, venha do carro ou da digitação. `null` quando não há nenhum. */
  vin: string | null
  vinSource: VinSource | null
  decode: VinDecodeResult | null
  /** Valor cru de uma chave de identidade. `null` = o carro não respondeu esta chave. */
  read: (key: string) => string | null
  /** Quantas das chaves de identidade vieram de fato nesta sessão. */
  collected: number
  total: number
  setManualVin: (vin: string) => void
  clearManualVin: () => void
  /** Recarrega os WMI informados manualmente depois de o usuário registrar um. */
  refreshOverrides: () => void
}

export function useVehicleIdentity(): VehicleIdentityApi {
  const { prefs, update } = usePrefs()
  const session = useCarSettings()
  const [overrides, setOverrides] = useState(loadWmiOverrides)

  const fromVehicle = session.vin?.trim() || null

  // O VIN lido do carro vira o veículo ativo, para que a tela continue útil depois de desconectar.
  // Em modo demo isso NÃO acontece: um VIN sintético não pode virar o "seu carro".
  useEffect(() => {
    if (session.source !== 'live') return
    if (!fromVehicle || fromVehicle === prefs.activeVin) return
    update({ activeVin: fromVehicle, activeVinSource: 'vehicle' })
  }, [fromVehicle, session.source, prefs.activeVin, update])

  const { vin, vinSource } = useMemo<{ vin: string | null; vinSource: VinSource | null }>(() => {
    if (fromVehicle) return { vin: fromVehicle, vinSource: session.source === 'demo' ? 'demo' : 'vehicle' }
    if (prefs.activeVin) return { vin: prefs.activeVin, vinSource: prefs.activeVinSource ?? 'manual' }
    return { vin: null, vinSource: null }
  }, [fromVehicle, session.source, prefs.activeVin, prefs.activeVinSource])

  const decode = useMemo(() => (vin ? decodeVin(vin, overrides) : null), [vin, overrides])

  const read = useCallback(
    (key: string) => {
      const v = session.values[key]
      return v === undefined || v === '' ? null : v
    },
    [session.values],
  )

  const collected = useMemo(
    () => ALL_IDENTITY_KEYS.filter((k) => read(k) !== null).length,
    [read],
  )

  const setManualVin = useCallback(
    (raw: string) => update({ activeVin: normalizeVin(raw) || null, activeVinSource: 'manual' }),
    [update],
  )
  const clearManualVin = useCallback(
    () => update({ activeVin: null, activeVinSource: null }),
    [update],
  )

  return {
    vin,
    vinSource,
    decode,
    read,
    collected,
    total: ALL_IDENTITY_KEYS.length,
    setManualVin,
    clearManualVin,
    refreshOverrides: useCallback(() => setOverrides(loadWmiOverrides()), []),
  }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
