package br.com.opengw.painel

/**
 * As teclas do volante que o painel conhece, com o código do serviço de teclas da central
 * (`docs/independencia/specs/projetores-cartao.md`, seção 2).
 */
enum class TeclaDoVolante(val codigo: Int) {
    UP(1024),
    DOWN(1025),
    LEFT(1026),
    RIGHT(1027),
    ENTER(1028),
    HOME(1029),
    BACK(1030),
    UP_LONG(1033),
    DOWN_LONG(1034),
    ENTER_LONG(1037),
    BACK_LONG(1039);

    /** ←/→ trocam o cartão do carro; nunca vão para a página. */
    val trocaCartao: Boolean
        get() = this == LEFT || this == RIGHT

    companion object {
        private val PELO_CODIGO: Map<Int, TeclaDoVolante> = entries.associateBy { it.codigo }

        /** A tecla deste código; 1031, 1032, 1035, 1036, 1038 e qualquer outro → null. */
        @JvmStatic
        fun doCodigo(codigo: Int): TeclaDoVolante? = PELO_CODIGO[codigo]
    }
}
