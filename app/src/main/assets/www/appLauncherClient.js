(function() {
  'use strict';
  // Native Android launcher UI lives in MainActivity.java
  // (icon strip + left freeform popups + right media slot).
  //
  // Shell layout:
  //   window.onAndroidShellLayout({
  //     left, right: 'idle'|'app',
  //     mode: 'triple'|'appCar'|'appsOnly',
  //     launchSide: 'auto'|'left'|'right', nextSide: 'left'|'right',
  //     leftBounds, rightBounds,
  //     safeTop, safeLeft, safeRight, safeBottom, launcherBottom
  //   })
  //   - left  → left freeform app is open
  //   - right → 'idle' now-playing card, or 'app' in the right slot
  //   - mode  → triple (app+car+app), appCar, appsOnly (persisted; dual-pane launchers)
  // Native: AppLauncherBridge.setShellMode(mode), setLaunchSide(side),
  //         launchAppInSlot(pkg, side), setSplitRatio('1:1'|'2:1'|'1:2'),
  //         swapApps(), saveAppsOnlyDefault(),
  //         dismissOverlays() — closes freeform so WebView menus can show
  //         setChromeOnTop(onTop) — raise viewer chrome over freeform
  //         setLauncherHidden(hidden) — hide/show the native icon strip
  //           (use window.requestLauncherHidden below, not this method directly)
  //         setSlotUse(side, 'app'|'widgets') — implicit: adding a widget
  //         claims the slot; launching an app claims it; emptying it frees it.
  //         saveWidgets(json), loadWidgets()
  // Payload also includes splitRatio for APP+APP.
  // Fallback: window.onAndroidLauncherPopup(active: boolean)  (left only)
  //
  // Now playing:
  //   window.onMediaNowPlaying({ title, artist, album, durationMs, positionMs,
  //     playing, appLabel, packageName, artDataUrl, hasTrack })
  //   window.onMediaPosition(positionMs)
  // Transport: window.MediaBridge.prev|playPause|next()

  var pendingShell = null;
  var pendingMedia = null;

  /**
   * Quem pede a faixa nativa de ícones fora do caminho — e o que acontece quando dois pedem.
   *
   * Duas telas precisam da central limpa: CONFIGURAÇÕES OPEN GW (openGwPanel.js) e o Modo
   * Descanso (openGwRest.js). Elas se sobrepõem — dá para entrar no descanso estando na página
   * de configurações. Se cada uma chamasse `setLauncherHidden(false)` ao sair, a primeira a sair
   * devolveria os ícones por cima da outra, que ainda quer a tela limpa.
   *
   * Então ninguém fala com a ponte direto. Cada tela declara um pedido com nome e o estado
   * enviado ao nativo é só "sobrou algum pedido?". Sai o último, voltam os ícones.
   *
   * Devolve se a faixa está escondida DE FATO — `false` também quando o launcher nativo não tem o
   * método, que era o caso enquanto o launcher era um APK à parte. Hoje ninguém usa a resposta;
   * ela existe para que uma tela que precise ajustar o próprio desenho não tenha de SUPOR que foi
   * atendida, e é por ela que o teste consegue provar a regra sem abrir a central.
   */
  var hideRequests = {};
  var lastHiddenSent = null;

  window.requestLauncherHidden = function(owner, wanted) {
    if (wanted) hideRequests[owner] = true;
    else delete hideRequests[owner];

    var hidden = false;
    for (var key in hideRequests) {
      if (Object.prototype.hasOwnProperty.call(hideRequests, key)) { hidden = true; break; }
    }
    if (hidden === lastHiddenSent) return hidden;

    var bridge = window.AppLauncherBridge;
    if (!bridge || typeof bridge.setLauncherHidden !== 'function') return false;
    try {
      bridge.setLauncherHidden(hidden);
    } catch (e) {
      return lastHiddenSent === true;
    }
    lastHiddenSent = hidden;
    return hidden;
  };

  // The safe-area insets have to land even before the viewer registers
  // window.__app: native sends them while the GLB is still loading, and without
  // this the whole load renders with no side band and the chrome under the
  // MMI header. The root element exists as soon as the page paints.
  function applySafeInsets(layout) {
    var el = document.getElementById('hv-root');
    var html = document.documentElement;
    function setVar(name, value) {
      var px = value + 'px';
      if (el) el.style.setProperty(name, px);
      // Splash SKIP sits outside #hv-root and inherits from <html>.
      if (html) html.style.setProperty(name, px);
    }
    if (typeof layout.safeTop === 'number') setVar('--hv-safe-top', layout.safeTop);
    if (typeof layout.safeLeft === 'number') setVar('--hv-safe-left', layout.safeLeft);
    if (typeof layout.safeRight === 'number') setVar('--hv-safe-right', layout.safeRight);
    if (typeof layout.safeBottom === 'number') setVar('--hv-safe-bottom', layout.safeBottom);
    if (typeof layout.launcherBottom === 'number') setVar('--hv-launcher-bottom', layout.launcherBottom);
    // Align page chrome with the left widget board, but never under the brand.
    // Native may send this while React is still loading the GLB, so the CSS var
    // has to land here — applyShellLayout skips setState during that window.
    if (layout.leftBounds && typeof layout.leftBounds.l === 'number') {
      setVar('--hv-chrome-main-left', Math.max(layout.leftBounds.l, 96));
    }
  }

  function applyShell(layout) {
    if (!layout || typeof layout !== 'object') return;
    window.__androidShell = layout;
    window.__androidPopupActive = !!layout.left;
    applySafeInsets(layout);
    if (window.__app && typeof window.__app.applyShellLayout === 'function') {
      window.__app.applyShellLayout(layout);
    } else if (window.__app && typeof window.__app.applyLauncherPopupLayout === 'function') {
      window.__app.applyLauncherPopupLayout(!!layout.left);
    }
  }

  window.onAndroidShellLayout = function(layout) {
    pendingShell = layout;
    applyShell(layout);
  };

  window.onAndroidLauncherPopup = function(active) {
    var right = (pendingShell && pendingShell.right) || 'idle';
    window.onAndroidShellLayout({ left: !!active, right: right });
  };

  window.onMediaNowPlaying = function(payload) {
    pendingMedia = payload || {};
    if (window.__app && typeof window.__app.applyMediaNowPlaying === 'function') {
      window.__app.applyMediaNowPlaying(pendingMedia);
    }
  };

  window.onMediaPosition = function(ms) {
    if (window.__app && typeof window.__app.applyMediaPosition === 'function') {
      window.__app.applyMediaPosition(ms);
    }
  };

  window.__flushAndroidShell = function() {
    if (pendingShell) applyShell(pendingShell);
    // Media is applied by the viewer after loading finishes (see applyMediaNowPlaying).
    if (pendingMedia && window.__app && typeof window.__app.applyMediaNowPlaying === 'function') {
      window.__app.applyMediaNowPlaying(pendingMedia);
    }
  };
})();
