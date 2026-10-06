package br.com.deepgwt.tela3d.janelas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** C47 (prefs), C54 (abrir como janela) e o JSON do layout da página (§3). */
class PreferenciasELayoutTest {

    // --- C47 ---

    @Test fun padroesQuandoNaoHaNada() {
        val d = PreferenciasDasJanelas(ArmazemEmMemoria()).carregar()
        assertEquals(ModoDeTela.APP_E_CARRO, d.modo)
        assertEquals(ProporcaoDaDivisao.UM_PARA_UM, d.proporcao)
        assertTrue(d.proximoEhEsquerda)
        assertEquals(ModoDaInterface.FLUTUANTE, d.modoDaInterface)
        assertEquals("", d.appEsquerda)
        assertEquals("", d.appDireita)
        assertTrue(d.abrirComoJanela.isEmpty())
    }

    @Test fun valoresInvalidosViramPadrao() {
        val a = ArmazemEmMemoria()
        a.textos["mode"] = "triple"
        a.textos["splitRatio"] = "9:9"
        a.textos["uiMode"] = "x"
        val d = PreferenciasDasJanelas(a).carregar()
        assertEquals(ModoDeTela.APP_E_CARRO, d.modo)
        assertEquals(ProporcaoDaDivisao.UM_PARA_UM, d.proporcao)
        assertEquals(ModoDaInterface.FLUTUANTE, d.modoDaInterface)
    }

    @Test fun leOValorSalvo() {
        val a = ArmazemEmMemoria()
        a.textos["mode"] = "appsOnly"
        a.textos["splitRatio"] = "2:1"
        a.textos["uiMode"] = "inset"
        a.textos["leftApp"] = "com.waze"
        a.textos["rightApp"] = "com.spotify"
        a.booleanos["nextLeft"] = false
        val d = PreferenciasDasJanelas(a).carregar()
        assertEquals(ModoDeTela.DOIS_APPS, d.modo)
        assertEquals(ProporcaoDaDivisao.DOIS_PARA_UM, d.proporcao)
        assertEquals(ModoDaInterface.EMBUTIDA, d.modoDaInterface)
        assertEquals("com.waze", d.appEsquerda)
        assertEquals("com.spotify", d.appDireita)
        assertFalse(d.proximoEhEsquerda)
    }

    @Test fun windowAppsComVirgulasVaziosERepetidos() {
        val a = ArmazemEmMemoria()
        a.textos["window_apps"] = " com.a , ,com.b,com.a,,com.c "
        assertEquals(setOf("com.a", "com.b", "com.c"), PreferenciasDasJanelas(a).carregar().abrirComoJanela)
    }

    @Test fun nuncaLeAsChavesSlot() {
        val a = ArmazemEmMemoria()
        a.textos["slot_appCar_left"] = "app"
        val c = CenarioDasJanelas(prefs = a.textos)
        assertEquals(UsoDoLado.WIDGETS, c.estado.usoEsquerda)
    }

    // --- D8: o padrão do Side by Side sobrevive às gravações comuns ---

    @Test fun gravacaoComumEmDoisAppsNaoMexeNoModo() {
        val a = ArmazemEmMemoria()
        val p = PreferenciasDasJanelas(a)
        p.gravarPadraoDoSideBySide(ProporcaoDaDivisao.UM_PARA_UM, "com.a", "com.b", true, ModoDaInterface.FLUTUANTE)
        assertEquals("appsOnly", a.textos["mode"])
        p.gravarComum(ModoDeTela.DOIS_APPS, ProporcaoDaDivisao.DOIS_PARA_UM, false, ModoDaInterface.FLUTUANTE)
        assertEquals("appsOnly", a.textos["mode"])
        assertEquals("2:1", a.textos["splitRatio"])
        assertEquals(false, a.booleanos["nextLeft"])
        assertEquals("com.a", a.textos["leftApp"])
        assertEquals("com.b", a.textos["rightApp"])
    }

