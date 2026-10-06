#!/usr/bin/env node
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
/**
 * O raio-X do conjunto (ícone de capô) recebe dados de verdade.
 *
 * QUAL ERA O PROBLEMA
 * O launcher lê UMA chave para saber para onde a potência está indo: `haval.power.flow`. Está
 * escrito no `CAR_SIGNALS` dele, com autoria e tudo:
 *
 *     // Derived by the power-flow mapper. Packed v1|state|ice|front|rear.
 *
 * Esse mapeador de fluxo nunca existiu neste repositório. O launcher foi escrito contra um
 * produtor que ninguém escreveu, então no carro o raio-X abria, o modelo aparecia, e nada dentro
 * dele se mexia: sem setas de fluxo, sem saber se o motor a combustão estava girando, sem tração
 * marcada em roda nenhuma. Sem a chave o launcher cai em `CAR_POWER_IDLE` — o retrato de "parado,
 * nada acontecendo" — e desenha isso para sempre.
 *
 * O QUE ESTE ARQUIVO PROTEGE
 * O formato NÃO é escolha nossa: o consumidor recusa qualquer coisa fora do padrão devolvendo
 * `null`, que vira "Fluxo indisponível" na tela — em silêncio, sem erro em log nenhum. Um espaço a
 * mais, um estado com outro nome, um float onde ele espera inteiro, e tudo volta a não funcionar
 * exatamente como antes.
 *
 * Por isso este teste não confere o formato por expressão regular: ele EXTRAI o `_powerUnpackFlow`
 * do próprio index.html e RODA os pacotes por dentro dele. Quem valida é o consumidor real, não
 * uma cópia da nossa ideia do que ele faz.
 *
 * Uso: node scripts/test-fluxo-de-potencia.mjs
 */
import { readFileSync, existsSync } from 'node:fs'
import { join, resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createContext, runInContext } from 'node:vm'

const RAIZ = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const WWW = join(RAIZ, 'app', 'src', 'main', 'assets', 'www')
const KT = join(RAIZ, 'app', 'src', 'main', 'java', 'br', 'com', 'deepgwt')

let falhas = 0
let total = 0

function ok(descricao, condicao, detalhe) {
  total++
  if (condicao) console.log('ok  ' + descricao)
  else {
    falhas++
    console.log('FALHOU  ' + descricao + (detalhe ? '\n        ' + detalhe : ''))
  }
}

function ler(caminho) {
  return existsSync(caminho) ? readFileSync(caminho, 'utf8') : ''
}

const bundle = ler(join(WWW, 'index.html'))
const mapper = ler(join(KT, 'managers', 'PowerFlowMapper.kt'))
const servico = ler(join(KT, 'services', 'LigacoesDoMotor.kt'))
const receptor = ler(join(KT, 'broadcastReceivers', 'ViewerBridgeReceiver.kt'))

if (!bundle || !mapper || !servico || !receptor) {
  console.log('Fontes não encontradas.')
  process.exit(1)
}

// ── 1. O consumidor, executado de verdade ──────────────────────────────────────────────────
//
// `_powerUnpackFlow` é um método de classe dentro do bundle. Recortamos o corpo dele e o
// transformamos numa função solta — o corpo é o que importa, e ele não usa `this`.

const trecho = bundle.match(/_powerUnpackFlow\(raw\)\s*\{([\s\S]*?)\n  \}/)
ok(
  'o _powerUnpackFlow do launcher foi encontrado no bundle',
  !!trecho,
  'sem ele este teste não tem como validar contra o consumidor real; o recorte precisa ser refeito'
)

let desempacotar = null
if (trecho) {
  const ctx = { Math, String, Number, isFinite }
  createContext(ctx)
  runInContext('var f = function (raw) {' + trecho[1] + '};', ctx)
  desempacotar = ctx.f
}

/** Todos os pacotes que o PowerFlowMapper sabe produzir, na forma exata em que ele os monta. */
const PACOTES = [
  ['v1|idle|0|0|0', 'carro parado e em espera'],
  ['v1|idle|1|0|0', 'motor em marcha lenta com o carro parado'],
  ['v1|ev|0|1|0', 'tração elétrica'],
  ['v1|hybrid|1|1|0', 'tração híbrida'],
  ['v1|ice|1|1|0', 'tração a combustão'],
  ['v1|regen|0|-1|0', 'regeneração'],
  ['v1|charge|0|0|0', 'carregando no plugue'],
  ['v1|ev|0|1|1', 'tração nos dois eixos, se um dia houver motor traseiro']
]

