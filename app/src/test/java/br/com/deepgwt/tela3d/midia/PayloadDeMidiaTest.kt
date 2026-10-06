package br.com.deepgwt.tela3d.midia

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V9 (assinatura/publicação), V11 (payload) e V12 (texto do cartão). */
class PayloadDeMidiaTest {

    private class Destino : DestinoDoTocandoAgora {
        val payloads = ArrayList<JSONObject>()
        val posicoes = ArrayList<Long>()
        override fun aoMudarFaixa(payload: JSONObject) {
            payloads += payload
        }

        override fun aoMudarPosicao(posicaoMs: Long) {
            posicoes += posicaoMs
        }
    }

    private fun entrada(
        fonte: FonteDaFaixa = FonteDaFaixa.SESSAO,
        titulo: String = "Faixa",
        posicao: Long = 48_250,
        tocando: Boolean = true,
        chave: String = "k1",
        art: String = "data:image/jpeg;base64,AAAA",
        icone: String = "data:image/png;base64,BBBB",
    ) = EntradaDoPayload(
        fonte = fonte, titulo = titulo, artista = "Artista", album = "Álbum", duracaoMs = 215_000, posicaoMs = posicao,
        tocando = tocando, rotulo = "Spotify", pacote = "com.spotify.music", chaveDaCapa = chave, artDataUrl = art,
        appIcon = icone, canLaunch = true,
    )

    private fun publicador(): Pair<PublicadorDePayload, Destino> {
        val d = Destino()
        return PublicadorDePayload().also { it.destino = d } to d
    }

    // --- V11 ------------------------------------------------------------------------------------

    @Test
    fun `v11 payload com faixa tem as 13 chaves com os tipos certos`() {
        val (p, d) = publicador()
        p.publicar(entrada())
        val json = d.payloads.single()
        assertEquals(13, json.length())
        assertEquals("Faixa", json.getString("title"))
        assertEquals("Artista", json.getString("artist"))
        assertEquals("Álbum", json.getString("album"))
        assertTrue(json.get("durationMs") is Number)
        assertEquals(215_000L, json.getLong("durationMs"))
        assertEquals(48_250L, json.getLong("positionMs"))
        assertTrue(json.getBoolean("playing"))
        assertEquals("Spotify", json.getString("appLabel"))
        assertEquals("com.spotify.music", json.getString("packageName"))
        assertEquals("data:image/jpeg;base64,AAAA", json.getString("artDataUrl"))
        assertEquals("data:image/png;base64,BBBB", json.getString("appIcon"))
        assertTrue(json.getBoolean("canLaunch"))
        assertTrue(json.getBoolean("hasTrack"))
        assertFalse(json.getBoolean("needsListener"))
    }

    @Test
    fun `v11 payload vazio sem acesso pede o acesso`() {
        val json = PayloadDeMidia.vazio(precisaDeAcesso = true).toJson()
        assertEquals(13, json.length())
        assertEquals(RotulosDeMidia.ACESSO_NECESSARIO, json.getString("appLabel"))
        assertTrue(json.getBoolean("needsListener"))
        assertFalse(json.getBoolean("hasTrack"))
        for (chave in listOf("title", "artist", "album", "packageName", "artDataUrl", "appIcon")) assertEquals("", json.getString(chave))
        assertEquals(0L, json.getLong("durationMs"))
        assertEquals(0L, json.getLong("positionMs"))
        assertFalse(json.getBoolean("playing"))
        assertFalse(json.getBoolean("canLaunch"))
    }

    @Test
    fun `v11 payload vazio com ouvinte conectado e tudo vazio`() {
        val json = PayloadDeMidia.vazio(precisaDeAcesso = false).toJson()
        assertEquals("", json.getString("appLabel"))
        assertFalse(json.getBoolean("needsListener"))
        assertEquals(PayloadDeMidia(), PayloadDeMidia.vazio(false))
    }