    @Test fun sairDoSideBySideGravaAppCar() {
        val a = ArmazemEmMemoria()
        val p = PreferenciasDasJanelas(a)
        p.gravarPadraoDoSideBySide(ProporcaoDaDivisao.UM_PARA_UM, "", "", true, ModoDaInterface.FLUTUANTE)
        p.gravarComum(ModoDeTela.APP_E_CARRO, ProporcaoDaDivisao.UM_PARA_UM, true, ModoDaInterface.FLUTUANTE)
        assertEquals("appCar", a.textos["mode"])
    }

    @Test fun saveGravaTodasAsChavesDoPadrao() {
        val a = ArmazemEmMemoria()
        PreferenciasDasJanelas(a).gravarPadraoDoSideBySide(ProporcaoDaDivisao.UM_PARA_DOIS, "com.a", "", false, ModoDaInterface.EMBUTIDA)
        assertEquals("appsOnly", a.textos["mode"])
        assertEquals("appCar", a.textos["lastNonAppsMode"])
        assertEquals("1:2", a.textos["splitRatio"])
        assertEquals("com.a", a.textos["leftApp"])
        assertEquals("", a.textos["rightApp"])
        assertEquals("inset", a.textos["uiMode"])
        assertEquals(false, a.booleanos["nextLeft"])
    }

    @Test fun gravaSoAsChavesProprias() {
        val a = ArmazemEmMemoria()
        a.textos["widgets"] = "[]"
        a.textos["hidden_apps"] = "com.x"
        val p = PreferenciasDasJanelas(a)
        p.gravarComum(ModoDeTela.APP_E_CARRO, ProporcaoDaDivisao.UM_PARA_UM, true, ModoDaInterface.FLUTUANTE)
        p.gravarUsoDoLado(ModoDeTela.DOIS_APPS, Lado.DIREITA, UsoDoLado.APP)
        p.gravarAbrirComoJanela(listOf("com.b", "com.a"))
        assertEquals("[]", a.textos["widgets"])
        assertEquals("com.x", a.textos["hidden_apps"])
        assertEquals("app", a.textos["slot_appsOnly_right"])
        assertEquals("com.a,com.b", a.textos["window_apps"])
        assertFalse(a.textos.containsKey("launchSide"))
    }

    // --- C54 ---

    @Test fun alternarAbrirComoJanelaGravaNaHora() {
        val gravados = ArrayList<Set<String>>()
        val c = AbrirComoJanela(emptyList(), "br.com.deepgwt", { it != "com.beantechs.settings" }, { gravados += it })
        c.alternar("com.waze")
        assertTrue(c.contem("com.waze"))
        c.alternar("com.waze")
        assertFalse(c.contem("com.waze"))
        assertEquals(listOf(setOf("com.waze"), emptySet<String>()), gravados)
        c.alternar("com.beantechs.settings")
        c.alternar("br.com.deepgwt")
        assertEquals(2, gravados.size)
    }

    @Test fun esquecerPacoteSoGravaSeEstavaLa() {
        val gravados = ArrayList<Set<String>>()
        val c = AbrirComoJanela(listOf("com.a"), "p", { true }, { gravados += it })
        c.esquecer("com.z")
        assertTrue(gravados.isEmpty())
        c.esquecer("com.a")
        assertEquals(listOf(emptySet<String>()), gravados)
    }

    // --- JSON do layout ---

    private fun estado(
        modo: ModoDeTela = ModoDeTela.APP_E_CARRO,
        esquerda: String = "",
        direita: String = "",
        proximoEsquerda: Boolean = true,
        ultimo: String = "",
    ) = EstadoDasJanelas(
        modo, ProporcaoDaDivisao.UM_PARA_UM, "auto", proximoEsquerda, ultimo, esquerda, direita,
        UsoDoLado.WIDGETS, UsoDoLado.WIDGETS, ModoDaInterface.FLUTUANTE, false,
    )

    private val medidasDoExemplo = LayoutParaAPagina.Medidas(
        esquerda = Retangulo(128, 52, 1037, 496),
        direita = Retangulo(1180, 100, 1792, 496),
        origem = Retangulo(0, 0, 1920, 720),
        margens = MargensDaPagina(128, 128, 24, 224, 224, 80, 24, 500),
        densidade = 1f,
    )

