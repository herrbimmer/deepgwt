// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { BrowserRouter as Router } from 'react-router-dom'
import { MotionConfig } from 'motion/react'
import { Cabecalho } from './components'
import { SideNav } from './components/SideNav'
import { AppRoutes } from './navigation/AppRoutes'
import { PrefsProvider } from './state/PrefsProvider'
import { NivelProvider } from './tiers/NivelProvider'
import { VehicleSessionProvider } from './state/VehicleSession'
import { FormFactorProvider } from './hooks/useFormFactor'

export function Aplicativo() {
  return (
    // reducedMotion="user" respeita o prefers-reduced-motion do SO/navegador pra toda a
    // árvore de uma vez (colapsa springs/whileTap pra troca instantânea de valores).
    <MotionConfig reducedMotion="user">
      <Router>
        <FormFactorProvider>
          <PrefsProvider>
            <VehicleSessionProvider>
              {/* O nível vem do carro: precisa da sessão (IP e conexão) por fora. */}
              <NivelProvider>
                {/* `app-shell`: no desktop o CSS global limita a largura e centraliza esta coluna,
                    dando a moldura de "janela de app" em vez de esticar até a borda do ultrawide. */}
                <div className="app-shell w-full min-h-screen flex flex-col bg-base text-text">
                  <Cabecalho />
                  <div className="flex flex-1 min-h-0">
                    <SideNav />
                    <AppRoutes />
                  </div>
                </div>
              </NivelProvider>
            </VehicleSessionProvider>
          </PrefsProvider>
        </FormFactorProvider>
      </Router>
    </MotionConfig>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
