package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacotesEEmuladorTest {
    // --- Broadcasts de pacote -----------------------------------------------------------------

    private fun ev(acao: String?, pacote: String?, substituindo: Boolean = false) =
        InterpretadorDePacotes.interpretar(acao, pacote, substituindo)

    @Test
    fun `remocao de verdade`() {
        assertEquals(EventoDePacote.Removido("com.a"), ev("android.intent.action.PACKAGE_REMOVED", "com.a"))
        assertEquals(EventoDePacote.Removido("com.a"), ev("android.intent.action.PACKAGE_FULLY_REMOVED", "com.a"))
    }

    @Test
    fun `remocao que e atualizacao e ignorada`() {
        assertNull(ev("android.intent.action.PACKAGE_REMOVED", "com.a", substituindo = true))
    }

    @Test
    fun `instalacao e substituicao refazem o app`() {
        assertEquals(EventoDePacote.Instalado("com.a"), ev("android.intent.action.PACKAGE_ADDED", "com.a"))
        assertEquals(EventoDePacote.Instalado("com.a"), ev("android.intent.action.PACKAGE_ADDED", "com.a", substituindo = true))
        assertEquals(EventoDePacote.Instalado("com.a"), ev("android.intent.action.PACKAGE_REPLACED", "com.a", substituindo = true))
    }

    @Test
    fun `pacote vazio ou acao alheia e ignorado`() {
        assertNull(ev("android.intent.action.PACKAGE_REMOVED", null))
        assertNull(ev("android.intent.action.PACKAGE_REMOVED", ""))
        assertNull(ev("android.intent.action.BOOT_COMPLETED", "com.a"))
        assertNull(ev(null, "com.a"))
    }

    @Test
    fun `o receptor escuta as quatro acoes`() {
        assertEquals(4, InterpretadorDePacotes.ACOES.size)
    }

    // --- Emulador (F1) ------------------------------------------------------------------------

    @Test
    fun `simulador do projeto e emulador`() {
        assertTrue(detectarEmulador("google/sdk_gcar_arm64/emulator_car64_arm64:14/UP1A/123:userdebug/dev-keys", "sdk_gcar_arm64", "sdk_gcar_arm64", "ranchu"))
    }

    @Test
    fun `cada regra de emulador`() {
        assertTrue(detectarEmulador("generic/x", "", "", ""))
        assertTrue(detectarEmulador("google/x/emulator64:14", "", "", ""))
        assertTrue(detectarEmulador("", "sdk_gphone64_arm64", "", ""))
        assertTrue(detectarEmulador("", "Android SDK built for x86", "", ""))
        assertTrue(detectarEmulador("", "", "sdk_x86", "", ))
        assertTrue(detectarEmulador("", "", "emulator", ""))
        assertTrue(detectarEmulador("", "", "", "goldfish"))
        assertTrue(detectarEmulador("", "", "", "ranchu"))
    }

    @Test
    fun `central do carro nao e emulador`() {
        assertFalse(detectarEmulador("GWM/h6/h6:11/RP1A/123:user/release-keys", "H6", "h6", "mt8666"))
        assertFalse(detectarEmulador(null, null, null, null))
    }
}
