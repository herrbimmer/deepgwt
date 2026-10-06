// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useMemo, useState } from 'react'
import { ChevronRightIcon, CpuIcon, CarFrontIcon, XIcon } from 'lucide-react'
import { CODING_CATALOG_BY_KEY } from '../data/codingCatalog'
import { RISK_CLASSES, riskOf } from './keyModel'
import { KeyEditor } from './CodingTree'
import { montarDominios, type EstadoModulo } from './moduleDiagram'
import type { Snapshot } from '../state/snapshots'
import type { FieldStatus } from '../state/VehicleSession'

/**
 * O diagrama de módulos do Coding, no formato da árvore de módulos do ISTA.
 *
 * O QUE É COPIADO DO ISTA E O QUE NÃO É
 *
 * O que é copiado é a GRAMÁTICA do desenho: um barramento horizontal no topo, o gateway no meio
 * dele, descidas verticais até cada domínio, colunas de caixas de módulo por domínio, o nome do
 * domínio embaixo da coluna e uma legenda de cores no rodapé. É o que faz uma tela dessas ser lida
 * de relance: dá para ver quantos módulos existem, como eles se agrupam e quais responderam, sem
 * ler nenhum texto.
 *
 * O que NÃO é copiado é o conteúdo. O ISTA desenha barramentos físicos (K-CAN, PT-CAN, MOST) e
 * ECUs endereçadas por diagnóstico. Este app não fala UDS com ECU nenhuma: ele lê e escreve o
 * dicionário do ControlService da multimídia, e o que existe ali é um espaço de nomes
 * (`car.configure.*`, `sys.radio.*`). Desenhar "PT-CAN" em cima disso seria inventar uma topologia
 * que este app não mediu. Então os domínios são os nomes reais do primeiro segmento da chave, as
 * caixas são os módulos reais do segundo, e a cor sai de um fato observável: quantas chaves do
 * módulo o carro devolveu com valor nesta leitura.
 *
 * POR QUE SÓ NO FORMATO HORIZONTAL
 *
 * O diagrama precisa de largura: as colunas ficam lado a lado e o barramento atravessa todas elas.
 * Espremido numa janela alta e estreita, cada coluna caberia sozinha na tela e o desenho viraria
 * uma lista com enfeite. Em pé, a lista recolhível (`CodingTree`) continua sendo a apresentação
 * certa, e é ela que aparece. Quem decide é `Coding.tsx`.
 */

const ESTADO_CLASSES: Record<EstadoModulo, string> = {
  responde: 'border-emerald-400/40 bg-emerald-500/15 text-emerald-100',
  parcial: 'border-amber-400/40 bg-amber-500/15 text-amber-100',
  mudo: 'border-white/15 bg-white/[0.04] text-text-tertiary',
}

const ESTADO_PONTO: Record<EstadoModulo, string> = {
  responde: 'bg-emerald-400',
  parcial: 'bg-amber-400',
  mudo: 'bg-white/25',
}

const LEGENDA: { estado: EstadoModulo; texto: string }[] = [
  { estado: 'responde', texto: 'todas as chaves vieram com valor' },
  { estado: 'parcial', texto: 'parte das chaves voltou vazia' },
  { estado: 'mudo', texto: 'nenhuma chave trouxe valor' },
]

