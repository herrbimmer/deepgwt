// O CSS do motor. Idêntico byte a byte em todo tema gerado — o que muda de um tema para outro
// vive no segundo bloco `<style>', montado por 'emit/themeCss.ts'.
//
// Nada aqui fixa cor. Tudo referencia tokens ('var(--t-…)'), que o bloco do tema define e troca
// entre dia, noite e farol.
//
// TETO DE COMPATIBILIDADE DO WEBVIEW DO CARRO
// ------------------------------------------
// O painel é um WebView do sistema (Android 9, /system/app/webview) e não há como consultar a
// versão dele daqui. O que existe é um piso PROVADO pelos dois temas que funcionam no carro:
//
//   • o Default (res/raw/app.html) usa `?.` e `??` -> Chrome 80+;
//   • Default e Bavarian ID7 usam `gap` em flex -> Chrome 84+;
//   • `clamp()` aparece nos dois -> Chrome 79+.
//
// Nenhum dos dois usa `inset` (Chrome 87) nem `aspect-ratio` (Chrome 88) — e eram exatamente
// essas duas as únicas propriedades do motor acima do piso provado. Numa engine entre 84 e 86 o
// navegador DESCARTA a declaração inteira em silêncio, e o efeito é o que o relatório descreve:
//
//   • `#app` e `.page` ficam 0x0 (absolutos sem deslocamento e sem largura);
//   • `.dial-center` colapsa e a leitura central do mostrador (velocidade, marcha, modo) vai
//     para o canto superior esquerdo do disco em vez do centro;
//   • `.boot` vira uma caixa preta pequena no canto, presa por cima do tema até o fim da varredura;
//   • a capa da mídia fica com altura zero.
//
// Por isso aqui só entra CSS dentro do piso provado: deslocamentos em quatro propriedades
// separadas, e nada de `aspect-ratio` (a capa é medida em px pelo renderizador, que conhece
// `el.rect.w`). `scripts/test-theme-engine.mjs` trava isso com uma lista negra.

