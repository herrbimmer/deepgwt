// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.KeyStore
import java.security.MessageDigest

plugins {
    alias(libs.plugins.agp.aplicativo)
    alias(libs.plugins.kotlin.no.android)
    alias(libs.plugins.kotlin.compilador.compose)
    alias(libs.plugins.kotlin.serializacao)
}

// Versão do APK: vem do gradle.properties (`appVersionCode` e `appVersionName`).
val codigoDaVersao: Int = providers.gradleProperty("appVersionCode").map { it.toInt() }.getOrElse(1)
val nomeDaVersao: String = providers.gradleProperty("appVersionName").getOrElse("0.0.1")

// Alpha 8.0: o nível de teste (Informações → Nível, `POST /licenca/nivel-de-teste`). O APK que o
// instalador leva ao carro é o `debug`, então NÃO pode depender de `BuildConfig.DEBUG`: fica
// desligado em tudo, menos no `simulator` e num build pedido com `-Pdeepgwt.nivelDeTeste=true`.
val nivelDeTesteNoDebug: String =
    (providers.gradleProperty("deepgwt.nivelDeTeste").orNull == "true").toString()

// Alpha 8.1: as chaves PÚBLICAS de produção da licença, uma linha "id base64" — o licenciador
// grava este arquivo ao criar a chave. Sem ele o build não cobra nada (ver `licenca/Portao.kt`).
val licencaChavesPublicas: String = run {
    val arquivo = file("licenca/chaves-publicas.txt")
    if (!arquivo.exists()) ""
    else arquivo.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .joinToString(";")
}

// applicationId != namespace de propósito: a central (beantechs) recusa `pm install` de
// qualquer applicationId que ela nunca viu ("beantechs disallow apk <pkg> to be installed!")
// — confirmado 04/10/2026 via telnet root no carro: só br.com.redesurftank.havalshisuku é uma
// identidade já conhecida por ela (br.com.opengw e br.com.deepgwt nunca foram instalados nela).
// Única `val` para o applicationId real: `mascararCertificado` abaixo precisa do MESMO valor
// que `android.defaultConfig.applicationId`, porque `Integridade.revelar` desmascara em
// runtime com `BuildConfig.APPLICATION_ID` — duplicar o literal nos dois lugares já causou um
// descasamento silencioso (quando applicationId != namespace, o runtime manda um valor e o
// Gradle mascarava com outro).
val applicationIdReal = "br.com.redesurftank.havalshisuku"

// Alpha 8.3: a impressão digital (SHA-256) do certificado que assina o APK, para o app conferir
// que não foi reassinado (`licenca/Integridade.kt`). Vai mascarada — a mesma conta de
// `Integridade.mascarar`. Keystore não achada = "" (o app não confere nada).
// `-Pdeepgwt.certificadoEsperado=<64 hex>` troca o valor: só para testar o caminho do APK alterado.
fun impressaoDoCertificado(keystore: File, senha: String?, alias: String?): ByteArray? {
    if (!keystore.isFile || senha.isNullOrEmpty()) return null
    return runCatching {
        val ks = KeyStore.getInstance(keystore, senha.toCharArray())
        val nome = alias?.takeIf { it.isNotBlank() } ?: ks.aliases().toList().firstOrNull()
        ks.getCertificate(nome)?.encoded?.let { MessageDigest.getInstance("SHA-256").digest(it) }
    }.getOrNull()
}

fun mascararCertificado(impressao: ByteArray?): String {
    if (impressao == null || impressao.size != 32) return ""
    val mascara = MessageDigest.getInstance("SHA-256")
        .digest("DEEPGWT-CERT|$applicationIdReal".toByteArray(Charsets.UTF_8))
    return impressao.mapIndexed { i, b -> (b.toInt() xor mascara[i].toInt()) and 0xff }.joinToString("") { "%02x".format(it) }
}

val certificadoForcado: ByteArray? = providers.gradleProperty("deepgwt.certificadoEsperado").orNull
    ?.trim()?.takeIf { it.length == 64 }
    ?.let { hex -> ByteArray(32) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() } }

// Onde o AGP guarda a keystore de debug (a mesma que assina o APK do instalador).
val keystoreDeDebug: File = run {
    val pasta = System.getenv("ANDROID_USER_HOME")?.let(::File)
        ?: System.getenv("ANDROID_SDK_HOME")?.let { File(it, ".android") }
        ?: File(System.getProperty("user.home"), ".android")
    File(pasta, "debug.keystore")
}
val certificadoDeDebug: String =
    mascararCertificado(certificadoForcado ?: impressaoDoCertificado(keystoreDeDebug, "android", "androiddebugkey"))
fun segredoDaAssinatura(nome: String): String? = System.getenv(nome)
val arquivoDaChave: File = file("release.keystore")

val certificadoDeRelease: String = mascararCertificado(
    certificadoForcado ?: impressaoDoCertificado(
        arquivoDaChave,
        segredoDaAssinatura("SIGNING_STORE_PASSWORD"),
        segredoDaAssinatura("SIGNING_KEY_ALIAS"),
    )
)

/** Texto como literal Java para o `buildConfigField`: escapa barra e aspas. */
fun literalJava(texto: String): String {
    val escapado = texto.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escapado\""
}

