// O canvas do editor: a pré-visualização real por baixo e as caixas de arraste por cima.
//
// A pré-visualização é o MESMO `app.html` que vai para o carro, dentro de um `<iframe srcDoc>`.
// Não existe um segundo caminho de desenho — o que aparece aqui é literalmente o que o cluster
// executa, com um mock no lugar do `window.Android`. Os valores que o mock recebe não são mais
// sorteados: vêm do cenário escolhido na linha do tempo, pelo canal da prévia (`preview/canal.ts`).
//
// Três regras de arraste, nesta ordem, e a ordem importa:
//   1. encaixe magnético, ANTES do teste de legalidade — senão um encaixe empurraria para dentro
//      de uma zona proibida e o editor teria que desfazer o próprio encaixe;
//   2. teste de legalidade sobre a tinta do elemento — de TODOS os escolhidos, quando são vários;
//   3. quando bloqueado, tenta de novo com um eixo de cada vez, para deslizar ao longo da parede
//      em vez de grudar.
//
// As áreas que o veículo desenha por cima do tema ficam escondidas por padrão e aparecem sozinhas
// durante o arraste, que é o único momento em que importam. Ligadas o tempo todo, poluíam a prévia.

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { LockIcon, LockOpenIcon, ScanIcon, TriangleAlertIcon } from 'lucide-react'
import type { Element, ThemeSpec } from './model/spec'
import { CANVAS_HEIGHT, CANVAS_WIDTH } from './model/spec'
import { rectFor, rectsOverlap } from './model/sizes'
import { OEM_FORBIDDEN, softZonesOf, placementZones } from './model/zones'
import { firstBlocker, movedTo } from './model/collision'
import { assinarModoLivre, definirModoLivre, modoLivreAtivo } from './model/freeMode'
import { deslocarJuntos } from './model/selecao'
import { resolveBackgroundCss } from './resolveBackground'
import { emitAppHtml } from './emit/emitHtml'
import { type CanalDePrevia, type JanelaDaPrevia, diferenca } from './preview/canal'
import type { ZoomDoCanvas } from './editor/preferencias'

export type ModoDeSelecao = 'substituir' | 'alternar' | 'somar'

export type ClusterCanvasProps = {
  spec: ThemeSpec
  activePageId: string
  selecionados: string[]
  onSelecionar: (ids: string[], modo?: ModoDeSelecao) => void
  /** Um arraste, uma seta ou um nudge viram UMA entrada no desfazer, com todos os movidos. */
  onMover: (movimentos: { id: string; at: { x: number; y: number } }[]) => void
  canal: CanalDePrevia
  mostrarZonas: boolean
  zoom: ZoomDoCanvas
  onZoom?: (z: ZoomDoCanvas) => void
  onMostrarZonas?: (v: boolean) => void
  /** Muda de valor para rever a abertura: a prévia recarrega uma vez com abertura e varredura. */
  verAbertura?: number
  /** Sem caixas de arraste nem rodapé: a prévia do assistente e das miniaturas. */
  somenteLeitura?: boolean
}

/** Distância, em pixels do canvas, em que o encaixe magnético prende. */
const SNAP = 12
/** Folga respeitada ao encaixar na borda de uma zona proibida. */
const FOLGA = 24

const pct = (n: number, total: number) => `${(n / total) * 100}%`

type Alvos = { xs: number[]; ys: number[] }

function encaixar(v: number, alvos: number[]): { v: number; preso: number | null } {
  let melhor = v
  let dist = SNAP
  let preso: number | null = null
  for (const a of alvos) {
    const d = Math.abs(a - v)
    if (d < dist) {
      dist = d
      melhor = a
      preso = a
    }
  }
  return { v: melhor, preso }
}

const ZOOMS: { valor: ZoomDoCanvas; rotulo: string }[] = [
  { valor: 'ajustar', rotulo: 'Ajustar' },
  { valor: 0.5, rotulo: '50%' },
  { valor: 0.75, rotulo: '75%' },
  { valor: 1, rotulo: '100%' },
]

