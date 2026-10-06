// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import type { BackgroundConfig } from './model/spec'
import { resolvePresetBackground } from './backgroundPresets'

/**
 * Sempre o atalho `background` completo, com position e size embutidos: misturar o atalho com
 * propriedades longas no mesmo `style` dispara o aviso de conflito do React entre re-renders.
 */
export function resolveBackgroundCss(bg: BackgroundConfig): string {
  switch (bg.mode) {
    case 'preset':
      return `${resolvePresetBackground(bg.presetId)} center / cover no-repeat`
    case 'solid':
      return bg.solid
    case 'gradient':
      return `linear-gradient(180deg, ${bg.gradientFrom} 0%, ${bg.gradientTo} 100%)`
    case 'custom':
      return bg.customDataUrl
        ? `url("${bg.customDataUrl}") center / cover no-repeat`
        : `${resolvePresetBackground('midnight')} center / cover no-repeat`
    // O papel de parede original, servido do disco. No carro o mesmo arquivo fica ao lado do
    // `app.html`, então lá o caminho é só o nome — quem resolve isso é o exportador.
    case 'passthrough':
      return bg.passthrough
        ? `#000 url("${bg.passthrough.sourceUrl}") center / cover no-repeat`
        : '#000'
  }
}

/** O mesmo fundo, mas com o caminho que vale DENTRO da pasta do tema no carro. */
export function backgroundCssForCar(bg: BackgroundConfig): string {
  if (bg.mode === 'solid') return bg.solid
  if (bg.mode === 'gradient') return `linear-gradient(180deg, ${bg.gradientFrom} 0%, ${bg.gradientTo} 100%)`
  if (bg.mode === 'passthrough' && bg.passthrough) {
    return `#000 url('${bg.passthrough.fileName}') center / cover no-repeat`
  }
  return "#000 url('background.png') center / cover no-repeat"
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
