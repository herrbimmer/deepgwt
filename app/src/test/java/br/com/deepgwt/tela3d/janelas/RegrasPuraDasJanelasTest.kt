package br.com.deepgwt.tela3d.janelas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Escolha do lado (V2), pacotes (V3) e normalização dos valores da ponte. */
class RegrasPuraDasJanelasTest {

    // --- V2 ---

    private fun sbs(proximoEsq: Boolean, esqLivre: Boolean, dirLivre: Boolean) =
        EscolhaDoLado.escolher(ModoDeTela.DOIS_APPS, esqLivre, dirLivre, proximoEsq)

    @Test fun autoComProximoEsquerdoEAmbosLivres() {
        assertEquals(ResultadoDaEscolha(Lado.ESQUERDA, false), sbs(true, true, true))
    }

    @Test fun autoComProximoEsquerdoEsquerdaLivreDireitaOcupada() {
        assertEquals(ResultadoDaEscolha(Lado.ESQUERDA, false), sbs(true, true, false))
    }

    @Test fun autoComProximoEsquerdoSoDireitaLivre() {
        assertEquals(ResultadoDaEscolha(Lado.DIREITA, true), sbs(true, false, true))
    }

    @Test fun autoComProximoDireitoAmbosLivres() {
        assertEquals(ResultadoDaEscolha(Lado.DIREITA, true), sbs(false, true, true))
    }

    @Test fun autoComProximoDireitoSoEsquerdaLivre() {
        assertEquals(ResultadoDaEscolha(Lado.ESQUERDA, false), sbs(false, true, false))
    }

    @Test fun autoComProximoDireitoSoDireitaLivre() {
        assertEquals(ResultadoDaEscolha(Lado.DIREITA, true), sbs(false, false, true))
    }

    @Test fun ambosOcupadosNaoEscolheENaoMudaOProximo() {
        assertEquals(ResultadoDaEscolha(null, true), sbs(true, false, false))
        assertEquals(ResultadoDaEscolha(null, false), sbs(false, false, false))
    }

    @Test fun appCarSoUsaOLadoEsquerdoESemMudarNada() {
        assertEquals(ResultadoDaEscolha(Lado.ESQUERDA, false), EscolhaDoLado.escolher(ModoDeTela.APP_E_CARRO, true, true, false))
        assertEquals(ResultadoDaEscolha(null, true), EscolhaDoLado.escolher(ModoDeTela.APP_E_CARRO, false, true, true))
    }

    @Test fun osLadosAlternamACadaAbertura() {
        var proximo = true
        val lados = ArrayList<Lado?>()
        var esq = true
        var dir = true
        repeat(3) {
            val r = EscolhaDoLado.escolher(ModoDeTela.DOIS_APPS, esq, dir, proximo)
            lados += r.lado
            proximo = r.proximoEhEsquerda
            if (r.lado == Lado.ESQUERDA) esq = false
            if (r.lado == Lado.DIREITA) dir = false
        }
        assertEquals(listOf(Lado.ESQUERDA, Lado.DIREITA, null), lados)
    }

    // --- V3 ---

    @Test fun projecaoDosPacotes() {
        assertTrue(PacotesDeProjecao.ehProjecao("com.ts.androidauto.app"))
        assertTrue(PacotesDeProjecao.ehProjecao("COM.TS.CARPLAY"))
        assertTrue(PacotesDeProjecao.ehProjecao("com.google.android.projection.gearhead"))
        assertTrue(PacotesDeProjecao.ehProjecao("com.ts.carplay.app.foo"))
        assertFalse(PacotesDeProjecao.ehProjecao("com.spotify.music"))
        assertFalse(PacotesDeProjecao.ehProjecao(null))
    }

