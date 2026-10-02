import { useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import {
  MonitorSmartphoneIcon,
  FolderSyncIcon,
  DatabaseBackupIcon,
  WifiIcon,
  CheckCircle2Icon,
  XCircleIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { useCarIp } from '../hooks/useCarIp'
import { useVehicleSession } from '../state/VehicleSession'
import { BackButton, Button, Card, NavTile, TextInput } from '../ui'

export const DevOptions = () => {
  const { carIp, setCarIp } = useCarIp()
  const { source } = useVehicleSession()
  const [testing, setTesting] = useState(false)
  const [testResult, setTestResult] = useState<'ok' | 'fail' | null>(null)

  const handleTest = async () => {
    setTesting(true)
    setTestResult(null)
    try {
      // `remote_list_displays` é o teste específico daqui (espelhamento precisa da lista de
      // telas); no modo demonstração não há o que consultar.
      if (source === 'demo') setTestResult('ok')
      else {
        await invoke('remote_list_displays', { carIp })
        setTestResult('ok')
      }
    } catch {
      setTestResult('fail')
    } finally {
      setTesting(false)
    }
  }

  const handleUseGateway = async () => {
    try {
      const gateway = await invoke<string>('endereco_do_carro')
      setCarIp(gateway)
    } catch {
      // sem gateway disponível agora — usuário digita manualmente
    }
  }

  const disabled = !carIp

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <h1 className="text-xl font-semibold text-center mb-2 text-text tracking-tight">
          Opções de Desenvolvedor
        </h1>
        <p className="text-text-tertiary mb-6 text-center text-sm leading-relaxed">
          Mirror de tela, transferência de arquivos e backup do carro. Requer o OPEN GW aberto
          no carro e este computador no Wi-Fi do carro (hotspot da central, 192.168.33.x).
        </p>

        <div className="mb-6 space-y-3">
          <label className="text-xs text-text-tertiary block">IP do carro no Wi-Fi dele</label>
          <div className="flex gap-2">
            <TextInput
              mono
              value={carIp}
              onChange={(e) => setCarIp(e.target.value)}
              placeholder="ex.: 192.168.33.1"
              containerClassName="flex-1"
            />
            <Button
              variant="secondary"
              size="sm"
              icon={WifiIcon}
              fullWidth={false}
              onClick={handleUseGateway}
              title="Preencher com o gateway atual (funciona se o laptop estiver no hotspot do carro)"
            >
              Gateway
            </Button>
          </div>
          <div className="flex items-center justify-between">
            <button
              onClick={handleTest}
              disabled={disabled || testing}
              className="text-xs text-primary hover:text-primary-hover disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {testing ? 'Testando...' : 'Testar conexão'}
            </button>
            {testResult === 'ok' && (
              <span className="flex items-center gap-1 text-success text-xs">
                <CheckCircle2Icon size={14} /> OPEN GW respondeu
              </span>
            )}
            {testResult === 'fail' && (
              <span className="flex items-center gap-1 text-danger text-xs">
                <XCircleIcon size={14} /> Sem resposta nesse IP
              </span>
            )}
          </div>
        </div>

        <div className="tile-grid">
          <NavTile
            as={Link}
            to="/espelhar"
            icon={MonitorSmartphoneIcon}
            tone="primary"
            title="Espelhar e controlar"
            subtitle="Ver e tocar na tela da central, e salvar uma print."
            disabled={disabled}
          />
          <NavTile
            as={Link}
            to="/dev-options/files"
            icon={FolderSyncIcon}
            tone="neutral"
            title="Transferência de Arquivos"
            subtitle="Navegar, baixar e enviar arquivos do/para o carro."
            disabled={disabled}
          />
          <NavTile
            as={Link}
            to="/dev-options/backup"
            icon={DatabaseBackupIcon}
            tone="neutral"
            title="Backup"
            subtitle="Extrair todos os APKs ou copiar apps + dados completos."
            disabled={disabled}
          />
        </div>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
