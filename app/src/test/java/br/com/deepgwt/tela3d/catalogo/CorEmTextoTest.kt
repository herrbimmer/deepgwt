package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorEmTextoTest {
    @Test
    fun `hexadecimal de 6 e 8 digitos`() {
        assertEquals(0xFF3D4650.toInt(), CorEmTexto.analisar("#3D4650"))
        assertEquals(0xFF3D4650.toInt(), CorEmTexto.analisar(" #3d4650 "))
        assertEquals(0x38FFFFFF, CorEmTexto.analisar("#38FFFFFF"))
    }

    @Test
    fun `nomes de cor`() {
        assertEquals(0xFFFF0000.toInt(), CorEmTexto.analisar("red"))
        assertEquals(0xFFFFFFFF.toInt(), CorEmTexto.analisar("White"))
    }

    @Test
    fun `invalidos`() {
        assertNull(CorEmTexto.analisar(null))
        assertNull(CorEmTexto.analisar(""))
        assertNull(CorEmTexto.analisar("#12345"))
        assertNull(CorEmTexto.analisar("#GGGGGG"))
        assertNull(CorEmTexto.analisar("#-12345"))
        assertNull(CorEmTexto.analisar("3D4650"))
        assertNull(CorEmTexto.analisar("naoexiste"))
    }

    @Test
    fun `padroes`() {
        assertEquals(0xFFFFFFFF.toInt(), CoresDaPersonalizacao.corDoGlifo(null))
        assertEquals(0xFFFFFFFF.toInt(), CoresDaPersonalizacao.corDoGlifo("xx"))
        assertEquals(0xFF3D4650.toInt(), CoresDaPersonalizacao.corDoFundo("xx"))
        assertEquals(0xFF111111.toInt(), CoresDaPersonalizacao.corDoFundo("#111111"))
    }

    @Test
    fun `paleta tem 10 cores e as duas primeiras fixas`() {
        assertEquals(10, CoresDaPersonalizacao.PALETA.size)
        assertEquals("#FFFFFF", CoresDaPersonalizacao.PALETA[0])
        assertEquals("#3D4650", CoresDaPersonalizacao.PALETA[2])
        assertEquals("#26C6DA", CoresDaPersonalizacao.PALETA[9])
        assertEquals(CoresDaPersonalizacao.PALETA, PALETA)
    }
}
