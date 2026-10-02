// Builds an SVG path (in raw pixel units) for a rounded rect whose corners follow a
// superellipse ("squircle") instead of a circular arc, so it looks right at any
// width/height the box happens to render at — needed since cards/buttons here get
// stretched across everything from a 9:21 phone-style window to a 16:9 landscape one.
export function squirclePathD(
  width: number,
  height: number,
  radius: number,
  smoothing = 4.5,
  steps = 10
): string {
  const w = Math.max(0, width)
  const h = Math.max(0, height)
  const r = Math.max(0, Math.min(radius, w / 2, h / 2))

  if (w === 0 || h === 0) return ''
  if (r < 0.5) return `M0,0 H${w} V${h} H0 Z`

  const p = 2 / smoothing
  const pts: [number, number][] = []

  const addCorner = (fn: (c: number, s: number) => [number, number]) => {
    for (let i = 0; i <= steps; i++) {
      const t = (Math.PI / 2) * (i / steps)
      const c = Math.pow(Math.cos(t), p)
      const s = Math.pow(Math.sin(t), p)
      pts.push(fn(c, s))
    }
  }

  // Walk clockwise starting at the end of the top-left corner. Each corner's start
  // point coincides with the previous corner's end point (both lie on the same
  // straight edge), so the straight edges need no explicit points of their own.
  addCorner((c, s) => [w - r + r * s, r - r * c]) // top-right: (w-r,0) -> (w,r)
  addCorner((c, s) => [w - r + r * c, h - r + r * s]) // bottom-right: (w,h-r) -> (w-r,h)
  addCorner((c, s) => [r - r * s, h - r + r * c]) // bottom-left: (r,h) -> (0,h-r)
  addCorner((c, s) => [r - r * c, r - r * s]) // top-left: (0,r) -> (r,0)

  return 'M' + pts.map(([x, y]) => `${x.toFixed(2)},${y.toFixed(2)}`).join(' L') + ' Z'
}
