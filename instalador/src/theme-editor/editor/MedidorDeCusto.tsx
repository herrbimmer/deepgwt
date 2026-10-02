// O rodapé do inspetor: quanto o tema pesa para o painel do carro, e o que o carro não manda.

import { useState } from 'react'
import { ChevronDownIcon, ChevronUpIcon, GaugeIcon } from 'lucide-react'
import type { Custo } from '../model/custo'
import { ROTULO_DO_NIVEL, fracaoDoCusto } from '../model/custo'

const COR: Record<Custo['nivel'], string> = {
  leve: 'bg-success',
  medio: 'bg-warning',
  pesado: 'bg-danger',
}

export function MedidorDeCusto({ custo, semDado }: { custo: Custo; semDado: string[] }) {
  const [aberto, setAberto] = useState(false)
  const detalhes = custo.motivos.length > 0 || semDado.length > 0
  return (
    <div>
      <button
        type="button"
        onClick={() => detalhes && setAberto((v) => !v)}
        className="w-full flex items-center gap-2 text-left"
        title="Conta o que se sabe que pesa no WebView do painel: desfoque, brilho, suavização, imagens e quantidade de elementos."
      >
        <GaugeIcon size={13} className="text-text-tertiary flex-shrink-0" />
        <span className="text-[11px] text-text-secondary flex-shrink-0">Custo no painel</span>
        <span className="flex-1 h-1.5 rounded-full bg-white/10 overflow-hidden">
          <span className={`block h-full ${COR[custo.nivel]}`} style={{ width: `${Math.max(6, fracaoDoCusto(custo) * 100)}%` }} />
        </span>
        <span className={`text-[11px] font-medium ${custo.nivel === 'pesado' ? 'text-danger' : custo.nivel === 'medio' ? 'text-warning' : 'text-success'}`}>
          {ROTULO_DO_NIVEL[custo.nivel]}
        </span>
        {detalhes && (aberto ? <ChevronDownIcon size={12} className="text-text-tertiary" /> : <ChevronUpIcon size={12} className="text-text-tertiary" />)}
      </button>
      {aberto && (
        <div className="mt-2 space-y-1.5">
          {custo.motivos.map((m) => (
            <p key={m} className="text-[11px] text-text-tertiary leading-snug">· {m}</p>
          ))}
          {custo.sugestao && <p className="text-[11px] text-text-secondary leading-snug">{custo.sugestao}</p>}
          {semDado.length > 0 && (
            <p className="text-[11px] text-amber-300/90 leading-snug">
              Sem dado deste carro (leitura ao vivo): {semDado.join(', ')}. O elemento fica no texto de reserva.
            </p>
          )}
        </div>
      )}
    </div>
  )
}
