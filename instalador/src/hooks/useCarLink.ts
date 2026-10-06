// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Descobrir o carro na rede e testar a conexão.
//
// Este bloco existia duas vezes, copiado literalmente entre `ThemeEditor` e `ImportHtmlTheme`:
// mesmo `useState`, mesmo `remote_list_displays` como teste de vida, mesma queda para IP manual.
// Duas cópias do mesmo código de rede é onde uma correção pega em uma tela e esquece a outra.

import { useCallback, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { useCarIp } from './useCarIp'

export type CarTestStatus = 'idle' | 'testing' | 'ok' | 'fail'

export function useCarLink() {
  const { carIp, setCarIp } = useCarIp()
  const [status, setStatus] = useState<CarTestStatus>('idle')
  const [manualIp, setManualIp] = useState(false)

  /** `remote_list_displays` é a chamada mais barata que prova que o DeepGWT está de pé. */
  const testar = useCallback(async (ip: string) => {
    if (!ip) {
      setStatus('fail')
      return false
    }
    setStatus('testing')
    try {
      await invoke('remote_list_displays', { carIp: ip })
      setStatus('ok')
      return true
    } catch {
      setStatus('fail')
      return false
    }
  }, [])

  const detectarGateway = useCallback(async () => {
    try {
      const gateway = await invoke<string>('endereco_do_carro')
      setCarIp(gateway)
      setManualIp(false)
      return testar(gateway)
    } catch {
      setStatus('fail')
      return false
    }
  }, [setCarIp, testar])

  /** Chame ao abrir a tela ou o diálogo de envio. */
  const preparar = useCallback(() => {
    if (carIp) {
      setManualIp(false)
      void testar(carIp)
    } else {
      setManualIp(true)
      setStatus('idle')
    }
  }, [carIp, testar])

  return { carIp, setCarIp, status, manualIp, setManualIp, testar, detectarGateway, preparar }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
