package br.com.deepgwt.tela3d.ponte

import android.webkit.JavascriptInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class PonteDeViagensTest {

    private val viagens = ViagensFalsas()
    private val ponte = PonteDeViagens(viagens)

    @Test
    fun `getTrip limita os pontos`() {
        ponte.getTrip("100", 0)
        ponte.getTrip("100", -1)
        ponte.getTrip("100", 600)
        ponte.getTrip("100", 5000)
        assertEquals(
            listOf("viagem(100,1500)", "viagem(100,1500)", "viagem(100,600)", "viagem(100,1500)"),
            viagens.chamadas,
        )
    }

    @Test
    fun `chave que nao e numero devolve valor neutro e nao chama o gravador`() {
        assertEquals("null", ponte.getTrip("abc", 600))
        assertEquals("null", ponte.getTrip(null, 600))
        assertEquals("", ponte.getTripMap("abc"))
        assertEquals("{}", ponte.getStopTotals("abc"))
        assertEquals("{}", ponte.getStopTotals(null))
        assertEquals("null", ponte.getTripPointsSince("x", "1", 10))
        assertEquals("null", ponte.getTripPointsSince("1", "y", 10))
        assertTrue(viagens.chamadas.isEmpty())
    }

    @Test
    fun `chaves sao aparadas`() {
        assertEquals("{p}", ponte.getStopTotals(" 0 "))
        assertEquals("data:x", ponte.getTripMap(" 5"))
        assertEquals("{pts}", ponte.getTripPointsSince(" 1", "2 ", 0))
        assertEquals(listOf("paradas(0)", "mapa(5)", "pontos(1,2,1500)"), viagens.chamadas)
    }

    @Test
    fun `os outros metodos so encaminham`() {
        assertEquals("[d]", ponte.getDays("2026-10-01", "2026-10-04"))
        assertEquals("{live}", ponte.getLiveTrip())
        assertEquals("[m]", ponte.getMonths())
        assertEquals("{t}", ponte.getTotals())
        assertEquals("[l]", ponte.getTrips(0, 30))
        ponte.finishTrip()
        assertEquals(
            listOf("dias(2026-10-01,2026-10-04)", "aoVivo", "meses", "totais", "lista(0,30)", "encerrar"),
            viagens.chamadas,
        )
    }

    @Test
    fun `excecao do gravador nao escapa`() {
        val quebrado = object : ViagensParaAPonte by ViagensFalsas() {
            override fun aoVivo(): String = throw IllegalStateException("x")
            override fun viagem(idMs: Long, maxPontos: Int): String = throw IllegalStateException("x")
        }
        val ponteQuebrada = PonteDeViagens(quebrado)
        assertEquals("{}", ponteQuebrada.getLiveTrip())
        assertEquals("null", ponteQuebrada.getTrip("1", 5))
    }

    @Test
    fun `os 10 metodos tem JavascriptInterface e o nome na pagina e TripBridge`() {
        assertEquals("TripBridge", PonteDeViagens.NOME_NA_PAGINA)
        val metodos = PonteDeViagens::class.java.declaredMethods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
        assertEquals(
            setOf(
                "finishTrip", "getDays", "getLiveTrip", "getMonths", "getStopTotals", "getTotals",
                "getTrip", "getTripMap", "getTripPointsSince", "getTrips",
            ),
            metodos.map { it.name }.toSet(),
        )
    }
}

class PonteDaTelemetriaTest {

    private val cache = CacheDeTelemetria()
    private val enviador = EnviadorFalso()
    private val executor = ExecutorManual()
    private val diario = DiarioFalso()

    private fun ponte(depuracao: Boolean = false) = PonteDaTelemetria(cache, enviador, executor, diario, depuracao)

    @Test
    fun `getCarData le o cache e nunca devolve nulo`() {
        cache.guardar("car.speed", "42")
        val p = ponte()
        assertEquals("42", p.getCarData("car.speed"))
        assertEquals("", p.getCarData("outra"))
        assertEquals("", p.getCarData(null))
    }

    @Test
    fun `comando valido valida direto e envia postado`() {
        val p = ponte()
        p.invokeVehicleCommand("toggle_trunk", null)
        assertTrue(enviador.envios.isEmpty())
        executor.rodarTudo()
        assertEquals(listOf("comando(toggle_trunk,)"), enviador.envios)
    }

