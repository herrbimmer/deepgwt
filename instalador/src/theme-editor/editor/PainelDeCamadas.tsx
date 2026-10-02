// A coluna da esquerda: páginas em cima, camadas embaixo.
//
// As camadas vêm da mais alta para a mais baixa, como o painel as empilha. Grupos aparecem na
// altura do seu elemento mais alto e recolhem. Cada linha tem o olho (tira do tema sem apagar) e o
// cadeado (o canvas para de arrastar); o resto das ações aparece ao passar o mouse, para a linha
// caber em 232 px sem virar uma régua de ícones.

import { useState } from 'react'
import {
  ChevronDownIcon, ChevronRightIcon, ChevronUpIcon, CopyIcon, EyeIcon, EyeOffIcon, FolderIcon,
  LockIcon, PlusIcon, Trash2Icon, UnlockIcon, ZapIcon,
} from 'lucide-react'
import type { Element, LayerGroup, ThemePage } from '../model/spec'
import { firstBlocker } from '../model/collision'
import { KIND_LABELS, rotuloDoElemento } from '../panels/ElementPanel'
import type { ModoDeSelecao } from '../ClusterCanvas'

// Uma cor por TIPO, não a cor real do elemento: a cor real quase sempre é um token da paleta, que
// só vira cor dentro do tema gerado. Resolver token aqui exigiria carregar a paleta na lista de
// camadas para um pontinho de 12 px.
const AMOSTRA: Record<Element['kind'], string> = {
  dial: '#8aa0b4',
  barLinear: '#0a84ff',
  readout: '#f2f4f6',
  indicator: '#34e871',
  media: '#c084fc',
  decor: '#5b6673',
  image: '#f59e0b',
  shape: '#94a3b8',
  chart: '#22d3ee',
}

export type PainelDeCamadasProps = {
  pages: ThemePage[]
  activeId: string
  onPagina: (id: string) => void
  onNovaPagina: () => void
  onDuplicarPagina: (id: string) => void
  onRenomearPagina: (id: string, nome: string) => void
  onApagarPagina: (id: string) => void

  page: ThemePage
  selecionados: string[]
  onSelecionar: (ids: string[], modo?: ModoDeSelecao) => void
  onReordenar: (id: string, direcao: 'up' | 'down') => void
  onDuplicar: (id: string) => void
  onApagar: (id: string) => void
  /** Liga/desliga uma bandeira em vários elementos de uma vez (o olho e o cadeado de um grupo). */
  onBandeira: (ids: string[], campo: 'hidden' | 'locked', valor: boolean) => void
  onGrupo: (grupo: LayerGroup) => void
  onAdicionar: () => void
  /** Básico: sem grupos nem cadeado, só o olho. */
  simples?: boolean
}

type Linha =
  | { tipo: 'grupo'; grupo: LayerGroup; membros: Element[] }
  | { tipo: 'elemento'; el: Element; noGrupo: boolean }

function montarLinhas(page: ThemePage): Linha[] {
  const ordenados = [...page.elements].sort((a, b) => b.place.z - a.place.z)
  const grupos = new Map((page.groups ?? []).map((g) => [g.id, g]))
  const vistos = new Set<string>()
  const linhas: Linha[] = []
  for (const el of ordenados) {
    const g = el.groupId ? grupos.get(el.groupId) : undefined
    if (!g) {
      linhas.push({ tipo: 'elemento', el, noGrupo: false })
      continue
    }
    if (vistos.has(g.id)) continue
    vistos.add(g.id)
    const membros = ordenados.filter((x) => x.groupId === g.id)
    linhas.push({ tipo: 'grupo', grupo: g, membros })
    if (!g.collapsed) for (const m of membros) linhas.push({ tipo: 'elemento', el: m, noGrupo: true })
  }
  return linhas
}

const botaoIcone = 'p-1 rounded text-text-tertiary hover:text-text hover:bg-white/[0.08] disabled:opacity-25'

