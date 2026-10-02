import { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { save } from '@tauri-apps/plugin-dialog'
import { DatabaseBackupIcon, PackageIcon, AlertTriangleIcon, RefreshCwIcon } from 'lucide-react'
import { useCarIp } from '../hooks/useCarIp'
import { BackButton, Banner, Button, Card, IconTile, ListPanel, Squircle, TextInput } from '../ui'

const formatSize = (bytes: number) => {
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

type AppEntry = { package: string; isSystem: boolean }

export const BackupTools = () => {
  const { carIp } = useCarIp()
  const [running, setRunning] = useState<'apps' | 'full' | 'app' | null>(null)
  const [bytesDone, setBytesDone] = useState(0)
  const [totalBytes, setTotalBytes] = useState<number | null>(null)
  const [estimating, setEstimating] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<string | null>(null)

  const [apps, setApps] = useState<AppEntry[]>([])
  const [loadingApps, setLoadingApps] = useState(false)
  const [appsError, setAppsError] = useState<string | null>(null)
  const [appFilter, setAppFilter] = useState('')
  const [selectedApp, setSelectedApp] = useState<string | null>(null)

  useEffect(() => {
    const unlisten = listen<number>('backup-progress', (event) => setBytesDone(event.payload))
    return () => {
      unlisten.then((u) => u())
    }
  }, [])

  const loadApps = useCallback(async () => {
    setLoadingApps(true)
    setAppsError(null)
    try {
      const raw = await invoke<string>('remote_list_apps', { carIp })
      const parsed = JSON.parse(raw) as AppEntry[]
      setApps(parsed.sort((a, b) => a.package.localeCompare(b.package)))
    } catch (e: any) {
      setAppsError(e.toString())
    } finally {
      setLoadingApps(false)
    }
  }, [carIp])

  useEffect(() => {
    loadApps()
  }, [loadApps])

  // Estima o total antes de iniciar — a resposta do backup em si é "close-delimited" (sem
  // Content-Length), então sem essa chamada prévia (`du -sk` no carro) não haveria como saber
  // a porcentagem, só os bytes já baixados.
  const estimateTotal = async (kind: 'apps' | 'full' | 'app', pkg?: string) => {
    setEstimating(true)
    setTotalBytes(null)
    try {
      const bytes = await invoke<number>('remote_backup_size', { carIp, kind, package: pkg ?? null })
      setTotalBytes(bytes)
    } catch {
      setTotalBytes(null) // segue sem total — barra cai pra "só bytes recebidos"
    } finally {
      setEstimating(false)
    }
  }

  const runBackup = async (kind: 'apps' | 'full') => {
    const localPath = await save({
      defaultPath: kind === 'apps' ? 'openhw_apps_backup.tar' : 'openhw_full_backup.tar',
      filters: [{ name: 'tar', extensions: ['tar'] }],
    })
    if (!localPath) return
    setRunning(kind)
    setBytesDone(0)
    setError(null)
    setDone(null)
    await estimateTotal(kind)
    try {
      await invoke(kind === 'apps' ? 'remote_backup_apps' : 'remote_backup_full', { carIp, localPath })
      setDone(localPath)
    } catch (e: any) {
      setError(e.toString())
    } finally {
      setRunning(null)
    }
  }

  const runSingleAppBackup = async () => {
    if (!selectedApp) return
    const localPath = await save({
      defaultPath: `${selectedApp}.tar`,
      filters: [{ name: 'tar', extensions: ['tar'] }],
    })
    if (!localPath) return
    setRunning('app')
    setBytesDone(0)
    setError(null)
    setDone(null)
    await estimateTotal('app', selectedApp)
    try {
      await invoke('remote_backup_app', { carIp, package: selectedApp, localPath })
      setDone(localPath)
    } catch (e: any) {
      setError(e.toString())
    } finally {
      setRunning(null)
    }
  }

  const filteredApps = apps.filter((a) => a.package.toLowerCase().includes(appFilter.toLowerCase()))

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-center gap-3 mb-6">
          <IconTile icon={DatabaseBackupIcon} tone="primary" size={44} />
          <h1 className="text-2xl font-semibold text-text tracking-tight">Backup</h1>
        </div>

        {error && <Banner tone="error" className="mb-4">{error}</Banner>}
        {done && <Banner tone="success" className="mb-4 break-all">Backup salvo em: {done}</Banner>}
        {running && (
          <div className="mb-4">
            <div className="flex items-center justify-between mb-2 text-sm">
              <span className="text-text-secondary">
                Baixando {running === 'apps' ? 'APKs' : running === 'full' ? 'backup completo' : selectedApp}
                {estimating ? ' (calculando tamanho...)' : '...'}
              </span>
              <span className="font-mono text-text-secondary">
                {totalBytes ? `${Math.min(100, Math.round((bytesDone / totalBytes) * 100))}%` : formatSize(bytesDone)}
              </span>
            </div>
            <div className="w-full h-1.5 bg-white/10 rounded-full overflow-hidden mb-1">
              <div
                className="h-full rounded-full bg-primary transition-all duration-300 ease-out"
                style={{
                  width: totalBytes
                    ? `${Math.min(100, (bytesDone / totalBytes) * 100)}%`
                    : estimating
                      ? '4%'
                      : '100%',
                }}
              />
            </div>
            {totalBytes != null && (
              <div className="text-xs text-text-tertiary">
                {formatSize(bytesDone)} de {formatSize(totalBytes)}
              </div>
            )}
          </div>
        )}

        <Squircle
          as="button"
          radius={20}
          border={{ color: 'rgba(255,255,255,0.1)' }}
          disabled={running !== null}
          onClick={() => runBackup('apps')}
          className="flex items-start gap-4 w-full bg-white/[0.04] hover:bg-white/[0.08] transition-colors py-5 px-6 mb-3 disabled:opacity-50"
        >
          <IconTile icon={PackageIcon} tone="primary" size={44} />
          <div className="text-left">
            <div className="font-medium text-text">Extrair todos os APKs</div>
            <div className="text-xs text-text-tertiary mt-0.5">
              Pasta completa (base + splits) de cada app de terceiros instalado — rápido,
              geralmente poucas dezenas de MB.
            </div>
          </div>
        </Squircle>

        <Squircle
          as="button"
          radius={20}
          border={{ color: 'rgba(255,255,255,0.1)' }}
          disabled={running !== null}
          onClick={() => runBackup('full')}
          className="flex items-start gap-4 w-full bg-white/[0.04] hover:bg-white/[0.08] transition-colors py-5 px-6 mb-4 disabled:opacity-50"
        >
          <IconTile icon={DatabaseBackupIcon} tone="neutral" size={44} />
          <div className="text-left">
            <div className="font-medium text-text">Backup completo (tudo)</div>
            <div className="text-xs text-text-tertiary mt-0.5 leading-relaxed">
              Todas as partições reais do carro: <code>/data</code>, <code>/sdcard</code>,{' '}
              <code>/system</code>, <code>/vendor</code>, <code>/product</code> e mais (a lista
              exata depende do que existir nesta central). Pode passar de dezenas de GB e demorar
              horas pelo Wi-Fi — não fecha o app nem sai da rede durante o processo.
            </div>
          </div>
        </Squircle>

        <Squircle radius={20} border={{ color: 'rgba(255,255,255,0.08)' }} className="bg-black/20 p-4 mb-4">
          <div className="flex items-center justify-between mb-3">
            <div className="text-sm font-medium text-text">Extrair um app específico</div>
            <button
              onClick={loadApps}
              disabled={loadingApps}
              className="flex items-center gap-1 text-xs text-text-tertiary hover:text-text"
            >
              <RefreshCwIcon size={12} className={loadingApps ? 'animate-spin' : ''} />
              <span>Recarregar</span>
            </button>
          </div>

          {appsError && <div className="text-xs text-danger mb-2">Falha ao listar apps: {appsError}</div>}

          <TextInput
            value={appFilter}
            onChange={(e) => setAppFilter(e.target.value)}
            placeholder="Filtrar por nome do pacote..."
            containerClassName="mb-3"
          />

          <ListPanel maxHeight="max-h-40" className="mb-3">
            {loadingApps && <div className="text-xs text-text-tertiary p-3">Carregando apps...</div>}
            {!loadingApps && filteredApps.length === 0 && (
              <div className="text-xs text-text-tertiary p-3">Nenhum app encontrado.</div>
            )}
            {filteredApps.map((a) => (
              <button
                key={a.package}
                onClick={() => setSelectedApp(a.package)}
                className={`flex items-center justify-between gap-2 w-full text-left px-3 py-2 text-xs border-b border-white/5 last:border-b-0 transition-colors ${
                  selectedApp === a.package ? 'bg-primary/25 text-text' : 'text-text-secondary hover:bg-white/5'
                }`}
              >
                <span className="truncate">{a.package}</span>
                {a.isSystem && (
                  <span className="flex-shrink-0 text-[10px] uppercase tracking-wide px-2 py-0.5 rounded-full bg-warning/15 text-warning border border-warning/25">
                    Sistema
                  </span>
                )}
              </button>
            ))}
          </ListPanel>

          <Button variant="primary" icon={PackageIcon} disabled={running !== null || !selectedApp} onClick={runSingleAppBackup}>
            {selectedApp ? `Extrair ${selectedApp}` : 'Selecione um app acima'}
          </Button>
        </Squircle>

        <Banner tone="warning" icon={AlertTriangleIcon} className="mb-6">
          Não fecha o app no carro nem sai da rede durante o backup — a conexão é
          "close-delimited" (sem retomada), então uma queda no meio do caminho corrompe o arquivo
          parcial e é preciso começar de novo.
        </Banner>

        <BackButton />
      </Card>
    </div>
  )
}
