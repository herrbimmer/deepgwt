package br.com.deepgwt.tela3d.janelas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Os casos C1–C54 sobre o núcleo, com tempo e sistema falsos. */
class NucleoDasJanelasTest {

    private fun cenario(modo: String = "appCar", extras: Map<String, String> = emptyMap()) =
        CenarioDasJanelas(prefs = mapOf("window_apps" to "com.waze,com.spotify,com.maps,com.chat", "mode" to modo) + extras)

    private fun primeirasAberturas(c: CenarioDasJanelas) = c.sistema.aberturas.filter { !it.reabertura }.map { it.pacote }

    private val esquerdaAppCar = Retangulo(128, 52, 1037, 496)
    private val direitaAppCar = Retangulo(1180, 100, 1792, 496)
    private val esquerda11 = Retangulo(128, 52, 956, 720)
    private val direita11 = Retangulo(964, 52, 1792, 720)

    // --- C1: decisão de abertura ---

    @Test fun pacoteVazioNaoFazNada() {
        val c = cenario()
        c.nucleo.abrirComoODock("")
        assertTrue(c.sistema.chamadas.isEmpty())
    }

    @Test fun pacoteDeProjecaoAbreAProjecaoDepoisDeEnfileirar() {
        val c = cenario()
        c.sistema.tarefaAchada["com.ts.carplay.app"] = 12
        c.nucleo.abrirComoODock("com.ts.carplay.app")
        assertTrue(c.sistema.chamadas.isEmpty())
        c.tempo.avancar(0)
        assertEquals(listOf("levantarPorPacote:com.ts.carplay.app"), c.sistema.chamadas)
    }

    @Test fun appDeMentiraDoEmuladorSoMostraUmAviso() {
        val c = cenario()
        c.catalogo.mentira = setOf("com.fake")
        c.nucleo.abrirComoODock("com.fake", "Falso")
        c.nucleo.abrirComoODock("com.fake")
        assertEquals(listOf("Falso (emulator stub)", "com.fake (emulator stub)"), c.tela.avisos)
        assertTrue(c.sistema.chamadas.isEmpty())
        assertTrue(c.catalogo.recentes.isEmpty())
    }

    @Test fun registraORecenteEAbreEmTelaCheiaApp_NaoMarcadoComoJanela() {
        val c = cenario()
        c.sistema.componentes["com.outro"] = "com.outro/.Main"
        c.nucleo.abrirComoODock("com.outro")
        assertEquals(listOf("com.outro"), c.catalogo.recentes)
        assertEquals(listOf("telaCheia:com.outro:${c.tela.limiteTelaCheia}:normal"), c.sistema.chamadas)
    }

