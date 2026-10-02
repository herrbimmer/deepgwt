package br.com.opengw.ponte

import br.com.opengw.midia.RadioNowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChavesVirtuaisTest {

    private val radioTocando = RadioNowPlaying.Leitura(canal = "{103900,0,1,1}", rds = "", estado = "1")
    private val radioParado = RadioNowPlaying.Leitura(canal = "{103900,0,1,1}", rds = "", estado = "0")
    private val semRadio = RadioNowPlaying.Leitura(canal = null, rds = null, estado = null)
    private val capa = Any()

    private fun calcular(app: MidiaDoApp, radio: RadioNowPlaying.Leitura) =
        RegraDaMidia.calcular(app, radio) { "data:image/png;base64,CAPA" }

    @Test
    fun `app tocando vale mesmo com o radio tocando`() {
        val v = calcular(MidiaDoApp(" Música ", " Artista ", " Álbum ", true, capa), radioTocando)
        assertEquals(ValoresDaMidia("Música", "Artista", "Álbum", "playing", "data:image/png;base64,CAPA"), v)
    }

    @Test
    fun `radio tocando e app pausado com titulo velho - vale o radio`() {
        val v = calcular(MidiaDoApp("Velha", "Antigo", "Disco", false, capa), radioTocando)
        assertEquals("FM 103,9", v.titulo)
        assertEquals("Rádio", v.artista)
        assertEquals("o álbum é sempre do app", "Disco", v.album)
        assertEquals("playing", v.estado)
        assertEquals("", v.capa)
    }

    @Test
    fun `app pausado sem radio - vale o app pausado`() {
        val v = calcular(MidiaDoApp("Pausada", "Alguém", null, false, capa), radioParado)
        assertEquals("Pausada", v.titulo)
        assertEquals("Alguém", v.artista)
        assertEquals("", v.album)
        assertEquals("paused", v.estado)
        assertEquals("data:image/png;base64,CAPA", v.capa)
    }

    @Test
    fun `nada tocando`() {
        assertEquals(ValoresDaMidia("", "", "", "paused", ""), calcular(MidiaDoApp.NENHUMA, semRadio))
        assertEquals(ValoresDaMidia("", "", "", "paused", ""), calcular(MidiaDoApp("   ", "x", null, false, capa), radioParado))
    }

    @Test
    fun `app tocando sem titulo nao vale mas o estado e tocando`() {
        val v = calcular(MidiaDoApp("", "Artista", null, true, capa), semRadio)
        assertEquals("", v.titulo)
        assertEquals("", v.artista)
        assertEquals("playing", v.estado)
        assertEquals("", v.capa)
    }

    @Test
    fun `sem assinatura da capa nada e codificado`() {
        var chamadas = 0
        RegraDaMidia.calcular(MidiaDoApp("T", "A", null, true, capa), semRadio, incluirCapa = false) { chamadas++; "x" }
        assertEquals(0, chamadas)
    }

    @Test
    fun `duracao e posicao sempre vazias`() {
        val v = calcular(MidiaDoApp("T", "A", null, true, capa), semRadio)
        assertEquals("", v.valor(ChavesVirtuais.MIDIA_DURACAO))
        assertEquals("", v.valor(ChavesVirtuais.MIDIA_POSICAO))
        assertNull(v.valor("app.media.outra"))
    }

    @Test
    fun `cada capa e codificada uma vez`() {
        val codificadas = mutableListOf<Any>()
        val cache = CacheDeCapa { c -> codificadas.add(c); "data:${codificadas.size}" }
        val a = Any()
        val b = Any()
        assertEquals("data:1", cache.dataUrl(a))
        assertEquals("data:1", cache.dataUrl(a))
        assertEquals("data:2", cache.dataUrl(b))
        assertEquals("", cache.dataUrl(null))
        assertEquals(2, codificadas.size)
        assertSame(a, codificadas[0])
    }

    @Test
    fun `chaves de display`() {
        assertEquals(1 to ChavesVirtuais.CampoDoDisplay.PACOTE, ChavesVirtuais.displayDaChave("app.display.1.active_app"))
        assertEquals(3 to ChavesVirtuais.CampoDoDisplay.ICONE, ChavesVirtuais.displayDaChave("app.display.3.active_app_icon"))
        assertEquals(3 to ChavesVirtuais.CampoDoDisplay.ROTULO, ChavesVirtuais.displayDaChave("app.display.3.active_app_label"))
        assertNull(ChavesVirtuais.displayDaChave("app.display.2.active_app"))
        assertNull(ChavesVirtuais.displayDaChave("app.display.1.outra"))
        assertEquals(
            listOf("app.display.1.active_app", "app.display.1.active_app_label", "app.display.1.active_app_icon"),
            ChavesVirtuais.chavesDoDisplay(1),
        )
        // Todas as virtuais estão no contrato.
        for (d in ChavesVirtuais.DISPLAYS) for (k in ChavesVirtuais.chavesDoDisplay(d)) assertTrue(k, k in ContratoV1.CHAVES_LEGIVEIS)
        for (k in ChavesVirtuais.MIDIA_DO_LACO + ChavesVirtuais.SEMPRE_VAZIAS) assertTrue(k, k in ContratoV1.CHAVES_LEGIVEIS)
    }
}