    @Test
    fun `comando com nivel normaliza o valor`() {
        ponte().invokeVehicleCommand("set_sunroof_level", "007")
        executor.rodarTudo()
        assertEquals(listOf("comando(set_sunroof_level,7)"), enviador.envios)
    }

    @Test
    fun `comando recusado nao envia nada e registra aviso`() {
        val p = ponte()
        p.invokeVehicleCommand("open_trunk", null)
        p.invokeVehicleCommand("toggle_trunk", "1")
        p.invokeVehicleCommand("set_sunroof_level", "101")
        p.invokeVehicleCommand(null, null)
        executor.rodarTudo()
        assertTrue(enviador.envios.isEmpty())
        assertEquals(4, diario.avisos.size)
    }

    @Test
    fun `escrita de chave valida e envia postada`() {
        ponte().setCarData("car.hvac.fan_speed", "3")
        assertTrue(enviador.envios.isEmpty())
        executor.rodarTudo()
        assertEquals(listOf("chave(car.hvac.fan_speed,3)"), enviador.envios)
    }

    @Test
    fun `escrita recusada nao envia`() {
        val p = ponte()
        p.setCarData("car.vehicle.speed", "3")
        p.setCarData("car.hvac.fan_speed", "x".repeat(65))
        executor.rodarTudo()
        assertTrue(enviador.envios.isEmpty())
        assertEquals(2, diario.avisos.size)
    }

    @Test
    fun `falha no envio nao escapa`() {
        val quebrado = object : EnviadorDeComandos by EnviadorFalso() {
            override fun enviarComando(comando: String, valor: String) = throw IllegalStateException("x")
        }
        val p = PonteDaTelemetria(cache, quebrado, executor, diario, false)
        p.invokeVehicleCommand("toggle_trunk", null)
        executor.rodarTudo()
        assertEquals(1, diario.erros.size)
    }

    @Test
    fun `reportPerf so registra em debug`() {
        ponte(depuracao = false).reportPerf("{\"fps\":60}")
        assertTrue(diario.infos.isEmpty())
        ponte(depuracao = true).reportPerf("{\"fps\":60}")
        assertEquals(1, diario.infos.size)
    }

    @Test
    fun `os 4 metodos tem JavascriptInterface`() {
        assertEquals("TelemetryBridge", PonteDaTelemetria.NOME_NA_PAGINA)
        val metodos = PonteDaTelemetria::class.java.declaredMethods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
        assertEquals(setOf("getCarData", "invokeVehicleCommand", "reportPerf", "setCarData"), metodos.map { it.name }.toSet())
    }
}

class PonteDaMidiaTest {

    private class MidiaFalsa : MidiaParaAPonte {
        val chamadas = ArrayList<String>()
        var falhar = false
        override fun anexar(aoAtualizar: (payloadJson: String) -> Unit, aoPosicao: (posicaoMs: Long) -> Unit) {}
        override fun iniciar() { chamadas += "iniciar"; if (falhar) throw IllegalStateException("x") }
        override fun empurrarAgora() { chamadas += "empurrar" }
        override fun parar() {}
        override fun proxima() { chamadas += "proxima" }
        override fun anterior() { chamadas += "anterior" }
        override fun tocarOuPausar() { chamadas += "tocarOuPausar" }
    }

    private val midia = MidiaFalsa()
    private val janelas = JanelasFalsas()
    private val acoes = AcoesFalsas()
    private val executor = ExecutorManual()
    private val ponte = PonteDaMidia(midia, janelas, acoes, executor, DiarioFalso())

    @Test
    fun `transporte posta na principal`() {
        ponte.next()
        ponte.prev()
        ponte.playPause()
        assertTrue(midia.chamadas.isEmpty())
        executor.rodarTudo()
        assertEquals(listOf("proxima", "anterior", "tocarOuPausar"), midia.chamadas)
    }

    @Test
    fun `onViewerReady liga a midia e depois pede a localizacao`() {
        ponte.onViewerReady()
        executor.rodarTudo()
        assertEquals(listOf("iniciar", "empurrar"), midia.chamadas)
        assertEquals(listOf("pedirLocalizacao"), acoes.chamadas)
    }

