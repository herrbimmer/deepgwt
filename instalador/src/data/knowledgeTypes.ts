// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Tipos da Vehicle Knowledge Base. Os DADOS ficam em `knowledge/` (fora do controle de versão,
// por decisão do dono) e são gerados para `src/data/generated/` por
// `node scripts/kb-build.mjs`. Estes tipos são código, não descoberta — ficam versionados.

/**
 * Nível de confiança de cada afirmação. Nunca promova um item de nível sem registrar a evidência
 * nova que justifica a promoção — ver `knowledge/README.md`.
 *
 * - `FACT`                comprovado por dump/leitura do próprio veículo, reprodutível
 * - `OBSERVED`            valor lido ao vivo, válido para aquele carro naquele momento
 * - `INFERRED`            deduzido do nome/contexto/código; nunca confirmado no carro
 * - `UNKNOWN`             existe, não sabemos o que faz
 * - `REQUIRES_VALIDATION` hipótese que precisa de teste no veículo
 */
export type Confidence = 'FACT' | 'OBSERVED' | 'INFERRED' | 'UNKNOWN' | 'REQUIRES_VALIDATION'

export type LayerMeta = {
  layer: string
  count: number
  /** Data em que a evidência foi capturada do veículo — não é a data de geração do arquivo. */
  capturedAt: string
  vehicle: string
  method: string
  confidence: Record<string, string>
}

export type ControlServiceKey = {
  enumName: string
  key: string
  namespace: string
  /** `'usada'` = já exposta pela UI de fábrica; `'oculta'` = nunca exposta. `null` = fora do levantamento. */
  appState: string | null
  existence: Confidence
  semantics: Confidence
}

export type ControlServiceLayer = {
  meta: LayerMeta & {
    sourceFile: string
    stateSourceFile: string
    namespaces: Record<string, number>
  }
  keys: ControlServiceKey[]
}

export type VhalProperty = {
  name: string | null
  /** Id da propriedade em hexadecimal minúsculo, ex.: `0x2140241e`. */
  id: string
  type: string | null
  /** `R` | `RW` | `W` */
  access: string | null
  /** Prefixo do nome do sinal, que costuma nomear a ECU/frame de origem (`GW_FD1_*`, `ESP_FD2_*`). */
  ecuPrefix: string | null
  /** Valor lido ao vivo na captura. `null` = não emitiu valor. */
  liveValue: string | null
  description: string | null
  existence: Confidence
  value: Confidence
  semantics: Confidence
}

export type VhalLayer = {
  meta: LayerMeta & {
    named: number
    described: number
    observedValues: number
    sourceFiles: string[]
    byAccess: Record<string, number>
  }
  properties: VhalProperty[]
}

export type CodingDefinition = {
  key: string
  name: string
  effect: string
  risk: string
  /** Ressalva que vinha entre parênteses no risco, ex.: `hardware`. */
  riskNote: string | null
  category: string | null
  appState: string
  existence: Confidence
  semantics: Confidence
  /** Escrever a chave e observar o resultado nunca foi feito neste veículo. */
  writeEffect: Confidence
}

export type CodingLayer = {
  meta: LayerMeta & { sourceFile: string }
  entries: CodingDefinition[]
}

// ---------------------------------------------------------------------------
// Camadas da sessão com o veículo ligado (2026-09-15).
//
// As camadas acima são CATÁLOGO: que chaves/propriedades existem nesta plataforma. As de baixo
// são LEITURA: o que este VIN respondeu, nesta data, por um método registrado. A diferença
// importa — catálogo não envelhece, leitura envelhece a cada viagem.
// ---------------------------------------------------------------------------

export type SessionMeta = {
  layer: string
  capturedAt: string
  vin: string
  method: string
  confidence: Record<string, string>
}

/** Uma flag da codificação de variante gravada pela fábrica (`persist.vendor.gwm.cfg.*`). */
export type FactoryFlag = {
  /** Nome sem o prefixo, ex.: `head.up.display`. */
  name: string
  /** Propriedade completa, como lida do getprop. */
  prop: string
  value: string
  /** `true` quando o valor é diferente de zero — a fábrica declarou o item presente. */
  equipped: boolean
  /** Domínio derivado do nome da flag, para agrupar a lista. Não é classificação da GWM. */
  domain: string
  existence: Confidence
  semantics: Confidence
}

