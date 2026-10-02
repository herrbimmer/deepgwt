/**
 * Perfil de motorista no launcher 3D.
 *
 * O QUE ISTO DESENHA
 *
 *   - uma pilha discreta no canto superior direito com o nome de quem está dirigindo;
 *   - ao tocar, a lista de perfis; ao escolher, o OPEN GW aplica o perfil inteiro e este
 *     arquivo mostra o que o carro aceitou e o que recusou.
 *
 * POR QUE A TROCA NÃO É FEITA AQUI
 *
 * Um perfil é ~150 preferências do OPEN GW mais as configurações do barramento. Nada disso está
 * ao alcance deste WebView: as pontes que o APK expõe ao JavaScript (TelemetryBridge,
 * AppLauncherBridge, MediaBridge, TripBridge) não leem nem escrevem preferência nenhuma. O que
 * este arquivo faz é pedir — `POST /profiles/select` no servidor que o OPEN GW já mantém em
 * 127.0.0.1:8766 — e mostrar a resposta. Toda a lógica de aplicar mora do lado de lá, num só
 * lugar, e é a mesma que a tela do app usa.
 *
 * O RELATÓRIO NÃO É ENFEITE
 *
 * O carro recusa configuração: mudar modo de condução em movimento, chave que não existe no
 * acabamento, módulo que não respondeu a tempo. Sem mostrar isso aqui, quem troca de perfil pelo
 * launcher vê o tema mudar e conclui que o resto foi junto. Por isso a janela só fecha depois
 * que a resposta chega, e as pendências ficam escritas nela.
 */
