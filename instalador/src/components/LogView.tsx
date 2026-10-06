// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { TerminalIcon } from 'lucide-react'
import { Squircle } from '../ui'

export type LogViewProps = {
  title: string
  /** Uma entrada por linha do registro, na ordem em que chegaram. */
  lines: string[]
  /** Mostra o cursor piscando no fim: a operação ainda está andando. */
  busy?: boolean
}

// As três bolinhas do "semáforo" de janela, só enfeite.
const SEMAFORO = ['bg-danger', 'bg-warning', 'bg-success']

/** Registro de uma operação: título, janela escura e uma linha de texto por entrada. */
export const LogView = ({ title, lines, busy = false }: LogViewProps) => (
  <>
    <div className="mb-4 flex items-center justify-between">
      <div className="flex items-center gap-3">
        <TerminalIcon size={22} className="text-success" />
        <h1 className="text-lg font-semibold tracking-tight text-text">{title}</h1>
      </div>
      <div className="flex gap-1.5" aria-hidden="true">
        {SEMAFORO.map((cor) => (
          <span key={cor} className={`h-2.5 w-2.5 rounded-full ${cor}`} />
        ))}
      </div>
    </div>
    <Squircle radius={16} className="mb-5 bg-black/50 p-4">
      <div className="h-52 overflow-auto font-mono text-sm text-success">
        {lines.map((linha, i) => (
          <div key={i} className="mb-1 whitespace-nowrap">
            {linha}
          </div>
        ))}
        {busy && <span className="ml-1 inline-block h-4 w-2 animate-pulse bg-success" />}
      </div>
    </Squircle>
  </>
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
