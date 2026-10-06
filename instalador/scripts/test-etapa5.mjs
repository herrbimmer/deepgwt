// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Testes da camada EXPERT: busca nos catálogos, consulta de chave e a tabela de validação.
//
// Sem dependência de teste: o Node 22 remove os tipos do TypeScript sozinho, e
// `scripts/ts-resolve.mjs` (registrado por --import) resolve os imports sem extensão.
//
// Os dois testes que mais importam aqui não verificam comportamento de tela:
//   1. os filtros de escopo do VHAL precisam bater com os números que a própria base declara no
//      `meta`. Se `filterVhal(scope:'nomeadas')` devolver um número diferente de `meta.named`, ou
//      a busca está errada ou o `meta` está mentindo — e nos dois casos as telas passam a exibir
//      contagem que não corresponde ao que listam;
//   2. o texto do veredito "nunca vista" não pode afirmar que a chave não existe. É a única
//      conclusão que a tela não tem como sustentar, e é a mais fácil de escrever sem querer.

let falhas = 0
let total = 0

function ok(cond, nome) {
  total += 1
  if (cond) return
  falhas += 1
  console.error(`  FALHOU: ${nome}`)
}

function eq(actual, expected, nome) {
  total += 1
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) return
  falhas += 1
  console.error(`  FALHOU: ${nome}\n    esperado: ${e}\n    obtido:   ${a}`)
}

const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => void store.set(k, String(v)),
  removeItem: (k) => void store.delete(k),
  clear: () => store.clear(),
}

const { filterControlService, filterVhal, facetsOf, page, PAGE_SIZE } =
  await import('../src/expert/catalog.ts')
const { lookupKey, VERDICT_TEXT } = await import('../src/expert/discovery.ts')
const { CONTROL_SERVICE, VHAL } = await import('../src/data/knowledge.ts')
const { VALIDATION, validationOf } = await import('../src/validation/status.ts')

console.log('\nCatálogo do ControlService')
{
  eq(filterControlService(CONTROL_SERVICE.keys, {}).length, CONTROL_SERVICE.meta.count,
    'filtro vazio devolve o levantamento inteiro')

  const porChave = filterControlService(CONTROL_SERVICE.keys, { query: 'total_odometer' })
  ok(porChave.length > 0, 'busca por trecho de chave encontra alguma coisa')
  ok(porChave.every((k) => k.key.includes('total_odometer') || k.enumName.toLowerCase().includes('total_odometer')),
    'todo resultado realmente contém o termo')

  eq(filterControlService(CONTROL_SERVICE.keys, { query: 'TOTAL_ODOMETER' }).length, porChave.length,
    'busca ignora maiúsculas e minúsculas')
  eq(filterControlService(CONTROL_SERVICE.keys, { query: '  total_odometer  ' }).length, porChave.length,
    'busca ignora espaço em volta')

  const ns = filterControlService(CONTROL_SERVICE.keys, { namespace: 'car.configure' })
  ok(ns.length > 0, 'filtro por namespace devolve resultado')
  ok(ns.every((k) => k.namespace === 'car.configure'), 'filtro por namespace é exato, não prefixo')

  const ocultas = filterControlService(CONTROL_SERVICE.keys, { appState: 'oculta' })
  ok(ocultas.every((k) => k.appState === 'oculta'), 'filtro por exposição não deixa passar outro estado')

  const combinado = filterControlService(CONTROL_SERVICE.keys, { namespace: 'car.configure', appState: 'oculta' })
  ok(combinado.length <= ns.length && combinado.length <= ocultas.length,
    'filtros combinam por interseção, nunca por união')

  eq(filterControlService(CONTROL_SERVICE.keys, { query: 'nao.existe.chave.nenhuma' }).length, 0,
    'termo sem correspondência devolve lista vazia, não a lista inteira')
}

console.log('\nCatálogo VHAL')
{
  eq(filterVhal(VHAL.properties, {}).length, VHAL.meta.count,
    'filtro vazio devolve o catálogo inteiro')
  eq(filterVhal(VHAL.properties, { scope: 'nomeadas' }).length, VHAL.meta.named,
    'escopo "nomeadas" bate com o meta.named declarado pela base')
  eq(filterVhal(VHAL.properties, { scope: 'descritas' }).length, VHAL.meta.described,
    'escopo "descritas" bate com o meta.described declarado pela base')
  eq(filterVhal(VHAL.properties, { scope: 'com-valor' }).length, VHAL.meta.observedValues,
    'escopo "com valor capturado" bate com o meta.observedValues declarado pela base')

  const comId = VHAL.properties.find((p) => p.id)
  eq(filterVhal(VHAL.properties, { query: comId.id }).length >= 1, true,
    'busca pelo id hexadecimal completo encontra a propriedade')
  eq(filterVhal(VHAL.properties, { query: comId.id.replace(/^0x/, '') }).length >= 1, true,
    'busca pelo id sem o prefixo 0x também encontra — é como o id aparece copiado de um log')

  const acessos = facetsOf(VHAL.properties, (p) => p.access)
  ok(acessos.length > 0, 'as facetas de acesso não estão vazias')
  const somaAcesso = acessos.reduce((s, f) => s + f.count, 0)
  eq(somaAcesso, VHAL.properties.filter((p) => p.access !== null).length,
    'a soma das facetas cobre todas as propriedades com acesso, sem contar duas vezes')
  ok(acessos.every((f, i) => i === 0 || acessos[i - 1].count >= f.count),
    'facetas vêm da mais numerosa para a menos')

  const prefixos = facetsOf(VHAL.properties, (p) => p.ecuPrefix)
  ok(prefixos.every((f) => f.value !== null && f.value !== ''), 'faceta nunca inclui valor nulo')
  const umPrefixo = prefixos[0]
  eq(filterVhal(VHAL.properties, { ecuPrefix: umPrefixo.value }).length, umPrefixo.count,
    'o filtro por prefixo devolve exatamente o que a faceta prometeu')
}

