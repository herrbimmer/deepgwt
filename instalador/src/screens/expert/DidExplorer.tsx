// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { DatabaseIcon } from 'lucide-react'
import { ScaffoldScreen } from '../../ui'

// DIDs — a tela onde inventar seria mais fácil e mais grave.
//
// A numeração dos identificadores de dado é definida pelo fabricante, módulo a módulo. Existem
// listas genéricas circulando, e nenhuma delas vale para este veículo sem prova. Uma lista errada
// de DIDs não falha em voz alta: ela responde alguma coisa, e o técnico interpreta o byte errado
// como se fosse o número de peça. Por isso esta tela está vazia e continua vazia.
export const ExpertDidExplorer = () => (
  <ScaffoldScreen
    icon={DatabaseIcon}
    title="Identificadores de Dado (DIDs)"
    intro="Os identificadores que cada módulo aceita ler, e o formato de cada resposta."
    capability="expert.dids"
    blockers={[
      {
        what: 'Não existe transporte UDS no aplicativo',
        why: 'Ler um DID é o serviço 0x22 do UDS. Sem UDS não há nem como perguntar.',
      },
      {
        what: 'A numeração é do fabricante, e não foi levantada deste veículo',
        why: 'knowledge/dids/ está vazia. Copiar uma lista genérica da internet produziria respostas plausíveis e erradas, que é pior do que nenhuma resposta.',
      },
      {
        what: 'O formato da resposta também é do fabricante',
        why: 'Saber que um DID responde não basta: sem o layout dos bytes, o valor lido não significa nada.',
      },
    ]}
    structure={[
      { step: 'Módulo', detail: 'Qual ECU responde, e em que endereço.' },
      { step: 'Identificador', detail: 'O DID em hexadecimal, e de onde veio o conhecimento dele.' },
      { step: 'Formato', detail: 'Tamanho, codificação e unidade da resposta.' },
      { step: 'Leitura', detail: 'O valor lido, com data e com o estado do veículo no momento.' },
      { step: 'Confiança', detail: 'Cada campo acima com o seu nível: fato, observado ou deduzido.' },
    ]}
  />
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