    @Test fun tipoDeProjecaoDoPacote() {
        assertEquals(TipoDeProjecao.CARPLAY, PacotesDeProjecao.tipoDoPacote("com.ts.carplay"))
        assertEquals(TipoDeProjecao.ANDROID_AUTO, PacotesDeProjecao.tipoDoPacote("com.ts.androidauto.projectionservice"))
        assertEquals(TipoDeProjecao.CARPLAY, PacotesDeProjecao.tipoDoPacote("com.x.carplayandroidauto"))
        assertEquals(TipoDeProjecao.ANDROID_AUTO, PacotesDeProjecao.tipoDoPacote("com.google.android.projection.gearhead"))
        assertEquals(TipoDeProjecao.NENHUM, PacotesDeProjecao.tipoDoPacote("com.waze"))
    }

    @Test fun pacotesDeTelaEDeReserva() {
        assertEquals(listOf("com.ts.androidauto.app", "com.autolink.androidauto.app"), PacotesDeProjecao.pacotesDeTela(TipoDeProjecao.ANDROID_AUTO))
        assertEquals(listOf("com.ts.carplay.app", "com.autolink.carplay.app"), PacotesDeProjecao.pacotesDeTela(TipoDeProjecao.CARPLAY))
        assertEquals(
            listOf("com.ts.androidauto.app", "com.autolink.androidauto.app", "com.ts.androidauto", "com.ts.androidauto.projectionservice"),
            PacotesDeProjecao.pacotesDeReserva(TipoDeProjecao.ANDROID_AUTO),
        )
        assertEquals(
            listOf("com.ts.carplay.app", "com.autolink.carplay.app", "com.ts.carplay"),
            PacotesDeProjecao.pacotesDeReserva(TipoDeProjecao.CARPLAY),
        )
        assertEquals("Android Auto", PacotesDeProjecao.rotulo(TipoDeProjecao.ANDROID_AUTO))
        assertEquals("ic_carplay", PacotesDeProjecao.icone(TipoDeProjecao.CARPLAY))
        assertEquals("ic_android_auto_default", PacotesDeProjecao.icone(TipoDeProjecao.ANDROID_AUTO, padrao = true))
    }

    @Test fun textosDaPaginaParaProjecao() {
        assertEquals(TipoDeProjecao.ANDROID_AUTO, PacotesDeProjecao.tipoDoTextoDaPagina("aa"))
        assertEquals(TipoDeProjecao.ANDROID_AUTO, PacotesDeProjecao.tipoDoTextoDaPagina("Android_Auto"))
        assertEquals(TipoDeProjecao.CARPLAY, PacotesDeProjecao.tipoDoTextoDaPagina("cp"))
        assertEquals(TipoDeProjecao.CARPLAY, PacotesDeProjecao.tipoDoTextoDaPagina("CARPLAY"))
        assertEquals(TipoDeProjecao.NENHUM, PacotesDeProjecao.tipoDoTextoDaPagina("xx"))
        assertEquals(TipoDeProjecao.NENHUM, PacotesDeProjecao.tipoDoTextoDaPagina(null))
    }

    @Test fun appsDaGwm() {
        val proprio = "br.com.deepgwt"
        assertTrue(RegrasDeOrigemDoApp.ehAppDaGwm("com.beantechs.hvac", proprio))
        assertTrue(RegrasDeOrigemDoApp.ehAppDaGwm("com.autolink.x", proprio))
        assertTrue(RegrasDeOrigemDoApp.ehAppDaGwm("com.gwm.hvac", proprio))
        assertTrue(RegrasDeOrigemDoApp.ehAppDaGwm("br.com.foo.gwm.bar", proprio))
        assertFalse(RegrasDeOrigemDoApp.ehAppDaGwm("br.com.deepgwt", proprio))
        assertFalse(RegrasDeOrigemDoApp.ehAppDaGwm("com.ts.carplay.app", proprio))
        assertFalse(RegrasDeOrigemDoApp.ehAppDaGwm("com.google.maps", proprio))
    }

    @Test fun appsFixosDoDock() {
        for (p in listOf("com.beantechs.vehiclecenter", "com.beantechs.settings", "com.beantechs.energyassistant", "com.beantechs.launcher")) {
            assertTrue(RegrasDeOrigemDoApp.ehAppFixoDoDock(p))
        }
        assertFalse(RegrasDeOrigemDoApp.ehAppFixoDoDock("com.beantechs.hvac"))
    }

