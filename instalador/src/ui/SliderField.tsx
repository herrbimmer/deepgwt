export type SliderFieldProps = {
  label: string
  value: number
  min: number
  max: number
  step?: number
  unit?: string
  /**
   * Teto real, quando for menor que `max`. O editor de temas calcula ao vivo o maior ajuste fino
   * que mantém o elemento fora das zonas do veículo: em vez de deixar o usuário passar do limite e
   * só depois descobrir, o controle trava aqui e a legenda diz o motivo.
   */
  hardMax?: number
  capReason?: string
  onChange: (value: number) => void
}

export function SliderField({
  label, value, min, max, step = 1, unit = '', hardMax, capReason, onChange,
}: SliderFieldProps) {
  const teto = hardMax != null ? Math.min(max, hardMax) : max
  const travado = hardMax != null && hardMax < max
  const pct = (n: number) => ((n - min) / (max - min)) * 100

  return (
    <div>
      <div className="flex items-center justify-between mb-1.5">
        <label className="text-xs font-medium text-text-secondary">{label}</label>
        <span className="text-xs font-mono text-text-tertiary">
          {value}
          {unit}
        </span>
      </div>
      <div className="relative">
        {travado && (
          <div
            className="absolute top-1/2 -translate-y-1/2 h-1 rounded-r-full bg-danger/25 pointer-events-none"
            style={{ left: `${pct(teto)}%`, right: 0 }}
          />
        )}
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(e) => onChange(Math.min(Number(e.target.value), teto))}
          className="w-full cursor-pointer relative"
        />
      </div>
      {travado && capReason && <p className="text-[10px] text-danger/80 mt-1">{capReason}</p>}
    </div>
  )
}
