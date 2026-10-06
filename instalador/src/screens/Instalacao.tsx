// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronDownIcon, DownloadIcon, FileUpIcon, PackageIcon } from 'lucide-react'
import { BackButton, Button, Card, IconTile, NavTile } from '../ui'

// Instalar é livre em qualquer nível (Alpha 8.0). O caminho principal é um botão só; o que serve
// a quem sabe o que está fazendo — instalar um APK escolhido à mão — fica recolhido.
export const Instalacao = () => {
  const [avancadoAberto, setAvancadoAberto] = useState(false)

  return (
    <div className="flex flex-1 flex-col items-center justify-center overflow-y-auto p-6">
      <Card maxWidth="sm">
        <header className="mb-6 flex flex-col items-center">
          <IconTile icon={DownloadIcon} tone="primary" size={48} className="mb-4" />
          <h1 className="type-title text-center text-text">Instalar no carro</h1>
          <p className="mt-2 text-center text-sm text-text-secondary">
            Conecte este computador ao Wi-Fi da central e clique em instalar. Isso já cuida de tudo
            (Shizuku incluso).
          </p>
        </header>

        <div className="space-y-3">
          <Button as={Link} to="/install/deepgwt" variant="success" size="lg" icon={PackageIcon}>
            Instalar no Carro
          </Button>

          <button
            type="button"
            onClick={() => setAvancadoAberto((aberto) => !aberto)}
            className="flex w-full items-center justify-center gap-1 py-2 text-xs text-text-tertiary transition-colors hover:text-text-secondary"
          >
            Opções avançadas
            <ChevronDownIcon size={14} className={avancadoAberto ? 'rotate-180 transition-transform' : 'transition-transform'} />
          </button>

          {avancadoAberto && (
            <div className="space-y-3 pt-1">
              <NavTile as={Link} to="/install/local" icon={FileUpIcon} tone="neutral" title="Instalar um APK local específico" />
            </div>
          )}

          <div className="pt-2">
            <BackButton to="/deepgwt" />
          </div>
        </div>
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
