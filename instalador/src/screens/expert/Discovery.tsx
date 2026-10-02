import { useMemo, useState } from 'react'
import { CompassIcon, SearchIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, ConfidenceChip, ListPanel, ListRow, TextInput } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import { lookupKey, VERDICT_TEXT, type KeyLookup } from '../../expert/discovery'
import { runScan } from '../../pro/scan'
import { showValue } from '../../coding/keyModel'

// Descoberta.
//
// Duas metades, e a ordem entre elas é intencional. Em cima, a consulta pontual: um nome de chave
// vindo de um log ou de um APK descompilado, e a resposta honesta sobre ele — inclusive a resposta
// "nunca vimos", que NÃO é "não existe". Embaixo, o que este carro respondeu e o levantamento não
// conhece: descoberta nova, listada como hipótese a validar.
//
// Nada nesta tela entra no catálogo. Uma chave que apareceu uma vez é uma chave que apareceu uma
// vez; virar fato exige reprodução, método e data registrados na base de conhecimento — e isso é
// trabalho de fora do app.
export const ExpertDiscovery = () => {
  const session = useCarSettings()
  const [entrada, setEntrada] = useState('')
  const [consulta, setConsulta] = useState<KeyLookup | null>(null)

  const foraDoLevantamento = useMemo(() => {
    const r = runScan({
      values: session.values,
      source: session.source,
      carIp: session.carIp,
      vin: session.vin,
    })
    return r.unknownAnswered
  }, [session.values, session.source, session.carIp, session.vin])

  const consultar = () => {
    const chave = entrada.trim()
    setConsulta(chave ? lookupKey(chave, session.values) : null)
  }

  const texto = consulta ? VERDICT_TEXT[consulta.verdict] : null

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Descoberta</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Consulte uma chave pelo nome e veja o que sabemos — e o que só achamos que sabemos.
            </p>
          </div>
          <CompassIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <div className="flex flex-col sm:flex-row gap-2 mb-3">
          <TextInput
            icon={SearchIcon}
            mono
            placeholder="car.basic.vehicle_speed"
            value={entrada}
            onChange={(e) => setEntrada(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && consultar()}
            containerClassName="flex-1"
          />
          <Button variant="secondary" onClick={consultar} className="sm:w-auto">
            Consultar
          </Button>
        </div>

        {consulta && texto && (
          <div className="rounded-2xl border border-white/[0.08] bg-black/20 px-5 py-4 mb-4">
            <div className="flex flex-wrap items-center gap-2 mb-1.5">
              <span className="font-mono text-xs text-text break-all">{consulta.key}</span>
              <ConfidenceChip level={texto.confidence} />
            </div>
            <div className="text-sm font-medium text-text">{texto.title}</div>
            <p className="text-[11px] text-text-tertiary mt-1.5 leading-relaxed">{texto.body}</p>

            {consulta.value !== undefined && (
              <div className="mt-3 flex items-center gap-2 text-[11px]">
                <span className="text-text-tertiary">Valor nesta sessão</span>
                <span className="font-mono text-primary">{showValue(consulta.value)}</span>
              </div>
            )}
            {consulta.entry && (
              <div className="mt-2 grid gap-1 text-[11px]">
                <Linha rotulo="Enum no dicionário" valor={consulta.entry.enumName} />
                <Linha rotulo="Namespace" valor={consulta.entry.namespace} />
                <Linha
                  rotulo="Exposta pela tela do carro"
                  valor={consulta.entry.appState ?? 'não classificada'}
                />
              </div>
            )}
          </div>
        )}

        <Banner tone="info" className="mb-4">
          <div className="text-xs leading-relaxed">
            Esta tela só enxerga o que o ControlService publica no instantâneo. Uma chave pode existir
            no carro e nunca aparecer aqui — por não ser publicada nesta versão de software, ou por
            depender de um estado que o veículo não está agora.
          </div>
        </Banner>

        <section>
          <h2 className="text-sm font-semibold text-text mb-1">
            Respondidas fora do levantamento ({foraDoLevantamento.length})
          </h2>
          <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">
            Chaves que este carro devolveu e que não estão nas 818 levantadas. Cada uma é hipótese
            até ser reproduzida e registrada com método e data.
          </p>
          {foraDoLevantamento.length === 0 ? (
            <div className="rounded-xl border border-white/[0.08] bg-white/[0.02] px-4 py-6 text-center text-xs text-text-tertiary">
              Nenhuma. Tudo que este carro respondeu já está no levantamento.
            </div>
          ) : (
            <ListPanel maxHeight="max-h-72">
              {foraDoLevantamento.map((k) => (
                <ListRow key={k}>
                  <span className="font-mono text-[11px] text-text flex-1 min-w-0 truncate">{k}</span>
                  <span className="font-mono text-[11px] text-text-secondary flex-shrink-0 max-w-[8rem] truncate text-right">
                    {showValue(session.values[k] === '' ? null : session.values[k])}
                  </span>
                  <ConfidenceChip level="REQUIRES_VALIDATION" />
                </ListRow>
              ))}
            </ListPanel>
          )}
        </section>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}

function Linha({ rotulo, valor }: { rotulo: string; valor: string }) {
  return (
    <div className="flex items-start gap-2">
      <span className="text-text-tertiary w-44 flex-shrink-0">{rotulo}</span>
      <span className="font-mono text-text break-all">{valor}</span>
    </div>
  )
}
