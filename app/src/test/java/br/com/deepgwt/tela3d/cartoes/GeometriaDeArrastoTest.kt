package br.com.deepgwt.tela3d.cartoes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class GeometriaDeArrastoTest {
    // quatro cartões de 100 com margem 12: esquerdas 0, 112, 224, 336 → centros 50, 162, 274, 386
    private val centros = listOf(50f, 162f, 274f, 386f)

    // --- índice de soltura ---

    @Test fun `sem passar de ninguem fica na origem`() {
        assertEquals(1, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 162f))
        assertEquals(1, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 170f))
        assertEquals(1, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 60f))
    }

    @Test fun `passando do centro de um cartao a direita`() {
        assertEquals(2, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 275f))
        assertEquals(3, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 387f))
    }

    @Test fun `passando do centro a esquerda`() {
        assertEquals(0, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 49f))
        assertEquals(0, GeometriaDeArrasto.indiceDeSoltura(centros, 3, -100f))
        assertEquals(3, GeometriaDeArrasto.indiceDeSoltura(centros, 3, 280f))
        assertEquals(2, GeometriaDeArrasto.indiceDeSoltura(centros, 3, 200f))
    }

    @Test fun `centro exatamente igual nao conta`() {
        assertEquals(1, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 274f))
        assertEquals(2, GeometriaDeArrasto.indiceDeSoltura(centros, 1, 274.5f))
    }

    // --- vizinhos ---

    @Test fun `vizinhos deslizam para a esquerda quando o cartao vai para a direita`() {
        assertArrayEquals(floatArrayOf(0f, 0f, -112f, -112f), GeometriaDeArrasto.deslocamentosDosVizinhos(4, 1, 3, 112f), 0f)
        assertArrayEquals(floatArrayOf(0f, 0f, -112f, 0f), GeometriaDeArrasto.deslocamentosDosVizinhos(4, 1, 2, 112f), 0f)
    }

    @Test fun `vizinhos deslizam para a direita quando o cartao vai para a esquerda`() {
        assertArrayEquals(floatArrayOf(112f, 112f, 0f, 0f), GeometriaDeArrasto.deslocamentosDosVizinhos(4, 2, 0, 112f), 0f)
        assertArrayEquals(floatArrayOf(0f, 112f, 0f, 0f), GeometriaDeArrasto.deslocamentosDosVizinhos(4, 2, 1, 112f), 0f)
    }

    @Test fun `na origem ninguem desliza`() {
        assertArrayEquals(FloatArray(4), GeometriaDeArrasto.deslocamentosDosVizinhos(4, 2, 2, 112f), 0f)
    }

    // --- soltura ---

    @Test fun `deslocamento de soltura soma larguras e margens com o sinal certo`() {
        val larguras = listOf(238f, 258f, 232f, 392f)
        assertEquals(232f + 12f, GeometriaDeArrasto.deslocamentoDeSoltura(larguras, 12f, 1, 2), 0f)
        assertEquals(232f + 12f + 392f + 12f, GeometriaDeArrasto.deslocamentoDeSoltura(larguras, 12f, 1, 3), 0f)
        assertEquals(-(238f + 12f), GeometriaDeArrasto.deslocamentoDeSoltura(larguras, 12f, 1, 0), 0f)
        assertEquals(-(238f + 12f + 258f + 12f), GeometriaDeArrasto.deslocamentoDeSoltura(larguras, 12f, 2, 0), 0f)
        assertEquals(0f, GeometriaDeArrasto.deslocamentoDeSoltura(larguras, 12f, 2, 2), 0f)
    }

    // --- rolagem automática ---

    @Test fun `rolagem automatica pela borda esquerda`() {
        // container de 100 a 1000
        assertEquals(0f, GeometriaDeArrasto.passoDeRolagem(500f, 100f, 1000f, 1f), 0f)
        assertEquals(0f, GeometriaDeArrasto.passoDeRolagem(190f, 100f, 1000f, 1f), 0f)
        assertEquals(-22f / 90f, GeometriaDeArrasto.passoDeRolagem(189f, 100f, 1000f, 1f), 1e-4f)
        assertEquals(-11f, GeometriaDeArrasto.passoDeRolagem(145f, 100f, 1000f, 1f), 1e-4f)
        assertEquals(-22f, GeometriaDeArrasto.passoDeRolagem(100f, 100f, 1000f, 1f), 1e-4f)
        // além da borda continua em 22
        assertEquals(-22f, GeometriaDeArrasto.passoDeRolagem(30f, 100f, 1000f, 1f), 1e-4f)
    }

    @Test fun `rolagem automatica pela borda direita`() {
        assertEquals(22f / 90f, GeometriaDeArrasto.passoDeRolagem(911f, 100f, 1000f, 1f), 1e-4f)
        assertEquals(11f, GeometriaDeArrasto.passoDeRolagem(955f, 100f, 1000f, 1f), 1e-4f)
        assertEquals(22f, GeometriaDeArrasto.passoDeRolagem(1000f, 100f, 1000f, 1f), 1e-4f)
        assertEquals(22f, GeometriaDeArrasto.passoDeRolagem(1100f, 100f, 1000f, 1f), 1e-4f)
    }

    @Test fun `rolagem automatica escala com a densidade`() {
        assertEquals(-44f, GeometriaDeArrasto.passoDeRolagem(0f, 0f, 2000f, 2f), 1e-4f)
        // 90 dp = 180 px de zona a 2×
        assertEquals(-22f, GeometriaDeArrasto.passoDeRolagem(90f, 0f, 2000f, 2f), 1e-4f)
    }

    // --- foco ---

    @Test fun `foco centraliza o cartao e nunca passa de zero`() {
        assertEquals(1000f + 119f - 500f, GeometriaDeArrasto.alvoDeFoco(1000f, 238f, 1000f), 0f)
        assertEquals(0f, GeometriaDeArrasto.alvoDeFoco(10f, 238f, 1000f), 0f)
    }
}
