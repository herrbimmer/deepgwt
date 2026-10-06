// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { PackageIcon, PlayIcon } from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { ProgressPanel } from '../components'
import { BackButton, Banner, Button, Card, IconTile } from '../ui'
import { ROTA_FALHA, ROTA_SUCESSO } from '../lib/resultadoDaInstalacao'

interface InstalarDeepGwtProps {
  /** 'update' reuses the exact same pm install -r flow (data-preserving upgrade) but frames it
   * as updating the app that's already on the car instead of a first-time install. */
  mode?: 'install' | 'update'
}

export const InstalarDeepGwt = ({ mode = 'install' }: InstalarDeepGwtProps) => {
  const isUpdate = mode === 'update'
  const [output, setOutput] = useState<string[]>([])
  const outputRef = useRef<string[]>([])
  outputRef.current = output
  const [isChecking, setIsChecking] = useState(true)
  const [networkError, setNetworkError] = useState<string | null>(null)
  const [isInstalling, setIsInstalling] = useState(false)
  const [isDone, setIsDone] = useState(false)
  const navigate = useNavigate()

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

  const handleInstall = async () => {
    setIsInstalling(true)
    setIsDone(false)
    const inicio = isUpdate ? '🔄 Atualizando DeepGWT para a versão embarcada neste instalador...' : '📦 DeepGWT + Shizuku embarcados neste instalador'
    setOutput([inicio])
    try {
      await invoke('install_deepgwt_with_shizuku')
      setIsDone(true)
      navigate(ROTA_SUCESSO, { state: { modo: mode } })
    } catch (e) {
      const erro = String(e)
      setIsInstalling(false)
      // O registro vai junto para a tela de falha (lido do ref: o estado desta closure é velho).
      navigate(ROTA_FALHA, { state: { modo: mode, erro, log: [...outputRef.current, `[ERRO] ${erro}`] } })
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex flex-col items-center mb-8">
          <IconTile icon={PackageIcon} tone="primary" size={48} className="mb-4" />
          <h1 className="text-2xl font-semibold text-text tracking-tight text-center">
            {isUpdate ? 'Atualizar DeepGWT' : 'Instalar DeepGWT'}
          </h1>
          {isUpdate && (
            <p className="text-text-secondary mt-2 text-center text-sm">
              Reinstala a versão embarcada neste instalador por cima da que já está no carro
              (<code>pm install -r</code>), mantendo suas configurações.
            </p>
          )}
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

        {!isChecking && !networkError && (
          <>

            {!isInstalling && (
              <Button variant="success" size="lg" icon={PlayIcon} onClick={handleInstall} className="mb-4">
                {isUpdate ? 'Atualizar no Carro' : 'Instalar no Carro'}
              </Button>
            )}
          </>
        )}

        <ProgressPanel title={isUpdate ? 'Atualização' : 'Instalação'} isRunning={isInstalling} isDone={isDone} output={output} />

        {isInstalling && (
          <Banner tone="warning" className="mb-4">
            Não feche o app nem desconecte do Wi-Fi durante a {isUpdate ? 'atualização' : 'instalação'}.
          </Banner>
        )}

        <BackButton to={isUpdate ? '/deepgwt' : '/install'} />
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