console.log('\nRecorte de página')
{
  const grande = filterVhal(VHAL.properties, {})
  const r = page(grande)
  eq(r.shown.length, PAGE_SIZE, 'o recorte respeita o teto de itens renderizados')
  eq(r.total, grande.length, 'o total informado continua sendo o total de verdade, não o recorte')
  eq(r.truncated, true, 'o recorte se declara truncado')

  const pequeno = page(grande.slice(0, 3))
  eq(pequeno.shown.length, 3, 'lista menor que o teto sai inteira')
  eq(pequeno.truncated, false, 'e não se declara truncada')
}

console.log('\nConsulta de chave')
{
  const conhecida = CONTROL_SERVICE.keys[0].key

  eq(lookupKey(conhecida, { [conhecida]: '42' }).verdict, 'conhecida-respondida',
    'chave do levantamento com resposta')
  eq(lookupKey(conhecida, {}).verdict, 'conhecida-silenciosa',
    'chave do levantamento sem resposta')
  eq(lookupKey('car.inventada.para.o.teste', { 'car.inventada.para.o.teste': '1' }).verdict, 'descoberta',
    'chave fora do levantamento que o carro respondeu é descoberta')
  eq(lookupKey('car.inventada.para.o.teste', {}).verdict, 'nao-encontrada',
    'chave fora do levantamento e sem resposta é "nunca vista"')

  eq(lookupKey(`  ${conhecida}  `, {}).key, conhecida, 'espaço em volta do nome é aparado')

  const vazia = lookupKey(conhecida, { [conhecida]: '' })
  eq(vazia.verdict, 'conhecida-respondida', 'resposta vazia ainda é resposta')
  eq(vazia.value, null, 'e o valor vazio vira null, não string vazia')
  eq(lookupKey(conhecida, {}).value, undefined, 'ausência de resposta é undefined, distinta de vazio')

  ok(lookupKey(conhecida, {}).entry !== null, 'a entrada do levantamento acompanha a chave conhecida')
  eq(lookupKey('car.inventada.para.o.teste', {}).entry, null, 'chave desconhecida não inventa entrada')

  eq(VERDICT_TEXT['descoberta'].confidence, 'REQUIRES_VALIDATION',
    'descoberta nova nasce como hipótese a validar, nunca como fato')
  eq(VERDICT_TEXT['nao-encontrada'].confidence, 'UNKNOWN',
    'chave nunca vista fica como desconhecida')
  eq(VERDICT_TEXT['conhecida-respondida'].confidence, 'OBSERVED',
    'valor lido nesta sessão é observado, não fato permanente')
}

