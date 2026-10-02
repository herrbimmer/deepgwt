// Os cenários da pré-visualização: sequências de valores, não sorteio.
//
// A prévia antiga sorteava um dente de serra a cada 320 ms para cada chave do tema. Servia para
// "ver o ponteiro andar", mas não respondia a pergunta que quem desenha faz de verdade: como fica o
// painel parado à noite, na estrada acima do limite, com um aviso do carro subindo por cima. Cada
// cenário aqui é uma pequena linha do tempo com pontos-chave por chave do carro; entre dois pontos
// um número anda em linha reta e um texto troca de uma vez, como no barramento.
//
// Todas as chaves citadas estão no contrato do tema (`themeBridgeKeys`) e os valores seguem o
// formato bruto que `dataSources.ts` documenta — marcha em código ('3' é P), booleano como '1'/'0'.
// `scripts/test-theme-editor.mjs` confere as duas coisas. Telefone ficou de fora de propósito: as
// quatro chaves `app.phone.*` chegam vazias do carro hoje, e um cenário "Ligação" mostraria algo
// que o painel nunca vai receber.

import type { PaletteMode } from '../model/spec'

export type Faixa =
  /** Número: anda em linha reta entre os pontos. */
  | { tipo: 'numero'; pontos: [number, number][]; casas?: number }
  /** Texto ou código: vale o último ponto já alcançado. */
  | { tipo: 'passo'; pontos: [number, string][] }

export type CenarioDePrevia = {
  id: string
  nome: string
  descricao: string
  /** Segundos. A linha do tempo volta ao começo ao chegar no fim. */
  duracao: number
  /** Trocar para este cenário também troca o seletor Dia/Noite/Farol. */
  modo?: PaletteMode
  faixas: Record<string, Faixa>
}

export const ID_AO_VIVO = 'aoVivo'

const fixo = (v: string): Faixa => ({ tipo: 'passo', pontos: [[0, v]] })
const num = (pontos: [number, number][], casas = 0): Faixa => ({ tipo: 'numero', pontos, casas })
const passos = (pontos: [number, string][]): Faixa => ({ tipo: 'passo', pontos })

/** Capa de demonstração: um degradê em SVG, pequeno e embutido, no mesmo formato data: do carro. */
const CAPA_DEMO =
  'data:image/svg+xml;base64,' +
  (typeof btoa === 'function'
    ? btoa(
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">' +
          '<stop offset="0" stop-color="#ff5f6d"/><stop offset="1" stop-color="#3a1c71"/></linearGradient></defs>' +
          '<rect width="200" height="200" fill="url(#g)"/><circle cx="100" cy="100" r="46" fill="none" stroke="#fff" stroke-opacity=".55" stroke-width="6"/>' +
          '<circle cx="100" cy="100" r="8" fill="#fff" fill-opacity=".8"/></svg>',
      )
    : '')

/**
 * O carro "em repouso": tudo o que um cenário não mexe fica nestes valores. Sem isto, um tema com
 * leitura de autonomia mostraria "--" em todo cenário que não fosse sobre autonomia.
 */
export const BASE: Record<string, string> = {
  'car.basic.vehicle_speed': '0',
  'car.basic.engine_speed': '0',
  'car.basic.gear_status': '2',
  'car.basic.engine_state': '13',
  'car.basic.total_odometer': '12458',
  'car.basic.instant_fuel_consumption': '{1,14.5}',
  'car.basic.inside_temp': '22',
  'car.basic.outside_temp': '24',
  'car.basic.remain_fuel_percentage': '62',
  'car.basic.head_light_status': '0',
  'car.map.tsr.nav_speed_limit': '60',
  'car.map.tsr.nav_speed_limit_sign_status': '1',
  'car.configure.default_temp_unit': '0',
  'car.drive_setting.drive_mode': '0',
  'car.drive_setting.steering_wheel_assist_mode': '0',
  'car.drive_setting.esp_enable': '1',
  'car.ev_info.cur_battery_power_percentage': '71',
  'car.ev_info.electric_mode_remain_odometer': '58',
  'car.ev_info.fuel_mode_remain_odometer': '612',
  'car.ev_info.energy_output_percentage': '0',
  'car.ev_info.power_battery_voltage': '382',
  'car.ev_info.cur_charge_current': '0',
  'car.ev_info.Instant_energy_consumption': '15.2',
  'car.ev_setting.power_model_config': '0',
  'car.ev_setting.energy_recovery_level': '0',
  'car.ev.setting.pedal_control_enable': '0',
  'car.hvac.power_mode': '1',
  'car.hvac.fan_speed': '2',
  'car.hvac.driver_temperature': '22',
  'car.hvac.auto_enable': '1',
  'app.media.state': 'paused',
  'app.media.title': '',
  'app.media.artist': '',
  'app.media.album': '',
  'app.media.album_art': '',
  warningActive: 'false',
}