export const ENGINE_CSS = `
* { box-sizing: border-box; }

/* O html fica SEMPRE transparente e só o body pinta. É isso que faz o "sair da frente do carro"
   funcionar: quando um aviso do veículo sobe, o body perde o fundo e a camada nativa aparece.
   Pintar o html quebraria isso e o tema cobriria avisos reais. */
html { background: transparent; margin: 0; padding: 0; }
body {
  margin: 0; padding: 0;
  width: 1920px; height: 720px;
  overflow: hidden;
  color: var(--t-ink, #fff);
  -webkit-font-smoothing: antialiased;
}
body.yield-to-car { background: none !important; background-color: transparent !important; }
body.yield-to-car #app { opacity: 0; }

#app { position: absolute; left: 0; top: 0; right: 0; bottom: 0; transition: opacity .25s ease; }
.page { position: absolute; left: 0; top: 0; right: 0; bottom: 0; display: none; }
.page.active { display: block; }
.el { position: absolute; }
.el.hidden-rule { opacity: 0; pointer-events: none; }

/* --- mostrador redondo ------------------------------------------------------------------ */
.dial { position: absolute; }
.dial svg { width: 100%; height: 100%; display: block; overflow: visible; }
.dial-face, .dial-ring, .dial-bezel { transition: fill .3s ease, stroke .3s ease; }

/* Vidro do mostrador. Existe como <div> e nao como <circle> porque 'backdrop-filter' e
   propriedade de caixa CSS: num filho de SVG o Chromium a ignora em silencio.

   O raio entra por variavel e o resto e constante, para o motor continuar byte a byte igual em
   todo tema. 'saturate' esta junto do 'blur' pelo mesmo motivo que o vidro da Apple: desfocar
   sozinho lava a cor do que esta atras, e o papel de parede vira cinza.

   A propriedade prefixada vem ANTES da padrao de proposito: se o WebView do carro so conhecer a
   prefixada, ela vale; se conhecer as duas, a ultima vence e as duas dizem a mesma coisa. */
.dial-glass {
  position: absolute; border-radius: 50%; pointer-events: none;
  /* z-index NEGATIVO, e isto nao e detalhe: um filho POSICIONADO pinta depois do conteudo de
     fluxo do mesmo contexto, entao sem esta linha o vidro fica em cima do <svg> e desfoca as
     proprias marcacoes, o ponteiro e os numeros -- o mostrador inteiro sai borrado. Com -1 ele
     pinta entre o fundo do host e o <svg>, que e onde um fundo mora. */
  z-index: -1;
  -webkit-backdrop-filter: blur(var(--glass-blur, 0px)) saturate(135%);
  backdrop-filter: blur(var(--glass-blur, 0px)) saturate(135%);
}

.tick { transition: stroke .3s ease, opacity .3s ease; }
.tick.off { opacity: 0; }
/* Acima do limite da via. O brilho sai de 'currentColor' via drop-shadow em vez de text-shadow
   porque em SVG o preenchimento de um <text> não gera sombra de texto. */
.tick.over-limit { filter: drop-shadow(0 0 4px currentColor); }
.tick-label.over-limit { filter: drop-shadow(0 0 6px currentColor); fill-opacity: 1; }

/* transform-box e transform-origin são OBRIGATÓRIOS. Sem os dois, um <text> de SVG escala a
   partir da origem do sistema de coordenadas e o número sai voando para fora do painel — some da
   tela sem erro nenhum no console. */
.tick-label {
  transform-box: fill-box;
  transform-origin: center;
  fill-opacity: .9;
  transition: transform var(--zoom-dur, .38s) var(--zoom-ease, cubic-bezier(.22,.61,.36,1)),
              fill-opacity .32s ease, fill .3s ease;
}
.tick-label.zoomed {
  fill-opacity: 1;
  font-weight: var(--zoom-weight, 700);
  transform: scale(var(--zoom-scale, 1.5));
}

.needle { transition: stroke .3s ease; }
/* O grupo do ponteiro gira a cada atualizacao de telemetria e o ponteiro pode ter brilho.
   Sem a promocao a camada, o raster do brilho e refeito a cada grau. */
.needle-rot { will-change: transform; }
.needle-cap, .needle-cap-ring { transition: fill .3s ease, stroke .3s ease; }
.arc { fill: none; stroke-linecap: round; transition: stroke .3s ease; }
.seg { transition: fill .2s ease, opacity .2s ease; }
.seg.off { opacity: .16; }

.dial-center {
  position: absolute; left: 0; top: 0; right: 0; bottom: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  pointer-events: none;
}
.dial-center .v { line-height: .9; }
.dial-center .l { opacity: .62; }

/* --- barra ------------------------------------------------------------------------------- */
.bar { display: flex; flex-direction: column; justify-content: center; height: 100%; gap: 8px; }
.bar-track { position: relative; width: 100%; height: 100%; border-radius: 999px; overflow: hidden; }
.bar-fill { position: absolute; left: 0; top: 0; height: 100%; width: 0%; border-radius: 999px; transition: width .25s ease; }
.bar.centre .bar-fill { left: 50%; transform-origin: left center; }
.bar-segments { display: flex; width: 100%; height: 100%; gap: 3px; }
.bar-segments > i { flex: 1; border-radius: 3px; }
.bar-row { display: flex; justify-content: space-between; align-items: baseline; }
.bar-row .l { opacity: .62; }
.bar.vertical { flex-direction: row; }
.bar.vertical .bar-fill { width: 100%; height: 0%; top: auto; bottom: 0; transition: height .25s ease; }

/* --- leitura ----------------------------------------------------------------------------- */
.readout { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; text-align: center; }
.readout.stat { flex-direction: row; gap: 10px; align-items: center; justify-content: flex-start; }
.readout .v { line-height: .9; white-space: nowrap; }
.readout .l { opacity: .62; }
.readout .sub { opacity: .62; }
.readout.text .v { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* --- indicador --------------------------------------------------------------------------- */
.indicator { display: flex; align-items: center; justify-content: center; height: 100%; transition: color .25s ease, opacity .25s ease; }
.indicator svg { width: 100%; height: 100%; display: block; }
.indicator.badge {
  border-radius: 999px; border: 1px solid currentColor;
  padding: 0 .55em; opacity: .95;
}

/* --- mídia ------------------------------------------------------------------------------- */
.media { display: flex; height: 100%; transition: opacity .35s ease, transform .35s ease; }
.media.card { flex-direction: column; align-items: center; justify-content: center; gap: 14px; border-radius: 22px; }
.media.line { flex-direction: row; align-items: center; justify-content: center; gap: 12px; }
.media.empty { opacity: 0; transform: translateY(6px); }
.media.line.empty { opacity: .45; transform: none; }
/* 32% de uma caixa de 640 dá 205 px, que é o tamanho da capa nos temas escritos à mão. O lado
   sai em PIXEL do renderizador (que conhece el.rect.w) e não da proporção automática, que é
   Chrome 88 — o WebView do carro só está provado até 84, e lá a capa ficaria com altura zero. */
.media-art { display: none; border-radius: 16px; object-fit: cover; background: rgba(255,255,255,.06); }
.media-art.has-src { display: block; }
.media-art-ph {
  border-radius: 16px;
  background: rgba(255,255,255,.06); color: var(--t-inkFaint, rgba(255,255,255,.3));
  display: flex; align-items: center; justify-content: center;
}
.media-art.has-src ~ .media-art-ph { display: none; }
/* O ícone é dimensionado aqui e não por padding no pai: padding em porcentagem resolve contra
   a LARGURA DO BLOCO CONTENDOR, não contra o próprio elemento, e engolia a caixa inteira. */
.media-art-ph svg { width: 32%; height: 32%; }
/* O lado da capa na linha vem do renderizador (54% da altura da caixa); aqui só o canto. */
.media.line .media-art, .media.line .media-art-ph { border-radius: 10px; }
/* Sem nada tocando a linha fica a 45% e sem texto: o quadrado da capa sozinho seria um ícone de
   nota musical solto no painel, sem dizer nada. Ele volta junto com o título. */
.media.line.empty .media-art, .media.line.empty .media-art-ph { display: none; }
.media.line .media-art-ph svg { width: 60%; height: 60%; }
.media-txt { min-width: 0; text-align: center; max-width: 100%; }
.media.line .media-txt { text-align: left; }
.media-txt div { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* --- marcha em destaque ------------------------------------------------------------------- */
.readout.gear { flex-direction: row; gap: .38em; }
.readout.gear span { opacity: .38; transition: color .25s ease, opacity .25s ease, transform .25s ease; }
.readout.gear span.on { opacity: 1; transform: scale(1.22); }

/* --- imagem e forma ---------------------------------------------------------------------- */
/* Quatro deslocamentos, nunca 'inset': o WebView do carro só está provado até o Chrome 84. */
.img, .shape { position: absolute; left: 0; top: 0; right: 0; bottom: 0; pointer-events: none; }
.img { background-repeat: no-repeat; background-position: center; }
.shape.circle { border-radius: 50%; }

/* --- mini-histórico ---------------------------------------------------------------------- */
.chart { display: flex; flex-direction: column; justify-content: flex-end; height: 100%; gap: 6px; }
.chart-row { display: flex; justify-content: space-between; align-items: baseline; }
.chart-row .l { opacity: .62; }
.chart svg { width: 100%; display: block; overflow: visible; }

/* --- fundo por página -------------------------------------------------------------------- */
.page-bg {
  position: absolute; left: 0; top: 0; right: 0; bottom: 0; pointer-events: none;
  background-size: cover; background-position: center; background-repeat: no-repeat;
}

/* --- decoração --------------------------------------------------------------------------- */
.decor { pointer-events: none; }
.decor.panel { border-radius: var(--decor-radius, 24px); }
.decor.glow { border-radius: 50%; }
.decor.divider { border-radius: 2px; }

/* --- paginador --------------------------------------------------------------------------- */
.pager { position: absolute; left: 50%; bottom: 12px; transform: translateX(-50%); display: flex; gap: 10px; z-index: 9990; }
.pager i { width: 10px; height: 10px; border-radius: 50%; background: rgba(255,255,255,.25); display: block; }
.pager i.on { background: rgba(255,255,255,.92); }

/* --- abertura ---------------------------------------------------------------------------- */
.boot {
  position: absolute; left: 0; top: 0; right: 0; bottom: 0; z-index: 9999;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 18px;
  background: #000; transition: opacity .6s ease;
}
.boot.done { opacity: 0; pointer-events: none; }
.boot-ring {
  width: 46px; height: 46px; border-radius: 50%;
  border: 2px solid rgba(255,255,255,.14); border-top-color: var(--t-accent, #fff);
  animation: boot-spin .9s linear infinite;
}
.boot-word { font-size: 18px; font-weight: 700; letter-spacing: 5px; color: var(--t-ink, #fff); }
@keyframes boot-spin { to { transform: rotate(360deg); } }

/* Quem pediu menos transparencia no sistema nao quer vidro: o desfoque sai, e o que segura a
   legibilidade passa a ser a tinta do disco, que continua desenhada por cima. */
@media (prefers-reduced-transparency: reduce) {
  .dial-glass { -webkit-backdrop-filter: none; backdrop-filter: none; }
}

@media (prefers-reduced-motion: reduce) {
  .boot-ring { animation: none; }
  .tick-label, .bar-fill, .needle, .arc, .media, #app, .readout.gear span { transition-duration: .01ms !important; }
}
`
