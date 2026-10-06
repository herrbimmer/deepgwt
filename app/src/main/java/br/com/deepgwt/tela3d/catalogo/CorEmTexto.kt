package br.com.deepgwt.tela3d.catalogo

import java.util.Locale

/**
 * Leitor de cor em texto, no mesmo formato que `Color.parseColor` aceita (`#RRGGBB`, `#AARRGGBB`
 * ou nome de cor), mas sem depender do Android — assim os testes de JVM o exercitam.
 */
internal object CorEmTexto {
    /** @return a cor ARGB, ou null se o texto não é uma cor válida. */
    fun analisar(texto: String?): Int? {
        if (texto == null) return null
        val t = texto.trim()
        if (t.isEmpty()) return null
        if (t[0] == '#') {
            val hex = t.substring(1)
            if (hex.length != 6 && hex.length != 8) return null
            val valor = hex.toLongOrNull(16) ?: return null
            // toLongOrNull aceita sinal; a cor em hexadecimal não.
            if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
            return if (hex.length == 6) (0xFF000000L or valor).toInt() else valor.toInt()
        }
        return NOMES[t.lowercase(Locale.ROOT)]
    }

    private val NOMES: Map<String, Int> = mapOf(
        "black" to 0xFF000000.toInt(),
        "darkgray" to 0xFF444444.toInt(),
        "gray" to 0xFF888888.toInt(),
        "lightgray" to 0xFFCCCCCC.toInt(),
        "white" to 0xFFFFFFFF.toInt(),
        "red" to 0xFFFF0000.toInt(),
        "green" to 0xFF00FF00.toInt(),
        "blue" to 0xFF0000FF.toInt(),
        "yellow" to 0xFFFFFF00.toInt(),
        "cyan" to 0xFF00FFFF.toInt(),
        "magenta" to 0xFFFF00FF.toInt(),
        "aqua" to 0xFF00FFFF.toInt(),
        "fuchsia" to 0xFFFF00FF.toInt(),
        "darkgrey" to 0xFF444444.toInt(),
        "grey" to 0xFF888888.toInt(),
        "lightgrey" to 0xFFCCCCCC.toInt(),
        "lime" to 0xFF00FF00.toInt(),
        "maroon" to 0xFF800000.toInt(),
        "navy" to 0xFF000080.toInt(),
        "olive" to 0xFF808000.toInt(),
        "purple" to 0xFF800080.toInt(),
        "silver" to 0xFFC0C0C0.toInt(),
        "teal" to 0xFF008080.toInt(),
    )
}

/** Cores padrão e paleta da folha "Personalizar". */
internal object CoresDaPersonalizacao {
    const val COR_PADRAO = "#FFFFFF"
    const val FUNDO_PADRAO = "#3D4650"

    val COR_PADRAO_ARGB: Int = 0xFFFFFFFF.toInt()
    val FUNDO_PADRAO_ARGB: Int = 0xFF3D4650.toInt()

    /** As 10 cores das duas fileiras (COR e FUNDO), nesta ordem. */
    val PALETA: List<String> = listOf(
        "#FFFFFF", "#9AA3AD", "#3D4650", "#111111", "#E53935",
        "#FB8C00", "#F5C542", "#43A047", "#1E88E5", "#26C6DA",
    )

    /** Cor do glifo; vazia ou inválida vira branco. */
    fun corDoGlifo(texto: String?): Int = CorEmTexto.analisar(texto) ?: COR_PADRAO_ARGB

    /** Cor do fundo; inválida vira #3D4650. */
    fun corDoFundo(texto: String?): Int = CorEmTexto.analisar(texto) ?: FUNDO_PADRAO_ARGB
}
