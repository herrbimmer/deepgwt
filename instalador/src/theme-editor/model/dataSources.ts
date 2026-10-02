// O que o editor mostra sobre cada chave que um tema pode ler.
//
// A LISTA em si não está aqui: ela é gerada de `ThemeBridgeImpl.kt` para
// `data/generated/themeBridgeKeys.ts`, e `scripts/kb-build.mjs` derruba a build se este arquivo
// citar uma chave fora do contrato. O que está aqui é só o que uma máquina não consegue deduzir:
// rótulo em português, agrupamento, formato do valor bruto, unidade e faixa sugerida.
//
// Este arquivo existe porque o editor antigo usava `screens/gwlinkParams.ts` — a lista da tela
// GWLink, que abrange o dicionário inteiro de 818 chaves do carro. Das 57 opções oferecidas ali,
// o contrato do tema aceita 10. As outras 47 assinavam, eram descartadas em silêncio, e o
// mostrador ficava em `--` para sempre enquanto o preview do editor, com mock, mostrava o
// ponteiro andando.

import { THEME_BRIDGE } from '../../data/knowledge'
import type { ThemeBridgeKey } from '../../data/knowledge'

export type SourceGroup =
  | 'Condução' | 'Energia' | 'Clima' | 'Mídia' | 'Telefone' | 'Aplicativos' | 'Estado do painel'

export type SourceFormat = 'number' | 'enum' | 'text' | 'boolish' | 'dataUrl' | 'json'

export type SourceHint = {
  key: string
  label: string
  group: SourceGroup
  format: SourceFormat
  unit?: string
  /** Faixa e marcações sugeridas, aplicáveis a um mostrador com um clique. */
  suggested?: { min: number; max: number; majors: number[] }
  enumLabels?: Record<string, string>
  /**
   * `CONFIRMED` = o mapeamento saiu do código do app. `UNCONFIRMED` = ninguém verificou no
   * veículo qual string chega de verdade. Não é firula: `head_light_status` é UNCONFIRMED e os
   * dois temas de fábrica comparam com `'1'` na fé.
   */
  rawFormat: 'CONFIRMED' | 'UNCONFIRMED'
  /** Preenchido quando o app responde vazio hoje, por mais que a chave esteja na allowlist. */
  stub?: string
  note?: string
}

