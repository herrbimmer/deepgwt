// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { FlaskConicalIcon, LoaderIcon, WifiIcon, WifiOffIcon, ZapIcon } from 'lucide-react'
import { useVehicleSession } from '../state/VehicleSession'

/**
 * Estado da conexão com o carro, visível de qualquer tela, e atalho para a tela de Conexão.
 *
 * Antes a pílula só refletia o app do carro por IP e ficava em "Sem verificação" até alguma tela
 * testar — ao lado de uma faixa verde "Conectado à rede do carro". Agora ela lê o mesmo monitor que
 * o Início e a tela de Conexão, então os três dizem a mesma coisa.
 */
export function ConnectionPill() {
  const { connection, headUnit, carIp } = useVehicleSession()

  const view =
    connection === 'demo'
      ? { label: 'Demonstração', cls: 'text-warning', Icon: FlaskConicalIcon, spin: false }
      : connection === 'online'
        ? { label: 'Conectado', cls: 'text-success', Icon: WifiIcon, spin: false }
        : headUnit === 'reachable'
          ? { label: 'Central encontrada', cls: 'text-primary', Icon: ZapIcon, spin: false }
          : connection === 'offline' && headUnit === 'unreachable'
            ? { label: 'Sem carro', cls: 'text-white/55', Icon: WifiOffIcon, spin: false }
            : { label: 'Procurando…', cls: 'text-white/60', Icon: LoaderIcon, spin: true }

  return (
    <Link
      to="/conectar"
      className={`flex items-center gap-1.5 rounded-full bg-black/40 px-2.5 py-1 text-[11px] backdrop-blur-md hover:bg-black/60 transition-colors ${view.cls}`}
      title={
        connection === 'demo'
          ? 'Dados sintéticos — clique para ver a conexão'
          : `${carIp ? `IP do carro: ${carIp}` : 'Nenhum IP do carro definido'} — clique para ver a conexão`
      }
    >
      <view.Icon size={12} className={view.spin ? 'animate-spin' : ''} />
      <span className="font-medium">{view.label}</span>
    </Link>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
