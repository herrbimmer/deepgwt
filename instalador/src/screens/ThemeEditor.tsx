// O editor de temas do painel.
//
// A tela é a moldura de três colunas (`theme-editor/editor/EditorShell`): páginas e camadas à
// esquerda, o canvas com a linha do tempo do cenário no centro, o inspetor sempre à vista na
// direita. Em cima, a barra com o que vale para o tema inteiro: nome, Básico/Avançado, desfazer,
// cenário, dia/noite/farol, "Ver no carro", histórico, salvar e enviar.
//
// Estado que é do TEMA mora no `ThemeSpec` e passa pelo desfazer. Estado que é da PRÉVIA (cenário,
// modo, valores fixados à mão) e do EDITOR (zoom, zonas, colunas) não entra no tema — o carro não
// tem como saber, e não precisa.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  BoxesIcon, CarIcon, CheckIcon, ChevronLeftIcon, DownloadIcon, HistoryIcon, LayersIcon, MoonIcon, PlusIcon,
  Redo2Icon, SendIcon, SunIcon, SunsetIcon, Trash2Icon, Undo2Icon,
} from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { Banner, Button, Modal } from '../ui'
import { CarLinkPanel } from '../components'
import { useCarLink } from '../hooks/useCarLink'
import { ClusterCanvas, type ModoDeSelecao } from '../theme-editor/ClusterCanvas'
import { ElementPanel, ITENS_PARA_ADICIONAR } from '../theme-editor/panels/ElementPanel'
import { ThemePanel } from '../theme-editor/panels/ThemePanel'
import { PainelBasicoDoElemento, PainelBasicoDoTema } from '../theme-editor/panels/PainelBasico'
import { EditorShell } from '../theme-editor/editor/EditorShell'
import { PainelDeCamadas } from '../theme-editor/editor/PainelDeCamadas'
import { PainelDeSelecao } from '../theme-editor/editor/PainelDeSelecao'
import { LinhaDoTempo } from '../theme-editor/editor/LinhaDoTempo'
import { MedidorDeCusto } from '../theme-editor/editor/MedidorDeCusto'
import { usePreferenciasDoEditor, type ModoDoEditor } from '../theme-editor/editor/preferencias'
import { CAR_THEME_ROOT, buildThemeBundle, bytesToBase64 } from '../theme-editor/exportTheme'
import type { Element, ElementKind, LayerGroup, PaletteMode, ThemePage, ThemeSpec } from '../theme-editor/model/spec'
import { createElement, newPage, novoId } from '../theme-editor/model/defaults'
import { firstLegalSlot, isLegal, movedTo } from '../theme-editor/model/collision'
import { agrupar, alinhar, desagrupar, distribuir, limparGrupos, type Alinhamento } from '../theme-editor/model/selecao'
import { estimarCusto } from '../theme-editor/model/custo'
import { getTheme, saveTheme } from '../theme-editor/storage'
import { apagarBloco, copiar, inserirElementos, lerCopia, listarBlocos, salvarBloco, type Bloco } from '../theme-editor/blocos'
import { escolherImagem } from '../theme-editor/imagens'
import { buildPayload } from '../theme-editor/emit/payload'
import { CENARIOS, CENARIO_POR_ID, ID_AO_VIVO, valoresNoInstante } from '../theme-editor/preview/cenarios'
import { criarCanalDePrevia } from '../theme-editor/preview/canal'
import { useVerNoCarro } from '../theme-editor/carro/useVerNoCarro'
import {
  EXTENSAO_DO_PACOTE, empacotar, gerarMiniatura, guardarMiniatura, listarVersoes, nomeDoArquivo,
  registrarVersao, type Versao,
} from '../theme-editor/compartilhar'
import { SAVE_MESSAGE, saveText } from '../lib/fileExchange'

type SendStatus = 'idle' | 'busy' | 'done' | 'error'

const LIMITE_HISTORICO = 80

const MODOS: { valor: PaletteMode; rotulo: string; icone: typeof SunIcon; dica: string }[] = [
  { valor: 'day', rotulo: 'Dia', icone: SunIcon, dica: 'Cores de dia' },
  { valor: 'night', rotulo: 'Noite', icone: MoonIcon, dica: 'Cores de noite — o "Noite" que o motorista escolhe nos ajustes do tema no carro' },
  { valor: 'headlight', rotulo: 'Farol', icone: SunsetIcon, dica: 'Cores com o farol aceso — automático, pela telemetria do carro' },
]

