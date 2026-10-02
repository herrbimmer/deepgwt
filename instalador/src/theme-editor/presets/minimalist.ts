// Minimalist — o terceiro preset da galeria, e o primeiro sem nenhum mostrador redondo.
//
// Desenho pedido pelo usuário a partir de uma foto de painel: fundo preto, número de velocidade
// grande e fino à esquerda, barra bipolar de potência logo abaixo, card de mídia discreto à
// direita, e uma faixa de status rente à base. Nada de disco, anel, bisel ou ponteiro.
//
// Duas decisões que o leitor vai querer entender antes de mexer nas coordenadas:
//
// 1. VELOCIDADE À ESQUERDA, MÍDIA NO CANTO DIREITO, OS DOIS NO MEIO DA ALTURA. O canto esquerdo
//    tem um dono no meio da altura: as três zonas que o OEM compõe por cima do tema (READY, placa
//    de velocidade e ESP) ocupam x 225-430, y 315-565. Em 2026-09-22 o bloco da velocidade foi para
//    o canto de cima (acima do READY); em 2026-09-23 o pedido foi descer e centralizar na vertical,
//    e entre as duas saídas possíveis — ficar no canto e descer só 20 px, ou sair de cima do READY
//    pela direita e descer de verdade — o usuário escolheu a segunda, vendo as duas renderizadas.
//    Então: número em x 350-830, y 220-380 (a borda esquerda passa 8 px da zona do READY, que
//    termina em 342), barra em y 381-423 (a zona do ESP só começa em y 445). A mídia fica no canto
//    direito, onde nada do carro atrapalha, com o centro na mesma altura do bloco da velocidade.
//    Nenhum elemento aqui é apenas "legal" por sorte — ver o teste em
//    `scripts/test-theme-engine.mjs`, que roda `firstBlocker` em todos eles.
//
// 2. O CENTRO FICA VAZIO DE PROPÓSITO. Na foto o miolo do painel traz a renderização do carro,
//    que é desenho do cluster OEM e não do tema. Como o WebView do tema fica ACIMA do cluster,
//    qualquer fundo opaco cobre aquela renderização — então ela só reapareceria com o fundo do
//    body transparente, e o que o cluster OEM deste veículo desenha no centro nunca foi
//    verificado. O preset não aposta nisso: mantém o preto sólido e marca o centro com um halo
//    de decoração, que é a poça de luz da foto sem fingir ser o carro.

import type { ThemeSpec } from '../model/spec'
import { text } from '../model/defaults'

/** 1 de janeiro de 2026. Fixo para que o preset seja uma constante de verdade. */
const EPOCA = 1767225600000

/**
 * Cor que segue a preferência de modo noturno do app quando o farol acende.
 *
 * `themeCss.ts` faz isso sozinho para `ink`, `accent` e `needle`; para um token próprio do tema é
 * preciso escrever o valor. Sem isto, `speedInk` e `fuelInk` continuariam brancos com o farol
 * aceso enquanto o resto do painel vira âmbar.
 */
const TINTA_DO_HOST = 'var(--night-tint, #ff9500)'

/** Rótulo pequeno em caixa alta. Repetido em quase todo elemento da faixa de status. */
const MICRO = text(13, 600, '$inkDim', { tracking: 2.4, transform: 'upper', tabular: false })

/**
 * O mesmo rótulo, aceso. Nas duas autonomias o ícone (bomba, bateria) pega a cor do rótulo, e com
 * `MICRO` ele ficava a 55%: o número claro ao lado de um ícone apagado lia como item desativado.
 */
const MICRO_ACESO = text(13, 600, '$ink', { tracking: 2.4, transform: 'upper', tabular: false })

