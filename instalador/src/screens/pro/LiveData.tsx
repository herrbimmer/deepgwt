// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useMemo, useRef, useState } from 'react'
import { ActivityIcon, SearchIcon } from 'lucide-react'
import { BackButton, Banner, Card, ListPanel, ListRow, TextInput } from '../../ui'
import { useCarSettings } from '../../state/VehicleSession'
import { CONTROL_SERVICE_BY_KEY } from '../../data/knowledge'
import { labelOf, namespaceOf, showValue } from '../../coding/keyModel'

// Dados em Tempo Real.
//
// O que a tela mostra é o snapshot do ControlService a cada 4 s, cru. Sem conversão de unidade,
// sem escala, sem faixa esperada, sem cor de "normal" — o app não tem nada disso para nenhuma
// chave, e pintar um valor de verde seria inventar um critério.
//
// O que ela ACRESCENTA, e é o que a torna útil numa oficina: memória. Ela guarda o valor de cada
// chave no instante em que a tela abriu e marca o que mudou desde então. Ver "o que se mexeu
// quando eu pisei no freio" é uma pergunta que se responde sem saber o que a chave significa.
export const ProLiveData = () => {
  const session = useCarSettings()
  const [busca, setBusca] = useState('')
  const [grupo, setGrupo] = useState('todos')
  const [soMudanca, setSoMudanca] = useState(false)

  // Linha de base do momento em que a tela abriu. Só é fixada quando o primeiro snapshot com
  // chaves chega — antes disso não há nada contra o que comparar.
  const baselineRef = useRef<Record<string, string> | null>(null)
  const [baselineAt, setBaselineAt] = useState<number | null>(null)
  useEffect(() => {
    if (baselineRef.current || Object.keys(session.values).length === 0) return
    baselineRef.current = { ...session.values }
    setBaselineAt(Date.now())
  }, [session.values])

  const rearmar = () => {
    baselineRef.current = { ...session.values }
    setBaselineAt(Date.now())
  }

  const base = baselineRef.current ?? {}

  const grupos = useMemo(
    () => [...new Set(Object.keys(session.values).map(namespaceOf))].sort(),
    [session.values],
  )

  const linhas = useMemo(() => {
    const termo = busca.trim().toLowerCase()
    return Object.keys(session.values)
      .filter((k) => (grupo === 'todos' ? true : namespaceOf(k) === grupo))
      .filter((k) => (termo ? k.toLowerCase().includes(termo) || labelOf(k).toLowerCase().includes(termo) : true))
      .map((k) => {
        const now = session.values[k]
        const was = base[k]
        return { key: k, now, was, mudou: was !== undefined && was !== now, nova: was === undefined }
      })
      .filter((l) => (soMudanca ? l.mudou || l.nova : true))
      .sort((a, b) => a.key.localeCompare(b.key))
  }, [session.values, base, busca, grupo, soMudanca])

  const mudadas = useMemo(
    () => Object.keys(session.values).filter((k) => base[k] !== undefined && base[k] !== session.values[k]).length,
    [session.values, base],
  )

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <h1 className="text-xl font-semibold text-text tracking-tight">Dados em Tempo Real</h1>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              Tudo que o carro responde, atualizado a cada 4 segundos. Valores crus, como vieram.
            </p>
          </div>
          <ActivityIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <Banner tone="info" className="mb-4">
          <div className="text-xs leading-relaxed">
            Nenhum valor abaixo é convertido ou interpretado. O app não conhece a unidade, a escala
            nem a faixa válida de nenhuma dessas chaves — mostrar o número que o carro mandou é
            tudo que ele pode afirmar.
          </div>
        </Banner>

        <div className="flex flex-col sm:flex-row gap-2 mb-3">
          <TextInput
            icon={SearchIcon}
            placeholder="Buscar por chave ou nome…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            containerClassName="flex-1"
          />
          <select
            value={grupo}
            onChange={(e) => setGrupo(e.target.value)}
            className="rounded-xl border border-white/[0.12] bg-black/25 px-3 py-2.5 text-sm text-text outline-none focus:outline-2 focus:outline-primary/60"
          >
            <option value="todos">Todos os grupos ({grupos.length})</option>
            {grupos.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mb-3 text-[11px]">
          <label className="flex items-center gap-1.5 text-text-secondary cursor-pointer">
            <input
              type="checkbox"
              checked={soMudanca}
              onChange={(e) => setSoMudanca(e.target.checked)}
              className="accent-primary"
            />
            Só o que mudou desde que abri ({mudadas})
          </label>
          <button
            type="button"
            onClick={rearmar}
            className="text-text-tertiary hover:text-text transition-colors underline underline-offset-2"
          >
            Marcar este instante como novo ponto de comparação
          </button>
          {baselineAt && (
            <span className="text-text-tertiary">
              comparando com {new Date(baselineAt).toLocaleTimeString('pt-BR')}
            </span>
          )}
        </div>

        <ListPanel maxHeight="max-h-[26rem]">
          {linhas.length === 0 ? (
            <div className="px-4 py-8 text-center text-xs text-text-tertiary">
              {Object.keys(session.values).length === 0
                ? 'O carro ainda não respondeu nenhuma chave nesta sessão.'
                : 'Nenhuma chave corresponde ao filtro.'}
            </div>
          ) : (
            linhas.map((l) => (
              <ListRow key={l.key} className={l.mudou ? 'bg-primary/[0.06]' : ''}>
                <div className="flex-1 min-w-0">
                  <div className="font-mono text-[11px] text-text-secondary truncate">{l.key}</div>
                  {CONTROL_SERVICE_BY_KEY.has(l.key) ? null : (
                    <div className="text-[10px] text-warning">fora do levantamento — a validar</div>
                  )}
                </div>
                {l.mudou && (
                  <span className="text-[10px] text-text-tertiary font-mono flex-shrink-0">
                    {showValue(l.was)} →
                  </span>
                )}
                <span
                  className={`font-mono text-xs tabular-nums flex-shrink-0 max-w-[10rem] truncate text-right ${
                    l.mudou ? 'text-primary' : 'text-text'
                  }`}
                  title={l.now}
                >
                  {showValue(l.now)}
                </span>
              </ListRow>
            ))
          )}
        </ListPanel>

        <p className="text-[11px] text-text-tertiary mt-2">
          {linhas.length} de {Object.keys(session.values).length} chaves ·{' '}
          {session.source === 'demo' ? 'dados sintéticos' : session.connection === 'online' ? 'leitura ao vivo' : 'sem leitura ativa'}
        </p>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
