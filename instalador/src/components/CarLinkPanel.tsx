// O bloco "onde está o carro" das telas de tema. Um só, usado pelas duas.

import { Banner, Button, TextInput } from '../ui'
import type { useCarLink } from '../hooks/useCarLink'

export type CarLinkPanelProps = {
  link: ReturnType<typeof useCarLink>
  /** Trava os campos enquanto um envio está em andamento. */
  ocupado?: boolean
  className?: string
}

export function CarLinkPanel({ link, ocupado = false, className = 'mb-4' }: CarLinkPanelProps) {
  const { carIp, setCarIp, status, manualIp, setManualIp, testar, detectarGateway } = link

  if (manualIp) {
    return (
      <div className={className}>
        <label className="text-xs font-medium text-text-secondary mb-1.5 block">IP do carro</label>
        <TextInput
          mono
          value={carIp}
          onChange={(e) => setCarIp(e.target.value)}
          placeholder="192.168.33.1"
          containerClassName="mb-2"
          disabled={ocupado}
        />
        <div className="flex items-center gap-3 mb-3">
          <Button variant="secondary" size="sm" fullWidth={false} onClick={() => void detectarGateway()} disabled={ocupado}>
            Detectar automaticamente
          </Button>
          <Button variant="secondary" size="sm" fullWidth={false} onClick={() => void testar(carIp)} disabled={ocupado || !carIp}>
            Testar conexão
          </Button>
        </div>
        {status === 'testing' && <Banner tone="info">Testando conexão com {carIp}...</Banner>}
        {status === 'ok' && <Banner tone="success">Carro encontrado em {carIp}.</Banner>}
        {status === 'fail' && <Banner tone="error">Não foi possível conectar em {carIp}. Confira o IP e a rede.</Banner>}
      </div>
    )
  }

  if (status === 'testing') return <Banner tone="info" className={className}>Testando conexão com {carIp}...</Banner>

  if (status === 'ok') {
    return (
      <Banner tone="success" className={className}>
        Carro encontrado em {carIp}.{' '}
        <button type="button" onClick={() => setManualIp(true)} className="underline underline-offset-2 hover:text-text">
          Usar outro IP
        </button>
      </Banner>
    )
  }

  return (
    <Banner tone="error" className={className}>
      Não foi possível conectar em {carIp}.{' '}
      <button type="button" onClick={() => setManualIp(true)} className="underline underline-offset-2 hover:text-text">
        Trocar IP
      </button>
    </Banner>
  )
}
