// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { FileSearchIcon, TriangleAlertIcon } from 'lucide-react'
import { BackButton } from './BackButton'
import { Card } from './Card'
import { IconTile } from './IconTile'
import { ValidationBadge } from './ValidationBadge'
import type { CapabilityId } from '../validation/status'

// Casca das telas que TÊM dado real — a contraparte de `ScaffoldScreen`.
//
// A regra que ela impõe: todo número na tela carrega, no mesmo lugar, de onde veio. Data da
// captura, método e arquivo de origem ficam no cabeçalho, não num rodapé que ninguém lê. Uma
// leitura de veículo envelhece — dizer "SOC 42%" sem dizer "lido em 15/09, com o carro parado
// carregando" é meia informação, e a metade que falta é a que decide se o número ainda vale.
//
// `limits` é obrigatório por escolha: toda tela de diagnóstico tem uma borda onde o dado acaba, e
// quem lê precisa vê-la sem ter que descobrir sozinho.

export type DataScreenProps = {
  icon: LucideIcon
  title: string
  intro: string
  capability?: CapabilityId
  provenance: {
    /** Data em que o veículo respondeu. Não é a data de hoje. */
    capturedAt: string
    method: string
    /** Caminho do arquivo que sustenta os números desta tela. */
    source: string
    vin?: string
  }
  /** Onde o dado acaba. Cada item é um limite concreto desta tela, não um aviso genérico. */
  limits: Array<{ what: string; why: string }>
  children: ReactNode
  backTo?: string
}

export function DataScreen({
  icon,
  title,
  intro,
  capability,
  provenance,
  limits,
  children,
  backTo,
}: DataScreenProps) {
  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-4">
          <IconTile icon={icon} tone="neutral" size={44} />
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-text tracking-tight">{title}</h1>
              <ValidationBadge capability={capability} />
            </div>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">{intro}</p>
          </div>
        </div>

        <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 mb-6">
          <div className="flex items-center gap-2 mb-2">
            <FileSearchIcon size={13} className="text-text-tertiary" />
            <span className="text-[11px] font-semibold text-text-secondary">Procedência</span>
          </div>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-[11px]">
            <dt className="text-text-tertiary">Lido em</dt>
            <dd className="text-text-secondary">
              {provenance.capturedAt}
              {provenance.vin ? ` · VIN ${provenance.vin}` : ''}
            </dd>
            <dt className="text-text-tertiary">Método</dt>
            <dd className="text-text-secondary leading-relaxed">{provenance.method}</dd>
            <dt className="text-text-tertiary">Fonte</dt>
            <dd className="font-mono text-[10px] text-text-tertiary break-all">{provenance.source}</dd>
          </dl>
        </div>

        {children}

        <section className="mt-6">
          <div className="flex items-center gap-2 mb-3">
            <TriangleAlertIcon size={14} className="text-text-tertiary" />
            <h2 className="text-sm font-semibold text-text">Onde este dado acaba</h2>
          </div>
          <div className="space-y-2">
            {limits.map((l) => (
              <div key={l.what} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
                <div className="text-xs font-medium text-text">{l.what}</div>
                <div className="text-[11px] text-text-tertiary mt-1 leading-relaxed">{l.why}</div>
              </div>
            ))}
          </div>
        </section>

        <div className="mt-6">
          <BackButton to={backTo} />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
