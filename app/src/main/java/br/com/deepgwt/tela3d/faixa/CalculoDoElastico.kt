package br.com.deepgwt.tela3d.faixa

import kotlin.math.abs
import kotlin.math.min

/**
 * Elástico nas pontas da rolagem horizontal (F27): arrastar contra uma ponta desloca o conteúdo em
 * `Δx × 0,45`, acumulado e limitado a ±[limitePx]. Puro: a view só aplica [puxada].
 */
class CalculoDoElastico(private val limitePx: Float) {
    /** Deslocamento atual do conteúdo, em px (positivo: puxado para a direita). */
    var puxada: Float = 0f
        private set

    val puxando: Boolean get() = puxada != 0f

    /**
     * Um movimento horizontal de [dx] px. Devolve se o elástico consumiu o toque; falso = a rolagem
     * normal segue. [noInicio] e [noFim] dizem se o conteúdo já está numa das pontas.
     */
    fun mover(dx: Float, noInicio: Boolean, noFim: Boolean): Boolean {
        if (puxada == 0f) {
            val comecaNoInicio = noInicio && dx > 0f
            val comecaNoFim = noFim && dx < 0f
            if (!comecaNoInicio && !comecaNoFim) return false
        } else if ((puxada > 0f && dx < 0f) || (puxada < 0f && dx > 0f)) {
            // Passou a ir contra o lado da puxada: zera de uma vez e a rolagem normal segue.
            puxada = 0f
            return false
        }
        val nova = puxada + dx * FATOR
        puxada = if (nova >= 0f) min(limitePx, nova) else -min(limitePx, -nova)
        return true
    }

    /** Soltou ou cancelou. Verdadeiro se há puxada que precisa voltar com animação. */
    fun soltar(): Boolean = abs(puxada) > LIMIAR_DA_VOLTA

    /** Zera a puxada (fim da volta ou puxada pequena demais para animar). */
    fun zerar() {
        puxada = 0f
    }

    /** Define a puxada durante a animação de volta. */
    fun definir(valor: Float) {
        puxada = valor
    }

    companion object {
        const val FATOR = 0.45f
        const val LIMITE_DP = 56f
        const val LIMIAR_DA_VOLTA = 0.5f
        const val VOLTA_MS = 280L
        const val FATOR_DA_VOLTA = 1.6f
    }
}
