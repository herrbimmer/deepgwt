// Estado de validação de cada capacidade do app.
//
// Regra do projeto: nada é marcado como validado em veículo sem evidência. Este arquivo é a
// única fonte dessa afirmação, e cada entrada `vehicle-validated` precisa citar de onde veio.
// Tudo que não estiver aqui é tratado como `not-validated`.

export type ValidationStatus =
  /** Comportamento confirmado em um veículo real. Exige `evidence`. */
  | 'vehicle-validated'
  /** Implementado, mas nunca confirmado em um veículo real. */
  | 'not-validated'
  /** A estrutura existe; a capacidade em si ainda não foi implementada (falta transporte/dado). */
  | 'not-implemented'

export type CapabilityId = string

export type ValidationEntry = {
  status: ValidationStatus
  note: string
  /** Onde está a prova. Obrigatório para `vehicle-validated`. */
  evidence?: string
}

const DEFAULT: ValidationEntry = {
  status: 'not-validated',
  note: 'Implementado, mas ainda não confirmado em um veículo real.',
}

export const VALIDATION: Record<CapabilityId, ValidationEntry> = {
  // As três únicas capacidades com confirmação registrada no repositório: o padrão de escrita
  // "0"/"1" foi observado funcionando nestas chaves. Ver o comentário em screens/AjustesAvancados
  // e data/adasCatalog.ts ("só o padrão 0/1 já validado em AVAS/DMS/ESP").
  'quick.avas': {
    status: 'vehicle-validated',
    note: 'Escrita "0"/"1" confirmada no veículo.',
    evidence: 'data/adasCatalog.ts (cabeçalho) — padrão 0/1 validado em AVAS',
  },
  'quick.dms': {
    status: 'vehicle-validated',
    note: 'Escrita "0"/"1" confirmada no veículo.',
    evidence: 'data/adasCatalog.ts (cabeçalho) — padrão 0/1 validado em DMS',
  },
  'coding.esp': {
    status: 'vehicle-validated',
    note: 'Escrita "0"/"1" confirmada no veículo.',
    evidence: 'data/adasCatalog.ts (cabeçalho) — padrão 0/1 validado em ESP',
  },

  'adas.toggles': {
    status: 'not-validated',
    note: 'As chaves existem no dicionário do carro, mas o efeito de cada uma nunca foi confirmado.',
  },
  'coding.curated': {
    status: 'not-validated',
    note: 'Chaves confirmadas como existentes; o efeito de escrever cada uma não foi testado.',
  },
  'coding.raw': {
    status: 'not-validated',
    note: 'Edição livre de qualquer chave. Sem rede de proteção além do ponto de restauração.',
  },
  'vehicle.monitor': {
    status: 'vehicle-validated',
    note:
      'Confirmado pelo dono no carro: a tela inteira (velocidade, marcha, modo, volante, energia, ' +
      'ignição e luzes) mostra o que o painel do veículo mostra. As baterias já tinham confirmação ' +
      'cruzada com o VHAL.',
    evidence:
      'Confirmação do dono em 30/09/2026, com o carro, na Alpha 9.4 ("funciona perfeitamente"); ' +
      'knowledge/session_2026-09-15/raw_http/telemetry.json e POWERTRAIN_AND_HV_SOH.md',
  },
  'vehicle.identity': {
    status: 'not-validated',
    note:
      'A decodificação do VIN é algoritmo publicado e não depende de veículo. Já o significado ' +
      'dos códigos de configuração lidos do carro nunca foi validado — a tela mostra o valor cru.',
  },
  'coding.guided': {
    status: 'not-validated',
    note:
      'As funções listadas existem no dicionário do carro. O efeito de ligar ou desligar cada ' +
      'uma nunca foi confirmado em um veículo — e as opções só aparecem quando o próprio carro ' +
      'respondeu "0" ou "1", porque o app não tem a tabela de valores aceitos.',
  },
  'changes.log': {
    status: 'not-validated',
    note:
      'O histórico registra o que o app pediu e o que o carro respondeu. Uma escrita marcada ' +
      'como gravada significa que a chamada teve sucesso, não que o carro passou a se comportar ' +
      'de outra forma — isso ninguém verificou.',
  },
  'privacy.center': {
    status: 'not-validated',
    note:
      'Cada item da tela cita o arquivo e a linha que o sustenta. É o resultado de uma leitura ' +
      'do código nesta versão, não uma auditoria de rede feita com o app rodando.',
  },
  'pro.scan': {
    status: 'not-validated',
    note:
      'A varredura compara o que o carro respondeu contra o dicionário de 818 chaves levantado ' +
      'em 2026-09-14. Ela mede cobertura de leitura — não enumera módulos, não lê DTC e não ' +
      'toca no barramento. Chave que o carro responda fora do levantamento entra como descoberta ' +
      'a validar, nunca como fato.',
  },
  'pro.live-data': {
    status: 'not-validated',
    note:
      'Os valores são os que o carro devolve, transcritos crus: o app não conhece a unidade, a ' +
      'escala nem a faixa válida de nenhuma chave, e por isso não converte nem julga nada. Há duas ' +
      'fontes, e elas falham de formas diferentes — o snapshot do ControlService depende da ponte ' +
      'do Shizuku, que caiu duas vezes na sessão de 2026-09-15; o registro de eventos do /diag ' +
      'continuou entregando dado nas mesmas horas, mas só mostra chave que mudou.',
    evidence: 'knowledge/session_2026-09-15/UNBLOCKS.md — U-01',
  },
  'pro.configuration': {
    status: 'not-validated',
    note:
      'A codificação de variante desta unidade são 546 flags, das quais 154 estão ligadas — ' +
      'proporção que mostra uma tabela de equipamento real, não um default vazio. Presença e ' +
      'ausência são fato lido do carro. O que cada número significa (por que "5" e não "1") não ' +
      'está no projeto e não foi deduzido: o valor aparece como veio.',
    evidence: 'knowledge/coding/factory.json — getprop persist.vendor.gwm.cfg.* (2026-09-15)',
  },
  'pro.module-info': {
    status: 'not-validated',
    note:
      'São as versões que a multimídia informa sobre si mesma, o MCU e a T-Box. Não é informação ' +
      'de ECU: número de peça, calibração e software de módulo exigem UDS, que o app não tem.',
  },
  'pro.odometer': {
    status: 'not-validated',
    note:
      'Compara as chaves cujo NOME indica quilometragem. Todas vêm da mesma interface, então isto ' +
      'não é — e não pode ser — uma verificação de adulteração, que exigiria ler painel, ABS e ' +
      'gateway de forma independente. Divergência é relatada como divergência, nunca como fraude.',
  },
  'pro.report': {
    status: 'not-validated',
    note:
      'O relatório transcreve o que foi lido nesta sessão e lista o que não foi coletado. Ele não ' +
      'emite veredito: não existe aprovado, reprovado nem suspeito em lugar nenhum do documento.',
  },
  'pro.history': {
    status: 'not-validated',
    note:
      'Registro de serviço montado a partir do que este aplicativo escreveu. Alterações feitas ' +
      'por qualquer outra ferramenta, ou pela própria tela do carro, não aparecem aqui — o app ' +
      'só enxerga os próprios eventos.',
  },
  'health.overview': {
    status: 'not-validated',
    note: 'A semântica dos valores de aviso não está documentada — a tela mostra o valor cru, sem veredito.',
  },

  // Capacidades cuja estrutura existe, mas cujo transporte ainda não existe no app.
  'diag.dtc': {
    status: 'not-implemented',
    note:
      'O veículo tem o caminho: o head unit roda um cliente UDS completo e é tester confiável na ' +
      'rede interna, então lê DTC sem sessão 0x10, sem 0x27 e sem a autenticação do gateway ENET. ' +
      'O que falta é do lado do app — bindar o serviço de diagnóstico pelo Shizuku. E isso é ' +
      'operação ativa: abre um canal de report no carro, então depende de autorização explícita, ' +
      'não de uma decisão do aplicativo.',
    evidence: 'knowledge/session_2026-09-15/DISCOVERY_LOG_2.md — D2-08 (métodos extraídos das vdex)',
  },
  'pro.diagnostics': {
    status: 'not-validated',
    note:
      'O relatório /diag da porta 8766 carrega, além do estado da ponte, um registro de eventos ' +
      'do próprio veículo: as mudanças de chave que o ControlService emitiu, com horário. Isso é ' +
      'telemetria do carro, e sobrevive à queda do binder do Shizuku — foi o que aconteceu duas ' +
      'vezes na sessão de 2026-09-15, com o snapshot devolvendo vazio e o registro continuando a ' +
      'entregar dado. O que a tela mostra são valores crus, sem unidade nem escala conhecidas.',
    evidence:
      'knowledge/session_2026-09-15/UNBLOCKS.md — U-01; série em knowledge/signals/trace.json',
  },
  'diag.ecu-inventory': {
    status: 'not-validated',
    note:
      'A enumeração existe e não custou nada ao veículo: a tabela ARP estática do head unit lista ' +
      '16 nós na rede automotiva, com endereço e VLAN de cada um. Nenhum frame de descoberta foi ' +
      'transmitido para obtê-la. O limite é a identidade: só dois nós têm papel conhecido por ' +
      'evidência — o gateway, que se identificou no DoIP, e o próprio head unit. Os outros 14 são ' +
      'endereços que existem e ninguém sabe o que são; descobrir exige UDS, que é operação ativa.',
    evidence: 'knowledge/network/topology.json — derivado de ip addr + ip neigh (2026-09-15)',
  },
  'diag.error-tree': {
    status: 'not-implemented',
    note:
      'Depende da leitura de DTC, que hoje aguarda autorização. E depende de uma segunda coisa que ' +
      'não temos: a tabela que traduz código em significado. O app de engenharia de fábrica que a ' +
      'tem está empacotado e ofuscado, e nada foi extraível dele. A árvore fica vazia — inventar ' +
      'causa provável para um código seria a pior forma de preencher esta tela.',
    evidence: 'knowledge/session_2026-09-15/DTC_AVAS_EQ_FINDINGS.md — seção 1 (EnginMode.apk ofuscado)',
  },
  'expert.vhal': {
    status: 'not-validated',
    note:
      'Navegar o catálogo das 1602 propriedades funciona offline e não precisa de carro: a ' +
      'existência de cada propriedade é fato do dump de 2026-09-14. Os valores vêm de duas ' +
      'capturas, e a tela diz de qual: 518 sinais em 2026-09-14 e 870 na leitura de 2026-09-15, ' +
      'esta com contagem de eventos — que é o que distingue um sinal contínuo de um que dispara ' +
      'uma única vez ao ligar. O que continua não existindo é leitura sob demanda: o app não tem ' +
      'ponte para o CarPropertyManager, então não há como pedir o valor de agora.',
  },
  'expert.controlservice': {
    status: 'not-validated',
    note:
      'Navegar as 818 chaves funciona offline. Quando há sessão com o carro, o valor atual de cada ' +
      'chave aparece ao lado — esse valor é leitura desta sessão, e o significado dele continua ' +
      'deduzido do nome.',
  },
  'expert.discovery': {
    status: 'not-validated',
    note:
      'Consulta uma chave por vez contra o levantamento e contra o que o carro respondeu. Chave ' +
      'que o carro responda fora do levantamento é registrada como descoberta a validar; chave que ' +
      'ninguém viu é registrada como nunca vista, jamais como inexistente.',
  },
  'expert.knowledge': {
    status: 'not-validated',
    note:
      'Navegador da base de conhecimento. Mostra o que cada camada afirma, com que método, em que ' +
      'data e com que nível de confiança — inclusive as camadas que estão vazias.',
  },
  'expert.uds': {
    status: 'not-implemented',
    note:
      'O app não fala UDS. O carro fala: há um stack completo no head unit (HALs IUds, IUdsClient, ' +
      'IUdsServer) e o DoIP responde na porta 13400. Os serviços implementados foram lidos do ' +
      'próprio binário. Já o acesso direto de fora, pelo adaptador ENET, está fechado: o gateway ' +
      'aceita só o tester 0x0E80 e recusa o roteamento por falta de autenticação.',
    evidence: 'knowledge/session_2026-09-15/DISCOVERY_LOG.md — D-12; DISCOVERY_LOG_2.md — D2-07',
  },
  'expert.dids': {
    status: 'not-implemented',
    note:
      'Depende de UDS. Os DIDs padrão (número de peça, série, versões) existem em qualquer ECU do ' +
      'mercado e seriam o caminho para dar nome aos 14 nós anônimos da rede — mas lê-los é operação ' +
      'ativa. Os DIDs proprietários da GWM, que é onde mora a saúde do pacote de alta tensão, não ' +
      'estão documentados em lugar nenhum a que tenhamos acesso.',
    evidence: 'knowledge/session_2026-09-15/MORE_ECUS_AVAS_HVBATTERY.md — S3-05',
  },
  'expert.network': {
    status: 'not-validated',
    note:
      'A rede do veículo é Ethernet automotiva, não CAN: seis VLANs, todas ativas, com o head unit ' +
      'presente em cada uma e 16 nós de endereçamento estático. Tudo isso foi lido da configuração ' +
      'do próprio head unit, sem sondar ninguém. CAN continua fora de alcance — os utilitários ' +
      'existem na imagem, mas não há interface de barramento exposta, são resquício da plataforma ' +
      'Qualcomm. O que cada nó é permanece desconhecido.',
    evidence: 'knowledge/network/topology.json; knowledge/session_2026-09-15/DISCOVERY_LOG.md — D-08, D-13',
  },
  'expert.ecu-info': {
    status: 'not-implemented',
    note:
      'Número de peça, fornecedor, versões e calibração por módulo exigem UDS. O que existe hoje é ' +
      'mais fraco e vale dizer o que é: os prefixos dos nomes de sinal agrupam as propriedades por ' +
      'módulo de origem, e a codificação de fábrica declara o que esta unidade tem. Nenhum dos dois ' +
      'é identificação de ECU.',
    evidence: 'knowledge/session_2026-09-15/MORE_ECUS_AVAS_HVBATTERY.md — S3-03',
  },
  'expert.strings': {
    status: 'not-implemented',
    note:
      'Nenhuma tabela de strings do veículo foi levantada. A pasta strings/ da base de ' +
      'conhecimento está vazia, e transcrever aqui os textos do próprio aplicativo seria passar ' +
      'uma coisa por outra.',
  },
  'expert.parameters': {
    status: 'not-implemented',
    note:
      'Parâmetro de ECU (adaptação, aprendizado, valor de calibração) exige UDS. O que o app tem ' +
      'são as listas de chaves que ele próprio mantém à mão — úteis, e outra coisa.',
  },
  'expert.calibration': {
    status: 'not-implemented',
    note:
      'Bloco de calibração e área de dados de módulo exigem UDS e, para a maioria dos blocos, ' +
      'autenticação por semente e chave. Nada disso existe no app.',
  },
}

export function validationOf(id: CapabilityId | undefined): ValidationEntry {
  if (!id) return DEFAULT
  return VALIDATION[id] ?? DEFAULT
}

export const VALIDATION_LABEL: Record<ValidationStatus, string> = {
  'vehicle-validated': 'Validado em veículo',
  'not-validated': 'Ainda não validado em veículo',
  'not-implemented': 'Ainda não disponível',
}
