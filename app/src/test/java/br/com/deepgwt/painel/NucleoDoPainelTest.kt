// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import android.webkit.WebView
import android.widget.FrameLayout
import br.com.deepgwt.ponte.AgendadorFalso
import br.com.deepgwt.ponte.Cancelavel
import br.com.deepgwt.ponte.CarroFalso
import br.com.deepgwt.ponte.FontesFalsas
import br.com.deepgwt.ponte.LeitorDeScript
import br.com.deepgwt.ponte.PonteDosTemas
import br.com.deepgwt.ponte.PreferenciasEmMemoria
import br.com.deepgwt.ponte.ProjecaoFalsa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Os casos do núcleo do projetor pedidos em `projetores-painel.md`, 9.1 (segundo parágrafo). */
class NucleoDoPainelTest {

    // ── Falsos ──

    private class TempoFalso : TempoDoPainel {
        var agora = 1_000L
        private class Item(var quando: Long, val intervalo: Long?, val bloco: () -> Unit)
        private val agenda = mutableListOf<Item>()

        override fun agora() = agora
        override fun naPrincipal(bloco: () -> Unit) = bloco()
        override fun depois(atrasoMs: Long, bloco: () -> Unit): Cancelavel {
            val item = Item(agora + atrasoMs, null, bloco)
            agenda.add(item)
            return Cancelavel { agenda.remove(item) }
        }

        override fun repetir(intervaloMs: Long, bloco: () -> Unit): Cancelavel {
            val item = Item(agora + intervaloMs, intervaloMs, bloco)
            agenda.add(item)
            return Cancelavel { agenda.remove(item) }
        }

        override fun emFundo(bloco: () -> Unit) = bloco()
        override fun emDisco(bloco: () -> Unit) = bloco()

        fun avancar(ms: Long) {
            val alvo = agora + ms
            while (true) {
                val proximo = agenda.filter { it.quando <= alvo }.minByOrNull { it.quando } ?: break
                agora = proximo.quando
                if (proximo.intervalo != null) proximo.quando += proximo.intervalo else agenda.remove(proximo)
                proximo.bloco()
            }
            agora = alvo
        }
    }

    private class JanelaFalsa(val eventos: MutableList<String>) : JanelaDoPainel {
        var cargas = 0
        val alfas = mutableListOf<Float>()
        var raiz = false
        var web = false
        var viva = false
        var recriacoes = 0

        override fun criarConteudo(objetoJs: Any) {
            viva = true
        }

        override fun montarCamadas(cartao: Int, avisoNoCirculo: Boolean) {
            eventos.add("montar")
        }

        override fun carregar(base: String, html: String) {
            cargas++
            eventos.add("carregar $base")
        }

        override fun executar(js: String, resposta: ((String?) -> Unit)?): Boolean {
            if (!viva) return false
            eventos.add("js:$js")
            resposta?.invoke(if (js.contains("onCardChanged")) "\"contrato\"" else null)
            return true
        }

        override fun alfaDoWebView(alfa: Float) {
            alfas.add(alfa)
        }

        override fun raizVisivel(visivel: Boolean) {
            raiz = visivel
        }

        override val raizEstaVisivel: Boolean get() = raiz

        override fun tornarWebViewVisivel() {
            web = true
        }

        override val webViewEstaVisivel: Boolean get() = web

        override fun quandoOQuadroEstiverPronto(pronto: () -> Unit) = pronto()
        override fun comporMascaras(plano: PlanoDasMascaras): Any? = null
        override fun aplicarMascaras(composto: Any?) = Unit
        override fun esquecerMascaraGlobal() = Unit
        override fun avisoDoMaosLivres(visivel: Boolean) = Unit
        override fun recriarWebView() {
            recriacoes++
        }

        override fun desmontarCamadas() {
            eventos.add("desmontar")
        }

        override fun destruir() {
            viva = false
        }
    }

    private class CamadasFalsas(val eventos: MutableList<String>) : CamadasDoPainel {
        override fun montar(raiz: FrameLayout, webView: () -> WebView?, naPrincipal: (() -> Unit) -> Unit, cartao: Int, avisoNoCirculo: Boolean) = Unit
        override fun desmontar(raiz: FrameLayout) = Unit
        override fun cartaoMudou(cartao: Int) {
            eventos.add("camadas.cartao $cartao")
        }