    @Test
    fun `falha da midia so registra e a localizacao ainda e pedida`() {
        midia.falhar = true
        ponte.onViewerReady()
        executor.rodarTudo()
        assertEquals(listOf("pedirLocalizacao"), acoes.chamadas)
    }

    @Test
    fun `dismissSlot so fecha se ha app no slot da direita`() {
        ponte.dismissSlot()
        executor.rodarTudo()
        assertFalse(janelas.chamadas.contains("fecharDireita"))
        janelas.estado = janelas.estado.copy(direitoOcupado = true)
        ponte.dismissSlot()
        executor.rodarTudo()
        assertTrue(janelas.chamadas.contains("fecharDireita"))
    }

    @Test
    fun `os 5 metodos tem JavascriptInterface`() {
        assertEquals("MediaBridge", PonteDaMidia.NOME_NA_PAGINA)
        val metodos = PonteDaMidia::class.java.declaredMethods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
        assertEquals(setOf("dismissSlot", "next", "onViewerReady", "playPause", "prev"), metodos.map { it.name }.toSet())
    }
}

class PonteDoLancadorTest {

    private val janelas = JanelasFalsas()
    private val faixa = FaixaFalsa()
    private val acoes = AcoesFalsas()
    private val executor = ExecutorManual()
    private val prefs = PreferenciasFalsas()
    private val diario = DiarioFalso()
    private var estadoPublicado: EstadoDoShell? = null
    private var urlBuscada: String? = null

    private val catalogo = object : CatalogoParaAPonte {
        override fun listaDeAppsJson(): String = "[{\"packageName\":\"a\"}]"
        override fun iconeComoDataUrl(pacote: String): String = if (pacote == "a") "data:image/png;base64,AA" else ""
        override fun aoPacoteRemovido(pacote: String) {}
        override fun aplicarAreaDoSeletor(xPx: Int, yPx: Int, larguraPx: Int, alturaPx: Int, ativo: Boolean) {}
    }

    private fun ponte(musica: MusicaAtiva = MusicaAtiva { true }) = PonteDoLancador(
        janelas = janelas, catalogo = catalogo, faixa = faixa, acoes = acoes, executor = executor,
        preferencias = prefs, buscador = BuscadorDeJson { url -> urlBuscada = url; "corpo" }, relogio = AmbienteFalso(),
        musica = musica, estadoDoShell = { estadoPublicado }, diario = diario,
    )

    @Test
    fun `tem exatamente os 34 metodos do contrato`() {
        val esperados = setOf(
            "beginSplashFade", "captureDesktopSnapshot", "closeApp", "dismissOverlays", "endSplashOverlay", "fetchJson",
            "getAppIcon", "getClockEnvironment", "getInstalledApps", "getShellLayout", "hideBootProgress", "isMusicActive",
            "launchAppInPopup", "launchAppInSlot", "launchProjection", "loadWidgets", "maximizeApp", "placeBootProgress",
            "reportDesktopSwitcherHit", "revealLauncher", "saveAppsOnlyDefault", "saveShellBackground", "saveWidgets",
            "setBootProgress", "setChromeOnTop", "setLaunchSide", "setLauncherHidden", "setShellMode", "setSlotUse",
            "setSplitRatio", "swapApps", "updateCenterFill", "updateClockCardFace", "updateDockIndicators",
        )
        assertEquals(34, esperados.size)
        val reais = PonteDoLancador::class.java.declaredMethods.filter { it.isAnnotationPresent(JavascriptInterface::class.java) }
        assertEquals(esperados, reais.map { it.name }.toSet())
        assertEquals(34, reais.size)
        assertEquals("AppLauncherBridge", PonteDoLancador.NOME_NA_PAGINA)
    }

    @Test
    fun `tudo de janelas e postado na principal inclusive popup maximize e close`() {
        val p = ponte()
        p.launchAppInPopup("com.a")
        p.maximizeApp("com.b")
        p.closeApp("com.c")
        p.launchAppInSlot("com.d", "right")
        p.dismissOverlays()
        p.swapApps()
        p.saveAppsOnlyDefault()
        assertTrue("nada pode rodar na thread de ponte", janelas.chamadas.isEmpty())
        executor.rodarTudo()
        assertEquals(
            listOf(
                "abrirEmPopup(com.a)", "maximizar(com.b)", "fechar(com.c)", "abrirNoLado(com.d,right)",
                "dispensarSobreposicoes", "trocarLados", "salvarModoApps",
            ),
            janelas.chamadas,
        )
    }