    @Test
    fun `v11 has track e titulo ou artista nao vazio`() {
        assertTrue(entrada(titulo = "").paraPayload().hasTrack) // tem artista
        val soCapa = EntradaDoPayload(
            FonteDaFaixa.CENTRAL, "", "", "", 0, 0, false, "USB", "p", "k", "data:image/jpeg;base64,AA", "", false,
        )
        assertFalse(soCapa.paraPayload().hasTrack)
    }

    @Test
    fun `v11 posicao e duracao nunca saem negativas`() {
        val p = entrada(posicao = -5).paraPayload()
        assertEquals(0L, p.positionMs)
    }

    @Test
    fun `v11 ida e volta pelo json`() {
        val original = entrada().paraPayload()
        assertEquals(original, PayloadDeMidia.deJson(original.toJson()))
        assertEquals(PayloadDeMidia(), PayloadDeMidia.deJson(JSONObject()))
    }

    // --- V9 ------------------------------------------------------------------------------------

    @Test
    fun `v9 so a posicao mudou publica uma vez`() {
        val (p, d) = publicador()
        assertTrue(p.publicar(entrada(posicao = 1_000)))
        assertFalse(p.publicar(entrada(posicao = 9_000)))
        assertEquals(1, d.payloads.size)
    }

    @Test
    fun `v9 mudar so playing publica de novo`() {
        val (p, d) = publicador()
        p.publicar(entrada(tocando = true))
        assertTrue(p.publicar(entrada(tocando = false)))
        assertEquals(2, d.payloads.size)
    }

    @Test
    fun `v9 reenviar agora publica mesmo igual`() {
        val (p, d) = publicador()
        p.publicar(entrada())
        p.esquecerAssinatura()
        assertTrue(p.publicar(entrada()))
        assertEquals(2, d.payloads.size)
    }

    @Test
    fun `v9 vencedor muda de fonte mantendo o texto publica de novo`() {
        val (p, d) = publicador()
        p.publicar(entrada(fonte = FonteDaFaixa.SESSAO))
        assertTrue(p.publicar(entrada(fonte = FonteDaFaixa.CENTRAL)))
        assertEquals(2, d.payloads.size)
    }

