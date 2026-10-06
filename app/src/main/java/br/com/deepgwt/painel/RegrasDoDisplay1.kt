// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

/**
 * Regras puras da camada do display 1 (`projetores-hud.md`, B.1 e C).
 */
object RegrasDoDisplay1 {

    /** C.1: aparece se o papel de parede está ligado, a tela principal ligada e não há app no display 1. */
    @JvmStatic
    fun aparece(papelLigado: Boolean, telaPrincipalLigada: Boolean, appNoDisplay1: Boolean): Boolean =
        papelLigado && telaPrincipalLigada && !appNoDisplay1

    /** O bitmap pequeno da cor com vinheta (a `ImageView` em CENTER_CROP estica para 1920×720). */
    const val LARGURA_DA_COR = 640
    const val ALTURA_DA_COR = 240

    /** Raio do gradiente: 0,62 × 640. */
    const val RAIO_DA_VINHETA = 0.62f * LARGURA_DA_COR

    /** Onde o gradiente começa a escurecer (de 0 a 0,45 é transparente). */
    const val INICIO_DA_VINHETA = 0.45f

    /** O padrão quando o app cria uma cor. */
    const val COR_PADRAO = "#101820|V45"

    /** Uma cor `#RRGGBB|V<n>` lida: componentes 0..255 e o alfa da borda da vinheta (0 = sem vinheta). */
    data class CorComVinheta(val vermelho: Int, val verde: Int, val azul: Int, val vinheta: Int) {
        /** Alfa do preto na borda: n × 255 ÷ 100. */
        val alfaDaVinheta: Int get() = vinheta * 255 / 100
    }

    private val HEX = Regex("[0-9A-Fa-f]{6}")

    /**
     * B.1: `#RRGGBB|V<n>` (o `#` é opcional; hex com exatamente 6 dígitos, senão inválido; `|V<n>`
     * opcional, n limitado a 0..100, ausente/ilegível = 0). Inválido → null (fundo preto).
     */
    @JvmStatic
    fun cor(valor: String?): CorComVinheta? {
        val partes = valor?.trim()?.split('|') ?: return null
        val hex = partes[0].trim().removePrefix("#")
        if (!HEX.matches(hex)) return null
        var vinheta = 0
        if (partes.size > 1) {
            val texto = partes[1].trim()
            if (texto.startsWith("V") || texto.startsWith("v")) {
                texto.substring(1).trim().toIntOrNull()?.let { vinheta = it.coerceIn(0, 100) }
            }
        }
        val rgb = hex.toInt(16)
        return CorComVinheta((rgb shr 16) and 0xff, (rgb shr 8) and 0xff, rgb and 0xff, vinheta)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