export function PainelDeCamadas(p: PainelDeCamadasProps) {
  const [renomeando, setRenomeando] = useState<{ id: string; valor: string; alvo: 'pagina' | 'grupo' } | null>(null)
  const selecao = new Set(p.selecionados)
  const ordem = [...p.page.elements].sort((a, b) => b.place.z - a.place.z).map((e) => e.id)

  const confirmarNome = () => {
    if (!renomeando) return
    const nome = renomeando.valor.trim()
    if (nome) {
      if (renomeando.alvo === 'pagina') p.onRenomearPagina(renomeando.id, nome)
      else {
        const g = (p.page.groups ?? []).find((x) => x.id === renomeando.id)
        if (g) p.onGrupo({ ...g, name: nome })
      }
    }
    setRenomeando(null)
  }

  const campoDeNome = (
    <input
      autoFocus
      value={renomeando?.valor ?? ''}
      onChange={(e) => setRenomeando((r) => (r ? { ...r, valor: e.target.value } : r))}
      onBlur={confirmarNome}
      onKeyDown={(e) => {
        if (e.key === 'Enter') confirmarNome()
        if (e.key === 'Escape') setRenomeando(null)
      }}
      className="flex-1 min-w-0 bg-black/40 border border-primary/60 rounded px-1.5 py-0.5 text-xs text-text outline-none"
    />
  )

  const clicar = (ev: React.MouseEvent, id: string) => {
    if (ev.shiftKey || ev.metaKey || ev.ctrlKey) p.onSelecionar([id], 'alternar')
    else p.onSelecionar([id], 'substituir')
  }

  return (
    // Sem seleção de texto: Shift+clique aqui monta seleção de elementos, não de palavras.
    <div className="space-y-5 select-none">
      {/* ---- páginas ---- */}
      <section>
        <div className="flex items-center justify-between mb-1.5">
          <h4 className="text-[11px] font-semibold text-text-secondary">Páginas</h4>
          <div className="flex items-center">
            <button type="button" className={botaoIcone} onClick={() => p.onDuplicarPagina(p.activeId)} title="Duplicar a página aberta" aria-label="Duplicar página">
              <CopyIcon size={12} />
            </button>
            <button type="button" className={botaoIcone} onClick={p.onNovaPagina} title="Nova página" aria-label="Nova página">
              <PlusIcon size={13} />
            </button>
          </div>
        </div>
        <div className="space-y-0.5">
          {p.pages.map((pg, i) => {
            const ativa = pg.id === p.activeId
            const auto = pg.showWhen && pg.showWhen.when !== 'always'
            return (
              <div
                key={pg.id}
                onClick={() => p.onPagina(pg.id)}
                onDoubleClick={() => i > 0 && setRenomeando({ id: pg.id, valor: pg.name, alvo: 'pagina' })}
                className={`group flex items-center gap-1.5 px-2 py-1.5 rounded-lg cursor-pointer text-xs ${
                  ativa ? 'bg-primary/15 text-text' : 'text-text-secondary hover:bg-white/[0.05]'
                }`}
              >
                <span className={`w-2.5 h-2 rounded-[3px] border ${ativa ? 'border-primary bg-primary/40' : 'border-white/30'}`} />
                {renomeando?.alvo === 'pagina' && renomeando.id === pg.id ? (
                  campoDeNome
                ) : (
                  <span className="flex-1 min-w-0 truncate">{pg.name}</span>
                )}
                {auto && <span title="Abre sozinha quando a condição dela vale"><ZapIcon size={11} className="text-amber-300 flex-shrink-0" /></span>}
                {i > 0 && (
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      p.onApagarPagina(pg.id)
                    }}
                    className={`${botaoIcone} opacity-0 group-hover:opacity-100`}
                    title="Apagar página"
                    aria-label={`Apagar ${pg.name}`}
                  >
                    <Trash2Icon size={11} />
                  </button>
                )}
              </div>
            )
          })}
        </div>
      </section>

      {/* ---- camadas ---- */}
      <section>
        <div className="flex items-center justify-between mb-1.5">
          <h4 className="text-[11px] font-semibold text-text-secondary">Camadas — {p.page.name}</h4>
          <button type="button" className={botaoIcone} onClick={p.onAdicionar} title="Adicionar elemento" aria-label="Adicionar elemento">
            <PlusIcon size={13} />
          </button>
        </div>
        {p.page.elements.length === 0 ? (
          <p className="text-xs text-text-tertiary px-1">Nenhum elemento nesta página ainda.</p>
        ) : (
          <div className="space-y-0.5">
            {montarLinhas(p.page).map((linha) => {
              if (linha.tipo === 'grupo') {
                const { grupo, membros } = linha
                const ids = membros.map((m) => m.id)
                const todosOcultos = membros.every((m) => m.hidden)
                const todosTravados = membros.every((m) => m.locked)
                const todosEscolhidos = ids.every((id) => selecao.has(id))
                return (
                  <div
                    key={grupo.id}
                    onClick={(e) => (e.shiftKey || e.metaKey ? p.onSelecionar(ids, 'somar') : p.onSelecionar(ids, 'substituir'))}
                    onDoubleClick={() => setRenomeando({ id: grupo.id, valor: grupo.name, alvo: 'grupo' })}
                    className={`group flex items-center gap-1 px-1 py-1.5 rounded-lg cursor-pointer text-xs ${
                      todosEscolhidos ? 'bg-primary/10 text-text' : 'text-text-secondary hover:bg-white/[0.05]'
                    }`}
                  >
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation()
                        p.onGrupo({ ...grupo, collapsed: !grupo.collapsed })
                      }}
                      className={botaoIcone}
                      aria-label={grupo.collapsed ? 'Abrir grupo' : 'Recolher grupo'}
                    >
                      {grupo.collapsed ? <ChevronRightIcon size={12} /> : <ChevronDownIcon size={12} />}
                    </button>
                    <FolderIcon size={12} className="text-text-tertiary flex-shrink-0" />
                    {renomeando?.alvo === 'grupo' && renomeando.id === grupo.id ? (
                      campoDeNome
                    ) : (
                      <span className="flex-1 min-w-0 truncate font-medium">{grupo.name}</span>
                    )}
                    <span className="text-[10px] text-text-tertiary">{membros.length}</span>
                    <button
                      type="button"
                      className={botaoIcone}
                      onClick={(e) => {
                        e.stopPropagation()
                        p.onBandeira(ids, 'hidden', !todosOcultos)
                      }}
                      title={todosOcultos ? 'Mostrar o grupo' : 'Ocultar o grupo (não vai para o carro)'}
                    >
                      {todosOcultos ? <EyeOffIcon size={12} /> : <EyeIcon size={12} />}
                    </button>
                    {!p.simples && (
                      <button
                        type="button"
                        className={botaoIcone}
                        onClick={(e) => {
                          e.stopPropagation()
                          p.onBandeira(ids, 'locked', !todosTravados)
                        }}
                        title={todosTravados ? 'Destravar o grupo' : 'Travar o grupo no canvas'}
                      >
                        {todosTravados ? <LockIcon size={12} className="text-amber-300" /> : <UnlockIcon size={12} />}
                      </button>
                    )}
                  </div>
                )
              }

              const { el, noGrupo } = linha
              const bloqueador = el.hidden ? null : firstBlocker(el)
              const i = ordem.indexOf(el.id)
              return (
                <div
                  key={el.id}
                  onClick={(e) => clicar(e, el.id)}
                  className={`group flex items-center gap-1.5 pr-1 py-1.5 rounded-lg cursor-pointer text-xs ${noGrupo ? 'pl-5' : 'pl-2'} ${
                    selecao.has(el.id) ? 'bg-primary/15 text-text' : 'text-text-secondary hover:bg-white/[0.05]'
                  } ${el.hidden ? 'opacity-50' : ''}`}
                >
                  <span className="w-2.5 h-2.5 rounded-full flex-shrink-0" style={{ background: AMOSTRA[el.kind] }} />
                  <div className="flex-1 min-w-0">
                    <div className="truncate font-medium flex items-center gap-1">
                      <span className="truncate">{el.name}</span>
                      {bloqueador && <LockIcon size={10} className="text-danger flex-shrink-0" />}
                    </div>
                    <div className="text-[10px] text-text-tertiary truncate">
                      {p.simples ? rotuloDoElemento(el) : KIND_LABELS[el.kind]}
                      {bloqueador ? ` · sobre ${bloqueador.zone.label}` : ''}
                      {el.visible.when !== 'always' ? ' · condicional' : ''}
                    </div>
                  </div>
                  <div className="flex items-center flex-shrink-0" onClick={(e) => e.stopPropagation()}>
                    {!p.simples && (
                      <span className="hidden group-hover:flex items-center">
                        <button type="button" disabled={i <= 0} onClick={() => p.onReordenar(el.id, 'up')} className={botaoIcone} title="Trazer para frente">
                          <ChevronUpIcon size={12} />
                        </button>
                        <button type="button" disabled={i >= ordem.length - 1} onClick={() => p.onReordenar(el.id, 'down')} className={botaoIcone} title="Enviar para trás">
                          <ChevronDownIcon size={12} />
                        </button>
                        <button type="button" onClick={() => p.onDuplicar(el.id)} className={botaoIcone} title="Duplicar (Ctrl/Cmd + D)">
                          <CopyIcon size={12} />
                        </button>
                        <button type="button" onClick={() => p.onApagar(el.id)} className={`${botaoIcone} hover:!text-danger`} title="Apagar (Delete)">
                          <Trash2Icon size={12} />
                        </button>
                      </span>
                    )}
                    <button
                      type="button"
                      onClick={() => p.onBandeira([el.id], 'hidden', !el.hidden)}
                      className={botaoIcone}
                      title={el.hidden ? 'Mostrar' : 'Ocultar — fica salvo aqui, mas não vai para o carro'}
                      aria-label={el.hidden ? `Mostrar ${el.name}` : `Ocultar ${el.name}`}
                    >
                      {el.hidden ? <EyeOffIcon size={12} /> : <EyeIcon size={12} />}
                    </button>
                    {!p.simples && (
                      <button
                        type="button"
                        onClick={() => p.onBandeira([el.id], 'locked', !el.locked)}
                        className={botaoIcone}
                        title={el.locked ? 'Destravar' : 'Travar no canvas'}
                        aria-label={el.locked ? `Destravar ${el.name}` : `Travar ${el.name}`}
                      >
                        {el.locked ? <LockIcon size={12} className="text-amber-300" /> : <UnlockIcon size={12} className="opacity-40 group-hover:opacity-100" />}
                      </button>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
        <button
          type="button"
          onClick={p.onAdicionar}
          className="mt-2 w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg border border-dashed border-white/15 text-xs text-text-tertiary hover:text-text hover:border-white/30"
        >
          <PlusIcon size={13} />
          Adicionar elemento
        </button>
      </section>
    </div>
  )
}
