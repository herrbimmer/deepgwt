import { useCallback, useMemo, useState } from 'react'
import { FactoryIcon, RefreshCwIcon, SearchIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, ConfidenceChip, ListPanel, ListRow, TextInput } from '../../ui'
import { readFactoryCodingLive, type LiveFactoryCoding } from '../../pro/factoryCodingLive'
import { hasTauri } from '../../lib/fileExchange'
import { useCarSettings } from '../../state/VehicleSession'
import { CONTROL_SERVICE, FACTORY_BY_NAME, FACTORY_CODING } from '../../data/knowledge'
import { RISK_CLASSES, isCurated, labelOf, riskOf, showValue } from '../../coding/keyModel'

const PREFIX = 'car.configure.'

// Configuração de fábrica — duas tabelas, e saber qual é qual muda o diagnóstico.
//
// A que o app lia até 2026-09-15 era `car.configure.*`: 164 chaves, das quais o carro respondeu
// QUATRO. Uma tela quase vazia, e a conclusão fácil seria "o carro não expõe a configuração".
//
// A sessão com o veículo mostrou que a codificação de variante de verdade está em outro lugar:
// `persist.vendor.gwm.cfg.*`, 546 flags lidas por getprop, 154 delas ligadas. É o bitmap de
// equipamento que a fábrica gravou nesta unidade — a resposta para "este carro TEM tal função?".
// A proporção importa: se fossem 546 zeros, seria tabela não populada; com 154 ligadas, é
// declaração real.
//
// O que continua igual nas duas: o número não é traduzido. `mood.lamp = 3` diz que há iluminação
// ambiente e que a variante é a 3. O que a 3 é, ninguém aqui sabe, e deduzir contaminaria tudo que
// dependesse disso.

type Aba = 'fabrica' | 'configure'

