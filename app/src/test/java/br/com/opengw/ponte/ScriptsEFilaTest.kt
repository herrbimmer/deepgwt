package br.com.opengw.ponte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptsEFilaTest {

    private val todosOsScripts = listOf(
        ScriptsDaPagina.onDataChanged("car.basic.vehicle_speed", "87.3"),
        ScriptsDaPagina.onCardChanged(1),
        ScriptsDaPagina.onKeyEvent("UP"),
        ScriptsDaPagina.control("carPlayInDash", true),
        ScriptsDaPagina.lote(listOf("speedLimitActive" to "1", "gearState" to "D")),
        ScriptsDaPagina.variavelCss("--night-tint", "#ff9500"),
        ScriptsDaPagina.escalaDosNumeros("1.15"),
        ScriptsDaPagina.compatibilidade,
        ScriptsDaPagina.batida,
        ScriptsDaPagina.cortina,
        ScriptsDaPagina.encerramento,
        ScriptsDaPagina.depuracaoDaProjecao,
    )

    @Test
    fun `nada de JS mais novo que o Chrome do carro`() {
        for (js in todosOsScripts) {
            assertFalse(js, js.contains("?."))
            assertFalse(js, js.contains("??"))
            assertFalse(js, js.contains("replaceAll"))
            assertFalse(js, js.contains("=>"))
            assertFalse(js, Regex("\\b(let|const)\\s").containsMatchIn(js))
            assertFalse(js, js.contains('`'))
        }
    }

    @Test
    fun `so a reescrita de fonte escreve no console`() {
        for (js in todosOsScripts) {
            if (js == ScriptsDaPagina.escalaDosNumeros("1.15")) continue
            assertFalse(js, js.contains("console."))
        }
    }

    @Test
    fun `onDataChanged so se existir e com chave e valor como string`() {
        val js = ScriptsDaPagina.onDataChanged("app.preferences.a'b\"c", "linha1\nlinha2")
        assertTrue(js.contains("typeof window.onDataChanged==='function'"))
        assertEquals("app.preferences.a'b\"c" to "linha1\nlinha2", LeitorDeScript.onDataChanged(js))
        assertEquals("x" to "12", LeitorDeScript.onDataChanged(ScriptsDaPagina.onDataChanged("x", "12")))
    }

    @Test
    fun `control avulso respeita o tipo do Kotlin e o lote faz numero cru`() {
        // carSpeed: string no avulso, número no lote.
        assertEquals(listOf("carSpeed" to "\"57\""), LeitorDeScript.controls(ScriptsDaPagina.control("carSpeed", "57")))
        assertEquals(listOf("carSpeed" to "57"), LeitorDeScript.controls(ScriptsDaPagina.lote(listOf("carSpeed" to "57"))))
        assertEquals(listOf("appInDash" to "\"left\""), LeitorDeScript.controls(ScriptsDaPagina.control("appInDash", "left")))
        assertEquals(listOf("appInDash" to "false"), LeitorDeScript.controls(ScriptsDaPagina.control("appInDash", false)))
        assertEquals(listOf("outside_temp" to "null"), LeitorDeScript.controls(ScriptsDaPagina.control("outside_temp", null)))
        assertEquals(listOf("projectionWindow" to "\"\""), LeitorDeScript.controls(ScriptsDaPagina.control("projectionWindow", "")))
        val lote = ScriptsDaPagina.lote(
            listOf("speedLimitActive" to "1", "clusterEnabled" to "true", "gearState" to "D", "fan" to 3, "x" to null),
        )
        assertEquals(
            listOf("speedLimitActive" to "1", "clusterEnabled" to "true", "gearState" to "\"D\"", "fan" to "3", "x" to "null"),
            LeitorDeScript.controls(lote),
        )
    }

    @Test
    fun `onCardChanged protegido devolve faltando, erro ou contrato`() {
        val js = ScriptsDaPagina.onCardChanged(3)
        assertEquals(3, LeitorDeScript.onCardChanged(js))
        assertTrue(js.contains("'faltando'"))
        assertTrue(js.contains("'contrato'"))
        assertTrue(js.contains("'erro: '"))
        assertFalse("sem reserva do legado Sport", js.contains("cardId"))
    }

    @Test
    fun `compatibilidade so cria o que falta e sem console`() {
        val js = ScriptsDaPagina.compatibilidade
        assertTrue(js.contains("if(typeof control!=='function'){w.control=function(){};}"))
        assertTrue(js.contains("if(typeof updateWarning!=='function'){w.updateWarning=function(){};}"))
        for (metodo in listOf("setUseDecentralizedScreens", "setNativeMaskState", "setNativeMasksConfig", "getAvailableKeys")) {
            assertTrue(metodo, js.contains("typeof a.$metodo!=='function'"))
        }
        assertTrue(js.contains("car.basic.vehicle_speed"))
        assertTrue(js.contains("car.basic.outside_temp"))
    }

    @Test
    fun `batida de 2 s troca a anterior`() {
        val js = ScriptsDaPagina.batida
        assertTrue(js.contains("clearInterval(window.__havalHeartbeatTimer)"))
        assertTrue(js.contains("window.__havalHeartbeatTimer=setInterval("))
        assertTrue(js.contains("Android.heartbeat()"))
        assertTrue(js.contains(",2000)"))
    }

    @Test
    fun `reescrita de fonte uma vez por documento mantendo a prioridade`() {
        val js = ScriptsDaPagina.escalaDosNumeros("1.15")
        assertTrue(js.contains("'--gauge-num-scale',\"1.15\""))
        assertTrue(js.contains("data-gw-font-scale-patched"))
        assertTrue(js.contains("calc('+v+' * var(--gauge-num-scale, 1))"))
        assertTrue(js.contains("getPropertyPriority('font-size')"))
        assertTrue(js.contains("indexOf('--gauge-num-scale')<0"))
    }

    @Test
    fun `cortina e encerramento`() {
        val cortina = ScriptsDaPagina.cortina
        for (seletor in listOf("'.boot'", "'#boot'", "'.boot-overlay'", "'#bootOverlay'")) assertTrue(cortina.contains(seletor))
        assertTrue(cortina.contains("op>0.01"))
        assertTrue(cortina.contains("presas:presas"))
        assertTrue(cortina.contains("oculto:document.visibilityState"))
        assertTrue(ScriptsDaPagina.encerramento.contains("window.cleanup()"))
        assertTrue(ScriptsDaPagina.encerramento.contains("clearInterval(window.__havalHeartbeatTimer)"))
    }

    @Test
    fun `fila guarda as 250 ultimas na ordem`() {
        val fila = FilaDaPagina()
        for (i in 1..251) assertFalse(fila.enviarOuGuardar(MensagemParaPagina("m$i")))
        assertEquals(250, fila.tamanho)
        val saida = fila.aoTerminarDeCarregar()
        assertEquals((2..251).map { "m$it" }, saida.map { it.js })
        assertTrue(fila.carregada)
        assertEquals(0, fila.tamanho)
        assertTrue("carregada: vai direto", fila.enviarOuGuardar(MensagemParaPagina("depois")))
    }

    @Test
    fun `carga nova descarta o que esperava`() {
        val fila = FilaDaPagina()
        fila.aoTerminarDeCarregar()
        fila.antesDeCarregar()
        assertFalse(fila.carregada)
        fila.enviarOuGuardar(MensagemParaPagina("velha"))
        fila.antesDeCarregar()
        assertEquals(emptyList<String>(), fila.aoTerminarDeCarregar().map { it.js })
    }

    @Test
    fun `fim da carga - compatibilidade, fila, batida`() {
        val pendentes = listOf(MensagemParaPagina("a"), MensagemParaPagina("b"))
        val ordem = SequenciaDeCarga.fimDaCarga("1.0.0", pendentes).map { it.js }
        assertEquals(listOf(ScriptsDaPagina.compatibilidade, "a", "b", ScriptsDaPagina.batida), ordem)
        // Painel original (sem theme.xml) conta como exigindo 0.1.0: também recebe as garantias.
        assertEquals(ScriptsDaPagina.compatibilidade, SequenciaDeCarga.fimDaCarga(null, emptyList()).first().js)
        // Tema que exige ponte mais nova não recebe as garantias.
        assertEquals(listOf("a", "b", ScriptsDaPagina.batida), SequenciaDeCarga.fimDaCarga("2.0.0", pendentes).map { it.js })
    }

    @Test
    fun `ajustes visuais`() {
        assertEquals("#ff9500" to "rgba(255,149,0,0.45)", AjustesVisuais.corNoturna(null))
        assertEquals("#ff9500" to "rgba(255,149,0,0.45)", AjustesVisuais.corNoturna("amber"))
        assertEquals("#ff2200" to "rgba(255,34,0,0.4)", AjustesVisuais.corNoturna("red"))
        assertEquals("#ff5c1a" to "rgba(255,92,26,0.42)", AjustesVisuais.corNoturna("mixed"))
        assertEquals("#ff9500" to "rgba(255,149,0,0.45)", AjustesVisuais.corNoturna("roxo"))
        assertEquals("1.0", AjustesVisuais.escalaComoTexto(1f))
        assertEquals("1.15", AjustesVisuais.escalaComoTexto(1.15f))
        assertEquals("1.0", AjustesVisuais.escalaComoTexto(null))
        assertEquals("1.0", AjustesVisuais.escalaComoTexto(Float.NaN))
    }
}
