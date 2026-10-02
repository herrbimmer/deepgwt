// Catálogo XTRA: 275 temas prontos, em grupos, que o instalador leva embutidos. Escolha um tema, um
// grupo inteiro ou vários e envie para o carro, onde eles aparecem na seção "Temas XTRA" de Telas.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import {
  CheckCircle2Icon, CheckIcon, EyeIcon, FilterIcon, LayersIcon, RefreshCwIcon, SearchIcon, SendIcon, SparklesIcon, Trash2Icon, XIcon,
} from 'lucide-react'
import { BackButton, Banner, Button, Card, IconTile, Modal, Segmented, TextInput } from '../ui'
import { CarLinkPanel } from '../components'
import { useCarLink } from '../hooks/useCarLink'
import {
  type EstadoDosTemas, ativarTemaNoCarro, castaDe, lerTemasDoCarro, type TemaNoCarro,
} from '../theme-editor/carro/temasNoCarro'
import {
  type CatalogoXtra as Catalogo, type CorDeDestaque, type FiltroNoCarro, type Filtros, type Fundo, LIMITE_CONFORTAVEL_NO_CARRO,
  type Ordem, type ResultadoDoLote, SEM_FILTROS, type Situacao, type TipoDeGrupo,
  aplicarFiltros, carregarCatalogo, contarPor, emLote, enviarTemaXtra, filtrosAtivos, formatarBytes, grupoDoTema, miniaturaDe,
  ordenarPastas, situacaoNoCarro,
} from '../temas-xtra/catalogoXtra'

type Filtro = 'todos' | 'ausentes' | 'no-carro'
type Aba = 'grupos' | 'pesquisa'

const ABA_SALVA = 'opengw_xtra_aba'
const ID_DA_BUSCA = 'xtra-busca'

const TIPOS: { id: TipoDeGrupo; rotulo: string }[] = [
  { id: 'estilos', rotulo: 'Estilos de desenho' },
  { id: 'clusters', rotulo: 'Clusters reais' },
  { id: 'marcas', rotulo: 'Marcas' },
]
const FUNDOS: { id: Fundo; rotulo: string }[] = [
  { id: 'escuro', rotulo: 'Escuro' },
  { id: 'claro', rotulo: 'Claro' },
]
const CORES: { id: CorDeDestaque; rotulo: string; amostra: string }[] = [
  { id: 'azul', rotulo: 'Azul', amostra: '#3e7bfa' },
  { id: 'ciano', rotulo: 'Ciano', amostra: '#22c3d6' },
  { id: 'verde', rotulo: 'Verde', amostra: '#30a46c' },
  { id: 'ambar', rotulo: 'Âmbar e laranja', amostra: '#f5a524' },
  { id: 'vermelho', rotulo: 'Vermelho', amostra: '#e5484d' },
  { id: 'roxo', rotulo: 'Roxo e rosa', amostra: '#8e4ec6' },
  { id: 'colorido', rotulo: 'Colorido', amostra: 'conic-gradient(#e5484d, #f5a524, #30a46c, #22c3d6, #3e7bfa, #8e4ec6, #e5484d)' },
  { id: 'mono', rotulo: 'Monocromático', amostra: 'linear-gradient(135deg, #f4f4f5 0 50%, #3f3f46 50% 100%)' },
]
const NO_CARRO: { id: FiltroNoCarro; rotulo: string }[] = [
  { id: 'todos', rotulo: 'Todos' },
  { id: 'ausentes', rotulo: 'Não enviados' },
  { id: 'no-carro', rotulo: 'No carro' },
  { id: 'desatualizado', rotulo: 'Para atualizar' },
]
const ORDENS: { id: Ordem; rotulo: string }[] = [
  { id: 'grupo', rotulo: 'Ordem do catálogo' },
  { id: 'nome', rotulo: 'Nome (A–Z)' },
  { id: 'leves', rotulo: 'Mais leves primeiro' },
  { id: 'pesados', rotulo: 'Mais pesados primeiro' },
]

/** "Marcas: Bávaro (Munique)" -> "Bávaro", para caber no cartão junto da versão. */
const grupoCurto = (nome: string) => nome.replace(/^(Marcas|Clusters reais): /, '').replace(/\s*\(.*\)$/, '')

const lerAbaSalva = (): Aba => {
  try {
    return localStorage.getItem(ABA_SALVA) === 'pesquisa' ? 'pesquisa' : 'grupos'
  } catch {
    return 'grupos'
  }
}

/** Uma opção de faceta: rótulo, quantos temas ela daria, marcada ou não. Clicar de novo desmarca. */
const OpcaoDeFiltro = ({
  rotulo, quantos, ativo, onClick, amostra,
}: { rotulo: string; quantos: number; ativo: boolean; onClick: () => void; amostra?: string }) => (
  <button
    type="button"
    onClick={onClick}
    disabled={!ativo && quantos === 0}
    aria-pressed={ativo}
    className={`w-full text-left px-2.5 py-1.5 rounded-lg text-[12px] flex items-center gap-2 transition-colors disabled:opacity-35 disabled:cursor-default ${
      ativo ? 'bg-primary/15 text-text ring-1 ring-primary/40' : 'text-text-secondary hover:bg-white/[0.06]'
    }`}
  >
    {amostra && <span className="w-3.5 h-3.5 rounded-full border border-white/25 flex-shrink-0" style={{ background: amostra }} />}
    <span className="flex-1 truncate">{rotulo}</span>
    <span className="text-[10px] text-text-tertiary tabular-nums">{quantos}</span>
  </button>
)

