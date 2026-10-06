// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { ActivityIcon, RefreshCwIcon, StethoscopeIcon } from 'lucide-react'
import { Banner, Button, ConfidenceChip, DataScreen, ListPanel, ListRow } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import { LIVE_TRACE } from '../../data/knowledge'
import { ACCESS_LAYERS, type PathReadiness } from '../../data/diagnosticAccess'
import { bridgeDiagnosis, parseDiagReport, type DiagReport } from '../../pro/diagTrace'
import { hasTauri } from '../../lib/fileExchange'

// Diagnóstico.
//
// Esta tela carregava uma frase errada, e vale registrar qual: "o comando gwlink_fetch_diagnostics
// descreve o aplicativo instalado no carro, não o veículo". A primeira metade é verdade — o
// relatório /diag existe para dizer se a ponte do Shizuku está viva. A segunda é falsa: junto vem
// um registro de eventos com as mudanças de chave que o próprio ControlService emitiu, com
// horário. Isso é o carro falando.
//
// E é a fonte que aguenta o que a outra não aguenta. Na sessão de 2026-09-15 o snapshot voltou
// vazio duas vezes, com o binder vencido, enquanto este registro continuou entregando dado.
//
// A tela faz três coisas, nesta ordem de importância: diz o estado da ponte (que explica um
// snapshot vazio sem culpar a rede), mostra a série de eventos do veículo, e deixa claro qual
// caminho de diagnóstico existe no carro mas ainda não existe no app.

// Segundos desde o boot, com uma casa. Milissegundo aqui não diz nada a quem está olhando o carro
// ligar: o que importa é "no segundo 21" contra "no segundo 68".
const seg = (ms: number | null): string =>
  ms === null ? '—' : `${(ms / 1000).toFixed(1).replace('.', ',')} s`

const READINESS_STYLE: Record<PathReadiness, { label: string; cls: string }> = {
  available: { label: 'disponível', cls: 'bg-success/15 text-success' },
  'needs-app': { label: 'falta no app', cls: 'bg-warning/15 text-warning' },
  'needs-consent': { label: 'exige autorização', cls: 'bg-danger/15 text-danger' },
  blocked: { label: 'bloqueado pelo veículo', cls: 'bg-white/[0.06] text-text-tertiary' },
}

const horaDe = (epochMs: number) =>
  new Date(epochMs).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })

