// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useState } from 'react'
import { open } from '@tauri-apps/plugin-dialog'
import { invoke } from '@tauri-apps/api/core'
import { FileCodeIcon, SendIcon, UploadIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, IconTile, TextInput } from '../ui'
import { CarLinkPanel } from '../components'
import { useCarLink } from '../hooks/useCarLink'
import { CAR_THEME_ROOT, textToBase64 } from '../theme-editor/exportTheme'
import { escapeXml } from '../theme-editor/emit/emitThemeXml'

type SendStatus = 'idle' | 'busy' | 'done' | 'error'

// Slug estável e único o bastante pra não colidir com outra pasta em files/themes/ no carro.
function slugify(name: string): string {
  const base = name
    .trim()
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-+|-+$)/g, '')
  return `${base || 'tema-html'}-${Date.now().toString(36)}`
}

// Manifesto mínimo: sem thumbnail/background/nativeMasks, todos opcionais no parser do
// ThemeManager.kt — o arquivo importado já é 100% autocontido (CSS/fundo/layout inline).
function buildMinimalThemeXml(name: string): string {
  return `<theme>
    <name>${escapeXml(name)}</name>
    <description>Tema HTML único importado no DeepGWT.</description>
    <version>0.1.0</version>
    <minBridgeVersion>1.0.0</minBridgeVersion>
    <contractVersion>v1.0</contractVersion>
    <mainFile>app.html</mainFile>
</theme>
`
}

export const ImportHtmlTheme = () => {
  const link = useCarLink()
  const { carIp } = link
  const [filePath, setFilePath] = useState<string | null>(null)
  const [themeName, setThemeName] = useState('')
  const [sendStatus, setSendStatus] = useState<SendStatus>('idle')
  const [sendStep, setSendStep] = useState('')
  const [sendError, setSendError] = useState<string | null>(null)

  useEffect(() => {
    link.preparar()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handlePickFile = async () => {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'HTML', extensions: ['html', 'htm'] }],
    })
    if (typeof selected === 'string') {
      setFilePath(selected)
      if (!themeName.trim()) {
        const base = selected.split(/[\\/]/).pop()?.replace(/\.html?$/i, '') ?? 'Tema HTML'
        setThemeName(base)
      }
    }
  }

  const handleSend = async () => {
    if (!filePath) return
    setSendStatus('busy')
    setSendError(null)
    try {
      setSendStep('Lendo arquivo...')
      const appHtmlB64 = await invoke<string>('read_local_file_b64', { path: filePath })
      const folderName = slugify(themeName)
      const base = `${CAR_THEME_ROOT}/${folderName}`
      const themeXml = buildMinimalThemeXml(themeName.trim() || 'Tema HTML')

      setSendStep('Enviando theme.xml...')
      await invoke('remote_upload_bytes_b64', { carIp, remotePath: `${base}/theme.xml`, dataB64: textToBase64(themeXml) })

      setSendStep('Enviando app.html...')
      await invoke('remote_upload_bytes_b64', { carIp, remotePath: `${base}/app.html`, dataB64: appHtmlB64 })

      setSendStatus('done')
    } catch (e: any) {
      setSendError(e?.toString?.() ?? String(e))
      setSendStatus('error')
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="md">
        <div className="flex items-center gap-3 mb-2">
          <IconTile icon={FileCodeIcon} tone="primary" size={44} />
          <h1 className="text-xl font-semibold text-text tracking-tight">Importar Tema HTML</h1>
        </div>
        <p className="text-text-secondary text-sm mb-6">
          Para um tema autoral único (um arquivo <span className="font-mono">.html</span> autocontido,
          feito fora do editor visual) — envia direto pro carro sem passar pelos mostradores
          arrastáveis.
        </p>

        <Button variant="secondary" icon={UploadIcon} onClick={handlePickFile} className="mb-4">
          {filePath ? filePath.split(/[\\/]/).pop() : 'Escolher arquivo .html...'}
        </Button>

        <label className="text-xs font-medium text-text-secondary mb-1.5 block">Nome do tema</label>
        <TextInput
          value={themeName}
          onChange={(e) => setThemeName(e.target.value)}
          placeholder="Ex.: Meu Cluster Customizado"
          containerClassName="mb-6"
        />

        <CarLinkPanel link={link} ocupado={sendStatus === 'busy'} />

        {sendStatus === 'busy' && <Banner tone="info" className="mb-4">{sendStep}</Banner>}
        {sendStatus === 'done' && (
          <Banner tone="success" className="mb-4">
            Enviado! Abra "Painel Virtual" no carro, selecione o tema na lista e toque em "Recarregar"
            se ele ainda não aparecer.
          </Banner>
        )}
        {sendStatus === 'error' && <Banner tone="error" className="mb-4">{sendError}</Banner>}

        <Button
          variant="primary"
          icon={SendIcon}
          onClick={handleSend}
          disabled={!filePath || link.status !== 'ok' || sendStatus === 'busy'}
          className="mb-6"
        >
          Enviar para o Carro
        </Button>

        <BackButton to="/temas" />
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
