import { useMemo, useState } from 'react'
import { CpuIcon, SearchIcon } from 'lucide-react'
import { BackButton, Banner, Card, ConfidenceChip, ListPanel, TextInput } from '../../ui'
import { VHAL, VHAL_LIVE, VHAL_LIVE_BY_ID } from '../../data/knowledge'
import { facetsOf, filterVhal, page, type VhalScope } from '../../expert/catalog'

// Explorador do catálogo VHAL.
//
// TRÊS AFIRMAÇÕES POR LINHA, COM TRÊS CONFIANÇAS DIFERENTES, e é por isso que esta tela existe em
// vez de uma tabela simples:
//   - que a propriedade EXISTE, com aquele id, tipo e acesso — FATO, veio do dump do carro;
//   - o VALOR ao lado — OBSERVADO em 2026-09-14, naquele carro, naquele estado. Não é leitura de
//     agora, e a tela diz isso em toda parte onde o valor aparece;
//   - a DESCRIÇÃO — DEDUZIDA do nome do sinal. Ninguém confirmou nenhuma delas no veículo.
//
// A tentação aqui seria mostrar o valor sem data, como se fosse ao vivo. Ler propriedade VHAL ao
// vivo exige uma ponte para o CarPropertyManager que este app não tem; fingir que tem seria
// transformar um instantâneo de um ano em telemetria.
const SCOPES: Array<{ id: VhalScope; label: string }> = [
  { id: 'todas', label: 'Todas' },
  { id: 'nomeadas', label: 'Com nome' },
  { id: 'descritas', label: 'Com descrição' },
  { id: 'com-valor', label: 'Com valor capturado' },
]

