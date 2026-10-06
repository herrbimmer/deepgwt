// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { ReactNode } from 'react'
import { Squircle } from './Squircle'

export type ListPanelProps = {
  className?: string
  maxHeight?: string
  children?: ReactNode
}

export function ListPanel({ className = '', maxHeight = 'max-h-80', children }: ListPanelProps) {
  return (
    <Squircle
      radius={18}
      border={{ color: 'rgba(255,255,255,0.08)' }}
      className={`bg-black/25 backdrop-blur-xl overflow-y-auto ${maxHeight} ${className}`}
    >
      {children}
    </Squircle>
  )
}

export function ListRow({ className = '', children }: { className?: string; children?: ReactNode }) {
  return (
    <div className={`flex items-center gap-3 px-4 py-2.5 border-b border-white/5 last:border-b-0 ${className}`}>
      {children}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
