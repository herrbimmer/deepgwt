// O assistente de três passos do modo Básico: estilo, cores, o que mostrar.
//
// A prévia à direita é o tema gerado de verdade, no mesmo iframe do editor, tocando o cenário
// "Cidade". Cada escolha regera o tema inteiro — é barato (só dados) e evita que a prévia mostre
// um estado que o botão "Criar" não produziria.

import { useMemo, useState } from 'react'
import { CheckIcon, ChevronLeftIcon, ChevronRightIcon, WandSparklesIcon } from 'lucide-react'
import { Banner, Button, ColorField, Modal, Segmented, TextInput } from '../../ui'
import { ClusterCanvas } from '../ClusterCanvas'
import { usePreviaTocando } from '../preview/usePreviaTocando'
import { DESTAQUES, type Tom } from './cores'
import {
  ESTILOS, ITENS, ITENS_INDISPONIVEIS, ITENS_PADRAO, gerarTema,
  type EstiloDoAssistente, type ItemDoAssistente,
} from './gerarTema'
import type { ThemeSpec } from '../model/spec'

const MINIATURA: Record<EstiloDoAssistente, string> = {
  classico: '/themes/cupertino-ultra/thumbnail.png',
  esportivo: '/themes/stuttgart-ntg55/thumbnail.png',
  digital: '/themes/minimalista/thumbnail.png',
  minimalista: '/themes/minimalista/thumbnail.png',
}

