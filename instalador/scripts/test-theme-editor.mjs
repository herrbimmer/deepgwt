// Testes da reformulação do criador de temas (Alpha 6.5): cenários da prévia, seleção múltipla,
// estilos compartilhados, custo, assistente, blocos, pacote e o que o payload leva dos elementos
// novos.
//
// Mesmo estilo de `test-theme-engine.mjs`: sem dependência de teste, cada bloco diz o defeito que
// ele impede. O que depende de DOM (iframe, IndexedDB, canvas) foi conferido no navegador; aqui fica
// a conta, que é onde um erro passa calado.

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
  if (JSON.stringify(actual) === JSON.stringify(expected)) return
  falhas += 1
  console.error(`  FALHOU: ${nome}\n    esperado: ${JSON.stringify(expected)}\n    obtido:   ${JSON.stringify(actual)}`)
}

const { createTheme, createElement } = await import('../src/theme-editor/model/defaults.ts')
const { THEME_BRIDGE } = await import('../src/data/knowledge.ts')
const { HINT_BY_KEY } = await import('../src/theme-editor/model/dataSources.ts')
const { CENARIOS, BASE, valoresNoInstante, faixaDeVelocidade } = await import('../src/theme-editor/preview/cenarios.ts')
const { diferenca } = await import('../src/theme-editor/preview/canal.ts')
const sel = await import('../src/theme-editor/model/selecao.ts')
const est = await import('../src/theme-editor/model/estilos.ts')
const { estimarCusto } = await import('../src/theme-editor/model/custo.ts')
const { gerarTema, ESTILOS, ITENS } = await import('../src/theme-editor/assistente/gerarTema.ts')
const { lerCor, contraste, FUNDO_DO_TOM, recolorir, CONTRASTE_MINIMO } = await import('../src/theme-editor/assistente/cores.ts')
const { PRESETS } = await import('../src/theme-editor/presets/index.ts')
const { buildPayload } = await import('../src/theme-editor/emit/payload.ts')
const { emitAppHtml } = await import('../src/theme-editor/emit/emitHtml.ts')
const { firstBlocker, isLegal, movedTo } = await import('../src/theme-editor/model/collision.ts')
const { rectFor } = await import('../src/theme-editor/model/sizes.ts')
const { definirModoLivre } = await import('../src/theme-editor/model/freeMode.ts')

// O armazenamento local não existe no Node; os módulos que o usam têm try/catch, mas os testes de
// blocos e do pacote precisam de um de verdade para ler o que gravaram.
const memoria = new Map()
globalThis.localStorage = {
  getItem: (k) => (memoria.has(k) ? memoria.get(k) : null),
  setItem: (k, v) => memoria.set(k, String(v)),
  removeItem: (k) => memoria.delete(k),
}
const blocos = await import('../src/theme-editor/blocos.ts')
const { empacotar, desempacotar } = await import('../src/theme-editor/compartilhar.ts')

const contrato = new Set(THEME_BRIDGE.keys.map((k) => k.key))
const pos = (el, x, y, size = 'md') => ({ ...el, place: { ...el.place, at: { x, y }, size } })

// ------------------------------------------------------------------ cenários da prévia
// Um cenário com chave fora do contrato mostraria na prévia um dado que o carro nunca manda.
{
  for (const c of CENARIOS) {
    for (const k of Object.keys(c.faixas)) ok(contrato.has(k), `cenário ${c.id}: ${k} está no contrato`)
    ok(c.duracao > 0, `cenário ${c.id}: duração positiva`)
  }
  for (const k of Object.keys(BASE)) ok(contrato.has(k), `repouso: ${k} está no contrato`)
  ok(!Object.keys(BASE).some((k) => k.startsWith('app.phone.')), 'nenhum cenário finge telefone (chega vazio no carro hoje)')

  // Códigos de enum têm de ser os que o carro manda: marcha '3' é P, não 'P'.
  const marchas = Object.keys(HINT_BY_KEY.get('car.basic.gear_status').enumLabels)
  for (const c of CENARIOS) {
    const f = c.faixas['car.basic.gear_status']
    if (f) for (const [, v] of f.pontos) ok(marchas.includes(v), `cenário ${c.id}: marcha "${v}" é um código do carro`)
  }

  const cidade = CENARIOS.find((c) => c.id === 'cidade')
  eq(valoresNoInstante(cidade, 0)['car.basic.vehicle_speed'], '0', 'cidade começa parada')
  eq(valoresNoInstante(cidade, 7)['car.basic.vehicle_speed'], '45', 'número anda em linha reta entre dois pontos (38→52 em 5..9 s)')
  eq(valoresNoInstante(cidade, 25)['car.map.tsr.nav_speed_limit'], '40', 'texto troca de uma vez no ponto')
  eq(valoresNoInstante(cidade, 7)['car.basic.outside_temp'], BASE['car.basic.outside_temp'], 'o que o cenário não mexe fica no repouso')
  eq(faixaDeVelocidade(cidade), [0, 58], 'a legenda da linha do tempo sai da faixa real do cenário')
  const noite = CENARIOS.find((c) => c.id === 'noite')
  eq(noite.modo, 'headlight', 'o cenário Noite liga o seletor em Farol')
  eq(diferenca({ a: '1', b: '2' }, { a: '1', b: '3', c: '4' }), { b: '3', c: '4' }, 'o iframe só recebe o que mudou')
}

