// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 3 (e 2-B): vetores V3, V7–V15 (V15 não portado), V23, V24 e as regras do orquestrador. */
class RegrasDoEspelhamentoTest {

    private val r = RegrasDoEspelhamento
    private val app = "br.com.deepgwt"

    @Test
    fun `V3 - rotulo e icone`() {
        val doSistema = { p: String -> if (p == "x.nao.instalado") null else "do sistema" }
        fun rot(p: String, nome: String? = null) = RotulosDosApps.rotulo(p, nome, doSistema)
        assertEquals("Meu Som", rot("com.spotify.music", "Meu Som"))
        assertNull(RotulosDosApps.iconeFixo("com.spotify.music"))
        assertEquals("Android Auto", rot("com.ts.androidauto.app"))
        assertEquals(IconeFixo.ANDROID_AUTO, RotulosDosApps.iconeFixo("com.ts.androidauto.app"))
        assertEquals("Android Auto", rot("com.google.android.projection.gearhead"))
        assertEquals(IconeFixo.ANDROID_AUTO, RotulosDosApps.iconeFixo("com.google.android.projection.gearhead"))
        assertEquals("Apple CarPlay", rot("com.zjinnova.zlink"))
        assertEquals(IconeFixo.CARPLAY, RotulosDosApps.iconeFixo("com.zjinnova.zlink"))
        assertEquals("YouTube", rot("COM.GOOGLE.ANDROID.YOUTUBE"))
        assertEquals(IconeFixo.YOUTUBE, RotulosDosApps.iconeFixo("COM.GOOGLE.ANDROID.YOUTUBE"))
        assertEquals("YouTube Music", rot("com.google.android.apps.youtube.music"))
        assertEquals(IconeFixo.YOUTUBE_MUSIC, RotulosDosApps.iconeFixo("com.google.android.apps.youtube.music"))
        assertEquals("do sistema", rot("com.beantechs.hvac"))
        assertEquals(IconeFixo.GWM, RotulosDosApps.iconeFixo("com.beantechs.hvac"))
        assertEquals("x.nao.instalado", rot("x.nao.instalado"))
        assertNull(RotulosDosApps.iconeFixo("x.nao.instalado"))
        assertEquals("Android Auto", rot("carplayandroidauto"))
        assertEquals(IconeFixo.ANDROID_AUTO, RotulosDosApps.iconeFixo("carplayandroidauto"))
        assertEquals("o PackageManager que lança vira o pacote", "a.b", RotulosDosApps.rotulo("a.b", null) { error("x") })
    }

    @Test
    fun `V7 - classificacao`() {
        for (p in listOf(
            "com.ts.carplay.app", "com.ts.carplay", "COM.TS.CARPLAY.APP", "com.zjinnova.zlink",
            "com.beantechs.carlink", "com.ts.carplay.app:remote", "carplayandroidauto",
        )) assertEquals(p, "com.ts.carplay.app", r.pacoteDeEspelhamento(p))
        for (p in listOf(
            "com.ts.androidauto.app", "com.ts.androidauto", "com.ts.androidauto.projectionservice",
            "com.google.android.projection.gearhead",
        )) assertEquals(p, "com.ts.androidauto.app", r.pacoteDeEspelhamento(p))
        for (p in listOf("com.apple.ottocast", "com.google.android.apps.auto", "com.android.settings", "com.spotify.music", "", null)) {
            assertNull(p, r.pacoteDeEspelhamento(p))
        }
        assertTrue(Espelhamento.ehEspelhamento("com.ts.carplay"))
        assertFalse(Espelhamento.ehEspelhamento("com.waze"))
    }

    @Test
    fun `V8 - USB de projecao`() {
        for (t in listOf("CONFIGURED", "configured", " CONNECTED ", "CONFIGURED\n", "x\nCONNECTED")) assertTrue(t, r.usbConfigurado(t))
        for (t in listOf("DISCONNECTED", "", "NOT_CONFIGURED", "CONFIGURED_X", "command echo\nDISCONNECTED")) assertFalse(t, r.usbConfigurado(t))
        assertFalse(r.usbConfigurado(null))
    }

