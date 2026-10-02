package br.com.opengw.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class CastaDoTemaTest {

    private val doApk = setOf("bavarianid7", "bavaroid8", "coffe3", "cupertinoextended", "cupertinoultra", "minimalista", "stuttgart-mbux", "sweedesensus", "tea30")

    @Test
    fun `pasta que vem no APK e Legacy`() {
        doApk.forEach { assertEquals(it, CastaDoTema.LEGACY, CastaDoTema.de(it, doApk)) }
    }

    @Test
    fun `comparacao ignora maiusculas`() {
        assertEquals(CastaDoTema.LEGACY, CastaDoTema.de("BavarianID7", doApk))
    }

    @Test
    fun `embutido e Legacy`() {
        assertEquals(CastaDoTema.LEGACY, CastaDoTema.de("Default", doApk))
        assertEquals(CastaDoTema.LEGACY, CastaDoTema.de("", doApk))
    }

    @Test
    fun `tema do catalogo, importado ou do criador e XTRA`() {
        listOf("agulha", "tokyo-neon", "meu-tema-html", "painel-do-miguel").forEach {
            assertEquals(it, CastaDoTema.XTRA, CastaDoTema.de(it, doApk))
        }
    }

    @Test
    fun `sem lista do APK tudo que tem pasta e XTRA`() {
        assertEquals(CastaDoTema.XTRA, CastaDoTema.de("coffe3", emptySet()))
    }

    @Test
    fun `ids que o instalador le`() {
        assertEquals("legacy", CastaDoTema.LEGACY.id)
        assertEquals("xtra", CastaDoTema.XTRA.id)
        assertEquals("Temas Legacy", CastaDoTema.LEGACY.titulo)
        assertEquals("Temas XTRA", CastaDoTema.XTRA.titulo)
    }
}
