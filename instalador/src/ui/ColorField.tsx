// Um campo de cor que aceita transparência.
//
// `<input type="color">` só entende `#rrggbb`: ligado direto a um token de tema ele apagaria o
// alfa de `rgba(242,244,246,.55)` na primeira edição — e mais da metade das cores dos dois temas
// de fábrica tem alfa. Então o campo guarda cor e opacidade separadas e só junta na saída.

import { useMemo } from 'react'

export type ColorFieldProps = {
  label?: string
  value: string
  onChange: (value: string) => void
  /** Mostra o controle de opacidade. Desligue onde alfa não faz sentido (fundo sólido). */
  alpha?: boolean
}

type Decomposta = { hex: string; alfa: number }

const doisDigitos = (n: number) => Math.round(n).toString(16).padStart(2, '0')

export function parseColor(css: string): Decomposta {
  const s = (css || '').trim()
  const rgba = s.match(/^rgba?\(\s*([\d.]+)[\s,]+([\d.]+)[\s,]+([\d.]+)\s*(?:[,/]\s*([\d.]+)\s*)?\)$/i)
  if (rgba) {
    return {
      hex: `#${doisDigitos(Number(rgba[1]))}${doisDigitos(Number(rgba[2]))}${doisDigitos(Number(rgba[3]))}`,
      alfa: rgba[4] === undefined ? 1 : Math.max(0, Math.min(1, Number(rgba[4]))),
    }
  }
  const hex8 = s.match(/^#([0-9a-f]{6})([0-9a-f]{2})$/i)
  if (hex8) return { hex: `#${hex8[1]}`, alfa: parseInt(hex8[2], 16) / 255 }
  const hex6 = s.match(/^#([0-9a-f]{6})$/i)
  if (hex6) return { hex: s.toLowerCase(), alfa: 1 }
  const hex3 = s.match(/^#([0-9a-f])([0-9a-f])([0-9a-f])$/i)
  if (hex3) return { hex: `#${hex3[1]}${hex3[1]}${hex3[2]}${hex3[2]}${hex3[3]}${hex3[3]}`.toLowerCase(), alfa: 1 }
  // Nomes CSS e tudo o mais que não dá para decompor: mostra escuro no seletor e preserva o texto.
  return { hex: '#000000', alfa: 1 }
}

export function joinColor(hex: string, alfa: number): string {
  if (alfa >= 0.999) return hex
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r},${g},${b},${Number(alfa.toFixed(2))})`
}

export function ColorField({ label, value, onChange, alpha = true }: ColorFieldProps) {
  const { hex, alfa } = useMemo(() => parseColor(value), [value])

  return (
    <div>
      {label && <label className="text-xs font-medium text-text-secondary mb-1.5 block">{label}</label>}
      <div className="flex items-center gap-2">
        <div
          className="w-9 h-9 rounded-lg border border-white/15 flex-shrink-0 relative overflow-hidden"
          style={{
            backgroundImage:
              'linear-gradient(45deg,#2a2a2a 25%,transparent 25%,transparent 75%,#2a2a2a 75%),linear-gradient(45deg,#2a2a2a 25%,#1a1a1a 25%,#1a1a1a 75%,#2a2a2a 75%)',
            backgroundSize: '8px 8px',
            backgroundPosition: '0 0, 4px 4px',
          }}
        >
          <input
            type="color"
            value={hex}
            onChange={(e) => onChange(joinColor(e.target.value, alfa))}
            className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
            aria-label={label ?? 'Cor'}
          />
          <span className="absolute inset-0 pointer-events-none" style={{ background: value }} />
        </div>
        <input
          value={value}
          onChange={(e) => onChange(e.target.value)}
          spellCheck={false}
          className="flex-1 min-w-0 bg-black/25 border border-white/10 rounded-lg px-2.5 py-2 text-xs font-mono text-text outline-none focus:border-primary/60"
        />
      </div>
      {alpha && (
        <div className="flex items-center gap-2 mt-1.5">
          <input
            type="range"
            min={0}
            max={100}
            value={Math.round(alfa * 100)}
            onChange={(e) => onChange(joinColor(hex, Number(e.target.value) / 100))}
            className="flex-1 cursor-pointer"
            aria-label="Opacidade"
          />
          <span className="text-[10px] font-mono text-text-tertiary w-9 text-right">{Math.round(alfa * 100)}%</span>
        </div>
      )}
    </div>
  )
}
