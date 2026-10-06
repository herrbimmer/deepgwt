// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Catálogo ADAS — extraído de `screens/AdasSettings.tsx` para que a tela não seja a dona dos
// dados. Outros módulos (fixture do modo demo, exploradores, relatórios) precisam desta lista
// sem importar uma tela inteira.
//
// Mesma lista (chaves, rótulos e grupos) da tela ADAS do DeepGWT do app do carro — ver
// AdasSettingsScreen.kt — pra manter os dois apps consistentes. O valor exato de cada chave
// nunca foi confirmado no carro real (só o padrão "0"/"1", já validado em AVAS/DMS/ESP).

export type AdasToggle = { key: string; title: string; description: string; group: string }

export const ADAS_GROUPS = [
  'Assistente de faixa',
  'Prevenção de colisão',
  'Piloto automático & rodovia',
  'Estacionamento assistido',
  'Outros avisos',
] as const

export const ADAS_TOGGLES: AdasToggle[] = [
  { key: 'car.intelligent_driving_setting.las.enable', title: 'Sistemas de assistência de faixa (LAS)', description: 'Chave geral dos recursos de faixa abaixo', group: 'Assistente de faixa' },
  { key: 'car.intelligent_driving_setting.las.lka_state', title: 'Manutenção de faixa (LKA)', description: 'Corrige a direção para manter o carro centralizado na faixa', group: 'Assistente de faixa' },
  { key: 'car.intelligent_driving_setting.las.ldw_state', title: 'Aviso de saída de faixa (LDW)', description: 'Alerta sonoro/visual ao sair da faixa sem sinalizar', group: 'Assistente de faixa' },
  { key: 'car.intelligent_driving_setting.las.lca_state', title: 'Assistente de troca de faixa (LCA)', description: 'Ajuda a manobrar o carro ao trocar de faixa', group: 'Assistente de faixa' },
  { key: 'car.intelligent_driving_setting.las.elk_state', title: 'Manutenção de faixa de emergência (ELK)', description: 'Correção mais brusca perto de barreiras ou faixa contínua', group: 'Assistente de faixa' },
  { key: 'car.intelligent_driving_setting.las.tsi_state', title: 'Reconhecimento de placas (TSI)', description: 'Exibe/alerta o limite de velocidade lido nas placas', group: 'Assistente de faixa' },
  { key: 'car.intelligent_driving_setting.eas.change_lane_assist_state', title: 'Assistente de mudança de faixa (EAS)', description: 'Auxílio ativo ao mudar de faixa em rodovia', group: 'Assistente de faixa' },

  { key: 'car.intelligent_driving_setting.fas.pcs_state', title: 'Frenagem de pré-colisão (PCS)', description: 'Freia automaticamente ao detectar risco de colisão frontal', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.fas.pps_state', title: 'Proteção de pedestres (PPS)', description: 'Detecção e frenagem para pedestres e ciclistas', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.fas.auto_emergency_turn', title: 'Desvio de emergência automático', description: 'Manobra evasiva automática em risco iminente de colisão', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.fas.front_cross_lateral_brake', title: 'Frenagem de cruzamento frontal', description: 'Freia para tráfego cruzando à frente em cruzamentos', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.fas.front_cross_lateral_wraning', title: 'Aviso de cruzamento frontal', description: 'Alerta de tráfego cruzando à frente em cruzamentos', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.fas.intersection_assist_state', title: 'Assistente de interseção', description: 'Auxílio em cruzamentos e conversões', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.sras.rcw_state', title: 'Aviso de colisão traseira (RCW)', description: 'Alerta de risco de colisão traseira', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.sras.rsa_state', title: 'Assistente de saída traseira (RSA)', description: 'Auxílio ao sair de ré de vagas/garagens', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.sras.ala_state', title: 'Alerta de área lateral (ALA)', description: 'Detecção de objetos/veículos nas laterais', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.sras.rsa_rsb_state', title: 'Frenagem de saída traseira (RSA/RSB)', description: 'Frenagem automática ao sair de ré com risco de colisão', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.sras.rsa_rsb_warning_state', title: 'Aviso de saída traseira (RSA/RSB)', description: 'Alerta ao sair de ré com risco de colisão', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.sras.door_open_warning', title: 'Aviso ao abrir a porta', description: 'Alerta de tráfego se aproximando ao abrir a porta', group: 'Prevenção de colisão' },
  { key: 'car.intelligent_driving_setting.smart_dodge_state', title: 'Desvio inteligente', description: 'Assistência de desvio inteligente de obstáculos', group: 'Prevenção de colisão' },

  { key: 'car.intelligent_driving_setting.over_speed_warning_state', title: 'Aviso de excesso de velocidade', description: 'Alerta ao ultrapassar o limite configurado', group: 'Piloto automático & rodovia' },
  { key: 'car.intelligent_driving_setting.noh_status', title: 'Piloto em rodovia (NOH)', description: 'Navegação assistida em rodovia conectada ao GPS', group: 'Piloto automático & rodovia' },
  { key: 'car.intelligent_driving_setting.noh_follow_navigation', title: 'NOH segue a navegação', description: 'O piloto de rodovia segue a rota traçada no GPS', group: 'Piloto automático & rodovia' },
  { key: 'car.intelligent_driving_setting.noh_auxiliary_navigation', title: 'NOH com navegação auxiliar', description: 'Usa dados de navegação auxiliares no piloto de rodovia', group: 'Piloto automático & rodovia' },
  { key: 'car.intelligent_driving_setting.vehicle_to_everything_enable', title: 'Comunicação V2X', description: 'Comunicação do veículo com infraestrutura e outros veículos', group: 'Piloto automático & rodovia' },
  { key: 'car.intelligent_driving_setting.wisdom_cornering_enable', title: 'Curva inteligente', description: 'Ajuste automático de velocidade/assistência em curvas', group: 'Piloto automático & rodovia' },

  { key: 'car.parking_setting.auto_park_enable', title: 'Estacionamento automático', description: 'Habilita a manobra automática para estacionar', group: 'Estacionamento assistido' },
  { key: 'car.parking_setting.auto_steering_enable', title: 'Direção automática ao estacionar', description: 'Controle automático do volante durante a manobra', group: 'Estacionamento assistido' },
  { key: 'car.parking_setting.apa.flanking_protection', title: 'Proteção lateral (APA)', description: 'Proteção contra colisão lateral durante manobras', group: 'Estacionamento assistido' },
  { key: 'car.parking_setting.fpas_enable', title: 'Assistência de estacionamento frontal (FPAS)', description: 'Assistência de manobra usando os sensores frontais', group: 'Estacionamento assistido' },
  { key: 'car.parking_setting.radar_alarm_sound_enable', title: 'Som dos sensores de estacionamento', description: 'Bipe sonoro dos sensores de proximidade', group: 'Estacionamento assistido' },
  { key: 'car.parking_setting.radar_setting.meb_state', title: 'Frenagem automática ao manobrar (MEB)', description: 'Freia automaticamente ao detectar obstáculo durante a manobra', group: 'Estacionamento assistido' },
  { key: 'car.parking_setting.avm_setting.avm_auto_active', title: 'Câmera 360° automática', description: 'Abre a câmera 360° automaticamente ao manobrar', group: 'Estacionamento assistido' },

  { key: 'car.intelligent_driving_setting.eas.warning_way', title: 'Forma de aviso do EAS', description: 'Alterna o tipo de alerta do assistente de faixa (valor a confirmar no carro)', group: 'Outros avisos' },
]

export const ADAS_TOGGLE_KEYS: readonly string[] = ADAS_TOGGLES.map((t) => t.key)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