    @Test
    fun `closeApp com nulo fecha o slot da esquerda`() {
        ponte().closeApp(null)
        executor.rodarTudo()
        assertEquals(listOf("fechar(null)"), janelas.chamadas)
    }

    @Test
    fun `lado do slot desconhecido vira esquerdo e pacote nulo e ignorado`() {
        val p = ponte()
        p.launchAppInSlot("com.a", "meio")
        p.launchAppInSlot(null, "left")
        p.launchAppInPopup(null)
        p.maximizeApp("")
        executor.rodarTudo()
        assertEquals(listOf("abrirNoLado(com.a,left)"), janelas.chamadas)
    }

    @Test
    fun `normalizacao de modo lado uso e proporcao`() {
        val p = ponte()
        p.setShellMode("triple")
        p.setShellMode("appsOnly")
        p.setLaunchSide("zzz")
        p.setLaunchSide("right")
        p.setSlotUse("right", "widgets")
        p.setSlotUse("x", "y")
        p.setSplitRatio("2:1")
        p.setSplitRatio("9:9")
        executor.rodarTudo()
        assertEquals(
            listOf(
                "modo(appCar)", "modo(appsOnly)", "ladoDeAbertura(auto)", "ladoDeAbertura(right)",
                "uso(right,widgets)", "uso(left,app)", "proporcao(2:1)",
            ),
            janelas.chamadas,
        )
    }

    @Test
    fun `launchProjection normaliza e ignora o resto`() {
        val p = ponte()
        p.launchProjection("android_auto")
        p.launchProjection("CarPlay")
        p.launchProjection("x")
        p.launchProjection(null)
        executor.rodarTudo()
        assertEquals(listOf("projecao(AA)", "projecao(CP)"), janelas.chamadas)
    }

    @Test
    fun `setChromeOnTop e setLauncherHidden vao para a UI`() {
        val p = ponte()
        p.setChromeOnTop(true)
        p.setLauncherHidden(true)
        assertTrue(acoes.chamadas.isEmpty())
        executor.rodarTudo()
        assertEquals(listOf("chromeNoTopo(true)", "escondida(true)"), acoes.chamadas)
    }

    @Test
    fun `progresso de carga limita o pct e ignora texto e titulo`() {
        val p = ponte()
        p.setBootProgress(150, "texto", "titulo")
        p.setBootProgress(-5, null, null)
        p.placeBootProgress("center")
        p.hideBootProgress()
        executor.rodarTudo()
        assertEquals(listOf("progresso(100)", "progresso(0)", "posicionar(center)", "esconderProgresso"), acoes.chamadas)
    }

    @Test
    fun `splash e faixa`() {
        val p = ponte()
        p.endSplashOverlay()
        p.revealLauncher()
        executor.rodarTudo()
        assertEquals(listOf("encerrarSplash", "revelarFaixa"), acoes.chamadas)
    }

    @Test
    fun `beginSplashFade so vale a primeira vez`() {
        val imediato = PonteDoLancador(
            janelas, catalogo, faixa, acoes, ExecutorImediato(), prefs, { "" }, AmbienteFalso(), { true }, { null }, diario,
        )
        imediato.beginSplashFade(1000)
        imediato.beginSplashFade(2000)
        assertEquals(listOf("fade(1000)"), acoes.chamadas)
    }

    @Test
    fun `miniatura ignora token vazio e limpa o resto`() {
        val p = ponte()
        p.captureDesktopSnapshot("'\";", 480, 180)
        p.captureDesktopSnapshot(null, 480, 180)
        p.captureDesktopSnapshot("tok-1_x'\"", 480, 180)
        executor.rodarTudo()
        assertEquals(listOf("miniatura(tok-1_x,480,180)"), acoes.chamadas)
    }

