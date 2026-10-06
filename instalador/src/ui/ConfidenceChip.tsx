// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { CheckCircle2Icon, EyeIcon, HelpCircleIcon, LightbulbIcon, FlaskConicalIcon } from 'lucide-react'
import type { Confidence } from '../data/knowledgeTypes'

// Selo de confiança da Knowledge Base, visível ao lado de cada afirmação.
//
// Existe para que a diferença entre "o carro respondeu isto" e "deduzimos isto pelo nome" nunca
// dependa de o usuário lembrar do rodapé. `title` carrega a definição completa.

const STYLE: Record<Confidence, { label: string; cls: string; Icon: typeof EyeIcon; title: string }> = {
  FACT: {
    label: 'Fato',
    cls: 'bg-success/15 text-success',
    Icon: CheckCircle2Icon,
    title: 'Comprovado por leitura do próprio veículo ou por algoritmo publicado. Reprodutível.',
  },
  OBSERVED: {
    label: 'Observado',
    cls: 'bg-primary/15 text-primary',
    Icon: EyeIcon,
    title: 'Valor lido ao vivo. Vale para este carro, neste momento.',
  },
  INFERRED: {
    label: 'Deduzido',
    cls: 'bg-warning/15 text-warning',
    Icon: LightbulbIcon,
    title: 'Deduzido do nome ou do contexto. Nunca confirmado no carro.',
  },
  UNKNOWN: {
    label: 'Desconhecido',
    cls: 'bg-white/[0.06] text-text-tertiary',
    Icon: HelpCircleIcon,
    title: 'Existe, mas não sabemos o que é. Preferimos dizer isso a inventar.',
  },
  REQUIRES_VALIDATION: {
    label: 'A validar',
    cls: 'bg-danger/15 text-danger',
    Icon: FlaskConicalIcon,
    title: 'Hipótese. Precisa de teste em um veículo real antes de valer como informação.',
  },
}

export function ConfidenceChip({ level, className = '' }: { level: Confidence; className?: string }) {
  const { label, cls, Icon, title } = STYLE[level]
  return (
    <span
      title={title}
      className={`inline-flex flex-shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${cls} ${className}`}
    >
      <Icon size={10} />
      {label}
    </span>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
