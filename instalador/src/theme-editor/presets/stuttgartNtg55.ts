// Stuttgart NTG 5.5, recriado como `ThemeSpec` editável.
//
// Geometria lida de `app/src/main/assets/PreinstalledThemes/coffe3/app.html`. Três coisas
// diferenciam este tema do Cupertino, e todas existem no modelo por causa dele:
//
//   1. Os mostradores NÃO têm disco. São transparentes de propósito, para o papel de parede
//      aparecer através deles — por isso `face.enabled: false`.
//   2. O ponteiro usa `hubRadial`: um gradiente radial ancorado no eixo de rotação. Como o centro
//      do gradiente É o eixo, girar o ponteiro não muda um pixel dele. Um `radial` comum faria o
//      gradiente escorrer junto com o ponteiro.
//   3. O medidor de potência é não-linear. O zero fica a um sexto do arco, então a regeneração
//      ocupa 41,67° contra 208,33° de potência — o `piecewise` abaixo é exatamente isso.
//
// O papel de parede entra como `passthrough`: os bytes originais, com o nome original. O arquivo
// chamado `car-bg.jpg` contém na verdade um PNG, e recodificá-lo pela extensão o quebraria.

import type { ThemeSpec } from '../model/spec'
import { text } from '../model/defaults'

const EPOCA = 1767225600000

const ZOOM = { enabled: true, scale: 1.6, weight: 800, durationMs: 380, easing: 'cubic-bezier(.22,.61,.36,1)' }

/**
 * Palito fino, sem cubo. O bisel do original está em `SPEED_R + 8 + 15 = 273`, e a pegada `lg`
 * vale 265 — daí o ajuste fino de 1,03, que dá 273 cravados.
 */
const PONTEIRO = {
  shape: 'line' as const,
  innerR: 26,
  outerInset: 54,
  width: 6,
  paint: { fill: 'hubRadial' as const, inner: '$needleDark', mid: '$needle', outer: '$needleBright', midAt: 0.58 },
  cap: 'none' as const,
  glow: true,
}

/**
 * Vidro, e não disco.
 *
 * Aqui estava `enabled: false` com tinta nenhuma: o mostrador era um buraco, e a razão continua
 * válida — a foto da rodovia é a identidade do tema. O que muda é que agora ele é um buraco com
 * vidro na frente.
 *
 * 18 px é o raio do desfoque nos 546 px do mostrador. O tema Default do carro já embarca um disco
 * de vidro (`.speed-inner-circle`, `blur(10px) saturate(180%)`) com 380 px — proporcionalmente
 * 14 px neste; 18 porque este disco não tem fundo próprio para ajudar.
 *
 * A TINTA CLAREIA, NÃO ESCURECE, e isso saiu de medição, não de gosto: sob os dois mostradores o
 * papel de parede tem luminância média de 3 em 255 (mediana 0). Ali não há foto para escurecer,
 * e uma tinta preta sobre preto seria trabalho invisível. Um filme claro deslocado para o alto à
 * esquerda é o que faz o disco existir sobre o fundo escuro — vidro apanhando luz, com o bisel
 * de `$faceRing` já pronto no papel de borda iluminada. O desfoque, esse, continua fazendo o que
 * cabe a ele: apagar a linha do horizonte, que cortava os dois mostradores ao meio.
 */
const VIDRO = {
  enabled: true,
  blurPx: 18,
  paint: {
    fill: 'radial' as const,
    from: 'rgba(255,255,255,.075)',
    to: 'rgba(255,255,255,.012)',
    cx: 0.34,
    cy: 0.28,
    stopAt: 0.85,
  },
  ring: null,
  bezel: { width: 5, color: '$faceRing' },
}

const MARCACOES = {
  subdivisions: 4,
  showLabels: true,
  // 30, e não mais: na escala de 260 os catorze números ficam a 72 px de distância entre centros,
  // e o zoom de 1,6 do número mais próximo do ponteiro leva um rótulo de três dígitos a 83 px de
  // largura. Medido no navegador: sobram 4,3 px entre o rótulo ampliado e o vizinho. A 32 px eles
  // se encostam. O teto aqui é o zoom, não o arco.
  labelStyle: text(30, 500, '$ink'),
  labelInset: 40,
  len: { major: 18, minor: 11, micro: 7 },
  width: { major: 3, minor: 1.6, micro: 1.2 },
}