export type FactoryCodingLayer = {
  meta: SessionMeta & {
    count: number
    equippedCount: number
    sourceFile: string
    byDomain: Record<string, { total: number; equipped: number }>
  }
  flags: FactoryFlag[]
}

export type NetworkInterface = {
  name: string
  state: string
  flags: string[]
  addresses: string[]
  mac: string | null
}

/** Um nó da rede automotiva, visto pela tabela ARP estática do head unit. */
export type NetworkNode = {
  /** Último octeto do IP, que é também o último byte do MAC. */
  node: number
  mac: string
  vlans: string[]
  ips: string[]
  arpState: string
  /** `null` = o endereço existe, o que mora nele não foi lido. */
  role: string | null
  confidence: Confidence
  evidence: string | null
}

export type VehicleNetworkLayer = {
  meta: SessionMeta & {
    count: number
    identified: number
    interfaceCount: number
    sourceFiles: string[]
    macPattern: string
  }
  interfaces: NetworkInterface[]
  nodes: NetworkNode[]
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/** Um sinal VHAL que emitiu valor na sessão. */
export type VhalObservation = {
  id: string
  name: string | null
  access: string | null
  changeMode: string | null
  ecuPrefix: string | null
  value: string
  /** Quantos eventos o sinal emitiu: 1 = disparo único no power-on; milhares = contínuo. */
  eventCount: number
  timestamp: number
  zone: string
  value_confidence: Confidence
  semantics: Confidence
}

export type VhalLiveLayer = {
  meta: SessionMeta & {
    count: number
    named: number
    unnamed: number
    catalogSize: number
    sourceFile: string
  }
  observations: VhalObservation[]
}

export type TracePoint = { at: string; value: string; via: 'logcat' | 'diag' }

/** Série temporal de uma chave do ControlService, montada de eventos que o carro emitiu sozinho. */
export type TraceKey = {
  key: string
  namespace: string
  samples: number
  first: string
  last: string
  distinct: number
  points: TracePoint[]
}

export type LiveTraceLayer = {
  meta: SessionMeta & { count: number; samples: number; sourceFiles: string[] }
  keys: TraceKey[]
}

export type PlatformProperty = { prop: string; label: string; value: string; confidence: Confidence }

export type PlatformLayer = {
  meta: SessionMeta & {
    sourceFile: string
    familyCount: number
    variantCount: number
  }
  identity: PlatformProperty[]
  osVersions: { prop: string; variant: string; value: string }[]
  /** Variantes agrupadas por string de build: mesma string = mesma família de plataforma. */
  families: { build: string; variants: string[] }[]
}

/**
 * Por onde o valor de uma chave chega a um tema do cluster. Não é detalhe de implementação: um
 * mostrador ligado a uma chave `control` fica no valor de repouso até o carro mudar aquele estado,
 * porque não existe empurrão inicial para ela.
 *
 * - `subscribe`  chega por `window.onDataChanged` após `Android.subscribe`, com snapshot inicial
 * - `control`    chega SÓ por `window.control(chave, valor)`; `subscribe` a aceita e nunca emite
 * - `both`       os dois canais, com tipos diferentes: string no snapshot, booleano no `control`
 * - `writeOnly`  o carro aceita escrever (`updateCarData`) e nunca devolve o valor
 */
export type ThemeBridgeChannel = 'subscribe' | 'control' | 'both' | 'writeOnly'

export type ThemeBridgeKey = {
  key: string
  channel: ThemeBridgeChannel
  /** O tema pode escrever nesta chave por `Android.updateCarData` (valor truncado em 64 chars). */
  writable: boolean
  /** Existe no dicionário do ControlService. `false` = pseudo-chave sintetizada pelo host. */
  busKey: boolean
}

/**
 * O contrato que o veículo impõe a um tema: exatamente o que `ThemeBridgeImpl.getAvailableKeys()`
 * publica. É um recorte pequeno do dicionário de 818 chaves, e a diferença importa — assinar uma
 * chave fora desta lista é descartado com log e sem erro nenhum visível em JavaScript.
 */
export type ThemeBridgeLayer = {
  meta: LayerMeta & {
    sourceFile: string
    counts: { readable: number; writable: number; controlOnly: number; writeOnly: number }
    /** Nomes aceitos por `Android.setNativeMaskState` — diferentes dos nomes do `theme.xml`. */
    maskNames: string[]
  }
  keys: ThemeBridgeKey[]
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
