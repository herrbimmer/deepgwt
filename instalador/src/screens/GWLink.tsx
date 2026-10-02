import { useEffect, useMemo, useRef, useState } from 'react'
import { GaugeIcon, SearchIcon, RefreshCwIcon, StethoscopeIcon } from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { GWLINK_BASIC_PARAMS } from './gwlinkParams'
import { BackButton, Banner, Button, Card, IconTile, ListPanel, ListRow, TextInput } from '../ui'

type TelemetryMap = Record<string, string>

const POLL_INTERVAL_MS = 1000

export const GWLink = () => {
  const [telemetry, setTelemetry] = useState<TelemetryMap>({})
  const [expertMode, setExpertMode] = useState(false)
  const [search, setSearch] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [lastUpdated, setLastUpdated] = useState<number | null>(null)
  const pollingRef = useRef(false)

  const [diagnostics, setDiagnostics] = useState<string | null>(null)
  const [diagnosticsError, setDiagnosticsError] = useState<string | null>(null)
  const [loadingDiagnostics, setLoadingDiagnostics] = useState(false)

  const fetchDiagnostics = async () => {
    setLoadingDiagnostics(true)
    setDiagnosticsError(null)
    try {
      const raw = await invoke<string>('gwlink_fetch_diagnostics')
      const parsed = JSON.parse(raw)
      setDiagnostics(JSON.stringify(parsed, null, 2))
    } catch (e: any) {
      setDiagnosticsError(e.toString())
      setDiagnostics(null)
    } finally {
      setLoadingDiagnostics(false)
    }
  }

  useEffect(() => {
    let cancelled = false

    const poll = async () => {
      if (pollingRef.current) return
      pollingRef.current = true
      try {
        const raw = await invoke<string>('gwlink_fetch_telemetry')
        if (cancelled) return
        const parsed = JSON.parse(raw) as TelemetryMap
        setTelemetry(parsed)
        setLastUpdated(Date.now())
        setError(null)
      } catch (e: any) {
        if (!cancelled) setError(e.toString())
      } finally {
        pollingRef.current = false
      }
    }

    poll()
    const interval = window.setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      window.clearInterval(interval)
    }
  }, [])

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase()

    if (!expertMode) {
      return GWLINK_BASIC_PARAMS.filter(
        (p) => !q || p.label.toLowerCase().includes(q) || p.key.toLowerCase().includes(q)
      ).map((p) => ({ key: p.key, label: p.label, value: telemetry[p.key] ?? '—' }))
    }

    return Object.keys(telemetry)
      .filter((key) => !q || key.toLowerCase().includes(q))
      .sort()
      .map((key) => ({ key, label: key, value: telemetry[key] || '—' }))
  }, [telemetry, expertMode, search])

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <IconTile icon={GaugeIcon} tone="primary" size={40} />
            <h1 className="text-xl font-semibold text-text tracking-tight">GWLink</h1>
          </div>
          <label className="flex items-center gap-2 text-sm text-text-secondary cursor-pointer select-none">
            <span>Mostrar todas as chaves</span>
            <input type="checkbox" checked={expertMode} onChange={(e) => setExpertMode(e.target.checked)} />
          </label>
        </div>

        {error && <Banner tone="error" className="mb-4">{error}</Banner>}

        <TextInput
          icon={SearchIcon}
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Buscar parâmetro..."
          containerClassName="mb-4"
        />

        <div className="flex items-center justify-between text-xs text-text-tertiary mb-2">
          <span>{rows.length} parâmetro(s)</span>
          <span className="flex items-center gap-1">
            <RefreshCwIcon size={12} className="animate-spin" />
            {lastUpdated
              ? `Atualizado há ${Math.max(0, Math.round((Date.now() - lastUpdated) / 1000))}s`
              : 'Aguardando...'}
          </span>
        </div>

        <ListPanel maxHeight="max-h-96" className="mb-4">
          {rows.length === 0 && (
            <div className="p-4 text-center text-text-tertiary text-sm">Nenhum parâmetro encontrado.</div>
          )}
          {rows.map((row) => (
            <ListRow key={row.key} className="justify-between">
              <span className="text-sm text-text-secondary truncate">{row.label}</span>
              <span className="text-sm font-mono text-primary flex-shrink-0">{row.value}</span>
            </ListRow>
          ))}
        </ListPanel>

        <Button
          variant="secondary"
          icon={StethoscopeIcon}
          disabled={loadingDiagnostics}
          onClick={fetchDiagnostics}
          className="mb-4"
        >
          {loadingDiagnostics ? 'Buscando diagnóstico...' : 'Diagnóstico do Shizuku (avançado)'}
        </Button>

        {diagnosticsError && <Banner tone="error" className="mb-4">{diagnosticsError}</Banner>}

        {diagnostics && (
          <pre className="bg-black/30 border border-border rounded-2xl p-3 mb-4 text-xs text-primary overflow-auto max-h-64 whitespace-pre-wrap break-words">
            {diagnostics}
          </pre>
        )}

        <BackButton />
      </Card>
    </div>
  )
}
