// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Raiz do build: só declara os plugins, sem aplicá-los. O módulo `app` aplica cada um na versão
// fixada em gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.agp.aplicativo) apply false
    alias(libs.plugins.kotlin.no.android) apply false
    alias(libs.plugins.kotlin.compilador.compose) apply false
    alias(libs.plugins.kotlin.serializacao) apply false
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
