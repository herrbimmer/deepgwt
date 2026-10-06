// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Peças compartilhadas pelos painéis do editor.
//
// Duas delas carregam decisão de verdade e não são só enfeite:
//
//   `TokenColorField` deixa escolher entre um TOKEN da paleta e uma cor literal. Um token troca
//   sozinho entre dia, noite e farol; um literal não troca nunca. Esconder essa diferença faria o
//   usuário pintar tudo de literal e descobrir à noite que o tema não acompanha.
//
//   `TextStyleEditor` expõe `hostScaled`, que emite `calc(Npx * var(--gauge-num-scale, 1))` e
//   portanto respeita o ajuste de tamanho de número que o motorista faz no app do carro.

import type { ReactNode } from 'react'
import type { Color, FontRef, Paint, Palette, TextStyle } from '../model/spec'
import { isToken, tokenName } from '../model/spec'
import { ColorField, Segmented, SelectField, SliderField } from '../../ui'

export function Grupo({ titulo, dica, children }: { titulo: string; dica?: string; children: ReactNode }) {
  return (
    <section className="space-y-3">
      <div>
        <h4 className="text-xs font-semibold text-text uppercase tracking-wide">{titulo}</h4>
        {dica && <p className="text-[11px] text-text-tertiary mt-0.5 leading-snug">{dica}</p>}
      </div>
      {children}
    </section>
  )
}

export function Linha({ children }: { children: ReactNode }) {
  return <div className="grid grid-cols-2 gap-2">{children}</div>
}

export function NumberField({
  label, value, onChange, step = 1, min, max, unit,
}: {
  label: string
  value: number
  onChange: (v: number) => void
  step?: number
  min?: number
  max?: number
  unit?: string
}) {
  return (
    <div>
      <label className="text-xs font-medium text-text-secondary mb-1.5 block">{label}</label>
      <div className="flex items-center gap-1.5">
        <input
          type="number"
          value={Number.isFinite(value) ? value : 0}
          step={step}
          min={min}
          max={max}
          onChange={(e) => {
            const n = Number(e.target.value)
            if (Number.isFinite(n)) onChange(n)
          }}
          className="flex-1 min-w-0 bg-black/25 border border-white/10 rounded-lg px-2.5 py-2 text-sm text-text outline-none focus:border-primary/60"
        />
        {unit && <span className="text-[11px] text-text-tertiary">{unit}</span>}
      </div>
    </div>
  )
}

export function TextField({
  label, value, onChange, placeholder,
}: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <div>
      <label className="text-xs font-medium text-text-secondary mb-1.5 block">{label}</label>
      <input
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="w-full bg-black/25 border border-white/10 rounded-lg px-2.5 py-2 text-sm text-text outline-none focus:border-primary/60"
      />
    </div>
  )
}

export function Switch({ label, checked, onChange, dica }: { label: string; checked: boolean; onChange: (v: boolean) => void; dica?: string }) {
  return (
    <label className="flex items-start gap-2.5 cursor-pointer">
      <input
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="mt-0.5 w-4 h-4 accent-[var(--color-primary,#0a84ff)] cursor-pointer flex-shrink-0"
      />
      <span className="min-w-0">
        <span className="text-xs font-medium text-text-secondary block">{label}</span>
        {dica && <span className="text-[10px] text-text-tertiary block leading-snug">{dica}</span>}
      </span>
    </label>
  )
}

// --- cor ligada à paleta -------------------------------------------------------------------

