/**
 * O conteúdo da área "CONFIGURAÇÕES OPEN GW".
 *
 * Essa área é uma das páginas do launcher: o app tem uma lista de "desktops" e um deles se chama
 * CONFIGURAÇÕES OPEN GW. Ao entrar nele, uma regra de CSS tira os widgets e o chrome e deixa só o
 * carro. É esse espaço que este arquivo preenche, com um véu escuro sobre o 3D e um cartão em
 * cima dele.
 *
 * A FAIXA DE ÍCONES NÃO APARECE AQUI
 * Ela é uma view NATIVA, desenhada por cima do WebView: nenhum z-index daqui alcança ela. Enquanto
 * o launcher era um APK separado isso era um teto — a página já chamava
 * `AppLauncherBridge.setLauncherHidden`, que não existia no dex, e a única alternativa era
 * `setShellMode("appsOnly")`, que junto some com as bordas de arrasto, dispensa o popup de mídia
 * aberto e grava o modo. Caro demais por uma fileira de ícones, então os ícones ficavam.
 *
 * Com os dois apps fundidos num só o dex é o nosso, `setLauncherHidden` existe, e esta página pede
 * a faixa escondida ao entrar e devolve ao sair.
 *
 * E O CARTÃO NÃO CRESCEU PARA OCUPAR O LUGAR DELA
 * Foi a primeira coisa que se tentou, e foi desfeita depois de medir. Esta página é coberta quase
 * inteira pelo cartão, que PEGA o toque (ele rola e tem botões) — quem troca de página é o arrasto
 * no canvas em volta dele. Com o limite antigo sobram ~180px de canvas embaixo do cartão, além da
 * faixa acima; descendo o cartão até a barra do OPEN GW sobram ~30px, e as bordas de arrasto
 * laterais não completam a troca sozinhas (conferido no emulador). Este arquivo já registra uma
 * vez em que alguém ficou preso nesta tela; 144px de conteúdo a mais não pagam repetir isso.
 *
 * POR QUE O PAINEL FICA DENTRO DE #hv-root
 * A primeira versão pendurava o painel no <body> para escapar da regra
 * `#hv-root.hv-blank-page > *:not(...)`, que esconde todo filho do root fora de uma lista curta
 * de exceções. Escapava da regra e caía num problema pior: #hv-root é `position: fixed` e cria
 * um contexto de empilhamento, então tudo que está no body fica ATRÁS dos filhos dele por mais
 * z-index que se peça. O resultado medido foi o pior dos dois mundos — a pílula de páginas e as
 * bordas de arrasto desenhadas por cima do painel, e mesmo assim o painel recebendo o toque
 * delas, o que deixava a pessoa sem como sair desta página.
 *
 * Agora o painel é filho do root e o nome dele entrou na lista de exceções daquela regra (uma
 * palavra a mais no seletor, em index.html). Dentro do root os z-index valem entre si: o painel
 * fica em 18, abaixo das bordas de arrasto (19) e da pílula (74), que continuam alcançáveis.
 *
 * Tudo que mexe no carro passa pelo OPEN GW (openGwClient.js). Este arquivo não fala com o
 * veículo diretamente: ele desenha, pergunta e mostra a resposta.
 */
