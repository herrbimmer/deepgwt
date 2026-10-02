// De onde o elemento tira o valor, e como o texto cru do carro vira número, ângulo ou palavra.
//
// O seletor é dividido em quatro seções porque as chaves do contrato NÃO se comportam todas
// igual, e esconder isso já custou um editor inteiro de mostradores mortos:
//
//   Ao vivo               — chega por `onDataChanged`, com valor inicial no `subscribe`.
//   Chega por evento      — as seis de projeção só existem via `window.control`: sem valor
//                           inicial, o mostrador fica no texto de reserva até o carro mandar.
//   Dupla                 — `warningActive`/`warningDismissed` chegam como texto no empurrão
//                           inicial e como booleano de verdade depois.
//   Só escrita            — listadas e desabilitadas, com o motivo à vista, para ninguém
//                           reaproveitá-las achando que dá para exibir.

import { useMemo, useState } from 'react'
import { CheckIcon, PinIcon, SearchIcon, XIcon } from 'lucide-react'
import { Banner, Segmented } from '../../ui'
import { ConfidenceChip } from '../../ui'
import type { Binding, ValueMap } from '../model/spec'
import { DATA_SOURCES, GROUP_ORDER, SOURCE_BY_KEY } from '../model/dataSources'
import type { SourceGroup } from '../model/dataSources'
import { mapForKey } from '../model/defaults'
import { Grupo, Linha, NumberField, Switch, TextField } from './shared'

const NOTA_DO_CANAL: Record<string, string> = {
  subscribe: 'Ao vivo, com valor inicial.',
  control: 'Chega por evento: sem valor inicial, só depois que o carro mandar.',
  both: 'Texto no primeiro empurrão, booleano depois. O motor trata os dois.',
  writeOnly: 'Só escrita: o carro nunca devolve o valor. Não dá para exibir.',
}

/** Busca sem acento e sem caixa: "autonomia eletrica" acha "Autonomia elétrica". */
const normalizar = (s: string) => s.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()

export type DataPanelProps = {
  binding: Binding | null
  map: ValueMap
  /** `true` quando o elemento não lê nada do carro — o relógio. */
  semLeitura?: boolean
  /** Esconde a escala: num mostrador redondo ela é editada na aba Estilo. */
  semEscala?: boolean
  /** `majors` só vem quando a chave nova sugere marcações — o mostrador usa, os outros ignoram. */
  onChange: (patch: { binding?: Binding; map?: ValueMap; majors?: number[] }) => void
  /** Valores fixados à mão para a prévia, por chave. Não entram no tema. */
  fixados?: Record<string, string>
  onFixar?: (chave: string, valor: string | null) => void
}

