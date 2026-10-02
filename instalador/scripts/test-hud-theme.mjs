// Testes do criador de temas do HUD (Alpha 5.9).
//
// O carro e o instalador leem o mesmo arquivo de tema. Este teste lê os MESMOS exemplos que o
// `HudTemaTest.kt` do app (app/src/test/resources/hud-tema) e confere que o editor chega ao mesmo
// tema normalizado — e que as frações da prévia são as do `companion object` da HudOverlayView.
//
// Roda com: node --import ./scripts/ts-resolve-register.mjs scripts/test-hud-theme.mjs

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
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
/** Igualdade de JSON sem depender da ordem das chaves (o Kotlin não garante a mesma ordem). */
const canon = (v) =>
  Array.isArray(v) ? v.map(canon) : v && typeof v === 'object'
    ? Object.fromEntries(Object.keys(v).sort().map((k) => [k, canon(v[k])]))
    : v
const eq = (a, b, nome) => {
  total += 1
  const x = JSON.stringify(canon(a))
  const y = JSON.stringify(canon(b))
  if (x === y) return
  falhas += 1
  console.error(`  FALHOU: ${nome}\n    esperado: ${y}\n    obtido:   ${x}`)
}

const raiz = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const recurso = (n) => JSON.parse(readFileSync(join(raiz, 'app/src/test/resources/hud-tema', n), 'utf8'))

const model = await import('../src/hud-editor/model.ts')
const { GRADE, lerCampo, CENARIOS, milhar, kmPorLitro, pesoDoRotulo } = await import('../src/hud-editor/render.ts')
const { HUD_PRESETS } = await import('../src/hud-editor/presets.ts')

// ---------------------------------------------------------------- o mesmo arquivo, o mesmo tema
{
  const tema = model.paraCarro(model.normalizarTemaHud(recurso('exemplo.json')))
  eq(tema, recurso('exemplo.normalizado.json'), 'exemplo compartilhado com o HudTemaTest.kt normaliza igual')
  // Normalizar o normalizado não muda nada.
  eq(model.paraCarro(model.normalizarTemaHud(tema)), tema, 'normalizar é idempotente')
}

// ---------------------------------------------------------------- recusa só o que não é tema
for (const bruto of [null, [], 'texto', { formato: 'outro' }, { nome: 'sem formato' }]) {
  let recusou = false
  try {
    model.normalizarTemaHud(bruto)
  } catch {
    recusou = true
  }
  ok(recusou, `recusa ${JSON.stringify(bruto)}`)
}
{
  const minimo = model.normalizarTemaHud({ formato: 'opengw-hud-tema' })
  eq(minimo, { ...model.temaHudPadrao('Tema sem nome') }, 'só o formato vira o HUD de fábrica')
}

// ---------------------------------------------------------------- regras que o carro também aplica
{
  eq([150, 200, 249, 250, 651, 950].map(model.arredondarPeso), [200, 200, 200, 300, 700, 900], 'peso em degraus de cem, na faixa')
  const todas = model.HUD_PAGINAS.map((p) => ({ pagina: p.id, ativa: false }))
  ok(model.normalizarTemaHud({ formato: 'opengw-hud-tema', paginas: todas }).paginas.every((p) => p.ativa),
    'nenhuma página ativa vira todas ativas')
  const semA = model.normalizarTemaHud({ formato: 'opengw-hud-tema', paginas: [{ pagina: 'VEICULO', linhaA: [], linhaB: ['HORA'] }] })
  ok(semA.paginas.find((p) => p.pagina === 'VEICULO').linhaA === undefined, 'página de números sem valor grande volta à de fábrica')
  const midia = model.normalizarTemaHud({ formato: 'opengw-hud-tema', paginas: [{ pagina: 'MIDIA', linhaA: ['HORA'] }] })
  ok(midia.paginas.find((p) => p.pagina === 'MIDIA').linhaA === undefined, 'mídia não aceita campos')
  eq(model.normalizarCor('#abc'), '#AABBCC', 'cor de três dígitos')
  ok(model.normalizarCor('vermelho') === null, 'cor ilegível é recusada')
}

// ---------------------------------------------------------------- os prontos são temas válidos
for (const p of HUD_PRESETS) {
  const carro = model.paraCarro(p.tema)
  eq(model.paraCarro(model.normalizarTemaHud(carro)), carro, `pronto "${p.nome}" sobrevive à ida e volta`)
  ok(model.paginasVisiveis(p.tema).length > 0, `pronto "${p.nome}" tem página no rodízio`)
  for (const pg of p.tema.paginas) {
    if (pg.cor) ok(model.luminancia(pg.cor) >= 0.18, `pronto "${p.nome}" ${pg.pagina}: cor aparece no vidro`)
  }
}
eq(model.paraCarro(HUD_PRESETS[0].tema), model.paraCarro(model.temaHudPadrao('Padrão OPEN GW')), '"Padrão OPEN GW" é o HUD de fábrica')

// ---------------------------------------------------------------- a prévia usa as frações da view
{
  const kt = readFileSync(
    join(raiz, 'app/src/main/java/br/com/opengw/hud/HudOverlayView.kt'),
    'utf8',
  )
  for (const [nome, valor] of Object.entries(GRADE)) {
    const m = kt.match(new RegExp(`const val ${nome} = ([0-9.]+)f`))
    ok(m && Number(m[1]) === valor, `fração ${nome} igual à da HudOverlayView (${m?.[1]} x ${valor})`)
  }
  ok(/const val DURACAO_FADE_MS/.test(kt), 'arquivo Kotlin lido de verdade')
  // Os rótulos da prévia são os textos do HudCampos.kt.
  const campos = readFileSync(join(raiz, 'app/src/main/java/br/com/opengw/hud/HudCampos.kt'), 'utf8')
  for (const c of model.HUD_CAMPOS) {
    ok(new RegExp(`\\b${c.id}\\(`).test(campos), `campo ${c.id} existe no HudCampo do carro`)
  }
  for (const [pagina, layout] of Object.entries(model.LAYOUT_PADRAO)) {
    const bloco = campos.slice(campos.indexOf(`HudPaginas.Pagina.${pagina} ->`))
    const a = layout.linhaA.map((c) => `HudCampo.${c}`).join(', ')
    ok(bloco.includes(a), `campos de fábrica de ${pagina} iguais aos do carro`)
  }
}

// ---------------------------------------------------------------- textos que o carro escreve
{
  eq(milhar(49435), '49.435', 'milhar')
  eq(kmPorLitro(100 / 7.8), '12,8', 'km/L com vírgula')
  eq(pesoDoRotulo(700), 500, 'rótulo dois degraus abaixo')
  eq(pesoDoRotulo(300), 300, 'rótulo nunca mais grosso que o valor')
  const cidade = CENARIOS[0]
  eq(lerCampo('TRACAO', cidade), null, 'tração some sem AWD')
  eq(lerCampo('POTENCIA', { ...cidade, potencia: -4 }).rotulo, 'Regenerando', 'potência negativa vira regeneração')
  eq(lerCampo('CONSUMO', { ...cidade, consumo: { tipo: 'eletrico' } }).valor, 'Elétrico', 'consumo sem motor a combustão')
}

console.log(`\nTema do HUD: ${total - falhas}/${total} ok`)
if (falhas > 0) process.exit(1)
