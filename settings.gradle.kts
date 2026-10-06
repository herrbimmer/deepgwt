// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
rootProject.name = "DeepGWT"
include(":app")

// Onde o Gradle procura plugins. O Google só entrega os grupos que são dele.
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("""com\.android.*""")
                includeGroupByRegex("""com\.google.*""")
                includeGroupByRegex("""androidx.*""")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Baixa o JDK pedido por `jvmToolchain` quando a máquina não tem.
    id("org.gradle.toolchains.foojay-resolver-convention").version("1.0.0")
}

// Onde o Gradle procura dependências. Módulo nenhum declara repositório próprio.
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