export function TokenColorField({
  label, value, palette, onChange,
}: { label: string; value: Color; palette: Palette; onChange: (c: Color) => void }) {
  const tokens = Object.entries(palette.tokens)
  const usandoToken = isToken(value)

  return (
    <div>
      <label className="text-xs font-medium text-text-secondary mb-1.5 block">{label}</label>
      <SelectField
        value={usandoToken ? tokenName(value) : '__literal'}
        onChange={(e) => {
          const v = e.target.value
          onChange(v === '__literal' ? (palette.tokens[tokens[0][0]]?.day ?? '#ffffff') : `$${v}`)
        }}
        containerClassName="mb-1.5"
      >
        {tokens.map(([nome, t]) => (
          <option key={nome} value={nome}>
            {t.label} (muda com o modo)
          </option>
        ))}
        <option value="__literal">Cor fixa…</option>
      </SelectField>
      {usandoToken ? (
        <div className="flex items-center gap-2 px-1">
          <span
            className="w-4 h-4 rounded border border-white/20 flex-shrink-0"
            style={{ background: palette.tokens[tokenName(value)]?.day ?? '#000' }}
          />
          <span
            className="w-4 h-4 rounded border border-white/20 flex-shrink-0"
            style={{ background: palette.tokens[tokenName(value)]?.night ?? '#000' }}
          />
          <span className="text-[10px] text-text-tertiary">dia · noite</span>
        </div>
      ) : (
        <ColorField value={value} onChange={onChange} />
      )}
    </div>
  )
}

// --- tinta ----------------------------------------------------------------------------------

const TIPOS_DE_TINTA = [
  { value: 'none' as const, label: 'Nenhuma' },
  { value: 'solid' as const, label: 'Sólida' },
  { value: 'linear' as const, label: 'Linear' },
  { value: 'radial' as const, label: 'Radial' },
  { value: 'hubRadial' as const, label: 'No eixo' },
]

