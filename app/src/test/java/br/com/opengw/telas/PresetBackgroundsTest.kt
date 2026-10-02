package br.com.opengw.telas

import br.com.opengw.telas.PresetBackgrounds
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetBackgroundsTest {
    @Test
    fun fundoAntigoDoWallhavenViraOEquivalenteNaMesmaPosicao() {
        assertEquals("opengw-01.jpg", PresetBackgrounds.resolve("wallhaven-0151j3.jpg"))
        assertEquals("opengw-08.jpg", PresetBackgrounds.resolve("wallhaven-4gvpk7.jpg"))
        assertEquals("opengw-20.jpg", PresetBackgrounds.resolve("wallhaven-zmg7zw.jpg"))
    }

    @Test
    fun nomeNovoEOutroNomeNaoMudam() {
        assertEquals("opengw-07.jpg", PresetBackgrounds.resolve("opengw-07.jpg"))
        assertEquals("qualquer.png", PresetBackgrounds.resolve("qualquer.png"))
        assertEquals("wallhaven-naoexiste.jpg", PresetBackgrounds.resolve("wallhaven-naoexiste.jpg"))
    }

    @Test
    fun osVinteArquivosNovosExistemNoApk() {
        val pasta = java.io.File("src/main/assets/backgrounds")
        for (i in 1..20) {
            assert(java.io.File(pasta, "opengw-%02d.jpg".format(i)).exists()) { "falta opengw-%02d.jpg".format(i) }
        }
        assert(pasta.listFiles { f -> f.name.startsWith("wallhaven-") }.isNullOrEmpty()) {
            "ainda há imagens do Wallhaven no APK"
        }
    }
}
