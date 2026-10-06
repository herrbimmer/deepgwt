// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Guardas da tela "Ajustes do DeepGWT" (6.4): leitura das respostas do carro, dependências,
// "alterado", busca e a mescla que protege o que o usuário está mexendo.
//
// O catálogo usado é o retrato real do app (src/mock/ajustesFixture.json, tirado do emulador),
// então as regras são checadas contra o que o carro de fato manda — não contra um exemplo inventado.

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

let falhas = 0
let total = 0
const ok = (cond, nome) => {
  total += 1
  if (!cond) {
    falhas += 1
    console.error(`  FALHOU: ${nome}`)
  }
}
const eq = (a, e, nome) => ok(JSON.stringify(a) === JSON.stringify(e), `${nome}\n    esperado: ${JSON.stringify(e)}\n    obtido:   ${JSON.stringify(a)}`)
const lanca = (fn, tipo, nome) => {
  try {
    fn()
    ok(false, `${nome} (não lançou)`)
  } catch (e) {
    ok(!tipo || e instanceof tipo, `${nome} (lançou ${e?.name})`)
  }
}

const m = await import('../src/ajustes-deepgwt/modelo.ts')
const fixture = JSON.parse(readFileSync(fileURLToPath(new URL('../src/mock/ajustesFixture.json', import.meta.url)), 'utf8'))

// ── 1. Respostas ─────────────────────────────────────────────────────────────────────────────
lanca(() => m.lerResposta('{"car.basic.vehicle_speed":"0"}'), m.CarroAntigoError, 'telemetria sem ok = carro antigo')
lanca(() => m.lerResposta('<html>'), Error, 'não-JSON vira erro')
lanca(() => m.lerResposta('{"ok":false,"error":"x"}'), Error, 'ok=false vira erro com o motivo')
const r = m.lerResposta(JSON.stringify(fixture))
const cat = m.lerCatalogo(r)
const est = m.lerEstado(r)
ok(cat.ajustes.length >= 100, `catálogo completo (${cat.ajustes.length})`)
ok(cat.secoes.length === 13, 'treze seções')
// Alpha 7.2: o estilo das telas do app (Grade, Bávaro e, desde a 7.3, Estugarda) também é trocado pelo instalador.
{
  const estilo = cat.ajustes.find((a) => a.id === 'interfaceStyle')
  ok(estilo?.secao === 'interface' && estilo.tipo === 'opcoes', 'estilo da interface na seção própria')
  ok(estilo?.padrao === 'grade', 'padrão do estilo é a grade de sempre')
  ok(JSON.stringify(estilo?.opcoes.map((o) => o.id)) === '["grade","bavaro","estugarda","cupertino"]', 'opções grade, bavaro, estugarda e cupertino (9.5)')
}
ok(Object.keys(est.valores).length === cat.ajustes.length, 'um valor para cada ajuste')
// 9.5: a luz ambiente diz a que reage (a seta vem desligada) e o HUD tem modo noturno.
{
  const reacoes = cat.ajustes.filter((a) => a.id.startsWith('ambientLightReact'))
  ok(reacoes.length === 6 && reacoes.every((a) => a.visivelSe?.id === 'ambientLightSyncDriveMode'), 'seis reações da luz, dentro da iluminação dinâmica')
  ok(reacoes.find((a) => a.id === 'ambientLightReactTurnSignal')?.padrao === false, 'a seta vem desligada')
  const noturno = cat.ajustes.find((a) => a.id === 'hudNightMode')
  ok(noturno?.padrao === 'desligado' && noturno.opcoes.map((o) => o.id).join() === 'desligado,automatico,sempre', 'modo noturno do HUD')
  ok(cat.ajustes.find((a) => a.id === 'hudNightBrightness')?.visivelSe?.valores.join() === 'automatico,sempre', 'brilho noturno só com o modo ligado')
}


const porId = new Map(cat.ajustes.map((a) => [a.id, a]))
const aj = (id) => porId.get(id)

