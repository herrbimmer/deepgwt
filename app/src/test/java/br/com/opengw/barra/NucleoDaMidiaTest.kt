package br.com.opengw.barra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sequências do núcleo da mídia com fontes falsas e relógio falso. */
class NucleoDaMidiaTest {

    private class LancadorFalso : LancadorParaABarra by LancadorParaABarra.VAZIO {
        var deixarParaNativa = false
        var audio = false
        override fun deixarParaAMidiaNativa(motivo: String) = deixarParaNativa
        override fun audioDoAndroidAutoAtivo(motivo: String, forcarLeitura: Boolean) = audio
    }

    private class AmbienteFalso : NucleoDaMidia.Ambiente {
        val lancadorFalso = LancadorFalso()
        override val lancador: LancadorParaABarra get() = lancadorFalso
        var agora = 1_000L
        var painel = ""
        var retrato = MidiaAgora.VAZIA
        var usb: Boolean? = null
        val fila = ArrayList<() -> Unit>()
        val registros = ArrayList<Map<String, Any?>>()
        override fun agoraMs() = agora
        override fun projecaoNoPainel() = painel
        override fun lerRetrato() = retrato
        override fun escreverRetrato(m: MidiaAgora) {
            retrato = m
        }
        override fun naPrincipal(bloco: () -> Unit) {
            fila += bloco
        }
        override fun registrar(categoria: String, campos: Map<String, Any?>) {
            registros += campos
        }
        override fun log(mensagem: String) = Unit
        override fun usbDaProjecaoPronto() = usb
        fun rodarFila() {
            val copia = fila.toList()
            fila.clear()
            copia.forEach { it() }
        }
    }

    private val amb = AmbienteFalso()
    private val n = NucleoDaMidia(amb)

    private fun sessao(
        pacote: String,
        titulo: String? = "T",
        tocando: Boolean = true,
        meta: Boolean = true,
        duracao: Long = 200_000,
        pos: Long = 10_000,
        podeBuscar: Boolean = true,
    ) = CandidatoDeSessao(pacote, titulo, "A", "", null, tocando, duracao, pos, amb.agora, podeBuscar, meta)

    @Test
    fun `sessao comum vira o retrato com progresso e buscar`() {
        n.publicarSessao(sessao("com.spotify.music"), sessaoAAPronta = false)
        val r = amb.retrato
        assertEquals("com.spotify.music", r.pacote)
        assertEquals("T", r.titulo)
        assertNull(r.album) // vazio vira nulo
        assertTrue(r.tocando)
        assertEquals(10_000L, r.decorridoMs)
        assertEquals(1_000L, r.progressoAtualizadoEmMs)
        assertTrue(r.podeBuscar)
    }

