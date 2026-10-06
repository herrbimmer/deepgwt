package br.com.deepgwt.tela3d.cartoes

import kotlin.math.max

/** Um item da linha de métricas da navegação (valor em cima, rótulo embaixo). */
data class ItemDeMetrica(
    val rotulo: String,
    val valor: String,
    /** O valor do ETA é desenhado na cor de acento. */
    val destaque: Boolean,
    /** Posição x relativa ao início da área de métricas. */
    val x: Float,
    val largura: Float,
)

data class LinhaDeMetricas(
    val itens: List<ItemDeMetrica>,
    val tamanhoDoValor: Float,
    val tamanhoDoRotulo: Float,
)

/**
 * Ajuste da linha de métricas do cartão de navegação (restante, tempo, chegada) à largura disponível.
 * Puro: quem mede o texto entra por [medir] (largura em px de um texto num tamanho em px, fonte média).
 */
object MetricasDaNavegacao {
    const val ROTULO_RESTANTE = "REMAINING"
    const val ROTULO_TEMPO = "TIME"
    const val ROTULO_CHEGADA = "ETA"
    const val TAMANHO_MINIMO_DO_VALOR_PX = 11f

    private class Bruto(val rotulo: String, val valor: String, val destaque: Boolean)

    /**
     * @param altura altura H da área do gráfico, em px
     * @param densidade px por dp (os mínimos da spec são em dp)
     */
    fun calcular(
        restante: String,
        duracao: String,
        chegada: String,
        larguraDisponivel: Float,
        altura: Float,
        densidade: Float,
        medir: (texto: String, tamanhoPx: Float) -> Float,
    ): LinhaDeMetricas {
        val tamanhoDoRotulo = max(9f * densidade, 0.13f * altura)
        if (larguraDisponivel <= 0f) return LinhaDeMetricas(emptyList(), max(15f * densidade, 0.28f * altura), tamanhoDoRotulo)
        var brutos = buildList {
            if (restante.isNotEmpty()) add(Bruto(ROTULO_RESTANTE, restante, false))
            if (duracao.isNotEmpty()) add(Bruto(ROTULO_TEMPO, duracao, false))
            if (chegada.isNotEmpty()) add(Bruto(ROTULO_CHEGADA, chegada, true))
        }
        var tamanhoDoValor = max(15f * densidade, 0.28f * altura)
        val vao = max(10f * densidade, 0.08f * altura)
        val extraDesejado = max(8f * densidade, 0.08f * altura)

        fun largurasDosItens(lista: List<Bruto>, tamanho: Float): List<Float> =
            lista.map { minOf(max(medir(it.valor, tamanho), medir(it.rotulo, tamanhoDoRotulo)), larguraDisponivel) }

        fun somaSemExtra(larguras: List<Float>): Float =
            larguras.sum() + vao * max(0, larguras.size - 1)

        var larguras = largurasDosItens(brutos, tamanhoDoValor)
        if (somaSemExtra(larguras) > larguraDisponivel) {
            val semTempo = brutos.filter { it.rotulo != ROTULO_TEMPO }
            if (semTempo.size != brutos.size) {
                brutos = semTempo
                larguras = largurasDosItens(brutos, tamanhoDoValor)
            }
        }
        while (somaSemExtra(larguras) > larguraDisponivel && tamanhoDoValor > TAMANHO_MINIMO_DO_VALOR_PX) {
            tamanhoDoValor = max(TAMANHO_MINIMO_DO_VALOR_PX, tamanhoDoValor - 1f)
            larguras = largurasDosItens(brutos, tamanhoDoValor)
        }

        val sobra = max(0f, larguraDisponivel - somaSemExtra(larguras))
        val extra = minOf(extraDesejado, sobra)
        val itens = ArrayList<ItemDeMetrica>(brutos.size)
        var x = 0f
        for ((i, b) in brutos.withIndex()) {
            if (i > 0) {
                x += vao
                if (b.rotulo == ROTULO_CHEGADA) x += extra
            }
            val largura = larguras[i]
            val valor = CorteDeTexto.cortar(b.valor, largura) { medir(it, tamanhoDoValor) }
            itens += ItemDeMetrica(b.rotulo, valor, b.destaque, x, largura)
            x += largura
        }
        return LinhaDeMetricas(itens, tamanhoDoValor, tamanhoDoRotulo)
    }
}