const Chip = ({ rotulo, onTirar }: { rotulo: string; onTirar: () => void }) => (
  <span className="inline-flex items-center gap-1 pl-2 pr-1 py-0.5 rounded-full text-[11px] bg-primary/15 text-text border border-primary/30">
    {rotulo}
    <button type="button" onClick={onTirar} className="p-0.5 rounded-full hover:bg-white/15" aria-label={`Tirar o filtro ${rotulo}`}>
      <XIcon size={11} />
    </button>
  </span>
)

const Faceta = ({ titulo, children }: { titulo: string; children: React.ReactNode }) => (
  <div className="mb-4">
    <div className="text-[10px] font-semibold text-text-tertiary uppercase tracking-wide mb-1 px-1">{titulo}</div>
    <div className="space-y-0.5">{children}</div>
  </div>
)
type Lote = { tipo: 'enviar' | 'remover'; pastas: string[]; feitos: number; atual: string | null; resultado: ResultadoDoLote | null }

const SELO: Record<Exclude<Situacao, 'ausente'>, { rotulo: string; classe: string }> = {
  'no-carro': { rotulo: 'No carro', classe: 'bg-success/90 text-black' },
  desatualizado: { rotulo: 'Atualizar', classe: 'bg-warning/90 text-black' },
}

export const CatalogoXtra = () => {
  const link = useCarLink()
  const [catalogo, setCatalogo] = useState<Catalogo | null>(null)
  const [estado, setEstado] = useState<EstadoDosTemas | null>(null)
  const [erro, setErro] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [grupo, setGrupo] = useState<string>('todos')
  const [filtro, setFiltro] = useState<Filtro>('todos')
  const [aba, setAbaCrua] = useState<Aba>(lerAbaSalva)
  const [filtros, setFiltros] = useState<Filtros>(SEM_FILTROS)
  const [ordem, setOrdem] = useState<Ordem>('grupo')
  const [selecao, setSelecao] = useState<Set<string>>(() => new Set())
  const [detalhe, setDetalhe] = useState<string | null>(null)
  const [lote, setLote] = useState<Lote | null>(null)
  const [aRemover, setARemover] = useState<string[] | null>(null)
  const [confirmado, setConfirmado] = useState(false)
  const cancelar = useRef(false)
  const topoDaGrade = useRef<HTMLDivElement>(null)

  const setAba = (a: Aba) => {
    setAbaCrua(a)
    try {
      localStorage.setItem(ABA_SALVA, a)
    } catch {
      /* sem armazenamento: só não lembra a aba */
    }
  }
  const mudarFiltro = <K extends keyof Filtros>(k: K, v: Filtros[K]) => setFiltros((f) => ({ ...f, [k]: v }))
  const alternarFiltro = <K extends keyof Filtros>(k: K, v: Filtros[K]) => setFiltros((f) => ({ ...f, [k]: f[k] === v ? SEM_FILTROS[k] : v }))

  // Ctrl/Cmd+F ou "/" leva à aba de pesquisa com o cursor na busca.
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const digitando = e.target instanceof HTMLElement && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)
      if (((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'f') || (e.key === '/' && !digitando)) {
        e.preventDefault()
        setAba('pesquisa')
        setTimeout(() => document.getElementById(ID_DA_BUSCA)?.focus(), 0)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const escolherGrupo = (id: string) => {
    setGrupo(id)
    topoDaGrade.current?.scrollIntoView({ block: 'start' })
  }

  useEffect(() => {
    carregarCatalogo().then(setCatalogo).catch((e) => setErro(e instanceof Error ? e.message : String(e)))
    link.preparar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const lerCarro = useCallback(async () => {
    try {
      setEstado(await lerTemasDoCarro(link.carIp))
    } catch (e) {
      setEstado(null)
      setErro(e instanceof Error ? e.message : String(e))
    }
  }, [link.carIp])

  useEffect(() => {
    if (link.status === 'ok') void lerCarro()
  }, [link.status, lerCarro])

  const conectado = link.status === 'ok'
  const noCarro = useMemo(() => (estado ? new Map(estado.temas.map((t) => [t.pasta, t] as [string, TemaNoCarro])) : null), [estado])
  const situacao = useCallback(
    (p: string): Situacao => (catalogo ? situacaoNoCarro(p, catalogo.temas[p], noCarro) : 'ausente'),
    [catalogo, noCarro],
  )
  const xtraNoCarro = estado ? estado.temas.filter((t) => castaDe(t) === 'xtra').length : 0
  const legacyNoCarro = estado ? estado.temas.length - xtraNoCarro : 0

  const gruposVisiveis = useMemo(() => {
    if (!catalogo) return []
    return catalogo.grupos
      .filter((g) => grupo === 'todos' || g.id === grupo)
      .map((g) => ({
        ...g,
        visiveis: g.temas.filter((p) => {
          if (filtro === 'todos') return true
          const s = situacao(p)
          return filtro === 'ausentes' ? s !== 'no-carro' : s !== 'ausente'
        }),
      }))
      .filter((g) => g.visiveis.length > 0)
  }, [catalogo, grupo, filtro, situacao])

  const resultado = useMemo(
    () => (catalogo ? ordenarPastas(catalogo, aplicarFiltros(catalogo, filtros, situacao), ordem) : []),
    [catalogo, filtros, situacao, ordem],
  )
  // Cada faceta conta com os OUTROS filtros ligados: o número ao lado da opção é o que ela daria.
  const contagens = useMemo(() => {
    if (!catalogo) return null
    const sem = (k: keyof Filtros) => aplicarFiltros(catalogo, filtros, situacao, k)
    const tipoDe = (p: string) => grupoDoTema(catalogo, p)?.tipo
    const noCarroDe = (p: string) => situacao(p)
    const semNoCarro = sem('noCarro')
    return {
      tipo: contarPor(sem('tipo'), tipoDe),
      grupo: contarPor(sem('grupo'), (p) => grupoDoTema(catalogo, p)?.id),
      fundo: contarPor(sem('fundo'), (p) => catalogo.temas[p].fundo),
      cor: contarPor(sem('cor'), (p) => catalogo.temas[p].cor),
      noCarro: {
        todos: semNoCarro.length,
        ausentes: semNoCarro.filter((p) => noCarroDe(p) === 'ausente').length,
        'no-carro': semNoCarro.filter((p) => noCarroDe(p) !== 'ausente').length,
        desatualizado: semNoCarro.filter((p) => noCarroDe(p) === 'desatualizado').length,
      } as Record<FiltroNoCarro, number>,
    }
  }, [catalogo, filtros, situacao])

  const alternar = (pastas: string[], ligar?: boolean) =>
    setSelecao((atual) => {
      const nova = new Set(atual)
      const todos = pastas.every((p) => nova.has(p))
      const marcar = ligar ?? !todos
      pastas.forEach((p) => (marcar ? nova.add(p) : nova.delete(p)))
      return nova
    })

  const selecionados = [...selecao]
  const bytesSelecionados = catalogo ? selecionados.reduce((n, p) => n + (catalogo.temas[p]?.bytes ?? 0), 0) : 0
  const selecionadosNoCarro = selecionados.filter((p) => situacao(p) !== 'ausente')
  const novosNoCarro = selecionados.filter((p) => situacao(p) === 'ausente').length
  const passaDoLimite = conectado && estado && xtraNoCarro + novosNoCarro > LIMITE_CONFORTAVEL_NO_CARRO

  const rodarLote = async (tipo: Lote['tipo'], pastas: string[]) => {
    if (!catalogo || !pastas.length) return
    cancelar.current = false
    setErro(null)
    setAviso(null)
    setDetalhe(null)
    setLote({ tipo, pastas, feitos: 0, atual: pastas[0], resultado: null })
    const acao = tipo === 'enviar'
      ? (p: string) => enviarTemaXtra(link.carIp, catalogo, p)
      : (p: string) => invoke<void>('remote_delete_theme', { carIp: link.carIp, folder: p })
    const resultado = await emLote(
      pastas,
      acao,
      (feitos, atual) => setLote((l) => (l ? { ...l, feitos, atual } : l)),
      () => cancelar.current,
    )
    setLote((l) => (l ? { ...l, resultado, atual: null } : l))
    if (resultado.feitos.length) setSelecao((s) => new Set([...s].filter((p) => !resultado.feitos.includes(p))))
    await lerCarro()
  }

  const ativar = async (pasta: string) => {
    setErro(null)
    try {
      setEstado(await ativarTemaNoCarro(link.carIp, pasta))
      setAviso(`Aplicado: o painel do carro já está com "${catalogo?.temas[pasta]?.nome ?? pasta}".`)
      setLote(null)
      setDetalhe(null)
    } catch (e) {
      setErro(e instanceof Error ? e.message : String(e))
    }
  }

  const temaDetalhe = detalhe && catalogo ? catalogo.temas[detalhe] : null
  const grupoDe = (p: string) => (catalogo ? grupoDoTema(catalogo, p)?.nome ?? '' : '')
  const nFiltros = filtrosAtivos(filtros)
  const pendentesDoResultado = resultado.filter((p) => situacao(p) !== 'no-carro')
  const resultadoTodoMarcado = resultado.length > 0 && resultado.every((p) => selecao.has(p))

  const cartao = (p: string, mostrarGrupo = false) => {
    if (!catalogo) return null
    const t = catalogo.temas[p]
    const marcado = selecao.has(p)
    const s = situacao(p)
    const ativo = estado?.ativo === p
    return (
      <div
        key={p}
        className={`group relative rounded-xl overflow-hidden border transition-colors cursor-pointer ${
          marcado ? 'border-primary ring-2 ring-primary/50' : 'border-white/10 hover:border-white/25'
        } bg-black/30`}
        onClick={() => alternar([p])}
        role="checkbox"
        aria-checked={marcado}
        aria-label={t.nome}
      >
        <img src={miniaturaDe(p)} alt="" loading="lazy" className="w-full aspect-[8/3] object-cover bg-black" />
        <span
          className={`absolute top-2 left-2 w-5 h-5 rounded-md border flex items-center justify-center ${
            marcado ? 'bg-primary border-primary' : 'bg-black/70 border-white/80 shadow'
          }`}
        >
          {marcado && <CheckIcon size={13} className="text-white" />}
        </span>
        <div className="absolute top-2 right-2 flex gap-1">
          {ativo && <span className="px-1.5 py-0.5 rounded-md text-[10px] font-semibold bg-primary text-white">Ativo</span>}
          {s !== 'ausente' && <span className={`px-1.5 py-0.5 rounded-md text-[10px] font-semibold ${SELO[s].classe}`}>{SELO[s].rotulo}</span>}
        </div>
        <div className="px-2.5 py-2 flex items-center gap-2">
          <div className="min-w-0 flex-1">
            <div className="text-[13px] font-medium text-text truncate">{t.nome}</div>
            <div className="text-[10px] text-text-tertiary truncate">
              {mostrarGrupo ? `${grupoCurto(grupoDe(p))} · ` : ''}v{t.versao} · {formatarBytes(t.bytes)}
            </div>
          </div>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation()
              setDetalhe(p)
            }}
            className="p-1.5 rounded-lg text-text-tertiary hover:text-text hover:bg-white/10"
            aria-label={`Detalhes de ${t.nome}`}
          >
            <EyeIcon size={14} />
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="2xl">
        <div className="flex items-center gap-3 mb-2">
          <IconTile icon={SparklesIcon} tone="primary" size={44} />
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold text-text tracking-tight">Catálogo XTRA</h1>
            {catalogo && (
              <div className="text-[11px] text-text-tertiary">
                {catalogo.total} temas · {catalogo.grupos.length} grupos · pacote v{catalogo.versao}
              </div>
            )}
          </div>
        </div>
        <p className="text-text-secondary text-sm mb-5">
          Temas prontos para o painel de instrumentos. Escolha um tema, um grupo inteiro ou vários, e
          envie para o carro. Lá eles aparecem em Telas → Tema do painel, na seção{' '}
          <span className="text-text font-medium">Temas XTRA</span>; os que vêm com o app ficam em{' '}
          <span className="text-text font-medium">Temas Legacy</span>.
        </p>

        <CarLinkPanel link={link} ocupado={lote !== null && !lote.resultado} />

        {erro && <Banner tone="error" className="mb-4">{erro}</Banner>}
        {aviso && <Banner tone="success" className="mb-4">{aviso}</Banner>}
        {conectado && estado && (
          <div className="flex items-center gap-2 mb-4 text-xs text-text-secondary">
            <span className="px-2 py-1 rounded-lg bg-white/[0.06] border border-white/10">
              No carro: <b className="text-text">{legacyNoCarro}</b> Legacy · <b className="text-text">{xtraNoCarro}</b> XTRA
            </span>
            {!estado.temaCustomizado && <span className="text-warning">Tema customizado desligado no carro: dá para enviar, não para ativar.</span>}
            <button type="button" onClick={() => void lerCarro()} className="ml-auto p-1.5 rounded-lg text-text-tertiary hover:text-text hover:bg-white/10" aria-label="Reler o carro">
              <RefreshCwIcon size={14} />
            </button>
          </div>
        )}

        {!catalogo && !erro && <Banner tone="info">Carregando o catálogo…</Banner>}

        {catalogo && (
          <div className="flex flex-wrap items-center gap-3 mb-4">
            <div className="flex gap-1 p-1 rounded-xl bg-black/25 border border-white/10" role="tablist" aria-label="Modo do catálogo">
              {([
                { id: 'grupos', rotulo: 'Navegar por grupo', icone: LayersIcon },
                { id: 'pesquisa', rotulo: 'Pesquisar e filtrar', icone: SearchIcon },
              ] as const).map((a) => (
                <button
                  key={a.id}
                  type="button"
                  role="tab"
                  aria-selected={aba === a.id}
                  onClick={() => setAba(a.id)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                    aba === a.id ? 'bg-primary text-white' : 'text-text-secondary hover:bg-white/[0.07]'
                  }`}
                >
                  <a.icone size={14} />
                  {a.rotulo}
                  {a.id === 'pesquisa' && nFiltros > 0 && (
                    <span className={`ml-0.5 px-1.5 rounded-full text-[10px] ${aba === a.id ? 'bg-white/25' : 'bg-primary/25 text-text'}`}>{nFiltros}</span>
                  )}
                </button>
              ))}
            </div>
            <span className="text-[11px] text-text-tertiary hidden sm:inline">Atalho: Ctrl+F ou / abre a pesquisa</span>
          </div>
        )}

        {catalogo && aba === 'grupos' && (
          <div ref={topoDaGrade} className="flex gap-5 items-start scroll-mt-4">
            <nav className="hidden md:block w-56 flex-shrink-0 sticky top-2 max-h-[calc(100vh-7rem)] overflow-y-auto space-y-0.5 pr-1" aria-label="Grupos">
              {[{ id: 'todos', nome: 'Todos os grupos', temas: Object.keys(catalogo.temas) }, ...catalogo.grupos].map((g) => {
                const ativo = grupo === g.id
                const noCarroDoGrupo = g.temas.filter((p) => situacao(p) !== 'ausente').length
                return (
                  <button
                    key={g.id}
                    type="button"
                    onClick={() => escolherGrupo(g.id)}
                    title={g.nome}
                    className={`w-full text-left px-2.5 py-1.5 rounded-lg text-[12px] flex items-center gap-2 transition-colors ${
                      ativo ? 'bg-primary/15 text-text' : 'text-text-secondary hover:bg-white/[0.06]'
                    }`}
                  >
                    <span className="flex-1 truncate">{g.nome}</span>
                    {noCarroDoGrupo > 0 && <span className="text-[10px] text-success">{noCarroDoGrupo}</span>}
                    <span className="text-[10px] text-text-tertiary tabular-nums">{g.temas.length}</span>
                  </button>
                )
              })}
            </nav>

            <div className="flex-1 min-w-0">
              <div className="flex flex-wrap items-center gap-3 mb-4">
                <Segmented<Filtro>
                  size="sm"
                  value={filtro}
                  onChange={setFiltro}
                  options={[
                    { value: 'todos', label: 'Todos' },
                    { value: 'ausentes', label: 'Não enviados' },
                    { value: 'no-carro', label: 'No carro' },
                  ]}
                />
                <button
                  type="button"
                  onClick={() => {
                    setAba('pesquisa')
                    setTimeout(() => document.getElementById(ID_DA_BUSCA)?.focus(), 0)
                  }}
                  className="ml-auto flex items-center gap-1.5 text-xs text-text-secondary hover:text-text"
                >
                  <SearchIcon size={13} /> Procurar um tema
                </button>
              </div>

              <select
                value={grupo}
                onChange={(e) => escolherGrupo(e.target.value)}
                className="md:hidden w-full mb-4 bg-black/25 border border-white/10 rounded-xl px-3 py-2 text-sm text-text"
              >
                <option value="todos">Todos os grupos</option>
                {catalogo.grupos.map((g) => <option key={g.id} value={g.id}>{g.nome}</option>)}
              </select>

              {gruposVisiveis.length === 0 && <Banner tone="info">Nenhum tema com esse filtro.</Banner>}

              {gruposVisiveis.map((g) => {
                const todosMarcados = g.visiveis.every((p) => selecao.has(p))
                const pendentes = g.visiveis.filter((p) => situacao(p) !== 'no-carro')
                const noCarroDoGrupo = g.temas.filter((p) => situacao(p) !== 'ausente').length
                return (
                  <section key={g.id} className="mb-7">
                    <div className="flex flex-wrap items-center gap-2 mb-2.5">
                      <LayersIcon size={15} className="text-primary" />
                      <h2 className="text-sm font-semibold text-text">{g.nome}</h2>
                      <span className="text-[11px] text-text-tertiary">
                        {g.temas.length} temas{noCarroDoGrupo ? ` · ${noCarroDoGrupo} no carro` : ''}
                      </span>
                      <div className="ml-auto flex items-center gap-2">
                        <Button variant="ghost" size="sm" fullWidth={false} icon={todosMarcados ? XIcon : CheckIcon} onClick={() => alternar(g.visiveis)}>
                          {todosMarcados ? 'Desmarcar grupo' : 'Selecionar grupo'}
                        </Button>
                        <Button
                          variant="secondary"
                          size="sm"
                          fullWidth={false}
                          icon={SendIcon}
                          disabled={!conectado || lote !== null || pendentes.length === 0}
                          title={conectado ? undefined : 'Conecte ao carro para enviar'}
                          onClick={() => void rodarLote('enviar', pendentes)}
                        >
                          {conectado && estado && pendentes.length === 0 ? 'Grupo no carro' : 'Enviar grupo'}
                        </Button>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">{g.visiveis.map((p) => cartao(p))}</div>
                  </section>
                )
              })}
            </div>
          </div>
        )}

        {catalogo && contagens && aba === 'pesquisa' && (
          <div className="flex flex-col md:flex-row gap-5 items-start">
            <aside
              className="w-full md:w-60 flex-shrink-0 md:sticky md:top-2 md:max-h-[calc(100vh-7rem)] md:overflow-y-auto pr-1"
              aria-label="Filtros"
            >
              <div className="flex items-center gap-2 mb-3 px-1">
                <FilterIcon size={14} className="text-primary" />
                <span className="text-sm font-semibold text-text">Filtros</span>
                {nFiltros > 0 && (
                  <button type="button" onClick={() => setFiltros(SEM_FILTROS)} className="ml-auto text-[11px] text-primary hover:underline">
                    Limpar ({nFiltros})
                  </button>
                )}
              </div>

              <Faceta titulo="Tipo">
                {TIPOS.map((o) => (
                  <OpcaoDeFiltro key={o.id} rotulo={o.rotulo} quantos={contagens.tipo.get(o.id) ?? 0} ativo={filtros.tipo === o.id} onClick={() => {
                    setFiltros((f) => ({ ...f, tipo: f.tipo === o.id ? null : o.id, grupo: null }))
                  }} />
                ))}
              </Faceta>

              <Faceta titulo="Grupo">
                <select
                  value={filtros.grupo ?? ''}
                  onChange={(e) => mudarFiltro('grupo', e.target.value || null)}
                  aria-label="Grupo"
                  className={`w-full bg-black/25 border rounded-lg px-2 py-1.5 text-[12px] text-text ${filtros.grupo ? 'border-primary/50' : 'border-white/10'}`}
                >
                  <option value="">Todos os grupos</option>
                  {catalogo.grupos
                    .filter((g) => !filtros.tipo || g.tipo === filtros.tipo)
                    .map((g) => (
                      <option key={g.id} value={g.id} disabled={!contagens.grupo.get(g.id) && filtros.grupo !== g.id}>
                        {g.nome} ({contagens.grupo.get(g.id) ?? 0})
                      </option>
                    ))}
                </select>
              </Faceta>

              <Faceta titulo="Fundo">
                {FUNDOS.map((o) => (
                  <OpcaoDeFiltro key={o.id} rotulo={o.rotulo} quantos={contagens.fundo.get(o.id) ?? 0} ativo={filtros.fundo === o.id} onClick={() => alternarFiltro('fundo', o.id)}
                    amostra={o.id === 'claro' ? '#f4f1ea' : '#0b0c0f'} />
                ))}
              </Faceta>

              <Faceta titulo="Cor de destaque">
                {CORES.filter((o) => (contagens.cor.get(o.id) ?? 0) > 0 || filtros.cor === o.id).map((o) => (
                  <OpcaoDeFiltro key={o.id} rotulo={o.rotulo} quantos={contagens.cor.get(o.id) ?? 0} ativo={filtros.cor === o.id} onClick={() => alternarFiltro('cor', o.id)} amostra={o.amostra} />
                ))}
              </Faceta>

              <Faceta titulo="No carro">
                {NO_CARRO.map((o) => (
                  <OpcaoDeFiltro key={o.id} rotulo={o.rotulo} quantos={contagens.noCarro[o.id]} ativo={filtros.noCarro === o.id} onClick={() => mudarFiltro('noCarro', o.id)} />
                ))}
                {!conectado && <p className="text-[10px] text-text-tertiary px-1 pt-1">Conecte ao carro para saber o que já está lá.</p>}
              </Faceta>
            </aside>

            <div className="flex-1 min-w-0 w-full">
              <div className="flex items-center gap-2 mb-3">
                <TextInput
                  id={ID_DA_BUSCA}
                  icon={SearchIcon}
                  value={filtros.busca}
                  autoFocus
                  onChange={(e) => mudarFiltro('busca', e.target.value)}
                  onKeyDown={(e) => e.key === 'Escape' && mudarFiltro('busca', '')}
                  placeholder="Pesquisar nome, estilo, marca ou descrição (ex.: ponteiro âmbar, bávaro, osciloscópio)"
                  containerClassName="flex-1"
                />
                {filtros.busca && (
                  <button type="button" onClick={() => mudarFiltro('busca', '')} className="p-2 rounded-lg text-text-tertiary hover:text-text hover:bg-white/10" aria-label="Limpar a pesquisa">
                    <XIcon size={15} />
                  </button>
                )}
              </div>

              <div className="flex flex-wrap items-center gap-2 mb-3">
                <span className="text-xs text-text-secondary">
                  <b className="text-text">{resultado.length}</b> de {catalogo.total} temas
                </span>
                {filtros.tipo && <Chip rotulo={TIPOS.find((o) => o.id === filtros.tipo)!.rotulo} onTirar={() => mudarFiltro('tipo', null)} />}
                {filtros.grupo && <Chip rotulo={catalogo.grupos.find((g) => g.id === filtros.grupo)?.nome ?? filtros.grupo} onTirar={() => mudarFiltro('grupo', null)} />}
                {filtros.fundo && <Chip rotulo={`Fundo ${filtros.fundo}`} onTirar={() => mudarFiltro('fundo', null)} />}
                {filtros.cor && <Chip rotulo={CORES.find((o) => o.id === filtros.cor)!.rotulo} onTirar={() => mudarFiltro('cor', null)} />}
                {filtros.noCarro !== 'todos' && <Chip rotulo={NO_CARRO.find((o) => o.id === filtros.noCarro)!.rotulo} onTirar={() => mudarFiltro('noCarro', 'todos')} />}
                <label className="ml-auto flex items-center gap-1.5 text-[11px] text-text-tertiary">
                  Ordenar
                  <select
                    value={ordem}
                    onChange={(e) => setOrdem(e.target.value as Ordem)}
                    className="bg-black/25 border border-white/10 rounded-lg px-2 py-1 text-[12px] text-text"
                  >
                    {ORDENS.map((o) => <option key={o.id} value={o.id}>{o.rotulo}</option>)}
                  </select>
                </label>
              </div>

              {resultado.length > 0 && (
                <div className="flex flex-wrap items-center gap-2 mb-3">
                  <Button variant="ghost" size="sm" fullWidth={false} icon={resultadoTodoMarcado ? XIcon : CheckIcon} onClick={() => alternar(resultado)}>
                    {resultadoTodoMarcado ? 'Desmarcar resultado' : `Selecionar os ${resultado.length}`}
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    fullWidth={false}
                    icon={SendIcon}
                    disabled={!conectado || lote !== null || pendentesDoResultado.length === 0}
                    title={conectado ? undefined : 'Conecte ao carro para enviar'}
                    onClick={() => void rodarLote('enviar', pendentesDoResultado)}
                  >
                    {conectado && estado && pendentesDoResultado.length === 0
                      ? 'Todos já no carro'
                      : `Enviar ${pendentesDoResultado.length === resultado.length ? 'o resultado' : `os ${pendentesDoResultado.length} que faltam`}`}
                  </Button>
                </div>
              )}

              {resultado.length === 0 ? (
                <div className="rounded-xl border border-dashed border-white/15 p-8 text-center">
                  <p className="text-sm text-text-secondary mb-3">Nenhum tema com esses filtros.</p>
                  <Button variant="secondary" size="sm" fullWidth={false} onClick={() => setFiltros(SEM_FILTROS)}>Limpar filtros</Button>
                </div>
              ) : (
                <div className="grid grid-cols-2 lg:grid-cols-3 gap-3">{resultado.map((p) => cartao(p, true))}</div>
              )}
            </div>
          </div>
        )}

        {selecao.size > 0 && (
          <div className="sticky bottom-0 -mx-2 mt-2 mb-2 p-3 rounded-2xl bg-[#16171a]/95 backdrop-blur-xl border border-white/15 shadow-2xl">
            <div className="flex flex-wrap items-center gap-3">
              <div className="text-sm text-text">
                <b>{selecao.size}</b> selecionado{selecao.size > 1 ? 's' : ''}
                <span className="text-text-tertiary"> · {formatarBytes(bytesSelecionados)}</span>
              </div>
              <Button variant="ghost" size="sm" fullWidth={false} onClick={() => setSelecao(new Set())}>Limpar</Button>
              <div className="ml-auto flex gap-2">
                {selecionadosNoCarro.length > 0 && (
                  <Button
                    variant="secondary"
                    size="sm"
                    fullWidth={false}
                    icon={Trash2Icon}
                    disabled={!conectado || lote !== null}
                    onClick={() => {
                      setARemover(selecionadosNoCarro)
                      setConfirmado(false)
                    }}
                  >
                    Remover do carro ({selecionadosNoCarro.length})
                  </Button>
                )}
                <Button variant="primary" size="sm" fullWidth={false} icon={SendIcon} disabled={!conectado || lote !== null} onClick={() => void rodarLote('enviar', selecionados)}>
                  Enviar ao carro
                </Button>
              </div>
            </div>
            {!conectado && <div className="text-[11px] text-text-tertiary mt-1.5">Conecte ao carro (acima) para enviar.</div>}
            {passaDoLimite && (
              <div className="text-[11px] text-warning mt-1.5">
                O carro vai ficar com {xtraNoCarro + novosNoCarro} temas XTRA. Acima de {LIMITE_CONFORTAVEL_NO_CARRO} a lista de
                Telas fica longa e mais lenta para abrir; dá para remover depois.
              </div>
            )}
          </div>
        )}

        <BackButton to="/temas" />
      </Card>

      {temaDetalhe && detalhe && (
        <Modal onClose={() => setDetalhe(null)} maxWidth="max-w-2xl">
          <img src={miniaturaDe(detalhe)} alt="" className="w-full aspect-[8/3] object-cover rounded-xl border border-white/10 mb-4 bg-black" />
          <div className="flex items-center gap-2 mb-1">
            <h2 className="text-lg font-semibold text-text">{temaDetalhe.nome}</h2>
            {situacao(detalhe) !== 'ausente' && (
              <span className={`px-1.5 py-0.5 rounded-md text-[10px] font-semibold ${SELO[situacao(detalhe) as Exclude<Situacao, 'ausente'>].classe}`}>
                {SELO[situacao(detalhe) as Exclude<Situacao, 'ausente'>].rotulo}
              </span>
            )}
          </div>
          <div className="text-[11px] text-text-tertiary mb-3">
            {grupoDe(detalhe)} · pasta <span className="font-mono">{detalhe}</span> · v{temaDetalhe.versao} · {formatarBytes(temaDetalhe.bytes)}
          </div>
          <p className="text-sm text-text-secondary mb-5 leading-relaxed">{temaDetalhe.descricao}</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="primary" size="sm" fullWidth={false} icon={SendIcon} disabled={!conectado} onClick={() => void rodarLote('enviar', [detalhe])}>
              {situacao(detalhe) === 'ausente' ? 'Enviar ao carro' : situacao(detalhe) === 'desatualizado' ? 'Atualizar no carro' : 'Enviar de novo'}
            </Button>
            {situacao(detalhe) !== 'ausente' && estado?.ativo !== detalhe && (
              <Button variant="secondary" size="sm" fullWidth={false} icon={CheckCircle2Icon} disabled={!estado?.temaCustomizado} onClick={() => void ativar(detalhe)}>
                Ativar no painel
              </Button>
            )}
            <Button variant="ghost" size="sm" fullWidth={false} onClick={() => alternar([detalhe])}>
              {selecao.has(detalhe) ? 'Tirar da seleção' : 'Pôr na seleção'}
            </Button>
          </div>
        </Modal>
      )}

      {lote && (
        <Modal onClose={lote.resultado ? () => setLote(null) : undefined} maxWidth="max-w-lg">
          <h2 className="text-lg font-semibold text-text mb-1">
            {lote.resultado
              ? lote.tipo === 'enviar' ? 'Envio terminado' : 'Remoção terminada'
              : `${lote.tipo === 'enviar' ? 'Enviando' : 'Removendo'} ${lote.pastas.length} tema${lote.pastas.length > 1 ? 's' : ''}`}
          </h2>
          <div className="h-2 rounded-full bg-white/10 overflow-hidden my-3">
            <div className="h-full bg-primary transition-[width] duration-200" style={{ width: `${(lote.feitos / lote.pastas.length) * 100}%` }} />
          </div>
          <div className="text-xs text-text-secondary mb-4">
            {lote.resultado
              ? `${lote.resultado.feitos.length} de ${lote.pastas.length} ${lote.tipo === 'enviar' ? 'enviados' : 'removidos'}${lote.resultado.falhas.length ? `, ${lote.resultado.falhas.length} com erro` : ''}.`
              : `${lote.feitos} de ${lote.pastas.length} · ${lote.atual ? catalogo?.temas[lote.atual]?.nome ?? lote.atual : ''}`}
          </div>
          {lote.resultado && lote.resultado.falhas.length > 0 && (
            <div className="max-h-40 overflow-y-auto mb-4 space-y-1">
              {lote.resultado.falhas.map((f) => (
                <div key={f.pasta} className="text-[11px] text-danger"><span className="font-mono">{f.pasta}</span>: {f.erro}</div>
              ))}
            </div>
          )}
          {lote.resultado && lote.tipo === 'enviar' && lote.resultado.feitos.length > 0 && (
            <p className="text-xs text-text-tertiary mb-4">
              No carro: Telas → Tema do painel → Temas XTRA. Se a tela já estiver aberta, toque em Recarregar.
            </p>
          )}
          <div className="flex gap-2">
            {!lote.resultado && (
              <Button variant="secondary" onClick={() => (cancelar.current = true)}>Parar depois deste</Button>
            )}
            {lote.resultado && lote.tipo === 'enviar' && lote.resultado.feitos.length === 1 && estado?.temaCustomizado && (
              <Button variant="primary" icon={CheckCircle2Icon} onClick={() => void ativar(lote.resultado!.feitos[0])}>Ativar no painel</Button>
            )}
            {lote.resultado && <Button variant="secondary" onClick={() => setLote(null)}>Fechar</Button>}
          </div>
        </Modal>
      )}

      {aRemover && (
        <Modal onClose={() => setARemover(null)} tone="danger">
          <h2 className="text-lg font-semibold text-text mb-2">Remover {aRemover.length} tema{aRemover.length > 1 ? 's' : ''} do carro?</h2>
          <p className="text-sm text-text-secondary mb-4">
            Apaga as pastas no carro. Eles continuam aqui no catálogo e podem ser enviados de novo. Se
            um deles estiver no painel agora, o carro volta para o Stuttgart NTG 5.5 (clássico).
          </p>
          <label className="flex items-start gap-2.5 cursor-pointer mb-6">
            <input type="checkbox" checked={confirmado} onChange={(e) => setConfirmado(e.target.checked)} className="mt-0.5 w-4 h-4 cursor-pointer flex-shrink-0" />
            <span className="text-xs text-text-secondary">Entendi que estas pastas serão apagadas do carro.</span>
          </label>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setARemover(null)}>Cancelar</Button>
            <Button
              variant="destructive"
              disabled={!confirmado}
              onClick={() => {
                const lista = aRemover
                setARemover(null)
                void rodarLote('remover', lista)
              }}
            >
              Remover do carro
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
