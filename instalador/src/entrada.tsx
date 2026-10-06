// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Estilos globais primeiro: as telas sobrescrevem a partir deles.
import './index.css'
import { createRoot } from 'react-dom/client'
import { Aplicativo } from './Aplicativo'

const contêiner = document.getElementById('aplicativo')
if (contêiner) createRoot(contêiner).render(<Aplicativo />)

// Liquid Glass nos sliders: a parte cheia da calha (--fill) acompanha o valor. O React atualiza o
// `value` sem disparar evento, então um laço leve cobre também as mudanças vindas de fora.
const pintarCalha = (el: HTMLInputElement) => {
  const min = Number(el.min) || 0
  const max = el.max === '' ? 100 : Number(el.max)
  const pct = max > min ? Math.max(0, Math.min(100, ((Number(el.value) - min) / (max - min)) * 100)) : 0
  const v = `${pct.toFixed(1)}%`
  if (el.style.getPropertyValue('--fill') !== v) el.style.setProperty('--fill', v)
}
document.addEventListener(
  'input',
  (e) => {
    const t = e.target as HTMLInputElement | null
    if (t && t.type === 'range') pintarCalha(t)
  },
  true,
)
setInterval(() => document.querySelectorAll<HTMLInputElement>('input[type="range"]').forEach(pintarCalha), 300)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
