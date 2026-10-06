// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { RotateCcwIcon } from 'lucide-react'
import { useCarIp } from '../hooks/useCarIp'
import { useVehicleSession } from '../state/VehicleSession'
import { Button } from '../ui'

// Reiniciar a central. Morava escondido no rodapé de "Configurações Avançadas", que não é onde
// alguém procura "a central travou". Agora fica em "Instalar e manter", junto das outras ações de
// manutenção. É ação, não destino — por isso é um cartão e não um item de menu — e pede dois
// cliques (arma → confirma), porque a tela do carro fica preta por ~30-40 s.
export function RestartHeadUnitCard() {
  const { carIp } = useCarIp()
  const { source, connection } = useVehicleSession()
  // O reinício vai pelo app do carro (HTTP). Com ele sabidamente fora do ar, o botão só geraria um
  // erro de rede — melhor dizer antes.
  const appDown = source !== 'demo' && connection === 'offline'
  const [confirming, setConfirming] = useState(false)
  const [restarting, setRestarting] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const handleRestart = async () => {
    if (!confirming) {
      setConfirming(true)
      return
    }
    setConfirming(false)
    setMessage(null)
    if (source === 'demo') {
      setMessage('Modo demonstração: nenhum comando foi enviado ao carro.')
      return
    }
    setRestarting(true)
    try {
      await invoke<string>('remote_restart', { carIp })
      setMessage('Reinício enviado — a central vai reiniciar em instantes.')
    } catch (e) {
      setMessage(`Falhou: ${String(e)}`)
    } finally {
      setRestarting(false)
    }
  }

  return (
    <div className="mt-6 rounded-2xl border border-white/[0.08] bg-black/20 p-4">
      <div className="flex items-center gap-2 mb-1">
        <RotateCcwIcon size={15} className="text-warning" />
        <h2 className="text-sm font-semibold text-text">Reiniciar central</h2>
      </div>
      <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
        Reinicia a multimídia do carro. A tela fica preta por ~30-40 s e volta como num ligar
        normal. Só com o carro parado: em movimento o carro recusa o pedido. Não afeta freio, direção
        nem motor. Precisa do app DeepGWT respondendo no carro.
      </p>
      <Button
        variant={confirming ? 'destructive' : 'warning'}
        size="sm"
        icon={RotateCcwIcon}
        fullWidth={false}
        disabled={!carIp || appDown || restarting}
        onClick={handleRestart}
        onBlur={() => setConfirming(false)}
      >
        {restarting ? 'Enviando...' : confirming ? 'Confirmar reinício?' : 'Reiniciar central'}
      </Button>
      {appDown && !message && (
        <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
          Indisponível agora: o app DeepGWT não está respondendo no carro.
        </p>
      )}
      {message && <p className="text-[11px] text-text-secondary mt-2 leading-relaxed">{message}</p>}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
