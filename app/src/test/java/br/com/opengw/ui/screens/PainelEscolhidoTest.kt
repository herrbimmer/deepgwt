package br.com.opengw.ui.screens

import br.com.opengw.nucleo.Preferencias
import br.com.opengw.projection.DestinoDaProjecao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os interruptores independentes de Telas (Alpha 6.0) no lugar do seletor único.
 *
 * O que estes testes travam: quem atualiza cai no que já via, cada combinação grava as chaves que
 * o resto do app lê, e a projeção vai para o lugar que a tela promete.
 */
class PainelEscolhidoTest {

    private val info = Preferencias.ORIGINAL_PANEL_INFO.chave
    private val circulo = Preferencias.ORIGINAL_PANEL_CIRCLE_PROJECTION.chave
    private val painelVirtual = Preferencias.ENABLE_VIRTUAL_CLUSTER.chave
    private val projetor = Preferencias.ENABLE_INSTRUMENT_PROJECTOR.chave
    private val menu = Preferencias.ENABLE_CUSTOM_MENU.chave

    @Test
    fun `quem vem de antes da 6_0 continua vendo o que via`() {
        // "Original + informações": temperatura, placa e o círculo (antes com o menu, agora com a projeção).
        assertEquals(
                PainelEscolhido(temaCustomizado = false, informacoes = true, circulo = true),
                PainelEscolhido.de(ModoDoPainel.ORIGINAL_COM_INFORMACOES, null, null)
        )
        // Desligado não acende nada, mesmo com as chaves novas ausentes (que valem "ligado").
        assertEquals(
                PainelEscolhido(false, false, false),
                PainelEscolhido.de(ModoDoPainel.DESLIGADO, null, null)
        )
        assertTrue(PainelEscolhido.de(ModoDoPainel.TEMA_CUSTOMIZADO, null, null).temaCustomizado)
    }

    @Test
    fun `painel desligado ignora o que ficou gravado`() {
        assertEquals(PainelEscolhido(false, false, false), PainelEscolhido.de(ModoDoPainel.DESLIGADO, true, true))
    }

    @Test
    fun `painel de fabrica com as duas gravadas desligadas vale o de antes`() {
        assertEquals(
                PainelEscolhido(false, true, true),
                PainelEscolhido.de(ModoDoPainel.ORIGINAL_COM_INFORMACOES, false, false)
        )
    }

    @Test
    fun `o modo sai dos interruptores`() {
        assertEquals(ModoDoPainel.DESLIGADO, PainelEscolhido(false, false, false).modo)
        assertEquals(ModoDoPainel.ORIGINAL_COM_INFORMACOES, PainelEscolhido(false, true, false).modo)
        assertEquals(ModoDoPainel.ORIGINAL_COM_INFORMACOES, PainelEscolhido(false, false, true).modo)
        // O tema customizado manda; os dois do painel de fábrica ficam só guardados.
        assertEquals(ModoDoPainel.TEMA_CUSTOMIZADO, PainelEscolhido(true, false, false).modo)
        assertEquals(ModoDoPainel.TEMA_CUSTOMIZADO, PainelEscolhido(true, true, true).modo)
    }

    @Test
    fun `cada combinacao grava e relida volta igual`() {
        for (tema in listOf(false, true)) for (i in listOf(false, true)) for (c in listOf(false, true)) {
            val escolha = PainelEscolhido(tema, i, c)
            val gravado = escolha.preferencias()
            val relido =
                    PainelEscolhido.de(
                            ModoDoPainel.de(
                                    gravado.getValue(projetor),
                                    gravado.getValue(Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.chave),
                                    gravado.getValue(menu),
                                    gravado.getValue(painelVirtual)
                            ),
                            gravado.getValue(info),
                            gravado.getValue(circulo)
                    )
            val esperado = if (!tema && !i && !c) PainelEscolhido(false, false, false) else escolha
            assertEquals("tema=$tema info=$i circulo=$c", esperado, relido)
        }
    }

    @Test
    fun `so a informacao ou so o circulo ja ligam a camada, sem desenhar o painel inteiro`() {
        for (escolha in listOf(PainelEscolhido(false, true, false), PainelEscolhido(false, false, true))) {
            val gravado = escolha.preferencias()
            assertTrue(gravado.getValue(projetor))
            // O menu do volante continua ligado com a camada: é o que entrega o BACK que dispensa
            // o aviso — e o aviso é o que tira a projeção da frente no círculo. O tema, sobre o
            // painel de fábrica, não age mais nas teclas.
            assertTrue(gravado.getValue(menu))
            assertFalse(gravado.getValue(painelVirtual))
        }
        assertTrue(PainelEscolhido(false, false, false).preferencias().values.none { it })
    }

    @Test
    fun `a projecao vai para onde a tela promete`() {
        assertEquals(DestinoDaProjecao.VAO_CENTRAL, PainelEscolhido(true, false, false).destinoDaProjecao)
        // Tema ligado ganha do círculo guardado: o painel de fábrica nem está na tela.
        assertEquals(DestinoDaProjecao.VAO_CENTRAL, PainelEscolhido(true, true, true).destinoDaProjecao)
        assertEquals(DestinoDaProjecao.CIRCULO_DIREITO, PainelEscolhido(false, false, true).destinoDaProjecao)
        assertEquals(DestinoDaProjecao.CIRCULO_DIREITO, PainelEscolhido(false, true, true).destinoDaProjecao)
        // Só as informações: não há onde projetar — o gesto avisa em vez de tapar o velocímetro.
        assertNull(PainelEscolhido(false, true, false).destinoDaProjecao)
        assertNull(PainelEscolhido(false, false, false).destinoDaProjecao)
    }
}
