import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArchiveIcon, ClipboardListIcon, FlaskConicalIcon, XCircleIcon } from 'lucide-react'
import { BackButton, Banner, Card, ListPanel, ListRow } from '../../ui'
import { loadChangeLog, subscribeChangeLog, type ChangeEntry } from '../../state/changeLog'
import { loadSnapshots, subscribeSnapshots, type Snapshot } from '../../state/snapshots'
import { labelOf, showValue } from '../../coding/keyModel'

const SEM_VIN = '(sem VIN registrado)'
const dia = (ts: number) => new Date(ts).toLocaleDateString('pt-BR')
const hora = (ts: number) => new Date(ts).toLocaleTimeString('pt-BR')

type Ficha = {
  vin: string
  entries: ChangeEntry[]
  snapshots: Snapshot[]
  primeira: number
  ultima: number
  falhas: number
  demo: number
}

// Histórico — a leitura de oficina do mesmo registro.
//
// A tela do usuário (`/alteracoes`) é uma caixa de ferramentas: desfazer, restaurar, apagar. Esta
// é um registro de serviço: agrupa por VEÍCULO e por dia, e não tem um único botão que escreve.
// O mesmo dado, duas perguntas diferentes — "o que eu mexi?" contra "o que já fizeram neste
// carro?".
//
// O limite está no topo e não é detalhe: este histórico só enxerga o que ESTE aplicativo escreveu.
// Um carro que passou por outra ferramenta aparece aqui como um carro em que nada foi feito.
export const ProWorkshopHistory = () => {
  const [entries, setEntries] = useState<ChangeEntry[]>(loadChangeLog)
  const [snapshots, setSnapshots] = useState<Snapshot[]>(loadSnapshots)
  const [vinAtivo, setVinAtivo] = useState<string | null>(null)

  useEffect(() => subscribeChangeLog(() => setEntries(loadChangeLog())), [])
  useEffect(() => subscribeSnapshots(() => setSnapshots(loadSnapshots())), [])

  const fichas = useMemo<Ficha[]>(() => {
    const mapa = new Map<string, Ficha>()
    const pegar = (vin: string): Ficha => {
      let f = mapa.get(vin)
      if (!f) {
        f = { vin, entries: [], snapshots: [], primeira: Infinity, ultima: 0, falhas: 0, demo: 0 }
        mapa.set(vin, f)
      }
      return f
    }
    for (const e of entries) {
      const f = pegar(e.vin ?? SEM_VIN)
      f.entries.push(e)
      f.primeira = Math.min(f.primeira, e.ts)
      f.ultima = Math.max(f.ultima, e.ts)
      if (e.result === 'error') f.falhas += 1
      if (e.source === 'demo') f.demo += 1
    }
    for (const s of snapshots) {
      const f = pegar(s.vin ?? SEM_VIN)
      f.snapshots.push(s)
      f.primeira = Math.min(f.primeira, s.ts)
      f.ultima = Math.max(f.ultima, s.ts)
    }
    return [...mapa.values()].sort((a, b) => b.ultima - a.ultima)
  }, [entries, snapshots])

  const ficha = useMemo(
    () => fichas.find((f) => f.vin === vinAtivo) ?? fichas[0] ?? null,
    [fichas, vinAtivo],
  )

  // Eventos do veículo selecionado, agrupados por dia — é assim que um registro de serviço se lê.
  const porDia = useMemo(() => {
    if (!ficha) return []
    const mapa = new Map<string, ChangeEntry[]>()
    for (const e of [...ficha.entries].sort((a, b) => b.ts - a.ts)) {
      const d = dia(e.ts)
      const lista = mapa.get(d) ?? []
      lista.push(e)
      mapa.set(d, lista)
    }
    return [...mapa.entries()]
  }, [ficha])

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Histórico</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Registro de serviço por veículo: o que foi escrito, quando, e com que resultado.
            </p>
          </div>
          <ClipboardListIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <Banner tone="warning" className="mb-5">
          <div className="font-medium mb-0.5">Este histórico só enxerga este aplicativo</div>
          <div className="text-xs opacity-85 leading-relaxed">
            Alterações feitas por outra ferramenta, por outro computador ou pela própria tela do
            carro não aparecem aqui. Um veículo sem registro nesta tela é um veículo em que{' '}
            <em>este app</em> não escreveu — não um veículo em que ninguém mexeu.
          </div>
        </Banner>

        {fichas.length === 0 ? (
          <div className="rounded-2xl border border-white/[0.08] bg-black/20 px-5 py-8 text-center">
            <ArchiveIcon size={24} className="text-text-tertiary mx-auto mb-3" />
            <div className="text-sm font-medium text-text">Nenhum veículo no registro</div>
            <p className="text-xs text-text-tertiary mt-1.5 max-w-md mx-auto leading-relaxed">
              O registro se preenche sozinho na primeira escrita ou no primeiro ponto de
              restauração.
            </p>
          </div>
        ) : (
          <>
            <section className="mb-6">
              <h2 className="text-sm font-semibold text-text mb-2">Veículos ({fichas.length})</h2>
              <div className="space-y-2">
                {fichas.map((f) => {
                  const ativo = ficha?.vin === f.vin
                  return (
                    <button
                      key={f.vin}
                      type="button"
                      onClick={() => setVinAtivo(f.vin)}
                      className={`w-full text-left rounded-xl border px-4 py-3 transition-colors ${
                        ativo
                          ? 'border-primary/40 bg-primary/[0.08]'
                          : 'border-white/[0.08] bg-white/[0.02] hover:bg-white/[0.05]'
                      }`}
                    >
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-xs text-text truncate">{f.vin}</span>
                        {f.demo > 0 && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-warning/15 text-warning px-1.5 py-0.5 text-[10px]">
                            <FlaskConicalIcon size={9} />
                            {f.demo} de demonstração
                          </span>
                        )}
                        {f.falhas > 0 && (
                          <span className="inline-flex items-center gap-1 rounded-full bg-danger/15 text-danger px-1.5 py-0.5 text-[10px]">
                            <XCircleIcon size={9} />
                            {f.falhas} falha(s)
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] text-text-tertiary mt-1">
                        {f.entries.length} alteração(ões) · {f.snapshots.length} ponto(s) de
                        restauração · de {dia(f.primeira)} a {dia(f.ultima)}
                      </div>
                    </button>
                  )
                })}
              </div>
            </section>

            {ficha && (
              <section className="mb-6">
                <h2 className="text-sm font-semibold text-text mb-2">
                  Linha do tempo — <span className="font-mono text-xs">{ficha.vin}</span>
                </h2>
                {porDia.length === 0 ? (
                  <p className="text-xs text-text-tertiary">
                    Nenhuma alteração registrada para este veículo. Há {ficha.snapshots.length}{' '}
                    ponto(s) de restauração guardado(s).
                  </p>
                ) : (
                  <div className="space-y-4">
                    {porDia.map(([d, lista]) => (
                      <div key={d}>
                        <div className="text-[11px] font-medium text-text-tertiary mb-1.5">
                          {d} · {lista.length} evento(s)
                        </div>
                        <ListPanel maxHeight="max-h-none">
                          {lista.map((e) => (
                            <ListRow key={e.id}>
                              <div className="flex-1 min-w-0">
                                <div className="text-xs text-text truncate">{labelOf(e.key)}</div>
                                <div className="font-mono text-[10px] text-text-tertiary truncate">
                                  {e.key}
                                </div>
                              </div>
                              <span className="font-mono text-[11px] text-text-tertiary flex-shrink-0">
                                {e.from === null ? '—' : showValue(e.from)} → {showValue(e.to)}
                              </span>
                              <span
                                className={`text-[10px] px-1.5 py-0.5 rounded flex-shrink-0 ${
                                  e.result === 'ok'
                                    ? 'bg-success/15 text-success'
                                    : e.result === 'error'
                                      ? 'bg-danger/15 text-danger'
                                      : 'bg-white/[0.06] text-text-tertiary'
                                }`}
                              >
                                {e.result === 'ok' ? 'gravado' : e.result === 'error' ? 'falhou' : 'pendente'}
                              </span>
                              <span className="text-[10px] text-text-tertiary flex-shrink-0 w-14 text-right">
                                {hora(e.ts)}
                              </span>
                            </ListRow>
                          ))}
                        </ListPanel>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )}

            {ficha && ficha.snapshots.length > 0 && (
              <section className="mb-6">
                <h2 className="text-sm font-semibold text-text mb-2">Pontos de restauração</h2>
                <ListPanel maxHeight="max-h-52">
                  {[...ficha.snapshots]
                    .sort((a, b) => b.ts - a.ts)
                    .map((s) => (
                      <ListRow key={s.id}>
                        <span className="text-xs text-text flex-1 truncate">{s.name}</span>
                        <span className="text-[11px] text-text-tertiary flex-shrink-0">
                          {Object.keys(s.values).length} chaves
                        </span>
                        <span className="text-[11px] text-text-tertiary flex-shrink-0">
                          {dia(s.ts)}
                          {s.dateIsApproximate && ' (data da conversão)'}
                        </span>
                      </ListRow>
                    ))}
                </ListPanel>
              </section>
            )}
          </>
        )}

        <p className="text-[11px] text-text-tertiary mb-1">
          Esta tela não escreve nada. Para desfazer, restaurar, exportar ou apagar, use{' '}
          <Link to="/alteracoes" className="underline underline-offset-2">
            Alterações e Restauração
          </Link>
          .
        </p>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
