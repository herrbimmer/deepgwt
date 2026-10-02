import { SlidersIcon } from 'lucide-react'
import { Banner, ScaffoldScreen } from '../../ui'

// Calibração e áreas de dados de módulo.
//
// Esta é a tela de maior consequência do nível de engenharia, e a única onde o aviso importa mais
// que a estrutura: escrever bloco de calibração errado não dá mensagem de erro, dá módulo que não
// dá partida. Como o app não tem UDS nem autenticação por semente e chave, o risco é teórico hoje
// — e o aviso fica registrado desde já, para que a capacidade não nasça sem ele.
export const ExpertCalibrationExplorer = () => (
  <ScaffoldScreen
    icon={SlidersIcon}
    title="Calibração e Dados de Módulo"
    intro="Os blocos de calibração e as áreas de dado que cada módulo guarda."
    capability="expert.calibration"
    blockers={[
      {
        what: 'Não existe transporte UDS no aplicativo',
        why: 'Ler ou escrever área de dado de módulo passa pelos serviços 0x22, 0x2E e 0x31 do UDS. Nenhum deles existe aqui.',
      },
      {
        what: 'A maioria dos blocos exige autenticação por semente e chave',
        why: 'O serviço 0x27 pede um segredo do fabricante que este projeto não tem e não vai adivinhar.',
      },
      {
        what: 'O mapa de memória deste veículo não foi levantado',
        why: 'Sem saber onde cada bloco começa e o que cada byte significa, ler endereço é sorteio.',
      },
    ]}
    structure={[
      { step: 'Módulo e bloco', detail: 'Qual ECU, qual área, e qual o tamanho declarado.' },
      { step: 'Conteúdo', detail: 'Os bytes lidos, crus, com data da leitura.' },
      { step: 'Interpretação', detail: 'O significado de cada campo — só quando houver evidência dele.' },
      { step: 'Cópia antes de tudo', detail: 'Leitura completa salva em disco antes de qualquer escrita ser oferecida.' },
    ]}
  >
    <Banner tone="error">
      <div className="font-medium mb-0.5">Quando isto existir, ele nasce somente-leitura</div>
      <div className="text-xs opacity-85 leading-relaxed">
        Escrever bloco de calibração errado não produz mensagem de erro: produz módulo que não
        responde mais. Qualquer escrita aqui depende de mapa de memória com evidência, de cópia
        íntegra salva antes, e de instrução explícita — nessa ordem.
      </div>
    </Banner>
  </ScaffoldScreen>
)
