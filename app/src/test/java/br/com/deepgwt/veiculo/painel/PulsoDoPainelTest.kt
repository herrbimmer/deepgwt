// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A.4 de `motor-painel-e-volante.md`: pulso de vida. */
class PulsoDoPainelTest {

    private fun bancada(integracao: Boolean = true) = BancadaDoPainel().comLinha().comCanal().apply {
        preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = integracao
    }

    @Test
    fun manda_75_antes_do_primeiro_134_e_depois_um_por_segundo() {
        val b = bancada()
        b.nucleo.iniciarPulso()
        assertTrue(b.nucleo.pulsoRodando)
        assertTrue("volta na hora", b.chamadas.isEmpty())
        b.linha.rodarPendentes()
        assertEquals(listOf("75=1"), b.chamadas)
        b.linha.avancar(999)
        assertEquals(listOf("75=1"), b.chamadas)
        b.linha.avancar(1)
        assertEquals(listOf("75=1", "134=0"), b.chamadas)
        b.linha.avancar(1_000)
        b.linha.avancar(1_000)
        assertEquals(listOf("75=1", "134=0", "134=1", "134=2"), b.chamadas)
    }

    @Test
    fun para_com_a_preferencia_desligada() {
        val b = bancada()
        b.nucleo.iniciarPulso()
        b.linha.avancar(2_000)
        assertEquals(listOf("75=1", "134=0", "134=1"), b.chamadas)
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = false
        b.linha.avancar(5_000)
        assertEquals(listOf("75=1", "134=0", "134=1"), b.chamadas)
        assertFalse(b.nucleo.pulsoRodando)
        assertEquals(0, b.linha.pendentes)
        // Pode começar de novo (e o contador continua).
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        b.nucleo.iniciarPulso()
        b.linha.avancar(1_000)
        assertEquals(listOf("75=1", "134=0", "134=1", "75=1", "134=2"), b.chamadas)
    }

    @Test
    fun nao_duplica() {
        val b = bancada()
        b.nucleo.iniciarPulso()
        b.nucleo.iniciarPulso()
        b.linha.avancar(1_000)
        b.nucleo.iniciarPulso()
        b.linha.avancar(1_000)
        assertEquals(listOf("75=1", "134=0", "134=1"), b.chamadas)
    }

    @Test
    fun pedido_134_2_reinicia() {
        val b = bancada()
        b.nucleo.mensagemRecebida(134, 2)
        assertTrue(b.chamadas.isEmpty())
        b.linha.rodarPendentes()
        // Um pulso agora e iniciarPulso (75 e o laço).
        assertEquals(listOf("134=0", "75=1"), b.chamadas)
        b.linha.avancar(1_000)
        assertEquals(listOf("134=0", "75=1", "134=1"), b.chamadas)
        // Com o pulso rodando: um pulso a mais agora, sem laço duplicado.
        b.nucleo.mensagemRecebida(134, 2)
        b.linha.rodarPendentes()
        b.linha.avancar(1_000)
        assertEquals(listOf("134=0", "75=1", "134=1", "134=2", "134=3"), b.chamadas)
    }

    @Test
    fun pedido_134_2_com_a_integracao_desligada_nao_faz_nada() {
        val b = bancada(integracao = false)
        b.nucleo.mensagemRecebida(134, 2)
        b.linha.avancar(3_000)
        assertTrue(b.chamadas.isEmpty())
        assertFalse(b.nucleo.pulsoRodando)
    }

    @Test
    fun outros_valores_de_134_nao_fazem_nada() {
        val b = bancada()
        b.nucleo.mensagemRecebida(134, 1)
        b.nucleo.mensagemRecebida(134, 0)
        b.linha.avancar(3_000)
        assertTrue(b.chamadas.isEmpty())
    }

    @Test
    fun contador_volta_a_0_depois_de_32767() {
        val b = bancada()
        b.nucleo.iniciarPulso()
        b.linha.rodarPendentes()
        repeat(32_770) { b.linha.avancar(1_000) }
        val pulsos = b.chamadas.filter { it.startsWith("134=") }.map { it.removePrefix("134=").toInt() }
        assertEquals(32_770, pulsos.size)
        assertEquals((0..32_767).toList() + listOf(0, 1), pulsos)
    }

    @Test
    fun sem_linha_registra_erro_e_nada() {
        val b = BancadaDoPainel().comCanal()
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        b.nucleo.iniciarPulso()
        assertFalse(b.nucleo.pulsoRodando)
        assertTrue(b.registro.erros.any { "sem a linha" in it })
        assertTrue(b.chamadas.isEmpty())
    }

    @Test
    fun sem_interface_descarta_e_o_contador_anda() {
        val b = BancadaDoPainel().comLinha()
        b.preferencias[Preferencias.ENABLE_INSTRUMENT_CUSTOM_MEDIA_INTEGRATION] = true
        b.nucleo.iniciarPulso()
        b.linha.avancar(2_000) // 75, 134=0 e 134=1 descartados
        assertTrue(b.chamadas.isEmpty())
        assertTrue(b.registro.avisos.any { "descartada" in it })
        b.nucleo.canal = b.canal
        b.linha.avancar(1_000)
        assertEquals(listOf("134=2"), b.chamadas)
    }

    @Test
    fun falha_no_envio_so_registra() {
        val b = bancada()
        b.canal.falhar = true
        b.nucleo.iniciarPulso()
        b.linha.avancar(1_000)
        assertTrue(b.nucleo.pulsoRodando)
        assertEquals(2, b.registro.erros.size)
        b.canal.falhar = false
        b.linha.avancar(1_000)
        assertEquals(listOf("134=1"), b.chamadas)
    }

    @Test
    fun trocar_a_linha_zera_o_pulso() {
        val b = bancada()
        b.nucleo.iniciarPulso()
        b.linha.avancar(1_000)
        val nova = LinhaFalsa(b.relogio, "nova")
        b.nucleo.trocarLinha(nova)
        assertFalse(b.nucleo.pulsoRodando)
        assertTrue(b.linha.encerrada)
        nova.avancar(5_000)
        assertEquals(listOf("75=1", "134=0"), b.chamadas)
        // Desligar (linha nula) também.
        b.nucleo.iniciarPulso()
        assertTrue(b.nucleo.pulsoRodando)
        b.nucleo.trocarLinha(null)
        assertFalse(b.nucleo.pulsoRodando)
        assertTrue(nova.encerrada)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
