package br.com.opengw.barra

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores da especificação da interface. */
class RegrasDaInterfaceTest {

    private val r = RegrasDaInterface

    @After
    fun depois() {
        val s = EstadoDaBarra
        s.fecharTudo()
        s.painelExpandido = false
        s.visivel = true
        s.gestoParaCima = GestoParaCima.GAVETA
        s.pacoteDoGesto = ""
        s.pacoteEscolhido = ""
    }

    @Test
    fun `ventilacao do banco caso 23`() {
        assertEquals(1, r.proximoNivelDaVentilacao("0", "3"))
        assertEquals(3, r.proximoNivelDaVentilacao("2", "3"))
        assertEquals(0, r.proximoNivelDaVentilacao("3", "3"))
        assertEquals(2, r.proximoNivelDaVentilacao("1", "{3,3}"))
        assertEquals(1, r.proximoNivelDaVentilacao("x", "3"))
        assertEquals(0, r.proximoNivelDaVentilacao("5", "5"))
        assertEquals(0, r.proximoNivelDaVentilacao("2", "1"))
        assertEquals(3, r.barrinhasDaVentilacao("5"))
        assertEquals(3, r.barrinhasDaVentilacao("0"))
        assertEquals(1, r.barrinhasDaVentilacao("1"))
        assertEquals(3, r.maximoDaVentilacao("{3,3}"))
        assertEquals(5, r.maximoDaVentilacao("9"))
    }

    @Test
    fun `passo do clima caso 24, como hoje e com o degrau (I-D9)`() {
        assertEquals(23.5f, r.passoDeTemperatura("23.0", 0.5f, encaixar = false), 0.0001f)
        assertEquals(22.5f, r.passoDeTemperatura("23.0", -0.5f, encaixar = false), 0.0001f)
        assertEquals(22.5f, r.passoDeTemperatura("--", 0.5f, encaixar = false), 0.0001f)
        assertEquals(32.0f, r.passoDeTemperatura("32.0", 0.5f, encaixar = false), 0.0001f)
        assertEquals(22.8f, r.passoDeTemperatura("22.3", 0.5f, encaixar = false), 0.0001f)
        assertEquals(23.0f, r.passoDeTemperatura("22.3", 0.5f, encaixar = true), 0.0001f)
        assertEquals(23.5f, r.passoDeTemperatura("23.0", 0.5f, encaixar = true), 0.0001f)
        assertEquals("23.5", r.temperaturaParaOCarro(23.5f))
    }

    @Test
    fun `icones e valores do volume casos 9, 28 e 29`() {
        assertEquals(RegrasDaInterface.IconeDoVolume.MUDO, r.iconeDaFatia(0))
        assertEquals(RegrasDaInterface.IconeDoVolume.BAIXO, r.iconeDaFatia(13))
        assertEquals(RegrasDaInterface.IconeDoVolume.ALTO, r.iconeDaFatia(14))
        assertEquals(RegrasDaInterface.IconeDoVolume.MUDO, r.iconeDoCartao(0, 0..39))
        assertEquals(RegrasDaInterface.IconeDoVolume.BAIXO, r.iconeDoCartao(19, 0..39))
        assertEquals(RegrasDaInterface.IconeDoVolume.ALTO, r.iconeDoCartao(20, 0..39))
        assertEquals(0, r.valorNoCartao(0f, 780f, 0..39))
        assertEquals(20, r.valorNoCartao(390f, 780f, 0..39))
        assertEquals(39, r.valorNoCartao(780f, 780f, 0..39))
        assertEquals(0, r.valorNoCartao(-10f, 780f, 0..39))
        assertEquals(0..39, r.faixaDaFatiaDeVolume("{0,39}"))
        assertEquals(0..40, r.faixaDaFatiaDeVolume(null))
    }

