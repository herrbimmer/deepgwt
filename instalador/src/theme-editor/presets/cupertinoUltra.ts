// Cupertino Ultra, recriado como `ThemeSpec` editável.
//
// Não é uma cópia dos bytes do `app.html` de fábrica: é o mesmo desenho expresso em dados, que o
// motor monta. O usuário escolheu assim justamente para poder abrir, mexer e salvar — um clone
// byte a byte seria fiel e inerte.
//
// Toda geometria daqui foi lida do tema instalado
// (`app/src/main/assets/PreinstalledThemes/cupertinoultra/app.html`). Os números não são
// aproximações: o mostrador esquerdo está em `left: 45px` com `SPEED_CX = 290`, logo o centro
// absoluto é 335; o bisel é `SPEED_R + 15 = 265`, que é exatamente metade da pegada `lg` da
// tabela de tamanhos. As duas diferenças deliberadas em relação ao original estão comentadas
// onde aparecem.

import type { ThemeSpec } from '../model/spec'
import { text } from '../model/defaults'

/** 1 de janeiro de 2026. Fixo para que o preset seja uma constante de verdade. */
const EPOCA = 1767225600000

const ZOOM = { enabled: true, scale: 1.5, weight: 700, durationMs: 380, easing: 'cubic-bezier(.22,.61,.36,1)' }

/** Ponteiro claro e fino, com sombra dupla. Igual nos dois mostradores. */
const PONTEIRO = {
  shape: 'line' as const,
  innerR: 20,
  outerInset: 54,
  width: 4,
  paint: { fill: 'solid' as const, color: '$needle' },
  cap: 'ringDot' as const,
  glow: true,
}

/** Disco verde com brilho deslocado para cima e à esquerda, anel de 9 px e bisel preto de 10 px. */
const DISCO = {
  enabled: true,
  paint: { fill: 'radial' as const, from: '$face', to: '$faceLow', cx: 0.34, cy: 0.28, stopAt: 0.78 },
  ring: { width: 9, color: '$faceRing' },
  bezel: { width: 10, color: '$bezel' },
}

const MARCACOES = {
  subdivisions: 4,
  showLabels: true,
  labelStyle: text(24, 500, '$ink', { tracking: 0.2 }),
  labelInset: 40,
  len: { major: 18, minor: 11, micro: 7 },
  width: { major: 3, minor: 1.6, micro: 1.2 },
}

