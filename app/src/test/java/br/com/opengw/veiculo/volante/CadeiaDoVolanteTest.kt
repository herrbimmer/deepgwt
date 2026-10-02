package br.com.opengw.veiculo.volante

import br.com.opengw.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B.2, B.7 e B.9 de `motor-painel-e-volante.md`: a cadeia de teclas. */
class CadeiaDoVolanteTest {

    private val todos = listOf("coringas:1024/1/ev1024", "diag:1024", "aa:1024/1", "hud:1024/1/ev1024", "circulo:1024/1/ev1024")

    @Test
    fun b2_ordem_sem_ninguem_consumir() {
        val b = BancadaDoVolante()
        assertFalse(b.soltou(1024))
        assertEquals(todos, b.chamadas)
    }

    @Test
    fun b2_o_primeiro_que_consome_encerra() {
        val casos = listOf("coringas" to 1, "aa" to 3, "hud" to 4, "circulo" to 5)
        for ((quem, ate) in casos) {
            val b = BancadaDoVolante()
            if (quem == "aa") b.painel.projecao.trataTecla = true else b.consome[quem] = true
            assertTrue(quem, b.soltou(1024))
            assertEquals(quem, todos.take(ate), b.chamadas)
        }
    }

    @Test
    fun b2_excecao_num_tratador_nao_corta() {
        val b = BancadaDoVolante()
        b.lanca += "coringas"
        b.lanca += "hud"
        b.consome["circulo"] = true
        assertTrue(b.soltou(1024))
        assertEquals(todos, b.chamadas)
        assertEquals(2, b.registro.erros.size)
    }

    @Test
    fun b2_excecao_na_midia_do_android_auto_nao_corta() {
        val b = BancadaDoVolante()
        b.painel.projecao.lancar = true
        b.consome["hud"] = true
        assertTrue(b.soltou(1024))
        assertEquals(todos.take(4), b.chamadas)
    }

    @Test
    fun b2_midia_do_android_auto_respeita_a_preferencia() {
        val b = BancadaDoVolante()
        b.painel.projecao.trataTecla = true
        b.preferencias[Preferencias.ENABLE_STEERING_WHEEL_MEDIA_CONTROL] = false
        assertFalse(b.soltou(1024))
        assertEquals(todos - "aa:1024/1", b.chamadas)
        // Padrão (ausente) = ligado.
        b.preferencias.booleanos.clear()
        b.chamadas.clear()
        assertTrue(b.soltou(1024))
        assertEquals(todos.take(3), b.chamadas)
    }

    @Test
    fun b2_diagnostico_so_registra() {
        val b = BancadaDoVolante()
        b.painel.projecao.teclasComDiagnostico += 85
        assertFalse(b.soltou(85))
        assertTrue(b.registro.infos.any { "diagnóstico" in it && "85" in it })
    }

    @Test
    fun b2_o_evento_chega_aos_tratadores() {
        val b = BancadaDoVolante()
        b.tecla(517, NucleoDoVolante.ACAO_APERTOU)
        assertTrue(b.chamadas.contains("coringas:517/0/ev517"))
    }

    @Test
    fun b7_engasgo_acima_de_15_ms() {
        val b = BancadaDoVolante()
        b.custo["hud"] = 15
        b.soltou(1025)
        assertTrue(b.registro.erros.isEmpty())
        b.custo["hud"] = 16
        b.soltou(1025)
        assertEquals(1, b.registro.erros.size)
        assertTrue(b.registro.erros.single().contains("1025"))
    }

    @Test
    fun b9_injetar_nao_mede_engasgo() {
        val b = BancadaDoVolante()
        b.custo["coringas"] = 100
        b.tecla(1025, NucleoDoVolante.ACAO_SOLTOU, doServico = false)
        assertTrue(b.registro.erros.isEmpty())
    }

    @Test
    fun b9_a_cadeia_inteira_devolve_se_alguem_consumiu() {
        val b = BancadaDoVolante().comMenu()
        assertTrue("menu do painel consome tecla do mapa", b.soltou(1028))
        assertFalse("tecla fora do mapa segue", b.soltou(1031))
        assertTrue("mãos livres", b.apertou(1037))
    }
}
