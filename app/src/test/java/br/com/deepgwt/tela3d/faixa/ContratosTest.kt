package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Test

class ContratosTest {
    @Test
    fun `modo da casca - valores da ponte e texto desconhecido vira appCar`() {
        assertEquals("appCar", ModoDaCasca.APP_E_CARRO.valor)
        assertEquals("appsOnly", ModoDaCasca.LADO_A_LADO.valor)
        assertEquals(ModoDaCasca.LADO_A_LADO, ModoDaCasca.deTexto("appsOnly"))
        assertEquals(ModoDaCasca.APP_E_CARRO, ModoDaCasca.deTexto("appCar"))
        assertEquals(ModoDaCasca.APP_E_CARRO, ModoDaCasca.deTexto("qualquer"))
        assertEquals(ModoDaCasca.APP_E_CARRO, ModoDaCasca.deTexto(null))
    }

    @Test
    fun `proporcao - valores e texto desconhecido vira 1 para 1`() {
        assertEquals(listOf("1:1", "2:1", "1:2"), Proporcao.entries.map { it.valor })
        assertEquals(Proporcao.DOIS_UM, Proporcao.deTexto("2:1"))
        assertEquals(Proporcao.UM_DOIS, Proporcao.deTexto("1:2"))
        assertEquals(Proporcao.UM_UM, Proporcao.deTexto("3:1"))
    }

    @Test
    fun `chaves e padroes das sobreposicoes seguem o formato gravado`() {
        assertEquals("__gwm_hub", SobreposicoesDoDock.CHAVE_DO_HUB)
        assertEquals("#FFFFFF", SobreposicoesDoDock.COR_PADRAO)
        assertEquals("#3D4650", SobreposicoesDoDock.FUNDO_PADRAO)
    }

    @Test
    fun `chaves de preferencia e comandos nao mudam`() {
        assertEquals("dockSurfaceMode", FaixaDeBaixoImpl.PREF_SUPERFICIE)
        assertEquals("fabX", BotaoFlutuanteDeApps.PREF_X)
        assertEquals("fabY", BotaoFlutuanteDeApps.PREF_Y)
        assertEquals(16777480, BotaoFlutuanteDeApps.FLAGS_DA_JANELA)
        assertEquals(
            listOf("showLauncher", "openDesktopStudio", "openLayoutDesktops", "openLayoutCards", "openLayoutAppearance", "closeDesktopStudio"),
            listOf(
                FaixaDeBaixoImpl.COMANDO_MOSTRAR_APPS, FaixaDeBaixoImpl.COMANDO_ABRIR_ESTUDIO,
                FaixaDeBaixoImpl.COMANDO_LAYOUT_PAGINAS, FaixaDeBaixoImpl.COMANDO_LAYOUT_CARTOES, FaixaDeBaixoImpl.COMANDO_LAYOUT_APARENCIA,
                FaixaDeBaixoImpl.COMANDO_FECHAR_ESTUDIO,
            ),
        )
        assertEquals("Espaço de trabalho", CartaoEspacoDeTrabalho.TITULO)
        assertEquals(PlanoDaFileira.CHAVE_DO_HUB, SobreposicoesDoDock.CHAVE_DO_HUB)
    }
}
