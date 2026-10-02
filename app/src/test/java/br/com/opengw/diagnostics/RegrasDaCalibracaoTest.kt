package br.com.opengw.diagnostics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Calibração do painel (Alpha 8.8): o que não depende do Android. */
class RegrasDaCalibracaoTest {

    private val r = RegrasDaCalibracao

    private fun raw(w: Int, h: Int, formato: Int, cabecalho: Int, pixels: ByteArray): ByteArray {
        val b = ByteArray(cabecalho + pixels.size)
        fun p32(i: Int, v: Int) { for (k in 0..3) b[i + k] = (v ushr (8 * k)).toByte() }
        p32(0, w); p32(4, h); p32(8, formato)
        pixels.copyInto(b, cabecalho)
        return b
    }

    @Test
    fun `raw do Android 9 tem 12 bytes de cabecalho`() {
        val px = byteArrayOf(10, 20, 30, 0, 40, 50, 60, 0)
        val cru = raw(2, 1, 1, 12, px)
        val lido = r.lerRaw(cru)!!
        assertEquals(RegrasDaCalibracao.Raw(2, 1, 1, 12, 4), lido)
        // Alfa sempre opaco, mesmo com o byte X zerado.
        assertArrayEquals(intArrayOf(0xff0a141e.toInt(), 0xff28323c.toInt()), r.paraArgb(cru, lido))
    }

    @Test
    fun `raw do Android 10 em diante tem 16 bytes`() {
        val cru = raw(1, 1, 1, 16, byteArrayOf(1, 2, 3, 4))
        assertEquals(16, r.lerRaw(cru)!!.cabecalho)
    }

    @Test
    fun `BGRA troca vermelho e azul`() {
        val cru = raw(1, 1, 5, 12, byteArrayOf(0x30, 0x20, 0x10, -1))
        assertArrayEquals(intArrayOf(0xff102030.toInt()), r.paraArgb(cru, r.lerRaw(cru)!!))
    }

    @Test
    fun `RGB 565 expande para 8 bits`() {
        // Branco puro em 565 = 0xFFFF.
        val cru = raw(1, 1, 4, 12, byteArrayOf(-1, -1))
        assertArrayEquals(intArrayOf(0xffffffff.toInt()), r.paraArgb(cru, r.lerRaw(cru)!!))
    }

    @Test
    fun `o que nao e imagem nao passa`() {
        assertNull("curto", r.lerRaw(ByteArray(8)))
        assertNull("PNG com aviso ou truncado", r.lerRaw("\u0089PNG\r\n\u001a\n".toByteArray() + ByteArray(41)))
        assertNull("tamanho não bate", r.lerRaw(raw(2, 2, 1, 12, ByteArray(10))))
        assertNull("formato desconhecido", r.lerRaw(raw(1, 1, 99, 12, ByteArray(4))))
    }

    @Test
    fun `tela de uma cor so`() {
        assertEquals(0xff000000.toInt(), r.corUnica(IntArray(10) { 0xff000000.toInt() }))
        assertNull(r.corUnica(intArrayOf(0xff000000.toInt(), 0xff000001.toInt())))
        assertNull(r.corUnica(IntArray(0)))
    }

    @Test
    fun `linha do tempo so com luzes e avisos`() {
        listOf(
            "car.ipk_light.door_warning",
            "car.ipk_light.vcu_ready_state",
            "car.ipk_info.fcw_warning",
            "car.basic.left_turn_light_status",
            "car.basic.hazard_light_status",
            "car.basic.seat_belt_warning",
            "car.basic.epb_state",
            "car.basic.driving_ready_state",
            "car.basic.door_status",
            "car.intelligent_driving_info.auto_hold_state",
            "car.drive.setting.hdc_enable",
            "car.basic.gear_status",
        ).forEach { assertTrue(it, r.relevante(it)) }
        listOf(
            "car.basic.vehicle_speed",
            "car.basic.engine_speed",
            "car.ev_info.power_battery_current",
            "car.basic.total_odometer",
        ).forEach { assertFalse(it, r.relevante(it)) }
    }

    @Test
    fun `sinais VHAL de lampada`() {
        assertTrue(r.sinalDeLampada("ABM1_AIRBFAILLMPCMD"))
        assertTrue(r.sinalDeLampada("ESP_FD2_ABSFAILSTS"))
        assertTrue(r.sinalDeLampada("PEPS4_WARNVLDKEYNOTFOUND"))
        assertFalse(r.sinalDeLampada("TPMS2_FLTIREPRESS"))
    }

    @Test
    fun `mudancas entre leituras`() {
        val antes = mapOf("a" to "0", "b" to "1", "c" to "x")
        val depois = mapOf("a" to "1", "b" to "1", "d" to "novo")
        assertEquals(
            listOf(
                RegrasDaCalibracao.Mudanca("a", "0", "1"),
                RegrasDaCalibracao.Mudanca("d", null, "novo"),
            ),
            r.mudancas(antes, depois)
        )
    }

    @Test
    fun `fim da sessao`() {
        val t0 = 1_000_000L
        assertNull(r.fim(t0 + 1000, t0, "0"))
        assertNull("sem leitura não encerra", r.fim(t0 + 1000, t0, null))
        assertNull("0,4 km/h ainda é parado", r.fim(t0 + 1000, t0, "0.4"))
        assertEquals(RegrasDaCalibracao.Fim.ANDOU, r.fim(t0 + 1000, t0, "5"))
        assertEquals(RegrasDaCalibracao.Fim.TEMPO, r.fim(t0 + RegrasDaCalibracao.LIMITE_MS + 1, t0, "0"))
        assertNull("sem início gravado, só a velocidade conta", r.fim(t0, 0L, "0"))
    }

    @Test
    fun `nome da pasta da captura`() {
        assertEquals("03-porta", r.nomeDaCaptura(3, "porta"))
        assertEquals("12-seta-dir", r.nomeDaCaptura(12, "Seta Dir"))
        assertEquals("00-passo", r.nomeDaCaptura(0, "///"))
    }

    @Test
    fun `passos tem ids unicos e desconhecido vira outro`() {
        val ids = RegrasDaCalibracao.PASSOS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("outro", r.passo("nao-existe").id)
    }

    @Test
    fun `a grade vem no APK fora dos temas pre-instalados`() {
        // O teste roda em app/; a grade não pode cair no seed nem na lista de temas.
        val raiz = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        assertTrue(File(raiz, "CalibracaoDoPainel/theme.xml").isFile)
        assertTrue(File(raiz, "CalibracaoDoPainel/app.html").isFile)
        assertFalse(File(raiz, "PreinstalledThemes/opengw-calibracao").exists())
        val xml = File(raiz, "CalibracaoDoPainel/theme.xml").readText()
        assertTrue("contrato v1.0", xml.contains("<contractVersion>v1.0</contractVersion>"))
        assertFalse("sem máscara nativa: o transparente mostra o painel de fábrica", xml.contains("<nativeMasks>"))
        assertFalse("sem fundo: o projetor fica transparente", xml.contains("<background>"))
        val html = File(raiz, "CalibracaoDoPainel/app.html").readText()
        assertFalse("sem camada composta", html.contains("will-change"))
        assertTrue("assina só a velocidade", html.contains("subscribe(JSON.stringify(['car.basic.vehicle_speed']))"))
        assertTrue("bate o coração do tema", html.contains("heartbeat()"))
    }
}
