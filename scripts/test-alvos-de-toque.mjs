// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Piso de área tocável (44dp/44px) nos controles que o motorista usa com o carro andando.
//
// Roda com `node scripts/test-alvos-de-toque.mjs`. A parte do launcher usa VIEWER_SRC, igual
// aos outros test-viewer-*; sem a pasta, essa metade se declara pulada em vez de falhar.
//
// POR QUE ESTE TESTE EXISTE: os alvos pequenos aqui não foram escritos pequenos de propósito —
// eles nasceram do tamanho do DESENHO (um círculo de 30dp fica bonito numa barra de 60dp) e
// ninguém separou o tamanho do desenho do tamanho da área que responde ao dedo. O erro é fácil
// de refazer: basta a próxima pessoa escrever `Modifier.size(30.dp).clickable{}` de novo, que é
// a forma mais curta e mais natural de escrever um botão redondo no Compose. Um teste que lê o
// fonte é o que impede isso de voltar sem ninguém perceber — medir no emulador não pega, porque
// o botão continua funcionando, só fica difícil de acertar.
//
// O que ele NÃO faz: varrer o app inteiro atrás de alvos pequenos. Uma varredura assim dá dezenas
// de falsos positivos (todo Icon de 16dp dentro de um pai grande) e uma lista de exceções que
// ninguém mantém. Ele cobre os controles que foram corrigidos, um a um, pelo nome.

import fs from 'node:fs'
import path from 'node:path'
import os from 'node:os'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
// Kit de interface e barra inferior moram em br/com/deepgwt; hoje
// são br.com.deepgwt.ui e br.com.deepgwt.barra.
const ui = path.join(raiz, 'app/src/main/java/br/com/deepgwt/ui')
const barraDir = path.join(raiz, 'app/src/main/java/br/com/deepgwt/barra')

let falhas = 0
const eq = (nome, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
  else console.log(`ok  ${nome}`)
}

const ler = (rel) => fs.readFileSync(path.join(ui, rel), 'utf8')
const lerBarra = (rel) => fs.readFileSync(path.join(barraDir, rel), 'utf8')

/** Sem comentários: os exemplos de uso na KDoc contêm justamente o código que o teste proíbe. */
const semComentarios = (src) => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')

// ── O token ────────────────────────────────────────────────────────────────────────────────

