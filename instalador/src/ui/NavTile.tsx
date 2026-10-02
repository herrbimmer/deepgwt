import { useMemo, type ElementType, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { ChevronRightIcon } from 'lucide-react'
import { motion } from 'motion/react'
import { springs } from '../lib/motion-presets'
import { Squircle } from './Squircle'
import { IconTile, type IconTone } from './IconTile'

export type NavTileProps = {
  as?: ElementType
  icon: LucideIcon
  tone?: IconTone
  title: ReactNode
  subtitle?: ReactNode
  /** Linha extra sob o subtítulo — ex.: o que falta para o destino funcionar agora. */
  badge?: ReactNode
  disabled?: boolean
  className?: string
  [key: string]: unknown
}

// Left-aligned icon + title + subtitle row used for menu-style navigation (Hub,
// Opções de Desenvolvedor, ...). Every destination used to get its own arbitrary
// button color; here the color only marks meaning (primary vs. destructive), and
// the row itself stays a neutral glass tile so a page of tiles reads as one menu.
export function NavTile({
  as,
  icon,
  tone = 'neutral',
  title,
  subtitle,
  badge,
  disabled,
  className = '',
  ...rest
}: NavTileProps) {
  const MotionAs = useMemo(() => motion.create((as ?? 'div') as ElementType), [as])
  return (
    <Squircle
      as={MotionAs}
      whileTap={disabled ? undefined : { scale: 0.98 }}
      transition={springs.press}
      radius={20}
      border={{ color: 'rgba(255,255,255,0.1)' }}
      aria-disabled={disabled}
      className={`flex items-start gap-4 w-full bg-white/[0.04] backdrop-blur-xl px-5 py-4 transition-colors duration-200 ${
        disabled ? 'pointer-events-none opacity-40' : 'hover:bg-white/[0.08]'
      } ${className}`}
      {...rest}
    >
      <IconTile icon={icon} tone={tone} size={44} />
      <div className="flex-1 text-left min-w-0">
        {/* Quebra em vez de cortar: no celular "Informações do veículo" virava "Informações do veíc…". */}
        <div className="font-medium text-text leading-snug">{title}</div>
        {subtitle && <div className="text-xs text-text-tertiary mt-0.5 leading-snug">{subtitle}</div>}
        {badge && <div className="mt-1.5">{badge}</div>}
      </div>
      <ChevronRightIcon size={18} className="text-text-tertiary flex-shrink-0 self-center" />
    </Squircle>
  )
}
