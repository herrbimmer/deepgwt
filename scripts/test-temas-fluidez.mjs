// Fluidez dos temas do cluster: o que não pode voltar a acontecer a 60 quadros por segundo.
//
// Roda com `node scripts/test-temas-fluidez.mjs`.
//
// POR QUE ESTE TESTE EXISTE
//
// O Coffe 3 e o Cupertino Ultra já tinham recebido, em rodadas anteriores, o conserto do
// ponteiro: parar de reescrever a GEOMETRIA de um elemento que carrega `filter: drop-shadow` e
// passar a girar um grupo. O Bavarian ID7 nasceu depois e nunca recebeu — continuou com uma
// transição CSS em x1/y1/x2/y2, que é animação de linha principal E invalidação da região do
// filtro, as duas coisas, sessenta vezes por segundo. Ninguém percebeu porque o defeito não
// aparece em nada além de quadros perdidos num painel que já é apertado de CPU.
//
// Um tema novo copiado de outro tema herda o que o outro tiver. Este arquivo é onde a regra
// fica escrita em vez de ficar só no comentário de um dos quatro.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const temasDir = path.join(raiz, 'app', 'src', 'main', 'assets', 'PreinstalledThemes')

// Tira comentários de linha e de bloco: várias regras aqui contam ocorrências de uma chamada, e
// a prosa que EXPLICA a chamada não pode contar como uma delas.
function semComentarios(txt) {
  return txt.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')
}

let falhas = 0
let total = 0
function ok(nome, condicao, porque) {
  total++
  if (condicao) { console.log('ok  ' + nome); return }
  falhas++
  console.log('FALHOU  ' + nome)
  if (porque) console.log('        ' + porque)
}

if (!fs.existsSync(temasDir)) {
  console.log('PULADO: ' + temasDir + ' não encontrado')
  process.exit(0)
}

const temas = fs.readdirSync(temasDir).filter((d) =>
  fs.existsSync(path.join(temasDir, d, 'app.html')))
const fonte = {}
for (const t of temas) fonte[t] = fs.readFileSync(path.join(temasDir, t, 'app.html'), 'utf8')

ok('os quatro temas embutidos estão presentes', temas.length >= 4, 'achados: ' + temas.join(', '))

// ── 1. Nenhum tema anima geometria SVG por transição ───────────────────────────────────────
//
// `transition: x1 ...` e parentes fazem o WebView interpolar a geometria na linha principal e,
// se o elemento tiver filtro, re-rasterizar o filtro a cada quadro. O caminho barato é girar um
// grupo: `transform` é a única propriedade, junto de `opacity`, que o compositor anima sozinho.
const GEOMETRIA = /transition:[^;}]*\b(x1|y1|x2|y2|cx|cy|\br\b|\bd\b)\s+\d/
for (const t of temas) {
  ok('[' + t + '] não há transição de geometria SVG',
    !GEOMETRIA.test(fonte[t]),
    'anima x1/y1/x2/y2 na linha principal e invalida a região do filtro a cada quadro')
}

