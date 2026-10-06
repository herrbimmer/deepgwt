package br.com.deepgwt.tela3d.midia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Formatos binários da central (caso H1) e do CarPlay (casos I2–I4), sobre o leitor falso. */
class DadosDaCentralETest {
    private val falha = LeitorFalso.Falha

    private fun exige(info: InformacaoDaCentral?): InformacaoDaCentral = requireNotNull(info) { "esperava informação" }

    /** Resposta da transação 22 com 14 textos, 9ª = título etc. */
    private fun respostaDeInformacao(
        fonteDoObjeto: Int = 402,
        classe: String? = "com.beantechs.MediaInfo",
        album: String? = "alb", titulo: String? = "Titulo", artista: String? = "Artista", url: String? = "file:///a.jpg",
        duracao: Long = 200_000, bitmap: Any? = null,
    ): Array<Any?> {
        val textos = Array<Any?>(14) { "t${it + 1}" }
        textos[6] = album
        textos[8] = titulo
        textos[10] = artista
        textos[11] = url
        val resposta = ArrayList<Any?>()
        resposta.addAll(listOf(1, fonteDoObjeto, 7.toByte(), null, classe))
        resposta.addAll(textos)
        resposta.addAll(listOf(duracao, 1, 2, 0.toByte(), bitmap))
        return resposta.toTypedArray()
    }

    // --- transação 22 ----------------------------------------------------------------------------

    @Test
    fun `h1 informacao le os campos nas posicoes certas`() {
        val leitor = LeitorFalso(*respostaDeInformacao(bitmap = "bmp"))
        val info = exige(DecodificadorDaCentral.informacao(leitor, 402))
        assertEquals("alb", info.album)
        assertEquals("Titulo", info.titulo)
        assertEquals("Artista", info.artista)
        assertEquals("file:///a.jpg", info.urlDaImagem)
        assertEquals(200_000L, info.duracaoMs)
        assertEquals("bmp", info.bitmap)
    }

    @Test
    fun `h1 informacao com string de bytes ignorados`() {
        val base = respostaDeInformacao().toMutableList()
        base[3] = "x" // string não nula: vem seguida de um array de bytes
        base.add(4, byteArrayOf(1, 2, 3))
        val info = exige(DecodificadorDaCentral.informacao(LeitorFalso(*base.toTypedArray()), 402))
        assertEquals("Titulo", info.titulo)
    }

    @Test
    fun `h1 nada presente ou sem classe ou de outra fonte nao ha informacao`() {
        assertNull(DecodificadorDaCentral.informacao(LeitorFalso(0), 402))
        assertNull(DecodificadorDaCentral.informacao(LeitorFalso(*respostaDeInformacao(classe = null)), 402))
        assertNull(DecodificadorDaCentral.informacao(LeitorFalso(*respostaDeInformacao(fonteDoObjeto = 2)), 402))
        assertNotNull(DecodificadorDaCentral.informacao(LeitorFalso(*respostaDeInformacao(fonteDoObjeto = 0)), 402))
    }

    @Test
    fun `h1 leitura do bitmap que falha mantem a faixa sem bitmap`() {
        val r = respostaDeInformacao().toMutableList()
        r[r.lastIndex] = falha
        val info = exige(DecodificadorDaCentral.informacao(LeitorFalso(*r.toTypedArray()), 402))
        assertNull(info.bitmap)
        assertEquals("Titulo", info.titulo)
    }

    // --- transação 19 ----------------------------------------------------------------------------

    private fun respostaDeEstado(fonte: Int = 402, estado: Int = 3, duracao: Long = 180_000, decorrido: Long = 5_000) =
        arrayOf<Any?>(1, fonte, estado, duracao, 0L, 0f, 0L, "x", decorrido)

    @Test
    fun `h1 estado le estado duracao e decorrido`() {
        val e = requireNotNull(DecodificadorDaCentral.estado(LeitorFalso(*respostaDeEstado()), 402))
        assertEquals(3, e.estado)
        assertEquals(180_000L, e.duracaoMs)
        assertEquals(5_000L, e.decorridoMs)
    }

    @Test
    fun `h1 estado negativo vira zero e fonte diferente nao vale`() {
        val e = requireNotNull(DecodificadorDaCentral.estado(LeitorFalso(*respostaDeEstado(fonte = 2, duracao = -5, decorrido = -9)), 2))
        assertEquals(0L, e.duracaoMs)
        assertEquals(0L, e.decorridoMs)
        assertNull(DecodificadorDaCentral.estado(LeitorFalso(*respostaDeEstado(fonte = 402)), 2))
        assertNotNull(DecodificadorDaCentral.estado(LeitorFalso(*respostaDeEstado(fonte = 0)), 2))
        assertNull(DecodificadorDaCentral.estado(LeitorFalso(0), 2))
    }

