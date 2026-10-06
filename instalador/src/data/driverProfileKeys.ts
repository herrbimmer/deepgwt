// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// As 16 chaves do veículo que o instalador conhece pelo nome: a fixture do modo demonstração, o
// Explorador de Parâmetros e a Varredura do Veículo usam esta lista.
//
// ESTA LISTA NÃO É MAIS O PERFIL DE MOTORISTA. O perfil do carro deixou de ser uma lista fixa: o
// DeepGWT varre TODAS as configurações do barramento por regra de nome (ver EscopoDoPerfil.kt,
// ~257 chaves neste catálogo) e todas as preferências do app, e o instalador virou cliente desse
// perfil em vez de manter uma cópia — ver `screens/DriverProfiles.tsx`. O que sobrou aqui é o
// conjunto reduzido que as telas de demonstração e diagnóstico precisam ter escrito à mão.
//
// DUAS CORREÇÕES, ambas encontradas quando a Varredura do Veículo passou esta lista pelo dump do
// carro pela primeira vez:
//
//   1. `car.ev_setting.wade_mode_enable` NÃO EXISTE no dicionário do carro. O nome certo é
//      `car.ev.setting.wade_mode_enable`, com ponto. O dicionário mistura as duas formas nesta
//      área de propósito (`car.ev_setting.*` para carga, `car.ev.setting.*` para o resto), e a
//      transcrição seguiu o NOME do enum Java (CAR_EV_SETTING_...) em vez do seu VALOR. Enquanto
//      esteve errada, o modo vau nunca foi lido nem gravado — falhava em silêncio.
//   2. Faltavam as duas chaves de banco do motorista que o app Android salva. O comentário acima
//      dizia "mesmo conjunto", e não era.
//
// `scripts/kb-build.mjs` agora falha se qualquer chave daqui não estiver no dump.
export const DRIVER_PROFILE_KEYS: readonly string[] = [
  'car.ev.setting.avas_enable',
  'car.frs_setting.distraction_detection_enable',
  'car.basic.window_close_by_lock_vehicle',
  'car.drive_setting.esp_enable',
  'car.drive_setting.drive_mode',
  'car.ev.setting.wade_mode_enable',
  'car.comfort_setting.driver_seat_ventilation_level',
  'car.comfort_setting.driver_seat_heating_level',
  'car.intelligent_driving_setting.las.enable',
  'car.intelligent_driving_setting.las.lka_state',
  'car.intelligent_driving_setting.las.ldw_state',
  'car.intelligent_driving_setting.las.lca_state',
  'car.intelligent_driving_setting.fas.pcs_state',
  'car.intelligent_driving_setting.fas.pps_state',
  'car.intelligent_driving_setting.over_speed_warning_state',
  'car.parking_setting.auto_park_enable',
]
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