    @Test
    fun `V9 - superficie`() {
        assertEquals(1 to 1, r.lerBuffer("activeBuffer=[   1x   1:  64,RGBx_8888]"))
        assertEquals(1920 to 720, r.lerBuffer("activeBuffer=[1920x 720:1920,Unknown 0x7fa30c06]"))
        assertEquals(1888 to 696, r.lerBuffer("activeBuffer=[1888x 696:1920,Unknown 0x7f91d2a8c4]"))
        assertEquals(0 to 0, r.lerBuffer("activeBuffer=[   0x   0:   0,Unknown/None]"))
        assertTrue(r.superficieVelha(r.lerBuffer("activeBuffer=[   1x   1:  64,RGBx_8888]")))
        assertFalse(r.superficieVelha(r.lerBuffer("activeBuffer=[1920x 720:1920,Unknown 0x7fa30c06]")))
        assertFalse(r.superficieVelha(r.lerBuffer("activeBuffer=[1888x 696:1920,Unknown 0x7f91d2a8c4]")))
        assertTrue(r.superficieVelha(r.lerBuffer("activeBuffer=[   0x   0:   0,Unknown/None]")))
        assertTrue(r.superficieVelha(r.lerBuffer("activeBuffer=[1x720:1920]")))
        assertTrue(r.superficieVelha(r.lerBuffer("activeBuffer=[1920x1:1920]")))
        assertFalse(r.superficieVelha(r.lerBuffer("activeBuffer=[2x2:2]")))
        assertNull(r.lerBuffer("sem nada"))
        assertFalse(r.superficieVelha(null))
    }

    // Trecho de `dumpsys SurfaceFlinger` com a camada do CarPlay da central: a camada tem o tamanho
    // do buffer menos a moldura (1888x696), e a camada que vem antes está vazia.
    private val dumpDoCarPlay = listOf(
        "Visible layers (count = 97)",
        "+ BufferLayer (StatusBar#0)",
        "      layerStack=   0, z=   300000, pos=(0,0), size=(1920, 48)",
        "      activeBuffer=[   0x   0:   0,Unknown/None], queued-frames=1",
        "--",
        "+ BufferLayer (SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0)",
        "      layerStack=   3, z=       -1, pos=(8,8), size=(1888, 696)",
        "      activeBuffer=[1888x 696:1920,Unknown 0x7f91d2a8c4], queued-frames=1",
        "+ ColorLayer (Background for -SurfaceView - com.ts.carplay.app/com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity#0)",
        "      activeBuffer=[   0x   0:   0,Unknown/None], queued-frames=0",
    ).joinToString("\n")

    @Test
    fun `V9 - bloco certo dentro do dump`() {
        val cp = MotorDoEspelhamento.CAMADA_DO_CARPLAY
        assertEquals(1888 to 696, r.bufferDaCamada(dumpDoCarPlay, cp))
        assertFalse(r.superficieVelha(dumpDoCarPlay, cp))

        val aa = MotorDoEspelhamento.CAMADA_DO_ANDROID_AUTO
        val bom = "+ BufferLayer ($aa#0)\n      activeBuffer=[1920x1088:1920,Unknown 0x1], queued-frames=0\n+ ColorLayer (x)"
        val velho = "+ BufferLayer ($aa#0)\n      activeBuffer=[   0x   0:   0,Unknown/None], queued-frames=0\n+ ColorLayer (x)"
        assertFalse(r.superficieVelha(bom, aa))
        assertTrue(r.superficieVelha(velho, aa))
        assertFalse("sem bloco = desconhecido = não velha", r.superficieVelha(dumpDoCarPlay, aa))
        assertFalse(r.superficieVelha("+ BufferLayer ($aa#0)\n  nada\n", aa))
    }

    @Test
    fun `V10 - reuso da instancia`() {
        assertTrue(r.reusoDaInstancia("Warning: Activity not started, its current task has been brought to the front"))
        assertTrue(r.reusoDaInstancia("Warning: Activity not started, intent has been delivered to currently running top-most instance."))
        assertFalse(r.reusoDaInstancia("Starting: Intent { cmp=a/b }"))
        assertFalse(r.reusoDaInstancia("Activity not started"))
    }

    @Test
    fun `V11 - limpeza do AA sem conexao`() {
        fun rem(usb: Boolean, canal: Boolean, quer: Boolean, rota: Boolean, agora: Long, tol: Long) =
            r.removerAaSemConexao(usb, canal, true, quer, rota, 1000, agora, tol)
        assertFalse(rem(false, false, true, true, 12000, 10000))
        assertFalse(rem(true, false, true, true, 12000, 10000))
        assertFalse(rem(false, true, true, true, 12000, 10000))
        assertFalse(rem(false, false, true, false, 40000, 30000))
        assertFalse(rem(false, false, false, true, 40000, 30000))
        assertFalse(rem(false, false, false, false, 20000, 30000))
        assertTrue(rem(false, false, false, false, 40000, 30000))
    }

