package br.com.opengw.luz

import br.com.opengw.veiculo.HeadlightPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Leitura das preferências com valores estranhos, regra de "ativo" e "último aviso vence o cache". */
class AjustesDaLuzTest {

    private fun ler(vararg pares: Pair<String, Any?>) = AjustesDaLuz.ler(FonteDeMapa(mapOf(*pares)))

    @Test
    fun `sem nada gravado vale o padrao`() {
        val a = ler()
        assertFalse(a.ligado)
        assertFalse(a.temFita)
        assertFalse(a.dinamica)
        assertFalse(a.animacoes)
        assertFalse(a.musica)
        assertEquals(ModoMusical.GRAVES, a.modoMusical)
        assertEquals(EfeitoDoAlbum.FORWARD_6_BU, a.efeitoDoAlbum)
        assertEquals(50, a.velocidadeDoAlbum)
        assertEquals(SaidaDaLuz.DMX, a.saidaDoAlbum)
        assertEquals(ModoDoAlbum.ANIMADO, a.modoBleDoAlbum)
        assertEquals(ModoDoAlbum.ANIMADO, a.modoDmxDoAlbum)
        assertEquals(OrdemDeCor.RBG, a.ordemDmx)
        assertEquals(OrdemDeCor.RGB, a.ordemBle)
        assertEquals(100, a.brilho)
        assertEquals(SaidaDaLuz.DMX, a.saida)
        assertTrue(a.reconectar)
        assertEquals(0f, a.matiz, 0f)
        assertEquals(ReacaoDaLuz.entries.toSet() - ReacaoDaLuz.SETA, a.reacoes)
        assertEquals("auto", a.fonteDoFarol)
        assertFalse(a.sorrateiro)
    }

    @Test
    fun `chaves no formato ja gravado nos carros`() {
        val a = ler(
            "ambientLightBleEnabled" to true,
            "ambientLightBleDeviceMac" to " AA:BB:CC:DD:EE:FF ",
            "ambientLightBleDeviceName" to "LEDCAR-01-00DD",
            "ambientLightSyncDriveMode" to true,
            "ambientLightAnimationsEnabled" to true,
            "ambientLightMusicAnimationEnabled" to true,
            "ambientLightMusicMode" to "album_wave",
            "ambientLightAlbumEffect" to "pulse",
            "ambientLightAlbumEffectSpeed" to 80,
            "ambientLightAlbumEffectOutput" to "BOTH",
            "ambientLightAlbumBleMode" to "STATIC",
            "ambientLightAlbumDmxMode" to "static",
            "ambientLightColorOrder" to "grb",
            "ambientLightBleColorOrder" to "BGR",
            "ambientLightBrightnessPercent" to 40,
            "ambientLightOutput" to "ble",
            "ambientLightAutoReconnect" to false,
            "ambientLightManualHue" to 200f,
            "ambientLightReactTurnSignal" to true,
            "ambientLightReactHazard" to false,
            "nightModeTrigger" to "clock",
            "stealthModeActive" to true,
        )
        assertTrue(a.ligado)
        assertEquals("AA:BB:CC:DD:EE:FF", a.endereco)
        assertEquals("LEDCAR-01-00DD", a.nome)
        assertTrue(a.dinamica && a.animacoes && a.musica)
        assertEquals(ModoMusical.ONDA_DO_ALBUM, a.modoMusical)
        assertEquals(EfeitoDoAlbum.PULSE, a.efeitoDoAlbum)
        assertEquals(80, a.velocidadeDoAlbum)
        assertEquals(SaidaDaLuz.AMBAS, a.saidaDoAlbum)
        assertEquals(ModoDoAlbum.ESTATICO, a.modoBleDoAlbum)
        assertEquals(ModoDoAlbum.ESTATICO, a.modoDmxDoAlbum)
        assertEquals(OrdemDeCor.GRB, a.ordemDmx)
        assertEquals(OrdemDeCor.BGR, a.ordemBle)
        assertEquals(40, a.brilho)
        assertEquals(SaidaDaLuz.BLE, a.saida)
        assertFalse(a.reconectar)
        assertEquals(CorLed(0, 170, 255), a.corManual)
        assertTrue(ReacaoDaLuz.SETA in a.reacoes)
        assertFalse(ReacaoDaLuz.PISCA_ALERTA in a.reacoes)
        assertEquals("clock", a.fonteDoFarol)
        assertTrue(a.sorrateiro)
    }