    @Test
    fun `area do seletor vai crua para a UI`() {
        ponte().reportDesktopSwitcherHit(1f, 2f, 3f, 4f, true)
        executor.rodarTudo()
        assertEquals(listOf("seletor(1.0,2.0,3.0,4.0,true)"), acoes.chamadas)
    }

    @Test
    fun `saveShellBackground grava ARGB e cor invalida grava preto`() {
        val p = ponte()
        p.saveShellBackground("#112233")
        assertEquals(0xFF112233.toInt(), prefs.inteiro("shellBg", 0))
        p.saveShellBackground("rgb(1,2,3)")
        assertEquals(0xFF000000.toInt(), prefs.inteiro("shellBg", 0))
        p.saveShellBackground(null)
        assertEquals(0xFF000000.toInt(), prefs.inteiro("shellBg", 0))
    }

    @Test
    fun `widgets gravam e leem sem validar nem limitar`() {
        val p = ponte()
        assertEquals("", p.loadWidgets())
        val grande = "w".repeat(200_000)
        p.saveWidgets(grande)
        assertEquals(grande, p.loadWidgets())
        p.saveWidgets(null)
        assertEquals("", p.loadWidgets())
    }

    @Test
    fun `getShellLayout usa o estado publicado ou o padrao`() {
        val p = ponte()
        assertEquals("{\"mode\":\"appCar\",\"splitRatio\":\"1:1\",\"launchSide\":\"auto\"}", p.getShellLayout())
        estadoPublicado = EstadoDoShell.PADRAO.copy(modo = "appsOnly", proporcao = "1:2", ladoPreferido = "left")
        assertEquals("{\"mode\":\"appsOnly\",\"splitRatio\":\"1:2\",\"launchSide\":\"left\"}", p.getShellLayout())
    }

    @Test
    fun `catalogo e musica`() {
        val p = ponte()
        assertEquals("[{\"packageName\":\"a\"}]", p.getInstalledApps())
        assertEquals("data:image/png;base64,AA", p.getAppIcon("a"))
        assertEquals("", p.getAppIcon("b"))
        assertEquals("", p.getAppIcon(null))
        assertTrue(p.isMusicActive())
        assertFalse(ponte(musica = { throw IllegalStateException("x") }).isMusicActive())
    }

    @Test
    fun `catalogo que falha devolve lista vazia`() {
        val quebrado = object : CatalogoParaAPonte by catalogo {
            override fun listaDeAppsJson(): String = throw IllegalStateException("x")
        }
        val p = PonteDoLancador(
            janelas, quebrado, faixa, acoes, executor, prefs, { "" }, AmbienteFalso(), { false }, { null }, diario,
        )
        assertEquals("[]", p.getInstalledApps())
    }

    @Test
    fun `ambiente do relogio`() {
        assertEquals(
            "{\"locale\":\"pt-BR\",\"timeZone\":\"America/Sao_Paulo\",\"system24\":true}",
            ponte().getClockEnvironment(),
        )
    }

    @Test
    fun `fetchJson entrega ao buscador`() {
        assertEquals("corpo", ponte().fetchJson("https://www.bing.com/x"))
        assertEquals("https://www.bing.com/x", urlBuscada)
    }

    @Test
    fun `updateClockCardFace nao faz nada nem decodifica`() {
        val p = ponte()
        p.updateClockCardFace("rev", 1, "data:image/png;base64," + "A".repeat(2_000_000))
        p.updateClockCardFace(null, 0, null)
        executor.rodarTudo()
        assertTrue(acoes.chamadas.isEmpty())
        assertTrue(diario.erros.isEmpty())
    }

    @Test
    fun `indicadores validos chegam a faixa postados e gravam dockSurfaceMode so ao mudar`() {
        val p = ponte()
        p.updateDockIndicators("{\"dockMode\":\"cards\",\"fps\":true}")
        assertTrue(faixa.aplicados.isEmpty())
        executor.rodarTudo()
        assertEquals(1, faixa.aplicados.size)
        assertEquals("cards", faixa.aplicados[0].modoDoDock)
        assertEquals("cards", prefs.texto("dockSurfaceMode", ""))
        prefs.mapa.remove("dockSurfaceMode")
        p.updateDockIndicators("{\"dockMode\":\"outro\"}")
        executor.rodarTudo()
        assertEquals("", prefs.texto("dockSurfaceMode", ""))
    }

