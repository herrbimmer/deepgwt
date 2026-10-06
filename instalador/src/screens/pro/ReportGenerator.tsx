// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import { CodeIcon, FileTextIcon, UserIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, TextInput } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import { useVehicleIdentity } from '../../vehicle/useVehicleIdentity'
import { loadChangeLog } from '../../state/changeLog'
import { loadSnapshots } from '../../state/snapshots'
import { runScan } from '../../pro/scan'
import { analyzeOdometer } from '../../pro/odometer'
import { buildReport, reportToHtml, reportToJson } from '../../lib/report'
import { SAVE_MESSAGE, saveText } from '../../lib/fileExchange'

const nomeArquivo = (vin: string | null, ext: string) => {
  const d = new Date()
  const data = `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}${String(d.getDate()).padStart(2, '0')}`
  return `relatorio-${vin ?? 'sem-vin'}-${data}.${ext}`
}

// Relatório.
//
// O documento é o que sai da oficina e o que fica com o cliente, então é onde a regra de não
// inventar vale mais: cada campo sem dado sai escrito "não coletado", e existe uma seção inteira
// dedicada ao que o relatório NÃO contém — com a razão de cada ausência. Um relatório que só lista
// o que deu certo é publicidade.
//
// Em modo demonstração o documento leva uma tarja no topo dizendo que nenhum veículo foi lido.
// Isso não é opcional e não tem como ser desligado por aqui.
export const ProReportGenerator = () => {
  const session = useCarSettings()
  const identity = useVehicleIdentity()

  const [tecnico, setTecnico] = useState('')
  const [notas, setNotas] = useState('')
  const [incluirVarredura, setIncluirVarredura] = useState(true)
  const [incluirHistorico, setIncluirHistorico] = useState(true)
  const [incluirPontos, setIncluirPontos] = useState(true)
  const [aviso, setAviso] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  const changes = useMemo(() => loadChangeLog(), [])
  const snapshots = useMemo(() => loadSnapshots(), [])

  const model = useMemo(
    () =>
      buildReport({
        values: session.values,
        source: session.source,
        carIp: session.carIp,
        vin: identity.vin,
        vinFields:
          identity.decode?.fields.map((f) => ({
            label: f.label,
            value: f.value,
            confidence: f.confidence,
          })) ?? [],
        scan: incluirVarredura
          ? runScan({
              values: session.values,
              source: session.source,
              carIp: session.carIp,
              vin: identity.vin,
            })
          : null,
        odometer: analyzeOdometer(session.values),
        changes: incluirHistorico ? changes : [],
        snapshots: incluirPontos ? snapshots : [],
        notes: notas,
        technician: tecnico,
      }),
    [
      session.values, session.source, session.carIp, identity.vin, identity.decode,
      incluirVarredura, incluirHistorico, incluirPontos, changes, snapshots, notas, tecnico,
    ],
  )

  const salvar = async (formato: 'html' | 'json') => {
    const texto = formato === 'html' ? reportToHtml(model) : reportToJson(model)
    const r = await saveText(nomeArquivo(model.vin, formato), texto, formato)
    if (r.ok) setAviso({ tone: 'success', text: SAVE_MESSAGE[r.how](r.path) })
    else if (r.how === 'cancelled') setAviso(null)
    else setAviso({ tone: 'error', text: r.error ?? 'Não foi possível salvar.' })
  }

  const contagem = [
    `${model.identity.reduce((n, g) => n + g.rows.filter((r) => r.value !== null).length, 0)} campos de identidade coletados`,
    `${model.configuration.length} chaves de configuração`,
    model.scan ? `varredura com ${model.scan.answered} chaves` : 'sem varredura',
    `${model.changes.length} alteração(ões)`,
    `${model.snapshots.length} ponto(s) de restauração`,
  ]

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Relatório</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Um arquivo HTML autossuficiente — sem CSS externo, sem fonte remota, sem script — com
              o que foi lido nesta sessão e o que não foi.
            </p>
          </div>
          <FileTextIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        {session.source === 'demo' && (
          <Banner tone="warning" className="mb-4">
            <div className="font-medium mb-0.5">O documento vai sair marcado como demonstração</div>
            <div className="text-xs opacity-85 leading-relaxed">
              Nenhum veículo está sendo lido. O relatório leva uma tarja no topo dizendo isso, e ela
              não pode ser desligada por aqui — um documento de demonstração que pareça um laudo é
              exatamente o que essa tarja existe para impedir.
            </div>
          </Banner>
        )}

        <section className="mb-5 space-y-3">
          <TextInput
            icon={UserIcon}
            placeholder="Responsável pela leitura (opcional)"
            value={tecnico}
            onChange={(e) => setTecnico(e.target.value)}
          />
          <textarea
            placeholder="Observações — o que foi verificado fora do app, o que ficou pendente…"
            value={notas}
            onChange={(e) => setNotas(e.target.value)}
            rows={4}
            className="w-full rounded-2xl border border-white/[0.12] bg-black/25 px-3.5 py-3 text-sm text-text placeholder:text-text-tertiary outline-none focus:outline-2 focus:outline-primary/60 resize-y"
          />
        </section>

        <section className="mb-5">
          <h2 className="text-sm font-semibold text-text mb-2">Seções</h2>
          <div className="space-y-1.5 text-xs text-text-secondary">
            {[
              { on: incluirVarredura, set: setIncluirVarredura, label: 'Varredura do dicionário', hint: 'roda uma comparação com o levantamento no instante da geração' },
              { on: incluirHistorico, set: setIncluirHistorico, label: 'Alterações feitas por este app', hint: `${changes.length} evento(s) guardados` },
              { on: incluirPontos, set: setIncluirPontos, label: 'Pontos de restauração', hint: `${snapshots.length} ponto(s)` },
            ].map((c) => (
              <label key={c.label} className="flex items-start gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={c.on}
                  onChange={(e) => c.set(e.target.checked)}
                  className="accent-primary mt-0.5"
                />
                <span>
                  {c.label}
                  <span className="text-text-tertiary"> — {c.hint}</span>
                </span>
              </label>
            ))}
          </div>
          <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
            Identidade, configuração de fábrica, odômetro e a lista do que não foi coletado entram
            sempre. Uma seção desligada aparece no documento como ausente, não sumida.
          </p>
        </section>

        <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 mb-5">
          <div className="text-xs font-medium text-text mb-1">O que vai no documento</div>
          <ul className="text-[11px] text-text-tertiary space-y-0.5">
            {contagem.map((c) => (
              <li key={c}>· {c}</li>
            ))}
          </ul>
        </div>

        {aviso && (
          <Banner tone={aviso.tone} className="mb-4">
            {aviso.text}
          </Banner>
        )}

        <div className="flex flex-col sm:flex-row gap-2">
          <Button variant="primary" icon={FileTextIcon} onClick={() => salvar('html')}>
            Salvar relatório (HTML)
          </Button>
          <Button variant="secondary" icon={CodeIcon} onClick={() => salvar('json')}>
            Salvar dados (JSON)
          </Button>
        </div>

        <p className="text-[11px] text-text-tertiary mt-3 leading-relaxed">
          O documento é gerado e gravado neste computador. Nada é enviado a nenhum servidor durante
          a geração — o que o app fala com a rede está listado em Privacidade, com arquivo e linha.
        </p>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
