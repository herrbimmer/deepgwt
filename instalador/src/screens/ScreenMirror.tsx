// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useCallback, useEffect, useRef, useState } from 'react'
import { invoke } from '@tauri-apps/api/core'
import { listen } from '@tauri-apps/api/event'
import { save } from '@tauri-apps/plugin-dialog'
import { revealItemInDir } from '@tauri-apps/plugin-opener'
import {
  ArrowLeftIcon,
  CameraIcon,
  HomeIcon,
  MonitorSmartphoneIcon,
  PauseIcon,
  PlayIcon,
  RefreshCwIcon,
} from 'lucide-react'
import { useCarIp } from '../hooks/useCarIp'
import { BackButton, Banner, Button, Card, IconTile, Segmented, Squircle } from '../ui'

type DisplayInfo = { id: number; name: string; width: number; height: number }

// Espelhar e controlar a central (Alpha 7.6: saiu de Ferramentas, modo Avançado, e voltou para o
// Início). Ver a tela do carro ao vivo, tocar, arrastar, segurar, rolar, Voltar/Início e salvar uma
// print em resolução cheia.
//
// A imagem ao vivo é JPEG reduzido (`/mirror`), para caber no Wi-Fi do carro a vários quadros por
// segundo; a print vem do `/screenshot`, PNG cru do `screencap`, na resolução da tela.

type Qualidade = 'rapida' | 'nitida'
const QUALIDADES: Record<Qualidade, { maxWidth: number; quality: number; fps: number }> = {
  rapida: { maxWidth: 720, quality: 40, fps: 10 },
  nitida: { maxWidth: 1280, quality: 60, fps: 8 },
}

// Android: KEYCODE_BACK e KEYCODE_HOME.
const TECLA_VOLTAR = 4
const TECLA_INICIO = 3

/** Segurar o clique por pelo menos isto sem arrastar vira toque longo. */
const TOQUE_LONGO_MS = 500
/** Um "degrau" da roda do mouse vira um arrasto deste tamanho, em px da tela do carro. */
const ROLAGEM_PX = 260
const ROLAGEM_INTERVALO_MS = 350

const nomeDaTela = (d: DisplayInfo) =>
  d.id === 0 ? 'Central' : d.id === 1 || d.id === 3 ? `Painel (${d.id})` : `${d.name} (${d.id})`

