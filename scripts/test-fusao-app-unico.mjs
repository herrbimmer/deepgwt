// A fusão dos dois apps: o launcher 3D virou parte do APK do OPEN GW.
//
// Roda com `node scripts/test-fusao-app-unico.mjs`.
//
// POR QUE ESTE TESTE EXISTE: até a versão 3.4 o launcher era um APK à parte, sem código-fonte
// no repositório — quem mexia nele editava `assets/www` na pasta descompactada e reempacotava
// com `scripts/repack-viewer.sh`. Isso deixou hábitos para trás que, repetidos, desfazem a
// fusão sem quebrar a compilação: editar o `www` do Downloads em vez do que está no repositório,
// devolver `category.LAUNCHER` para a tela inicial (e com isso um segundo ícone no carro),
// apagar as fontes decompiladas achando que são lixo de build, ou reapontar VIEWER_PACKAGE para
// `com.havalh6.viewer`, que não existe mais como pacote instalado.
//
// E um que escapou por uma versão inteira: o INSTALADOR continuava embutindo o APK avulso do
// launcher e instalando o pacote extinto logo depois do OPEN GW. O repositório estava fundido e o
// carro terminava com dois apps assim mesmo — por isso a seção 7 olha para fora do `app/`.
//
// Cada verificação aqui trava um desses caminhos. Nenhuma delas roda o app: são leituras de
// fonte, que é o que pega uma fusão desfeita ANTES de o APK ir para o carro.
//
// O que ele NÃO faz: conferir se a tela inicial 3D funciona. Isso é o emulador que diz.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const ler = (rel) => fs.readFileSync(path.join(raiz, rel), 'utf8')
const existe = (rel) => fs.existsSync(path.join(raiz, rel))

let falhas = 0
const eq = (nome, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want)
  if (!ok) { falhas++; console.log(`FALHOU ${nome}\n  obtido ${JSON.stringify(got)}\n  esperado ${JSON.stringify(want)}`) }
  else console.log(`ok  ${nome}`)
}

// ── 1. As fontes do launcher estão no repositório ──────────────────────────────────────────
const dirLauncher = 'app/src/main/java/com/havalh6/viewer'
eq('fontes do launcher existem', existe(dirLauncher), true)
const fontes = existe(dirLauncher) ? fs.readdirSync(path.join(raiz, dirLauncher)) : []
eq('MainActivity do launcher presente', fontes.includes('MainActivity.java'), true)
// 25 arquivos hoje; o piso pega remoção em massa sem engessar o número exato.
eq('conjunto de classes do launcher completo', fontes.length >= 20, true)

const main = existe(dirLauncher) ? ler(`${dirLauncher}/MainActivity.java`) : ''
// O jadx deixa estes dois marcadores onde falhou. Nenhum pode sobreviver: o primeiro vira
// erro de compilação, o segundo compila e explode em runtime.
const todas = fontes.map((f) => ler(`${dirLauncher}/${f}`)).join('\n')
eq('nenhum "??" de inferência do jadx', /^\s*\?\?\s/m.test(todas), false)
eq('nenhum método não decompilado', todas.includes('Method not decompiled'), false)

// ── 2. Um app, um ícone ────────────────────────────────────────────────────────────────────
const manifesto = ler('app/src/main/AndroidManifest.xml')
const iMain = manifesto.indexOf('android:name="com.havalh6.viewer.MainActivity"')
eq('tela inicial declarada no manifesto', iMain > 0, true)
const blocoHome = manifesto.slice(iMain, manifesto.indexOf('</activity>', iMain))
eq('tela inicial é HOME', blocoHome.includes('android.intent.category.HOME'), true)
eq('tela inicial é DEFAULT', blocoHome.includes('android.intent.category.DEFAULT'), true)
// O ponto da fusão: UM ícone no carro. Quem abre pelo ícone é o LauncherAlias (configurações);
// a tela inicial aparece ao pressionar Home, não por ícone.
eq('tela inicial NÃO tem category.LAUNCHER', blocoHome.includes('android.intent.category.LAUNCHER'), false)
const aliasLauncher = (manifesto.match(/android:name="\.LauncherAlias"/g) || []).length
eq('LauncherAlias continua sendo o ícone', aliasLauncher, 1)

