// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useCallback, useEffect, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { HardDriveIcon, RefreshCwIcon } from 'lucide-react'
import { Banner, Button } from '../ui'
import { useCarIp, DEMO_CAR_IP } from '../hooks/useCarIp'

/** Um volume montado na central, exatamente como `StorageInfo` do app do carro o descreve. */
export type CarVolume = {
  label: string
  path: string
  totalBytes: number
  freeBytes: number
  usedBytes: number
  removable: boolean
}

type StorageResponse = {
  ok: boolean
  totalBytes: number
  freeBytes: number
  usedBytes: number
  volumes: CarVolume[]
}

const formatSize = (bytes: number) => {
  const kb = 1024
  const mb = kb * 1024
  const gb = mb * 1024
  if (bytes >= gb) return `${(bytes / gb).toFixed(1)} GB`
  if (bytes >= mb) return `${(bytes / mb).toFixed(0)} MB`
  if (bytes >= kb) return `${(bytes / kb).toFixed(0)} KB`
  return `${bytes} B`
}

// Mesmos limiares do card no carro (85% / 95%), para que a mesma central não apareça "tranquila"
// aqui e "cheia" lá.
const barColor = (ratio: number) =>
  ratio >= 0.95 ? 'bg-danger' : ratio >= 0.85 ? 'bg-warning' : 'bg-primary'

function VolumeBar({ volume }: { volume: CarVolume }) {
  const ratio = volume.totalBytes > 0 ? Math.min(1, Math.max(0, volume.usedBytes / volume.totalBytes)) : 0
  return (
    <div className="px-4 py-3 border-b border-white/5 last:border-b-0">
      <div className="flex items-baseline justify-between gap-3 mb-2">
        <span className="text-xs font-medium text-text">{volume.label}</span>
        <span className="text-[11px] text-text-tertiary">{Math.round(ratio * 100)}% usado</span>
      </div>
      <div className="h-2 rounded-full bg-white/[0.07] overflow-hidden">
        <div className={`h-full rounded-full ${barColor(ratio)}`} style={{ width: `${ratio * 100}%` }} />
      </div>
      <div className="flex items-baseline justify-between gap-3 mt-2">
        <span className="text-[11px] text-text-tertiary">
          {formatSize(volume.usedBytes)} de {formatSize(volume.totalBytes)}
          <span className="font-mono text-[10px] text-text-tertiary/70"> · {volume.path}</span>
        </span>
        <span className="text-[11px] text-success">{formatSize(volume.freeBytes)} livres</span>
      </div>
    </div>
  )
}

// Armazenamento da central, lido pela rota `/storage` do DeepGWT no carro.
//
// Os números são os mesmos que o app mostra no carro — vêm do mesmo `StorageInfo`, não de um
// cálculo próprio deste lado. Do lado do carro é `StatFs`, sem Shizuku, então isto funciona mesmo
// numa instalação recém-feita em que nada foi autorizado ainda.
export function CarStoragePanel() {
  const { carIp } = useCarIp()
  const [data, setData] = useState<StorageResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    if (!carIp || carIp === DEMO_CAR_IP) return
    setLoading(true)
    setError(null)
    try {
      const raw = await invoke<string>('remote_storage_info', { carIp })
      setData(JSON.parse(raw) as StorageResponse)
    } catch (e: any) {
      setError(e.toString())
    } finally {
      setLoading(false)
    }
  }, [carIp])

  useEffect(() => {
    load()
  }, [load])

  const volumes = data?.volumes ?? []

  return (
    <section>
      <div className="flex items-center gap-2 mb-3">
        <HardDriveIcon size={15} className="text-text-tertiary" />
        <h2 className="text-sm font-semibold text-text">Armazenamento da central</h2>
        <Button
          variant="ghost"
          size="sm"
          icon={RefreshCwIcon}
          fullWidth={false}
          className="ml-auto"
          disabled={loading || !carIp || carIp === DEMO_CAR_IP}
          onClick={load}
        >
          {loading ? 'Lendo…' : 'Atualizar'}
        </Button>
      </div>

      {!carIp && (
        <Banner tone="info">
          Sem conexão com o carro. O espaço é lido do próprio veículo — não há como estimá-lo daqui.
        </Banner>
      )}

      {carIp === DEMO_CAR_IP && (
        <Banner tone="info">
          Modo demonstração: nenhum carro é consultado, então não há armazenamento para mostrar.
        </Banner>
      )}

      {error && (
        <Banner tone="error">
          Não foi possível ler o armazenamento: {error}
          <span className="block text-[11px] mt-1 opacity-80">
            Esta leitura exige o DeepGWT aberto no carro e uma versão que já tenha a rota
            /storage.
          </span>
        </Banner>
      )}

      {data && volumes.length === 0 && !error && (
        <Banner tone="warning">O carro respondeu, mas não reportou nenhum volume legível.</Banner>
      )}

      {volumes.length > 0 && (
        <>
          <div className="rounded-2xl border border-white/[0.08] overflow-hidden">
            {volumes.map((v) => (
              <VolumeBar key={v.path} volume={v} />
            ))}
          </div>
          {volumes.length > 1 && (
            <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
              Somando todos os volumes: {formatSize(data!.usedBytes)} em uso de{' '}
              {formatSize(data!.totalBytes)}, {formatSize(data!.freeBytes)} livres.
            </p>
          )}
          <p className="text-[11px] text-text-tertiary mt-2 leading-relaxed">
            "Livre" é o espaço que um aplicativo conseguiria gravar de fato; a reserva interna do
            sistema fica de fora, para não prometer espaço que não existe.
          </p>
        </>
      )}
    </section>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
