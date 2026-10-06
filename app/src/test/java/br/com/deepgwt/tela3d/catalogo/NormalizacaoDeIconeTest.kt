package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizacaoDeIconeTest {
    private fun escala(fracao: Float) = NormalizacaoDeIcone.escalaDePreenchimento(fracao)

    @Test
    fun `fracao 0,60 da escala 1,3667 e deslocamento -18`() {
        assertEquals(1.3667f, escala(0.60f), 0.0005f)
        assertEquals(-18, NormalizacaoDeIcone.deslocamento(96, escala(0.60f)))
    }

    @Test
    fun `fracao 0,50 estoura e cai em 1,6 com deslocamento -29`() {
        assertEquals(1.6f, escala(0.50f), 0f)
        assertEquals(-29, NormalizacaoDeIcone.deslocamento(96, escala(0.50f)))
    }

    @Test
    fun `fracao 0,82 ou maior nao amplia`() {
        assertEquals(1.0f, escala(0.82f), 0f)
        assertEquals(1.0f, escala(1.0f), 0f)
        assertEquals(0, NormalizacaoDeIcone.deslocamento(96, escala(0.82f)))
        assertEquals(0, NormalizacaoDeIcone.deslocamento(96, escala(1.0f)))
    }

    @Test
    fun `fracao 0,30 usa o piso 0,35 e cai em 1,6`() {
        assertEquals(1.6f, escala(0.30f), 0f)
        // sem o teto, o piso daria 0,82/0,35 = 2,34
        assertEquals(1.6f, escala(0.35f), 0f)
    }

    @Test
    fun `deslocamento arredonda meio para cima`() {
        // T=10, escala 1.5 -> 10*0.5/2 = 2.5 -> 3
        assertEquals(-3, NormalizacaoDeIcone.deslocamento(10, 1.5f))
        assertEquals(0, NormalizacaoDeIcone.deslocamento(96, 1.0f))
    }

    private fun pixels(l: Int, a: Int, vararg visiveis: Triple<Int, Int, Int>): IntArray {
        val p = IntArray(l * a)
        for ((x, y, alfa) in visiveis) p[y * l + x] = alfa shl 24
        return p
    }

    @Test
    fun `caixa de conteudo conta alfa acima de 16 com mais um`() {
        // ocupa x 10..29 (20 px) e y 5..14 (10 px) -> maior dimensão 20 -> 20/96
        val px = pixels(96, 96, Triple(10, 5, 255), Triple(29, 14, 255))
        assertEquals(20f / 96f, NormalizacaoDeIcone.fracaoDeConteudo(px, 96, 96), 1e-6f)
    }

    @Test
    fun `alfa 16 ou menos nao conta`() {
        val px = pixels(96, 96, Triple(0, 0, 16), Triple(95, 95, 16), Triple(40, 40, 17))
        assertEquals(1f / 96f, NormalizacaoDeIcone.fracaoDeConteudo(px, 96, 96), 1e-6f)
    }

    @Test
    fun `nada visivel da fracao 1`() {
        assertEquals(1.0f, NormalizacaoDeIcone.fracaoDeConteudo(IntArray(96 * 96), 96, 96), 0f)
    }

    @Test
    fun `entrada invalida da fracao 1`() {
        assertEquals(1.0f, NormalizacaoDeIcone.fracaoDeConteudo(IntArray(3), 96, 96), 0f)
        assertEquals(1.0f, NormalizacaoDeIcone.fracaoDeConteudo(IntArray(0), 0, 0), 0f)
    }

    @Test
    fun `conteudo cheio da fracao 1`() {
        val cheio = IntArray(96 * 96) { 0xFF000000.toInt() }
        assertEquals(1.0f, NormalizacaoDeIcone.fracaoDeConteudo(cheio, 96, 96), 0f)
    }
}
