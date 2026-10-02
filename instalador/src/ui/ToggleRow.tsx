import type { ReactNode } from 'react'
import { motion } from 'motion/react'
import { springs } from '../lib/motion-presets'
import { Squircle } from './Squircle'

export type ToggleRowStatus = 'saving' | 'saved' | 'error'

export type ToggleRowProps = {
  title: string
  description?: ReactNode
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
  // Indicador inline de gravação imediata no carro (ver useCarSettingsSync) — substitui o
  // antigo botão único "Sincronizar Configurações" por feedback por linha.
  status?: ToggleRowStatus
}

const STATUS_LABEL: Record<ToggleRowStatus, string> = {
  saving: 'Salvando...',
  saved: 'Salvo',
  error: 'Falha ao salvar',
}

const STATUS_CLASS: Record<ToggleRowStatus, string> = {
  saving: 'text-text-tertiary',
  saved: 'text-primary',
  error: 'text-danger',
}

export function ToggleRow({ title, description, checked, onChange, disabled, status }: ToggleRowProps) {
  return (
    <Squircle
      radius={16}
      border={{ color: 'rgba(255,255,255,0.1)' }}
      className={`flex items-center gap-4 w-full bg-white/[0.04] backdrop-blur-xl px-5 py-4 ${
        disabled ? 'opacity-40' : ''
      }`}
    >
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <div className="text-text font-medium text-sm">{title}</div>
          {status && <span className={`text-[11px] flex-shrink-0 ${STATUS_CLASS[status]}`}>{STATUS_LABEL[status]}</span>}
        </div>
        {description && <div className="text-text-tertiary text-xs mt-1 leading-relaxed">{description}</div>}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={`relative w-11 h-6 rounded-full transition-colors duration-200 flex-shrink-0 disabled:cursor-not-allowed ${
          checked ? 'bg-primary' : 'bg-white/15'
        }`}
      >
        <motion.span
          className="absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow"
          animate={{ x: checked ? 20 : 0 }}
          transition={springs.reposition}
        />
      </button>
    </Squircle>
  )
}
