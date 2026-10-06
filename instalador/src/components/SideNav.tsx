// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { HomeIcon, CircleDotIcon, LockIcon, type LucideIcon } from 'lucide-react'
import { useFormFactor } from '../hooks/useFormFactor'
import { useNivel } from '../tiers/NivelProvider'
import { HUB_SECTIONS, sectionItems, useRouteNode, type RouteNode } from '../navigation/routeMeta'

// Barra lateral do modo desktop.
//
// Numa janela de PC o app era uma coluna de celular: para ir de "Temas" a "Ajustes do carro" era
// Voltar -> Início -> rolar -> clicar. A barra lateral mostra os mesmos destinos do Início, pelas
// mesmas seções e os mesmos cadeados de nível (sai da árvore de rotas), sempre à vista. No celular
// ela não existe — lá o Início e o breadcrumb fazem esse papel.
export function SideNav() {
  const { isDesktop } = useFormFactor()
  const { permite } = useNivel()
  const entry = useRouteNode()
  if (!isDesktop) return null

  const activePaths = new Set(entry?.trail.map((n) => n.path) ?? [])
  const isHome = entry?.node.path === '/'

  return (
    <nav
      aria-label="Navegação principal"
      className="app-sidenav sticky top-16 self-start h-[calc(100vh-4rem)] w-60 shrink-0 overflow-y-auto border-r border-border px-3 py-4 flex flex-col"
    >
      <NavLink to="/" icon={HomeIcon} title="Início" active={isHome} />

      {HUB_SECTIONS.map((section) => {
        const items = sectionItems(section.id)
        if (items.length === 0) return null
        return (
          <div key={section.id} className="mt-4">
            <div className="px-2.5 mb-1 text-[10px] font-semibold uppercase tracking-[0.08em] text-text-tertiary">
              {section.title}
            </div>
            {items.map((item) => (
              <NavItem key={item.path} item={item} active={activePaths.has(item.path)} travado={!permite(item.recurso)} />
            ))}
          </div>
        )
      })}

      <div className="flex-1" />
      <div className="mt-4 pt-3 border-t border-border">
        {sectionItems('rodape').map((item) => (
          <NavItem key={item.path} item={item} active={activePaths.has(item.path)} small />
        ))}
      </div>
    </nav>
  )
}

function NavItem({ item, active, small = false, travado = false }: { item: RouteNode; active: boolean; small?: boolean; travado?: boolean }) {
  return <NavLink to={item.path} icon={item.icon ?? CircleDotIcon} title={item.title} active={active} small={small} travado={travado} />
}

function NavLink({
  to,
  icon: Icon,
  title,
  active,
  small = false,
  travado = false,
}: {
  to: string
  icon: LucideIcon
  title: string
  active: boolean
  small?: boolean
  /** O nível do carro não libera: aparece apagado, com cadeado, e continua abrindo (a tela diz o nível). */
  travado?: boolean
}) {
  return (
    <Link
      to={to}
      aria-current={active ? 'page' : undefined}
      className={`flex items-center gap-2.5 rounded-lg px-2.5 ${small ? 'py-1.5 text-xs' : 'py-2 text-[13px]'} transition-colors ${
        active ? 'bg-white/[0.09] text-text' : 'text-text-secondary hover:bg-white/[0.05] hover:text-text'
      }`}
    >
      <Icon size={small ? 14 : 16} className={`flex-shrink-0 ${active ? 'text-primary' : 'text-text-tertiary'}`} />
      <span className={`truncate ${travado ? 'opacity-55' : ''}`}>{title}</span>
      {travado && <LockIcon size={12} className="ml-auto flex-shrink-0 text-text-tertiary" aria-label="Bloqueado pelo nível" />}
    </Link>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