export const ThemeEditor = () => {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [prefs, definirPrefs] = usePreferenciasDoEditor()
  // Sem escolha gravada, o editor abre no básico (era o do modo Básico, o de quase todo mundo).
  const modoDoEditor: ModoDoEditor = prefs.modo ?? 'basico'
  const basico = modoDoEditor === 'basico'

  const [spec, setSpec] = useState<ThemeSpec | null>(null)
  const [activePageId, setActivePageId] = useState('')
  const [selecionados, setSelecionados] = useState<string[]>([])
  const [salvo, setSalvo] = useState(true)
  // Pilhas de desfazer/refazer. Guardam o `ThemeSpec` inteiro: ele é um objeto de dados puro de
  // alguns KB, e um instantâneo por edição é muito mais barato — em código e em bugs — do que
  // descrever cada operação como um par fazer/desfazer. O teto existe para a memória não crescer
  // sem fim numa sessão longa de edição.
  const [passado, setPassado] = useState<ThemeSpec[]>([])
  const [futuro, setFuturo] = useState<ThemeSpec[]>([])
  // Digitar o nome do tema chama `atualizar` a cada tecla. Sem agrupar, um nome de 20 letras
  // consumia 20 posições do histórico e "desfazer" virava "apagar uma letra".
  const grupoAnterior = useRef<string | null>(null)

  // Prévia
  const [cenarioId, setCenarioId] = useState('cidade')
  const [modoDaPrevia, setModoDaPrevia] = useState<PaletteMode>('day')
  const [fixados, setFixados] = useState<Record<string, string>>({})
  const [verAbertura, setVerAbertura] = useState(0)
  const [semDado, setSemDado] = useState<string[]>([])
  const canal = useMemo(
    () => criarCanalDePrevia({ valores: valoresNoInstante(CENARIOS[1], 0), modo: 'day' }),
    [],
  )

  // Modais e avisos
  const [showAdd, setShowAdd] = useState(false)
  const [showSend, setShowSend] = useState(false)
  const [showHistorico, setShowHistorico] = useState(false)
  const [showAoVivo, setShowAoVivo] = useState(false)
  const [sendStatus, setSendStatus] = useState<SendStatus>('idle')
  const [sendStep, setSendStep] = useState('')
  const [sendError, setSendError] = useState<string | null>(null)
  const [pesadoConfirmado, setPesadoConfirmado] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const [avisoDaSelecao, setAvisoDaSelecao] = useState<string | null>(null)
  const [blocos, setBlocos] = useState<Bloco[]>(() => listarBlocos())
  const [versoes, setVersoes] = useState<Versao[] | null>(null)

  // "Ver no carro"
  const [aoVivoLigado, setAoVivoLigado] = useState(false)
  const link = useCarLink()
  const noCarro = useVerNoCarro(spec ?? (null as unknown as ThemeSpec), aoVivoLigado && spec !== null, link.carIp, link.status === 'ok')

  useEffect(() => {
    if (!id) return
    const achado = getTheme(id)
    if (!achado) {
      navigate('/temas')
      return
    }
    setSpec(achado)
    setActivePageId(achado.pages[0].id)
  }, [id, navigate])

  const page = useMemo(
    () => spec?.pages.find((p) => p.id === activePageId) ?? spec?.pages[0] ?? null,
    [spec, activePageId],
  )

  const custo = useMemo(() => (spec ? estimarCusto(spec) : null), [spec])
  const chavesDoTema = useMemo(() => (spec ? buildPayload(spec).subscribeKeys : []), [spec])

  // Os atalhos precisam ser registrados ANTES do retorno antecipado abaixo — hook não pode ficar
  // depois de um `return`. Como as funções que eles chamam só existem depois, o ouvinte lê de uma
  // referência que cada render mantém atualizada; assim ele é montado uma vez só e nunca vê uma
  // versão velha do tema.
  const acoes = useRef<Record<string, () => void>>({})
  useEffect(() => {
    const aoTeclar = (ev: KeyboardEvent) => {
      const alvo = ev.target as HTMLElement | null
      // Dentro de um campo de texto, Cmd+Z é o desfazer DO CAMPO, que é o que a pessoa espera.
      if (alvo && (/^(INPUT|TEXTAREA|SELECT)$/.test(alvo.tagName) || alvo.isContentEditable)) return
      const tecla = ev.key.toLowerCase()
      const cmd = ev.metaKey || ev.ctrlKey
      const a = acoes.current
      if (!cmd) {
        if (tecla === 'delete' || tecla === 'backspace') { ev.preventDefault(); a.apagar?.() }
        else if (tecla === 'escape') a.limparSelecao?.()
        return
      }
      if (tecla === 'z' && !ev.shiftKey) { ev.preventDefault(); a.desfazer?.() }
      else if ((tecla === 'z' && ev.shiftKey) || tecla === 'y') { ev.preventDefault(); a.refazer?.() }
      else if (tecla === 'd') { ev.preventDefault(); a.duplicar?.() }
      else if (tecla === 'c') { a.copiar?.() }
      else if (tecla === 'v') { ev.preventDefault(); a.colar?.() }
      else if (tecla === 'a') { ev.preventDefault(); a.tudo?.() }
      else if (tecla === 'g' && ev.shiftKey) { ev.preventDefault(); a.desagrupar?.() }
      else if (tecla === 'g') { ev.preventDefault(); a.agrupar?.() }
      else if (tecla === 's') { ev.preventDefault(); a.salvar?.() }
    }
    window.addEventListener('keydown', aoTeclar)
    return () => window.removeEventListener('keydown', aoTeclar)
  }, [])

  // Sair com "Ver no carro" ligado: a limpeza do efeito devolve o tema de antes ao carro.
  useEffect(() => {
    if (!aoVivoLigado) return
    const aoFechar = () => void invoke('remote_temas_rascunho', { carIp: link.carIp, ativar: false }).catch(() => {})
    window.addEventListener('beforeunload', aoFechar)
    return () => window.removeEventListener('beforeunload', aoFechar)
  }, [aoVivoLigado, link.carIp])

  const onSelecionar = useCallback((ids: string[], modo: ModoDeSelecao = 'substituir') => {
    setAvisoDaSelecao(null)
    setSelecionados((atual) => {
      if (modo === 'substituir') return ids
      if (modo === 'somar') return [...new Set([...atual, ...ids])]
      const n = new Set(atual)
      for (const i of ids) {
        if (n.has(i)) n.delete(i)
        else n.add(i)
      }
      return [...n]
    })
  }, [])

  const [arranjoLargo, setArranjoLargo] = useState(true)

  if (!spec || !page || !custo) return null

  /**
   * @param grupo edições consecutivas com o mesmo rótulo contam como uma só no histórico.
   */
  // Escritos como manipuladores diretos, e não com atualizadores de estado aninhados: cada um
  // mexe em três estados de uma vez, e efeito dentro de atualizador roda duas vezes no StrictMode.
  const atualizar = (proximo: ThemeSpec, grupo: string | null = null) => {
    const mesmoGrupo = grupo !== null && grupo === grupoAnterior.current && passado.length > 0
    if (!mesmoGrupo) setPassado([...passado, spec].slice(-LIMITE_HISTORICO))
    grupoAnterior.current = grupo
    if (futuro.length > 0) setFuturo([])
    setSpec(proximo)
    setSalvo(false)
  }

  const desfazer = () => {
    if (passado.length === 0) return
    setPassado(passado.slice(0, -1))
    setFuturo([spec, ...futuro].slice(0, LIMITE_HISTORICO))
    setSpec(passado[passado.length - 1])
    setSalvo(false)
    setSelecionados([])
    grupoAnterior.current = null
  }

  const refazer = () => {
    if (futuro.length === 0) return
    setPassado([...passado, spec].slice(-LIMITE_HISTORICO))
    setFuturo(futuro.slice(1))
    setSpec(futuro[0])
    setSalvo(false)
    setSelecionados([])
    grupoAnterior.current = null
  }

  const trocarPaginaInteira = (fn: (p: ThemePage) => ThemePage, grupo: string | null = null) =>
    atualizar({ ...spec, pages: spec.pages.map((p) => (p.id === page.id ? limparGrupos(fn(p)) : p)) }, grupo)
  const trocarPagina = (fn: (els: Element[]) => Element[], grupo: string | null = null) =>
    trocarPaginaInteira((p) => ({ ...p, elements: fn(p.elements) }), grupo)

  const escolhidos = page.elements.filter((el) => selecionados.includes(el.id))
  const selecionado = escolhidos.length === 1 ? escolhidos[0] : null

  const handleSalvar = () => {
    const gravado = saveTheme(spec)
    setSpec(gravado)
    setSalvo(true)
    void registrarVersao(gravado)
    // A miniatura da lista sai do tema desenhado; demora meio segundo e não pode segurar a tela.
    void gerarMiniatura(gravado).then((src) => guardarMiniatura(gravado.id, src)).catch(() => {})
  }

  const adicionar = async (kind: ElementKind, style?: string) => {
    const maxZ = page.elements.length ? Math.max(...page.elements.map((e) => e.place.z)) : 0
    let novo = createElement({ kind, style, zone: 'free' }, maxZ)
    if (kind === 'image') {
      try {
        const lida = await escolherImagem()
        if (!lida) return
        novo = { ...novo, name: lida.fileName.replace(/\.[^.]+$/, '') || 'Imagem', src: lida.src, fileName: lida.fileName, bytes: lida.bytes, aspect: lida.aspect } as Element
      } catch (e) {
        setAviso(e instanceof Error ? e.message : String(e))
        return
      }
    }
    const vaga = isLegal(novo) ? null : firstLegalSlot(spec, novo)
    const posicionado = vaga ? movedTo(novo, vaga) : novo
    trocarPagina((els) => [...els, posicionado])
    setSelecionados([posicionado.id])
    setShowAdd(false)
  }

  const inserirBloco = (b: Bloco) => {
    const r = inserirElementos(spec, page, b.elementos, { grupo: b.nome })
    atualizar(r.spec)
    setSelecionados(r.ids)
    setShowAdd(false)
  }

  /**
   * Cópias deslocadas 24 px, uma camada acima e já selecionadas.
   *
   * O deslocamento existe para a cópia não nascer exatamente embaixo do original — sem ele o
   * clique seguinte pegaria um dos dois sem dizer qual. Vinte e quatro pixels é o suficiente para
   * a borda de seleção aparecer inteira mesmo no elemento mais estreito da tabela de pegadas.
   */
  const duplicarElementos = (ids: string[]) => {
    const originais = page.elements.filter((el) => ids.includes(el.id))
    if (!originais.length) return
    const r = inserirElementos(spec, page, originais, { deslocar: 24 })
    atualizar({
      ...r.spec,
      pages: r.spec.pages.map((p) =>
        p.id !== page.id ? p : {
          ...p,
          elements: p.elements.map((el) => (r.ids.includes(el.id) ? ({ ...el, name: `${el.name} (cópia)` } as Element) : el)),
        }),
    })
    setSelecionados(r.ids)
  }

  const apagarElementos = (ids: string[]) => {
    if (!ids.length) return
    trocarPagina((els) => els.filter((el) => !ids.includes(el.id)))
    setSelecionados([])
  }

  const duplicarPagina = (pid: string) => {
    const original = spec.pages.find((p) => p.id === pid)
    if (!original) return
    const grupos = new Map((original.groups ?? []).map((g) => [g.id, novoId('grp')]))
    const copia: ThemePage = {
      ...(structuredClone(original) as ThemePage),
      id: novoId('page'),
      name: `${original.name} (cópia)`,
      groups: (original.groups ?? []).map((g) => ({ ...g, id: grupos.get(g.id)! })),
      elements: original.elements.map((el) => ({
        ...(structuredClone(el) as Element),
        id: novoId('el'),
        groupId: el.groupId ? grupos.get(el.groupId) : undefined,
      } as Element)),
    }
    const onde = spec.pages.findIndex((p) => p.id === pid)
    const paginas = [...spec.pages]
    paginas.splice(onde + 1, 0, copia)
    atualizar({ ...spec, pages: paginas })
    setActivePageId(copia.id)
    setSelecionados([])
  }

  const reordenar = (elId: string, direcao: 'up' | 'down') => {
    const ordenados = [...page.elements].sort((a, b) => b.place.z - a.place.z)
    const i = ordenados.findIndex((e) => e.id === elId)
    const j = direcao === 'up' ? i - 1 : i + 1
    if (i === -1 || j < 0 || j >= ordenados.length) return
    const za = ordenados[i].place.z
    const zb = ordenados[j].place.z
    trocarPagina((els) =>
      els.map((el) =>
        el.id === ordenados[i].id
          ? ({ ...el, place: { ...el.place, z: zb } } as Element)
          : el.id === ordenados[j].id
            ? ({ ...el, place: { ...el.place, z: za } } as Element)
            : el,
      ),
    )
  }

  const adicionarPagina = () => {
    const nova = newPage(`Auxiliar ${spec.pages.length}`)
    atualizar({ ...spec, pages: [...spec.pages, nova] })
    setActivePageId(nova.id)
    setSelecionados([])
    setAviso(
      'Páginas extras só trocam no carro com o "menu customizado" ligado no app — é ele que libera ' +
      'as teclas do volante para o tema. Uma página também pode abrir sozinha por condição (marcha ' +
      'à ré, modo Sport): escolha na aba Página do inspetor.',
    )
  }

  const moverVarios = (movimentos: { id: string; at: { x: number; y: number } }[]) => {
    const alvo = new Map(movimentos.map((m) => [m.id, m.at]))
    trocarPagina((els) => els.map((el) => (alvo.has(el.id) ? movedTo(el, alvo.get(el.id)!) : el)))
  }

  const aplicarNaSelecao = (fn: (els: Element[]) => { elementos: Element[]; barrados: { nome: string; zona: string }[] }) => {
    const r = fn(page.elements)
    trocarPagina(() => r.elementos)
    setAvisoDaSelecao(
      r.barrados.length
        ? `${r.barrados.map((b) => b.nome).join(', ')} ficou onde estava: cairia sobre ${r.barrados[0].zona} do veículo.`
        : null,
    )
  }

  const agruparSelecao = () => {
    if (escolhidos.length < 2) return
    const r = agrupar(page, escolhidos.map((e) => e.id))
    atualizar({ ...spec, pages: spec.pages.map((p) => (p.id === page.id ? limparGrupos(r.page) : p)) })
  }
  const grupoDaSelecao = escolhidos.length && escolhidos.every((e) => e.groupId && e.groupId === escolhidos[0].groupId)
    ? escolhidos[0].groupId! : null
  const desagruparSelecao = () => {
    if (!grupoDaSelecao) return
    atualizar({ ...spec, pages: spec.pages.map((p) => (p.id === page.id ? desagrupar(p, grupoDaSelecao) : p)) })
  }

  const colar = () => {
    const c = lerCopia()
    if (!c) return
    const mesmaPagina = c.temaId === spec.id && c.paginaId === page.id
    const r = inserirElementos(spec, page, c.elementos, { deslocar: mesmaPagina ? 24 : 0 })
    atualizar(r.spec)
    setSelecionados(r.ids)
  }

  // Render é o único lugar em que estas funções existem já ligadas ao tema atual.
  acoes.current = {
    desfazer,
    refazer,
    duplicar: () => duplicarElementos(selecionados),
    apagar: () => apagarElementos(selecionados),
    limparSelecao: () => setSelecionados([]),
    copiar: () => escolhidos.length && copiar({ temaId: spec.id, paginaId: page.id, elementos: escolhidos }),
    colar,
    tudo: () => setSelecionados(page.elements.filter((e) => !e.locked && !e.hidden).map((e) => e.id)),
    agrupar: agruparSelecao,
    desagrupar: desagruparSelecao,
    salvar: handleSalvar,
  }

  const enviar = async () => {
    setSendStatus('busy')
    setSendError(null)
    try {
      setSendStep('Gerando os arquivos do tema...')
      const pacote = await buildThemeBundle(spec)
      const base = `${CAR_THEME_ROOT}/${pacote.folderName}`
      for (const arquivo of pacote.files) {
        setSendStep(`Enviando ${arquivo.name}...`)
        await invoke('remote_upload_bytes_b64', {
          carIp: link.carIp,
          remotePath: `${base}/${arquivo.name}`,
          dataB64: bytesToBase64(arquivo.bytes),
        })
      }
      handleSalvar()
      setSendStatus('done')
    } catch (e) {
      setSendError(e instanceof Error ? e.message : String(e))
      setSendStatus('error')
    }
  }

  const exportar = async (tipo: 'json' | 'pacote') => {
    const r = tipo === 'json'
      ? await saveText(`${spec.folderName}.json`, JSON.stringify(spec, null, 2), 'json')
      : await saveText(nomeDoArquivo(spec, EXTENSAO_DO_PACOTE), empacotar(spec, await gerarMiniatura(spec).catch(() => null)), 'json')
    if (r.ok) setAviso(SAVE_MESSAGE[r.how](r.path))
    else if (r.how !== 'cancelled') setAviso(`Não foi possível salvar: ${r.error ?? 'erro desconhecido'}`)
  }

  const trocarCenario = (idNovo: string) => {
    setCenarioId(idNovo)
    const c = CENARIO_POR_ID.get(idNovo)
    if (c?.modo) setModoDaPrevia(c.modo)
  }

  const fixar = (chave: string, valor: string | null) =>
    setFixados((f) => {
      const n = { ...f }
      if (valor === null) delete n[chave]
      else n[chave] = valor
      return n
    })

  // ---- inspetor ------------------------------------------------------------------------------

  const inspetor = basico ? (
    selecionado ? (
      <PainelBasicoDoElemento
        spec={spec}
        element={selecionado}
        onChange={(el) => trocarPagina((els) => els.map((x) => (x.id === el.id ? el : x)))}
        onAvancado={() => definirPrefs({ modo: 'avancado' })}
      />
    ) : escolhidos.length > 1 ? (
      <PainelDeSelecao
        elementos={escolhidos}
        onAlinhar={(como: Alinhamento) => aplicarNaSelecao((els) => alinhar(els, selecionados, como))}
        onDistribuir={(eixo) => aplicarNaSelecao((els) => distribuir(els, selecionados, eixo))}
        onAgrupar={agruparSelecao}
        onDesagrupar={grupoDaSelecao ? desagruparSelecao : null}
        onDuplicar={() => duplicarElementos(selecionados)}
        onApagar={() => apagarElementos(selecionados)}
        onSalvarBloco={(nome) => {
          salvarBloco(nome, escolhidos)
          setBlocos(listarBlocos())
        }}
        aviso={avisoDaSelecao}
      />
    ) : (
      <PainelBasicoDoTema spec={spec} onChange={(s) => atualizar(s)} onAssistente={() => navigate('/temas?assistente=1')} />
    )
  ) : selecionado ? (
    <ElementPanel
      spec={spec}
      element={selecionado}
      onChange={(atualizado) => trocarPagina((els) => els.map((el) => (el.id === atualizado.id ? atualizado : el)))}
      onChangeSpec={(s) => atualizar(s)}
      onDelete={() => apagarElementos([selecionado.id])}
      fixados={fixados}
      onFixar={fixar}
    />
  ) : escolhidos.length > 1 ? (
    <PainelDeSelecao
      elementos={escolhidos}
      onAlinhar={(como: Alinhamento) => aplicarNaSelecao((els) => alinhar(els, selecionados, como))}
      onDistribuir={(eixo) => aplicarNaSelecao((els) => distribuir(els, selecionados, eixo))}
      onAgrupar={agruparSelecao}
      onDesagrupar={grupoDaSelecao ? desagruparSelecao : null}
      onDuplicar={() => duplicarElementos(selecionados)}
      onApagar={() => apagarElementos(selecionados)}
      onSalvarBloco={(nome) => {
        salvarBloco(nome, escolhidos)
        setBlocos(listarBlocos())
        setAviso(`Bloco "${nome.trim() || 'Bloco sem nome'}" salvo. Ele aparece em Adicionar elemento, em qualquer tema.`)
      }}
      aviso={avisoDaSelecao}
    />
  ) : (
    <ThemePanel spec={spec} page={page} onChange={atualizar} />
  )

  // ---- barra ---------------------------------------------------------------------------------

  const estadoAoVivo =
    noCarro.fase === 'no-carro' ? { cor: 'bg-success', texto: 'No carro' }
      : noCarro.fase === 'enviando' ? { cor: 'bg-warning animate-pulse', texto: 'Enviando…' }
        : noCarro.fase === 'erro' ? { cor: 'bg-danger', texto: 'Erro' }
          : aoVivoLigado ? { cor: 'bg-warning', texto: 'Conectando…' }
            : { cor: 'bg-white/25', texto: 'Ver no carro' }

  const barra = (
    <div className="space-y-2">
      <div className="flex items-center gap-2 flex-wrap">
        <button
          type="button"
          onClick={() => navigate('/temas')}
          className="flex items-center gap-0.5 text-xs text-text-tertiary hover:text-text flex-shrink-0"
        >
          <ChevronLeftIcon size={14} /> Temas
        </button>
        <input
          value={spec.name}
          onChange={(e) => atualizar({ ...spec, name: e.target.value }, 'nome')}
          aria-label="Nome do tema"
          className="flex-1 min-w-[10rem] max-w-md bg-transparent border border-transparent hover:border-white/10 focus:border-primary/60 rounded-lg px-2 py-1 text-sm font-semibold text-text outline-none"
        />
        <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-black/25 border border-white/10" role="group" aria-label="Modo do editor">
          {(['basico', 'avancado'] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => definirPrefs({ modo: m })}
              className={`px-2.5 py-1 rounded-md text-[11px] font-medium ${modoDoEditor === m ? 'bg-primary text-white' : 'text-text-secondary hover:text-text'}`}
              title={m === 'basico' ? 'Cores, tamanhos e o que mostrar, sem nome de chave' : 'Tudo: dados, escala, marcações, regras'}
            >
              {m === 'basico' ? 'Básico' : 'Avançado'}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <Button variant="ghost" size="sm" icon={HistoryIcon} fullWidth={false} title="As últimas 20 gravações deste tema" onClick={() => {
          setShowHistorico(true)
          setVersoes(null)
          void listarVersoes(spec.id).then(setVersoes)
        }}>
          Histórico
        </Button>
        <Button variant="ghost" size="sm" icon={DownloadIcon} fullWidth={false} title="Pacote .opengw-tema, com imagens e miniatura, para compartilhar" onClick={() => void exportar('pacote')}>
          Exportar
        </Button>
        <Button variant="secondary" size="sm" icon={CheckIcon} fullWidth={false} title="Salvar (Ctrl/Cmd + S)" onClick={handleSalvar}>
          {salvo ? 'Salvo' : 'Salvar'}
        </Button>
        <Button
          variant="primary"
          size="sm"
          icon={SendIcon}
          fullWidth={false}
          onClick={() => {
            setSendStatus('idle')
            setSendError(null)
            setPesadoConfirmado(false)
            setShowSend(true)
            link.preparar()
          }}
        >
          Enviar
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
        <button type="button" onClick={() => setShowAdd(true)} className="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-white/[0.07] text-xs text-text hover:bg-white/[0.12]">
          <PlusIcon size={13} /> Elemento
        </button>
        <span className="w-px h-5 bg-white/10" />
        <label className="flex items-center gap-1.5 text-[11px] text-text-tertiary">
          Cenário
          <select
            value={cenarioId}
            onChange={(e) => trocarCenario(e.target.value)}
            className="bg-black/25 border border-white/10 rounded-lg px-2 py-1 text-xs text-text outline-none focus:border-primary/60"
          >
            {CENARIOS.map((c) => (
              <option key={c.id} value={c.id}>{c.nome}</option>
            ))}
            <option value={ID_AO_VIVO}>Ao vivo do carro</option>
          </select>
        </label>
        <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-black/25 border border-white/10" role="group" aria-label="Cores da prévia">
          {MODOS.map((m) => (
            <button
              key={m.valor}
              type="button"
              title={m.dica}
              onClick={() => setModoDaPrevia(m.valor)}
              className={`flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] ${modoDaPrevia === m.valor ? 'bg-white/15 text-text' : 'text-text-tertiary hover:text-text'}`}
            >
              <m.icone size={12} /> {m.rotulo}
            </button>
          ))}
        </div>
        <span className="w-px h-5 bg-white/10" />
        <button
          type="button"
          onClick={() => {
            if (aoVivoLigado) setAoVivoLigado(false)
            else {
              setShowAoVivo(true)
              link.preparar()
            }
          }}
          title="O painel do carro mostra este tema enquanto você edita. Ao desligar, o carro volta ao tema de antes."
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs border ${
            aoVivoLigado ? 'border-success/40 bg-success/10 text-text' : 'border-white/10 text-text-secondary hover:text-text'
          }`}
        >
          <span className={`w-2 h-2 rounded-full ${estadoAoVivo.cor}`} />
          <CarIcon size={13} />
          {estadoAoVivo.texto}
        </button>
      </div>
      {noCarro.fase === 'erro' && (
        <p className="text-[11px] text-danger">Ver no carro: {noCarro.mensagem}</p>
      )}
    </div>
  )

  return (
    <>
      <EditorShell
        barra={barra}
        esquerdaRecolhida={prefs.esquerdaRecolhida}
        onRecolherEsquerda={(v) => definirPrefs({ esquerdaRecolhida: v })}
        onArranjo={(a) => setArranjoLargo(a === 'largo')}
        esquerda={{
          titulo: 'Páginas e camadas',
          icone: LayersIcon,
          conteudo: (
            <PainelDeCamadas
              pages={spec.pages}
              activeId={page.id}
              onPagina={(pid) => {
                setActivePageId(pid)
                setSelecionados([])
              }}
              onNovaPagina={adicionarPagina}
              onDuplicarPagina={duplicarPagina}
              onRenomearPagina={(pid, name) => atualizar({ ...spec, pages: spec.pages.map((p) => (p.id === pid ? { ...p, name } : p)) })}
              onApagarPagina={(pid) => {
                if (spec.pages.length <= 1) return
                const restantes = spec.pages.filter((p) => p.id !== pid)
                atualizar({ ...spec, pages: restantes })
                if (activePageId === pid) setActivePageId(restantes[0].id)
                setSelecionados([])
              }}
              page={page}
              selecionados={selecionados}
              onSelecionar={onSelecionar}
              onReordenar={reordenar}
              onDuplicar={(elId) => duplicarElementos([elId])}
              onApagar={(elId) => apagarElementos([elId])}
              onBandeira={(ids, campo, valor) => {
                trocarPagina((els) => els.map((el) => (ids.includes(el.id) ? ({ ...el, [campo]: valor || undefined } as Element) : el)))
                if (valor) setSelecionados((s) => s.filter((x) => !ids.includes(x)))
              }}
              onGrupo={(g: LayerGroup) => trocarPaginaInteira((p) => ({ ...p, groups: (p.groups ?? []).map((x) => (x.id === g.id ? g : x)) }))}
              onAdicionar={() => setShowAdd(true)}
              simples={basico}
            />
          ),
        }}
        centro={
          <div className={arranjoLargo ? 'max-w-[1400px] mx-auto' : ''}>
            {aviso && (
              <Banner tone="info" className="mb-3">
                {aviso}{' '}
                <button type="button" onClick={() => setAviso(null)} className="underline underline-offset-2 hover:text-text">
                  Entendi
                </button>
              </Banner>
            )}
            <ClusterCanvas
              spec={spec}
              activePageId={page.id}
              selecionados={selecionados}
              onSelecionar={onSelecionar}
              onMover={moverVarios}
              canal={canal}
              mostrarZonas={prefs.mostrarZonas}
              onMostrarZonas={(v) => definirPrefs({ mostrarZonas: v })}
              zoom={prefs.zoom}
              onZoom={(z) => definirPrefs({ zoom: z })}
              verAbertura={verAbertura}
            />
            <LinhaDoTempo
              cenarioId={cenarioId}
              modo={modoDaPrevia}
              fixados={fixados}
              canal={canal}
              onVerAbertura={() => setVerAbertura((n) => n + 1)}
              chavesDoTema={chavesDoTema}
              onChavesSemDado={setSemDado}
            />
            {Object.keys(fixados).length > 0 && (
              <button type="button" onClick={() => setFixados({})} className="mt-1.5 text-[11px] text-text-tertiary hover:text-text">
                Soltar todos os valores fixados
              </button>
            )}
          </div>
        }
        direita={{
          titulo: 'Inspetor',
          conteudo: inspetor,
          rodape: <MedidorDeCusto custo={custo} semDado={semDado} />,
        }}
      />

      {showAdd && (
        <Modal onClose={() => setShowAdd(false)} maxWidth="max-w-2xl">
          <div className="max-h-[78vh] overflow-y-auto -mx-1 px-1">
            <h2 className="text-lg font-semibold text-text mb-3">Adicionar</h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2 mb-5">
              {ITENS_PARA_ADICIONAR.map((item) => (
                <button
                  key={`${item.kind}-${item.style ?? ''}`}
                  type="button"
                  onClick={() => void adicionar(item.kind, item.style)}
                  className="text-left rounded-xl border border-white/10 px-3 py-2.5 hover:border-primary/60 hover:bg-primary/10"
                >
                  <div className="text-sm text-text font-medium">{item.rotulo}</div>
                  <div className="text-[11px] text-text-tertiary leading-snug">{item.dica}</div>
                </button>
              ))}
            </div>
            <div className="flex items-center gap-2 mb-2">
              <BoxesIcon size={14} className="text-text-tertiary" />
              <h3 className="text-sm font-semibold text-text">Blocos</h3>
            </div>
            <p className="text-[11px] text-text-tertiary mb-2">
              Conjuntos prontos: entram na mesma posição em que foram montados e já agrupados. Os seus
              aparecem primeiro — salve uma seleção como bloco escolhendo vários elementos.
            </p>
            <div className="grid sm:grid-cols-2 gap-2 mb-5">
              {blocos.map((b) => (
                <div key={b.id} className="flex items-start gap-2 rounded-xl border border-white/10 px-3 py-2.5 hover:border-primary/60">
                  <button type="button" onClick={() => inserirBloco(b)} className="flex-1 text-left min-w-0">
                    <div className="text-sm text-text font-medium truncate">{b.nome}</div>
                    <div className="text-[11px] text-text-tertiary leading-snug">{b.descricao}</div>
                  </button>
                  {!b.pronto && (
                    <button
                      type="button"
                      onClick={() => {
                        apagarBloco(b.id)
                        setBlocos(listarBlocos())
                      }}
                      className="p-1 rounded text-text-tertiary hover:text-danger"
                      title="Apagar bloco"
                    >
                      <Trash2Icon size={12} />
                    </button>
                  )}
                </div>
              ))}
            </div>
            <p className="text-xs text-text-tertiary mb-4">
              Tudo nasce ligado a uma chave que o carro entrega de verdade e numa posição que não cobre
              nada do que o veículo desenha.
            </p>
            <Button variant="ghost" onClick={() => setShowAdd(false)}>
              Fechar
            </Button>
          </div>
        </Modal>
      )}

      {showAoVivo && (
        <Modal onClose={() => setShowAoVivo(false)}>
          <h2 className="text-lg font-semibold text-text mb-2">Ver no carro</h2>
          <p className="text-sm text-text-secondary mb-3">
            Enquanto estiver ligado, o painel do carro mostra este tema e acompanha cada edição (com
            um respiro de pouco mais de um segundo). O carro guarda o tema de antes e volta para ele
            quando você desligar ou sair do editor.
          </p>
          <p className="text-xs text-text-tertiary mb-4">
            Precisa do OPEN GW Alpha 6.5 ou mais novo no carro, e do tema customizado do painel ligado.
          </p>
          <CarLinkPanel link={link} />
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setShowAoVivo(false)}>
              Cancelar
            </Button>
            <Button
              variant="primary"
              disabled={link.status !== 'ok'}
              onClick={() => {
                setAoVivoLigado(true)
                setShowAoVivo(false)
              }}
            >
              Ligar
            </Button>
          </div>
        </Modal>
      )}

      {showHistorico && (
        <Modal onClose={() => setShowHistorico(false)} maxWidth="max-w-lg">
          <h2 className="text-lg font-semibold text-text mb-1">Histórico</h2>
          <p className="text-xs text-text-tertiary mb-4">
            As últimas 20 gravações deste tema, neste computador. Voltar abre a versão no editor como
            uma edição — dá para desfazer, e nada muda até salvar.
          </p>
          <div className="max-h-[55vh] overflow-y-auto space-y-1.5 mb-4">
            {versoes === null && <p className="text-xs text-text-tertiary">Lendo…</p>}
            {versoes?.length === 0 && <Banner tone="info">Nenhuma gravação ainda. Cada "Salvar" guarda uma.</Banner>}
            {versoes?.map((v, i) => (
              <div key={v.chave} className="flex items-center gap-3 rounded-xl border border-white/10 px-3 py-2">
                <div className="flex-1 min-w-0">
                  <div className="text-sm text-text">
                    {new Date(v.em).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                    {i === 0 && <span className="text-[10px] text-text-tertiary"> · mais recente</span>}
                  </div>
                  <div className="text-[11px] text-text-tertiary truncate">
                    {v.nome} · {v.paginas} página{v.paginas > 1 ? 's' : ''} · {v.elementos} elementos
                  </div>
                </div>
                <Button
                  variant="secondary"
                  size="sm"
                  fullWidth={false}
                  onClick={() => {
                    // A identidade do tema continua a de agora: voltar uma versão não muda a pasta
                    // no carro, e o `updatedAt` novo sai no próximo salvar.
                    atualizar({ ...v.spec, id: spec.id, folderName: spec.folderName, createdAt: spec.createdAt, updatedAt: spec.updatedAt })
                    setActivePageId(v.spec.pages[0].id)
                    setSelecionados([])
                    setShowHistorico(false)
                  }}
                >
                  Voltar para esta
                </Button>
              </div>
            ))}
          </div>
          <Button variant="ghost" onClick={() => setShowHistorico(false)}>
            Fechar
          </Button>
        </Modal>
      )}

      {showSend && (
        <Modal onClose={() => (sendStatus === 'busy' ? undefined : setShowSend(false))}>
          <h2 className="text-lg font-semibold text-text mb-2">Enviar para o carro</h2>
          <p className="text-sm text-text-secondary mb-4">
            Empurra este tema direto para "Meus Temas" no OPEN GW do carro, pela mesma rede Wi-Fi do carro.
            Depois de enviar, escolha o tema em Temas no carro (aqui ou no próprio carro).
          </p>

          {custo.nivel === 'pesado' && sendStatus !== 'done' && (
            <Banner tone="warning" className="mb-4">
              Este tema está pesado para o painel: {custo.motivos.slice(0, 3).join('; ')}. {custo.sugestao}
              <label className="flex items-center gap-2 mt-2 cursor-pointer">
                <input type="checkbox" checked={pesadoConfirmado} onChange={(e) => setPesadoConfirmado(e.target.checked)} />
                <span className="text-xs">Enviar mesmo assim</span>
              </label>
            </Banner>
          )}
          {semDado.length > 0 && (
            <Banner tone="info" className="mb-4">
              Na leitura ao vivo, este carro não mandou: {semDado.join(', ')}. Os elementos ligados a
              essas chaves vão ficar no texto de reserva.
            </Banner>
          )}

          <CarLinkPanel link={link} ocupado={sendStatus === 'busy'} />

          {sendStatus === 'busy' && <Banner tone="info" className="mb-4">{sendStep}</Banner>}
          {sendStatus === 'done' && (
            <Banner tone="success" className="mb-4">
              Enviado. Abra "Meus Temas" no carro e toque em "Recarregar" se ele ainda não aparecer.
            </Banner>
          )}
          {sendStatus === 'error' && <Banner tone="error" className="mb-4">{sendError}</Banner>}

          <div className="flex gap-3">
            <Button variant="secondary" disabled={sendStatus === 'busy'} onClick={() => setShowSend(false)}>
              {sendStatus === 'done' ? 'Fechar' : 'Cancelar'}
            </Button>
            <Button
              variant="primary"
              disabled={link.status !== 'ok' || sendStatus === 'busy' || (custo.nivel === 'pesado' && !pesadoConfirmado)}
              onClick={() => void enviar()}
            >
              Enviar
            </Button>
          </div>
        </Modal>
      )}
    </>
  )
}
