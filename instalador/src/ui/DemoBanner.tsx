// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { FlaskConicalIcon } from 'lucide-react'

// Enquanto o modo demo está ativo este aviso fica visível, sempre. Dado sintético nunca pode
// ser confundido com leitura do veículo.
export function DemoBanner({ onExit }: { onExit?: () => void }) {
  return (
    <div className="w-full bg-warning/15 border-b border-warning/25 px-4 py-1.5 flex items-center justify-center gap-3 text-warning text-xs">
      <FlaskConicalIcon size={13} />
      <span className="font-medium tracking-wide">
        MODO DEMONSTRAÇÃO — dados sintéticos, nenhum veículo conectado
      </span>
      {onExit && (
        <button onClick={onExit} className="underline underline-offset-2 hover:opacity-80">
          sair
        </button>
      )}
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
