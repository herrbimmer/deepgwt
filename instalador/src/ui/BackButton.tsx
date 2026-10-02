import { ArrowLeftIcon } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { Button } from './Button'
import { useRouteNode } from '../navigation/routeMeta'

export type BackButtonProps = {
  /**
   * Destino explícito. Opcional: sem ele, o pai vem da árvore de rotas
   * (`navigation/routeMeta`), e re-parentar uma tela deixa de exigir editar os botões dentro
   * dela. Mantido na API porque todas as telas existentes já passam `to`.
   */
  to?: string
  label?: string
  className?: string
  onClick?: () => void
}

export function BackButton({ to, label = 'Voltar', className = '', onClick }: BackButtonProps) {
  const entry = useRouteNode()
  const navigate = useNavigate()
  const target = to ?? entry?.parentPath ?? null

  // Sem `to` e sem pai conhecido (rota fora da árvore): volta no histórico em vez de sumir.
  if (!target) {
    return (
      <Button
        onClick={() => {
          onClick?.()
          navigate(-1)
        }}
        variant="ghost"
        icon={ArrowLeftIcon}
        className={className}
      >
        {label}
      </Button>
    )
  }

  return (
    <Button as={Link} to={target} onClick={onClick} variant="ghost" icon={ArrowLeftIcon} className={className}>
      {label}
    </Button>
  )
}
