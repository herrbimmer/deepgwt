// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.volante

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.painel.TeclaDoVolante
import br.com.deepgwt.veiculo.painel.EventoDoPainel
import br.com.deepgwt.veiculo.painel.FotoDoCartao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B.4, B.5, B.6 de `motor-painel-e-volante.md` e a ligação com o árbitro (A.5/A.6). */
class MenuDoPainelTest {

    private fun BancadaDoVolante.paraOTema() = recebidos.filterIsInstance<EventoDoPainel.TeclaParaOTema>().map { it.tecla }

    @Test
    fun b4_mapa() {
        val mapa = mapOf(
            1024 to TeclaDoVolante.UP, 1025 to TeclaDoVolante.DOWN, 1026 to TeclaDoVolante.LEFT,
            1027 to TeclaDoVolante.RIGHT, 1028 to TeclaDoVolante.ENTER, 1029 to TeclaDoVolante.HOME,
            1030 to TeclaDoVolante.BACK, 1033 to TeclaDoVolante.UP_LONG, 1034 to TeclaDoVolante.DOWN_LONG,
            1037 to TeclaDoVolante.ENTER_LONG, 1039 to TeclaDoVolante.BACK_LONG,
        )
        for ((codigo, tecla) in mapa) {
            val b = BancadaDoVolante().comMenu()
            assertTrue(b.soltou(codigo))
            assertEquals(EventoDoPainel.TeclaRecebida(tecla, codigo, 1), b.recebidos.first())
        }
        for (codigo in listOf(1031, 1032, 1035, 1036, 1038, 24, 25, 85, 87, 88, 517)) {
            val b = BancadaDoVolante().comMenu()
            assertFalse("$codigo", b.soltou(codigo))
            assertTrue("$codigo", b.recebidos.isEmpty())
        }
    }

    @Test
    fun b4_desligado_nao_faz_nada() {
        val b = BancadaDoVolante()
        assertFalse(b.soltou(1028))
        assertTrue(b.recebidos.isEmpty())
        assertEquals(null, b.painel.nucleo.fotoAtual().ultimaTecla)
    }

    @Test
    fun b4_apertou_so_publica_tecla_recebida_e_guarda_a_ultima_tecla() {
        val b = BancadaDoVolante().comMenu()
        assertTrue(b.apertou(1028))
        assertEquals(listOf<EventoDoPainel>(EventoDoPainel.TeclaRecebida(TeclaDoVolante.ENTER, 1028, 0)), b.recebidos)
        val foto = b.painel.nucleo.fotoAtual()
        assertEquals(TeclaDoVolante.ENTER, foto.ultimaTecla)
        assertEquals(b.relogio.agora(), foto.instanteDaTecla)
        assertEquals(listOf("tecla_do_painel"), b.painel.eventosDoDiario())
        assertEquals(1028, b.painel.diario.single().second["codigo"])
    }

    @Test
    fun b4_duplicada_em_220_ms_e_ignorada_e_221_passa() {
        val b = BancadaDoVolante().comMenu()
        b.soltou(1028)
        b.relogio.andar(220)
        b.soltou(1028)
        assertEquals(listOf(TeclaDoVolante.ENTER), b.paraOTema())
        assertTrue(b.painel.eventosDoDiario().contains("tecla_do_painel_duplicada"))
        b.relogio.andar(1) // 221 desde a última tratada
        b.soltou(1028)
        assertEquals(listOf(TeclaDoVolante.ENTER, TeclaDoVolante.ENTER), b.paraOTema())
        // Todas foram "recebidas" (inclusive a duplicada).
        assertEquals(3, b.recebidos.count { it is EventoDoPainel.TeclaRecebida })
    }

    @Test
    fun b4_outra_tecla_nao_e_duplicada() {
        val b = BancadaDoVolante().comMenu()
        b.soltou(1024)
        b.relogio.andar(10)
        b.soltou(1025)
        assertEquals(listOf(TeclaDoVolante.UP, TeclaDoVolante.DOWN), b.paraOTema())
    }

