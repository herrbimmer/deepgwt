// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * O ciclo de cobrança do chip: começa à meia-noite local do dia de renovação mais recente que não
 * passa de "agora". Mês sem esse dia usa o último dia dele (dia 31 em fevereiro de 2026 = 28), e
 * o ciclo começa nesse dia efetivo.
 */
internal object CicloDeCobranca {

    /** Meia-noite local, em ms desde 1970, do início do ciclo que contém [agoraMs]. */
    fun inicioEmMs(agoraMs: Long, diaDoCiclo: Int, fuso: ZoneId): Long =
        inicio(agoraMs, diaDoCiclo, fuso).atStartOfDay(fuso).toInstant().toEpochMilli()

    /** "AAAA-M" do início do ciclo, mês sem zero à esquerda: `"2026-9"`. */
    fun etiqueta(agoraMs: Long, diaDoCiclo: Int, fuso: ZoneId): String {
        val inicio = inicio(agoraMs, diaDoCiclo, fuso)
        return "${inicio.year}-${inicio.monthValue}"
    }

    private fun inicio(agoraMs: Long, diaDoCiclo: Int, fuso: ZoneId): LocalDate {
        val dia = diaDoCiclo.coerceIn(1, 31)
        val agora = Instant.ofEpochMilli(agoraMs)
        val hoje = agora.atZone(fuso).toLocalDate()
        val doMes = diaEfetivo(hoje.withDayOfMonth(1), dia)
        if (!doMes.atStartOfDay(fuso).toInstant().isAfter(agora)) return doMes
        return diaEfetivo(hoje.withDayOfMonth(1).minusMonths(1), dia)
    }

    /** O dia de renovação dentro do mês de [primeiroDoMes]: no máximo o último dia dele. */
    private fun diaEfetivo(primeiroDoMes: LocalDate, dia: Int): LocalDate =
        primeiroDoMes.withDayOfMonth(minOf(dia, primeiroDoMes.lengthOfMonth()))
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
