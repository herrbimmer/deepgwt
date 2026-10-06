// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** A rampa do modo sol e a escolha do próximo alarme (`specs/brilho-automatico.md`, 5.4 e 5.5). */
class DiaDeSolTest {

    private val nascer = 1_790_000_000_000L
    private val por = nascer + 12 * HORA

    private fun dia(nivelDia: Int = 10, nivelNoite: Int = 1, passoMs: Long = 5 * MIN, porMs: Long = por) =
        DiaDeSol(NascerEPor(nascer, porMs), nivelDia, nivelNoite, passoMs)

    // ── nível ──

    @Test
    fun `rampa do nascer de 1 a 10 com passo de 5 min dura 45 min centrada no nascer`() {
        val d = dia()
        val inicio = nascer - 22 * MIN - 30 * SEG

        assertEquals(inicio, d.inicioDoNascer)
        assertEquals(1, d.nivel(inicio - 1))
        for (passos in 0..9) {
            assertEquals("depois de $passos passos", 1 + passos, d.nivel(inicio + passos * 5 * MIN))
        }
        assertEquals(1, d.nivel(inicio + 5 * MIN - 1))
        assertEquals(5, d.nivel(nascer))
        assertEquals(9, d.nivel(inicio + 45 * MIN - 1))
        assertEquals(10, d.nivel(inicio + 45 * MIN))
        assertEquals(10, d.nivel(nascer + 3 * HORA))
    }

    @Test
    fun `rampa do por de 10 a 1`() {
        val d = dia()
        val inicio = por - 22 * MIN - 30 * SEG

        assertEquals(inicio, d.inicioDoPor)
        assertEquals(10, d.nivel(inicio - 1))
        assertEquals(10, d.nivel(inicio))
        assertEquals(9, d.nivel(inicio + 5 * MIN))
        assertEquals(6, d.nivel(por))
        assertEquals(2, d.nivel(inicio + 45 * MIN - 1))
        assertEquals(1, d.nivel(inicio + 45 * MIN))
        assertEquals(1, d.nivel(por + 5 * HORA))
    }

    @Test
    fun `noite mais clara que o dia desce no nascer e sobe no por`() {
        val d = dia(nivelDia = 3, nivelNoite = 7)
        val inicioDoNascer = nascer - 10 * MIN
        val inicioDoPor = por - 10 * MIN

        assertEquals(7, d.nivel(inicioDoNascer - 1))
        assertEquals(listOf(7, 6, 5, 4, 3), (0..4).map { d.nivel(inicioDoNascer + it * 5 * MIN) })
        assertEquals(3, d.nivel(nascer + 6 * HORA))
        assertEquals(listOf(3, 4, 5, 6, 7), (0..4).map { d.nivel(inicioDoPor + it * 5 * MIN) })
        assertEquals(7, d.nivel(por + HORA))
    }

    @Test
    fun `sem diferenca entre os niveis nao ha rampa`() {
        val d = dia(nivelDia = 5, nivelNoite = 5)

        for (t in listOf(nascer - HORA, nascer - 1, nascer, por - 1, por, por + HORA)) {
            assertEquals(5, d.nivel(t))
            assertFalse(d.dentroDeRampa(t))
        }
    }

    @Test
    fun `passo invalido troca seca no nascer e no por`() {
        for (passo in listOf(0L, -5 * MIN)) {
            val d = dia(passoMs = passo)

            assertEquals(1, d.nivel(nascer - 1))
            assertEquals(10, d.nivel(nascer))
            assertEquals(10, d.nivel(por - 1))
            assertEquals(1, d.nivel(por))
            assertFalse(d.dentroDeRampa(nascer))
        }
    }

    @Test
    fun `dia curto demais para as rampas nao salta nem passa dos limites`() {
        // Dia de 1 h com rampas de 4 h 30 (gap 9 × 30 min): perto do círculo polar.
        val d = dia(passoMs = 30 * MIN, porMs = nascer + HORA)

        var anterior = d.nivel(nascer - 6 * HORA)
        var t = nascer - 6 * HORA
        while (t <= nascer + 7 * HORA) {
            val nivel = d.nivel(t)
            assertTrue("nível $nivel em ${t - nascer} ms", nivel in 1..10)
            assertTrue("salto de $anterior para $nivel em ${t - nascer} ms", abs(nivel - anterior) <= 1)
            anterior = nivel
            t += MIN
        }
        assertEquals(1, d.nivel(nascer + 7 * HORA))
    }

    // ── próximo alarme ──

    private val amanha = DiaDeSol(NascerEPor(nascer + 24 * HORA, por + 24 * HORA), 10, 1, 5 * MIN)
    private val inicioDeAmanha = nascer + 15 * HORA

    private fun proximo(d: DiaDeSol, agora: Long, temAmanha: Boolean = true) =
        d.proximaAvaliacao(agora, { if (temAmanha) amanha else null }, inicioDeAmanha, 30 * SEG)

    @Test
    fun `antes da rampa do nascer acorda no inicio dela`() {
        val d = dia()

        assertEquals(d.inicioDoNascer, proximo(d, nascer - 3 * HORA))
    }

    @Test
    fun `dentro de uma rampa acorda daqui a um passo`() {
        val d = dia()

        val agora = d.inicioDoNascer + 7 * MIN
        assertTrue(d.dentroDeRampa(agora))
        assertEquals(agora + 5 * MIN, proximo(d, agora))
        val noPor = d.inicioDoPor + 1
        assertEquals(noPor + 5 * MIN, proximo(d, noPor))
    }

    @Test
    fun `entre as rampas acorda no inicio da rampa do por`() {
        val d = dia()

        assertEquals(d.inicioDoPor, proximo(d, nascer + 2 * HORA))
    }

    @Test
    fun `depois da rampa do por acorda na rampa do nascer de amanha`() {
        val d = dia()

        assertEquals(amanha.inicioDoNascer, proximo(d, por + 2 * HORA))
    }

    @Test
    fun `sem nascer amanha acorda no comeco de amanha`() {
        val d = dia()

        assertEquals(inicioDeAmanha, proximo(d, por + 2 * HORA, temAmanha = false))
    }

    @Test
    fun `nunca menos de 30 s a frente`() {
        val d = dia()
        val agora = d.inicioDoNascer - 10 * SEG

        assertEquals(agora + 30 * SEG, proximo(d, agora))
    }

    @Test
    fun `troca seca acorda no proprio evento`() {
        val d = dia(passoMs = 0)

        assertEquals(nascer, proximo(d, nascer - HORA))
        assertEquals(por, proximo(d, nascer + HORA))
    }

    private companion object {
        const val SEG = 1_000L
        const val MIN = 60 * SEG
        const val HORA = 60 * MIN
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
