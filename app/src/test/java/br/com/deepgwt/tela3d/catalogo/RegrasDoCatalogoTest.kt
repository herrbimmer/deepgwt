package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasDoCatalogoTest {
    private val proprio = "br.com.redesurftank.havalshisuku"

    private fun projecao(p: String?): Boolean {
        val m = p?.lowercase() ?: return false
        return m == "com.ts.androidauto.app" || m == "com.ts.androidauto" ||
            m == "com.ts.androidauto.projectionservice" || m == "com.ts.carplay.app" ||
            m == "com.ts.carplay" || m.contains("androidauto") || m.contains("carplay") ||
            m.contains("projection.gearhead")
    }

    private val regras = RegrasDoCatalogo(proprio, ::projecao)

    @Test
    fun `a lista de ignorados tem 41 pacotes`() {
        assertEquals(41, FatosDoCarro.IGNORADOS.size)
    }

    @Test
    fun `os 4 fixados estao na ordem certa`() {
        assertEquals(
            listOf(
                "com.beantechs.vehiclecenter", "com.beantechs.settings",
                "com.beantechs.energyassistant", "com.beantechs.launcher",
            ),
            FatosDoCarro.FIXADOS,
        )
    }

    @Test
    fun `catalogo exclui cada um dos 41 ignorados`() {
        for (p in FatosDoCarro.IGNORADOS) {
            assertFalse("catálogo: $p", regras.entraNoCatalogo(p, emptySet()))
            assertFalse("linha: $p", regras.entraNaLinha(p))
            assertFalse("recente: $p", regras.podeSerRecente(p, emptySet()))
        }
    }

    @Test
    fun `ignorados sao comparados sem tolerar maiusculas`() {
        assertTrue(regras.entraNoCatalogo("com.beantechs.pkimaintain", emptySet()))
        assertFalse(regras.entraNoCatalogo("com.beantechs.PKIMaintain", emptySet()))
    }

    @Test
    fun `catalogo exclui vazio o proprio app projecao e ocultos`() {
        assertFalse(regras.entraNoCatalogo(null, emptySet()))
        assertFalse(regras.entraNoCatalogo("", emptySet()))
        assertFalse(regras.entraNoCatalogo(proprio, emptySet()))
        assertFalse(regras.entraNoCatalogo("com.exemplo.carplay.x", emptySet()))
        assertFalse(regras.entraNoCatalogo("com.exemplo.app", setOf("com.exemplo.app")))
        assertTrue(regras.entraNoCatalogo("com.exemplo.app", setOf("outro")))
    }

    @Test
    fun `fixados entram no catalogo mas nao na linha nem nos recentes`() {
        for (p in FatosDoCarro.FIXADOS) {
            assertTrue("catálogo: $p", regras.entraNoCatalogo(p, emptySet()))
            assertFalse("linha: $p", regras.entraNaLinha(p))
            assertFalse("recente: $p", regras.podeSerRecente(p, emptySet()))
        }
        // ocultos somem do catálogo
        assertFalse(regras.entraNoCatalogo("com.beantechs.launcher", setOf("com.beantechs.launcher")))
    }

    @Test
    fun `linha inclui ocultos mas recentes nao`() {
        assertTrue(regras.entraNaLinha("com.exemplo.app"))
        assertTrue(regras.podeSerRecente("com.exemplo.app", emptySet()))
        assertFalse(regras.podeSerRecente("com.exemplo.app", setOf("com.exemplo.app")))
        assertFalse(regras.entraNaLinha(proprio))
        assertFalse(regras.entraNaLinha("com.ts.carplay.app"))
    }

    @Test
    fun `classificacao de app GWM`() {
        assertTrue(regras.ehAppGwm("com.beantechs.x"))
        assertTrue(regras.ehAppGwm("com.autolink.y"))
        assertTrue(regras.ehAppGwm("com.gwm.z"))
        assertTrue(regras.ehAppGwm("br.com.foo.gwm.bar"))
        assertTrue(regras.ehAppGwm("COM.BEANTECHS.X"))
        assertFalse(regras.ehAppGwm("com.exemplo"))
        assertFalse(regras.ehAppGwm(proprio))
        assertFalse(regras.ehAppGwm("com.ts.androidauto.app"))
        assertFalse(regras.ehAppGwm(""))
        assertFalse(regras.ehAppGwm(null))
    }

    @Test
    fun `emblema GWM exclui o energy assistant em qualquer caixa`() {
        assertTrue(regras.usaEmblemaGwm("com.beantechs.settings"))
        assertFalse(regras.usaEmblemaGwm("com.beantechs.energyassistant"))
        assertFalse(regras.usaEmblemaGwm("COM.BEANTECHS.ENERGYASSISTANT"))
        assertFalse(regras.usaEmblemaGwm("com.exemplo"))
    }

    @Test
    fun `abrir como janela`() {
        assertTrue(regras.podeAlternarJanela("com.waze"))
        assertFalse(regras.podeAlternarJanela(null))
        assertFalse(regras.podeAlternarJanela(""))
        assertFalse(regras.podeAlternarJanela(proprio))
        assertFalse(regras.podeAlternarJanela("com.beantechs.launcher"))
        assertFalse(regras.podeAlternarJanela("com.beantechs.qualquer"))
        assertFalse(regras.podeAlternarJanela("com.ts.carplay.app"))
    }

    @Test
    fun `desinstalar exclui proprio fixado e GWM`() {
        assertTrue(regras.podeDesinstalarSemSistema("com.waze"))
        assertFalse(regras.podeDesinstalarSemSistema(proprio))
        assertFalse(regras.podeDesinstalarSemSistema("com.beantechs.settings"))
        assertFalse(regras.podeDesinstalarSemSistema("com.beantechs.outro"))
        assertFalse(regras.podeDesinstalarSemSistema(""))
    }

    @Test
    fun `rotulos`() {
        assertEquals("Energy Assistant", regras.rotuloBase("COM.BEANTECHS.ENERGYASSISTANT", "Outro"))
        assertEquals("Waze", regras.rotuloBase("com.waze", "Waze"))
        assertEquals("", regras.rotuloBase("com.waze", null))
        assertEquals("Configurações do Veículo", regras.rotuloDeFixado("com.beantechs.vehiclecenter", "x"))
        assertEquals("Configurações do Sistema", regras.rotuloDeFixado("com.beantechs.settings", "x"))
        assertEquals("Energy Assistant", regras.rotuloDeFixado("com.beantechs.energyassistant", "x"))
        assertEquals("GWM Home", regras.rotuloDeFixado("com.beantechs.launcher", "x"))
        assertEquals("Waze", regras.rotuloDeFixado("com.waze", "Waze"))
    }
}