export const ExpertVhalExplorer = () => {
  const [busca, setBusca] = useState('')
  const [scope, setScope] = useState<VhalScope>('todas')
  const [access, setAccess] = useState('')
  const [ecuPrefix, setEcuPrefix] = useState('')
  const [aberta, setAberta] = useState<string | null>(null)

  const prefixos = useMemo(() => facetsOf(VHAL.properties, (p) => p.ecuPrefix), [])
  const acessos = useMemo(() => facetsOf(VHAL.properties, (p) => p.access), [])

  const filtradas = useMemo(
    () => filterVhal(VHAL.properties, { query: busca, scope, access, ecuPrefix }),
    [busca, scope, access, ecuPrefix],
  )
  const { shown, total, truncated } = useMemo(() => page(filtradas), [filtradas])

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="xl">
        <div className="flex items-start gap-4 mb-5">
          <div className="flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-xl font-semibold text-text tracking-tight">Explorador VHAL</h1>
              <ConfidenceChip level="FACT" />
            </div>
            <p className="text-text-secondary mt-1 text-sm leading-relaxed">
              As {VHAL.meta.count} propriedades da camada de abstração do veículo: {VHAL.meta.named}{' '}
              com nome legível, {VHAL.meta.described} com descrição deduzida e{' '}
              {VHAL_LIVE.meta.count} que responderam com valor na leitura de{' '}
              {VHAL_LIVE.meta.capturedAt}.
            </p>
          </div>
          <CpuIcon size={22} className="text-text-tertiary mt-1" />
        </div>

        <Banner tone="warning" className="mb-4">
          <div className="font-medium mb-0.5">Os valores são de capturas, não de agora</div>
          <div className="text-xs opacity-85 leading-relaxed">
            Este app não lê propriedade VHAL sob demanda — isso exige uma ponte para o
            CarPropertyManager que ele não tem. Cada valor abaixo diz de qual leitura veio:{' '}
            {VHAL_LIVE.meta.capturedAt} (com o carro acordado, {VHAL_LIVE.meta.count} sinais) ou{' '}
            {VHAL.meta.capturedAt} ({VHAL.meta.observedValues} sinais). Trate como amostra
            histórica, nunca como telemetria.
          </div>
        </Banner>

        <div className="flex flex-col sm:flex-row gap-2 mb-2">
          <TextInput
            icon={SearchIcon}
            placeholder="Buscar por nome, id (0x…), descrição ou prefixo…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            containerClassName="flex-1"
          />
          <select
            value={access}
            onChange={(e) => setAccess(e.target.value)}
            className="rounded-xl border border-white/[0.12] bg-black/25 px-3 py-2.5 text-xs text-text outline-none"
          >
            <option value="">Qualquer acesso</option>
            {acessos.map((a) => (
              <option key={a.value} value={a.value}>
                {a.value} ({a.count})
              </option>
            ))}
          </select>
          <select
            value={ecuPrefix}
            onChange={(e) => setEcuPrefix(e.target.value)}
            className="rounded-xl border border-white/[0.12] bg-black/25 px-3 py-2.5 text-xs text-text outline-none"
          >
            <option value="">Qualquer prefixo</option>
            {prefixos.map((p) => (
              <option key={p.value} value={p.value}>
                {p.value} ({p.count})
              </option>
            ))}
          </select>
        </div>

        <div className="flex flex-wrap gap-1.5 mb-3">
          {SCOPES.map((s) => (
            <button
              key={s.id}
              onClick={() => setScope(s.id)}
              className={`rounded-full px-3 py-1 text-[11px] transition-colors ${
                scope === s.id
                  ? 'bg-primary/20 text-primary'
                  : 'bg-white/[0.04] text-text-tertiary hover:bg-white/[0.08]'
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>

        <div className="text-[11px] text-text-tertiary mb-2">
          {total} {total === 1 ? 'propriedade' : 'propriedades'} no filtro
          {truncated && ` · mostrando as ${shown.length} primeiras`}
        </div>

        <ListPanel maxHeight="max-h-[30rem]">
          {shown.length === 0 ? (
            <div className="px-4 py-8 text-center text-xs text-text-tertiary">
              Nenhuma propriedade do catálogo corresponde ao filtro.
            </div>
          ) : (
            shown.map((p) => {
              const expandida = aberta === p.id
              // O valor da leitura mais recente, quando este sinal respondeu nela.
              const vivo = VHAL_LIVE_BY_ID.get(p.id)
              return (
                <div key={p.id} className="border-b border-white/5 last:border-b-0">
                  <button
                    onClick={() => setAberta(expandida ? null : p.id)}
                    className="w-full flex items-center gap-3 px-4 py-2.5 text-left hover:bg-white/[0.04] transition-colors"
                  >
                    <div className="flex-1 min-w-0">
                      <div className="text-[11px] text-text truncate">
                        {p.name ?? <span className="italic text-text-tertiary">sem nome no dump</span>}
                      </div>
                      <div className="font-mono text-[10px] text-text-tertiary truncate">{p.id}</div>
                    </div>
                    {p.access && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/[0.06] text-text-tertiary flex-shrink-0">
                        {p.access}
                      </span>
                    )}
                    {vivo ?? p.liveValue ? (
                      <span
                        title={
                          vivo
                            ? `Leitura de ${VHAL_LIVE.meta.capturedAt} · ${vivo.eventCount} evento(s)`
                            : `Capturado em ${VHAL.meta.capturedAt}`
                        }
                        className={`font-mono text-[11px] tabular-nums flex-shrink-0 max-w-[7rem] truncate text-right ${
                          vivo ? 'text-primary' : 'text-text'
                        }`}
                      >
                        {vivo?.value ?? p.liveValue}
                      </span>
                    ) : (
                      <span className="text-[10px] text-text-tertiary italic flex-shrink-0">
                        sem valor
                      </span>
                    )}
                  </button>

                  {expandida && (
                    <div className="px-4 pb-3 space-y-2 bg-black/20">
                      <Campo rotulo="Id" valor={p.id} confidence="FACT" />
                      <Campo rotulo="Tipo" valor={p.type} confidence="FACT" />
                      <Campo rotulo="Acesso" valor={p.access} confidence="FACT" />
                      <Campo rotulo="Prefixo do sinal" valor={p.ecuPrefix} confidence="INFERRED" />
                      <Campo
                        rotulo={`Valor em ${VHAL.meta.capturedAt}`}
                        valor={p.liveValue}
                        confidence="OBSERVED"
                      />
                      <Campo
                        rotulo={`Valor em ${VHAL_LIVE.meta.capturedAt}`}
                        valor={vivo?.value ?? null}
                        confidence="OBSERVED"
                      />
                      {/* A contagem de eventos é o que separa um sinal contínuo de um disparo
                          único no power-on — e portanto o que dá para plotar do que não dá. */}
                      <Campo
                        rotulo="Eventos emitidos nessa leitura"
                        valor={vivo ? `${vivo.eventCount}` : null}
                        confidence="FACT"
                      />
                      <Campo rotulo="Descrição" valor={p.description} confidence="INFERRED" />
                    </div>
                  )}
                </div>
              )
            })
          )}
        </ListPanel>

        <p className="text-[11px] text-text-tertiary mt-3 leading-relaxed">
          O prefixo do sinal (<code className="font-mono">GW_FD1_</code>,{' '}
          <code className="font-mono">ESP_FD2_</code>…) costuma nomear o frame ou o módulo de origem,
          e é dedução a partir do nome — não é um inventário de ECUs. O inventário de verdade está em
          branco em <span className="font-mono">/pro/ecus</span>, e continua em branco.
        </p>

        <div className="mt-6">
          <BackButton />
        </div>
      </Card>
    </div>
  )
}

function Campo({
  rotulo,
  valor,
  confidence,
}: {
  rotulo: string
  valor: string | null
  confidence: 'FACT' | 'OBSERVED' | 'INFERRED'
}) {
  return (
    <div className="flex items-start gap-2 text-[11px]">
      <span className="text-text-tertiary w-32 flex-shrink-0">{rotulo}</span>
      <span className="flex-1 min-w-0 font-mono text-text break-all">
        {valor ?? <span className="italic font-sans text-text-tertiary">não consta no dump</span>}
      </span>
      {valor !== null && <ConfidenceChip level={confidence} />}
    </div>
  )
}
