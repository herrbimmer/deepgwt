package br.com.deepgwt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Cronometro0a100Test {

    @Test
    fun `a corrida completa grava o tempo e a amostra seguinte nao o sobrescreve`() {
        val c = Cronometro0a100()
        c.amostra(0f, 0)
        c.amostra(3f, 100)
        c.amostra(60f, 3_000)
        c.amostra(100.4f, 6_100)
        assertEquals(6_000L, c.tempoMs)
        assertNull(c.inicioMs)

        // O carro continua acelerando: nada de corrida nova, nada de tempo novo.
        c.amostra(101f, 6_300)
        c.amostra(104f, 6_500)
        c.amostra(130f, 9_000)
        assertEquals(6_000L, c.tempoMs)
        assertNull(c.inicioMs)
    }

    @Test
    fun `abrir a tela ja em movimento nao cronometra`() {
        val c = Cronometro0a100()
        c.amostra(60f, 0)
        c.amostra(80f, 1_000)
        c.amostra(100f, 2_000)
        assertNull(c.tempoMs)
        assertNull(c.inicioMs)
    }

    @Test
    fun `depois de parar de novo a proxima corrida conta`() {
        val c = Cronometro0a100()
        c.amostra(0f, 0)
        c.amostra(2f, 10)
        c.amostra(100f, 5_010)
        assertEquals(5_000L, c.tempoMs)

        c.amostra(50f, 20_000)
        c.amostra(0.2f, 30_000)
        c.amostra(5f, 31_000)
        assertNotNull(c.inicioMs)
        c.amostra(100f, 35_000)
        assertEquals(4_000L, c.tempoMs)
    }

    @Test
    fun `parar no meio cancela sem gravar tempo`() {
        val c = Cronometro0a100()
        c.amostra(0f, 0)
        c.amostra(30f, 1_000)
        assertNotNull(c.inicioMs)
        c.amostra(0f, 2_000)
        assertNull(c.inicioMs)
        assertNull(c.tempoMs)
    }

    @Test
    fun `zerar andando nao arma uma corrida falsa`() {
        val c = Cronometro0a100()
        c.amostra(0f, 0)
        c.amostra(10f, 100)
        c.zerar()
        assertNull(c.inicioMs)
        c.amostra(40f, 500)
        c.amostra(100f, 4_000)
        assertNull("sem repouso depois do Zerar, nao ha corrida", c.tempoMs)
    }

    @Test
    fun `zerar com o carro parado deixa a proxima partida contar`() {
        val c = Cronometro0a100()
        c.amostra(0f, 0)
        c.zerar()
        c.amostra(5f, 1_000)
        assertNotNull(c.inicioMs)
        c.amostra(100f, 6_000)
        assertEquals(5_000L, c.tempoMs)
    }
}
