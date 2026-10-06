// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { ChevronRightIcon } from 'lucide-react'
import { useRouteNode } from './routeMeta'

// Trilha da raiz até a tela atual, derivada da árvore de rotas. Não existia navegação de
// contexto nenhuma antes — só um botão "Voltar" com o destino escrito à mão em cada tela.
export function Breadcrumb() {
  const entry = useRouteNode()
  if (!entry || entry.trail.length < 2) return null

  return (
    <nav aria-label="Trilha de navegação" className="flex items-center gap-1 flex-wrap text-xs text-text-tertiary">
      {entry.trail.map((node, i) => {
        const isLast = i === entry.trail.length - 1
        return (
          <span key={node.path} className="flex items-center gap-1">
            {i > 0 && <ChevronRightIcon size={12} className="opacity-50" />}
            {isLast ? (
              <span className="text-text-secondary">{node.title}</span>
            ) : (
              <Link to={node.path} className="hover:text-text-secondary transition-colors">
                {node.title}
              </Link>
            )}
          </span>
        )
      })}
    </nav>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