    @Test
    fun `indicadores invalidos nao aplicam nada`() {
        val p = ponte()
        p.updateDockIndicators("{quebrado")
        p.updateDockIndicators(null)
        p.updateDockIndicators("")
        executor.rodarTudo()
        assertTrue(faixa.aplicados.isEmpty())
        assertEquals(1, diario.avisos.size)
    }

    @Test
    fun `falha ao aplicar so registra`() {
        faixa.falharAoAplicar = true
        ponte().updateDockIndicators("{\"fps\":true}")
        executor.rodarTudo()
        assertEquals(1, diario.erros.size)
    }

    @Test
    fun `preenchimento do centro`() {
        val p = ponte()
        p.updateCenterFill("{\"mode\":\"mixed\",\"visible\":false}")
        p.updateCenterFill("{\"mode\":\"zzz\"}")
        p.updateCenterFill("{ruim")
        executor.rodarTudo()
        assertEquals(listOf("mixed" to false, "car" to null), faixa.preenchimentos)
    }

    @Test
    fun `excecao em janelas nao escapa do metodo da ponte`() {
        val quebrada = object : JanelasParaAPonte by JanelasFalsas() {
            override fun trocarLados() = throw IllegalStateException("x")
        }
        val p = PonteDoLancador(
            quebrada, catalogo, faixa, acoes, executor, prefs, { "" }, AmbienteFalso(), { false }, { null }, diario,
        )
        p.swapApps()
        executor.rodarTudo()
        assertEquals(1, diario.erros.size)
    }
}

class BuscadorDoBingTest {

    private class Resposta(
        override val codigo: Int,
        private val corpo: ByteArray = ByteArray(0),
        private val cabecalhos: Map<String, String> = emptyMap(),
    ) : RespostaHttp {
        var fechada = false
        override fun cabecalho(nome: String): String? = cabecalhos[nome]
        override fun corpo(): InputStream = ByteArrayInputStream(corpo)
        override fun fechar() {
            fechada = true
        }
    }

    private val abertas = ArrayList<String>()

    private fun buscador(vararg respostas: Resposta): BuscadorDoBing {
        val fila = ArrayDeque(respostas.toList())
        return BuscadorDoBing(DiarioFalso()) { url ->
            abertas += url
            fila.removeFirst()
        }
    }

    @Test
    fun `so aceita os hosts do Bing por https`() {
        assertTrue(BuscadorDoBing.enderecoPermitido("https://www.bing.com/HPImageArchive.aspx"))
        assertTrue(BuscadorDoBing.enderecoPermitido(" https://bing.com/x "))
        assertFalse(BuscadorDoBing.enderecoPermitido("http://www.bing.com/x"))
        assertFalse(BuscadorDoBing.enderecoPermitido("https://www.bing.com.evil.example/x"))
        assertFalse(BuscadorDoBing.enderecoPermitido("https://wallhaven.cc/api"))
        assertFalse(BuscadorDoBing.enderecoPermitido("https://www.bing.com@evil.example/"))
        assertFalse(BuscadorDoBing.enderecoPermitido(null))
    }

    @Test
    fun `url fora do Bing nem abre conexao`() {
        val b = buscador()
        assertEquals("", b.buscar("https://wallhaven.cc/api/v1/search"))
        assertEquals("", b.buscar(null))
        assertTrue(abertas.isEmpty())
    }

    @Test
    fun `200 devolve o corpo em UTF-8 e fecha a conexao`() {
        val r = Resposta(200, "{\"a\":\"ação\"}".toByteArray(Charsets.UTF_8))
        assertEquals("{\"a\":\"ação\"}", buscador(r).buscar("https://www.bing.com/x"))
        assertTrue(r.fechada)
    }

    @Test
    fun `codigo diferente de 200 devolve vazio`() {
        assertEquals("", buscador(Resposta(404, "x".toByteArray())).buscar("https://www.bing.com/x"))
        assertEquals("", buscador(Resposta(500)).buscar("https://www.bing.com/x"))
    }