export function DataPanel({ binding, map, semLeitura, semEscala, onChange, fixados = {}, onFixar }: DataPanelProps) {
  const [busca, setBusca] = useState('')
  const [grupo, setGrupo] = useState<SourceGroup | 'todos'>('todos')
  const [trocando, setTrocando] = useState(false)
  const fonte = binding ? SOURCE_BY_KEY.get(binding.key) : undefined
  const hint = fonte?.hint

  const achados = useMemo(() => {
    const termo = normalizar(busca.trim())
    return DATA_SOURCES.filter((s) => {
      if (grupo !== 'todos' && s.hint.group !== grupo) return false
      if (!termo) return true
      return normalizar(s.hint.label).includes(termo) || s.key.toLowerCase().includes(termo) || normalizar(s.hint.group).includes(termo)
    })
  }, [busca, grupo])

  if (semLeitura) {
    return (
      <Grupo titulo="Dados" dica="O relógio usa a hora do próprio painel; não lê nada do carro.">
        <Banner tone="info">Nada para configurar aqui.</Banner>
      </Grupo>
    )
  }

  // Escolher uma chave aplica o mapeamento sugerido de uma vez: faixa, unidade e, num mostrador,
  // as marcações. É o que deixa trocar "velocidade" por "rotação" sem reescrever a escala à mão.
  const trocarChave = (key: string) => {
    const sugestao = mapForKey(key)
    onChange({
      binding: { ...sugestao.binding, fallback: binding?.fallback ?? '--' },
      map: sugestao.map,
      majors: sugestao.majors.length ? sugestao.majors : undefined,
    })
    setTrocando(false)
    setBusca('')
  }

  const patchBinding = (p: Partial<Binding>) => onChange({ binding: { ...(binding ?? { key: '' }), ...p } })
  const fixado = binding ? fixados[binding.key] : undefined

  return (
    <div className="space-y-5">
      <Grupo titulo="Fonte de dado" dica="Só aparecem aqui as chaves que o carro realmente entrega a um tema.">
        {!trocando && hint ? (
          <button
            type="button"
            onClick={() => setTrocando(true)}
            className="w-full text-left rounded-xl border border-white/10 bg-black/20 px-3 py-2.5 hover:border-white/25"
          >
            <div className="text-sm text-text font-medium">{hint.label}</div>
            <div className="text-[10px] text-text-tertiary">{hint.group} · toque para trocar</div>
          </button>
        ) : (
          <div className="space-y-2">
            <div className="relative">
              <SearchIcon size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-text-tertiary" />
              <input
                autoFocus={trocando}
                value={busca}
                onChange={(e) => setBusca(e.target.value)}
                placeholder="Buscar: velocidade, bateria, música…"
                className="w-full bg-black/25 border border-white/10 rounded-lg pl-7 pr-2.5 py-2 text-sm text-text outline-none focus:border-primary/60"
              />
            </div>
            <div className="flex flex-wrap gap-1">
              {(['todos', ...GROUP_ORDER] as const).map((g) => (
                <button
                  key={g}
                  type="button"
                  onClick={() => setGrupo(g)}
                  className={`px-2 py-0.5 rounded-full text-[10px] border ${
                    grupo === g ? 'border-primary bg-primary/20 text-text' : 'border-white/10 text-text-tertiary hover:text-text'
                  }`}
                >
                  {g === 'todos' ? 'Todos' : g}
                </button>
              ))}
            </div>
            <div className="max-h-64 overflow-y-auto rounded-xl border border-white/10 divide-y divide-white/5">
              {achados.length === 0 && <p className="text-xs text-text-tertiary p-3">Nada com esse nome.</p>}
              {achados.map((s) => {
                const escrita = s.channel === 'writeOnly'
                const atual = s.key === binding?.key
                return (
                  <button
                    key={s.key}
                    type="button"
                    disabled={escrita}
                    onClick={() => trocarChave(s.key)}
                    title={NOTA_DO_CANAL[s.channel]}
                    className={`w-full text-left px-3 py-2 flex items-start gap-2 disabled:opacity-40 ${atual ? 'bg-primary/15' : 'hover:bg-white/[0.05]'}`}
                  >
                    <div className="flex-1 min-w-0">
                      <div className="text-xs text-text">{s.hint.label}</div>
                      <div className="text-[10px] text-text-tertiary truncate">
                        {s.hint.group}
                        {s.hint.stub ? ' · vazio no carro hoje' : ''}
                        {s.channel === 'control' ? ' · só por evento' : ''}
                        {escrita ? ' · só escrita' : ''}
                      </div>
                    </div>
                    {atual && <CheckIcon size={13} className="text-primary mt-0.5" />}
                  </button>
                )
              })}
            </div>
            {hint && (
              <button type="button" onClick={() => setTrocando(false)} className="text-[11px] text-text-tertiary hover:text-text">
                Manter {hint.label}
              </button>
            )}
          </div>
        )}

        {hint && (
          <div className="space-y-2">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-[10px] font-mono text-text-tertiary">{binding?.key}</span>
              {/* CONFIRMED sai do código do app, então é fato; UNCONFIRMED foi deduzido do nome
                  e nunca verificado no carro, que é a definição de "Deduzido". */}
              <ConfidenceChip level={hint.rawFormat === 'CONFIRMED' ? 'FACT' : 'INFERRED'} />
              <span className="text-[10px] text-text-tertiary">{hint.group}</span>
            </div>
            {hint.stub && <Banner tone="warning">{hint.stub}</Banner>}
            {hint.note && <p className="text-[11px] text-text-tertiary leading-snug">{hint.note}</p>}
            {fonte?.channel === 'control' && (
              <Banner tone="info">
                Esta chave não tem valor inicial: até o carro mandar o primeiro evento, o elemento
                mostra o texto de reserva.
              </Banner>
            )}
          </div>
        )}
      </Grupo>

      {binding && onFixar && (
        <Grupo
          titulo="Valor na prévia"
          dica="Fixa esta chave num valor só na pré-visualização, por cima do cenário. Não vai para o tema."
        >
          <div className="flex items-center gap-1.5">
            <input
              value={fixado ?? ''}
              placeholder="valor do cenário"
              onChange={(e) => onFixar(binding.key, e.target.value === '' ? null : e.target.value)}
              className="flex-1 min-w-0 bg-black/25 border border-white/10 rounded-lg px-2.5 py-2 text-sm text-text outline-none focus:border-primary/60"
            />
            {fixado !== undefined ? (
              <button
                type="button"
                onClick={() => onFixar(binding.key, null)}
                className="p-2 rounded-lg text-text-tertiary hover:text-text hover:bg-white/[0.08]"
                title="Soltar: volta a seguir o cenário"
                aria-label="Soltar valor"
              >
                <XIcon size={13} />
              </button>
            ) : (
              <PinIcon size={13} className="text-text-tertiary mx-2" />
            )}
          </div>
          {hint?.format === 'enum' && hint.enumLabels && Object.keys(hint.enumLabels).length > 0 && (
            <div className="flex flex-wrap gap-1">
              {Object.entries(hint.enumLabels).map(([codigo, rotulo]) => (
                <button
                  key={codigo}
                  type="button"
                  onClick={() => onFixar(binding.key, codigo)}
                  className={`px-2 py-0.5 rounded-full text-[10px] border ${
                    fixado === codigo ? 'border-primary bg-primary/20 text-text' : 'border-white/10 text-text-tertiary hover:text-text'
                  }`}
                >
                  {rotulo}
                </button>
              ))}
            </div>
          )}
          {hint?.format === 'boolish' && (
            <div className="flex gap-1">
              {[['1', 'Ligado'], ['0', 'Desligado']].map(([v, r]) => (
                <button
                  key={v}
                  type="button"
                  onClick={() => onFixar(binding.key, v)}
                  className={`px-2 py-0.5 rounded-full text-[10px] border ${
                    fixado === v ? 'border-primary bg-primary/20 text-text' : 'border-white/10 text-text-tertiary hover:text-text'
                  }`}
                >
                  {r}
                </button>
              ))}
            </div>
          )}
        </Grupo>
      )}

      <Grupo titulo="Formatação">
        <Linha>
          <TextField label="Unidade" value={binding?.unit ?? ''} placeholder="km/h" onChange={(v) => patchBinding({ unit: v })} />
          <NumberField label="Casas decimais" value={binding?.decimals ?? 0} min={0} max={3} onChange={(v) => patchBinding({ decimals: v })} />
        </Linha>
        <TextField
          label="Texto de reserva"
          value={binding?.fallback ?? '--'}
          placeholder="--"
          onChange={(v) => patchBinding({ fallback: v })}
        />
        <Linha>
          <NumberField
            label="Multiplicar por"
            value={binding?.transform?.scale ?? 1}
            step={0.001}
            onChange={(v) => patchBinding({ transform: { ...binding?.transform, scale: v } })}
          />
          <NumberField
            label="Somar"
            value={binding?.transform?.offset ?? 0}
            step={0.1}
            onChange={(v) => patchBinding({ transform: { ...binding?.transform, offset: v } })}
          />
        </Linha>
        <Switch
          label="Usar o valor absoluto"
          checked={binding?.transform?.abs ?? false}
          dica="Ignora o sinal. Útil na regeneração, que o carro manda negativa."
          onChange={(v) => patchBinding({ transform: { ...binding?.transform, abs: v } })}
        />
        <NumberField
          label="Suavizar a leitura"
          value={binding?.smoothMs ?? 0}
          step={20}
          min={0}
          max={2000}
          unit="ms"
          onChange={(v) => patchBinding({ smoothMs: Math.max(0, v) })}
        />
        <p className="text-[11px] text-text-tertiary leading-snug">
          Para chaves que chegam em degraus grandes — a porcentagem de potência pula de 18 para 30
          e o ponteiro parece travado. O valor é o tempo para percorrer o degrau, e é também o
          atraso que ele acrescenta sobre o dado do carro: 0 desliga, e é o certo em qualquer
          leitura que já chegue fina, como a velocidade.
        </p>
      </Grupo>

      {semEscala ? (
        <p className="text-[11px] text-text-tertiary leading-snug">
          A escala deste mostrador é editada na aba <strong>Estilo</strong>, em "Escala do mostrador".
        </p>
      ) : (
        <MapEditor map={map} onChange={(m) => onChange({ map: m })} sugestao={hint?.suggested} />
      )}
    </div>
  )
}

