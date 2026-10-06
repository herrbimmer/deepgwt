// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import { KeyRoundIcon, SearchIcon } from 'lucide-react'
import { BackButton, Banner, Card, ConfidenceChip, ListPanel, ListRow, TextInput } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import { CONTROL_SERVICE } from '../../data/knowledge'
import { facetsOf, filterControlService, page } from '../../expert/catalog'
import { descriptionOf, isCurated, labelOf, showValue } from '../../coding/keyModel'

// Explorador do dicionário do ControlService.
//
// DUAS COLUNAS, DUAS COISAS DIFERENTES, e a tela existe em boa parte para não deixar ninguém
// confundir as duas. A esquerda é CATÁLOGO: a chave existe, isso é fato do levantamento de
// 2026-09-14 e não depende de haver carro nenhum ligado agora. A direita é LEITURA: o que este
// carro devolveu nesta sessão, que vale para este carro e para este momento.
//
// O que a tela não tem, e por isso não mostra: a tabela de valores aceitos de nenhuma chave. Ela
// não sabe que `2` é "médio" em lugar nenhum. Mostra `2`.
export const ExpertControlServiceExplorer = () => {
  const session = useCarSettings()
  const [busca, setBusca] = useState('')
  const [namespace, setNamespace] = useState('')
  const [appState, setAppState] = useState('')

  const namespaces = useMemo(() => facetsOf(CONTROL_SERVICE.keys, (k) => k.namespace), [])

  const filtradas = useMemo(
    () => filterControlService(CONTROL_SERVICE.keys, { query: busca, namespace, appState }),
    [busca, namespace, appState],
  )
  const { shown, total, truncated } = useMemo(() => page(filtradas), [filtradas])

  const respondidas = useMemo(
    () => filtradas.filter((k) => k.key in session.values).length,
    [filtradas, session.values],
  )

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-text tracking-tight">
                Explorador do ControlService
              </h1>
              <ConfidenceChip level="FACT" />
            </div>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              As {CONTROL_SERVICE.meta.count} chaves do dicionário levantado deste modelo em{' '}
              {CONTROL_SERVICE.meta.capturedAt}, em {namespaces.length} namespaces. Navegar não exige
              carro; a coluna de valor só aparece quando há sessão.
            </p>
          </div>
          <KeyRoundIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <Banner tone="info" className="mb-4">
          <div className="text-xs leading-relaxed">
            <strong className="font-medium">O selo Fato vale para a existência da chave</strong>, não
            para o que ela faz. O nome sugere; ninguém verificou. E o valor à direita, quando existe,
            é leitura desta sessão — some assim que o carro sair de alcance.
          </div>
        </Banner>

        <div className="flex flex-col sm:flex-row gap-2 mb-3">
          <TextInput
            icon={SearchIcon}
            placeholder="Buscar chave ou nome do enum…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            containerClassName="flex-1"
          />
          <select
            value={namespace}
            onChange={(e) => setNamespace(e.target.value)}
            className="rounded-xl border border-white/[0.12] bg-black/25 px-3 py-2.5 text-xs text-text outline-none"
          >
            <option value="">Todos os namespaces</option>
            {namespaces.map((n) => (
              <option key={n.value} value={n.value}>
                {n.value} ({n.count})
              </option>
            ))}
          </select>
          <select
            value={appState}
            onChange={(e) => setAppState(e.target.value)}
            className="rounded-xl border border-white/[0.12] bg-black/25 px-3 py-2.5 text-xs text-text outline-none"
          >
            <option value="">Exposta ou não</option>
            <option value="usada">Já exposta pela tela do carro</option>
            <option value="oculta">Nunca exposta</option>
          </select>
        </div>

        <div className="text-[11px] text-text-tertiary mb-2">
          {total} {total === 1 ? 'chave' : 'chaves'} no filtro · {respondidas}{' '}
          {respondidas === 1 ? 'respondida' : 'respondidas'} nesta sessão
          {truncated && ` · mostrando as ${shown.length} primeiras`}
        </div>

        <ListPanel maxHeight="max-h-[30rem]">
          {shown.length === 0 ? (
            <div className="px-4 py-8 text-center text-xs text-text-tertiary">
              Nenhuma chave do levantamento corresponde ao filtro. Isso não diz nada sobre o carro —
              diz que o levantamento não tem essa chave.
            </div>
          ) : (
            shown.map((k) => {
              const respondida = k.key in session.values
              const raw = session.values[k.key]
              return (
                <ListRow key={k.key}>
                  <div className="flex-1 min-w-0">
                    <div className="font-mono text-[11px] text-text truncate">{k.key}</div>
                    <div className="text-[10px] text-text-tertiary truncate">
                      {isCurated(k.key) ? descriptionOf(k.key) ?? labelOf(k.key) : k.enumName}
                    </div>
                  </div>
                  {k.appState && (
                    <span
                      className={`text-[10px] px-1.5 py-0.5 rounded flex-shrink-0 ${
                        k.appState === 'oculta'
                          ? 'bg-warning/15 text-warning'
                          : 'bg-white/[0.06] text-text-tertiary'
                      }`}
                    >
                      {k.appState}
                    </span>
                  )}
                  {respondida ? (
                    <span
                      title="Lido nesta sessão"
                      className="font-mono text-xs text-primary tabular-nums flex-shrink-0 max-w-[8rem] truncate text-right"
                    >
                      {showValue(raw === '' ? null : raw)}
                    </span>
                  ) : (
                    <span className="text-[10px] text-text-tertiary italic flex-shrink-0">
                      sem leitura
                    </span>
                  )}
                </ListRow>
              )
            })
          )}
        </ListPanel>

        <p className="text-[11px] text-text-tertiary mt-3 leading-relaxed">
          Origem: {CONTROL_SERVICE.meta.method} — {CONTROL_SERVICE.meta.vehicle}. A marcação{' '}
          <em>usada</em> / <em>oculta</em> veio da mesma curadoria e diz se a tela de fábrica do
          carro expõe a chave, não se ela é segura de escrever.
        </p>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
