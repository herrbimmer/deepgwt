package br.com.opengw.brilho

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** O modo de horário fixo: a noite vai de um horário a outro do relógio local. Sem Android. */
internal object HorarioFixo {

    /** Minutos desde a meia-noite; hora e minuto fora da faixa são trazidos para dentro dela. */
    fun minutoDoDia(hora: Int, minuto: Int): Int = hora.coerceIn(0, 23) * 60 + minuto.coerceIn(0, 59)

    /**
     * Noite se [minuto] está em [inicio, fim). Início depois do fim: a noite atravessa a
     * meia-noite. Início igual ao fim: intervalo vazio, nunca é noite.
     */
    fun ehNoite(minuto: Int, inicio: Int, fim: Int): Boolean = when {
        inicio < fim -> minuto >= inicio && minuto < fim
        inicio > fim -> minuto >= inicio || minuto < fim
        else -> false
    }

    /** Minuto do dia no relógio local em [agoraMs]. */
    fun minutoAgora(agoraMs: Long, zona: ZoneId): Int {
        val hora = Instant.ofEpochMilli(agoraMs).atZone(zona)
        return hora.hour * 60 + hora.minute
    }

    /** A próxima vez, depois de [agoraMs], em que o relógio local marca [minuto]: hoje ou amanhã. */
    fun proximaVez(agoraMs: Long, zona: ZoneId, minuto: Int): Long {
        val hoje = Instant.ofEpochMilli(agoraMs).atZone(zona).toLocalDate()
        val deHoje = instante(hoje, minuto, zona)
        return if (deHoje > agoraMs) deHoje else instante(hoje.plusDays(1), minuto, zona)
    }

    private fun instante(dia: LocalDate, minuto: Int, zona: ZoneId): Long =
        dia.atTime(minuto / 60, minuto % 60).atZone(zona).toInstant().toEpochMilli()
}
