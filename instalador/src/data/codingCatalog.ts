// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
export type CodingRisk = 'baixo' | 'medio' | 'alto'

export type CodingCatalogEntry = {
  key: string
  name: string
  description: string
  category: string
  risk: CodingRisk
}

// Curadoria das chaves car.configure.* (variant coding de fábrica: o que o carro TEM/FAZ) mais
// relevantes, levantada em 2026-09-14 varrendo as 818 chaves do dicionário ControlService deste
// carro contra o que o app já expõe (201 usadas / 617 ocultas). Cada entrada aqui é uma chave
// confirmada como oculta (nunca exposta pela UI de fábrica) e potencialmente interessante.
//
// Isto é o modo "curado" (estilo BimmerLink: nome amigável, descrição, risco) — o modo "avançado"
// em Coding.tsx continua expondo as 818 chaves cruas para quem quiser ir além desta lista.
export const CODING_CATALOG: CodingCatalogEntry[] = [
  // Iluminação / Ambiente
  { key: 'car.configure.mood_lamp', name: 'Mood lamp (luz ambiente)', description: 'Habilita a luz ambiente interna.', category: 'Iluminação / Ambiente', risk: 'baixo' },
  { key: 'car.configure.mood_lamp_partition', name: 'Zonas da luz ambiente', description: 'Ativa controle por zonas (multi-região).', category: 'Iluminação / Ambiente', risk: 'baixo' },
  { key: 'car.configure.audio_parsing_multicolor_rhythm_mood_lamp', name: 'Luz ambiente reativa ao som', description: 'Pulsa a luz no ritmo do áudio.', category: 'Iluminação / Ambiente', risk: 'baixo' },
  { key: 'car.configure.adaptive_front_lighting_system', name: 'AFS — farol adaptativo', description: 'Farol que acompanha a curva. Só funciona se o carro tiver o hardware correspondente.', category: 'Iluminação / Ambiente', risk: 'medio' },
  { key: 'car.configure.led_ultra_long_assist_high_beam', name: 'Assistente de farol alto LED', description: 'Alto auxiliar de longo alcance. Depende de hardware.', category: 'Iluminação / Ambiente', risk: 'medio' },
  { key: 'car.configure.auto_headlight', name: 'Farol automático', description: 'Liga o farol por sensor de luz.', category: 'Iluminação / Ambiente', risk: 'baixo' },
  { key: 'car.configure.head_lamp_auto_off', name: 'Desliga farol automático', description: 'Apaga o farol ao sair do carro.', category: 'Iluminação / Ambiente', risk: 'baixo' },

  // Conforto / Bancos / Clima
  { key: 'car.configure.front_seat_cooling_and_massage', name: 'Ventilação + massagem dianteira', description: 'Habilita ventilação/massagem dos bancos da frente. Só funciona se o carro tiver o hardware.', category: 'Conforto / Bancos / Clima', risk: 'medio' },
  { key: 'car.configure.second_seat_cooling_and_massage', name: 'Ventilação/massagem traseira', description: 'Idem para os bancos traseiros. Depende de hardware.', category: 'Conforto / Bancos / Clima', risk: 'medio' },
  { key: 'car.configure.seat_heating', name: 'Aquecimento de banco', description: 'Habilita bancos aquecidos. Depende de hardware.', category: 'Conforto / Bancos / Clima', risk: 'medio' },
  { key: 'car.configure.steer_wheel_heating', name: 'Volante aquecido', description: 'Habilita o aquecimento do volante. Depende de hardware.', category: 'Conforto / Bancos / Clima', risk: 'medio' },
  { key: 'car.configure.driver_waist_supporter', name: 'Apoio lombar do motorista', description: 'Habilita o ajuste lombar elétrico.', category: 'Conforto / Bancos / Clima', risk: 'baixo' },
  { key: 'car.configure.fragrance_system', name: 'Sistema de fragrância', description: 'Habilita o difusor de aroma. Depende de hardware.', category: 'Conforto / Bancos / Clima', risk: 'medio' },
  { key: 'car.configure.air_purifier', name: 'Purificador de ar', description: 'Habilita purificador/ionizador de ar.', category: 'Conforto / Bancos / Clima', risk: 'baixo' },
  { key: 'car.comfort_setting.chair_memory.auto_enable', name: 'Auto-aplicar memória de banco', description: 'Recall automático da posição do banco ao entrar.', category: 'Conforto / Bancos / Clima', risk: 'baixo' },
  { key: 'car.configure.onekey_inner_cycle_mode', name: 'Recirculação one-key', description: 'Botão único para recirculação de ar.', category: 'Conforto / Bancos / Clima', risk: 'baixo' },

  // ADAS / Segurança
  { key: 'car.configure.aeb_intersection_assist', name: 'AEB em cruzamento', description: 'Frenagem autônoma em cruzamentos. Sistema de segurança ativa.', category: 'ADAS / Segurança', risk: 'alto' },
  { key: 'car.configure.fcw_and_aeb', name: 'FCW + AEB', description: 'Alerta e frenagem de colisão frontal. Sistema de segurança ativa.', category: 'ADAS / Segurança', risk: 'alto' },
  { key: 'car.configure.lca', name: 'LCA — alerta de mudança de faixa', description: 'Blind spot / lane change assist. Sistema de segurança ativa.', category: 'ADAS / Segurança', risk: 'alto' },
  { key: 'car.configure.ldw_and_lka', name: 'LDW + LKA', description: 'Aviso e manutenção de faixa. Sistema de segurança ativa.', category: 'ADAS / Segurança', risk: 'alto' },
  { key: 'car.configure.highway_assist_system', name: 'Highway Assist', description: 'Condução assistida em rodovia. Sistema de segurança ativa.', category: 'ADAS / Segurança', risk: 'alto' },
  { key: 'car.configure.night_vision_system', name: 'Visão noturna', description: 'Câmera térmica/IR. Depende de hardware.', category: 'ADAS / Segurança', risk: 'medio' },
  { key: 'car.configure.drowsiness_warning_system', name: 'Alerta de sonolência', description: 'Monitor de fadiga do motorista.', category: 'ADAS / Segurança', risk: 'medio' },
  { key: 'car.configure.traffic_sign_warning', name: 'TSR — leitura de placas', description: 'Reconhecimento de sinais de trânsito.', category: 'ADAS / Segurança', risk: 'medio' },
  { key: 'car.configure.front_dead_angle_system', name: 'Ponto cego dianteiro', description: 'Detecção de ângulo morto frontal.', category: 'ADAS / Segurança', risk: 'medio' },

  // Unidades / Região / Mercado
  { key: 'car.configure.standard_unit', name: 'Sistema de unidades', description: 'Métrico ou imperial.', category: 'Unidades / Região / Mercado', risk: 'baixo' },
  { key: 'car.configure.default_temp_unit', name: 'Unidade de temperatura', description: 'Celsius ou Fahrenheit de fábrica.', category: 'Unidades / Região / Mercado', risk: 'baixo' },
  { key: 'car.configure.speed_meter', name: 'Tipo de velocímetro', description: 'km/h vs. mph / escala do velocímetro.', category: 'Unidades / Região / Mercado', risk: 'medio' },
  { key: 'car.configure.electrical_language', name: 'Idioma do sistema (coding)', description: 'Idioma padrão dos módulos elétricos.', category: 'Unidades / Região / Mercado', risk: 'medio' },
  { key: 'car.configure.sales_area', name: 'Área de venda', description: 'Coding de mercado. Pode travar recursos do carro — só mexer com certeza.', category: 'Unidades / Região / Mercado', risk: 'alto' },
  { key: 'car.configure.regional', name: 'Região', description: 'Coding regional. Risco de travar recursos — só mexer com certeza.', category: 'Unidades / Região / Mercado', risk: 'alto' },
  { key: 'car.configure.special_export_country', name: 'País de exportação', description: 'Coding de país. Risco de travar recursos — só mexer com certeza.', category: 'Unidades / Região / Mercado', risk: 'alto' },
  { key: 'car.configure.drive_hand_type', name: 'Lado da direção (LHD/RHD)', description: 'Volante à esquerda ou à direita. Não mexer sem certeza absoluta.', category: 'Unidades / Região / Mercado', risk: 'alto' },
  { key: 'car.configure.emission_standards', name: 'Norma de emissões', description: 'Coding de emissão. Risco regulatório — só mexer com certeza.', category: 'Unidades / Região / Mercado', risk: 'alto' },

  // Powertrain / Off-road / EV
  { key: 'car.configure.driving_mode_optional', name: 'Modos de condução disponíveis', description: 'Habilita modos extras (Sport etc.).', category: 'Powertrain / Off-road / EV', risk: 'medio' },
  { key: 'car.configure.driving_mode2_optional', name: 'Modos de condução (grupo 2)', description: 'Modos adicionais de condução.', category: 'Powertrain / Off-road / EV', risk: 'medio' },
  { key: 'car.configure.drive_mode_memory', name: 'Memória do modo de condução', description: 'Lembra o último modo usado.', category: 'Powertrain / Off-road / EV', risk: 'baixo' },
  { key: 'car.configure.catapult_start', name: 'Launch control (catapult)', description: 'Largada de performance.', category: 'Powertrain / Off-road / EV', risk: 'medio' },
  { key: 'car.configure.differential_lock', name: 'Bloqueio de diferencial', description: 'Trava de diferencial para off-road. Depende de hardware.', category: 'Powertrain / Off-road / EV', risk: 'medio' },
  { key: 'car.configure.off_road_expert_mode', name: 'Modo off-road expert', description: 'Telas e controles off-road avançados.', category: 'Powertrain / Off-road / EV', risk: 'baixo' },
  { key: 'car.configure.off_road_cruise_control', name: 'Cruise off-road (crawl)', description: 'Controle de velocidade em trilha.', category: 'Powertrain / Off-road / EV', risk: 'medio' },
  { key: 'car.configure.braking_energy_recovery', name: 'Regeneração de frenagem', description: 'Nível de regeneração (EV/HEV).', category: 'Powertrain / Off-road / EV', risk: 'baixo' },
  { key: 'car.configure.bidirection_charging', name: 'Carga bidirecional (V2L/V2G)', description: 'Descarga para equipamentos externos.', category: 'Powertrain / Off-road / EV', risk: 'medio' },
  { key: 'car.configure.idle_stop_and_go', name: 'Start-Stop', description: 'Desliga o motor quando o carro para.', category: 'Powertrain / Off-road / EV', risk: 'baixo' },

  // Portas / Segurança veicular
  { key: 'car.configure.door_unlock_mode', name: 'Modo de destrave', description: '1 porta, todas as portas, ou ao entrar em movimento.', category: 'Portas / Segurança veicular', risk: 'baixo' },
  { key: 'car.configure.super_lock', name: 'Super-trava (anti-furto)', description: 'Bloqueio duplo das portas.', category: 'Portas / Segurança veicular', risk: 'medio' },
  { key: 'car.configure.mobile_bluetooth_key', name: 'Chave digital Bluetooth', description: 'Usa o celular como chave.', category: 'Portas / Segurança veicular', risk: 'medio' },
  { key: 'car.configure.nfc', name: 'Chave NFC', description: 'Cartão/celular NFC como chave.', category: 'Portas / Segurança veicular', risk: 'medio' },
  { key: 'car.configure.remote_control', name: 'Controle remoto', description: 'Comandos remotos do veículo.', category: 'Portas / Segurança veicular', risk: 'baixo' },
  { key: 'car.configure.rear_door_opening', name: 'Abertura porta traseira', description: 'Configuração da porta traseira/mala.', category: 'Portas / Segurança veicular', risk: 'baixo' },

  // Cluster / HUD / Telas
  { key: 'car.configure.hud', name: 'Head-Up Display', description: 'Habilita o HUD. Depende de hardware.', category: 'Cluster / HUD / Telas', risk: 'medio' },
  { key: 'car.configure.cluster_type', name: 'Tipo de cluster', description: 'Variante do painel de instrumentos. Não casar errado — pode inutilizar o painel.', category: 'Cluster / HUD / Telas', risk: 'alto' },
  { key: 'car.configure.cluster_time_display', name: 'Relógio no cluster', description: 'Mostra a hora no painel.', category: 'Cluster / HUD / Telas', risk: 'baixo' },
  { key: 'car.configure.sub_instrument_screen', name: 'Tela secundária', description: 'Habilita sub-display do painel.', category: 'Cluster / HUD / Telas', risk: 'medio' },
  { key: 'car.configure.outside_temp_display', name: 'Temp. externa no painel', description: 'Exibe a temperatura externa.', category: 'Cluster / HUD / Telas', risk: 'baixo' },

  // Áudio
  { key: 'car.configure.amplifier', name: 'Amplificador', description: 'Habilita amplificador externo. Depende de hardware.', category: 'Áudio', risk: 'medio' },
  { key: 'car.configure.anc_system', name: 'ANC — cancelamento de ruído', description: 'Cancelamento ativo de ruído. Depende de hardware.', category: 'Áudio', risk: 'medio' },
  { key: 'car.configure.speaker', name: 'Config. de alto-falantes', description: 'Layout de speakers.', category: 'Áudio', risk: 'medio' },
]

export const CODING_CATALOG_BY_KEY: ReadonlyMap<string, CodingCatalogEntry> = new Map(
  CODING_CATALOG.map((entry) => [entry.key, entry]),
)

export const CODING_CATEGORIES: readonly string[] = [...new Set(CODING_CATALOG.map((entry) => entry.category))]
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
