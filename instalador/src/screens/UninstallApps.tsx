import { useEffect, useMemo, useState } from 'react'
import { Trash2Icon, SearchIcon, ShieldAlertIcon, RefreshCwIcon } from 'lucide-react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { ProgressPanel } from '../components'
import { BackButton, Banner, Button, Card, IconTile, ListPanel, ListRow, Modal, TextInput } from '../ui'

type InstalledPackage = {
  package_name: string
  is_system: boolean
}

const RISK_PHRASE = 'ACEITO O RISCO'

export const UninstallApps = () => {
  const [packages, setPackages] = useState<InstalledPackage[]>([])
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [search, setSearch] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [output, setOutput] = useState<string[]>([])
  const [isRunning, setIsRunning] = useState(false)
  const [isDone, setIsDone] = useState(false)
  const [showRiskDialog, setShowRiskDialog] = useState(false)
  const [riskInput, setRiskInput] = useState('')

  const loadPackages = async () => {
    setIsLoading(true)
    setLoadError(null)
    try {
      const result = await invoke<InstalledPackage[]>('list_installed_packages')
      setPackages(result)
    } catch (e: any) {
      setLoadError(e.toString())
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    loadPackages()
  }, [])

  useEffect(() => {
    const unlistenOutput = listen<string>('telnet-output', (event) => {
      setOutput((prev) => [...prev, event.payload])
    })
    return () => {
      unlistenOutput.then((unlisten) => unlisten())
    }
  }, [])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return packages
    return packages.filter((p) => p.package_name.toLowerCase().includes(q))
  }, [packages, search])

  const toggle = (pkg: string) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(pkg)) next.delete(pkg)
      else next.add(pkg)
      return next
    })
  }

  const selectedPackages = packages.filter((p) => selected.has(p.package_name))
  const hasSystemAppSelected = selectedPackages.some((p) => p.is_system)

  const requestRemoval = () => {
    if (selected.size === 0) return
    if (hasSystemAppSelected) {
      setRiskInput('')
      setShowRiskDialog(true)
    } else {
      performRemoval()
    }
  }

  const performRemoval = async () => {
    setShowRiskDialog(false)
    setIsRunning(true)
    setIsDone(false)
    setOutput([])
    try {
      for (const pkg of selectedPackages) {
        await invoke('uninstall_package', { packageName: pkg.package_name })
      }
      setIsDone(true)
      setSelected(new Set())
      await loadPackages()
    } catch (e) {
      setOutput((prev) => [...prev, `[ERRO] ${e}`])
    } finally {
      setIsRunning(false)
    }
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <div className="flex flex-col items-center mb-6">
          <IconTile icon={Trash2Icon} tone="danger" size={48} className="mb-4" />
          <h1 className="text-2xl font-semibold text-text tracking-tight text-center">
            Desinstalar Aplicativos
          </h1>
        </div>

        {loadError && <Banner tone="error" className="mb-4">{loadError}</Banner>}

        <div className="flex items-center gap-2 mb-4">
          <TextInput
            icon={SearchIcon}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar por pacote..."
            containerClassName="flex-1"
          />
          <Button
            variant="secondary"
            size="sm"
            fullWidth={false}
            disabled={isLoading}
            onClick={loadPackages}
            title="Recarregar lista"
          >
            <RefreshCwIcon size={16} className={isLoading ? 'animate-spin' : ''} />
          </Button>
        </div>

        <ListPanel className="mb-4">
          {isLoading && <div className="p-4 text-center text-text-tertiary text-sm">Carregando aplicativos...</div>}
          {!isLoading && filtered.length === 0 && (
            <div className="p-4 text-center text-text-tertiary text-sm">Nenhum aplicativo encontrado.</div>
          )}
          {!isLoading &&
            filtered.map((pkg) => (
              <ListRow key={pkg.package_name} className="hover:bg-white/5 cursor-pointer">
                <label className="flex items-center gap-3 w-full cursor-pointer">
                  <input
                    type="checkbox"
                    checked={selected.has(pkg.package_name)}
                    onChange={() => toggle(pkg.package_name)}
                  />
                  <span className="flex-1 text-sm text-text-secondary font-mono truncate">
                    {pkg.package_name}
                  </span>
                  {pkg.is_system && (
                    <span className="flex-shrink-0 text-[10px] uppercase tracking-wide px-2 py-0.5 rounded-full bg-warning/15 text-warning border border-warning/25">
                      Sistema
                    </span>
                  )}
                </label>
              </ListRow>
            ))}
        </ListPanel>

        {!isRunning && (
          <Button
            variant="destructive"
            size="lg"
            icon={Trash2Icon}
            disabled={selected.size === 0}
            onClick={requestRemoval}
            className="mb-4"
          >
            Remover {selected.size > 0 ? `(${selected.size})` : ''}
          </Button>
        )}

        <ProgressPanel title="Remoção" isRunning={isRunning} isDone={isDone} output={output} />

        <BackButton />
      </Card>

      {showRiskDialog && (
        <Modal onClose={() => setShowRiskDialog(false)} tone="danger">
          <div className="flex items-center gap-2 mb-3 text-danger">
            <ShieldAlertIcon size={22} />
            <h2 className="text-lg font-semibold text-text">Isso é um app de sistema</h2>
          </div>
          <p className="text-text-secondary text-sm mb-4 leading-relaxed">
            Um ou mais aplicativos selecionados são apps de sistema/nativos do carro. Removê-los
            pode deixar funções da multimídia instáveis ou colocar sua conta (garantia,
            atualizações OTA) em risco. Isso normalmente pode ser revertido reinstalando o app ou
            com um reset de fábrica, mas prossiga por sua conta e risco.
          </p>
          <p className="text-text-tertiary text-xs mb-2">
            Digite <span className="font-mono text-danger">{RISK_PHRASE}</span> para continuar:
          </p>
          <TextInput
            value={riskInput}
            onChange={(e) => setRiskInput(e.target.value)}
            placeholder={RISK_PHRASE}
            autoFocus
            containerClassName="mb-4"
          />
          <div className="flex gap-3">
            <Button variant="secondary" onClick={() => setShowRiskDialog(false)}>
              Cancelar
            </Button>
            <Button variant="destructive" disabled={riskInput !== RISK_PHRASE} onClick={performRemoval}>
              Prosseguir
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
