export type SegmentedOption<T extends string> = { value: T; label: string; title?: string }

export type SegmentedProps<T extends string> = {
  value: T
  options: SegmentedOption<T>[]
  onChange: (value: T) => void
  /** Rótulo acima do controle. Opcional — em um grupo já rotulado, atrapalha. */
  label?: string
  size?: 'sm' | 'md'
}

/**
 * Escolha única entre poucas opções, sem abrir menu. É o controle certo quando o usuário precisa
 * ver todas as alternativas ao mesmo tempo — estilo de mostrador, âncora, modo de fundo.
 */
export function Segmented<T extends string>({ value, options, onChange, label, size = 'md' }: SegmentedProps<T>) {
  const pad = size === 'sm' ? 'px-2 py-1 text-[11px]' : 'px-3 py-1.5 text-xs'
  return (
    <div>
      {label && <label className="text-xs font-medium text-text-secondary mb-1.5 block">{label}</label>}
      <div className="flex flex-wrap gap-1 p-1 rounded-xl bg-black/25 border border-white/10">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            title={o.title}
            onClick={() => onChange(o.value)}
            className={`flex-1 min-w-fit rounded-lg font-medium transition-colors ${pad} ${
              value === o.value ? 'bg-primary text-white' : 'text-text-secondary hover:bg-white/[0.07]'
            }`}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  )
}
