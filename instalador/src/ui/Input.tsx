// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { InputHTMLAttributes } from 'react'
import type { LucideIcon } from 'lucide-react'
import { Squircle } from './Squircle'

export type TextInputProps = InputHTMLAttributes<HTMLInputElement> & {
  icon?: LucideIcon
  mono?: boolean
  containerClassName?: string
}

export function TextInput({ icon: Icon, mono, className = '', containerClassName = '', ...rest }: TextInputProps) {
  return (
    <Squircle
      radius={14}
      border={{ color: 'rgba(255,255,255,0.12)' }}
      className={`flex items-center gap-2 bg-black/25 backdrop-blur-xl px-3 focus-within:outline focus-within:outline-2 focus-within:outline-primary/60 ${containerClassName}`}
    >
      {Icon && <Icon size={15} className="text-text-tertiary flex-shrink-0" />}
      <input
        className={`flex-1 min-w-0 bg-transparent py-2.5 text-sm text-text placeholder:text-text-tertiary outline-none ${
          mono ? 'font-mono' : ''
        } ${className}`}
        {...rest}
      />
    </Squircle>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