android {
    namespace = "br.com.deepgwt"
    compileSdk = 36

    defaultConfig {
        // Ver nota em `applicationIdReal` acima — tem que ser a mesma `val`, não um literal.
        applicationId = applicationIdReal
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = codigoDaVersao
        versionName = nomeDaVersao
        // IndependentGW: registro persistente de eventos do painel (`ClusterPersistentEventLogger`).
        // Ligado nos builds de debug (o APK que o instalador leva ao carro) e no simulador.
        buildConfigField("boolean", "DIAGNOSTICO_DO_PAINEL", "false")
        buildConfigField("boolean", "NIVEL_DE_TESTE", "false")
        buildConfigField("String", "LICENCA_CHAVES_PUBLICAS", literalJava(licencaChavesPublicas))
        // De onde o app lê "há versão nova?" (API de releases). Vazio = o app não consulta nada.
        // Só vale um feed do próprio DeepGWT.
        buildConfigField(
            "String",
            "UPDATE_FEED_URL",
            literalJava(providers.gradleProperty("deepgwt.updateFeedUrl").orElse("").get())
        )
        buildConfigField("String", "LICENCA_CERTIFICADO", literalJava(""))
        // Desligado em tudo, menos no build `simulator` (abaixo).
        buildConfigField("boolean", "SIMULATOR_MODE", false.toString())
    }

    signingConfigs {
        // Os segredos da assinatura vêm do ambiente; sem eles o build de release não assina.
        create("release") {
            storeFile = arquivoDaChave
            storePassword = segredoDaAssinatura("SIGNING_STORE_PASSWORD")
            keyAlias = segredoDaAssinatura("SIGNING_KEY_ALIAS")
            keyPassword = segredoDaAssinatura("SIGNING_KEY_PASSWORD")
        }
    }

    buildTypes {
        named("debug") {
            buildConfigField("boolean", "NIVEL_DE_TESTE", nivelDeTesteNoDebug)
            buildConfigField("String", "LICENCA_CERTIFICADO", literalJava(certificadoDeDebug))
            buildConfigField("boolean", "DIAGNOSTICO_DO_PAINEL", "true")
        }
        create("simulator") {
            initWith(buildTypes["debug"])
            matchingFallbacks.add("debug")
            buildConfigField("boolean", "DIAGNOSTICO_DO_PAINEL", "true")
            buildConfigField("boolean", "NIVEL_DE_TESTE", "true")
            buildConfigField("boolean", "SIMULATOR_MODE", true.toString())
        }
        named("release") {
            signingConfig = signingConfigs["release"]
            buildConfigField("String", "LICENCA_CERTIFICADO", literalJava(certificadoDeRelease))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFile(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFile("proguard-rules.pro")
        }
    }

    sourceSets {
        // Alpha 9.7: assets/legal/ (NOTICE, LICENSE e THIRD_PARTY_LICENSES.md), gerado a partir dos
        // arquivos da raiz do repositório pela tarefa `copiarAvisosLegais` (abaixo).
        getByName("main") {
            assets.srcDir(layout.buildDirectory.dir("generated/avisosLegais").get().asFile)
        }
        getByName("simulator") {
            java.srcDirs("src/simulator/java")
        }
    }

    compileOptions {
        // Java 11 é o piso do AGP 9; o Kotlin segue o mesmo alvo (bloco `kotlin`, abaixo).
        val alvoJava = JavaVersion.VERSION_11
        sourceCompatibility = alvoJava
        targetCompatibility = alvoJava
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
    }
}
// © 2026 Miguel Vespermann Araújo — VASL 1.0 — unauthorized forks/redistribution prohibited.

// Alpha 9.7: os avisos de licença acompanham cada cópia do APK (MIT, Apache-2.0 e OFL exigem o texto
// junto da distribuição). Fonte única: os arquivos da raiz; nada é copiado para dentro do app/.
val copiarAvisosLegais by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.file("NOTICE"))
    from(rootProject.layout.projectDirectory.file("LICENSE"))
    from(rootProject.layout.projectDirectory.file("THIRD_PARTY_LICENSES.md"))
    from(rootProject.layout.projectDirectory.file("EULA.md"))
    from(rootProject.layout.projectDirectory.file("PRIVACIDADE.md"))
    into(layout.buildDirectory.dir("generated/avisosLegais/legal"))
}
tasks.named("preBuild") { dependsOn(copiarAvisosLegais) }

kotlin {
    jvmToolchain(11)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    // Interface: AndroidX, Material e Jetpack Compose (versões do BOM)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.material.componentes)
    implementation(platform(libs.compose.plataforma))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icones.estendidos)
    implementation(libs.imagem.coil)
    // Acesso ao sistema do carro
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provedor)
    implementation(libs.api.oculta.bypass)
    annotationProcessor(libs.refine.processador)
    compileOnly(libs.refine.anotacao)
    // Dados, rede e arquivos
    implementation(libs.json.kotlinx)
    implementation(libs.json.gson)
    implementation(libs.rede.commons)
    implementation(libs.androidx.documentfile)
    // QR da página da licença no celular (Alpha 8.1). Só o núcleo, sem câmera.
    implementation(libs.qr.zxing)
    // Testes e ferramentas de desenvolvimento
    testImplementation(libs.teste.junit)
    testImplementation(libs.teste.json)
    testImplementation(libs.teste.sqlite)
    debugImplementation(libs.compose.ui.ferramentas)
    debugImplementation(libs.compose.ui.manifesto.de.teste)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