    @Test fun jsonDoExemploDaEspecificacao() {
        val esperado = "{\"left\":false,\"right\":\"idle\",\"mode\":\"appCar\",\"launchSide\":\"auto\",\"nextSide\":\"left\"," +
            "\"lastSide\":\"left\",\"leftUse\":\"widgets\",\"rightUse\":\"widgets\"," +
            "\"leftBounds\":{\"l\":128.0,\"t\":52.0,\"r\":1037.0,\"b\":496.0}," +
            "\"rightBounds\":{\"l\":1180.0,\"t\":100.0,\"r\":1792.0,\"b\":496.0}," +
            "\"safeLeft\":128.0,\"safeRight\":128.0,\"safeTop\":46.0,\"safeBottom\":224.0,\"launcherBottom\":80.0," +
            "\"popupTop\":24.0,\"popupBottom\":500.0,\"uiMode\":\"floating\",\"splitRatio\":\"1:1\"}"
        assertEquals(esperado, LayoutParaAPagina.montar(estado(), medidasDoExemplo))
    }

    @Test fun rightEAppQuandoHaAppNaDireita() {
        val json = LayoutParaAPagina.montar(estado(modo = ModoDeTela.DOIS_APPS, esquerda = "com.a", direita = "com.b"), medidasDoExemplo)
        assertTrue(json.contains("\"left\":true"))
        assertTrue(json.contains("\"right\":\"app\""))
        assertTrue(json.contains("\"mode\":\"appsOnly\""))
    }

    @Test fun lastSideIgualANextSideQuandoVazio() {
        val json = LayoutParaAPagina.montar(estado(proximoEsquerda = false), medidasDoExemplo)
        assertTrue(json.contains("\"nextSide\":\"right\""))
        assertTrue(json.contains("\"lastSide\":\"right\""))
        val comUltimo = LayoutParaAPagina.montar(estado(proximoEsquerda = false, ultimo = "left"), medidasDoExemplo)
        assertTrue(comUltimo.contains("\"lastSide\":\"left\""))
    }

    @Test fun densidadeDivideOsNumeros() {
        val m = LayoutParaAPagina.Medidas(
            esquerda = Retangulo(160, 65, 1037, 440), direita = Retangulo(1180, 100, 1760, 440),
            origem = Retangulo(0, 0, 1920, 720), margens = MargensDaPagina(160, 160, 30, 280, 280, 100, 30, 700),
            densidade = 1.25f,
        )
        val json = LayoutParaAPagina.montar(estado(), m)
        assertTrue(json, json.contains("\"leftBounds\":{\"l\":128.0,\"t\":52.0,"))
        assertTrue(json, json.contains("\"safeTop\":${(30 / 1.25f + 22f)}"))
    }

    @Test fun osBoundsDescontamAOrigemDaPagina() {
        val m = LayoutParaAPagina.Medidas(
            esquerda = Retangulo(128, 52, 1037, 496), direita = Retangulo(1180, 100, 1792, 496),
            origem = Retangulo(100, 10, 1900, 700), margens = MargensDaPagina(0, 0, 0, 0, 0, 0, 0, 0), densidade = 1f,
        )
        val json = LayoutParaAPagina.montar(estado(), m)
        assertTrue(json, json.contains("\"leftBounds\":{\"l\":28.0,\"t\":42.0,\"r\":937.0,\"b\":486.0}"))
    }

    @Test fun nuncaEnviaNaNNemInfinito() {
        assertEquals("0.0", LayoutParaAPagina.numero(Float.NaN))
        assertEquals("0.0", LayoutParaAPagina.numero(Float.POSITIVE_INFINITY))
        val m = LayoutParaAPagina.Medidas(
            esquerda = Retangulo(0, 0, 0, 0), direita = Retangulo(0, 0, 0, 0),
            origem = Retangulo(0, 0, 0, 0), margens = MargensDaPagina(0, 0, 0, 0, 0, 0, 0, 0), densidade = 0f,
        )
        val json = LayoutParaAPagina.montar(estado(), m)
        assertFalse(json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
    }

    @Test fun getShellLayoutTemOsTresCampos() {
        assertEquals(
            "{\"mode\":\"appsOnly\",\"splitRatio\":\"1:1\",\"launchSide\":\"auto\"}",
            LayoutParaAPagina.textoDeGetShellLayout(estado(modo = ModoDeTela.DOIS_APPS)),
        )
    }
}
