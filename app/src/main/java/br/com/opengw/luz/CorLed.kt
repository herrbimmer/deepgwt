package br.com.opengw.luz

import kotlin.math.floor
import kotlin.math.max

/** Cor de 8 bits por canal. Valores fora de 0..255 são aceitos e limitados na hora de montar o quadro. */
data class CorLed(val r: Int, val g: Int, val b: Int) {

    companion object {
        /**
         * Saturação e valor máximos; matiz em graus, limitada a 0..360 (360 = vermelho).
         *
         * Mesma conta da conversão HSV da plataforma (`Color.HSVToColor`), feita aqui para rodar
         * também nos testes da JVM: setor de 60°, fração do setor e cada canal arredondado com
         * meio para cima.
         */
        @JvmStatic
        fun daMatiz(graus: Float): CorLed {
            val h = if (graus.isNaN()) 0f else graus.coerceIn(0f, 360f)
            val setor = if (h >= 360f) 0f else h / 60f
            val inteiro = floor(setor)
            val fracao = setor - inteiro
            val cheio = 255
            val desce = arredondar((1f - fracao) * 255f)
            val sobe = arredondar((1f - (1f - fracao)) * 255f)
            return when (inteiro.toInt()) {
                0 -> CorLed(cheio, sobe, 0)
                1 -> CorLed(desce, cheio, 0)
                2 -> CorLed(0, cheio, sobe)
                3 -> CorLed(0, desce, cheio)
                4 -> CorLed(sobe, 0, cheio)
                else -> CorLed(cheio, 0, desce)
            }
        }

        private fun arredondar(x: Float): Int = floor(x + 0.5f).toInt()
    }
}

/** As cores fixas do módulo (caso 42 da especificação). */
internal object Cores {
    val VERMELHO = CorLed(255, 0, 0)
    val VERDE = CorLed(0, 255, 0)
    val AZUL = CorLed(0, 0, 255)
    val BRANCO = CorLed(255, 255, 255)
    val AZUL_SUAVE = CorLed(80, 120, 255)
    val AZUL_GELO = CorLed(0, 180, 255)
    val LARANJA = CorLed(255, 120, 0)
    val AMBAR = CorLed(255, 176, 59)
    val AMARELO = CorLed(255, 255, 0)
    val ROXO = CorLed(255, 0, 255)
    val APAGADO = CorLed(0, 0, 0)

    /** Ponto de partida dos pulsos do Sport. */
    val VERMELHO_FRACO = CorLed(120, 0, 0)

    /** Ponto baixo da respiração do Eco. */
    val VERDE_FRACO = CorLed(0, 90, 0)

    /** Troca do preto absoluto e cor "visível" de reserva. */
    val CIANO_DE_RESERVA = CorLed(66, 227, 255)
}

/** Contas de cor usadas pelos efeitos. Tudo em `Double`, canal arredondado com meio para cima. */
internal object ContasDeCor {

    fun arredondar(x: Double): Int = floor(x + 0.5).toInt()

    fun limitar(canal: Int): Int = canal.coerceIn(0, 255)

    fun limitar(cor: CorLed): CorLed = CorLed(limitar(cor.r), limitar(cor.g), limitar(cor.b))

    /** `a + (b − a)·t` por canal, com `t` limitado a 0..1. */
    fun mistura(a: CorLed, b: CorLed, t: Double): CorLed {
        val k = if (t.isNaN()) 0.0 else t.coerceIn(0.0, 1.0)
        return CorLed(canal(a.r, b.r, k), canal(a.g, b.g, k), canal(a.b, b.b, k))
    }

    private fun canal(a: Int, b: Int, t: Double): Int = limitar(arredondar(a + (b - a) * t))

    /** Cada canal vezes [k], arredondado e limitado. */
    fun vezes(cor: CorLed, k: Double): CorLed =
        CorLed(limitar(arredondar(cor.r * k)), limitar(arredondar(cor.g * k)), limitar(arredondar(cor.b * k)))

    fun maiorCanal(cor: CorLed): Int = max(cor.r, max(cor.g, cor.b))

    /** Preto vira o ciano de reserva; uma cor apagada demais sobe até o maior canal valer 72. */
    fun visivel(cor: CorLed): CorLed {
        val c = limitar(cor)
        val maior = maiorCanal(c)
        return when {
            maior == 0 -> Cores.CIANO_DE_RESERVA
            maior < 72 -> vezes(c, 72.0 / maior)
            else -> c
        }
    }

    /**
     * Realce das cores da capa: preto vira o ciano de reserva, cor escura sobe até o maior canal
     * valer 112 e depois a saturação cresce 1,18× em torno da luma.
     */
    fun realce(cor: CorLed): CorLed {
        var c = limitar(cor)
        val maior = maiorCanal(c)
        if (maior == 0) c = Cores.CIANO_DE_RESERVA else if (maior < 112) c = vezes(c, 112.0 / maior)
        val luma = 0.299 * c.r + 0.587 * c.g + 0.114 * c.b
        fun sat(x: Int) = limitar(arredondar(luma + (x - luma) * 1.18))
        return CorLed(sat(c.r), sat(c.g), sat(c.b))
    }

    /** Maior canal ÷ 255 (o "nível" mostrado no diagnóstico da onda do álbum). */
    fun intensidade(cor: CorLed): Double = maiorCanal(limitar(cor)) / 255.0

    /** Cor a partir de um `Int` ARGB (o alfa é ignorado). */
    fun deArgb(argb: Int): CorLed = CorLed((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    fun paraArgb(cor: CorLed): Int {
        val c = limitar(cor)
        return (0xFF shl 24) or (c.r shl 16) or (c.g shl 8) or c.b
    }
}

/** Transição (fade) de A para B: 20 passos, canal truncado (não arredondado). */
internal object Transicao {
    const val PASSOS = 20
    const val PAUSA_MS = 30L

    fun passo(a: CorLed, b: CorLed, k: Int): CorLed =
        CorLed(canal(a.r, b.r, k), canal(a.g, b.g, k), canal(a.b, b.b, k))

    fun passos(a: CorLed, b: CorLed): List<CorLed> = (1..PASSOS).map { passo(a, b, it) }

    private fun canal(a: Int, b: Int, k: Int): Int = (a + (b - a) * k / PASSOS.toDouble()).toInt()
}