// ── 2. Todo ponteiro gira; nenhum é reescrito ponto a ponto por quadro ─────────────────────
// O Bávaro ID8, o Sweede SENSUS, o TEA 3.0 e o Cupertino Extended não têm camada composta: têm
// regras próprias nas seções 3b, 3c e 3d.
const SEM_CAMADA = ['bavaroid8', 'sweedesensus', 'tea30', 'cupertinoextended']
for (const t of temas) {
  if (SEM_CAMADA.includes(t)) continue
  const f = fonte[t]
  const giraPorAtributo = /setAttribute\(\s*'transform'\s*,\s*[`'"]rotate\(/.test(f)
  // O Bavarian ID7 (6.9) leva a ponta pelo aro e gira: um só transform, translate + rotate.
  const giraPorCss = /\.style\.transform\s*=\s*'rotate\(/.test(f) ||
    /\.style\.transform\s*=\s*'translate\([^;\n]*rotate\(/.test(f)
  ok('[' + t + '] o que se move gira em vez de ser redesenhado',
    giraPorAtributo || giraPorCss,
    'sem transform, cada atualização reescreve a geometria e refaz o raster do brilho')
  ok('[' + t + '] o grupo que gira é promovido a camada',
    /will-change:\s*transform/.test(f),
    'sem will-change o raster do ponteiro, brilho incluso, é refeito a cada quadro')
}

// ── 3. Bavarian ID7 ────────────────────────────────────────────────────────────────────────
// Desde a 6.9 o aro não é círculo: a ponta do ponteiro corre pelo guia e o ponteiro aponta para o
// pivô. Continua sendo UM transform por atualização num grupo promovido a camada; a geometria do
// ponteiro está no markup e nunca é reescrita.
const bav = fonte['bavarianid7'] || ''
ok('[bavarianid7] os dois ponteiros estão dentro de um grupo que se move por transform',
  /<g id="needleSpeedRot" class="needle-rot">/.test(bav) &&
  /<g id="needlePowerRot" class="needle-rot">/.test(bav))
ok('[bavarianid7] transform-box: view-box com origem 0 0, para o translate valer em espaço de usuário',
  /\.needle-rot \{[\s\S]*?transform-box: view-box;[\s\S]*?transform-origin: 0 0;/.test(bav),
  'sem isso o translate/rotate é resolvido na caixa do elemento e o ponteiro sai do aro')
ok('[bavarianid7] a suavização de 90 ms é transição de transform',
  /\.needle-rot \{[\s\S]*?transition: transform 90ms linear;/.test(bav),
  'transform o compositor anima sozinho')
ok('[bavarianid7] a geometria do ponteiro nunca é escrita pelo script',
  !/setAttribute\('x[12]'/.test(semComentarios(bav)) && !/setAttribute\('y[12]'/.test(semComentarios(bav)),
  'o ponteiro é desenhado deitado no eixo x do grupo; só o transform do grupo muda')
ok('[bavarianid7] aimNeedle escreve um único transform',
  /function aimNeedle[\s\S]{0,500}?grupoEl\.style\.transform = 'translate\(/.test(bav))
ok('[bavarianid7] sem drop-shadow no ponteiro (o brilho é gradiente dentro do grupo)',
  !/\.needle[^{]*\{[^}]*drop-shadow/.test(bav))
ok('[bavarianid7] o comprimento do guia é medido uma vez e guardado',
  /const COMPRIMENTO_DO_GUIA = new WeakMap\(\)/.test(bav) &&
  /if \(len === undefined\) \{ len = pathEl\.getTotalLength\(\); COMPRIMENTO_DO_GUIA\.set\(pathEl, len\); \}/.test(bav) &&
  // Fora do guarda do WeakMap não pode sobrar nenhuma chamada. Os comentários do arquivo falam
  // de getTotalLength() em prosa, então eles saem antes da contagem.
  (semComentarios(bav).match(/getTotalLength\(\)/g) || []).length === 1,
  'getTotalLength() a cada atualização esvazia o layout pendente DEPOIS de o ponteiro anterior ' +
    'já ter escrito — leitura-depois-de-escrita, que é layout síncrono forçado')
ok('[bavarianid7] a regeneração troca de classe só quando o estado muda',
  /if \(regen !== emRegen\) \{/.test(bav),
  'trocar classe por quadro recalcularia o estilo do mostrador inteiro a cada telemetria')
ok('[bavarianid7] respeita movimento reduzido',
  /prefers-reduced-motion: reduce\) \{ \.needle-rot \{ transition: none; \} \}/.test(bav))

// ── 3b. Bávaro ID8 ─────────────────────────────────────────────────────────────────────────
// Sem camada composta nenhuma (8.6). No WebView do app a página abre num WebView ainda sem tamanho;
// camada composta criada ali saía rasterizada na escala errada, e o enchimento ficava menor que o
// chevron e fora do lugar (e o clip-path de um contêiner com camada composta virava o retângulo em
// volta do polígono). Os temas que já rodam no carro também só movem grupos DENTRO do SVG.
// O enchimento é uma pilha de faixas PARADAS recortadas pelo chevron: quando o valor anda só as
// faixas da borda trocam de visibilidade, e a linha da frente, pequena, anda por transform.
const id8 = fonte['bavaroid8'] || ''
if (id8) {
  const limpo = semComentarios(id8)
  ok('[bavaroid8] nenhuma camada composta (nenhum will-change)',
    !/will-change\s*:/.test(limpo),
    'camada criada com o WebView ainda sem tamanho fica na escala errada e fora do lugar')
  ok('[bavaroid8] o script nunca escreve geometria (d, points, x, y, largura, altura, clip-path)',
    !/setAttribute\(\s*'(d|points|x|y|x1|y1|x2|y2|width|height|cx|cy|r)'/.test(limpo) &&
      !/\.style\.(clipPath|webkitClipPath|left|top|width|height)\s*=/.test(limpo.replace(/function aplicarVaoDaProjecao[\s\S]*?\n  \}\n/, '')),
    'reescrever geometria por quadro repinta a face inteira')
  ok('[bavaroid8] o enchimento só troca a visibilidade das faixas que cruzaram a frente',
    /if \(k !== f\.primeira\) \{/.test(id8) &&
      /for \(let i = a; i < b; i\+\+\) f\.itens\[i\]\.style\.visibility = i >= k \? 'visible' : 'hidden';/.test(id8),
    'varrer todas as faixas a cada quadro seria trabalho à toa num painel apertado de CPU')
  ok('[bavaroid8] a linha da frente anda por transform, e só quando muda',
    /if \(mudou\('frente' \+ lado, tf\)\) frente\.setAttribute\('transform', tf\);/.test(id8) &&
      /const tf = 'translate\(0 ' \+/.test(id8))
  ok('[bavaroid8] a zona de recarga acende por opacidade, e só quando muda',
    /if \(mudou\('zona', op\)\) els\.zonaAzul\.style\.opacity = op;/.test(id8))
  ok('[bavaroid8] sem animação no contêiner (#app)',
    !/#app \{[^}]*animation\s*:/.test(id8))
}

// ── 3c. Sweede SENSUS ──────────────────────────────────────────────────────────────────────
// Também sem camada composta (8.7), pelo mesmo motivo do ID8. O ponteiro leva uma luz larga junto
// (±88°), e o que gira dentro do SVG invalida o retângulo em volta de si: tudo embaixo é
// repintado. Girando a luz inteira a cada quadro o painel caía para ~24 quadros por segundo no
// emulador (os temas do carro ficam em 51-60 nas mesmas condições). Por isso a luz é dividida: a
// de perto (±20°, com o ponteiro) gira a cada quadro; a de longe só acompanha a cada 2° e crava
// quando o ponteiro para. O aro é um anel de cunhas, não um disco com degradê cônico: o disco
// entrava inteiro na área repintada.
const sensus = fonte['sweedesensus'] || ''
if (sensus) {
  const limpo = semComentarios(sensus)
  ok('[sweedesensus] nenhuma camada composta (nenhum will-change)',
    !/will-change\s*:/.test(limpo),
    'camada criada com o WebView ainda sem tamanho fica na escala errada e fora do lugar')
  ok('[sweedesensus] o script nunca escreve geometria (d, points, x, y, largura, altura, clip-path)',
    !/setAttribute\(\s*'(d|points|x|y|x1|y1|x2|y2|width|height|cx|cy|r)'/.test(limpo) &&
      !/\.style\.(clipPath|webkitClipPath|left|top|width|height)\s*=/.test(limpo.replace(/function aplicarVaoDaProjecao[\s\S]*?\n  \}\n/, '')),
    'reescrever geometria por quadro repinta o mostrador inteiro')
  ok('[sweedesensus] a luz de perto e o ponteiro giram num transform só, e só quando muda',
    /if \(mudou\('giro' \+ lado, tf\)\) m\.perto\.setAttribute\('transform', tf\);/.test(sensus) &&
      /id="luzPertoE"/.test(sensus) && /id="luzPertoD"/.test(sensus))
  ok('[sweedesensus] a luz de longe só acompanha a cada GEO.longePasso graus (ou quando o ponteiro para)',
    /Math\.abs\(angulo - m\.anguloLonge\) >= GEO\.longePasso \|\| \(parado && angulo !== m\.anguloLonge\)/.test(sensus) &&
      /"longePasso":2/.test(sensus) && /id="luzLongeE"/.test(sensus) && /id="luzLongeD"/.test(sensus),
    'girar a luz inteira a cada quadro repinta meio mostrador por quadro')
  ok('[sweedesensus] o enchimento só troca a visibilidade das fatias que cruzaram o ponteiro',
    /if \(k !== m\.acesas\) \{/.test(sensus) &&
      /for \(let i = a; i < b; i\+\+\) m\.fatias\[i\]\.style\.visibility = i < k \? 'visible' : 'hidden';/.test(sensus))
  ok('[sweedesensus] a cor das cunhas só é reescrita na troca de tom',
    (limpo.match(/setAttribute\('fill'/g) || []).length === 2 &&
      /function aplicarTom\(\) \{[\s\S]*?setAttribute\('fill'[\s\S]*?setAttribute\('fill'[\s\S]*?\n  \}/.test(limpo))
  ok('[sweedesensus] o aro é anel de cunhas, sem degradê cônico em disco',
    !/conic-gradient/.test(sensus) && /<g class="aro">/.test(sensus))
  ok('[sweedesensus] sem animação no contêiner (#app)',
    !/#app \{[^}]*animation\s*:/.test(sensus))
}

// ── 3d. TEA 3.0 e Cupertino Extended (Alpha 9.5) ───────────────────────────────────────────
// Sem camada composta, como o SENSUS. Os ponteiros (TEA; Cupertino desde a 9.6) são grupos
// pequenos que giram por transform, só quando o ângulo muda. Os arcos são fatias de 2,25° que só
// trocam de visibilidade quando o valor as cruza: mexer no dashoffset do arco inteiro repintaria o
// mostrador inteiro a cada quadro.
for (const t of ['tea30', 'cupertinoextended']) {
  const f = fonte[t] || ''
  if (!f) continue
  const limpo = semComentarios(f)
  ok('[' + t + '] nenhuma camada composta (nenhum will-change nem backdrop-filter)',
    !/will-change\s*:/.test(limpo) && !/backdrop-filter/.test(limpo),
    'camada criada com o WebView ainda sem tamanho fica na escala errada e fora do lugar')
  ok('[' + t + '] o que gira é um transform, escrito só quando muda',
    /if \(mudou\('(pE|bE)', tf\)\) els\.(pontE|botE)\.setAttribute\('transform', tf\);/.test(f) &&
      /if \(mudou\('(pD|bD)', tf\)\) els\.(pontD|botD)\.setAttribute\('transform', tf\);/.test(f))
  ok('[' + t + '] os arcos acendem por fatias, só as que o valor cruzou',
    /if \(k === arco\.acesas\) return;/.test(f) &&
      /for \(let i = a; i < b; i\+\+\) arco\.itens\[i\]\.setAttribute\('visibility', i < k \? 'visible' : 'hidden'\);/.test(f) &&
      !/stroke-dashoffset/.test(limpo.replace(/id="anel(Bat|Comb)"[^>]*>/g, '').replace(/function anel\([^)]*\) \{[^}]*\}/, '')),
    'dashoffset no arco inteiro repinta o mostrador inteiro a cada quadro')
  ok('[' + t + '] sem animação no contêiner (#app)', !/#app \{[^}]*animation\s*:/.test(f))
}

// ── 4. Todo tema junta a telemetria num quadro só ──────────────────────────────────────────
for (const t of temas) {
  const f = fonte[t]
  ok('[' + t + '] a telemetria é desenhada uma vez por quadro',
    /requestAnimationFrame\((flushRender|flush|passo)\)/.test(f),
    'sem coalescência, cada valor que chega desenha o painel inteiro de novo')
  ok('[' + t + '] há rede embaixo do rAF',
    /setTimeout\((flushRender|flush),\s*120\)/.test(f),
    'requestAnimationFrame não dispara com o WebView escondido; sem o temporizador o painel ' +
      'congela quando uma projeção assume a tela')
}

// ── 5. O motor de temas do editor: regras de cor só quando alguma muda ─────────────────────
const motorPath = path.join(raiz, 'instalador', 'src', 'theme-editor', 'runtime', 'engineJs.ts')
// O NTG do motor saiu do APK na 7.6; as regras continuam valendo para a referência dele.
const refNtg = path.join(raiz, 'scripts', 'referencia', 'stuttgart-ntg55', 'app.html')
const stg = fonte['stuttgart-ntg55'] || (fs.existsSync(refNtg) ? fs.readFileSync(refNtg, 'utf8') : '')
const motor = fs.existsSync(motorPath) ? fs.readFileSync(motorPath, 'utf8') : ''
const ler = (...p) => { const f = path.join(raiz, 'instalador', 'src', 'theme-editor', ...p); return fs.existsSync(f) ? fs.readFileSync(f, 'utf8') : '' }
const cssMotor = ler('runtime', 'engineCss.ts')
const rend = ler('runtime', 'renderersJs.ts')
ok('o motor do editor de temas existe', motor.length > 0,
  'o app.html do Stuttgart é gerado a partir dele; consertar só o gerado volta na próxima exportação')

for (const [nome, f] of [['stuttgart-ntg55', stg], ['engineJs.ts', motor]]) {
  if (!f) continue
  ok('[' + nome + '] as regras de cor só são varridas quando alguma chave delas muda',
    /if \(PENDING_REGRAS\) \{ PENDING_REGRAS = false; aplicarRegrasDeCor\(\); \}/.test(f),
    'antes a varredura corria em todo flush — sessenta vezes por segundo só porque a ' +
      'velocidade mudou')
  ok('[' + nome + '] uma chave que alimenta elemento E regra marca a varredura',
    /BUFFER\[key\] = raw;\n(\s*\/\/[^\n]*\n)*\s*if \(REGRA_KEYS\[key\]\) PENDING_REGRAS = true;/.test(f),
    'antes só as chaves SEM elemento marcavam; as outras funcionavam por acidente, porque a ' +
      'varredura era incondicional — gatear sem isto quebraria a cor')
  ok('[' + nome + '] repor o buffer repõe também o estado das regras',
    /PENDING_REGRAS = true;\n\s*if \(Object\.keys\(PENDING\)\.length\) flush\(\); else aplicarRegrasDeCor\(\);/.test(f),
    'a condição de uma regra pode ter mudado enquanto o painel estava fora da tela')
}

// ── 6. A suavização de leitura é filtro de VALOR, e continua opcional ──────────────────────
//
// Nem toda chave chega com a mesma granularidade, e o mesmo degrau pesa diferente em cada
// mostrador. A porcentagem de potência pula de 18 para 30 e fica parada: o ponteiro parece
// travado. A velocidade sobe de 1 em 1 km/h, o que é fino para o NÚMERO e grosso para o
// PONTEIRO, onde um km/h vale 2,08° na escala fechada do Stuttgart. O conserto é um filtro
// sobre o valor, antes de virar ângulo e texto, declarado POR LIGAÇÃO — e cada linha abaixo
// existe porque tirá-la devolve um defeito diferente.
//
// O filtro é criticamente amortecido de SEGUNDA ordem. A primeira ordem que havia antes era mais
// rápida no início, então logo depois de um degrau grande o ponteiro dava um passo grande — no
// mostrador de potência, 2,91° de uma vez, do tamanho do pulinho que se queria tirar.
for (const [nome, f] of [['stuttgart-ntg55', stg], ['engineJs.ts', motor]]) {
  if (!f) continue
  const limpo = semComentarios(f)
  ok('[' + nome + '] a suavização é um filtro sobre o valor, não uma transição de desenho',
    /function passoDeSuavizacao\(\)/.test(limpo) && /inst\.map\(quantizar\(s, s\.atual\)\)/.test(limpo),
    'no CSS ela moveria o ponteiro e deixaria o número digital saltando de 18 para 30 sozinho')
  ok('[' + nome + '] o filtro guarda a taxa de um quadro para o outro',
    /var c = s\.taxa \+ s\.omega \* erro;/.test(limpo) &&
      /s\.taxa = \(c - s\.omega \* erro - s\.omega \* c \* seg\) \* amort;/.test(limpo),
    'sem a taxa o filtro vira primeira ordem de novo: parte a toda a cada leitura nova, e é ' +
      'esse arranque que dava o passo de 2,91° no ponteiro da potência')
  ok('[' + nome + '] só crava no alvo quando também está parado',
    /function paradoNoAlvo\(s\) \{\n\s*return Math\.abs\(s\.alvo - s\.atual\) <= s\.epsilon && Math\.abs\(s\.taxa\) <= s\.omega \* s\.epsilon;/.test(limpo),
    'perto do alvo não basta: um ponteiro passando rápido também fica perto por um quadro, e ' +
      'cravar ali guardaria a taxa velha para a próxima ativação — um empurrão vindo do nada')
  ok('[' + nome + '] smoothMs continua significando "95% de um degrau"',
    /omega: 4\.744 \/ Math\.max\(0\.001, b\.smoothMs \/ 1000\)/.test(limpo),
    '4,744 é onde (1+wt)e^-wt cai a 5%; trocar isso re-afinaria todo tema já exportado. E o piso ' +
      'é 0,001 s e não 1: em segundos, um piso de 1 travaria toda declaração abaixo de um segundo')
  ok('[' + nome + '] o degrau do desenho é declarado, e sem declaração nada muda',
    /return s\.passo > 0 \? Math\.round\(v \/ s\.passo\) \* s\.passo : v;/.test(limpo),
    'o `: v` é o que mantém intacta toda ligação que já suavizava antes de o degrau existir')
  ok('[' + nome + '] com degrau declarado, é ele que decide quando o filtro para',
    /epsilon: passo > 0\n\s*\? passo \/ 2\n\s*:/.test(limpo),
    'com o epsilon antigo o filtro seguiria iterando a 60 Hz abaixo de meio degrau, ' +
      'quando o desenho já não muda mais — laço aberto para nada num painel apertado de CPU')
  ok('[' + nome + '] o primeiro valor entra cravado',
    /if \(s\.atual === null\) \{ s\.atual = n; s\.taxa = 0; return false; \}/.test(limpo),
    'sem isto TODO painel abre com os ponteiros rastejando de zero até a leitura real')
  ok('[' + nome + '] um intervalo longo crava em vez de interpolar',
    /if \(dt > 400\) \{ s\.atual = s\.alvo; s\.taxa = 0; \}/.test(limpo),
    'o rAF para com o WebView oculto; na volta, interpolar mostraria um valor velho caminhando ' +
      'até o atual — e o salto que o filtro evita aconteceria na frente de quem está olhando')
  ok('[' + nome + '] o filtro pede quadro só enquanto anda',
    /if \(SUAVES\.length\) scheduleRender\(\);/.test(limpo),
    'incondicional, ele seguraria um laço de 60 Hz aberto para sempre num painel parado')
  ok('[' + nome + '] a visibilidade não passa pelo filtro',
    /if \(inst\.suave && key === inst\.suave\.chave && mirarSuave\(inst, raw\)\) \{\n\s*applyVisibility\(inst\);/.test(limpo),
    'um elemento que só aparece acima de um limiar tem de aparecer na hora em que o carro disse, ' +
      'não quando o filtro chegar lá')
  ok('[' + nome + '] movimento reduzido desliga a suavização',
    /function fazSuave\(el\) \{\n\s*if \(RM\) return null;/.test(limpo),
    'quem pede menos movimento pede menos movimento')
  ok('[' + nome + '] só suaviza quem declarou',
    /if \(!b \|\| !b\.key \|\| !\(b\.smoothMs > 0\)\) return null;/.test(limpo),
    'ligado por padrão, ele atrasaria também o NÚMERO da velocidade, que é a leitura que não ' +
      'pode atrasar; o filtro do velocímetro está só na ligação do ponteiro')
}

// Os dois consertos de ponteiro deste projeto NÃO se somam, e a regra é essa.
//
// O Bavarian ID7 gira o grupo com uma transição de 90 ms, e ali isso é o certo: o valor dele não
// passa por filtro nenhum, então a transição é a única coisa entre um degrau e o desenho. O
// Stuttgart filtra o VALOR, e o ponteiro já sai andando quadro a quadro; uma transição por cima
// seria um segundo atraso empilhado no primeiro, e o ponteiro passaria a chegar depois do número
// que ele acompanha. Um tema pode ter um ou o outro — nunca os dois.
const cssPath = path.join(raiz, 'instalador', 'src', 'theme-editor', 'runtime', 'engineCss.ts')
const TRANSICAO_DE_PONTEIRO = /\.needle-rot[^{]*\{[^}]*transition:\s*transform/
for (const t of temas) {
  const f = semComentarios(fonte[t])
  if (!/"smoothMs"/.test(f)) continue
  ok('[' + t + '] filtra o valor, então NÃO empilha transição de transform no ponteiro',
    !TRANSICAO_DE_PONTEIRO.test(f),
    'dois atrasos somados: o ponteiro chegaria depois do número que ele acompanha')
}
if (fs.existsSync(cssPath)) {
  ok('[engineCss.ts] o motor não impõe transição de ponteiro a todo tema',
    !TRANSICAO_DE_PONTEIRO.test(semComentarios(fs.readFileSync(cssPath, 'utf8'))),
    'o CSS do motor é o mesmo em todo tema exportado; a transição lá dentro chegaria também nos ' +
      'que filtram o valor, que é justamente onde ela não pode estar')
}

// ── 7. O vidro do mostrador desfoca o FUNDO, e só o fundo ─────────────────────────────────
//
// `backdrop-filter` não tem meio-termo: ou o elemento fica atrás do desenho e desfoca o papel de
// parede, ou fica na frente e desfoca as marcações, o ponteiro e os números — o mostrador inteiro
// sai borrado, sem um erro no console para avisar. Foi exatamente o que aconteceu na primeira
// tentativa desta rodada, e cada verificação aqui é uma peça dessa ordem de pintura.
const CSS_VIDRO = /\.dial-glass \{[^}]*\}/
for (const [nome, f] of [['stuttgart-ntg55', stg], ['engineCss.ts', cssMotor], ['renderersJs.ts', rend]]) {
  if (!f) continue
  const limpo = semComentarios(f)
  if (!/dial-glass/.test(limpo)) continue

  if (/\.dial-glass \{/.test(limpo)) {
    const regra = CSS_VIDRO.exec(limpo)[0]
    ok('[' + nome + '] o vidro pinta ATRÁS do desenho do mostrador',
      /z-index:\s*-1/.test(regra),
      'um filho posicionado pinta depois do conteúdo de fluxo: sem z-index negativo o vidro ' +
        'cobre o <svg> e desfoca as próprias marcações, o ponteiro e os números')
    ok('[' + nome + '] o vidro é redondo',
      /border-radius:\s*50%/.test(regra),
      'quadrado, ele apareceria como um retângulo desfocado nos quatro cantos do mostrador')
    ok('[' + nome + '] a forma prefixada do desfoque vem antes da padrão',
      regra.indexOf('-webkit-backdrop-filter') < regra.indexOf(' backdrop-filter'),
      'num WebView que conhece as duas, a última vence; invertidas, a antiga é que valeria')
    ok('[' + nome + '] o raio do desfoque entra por variável NAS DUAS declarações',
      (regra.match(/blur\(var\(--glass-blur, 0px\)\)/g) || []).length === 2,
      'fixo na regra, o CSS do motor deixaria de ser o mesmo byte a byte em todo tema — e fixo ' +
        'em UMA só das duas, o WebView que entende a prefixada desfocaria com outro raio')
    ok('[' + nome + '] menos transparência desliga o vidro',
      /@media \(prefers-reduced-transparency: reduce\) \{\s*\.dial-glass \{[^}]*backdrop-filter: none/.test(limpo),
      'quem pediu menos transparência no sistema não quer vidro')
  }

  if (/createElement\('div'\)/.test(limpo) && /dial-glass/.test(limpo)) {
    ok('[' + nome + '] o vidro é um <div>, não um <circle>',
      /vidro = document\.createElement\('div'\);\s*vidro\.className = 'dial-glass';/.test(limpo),
      'backdrop-filter é propriedade de caixa CSS: dentro de um SVG o Chromium a ignora em silêncio')
    ok('[' + nome + '] só desfoca quem declarou',
      /if \(el\.face\.blurPx > 0\) \{/.test(limpo),
      'desfocar é a coisa mais cara que um tema pede ao painel; ligado por padrão, todo tema pagaria')
  }
}

// O `app.html` que viaja no APK tem de trazer as duas coisas: a regra e o elemento.
for (const t of temas) {
  const f = semComentarios(fonte[t])
  if (!/"blurPx"/.test(f)) continue
  ok('[' + t + '] declara desfoque e traz a regra que o desenha',
    /\.dial-glass \{/.test(f) && /dial-glass'/.test(f),
    'o payload pedindo vidro sem a regra no <style> dá um <div> transparente e nenhum desfoque')
}

// ── 8. O empurrão de telemetria não escreve no logcat por valor ────────────────────────────
const pontePath = path.join(raiz, 'app', 'src', 'main', 'java', 'br', 'com', 'opengw',
  'bridge', 'ThemeBridgeImpl.kt')
if (fs.existsSync(pontePath)) {
  const ponte = fs.readFileSync(pontePath, 'utf8')
  ok('[ThemeBridgeImpl] o diagnóstico por valor é guardado',
    /if \(DIAG_TELEMETRIA\) \{\n\s*Log\.d\(TAG, "\[DIAG\] pushOnDataChanged/.test(ponte),
    'Log.d NÃO é removido em release: a chamada executa e interpola a string a cada valor ' +
      'de telemetria, no mesmo processo que tenta desenhar 60 quadros')
  ok('[ThemeBridgeImpl] a guarda é lida uma vez, não por chamada',
    /private val DIAG_TELEMETRIA = Log\.isLoggable\(TAG, Log\.DEBUG\)/.test(ponte),
    'Log.isLoggable é chamada de sistema; no caminho mais quente ela seria o próprio custo')
}

console.log('')
console.log(falhas ? `${total} verificações, ${falhas} falharam.` : `${total} verificações, todas passaram.`)
process.exit(falhas ? 1 : 0)
