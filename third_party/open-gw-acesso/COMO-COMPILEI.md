# Como compilei o Shizuku `br.com.opengw.shizuku`

Fonte: https://github.com/RikkaApps/Shizuku, commit b844bc491f1790c72328e1a8e5b2349f8978f0ea (18/06/2025, 5 commits depois da tag v13.6.0).
Submodulo `api` (RikkaApps/Shizuku-API, MIT): a27f6e4151ba7b39965ca47edb2bf0aeed7102e5 (sem alteracao).
Licenca: Apache-2.0 (LICENSE). Nao ha arquivo NOTICE no repositorio. `api/` e MIT (LICENSE-Shizuku-API-MIT).
README do projeto (secao License): proibe usar o icone ic_launcher*.png fora do Shizuku, o nome "Shizuku" como nome do app, o id `moe.shizuku.privileged.api` e declarar permissoes `moe.shizuku.manager.permission.*`. Ver riscos no relatorio.

## Ferramentas
- JDK 21.0.10 (Android Studio JBR): export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
- Gradle 8.14 (wrapper), AGP 8.10.1, Kotlin 2.1.21
- SDK ~/Library/Android/sdk: platform android-36, build-tools 36.0.0, CMake 3.31.6, NDK 29.0.13113456 (o que o build.gradle pede; instalado com `sdkmanager "ndk;29.0.13113456"`)

## Passos
```
mkdir -p ~/Library/Caches/opengw-shizuku && cd ~/Library/Caches/opengw-shizuku
git clone https://github.com/RikkaApps/Shizuku src && cd src
git checkout b844bc491f1790c72328e1a8e5b2349f8978f0ea
git config submodule.api.url https://github.com/RikkaApps/Shizuku-API.git   # .gitmodules usa ssh
git submodule update --init --recursive
git rm -q manager/src/main/res/mipmap-*/ic_launcher*.png manager/src/main/res/mipmap-xhdpi/banner.png   # icones/banner herdados do Shizuku (README proibe reuso)
git apply ../saida/opengw-shizuku.patch       # id, nome, icone, minSdk 28, textos
keytool -genkeypair -keystore ../chave-opengw-shizuku.jks -storepass opengw123 -keypass opengw123 \
  -alias opengw -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=OPEN GW Shizuku, O=OPEN GW, C=BR"
cat > signing.properties <<X
KEYSTORE_FILE=$HOME/Library/Caches/opengw-shizuku/chave-opengw-shizuku.jks
KEYSTORE_PASSWORD=opengw123
KEYSTORE_ALIAS=opengw
KEYSTORE_ALIAS_PASSWORD=opengw123
X
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME=~/Library/Android/sdk
./gradlew --no-daemon :manager:assembleRelease
# saida: manager/build/outputs/apk/release/shizuku-v13.6.0.r1091.b844bc49-release.apk (ja assinado com signing.properties)
# starter e .so: unzip -p <apk> lib/<abi>/libshizuku.so
```
signing.properties e local.properties sao ignorados pelo git. Keystore: senha de store e chave `opengw123`, alias `opengw`.
versionCode/versionName vem da contagem de commits (1091 no clone completo).

## Mudancas (ver opengw-shizuku.patch)
Id: manager/build.gradle (applicationId), server ServerConstants.MANAGER_APPLICATION_ID, manager/src/main/jni/starter.cpp (PACKAGE_NAME, usado em `pm path`), starter ServiceStarter (autoridade do provider `<id>.shizuku`), shell ShizukuShellLoader (setPackage do REQUEST_BINDER).
Nome: `app_name` = "OPEN GW Acesso" em values/, values-fa/, values-hu/ (eram "Shizuku"/"Shizuku HU"); nas strings de UI que nomeiam o app/servico (action_stop, dialog_stop_message, notification_*, permission_*, adb_pairing_tutorial_*, dialog_*legacy*) "Shizuku" -> "OPEN GW Acesso" em todos os idiomas; titulo do dialogo em RequestPermissionActivity.kt usa @string/app_name. Textos explicativos (aprender, root, terminal, etc.) continuam citando o projeto Shizuku.
Icone: removidos mipmap-*/ic_launcher*.png (todas as densidades, round/foreground/background), mipmap-xhdpi/banner.png, drawable/ic_launcher.xml, drawable-v26/ic_launcher.xml, drawable/ic_monochrome.xml; novos drawable/ic_opengw_acesso.xml (adaptativo), ..._primeiro_plano.xml e ..._monocromo.xml (monograma OG proprio, escala 0,85 centrado => dentro da zona segura 66/108), values/opengw_icone.xml (fundo #14171B). ic_system_icon (logo do Shizuku usado em notificacao/dialogo/permissao) trocado pelo mesmo monograma em 24dp. Atributos android:banner removidos do manifesto (leanback desligado no projeto).
minSdk do manager sobe de 24 para 28 (carro = Android 9); com isso nao ha PNG para API<26. Efeito colateral: classes.dex e resources.arsc ficam sem compressao no APK (APK ~3,3 MB); extractNativeLibs continua true (o starter e executado de nativeLibraryDir).
O patch NAO inclui os PNGs removidos (para nao redistribuir os icones); eles saem pelo `git rm` acima.
Mantido igual de proposito: permissao `moe.shizuku.manager.permission.API_V23` (+ MANAGER e o grupo `moe.shizuku.manager.permission-group.API`), extra `moe.shizuku.privileged.api.intent.extra.BINDER` (contrato com apps cliente), `rikka.shizuku.intent.action.REQUEST_BINDER`. Ver AVISO-OPEN-GW-ACESSO.md.

## Starter
`shizuku-starter` = lib/arm64-v8a/libshizuku.so do APK (ELF aarch64 PIE stripped, release). Ele consulta `pm path br.com.opengw.shizuku`.
