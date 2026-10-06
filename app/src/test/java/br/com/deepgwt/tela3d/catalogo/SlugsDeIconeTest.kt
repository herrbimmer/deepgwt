package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlugsDeIconeTest {
    @Test
    fun `sao 28 slugs e todos tem recurso`() {
        assertEquals(28, SLUGS_DE_ICONE.size)
        assertEquals(28, SLUGS_DE_ICONE.map { it.first }.toSet().size)
        for ((slug, nome) in SLUGS_DE_ICONE) {
            assertNotNull("recurso de $slug", SlugsDeIcone.recurso(slug))
            assertTrue("nome de $slug", nome.isNotBlank())
        }
    }

    @Test
    fun `ordem da grade`() {
        assertEquals("youtube", SLUGS_DE_ICONE.first().first)
        assertEquals("YT Music", SLUGS_DE_ICONE[1].second)
        assertEquals("gwm", SLUGS_DE_ICONE[2].first)
        assertEquals("speaker", SLUGS_DE_ICONE.last().first)
        assertEquals("Caixa", SLUGS_DE_ICONE.last().second)
    }

    @Test
    fun `slug desconhecido nao tem recurso`() {
        assertNull(SlugsDeIcone.recurso("nao_existe"))
        assertNull(SlugsDeIcone.recurso(null))
        assertNull(SlugsDeIcone.recurso(""))
    }

    @Test
    fun `so youtube youtube_music e gwm mantem as cores`() {
        val marca = SLUGS_DE_ICONE.map { it.first }.filter { SlugsDeIcone.ehDeMarca(it) }
        assertEquals(listOf("youtube", "youtube_music", "gwm"), marca)
        assertFalse(SlugsDeIcone.ehDeMarca(null))
    }
}
