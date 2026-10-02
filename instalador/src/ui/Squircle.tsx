import { forwardRef, useLayoutEffect, useRef, useState, type ElementType, type ReactNode } from 'react'
import { squirclePathD } from './squirclePath'

export type SquircleBorder = {
  color?: string
  width?: number
}

export type SquircleProps = {
  as?: ElementType
  radius?: number
  smoothing?: number
  border?: SquircleBorder
  className?: string
  style?: React.CSSProperties
  children?: ReactNode
} & Record<string, any>

function setRef<T>(ref: React.Ref<T> | undefined, value: T | null) {
  if (!ref) return
  if (typeof ref === 'function') ref(value)
  else (ref as React.MutableRefObject<T | null>).current = value
}

// Clips any element (div, button, Link, ...) to a squircle instead of a plain
// rounded-rect. The shape is recomputed from the element's real pixel size via
// ResizeObserver, so it stays correct across resizes/aspect ratios instead of
// distorting the way a scaled clip-path would on a non-square box.
export const Squircle = forwardRef<HTMLElement, SquircleProps>(function Squircle(
  { as, radius = 22, smoothing = 4.5, border, className, style, children, ...rest },
  forwardedRef
) {
  const Tag = (as ?? 'div') as ElementType
  const localRef = useRef<HTMLElement | null>(null)
  const [size, setSize] = useState({ w: 0, h: 0 })

  useLayoutEffect(() => {
    const el = localRef.current
    if (!el) return
    // Tamanho de LAYOUT, sem transformações. `getBoundingClientRect()` devolve o tamanho já
    // escalado: um <Modal> entra animando de `scale: 0.95` e era medido nesse instante, então o
    // recorte ficava com 95% do tamanho e cortava a borda direita e o rodapé de todo modal — e o
    // ResizeObserver não corrigia depois, porque transformação não muda o tamanho de layout.
    const apply = (w: number, h: number) =>
      setSize((prev) =>
        Math.abs(prev.w - w) > 0.5 || Math.abs(prev.h - h) > 0.5 ? { w, h } : prev
      )
    apply(el.offsetWidth, el.offsetHeight)
    const ro = new ResizeObserver((entries) => {
      const box = entries[entries.length - 1]?.borderBoxSize?.[0]
      if (box) apply(box.inlineSize, box.blockSize)
      else apply(el.offsetWidth, el.offsetHeight)
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const path = size.w > 0 && size.h > 0 ? squirclePathD(size.w, size.h, radius, smoothing) : null
  const clipPath = path ? `path('${path}')` : undefined

  return (
    <Tag
      ref={(node: HTMLElement | null) => {
        localRef.current = node
        setRef(forwardedRef, node)
      }}
      className={className}
      style={{
        position: 'relative',
        clipPath,
        WebkitClipPath: clipPath,
        ...style,
      }}
      {...rest}
    >
      {children}
      {border && path && (
        <svg
          className="pointer-events-none absolute inset-0"
          width="100%"
          height="100%"
          preserveAspectRatio="none"
        >
          <path
            d={path}
            fill="none"
            stroke={border.color ?? 'rgba(255,255,255,0.14)'}
            strokeWidth={border.width ?? 1}
          />
        </svg>
      )}
    </Tag>
  )
})
