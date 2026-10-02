package br.com.opengw.painel

import br.com.opengw.painel.TeclaDoVolante.BACK
import br.com.opengw.painel.TeclaDoVolante.DOWN
import br.com.opengw.painel.TeclaDoVolante.ENTER
import br.com.opengw.painel.TeclaDoVolante.ENTER_LONG
import br.com.opengw.painel.TeclaDoVolante.HOME
import br.com.opengw.painel.TeclaDoVolante.LEFT
import br.com.opengw.painel.TeclaDoVolante.RIGHT
import br.com.opengw.painel.TeclaDoVolante.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V1 e V2 de `projetores-cartao.md`. */
class ArbitroDoCartaoTest {

    private fun ignora(
        anterior: Int, novo: Int, desdeTecla: Long, tecla: TeclaDoVolante?, desdeSintetico: Long, alvo: Int,
        projecao: Boolean = false, sintetica: Boolean = false,
    ) = ArbitroDoCartao.ignorarCartaoDoCarro(anterior, novo, desdeTecla, tecla, desdeSintetico, alvo, projecao, sintetica)

    @Test
    fun v1_arbitro() {
        assertTrue(ignora(1, 3, 616, RIGHT, 616, 1))
        assertTrue(ignora(1, 1, 616, RIGHT, 616, 1))
        assertTrue(ignora(1, 0, -1, null, -1, -1))
        assertFalse(ignora(1, 3, -1, null, -1, -1))
        assertTrue(ignora(0, 3, -1, null, -1, -1))
        assertFalse(ignora(0, 3, 250, RIGHT, -1, -1))
        assertFalse(ignora(0, 1, 250, HOME, -1, -1))
        assertTrue(ignora(0, 1, -1, null, -1, -1))
        assertTrue(ignora(3, 1, 50, DOWN, 4_282, 3))
        assertTrue(ignora(3, 1, 282, UP, 4_000, 3))
        assertTrue(ignora(3, 0, 1_884, ENTER, 4_282, 3))
        assertFalse(ignora(3, 1, 500, BACK, -1, -1))
        assertFalse(ignora(3, 1, 4_000, DOWN, -1, -1))
        assertTrue(ignora(3, 1, 1_527, RIGHT, 1_536, 3, projecao = false, sintetica = true))
        assertTrue(ignora(3, 1, 30_001, RIGHT, 30_000, 3, projecao = false, sintetica = true))
        assertFalse(ignora(3, 1, 200, BACK, -1, -1, projecao = false, sintetica = false))
        assertFalse(ignora(3, 0, 200, HOME, -1, -1, projecao = false, sintetica = false))
        assertFalse(ignora(3, 1, 5, BACK, 5_000, 3, projecao = false, sintetica = true))
        assertFalse(ignora(3, 0, 5, HOME, 5_000, 3, projecao = false, sintetica = true))
        assertFalse(ignora(3, 1, 5, BACK, 5_000, 3, projecao = true, sintetica = true))
        assertTrue(ignora(3, 1, -1, null, -1, -1, projecao = true))
        assertFalse(ignora(3, 1, 500, BACK, -1, -1, projecao = true))
        assertFalse(ignora(3, 0, 2_400, LEFT, -1, -1))
        assertFalse(ignora(1, 0, 2_400, LEFT, -1, -1))
        assertTrue(ignora(1, 0, 2_600, LEFT, -1, -1))
        assertTrue(ignora(3, 1, 3_400, ENTER_LONG, -1, -1))
    }

    @Test
    fun v2_teclas() {
        val esperado = mapOf(
            1024 to UP, 1025 to DOWN, 1026 to LEFT, 1027 to RIGHT, 1028 to ENTER, 1029 to HOME, 1030 to BACK,
            1033 to TeclaDoVolante.UP_LONG, 1034 to TeclaDoVolante.DOWN_LONG, 1037 to ENTER_LONG, 1039 to TeclaDoVolante.BACK_LONG,
        )
        for ((codigo, tecla) in esperado) assertEquals(tecla, TeclaDoVolante.doCodigo(codigo))
        for (codigo in listOf(1031, 1032, 1035, 1036, 1038, 0, -1)) assertNull(TeclaDoVolante.doCodigo(codigo))
        assertTrue(LEFT.trocaCartao)
        assertTrue(RIGHT.trocaCartao)
        assertFalse(ENTER.trocaCartao)
        assertTrue(ArbitroDoCartao.tratarAcao(1))
        assertFalse(ArbitroDoCartao.tratarAcao(0))
    }

    @Test
    fun v2_anel() {
        assertEquals(1, ArbitroDoCartao.proximoCartao(0, RIGHT))
        assertEquals(3, ArbitroDoCartao.proximoCartao(1, RIGHT))
        assertEquals(0, ArbitroDoCartao.proximoCartao(3, RIGHT))
        assertEquals(3, ArbitroDoCartao.proximoCartao(0, LEFT))
        assertEquals(1, ArbitroDoCartao.proximoCartao(3, LEFT))
        assertEquals(0, ArbitroDoCartao.proximoCartao(1, LEFT))
        assertEquals(1, ArbitroDoCartao.proximoCartao(7, RIGHT))
        assertEquals(3, ArbitroDoCartao.proximoCartao(7, LEFT))
        assertEquals(1, ArbitroDoCartao.proximoCartao(1, UP))
    }

    @Test
    fun soltaPosseDoAr() {
        for (t in listOf(LEFT, RIGHT, HOME, BACK)) assertTrue(ArbitroDoCartao.soltaPosseDoAr(t))
        for (t in listOf(UP, DOWN, ENTER, ENTER_LONG, TeclaDoVolante.BACK_LONG, null)) assertFalse(ArbitroDoCartao.soltaPosseDoAr(t))
    }
}
