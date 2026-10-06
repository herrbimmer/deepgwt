// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O inspetor do modo Básico: estilo, cores e o que mostrar, sem chave nenhuma à vista.
//
// Duas telas. Sem elemento escolhido: a aparência do tema inteiro (destaque, claro ou escuro,
// fundo). Com um escolhido: o que ele mostra, dito em português, o tamanho e a cor. Tudo que
// exige saber como o carro manda o dado — escala, marcações, transformação — fica no Avançado,
// que continua a um clique.

import { useState } from 'react'
import { MoonIcon, SunIcon, WandSparklesIcon } from 'lucide-react'
import { Banner, ColorField, Segmented } from '../../ui'
import type { Element, SizeToken, ThemeSpec } from '../model/spec'
import { CANVAS_WIDTH, SIZE_LABEL } from '../model/spec'
import { rectFor } from '../model/sizes'
import { firstLegalSlot, isLegal, movedTo } from '../model/collision'
import { DATA_SOURCES, GROUP_ORDER, HINT_BY_KEY } from '../model/dataSources'
import { mapForKey } from '../model/defaults'
import { textosDe, comTexto } from '../model/estilos'
import { DESTAQUES, lerCor, luminancia, recolorir, fundoDoTom, type Tom } from '../assistente/cores'
import { BackgroundPanel } from '../BackgroundPanel'
import { rotuloDoElemento } from './ElementPanel'
import { Grupo, TextField, TokenColorField } from './shared'

/** Claro ou escuro, deduzido do texto principal do tema: texto escuro é tema claro. */
function tomDe(spec: ThemeSpec): Tom {
  const ink = lerCor(spec.palette.tokens.ink?.day ?? '#fff')
  return ink && luminancia(ink) < 0.3 ? 'claro' : 'escuro'
}

export function PainelBasicoDoTema({ spec, onChange, onAssistente }: {
  spec: ThemeSpec
  onChange: (s: ThemeSpec) => void
  onAssistente: () => void
}) {
  const [aviso, setAviso] = useState<string | null>(null)
  const destaque = spec.palette.tokens.accent?.day ?? '#0a84ff'
  const tom = tomDe(spec)

  const aplicar = (cor: string, novoTom: Tom | null) => {
    const { palette, ajustada } = recolorir(spec.palette, cor, novoTom)
    const background = novoTom && novoTom !== tom ? fundoDoTom(novoTom, cor, spec.background) : spec.background
    onChange({ ...spec, palette, background })
    setAviso(ajustada ? 'A cor foi ajustada para contrastar com o fundo.' : null)
  }

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-sm font-semibold text-text">Aparência</h3>
        <p className="text-[11px] text-text-tertiary">Clique num elemento do painel para mudar só ele.</p>
      </div>

      <Grupo titulo="Cor de destaque" dica="Ponteiros, arcos e realces. As cores de noite e do farol continuam as do carro.">
        <div className="flex flex-wrap gap-2">
          {DESTAQUES.map((d) => (
            <button
              key={d.cor}
              type="button"
              title={d.nome}
              aria-label={d.nome}
              onClick={() => aplicar(d.cor, null)}
              className={`w-8 h-8 rounded-full border transition-transform active:scale-95 ${
                destaque.toLowerCase() === d.cor ? 'border-white ring-2 ring-primary/70' : 'border-white/20'
              }`}
              style={{ background: d.cor }}
            />
          ))}
        </div>
        <ColorField value={destaque} alpha={false} onChange={(c) => aplicar(c, null)} />
        {aviso && <p className="text-[11px] text-amber-300/90">{aviso}</p>}
      </Grupo>

      <Grupo titulo="Fundo claro ou escuro">
        <div className="grid grid-cols-2 gap-2">
          {(['escuro', 'claro'] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => aplicar(destaque, t)}
              className={`flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl text-xs border ${
                tom === t ? 'border-primary bg-primary/15 text-text' : 'border-white/10 text-text-secondary hover:border-white/25'
              }`}
            >
              {t === 'escuro' ? <MoonIcon size={13} /> : <SunIcon size={13} />}
              {t === 'escuro' ? 'Escuro' : 'Claro'}
            </button>
          ))}
        </div>
      </Grupo>

      <BackgroundPanel background={spec.background} onChange={(background) => onChange({ ...spec, background })} />

      <button
        type="button"
        onClick={onAssistente}
        className="w-full flex items-center justify-center gap-1.5 px-3 py-2 rounded-xl border border-white/10 text-xs text-text-secondary hover:text-text hover:border-white/25"
      >
        <WandSparklesIcon size={13} />
        Começar outro tema com o assistente
      </button>
    </div>
  )
}

/** As chaves que o Básico oferece: as exibíveis, sem as vazias no carro hoje. */
const OPCOES = DATA_SOURCES.filter((s) => s.channel !== 'writeOnly' && !s.hint.stub && s.hint.format !== 'json' && s.hint.format !== 'dataUrl')

