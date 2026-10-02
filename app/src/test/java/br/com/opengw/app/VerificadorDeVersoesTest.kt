package br.com.opengw.app

import br.com.opengw.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** `telas-e-servico-diversos.md` X-V1, X-V3 e U3. */
class VerificadorDeVersoesTest {

    @Test
    fun `comparacao de versoes (X-V1)`() {
        val casos = listOf(
            Triple("v1.0.0.71-preview", "1.0.0.70-preview", 1),
            Triple("v1.0.0.71-preview", "1.0.0.71-preview", 0),
            Triple("1.0.0.263-full-preview-buttons", "v1.0.0.71-preview", 1),
            Triple("9.8", "9.7", 1),
            Triple("9.7", "9.7.0", 0),
            Triple("v9.7.1", "9.7", 1),
            Triple("Alpha 9.8 021020260900", "Alpha 9.7 011020260825", 1),
            Triple("v9.8", "Alpha 9.7 011020260825", 1),
            Triple("10.0", "9.99", 1),
            Triple("1..2", "1.2", 0),
            Triple("9.7-beta", "9.7", 0),
            Triple("9.7-preview", "9.7", 0),
            Triple("", "0", 0),
        )
        for ((a, b, esperado) in casos) {
            assertEquals("comparar($a, $b)", esperado, VerificadorDeVersoes.comparar(a, b))
            assertEquals("comparar($b, $a)", -esperado, VerificadorDeVersoes.comparar(b, a))
        }
        assertTrue(VerificadorDeVersoes.maisNova("9.8", "9.7"))
        assertFalse(VerificadorDeVersoes.maisNova("9.7", "9.7.0"))
        // X-D9: "V" maiúsculo não é tirado.
        assertEquals(-1, VerificadorDeVersoes.comparar("V9.8", "9.7"))
    }

    @Test
    fun `consulta de versoes (X-V3)`() {
        val corpo = """
            [
             {"tag_name":"v9.9-preview","prerelease":true,"assets":[{"name":"notas.txt","browser_download_url":"u0"},{"name":"app.apk","browser_download_url":"u1"}]},
             {"tag_name":"v9.8-preview","prerelease":true,"assets":[{"name":"b.apk","browser_download_url":"u2"}]},
             {"tag_name":"v9.8","prerelease":false,"assets":[]},
             {"tag_name":"v9.7","prerelease":false,"assets":[{"name":"x.APK","browser_download_url":"u3"},{"name":"y.apk","browser_download_url":"u4"}]}
            ]
        """.trimIndent()
        assertEquals(
            ConsultaDeVersoes(Versao("v9.7", "u4", false), Versao("v9.9-preview", "u1", true)),
            VerificadorDeVersoes.interpretar(corpo),
        )
        assertEquals(ConsultaDeVersoes(null, null), VerificadorDeVersoes.interpretar("{}"))
        assertEquals(ConsultaDeVersoes(null, null), VerificadorDeVersoes.interpretar("não é json"))
    }

    @Test
    fun `o feed e o verificador nao apontam para repositorios de terceiros (U3)`() {
        val url = BuildConfig.UPDATE_FEED_URL
        assertTrue("BuildConfig.UPDATE_FEED_URL", url.isEmpty() || url.startsWith("https://"))
        val fonte = listOf(
            File("src/main/java/br/com/opengw/app/VerificadorDeVersoes.kt"),
            File("app/src/main/java/br/com/opengw/app/VerificadorDeVersoes.kt"),
        ).first { it.isFile }
        val texto = fonte.readText()
        assertFalse("código do verificador traz URL do GitHub", texto.contains("github.com", ignoreCase = true))
    }
}