export const CENARIOS: CenarioDePrevia[] = [
  {
    id: 'parado',
    nome: 'Parado',
    descricao: 'Em P, 0 km/h, motor desligado. O painel como fica na garagem.',
    duracao: 10,
    faixas: {
      'car.basic.gear_status': fixo('3'),
      'car.basic.vehicle_speed': fixo('0'),
      'car.basic.engine_speed': fixo('0'),
    },
  },
  {
    id: 'cidade',
    nome: 'Cidade',
    descricao: 'Anda e para entre 0 e 60 km/h. Regenera nas frenagens.',
    duracao: 36,
    faixas: {
      'car.basic.vehicle_speed': num([[0, 0], [5, 38], [9, 52], [13, 48], [17, 18], [20, 0], [23, 0], [28, 44], [32, 58], [36, 0]]),
      'car.basic.engine_speed': num([[0, 0], [4, 0], [5, 1450], [9, 1900], [13, 1600], [16, 0], [27, 0], [28, 1500], [32, 2200], [34, 0], [36, 0]]),
      'car.ev_info.energy_output_percentage': num([[0, 0], [3, 34], [9, 18], [14, 6], [16, -28], [20, 0], [26, 0], [28, 38], [32, 22], [34, -35], [36, 0]]),
      'car.ev_info.cur_battery_power_percentage': num([[0, 71], [36, 69]]),
      'car.ev_info.Instant_energy_consumption': num([[0, 12], [9, 18.4], [17, 9.6], [32, 21.3], [36, 11]], 1),
      'car.map.tsr.nav_speed_limit': passos([[0, '60'], [22, '40'], [30, '60']]),
    },
  },
  {
    id: 'estrada',
    nome: 'Estrada',
    descricao: 'Entre 100 e 120 km/h; a placa cai para 80 no meio do trecho.',
    duracao: 30,
    faixas: {
      'car.basic.vehicle_speed': num([[0, 104], [8, 118], [14, 112], [18, 96], [24, 84], [30, 104]]),
      'car.basic.engine_speed': num([[0, 2100], [8, 2550], [14, 2400], [18, 2000], [24, 1850], [30, 2100]]),
      'car.ev_info.energy_output_percentage': num([[0, 24], [8, 42], [14, 30], [18, -12], [24, 12], [30, 24]]),
      'car.map.tsr.nav_speed_limit': passos([[0, '110'], [16, '80'], [27, '110']]),
      'car.basic.remain_fuel_percentage': num([[0, 58], [30, 57]]),
    },
  },
  {
    id: 'arrancada',
    nome: 'Arrancada',
    descricao: 'Modo Sport, 0 a 100 km/h com rotação e potência no máximo.',
    duracao: 14,
    faixas: {
      'car.drive_setting.drive_mode': fixo('1'),
      'car.basic.vehicle_speed': num([[0, 0], [1, 0], [3, 38], [5, 66], [8, 100], [10, 104], [13, 60], [14, 0]]),
      'car.basic.engine_speed': num([[0, 900], [1, 1200], [3, 5200], [4, 3900], [5, 5600], [6, 4300], [8, 5900], [10, 2600], [13, 1200], [14, 900]]),
      'car.ev_info.energy_output_percentage': num([[0, 0], [1, 100], [8, 100], [9, 40], [11, -60], [14, 0]]),
    },
  },
  {
    id: 'noite',
    nome: 'Noite',
    descricao: 'Farol aceso, cidade devagar. Liga o seletor em Farol.',
    duracao: 24,
    modo: 'headlight',
    faixas: {
      'car.basic.head_light_status': fixo('1'),
      'car.basic.vehicle_speed': num([[0, 0], [6, 34], [12, 42], [18, 20], [24, 0]]),
      'car.basic.engine_speed': num([[0, 0], [6, 1300], [12, 1500], [17, 0], [24, 0]]),
      'car.basic.outside_temp': fixo('17'),
    },
  },
  {
    id: 'alerta',
    nome: 'Alerta',
    descricao: 'Combustível na reserva, ESP desligado, acima do limite e um aviso do carro.',
    duracao: 20,
    faixas: {
      'car.basic.remain_fuel_percentage': fixo('9'),
      'car.ev_info.cur_battery_power_percentage': fixo('14'),
      'car.drive_setting.esp_enable': passos([[0, '1'], [3, '0']]),
      'car.basic.vehicle_speed': num([[0, 52], [6, 71], [12, 74], [20, 58]]),
      'car.basic.engine_speed': num([[0, 1700], [6, 2300], [20, 1900]]),
      'car.map.tsr.nav_speed_limit': fixo('60'),
      // O aviso do carro faz o tema sair da frente (o body fica transparente). A prévia mostra
      // isso de verdade, com um rótulo por cima do canvas explicando o vazio.
      warningActive: passos([[0, 'false'], [13, 'true'], [17, 'false']]),
    },
  },
  {
    id: 'midia',
    nome: 'Mídia tocando',
    descricao: 'Uma faixa tocando com capa, andando na cidade.',
    duracao: 24,
    faixas: {
      'app.media.state': fixo('playing'),
      'app.media.title': passos([[0, 'Noite de Estrada'], [12, 'Faixa com um título bem mais comprido que a caixa']]),
      'app.media.artist': passos([[0, 'Banda de Demonstração'], [12, 'Outro Artista']]),
      'app.media.album': fixo('Álbum de Exemplo'),
      'app.media.album_art': fixo(CAPA_DEMO),
      'car.basic.vehicle_speed': num([[0, 22], [8, 46], [16, 38], [24, 22]]),
      'car.basic.engine_speed': num([[0, 0], [7, 1500], [16, 1400], [22, 0], [24, 0]]),
    },
  },
  {
    id: 'eletrico',
    nome: 'Só elétrico',
    descricao: 'Modo EV: rotação zerada, bateria e autonomia elétrica em foco.',
    duracao: 20,
    faixas: {
      'car.ev_setting.power_model_config': fixo('3'),
      'car.basic.engine_speed': fixo('0'),
      'car.basic.vehicle_speed': num([[0, 0], [6, 42], [12, 55], [16, 20], [20, 0]]),
      'car.ev_info.energy_output_percentage': num([[0, 0], [5, 46], [12, 20], [15, -40], [20, 0]]),
      'car.ev_info.cur_battery_power_percentage': num([[0, 64], [20, 62]]),
      'car.ev_info.electric_mode_remain_odometer': num([[0, 51], [20, 49]]),
    },
  },
]

