// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.painel.ArbitroDoCartao
import br.com.deepgwt.painel.TeclaDoVolante
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A.3, A.5, A.6 e A.7 de `motor-painel-e-volante.md`. */
class CartaoDoPainelTest {

    private class Vetor(
        val anterior: Int, val novo: Int, val msTecla: Long, val codigo: Int, val msSintetico: Long, val alvo: Int,
        val protecao: Boolean, val arDoApp: Boolean, val ignorar: Boolean,
    )

    /** A tabela de A.5, exata. */
    private val vetores = listOf(
        Vetor(1, 3, 616, 1027, 616, 1, false, false, true),
        Vetor(1, 1, 616, 1027, 616, 1, false, false, true),
        Vetor(1, 0, -1, -1, -1, -1, false, false, true),
        Vetor(1, 3, -1, -1, -1, -1, false, false, false),
        Vetor(0, 3, -1, -1, -1, -1, false, false, true),
        Vetor(0, 3, 250, 1027, -1, -1, false, false, false),
        Vetor(0, 1, 250, 1029, -1, -1, false, false, false),
        Vetor(0, 1, -1, -1, -1, -1, false, false, true),
        Vetor(3, 1, 50, 1025, 4282, 3, false, false, true),
        Vetor(3, 1, 282, 1024, 4000, 3, false, false, true),
        Vetor(3, 0, 1884, 1028, 4282, 3, false, false, true),
        Vetor(3, 1, 500, 1030, -1, -1, false, false, false),
        Vetor(3, 1, 4000, 1025, -1, -1, false, false, false),
        Vetor(3, 1, 1527, 1027, 1536, 3, false, true, true),
        Vetor(3, 1, 30001, 1027, 30000, 3, false, true, true),
        Vetor(3, 1, 200, 1030, -1, -1, false, false, false),
        Vetor(3, 0, 200, 1029, -1, -1, false, false, false),
        Vetor(3, 1, 5, 1030, 5000, 3, false, true, false),
        Vetor(3, 0, 5, 1029, 5000, 3, false, true, false),
        Vetor(3, 1, 5, 1030, 5000, 3, true, true, false),
        Vetor(3, 1, -1, -1, -1, -1, true, false, true),
        Vetor(3, 1, 500, 1030, -1, -1, true, false, false),
    )

    /** Monta o estado que o motor guardaria para o vetor (instantes = agora − ms). */
    private fun BancadaDoPainel.prepararVetor(v: Vetor) {
        val agora = relogio.agora()
        nucleo.restaurar(
            FotoDoCartao(
                cartao = v.anterior,
                ultimaTecla = TeclaDoVolante.doCodigo(v.codigo),
                instanteDaTecla = if (v.msTecla < 0) NUNCA else agora - v.msTecla,
                alvoSintetico = v.alvo,
                instanteSintetico = if (v.msSintetico < 0) NUNCA else agora - v.msSintetico,
                arDoApp = v.arDoApp,
            ),
        )
        projecao.proteger = v.protecao
    }

    @Test
    fun a5_vetores_pelo_motor() {
        for ((i, v) in vetores.withIndex()) {
            val b = BancadaDoPainel().comLinha()
            b.prepararVetor(v)
            b.nucleo.mensagemRecebida(133, v.novo)
            b.linha.rodarPendentes()
            val rotulo = "vetor ${i + 1}: ${v.anterior} → ${v.novo}"
            if (v.ignorar) {
                assertEquals(rotulo, v.anterior, b.nucleo.cartaoAtual)
                assertTrue(rotulo, b.recebidos.isEmpty())
                assertEquals(rotulo, listOf("cartao_do_carro_ignorado"), b.eventosDoDiario())
            } else {
                assertEquals(rotulo, v.novo, b.nucleo.cartaoAtual)
                assertEquals(rotulo, listOf<EventoDoPainel>(EventoDoPainel.CartaoMudou(v.novo)), b.recebidos)
                assertEquals(rotulo, listOf("cartao_do_carro_aceito"), b.eventosDoDiario())
            }
        }
    }

    @Test
    fun a5_foto_passa_os_tempos_ao_arbitro() {
        val agora = 50_000L
        for (v in vetores) {
            val foto = FotoDoCartao(
                cartao = v.anterior,
                ultimaTecla = TeclaDoVolante.doCodigo(v.codigo),
                instanteDaTecla = if (v.msTecla < 0) NUNCA else agora - v.msTecla,
                alvoSintetico = v.alvo,
                instanteSintetico = if (v.msSintetico < 0) NUNCA else agora - v.msSintetico,
                arDoApp = v.arDoApp,
            )
            assertEquals(v.ignorar, foto.ignorar(v.novo, agora, v.protecao))
        }
    }

