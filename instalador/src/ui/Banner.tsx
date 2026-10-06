// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { AlertTriangleIcon, CheckCircleIcon, InfoIcon, XCircleIcon } from 'lucide-react'

export type BannerTone = 'error' | 'success' | 'warning' | 'info'

const TONE_CLASSES: Record<BannerTone, string> = {
  error: 'bg-danger/10 border-danger/25 text-red-100',
  success: 'bg-success/10 border-success/25 text-green-100',
  warning: 'bg-warning/10 border-warning/25 text-amber-100',
  info: 'bg-white/5 border-white/10 text-text-secondary',
}

const TONE_ICONS: Record<BannerTone, LucideIcon> = {
  error: XCircleIcon,
  success: CheckCircleIcon,
  warning: AlertTriangleIcon,
  info: InfoIcon,
}

export type BannerProps = {
  tone?: BannerTone
  icon?: LucideIcon | null
  className?: string
  children?: ReactNode
}

export function Banner({ tone = 'info', icon, className = '', children }: BannerProps) {
  const Icon = icon === null ? null : icon ?? TONE_ICONS[tone]
  return (
    <div className={`flex items-start gap-3 rounded-2xl border backdrop-blur-xl px-4 py-3 text-sm ${TONE_CLASSES[tone]} ${className}`}>
      {Icon && <Icon size={18} className="flex-shrink-0 mt-0.5" />}
      <div className="flex-1 leading-relaxed">{children}</div>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
