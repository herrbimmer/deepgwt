// Onde o elemento fica e quanto ele ocupa.
//
// Sem largura e sem altura. O tamanho sai de uma tabela por tipo (PP a GG) mais um ajuste fino, e
// o ajuste fino tem TETO CALCULADO AO VIVO: o maior fator que mantém toda a tinta do elemento
// fora das zonas que o veículo desenha. O controle trava ali, com a legenda dizendo qual zona
// limitou, em vez de deixar passar e só reclamar depois.

import { useSyncExternalStore } from 'react'
import { Banner, Segmented, SliderField } from '../../ui'
import type { Anchor, Element, SizeToken, ThemeSpec, ZoneId } from '../model/spec'
import { CANVAS_HEIGHT, CANVAS_WIDTH, SIZE_LABEL } from '../model/spec'
import { SCALE_MAX, SCALE_MIN, rectFor } from '../model/sizes'
import { firstBlocker, firstLegalSlot, isLegal, maxLegalScale, movedTo, softTouches } from '../model/collision'
import { placementZones, UNMAPPED_TELLTALES_NOTE } from '../model/zones'
import { assinarModoLivre, modoLivreAtivo } from '../model/freeMode'
import { Grupo, Linha, NumberField } from './shared'
import { ConditionField } from './ConditionField'
import { SelectField } from '../../ui'

const ANCORAS: { value: Anchor; label: string }[] = [
  { value: 'topLeft', label: '↖' }, { value: 'topCenter', label: '↑' }, { value: 'topRight', label: '↗' },
  { value: 'midLeft', label: '←' }, { value: 'center', label: '•' }, { value: 'midRight', label: '→' },
  { value: 'bottomLeft', label: '↙' }, { value: 'bottomCenter', label: '↓' }, { value: 'bottomRight', label: '↘' },
]

export type PositionPanelProps = {
  spec: ThemeSpec
  element: Element
  onChange: (el: Element) => void
}

