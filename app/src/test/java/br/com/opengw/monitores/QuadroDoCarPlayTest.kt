package br.com.opengw.monitores

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formato do quadro (B.2), a capa (B.5) e a ligação em duas tentativas (B3), sem Android. */
class QuadroDoCarPlayTest {

    private fun escreverObjeto(
        p: ParcelFalso,
        titulo: String? = "Faixa",
        artista: String? = "Banda",
        duracao: Long = 200_000,
        caminho: String? = "/sdcard/capa.jpg",
        estado: Int = 1,
        posicao: Long = 12_000,
        aleatorio: Int = 0,
        repetir: Int = 2,
        capa: ByteArray? = null,
    ) {
        p.escreverString(titulo)
        p.escreverString(artista)
        p.escreverLong(duracao)
        p.escreverString(caminho)
        p.escreverInt(estado)
        p.escreverLong(posicao)
        p.escreverInt(aleatorio)
        p.escreverInt(repetir)
        p.escreverInt(capa?.size ?: 0)
        if (capa != null) p.escreverBytes(capa)
    }

    @Test
    fun `quadro completo com capa`() {
        val p = ParcelFalso()
        val capa = byteArrayOf(1, 2, 3, 4, 5)
        p.escreverInt(1)
        escreverObjeto(p, capa = capa)

        val q = QuadroDoCarPlay.lerComPresenca(p)

        assertNotNull(q)
        q!!
        assertEquals("Faixa", q.titulo)
        assertEquals("Banda", q.artista)
        assertEquals(200_000L, q.duracaoMs)
        assertEquals("/sdcard/capa.jpg", q.caminhoDaCapa)
        assertEquals(1, q.estado)
        assertEquals(12_000L, q.posicaoMs)
        assertEquals(0, q.aleatorio)
        assertEquals(2, q.repetir)
        assertArrayEquals(capa, q.bytesDaCapa)
        assertEquals(0, p.restantes())
    }

    @Test
    fun `tamanho da capa menor ou igual a zero nao le mais nada`() {
        for (tamanho in intArrayOf(0, -1)) {
            val p = ParcelFalso()
            p.escreverInt(1)
            escreverObjeto(p)
            p.itens.removeLast() // troca o tamanho escrito por [tamanho]
            p.escreverInt(tamanho)
            p.escreverInt(99) // o emissor ainda escreve um int a mais: é o último campo
            val q = QuadroDoCarPlay.lerComPresenca(p)!!
            assertNull(q.bytesDaCapa)
            assertEquals("o int a mais fica sem ler", 1, p.restantes())
        }
    }

    @Test
    fun `presenca 0 e sem objeto e nao le mais nada`() {
        val p = ParcelFalso()
        p.escreverInt(0)
        assertNull(QuadroDoCarPlay.lerComPresenca(p))
        assertEquals(0, p.restantes())
    }

    @Test
    fun `temConteudo`() {
        fun q(
            titulo: String? = null,
            artista: String? = null,
            caminho: String? = null,
            estado: Int = 0,
            bytes: ByteArray? = null,
        ) = QuadroDoCarPlay(titulo, artista, -1, caminho, estado, -1, -1, -1, bytes)

        assertFalse(q().temConteudo())
        assertFalse(q(titulo = "", artista = "", caminho = "").temConteudo())
        assertFalse(q(estado = 2).temConteudo())
        assertFalse(q(estado = 3).temConteudo())
        assertTrue(q(titulo = "a").temConteudo())
        assertTrue(q(artista = "b").temConteudo())
        assertTrue(q(caminho = "/x").temConteudo())
        assertTrue(q(bytes = byteArrayOf(1)).temConteudo())
        assertTrue(q(estado = 1).temConteudo())
        // Espaços não são aparados: "  " é conteúdo.
        assertTrue(q(titulo = " ").temConteudo())
    }

    // ── capa ──

    @Test
    fun `fator de amostragem e normalizacao`() {
        // 4000x3000 -> fator 4 -> 1000x750 -> 720x540
        assertEquals(4, RegrasDaCapa.fatorDeAmostragem(4000, 3000))
        assertEquals(RegrasDaCapa.Dimensoes(720, 540), RegrasDaCapa.normalizar(1000, 750))
        // 700x700 -> fator 1, sem reduzir
        assertEquals(1, RegrasDaCapa.fatorDeAmostragem(700, 700))
        assertEquals(RegrasDaCapa.Dimensoes(700, 700), RegrasDaCapa.normalizar(700, 700))
        assertEquals(RegrasDaCapa.Dimensoes(720, 720), RegrasDaCapa.normalizar(720, 720))
    }

