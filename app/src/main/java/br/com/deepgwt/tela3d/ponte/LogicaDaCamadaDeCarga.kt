package br.com.deepgwt.tela3d.ponte

/**
 * A lógica (sem Views) da camada nativa de carga: linha de progresso, spinner de centro e botão SKIP.
 * As medidas são as da especificação; quem desenha é a [CamadaDeCarga].
 */
object MedidasDaCamadaDeCarga {
    const val COR_DO_TEXTO_DO_SKIP: Int = 0xE8FFFFFF.toInt()
    const val COR_DO_FUNDO_DO_SKIP: Int = 0x6B000000
    const val COR_DA_TRILHA: Int = 0x28FFFFFF
    const val COR_DO_PREENCHIMENTO: Int = 0xFF4FD6E8.toInt()

    const val TEXTO_DO_SKIP = "SKIP"
    const val TAMANHO_DO_TEXTO_DO_SKIP_SP = 12f
    const val ESPACAMENTO_DAS_LETRAS_EM = 0.16f
    const val PREENCHIMENTO_HORIZONTAL_DO_SKIP_DP = 16
    const val PREENCHIMENTO_VERTICAL_DO_SKIP_DP = 8
    const val ELEVACAO_DO_SKIP_DP = 24
    const val MARGEM_ESQUERDA_DO_SKIP_DP = 14

    const val ELEVACAO_DO_HUD_DP = 20
    const val ALTURA_DA_LINHA_PX = 2
    const val Y_DA_LINHA_NA_TELA_PX = 655
    const val TAMANHO_DO_SPINNER_DP = 36

    /** A linha fica em y = 655 px da tela: a margem superior desconta a origem da página. */
    fun margemSuperiorDaLinha(origemDaPaginaYEmPx: Int): Int = maxOf(0, Y_DA_LINHA_NA_TELA_PX - origemDaPaginaYEmPx)

    /** A margem inferior do SKIP é a reserva do chrome inferior (80 dp − overscan), nunca negativa. */
    fun margemInferiorDoSkip(reservaInferiorEmPx: Int): Int = maxOf(0, reservaInferiorEmPx)

    /** Largura do preenchimento; a trilha ainda sem medida usa a largura da tela. */
    fun larguraDoPreenchimento(larguraDaTrilha: Int, larguraDaTela: Int, pct: Int): Int {
        val base = if (larguraDaTrilha > 0) larguraDaTrilha else larguraDaTela
        return Math.round(base.toDouble() * limitarPct(pct) / 100.0).toInt()
    }

    fun limitarPct(pct: Int): Int = pct.coerceIn(0, 100)
}

/** O que está visível no HUD de carga. */
data class EstadoDoHud(val modoCentro: Boolean, val linhaVisivel: Boolean, val spinnerVisivel: Boolean) {
    companion object {
        val ESCONDIDO = EstadoDoHud(modoCentro = false, linhaVisivel = false, spinnerVisivel = false)
    }
}

/**
 * As transições do HUD de carga (comportamento 26), sem Views. O estado inicial é tudo invisível.
 * - Progresso: no modo "centro" mostra o spinner e esconde a linha; fora dele o contrário.
 * - Posicionar `"center"`: modo centro (esconde a linha, mostra o spinner); outro valor: modo linha
 *   (esconde o spinner; a linha só aparece quando chega um progresso).
 * - Esconder: sai do modo centro e esconde os dois.
 */
object MaquinaDoHud {
    fun aoProgredir(atual: EstadoDoHud): EstadoDoHud =
        if (atual.modoCentro) atual.copy(spinnerVisivel = true, linhaVisivel = false)
        else atual.copy(spinnerVisivel = false, linhaVisivel = true)

    fun aoPosicionar(atual: EstadoDoHud, modo: String?): EstadoDoHud =
        if (modo == "center") atual.copy(modoCentro = true, linhaVisivel = false, spinnerVisivel = true)
        else atual.copy(modoCentro = false, spinnerVisivel = false)

    fun aoEsconder(): EstadoDoHud = EstadoDoHud.ESCONDIDO
}

/** A miniatura da área de trabalho (`captureDesktopSnapshot`). */
object MiniaturaDaArea {
    const val LARGURA_MINIMA = 64
    const val LARGURA_MAXIMA = 960
    const val ALTURA_MINIMA = 24
    const val ALTURA_MAXIMA = 360
    const val QUALIDADE_DO_JPEG = 72
    const val PREFIXO_DA_URL = "data:image/jpeg;base64,"

    /** O token só leva `[A-Za-z0-9_-]`; sobrando nada, a captura nem começa (a página desiste sozinha). */
    fun limparToken(token: String?): String = token?.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }.orEmpty()

    fun largura(pedida: Int): Int = pedida.coerceIn(LARGURA_MINIMA, LARGURA_MAXIMA)
    fun altura(pedida: Int): Int = pedida.coerceIn(ALTURA_MINIMA, ALTURA_MAXIMA)
}

/** O retângulo da pílula de troca de área de trabalho, em pixels de tela, e se o proxy de toque o quer. */
data class AreaDoSeletor(val x: Int, val y: Int, val largura: Int, val altura: Int, val quer: Boolean) {
    companion object {
        val INATIVA = AreaDoSeletor(0, 0, 0, 0, false)

        /**
         * Do retângulo em px CSS (que a página manda) à área em pixels de tela: origem = posição da
         * WebView na tela + `round(x * escala)`; tamanho = `max(0, round(w * escala))`. Escala ≤ 0 vale 1.
         * "Quer proxy" = ativo e largura > 1 e altura > 1.
         */
        fun converter(
            origemXNaTela: Int, origemYNaTela: Int,
            xCss: Float, yCss: Float, larguraCss: Float, alturaCss: Float,
            ativo: Boolean, escala: Float,
        ): AreaDoSeletor {
            val e = if (escala > 0f && !escala.isNaN()) escala.toDouble() else 1.0
            val x = origemXNaTela + Math.round(xCss * e).toInt()
            val y = origemYNaTela + Math.round(yCss * e).toInt()
            val l = maxOf(0, Math.round(larguraCss * e).toInt())
            val a = maxOf(0, Math.round(alturaCss * e).toInt())
            return AreaDoSeletor(x, y, l, a, ativo && l > 1 && a > 1)
        }
    }
}
