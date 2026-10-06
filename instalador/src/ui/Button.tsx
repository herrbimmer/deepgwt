// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, type ElementType, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { motion } from 'motion/react'
import { springs } from '../lib/motion-presets'
import { Squircle } from './Squircle'

export type ButtonVariant = 'primary' | 'secondary' | 'destructive' | 'success' | 'warning' | 'ghost'
export type ButtonSize = 'sm' | 'md' | 'lg'

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover',
  destructive: 'bg-danger text-white hover:bg-danger-hover',
  success: 'bg-success text-[#04210f] hover:bg-success-hover',
  warning: 'bg-warning text-[#2b1700] hover:bg-warning-hover',
  secondary: 'bg-surface-strong text-text hover:bg-white/[0.14]',
  ghost: 'bg-white/[0.02] text-text-secondary hover:bg-white/[0.07] hover:text-text',
}

const VARIANT_BORDER: Partial<Record<ButtonVariant, string>> = {
  secondary: 'rgba(255,255,255,0.14)',
  ghost: 'rgba(255,255,255,0.1)',
}

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'text-xs px-3.5 py-2 gap-1.5',
  md: 'text-sm px-5 py-3 gap-2',
  lg: 'text-[15px] px-6 py-4 gap-2.5 font-semibold',
}

const SIZE_RADIUS: Record<ButtonSize, number> = { sm: 12, md: 16, lg: 18 }
const ICON_SIZE: Record<ButtonSize, number> = { sm: 14, md: 16, lg: 18 }

export type ButtonProps = {
  as?: ElementType
  variant?: ButtonVariant
  size?: ButtonSize
  icon?: LucideIcon
  iconPosition?: 'left' | 'right'
  fullWidth?: boolean
  disabled?: boolean
  className?: string
  children?: ReactNode
  [key: string]: unknown
}

export function Button({
  as,
  variant = 'secondary',
  size = 'md',
  icon: Icon,
  iconPosition = 'left',
  fullWidth = true,
  disabled,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  // motion.create(...) builds a new component type — memoized per `as` so identity stays
  // stable across renders (recreating it every render would force a remount each time).
  const MotionAs = useMemo(() => motion.create((as ?? 'button') as ElementType), [as])
  return (
    <Squircle
      as={MotionAs}
      whileTap={disabled ? undefined : { scale: 0.97 }}
      transition={springs.press}
      radius={SIZE_RADIUS[size]}
      border={VARIANT_BORDER[variant] ? { color: VARIANT_BORDER[variant] } : undefined}
      disabled={as ? undefined : disabled}
      aria-disabled={disabled}
      className={`inline-flex items-center justify-center font-medium transition-colors duration-200 select-none ${
        disabled ? 'pointer-events-none opacity-40' : ''
      } ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${fullWidth ? 'w-full' : ''} ${className}`}
      {...rest}
    >
      {Icon && iconPosition === 'left' && <Icon size={ICON_SIZE[size]} className="flex-shrink-0" />}
      <span>{children}</span>
      {Icon && iconPosition === 'right' && <Icon size={ICON_SIZE[size]} className="flex-shrink-0" />}
    </Squircle>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