        override fun avisoNoCirculoMudou(ocupa: Boolean) {
            eventos.add("camadas.circulo $ocupa")
        }

        override fun avisoNovoDoCarro() {
            eventos.add("camadas.avisoNovo")
        }

        override fun paginaRecarregou() {
            eventos.add("camadas.paginaRecarregou")
        }
    }

    private class AppsFalsos : AppsNoPainel by AppsNoPainel.NENHUM {
        var carPlay = false
        override fun carPlayNoDisplay3() = carPlay
    }

    private class CarroDoTeste : CarroDoPainel {
        val valores = HashMap<String, String>()
        var ouvinte: ((String, String?) -> Unit)? = null
        override fun ler(chave: String): String? = valores[chave]
        override fun observar(ouvinte: (String, String?) -> Unit): Cancelavel {
            this.ouvinte = ouvinte
            return Cancelavel { this.ouvinte = null }
        }
    }

    private class PaginasFalsas : PaginasDoPainel {
        var tema: TemaInstalado? = null
        override fun escolher(pastaAtiva: String): PaginaDoPainel =
            if (pastaAtiva.isNotEmpty() && tema != null) PaginaDoPainel(tema, "file:///themes/$pastaAtiva/", "1.0.0", "app.html")
            else PaginaDoPainel(null, "file:///android_asset/PainelOriginal/", null, "PainelOriginal/index.html")

        override fun ler(pagina: PaginaDoPainel) = pagina to "<html></html>"
    }

    private class RegistroDoTeste : RegistroDoPainel {
        override var diarioLigado = true
        val diario = mutableListOf<Pair<String, Map<String, Any?>>>()
        override fun diario(evento: String, detalhes: Map<String, Any?>) {
            diario.add(evento to detalhes)
        }

        override fun info(mensagem: String) = Unit
        override fun erro(mensagem: String, causa: Throwable?) = Unit
    }

    private class TemasVazios : PapelDeParede.Temas {
        override fun fundoDoTemaAtivo(dica: String?): String? = null
        override fun arquivoNoTema(pasta: String, nome: String): String? = null
        override fun arquivoExiste(caminho: String) = false
    }

    // ── Cenário ──

    private val eventos = mutableListOf<String>()
    private val tempo = TempoFalso()
    private val janela = JanelaFalsa(eventos)
    private val prefs = PreferenciasEmMemoria().apply {
        mapa["enableInstrumentProjector"] = true
        mapa["enableInstrumentCustomMediaIntegration"] = true
    }
    private val apps = AppsFalsos()
    private val carro = CarroDoTeste()
    private val paginas = PaginasFalsas()
    private val registro = RegistroDoTeste()
    private var cartaoDoMotor = 1

    private fun nucleo(telaLigada: Boolean = true): NucleoDoPainel = NucleoDoPainel(
        ligacoes = LigacoesDoPainel(camadas = CamadasFalsas(eventos), apps = apps, cartaoAtual = { cartaoDoMotor }),
        janela = janela,
        tempo = tempo,
        preferencias = prefs,
        carro = carro,
        paginas = paginas,
        temas = TemasVazios(),
        registro = registro,
        fontes = FontesFalsas(),
        criarPonte = { painel, fontes ->
            PonteDosTemas(painel, fontes, ProjecaoFalsa(), prefs, CarroFalso(), AgendadorFalso(), br.com.deepgwt.ponte.RegistroFalso(), { "" }, { 12 }, { false })
        },
        telaPrincipalLigada = telaLigada,
        depuravel = false,
        primeiraCargaDoProcesso = { false },
        horaLocal = { "12:34" },
    )

    private fun carregar(n: NucleoDoPainel) {
        n.paginaComecou()
        n.paginaTerminou()
    }

    private fun scripts(): List<String> = eventos.filter { it.startsWith("js:") }.map { it.removePrefix("js:") }

    private fun controles(js: String): List<String> = LeitorDeScript.controls(js).map { it.first }

    // ── Testes ──

