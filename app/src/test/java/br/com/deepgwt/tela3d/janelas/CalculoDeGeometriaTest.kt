package br.com.deepgwt.tela3d.janelas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V1 da especificação: geometria dos lados e margens da página. */
class CalculoDeGeometriaTest {

    private fun entradas(
        densidade: Float = 1f,
        barra: Int = 24,
        trilho: Int = 0,
        insetEsquerdo: Int = 0,
        alturaReal: Int = 720,
        raiz: Retangulo? = Retangulo(0, 0, 1920, 720),
        interfaceModo: ModoDaInterface = ModoDaInterface.FLUTUANTE,
        faixaNaTela: Int? = null,
        temInsets: Boolean = true,
        maiorSuperior: Int = 0,
    ) = EntradasDeGeometria(
        larguraReal = 1920, alturaReal = alturaReal, larguraDasMetricas = 1920, alturaDasMetricas = alturaReal,
        densidade = densidade, alturaDaBarraDeStatus = barra, larguraDoTrilhoDoSistema = trilho,
        maiorInsetEsquerdo = insetEsquerdo, maiorInsetSuperior = maiorSuperior, insetDireito = 0, insetInferior = 0,
        temInsets = temInsets, modoDaInterface = interfaceModo, raizNaTela = raiz, topoDaFaixaNaTela = faixaNaTela,
    )

    private fun lado(e: EntradasDeGeometria, l: Lado, m: ModoDeTela, p: ProporcaoDaDivisao = ProporcaoDaDivisao.UM_PARA_UM) =
        CalculoDeGeometria.limitesDoLado(e, l, m, p)

    @Test fun appCarEsquerdoEDireito() {
        val e = entradas()
        assertEquals(Retangulo(128, 52, 1037, 496), lado(e, Lado.ESQUERDA, ModoDeTela.APP_E_CARRO))
        assertEquals(Retangulo(1180, 100, 1792, 496), lado(e, Lado.DIREITA, ModoDeTela.APP_E_CARRO))
    }

    @Test fun doisAppsNasTresProporcoes() {
        val e = entradas()
        val m = ModoDeTela.DOIS_APPS
        assertEquals(Retangulo(128, 52, 956, 720), lado(e, Lado.ESQUERDA, m, ProporcaoDaDivisao.UM_PARA_UM))
        assertEquals(Retangulo(964, 52, 1792, 720), lado(e, Lado.DIREITA, m, ProporcaoDaDivisao.UM_PARA_UM))
        assertEquals(Retangulo(128, 52, 1276, 720), lado(e, Lado.ESQUERDA, m, ProporcaoDaDivisao.DOIS_PARA_UM))
        assertEquals(Retangulo(1284, 52, 1792, 720), lado(e, Lado.DIREITA, m, ProporcaoDaDivisao.DOIS_PARA_UM))
        assertEquals(Retangulo(128, 52, 636, 720), lado(e, Lado.ESQUERDA, m, ProporcaoDaDivisao.UM_PARA_DOIS))
        assertEquals(Retangulo(644, 52, 1792, 720), lado(e, Lado.DIREITA, m, ProporcaoDaDivisao.UM_PARA_DOIS))
    }

    @Test fun comMaiorInsetEsquerdoDe128() {
        val e = entradas(insetEsquerdo = 128)
        assertEquals(Retangulo(128, 0, 1920, 720), CalculoDeGeometria.areaUtil(e).let { Retangulo(it.esquerda, it.topo, it.direita, it.fundo) })
        assertEquals(Retangulo(157, 52, 1096, 496), lado(e, Lado.ESQUERDA, ModoDeTela.APP_E_CARRO))
        assertEquals(Retangulo(1229, 100, 1792, 496), lado(e, Lado.DIREITA, ModoDeTela.APP_E_CARRO))
        val m = ModoDeTela.DOIS_APPS
        assertEquals(Retangulo(135, 52, 1021, 720), lado(e, Lado.ESQUERDA, m, ProporcaoDaDivisao.UM_PARA_UM))
        assertEquals(Retangulo(1027, 52, 1792, 720), lado(e, Lado.DIREITA, m, ProporcaoDaDivisao.UM_PARA_UM))
        assertEquals(Retangulo(135, 52, 1320, 720), lado(e, Lado.ESQUERDA, m, ProporcaoDaDivisao.DOIS_PARA_UM))
        assertEquals(Retangulo(1326, 52, 1792, 720), lado(e, Lado.DIREITA, m, ProporcaoDaDivisao.DOIS_PARA_UM))
        assertEquals(Retangulo(135, 52, 722, 720), lado(e, Lado.ESQUERDA, m, ProporcaoDaDivisao.UM_PARA_DOIS))
        assertEquals(Retangulo(728, 52, 1792, 720), lado(e, Lado.DIREITA, m, ProporcaoDaDivisao.UM_PARA_DOIS))
    }

