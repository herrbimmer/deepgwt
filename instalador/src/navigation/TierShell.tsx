// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Suspense } from 'react'
import { Link, Outlet } from 'react-router-dom'
import { WifiOffIcon } from 'lucide-react'
import { Banner, DemoBanner, UnvalidatedNotice } from '../ui'
import { useVehicleSession } from '../state/VehicleSession'
import { Breadcrumb } from './Breadcrumb'
import { pedeRisco, useRouteNode } from './routeMeta'
import { useNivel } from '../tiers/NivelProvider'
import { ConfirmacaoDeRisco, TelaBloqueadaPeloNivel } from '../tiers/TelasDoNivel'
import { unmetRequirement } from './requirements'

// Casca comum a todas as telas. Deliberadamente fina: cada tela já renderiza o seu próprio
// `flex-1 flex flex-col items-center justify-center p-6`, então a casca só acrescenta o contexto
// (trilha, modo demo, aviso de validação) acima e deixa o `<Outlet/>` ocupar o resto.
//
// O aviso de validação é renderizado AQUI, a partir da árvore de rotas — assim uma tela nova não
// consegue esquecer de mostrá-lo; teria que mentir na árvore para escondê-lo. O aviso de conexão
// segue a mesma regra: cada tela declara na árvore como alcança o carro (`transport`), e é a casca
// que diz, no topo, o que falta e onde resolver. Antes cada tela tratava a falta de conexão do seu
// jeito — umas com um aviso, outras só com os controles apagados sem explicação.
//
// NÍVEL (Alpha 8.0). Também é aqui que a tela que o nível do carro não libera dá lugar ao cadeado,
// e que as ferramentas de escrita crua pedem a confirmação de risco na primeira entrada. Mesmo
// motivo: uma tela nova não consegue esquecer a trava.
export function TierShell() {
  const entry = useRouteNode()
  const { source, setSource, connection, headUnit } = useVehicleSession()
  const { permite, riscoConfirmado } = useNivel()
  const showChrome = entry !== null && entry.trail.length > 1
  const missing = entry ? unmetRequirement(entry.node.transport, connection, headUnit) : null
  const travada = entry !== null && !permite(entry.node.recurso)
  const semConfirmar = entry !== null && !travada && pedeRisco(entry.node.path) && !riscoConfirmado

  return (
    // `app-content`: coluna do conteúdo, ao lado da barra lateral no desktop. `min-w-0` deixa as
    // telas largas (Coding, editores) encolherem em vez de empurrar a janela para o lado.
    <div className="app-content flex-1 min-w-0 flex flex-col">
      {source === 'demo' && <DemoBanner onExit={() => setSource('live')} />}
      {/* `app-main`: âncora do CSS global de desktop. É o que permite alinhar o conteúdo ao topo
          (em vez de flutuar centralizado no meio de uma janela alta, jeito de modal de celular) e
          alargar a coluna — sem tocar em nenhuma das 45 telas. `flex-1` deixa o `flex-1` interno de
          cada tela crescer dentro daqui, preenchendo a altura como um app de desktop de verdade. */}
      <main className="app-main flex-1 flex flex-col min-h-0">
        {showChrome && (
          <div className="app-chrome w-full max-w-3xl mx-auto px-6 pt-4">
            <Breadcrumb />
            {missing && (
              <Banner tone="warning" icon={WifiOffIcon} className="mt-3">
                <span className="text-xs leading-relaxed">
                  {missing.long}{' '}
                  <Link to="/conectar" className="underline underline-offset-2 hover:opacity-80">
                    Abrir Conexão
                  </Link>
                </span>
              </Banner>
            )}
            {entry?.node.capability && (
              <div className="mt-3">
                <UnvalidatedNotice capability={entry.node.capability} />
              </div>
            )}
          </div>
        )}
        {/* As telas de oficina e engenharia são carregadas sob demanda, para que a base de
            conhecimento não entre na primeira carga do app. Enquanto o pedaço chega, a tela diz que
            está carregando — e não desenha um esqueleto de conteúdo, que é como um estado de
            carregamento vira dado de exemplo. */}
        {travada && entry ? (
          <TelaBloqueadaPeloNivel recurso={entry.node.recurso} />
        ) : semConfirmar ? (
          <ConfirmacaoDeRisco />
        ) : (
          <Suspense fallback={<Carregando />}>
            <Outlet />
          </Suspense>
        )}
      </main>
    </div>
  )
}

function Carregando() {
  return (
    <div className="flex-1 flex items-center justify-center p-6">
      <div className="text-xs text-text-tertiary">Carregando a tela…</div>
    </div>
  )
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