// ── 3. Serviços do launcher e permissões que só ele usa ────────────────────────────────────
for (const s of ['MediaNotificationListener', 'DesktopSwitcherHitService']) {
  eq(`serviço ${s} no manifesto`, manifesto.includes(`com.havalh6.viewer.${s}`), true)
}
// São o segundo listener de notificação e o segundo serviço de acessibilidade do pacote; sem
// label próprio o Android mostra duas linhas idênticas chamadas OPEN GW nas configurações.
eq('listener do launcher tem label próprio', manifesto.includes('@string/rotulo_do_ouvinte_de_midia'), true)
eq('acessibilidade do launcher tem label próprio', manifesto.includes('@string/rotulo_do_seletor_de_desktops'), true)
for (const p of ['GET_TASKS', 'REORDER_TASKS', 'KILL_BACKGROUND_PROCESSES']) {
  eq(`permissão ${p}`, manifesto.includes(`android.permission.${p}`), true)
}
// targetSdk 28 bloqueia http por padrão, e o WebView da tela inicial fala com o GWLink em
// localhost por http. Sem isto a tela inicial sobe sem nenhum dado do carro.
eq('cleartext liberado', manifesto.includes('android:usesCleartextTraffic="true"'), true)

// ── 4. O `www` que vai para o APK é o do repositório ───────────────────────────────────────
eq('assets/www no repositório', existe('app/src/main/assets/www/index.html'), true)
eq('openGwClient.js no repositório', existe('app/src/main/assets/www/openGwClient.js'), true)
const versao = ler('gradle.properties').match(/appVersionName=(.+)/)[1].trim()
const buildJs = ler('app/src/main/assets/www/openGwClient.js').match(/LAUNCHER_BUILD = '([^']*)'/)[1]
// Um app só, uma versão só: a string que a tela inicial mostra é a mesma do APK.
eq('versão do launcher = versão do app', buildJs, versao)

// ── 5. Recursos do launcher ────────────────────────────────────────────────────────────────
// O launcher tem a marca própria (`ic_gwm_viewer.xml`) e divide com o app os ícones de reserva dos
// atalhos (vetores próprios em `res/drawable/`).
for (const n of ['ic_gwm_viewer.xml', 'ic_android_auto_default.xml', 'ic_youtube_default.xml',
                 'ic_youtube_music_default.xml', 'ic_carplay_default.xml']) {
  eq(`recurso ${n}`, existe(`app/src/main/res/drawable/${n}`), true)
}
// O código do launcher tem de apontar para os renomeados, nunca para os do app.
eq('launcher usa ic_gwm_viewer', /R\.drawable\.ic_gwm_viewer/.test(todas), true)
eq('launcher não usa mais ic_gwm do app', /R\.drawable\.ic_gwm(?![A-Za-z0-9_])/.test(todas), false)
// As classes ficaram no pacote com.havalh6.viewer, mas o R é o do app único.
eq('launcher importa o R do app', main.includes('import br.com.opengw.R;'), true)

// ── 6. O lado Kotlin sabe que a tela inicial é deste pacote ────────────────────────────────
const hlc = ler('app/src/main/java/br/com/opengw/managers/HomeLauncherController.kt')
eq('VIEWER_PACKAGE aponta para o próprio app',
   /const val VIEWER_PACKAGE = br\.com\.opengw\.BuildConfig\.APPLICATION_ID/.test(hlc), true)
eq('VIEWER_PACKAGE não é mais o pacote antigo', hlc.includes('"com.havalh6.viewer"'), false)

// ── 7. O instalador leva um app só ─────────────────────────────────────────────────────────
// A tela inicial 3D é parte do APK do OPEN GW: o instalador não embute nem instala nenhum outro pacote
// além dele e do Shizuku, e não carrega código para remover pacotes de outros projetos.
const tauriConf = ler('instalador/src-tauri/tauri.conf.json')
eq('instalador não embute o APK do launcher', tauriConf.includes('haval-viewer.apk'), false)
const libRs = ler('instalador/src-tauri/src/lib.rs')
eq('instalador não instala o launcher à parte', libRs.includes('install_bundled_viewer'), false)
eq('instalador não remove pacotes de outros projetos', /LEGACY_|remove_legacy/.test(libRs), false)
// O script que monta o instalador não pode voltar a reempacotar a pasta de ~/Downloads: o APK
// que ele produzisse não tem nada do que foi feito no repositório desde a fusão.
const sync = ler('sincronizar-apk.sh')
eq('sincronizar-apk não reempacota mais o launcher', sync.includes('repack-viewer.sh'), false)
eq('sincronizar-apk não aponta mais para ~/Downloads', sync.includes('VIEWER_SRC'), false)

console.log(falhas ? `\n${falhas} FALHA(S)` : '\nTodas passaram')
process.exit(falhas ? 1 : 0)
