import { useEffect, useRef, useState } from 'react'
import { ChevronDownIcon, ChevronRightIcon } from 'lucide-react'
import { LogView } from './LogView'

export type ProgressPanelProps = {
  title: string
  isRunning: boolean
  isDone: boolean
  output: string[]
}

// Barra de progresso + porcentagem no lugar do parágrafo explicando o que o instalador
// está fazendo. Não existe um sinal real de progresso vindo do backend (as operações só
// emitem linhas de log via evento `telnet-output`), então a barra usa uma curva de avanço
// simulada (rápida no início, desacelera perto de 90%) até a operação terminar de verdade
// e ela pular pra 100% — o mesmo padrão usado por instaladores que não sabem o tamanho
// total do trabalho de antemão. O log de verdade continua disponível em "Modo avançado".
export const ProgressPanel = ({ title, isRunning, isDone, output }: ProgressPanelProps) => {
  const [progress, setProgress] = useState(0)
  const [advancedOpen, setAdvancedOpen] = useState(false)
  const intervalRef = useRef<number | null>(null)

  useEffect(() => {
    if (intervalRef.current) {
      window.clearInterval(intervalRef.current)
      intervalRef.current = null
    }

    if (isRunning) {
      setProgress((p) => (p <= 0 ? 4 : p))
      intervalRef.current = window.setInterval(() => {
        setProgress((p) => {
          if (p >= 90) return p
          const step = Math.max(0.4, (90 - p) * 0.06)
          return Math.min(90, p + step)
        })
      }, 200)
    } else if (isDone) {
      setProgress(100)
    } else {
      setProgress(0)
    }

    return () => {
      if (intervalRef.current) window.clearInterval(intervalRef.current)
    }
  }, [isRunning, isDone])

  if (!isRunning && !isDone && output.length === 0) return null

  return (
    <div className="mb-4">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm text-text-secondary">{title}</span>
        <span className="text-sm font-mono text-text-secondary">{Math.round(progress)}%</span>
      </div>
      <div className="w-full h-1.5 bg-white/10 rounded-full overflow-hidden mb-3">
        <div
          className={`h-full rounded-full transition-all duration-300 ease-out ${
            isDone ? 'bg-success' : 'bg-primary'
          }`}
          style={{ width: `${progress}%` }}
        />
      </div>
      <button
        onClick={() => setAdvancedOpen((v) => !v)}
        className="flex items-center gap-1 text-xs text-text-tertiary hover:text-text-secondary mb-2 transition-colors"
        type="button"
      >
        {advancedOpen ? <ChevronDownIcon size={14} /> : <ChevronRightIcon size={14} />}
        <span>Modo avançado</span>
      </button>
      {advancedOpen && (
        <LogView title={title} lines={output} busy={isRunning} />
      )}
    </div>
  )
}
