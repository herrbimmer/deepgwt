// Gera a pasta de um tema do editor dentro de `app/src/main/assets/PreinstalledThemes/`.
//
// POR QUE ISSO EXISTE
//
// O Stuttgart NTG 5.5 só existia como predefinição do Theme Lab: para tê-lo no carro era preciso
// abrir o instalador e enviá-lo. Isso significa que uma CORREÇÃO no motor de temas só chegava ao
// carro se o usuário reenviasse o tema à mão. Gerando a pasta aqui, ela viaja dentro do APK e o
// `ThemeManager.seedPreinstalledThemes()` a instala sozinho na primeira abertura depois da
// atualização — desde que a versão do XML seja maior que a que estiver no disco.
//
// A VERSÃO É FIXA, NÃO É "AGORA"
//
// `versionFrom` deriva a versão do instante da última edição. Usar `Date.now()` daria uma versão
// nova a cada execução e o `app.html` gerado deixaria de ser comparável entre duas builds. Então o
// carimbo é literal e sobe junto com a build — é o mesmo número do `appVersionName`.
//
// Uso:
//   node --import ./scripts/ts-resolve-register.mjs scripts/gerar-tema-preinstalado.mjs

import { copyFileSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const AQUI = dirname(fileURLToPath(import.meta.url))
const RAIZ_TOOL = join(AQUI, '..')
const RAIZ_REPO = join(RAIZ_TOOL, '..')
const DESTINO = join(RAIZ_REPO, 'app/src/main/assets/PreinstalledThemes')
const PUBLIC = join(RAIZ_TOOL, 'public')

/** Carimbo desta leva (mesmo de `gradle.properties`: DDMMAAAAHHMM). */
const CARIMBO = '2026-09-29T09:35:00-03:00'

const { emitAppHtml } = await import('../src/theme-editor/emit/emitHtml.ts')
const { emitThemeXml, versionFrom } = await import('../src/theme-editor/emit/emitThemeXml.ts')
const { backgroundCssForCar } = await import('../src/theme-editor/resolveBackground.ts')
const { FONT_FILES } = await import('../src/theme-editor/emit/payload.ts')
const { PRESET_BY_ID } = await import('../src/theme-editor/presets/index.ts')

/**
 * Quais predefinições este script (re)gera dentro do APK.
 *
 * O Stuttgart NTG 5.5 do motor não viaja mais no APK (Alpha 7.6: o NTG que se escolhe é o
 * clássico, `coffe3`); a cópia dele ficou em `scripts/referencia/`. Antes disso ele já tinha SAÍDO
 * desta lista, porque o `app.html` dele recebeu à mão
 * o vão da projeção da central (camada `#bg`, `PROJ_BOX`, `aplicarVaoDaProjecao`) que o motor em
 * TS ainda não tem. Regerá-lo aqui apagaria esse código sem aviso, e o `theme.xml` voltaria para
 * uma versão `1.0.x` MENOR que a 1.4.0 do carro — o seed manteria o arquivo velho. Ele volta para
 * a lista quando o vão for portado para `engineJs.ts`; até lá, `test-theme-engine.mjs` confere que
 * os dados do tema e o bloco de mídia dele continuam iguais aos do gerador.
 */
const A_GERAR = ['preset-minimalist']

for (const id of A_GERAR) {
  const preset = PRESET_BY_ID.get(id)
  if (!preset) {
    console.error(`predefinição desconhecida: ${id}`)
    process.exitCode = 1
    continue
  }
  const spec = { ...preset.spec, updatedAt: Date.parse(CARIMBO) }
  const pasta = join(DESTINO, spec.folderName)
  mkdirSync(pasta, { recursive: true })

  writeFileSync(join(pasta, 'theme.xml'), emitThemeXml(spec))
  writeFileSync(
    join(pasta, 'app.html'),
    emitAppHtml(spec, { backgroundCss: backgroundCssForCar(spec.background) }),
  )

  // Miniatura e papel de parede são bytes prontos em `public/themes/<id>/`; passam intactos —
  // rasterizar de novo um arquivo pronto só perde qualidade.
  const origem = join(PUBLIC, 'themes', spec.folderName)
  copyFileSync(join(origem, 'thumbnail.png'), join(pasta, 'thumbnail.png'))
  if (spec.background.mode === 'passthrough' && spec.background.passthrough) {
    const nome = spec.background.passthrough.fileName
    copyFileSync(join(origem, nome), join(pasta, nome))
  }

  // Só as fontes que o tema declara usar.
  for (const f of spec.fonts) {
    const arq = FONT_FILES[f]
    if (!arq) continue
    copyFileSync(join(PUBLIC, 'fonts', arq.file), join(pasta, arq.file))
  }

  console.log(`${spec.name} -> assets/PreinstalledThemes/${spec.folderName} (v${versionFrom(spec.updatedAt)})`)
}