export const ProFactoryConfiguration = () => {
  const session = useCarSettings()
  const [aba, setAba] = useState<Aba>('fabrica')
  const [busca, setBusca] = useState('')
  const [soEquipadas, setSoEquipadas] = useState(true)
  const [soRespondidas, setSoRespondidas] = useState(true)
  const [aoVivo, setAoVivo] = useState<LiveFactoryCoding | null>(null)
  const [lendo, setLendo] = useState(false)
  const [erroLeitura, setErroLeitura] = useState<string | null>(null)

  // Relê a codificação do carro agora e compara com a captura. Só leitura: `getprop`.
  const relerDoCarro = useCallback(async () => {
    if (!hasTauri()) {
      setErroLeitura(
        'Esta tela precisa do aplicativo instalado (a janela do OPEN GW), não do navegador: a leitura passa pelo processo nativo, que o Vite sozinho não tem.',
      )
      return
    }
    setLendo(true)
    setErroLeitura(null)
    try {
      setAoVivo(await readFactoryCodingLive())
    } catch (e) {
      setErroLeitura(e instanceof Error ? e.message : String(e))
      setAoVivo(null)
    } finally {
      setLendo(false)
    }
  }, [])

  // --- aba 1: codificação de fábrica lida do carro -------------------------
  // Quando há leitura ao vivo, ela manda: o valor de agora vale mais que o da captura. Sem ela, a
  // tela mostra a captura e diz a data no cabeçalho de procedência.
  const flags = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    const base = aoVivo
      ? aoVivo.flags.map((f) => ({
          name: f.name,
          prop: `persist.vendor.gwm.cfg.${f.name}`,
          value: f.value,
          equipped: f.equipped,
          domain: FACTORY_BY_NAME.get(f.name)?.domain ?? 'Outros',
          changed: f.changed,
          knownValue: f.knownValue,
        }))
      : FACTORY_CODING.flags.map((f) => ({ ...f, changed: false, knownValue: null as string | null }))
    return base
      .filter((f) => (soEquipadas ? f.equipped : true))
      .filter((f) => (termo ? f.name.toLowerCase().includes(termo) : true))
  }, [busca, soEquipadas, aoVivo])

  const porDominio = useMemo(() => {
    const grupos = new Map<string, typeof flags>()
    for (const f of flags) {
      const lista = grupos.get(f.domain) ?? []
      lista.push(f)
      grupos.set(f.domain, lista)
    }
    return [...grupos.entries()].sort((a, b) => b[1].length - a[1].length)
  }, [flags])

  // --- aba 2: car.configure.* lido ao vivo nesta sessão --------------------
  const conhecidas = useMemo(
    () => CONTROL_SERVICE.keys.filter((k) => k.key.startsWith(PREFIX)).map((k) => k.key).sort(),
    [],
  )

  const respondidasForaDoDump = useMemo(
    () => Object.keys(session.values).filter((k) => k.startsWith(PREFIX) && !conhecidas.includes(k)).sort(),
    [session.values, conhecidas],
  )

  const linhas = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    return [...conhecidas, ...respondidasForaDoDump]
      .map((key) => {
        const raw = session.values[key]
        return {
          key,
          value: raw === undefined || raw === '' ? null : raw,
          respondida: raw !== undefined,
          foraDoDump: !conhecidas.includes(key),
        }
      })
      .filter((l) => (soRespondidas ? l.respondida : true))
      .filter((l) => (termo ? l.key.toLowerCase().includes(termo) || labelOf(l.key).toLowerCase().includes(termo) : true))
  }, [conhecidas, respondidasForaDoDump, session.values, busca, soRespondidas])

  const respondidas = useMemo(
    () => conhecidas.filter((k) => k in session.values).length,
    [conhecidas, session.values],
  )

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-text tracking-tight">Configuração de Fábrica</h1>
              <ConfidenceChip level="FACT" />
            </div>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              O que a fábrica declarou que este carro é:{' '}
              {aoVivo ? aoVivo.equippedCount : FACTORY_CODING.meta.equippedCount} itens equipados em{' '}
              {aoVivo ? aoVivo.flags.length : FACTORY_CODING.meta.count} flags,{' '}
              {aoVivo
                ? 'lidos do carro agora.'
                : `lidos do veículo em ${FACTORY_CODING.meta.capturedAt}.`}
            </p>
          </div>
          <FactoryIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <div className="flex gap-1 mb-4 rounded-xl bg-black/20 p-1">
          {(
            [
              ['fabrica', `Codificação de variante (${FACTORY_CODING.meta.count})`],
              ['configure', `car.configure.* (${respondidas}/${conhecidas.length})`],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              onClick={() => setAba(id)}
              className={`flex-1 rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
                aba === id ? 'bg-white/[0.08] text-text' : 'text-text-tertiary hover:text-text-secondary'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-2 mb-3">
          <Button variant="secondary" size="sm" onClick={() => void relerDoCarro()} disabled={lendo}>
            <RefreshCwIcon size={13} className={lendo ? 'animate-spin' : ''} />
            {lendo ? 'Lendo do carro…' : 'Ler do carro agora'}
          </Button>
          <span className="text-[11px] text-text-tertiary">
            {aoVivo
              ? `Leitura de ${new Date(aoVivo.readAt).toLocaleTimeString('pt-BR')} — getprop pelo shell, nada escrito.`
              : `Exibindo a captura de ${FACTORY_CODING.meta.capturedAt}. Exige conexão telnet com o carro.`}
          </span>
        </div>

        {erroLeitura && (
          <Banner tone="error" className="mb-4">
            <div className="font-medium mb-0.5">Não foi possível ler do carro</div>
            <div className="text-xs opacity-85 leading-relaxed">
              {erroLeitura} — a tela continua mostrando a captura de {FACTORY_CODING.meta.capturedAt}.
            </div>
          </Banner>
        )}

        {aoVivo && (aoVivo.changed.length > 0 || aoVivo.novas.length > 0 || aoVivo.ausentes.length > 0) && (
          <Banner tone={aoVivo.changed.length > 0 ? 'warning' : 'info'} className="mb-4">
            <div className="font-medium mb-0.5">A leitura de agora difere da captura</div>
            <div className="text-xs opacity-85 leading-relaxed">
              {aoVivo.changed.length > 0 && (
                <>
                  {aoVivo.changed.length} flag(s) com valor diferente:{' '}
                  <span className="font-mono">
                    {aoVivo.changed.slice(0, 6).map((f) => `${f.name} ${f.knownValue}→${f.value}`).join(', ')}
                    {aoVivo.changed.length > 6 ? '…' : ''}
                  </span>
                  .{' '}
                </>
              )}
              {aoVivo.novas.length > 0 && <>{aoVivo.novas.length} flag(s) fora do catálogo. </>}
              {aoVivo.ausentes.length > 0 && <>{aoVivo.ausentes.length} do catálogo não responderam. </>}
              Divergência não é erro nem fraude: pode ser recodificação, outra versão de firmware ou
              outro veículo. A tela relata, não conclui.
            </div>
          </Banner>
        )}

        <Banner tone="warning" className="mb-4">
          <div className="font-medium mb-0.5">Valores crus, sem tradução</div>
          <div className="text-xs opacity-85 leading-relaxed">
            O projeto não tem a tabela que traduz estes códigos. Um <code className="font-mono">3</code>{' '}
            continua <code className="font-mono">3</code> na tela. Diferente de zero significa que a
            fábrica declarou o item presente — isso é fato. Qual variante o número identifica, não.
          </div>
        </Banner>

        <div className="flex flex-col sm:flex-row gap-2 mb-3">
          <TextInput
            icon={SearchIcon}
            placeholder={aba === 'fabrica' ? 'Buscar item de equipamento…' : 'Buscar chave de configuração…'}
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            containerClassName="flex-1"
          />
          {aba === 'fabrica' ? (
            <label className="flex items-center gap-2 text-xs text-text-secondary cursor-pointer px-1">
              <input
                type="checkbox"
                checked={soEquipadas}
                onChange={(e) => setSoEquipadas(e.target.checked)}
                className="accent-primary"
              />
              Só o que este carro tem
            </label>
          ) : (
            <label className="flex items-center gap-2 text-xs text-text-secondary cursor-pointer px-1">
              <input
                type="checkbox"
                checked={soRespondidas}
                onChange={(e) => setSoRespondidas(e.target.checked)}
                className="accent-primary"
              />
              Só as que o carro respondeu
            </label>
          )}
        </div>

        {aba === 'fabrica' ? (
          <>
            <ListPanel maxHeight="max-h-[26rem]">
              {flags.length === 0 ? (
                <div className="px-4 py-8 text-center text-xs text-text-tertiary">
                  Nenhum item corresponde ao filtro.
                </div>
              ) : (
                porDominio.map(([dominio, itens]) => (
                  <div key={dominio}>
                    <div className="sticky top-0 z-10 bg-surface/95 backdrop-blur px-4 py-1.5 text-[10px] font-semibold uppercase tracking-wide text-text-tertiary border-b border-white/[0.06]">
                      {dominio}
                      <span className="ml-2 font-normal normal-case tracking-normal">
                        {FACTORY_CODING.meta.byDomain[dominio]?.equipped ?? 0} equipados de{' '}
                        {FACTORY_CODING.meta.byDomain[dominio]?.total ?? 0}
                      </span>
                    </div>
                    {itens.map((f) => (
                      <ListRow key={f.prop}>
                        <div className="flex-1 min-w-0">
                          <div className="font-mono text-[11px] text-text truncate">{f.name}</div>
                          <div className="font-mono text-[10px] text-text-tertiary truncate">{f.prop}</div>
                          {f.changed && (
                            <div className="text-[10px] text-warning">
                              na captura de {FACTORY_CODING.meta.capturedAt} era {f.knownValue}
                            </div>
                          )}
                        </div>
                        {f.equipped ? (
                          <span className="font-mono text-xs text-success tabular-nums flex-shrink-0">
                            {f.value}
                          </span>
                        ) : (
                          <span className="text-[11px] text-text-tertiary flex-shrink-0">ausente</span>
                        )}
                      </ListRow>
                    ))}
                  </div>
                ))
              )}
            </ListPanel>

            <p className="text-[11px] text-text-tertiary mt-3 leading-relaxed">
              Lido por <code className="font-mono">getprop persist.vendor.gwm.cfg.*</code> no head
              unit — leitura, nada escrito. Esta é a tabela que diz se uma função existe neste
              carro: oferecer um ajuste cuja flag está zerada faz o app pedir algo que nenhum módulo
              vai executar.
            </p>
          </>
        ) : (
          <>
            <ListPanel maxHeight="max-h-[26rem]">
              {linhas.length === 0 ? (
                <div className="px-4 py-8 text-center text-xs text-text-tertiary">
                  Nenhuma chave corresponde ao filtro.
                </div>
              ) : (
                linhas.map((l) => (
                  <ListRow key={l.key}>
                    <div className="flex-1 min-w-0">
                      <div className="text-xs text-text truncate">
                        {isCurated(l.key) ? labelOf(l.key) : l.key.slice(PREFIX.length)}
                      </div>
                      <div className="font-mono text-[10px] text-text-tertiary truncate">{l.key}</div>
                      {l.foraDoDump && (
                        <div className="text-[10px] text-warning">fora do levantamento — a validar</div>
                      )}
                    </div>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded flex-shrink-0 ${RISK_CLASSES[riskOf(l.key)]}`}>
                      {riskOf(l.key)}
                    </span>
                    {l.respondida ? (
                      <span className="font-mono text-xs text-text tabular-nums flex-shrink-0 max-w-[9rem] truncate text-right">
                        {showValue(l.value)}
                      </span>
                    ) : (
                      <span className="text-[11px] text-text-tertiary italic flex-shrink-0">sem resposta</span>
                    )}
                  </ListRow>
                ))
              )}
            </ListPanel>

            <p className="text-[11px] text-text-tertiary mt-3 leading-relaxed">
              Esta aba é a leitura ao vivo do ControlService, e quase tudo aqui fica sem resposta —
              foi o que motivou procurar a codificação em outro lugar. Chave sem resposta não é
              equipamento ausente: é ausência de resposta desta interface. Para saber o que o carro
              tem, a primeira aba é a fonte.
            </p>
          </>
        )}

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