    @Test
    fun `V12 - saude do CarPlay`() {
        val desde = r.desdeDaSaude(1000.0, 1003.0)
        assertEquals(1001.2, desde, 1e-9)
        val codec = "1001.5 1000 1234 1250 W NdkMediaCodec: dequeueInputBuffer invalid bufidx-1"
        assertTrue(r.saudeDoCarPlay(List(6) { codec }.joinToString("\n"), desde).pedeRecriar)
        assertFalse(r.saudeDoCarPlay(List(5) { codec }.joinToString("\n"), desde).pedeRecriar)
        val antiga = codec.replace("1001.5", "1000.1")
        assertFalse(r.saudeDoCarPlay(List(6) { antiga }.joinToString("\n"), desde).pedeRecriar)
        val superficie = "1001.6 1000 1234 1250 W CarPlay: jsurface is NULL"
        assertTrue(r.saudeDoCarPlay(List(4) { superficie }.joinToString("\n"), desde).pedeRecriar)
        val sessao = r.saudeDoCarPlay("1001.7 1000 1234 1250 I UsbCarplay: isCarPlayConnected=false", desde)
        assertTrue(sessao.sessaoCaiu)
        assertFalse("só sessão: não recria", sessao.pedeRecriar)
    }

    @Test
    fun `V13 - guardas por janela`() {
        assertTrue(r.guardaDoCarPlayConfere("com.android.settings"))
        assertTrue(r.guardaDoCarPlayConfere(app))
        for (p in listOf("com.android.systemui", "com.beantechs.mediacenter", "com.beantechs.mediacenter.h5.core", "com.beantechs.vehiclecenter",
            "COM.BEANTECHS.VEHICLECENTER", "com.ts.carplay.app", "com.ts.androidauto.app")) {
            assertFalse(p, r.guardaDoCarPlayConfere(p))
        }

        for (p in listOf("com.android.settings", "com.beantechs.hvac", "com.beantechs.launcher", "com.beantechs.applist")) {
            assertEquals(p, AcaoDaGuardaDoAa.FOCO_DE_VIDEO, r.acaoDaGuardaDoAa(p, app))
        }
        for (p in listOf("com.beantechs.avm", "com.beantechs.backcamera", app)) {
            assertEquals(p, AcaoDaGuardaDoAa.CONFERIR, r.acaoDaGuardaDoAa(p, app))
        }
        assertNull(r.acaoDaGuardaDoAa("com.ts.carplay.app", app))
        assertNull(r.acaoDaGuardaDoAa("com.ts.androidauto.app", app))

        val f = AcaoDaGuardaDoAa.FOCO_DE_VIDEO
        val c = AcaoDaGuardaDoAa.CONFERIR
        assertTrue(r.pularGuardaDoAa("x", f, 1000, "x", f, 2000, 4000))
        assertFalse(r.pularGuardaDoAa("x", f, 1000, "y", f, 2000, 4000))
        assertFalse(r.pularGuardaDoAa("x", f, 1000, "x", c, 2000, 4000))
        assertFalse(r.pularGuardaDoAa(null, null, null, "x", f, 2000, 4000))

        assertTrue(r.aaDeveVoltarAoPainel("com.ts.androidauto.app", true, false, 0))
        assertTrue(r.aaDeveVoltarAoPainel("com.ts.androidauto.projectionservice", true, false, 1))
        assertFalse(r.aaDeveVoltarAoPainel("com.ts.androidauto.app", false, false, 0))
        assertFalse(r.aaDeveVoltarAoPainel("com.ts.androidauto.app", true, true, 0))
        assertFalse(r.aaDeveVoltarAoPainel("com.ts.androidauto.app", true, false, 3))
        assertFalse(r.aaDeveVoltarAoPainel("com.ts.androidauto.app", true, false, null))
        assertFalse(r.aaDeveVoltarAoPainel("com.beantechs.applist", true, false, 0))

        assertTrue(r.aaElegivelParaGuardas("com.ts.androidauto.app", true))
        assertFalse(r.aaElegivelParaGuardas("com.ts.androidauto.app", false))
        assertFalse(r.aaElegivelParaGuardas("com.ts.carplay.app", true))
        assertFalse(r.aaElegivelParaGuardas(null, true))
    }

