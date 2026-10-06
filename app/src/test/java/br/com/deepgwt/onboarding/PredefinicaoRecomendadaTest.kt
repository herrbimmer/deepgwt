package br.com.deepgwt.onboarding

import br.com.deepgwt.ajustes.CatalogoDeAjustes
import br.com.deepgwt.ajustes.TipoDeAjuste
import br.com.deepgwt.nucleo.Preferencias as K
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PredefinicaoRecomendadaTest {

    @Test
    fun `todo id da predefinicao existe no catalogo e e interruptor`() {
        PredefinicaoRecomendada.INTERRUPTORES.keys.forEach { id ->
            val a = CatalogoDeAjustes.ajuste(id)
            assertNotNull("ajuste $id", a)
            assertEquals("$id é interruptor", TipoDeAjuste.INTERRUPTOR, a!!.tipo)
        }
        assertNotNull(CatalogoDeAjustes.ajuste(CatalogoDeAjustes.ID_DO_TEMA_DO_PAINEL))
    }

    @Test
    fun `a predefinicao faz o que o dono pediu`() {
        val j = PredefinicaoRecomendada.ajustes
        assertEquals(true, j[K.DISABLE_AVAS.chave])
        assertEquals(true, j[K.QUICK_GESTURES_ENABLED.chave])
        assertEquals(true, j[K.HUD_OVERLAY_ENABLED.chave])
        assertEquals(true, j["painel.tema_customizado"])
        assertEquals("stuttgart-mbux", j[CatalogoDeAjustes.ID_DO_TEMA_DO_PAINEL])
        assertEquals(true, j[K.DISABLE_AVM_CAR_STOPPED.chave])
        assertEquals(true, j[K.AVM_MANUAL_WHEN_STOPPED.chave])
        assertEquals(true, j[K.DISABLE_HOTSPOT_ON_POWER_OFF.chave])
    }

    @Test
    fun `o tema da predefinicao vem no APK`() {
        val pasta = File("src/main/assets/PreinstalledThemes/${PredefinicaoRecomendada.TEMA}")
        assertTrue("falta ${pasta.absolutePath}", File(pasta, "app.html").isFile && File(pasta, "theme.xml").isFile)
    }

    @Test
    fun `do zero devolve os mesmos interruptores ao padrao do catalogo`() {
        val p = PredefinicaoRecomendada.padroes()
        assertEquals(PredefinicaoRecomendada.INTERRUPTORES.keys, p.keys)
        p.forEach { (id, v) -> assertEquals(CatalogoDeAjustes.ajuste(id)!!.padrao, v) }
    }
}