const HINTS: SourceHint[] = [
  // --- Condução ---------------------------------------------------------------------------
  { key: 'car.basic.vehicle_speed', label: 'Velocidade', group: 'Condução', format: 'number', unit: 'km/h',
    suggested: { min: 0, max: 240, majors: [0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220, 240] },
    rawFormat: 'CONFIRMED',
    note: 'Valor cru do barramento. O cluster do próprio carro mostra um número ajustado (cerca de 7% acima); um tema que exiba o cru lê um pouco menos.' },
  { key: 'car.basic.engine_speed', label: 'Rotação do motor a combustão', group: 'Condução', format: 'number', unit: 'rpm',
    suggested: { min: 0, max: 8000, majors: [0, 1000, 2000, 3000, 4000, 5000, 6000, 7000, 8000] },
    rawFormat: 'CONFIRMED' },
  { key: 'car.basic.gear_status', label: 'Marcha', group: 'Condução', format: 'enum',
    enumLabels: { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' }, rawFormat: 'CONFIRMED',
    note: 'Qualquer valor fora do mapa cai em N, que é como o próprio app trata.' },
  { key: 'car.basic.engine_state', label: 'Estado da ignição', group: 'Condução', format: 'enum',
    enumLabels: { '-1': 'Desconhecido', '10': 'ACC', '11': 'Ignição', '12': 'Partida', '13': 'Rodando', '14': 'Espera', '15': 'Dormindo' },
    rawFormat: 'UNCONFIRMED',
    note: 'O próprio enum no código do app diz que o mapeamento é palpite. E não significa "motor a combustão girando": num híbrido fica em 13 o tempo todo.' },
  { key: 'car.basic.total_odometer', label: 'Odômetro total', group: 'Condução', format: 'number', unit: 'km', rawFormat: 'CONFIRMED' },
  { key: 'car.basic.instant_fuel_consumption', label: 'Consumo instantâneo', group: 'Condução', format: 'json',
    rawFormat: 'UNCONFIRMED',
    note: 'Chega como "{métrica,consumo}". Métrica 4 é marcha lenta, em L/h, e não converte para km/l.' },
  { key: 'car.drive_setting.drive_mode', label: 'Modo de condução', group: 'Condução', format: 'enum',
    enumLabels: { '0': 'Normal', '1': 'Sport', '2': 'Eco', '3': 'Neve', '4': 'Areia', '5': 'Lama', '11': 'AWD' },
    rawFormat: 'CONFIRMED' },
  { key: 'car.drive_setting.steering_wheel_assist_mode', label: 'Assistência de direção', group: 'Condução', format: 'enum',
    enumLabels: { '0': 'Normal', '1': 'Esportiva', '2': 'Conforto' }, rawFormat: 'CONFIRMED' },
  { key: 'car.drive_setting.esp_enable', label: 'ESP ligado', group: 'Condução', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'car.basic.head_light_status', label: 'Farol aceso', group: 'Condução', format: 'boolish',
    rawFormat: 'UNCONFIRMED',
    note: 'O formato real nunca foi verificado no carro. O motor aceita 1, true, on, yes e ligado — não compare com "1" na mão.' },
  { key: 'car.map.tsr.nav_speed_limit', label: 'Limite da via (placa)', group: 'Condução', format: 'number', unit: 'km/h',
    suggested: { min: 0, max: 130, majors: [0, 30, 50, 60, 80, 100, 120] },
    rawFormat: 'UNCONFIRMED',
    note: 'Limite da via lido pela câmera (TSR). A chave existe no barramento e o app já a conhece, mas o valor bruto nunca foi lido deste veículo: pode chegar em km/h, em código de placa, ou como 0 quando não há placa. Confirme no carro antes de depender dela.' },
  { key: 'car.map.tsr.nav_speed_limit_sign_status', label: 'Placa de limite válida', group: 'Condução', format: 'boolish',
    rawFormat: 'UNCONFIRMED',
    note: 'Serve para distinguir "a via é 40" de "a câmera perdeu a placa e o número ficou velho". O formato real ainda não foi verificado no veículo.' },
  { key: 'car.basic.inside_temp', label: 'Temperatura interna', group: 'Condução', format: 'number', unit: '°C',
    suggested: { min: -10, max: 50, majors: [-10, 0, 10, 20, 30, 40, 50] }, rawFormat: 'CONFIRMED',
    note: 'O carro usa -1, 255 e "--" como valor inválido. O motor descarta os três.' },
  { key: 'car.basic.outside_temp', label: 'Temperatura externa', group: 'Condução', format: 'number', unit: '°C',
    suggested: { min: -20, max: 50, majors: [-20, -10, 0, 10, 20, 30, 40, 50] }, rawFormat: 'CONFIRMED',
    note: 'Mesmos valores inválidos da temperatura interna.' },
  { key: 'car.configure.default_temp_unit', label: 'Unidade de temperatura', group: 'Condução', format: 'enum',
    enumLabels: { '1': '°F' }, rawFormat: 'CONFIRMED', note: '1 é Fahrenheit; qualquer outra coisa é Celsius.' },

  // --- Energia ----------------------------------------------------------------------------
  { key: 'car.basic.remain_fuel_percentage', label: 'Combustível restante', group: 'Energia', format: 'number', unit: '%',
    suggested: { min: 0, max: 100, majors: [0, 25, 50, 75, 100] }, rawFormat: 'CONFIRMED' },
  { key: 'car.ev_info.cur_battery_power_percentage', label: 'Carga da bateria', group: 'Energia', format: 'number', unit: '%',
    suggested: { min: 0, max: 100, majors: [0, 25, 50, 75, 100] }, rawFormat: 'CONFIRMED' },
  { key: 'car.ev_info.electric_mode_remain_odometer', label: 'Autonomia elétrica', group: 'Energia', format: 'number', unit: 'km', rawFormat: 'CONFIRMED' },
  { key: 'car.ev_info.fuel_mode_remain_odometer', label: 'Autonomia a combustível', group: 'Energia', format: 'number', unit: 'km', rawFormat: 'CONFIRMED' },
  { key: 'car.ev_info.energy_output_percentage', label: 'Potência e regeneração', group: 'Energia', format: 'number', unit: '%',
    suggested: { min: -100, max: 100, majors: [-100, -50, 0, 20, 40, 60, 80, 100] }, rawFormat: 'CONFIRMED',
    note: 'O único valor com sinal do contrato: negativo é frenagem regenerativa.' },
  { key: 'car.ev_info.power_battery_voltage', label: 'Tensão da bateria de tração', group: 'Energia', format: 'number', unit: 'V',
    suggested: { min: 250, max: 450, majors: [250, 300, 350, 400, 450] }, rawFormat: 'CONFIRMED' },
  { key: 'car.ev_info.cur_charge_current', label: 'Corrente de carga', group: 'Energia', format: 'number', unit: 'A',
    suggested: { min: -300, max: 300, majors: [-300, -150, 0, 150, 300] }, rawFormat: 'CONFIRMED' },
  { key: 'car.ev_info.Instant_energy_consumption', label: 'Consumo instantâneo de energia', group: 'Energia', format: 'number', unit: 'kWh/100km', rawFormat: 'UNCONFIRMED',
    note: 'O nome da chave tem I maiúsculo no carro. Não é erro de digitação aqui.' },
  { key: 'car.ev_setting.power_model_config', label: 'Modo de energia', group: 'Energia', format: 'enum',
    enumLabels: { '0': 'HEV', '1': 'EVP', '3': 'EV' }, rawFormat: 'CONFIRMED', note: 'O valor 2 não tem rótulo no app.' },
  { key: 'car.ev_setting.energy_recovery_level', label: 'Nível de regeneração', group: 'Energia', format: 'enum',
    enumLabels: { '0': 'Normal', '1': 'Alto', '2': 'Baixo' }, rawFormat: 'CONFIRMED',
    note: 'A numeração é invertida mesmo: a constante chamada NORMAL vale 2 e o rótulo de 2 é Baixo.' },
  { key: 'car.ev.setting.pedal_control_enable', label: 'Pedal único', group: 'Energia', format: 'boolish', rawFormat: 'UNCONFIRMED',
    note: 'Esta chave usa pontos onde as vizinhas usam sublinhado: ev.setting, não ev_setting.' },

  // --- Clima ------------------------------------------------------------------------------
  { key: 'car.hvac.power_mode', label: 'Ar-condicionado ligado', group: 'Clima', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'car.hvac.fan_speed', label: 'Velocidade do ventilador', group: 'Clima', format: 'number',
    suggested: { min: 0, max: 7, majors: [0, 1, 2, 3, 4, 5, 6, 7] }, rawFormat: 'CONFIRMED', note: '0 a 7; zero implica desligado.' },
  { key: 'car.hvac.driver_temperature', label: 'Temperatura do motorista', group: 'Clima', format: 'number', unit: '°C',
    suggested: { min: 16, max: 32, majors: [16, 20, 24, 28, 32] }, rawFormat: 'CONFIRMED', note: 'De 16,0 a 32,0 em passos de 0,5.' },
  { key: 'car.hvac.cycle_mode', label: 'Recirculação', group: 'Clima', format: 'enum', enumLabels: {}, rawFormat: 'UNCONFIRMED' },
  { key: 'car.hvac.auto_enable', label: 'Clima automático', group: 'Clima', format: 'boolish', rawFormat: 'UNCONFIRMED' },
  { key: 'car.hvac.anion_enable', label: 'Ionizador', group: 'Clima', format: 'boolish', rawFormat: 'UNCONFIRMED' },

  // --- Mídia ------------------------------------------------------------------------------
  { key: 'app.media.state', label: 'Estado da reprodução', group: 'Mídia', format: 'enum',
    enumLabels: { playing: 'Tocando', paused: 'Pausado' }, rawFormat: 'CONFIRMED' },
  { key: 'app.media.title', label: 'Título da faixa', group: 'Mídia', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.media.artist', label: 'Artista', group: 'Mídia', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.media.album', label: 'Álbum', group: 'Mídia', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.media.album_art', label: 'Capa do álbum', group: 'Mídia', format: 'dataUrl', rawFormat: 'CONFIRMED',
    note: 'Chega como data:image/png;base64. Funciona, apesar de o guia de autoria dizer que é stub.' },
  { key: 'app.media.duration', label: 'Duração da faixa', group: 'Mídia', format: 'text', rawFormat: 'CONFIRMED',
    stub: 'O app devolve string vazia para esta chave hoje.' },
  { key: 'app.media.position', label: 'Posição na faixa', group: 'Mídia', format: 'text', rawFormat: 'CONFIRMED',
    stub: 'O app devolve string vazia para esta chave hoje.' },
  { key: 'sys.radio.cur_channel_info', label: 'Estação de rádio', group: 'Mídia', format: 'json', rawFormat: 'UNCONFIRMED',
    note: 'Chega como tupla, por exemplo "94900,0,1,1". O primeiro campo é a frequência em kHz.' },

  // --- Telefone ---------------------------------------------------------------------------
  { key: 'app.phone.state', label: 'Estado da chamada', group: 'Telefone', format: 'text', rawFormat: 'CONFIRMED',
    stub: 'O app devolve string vazia para esta chave hoje.' },
  { key: 'app.phone.caller_name', label: 'Nome de quem liga', group: 'Telefone', format: 'text', rawFormat: 'CONFIRMED',
    stub: 'O app devolve string vazia para esta chave hoje.' },
  { key: 'app.phone.caller_number', label: 'Número de quem liga', group: 'Telefone', format: 'text', rawFormat: 'CONFIRMED',
    stub: 'O app devolve string vazia para esta chave hoje.' },
  { key: 'app.phone.call_duration', label: 'Duração da chamada', group: 'Telefone', format: 'text', rawFormat: 'CONFIRMED',
    stub: 'O app devolve string vazia para esta chave hoje.' },

  // --- Aplicativos ------------------------------------------------------------------------
  { key: 'app.display.1.active_app', label: 'App na multimídia (pacote)', group: 'Aplicativos', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.display.1.active_app_label', label: 'App na multimídia (nome)', group: 'Aplicativos', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.display.1.active_app_icon', label: 'App na multimídia (ícone)', group: 'Aplicativos', format: 'dataUrl', rawFormat: 'CONFIRMED' },
  { key: 'app.display.3.active_app', label: 'App no painel (pacote)', group: 'Aplicativos', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.display.3.active_app_label', label: 'App no painel (nome)', group: 'Aplicativos', format: 'text', rawFormat: 'CONFIRMED' },
  { key: 'app.display.3.active_app_icon', label: 'App no painel (ícone)', group: 'Aplicativos', format: 'dataUrl', rawFormat: 'CONFIRMED' },
  { key: 'app.launcher.apps', label: 'Lista de aplicativos', group: 'Aplicativos', format: 'json', rawFormat: 'CONFIRMED' },
  { key: 'app.navigation.directions', label: 'Instrução de navegação', group: 'Aplicativos', format: 'json', rawFormat: 'CONFIRMED',
    stub: 'O app devolve sempre "{}" para esta chave hoje.' },

  // --- Estado do painel -------------------------------------------------------------------
  { key: 'warningActive', label: 'Aviso do veículo ativo', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED',
    note: 'Chega como texto no empurrão inicial e como booleano de verdade depois. O motor trata os dois.' },
  { key: 'warningDismissed', label: 'Aviso dispensado', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'bsdLeft', label: 'Ponto cego à esquerda', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'bsdRight', label: 'Ponto cego à direita', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'carPlayInDash', label: 'CarPlay no painel', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'projectionMirrorInDash', label: 'Espelhamento no painel', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'projectionPreparingD3', label: 'Projeção preparando o painel', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },
  { key: 'projectionCardOverlayAllowed', label: 'Sobreposição de card permitida', group: 'Estado do painel', format: 'boolish', rawFormat: 'CONFIRMED' },

  // --- Só escrita -------------------------------------------------------------------------
  { key: 'car.ev_setting.power_reserve_config', label: 'Reserva de potência', group: 'Energia', format: 'number', rawFormat: 'UNCONFIRMED' },
  { key: 'car.ev_setting.charge_soc_target_config', label: 'Alvo de carga', group: 'Energia', format: 'number', unit: '%', rawFormat: 'UNCONFIRMED' },
]

export const HINT_BY_KEY = new Map(HINTS.map((h) => [h.key, h]))

export type DataSource = ThemeBridgeKey & { hint: SourceHint }

/** Contrato e rótulos juntos, na ordem em que o carro declara as chaves. */
export const DATA_SOURCES: DataSource[] = THEME_BRIDGE.keys.map((k) => ({
  ...k,
  hint: HINT_BY_KEY.get(k.key) ?? {
    key: k.key,
    label: k.key,
    group: 'Estado do painel' as const,
    format: 'text' as const,
    rawFormat: 'UNCONFIRMED' as const,
    note: 'Chave nova no contrato do carro, ainda sem descrição no editor.',
  },
}))

export const SOURCE_BY_KEY = new Map(DATA_SOURCES.map((s) => [s.key, s]))

/** As que dá para exibir: tudo menos as de escrita sem leitura. */
export const DISPLAYABLE_SOURCES = DATA_SOURCES.filter((s) => s.channel !== 'writeOnly')

export const GROUP_ORDER: SourceGroup[] = [
  'Condução', 'Energia', 'Clima', 'Mídia', 'Telefone', 'Aplicativos', 'Estado do painel',
]
