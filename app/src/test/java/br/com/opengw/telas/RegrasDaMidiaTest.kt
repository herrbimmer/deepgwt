package br.com.opengw.telas

import br.com.opengw.veiculo.Sinais
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 4: vetores V16–V19, V21, V22, V25 (V20 não portado: decisão 1) e o motor da mídia. */
class RegrasDaMidiaTest {

    private val r = RegrasDaMidia
    private val k = TeclasDaMidia

    @Test
    fun `V16 - ceder ao radio`() {
        for (estado in listOf("1", " 1 ")) {
            assertTrue(r.cedeAoRadio(false, estado, null))
            assertTrue(r.cedeAoRadio(false, estado, "com.spotify.music"))
        }
        for (fonte in listOf("com.beantechs.radio", "fm_radio", "RADIO")) assertTrue(fonte, r.cedeAoRadio(false, "0", fonte))
        for (fonte in listOf("com.ts.androidauto.projectionservice", "com.ts.carplay", "carplay_radio", "com.spotify.music", "", null)) {
            assertFalse("$fonte", r.cedeAoRadio(false, "0", fonte))
        }
        for (estado in listOf("0", "2", "-1", "", null)) assertFalse("$estado", r.cedeAoRadio(false, estado, null))
        assertFalse(r.cedeAoRadio(true, "1", "fm_radio"))
    }

    @Test
    fun `V17 - audio do AA`() {
        val tocando = """
match usage USAGE_AAUTO_MEDIA
ID:111 -- type:android.media.AudioTrack -- u/pid:1000/3728 -- state:started -- attr:AudioAttributes: usage=USAGE_AAUTO_MEDIA content=CONTENT_TYPE_UNKNOWN flags=0x100 tags= bundle=null
ID:119 -- type:android.media.AudioTrack -- u/pid:1000/3728 -- state:idle -- attr:AudioAttributes: usage=USAGE_AAUTO_VR content=CONTENT_TYPE_UNKNOWN flags=0x100 tags= bundle=null
""".trimIndent()
        assertTrue(r.audioDoAaTocando(tocando))
        val parado = tocando.replace("state:started", "state:idle") +
            "\nID:120 -- type:android.media.AudioTrack -- state:started -- attr:AudioAttributes: usage=USAGE_AAUTO_GUIDANCE"
        assertFalse(r.audioDoAaTocando(parado))
        assertFalse(r.audioDoAaTocando(""))
    }

    @Test
    fun `V18 - AA ativo, regra pura`() {
        val aa = "com.ts.androidauto"
        fun ativo(
            no3: String? = null, midia: String? = null, painel: String? = null, canal: Int? = null,
            tarefa: Boolean = false, sessao: Boolean = true, audio: Boolean = false,
        ) = r.aaAtivo(no3, midia, painel, canal, tarefa, sessao, audio)
        assertTrue(ativo(midia = aa))
        assertTrue(ativo(painel = "com.ts.androidauto.app"))
        assertFalse(ativo(midia = "com.ts.carplay"))
        assertFalse(ativo(midia = aa, sessao = false))
        assertFalse(ativo(no3 = aa, sessao = false))
        assertTrue(ativo(canal = 3))
        assertTrue(ativo(canal = 7))
        assertTrue(ativo(tarefa = true))
        assertFalse(ativo(tarefa = true, sessao = false))
        assertFalse(ativo(canal = 1))
        assertTrue(ativo(audio = true, sessao = false))
    }

    @Test
    fun `V19 - display alvo`() {
        assertEquals(3, r.displayAlvo(true, true, 0))
        assertEquals(0, r.displayAlvo(false, true, 3))
        for ((destino, alvo) in listOf(-1 to 0, 0 to 0, 1 to 0, 3 to 3, 4096 to 0)) {
            assertEquals("$destino", alvo, r.displayAlvo(false, false, destino))
        }
    }

    @Test
    fun `V22 - alvo e reconciliacao do volante`() {
        assertEquals(false, r.alvoDoVolante(k.PLAY_PAUSE, null, true))
        assertEquals(true, r.alvoDoVolante(k.PLAY_PAUSE, null, false))
        assertEquals(true, r.alvoDoVolante(k.PLAY_PAUSE, false, true))
        assertEquals(false, r.alvoDoVolante(k.PLAY_PAUSE, true, false))
        assertEquals(true, r.alvoDoVolante(k.PLAY, null, true))
        assertEquals(false, r.alvoDoVolante(k.PAUSE, null, false))
        assertNull(r.alvoDoVolante(k.NEXT, null, false))

        for (t in listOf(k.PLAY_PAUSE, k.PLAY, k.PAUSE, 1004)) assertTrue(r.reconciliarVolante(t, k.UP, true, true, true))
        assertFalse(r.reconciliarVolante(k.PLAY_PAUSE, k.UP, true, true, false))
        assertFalse(r.reconciliarVolante(k.PLAY_PAUSE, k.DOWN, true, true, true))
        assertFalse(r.reconciliarVolante(k.NEXT, k.UP, true, true, true))
        assertFalse(r.reconciliarVolante(k.PLAY_PAUSE, k.UP, false, true, true))
        assertFalse(r.reconciliarVolante(k.PLAY_PAUSE, k.UP, true, false, true))
    }

