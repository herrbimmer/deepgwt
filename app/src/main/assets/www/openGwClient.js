/**
 * Ponte do launcher 3D com o OPEN GW.
 *
 * POR QUE HTTP E NÃO UMA PONTE NATIVA
 * Este APK não tem código-fonte no repositório — só o dex. As interfaces que ele expõe ao
 * JavaScript são fixas (TelemetryBridge, AppLauncherBridge, MediaBridge, TripBridge) e nenhuma
 * delas troca o launcher do carro, liga o modo sorrateiro ou lê o modelo do veículo. O que dá
 * para mudar sem recompilar o dex são estes arquivos em assets/www.
 *
 * O caminho que sobra é o servidor que o OPEN GW já mantém em 127.0.0.1:8766 (GwLinkServer),
 * o mesmo que o instalador do computador usa. Funciona porque o WebView deste app roda com
 * `setMixedContentMode(ALWAYS_ALLOW)`: a página vem por https de appassets.androidplatform.net e
 * mesmo assim pode falar http com o próprio aparelho. O OPEN GW responde com CORS liberado.
 *
 * Telemetria NÃO passa por aqui. Ela já chegava antes: o OPEN GW transmite
 * `com.haval.vehicle.EVENT_CHANGED` a cada mudança e o lado nativo deste app converte em
 * `window.onCarDataUpdate`. O que faltava era o retrato inicial — o app pede
 * `com.haval.vehicle.REQUEST_SNAPSHOT` ao abrir e, até agora, ninguém respondia. Isso foi
 * resolvido do lado do OPEN GW (ViewerBridgeReceiver), não aqui.
 */
