// Fundos prontos, sem depender de arquivo nenhum além do papel de parede que o app já instala.
//
// Os três predefinidos antigos apontavam para `public/themes/{nordic,coffe3,horizon}/car-bg.png`,
// cópias de agosto de temas que o app APAGA no boot (`ThemeManager.kt`). Ficaram dois caminhos:
// gradientes puros, que não custam byte nenhum no pacote, e o papel de parede do Stuttgart, que é
// o único arquivo de imagem realmente commitado.

export type BackgroundPreset = {
  id: string
  label: string
  /** Valor CSS de `background`, ou um caminho começando com `/` para virar `url(...)`. */
  preview: string
}

export const BACKGROUND_PRESETS: BackgroundPreset[] = [
  { id: 'midnight', label: 'Meia-noite', preview: 'linear-gradient(180deg, #05060a 0%, #10131c 100%)' },
  { id: 'graphite', label: 'Grafite', preview: 'linear-gradient(180deg, #1c1c1e 0%, #050505 100%)' },
  { id: 'forest', label: 'Floresta', preview: 'linear-gradient(180deg, #0b1a13 0%, #04090b 100%)' },
  { id: 'ember', label: 'Brasa', preview: 'linear-gradient(180deg, #1a0d08 0%, #070403 100%)' },
  { id: 'road', label: 'Rodovia', preview: '/themes/stuttgart-ntg55/car-bg.jpg' },
]

export const PRESET_BY_ID = new Map(BACKGROUND_PRESETS.map((p) => [p.id, p]))

export function resolvePresetBackground(presetId: string): string {
  const preset = PRESET_BY_ID.get(presetId) ?? BACKGROUND_PRESETS[0]
  return preset.preview.startsWith('/') ? `url("${preset.preview}")` : preset.preview
}

/** Caminho do arquivo quando o predefinido é uma imagem, para o exportador empacotar os bytes. */
export function presetImageUrl(presetId: string): string | null {
  const preset = PRESET_BY_ID.get(presetId)
  return preset && preset.preview.startsWith('/') ? preset.preview : null
}
