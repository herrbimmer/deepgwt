// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useState } from 'react'
import { RefreshCwIcon, PlayIcon } from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { ProgressPanel } from '../components'
import { BackButton, Banner, Button, Card, IconTile } from '../ui'

export const RestartShizuku = () => {
  const [output, setOutput] = useState<string[]>([])
  const [isChecking, setIsChecking] = useState(true)
  const [networkError, setNetworkError] = useState<string | null>(null)
  const [isRunning, setIsRunning] = useState(false)
  const [isDone, setIsDone] = useState(false)

  useEffect(() => {
    const checkNetwork = async () => {
      setIsChecking(true)
      setNetworkError(null)
      try {
        await invoke('conferir_wifi_do_carro')
      } catch (e: any) {
        setNetworkError(e.toString())
      } finally {
        setIsChecking(false)
      }
    }
    checkNetwork()
  }, [])

  useEffect(() => {
    const unlistenOutput = listen<string>('telnet-output', (event) => {
      setOutput((prev) => [...prev, event.payload])
    })
    return () => {
      unlistenOutput.then((unlisten) => unlisten())
    }
  }, [])

  const handleRestart = async () => {
    setIsRunning(true)
    setIsDone(false)
    setOutput(['🔁 Reiniciando o Shizuku...'])
    try {
      await invoke('restart_shizuku')
      setIsDone(true)
    } catch (e) {
      setOutput((prev) => [...prev, `[ERRO] ${e}`])
    } finally {
      setIsRunning(false)
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex flex-col items-center mb-8">
          <IconTile icon={RefreshCwIcon} tone="primary" size={48} className="mb-4" />
          <h1 className="text-2xl font-semibold text-text tracking-tight text-center">Reiniciar Shizuku</h1>
        </div>

        {isChecking && <div className="text-center text-text-secondary mb-4 text-sm">Verificando rede...</div>}

        {networkError && (
          <Banner tone="error" className="mb-4">
            <p className="font-medium mb-2">Erro de Rede:</p>
            <p>{networkError}</p>
            <div className="mt-3 space-y-0.5">
              <p className="font-medium">Para resolver:</p>
              <p>1. Ative o hotspot no dispositivo Haval</p>
              <p>2. Conecte este dispositivo ao WiFi do Haval</p>
              <p>3. Tente novamente</p>
            </div>
          </Banner>
        )}

        {!isChecking && !networkError && !isRunning && !isDone && (
          <Button variant="primary" size="lg" icon={PlayIcon} onClick={handleRestart} className="mb-4">
            Reiniciar Shizuku
          </Button>
        )}

        {isDone && (
          <Banner tone="success" className="mb-4">
            Shizuku reiniciado. O DeepGWT deve voltar a funcionar normalmente.
          </Banner>
        )}

        <ProgressPanel title="Reinício" isRunning={isRunning} isDone={isDone} output={output} />

        {isRunning && (
          <Banner tone="warning" className="mb-4">
            Não feche o app nem desconecte do Wi-Fi durante o reinício.
          </Banner>
        )}

        <BackButton to="/deepgwt" />
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
