package br.com.opengw.veiculo.volante

import br.com.opengw.veiculo.painel.EventoDoPainel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B.3 de `motor-painel-e-volante.md`: modo mãos livres. */
class MaosLivresTest {

    private fun BancadaDoVolante.saidas() = chamadas.filter { it.startsWith("maosLivres:") || it.startsWith("dpad:") }

    @Test
    fun segurar_ok_alterna() {
        val b = BancadaDoVolante()
        assertFalse(b.nucleo.maosLivresAtivo)
        assertTrue(b.apertou(1037))
        assertTrue(b.nucleo.maosLivresAtivo)
        assertEquals(listOf("maosLivres:true"), b.saidas())
        assertEquals(listOf<EventoDoPainel>(EventoDoPainel.MaosLivresMudou(true)), b.recebidos)
    }

    @Test
    fun dentro_de_800_ms_consome_sem_alternar() {
        val b = BancadaDoVolante()
        b.apertou(1037)
        b.relogio.andar(800)
        assertTrue(b.apertou(1037))
        assertTrue(b.nucleo.maosLivresAtivo)
        b.relogio.andar(1)
        assertTrue(b.apertou(1037))
        assertFalse(b.nucleo.maosLivresAtivo)
        assertEquals(listOf("maosLivres:true", "maosLivres:false"), b.saidas())
        assertEquals(
            listOf<EventoDoPainel>(EventoDoPainel.MaosLivresMudou(true), EventoDoPainel.MaosLivresMudou(false)),
            b.recebidos,
        )
    }

    @Test
    fun dv1_segurar_ok_soltou_nao_alterna() {
        val b = BancadaDoVolante()
        assertFalse(b.soltou(1037))
        assertFalse(b.nucleo.maosLivresAtivo)
    }

    @Test
    fun setas_viram_dpad_so_com_o_modo_ativo_e_apertou() {
        val b = BancadaDoVolante()
        assertFalse("inativo: segue", b.apertou(1024))
        assertTrue(b.saidas().isEmpty())
        b.apertou(1037)
        b.chamadas.clear()
        val esperado = mapOf(1024 to 19, 1025 to 20, 1026 to 21, 1027 to 22, 1028 to 23)
        for ((codigo, dpad) in esperado) {
            b.chamadas.clear()
            assertTrue(b.apertou(codigo))
            assertEquals(listOf("dpad:$dpad"), b.saidas())
        }
        b.chamadas.clear()
        assertFalse("soltou não navega", b.soltou(1024))
        assertFalse("HOME não é direção", b.apertou(1029))
        assertTrue(b.saidas().isEmpty())
    }

    @Test
    fun hud_vem_antes_das_maos_livres() {
        val b = BancadaDoVolante()
        b.consome["hud"] = true
        assertTrue(b.apertou(1037))
        assertFalse(b.nucleo.maosLivresAtivo)
        assertTrue(b.recebidos.isEmpty())
    }

    @Test
    fun saida_que_lanca_nao_derruba() {
        val b = BancadaDoVolante()
        b.nucleo.maosLivres = object : SaidaDasMaosLivres {
            override fun mudou(ativo: Boolean) = throw IllegalStateException("barra quebrada")
            override fun teclaDeDirecao(codigoAndroid: Int) = throw IllegalStateException("atividade quebrada")
        }
        assertTrue(b.apertou(1037))
        assertTrue(b.nucleo.maosLivresAtivo)
        assertEquals(listOf<EventoDoPainel>(EventoDoPainel.MaosLivresMudou(true)), b.recebidos)
        assertTrue(b.apertou(1025))
        assertEquals(2, b.registro.erros.size)
    }
}
