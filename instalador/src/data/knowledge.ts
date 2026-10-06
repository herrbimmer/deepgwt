// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Fachada da Vehicle Knowledge Base.
//
// Os dados vivem em `knowledge/` (fora do controle de versão, por decisão do dono) e são gerados para `src/data/generated/` por `node scripts/kb-build.mjs`. Todo o app
// importa daqui, nunca dos arquivos gerados diretamente.

import { CONTROL_SERVICE } from './generated/controlServiceKeys'
import { VHAL } from './generated/vhalProperties'
import { CODING } from './generated/codingDefinitions'
import { FACTORY_CODING } from './generated/factoryCoding'
import { VEHICLE_NETWORK } from './generated/vehicleNetwork'
import { VHAL_LIVE } from './generated/vhalObservations'
import { LIVE_TRACE } from './generated/liveTrace'
import { PLATFORM } from './generated/platformIdentity'
import { THEME_BRIDGE } from './generated/themeBridgeKeys'
import { EQUIPMENT_LINK_BY_KEY, type EquipmentVerdict } from './equipmentLinks'

export { CONTROL_SERVICE, VHAL, CODING }
export { FACTORY_CODING, VEHICLE_NETWORK, VHAL_LIVE, LIVE_TRACE, PLATFORM, THEME_BRIDGE }
export { EQUIPMENT_LINKS, EQUIPMENT_LINK_BY_KEY } from './equipmentLinks'
export type { EquipmentLink, EquipmentVerdict } from './equipmentLinks'
export type {
  Confidence, ControlServiceKey, ControlServiceLayer, VhalProperty, VhalLayer,
  CodingDefinition, CodingLayer, FactoryFlag, FactoryCodingLayer, NetworkNode, NetworkInterface,
  VehicleNetworkLayer, VhalObservation, VhalLiveLayer, TraceKey, TracePoint, LiveTraceLayer,
  PlatformProperty, PlatformLayer, ThemeBridgeChannel, ThemeBridgeKey, ThemeBridgeLayer,
} from './knowledgeTypes'

/** Índice por chave `car.*`/`sys.*`. */
export const CONTROL_SERVICE_BY_KEY = new Map(CONTROL_SERVICE.keys.map((k) => [k.key, k]))
/** Índice por id hexadecimal da propriedade VHAL, ex.: `0x2140241e`. */
export const VHAL_BY_ID = new Map(VHAL.properties.map((p) => [p.id, p]))
/** Índice por nome do sinal VHAL — só as 1087 propriedades que têm nome legível. */
export const VHAL_BY_NAME = new Map(
  VHAL.properties.filter((p) => p.name).map((p) => [p.name as string, p]),
)

/** Índice por nome da flag de fábrica, ex.: `head.up.display`. */
export const FACTORY_BY_NAME = new Map(FACTORY_CODING.flags.map((f) => [f.name, f]))
/** Índice do valor lido ao vivo por id de propriedade VHAL. */
export const VHAL_LIVE_BY_ID = new Map(VHAL_LIVE.observations.map((o) => [o.id, o]))
/** Índice do valor lido ao vivo por nome de sinal VHAL. */
export const VHAL_LIVE_BY_NAME = new Map(
  VHAL_LIVE.observations.filter((o) => o.name).map((o) => [o.name as string, o]),
)
/** Índice da série temporal por chave do ControlService. */
export const TRACE_BY_KEY = new Map(LIVE_TRACE.keys.map((k) => [k.key, k]))

/**
 * Índice do contrato de temas. Só o que está aqui pode ser assinado por um tema do cluster —
 * qualquer outra chave é descartada pelo carro com um log e nada mais.
 */
export const THEME_BRIDGE_BY_KEY = new Map(THEME_BRIDGE.keys.map((k) => [k.key, k]))
/** As chaves que um tema consegue exibir: tudo menos as de escrita sem leitura. */
export const THEME_READABLE_KEYS = THEME_BRIDGE.keys.filter((k) => k.channel !== 'writeOnly')

/**
 * A função equipada de fábrica neste VIN? `null` quando a flag não existe na tabela — que é
 * diferente de `false`: ausente da tabela significa "não sabemos", zero significa "a fábrica
 * declarou ausente".
 */
export const isEquipped = (flagName: string): boolean | null =>
  FACTORY_BY_NAME.get(flagName)?.equipped ?? null

/**
 * O que a codificação de fábrica diz sobre um ajuste do catálogo neste carro. `null` quando não há
 * vínculo registrado — que é a maioria dos casos, e a tela não deve fingir que sabe.
 */
export const equipmentVerdictOf = (codingKey: string): EquipmentVerdict => {
  const link = EQUIPMENT_LINK_BY_KEY.get(codingKey)
  if (!link) return null
  const flag = FACTORY_BY_NAME.get(link.factoryFlag)
  if (!flag) return null
  return { equipped: flag.equipped, value: flag.value, factoryFlag: flag.name }
}

/** Resumo da base, para telas de conhecimento e relatórios citarem números reais. */
export const KNOWLEDGE_SUMMARY = {
  controlServiceKeys: CONTROL_SERVICE.meta.count,
  vhalProperties: VHAL.meta.count,
  vhalNamed: VHAL.meta.named,
  vhalDescribed: VHAL.meta.described,
  vhalObservedValues: VHAL.meta.observedValues,
  codingDefinitions: CODING.meta.count,
  capturedAt: VHAL.meta.capturedAt,
  vehicle: VHAL.meta.vehicle,
} as const

/** O mesmo para a sessão com o carro ligado — leitura deste VIN, não catálogo da plataforma. */
export const SESSION_SUMMARY = {
  capturedAt: FACTORY_CODING.meta.capturedAt,
  vin: FACTORY_CODING.meta.vin,
  factoryFlags: FACTORY_CODING.meta.count,
  factoryEquipped: FACTORY_CODING.meta.equippedCount,
  networkNodes: VEHICLE_NETWORK.meta.count,
  networkIdentified: VEHICLE_NETWORK.meta.identified,
  networkInterfaces: VEHICLE_NETWORK.meta.interfaceCount,
  vhalLiveSignals: VHAL_LIVE.meta.count,
  traceKeys: LIVE_TRACE.meta.count,
  traceSamples: LIVE_TRACE.meta.samples,
  osVariants: PLATFORM.meta.variantCount,
  buildFamilies: PLATFORM.meta.familyCount,
} as const
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