    @Test
    fun fimDaCargaNaOrdem() {
        val n = nucleo()
        n.iniciar()
        assertEquals(1, janela.cargas)
        val inicio = eventos.size
        carregar(n)
        val depois = eventos.subList(inicio, eventos.size).toList()
        fun indice(desde: Int = 0, teste: (String) -> Boolean): Int {
            for (i in desde until depois.size) if (teste(depois[i])) return i
            return -1
        }
        val compat = indice { it.contains("typeof control!=='function'") }
        val batida = indice { it.contains("__havalHeartbeatTimer=setInterval") }
        val escala = indice { it.contains("--gauge-num-scale") }
        val camadas = indice { it == "camadas.paginaRecarregou" }
        val lote = indice { it.startsWith("js:") && controles(it).size == 54 }
        val cartao = indice { it.contains("window.onCardChanged(1)") }
        val selo = indice(cartao) { it.startsWith("js:") && controles(it) == listOf("warningActive", "warningDismissed") }
        val visibilidade = indice(cartao) {
            it.startsWith("js:") && controles(it) == listOf("clusterEnabled", "originalInfo", "clusterBackground", "appInDash")
        }
        val projecao = indice(visibilidade) {
            it.startsWith("js:") && controles(it) == listOf("carPlayInDash", "projectionMirrorInDash", "projectionPreparingD3", "projectionCardOverlayAllowed")
        }
        val relogio = indice(projecao) { it.startsWith("js:") && controles(it) == listOf("clockTime") }
        val ordem = listOf(compat, batida, escala, camadas, lote, cartao, selo, visibilidade, projecao, relogio)
        assertTrue("todos achados: $ordem\n${depois.joinToString("\n")}", ordem.all { it >= 0 })
        assertEquals("ordem C.5: $ordem", ordem.sorted(), ordem)
        assertTrue(janela.raiz)
        assertTrue(janela.web)
    }

    @Test
    fun veuSoComPainelOriginal() {
        prefs.mapa["enableVirtualCluster"] = false
        val n = nucleo()
        n.iniciar()
        assertEquals("o pedido de carga cobre", 0f, janela.alfas.last())
        carregar(n)
        assertEquals("sob o véu fica 0", 0f, janela.alfas.last())
        tempo.avancar(699)
        assertEquals(0f, janela.alfas.last())
        tempo.avancar(1)
        assertEquals("700 ms depois do quadro pronto", 1f, janela.alfas.last())
    }

    @Test
    fun veuTemTetoDeCincoSegundos() {
        prefs.mapa["enableVirtualCluster"] = false
        val n = nucleo()
        n.iniciar()
        assertEquals(0f, janela.alfas.last())
        tempo.avancar(4_999)
        assertEquals(0f, janela.alfas.last())
        tempo.avancar(1)
        assertEquals(1f, janela.alfas.last())
    }

