#!/usr/bin/env node
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
/**
 * A faixa nativa de ícones não aparece nas duas telas que pedem a central limpa.
 *
 * QUAL ERA O PROBLEMA
 * A faixa de ícones do launcher é uma View desenhada FORA da WebView. Nenhum z-index de dentro da
 * página alcança ela, e enquanto o launcher era um APK separado o dex não expunha método nenhum
 * para escondê-la — os dois arquivos da página chegaram a documentar isso como teto conhecido. O
 * resultado: em CONFIGURAÇÕES DeepGWT e no Modo Descanso (uma tela preta com um relógio) a
 * fileira de ícones de app continuava acesa por cima.
 *
 * O QUE MUDOU
 * Com os dois apps fundidos num APK só, o código nativo é o mesmo projeto: a ponte ganhou
 * `AppLauncherBridge.setLauncherHidden(bool)` e as duas telas passaram a pedir. Desde a tela 3D
 * nova (`br.com.deepgwt.tela3d`) o lado nativo está em Kotlin: ponte (`PonteDoLancador`), Activity
 * (`TelaInicial3dActivity`), adaptador da faixa (`integracao/AdaptadoresDaFaixa.kt`) e a faixa em si
 * (`FaixaDeBaixoImpl`, `RegrasDaFaixa`).
 *
 * POR QUE O PEDIDO PASSA POR UM ÁRBITRO
 * As duas telas se sobrepõem — dá para entrar no Modo Descanso estando na página de
 * configurações. Se cada uma mandasse `setLauncherHidden(false)` ao sair, a primeira a sair
 * devolveria os ícones por cima da outra, que ainda quer a tela limpa. Por isso ninguém fala com a
 * ponte direto: `window.requestLauncherHidden(dono, quer)` (appLauncherClient.js) conta pedidos e
 * só devolve a faixa quando sai o último. Esse detalhe é o que este arquivo mais protege, porque é
 * o que quebra em silêncio e só aparece numa sequência específica de telas.
 *
 * Teste de FONTE, como os outros do launcher: o HTML de 1,9 MB só roda dentro da WebView da
 * central com as pontes nativas presentes, e não há aqui um jeito honesto de executá-lo. O
 * comportamento foi conferido ao vivo no emulador; o que este arquivo garante é que não volte.
 *
 * Uso: node scripts/test-faixa-de-icones.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createContext, runInContext } from 'node:vm'

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const WWW = join(RAIZ, 'app', 'src', 'main', 'assets', 'www')
const TELA3D = join(RAIZ, 'app', 'src', 'main', 'java', 'br', 'com', 'deepgwt', 'tela3d')

let falhas = 0
let total = 0

function ok(descricao, condicao, detalhe) {
  total++
  if (condicao) {
    console.log('ok  ' + descricao)
  } else {
    falhas++
    console.log('FALHOU  ' + descricao + (detalhe ? '\n        ' + detalhe : ''))
  }
}

function ler(caminho) {
  return existsSync(caminho) ? readFileSync(caminho, 'utf8') : ''
}

/**
 * Tira comentários antes de procurar CHAMADA de método.
 *
 * Os dois arquivos falam de `AppLauncherBridge.setLauncherHidden` em prosa, para explicar por que
 * o pedido passa pelo árbitro. Sem esta limpeza a explicação derrubaria a verificação que existe
 * justamente para garantir que ninguém chame a ponte direto — o teste passaria a punir quem
 * documenta.
 */
function semComentarios(fonte) {
  return fonte.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/^[ \t]*\/\/.*$/gm, ' ')
}

const painelCodigo = () => semComentarios(painel)
const descansoCodigo = () => semComentarios(descanso)

const ponte = semComentarios(ler(join(TELA3D, 'ponte', 'PonteDoLancador.kt')))
const atividade = semComentarios(ler(join(TELA3D, 'ponte', 'TelaInicial3dActivity.kt')))
const adaptador = semComentarios(ler(join(TELA3D, 'integracao', 'AdaptadoresDaFaixa.kt')))
const faixa = semComentarios(ler(join(TELA3D, 'faixa', 'FaixaDeBaixoImpl.kt')))
const regras = semComentarios(ler(join(TELA3D, 'faixa', 'RegrasDaFaixa.kt')))
const cliente = ler(join(WWW, 'appLauncherClient.js'))
const painel = ler(join(WWW, 'deepGwtPanel.js'))
const descanso = ler(join(WWW, 'deepGwtRest.js'))

if (!ponte || !atividade || !adaptador || !faixa || !regras || !cliente || !painel || !descanso) {
  console.log('Fontes da tela 3D não encontradas (br.com.deepgwt.tela3d, assets/www)')
  process.exit(1)
}

