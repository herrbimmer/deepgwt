// Snapshot sintético de telemetria, para a UI inteira ser navegável e testável sem carro.
//
// O conjunto de chaves NÃO é escrito à mão: é derivado em tempo de execução das listas de chaves
// que as próprias telas leem. Assim a fixture não pode divergir do app, e não contém nenhum nome
// de chave inventado — todos vêm de uma lista já existente no repositório.
//
// Os VALORES são placeholders determinísticos e claramente sintéticos. Nunca são apresentados
// como leitura do veículo: enquanto o modo demo está ativo, um aviso fica visível na tela.

import { GWLINK_BASIC_PARAMS } from '../screens/gwlinkParams'
import { CODING_CATALOG } from '../data/codingCatalog'
import { ADAS_TOGGLE_KEYS } from '../data/adasCatalog'
import { DRIVER_PROFILE_KEYS } from '../data/driverProfileKeys'
import { ALL_IDENTITY_KEYS } from '../vehicle/identityKeys'
import { MONITOR_KEYS } from '../vehicle/monitorSignals'

export const DEMO_ORIGIN = 'synthetic' as const

/**
 * VIN de demonstração com dígito verificador válido (posição 9 = `5`, conferida pelo algoritmo
 * da norma) — serve para exercitar o decodificador de ponta a ponta.
 *
 * Note o `DEM5` em vez de `DEMO`: a norma não usa a letra O, para não confundir com zero. A
 * primeira versão desta fixture escrevia `DEMO` e produzia um VIN que o próprio decodificador
 * rejeita — o teste de `src/vehicle/vin.ts` foi o que pegou isso.
 *
 * O WMI `LDG` não é uma atribuição real de fabricante: a tela de identidade mostra o fabricante
 * como desconhecido, que é o comportamento correto para um WMI que não está em tabela nenhuma.
 */
export const DEMO_VIN = 'LDG00DEM5R0000019'

/** Chaves avulsas usadas por telas que não têm lista própria (Ajustes rápidos, DVR). */
const LOOSE_KEYS = [
  'car.ev.setting.avas_enable',
  'car.frs_setting.distraction_detection_enable',
  'car.basic.window_close_by_lock_vehicle',
  'sys.dvr.remote_switch',
] as const

/** Valores plausíveis em forma, explicitamente sintéticos em conteúdo. */
const BASIC_VALUES: Record<string, string> = {
  'car.basic.vin_code': DEMO_VIN,
  'car.basic.vehicle_version': 'DEMO-0.0.0',
  'car.basic.vehicle_speed': '0',
  'car.basic.engine_speed': '0',
  'car.basic.gear_status': '3',
  'car.basic.engine_state': '0',
  'car.basic.battery_voltage': '12.4',
  'car.basic.total_odometer': '12345',
  'car.basic.accumulated_odometer': '12345',
  'car.basic.outside_temp': '24',
  'car.basic.inside_temp': '23',
  'car.basic.remain_fuel_percentage': '62',
  'car.basic.coolant_temp': '88',
}

/**
 * Identidade sintética. Os textos dizem DEMO em letras maiúsculas de propósito: se um destes
 * valores vazar para um relatório, fica óbvio na hora que não saiu de um carro.
 * As chaves numéricas recebem `1` porque o app não sabe o que os códigos de configuração
 * significam — inventar um código "realista" seria fabricar informação.
 */
const IDENTITY_VALUES: Record<string, string> = {
  'car.basic.vehicle_model1': 'DEMO-MODEL-1',
  'car.basic.vehicle_model2': 'DEMO-MODEL-2',
  'car.basic.configure_code': 'DEMO-CONFIG',
  'car.configure.product_name': 'VEICULO DEMO',
  'car.configure.project_code': 'DEMO-PRJ',
  'sys.basic.os_version': 'DEMO-OS-0.0.0',
  'sys.basic.hw_version': 'DEMO-HW-0',
  'sys.basic.mcu_version': 'DEMO-MCU-0',
  'sys.network.tbox.sofeware_version': 'DEMO-TBOX-SW-0',
  'sys.network.tbox.hardware_version': 'DEMO-TBOX-HW-0',
}

