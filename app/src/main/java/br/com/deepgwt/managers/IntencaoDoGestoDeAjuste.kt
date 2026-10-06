package br.com.deepgwt.managers

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lê a intenção de um arrasto de 3 dedos: qual eixo e quantos degraus. Kotlin puro, testado na JVM.
 *
 * ## O eixo que MAIS se moveu é a intenção
 *
 * Ninguém arrasta três dedos num carro em linha perfeita. O gesto vale pelo eixo dominante:
 * `|dy| >= |dx|` é vertical (temperatura), senão é horizontal (ventilador), em qualquer inclinação
 * até a diagonal. Nada de ângulo de tolerância estreito, nem exigência de o outro eixo ficar quieto.
 *
 * ## Uma régua só para os dois eixos
 *
 * A distância mínima e o tamanho do degrau são frações do MENOR eixo do digitalizador, não de cada
 * eixo. Antes cada direção era medida pelo próprio eixo: numa tela larga (1920x720) o arrasto para
 * os lados pedia 2,7x mais caminho que o vertical, e a decisão do eixo comparava unidades cruas
 * enquanto a distância usava frações — duas réguas para a mesma coisa. Com a menor dimensão como
 * referência, um palmo de arrasto vale o mesmo para cima ou para o lado.
 */
object IntencaoDoGestoDeAjuste {

    /** Eixo que o gesto escolheu. */
    enum class Eixo { VERTICAL, HORIZONTAL }

    /**
     * [degraus] vem COM SINAL: no vertical, positivo = dedos subindo (mais quente); no horizontal,
     * positivo = dedos para a direita (mais ventilação).
     */
    data class Intencao(val eixo: Eixo, val degraus: Int)

    /** Deslocamento mínimo, como fração do menor eixo. Era 0,15 do eixo de cada direção. */
    const val DESLOCAMENTO_MINIMO = 0.08f

    /** Fração a mais do menor eixo por degrau extra: um arrasto mais longo mexe mais. */
    const val FRACAO_POR_DEGRAU_EXTRA = 0.10f

    /** Teto de degraus por gesto, para um arrastão não levar a temperatura de ponta a ponta. */
    const val MAX_DEGRAUS = 6

    /**
     * @param dx deslocamento médio dos dedos em X, em unidades cruas do digitalizador.
     * @param dy deslocamento médio em Y (cresce para baixo).
     * @return a intenção, ou nulo se o arrasto foi curto demais para valer como gesto.
     */
    fun interpretar(dx: Double, dy: Double, rangeX: Int, rangeY: Int): Intencao? {
        val vertical = abs(dy) >= abs(dx)
        val referencia = minOf(rangeX, rangeY).coerceAtLeast(1)
        val percorrido = (if (vertical) abs(dy) else abs(dx)) / referencia
        if (percorrido < DESLOCAMENTO_MINIMO) return null

        val degraus =
            (1 + (percorrido - DESLOCAMENTO_MINIMO) / FRACAO_POR_DEGRAU_EXTRA)
                .roundToInt()
                .coerceIn(1, MAX_DEGRAUS)
        return if (vertical) {
            // Y cresce pra baixo: dy negativo = dedos subindo.
            Intencao(Eixo.VERTICAL, if (dy < 0) degraus else -degraus)
        } else {
            Intencao(Eixo.HORIZONTAL, if (dx > 0) degraus else -degraus)
        }
    }
}
