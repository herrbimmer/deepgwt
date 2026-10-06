// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.telas.PresetBackgrounds
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetBackgroundsTest {
    @Test
    fun fundoAntigoDoWallhavenViraOEquivalenteNaMesmaPosicao() {
        assertEquals("deepgwt-01.jpg", PresetBackgrounds.resolve("wallhaven-0151j3.jpg"))
        assertEquals("deepgwt-08.jpg", PresetBackgrounds.resolve("wallhaven-4gvpk7.jpg"))
        assertEquals("deepgwt-20.jpg", PresetBackgrounds.resolve("wallhaven-zmg7zw.jpg"))
    }

    @Test
    fun localeComDigitosArabesNaoMudaONomeDoArquivo() {
        val antes = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG-u-nu-arab"))
            assertEquals("deepgwt-01.jpg", PresetBackgrounds.resolve("wallhaven-0151j3.jpg"))
        } finally {
            java.util.Locale.setDefault(antes)
        }
    }

    @Test
    fun nomeNovoEOutroNomeNaoMudam() {
        assertEquals("deepgwt-07.jpg", PresetBackgrounds.resolve("deepgwt-07.jpg"))
        assertEquals("qualquer.png", PresetBackgrounds.resolve("qualquer.png"))
        assertEquals("wallhaven-naoexiste.jpg", PresetBackgrounds.resolve("wallhaven-naoexiste.jpg"))
    }

    @Test
    fun osVinteArquivosNovosExistemNoApk() {
        val pasta = java.io.File("src/main/assets/backgrounds")
        for (i in 1..20) {
            assert(java.io.File(pasta, "deepgwt-%02d.jpg".format(i)).exists()) { "falta deepgwt-%02d.jpg".format(i) }
        }
        assert(pasta.listFiles { f -> f.name.startsWith("wallhaven-") }.isNullOrEmpty()) {
            "ainda há imagens do Wallhaven no APK"
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