// ------------------------------------------------------------------ seleção múltipla
{
  definirModoLivre(false)
  const a = pos(createElement({ kind: 'readout', key: 'car.basic.outside_temp' }), 800, 100, 'sm')
  const b = pos(createElement({ kind: 'readout', key: 'car.basic.total_odometer' }), 1000, 160, 'sm')
  const c = pos(createElement({ kind: 'readout', key: 'car.basic.gear_status' }), 1300, 130, 'sm')
  const els = [a, b, c]
  const ids = els.map((e) => e.id)

  const topo = sel.alinhar(els, ids, 'topo').elementos
  const ys = topo.map((e) => rectFor(e).y)
  ok(ys.every((y) => y === ys[0]), 'alinhar em cima deixa as bordas de cima iguais')
  eq(ys[0], Math.min(...els.map((e) => rectFor(e).y)), 'e na borda de cima da seleção')

  const dist = sel.distribuir(els, ids, 'h').elementos.map(rectFor).sort((p, q) => p.x - q.x)
  const vao1 = dist[1].x - (dist[0].x + dist[0].width)
  const vao2 = dist[2].x - (dist[1].x + dist[1].width)
  ok(Math.abs(vao1 - vao2) <= 1, `distribuir deixa os vãos iguais (${vao1} × ${vao2})`)
  eq([dist[0].x, dist[2].x + dist[2].width], [rectFor(a).x, rectFor(c).x + rectFor(c).width], 'distribuir não mexe nas pontas')
  eq(sel.distribuir(els.slice(0, 2), ids.slice(0, 2), 'h').elementos, els.slice(0, 2), 'distribuir com dois não faz nada')

  // A zona do veículo vale para cada um: alinhar à esquerda empurraria o número para cima do ESP.
  const perto = pos(createElement({ kind: 'readout', key: 'car.basic.vehicle_speed' }), 700, 490, 'md')
  const longe = pos(createElement({ kind: 'readout', key: 'car.basic.total_odometer' }), 360, 250, 'sm')
  const r = sel.alinhar([perto, longe], [perto.id, longe.id], 'esquerda')
  ok(r.barrados.some((x) => x.id === perto.id), 'o que cairia numa zona do veículo fica onde está e é avisado')
  ok(r.elementos.every((e) => isLegal(e)), 'alinhar nunca produz elemento ilegal')

  // `a` iria para cima do READY; os outros dois caberiam. Nenhum vai.
  eq(sel.deslocarJuntos(els, ids, 290 - a.place.at.x, 345 - a.place.at.y), null, 'mover junto é tudo ou nada')
  const movidos = sel.deslocarJuntos(els, ids, 10, 5)
  ok(movidos.every((e, i) => e.place.at.x === els[i].place.at.x + 10 && e.place.at.y === els[i].place.at.y + 5), 'mover junto aplica o mesmo vetor a todos')

  const pagina = { id: 'p', name: 'P', elements: els, groups: [] }
  const g = sel.agrupar(pagina, [a.id, b.id], 'Status')
  eq(g.page.elements.filter((e) => e.groupId === g.grupo.id).length, 2, 'agrupar liga os escolhidos ao grupo')
  const solto = sel.desagrupar(g.page, g.grupo.id)
  ok(solto.elements.every((e) => !e.groupId) && solto.groups.length === 0, 'desagrupar desfaz os dois lados')
  const orfao = sel.limparGrupos({ ...pagina, groups: [{ id: 'x', name: 'Vazio' }], elements: [{ ...a, groupId: 'y' }, b, c] })
  ok(orfao.groups.length === 0 && !orfao.elements[0].groupId, 'grupo vazio e ligação para grupo inexistente somem')

  const copias = sel.copiasPara(pagina, [a, b], 24)
  ok(copias.every((e) => !ids.includes(e.id)), 'cópia tem id novo (vira id de nó no HTML)')
  ok(copias.every((e) => e.place.z > Math.max(...els.map((x) => x.place.z))), 'cópia nasce por cima')
  eq(copias[0].place.at, { x: a.place.at.x + 24, y: a.place.at.y + 24 }, 'cópia deslocada 24 px')
}

