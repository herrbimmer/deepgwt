// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import { Link } from 'react-router-dom'
import { MemoryStickIcon } from 'lucide-react'
import { Banner, ScaffoldScreen } from '../../ui'

// Informação de ECU — vazia, com dois desvios para o que EXISTE.
//
// Existe informação de versão neste app, e ela é real: a multimídia informa a própria versão, a do
// MCU e a da T-Box. Isso não é informação de ECU, e a diferença é a que separa "o que o rádio diz
// sobre si mesmo" de "o que o módulo de freio responde quando perguntado". Quem chega nesta tela
// procurando número de peça precisa saber, antes de sair, que o que existe é a outra coisa — e
// onde ela está.
export const ExpertEcuInformation = () => (
  <ScaffoldScreen
    icon={MemoryStickIcon}
    title="Informação de ECU"
    intro="Número de peça, versão de software, calibração e fornecedor, módulo a módulo."
    capability="expert.ecu-info"
    blockers={[
      {
        what: 'Identificação de módulo é leitura UDS',
        why: 'Número de peça e calibração vivem em identificadores de dado lidos pelo serviço 0x22. Sem UDS, não há pergunta a fazer.',
      },
      {
        what: 'Não há enumeração de módulos por nenhuma interface atual',
        why: 'Nem o telnet nem a API do app DeepGWT listam ECUs. O que existe é o dicionário de chaves da multimídia.',
      },
      {
        what: 'Os prefixos de sinal do catálogo VHAL não são um inventário',
        why: 'Um prefixo como GW_FD1_ sugere origem, e sugestão não é módulo presente, endereço nem versão.',
      },
    ]}
    structure={[
      { step: 'Módulo', detail: 'Nome, endereço de diagnóstico e barramento em que responde.' },
      { step: 'Identificação', detail: 'Número de peça, fornecedor, número de série.' },
      { step: 'Software', detail: 'Versão, data de programação e nível de calibração.' },
      { step: 'Estado', detail: 'Se responde, em que sessão, e com que falhas memorizadas.' },
    ]}
  >
    <Banner tone="info">
      <div className="font-medium mb-0.5">O que existe hoje, e não é isto</div>
      <div className="text-xs opacity-85 leading-relaxed space-y-1">
        <div>
          <Link to="/pro/modulos" className="underline hover:text-text">
            Informações de Módulo
          </Link>{' '}
          — as versões que a multimídia informa sobre si mesma, sobre o MCU e sobre a T-Box. É real,
          e é o rádio falando do rádio.
        </div>
        <div>
          <Link to="/expert/vhal" className="underline hover:text-text">
            Explorador VHAL
          </Link>{' '}
          — os prefixos de sinal por trás das propriedades. É dedução sobre origem de sinal, não
          inventário de módulo.
        </div>
      </div>
    </Banner>
  </ScaffoldScreen>
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
