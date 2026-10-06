package br.com.deepgwt.tela3d.midia

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V1 (capa utilizável), V3 (ID do YouTube), V4 (escala) e as demais regras de capa (caso E). */
class RegrasDeCapaTest {

    // --- V1 ------------------------------------------------------------------------------------

    @Test
    fun `v1 capa utilizavel`() {
        val casos = listOf(
            40 to 40 to false, 47 to 300 to true, 100 to 80 to false, 120 to 90 to false, 121 to 90 to true,
            96 to 96 to true, 95 to 95 to false, 95 to 80 to false, 50 to 95 to true, 90 to 100 to true,
            70 to 60 to false, 100 to 100 to true, 300 to 300 to true,
        )
        for ((dim, esperado) in casos) {
            assertEquals("${dim.first}x${dim.second}", esperado, RegrasDeCapa.ehUtilizavel(dim.first, dim.second))
        }
    }

    // --- V3 ------------------------------------------------------------------------------------

    @Test
    fun `v3 id do youtube em urls`() {
        val id = "dQw4w9WgXcQ"
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://www.youtube.com/watch?v=$id"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://music.youtube.com/watch?feature=share&v=$id"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://youtu.be/$id?t=3"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://i.ytimg.com/vi/$id/hqdefault.jpg"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://i.ytimg.com/vi_webp/$id/maxresdefault.webp"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://www.youtube.com/shorts/$id"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://www.youtube.com/embed/$id"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://www.youtube-nocookie.com/embed/$id"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://www.youtube.com/live/$id"))
        assertEquals(id, IdDeVideoDoYoutube.deUrl("https://www.youtube.com/v/$id"))
        assertNull(IdDeVideoDoYoutube.deUrl("https://example.com/a.jpg"))
        assertNull(IdDeVideoDoYoutube.deUrl(null))
        assertNull(IdDeVideoDoYoutube.deUrl(""))
    }

