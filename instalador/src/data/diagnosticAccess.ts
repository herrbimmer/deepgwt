// Como se chega aos dados de diagnóstico deste veículo — e onde cada caminho para.
//
// Este arquivo existe porque uma frase do app estava errada. Até a sessão de 2026-09-15, oito
// telas afirmavam "não existe transporte de diagnóstico". Isso era verdade sobre o APLICATIVO e
// falso sobre o CARRO: o veículo tem DoIP e UDS completos, e o head unit é um tester confiável
// dentro da rede de diagnóstico. A diferença não é cosmética — "não existe" encerra a
// investigação, "existe e o app não usa" abre uma tarefa.
//
// Cada item abaixo cita o arquivo de evidência em `knowledge/session_2026-09-15/`. Nada aqui foi
// executado contra o veículo: o mapa foi levantado por leitura (telnet root, dumpsys, strings das
// vdex, getprop) e, no caso do gateway ENET, por um handshake que o próprio gateway recusou.

import type { Confidence } from './knowledgeTypes'

const S = 'knowledge/session_2026-09-15'

/**
 * O que o app precisa fazer para usar o caminho.
 *
 * - `available`   já dá para usar com o que existe no app hoje
 * - `needs-app`   o carro entrega; falta código no app (não depende de autorização do veículo)
 * - `needs-consent` funciona, mas sai do modo só-leitura — precisa de autorização explícita
 * - `blocked`     o veículo recusa, e não é questão de implementar
 */
export type PathReadiness = 'available' | 'needs-app' | 'needs-consent' | 'blocked'

export type AccessLayer = {
  id: 'controlservice' | 'vhal' | 'headunit-uds' | 'enet-doip'
  name: string
  transport: string
  /** O que esta camada enxerga — e de que ângulo. */
  sees: string
  readiness: PathReadiness
  confidence: Confidence
  /** Por que o estado é esse. Uma frase, sem rodeio. */
  state: string
  evidence: string
}

export const ACCESS_LAYERS: AccessLayer[] = [
  {
    id: 'controlservice',
    name: 'ControlService',
    transport: 'HTTP 8766 (GwLink/Shizuku) e logcat do IntelligentVehicleControlService',
    sees:
      'As 818 chaves que a multimídia usa para desenhar a própria tela. É o carro narrando o ' +
      'painel, não a ECU falando.',
    readiness: 'available',
    confidence: 'FACT',
    state:
      '817 das 818 chaves responderam. O logcat é uma segunda via da mesma informação e sobrevive ' +
      'à queda do binder do Shizuku, que derrubou o snapshot HTTP duas vezes nesta sessão.',
    evidence: `${S}/DISCOVERY_LOG.md — D-02, D-03; ${S}/POWERTRAIN_AND_HV_SOH.md — adendo da sessão 6`,
  },
  {
    id: 'vhal',
    name: 'VHAL (domain controller Bosch)',
    transport: 'dumpsys car_service via telnet root',
    sees:
      '1602 propriedades de carroceria, chassi, ADAS e gateway, com id, tipo, acesso e contagem ' +
      'de eventos. Não carrega os módulos de tração.',
    readiness: 'available',
    confidence: 'FACT',
    state:
      '870 sinais responderam com valor na captura de 2026-09-15. Nenhum prefixo de powertrain ' +
      '(EMS, HCU, MCU, BMS, OBC, DCDC, VCU) existe no catálogo — esses sete módulos não passam por aqui.',
    evidence: `${S}/POWERTRAIN_AND_HV_SOH.md — seção 1; ${S}/raw_telnet5/10_vhal_full.txt`,
  },
  {
    id: 'headunit-uds',
    name: 'UDS pela head unit',
    transport: 'binder interno (ts.car.diag → DtcManager → UdsClientProxy), via Shizuku',
    sees:
      'DTC e DID de qualquer ECU, lidos pelo cliente UDS que já roda dentro do carro. É a ECU ' +
      'falando, em primeira mão.',
    readiness: 'needs-consent',
    confidence: 'REQUIRES_VALIDATION',
    state:
      'O caminho existe e está mapeado método a método; quem autentica no gateway é o próprio ' +
      'carro, então não exige sessão 0x10 nem seed→key. Invocá-lo é operação ativa — o app abre ' +
      'um canal de report no veículo — e por isso nunca foi executado.',
    evidence: `${S}/DISCOVERY_LOG_2.md — D2-08; ${S}/DISCOVERY_LOG.md — D-12`,
  },
  {
    id: 'enet-doip',
    name: 'ENET / DoIP direto',
    transport: 'Ethernet OBD2 → gateway 169.254.15.118, endereço lógico 0x1000',
    sees: 'UDS endereçado a cada ECU, sem intermediário.',
    readiness: 'blocked',
    confidence: 'FACT',
    state:
      'O gateway aceita apenas o tester 0x0E80 e responde 0x04 (missing authentication) à ativação ' +
      'de roteamento. O tipo 0x01 (WWH-OBD, o caminho legislado de emissões) volta 0x06, não ' +
      'suportado. Sem a autenticação da GWM não passa — e tentar 0x27 às cegas arrisca travar o módulo.',
    evidence: `${S}/DISCOVERY_LOG_2.md — D2-07`,
  },
]

