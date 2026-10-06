package br.com.deepgwt.ajustes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasDosAtalhosTest {

    @Test
    fun `lista vai e volta do JSON na mesma ordem`() {
        val ids = listOf("disableAvas", "painel.circulo", "hudOverlayEnabled")
        assertEquals(ids, RegrasDosAtalhos.ler(RegrasDosAtalhos.escrever(ids)))
    }

    @Test
    fun `texto ausente, vazio ou lixo viram lista vazia`() {
        assertEquals(emptyList<String>(), RegrasDosAtalhos.ler(null))
        assertEquals(emptyList<String>(), RegrasDosAtalhos.ler(""))
        assertEquals(emptyList<String>(), RegrasDosAtalhos.ler("não é json"))
        assertEquals(emptyList<String>(), RegrasDosAtalhos.ler("{\"a\":1}"))
    }

    @Test
    fun `repetidos e em branco caem fora na leitura`() {
        assertEquals(listOf("a", "b"), RegrasDosAtalhos.ler("[\"a\",\"\",\"b\",\"a\"]"))
    }

    @Test
    fun `adicionar nao repete e respeita o limite`() {
        assertEquals(listOf("a", "b"), RegrasDosAtalhos.adicionar(listOf("a"), "b"))
        assertEquals(listOf("a"), RegrasDosAtalhos.adicionar(listOf("a"), "a"))
        val cheia = (1..RegrasDosAtalhos.LIMITE).map { "id$it" }
        assertEquals(cheia, RegrasDosAtalhos.adicionar(cheia, "novo"))
    }

    @Test
    fun `remover tira so o pedido`() {
        assertEquals(listOf("a", "c"), RegrasDosAtalhos.remover(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `o pacote falso do atalho vai e volta`() {
        val pacote = RegrasDosAtalhos.pacoteDe("disableAvas")
        assertTrue(RegrasDosAtalhos.ehAtalho(pacote))
        assertEquals("disableAvas", RegrasDosAtalhos.idDe(pacote))
        assertFalse(RegrasDosAtalhos.ehAtalho("com.google.android.apps.maps"))
        assertFalse(RegrasDosAtalhos.ehAtalho(RegrasDosAtalhos.PREFIXO))
        assertFalse(RegrasDosAtalhos.ehAtalho(null))
    }
}
