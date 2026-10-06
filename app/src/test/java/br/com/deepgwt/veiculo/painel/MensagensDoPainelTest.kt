// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A.2 e A.9 de `motor-painel-e-volante.md`. */
class MensagensDoPainelTest {

    private fun bancada(integracao: Boolean = true) = BancadaDoPainel().comLinha().comCanal().apply {
        preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = integracao
    }

    @Test
    fun a9_responde_1_e_2_antes_de_chamar_o_android_auto() {
        val b = bancada()
        b.nucleo.mensagemRecebida(135, 1)
        b.nucleo.mensagemRecebida(135, 2)
        assertEquals(listOf("135=1", "aa-midia:1", "135=2", "aa-midia:2"), b.chamadas)
        assertEquals(listOf("midia_do_painel", "midia_do_painel"), b.eventosDoDiario())
        assertEquals(1, b.diario[0].second["valor"])
    }

    @Test
    fun a9_nao_responde_3() {
        val b = bancada()
        b.nucleo.mensagemRecebida(135, 3)
        assertEquals(listOf("aa-midia:3"), b.chamadas)
    }

    @Test
    fun a9_sem_resposta_com_a_integracao_desligada() {
        val b = bancada(integracao = false)
        b.nucleo.mensagemRecebida(135, 1)
        assertEquals(listOf("aa-midia:1"), b.chamadas)
        assertEquals(listOf("midia_do_painel"), b.eventosDoDiario())
    }

    @Test
    fun a9_resposta_independe_do_android_auto() {
        val b = bancada()
        b.projecao.lancar = true
        b.nucleo.mensagemRecebida(135, 2)
        assertEquals(listOf("135=2", "aa-midia:2"), b.chamadas)
        b.projecao.lancar = false
        b.canal.falhar = true
        b.nucleo.mensagemRecebida(135, 1)
        assertEquals(listOf("135=2", "aa-midia:2", "aa-midia:1"), b.chamadas)
        assertEquals(2, b.registro.erros.size)
    }

    @Test
    fun a9_responde_na_thread_de_quem_entregou() {
        val b = bancada()
        b.nucleo.mensagemRecebida(135, 1)
        assertEquals("sem passar pela linha", 0, b.linha.pendentes)
        assertEquals("135=1", b.chamadas.first())
    }

    @Test
    fun a2_so_as_4_mensagens_entram_no_log() {
        val b = bancada()
        for (id in 1..140) b.nucleo.mensagemRecebida(id, 0)
        val logadas = b.registro.infos.filter { it.startsWith("painel → app") }
        assertEquals(4, logadas.size)
        for (id in listOf(75, 133, 134, 135)) assertTrue(logadas.any { it.startsWith("painel → app: $id =") })
    }

    @Test
    fun a2_diagnostico_pedido_pela_projecao() {
        val b = bancada()
        b.projecao.mensagensComDiagnostico += 200
        b.nucleo.mensagemRecebida(200, 7)
        b.nucleo.mensagemRecebida(201, 7)
        val logadas = b.registro.infos.filter { it.startsWith("painel → app") }
        assertEquals(listOf("painel → app (diagnóstico): 200 = 7"), logadas)
    }

    @Test
    fun a2_mensagem_sem_valor_e_ignorada() {
        val b = bancada()
        b.nucleo.mensagemRecebida(133, null)
        b.nucleo.mensagemRecebida(135, null)
        b.linha.rodarPendentes()
        assertEquals(0, b.nucleo.cartaoAtual)
        assertTrue(b.chamadas.isEmpty())
        assertEquals(2, b.registro.avisos.size)
    }

    @Test
    fun a2_outras_mensagens_nao_mexem_em_nada() {
        val b = bancada()
        b.nucleo.mensagemRecebida(75, 1)
        b.nucleo.mensagemRecebida(99, 3)
        b.linha.avancar(2_000)
        assertEquals(0, b.nucleo.cartaoAtual)
        assertTrue(b.chamadas.isEmpty())
        assertTrue(b.recebidos.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
