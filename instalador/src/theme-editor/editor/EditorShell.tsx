// A moldura dos dois editores (painel e HUD): barra em cima, três colunas embaixo.
//
//   esquerda — páginas e camadas; recolhe num trilho de ícones
//   centro   — o canvas e a linha do tempo
//   direita  — o inspetor, sempre à vista
//
// A regra que a reformulação inteira segue é a segunda da proposta: o que se edita fica sempre à
// vista. Antes o inspetor ficava EMBAIXO da lista de camadas e, numa janela de 780 px, fora da
// tela. Aqui cada coluna rola sozinha e a direita nunca sai do lugar.
//
// A largura que decide o arranjo é a da PRÓPRIA moldura, medida, e não a da janela: o menu lateral
// do app come 240 px, então uma janela de 1180 deixa ~940 para o editor.
//   largo    (≥ 1100) — as três colunas;
//   médio    (≥ 720)  — a esquerda vira trilho, e abre por cima do canvas como gaveta;
//   estreito (< 720)  — uma coluna só: canvas em cima, camadas e inspetor em abas embaixo.

import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { LucideIcon } from 'lucide-react'
import { PanelLeftCloseIcon, PanelLeftOpenIcon, XIcon } from 'lucide-react'

export type Arranjo = 'largo' | 'medio' | 'estreito'

export type EditorShellProps = {
  barra: ReactNode
  esquerda: { titulo: string; icone: LucideIcon; conteudo: ReactNode }
  centro: ReactNode
  direita: { titulo: string; conteudo: ReactNode; rodape?: ReactNode }
  esquerdaRecolhida: boolean
  onRecolherEsquerda: (v: boolean) => void
  /** Avisado quando o arranjo muda — o canvas usa para decidir o zoom de "ajustar". */
  onArranjo?: (a: Arranjo) => void
}

const LARGO = 1100
const MEDIO = 720

