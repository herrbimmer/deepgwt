// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// A fusão dos dois apps: o launcher 3D virou parte do APK do DeepGWT.
//
// Desde a tela 3D nova a Activity HOME é `br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity` e o
// código dela está em `app/src/main/java/br/com/deepgwt/tela3d/**` (Kotlin, escrito do zero). As
// verificações abaixo travam o MESMO que antes (um app, um ícone, serviços com label próprio, `www`
// do repositório, recursos, instalador sem APK avulso), agora sobre o código novo.
//
// Roda com `node scripts/test-fusao-app-unico.mjs`.
//
// POR QUE ESTE TESTE EXISTE: até a versão 3.4 o launcher era um APK à parte, sem código-fonte
// no repositório — quem mexia nele editava `assets/www` na pasta descompactada e reempacotava
// com `scripts/repack-viewer.sh`. Isso deixou hábitos para trás que, repetidos, desfazem a
// fusão sem quebrar a compilação: editar o `www` do Downloads em vez do que está no repositório,
// devolver `category.LAUNCHER` para a tela inicial (e com isso um segundo ícone no carro),
// apagar o pacote da tela 3D achando que é lixo de build, ou reapontar VIEWER_PACKAGE para
// `com.havalh6.viewer`, que não existe mais como pacote instalado.
//
// E um que escapou por uma versão inteira: o INSTALADOR continuava embutindo o APK avulso do
// launcher e instalando o pacote extinto logo depois do DeepGWT. O repositório estava fundido e o
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

// ── 1. As fontes da tela 3D estão no repositório ───────────────────────────────────────────
const dirTela3d = 'app/src/main/java/br/com/deepgwt/tela3d'
eq('fontes da tela 3D existem', existe(dirTela3d), true)
const modulos = ['ponte', 'janelas', 'catalogo', 'faixa', 'cartoes', 'midia', 'viagens', 'integracao']
for (const m of modulos) eq(`módulo ${m} presente`, existe(`${dirTela3d}/${m}`) && fs.readdirSync(path.join(raiz, dirTela3d, m)).length > 0, true)
eq('Activity HOME da tela 3D presente', existe(`${dirTela3d}/ponte/TelaInicial3dActivity.kt`), true)
eq('fábrica real das dependências presente', existe(`${dirTela3d}/integracao/DependenciasDaTela3dReal.kt`), true)

/** Todos os .kt de uma pasta, recursivamente. */
const fontesKt = (rel) => fs.readdirSync(path.join(raiz, rel), { withFileTypes: true }).flatMap((d) =>
  d.isDirectory() ? fontesKt(`${rel}/${d.name}`) : d.name.endsWith('.kt') ? [`${rel}/${d.name}`] : [])
const arquivos = existe(dirTela3d) ? fontesKt(dirTela3d) : []
// 100 arquivos hoje; o piso pega remoção em massa sem engessar o número exato.
eq('conjunto de classes da tela 3D completo', arquivos.length >= 80, true)
const todas = arquivos.map((f) => ler(f)).join('\n')
// Código novo não pode voltar a depender do pacote antigo (nem por import nem por nome de classe).
eq('o código novo não cita com.havalh6', /com\.havalh6/.test(todas), false)
// A fábrica é registrada pela Application ANTES de qualquer Activity: sem isso a tela abre só com a página.
eq('a Application registra a fábrica real', ler('app/src/main/java/br/com/deepgwt/App.kt').includes('FabricaDaTela3dReal.registrar()'), true)

// ── 2. Um app, um ícone ────────────────────────────────────────────────────────────────────
const manifesto = ler('app/src/main/AndroidManifest.xml')
const iMain = manifesto.indexOf('android:name="br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity"')
eq('tela inicial declarada no manifesto', iMain > 0, true)
const blocoHome = manifesto.slice(iMain, manifesto.indexOf('</activity>', iMain))
eq('tela inicial é HOME', blocoHome.includes('android.intent.category.HOME'), true)
eq('tela inicial é DEFAULT', blocoHome.includes('android.intent.category.DEFAULT'), true)
// O ponto da fusão: UM ícone no carro. Quem abre pelo ícone é o LauncherAlias (configurações);
// a tela inicial aparece ao pressionar Home, não por ícone.
eq('tela inicial NÃO tem category.LAUNCHER', blocoHome.includes('android.intent.category.LAUNCHER'), false)
const aliasLauncher = (manifesto.match(/android:name="\.LauncherAlias"/g) || []).length
eq('LauncherAlias continua sendo o ícone', aliasLauncher, 1)
eq('um só HOME no manifesto', (manifesto.match(/android\.intent\.category\.HOME/g) || []).length, 1)

