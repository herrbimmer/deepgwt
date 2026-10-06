// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { BackButton, Banner, Card, ToggleRow } from '../ui'
import { useCarIp } from '../hooks/useCarIp'
import { useCarSettingsSync } from '../hooks/useCarSettingsSync'

const KEY_AVAS = 'car.ev.setting.avas_enable'
const KEY_DMS = 'car.frs_setting.distraction_detection_enable'
const KEY_WINDOW_CLOSE_ON_LOCK = 'car.basic.window_close_by_lock_vehicle'

export const AjustesAvancados = () => {
  const { carIp } = useCarIp()
  const { get, set, fieldStatus, error } = useCarSettingsSync(carIp)

  return (
    <div className="flex-1 flex flex-col items-center justify-center p-6 overflow-y-auto">
      <Card maxWidth="md">
        <h1 className="text-xl font-semibold text-center mb-2 text-text tracking-tight">
          Ajustes rápidos
        </h1>
        <p className="text-text-tertiary mb-6 text-center text-sm leading-relaxed">
          Altera na hora, direto no carro. O que mudar por lá também aparece aqui em poucos segundos.
        </p>

        {error && (
          <Banner tone="error" className="mb-4">
            {error}
          </Banner>
        )}

        <div className="space-y-3 mb-6">
          <ToggleRow
            title="Desativar AVAS"
            description="Sistema de alerta de veículo silencioso."
            checked={get(KEY_AVAS) !== '1'}
            onChange={(checked) => set(KEY_AVAS, checked ? '0' : '1')}
            status={fieldStatus[KEY_AVAS]}
            disabled={!carIp}
          />
          <ToggleRow
            title="Desativar monitoramento de distração"
            description="Impede o sistema de câmera interna (DMS) de monitorar distração do motorista."
            checked={get(KEY_DMS) !== '1'}
            onChange={(checked) => set(KEY_DMS, checked ? '0' : '1')}
            status={fieldStatus[KEY_DMS]}
            disabled={!carIp}
          />
          <ToggleRow
            title="Fechar vidros ao trancar"
            description="Ao trancar o veículo, fecha os vidros automaticamente (recurso nativo do carro — valide o comportamento no seu veículo)."
            checked={get(KEY_WINDOW_CLOSE_ON_LOCK) === '1'}
            onChange={(checked) => set(KEY_WINDOW_CLOSE_ON_LOCK, checked ? '1' : '0')}
            status={fieldStatus[KEY_WINDOW_CLOSE_ON_LOCK]}
            disabled={!carIp}
          />
        </div>

        <BackButton to="/config-avancadas" />
      </Card>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
