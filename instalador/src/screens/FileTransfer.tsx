import { useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { open, save } from '@tauri-apps/plugin-dialog'
import { FolderSyncIcon, FolderIcon, FileIcon, DownloadIcon, UploadIcon } from 'lucide-react'
import { useCarIp } from '../hooks/useCarIp'
import { BackButton, Banner, Button, Card, IconTile, ListPanel, ListRow, TextInput } from '../ui'

type FileEntry = { name: string; isDir: boolean; size: number }

const formatSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}

const joinPath = (dir: string, name: string) => (dir.endsWith('/') ? `${dir}${name}` : `${dir}/${name}`)

export const FileTransfer = () => {
  const { carIp } = useCarIp()
  const [path, setPath] = useState('/sdcard')
  const [entries, setEntries] = useState<FileEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busyName, setBusyName] = useState<string | null>(null)
  const [downloadedBytes, setDownloadedBytes] = useState<number | null>(null)

  const list = async (targetPath: string) => {
    setLoading(true)
    setError(null)
    try {
      const raw = await invoke<string>('remote_list_files', { carIp, path: targetPath })
      const parsed = JSON.parse(raw) as FileEntry[]
      parsed.sort((a, b) => (a.isDir === b.isDir ? a.name.localeCompare(b.name) : a.isDir ? -1 : 1))
      setEntries(parsed)
      setPath(targetPath)
    } catch (e: any) {
      setError(e.toString())
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    list(path)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [carIp])

  useEffect(() => {
    const unlisten = listen<number>('download-progress', (event) => {
      setDownloadedBytes(event.payload)
    })
    return () => {
      unlisten.then((u) => u())
    }
  }, [])

  const handleDownload = async (entry: FileEntry) => {
    const remotePath = joinPath(path, entry.name)
    const localPath = await save({ defaultPath: entry.name })
    if (!localPath) return
    setBusyName(entry.name)
    setDownloadedBytes(0)
    setError(null)
    try {
      await invoke('remote_download_file', { carIp, remotePath, localPath })
    } catch (e: any) {
      setError(e.toString())
    } finally {
      setBusyName(null)
      setDownloadedBytes(null)
    }
  }

  const handleUpload = async () => {
    const selected = await open({ multiple: false })
    if (typeof selected !== 'string') return
    const fileName = selected.split(/[\\/]/).pop() || 'arquivo'
    const remotePath = joinPath(path, fileName)
    setBusyName(fileName)
    setError(null)
    try {
      await invoke('remote_upload_file', { carIp, localPath: selected, remotePath })
      await list(path)
    } catch (e: any) {
      setError(e.toString())
    } finally {
      setBusyName(null)
    }
  }

  const goUp = () => {
    const trimmed = path.replace(/\/+$/, '')
    const parent = trimmed.substring(0, trimmed.lastIndexOf('/')) || '/'
    list(parent)
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="2xl">
        <div className="flex items-center gap-3 mb-4">
          <IconTile icon={FolderSyncIcon} tone="primary" size={40} />
          <h1 className="text-xl font-semibold text-text tracking-tight">Transferência de Arquivos</h1>
        </div>

        <div className="flex gap-2 mb-4">
          <Button variant="secondary" size="sm" fullWidth={false} onClick={goUp}>
            ..
          </Button>
          <TextInput
            mono
            value={path}
            onChange={(e) => setPath(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && list(path)}
            containerClassName="flex-1"
          />
          <Button variant="secondary" fullWidth={false} onClick={() => list(path)}>
            Ir
          </Button>
          <Button variant="primary" icon={UploadIcon} fullWidth={false} disabled={busyName !== null} onClick={handleUpload}>
            Enviar
          </Button>
        </div>

        {error && <Banner tone="error" className="mb-4">{error}</Banner>}

        {busyName && downloadedBytes !== null && (
          <div className="text-xs text-primary mb-2">
            Baixando {busyName}... {formatSize(downloadedBytes)}
          </div>
        )}
        {busyName && downloadedBytes === null && <div className="text-xs text-primary mb-2">Enviando {busyName}...</div>}

        <ListPanel maxHeight="max-h-96" className="mb-4">
          {loading && <div className="p-4 text-center text-text-tertiary text-sm">Carregando...</div>}
          {!loading && entries.length === 0 && (
            <div className="p-4 text-center text-text-tertiary text-sm">Diretório vazio ou inacessível.</div>
          )}
          {!loading &&
            entries.map((entry) => (
              <ListRow key={entry.name} className="justify-between">
                <button
                  onClick={() => (entry.isDir ? list(joinPath(path, entry.name)) : undefined)}
                  className={`flex items-center gap-2 text-sm truncate flex-1 text-left ${
                    entry.isDir ? 'text-primary hover:text-primary-hover' : 'text-text-secondary'
                  }`}
                  disabled={!entry.isDir}
                >
                  {entry.isDir ? <FolderIcon size={16} /> : <FileIcon size={16} />}
                  <span className="truncate">{entry.name}</span>
                </button>
                <span className="text-xs text-text-tertiary flex-shrink-0">
                  {entry.isDir ? '' : formatSize(entry.size)}
                </span>
                {!entry.isDir && (
                  <button
                    onClick={() => handleDownload(entry)}
                    disabled={busyName !== null}
                    className="text-primary hover:text-primary-hover disabled:opacity-40 flex-shrink-0"
                    title="Baixar"
                  >
                    <DownloadIcon size={16} />
                  </button>
                )}
              </ListRow>
            ))}
        </ListPanel>

        <BackButton />
      </Card>
    </div>
  )
}