// ------------------------------------------------------------------ estilos compartilhados
// A ligação é escrita, não resolvida: mudar o estilo reescreve os textos, e o payload de um tema
// com estilo compartilhado é o mesmo de um tema com os valores copiados à mão.
{
  const t = createTheme('Estilos')
  const l1 = pos(createElement({ kind: 'readout', key: 'car.basic.outside_temp' }), 800, 100)
  const l2 = pos(createElement({ kind: 'readout', key: 'car.basic.total_odometer' }), 1100, 100)
  t.pages[0].elements = [l1, l2]
  const { spec: comEstilo, id } = est.criarEstiloCompartilhado(t, 'Número grande', l1.text)
  const ligado = est.ligarAoEstilo(comEstilo, id)
  let s = { ...comEstilo, pages: [{ ...comEstilo.pages[0], elements: comEstilo.pages[0].elements.map((e) => est.comTexto(e, 'text', ligado)) }] }
  eq(est.usosDosEstilos(s)[id], 2, 'os dois textos ficam ligados')
  s = est.atualizarEstiloCompartilhado(s, id, { ...ligado, sizePx: 88, color: '#ff0000' })
  ok(s.pages[0].elements.every((e) => e.text.sizePx === 88 && e.text.color === '#ff0000' && e.text.shared === id), 'mudar o estilo muda todos os textos ligados')
  ok(s.pages[0].elements.every((e) => e.labelStyle.sizePx !== 88), 'a legenda, que não está ligada, não muda')

  const manual = { ...t, pages: [{ ...t.pages[0], elements: t.pages[0].elements.map((e) => ({ ...e, text: { ...e.text, sizePx: 88, color: '#ff0000' } })) }] }
  const semId = (p) => JSON.stringify(p.pages[0].elements.map(({ id: _i, ...r }) => r))
  eq(semId(buildPayload(s)), semId(buildPayload(manual)), 'o payload não sabe que estilo compartilhado existe')

  const apagado = est.apagarEstiloCompartilhado(s, id)
  ok(!apagado.textStyles[id] && apagado.pages[0].elements.every((e) => !e.text.shared && e.text.sizePx === 88), 'apagar o estilo mantém os valores e só solta a ligação')
}

