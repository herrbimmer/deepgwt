// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileUpIcon, PlayIcon } from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { open } from '@tauri-apps/plugin-dialog'
import { ProgressPanel } from '../components'
import { BackButton, Banner, Button, Card, IconTile } from '../ui'
import { ROTA_FALHA, ROTA_SUCESSO } from '../lib/resultadoDaInstalacao'

export const InstallLocalApk = () => {
  const [apkPath, setApkPath] = useState<string>('')
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

  const handlePickFile = async () => {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'APK', extensions: ['apk'] }],
    })
    if (typeof selected === 'string') {
      setApkPath(selected)
    }
  }

  const handleInstall = async () => {
    if (!apkPath) return
    setIsInstalling(true)
    setIsDone(false)
    setOutput([`📄 Arquivo selecionado: ${apkPath}`])
    try {
      await invoke('install_local_apk', { apkPath })
      setIsDone(true)
      navigate(ROTA_SUCESSO, { state: { modo: 'local' } })
    } catch (e) {
      const erro = String(e)
      setIsInstalling(false)
      navigate(ROTA_FALHA, { state: { modo: 'local', erro, log: [...outputRef.current, `[ERRO] ${erro}`] } })
    }
  }

  const fileName = apkPath ? apkPath.split(/[\\/]/).pop() : ''

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex flex-col items-center mb-6">
          <IconTile icon={FileUpIcon} tone="primary" size={48} className="mb-4" />
          <h1 className="text-2xl font-semibold text-text tracking-tight text-center">Instalar APK Local</h1>
          <p className="text-text-secondary mt-2 text-center text-sm">
            Selecione um APK no seu computador (ex.: um build próprio) e envie direto para a
            central via Wi-Fi, sem depender de nenhum release no GitHub.
          </p>
        </div>

        {isChecking && <div className="text-center text-text-secondary mb-4 text-sm">Verificando rede...</div>}

        {networkError && (
          <Banner tone="error" className="mb-4">
            <p className="font-medium mb-2">Erro de Rede:</p>
            <p>{networkError}</p>
            <div className="mt-3 space-y-0.5">
              <p className="font-medium">Para resolver:</p>
              <p>1. Ligue o carro e o hotspot (Wi-Fi) da central multimídia</p>
              <p>2. Conecte este computador à rede do carro — a rede de casa não serve</p>
              <p>3. Saia desta tela e entre de novo para nova busca</p>
            </div>
          </Banner>
        )}

        {!isChecking && !networkError && (
          <>
            <Button
              variant="secondary"
              icon={FileUpIcon}
              disabled={isInstalling}
              onClick={handlePickFile}
              className="mb-3"
            >
              {fileName || 'Selecionar APK...'}
            </Button>


            {!isInstalling && (
              <Button
                variant="success"
                size="lg"
                icon={PlayIcon}
                disabled={!apkPath}
                onClick={handleInstall}
                className="mb-4"
              >
                Instalar no Carro
              </Button>
            )}
          </>
        )}

        <ProgressPanel title="Instalação" isRunning={isInstalling} isDone={isDone} output={output} />

        {isInstalling && (
          <Banner tone="warning" className="mb-4">
            Não feche o app nem desconecte do Wi-Fi durante a instalação.
          </Banner>
        )}

        <BackButton to="/install" />
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
