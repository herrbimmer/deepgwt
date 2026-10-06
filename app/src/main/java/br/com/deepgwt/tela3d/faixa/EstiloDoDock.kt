package br.com.deepgwt.tela3d.faixa

/**
 * Estilo da bandeja de apps (Alpha 10.3). A fileira de ícones é sempre nativa; o vidro atrás dela é
 * desenhado pela página (para desfocar a cena 3D) a partir do retângulo que a faixa envia.
 *
 * Alturas em dp, contadas do topo da faixa ([MedidasDaFaixa.ALTURA_DA_FAIXA_DP]).
 */
enum class EstiloDoDock(
    val valor: String,
    /** Onde começa a fileira de ícones. */
    val topoDaFileiraDp: Int,
    /** Reflexo dos ícones sobre a prateleira. */
    val reflexo: Boolean,
    /** Retângulo do vidro; [placaBaseDp] nulo = vai até o fim da página. */
    val placaTopoDp: Int,
    val placaBaseDp: Int?,
    /** O vidro cobre a largura toda da página (barra). */
    val placaNaLarguraToda: Boolean = false,
) {
    NENHUM("none", 39, false, 0, 0),
    FLAT("flat", 39, false, 31, 143),
    LION("lion", 12, true, 46, 142),
    BARRA("bar", 39, false, 28, null, placaNaLarguraToda = true),
    ;

    val temPlaca: Boolean get() = this != NENHUM

    companion object {
        val PADRAO = FLAT

        fun deTexto(texto: String?): EstiloDoDock? = entries.firstOrNull { it.valor == texto }

        /** Preferência gravada: qualquer valor desconhecido vira o padrão. */
        fun daPreferencia(texto: String?): EstiloDoDock = deTexto(texto) ?: PADRAO
    }
}