// ── 3. Serviços da tela 3D e permissões que só ela usa ─────────────────────────────────────
// Os dois serviços apontam para as classes novas. As cascas com o nome antigo
// (`com.havalh6.viewer.*`, que preservam as permissões já concedidas no carro) são do orquestrador:
// o manifesto deixa o aviso escrito.
for (const s of ['DesktopSwitcherHitService', 'MediaNotificationListener']) {
  eq(`casca ${s} no manifesto`, manifesto.includes(`android:name="com.havalh6.viewer.${s}"`), true)
  eq(`casca ${s} existe`, existe(`app/src/main/java/com/havalh6/viewer/${s}.kt`), true)
}
const manifestoSemComentarios = manifesto.replace(/<!--[\s\S]*?-->/g, '')
eq('manifesto só cita o pacote antigo nas duas cascas',
   [...new Set(manifestoSemComentarios.match(/com\.havalh6[\w.]*/g) || [])].sort().join(','),
   'com.havalh6.viewer.DesktopSwitcherHitService,com.havalh6.viewer.MediaNotificationListener')
// São o segundo listener de notificação e o segundo serviço de acessibilidade do pacote; sem
// label próprio o Android mostra duas linhas idênticas chamadas DeepGWT nas configurações.
eq('listener da tela inicial tem label próprio', manifesto.includes('@string/rotulo_do_ouvinte_de_midia'), true)
eq('acessibilidade da tela inicial tem label próprio', manifesto.includes('@string/rotulo_do_seletor_de_desktops'), true)
for (const p of ['GET_TASKS', 'REORDER_TASKS', 'KILL_BACKGROUND_PROCESSES']) {
  eq(`permissão ${p}`, manifesto.includes(`android.permission.${p}`), true)
}
// targetSdk 28 bloqueia http por padrão, e o WebView da tela inicial fala com o GWLink em
// localhost por http. Sem isto a tela inicial sobe sem nenhum dado do carro.
eq('cleartext liberado', manifesto.includes('android:usesCleartextTraffic="true"'), true)
// O R8 não pode podar a tela 3D (reflexão sobre API oculta e métodos só o JavaScript chama).
const proguard = ler('app/proguard-rules.pro')
eq('proguard mantém o pacote da tela 3D', proguard.includes('-keep class br.com.deepgwt.tela3d.** {'), true)
eq('proguard mantém os @JavascriptInterface da tela 3D',
   /-keepclassmembers class br\.com\.deepgwt\.tela3d\.\*\* \{\s*@android\.webkit\.JavascriptInterface <methods>;/.test(proguard), true)

// ── 4. O `www` que vai para o APK é o do repositório ───────────────────────────────────────
eq('assets/www no repositório', existe('app/src/main/assets/www/index.html'), true)
eq('deepGwtClient.js no repositório', existe('app/src/main/assets/www/deepGwtClient.js'), true)
const versao = ler('gradle.properties').match(/appVersionName=(.+)/)[1].trim()
const buildJs = ler('app/src/main/assets/www/deepGwtClient.js').match(/LAUNCHER_BUILD = '([^']*)'/)[1]
// Um app só, uma versão só: a string que a tela inicial mostra é a mesma do APK.
eq('versão do launcher = versão do app', buildJs, versao)

// ── 5. Recursos da tela 3D ─────────────────────────────────────────────────────────────────
// A tela 3D tem a marca própria (`ic_gwm_viewer.xml`) e divide com o app os ícones de reserva dos
// atalhos (vetores próprios em `res/drawable/`).
for (const n of ['ic_gwm_viewer.xml', 'ic_android_auto_default.xml', 'ic_youtube_default.xml',
                 'ic_youtube_music_default.xml', 'ic_carplay_default.xml']) {
  eq(`recurso ${n}`, existe(`app/src/main/res/drawable/${n}`), true)
}
// O código da tela 3D tem de apontar para os renomeados, nunca para os do app.
eq('tela 3D usa ic_gwm_viewer', /R\.drawable\.ic_gwm_viewer/.test(todas), true)
eq('tela 3D não usa mais ic_gwm do app', /R\.drawable\.ic_gwm(?![A-Za-z0-9_])/.test(todas), false)
// O R é o do app único.
eq('tela 3D importa o R do app', todas.includes('import br.com.deepgwt.R'), true)

// ── 6. O lado Kotlin sabe que a tela inicial é deste pacote ────────────────────────────────
const hlc = ler('app/src/main/java/br/com/deepgwt/managers/HomeLauncherController.kt')
eq('VIEWER_PACKAGE aponta para o próprio app',
   /const val VIEWER_PACKAGE = br\.com\.deepgwt\.BuildConfig\.APPLICATION_ID/.test(hlc), true)
eq('VIEWER_PACKAGE não é mais o pacote antigo', hlc.includes('"com.havalh6.viewer"'), false)
// O componente que a guarda abre é a Activity nova (o antigo só existe como constante de migração).
const guarda = ler('app/src/main/java/br/com/deepgwt/managers/GuardaDoLauncher3D.kt')
eq('COMPONENTE_3D aponta para a Activity nova',
   guarda.includes('const val COMPONENTE_3D = "br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity"'), true)
// Reafirmar o HOME compara o componente completo: o app instalado antes tinha a Activity antiga com o mesmo pacote.
eq('reafirmar o HOME compara o componente completo', hlc.includes('ehATela3dAtual(currentHomeComponent(context))'), true)

// ── 7. O instalador leva um app só ─────────────────────────────────────────────────────────
// A tela inicial 3D é parte do APK do DeepGWT: o instalador não embute nem instala nenhum outro pacote
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
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