/**
 * Estado sintético para a tela "Monitorar Veículo": carro parado, em P, pronto para andar.
 *
 * Duas escolhas de propósito. A primeira é `head_light_status` VAZIO: é o comportamento real
 * desta chave no veículo de referência, e deixá-la vazia aqui faz o modo demo exercitar o terceiro
 * estado da lâmpada ("sem resposta"), que é justamente o que se perderia se a fixture preenchesse
 * tudo. A segunda é `low_beam_light_status` em `1` com o mestre vazio — a mesma combinação que o
 * carro devolveu, e que a tela precisa saber exibir sem concluir "faróis ligados".
 */
const MONITOR_VALUES: Record<string, string> = {
  'car.basic.vehicle_speed': '0.0',
  'car.basic.gear_status': '3',
  'car.basic.steering_wheel_angle': '0.0',
  'car.drive_setting.drive_mode': '0',
  'car.basic.driving_ready_state': '1',
  'car.basic.power_mode': '2',
  'car.basic.engine_state': '11',
  'car.basic.remain_fuel_percentage': '62',
  'car.ev_info.fuel_mode_remain_odometer': '410',
  'car.ev_info.cur_battery_power_percentage': '58',
  'car.ev_info.power_battery_voltage': '311.0',
  'car.ev_info.electric_mode_remain_odometer': '73',
  'car.basic.battery_power_level': '92',
  'car.basic.battery_voltage': '12.4',
  'car.basic.head_light_status': '',
  'car.basic.low_beam_light_status': '1',
  'car.basic.high_beam_light_status': '0',
  'car.basic.front_fog_light_status': '0',
  'car.basic.rear_fog_light_status': '0',
  'car.basic.left_turn_light_status': '0',
  'car.basic.right_turn_light_status': '0',
  'car.basic.hazard_light_status': '0',
}

export function buildDemoSnapshot(): Record<string, string> {
  const values: Record<string, string> = {}

  // Chaves booleanas (as telas as tratam como '1'/'0'): alternam para que listas não fiquem
  // todas iguais e o filtro "só o que está ligado" tenha o que mostrar.
  const boolean = [
    ...CODING_CATALOG.map((e) => e.key),
    ...ADAS_TOGGLE_KEYS,
    ...DRIVER_PROFILE_KEYS,
    ...LOOSE_KEYS,
  ]
  boolean.forEach((key, i) => {
    if (values[key] === undefined) values[key] = i % 3 === 0 ? '1' : '0'
  })

  for (const { key } of GWLINK_BASIC_PARAMS) {
    if (values[key] === undefined) values[key] = BASIC_VALUES[key] ?? '0'
  }
  for (const key of ALL_IDENTITY_KEYS) {
    if (values[key] === undefined) values[key] = IDENTITY_VALUES[key] ?? '1'
  }
  for (const key of MONITOR_KEYS) {
    if (values[key] === undefined) values[key] = MONITOR_VALUES[key] ?? '0'
  }
  Object.assign(values, BASIC_VALUES, IDENTITY_VALUES, MONITOR_VALUES)

  return values
}

/** De onde vem cada pedaço da fixture — mostrado na UI para deixar a procedência explícita. */
export function demoKeySources(): { list: string; count: number }[] {
  return [
    { list: 'screens/gwlinkParams.ts', count: GWLINK_BASIC_PARAMS.length },
    { list: 'data/codingCatalog.ts', count: CODING_CATALOG.length },
    { list: 'data/adasCatalog.ts', count: ADAS_TOGGLE_KEYS.length },
    { list: 'data/driverProfileKeys.ts', count: DRIVER_PROFILE_KEYS.length },
    { list: 'vehicle/identityKeys.ts', count: ALL_IDENTITY_KEYS.length },
    { list: 'vehicle/monitorSignals.ts', count: MONITOR_KEYS.length },
    { list: 'chaves avulsas (Ajustes rápidos, DVR)', count: LOOSE_KEYS.length },
  ]
}
