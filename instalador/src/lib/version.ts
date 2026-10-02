// Nome da build: canal + versão + carimbo. O canal é fixado aqui; a versão (major.minor) vem do
// package.json, mantendo uma única fonte de verdade e evitando um literal que envelhece sozinho.
import pkg from '../../package.json'

const BUILD_CHANNEL = 'Alpha'
const shortVersion = (pkg as { version: string }).version.split('.').slice(0, 2).join('.')

/**
 * Carimbo da build, no formato DDMMAAAAHHMM.
 *
 * Fica como literal porque é o mesmo número que vai no `appVersionName` do APK
 * (`gradle.properties`), e os dois TÊM de bater: é por ele que se sabe, olhando a tela do carro e
 * a do instalador, se os dois lados são da mesma leva. O `version` do package.json/tauri.conf.json
 * continua semver puro — o Tauri não aceita outra coisa —, então o carimbo mora só aqui.
 */
const BUILD_STAMP = '011020260825'

export const APP_VERSION = `${BUILD_CHANNEL} ${shortVersion} ${BUILD_STAMP}`

/**
 * Versão do PACOTE do instalador (semver completo do package.json).
 *
 * `APP_VERSION` é o nome do APK embutido — é ele que se compara com a tela do carro, e por isso o
 * cabeçalho mostra ele. Quando só o instalador muda (sem APK novo), o `major.minor` e o carimbo
 * continuam os do APK embutido e sobe só o terceiro número (6.3.0 -> 6.3.1): o `.dmg` ganha nome
 * próprio, e o número que a pessoa compara com o carro continua dizendo a verdade.
 */
export const INSTALLER_VERSION = (pkg as { version: string }).version