    @Test
    fun `valores estranhos caem no padrao e numeros sao limitados`() {
        val a = ler(
            "ambientLightBleEnabled" to "sim",
            "ambientLightBleDeviceMac" to "   ",
            "ambientLightMusicMode" to "ROCK",
            "ambientLightAlbumEffect" to 13,
            "ambientLightAlbumEffectSpeed" to 500,
            "ambientLightAlbumEffectOutput" to "AMBAS",
            "ambientLightColorOrder" to "rgbx",
            "ambientLightBleColorOrder" to null,
            "ambientLightBrightnessPercent" to -20,
            "ambientLightOutput" to "",
            "ambientLightManualHue" to 999.0,
        )
        assertFalse(a.ligado)
        assertNull(a.endereco)
        assertFalse(a.temFita)
        assertEquals(ModoMusical.GRAVES, a.modoMusical)
        assertEquals(EfeitoDoAlbum.FORWARD_6_BU, a.efeitoDoAlbum)
        assertEquals(100, a.velocidadeDoAlbum)
        assertEquals(SaidaDaLuz.DMX, a.saidaDoAlbum)
        assertEquals(OrdemDeCor.RBG, a.ordemDmx)
        assertEquals(OrdemDeCor.RGB, a.ordemBle)
        assertEquals(0, a.brilho)
        assertEquals(SaidaDaLuz.DMX, a.saida)
        assertEquals(360f, a.matiz, 0f)
        assertEquals(1, ler("ambientLightAlbumEffectSpeed" to 0).velocidadeDoAlbum)
        assertEquals(100, ler("ambientLightBrightnessPercent" to 250).brilho)
        assertEquals(0f, ler("ambientLightManualHue" to -10f).matiz, 0f)
        assertEquals(0f, ler("ambientLightManualHue" to Float.NaN).matiz, 0f)
    }

    @Test
    fun `matiz gravada como numero de outro tipo ainda vale`() {
        assertEquals(120f, ler("ambientLightManualHue" to 120).matiz, 0f)
        assertEquals(90.5f, ler("ambientLightManualHue" to "90,5").matiz, 0f)
    }

    @Test
    fun `cor-base estatica`() {
        val semDinamica = ler("ambientLightManualHue" to 120f)
        assertEquals(CorLed(0, 255, 0), semDinamica.corBase(ModoDeConducao.SPORT))
        val comDinamica = ler("ambientLightSyncDriveMode" to true, "ambientLightManualHue" to 120f)
        assertEquals(CorLed(255, 0, 0), comDinamica.corBase(ModoDeConducao.SPORT))
        val semReacaoDoModo = ler("ambientLightSyncDriveMode" to true, "ambientLightManualHue" to 120f, "ambientLightReactDriveMode" to false)
        assertEquals(CorLed(0, 255, 0), semReacaoDoModo.corBase(ModoDeConducao.SPORT))
    }

    @Test
    fun `regra de ativo`() {
        for (v in listOf(null, "", " ", "0", " 0 ", "{0,0,0,0}", "{0,0,0,0,0}", "false", "FALSE", "null", "undefined", "unknown", "--", "nan", "NaN")) {
            assertFalse("'$v' devia ser desligado", HeadlightPolicy.ligada(v))
        }
        for (v in listOf("1", "2", "true", "{0,1,0,0}", "abc")) {
            assertTrue("'$v' devia ser ligado", HeadlightPolicy.ligada(v))
        }
    }

    @Test
    fun `ultimo aviso vence o cache`() {
        val cache = mutableMapOf("seta" to "1", "farol" to "1")
        val m = MemoriaDosSinais()
        // Nunca chegou aviso: lê o cache.
        assertEquals("1", m.valor("seta") { cache[it] })
        // O aviso de "desligou" chega ANTES de o cache mudar: vale o aviso.
        m.receber("seta", "0")
        assertEquals("0", m.valor("seta") { cache[it] })
        // Aviso nulo não apaga o último valor.
        m.receber("seta", null)
        assertEquals("0", m.valor("seta") { cache[it] })
        // Outra chave continua no cache.
        assertEquals("1", m.valor("farol") { cache[it] })
        m.limpar()
        assertEquals("1", m.valor("seta") { cache[it] })
    }

    @Test
    fun `chaves acompanhadas pela luz`() {
        val esperadas = setOf(
            "car.basic.hazard_light_status",
            "car.ipk_info.bsd_lca_warning_reqleft",
            "car.ipk_info.bsd_lca_warning_reqright",
            "car.basic.left_turn_light_status",
            "car.basic.right_turn_light_status",
            "car.basic.head_light_status",
            "car.basic.low_beam_light_status",
            "car.basic.high_beam_light_status",
            "car.drive.setting.outline_lamps_state",
        )
        assertEquals(esperadas, ChavesDoCarro.ALERTA)
        assertEquals("car.drive_setting.drive_mode", ChavesDoCarro.MODO_DE_CONDUCAO)
        assertEquals("car.light_setting.ambient_light.rhythmic_switch", ChavesDoCarro.RITMO_DA_LUZ_ORIGINAL)
        assertEquals("car.light_setting.ambient_light.sync_music_freq", ChavesDoCarro.FREQUENCIA_DA_MUSICA)
    }
}