    @Test
    fun `corpo acima de 2 MB devolve vazio`() {
        val exato = ByteArray(BuscadorDoBing.TETO_DO_CORPO_EM_BYTES) { 'a'.code.toByte() }
        assertEquals(BuscadorDoBing.TETO_DO_CORPO_EM_BYTES, buscador(Resposta(200, exato)).buscar("https://www.bing.com/x").length)
        val grande = ByteArray(BuscadorDoBing.TETO_DO_CORPO_EM_BYTES + 1) { 'a'.code.toByte() }
        assertEquals("", buscador(Resposta(200, grande)).buscar("https://www.bing.com/x"))
    }

    @Test
    fun `redirecionamento dentro do Bing e seguido`() {
        val b = buscador(
            Resposta(302, cabecalhos = mapOf("Location" to "/novo?x=1")),
            Resposta(200, "ok".toByteArray()),
        )
        assertEquals("ok", b.buscar("https://www.bing.com/antigo"))
        assertEquals(listOf("https://www.bing.com/antigo", "https://www.bing.com/novo?x=1"), abertas)
    }

    @Test
    fun `redirecionamento para fora do Bing e recusado`() {
        val b = buscador(
            Resposta(302, cabecalhos = mapOf("Location" to "https://evil.example/x")),
            Resposta(200, "segredo".toByteArray()),
        )
        assertEquals("", b.buscar("https://www.bing.com/x"))
        assertEquals(1, abertas.size)
    }

    @Test
    fun `redirecionamento para http e recusado e laco de redirecionamentos para`() {
        val http = buscador(Resposta(301, cabecalhos = mapOf("Location" to "http://www.bing.com/x")))
        assertEquals("", http.buscar("https://www.bing.com/x"))
        abertas.clear()
        val laco = buscador(*Array(10) { Resposta(302, cabecalhos = mapOf("Location" to "/loop")) })
        assertEquals("", laco.buscar("https://www.bing.com/loop"))
        assertEquals(BuscadorDoBing.MAXIMO_DE_REDIRECIONAMENTOS + 1, abertas.size)
    }

    @Test
    fun `excecao de rede devolve vazio`() {
        val b = BuscadorDoBing(DiarioFalso()) { throw java.io.IOException("sem rede") }
        assertEquals("", b.buscar("https://www.bing.com/x"))
    }

    @Test
    fun `lerComTeto devolve nulo acima do teto`() {
        assertNull(BuscadorDoBing.lerComTeto(ByteArrayInputStream(ByteArray(11)), 10))
        assertEquals(10, BuscadorDoBing.lerComTeto(ByteArrayInputStream(ByteArray(10) { 'x'.code.toByte() }), 10)!!.length)
    }
}

class PaginaDaTela3dImplTest {

    private val cache = CacheDeTelemetria()
    private val scripts = ArrayList<String>()
    private var avisos = 0
    private val pagina = PaginaDaTela3dImpl(cache, { scripts += it }, { avisos++ })

    @Test
    fun `telemetria vai ao cache e a pagina`() {
        pagina.publicarTelemetria("app.location.place", "São Paulo")
        assertEquals("São Paulo", pagina.valorDeTelemetria("app.location.place"))
        assertEquals(1, scripts.size)
        assertTrue(scripts[0].contains("window.onCarDataUpdate(\"app.location.place\",\"São Paulo\")"))
    }

    @Test
    fun `valor desconhecido e vazio`() {
        assertEquals("", pagina.valorDeTelemetria("nada"))
    }

    @Test
    fun `os envios para a pagina viram scripts`() {
        pagina.enviarMidia("{\"title\":\"x\"}")
        pagina.enviarPosicaoDaMidia(1234L)
        pagina.enviarComandoDoDock("cardAction:power:popup")
        pagina.invalidarCatalogoDeApps()
        assertEquals(4, scripts.size)
        assertTrue(scripts[0].contains("onMediaNowPlaying"))
        assertTrue(scripts[1].contains("onMediaPosition(1234)"))
        assertTrue(scripts[2].contains("dockCommand(\"cardAction:power:popup\")"))
        assertTrue(scripts[3].contains("_invalidateAppsCatalog"))
    }

    @Test
    fun `aviso de layout pede ao dono`() {
        pagina.avisarLayoutDoShell()
        pagina.avisarLayoutDoShell()
        assertEquals(2, avisos)
    }
}