(function () {
  'use strict';

  var CSS = [
    // Cores. O launcher inteiro já tem um interruptor de claro/escuro (widgetThemeMode), e o que
    // ele faz é pôr a classe `hv-widgets-light` no #hv-root. Tudo aqui sai dessa mesma classe,
    // então este painel não tem como discordar do resto do app.
    '#hv-root{--gw-bg:rgba(16,18,22,.93);--gw-fg:#eef1f4;--gw-line:rgba(255,255,255,.15);',
    '  --gw-card:rgba(255,255,255,.06);--gw-sel:#4fd6e8;--gw-sel-bg:rgba(79,214,232,.14);',
    '  --gw-danger:#ff8f8f;--gw-danger-line:rgba(255,120,120,.4);--gw-danger-bg:rgba(150,40,40,.18);',
    '  --gw-warn-line:rgba(240,190,90,.38);--gw-warn-bg:rgba(120,90,20,.18);--gw-shadow:0 20px 60px rgba(0,0,0,.55);}',
    '#hv-root.hv-widgets-light{--gw-bg:rgba(255,255,255,.96);--gw-fg:#101114;--gw-line:rgba(0,0,0,.12);',
    '  --gw-card:#f7f8fa;--gw-sel:#1f6feb;--gw-sel-bg:#eef4ff;',
    '  --gw-danger:#a01b1b;--gw-danger-line:rgba(190,30,30,.35);--gw-danger-bg:#fff6f6;',
    '  --gw-warn-line:rgba(190,120,20,.4);--gw-warn-bg:#fffaf0;--gw-shadow:0 18px 44px rgba(20,25,35,.28);}',

    // O painel é um cartão dentro da área segura, e não uma folha que cobre a tela: a caixa
    // termina onde a faixa de ícones começa e a rolagem acaba junto com ela. Isso continua valendo
    // mesmo agora que a faixa é escondida nesta página (ver o cabeçalho): o espaço que sobra
    // embaixo é por onde se arrasta para sair daqui, não sobra de layout.
    //
    // max() e não soma nas duas variáveis de baixo: elas medem a MESMA borda por caminhos
    // diferentes (a faixa do launcher e o recorte do sistema). Somá-las descontava a faixa duas
    // vezes e sobrava um terço da tela numa central de 600px.
    // pointer-events:none de propósito. O véu é enfeite, não camada de interação: se ele pegar o
    // toque, tira do canvas o arrasto que troca de página e a pessoa fica presa nesta tela com as
    // setinhas da pílula como única saída — que é exatamente o problema que já apareceu aqui uma
    // vez, quando o painel morava no <body>.
    '.opengw-scrim{position:absolute;inset:0;z-index:17;display:none;pointer-events:none;',
    '  background:rgba(6,8,12,.62);}',
    '.opengw-scrim.is-on{display:block;}',
    // Nas laterais o painel NÃO usa --hv-safe-left/right: esses recuos existem para os widgets
    // desviarem das janelas de app que podem abrir dos dois lados, e nesta página não há widget
    // nem janela nenhuma. Numa central de 1011px eles custavam 256px de largura — um quarto da
    // tela — que aqui vira conteúdo.
    '.opengw-panel{position:absolute;z-index:18;display:none;overflow-y:auto;',
    '  top:var(--hv-safe-top, 0px);left:16px;right:16px;',
    '  bottom:max(var(--hv-launcher-bottom, 0px), var(--hv-safe-bottom, 0px));',
    // Duas camadas de sombra por cima da cor de fundo. As com `local` rolam junto com o
    // conteúdo e as com `scroll` ficam presas na moldura, então a sombra só aparece do lado em
    // que ainda há coisa fora da vista — numa central baixa, é o único aviso de que o cartão
    // rola. Sem ela o texto simplesmente termina na borda e parece cortado.
    '  color:var(--gw-fg);padding:18px 22px 22px;',
    '  background:linear-gradient(var(--gw-bg) 40%, rgba(0,0,0,0)) top/100% 22px local no-repeat,',
    '    linear-gradient(rgba(0,0,0,0), var(--gw-bg) 70%) bottom/100% 22px local no-repeat,',
    '    radial-gradient(farthest-side at 50% 0, rgba(0,0,0,.28), rgba(0,0,0,0)) top/100% 12px scroll no-repeat,',
    '    radial-gradient(farthest-side at 50% 100%, rgba(0,0,0,.28), rgba(0,0,0,0)) bottom/100% 12px scroll no-repeat,',
    '    var(--gw-bg);',
    '  border:1px solid var(--gw-line);border-radius:18px;box-shadow:var(--gw-shadow);',
    "  font-family:'IBM Plex Sans',system-ui,sans-serif;}",
    '.opengw-panel.is-on{display:block;}',
    '.opengw-panel-head{display:flex;align-items:baseline;gap:12px;margin-bottom:4px;}',
    '.opengw-panel-title{font-size:19px;font-weight:700;letter-spacing:.02em;}',
    '.opengw-panel-state{font-size:11px;letter-spacing:.08em;text-transform:uppercase;opacity:.55;}',
    '.opengw-panel-sub{font-size:12px;opacity:.6;line-height:1.5;margin-bottom:22px;max-width:70ch;}',
    '.opengw-section{font-size:9px;letter-spacing:.18em;opacity:.45;margin:22px 0 10px;}',
    '.opengw-cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px;}',
    '.opengw-card{border:1px solid var(--gw-line);border-radius:12px;background:var(--gw-card);',
    '  padding:14px 15px;text-align:left;cursor:pointer;transition:transform .12s ease,border-color .12s ease;',
    '  font-family:inherit;color:inherit;display:block;width:100%;}',
    '.opengw-card:active{transform:scale(.985);}',
    '.opengw-card.is-on{border-color:var(--gw-sel);background:var(--gw-sel-bg);}',
    '.opengw-card[disabled]{opacity:.45;cursor:default;}',
    // display:block é obrigatório: estes dois são <span>, e inline faz o título e a explicação
    // saírem colados na mesma linha.
    '.opengw-card-name{display:block;font-size:13px;font-weight:700;margin-bottom:5px;}',
    '.opengw-card-hint{display:block;font-size:11px;opacity:.6;line-height:1.45;}',
    '.opengw-card.is-danger{border-color:var(--gw-danger-line);background:var(--gw-danger-bg);}',
    '.opengw-card.is-danger .opengw-card-name{color:var(--gw-danger);}',
    '.opengw-row{display:flex;align-items:center;gap:14px;border:1px solid var(--gw-line);',
    '  border-radius:12px;background:var(--gw-card);padding:14px 15px;margin-bottom:10px;}',
    '.opengw-row-main{flex:1;min-width:0;}',
    '.opengw-slider{width:180px;accent-color:var(--gw-sel);}',
    '.opengw-toggle{width:52px;height:30px;border-radius:15px;border:none;background:#888e99;',
    '  position:relative;cursor:pointer;flex:none;transition:background .16s ease;}',
    '.opengw-toggle.is-on{background:var(--gw-sel);}',
    '.opengw-toggle::after{content:"";position:absolute;top:3px;left:3px;width:24px;height:24px;',
    '  border-radius:50%;background:#fff;transition:transform .16s ease;}',
    '.opengw-toggle.is-on::after{transform:translateX(22px);}',
    '.opengw-note{font-size:11px;opacity:.55;line-height:1.5;margin-top:10px;max-width:70ch;}',
    '.opengw-warn{border:1px solid var(--gw-warn-line);background:var(--gw-warn-bg);border-radius:12px;',
    '  padding:12px 14px;font-size:11px;line-height:1.55;margin-top:10px;max-width:70ch;}',
    // Modal compartilhado com o seletor de modelo do openGwClient.js. Fica acima da pílula de
    // páginas (74) de propósito: enquanto há uma pergunta na tela, trocar de página por baixo
    // dela deixaria a pergunta órfã.
    '.opengw-modal{position:absolute;inset:0;z-index:90;background:rgba(8,8,10,.72);',
    // flex-start, não center: centralizar num espaço menor que o cartão corta o conteúdo em
    // cima E embaixo. Ancorado no topo, o cartão cresce até o limite e rola por dentro.
    '  display:flex;align-items:flex-start;justify-content:center;',
    '  padding:calc(var(--hv-safe-top, 0px) + 14px) calc(var(--hv-safe-right, 0px) + 16px)',
    '    calc(max(var(--hv-launcher-bottom, 0px), var(--hv-safe-bottom, 0px)) + 14px)',
    '    calc(var(--hv-safe-left, 0px) + 16px);',
    "  font-family:'IBM Plex Sans',system-ui,sans-serif;}",
    '.opengw-modal-card{background:var(--gw-bg);color:var(--gw-fg);border-radius:16px;padding:26px 28px;',
    '  max-width:560px;width:100%;max-height:100%;',
    '  display:flex;flex-direction:column;min-height:0;}',
    // Só o miolo rola; o botão de confirmar fica ancorado e sempre alcançável.
    '.opengw-modal-body{flex:1 1 auto;overflow-y:auto;min-height:0;}',
    '.opengw-modal-title{font-size:18px;font-weight:700;margin-bottom:8px;}',
    '.opengw-modal-sub{font-size:12px;opacity:.65;line-height:1.55;margin-bottom:10px;}',
    '.opengw-modal-reason{font-size:11px;opacity:.5;line-height:1.5;border-left:2px solid rgba(0,0,0,.15);',
    '  padding-left:10px;margin-bottom:18px;}',
    '.opengw-modal-note{font-size:11px;opacity:.55;line-height:1.5;margin-bottom:8px;}',
    '.opengw-label{font-size:9px;letter-spacing:.18em;opacity:.45;margin:14px 0 8px;}',
    '.opengw-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:9px;}',
    '.opengw-choice{border:1px solid var(--gw-line);border-radius:11px;background:var(--gw-card);',
    '  padding:12px;cursor:pointer;text-align:left;font-family:inherit;color:inherit;}',
    '.opengw-choice.is-on{border-color:var(--gw-sel);background:var(--gw-sel-bg);}',
    '.opengw-choice-name{display:block;font-size:13px;font-weight:700;}',
    '.opengw-choice-hint{display:block;font-size:10px;opacity:.6;margin-top:3px;}',
    '.opengw-colors{display:flex;gap:10px;flex-wrap:wrap;}',
    '.opengw-swatch{width:42px;height:42px;border-radius:50%;border:2px solid var(--gw-line);',
    '  cursor:pointer;padding:0;}',
    '.opengw-swatch.is-on{border-color:var(--gw-sel);box-shadow:0 0 0 3px var(--gw-sel-bg);}',
    '.opengw-primary{flex:none;margin-top:22px;width:100%;padding:13px;border-radius:11px;border:none;',
    '  background:var(--gw-sel);color:#08121a;font-family:inherit;font-size:13px;font-weight:700;cursor:pointer;}',
    '#hv-root.hv-widgets-light .opengw-primary{color:#fff;}',
    '.opengw-primary[disabled]{background:#8a9099;cursor:default;}',
    '.opengw-secondary{margin-top:10px;width:100%;padding:12px;border-radius:11px;color:inherit;',
    '  border:1px solid var(--gw-line);background:var(--gw-card);font-family:inherit;font-size:12px;cursor:pointer;}',
    // Telas baixas: a central de teste tem 600px de altura e a faixa de ícones come 190 deles.
    // Sobra pouco, então o cartão encolhe o respiro em vez de empurrar o botão para fora.
    '@media (max-height:760px){',
    '  .opengw-modal-card{padding:18px 20px;}',
    '  .opengw-modal-title{font-size:16px;margin-bottom:5px;}',
    '  .opengw-modal-sub{margin-bottom:6px;}',
    '  .opengw-modal-reason{margin-bottom:10px;}',
    '  .opengw-label{margin:10px 0 6px;}',
    // Quatro carrocerias lado a lado em vez de empilhadas: é a diferença entre caber e não caber.
    '  .opengw-grid{grid-template-columns:repeat(4,1fr);gap:7px;}',
    '  .opengw-choice{padding:9px 10px;}',
    '  .opengw-choice-name{font-size:12px;}',
    '  .opengw-choice-hint{font-size:9px;}',
    // Numa central baixa sobram ~225px entre o cabeçalho da MMI e a faixa de ícones. O texto
    // longo de explicação é o que se corta primeiro: o título e o motivo já dizem o essencial,
    // e as opções e o botão são o que a pessoa precisa alcançar. Nas telas dos carros de verdade,
    // bem mais altas, o texto completo continua aparecendo.
    '  .opengw-modal-sub{display:none;}',
    '  .opengw-modal-note{display:none;}',
    '  .opengw-modal-reason{margin-bottom:8px;}',
    '  .opengw-swatch{width:32px;height:32px;}',
    '  .opengw-primary{margin-top:14px;padding:11px;}',
    '  .opengw-panel{padding:14px 18px 18px;}',
    '  .opengw-section{margin:12px 0 7px;}',
    // O parágrafo de abertura custa três linhas e não diz nada que os cartões não digam. Numa
    // central de 600px isso é a diferença entre ver a primeira seção inteira ou metade dela.
    '  .opengw-panel-sub{display:none;}',
    '  .opengw-panel-head{margin-bottom:2px;}',
    '  .opengw-cards{grid-template-columns:repeat(auto-fill,minmax(196px,1fr));gap:9px;}',
    '  .opengw-card{padding:11px 12px;}',
    '  .opengw-row{padding:11px 12px;margin-bottom:8px;}',
    '}',
    '@media (prefers-reduced-motion: reduce){.opengw-card,.opengw-toggle,.opengw-toggle::after{transition:none;}}'
  ].join('');

  var panel = null;
  var scrim = null;
  var quick = null;

  function injectCss() {
    if (document.getElementById('opengw-panel-css')) return;
    var style = document.createElement('style');
    style.id = 'opengw-panel-css';
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  function el(tag, className, html) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (html != null) node.innerHTML = html;
    return node;
  }

  function card(name, hint, onClick, opts) {
    opts = opts || {};
    var b = el('button', 'opengw-card' + (opts.danger ? ' is-danger' : ''));
    b.type = 'button';
    b.appendChild(el('span', 'opengw-card-name', window.OpenGW.escapeHtml(name)));
    b.appendChild(el('span', 'opengw-card-hint', window.OpenGW.escapeHtml(hint)));
    if (opts.disabled) b.disabled = true;
    else b.onclick = onClick;
    return b;
  }

  function build(host) {
    // O véu vem antes do painel na árvore, e abaixo dele no z-index: escurece o carro sem
    // apagá-lo e segura o toque que iria parar na órbita da câmera.
    scrim = el('div', 'opengw-scrim');
    scrim.id = 'opengw-scrim';
    host.appendChild(scrim);
    panel = el('div', 'opengw-panel');
    panel.id = 'opengw-panel';
    host.appendChild(panel);
    render();
  }

  function setState(text) {
    var node = panel && panel.querySelector('.opengw-panel-state');
    if (node) node.textContent = text;
  }

  function render() {
    if (!panel) return;
    panel.innerHTML = '';

    var head = el('div', 'opengw-panel-head');
    head.appendChild(el('div', 'opengw-panel-title', 'Configurações OPEN GW'));
    head.appendChild(el('div', 'opengw-panel-state', 'lendo…'));
    panel.appendChild(head);
    panel.appendChild(el('div', 'opengw-panel-sub',
      'Atalhos para o que costuma ser preciso com o carro ligado. Tudo aqui é executado pelo ' +
      'OPEN GW — se ele não estiver aberto no carro, os botões ficam desligados.'));

    // ── Tela inicial ────────────────────────────────────────────────────────────────────
    panel.appendChild(el('div', 'opengw-section', 'TELA INICIAL'));
    var telaCards = el('div', 'opengw-cards');
    telaCards.appendChild(card(
      'Voltar ao launcher de fábrica',
      'O carro volta a abrir na tela original da Haval. Nada é desinstalado: este launcher ' +
      'continua no aparelho e volta pelo OPEN GW, em Tela Inicial.',
      voltarParaFabrica
    ));
    telaCards.appendChild(card(
      'Abrir OPEN GW',
      'Traz o app para a frente, por cima do launcher.',
      function () {
        if (!window.OpenGW.openApp()) toast('Não foi possível abrir o OPEN GW por aqui.');
      }
    ));
    telaCards.appendChild(card(
      'Modo Descanso',
      'Apaga a central: fundo preto e só um relógio analógico discreto no meio. Um toque em ' +
      'qualquer lugar da tela traz o launcher de volta. Atalho: segure o relógio em qualquer página.',
      function () {
        if (window.OpenGwRest) window.OpenGwRest.enter();
        else toast('Modo Descanso indisponível neste launcher.');
      }
    ));
    panel.appendChild(telaCards);

    // ── Perfil de motorista ─────────────────────────────────────────────────────────────
    panel.appendChild(el('div', 'opengw-section', 'PERFIL DE MOTORISTA'));
    var perfilAtivo = window.OpenGwProfile && window.OpenGwProfile.ativo();
    var perfilCards = el('div', 'opengw-cards');
    perfilCards.appendChild(card(
      perfilAtivo ? 'Dirigindo: ' + perfilAtivo : 'Nenhum perfil ativo',
      'Troca o carro inteiro de uma vez: tema do painel, HUD, som do motor, barra inferior, ' +
      'atalhos, configurações do veículo e a memória do banco. Atalho: a pilha no canto ' +
      'superior direito da tela inicial.',
      function () {
        if (window.OpenGwProfile) window.OpenGwProfile.abrir();
        else toast('Perfis de motorista indisponíveis neste launcher.');
      }
    ));
    panel.appendChild(perfilCards);

    // ── Aparência ───────────────────────────────────────────────────────────────────────
    panel.appendChild(el('div', 'opengw-section', 'TEMA DO APP'));
    var themeHost = el('div');
    themeHost.id = 'opengw-theme';
    panel.appendChild(themeHost);

    // ── Modelo ──────────────────────────────────────────────────────────────────────────
    panel.appendChild(el('div', 'opengw-section', 'MODELO E COR'));
    var cache = window.OpenGW.readCache() || {};
    var identity = window.OpenGW.identity;
    var trimLabel = cache.trim ? cache.trim.toUpperCase() : 'não definido';
    var origem = identity && identity.source === 'reported' ? 'lido do carro'
      : identity && identity.source === 'user' ? 'escolhido por você'
      : 'salvo neste launcher';

    var modeloCards = el('div', 'opengw-cards');
    modeloCards.appendChild(card(
      'Modelo: ' + trimLabel,
      origem + '. Tocar para escolher outro — o carro recarrega a carroceria certa.',
      function () { window.OpenGW.showModelPicker(window.OpenGW.identity); }
    ));
    panel.appendChild(modeloCards);
    if (identity && identity.reason) {
      panel.appendChild(el('div', 'opengw-note', window.OpenGW.escapeHtml(identity.reason)));
    }

    // ── ASD ─────────────────────────────────────────────────────────────────────────────
    panel.appendChild(el('div', 'opengw-section', 'SOM DO MOTOR (ASD)'));
    var asdHost = el('div');
    asdHost.id = 'opengw-asd';
    panel.appendChild(asdHost);

    // ── Modo sorrateiro ─────────────────────────────────────────────────────────────────
    panel.appendChild(el('div', 'opengw-section', 'MODO SORRATEIRO'));
    var stealthCards = el('div', 'opengw-cards');
    stealthCards.appendChild(card(
      'Ativar modo sorrateiro',
      'Esconde o ícone do OPEN GW e desliga barra, efeitos e painel virtual.',
      confirmarSorrateiro,
      { danger: true }
    ));
    panel.appendChild(stealthCards);
    panel.appendChild(el('div', 'opengw-warn',
      '<strong>Só tem volta pelo computador.</strong> Depois de ativado, o ícone do OPEN GW some ' +
      'do carro e a única saída é o botão “Sair do Modo Sorrateiro” no instalador OPEN GW, ' +
      'no seu computador. Não ative sem ter o instalador à mão.'));

    renderTheme();
    refreshQuick();
  }

  /**
   * Claro, escuro ou automático — para o launcher inteiro.
   *
   * "Automático" é o que o app já fazia sozinho: ele mede o brilho do fundo da página e escolhe o
   * contrário, para os cartões lerem sobre ele. Claro e escuro são escolhas fixas e não têm nada
   * a ver com o dia ou a noite do cenário 3D — o céu pode estar de noite com o app claro.
   */
  var THEME_MODES = [
    { key: 'dark', name: 'Escuro', hint: 'Cartões escuros a qualquer hora, com o cenário de dia ou de noite.' },
    { key: 'light', name: 'Claro', hint: 'Cartões claros a qualquer hora, com o cenário de dia ou de noite.' },
    { key: 'auto', name: 'Automático', hint: 'Segue o fundo de cada página: claro sobre fundo escuro e vice-versa.' }
  ];

  function currentThemeMode() {
    try {
      var app = window.__app;
      var mode = app && app.state && app.state.widgetThemeMode;
      return mode === 'light' || mode === 'auto' ? mode : 'dark';
    } catch (e) {
      return 'dark';
    }
  }

  var themeRendered = null;
  function renderTheme() {
    var host = panel && panel.querySelector('#opengw-theme');
    if (!host) return;
    themeRendered = currentThemeMode();
    host.innerHTML = '';
    var app = window.__app;
    if (!app || typeof app.setAppThemeMode !== 'function') {
      host.appendChild(el('div', 'opengw-note', 'Tema indisponível nesta versão do launcher.'));
      return;
    }
    var cur = currentThemeMode();
    var wrap = el('div', 'opengw-cards');
    THEME_MODES.forEach(function (mode) {
      var button = card(mode.name, mode.hint, function () {
        app.setAppThemeMode(mode.key);
        renderTheme();
      });
      if (mode.key === cur) button.classList.add('is-on');
      wrap.appendChild(button);
    });
    host.appendChild(wrap);
    host.appendChild(el('div', 'opengw-note', 'Vale para todas as páginas do launcher.'));
  }

  function renderAsd() {
    var host = panel && panel.querySelector('#opengw-asd');
    if (!host) return;
    host.innerHTML = '';

    var asd = quick && quick.asd;
    if (!asd || asd.error) {
      host.appendChild(el('div', 'opengw-note',
        asd && asd.error
          ? 'ASD indisponível: ' + window.OpenGW.escapeHtml(asd.error)
          : 'Sem resposta do OPEN GW — ASD não pode ser ajustado agora.'));
      return;
    }

    host.appendChild(toggleRow(
      'Som do motor',
      'Liga a simulação de motor a combustão pelos alto-falantes.',
      !!asd.enabled,
      function (novo) { return window.OpenGW.post('/quick/asd', { enabled: novo }); }
    ));

    var volRow = el('div', 'opengw-row');
    var volMain = el('div', 'opengw-row-main');
    volMain.appendChild(el('div', 'opengw-card-name', 'Volume do ASD'));
    volMain.appendChild(el('div', 'opengw-card-hint',
      'Independente do volume de mídia. Atual: <span id="opengw-vol-val">' + (asd.volume | 0) + '%</span>'));
    volRow.appendChild(volMain);
    var slider = document.createElement('input');
    slider.type = 'range';
    slider.min = '0';
    slider.max = '100';
    slider.value = String(asd.volume | 0);
    slider.className = 'opengw-slider';
    // `input` acompanha o dedo; a gravação só acontece ao soltar, para não mandar cem
    // requisições ao carro no meio de um arrasto.
    slider.oninput = function () {
      var out = panel.querySelector('#opengw-vol-val');
      if (out) out.textContent = slider.value + '%';
    };
    slider.onchange = function () {
      window.OpenGW.post('/quick/asd', { volume: parseInt(slider.value, 10) })
        .then(applyQuick)
        .catch(function (e) { toast('ASD: ' + e.message); });
    };
    volRow.appendChild(slider);
    host.appendChild(volRow);

    host.appendChild(toggleRow(
      'Abafador',
      'Deixa o som mais surdo, como escapamento fechado.',
      !!asd.muffle,
      function (novo) { return window.OpenGW.post('/quick/asd', { muffle: novo }); }
    ));
  }

  function toggleRow(name, hint, on, action) {
    var row = el('div', 'opengw-row');
    var main = el('div', 'opengw-row-main');
    main.appendChild(el('div', 'opengw-card-name', window.OpenGW.escapeHtml(name)));
    main.appendChild(el('div', 'opengw-card-hint', window.OpenGW.escapeHtml(hint)));
    row.appendChild(main);

    var btn = el('button', 'opengw-toggle' + (on ? ' is-on' : ''));
    btn.type = 'button';
    btn.onclick = function () {
      var novo = !btn.classList.contains('is-on');
      // Vira na hora e desfaz se o carro recusar: esperar a ida e volta faz o botão parecer
      // quebrado numa central lenta.
      btn.classList.toggle('is-on', novo);
      action(novo).then(applyQuick).catch(function (e) {
        btn.classList.toggle('is-on', !novo);
        toast(e.message);
      });
    };
    row.appendChild(btn);
    return row;
  }

  function voltarParaFabrica() {
    var pacote = quick && quick.launcher && quick.launcher.factoryPackage;
    if (!pacote) { toast('OPEN GW não respondeu — não dá para trocar agora.'); return; }
    if (quick.launcher.factoryInstalled === false) {
      toast('O launcher de fábrica não está instalado neste carro.');
      return;
    }
    window.OpenGW.post('/launcher/set', { package: pacote })
      .then(function () {
        toast('Pronto: a tela de fábrica voltou. O launcher 3D continua instalado.');
        refreshQuick();
      })
      .catch(function (e) { toast('Não deu para trocar: ' + e.message); });
  }

  function confirmarSorrateiro() {
    if (document.getElementById('opengw-stealth-confirm')) return;
    var wrap = el('div', 'opengw-modal');
    wrap.id = 'opengw-stealth-confirm';
    wrap.innerHTML =
      '<div class="opengw-modal-card">' +
        '<div class="opengw-modal-title">Ativar o modo sorrateiro?</div>' +
        '<div class="opengw-modal-sub">O ícone do OPEN GW sai do carro, a barra inferior e os ' +
          'efeitos param, e o painel virtual volta ao tema de fábrica.</div>' +
        '<div class="opengw-warn">A volta <strong>não existe dentro do carro</strong>. Só o ' +
          'instalador OPEN GW, no computador, consegue desativar o modo sorrateiro.</div>' +
        '<button type="button" class="opengw-primary" id="opengw-stealth-go">Ativar mesmo assim</button>' +
        '<button type="button" class="opengw-secondary" id="opengw-stealth-no">Cancelar</button>' +
      '</div>';
    (window.OpenGW.mountRoot() || document.body).appendChild(wrap);
    wrap.querySelector('#opengw-stealth-no').onclick = function () { wrap.remove(); };
    wrap.querySelector('#opengw-stealth-go').onclick = function () {
      this.disabled = true;
      window.OpenGW.post('/quick/stealth', {})
        .then(function () { wrap.remove(); toast('Modo sorrateiro ativado.'); refreshQuick(); })
        .catch(function (e) { wrap.remove(); toast('Falhou: ' + e.message); });
    };
  }

  function applyQuick(data) {
    quick = data;
    setState(data && data.ok ? 'conectado' : 'sem OPEN GW');
    renderAsd();
  }

  function refreshQuick() {
    return window.OpenGW.get('/quick/state')
      .then(applyQuick)
      .catch(function () {
        quick = null;
        setState('sem OPEN GW');
        renderAsd();
      });
  }

  function toast(msg) {
    var t = el('div', 'opengw-modal');
    t.innerHTML = '<div class="opengw-modal-card"><div class="opengw-modal-sub" style="margin:0">' +
      window.OpenGW.escapeHtml(msg) + '</div>' +
      '<button type="button" class="opengw-secondary">Fechar</button></div>';
    t.querySelector('button').onclick = function () { t.remove(); };
    (window.OpenGW.mountRoot() || document.body).appendChild(t);
  }

  var GW_DESKTOP_NAME = 'CONFIGURAÇÕES OPEN GW';

  /**
   * Estamos na área do OPEN GW?
   *
   * Uma leitura só, e é o nome da página atual. A versão anterior aceitava também a classe
   * `hv-blank-page` no #hv-root como atalho, e era isso que produzia o bug da "print": o app
   * redesenha dentro do laço de animação, e nessa página o laço está PARADO (_render3dPaused),
   * então a classe não era retirada do root ao sair. O painel lia a classe velha, continuava
   * ligado, e a tela inteira ficava congelada na página de configurações enquanto o contador de
   * páginas já mostrava a próxima.
   *
   * O índice do desktop, não. Esse muda na hora, em `_switchDesktop`, antes de qualquer
   * redesenho — é o único sinal que não depende do laço estar rodando.
   */
  function isOpenGwDesktop() {
    try {
      var app = window.__app;
      var list = app && app._desktops;
      var current = list && list[app._desktopIndex || 0];
      return !!(current && current.name === GW_DESKTOP_NAME);
    } catch (e) {
      return false;
    }
  }

  var lastOn = null;
  function sync() {
    if (!panel) return;
    // O React é dono dos filhos do #hv-root e pode trocar a árvore inteira numa
    // remontagem, levando junto um nó que não é dele. Barato de conferir, e evita
    // que a página volte a ficar branca sem nada explicar por quê.
    if (!panel.isConnected || (scrim && !scrim.isConnected)) {
      var host = window.OpenGW.mountRoot();
      if (!host) return;
      if (scrim && !scrim.isConnected) host.appendChild(scrim);
      if (!panel.isConnected) host.appendChild(panel);
      lastOn = null;
    }
    var on = isOpenGwDesktop();
    if (on === lastOn) return;
    lastOn = on;
    panel.classList.toggle('is-on', on);
    if (scrim) scrim.classList.toggle('is-on', on);
    // A faixa de ícones é nativa e não obedece a z-index daqui (ver o cabeçalho). O pedido vai
    // pelo árbitro em appLauncherClient.js porque o Modo Descanso também pede: sair do descanso
    // não pode acender os ícones de volta enquanto esta página ainda estiver aberta.
    if (typeof window.requestLauncherHidden === 'function') window.requestLauncherHidden('painel', on);
    // Só consulta o carro ao ENTRAR na área; sair não precisa de rede.
    if (on) refreshQuick();
  }

  /**
   * Fica sabendo da troca de página no mesmo instante em que ela acontece.
   *
   * `_switchDesktop` é o único caminho de troca (setas, pontinhos, arrasto e teclado terminam
   * todos nele), então embrulhá-lo dá um aviso imediato e sem laço. A verificação periódica fica
   * como rede de segurança para o resto: primeira montagem, recarga e qualquer caminho futuro
   * que mude o índice por fora.
   */
  function tryWrapSwitchDesktop() {
    try {
      var app = window.__app;
      if (!app || typeof app._switchDesktop !== 'function') return false;
      if (app._switchDesktop.__openGwWrapped) return true;
      var original = app._switchDesktop;
      var wrapped = function () {
        var out = original.apply(this, arguments);
        sync();
        return out;
      };
      wrapped.__openGwWrapped = true;
      app._switchDesktop = wrapped;
      return true;
    } catch (e) {
      console.warn('[OpenGW] sem aviso direto de troca de página:', e);
      return false;
    }
  }

  function watchDesktop() {
    var wrapped = tryWrapSwitchDesktop();
    setInterval(function () {
      if (!wrapped) wrapped = tryWrapSwitchDesktop();
      sync();
      // O tema também muda pelo botão de aparência do estúdio, que não passa por aqui. Comparar
      // duas strings a cada 400 ms é barato; redesenhar sem comparar não é.
      if (lastOn && themeRendered !== currentThemeMode()) renderTheme();
    }, 400);
    sync();
  }

  window.OpenGWPanel = {
    install: function () {
      if (panel) return;
      injectCss();
      // O painel precisa estar DENTRO de #hv-root (ver o cabeçalho deste arquivo), e o root só
      // nasce quando o React desenha — depois deste arquivo carregar.
      window.OpenGW.whenMounted(function (host) {
        if (panel) return;
        build(host);
        watchDesktop();
      });
    },
    refresh: refreshQuick
  };
})();
