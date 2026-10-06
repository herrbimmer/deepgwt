// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo } from 'react'
import { GaugeIcon, ScaleIcon, ShieldAlertIcon } from 'lucide-react'
import { BackButton, Banner, Card, ConfidenceChip } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import {
  FINDING_TEXT, ODOMETER_UNIT_KEY, analyzeOdometer, type OdometerReading,
} from '../../pro/odometer'

const TONE: Record<string, 'success' | 'warning' | 'info'> = {
  coincidem: 'success',
  divergem: 'warning',
  insuficiente: 'info',
}

const Linha = ({ r }: { r: OdometerReading }) => (
  <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
    <div className="flex items-baseline gap-3">
      <span className="text-xs text-text flex-1 min-w-0 truncate">{r.label}</span>
      {r.value === null ? (
        <span className="text-[11px] text-text-tertiary italic flex-shrink-0">não coletado</span>
      ) : (
        <span className="font-mono text-sm text-text tabular-nums flex-shrink-0">{r.value}</span>
      )}
    </div>
    <div className="font-mono text-[10px] text-text-tertiary mt-1 truncate">{r.key}</div>
    <div className="text-[11px] text-text-tertiary mt-1 leading-relaxed">{r.reading}</div>
  </div>
)

// Consistência de Odômetro.
//
// Esta é a tela onde é mais fácil dizer uma frase que destrói a reputação de alguém. Uma
// divergência numérica entre duas chaves não prova adulteração, não sugere adulteração e não
// autoriza ninguém a escrever "suspeita de adulteração" num laudo. O motivo está no módulo
// `pro/odometer.ts` e é repetido aqui em destaque: todas as leituras saem da MESMA interface.
//
// Por isso o resultado descreve os valores ("os valores coletados divergem"), nunca o carro, e
// nunca a intenção de ninguém. A palavra fraude não aparece na tela — exceto para dizer que esta
// tela não a investiga.
export const ProOdometerConsistency = () => {
  const session = useCarSettings()
  const a = useMemo(() => analyzeOdometer(session.values), [session.values])
  const finding = FINDING_TEXT[a.finding]

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Consistência de Odômetro</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Compara as chaves cujo nome indica quilometragem — {a.collected} de {a.readings.length}{' '}
              responderam nesta leitura.
            </p>
          </div>
          <GaugeIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        {/* O limite vem ANTES do resultado, não depois. Quem lê só a primeira tela precisa sair
            sabendo que isto não é uma verificação de adulteração. */}
        <Banner tone="error" icon={ShieldAlertIcon} className="mb-4">
          <div className="font-medium mb-0.5">Isto não é uma verificação de adulteração</div>
          <div className="text-xs opacity-85 leading-relaxed">
            Todos os números abaixo saem da mesma interface: o ControlService da multimídia, pela
            porta 8766. Não são leituras independentes de painel, ABS, BCM ou gateway. Se esse
            caminho reportar um valor errado, todas as chaves reportam o mesmo valor errado e esta
            tela diria "coincidem" com a maior das confianças. Verificação de adulteração exige ler
            módulos independentes por UDS — o que este app não faz.
          </div>
        </Banner>

        <Banner tone={TONE[a.finding]} className="mb-5">
          <div className="font-medium mb-0.5">{finding.title}</div>
          <div className="text-xs opacity-85 leading-relaxed">{finding.body}</div>
          {a.spread !== null && a.spread > 0 && (
            <div className="text-xs mt-1.5">
              Maior diferença entre os valores comparados: <b className="font-mono">{a.spread}</b>
            </div>
          )}
        </Banner>

        <section className="mb-6">
          <div className="flex flex-wrap items-center gap-2 mb-1">
            <ScaleIcon size={15} className="text-text-tertiary" />
            <h2 className="text-sm font-semibold text-text">Chaves comparadas</h2>
            <ConfidenceChip level="INFERRED" />
          </div>
          <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
            Agrupadas <strong>pelo nome da chave</strong>. Que todas meçam a mesma grandeza é
            dedução: o levantamento prova que as chaves existem, não o que elas contam.{' '}
            {a.comparable.length} entraram na comparação — as demais não responderam ou não vieram
            como número.
          </p>
          <div className="space-y-2">
            {a.totals.map((r) => (
              <Linha key={r.key} r={r} />
            ))}
          </div>
        </section>

        <section className="mb-6">
          <h2 className="text-sm font-semibold text-text mb-1">Contexto — fora da comparação</h2>
          <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
            Medem outra coisa e não devem coincidir com o total. Estão aqui porque ajudam a ler o
            conjunto, não porque entram na conta.
          </p>
          <div className="space-y-2">
            {a.contexto.map((r) => (
              <Linha key={r.key} r={r} />
            ))}
          </div>
        </section>

        <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 mb-6">
          <div className="flex items-baseline gap-3">
            <span className="text-xs text-text-secondary flex-1">Unidade declarada pelo carro</span>
            {a.unitRaw === null ? (
              <span className="text-[11px] text-text-tertiary italic">não coletado</span>
            ) : (
              <span className="font-mono text-sm text-text">{a.unitRaw}</span>
            )}
          </div>
          <div className="font-mono text-[10px] text-text-tertiary mt-1">{ODOMETER_UNIT_KEY}</div>
          <div className="text-[11px] text-text-tertiary mt-1 leading-relaxed">
            Valor cru. O app não tem a tabela que diz qual código é quilômetro e qual é milha, então
            os números acima aparecem sem unidade.
          </div>
        </div>

        <div className="mt-2">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