    @Test
    fun semVeuComTemaCustomizado() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        assertFalse("nunca alfa 0: ${janela.alfas}", janela.alfas.contains(0f))
    }

    @Test
    fun trocaDeTemaComAsDuasPreferenciasEhUmaCarga() {
        paginas.tema = CatalogoDeTemas.EMBUTIDO.copy(pasta = "coffe3", nome = "Stuttgart")
        val n = nucleo()
        n.iniciar()
        carregar(n)
        assertEquals(1, janela.cargas)
        // Como o SharedPreferences de verdade: grava as duas na memória e depois avisa uma vez cada.
        prefs.mapa["activeCustomTheme"] = "coffe3"
        prefs.mapa["themeReloadNonce"] = 5L
        prefs.gravar("activeCustomTheme", "coffe3")
        prefs.gravar("themeReloadNonce", 5L)
        assertEquals(2, janela.cargas)
        assertEquals("coffe3", n.paginaEmUso?.pasta)
        // Só o carimbo novo: recarrega de novo.
        prefs.gravar("themeReloadNonce", 6L)
        assertEquals(3, janela.cargas)
        // Repetido: nada.
        prefs.gravar("themeReloadNonce", 6L)
        assertEquals(3, janela.cargas)
    }

    @Test
    fun vigiaRecarregaSoVisivelESemBatida() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        tempo.avancar(14_000)
        n.batimento()
        tempo.avancar(14_000)
        assertEquals("com batida não recarrega", 1, janela.cargas)
        tempo.avancar(6_000)
        assertEquals("15 s sem batida com o painel visível", 2, janela.cargas)
        assertTrue(registro.diario.any { it.first == "webview_watchdog_reload" })
    }

    @Test
    fun vigiaNaoRecarregaEscondido() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        n.telaPrincipal(false)
        assertFalse(janela.raiz)
        tempo.avancar(60_000)
        assertEquals(1, janela.cargas)
        // Ao voltar, o tempo escondido não conta.
        n.telaPrincipal(true)
        assertTrue(janela.raiz)
        tempo.avancar(14_000)
        assertEquals(1, janela.cargas)
    }

    @Test
    fun cartaoComProjecaoMandaOEstadoAntes() {
        apps.carPlay = true
        val n = nucleo()
        n.iniciar()
        carregar(n)
        val inicio = eventos.size
        n.cartaoMudou(3)
        val depois = eventos.subList(inicio, eventos.size)
        val projecao = depois.indexOfFirst { it.startsWith("js:") && controles(it).contains("carPlayInDash") }
        val cartao = depois.indexOfFirst { it.contains("window.onCardChanged(3)") }
        assertTrue("projeção $projecao antes do cartão $cartao", projecao in 0 until cartao)
        val js = depois[projecao]
        assertTrue(js.contains("control(\"carPlayInDash\",true)"))
        // Entrada do cartão 3 (ar).
        assertTrue(depois.any { it.startsWith("js:") && controles(it).firstOrNull() == "fan" })
        assertTrue(registro.diario.any { it.first == "cluster_card_change" && it.second["projectionActive"] == true })
    }

    @Test
    fun cartaoSemProjecaoVaiPeloCaminhoRapido() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        n.cartaoMudou(0)
        val evento = registro.diario.last { it.first == "cluster_card_change" }.second
        assertEquals(true, evento["fastPath"])
        assertEquals(1, evento["from"])
        assertEquals(0, evento["to"])
        assertEquals("hidden", evento["screen"])
        assertTrue(eventos.contains("camadas.cartao 0"))
    }

    @Test
    fun telemetriaDeduplicaEDisparaOSelo() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        val ouvinte = carro.ouvinte!!
        var antes = scripts().size
        ouvinte("car.basic.gear_status", "2")
        assertEquals(listOf("gearState"), controles(scripts().last()))
        assertTrue(scripts().last().contains("\"D\""))
        ouvinte("car.basic.gear_status", "2")
        assertEquals(antes + 1, scripts().size)
        antes = scripts().size
        ouvinte("car.basic.seat_belt_warning", "{1,0,0,0,0}")
        assertTrue(n.avisoAtivo)
        assertTrue(scripts().drop(antes).any { it.contains("control(\"warningActive\",true)") })
        assertTrue(eventos.contains("camadas.avisoNovo"))
        assertTrue("parado, o pop-up ocupa o círculo", eventos.contains("camadas.circulo true"))
        tempo.avancar(8_020)
        assertTrue("passado o pop-up, parado, o círculo volta", eventos.contains("camadas.circulo false"))
    }

    @Test
    fun rastroDaPaginaOriginal() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        n.ponte.objetoJs.subscribe("[\"car.basic.outside_temp\"]")
        val ouvinte = carro.ouvinte!!
        ouvinte("car.basic.outside_temp", "20.5")
        ouvinte("car.basic.outside_temp", "20.6")
        tempo.avancar(1_000)
        ouvinte("car.basic.outside_temp", "20.7")
        ouvinte("car.basic.inside_temp", "22.0")
        val rastros = registro.diario.filter { it.first == "webview_console" }.map { it.second["message"] }
        assertEquals(
            listOf("[onDataChanged TRACE] key=car.basic.outside_temp value=20.5", "[onDataChanged TRACE] key=car.basic.outside_temp value=20.7"),
            rastros,
        )
    }

    @Test
    fun encerrarDesmontaPrimeiroEParaDeOuvir() {
        val n = nucleo()
        n.iniciar()
        carregar(n)
        n.encerrar()
        assertEquals("desmontar", eventos.last { !it.startsWith("js:") })
        assertEquals(null, carro.ouvinte)
        assertEquals(0, prefs.quantosOuvintes)
        assertFalse(janela.viva)
        val antes = janela.cargas
        tempo.avancar(60_000)
        assertEquals(antes, janela.cargas)
    }

    @Test
    fun telaPrincipalLigada() {
        for (v in listOf("11", "12", "13", "0", "1", "")) assertTrue("v=$v", NucleoDoPainel.telaPrincipalLigada(v))
        for (v in listOf(null, "-1", "15", "14", "10")) assertFalse("v=$v", NucleoDoPainel.telaPrincipalLigada(v))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
