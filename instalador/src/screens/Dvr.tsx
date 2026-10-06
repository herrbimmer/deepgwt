// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { save } from '@tauri-apps/plugin-dialog'
import { openPath } from '@tauri-apps/plugin-opener'
import { appCacheDir, join } from '@tauri-apps/api/path'
import { VideoIcon, DownloadIcon, PlayIcon, SearchIcon, FolderSearchIcon } from 'lucide-react'
import { BackButton, Banner, Button, Card, ListPanel, ListRow, TextInput, ToggleRow } from '../ui'
import { useCarIp } from '../hooks/useCarIp'
import { useCarSettingsSync } from '../hooks/useCarSettingsSync'

const KEY_DVR_REMOTE_SWITCH = 'sys.dvr.remote_switch'
const STORAGE_PATH_KEY = 'deepgwt_dvr_storage_path'

// Mesma lista de candidatos tentada pelo `DvrManager.detectStoragePath()` no lado do carro —
// mantidas em sincronia manualmente (arquivo espelho, não compartilhado entre Kotlin/TS).
const CANDIDATE_PATHS = [
  '/sdcard/DVR',
  '/sdcard/dvr',
  '/sdcard/Movies/DVR',
  '/storage/emulated/0/DVR',
  '/storage/emulated/0/DCIM/DVR',
  '/storage/emulated/0/Video/dvr',
  '/storage/emulated/0/dvr',
  '/data/dvr',
]

type FileEntry = { name: string; isDir: boolean; size: number }

const formatSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export const Dvr = () => {
  const { carIp } = useCarIp()
  const { get, set, fieldStatus, error } = useCarSettingsSync(carIp)
  const [storagePath, setStoragePath] = useState(() => {
    try {
      return localStorage.getItem(STORAGE_PATH_KEY) || ''
    } catch {
      return ''
    }
  })
  const [entries, setEntries] = useState<FileEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [detecting, setDetecting] = useState(false)
  const [busyName, setBusyName] = useState<string | null>(null)
  const [listError, setListError] = useState<string | null>(null)

  const persistPath = (path: string) => {
    setStoragePath(path)
    try {
      localStorage.setItem(STORAGE_PATH_KEY, path)
    } catch {
      // segue só em memória pra sessão atual
    }
  }

  const listRecordings = async (path: string) => {
    if (!carIp || !path) return
    setLoading(true)
    setListError(null)
    try {
      const raw = await invoke<string>('remote_list_files', { carIp, path })
      const parsed = (JSON.parse(raw) as FileEntry[]).filter((e) => !e.isDir)
      setEntries(parsed)
    } catch (e: any) {
      setEntries([])
      setListError(e.toString())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (storagePath) listRecordings(storagePath)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [carIp])

  const handleDetect = async () => {
    if (!carIp) return
    setDetecting(true)
    setListError(null)
    try {
      for (const candidate of CANDIDATE_PATHS) {
        try {
          const raw = await invoke<string>('remote_list_files', { carIp, path: candidate })
          const parsed = (JSON.parse(raw) as FileEntry[]).filter((e) => !e.isDir)
          if (parsed.length > 0) {
            persistPath(candidate)
            setEntries(parsed)
            return
          }
        } catch {
          // candidato não existe/inacessível — tenta o próximo
        }
      }
      setListError(
        'Nenhum dos caminhos comuns tem gravações. Tente "Buscar em todo o armazenamento" ' +
          '(mais lento) ou digite o caminho manualmente.',
      )
    } finally {
      setDetecting(false)
    }
  }

  const handleDeepSearch = async () => {
    if (!carIp) return
    setDetecting(true)
    setListError(null)
    try {
      const raw = await invoke<string>('remote_dvr_find', { carIp })
      const parsed = JSON.parse(raw) as { ok: boolean; path?: string; error?: string }
      if (parsed.ok && parsed.path) {
        persistPath(parsed.path)
        await listRecordings(parsed.path)
      } else {
        setListError(parsed.error || 'Nenhum arquivo de vídeo encontrado no armazenamento do carro.')
      }
    } catch (e: any) {
      setListError(e.toString())
    } finally {
      setDetecting(false)
    }
  }

  const handleDownload = async (entry: FileEntry) => {
    const remotePath = `${storagePath.replace(/\/+$/, '')}/${entry.name}`
    const localPath = await save({ defaultPath: entry.name })
    if (!localPath) return
    setBusyName(entry.name)
    setListError(null)
    try {
      await invoke('remote_download_file', { carIp, remotePath, localPath })
    } catch (e: any) {
      setListError(e.toString())
    } finally {
      setBusyName(null)
    }
  }

  const handleOpen = async (entry: FileEntry) => {
    const remotePath = `${storagePath.replace(/\/+$/, '')}/${entry.name}`
    setBusyName(entry.name)
    setListError(null)
    try {
      const localPath = await join(await appCacheDir(), entry.name)
      await invoke('remote_download_file', { carIp, remotePath, localPath })
      await openPath(localPath)
    } catch (e: any) {
      setListError(e.toString())
    } finally {
      setBusyName(null)
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <h1 className="text-xl font-semibold text-center mb-2 text-text tracking-tight">DVR</h1>
        <p className="text-text-tertiary mb-6 text-center text-sm leading-relaxed">
          Liga/desliga a gravação nativa do carro e baixa/abre as gravações já feitas.
        </p>

        {error && (
          <Banner tone="error" className="mb-4">
            {error}
          </Banner>
        )}

        <div className="mb-6">
          <ToggleRow
            title="Gravação remota"
            description="Liga/desliga o DVR nativo do carro (sys.dvr.remote_switch)."
            checked={get(KEY_DVR_REMOTE_SWITCH) === '1'}
            onChange={(checked) => set(KEY_DVR_REMOTE_SWITCH, checked ? '1' : '0')}
            status={fieldStatus[KEY_DVR_REMOTE_SWITCH]}
            disabled={!carIp}
          />
        </div>

        <div className="mb-4 space-y-3">
          <label className="text-xs text-text-tertiary block">Caminho das gravações no carro</label>
          <div className="flex gap-2">
            <TextInput
              mono
              value={storagePath}
              onChange={(e) => persistPath(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && listRecordings(storagePath)}
              placeholder="/sdcard/DVR"
              containerClassName="flex-1"
            />
            <Button variant="secondary" fullWidth={false} onClick={() => listRecordings(storagePath)} disabled={!carIp || !storagePath}>
              Ir
            </Button>
            <Button
              variant="secondary"
              icon={SearchIcon}
              fullWidth={false}
              onClick={handleDetect}
              disabled={!carIp || detecting}
            >
              {detecting ? 'Detectando...' : 'Detectar'}
            </Button>
          </div>
          <Button
            variant="secondary"
            icon={FolderSearchIcon}
            fullWidth={false}
            onClick={handleDeepSearch}
            disabled={!carIp || detecting}
          >
            {detecting ? 'Buscando...' : 'Buscar em todo o armazenamento'}
          </Button>
        </div>

        {listError && (
          <Banner tone="error" className="mb-4">
            {listError}
          </Banner>
        )}

        <ListPanel maxHeight="max-h-72" className="mb-4">
          {loading && <div className="p-4 text-center text-text-tertiary text-sm">Carregando...</div>}
          {!loading && entries.length === 0 && (
            <div className="p-4 text-center text-text-tertiary text-sm">
              Nenhuma gravação encontrada (ou o caminho ainda não foi confirmado).
            </div>
          )}
          {!loading &&
            entries.map((entry) => (
              <ListRow key={entry.name} className="justify-between">
                <div className="flex items-center gap-2 text-sm truncate flex-1 text-text-secondary">
                  <VideoIcon size={16} />
                  <span className="truncate">{entry.name}</span>
                </div>
                <span className="text-xs text-text-tertiary flex-shrink-0 mr-2">{formatSize(entry.size)}</span>
                <button
                  onClick={() => handleOpen(entry)}
                  disabled={busyName !== null}
                  className="text-primary hover:text-primary-hover disabled:opacity-40 flex-shrink-0 mr-2"
                  title="Baixar e abrir"
                >
                  <PlayIcon size={16} />
                </button>
                <button
                  onClick={() => handleDownload(entry)}
                  disabled={busyName !== null}
                  className="text-primary hover:text-primary-hover disabled:opacity-40 flex-shrink-0"
                  title="Baixar"
                >
                  <DownloadIcon size={16} />
                </button>
              </ListRow>
            ))}
        </ListPanel>
        {busyName && <div className="text-xs text-primary mb-4 text-center">Baixando {busyName}...</div>}

        <div className="mt-2">
          <BackButton to="/config-avancadas" />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