(function () {
  'use strict';

  /** De quanto em quanto tempo a pilha confere o perfil ativo, em ms. */
  var INTERVALO_DE_CONFERENCIA_MS = 60000;

  /**
   * Quanto esperar pela aplicação de um perfil, em ms.
   *
   * Os 4 s padrão do `post` não servem aqui: aplicar grava ~150 preferências, manda oito
   * subsistemas relerem e escreve no barramento chave por chave. Desistir no meio não cancela
   * nada — o carro continua aplicando — só faz a tela mentir.
   */
  var ESPERA_DA_TROCA_MS = 45000;

  var estado = { ativo: null, perfis: [], disponivel: false };
  var pilula = null;
  var conferindo = null;

  function OpenGW() {
    return window.OpenGW;
  }

  function escapar(texto) {
    var gw = OpenGW();
    if (gw && gw.escapeHtml) return gw.escapeHtml(texto);
    return String(texto).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  // ── A pilha da tela inicial ───────────────────────────────────────────────────────────────
  //
  // Mesma linguagem do atalho de projeção que já existe (mesma altura, mesma borda, mesmo vidro),
  // e empilhada logo acima dele: os dois são absolutos dentro do #hv-root, então a posição é
  // calculada e não disputada — 48px acima é a altura de um mais a folga.
  //
  // POR QUE NO ALTO À DIREITA
  //
  // O canto inferior esquerdo era o lugar óbvio e está ocupado: a fileira de widgets do próprio
  // launcher (WORKSPACE, relógio, ESTADO DO VEÍCULO) começa ali e é desenhada POR CIMA de
  // qualquer coisa pendurada no #hv-root. Medido no emulador nas duas resoluções — 1080x600 e
  // 1920x720, a do carro: as duas pilhas apareciam fantasma, atrás do cartão WORKSPACE, legíveis
  // pela metade e sem receber toque.
  //
  // O alto à direita é o único retângulo que fica livre nas duas: só o cenário 3D passa por lá.
  // O alto à ESQUERDA não serve — o cartão de mídia começa em y=120 na tela menor.

  function injetarCss() {
    if (document.getElementById('opengw-profile-css')) return;
    var css = document.createElement('style');
    css.id = 'opengw-profile-css';
    css.textContent =
      // A posição absoluta continua sendo a REGRA BASE de propósito: o trilho de atalhos é do
      // openGwClient.js e a regra dele (`#opengw-atalhos > #opengw-profile`, dois ids) ganha
      // desta por especificidade quando a pílula está lá dentro. Se um dia o trilho não
      // existir, a pílula volta sozinha para onde sempre esteve, em vez de cair empilhada no
      // canto superior esquerdo do #hv-root.
      '#opengw-profile{position:absolute;z-index:61;pointer-events:auto;' +
      'right:calc(var(--hv-safe-right, 0px) + 24px);top:42px;' +
      'display:flex;align-items:center;gap:8px;padding:9px 14px;border-radius:999px;' +
      // Lado a lado com os outros dois atalhos, a diferença de altura (33.8px medidos contra
      // 44px) e de corpo de texto (10px contra 11px) lia como defeito. Empilhadas, ninguém
      // comparava. 44px também é o piso de alvo de toque.
      'min-height:44px;box-sizing:border-box;' +
      'border:1px solid rgba(255,255,255,.16);background:rgba(10,10,10,.62);' +
      'color:rgba(255,255,255,.86);font:600 11px/1 "IBM Plex Sans",system-ui,sans-serif;' +
      'letter-spacing:.14em;cursor:pointer;-webkit-tap-highlight-color:transparent;' +
      'backdrop-filter:blur(8px);max-width:26vw;}' +
      '#opengw-profile:active{background:rgba(79,158,255,.26)}' +
      '#opengw-profile svg{width:14px;height:14px;flex:none}' +
      '#opengw-profile .nome{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}' +
      '#opengw-profile.is-vazio{opacity:.62}' +
      // A lista de perfis reaproveita a janela modal do painel OPEN GW (.opengw-modal e filhos),
      // então só o que é exclusivo daqui é declarado.
      //
      // A folga embaixo da lista não é respiro: o miolo da janela rola (.opengw-modal-body tem
      // overflow-y:auto) e, sem ela, a borda inferior do cartão do perfil ativo cai exatamente
      // na aresta de rolagem e é cortada — conferido no emulador a 1920x720, a altura do carro.
      // A janela do painel OPEN GW reserva embaixo o MAIOR entre a barra do launcher e a área
      // segura da central. Medido aqui: área segura = 224px de 463 de viewport, quase metade da
      // tela — ela existe para manter uma PILHA acima da fileira de widgets, e é o valor certo
      // para isso. Para uma janela de diálogo é demais: sobravam 152px de cartão e a lista saía
      // cortada mesmo com um único perfil. Esta janela respeita só a barra do próprio launcher.
      '#opengw-profile-modal{padding-bottom:calc(var(--hv-launcher-bottom, 0px) + 14px);}' +
      '#opengw-profile-list{padding-bottom:6px;}' +
      '.opengw-perfil-linha{display:flex;align-items:center;gap:10px;}' +
      '.opengw-perfil-relatorio{font-size:11px;line-height:1.5;opacity:.75;margin-top:10px;' +
      'border-left:2px solid rgba(127,127,127,.35);padding-left:10px;}' +
      '.opengw-perfil-ok{color:#57b56a;}' +
      '.opengw-perfil-erro{color:#d9714e;}';
    document.head.appendChild(css);
  }

  function icone() {
    return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" ' +
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>' +
      '<circle cx="12" cy="7" r="4"></circle></svg>';
  }

  function instalarPilula(host) {
    if (document.getElementById('opengw-profile')) return;
    injetarCss();

    var b = document.createElement('button');
    b.id = 'opengw-profile';
    b.type = 'button';
    b.innerHTML = icone() + '<span class="nome"></span>';
    var gw = OpenGW();
    // O trilho é do openGwClient.js. Se ele não estiver lá (versão antiga do launcher, ou
    // carregamento pela metade), a pílula ainda tem de aparecer: cai no host, como sempre foi.
    ((gw && gw.trilhoDeAtalhos && gw.trilhoDeAtalhos(host)) || host).appendChild(b);
    pilula = b;

    b.addEventListener('click', function () {
      abrirLista();
    });

    pintar();
  }

  function pintar() {
    if (!pilula) return;
    var nome = pilula.querySelector('.nome');
    if (!estado.disponivel) {
      nome.textContent = 'PERFIL';
      pilula.classList.add('is-vazio');
      pilula.title = 'O OPEN GW não respondeu.';
      return;
    }
    if (!estado.ativo) {
      nome.textContent = 'SEM PERFIL';
      pilula.classList.add('is-vazio');
      pilula.title = 'Nenhum perfil ativo. Toque para escolher.';
      return;
    }
    nome.textContent = String(estado.ativo).toUpperCase();
    pilula.classList.remove('is-vazio');
    pilula.title = 'Perfil de ' + estado.ativo + '. Toque para trocar.';
  }

  function conferir() {
    var gw = OpenGW();
    if (!gw) return Promise.resolve(estado);
    return gw.get('/profiles').then(function (dados) {
      estado.disponivel = true;
      estado.ativo = dados && dados.active ? dados.active : null;
      estado.perfis = (dados && dados.profiles) || [];
      pintar();
      return estado;
    }).catch(function () {
      estado.disponivel = false;
      pintar();
      return estado;
    });
  }

  // ── A lista ───────────────────────────────────────────────────────────────────────────────

  function abrirLista() {
    if (document.getElementById('opengw-profile-modal')) return;
    injetarCss();

    var gw = OpenGW();
    var wrap = document.createElement('div');
    wrap.id = 'opengw-profile-modal';
    wrap.className = 'opengw-modal';
    wrap.innerHTML =
      // O título fica FORA do miolo que rola, ao contrário do seletor de modelo que serviu de
      // molde. Medido no emulador a 1920x720: o cartão é limitado pelas áreas seguras da central
      // (58px em cima, 224px embaixo) e sobra pouco mais de 150px de altura; com o título dentro
      // da rolagem, o conteúdo pedia 82px numa janela de 65 e a borda do perfil ativo saía
      // cortada. Fora dela o título também para de sumir quando a lista é longa.
      '<div class="opengw-modal-card">' +
        '<div class="opengw-modal-title">Quem está dirigindo?</div>' +
        '<div class="opengw-modal-body">' +
          '<div class="opengw-modal-sub">Trocar de perfil devolve o OPEN GW e as configurações do ' +
            'carro do jeito que essa pessoa deixou — tema do painel, HUD, som do motor, barra, ' +
            'atalhos e a memória do banco.</div>' +
          '<div class="opengw-grid" id="opengw-profile-list"></div>' +
          '<div id="opengw-profile-report"></div>' +
        '</div>' +
        '<button type="button" class="opengw-secondary" id="opengw-profile-close">Fechar</button>' +
      '</div>';

    ((gw && gw.mountRoot && gw.mountRoot()) || document.body).appendChild(wrap);
    wrap.querySelector('#opengw-profile-close').onclick = function () { wrap.remove(); };

    var lista = wrap.querySelector('#opengw-profile-list');
    lista.innerHTML = '<div class="opengw-modal-sub" style="margin:0">lendo…</div>';

    conferir().then(function () {
      desenharLista(wrap, lista);
    });
  }

  function desenharLista(wrap, lista) {
    lista.innerHTML = '';

    if (!estado.disponivel) {
      lista.innerHTML = '<div class="opengw-modal-sub" style="margin:0">O OPEN GW não respondeu. ' +
        'Sem ele no ar, o launcher não consegue trocar de perfil.</div>';
      return;
    }
    if (!estado.perfis.length) {
      lista.innerHTML = '<div class="opengw-modal-sub" style="margin:0">Nenhum perfil salvo ainda. ' +
        'Crie o primeiro no OPEN GW, em Perfis de Motorista: ele guarda o carro do jeito que ' +
        'estiver naquele instante.</div>';
      return;
    }

    estado.perfis.forEach(function (perfil) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'opengw-choice' + (perfil.name === estado.ativo ? ' is-on' : '');
      b.innerHTML =
        '<span class="opengw-choice-name">' + escapar(perfil.name) + '</span>' +
        '<span class="opengw-choice-hint">' + escapar(descrever(perfil)) + '</span>';
      b.onclick = function () {
        escolher(wrap, lista, perfil.name);
      };
      lista.appendChild(b);
    });
  }

  function descrever(perfil) {
    var partes = [];
    if (perfil.ajustes) partes.push(perfil.ajustes + ' ajustes do OPEN GW');
    if (perfil.veiculo) partes.push(perfil.veiculo + ' do carro');
    if (perfil.banco !== null && perfil.banco !== undefined) partes.push('banco ' + perfil.banco);
    if (!partes.length) partes.push('vazio');
    return partes.join(' · ');
  }

  function escolher(wrap, lista, nome) {
    var relatorio = wrap.querySelector('#opengw-profile-report');
    // Desabilitar TODOS os botões, e não só o tocado: aplicar dois perfis ao mesmo tempo
    // escreveria as duas listas de preferências uma por cima da outra, e o resultado não seria
    // nenhum dos dois.
    Array.prototype.forEach.call(lista.children, function (c) { c.disabled = true; });
    relatorio.innerHTML = '<div class="opengw-perfil-relatorio">Aplicando o perfil de ' +
      escapar(nome) + '…</div>';

    var gw = OpenGW();
    gw.post('/profiles/select', { name: nome }, ESPERA_DA_TROCA_MS).then(function (resposta) {
      estado.ativo = (resposta && resposta.active) || nome;
      pintar();
      mostrarRelatorio(relatorio, resposta && resposta.relatorio);
      desenharLista(wrap, lista);
    }).catch(function (e) {
      relatorio.innerHTML = '<div class="opengw-perfil-relatorio opengw-perfil-erro">' +
        'Não deu para aplicar: ' + escapar(e.message) + '</div>';
      Array.prototype.forEach.call(lista.children, function (c) { c.disabled = false; });
      // A troca pode ter acontecido pela metade antes de a resposta se perder no caminho; reler
      // é o único jeito honesto de saber em que estado o carro ficou.
      conferir();
    });
  }

  function mostrarRelatorio(host, relatorio) {
    if (!relatorio) { host.innerHTML = ''; return; }
    var classe = relatorio.tudoCerto ? 'opengw-perfil-ok' : 'opengw-perfil-erro';
    var html = '<div class="opengw-perfil-relatorio"><div class="' + classe + '">' +
      escapar(relatorio.resumo) + '</div>';
    (relatorio.detalhes || []).forEach(function (linha) {
      html += '<div>' + escapar(linha) + '</div>';
    });
    host.innerHTML = html + '</div>';
  }

  // ── Ligação ───────────────────────────────────────────────────────────────────────────────

  function comecarAConferir() {
    if (conferindo) return;
    conferindo = setInterval(function () {
      // Com a tela apagada ou o launcher atrás de outro app, conferir é gasto sem leitor.
      if (document.hidden) return;
      conferir();
    }, INTERVALO_DE_CONFERENCIA_MS);
  }

  function instalar() {
    var gw = OpenGW();
    if (!gw || !gw.whenMounted) return;
    gw.whenMounted(function (host) {
      instalarPilula(host);
      conferir();
      comecarAConferir();
    });
  }

  window.OpenGwProfile = {
    abrir: abrirLista,
    conferir: conferir,
    instalar: instalar,
    ativo: function () { return estado.ativo; }
  };

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', instalar);
  } else {
    instalar();
  }
})();
