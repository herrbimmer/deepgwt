import { TerminalSquareIcon } from 'lucide-react'
import { Banner, ConfidenceChip, ListPanel, ListRow, ScaffoldScreen } from '../../ui'
import { UDS_SERVICES, UDS_EVIDENCE } from '../../data/diagnosticAccess'

// Console UDS — o app não fala UDS; o carro fala.
//
// A tabela grande é do PADRÃO ISO 14229 e continua sendo só isso: o que uma ferramenta de
// diagnóstico precisaria saber falar. O que mudou em 2026-09-15 é a segunda tabela — os serviços
// que o cliente UDS de DENTRO do carro implementa, lidos do binário dele. Essa lista não é padrão,
// é deste veículo.
//
// E há duas portas para o mesmo protocolo, com destinos opostos. Pelo conector ENET, o gateway
// recusa: só aceita o tester 0x0E80 e responde "falta autenticação" à ativação de roteamento — sem
// o segredo da GWM não passa, e insistir com 0x27 às cegas arrisca travar o módulo. Por dentro, o
// head unit já é tester confiável e não precisa de nada disso.
//
// Continua sem caixa de texto, e a razão continua boa: um console que envia é operação ativa.
const SERVICOS: Array<{ sid: string; nome: string; oQueFaz: string }> = [
  { sid: '0x10', nome: 'DiagnosticSessionControl', oQueFaz: 'Abre uma sessão de diagnóstico; quase tudo depende dela.' },
  { sid: '0x11', nome: 'ECUReset', oQueFaz: 'Reinicia o módulo.' },
  { sid: '0x14', nome: 'ClearDiagnosticInformation', oQueFaz: 'Apaga falhas memorizadas.' },
  { sid: '0x19', nome: 'ReadDTCInformation', oQueFaz: 'Lê falhas, estado e dados congelados.' },
  { sid: '0x22', nome: 'ReadDataByIdentifier', oQueFaz: 'Lê um identificador de dado (DID).' },
  { sid: '0x23', nome: 'ReadMemoryByAddress', oQueFaz: 'Lê memória por endereço.' },
  { sid: '0x27', nome: 'SecurityAccess', oQueFaz: 'Semente e chave. Depende de um segredo do fabricante.' },
  { sid: '0x28', nome: 'CommunicationControl', oQueFaz: 'Liga e desliga comunicação do módulo no barramento.' },
  { sid: '0x2E', nome: 'WriteDataByIdentifier', oQueFaz: 'Escreve em um identificador de dado.' },
  { sid: '0x2F', nome: 'InputOutputControlByIdentifier', oQueFaz: 'Assume o controle de uma entrada ou saída.' },
  { sid: '0x31', nome: 'RoutineControl', oQueFaz: 'Inicia, para e consulta uma rotina do módulo.' },
  { sid: '0x34 / 0x36 / 0x37', nome: 'RequestDownload / TransferData / TransferExit', oQueFaz: 'Transferência de bloco — é por aqui que se regrava um módulo.' },
  { sid: '0x3E', nome: 'TesterPresent', oQueFaz: 'Mantém a sessão viva.' },
  { sid: '0x85', nome: 'ControlDTCSetting', oQueFaz: 'Suspende o registro de falhas durante um teste.' },
]