export function PainelBasicoDoElemento({ spec, element, onChange, onAvancado }: {
  spec: ThemeSpec
  element: Element
  onChange: (el: Element) => void
  onAvancado: () => void
}) {
  const chave = 'binding' in element && element.binding ? element.binding.key : null
  const lerDoCarro = chave !== null && element.kind !== 'media'
  const texto = textosDe(element)[0] ?? null

  const trocarDado = (key: string) => {
    if (!('binding' in element) || !element.binding) return
    const m = mapForKey(key)
    const nome = HINT_BY_KEY.get(key)?.label ?? element.name
    const base = { ...element, name: nome, binding: { ...m.binding, fallback: element.binding.fallback ?? '--' }, map: m.map } as Element
    if (base.kind === 'dial' && m.majors.length) {
      onChange({ ...base, ticks: { ...base.ticks, majors: m.majors }, center: base.center ? { ...base.center, label: nome } : null })
    } else if (base.kind === 'readout' && base.label) {
      onChange({ ...base, label: nome })
    } else onChange(base)
  }

  const mudarTamanho = (size: SizeToken) => {
    const candidato = { ...element, place: { ...element.place, size } } as Element
    if (isLegal(candidato)) return onChange(candidato)
    const vaga = firstLegalSlot(spec, candidato)
    onChange(vaga ? movedTo(candidato, vaga) : candidato)
  }

  const r = rectFor(element)
  const espelhar = () => {
    const cx = r.x + r.width / 2
    const alvo = movedTo(element, { x: Math.round(element.place.at.x + (CANVAS_WIDTH - cx - cx)), y: element.place.at.y })
    if (isLegal(alvo)) onChange(alvo)
  }

  return (
    <div className="space-y-5">
      <div>
        <h3 className="text-sm font-semibold text-text truncate">{element.name}</h3>
        <p className="text-[11px] text-text-tertiary">{rotuloDoElemento(element)}</p>
      </div>

      <TextField label="Nome" value={element.name} onChange={(name) => onChange({ ...element, name } as Element)} />

      {lerDoCarro && (
        <Grupo titulo="O que mostra">
          <select
            value={chave ?? ''}
            onChange={(e) => trocarDado(e.target.value)}
            className="w-full bg-black/25 border border-white/10 rounded-lg px-2.5 py-2 text-sm text-text outline-none focus:border-primary/60"
          >
            {GROUP_ORDER.map((g) => {
              const itens = OPCOES.filter((s) => s.hint.group === g)
              if (!itens.length) return null
              return (
                <optgroup key={g} label={g}>
                  {itens.map((s) => (
                    <option key={s.key} value={s.key}>{s.hint.label}</option>
                  ))}
                </optgroup>
              )
            })}
          </select>
        </Grupo>
      )}

      <Grupo titulo="Tamanho">
        <Segmented
          value={element.place.size}
          size="sm"
          options={(['xs', 'sm', 'md', 'lg', 'xl'] as SizeToken[]).map((s) => ({ value: s, label: SIZE_LABEL[s] }))}
          onChange={mudarTamanho}
        />
      </Grupo>

      {texto && (
        <Grupo titulo="Cor">
          <TokenColorField
            label={texto.rotulo}
            value={texto.estilo.color}
            palette={spec.palette}
            onChange={(color) => onChange(comTexto(element, texto.chave, { ...texto.estilo, color }))}
          />
        </Grupo>
      )}

      <Grupo titulo="Posição">
        <div className="flex flex-wrap gap-1.5">
          <button
            type="button"
            onClick={() => {
              const alvo = movedTo(element, { x: Math.round(element.place.at.x + (CANVAS_WIDTH / 2 - (r.x + r.width / 2))), y: element.place.at.y })
              if (isLegal(alvo)) onChange(alvo)
            }}
            className="px-2.5 py-1 rounded-lg border border-white/10 text-[11px] text-text-secondary hover:border-white/30 hover:text-text"
          >
            Centralizar ↔
          </button>
          <button
            type="button"
            onClick={espelhar}
            className="px-2.5 py-1 rounded-lg border border-white/10 text-[11px] text-text-secondary hover:border-white/30 hover:text-text"
          >
            Espelhar ↔
          </button>
        </div>
        <p className="text-[10px] text-text-tertiary">Ou arraste no painel. Setas movem 1 px; Shift + setas, 10 px.</p>
      </Grupo>

      {element.kind === 'dial' && element.style !== 'ring' && (
        <Banner tone="info">Escala, marcações e ponteiro deste mostrador ficam no modo Avançado.</Banner>
      )}

      <button type="button" onClick={onAvancado} className="text-[11px] text-primary hover:underline">
        Mais opções no modo Avançado
      </button>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
