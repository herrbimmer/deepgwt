import { useDeferredValue, useMemo, useState } from 'react'
import { SearchIcon } from 'lucide-react'
import { ConfidenceChip, TextInput } from '../ui'
import { CONTROL_SERVICE, VHAL } from '../data/knowledge'
import type { Confidence } from '../data/knowledgeTypes'

/**
 * Todas as chaves que este veículo expõe, num lugar só.
 *
 * POR QUE ESTA TELA EXISTE
 *
 * "Informações do Veículo" mostrava 44 chaves — as de identidade — e parava ali. O resto do que
 * foi levantado deste carro (818 chaves do ControlService e 1602 propriedades do VHAL) só era
 * alcançável pelas telas de Engenharia, uma a uma, e quem abria esta tela para "ver o que o carro
 * tem" via 44 linhas e concluía que era isso que existia.
 *
 * DUAS CAMADAS, QUE NÃO SÃO A MESMA COISA
 *
 *   ControlService — o dicionário de chaves `car.*`/`sys.*` que a multimídia usa para falar com o
 *   veículo. É o que a aba Coding escreve. Tem valor ao vivo quando há carro conectado.
 *
 *   VHAL — as propriedades do Vehicle HAL do Android, lidas por id. Camada mais baixa: muita
 *   coisa aqui não tem correspondente em `car.*`. O valor mostrado é o que foi LIDO na captura de
 *   2026-09-14 (`liveValue`), não uma leitura de agora — está rotulado como tal.
 *
 * O QUE ESTA TELA NÃO FAZ: traduzir valores. Um `3` continua `3`. A tabela que diria o que `3`
 * significa não está no carro nem no projeto, e preenchê-la por conta seria inventar.
 */

const PAGE = 120

type Layer = 'controlservice' | 'vhal'

type Row = {
  id: string
  /** Nome completo da variável, como o carro a chama. */
  name: string
  /** Segunda linha: namespace/ECU, tipo, acesso — o que ajuda a situar a chave. */
  meta: string
  value: string | null
  /** De onde veio `value`, em uma palavra. */
  valueSource: string
  semantics: Confidence
}

