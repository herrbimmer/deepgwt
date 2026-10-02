package br.com.opengw.luz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alpha 9.5: a luz ambiente não pisca mais com a seta, e cada reação é escolhida. */
class ReacoesDaLuzTest {

    private val padrao = ReacaoDaLuz.ligadas { _, p -> p }

    private fun sinais(
        pisca: Boolean = false,
        cego: Boolean = false,
        seta: Boolean = false,
        farol: Boolean = false,
        sport: Boolean = false
    ) = SinaisDaLuz(pisca, cego, seta, farol, sport)

    @Test
    fun `a seta vem desligada e as outras reacoes ligadas`() {
        assertFalse(ReacaoDaLuz.SETA in padrao)
        assertTrue(padrao.containsAll(ReacaoDaLuz.entries - ReacaoDaLuz.SETA))
    }

    @Test
    fun `com o padrao, dar seta nao muda a luz`() {
        assertEquals(CamadaDaLuz.NENHUMA, DecisaoDaLuz.camada(sinais(seta = true), padrao))
    }

    @Test
    fun `pisca-alerta pulsa mesmo com as setas acesas junto`() {
        assertEquals(CamadaDaLuz.PISCA_ALERTA, DecisaoDaLuz.camada(sinais(pisca = true, seta = true), padrao))
    }

    @Test
    fun `pisca-alerta desligado nas reacoes nao deixa a seta pulsar no lugar dele`() {
        val reacoes = ReacaoDaLuz.entries.toSet() - ReacaoDaLuz.PISCA_ALERTA
        assertEquals(CamadaDaLuz.NENHUMA, DecisaoDaLuz.camada(sinais(pisca = true, seta = true), reacoes))
    }

    @Test
    fun `seta so pulsa quando a pessoa liga a reacao`() {
        val reacoes = padrao + ReacaoDaLuz.SETA
        assertEquals(CamadaDaLuz.SETA, DecisaoDaLuz.camada(sinais(seta = true), reacoes))
        assertEquals(CamadaDaLuz.NENHUMA, DecisaoDaLuz.camada(sinais(seta = false), reacoes))
    }

    @Test
    fun `ponto cego vence a seta e perde para o pisca-alerta`() {
        val tudo = ReacaoDaLuz.entries.toSet()
        assertEquals(CamadaDaLuz.PONTO_CEGO, DecisaoDaLuz.camada(sinais(cego = true, seta = true), tudo))
        assertEquals(CamadaDaLuz.PISCA_ALERTA, DecisaoDaLuz.camada(sinais(pisca = true, cego = true), tudo))
    }

    @Test
    fun `farol pinta ambar fora do Sport e respeita o interruptor`() {
        assertEquals(CamadaDaLuz.FAROL, DecisaoDaLuz.camada(sinais(farol = true), padrao))
        assertEquals(CamadaDaLuz.NENHUMA, DecisaoDaLuz.camada(sinais(farol = true, sport = true), padrao))
        assertEquals(CamadaDaLuz.NENHUMA, DecisaoDaLuz.camada(sinais(farol = true), padrao - ReacaoDaLuz.FAROL))
    }

    @Test
    fun `sem a cor do modo, o Sport nao tira o ambar do farol`() {
        val semModo = padrao - ReacaoDaLuz.MODO_DE_CONDUCAO
        assertEquals(CamadaDaLuz.FAROL, DecisaoDaLuz.camada(sinais(farol = true, sport = true), semModo))
    }

    @Test
    fun `cada reacao le a propria preferencia`() {
        val gravado = mapOf(ReacaoDaLuz.SETA.chave.chave to true, ReacaoDaLuz.PISCA_ALERTA.chave.chave to false)
        val lidas = ReacaoDaLuz.ligadas { chave, p -> gravado[chave] ?: p }
        assertTrue(ReacaoDaLuz.SETA in lidas)
        assertFalse(ReacaoDaLuz.PISCA_ALERTA in lidas)
        assertTrue(ReacaoDaLuz.FAROL in lidas)
    }
}