    @Test
    fun `V25 - duplicata do volante`() {
        val ultima = TeclaAceita(k.PLAY_PAUSE, k.UP, 1000)
        assertTrue(r.duplicataDoVolante(ultima, k.PLAY_PAUSE, k.UP, 1004))
        assertFalse(r.duplicataDoVolante(ultima, k.PLAY_PAUSE, k.UP, 1400))
        assertFalse(r.duplicataDoVolante(ultima, k.PLAY_PAUSE, k.DOWN, 1004))
        assertFalse(r.duplicataDoVolante(ultima, k.NEXT, k.UP, 1004))
        assertTrue(r.duplicataDoVolante(TeclaAceita(k.MUTE, k.UP, 1000), k.MUTE, k.UP, 1001))
        assertFalse(r.duplicataDoVolante(null, k.MUTE, k.UP, 1001))
    }

    @Test
    fun `M1 - ordinal AAP e escolha do aparelho`() {
        assertEquals(6, r.ordinalAap(k.PLAY_PAUSE))
        assertEquals(6, r.ordinalAap(1004))
        assertEquals(7, r.ordinalAap(k.PLAY))
        assertEquals(8, r.ordinalAap(k.PAUSE))
        assertEquals(9, r.ordinalAap(k.PREVIOUS))
        assertEquals(10, r.ordinalAap(k.NEXT))
        assertNull(r.ordinalAap(k.MUTE))

        val usb = AparelhoDoAa(4, 0, 1, false, true)
        val bt = AparelhoDoAa(4, 0, 1, true, false)
        val semFio = AparelhoDoAa(4, 0, 2, false, false)
        val disponivel = AparelhoDoAa(4, 1, 1, false, false)
        val outroModo = AparelhoDoAa(3, 1, 2, true, true)
        assertEquals(disponivel, r.aparelhoParaReconectar(listOf(usb, bt, semFio, disponivel, outroModo)))
        assertEquals(semFio, r.aparelhoParaReconectar(listOf(usb, bt, semFio)))
        assertEquals(bt, r.aparelhoParaReconectar(listOf(usb, bt)))
        assertNull(r.aparelhoParaReconectar(listOf(outroModo)))
    }

    @Test
    fun `A5 - evidencia do DCM vale 180 s`() {
        assertFalse(r.evidenciaDoDcmValida(0, 1000))
        assertTrue(r.evidenciaDoDcmValida(1000, 181_000))
        assertFalse(r.evidenciaDoDcmValida(1000, 181_001))
    }

    // ── motor da mídia ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `volante - tocar e pausar com a rota nativa, duplicata e reconciliacao`() {
        val b = Bancada()
        b.barra.rotaNativa = true
        b.barra.tocandoNativo = true
        b.prefs[ChavesDoLancador.DESTINO_DO_ANDROID_AUTO] = 3

        assertTrue(b.midia.teclaDoVolante(k.PLAY_PAUSE, k.DOWN))
        assertTrue("o UP é outra ação", b.midia.teclaDoVolante(k.PLAY_PAUSE, k.UP))
        assertEquals(listOf("com.ts.androidauto" to false), b.barra.dicas)
        b.relogio.andar(100)
        assertTrue("duplicata", b.midia.teclaDoVolante(k.PLAY_PAUSE, k.UP))
        assertEquals(1, b.barra.dicas.size)

        b.barra.rotaEnvia = false
        b.fundo.rodarTudo()
        assertEquals("pausar sem envio tenta de novo depois de 2,4 s", listOf(false, false), b.barra.alvosPelaRota)
        assertTrue(b.relogio.esperas.containsAll(listOf(900L, 2400L)))
        assertTrue("o app não injeta tecla", b.central.comandos.none { it.startsWith("input keyevent") })