    @Test
    fun a5_soltura_por_tecla() {
        for (codigo in listOf(1026, 1027, 1029, 1030)) assertTrue(ArbitroDoCartao.soltaPosseDoAr(TeclaDoVolante.doCodigo(codigo)))
        for (codigo in listOf(1024, 1025, 1028)) assertFalse(ArbitroDoCartao.soltaPosseDoAr(TeclaDoVolante.doCodigo(codigo)))
    }

    @Test
    fun a3_aceitar_muda_o_cartao_e_publica_fora_da_thread() {
        val b = BancadaDoPainel().comLinha()
        b.nucleo.restaurar(FotoDoCartao(cartao = 1))
        b.nucleo.mensagemRecebida(133, 3)
        assertEquals(3, b.nucleo.cartaoAtual)
        assertTrue("só na linha do painel", b.recebidos.isEmpty())
        b.linha.rodarPendentes()
        assertEquals(listOf<EventoDoPainel>(EventoDoPainel.CartaoMudou(3)), b.recebidos)
        assertEquals(b.relogio.agora(), b.nucleo.instanteDaUltima133)
    }

    @Test
    fun a3_ignorar_nao_publica() {
        val b = BancadaDoPainel().comLinha()
        b.nucleo.mensagemRecebida(133, 3) // 0 → 3 sem tecla: o carro subiu o ar sozinho
        b.linha.rodarPendentes()
        assertEquals(0, b.nucleo.cartaoAtual)
        assertTrue(b.recebidos.isEmpty())
        val detalhes = b.diario.single().second
        assertEquals(0, detalhes["de"])
        assertEquals(3, detalhes["para"])
        assertEquals(-1L, detalhes["msDesdeTecla"])
        assertEquals(false, detalhes["protecaoDaProjecao"])
        assertEquals(false, detalhes["protecaoSintetica"])
    }

    @Test
    fun a3_sair_do_3_solta_a_posse_do_ar() {
        val b = BancadaDoPainel().comLinha()
        val agora = b.relogio.agora()
        b.nucleo.restaurar(FotoDoCartao(3, TeclaDoVolante.BACK, agora - 5, 3, agora - 5_000, arDoApp = true))
        b.nucleo.mensagemRecebida(133, 1)
        val foto = b.nucleo.fotoAtual()
        assertEquals(1, foto.cartao)
        assertFalse(foto.arDoApp)
        assertEquals(-1, foto.alvoSintetico)
        assertEquals(NUNCA, foto.instanteSintetico)
        // A última tecla continua (só a posse e a sintética saem).
        assertEquals(TeclaDoVolante.BACK, foto.ultimaTecla)
    }

    @Test
    fun a3_sem_linha_publica_na_hora() {
        val b = BancadaDoPainel()
        b.nucleo.restaurar(FotoDoCartao(cartao = 1))
        b.nucleo.mensagemRecebida(133, 3)
        assertEquals(listOf<EventoDoPainel>(EventoDoPainel.CartaoMudou(3)), b.recebidos)
    }

    @Test
    fun a3_projecao_que_lanca_nao_derruba() {
        val b = BancadaDoPainel().comLinha()
        b.projecao.lancar = true
        b.nucleo.restaurar(FotoDoCartao(cartao = 1))
        b.nucleo.mensagemRecebida(133, 3)
        assertEquals(3, b.nucleo.cartaoAtual)
        assertTrue(b.registro.erros.isNotEmpty())
    }