const TIPOS_DE_MAPA = [
  { value: 'linear' as const, label: 'Faixa' },
  { value: 'dualRange' as const, label: 'Duas faixas' },
  { value: 'piecewise' as const, label: 'Não linear' },
  { value: 'enum' as const, label: 'Lista' },
  { value: 'boolish' as const, label: 'Liga/desliga' },
  { value: 'text' as const, label: 'Texto' },
]

function MapEditor({
  map, onChange, sugestao,
}: {
  map: ValueMap
  onChange: (m: ValueMap) => void
  sugestao?: { min: number; max: number; majors: number[] }
}) {
  const trocar = (kind: ValueMap['kind']) => {
    if (kind === 'linear') return onChange({ kind: 'linear', min: 0, max: 100 })
    if (kind === 'dualRange') return onChange({ kind: 'dualRange', min: 0, low: 120, high: 260, upAt: 120, downAt: 100, rescaleMs: 650 })
    if (kind === 'piecewise') return onChange({ kind: 'piecewise', points: [{ v: -100, t: 0 }, { v: 0, t: 1 / 6 }, { v: 100, t: 1 }] })
    if (kind === 'enum') return onChange({ kind: 'enum', labels: {}, fallback: '--' })
    if (kind === 'boolish') return onChange({ kind: 'boolish' })
    return onChange({ kind: 'text' })
  }

  return (
    <Grupo titulo="Escala" dica="Como o valor vira posição no arco ou palavra na tela.">
      <Segmented value={map.kind} options={TIPOS_DE_MAPA} size="sm" onChange={trocar} />

      {map.kind === 'linear' && (
        <>
          <Linha>
            <NumberField label="Mínimo" value={map.min} onChange={(v) => onChange({ ...map, min: v })} />
            <NumberField label="Máximo" value={map.max} onChange={(v) => onChange({ ...map, max: v })} />
          </Linha>
          {sugestao && (
            <button
              type="button"
              onClick={() => onChange({ kind: 'linear', min: sugestao.min, max: sugestao.max })}
              className="text-[11px] text-primary hover:underline"
            >
              Aplicar faixa sugerida ({sugestao.min} a {sugestao.max})
            </button>
          )}
        </>
      )}

      {map.kind === 'dualRange' && (
        <>
          <p className="text-[11px] text-text-tertiary leading-snug">
            Duas faixas com histerese: abre para a faixa alta ao passar do limite de subida e só
            volta abaixo do de descida. As duas bordas diferentes impedem a escala de piscar.
          </p>
          <Linha>
            <NumberField label="Mínimo" value={map.min} onChange={(v) => onChange({ ...map, min: v })} />
            <NumberField label="Faixa curta até" value={map.low} onChange={(v) => onChange({ ...map, low: v })} />
          </Linha>
          <Linha>
            <NumberField label="Faixa longa até" value={map.high} onChange={(v) => onChange({ ...map, high: v })} />
            <NumberField label="Duração da troca" value={map.rescaleMs} step={50} unit="ms" onChange={(v) => onChange({ ...map, rescaleMs: v })} />
          </Linha>
          <Linha>
            <NumberField label="Abre acima de" value={map.upAt} onChange={(v) => onChange({ ...map, upAt: v })} />
            <NumberField label="Fecha abaixo de" value={map.downAt} onChange={(v) => onChange({ ...map, downAt: v })} />
          </Linha>
        </>
      )}

      {map.kind === 'piecewise' && (
        <>
          <p className="text-[11px] text-text-tertiary leading-snug">
            Cada ponto casa um valor com uma fração do arco. É assim que o zero do medidor de
            potência cai a um sexto do arco em vez do meio.
          </p>
          {map.points.map((p, i) => (
            <Linha key={i}>
              <NumberField
                label={`Valor ${i + 1}`}
                value={p.v}
                onChange={(v) => onChange({ ...map, points: map.points.map((q, j) => (i === j ? { ...q, v } : q)) })}
              />
              <NumberField
                label="Fração do arco"
                value={Number(p.t.toFixed(4))}
                step={0.01}
                min={0}
                max={1}
                onChange={(t) => onChange({ ...map, points: map.points.map((q, j) => (i === j ? { ...q, t } : q)) })}
              />
            </Linha>
          ))}
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => onChange({ ...map, points: [...map.points, { v: 0, t: 1 }] })}
              className="text-[11px] text-primary hover:underline"
            >
              Adicionar ponto
            </button>
            {map.points.length > 2 && (
              <button
                type="button"
                onClick={() => onChange({ ...map, points: map.points.slice(0, -1) })}
                className="text-[11px] text-text-tertiary hover:text-danger"
              >
                Remover o último
              </button>
            )}
          </div>
        </>
      )}

      {map.kind === 'enum' && (
        <>
          <p className="text-[11px] text-text-tertiary leading-snug">
            O carro manda um código; aqui você diz que palavra mostrar para cada um.
          </p>
          {Object.entries(map.labels).map(([codigo, texto]) => (
            <Linha key={codigo}>
              <TextField
                label="Código"
                value={codigo}
                onChange={(novo) => {
                  const labels: Record<string, string> = {}
                  for (const [c, t] of Object.entries(map.labels)) labels[c === codigo ? novo : c] = t
                  onChange({ ...map, labels })
                }}
              />
              <TextField
                label="Mostra"
                value={texto}
                onChange={(t) => onChange({ ...map, labels: { ...map.labels, [codigo]: t } })}
              />
            </Linha>
          ))}
          <div className="flex gap-3 items-end">
            <button
              type="button"
              onClick={() => onChange({ ...map, labels: { ...map.labels, '': '' } })}
              className="text-[11px] text-primary hover:underline"
            >
              Adicionar código
            </button>
          </div>
          <TextField label="Quando não bater nenhum" value={map.fallback} onChange={(v) => onChange({ ...map, fallback: v })} />
        </>
      )}

      {map.kind === 'boolish' && (
        <p className="text-[11px] text-text-tertiary leading-snug">
          Aceita 1, true, on, yes e ligado, sem diferenciar maiúsculas. Qualquer outra coisa é
          desligado. O formato real de algumas chaves nunca foi confirmado no veículo, por isso a
          lista é larga de propósito.
        </p>
      )}

      {map.kind === 'text' && (
        <p className="text-[11px] text-text-tertiary leading-snug">
          Mostra o texto exatamente como o carro mandou.
        </p>
      )}
    </Grupo>
  )
}
