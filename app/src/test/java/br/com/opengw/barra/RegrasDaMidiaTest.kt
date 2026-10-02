package br.com.opengw.barra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores da especificação da mídia. */
class RegrasDaMidiaTest {

    private val r = RegrasDaMidia
    private val nenhuma = CentralDeMidiaLida.NENHUMA
    private val usb = CentralDeMidiaLida(2, null)

    @Test
    fun `estado usb da projecao caso 34 e laco caso 35`() {
        listOf("CONFIGURED", "configured", " CONNECTED ", "x\nCONFIGURED\n").forEach { assertTrue(it, r.usbDaProjecaoPronto(it)) }
        listOf("DISCONNECTED", "", "unknown", null).forEach { assertFalse(it.toString(), r.usbDaProjecaoPronto(it)) }
        assertEquals(LimpezaPorDesconexao.CARPLAY, r.limpezaPorDesconexao("com.ts.carplay", false))
        assertEquals(LimpezaPorDesconexao.CARPLAY, r.limpezaPorDesconexao("com.ts.carplay.app", true))
        assertEquals(LimpezaPorDesconexao.ANDROID_AUTO, r.limpezaPorDesconexao("com.ts.androidauto", false))
        assertEquals(LimpezaPorDesconexao.NADA, r.limpezaPorDesconexao("com.ts.androidauto", true))
        assertEquals(LimpezaPorDesconexao.NADA, r.limpezaPorDesconexao("com.spotify", false))
    }

    @Test
    fun `reserva do android auto caso 11`() {
        fun res(c: String?, meta: Boolean, dono: String?, painel: String, pronta: Boolean, central: CentralDeMidiaLida = nenhuma) =
            r.reservaDoAndroidAuto(c, meta, dono, painel, central) { pronta }
        val nativo = CentralDeMidiaLida(402, null)
        assertTrue(res("com.android.bluetooth", true, "com.ts.androidauto", "", true))
        assertFalse(res("com.android.bluetooth", true, "com.ts.androidauto", "", false))
        assertTrue(res("com.onecar.onlinemusic", true, null, "com.ts.androidauto.app", true))
        assertFalse(res("com.android.bluetooth", true, "com.ts.carplay", "", true))
        assertFalse(res("com.android.bluetooth", true, null, "com.ts.carplay.app", true))
        assertFalse(res("com.ts.androidauto.app", true, "com.ts.androidauto", "", true))
        assertFalse(res("com.android.bluetooth", false, "com.ts.androidauto", "", true))
        assertFalse(res("com.spotify.music", true, "com.ts.androidauto", "", true))
        assertFalse(res("com.beantechs.mediacenter", true, "com.ts.androidauto", "", true))
        assertTrue(res("com.beantechs.mediacenter", true, null, "", true, nativo))
    }

    @Test
    fun `manter a projecao caso 12`() {
        assertTrue(r.manterProjecao("com.ts.carplay", "com.android.bluetooth", true, true))
        assertFalse(r.manterProjecao("com.ts.carplay", "com.spotify.music", true, true))
        assertTrue(r.manterProjecao("com.ts.carplay", "com.spotify.music", false, true))
        assertFalse(r.manterProjecao("com.ts.androidauto", "com.android.bluetooth", true, false))
        assertTrue(r.manterProjecao("com.ts.androidauto", "com.android.bluetooth", true, true))
        assertFalse(r.manterProjecao("com.spotify", "com.android.bluetooth", true, true))
    }

    @Test
    fun `preservar dono usb caso 13`() {
        val u = RegrasDaMidia.USB
        assertTrue(r.preservarDonoUsb(u, usb, null, false))
        assertTrue(r.preservarDonoUsb(u, usb, "com.beantechs.mediacenter", true))
        assertFalse(r.preservarDonoUsb(u, usb, "com.spotify", true))
        assertTrue(r.preservarDonoUsb(u, usb, "com.spotify", false))
        assertFalse(r.preservarDonoUsb(u, usb, "com.ts.carplay", false))
        assertFalse(r.preservarDonoUsb(u, CentralDeMidiaLida(10, null), null, false))
        assertFalse(r.preservarDonoUsb("com.spotify", usb, null, false))
    }

    @Test
    fun `publicar usb caso 38`() {
        fun p(dono: String?, painel: String = "", conc: String? = null, aa: Boolean = false, tocando: Boolean = true, c: CentralDeMidiaLida = usb) =
            r.publicarUsb(c, dono, painel, conc, aa, tocando)
        assertTrue(p(null))
        assertFalse(p(null, tocando = false))
        assertTrue(p(RegrasDaMidia.USB, tocando = false))
        assertFalse(p(null, conc = "com.spotify"))
        assertFalse(p("com.ts.carplay"))
        assertFalse(p(null, painel = "com.ts.carplay.app"))
        assertFalse(p("com.ts.androidauto", aa = true))
        assertTrue(p("com.ts.androidauto", aa = false, tocando = true))
        assertFalse(p(null, c = CentralDeMidiaLida(2, 10)))
        assertFalse(p(null, c = CentralDeMidiaLida(1, null)))
    }

