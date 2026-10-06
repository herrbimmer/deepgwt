package br.com.deepgwt.tela3d.integracao

import br.com.deepgwt.tela3d.faixa.ModoDaCasca
import br.com.deepgwt.tela3d.faixa.Proporcao
import br.com.deepgwt.tela3d.faixa.ReservasDaFaixa
import br.com.deepgwt.tela3d.faixa.calcularReservas
import br.com.deepgwt.tela3d.janelas.EstadoDasJanelas
import br.com.deepgwt.tela3d.janelas.ModoDaInterface
import br.com.deepgwt.tela3d.janelas.ModoDeTela
import br.com.deepgwt.tela3d.janelas.ProporcaoDaDivisao
import br.com.deepgwt.tela3d.janelas.UsoDoLado
import br.com.deepgwt.tela3d.ponte.IndicadoresDoDock
import br.com.deepgwt.tela3d.ponte.ModoDeOcupacao
import br.com.deepgwt.tela3d.ponte.RetanguloEmPx
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import br.com.deepgwt.tela3d.catalogo.TipoDeProjecao as ProjecaoDoCatalogo
import br.com.deepgwt.tela3d.faixa.TipoDeProjecao as ProjecaoDaFaixa
import br.com.deepgwt.tela3d.janelas.TipoDeProjecao as ProjecaoDasJanelas
import br.com.deepgwt.tela3d.midia.TipoDeProjecao as ProjecaoDaMidia

class ConversoesTest {

    // --- projeção ---

    @Test
    fun `projecao da midia vira a das janelas e a da faixa sem perder nenhum caso`() {
        assertEquals(ProjecaoDasJanelas.NENHUM, Conversoes.projecaoDaMidiaParaAsJanelas(ProjecaoDaMidia.NENHUMA))
        assertEquals(ProjecaoDasJanelas.ANDROID_AUTO, Conversoes.projecaoDaMidiaParaAsJanelas(ProjecaoDaMidia.ANDROID_AUTO))
        assertEquals(ProjecaoDasJanelas.CARPLAY, Conversoes.projecaoDaMidiaParaAsJanelas(ProjecaoDaMidia.CARPLAY))
        assertEquals(ProjecaoDaFaixa.NENHUMA, Conversoes.projecaoDaMidiaParaAFaixa(ProjecaoDaMidia.NENHUMA))
        assertEquals(ProjecaoDaFaixa.ANDROID_AUTO, Conversoes.projecaoDaMidiaParaAFaixa(ProjecaoDaMidia.ANDROID_AUTO))
        assertEquals(ProjecaoDaFaixa.CARPLAY, Conversoes.projecaoDaMidiaParaAFaixa(ProjecaoDaMidia.CARPLAY))
    }

    @Test
    fun `projecao de ida e volta entre janelas e faixa`() {
        for (t in ProjecaoDasJanelas.entries) {
            assertEquals(t, Conversoes.projecaoDaFaixaParaAsJanelas(Conversoes.projecaoDasJanelasParaAFaixa(t)))
        }
        for (t in ProjecaoDaFaixa.entries) {
            assertEquals(t, Conversoes.projecaoDasJanelasParaAFaixa(Conversoes.projecaoDaFaixaParaAsJanelas(t)))
        }
    }

    @Test
    fun `projecao do catalogo para a midia`() {
        assertEquals(ProjecaoDaMidia.ANDROID_AUTO, Conversoes.projecaoDoCatalogoParaAMidia(ProjecaoDoCatalogo.ANDROID_AUTO))
        assertEquals(ProjecaoDaMidia.CARPLAY, Conversoes.projecaoDoCatalogoParaAMidia(ProjecaoDoCatalogo.CARPLAY))
    }

    // --- modo e proporção ---

    @Test
    fun `modo e proporcao fazem ida e volta`() {
        for (m in ModoDeTela.entries) {
            assertEquals(m, Conversoes.modoDaFaixaParaAsJanelas(Conversoes.modoDasJanelasParaAFaixa(m)))
        }
        for (p in ProporcaoDaDivisao.entries) {
            assertEquals(p, Conversoes.proporcaoDaFaixaParaAsJanelas(Conversoes.proporcaoDasJanelasParaAFaixa(p)))
        }
        // os textos que a página lê são os mesmos nos dois módulos
        for (m in ModoDeTela.entries) assertEquals(m.texto, Conversoes.modoDasJanelasParaAFaixa(m).valor)
        for (p in ProporcaoDaDivisao.entries) assertEquals(p.texto, Conversoes.proporcaoDasJanelasParaAFaixa(p).valor)
        assertEquals(ModoDaCasca.LADO_A_LADO, Conversoes.modoDasJanelasParaAFaixa(ModoDeTela.DOIS_APPS))
        assertEquals(Proporcao.DOIS_UM, Conversoes.proporcaoDasJanelasParaAFaixa(ProporcaoDaDivisao.DOIS_PARA_UM))
    }

