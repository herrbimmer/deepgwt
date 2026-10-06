// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneId

/** O modo de horário fixo (`specs/brilho-automatico.md`, 4). */
class HorarioFixoTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private fun m(hora: Int, minuto: Int = 0) = HorarioFixo.minutoDoDia(hora, minuto)

    @Test
    fun `noite que atravessa a meia-noite`() {
        val inicio = m(20)
        val fim = m(6)

        assertFalse(HorarioFixo.ehNoite(m(19, 59), inicio, fim))
        assertTrue(HorarioFixo.ehNoite(m(20), inicio, fim))
        assertTrue(HorarioFixo.ehNoite(m(23, 59), inicio, fim))
        assertTrue(HorarioFixo.ehNoite(m(0), inicio, fim))
        assertTrue(HorarioFixo.ehNoite(m(5, 59), inicio, fim))
        assertFalse(HorarioFixo.ehNoite(m(6), inicio, fim))
        assertFalse(HorarioFixo.ehNoite(m(12), inicio, fim))
    }

    @Test
    fun `noite dentro do mesmo dia`() {
        val inicio = m(1, 30)
        val fim = m(5)

        assertFalse(HorarioFixo.ehNoite(m(1, 29), inicio, fim))
        assertTrue(HorarioFixo.ehNoite(m(1, 30), inicio, fim))
        assertTrue(HorarioFixo.ehNoite(m(4, 59), inicio, fim))
        assertFalse(HorarioFixo.ehNoite(m(5), inicio, fim))
        assertFalse(HorarioFixo.ehNoite(m(23), inicio, fim))
    }

    @Test
    fun `inicio igual ao fim nunca e noite`() {
        for (minuto in 0 until 24 * 60) assertFalse(HorarioFixo.ehNoite(minuto, m(20), m(20)))
    }

    @Test
    fun `hora e minuto fora da faixa ficam dentro dela`() {
        assertEquals(23 * 60 + 59, HorarioFixo.minutoDoDia(25, 70))
        assertEquals(0, HorarioFixo.minutoDoDia(-1, -5))
    }

    @Test
    fun `minuto agora no relogio local`() {
        assertEquals(m(23, 15), HorarioFixo.minutoAgora(ms("2026-10-02T02:15:30Z"), saoPaulo))
    }

    @Test
    fun `proxima vez hoje se ainda nao passou, senao amanha`() {
        val vinte = m(20)

        assertEquals(ms("2026-10-01T20:00-03:00"), HorarioFixo.proximaVez(ms("2026-10-01T19:00-03:00"), saoPaulo, vinte))
        assertEquals(ms("2026-10-02T20:00-03:00"), HorarioFixo.proximaVez(ms("2026-10-01T21:00-03:00"), saoPaulo, vinte))
        // Na hora exata já conta como passado: o alarme que acabou de tocar não se repete.
        assertEquals(ms("2026-10-02T20:00-03:00"), HorarioFixo.proximaVez(ms("2026-10-01T20:00-03:00"), saoPaulo, vinte))
        assertEquals(ms("2026-10-02T06:00-03:00"), HorarioFixo.proximaVez(ms("2026-10-01T23:00-03:00"), saoPaulo, m(6)))
    }

    @Test
    fun `proxima vez num horario que o horario de verao pula`() {
        // Nova York, 08/03/2026: das 2:00 o relógio vai para as 3:00; 2:30 vira 3:30.
        val novaYork = ZoneId.of("America/New_York")

        val proxima = HorarioFixo.proximaVez(ms("2026-03-08T01:00-05:00"), novaYork, m(2, 30))

        assertEquals(ms("2026-03-08T03:30-04:00"), proxima)
    }

    private fun ms(data: String): Long = OffsetDateTime.parse(data).toInstant().toEpochMilli()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
