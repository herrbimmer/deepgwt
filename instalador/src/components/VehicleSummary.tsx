// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { ChevronRightIcon } from 'lucide-react'
import { useCarSettings } from '../state/VehicleSession'
import { IDENTITY_SUMMARY_KEYS } from '../vehicle/identityKeys'

// Resumo do veículo mostrado na tela de conexão.
//
// Componente separado de propósito: ele é quem assina o polling (`useCarSettings`), e só é
// montado quando já existe conexão. Assim a tela de conexão não gera tráfego enquanto a pessoa
// ainda está tentando se conectar.
export function VehicleSummary() {
  const { values, status, keyCount } = useCarSettings()
  const read = (k: string) => {
    const v = values[k]
    return v === undefined || v === '' ? null : v
  }
  const anything = IDENTITY_SUMMARY_KEYS.some((k) => read(k.key) !== null)

  return (
    <div className="rounded-2xl border border-white/[0.08] bg-white/[0.03] overflow-hidden">
      <div className="flex items-baseline justify-between px-4 py-2.5 border-b border-white/5">
        <span className="text-xs font-semibold text-text">Veículo</span>
        <span className="text-[10px] text-text-tertiary">
          {status === 'loading' && !keyCount ? 'lendo…' : `${keyCount} chaves lidas`}
        </span>
      </div>

      {!anything ? (
        <p className="px-4 py-3 text-[11px] text-text-tertiary leading-relaxed">
          Conectado, mas o carro ainda não respondeu as chaves de identificação. Elas chegam pelo
          app DeepGWT, que precisa estar aberto no carro e alcançável pelo IP configurado.
        </p>
      ) : (
        <div>
          {IDENTITY_SUMMARY_KEYS.map((k) => {
            const v = read(k.key)
            return (
              <div key={k.key} className="flex items-baseline gap-3 px-4 py-2 border-b border-white/5">
                <span className="text-[11px] text-text-tertiary w-28 flex-shrink-0">{k.label}</span>
                <span
                  className={`flex-1 text-xs font-mono break-all ${
                    v === null ? 'text-text-tertiary italic font-sans' : 'text-text'
                  }`}
                >
                  {v ?? 'não coletado'}
                </span>
              </div>
            )
          })}
        </div>
      )}

      <Link
        to="/veiculo"
        className="flex items-center justify-between px-4 py-2.5 text-xs text-text-secondary hover:bg-white/[0.04] transition-colors"
      >
        Informações completas do veículo
        <ChevronRightIcon size={14} />
      </Link>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
