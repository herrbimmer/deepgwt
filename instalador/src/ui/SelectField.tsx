import type { SelectHTMLAttributes } from 'react'
import { ChevronDownIcon } from 'lucide-react'
import { Squircle } from './Squircle'

export type SelectFieldProps = SelectHTMLAttributes<HTMLSelectElement> & {
  containerClassName?: string
}

export function SelectField({ className = '', containerClassName = '', children, ...rest }: SelectFieldProps) {
  return (
    <Squircle
      radius={14}
      border={{ color: 'rgba(255,255,255,0.12)' }}
      className={`relative flex items-center bg-black/25 px-3 focus-within:outline focus-within:outline-2 focus-within:outline-primary/60 ${containerClassName}`}
    >
      <select
        className={`flex-1 min-w-0 bg-transparent py-2.5 pr-6 text-sm text-text outline-none appearance-none ${className}`}
        {...rest}
      >
        {children}
      </select>
      <ChevronDownIcon size={14} className="text-text-tertiary absolute right-3 pointer-events-none" />
    </Squircle>
  )
}