    @Test
    fun a6_anel() {
        val casos = listOf(
            Triple(0, TeclaDoVolante.RIGHT, 1), Triple(0, TeclaDoVolante.LEFT, 3),
            Triple(1, TeclaDoVolante.RIGHT, 3), Triple(1, TeclaDoVolante.LEFT, 0),
            Triple(3, TeclaDoVolante.RIGHT, 0), Triple(3, TeclaDoVolante.LEFT, 1),
            Triple(2, TeclaDoVolante.RIGHT, 1),
        )
        for ((atual, tecla, esperado) in casos) {
            val b = BancadaDoPainel().comLinha()
            b.nucleo.restaurar(FotoDoCartao(cartao = atual))
            b.nucleo.navegar(tecla)
            val foto = b.nucleo.fotoAtual()
            assertEquals("$atual ${tecla.name}", esperado, foto.cartao)
            assertEquals(esperado, foto.alvoSintetico)
            assertEquals(b.relogio.agora(), foto.instanteSintetico)
            assertEquals(esperado == 3, foto.arDoApp)
            assertTrue(b.recebidos.isEmpty())
            b.linha.rodarPendentes()
            assertEquals(listOf<EventoDoPainel>(EventoDoPainel.CartaoMudou(esperado)), b.recebidos)
        }
    }

    @Test
    fun a6_desconhecido_com_tema_antigo_pergunta_ao_menu_antigo() {
        val b = BancadaDoPainel().comLinha()
        b.preferencias[Preferencias.ACTIVE_CUSTOM_THEME] = "sportred"
        b.menuAntigo.cartao = 1
        b.nucleo.restaurar(FotoDoCartao(cartao = 2))
        b.nucleo.navegar(TeclaDoVolante.RIGHT)
        assertEquals(3, b.nucleo.cartaoAtual)
        assertTrue(b.nucleo.fotoAtual().arDoApp)
    }

    @Test
    fun a6_desconhecido_sem_tema_antigo_conta_como_0() {
        val b = BancadaDoPainel().comLinha()
        b.preferencias[Preferencias.ACTIVE_CUSTOM_THEME] = "OutroTema"
        b.menuAntigo.cartao = 1
        b.nucleo.restaurar(FotoDoCartao(cartao = 2))
        b.nucleo.navegar(TeclaDoVolante.LEFT)
        assertEquals(3, b.nucleo.cartaoAtual)
    }

    @Test
    fun a6_no_anel_nao_pergunta_ao_menu_antigo() {
        val b = BancadaDoPainel().comLinha()
        b.preferencias[Preferencias.ACTIVE_CUSTOM_THEME] = "SportRedLite"
        b.menuAntigo.cartao = 3
        b.nucleo.restaurar(FotoDoCartao(cartao = 1))
        b.nucleo.navegar(TeclaDoVolante.RIGHT)
        assertEquals(3, b.nucleo.cartaoAtual)
        b.nucleo.navegar(TeclaDoVolante.RIGHT)
        assertEquals(0, b.nucleo.cartaoAtual)
        assertFalse(b.nucleo.fotoAtual().arDoApp)
    }

    @Test
    fun a6_o_eco_do_alvo_e_aceito_e_outro_e_ignorado() {
        val b = BancadaDoPainel().comLinha()
        b.nucleo.teclaDoPainelRecebida(TeclaDoVolante.RIGHT)
        b.nucleo.navegar(TeclaDoVolante.RIGHT) // 0 → 1
        b.linha.avancar(300)
        b.nucleo.mensagemRecebida(133, 3) // eco atrasado de outro cartão: regra 3
        assertEquals(1, b.nucleo.cartaoAtual)
        b.nucleo.mensagemRecebida(133, 1) // o eco do alvo: mesmo cartão (regra 1), nada muda
        assertEquals(1, b.nucleo.cartaoAtual)
        b.linha.rodarPendentes()
        assertEquals(listOf<EventoDoPainel>(EventoDoPainel.CartaoMudou(1)), b.recebidos)
    }

    @Test
    fun a7_soltar_a_posse() {
        val b = BancadaDoPainel()
        b.nucleo.navegar(TeclaDoVolante.LEFT) // 0 → 3: o app é dono do ar
        assertTrue(b.nucleo.fotoAtual().arDoApp)
        b.nucleo.soltarPosseDoAr()
        val foto = b.nucleo.fotoAtual()
        assertFalse(foto.arDoApp)
        assertEquals(-1, foto.alvoSintetico)
        assertEquals(NUNCA, foto.instanteSintetico)
        assertEquals(3, foto.cartao)
    }

    @Test
    fun b6_tema_esportivo_antigo() {
        for (t in listOf("SportRed", "sportred", "SPORTREDLITE", "SportRedLite")) assertTrue(RegrasDoMotorDoPainel.temaEsportivoAntigo(t))
        for (t in listOf(null, "", "Default", "SportRed2", " SportRed")) assertFalse(RegrasDoMotorDoPainel.temaEsportivoAntigo(t))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
