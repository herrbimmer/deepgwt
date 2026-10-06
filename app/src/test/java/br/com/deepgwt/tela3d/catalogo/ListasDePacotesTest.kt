package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListasDePacotesTest {

    @Test
    fun `leitura apara espacos e ignora vazios e repetidos`() {
        val lido = ListaDePacotes.ler(" com.a , ,com.b,com.a,, com.c ")
        assertEquals(listOf("com.a", "com.b", "com.c"), lido)
    }

    @Test
    fun `leitura de nulo vazio ou so virgulas`() {
        assertTrue(ListaDePacotes.ler(null).isEmpty())
        assertTrue(ListaDePacotes.ler("").isEmpty())
        assertTrue(ListaDePacotes.ler("  ").isEmpty())
        assertTrue(ListaDePacotes.ler(",,,").isEmpty())
    }

    @Test
    fun `leitura nao corta em 12`() {
        val texto = (1..20).joinToString(",") { "p$it" }
        assertEquals(20, ListaDePacotes.ler(texto).size)
    }

    @Test
    fun `escrita sem espacos e ordenada para conjuntos`() {
        assertEquals("com.spotify.music,com.waze", ListaDePacotes.escrever(listOf("com.spotify.music", "com.waze")))
        assertEquals("", ListaDePacotes.escrever(emptyList()))
        assertEquals("a,b,c", ListaDePacotes.escreverOrdenado(setOf("c", "a", "b")))
    }

    @Test
    fun `mru inserir 13 pacotes deixa 12 e o mais antigo sai`() {
        var lista = emptyList<String>()
        for (i in 1..13) lista = RecentesMru.inserir(lista, "p$i")
        assertEquals(12, lista.size)
        assertEquals("p13", lista.first())
        assertEquals("p2", lista.last())
        assertTrue("p1" !in lista)
    }

    @Test
    fun `mru reinserir existente vai para a posicao 0 sem repetir`() {
        var lista = emptyList<String>()
        for (i in 1..5) lista = RecentesMru.inserir(lista, "p$i")
        lista = RecentesMru.inserir(lista, "p2")
        assertEquals(listOf("p2", "p5", "p4", "p3", "p1"), lista)
    }

    @Test
    fun `mru reinserir com a lista cheia nao descarta ninguem`() {
        var lista = emptyList<String>()
        for (i in 1..12) lista = RecentesMru.inserir(lista, "p$i")
        lista = RecentesMru.inserir(lista, "p1")
        assertEquals(12, lista.size)
        assertEquals("p1", lista.first())
        assertEquals("p2", lista.last())
    }

    @Test
    fun `atalhos sao os tres primeiros instalados e permitidos`() {
        val mru = listOf("a", "b", "c", "d", "e")
        val instalados = setOf("a", "c", "d", "e")
        val r = RecentesMru.atalhos(mru, instalados) { it != "c" }
        assertEquals(listOf("a", "d", "e"), r)
    }

    @Test
    fun `semente para em tres e nao repete`() {
        val candidatos = listOf("a", "a", "x", "b", "c", "d")
        val r = RecentesMru.semear(candidatos, setOf("a", "b", "c", "d")) { true }
        assertEquals(listOf("a", "b", "c"), r)
    }
}