        // tocar: uma tentativa só; uma tecla nova cancela a reconciliação pendente
        b.relogio.andar(1000)
        b.barra.tocandoNativo = false
        assertTrue(b.midia.teclaDoVolante(k.PLAY_PAUSE, k.UP))
        b.relogio.andar(1000)
        assertTrue(b.midia.teclaDoVolante(k.PLAY, k.UP))
        b.fundo.rodarTudo()
        assertEquals("só a última geração age", listOf(false, false, true), b.barra.alvosPelaRota)
    }

    @Test
    fun `volante - teclas fora da lista e AA inativo nao sao tratadas`() {
        val b = Bancada()
        assertFalse(b.midia.teclaDoVolante(24, k.UP))
        assertFalse(b.midia.teclaDoVolante(k.NEXT, 5))
        assertFalse("sem AA ativo", b.midia.teclaDoVolante(k.NEXT, k.UP))
        b.carro.valores[Sinais.SYS_RADIO_PLAY_STATE.chave] = "1"
        b.barra.pacoteDaMidia = "com.ts.androidauto"
        assertFalse("rádio nativo tocando: cede", b.midia.teclaDoVolante(k.NEXT, k.UP))
    }

    @Test
    fun `volante - pular com a rota nativa manda a reserva so se a faixa nao trocou`() {
        val b = Bancada()
        b.barra.rotaNativa = true
        b.canalAa.binderVivo = true
        b.barra.tituloDaMidia = "A"
        assertTrue(b.midia.teclaDoVolante(k.NEXT, k.UP))
        assertEquals(1, b.barra.progressoZerado)
        b.fundo.rodarTudo()
        assertEquals(listOf("next"), b.canalAa.transacoes)

        b.relogio.andar(1000)
        assertTrue(b.midia.teclaDoVolante(k.PREVIOUS, k.UP))
        b.barra.tituloDaMidia = "B"
        b.fundo.rodarTudo()
        assertEquals("a rota nativa já trocou: nada", listOf("next"), b.canalAa.transacoes)
    }

    @Test
    fun `painel - comando de midia so registra com o AA ativo`() {
        val b = Bancada()
        assertFalse(b.midia.comandoDeMidiaDoPainel(1))
        b.barra.rotaNativa = true
        assertTrue(b.midia.comandoDeMidiaDoPainel(2))
        assertFalse(b.midia.comandoDeMidiaDoPainel(3))
        assertTrue(b.midia.mensagemDoPainelMereceDiagnostico(135))
        assertFalse(b.midia.mensagemDoPainelMereceDiagnostico(141))
        assertTrue(b.midia.teclaMereceDiagnostico(1004))
        assertFalse(MidiaDoAndroidAuto.consumirTeclaDaAcessibilidade(85, 1))
        assertTrue(MidiaDoAndroidAuto.pedirFocoDeVideo(false))
    }

    @Test
    fun `M1 - tecla AAP manda DOWN, 70 ms e UP`() {
        val b = Bancada()
        b.canalAa.binderVivo = true
        b.canalAa.statusAtual = 3
        b.central.pilha(3, "com.ts.androidauto.app", "com.ts.androidauto.app.display.AapActivity")
        assertTrue(b.rodar { b.midia.pularFaixa(paraFrente = true, jaPreparado = false) })
        assertEquals(listOf(10 to 0, 10 to 1), b.canalAa.teclas)
        assertTrue(b.relogio.esperas.contains(70L))
        assertTrue(b.central.comandos.contains("am broadcast -a ts.car.androidauto.view_state --es state foreground --ei displayId 3"))
    }

    @Test
    fun `M5 - direto usa play e pause`() {
        val b = Bancada()
        b.canalAa.binderVivo = true
        b.canalAa.statusAtual = 3
        b.central.pilha(0, "com.ts.androidauto.app", "com.ts.androidauto.app.display.AapActivity")
        assertTrue(b.rodar { b.midia.tocarOuPausarDireto(tocandoAgora = true) })
        assertTrue(b.rodar { b.midia.tocarOuPausarDireto(tocandoAgora = false) })
        assertEquals(listOf("pause", "play"), b.canalAa.transacoes)
        assertTrue(b.relogio.esperas.contains(520L))
    }

    @Test
    fun `A4 - audio guardado por 3 s`() {
        val b = Bancada()
        b.central.audio = "ID:1 -- state:started -- usage=USAGE_AAUTO_MEDIA"
        assertTrue(b.midia.audioTocando("x"))
        b.central.audio = ""
        assertTrue("guardado", b.midia.audioTocando("x"))
        assertFalse("forçado", b.midia.audioTocando("x", forcarLeitura = true))
        b.central.audio = "ID:1 -- state:started -- usage=USAGE_AAUTO_MEDIA"
        b.relogio.andar(3_000)
        assertTrue(b.midia.audioTocando("x"))
        b.central.shizuku = false
        b.relogio.andar(3_000)
        assertFalse("sem Shizuku: texto vazio", b.midia.audioTocando("x"))
    }
}
