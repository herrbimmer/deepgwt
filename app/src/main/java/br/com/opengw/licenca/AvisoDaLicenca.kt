package br.com.opengw.licenca

/**
 * O que dizer sobre as horas da licença, e quando (Alpha 8.1). Kotlin puro.
 *
 * Só ao ligar o carro, parado, uma vez por partida — nunca no meio do caminho. Três casos, do mais
 * importante para o menos:
 *   FIM          as horas de um nível acabaram e o OPEN GW desceu de nível (mostrado na partida
 *                seguinte à queda, que só acontece com o carro desligado);
 *   UMA_HORA     falta menos de 1 hora do nível ativo;
 *   POUCO        resta menos de 10% das horas do nível ativo.
 * O mesmo texto (menos o FIM) fica em Informações → Licença e no instalador enquanto valer.
 */
object AvisoDaLicenca {
    enum class Tipo(val id: String) { FIM("fim"), UMA_HORA("uma-hora"), POUCO("pouco") }

    data class Aviso(val tipo: Tipo, val texto: String)

    const val FRACAO_POUCO = 0.10
    const val UMA_HORA_SEG = 3600L

    /** "12 h", "1 h 30 min", "45 min". */
    fun duracao(seg: Long): String {
        val min = (seg + 59) / 60
        val h = min / 60
        val m = min % 60
        return when {
            h == 0L -> "$m min"
            m == 0L || h >= 10 -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** O aviso do saldo do nível ativo, ou null se está tudo bem (ou é vitalícia). */
    fun doSaldo(ativo: Saldo?): Aviso? {
        if (ativo == null || ativo.vitalicia || !ativo.temSaldo) return null
        val nome = ativo.nivel.titulo
        return when {
            ativo.restanteSeg < UMA_HORA_SEG ->
                Aviso(Tipo.UMA_HORA, "Falta menos de 1 hora de $nome. Veja em Informações → Licença.")
            ativo.fracaoRestante < FRACAO_POUCO ->
                Aviso(Tipo.POUCO, "Restam ${duracao(ativo.restanteSeg)} de $nome. Veja em Informações → Licença.")
            else -> null
        }
    }

    fun daQueda(de: Nivel, para: Nivel): Aviso =
        Aviso(
            Tipo.FIM,
            "Suas horas de ${de.titulo} acabaram. O OPEN GW voltou ao ${para.titulo}" +
                (if (para == Nivel.DRIVE) ", o nível grátis." else ".")
        )

    /** O que mostrar na partida: a queda tem prioridade sobre o saldo. */
    fun naPartida(ativo: Saldo?, queda: Pair<Nivel, Nivel>?): Aviso? =
        queda?.let { daQueda(it.first, it.second) } ?: doSaldo(ativo)
}