export const CUPERTINO_ULTRA: ThemeSpec = {
  schema: 2,
  id: 'preset-cupertino-ultra',
  folderName: 'cupertino-ultra',
  name: 'Cupertino Ultra',
  description:
    'Painel digital de mostradores redondos: velocímetro à esquerda e conta-giros à direita, ' +
    'disco verde com arco de progresso e ponteiro flutuante sobre moldura preta, marcha e modo de ' +
    'condução no conta-giros, card de mídia no centro, farolete e odômetro na base.',
  version: '1.2.0',
  createdAt: EPOCA,
  updatedAt: EPOCA,
  origin: null,

  canvas: { width: 1920, height: 720 },

  background: {
    mode: 'solid',
    presetId: 'midnight',
    solid: '#08090b',
    gradientFrom: '#08090b',
    gradientTo: '#14161a',
    customPath: '',
    customDataUrl: '',
    passthrough: null,
  },

  // As duas famílias de tinta do original (`--bezel-ink` sobre a moldura preta e `--face-ink`
  // sobre o disco) são quase idênticas de dia e divergem à noite. Aqui viram um token só, com os
  // valores do disco, que é onde há mais texto.
  palette: {
    headlightFromHostVars: true,
    tokens: {
      ink: { label: 'Texto principal', day: '#f3f7f4', night: '#ffb3ac' },
      inkDim: { label: 'Texto secundário', day: 'rgba(243,247,244,.6)', night: 'rgba(255,179,172,.6)' },
      inkFaint: { label: 'Texto apagado', day: 'rgba(243,247,244,.3)', night: 'rgba(255,179,172,.3)' },
      accent: { label: 'Destaque', day: '#34e871', night: '#34e871' },
      needle: { label: 'Ponteiro', day: '#f7fbf8', night: '#ff6a61' },
      needleGlow: { label: 'Sombra do ponteiro', day: 'rgba(0,0,0,.45)', night: 'rgba(255,59,48,.5)' },
      warn: { label: 'Alerta', day: '#ff453a', night: '#ff453a' },
      ok: { label: 'Positivo', day: '#34e871', night: '#34e871' },
      low: { label: 'Nível baixo', day: '#ff9500', night: '#ff9500' },
      face: { label: 'Disco (centro)', day: '#3c6b52', night: '#1c3a2a' },
      faceLow: { label: 'Disco (borda)', day: '#0e2117', night: '#06110b' },
      faceRing: { label: 'Anel do disco', day: 'rgba(120,200,160,.35)', night: 'rgba(255,59,48,.22)' },
      bezel: { label: 'Bisel', day: '#05060a', night: '#05060a' },
      panel: { label: 'Painel de fundo', day: 'rgba(255,255,255,.06)', night: 'rgba(255,59,48,.08)' },
      track: { label: 'Trilho da barra', day: 'rgba(255,255,255,.12)', night: 'rgba(255,59,48,.16)' },
    },
  },

  // Nenhuma regra de cor: os dois temas de fábrica não têm. O editor oferece a lista de condições
  // prontas para quem quiser acrescentar.
  colorRules: [],

  fonts: ['sysSans'],
  displayModeConfig: true,
  speedScaleConfig: true,

  nativeMasks: {
    fuel: { enabled: false, x: 0, y: 520, width: 380, height: 200, opacity: 1 },
    battery: { enabled: false, x: 1540, y: 520, width: 380, height: 200, opacity: 1 },
    speed: { enabled: true, x: 0, y: 0, width: 640, height: 720, opacity: 1 },
    info: { enabled: true, x: 1280, y: 0, width: 640, height: 720, opacity: 1 },
    topCenter: { enabled: true, x: 660, y: 0, width: 600, height: 90, opacity: 1 },
  },
  appDefaultPosition: { x: 640, y: 95, width: 640, height: 560 },

  boot: { enabled: true, wordmark: 'CUPERTINO ULTRA', sweepMs: 1300 },

  pages: [
    {
      id: 'cup-page-1',
      name: 'Principal',
      elements: [
        // --- mostrador esquerdo: velocidade -------------------------------------------------
        {
          id: 'cup-dial-speed',
          kind: 'dial',
          name: 'Velocímetro',
          style: 'faceNeedle',
          place: { zone: 'left', anchor: 'center', at: { x: 335, y: 400 }, size: 'lg', scale: 1, z: 1, opacity: 1 },
          visible: { when: 'always' },
          sweep: { startDeg: -125, endDeg: 125 },
          binding: { key: 'car.basic.vehicle_speed', decimals: 0, unit: '', fallback: '0' },
          // A faixa curta dá resolução no uso normal; acima de 120 km/h o mostrador abre para 260
          // e só volta abaixo de 100. As duas bordas diferentes são o que impede a escala de
          // piscar com o carro cravado em 120.
          map: { kind: 'dualRange', min: 0, low: 120, high: 260, upAt: 120, downAt: 100, rescaleMs: 650 },
          face: DISCO,
          ticks: {
            ...MARCACOES,
            majors: [0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220, 240, 260],
            zones: [],
          },
          needle: PONTEIRO,
          arc: { enabled: true, radiusOffset: 7, width: 7, quantizeDeg: 1, split: null },
          zoom: ZOOM,
          // O original escreve o número no miolo do mostrador, e ali é onde o veículo desenha a
          // placa de limite de velocidade. A leitura sai para um elemento próprio, logo abaixo da
          // zona — ver `cup-speed-value`.
          center: null,
        },
        {
          id: 'cup-speed-value',
          kind: 'readout',
          name: 'Velocidade',
          style: 'value',
          place: { zone: 'left', anchor: 'topCenter', at: { x: 305, y: 566 }, size: 'md', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.vehicle_speed', decimals: 0, unit: '', fallback: '0' },
          map: { kind: 'linear', min: 0, max: 260 },
          text: text(80, 500, '$ink', { hostScaled: true, tracking: -1 }),
          label: 'km/h',
          labelStyle: text(14, 600, '$inkDim', { tracking: 3, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'cup-fuel-range',
          kind: 'readout',
          name: 'Autonomia a combustível',
          style: 'stat',
          place: { zone: 'left', anchor: 'center', at: { x: 305, y: 682 }, size: 'sm', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_info.fuel_mode_remain_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 900 },
          text: text(19, 500, '$inkDim'),
          label: '',
          labelStyle: text(12, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: 'fuel',
        },
        {
          id: 'cup-park-badge',
          kind: 'indicator',
          name: 'Freio de estacionamento',
          style: 'badge',
          place: { zone: 'left', anchor: 'center', at: { x: 179, y: 134 }, size: 'sm', scale: 1, z: 4, opacity: 1 },
          // O original assina `car.basic.hand_brake_status`, que o contrato do tema NÃO aceita:
          // o ícone nunca acendeu no carro. Aqui o selo sai da marcha, que é a mesma informação
          // por um caminho que funciona.
          visible: { when: 'equals', key: 'car.basic.gear_status', value: '3' },
          binding: { key: 'car.basic.gear_status', fallback: '' },
          map: { kind: 'enum', labels: { '3': 'P' }, fallback: '' },
          icon: null,
          onPaint: { fill: 'solid', color: '$warn' },
          offPaint: { fill: 'solid', color: '$inkFaint' },
          text: text(16, 700, '$warn', { tabular: false }),
        },

        // --- mostrador direito: rotação -----------------------------------------------------
        {
          id: 'cup-dial-tach',
          kind: 'dial',
          name: 'Conta-giros',
          style: 'faceNeedle',
          place: { zone: 'right', anchor: 'center', at: { x: 1585, y: 400 }, size: 'lg', scale: 1, z: 1, opacity: 1 },
          visible: { when: 'always' },
          sweep: { startDeg: -125, endDeg: 125 },
          // A escala é em milhares de rpm; `scale: 0.001` faz a conversão antes do mapa, então as
          // marcações são 0 a 8 e não 0 a 8000.
          binding: { key: 'car.basic.engine_speed', transform: { scale: 0.001 }, decimals: 1, unit: '', fallback: '0' },
          map: { kind: 'linear', min: 0, max: 8 },
          face: DISCO,
          ticks: {
            ...MARCACOES,
            majors: [0, 1, 2, 3, 4, 5, 6, 7, 8],
            zones: [
              { from: 0, to: 6, token: '$ok' },
              { from: 6, to: 8, token: '$warn' },
            ],
          },
          needle: PONTEIRO,
          arc: {
            enabled: true, radiusOffset: 7, width: 7, quantizeDeg: 1,
            split: { at: 6, beforeToken: '$ok', afterToken: '$warn' },
          },
          zoom: ZOOM,
          center: null,
        },
        {
          id: 'cup-gear',
          kind: 'readout',
          name: 'Marcha',
          style: 'value',
          place: { zone: 'right', anchor: 'center', at: { x: 1615, y: 535 }, size: 'md', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.gear_status', fallback: 'P' },
          map: { kind: 'enum', labels: { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' }, fallback: 'N' },
          text: text(80, 600, '$ink', { hostScaled: true, tracking: -1, tabular: false }),
          label: '',
          labelStyle: text(15, 700, '$inkDim', { tracking: 3, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'cup-drive-mode',
          kind: 'readout',
          name: 'Modo de condução',
          style: 'text',
          place: { zone: 'right', anchor: 'center', at: { x: 1615, y: 590 }, size: 'xs', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.drive_setting.drive_mode', fallback: 'NORMAL' },
          map: {
            kind: 'enum',
            labels: { '0': 'Normal', '1': 'Sport', '2': 'Eco', '3': 'Neve', '4': 'Areia', '5': 'Lama', '11': 'AWD' },
            fallback: 'Normal',
          },
          text: text(15, 700, '$inkDim', { tracking: 3, transform: 'upper', tabular: false }),
          label: '',
          labelStyle: text(12, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },

        // --- centro e base ------------------------------------------------------------------
        {
          id: 'cup-media',
          kind: 'media',
          name: 'Tocando agora',
          style: 'card',
          place: { zone: 'centre', anchor: 'center', at: { x: 980, y: 375 }, size: 'xl', scale: 1, z: 0, opacity: 1 },
          visible: { when: 'always' },
          showArtwork: true,
          title: text(27, 600, '$ink', { tabular: false }),
          artist: text(20, 500, '$inkDim', { tabular: false }),
          panel: { fill: 'none' },
        },
        {
          id: 'cup-out-temp',
          kind: 'readout',
          name: 'Temperatura externa',
          style: 'value',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 960, y: 45 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.outside_temp', decimals: 0, unit: '°', fallback: '--°' },
          map: { kind: 'linear', min: -20, max: 50 },
          text: text(32, 400, '$ink', { tracking: 0.2 }),
          label: '',
          labelStyle: text(12, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'cup-odometer',
          kind: 'readout',
          name: 'Odômetro',
          style: 'value',
          place: { zone: 'free', anchor: 'center', at: { x: 1005, y: 686 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.total_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 999999 },
          text: text(20, 500, '$inkDim'),
          label: '',
          labelStyle: text(12, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
      ],
    },
  ],
}
