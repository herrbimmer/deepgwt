// Catálogo XTRA: o que está em public/temas-xtra bate com o catalogo.json, cada tema passa nas
// regras do carro (Chrome 84, contrato v1.0) e a lógica da tela (situação no carro, busca, lote).

import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { PASTAS_RESERVADAS, bytesDaPasta, lerThemeXml, pastasDe, problemasDoTema } from './temas-xtra-regras.mjs'
import { CORES, lerPngRgb, perfilDeCor } from './temas-xtra-cor.mjs'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RAIZ = join(AQUI, '..', 'public', 'temas-xtra')
const DO_APK = join(AQUI, '..', '..', 'app', 'src', 'main', 'assets', 'PreinstalledThemes')

let ok = 0
const falhas = []
const check = (cond, msg) => (cond ? ok++ : falhas.push(msg))

check(existsSync(join(RAIZ, 'catalogo.json')), 'public/temas-xtra/catalogo.json existe')
const cat = JSON.parse(readFileSync(join(RAIZ, 'catalogo.json'), 'utf8'))
const pastas = pastasDe(RAIZ)
const nosGrupos = cat.grupos.flatMap((g) => g.temas)

check(cat.total === pastas.length, `total ${cat.total} == pastas ${pastas.length}`)
check(cat.total === 275, `275 temas (veio ${cat.total})`)
check(cat.grupos.length === 26, `26 grupos (veio ${cat.grupos.length})`)
check(new Set(nosGrupos).size === nosGrupos.length, 'nenhum tema em dois grupos')
check(nosGrupos.length === pastas.length && pastas.every((p) => nosGrupos.includes(p)), 'todo tema tem grupo e todo grupo aponta para pasta existente')
check(Object.keys(cat.temas).sort().join() === pastas.join(), 'catalogo.temas cobre exatamente as pastas')
check(new Set(cat.grupos.map((g) => g.id)).size === cat.grupos.length, 'ids de grupo únicos')
check(cat.grupos[0].nome === 'Coleção original', 'primeiro grupo renomeado para Coleção original')
check(readdirSync(RAIZ).filter((f) => !pastas.includes(f)).join() === 'catalogo.json', 'só pastas de tema e o catalogo.json')

const reservadas = [...PASTAS_RESERVADAS, ...pastasDe(DO_APK)]
const problemas = pastas.flatMap((p) => problemasDoTema(join(RAIZ, p), p, reservadas))
check(problemas.length === 0, `regras do carro: ${problemas.slice(0, 5).join('; ')}`)
for (const p of pastas) {
  const meta = lerThemeXml(readFileSync(join(RAIZ, p, 'theme.xml'), 'utf8'))
  const t = cat.temas[p]
  check(t && t.nome === meta.nome && t.versao === meta.versao, `${p}: nome/versão do catálogo == theme.xml`)
  check(t && t.bytes === bytesDaPasta(join(RAIZ, p)), `${p}: bytes`)
}

// Filtros: tipo do grupo, fundo e cor gravados na importação e coerentes com a miniatura.
const TIPOS = { estilos: 13, clusters: 3, marcas: 10 }
for (const [tipo, n] of Object.entries(TIPOS)) check(cat.grupos.filter((g) => g.tipo === tipo).length === n, `${n} grupos do tipo ${tipo}`)
check(cat.grupos.filter((g) => g.nome.startsWith('Marcas:')).every((g) => g.tipo === 'marcas'), 'grupos "Marcas:" são do tipo marcas')
for (const p of pastas) {
  const t = cat.temas[p]
  check(['escuro', 'claro'].includes(t.fundo) && CORES.includes(t.cor), `${p}: fundo/cor válidos (${t.fundo}/${t.cor})`)
  const r = perfilDeCor(readFileSync(join(RAIZ, p, 'thumbnail.png')))
  check(r.fundo === t.fundo && r.cor === t.cor, `${p}: fundo/cor do catálogo == miniatura`)
}
check(cat.temas.bauhaus.fundo === 'claro' && cat.temas.agulha.fundo === 'escuro', 'fundo: bauhaus claro, agulha escuro')
check(cat.temas.ambar.cor === 'ambar' && cat.temas['bavaro-eletrico'].cor === 'verde' && cat.temas.agulha.cor === 'mono', 'cor: ambar, bavaro-eletrico verde, agulha mono')
check(new Set(pastas.map((p) => cat.temas[p].cor)).size >= 6, 'as cores se espalham (não caiu tudo numa só)')