    @Test
    fun `v3 id nu so onde aceita`() {
        assertNull(IdDeVideoDoYoutube.deUrl("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", IdDeVideoDoYoutube.deUrlOuNu("dQw4w9WgXcQ"))
        assertNull(IdDeVideoDoYoutube.deUrlOuNu("dQw4w9WgXcQx")) // 12 caracteres
        assertNull(IdDeVideoDoYoutube.deUrlOuNu(null))
        assertEquals("dQw4w9WgXcQ", IdDeVideoDoYoutube.deUrlOuNu("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test
    fun `v3 ordem de tentativa nos metadados`() {
        val tentativas = listOf<Pair<String?, Boolean>>(
            null to true,
            "abcdefghijk" to false, // nu não vale aqui
            "https://youtu.be/AAAAAAAAAAA" to false,
            "https://youtu.be/BBBBBBBBBBB" to false,
        )
        assertEquals("AAAAAAAAAAA", IdDeVideoDoYoutube.primeiroDe(tentativas))
        assertEquals("abcdefghijk", IdDeVideoDoYoutube.primeiroDe(listOf("abcdefghijk" to true)))
        assertNull(IdDeVideoDoYoutube.primeiroDe(emptyList()))
    }

    @Test
    fun `candidatas de download em ordem de resolucao`() {
        val c = IdDeVideoDoYoutube.candidatasDeDownload("dQw4w9WgXcQ")
        assertEquals(
            listOf("maxresdefault", "hq720", "hqdefault", "mqdefault").map { "https://i.ytimg.com/vi/dQw4w9WgXcQ/$it.jpg" },
            c,
        )
    }

    // --- V4 ------------------------------------------------------------------------------------

    @Test
    fun `v4 escala da capa`() {
        assertEquals(Dimensoes(720, 360), RegrasDeCapa.dimensoesCodificadas(1000, 500))
        assertEquals(Dimensoes(360, 720), RegrasDeCapa.dimensoesCodificadas(500, 1000))
        assertEquals(Dimensoes(720, 720), RegrasDeCapa.dimensoesCodificadas(720, 720))
        assertEquals(Dimensoes(720, 100), RegrasDeCapa.dimensoesCodificadas(721, 100))
        assertEquals(Dimensoes(720, 1), RegrasDeCapa.dimensoesCodificadas(4000, 1))
        assertEquals(Dimensoes(300, 200), RegrasDeCapa.dimensoesCodificadas(300, 200))
    }

    @Test
    fun `fator de amostragem reduz sem ficar abaixo de 1440`() {
        assertEquals(1, RegrasDeCapa.fatorDeAmostragem(1000, 1000))
        assertEquals(1, RegrasDeCapa.fatorDeAmostragem(2000, 1500))
        assertEquals(2, RegrasDeCapa.fatorDeAmostragem(4000, 3000))
        assertEquals(4, RegrasDeCapa.fatorDeAmostragem(6000, 6000))
        assertEquals(1, RegrasDeCapa.fatorDeAmostragem(0, 0))
        assertEquals(1, RegrasDeCapa.fatorDeAmostragem(10_000, 100)) // só um lado grande: não reduz
    }

    // --- origem da capa (E1) --------------------------------------------------------------------

    @Test
    fun `maior bitmap por area empate fica com o primeiro`() {
        val dims = listOf(100 to 100, 200 to 50, 50 to 200, 10 to 10)
        assertEquals(0, RegrasDeCapa.indiceDoMaiorPorArea(dims) { it.first.toLong() * it.second })
        assertEquals(-1, RegrasDeCapa.indiceDoMaiorPorArea(emptyList<Pair<Int, Int>>()) { 1L })
        assertEquals(1, RegrasDeCapa.indiceDoMaiorPorArea(listOf(1 to 1, 3 to 3, 2 to 2)) { it.first.toLong() * it.second })
    }

    @Test
    fun `varredura de metadados de texto`() {
        assertEquals(
            "https://x.com/a.jpg",
            RegrasDeCapa.uriPorVarredura(listOf("android.media.metadata.TITLE" to "Faixa", "my_cover" to "https://x.com/a.jpg")),
        )
        // valor de rede mas chave sem relação com imagem: não vale
        assertEquals("", RegrasDeCapa.uriPorVarredura(listOf("android.media.metadata.TITLE" to "https://x.com/page")))
        // valor com youtube vale com qualquer chave
        assertEquals(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            RegrasDeCapa.uriPorVarredura(listOf("android.media.metadata.MEDIA_URI" to "https://www.youtube.com/watch?v=dQw4w9WgXcQ")),
        )
        assertEquals("content://a/b", RegrasDeCapa.uriPorVarredura(listOf("album_art" to "content://a/b")))
        assertEquals("", RegrasDeCapa.uriPorVarredura(emptyList()))
    }

    @Test
    fun `uri que pede decodificar`() {
        assertTrue(RegrasDeCapa.uriPedeDecodificar("content://a/b"))
        assertTrue(RegrasDeCapa.uriPedeDecodificar("file:///sdcard/a.jpg"))
        assertFalse(RegrasDeCapa.uriPedeDecodificar("https://x/a.jpg"))
        assertFalse(RegrasDeCapa.uriPedeDecodificar("/sdcard/a.jpg"))
        assertFalse(RegrasDeCapa.uriPedeDecodificar(""))
    }

    @Test
    fun `local da imagem da central e do carplay`() {
        assertEquals(RegrasDeCapa.LocalDaImagem.ARQUIVO, RegrasDeCapa.localDaImagem("file:///sdcard/a.jpg"))
        assertEquals(RegrasDeCapa.LocalDaImagem.ARQUIVO, RegrasDeCapa.localDaImagem("/sdcard/a.jpg"))
        assertEquals(RegrasDeCapa.LocalDaImagem.CONTEUDO, RegrasDeCapa.localDaImagem("content://a/b"))
        assertEquals(RegrasDeCapa.LocalDaImagem.NENHUM, RegrasDeCapa.localDaImagem("https://x/a.jpg"))
        assertEquals(RegrasDeCapa.LocalDaImagem.NENHUM, RegrasDeCapa.localDaImagem("HTTP://x/a.jpg"))
        assertEquals(RegrasDeCapa.LocalDaImagem.NENHUM, RegrasDeCapa.localDaImagem("  "))
    }

    private fun plano(
        maiorLado: Int = 0, uri: String = "", id: String? = null,
        pacote: String = "com.spotify.music", titulo: String = "T", artista: String = "A",
    ) = RegrasDeCapa.planejarOrigem(maiorLado, uri, id, pacote, titulo, artista)

    @Test
    fun `bitmap grande ignora a url remota`() {
        val p = plano(maiorLado = 500, uri = "https://x/a.jpg")
        assertFalse(p.descartarBitmap)
        assertEquals("", p.urlRemota)
        assertEquals("", p.consulta)
    }

    @Test
    fun `bitmap menor que 320 e descartado em favor da remota`() {
        val p = plano(maiorLado = 319, uri = "https://x/a.jpg")
        assertTrue(p.descartarBitmap)
        assertEquals("https://x/a.jpg", p.urlRemota)
        assertFalse(plano(maiorLado = 320, uri = "https://x/a.jpg").descartarBitmap)
    }

    @Test
    fun `bitmap pequeno sem url remota e mantido`() {
        val p = plano(maiorLado = 100, uri = "content://x/y")
        assertFalse(p.descartarBitmap)
        assertEquals("", p.urlRemota)
    }

    @Test
    fun `id do youtube vira url de maxres`() {
        val p = plano(uri = "https://www.youtube.com/watch?v=dQw4w9WgXcQ", id = "dQw4w9WgXcQ")
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", p.urlRemota)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", plano(id = "dQw4w9WgXcQ").urlRemota)
    }

    @Test
    fun `consulta so para app do youtube sem bitmap nem url`() {
        assertEquals("Titulo Artista", plano(pacote = "com.google.android.apps.youtube.music", titulo = "Titulo", artista = "Artista").consulta)
        assertEquals("Titulo", plano(pacote = "COM.GOOGLE.ANDROID.YOUTUBE", titulo = "Titulo", artista = "").consulta)
        assertEquals("", plano(pacote = "com.spotify.music").consulta)
        assertEquals("", plano(pacote = "app.youtube", maiorLado = 400).consulta)
        assertEquals("", plano(pacote = "app.youtube", uri = "https://x/a.jpg").consulta)
        assertEquals("", plano(pacote = "app.youtube", titulo = "", artista = "").consulta)
    }

    @Test
    fun `decodificado so vence se estritamente maior`() {
        assertTrue(RegrasDeCapa.decodificadoVence(100, 101))
        assertFalse(RegrasDeCapa.decodificadoVence(100, 100))
        assertFalse(RegrasDeCapa.decodificadoVence(100, 50))
    }

    // --- busca no youtube (E8) -------------------------------------------------------------------

    @Test
    fun `url da busca codifica a consulta em utf8`() {
        assertEquals(
            "https://www.youtube.com/results?hl=en&search_query=A+B%C3%A7",
            BuscaNoYoutube.urlDaBusca("A Bç"),
        )
    }

    @Test
    fun `busca acha o primeiro video id com espacos livres`() {
        val corpo = "<html>xx \"videoId\" : \"dQw4w9WgXcQ\" ,\"videoId\":\"AAAAAAAAAAA\"</html>"
        assertEquals("dQw4w9WgXcQ", BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream(corpo.toByteArray())))
    }

    @Test
    fun `busca acha o id que atravessa a fronteira de blocos`() {
        val enchimento = "x".repeat(BuscaNoYoutube.TAMANHO_DO_BLOCO - 10)
        val corpo = enchimento + "\"videoId\":\"abcdefghijk\"" + "y".repeat(100)
        assertEquals("abcdefghijk", BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream(corpo.toByteArray())))
    }

    @Test
    fun `busca respeita o limite de bytes e nao acha o que vem depois`() {
        val corpo = "z".repeat(5_000) + "\"videoId\":\"abcdefghijk\""
        assertNull(BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream(corpo.toByteArray()), limiteDeBytes = 4_000))
        assertNull(BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream(ByteArray(0))))
    }

    @Test
    fun `busca mantem so a cauda do texto sem perder o padrao`() {
        // muito texto sem padrão (descarta o começo) e o padrão no fim
        val corpo = "k".repeat(200_000) + "\"videoId\":\"ZZZZZZZZZZZ\""
        assertEquals("ZZZZZZZZZZZ", BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream(corpo.toByteArray())))
    }

    @Test
    fun `busca nao confunde id curto ou longo demais`() {
        assertNull(BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream("\"videoId\":\"abc\"".toByteArray())))
        assertNull(BuscaNoYoutube.primeiroVideoId(ByteArrayInputStream("\"videoId\":\"abcdefghijkl\"".toByteArray())))
    }

    @Test
    fun `leitura em blocos nao depende do tamanho que o stream entrega`() {
        val corpo = ("a".repeat(30_000) + "\"videoId\":\"abcdefghijk\"").toByteArray()
        val gotaAGota = object : InputStream() {
            private var i = 0
            override fun read(): Int = if (i < corpo.size) corpo[i++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (i >= corpo.size) return -1
                val n = minOf(len, 7, corpo.size - i)
                System.arraycopy(corpo, i, b, off, n)
                i += n
                return n
            }
        }
        assertEquals("abcdefghijk", BuscaNoYoutube.primeiroVideoId(gotaAGota))
    }

    // --- chave por conteúdo (E1.8) --------------------------------------------------------------

    @Test
    fun `chave de capa e estavel para o mesmo conteudo e muda com ele`() {
        val a = ChaveDeCapa.deAmostra(300, 300, intArrayOf(1, 2, 3))
        assertEquals(a, ChaveDeCapa.deAmostra(300, 300, intArrayOf(1, 2, 3)))
        assertNotEquals(a, ChaveDeCapa.deAmostra(300, 300, intArrayOf(1, 2, 4)))
        assertNotEquals(a, ChaveDeCapa.deAmostra(301, 300, intArrayOf(1, 2, 3)))
        assertEquals("a|b|", ChaveDeCapa.montar("a", "b", ""))
    }
}
