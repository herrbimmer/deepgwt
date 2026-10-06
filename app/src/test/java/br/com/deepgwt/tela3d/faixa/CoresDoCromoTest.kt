package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoresDoCromoTest {
    @Test
    fun `misturar descarta o alfa e devolve cor opaca`() {
        val r = CoresDoCromo.misturar(0xE6303034.toInt(), 0xFFFFFFFF.toInt(), 0.10f)
        assertEquals(0xFF, r ushr 24)
        // 48 + 207 * 0,1 = 68,7 -> 69; 52 + 203 * 0,1 = 72,3 -> 72
        assertEquals(0xFF454548.toInt(), r)
    }

    @Test
    fun `misturar limita a fracao em 0 e 1`() {
        val a = 0x80102030.toInt()
        val b = 0x10FFEEDD
        assertEquals(0xFF102030.toInt(), CoresDoCromo.misturar(a, b, -3f))
        assertEquals(0xFFFFEEDD.toInt(), CoresDoCromo.misturar(a, b, 7f))
        assertEquals(0xFF102030.toInt(), CoresDoCromo.misturar(a, b, Float.NaN))
    }

    @Test
    fun `misturar na metade`() {
        assertEquals(0xFF808080.toInt(), CoresDoCromo.misturar(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun `comAlfa troca so o alfa`() {
        assertEquals(0x332AA7B7, CoresDoCromo.comAlfa(0xFF2AA7B7.toInt(), 0x33))
        assertEquals(0x002AA7B7, CoresDoCromo.comAlfa(0xFF2AA7B7.toInt(), -5))
        assertEquals(0xFF2AA7B7.toInt(), CoresDoCromo.comAlfa(0x112AA7B7, 999))
    }

    @Test
    fun `cores por tema`() {
        assertEquals(0xFFFFFFFF.toInt(), CoresDoCromo.corDeRotulo(false))
        assertEquals(0xFF3D4550.toInt(), CoresDoCromo.corDeRotulo(true))
        assertEquals(0xCCFFFFFF.toInt(), CoresDoCromo.corDeRotuloMuted(false))
        assertEquals(0xFF9AA3AE.toInt(), CoresDoCromo.corDeRotuloMuted(true))
        assertEquals(0xE8FFFFFF.toInt(), CoresDoCromo.corDeGlifo(false))
        assertEquals(0xFF7E8996.toInt(), CoresDoCromo.corDeGlifo(true))
    }

    @Test
    fun `lerAcento`() {
        assertEquals(0xFF2AA7B7.toInt(), CoresDoCromo.lerAcento("#2AA7B7"))
        assertEquals(0xFF2255CC.toInt(), CoresDoCromo.lerAcento("#25c"))
        assertNull(CoresDoCromo.lerAcento("2AA7B7"))
        assertNull(CoresDoCromo.lerAcento(null))
        assertNull(CoresDoCromo.lerAcento("#+12345"))
    }

    @Test
    fun `lerCorOuPadrao aceita 6 e 8 digitos e cai no padrao`() {
        val padrao = 0xFF3D4650.toInt()
        assertEquals(0xFFE53935.toInt(), CoresDoCromo.lerCorOuPadrao("#E53935", padrao))
        assertEquals(0x80E53935.toInt(), CoresDoCromo.lerCorOuPadrao("#80E53935", padrao))
        assertEquals(padrao, CoresDoCromo.lerCorOuPadrao("lixo", padrao))
        assertEquals(padrao, CoresDoCromo.lerCorOuPadrao("#12", padrao))
        assertEquals(padrao, CoresDoCromo.lerCorOuPadrao(null, padrao))
    }
}
