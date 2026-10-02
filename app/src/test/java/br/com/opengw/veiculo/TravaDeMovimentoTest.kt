package br.com.opengw.veiculo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Trava de movimento (Alpha 8.5, auditoria B3 porta-malas, B5 reinício pela rede, B6 fechar ao desligar). */
class TravaDeMovimentoTest {

    @Test
    fun `parado e andando`() {
        val t = TravaDeMovimento
        assertNull(t.avaliar("0", "3", exigirP = true, exigirLeitura = true))
        assertNull("0,4 km/h de oscilação ainda é parado", t.avaliar("0.4", null, exigirP = false, exigirLeitura = true))
        assertEquals(TravaDeMovimento.Motivo.ANDANDO, t.avaliar("12", "3", exigirP = false, exigirLeitura = false))
        assertEquals(TravaDeMovimento.Motivo.ANDANDO, t.avaliar("2,5", null, exigirP = false, exigirLeitura = false))
    }

    @Test
    fun `P so quando pedido e lido`() {
        val t = TravaDeMovimento
        assertEquals(TravaDeMovimento.Motivo.FORA_DE_P, t.avaliar("0", "1", exigirP = true, exigirLeitura = true))
        assertNull(t.avaliar("0", "P", exigirP = true, exigirLeitura = true))
        assertNull("marcha ilegível não trava", t.avaliar("0", "", exigirP = true, exigirLeitura = true))
        assertNull("sem exigir P, marcha não importa", t.avaliar("0", "1", exigirP = false, exigirLeitura = true))
    }

    @Test
    fun `sem leitura de velocidade`() {
        val t = TravaDeMovimento
        assertEquals("porta-malas exige leitura", TravaDeMovimento.Motivo.SEM_LEITURA, t.avaliar(null, "3", exigirP = true, exigirLeitura = true))
        assertEquals(TravaDeMovimento.Motivo.SEM_LEITURA, t.avaliar("abc", "3", exigirP = false, exigirLeitura = true))
        assertNull("reiniciar é o remédio quando o carro não responde", t.avaliar(null, null, exigirP = false, exigirLeitura = false))
    }
}
