package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculoDoElasticoTest {
    private fun calculo() = CalculoDoElastico(56f)

    @Test
    fun `no meio da rolagem nao consome`() {
        val c = calculo()
        assertFalse(c.mover(30f, noInicio = false, noFim = false))
        assertEquals(0f, c.puxada, 0f)
    }

    @Test
    fun `arrastar para a direita no inicio puxa 0,45 do deslocamento`() {
        val c = calculo()
        assertTrue(c.mover(20f, noInicio = true, noFim = false))
        assertEquals(9f, c.puxada, 0.0001f)
        assertTrue(c.mover(20f, noInicio = true, noFim = false))
        assertEquals(18f, c.puxada, 0.0001f)
    }

    @Test
    fun `arrastar para a esquerda no fim puxa para o lado negativo`() {
        val c = calculo()
        assertTrue(c.mover(-20f, noInicio = false, noFim = true))
        assertEquals(-9f, c.puxada, 0.0001f)
    }

    @Test
    fun `arrastar para a esquerda no inicio e rolagem normal`() {
        val c = calculo()
        assertFalse(c.mover(-20f, noInicio = true, noFim = false))
        assertEquals(0f, c.puxada, 0f)
    }

    @Test
    fun `puxada limitada a mais ou menos 56`() {
        val c = calculo()
        repeat(50) { c.mover(40f, true, false) }
        assertEquals(56f, c.puxada, 0.0001f)
        assertTrue(c.mover(40f, true, false))
        assertEquals(56f, c.puxada, 0.0001f)
        val d = calculo()
        repeat(50) { d.mover(-40f, false, true) }
        assertEquals(-56f, d.puxada, 0.0001f)
    }

    @Test
    fun `contra o lado da puxada ela vai a zero de uma vez e a rolagem normal segue`() {
        val c = calculo()
        c.mover(100f, true, false)
        assertTrue(c.puxada > 0f)
        assertFalse(c.mover(-1f, true, false))
        assertEquals(0f, c.puxada, 0f)
        assertFalse(c.puxando)
    }

    @Test
    fun `contra o lado da puxada tambem no lado negativo`() {
        val c = calculo()
        c.mover(-100f, false, true)
        assertFalse(c.mover(5f, false, true))
        assertEquals(0f, c.puxada, 0f)
    }

    @Test
    fun `puxando consome mesmo com deslocamento zero`() {
        val c = calculo()
        c.mover(40f, true, false)
        assertTrue(c.mover(0f, false, false))
    }

    @Test
    fun `soltar volta so se a puxada passa de 0,5`() {
        val c = calculo()
        c.mover(1f, true, false) // 0,45
        assertFalse(c.soltar())
        c.mover(1f, true, false) // 0,9
        assertTrue(c.soltar())
        c.zerar()
        assertEquals(0f, c.puxada, 0f)
        assertFalse(c.soltar())
    }

    @Test
    fun `soltar tambem para puxada negativa`() {
        val c = calculo()
        c.mover(-40f, false, true)
        assertTrue(c.soltar())
    }

    @Test
    fun `constantes do contrato`() {
        assertEquals(0.45f, CalculoDoElastico.FATOR, 0f)
        assertEquals(56f, CalculoDoElastico.LIMITE_DP, 0f)
        assertEquals(280L, CalculoDoElastico.VOLTA_MS)
        assertEquals(1.6f, CalculoDoElastico.FATOR_DA_VOLTA, 0f)
    }
}