function carimbo(): string {
  const a = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${a.getFullYear()}-${p(a.getMonth() + 1)}-${p(a.getDate())} ${p(a.getHours())}.${p(a.getMinutes())}.${p(a.getSeconds())}`
}

export const ScreenMirror = () => {
  const { carIp } = useCarIp()
  const [displays, setDisplays] = useState<DisplayInfo[]>([])
  const [selected, setSelected] = useState<DisplayInfo | null>(null)
  const [frameUrl, setFrameUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<{ texto: string; caminho?: string } | null>(null)
  const [loadingDisplays, setLoadingDisplays] = useState(true)
  const [transmitindo, setTransmitindo] = useState(false)
  const [salvando, setSalvando] = useState(false)
  const [qualidade, setQualidade] = useState<Qualidade>('nitida')
  const imgRef = useRef<HTMLImageElement>(null)
  const dragStart = useRef<{ x: number; y: number; t: number } | null>(null)
  const ultimaRolagem = useRef(0)

  const loadDisplays = useCallback(async () => {
    setLoadingDisplays(true)
    setError(null)
    try {
      const raw = await invoke<string>('remote_list_displays', { carIp })
      const parsed = JSON.parse(raw) as DisplayInfo[]
      setDisplays(parsed)
      return parsed
    } catch (e: any) {
      setError(e.toString())
      return []
    } finally {
      setLoadingDisplays(false)
    }
  }, [carIp])

  const startDisplay = useCallback(
    async (display: DisplayInfo, q: Qualidade = qualidade) => {
      setError(null)
      setFrameUrl(null)
      setSelected(display)
      try {
        await invoke('start_mirror', { carIp, display: display.id, ...QUALIDADES[q] })
        setTransmitindo(true)
      } catch (e: any) {
        setTransmitindo(false)
        setError(e.toString())
      }
    },
    [carIp, qualidade],
  )

  const stopMirror = useCallback(async () => {
    setTransmitindo(false)
    try {
      await invoke('stop_mirror')
    } catch {
      // sem stream ativo, ignora
    }
  }, [])

  // Abre direto na central: é o que quem chega aqui quer ver.
  useEffect(() => {
    let vivo = true
    loadDisplays().then((lista) => {
      const central = lista.find((d) => d.id === 0)
      if (vivo && central) startDisplay(central)
    })
    return () => {
      vivo = false
    }
    // Só na entrada da tela; trocar de tela ou de qualidade é pelos botões.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadDisplays])

  useEffect(() => {
    const unlistenFrame = listen<{ display: number; dataUrl: string }>('mirror-frame', (event) => {
      if (selected && event.payload.display === selected.id) {
        setFrameUrl(event.payload.dataUrl)
      }
    })
    const unlistenError = listen<string>('mirror-error', (event) => {
      setError(event.payload)
    })
    // O carro fecha o stream quando a tela para de responder (10 capturas seguidas sem imagem) ou
    // a conexão cai. Sem isto a tela ficava parada no último quadro, parecendo viva.
    const unlistenStopped = listen('mirror-stopped', () => {
      setTransmitindo(false)
    })
    return () => {
      unlistenFrame.then((u) => u())
      unlistenError.then((u) => u())
      unlistenStopped.then((u) => u())
    }
  }, [selected])

  useEffect(() => {
    return () => {
      stopMirror()
    }
  }, [stopMirror])

  const toDeviceCoords = (clientX: number, clientY: number): [number, number] | null => {
    if (!imgRef.current || !selected) return null
    const rect = imgRef.current.getBoundingClientRect()
    const relX = (clientX - rect.left) / rect.width
    const relY = (clientY - rect.top) / rect.height
    if (relX < 0 || relX > 1 || relY < 0 || relY > 1) return null
    return [Math.round(relX * selected.width), Math.round(relY * selected.height)]
  }

  const reportInputResult = (raw: string) => {
    try {
      const res = JSON.parse(raw) as { ok: boolean; exitCode: number; output: string }
      if (!res.ok) {
        setError(`O toque não chegou (exit ${res.exitCode})${res.output ? `: ${res.output}` : ''}`)
      } else {
        setError(null)
      }
    } catch {
      // resposta inesperada, ignora
    }
  }

  const swipe = async (x1: number, y1: number, x2: number, y2: number, durationMs: number) => {
    if (!selected) return
    const raw = await invoke<string>('remote_input_swipe', {
      carIp,
      display: selected.id,
      x1,
      y1,
      x2,
      y2,
      durationMs,
    })
    reportInputResult(raw)
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    if (e.button !== 0) return
    dragStart.current = { x: e.clientX, y: e.clientY, t: performance.now() }
  }

  const handleMouseUp = async (e: React.MouseEvent) => {
    if (!selected || !dragStart.current) return
    const start = dragStart.current
    dragStart.current = null
    const dx = e.clientX - start.x
    const dy = e.clientY - start.y
    const segurou = performance.now() - start.t
    const startCoords = toDeviceCoords(start.x, start.y)
    if (!startCoords) return

    try {
      if (Math.abs(dx) < 6 && Math.abs(dy) < 6) {
        if (segurou >= TOQUE_LONGO_MS) {
          // `input` do Android 9 não tem "longpress": um arrasto parado no mesmo ponto é o mesmo gesto.
          await swipe(startCoords[0], startCoords[1], startCoords[0], startCoords[1], Math.max(600, Math.round(segurou)))
          return
        }
        const raw = await invoke<string>('remote_input_tap', {
          carIp,
          display: selected.id,
          x: startCoords[0],
          y: startCoords[1],
        })
        reportInputResult(raw)
        return
      }

      const endCoords = toDeviceCoords(e.clientX, e.clientY)
      if (!endCoords) return
      await swipe(startCoords[0], startCoords[1], endCoords[0], endCoords[1], Math.min(1200, Math.max(200, Math.round(segurou))))
    } catch (e: any) {
      setError(e.toString())
    }
  }

  // Roda do mouse = arrastar a lista na direção da rolagem, no ponto em que o ponteiro está.
  const handleWheel = async (e: React.WheelEvent) => {
    if (!selected || e.deltaY === 0) return
    const agora = performance.now()
    if (agora - ultimaRolagem.current < ROLAGEM_INTERVALO_MS) return
    ultimaRolagem.current = agora
    const ponto = toDeviceCoords(e.clientX, e.clientY)
    if (!ponto) return
    const sentido = e.deltaY > 0 ? -1 : 1
    const y2 = Math.max(0, Math.min(selected.height - 1, ponto[1] + sentido * ROLAGEM_PX))
    try {
      await swipe(ponto[0], ponto[1], ponto[0], y2, 250)
    } catch (err: any) {
      setError(err.toString())
    }
  }

  const tecla = async (keycode: number) => {
    if (!selected) return
    try {
      const raw = await invoke<string>('remote_input_key', { carIp, display: selected.id, keycode })
      reportInputResult(raw)
    } catch (e: any) {
      setError(e.toString())
    }
  }

  const salvarPrint = async () => {
    if (!selected) return
    const localPath = await save({
      defaultPath: `DeepGWT ${nomeDaTela(selected)} ${carimbo()}.png`,
      filters: [{ name: 'Imagem PNG', extensions: ['png'] }],
    })
    if (!localPath) return
    setSalvando(true)
    setError(null)
    try {
      await invoke('remote_screenshot_save', { carIp, display: selected.id, localPath })
      setAviso({ texto: 'Print salva.', caminho: localPath })
    } catch (e: any) {
      setError(`Não foi possível salvar a print: ${e.toString()}`)
    } finally {
      setSalvando(false)
    }
  }

  const trocarQualidade = (q: Qualidade) => {
    setQualidade(q)
    if (selected && transmitindo) startDisplay(selected, q)
  }

  const podeTocar = selected !== null && selected.id === 0

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="2xl">
        <div className="flex items-center justify-between mb-4 gap-3">
          <div className="flex items-center gap-3">
            <IconTile icon={MonitorSmartphoneIcon} tone="primary" size={40} />
            <div>
              <h1 className="text-xl font-semibold text-text tracking-tight">Espelhar e controlar</h1>
              <p className="text-xs text-text-tertiary">A tela do carro neste computador. Clique para tocar.</p>
            </div>
          </div>
          <button
            onClick={loadDisplays}
            className="flex items-center gap-1 text-xs text-text-tertiary hover:text-text flex-shrink-0"
          >
            <RefreshCwIcon size={14} className={loadingDisplays ? 'animate-spin' : ''} />
            <span>Redetectar telas</span>
          </button>
        </div>

        {error && <Banner tone="error" className="mb-4">{error}</Banner>}
        {aviso && (
          <Banner tone="success" className="mb-4">
            <span>{aviso.texto}</span>
            {aviso.caminho && (
              <button
                className="ml-2 underline underline-offset-2"
                onClick={() => aviso.caminho && revealItemInDir(aviso.caminho).catch(() => {})}
              >
                Mostrar no Finder
              </button>
            )}
          </Banner>
        )}

        <div className="flex flex-wrap items-end gap-3 mb-4">
          <div className="flex flex-wrap gap-2 flex-1">
            {displays.map((d) => (
              <Squircle
                key={d.id}
                as="button"
                radius={14}
                border={{ color: selected?.id === d.id ? 'rgba(10,132,255,0.5)' : 'rgba(255,255,255,0.1)' }}
                onClick={() => startDisplay(d)}
                className={`px-4 py-2 text-sm transition-colors ${
                  selected?.id === d.id ? 'bg-primary text-white' : 'bg-white/[0.04] text-text-secondary hover:bg-white/10'
                }`}
              >
                {nomeDaTela(d)} · {d.width}x{d.height}
              </Squircle>
            ))}
            {!loadingDisplays && displays.length === 0 && !error && (
              <span className="text-sm text-text-tertiary">Nenhuma tela encontrada.</span>
            )}
          </div>
          <Segmented<Qualidade>
            size="sm"
            value={qualidade}
            onChange={trocarQualidade}
            options={[
              { value: 'nitida', label: 'Nítida', title: '1280 px, 8 quadros/s' },
              { value: 'rapida', label: 'Rápida', title: '720 px, 10 quadros/s — para Wi-Fi fraco' },
            ]}
          />
        </div>

        <Squircle radius={20} className="bg-black flex items-center justify-center min-h-[300px] overflow-hidden">
          {frameUrl ? (
            <img
              ref={imgRef}
              src={frameUrl}
              alt="Tela do carro ao vivo"
              className={`max-w-full max-h-[62vh] select-none ${podeTocar ? 'cursor-crosshair' : 'cursor-default'}`}
              onMouseDown={handleMouseDown}
              onMouseUp={handleMouseUp}
              onMouseLeave={() => (dragStart.current = null)}
              onWheel={handleWheel}
              draggable={false}
            />
          ) : (
            <span className="text-text-tertiary text-sm py-16">
              {selected ? (transmitindo ? 'Aguardando o primeiro quadro…' : 'Transmissão parada.') : 'Selecione uma tela acima'}
            </span>
          )}
        </Squircle>

        <div className="flex flex-wrap items-center gap-2 mt-4">
          <Button size="sm" fullWidth={false} icon={ArrowLeftIcon} onClick={() => tecla(TECLA_VOLTAR)} disabled={!podeTocar}>
            Voltar
          </Button>
          <Button size="sm" fullWidth={false} icon={HomeIcon} onClick={() => tecla(TECLA_INICIO)} disabled={!podeTocar}>
            Início
          </Button>
          <div className="flex-1" />
          {transmitindo ? (
            <Button size="sm" fullWidth={false} variant="ghost" icon={PauseIcon} onClick={stopMirror}>
              Pausar
            </Button>
          ) : (
            <Button size="sm" fullWidth={false} variant="ghost" icon={PlayIcon} onClick={() => selected && startDisplay(selected)} disabled={!selected}>
              Retomar
            </Button>
          )}
          <Button size="sm" fullWidth={false} variant="primary" icon={CameraIcon} onClick={salvarPrint} disabled={!selected || salvando}>
            {salvando ? 'Salvando…' : 'Salvar print'}
          </Button>
        </div>

        <p className="text-xs text-text-tertiary mt-3 leading-relaxed">
          Clique = toque · segure = toque longo · arraste = deslizar · roda do mouse = rolar. A print sai
          em PNG, na resolução da tela. O toque só vale na central: nesta versão do Android o{' '}
          <code>input</code> não alcança as telas do painel, que ficam só para ver e para print.
        </p>

        <div className="mt-6">
          <BackButton onClick={stopMirror} />
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
