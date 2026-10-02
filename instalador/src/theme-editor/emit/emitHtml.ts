// Junta motor, CSS do tema e payload num `app.html` autocontido.
//
// A MESMA função gera o preview ao vivo do editor (dentro de um `<iframe srcDoc>`) e o arquivo
// enviado ao carro. Não existem dois caminhos de render: o que você vê editando é literalmente o
// que roda no cluster. A única diferença entre os dois é de onde vêm os arquivos de fonte e a
// imagem de fundo, e isso entra por parâmetro.

import type { ThemeSpec } from '../model/spec'
import { ENGINE_CSS } from '../runtime/engineCss'
import { ENGINE_JS } from '../runtime/engineJs'
import { buildPayload } from './payload'
import { buildThemeCss } from './themeCss'

const escapeHtml = (s: string) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

export type EmitOptions = {
  /** Prefixo dos arquivos de fonte: vazio no carro, `/fonts/` no preview do editor. */
  fontBase?: string
  /** `background` do body já resolvido. */
  backgroundCss: string
  /** Qual página começa ativa. Só o preview usa; no carro é sempre a primeira. */
  activePageId?: string
}

export function emitAppHtml(spec: ThemeSpec, opts: EmitOptions): string {
  const payload = buildPayload(spec, opts.activePageId)
  const themeCss = buildThemeCss(spec, { fontBase: opts.fontBase ?? '', backgroundCss: opts.backgroundCss })
  const boot = spec.boot && spec.boot.enabled
    ? `<div id="boot" class="boot"><div class="boot-ring"></div><div class="boot-word">${escapeHtml(spec.boot.wordmark)}</div></div>`
    : ''

  return `<!doctype html>
<html>
<head>
<meta charset="utf-8" />
<title>${escapeHtml(spec.name)}</title>
<style>${ENGINE_CSS}</style>
<style>${themeCss}</style>
</head>
<body>
<div id="app"></div>
${boot}
<script>${ENGINE_JS}</script>
<script>OPENGW.boot(${JSON.stringify(payload)});</script>
</body>
</html>
`
}

/** Só o motor, para o teste comparar bytes entre dois temas diferentes. */
export const engineFingerprint = () => `${ENGINE_CSS}\n${ENGINE_JS}`
