import { TypeIcon } from 'lucide-react'
import { ScaffoldScreen } from '../../ui'

// Explorador de strings — vazio, e a razão do vazio é o conteúdo da tela.
//
// A saída fácil aqui seria listar os textos do PRÓPRIO aplicativo e chamar isso de "strings do
// veículo". São coisas diferentes, e num explorador de engenharia a diferença é grande: uma string
// do carro diz como o software do veículo nomeia uma função internamente; uma string deste app diz
// como nós a nomeamos em português. A segunda não ensina nada sobre o carro.
export const ExpertStringExplorer = () => (
  <ScaffoldScreen
    icon={TypeIcon}
    title="Explorador de Strings"
    intro="As tabelas de texto do software do veículo, por idioma e por módulo."
    capability="expert.strings"
    blockers={[
      {
        what: 'Nenhuma tabela de strings do veículo foi levantada',
        why: 'A pasta knowledge/strings/ existe na estrutura e está vazia. Enquanto estiver, não há o que explorar.',
      },
      {
        what: 'Os textos deste aplicativo não servem no lugar',
        why: 'São a nossa tradução em português, não o vocabulário interno do carro. Mostrá-los aqui seria passar uma coisa por outra.',
      },
      {
        what: 'Extrair do veículo exige acesso ao sistema de arquivos da multimídia',
        why: 'As tabelas ficam em recursos do software do carro. Chegar até elas é trabalho da Fase 2, com o veículo alcançável.',
      },
    ]}
    structure={[
      { step: 'Origem', detail: 'De qual módulo e de qual arquivo a tabela foi extraída, com data.' },
      { step: 'Identificador', detail: 'A chave interna do texto, como o software do carro a nomeia.' },
      { step: 'Texto por idioma', detail: 'O valor em cada idioma presente na tabela.' },
      { step: 'Onde aparece', detail: 'Em que tela do carro o texto foi observado — deduzido, e marcado como tal.' },
    ]}
  />
)