    @Test
    fun `v9 capa passa de url para data url publica de novo`() {
        val (p, d) = publicador()
        p.publicar(entrada(art = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", chave = "k"))
        assertTrue(p.publicar(entrada(art = "data:image/jpeg;base64,AAAA", chave = "k")))
        assertEquals(2, d.payloads.size)
    }

    @Test
    fun `v9 chave de capa nova publica de novo`() {
        val (p, d) = publicador()
        p.publicar(entrada(chave = "a"))
        assertTrue(p.publicar(entrada(chave = "b")))
        assertEquals(2, d.payloads.size)
    }

    @Test
    fun `v9 icone do app diferente publica de novo`() {
        val (p, d) = publicador()
        p.publicar(entrada(icone = ""))
        assertTrue(p.publicar(entrada(icone = "data:image/png;base64,CCCC")))
        assertEquals(2, d.payloads.size)
    }

    @Test
    fun `v9 vazio so publica quando muda`() {
        val (p, d) = publicador()
        assertTrue(p.publicarVazio(true))
        assertFalse(p.publicarVazio(true))
        assertTrue(p.publicarVazio(false))
        assertTrue(p.publicar(entrada()))
        assertTrue(p.publicarVazio(false))
        assertEquals(4, d.payloads.size)
    }

    @Test
    fun `sem destino nada e entregue e o primeiro envio depois sai`() {
        val p = PublicadorDePayload()
        assertFalse(p.publicar(entrada()))
        assertFalse(p.publicarVazio(true))
        val d = Destino()
        p.destino = d
        assertTrue(p.publicar(entrada()))
        assertEquals(1, d.payloads.size)
        p.destino = null
        assertFalse(p.publicar(entrada(titulo = "outra")))
    }

    // --- V12 ------------------------------------------------------------------------------------

    @Test
    fun `v12 titulo do cartao`() {
        val r = RegrasDoCartaoDeMidia
        assertEquals("X", r.titulo(true, "X", "App"))
        assertEquals("App", r.titulo(true, "", "App"))
        assertEquals("Nada tocando", r.titulo(false, "", ""))
        assertEquals(RotulosDeMidia.ACESSO_NECESSARIO, r.titulo(false, "", RotulosDeMidia.ACESSO_NECESSARIO))
        assertEquals("App", r.titulo(false, "ignorado", "App")) // sem faixa o título não vale
    }

    @Test
    fun `v12 subtitulo do cartao`() {
        val r = RegrasDoCartaoDeMidia
        assertEquals("Art", r.subtitulo("Art", true, "Alb"))
        assertEquals("Alb", r.subtitulo("", true, "Alb"))
        assertEquals("Escolha um app de mídia", r.subtitulo("", false, "Alb"))
        assertEquals("", r.subtitulo("", true, ""))
        assertFalse(r.subtituloVisivel(""))
        assertTrue(r.subtituloVisivel("x"))
    }

    @Test
    fun `k1 botoes habilitados so com faixa`() {
        val r = RegrasDoCartaoDeMidia
        val com = PayloadDeMidia(hasTrack = true)
        val sem = PayloadDeMidia()
        assertTrue(r.botoesHabilitados(com))
        assertEquals(1.0f, r.opacidadeDosBotoes(com), 0f)
        assertFalse(r.botoesHabilitados(sem))
        assertEquals(0.35f, r.opacidadeDosBotoes(sem), 0f)
    }

    @Test
    fun `k4 so decodifica data url`() {
        val r = RegrasDoCartaoDeMidia
        assertEquals("AAAA", r.base64DaDataUrl("data:image/jpeg;base64,AAAA"))
        assertNull(r.base64DaDataUrl("https://i.ytimg.com/vi/x/maxresdefault.jpg"))
        assertNull(r.base64DaDataUrl(""))
        assertNull(r.base64DaDataUrl(null))
        assertNull(r.base64DaDataUrl("data:image/jpeg;base64"))
        assertNull(r.base64DaDataUrl("data:image/jpeg;base64,"))
        assertTrue(r.ehDataUrl("data:x"))
        assertFalse(r.ehDataUrl("http://x"))
    }

    @Test
    fun `k5 descricoes de acessibilidade em portugues`() {
        val r = RegrasDoCartaoDeMidia
        assertEquals("Pausar", r.descricaoDeTocarOuPausar(true))
        assertEquals("Tocar", r.descricaoDeTocarOuPausar(false))
        assertEquals(32, RegrasDoCartaoDeMidia.MARGEM_DO_ESPACO_RESERVADO_DP)
    }

    @Test
    fun `k6 origem do icone do chip`() {
        val r = RegrasDoCartaoDeMidia
        val o = RegrasDoCartaoDeMidia.OrigemDoIconeDoChip.values()
        assertEquals(o[0], r.origemDoIconeDoChip(true, "com.ts.carplay", "CARPLAY")) // configurado pelo usuário
        assertEquals(RegrasDoCartaoDeMidia.OrigemDoIconeDoChip.ANDROID_AUTO, r.origemDoIconeDoChip(false, "com.ts.androidauto.app", "ANDROID AUTO"))
        assertEquals(RegrasDoCartaoDeMidia.OrigemDoIconeDoChip.CARPLAY, r.origemDoIconeDoChip(false, "com.ts.carplay", "CARPLAY"))
        assertEquals(RegrasDoCartaoDeMidia.OrigemDoIconeDoChip.ICONE_DO_PAYLOAD, r.origemDoIconeDoChip(false, "com.spotify.music", "Spotify"))
    }

    @Test
    fun `k7 toque abre o app so se pode e ha pacote`() {
        val r = RegrasDoCartaoDeMidia
        assertTrue(r.toqueAbreOAplicativo(PayloadDeMidia(canLaunch = true, packageName = "p")))
        assertFalse(r.toqueAbreOAplicativo(PayloadDeMidia(canLaunch = true, packageName = "")))
        assertFalse(r.toqueAbreOAplicativo(PayloadDeMidia(canLaunch = false, packageName = "p")))
    }
}
