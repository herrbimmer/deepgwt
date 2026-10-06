// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Liga cada ajuste do catálogo à flag de fábrica que diz se o carro TEM aquele hardware.
//
// O problema que isto resolve é concreto e foi encontrado no veículo. O catálogo oferece
// "auto-aplicar memória de banco" como um ajuste de risco baixo. Escrever essa chave não faz
// absolutamente nada nesta unidade: a codificação de fábrica declara `seat.memory.type = 0`,
// `seat.control.module = 0` e `driver.seat = 0`, e o módulo de banco nunca emitiu um único evento
// em todo o log do veículo. O carro aceita a escrita e nenhum módulo do outro lado a executa.
//
// Um ajuste que "grava com sucesso" e não muda nada é pior do que um ajuste indisponível: quem
// usou fica procurando defeito onde não há. Com este vínculo, a tela mostra antes de deixar tentar.
//
// REGRA para adicionar uma linha aqui: a flag precisa existir na tabela lida do carro — o gerador
// da base de conhecimento recusa um nome inventado — e a correspondência precisa ser evidente pelo
// próprio nome. Onde a correspondência for discutível, deixe de fora: ficar sem vínculo é o
// comportamento seguro (o app não afirma nada), enquanto um vínculo errado esconde uma função que
// o carro tem.

export type EquipmentLink = {
  /** Chave do catálogo de coding. */
  codingKey: string
  /** Nome da flag em `persist.vendor.gwm.cfg.*`, sem o prefixo. */
  factoryFlag: string
}

export const EQUIPMENT_LINKS: EquipmentLink[] = [
  // Conforto
  { codingKey: 'car.comfort_setting.chair_memory.auto_enable', factoryFlag: 'seat.memory.type' },
  { codingKey: 'car.configure.front_seat_cooling_and_massage', factoryFlag: 'frt.seat.cooling.frt.seat.massage' },
  { codingKey: 'car.configure.seat_heating', factoryFlag: 'seat.heating' },
  { codingKey: 'car.configure.driver_waist_supporter', factoryFlag: 'driver.waist.supporter' },
  { codingKey: 'car.configure.fragrance_system', factoryFlag: 'fragrance.system' },
  { codingKey: 'car.configure.air_purifier', factoryFlag: 'air.purifier' },

  // Iluminação
  { codingKey: 'car.configure.mood_lamp', factoryFlag: 'mood.lamp' },
  { codingKey: 'car.configure.audio_parsing_multicolor_rhythm_mood_lamp', factoryFlag: 'audio.parsing.multi.color.rhythm.mood.lamp' },
  { codingKey: 'car.configure.adaptive_front_lighting_system', factoryFlag: 'adaptive.front.lighting.system' },

  // ADAS
  { codingKey: 'car.configure.aeb_intersection_assist', factoryFlag: 'aeb.junction.assist' },
  { codingKey: 'car.configure.fcw_and_aeb', factoryFlag: 'forward.collision.warning.auto.emergency.braking' },
  { codingKey: 'car.configure.lca', factoryFlag: 'lane.change.assist' },
  { codingKey: 'car.configure.ldw_and_lka', factoryFlag: 'ldw.lka' },

  // Painel e áudio
  { codingKey: 'car.configure.hud', factoryFlag: 'head.up.display' },
  { codingKey: 'car.configure.cluster_type', factoryFlag: 'cluster' },
  { codingKey: 'car.configure.amplifier', factoryFlag: 'amplifier' },
  { codingKey: 'car.configure.speaker', factoryFlag: 'speaker' },

  // Condução
  { codingKey: 'car.configure.drive_mode_memory', factoryFlag: 'memory.of.driving.mode' },
  { codingKey: 'car.configure.braking_energy_recovery', factoryFlag: 'braking.energy.recovery' },
  { codingKey: 'car.configure.off_road_expert_mode', factoryFlag: 'off.road.expert.mode' },
  { codingKey: 'car.configure.idle_stop_and_go', factoryFlag: 'idle.stop.and.go' },
  { codingKey: 'car.configure.super_lock', factoryFlag: 'super.lock' },
]

export const EQUIPMENT_LINK_BY_KEY = new Map(EQUIPMENT_LINKS.map((l) => [l.codingKey, l]))

/**
 * O que a codificação de fábrica diz sobre este ajuste neste carro.
 *
 * `null` significa que não há vínculo registrado — e é o caso da maioria. Não sabemos, e a tela
 * não deve sugerir que sabe.
 */
export type EquipmentVerdict = {
  equipped: boolean
  /** Valor cru da flag. Diferente de zero identifica a variante, e o número não foi traduzido. */
  value: string
  factoryFlag: string
} | null
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