    // --- escolha de fonte e montagem -------------------------------------------------------------

    @Test
    fun `h1 escolha da fonte 402 antes de 2`() {
        assertEquals(402, DecodificadorDaCentral.escolherFonte(402, 2))
        assertEquals(402, DecodificadorDaCentral.escolherFonte(2, 402))
        assertEquals(2, DecodificadorDaCentral.escolherFonte(2, 4))
        assertEquals(2, DecodificadorDaCentral.escolherFonte(10, 2))
        assertNull(DecodificadorDaCentral.escolherFonte(4, 10))
        assertNull(DecodificadorDaCentral.escolherFonte(null, null))
    }

    private fun info(titulo: String? = "T", artista: String? = "A", album: String? = "alb", url: String? = "", duracao: Long = 0) =
        InformacaoDaCentral(album, titulo, artista, url, duracao, null)

    @Test
    fun `h1 faixa de android auto`() {
        val d = requireNotNull(DecodificadorDaCentral.montarFaixa(402, info(), EstadoDaCentral(3, 100, 40)))
        assertEquals("com.ts.androidauto.app", d.pacote)
        assertEquals("ANDROID AUTO", d.rotulo)
        assertTrue(d.tocando)
        assertEquals(40L, d.posicaoMs)
        assertEquals(100L, d.duracaoMs)
    }