export function EditorShell({
  barra, esquerda, centro, direita, esquerdaRecolhida, onRecolherEsquerda, onArranjo,
}: EditorShellProps) {
  const raiz = useRef<HTMLDivElement>(null)
  const [arranjo, setArranjo] = useState<Arranjo>('largo')
  const [gavetaAberta, setGavetaAberta] = useState(false)
  const [abaEstreita, setAbaEstreita] = useState<'esquerda' | 'direita'>('direita')

  useEffect(() => {
    const no = raiz.current
    if (!no) return
    const medir = () => {
      const w = no.getBoundingClientRect().width
      setArranjo(w >= LARGO ? 'largo' : w >= MEDIO ? 'medio' : 'estreito')
    }
    medir()
    const ro = new ResizeObserver(medir)
    ro.observe(no)
    return () => ro.disconnect()
  }, [])

  useEffect(() => onArranjo?.(arranjo), [arranjo, onArranjo])

  // A gaveta fecha no Esc, como qualquer camada por cima do conteúdo.
  useEffect(() => {
    if (!gavetaAberta) return
    const aoTeclar = (ev: KeyboardEvent) => ev.key === 'Escape' && setGavetaAberta(false)
    window.addEventListener('keydown', aoTeclar)
    return () => window.removeEventListener('keydown', aoTeclar)
  }, [gavetaAberta])

  const trilho = arranjo === 'medio' || (arranjo === 'largo' && esquerdaRecolhida)
  const Icone = esquerda.icone

  const colunaEsquerda = (
    <div className="flex flex-col min-h-0 h-full">
      <div className="flex items-center justify-between gap-2 px-3 pt-3 pb-2">
        <h3 className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wide">{esquerda.titulo}</h3>
        {arranjo === 'largo' ? (
          <button
            type="button"
            onClick={() => onRecolherEsquerda(true)}
            className="p-1 rounded-md text-text-tertiary hover:text-text hover:bg-white/[0.06]"
            title="Recolher a coluna"
            aria-label="Recolher a coluna"
          >
            <PanelLeftCloseIcon size={14} />
          </button>
        ) : (
          <button
            type="button"
            onClick={() => setGavetaAberta(false)}
            className="p-1 rounded-md text-text-tertiary hover:text-text hover:bg-white/[0.06]"
            aria-label="Fechar"
          >
            <XIcon size={14} />
          </button>
        )}
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto px-3 pb-3">{esquerda.conteudo}</div>
    </div>
  )

  const colunaDireita = (
    <div className="flex flex-col min-h-0 h-full">
      <div className="flex-1 min-h-0 overflow-y-auto p-4">{direita.conteudo}</div>
      {direita.rodape && <div className="border-t border-white/10 px-4 py-3">{direita.rodape}</div>}
    </div>
  )

  if (arranjo === 'estreito') {
    return (
      <div ref={raiz} className="flex-1 min-w-0 flex flex-col">
        <div className="border-b border-white/10 px-4 py-3">{barra}</div>
        <div className="p-3">{centro}</div>
        <div className="px-3 pb-6">
          <div className="flex gap-1 p-1 rounded-xl bg-black/25 border border-white/10 mb-3">
            {(['esquerda', 'direita'] as const).map((aba) => (
              <button
                key={aba}
                type="button"
                onClick={() => setAbaEstreita(aba)}
                className={`flex-1 rounded-lg px-3 py-1.5 text-xs font-medium ${
                  abaEstreita === aba ? 'bg-primary text-white' : 'text-text-secondary hover:bg-white/[0.07]'
                }`}
              >
                {aba === 'esquerda' ? esquerda.titulo : direita.titulo}
              </button>
            ))}
          </div>
          <div className="rounded-2xl border border-white/10 bg-white/[0.03]">
            {abaEstreita === 'esquerda' ? (
              <div className="p-3">{esquerda.conteudo}</div>
            ) : (
              <>
                <div className="p-4">{direita.conteudo}</div>
                {direita.rodape && <div className="border-t border-white/10 px-4 py-3">{direita.rodape}</div>}
              </>
            )}
          </div>
        </div>
      </div>
    )
  }

  return (
    <div ref={raiz} className="flex-1 min-w-0 flex flex-col h-[calc(100dvh-4rem)]">
      <div className="border-b border-white/10 px-4 py-2.5 shrink-0">{barra}</div>
      <div className="relative flex flex-1 min-h-0">
        {trilho ? (
          <div className="w-11 shrink-0 border-r border-white/10 flex flex-col items-center py-3 gap-2">
            <button
              type="button"
              onClick={() => (arranjo === 'largo' ? onRecolherEsquerda(false) : setGavetaAberta((v) => !v))}
              className={`p-2 rounded-lg transition-colors ${
                gavetaAberta ? 'bg-primary/20 text-text' : 'text-text-tertiary hover:text-text hover:bg-white/[0.06]'
              }`}
              title={arranjo === 'largo' ? 'Mostrar a coluna' : esquerda.titulo}
              aria-label={esquerda.titulo}
            >
              {arranjo === 'largo' ? <PanelLeftOpenIcon size={16} /> : <Icone size={16} />}
            </button>
          </div>
        ) : (
          <aside className="w-[232px] shrink-0 border-r border-white/10 min-h-0">{colunaEsquerda}</aside>
        )}

        {arranjo === 'medio' && gavetaAberta && (
          <>
            {/* O véu cobre só o centro: o inspetor continua clicável com a gaveta aberta. */}
            <div className="absolute top-0 bottom-0 left-11 right-[300px] z-30 bg-black/30" onClick={() => setGavetaAberta(false)} />
            <aside className="absolute left-11 top-0 bottom-0 z-40 w-[260px] border-r border-white/10 bg-[#14161a] shadow-2xl min-h-0">
              {colunaEsquerda}
            </aside>
          </>
        )}

        <main className="flex-1 min-w-0 min-h-0 overflow-y-auto p-4">{centro}</main>

        <aside
          className={`${arranjo === 'largo' ? 'w-[320px]' : 'w-[300px]'} shrink-0 border-l border-white/10 min-h-0`}
          aria-label={direita.titulo}
        >
          {colunaDireita}
        </aside>
      </div>
    </div>
  )
}
