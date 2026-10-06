// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { UploadIcon } from 'lucide-react'
import { open } from '@tauri-apps/plugin-dialog'
import { invoke } from '@tauri-apps/api/core'
import type { BackgroundConfig, BackgroundMode } from './model/spec'
import { BACKGROUND_PRESETS, presetImageUrl, resolvePresetBackground } from './backgroundPresets'
import { Banner, Button, ColorField } from '../ui'

function mimeFromExtension(path: string): string {
  const ext = path.split('.').pop()?.toLowerCase()
  if (ext === 'jpg' || ext === 'jpeg') return 'image/jpeg'
  if (ext === 'webp') return 'image/webp'
  return 'image/png'
}

export type BackgroundPanelProps = {
  background: BackgroundConfig
  onChange: (background: BackgroundConfig) => void
}

const MODES: { id: BackgroundMode; label: string }[] = [
  { id: 'preset', label: 'Pronto' },
  { id: 'solid', label: 'Cor' },
  { id: 'gradient', label: 'Degradê' },
  { id: 'custom', label: 'Imagem' },
]

export function BackgroundPanel({ background, onChange }: BackgroundPanelProps) {
  const handlePickImage = async () => {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'Imagem', extensions: ['png', 'jpg', 'jpeg', 'webp'] }],
    })
    if (typeof selected === 'string') {
      const dataB64 = await invoke<string>('read_local_file_b64', { path: selected })
      const customDataUrl = `data:${mimeFromExtension(selected)};base64,${dataB64}`
      onChange({ ...background, mode: 'custom', customPath: selected, customDataUrl })
    }
  }

  return (
    <div className="space-y-4">
      <div>
        <h4 className="text-xs font-semibold text-text uppercase tracking-wide">Fundo do painel</h4>
        <p className="text-[11px] text-text-tertiary mt-0.5">
          Vale para o cluster inteiro, atrás de todos os elementos.
        </p>
      </div>

      {background.mode === 'passthrough' && background.passthrough ? (
        <>
          <div
            className="aspect-[8/3] rounded-xl border border-white/10 bg-cover bg-center"
            style={{ backgroundImage: `url("${background.passthrough.sourceUrl}")` }}
          />
          <Banner tone="info">
            Papel de parede original do tema (<span className="font-mono">{background.passthrough.fileName}</span>),
            enviado byte a byte. O arquivo não passa por recodificação — o do Stuttgart tem extensão
            .jpg e conteúdo PNG, e recodificar pela extensão o quebraria.
          </Banner>
          <Button variant="secondary" size="sm" onClick={() => onChange({ ...background, mode: 'preset', passthrough: null })}>
            Trocar por outro fundo
          </Button>
        </>
      ) : (
        <>
          <div className="grid grid-cols-4 gap-2">
            {MODES.map((m) => (
              <Button
                key={m.id}
                variant={background.mode === m.id ? 'primary' : 'secondary'}
                size="sm"
                onClick={() => onChange({ ...background, mode: m.id })}
              >
                {m.label}
              </Button>
            ))}
          </div>

          {background.mode === 'preset' && (
            <div className="grid grid-cols-5 gap-2">
              {BACKGROUND_PRESETS.map((preset) => (
                <button
                  key={preset.id}
                  type="button"
                  onClick={() => onChange({ ...background, presetId: preset.id })}
                  className={`aspect-video rounded-lg border-2 transition-colors ${
                    background.presetId === preset.id ? 'border-primary' : 'border-white/10 hover:border-white/30'
                  }`}
                  style={{ background: `${resolvePresetBackground(preset.id)} center / cover no-repeat` }}
                  title={preset.label + (presetImageUrl(preset.id) ? ' (imagem)' : '')}
                />
              ))}
            </div>
          )}

          {background.mode === 'solid' && (
            <ColorField label="Cor" value={background.solid} alpha={false} onChange={(solid) => onChange({ ...background, solid })} />
          )}

          {background.mode === 'gradient' && (
            <div className="grid grid-cols-2 gap-3">
              <ColorField label="Topo" value={background.gradientFrom} alpha={false} onChange={(gradientFrom) => onChange({ ...background, gradientFrom })} />
              <ColorField label="Base" value={background.gradientTo} alpha={false} onChange={(gradientTo) => onChange({ ...background, gradientTo })} />
            </div>
          )}

          {background.mode === 'custom' && (
            <Button variant="secondary" icon={UploadIcon} onClick={handlePickImage}>
              {background.customPath ? background.customPath.split(/[\\/]/).pop() : 'Escolher imagem...'}
            </Button>
          )}
        </>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
