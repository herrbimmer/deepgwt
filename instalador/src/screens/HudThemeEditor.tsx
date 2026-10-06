// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ArrowDownIcon, ArrowUpIcon, CheckIcon, ChevronLeftIcon, DownloadIcon, LayersIcon, PlusIcon, Redo2Icon, RotateCcwIcon,
  SendIcon, Undo2Icon, XIcon,
} from 'lucide-react'
import { Banner, Button, ColorField, Modal, Segmented, SelectField, SliderField, TextInput } from '../ui'
import { EditorShell } from '../theme-editor/editor/EditorShell'
import { usePreferenciasDoEditor } from '../theme-editor/editor/preferencias'
import { CarLinkPanel } from '../components'
import { useCarLink } from '../hooks/useCarLink'
import { SAVE_MESSAGE, saveText } from '../lib/fileExchange'
import { HudPreview, type FundoDaPrevia } from '../hud-editor/HudPreview'
import {
  CORES_DE_ROTULO, CORES_DE_VALOR, HUD_CAMPOS, LAYOUT_PADRAO, LIMITES,
  type HudCampoId, type HudPaginaId, type HudPaginaTema, type HudTemaSalvo,
  aceitaCampos, corDaPagina, layoutDe, luminancia, nomeDoPeso, paraCarro, paginasVisiveis, tituloDe, tituloDeFabrica,
} from '../hud-editor/model'
import { type AlvoDaPrevia, CENARIOS } from '../hud-editor/render'
import { lerTemaHud, salvarTemaHud } from '../hud-editor/storage'
import { aplicarTemaNoCarro } from '../hud-editor/carro'

type Envio = 'idle' | 'busy' | 'done' | 'error'

const LIMITE_HISTORICO = 80

// ---------------------------------------------------------------- peças do painel

function Secao({ titulo, dica, children, acao }: { titulo: string; dica?: string; children: ReactNode; acao?: ReactNode }) {
  return (
    <section className="bg-white/[0.04] border border-white/10 rounded-2xl p-4 space-y-3">
      <div className="flex items-start justify-between gap-2">
        <div>
          <h4 className="text-xs font-semibold text-text uppercase tracking-wide">{titulo}</h4>
          {dica && <p className="text-[11px] text-text-tertiary mt-0.5 leading-snug">{dica}</p>}
        </div>
        {acao}
      </div>
      {children}
    </section>
  )
}

