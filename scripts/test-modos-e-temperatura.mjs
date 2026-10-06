// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A rodada que mexeu na barra inferior: temperatura unificada, Painel Esportivo fora da barra e
// modo de condução por pressão longa no SPORT+.
//
// Roda com `node scripts/test-modos-e-temperatura.mjs`.
//
// Desde a troca 3.7 a barra é a de br.com.deepgwt.barra (sala limpa). As guardas de TEXTO sobre o
// arquivo antigo da barra (coluna de modos na janela de menus, guarda do arrasto, `-1` inicial)
// saíram com ele: esse comportamento está nos testes JVM de app/src/test/java/br/com/deepgwt/barra/
// (RegrasDaInterfaceTest, ContasDaBarraTest) e na especificação barra-inferior-interface.md. Aqui
// fica o que mora FORA da barra (Configurações, SPORT+, launcher 3D, servidor, projeção) e o formato
// gravado da temperatura.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const java = path.join(raiz, 'app/src/main/java/br/com/deepgwt')

let falhas = 0
const eq = (nome, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
  else console.log(`ok  ${nome}`)
}

const ler = (rel) => fs.readFileSync(path.join(java, rel), 'utf8')
const lerRaiz = (rel) => fs.readFileSync(path.join(raiz, rel), 'utf8')
/** Sem comentários: a KDoc descreve justamente as formas que este teste proíbe. */
const semComentarios = (src) => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '')

/** Corpo de uma função por contagem de chaves, a partir da assinatura. */
function corpo(src, assinatura) {
  const i = src.indexOf(assinatura)
  if (i < 0) return null
  const abre = src.indexOf('{', i + assinatura.length)
  if (abre < 0) return null
  let nivel = 0
  for (let j = abre; j < src.length; j++) {
    if (src[j] === '{') nivel++
    else if (src[j] === '}') { nivel--; if (nivel === 0) return src.slice(abre, j + 1) }
  }
  return null
}

const lerBarra = (arquivo) => lerRaiz(path.join('app/src/main/java/br/com/deepgwt/barra', arquivo))
const tipos = lerBarra('TiposDaBarra.kt')
const contas = semComentarios(lerBarra('ContasDaBarra.kt'))
const preferenciasDaBarra = semComentarios(lerBarra('PreferenciasDaBarra.kt'))
const gaveta = lerBarra('GavetaDeAtalhos.kt')
const barraVisivel = semComentarios(lerBarra('BarraVisivel.kt'))
const ajustes = ler('ui/screens/AbaDeConfiguracoes.kt')
const sportPlus = ler('managers/SportPlusController.kt')
const gwlink = ler('managers/GwLinkServer.kt')
const projecao = ler('projection/ScreenProjectionController.kt')
const launcherJs = lerRaiz('app/src/main/assets/www/deepGwtClient.js')

// ══ 1. Temperatura: uma escolha, não dois interruptores ═══════════════════════════════════

eq('existe uma escolha única de controle de temperatura',
   /enum class ControleDeTemperatura\(val valorGravado: String\)/.test(tipos), true)
eq('com os três estados possíveis (texto gravado nos carros)',
   /DESLIGADO\("off"\)[\s\S]*?UMA_ZONA\("single"\)[\s\S]*?DUAS_ZONAS\("dual"\)/.test(tipos), true)
// Migração: quem já tinha a barra montada não pode encontrá-la remontada.
eq('quem já tinha a barra montada é migrado',
   /fun controleDeTemperatura\(gravado: String\?, mostrarAr: Boolean, duasZonas: Boolean\)/.test(contas), true)
eq('e a barra aplica a migração ao ler as preferências',
   /ContasDaBarra\.controleDeTemperatura\(/.test(preferenciasDaBarra), true)
eq('a tela de ajustes lê com a mesma regra',
   /ContasDaBarra\.controleDeTemperatura\(/.test(ajustes), true)
eq('a tela de ajustes mostra uma linha só, com o nome pedido',
   (ajustes.match(/"Controle de temperatura"/g) || []).length, 1)
eq('e não os dois rótulos antigos',
   /"Controle de A\.C"/.test(ajustes) || /"Controle de A\.C \(dual zone\)"/.test(ajustes), false)

// ══ 2. O Painel Esportivo saiu da barra ═══════════════════════════════════════════════════

eq('a barra não monta a fatia do Painel Esportivo', /Painel Esportivo/.test(barraVisivel), false)
// Ele continua alcançável pela GAVETA que sobe com o arrasto — senão o atalho some sem ninguém notar.
eq('e ele continua alcançável pela gaveta do arrasto', /"Painel Esportivo"/.test(gaveta), true)
eq('e o interruptor dele saiu de Configurações',
   /"Botão do Painel Esportivo"/.test(ajustes), false)
// Ele virou atalho do launcher 3D, alimentado por uma chave EMPURRADA.
eq('o SPORT+ publica o próprio estado', /const val KEY_ACTIVE = "haval\.sportplus\.active"/.test(sportPlus), true)
eq('ao ligar e ao desligar',
   (sportPlus.match(/publicarEstado\(\)/g) || []).length >= 3, true)
eq('o launcher tem o atalho', /function installSportsDisplayShortcut/.test(launcherJs), true)
eq('e ele é instalado no boot do launcher', /installSportsDisplayShortcut\(host\)/.test(launcherJs), true)
const atalho = corpo(launcherJs, 'function installSportsDisplayShortcut')
// Um laço de perguntas numa tela que desenha um carro em 3D custaria mais que o atalho vale.
eq('o atalho NÃO fica perguntando ao carro', /setInterval|setTimeout/.test(atalho), false)
eq('ele ouve a chave empurrada', /carTelemetry\.onUpdate/.test(atalho), true)
eq('e pergunta uma única vez, na instalação',
   (atalho.match(/DeepGWT\.get\(/g) || []).length, 1)
eq('a rota que abre a tela existe', /request\.path == "\/quick\/sportsdisplay"/.test(gwlink), true)
eq('e o estado inicial vem junto do resto', /\.put\("sportPlusActive", SportPlusController\.isActive\)/.test(gwlink), true)

// ══ 3. O botão de projeção volta a ser clicável ══════════════════════════════════════════

// Era `@Volatile private var janela`: leitura de campo comum não é assinatura, o Compose não
// recompunha quando o projetor subia e o botão ficava apagado para sempre.
eq('a janela do painel é estado observável',
   /private val janelaState = mutableStateOf<JanelaDoPainel\?>\(null\)/.test(projecao), true)
eq('e não voltou a ser um campo @Volatile', /@Volatile private var janela\b/.test(projecao), false)
eq('painelPronto continua sendo o que o botão lê', /val painelPronto: Boolean/.test(projecao), true)

console.log(falhas === 0 ? '\nTODOS OS TESTES PASSARAM' : `\n${falhas} FALHA(S)`)
process.exit(falhas === 0 ? 0 : 1)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
