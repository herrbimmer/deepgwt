package br.com.deepgwt.tela3d.midia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V10: quando o visualizador de áudio é desejado e o que a política faz. */
class PoliticaDoVisualizadorTest {

    private class Alvo : AlvoDoVisualizador {
        val ordens = ArrayList<Boolean>()
        override fun desejar(sim: Boolean) {
            ordens += sim
        }
    }

    private class Cenario(
        var permissao: Boolean = true,
        var musicaAtiva: () -> Boolean = { false },
    ) {
        val alvo = Alvo()
        val registros = ArrayList<String>()
        val nucleo = NucleoDaPoliticaDoVisualizador(alvo, { permissao }, { musicaAtiva() }, { registros += it })
    }

    @Test
    fun `v10 decisao`() {
        assertTrue(DecisaoDoVisualizador.desejado(payloadTocando = true, musicaAtiva = false))
        assertTrue(DecisaoDoVisualizador.desejado(payloadTocando = false, musicaAtiva = true))
        assertFalse(DecisaoDoVisualizador.desejado(payloadTocando = false, musicaAtiva = false))
    }

    @Test
    fun `v10 tocando no payload liga`() {
        val c = Cenario()
        c.nucleo.definirTocando(true)
        assertTrue(c.nucleo.avaliar())
        assertEquals(listOf(true), c.alvo.ordens)
    }

    @Test
    fun `v10 musica ativa no sistema liga mesmo sem payload tocando`() {
        val c = Cenario(musicaAtiva = { true })
        assertTrue(c.nucleo.avaliar())
        assertEquals(listOf(true), c.alvo.ordens)
    }

    @Test
    fun `v10 nada tocando desliga`() {
        val c = Cenario()
        assertFalse(c.nucleo.avaliar())
        assertEquals(listOf(false), c.alvo.ordens)
    }

    @Test
    fun `v10 erro ao perguntar ao sistema vale falso`() {
        val c = Cenario(musicaAtiva = { throw IllegalStateException("sem audio service") })
        assertFalse(c.nucleo.avaliar())
        c.nucleo.definirTocando(true)
        assertTrue(c.nucleo.avaliar()) // o payload ainda liga
    }

    @Test
    fun `v10 sem permissao nao liga e registra uma unica vez`() {
        val c = Cenario(permissao = false)
        c.nucleo.definirTocando(true)
        repeat(5) { c.nucleo.avaliar() }
        assertTrue(c.alvo.ordens.isEmpty())
        assertEquals(1, c.registros.size)
    }

    @Test
    fun `v10 permissao concedida depois passa a funcionar`() {
        val c = Cenario(permissao = false)
        c.nucleo.definirTocando(true)
        c.nucleo.avaliar()
        c.permissao = true
        c.nucleo.avaliar()
        assertEquals(listOf(true), c.alvo.ordens)
    }

    @Test
    fun `parou de tocar desliga`() {
        val c = Cenario()
        c.nucleo.definirTocando(true)
        c.nucleo.avaliar()
        c.nucleo.definirTocando(false)
        c.nucleo.avaliar()
        assertEquals(listOf(true, false), c.alvo.ordens)
    }
}
