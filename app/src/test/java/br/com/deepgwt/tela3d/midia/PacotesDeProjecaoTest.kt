package br.com.deepgwt.tela3d.midia

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V6 (presença) e V7 (pacotes). */
class PacotesDeProjecaoTest {

    // --- V6 ------------------------------------------------------------------------------------

    private fun presenca(cpEnlace: Boolean, aaEnlace: Boolean, cpFaixa: Boolean, aaFaixa: Boolean) =
        ResolucaoDePresenca.resolver(cpEnlace, aaEnlace, cpFaixa, aaFaixa)

    @Test
    fun `v6 presenca`() {
        val n = TipoDeProjecao.NENHUMA
        val aa = TipoDeProjecao.ANDROID_AUTO
        val cp = TipoDeProjecao.CARPLAY
        // (CarPlay por enlace, AA por enlace, faixa de CarPlay, faixa de AA) -> resultado
        assertEquals(n, presenca(false, false, false, false))
        assertEquals(cp, presenca(true, false, false, false))
        assertEquals(aa, presenca(false, true, false, false))
        assertEquals(cp, presenca(false, false, true, false))
        assertEquals(aa, presenca(false, false, false, true))
        assertEquals(cp, presenca(true, true, true, false))
        assertEquals(aa, presenca(true, true, false, true))
        assertEquals(cp, presenca(true, true, true, true))
        assertEquals(cp, presenca(true, true, false, false))
        assertEquals(cp, presenca(false, false, true, true))
        assertEquals(aa, presenca(true, false, false, true))
        assertEquals(cp, presenca(false, true, true, false))
    }

    @Test
    fun `estados dos enlaces`() {
        assertTrue(ResolucaoDePresenca.enlaceDoCarPlayAtivo(2))
        assertFalse(ResolucaoDePresenca.enlaceDoCarPlayAtivo(1))
        assertFalse(ResolucaoDePresenca.enlaceDoCarPlayAtivo(null))
        for (ativo in listOf(3, 7, 8)) assertTrue(ResolucaoDePresenca.enlaceDoAndroidAutoAtivo(ativo))
        for (inativo in listOf(0, 1, 2, 4, 9)) assertFalse(ResolucaoDePresenca.enlaceDoAndroidAutoAtivo(inativo))
        assertFalse(ResolucaoDePresenca.enlaceDoAndroidAutoAtivo(null))
    }

    // --- V7 ------------------------------------------------------------------------------------

    @Test
    fun `v7 pacotes de projecao`() {
        val casos = listOf(
            "com.ts.androidauto.app" to TipoDeProjecao.ANDROID_AUTO,
            "com.ts.carplay.app" to TipoDeProjecao.CARPLAY,
            "com.google.android.projection.gearhead" to TipoDeProjecao.ANDROID_AUTO,
            "com.autolink.carplay.app" to TipoDeProjecao.CARPLAY,
            "com.spotify.music" to TipoDeProjecao.NENHUMA,
            "COM.TS.CARPLAY" to TipoDeProjecao.CARPLAY,
            "com.ts.androidauto.projectionservice" to TipoDeProjecao.ANDROID_AUTO,
            "com.autolink.androidauto.app" to TipoDeProjecao.ANDROID_AUTO,
        )
        for ((pacote, tipo) in casos) {
            assertEquals(pacote, tipo, PacotesDeProjecao.tipoDoPacote(pacote))
            assertEquals(pacote, tipo != TipoDeProjecao.NENHUMA, PacotesDeProjecao.ehDeProjecao(pacote))
        }
        assertFalse(PacotesDeProjecao.ehDeProjecao(null))
        assertEquals(TipoDeProjecao.NENHUMA, PacotesDeProjecao.tipoDoPacote(null))
    }

    @Test
    fun `v7 fonte por rotulo`() {
        assertTrue(PacotesDeProjecao.ehFonteAndroidAuto("com.x", "android auto"))
        assertTrue(PacotesDeProjecao.ehFonteAndroidAuto("com.x", "ANDROID AUTO"))
        assertTrue(PacotesDeProjecao.ehFonteCarPlay("com.x", "  CarPlay "))
        assertFalse(PacotesDeProjecao.ehFonteAndroidAuto("com.x", "USB"))
        assertFalse(PacotesDeProjecao.ehFonteCarPlay("com.x", "USB"))
        assertTrue(PacotesDeProjecao.ehFonteAndroidAuto("com.ts.androidauto.app", ""))
        assertTrue(PacotesDeProjecao.ehFonteCarPlay("com.ts.carplay", null))
        assertFalse(PacotesDeProjecao.ehFonteAndroidAuto(null, null))
    }

    @Test
    fun `classificacao nao depende da localidade do aparelho`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("tr", "TR")) // em turco "I".lowercase() viraria "ı"
            assertTrue(PacotesDeProjecao.ehDeProjecao("COM.TS.CARPLAY"))
            assertTrue(PacotesDeProjecao.ehDeProjecao("COM.GOOGLE.PROJECTION.GEARHEAD"))
            assertTrue(PacotesDeProjecao.ehFonteAndroidAuto("X", "ANDROID AUTO"))
            assertEquals(TipoDeProjecao.CARPLAY, PacotesDeProjecao.tipoDoPacote("COM.AUTOLINK.CARPLAY.APP"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
