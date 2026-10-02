package br.com.opengw.midia

import br.com.opengw.midia.CapaParaTema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapaParaTemaTest {

    @Test
    fun `capa do tamanho que a barra inferior guarda e reduzida`() {
        // 720 é o teto de normalizeMediaArtwork(): o caso comum de toda capa de streaming.
        assertEquals(CapaParaTema.LADO_MAX to CapaParaTema.LADO_MAX, CapaParaTema.dimensoes(720, 720))
    }

    @Test
    fun `reducao mantem a proporcao`() {
        val (l, a) = CapaParaTema.dimensoes(720, 405)
        assertEquals(320, l)
        assertEquals(180, a)
        val (l2, a2) = CapaParaTema.dimensoes(405, 720)
        assertEquals(180, l2)
        assertEquals(320, a2)
    }

    @Test
    fun `capa que ja cabe nao e ampliada`() {
        assertEquals(96 to 96, CapaParaTema.dimensoes(96, 96))
        assertEquals(320 to 200, CapaParaTema.dimensoes(320, 200))
    }

    @Test
    fun `cabe no maior lugar que um tema reserva para a capa`() {
        // Card do Cupertino Ultra: 34% da caixa xl de 760 px.
        assertTrue(CapaParaTema.LADO_MAX >= Math.round(760 * 0.34).toInt())
    }

    @Test
    fun `faixa extrema nunca vira zero`() {
        assertEquals(320 to 1, CapaParaTema.dimensoes(4000, 2))
    }

    @Test
    fun `dimensao invalida volta intacta`() {
        assertEquals(0 to 0, CapaParaTema.dimensoes(0, 0))
        assertEquals(-1 to 10, CapaParaTema.dimensoes(-1, 10))
    }
}
