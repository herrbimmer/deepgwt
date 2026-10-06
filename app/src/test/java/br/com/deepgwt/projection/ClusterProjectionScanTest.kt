// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.projection

import br.com.deepgwt.telas.ProjectionGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A leitura da varredura do tema.
 *
 * Isto lê dado vindo de FORA (a WebView do tema, que pode ser qualquer tema que o usuário instale),
 * então o que se trava aqui é a tolerância: resposta truncada, resposta com lixo no meio, resposta
 * vazia e resposta ausente têm significados diferentes e não podem virar todas a mesma coisa.
 */
class ClusterProjectionScanTest {

    private val W = 1920
    private val H = 720

    private fun ler(s: String?) = ClusterProjectionWindow.interpretarOcupados(s, W, H)

    @Test
    fun `le a lista normal que o tema devolve`() {
        val caixas = ler("\"60,80,560,560;1300,80,560,560\"")!!
        assertEquals(2, caixas.size)
        assertEquals(ProjectionGeometry.Caixa(60, 80, 560, 560), caixas[0])
        assertEquals(ProjectionGeometry.Caixa(1300, 80, 560, 560), caixas[1])
    }

    @Test
    fun `aceita a resposta sem as aspas de evaluateJavascript`() {
        // Nem todo caminho passa pela serialização da WebView (testes, ponte futura).
        val caixas = ler("10,20,30,40")!!
        assertEquals(1, caixas.size)
        assertEquals(ProjectionGeometry.Caixa(10, 20, 30, 40), caixas[0])
    }

    @Test
    fun `resposta ausente e resposta vazia significam coisas diferentes`() {
        // Ausente: a varredura não rodou -> cair na caixa padrão.
        assertNull(ler(null))
        assertNull(ler(""))
        assertNull(ler("null"))
        assertNull(ler("\"null\""))
        assertNull(ler("\"ERRO\""))
        // Vazia: rodou e o tema não desenha nada -> a tela toda está livre, e isso é uma resposta.
        val vazia = ler("\"\"")
        assertNotNull(vazia)
        assertTrue(vazia!!.isEmpty())
    }

    @Test
    fun `o fundo do tema e descartado`() {
        // Quase todo tema tem um elemento opaco do tamanho da tela. Contá-lo marcaria tudo como
        // ocupado e a projeção nunca acharia onde caber — este descarte é o que faz a varredura
        // funcionar em tema de verdade.
        val caixas = ler("\"0,0,1920,720;60,80,560,560\"")!!
        assertEquals(1, caixas.size)
        assertEquals(ProjectionGeometry.Caixa(60, 80, 560, 560), caixas[0])
    }

    @Test
    fun `um elemento grande mas abaixo do limite continua contando`() {
        // 1920x380 = 40% da tela: é uma faixa de verdade, não fundo, e tem de ocupar espaço.
        val caixas = ler("\"0,0,1920,380\"")!!
        assertEquals(1, caixas.size)
    }

    @Test
    fun `lixo no meio nao derruba a leitura inteira`() {
        val caixas = ler("\"60,80,560,560;quebrado;;1,2;1300,80,560,560;10,10,0,50\"")!!
        assertEquals(2, caixas.size)
        assertEquals(60, caixas[0].x)
        assertEquals(1300, caixas[1].x)
    }

    @Test
    fun `a varredura de um painel de dois mostradores acha o vao`() {
        // O caminho inteiro: texto do tema -> caixas -> maior vão central.
        val caixas = ler("\"0,0,1920,720;60,80,560,560;1300,80,560,560;880,30,160,54\"")!!
        val vao = ProjectionGeometry.maiorCaixaLivreNoCentro(W, H, caixas)
        assertNotNull(vao)
        vao!!
        assertTrue("vao=$vao", vao.x >= 620 && vao.direita <= 1300)
        assertTrue("vao=$vao", vao.largura >= ProjectionGeometry.LARGURA_MINIMA)
        assertTrue("vao=$vao", vao.altura >= ProjectionGeometry.ALTURA_MINIMA)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