export const MINIMALIST: ThemeSpec = {
  schema: 2,
  id: 'preset-minimalist',
  // NÃO é 'minimalist'. Esse nome está em `RETIRED_THEME_FOLDERS` do ThemeManager (um Minimalist
  // antigo, de outra geração de temas, que saiu do APK): o app apaga a pasta com esse nome em TODA
  // abertura, antes de semear. Com 'minimalist' este tema seria apagado e recopiado do APK a cada
  // início do app. Tirar o nome da lista não resolve: aí um carro que ainda guarde o antigo com
  // versão maior ficaria com o antigo, porque o seed respeita versão de disco mais nova.
  folderName: 'minimalista',
  name: 'Minimalist',
  description:
    'Painel sem mostradores: velocidade em número grande e fino à esquerda, barra bipolar de ' +
    'potência e regeneração abaixo dela, marcha e modo de energia no topo, card de mídia à ' +
    'direita e faixa de autonomia, modo de condução e carga na base. Os números ficam vermelhos ' +
    'acima do limite da via lido pela câmera.',
  version: '1.0.0',
  createdAt: EPOCA,
  updatedAt: EPOCA,
  origin: null,

  canvas: { width: 1920, height: 720 },

  background: {
    mode: 'solid',
    presetId: 'midnight',
    solid: '#000000',
    gradientFrom: '#000000',
    gradientTo: '#0a0c10',
    customPath: '',
    customDataUrl: '',
    passthrough: null,
  },

  // Os tokens de mostrador (face, faceLow, faceRing, bezel, needle, needleGlow) não são usados por
  // nenhum elemento deste tema. Ficam na paleta assim mesmo: quem clonar o Minimalist e acrescentar
  // um mostrador redondo no editor encontra as cores prontas em vez de um disco cinza.
  palette: {
    headlightFromHostVars: true,
    tokens: {
      ink: { label: 'Texto principal', day: '#f5f7fa', night: '#ff3b30' },
      inkDim: { label: 'Texto secundário', day: 'rgba(245,247,250,.55)', night: 'rgba(255,59,48,.6)' },
      inkFaint: { label: 'Texto apagado', day: 'rgba(245,247,250,.26)', night: 'rgba(255,59,48,.3)' },
      speedInk: { label: 'Número da velocidade', day: '#ffffff', night: '#ff3b30', headlight: TINTA_DO_HOST },
      // Cheio de dia (era 72%): a autonomia é leitura de relance, não rodapé. Pedido de 2026-09-23.
      fuelInk: { label: 'Autonomia e combustível', day: '#f5f7fa', night: '#ff3b30', headlight: TINTA_DO_HOST },
      accent: { label: 'Destaque', day: '#4a7bd4', night: '#ff453a' },
      needle: { label: 'Ponteiro', day: '#f5f7fa', night: '#ff6a61' },
      needleGlow: { label: 'Sombra do ponteiro', day: 'rgba(0,0,0,.45)', night: 'rgba(255,59,48,.5)' },
      warn: { label: 'Alerta', day: '#ff453a', night: '#ff453a' },
      ok: { label: 'Positivo', day: '#6fdc8c', night: '#6fdc8c' },
      low: { label: 'Nível baixo', day: '#ff9500', night: '#ff9500' },
      face: { label: 'Fundo do mostrador', day: '#15181d', night: '#120e0e' },
      faceLow: { label: 'Borda do fundo', day: '#05070a', night: '#050303' },
      faceRing: { label: 'Anel do mostrador', day: 'rgba(245,247,250,.14)', night: 'rgba(255,59,48,.2)' },
      bezel: { label: 'Bisel', day: '#000000', night: '#000000' },
      panel: { label: 'Painel de fundo', day: 'rgba(245,247,250,.05)', night: 'rgba(255,59,48,.07)' },
      track: { label: 'Trilho da barra', day: 'rgba(245,247,250,.14)', night: 'rgba(255,59,48,.18)' },
    },
  },

  // As três regras que o tema traz de fábrica. Todas usam condições prontas do editor, e cada uma
  // troca só o token do seu próprio bloco — trocar `ink` deixaria relógio e marcha vermelhos junto.
  colorRules: [
    {
      id: 'min-rule-over-limit',
      enabled: true,
      label: 'Acima do limite da via',
      tokens: ['speedInk'],
      color: '#ff453a',
      when: { when: 'overLimit', marginKmh: 0 },
    },
    {
      id: 'min-rule-regen',
      enabled: true,
      label: 'Regenerando',
      tokens: ['accent'],
      color: '#30d158',
      when: { when: 'threshold', key: 'car.ev_info.energy_output_percentage', op: 'lt', value: 0 },
    },
    {
      id: 'min-rule-low-fuel',
      enabled: true,
      label: 'Combustível abaixo de 15%',
      tokens: ['fuelInk'],
      color: '#ff9500',
      when: { when: 'threshold', key: 'car.basic.remain_fuel_percentage', op: 'lt', value: 15 },
    },
  ],

  fonts: ['sysSans'],
  displayModeConfig: true,
  // Sem mostrador redondo não há escala para reescalonar, e o combo "Escala do Velocímetro" nos
  // ajustes do tema dentro do carro não faria nada. O limite da via continua chegando ao tema pela
  // regra de cor acima, que assina as duas chaves do TSR por conta própria.
  speedScaleConfig: false,

  nativeMasks: {
    fuel: { enabled: false, x: 0, y: 520, width: 380, height: 200, opacity: 1 },
    battery: { enabled: false, x: 1540, y: 520, width: 380, height: 200, opacity: 1 },
    speed: { enabled: true, x: 0, y: 0, width: 640, height: 720, opacity: 1 },
    info: { enabled: true, x: 1280, y: 0, width: 640, height: 720, opacity: 1 },
    topCenter: { enabled: true, x: 660, y: 0, width: 600, height: 200, opacity: 1 },
  },
  appDefaultPosition: { x: 640, y: 95, width: 640, height: 560 },

  boot: { enabled: true, wordmark: 'MINIMALIST', sweepMs: 900 },

  pages: [
    {
      id: 'min-page-1',
      name: 'Principal',
      elements: [
        // --- centro: só a poça de luz -------------------------------------------------------
        // `decor` é a única categoria de tinta suave do modelo: avisa quando encosta numa zona do
        // veículo e nunca bloqueia. É o tipo certo para um halo, que não carrega leitura nenhuma.
        {
          id: 'min-center-glow',
          kind: 'decor',
          name: 'Halo central',
          style: 'glow',
          place: { zone: 'centre', anchor: 'center', at: { x: 960, y: 440 }, size: 'xl', scale: 1.25, z: 0, opacity: 1 },
          visible: { when: 'always' },
          paint: { fill: 'radial', from: 'rgba(120,145,180,.14)', to: 'rgba(0,0,0,0)', cx: 0.5, cy: 0.5, stopAt: 0.46 },
          radius: 0,
          blur: 0,
        },

        // --- barra superior -----------------------------------------------------------------
        {
          id: 'min-out-temp',
          kind: 'readout',
          name: 'Temperatura externa',
          style: 'value',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 832, y: 132 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          // A unidade começa em `°`, e o formatador do motor cola unidades assim ao número: sai
          // "24°C" e não "24 °C".
          binding: { key: 'car.basic.outside_temp', decimals: 0, unit: '°C', fallback: '--' },
          map: { kind: 'linear', min: -20, max: 50 },
          text: text(30, 400, '$ink', { tracking: 0.2 }),
          label: '',
          labelStyle: MICRO,
          icon: null,
        },
        {
          id: 'min-gear',
          kind: 'readout',
          name: 'Marcha',
          style: 'value',
          // Maior a pedido (2026-09-23): era 42 px numa caixa xs; agora 60 px, peso 700, numa caixa
          // sm (180x64) em y 86-150. Os vizinhos (temperatura em 832, relógio em 1118) têm o texto
          // centrado nas próprias caixas, então as caixas se tocam mas os textos não.
          place: { zone: 'topCenter', anchor: 'center', at: { x: 960, y: 118 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.gear_status', fallback: 'P' },
          map: { kind: 'enum', labels: { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' }, fallback: 'N' },
          text: text(60, 700, '$ink', { tracking: 1, tabular: false }),
          label: '',
          labelStyle: MICRO,
          icon: null,
        },
        {
          id: 'min-power-mode',
          kind: 'readout',
          name: 'Modo de energia',
          style: 'text',
          // Na foto o que fica verde embaixo da marcha é "READY". Aqui é o modo de energia, e a
          // troca é deliberada: o READY que o veículo escreve já aparece na coluna da esquerda, e a
          // chave que chegaria perto dele no contrato (`car.basic.engine_state`) tem mapeamento não
          // confirmado e, num híbrido, fica parada em "Rodando" o tempo todo — um selo aceso para
          // sempre. `power_model_config` é confirmada e muda de verdade enquanto se dirige.
          place: { zone: 'topCenter', anchor: 'center', at: { x: 960, y: 168 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_setting.power_model_config', fallback: 'HEV' },
          map: { kind: 'enum', labels: { '0': 'HEV', '1': 'EVP', '3': 'EV' }, fallback: 'HEV' },
          text: text(16, 700, '$ok', { tracking: 3.4, transform: 'upper', tabular: false }),
          label: '',
          labelStyle: MICRO,
          icon: null,
        },
        {
          id: 'min-clock',
          kind: 'readout',
          name: 'Relógio',
          style: 'clock',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 1118, y: 132 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          // O relógio é o único elemento que não lê o carro: o motor o atualiza a cada 15 s a
          // partir do relógio do próprio sistema.
          binding: null,
          map: { kind: 'text' },
          text: text(30, 400, '$ink', { tracking: 0.6 }),
          label: '',
          labelStyle: MICRO,
          icon: null,
        },

        // --- bloco da velocidade ------------------------------------------------------------
        {
          id: 'min-speed',
          kind: 'readout',
          name: 'Velocidade',
          style: 'value',
          // A caixa xl (480x160) ocupa x 350-830, y 220-380: à direita do READY (zona segura até
          // x 342) e acima do ESP (a partir de y 445). Mais à esquerda nesta altura ela cairia em
          // cima do READY, e o editor recusaria o arraste.
          place: { zone: 'left', anchor: 'center', at: { x: 590, y: 300 }, size: 'xl', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.vehicle_speed', decimals: 0, unit: '', fallback: '0' },
          map: { kind: 'linear', min: 0, max: 260 },
          text: text(132, 300, '$speedInk', { hostScaled: true, tracking: -4 }),
          label: 'km/h',
          labelStyle: text(22, 500, '$inkDim', { tracking: 1, transform: 'none', tabular: false }),
          icon: null,
        },
        {
          id: 'min-power-bar',
          kind: 'barLinear',
          name: 'Potência e regeneração',
          style: 'centreOut',
          // Sob o número, no mesmo eixo: x 420-760, y 381-423. A zona do ESP (x 330-430) só começa
          // em y 445, então a barra passa 22 px acima dela.
          place: { zone: 'left', anchor: 'center', at: { x: 590, y: 402 }, size: 'md', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          orientation: 'h',
          binding: { key: 'car.ev_info.energy_output_percentage', decimals: 0, unit: '%', fallback: '0' },
          // A única chave com sinal do contrato: negativo é frenagem regenerativa. `centreOut`
          // preenche a partir do meio do trilho, para a esquerda quando negativa.
          map: { kind: 'linear', min: -100, max: 100 },
          track: { fill: 'solid', color: '$track' },
          fill: { fill: 'solid', color: '$accent' },
          segments: 12,
          // Com a linha de leitura presente, o trilho encolhe para caber ao lado dela — é assim que
          // esta barra fica fina sem sair da tabela de tamanhos.
          readout: {
            text: text(20, 500, '$inkDim'),
            label: '',
            labelStyle: MICRO,
            offsetY: 0,
          },
        },

        // --- faixa de status na base ---------------------------------------------------------
        {
          id: 'min-fuel-range',
          kind: 'readout',
          name: 'Autonomia a combustível',
          style: 'stat',
          // `stat` alinha ícone e número à esquerda da caixa, então a âncora é a borda esquerda:
          // ancorar pelo centro deixaria o número flutuando conforme o tamanho da caixa.
          place: { zone: 'left', anchor: 'midLeft', at: { x: 460, y: 664 }, size: 'sm', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_info.fuel_mode_remain_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 900 },
          // 34 px peso 600, em branco cheio (2026-09-23): eram 26 px a 72%. Cabe na caixa sm:
          // ícone de 35 px + 10 de vão + "658 km" (~115 px) = 160 de 180.
          text: text(34, 600, '$fuelInk'),
          label: '',
          labelStyle: MICRO_ACESO,
          icon: 'fuel',
        },
        {
          id: 'min-fuel-bar',
          kind: 'barLinear',
          name: 'Nível de combustível',
          style: 'fill',
          place: { zone: 'left', anchor: 'midLeft', at: { x: 496, y: 702 }, size: 'xs', scale: 0.62, z: 3, opacity: 1 },
          visible: { when: 'always' },
          orientation: 'h',
          binding: { key: 'car.basic.remain_fuel_percentage', decimals: 0, unit: '%', fallback: '0' },
          map: { kind: 'linear', min: 0, max: 100 },
          track: { fill: 'solid', color: '$track' },
          fill: { fill: 'solid', color: '$fuelInk' },
          segments: 12,
          readout: null,
        },
        {
          id: 'min-ev-range',
          kind: 'readout',
          name: 'Autonomia elétrica',
          style: 'stat',
          place: { zone: 'left', anchor: 'midLeft', at: { x: 700, y: 664 }, size: 'sm', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_info.electric_mode_remain_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 200 },
          // Mesmo tratamento da autonomia a combustível: estava em `inkDim` (55%), o item mais
          // apagado do painel inteiro.
          text: text(34, 600, '$ink'),
          label: '',
          labelStyle: MICRO_ACESO,
          icon: 'battery',
        },

        // --- mídia e status à direita ---------------------------------------------------------
        {
          id: 'min-media',
          kind: 'media',
          name: 'Tocando agora',
          style: 'card',
          // Os tamanhos de texto são grandes aqui de propósito: o ajuste fino 0,62 multiplica
          // tudo, então 40 e 30 chegam à tela como 25 e 19. Escrever 25 e 19 direto daria 15 e 12.
          // Canto direito, no meio da altura: o centro (y 321) é o mesmo do bloco da velocidade
          // (número + barra, y 220-423). A caixa (422x227) fica em x 1409-1831, y 208-435.
          place: { zone: 'right', anchor: 'center', at: { x: 1620, y: 321 }, size: 'lg', scale: 0.62, z: 2, opacity: 1 },
          visible: { when: 'always' },
          showArtwork: true,
          title: text(40, 600, '$ink', { tabular: false }),
          artist: text(30, 500, '$inkDim', { tabular: false }),
          panel: { fill: 'none' },
        },
        {
          id: 'min-drive-mode',
          kind: 'readout',
          name: 'Modo de condução',
          style: 'text',
          place: { zone: 'right', anchor: 'center', at: { x: 1432, y: 664 }, size: 'sm', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.drive_setting.drive_mode', fallback: 'NORMAL' },
          map: {
            kind: 'enum',
            labels: { '0': 'Normal', '1': 'Sport', '2': 'Eco', '3': 'Neve', '4': 'Areia', '5': 'Lama', '11': 'AWD' },
            fallback: 'Normal',
          },
          text: text(18, 700, '$inkDim', { tracking: 4, transform: 'upper', tabular: false }),
          label: '',
          labelStyle: MICRO,
          icon: null,
        },
        {
          id: 'min-battery-bar',
          kind: 'barLinear',
          name: 'Carga da bateria',
          style: 'fill',
          place: { zone: 'right', anchor: 'midRight', at: { x: 1840, y: 664 }, size: 'xs', scale: 0.62, z: 3, opacity: 1 },
          visible: { when: 'always' },
          orientation: 'h',
          binding: { key: 'car.ev_info.cur_battery_power_percentage', decimals: 0, unit: '%', fallback: '0' },
          map: { kind: 'linear', min: 0, max: 100 },
          track: { fill: 'solid', color: '$track' },
          fill: { fill: 'solid', color: '$inkDim' },
          segments: 12,
          readout: null,
        },
      ],
    },
  ],
}
