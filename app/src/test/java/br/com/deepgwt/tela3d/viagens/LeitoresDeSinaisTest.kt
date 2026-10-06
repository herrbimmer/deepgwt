package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitoresDeSinaisTest {

    @Test
    fun `READY ligado`() {
        for (t in listOf("1", "{1,2}", "2", "abc")) assertEquals(t, true, LeitoresDeSinais.prontoDe(t))
    }

    @Test
    fun `READY desligado`() {
        for (t in listOf("0", "-1", "{0,1}", "{-1,0}")) assertEquals(t, false, LeitoresDeSinais.prontoDe(t))
    }

    @Test
    fun `READY vazio ou nulo e ignorado`() {
        assertNull(LeitoresDeSinais.prontoDe(""))
        assertNull(LeitoresDeSinais.prontoDe("  "))
        assertNull(LeitoresDeSinais.prontoDe(null))
    }

    @Test
    fun `combustao ligada so com 1 ou true`() {
        for (t in listOf("1", "true", "TRUE", "{1,2}", " 1 ")) assertEquals(t, true, LeitoresDeSinais.combustaoDe(t))
        for (t in listOf("0", "{0}", "yes")) assertEquals(t, false, LeitoresDeSinais.combustaoDe(t))
        assertNull(LeitoresDeSinais.combustaoDe(""))
    }

    @Test
    fun `numero`() {
        val casos = mapOf(
            "12.5" to 12.5, "12,5" to 12.5, "{3,45.5}" to 45.5, "{45.5}" to 45.5, "  7 " to 7.0,
            "1e3" to 1000.0, "{1,}" to 1.0, "{ 1 , 2.5 }" to 2.5,
        )
        for ((texto, esperado) in casos) assertEquals(texto, esperado, LeitoresDeSinais.numeroDe(texto), 0.0)
        for (texto in listOf("abc", "", "Infinity", "NaN")) assertTrue(texto, LeitoresDeSinais.numeroDe(texto).isNaN())
        assertTrue(LeitoresDeSinais.numeroDe(null).isNaN())
    }

    @Test
    fun `consumo valido`() {
        assertEquals(Consumo(1, 7.5), LeitoresDeSinais.consumoDe("7.5"))
        assertEquals(Consumo(1, 7.5), LeitoresDeSinais.consumoDe("{1,7.5}"))
        assertEquals(Consumo(4, 1.2), LeitoresDeSinais.consumoDe("{4,1.2}"))
        assertEquals(Consumo(1, 60.0), LeitoresDeSinais.consumoDe("{1,60}"))
        assertEquals(Consumo(4, 10.0), LeitoresDeSinais.consumoDe("{4,10}"))
    }

    @Test
    fun `consumo de outro modo e neutro`() {
        for (t in listOf("{0,3}", "{2,3}", "{3,0}")) assertEquals(t, Consumo(0, 0.0), LeitoresDeSinais.consumoDe(t))
    }

    @Test
    fun `consumo invalido e sem consumo`() {
        val invalidos = listOf("{1,60.1}", "{4,10.1}", "{1,65535}", "6553.5", "1023", "-1", "{1,-1}", "abc", "{1,x}", "{1}")
        for (t in invalidos) {
            val c = LeitoresDeSinais.consumoDe(t)
            assertEquals(t, 0, c.modo)
            assertTrue(t, c.valor.isNaN())
        }
    }

    @Test
    fun `fluxo de potencia`() {
        assertEquals(true, LeitoresDeSinais.fluxoDe("v1|1|1|0|0"))
        assertEquals(true, LeitoresDeSinais.fluxoDe("v1|1|1|0|0|9"))
        assertEquals(false, LeitoresDeSinais.fluxoDe("v1|1|0|0|0"))
        assertEquals(false, LeitoresDeSinais.fluxoDe("v1|0|2|0|0"))
        for (t in listOf("v1|1|1|0", "v2|1|1|0|0", "x", "")) assertNull(t, LeitoresDeSinais.fluxoDe(t))
    }

    @Test
    fun `faixa de validade`() {
        assertTrue(LeitoresDeSinais.dentroDaFaixa(0.0, 0.0, 100.0))
        assertTrue(LeitoresDeSinais.dentroDaFaixa(100.0, 0.0, 100.0))
        for (v in listOf(100.0001, -1.0, 65535.0, 6553.5, 1023.0, 2047.0, Double.NaN)) {
            assertFalse("$v", LeitoresDeSinais.dentroDaFaixa(v, 0.0, 100.0))
        }
        assertTrue(LeitoresDeSinais.dentroDaFaixa(300.0, 0.0, 300.0))
        assertTrue(LeitoresDeSinais.dentroDaFaixa(250.0, 0.0, 300.0))
        assertFalse(LeitoresDeSinais.dentroDaFaixa(300.0, 0.0, 100.0))
    }

    @Test
    fun `as chaves cruas sao dez e incluem hodometro e tanque`() {
        assertEquals(10, ChavesDeSinais.CRUAS.size)
        assertTrue(ChavesDeSinais.ehCrua(ChavesDeSinais.HODOMETRO))
        assertTrue(ChavesDeSinais.ehCrua(ChavesDeSinais.NIVEL_DO_TANQUE))
        assertFalse(ChavesDeSinais.ehCrua(ChavesDeSinais.VELOCIDADE))
    }
}