    @Test
    fun `V14 - USB, reconexao, quarentena e instabilidade`() {
        assertFalse(r.toleranciaDeReconexao(12000, 0, 10000, 4000))
        assertFalse(r.toleranciaDeReconexao(12000, 11000, 10000, 4000))
        assertTrue(r.toleranciaDeReconexao(12500, 9000, 10000, 4000))
        assertFalse(r.toleranciaDeReconexao(15000, 9000, 10000, 4000))

        assertTrue(r.quarentenaDo0(12500, 9000, 10000, 12000, 4000))
        assertFalse(r.quarentenaDo0(12500, 0, 10000, 12000, 4000))
        assertFalse(r.quarentenaDo0(12500, 9000, 10000, 0, 4000))
        assertFalse(r.quarentenaDo0(17000, 9000, 10000, 12000, 4000))

        assertTrue(r.instabilidade(true, true, false, 10500, 10000, 0, 4000))
        assertTrue(r.instabilidade(true, true, true, 12500, 9000, 10000, 4000))
        assertFalse(r.instabilidade(false, true, false, 10500, 10000, 0, 4000))
        assertFalse(r.instabilidade(true, false, false, 10500, 10000, 0, 4000))
        assertFalse(r.instabilidade(true, true, false, 15000, 10000, 0, 4000))

        assertTrue(r.moverPilhaVivaEhSeguro(0, 1, false))
        assertFalse(r.moverPilhaVivaEhSeguro(0, 2, false))
        assertFalse(r.moverPilhaVivaEhSeguro(0, 1, true))
        assertFalse(r.moverPilhaVivaEhSeguro(3, 1, false))
    }

    @Test
    fun `V23 - pid vivo`() {
        for (s in listOf("", "0", "-5", "pidof: x", "12a")) assertFalse(s, r.pidVivo(s))
        for (s in listOf("123", "0 456", "28494 32141")) assertTrue(s, r.pidVivo(s))
    }

    @Test
    fun `V24 - velocidade`() {
        for (s in listOf(null, "", "  ", "--", "abc")) assertNull(s, r.velocidade(s))
        assertEquals(0.0, r.velocidade("0")!!, 0.0)
        assertEquals(12.5, r.velocidade("12,5")!!, 0.0)
        assertEquals(-3.0, r.velocidade("-3")!!, 0.0)
        assertEquals(14.5, r.velocidade("speed=14,5 km/h")!!, 0.0)
        assertEquals(1.2, r.velocidade("1.2.3")!!, 0.0)
        assertEquals(100.75, r.velocidade("100.75km")!!, 0.0)
    }

    @Test
    fun `K1 - estado observado e atualizado`() {
        assertEquals(EstadoDoCarPlay.NO_PAINEL, r.estadoObservado(true, true))
        assertEquals(EstadoDoCarPlay.NA_PRINCIPAL, r.estadoObservado(false, true))
        assertEquals(EstadoDoCarPlay.DESCONECTADO, r.estadoObservado(false, false))
        assertEquals(
            EstadoDoCarPlay.PREPARANDO_O_PAINEL,
            r.estadoAtualizado(EstadoDoCarPlay.PREPARANDO_O_PAINEL, EstadoDoCarPlay.NA_PRINCIPAL, reconciliar = false),
        )
        assertEquals(
            EstadoDoCarPlay.NA_PRINCIPAL,
            r.estadoAtualizado(EstadoDoCarPlay.PREPARANDO_O_PAINEL, EstadoDoCarPlay.NA_PRINCIPAL, reconciliar = true),
        )
        assertEquals(
            EstadoDoCarPlay.VOLTANDO_A_PRINCIPAL,
            r.estadoAtualizado(EstadoDoCarPlay.VOLTANDO_A_PRINCIPAL, EstadoDoCarPlay.NO_PAINEL, reconciliar = false),
        )
        assertEquals(
            EstadoDoCarPlay.NO_PAINEL,
            r.estadoAtualizado(EstadoDoCarPlay.EM_RECUPERACAO, EstadoDoCarPlay.NO_PAINEL, reconciliar = false),
        )
    }

    @Test
    fun `C9 - abertura automatica`() {
        val aaNo3 = ConfigDoApp("com.ts.androidauto.app", "a", 3, 0, 0, 1920, 720)
        assertEquals(aaNo3, r.configDeAberturaAutomatica(aaNo3, moverProjecaoAoPainel = true))
        assertEquals(0, r.configDeAberturaAutomatica(aaNo3, moverProjecaoAoPainel = false).display)
        val spotify = ConfigDoApp("com.spotify.music", "a", 3, 0, 0, 1920, 720)
        assertEquals(spotify, r.configDeAberturaAutomatica(spotify, moverProjecaoAoPainel = false))
        val aaNo0 = aaNo3.copy(display = 0)
        assertEquals(aaNo0, r.configDeAberturaAutomatica(aaNo0, moverProjecaoAoPainel = false))
    }

    @Test
    fun `H3 - token do boot`() {
        assertEquals("4a3f0c2e-1b2d-4e5f-9a8b-7c6d5e4f3a2b", r.tokenDoBoot("4a3f0c2e-1b2d-4e5f-9a8b-7c6d5e4f3a2b\n"))
        assertNull(r.tokenDoBoot("cat: permission denied"))
        assertNull(r.tokenDoBoot(null))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