    @Test
    fun `fator de amostragem e potencia de 2 e para quando metade nao chega a 720`() {
        assertEquals(1, RegrasDaCapa.fatorDeAmostragem(1439, 1439)) // metade = 719
        assertEquals(2, RegrasDaCapa.fatorDeAmostragem(1440, 1440)) // metade = 720
        assertEquals(1, RegrasDaCapa.fatorDeAmostragem(4000, 1000)) // a altura segura o fator
        assertEquals(8, RegrasDaCapa.fatorDeAmostragem(6000, 6000)) // 3000/8 = 375... 3000/4 = 750 >= 720 -> 8
    }

    @Test
    fun `normalizar mantem a proporcao e nunca passa de 720 nem fica abaixo de 1`() {
        assertEquals(RegrasDaCapa.Dimensoes(720, 360), RegrasDaCapa.normalizar(1440, 720))
        assertEquals(RegrasDaCapa.Dimensoes(360, 720), RegrasDaCapa.normalizar(720, 1440))
        assertEquals(RegrasDaCapa.Dimensoes(720, 1), RegrasDaCapa.normalizar(100_000, 1))
    }

    @Test
    fun `esquema do caminho`() {
        assertNull(RegrasDaCapa.esquemaDoCaminho("/data/capa.jpg"))
        assertNull(RegrasDaCapa.esquemaDoCaminho("capa.jpg"))
        assertEquals("http", RegrasDaCapa.esquemaDoCaminho("http://x/y.jpg"))
        assertEquals("https", RegrasDaCapa.esquemaDoCaminho("HTTPS://x/y.jpg"))
        assertEquals("content", RegrasDaCapa.esquemaDoCaminho("content://media/1"))
        assertEquals("file", RegrasDaCapa.esquemaDoCaminho("file:///sdcard/a.png"))
        assertNull(RegrasDaCapa.esquemaDoCaminho(":x"))
        assertNull(RegrasDaCapa.esquemaDoCaminho("/a/b:c"))
    }

    @Test
    fun `bytes iguais reaproveitam o mesmo objeto sem decodificar de novo`() {
        val cache = CacheDaUltimaCapa<String>()
        var decodificacoes = 0
        val decodificar: (ByteArray) -> String? = { decodificacoes++; "bitmap${decodificacoes}" }

        val a = cache.obter(byteArrayOf(1, 2, 3), decodificar)
        val b = cache.obter(byteArrayOf(1, 2, 3), decodificar) // outro vetor, mesmo conteúdo
        assertSame(a, b)
        assertEquals(1, decodificacoes)

        val c = cache.obter(byteArrayOf(1, 2, 4), decodificar) // mesmo tamanho, outro conteúdo
        assertEquals(2, decodificacoes)
        assertTrue(a != c)

        cache.obter(byteArrayOf(1, 2, 4, 9), decodificar) // outro tamanho
        assertEquals(3, decodificacoes)

        // Guarda no máximo o último: voltar aos primeiros bytes decodifica de novo.
        cache.obter(byteArrayOf(1, 2, 3), decodificar)
        assertEquals(4, decodificacoes)
    }

    @Test
    fun `decodificacao nula nao entra no cache`() {
        val cache = CacheDaUltimaCapa<String>()
        var chamadas = 0
        assertNull(cache.obter(byteArrayOf(7)) { chamadas++; null })
        assertNull(cache.obter(byteArrayOf(7)) { chamadas++; null })
        assertEquals(2, chamadas)
    }

    // ── B3: as duas formas de ligar, nesta ordem ──

    @Test
    fun `liga primeiro pelo componente e so tenta a acao se a primeira falhar`() {
        val ordem = ArrayList<String>()
        assertTrue(ligarPelaOrdem({ ordem += "a"; true }, { ordem += "b"; true }))
        assertEquals(listOf("a"), ordem)

        ordem.clear()
        assertTrue(ligarPelaOrdem({ ordem += "a"; false }, { ordem += "b"; true }))
        assertEquals(listOf("a", "b"), ordem)

        ordem.clear()
        assertTrue(ligarPelaOrdem({ ordem += "a"; throw SecurityException("x") }, { ordem += "b"; true }))
        assertEquals(listOf("a", "b"), ordem)

        ordem.clear()
        assertFalse(ligarPelaOrdem({ ordem += "a"; false }, { ordem += "b"; throw IllegalStateException() }))
        assertEquals(listOf("a", "b"), ordem)
    }
}
