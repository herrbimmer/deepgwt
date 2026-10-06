package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Test

class AfinamentoTest {
    private fun lista(n: Int) = (0 until n).toList()

    @Test
    fun `detalhe de mil pontos com limite 300`() {
        val r = Afinamento.afinar(lista(1000), 300)
        assertEquals(251, r.size)
        assertEquals(0, r.first())
        assertEquals(4, r[1])
        assertEquals(996, r[249])
        assertEquals(999, r.last())
    }

    @Test
    fun `detalhe de 7200 pontos com limite 600`() {
        assertEquals(601, Afinamento.afinar(lista(7200), 600).size)
    }

    @Test
    fun `lista curta passa inteira`() {
        assertEquals(lista(10), Afinamento.afinar(lista(10), 300))
        assertEquals(lista(10), Afinamento.afinarAoVivo(lista(10), 300))
    }

    @Test
    fun `600 itens com limite 300 passam de um item`() {
        assertEquals(301, Afinamento.afinar(lista(600), 300).size)
    }

    @Test
    fun `ao vivo conta a partir de um`() {
        val a = Afinamento.afinarAoVivo(lista(3000), 1500)
        assertEquals(1500, a.size)
        assertEquals(1, a.first())
        assertEquals(3, a[1])
        assertEquals(2999, a.last())
        val b = Afinamento.afinarAoVivo(lista(1000), 300)
        assertEquals(250, b.size)
        assertEquals(3, b.first())
        assertEquals(7, b[1])
        assertEquals(999, b.last())
    }

    @Test
    fun `o ultimo item sempre fica`() {
        assertEquals(1000, Afinamento.afinarAoVivo(lista(1001), 300).last())
        assertEquals(1000, Afinamento.afinar(lista(1001), 300).last())
    }

    @Test
    fun `lista vazia`() {
        assertEquals(emptyList<Int>(), Afinamento.afinar(emptyList<Int>(), 300))
    }
}