    @Test fun densidade125ComBarraDe30() {
        val e = entradas(densidade = 1.25f, barra = 30)
        assertEquals(Retangulo(160, 65, 1037, 440), lado(e, Lado.ESQUERDA, ModoDeTela.APP_E_CARRO))
        assertEquals(Retangulo(1180, 100, 1760, 440), lado(e, Lado.DIREITA, ModoDeTela.APP_E_CARRO))
        assertEquals(Retangulo(160, 65, 956, 720), lado(e, Lado.ESQUERDA, ModoDeTela.DOIS_APPS))
        assertEquals(Retangulo(964, 65, 1760, 720), lado(e, Lado.DIREITA, ModoDeTela.DOIS_APPS))
        assertEquals(280, CalculoDeGeometria.reservaDoDock(e, ModoDeTela.APP_E_CARRO))
        assertEquals(160, CalculoDeGeometria.reservaDoTrilho(e))
    }

    @Test fun alturaMinimaDe200EmDisplayBaixo() {
        val e = entradas(alturaReal = 400, raiz = Retangulo(0, 0, 1920, 400))
        assertEquals(Retangulo(128, 52, 1037, 252), lado(e, Lado.ESQUERDA, ModoDeTela.APP_E_CARRO))
        assertEquals(Retangulo(1180, 100, 1792, 300), lado(e, Lado.DIREITA, ModoDeTela.APP_E_CARRO))
    }

    @Test fun reservaDoDockEZeroEmDoisApps() {
        assertEquals(0, CalculoDeGeometria.reservaDoDock(entradas(), ModoDeTela.DOIS_APPS))
        assertEquals(224, CalculoDeGeometria.reservaDoDock(entradas(), ModoDeTela.APP_E_CARRO))
    }

    @Test fun areaUtilCaiParaODisplayQuandoMuitoPequena() {
        val e = EntradasDeGeometria(
            1920, 720, 1920, 720, 1f, 24, 0, 1700, 0, 0, 0, true, ModoDaInterface.FLUTUANTE, null, null,
        )
        val a = CalculoDeGeometria.areaUtil(e)
        assertEquals(Retangulo(0, 0, 1920, 720), a)
    }

    @Test fun semInsetsAAreaEODisplayInteiro() {
        val e = entradas(temInsets = false, insetEsquerdo = 500)
        assertEquals(Retangulo(0, 0, 1920, 720), CalculoDeGeometria.areaUtil(e))
    }

    @Test fun margensComOrigemNoDisplayInteiro() {
        val e = entradas()
        val m = CalculoDeGeometria.margens(e, ModoDeTela.APP_E_CARRO)
        assertEquals(128, m.lateralEsquerda)
        assertEquals(128, m.lateralDireita)
        assertEquals(24, m.superior)
        assertEquals(224, m.reservaDoDockCompensada)
        assertEquals(80, m.folgaDoChromeCompensada)
        assertEquals(24, m.topoDosPopups)
        assertEquals(720, m.fundoDosPopups)
    }