/** O corpo de uma função Kotlin: do `fun nome(` até a chave que fecha (conta chaves). */
function corpoDe(fonte, assinatura) {
  const i = fonte.indexOf(assinatura)
  if (i < 0) return ''
  let j = fonte.indexOf('{', i)
  const igual = fonte.indexOf('=', i)
  // função de expressão (`= ...`): o corpo é a linha
  if (j < 0 || (igual >= 0 && igual < j)) return fonte.slice(i, fonte.indexOf('\n', i))
  let nivel = 0
  for (let k = j; k < fonte.length; k++) {
    if (fonte[k] === '{') nivel++
    else if (fonte[k] === '}' && --nivel === 0) return fonte.slice(i, k + 1)
  }
  return fonte.slice(i)
}

console.log('\nLado nativo (ponte, Activity, adaptador e faixa em Kotlin)\n')

ok(
  'a ponte expõe setLauncherHidden ao JavaScript',
  /@JavascriptInterface\s*\n\s*fun setLauncherHidden\(escondida: Boolean\)/.test(ponte),
  'sem @JavascriptInterface o método existe no dex e mesmo assim não é chamável da página'
)
ok(
  'o pedido é entregue na thread principal',
  /fun setLauncherHidden\(escondida: Boolean\) =\s*naUi\("setLauncherHidden"\) \{ acoes\.definirFaixaEscondidaPelaPagina\(escondida\) \}/.test(ponte),
  'a ponte roda na thread do JavaScript; mexer em View a partir dela é crash na hora certa errada'
)
ok(
  'a Activity repassa o pedido à faixa',
  /override fun definirFaixaEscondidaPelaPagina\(escondida: Boolean\) \{[\s\S]{0,300}dependencias\.faixa\.definirEscondidaPelaPagina\(escondida\)/.test(atividade),
  'o intermediário entre a ponte e a faixa; sem ele o pedido some'
)
ok(
  'o adaptador entrega o pedido à faixa',
  /override fun definirEscondidaPelaPagina\(escondida: Boolean\) = faixa\.pedirEscondida\(escondida\)/.test(adaptador)
)
ok(
  'o pedido entra na condição de visibilidade da faixa',
  /escondidaPelaPagina -> EstadoDaFaixa\.GONE/.test(regras) &&
    /RegrasDaFaixa\.estadoDaFaixa\(revelada, escondidaPelaPagina, modoDaCasca\)/.test(faixa),
  'RegrasDaFaixa.estadoDaFaixa é o único ponto que decide a faixa — fora dele o pedido é ignorado'
)
ok(
  'aplicar o pedido repinta a faixa',
  /override fun pedirEscondida\(escondida: Boolean\) \{[\s\S]{0,300}aplicarVisibilidade\(\)/.test(faixa)
)
ok(
  'nada devolve a faixa sozinha depois de um tempo',
  !/postDelayed|postAtTime|Timer/.test(corpoDe(faixa, 'override fun pedirEscondida(')) &&
    !/postDelayed/.test(corpoDe(atividade, 'override fun definirFaixaEscondidaPelaPagina(')),
  'o Modo Descanso fica ligado por horas; um teto de tempo acenderia os ícones no meio da noite'
)
ok(
  'recarregar a página zera o pedido',
  /override fun aoTerminarDeCarregar\(url: String\?\) \{[\s\S]{0,500}faixaEscondidaPelaPagina = false[\s\S]{0,120}definirEscondidaPelaPagina\(false\)/.test(atividade),
  'é a única saída se a página pedir escondido e morrer antes de devolver'
)

console.log('\nÁrbitro (appLauncherClient.js)\n')

ok(
  'window.requestLauncherHidden existe',
  /window\.requestLauncherHidden = function\(owner, wanted\)/.test(cliente)
)
ok(
  'um pedido sai da lista pelo nome de quem pediu',
  /if \(wanted\) hideRequests\[owner\] = true;\s*\n\s*else delete hideRequests\[owner\];/.test(cliente),
  'sem nome, quem sai apaga o pedido de quem ficou'
)
ok(
  'a faixa só volta quando não sobra pedido nenhum',
  /var hidden = false;[\s\S]{0,320}\{ hidden = true; break; \}/.test(cliente)
)
ok(
  'não repete o mesmo estado para o nativo',
  /if \(hidden === lastHiddenSent\) return hidden;/.test(cliente)
)
ok(
  'sem o método nativo, responde que NÃO escondeu',
  /typeof bridge\.setLauncherHidden !== 'function'\) return false;/.test(cliente),
  'um "sim" falso faria uma tela futura desenhar contando com um espaço que os ícones ocupam'
)

/**
 * O árbitro é a única parte disto que dá para EXECUTAR aqui: ele não toca no DOM nem no 3D, só
 * conta pedidos e fala com a ponte. Então em vez de conferir o texto dele, esta seção roda o
 * arquivo num contexto com uma ponte de mentira e refaz a sequência que quebrava antes — entrar
 * no Modo Descanso já estando na página de configurações e sair dele primeiro.
 */
function montarArbitro(comPonte) {
  const chamadas = []
  const ctx = { console }
  ctx.window = ctx
  ctx.document = { getElementById: () => null, documentElement: null }
  ctx.AppLauncherBridge = comPonte
    ? { setLauncherHidden: (v) => { chamadas.push(v) } }
    : {}
  createContext(ctx)
  runInContext(cliente, ctx)
  return { pedir: ctx.requestLauncherHidden, chamadas }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

console.log('\nComportamento do árbitro (executado)\n')

{
  const { pedir, chamadas } = montarArbitro(true)

  // O retorno E o que chegou na ponte, passo a passo: `chamadas` é o mesmo array o tempo todo, e
  // conferir só no fim esconderia exatamente a chamada a mais que se quer impedir.
  const passo = (dono, quer) => {
    const resposta = pedir(dono, quer)
    return { resposta, enviado: chamadas.slice() }
  }

  const p1 = passo('painel', true)
  const p2 = passo('rest', true)
  const p3 = passo('rest', false)
  const p4 = passo('painel', false)

  ok(
    'o primeiro pedido esconde a faixa',
    p1.resposta === true && JSON.stringify(p1.enviado) === '[true]',
    'enviado: ' + JSON.stringify(p1.enviado)
  )
  ok(
    'o segundo pedido não repete a chamada ao nativo',
    p2.resposta === true && JSON.stringify(p2.enviado) === '[true]',
    'enviado: ' + JSON.stringify(p2.enviado)
  )
  ok(
    'sair do Modo Descanso NÃO acende os ícones sobre a página de configurações',
    p3.resposta === true && JSON.stringify(p3.enviado) === '[true]',
    'este é o defeito que o árbitro existe para impedir — enviado: ' + JSON.stringify(p3.enviado)
  )
  ok(
    'a faixa só volta quando o último pedido sai',
    p4.resposta === false && JSON.stringify(p4.enviado) === '[true,false]',
    'enviado: ' + JSON.stringify(p4.enviado)
  )
}

{
  const { pedir, chamadas } = montarArbitro(false)
  ok(
    'sem o método na ponte, responde false e não estoura',
    pedir('painel', true) === false && chamadas.length === 0
  )
}

console.log('\nCONFIGURAÇÕES DeepGWT (deepGwtPanel.js)\n')

ok(
  'a página pede a faixa escondida ao entrar e devolve ao sair',
  /window\.requestLauncherHidden\('painel', on\)/.test(painel),
  'o mesmo `on` que liga o painel liga o pedido — não há caminho de saída sem devolver'
)
ok(
  'não fala com a ponte por fora do árbitro',
  !/\.setLauncherHidden\s*\(/.test(painelCodigo()),
  'o pedido tem de passar pelo árbitro, senão sair de uma tela acende os ícones sobre a outra'
)
ok(
  'o cartão NÃO cresce para o lugar que a faixa deixou',
  !/is-clear/.test(painel) &&
    /bottom:max\(var\(--hv-launcher-bottom, 0px\), var\(--hv-safe-bottom, 0px\)\)/.test(painel),
  'o espaço embaixo do cartão é por onde se arrasta para sair desta página: medido no emulador, ' +
    'descer o cartão até a barra do DeepGWT deixa ~30px de canvas no lugar de ~180px, e as ' +
    'bordas de arrasto laterais não completam a troca sozinhas. Esta tela já prendeu alguém uma vez.'
)
ok(
  'o comentário que dizia que os ícones ficam foi retirado',
  !painel.includes('que não existe no dex') && !painel.includes('Então os ícones ficam'),
  'documentação velha manda a próxima pessoa desistir de um problema que já está resolvido'
)

console.log('\nModo Descanso (deepGwtRest.js)\n')

ok(
  'pede a faixa escondida ao entrar',
  /function raiseChrome\(\)[\s\S]{0,600}window\.requestLauncherHidden\('rest', true\)/.test(descanso)
)
ok(
  'devolve ao sair',
  /function restoreChrome\(\)[\s\S]{0,400}window\.requestLauncherHidden\('rest', false\)/.test(descanso)
)
ok(
  'não fala com a ponte por fora do árbitro',
  !/\.setLauncherHidden\s*\(/.test(descansoCodigo()),
  'o pedido tem de passar pelo árbitro, senão sair de uma tela acende os ícones sobre a outra'
)
ok(
  'continua levantando a WebView sobre o resto do chrome',
  /setChromeOnTop\(true\)/.test(descanso),
  'o setChromeOnTop faz outra coisa: mantém o toque de saída chegando na camada preta'
)
ok(
  'o comentário que chamava isso de limitação do launcher foi retirado',
  !descanso.includes('é limitação do launcher nativo'),
  'deixou de ser limitação quando o dex passou a ser deste APK'
)

console.log('')
if (falhas > 0) {
  console.log(`${total} verificações, ${falhas} falharam.`)
  process.exit(1)
}
console.log(`${total} verificações, todas passaram.`)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