export const STUTTGART_NTG55: ThemeSpec = {
  schema: 2,
  id: 'preset-stuttgart-ntg55',
  folderName: 'stuttgart-ntg55',
  name: 'Stuttgart NTG 5.5',
  description:
    'Velocímetro analógico clássico à esquerda, medidor de potência e regeneração à direita, ' +
    'bateria e combustível ampliados na base, mídia e marcha no topo, sobre papel de parede de ' +
    'rodovia. Mostradores transparentes e ponteiro azul com gradiente no eixo.',
  version: '0.9.0',
  createdAt: EPOCA,
  updatedAt: EPOCA,
  origin: null,

  canvas: { width: 1920, height: 720 },

  background: {
    mode: 'passthrough',
    presetId: 'midnight',
    solid: '#000000',
    gradientFrom: '#000000',
    gradientTo: '#101418',
    customPath: '',
    customDataUrl: '',
    passthrough: { fileName: 'car-bg.jpg', sourceUrl: '/themes/stuttgart-ntg55/car-bg.jpg' },
  },

  // Três estados de cor, e é este tema que justifica os três: de dia branco, à noite vermelho por
  // escolha do motorista, e âmbar quando o farol acende. `headlightFromHostVars` faz o âmbar sair
  // da preferência do próprio app em vez de ser fixado aqui — quem escolheu vermelho no carro vê
  // vermelho.
  palette: {
    headlightFromHostVars: true,
    tokens: {
      ink: { label: 'Texto principal', day: '#f2f4f6', night: '#ff3b30' },
      inkDim: { label: 'Texto secundário', day: 'rgba(242,244,246,.55)', night: 'rgba(255,59,48,.6)' },
      inkFaint: { label: 'Texto apagado', day: 'rgba(242,244,246,.28)', night: 'rgba(255,59,48,.32)' },
      accent: { label: 'Destaque', day: '#3fa0ff', night: '#ff3b30' },
      needle: { label: 'Ponteiro (meio)', day: '#3fa0ff', night: '#ff3b30' },
      needleDark: { label: 'Ponteiro (eixo)', day: '#0c4a86', night: '#7a1712' },
      needleBright: { label: 'Ponteiro (ponta)', day: '#eaf6ff', night: '#ff9e90' },
      needleGlow: { label: 'Brilho do ponteiro', day: 'rgba(63,160,255,.65)', night: 'rgba(255,59,48,.7)' },
      warn: { label: 'Alerta', day: '#ff4d4f', night: '#ff4d4f' },
      ok: { label: 'Positivo', day: '#35d07f', night: '#35d07f' },
      low: { label: 'Nível baixo', day: '#ff9500', night: '#ff9500' },
      face: { label: 'Disco (centro)', day: 'rgba(10,14,20,.55)', night: 'rgba(10,6,6,.6)' },
      faceLow: { label: 'Disco (borda)', day: 'rgba(0,0,0,.75)', night: 'rgba(0,0,0,.8)' },
      faceRing: { label: 'Bisel de vidro', day: 'rgba(255,255,255,.5)', night: 'rgba(255,59,48,.35)' },
      bezel: { label: 'Bisel', day: '#05060a', night: '#05060a' },
      panel: { label: 'Painel de fundo', day: 'rgba(255,255,255,.07)', night: 'rgba(255,59,48,.08)' },
      track: { label: 'Trilho da barra', day: 'rgba(255,255,255,.14)', night: 'rgba(255,59,48,.16)' },
    },
  },

  // Nenhuma regra de cor: os dois temas de fábrica não têm. O editor oferece a lista de condições
  // prontas para quem quiser acrescentar.
  colorRules: [],

  fonts: ['exoV'],
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

  boot: { enabled: true, wordmark: 'STUTTGART NTG 5.5', sweepMs: 1300 },

  pages: [
    {
      id: 'stu-page-1',
      name: 'Principal',
      elements: [
        // --- brilho sob cada mostrador ------------------------------------------------------
        // No original são dois gradientes radiais em CSS puro, que escurecem o papel de parede
        // atrás das marcações para elas continuarem legíveis sobre a foto.
        {
          id: 'stu-glow-left',
          kind: 'decor',
          name: 'Brilho do velocímetro',
          style: 'glow',
          place: { zone: 'left', anchor: 'center', at: { x: 335, y: 400 }, size: 'xl', scale: 1, z: 0, opacity: 1 },
          visible: { when: 'always' },
          paint: { fill: 'radial', from: 'rgba(0,0,0,.85)', to: 'rgba(0,0,0,0)', cx: 0.5, cy: 0.5, stopAt: 0.7 },
          radius: 999,
          blur: 0,
        },
        {
          id: 'stu-glow-right',
          kind: 'decor',
          name: 'Brilho do medidor de potência',
          style: 'glow',
          place: { zone: 'right', anchor: 'center', at: { x: 1585, y: 400 }, size: 'xl', scale: 1, z: 0, opacity: 1 },
          visible: { when: 'always' },
          paint: { fill: 'radial', from: 'rgba(0,0,0,.85)', to: 'rgba(0,0,0,0)', cx: 0.5, cy: 0.5, stopAt: 0.7 },
          radius: 999,
          blur: 0,
        },

        // --- mostrador esquerdo: velocidade -------------------------------------------------
        {
          id: 'stu-dial-speed',
          kind: 'dial',
          name: 'Velocímetro',
          style: 'ticksNeedle',
          place: { zone: 'left', anchor: 'center', at: { x: 335, y: 400 }, size: 'lg', scale: 1.03, z: 1, opacity: 1 },
          visible: { when: 'always' },
          sweep: { startDeg: -125, endDeg: 125 },
          // O PONTEIRO É SUAVIZADO; O NÚMERO, NÃO
          //
          // O carro publica a velocidade de 1 em 1 km/h. No número isso é fino — 61, 62, 63 é a
          // leitura certa e qualquer filtro só atrasaria. No PONTEIRO o mesmo degrau vale 2,08°
          // (250° de curso para os 120 km/h da escala fechada), e 2° de cada vez é um ponteiro
          // que anda aos pulinhos. Por isso o filtro está aqui, na ligação do mostrador, e não
          // na de `stu-speed-value`: são duas ligações separadas, para a mesma chave, e só esta
          // paga o atraso.
          //
          // 190 ms cobrem 95% de um degrau. O número saiu de uma comparação medida, não de
          // gosto: com o filtro criticamente amortecido do motor, 190 ms entregam o MESMO atraso
          // que os 240 ms do exponencial que havia antes (1,56° mediano, 2,6° de pico a
          // 10 km/h/s) e cortam o pior passo de um quadro pela metade — de 1,04° para 0,52°, que
          // é exatamente o degrau declarado. Ou seja: mais suave de graça.
          //
          // O preço em atraso continua existindo e está aferido em `test-theme-engine.mjs`: o
          // ponteiro fica ~0,75 km/h atrás do número numa aceleração de dia a dia. Por isso ele
          // está aqui e não na ligação de `stu-speed-value` — o número não atrasa.
          //
          // `smoothStep` é o tamanho do passo do desenho: 0,25 km/h, quatro passos de 0,52° para
          // cada km/h que o carro anuncia.
          binding: {
            key: 'car.basic.vehicle_speed',
            decimals: 0,
            unit: '',
            fallback: '0',
            smoothMs: 190,
            smoothStep: 0.25,
          },
          map: { kind: 'dualRange', min: 0, low: 120, high: 260, upAt: 120, downAt: 100, rescaleMs: 650 },
          face: VIDRO,
          ticks: {
            ...MARCACOES,
            majors: [0, 20, 40, 60, 80, 100, 120, 140, 160, 180, 200, 220, 240, 260],
            zones: [],
          },
          needle: PONTEIRO,
          arc: { enabled: false, radiusOffset: 7, width: 7, quantizeDeg: 1, split: null },
          zoom: ZOOM,
          // Mesmo motivo do Cupertino: o miolo do mostrador esquerdo é onde o veículo desenha a
          // placa de limite. A leitura sai para `stu-speed-value`.
          center: null,
        },
        {
          id: 'stu-speed-value',
          kind: 'readout',
          name: 'Velocidade',
          style: 'value',
          // x = 335: o centro do mostrador, não 305. y = 613 não é escolha — é o único valor que
          // cabe. O bloco cresce em torno do próprio centro e a 100 px ocupa 549..661: 8 px abaixo
          // do rótulo mais baixo das marcações (termina em 541) e 12 px acima da borda do vidro
          // (673). Subir encosta no rótulo, descer atravessa o bisel.
          place: { zone: 'left', anchor: 'center', at: { x: 335, y: 613 }, size: 'md', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.vehicle_speed', decimals: 0, unit: '', fallback: '0' },
          map: { kind: 'linear', min: 0, max: 260 },
          // O bloco cresce em torno do próprio centro, e o centro não se mexe. A 100 px ele ocupa
          // y 557..667: abaixo do rótulo mais baixo do mostrador (que termina em 539) e acima do
          // bisel (673). Continua dentro do vão inferior do arco, que é onde ele sempre esteve.
          text: text(100, 300, '$ink', { hostScaled: true, tracking: -1 }),
          label: 'km/h',
          labelStyle: text(15, 600, '$inkDim', { tracking: 3, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-park-badge',
          kind: 'indicator',
          name: 'Freio de estacionamento',
          style: 'badge',
          place: { zone: 'left', anchor: 'center', at: { x: 161, y: 116 }, size: 'xs', scale: 1, z: 4, opacity: 1 },
          // O original assina `car.basic.hand_brake_status`, fora do contrato do tema: nunca
          // acendeu. Reaproveitado a partir da marcha, que funciona.
          visible: { when: 'equals', key: 'car.basic.gear_status', value: '3' },
          binding: { key: 'car.basic.gear_status', fallback: '' },
          map: { kind: 'enum', labels: { '3': 'P' }, fallback: '' },
          icon: null,
          onPaint: { fill: 'solid', color: '$warn' },
          offPaint: { fill: 'solid', color: '$inkFaint' },
          text: text(16, 700, '$warn', { tabular: false }),
        },

        // --- mostrador direito: potência e regeneração --------------------------------------
        {
          id: 'stu-dial-power',
          kind: 'dial',
          name: 'Potência e regeneração',
          style: 'ticksNeedle',
          place: { zone: 'right', anchor: 'center', at: { x: 1585, y: 400 }, size: 'lg', scale: 1.03, z: 1, opacity: 1 },
          visible: { when: 'always' },
          sweep: { startDeg: -125, endDeg: 125 },
          // A ÚNICA ligação suavizada deste tema, e a diferença com a do velocímetro é o dado, não
          // o mostrador. A velocidade chega de 1 em 1 km/h e o ponteiro já parece fluido; a
          // porcentagem de potência chega em degraus de 5 a 12 pontos, espaçados, e o mesmo
          // ponteiro parece travado. 420 ms é o tempo para cobrir 95% de um degrau — e é também o
          // atraso que ele acrescenta sobre o dado bruto. Num medidor de potência isso não se vê;
          // no velocímetro seria mentira, e por isso a suavização NÃO é padrão do motor.
          binding: {
            key: 'car.ev_info.energy_output_percentage',
            decimals: 0,
            unit: '',
            fallback: '0',
            smoothMs: 420,
          },
          // O zero a um sexto do arco: 41,67° para -100..0 e 208,33° para 0..100. É a calibração
          // do original, aqui sem nenhum caso especial no motor.
          map: { kind: 'piecewise', points: [{ v: -100, t: 0 }, { v: 0, t: 1 / 6 }, { v: 100, t: 1 }] },
          face: VIDRO,
          ticks: {
            ...MARCACOES,
            majors: [-100, -50, 0, 20, 40, 60, 80, 100],
            // Até -1, e não até 0: no original o zero fica na cor normal, marcando a fronteira
            // entre recuperar e gastar energia em vez de pertencer ao lado verde.
            zones: [{ from: -100, to: -1, token: '$ok' }],
          },
          needle: PONTEIRO,
          arc: { enabled: false, radiusOffset: 7, width: 7, quantizeDeg: 1, split: null },
          zoom: ZOOM,
          center: null,
        },
        {
          id: 'stu-power-value',
          kind: 'readout',
          name: 'Potência',
          style: 'value',
          // O espelho exato do lado esquerdo: centro do mostrador em x, e o MESMO y. Estava 30 px
          // para fora e 30 px acima — com os dois mostradores sendo gêmeos, duas alturas
          // diferentes na mesma tela leem como defeito de alinhamento, não como intenção.
          place: { zone: 'right', anchor: 'center', at: { x: 1585, y: 613 }, size: 'md', scale: 1, z: 3, opacity: 1 },
          visible: { when: 'always' },
          // Mesma suavização do mostrador, pelo mesmo motivo — e o número precisa dela tanto
          // quanto o ponteiro: girar o ponteiro devagar e deixar o "30%" aparecer de uma vez
          // resolveria metade do problema e denunciaria a outra metade.
          binding: {
            key: 'car.ev_info.energy_output_percentage',
            decimals: 0,
            unit: '',
            fallback: '0',
            smoothMs: 420,
          },
          map: { kind: 'linear', min: -100, max: 100 },
          text: text(100, 300, '$ink', { hostScaled: true, tracking: -1 }),
          label: '%',
          labelStyle: text(15, 600, '$inkDim', { tracking: 3, transform: 'upper', tabular: false }),
          icon: null,
        },

        // --- barra superior -----------------------------------------------------------------
        {
          id: 'stu-out-temp',
          kind: 'readout',
          name: 'Temperatura externa',
          style: 'value',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 762, y: 32 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.outside_temp', decimals: 0, unit: '°', fallback: '--°' },
          map: { kind: 'linear', min: -20, max: 50 },
          text: text(24, 700, '$ink'),
          label: '',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-drive-mode',
          kind: 'indicator',
          name: 'Modo de condução',
          style: 'badge',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 884, y: 32 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.drive_setting.drive_mode', fallback: '0' },
          map: {
            kind: 'enum',
            labels: { '0': 'Normal', '1': 'Sport', '2': 'Eco', '3': 'Neve', '4': 'Areia', '5': 'Lama', '11': 'AWD' },
            fallback: 'Normal',
          },
          icon: null,
          onPaint: { fill: 'solid', color: '$inkDim' },
          offPaint: { fill: 'solid', color: '$inkFaint' },
          text: text(13, 700, '$inkDim', { tracking: 1, transform: 'upper', tabular: false }),
        },
        {
          id: 'stu-gear',
          kind: 'readout',
          name: 'Marcha',
          style: 'value',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 960, y: 32 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.gear_status', fallback: 'P' },
          map: { kind: 'enum', labels: { '0': 'N', '1': 'N', '2': 'D', '3': 'P', '4': 'R' }, fallback: 'N' },
          // De 26 para 60. A caixa continua a mesma (xs) e o texto transborda dela — nada no motor
          // recorta uma leitura, e a caixa só serve de âncora. O que limita é a vizinhança, medida
          // no navegador: entre a tinta do modo de condução (termina em 909) e a do modo de
          // energia (começa em 1021) sobram 112 px, e o "N" a 60 px ocupa 42. Para baixo, a linha
          // de mídia começa em y=68 e a marcha a 60 px termina em 59.
          text: text(60, 700, '$ink', { tabular: false }),
          label: '',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-power-mode',
          kind: 'indicator',
          name: 'Modo de energia',
          style: 'badge',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 1036, y: 32 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_setting.power_model_config', fallback: '0' },
          map: { kind: 'enum', labels: { '0': 'HEV', '1': 'EVP', '3': 'EV' }, fallback: 'HEV' },
          icon: null,
          onPaint: { fill: 'solid', color: '$inkDim' },
          offPaint: { fill: 'solid', color: '$inkFaint' },
          text: text(13, 700, '$inkDim', { tracking: 1, transform: 'upper', tabular: false }),
        },
        {
          id: 'stu-clock',
          kind: 'readout',
          name: 'Relógio',
          style: 'clock',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 1160, y: 32 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: null,
          map: { kind: 'text' },
          text: text(16, 700, '$ink'),
          label: '',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-media-line',
          kind: 'media',
          name: 'Tocando agora',
          style: 'line',
          place: { zone: 'topCenter', anchor: 'center', at: { x: 960, y: 84 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          // Capa ligada em 2026-09-22 a pedido. Ela entra À ESQUERDA do título, 56 px (54% da
          // altura da caixa), e o grupo capa + texto fica centralizado sob a marcha. Cabe entre os
          // selos de modo (base em y 50) e o vão da projeção (começa em y 127): 56..112.
          // Com o rádio tocando não há capa (o rádio não é app Android), e o quadrado mostra a nota
          // musical no lugar; sem nada tocando, a linha inteira some.
          showArtwork: true,
          title: text(19, 600, '$ink', { tabular: false }),
          artist: text(16, 500, '$inkDim', { tabular: false }),
          panel: { fill: 'none' },
        },

        // --- base: bateria, combustível e cantos --------------------------------------------
        {
          id: 'stu-ev-range',
          kind: 'readout',
          name: 'Autonomia elétrica',
          style: 'stat',
          place: { zone: 'free', anchor: 'center', at: { x: 830, y: 655 }, size: 'md', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_info.electric_mode_remain_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 200 },
          text: text(60, 300, '$ink'),
          label: '',
          labelStyle: text(17, 500, '$inkDim', { tabular: false }),
          icon: 'battery',
        },
        {
          id: 'stu-battery-pct',
          kind: 'readout',
          name: 'Carga da bateria',
          style: 'value',
          place: { zone: 'free', anchor: 'center', at: { x: 872, y: 700 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_info.cur_battery_power_percentage', decimals: 0, unit: '%', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 100 },
          text: text(22, 500, '$inkDim'),
          label: '',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-fuel-range',
          kind: 'readout',
          name: 'Autonomia a combustível',
          style: 'stat',
          place: { zone: 'free', anchor: 'center', at: { x: 1110, y: 655 }, size: 'md', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.ev_info.fuel_mode_remain_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 900 },
          text: text(60, 300, '$ink'),
          label: '',
          labelStyle: text(17, 500, '$inkDim', { tabular: false }),
          icon: 'fuel',
        },
        {
          id: 'stu-fuel-pct',
          kind: 'readout',
          name: 'Combustível restante',
          style: 'value',
          place: { zone: 'free', anchor: 'center', at: { x: 1152, y: 700 }, size: 'xs', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.remain_fuel_percentage', decimals: 0, unit: '%', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 100 },
          text: text(22, 500, '$inkDim'),
          label: '',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-consumption',
          kind: 'readout',
          name: 'Consumo instantâneo',
          style: 'value',
          place: { zone: 'free', anchor: 'midLeft', at: { x: 56, y: 668 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          // O original lê `car.basic.instant_fuel_consumption`, que chega como a tupla
          // "{métrica,consumo}" e não dá um número direto. Esta chave entrega kWh/100km em texto
          // simples, que é o que uma leitura de um valor só consegue mostrar sem interpretar JSON.
          binding: { key: 'car.ev_info.Instant_energy_consumption', decimals: 1, unit: 'kWh/100km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 40 },
          text: text(26, 400, '$ink'),
          label: 'Consumo',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
        {
          id: 'stu-odometer',
          kind: 'readout',
          name: 'Odômetro',
          style: 'value',
          place: { zone: 'free', anchor: 'midRight', at: { x: 1864, y: 668 }, size: 'sm', scale: 1, z: 2, opacity: 1 },
          visible: { when: 'always' },
          binding: { key: 'car.basic.total_odometer', decimals: 0, unit: 'km', fallback: '--' },
          map: { kind: 'linear', min: 0, max: 999999 },
          text: text(26, 400, '$ink'),
          label: 'Odômetro',
          labelStyle: text(11, 600, '$inkDim', { tracking: 2, transform: 'upper', tabular: false }),
          icon: null,
        },
      ],
    },
  ],
}