    @Test fun configuracoesDoCarroAbremARaizDasConfiguracoes() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.android.car.settings")
        assertEquals(1, c.sistema.aberturas.size)
        assertEquals("configuracoes", c.sistema.aberturas[0].tipo)
        assertEquals("com.android.settings", c.sistema.aberturas[0].pacote)
    }

    @Test fun appDaGwmEFixoDoDockAbremEmTelaCheia() {
        val c = cenario(extras = mapOf("window_apps" to "com.beantechs.settings"))
        c.sistema.componentes["com.beantechs.settings"] = "com.beantechs.settings/.Main"
        c.sistema.componentes["com.beantechs.hvac"] = "com.beantechs.hvac/.Main"
        c.nucleo.abrirComoODock("com.beantechs.settings")
        c.nucleo.abrirComoODock("com.beantechs.hvac")
        assertEquals(listOf("telaCheia", "telaCheia"), c.sistema.aberturas.map { it.tipo })
    }

    @Test fun semAtividadeDeEntradaOPedidoEIgnorado() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.sem.entrada")
        assertTrue(c.sistema.aberturas.isEmpty())
        c.sistema.componentes.remove("com.waze")
        c.nucleo.abrirComoODock("com.waze")
        assertTrue(c.sistema.aberturas.isEmpty())
        assertEquals("", c.estado.pacoteEsquerda)
    }

    @Test fun telaCheiaEsqueceOSlotDoApp() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        c.nucleo.maximizar("com.waze")
        assertEquals("", c.estado.pacoteEsquerda)
        assertEquals("telaCheia", c.sistema.aberturas.last().tipo)
    }

    @Test fun excecaoAoAbrirTelaCheiaVaiParaOLog() {
        val c = cenario()
        c.sistema.componentes["com.outro"] = "com.outro/.Main"
        c.sistema.abrirLanca = true
        c.nucleo.abrirComoODock("com.outro")
        assertTrue(c.diario.contem("erro: tela cheia de com.outro falhou"))
    }

    // --- C10: janela do lado esquerdo ---

    @Test fun abreAJanelaNoLadoEsquerdoEmAppCar() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        assertEquals(
            listOf("janelaLivre", "removerTodas:com.waze", "janela:com.waze:$esquerdaAppCar:primeira"),
            c.sistema.chamadas,
        )
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        assertEquals(UsoDoLado.APP, c.estado.usoEsquerda)
        assertTrue(c.tela.layouts.last().pacoteEsquerda == "com.waze")
    }

    @Test fun repetidoNoMesmoAppFechaEEncerra() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.abrirComoODock("com.waze")
        assertTrue(c.sistema.chamadas.contains("fechar:com.waze:-1"))
        assertTrue(c.sistema.chamadas.contains("encerrar:com.waze"))
        assertEquals("", c.estado.pacoteEsquerda)
    }

    @Test fun emAppCarSemLadoLivreNaoFazNada() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.abrirComoODock("com.spotify")
        assertTrue(c.sistema.chamadas.isEmpty())
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        assertTrue(c.diario.contem("sem lado disponível"))
    }

    // --- C12: reposição repetida ---

    @Test fun reposicaoRodaEm150E200ms16VezesEReabreSoNaSegunda() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.sistema.limparChamadas()
        c.tempo.avancar(149)
        assertEquals(0, c.sistema.contar("fixar:com.waze"))
        c.tempo.avancar(1)
        assertEquals(1, c.sistema.contar("fixar:com.waze"))
        assertEquals(0, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
        c.tempo.avancar(200)
        assertEquals(2, c.sistema.contar("fixar:com.waze"))
        assertEquals(1, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
        c.tempo.avancar(3_150 - 350)
        assertEquals(16, c.sistema.contar("fixar:com.waze"))
        c.tempo.avancar(20_000)
        assertEquals(16, c.sistema.contar("fixar:com.waze"))
        assertEquals(1, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
    }

    @Test fun reposicaoGuardaONumeroDaTarefaAchada() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.tempo.avancar(150)
        c.sistema.limparChamadas()
        c.nucleo.abrirComoODock("com.waze")
        assertTrue(c.sistema.chamadas.contains("fechar:com.waze:31"))
    }

    @Test fun reposicaoParaQuandoOPacoteDoLadoMuda() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.tempo.avancar(150)
        assertEquals(1, c.sistema.contar("fixar:com.waze"))
        c.nucleo.maximizar("com.waze")
        c.tempo.avancar(10_000)
        assertEquals(1, c.sistema.contar("fixar:com.waze"))
    }

    @Test fun reposicaoSoUmaPorLado() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.tempo.avancar(100)
        c.nucleo.definirProporcao("2:1")
        c.tempo.avancar(10_000)
        // a segunda rotina cancelou a primeira: 16 execuções da nova + 1 do refixar imediato
        assertEquals(17, c.sistema.contar("fixar:com.waze"))
    }

    // --- Side by Side ---

    @Test fun doisAppsAlternamOsLadosEEntramNasProporcoes() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.nucleo.abrirComoODock("com.spotify")
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        assertEquals("com.spotify", c.estado.pacoteDireita)
        assertTrue(c.sistema.chamadas.contains("janela:com.waze:$esquerda11:primeira"))
        assertTrue(c.sistema.chamadas.contains("janela:com.spotify:$direita11:primeira"))
        assertEquals("right", c.estado.ultimoLado)
        assertTrue(c.estado.proximoEhEsquerda)
    }

    @Test fun doisAppsCheiosNaoAbremTerceiro() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.abrirComoODock("com.maps")
        assertTrue(c.sistema.chamadas.isEmpty())
    }

    @Test fun tocarNoAppDoLadoDireitoFechaEEncerra() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.abrirComoODock("com.spotify")
        assertTrue(c.sistema.chamadas.contains("fechar:com.spotify:-1"))
        assertTrue("o lado direito também encerra o app", c.sistema.chamadas.contains("encerrar:com.spotify"))
        assertEquals("", c.estado.pacoteDireita)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun trocarAsProporcoesRefixaOsLadosOcupados() {
        val c = cenario(modo = "appsOnly")
        c.sistema.tarefaAchada["com.waze"] = 31
        c.sistema.tarefaAchada["com.spotify"] = 32
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.definirProporcao("2:1")
        assertEquals(ProporcaoDaDivisao.DOIS_PARA_UM, c.estado.proporcao)
        assertTrue(c.sistema.chamadas.contains("fixar:com.waze:${Retangulo(128, 52, 1276, 720)}"))
        assertTrue(c.sistema.chamadas.contains("fixar:com.spotify:${Retangulo(1284, 52, 1792, 720)}"))
        assertEquals(listOf(ProporcaoDaDivisao.DOIS_PARA_UM), c.chrome.proporcoes)
        assertEquals("2:1", c.armazem.textos["splitRatio"])
    }

    @Test fun proporcaoInvalidaOuIgualEIgnorada() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.definirProporcao("5:5")
        c.nucleo.definirProporcao(null)
        c.nucleo.definirProporcao("1:1")
        assertTrue(c.chrome.proporcoes.isEmpty())
        assertTrue(c.armazem.gravacoes.isEmpty())
    }

    @Test fun trocarLadosTrocaOsSlotsERefixa() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.trocarLados()
        assertEquals("com.spotify", c.estado.pacoteEsquerda)
        assertEquals("com.waze", c.estado.pacoteDireita)
        assertTrue(c.sistema.chamadas.contains("fixar:com.spotify:$esquerda11"))
        assertTrue(c.sistema.chamadas.contains("fixar:com.waze:$direita11"))
    }

    @Test fun trocarLadosSemAppsNaoFazNada() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.trocarLados()
        assertTrue(c.sistema.chamadas.isEmpty())
    }

    // --- C20: modo de tela ---

    @Test fun sairDoSideBySideMinimizaOsDoisLadosSemEncerrar() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.definirModo("appCar")
        assertTrue(c.sistema.chamadas.contains("fechar:com.waze:-1"))
        assertTrue(c.sistema.chamadas.contains("fechar:com.spotify:-1"))
        assertEquals(0, c.sistema.contar("encerrar:"))
        assertEquals("", c.estado.pacoteEsquerda)
        assertEquals("", c.estado.pacoteDireita)
        assertEquals(ModoDeTela.APP_E_CARRO, c.estado.modo)
        assertEquals(listOf(ModoDeTela.APP_E_CARRO), c.chrome.modos)
        assertTrue(c.diario.contem("slots do Side by Side minimizados"))
        assertEquals("appCar", c.armazem.textos["mode"])
    }

    @Test fun trocarOModoVoltaOsUsosParaWidgets() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        assertEquals(UsoDoLado.APP, c.estado.usoEsquerda)
        c.nucleo.definirModo("appsOnly")
        assertEquals(UsoDoLado.WIDGETS, c.estado.usoEsquerda)
        assertEquals(UsoDoLado.WIDGETS, c.estado.usoDireita)
    }

    @Test fun entrarNoSideBySideNaoApagaOPadraoSalvo() {
        val c = cenario(extras = mapOf("mode" to "appsOnly"))
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.definirProporcao("2:1")
        c.nucleo.definirModo("appsOnly")
        assertEquals("appsOnly", c.armazem.textos["mode"])
    }

    @Test fun modoTripleEOAppCar() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.definirModo("triple")
        assertEquals(ModoDeTela.APP_E_CARRO, c.estado.modo)
        c.nucleo.definirModo(null)
        assertEquals(ModoDeTela.APP_E_CARRO, c.estado.modo)
        assertEquals(1, c.chrome.modos.size)
    }

    @Test fun mudarDeModoAvisaAPaginaERecalculaAsAncoras() {
        val c = cenario()
        val antes = c.tela.layouts.size
        val recalculos = c.tela.recalculos
        c.nucleo.definirModo("appsOnly")
        assertTrue(c.tela.layouts.size > antes)
        assertTrue(c.tela.recalculos > recalculos)
        assertEquals(ModoDeTela.DOIS_APPS, c.tela.layouts.last().modo)
    }

    // --- C24, C25 ---

    @Test fun usoWidgetsFechaOAppDoLado() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.definirUsoDoLado("left", "widgets")
        assertTrue(c.sistema.chamadas.contains("encerrar:com.waze"))
        assertEquals("", c.estado.pacoteEsquerda)
        assertEquals("widgets", c.armazem.textos["slot_appCar_left"])
    }

    @Test fun usoIgualAoAtualNaoFazNada() {
        val c = cenario()
        c.nucleo.definirUsoDoLado("left", "widgets")
        assertTrue(c.armazem.gravacoes.isEmpty())
    }

    @Test fun preferenciaDeLadoFoiRemovida() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.definirLadoPreferido("right")
        c.nucleo.definirLadoPreferido("left")
        assertEquals("auto", c.estado.ladoPreferido)
        assertTrue(c.armazem.gravacoes.isEmpty())
        assertTrue(c.nucleo.textoDeGetShellLayout().contains("\"launchSide\":\"auto\""))
    }

    // --- C26 e C27 ---

    @Test fun saveGravaOPadraoEMostraOAviso() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.salvarPadrao()
        assertTrue(c.tela.avisos.isEmpty())
        c.tempo.avancar(0)
        assertEquals(listOf("Side by Side saved as default"), c.tela.avisos)
        assertEquals("appsOnly", c.armazem.textos["mode"])
        assertEquals("com.waze", c.armazem.textos["leftApp"])
        assertEquals("", c.armazem.textos["rightApp"])
        assertEquals(ModoDeTela.DOIS_APPS, c.estado.modo)
        // gravação comum depois do SAVE não derruba o padrão
        c.nucleo.definirProporcao("1:2")
        assertEquals("appsOnly", c.armazem.textos["mode"])
        assertEquals("com.waze", c.armazem.textos["leftApp"])
    }

    @Test fun restauraOSideBySideNaRevelacao() {
        val c = cenario(modo = "appsOnly", extras = mapOf("leftApp" to "com.waze", "rightApp" to "com.spotify"))
        c.nucleo.aoRevelarLauncher()
        c.tempo.avancar(499)
        assertTrue(c.sistema.aberturas.isEmpty())
        c.tempo.avancar(1)
        assertEquals(listOf("com.waze"), primeirasAberturas(c))
        c.tempo.avancar(399)
        assertEquals(listOf("com.waze"), primeirasAberturas(c))
        c.tempo.avancar(1)
        assertEquals(listOf("com.waze", "com.spotify"), primeirasAberturas(c))
        assertEquals("com.spotify", c.estado.pacoteDireita)
    }

    @Test fun restauracaoSoUmaVezEMesmoSemPadraoNaoFazNada() {
        val c = cenario(modo = "appsOnly", extras = mapOf("leftApp" to "com.waze"))
        c.nucleo.aoRevelarLauncher()
        c.nucleo.aoRevelarLauncher()
        c.tempo.avancar(5_000)
        assertEquals(1, c.sistema.aberturas.count { !it.reabertura })
    }

    @Test fun emAppCarARevelacaoNaoRestauraNada() {
        val c = cenario(modo = "appCar", extras = mapOf("leftApp" to "com.waze"))
        c.nucleo.aoRevelarLauncher()
        c.tempo.avancar(5_000)
        assertTrue(c.sistema.aberturas.isEmpty())
    }

    @Test fun restauracaoDaDireitaSoSeOModoAindaEDoisApps() {
        val c = cenario(modo = "appsOnly", extras = mapOf("leftApp" to "com.waze", "rightApp" to "com.spotify"))
        c.nucleo.aoRevelarLauncher()
        c.tempo.avancar(600)
        c.nucleo.definirModo("appCar")
        c.tempo.avancar(2_000)
        assertEquals("", c.estado.pacoteDireita)
        assertEquals(0, c.sistema.aberturas.count { it.pacote == "com.spotify" })
    }

    // --- C4: abrir no lado pedido ---

    @Test fun abrirNoLadoEAdiadoEAbreOndeAPaginaPediu() {
        val c = cenario()
        c.nucleo.abrirNoLado("com.waze", Lado.DIREITA)
        assertTrue(c.sistema.chamadas.isEmpty())
        c.tempo.avancar(0)
        assertEquals("com.waze", c.estado.pacoteDireita)
        assertTrue(c.sistema.chamadas.contains("janela:com.waze:$direitaAppCar:primeira"))
        assertEquals(listOf("com.waze"), c.catalogo.recentes)
        assertEquals(UsoDoLado.APP, c.estado.usoDireita)
    }

    @Test fun abrirNoLadoNaoPassaPelasRegrasDeTelaCheia() {
        val c = cenario(extras = mapOf("window_apps" to ""))
        c.nucleo.abrirNoLado("com.waze", Lado.ESQUERDA)
        c.tempo.avancar(0)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun abrirNoLadoComConfiguracoesDoCarroNaoMexeNosSlots() {
        val c = cenario()
        c.nucleo.abrirNoLado("com.android.car.settings", Lado.ESQUERDA)
        c.tempo.avancar(0)
        assertEquals("configuracoes", c.sistema.aberturas.single().tipo)
        assertEquals("", c.estado.pacoteEsquerda)
        assertTrue(c.catalogo.recentes.isEmpty())
    }

    @Test fun abrirNoLadoComPacoteDeProjecaoAbreAProjecao() {
        val c = cenario()
        c.sistema.tarefaAchada["com.ts.androidauto.app"] = 40
        c.nucleo.abrirNoLado("com.ts.androidauto.app", Lado.ESQUERDA)
        c.tempo.avancar(0)
        assertTrue(c.sistema.chamadas.contains("levantarPorPacote:com.ts.androidauto.app"))
        assertEquals("", c.estado.pacoteEsquerda)
    }

    @Test fun mesmoAppNoOutroLadoMudaDeLado() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirNoLado("com.waze", Lado.DIREITA)
        c.tempo.avancar(0)
        c.assentar()
        c.nucleo.abrirNoLado("com.waze", Lado.ESQUERDA)
        c.tempo.avancar(0)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        assertEquals("", c.estado.pacoteDireita)
    }

    @Test fun abrirNoLadoSubstituiOAppDoLado() {
        val c = cenario()
        c.nucleo.abrirNoLado("com.waze", Lado.ESQUERDA)
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.abrirNoLado("com.spotify", Lado.ESQUERDA)
        c.tempo.avancar(0)
        assertEquals("com.spotify", c.estado.pacoteEsquerda)
        assertTrue(c.sistema.chamadas.contains("encerrar:com.waze"))
    }

    // --- C13/C14/C15/C16 ---

    @Test fun closeAppDoLadoDireitoFechaODireito() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.nucleo.fecharPorPacote("com.spotify")
        assertEquals("", c.estado.pacoteDireita)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun closeAppDeOutroAppFechaPelaRotaEsquerda() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.fecharPorPacote("com.outro")
        assertTrue(c.sistema.chamadas.contains("fechar:com.outro:-1"))
        assertTrue(c.sistema.chamadas.contains("encerrar:com.outro"))
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun closeAppSemPacoteFechaOAppDoEsquerdo() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.nucleo.fecharPorPacote(null)
        assertEquals("", c.estado.pacoteEsquerda)
    }

    @Test fun fecharJanelaDireitaFechaOLadoDireito() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.nucleo.fecharJanelaDireita()
        assertEquals("", c.estado.pacoteDireita)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        c.nucleo.fecharJanelaDireita()
    }

    @Test fun fecharTudoEAdiadoEFechaOsDois() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.fecharTudo()
        assertTrue(c.sistema.chamadas.isEmpty())
        c.tempo.avancar(0)
        assertEquals("", c.estado.pacoteEsquerda)
        assertEquals("", c.estado.pacoteDireita)
        assertTrue(c.sistema.chamadas.contains("encerrar:com.waze"))
        assertTrue(c.sistema.chamadas.contains("encerrar:com.spotify"))
    }

    @Test fun maximizarReabreEmTelaCheiaERedimensionaATarefa() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.tempo.avancar(150)
        c.sistema.limparChamadas()
        c.nucleo.maximizar("com.waze")
        assertEquals(
            listOf(
                "telaCheia:com.waze:${c.tela.limiteTelaCheia}:normal",
                "redimensionar:com.waze:31:${c.tela.limiteTelaCheia}",
            ),
            c.sistema.chamadas,
        )
        assertEquals("", c.estado.pacoteEsquerda)
    }

    @Test fun maximizarSemPacoteUsaOAppDoLado() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.nucleo.maximizar(null)
        assertEquals("com.waze", c.sistema.aberturas.last().pacote)
        assertEquals("telaCheia", c.sistema.aberturas.last().tipo)
    }

    @Test fun maximizarOAppDoDireitoEscolheOLadoDireito() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.abrirComoODock("com.spotify")
        c.assentar()
        c.nucleo.maximizar("com.spotify")
        assertEquals("", c.estado.pacoteDireita)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun maximizarDeOutroAppNaoUsaONumeroGuardadoDoLado() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.tempo.avancar(150)
        c.sistema.limparChamadas()
        c.nucleo.maximizar("com.maps")
        assertTrue(c.sistema.chamadas.any { it.startsWith("redimensionar:com.maps:-1:") })
    }

    // --- C29/C30: levantar ---

    @Test fun toqueSoltoLevantaAsJanelasEm60ms() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(59)
        assertEquals(0, c.sistema.contar("levantar:"))
        c.tempo.avancar(1)
        assertEquals(1, c.sistema.contar("levantar:com.waze"))
    }

    @Test fun toqueNaFaixaDeCimaEspera700msAMais() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.aoSoltarToque(true)
        c.tempo.avancar(759)
        assertEquals(0, c.sistema.contar("levantar:"))
        c.tempo.avancar(1)
        assertEquals(1, c.sistema.contar("levantar:com.waze"))
    }

    @Test fun semJanelaDeLadoOToqueNaoFazNada() {
        val c = cenario()
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(10_000)
        assertTrue(c.sistema.chamadas.isEmpty())
    }

    @Test fun levantarQueFalhaReabreNoLadoComOLimiteDe900ms() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.levantarFunciona = false
        c.sistema.limparChamadas()
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(60)
        assertEquals(1, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(60)
        assertEquals("a reabertura é limitada a 1 por 900 ms", 1, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
        c.tempo.avancar(900)
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(60)
        assertEquals(2, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
    }

    @Test fun tarefaNaoAchadaTambemCaiParaReabrir() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(60)
        assertEquals(1, c.sistema.contar("janela:com.waze:$esquerdaAppCar:reabertura"))
    }

    @Test fun chromePorCimaImpedeLevantarEExpiraEm20s() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.definirChromePorCima(true)
        assertTrue(c.estado.chromePorCima)
        assertFalse("o launcher volta a ser focável", c.tela.naoFocavel)
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(10_000)
        assertEquals(0, c.sistema.contar("levantar:"))
        c.tempo.avancar(10_000)
        assertFalse(c.estado.chromePorCima)
        assertTrue(c.diario.contem("chrome por cima expirou"))
        c.tempo.avancar(100)
        assertEquals(1, c.sistema.contar("levantar:com.waze"))
        assertTrue(c.tela.naoFocavel)
    }

    @Test fun desligarOChromePorCimaZeraAMemoriaELevantaLogo() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.nucleo.definirChromePorCima(true)
        c.sistema.limparChamadas()
        c.nucleo.definirChromePorCima(false)
        assertTrue(c.sistema.chamadas.contains("esquecerUltimo"))
        c.tempo.avancar(60)
        assertEquals(1, c.sistema.contar("levantar:com.waze"))
    }

    @Test fun chromePorCimaIgualAoAtualNaoFazNada() {
        val c = cenario()
        c.nucleo.definirChromePorCima(false)
        assertTrue(c.sistema.chamadas.isEmpty())
        assertEquals(0, c.tempo.pendentes())
    }

    @Test fun focoDaJanelaVoltandoLevantaOsLados() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.aoGanharFoco()
        c.tempo.avancar(60)
        assertEquals(1, c.sistema.contar("levantar:com.waze"))
    }

    @Test fun aoRetomarMarcaNoTopoNoMaximoACada250ms() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.sistema.limparChamadas()
        c.nucleo.aoRetomar()
        c.nucleo.aoRetomar()
        assertEquals(1, c.sistema.contar("noTopo:com.waze"))
        c.tempo.avancar(250)
        c.nucleo.aoRetomar()
        assertEquals(2, c.sistema.contar("noTopo:com.waze"))
    }

    // --- C31: política de foco ---

    @Test fun launcherFicaNaoFocavelEnquantoHaJanela() {
        val c = cenario()
        assertFalse(c.tela.naoFocavel)
        c.nucleo.abrirComoODock("com.waze")
        assertTrue(c.tela.naoFocavel)
        assertTrue(c.tela.noLimite)
        assertTrue(c.diario.contem("launcher NOT_FOCUSABLE enquanto há janela"))
        c.assentar()
        c.nucleo.fecharPorPacote("com.waze")
        assertFalse(c.tela.naoFocavel)
        assertEquals(listOf(ModoDaInterface.FLUTUANTE), c.tela.imersivos)
        assertTrue(c.diario.contem("launcher focável"))
    }

    @Test fun pausaDaWebViewSoSemJanelaDeLado() {
        val c = cenario()
        assertTrue(c.nucleo.aoPausar())
        c.nucleo.abrirComoODock("com.waze")
        assertFalse(c.nucleo.aoPausar())
    }

    // --- C32/C33: vigia ---

    @Test fun vigiaComecaEm700msEPulaDuranteAAberturaEmCurso() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.sistema.limparChamadas()
        c.tempo.avancar(1_999)
        assertEquals(0, c.sistema.contar("visibilidade:"))
        c.tempo.avancar(1_000)
        assertTrue(c.sistema.contar("visibilidade:com.waze") >= 1)
    }

    @Test fun vigiaLimpaOSlotQuandoAJanelaSumiu() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar(2_000)
        c.sistema.visibilidade["com.waze"] = Visibilidade.SUMIU
        c.tempo.avancar(1_000)
        assertEquals("", c.estado.pacoteEsquerda)
        assertTrue(c.diario.contem("janela livre esquerda sumiu ou minimizada"))
        // a tarefa não é removida e o app não é encerrado
        assertEquals(0, c.sistema.contar("encerrar:"))
        assertEquals(0, c.sistema.contar("fechar:"))
        // o vigia desliga junto
        c.sistema.limparChamadas()
        c.tempo.avancar(10_000)
        assertEquals(0, c.sistema.contar("visibilidade:"))
    }

    @Test fun desconhecidoNuncaLimpaOSlot() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar(2_000)
        c.sistema.visibilidadePadrao = Visibilidade.DESCONHECIDA
        c.tempo.avancar(10_000)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        c.nucleo.aoRetomar()
        c.tempo.avancar(1_000)
        assertEquals("o retorno ao primeiro plano também não limpa no desconhecido", "com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun visivelMantemOSlot() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar(10_000)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun oOuvinteDePilhaDisparaASincronizacao() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar(2_100)
        c.sistema.limparChamadas()
        c.sistema.visibilidade["com.waze"] = Visibilidade.SUMIU
        c.sistema.ouvinte!!.run()
        c.tempo.avancar(0)
        assertEquals("", c.estado.pacoteEsquerda)
    }

    @Test fun sincronizacaoPausaDuranteAFolgaDoLevantar() {
        val c = cenario()
        c.sistema.tarefaAchada["com.waze"] = 31
        c.nucleo.abrirComoODock("com.waze")
        c.assentar(2_100)
        c.sistema.visibilidade["com.waze"] = Visibilidade.SUMIU
        c.nucleo.aoSoltarToque(false)
        c.tempo.avancar(60)
        c.sistema.limparChamadas()
        c.nucleo.sincronizar()
        assertEquals("durante a folga nada é lido", 0, c.sistema.contar("visibilidade:"))
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    // --- C45: página ---

    @Test fun semRaizMedidaTentaDeNovoACada300ms() {
        val c = cenario()
        c.tela.medida = false
        val antes = c.tela.layouts.size
        c.nucleo.avisarPagina()
        c.tempo.avancar(900)
        assertEquals(antes, c.tela.layouts.size)
        c.tela.medida = true
        c.tempo.avancar(300)
        assertEquals(antes + 1, c.tela.layouts.size)
        c.tempo.avancar(5_000)
        assertEquals(antes + 1, c.tela.layouts.size)
    }

    @Test fun avisarAPaginaTambemAvisaAFaixa() {
        val c = cenario()
        val antes = c.chrome.mudancasDeSlot
        c.nucleo.avisarPagina()
        assertEquals(antes + 1, c.chrome.mudancasDeSlot)
    }

    @Test fun aRaizMedidaRecalculaAsAncorasEAvisa() {
        val c = cenario()
        val recalculos = c.tela.recalculos
        val layouts = c.tela.layouts.size
        c.nucleo.aoRaizMedida()
        assertEquals(recalculos + 1, c.tela.recalculos)
        assertEquals(layouts + 1, c.tela.layouts.size)
    }

    @Test fun oLayoutDaPaginaVemDoEstado() {
        val c = cenario(modo = "appsOnly")
        c.nucleo.abrirComoODock("com.waze")
        val ultimo = c.tela.layouts.last()
        assertEquals("com.waze", ultimo.pacoteEsquerda)
        assertEquals(ModoDeTela.DOIS_APPS, ultimo.modo)
        assertEquals("left", ultimo.ultimoLado)
        assertFalse(ultimo.proximoEhEsquerda)
    }

    // --- C46 e extras do §4 ---

    @Test fun uiModeValidoGravaEReaplicaOImersivo() {
        val c = cenario()
        c.nucleo.consumirExtras(ExtrasDeEntrada(modoDaInterface = "inset"))
        assertEquals(ModoDaInterface.EMBUTIDA, c.estado.modoDaInterface)
        assertEquals(listOf(ModoDaInterface.EMBUTIDA), c.tela.imersivos)
        assertEquals("inset", c.armazem.textos["uiMode"])
        val layouts = c.tela.layouts.size
        c.tempo.avancar(249)
        assertEquals(layouts, c.tela.layouts.size)
        c.tempo.avancar(1)
        assertEquals(layouts + 1, c.tela.layouts.size)
    }

    @Test fun uiModeInvalidoEIgnorado() {
        val c = cenario()
        c.nucleo.consumirExtras(ExtrasDeEntrada(modoDaInterface = "nada"))
        assertEquals(ModoDaInterface.FLUTUANTE, c.estado.modoDaInterface)
        assertTrue(c.tela.imersivos.isEmpty())
    }

    @Test fun extrasNaOrdemUiModeTarefaProjecaoELancamento() {
        val c = cenario()
        c.sistema.tarefaAchada["com.ts.androidauto.app"] = 40
        c.nucleo.consumirExtras(ExtrasDeEntrada("full", 5, "AA", "com.waze"))
        c.tempo.avancar(0)
        assertEquals(ModoDaInterface.TELA_CHEIA, c.estado.modoDaInterface)
        val ordem = c.sistema.chamadas.filter { it.startsWith("levantarNumero") || it.startsWith("levantarPorPacote") || it.startsWith("janelaLivre") }
        assertEquals("levantarNumero:5", ordem.first())
        assertTrue(c.sistema.chamadas.contains("levantarPorPacote:com.ts.androidauto.app"))
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun tarefaNegativaNoExtraEIgnorada() {
        val c = cenario()
        c.nucleo.consumirExtras(ExtrasDeEntrada(tarefaParaLevantar = -1))
        assertEquals(0, c.sistema.contar("levantarNumero"))
    }

    @Test fun aoCriarLeAsPreferenciasERegistraOOuvinte() {
        val c = CenarioDasJanelas(prefs = mapOf("mode" to "appsOnly", "splitRatio" to "1:2", "uiMode" to "full"), booleanos = mapOf("nextLeft" to false))
        assertEquals(ModoDeTela.DOIS_APPS, c.estado.modo)
        assertEquals(ProporcaoDaDivisao.UM_PARA_DOIS, c.estado.proporcao)
        assertEquals(ModoDaInterface.TELA_CHEIA, c.estado.modoDaInterface)
        assertFalse(c.estado.proximoEhEsquerda)
        assertTrue(c.sistema.ouvinte != null)
        assertEquals(listOf(TipoDeProjecao.NENHUM), c.chrome.projecoes)
    }

    @Test fun semOuvinteDePilhaSegueSoComOVigia() {
        val c = cenario()
        c.sistema.ouvinteFunciona = false
        c.nucleo.aoCriar(ExtrasDeEntrada.NENHUM)
        assertTrue(c.diario.contem("ouvinte de pilha indisponível"))
    }

    @Test fun aPresencaDaProjecaoVaiParaAFaixa() {
        val c = cenario()
        c.projecao.ouvinte!!.invoke(TipoDeProjecao.CARPLAY)
        assertEquals(TipoDeProjecao.CARPLAY, c.chrome.projecoes.last())
    }

    // --- C40/C42: projeção ---

    @Test fun projecaoComTarefaLevantaESoAvisaAPagina() {
        val c = cenario()
        c.sistema.tarefaAchada["com.ts.androidauto.app"] = 40
        c.nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        assertTrue(c.projecao.pedidos.isEmpty())
        assertEquals(listOf("levantarPorPacote:com.ts.androidauto.app"), c.sistema.chamadas)
        c.tempo.avancar(5_000)
        assertEquals(0, c.sistema.contar("projecao:"))
    }

    @Test fun projecaoTentaOSegundoPacoteDeTela() {
        val c = cenario()
        c.sistema.tarefaAchada["com.autolink.androidauto.app"] = 8
        c.nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        assertEquals(
            listOf("levantarPorPacote:com.ts.androidauto.app", "levantarPorPacote:com.autolink.androidauto.app"),
            c.sistema.chamadas,
        )
    }

    @Test fun projecaoSemTarefaPedeAExibicaoEEsperaUmSegundoEMeio() {
        val c = cenario()
        c.nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        assertEquals(listOf(TipoDeProjecao.ANDROID_AUTO), c.projecao.pedidos)
        c.sistema.limparChamadas()
        c.tempo.avancar(1_499)
        assertTrue(c.sistema.chamadas.isEmpty())
        c.tempo.avancar(1)
        assertTrue(c.sistema.chamadas.contains("projecao:com.ts.androidauto.app/Aap:${NucleoDasJanelas.TELA_INTEIRA}"))
    }

    @Test fun seATarefaApareceNosUmSegundoEMeioELevantadaEmVezDeLancar() {
        val c = cenario()
        c.nucleo.abrirProjecao(TipoDeProjecao.CARPLAY)
        c.sistema.tarefaAchada["com.ts.carplay.app"] = 12
        c.sistema.limparChamadas()
        c.tempo.avancar(1_500)
        assertEquals(0, c.sistema.contar("projecao:"))
        assertEquals(1, c.sistema.contar("levantarPorPacote:com.ts.carplay.app"))
    }

    @Test fun novaSolicitacaoCancelaAResolucaoAnterior() {
        val c = cenario()
        c.nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        c.tempo.avancar(500)
        c.nucleo.abrirProjecao(TipoDeProjecao.CARPLAY)
        c.sistema.limparChamadas()
        c.tempo.avancar(5_000)
        assertEquals(1, c.sistema.contar("projecao:"))
        assertEquals(listOf(TipoDeProjecao.ANDROID_AUTO, TipoDeProjecao.CARPLAY), c.projecao.pedidos)
    }

    @Test fun projecaoSemAlternativaDeAberturaSoRegistra() {
        val c = cenario()
        c.sistema.reservaDeProjecao = emptyList()
        c.nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        c.tempo.avancar(2_000)
        assertTrue(c.diario.contem("projeção: sem alternativa de abertura"))
    }

    @Test fun projecaoTentaAProximaIntentQuandoUmaFalha() {
        val c = cenario()
        val tentativas = ArrayList<String>()
        c.sistema.reservaDeProjecao = listOf("a/.A", "b/.B")
        // a primeira lança, a segunda funciona
        val falso = object : PortaDoSistema by c.sistema {
            override fun abrirProjecao(componente: String, limites: Retangulo) {
                tentativas += componente
                if (componente == "a/.A") throw IllegalStateException("x")
            }
        }
        val nucleo = NucleoDasJanelas(
            "br.com.deepgwt", falso, c.tela, c.tempo, c.tempo, PreferenciasDasJanelas(c.armazem), c.chrome, c.catalogo, c.projecao, c.diario,
        )
        nucleo.aoCriar(ExtrasDeEntrada.NENHUM)
        nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        c.tempo.avancar(1_500)
        assertEquals(listOf("a/.A", "b/.B"), tentativas)
    }

    @Test fun projecaoNuncaMexeNosSlots() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.nucleo.abrirProjecao(TipoDeProjecao.ANDROID_AUTO)
        c.tempo.avancar(2_000)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun tipoNenhumNaoFazNada() {
        val c = cenario()
        c.nucleo.abrirProjecao(TipoDeProjecao.NENHUM)
        assertTrue(c.sistema.chamadas.isEmpty())
        assertTrue(c.projecao.pedidos.isEmpty())
    }

    // --- C49/C50 ---

    @Test fun aSaidaDoLauncherFechaOsDoisLados() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.assentar()
        c.nucleo.aoPararVisivelmente(janelaVisivel = false, mudandoDeConfiguracao = false)
        c.tempo.avancar(0)
        assertEquals("", c.estado.pacoteEsquerda)
        assertEquals(1, c.catalogo.saiuDaFrente)
    }

    @Test fun mudancaDeConfiguracaoOuAberturaEmCursoNaoFecham() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.aoPararVisivelmente(janelaVisivel = false, mudandoDeConfiguracao = false)
        c.tempo.avancar(0)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        c.assentar()
        c.nucleo.aoPararVisivelmente(janelaVisivel = false, mudandoDeConfiguracao = true)
        c.tempo.avancar(0)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
        c.nucleo.aoPararVisivelmente(janelaVisivel = true, mudandoDeConfiguracao = false)
        c.tempo.avancar(0)
        assertEquals("com.waze", c.estado.pacoteEsquerda)
    }

    @Test fun destruirCancelaTudoENaoFechaJanelas() {
        val c = cenario()
        c.nucleo.abrirComoODock("com.waze")
        c.nucleo.definirChromePorCima(true)
        c.nucleo.aoRetomar()
        c.nucleo.aoDestruir()
        assertEquals(0, c.tempo.pendentes())
        assertTrue(c.sistema.chamadas.contains("removerOuvinte"))
        assertEquals(0, c.sistema.contar("encerrar:"))
        c.sistema.limparChamadas()
        c.tempo.avancar(30_000)
        assertTrue(c.sistema.chamadas.isEmpty())
        assertFalse(c.estado.chromePorCima)
    }

    // --- C54 pelo núcleo ---

    @Test fun abrirComoJanelaDoNucleo() {
        val c = cenario(extras = mapOf("window_apps" to "com.waze"))
        assertTrue(c.nucleo.abreComoJanela("com.waze"))
        assertFalse(c.nucleo.abreComoJanela("com.spotify"))
        assertTrue(c.nucleo.podeAlternarAbrirComoJanela("com.spotify"))
        assertFalse(c.nucleo.podeAlternarAbrirComoJanela("com.beantechs.hvac"))
        assertFalse(c.nucleo.podeAlternarAbrirComoJanela("com.ts.carplay.app"))
        c.nucleo.alternarAbrirComoJanela("com.spotify")
        assertEquals("com.spotify,com.waze", c.armazem.textos["window_apps"])
        c.nucleo.esquecerPacote("com.waze")
        assertEquals("com.spotify", c.armazem.textos["window_apps"])
    }

    @Test fun appComoJanelaMarcadoAbreEmJanelaNoModoAppCar() {
        val c = cenario(extras = mapOf("window_apps" to ""))
        c.sistema.componentes["com.waze"] = "com.waze/.Main"
        c.nucleo.abrirComoODock("com.waze")
        assertEquals("telaCheia", c.sistema.aberturas.last().tipo)
        c.nucleo.alternarAbrirComoJanela("com.waze")
        c.nucleo.abrirComoODock("com.waze")
        assertEquals("janela", c.sistema.aberturas.last().tipo)
    }

    @Test fun estadoPublicadoAcompanhaOsSlots() {
        val c = cenario()
        assertNull(c.estado.pacoteEsquerda.takeIf { it.isNotEmpty() })
        c.nucleo.abrirComoODock("com.waze")
        assertTrue(c.estado.temJanelaDeLado)
        assertTrue(c.nucleo.temJanelaDeLado())
    }
}
