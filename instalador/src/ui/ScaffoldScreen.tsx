import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { CircleDashedIcon, ListChecksIcon, LockIcon } from 'lucide-react'
import { BackButton } from './BackButton'
import { Card } from './Card'
import { IconTile } from './IconTile'
import { ValidationBadge } from './ValidationBadge'
import { validationOf, type CapabilityId } from '../validation/status'

// Casca das telas cuja capacidade ainda não existe.
//
// O PROBLEMA QUE ELA RESOLVE: a saída fácil, num app de diagnóstico, é preencher a tela com algo
// — uma lista de ECUs plausíveis, um DTC de exemplo, um gráfico com dados de amostra. Quem abre a
// tela vê um produto pronto e não tem como saber que nada ali saiu de um carro. Esta casca torna
// essa saída fácil desnecessária: uma tela sem dado tem um layout próprio, digno, que diz o que
// falta e o que vai aparecer aqui quando faltar nada.
//
// O que ela NUNCA renderiza: valor de exemplo, contagem fictícia, gráfico de amostra, estado
// "carregando" perpétuo. Zero é zero, e aparece escrito.
export type ScaffoldScreenProps = {
  icon: LucideIcon
  title: string
  /** O que esta tela é, em uma frase. */
  intro: string
  capability?: CapabilityId
  /** O que precisa existir para a tela ter dado. Cada item é um bloqueio concreto, não um "em breve". */
  blockers: Array<{ what: string; why: string }>
  /** As seções que a tela vai ter quando houver dado. Serve de contrato, não de promessa de prazo. */
  structure: Array<{ step: string; detail: string }>
  /** Conteúdo real, quando existir algum — catálogo offline, aviso específico da tela. */
  children?: ReactNode
  backTo?: string
}

export function ScaffoldScreen({
  icon,
  title,
  intro,
  capability,
  blockers,
  structure,
  children,
  backTo,
}: ScaffoldScreenProps) {
  const entry = validationOf(capability)

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <IconTile icon={icon} tone="neutral" size={44} />
          <div className="flex-1 min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-text tracking-tight">{title}</h1>
              <ValidationBadge capability={capability} />
            </div>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">{intro}</p>
          </div>
        </div>

        {/* O estado vazio é o conteúdo principal, não um aviso de rodapé. */}
        <div className="rounded-2xl border border-white/[0.08] bg-black/20 px-5 py-8 text-center mb-6">
          <CircleDashedIcon size={26} className="text-text-tertiary mx-auto mb-3" />
          <div className="text-sm font-medium text-text">Nenhum dado — e nenhum dado inventado</div>
          <p className="text-xs text-text-tertiary mt-1.5 max-w-md mx-auto leading-relaxed">
            {entry.note}
          </p>
        </div>

        <section className="mb-6">
          <div className="flex items-center gap-2 mb-3">
            <LockIcon size={14} className="text-text-tertiary" />
            <h2 className="text-sm font-semibold text-text">O que falta para isto existir</h2>
          </div>
          <div className="space-y-2">
            {blockers.map((b) => (
              <div key={b.what} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
                <div className="text-xs font-medium text-text">{b.what}</div>
                <div className="text-[11px] text-text-tertiary mt-1 leading-relaxed">{b.why}</div>
              </div>
            ))}
          </div>
        </section>

        <section className="mb-6">
          <div className="flex items-center gap-2 mb-3">
            <ListChecksIcon size={14} className="text-text-tertiary" />
            <h2 className="text-sm font-semibold text-text">O que esta tela vai mostrar</h2>
          </div>
          <ol className="space-y-2">
            {structure.map((s, i) => (
              <li key={s.step} className="flex gap-3 items-start">
                <span className="flex-shrink-0 w-5 h-5 mt-0.5 rounded-full bg-white/[0.06] text-text-tertiary text-[10px] font-semibold flex items-center justify-center">
                  {i + 1}
                </span>
                <div className="min-w-0">
                  <div className="text-xs text-text">{s.step}</div>
                  <div className="text-[11px] text-text-tertiary leading-relaxed">{s.detail}</div>
                </div>
              </li>
            ))}
          </ol>
        </section>

        {children}

        <div className="mt-6">
          <BackButton to={backTo} />
        </div>
      </Card>
    </div>
  )
}