export function AssistenteDeTema({ onCriar, onFechar }: { onCriar: (spec: ThemeSpec) => void; onFechar: () => void }) {
  const [passo, setPasso] = useState(0)
  const [nome, setNome] = useState('Meu painel')
  const [estilo, setEstilo] = useState<EstiloDoAssistente>('classico')
  const [destaque, setDestaque] = useState('#0a84ff')
  const [tom, setTom] = useState<Tom>('escuro')
  const [itens, setItens] = useState<Set<ItemDoAssistente>>(new Set(ITENS_PADRAO))

  const gerado = useMemo(
    () => gerarTema({ nome, estilo, destaque, tom, itens: [...itens] }),
    [nome, estilo, destaque, tom, itens],
  )
  const canal = usePreviaTocando('cidade')

  const alternar = (i: ItemDoAssistente) =>
    setItens((atual) => {
      const n = new Set(atual)
      if (n.has(i)) n.delete(i)
      else n.add(i)
      return n
    })

  const PASSOS = ['Estilo', 'Cores', 'O que mostrar']

  return (
    <Modal onClose={onFechar} maxWidth="max-w-5xl">
      <div className="max-h-[80vh] overflow-y-auto -mx-1 px-1">
      <div className="flex items-center gap-2 mb-1">
        <WandSparklesIcon size={18} className="text-primary" />
        <h2 className="text-lg font-semibold text-text">Criar com o assistente</h2>
      </div>
      <p className="text-xs text-text-tertiary mb-4">
        Três escolhas e o painel sai pronto — sem precisar saber o nome de chave nenhuma. Depois dá
        para mexer em tudo no editor.
      </p>

      <div className="flex items-center gap-2 mb-4">
        {PASSOS.map((p, i) => (
          <button
            key={p}
            type="button"
            onClick={() => setPasso(i)}
            className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] border ${
              i === passo ? 'border-primary bg-primary/20 text-text' : i < passo ? 'border-white/15 text-text-secondary' : 'border-white/10 text-text-tertiary'
            }`}
          >
            <span className="w-4 h-4 rounded-full bg-white/10 flex items-center justify-center text-[10px]">
              {i < passo ? <CheckIcon size={10} /> : i + 1}
            </span>
            {p}
          </button>
        ))}
      </div>

      <div className="grid md:grid-cols-[1fr_1.15fr] gap-5">
        <div className="min-w-0 space-y-4">
          {passo === 0 && (
            <>
              <div>
                <label className="text-xs font-medium text-text-secondary mb-1.5 block">Nome do tema</label>
                <TextInput value={nome} onChange={(e) => setNome(e.target.value)} />
              </div>
              <div className="grid grid-cols-2 gap-2">
                {ESTILOS.map((e) => (
                  <button
                    key={e.id}
                    type="button"
                    onClick={() => setEstilo(e.id)}
                    className={`text-left rounded-xl border overflow-hidden transition-colors ${
                      estilo === e.id ? 'border-primary ring-1 ring-primary/50' : 'border-white/10 hover:border-white/25'
                    }`}
                  >
                    <img src={MINIATURA[e.id]} alt="" className={`w-full aspect-[8/3] object-cover ${e.id === 'minimalista' ? 'grayscale' : ''}`} />
                    <div className="p-2">
                      <div className="text-xs font-medium text-text">{e.nome}</div>
                      <div className="text-[10px] text-text-tertiary leading-snug">{e.descricao}</div>
                    </div>
                  </button>
                ))}
              </div>
            </>
          )}

          {passo === 1 && (
            <>
              <div>
                <label className="text-xs font-medium text-text-secondary mb-1.5 block">Cor de destaque</label>
                <div className="flex flex-wrap gap-2 mb-2">
                  {DESTAQUES.map((d) => (
                    <button
                      key={d.cor}
                      type="button"
                      title={d.nome}
                      aria-label={d.nome}
                      onClick={() => setDestaque(d.cor)}
                      className={`w-8 h-8 rounded-full border ${destaque.toLowerCase() === d.cor ? 'border-white ring-2 ring-primary/70' : 'border-white/20'}`}
                      style={{ background: d.cor }}
                    />
                  ))}
                </div>
                <ColorField value={destaque} alpha={false} onChange={setDestaque} />
              </div>
              <Segmented
                label="Fundo"
                value={tom}
                options={[
                  { value: 'escuro', label: 'Escuro' },
                  { value: 'claro', label: 'Claro' },
                ]}
                onChange={setTom}
              />
              <p className="text-[11px] text-text-tertiary leading-snug">
                O tema troca sozinho para as cores de noite quando você escolher "Noite" no carro, e
                para a cor do farol quando ele acender. O destaque é ajustado se ficar sem contraste
                com o fundo.
              </p>
            </>
          )}

          {passo === 2 && (
            <>
              {(['Condução', 'Energia', 'Painel'] as const).map((g) => (
                <div key={g}>
                  <h4 className="text-[11px] font-semibold text-text-tertiary uppercase tracking-wide mb-1.5">{g}</h4>
                  <div className="grid grid-cols-2 gap-1.5">
                    {ITENS.filter((i) => i.grupo === g).map((i) => (
                      <label key={i.id} className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg border border-white/10 cursor-pointer hover:border-white/25">
                        <input type="checkbox" checked={itens.has(i.id)} onChange={() => alternar(i.id)} className="w-4 h-4" />
                        <span className="text-xs text-text-secondary">{i.nome}</span>
                      </label>
                    ))}
                  </div>
                </div>
              ))}
              <div className="space-y-1">
                {ITENS_INDISPONIVEIS.map((i) => (
                  <p key={i.nome} className="text-[11px] text-text-tertiary">
                    <span className="line-through">{i.nome}</span> — {i.motivo}.
                  </p>
                ))}
              </div>
            </>
          )}
        </div>

        <div className="min-w-0">
          <ClusterCanvas
            spec={gerado.spec}
            activePageId={gerado.spec.pages[0].id}
            selecionados={[]}
            onSelecionar={() => {}}
            onMover={() => {}}
            canal={canal}
            mostrarZonas={false}
            zoom="ajustar"
            somenteLeitura
          />
          <p className="text-[11px] text-text-tertiary mt-2">
            Prévia com o cenário "Cidade". {gerado.spec.pages[0].elements.length} elementos.
          </p>
          {gerado.avisos.map((a) => (
            <Banner key={a} tone="info" className="mt-2">{a}</Banner>
          ))}
        </div>
      </div>

      <div className="flex gap-3 mt-6">
        <Button variant="ghost" fullWidth={false} icon={ChevronLeftIcon} onClick={() => (passo === 0 ? onFechar() : setPasso(passo - 1))}>
          {passo === 0 ? 'Cancelar' : 'Voltar'}
        </Button>
        <div className="flex-1" />
        {passo < 2 ? (
          <Button variant="primary" fullWidth={false} icon={ChevronRightIcon} iconPosition="right" onClick={() => setPasso(passo + 1)}>
            Próximo
          </Button>
        ) : (
          <Button variant="primary" fullWidth={false} icon={CheckIcon} disabled={itens.size === 0} onClick={() => onCriar(gerado.spec)}>
            Criar tema
          </Button>
        )}
      </div>
      </div>
    </Modal>
  )
}
