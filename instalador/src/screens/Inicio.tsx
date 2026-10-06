// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { PackageIcon } from 'lucide-react'
import { MenuPage } from '../navigation/RouteMenu'
import { RestartHeadUnitCard } from '../components/RestartHeadUnitCard'
import { APP_VERSION } from '../lib/version'

// "Instalar e manter" (/deepgwt). Os itens vêm da árvore de rotas; aqui só entram a versão que
// este instalador leva para o carro — que é o que responde "preciso atualizar?" — e o reinício
// da central, que é manutenção mas não é um destino.
export const Inicio = () => (
  <MenuPage
    path="/deepgwt"
    intro={
      <div className="mb-5 flex items-center gap-2.5 rounded-2xl border border-white/[0.08] bg-white/[0.02] px-4 py-3 text-xs text-text-secondary">
        <PackageIcon size={15} className="text-text-tertiary flex-shrink-0" />
        <span>
          Este instalador leva para o carro o DeepGWT{' '}
          <span className="font-mono text-text">{APP_VERSION}</span>. Compare com a versão que aparece
          em Ajustes → Atualização, na central.
        </span>
      </div>
    }
    footer={<RestartHeadUnitCard />}
  />
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