    @Test fun margemInferiorCompensadaQuandoAOrigemTermina60PxAntes() {
        val e = entradas(raiz = Retangulo(0, 0, 1920, 660))
        assertEquals(60, CalculoDeGeometria.recuoInferior(e))
        val m = CalculoDeGeometria.margens(e, ModoDeTela.APP_E_CARRO)
        assertEquals(164, m.reservaDoDockCompensada)
        assertEquals(20, m.folgaDoChromeCompensada)
    }

    @Test fun margemSuperiorZeraSemBarraDeStatusEmTelaCheia() {
        val e = entradas(interfaceModo = ModoDaInterface.TELA_CHEIA)
        assertEquals(0, CalculoDeGeometria.margemSuperior(e))
        assertEquals(24, CalculoDeGeometria.margemSuperior(entradas(interfaceModo = ModoDaInterface.EMBUTIDA)))
    }

    @Test fun margensLateraisDescontamAOrigemDaPagina() {
        val e = entradas(raiz = Retangulo(100, 0, 1900, 720))
        assertEquals(28, CalculoDeGeometria.margemLateral(e, direita = false))
        assertEquals(108, CalculoDeGeometria.margemLateral(e, direita = true))
    }

    @Test fun fundoDosPopupsUsaOTopoDaFaixa() {
        val e = entradas(faixaNaTela = 572)
        val m = CalculoDeGeometria.margens(e, ModoDeTela.APP_E_CARRO)
        assertEquals(560, m.fundoDosPopups)
    }

    @Test fun origemSemRaizMedida() {
        val inset = entradas(raiz = null, interfaceModo = ModoDaInterface.EMBUTIDA, insetEsquerdo = 128)
        assertEquals(Retangulo(128, 0, 1920, 720), CalculoDeGeometria.origemDaPagina(inset))
        val flutuante = entradas(raiz = null, interfaceModo = ModoDaInterface.FLUTUANTE, insetEsquerdo = 128)
        assertEquals(Retangulo(0, 0, 1920, 720), CalculoDeGeometria.origemDaPagina(flutuante))
    }

    @Test fun ancoraEscolhidaPelaMetadeDaTela() {
        assertEquals(Lado.ESQUERDA, CalculoDeGeometria.ladoDaAncora(Retangulo(128, 0, 900, 700), 1920))
        assertEquals(Lado.DIREITA, CalculoDeGeometria.ladoDaAncora(Retangulo(964, 0, 1792, 700), 1920))
        assertEquals(Lado.ESQUERDA, CalculoDeGeometria.ladoDaAncora(null, 1920))
        assertEquals(Lado.DIREITA, CalculoDeGeometria.ladoDaAncora(Retangulo(960, 0, 1792, 700), 1920))
    }

    @Test fun faixaDeToqueDe28Dp() {
        assertEquals(28, CalculoDeGeometria.alturaDaFaixaDeToqueNoTopo(1f))
        assertEquals(35, CalculoDeGeometria.alturaDaFaixaDeToqueNoTopo(1.25f))
        assertEquals(28, CalculoDeGeometria.alturaDaFaixaDeToqueNoTopo(0f))
    }

    @Test fun observadorDeInsetsSoCresce() {
        val o = ObservadorDeInsets()
        o.observar(100, 20)
        o.observar(50, 30)
        assertEquals(100, o.maiorEsquerdo)
        assertEquals(30, o.maiorSuperior)
    }

    @Test fun flagsDaInterfaceNosTresModos() {
        assertEquals(0x1102, FlagsDaInterface.paraModo(ModoDaInterface.EMBUTIDA))
        assertEquals(0x1702, FlagsDaInterface.paraModo(ModoDaInterface.FLUTUANTE))
        assertEquals(0x1706, FlagsDaInterface.paraModo(ModoDaInterface.TELA_CHEIA))
    }

    @Test fun todosOsLadosFicamDentroDoTrilhoSimetrico() {
        for (modo in ModoDeTela.entries) for (p in ProporcaoDaDivisao.entries) for (l in Lado.entries) {
            val r = lado(entradas(), l, modo, p)
            assertTrue("$modo $p $l", r.esquerda >= 128 && r.direita <= 1920 - 128)
            assertTrue("$modo $p $l altura", r.altura >= 200)
        }
    }
}
