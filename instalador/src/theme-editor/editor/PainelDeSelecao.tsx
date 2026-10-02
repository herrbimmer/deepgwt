// O inspetor quando há vários elementos escolhidos: alinhar, distribuir, agrupar, guardar como
// bloco, apagar. Propriedades individuais continuam no elemento — com três tipos diferentes na
// seleção não existe "a" cor ou "o" tamanho comum.

import { useState } from 'react'
import {
  AlignCenterHorizontalIcon, AlignCenterVerticalIcon, AlignEndHorizontalIcon, AlignEndVerticalIcon,
  AlignHorizontalSpaceAroundIcon, AlignStartHorizontalIcon, AlignStartVerticalIcon, AlignVerticalSpaceAroundIcon,
  BoxesIcon, CopyIcon, FolderInputIcon, FolderOutputIcon, Trash2Icon,
} from 'lucide-react'
import { Banner } from '../../ui'
import type { Element } from '../model/spec'
import type { Alinhamento } from '../model/selecao'
import { Grupo } from '../panels/shared'

export type PainelDeSelecaoProps = {
  elementos: Element[]
  onAlinhar: (como: Alinhamento) => void
  onDistribuir: (eixo: 'h' | 'v') => void
  onAgrupar: () => void
  onDesagrupar: (() => void) | null
  onDuplicar: () => void
  onApagar: () => void
  onSalvarBloco: (nome: string) => void
  aviso: string | null
}

const botao = 'flex items-center justify-center p-2 rounded-lg bg-white/[0.06] text-text-secondary hover:bg-white/[0.12] hover:text-text disabled:opacity-30 disabled:hover:bg-white/[0.06]'

export function PainelDeSelecao(p: PainelDeSelecaoProps) {
  const [nomeDoBloco, setNomeDoBloco] = useState<string | null>(null)
  const n = p.elementos.length
  const alinhar: { como: Alinhamento; icone: typeof AlignStartVerticalIcon; titulo: string }[] = [
    { como: 'esquerda', icone: AlignStartVerticalIcon, titulo: 'Alinhar à esquerda' },
    { como: 'centroH', icone: AlignCenterVerticalIcon, titulo: 'Centralizar na horizontal' },
    { como: 'direita', icone: AlignEndVerticalIcon, titulo: 'Alinhar à direita' },
    { como: 'topo', icone: AlignStartHorizontalIcon, titulo: 'Alinhar em cima' },
    { como: 'centroV', icone: AlignCenterHorizontalIcon, titulo: 'Centralizar na vertical' },
    { como: 'base', icone: AlignEndHorizontalIcon, titulo: 'Alinhar embaixo' },
  ]

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-sm font-semibold text-text">{n} elementos</h3>
        <p className="text-[11px] text-text-tertiary truncate">{p.elementos.map((e) => e.name).join(', ')}</p>
      </div>

      <Grupo titulo="Alinhar" dica="Pelas bordas da seleção. Um elemento que cairia numa área do carro fica onde está.">
        <div className="grid grid-cols-6 gap-1.5">
          {alinhar.map((a) => (
            <button key={a.como} type="button" className={botao} title={a.titulo} aria-label={a.titulo} onClick={() => p.onAlinhar(a.como)}>
              <a.icone size={15} />
            </button>
          ))}
        </div>
      </Grupo>

      <Grupo titulo="Distribuir" dica="Espaço igual entre as caixas, do primeiro ao último. Precisa de três ou mais.">
        <div className="grid grid-cols-2 gap-1.5">
          <button type="button" className={`${botao} gap-1.5 text-xs`} disabled={n < 3} onClick={() => p.onDistribuir('h')}>
            <AlignHorizontalSpaceAroundIcon size={15} /> Na horizontal
          </button>
          <button type="button" className={`${botao} gap-1.5 text-xs`} disabled={n < 3} onClick={() => p.onDistribuir('v')}>
            <AlignVerticalSpaceAroundIcon size={15} /> Na vertical
          </button>
        </div>
      </Grupo>

      {p.aviso && <Banner tone="warning">{p.aviso}</Banner>}

      <Grupo titulo="Organizar">
        <div className="grid grid-cols-2 gap-1.5">
          <button type="button" className={`${botao} gap-1.5 text-xs`} onClick={p.onAgrupar} title="Agrupar (Ctrl/Cmd + G)">
            <FolderInputIcon size={14} /> Agrupar
          </button>
          <button type="button" className={`${botao} gap-1.5 text-xs`} disabled={!p.onDesagrupar} onClick={() => p.onDesagrupar?.()} title="Desagrupar (Ctrl/Cmd + Shift + G)">
            <FolderOutputIcon size={14} /> Desagrupar
          </button>
          <button type="button" className={`${botao} gap-1.5 text-xs`} onClick={p.onDuplicar} title="Duplicar (Ctrl/Cmd + D)">
            <CopyIcon size={14} /> Duplicar
          </button>
          <button type="button" className={`${botao} gap-1.5 text-xs hover:!text-danger`} onClick={p.onApagar} title="Apagar (Delete)">
            <Trash2Icon size={14} /> Apagar
          </button>
        </div>
      </Grupo>

      <Grupo titulo="Guardar como bloco" dica="Vai para a biblioteca deste computador e entra em qualquer tema pelo Adicionar elemento.">
        {nomeDoBloco === null ? (
          <button type="button" className={`${botao} w-full gap-1.5 text-xs`} onClick={() => setNomeDoBloco('')}>
            <BoxesIcon size={14} /> Salvar seleção como bloco
          </button>
        ) : (
          <div className="flex items-center gap-1.5">
            <input
              autoFocus
              value={nomeDoBloco}
              placeholder="Nome do bloco"
              onChange={(e) => setNomeDoBloco(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  p.onSalvarBloco(nomeDoBloco)
                  setNomeDoBloco(null)
                }
                if (e.key === 'Escape') setNomeDoBloco(null)
              }}
              className="flex-1 min-w-0 bg-black/25 border border-primary/50 rounded-lg px-2.5 py-1.5 text-xs text-text outline-none"
            />
            <button
              type="button"
              className="px-2.5 py-1.5 rounded-lg bg-primary text-white text-xs"
              onClick={() => {
                p.onSalvarBloco(nomeDoBloco)
                setNomeDoBloco(null)
              }}
            >
              Salvar
            </button>
          </div>
        )}
      </Grupo>
    </div>
  )
}
