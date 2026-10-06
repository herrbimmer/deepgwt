// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.midia

import br.com.deepgwt.midia.RadioNowPlaying
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O primeiro teste é o defeito: com o rádio tocando, o indicador de mídia do painel ficava vazio e
 * o HUD desenhava o vetor cru do barramento na tela.
 */
class RadioNowPlayingTest {

    /** Exatamente o que a captura do carro traz, com o rádio em 103,9 FM. */
    private val CANAL_DO_CARRO = "{103900,0,1,1}"

    @Test
    fun `a estacao do carro vira texto legivel`() {
        assertEquals("FM 103,9", RadioNowPlaying.titulo(CANAL_DO_CARRO))
        assertEquals("FM 103,9 MHz", RadioNowPlaying.linhaUnica(CANAL_DO_CARRO))
        assertEquals(RadioNowPlaying.RODAPE_PADRAO, RadioNowPlaying.subtitulo(CANAL_DO_CARRO))
    }

    @Test
    fun `a frequencia sai do primeiro campo do vetor, nao de outro`() {
        // {103900,0,1,1}: se alguém ler o vetor inteiro, o zero do segundo campo vira "FM 0,0".
        val estacao = RadioNowPlaying.estacao(CANAL_DO_CARRO)!!
        assertEquals("103,9", estacao.numero)
        assertEquals("FM", estacao.banda)
        assertEquals("MHz", estacao.unidade)
    }

    @Test
    fun `a banda vem da faixa da frequencia, e nao do campo de banda`() {
        // O campo de banda é `0` com FM tocando; sem AM nem DAB na captura não dá para confirmar a
        // codificação dele. A faixa, sim, é verificável sozinha.
        assertEquals("FM", RadioNowPlaying.estacao("{92900,0,1,1}")!!.banda)
        assertEquals("FM", RadioNowPlaying.estacao("{107500,3,0,0}")!!.banda)
        assertEquals("AM", RadioNowPlaying.estacao("{1030,0,1,1}")!!.banda)
        assertEquals("1030 kHz", RadioNowPlaying.estacao("{1030,0,1,1}")!!.rotulo.removePrefix("AM "))
    }

    @Test
    fun `frequencia fora das faixas conhecidas nao inventa banda`() {
        val estacao = RadioNowPlaying.estacao("{225648,0,1,1}")!!
        assertEquals(RadioNowPlaying.RODAPE_PADRAO, estacao.banda)
        assertEquals("225648", estacao.numero)
        assertEquals("", estacao.unidade)
    }

    @Test
    fun `vetor sem estacao devolve nada, e nao FM 0,0`() {
        assertNull(RadioNowPlaying.estacao("{}"))
        assertNull(RadioNowPlaying.estacao("{0,0,0,0}"))
        assertNull(RadioNowPlaying.estacao(""))
        assertNull(RadioNowPlaying.estacao(null))
        // Lista de favoritos vazia; não é canal, e o -1 não pode virar frequência.
        assertNull(RadioNowPlaying.estacao("{-1,-1,-1,-1,-1,-1}"))
        assertEquals("", RadioNowPlaying.titulo("{}"))
        assertEquals("", RadioNowPlaying.linhaUnica(null))
    }

    @Test
    fun `o arredondamento para MHz e o mais proximo, nao o truncado`() {
        assertEquals("103,9", RadioNowPlaying.estacao("{103900,0,1,1}")!!.numero)
        assertEquals("87,9", RadioNowPlaying.estacao("{87900,0,1,1}")!!.numero)
        assertEquals("100,0", RadioNowPlaying.estacao("{100000,0,1,1}")!!.numero)
        assertEquals("89,1", RadioNowPlaying.estacao("{89050,0,1,1}")!!.numero)
    }

    @Test
    fun `o estado de tocando e o mesmo que a barra inferior ja usava`() {
        assertTrue(RadioNowPlaying.tocando("1"))
        assertTrue(RadioNowPlaying.tocando("playing"))
        assertTrue(RadioNowPlaying.tocando("PLAY"))
        assertFalse(RadioNowPlaying.tocando("0"))
        assertFalse(RadioNowPlaying.tocando(""))
        assertFalse(RadioNowPlaying.tocando(null))
    }

    @Test
    fun `o nome do RDS so e aceito quando parece nome`() {
        // Vem vazio neste carro e o formato é desconhecido; um vetor de números NÃO é um nome.
        assertNull(RadioNowPlaying.nomeRds(""))
        assertNull(RadioNowPlaying.nomeRds(null))
        assertNull(RadioNowPlaying.nomeRds("{}"))
        assertNull(RadioNowPlaying.nomeRds("{103900,0,1,1}"))
        assertEquals("Radio Cidade", RadioNowPlaying.nomeRds("{Radio Cidade,103900}"))
    }

    @Test
    fun `com nome de RDS a frequencia desce para a segunda linha`() {
        val rds = "{Radio Cidade,103900}"
        assertEquals("Radio Cidade", RadioNowPlaying.titulo(CANAL_DO_CARRO, rds))
        assertEquals("FM 103,9 MHz", RadioNowPlaying.subtitulo(CANAL_DO_CARRO, rds))
        assertEquals("Radio Cidade · FM 103,9 MHz", RadioNowPlaying.linhaUnica(CANAL_DO_CARRO, rds))
    }

    @Test
    fun `as duas linhas nunca repetem o mesmo texto`() {
        // Sem RDS a de cima é a estação, então a de baixo tem de ser outra coisa.
        assertTrue(RadioNowPlaying.titulo(CANAL_DO_CARRO) != RadioNowPlaying.subtitulo(CANAL_DO_CARRO))
        val rds = "{Radio Cidade,103900}"
        assertTrue(RadioNowPlaying.titulo(CANAL_DO_CARRO, rds) !=
                RadioNowPlaying.subtitulo(CANAL_DO_CARRO, rds))
    }

    @Test
    fun `de le exatamente as tres chaves do radio`() {
        val lidas = mutableListOf<String>()
        RadioNowPlaying.de { chave -> lidas.add(chave); null }
        assertEquals(RadioNowPlaying.CHAVES, lidas.toSet())
        assertEquals(3, lidas.size)
    }

    @Test
    fun `a leitura completa do carro responde tocando com a estacao`() {
        val leitura =
                RadioNowPlaying.Leitura(canal = CANAL_DO_CARRO, rds = "", estado = "1")
        assertTrue(leitura.estaTocando)
        assertEquals("FM 103,9", leitura.titulo)
        assertEquals("FM 103,9 MHz", leitura.linhaUnica)
    }

    @Test
    fun `radio parado ainda sabe a estacao, e quem chama decide esconder`() {
        // O canal fica no cache com o rádio desligado. A política não mente sobre ele; o HUD e o
        // painel é que consultam `estaTocando` antes de mostrar.
        val leitura =
                RadioNowPlaying.Leitura(canal = CANAL_DO_CARRO, rds = null, estado = "0")
        assertFalse(leitura.estaTocando)
        assertEquals("FM 103,9", leitura.titulo)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