function Interruptor({ titulo, dica, ligado, onChange, desabilitado }: {
  titulo: string; dica?: string; ligado: boolean; onChange: (v: boolean) => void; desabilitado?: boolean
}) {
  return (
    <div className={`flex items-center gap-3 ${desabilitado ? 'opacity-40' : ''}`}>
      <div className="flex-1 min-w-0">
        <div className="text-sm text-text">{titulo}</div>
        {dica && <div className="text-[11px] text-text-tertiary leading-snug">{dica}</div>}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={ligado}
        aria-label={titulo}
        disabled={desabilitado}
        onClick={() => onChange(!ligado)}
        className={`relative w-10 h-6 rounded-full transition-colors flex-shrink-0 disabled:cursor-not-allowed ${ligado ? 'bg-primary' : 'bg-white/15'}`}
      >
        <span className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform ${ligado ? 'translate-x-4' : ''}`} />
      </button>
    </div>
  )
}

/** Atalhos de cor + o campo livre. O aviso aparece quando a cor fica escura demais para o vidro. */
function CorComAtalhos({ label, valor, atalhos, onChange, avisarEscura = true }: {
  label: string; valor: string; atalhos: { nome: string; cor: string }[]; onChange: (c: string) => void; avisarEscura?: boolean
}) {
  const escura = avisarEscura && luminancia(valor) < 0.18
  return (
    <div>
      <label className="text-xs font-medium text-text-secondary mb-1.5 block">{label}</label>
      <div className="flex items-center gap-1.5 mb-2">
        {atalhos.map((a) => (
          <button
            key={a.cor}
            type="button"
            title={a.nome}
            aria-label={a.nome}
            onClick={() => onChange(a.cor)}
            className={`w-7 h-7 rounded-full border transition-transform active:scale-95 ${
              valor.toUpperCase() === a.cor.toUpperCase() ? 'border-white ring-2 ring-primary/70' : 'border-white/20'
            }`}
            style={{ background: a.cor }}
          />
        ))}
      </div>
      <ColorField value={valor} alpha={false} onChange={(c) => onChange(c.toUpperCase())} />
      {escura && (
        <p className="text-[11px] text-warning mt-1.5 leading-snug">
          Cor escura quase não aparece no para-brisa: o HUD projeta luz, e pouca luz é quase nada.
        </p>
      )}
    </div>
  )
}

function SeletorDeCampo({ valor, onChange, onRemover }: { valor: HudCampoId; onChange: (c: HudCampoId) => void; onRemover?: () => void }) {
  const info = HUD_CAMPOS.find((c) => c.id === valor)
  return (
    <div>
      <div className="flex items-center gap-1.5">
        <SelectField value={valor} onChange={(e) => onChange(e.target.value as HudCampoId)} containerClassName="flex-1">
          {HUD_CAMPOS.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nome}
            </option>
          ))}
        </SelectField>
        {onRemover && (
          <button
            type="button"
            onClick={onRemover}
            aria-label="Remover campo"
            className="p-2 rounded-lg text-text-tertiary hover:text-danger hover:bg-danger/10"
          >
            <XIcon size={14} />
          </button>
        )}
      </div>
      {info?.dica && <p className="text-[10px] text-text-tertiary mt-1 leading-snug">{info.dica}</p>}
    </div>
  )
}

// ---------------------------------------------------------------- tela

export const HudThemeEditor = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [tema, setTema] = useState<HudTemaSalvo | null>(null)
  const [alvo, setAlvo] = useState<AlvoDaPrevia>('VELOCIDADE')
  const [cenarioId, setCenarioId] = useState(CENARIOS[0].id)
  const [fundo, setFundo] = useState<FundoDaPrevia>('hud')
  const [salvo, setSalvo] = useState(true)
  const [passado, setPassado] = useState<HudTemaSalvo[]>([])
  const [futuro, setFuturo] = useState<HudTemaSalvo[]>([])
  const grupoAnterior = useRef<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [showEnvio, setShowEnvio] = useState(false)
  const [envio, setEnvio] = useState<Envio>('idle')
  const [erroEnvio, setErroEnvio] = useState<string | null>(null)
  const link = useCarLink()
  // A coluna recolhida é preferência do EDITOR, a mesma do editor do painel: quem recolhe num
  // encontra recolhida no outro.
  const [prefs, definirPrefs] = usePreferenciasDoEditor()

  useEffect(() => {
    if (!id) return
    const achado = lerTemaHud(id)
    if (!achado) {
      navigate('/temas?tipo=hud')
      return
    }
    setTema(achado)
  }, [id, navigate])

  // Mesmo esquema do editor do painel: a lista de atalhos é montada uma vez e lê as ações de uma
  // referência que cada render atualiza.
  const acoes = useRef({ desfazer: () => {}, refazer: () => {} })
  useEffect(() => {
    const aoTeclar = (ev: KeyboardEvent) => {
      const alvoEv = ev.target as HTMLElement | null
      if (alvoEv && /^(INPUT|TEXTAREA|SELECT)$/.test(alvoEv.tagName)) return
      if (!(ev.metaKey || ev.ctrlKey)) return
      const tecla = ev.key.toLowerCase()
      if (tecla === 'z' && !ev.shiftKey) {
        ev.preventDefault()
        acoes.current.desfazer()
      } else if ((tecla === 'z' && ev.shiftKey) || tecla === 'y') {
        ev.preventDefault()
        acoes.current.refazer()
      }
    }
    window.addEventListener('keydown', aoTeclar)
    return () => window.removeEventListener('keydown', aoTeclar)
  }, [])

  if (!tema) return null

  const cenario = CENARIOS.find((c) => c.id === cenarioId) ?? CENARIOS[0]

  /** @param grupo edições seguidas com o mesmo rótulo (arrastar um controle) contam como uma. */
  const atualizar = (proximo: HudTemaSalvo, grupo: string | null = null) => {
    const mesmoGrupo = grupo !== null && grupo === grupoAnterior.current && passado.length > 0
    if (!mesmoGrupo) setPassado([...passado, tema].slice(-LIMITE_HISTORICO))
    grupoAnterior.current = grupo
    if (futuro.length > 0) setFuturo([])
    setTema(proximo)
    setSalvo(false)
  }
  const desfazer = () => {
    if (passado.length === 0) return
    setPassado(passado.slice(0, -1))
    setFuturo([tema, ...futuro].slice(0, LIMITE_HISTORICO))
    setTema(passado[passado.length - 1])
    setSalvo(false)
    grupoAnterior.current = null
  }
  const refazer = () => {
    if (futuro.length === 0) return
    setPassado([...passado, tema].slice(-LIMITE_HISTORICO))
    setFuturo(futuro.slice(1))
    setTema(futuro[0])
    setSalvo(false)
    grupoAnterior.current = null
  }
  acoes.current = { desfazer, refazer }

  const estilo = (parte: Partial<HudTemaSalvo['estilo']>, grupo: string | null = null) =>
    atualizar({ ...tema, estilo: { ...tema.estilo, ...parte } }, grupo)
  const velocidade = (parte: Partial<HudTemaSalvo['velocidade']>, grupo: string | null = null) =>
    atualizar({ ...tema, velocidade: { ...tema.velocidade, ...parte } }, grupo)
  const placa = (parte: Partial<HudTemaSalvo['placa']>, grupo: string | null = null) =>
    atualizar({ ...tema, placa: { ...tema.placa, ...parte } }, grupo)
  const pagina = (pid: HudPaginaId, fn: (p: HudPaginaTema) => HudPaginaTema, grupo: string | null = null) =>
    atualizar({ ...tema, paginas: tema.paginas.map((p) => (p.pagina === pid ? fn(p) : p)) }, grupo)

  const mover = (pid: HudPaginaId, passo: number) => {
    const lista = [...tema.paginas]
    const de = lista.findIndex((p) => p.pagina === pid)
    const para = de + passo
    if (de < 0 || para < 0 || para >= lista.length) return
    const [item] = lista.splice(de, 1)
    lista.splice(para, 0, item)
    atualizar({ ...tema, paginas: lista })
  }

  const ativas = tema.paginas.filter((p) => p.ativa).length

  const salvar = () => {
    const gravado = salvarTemaHud(tema)
    setTema(gravado)
    setSalvo(true)
  }

  const exportar = async () => {
    const nomeArquivo = `${tema.nome.trim().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-|-$/g, '').toLowerCase() || 'tema'}.hud.json`
    const r = await saveText(nomeArquivo, JSON.stringify(paraCarro(tema), null, 2), 'json')
    if (r.ok) setAviso(SAVE_MESSAGE[r.how](r.path))
    else if (r.how !== 'cancelled') setAviso(`Não foi possível salvar: ${r.error ?? 'erro desconhecido'}`)
  }

  const enviar = async () => {
    setEnvio('busy')
    setErroEnvio(null)
    try {
      await aplicarTemaNoCarro(link.carIp, tema)
      salvar()
      setEnvio('done')
    } catch (e) {
      setErroEnvio(e instanceof Error ? e.message : String(e))
      setEnvio('error')
    }
  }

  // ---- painel da página escolhida -------------------------------------------------------------

  const painelDaPagina = () => {
    if (alvo === 'ALERTA') {
      return (
        <Secao
          titulo="Cortina de alerta"
          dica="Porta aberta, cinto, pressão de pneu… Entra por cima de qualquer página quando o carro avisa."
        >
          <Interruptor
            titulo="Mostrar alertas no HUD"
            ligado={tema.alertas}
            onChange={(v) => atualizar({ ...tema, alertas: v })}
          />
          <p className="text-[11px] text-text-tertiary leading-snug">
            Cor, letra e tamanho da cortina são fixos: é o único conteúdo que o motorista não escolheu ver, e o
            tema não pode deixá-lo mais discreto.
          </p>
        </Secao>
      )
    }
    const pid = alvo
    const p = tema.paginas.find((x) => x.pagina === pid)!
    if (pid === 'VELOCIDADE') {
      return (
        <Secao titulo="Página — Velocidade" dica="A página que fica no para-brisa quase o tempo todo.">
          <SliderField
            label="Tamanho do número"
            value={tema.velocidade.tamanho}
            min={LIMITES.velocidade.min}
            max={LIMITES.velocidade.max}
            unit="% da altura"
            onChange={(v) => velocidade({ tamanho: v }, 'vel-tamanho')}
          />
          <CorComAtalhos label="Cor do número" valor={tema.velocidade.cor} atalhos={CORES_DE_VALOR} onChange={(c) => velocidade({ cor: c })} />
          <Interruptor titulo='"km/h" embaixo do número' ligado={tema.velocidade.unidade} onChange={(v) => velocidade({ unidade: v })} />
          <div className="border-t border-white/10 pt-3 space-y-3">
            <Interruptor
              titulo="Placa de limite da via"
              dica="Aparece com a placa lida pela câmera; sem placa, um aro apagado marca o lugar."
              ligado={tema.placa.mostrar}
              onChange={(v) => placa({ mostrar: v })}
            />
            {tema.placa.mostrar && (
              <>
                <SliderField
                  label="Tamanho da placa"
                  value={tema.placa.tamanho}
                  min={LIMITES.placa.min}
                  max={LIMITES.placa.max}
                  unit="% da altura"
                  onChange={(v) => placa({ tamanho: v }, 'placa-tamanho')}
                />
                <Segmented
                  label="Lado da placa"
                  size="sm"
                  value={tema.placa.lado}
                  options={[
                    { value: 'esquerda', label: 'Esquerda' },
                    { value: 'direita', label: 'Direita' },
                  ]}
                  onChange={(v) => placa({ lado: v })}
                />
              </>
            )}
          </div>
        </Secao>
      )
    }

    const layout = layoutDe(tema, pid)
    const editarLayout = (linhaA: HudCampoId[], linhaB: HudCampoId[]) =>
      pagina(pid, (x) => ({ ...x, linhaA, linhaB }))
    const layoutMudou =
      aceitaCampos(pid) && layout != null &&
      (layout.linhaA.join() !== LAYOUT_PADRAO[pid].linhaA.join() || layout.linhaB.join() !== LAYOUT_PADRAO[pid].linhaB.join())

    return (
      <Secao titulo={`Página — ${tituloDe(tema, pid)}`} dica={p.ativa ? undefined : 'Fora do rodízio: o volante não chega nela.'}>
        <div>
          <label className="text-xs font-medium text-text-secondary mb-1.5 block">Nome no alto da página</label>
          <TextInput
            value={p.titulo ?? ''}
            placeholder={tituloDeFabrica(pid)}
            maxLength={LIMITES.tituloMax}
            onChange={(e) => pagina(pid, (x) => ({ ...x, titulo: e.target.value || undefined }), `titulo-${pid}`)}
          />
        </div>
        <CorComAtalhos
          label="Cor dos valores"
          valor={corDaPagina(tema, pid)}
          atalhos={CORES_DE_VALOR}
          onChange={(c) => pagina(pid, (x) => ({ ...x, cor: c === '#FFFFFF' ? undefined : c }))}
        />
        <SliderField
          label="Tamanho do conteúdo"
          value={p.escala ?? LIMITES.escala.padrao}
          min={LIMITES.escala.min}
          max={LIMITES.escala.max}
          unit="%"
          onChange={(v) => pagina(pid, (x) => ({ ...x, escala: v === LIMITES.escala.padrao ? undefined : v }), `escala-${pid}`)}
        />
        {aceitaCampos(pid) && layout && (
          <div className="border-t border-white/10 pt-3 space-y-3">
            <div className="flex items-center justify-between gap-2">
              <div>
                <div className="text-xs font-semibold text-text uppercase tracking-wide">O que a página mostra</div>
                <p className="text-[11px] text-text-tertiary leading-snug">
                  Em cima, até {LIMITES.maxA} valores grandes; embaixo, até {LIMITES.maxB} menores.
                </p>
              </div>
              {layoutMudou && (
                <button
                  type="button"
                  onClick={() => pagina(pid, (x) => ({ ...x, linhaA: undefined, linhaB: undefined }))}
                  className="flex items-center gap-1 text-[11px] text-text-tertiary hover:text-text flex-shrink-0"
                  title="Voltar aos campos de fábrica"
                >
                  <RotateCcwIcon size={12} />
                  Fábrica
                </button>
              )}
            </div>
            <div className="space-y-2">
              <div className="text-[11px] font-medium text-text-secondary">Linha de cima</div>
              {layout.linhaA.map((campo, i) => (
                <SeletorDeCampo
                  key={`a${i}`}
                  valor={campo}
                  onChange={(c) => editarLayout(layout.linhaA.map((x, j) => (j === i ? c : x)), layout.linhaB)}
                  onRemover={layout.linhaA.length > 1 ? () => editarLayout(layout.linhaA.filter((_, j) => j !== i), layout.linhaB) : undefined}
                />
              ))}
              {layout.linhaA.length < LIMITES.maxA && (
                <Button variant="ghost" size="sm" fullWidth={false} icon={PlusIcon}
                  onClick={() => editarLayout([...layout.linhaA, 'HORA'], layout.linhaB)}>
                  Valor grande
                </Button>
              )}
            </div>
            <div className="space-y-2">
              <div className="text-[11px] font-medium text-text-secondary">Linha de baixo</div>
              {layout.linhaB.length === 0 && (
                <p className="text-[11px] text-text-tertiary leading-snug">
                  Vazia: os valores de cima descem para o meio e crescem.
                </p>
              )}
              {layout.linhaB.map((campo, i) => (
                <SeletorDeCampo
                  key={`b${i}`}
                  valor={campo}
                  onChange={(c) => editarLayout(layout.linhaA, layout.linhaB.map((x, j) => (j === i ? c : x)))}
                  onRemover={() => editarLayout(layout.linhaA, layout.linhaB.filter((_, j) => j !== i))}
                />
              ))}
              {layout.linhaB.length < LIMITES.maxB && (
                <Button variant="ghost" size="sm" fullWidth={false} icon={PlusIcon}
                  onClick={() => editarLayout(layout.linhaA, [...layout.linhaB, 'TEMP_FORA'])}>
                  Valor pequeno
                </Button>
              )}
            </div>
          </div>
        )}
      </Secao>
    )
  }

  const abas: { id: AlvoDaPrevia; nome: string; ativa: boolean }[] = [
    ...tema.paginas.map((p) => ({ id: p.pagina as AlvoDaPrevia, nome: tituloDe(tema, p.pagina), ativa: p.ativa })),
    { id: 'ALERTA', nome: 'Alerta', ativa: tema.alertas },
  ]

  // A mesma barra do editor do painel: nome à esquerda com largura flexível (o nome longo não
  // corta mais em "Padrão DeepGWT (cópi…"), ações do tema à direita, prévia embaixo.
  const barra = (
    <div className="space-y-2">
      <div className="flex items-center gap-2 flex-wrap">
        <button
          type="button"
          onClick={() => navigate('/temas?tipo=hud')}
          className="flex items-center gap-0.5 text-xs text-text-tertiary hover:text-text flex-shrink-0"
        >
          <ChevronLeftIcon size={14} /> Temas
        </button>
        <input
          value={tema.nome}
          maxLength={LIMITES.nomeMax}
          onChange={(e) => atualizar({ ...tema, nome: e.target.value }, 'nome')}
          aria-label="Nome do tema"
          className="flex-1 min-w-[10rem] max-w-md bg-transparent border border-transparent hover:border-white/10 focus:border-primary/60 rounded-lg px-2 py-1 text-sm font-semibold text-text outline-none"
        />
        <div className="flex-1" />
        <Button variant="ghost" size="sm" icon={DownloadIcon} fullWidth={false} onClick={() => void exportar()}>
          Exportar
        </Button>
        <Button variant="secondary" size="sm" icon={CheckIcon} fullWidth={false} onClick={salvar}>
          {salvo ? 'Salvo' : 'Salvar'}
        </Button>
        <Button
          variant="primary"
          size="sm"
          icon={SendIcon}
          fullWidth={false}
          onClick={() => {
            setEnvio('idle')
            setErroEnvio(null)
            setShowEnvio(true)
            link.preparar()
          }}
        >
          Aplicar no carro
        </Button>
      </div>
      <div className="flex items-center gap-2 flex-wrap">
        <div className="flex items-center gap-0.5">
          <button type="button" onClick={desfazer} disabled={passado.length === 0} className="p-1.5 rounded-lg text-text-secondary hover:text-text hover:bg-white/[0.07] disabled:opacity-30" title="Desfazer (Ctrl/Cmd + Z)" aria-label="Desfazer">
            <Undo2Icon size={15} />
          </button>
          <button type="button" onClick={refazer} disabled={futuro.length === 0} className="p-1.5 rounded-lg text-text-secondary hover:text-text hover:bg-white/[0.07] disabled:opacity-30" title="Refazer (Ctrl/Cmd + Shift + Z)" aria-label="Refazer">
            <Redo2Icon size={15} />
          </button>
        </div>
        <span className="w-px h-5 bg-white/10" />
        <label className="flex items-center gap-1.5 text-[11px] text-text-tertiary">
          Cenário
          <select
            value={cenarioId}
            onChange={(e) => setCenarioId(e.target.value)}
            className="bg-black/25 border border-white/10 rounded-lg px-2 py-1 text-xs text-text outline-none focus:border-primary/60"
          >
            {CENARIOS.map((c) => (
              <option key={c.id} value={c.id}>{c.nome}</option>
            ))}
          </select>
        </label>
        <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-black/25 border border-white/10" role="group" aria-label="Fundo da prévia">
          {([
            { value: 'hud' as const, label: 'Tela do HUD' },
            { value: 'parabrisa' as const, label: 'No para-brisa' },
          ]).map((o) => (
            <button
              key={o.value}
              type="button"
              onClick={() => setFundo(o.value)}
              className={`px-2 py-0.5 rounded-md text-[11px] ${fundo === o.value ? 'bg-white/15 text-text' : 'text-text-tertiary hover:text-text'}`}
            >
              {o.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  )

  // Esquerda: as páginas do rodízio na ordem do volante, com liga/desliga — o mesmo lugar em que o
  // editor do painel lista páginas e camadas.
  const colunaDasPaginas = (
    <div className="space-y-4 select-none">
      <p className="text-[11px] text-text-tertiary leading-snug">
        A ordem em que os direcionais do volante andam. A última ativa não pode sair.
      </p>
      <div className="space-y-1">
        {tema.paginas.map((p, i) => (
          <div
            key={p.pagina}
            className={`flex items-center gap-1.5 rounded-lg px-1.5 py-1 border ${alvo === p.pagina ? 'border-primary/60 bg-primary/10' : 'border-transparent hover:bg-white/[0.05]'}`}
          >
            <div className="flex flex-col">
              <button type="button" aria-label="Subir" disabled={i === 0} onClick={() => mover(p.pagina, -1)}
                className="p-0.5 text-text-tertiary hover:text-text disabled:opacity-25">
                <ArrowUpIcon size={11} />
              </button>
              <button type="button" aria-label="Descer" disabled={i === tema.paginas.length - 1} onClick={() => mover(p.pagina, 1)}
                className="p-0.5 text-text-tertiary hover:text-text disabled:opacity-25">
                <ArrowDownIcon size={11} />
              </button>
            </div>
            <button type="button" onClick={() => setAlvo(p.pagina)} className="flex-1 min-w-0 text-left">
              <div className={`text-xs truncate ${p.ativa ? 'text-text' : 'text-text-tertiary line-through decoration-white/30'}`}>{tituloDe(tema, p.pagina)}</div>
              {p.titulo && <div className="text-[10px] text-text-tertiary truncate">{tituloDeFabrica(p.pagina)}</div>}
            </button>
            <Interruptor
              titulo=""
              ligado={p.ativa}
              desabilitado={p.ativa && ativas <= 1}
              onChange={(v) => pagina(p.pagina, (x) => ({ ...x, ativa: v }))}
            />
          </div>
        ))}
        <button
          type="button"
          onClick={() => setAlvo('ALERTA')}
          className={`w-full text-left rounded-lg px-2.5 py-1.5 border text-xs ${alvo === 'ALERTA' ? 'border-primary/60 bg-primary/10 text-text' : 'border-transparent hover:bg-white/[0.05] text-text-secondary'}`}
        >
          Cortina de alerta{tema.alertas ? '' : ' (desligada)'}
        </button>
      </div>
    </div>
  )

  const centro = (
    <div className="space-y-4 max-w-[980px] mx-auto">
      {aviso && (
        <Banner tone="info">
          {aviso}{' '}
          <button type="button" onClick={() => setAviso(null)} className="underline underline-offset-2 hover:text-text">
            Entendi
          </button>
        </Banner>
      )}
      <div className="flex flex-wrap gap-1 p-1 rounded-xl bg-black/25 border border-white/10">
        {abas.map((a) => (
          <button
            key={a.id}
            type="button"
            onClick={() => setAlvo(a.id)}
            className={`flex-1 min-w-fit rounded-lg px-3 py-1.5 text-xs font-medium transition-colors ${
              alvo === a.id ? 'bg-primary text-white' : a.ativa ? 'text-text-secondary hover:bg-white/[0.07]' : 'text-text-tertiary/60 hover:bg-white/[0.05] line-through decoration-white/30'
            }`}
            title={a.ativa ? undefined : a.id === 'ALERTA' ? 'Alertas desligados' : 'Fora do rodízio'}
          >
            {a.nome}
          </button>
        ))}
      </div>

      <div className="rounded-2xl overflow-hidden border border-white/10 shadow-[0_20px_60px_-20px_rgba(0,0,0,0.8)]">
        <HudPreview tema={tema} alvo={alvo} cenario={cenario} fundo={fundo} />
      </div>

      <div>
        <div className="text-[11px] font-medium text-text-secondary mb-2">
          Rodízio do volante — {paginasVisiveis(tema).length} página{paginasVisiveis(tema).length === 1 ? '' : 's'}
        </div>
        <div className="grid grid-cols-3 sm:grid-cols-5 gap-2">
          {paginasVisiveis(tema).map((pid) => (
            <button
              key={pid}
              type="button"
              onClick={() => setAlvo(pid)}
              className={`rounded-lg overflow-hidden border transition-colors ${alvo === pid ? 'border-primary' : 'border-white/10 hover:border-white/30'}`}
            >
              <HudPreview tema={tema} alvo={pid} cenario={cenario} />
              <div className="text-[10px] text-text-tertiary py-1 truncate px-1">{tituloDe(tema, pid)}</div>
            </button>
          ))}
        </div>
      </div>

      <p className="text-[11px] text-text-tertiary leading-relaxed">
        A prévia usa a Roboto do Android e as mesmas medidas do desenho do carro (480×240). No vidro o HUD é
        ampliado pela ótica: o que parece pequeno aqui fica maior lá. {cenario.nome}: situação da prévia,
        trocada no seletor Cenário da barra.
      </p>
    </div>
  )

  const inspetor = (
    <div className="space-y-4">
      {painelDaPagina()}

      <Secao titulo="Estilo" dica="Vale para todas as páginas.">
        <Segmented
          label="Letra"
          size="sm"
          value={tema.estilo.fonte}
          options={[
            { value: 'padrao', label: 'Roboto' },
            { value: 'condensada', label: 'Roboto Condensada', title: 'Mais número por largura' },
          ]}
          onChange={(v) => estilo({ fonte: v })}
        />
        <SliderField
          label={`Espessura — ${nomeDoPeso(tema.estilo.peso)}`}
          value={tema.estilo.peso}
          min={LIMITES.peso.min}
          max={LIMITES.peso.max}
          step={LIMITES.peso.passo}
          onChange={(v) => estilo({ peso: v }, 'peso')}
        />
        <CorComAtalhos
          label="Cor dos rótulos e unidades"
          valor={tema.estilo.corRotulo}
          atalhos={CORES_DE_ROTULO}
          onChange={(c) => estilo({ corRotulo: c })}
        />
        <Interruptor titulo="Nome da página no alto" ligado={tema.estilo.cabecalho} onChange={(v) => estilo({ cabecalho: v })} />
        <Interruptor
          titulo="Pontinhos de página"
          dica="Mostram em qual página o HUD está e que o volante alcança as outras."
          ligado={tema.estilo.pontos}
          onChange={(v) => estilo({ pontos: v })}
        />
      </Secao>
    </div>
  )

  return (
    <>
      <EditorShell
        barra={barra}
        esquerdaRecolhida={prefs.esquerdaRecolhida}
        onRecolherEsquerda={(v) => definirPrefs({ esquerdaRecolhida: v })}
        esquerda={{ titulo: 'Páginas do HUD', icone: LayersIcon, conteudo: colunaDasPaginas }}
        centro={centro}
        direita={{ titulo: 'Inspetor', conteudo: inspetor }}
      />

      {showEnvio && (
        <Modal onClose={() => (envio === 'busy' ? undefined : setShowEnvio(false))}>
          <h2 className="text-lg font-semibold text-text mb-2">Aplicar no carro</h2>
          <p className="text-sm text-text-secondary mb-4">
            O HUD do carro troca na hora, pelo Wi-Fi do carro. Os controles do HUD na tela Telas continuam
            ajustando o tema, e "Voltar ao padrão" lá desfaz tudo.
          </p>
          <CarLinkPanel link={link} ocupado={envio === 'busy'} />
          {envio === 'busy' && <Banner tone="info" className="mb-4">Aplicando "{tema.nome}"...</Banner>}
          {envio === 'done' && <Banner tone="success" className="mb-4">Aplicado. O HUD já está com "{tema.nome}".</Banner>}
          {envio === 'error' && <Banner tone="error" className="mb-4">{erroEnvio}</Banner>}
          <div className="flex gap-3">
            <Button variant="secondary" disabled={envio === 'busy'} onClick={() => setShowEnvio(false)}>
              {envio === 'done' ? 'Fechar' : 'Cancelar'}
            </Button>
            <Button variant="primary" disabled={link.status !== 'ok' || envio === 'busy'} onClick={() => void enviar()}>
              Aplicar
            </Button>
          </div>
        </Modal>
      )}
    </>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
