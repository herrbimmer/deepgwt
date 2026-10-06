// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { InputHTMLAttributes, ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { motion } from 'motion/react'
import { springs } from '../lib/motion-presets'
import { Squircle } from './Squircle'

const MotionLabel = motion.create('label')

export type CheckboxCardProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'title'> & {
  icon?: LucideIcon
  title: ReactNode
  description?: ReactNode
}

export function CheckboxCard({ icon: Icon, title, description, className = '', ...rest }: CheckboxCardProps) {
  return (
    <Squircle
      as={MotionLabel}
      whileTap={{ scale: 0.98 }}
      transition={springs.press}
      radius={18}
      border={{ color: 'rgba(10,132,255,0.28)' }}
      className={`flex items-start gap-3 w-full bg-primary/10 backdrop-blur-xl px-4 py-3.5 cursor-pointer ${className}`}
    >
      <input type="checkbox" className="mt-1 flex-shrink-0" {...rest} />
      <div>
        <div className="flex items-center gap-2 text-primary font-medium text-sm">
          {Icon && <Icon size={16} />}
          <span>{title}</span>
        </div>
        {description && <p className="text-text-secondary text-xs mt-1 leading-relaxed">{description}</p>}
      </div>
    </Squircle>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
