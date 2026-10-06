// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O caso que motivou esta política é o primeiro teste: a chave mestra do farol chega VAZIA neste
 * carro, e enquanto ela era a única fonte nenhum tema jamais acendeu o modo noturno.
 */
class HeadlightPolicyTest {

    private fun sinais(
        mestre: String? = null,
        baixa: String? = null,
        alta: String? = null,
        posicao: String? = null
    ) = HeadlightPolicy.Sinais(mestre, baixa, alta, posicao)

    @Test
    fun `automatico usa luz baixa quando a chave mestra vem vazia`() {
        // Exatamente o que a captura do carro traz: mestra "", luz baixa "1".
        val s = sinais(mestre = "", baixa = "1", alta = "0", posicao = "1")
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_AUTO, s, hora = 12))
    }

    @Test
    fun `automatico com mestra vazia e tudo apagado fica desligado`() {
        val s = sinais(mestre = "", baixa = "0", alta = "0", posicao = "0")
        assertFalse(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_AUTO, s, hora = 12))
    }

    @Test
    fun `automatico respeita a mestra quando ela existe, mesmo com a luz baixa ligada`() {
        // Num carro que publica a chave mestra, ela manda: "0" é resposta, não ausência.
        val s = sinais(mestre = "0", baixa = "1", alta = "0")
        assertFalse(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_AUTO, s, hora = 12))
    }

    @Test
    fun `mestra vazia nao e o mesmo que mestra zerada`() {
        assertFalse(HeadlightPolicy.publicada(""))
        assertFalse(HeadlightPolicy.publicada(null))
        assertFalse(HeadlightPolicy.publicada("  "))
        assertTrue(HeadlightPolicy.publicada("0"))
        assertTrue(HeadlightPolicy.publicada("1"))
    }

    @Test
    fun `fonte so-mestra nao inventa farol a partir de outra luz`() {
        val s = sinais(mestre = "", baixa = "1", alta = "1", posicao = "1")
        assertFalse(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_MESTRE, s, hora = 12))
    }

    @Test
    fun `luz alta sozinha ja conta como farol aceso`() {
        val s = sinais(mestre = "", baixa = "0", alta = "1")
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_AUTO, s, hora = 12))
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_LUZ_BAIXA, s, hora = 12))
    }

    @Test
    fun `fonte por horario ignora o carro inteiro`() {
        val apagado = sinais(mestre = "0", baixa = "0", alta = "0", posicao = "0")
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_HORARIO, apagado, hora = 18))
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_HORARIO, apagado, hora = 23))
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_HORARIO, apagado, hora = 0))
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_HORARIO, apagado, hora = 5))
        assertFalse(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_HORARIO, apagado, hora = 6))
        assertFalse(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_HORARIO, apagado, hora = 17))
    }

    @Test
    fun `sempre e nunca sao absolutos`() {
        val s = sinais(mestre = "", baixa = "0")
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_SEMPRE, s, hora = 12))
        assertFalse(
            HeadlightPolicy.resolver(HeadlightPolicy.FONTE_NUNCA, sinais(baixa = "1"), hora = 23)
        )
    }

    @Test
    fun `preferencia desconhecida cai no automatico e nao no escuro`() {
        // Uma preferência gravada por outra versão não pode apagar o modo noturno em silêncio.
        val s = sinais(mestre = "", baixa = "1")
        assertTrue(HeadlightPolicy.resolver("fonte-que-nao-existe", s, hora = 12))
        assertTrue(HeadlightPolicy.resolver(null, s, hora = 12))
    }

    @Test
    fun `luz de posicao e uma fonte separada`() {
        val s = sinais(mestre = "", baixa = "0", alta = "0", posicao = "1")
        assertTrue(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_POSICAO, s, hora = 12))
        assertFalse(HeadlightPolicy.resolver(HeadlightPolicy.FONTE_LUZ_BAIXA, s, hora = 12))
    }

    @Test
    fun `o texto entregue ao tema e 1 ou 0`() {
        assertEquals(
            "1",
            HeadlightPolicy.resolverComoTexto(HeadlightPolicy.FONTE_AUTO, sinais(baixa = "1"), 12)
        )
        assertEquals(
            "0",
            HeadlightPolicy.resolverComoTexto(HeadlightPolicy.FONTE_AUTO, sinais(baixa = "0"), 12)
        )
    }

    @Test
    fun `sinaisDe le exatamente as quatro chaves do barramento`() {
        val lidas = mutableListOf<String>()
        HeadlightPolicy.sinaisDe { chave -> lidas.add(chave); null }
        assertEquals(HeadlightPolicy.CHAVES_DE_ORIGEM, lidas.toSet())
        assertEquals(4, lidas.size)
    }

    @Test
    fun `a chave do contrato e a que os temas assinam`() {
        assertEquals("car.basic.head_light_status", HeadlightPolicy.CHAVE_DO_CONTRATO)
        assertTrue(HeadlightPolicy.CHAVE_DO_CONTRATO in HeadlightPolicy.CHAVES_DE_ORIGEM)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
