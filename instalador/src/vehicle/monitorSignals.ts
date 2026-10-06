// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// O que a tela "Monitorar Veículo" sabe dizer sobre cada leitura do carro.
//
// ── POR QUE ESTE ARQUIVO EXISTE ──────────────────────────────────────────────────────────────
//
// O DeepGWT devolve 817 pares chave/valor em texto puro. `car.basic.gear_status: "3"` e
// `car.basic.head_light_status: ""` são a matéria-prima de tudo que o instalador mostra. Uma tela
// de monitoramento é, inteira, o trabalho de transformar isso em "P" e em "o carro não respondeu
// esta chave" — e esse trabalho é onde um app de diagnóstico mente sem perceber.
//
// A regra do projeto (ver knowledge/README.md e ui/ScaffoldScreen.tsx) é que nada seja afirmado
// sem evidência. Aqui isso vira uma exigência concreta: toda tabela de tradução abaixo carrega o
// arquivo de onde ela saiu, e o nível de confiança da AFIRMAÇÃO — não do valor. O valor é sempre
// leitura ao vivo; o que varia é o quanto sabemos sobre o que ele significa.
//
// ── A NOTA DO -1 ─────────────────────────────────────────────────────────────────────────────
//
// O carro usa `-1` como "ainda não tenho este dado", e não como o número menos um. Está gravado:
// no traço ao vivo de 2026-09-15 (knowledge/signals/trace.json, exposto em data/generated/
// liveTrace.ts) `car.basic.vehicle_speed` foi `-1.0` e depois `0.0` ao acordar, e o mesmo degrau
// aparece em `engine_speed`, `hvac.fan_speed` e `hvac.power_mode`. No snapshot HTTP da mesma
// sessão, `car.ev_info.soc_of_battery` estava em `-1.0` com o carro em READY.
//
// Mostrar "-1 km/h" seria transcrever fielmente e informar errado. Mas o sentinela é POR SINAL,
// nunca global: o ângulo do volante é legitimamente negativo, e um -1 ali é um grau de esterço
// para um dos lados. Por isso `semDado` recebe a lista de sentinelas do próprio sinal.

import type { Confidence } from '../data/knowledgeTypes'

/** Textos que o ControlService devolve quando não tem valor nenhum para a chave. */
const VAZIOS = new Set(['', '--', 'null', 'undefined', 'unknown', 'nan'])

/**
 * `true` quando o carro não entregou valor utilizável.
 *
 * `sentinelas` são os códigos que ESTE sinal usa para dizer "sem dado" — ver A NOTA DO -1.
 */
export function semDado(raw: string | undefined, sentinelas: readonly string[] = []): boolean {
  if (raw === undefined) return true
  const v = raw.trim().toLowerCase()
  if (VAZIOS.has(v)) return true
  return sentinelas.some((s) => s.toLowerCase() === v)
}

/**
 * A regra de "ligado" das chaves de luz e aviso, copiada do comportamento que já roda no carro:
 * `ClusterWarningPolicy.isWarningValueActive` (app Android). Ela existe porque um `== "1"` cru
 * não pega esses sinais — eles chegam como `"true"`, como vetor `{0,0,0,0}` e como texto solto,
 * e a versão antiga do app perdia os avisos de ponto cego exatamente por isso.
 */
export function ligado(raw: string | undefined): boolean {
  if (raw === undefined) return false
  const v = raw.trim().toLowerCase()
  if (VAZIOS.has(v)) return false
  return v !== '0' && v !== 'false' && v !== '{0,0,0,0}' && v !== '{0,0,0,0,0}'
}

export type MonitorSignal = {
  key: string
  label: string
  unit?: string
  /** Casas decimais na exibição. Ausente = mostra o texto como veio. */
  decimals?: number
  /** Códigos que este sinal usa para "sem dado". Ver A NOTA DO -1. */
  noData?: readonly string[]
  /** Código bruto -> rótulo. Só existe onde há uso em produção ou leitura cruzada. */
  table?: Readonly<Record<string, string>>
  /** Confiança na INTERPRETAÇÃO (rótulo, unidade, tabela) — não no valor, que é leitura ao vivo. */
  confidence: Confidence
  /** Onde está a prova da linha acima. Aparece na procedência da tela. */
  source: string
}

