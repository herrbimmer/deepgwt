package br.com.opengw.brilho

import kotlin.math.abs

/**
 * Um dia no modo sol: as duas rampas (nascer e pôr) e o nível em cada instante. Sem Android.
 *
 * Cada rampa dura `|dia − noite| × passo`, metade antes e metade depois do evento. Dentro dela o
 * nível parte da origem e anda 1 a cada passo completo desde o início, sem passar do destino.
 * Fora: noite antes da rampa do nascer, dia entre as rampas, noite depois da rampa do pôr.
 * Sem diferença entre os níveis (ou passo ≤ 0) a troca é seca: dia em [nascer, pôr).
 *
 * Dia curto demais para as duas rampas inteiras (perto dos polos): cada uma vai no máximo até o
 * ponto médio entre nascer e pôr; a do pôr continua contando os passos desde o seu início
 * normal, então o nível sobe até perto do meio do dia e volta, sem salto grande.
 */
internal class DiaDeSol(
    val sol: NascerEPor,
    private val nivelDia: Int,
    private val nivelNoite: Int,
    private val passoMs: Long,
) {
    private val seca = nivelDia == nivelNoite || passoMs <= 0
    private val duracao: Long = if (seca) 0L else abs(nivelDia - nivelNoite).toLong() * passoMs
    private val meio = sol.nascerMs + (sol.porMs - sol.nascerMs) / 2

    /** Início da rampa do nascer. */
    val inicioDoNascer: Long = sol.nascerMs - duracao / 2
    private val fimDoNascer: Long = minOf(inicioDoNascer + duracao, meio)

    /** Onde a rampa do pôr começaria se o dia fosse longo; os passos contam daqui. */
    private val inicioNormalDoPor: Long = sol.porMs - duracao / 2

    /** Início da rampa do pôr. */
    val inicioDoPor: Long = maxOf(inicioNormalDoPor, meio)
    private val fimDoPor: Long = inicioNormalDoPor + duracao

    fun nivel(agoraMs: Long): Int = when {
        agoraMs < inicioDoNascer -> nivelNoite
        agoraMs < fimDoNascer -> andar(nivelNoite, nivelDia, agoraMs - inicioDoNascer)
        agoraMs < inicioDoPor -> nivelDia
        agoraMs < fimDoPor -> andar(nivelDia, nivelNoite, agoraMs - inicioNormalDoPor)
        else -> nivelNoite
    }

    fun dentroDeRampa(agoraMs: Long): Boolean =
        agoraMs in inicioDoNascer until fimDoNascer || agoraMs in inicioDoPor until fimDoPor

    /**
     * Quando reavaliar. Dentro de uma rampa: daqui a um passo. Fora: no início da próxima rampa
     * (a do nascer de hoje, a do pôr de hoje ou a do nascer de [amanha]); sem nascer amanhã
     * (dia ou noite polar), em [inicioDeAmanhaMs]. Nunca menos de [minimoMs] à frente.
     */
    fun proximaAvaliacao(
        agoraMs: Long,
        amanha: () -> DiaDeSol?,
        inicioDeAmanhaMs: Long,
        minimoMs: Long,
    ): Long {
        val alvo = when {
            dentroDeRampa(agoraMs) -> agoraMs + passoMs
            agoraMs < inicioDoNascer -> inicioDoNascer
            agoraMs < inicioDoPor -> inicioDoPor
            else -> amanha()?.inicioDoNascer ?: inicioDeAmanhaMs
        }
        return maxOf(alvo, agoraMs + minimoMs)
    }

    private fun andar(origem: Int, destino: Int, decorridoMs: Long): Int {
        val passos = if (decorridoMs <= 0) 0L else decorridoMs / passoMs
        return if (destino >= origem) {
            minOf(origem.toLong() + passos, destino.toLong()).toInt()
        } else {
            maxOf(origem.toLong() - passos, destino.toLong()).toInt()
        }
    }
}