    // --- estado do shell ---

    private fun estadoDasJanelas(
        pacoteEsquerda: String = "",
        pacoteDireita: String = "",
        proximoEhEsquerda: Boolean = true,
        ultimoLado: String = "",
    ) = EstadoDasJanelas(
        modo = ModoDeTela.DOIS_APPS,
        proporcao = ProporcaoDaDivisao.DOIS_PARA_UM,
        ladoPreferido = "auto",
        proximoEhEsquerda = proximoEhEsquerda,
        ultimoLado = ultimoLado,
        pacoteEsquerda = pacoteEsquerda,
        pacoteDireita = pacoteDireita,
        usoEsquerda = UsoDoLado.APP,
        usoDireita = UsoDoLado.WIDGETS,
        modoDaInterface = ModoDaInterface.FLUTUANTE,
        chromePorCima = false,
    )

    @Test
    fun `estado do shell traz os textos que a pagina le e os retangulos dos dois lados`() {
        val e = Conversoes.estadoDoShell(
            estadoDasJanelas(pacoteEsquerda = "com.a", pacoteDireita = "", proximoEhEsquerda = false, ultimoLado = "left"),
            RetanguloEmPx(10, 20, 30, 40), RetanguloEmPx(50, 60, 70, 80),
        )
        assertEquals("appsOnly", e.modo)
        assertEquals("2:1", e.proporcao)
        assertEquals("auto", e.ladoPreferido)
        assertEquals("right", e.proximoLado)
        assertEquals("left", e.ultimoLado)
        assertEquals("app", e.usoEsquerdo)
        assertEquals("widgets", e.usoDireito)
        assertTrue(e.esquerdoOcupado)
        assertFalse(e.direitoOcupado)
        assertEquals(RetanguloEmPx(10, 20, 30, 40), e.limitesEsquerdo)
        assertEquals(RetanguloEmPx(50, 60, 70, 80), e.limitesDireito)
    }

    @Test
    fun `proximo lado e esquerda quando o estado diz que sim`() {
        val e = Conversoes.estadoDoShell(estadoDasJanelas(), RetanguloEmPx.VAZIO, RetanguloEmPx.VAZIO)
        assertEquals("left", e.proximoLado)
        assertFalse(e.esquerdoOcupado)
    }

    // --- margens e medidas ---

    private val leituraDoCarro = Conversoes.LeituraDaTela(
        densidade = 1f, larguraRealPx = 1920, alturaRealPx = 720,
        raizEsquerda = 0, raizTopo = 0, raizLargura = 1920, raizAltura = 660,
        larguraDoTrilhoDimenPx = 0, maiorInsetEsquerdoPx = 0, maiorInsetSuperiorPx = 0,
        alturaBarraStatusPx = 0, ocupacao = ModoDeOcupacao.FLUTUANTE,
    )

    @Test
    fun `medidas com a raiz medida usam o retangulo dela e a barra de status some so no modo full`() {
        val m = Conversoes.medidasDaTela(leituraDoCarro, ModoDaCasca.APP_E_CARRO)
        assertEquals(0, m.paginaEsq)
        assertEquals(1920, m.paginaDir)
        assertEquals(660, m.paginaBase)
        assertTrue(m.barraStatusVisivel)
        val cheia = Conversoes.medidasDaTela(
            Conversoes.LeituraDaTela(1f, 1920, 720, 0, 0, 1920, 720, 0, 0, 0, 0, ModoDeOcupacao.TELA_CHEIA),
            ModoDaCasca.APP_E_CARRO,
        )
        assertFalse(cheia.barraStatusVisivel)
    }

    @Test
    fun `raiz sem tamanho vale a tela inteira e densidade invalida vale um`() {
        val l = Conversoes.LeituraDaTela(0f, 1920, 720, 5, 7, 0, 0, 0, 0, 0, 0, ModoDeOcupacao.FLUTUANTE)
        val m = Conversoes.medidasDaTela(l, ModoDaCasca.LADO_A_LADO)
        assertEquals(0, m.paginaEsq)
        assertEquals(0, m.paginaTopo)
        assertEquals(1920, m.paginaDir)
        assertEquals(720, m.paginaBase)
        assertEquals(1f, m.densidade, 0f)
        assertEquals(ModoDaCasca.LADO_A_LADO, m.modo)
    }

