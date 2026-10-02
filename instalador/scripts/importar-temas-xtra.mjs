// Traz o pacote de temas (a pasta "TEMAS OPEN GW") para dentro do instalador, como catálogo XTRA.
//
//   node scripts/importar-temas-xtra.mjs ["<pasta TEMAS OPEN GW>"]
//
// Copia só o que vai para o carro (temas/<pasta>/{theme.xml,app.html,thumbnail.png}) para
// public/temas-xtra/ e escreve public/temas-xtra/catalogo.json com os grupos de
// _gerador/familias.py. O Vite embute public/ no app, então o catálogo funciona sem internet e o
// envio usa o mesmo /files/put do resto do instalador. Recusa tudo se um tema tiver problema.

import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { perfilDeCor } from './temas-xtra-cor.mjs'
import { ARQUIVOS_DO_TEMA, PASTAS_RESERVADAS, bytesDaPasta, lerThemeXml, pastasDe, problemasDoTema } from './temas-xtra-regras.mjs'

const AQUI = dirname(fileURLToPath(import.meta.url))
const ORIGEM = process.argv[2] ?? join(homedir(), 'Desktop', 'TEMAS OPEN GW')
const DESTINO = join(AQUI, '..', 'public', 'temas-xtra')
const DO_APK = join(AQUI, '..', '..', 'app', 'src', 'main', 'assets', 'PreinstalledThemes')

const NOMES_DE_GRUPO = { 'Os vinte e cinco anteriores': 'Coleção original' }

/** O tipo do grupo vira filtro no catálogo: estilos de desenho, clusters que existiram, marcas. */
const tipoDoGrupo = (nome) => (nome.startsWith('Marcas:') ? 'marcas' : nome.startsWith('Clusters reais') ? 'clusters' : 'estilos')

const slug = (s) =>
  s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')

const falhar = (msg) => {
  console.error(`importar-temas-xtra: ${msg}`)
  process.exit(1)
}

const temas = join(ORIGEM, 'temas')
if (!existsSync(temas)) falhar(`não achei ${temas}`)

const familias = JSON.parse(
  execFileSync('python3', ['-c', 'import json,familias;print(json.dumps(familias.FAM,ensure_ascii=False))'], {
    cwd: join(ORIGEM, '_gerador'),
    encoding: 'utf8',
  }),
)

const versaoDoPacote = (readFileSync(join(ORIGEM, 'README.md'), 'utf8').match(/—\s*v(\d+\.\d+(?:\.\d+)?)/) ?? [])[1] ?? ''
const reservadas = [...PASTAS_RESERVADAS, ...pastasDe(DO_APK)]
const pastas = pastasDe(temas)
const erros = []

const vistos = new Map()
const grupos = familias.map(([nome, , lista]) => {
  const nomeFinal = NOMES_DE_GRUPO[nome] ?? nome
  for (const p of lista) {
    if (vistos.has(p)) erros.push(`${p}: está em "${vistos.get(p)}" e em "${nomeFinal}"`)
    vistos.set(p, nomeFinal)
  }
  return { id: slug(nomeFinal), nome: nomeFinal, tipo: tipoDoGrupo(nomeFinal), temas: lista }
})
for (const p of pastas) if (!vistos.has(p)) erros.push(`${p}: pasta sem grupo em familias.py`)
for (const p of vistos.keys()) if (!pastas.includes(p)) erros.push(`${p}: está em familias.py mas não em temas/`)
for (const p of pastas) erros.push(...problemasDoTema(join(temas, p), p, reservadas))
if (erros.length) falhar(`${erros.length} problema(s):\n  ${erros.join('\n  ')}`)

const catalogo = { versao: versaoDoPacote, total: pastas.length, grupos, temas: {} }
rmSync(DESTINO, { recursive: true, force: true })
for (const p of pastas) {
  mkdirSync(join(DESTINO, p), { recursive: true })
  for (const f of ARQUIVOS_DO_TEMA) copyFileSync(join(temas, p, f), join(DESTINO, p, f))
  const meta = lerThemeXml(readFileSync(join(temas, p, 'theme.xml'), 'utf8'))
  const { fundo, cor } = perfilDeCor(readFileSync(join(temas, p, 'thumbnail.png')))
  catalogo.temas[p] = { nome: meta.nome, versao: meta.versao, descricao: meta.descricao, bytes: bytesDaPasta(join(temas, p)), fundo, cor }
}
writeFileSync(join(DESTINO, 'catalogo.json'), JSON.stringify(catalogo, null, 1) + '\n')

const mb = Object.values(catalogo.temas).reduce((n, t) => n + t.bytes, 0) / 1e6
console.log(`catálogo XTRA v${versaoDoPacote}: ${pastas.length} temas em ${grupos.length} grupos, ${mb.toFixed(1)} MB -> ${DESTINO}`)