export function ClusterCanvas({
  spec, activePageId, selecionados, onSelecionar, onMover, canal, mostrarZonas, zoom, onZoom,
  onMostrarZonas, verAbertura = 0, somenteLeitura = false,
}: ClusterCanvasProps) {
  const molduraRef = useRef<HTMLDivElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const iframeRef = useRef<HTMLIFrameElement>(null)
  // O documento do tema tem 1920x720 FIXOS e `overflow: hidden`. Um iframe esticado por CSS não
  // reduz o conteúdo: mostra o canto superior esquerdo em escala 1:1 e corta o resto. Quem reduz é
  // um `transform: scale` sobre um iframe de tamanho real — e por isso a escala precisa ser medida.
  const [larguraDisponivel, setLarguraDisponivel] = useState(0)
  const escala = zoom === 'ajustar' ? (larguraDisponivel || CANVAS_WIDTH) / CANVAS_WIDTH : zoom
  const [fantasma, setFantasma] = useState<Record<string, { x: number; y: number }> | null>(null)
  const [guias, setGuias] = useState<{ x: number | null; y: number | null }>({ x: null, y: null })
  const [laco, setLaco] = useState<{ x0: number; y0: number; x1: number; y1: number } | null>(null)
  // `travado` separa as duas leituras do MESMO fato: o elemento invade uma zona do veículo.
  // Sem modo livre isso é uma recusa (cadeado vermelho, o elemento não vai); com modo livre é um
  // aviso (triângulo âmbar, o elemento vai e quem desenha decide).
  const [bloqueio, setBloqueio] = useState<{ zoneId: string; label: string; travado: boolean; x: number; y: number } | null>(null)
  const livre = useSyncExternalStore(assinarModoLivre, modoLivreAtivo, () => false)
  const avisoDoCarro = useSyncExternalStore(
    canal.assinar,
    () => canal.ler().valores.warningActive === 'true',
    () => false,
  )

  useEffect(() => {
    const no = molduraRef.current
    if (!no) return
    const medir = () => setLarguraDisponivel(no.getBoundingClientRect().width)
    medir()
    const ro = new ResizeObserver(medir)
    ro.observe(no)
    return () => ro.disconnect()
  }, [])

  const page = spec.pages.find((p) => p.id === activePageId) ?? spec.pages[0]
  const backgroundCss = resolveBackgroundCss(spec.background)
  const visiveis = useMemo(() => page.elements.filter((el) => !el.hidden), [page.elements])
  const selecao = useMemo(() => new Set(selecionados), [selecionados])

  // O `srcDoc` do iframe é reconstruído a cada mudança, e trocar o `srcDoc` RECARREGA a página.
  // Sem estas duas precauções o preview reiniciava a cada pixel de arraste:
  //   - o atraso deixa o arraste inteiro acontecer sobre um preview parado, e recarrega uma vez só
  //     quando o usuário solta (o fantasma sobreposto já mostra a posição ao vivo);
  //   - a abertura e a varredura ficam desligadas, senão cada recarga cobriria o painel com a
  //     animação de partida, que não é o que se está editando. "Ver abertura" liga as duas numa
  //     recarga só.
  // A abertura vale para o tema COMO ESTAVA quando foi pedida: a próxima edição é outro objeto e
  // volta ao preview sem ela, sem temporizador e sem uma recarga extra no fim da animação.
  const [aberturaDoTema, setAberturaDoTema] = useState<{ spec: ThemeSpec; n: number } | null>(null)
  useEffect(() => {
    if (verAbertura) setAberturaDoTema({ spec, n: verAbertura })
    // Só o pedido dispara; o tema do momento é lido, não assinado.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [verAbertura])
  const comAbertura = aberturaDoTema?.spec === spec
  const paraPreview = useMemo(
    () => (comAbertura ? { ...spec } : { ...spec, boot: null, welcomeSweep: false }),
    // `n` entra para "ver de novo" recarregar mesmo sem edição no meio.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [spec, comAbertura, aberturaDoTema?.n],
  )
  const [specAtrasado, setSpecAtrasado] = useState(paraPreview)
  useEffect(() => {
    const t = window.setTimeout(() => setSpecAtrasado(paraPreview), comAbertura ? 0 : 220)
    return () => window.clearTimeout(t)
  }, [paraPreview, comAbertura])

  const appHtml = useMemo(
    () => emitAppHtml(specAtrasado, { fontBase: '/fonts/', backgroundCss, activePageId }),
    [specAtrasado, backgroundCss, activePageId],
  )

  // --- canal da prévia -> iframe ------------------------------------------------------------
  const enviado = useRef<Record<string, string>>({})
  const modoEnviado = useRef<string>('')
  const empurrar = useCallback((tudo: boolean) => {
    const janela = iframeRef.current?.contentWindow as JanelaDaPrevia | null
    const api = janela?.OPENGW?.preview
    if (!api) return
    const { valores, modo } = canal.ler()
    if (tudo) {
      api.simulacao(false)
      enviado.current = {}
      modoEnviado.current = ''
    }
    if (modo !== modoEnviado.current) {
      api.modo(modo)
      modoEnviado.current = modo
    }
    const d = diferenca(enviado.current, valores)
    if (Object.keys(d).length) api.valores(d)
    enviado.current = valores
  }, [canal])

  useEffect(() => canal.assinar(() => empurrar(false)), [canal, empurrar])

  const zonasSuaves = softZonesOf(spec)

  /** Alvos de encaixe, calculados uma vez por arraste. Os elementos que estão sendo movidos saem. */
  const alvosPara = useCallback(
    (movidos: Set<string>): Alvos => {
      const xs = [CANVAS_WIDTH / 2]
      const ys = [CANVAS_HEIGHT / 2]
      for (const z of placementZones(spec)) {
        if (z.id === 'free') continue
        xs.push(z.rect.x + z.rect.width / 2)
        ys.push(z.rect.y + z.rect.height / 2)
      }
      for (const z of OEM_FORBIDDEN) {
        xs.push(z.safe.x - FOLGA, z.safe.x + z.safe.width + FOLGA)
        ys.push(z.safe.y - FOLGA, z.safe.y + z.safe.height + FOLGA)
      }
      for (const outro of visiveis) {
        if (movidos.has(outro.id)) continue
        const r = rectFor(outro)
        const cx = r.x + r.width / 2
        xs.push(cx, CANVAS_WIDTH - cx)
        ys.push(r.y + r.height / 2)
      }
      return { xs, ys }
    },
    [spec, visiveis],
  )

  const iniciarArraste = (e: React.PointerEvent, el: Element) => {
    e.preventDefault()
    e.stopPropagation()
    if (somenteLeitura) return
    const aditivo = e.shiftKey || e.metaKey || e.ctrlKey
    if (aditivo) {
      // Shift/Cmd-clique monta a seleção; não arrasta, para um clique a mais não mover nada.
      onSelecionar([el.id], 'alternar')
      return
    }
    const ids = selecao.has(el.id) ? selecionados : [el.id]
    if (!selecao.has(el.id)) onSelecionar([el.id], 'substituir')
    const container = containerRef.current
    if (!container) return
    const caixa = container.getBoundingClientRect()
    const esc = caixa.width / CANVAS_WIDTH
    const movidos = new Set(ids)
    const alvos = alvosPara(movidos)
    const elementos = page.elements.filter((x) => movidos.has(x.id) && !x.locked)
    const r0 = rectFor(el)
    const centro0 = { x: r0.x + r0.width / 2, y: r0.y + r0.height / 2 }
    const inicio = { x: e.clientX, y: e.clientY }
    let delta = { x: 0, y: 0 }
    let mexeu = false
    const limiteX: [number, number] = [
      Math.max(...elementos.map((x) => -x.place.at.x)),
      Math.min(...elementos.map((x) => CANVAS_WIDTH - x.place.at.x)),
    ]
    const limiteY: [number, number] = [
      Math.max(...elementos.map((x) => -x.place.at.y)),
      Math.min(...elementos.map((x) => CANVAS_HEIGHT - x.place.at.y)),
    ]

    const aoMover = (ev: PointerEvent) => {
      // Botão solto sem `pointerup` (a janela perdeu o foco no meio, o sistema engoliu o evento):
      // o arraste acaba aqui, em vez de o elemento seguir o ponteiro pela tela afora.
      if (ev.buttons === 0) return aoSoltar()
      const dxBruto = (ev.clientX - inicio.x) / esc
      const dyBruto = (ev.clientY - inicio.y) / esc
      if (!mexeu && Math.hypot(dxBruto, dyBruto) < 2) return
      mexeu = true
      // O encaixe é decidido pelo elemento que está sob o ponteiro; os outros seguem o mesmo vetor.
      const ex = encaixar(Math.round(centro0.x + dxBruto), alvos.xs)
      const ey = encaixar(Math.round(centro0.y + dyBruto), alvos.ys)
      // A âncora de todo elemento movido fica dentro do painel, como no arraste de um só: sem
      // isto, um puxão rápido jogava o grupo para fora do canvas, onde nem dá para clicar de volta.
      const candidato = {
        x: Math.max(limiteX[0], Math.min(limiteX[1], ex.v - centro0.x)),
        y: Math.max(limiteY[0], Math.min(limiteY[1], ey.v - centro0.y)),
      }
      const tentar = (d: { x: number; y: number }) => deslocarJuntos(elementos, elementos.map((x) => x.id), d.x, d.y)
      let aceito = candidato
      if (!tentar(candidato)) {
        if (tentar({ x: candidato.x, y: delta.y })) aceito = { x: candidato.x, y: delta.y }
        else if (tentar({ x: delta.x, y: candidato.y })) aceito = { x: delta.x, y: candidato.y }
        else aceito = delta
      }
      delta = aceito
      const novo: Record<string, { x: number; y: number }> = {}
      for (const x of elementos) novo[x.id] = { x: Math.round(x.place.at.x + delta.x), y: Math.round(x.place.at.y + delta.y) }
      setFantasma(novo)
      setGuias({ x: ex.preso !== null && aceito.x === candidato.x ? ex.preso : null, y: ey.preso !== null && aceito.y === candidato.y ? ey.preso : null })

      // Calculado SEMPRE, e não só quando barra: em modo livre o movimento é aceito e este é o
      // único jeito de o canvas ainda dizer que a posição encosta numa zona do veículo.
      const invasao = elementos
        .map((x) => firstBlocker(movedTo(x, { x: x.place.at.x + candidato.x, y: x.place.at.y + candidato.y })))
        .find(Boolean)
      if (invasao) {
        setBloqueio({
          zoneId: invasao.zone.id,
          label: invasao.zone.label,
          travado: !livre,
          x: ev.clientX - caixa.left,
          y: ev.clientY - caixa.top,
        })
      } else {
        setBloqueio(null)
      }
    }

    const aoSoltar = () => {
      window.removeEventListener('pointermove', aoMover)
      window.removeEventListener('pointerup', aoSoltar)
      window.removeEventListener('pointercancel', aoSoltar)
      // Clique sem arraste num elemento de uma seleção múltipla: fica só ele, como em todo editor.
      if (!mexeu && ids.length > 1) onSelecionar([el.id], 'substituir')
      if (mexeu && (delta.x || delta.y)) {
        onMover(elementos.map((x) => ({ id: x.id, at: { x: Math.round(x.place.at.x + delta.x), y: Math.round(x.place.at.y + delta.y) } })))
      }
      setFantasma(null)
      setBloqueio(null)
      setGuias({ x: null, y: null })
    }

    window.addEventListener('pointermove', aoMover)
    window.addEventListener('pointerup', aoSoltar)
    window.addEventListener('pointercancel', aoSoltar)
  }

  // Laço: arrastar no vazio seleciona o que a caixa tocar. Um clique sem arraste limpa a seleção.
  const iniciarLaco = (e: React.PointerEvent) => {
    if (somenteLeitura || e.button !== 0) return
    const container = containerRef.current
    if (!container) return
    const caixa = container.getBoundingClientRect()
    const esc = caixa.width / CANVAS_WIDTH
    const x0 = (e.clientX - caixa.left) / esc
    const y0 = (e.clientY - caixa.top) / esc
    const somar = e.shiftKey || e.metaKey || e.ctrlKey
    let atual = { x0, y0, x1: x0, y1: y0 }
    const aoMover = (ev: PointerEvent) => {
      if (ev.buttons === 0) return aoSoltar()
      atual = { x0, y0, x1: (ev.clientX - caixa.left) / esc, y1: (ev.clientY - caixa.top) / esc }
      setLaco(atual)
    }
    const aoSoltar = () => {
      window.removeEventListener('pointermove', aoMover)
      window.removeEventListener('pointerup', aoSoltar)
      window.removeEventListener('pointercancel', aoSoltar)
      setLaco(null)
      const r = {
        x: Math.min(atual.x0, atual.x1), y: Math.min(atual.y0, atual.y1),
        width: Math.abs(atual.x1 - atual.x0), height: Math.abs(atual.y1 - atual.y0),
      }
      if (r.width < 4 && r.height < 4) {
        if (!somar) onSelecionar([], 'substituir')
        return
      }
      const tocados = visiveis.filter((el) => !el.locked && rectsOverlap(rectFor(el), r)).map((el) => el.id)
      onSelecionar(tocados, somar ? 'somar' : 'substituir')
    }
    window.addEventListener('pointermove', aoMover)
    window.addEventListener('pointerup', aoSoltar)
    window.addEventListener('pointercancel', aoSoltar)
  }

  // Setas do teclado passam pelo MESMO teste do arraste — inclusive quanto ao modo livre. Um
  // atalho com regra própria seria uma porta dos fundos: ou os dois caminhos recusam, ou os dois
  // aceitam e avisam. Com vários escolhidos, andam todos ou nenhum.
  useEffect(() => {
    if (somenteLeitura || !selecionados.length) return
    const aoTeclar = (ev: KeyboardEvent) => {
      const alvo = ev.target as HTMLElement | null
      if (alvo && (/^(INPUT|TEXTAREA|SELECT)$/.test(alvo.tagName) || alvo.isContentEditable)) return
      const passo = ev.shiftKey ? 10 : 1
      const d = ({ ArrowLeft: [-passo, 0], ArrowRight: [passo, 0], ArrowUp: [0, -passo], ArrowDown: [0, passo] } as Record<string, number[]>)[ev.key]
      if (!d) return
      ev.preventDefault()
      const moveis = page.elements.filter((x) => selecao.has(x.id) && !x.locked)
      if (!moveis.length) return
      const invasao = moveis
        .map((x) => firstBlocker(movedTo(x, { x: x.place.at.x + d[0], y: x.place.at.y + d[1] })))
        .find(Boolean)
      const juntos = deslocarJuntos(moveis, moveis.map((x) => x.id), d[0], d[1])
      if (!juntos) {
        if (invasao) setBloqueio({ zoneId: invasao.zone.id, label: invasao.zone.label, travado: true, x: -999, y: -999 })
        return
      }
      setBloqueio(invasao ? { zoneId: invasao.zone.id, label: invasao.zone.label, travado: false, x: -999, y: -999 } : null)
      onMover(juntos.map((x) => ({ id: x.id, at: x.place.at })))
    }
    window.addEventListener('keydown', aoTeclar)
    return () => window.removeEventListener('keydown', aoTeclar)
  }, [selecionados, selecao, page.elements, onMover, livre, somenteLeitura])

  const arrastando = fantasma !== null
  const zonasVisiveis = !somenteLeitura && (mostrarZonas || arrastando)
  const larguraDoCanvas = zoom === 'ajustar' ? undefined : Math.round(CANVAS_WIDTH * zoom)

  return (
    <div>
      <div ref={molduraRef} className={zoom === 'ajustar' ? 'w-full' : 'w-full overflow-auto rounded-2xl'}>
        <div
          className={zoom === 'ajustar' ? 'w-full' : ''}
          style={{ aspectRatio: `${CANVAS_WIDTH} / ${CANVAS_HEIGHT}`, width: larguraDoCanvas }}
        >
          <div
            ref={containerRef}
            className="relative isolate w-full h-full overflow-hidden rounded-2xl border border-white/10 bg-black select-none"
            onPointerDown={iniciarLaco}
          >
            <iframe
              ref={iframeRef}
              key={`${activePageId}-${spec.pages.length}-${comAbertura ? aberturaDoTema?.n : 0}`}
              title="Pré-visualização do cluster"
              srcDoc={appHtml}
              onLoad={() => empurrar(true)}
              className="absolute left-0 top-0 border-0 pointer-events-none origin-top-left"
              style={{
                width: CANVAS_WIDTH,
                height: CANVAS_HEIGHT,
                transform: `scale(${escala})`,
                backgroundColor: 'transparent',
                colorScheme: 'normal',
              }}
            />

            {avisoDoCarro && (
              <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                <span className="text-[11px] text-amber-200/90 bg-black/60 px-3 py-1.5 rounded-lg border border-amber-400/30">
                  Aviso do carro na tela: o tema sai da frente enquanto ele dura.
                </span>
              </div>
            )}

            {zonasVisiveis && (
              <>
                {zonasSuaves.map((z) => (
                  <div
                    key={z.id}
                    title={z.why}
                    className="absolute border border-dashed border-amber-400/40 pointer-events-none"
                    style={{
                      left: pct(z.rect.x, CANVAS_WIDTH), top: pct(z.rect.y, CANVAS_HEIGHT),
                      width: pct(z.rect.width, CANVAS_WIDTH), height: pct(z.rect.height, CANVAS_HEIGHT),
                    }}
                  >
                    <span className="absolute left-1 top-1 text-[9px] text-amber-300/70 bg-black/50 px-1 rounded">
                      {z.label}
                    </span>
                  </div>
                ))}
                {OEM_FORBIDDEN.map((z) => (
                  <div
                    key={z.id}
                    title={z.why}
                    className={`absolute border-2 border-danger/70 bg-danger/10 pointer-events-none rounded-sm ${
                      bloqueio?.zoneId === z.id ? 'zona-pulsando' : ''
                    }`}
                    style={{
                      left: pct(z.safe.x, CANVAS_WIDTH), top: pct(z.safe.y, CANVAS_HEIGHT),
                      width: pct(z.safe.width, CANVAS_WIDTH), height: pct(z.safe.height, CANVAS_HEIGHT),
                    }}
                  >
                    <span className="absolute inset-x-0 -top-4 text-center text-[9px] text-danger/90 whitespace-nowrap">
                      {z.label}
                    </span>
                  </div>
                ))}
              </>
            )}

            {guias.x !== null && (
              <div className="absolute top-0 bottom-0 w-px bg-fuchsia-400/80 pointer-events-none z-[998]" style={{ left: pct(guias.x, CANVAS_WIDTH) }} />
            )}
            {guias.y !== null && (
              <div className="absolute left-0 right-0 h-px bg-fuchsia-400/80 pointer-events-none z-[998]" style={{ top: pct(guias.y, CANVAS_HEIGHT) }} />
            )}

            {!somenteLeitura && visiveis.map((el) => {
              const at = fantasma?.[el.id] ?? el.place.at
              const r = rectFor({ ...el, place: { ...el.place, at } } as Element)
              const selecionadoAqui = selecao.has(el.id)
              const emArraste = fantasma?.[el.id] !== undefined
              const invade = emArraste && firstBlocker({ ...el, place: { ...el.place, at } } as Element) !== null
              const ilegal = invade && !livre
              return (
                <div
                  key={el.id}
                  className={`absolute border-2 group transition-colors ${el.locked ? 'pointer-events-none' : 'cursor-move'} ${
                    ilegal
                      ? 'border-danger bg-danger/10'
                      : invade
                        ? 'border-amber-400 bg-amber-400/10'
                        : selecionadoAqui
                          ? 'border-primary bg-primary/10'
                          : 'border-transparent hover:border-white/40'
                  }`}
                  style={{
                    left: pct(r.x, CANVAS_WIDTH), top: pct(r.y, CANVAS_HEIGHT),
                    width: pct(r.width, CANVAS_WIDTH), height: pct(r.height, CANVAS_HEIGHT),
                    zIndex: 100 + el.place.z,
                  }}
                  onPointerDown={(e) => iniciarArraste(e, el)}
                >
                  <span
                    className={`absolute -top-0.5 left-0 -translate-y-full text-[10px] font-medium px-2 py-0.5 rounded-t-md whitespace-nowrap ${
                      selecionadoAqui ? 'bg-primary text-white' : 'bg-black/70 text-text-secondary opacity-0 group-hover:opacity-100'
                    }`}
                  >
                    {el.name}
                  </span>
                </div>
              )
            })}

            {laco && (
              <div
                className="absolute border border-primary bg-primary/10 pointer-events-none z-[999]"
                style={{
                  left: pct(Math.min(laco.x0, laco.x1), CANVAS_WIDTH),
                  top: pct(Math.min(laco.y0, laco.y1), CANVAS_HEIGHT),
                  width: pct(Math.abs(laco.x1 - laco.x0), CANVAS_WIDTH),
                  height: pct(Math.abs(laco.y1 - laco.y0), CANVAS_HEIGHT),
                }}
              />
            )}

            {bloqueio && bloqueio.x >= 0 && (
              <div
                className={`absolute z-[999] pointer-events-none flex items-center gap-1 text-white text-[10px] font-medium px-2 py-1 rounded-md shadow-lg ${
                  bloqueio.travado ? 'bg-danger' : 'bg-amber-500'
                }`}
                style={{ left: bloqueio.x + 14, top: bloqueio.y + 14 }}
              >
                {bloqueio.travado ? <LockIcon size={11} /> : <TriangleAlertIcon size={11} />}
                <span>
                  {bloqueio.label} do veículo{bloqueio.travado ? '' : ' — passa por cima do tema'}
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      {!somenteLeitura && (
        <>
          <div className="flex items-center justify-between gap-3 mt-2 flex-wrap">
            <div className="flex items-center gap-3 flex-wrap">
              {onZoom && (
                <div className="flex items-center gap-0.5 p-0.5 rounded-lg bg-black/25 border border-white/10" role="group" aria-label="Zoom">
                  {ZOOMS.map((z) => (
                    <button
                      key={String(z.valor)}
                      type="button"
                      onClick={() => onZoom(z.valor)}
                      className={`px-2 py-0.5 rounded-md text-[11px] ${zoom === z.valor ? 'bg-white/15 text-text' : 'text-text-tertiary hover:text-text'}`}
                    >
                      {z.rotulo}
                    </button>
                  ))}
                </div>
              )}
              {onMostrarZonas && (
                <button
                  type="button"
                  onClick={() => onMostrarZonas(!mostrarZonas)}
                  title="As áreas que o veículo desenha por cima do tema. Aparecem sozinhas enquanto você arrasta."
                  className={`flex items-center gap-1.5 text-xs transition-colors ${mostrarZonas ? 'text-text' : 'text-text-tertiary hover:text-text'}`}
                >
                  <ScanIcon size={12} />
                  <span>{mostrarZonas ? 'Esconder áreas do carro' : 'Mostrar áreas do carro'}</span>
                </button>
              )}
              <button
                type="button"
                onClick={() => definirModoLivre(!livre)}
                title={
                  livre
                    ? 'As zonas do veículo continuam marcadas e avisando, mas não impedem mais nada.'
                    : 'Liberar o posicionamento: as zonas do veículo passam a avisar em vez de bloquear.'
                }
                className={`flex items-center gap-1.5 text-xs transition-colors ${
                  livre ? 'text-amber-400 hover:text-amber-300' : 'text-text-tertiary hover:text-text'
                }`}
              >
                {livre ? <LockOpenIcon size={12} /> : <LockIcon size={12} />}
                <span>{livre ? 'Modo livre ligado' : 'Modo livre'}</span>
              </button>
            </div>
            <span className="text-[11px] text-text-tertiary">
              Arraste no vazio para escolher vários · Shift+clique soma · setas 1 px, Shift+setas 10 px
            </span>
          </div>
          {livre && (
            <p className="text-[11px] text-amber-400/80 mt-1.5 leading-snug">
              Modo livre: o elemento vai para onde você mandar. As três zonas marcadas continuam sendo
              desenhadas pelo veículo POR CIMA do tema — o que ficar embaixo delas não aparece quando o
              indicador acende.
            </p>
          )}
        </>
      )}

      <style>{`
        @keyframes zona-pulso { 0%,100% { opacity: 1 } 50% { opacity: .35 } }
        .zona-pulsando { animation: zona-pulso .18s ease-in-out 2; }
        @media (prefers-reduced-motion: reduce) { .zona-pulsando { animation: none } }
      `}</style>
    </div>
  )
}
