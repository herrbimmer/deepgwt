import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { CircleDotIcon, WifiOffIcon } from 'lucide-react'
import { BackButton, Card, IconTile, NavTile } from '../ui'
import { useVehicleSession } from '../state/VehicleSession'
import { useNivel } from '../tiers/NivelProvider'
import { SeloDoNivel } from '../tiers/SeloDoNivel'
import { nivelDoRecurso } from '../tiers/niveis'
import { childrenOf, ROUTE_INDEX, type RouteNode } from './routeMeta'
import { unmetRequirement } from './requirements'

/** Selo "o que falta" de um item de menu. */
export function RequirementBadge({ text }: { text: string }) {
  return (
    <span className="inline-flex items-center gap-1 text-[11px] text-warning/90">
      <WifiOffIcon size={12} className="flex-shrink-0" />
      {text}
    </span>
  )
}

/**
 * Grade de destinos tirada da árvore de rotas. Cada item diz, ali mesmo, se falta alguma coisa
 * para ele funcionar agora. Antes os itens ficavam apagados sem explicação (Configurações
 * Avançadas sem IP) ou abriam uma tela vazia — nos dois casos a pessoa não sabia o que fazer.
 * O item NUNCA fica bloqueado por conexão: a tela de destino explica o que falta e às vezes
 * funciona mesmo assim (a detecção pode estar errada, e travar por um palpite é pior que deixar
 * tentar).
 *
 * Nível (Alpha 8.0): o que o nível do carro não libera aparece com cadeado e o nome do nível que
 * libera. Continua abrindo — a casca mostra, no lugar da tela, o que o nível traz.
 */
export function RouteTiles({ items }: { items: RouteNode[] }) {
  const { connection, headUnit } = useVehicleSession()
  const { permite, tabela } = useNivel()
  return (
    <div className="tile-grid">
      {items.map((item) => {
        if (!permite(item.recurso)) {
          return (
            <div key={item.path} className="opacity-60">
              <NavTile
                as={Link}
                to={item.path}
                icon={item.icon ?? CircleDotIcon}
                tone="neutral"
                title={item.title}
                subtitle={item.subtitle}
                badge={<SeloDoNivel nivel={nivelDoRecurso(item.recurso, tabela)} />}
              />
            </div>
          )
        }
        const req = unmetRequirement(item.transport, connection, headUnit)
        return (
          <NavTile
            key={item.path}
            as={Link}
            to={item.path}
            icon={item.icon ?? CircleDotIcon}
            tone={item.tone ?? 'neutral'}
            title={item.title}
            subtitle={item.subtitle}
            badge={req ? <RequirementBadge text={req.short} /> : undefined}
          />
        )
      })}
    </div>
  )
}

type MenuPageProps = {
  /** Rota do menu na árvore — título, subtítulo, ícone e itens saem dela. */
  path: string
  /** Conteúdo entre o cabeçalho e os itens (avisos, dicas). */
  intro?: ReactNode
  /** Conteúdo depois dos itens (ações que não são destinos). */
  footer?: ReactNode
}

/**
 * Página de menu genérica. "OPEN GW no carro", "Ajustes do carro" e "Ferramentas" eram três
 * telas com os itens escritos à mão — e por isso o filtro de modo não valia nelas, os títulos
 * divergiam da trilha (o botão dizia uma coisa e o breadcrumb outra) e mover
 * uma tela de lugar exigia editar a tela antiga. Agora o menu é a árvore.
 */
export function MenuPage({ path, intro, footer }: MenuPageProps) {
  const node = ROUTE_INDEX.get(path)?.node
  const items = childrenOf(path)

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-start gap-4 mb-6">
          {node?.icon && <IconTile icon={node.icon} tone={node.tone ?? 'neutral'} size={44} />}
          <div className="flex-1 min-w-0">
            <h1 className="text-xl font-semibold text-text tracking-tight">{node?.title}</h1>
            {node?.subtitle && (
              <p className="text-text-secondary mt-1 text-sm leading-relaxed">{node.subtitle}</p>
            )}
          </div>
        </div>
        {intro}
        <RouteTiles items={items} />
        {footer}
        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
