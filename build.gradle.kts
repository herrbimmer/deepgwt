// Raiz do build: só declara os plugins, sem aplicá-los. O módulo `app` aplica cada um na versão
// fixada em gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.agp.aplicativo) apply false
    alias(libs.plugins.kotlin.no.android) apply false
    alias(libs.plugins.kotlin.compilador.compose) apply false
    alias(libs.plugins.kotlin.serializacao) apply false
}