export function PaintField({
  label, value, palette, onChange, permitirHub = false,
}: { label: string; value: Paint; palette: Palette; onChange: (p: Paint) => void; permitirHub?: boolean }) {
  const opcoes = permitirHub ? TIPOS_DE_TINTA : TIPOS_DE_TINTA.filter((t) => t.value !== 'hubRadial')

  const trocar = (tipo: Paint['fill']) => {
    const base: Color = value.fill === 'solid' ? value.color : '$accent'
    if (tipo === 'none') return onChange({ fill: 'none' })
    if (tipo === 'solid') return onChange({ fill: 'solid', color: base })
    if (tipo === 'linear') return onChange({ fill: 'linear', from: base, to: '$faceLow', angleDeg: 135 })
    if (tipo === 'radial') return onChange({ fill: 'radial', from: base, to: '$faceLow', cx: 0.34, cy: 0.28, stopAt: 0.78 })
    return onChange({ fill: 'hubRadial', inner: '$bezel', mid: base, outer: '$ink', midAt: 0.58 })
  }

  return (
    <div className="space-y-2">
      <Segmented label={label} size="sm" value={value.fill} options={opcoes} onChange={trocar} />
      {value.fill === 'solid' && (
        <TokenColorField label="Cor" value={value.color} palette={palette} onChange={(c) => onChange({ ...value, color: c })} />
      )}
      {value.fill === 'linear' && (
        <>
          <TokenColorField label="De" value={value.from} palette={palette} onChange={(c) => onChange({ ...value, from: c })} />
          <TokenColorField label="Para" value={value.to} palette={palette} onChange={(c) => onChange({ ...value, to: c })} />
          <SliderField label="Ângulo" value={value.angleDeg} min={0} max={360} unit="°" onChange={(v) => onChange({ ...value, angleDeg: v })} />
        </>
      )}
      {value.fill === 'radial' && (
        <>
          <TokenColorField label="Centro" value={value.from} palette={palette} onChange={(c) => onChange({ ...value, from: c })} />
          <TokenColorField label="Borda" value={value.to} palette={palette} onChange={(c) => onChange({ ...value, to: c })} />
          <Linha>
            <SliderField label="Brilho X" value={Math.round(value.cx * 100)} min={0} max={100} unit="%" onChange={(v) => onChange({ ...value, cx: v / 100 })} />
            <SliderField label="Brilho Y" value={Math.round(value.cy * 100)} min={0} max={100} unit="%" onChange={(v) => onChange({ ...value, cy: v / 100 })} />
          </Linha>
          <SliderField label="Fim do degradê" value={Math.round(value.stopAt * 100)} min={20} max={100} unit="%" onChange={(v) => onChange({ ...value, stopAt: v / 100 })} />
        </>
      )}
      {value.fill === 'hubRadial' && (
        <>
          <p className="text-[10px] text-text-tertiary leading-snug">
            Degradê ancorado no eixo de rotação. Como o centro do degradê é o próprio eixo, girar o
            ponteiro não muda um pixel dele.
          </p>
          <TokenColorField label="No eixo" value={value.inner} palette={palette} onChange={(c) => onChange({ ...value, inner: c })} />
          <TokenColorField label="No meio" value={value.mid} palette={palette} onChange={(c) => onChange({ ...value, mid: c })} />
          <TokenColorField label="Na ponta" value={value.outer} palette={palette} onChange={(c) => onChange({ ...value, outer: c })} />
          <SliderField label="Onde fica o meio" value={Math.round(value.midAt * 100)} min={10} max={90} unit="%" onChange={(v) => onChange({ ...value, midAt: v / 100 })} />
        </>
      )}
    </div>
  )
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// --- tipografia -----------------------------------------------------------------------------

export const FONT_LABELS: Record<FontRef, string> = {
  sysSans: 'Sistema (sem serifa)',
  sysCondensed: 'Sistema (condensada)',
  sysSerif: 'Sistema (serifada)',
  sysMono: 'Sistema (monoespaçada)',
  exoV: 'Exo V (acompanha o tema)',
  michroma: 'Michroma (acompanha o tema)',
}

export function TextStyleEditor({
  label, value, palette, onChange, compacto = false,
}: { label: string; value: TextStyle; palette: Palette; onChange: (t: TextStyle) => void; compacto?: boolean }) {
  return (
    <div className="space-y-2.5 rounded-xl border border-white/10 p-3">
      <h5 className="text-[11px] font-semibold text-text-secondary uppercase tracking-wide">{label}</h5>
      <SelectField value={value.font} onChange={(e) => onChange({ ...value, font: e.target.value as FontRef })}>
        {(Object.keys(FONT_LABELS) as FontRef[]).map((f) => (
          <option key={f} value={f}>{FONT_LABELS[f]}</option>
        ))}
      </SelectField>
      <Linha>
        <NumberField label="Tamanho" value={value.sizePx} min={8} max={220} unit="px" onChange={(v) => onChange({ ...value, sizePx: v })} />
        <NumberField label="Peso" value={value.weight} min={100} max={900} step={100} onChange={(v) => onChange({ ...value, weight: v })} />
      </Linha>
      {!compacto && (
        <Linha>
          <NumberField label="Espaçamento" value={value.tracking} step={0.1} min={-3} max={12} unit="px" onChange={(v) => onChange({ ...value, tracking: v })} />
          <Segmented
            label="Caixa"
            size="sm"
            value={value.transform}
            options={[{ value: 'none', label: 'Normal' }, { value: 'upper', label: 'MAIÚSCULAS' }]}
            onChange={(v) => onChange({ ...value, transform: v })}
          />
        </Linha>
      )}
      <TokenColorField label="Cor" value={value.color} palette={palette} onChange={(c) => onChange({ ...value, color: c })} />
      {!compacto && (
        <div className="space-y-1.5">
          <Switch
            label="Dígitos de largura fixa"
            checked={value.tabular}
            dica="Sem isso os números dançam de lado a cada atualização."
            onChange={(v) => onChange({ ...value, tabular: v })}
          />
          <Switch
            label="Acompanhar o tamanho de número do carro"
            checked={value.hostScaled}
            dica="O motorista ajusta de 0,7 a 1,4 nos ajustes do painel. Ligue nas leituras grandes."
            onChange={(v) => onChange({ ...value, hostScaled: v })}
          />
        </div>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
