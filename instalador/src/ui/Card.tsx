// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { ReactNode } from 'react'
import { Squircle } from './Squircle'

const WIDTHS = {
  sm: 'max-w-md',
  md: 'max-w-xl',
  lg: 'max-w-2xl',
  xl: 'max-w-3xl',
  '2xl': 'max-w-4xl',
} as const

export type CardProps = {
  maxWidth?: keyof typeof WIDTHS
  className?: string
  children?: ReactNode
  [key: string]: unknown
}

export function Card({ maxWidth = 'sm', className = '', children, ...rest }: CardProps) {
  // `app-card` + `data-w` são os ganchos do CSS de desktop: no modo deitado/PC cada nível de
  // largura ganha um teto maior (ver `index.css`), então o mesmo cartão respira mais na janela de
  // PC e continua estreito no celular — sem duplicar o componente nem tocar em cada tela.
  return (
    <Squircle
      radius={28}
      border={{ color: 'rgba(255,255,255,0.1)' }}
      data-w={maxWidth}
      className={`app-card w-full ${WIDTHS[maxWidth]} bg-white/[0.05] backdrop-blur-2xl p-6 sm:p-8 ${className}`}
      {...rest}
    >
      {children}
    </Squircle>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
