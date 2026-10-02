package br.com.opengw.telas

/**
 * Nomes dos fundos prontos que vêm no APK (`assets/backgrounds/`).
 *
 * Até a Alpha 9.6 eram 20 papéis de parede do Wallhaven. Na 9.7 saíram, porque cada um tinha um
 * autor próprio e nenhuma licença de redistribuição, e entraram 20 imagens originais do OPEN GW,
 * geradas por código (`scripts/gerar-arte-original.py`). Quem tinha um dos fundos antigos escolhido
 * continua com um fundo: [resolve] troca o nome antigo pelo novo na mesma posição da lista.
 */
object PresetBackgrounds {
    /** Os 20 identificadores do Wallhaven, na ordem em que a lista antiga os trazia. */
    private val LEGACY_WALLHAVEN_IDS = listOf(
        "0151j3", "0jxevy", "3kkrj9", "3lg189", "47j39o", "483k3k", "4djkzj", "4gvpk7", "737r1v",
        "96zqpw", "d6o77l", "jxqv85", "k81kk6", "lyyddq", "mlzl11", "n6rv2w", "nrk7r1", "nzvyev",
        "yx3vj7", "zmg7zw",
    )

    private val LEGACY_NAME = Regex("^wallhaven-([0-9a-z]+)\\.jpg$")

    /** Nome do arquivo em `assets/backgrounds/` que deve ser aberto para o fundo [name]. */
    fun resolve(name: String): String {
        val id = LEGACY_NAME.matchEntire(name)?.groupValues?.get(1) ?: return name
        val position = LEGACY_WALLHAVEN_IDS.indexOf(id)
        return if (position >= 0) "opengw-%02d.jpg".format(position + 1) else name
    }
}
