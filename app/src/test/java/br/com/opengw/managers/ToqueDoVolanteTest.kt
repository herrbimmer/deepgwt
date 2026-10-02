package br.com.opengw.managers

import br.com.opengw.managers.ToqueDoVolante.Companion.APERTOU
import br.com.opengw.managers.ToqueDoVolante.Companion.SOLTOU
import org.junit.Assert.assertEquals
import org.junit.Test

class ToqueDoVolanteTest {

    private fun toques(vararg eventos: Pair<Int, Long>, codigo: Int = 1024): Int {
        val t = ToqueDoVolante()
        return eventos.count { (acao, em) -> t.ehNovoToque(codigo, acao, em) }
    }

    @Test
    fun `so SOLTOU, como esta central entrega o volante, cada um e um toque`() {
        // É o caso que deixava o HUD parado: ele só andava no ACTION_DOWN.
        assertEquals(3, toques(SOLTOU to 0L, SOLTOU to 150L, SOLTOU to 300L))
    }

    @Test
    fun `APERTOU e SOLTOU do mesmo aperto contam uma vez`() {
        assertEquals(2, toques(APERTOU to 0L, SOLTOU to 90L, APERTOU to 200L, SOLTOU to 260L))
    }

    @Test
    fun `tecla segura repetindo APERTOU conta uma vez`() {
        assertEquals(1, toques(APERTOU to 0L, APERTOU to 300L, APERTOU to 600L, APERTOU to 900L, SOLTOU to 1_200L))
    }

    @Test
    fun `teclas diferentes nao se atrapalham`() {
        val t = ToqueDoVolante()
        assertEquals(true, t.ehNovoToque(1024, APERTOU, 0))
        assertEquals(true, t.ehNovoToque(1025, APERTOU, 10))
        assertEquals(false, t.ehNovoToque(1024, SOLTOU, 20))
        assertEquals(false, t.ehNovoToque(1025, SOLTOU, 30))
        assertEquals(false, t.ehNovoToque(1025, 2, 40))
    }
}