if (desempacotar) {
  for (const [pacote, nome] of PACOTES) {
    ok(
      `o launcher aceita o pacote de ${nome}`,
      desempacotar(pacote) !== null,
      `"${pacote}" seria recusado e o cartão mostraria "Fluxo indisponível"`
    )
  }

  // O SINAL é o que o consumidor usa; o valor absoluto ele descarta.
  ok(
    'o sentido da tração chega como o launcher espera',
    desempacotar('v1|ev|0|1|0').front === 1 &&
      desempacotar('v1|regen|0|-1|0').front === -1 &&
      desempacotar('v1|idle|0|0|0').front === 0
  )
  ok(
    'o motor a combustão chega como booleano',
    desempacotar('v1|hybrid|1|1|0').ice === true && desempacotar('v1|ev|0|1|0').ice === false
  )

  // E os erros que este contrato comete com mais facilidade.
  ok(
    'um estado inventado é recusado pelo launcher',
    desempacotar('v1|boost|0|1|0') === null,
    'só valem idle, ev, hybrid, ice, regen e charge'
  )
  ok(
    'um float no lugar do inteiro é recusado',
    desempacotar('v1|ev|0|1.0|0') === null,
    'o launcher casa /^-?\\d+$/ no eixo — "1.0" não passa'
  )
  ok(
    'espaço sobrando é recusado',
    desempacotar('v1| ev|0|1|0') === null
  )
  ok(
    'versão diferente de v1 é recusada',
    desempacotar('v2|ev|0|1|0') === null
  )
}

// ── 2. O produtor monta exatamente esses pacotes ────────────────────────────────────────────

ok(
  'o PowerFlowMapper monta o pacote no formato v1|state|ice|front|rear',
  /"v1\|\$state\|\$\{if \(iceOn\) 1 else 0\}\|\$front\|\$rear"/.test(mapper),
  'a montagem mudou de forma; refazer a lista PACOTES acima junto'
)
ok(
  'os seis estados do contrato estão todos cobertos',
  ['idle', 'ev', 'hybrid', 'ice', 'regen', 'charge'].every((e) => mapper.includes(`"${e}"`))
)
ok(
  'front e rear saem como inteiro, não como medida',
  /front = 1/.test(mapper) && /front = -1/.test(mapper) && !/front = [0-9]+\.[0-9]/.test(mapper),
  'o launcher só lê o sinal; publicar um valor sugeriria telemetria de eixo que este carro não tem'
)

// ── 3. O motor a combustão vem do GIRO, não do estado de ignição ────────────────────────────
//
// THEME_AUTHORING_GUIDE.md §7.2 é explícito: `engine_state` é ignição, não motor — no carro ele vem
// "11" andando no elétrico e parado em READY, e nada nele muda quando o motor liga e desliga
// sozinho. Usá-lo aqui não diria nada sobre o motor a combustão.

ok(
  'o motor a combustão é decidido por car.basic.engine_speed',
  mapper.includes('CAR_BASIC_ENGINE_SPEED'),
)
ok(
  'engine_state NÃO é usado para decidir se o motor está girando',
  !/CAR_BASIC_ENGINE_STATE/.test(mapper),
  'ver THEME_AUTHORING_GUIDE.md §7.2 — num híbrido essa chave não responde essa pergunta'
)
ok(
  'a decisão tem histerese nos dois sentidos',
  /ICE_ON_RPM/.test(mapper) && /ICE_OFF_RPM/.test(mapper) &&
    /!iceOn && rpm >= ICE_ON_RPM/.test(mapper) && /iceOn && rpm <= ICE_OFF_RPM/.test(mapper),
  'sem histerese o indicador pisca com o motor oscilando em torno do corte'
)

// ── 4. A chave chega ao launcher pelos DOIS caminhos ────────────────────────────────────────

ok(
  'toda chave que o carro publica passa pelo mapeador',
  /Veiculo\.Ouvinte \{ chave, valor -> PowerFlowMapper\.onSignal\(chave, valor\) \}/.test(servico) &&
    /ouvintesFixos = listOf\(MapeadorDeFluxo\)/.test(servico),
  'o gancho é um ouvinte do motor registrado em LigacoesDoMotor (o antigo funil dispatchTelemetryOnly foi substituído)'
)
ok(
  'o retrato pedido pelo launcher inclui a chave derivada',
  /PowerFlowMapper\.republish\(\)/.test(receptor),
  'dispatchAllData só reemite chaves CRUAS; sem isto o raio-X abre vazio num carro parado'
)
ok(
  'a chave publicada é a que o launcher escuta',
  /const val KEY_FLOW = "haval\.power\.flow"/.test(mapper) &&
    bundle.includes('powerFlow: "haval.power.flow"')
)
ok(
  'o espelho booleano do gravador de trajetos também sai',
  /const val KEY_ICE = "haval\.power\.ice"/.test(mapper)
)

// ── 5. Não há recursão pelo funil de telemetria ─────────────────────────────────────────────
//
// O mapeador publica PELA MESMA função que o alimenta. Se a chave derivada estivesse entre as
// observadas, cada publicação se realimentaria.

const observadas = mapper.match(/private val WATCHED: Set<String> = setOf\(([\s\S]*?)\)/)
ok(
  'a lista de chaves observadas foi encontrada',
  !!observadas
)
if (observadas) {
  ok(
    'a chave que ele publica não está entre as que ele escuta',
    !observadas[1].includes('KEY_FLOW') && !observadas[1].includes('haval.power'),
    'se estivesse, publicar dispararia um novo cálculo, que publicaria de novo'
  )
}

console.log('')
if (falhas > 0) {
  console.log(`${total} verificações, ${falhas} falharam.`)
  process.exit(1)
}
console.log(`${total} verificações, todas passaram.`)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
