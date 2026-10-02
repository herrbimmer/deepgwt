import { useEffect, useState } from 'react'
import { Trash2Icon, PlayIcon } from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { ProgressPanel } from '../components'
import { BackButton, Banner, Button, Card, IconTile, Modal } from '../ui'

export const DesinstalarOpenGw = () => {
  const [output, setOutput] = useState<string[]>([])
  const [isChecking, setIsChecking] = useState(true)
  const [networkError, setNetworkError] = useState<string | null>(null)
  const [isRunning, setIsRunning] = useState(false)
  const [isDone, setIsDone] = useState(false)
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)

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

  const handleUninstall = async () => {
    setShowConfirmDialog(false)
    setIsRunning(true)
    setIsDone(false)
    setOutput(['🗑️ Removendo OPEN GW, Shizuku e microG do carro...'])
    try {
      await invoke('uninstall_opengw')
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
          <IconTile icon={Trash2Icon} tone="danger" size={48} className="mb-4" />
          <h1 className="text-2xl font-semibold text-text tracking-tight text-center">
            Remover OPEN GW do Carro
          </h1>
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
          <Button variant="destructive" size="lg" icon={PlayIcon} onClick={() => setShowConfirmDialog(true)} className="mb-4">
            Remover do Carro
          </Button>
        )}

        {isDone && (
          <Banner tone="success" className="mb-4">
            OPEN GW, Shizuku e microG removidos. O carro voltou ao estado de fábrica.
          </Banner>
        )}

        <ProgressPanel title="Remoção" isRunning={isRunning} isDone={isDone} output={output} />

        {isRunning && (
          <Banner tone="warning" className="mb-4">
            Não feche o app nem desconecte do Wi-Fi durante a remoção.
          </Banner>
        )}

        <BackButton to="/opengw" />
      </Card>

      {showConfirmDialog && (
        <Modal onClose={() => setShowConfirmDialog(false)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-3">Remover tudo do carro?</h2>
          <p className="text-text-secondary text-sm mb-6 leading-relaxed">
            Isso vai desinstalar o OPEN GW, o Shizuku e o microG do carro permanentemente. Todas
            as personalizações (modo de condução, ambient light, ASD, equalizador, etc.) deixam de
            funcionar. Só é possível reverter instalando tudo de novo pelo instalador.
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setShowConfirmDialog(false)}>
              Cancelar
            </Button>
            <Button variant="destructive" onClick={handleUninstall}>
              Sim, remover
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