    @Test
    fun `sinais do radio caso 28`() {
        assertTrue(r.sinalDoRadioProtege("sys.radio.play_state", "1", false))
        assertTrue(r.sinalDoRadioProtege("sys.radio.play_state", "PLAYING", false))
        assertFalse(r.sinalDoRadioProtege("sys.radio.play_state", "0", false))
        assertFalse(r.sinalDoRadioProtege("sys.radio.play_state", "", false))
        assertTrue(r.sinalDoRadioProtege("sys.basic.audio.source_app", "com.beantechs.Radio", false))
        assertFalse(r.sinalDoRadioProtege("sys.radio.cur_channel_info", "{103900,0,1,1}", true))
        assertTrue(r.sinalDoRadioProtege("sys.radio.cur_channel_info", "{103900,0,1,1}", false))
        assertFalse(r.sinalDoRadioProtege("sys.radio.cur_channel_info", "{0,0,0}", false))
        assertFalse(r.sinalDoRadioProtege("sys.radio.cur_channel_info", "{}", false))
        assertFalse(r.sinalDoRadioProtege("sys.radio.cur_channel_info", "0", false))
    }

    @Test
    fun `leitura da central`() {
        assertTrue(CentralDeMidiaLida(10, null).radioLocal)
        assertTrue(CentralDeMidiaLida(null, 14).radioLocal)
        assertFalse(CentralDeMidiaLida(15, null).radioLocal)
        val aa = CentralDeMidiaLida(402, null)
        assertTrue(aa.androidAutoNativo && aa.ignorarProtecaoPorAA)
        val aaComRadio = CentralDeMidiaLida(402, 12)
        assertTrue(aaComRadio.androidAutoNativo && !aaComRadio.ignorarProtecaoPorAA)
        assertTrue(CentralDeMidiaLida(2, null).usb)
    }

    @Test
    fun `estado aceito caso 25`() {
        assertTrue(r.estadoAceito(402, 7))
        assertTrue(r.estadoAceito(7, 7))
        assertTrue(r.estadoAceito(0, 7))
        assertFalse(r.estadoAceito(5, 7))
        assertFalse(r.estadoAceito(5, null))
    }

    @Test
    fun `decisao efetiva caso 52`() {
        assertTrue(r.reproducaoEfetiva(false, null, null, true, false, false))
        assertTrue(r.reproducaoEfetiva(false, true, null, false, false, false))
        assertTrue(r.reproducaoEfetiva(true, null, null, null, false, false))
        assertTrue(r.reproducaoEfetiva(false, false, null, null, true, true))
        assertFalse(r.reproducaoEfetiva(false, false, null, null, false, true))
        assertTrue(r.reproducaoEfetiva(false, false, 1, null, false, false))
        assertFalse(r.reproducaoEfetiva(true, false, 2, null, false, true))
    }

    @Test
    fun `verificar caso 53`() {
        assertTrue(r.verificar(true, true, null, false))
        assertFalse(r.verificar(true, false, null, false))
        assertTrue(r.verificar(false, false, null, true))
        assertFalse(r.verificar(false, false, null, false))
        assertFalse(r.verificar(false, true, null, true))
    }

    @Test
    fun `alvo segurado caso 58`() {
        fun novo() = AlvoSegurado().apply { marcar(true, 10_000, 60_000) }
        assertEquals(true, novo().resolver(null, null, 12_000))
        assertEquals(true, novo().resolver(true, 61_000, 12_000))
        assertEquals(true, novo().resolver(false, 61_000, 12_000))
        assertEquals(false, novo().resolver(false, 64_000, 19_000))
        assertEquals(true, novo().resolver(false, 62_000, 19_000))
        assertEquals(false, novo().resolver(false, 60_000, 56_000))
        assertNull(novo().resolver(null, null, 56_000))
        // Escapou: o alvo foi esquecido.
        val a = novo()
        a.resolver(false, 64_000, 19_000)
        assertNull(a.resolver(null, null, 20_000))
    }

    @Test
    fun `intervalo minimo casos 50 a 52`() {
        assertTrue(r.aceitaComando(500, 0, 650))
        assertFalse(r.aceitaComando(1_000, 500, 650))
        assertTrue(r.aceitaComando(1_200, 500, 650))
        assertFalse(r.aceitaComando(2_400, 500, 2_000))
        assertTrue(r.aceitaComando(2_600, 500, 2_000))
    }