export const LAYER_BY_ID = new Map(ACCESS_LAYERS.map((l) => [l.id, l]))

/** Serviços UDS que o cliente de dentro do carro implementa, extraídos do DEX do UdsService. */
export const UDS_SERVICES: { sid: string; name: string; method: string; kind: 'read' | 'write' }[] = [
  { sid: '0x19', name: 'ReadDTCInformation', method: 'requestReadDtcInformation', kind: 'read' },
  { sid: '0x22', name: 'ReadDataByIdentifier', method: 'UdsClientProxy (DID)', kind: 'read' },
  { sid: '0x10', name: 'DiagnosticSessionControl', method: 'switchDiagnosticSessionMode', kind: 'write' },
  { sid: '0x27', name: 'SecurityAccess', method: 'requestSecurityAccess', kind: 'write' },
  { sid: '0x31', name: 'RoutineControl', method: 'requestRoutineControl', kind: 'write' },
  { sid: '0x36', name: 'TransferData', method: 'requestTransferData', kind: 'write' },
  { sid: '0x85', name: 'ControlDTCSetting', method: 'requestControlDtcSetting', kind: 'write' },
]

export const UDS_EVIDENCE = `${S}/DISCOVERY_LOG.md — D-12 (HALs vendor.autolink.uds@1.0 e tipos extraídos do DEX)`

/**
 * A única fatia de DTC legível hoje sem bindar nada: o amplificador e os microfones do ANC
 * publicam o próprio DTC como propriedade VHAL de leitura.
 *
 * Com uma ressalva que decide o uso: as propriedades só se preenchem depois que alguém escreve o
 * gatilho `HUT3_AMP_GETDTCINFOREQ`. Na captura desta sessão elas estavam vazias — o gatilho é
 * escrita, e escrita está fora do modo só-leitura.
 */
export const VHAL_DTC_PROPERTIES = {
  readable: [
    { range: '0x21405366–0x2140536d', name: 'AMP1_AMP_CHAN1..8DTCINFO', what: 'DTC dos 8 canais do amplificador' },
    { range: '0x21405385–0x21405388', name: 'AMP4_ANC1_MICROPHONE_CHA1..4DTCINFO', what: 'DTC dos microfones do ANC' },
  ],
  trigger: { id: '0x21405262', name: 'HUT3_AMP_GETDTCINFOREQ', what: 'gatilho de escrita — faz o amplificador reportar' },
  state: 'Lidas em 2026-09-15: todas vazias, porque o gatilho nunca foi escrito.',
  evidence: `${S}/DTC_AVAS_EQ_FINDINGS.md — seção 1; ${S}/DISCOVERY_LOG_2.md — D2-08`,
}

/** Serviços SOME/IP declarados pelo SomeIpService, sobre a Ethernet automotiva. */
export const SOMEIP_SERVICES = [
  { id: 'TBOX_SYSTEM / TELEPHONY / TSP_COM', role: 'Telemática e conectividade com a nuvem' },
  { id: 'TBOX_V2X_SYSTEM_FUNCTIONS', role: 'V2X' },
  { id: 'TBOX_RESERVATION_FUNCTIONS', role: 'Agendamento de climatização e de carga' },
  { id: 'IDC_L3_SYSTEM_FUNCTION / STATUS_REPORT', role: 'Condução autônoma nível 3' },
  { id: 'AVP_SYSTEM_FUNCTIONS', role: 'Estacionamento autônomo (valet)' },
  { id: 'INTELLIGENT_RECOGNITION_MODULE', role: 'Reconhecimento por câmera' },
  { id: 'DVR_FILE_OPERATION / STATUS_REPORT / SYSTEM_CONFIG', role: 'DVR — listar, apagar, formatar, configurar' },
]

export const SOMEIP_EVIDENCE = `${S}/DISCOVERY_LOG_2.md — D2-03 (catálogo extraído do classes.dex do SomeIpService)`

/** Portas de diagnóstico abertas, com o processo dono de cada socket. */
export const DIAG_PORTS = [
  { port: 'udp/13400', protocol: 'DoIP (ISO 13400)', owner: 'vendor.autolink.uds@1.0-service' },
  { port: 'udp/30490', protocol: 'SOME/IP-SD', owner: 'vendor.ts.someip@1.0-service' },
  { port: 'tcp/23', protocol: 'telnet (shell root)', owner: 'head unit — foi por aqui que tudo isto foi lido' },
  { port: 'tcp/8766', protocol: 'GwLinkServer (HTTP)', owner: 'ponte do Shizuku — instável nesta sessão' },
  { port: 'tcp/5555', protocol: 'ADB over TCP', owner: 'não autorizado: exige aceitar o diálogo na tela do carro' },
]

export const DIAG_PORTS_EVIDENCE = `${S}/DISCOVERY_LOG.md — D-01, D-10, D-11`