export const ExpertUdsConsole = () => (
  <ScaffoldScreen
    icon={TerminalSquareIcon}
    title="Console UDS"
    intro="Enviar requisição de diagnóstico a um módulo e ler a resposta crua."
    capability="expert.uds"
    blockers={[
      {
        what: 'O aplicativo não fala UDS — o carro fala',
        why: 'O app tem shell por telnet e a API HTTP na porta 8766. Nenhum dos dois é UDS. Mas o head unit roda um cliente UDS completo, já autenticado na rede interna: o caminho existe e passa por dentro, não pelo conector.',
      },
      {
        what: 'Pelo conector ENET, o gateway recusa roteamento',
        why: 'Testado em 2026-09-15: só o tester 0x0E80 é aceito, e a ativação volta com "falta autenticação". O tipo de ativação legislado para emissões volta "não suportado". Sem o segredo da GWM, o acesso direto não abre.',
      },
      {
        what: 'Enviar requisição é operação ativa',
        why: 'Mesmo um serviço de leitura como 0x19 ou 0x22 abre diálogo com um módulo. Isso sai do modo só-leitura que rege o projeto e depende de autorização explícita — não de um botão nesta tela.',
      },
      {
        what: 'Um console que não envia não é um console',
        why: 'Uma caixa de texto com um botão desligado passaria a impressão de capacidade que não existe. Por isso não há caixa de texto nesta tela.',
      },
    ]}
    structure={[
      { step: 'Destino', detail: 'Módulo e endereço de requisição e resposta.' },
      { step: 'Requisição', detail: 'Serviço, sub-função e dados, em hexadecimal.' },
      { step: 'Resposta crua', detail: 'Os bytes como vieram, antes de qualquer interpretação.' },
      { step: 'Leitura da resposta', detail: 'Interpretação só quando houver evidência do formato; caso contrário, os bytes e nada mais.' },
      { step: 'Registro', detail: 'Toda requisição enviada gravada no histórico, com data e resultado.' },
    ]}
  >
    <section className="mb-5">
      <div className="flex flex-wrap items-center gap-2 mb-1">
        <h2 className="text-sm font-semibold text-text">O que o cliente UDS deste carro implementa</h2>
        <ConfidenceChip level="FACT" />
      </div>
      <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">
        Extraído do binário do serviço que roda na multimídia — não é o padrão, é esta unidade. As
        flags de autenticação do caminho interno estão em zero, o que significa que a leitura por
        aqui não exigiria sessão elevada nem semente e chave.
      </p>
      <ListPanel maxHeight="max-h-60">
        {UDS_SERVICES.map((s) => (
          <ListRow key={s.sid}>
            <span className="font-mono text-[11px] text-text-secondary flex-shrink-0 w-12">{s.sid}</span>
            <div className="flex-1 min-w-0">
              <div className="text-xs text-text truncate">{s.name}</div>
              <div className="font-mono text-[10px] text-text-tertiary truncate">{s.method}</div>
            </div>
            <span
              className={`text-[10px] px-1.5 py-0.5 rounded flex-shrink-0 ${
                s.kind === 'read' ? 'bg-success/15 text-success' : 'bg-danger/15 text-danger'
              }`}
            >
              {s.kind === 'read' ? 'leitura' : 'escrita'}
            </span>
          </ListRow>
        ))}
      </ListPanel>
      <p className="text-[10px] text-text-tertiary mt-2 font-mono break-all">{UDS_EVIDENCE}</p>
    </section>

    <section className="mb-2">
      <h2 className="text-sm font-semibold text-text mb-1">Os serviços do padrão ISO 14229</h2>
      <p className="text-[11px] text-text-tertiary mb-2 leading-relaxed">
        Esta lista é do padrão, não deste carro. Ela descreve o que uma ferramenta de diagnóstico
        precisaria saber falar. O que os módulos deste veículo aceitam ninguém verificou — e é por
        isso que a coluna da direita diz desconhecido em todas as linhas.
      </p>
      <ListPanel maxHeight="max-h-80">
        {SERVICOS.map((s) => (
          <ListRow key={s.sid}>
            <span className="font-mono text-[11px] text-text-secondary w-28 flex-shrink-0">{s.sid}</span>
            <div className="flex-1 min-w-0">
              <div className="text-[11px] text-text truncate">{s.nome}</div>
              <div className="text-[10px] text-text-tertiary leading-snug">{s.oQueFaz}</div>
            </div>
            <ConfidenceChip level="UNKNOWN" />
          </ListRow>
        ))}
      </ListPanel>
    </section>

    <Banner tone="warning" className="mb-2">
      <div className="text-xs leading-relaxed">
        Nenhuma linha acima é capacidade do OPEN GW. O app não envia nenhum destes serviços, e a
        tabela está aqui como referência do que falta, não do que existe.
      </div>
    </Banner>
  </ScaffoldScreen>
)