    @Test
    fun `usb dono preservado contra sessao parada`() {
        n.central = CentralDeMidiaLida(2, null)
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.USB, titulo = "Pendrive")
        n.publicarSessao(sessao("com.spotify.music", tocando = false), false)
        assertEquals("Pendrive", amb.retrato.titulo)
        // Tocando, a sessão toma o lugar.
        n.publicarSessao(sessao("com.spotify.music", tocando = true), false)
        assertEquals("com.spotify.music", amb.retrato.pacote)
    }

    @Test
    fun `reserva do aa nunca rebaixa um aa tocando`() {
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.ANDROID_AUTO, titulo = "Velha", tocando = true)
        n.publicarSessao(sessao(RegrasDaMidia.BLUETOOTH, titulo = "Nova", tocando = false), sessaoAAPronta = true)
        val r = amb.retrato
        assertEquals(RegrasDaMidia.ANDROID_AUTO, r.pacote)
        assertEquals("Nova", r.titulo)
        assertTrue(r.tocando)
        assertFalse(r.podeBuscar)
    }

    @Test
    fun `projecao mantida contra o celular e contra sessao sem metadados`() {
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.CARPLAY, titulo = "CP")
        n.publicarSessao(sessao(RegrasDaMidia.BLUETOOTH), false)
        assertEquals("CP", amb.retrato.titulo)
        n.publicarSessao(sessao("com.spotify.music", meta = false), false)
        assertEquals("CP", amb.retrato.titulo)
        // Sessão com metadados de outro app substitui.
        n.publicarSessao(sessao("com.spotify.music"), false)
        assertEquals("com.spotify.music", amb.retrato.pacote)
    }

    @Test
    fun `sem sessao limpa preservando projecao`() {
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.CARPLAY, titulo = "CP", mudo = true)
        n.publicarSessao(null, false)
        assertEquals("CP", amb.retrato.titulo)
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = "com.spotify.music", titulo = "S", mudo = true)
        n.publicarSessao(null, false)
        assertNull(amb.retrato.pacote)
        assertTrue("o mudo não muda na limpeza", amb.retrato.mudo)
    }

    @Test
    fun `limpeza geral pedida nao apaga uma publicacao mais nova (M-D6)`() {
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = "com.a", titulo = "A")
        n.pedirLimpezaGeral(preservarProjecao = false)
        n.publicarSessao(sessao("com.b"), false)
        amb.rodarFila()
        assertEquals("com.b", amb.retrato.pacote)
        n.pedirLimpezaGeral(preservarProjecao = false)
        amb.rodarFila()
        assertNull(amb.retrato.pacote)
    }

    @Test
    fun `notificacao sempre tocando e cede a projecao`() {
        n.publicarNotificacao("com.app", "T", "A", "", null)
        assertTrue(amb.retrato.tocando)
        assertEquals(0L, amb.retrato.progressoAtualizadoEmMs)
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.ANDROID_AUTO, titulo = "AA")
        n.publicarNotificacao(RegrasDaMidia.BLUETOOTH, "BT", "A", null, null)
        assertEquals("AA", amb.retrato.titulo)
        n.publicarNotificacao("com.spotify.music", "S", "A", null, null)
        assertEquals("com.spotify.music", amb.retrato.pacote)
    }

    @Test
    fun `carplay mantem textos sem fonte nova e limpa`() {
        fun cp(t: String?, a: String?, caminho: String?, limpar: Boolean = false) =
            AtualizacaoDoCarPlay(t, a, null, caminho, 100_000, 5_000, true, limpar)
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = "com.spotify.music", titulo = "S", album = "Al")
        n.publicarCarPlay(cp(null, "Art", "/c1"))
        assertEquals(RegrasDaMidia.CARPLAY, amb.retrato.pacote)
        assertNull("fonte mudou: título some", amb.retrato.titulo)
        assertNull(amb.retrato.album)
        assertTrue(amb.retrato.podeBuscar)
        n.publicarCarPlay(cp("Música", null, "/c1"))
        assertEquals("Música", amb.retrato.titulo)
        assertEquals("Art", amb.retrato.artista)
        n.publicarCarPlay(cp(null, null, null, limpar = true))
        assertNull(amb.retrato.pacote)
    }

    @Test
    fun `protecao do radio limpa o aa e e ignorada com aa nativo`() {
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.ANDROID_AUTO, titulo = "AA")
        n.ativarProtecaoDoRadio("teste")
        assertNull(amb.retrato.pacote)
        assertTrue(n.protecaoAtiva(consultar = false))
        amb.agora += 12_001
        assertFalse(n.protecaoAtiva(consultar = false))
        // Com AA nativo (e sem rádio local), a proteção não liga.
        n.central = CentralDeMidiaLida(402, null)
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.ANDROID_AUTO, titulo = "AA")
        n.ativarProtecaoDoRadio("teste")
        assertFalse(n.protecaoAtiva(consultar = false))
    }

    @Test
    fun `central em radio ativa a protecao pela principal`() {
        n.central = CentralDeMidiaLida(12, null)
        assertTrue(n.protecaoAtiva(consultar = false))
        assertEquals(1, amb.fila.size)
        n.central = CentralDeMidiaLida(null, null)
        amb.fila.clear()
        amb.lancadorFalso.deixarParaNativa = true
        assertFalse(n.protecaoAtiva(consultar = false))
        assertTrue(n.protecaoAtiva(consultar = true))
    }

    @Test
    fun `aa nativo com estado e alvo segurado`() {
        n.central = CentralDeMidiaLida(402, null)
        amb.lancadorFalso.audio = true
        assertTrue(n.transporteAAPronto())
        // Alvo "pausar" marcado; a central ainda diz "tocando" por alguns segundos.
        n.marcarAlvo(false)
        assertEquals(RegrasDaMidia.ANDROID_AUTO, amb.retrato.pacote)
        assertFalse(amb.retrato.tocando)
        amb.agora += 2_000
        val tocando = EstadoNaCentral(402, 3, 200_000, 30_000)
        assertTrue(n.aplicarEstadoAA(tocando, transportePronto = true))
        assertFalse("o alvo segura", amb.retrato.tocando)
        // Sem transporte, não aplica.
        assertFalse(n.aplicarEstadoAA(tocando, transportePronto = false))
    }

    @Test
    fun `metadados do aa nativo`() {
        n.central = CentralDeMidiaLida(402, null)
        val info = InfoNaCentral(402, "Faixa", "Artista", null, null, null, null, 180_000)
        n.aplicarMetadadosAA(info, EstadoNaCentral(402, 3, 180_000, 5_000), null, transportePronto = true)
        val r = amb.retrato
        assertEquals(RegrasDaMidia.ANDROID_AUTO, r.pacote)
        assertEquals("Faixa", r.titulo)
        assertTrue(r.tocando)
        assertEquals(180_000L, r.duracaoMs)
        assertEquals(5_000L, r.decorridoMs)
        // Dono CarPlay: nada do AA.
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.CARPLAY)
        n.aplicarMetadadosAA(info, null, null, transportePronto = true)
        assertEquals(RegrasDaMidia.CARPLAY, amb.retrato.pacote)
    }

    @Test
    fun `troca de faixa zera o progresso do aa e abre a janela`() {
        amb.retrato = MidiaAgora.VAZIA.copy(pacote = RegrasDaMidia.ANDROID_AUTO, tocando = true, duracaoMs = 200_000, decorridoMs = 90_000, progressoAtualizadoEmMs = 1_000)
        assertTrue(n.trocouDeFaixa("teste"))
        assertEquals(0L, amb.retrato.decorridoMs)
        assertTrue(n.janelaDeTrocaAberta())
        amb.agora += 4_001
        assertFalse(n.janelaDeTrocaAberta())
    }

    @Test
    fun `usb publicado e limpo com registro`() {
        n.aplicarUsb(FaixaUsb("Faixa", null, null, null, "/storage/x/a.mp3", 100_000, 2_000, true))
        assertEquals(RegrasDaMidia.USB, amb.retrato.pacote)
        assertEquals("published", amb.registros.last()["etapa"])
        assertTrue(n.limparUsb())
        assertNull(amb.retrato.pacote)
        assertEquals("cleared", amb.registros.last()["etapa"])
        assertFalse(n.limparUsb())
    }

    @Test
    fun `dica do volante e mudo publicado`() {
        n.dicaDoVolante(true)
        assertEquals(RegrasDaMidia.ANDROID_AUTO, amb.retrato.pacote)
        assertTrue(amb.retrato.tocando)
        n.publicarMudo(true)
        assertTrue(amb.retrato.mudo)
    }
}
