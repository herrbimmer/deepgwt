package br.com.deepgwt.managers

import br.com.deepgwt.managers.IntencaoDoGestoDeAjuste.Eixo
import br.com.deepgwt.managers.IntencaoDoGestoDeAjuste.interpretar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class IntencaoDoGestoDeAjusteTest {

    // Tela larga: o eixo X é 2,7x o Y, como a central de 1920x720.
    private val rx = 1920
    private val ry = 720

    @Test
    fun `arrasto torto para cima ainda e vertical`() {
        // 100 para cima, 60 para o lado: antes de existir tolerância isto já era vertical, mas o
        // contrato é este e não pode regredir.
        val i = interpretar(dx = 60.0, dy = -100.0, rangeX = rx, rangeY = ry)
        assertNotNull(i)
        assertEquals(Eixo.VERTICAL, i!!.eixo)
        assertEquals(true, i.degraus > 0)
    }

    @Test
    fun `arrasto torto para o lado ainda e horizontal`() {
        val i = interpretar(dx = 160.0, dy = 110.0, rangeX = rx, rangeY = ry)
        assertEquals(Eixo.HORIZONTAL, i!!.eixo)
        assertEquals(true, i.degraus > 0)
    }

    @Test
    fun `o eixo que mais andou decide, quase na diagonal`() {
        assertEquals(Eixo.VERTICAL, interpretar(99.0, 100.0, rx, ry)!!.eixo)
        assertEquals(Eixo.HORIZONTAL, interpretar(101.0, 100.0, rx, ry)!!.eixo)
    }

    @Test
    fun `sinais - cima e direita positivos, baixo e esquerda negativos`() {
        assertEquals(true, interpretar(0.0, -200.0, rx, ry)!!.degraus > 0)
        assertEquals(true, interpretar(0.0, 200.0, rx, ry)!!.degraus < 0)
        assertEquals(true, interpretar(200.0, 0.0, rx, ry)!!.degraus > 0)
        assertEquals(true, interpretar(-200.0, 0.0, rx, ry)!!.degraus < 0)
    }

    @Test
    fun `a mesma distancia vale igual para cima e para o lado`() {
        val cima = interpretar(0.0, -150.0, rx, ry)!!
        val lado = interpretar(150.0, 0.0, rx, ry)!!
        assertEquals(cima.degraus, lado.degraus)
    }

    @Test
    fun `um arrasto curto pelo menos de 8 por cento do menor eixo nao vale`() {
        assertNull(interpretar(0.0, -50.0, rx, ry)) // 50/720 = 0,069
        assertNull(interpretar(50.0, 0.0, rx, ry))
        assertNotNull(interpretar(0.0, -60.0, rx, ry)) // 60/720 = 0,083
    }

    @Test
    fun `arrasto mais longo mexe mais, com teto`() {
        val curto = interpretar(0.0, -70.0, rx, ry)!!.degraus
        val medio = interpretar(0.0, -300.0, rx, ry)!!.degraus
        val enorme = interpretar(0.0, -5000.0, rx, ry)!!.degraus
        assertEquals(1, curto)
        assertEquals(true, medio > curto)
        assertEquals(IntencaoDoGestoDeAjuste.MAX_DEGRAUS, enorme)
    }

    @Test
    fun `digitalizador quadrado do emulador usa a mesma regua`() {
        val i = interpretar(0.0, -4000.0, 32767, 32767) // 12% do eixo
        assertEquals(Eixo.VERTICAL, i!!.eixo)
        assertEquals(1 + ((4000.0 / 32767 - 0.08) / 0.10).let { Math.round(it).toInt() }, i.degraus)
    }
}
