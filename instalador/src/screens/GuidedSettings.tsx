// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import {
  ArrowLeftIcon, ChevronRightIcon, SearchIcon, ShieldAlertIcon, SlidersHorizontalIcon,
  CheckIcon, HistoryIcon, CameraIcon,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { BackButton, Banner, Button, Card, ListPanel, ListRow, Modal, TextInput } from '../ui'
import { useCarSettings } from '../state/VehicleSession'
import { CODING_CATALOG, CODING_CATEGORIES, type CodingCatalogEntry } from '../data/codingCatalog'
import { RISK_CLASSES, RISK_LABEL, RISK_REASON, looksBoolean, riskOf, showValue } from '../coding/keyModel'

// Ajustes guiados — a camada USER do coding.
//
// A tela de Coding continua existindo e continua igual: ela é a lista crua das 818 chaves, para
// quem quer ir além. Esta aqui é o caminho estreito: categoria -> função -> o que ela faz em
// português -> como está agora -> opções -> confirmação.
//
// DUAS REGRAS QUE ESTA TELA NÃO QUEBRA:
//
// 1. Não inventa opção. Só oferece "Ligado/Desligado" quando o carro respondeu exatamente "0" ou
//    "1" naquela chave. Se respondeu "3", a tela diz que não sabe quais são as opções e manda
//    para o editor avançado — em vez de desenhar um seletor com valores plausíveis. O domínio
//    aceito por cada chave não está documentado em lugar nenhum.
//
// 2. Não esconde risco para simplificar. O usuário comum não precisa saber o que é DID ou UDS,
//    mas precisa saber que desligar um assistente de frenagem tem consequência. O jargão sai; o
//    aviso fica.
export const GuidedSettings = () => {
  const { values, set, fieldStatus, source, status, refresh } = useCarSettings()
  const [category, setCategory] = useState<string | null>(null)
  const [query, setQuery] = useState('')
  const [selected, setSelected] = useState<CodingCatalogEntry | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  const [ack, setAck] = useState(false)

  const read = (key: string): string | undefined => values[key]

  const categories = useMemo(
    () =>
      CODING_CATEGORIES.map((name) => {
        const all = CODING_CATALOG.filter((e) => e.category === name)
        return {
          name,
          total: all.length,
          answered: all.filter((e) => read(e.key) !== undefined).length,
        }
      }),
    [values],
  )

  const listed = useMemo(() => {
    const q = query.trim().toLowerCase()
    return CODING_CATALOG.filter((e) => {
      if (!q && category && e.category !== category) return false
      if (!q) return true
      return (
        e.name.toLowerCase().includes(q) ||
        e.description.toLowerCase().includes(q) ||
        e.category.toLowerCase().includes(q)
      )
    })
  }, [category, query])

  const connected = Object.keys(values).length > 0

  /** Como a tela descreve o valor atual, SEM adivinhar o que um valor desconhecido significa. */
  const stateLabel = (key: string): { text: string; cls: string } => {
    const raw = read(key)
    if (raw === undefined) return { text: 'não respondido pelo carro', cls: 'text-text-tertiary' }
    if (raw === '1') return { text: 'Ligado', cls: 'text-success' }
    if (raw === '0') return { text: 'Desligado', cls: 'text-text-secondary' }
    return { text: `valor ${showValue(raw)} — sem tradução conhecida`, cls: 'text-amber-200' }
  }

  const confirmWrite = () => {
    if (!selected || pending === null) return
    set(selected.key, pending)
    setPending(null)
    setAck(false)
  }

  // ------------------------------------------------------------------ detalhe de uma função
  if (selected) {
    const raw = read(selected.key)
    const risk = riskOf(selected.key)
    const boolean = looksBoolean(raw)
    const st = stateLabel(selected.key)
    const fs = fieldStatus[selected.key]

    return (
      <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
        <Card maxWidth="md">
          <button
            onClick={() => setSelected(null)}
            className="flex items-center gap-1.5 text-xs text-text-tertiary hover:text-text mb-5 transition-colors"
          >
            <ArrowLeftIcon size={13} />
            {selected.category}
          </button>

          <h1 className="text-xl font-semibold text-text tracking-tight mb-2">{selected.name}</h1>
          <p className="text-text-secondary text-sm leading-relaxed mb-5">{selected.description}</p>

          <div className="rounded-2xl border border-white/[0.08] bg-white/[0.02] p-4 mb-5">
            <div className="text-[11px] text-text-tertiary mb-1">Como está agora</div>
            <div className={`text-lg font-medium ${st.cls}`}>{st.text}</div>
            {fs && (
              <div className="text-[11px] mt-1 text-text-tertiary">
                {fs === 'saving' ? 'Gravando no carro...' : fs === 'saved' ? 'Gravado.' : 'Falhou ao gravar.'}
              </div>
            )}
          </div>

          <div className={`rounded-2xl p-4 mb-5 ${risk === 'alto' ? 'bg-danger/[0.07] border border-danger/25' : risk === 'medio' ? 'bg-warning/[0.07] border border-warning/25' : 'bg-white/[0.02] border border-white/[0.08]'}`}>
            <div className="flex items-center gap-2 mb-1.5">
              <span className={`px-2 py-0.5 rounded text-[10px] uppercase ${RISK_CLASSES[risk]}`}>
                risco {RISK_LABEL[risk].toLowerCase()}
              </span>
            </div>
            <p className="text-xs text-text-secondary leading-relaxed">{RISK_REASON[risk]}</p>
          </div>

          {raw === undefined ? (
            <Banner tone="info" className="mb-5">
              O carro não respondeu esta função. Pode ser que este veículo não a tenha, ou que ela
              só apareça em outra condição. Sem valor atual, alterar seria escrever às cegas — por
              isso as opções estão desativadas.
            </Banner>
          ) : boolean ? (
            <>
              <div className="text-[11px] text-text-tertiary mb-2">Opções</div>
              <div className="grid grid-cols-2 gap-2 mb-5">
                {[
                  ['1', 'Ligar'],
                  ['0', 'Desligar'],
                ].map(([value, label]) => (
                  <button
                    key={value}
                    disabled={raw === value}
                    onClick={() => {
                      setPending(value)
                      setAck(false)
                    }}
                    className={`rounded-xl border px-4 py-3 text-sm transition-colors ${
                      raw === value
                        ? 'border-primary/40 bg-primary/15 text-text cursor-default'
                        : 'border-white/[0.1] bg-white/[0.02] text-text-secondary hover:bg-white/[0.06] hover:text-text'
                    }`}
                  >
                    <div className="flex items-center justify-center gap-2">
                      {raw === value && <CheckIcon size={14} />}
                      {raw === value ? `${label.replace('Ligar', 'Ligado').replace('Desligar', 'Desligado')} (atual)` : label}
                    </div>
                  </button>
                ))}
              </div>
              <p className="text-[11px] text-text-tertiary leading-relaxed mb-5">
                Só existem duas opções aqui porque o carro respondeu "0" ou "1" nesta função. O app
                não tem a tabela que diz quais outros valores ela aceitaria, e não vai inventar uma.
              </p>
            </>
          ) : (
            <Banner tone="warning" className="mb-5">
              <div className="font-medium mb-1">Esta função não é liga/desliga.</div>
              <div className="text-xs opacity-90 leading-relaxed">
                O carro respondeu <code className="font-mono">{showValue(raw)}</code>, e o app não
                tem como saber quais valores essa função aceita nem o que cada um significa.
                Mostrar opções aqui seria inventar. Para editar assim mesmo, use a tela de Coding —
                lá o valor é digitado à mão, por sua conta.
              </div>
              <Link
                to="/config-avancadas/coding"
                className="inline-block mt-2 text-xs text-primary hover:text-primary-hover"
              >
                Abrir Coding avançado →
              </Link>
            </Banner>
          )}

          {/* O nome técnico da chave fica aqui embaixo, discreto: quem não precisa dele não
              esbarra nele, e quem precisa não fica sem. */}
          <details className="mb-5">
            <summary className="text-[11px] text-text-tertiary cursor-pointer hover:text-text-secondary">
              Detalhe técnico
            </summary>
            <div className="mt-2 font-mono text-[11px] text-text-tertiary break-all">{selected.key}</div>
            <div className="mt-1 text-[11px] text-text-tertiary leading-relaxed">
              Esta chave existe no dicionário deste carro (levantamento de 2026-09-14). O efeito de
              escrevê-la nunca foi confirmado em um veículo.
            </div>
          </details>

          <BackButton />
        </Card>

        {pending !== null && (
          <Modal
            onClose={() => {
              setPending(null)
              setAck(false)
            }}
            tone={risk === 'alto' ? 'danger' : 'neutral'}
          >
            <h2 className="text-lg font-semibold text-text mb-2 tracking-tight">
              {pending === '1' ? 'Ligar' : 'Desligar'} "{selected.name}"?
            </h2>
            <div className="flex items-center gap-2 text-sm mb-4">
              <span className="text-text-tertiary">{stateLabel(selected.key).text}</span>
              <ChevronRightIcon size={14} className="text-text-tertiary" />
              <span className="text-text font-medium">{pending === '1' ? 'Ligado' : 'Desligado'}</span>
            </div>

            {risk === 'alto' && (
              <Banner tone="error" icon={ShieldAlertIcon} className="mb-4">
                {RISK_REASON.alto}
              </Banner>
            )}
            {risk === 'medio' && (
              <Banner tone="warning" className="mb-4">
                {RISK_REASON.medio}
              </Banner>
            )}
            {source === 'demo' && (
              <Banner tone="info" className="mb-4">
                Modo demonstração: nada será escrito em um veículo.
              </Banner>
            )}

            <p className="text-xs text-text-tertiary leading-relaxed mb-4">
              A alteração entra no histórico com o valor de antes, então dá para desfazer depois.
            </p>

            {risk !== 'baixo' && (
              <label className="flex items-start gap-2 mb-5 text-xs text-text-secondary cursor-pointer">
                <input type="checkbox" checked={ack} onChange={(e) => setAck(e.target.checked)} className="mt-0.5" />
                <span>Entendi o risco e quero gravar esta alteração no carro.</span>
              </label>
            )}

            <div className="flex gap-3">
              <Button
                variant="secondary"
                onClick={() => {
                  setPending(null)
                  setAck(false)
                }}
              >
                Cancelar
              </Button>
              <Button
                variant={risk === 'alto' ? 'destructive' : 'primary'}
                disabled={risk !== 'baixo' && !ack}
                onClick={confirmWrite}
              >
                Gravar no carro
              </Button>
            </div>
          </Modal>
        )}
      </div>
    )
  }

  // ------------------------------------------------------------------ lista
  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-start gap-4 mb-5">
          <SlidersHorizontalIcon size={22} className="text-primary flex-shrink-0 mt-1" />
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Ajustes guiados</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              As funções do carro que dá para ligar e desligar por aqui, explicadas em português.
            </p>
          </div>
        </div>

        {!connected && (
          <Banner tone="warning" className="mb-5">
            O carro ainda não respondeu nenhuma configuração. A lista abaixo mostra o que existe,
            mas sem o valor atual não dá para alterar nada com segurança.
          </Banner>
        )}

        <div className="flex flex-wrap gap-2 mb-5">
          <div className="flex-1 min-w-48">
            <TextInput
              icon={SearchIcon}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar uma função..."
            />
          </div>
          <Button variant="ghost" size="sm" fullWidth={false} onClick={refresh} disabled={status === 'loading'}>
            {status === 'loading' ? 'Lendo...' : 'Reler do carro'}
          </Button>
          <Button as={Link} to="/alteracoes" variant="ghost" size="sm" icon={HistoryIcon} fullWidth={false}>
            Histórico
          </Button>
          <Button as={Link} to="/alteracoes" variant="ghost" size="sm" icon={CameraIcon} fullWidth={false}>
            Pontos de restauração
          </Button>
        </div>

        {!query && !category ? (
          <>
            <div className="text-[11px] text-text-tertiary mb-3">Escolha uma categoria</div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
              {categories.map((c) => (
                <button
                  key={c.name}
                  onClick={() => setCategory(c.name)}
                  className="flex items-center gap-3 rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 text-left hover:bg-white/[0.06] transition-colors"
                >
                  <div className="flex-1 min-w-0">
                    <div className="text-sm text-text truncate">{c.name}</div>
                    <div className="text-[11px] text-text-tertiary">
                      {c.total} função(ões) · {c.answered} com valor lido
                    </div>
                  </div>
                  <ChevronRightIcon size={15} className="text-text-tertiary flex-shrink-0" />
                </button>
              ))}
            </div>
            <p className="text-[11px] text-text-tertiary leading-relaxed mt-4">
              São {CODING_CATALOG.length} funções escolhidas a dedo entre as 818 chaves que este
              carro expõe — as que dão para explicar sem jargão. O resto continua acessível na tela
              de Coding, no nível avançado.
            </p>
          </>
        ) : (
          <>
            <div className="flex items-center gap-2 mb-3">
              {category && !query && (
                <button
                  onClick={() => setCategory(null)}
                  className="flex items-center gap-1.5 text-xs text-text-tertiary hover:text-text transition-colors"
                >
                  <ArrowLeftIcon size={13} />
                  Todas as categorias
                </button>
              )}
              <div className="flex-1" />
              <span className="text-[11px] text-text-tertiary">{listed.length} função(ões)</span>
            </div>

            {listed.length === 0 ? (
              <p className="p-6 text-text-tertiary text-sm text-center">Nenhuma função encontrada.</p>
            ) : (
              <ListPanel maxHeight="max-h-[30rem]">
                {listed.map((e) => {
                  const st = stateLabel(e.key)
                  const risk = riskOf(e.key)
                  return (
                    <ListRow key={e.key}>
                      <button
                        onClick={() => {
                          setSelected(e)
                          setPending(null)
                          setAck(false)
                        }}
                        className="flex items-center gap-3 flex-1 min-w-0 text-left"
                      >
                        <div className="flex-1 min-w-0">
                          <div className="text-sm text-text truncate">{e.name}</div>
                          <div className="text-[11px] text-text-tertiary truncate">
                            {query ? e.category : e.description}
                          </div>
                        </div>
                        {risk !== 'baixo' && (
                          <span className={`px-1.5 py-0.5 rounded text-[10px] uppercase flex-shrink-0 ${RISK_CLASSES[risk]}`}>
                            {RISK_LABEL[risk]}
                          </span>
                        )}
                        <span className={`text-xs w-44 text-right truncate flex-shrink-0 ${st.cls}`}>{st.text}</span>
                        <ChevronRightIcon size={15} className="text-text-tertiary flex-shrink-0" />
                      </button>
                    </ListRow>
                  )
                })}
              </ListPanel>
            )}
          </>
        )}

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
