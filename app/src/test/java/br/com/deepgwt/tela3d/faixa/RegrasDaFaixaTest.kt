package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasDaFaixaTest {
    // --- F4: visibilidade da faixa (tabela de verdade) ---

    @Test
    fun `faixa - tabela de verdade`() {
        val aC = ModoDaCasca.APP_E_CARRO
        val lado = ModoDaCasca.LADO_A_LADO
        // revelada, escondida, modo -> estado
        assertEquals(EstadoDaFaixa.INVISIVEL, RegrasDaFaixa.estadoDaFaixa(false, false, aC))
        assertEquals(EstadoDaFaixa.INVISIVEL, RegrasDaFaixa.estadoDaFaixa(false, true, aC))
        assertEquals(EstadoDaFaixa.VISIVEL, RegrasDaFaixa.estadoDaFaixa(true, false, aC))
        assertEquals(EstadoDaFaixa.GONE, RegrasDaFaixa.estadoDaFaixa(true, true, aC))
        assertEquals(EstadoDaFaixa.GONE, RegrasDaFaixa.estadoDaFaixa(false, false, lado))
        assertEquals(EstadoDaFaixa.GONE, RegrasDaFaixa.estadoDaFaixa(true, false, lado))
        assertEquals(EstadoDaFaixa.GONE, RegrasDaFaixa.estadoDaFaixa(true, true, lado))
    }

    @Test
    fun `faixa so e visivel se revelada, nao escondida e fora do lado a lado`() {
        for (revelada in listOf(false, true)) {
            for (escondida in listOf(false, true)) {
                for (modo in ModoDaCasca.entries) {
                    val visivel = RegrasDaFaixa.estadoDaFaixa(revelada, escondida, modo) == EstadoDaFaixa.VISIVEL
                    assertEquals(revelada && !escondida && modo == ModoDaCasca.APP_E_CARRO, visivel)
                }
            }
        }
    }

    // --- F13: banda ---

    @Test
    fun `banda - depende so da revelacao e da altura`() {
        assertTrue(RegrasDaFaixa.bandaVisivel(true, 60))
        assertFalse(RegrasDaFaixa.bandaVisivel(true, 0))
        assertFalse(RegrasDaFaixa.bandaVisivel(false, 60))
        assertFalse(RegrasDaFaixa.bandaVisivel(false, 0))
    }

    // --- F7 e F35 ---

    @Test
    fun `superficie - margem do conteiner e celula Cards`() {
        assertEquals(12, RegrasDaFaixa.margemDoConteinerDp(SuperficieDoDock.LANCADOR))
        assertEquals(0, RegrasDaFaixa.margemDoConteinerDp(SuperficieDoDock.CARTOES))
    }

    @Test
    fun `fileira de apps some quando a edicao dos cartoes esta ativa`() {
        assertTrue(RegrasDaFaixa.mostrarFileiraDeApps(SuperficieDoDock.LANCADOR, false))
        assertFalse(RegrasDaFaixa.mostrarFileiraDeApps(SuperficieDoDock.LANCADOR, true))
        assertFalse(RegrasDaFaixa.mostrarFileiraDeApps(SuperficieDoDock.CARTOES, false))
        assertFalse(RegrasDaFaixa.mostrarFileiraDeApps(SuperficieDoDock.CARTOES, true))
    }

    @Test
    fun `alinhamento do primeiro cartao com a area de widgets`() {
        // área esquerda a 300 px da borda da página, faixa com preenchimento de 128, sem margem
        assertEquals(172, RegrasDaFaixa.preenchimentoEsquerdoDaLinhaDeCartoes(300, 0, 128, 0))
        // a página começa em 40: (300 - 40) - 128 - 0
        assertEquals(132, RegrasDaFaixa.preenchimentoEsquerdoDaLinhaDeCartoes(300, 40, 128, 0))
        // nunca negativo: o primeiro cartão encosta no preenchimento da faixa
        assertEquals(0, RegrasDaFaixa.preenchimentoEsquerdoDaLinhaDeCartoes(0, 0, 128, 0))
        assertEquals(0, RegrasDaFaixa.preenchimentoEsquerdoDaLinhaDeCartoes(100, 0, 128, 0))
        // com margem de 12 no lançador
        assertEquals(160, RegrasDaFaixa.preenchimentoEsquerdoDaLinhaDeCartoes(300, 0, 128, 12))
    }

    // --- F11 a F16: composição da fileira ---

    private fun linha(vararg pares: Pair<String, Boolean>) = pares.map { AppDaLinha(it.first, it.second) }

    private fun chaves(plano: List<ItemPlanejado>) = plano.map { it.chave }

    @Test
    fun `fileira - ordem projecao, hub, side by side, recentes e demais`() {
        val plano = PlanoDaFileira.montar(
            projecaoPresente = true,
            hubVisivel = true,
            recentes = listOf("c", "a"),
            linha = linha("a" to false, "b" to false, "c" to false, "d" to false),
        )
        assertEquals(
            listOf(PlanoDaFileira.CHAVE_DA_PROJECAO, PlanoDaFileira.CHAVE_DO_HUB, PlanoDaFileira.CHAVE_DO_LADO_A_LADO, "c", "a", "b", "d"),
            chaves(plano),
        )
        assertEquals(
            listOf(TipoDeItem.PROJECAO, TipoDeItem.HUB, TipoDeItem.LADO_A_LADO, TipoDeItem.APP, TipoDeItem.APP, TipoDeItem.APP, TipoDeItem.APP),
            plano.map { it.tipo },
        )
    }

    @Test
    fun `fileira - sem projecao e sem hub so sobra o side by side`() {
        val plano = PlanoDaFileira.montar(false, false, emptyList(), emptyList())
        assertEquals(listOf(PlanoDaFileira.CHAVE_DO_LADO_A_LADO), chaves(plano))
    }

    @Test
    fun `fileira - o app de um slot de recente aparece uma so vez`() {
        val plano = PlanoDaFileira.montar(false, false, listOf("b", "a", "c"), linha("a" to false, "b" to false, "c" to false, "d" to false))
        val apps = plano.filter { it.tipo == TipoDeItem.APP }
        assertEquals(listOf("b", "a", "c", "d"), apps.map { it.chave })
        assertEquals(apps.size, apps.map { it.chave }.toSet().size)
        assertEquals(listOf(true, true, true, false), apps.map { it.recente })
    }

    @Test
    fun `fileira - so 3 slots de recentes`() {
        val plano = PlanoDaFileira.montar(false, false, listOf("a", "b", "c", "d"), linha("a" to false, "b" to false, "c" to false, "d" to false))
        val apps = plano.filter { it.tipo == TipoDeItem.APP }
        assertEquals(listOf("a", "b", "c", "d"), apps.map { it.chave })
        assertEquals(listOf(true, true, true, false), apps.map { it.recente })
    }

    @Test
    fun `fileira - ocultos ficam fora e recente oculto nao ocupa slot`() {
        val plano = PlanoDaFileira.montar(false, false, listOf("b", "a"), linha("a" to false, "b" to true, "c" to true, "d" to false))
        assertEquals(listOf("a", "d"), plano.filter { it.tipo == TipoDeItem.APP }.map { it.chave })
    }

    @Test
    fun `fileira - ordem do sistema mantida e um item por pacote`() {
        val plano = PlanoDaFileira.montar(false, false, emptyList(), linha("z" to false, "a" to false, "z" to false, "m" to false))
        assertEquals(listOf("z", "a", "m"), plano.filter { it.tipo == TipoDeItem.APP }.map { it.chave })
    }

    @Test
    fun `fileira - recente que nao esta na linha ainda aparece uma vez`() {
        val plano = PlanoDaFileira.montar(false, false, listOf("x", "x"), linha("a" to false))
        assertEquals(listOf("x", "a"), plano.filter { it.tipo == TipoDeItem.APP }.map { it.chave })
    }

    // --- revelação ---

    @Test
    fun `revelacao - o ultimo item da fila comeca primeiro`() {
        assertEquals(0L, RevelacaoDaFileira.atrasoMs(13, 12))
        assertEquals(2400L, RevelacaoDaFileira.atrasoMs(13, 0))
        assertEquals(200L, RevelacaoDaFileira.atrasoMs(13, 11))
        assertEquals(0L, RevelacaoDaFileira.atrasoMs(1, 0))
        // total para 13 itens: (13 - 1) * 0,2 + 0,52 s
        assertEquals(2920L, RevelacaoDaFileira.atrasoMs(13, 0) + RevelacaoDaFileira.DURACAO_MS)
    }

    @Test
    fun `revelacao - deslocamento, alfa e rastro por progresso`() {
        assertEquals(-120f, RevelacaoDaFileira.deslocamentoDp(0f), 0.001f)
        assertEquals(0f, RevelacaoDaFileira.deslocamentoDp(1f), 0.001f)
        assertEquals(-60f, RevelacaoDaFileira.deslocamentoDp(0.5f), 0.001f)
        assertEquals(0f, RevelacaoDaFileira.alfa(0f), 0.001f)
        assertEquals(0.8f, RevelacaoDaFileira.alfa(0.5f), 0.001f)
        assertEquals(1f, RevelacaoDaFileira.alfa(0.7f), 0.001f)
        assertEquals(1f, RevelacaoDaFileira.alfa(1f), 0.001f)
        assertEquals(90f, RevelacaoDaFileira.rastroDp(0f), 0.001f)
        assertEquals(0f, RevelacaoDaFileira.rastroDp(1f), 0.001f)
    }

    @Test
    fun `rastro - alfas das copias`() {
        assertEquals(0, RevelacaoDaFileira.alfaDaCopiaDoRastro(6))
        assertEquals(25, RevelacaoDaFileira.alfaDaCopiaDoRastro(5))
        assertEquals(50, RevelacaoDaFileira.alfaDaCopiaDoRastro(4))
        assertEquals(75, RevelacaoDaFileira.alfaDaCopiaDoRastro(3))
        assertEquals(100, RevelacaoDaFileira.alfaDaCopiaDoRastro(2))
        assertEquals(125, RevelacaoDaFileira.alfaDaCopiaDoRastro(1))
        assertTrue(RevelacaoDaFileira.alfaDaCopiaDoRastro(6) < RevelacaoDaFileira.ALFA_MINIMO_DA_COPIA)
        assertEquals(1f, RevelacaoDaFileira.fracaoDaCopia(6), 0.0001f)
    }

    // --- menu de edição ---

    @Test
    fun `menu de edicao - app comum tem todas as linhas aplicaveis em ordem`() {
        val linhas = RegrasDoMenuDeEdicao.linhas(
            podeAbrirComoJanela = true, abrirComoJanelaMarcado = false, haOcultos = true, podeDesinstalar = true,
        )
        assertEquals(
            listOf("Customize…", "Hide", "Open as window", "Unhide selected…", "Uninstall"),
            linhas.map { it.texto },
        )
    }

    @Test
    fun `menu de edicao - marca no abrir como janela`() {
        val linhas = RegrasDoMenuDeEdicao.linhas(true, true, false, false)
        assertEquals(listOf("Customize…", "Hide", "✓ Open as window"), linhas.map { it.texto })
    }

    @Test
    fun `menu de edicao - hub e app de sistema so tem as basicas`() {
        val linhas = RegrasDoMenuDeEdicao.linhas(false, false, false, false)
        assertEquals(listOf("Customize…", "Hide"), linhas.map { it.texto })
    }

    @Test
    fun `menu de edicao - reexibir so com ocultos`() {
        assertFalse(RegrasDoMenuDeEdicao.linhas(false, false, false, false).contains(LinhaDoMenuDeEdicao.REEXIBIR))
        assertTrue(RegrasDoMenuDeEdicao.linhas(false, false, true, false).contains(LinhaDoMenuDeEdicao.REEXIBIR))
    }
}
