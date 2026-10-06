package br.com.deepgwt.tela3d.faixa

import kotlin.math.roundToInt

// Cores e operações de cor do tema do cromo (A2). Puro: só inteiros ARGB.

object CoresDoCromo {
    const val ACENTO_PADRAO: Int = 0xFF2AA7B7.toInt()

    // Cor de rótulo, "muted" e glifo normal, por tema.
    const val ROTULO_ESCURO: Int = 0xFFFFFFFF.toInt()
    const val ROTULO_CLARO: Int = 0xFF3D4550.toInt()
    const val MUTED_ESCURO: Int = 0xCCFFFFFF.toInt()
    const val MUTED_CLARO: Int = 0xFF9AA3AE.toInt()
    const val GLIFO_ESCURO: Int = 0xE8FFFFFF.toInt()
    const val GLIFO_CLARO: Int = 0xFF7E8996.toInt()

    /** Rótulo dos itens de app: fixo nos dois temas. */
    const val ROTULO_DO_ITEM: Int = 0xE6FFFFFF.toInt()
    const val SOMBRA_DO_ROTULO: Int = 0xCC000000.toInt()

    fun corDeRotulo(claro: Boolean): Int = if (claro) ROTULO_CLARO else ROTULO_ESCURO
    fun corDeRotuloMuted(claro: Boolean): Int = if (claro) MUTED_CLARO else MUTED_ESCURO
    fun corDeGlifo(claro: Boolean): Int = if (claro) GLIFO_CLARO else GLIFO_ESCURO

    /**
     * Mistura linear por canal RGB; [fracao] fica em [0, 1]. O resultado é SEMPRE opaco: o alfa das
     * duas entradas é descartado.
     */
    fun misturar(a: Int, b: Int, fracao: Float): Int {
        val f = if (fracao.isNaN()) 0f else fracao.coerceIn(0f, 1f)
        fun canal(deslocamento: Int): Int {
            val x = (a ushr deslocamento) and 0xFF
            val y = (b ushr deslocamento) and 0xFF
            return (x + (y - x) * f).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (canal(16) shl 16) or (canal(8) shl 8) or canal(0)
    }

    /** Troca o alfa de [cor] por [alfa] (0 a 255). */
    fun comAlfa(cor: Int, alfa: Int): Int = (alfa.coerceIn(0, 255) shl 24) or (cor and 0x00FFFFFF)


    /**
     * Lê `#RGB` ou `#RRGGBB` (com `#`) e devolve ARGB opaco; qualquer outra coisa devolve nulo.
     */
    fun lerAcento(texto: String?): Int? {
        val t = texto?.trim() ?: return null
        if (!t.startsWith("#")) return null
        val hex = t.substring(1)
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        return when (hex.length) {
            3 -> {
                val r = hex.substring(0, 1).repeat(2)
                val g = hex.substring(1, 2).repeat(2)
                val b = hex.substring(2, 3).repeat(2)
                (0xFF000000L or "$r$g$b".toLong(16)).toInt()
            }
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            else -> null
        }
    }

    /** Lê uma cor `#RRGGBB` ou `#AARRGGBB` das personalizações; inválida devolve o [padrao]. */
    fun lerCorOuPadrao(texto: String?, padrao: Int): Int {
        val t = texto?.trim().orEmpty()
        if (!t.startsWith("#")) return padrao
        val hex = t.substring(1)
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return padrao
        return when (hex.length) {
            6 -> (0xFF000000L or hex.toLong(16)).toInt()
            8 -> hex.toLong(16).toInt()
            else -> padrao
        }
    }
}
