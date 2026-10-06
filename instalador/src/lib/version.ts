// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
// Nome da build: canal + versão + carimbo (`Alpha 9.8.0 051020261530`). O canal é fixado aqui; a
// versão (MAJOR.MINOR.PATCH) vem do package.json, mantendo uma única fonte de verdade e evitando um
// literal que envelhece sozinho. Quem sobe tudo junto é `scripts/subir-versao.py` (ver ../VERSIONAMENTO.md).
import pkg from '../../package.json'

const BUILD_CHANNEL = 'Alpha'

/**
 * Carimbo da build, no formato DDMMAAAAHHMM.
 *
 * Fica como literal porque é o mesmo número que vai no `appVersionName` do APK
 * (`gradle.properties`), e os dois TÊM de bater: é por ele que se sabe, olhando a tela do carro e
 * a do instalador, se os dois lados são da mesma leva. O `version` do package.json/tauri.conf.json
 * continua semver puro — o Tauri não aceita outra coisa —, então o carimbo mora só aqui.
 */
const BUILD_STAMP = '061020261436'

export const APP_VERSION = `${BUILD_CHANNEL} ${(pkg as { version: string }).version} ${BUILD_STAMP}`

/**
 * Versão do PACOTE do instalador: o semver completo do package.json, que é o mesmo número do APK
 * embutido (`APP_VERSION` sem o canal e o carimbo). Instalador e APK sobem juntos.
 */
export const INSTALLER_VERSION = (pkg as { version: string }).version
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
