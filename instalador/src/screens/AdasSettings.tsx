// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useState } from 'react'
import { BackButton, Banner, Button, Card, ToggleRow } from '../ui'
import { useCarIp } from '../hooks/useCarIp'
import { useCarSettingsSync } from '../hooks/useCarSettingsSync'
import { ADAS_GROUPS as GROUPS, ADAS_TOGGLES } from '../data/adasCatalog'

export const AdasSettings = () => {
  const { carIp } = useCarIp()
  const { get, set, fieldStatus, error } = useCarSettingsSync(carIp)
  const [acknowledged, setAcknowledged] = useState(false)

  if (!acknowledged) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
        <Card maxWidth="md">
          <h1 className="text-xl font-semibold mb-4 text-text tracking-tight">Antes de continuar</h1>
          <p className="text-text-secondary text-sm leading-relaxed mb-6">
            Estes ajustes controlam sistemas de segurança ativa do veículo (assistente de faixa,
            frenagem de emergência, alertas de colisão, estacionamento assistido, etc). Desativar
            algum deles pode reduzir a segurança do carro e, dependendo da sua legislação local,
            ter implicações legais.
            <br />
            <br />
            O valor exato que cada ajuste espera não foi confirmado por documentação oficial — só
            o padrão "ligado/desligado" já validado em outras funções. Teste cada opção com
            cuidado no seu carro antes de confiar nela.
          </p>
          <Button variant="primary" onClick={() => setAcknowledged(true)}>
            Entendi, continuar
          </Button>
          <div className="mt-6">
            <BackButton to="/config-avancadas" />
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col items-center p-6 overflow-y-auto">
      <Card maxWidth="lg">
        <h1 className="text-xl font-semibold text-center mb-2 text-text tracking-tight">ADAS</h1>
        <p className="text-text-tertiary mb-6 text-center text-sm leading-relaxed">
          Altera na hora, direto no carro. O que mudar por lá também aparece aqui em poucos segundos.
        </p>

        {error && (
          <Banner tone="error" className="mb-4">
            {error}
          </Banner>
        )}

        <div className="space-y-6 mb-6">
          {GROUPS.map((group) => (
            <div key={group}>
              <h2 className="type-label text-text-secondary mb-3">
                {group}
              </h2>
              <div className="space-y-3">
                {ADAS_TOGGLES.filter((t) => t.group === group).map((toggle) => (
                  <ToggleRow
                    key={toggle.key}
                    title={toggle.title}
                    description={toggle.description}
                    checked={get(toggle.key) === '1'}
                    onChange={(checked) => set(toggle.key, checked ? '1' : '0')}
                    status={fieldStatus[toggle.key]}
                    disabled={!carIp}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>

        <BackButton to="/config-avancadas" />
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