export function AllVehicleKeys({
  read,
  connected,
}: {
  /** Valor ao vivo de uma chave do ControlService. `null` = o carro não respondeu. */
  read: (key: string) => string | null
  connected: boolean
}) {
  const [layer, setLayer] = useState<Layer>('controlservice')
  const [query, setQuery] = useState('')
  const [shown, setShown] = useState(PAGE)
  // 2420 linhas filtradas a cada tecla travava o campo de busca na central e no notebook. O valor
  // adiado deixa a digitação responder na hora e a lista alcançar depois.
  const deferredQuery = useDeferredValue(query)

  const rows = useMemo<Row[]>(() => {
    if (layer === 'controlservice') {
      return CONTROL_SERVICE.keys.map((k) => ({
        id: k.key,
        name: k.key,
        meta: [
          k.namespace,
          k.appState === 'usada' ? 'exposta pela interface do carro' : null,
          k.appState === 'oculta' ? 'nunca exposta pela interface do carro' : null,
        ]
          .filter(Boolean)
          .join(' · '),
        value: read(k.key),
        valueSource: connected ? 'lido do carro agora' : 'sem carro conectado',
        semantics: k.semantics,
      }))
    }
    return VHAL.properties.map((p) => ({
      id: p.id,
      name: p.name ?? p.id,
      meta: [p.name ? p.id : null, p.type, p.access, p.ecuPrefix, p.description]
        .filter(Boolean)
        .join(' · '),
      value: p.liveValue,
      valueSource: `lido em ${VHAL.meta.capturedAt}`,
      semantics: p.semantics,
    }))
  }, [layer, read, connected])

  const filtered = useMemo(() => {
    const q = deferredQuery.trim().toLowerCase()
    if (!q) return rows
    return rows.filter(
      (r) =>
        r.name.toLowerCase().includes(q) ||
        r.meta.toLowerCase().includes(q) ||
        (r.value ?? '').toLowerCase().includes(q),
    )
  }, [rows, deferredQuery])

  const withValue = useMemo(() => filtered.filter((r) => r.value !== null).length, [filtered])
  const visible = filtered.slice(0, shown)

  const tab = (id: Layer, label: string, count: number) => (
    <button
      key={id}
      onClick={() => {
        setLayer(id)
        setShown(PAGE)
      }}
      className={`px-3 py-1.5 rounded-full text-xs font-medium transition-colors ${
        layer === id ? 'bg-primary/20 text-text' : 'text-text-tertiary hover:bg-white/5'
      }`}
    >
      {label} ({count})
    </button>
  )

  return (
    <section>
      <div className="flex items-baseline justify-between mb-1 gap-3 flex-wrap">
        <h2 className="text-sm font-semibold text-text">Todas as chaves deste veículo</h2>
        <span className="text-[11px] text-text-tertiary">
          {CONTROL_SERVICE.meta.count + VHAL.meta.count} no total
        </span>
      </div>
      <p className="text-[11px] text-text-tertiary mb-3 leading-relaxed">
        O levantamento completo do veículo, não só as chaves de identidade. Os nomes e a existência
        vieram de leitura direta deste carro; o significado dos valores não foi validado, então o
        valor aparece exatamente como o carro o devolveu.
      </p>

      <div className="flex gap-2 mb-3 flex-wrap">
        {tab('controlservice', 'ControlService', CONTROL_SERVICE.meta.count)}
        {tab('vhal', 'VHAL', VHAL.meta.count)}
      </div>

      <TextInput
        icon={SearchIcon}
        value={query}
        onChange={(e) => {
          setQuery(e.target.value)
          setShown(PAGE)
        }}
        placeholder={
          layer === 'controlservice'
            ? 'Buscar chave, namespace ou valor...'
            : 'Buscar nome, id, ECU ou valor...'
        }
      />

      <p className="text-[11px] text-text-tertiary my-2">
        {filtered.length} chave(s) · {withValue} com valor (
        {layer === 'controlservice'
          ? connected
            ? 'leitura desta sessão'
            : 'nenhuma: sem carro conectado'
          : `captura de ${VHAL.meta.capturedAt}`}
        )
      </p>

      <div className="rounded-xl border border-white/[0.08] overflow-hidden">
        {visible.length === 0 ? (
          <p className="px-4 py-6 text-text-tertiary text-sm">Nenhuma chave encontrada.</p>
        ) : (
          visible.map((r) => (
            <div
              key={r.id}
              className="flex items-baseline gap-3 px-3 py-2 border-b border-white/5 last:border-b-0 hover:bg-white/[0.03]"
            >
              <span className="text-xs text-text-secondary w-56 sm:w-80 flex-shrink-0 min-w-0">
                {/* Nome COMPLETO, sem truncar: é o que o usuário precisa para procurar a chave
                    na aba Coding ou digitá-la num console. */}
                <span className="block font-mono break-all text-text">{r.name}</span>
                <span className="block text-[10px] text-text-tertiary break-all">{r.meta}</span>
              </span>
              <span
                className={`flex-1 text-sm font-mono break-all ${
                  r.value === null ? 'text-text-tertiary italic font-sans' : 'text-text'
                }`}
              >
                {r.value ?? 'sem valor'}
                {r.value !== null && (
                  <span className="block text-[10px] text-text-tertiary font-sans not-italic">
                    {r.valueSource}
                  </span>
                )}
              </span>
              <ConfidenceChip level={r.semantics} />
            </div>
          ))
        )}
      </div>

      {filtered.length > shown && (
        <button
          onClick={() => setShown((n) => n + PAGE * 4)}
          className="mt-3 text-xs text-primary hover:text-primary-hover"
        >
          Mostrar mais ({filtered.length - shown} restantes)
        </button>
      )}
    </section>
  )
}