const token = ler('TouchTarget.kt')
eq('o piso é 44dp', /val Minimum: Dp = 44\.dp/.test(token), true)
// defaultMinSize e não size: um piso que não vira teto. Um botão que já é maior continua maior.
eq('touchTarget usa defaultMinSize (piso, não tamanho fixo)', /defaultMinSize\(/.test(token), true)
eq('touchTarget não fixa tamanho', /\.size\(/.test(semComentarios(token)), false)

// ── Barra inferior (3.7, sala limpa) ───────────────────────────────────────────────────────
// Os alvos da barra nova são cobertos pelos testes da sala limpa e pela especificação
// (barra-inferior-interface.md: alvo mínimo 44 dp). Aqui fica só o tamanho do cartão de volume,
// que depende da geometria do pop-up do carro.
{
  const geo = lerBarra('JanelaDoVolumeNoTopo.kt')
  const alturaOriginal = Number(/ALTURA_DO_ORIGINAL_DP = (\d+)/.exec(geo)?.[1])
  const folga = Number(/FOLGA_DP = (\d+)/.exec(geo)?.[1])
  eq('o cartão de volume comporta 44dp', alturaOriginal + 2 * folga >= 44, true)
}

// ── Sombra de ambiente nas superfícies que flutuam ─────────────────────────────────────────
const vidro = ler('LiquidGlass.kt')
for (const tier of ['Chrome', 'Popover', 'Transient']) {
  const m = vidro.match(new RegExp(`${tier}\\(fill[\\s\\S]*?elevation = (\\d+)\\.dp\\)`))
  eq(`GlassTier.${tier} tem sombra`, m ? Number(m[1]) > 0 : false, true)
}
// Superfície maior lê como mais grossa: a sombra acompanha o tamanho, não é um valor só.
const elev = (t) => Number(vidro.match(new RegExp(`${t}\\(fill[\\s\\S]*?elevation = (\\d+)\\.dp\\)`))[1])
eq('sombra cresce com o tamanho da superfície', elev('Chrome') > elev('Popover') && elev('Popover') > elev('Transient'), true)
// O padrão continua 0: cartão empilhado dentro de lista que rola não leva sombra.
eq('liquidGlass() sem tier continua sem sombra', /elevation: Dp = 0\.dp/.test(vidro), true)

// ── Launcher 3D ────────────────────────────────────────────────────────────────────────────
// Depois da fusão dos dois apps o `assets/www` do launcher mora DENTRO do repositório
// (app/src/main/assets/www) e é ele que vai para o APK — então o padrão passou a ser esse, e
// estas verificações deixaram de ser opcionais. VIEWER_SRC continua valendo para apontar para
// uma pasta descompactada de fora, e o "PULADO" segue existindo para esse caso.
const viewerSrc = process.env.VIEWER_SRC || path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', 'app', 'src', 'main')
const clientPath = path.join(viewerSrc, 'assets', 'www', 'deepGwtClient.js')
if (!fs.existsSync(clientPath)) {
  console.log(`PULADO (launcher): não encontrado em ${viewerSrc}`)
} else {
  const js = fs.readFileSync(clientPath, 'utf8')
  // O atalho de projeção deixou de ser posicionado por conta própria: agora ele é um item do
  // trilho de atalhos (#deepgwt-atalhos), e o que sobrou no bloco dele é só a aparência.
  const bloco = js.slice(js.indexOf("'#deepgwt-projection{position:relative"), js.indexOf("document.head.appendChild(css);\n\n    var btn"))
  eq('pílula de projeção: 44px de altura mínima', /min-height:44px/.test(bloco), true)
  // Lado a lado no trilho, as TRÊS pílulas precisam do mesmo piso de toque: uma mais baixa que
  // as outras não é só feia, é um alvo menor que o mínimo bem ao lado de dois que passam.
  const esportivo = js.slice(js.indexOf("'#deepgwt-sportsdisplay{position:relative"), js.indexOf("'#deepgwt-sportsdisplay.is-on{"))
  eq('pílula do painel esportivo: 44px de altura mínima', /min-height:44px/.test(esportivo), true)
  eq('pílula do painel esportivo: o mínimo inclui padding e borda', /box-sizing:border-box/.test(esportivo), true)
  const perfilJs = fs.readFileSync(path.join(viewerSrc, 'assets', 'www', 'deepGwtProfile.js'), 'utf8')
  const pilulaPerfil = perfilJs.slice(perfilJs.indexOf("'#deepgwt-profile{position:absolute"), perfilJs.indexOf("'#deepgwt-profile:active"))
  eq('pílula de perfil: 44px de altura mínima', /min-height:44px/.test(pilulaPerfil), true)
  eq('pílula de perfil: o mínimo inclui padding e borda', /box-sizing:border-box/.test(pilulaPerfil), true)
  // Sem box-sizing o mínimo passaria a valer só para o conteúdo, e a pílula ficaria com 44+20.
  eq('pílula: o mínimo inclui padding e borda', /box-sizing:border-box/.test(bloco), true)
  eq('pílula: vidro da skill (blur + saturate)', /backdrop-filter:blur\(20px\) saturate\(180%\)/.test(bloco), true)
  // Duas camadas de sombra, não uma.
  const sombra = bloco.match(/box-shadow:([^']*)'/)
  eq('pílula: sombra em duas camadas', sombra ? sombra[1].split('),').length >= 2 : false, true)
  // As setas de página já passavam do piso antes desta rodada; o teste fixa isso.
  const setas = js.slice(js.indexOf("'#deepgwt-page-nav .seta{position:absolute"), js.indexOf("'#deepgwt-page-nav .seta:active"))
  const w = Number(setas.match(/width:(\d+)px/)[1])
  const h = Number(setas.match(/height:(\d+)px/)[1])
  eq('setas de página passam do piso', w >= 44 && h >= 44, true)
}

console.log(falhas ? `\n${falhas} FALHA(S)` : '\nTodas passaram')
process.exit(falhas ? 1 : 0)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
