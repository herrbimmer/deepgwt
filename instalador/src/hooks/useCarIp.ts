// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { useVehicleSession } from '../state/VehicleSession'

/** Endereço simbólico do modo demonstração — nunca é usado em uma requisição real. */
export const DEMO_CAR_IP = 'demo'

// IP do carro. A implementação real vive agora na sessão do veículo (`state/VehicleSession`),
// que é dona de um único IP para o app inteiro — antes cada tela mantinha o seu próprio
// `useState` sobre a mesma chave de localStorage, então mudar o IP em uma tela não refletia nas
// outras dentro da mesma sessão.
//
// A assinatura foi mantida de propósito: as 12 telas que já usavam este hook continuam
// funcionando sem alteração.
export const useCarIp = () => {
  const { carIp, setCarIp, source } = useVehicleSession()
  // No modo demonstração devolvemos um endereço simbólico para que os gates `if (!carIp)` que
  // já existiam em cada tela liberem a interface, sem precisar ensinar o modo demo a todas
  // elas. Nenhuma requisição é feita: a sessão intercepta leitura e escrita antes do `invoke`.
  return { carIp: source === 'demo' ? DEMO_CAR_IP : carIp, setCarIp }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