// ------------------------------------------------------------------ elementos novos no payload
{
  const t = createTheme('Novos')
  const anel = pos(createElement({ kind: 'dial', style: 'ring' }), 960, 330, 'sm')
  const marcha = pos(createElement({ kind: 'readout', style: 'gear' }), 960, 60, 'sm')
  const graf = { ...pos(createElement({ kind: 'chart' }), 1560, 560, 'lg'), windowSec: 600 }
  const forma = pos(createElement({ kind: 'shape', style: 'line' }), 960, 640, 'lg')
  const img = pos(createElement({ kind: 'image' }), 1560, 200)
  const oculto = { ...pos(createElement({ kind: 'readout', key: 'car.hvac.fan_speed' }), 800, 200, 'sm'), hidden: true }
  t.pages[0].elements = [anel, marcha, graf, forma, img, oculto]
  t.pages.push({ id: 'pg-re', name: 'Ré', elements: [], showWhen: { when: 'equals', key: 'car.ev_setting.energy_recovery_level', value: '1' } })
  t.welcomeSweep = true
  const p = buildPayload(t)
  const el = (id) => p.pages[0].elements.find((e) => e.id === id)

  ok(!el(oculto.id), 'elemento oculto não vai para o app.html')
  ok(!p.subscribeKeys.includes('car.hvac.fan_speed'), 'nem a chave dele é assinada')
  ok(p.subscribeKeys.includes('car.ev_setting.energy_recovery_level'), 'a condição de uma página automática é assinada mesmo sem elemento')
  eq(p.pages[1].showWhen, t.pages[1].showWhen, 'a condição da página viaja no payload')
  ok(p.pages[0].showWhen === undefined && p.pages[0].bg === undefined, 'página sem nada disso gera o payload de sempre')
  ok(el(anel.id).parts.arc && !el(anel.id).parts.needle && !el(anel.id).parts.ticks, 'anel: só o arco')
  ok(typeof el(anel.id).arcTrack === 'string', 'anel: trilho atrás do arco')
  ok(p.pages[0].elements.filter((e) => e.kind === 'dial' && e.id !== anel.id).every((e) => e.arcTrack === undefined), 'mostrador sem trilho não ganha campo novo')
  eq(el(marcha.id).binding.key, 'car.basic.gear_status', 'marcha em destaque lê a marcha')
  eq(el(graf.id).points, 120, 'mini-histórico guarda no máximo 120 pontos')
  eq(el(graf.id).stepSec, 5, 'janela de 10 min amostra de 5 em 5 s')
  ok(el(img.id).src.startsWith('data:image/'), 'imagem viaja embutida')
  eq(el(forma.id).shape, 'line', 'forma leva o tipo')
  eq(p.welcomeSweep, true, 'varredura de boas-vindas viaja quando ligada')
  eq(buildPayload(createTheme('x')).welcomeSweep, undefined, 'e não viaja quando desligada')

  ok(!firstBlocker(forma), 'forma nunca bloqueia (não é conteúdo legível)')
  const imgNoReady = movedTo(img, { x: 290, y: 345 })
  ok(firstBlocker(imgNoReady) !== null, 'imagem é conteúdo: bloqueia em cima do READY')

  const html = emitAppHtml(t, { backgroundCss: '#000' })
  const script = html.slice(html.indexOf('<script>') + 8, html.indexOf('</script>', html.indexOf('<script>')))
  let compilou = true
  try { new Function(script) } catch (e) { compilou = false; console.error('    ' + e.message) }
  ok(compilou, 'o motor com os renderizadores novos compila')
  ok(/preview: \{/.test(html) && /simulacao: function/.test(html), 'o motor expõe a API da prévia ao editor')
  ok(/if \(GRAFICOS\.length\) setInterval\(amostrarGraficos, 1000\)/.test(html), 'um temporizador só para todos os gráficos, e só se houver gráfico')
  ok(/function avaliarPaginasAutomaticas/.test(html) && /if \(vale === a\.valia\) continue/.test(html), 'página automática troca na borda da condição')
  ok(/if \(!THEME \|\| !THEME\.welcomeSweep \|\| RM/.test(html), 'a varredura respeita reduzir movimento')
}

// ------------------------------------------------------------------ custo no painel
{
  const niveis = Object.fromEntries(PRESETS.map((p) => [p.id, estimarCusto(p.spec).nivel]))
  ok(Object.values(niveis).every((n) => n !== 'pesado'), `os temas prontos rodam no carro, então não são "pesados" (${JSON.stringify(niveis)})`)
  const t = createTheme('Pesado')
  const vidro = (x) => {
    const d = pos(createElement({ kind: 'dial', key: 'car.basic.vehicle_speed' }), x, 360, 'md')
    return { ...d, face: { ...d.face, enabled: true, blurPx: 30 }, binding: { ...d.binding, smoothMs: 600 } }
  }
  t.pages[0].elements = [vidro(700), vidro(1100), vidro(1500), pos(createElement({ kind: 'chart' }), 960, 650)]
  const c = estimarCusto(t)
  eq(c.nivel, 'pesado', 'três vidros de 30 px com suavização é pesado')
  ok(c.motivos[0].includes('vidro'), 'o motivo principal vem primeiro')
  ok(c.sugestao && c.sugestao.includes('desfoque'), 'e a sugestão diz o que tirar')
  eq(estimarCusto(createTheme('vazio')).nivel, 'leve', 'tema vazio é leve')
}

// ------------------------------------------------------------------ assistente
// O assistente não pode gerar nada que o editor recusaria: elemento em cima de zona do veículo,
// chave fora do contrato, destaque sem contraste.
{
  const todos = ITENS.map((i) => i.id)
  for (const e of ESTILOS) {
    for (const tom of ['escuro', 'claro']) {
      for (const itens of [todos, ['velocidade'], ['velocidade', 'potencia', 'musica']]) {
        const { spec } = gerarTema({ nome: 'A', estilo: e.id, destaque: '#ffd60a', tom, itens })
        const els = spec.pages[0].elements
        const nome = `${e.id}/${tom}/${itens.length}`
        ok(els.every((x) => !firstBlocker(x)), `assistente ${nome}: nada em cima de zona do veículo`)
        const chaves = buildPayload(spec).subscribeKeys
        ok(chaves.every((k) => contrato.has(k)), `assistente ${nome}: toda chave no contrato`)
        const destaque = lerCor(spec.palette.tokens.accent.day)
        ok(contraste(destaque, FUNDO_DO_TOM[tom]) >= CONTRASTE_MINIMO - 0.01, `assistente ${nome}: destaque contrasta com o fundo`)
      }
    }
  }
  const soVel = gerarTema({ nome: 'V', estilo: 'digital', destaque: '#0a84ff', tom: 'escuro', itens: ['velocidade'] }).spec
  ok(!buildPayload(soVel).subscribeKeys.includes('app.media.title'), 'o que não foi marcado não entra (nem a mídia)')
  const claro = gerarTema({ nome: 'C', estilo: 'classico', destaque: '#0a84ff', tom: 'claro', itens: ['velocidade'] }).spec
  ok(lerCor(claro.palette.tokens.ink.day) && contraste(lerCor(claro.palette.tokens.ink.day), FUNDO_DO_TOM.claro) >= 7, 'tom claro: texto escuro legível')
  eq(claro.palette.tokens.ink.night, PRESETS[0].spec.palette.tokens.ink.night, 'as cores de noite não mudam: são do motorista')
  const r = recolorir(PRESETS[0].spec.palette, '#101010', 'escuro')
  ok(r.ajustada, 'um destaque escuro sobre fundo escuro é ajustado e o ajuste é avisado')
}

// ------------------------------------------------------------------ blocos
{
  const prontos = blocos.listarBlocos().filter((b) => b.pronto)
  eq(prontos.length, 6, 'os seis blocos prontos resolvem nos presets (id de elemento renomeado quebraria um)')
  const t = createTheme('Blocos')
  const potencia = prontos.find((b) => b.id === 'pronto-potencia')
  const r = blocos.inserirElementos(t, t.pages[0], potencia.elementos, { grupo: potencia.nome })
  eq(r.ids.length, potencia.elementos.length, 'o bloco entra inteiro')
  ok(r.ids.every((id) => !potencia.elementos.some((e) => e.id === id)), 'com ids novos')
  eq(r.spec.pages[0].groups.length, 1, 'e já agrupado com o nome do bloco')
  const comExo = pos(createElement({ kind: 'readout', key: 'car.basic.outside_temp' }), 900, 100, 'sm')
  comExo.text = { ...comExo.text, font: 'exoV' }
  ok(blocos.inserirElementos(t, t.pages[0], [comExo]).spec.fonts.includes('exoV'), 'a fonte com arquivo entra junto para o texto não cair na do sistema')
  ok(r.spec.pages[0].elements.every((e) => isLegal(e)), 'e em lugar legal')

  const salvo = blocos.salvarBloco('Meu bloco', potencia.elementos)
  ok(blocos.listarBlocos()[0].id === salvo.id, 'o bloco do usuário aparece primeiro')
  blocos.apagarBloco(salvo.id)
  ok(!blocos.listarBlocos().some((b) => b.id === salvo.id), 'e dá para apagar')

  blocos.copiar({ temaId: 'a', paginaId: 'b', elementos: potencia.elementos })
  eq(blocos.lerCopia().elementos.length, potencia.elementos.length, 'copiar e colar sobrevive a trocar de tema')
}

// ------------------------------------------------------------------ pacote .opengw-tema
{
  const t = createTheme('Compartilhado')
  t.pages[0].elements = [pos(createElement({ kind: 'image' }), 1500, 200)]
  const texto = empacotar(t, 'data:image/png;base64,AAAA')
  const volta = desempacotar(texto)
  eq(volta.spec, t, 'o pacote devolve o tema inteiro, imagem embutida inclusive')
  eq(volta.miniatura, 'data:image/png;base64,AAAA', 'e a miniatura')
  eq(desempacotar(JSON.stringify(t)).spec, t, 'o .json antigo (o ThemeSpec puro) também abre')
  let recusou = false
  try { desempacotar('{"nada": 1}') } catch { recusou = true }
  ok(recusou, 'arquivo que não é tema é recusado com mensagem')
}

console.log(`\nCriador de temas: ${falhas ? `${falhas} de ${total} falharam` : `${total}/${total} ok`}`)
if (falhas) process.exitCode = 1
