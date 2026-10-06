// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/** Uma avaliação inteira, sem Android: modo, nível e alarmes (`specs/brilho-automatico.md`). */
class PlanejadorTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val goiania = Posicao(-16.686, -49.264)
    private val tromso = Posicao(69.65, 18.96)

    /** Os padrões das preferências. */
    private val padrao = Ajustes(
        usarSol = false,
        nivelDia = 10,
        nivelNoite = 1,
        passoMin = 5,
        inicioDaNoiteHora = 20,
        inicioDaNoiteMinuto = 0,
        fimDaNoiteHora = 6,
        fimDaNoiteMinuto = 0,
    )
    private val peloSol = padrao.copy(usarSol = true)

    private fun planejar(ajustes: Ajustes, agora: String, posicao: Posicao? = null) =
        Planejador.planejar(ajustes, ms(agora), saoPaulo, posicao)

    // ── horário fixo ──

    @Test
    fun `horario fixo de dia aplica o nivel de dia e agenda o inicio e o fim da noite`() {
        val plano = planejar(padrao, "2026-10-01T12:00-03:00")

        assertEquals(Modo.HORARIO_FIXO, plano.modo)
        assertEquals(10, plano.nivel)
        assertEquals(listOf(ms("2026-10-01T20:00-03:00"), ms("2026-10-02T06:00-03:00")), plano.alarmes)
    }

    @Test
    fun `horario fixo depois da meia-noite ainda e noite`() {
        val plano = planejar(padrao, "2026-10-02T02:00-03:00")

        assertEquals(1, plano.nivel)
        assertEquals(listOf(ms("2026-10-02T20:00-03:00"), ms("2026-10-02T06:00-03:00")), plano.alarmes)
    }

    @Test
    fun `horario fixo com minutos`() {
        val ajustes = padrao.copy(inicioDaNoiteHora = 18, inicioDaNoiteMinuto = 45, fimDaNoiteHora = 7, fimDaNoiteMinuto = 15)

        assertEquals(10, planejar(ajustes, "2026-10-01T18:44-03:00").nivel)
        assertEquals(1, planejar(ajustes, "2026-10-01T18:45-03:00").nivel)
        assertEquals(1, planejar(ajustes, "2026-10-02T07:14-03:00").nivel)
        assertEquals(10, planejar(ajustes, "2026-10-02T07:15-03:00").nivel)
    }

    // ── sol: quando não dá, horário fixo ──

    @Test
    fun `modo sol sem posicao cai no horario fixo`() {
        val plano = planejar(peloSol, "2026-10-01T21:00-03:00", posicao = null)

        assertEquals(Modo.HORARIO_FIXO, plano.modo)
        assertEquals(1, plano.nivel)
        assertEquals(2, plano.alarmes.size)
    }

    @Test
    fun `modo sol em dia ou noite polar cai no horario fixo`() {
        for (agora in listOf("2026-06-21T12:00-03:00", "2026-12-21T12:00-03:00")) {
            val plano = planejar(peloSol, agora, tromso)

            assertEquals(agora, Modo.HORARIO_FIXO, plano.modo)
            assertEquals(agora, 10, plano.nivel)
            assertEquals(agora, 2, plano.alarmes.size)
        }
    }

    // ── sol ──

    private val solDeHoje = Sol.nascerEPor(LocalDate.parse("2026-10-01"), goiania.latitude, goiania.longitude)!!
    private val meiaRampa = 22 * MIN + 30 * SEG

    @Test
    fun `modo sol de madrugada fica na noite e acorda na rampa do nascer`() {
        val plano = planejar(peloSol, "2026-10-01T03:00-03:00", goiania)

        assertEquals(Modo.SOL, plano.modo)
        assertEquals(1, plano.nivel)
        assertEquals(listOf(solDeHoje.nascerMs - meiaRampa), plano.alarmes)
    }

    @Test
    fun `modo sol de dia fica no dia e acorda na rampa do por`() {
        val plano = planejar(peloSol, "2026-10-01T12:00-03:00", goiania)

        assertEquals(Modo.SOL, plano.modo)
        assertEquals(10, plano.nivel)
        assertEquals(listOf(solDeHoje.porMs - meiaRampa), plano.alarmes)
    }

    @Test
    fun `modo sol na hora do nascer esta no meio da rampa`() {
        // Passo de 5 min: a rampa começou 22,5 min antes; 4 passos completos, nível 1 + 4.
        val plano = Planejador.planejar(peloSol, solDeHoje.nascerMs, saoPaulo, goiania)

        assertEquals(Modo.SOL, plano.modo)
        assertEquals(5, plano.nivel)
        assertEquals(listOf(solDeHoje.nascerMs + 5 * MIN), plano.alarmes)
    }

    @Test
    fun `modo sol a noite usa o dia civil local e acorda no nascer de amanha`() {
        // 23:00 em Goiânia já é dia 2 em UTC; o "hoje" é o 1º, e o próximo nascer é o do dia 2.
        val agora = "2026-10-01T23:00-03:00"
        val solDeAmanha = Sol.nascerEPor(LocalDate.parse("2026-10-02"), goiania.latitude, goiania.longitude)
        assertNotNull(solDeAmanha)

        val plano = planejar(peloSol, agora, goiania)

        assertEquals(1, plano.nivel)
        assertEquals(listOf(solDeAmanha!!.nascerMs - meiaRampa), plano.alarmes)
        assertTrue(plano.alarmes.single() > ms(agora))
    }

    @Test
    fun `passo fora de 1 a 30 minutos e limitado`() {
        assertEquals(1 * MIN, Planejador.passoMs(padrao.copy(passoMin = 0)))
        assertEquals(1 * MIN, Planejador.passoMs(padrao.copy(passoMin = -3)))
        assertEquals(30 * MIN, Planejador.passoMs(padrao.copy(passoMin = 99)))
        assertEquals(12 * MIN, Planejador.passoMs(padrao.copy(passoMin = 12)))
    }

    @Test
    fun `rampa usa o passo limitado`() {
        // Passo 99 vira 30: rampa de 9 × 30 min, começa 2 h 15 antes do nascer.
        val plano = Planejador.planejar(peloSol.copy(passoMin = 99), solDeHoje.nascerMs, saoPaulo, goiania)

        assertEquals(5, plano.nivel)
        assertEquals(listOf(solDeHoje.nascerMs + 30 * MIN), plano.alarmes)
    }

    private fun ms(data: String): Long = OffsetDateTime.parse(data).toInstant().toEpochMilli()

    private companion object {
        const val SEG = 1_000L
        const val MIN = 60 * SEG
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