// Miniaturas reais dos temas Legacy (não mais o desenho genérico de 200x200).
const LEGACY_DIR = join(AQUI, '..', 'public', 'temas-legacy')
for (const p of pastasDe(DO_APK)) {
  const apk = readFileSync(join(DO_APK, p, 'thumbnail.png'))
  const { largura, altura } = lerPngRgb(apk)
  check(largura === 480 && altura === 180, `${p}: miniatura do APK 480x180 (veio ${largura}x${altura})`)
  check(existsSync(join(LEGACY_DIR, `${p}.png`)) && readFileSync(join(LEGACY_DIR, `${p}.png`)).equals(apk), `${p}: public/temas-legacy igual ao APK (rode copiar-miniaturas-legacy.mjs)`)
}
const PRESETS = { 'cupertino-ultra': 'cupertinoultra', minimalista: 'minimalista' }
for (const [pub, apk] of Object.entries(PRESETS)) {
  check(
    readFileSync(join(AQUI, '..', 'public', 'themes', pub, 'thumbnail.png')).equals(readFileSync(join(DO_APK, apk, 'thumbnail.png'))),
    `public/themes/${pub}: mesma miniatura do APK`,
  )
}
// O NTG do motor saiu do APK na 7.6; a miniatura da predefinição segue a da referência.
const REF_NTG = join(AQUI, '..', '..', 'scripts', 'referencia', 'stuttgart-ntg55')
check(
  readFileSync(join(AQUI, '..', 'public', 'themes', 'stuttgart-ntg55', 'thumbnail.png')).equals(readFileSync(join(REF_NTG, 'thumbnail.png'))),
  'public/themes/stuttgart-ntg55: mesma miniatura da referência',
)
check(!existsSync(join(DO_APK, 'stuttgart-ntg55')), 'o Stuttgart NTG 5.5 do motor não viaja mais no APK (7.6)')
check(!existsSync(join(LEGACY_DIR, 'stuttgart-ntg55.png')), 'sem miniatura Legacy do NTG do motor no instalador (7.6)')

// A lógica da tela.
const m = await import('../src/temas-xtra/catalogoXtra.ts')
const { castaDe } = await import('../src/theme-editor/carro/temasNoCarro.ts')
const tema = { nome: 'Agulha', versao: '1.2.0', descricao: '', bytes: 1 }
const noCarro = (versao) => new Map([['agulha', { pasta: 'agulha', nome: 'Agulha', versao, descricao: '', embutido: false }]])
check(m.situacaoNoCarro('agulha', tema, null) === 'ausente', 'sem leitura do carro: ausente')
check(m.situacaoNoCarro('agulha', tema, new Map()) === 'ausente', 'fora do carro: ausente')
check(m.situacaoNoCarro('agulha', tema, noCarro('1.2.0')) === 'no-carro', 'mesma versão: no carro')
check(m.situacaoNoCarro('agulha', tema, noCarro('1.1.9')) === 'desatualizado', 'carro mais velho: desatualizado')
check(m.situacaoNoCarro('agulha', tema, noCarro('1.10.0')) === 'no-carro', 'comparação numérica, não de texto')
check(m.ORDEM_DE_ENVIO.at(-1) === 'theme.xml', 'theme.xml vai por último')
check(m.filtrarTemas(cat, pastas, 'bavaro').length >= 10, 'busca sem acento acha os Bávaro')
check(m.filtrarTemas(cat, pastas, '').length === pastas.length, 'busca vazia devolve tudo')
check(m.formatarBytes(1_550_000) === '1,6 MB' && m.formatarBytes(38_400) === '38 kB', 'formatarBytes')