(function () {
  'use strict';

  // ── Orçamento de desempenho do launcher 3D ────────────────────────────────────────────────
  //
  // POR QUE AQUI E NÃO NO BUNDLE
  //
  // Este arquivo é carregado pelo index.html ANTES do módulo principal do app (a ordem está no
  // próprio HTML: support → telemetry → appLauncher → openGwPanel → openGwClient → app). Isso é o
  // que permite trocar o relógio de quadros e a densidade de pixels antes de o renderizador
  // existir, sem tocar no bundle de 1,9 MB que não tem código-fonte no repositório.
  //
  // DUAS MEDIDAS, PEDIDAS NOME A NOME
  //
  //   1. teto de 24 quadros por segundo — o app já desenha sob demanda, mas quando desenha ele
  //      corre solto no `requestAnimationFrame`, que nesta central tenta 60. Num carro parado,
  //      com o modelo girando devagar, 24 é indistinguível e devolve mais da metade do tempo de
  //      GPU para o resto do sistema;
  //   2. render a 90% da resolução da tela — a imagem é desenhada num buffer 10% menor e a
  //      própria composição do WebView a amplia. Custa ~19% menos pixels por quadro.
  //
  // Nada disto toca a interface 2D: os textos, os ícones e os widgets continuam desenhados na
  // resolução nativa do painel, porque são DOM, não canvas.

  // ── Versão desta camada ───────────────────────────────────────────────────────────────────
  //
  // O APK do launcher não é compilado a partir deste repositório, então o versionName dele no
  // AndroidManifest não muda quando `assets/www` muda — e é `assets/www` que muda no dia a dia.
  // Esta linha é o versionamento REAL do que está rodando: ela aparece no console do WebView e em
  // `window.__openGwLauncherBuild`, e segue a mesma regra do app (ver VERSIONAMENTO.md).
  var LAUNCHER_BUILD = 'Alpha 9.7 011020260825';
  if (typeof window !== 'undefined') window.__openGwLauncherBuild = LAUNCHER_BUILD;

  var TARGET_FPS = 24;
  var MIN_FRAME_MS = 1000 / TARGET_FPS;
  var RENDER_SCALE = 0.9;
  // Com a página escondida (central em outro app, tela apagada) o laço continua armado: o app
  // decide sozinho se DESENHA, mas o rAF em si segue acordando a 60 Hz e pagando o
  // agendamento. Dois quadros por segundo bastam para reagir quando ela volta.
  var HIDDEN_FRAME_MS = 500;
  // Teto de sombra no Android. 2048x2048 era um mapa de 16 MB redesenhado a cada porta que abre
  // (19 pontos de `shadowMap.needsUpdate` no bundle); a sombra aqui é de contato, embaixo do
  // carro, num painel de 720p — 1024 é indistinguível e custa um quarto do preenchimento.
  var SHADOW_MAP_MAX_ANDROID = 1024;
  var frameBudgetMs = MIN_FRAME_MS;

  function installFrameBudget() {
    var raf = window.requestAnimationFrame;
    var caf = window.cancelAnimationFrame;
    if (typeof raf !== 'function') return;

    var realRaf = raf.bind(window);
    var realCaf = typeof caf === 'function' ? caf.bind(window) : function () {};
    // O id devolvido tem que continuar servindo para cancelar, e a espera por um quadro válido
    // re-arma o rAF de verdade (id novo a cada tentativa). Por isso o id público é nosso e o mapa
    // guarda qual id interno está armado agora.
    var pending = {};
    var nextId = 1;
    var lastFrameMs = 0;
    // `now` do último quadro liberado — o que deixa vários consumidores entrarem no mesmo quadro.
    var quadroAbertoMs = -1;

    window.requestAnimationFrame = function (callback) {
      var id = nextId++;
      // POR QUE CADA PEDIDO TEM PRAZO PRÓPRIO
      //
      // A cadência (`lastFrameMs`) é uma só para a página inteira, e isso sozinho MATA DE FOME
      // qualquer segundo consumidor de rAF. Medido no emulador: o laço de render está sempre
      // primeiro na fila do quadro, passa, empurra `lastFrameMs` para a frente e re-arma; o
      // segundo pedido, avaliado logo depois no MESMO quadro, vê a cadência recém-consumida,
      // re-arma, e repete isso para sempre — 0 quadros entregues em 90 segundos, enquanto o rAF
      // nativo entregava 60 por segundo. Na prática seria uma animação de widget (varredura de
      // ponteiro, transição, inércia de rolagem) congelada sem erro nenhum no console.
      //
      // O orçamento decide QUAIS QUADROS saem, não quais pedidos passam. A diferença não é
      // acadêmica: com a decisão por pedido, o laço de render — sempre primeiro na fila do quadro —
      // passava, empurrava o alvo e re-armava, e qualquer segundo consumidor avaliado logo depois
      // no MESMO quadro via a cadência recém-consumida e re-armava para sempre. Medido no
      // emulador: 0 quadros entregues em 90 segundos, enquanto o rAF nativo entregava 60 por
      // segundo — na prática uma animação de widget (varredura de ponteiro, transição, inércia de
      // rolagem) congelada sem nenhum erro no console.
      //
      // Por quadro, o problema some sozinho: todos os callbacks de um mesmo quadro recebem o
      // MESMO `now` (a especificação exige), então o primeiro a chegar abre o quadro e os demais
      // entram junto. Nenhum consumidor passa na frente de outro e o teto continua valendo para
      // todos, que é o que foi pedido.
      var arm = function () {
        pending[id] = realRaf(function (now) {
          if (now !== quadroAbertoMs) {
            // Margem de meio milissegundo: sem ela, um quadro que chega 0,2 ms cedo é adiado por
            // um ciclo inteiro e a taxa efetiva desaba.
            if (now - lastFrameMs < frameBudgetMs - 0.5) {
              arm();
              return;
            }
            // O PRÓXIMO alvo avança um orçamento a partir do ALVO anterior, não do instante em que
            // este quadro saiu. Sem isso, num painel de 60 Hz (16,7 ms por quadro de hardware) só
            // dá para entregar a cada 2 ou 3 quadros — 30 ou 20 fps — e o arredondamento para cima
            // sempre pega o 20. Carregando o resto, a cadência alterna 50/33 ms e a MÉDIA fecha em
            // 24, que é o que foi pedido. É a mesma cadência irregular que cinema a 24 quadros tem
            // num projetor de 60 Hz.
            lastFrameMs += frameBudgetMs;
            // Se o laço ficou parado (aba escondida, carro em espera), o alvo ficou para trás e
            // recuperar o atraso quadro a quadro daria uma rajada. Ressincroniza.
            if (now - lastFrameMs > frameBudgetMs) lastFrameMs = now;
            quadroAbertoMs = now;
          }
          delete pending[id];
          callback(now);
        });
      };
      arm();
      return id;
    };

    window.cancelAnimationFrame = function (id) {
      var inner = pending[id];
      if (inner === undefined) return;
      realCaf(inner);
      delete pending[id];
    };
  }

  function installRenderScale() {
    var real = window.devicePixelRatio || 1;
    var scaled = real * RENDER_SCALE;
    try {
      // O app calcula o teto dele com `Math.min(window.devicePixelRatio || 1, 1.5)` e monta a
      // escada de qualidade a partir disso. Reduzindo o que ele lê, todos os degraus — inclusive
      // o de "refinar quando parado" — ficam abaixo da resolução nativa, e a lógica adaptativa
      // dele continua funcionando dentro do novo teto em vez de brigar com ele.
      Object.defineProperty(window, 'devicePixelRatio', {
        configurable: true,
        get: function () { return scaled; }
      });
    } catch (e) {
      console.warn('[OpenGW] não foi possível reduzir a resolução de render', e);
    }
  }


  // ── Dica de GPU na criação do contexto ────────────────────────────────────────────────────
  //
  // O bundle constrói o `WebGLRenderer` com `{ antialias, alpha, preserveDrawingBuffer }` e mais
  // nada. Sem `powerPreference`, o driver escolhe o perfil dele — em SoC com GPU de duas ilhas
  // (a desta central é uma delas) isso costuma cair no perfil de economia, que é o mesmo silício
  // com o relógio mais baixo. Pedir "high-performance" não muda um pixel do que é desenhado.
  //
  // `failIfMajorPerformanceCaveat: false` é o padrão da especificação e está aqui explícito só
  // para que uma build de WebView que inverta o padrão não derrube o contexto para software.
  //
  // O antialias NÃO é tocado de propósito: desligar o MSAA devolveria bastante preenchimento,
  // mas serrilha a silhueta do carro contra o fundo claro — é a troca de qualidade que o pedido
  // excluiu. O ganho equivalente veio da sombra e da malha, abaixo.
  function installGpuHints() {
    if (typeof HTMLCanvasElement === 'undefined') return;
    var proto = HTMLCanvasElement.prototype;
    var real = proto.getContext;
    if (typeof real !== 'function' || real.__openGwPatched) return;
    function patched(tipo, atributos) {
      if (tipo === 'webgl' || tipo === 'webgl2' || tipo === 'experimental-webgl') {
        var attrs = {};
        if (atributos) for (var k in atributos) attrs[k] = atributos[k];
        // 'default' CONTA COMO NÃO DECLARADO — medido no próprio launcher.
        //
        // A primeira versão disto só preenchia quando o atributo vinha `undefined`, para não passar
        // por cima de quem pediu alguma coisa. Só que o three.js SEMPRE preenche: ele desestrutura
        // os parâmetros com `powerPreference = 'default'` e repassa o objeto completo para o
        // `getContext`. Resultado medido no contexto do app: `powerPreference: "default"`, ou seja,
        // a dica nunca chegava ao driver. 'default' é literalmente "não tenho preferência" na
        // especificação — tratá-lo como declaração era respeitar um silêncio como se fosse escolha.
        // Um valor de verdade ('low-power' ou 'high-performance') continua sendo respeitado.
        if (attrs.powerPreference === undefined || attrs.powerPreference === 'default') {
          attrs.powerPreference = 'high-performance';
        }
        if (attrs.failIfMajorPerformanceCaveat === undefined) attrs.failIfMajorPerformanceCaveat = false;
        atributos = attrs;
      }
      return real.call(this, tipo, atributos);
    }
    patched.__openGwPatched = true;
    proto.getContext = patched;
  }

  // ── Freio quando a página está escondida ──────────────────────────────────────────────────
  //
  // Não substitui o `_render3dPaused` do app (que decide se DESENHA); corta o custo do
  // agendamento quando nem o app nem o compositor têm o que fazer.
  function installHiddenThrottle() {
    if (typeof document === 'undefined' || typeof document.addEventListener !== 'function') return;
    var sync = function () {
      frameBudgetMs = document.hidden ? HIDDEN_FRAME_MS : MIN_FRAME_MS;
    };
    document.addEventListener('visibilitychange', sync, false);
    sync();
  }

  // ── Ajustes no renderizador depois que ele existe ─────────────────────────────────────────
  //
  // O bundle publica `window.__renderer` e `window.__scene` (ele já fazia isso para depuração).
  // É por aí que dá para mexer no que só existe depois da cena montada, sem tocar no 1,9 MB de
  // bundle que não tem fonte neste repositório.
  //
  // Trocar `mapSize` com o mapa já alocado não faz nada sozinho: o alvo de render fica com o
  // tamanho antigo até ser descartado. Por isso o `dispose()` e o `map = null` antes.
  function installViewerPerfTuning() {
    if (typeof window === 'undefined' || typeof setInterval !== 'function') return;
    var tentativas = 0;
    var relogio = setInterval(function () {
      tentativas++;
      var r = window.__renderer;
      var cena = window.__scene;
      if (!r || !cena) {
        // 60 tentativas a 250 ms = 15 s. Passou disso, o launcher não é esta build e não há o
        // que ajustar — desistir calado é melhor que um intervalo eterno.
        if (tentativas > 60) clearInterval(relogio);
        return;
      }
      clearInterval(relogio);
      var relatorio = { sombras: [], powerPreference: null };
      try {
        var ctx = r.getContext && r.getContext();
        if (ctx && ctx.getContextAttributes) {
          relatorio.powerPreference = ctx.getContextAttributes().powerPreference || null;
        }
      } catch (e) { /* contexto perdido: não é motivo para abortar o resto */ }

      var ehAndroid = !!(window.__perfMobile && window.__perfMobile());
      if (ehAndroid) {
        try {
          cena.traverse(function (o) {
            var sh = o && o.shadow;
            if (!sh || !sh.mapSize) return;
            var lado = Math.max(sh.mapSize.x, sh.mapSize.y);
            if (lado <= SHADOW_MAP_MAX_ANDROID) return;
            if (sh.map && typeof sh.map.dispose === 'function') sh.map.dispose();
            sh.map = null;
            if (sh.mapPass && typeof sh.mapPass.dispose === 'function') {
              sh.mapPass.dispose();
              sh.mapPass = null;
            }
            sh.mapSize.set(SHADOW_MAP_MAX_ANDROID, SHADOW_MAP_MAX_ANDROID);
            relatorio.sombras.push((o.name || o.type) + ' ' + lado + '->' + SHADOW_MAP_MAX_ANDROID);
          });
          if (relatorio.sombras.length && r.shadowMap) r.shadowMap.needsUpdate = true;
        } catch (e) {
          console.warn('[OpenGW] não foi possível reduzir o mapa de sombra', e);
        }
      }
      relatorio.vidroLeve = !!(document.documentElement && document.documentElement.classList.contains(CLASSE_VIDRO_LEVE));
      relatorio.clearcoatZerado = [];
      relatorio.alivios = 0;
      window.__openGwPerf = relatorio;
      if (ehAndroid) {
        zerarClearcoatResidual(cena, relatorio);
        // O modelo do carro, as rodas do catálogo e o trem de força chegam DEPOIS do renderizador.
        // Três passadas cobrem a partida inteira; trocar de carroceria depois disso não passa por
        // aqui (o custo do que sobra é o de antes, nada quebra).
        [5000, 15000, 30000].forEach(function (ms) {
          setTimeout(function () { zerarClearcoatResidual(cena, relatorio); }, ms);
        });
      }
      console.info('[OpenGW] ajustes de desempenho aplicados', relatorio);
    }, 250);
  }

  // ── Clearcoat que não aparece mas cobra ───────────────────────────────────────────────────
  //
  // Medido na cena montada: dois materiais "Brushed metal" vêm do GLB com clearcoat = 0.001. O
  // three.js decide o shader por `clearcoat > 0`, então 0,001 — invisível — compila e roda a segunda
  // camada de especular inteira em cada pixel dessas peças. Zerar abaixo do limiar não muda um
  // pixel e tira esse ramo do shader. A pintura (1.0), os vidros (1.0) e o disco de freio (0.25)
  // ficam como estão: ali o verniz aparece.
  var CLEARCOAT_INVISIVEL = 0.05;

  function zerarClearcoatResidual(cena, relatorio) {
    try {
      cena.traverse(function (o) {
        if (!o || !o.isMesh) return;
        var mats = Array.isArray(o.material) ? o.material : [o.material];
        for (var i = 0; i < mats.length; i++) {
          var m = mats[i];
          if (!m || !m.isMeshPhysicalMaterial) continue;
          if (!(m.clearcoat > 0) || m.clearcoat >= CLEARCOAT_INVISIVEL) continue;
          relatorio.clearcoatZerado.push((m.name || m.type) + ' ' + m.clearcoat);
          m.clearcoat = 0;
          m.needsUpdate = true;
        }
      });
    } catch (e) {
      console.warn('[OpenGW] não foi possível ajustar o clearcoat', e);
    }
  }

  // ── Vidro sem desfoque no Android ─────────────────────────────────────────────────────────
  //
  // Os painéis e botões por cima do 3D usam `backdrop-filter: blur(...)` (63 regras no bundle).
  // O desfoque é refeito pelo compositor TODA vez que o que está atrás muda — e atrás está o canvas
  // WebGL, que muda a cada quadro enquanto a câmera anda, a porta abre ou a roda gira. Com o carro
  // parado a cena não redesenha e o desfoque sai de graça; em movimento ele é uma passada de blur
  // por elemento visível por quadro, em cima de uma GPU que já é limitada por preenchimento.
  //
  // Só o desfoque sai. A cor de fundo translúcida de cada elemento continua (ela vem das variáveis
  // de tema, então claro/escuro/noite seguem certos): o vidro passa de "fosco" para "fumê".
  //
  // Para comparar no carro: localStorage.opengw_vidro_fosco = '1' e recarregar devolve o desfoque.
  var CLASSE_VIDRO_LEVE = 'opengw-vidro-leve';

  function installVidroLeve() {
    if (typeof document === 'undefined' || !document.documentElement) return;
    var android = false;
    try {
      var qs = new URLSearchParams((location.search || '').replace(/^[#?]/, ''));
      var hs = new URLSearchParams((location.hash || '').replace(/^[#?]/, ''));
      // Mesmo critério do bundle para `_perfMobile` (ele ainda não existe a esta altura).
      android = qs.has('android') || hs.has('android');
    } catch (e) { /* URL estranha: fica como está */ }
    if (!android) return;
    try {
      if (window.localStorage && localStorage.getItem('opengw_vidro_fosco') === '1') return;
    } catch (e) { /* armazenamento bloqueado: segue com o vidro leve */ }
    var css = document.createElement('style');
    css.id = 'opengw-vidro-leve';
    css.textContent =
      'html.' + CLASSE_VIDRO_LEVE + ' *,html.' + CLASSE_VIDRO_LEVE + ' *::before,html.' + CLASSE_VIDRO_LEVE + ' *::after{' +
      '-webkit-backdrop-filter:none!important;backdrop-filter:none!important}';
    (document.head || document.documentElement).appendChild(css);
    document.documentElement.classList.add(CLASSE_VIDRO_LEVE);
  }

  // ── Alívio de memória pedido pelo Android ─────────────────────────────────────────────────
  //
  // `MainActivity.onTrimMemory` chama isto quando o sistema avisa que a memória está apertando ou
  // que o launcher saiu da frente. Só solta o que o app recria sozinho quando precisar:
  //   - o renderizador das miniaturas de roda (um SEGUNDO contexto WebGL, recriado por
  //     `_ensureThumbRenderer` na próxima vez que o catálogo abrir);
  //   - as listas de render do renderizador principal (refeitas no próximo quadro).
  // Nada de textura, malha ou shader do carro: soltar isso custaria uma recarga visível ao voltar.
  function aliviarMemoria(nivel) {
    var app = window.__app;
    var r = window.__renderer;
    try {
      if (app && typeof app._disposeThumbRenderer === 'function' && app._thumbRenderer) app._disposeThumbRenderer();
    } catch (e) { /* segue para o resto */ }
    try {
      if (r && r.renderLists && typeof r.renderLists.dispose === 'function') r.renderLists.dispose();
    } catch (e) { /* idem */ }
    if (window.__openGwPerf) window.__openGwPerf.alivios = (window.__openGwPerf.alivios || 0) + 1;
    console.info('[OpenGW] memória aliviada (nível ' + nivel + ')');
  }
  if (typeof window !== 'undefined') window.__openGwAliviarMemoria = aliviarMemoria;

  installGpuHints();
  installFrameBudget();
  installRenderScale();
  installHiddenThrottle();
  installVidroLeve();
  installViewerPerfTuning();

  // ── O borrão branco embaixo do facho, à noite ─────────────────────────────────────────────
  //
  // O QUE FOI MEDIDO (no próprio launcher, com a cena montada)
  //
  //   floorMesh   y = 0.01755   opaco, escreve profundidade
  //   nightFloor  y = 0.004     aditivo, transparente, NÃO escreve profundidade
  //
  // O `nightFloor` é o plano que recebe os holofotes e desenha a poça de luz dos faróis. Ele
  // está 13 milímetros ABAIXO de um chão opaco. Onde o teste de profundidade decide certo, o
  // chão ganha e a poça some; onde a precisão do buffer não separa dois planos tão juntos, ele
  // vaza por cima. Qual metade vaza depende do ângulo da câmera — e é isso que pisca.
  //
  // O 0.004 não foi escrito errado: ele foi escrito para um chão em y = 0. Quem sobe o chão é a
  // altura de suspensão (`floorMesh._rideBaseY`, aplicada em tempo de execução), então o defeito
  // só aparece depois que essa altura entra em jogo — e some se o carro voltar para o piso
  // original. Por isso a correção não é um número novo: é manter a poça SEMPRE um pouco acima do
  // chão, seja qual for a altura dele no momento.
  //
  // A folga mantém a ordem original (poça acima do chão, sombra acima da poça) e é grande o
  // bastante para o buffer de profundidade distinguir os três planos.
  var GROUND_GAP = 0.006;

  function installGroundOrder() {
    var applied = 0;
    setInterval(function () {
      var app = window.__app;
      if (!app || !app.floorMesh || !app.nightFloor) return;
      var floorY = app.floorMesh.position.y;
      var wanted = floorY + GROUND_GAP;
      if (Math.abs(app.nightFloor.position.y - wanted) < 1e-4) return;
      app.nightFloor.position.y = wanted;
      // A sombra de contato precisa continuar POR CIMA da poça, senão a luz aditiva apaga a
      // sombra do carro em vez de conviver com ela.
      if (app.shadowCatcher) app.shadowCatcher.position.y = floorY + GROUND_GAP * 2;
      if (++applied === 1) {
        console.log('[OpenGW] poça de luz reposicionada acima do chão (chão y=' + floorY.toFixed(5) + ')');
      }
    }, 500);
  }

  installGroundOrder();

  // ── Barra de carregamento do modelo 3D ────────────────────────────────────────────────────
  //
  // A primeira abertura leva vários segundos com a palavra "OPEN GW" escrevendo-se na tela e nada
  // dizendo se falta muito. O progresso REAL já existe e ninguém mostrava: o bloco de pré-carga do
  // index.html publica `window.__bootGlb = { loaded, total }` em bytes, atualizado a cada pedaço
  // que chega da rede. A barra abaixo lê esses dois números — não é uma animação fingindo
  // progresso, é a transferência do GLB do carro.
  //
  // Amostragem por temporizador, e não por callback: o comentário do próprio index.html registra
  // que um callback por pedaço deixou a abertura 13 s mais lenta.
  var BOOT_PROGRESS_POLL_MS = 120;

  function installBootProgress() {
    var splash = document.getElementById('hv-splash');
    if (!splash || !window.__bootGlb) return;

    var css = document.createElement('style');
    css.textContent =
      '#opengw-boot-progress{position:absolute;left:50%;transform:translateX(-50%);' +
      'bottom:calc(var(--hv-launcher-bottom, 60px) + 26px);width:min(46vw,520px);z-index:21;' +
      'font:600 10px/1 "IBM Plex Sans",system-ui,sans-serif;letter-spacing:.16em;' +
      'color:rgba(255,255,255,.55);text-align:center;opacity:0;transition:opacity .4s ease}' +
      '#opengw-boot-progress.on{opacity:1}' +
      '#opengw-boot-progress .trilho{height:3px;border-radius:3px;background:rgba(255,255,255,.14);' +
      'overflow:hidden;margin-bottom:8px}' +
      '#opengw-boot-progress .barra{height:100%;width:0%;border-radius:3px;background:#4A9EFF;' +
      'transition:width .18s linear}';
    document.head.appendChild(css);

    var box = document.createElement('div');
    box.id = 'opengw-boot-progress';
    box.innerHTML = '<div class="trilho"><div class="barra"></div></div><span class="rotulo">CARREGANDO O MODELO 3D</span>';
    splash.appendChild(box);

    var barra = box.querySelector('.barra');
    var rotulo = box.querySelector('.rotulo');
    var timer = setInterval(function () {
      var s = window.__bootGlb;
      if (!s) return;
      if (s.failed) {
        // Falhou a pré-carga: o carregador do app busca o arquivo por conta própria. Prometer um
        // progresso que já não é medido seria mentir, então a barra sai de cena.
        clearInterval(timer);
        box.classList.remove('on');
        return;
      }
      // Sem `total` (resposta sem content-length) não há fração para mostrar — só os megabytes
      // que já chegaram, que continuam sendo informação verdadeira.
      if (!s.total) {
        box.classList.add('on');
        rotulo.textContent = 'CARREGANDO O MODELO 3D · ' + (s.loaded / 1048576).toFixed(1) + ' MB';
        return;
      }
      var pct = Math.max(0, Math.min(100, Math.round((s.loaded / s.total) * 100)));
      box.classList.add('on');
      barra.style.width = pct + '%';
      rotulo.textContent = 'CARREGANDO O MODELO 3D · ' + pct + '%';
      if (s.loaded >= s.total) {
        clearInterval(timer);
        barra.style.width = '100%';
        rotulo.textContent = 'MONTANDO A CENA';
      }
    }, BOOT_PROGRESS_POLL_MS);
  }

  if (document.getElementById('hv-splash')) {
    installBootProgress();
  } else {
    document.addEventListener('DOMContentLoaded', installBootProgress);
  }

  var BASE = 'http://127.0.0.1:8766';
  var IDENTITY_CACHE_KEY = 'opengw_identity_v1';
  var OPENGW_PACKAGE = 'br.com.opengw';
  var TRIMS = [
    { key: 'phev34', label: 'H6 PHEV34', hint: 'Bateria 34 kWh' },
    { key: 'phev19', label: 'H6 PHEV19', hint: 'Bateria 19 kWh' },
    { key: 'hev', label: 'H6 HEV', hint: 'Híbrido sem tomada' },
    { key: 'gt', label: 'H6 GT', hint: 'Carroceria GT' }
  ];

  // Cores de fábrica do H6. O carro NÃO publica a cor da pintura em lugar nenhum do barramento
  // (procurado em todas as 818 chaves: só existe cor de luz ambiente), e o VIN também não a
  // carrega. Por isso a cor é sempre escolha de quem dirige — não há o que detectar.
  var COLORS = [
    { hex: '#F4F5F7', label: 'Branco' },
    { hex: '#0B0B0D', label: 'Preto' },
    { hex: '#9AA0A6', label: 'Prata' },
    { hex: '#2C3E63', label: 'Azul' },
    { hex: '#7A1F24', label: 'Vermelho' },
    { hex: '#4A4E54', label: 'Cinza' }
  ];

  function timeoutFetch(url, options, ms) {
    options = options || {};
    // O WebView deste carro é antigo; AbortController existe, mas uma corrida com Promise.race
    // não depende disso e falha do mesmo jeito se o OPEN GW não estiver de pé.
    return Promise.race([
      fetch(url, options),
      new Promise(function (_, reject) {
        setTimeout(function () { reject(new Error('sem resposta do OPEN GW')); }, ms || 4000);
      })
    ]);
  }

  function get(path) {
    return timeoutFetch(BASE + path, { method: 'GET' }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }

  /**
   * `ms` existe para as poucas rotas que fazem trabalho de verdade antes de responder. Trocar de
   * perfil de motorista, por exemplo, grava ~150 preferências e escreve no barramento chave por
   * chave; com os 4 s padrão a promessa era rejeitada NO MEIO da aplicação — o carro continuava
   * aplicando e a tela dizia que tinha falhado, que é a pior das duas mentiras possíveis.
   */
  function post(path, body, ms) {
    return timeoutFetch(BASE + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {})
    }, ms).then(function (r) {
      return r.json().then(function (json) {
        if (!r.ok || json.ok === false) throw new Error(json.error || ('HTTP ' + r.status));
        return json;
      });
    });
  }

  function readCache() {
    try { return JSON.parse(localStorage.getItem(IDENTITY_CACHE_KEY) || 'null'); }
    catch (e) { return null; }
  }

  function writeCache(value) {
    try { localStorage.setItem(IDENTITY_CACHE_KEY, JSON.stringify(value)); } catch (e) {}
  }

  var OpenGW = {
    available: false,
    identity: null,
    get: get,
    post: post,
    readCache: readCache,
    writeCache: writeCache,
    TRIMS: TRIMS,
    COLORS: COLORS
  };
  window.OpenGW = OpenGW;

  /**
   * Onde pendurar qualquer coisa que precise aparecer por cima da tela.
   *
   * Tem que ser DENTRO de #hv-root. O root é `position: fixed` e cria um contexto de
   * empilhamento próprio, então tudo que fica no <body> é desenhado ATRÁS dos filhos dele —
   * medido: a pílula de páginas (z-index 74) e as bordas de arrasto apareciam por cima de um
   * painel que estava no body com z-index 40, e o painel é que engolia o toque. Dentro do root
   * os z-index passam a valer entre si e dá para escolher a ordem de propósito.
   *
   * O root só existe depois que o React desenha, e estes arquivos carregam antes disso; quem
   * chama precisa aceitar `null` e tentar de novo.
   */
  function mountRoot() {
    return document.getElementById('hv-root');
  }
  OpenGW.mountRoot = mountRoot;

  /**
   * Chama `cb` quando o app estiver de pé, com o #hv-root dele já montado.
   *
   * Não basta o #hv-root existir: o modelo da página vem escrito no HTML como
   * espaço reservado e o React troca essa árvore inteira quando monta pela primeira vez.
   * Pendurar o painel no root antes disso é pendurar no nó que vai ser jogado fora — foi
   * exatamente o que aconteceu no teste: CSS no lugar, painel inexistente. `window.__app`
   * com a lista de páginas já lida é o sinal de que a montagem terminou.
   */
  function whenMounted(cb) {
    var ready = function () {
      var app = window.__app;
      return mountRoot() && app && app._desktops && app._desktops.length ? mountRoot() : null;
    };
    var host = ready();
    if (host) { cb(host); return; }
    var tries = 0;
    var timer = setInterval(function () {
      var node = ready();
      if (node) { clearInterval(timer); cb(node); return; }
      // ~30 s. Passou disso, o app não subiu e não há tela para decorar.
      if (++tries > 300) clearInterval(timer);
    }, 100);
  }
  OpenGW.whenMounted = whenMounted;

  /**
   * O trilho dos atalhos do OPEN GW, no alto à direita.
   *
   * POR QUE UM TRILHO E NÃO TRÊS `top:` DIFERENTES
   *
   * As três pílulas (perfil, projeção, Painel Esportivo) nasceram empilhadas, cada uma com o
   * seu `top:` absoluto — 14, 62 e 118. Empilhadas elas ocupavam a coluna inteira à direita e
   * a de baixo, que só aparece com o SPORT+ ligado, entrava por cima da fileira de widgets do
   * launcher nas telas mais baixas. Em linha, as três somam a altura de UMA.
   *
   * O trilho é ancorado pela DIREITA e cresce para a esquerda. Isso é o que faz o Painel
   * Esportivo poder aparecer e sumir sem empurrar as outras duas: quem entra é o item mais à
   * esquerda (`order: 1`), e o lado que se move é o lado onde não há dedo nenhum. Com o trilho
   * ancorado pela esquerda, ligar o SPORT+ deslocaria o botão de projeção no instante em que
   * alguém fosse tocá-lo.
   *
   * A ORDEM É DECLARADA, NÃO HERDADA DO DOM
   *
   * Duas pílulas são instaladas por este arquivo e a terceira por `openGwProfile.js`, e quem
   * chega primeiro depende de quando `whenMounted` dispara em cada um. `order:` fixa o que se
   * vê independentemente disso.
   *
   * O trilho não recebe toque (`pointer-events:none`); só os filhos recebem. Senão a faixa
   * inteira, gaps inclusive, roubaria o toque do cenário 3D que passa por trás.
   *
   * POR QUE `top: 42px` E NÃO 14
   *
   * O seletor de áreas de trabalho do próprio launcher (`.hv-desktop-switcher`: as duas
   * setas, o nome e os pontinhos) ocupa de y=0 a y=37 dentro do #hv-root, centrado, e está
   * em z-index 74 — acima destes 61. Ele é invisível em repouso, mas continua recebendo
   * toque. Empilhadas, as pílulas começavam em 14 e só a de perfil ficava nessa faixa, à
   * direita do seletor; em linha, com o SPORT+ ligado, a do Painel Esportivo caía bem em
   * cima dele e o toque abria o editor de áreas de trabalho em vez do painel — conferido
   * no emulador. 42px põe o trilho inteiro abaixo dos 37, e o launcher fica com todos os
   * pixels do seletor dele. A altura do seletor é a mesma em 1080x600 e em 1920x720:
   * é cromo de tamanho fixo, não escala com a tela.
   */
  function trilhoDeAtalhos(host) {
    var existente = document.getElementById('opengw-atalhos');
    if (existente) return existente;

    if (!document.getElementById('opengw-atalhos-css')) {
      var css = document.createElement('style');
      css.id = 'opengw-atalhos-css';
      css.textContent =
        '#opengw-atalhos{position:absolute;z-index:61;pointer-events:none;' +
        'right:calc(var(--hv-safe-right, 0px) + 24px);top:42px;' +
        'display:flex;align-items:center;justify-content:flex-end;gap:10px;' +
        // O teto é a largura ÚTIL da tela, não metade dela. A faixa em que o trilho mora
        // (y de 14 a 58) está livre de ponta a ponta: o cartão de mídia começa em y=120 e
        // o relógio do launcher é desenhado fora do #hv-root.
        //
        // Um teto de metade da tela parecia folgado no navegador — onde --hv-safe-right é
        // vazio — e apertava no carro, onde o launcher define a área segura em tempo de
        // execução (appLauncherClient.js). Com o SPORT+ ligado, as três pílulas passavam do
        // teto e a ÚNICA que encolhe, a do perfil, comia a folga inteira: o nome de quem
        // está dirigindo sumia e sobrava um círculo com o ícone. Conferido no emulador.
        'max-width:calc(100% - var(--hv-safe-left, 0px) - var(--hv-safe-right, 0px) - 48px);}' +
        '#opengw-atalhos > *{pointer-events:auto;flex:0 0 auto;}' +
        // Dois ids de propósito. Cada pílula declara a própria regra `#opengw-...`, de mesma
        // especificidade que `#opengw-atalhos > *`: qual venceria dependeria de qual arquivo
        // injetou o <style> primeiro, e isso é uma corrida entre dois `whenMounted`. Com dois
        // ids, o trilho manda sempre.
        '#opengw-atalhos > #opengw-profile,#opengw-atalhos > #opengw-projection,' +
        '#opengw-atalhos > #opengw-sportsdisplay{position:relative;' +
        'top:auto;right:auto;left:auto;bottom:auto;}' +
        // Só o nome do perfil encolhe — os outros dois rótulos são fixos e curtos.
        '#opengw-atalhos > #opengw-profile{flex:0 1 auto;min-width:0;}' +
        '#opengw-atalhos > #opengw-sportsdisplay{order:1}' +
        '#opengw-atalhos > #opengw-projection{order:2}' +
        '#opengw-atalhos > #opengw-profile{order:3}';
      document.head.appendChild(css);
    }

    var trilho = document.createElement('div');
    trilho.id = 'opengw-atalhos';
    host.appendChild(trilho);
    return trilho;
  }
  OpenGW.trilhoDeAtalhos = trilhoDeAtalhos;

  /** Abre o app do OPEN GW por cima do launcher, pela ponte nativa que já existe. */
  OpenGW.openApp = function () {
    var B = window.AppLauncherBridge;
    if (!B) return false;
    try {
      if (typeof B.maximizeApp === 'function') { B.maximizeApp(OPENGW_PACKAGE); return true; }
      if (typeof B.launchAppInPopup === 'function') { B.launchAppInPopup(OPENGW_PACKAGE); return true; }
      if (typeof B.launchAppInSlot === 'function') { B.launchAppInSlot(OPENGW_PACKAGE, 'left'); return true; }
    } catch (e) {
      console.warn('[OpenGW] não foi possível abrir o app', e);
    }
    return false;
  };

  /**
   * Faz o modelo valer. O caminho é a mesma `?model=` que o boot deste app já entende — ela
   * escolhe o GLB antes do primeiro quadro, decide as rodas e o pacote de energia. Mexer no
   * estado interno depois de carregado trocaria a carroceria com o carro já na tela e deixaria
   * rodas e powertrain fora de sincronia; recarregar com o preset certo é o caminho que o próprio
   * app foi escrito para seguir.
   */
  function applyTrim(trim, reload) {
    var cache = readCache() || {};
    cache.trim = trim;
    writeCache(cache);
    if (!reload) return;
    try {
      var hash = new URLSearchParams((location.hash || '').replace(/^[#?]/, ''));
      hash.set('model', trim);
      location.hash = hash.toString();
      location.reload();
    } catch (e) {
      console.warn('[OpenGW] falha ao aplicar o modelo', e);
    }
  }
  OpenGW.applyTrim = applyTrim;

  /** Pinta a carroceria com a cor escolhida, se o app expuser o ponto de pintura. */
  function applyColor(hex) {
    var cache = readCache() || {};
    cache.colorHex = hex;
    writeCache(cache);
    try {
      var app = window.__app;
      if (app && typeof app.setState === 'function') app.setState({ paint: hex });
    } catch (e) {
      console.warn('[OpenGW] falha ao aplicar a cor', e);
    }
  }
  OpenGW.applyColor = applyColor;

  // ── Identidade ──────────────────────────────────────────────────────────────────────────
  //
  // O boot já leu o cache (bloco no topo do index.html) e ajustou a URL. Aqui só confirmamos com
  // o carro, em segundo plano, sem segurar o primeiro quadro.

  function refreshIdentity() {
    return get('/identity').then(function (data) {
      OpenGW.available = true;
      OpenGW.identity = data;
      var cache = readCache();

      if (data.trim) {
        // O carro (ou uma escolha anterior gravada nele) sabe o modelo.
        if (!cache || cache.trim !== data.trim) {
          writeCache({ trim: data.trim, colorHex: (cache && cache.colorHex) || data.colorHex || null });
          // Só recarrega se o que está na tela não for o modelo certo. Recarregar à toa
          // custaria ao motorista uma tela preta de vários segundos a cada partida.
          if (!cache) applyTrim(data.trim, true);
        }
        if (data.colorHex && (!cache || !cache.colorHex)) applyColor(data.colorHex);
        return data;
      }

      // O carro não identificou o modelo. Perguntar — uma vez só.
      if (!cache || !cache.trim) showModelPicker(data);
      return data;
    }).catch(function (err) {
      OpenGW.available = false;
      console.warn('[OpenGW] identidade indisponível:', err.message);
      // Sem OPEN GW no ar e sem escolha anterior, perguntar mesmo assim: o launcher precisa de
      // uma carroceria para desenhar, e ficar no palpite silencioso é o que se quer evitar.
      if (!readCache()) showModelPicker(null);
      return null;
    });
  }
  OpenGW.refreshIdentity = refreshIdentity;

  // ── Primeira execução: escolher o modelo ────────────────────────────────────────────────

  function showModelPicker(identity) {
    if (document.getElementById('opengw-picker')) return;
    var chosenTrim = null;
    var chosenColor = (readCache() || {}).colorHex || COLORS[0].hex;

    var wrap = document.createElement('div');
    wrap.id = 'opengw-picker';
    wrap.className = 'opengw-modal';

    var reason = identity && identity.reason
      ? identity.reason
      : 'O OPEN GW não respondeu, então não foi possível perguntar ao carro.';

    wrap.innerHTML =
      // O botão fica FORA da área que rola: numa central baixa ele cairia abaixo da dobra e a
      // tela pareceria travada, com as opções à vista e nenhuma forma de confirmar.
      '<div class="opengw-modal-card">' +
        '<div class="opengw-modal-body">' +
          '<div class="opengw-modal-title">Qual é este carro?</div>' +
          '<div class="opengw-modal-sub">Não deu para identificar o modelo automaticamente. ' +
            'Escolha uma vez — o launcher grava a resposta junto com o que este carro reporta e ' +
            'não pergunta de novo.</div>' +
          '<div class="opengw-modal-reason">' + escapeHtml(reason) + '</div>' +
          '<div class="opengw-label">Modelo</div>' +
          '<div class="opengw-grid" id="opengw-trims"></div>' +
          '<div class="opengw-label">Cor da pintura</div>' +
          '<div class="opengw-modal-note">A cor não é informada pelo carro em nenhuma via — ' +
            'nem pelos sinais, nem pelo VIN. Esta escolha é só sua.</div>' +
          '<div class="opengw-colors" id="opengw-colors"></div>' +
        '</div>' +
        '<button type="button" class="opengw-primary" id="opengw-confirm" disabled>Confirmar</button>' +
      '</div>';

    (mountRoot() || document.body).appendChild(wrap);

    var allowed = (identity && identity.suggestedTrims)
      ? String(identity.suggestedTrims).split(',').filter(Boolean)
      : TRIMS.map(function (t) { return t.key; });

    var trimsHost = wrap.querySelector('#opengw-trims');
    TRIMS.forEach(function (trim) {
      if (allowed.indexOf(trim.key) === -1) return;
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'opengw-choice';
      b.innerHTML = '<span class="opengw-choice-name">' + trim.label + '</span>' +
        '<span class="opengw-choice-hint">' + trim.hint + '</span>';
      b.onclick = function () {
        chosenTrim = trim.key;
        Array.prototype.forEach.call(trimsHost.children, function (c) { c.classList.remove('is-on'); });
        b.classList.add('is-on');
        wrap.querySelector('#opengw-confirm').disabled = false;
      };
      trimsHost.appendChild(b);
    });

    var colorsHost = wrap.querySelector('#opengw-colors');
    COLORS.forEach(function (color) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'opengw-swatch';
      b.style.background = color.hex;
      b.title = color.label;
      if (color.hex === chosenColor) b.classList.add('is-on');
      b.onclick = function () {
        chosenColor = color.hex;
        Array.prototype.forEach.call(colorsHost.children, function (c) { c.classList.remove('is-on'); });
        b.classList.add('is-on');
      };
      colorsHost.appendChild(b);
    });

    wrap.querySelector('#opengw-confirm').onclick = function () {
      if (!chosenTrim) return;
      this.disabled = true;
      this.textContent = 'Salvando…';
      // Gravar no carro é o que torna a escolha permanente para ESTE veículo: o OPEN GW guarda
      // a resposta junto da assinatura de identidade que ele leu. Se o OPEN GW não estiver de
      // pé, a escolha ainda vale localmente — só não sobrevive a uma reinstalação do launcher.
      post('/identity/select', { trim: chosenTrim, colorHex: chosenColor })
        .catch(function (e) { console.warn('[OpenGW] escolha não gravada no carro:', e.message); })
        .then(function () {
          writeCache({ trim: chosenTrim, colorHex: chosenColor });
          applyTrim(chosenTrim, true);
        });
    };
  }
  OpenGW.showModelPicker = showModelPicker;

  function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }
  OpenGW.escapeHtml = escapeHtml;

  // ── Boot ────────────────────────────────────────────────────────────────────────────────

  // ── Navegação entre páginas ───────────────────────────────────────────────────────────────
  //
  // O QUE MUDA
  //
  //   - a pílula de páginas do topo (nome, contador e pontinhos) sai da vista;
  //   - duas setas grandes, uma em cada canto da tela, trocam de página;
  //   - dois dedos arrastando para o lado, em qualquer ponto, também trocam.
  //
  // POR QUE A PÍLULA CONTINUA NO DOM
  //
  // Toda a lógica de paginação — ordem das páginas, laço no fim da lista, animação, persistência
  // do índice — vive no bundle do app, que não tem código-fonte aqui. Os botões ‹ e › dela são a
  // porta de entrada dessa lógica. Escondê-los com `display:none` e chamar `.click()` continua
  // funcionando (um clique sintético não exige que o elemento esteja visível), então as setas
  // novas e o gesto de dois dedos apenas ACIONAM os controles originais em vez de reimplementar
  // o que eles fazem. Nada de paginação foi reescrito, logo nada dela pode divergir.
  //
  // A pílula é escondida por CSS, não removida: o app a redesenha a cada troca de página, e um
  // nó removido voltaria no quadro seguinte.

  // ── Física do gesto (skill apple-design) ──────────────────────────────────────────────────
  //
  // Funções puras, sem DOM, para `scripts/test-viewer-gesto-pagina.mjs` recortar e exercitar.
  // Elas existem porque gesto e animação têm de ser a MESMA coisa: o que decide o resultado é a
  // trajetória do dedo, não um interruptor de distância.

  /** Distância horizontal PROJETADA (não percorrida) que confirma a troca de página. */
  var PAGINA_COMPROMISSO_PX = 70;
  /** Acima disto o arrasto é vertical e não é troca de página. */
  var TWO_FINGER_VERTICAL_TOLERANCE = 1.0;
  /**
   * Teto de páginas por gesto. A skill manda transformar entrada pequena em saída grande — um
   * lançamento forte poderia virar três páginas. Aqui fica em UMA de propósito, por dois motivos
   * que valem mais que o efeito: num carro em movimento uma folha virando sozinha três vezes é
   * distração, e a animação de página é do bundle do app, que não promete enfileirar três
   * cliques sintéticos no mesmo quadro. A projeção decide SE vira, não quantas.
   */
  var PAGINA_MAX_POR_GESTO = 1;
  /** Movimento mínimo antes de decidir o eixo do gesto — histerese, para o dedo não gaguejar. */
  var GESTO_HISTERESE_PX = 10;

  /**
   * Onde um lançamento PARA, dada a velocidade no instante em que o dedo sai (px/s).
   *
   * Decaimento exponencial, que é o que a Apple publica no código de exemplo de "Designing Fluid
   * Interfaces" e o que governa a inércia de rolagem do iOS — NÃO a fórmula de livro v²/(2a),
   * que dá outro número. 0.998 é a taxa de rolagem normal; 0.99 seria mais seco.
   */
  function projetar(velocidade, desaceleracao) {
    var d = typeof desaceleracao === 'number' ? desaceleracao : 0.998;
    return (velocidade / 1000) * d / (1 - d);
  }

  /**
   * Resistência progressiva depois do limite, em vez de parar seco: quanto mais passa, menos
   * acompanha. Um fim de curso duro lê como "travou"; a resistência lê como "responde, mas
   * acabou". Mesma fórmula do `rubberband()` do lado Compose (ui/theme/Motion.kt).
   */
  function elastico(excesso, dimensao, constante) {
    var c = typeof constante === 'number' ? constante : 0.55;
    if (!(dimensao > 0)) return excesso;
    var sinal = excesso < 0 ? -1 : 1;
    var m = Math.abs(excesso);
    return sinal * (m * dimensao * c) / (dimensao + c * m);
  }

  /**
   * Quantas páginas virar ao soltar. Positivo avança (arrasto para a ESQUERDA, como virar a
   * folha), negativo volta, zero desiste.
   *
   * O valor julgado é `dx + projeção(velocidade)`, não `dx`: um toque curto e rápido vira a
   * página, e um arrasto longo e lento que já parou não vira. É também o que permite DESISTIR —
   * arrastar de volta antes de soltar zera a projeção, coisa que a versão anterior não permitia
   * porque disparava no meio do gesto, assim que cruzava 70 px, com o dedo ainda na tela.
   */
  function paginasDoGesto(dx, velocidade) {
    var destino = dx + projetar(velocidade);
    var passos = Math.floor(Math.abs(destino) / PAGINA_COMPROMISSO_PX);
    if (passos < 1) return 0;
    if (passos > PAGINA_MAX_POR_GESTO) passos = PAGINA_MAX_POR_GESTO;
    return destino < 0 ? passos : -passos;
  }

  /**
   * Força do aviso visual (0 = repouso, 1 = no ponto de virar) a partir do arrasto. Passando de
   * 1 ela continua subindo, mas com resistência e teto — a seta avisa "aqui já basta" em vez de
   * crescer sem fim.
   */
  function forcaDoArrasto(dx) {
    var bruto = Math.abs(dx) / PAGINA_COMPROMISSO_PX;
    if (bruto <= 1) return bruto;
    return 1 + elastico(bruto - 1, 1) * 0.12;
  }

  /**
   * Velocidade horizontal em px/s a partir das últimas amostras do gesto. Uma janela de amostras,
   * não o último par: o último par sozinho pega o ruído do digitalizador e devolve velocidades
   * absurdas justo no quadro que decide o resultado.
   */
  function velocidadeDasAmostras(amostras) {
    if (!amostras || amostras.length < 2) return 0;
    var fim = amostras[amostras.length - 1];
    var ini = amostras[0];
    var dt = fim.t - ini.t;
    if (!(dt > 0)) return 0;
    return (fim.x - ini.x) / dt * 1000;
  }

  // ── Fim do bloco puro ─────────────────────────────────────────────────────────────────────

  /** A preferência de acessibilidade do sistema, lida a cada uso (o usuário pode trocar em voo). */
  function movimentoReduzido() {
    try { return window.matchMedia('(prefers-reduced-motion: reduce)').matches; } catch (e) { return false; }
  }

  /**
   * Mola criticamente amortecida (damping 1.0 da tabela da Apple) de `de` até 0, partindo com
   * `velocidade`. Solução analítica — x(t) = (x0 + (v0 + ω·x0)·t)·e^(−ω·t), ω = 2π/resposta — em
   * vez de integrar passo a passo: não acumula erro e não depende do intervalo entre quadros,
   * que aqui é o do orçamento de 24 fps e não o do painel.
   *
   * ω = 2π/resposta é a MESMA conversão resposta→rigidez do lado Compose (ui/theme/Motion.kt),
   * de propósito: o app e o launcher assentam no mesmo tempo.
   *
   * Devolve a função que cancela. Poder interromper não é extra: é o ponto 3 da skill — se o
   * usuário voltar a encostar enquanto a mola corre, ela para onde está e o dedo assume dali.
   */
  function molaAteZero(de, velocidade, resposta, aoQuadro) {
    var w = 2 * Math.PI / resposta;
    var t0 = null;
    var vivo = true;
    var id = 0;
    function passo(agora) {
      if (!vivo) return;
      if (t0 === null) t0 = agora;
      var t = (agora - t0) / 1000;
      var x = (de + (velocidade + w * de) * t) * Math.exp(-w * t);
      if (t >= resposta * 2 || Math.abs(x) < 0.004) { vivo = false; aoQuadro(0); return; }
      aoQuadro(x);
      id = requestAnimationFrame(passo);
    }
    id = requestAnimationFrame(passo);
    return function () { vivo = false; cancelAnimationFrame(id); };
  }

  function pageArrows() {
    var sw = document.querySelector('.hv-desktop-switcher');
    if (!sw) return null;
    var arrows = sw.querySelectorAll('.hv-desktop-arrow');
    if (arrows.length < 2) return null;
    return { prev: arrows[0], next: arrows[arrows.length - 1] };
  }

  function turnPage(direction) {
    var a = pageArrows();
    if (!a) return false;
    (direction < 0 ? a.prev : a.next).click();
    return true;
  }
  OpenGW.turnPage = turnPage;

  function installPageNavigation(host) {
    if (document.getElementById('opengw-page-nav')) return;

    var css = document.createElement('style');
    css.textContent =
      // A pílula do topo sai da vista mas continua clicável por código (ver acima).
      '.hv-desktop-switcher{opacity:0 !important;pointer-events:none !important}' +
      '#opengw-page-nav{position:absolute;inset:0;z-index:60;pointer-events:none}' +
      '#opengw-page-nav .seta{position:absolute;top:50%;--forca:0;' +
      'pointer-events:auto;width:56px;height:150px;display:flex;align-items:center;' +
      'justify-content:center;border:0;padding:0;cursor:pointer;' +
      'background:linear-gradient(90deg,rgba(0,0,0,.34),rgba(0,0,0,0));' +
      'color:rgba(255,255,255,.62);font:300 42px/1 system-ui,sans-serif;' +
      // `touch-action:manipulation` desliga o zoom de toque duplo nestes botões e, com ele, a
      // espera que o navegador guarda depois do primeiro toque para ver se vem um segundo.
      '-webkit-tap-highlight-color:transparent;touch-action:manipulation;' +
      // Opacidade e escala NÃO entram na transição: durante o gesto elas seguem o dedo quadro a
      // quadro, e uma transição aqui colocaria uma animação de 150 ms entre o dedo e a seta.
      'opacity:calc(.62 + .38 * var(--forca));' +
      'transform:translateY(-50%) scale(calc(1 + .16 * var(--forca)));' +
      'transition:color .18s ease,background .18s ease}' +
      // O realce do toque aparece AGORA; só a volta ao repouso é suavizada. Uma transição nos
      // dois sentidos atrasa em 180 ms a única coisa que confirma que o toque foi registrado.
      '#opengw-page-nav .seta:active{transition:none;color:#fff;background:rgba(79,158,255,.22)}' +
      '#opengw-page-nav .seta.direita{background:linear-gradient(270deg,rgba(0,0,0,.34),rgba(0,0,0,0))}' +
      // Promover a camada só enquanto o gesto corre: manter duas camadas extras a vida inteira
      // custa memória de GPU num aparelho que já está no limite renderizando o carro.
      '#opengw-page-nav.arrastando .seta{will-change:opacity,transform}' +
      // As setas encostam na borda mas param acima da barra do launcher, para não brigar com o
      // gesto de sistema que vive no rodapé.
      '#opengw-page-nav .seta.esquerda{left:0}' +
      '#opengw-page-nav .seta.direita{right:0}' +
      // Menos movimento: o aviso continua existindo (ele informa), só perde escala e brilho.
      '@media (prefers-reduced-motion: reduce){#opengw-page-nav .seta{transform:translateY(-50%);' +
      'opacity:calc(.62 + .18 * var(--forca));transition:none}}' +
      '@media (prefers-contrast: more){#opengw-page-nav .seta{color:#fff;' +
      'background:rgba(0,0,0,.72)}}';
    document.head.appendChild(css);

    var nav = document.createElement('div');
    nav.id = 'opengw-page-nav';
    nav.innerHTML =
      '<button type="button" class="seta esquerda" aria-label="Página anterior">‹</button>' +
      '<button type="button" class="seta direita" aria-label="Próxima página">›</button>';
    host.appendChild(nav);

    var setaEsq = nav.querySelector('.esquerda');
    var setaDir = nav.querySelector('.direita');

    function forca(el, v) { el.style.setProperty('--forca', String(v)); }

    setaEsq.addEventListener('click', function () { turnPage(-1); });
    setaDir.addEventListener('click', function () { turnPage(1); });

    // Dois dedos em qualquer lugar. `touchstart` é registrado na fase de CAPTURA para ver o
    // gesto antes dos widgets, e passivo: nada é cancelado, então um arrasto de um dedo dentro
    // de um widget continua chegando nele sem alteração nenhuma.
    //
    // O QUE MUDOU, e por quê: a versão anterior virava a página NO MEIO do gesto, no quadro em
    // que o arrasto cruzava 70 px, com os dedos ainda na tela — sem nada antes avisando que ia
    // acontecer e sem nenhuma forma de desistir. Agora o arrasto é acompanhado quadro a quadro
    // (a seta do lado para onde se vai cresce junto com o dedo), e o resultado é decidido ao
    // SOLTAR, pela projeção da velocidade. Arrastar de volta antes de soltar cancela.
    var gesto = null;
    var cancelarMola = null;

    function centro(e) {
      return {
        x: (e.touches[0].clientX + e.touches[1].clientX) / 2,
        y: (e.touches[0].clientY + e.touches[1].clientY) / 2
      };
    }

    function repousar(g, vel) {
      var alvo = g.dx < 0 ? setaDir : setaEsq;
      var outra = g.dx < 0 ? setaEsq : setaDir;
      forca(outra, 0);
      var de = parseFloat(alvo.style.getPropertyValue('--forca')) || 0;
      function fim() { nav.classList.remove('arrastando'); cancelarMola = null; }
      if (de === 0 || movimentoReduzido()) { forca(alvo, 0); fim(); return; }
      // A velocidade do dedo entra na mola em vez de ser descartada: sem isso existe uma emenda
      // visível entre arrastar e animar, que é justo o que separa "fluido" de "correto".
      // px/s viram força/s dividindo pela distância que vale 1 de força.
      var sentido = g.dx < 0 ? -1 : 1;
      cancelarMola = molaAteZero(de, (vel * sentido) / PAGINA_COMPROMISSO_PX, 0.3, function (x) {
        forca(alvo, Math.max(x, 0));
        if (x === 0) fim();
      });
    }

    document.addEventListener('touchstart', function (e) {
      if (!e.touches || e.touches.length !== 2) { gesto = null; return; }
      // Agarrar uma mola em voo: ela para onde está e o dedo assume dali, sem salto.
      if (cancelarMola) { cancelarMola(); cancelarMola = null; }
      var c = centro(e);
      gesto = { x0: c.x, y0: c.y, dx: 0, eixo: 0, amostras: [{ t: performance.now(), x: c.x }] };
    }, { capture: true, passive: true });

    document.addEventListener('touchmove', function (e) {
      if (!gesto || !e.touches || e.touches.length !== 2) return;
      var c = centro(e);
      var dx = c.x - gesto.x0;
      var dy = c.y - gesto.y0;
      // O eixo é decidido UMA vez, depois da histerese, e não muda mais. Reavaliar a cada quadro
      // faz o aviso piscar sempre que o dedo serpenteia na diagonal.
      if (gesto.eixo === 0) {
        if (Math.abs(dx) < GESTO_HISTERESE_PX && Math.abs(dy) < GESTO_HISTERESE_PX) return;
        if (Math.abs(dy) > Math.abs(dx) * TWO_FINGER_VERTICAL_TOLERANCE) { gesto = null; return; }
        gesto.eixo = 1;
        nav.classList.add('arrastando');
      }
      gesto.dx = dx;
      gesto.amostras.push({ t: performance.now(), x: c.x });
      if (gesto.amostras.length > 6) gesto.amostras.shift();
      var v = forcaDoArrasto(dx);
      forca(dx < 0 ? setaDir : setaEsq, v);
      forca(dx < 0 ? setaEsq : setaDir, 0);
    }, { capture: true, passive: true });

    function soltar(e) {
      if (!gesto) return;
      if (e && e.touches && e.touches.length >= 2) return;
      var g = gesto;
      gesto = null;
      if (g.eixo !== 1) return;
      var vel = velocidadeDasAmostras(g.amostras);
      var passos = paginasDoGesto(g.dx, vel);
      if (passos !== 0) turnPage(passos > 0 ? 1 : -1);
      repousar(g, vel);
    }

    document.addEventListener('touchend', soltar, { capture: true, passive: true });
    document.addEventListener('touchcancel', soltar, { capture: true, passive: true });
  }

  // ── Atalho de Android Auto / CarPlay ──────────────────────────────────────────────────────
  //
  // Um toque abre o que estiver escolhido; segurar troca a escolha entre os dois. A escolha fica
  // gravada no aparelho, então a primeira vez pergunta na prática (o rótulo mostra qual é) e a
  // partir daí é só tocar.
  //
  // Os pacotes são os mesmos que o OPEN GW já usa para reconhecer a projeção na barra inferior
  // (br.com.opengw.barra: `com.ts.androidauto.app` e `com.ts.carplay.app`), e a abertura passa pela ponte
  // nativa que este launcher já expõe — a mesma que abre o app do OPEN GW.
  var PROJECTION_CHOICE_KEY = 'opengw_projection_choice';
  var PROJECTION_HOLD_MS = 600;
  var PROJECTION_APPS = {
    androidauto: { pacote: 'com.ts.androidauto.app', rotulo: 'ANDROID AUTO' },
    carplay: { pacote: 'com.ts.carplay.app', rotulo: 'CARPLAY' }
  };

  function projectionChoice() {
    try {
      var v = localStorage.getItem(PROJECTION_CHOICE_KEY);
      if (v === 'carplay' || v === 'androidauto') return v;
    } catch (e) {}
    return 'androidauto';
  }

  function setProjectionChoice(v) {
    try { localStorage.setItem(PROJECTION_CHOICE_KEY, v); } catch (e) {}
  }

  function launchPackage(pkg) {
    var B = window.AppLauncherBridge;
    if (!B) { console.warn('[OpenGW] sem ponte do launcher para abrir', pkg); return false; }
    try {
      if (typeof B.maximizeApp === 'function') { B.maximizeApp(pkg); return true; }
      if (typeof B.launchAppInPopup === 'function') { B.launchAppInPopup(pkg); return true; }
      if (typeof B.launchAppInSlot === 'function') { B.launchAppInSlot(pkg, 'left'); return true; }
    } catch (e) {
      console.warn('[OpenGW] falha ao abrir', pkg, e);
    }
    return false;
  }

  function installProjectionShortcut(host) {
    if (document.getElementById('opengw-projection')) return;

    var css = document.createElement('style');
    css.textContent =
      // Posição e empilhamento moram no trilho (ver trilhoDeAtalhos); aqui fica só o botão.
      // `position:relative` continua sendo necessário: o preenchimento do "segurar" é
      // absoluto DENTRO do botão e precisa deste bloco como referência.
      '#opengw-projection{position:relative;overflow:hidden;--seg:0;' +
      // 44px de altura mínima é o piso de alvo de toque da skill; a pílula tinha 33.8px
      // medidos na tela. `box-sizing` porque a altura precisa CONTER o padding e a borda,
      // senão o mínimo vira 44 + 18 + 2.
      'display:flex;align-items:center;justify-content:center;gap:8px;' +
      'min-height:44px;box-sizing:border-box;padding:9px 16px;border-radius:999px;' +
      'border:1px solid rgba(255,255,255,.16);background:rgba(10,10,10,.62);' +
      'color:rgba(255,255,255,.86);font:600 11px/1 "IBM Plex Sans",system-ui,sans-serif;' +
      'letter-spacing:.14em;cursor:pointer;-webkit-tap-highlight-color:transparent;' +
      'touch-action:manipulation;transition:background .18s ease;' +
      // Duas sombras, não uma: a curta assenta a borda no fundo, a longa e difusa é a luz
      // de ambiente. Uma sombra só, dura, vira contorno — que é o que a skill manda evitar.
      'box-shadow:0 1px 2px rgba(0,0,0,.30),0 10px 28px rgba(0,0,0,.36);' +
      'backdrop-filter:blur(20px) saturate(180%)}' +
      // Realce instantâneo na descida do dedo, suave só na volta.
      '#opengw-projection:active{transition:none;background:rgba(79,158,255,.26)}' +
      '#opengw-projection svg{width:16px;height:16px;flex:none;position:relative}' +
      '#opengw-projection .rotulo{position:relative}' +
      // Preenchimento do "segurar". Só `transform` — é a única propriedade, junto de `opacity`,
      // que o compositor anima sem repintar o botão a cada quadro.
      '#opengw-projection .segurar{position:absolute;left:0;top:0;bottom:0;width:100%;' +
      'background:rgba(79,158,255,.34);pointer-events:none;transform-origin:left center;' +
      'transform:scaleX(var(--seg))}' +
      // Sem transparência: o vidro vira sólido, não some. E sem `backdrop-filter`, que é o que
      // custa caro sobre uma tela 3D que repinta o tempo todo.
      '@media (prefers-reduced-transparency: reduce){#opengw-projection{' +
      'background:rgba(10,10,10,.96);backdrop-filter:none}}' +
      // Sem movimento: a sombra de ambiente é decoração, e decoração translúcida grande é
      // exatamente o que a preferência pede para sumir.
      '@media (prefers-reduced-motion: reduce){#opengw-projection{' +
      'box-shadow:0 1px 2px rgba(0,0,0,.30)}}' +
      '@media (prefers-contrast: more){#opengw-projection{background:#000;backdrop-filter:none;' +
      'border-color:rgba(255,255,255,.6);color:#fff}}';
    document.head.appendChild(css);

    var btn = document.createElement('button');
    btn.id = 'opengw-projection';
    btn.type = 'button';
    btn.innerHTML =
      '<span class="segurar" aria-hidden="true"></span>' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<rect x="2" y="4" width="20" height="13" rx="2"></rect><path d="M8 21h8"></path>' +
      '<path d="M12 17v4"></path></svg><span class="rotulo"></span>';
    trilhoDeAtalhos(host).appendChild(btn);

    var rotulo = btn.querySelector('.rotulo');
    var preenchimento = btn.querySelector('.segurar');
    function pintar() {
      var escolha = projectionChoice();
      rotulo.textContent = PROJECTION_APPS[escolha].rotulo;
      var outro = PROJECTION_APPS[escolha === 'carplay' ? 'androidauto' : 'carplay'].rotulo;
      btn.title = 'Toque para abrir ' + PROJECTION_APPS[escolha].rotulo +
        ' · segure para trocar para ' + outro;
      // Um `title` é uma dica de mouse: num painel de carro ninguém nunca a vê. O que torna o
      // "segurar" descobrível é o preenchimento crescendo sob o dedo, e o rótulo abaixo é o que
      // um leitor de tela anuncia.
      btn.setAttribute('aria-label', 'Abrir ' + PROJECTION_APPS[escolha].rotulo +
        '. Segure para trocar para ' + outro + '.');
    }
    pintar();

    // "Segurar" é medido pelo MESMO laço que desenha o preenchimento, não por um temporizador em
    // paralelo: assim a troca e a barra chegando ao fim acontecem no mesmo quadro. Com dois
    // relógios distintos um chega antes do outro e o gesto lê como duas coisas soltas em vez de
    // uma só — é o ponto "harmonia" da skill, que vale para visual+som+háptico e igualmente para
    // dois canais visuais.
    var quadroSegurar = 0;
    var inicioSegurar = 0;
    var trocou = false;

    function pintarSegurar(v) { btn.style.setProperty('--seg', String(v)); }

    function passoSegurar(agora) {
      var prog = (agora - inicioSegurar) / PROJECTION_HOLD_MS;
      if (prog >= 1) {
        quadroSegurar = 0;
        trocou = true;
        setProjectionChoice(projectionChoice() === 'carplay' ? 'androidauto' : 'carplay');
        pintar();
        pintarSegurar(0);
        return;
      }
      pintarSegurar(prog);
      quadroSegurar = requestAnimationFrame(passoSegurar);
    }

    function comecar() {
      trocou = false;
      if (quadroSegurar) cancelAnimationFrame(quadroSegurar);
      inicioSegurar = performance.now();
      quadroSegurar = requestAnimationFrame(passoSegurar);
    }
    // Soltar antes do fim desfaz: o preenchimento volta a zero e nada muda. Desistir tem de ser
    // tão fácil quanto começar.
    function terminar() {
      if (quadroSegurar) { cancelAnimationFrame(quadroSegurar); quadroSegurar = 0; }
      pintarSegurar(0);
    }

    btn.addEventListener('touchstart', comecar, { passive: true });
    btn.addEventListener('touchend', terminar);
    btn.addEventListener('touchcancel', terminar);
    btn.addEventListener('mousedown', comecar);
    btn.addEventListener('mouseup', terminar);
    btn.addEventListener('mouseleave', terminar);
    btn.addEventListener('contextmenu', function (e) { e.preventDefault(); });
    btn.addEventListener('click', function () {
      // Depois de uma troca por "segurar", o clique que vem ao soltar não abre nada.
      if (trocou) { trocou = false; return; }
      launchPackage(PROJECTION_APPS[projectionChoice()].pacote);
    });
  }


  // ── Atalho do Painel Esportivo ────────────────────────────────────────────────────────────
  //
  // Ele MORAVA na barra inferior, numa fatia fixa ligada e desligada em Configurações. Custava
  // uma fatia inteira da barra o tempo todo para uma tela que só interessa com o carro em modo
  // esportivo — e, numa central de 1080px com as fatias todas ligadas, era justamente a fatia
  // que o trio central cortava. Aqui ele não ocupa nada enquanto não serve: aparece quando o
  // SPORT+ liga e some quando ele desliga.
  //
  // COMO ELE SABE. Pela chave `haval.sportplus.active`, EMPURRADA pelo OPEN GW
  // (SportPlusController) no mesmo caminho das outras chaves derivadas. Não há laço de perguntas:
  // numa tela que já desenha um carro em 3D, perguntar de tempos em tempos custaria mais do que
  // o atalho inteiro vale. A única pergunta é UMA, na instalação, para o caso de o launcher subir
  // depois de o SPORT+ já estar ligado.
  var SPORT_PLUS_KEY = 'haval.sportplus.active';

  function sportPlusLigado(valor) {
    return valor === true || valor === 1 || valor === '1' || valor === 'true';
  }

  function installSportsDisplayShortcut(host) {
    if (document.getElementById('opengw-sportsdisplay')) return;

    var css = document.createElement('style');
    css.textContent =
      // Mesma linha dos outros dois atalhos do OPEN GW (ver trilhoDeAtalhos). É o item mais à
      // esquerda do trilho: entrando e saindo com o SPORT+, é o único que muda de tamanho, e
      // ancorado à direita ele cresce para o lado vazio sem empurrar projeção nem perfil.
      '#opengw-sportsdisplay{position:relative;' +
      'display:none;align-items:center;justify-content:center;gap:8px;' +
      'min-height:44px;box-sizing:border-box;padding:9px 16px;border-radius:999px;' +
      'border:1px solid rgba(224,57,44,.42);background:rgba(28,10,10,.66);' +
      'color:rgba(255,235,232,.92);font:600 11px/1 "IBM Plex Sans",system-ui,sans-serif;' +
      'letter-spacing:.14em;cursor:pointer;-webkit-tap-highlight-color:transparent;' +
      'touch-action:manipulation;transition:background .18s ease,opacity .2s ease,transform .2s ease;' +
      'box-shadow:0 1px 2px rgba(0,0,0,.30),0 10px 28px rgba(0,0,0,.36);' +
      'backdrop-filter:blur(20px) saturate(180%)}' +
      // `display` não anima, então a entrada é feita em duas partes: o bloco entra escondido por
      // opacidade e sobe um passo curto no quadro seguinte. Sem isso o atalho aparece estalando.
      '#opengw-sportsdisplay.is-on{display:flex;opacity:0;transform:translateY(6px)}' +
      '#opengw-sportsdisplay.is-on.is-in{opacity:1;transform:none}' +
      '#opengw-sportsdisplay:active{transition:none;background:rgba(224,57,44,.34)}' +
      '#opengw-sportsdisplay svg{width:16px;height:16px;flex:none}' +
      '@media (prefers-reduced-transparency: reduce){#opengw-sportsdisplay{' +
      'background:rgba(24,8,8,.97);backdrop-filter:none}}' +
      // Sem movimento: entra e sai sem deslizar, e sem a sombra grande e difusa.
      '@media (prefers-reduced-motion: reduce){#opengw-sportsdisplay{' +
      'transition:none;box-shadow:0 1px 2px rgba(0,0,0,.30)}' +
      '#opengw-sportsdisplay.is-on{opacity:1;transform:none}}' +
      '@media (prefers-contrast: more){#opengw-sportsdisplay{background:#000;backdrop-filter:none;' +
      'border-color:rgba(255,255,255,.6);color:#fff}}';
    document.head.appendChild(css);

    var btn = document.createElement('button');
    btn.id = 'opengw-sportsdisplay';
    btn.type = 'button';
    btn.setAttribute('aria-label', 'Abrir o Painel Esportivo');
    btn.innerHTML =
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M12 20a8 8 0 1 0-8-8"></path><path d="M12 12l4.5-4"></path>' +
      '<path d="M4 20h4"></path></svg><span>PAINEL ESPORTIVO</span>';
    trilhoDeAtalhos(host).appendChild(btn);

    var visivel = false;
    function mostrar(novo) {
      if (novo === visivel) return;
      visivel = novo;
      if (novo) {
        btn.classList.add('is-on');
        // Um quadro entre entrar no fluxo e começar a transição: no mesmo quadro o navegador
        // junta as duas mudanças e não há o que interpolar.
        requestAnimationFrame(function () { btn.classList.add('is-in'); });
      } else {
        btn.classList.remove('is-in');
        btn.classList.remove('is-on');
      }
    }

    btn.addEventListener('click', function () {
      OpenGW.post('/quick/sportsdisplay', {})
        .catch(function (e) { console.warn('[OpenGW] Painel Esportivo não abriu', e); });
    });

    if (window.carTelemetry && typeof window.carTelemetry.onUpdate === 'function') {
      window.carTelemetry.onUpdate(function (chave, valor) {
        if (chave === SPORT_PLUS_KEY) mostrar(sportPlusLigado(valor));
      });
    }
    // A única pergunta: o estado de agora, para o caso de o SPORT+ já estar ligado quando esta
    // página abriu. Falhar aqui é aceitável — o atalho só fica escondido até o SPORT+ mudar de
    // estado, que é quando a chave empurrada chega.
    OpenGW.get('/quick/state')
      .then(function (d) { mostrar(!!(d && d.sportPlusActive)); })
      .catch(function () {});
  }

  function boot() {
    refreshIdentity();
    if (window.OpenGWPanel && typeof window.OpenGWPanel.install === 'function') {
      window.OpenGWPanel.install();
    }
    whenMounted(function (host) {
      installPageNavigation(host);
      installProjectionShortcut(host);
      installSportsDisplayShortcut(host);
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
