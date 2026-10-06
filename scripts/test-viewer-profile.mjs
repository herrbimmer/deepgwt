#!/usr/bin/env node
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
/**
 * Trava o perfil de motorista no launcher 3D.
 *
 * Por que é teste de FONTE: o launcher é um APK de terceiro de 1,9 MB de HTML que só roda dentro
 * da WebView da central, com as pontes nativas presentes e com o DeepGWT no ar em 127.0.0.1:8766.
 * Não existe aqui um jeito honesto de executá-lo. O que este arquivo garante é que as mudanças
 * continuem no lugar depois de um reempacotamento ou de uma atualização do launcher — que é
 * justamente quando elas somem sem ninguém ver.
 *
 * Uso: VIEWER_SRC=~/Downloads/haval-h6-viewer-... node scripts/test-viewer-profile.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { homedir } from 'node:os'
import { fileURLToPath } from 'node:url'

// Depois da fusão dos dois apps o `assets/www` do launcher mora DENTRO do repositório
// (app/src/main/assets/www) e é ele que vai para o APK — então o padrão passou a ser esse, e
// estas verificações deixaram de ser opcionais. VIEWER_SRC continua valendo para apontar para
// uma pasta descompactada de fora, e o "PULADO" segue existindo para esse caso.
const VIEWER_SRC = process.env.VIEWER_SRC || resolve(dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main')
const WWW = join(VIEWER_SRC, 'assets', 'www')

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

if (!existsSync(WWW)) {
  console.log('Launcher 3D não encontrado em ' + VIEWER_SRC + ' — nada a verificar.')
  process.exit(0)
}

const index = readFileSync(join(WWW, 'index.html'), 'utf8')
const perfilPath = join(WWW, 'deepGwtProfile.js')
const perfil = existsSync(perfilPath) ? readFileSync(perfilPath, 'utf8') : ''
const cliente = readFileSync(join(WWW, 'deepGwtClient.js'), 'utf8')
const painel = readFileSync(join(WWW, 'deepGwtPanel.js'), 'utf8')
// O CSS do launcher é escrito como concatenação de literais ('a' + 'b'), então uma regra
// atravessa aspas e `+`. Colar os pedaços de volta deixa as regras legíveis por regex.
const cssColado = cliente.replace(/'\s*\+\s*\n?\s*'/g, '')

console.log('\nPerfil de motorista no launcher\n')

ok('deepGwtProfile.js existe', perfil.length > 0)
ok(
  'o index.html carrega o perfil DEPOIS do deepGwtClient.js',
  /<script src="\.\/deepGwtClient\.js"><\/script>\s*\n\s*<script src="\.\/deepGwtProfile\.js"><\/script>/.test(index),
  'antes dele, window.DeepGWT ainda não existe quando este arquivo é avaliado'
)

ok(
  'a pílula do perfil guarda a posição do alto à direita como regra base',
  /#deepgwt-profile\{[^}]*right:calc\(var\(--hv-safe-right, 0px\) \+ \d+px\);top:\d+px/.test(perfil),
  'é o retorno seguro: sem o trilho do deepGwtClient.js, a pílula ainda precisa nascer no canto ' +
    'onde sempre esteve, e não empilhada em 0,0 dentro do #hv-root'
)
ok(
  'a pílula do perfil entra no trilho de atalhos, não no host',
  /trilhoDeAtalhos\(host\)\) \|\| host\)\.appendChild\(b\)/.test(perfil),
  'as três pílulas do DeepGWT ficam numa linha só; o `|| host` é o retorno seguro'
)
ok(
  'as três pílulas não podem se sobrepor: o trilho é uma linha, não três absolutos',
  /#deepgwt-atalhos\{[^}]*display:flex/.test(cssColado) &&
    /#deepgwt-atalhos\{[^}]*right:calc\(var\(--hv-safe-right, 0px\) \+ \d+px\)/.test(cssColado),
  'antes as três eram absolutas dentro do mesmo #hv-root e só um `top:` diferente as separava'
)
ok(
  'o trilho é ancorado pela DIREITA',
  /#deepgwt-atalhos\{[^}]*justify-content:flex-end/.test(cssColado),
  'o painel esportivo entra e sai com o SPORT+; ancorado à direita ele cresce para o lado vazio ' +
    'e não empurra projeção nem perfil debaixo do dedo de quem ia tocá-los'
)
ok(
  'o painel esportivo é o item mais à esquerda',
  /#deepgwt-atalhos > #deepgwt-sportsdisplay\{order:1\}/.test(cliente) &&
    /#deepgwt-atalhos > #deepgwt-projection\{order:2\}/.test(cliente) &&
    /#deepgwt-atalhos > #deepgwt-profile\{order:3\}/.test(cliente),
  'é o único que muda de tamanho em uso; ordem declarada porque duas pílulas são instaladas por ' +
    'um arquivo e a terceira por outro, e quem chega primeiro depende do whenMounted de cada um'
)
ok(
  'a posição dentro do trilho vence por especificidade, não por ordem de injeção',
  /#deepgwt-atalhos > #deepgwt-profile,#deepgwt-atalhos > #deepgwt-projection,/.test(cliente),
  'com um id só de cada lado, qual regra vence dependeria de qual <style> entrou primeiro — e ' +
    'isso é uma corrida entre dois whenMounted'
)
ok(
  'o teto do trilho é a largura útil da tela, não uma fração dela',
  /#deepgwt-atalhos\{[^}]*max-width:calc\(100% - var\(--hv-safe-left, 0px\) - var\(--hv-safe-right, 0px\) - \d+px\)/.test(cssColado),
  'no navegador --hv-safe-right é vazio e um teto de 50% parecia folgado; no carro o launcher ' +
    'define a área segura em tempo de execução e o teto apertava. Como a pílula do perfil é a ' +
    'única que encolhe, ela comia a folga inteira e o nome de quem dirige sumia'
)
ok(
  'só o nome do perfil encolhe, e ele encolhe com reticências',
  /#deepgwt-atalhos > #deepgwt-profile\{flex:0 1 auto;min-width:0;\}/.test(cssColado) &&
    /#deepgwt-atalhos > \*\{pointer-events:auto;flex:0 0 auto;\}/.test(cssColado) &&
    /#deepgwt-profile \.nome\{overflow:hidden;text-overflow:ellipsis/.test(perfil),
  'um nome de perfil muito longo tem de virar reticências, nunca empurrar as outras duas ' +
    'pílulas para fora da tela'
)
ok(
  'o trilho não rouba o toque do cenário 3D',
  /#deepgwt-atalhos\{[^}]*pointer-events:none/.test(cssColado) &&
    /#deepgwt-atalhos > \*\{pointer-events:auto/.test(cssColado),
  'a faixa ocupa metade da largura da tela; sem isso, os vãos entre as pílulas comeriam o ' +
    'arrasto que gira o carro'
)
ok(
  'a pilha diz de quem é o carro, e diz quando não sabe',
  /SEM PERFIL/.test(perfil) && /estado\.ativo/.test(perfil)
)

ok(
  'a janela de escolha reaproveita a modal do painel DeepGWT',
  /deepgwt-modal-card/.test(perfil) && /deepgwt-choice/.test(perfil) && /deepgwt-grid/.test(perfil),
  'sem isso a lista de perfis seria a única janela do launcher com aparência própria'
)
ok(
  'o perfil ativo aparece marcado na lista',
  /perfil\.name === estado\.ativo \? ' is-on' : ''/.test(perfil)
)

ok(
  'trocar de perfil passa pelo DeepGWT, não pelo launcher',
  /post\('\/profiles\/select'/.test(perfil),
  'as pontes nativas deste APK não leem nem escrevem preferência nenhuma'
)
ok(
  'a troca espera mais do que os 4 s padrão',
  /ESPERA_DA_TROCA_MS\s*=\s*(\d+)/.test(perfil) &&
    Number(perfil.match(/ESPERA_DA_TROCA_MS\s*=\s*(\d+)/)[1]) >= 20000 &&
    /post\('\/profiles\/select',\s*\{ name: nome \},\s*ESPERA_DA_TROCA_MS\)/.test(perfil),
  'desistir no meio não cancela a aplicação — só faz a tela mentir'
)
ok(
  'o post do launcher aceita um tempo de espera por chamada',
  /function post\(path, body, ms\)/.test(cliente) && /\}, ms\)\.then/.test(cliente)
)
ok(
  'nenhum segundo perfil pode ser aplicado por cima do primeiro',
  /lista\.children, function \(c\) \{ c\.disabled = true; \}/.test(perfil),
  'duas aplicações ao mesmo tempo escreveriam as duas listas uma sobre a outra'
)

ok(
  'o que o carro recusou aparece na tela',
  /relatorio\.detalhes/.test(perfil) && /mostrarRelatorio/.test(perfil),
  'sem isto, quem troca pelo launcher vê o tema mudar e conclui que o resto foi junto'
)
ok(
  'sucesso e pendência são visualmente diferentes',
  /deepgwt-perfil-ok/.test(perfil) && /deepgwt-perfil-erro/.test(perfil)
)
ok(
  'uma troca que falhou no meio do caminho relê o estado real',
  /catch\(function \(e\) \{[\s\S]{0,800}conferir\(\)/.test(perfil),
  'a aplicação pode ter acontecido pela metade antes de a resposta se perder'
)

ok(
  'nome de perfil é escapado antes de virar HTML',
  !/innerHTML[\s\S]{0,80}\+ perfil\.name/.test(perfil) &&
    /escapar\(perfil\.name\)/.test(perfil) &&
    /escapar\(nome\)/.test(perfil),
  'o nome é escrito por quem usa o carro e entra por innerHTML'
)

ok(
  'a conferência periódica para com a tela escondida',
  /document\.hidden/.test(perfil),
  'conferir com o launcher atrás de outro app é gasto sem leitor'
)
ok(
  'a conferência não é por quadro',
  /INTERVALO_DE_CONFERENCIA_MS\s*=\s*(\d+)/.test(perfil) &&
    Number(perfil.match(/INTERVALO_DE_CONFERENCIA_MS\s*=\s*(\d+)/)[1]) >= 30000
)

ok(
  'o painel DeepGWT tem a entrada de perfil',
  /PERFIL DE MOTORISTA/.test(painel) && /window\.DeepGwtProfile/.test(painel)
)
ok(
  'o painel mostra quem está dirigindo sem precisar abrir a lista',
  /Dirigindo: ' \+ perfilAtivo/.test(painel)
)

console.log('')
if (falhas > 0) {
  console.log(`${total} verificações, ${falhas} falharam.`)
  process.exit(1)
}
console.log(`${total} verificações, todas passaram.`)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