export const CENARIO_POR_ID = new Map(CENARIOS.map((c) => [c.id, c]))

function valorDaFaixa(f: Faixa, t: number): string {
  if (f.tipo === 'passo') {
    let v = f.pontos[0][1]
    for (const [tp, vp] of f.pontos) if (tp <= t) v = vp
    return v
  }
  const p = f.pontos
  const casas = f.casas ?? 0
  const fmt = (n: number) => (casas ? n.toFixed(casas) : String(Math.round(n)))
  if (t <= p[0][0]) return fmt(p[0][1])
  for (let i = 1; i < p.length; i++) {
    const [t1, v1] = p[i]
    if (t <= t1) {
      const [t0, v0] = p[i - 1]
      const f01 = t1 === t0 ? 1 : (t - t0) / (t1 - t0)
      return fmt(v0 + (v1 - v0) * f01)
    }
  }
  return fmt(p[p.length - 1][1])
}

/** Todos os valores do carro num instante do cenário, já no formato bruto do barramento. */
export function valoresNoInstante(c: CenarioDePrevia, t: number): Record<string, string> {
  const saida: Record<string, string> = { ...BASE }
  for (const [chave, faixa] of Object.entries(c.faixas)) saida[chave] = valorDaFaixa(faixa, t)
  return saida
}

/** Faixa da velocidade do cenário, para a legenda da linha do tempo ("0–60 km/h"). */
export function faixaDeVelocidade(c: CenarioDePrevia): [number, number] | null {
  const f = c.faixas['car.basic.vehicle_speed']
  if (!f) return null
  if (f.tipo === 'passo') {
    const n = Number(f.pontos[0][1])
    return [n, n]
  }
  const vs = f.pontos.map((p) => p[1])
  return [Math.min(...vs), Math.max(...vs)]
}
