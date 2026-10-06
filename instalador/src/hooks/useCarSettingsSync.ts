// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useCarSettings } from '../state/VehicleSession'
export type { SyncStatus, FieldStatus } from '../state/VehicleSession'

/**
 * @deprecated Use `useCarSettings()` de `state/VehicleSession`.
 *
 * Mantido como adaptador: este hook costumava montar o seu próprio polling do snapshot inteiro
 * do carro, então cinco telas abertas geravam cinco requisições por ciclo. Agora ele apenas se
 * inscreve no poll único da sessão. O parâmetro `carIp` é ignorado — o IP é o da sessão — mas
 * continua na assinatura para que as telas existentes não precisem mudar.
 */
export const useCarSettingsSync = (_carIp?: string) => useCarSettings()
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