    @Test
    fun b4_voltar_dispensa_aviso_e_vai_ao_tema() {
        val b = BancadaDoVolante().comMenu()
        b.soltou(1030)
        assertEquals(
            listOf(
                EventoDoPainel.TeclaRecebida(TeclaDoVolante.BACK, 1030, 1),
                EventoDoPainel.DispensarAviso,
                EventoDoPainel.TeclaParaOTema(TeclaDoVolante.BACK),
            ),
            b.recebidos,
        )
    }

    @Test
    fun b4_home_e_voltar_soltam_a_posse_do_ar() {
        for (codigo in listOf(1029, 1030)) {
            val b = BancadaDoVolante().comMenu()
            b.painel.nucleo.restaurar(FotoDoCartao(cartao = 3, alvoSintetico = 3, instanteSintetico = b.relogio.agora(), arDoApp = true))
            b.soltou(codigo)
            val foto = b.painel.nucleo.fotoAtual()
            assertFalse("$codigo", foto.arDoApp)
            assertEquals(-1, foto.alvoSintetico)
        }
        // ENTER não solta.
        val b = BancadaDoVolante().comMenu()
        b.painel.nucleo.restaurar(FotoDoCartao(cartao = 3, alvoSintetico = 3, instanteSintetico = b.relogio.agora(), arDoApp = true))
        b.soltou(1028)
        assertTrue(b.painel.nucleo.fotoAtual().arDoApp)
    }

    @Test
    fun b5_esquerda_e_direita_nunca_vao_ao_tema() {
        val b = BancadaDoVolante().comMenu()
        b.soltou(1027) // 0 → 1
        b.relogio.andar(300)
        b.soltou(1027) // 1 → 3
        b.relogio.andar(300)
        b.soltou(1026) // 3 → 1
        assertTrue(b.paraOTema().isEmpty())
        assertEquals(1, b.painel.nucleo.cartaoAtual)
        assertEquals(
            listOf(1, 3, 1),
            b.recebidos.filterIsInstance<EventoDoPainel.CartaoMudou>().map { it.cartao },
        )
    }

    @Test
    fun b6_tema_antigo_desvia_para_o_menu_antigo() {
        val b = BancadaDoVolante().comMenu()
        b.preferencias[Preferencias.ACTIVE_CUSTOM_THEME] = "SportRedLite"
        b.soltou(1028)
        b.relogio.andar(300)
        b.soltou(1030)
        assertEquals(listOf("ENTER", "BACK"), b.painel.menuAntigo.teclas)
        assertTrue(b.paraOTema().isEmpty())
        assertTrue(b.recebidos.contains(EventoDoPainel.DispensarAviso))
    }

    @Test
    fun a5_o_arbitro_usa_os_tempos_guardados_pelo_volante() {
        val b = BancadaDoVolante().comMenu()
        // O carro sobe o ar sozinho sobre o cartão dele: ignorado.
        b.painel.nucleo.mensagemRecebida(133, 3)
        assertEquals(0, b.painel.nucleo.cartaoAtual)
        // → antecipa 0 → 1; o eco atrasado de outro cartão é ignorado, o do alvo não muda nada.
        b.soltou(1027)
        b.relogio.andar(616)
        b.painel.nucleo.mensagemRecebida(133, 3)
        assertEquals(1, b.painel.nucleo.cartaoAtual)
        // Passada a janela do eco sintético e da navegação, o carro volta a mandar.
        b.relogio.andar(2_000)
        b.painel.nucleo.mensagemRecebida(133, 3)
        assertEquals(3, b.painel.nucleo.cartaoAtual)
        // ↓ no cartão do ar: o carro não tira o cartão por 3,5 s.
        b.soltou(1025)
        b.relogio.andar(282)
        b.painel.nucleo.mensagemRecebida(133, 1)
        assertEquals(3, b.painel.nucleo.cartaoAtual)
        // VOLTAR: saída intencional, aceita.
        b.soltou(1030)
        b.relogio.andar(200)
        b.painel.nucleo.mensagemRecebida(133, 1)
        assertEquals(1, b.painel.nucleo.cartaoAtual)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
