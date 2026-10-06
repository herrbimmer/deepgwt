// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import br.com.deepgwt.ponte.ModoDeExibicao

/** `<AppDefaultPosition>`: onde os apps abertos no painel ficam até o tema dizer outra coisa. */
data class AreaDosApps(val x: Int, val y: Int, val largura: Int, val altura: Int)

/** Um `<configuration>` do `theme.xml` (ajuste do tema na tela Telas). */
data class AjusteDoTema(
    val id: String,
    val rotulo: String,
    /** Em minúsculas: boolean, text, number, combo, color (outros passam). */
    val tipo: String,
    /** `<default>`. */
    val padrao: String,
    /** `<stateVariable>`. */
    val variavel: String,
    /** `<options>` separadas por vírgula, sem espaços nas pontas. */
    val opcoes: List<String>,
    /** `<group>`; "" = aba "Geral". */
    val grupo: String,
)

/** Uma máscara nativa declarada pelo tema (`<fuelMask>`…), já limitada a 1920×720. */
data class MascaraNativa(
    val ligada: Boolean,
    val imagem: String,
    val x: Int,
    val y: Int,
    val largura: Int,
    val altura: Int,
    val opacidade: Float,
)

/** As cinco máscaras nativas de um tema. */
data class MascarasNativas(
    val fuel: MascaraNativa,
    val battery: MascaraNativa,
    val speed: MascaraNativa,
    val info: MascaraNativa,
    val topCenter: MascaraNativa,
) {
    val algumaLigada: Boolean
        get() = fuel.ligada || battery.ligada || speed.ligada || info.ligada || topCenter.ligada

    /** Pelo nome que a ponte usa (`fuel`, `battery`, `speed`, `info`, `topcenter`). */
    fun porNome(nome: String): MascaraNativa? = when (nome) {
        "fuel" -> fuel
        "battery" -> battery
        "speed" -> speed
        "info" -> info
        "topcenter" -> topCenter
        else -> null
    }
}

/** Um tema instalado em `<filesDir>/themes/<pasta>/` (ou o embutido). */
data class TemaInstalado(
    /** Nome da pasta; "Default" para o embutido. */
    val pasta: String,
    val nome: String,
    val descricao: String,
    val versao: String,
    /** Caminho absoluto, URL http(s) ou "". */
    val miniatura: String,
    /** `<mainFile>`; padrão "index.html". */
    val arquivoPrincipal: String,
    val local: Boolean,
    /** `<AppDefaultPosition>`; null se faltar algum dos 4. */
    val areaDosApps: AreaDosApps?,
    /** `<decentralized>`. */
    val descentralizado: Boolean,
    /** `<minBridgeVersion>`; null se ausente. */
    val versaoMinimaDaPonte: String?,
    /** `<contractVersion>`; vazio vira "legacy". */
    val versaoDoContrato: String,
    val ajustes: List<AjusteDoTema>,
    val modosDeExibicao: List<ModoDeExibicao>,
    /** `<background>` relativo, sem espaços nas pontas. */
    val fundo: String,
    /** Caminho absoluto se o arquivo existe, senão "". */
    val fundoAbsoluto: String,
    /** Null se o `theme.xml` não tem nenhuma das 5 etiquetas de máscara. */
    val mascaras: MascarasNativas?,
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
