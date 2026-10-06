// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.abs

/** Valores de referência de `docs/independencia/specs/brilho-automatico.md` (±2 min). */
class SolTest {

    @Test
    fun `goiania em 01-10-2026`() {
        val sol = calcular("2026-10-01", -16.686, -49.264)

        assertPerto("2026-10-01T08:59", sol.nascerMs)
        assertPerto("2026-10-01T21:13", sol.porMs)
    }

    @Test
    fun `sao paulo no solsticio de junho`() {
        val sol = calcular("2026-06-21", -23.55, -46.633)

        assertPerto("2026-06-21T09:47", sol.nascerMs)
        assertPerto("2026-06-21T20:28", sol.porMs)
    }

    @Test
    fun `sao paulo no solsticio de dezembro`() {
        val sol = calcular("2026-12-21", -23.55, -46.633)

        assertPerto("2026-12-21T08:16", sol.nascerMs)
        assertPerto("2026-12-21T21:52", sol.porMs)
    }

    @Test
    fun `tromso no dia polar de junho nao tem nascer nem por`() {
        assertNull(Sol.nascerEPor(LocalDate.parse("2026-06-21"), 69.65, 18.96))
    }

    @Test
    fun `tromso na noite polar de dezembro nao tem nascer nem por`() {
        assertNull(Sol.nascerEPor(LocalDate.parse("2026-12-21"), 69.65, 18.96))
    }

    @Test
    fun `longitude leste positiva`() {
        // Tóquio (35,68; 139,69) no equinócio de março: nascer por volta de 05:46 e pôr por volta
        // de 17:52 no horário local (UTC+9). Confere o sinal da longitude e que o dia civil é o
        // do lugar (o nascer cai antes da 0 h UTC).
        val sol = calcular("2026-03-20", 35.68, 139.69)

        assertPerto("2026-03-19T20:46", sol.nascerMs, toleranciaMin = 5)
        assertPerto("2026-03-20T08:52", sol.porMs, toleranciaMin = 5)
    }

    @Test
    fun `coordenada invalida nao calcula`() {
        val dia = LocalDate.parse("2026-10-01")

        assertNull(Sol.nascerEPor(dia, Double.NaN, -49.0))
        assertNull(Sol.nascerEPor(dia, -16.0, Double.POSITIVE_INFINITY))
        assertNull(Sol.nascerEPor(dia, 91.0, 0.0))
        assertNull(Sol.nascerEPor(dia, 0.0, 181.0))
    }

    @Test
    fun `nascer vem antes do por e o dia tem duracao plausivel`() {
        var dia = LocalDate.parse("2026-01-01")
        repeat(365) {
            val sol = calcular(dia.toString(), -16.686, -49.264)
            val horas = (sol.porMs - sol.nascerMs) / 3_600_000.0
            assertTrue("$dia: $horas h", horas in 11.0..13.5)
            dia = dia.plusDays(1)
        }
    }

    private fun calcular(dia: String, latitude: Double, longitude: Double): NascerEPor {
        val sol = Sol.nascerEPor(LocalDate.parse(dia), latitude, longitude)
        assertNotNull("sem nascer/pôr em $dia", sol)
        return sol!!
    }

    private fun assertPerto(esperadoUtc: String, obtidoMs: Long, toleranciaMin: Int = 2) {
        val esperadoMs = LocalDateTime.parse(esperadoUtc).toInstant(ZoneOffset.UTC).toEpochMilli()
        val diferenca = abs(obtidoMs - esperadoMs)
        assertTrue(
            "esperado $esperadoUtc UTC, veio ${Instant.ofEpochMilli(obtidoMs)}",
            diferenca <= toleranciaMin * 60_000L,
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
