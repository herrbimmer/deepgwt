// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.automacoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regras puras (motor-automacoes.md §G e §H, vetores exatos). */
class RegrasDasAutomacoesTest {

    private fun m(h: Int, min: Int) = RegrasDasAutomacoes.minutos(h, min)

    private fun dentro(agora: Int, inicio: Int, fim: Int) = RegrasDasAutomacoes.dentroDaJanela(agora, inicio, fim)

    @Test
    fun `G2 - janela padrao 18h00-09h00`() {
        val i = m(18, 0)
        val f = m(9, 0)
        assertFalse(dentro(m(17, 59), i, f))
        assertTrue(dentro(m(18, 0), i, f))
        assertTrue(dentro(m(23, 30), i, f))
        assertTrue(dentro(m(8, 59), i, f))
        assertFalse(dentro(m(9, 0), i, f))
    }

    @Test
    fun `G2 - janela 09h00-18h00`() {
        val i = m(9, 0)
        val f = m(18, 0)
        assertFalse(dentro(m(8, 59), i, f))
        assertTrue(dentro(m(9, 0), i, f))
        assertTrue(dentro(m(17, 59), i, f))
        assertFalse(dentro(m(18, 0), i, f))
    }

    @Test
    fun `G2 D-A11 - inicio igual ao fim abre a qualquer hora`() {
        val i = m(7, 0)
        for (agora in listOf(m(0, 0), m(6, 59), m(7, 0), m(12, 0), m(23, 59))) {
            assertTrue(dentro(agora, i, i))
        }
    }

    @Test
    fun `H2 - original do botao`() {
        assertEquals("0", RegrasDasAutomacoes.originalDoBotao("0"))
        assertEquals("98", RegrasDasAutomacoes.originalDoBotao(" 98 "))
        assertEquals("12", RegrasDasAutomacoes.originalDoBotao("12"))
        for (invalido in listOf("99", "", "  ", "null", "undefined", "abc", "-1", "100", "3.5", null)) {
            assertNull("'$invalido'", RegrasDasAutomacoes.originalDoBotao(invalido))
        }
    }

    @Test
    fun `I1 - somar servico e normalizar`() {
        assertEquals("a/.X", RegrasDasAutomacoes.somarServico("", "a/.X"))
        assertEquals("a/.X", RegrasDasAutomacoes.somarServico("null", "a/.X"))
        assertEquals("b/.Y:a/.X", RegrasDasAutomacoes.somarServico("b/.Y", "a/.X"))
        assertNull(RegrasDasAutomacoes.somarServico("b/.Y:a/a.X", "a/.X"))
        assertEquals("pkg/pkg.Classe", RegrasDasAutomacoes.normalizarComponente("pkg/.Classe"))
        assertEquals("pkg/outro.Classe", RegrasDasAutomacoes.normalizarComponente("pkg/outro.Classe"))
    }

    @Test
    fun `B8 - READY desligado`() {
        assertTrue(RegrasDasAutomacoes.readyDesligado("0"))
        assertTrue(RegrasDasAutomacoes.readyDesligado(" -1 "))
        assertFalse(RegrasDasAutomacoes.readyDesligado("1"))
        assertFalse(RegrasDasAutomacoes.readyDesligado(""))
        assertFalse(RegrasDasAutomacoes.readyDesligado(null))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
