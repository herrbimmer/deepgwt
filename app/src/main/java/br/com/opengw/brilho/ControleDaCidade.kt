package br.com.opengw.brilho

import kotlin.math.roundToLong

/**
 * Quando perguntar o nome da cidade: no máximo uma consulta em andamento, e a mesma coordenada
 * (3 casas decimais) não é consultada de novo antes de [intervaloMs]. Conta a partir do começo da
 * consulta, deu certo ou não. Sem Android; o relógio vem de quem chama (monotônico, no app).
 */
internal class ControleDaCidade(private val intervaloMs: Long = INTERVALO_MS) {

    private var emAndamento = false
    private var ultimaCoordenada: Pair<Long, Long>? = null
    private var ultimaConsultaMs = 0L

    /** Pode consultar esta coordenada agora? Se sim, a consulta passa a estar em andamento. */
    @Synchronized
    fun comecar(latitude: Double, longitude: Double, agoraMs: Long): Boolean {
        if (emAndamento) return false
        val coordenada = arredondar(latitude) to arredondar(longitude)
        if (coordenada == ultimaCoordenada && agoraMs - ultimaConsultaMs < intervaloMs) return false
        emAndamento = true
        ultimaCoordenada = coordenada
        ultimaConsultaMs = agoraMs
        return true
    }

    /** A consulta começada por [comecar] acabou (com ou sem resposta). */
    @Synchronized
    fun terminou() {
        emAndamento = false
    }

    private fun arredondar(grau: Double): Long = (grau * 1000.0).roundToLong()

    companion object {
        const val INTERVALO_MS = 5 * 60_000L
    }
}