const sit = () => 'ausente'
const sitMap = (m) => (p) => m[p] ?? 'ausente'
check(m.filtrarTemas(cat, pastas, 'bavaro laranja').includes('bavaro-laranja'), 'busca com duas palavras')
check(m.filtrarTemas(cat, pastas, 'laranja bavaro').includes('bavaro-laranja'), 'busca: ordem das palavras não importa')
check(m.filtrarTemas(cat, pastas, 'aviacao').includes('pfd'), 'busca acha pelo nome do grupo')
check(m.filtrarTemas(cat, pastas, 'xyzqw').length === 0, 'busca sem resultado')
const f = (x) => ({ ...m.SEM_FILTROS, ...x })
check(m.aplicarFiltros(cat, m.SEM_FILTROS, sit).length === 275, 'sem filtro: 275')
check(m.aplicarFiltros(cat, f({ tipo: 'marcas' }), sit).length === 100, 'tipo marcas: 100')
check(m.aplicarFiltros(cat, f({ tipo: 'clusters' }), sit).length === 30, 'tipo clusters: 30')
check(m.aplicarFiltros(cat, f({ grupo: 'colecao-original' }), sit).length === 25, 'grupo coleção original: 25')
check(m.aplicarFiltros(cat, f({ tipo: 'marcas', grupo: 'colecao-original' }), sit).length === 0, 'tipo e grupo se somam')
const claros = m.aplicarFiltros(cat, f({ fundo: 'claro' }), sit)
check(claros.length > 0 && claros.every((p) => cat.temas[p].fundo === 'claro'), 'fundo claro')
const verdes = m.aplicarFiltros(cat, f({ cor: 'verde', fundo: 'escuro' }), sit)
check(verdes.length > 0 && verdes.every((p) => cat.temas[p].cor === 'verde' && cat.temas[p].fundo === 'escuro'), 'cor + fundo')
const carro = sitMap({ agulha: 'no-carro', zero: 'desatualizado' })
check(m.aplicarFiltros(cat, f({ noCarro: 'no-carro' }), carro).join() === ['zero', 'agulha'].filter((p) => pastas.includes(p)).sort((a, b) => nosGrupos.indexOf(a) - nosGrupos.indexOf(b)).join(), 'no carro inclui os para atualizar')
check(m.aplicarFiltros(cat, f({ noCarro: 'desatualizado' }), carro).join() === 'zero', 'para atualizar')
check(m.aplicarFiltros(cat, f({ noCarro: 'ausentes' }), carro).length === 273, 'não enviados')
check(m.aplicarFiltros(cat, f({ cor: 'verde', tipo: 'marcas' }), sit, 'cor').length === 100, 'ignorar uma faceta para contar')
check(m.filtrosAtivos(f({ busca: ' ', tipo: 'marcas', noCarro: 'ausentes' })) === 2, 'filtrosAtivos ignora busca em branco')
const porNome = m.ordenarPastas(cat, pastas, 'nome').map((p) => cat.temas[p].nome)
check(porNome.every((n, i) => i === 0 || porNome[i - 1].localeCompare(n, 'pt-BR') <= 0), 'ordenar por nome')
const leves = m.ordenarPastas(cat, pastas, 'leves').map((p) => cat.temas[p].bytes)
check(leves.every((b, i) => i === 0 || leves[i - 1] <= b), 'ordenar mais leves primeiro')
check(m.ordenarPastas(cat, pastas, 'grupo') === pastas, 'ordem do catálogo não mexe')
const cont = m.contarPor(pastas, (p) => cat.temas[p].fundo)
check((cont.get('escuro') ?? 0) + (cont.get('claro') ?? 0) === 275, 'contarPor soma o total')

const { miniaturaLegacyDe } = await import('../src/theme-editor/carro/temasNoCarro.ts')
check(miniaturaLegacyDe('coffe3') === '/temas-legacy/coffe3.png' && miniaturaLegacyDe('agulha') === null, 'miniaturaLegacyDe')
// Um tema novo no APK (a 7.4 somou o stuttgart-mbux) tem de entrar também em PASTAS_LEGACY.
check(pastasDe(DO_APK).every((p) => miniaturaLegacyDe(p) === `/temas-legacy/${p}.png`), 'PASTAS_LEGACY cobre todas as pastas do APK')

check(castaDe({ pasta: 'coffe3', nome: '', versao: '', descricao: '', embutido: false }) === 'legacy', 'castaDe: carro antigo, pasta do APK')
check(castaDe({ pasta: 'agulha', nome: '', versao: '', descricao: '', embutido: false }) === 'xtra', 'castaDe: carro antigo, pasta enviada')
check(castaDe({ pasta: 'coffe3', nome: '', versao: '', descricao: '', embutido: false, casta: 'xtra' }) === 'xtra', 'castaDe: o carro manda')

const ordem = []
const lote = await m.emLote(['a', 'b', 'c'], async (p) => {
  if (p === 'b') throw new Error('falhou')
  ordem.push(p)
}, () => {})
check(lote.feitos.join() === 'a,c' && lote.falhas.length === 1 && lote.falhas[0].pasta === 'b', 'lote segue depois de uma falha')
let n = 0
const parado = await m.emLote(['a', 'b', 'c'], async () => void n++, () => {}, () => n >= 1)
check(parado.feitos.length === 1, 'cancelar para antes do próximo')

let recusou = false
try {
  await m.enviarTemaXtra('1.2.3.4', cat, '../../etc')
} catch {
  recusou = true
}
check(recusou, 'enviarTemaXtra recusa pasta fora do catálogo')

// O carro sabe separar as castas (app 7.0).
const kt = (f) => readFileSync(join(AQUI, '..', '..', 'app', 'src', 'main', 'java', 'br', 'com', 'opengw', f), 'utf8')
check(/put\("casta", t\.casta\.id\)/.test(kt('managers/GwLinkServer.kt')), 'GET /temas manda a casta')
check(/CastaDoTema\.values\(\)\.forEach/.test(kt('ui/telas/TelasScreen.kt')), 'Telas desenha uma fileira por casta')
check(/LazyRow\(/.test(kt('ui/telas/TelasScreen.kt')), 'fileira de temas é preguiçosa')

console.log(`temas-xtra: ${ok} ok, ${falhas.length} falha(s)`)
if (falhas.length) {
  falhas.slice(0, 30).forEach((f) => console.log('  FALHOU:', f))
  process.exit(1)
}
