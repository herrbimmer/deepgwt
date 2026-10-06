// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronDownIcon, CircleDotIcon } from 'lucide-react'
import { ValidationBadge } from '../ui'
import type { RouteNode } from './routeMeta'
import { splitByImplementation } from './capabilities'

/**
 * Lista de destinos da Oficina e da Engenharia, com o selo de validação de cada um.
 *
 * As telas que ainda não têm conteúdo (dependem de UDS, que o app não tem) ficavam misturadas às
 * que funcionam: na Engenharia eram 6 de 12. Continuam existindo e continuam abrindo — dizem o que
 * falta —, mas agora ficam recolhidas em "Em desenvolvimento", para que a lista que a pessoa vê
 * primeiro seja a do que dá para usar.
 */
export function CapabilityMenu({ items }: { items: RouteNode[] }) {
  const { ready, pending } = splitByImplementation(items)
  const [showPending, setShowPending] = useState(false)

  return (
    <>
      <div className="space-y-2">
        {ready.map((item) => (
          <CapabilityRow key={item.path} item={item} />
        ))}
      </div>

      {pending.length > 0 && (
        <div className="mt-4">
          <button
            onClick={() => setShowPending((v) => !v)}
            aria-expanded={showPending}
            className="flex items-center gap-1.5 text-xs text-text-tertiary hover:text-text-secondary transition-colors"
          >
            <ChevronDownIcon size={14} className={`transition-transform ${showPending ? 'rotate-180' : ''}`} />
            Em desenvolvimento ({pending.length}) — telas que ainda não leem o carro
          </button>
          {showPending && (
            <div className="space-y-2 mt-3">
              {pending.map((item) => (
                <CapabilityRow key={item.path} item={item} muted />
              ))}
            </div>
          )}
        </div>
      )}
    </>
  )
}

function CapabilityRow({ item, muted = false }: { item: RouteNode; muted?: boolean }) {
  const Icon = item.icon ?? CircleDotIcon
  return (
    <Link
      to={item.path}
      className={`flex items-start gap-3.5 rounded-2xl border border-white/[0.08] px-4 py-3.5 transition-colors ${
        muted ? 'bg-white/[0.01] hover:bg-white/[0.04]' : 'bg-white/[0.04] hover:bg-white/[0.08]'
      }`}
    >
      <Icon size={18} className={`flex-shrink-0 mt-0.5 ${muted ? 'text-text-tertiary' : 'text-text-secondary'}`} />
      <div className="flex-1 min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium text-text">{item.title}</span>
          <ValidationBadge capability={item.capability} />
        </div>
        {item.subtitle && (
          <div className="text-[11px] text-text-tertiary mt-0.5 leading-snug">{item.subtitle}</div>
        )}
      </div>
    </Link>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