    @Test fun quemPodeAlternarAbrirComoJanela() {
        val proprio = "br.com.deepgwt"
        assertTrue(RegrasDeOrigemDoApp.podeAlternarAbrirComoJanela("com.waze", proprio))
        assertFalse(RegrasDeOrigemDoApp.podeAlternarAbrirComoJanela("", proprio))
        assertFalse(RegrasDeOrigemDoApp.podeAlternarAbrirComoJanela(proprio, proprio))
        assertFalse(RegrasDeOrigemDoApp.podeAlternarAbrirComoJanela("com.beantechs.settings", proprio))
        assertFalse(RegrasDeOrigemDoApp.podeAlternarAbrirComoJanela("com.beantechs.hvac", proprio))
        assertFalse(RegrasDeOrigemDoApp.podeAlternarAbrirComoJanela("com.ts.carplay", proprio))
    }

    // --- normalização dos valores da ponte ---

    @Test fun modoDeTelaNormaliza() {
        assertEquals(ModoDeTela.DOIS_APPS, ModoDeTela.doTexto("appsOnly"))
        assertEquals(ModoDeTela.APP_E_CARRO, ModoDeTela.doTexto("triple"))
        assertEquals(ModoDeTela.APP_E_CARRO, ModoDeTela.doTexto("appCar"))
        assertEquals(ModoDeTela.APP_E_CARRO, ModoDeTela.doTexto("qualquer"))
        assertEquals(ModoDeTela.APP_E_CARRO, ModoDeTela.doTexto(null))
    }

    @Test fun proporcaoValidaOuNula() {
        assertEquals(ProporcaoDaDivisao.UM_PARA_UM, ProporcaoDaDivisao.doTexto("1:1"))
        assertEquals(ProporcaoDaDivisao.DOIS_PARA_UM, ProporcaoDaDivisao.doTexto("2:1"))
        assertEquals(ProporcaoDaDivisao.UM_PARA_DOIS, ProporcaoDaDivisao.doTexto("1:2"))
        assertNull(ProporcaoDaDivisao.doTexto("3:1"))
        assertNull(ProporcaoDaDivisao.doTexto(null))
    }

    @Test fun usoEladoDaPagina() {
        assertEquals(UsoDoLado.WIDGETS, UsoDoLado.doTexto("widgets"))
        assertEquals(UsoDoLado.APP, UsoDoLado.doTexto("app"))
        assertEquals(UsoDoLado.APP, UsoDoLado.doTexto("outro"))
        assertEquals(UsoDoLado.APP, UsoDoLado.doTexto(null))
        assertEquals(Lado.DIREITA, Lado.doTextoDaPagina("right"))
        assertEquals(Lado.ESQUERDA, Lado.doTextoDaPagina("left"))
        assertEquals(Lado.ESQUERDA, Lado.doTextoDaPagina("qualquer"))
        assertEquals(Lado.ESQUERDA, Lado.doTextoDaPagina(null))
    }

    @Test fun modoDaInterfaceValidoOuNulo() {
        assertEquals(ModoDaInterface.EMBUTIDA, ModoDaInterface.doTexto("inset"))
        assertEquals(ModoDaInterface.FLUTUANTE, ModoDaInterface.doTexto("floating"))
        assertEquals(ModoDaInterface.TELA_CHEIA, ModoDaInterface.doTexto("full"))
        assertNull(ModoDaInterface.doTexto("x"))
    }

    @Test fun politicaDeFoco() {
        assertEquals(0x308, PoliticaDeFoco.novasFlags(0, naoFocavel = true, noLimite = true))
        assertEquals(0x0, PoliticaDeFoco.novasFlags(0x308, naoFocavel = false, noLimite = false))
        assertEquals(0x300, PoliticaDeFoco.novasFlags(0x308, naoFocavel = false, noLimite = true))
        assertEquals(0x1000_0300, PoliticaDeFoco.novasFlags(0x1000_0000, naoFocavel = false, noLimite = true))
    }
}