    @Test
    fun `h1 faixa de usb`() {
        val d = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(), EstadoDaCentral(2, 0, 0)))
        assertEquals("com.beantechs.mediacenter", d.pacote)
        assertEquals("USB", d.rotulo)
        assertFalse(d.tocando)
    }

    @Test
    fun `h1 duracao do estado antes da da informacao`() {
        assertEquals(100L, requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(duracao = 50), EstadoDaCentral(3, 100, 0))).duracaoMs)
        assertEquals(50L, requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(duracao = 50), EstadoDaCentral(3, 0, 0))).duracaoMs)
        assertEquals(0L, requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(duracao = 0), EstadoDaCentral(3, 0, 0))).duracaoMs)
        assertEquals(50L, requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(duracao = 50), null)).duracaoMs)
    }

    @Test
    fun `h1 sem informacao e sem estado nao ha faixa`() {
        assertNull(DecodificadorDaCentral.montarFaixa(2, null, null))
        val d = requireNotNull(DecodificadorDaCentral.montarFaixa(2, null, EstadoDaCentral(3, 0, 10)))
        assertEquals("", d.titulo)
        assertTrue(d.tocando)
    }

    @Test
    fun `h1 nulos viram vazio`() {
        val d = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(titulo = null, artista = null, album = null, url = null), null))
        assertEquals("", d.titulo)
        assertEquals("", d.artista)
        assertEquals("", d.album)
        assertEquals("", d.urlRemota)
        assertEquals("", d.imagemLocal)
    }

    @Test
    fun `h1 imagem http e remota e as outras sao locais`() {
        val http = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(url = "https://x/a.jpg"), null))
        assertEquals("https://x/a.jpg", http.urlRemota)
        assertEquals("", http.imagemLocal)
        val arquivo = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(url = "/sdcard/a.jpg"), null))
        assertEquals("", arquivo.urlRemota)
        assertEquals("/sdcard/a.jpg", arquivo.imagemLocal)
        val conteudo = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(url = "content://a/b"), null))
        assertEquals("content://a/b", conteudo.imagemLocal)
    }

    @Test
    fun `h1 chave da capa muda com a faixa e com o bitmap do pacote`() {
        val d = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(), null))
        val outra = requireNotNull(DecodificadorDaCentral.montarFaixa(2, info(titulo = "Outro"), null))
        val a = DecodificadorDaCentral.chaveDaCapa(d, null)
        assertEquals(a, DecodificadorDaCentral.chaveDaCapa(d, null))
        assertFalse(a == DecodificadorDaCentral.chaveDaCapa(outra, null))
        assertFalse(a == DecodificadorDaCentral.chaveDaCapa(d, Dimensoes(300, 300)))
    }

    // --- CarPlay (I2, I3) ------------------------------------------------------------------------

    private fun transacaoDoCarPlay(
        titulo: String? = "Musica", artista: String? = "Banda", duracao: Long = 200_000, caminho: String? = "",
        status: Int = 1, decorrido: Long = 3_000, tamanho: Int = 0, bytes: Any? = null,
    ): Array<Any?> {
        val base = arrayListOf<Any?>(1, titulo, artista, duracao, caminho, status, decorrido, 7, 8, tamanho)
        if (tamanho > 0) base += bytes
        return base.toTypedArray()
    }

    @Test
    fun `i2 le a atualizacao completa`() {
        val a = requireNotNull(DecodificadorDoCarPlay.ler(LeitorFalso(*transacaoDoCarPlay())))
        assertEquals("Musica", a.titulo)
        assertEquals("Banda", a.artista)
        assertEquals(200_000L, a.duracaoMs)
        assertEquals(3_000L, a.decorridoMs)
        assertTrue(a.tocando)
        assertTrue(a.valida)
        assertNull(a.imagem)
    }

    @Test
    fun `i2 status diferente de 1 nao esta tocando`() {
        for (s in listOf(0, 2, 3)) {
            assertFalse(requireNotNull(DecodificadorDoCarPlay.ler(LeitorFalso(*transacaoDoCarPlay(status = s)))).tocando)
        }
    }

    @Test
    fun `i2 le os bytes da imagem quando o tamanho e maior que zero`() {
        val img = byteArrayOf(1, 2, 3, 4)
        val a = requireNotNull(DecodificadorDoCarPlay.ler(LeitorFalso(*transacaoDoCarPlay(tamanho = 4, bytes = img))))
        assertTrue(img.contentEquals(a.imagem))
    }

    @Test
    fun `i2 falha ao ler a imagem mantem o texto`() {
        val leitor = LeitorFalso(*transacaoDoCarPlay(tamanho = 4, bytes = falha))
        val a = requireNotNull(DecodificadorDoCarPlay.ler(leitor))
        assertEquals("Musica", a.titulo)
        assertNull(a.imagem)
    }

    @Test
    fun `i2 presente zero e atualizacao vazia`() {
        assertNull(DecodificadorDoCarPlay.ler(LeitorFalso(0)))
    }

    @Test
    fun `i2 falha de leitura propaga para quem trata`() {
        var lancou = false
        try {
            DecodificadorDoCarPlay.ler(LeitorFalso(1, "t", falha))
        } catch (_: IllegalStateException) {
            lancou = true
        }
        assertTrue(lancou)
    }

    @Test
    fun `i3 validade da faixa`() {
        fun a(t: String = "", ar: String = "", c: String = "", status: Int = 0, img: ByteArray? = null) =
            AtualizacaoDoCarPlay(t, ar, 0, c, status, 0, img)
        assertTrue(a(t = "x").valida)
        assertTrue(a(ar = "x").valida)
        assertTrue(a(c = "/a.jpg").valida)
        assertTrue(a(img = byteArrayOf(1)).valida)
        assertTrue(a(status = 1).valida) // está tocando
        assertFalse(a().valida)
        assertFalse(a(t = "  ", ar = " ", c = "\n").valida) // só espaços
        assertFalse(a(img = ByteArray(0)).valida)
    }

    @Test
    fun `i3 chave da capa do carplay`() {
        val a = AtualizacaoDoCarPlay(" T ", "A", 0, "/c.jpg", 1, 0, byteArrayOf(1, 2))
        assertEquals("carplay|T|A|2|/c.jpg", a.chaveDaCapa)
        assertEquals("carplay|||0|", AtualizacaoDoCarPlay("", "", 0, "", 0, 0, null).chaveDaCapa)
    }

    // --- tolerância (I4) ------------------------------------------------------------------------

    @Test
    fun `i4 vazia dentro de 4 s espera o restante e depois limpa`() {
        val t = ToleranciaDeAtualizacaoVazia()
        t.aoReceberValida(10_000)
        val d = t.aoReceberVazia(11_000)
        assertEquals(DecisaoDaVazia.Aguardar(3_000), d)
        // nada chegou: ao fim da espera limpa
        assertTrue(t.deveLimparAoFimDaEspera(14_000))
    }

    @Test
    fun `i4 valida que chega durante a espera cancela a limpeza`() {
        val t = ToleranciaDeAtualizacaoVazia()
        t.aoReceberValida(10_000)
        t.aoReceberVazia(11_000)
        t.aoReceberValida(13_000)
        assertFalse(t.deveLimparAoFimDaEspera(14_000)) // a última válida foi há 1 s
        assertTrue(t.deveLimparAoFimDaEspera(17_000))
    }

    @Test
    fun `i4 vazia depois da tolerancia ou sem valida anterior limpa na hora`() {
        val t = ToleranciaDeAtualizacaoVazia()
        assertEquals(DecisaoDaVazia.LimparAgora, t.aoReceberVazia(1_000))
        t.aoReceberValida(10_000)
        assertEquals(DecisaoDaVazia.LimparAgora, t.aoReceberVazia(14_000))
        assertEquals(DecisaoDaVazia.Aguardar(1), t.aoReceberVazia(13_999))
    }

    @Test
    fun `i4 esquecer volta ao estado inicial`() {
        val t = ToleranciaDeAtualizacaoVazia()
        t.aoReceberValida(10_000)
        t.esquecer()
        assertEquals(DecisaoDaVazia.LimparAgora, t.aoReceberVazia(10_500))
    }
}
