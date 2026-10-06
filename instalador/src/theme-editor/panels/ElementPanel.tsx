// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O painel do elemento selecionado. Abas porque um mostrador tem gente demais para uma coluna só.

import { useState } from 'react'
import { Trash2Icon } from 'lucide-react'
import { Segmented } from '../../ui'
import type { Element, ElementKind, ThemeSpec } from '../model/spec'
import { createElement } from '../model/defaults'
import { firstLegalSlot, isLegal, movedTo } from '../model/collision'
import { HINT_BY_KEY } from '../model/dataSources'
import { DataPanel } from './DataPanel'
import { GaugePanel } from './GaugePanel'
import { PositionPanel } from './PositionPanel'
import { TypographyPanel } from './TypographyPanel'
import { Grupo, TextField } from './shared'

export const KIND_LABELS: Record<ElementKind, string> = {
  dial: 'Mostrador redondo',
  barLinear: 'Barra',
  readout: 'Leitura',
  indicator: 'Indicador',
  media: 'Mídia',
  decor: 'Decoração',
  image: 'Imagem',
  shape: 'Forma',
  chart: 'Mini-histórico',
}

/**
 * O que o "Adicionar elemento" oferece. Mais itens que tipos: o anel e a marcha são estilos de
 * tipos que já existem, mas quem procura "anel de bateria" não sabe que é um mostrador.
 */
export const ITENS_PARA_ADICIONAR: { kind: ElementKind; style?: string; rotulo: string; dica: string }[] = [
  { kind: 'dial', rotulo: 'Mostrador redondo', dica: 'Velocímetro, conta-giros, potência.' },
  { kind: 'dial', style: 'ring', rotulo: 'Anel de progresso', dica: 'Bateria, combustível, qualquer porcentagem em anel.' },
  { kind: 'barLinear', rotulo: 'Barra', dica: 'Nível deitado ou em pé.' },
  { kind: 'readout', rotulo: 'Leitura', dica: 'Um número grande com legenda.' },
  { kind: 'readout', style: 'gear', rotulo: 'Marcha em destaque', dica: 'A fila P R N D com a marcha atual acesa.' },
  { kind: 'chart', rotulo: 'Mini-histórico', dica: 'A linha dos últimos minutos de uma leitura.' },
  { kind: 'indicator', rotulo: 'Indicador', dica: 'Luz que acende: farol, ESP, carregando.' },
  { kind: 'media', rotulo: 'Mídia', dica: 'Música tocando, com capa.' },
  { kind: 'image', rotulo: 'Imagem ou logo', dica: 'PNG, JPEG ou SVG seu, embutido no tema.' },
  { kind: 'shape', style: 'rect', rotulo: 'Moldura', dica: 'Retângulo com contorno ou preenchido.' },
  { kind: 'shape', style: 'line', rotulo: 'Divisória', dica: 'Uma linha para separar blocos.' },
  { kind: 'shape', style: 'circle', rotulo: 'Círculo', dica: 'Disco ou aro.' },
  { kind: 'decor', rotulo: 'Decoração', dica: 'Painel translúcido ou brilho de fundo.' },
]

/** Uma descrição sem jargão: "Velocidade — mostrador". É o que o modo Básico mostra. */
export function rotuloDoElemento(el: Element): string {
  const chave = 'binding' in el && el.binding ? el.binding.key : null
  const dado = chave ? HINT_BY_KEY.get(chave)?.label : null
  const tipo =
    el.kind === 'dial' && el.style === 'ring' ? 'anel'
      : el.kind === 'readout' && el.style === 'gear' ? 'marcha'
        : el.kind === 'readout' && el.style === 'clock' ? 'relógio'
          : KIND_LABELS[el.kind].toLowerCase()
  return dado ? `${dado} — ${tipo}` : KIND_LABELS[el.kind]
}

type Aba = 'elemento' | 'dados' | 'estilo' | 'texto' | 'posicao'

const ABAS: { value: Aba; label: string }[] = [
  { value: 'elemento', label: 'Geral' },
  { value: 'dados', label: 'Dados' },
  { value: 'estilo', label: 'Estilo' },
  { value: 'texto', label: 'Texto' },
  { value: 'posicao', label: 'Posição' },
]

export type ElementPanelProps = {
  spec: ThemeSpec
  element: Element
  onChange: (el: Element) => void
  /** Mudanças que tocam o tema inteiro: fontes empacotadas e estilos de texto compartilhados. */
  onChangeSpec: (spec: ThemeSpec) => void
  onDelete: () => void
  fixados: Record<string, string>
  onFixar: (chave: string, valor: string | null) => void
}

