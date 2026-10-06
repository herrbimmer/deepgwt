// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import {
  AlertTriangleIcon, EyeOffIcon, RadarIcon, RefreshCwIcon, SearchXIcon,
} from 'lucide-react'
import { BackButton, Banner, Button, Card, ConfidenceChip, ListPanel, ListRow } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import { CONTROL_SERVICE } from '../../data/knowledge'
import { NOT_COVERED, coveragePct, runScan, type ScanResult } from '../../pro/scan'

const Stat = ({ label, value, hint }: { label: string; value: string; hint?: string }) => (
  <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-3 py-2.5">
    <div className="text-[11px] text-text-tertiary">{label}</div>
    <div className="text-lg font-semibold text-text tabular-nums leading-tight mt-0.5">{value}</div>
    {hint && <div className="text-[10px] text-text-tertiary mt-0.5 leading-snug">{hint}</div>}
  </div>
)

// Varredura do Veículo.
//
// É a tela que mais corre risco de virar teatro: "scan" tem um formato esperado — barra de
// progresso, módulos piscando, um número grande no fim. Aqui a varredura mede uma coisa só, e
// mede de verdade: quanto do dicionário de 818 chaves este carro respondeu, e o que ele respondeu
// que o dicionário não conhece. O resto da tela é a lista do que ela NÃO cobre, com o mesmo
// destaque do resultado.
export const ProVehicleScan = () => {
  const session = useCarSettings()
  const [result, setResult] = useState<ScanResult | null>(null)
  const [verSilenciosas, setVerSilenciosas] = useState(false)

  const run = async () => {
    // Uma varredura tem que ler agora, não reaproveitar o que estava na tela. `refresh()` é o
    // mesmo caminho do polling — se falhar, o erro da sessão aparece no banner abaixo.
    await session.refresh()
    setResult(
      runScan({
        values: session.values,
        source: session.source,
        carIp: session.carIp,
        vin: session.vin,
      }),
    )
  }

  const semResposta = useMemo(
    () => (result ? result.namespaces.filter((n) => n.answered === 0 && n.known > 0) : []),
    [result],
  )

  const podeLer = session.source === 'demo' || session.connection === 'online' || session.keyCount > 0

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Varredura do Veículo</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Compara o que este carro responde com o dicionário de {CONTROL_SERVICE.meta.count}{' '}
              chaves levantado em {CONTROL_SERVICE.meta.capturedAt}.
            </p>
          </div>
          <RadarIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        {!podeLer && (
          <Banner tone="warning" className="mb-4">
            Sem leitura disponível. Abra o DeepGWT no carro e confira o IP em Conexão, ou entre no modo
            demonstração para percorrer a tela com dados sintéticos.
          </Banner>
        )}
        {session.error && (
          <Banner tone="error" className="mb-4">
            {session.error}
          </Banner>
        )}

        <Button
          variant="primary"
          icon={result ? RefreshCwIcon : RadarIcon}
          onClick={run}
          disabled={session.status === 'loading'}
          className="mb-5"
        >
          {session.status === 'loading'
            ? 'Lendo o carro…'
            : result
              ? 'Executar novamente'
              : 'Executar varredura'}
        </Button>

        {!result ? (
          <div className="rounded-2xl border border-white/[0.08] bg-black/20 px-5 py-8 text-center">
            <SearchXIcon size={24} className="text-text-tertiary mx-auto mb-3" />
            <div className="text-sm font-medium text-text">Nenhuma varredura executada</div>
            <p className="text-xs text-text-tertiary mt-1.5 max-w-md mx-auto leading-relaxed">
              Nada é mostrado antes de ler. A tela não guarda resultado de sessão anterior nem
              exibe exemplo.
            </p>
          </div>
        ) : (
          <>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 mb-5">
              <Stat label="Respondidas" value={String(result.answered)} hint="chaves com resposta" />
              <Stat
                label="Cobertura"
                value={`${coveragePct(result)}%`}
                hint={`${result.knownAnswered} de ${result.knownTotal}`}
              />
              <Stat
                label="Sem resposta"
                value={String(result.knownSilent.length)}
                hint="do dicionário conhecido"
              />
              <Stat
                label="Fora do dump"
                value={String(result.unknownAnswered.length)}
                hint="descoberta a validar"
              />
            </div>

            <p className="text-[11px] text-text-tertiary mb-5 leading-relaxed">
              Leitura de {new Date(result.ts).toLocaleString('pt-BR')} ·{' '}
              {result.source === 'demo' ? 'dados sintéticos' : `carro em ${result.carIp || 'IP não definido'}`} ·{' '}
              {result.emptyValues} chave(s) responderam vazio (presentes, sem valor).
            </p>

            {result.unknownAnswered.length > 0 && (
              <section className="mb-6">
                <div className="flex flex-wrap items-center gap-2 mb-2">
                  <AlertTriangleIcon size={15} className="text-warning" />
                  <h2 className="text-sm font-semibold text-text">Chaves fora do levantamento</h2>
                  <ConfidenceChip level="REQUIRES_VALIDATION" />
                </div>
                <p className="text-[11px] text-text-tertiary leading-relaxed mb-2">
                  Este carro respondeu chaves que o dump de {CONTROL_SERVICE.meta.capturedAt} não
                  conhece. Que elas existam neste veículo é o que acabou de ser observado; o que
                  fazem, ninguém sabe. Entram como descoberta a validar — não como fato, e não no
                  catálogo.
                </p>
                <ListPanel maxHeight="max-h-52">
                  {result.unknownAnswered.map((k) => (
                    <ListRow key={k}>
                      <span className="font-mono text-[11px] text-text-secondary truncate">{k}</span>
                    </ListRow>
                  ))}
                </ListPanel>
              </section>
            )}

            <section className="mb-6">
              <h2 className="text-sm font-semibold text-text mb-2">Cobertura por grupo de chaves</h2>
              <ListPanel maxHeight="max-h-72">
                {result.namespaces.map((n) => {
                  const pct = n.known === 0 ? 0 : Math.round((n.answered / n.known) * 100)
                  return (
                    <ListRow key={n.namespace}>
                      <span className="font-mono text-[11px] text-text-secondary flex-1 truncate">
                        {n.namespace}
                      </span>
                      <div className="w-24 h-1.5 rounded-full bg-white/[0.06] overflow-hidden flex-shrink-0">
                        <div
                          className={`h-full ${pct === 0 ? 'bg-white/10' : 'bg-primary/70'}`}
                          style={{ width: `${pct}%` }}
                        />
                      </div>
                      <span className="text-[11px] text-text-tertiary tabular-nums w-20 text-right flex-shrink-0">
                        {n.answered}/{n.known}
                      </span>
                      {n.unknown > 0 && (
                        <span className="text-[10px] text-warning tabular-nums flex-shrink-0">
                          +{n.unknown}
                        </span>
                      )}
                    </ListRow>
                  )
                })}
              </ListPanel>
              {semResposta.length > 0 && (
                <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
                  {semResposta.length} grupo(s) não responderam nenhuma chave. Isso pode significar
                  que o carro não tem o sistema, que a chave existe e não está publicada nesta
                  interface, ou que o valor só aparece com o veículo em outro estado. A varredura
                  não escolhe entre essas hipóteses.
                </p>
              )}
            </section>

            <section className="mb-6">
              <button
                type="button"
                onClick={() => setVerSilenciosas((v) => !v)}
                className="flex items-center gap-2 text-xs text-text-secondary hover:text-text transition-colors"
              >
                <EyeOffIcon size={14} />
                {verSilenciosas ? 'Esconder' : 'Ver'} as {result.knownSilent.length} chaves sem resposta
              </button>
              {verSilenciosas && (
                <ListPanel maxHeight="max-h-64" className="mt-2">
                  {result.knownSilent.map((k) => (
                    <ListRow key={k}>
                      <span className="font-mono text-[11px] text-text-tertiary truncate">{k}</span>
                    </ListRow>
                  ))}
                </ListPanel>
              )}
            </section>
          </>
        )}

        {/* A lista do que a varredura não cobre fica FORA do `if (result)`: ela não é uma nota de
            rodapé do resultado, é parte da definição do que esta tela faz. */}
        <section className="mt-2">
          <h2 className="text-sm font-semibold text-text mb-2">O que esta varredura não cobre</h2>
          <div className="space-y-2">
            {NOT_COVERED.map((n) => (
              <div key={n.what} className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-2.5">
                <div className="text-xs font-medium text-text">{n.what}</div>
                <div className="text-[11px] text-text-tertiary mt-0.5 leading-relaxed">{n.why}</div>
              </div>
            ))}
          </div>
        </section>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
