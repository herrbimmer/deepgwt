// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { Transition } from 'motion/react'

/**
 * Presets de spring nomeados (skill apple-design / "Designing Fluid Interfaces"): motion
 * definido por damping (overshoot) + response (segundos até assentar), não por duração fixa.
 * A API bounce/duration do `motion` mapeia direto pra esse par.
 */
export const springs: Record<'press' | 'reposition' | 'sheet' | 'momentum', Transition> = {
  /** Crítico (sem overshoot), bem rápido — feedback de toque. */
  press: { type: 'spring', bounce: 0, duration: 0.15 },
  /** Crítico (sem overshoot) — reposicionamento de elementos. */
  reposition: { type: 'spring', bounce: 0, duration: 0.4 },
  /** Levemente subamortecido — entrada de popups/sheets. */
  sheet: { type: 'spring', bounce: 0.15, duration: 0.3 },
  /** Levemente subamortecido — assentamento ao soltar um arrasto com momentum. */
  momentum: { type: 'spring', bounce: 0.15, duration: 0.4 }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
