// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O único CSS que muda de um tema para outro: tokens de cor, fontes e fundo.
//
// Tudo o mais vem de `ENGINE_CSS`, byte a byte igual em todo tema. É essa separação que torna o
// `diff` entre dois `app.html` gerados legível — se o motor aparecer no diff, algo mudou nele.

import type { FontRef, Palette, ThemeSpec } from '../model/spec'
import { FONT_FILES } from './payload'

/**
 * Tokens que herdam a cor do modo noturno do próprio app quando o farol acende, em vez de fixar
 * âmbar. O app injeta `--night-tint`/`--night-glow` conforme a preferência do usuário (âmbar,
 * vermelho ou misto), então o tema acompanha a escolha dele.
 */
const HOST_TINT_TOKENS = new Set(['ink', 'accent', 'needle'])
const HOST_GLOW_TOKENS = new Set(['needleGlow'])

function modeBlock(palette: Palette, modo: 'day' | 'night' | 'headlight'): string {
  const linhas: string[] = []
  for (const [nome, tok] of Object.entries(palette.tokens)) {
    let valor: string
    if (modo === 'day') valor = tok.day
    else if (modo === 'night') valor = tok.night
    else if (tok.headlight) valor = tok.headlight
    else if (palette.headlightFromHostVars && HOST_TINT_TOKENS.has(nome)) valor = 'var(--night-tint, #ff9500)'
    else if (palette.headlightFromHostVars && HOST_GLOW_TOKENS.has(nome)) valor = 'var(--night-glow, rgba(255,149,0,.45))'
    else valor = tok.day
    linhas.push(`  --t-${nome}: ${valor};`)
  }
  return linhas.join('\n')
}

function fontFaces(fonts: FontRef[], fontBase: string): string {
  const nomes: Partial<Record<FontRef, string>> = { exoV: 'ExoV', michroma: 'Michroma' }
  return fonts
    .map((f) => {
      const arq = FONT_FILES[f]
      if (!arq) return ''
      // Exo V é variável de 300 a 800, e é desse contraste que vem a identidade tipográfica do
      // Stuttgart. Declarar um peso só colapsaria os dois extremos no mesmo traço.
      const faixa = f === 'exoV' ? '300 800' : '400'
      return `@font-face {\n  font-family: '${nomes[f]}';\n  font-weight: ${faixa};\n  font-style: normal;\n  font-display: swap;\n  src: url('${fontBase}${arq.file}') format('${arq.format}');\n}`
    })
    .filter(Boolean)
    .join('\n')
}

export type CssOptions = {
  /** Prefixo dos arquivos de fonte. Vazio no carro (ficam ao lado do app.html), `/fonts/` no preview. */
  fontBase: string
  /** Valor completo de `background` do body, já resolvido por quem chama. */
  backgroundCss: string
}

export function buildThemeCss(spec: ThemeSpec, opts: CssOptions): string {
  return `${fontFaces(spec.fonts, opts.fontBase)}

:root {
${modeBlock(spec.palette, 'day')}
}
body.mode-night {
${modeBlock(spec.palette, 'night')}
}
body.mode-headlight {
${modeBlock(spec.palette, 'headlight')}
}

/* O fundo fica no body, nunca no html: é o que permite ao motor limpá-lo e sair da frente de um
   aviso real do veículo. */
body { background: ${opts.backgroundCss}; }
`
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