// ── 2. Dependências ──────────────────────────────────────────────────────────────────────────
const vals = { ...est.valores, enable_persistent_bottom_bar: false }
eq(m.bloqueadoPor(aj('bottomBarHeightDp'), porId, vals)?.id, 'enable_persistent_bottom_bar', 'altura depende da barra')
ok(m.visivel(aj('bottomBarHeightDp'), porId, { ...vals, enable_persistent_bottom_bar: true }), 'altura aparece com a barra')
// Em cadeia: cadência depende da fonte de rotação, que depende do perfil de som.
const asd = { ...est.valores, asdMasterEnabled: true, asdSoundProfile: 'ELETRICO', asdRpmSource: 'SIMULADA' }
eq(m.bloqueadoPor(aj('asdCadence'), porId, asd)?.id, 'asdSoundProfile', 'cadência some quando o perfil esconde a fonte')
ok(m.visivel(aj('asdCadence'), porId, { ...asd, asdSoundProfile: 'COMBUSTAO' }), 'cadência volta com combustão + simulada')
// Toda dependência do catálogo aponta para um ajuste que existe.
cat.ajustes.forEach((a) => a.visivelSe && ok(porId.has(a.visivelSe.id), `${a.id}: depende de ${a.visivelSe.id}, que existe`))
// Dependência de ajuste que este carro não descreve não esconde nada.
ok(m.visivel({ ...aj('bottomBarHeightDp'), visivelSe: { id: 'fantasma', valores: ['true'] } }, porId, vals), 'dependência desconhecida não esconde')

// ── 3. Alterado ──────────────────────────────────────────────────────────────────────────────
ok(!m.alterado(aj('bottomBarHeightDp'), 60), 'padrão não é alterado')
ok(m.alterado(aj('bottomBarHeightDp'), 76), 'fora do padrão é alterado')
ok(!m.alterado(aj('gaugeNumberScale'), 1.0000001), 'decimal com lixo binário não conta')
ok(!m.alterado(aj('hudSpeedColor'), '#ffffff'), 'cor sem diferença de caixa')
const semPadrao = cat.ajustes.find((a) => a.padrao === null)
ok(semPadrao && !m.alterado(semPadrao, true) && !m.alterado(semPadrao, false), 'sem padrão (amplificador) nunca está alterado')

// ── 4. Texto e números ───────────────────────────────────────────────────────────────────────
eq(m.descreverValor(aj('volumeOverlayLogoType'), 'jbl'), 'JBL', 'opção mostra o rótulo')
eq(m.descreverValor(aj('bottomBarHeightDp'), 60), '60 dp', 'número com unidade')
eq(m.formatarNumero(aj('gaugeNumberScale'), 1.25), '1.25', 'decimal com duas casas')
eq(m.formatarNumero(aj('gaugeNumberScale'), 1), '1', 'decimal redondo sem zeros')
eq(m.faixaDe(aj('sys.settings.audio.sound_field_value'), est.faixas), [0, 14], 'faixa do amplificador vale mais')
eq(m.faixaDe(aj('bottomBarHeightDp'), est.faixas), [40, 140], 'faixa do catálogo sem a do carro')

// ── 5. Busca ─────────────────────────────────────────────────────────────────────────────────
const secao = (id) => cat.secoes.find((s) => s.id === id)
ok(m.combina(aj('bottomBarHeightDp'), secao('barra'), 'altura barra'), 'busca por várias palavras')
ok(m.combina(aj('hudOverlayEnabled'), secao('hud'), 'head-up'), 'busca pelo nome da seção')
ok(m.combina(aj('disableVoiceWakeUp'), secao('gestos'), 'ola haval'), 'busca ignora acento')
ok(!m.combina(aj('bottomBarHeightDp'), secao('barra'), 'altura hud'), 'todas as palavras precisam aparecer')

// ── 5b. Níveis (Alpha 8.0) ───────────────────────────────────────────────────────────────────
// O carro diz o nível que cada ajuste pede; a tela só desenha o cadeado quando vem `bloqueado`.
cat.ajustes.forEach((a) => ok(typeof a.recurso === 'string' && typeof a.nivelExigido === 'string', `${a.id}: recurso e nível`))
eq(aj('painel.informacoes').nivelExigido, 'drive', 'informações no painel original são Drive')
eq(aj('asdMasterEnabled').nivelExigido, 'sport', 'ASD é Sport')
eq(aj('interfaceEscala').nivelExigido, 'drive', 'tamanho da interface é Drive (acessibilidade)')
ok(cat.ajustes.every((a) => !a.bloqueado), 'o retrato (sem cobrança) não tem nada trancado')

// ── 6. Mescla ────────────────────────────────────────────────────────────────────────────────
const atuais = { a: 1, b: true, c: 'x' }
const r1 = m.mesclar(atuais, { a: 2, b: true, c: 'y' }, new Set(['c']))
eq(r1.valores, { a: 2, b: true, c: 'x' }, 'o que está sendo mexido não volta atrás')
eq(r1.mudaram, ['a'], 'só o que mudou por fora é destacado')
eq(m.mesclar({}, { a: 1 }, new Set()).mudaram, [], 'primeira leitura não destaca nada')

console.log(`ajustes: ${total - falhas}/${total} verificações`)
if (falhas) process.exit(1)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