    @Test
    fun `grade de apps preenchida de baixo para cima e troca no arrasto`() {
        // 4 itens em 3 colunas: 2 fileiras; os 3 primeiros embaixo.
        assertEquals(1 to 0, r.posicaoNaGrade(0, 4))
        assertEquals(1 to 2, r.posicaoNaGrade(2, 4))
        assertEquals(0 to 0, r.posicaoNaGrade(3, 4))
        assertEquals(0 to 1, r.posicaoNaGrade(1, 2))
        val itens = listOf(Retangulo(0, 100, 100, 200), Retangulo(110, 100, 210, 200), Retangulo(220, 100, 320, 200))
        assertEquals(1, r.alvoDaTroca(160f, 150f, itens, 0))
        assertNull("borda (fora dos 60% internos)", r.alvoDaTroca(115f, 150f, itens, 0))
        assertNull("o próprio", r.alvoDaTroca(50f, 150f, itens, 0))
        assertEquals(listOf("b", "a", "c"), r.trocar(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("a"), r.trocar(listOf("a"), 0, 3))
    }

    @Test
    fun `lista do menu apps com os predefinidos caso 16`() {
        fun app(p: String) = AppDaBarra(p, null, null, null)
        val lista = r.listaDeApps(
            listOf(app("com.spotify.music"), app("com.ts.carplay.app")),
            listOf(app("com.ts.androidauto.app"), app("com.ts.carplay.app"), app("com.waze")),
        )
        assertEquals(listOf("com.spotify.music", "com.ts.carplay.app", "com.ts.androidauto.app"), lista.map { it.pacote })
        assertTrue(r.mostrarAdicionar("com.waze", lista, "eu"))
        assertFalse(r.mostrarAdicionar("com.spotify.music", lista, "eu"))
        assertFalse(r.mostrarAdicionar("", lista, "eu"))
        assertFalse(r.mostrarAdicionar("eu", lista, "eu"))
        assertEquals(0xFF112233.toInt(), r.corDoIcone("#112233"))
        assertEquals(0x80112233.toInt(), r.corDoIcone("#80112233"))
        assertNull(r.corDoIcone("azul"))
        assertNull(r.corDoIcone(null))
    }

    @Test
    fun `deslizes e gesto para cima casos 3 a 5`() {
        assertEquals(RegrasDaInterface.Deslize.PARA_CIMA, r.deslizeDaBarra(-46f))
        assertEquals(RegrasDaInterface.Deslize.NADA, r.deslizeDaBarra(-45f))
        assertEquals(RegrasDaInterface.Deslize.PARA_BAIXO, r.deslizeDaBarra(21f))
        assertEquals(RegrasDaInterface.Deslize.NADA, r.deslizeDaBarra(20f))
        assertTrue(r.gatilhoTrazABarra(-31f))
        assertFalse(r.gatilhoTrazABarra(-30f))
        assertEquals("com.beantechs.mediacenter", r.pacoteDoGesto(GestoParaCima.HOME_DE_FABRICA, ""))
        assertEquals("com.beantechs.applist", r.pacoteDoGesto(GestoParaCima.LISTA_DE_APPS, ""))
        assertEquals("com.x", r.pacoteDoGesto(GestoParaCima.APP_ESPECIFICO, "com.x"))
        assertNull(r.pacoteDoGesto(GestoParaCima.APP_ESPECIFICO, ""))
        assertNull(r.pacoteDoGesto(GestoParaCima.PAINEL, "com.x"))
    }

    @Test
    fun `gesto para cima no estado caso 5`() {
        val s = EstadoDaBarra
        s.menuDeClimaAberto = true
        s.gestoParaCima = GestoParaCima.GAVETA
        executarGestoParaCima()
        assertTrue(s.gavetaAberta)
        assertFalse(s.menuDeClimaAberto)
        s.gestoParaCima = GestoParaCima.PAINEL
        executarGestoParaCima()
        assertTrue(s.painelExpandido)
        assertFalse(s.gavetaAberta)
        s.gestoParaCima = GestoParaCima.APP_ESPECIFICO
        s.pacoteDoGesto = "com.x"
        executarGestoParaCima()
        assertFalse(s.painelExpandido)
        assertEquals("com.x", s.pacoteEscolhido)
        s.gestoParaCima = GestoParaCima.NADA
        s.menuDeAppsAberto = true
        s.visivel = false
        executarGestoParaCima()
        assertFalse(s.menuDeAppsAberto)
        assertFalse("NADA não mostra a barra", s.visivel)
    }

    @Test
    fun `textos dos menus`() {
        assertEquals("--", r.textoDoMostrador("23.0", ligado = false))
        assertEquals("23.0°C", r.textoDoMostrador("23", ligado = true))
        assertEquals("--", r.textoDoMostrador("255", ligado = true))
        assertEquals("23.5°", r.textoDaTemperatura(23.5f))
        assertEquals("--", r.textoDaTemperatura(null))
        assertTrue(r.linhaDoSportPlus(false, true, "1").startsWith("Vai desligar"))
        assertEquals("ESP desligado e motor a combustão forçado.", r.linhaDoSportPlus(true, false, "1"))
        assertTrue(r.linhaDoSportPlus(false, false, "1").endsWith("ligado."))
        assertTrue(r.linhaDoSportPlus(false, false, "0").endsWith("desligado."))
        assertEquals(3_000L, r.tempoDoCartaoMs(false))
        assertEquals(3_500L, r.tempoDoCartaoMs(true))
        assertEquals("JBL", r.textoDoLogo("jbl", ""))
        assertEquals("GWM", r.textoDoLogo("custom", " "))
        assertEquals("Meu", r.textoDoLogo("custom", "Meu"))
        assertNull(r.textoDoLogo("gwm", ""))
    }

    @Test
    fun `distribuicao das fatias cabe com tudo ligado`() {
        val todas = ContasDaBarra.fatias(ControleDeTemperatura.DUAS_ZONAS, mostrarVolume = true, mostrarModo = true)
        assertEquals(312, r.larguraMinimaDoLadoDp(todas.esquerda)) // 44+100+44, 12, 112
        assertEquals(288, r.larguraMinimaDoLadoDp(todas.direita)) // 88, 12, 44+100+44
        assertEquals(268, RegrasDaInterface.LARGURA_DO_TRIO_DP)
        // Carro: 1920 px a 160 dpi, painel lateral de 128 px.
        assertEquals((1920f - 128f - 8f - 12f - 268f) / 2f, r.larguraDoLadoDp(1920f, 128f, 0f), 0.001f)
        assertTrue(r.fatiasCabem(todas, 1920f, 128f, 0f))
        // Com o buraco do Android Auto (100 px) e com o painel escondido.
        assertTrue(r.fatiasCabem(todas, 1920f, 128f, 100f))
        assertTrue(r.fatiasCabem(todas, 1920f, 0f, 0f))
        // Validação da spec: 1080×600 a densidade 0,75 (1440 dp; painel de 128 px = 170,7 dp).
        assertTrue(r.fatiasCabem(todas, 1440f, 170.7f, 0f))
        // Emulador com wm density 160 (~1,43): 1920 px = 1342 dp; painel de 128 px = 89,5 dp.
        assertTrue(r.fatiasCabem(todas, 1342f, 89.5f, 0f))
        assertFalse(r.fatiasCabem(todas, 900f, 0f, 0f))
        assertEquals(0, r.larguraMinimaDoLadoDp(emptyList()))
    }
}