export function ElementPanel({ spec, element, onChange, onChangeSpec, onDelete, fixados, onFixar }: ElementPanelProps) {
  const [aba, setAba] = useState<Aba>('elemento')

  /**
   * Trocar de tipo recria o elemento com padrões coerentes do tipo novo, preservando nome e
   * posição. Se a pegada nova cair em cima de uma zona do veículo, o editor reposiciona sozinho —
   * é a mesma garantia que vale ao criar: o editor nunca produz um elemento ilegal.
   */
  const trocarTipo = (kind: ElementKind) => {
    if (kind === element.kind) return
    const novo = createElement({ kind, zone: element.place.zone, name: element.name }, element.place.z - 1)
    const posicionado = {
      ...novo,
      id: element.id,
      name: element.name,
      groupId: element.groupId,
      place: { ...novo.place, zone: element.place.zone, at: element.place.at, z: element.place.z },
    } as Element
    if (isLegal(posicionado)) return onChange(posicionado)
    const vaga = firstLegalSlot(spec, posicionado)
    onChange(vaga ? movedTo(posicionado, vaga) : posicionado)
  }

  const semLeitura = element.kind === 'readout' && element.style === 'clock'
  const semDados = element.kind === 'decor' || element.kind === 'image' || element.kind === 'shape'

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-text truncate">{element.name}</h3>
          <p className="text-[11px] text-text-tertiary">{rotuloDoElemento(element)}</p>
        </div>
        <button
          type="button"
          onClick={onDelete}
          className="p-1.5 rounded-lg text-text-tertiary hover:text-danger hover:bg-danger/10 flex-shrink-0"
          title="Remover elemento (Delete)"
        >
          <Trash2Icon size={14} />
        </button>
      </div>

      <Segmented value={aba} options={ABAS} size="sm" onChange={setAba} />

      {aba === 'elemento' && (
        <div className="space-y-5">
          <Grupo titulo="Identificação">
            <TextField label="Nome" value={element.name} onChange={(name) => onChange({ ...element, name } as Element)} />
          </Grupo>
          <Grupo titulo="Tipo" dica="Trocar recria o elemento com padrões do tipo novo, mantendo nome e posição.">
            <div className="grid grid-cols-2 gap-1.5">
              {(Object.keys(KIND_LABELS) as ElementKind[]).map((k) => (
                <button
                  key={k}
                  type="button"
                  onClick={() => trocarTipo(k)}
                  className={`px-2.5 py-2 rounded-xl text-xs font-medium transition-colors ${
                    element.kind === k ? 'bg-primary text-white' : 'bg-white/[0.06] text-text-secondary hover:bg-white/[0.1]'
                  }`}
                >
                  {KIND_LABELS[k]}
                </button>
              ))}
            </div>
          </Grupo>
        </div>
      )}

      {aba === 'dados' && (
        semDados ? (
          <p className="text-xs text-text-tertiary">{KIND_LABELS[element.kind]} não lê nada do carro.</p>
        ) : element.kind === 'media' ? (
          <p className="text-xs text-text-tertiary">
            O elemento de mídia lê título, artista e capa do álbum de uma vez — não há o que escolher.
          </p>
        ) : (
          <DataPanel
            binding={element.binding}
            map={element.map}
            semLeitura={semLeitura}
            // Num mostrador redondo a escala mora na aba Estilo, dita no vocabulário de painel
            // (VMAX, reescalonar, seguir a placa). Dois editores para o mesmo campo divergem.
            semEscala={element.kind === 'dial'}
            fixados={fixados}
            onFixar={onFixar}
            onChange={({ majors, ...patch }) => {
              if (element.kind === 'dial' && majors) {
                onChange({ ...element, ...patch, ticks: { ...element.ticks, majors } } as Element)
              } else {
                onChange({ ...element, ...patch } as Element)
              }
            }}
          />
        )
      )}

      {aba === 'estilo' && <GaugePanel element={element} palette={spec.palette} onChange={onChange} />}

      {aba === 'texto' && (
        <TypographyPanel spec={spec} element={element} onChangeElement={onChange} onChangeSpec={onChangeSpec} />
      )}

      {aba === 'posicao' && <PositionPanel spec={spec} element={element} onChange={onChange} />}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