export function PositionPanel({ spec, element, onChange }: PositionPanelProps) {
  const set = (el: Element) => onChange(el)
  const place = element.place
  const r = rectFor(element)
  const teto = maxLegalScale(element, SCALE_MAX, SCALE_MIN)
  const bloqueadorNoTeto = teto < SCALE_MAX
    ? firstBlocker({ ...element, place: { ...place, scale: Math.min(SCALE_MAX, teto + 0.01) } } as Element)
    : null
  const encostaSuave = softTouches(element)
  const livre = useSyncExternalStore(assinarModoLivre, modoLivreAtivo, () => false)
  // Em modo livre `isLegal` é sempre verdadeiro; o que importa aqui é se o elemento INVADE, que é
  // `firstBlocker` — senão o painel deixaria de avisar exatamente quando o aviso passa a ser a
  // única defesa que sobrou.
  const invasao = firstBlocker(element)
  const ilegal = !isLegal(element)

  /** Centro geométrico atual — é dele que saem centralizar e espelhar. */
  const centro = { x: r.x + r.width / 2, y: r.y + r.height / 2 }

  /**
   * Leva o CENTRO do elemento para onde se pede, convertendo de volta para a âncora.
   *
   * A âncora pode ser qualquer um dos nove pontos, então `at` não é o centro: o deslocamento entre
   * os dois é o que esta conta desfaz. Eixo com `null` fica onde está.
   */
  const paraCentro = (x: number | null, y: number | null) => {
    const desloc = { x: centro.x - place.at.x, y: centro.y - place.at.y }
    set(movedTo(element, {
      x: x === null ? place.at.x : Math.round(x - desloc.x),
      y: y === null ? place.at.y : Math.round(y - desloc.y),
    }))
  }

  /** Muda um campo que pode alterar a pegada e, se isso deixar o elemento ilegal, reposiciona. */
  const mudarPegada = (patch: Partial<Element['place']>) => {
    const candidato = { ...element, place: { ...place, ...patch } } as Element
    if (isLegal(candidato)) return set(candidato)
    const vaga = firstLegalSlot(spec, candidato)
    set(vaga ? movedTo(candidato, vaga) : candidato)
  }

  return (
    <div className="space-y-5">
      {ilegal && (
        <Banner tone="error">
          Este elemento está sobre uma área que o veículo desenha por cima do tema
          ({invasao?.zone.label}). Arraste-o ou reduza o tamanho.
        </Banner>
      )}
      {!ilegal && invasao && (
        <Banner tone="warning">
          Modo livre: este elemento está embaixo de {invasao.zone.label}, que o veículo desenha por
          cima do tema. Enquanto o indicador estiver aceso, o que está aqui não aparece.
        </Banner>
      )}

      <Grupo titulo="Zona" dica="Só orienta o encaixe e onde um elemento novo nasce. A posição continua livre.">
        <SelectField value={place.zone} onChange={(e) => set({ ...element, place: { ...place, zone: e.target.value as ZoneId } } as Element)}>
          {placementZones(spec).map((z) => (
            <option key={z.id} value={z.id}>{z.label}</option>
          ))}
        </SelectField>
      </Grupo>

      <Grupo titulo="Posição">
        <Linha>
          <NumberField label="X" value={place.at.x} onChange={(x) => set(movedTo(element, { ...place.at, x }))} unit="px" />
          <NumberField label="Y" value={place.at.y} onChange={(y) => set(movedTo(element, { ...place.at, y }))} unit="px" />
        </Linha>
        <div>
          <label className="text-xs font-medium text-text-secondary mb-1.5 block">
            Ponto de ancoragem
          </label>
          <div className="grid grid-cols-3 gap-1 w-fit">
            {ANCORAS.map((a) => (
              <button
                key={a.value}
                type="button"
                title={a.value}
                onClick={() => mudarPegada({ anchor: a.value })}
                className={`w-8 h-8 rounded-lg border text-sm transition-colors ${
                  place.anchor === a.value ? 'border-primary bg-primary/15 text-text' : 'border-white/10 text-text-tertiary hover:border-white/30'
                }`}
              >
                {a.label}
              </button>
            ))}
          </div>
          <p className="text-[10px] text-text-tertiary mt-1.5">
            A âncora é um ponto, não um canto: trocar de tamanho não desloca o centro visual.
          </p>
        </div>
        {/*
          Centralizar e espelhar à mão é conta de cabeça toda vez: a âncora não é o canto, então o
          X que centraliza depende do tamanho atual E do ponto de ancoragem escolhido. Estes três
          botões fazem a conta. Espelhar é o que monta um par simétrico de mostradores — duplicar
          o da esquerda e espelhar dá o da direita exatamente alinhado, que na mão nunca sai.
        */}
        <div className="flex flex-wrap gap-1.5">
          {[
            { label: 'Centro ↔', titulo: 'Centralizar na horizontal', fn: () => paraCentro(CANVAS_WIDTH / 2, null) },
            { label: 'Centro ↕', titulo: 'Centralizar na vertical', fn: () => paraCentro(null, CANVAS_HEIGHT / 2) },
            { label: 'Espelhar ↔', titulo: 'Refletir para o outro lado do painel', fn: () => paraCentro(CANVAS_WIDTH - centro.x, null) },
          ].map((b) => (
            <button
              key={b.label}
              type="button"
              title={b.titulo}
              onClick={b.fn}
              className="px-2.5 py-1 rounded-lg border border-white/10 text-[11px] text-text-secondary hover:border-white/30 hover:text-text transition-colors"
            >
              {b.label}
            </button>
          ))}
        </div>
        <p className="text-[10px] font-mono text-text-tertiary">
          Ocupa {r.width}×{r.height} px, de ({r.x}, {r.y}) a ({r.x + r.width}, {r.y + r.height}).
        </p>
      </Grupo>

      <Grupo
        titulo="Tamanho"
        dica={
          livre
            ? 'Escolhido por faixa, não por pixel: é o que mantém todo mostrador proporcional. Em modo livre o ajuste fino vai até o fim da faixa, sem teto.'
            : 'Escolhido por faixa, não por pixel: é o que mantém todo mostrador proporcional.'
        }
      >
        <Segmented
          value={place.size}
          size="sm"
          options={(['xs', 'sm', 'md', 'lg', 'xl'] as SizeToken[]).map((s) => ({ value: s, label: SIZE_LABEL[s] }))}
          onChange={(size) => mudarPegada({ size })}
        />
        <SliderField
          label="Ajuste fino"
          value={Math.round(place.scale * 100)}
          min={Math.round(SCALE_MIN * 100)}
          max={Math.round(SCALE_MAX * 100)}
          hardMax={Math.round(teto * 100)}
          capReason={bloqueadorNoTeto ? `Limitado por: ${bloqueadorNoTeto.zone.label}` : undefined}
          unit="%"
          onChange={(v) => set({ ...element, place: { ...place, scale: v / 100 } } as Element)}
        />
      </Grupo>

      <Grupo titulo="Camada">
        <Linha>
          <NumberField label="Ordem (z)" value={place.z} min={0} max={99} onChange={(z) => set({ ...element, place: { ...place, z } } as Element)} />
          <SliderField label="Opacidade" value={Math.round(place.opacity * 100)} min={0} max={100} unit="%" onChange={(v) => set({ ...element, place: { ...place, opacity: v / 100 } } as Element)} />
        </Linha>
      </Grupo>

      <VisibilidadePanel element={element} onChange={onChange} />

      {encostaSuave.length > 0 && (
        <Banner tone="warning">
          O ponteiro cruza {encostaSuave.map((z) => z.label).join(' e ')} durante a varredura. Isso é
          permitido — os dois temas de fábrica fazem igual — mas o traço fica por baixo do que o
          veículo desenha.
        </Banner>
      )}
      <p className="text-[10px] text-text-tertiary leading-snug">{UNMAPPED_TELLTALES_NOTE}</p>
    </div>
  )
}

function VisibilidadePanel({ element, onChange }: { element: Element; onChange: (el: Element) => void }) {
  return (
    <Grupo titulo="Quando aparece">
      <ConditionField
        value={element.visible}
        comAtalhos
        onChange={(visible) => onChange({ ...element, visible } as Element)}
      />
    </Grupo>
  )
}