    @Test
    fun `estimativa caso 44`() {
        assertEquals(13_000L, r.estimativa(10_000, 200_000, 1_000, 4_000, true))
        assertEquals(10_000L, r.estimativa(10_000, 200_000, 1_000, 4_000, false))
        assertEquals(200_000L, r.estimativa(199_000, 200_000, 1_000, 9_000, true))
        assertEquals(0L, r.estimativa(-5, 0, 1_000, 9_000, true))
    }

    @Test
    fun `segurar regressao aa caso 42`() {
        val aa = "com.ts.androidauto"
        fun s(ant: Long, rec: Long, durAnt: Long = 200_000, dono: String = aa, tocando: Boolean = true, permitida: Boolean = false) =
            r.segurarRegressaoAA(dono, ant, rec, durAnt, 200_000, tocando, permitida)
        assertTrue(s(60_000, 0))
        assertTrue(s(60_000, 800))
        assertFalse(s(60_000, 59_000))
        assertTrue(s(60_000, 58_000))
        assertFalse(s(2_000, 0))
        assertFalse(s(199_000, 0))
        assertFalse(s(60_000, 0, permitida = true))
        assertFalse(s(60_000, 0, dono = "com.spotify"))
        assertFalse(s(60_000, 5_000, tocando = false))
        assertFalse(s(60_000, 0, durAnt = 180_000))
    }

    @Test
    fun `progresso nativo caso 43`() {
        fun p(nativo: Long?, mudou: Boolean) = r.progressoNativoResolvido(60_000, 200_000, 1_000, 3_000, true, mudou, nativo)
        assertEquals(62_000L, p(0, false))
        assertEquals(0L, p(0, true))
        assertEquals(61_500L, p(61_500, false))
        assertEquals(0L, p(null, true))
        assertEquals(62_000L, p(null, false))
    }

    @Test
    fun `progresso aplicado ao retrato caso 41`() {
        val base = MidiaAgora.VAZIA.copy(pacote = "com.ts.androidauto", tocando = true, duracaoMs = 200_000, decorridoMs = 60_000, progressoAtualizadoEmMs = 1_000)
        // Zero transitório da central no meio da faixa: segura posição e hora.
        val segurado = r.comProgresso(base, base, 200_000, 0, 5_000, false, false)
        assertEquals(60_000L, segurado.decorridoMs)
        assertEquals(1_000L, segurado.progressoAtualizadoEmMs)
        // Permitida (troca de faixa): vai a zero, com a hora de agora (há duração).
        val trocado = r.comProgresso(base, base, 200_000, 0, 5_000, false, true)
        assertEquals(0L, trocado.decorridoMs)
        assertEquals(5_000L, trocado.progressoAtualizadoEmMs)
        // Sem duração e sem posição: hora 0; pode buscar só com duração.
        val semNada = r.comProgresso(MidiaAgora.VAZIA, MidiaAgora.VAZIA, 0, 0, 5_000, true, false)
        assertEquals(0L, semNada.progressoAtualizadoEmMs)
        assertFalse(semNada.podeBuscar)
        val comDuracao = r.comProgresso(MidiaAgora.VAZIA, MidiaAgora.VAZIA, 1_000, 2_000, 5_000, true, false)
        assertEquals(1_000L, comDuracao.decorridoMs)
        assertTrue(comDuracao.podeBuscar)
    }

    @Test
    fun `mudo casos 60 e 61`() {
        listOf("1", "true", " ON ", "muted").forEach { assertEquals(true, r.interpretarMudo(it)) }
        listOf("0", "off", "unmute").forEach { assertEquals(false, r.interpretarMudo(it)) }
        listOf("", "2", null).forEach { assertNull(r.interpretarMudo(it)) }
        assertEquals(true, r.combinarMudo(listOf("0", "1")))
        assertEquals(false, r.combinarMudo(listOf("0", "0")))
        assertNull(r.combinarMudo(emptyList()))
        assertNull(r.combinarMudo(listOf(null, "")))
        assertEquals(true, r.mudoComVolume(false, 0, null))
        assertEquals(false, r.mudoComVolume(true, 5, false))
        assertEquals(true, r.mudoComVolume(true, 5, true))
        assertNull(r.mudoComVolume(null, null, null))
    }

    @Test
    fun `capa normalizada caso 15 e amostragem caso 14`() {
        assertEquals(720 to 720, r.tamanhoNormalizado(720, 720))
        assertEquals(300 to 200, r.tamanhoNormalizado(300, 200))
        assertEquals(720 to 360, r.tamanhoNormalizado(1440, 720))
        assertEquals(1 to 720, r.tamanhoNormalizado(1, 3000))
        assertEquals(1, r.amostraParaCapa(1000, 1000))
        assertEquals(2, r.amostraParaCapa(1440, 1440))
        assertEquals(4, r.amostraParaCapa(3000, 3000))
        assertEquals(1, r.amostraAteCaber(1440, 1000))
        assertEquals(4, r.amostraAteCaber(3000, 5000))
    }
}