    @Test
    fun `as margens tiram os 22 de folga que a ponte soma de novo`() {
        val medidas = Conversoes.medidasDaTela(leituraDoCarro, ModoDaCasca.APP_E_CARRO)
        val r: ReservasDaFaixa = calcularReservas(medidas)
        val margens = Conversoes.margensDaPagina(r, medidas, topoDaFaixaNaTelaPx = null)
        assertEquals(r.safeTopCss - 22f, margens.topoCss, 0.001f)
        assertEquals(r.safeBottomCss, margens.reservaInferiorCss, 0.001f)
        assertEquals(r.launcherBottomCss, margens.folgaInferiorCss, 0.001f)
        assertEquals(r.safeLeftCss, margens.esquerdaCss, 0.001f)
        assertEquals(r.safeRightCss, margens.direitaCss, 0.001f)
        assertEquals(medidas.paginaEsq, margens.origemDaPaginaXEmPx)
        assertEquals(medidas.paginaTopo, margens.origemDaPaginaYEmPx)
        // sem faixa medida o fundo dos popups é a altura da página
        assertEquals(660f, margens.popupBaseCss, 0.001f)
    }

    @Test
    fun `no carro a pagina de 1920 por 660 reserva 224 abaixo no appCar e nada no appsOnly`() {
        val appCar = Conversoes.medidasDaTela(leituraDoCarro, ModoDaCasca.APP_E_CARRO)
        val lado = Conversoes.medidasDaTela(leituraDoCarro, ModoDaCasca.LADO_A_LADO)
        // 148 + 60 + 16 = 224; o overscan inferior (720 - 660 = 60) é descontado
        assertEquals(164f, calcularReservas(appCar).safeBottomCss, 0.001f)
        assertEquals(0f, calcularReservas(lado).safeBottomCss, 0.001f)
    }

    // --- JSON para a faixa ---

    private fun indicadores(
        modoDoDock: String? = null,
        temaClaro: Boolean? = null,
        cor: Int? = null,
        tela: String? = null,
        estudio: Boolean? = null,
        edicao: Boolean? = null,
    ) = IndicadoresDoDock(
        modoDoDock = modoDoDock, nomeDaAreaAtiva = null, quantidadeDeAreas = null, indiceDaAreaAtiva = null,
        estudioAberto = estudio, telaDoEstudio = tela, corDeDestaque = cor, cartoes = null,
        editandoTrilho = edicao, focoDoTrilho = null, resumoDoClima = null, resumoDoConsumo = null,
        modelo = null, desempenho = null, tinta = null, tempo = null, fps = null, raioX = null,
        modoDoTemaDosWidgets = null, temaDosWidgets = null, temaClaro = temaClaro,
    )

    @Test
    fun `json da faixa so leva o que foi informado`() {
        assertEquals("{}", Conversoes.jsonDeIndicadoresParaAFaixa(indicadores()))
        val j = JSONObject(
            Conversoes.jsonDeIndicadoresParaAFaixa(
                indicadores(modoDoDock = "cards", temaClaro = true, cor = 0xFF4FD6E8.toInt(), tela = "layout", estudio = true, edicao = false),
            ),
        )
        assertEquals("cards", j.getString("dockMode"))
        assertEquals("light", j.getString("uiTheme"))
        assertEquals("#4FD6E8", j.getString("accent"))
        assertEquals("layout", j.getString("studioScreen"))
        assertTrue(j.getBoolean("desktopStudioOpen"))
        assertFalse(j.getBoolean("railEdit"))
        assertEquals("dark", JSONObject(Conversoes.jsonDeIndicadoresParaAFaixa(indicadores(temaClaro = false))).getString("uiTheme"))
    }

    @Test
    fun `json da faixa leva o estilo do dock`() {
        val ind = indicadores().copy(estiloDoDock = "lion")
        assertEquals("lion", JSONObject(Conversoes.jsonDeIndicadoresParaAFaixa(ind)).getString("dockStyle"))
        assertFalse(JSONObject(Conversoes.jsonDeIndicadoresParaAFaixa(indicadores())).has("dockStyle"))
    }

    @Test
    fun `cor de destaque com zeros a esquerda mantem os seis digitos`() {
        val j = JSONObject(Conversoes.jsonDeIndicadoresParaAFaixa(indicadores(cor = 0xFF000A0B.toInt())))
        assertEquals("#000A0B", j.getString("accent"))
    }

    @Test
    fun `json do preenchimento do centro`() {
        assertEquals("{}", Conversoes.jsonDePreenchimentoParaAFaixa(null, null))
        val j = JSONObject(Conversoes.jsonDePreenchimentoParaAFaixa("mixed", false))
        assertEquals("mixed", j.getString("mode"))
        assertFalse(j.getBoolean("visible"))
    }

    // --- toque ---

    @Test
    fun `toque no topo reservado vira altura zero e fora dele altura enorme`() {
        assertEquals(0f, Conversoes.alturaDoToque(true), 0f)
        assertTrue(Conversoes.alturaDoToque(false) > 100_000f)
    }
}