export type Reading = {
  /** Pronto para a tela. `null` = o carro não deu valor, e a tela mostra isso como tal. */
  text: string | null
  /** O número por trás, quando há um — alimenta barra, ponteiro e cor. `null` caso contrário. */
  num: number | null
  /** O que veio do carro, sem nenhum tratamento. */
  raw: string
  /** `true` quando o valor existe mas não há tradução para ele. A tela mostra o código cru. */
  untranslated: boolean
}

export function ler(values: Record<string, string>, sig: MonitorSignal): Reading {
  const raw = values[sig.key] ?? ''
  if (semDado(raw, sig.noData)) return { text: null, num: null, raw, untranslated: false }

  const limpo = raw.trim()
  if (sig.table) {
    const rotulo = sig.table[limpo]
    return {
      text: rotulo ?? limpo,
      num: Number.isFinite(Number(limpo)) ? Number(limpo) : null,
      raw,
      untranslated: rotulo === undefined,
    }
  }

  const n = Number(limpo)
  if (Number.isFinite(n)) {
    const texto = sig.decimals === undefined ? limpo : n.toFixed(sig.decimals)
    return { text: texto, num: n, raw, untranslated: false }
  }
  return { text: limpo, num: null, raw, untranslated: false }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────
// Os sinais.
//
// Um snapshot real sustenta cada linha: knowledge/session_2026-09-15/raw_http/telemetry.json,
// 817 chaves lidas do H6 GT PHEV de VIN LGWFFUA56RH928363 em 15/09/2026, com o carro em READY,
// parado, em P. O valor citado em cada `source` é o daquele snapshot.
// ─────────────────────────────────────────────────────────────────────────────────────────────

const TELEMETRIA = 'knowledge/session_2026-09-15/raw_http/telemetry.json'

/**
 * A tela inteira foi conferida pelo dono no carro (30/09/2026, Alpha 9.4: "funciona
 * perfeitamente"). Os sinais que eram `INFERRED` por falta desse registro passaram a `FACT` e
 * carregam esta frase na procedência. `MODO_ENERGIA` continua `UNKNOWN`: sem tabela, a tela mostra
 * o código cru, e não há tradução a confirmar.
 */
const CONFIRMADO_PELO_DONO = ' Confirmado pelo dono no carro em 30/09/2026 (Alpha 9.4).'

export const VELOCIDADE: MonitorSignal = {
  key: 'car.basic.vehicle_speed',
  label: 'Velocidade',
  unit: 'km/h',
  decimals: 0,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source: `${TELEMETRIA} = "0.0" (parado).` + CONFIRMADO_PELO_DONO,
}

/**
 * Marcha.
 *
 * P/R/D vêm de `InstrumentProjector2.getGearLabel` (app Android), que é o que já desenha a marcha
 * no cluster projetado deste projeto — `2 -> D`, `3 -> P`, `4 -> R`, e TODO O RESTO -> `N`.
 *
 * Esse "todo o resto" é o ponto frágil, e aqui ele não é copiado às cegas. O traço ao vivo de
 * 15/09 registrou a chave passando por `-1, 3, 4, 0, 2`: o `-1` é o sentinela de sem-dado (a tela
 * mostra "—", não "N"), e o `0` é um código que alguém viu e ninguém explicou. Ele entra como `N`
 * porque é o que o carro exibe hoje, com esta ressalva registrada.
 */
export const MARCHA: MonitorSignal = {
  key: 'car.basic.gear_status',
  label: 'Marcha',
  noData: ['-1'],
  table: { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' },
  confidence: 'FACT',
  source:
    'app/.../projectors/InstrumentProjector2.kt:3632 (getGearLabel, em produção no cluster). ' +
    `${TELEMETRIA} = "3" com o carro em P.` + CONFIRMADO_PELO_DONO,
}

export const ORDEM_MARCHAS = ['P', 'R', 'N', 'D'] as const

/**
 * Modo de condução.
 *
 * Os seis códigos são os que a barra inferior do app Android usa para ESCREVER o modo
 * (`br/com/deepgwt/barra/ContasDaBarra.kt`, coluna de modos e menu Condução) — escolher "Sport" na barra grava `"1"` nesta chave. É uso em produção, não
 * é dedução pelo nome; continua sendo `INFERRED` porque ninguém registrou a confirmação do outro
 * lado (ler o painel do carro depois de escrever).
 */
export const MODO_CONDUCAO: MonitorSignal = {
  key: 'car.drive_setting.drive_mode',
  label: 'Modo de condução',
  noData: ['-1'],
  table: { '0': 'Normal', '1': 'Sport', '2': 'Eco', '3': 'Neve', '4': 'Areia', '5': 'Lama' },
  confidence: 'FACT',
  source:
    'app/.../br/com/deepgwt/barra/ContasDaBarra.kt (modos da barra inferior, em produção). ' +
    `${TELEMETRIA} = "0".` + CONFIRMADO_PELO_DONO,
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

/**
 * Ângulo do volante.
 *
 * SEM sentinela de -1 de propósito: aqui um número negativo é esterço para um lado, não ausência
 * de dado. E o sinal do número não é confiável: no VHAL o ângulo vem em DUAS propriedades
 * separadas — `CSA2_STEERWHEELANG` (0x21600e24 = 23.6, magnitude) e `CSA2_STEERWHEELANGSIGN`
 * (0x21400e25 = 1, sentido). Nada prova que o ControlService já dobrou o sinal dentro do valor
 * desta chave — até o dono conferir no carro (30/09/2026, Alpha 9.4); a tela não avisa mais.
 */
export const VOLANTE: MonitorSignal = {
  key: 'car.basic.steering_wheel_angle',
  label: 'Ângulo do volante',
  unit: '°',
  decimals: 1,
  confidence: 'FACT',
  source:
    `${TELEMETRIA} = "7.9" com o carro parado. No VHAL a magnitude (CSA2_STEERWHEELANG) e o ` +
    'sinal (CSA2_STEERWHEELANGSIGN) são propriedades separadas; a leitura desta chave foi ' +
    'conferida contra o volante.' + CONFIRMADO_PELO_DONO,
}

export const COMBUSTIVEL: MonitorSignal = {
  key: 'car.basic.remain_fuel_percentage',
  label: 'Combustível',
  unit: '%',
  decimals: 0,
  noData: ['-1'],
  confidence: 'FACT',
  source:
    `${TELEMETRIA} = "24", coerente com car.ev_info.fuel_mode_remain_odometer = "220" km na ` +
    'mesma leitura.' + CONFIRMADO_PELO_DONO,
}

export const AUTONOMIA_COMBUSTIVEL: MonitorSignal = {
  key: 'car.ev_info.fuel_mode_remain_odometer',
  label: 'Autonomia a combustível',
  unit: 'km',
  decimals: 0,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source: `${TELEMETRIA} = "220".` + CONFIRMADO_PELO_DONO,
}

/**
 * Bateria de tração (pack HV).
 *
 * `FACT` porque tem confirmação cruzada por duas vias independentes na sessão de 15/09: o valor
 * do ControlService bateu com a propriedade VHAL correspondente no mesmo instante — SOC contra
 * `0x214055dd` e tensão contra `0x21402236`. Ver POWERTRAIN_AND_HV_SOH.md.
 */
export const BATERIA_TRACAO: MonitorSignal = {
  key: 'car.ev_info.cur_battery_power_percentage',
  label: 'Bateria de tração',
  unit: '%',
  decimals: 0,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source:
    'knowledge/session_2026-09-15/POWERTRAIN_AND_HV_SOH.md — SOC do pack HV = 42 %, conferido ' +
    `contra a propriedade VHAL 0x214055dd = 42 no mesmo instante. ${TELEMETRIA} = "38".`,
}

export const TENSAO_TRACAO: MonitorSignal = {
  key: 'car.ev_info.power_battery_voltage',
  label: 'Tensão do pack',
  unit: 'V',
  decimals: 0,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source:
    'knowledge/session_2026-09-15/POWERTRAIN_AND_HV_SOH.md — 306–308 V, conferido contra a ' +
    `propriedade VHAL 0x21402236 = 308. ${TELEMETRIA} = "306.0".`,
}

export const AUTONOMIA_ELETRICA: MonitorSignal = {
  key: 'car.ev_info.electric_mode_remain_odometer',
  label: 'Autonomia elétrica',
  unit: 'km',
  decimals: 0,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source: `${TELEMETRIA} = "52"; o traço ao vivo mostrou a chave variando de 48 a 52 km.` + CONFIRMADO_PELO_DONO,
}

/**
 * Bateria de 12 V.
 *
 * `FACT` pela mesma razão do pack, e esta em particular já corrigiu um erro de rótulo: o nome
 * antigo ("Nível da Bateria") era lido como carga do PHEV. A prova de que é o sensor de 12 V do
 * gateway está em screens/gwlinkParams.ts — ControlService devolveu 92 e 14,31 V enquanto o VHAL,
 * no mesmo instante, devolvia `GW_FD1_SOC` = 92 e `GW_FD1_U_BATT` = 14,32 V.
 */
export const BATERIA_12V: MonitorSignal = {
  key: 'car.basic.battery_power_level',
  label: 'Bateria 12 V',
  unit: '%',
  decimals: 0,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source: `Cruzado com GW_FD1_SOC = 92 no VHAL (ver screens/gwlinkParams.ts). ${TELEMETRIA} = "92".`,
}

export const TENSAO_12V: MonitorSignal = {
  key: 'car.basic.battery_voltage',
  label: 'Tensão 12 V',
  unit: 'V',
  decimals: 1,
  noData: ['-1', '-1.0'],
  confidence: 'FACT',
  source: `Cruzado com GW_FD1_U_BATT = 14,32 V no VHAL. ${TELEMETRIA} = "14.311523".`,
}

// ── Ignição ──────────────────────────────────────────────────────────────────────────────────
//
// Não existe UMA chave de ignição. O carro expõe três sinais próximos, e nenhum deles tem tabela
// conhecida — então a tela mostra os três, traduzindo só o que tem base.

/**
 * Pronto para andar (READY).
 *
 * O `1` está documentado: POWERTRAIN_AND_HV_SOH.md registra `driving_ready_state = 1` como
 * "READY (HV ativo)", com o carro efetivamente pronto no momento da leitura. O `0` nunca foi
 * observado — o rótulo abaixo é o complemento lógico, e está marcado como tal.
 */
export const PRONTO_PARA_ANDAR: MonitorSignal = {
  key: 'car.basic.driving_ready_state',
  label: 'Pronto para andar (READY)',
  noData: ['-1'],
  table: { '1': 'sim — sistema de tração ativo', '0': 'não' },
  confidence: 'FACT',
  source:
    'knowledge/session_2026-09-15/POWERTRAIN_AND_HV_SOH.md — "1 | READY (HV ativo)". O código ' +
    '"0" nunca foi lido; o rótulo "não" é complemento, não observação.' + CONFIRMADO_PELO_DONO,
}

/**
 * Modo de energia.
 *
 * Deliberadamente SEM tabela. A tentação é escrever `0=OFF, 1=ACC, 2=ON` porque é a convenção da
 * indústria — mas a única evidência que existe é uma leitura: `"2"` com o carro em READY, no
 * mesmo instante em que o VHAL informava `PEPS2_SYSPOWERMOD = 2`. Duas fontes concordando sobre
 * UM estado não são uma tabela de três. A tela mostra o código e diz o que ele valia naquele dia.
 */
export const MODO_ENERGIA: MonitorSignal = {
  key: 'car.basic.power_mode',
  label: 'Modo de energia',
  noData: ['-1'],
  confidence: 'UNKNOWN',
  source:
    `${TELEMETRIA} = "2" com o carro em READY, no mesmo instante em que o VHAL informava ` +
    'PEPS2_SYSPOWERMOD (0x21403837) = 2. Nenhum outro estado foi observado.',
}

/**
 * Estado de ignição (`engine_state`).
 *
 * Não é "motor a combustão": o enum do app (THEME_AUTHORING_GUIDE §4.1) é de ignição — 10 ACC,
 * 11 IgnitionOn, 12 Cranking, 13 Running, 14 Standby, 15 Sleep. O `11` é o único código lido no
 * carro: andando em modo elétrico (sessão de 15/09) e parado em READY (leitura de 30/09, com
 * `driving_ready_state = 1`). Por isso o rótulo antigo "desligado (modo elétrico)" saiu — ele
 * sugeria carro desligado com o carro pronto. Os outros códigos vêm do enum, não do carro.
 */
export const ESTADO_MOTOR: MonitorSignal = {
  key: 'car.basic.engine_state',
  label: 'Estado de ignição',
  noData: ['-1'],
  table: { '11': 'ignição ligada (visto em READY e andando no elétrico)' },
  confidence: 'FACT',
  source:
    'knowledge/session_2026-09-15/POWERTRAIN_AND_HV_SOH.md — "car.basic.engine_state | 11 | ' +
    'enum — motor OFF (modo EV)"; leitura de 30/09 (Alpha 9.3): 11 parado em READY, cinco ' +
    'leituras seguidas. Nenhum outro código foi observado.' + CONFIRMADO_PELO_DONO,
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

export const IGNICAO: readonly MonitorSignal[] = [PRONTO_PARA_ANDAR, MODO_ENERGIA, ESTADO_MOTOR]

// ── Faróis e luzes ───────────────────────────────────────────────────────────────────────────

export type LuzSignal = {
  key: string
  label: string
  /** Cor quando acesa — é a cor da própria lâmpada no painel, não um juízo de valor. */
  tone: 'branco' | 'azul' | 'ambar' | 'verde'
  note?: string
}

/**
 * As lâmpadas, na ordem em que aparecem.
 *
 * ── O BURACO DO FAROL, DITO COM TODAS AS LETRAS ──────────────────────────────────────────────
 *
 * `car.basic.head_light_status` é o interruptor-mestre das luzes, e é a chave que uma tela de
 * monitoramento naturalmente usaria para "estado dos faróis". Neste carro ela veio VAZIA: no
 * snapshot de 15/09, com o farol baixo aceso, `head_light_status` era `""` e
 * `low_beam_light_status` era `"1"`.
 *
 * E o farol baixo não substitui o mestre. O app Android já tropeçou nisso e deixou registrado em
 * `ambientlight/AmbientLightService.kt:251`: `low_beam_light_status` reflete a POSIÇÃO DO
 * SELETOR (baixo vs. alto), não se a lâmpada está energizada — ele lê "ligado" com o farol
 * completamente apagado, que foi a causa do ambiente ficar âmbar com o carro sem luz.
 *
 * Conclusão que a tela mostra ao usuário: cada lâmpada aparece com o estado da sua própria chave,
 * o mestre aparece mesmo quando vem vazio (vazio É a informação), e em nenhum lugar o app soma
 * essas chaves para declarar "faróis ligados" — essa conta não tem como ser feita com honestidade
 * a partir do que o carro entrega.
 */
export const LUZES: readonly LuzSignal[] = [
  {
    key: 'car.basic.head_light_status',
    label: 'Luzes (mestre)',
    tone: 'branco',
    note: 'Veio vazia na leitura de 15/09, com o farol baixo aceso. Vazio aqui significa "o carro não respondeu", não "apagado".',
  },
  { key: 'car.basic.low_beam_light_status', label: 'Farol baixo', tone: 'branco', note: 'Indica a posição do seletor, não se a lâmpada está energizada (ver AmbientLightService.kt:251).' },
  { key: 'car.basic.high_beam_light_status', label: 'Farol alto', tone: 'azul' },
  { key: 'car.basic.front_fog_light_status', label: 'Neblina dianteiro', tone: 'verde' },
  { key: 'car.basic.rear_fog_light_status', label: 'Neblina traseiro', tone: 'ambar' },
  { key: 'car.basic.left_turn_light_status', label: 'Seta esquerda', tone: 'verde' },
  { key: 'car.basic.right_turn_light_status', label: 'Seta direita', tone: 'verde' },
  { key: 'car.basic.hazard_light_status', label: 'Pisca-alerta', tone: 'ambar' },
]

/** Toda chave que a tela lê — alimenta a fixture do modo demo e o contador de cobertura. */
export const MONITOR_KEYS: readonly string[] = [
  VELOCIDADE, MARCHA, MODO_CONDUCAO, VOLANTE,
  COMBUSTIVEL, AUTONOMIA_COMBUSTIVEL,
  BATERIA_TRACAO, TENSAO_TRACAO, AUTONOMIA_ELETRICA,
  BATERIA_12V, TENSAO_12V,
  ...IGNICAO,
].map((s) => s.key).concat(LUZES.map((l) => l.key))
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
