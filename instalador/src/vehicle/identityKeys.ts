// Chaves do carro que descrevem o próprio carro.
//
// PROCEDÊNCIA: todos os nomes abaixo vêm do dump de ControlService de 2026-09-14 (as 818 chaves
// da Knowledge Base). A EXISTÊNCIA de cada chave é FACT. Um teste garante isso: nenhuma chave
// aqui pode deixar de existir no catálogo sem quebrar a verificação de `scripts/kb-build.mjs`.
//
// O QUE OS RÓTULOS SÃO: tradução do nome da chave, nada mais. `car.configure.transmission_type`
// vira "Tipo de transmissão" porque é isso que o nome diz — não porque alguém leu o valor e
// entendeu o que ele significa.
//
// O QUE OS RÓTULOS NÃO SÃO: decodificação. O valor `3` em `car.configure.energy_type` continua
// sendo `3` na tela. O app não tem a tabela que diz o que `3` quer dizer, então não inventa uma.
// Por isso todo grupo carrega `valueSemantics: 'INFERRED'` e a tela mostra o valor cru.

import type { Confidence } from '../data/knowledgeTypes'

export type IdentityKey = {
  key: string
  /** Tradução do nome da chave. Não é interpretação do valor. */
  label: string
}

export type IdentityGroup = {
  id: string
  title: string
  /** O que este grupo é — e o que ele não prova. */
  blurb: string
  /** Confiança na EXISTÊNCIA das chaves. */
  existence: Confidence
  /** Confiança no SIGNIFICADO dos valores. Nunca é FACT: nenhuma tabela de valores foi validada. */
  valueSemantics: Confidence
  keys: IdentityKey[]
}

export const IDENTITY_GROUPS: IdentityGroup[] = [
  {
    id: 'identificacao',
    title: 'Identificação',
    blurb: 'O que o próprio carro responde quando perguntado quem ele é.',
    existence: 'FACT',
    valueSemantics: 'INFERRED',
    keys: [
      { key: 'car.basic.vin_code', label: 'Chassi (VIN)' },
      { key: 'car.basic.vehicle_model1', label: 'Modelo (campo 1)' },
      { key: 'car.basic.vehicle_model2', label: 'Modelo (campo 2)' },
      { key: 'car.basic.configure_code', label: 'Código de configuração' },
      { key: 'car.configure.product_name', label: 'Nome do produto' },
      { key: 'car.configure.project_code', label: 'Código do projeto' },
    ],
  },
  {
    id: 'mercado',
    title: 'Mercado e destino',
    blurb: 'Região e normas para as quais o carro foi configurado de fábrica.',
    existence: 'FACT',
    valueSemantics: 'INFERRED',
    keys: [
      { key: 'car.configure.sales_area', label: 'Área de vendas' },
      { key: 'car.configure.regional', label: 'Região' },
      { key: 'car.configure.special_export_country', label: 'País de exportação especial' },
      { key: 'car.configure.emission_standards', label: 'Norma de emissões' },
      { key: 'car.configure.drive_hand_type', label: 'Lado da direção' },
      { key: 'car.configure.electrical_language', label: 'Idioma elétrico' },
      { key: 'car.configure.standard_unit', label: 'Sistema de unidades' },
      { key: 'car.configure.default_temp_unit', label: 'Unidade de temperatura padrão' },
    ],
  },
  {
    id: 'powertrain',
    title: 'Conjunto motriz',
    blurb: 'Como a fábrica declarou o trem de força deste carro.',
    existence: 'FACT',
    valueSemantics: 'INFERRED',
    keys: [
      { key: 'car.configure.energy_type', label: 'Tipo de energia' },
      { key: 'car.configure.power_type', label: 'Tipo de propulsão' },
      { key: 'car.configure.transmission_type', label: 'Tipo de transmissão' },
      { key: 'car.configure.engine_control_unit', label: 'Central do motor' },
      { key: 'car.configure.ev_drive_architecture', label: 'Arquitetura elétrica de tração' },
      { key: 'car.configure.starting_system', label: 'Sistema de partida' },
      { key: 'car.configure.booster_brake_type', label: 'Tipo de servo-freio' },
    ],
  },
  {
    id: 'versoes',
    title: 'Versões de software',
    blurb: 'Versões relatadas pela multimídia, pelo MCU e pelo módulo de conectividade.',
    existence: 'FACT',
    valueSemantics: 'INFERRED',
    keys: [
      { key: 'car.basic.vehicle_version', label: 'Versão do veículo' },
      { key: 'sys.basic.os_version', label: 'Versão do sistema' },
      { key: 'sys.basic.hw_version', label: 'Versão de hardware' },
      { key: 'sys.basic.mcu_version', label: 'Versão do MCU' },
      { key: 'sys.network.tbox.sofeware_version', label: 'Versão de software da T-Box' },
      { key: 'sys.network.tbox.hardware_version', label: 'Versão de hardware da T-Box' },
    ],
  },
  {
    id: 'equipamentos',
    title: 'Equipamentos declarados',
    blurb:
      'Itens que a configuração de fábrica declara como presentes. Declarado não é o mesmo que ' +
      'instalado e funcionando — isto não substitui uma inspeção.',
    existence: 'FACT',
    valueSemantics: 'INFERRED',
    keys: [
      { key: 'car.configure.cluster_type', label: 'Tipo de painel de instrumentos' },
      { key: 'car.configure.cockpit_combination_type', label: 'Combinação de cockpit' },
      { key: 'car.configure.hud', label: 'Head-up display' },
      { key: 'car.configure.omnivew_system', label: 'Câmera 360°' },
      { key: 'car.configure.recorder', label: 'Gravador de vídeo (DVR)' },
      { key: 'car.configure.tpms', label: 'Monitoramento de pressão dos pneus' },
      { key: 'car.configure.telematics', label: 'Telemática' },
      { key: 'car.configure.mood_lamp', label: 'Iluminação ambiente' },
      { key: 'car.configure.air_condition_type', label: 'Tipo de ar-condicionado' },
      { key: 'car.configure.seat_heating', label: 'Aquecimento dos bancos' },
    ],
  },
]

/** Resumo mostrado na tela de conexão: poucas linhas, sem rolagem. */
export const IDENTITY_SUMMARY_KEYS: IdentityKey[] = [
  { key: 'car.basic.vin_code', label: 'VIN' },
  { key: 'car.configure.product_name', label: 'Produto' },
  { key: 'car.basic.vehicle_model1', label: 'Modelo' },
  { key: 'car.basic.configure_code', label: 'Configuração' },
  { key: 'car.basic.vehicle_version', label: 'Versão do veículo' },
  { key: 'car.basic.total_odometer', label: 'Odômetro total' },
]

export const ALL_IDENTITY_KEYS: string[] = [
  ...new Set([
    ...IDENTITY_GROUPS.flatMap((g) => g.keys.map((k) => k.key)),
    ...IDENTITY_SUMMARY_KEYS.map((k) => k.key),
  ]),
]
