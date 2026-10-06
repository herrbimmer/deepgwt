// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { LucideIcon } from 'lucide-react'
import { Squircle } from './Squircle'

export type IconTone = 'primary' | 'danger' | 'warning' | 'success' | 'neutral'

const TONE_CLASSES: Record<IconTone, string> = {
  primary: 'bg-primary/15 text-primary',
  danger: 'bg-danger/15 text-danger',
  warning: 'bg-warning/15 text-warning',
  success: 'bg-success/15 text-success',
  neutral: 'bg-white/10 text-text-secondary',
}

export type IconTileProps = {
  icon: LucideIcon
  tone?: IconTone
  size?: number
  className?: string
}

export function IconTile({ icon: Icon, tone = 'primary', size = 48, className = '' }: IconTileProps) {
  return (
    <Squircle
      radius={size * 0.32}
      className={`inline-flex items-center justify-center flex-shrink-0 ${TONE_CLASSES[tone]} ${className}`}
      style={{ width: size, height: size }}
    >
      <Icon size={Math.round(size * 0.48)} />
    </Squircle>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
