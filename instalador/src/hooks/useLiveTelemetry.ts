// Leitura contínua do carro, num ritmo escolhido pela tela.
//
// ── POR QUE NÃO USAR `useCarSettings()` ──────────────────────────────────────────────────────
//
// A sessão do veículo (state/VehicleSession) já tem um polling, e ele é deliberadamente lento:
// 4 s, um único poll para o app inteiro, ligado só enquanto alguma tela assina. Isso está certo
// para telas de configuração — ninguém precisa ver um interruptor de ADAS mudar em tempo real.
//
// Para monitorar o carro andando, 4 s é outra coisa: a velocidade aparece quatro segundos depois
// de existir, a marcha some entre P e D, e o ângulo do volante vira uma sequência de saltos. Este
// hook faz o mesmo pedido HTTP da sessão (`remote_gwlink_telemetry`, o snapshot completo da porta
// 8766), só que num intervalo que a tela decide.
//
// A disciplina de "um poll por vez" continua valendo, e é por isso que a tela do monitor usa
// `useVehicleSession()` (que NÃO assina o polling de 4 s) em vez de `useCarSettings()`. Se ela
// assinasse os dois, o app faria duas varreduras concorrentes das 817 chaves.
//
// ── O QUE O RITMO PEDIDO NÃO GARANTE ─────────────────────────────────────────────────────────
//
// `remote_gwlink_telemetry` não tem timeout do lado Rust, e o carro responde no tempo dele. Pedir
// 1 s não faz o carro responder em 1 s. O hook nunca deixa dois pedidos se sobreporem (`emVoo`) e
// devolve `rttMs`, o tempo real da última ida e volta — é o que a tela mostra, em vez de fingir
// que o intervalo pedido é a taxa obtida.

import { useCallback, useEffect, useRef, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { useVehicleSession } from '../state/VehicleSession'
import { buildDemoSnapshot } from '../mock/telemetryFixture'

export type LiveTelemetry = {
  values: Record<string, string>
  /** Quando o último pedido voltou com sucesso. `null` = nenhum ainda. */
  lastOkAt: number | null
  /** Ida e volta do último pedido bem-sucedido, em ms. É a taxa real, não a pedida. */
  rttMs: number | null
  error: string | null
  /** Quantos pedidos falharam em sequência. Zera a cada sucesso. */
  failures: number
  demo: boolean
  refreshNow: () => void
}

export function useLiveTelemetry(intervalMs: number, paused = false): LiveTelemetry {
  const { carIp, source } = useVehicleSession()
  const demo = source === 'demo'

  const [values, setValues] = useState<Record<string, string>>({})
  const [lastOkAt, setLastOkAt] = useState<number | null>(null)
  const [rttMs, setRttMs] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [failures, setFailures] = useState(0)

  const emVoo = useRef(false)
  // O snapshot sintético é construído UMA vez e devolvido sempre pela mesma referência. Sem isso
  // o modo demo redesenharia a tela inteira a cada tique com dado idêntico — `buildDemoSnapshot()`
  // devolve um objeto novo toda vez, e um objeto novo basta para o React reconciliar tudo.
  const demoRef = useRef<Record<string, string> | null>(null)
  // `tick` é o gatilho do "atualizar agora": mudar o número refaz o efeito sem mexer no intervalo.
  const [tick, setTick] = useState(0)
  const refreshNow = useCallback(() => setTick((n) => n + 1), [])

  useEffect(() => {
    if (paused) return

    let cancelado = false

    const buscar = async () => {
      if (emVoo.current || cancelado) return

      if (demo) {
        if (!demoRef.current) demoRef.current = buildDemoSnapshot()
        setValues(demoRef.current)
        setLastOkAt(Date.now())
        setRttMs(null)
        setError(null)
        setFailures(0)
        return
      }
      if (!carIp) {
        setError('Nenhum IP do carro configurado.')
        return
      }

      emVoo.current = true
      const t0 = performance.now()
      try {
        const bruto = await invoke<string>('remote_gwlink_telemetry', { carIp })
        if (cancelado) return
        setValues(JSON.parse(bruto) as Record<string, string>)
        setRttMs(Math.round(performance.now() - t0))
        setLastOkAt(Date.now())
        setError(null)
        setFailures(0)
      } catch (e) {
        if (cancelado) return
        setError(String(e))
        setFailures((n) => n + 1)
      } finally {
        emVoo.current = false
      }
    }

    buscar()
    const id = window.setInterval(buscar, intervalMs)
    return () => {
      cancelado = true
      window.clearInterval(id)
    }
  }, [carIp, demo, intervalMs, paused, tick])

  return { values, lastOkAt, rttMs, error, failures, demo, refreshNow }
}
