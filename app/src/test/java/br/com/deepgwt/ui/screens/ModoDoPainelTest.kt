// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.screens

import br.com.deepgwt.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O seletor único de Telas no lugar de "Habilitar Funções do Cluster" + "Painel Virtual".
 *
 * O que estes testes travam é a PONTE com as preferências antigas: quem atualiza o app não pode
 * cair num modo diferente do que já estava vendo no carro, e cada modo tem de gravar exatamente as
 * quatro chaves que o resto do app já lê.
 */
class ModoDoPainelTest {

    private val projetor = Preferencias.ENABLE_INSTRUMENT_PROJECTOR.chave
    private val midia = Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION.chave
    private val menu = Preferencias.ENABLE_CUSTOM_MENU.chave
    private val painelVirtual = Preferencias.ENABLE_VIRTUAL_CLUSTER.chave

    @Test
    fun `interruptor mestre desligado vira Desligado, mesmo com Painel Virtual gravado`() {
        assertEquals(ModoDoPainel.DESLIGADO, ModoDoPainel.de(false, false, false, false))
        // Sem projetor não há tema nenhum na tela: o Painel Virtual gravado sozinho não é um modo.
        assertEquals(ModoDoPainel.DESLIGADO, ModoDoPainel.de(false, false, false, true))
    }

    @Test
    fun `funcoes ligadas sem Painel Virtual e o painel original com informacoes`() {
        // É o estado em que as temperaturas e o WARN apareciam por cima do painel de fábrica.
        assertEquals(ModoDoPainel.ORIGINAL_COM_INFORMACOES, ModoDoPainel.de(true, true, true, false))
        // Qualquer uma das três chaves basta, como no interruptor antigo.
        assertEquals(ModoDoPainel.ORIGINAL_COM_INFORMACOES, ModoDoPainel.de(false, true, false, false))
        assertEquals(ModoDoPainel.ORIGINAL_COM_INFORMACOES, ModoDoPainel.de(false, false, true, false))
    }

    @Test
    fun `funcoes ligadas com Painel Virtual e o tema customizado`() {
        assertEquals(ModoDoPainel.TEMA_CUSTOMIZADO, ModoDoPainel.de(true, true, true, true))
    }

    @Test
    fun `cada modo grava as quatro chaves e relido volta ao mesmo modo`() {
        for (modo in ModoDoPainel.values()) {
            val gravado = ModoDoPainel.preferenciasDe(modo)
            assertEquals(setOf(projetor, midia, menu, painelVirtual), gravado.keys)
            val relido =
                    ModoDoPainel.de(
                            gravado.getValue(projetor),
                            gravado.getValue(midia),
                            gravado.getValue(menu),
                            gravado.getValue(painelVirtual)
                    )
            assertEquals(modo, relido)
        }
    }

    @Test
    fun `painel original liga a camada sem desenhar os mostradores`() {
        val gravado = ModoDoPainel.preferenciasDe(ModoDoPainel.ORIGINAL_COM_INFORMACOES)
        assertTrue(gravado.getValue(projetor))
        assertTrue(gravado.getValue(midia))
        assertTrue(gravado.getValue(menu))
        assertFalse(gravado.getValue(painelVirtual))
    }

    @Test
    fun `desligado apaga tudo`() {
        assertTrue(ModoDoPainel.preferenciasDe(ModoDoPainel.DESLIGADO).values.none { it })
    }

    @Test
    fun `o tema so volta ao de fabrica ao sair do tema customizado ou desligar`() {
        assertTrue(
                ModoDoPainel.voltaAoTemaDeFabrica(
                        ModoDoPainel.TEMA_CUSTOMIZADO,
                        ModoDoPainel.ORIGINAL_COM_INFORMACOES
                )
        )
        assertTrue(
                ModoDoPainel.voltaAoTemaDeFabrica(
                        ModoDoPainel.ORIGINAL_COM_INFORMACOES,
                        ModoDoPainel.DESLIGADO
                )
        )
        // Ligar o painel original não apaga as cores que alguém deu ao Default.
        assertFalse(
                ModoDoPainel.voltaAoTemaDeFabrica(
                        ModoDoPainel.DESLIGADO,
                        ModoDoPainel.ORIGINAL_COM_INFORMACOES
                )
        )
        assertFalse(
                ModoDoPainel.voltaAoTemaDeFabrica(
                        ModoDoPainel.ORIGINAL_COM_INFORMACOES,
                        ModoDoPainel.TEMA_CUSTOMIZADO
                )
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