export function CodingModuleTree({
  keys,
  values,
  baseline,
  snapshots,
  fieldStatus,
  onWrite,
  query,
  vin,
  pending = {},
}: {
  keys: string[]
  values: Record<string, string>
  baseline: Record<string, string>
  snapshots: Snapshot[]
  fieldStatus: Record<string, FieldStatus>
  onWrite: (key: string, value: string) => void
  query: string
  vin?: string | null
  /** Valores já escolhidos e ainda não gravados no carro (chave → valor). */
  pending?: Record<string, string>
}) {
  // Duas seleções independentes: o módulo aberto (que o diagrama destaca) e a variável escolhida
  // dentro dele. Elas ficam aqui, e não dentro das caixas, porque o conteúdo do módulo é mostrado
  // no painel lateral — abrir um módulo NÃO pode empurrar as caixas vizinhas para baixo, senão o
  // diagrama se remonta a cada toque e a pessoa perde o lugar de onde estava olhando.
  const [moduloAberto, setModuloAberto] = useState<string | null>(null)
  const [selecionada, setSelecionada] = useState<string | null>(null)

  const chavesVisiveis = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return keys
    return keys.filter(
      (k) =>
        k.toLowerCase().includes(q) ||
        (values[k] ?? '').toLowerCase().includes(q) ||
        (CODING_CATALOG_BY_KEY.get(k)?.name.toLowerCase().includes(q) ?? false),
    )
  }, [keys, query, values])

  const dominios = useMemo(
    () => montarDominios(chavesVisiveis, values),
    [chavesVisiveis, values],
  )

  const moduloEmFoco = useMemo(
    () =>
      dominios.flatMap((d) => d.modulos).find((m) => m.id === moduloAberto) ?? null,
    [dominios, moduloAberto],
  )

  const abrirModulo = (id: string) => {
    if (moduloAberto === id) {
      setModuloAberto(null)
      setSelecionada(null)
      return
    }
    setModuloAberto(id)
    setSelecionada(null)
  }

  if (chavesVisiveis.length === 0) {
    return <p className="p-6 text-text-tertiary text-sm">Nenhuma chave encontrada.</p>
  }

  return (
    <div className="flex-1 min-h-0 flex gap-4">
      <div className="flex-1 min-w-0 rounded-2xl border border-white/10 bg-white/[0.03] overflow-auto">
        <div className="min-w-max p-5">
          {/* ── GATEWAY ──
              O veículo em si, no meio do barramento. No ISTA é a ECU de gateway, por onde toda
              consulta passa; aqui é o ControlService da multimídia, que é literalmente o único
              caminho pelo qual este app lê e escreve qualquer uma destas chaves. */}
          <div className="flex justify-center">
            <div className="inline-flex items-center gap-2 rounded-lg border border-primary/40 bg-primary/10 px-3 py-2">
              <CarFrontIcon size={15} className="text-primary flex-shrink-0" />
              <span className="text-xs font-semibold text-text">Veículo</span>
              <span className="font-mono text-[10px] text-text-tertiary">
                {vin || 'VIN não lido'}
              </span>
              <span className="text-[10px] text-text-tertiary border-l border-white/15 pl-2">
                ControlService · {chavesVisiveis.length} chaves
              </span>
            </div>
          </div>

          {/* Descida do gateway até o barramento. */}
          <div className="flex justify-center">
            <span className="w-px h-4 bg-white/20" />
          </div>

          {/* ── BARRAMENTO ──
              Uma linha só, atravessando todos os domínios, com uma descida por domínio. */}
          <div className="relative">
            <span className="absolute left-0 right-0 top-0 h-px bg-white/20" />
            <div className="flex items-start gap-8 pt-0">
              {dominios.map((dominio) => (
                <div key={dominio.nome} className="flex flex-col items-center">
                  {/* Descida do barramento até as caixas deste domínio. */}
                  <span className="w-px h-5 bg-white/20" />

                  <div className="flex items-start gap-3">
                    {dominio.colunas.map((coluna, i) => (
                      <div key={i} className="flex flex-col gap-1.5">
                        {coluna.map((modulo) => {
                          const aberto = moduloAberto === modulo.id
                          return (
                            <button
                              key={modulo.id}
                              onClick={() => abrirModulo(modulo.id)}
                              className={`w-[190px] flex items-center gap-1.5 rounded-md border px-2 py-1.5 text-left transition-colors hover:brightness-125 ${
                                ESTADO_CLASSES[modulo.estado]
                              } ${aberto ? 'ring-2 ring-primary/70' : ''}`}
                              title={`${modulo.dominio}.${modulo.nome} — ${modulo.comValor} de ${modulo.chaves.length} chaves com valor`}
                            >
                              <ChevronRightIcon
                                size={11}
                                className={`flex-shrink-0 opacity-60 transition-transform ${
                                  aberto ? 'rotate-90' : ''
                                }`}
                              />
                              <CpuIcon size={12} className="flex-shrink-0 opacity-70" />
                              <span className="font-mono text-[11px] truncate flex-1">
                                {modulo.nome}
                              </span>
                              <span className="text-[10px] opacity-70 flex-shrink-0">
                                {modulo.chaves.length}
                              </span>
                            </button>
                          )
                        })}
                      </div>
                    ))}
                  </div>

                  {/* Nome do domínio embaixo da coluna, como o nome do barramento no ISTA. */}
                  <span className="mt-2 font-mono text-[11px] text-text-secondary tracking-wide">
                    {dominio.nome}
                  </span>
                </div>
              ))}
            </div>
          </div>

          {/* ── LEGENDA ── */}
          <div className="mt-5 pt-3 border-t border-white/10 flex flex-wrap items-center gap-x-5 gap-y-2">
            {LEGENDA.map(({ estado, texto }) => (
              <span key={estado} className="flex items-center gap-1.5 text-[10px] text-text-tertiary">
                <span className={`w-2.5 h-2.5 rounded-sm ${ESTADO_PONTO[estado]}`} />
                {texto}
              </span>
            ))}
            <span className="text-[10px] text-text-tertiary ml-auto">
              Toque num módulo para abrir as variáveis dele.
            </span>
          </div>
        </div>
      </div>

      {/* O painel só ocupa espaço quando há um módulo aberto — sem isso o diagrama perderia
          largura o tempo todo para um painel vazio. */}
      {moduloEmFoco && (
        <aside className="w-[380px] flex-shrink-0 rounded-2xl border border-white/10 bg-white/[0.03] flex flex-col min-h-0">
          <div className="flex items-center gap-2 px-4 py-3 border-b border-white/10 flex-shrink-0">
            <span className={`w-2.5 h-2.5 rounded-sm ${ESTADO_PONTO[moduloEmFoco.estado]}`} />
            <span className="font-mono text-xs text-text truncate flex-1">
              {moduloEmFoco.dominio}.{moduloEmFoco.nome}
            </span>
            <span className="text-[10px] text-text-tertiary flex-shrink-0">
              {moduloEmFoco.comValor}/{moduloEmFoco.chaves.length}
            </span>
            <button
              onClick={() => {
                setModuloAberto(null)
                setSelecionada(null)
              }}
              className="text-text-tertiary hover:text-text flex-shrink-0"
              aria-label="Fechar"
            >
              <XIcon size={14} />
            </button>
          </div>

          {selecionada ? (
            <div className="flex-1 min-h-0 overflow-auto p-4">
              <button
                onClick={() => setSelecionada(null)}
                className="text-[11px] text-primary hover:text-primary-hover mb-3"
              >
                ← variáveis do módulo
              </button>
              {/* Nome COMPLETO da variável, sem truncar: é ele que identifica a chave no carro. */}
              <p className="font-mono text-xs text-text break-all mb-3 pb-3 border-b border-white/10">
                {selecionada}
              </p>
              <KeyEditor
                key={selecionada}
                codingKey={selecionada}
                current={values[selecionada]}
                baselineValue={baseline[selecionada]}
                snapshots={snapshots}
                status={fieldStatus[selecionada]}
                pendingValue={pending[selecionada]}
                onWrite={onWrite}
              />
            </div>
          ) : (
            <div className="flex-1 min-h-0 overflow-auto p-2">
              {moduloEmFoco.chaves.map((chave) => {
                const atual = values[chave]
                const pend = pending[chave]
                const mudou = chave in baseline && (atual ?? '') !== baseline[chave]
                const entrada = CODING_CATALOG_BY_KEY.get(chave)
                return (
                  <button
                    key={chave}
                    onClick={() => setSelecionada(chave)}
                    className="w-full text-left rounded-lg px-2 py-1.5 hover:bg-white/[0.05] flex items-center gap-2"
                  >
                    <span className="flex-1 min-w-0">
                      <span className="block font-mono text-[11px] text-text break-all leading-tight">
                        {chave}
                      </span>
                      {entrada && (
                        <span className="block text-[10px] text-text-tertiary truncate">
                          {entrada.name}
                        </span>
                      )}
                    </span>
                    <span
                      className={`px-1.5 py-0.5 rounded text-[9px] uppercase flex-shrink-0 ${
                        RISK_CLASSES[riskOf(chave)]
                      }`}
                    >
                      {riskOf(chave)}
                    </span>
                    <span
                      className={`font-mono text-[11px] w-24 text-right truncate flex-shrink-0 ${
                        mudou ? 'text-amber-200' : 'text-text-secondary'
                      }`}
                    >
                      {pend !== undefined ? (
                        <>
                          {atual || '∅'} → <span className="text-amber-200">{pend || '∅'}</span>
                        </>
                      ) : (
                        atual || '∅'
                      )}
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </aside>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