export const ProDiagnostics = () => {
  const session = useCarSettings()
  const [report, setReport] = useState<DiagReport | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [carregando, setCarregando] = useState(false)

  const ler = useCallback(async () => {
    // Sem processo nativo não há como chamar o carro, e o erro que sairia daí seria um TypeError
    // interno — inútil para quem lê.
    if (!hasTauri()) {
      setErro(
        'Esta tela precisa do aplicativo instalado (a janela do DeepGWT), não do navegador: a leitura passa pelo processo nativo, que o Vite sozinho não tem.',
      )
      return
    }
    setCarregando(true)
    setErro(null)
    try {
      const raw = await invoke<string>('gwlink_fetch_diagnostics')
      setReport(parseDiagReport(raw))
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
      setReport(null)
    } finally {
      setCarregando(false)
    }
  }, [])

  useEffect(() => {
    void ler()
  }, [ler])

  const diagnose = report ? bridgeDiagnosis(report.bridge) : null
  const semSnapshot = Object.keys(session.values).length === 0

  return (
    <DataScreen
      icon={StethoscopeIcon}
      title="Diagnóstico"
      intro="Estado da ponte com o carro e os eventos que o veículo emitiu por conta própria."
      capability="pro.diagnostics"
      provenance={{
        capturedAt: report ? 'leitura desta sessão' : LIVE_TRACE.meta.capturedAt,
        vin: LIVE_TRACE.meta.vin,
        method: LIVE_TRACE.meta.method,
        source: report ? 'GET /diag na porta 8766 (ao vivo)' : LIVE_TRACE.meta.sourceFiles.join(' · '),
      }}
      limits={[
        {
          what: 'Só aparece a chave que mudou',
          why:
            'O registro guarda eventos, não estado. Uma chave que não variou durante a janela não ' +
            'aparece aqui — e isso não diz nada sobre ela existir ou não no carro.',
        },
        {
          what: 'Sem unidade, sem escala',
          why:
            'Os valores são transcritos como vieram. O app não sabe se 42 é porcentagem, grau ou ' +
            'contagem, e não converte nem colore nada.',
        },
        {
          what: 'Isto não é leitura de DTC',
          why:
            'Código de falha vem por UDS, e o caminho para isso está mapeado abaixo. O que esta ' +
            'tela mostra são sinais de operação, não falhas.',
        },
      ]}
    >
      {erro && (
        <Banner tone="error" className="mb-4">
          <div className="font-medium mb-0.5">Não foi possível ler o relatório</div>
          <div className="text-xs opacity-85 leading-relaxed">{erro}</div>
        </Banner>
      )}

      <section className="mb-5">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-sm font-semibold text-text">Estado da ponte</h2>
            <ConfidenceChip level="OBSERVED" />
          </div>
          <Button variant="ghost" size="sm" onClick={() => void ler()} disabled={carregando}>
            <RefreshCwIcon size={13} className={carregando ? 'animate-spin' : ''} />
            {carregando ? 'Lendo…' : 'Reler'}
          </Button>
        </div>

        {report ? (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {[
                {
                  label: 'Binder agora',
                  value:
                    report.bridge.binderAlive === null
                      ? '—'
                      : report.bridge.binderAlive
                        ? 'vivo'
                        : 'sem binder',
                  tone: report.bridge.binderAlive === false ? 'text-danger' : 'text-text',
                },
                {
                  label: 'Perdas de binder',
                  value: report.bridge.binderTimeoutCount?.toString() ?? '—',
                  tone: 'text-text',
                },
                { label: 'Eventos do veículo', value: report.traceLines.toString(), tone: 'text-text' },
                { label: 'Chaves distintas', value: report.series.length.toString(), tone: 'text-text' },
              ].map((c) => (
                <div key={c.label} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-3 py-2">
                  <div className="text-[10px] text-text-tertiary">{c.label}</div>
                  <div className={`text-sm font-medium tabular-nums ${c.tone}`}>{c.value}</div>
                </div>
              ))}
            </div>

            {diagnose && (
              <Banner tone="warning" className="mt-3">
                <div className="font-medium mb-0.5">
                  {semSnapshot ? 'O snapshot está vazio, e o motivo é este' : 'A ponte está instável'}
                </div>
                <div className="text-xs opacity-85 leading-relaxed">{diagnose}</div>
              </Banner>
            )}
          </>
        ) : (
          <div className="rounded-xl border border-white/[0.08] bg-black/20 px-4 py-6 text-center text-xs text-text-tertiary">
            {carregando ? 'Lendo o relatório…' : 'Sem relatório. O carro precisa estar alcançável na rede.'}
          </div>
        )}
      </section>

      {report && (report.boot.bootReceiver !== null || report.boot.shizukuBinderReceived !== null) && (
        <section className="mb-5">
          <div className="flex flex-wrap items-center gap-2 mb-2">
            <h2 className="text-sm font-semibold text-text">Partida do Shizuku</h2>
            <ConfidenceChip level="OBSERVED" />
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
            {[
              { label: 'BOOT_COMPLETED', value: seg(report.boot.bootReceiver) },
              { label: '1ª tentativa', value: seg(report.boot.firstBootstrapAttempt) },
              { label: 'Binder pronto', value: seg(report.boot.shizukuBinderReceived) },
              { label: 'Depois do boot', value: seg(report.boot.binderAfterBootMs) },
            ].map((c) => (
              <div key={c.label} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-3 py-2">
                <div className="text-[10px] text-text-tertiary">{c.label}</div>
                <div className="text-sm font-medium tabular-nums text-text">{c.value}</div>
              </div>
            ))}
          </div>

          <p className="mt-2 text-[11px] text-text-tertiary leading-snug">
            Tudo contado a partir do momento em que a central ligou. &quot;Depois do boot&quot; é o que se
            sente esperando: quanto tempo passou entre o Android avisar que terminou de subir e o
            Shizuku ficar utilizável.
            {report.boot.startOutcome && report.boot.startOutcome !== 'BINDER_UP' && (
              <>
                {' '}
                O último start do servidor terminou em <strong>{report.boot.startOutcome}</strong>
                {report.boot.apkArg ? '' : ' (o starter subiu sem --apk=)'}.
              </>
            )}
          </p>

          {report.boot.staleBinderOnAlreadyRunningCount !== null &&
            report.boot.staleBinderOnAlreadyRunningCount > 0 && (
              <p className="mt-2 text-[11px] text-text-tertiary leading-snug">
                Esta partida foi <strong>morna</strong>, não fria: a central acordou em vez de dar
                boot, e por{' '}
                <strong>{report.boot.staleBinderOnAlreadyRunningCount}×</strong> o app encontrou o
                serviço de pé com o binder do Shizuku já morto — o servidor não sobrevive ao ciclo
                de energia, mas o processo do app sobrevive achando que sim. Nesses casos ele
                religa o servidor na hora, em vez de esperar a escada de tempos limite.
                {report.boot.lastStaleBinderOnAlreadyRunningStart !== null && (
                  <> A última vez foi em {seg(report.boot.lastStaleBinderOnAlreadyRunningStart)}.</>
                )}
              </p>
            )}

          {report.boot.serverLog && (
            <pre className="mt-2 max-h-28 overflow-auto rounded-xl border border-white/[0.08] bg-black/20 px-3 py-2 text-[10px] leading-relaxed text-text-tertiary whitespace-pre-wrap">
              {report.boot.serverLog}
            </pre>
          )}
        </section>
      )}

      <section className="mb-5">
        <div className="flex flex-wrap items-center gap-2 mb-2">
          <ActivityIcon size={14} className="text-text-tertiary" />
          <h2 className="text-sm font-semibold text-text">Eventos do veículo</h2>
          <ConfidenceChip level="OBSERVED" />
        </div>

        {report && report.series.length > 0 ? (
          <>
            <p className="text-[11px] text-text-tertiary leading-relaxed mb-2">
              {report.traceLines} eventos, {report.series.length} chaves distintas. O carro emitiu
              estes valores sozinho — nada foi solicitado a ele.
            </p>
            <ListPanel maxHeight="max-h-[22rem]">
              {report.series.map((s) => (
                <ListRow key={s.key}>
                  <div className="flex-1 min-w-0">
                    <div className="font-mono text-[11px] text-text truncate">{s.key}</div>
                    <div className="text-[10px] text-text-tertiary">
                      {s.points.length} amostra(s) · {horaDe(s.firstAt)} → {horaDe(s.lastAt)}
                      {s.distinct === 1 ? ' · não variou' : ` · ${s.distinct} valores distintos`}
                    </div>
                  </div>
                  <div className="text-right flex-shrink-0">
                    <div className="font-mono text-xs text-text tabular-nums">{s.last}</div>
                    {s.first !== s.last && (
                      <div className="font-mono text-[10px] text-text-tertiary tabular-nums">
                        de {s.first}
                      </div>
                    )}
                  </div>
                </ListRow>
              ))}
            </ListPanel>
          </>
        ) : (
          <div className="rounded-xl border border-white/[0.08] bg-black/20 px-4 py-5 text-center">
            <div className="text-xs text-text-tertiary leading-relaxed">
              {report
                ? 'O relatório veio sem eventos de veículo. O registro guarda uma janela curta: se o carro estiver parado e nenhuma chave mudar, ele fica assim.'
                : 'Sem leitura nesta sessão.'}
            </div>
            <div className="text-[11px] text-text-tertiary mt-2">
              A sessão de {LIVE_TRACE.meta.capturedAt} capturou {LIVE_TRACE.meta.count} chaves e{' '}
              {LIVE_TRACE.meta.samples} amostras por esta via — o histórico está na base de
              conhecimento.
            </div>
          </div>
        )}
      </section>

      <section>
        <div className="flex flex-wrap items-center gap-2 mb-2">
          <h2 className="text-sm font-semibold text-text">Caminhos de diagnóstico deste veículo</h2>
          <ConfidenceChip level="FACT" />
        </div>
        <p className="text-[11px] text-text-tertiary leading-relaxed mb-3">
          Quatro camadas, e cada uma para num lugar diferente. Vale distinguir duas frases que
          parecem a mesma: "o app não faz" é tarefa; "o veículo recusa" é limite.
        </p>
        <div className="space-y-2">
          {ACCESS_LAYERS.map((l) => (
            <div key={l.id} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-3">
              <div className="flex flex-wrap items-center gap-2 mb-1">
                <span className="text-xs font-medium text-text">{l.name}</span>
                <span
                  className={`text-[10px] px-1.5 py-0.5 rounded ${READINESS_STYLE[l.readiness].cls}`}
                >
                  {READINESS_STYLE[l.readiness].label}
                </span>
                <ConfidenceChip level={l.confidence} />
              </div>
              <div className="font-mono text-[10px] text-text-tertiary mb-1.5">{l.transport}</div>
              <div className="text-[11px] text-text-secondary leading-relaxed">{l.sees}</div>
              <div className="text-[11px] text-text-tertiary leading-relaxed mt-1">{l.state}</div>
            </div>
          ))}
        </div>
      </section>
    </DataScreen>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