console.log('\nO que os vereditos NÃO podem afirmar')
{
  const naoEncontrada = VERDICT_TEXT['nao-encontrada']
  ok(/NÃO quer dizer que ela não existe/.test(naoEncontrada.body),
    'o veredito "nunca vista" nega explicitamente a conclusão de inexistência')

  for (const [id, t] of Object.entries(VERDICT_TEXT)) {
    const texto = `${t.title} ${t.body}`
    ok(!/inexistente|não existe no carro|não está no carro/i.test(texto),
      `o veredito "${id}" não afirma que a chave não existe no veículo`)
    ok(!/fraude|adulterad|suspeit/i.test(texto), `o veredito "${id}" não acusa nada`)
  }

  // "descoberta" é o veredito onde a tentação de promover a fato é maior.
  ok(/hipótese|precisa ser reproduzida/i.test(VERDICT_TEXT['descoberta'].body),
    'o veredito de descoberta diz que a chave ainda não vale como fato')
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

console.log('\nTabela de validação')
{
  const entradas = Object.entries(VALIDATION)
  const validadas = entradas.filter(([, v]) => v.status === 'vehicle-validated')
  // 9.5: o Monitorar Veículo entrou, confirmado pelo dono no carro em 30/09/2026.
  eq(validadas.map(([id]) => id).sort(), ['coding.esp', 'quick.avas', 'quick.dms', 'vehicle.monitor'],
    'são 4 as capacidades validadas em veículo (AVAS, DMS, ESP e o monitor)')
  ok(validadas.every(([, v]) => typeof v.evidence === 'string' && v.evidence.length > 0),
    'toda capacidade validada cita onde está a prova')

  const expert = entradas.filter(([id]) => id.startsWith('expert.'))
  ok(expert.length >= 10, 'as capacidades de engenharia estão declaradas')
  ok(expert.every(([, v]) => v.status !== 'vehicle-validated'),
    'nenhuma capacidade de engenharia se declara validada em veículo')
  ok(entradas.every(([, v]) => v.note.trim().length > 0),
    'toda capacidade explica o próprio estado — nenhuma entrada muda de status em silêncio')

  eq(validationOf('nao.existe.essa.capacidade').status, 'not-validated',
    'capacidade desconhecida cai no padrão não-validado, nunca em validado')
  eq(validationOf(undefined).status, 'not-validated', 'ausência de capacidade também cai no padrão')
}

console.log('\nDiagrama de módulos do Coding (formato ISTA)')
{
  const { montarDominios, MODULOS_POR_COLUNA, DOMINIO_SEM_NAMESPACE } = await import(
    '../src/coding/moduleDiagram.ts'
  )

  // A cor da caixa é a única coisa do diagrama que AFIRMA algo sobre o carro, então é o que
  // precisa estar certo: ela só pode dizer "verde" quando toda chave do módulo veio com valor.
  {
    const dominios = montarDominios(
      ['car.a.x', 'car.a.y', 'car.b.x', 'car.b.y', 'car.c.x'],
      { 'car.a.x': '1', 'car.a.y': '0', 'car.b.x': '1', 'car.b.y': '', 'car.c.x': '' },
    )
    const car = dominios.find((d) => d.nome === 'car')
    const porNome = Object.fromEntries(car.modulos.map((m) => [m.nome, m]))
    eq(porNome.a.estado, 'responde', 'módulo com todas as chaves preenchidas fica verde')
    eq(porNome.b.estado, 'parcial', 'módulo com uma chave vazia fica parcial')
    eq(porNome.c.estado, 'mudo', 'módulo sem nenhum valor fica apagado')
    eq(porNome.a.comValor, 2, 'a contagem de chaves com valor é a que a caixa mostra')
  }

  // `0` é um valor que o carro devolveu, não ausência de valor. Tratar "0" como vazio pintaria de
  // apagado justamente os módulos de chaves liga/desliga desligadas — o caso mais comum que existe.
  {
    const [d] = montarDominios(['car.x.a'], { 'car.x.a': '0' })
    eq(d.modulos[0].estado, 'responde', 'valor "0" conta como chave respondida')
  }

  // Chave que o carro nunca reportou e chave reportada vazia são distintas no resto do app; no
  // diagrama as duas contam como sem valor, e é isso que a legenda diz.
  {
    const [d] = montarDominios(['car.x.a', 'car.x.b'], { 'car.x.a': '' })
    eq(d.modulos[0].comValor, 0, 'chave ausente do mapa conta como sem valor, igual à vazia')
    eq(d.modulos[0].estado, 'mudo', 'módulo sem leitura nenhuma fica apagado')
  }

  // Uma chave de um segmento só existe no carro (`system_setting_daynight_theme`). Ela não tem
  // `dominio.modulo`, e sumir do diagrama seria pior do que aparecer num domínio próprio.
  {
    const dominios = montarDominios(['system_setting_daynight_theme'], {})
    eq(dominios.length, 1, 'chave sem namespace não é descartada')
    eq(dominios[0].nome, DOMINIO_SEM_NAMESPACE, 'ela cai num domínio próprio e declarado')
  }

  // As colunas são o que impede um domínio grande (o `car` real tem 37 módulos) de virar uma
  // coluna de 37 caixas que não cabe em tela nenhuma.
  {
    const chaves = []
    for (let i = 0; i < 25; i += 1) chaves.push(`car.m${String(i).padStart(2, '0')}.k`)
    const [car] = montarDominios(chaves, {})
    eq(car.modulos.length, 25, 'todos os módulos entram')
    eq(car.colunas.length, 3, '25 módulos rendem 3 colunas com o limite atual')
    ok(
      car.colunas.every((c) => c.length <= MODULOS_POR_COLUNA),
      'nenhuma coluna passa do limite',
    )
    eq(
      car.colunas.flat().length,
      car.modulos.length,
      'repartir em colunas não perde nem duplica módulo',
    )
    eq(
      car.colunas.flat().map((m) => m.nome),
      car.modulos.map((m) => m.nome),
      'a ordem alfabética sobrevive à repartição em colunas',
    )
  }

  // Domínio maior primeiro: é o que ancora o desenho à esquerda.
  {
    const dominios = montarDominios(['sys.a.k', 'car.a.k', 'car.b.k'], {})
    eq(dominios.map((d) => d.nome), ['car', 'sys'], 'o domínio com mais módulos vem primeiro')
  }
}

console.log(`\n${total} verificações. ${falhas === 0 ? 'Todas passaram.' : `${falhas} falharam.`}`)
process.exit(falhas === 0 ? 0 : 1)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
