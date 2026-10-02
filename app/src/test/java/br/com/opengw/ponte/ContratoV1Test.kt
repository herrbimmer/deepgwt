package br.com.opengw.ponte

import br.com.opengw.telas.ProjectionGeometry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContratoV1Test {

    /** A lista 6.1 da especificação, digitada à parte: qualquer mudança na ponte quebra aqui. */
    private val esperadas = listOf(
        "car.basic.vehicle_speed", "car.basic.engine_speed", "car.basic.instant_fuel_consumption",
        "car.basic.total_odometer", "car.basic.gear_status", "car.basic.inside_temp", "car.basic.outside_temp",
        "car.basic.remain_fuel_percentage", "car.basic.engine_state", "car.basic.driving_ready_state",
        "sys.radio.cur_channel_info", "car.basic.head_light_status", "car.map.tsr.nav_speed_limit",
        "car.map.tsr.nav_speed_limit_sign_status", "car.ev_info.cur_battery_power_percentage",
        "car.ev_info.fuel_mode_remain_odometer", "car.ev_info.electric_mode_remain_odometer",
        "car.configure.default_temp_unit", "car.drive_setting.esp_enable", "car.ev_setting.power_model_config",
        "car.drive_setting.drive_mode", "car.drive_setting.steering_wheel_assist_mode",
        "car.ev_setting.energy_recovery_level", "car.ev.setting.pedal_control_enable",
        "car.ev_info.energy_output_percentage", "car.ev_info.cur_charge_current",
        "car.ev_info.power_battery_voltage", "car.ev_info.Instant_energy_consumption", "car.hvac.power_mode",
        "car.hvac.fan_speed", "car.hvac.driver_temperature", "car.hvac.cycle_mode", "car.hvac.auto_enable",
        "car.hvac.anion_enable", "app.display.1.active_app", "app.display.1.active_app_label",
        "app.display.1.active_app_icon", "app.display.3.active_app", "app.display.3.active_app_label",
        "app.display.3.active_app_icon", "app.launcher.apps", "app.navigation.directions", "app.media.state",
        "app.media.title", "app.media.artist", "app.media.album", "app.media.duration", "app.media.position",
        "app.media.album_art", "app.phone.state", "app.phone.caller_name", "app.phone.caller_number",
        "app.phone.call_duration", "bsdLeft", "bsdRight", "carPlayInDash", "projectionMirrorInDash",
        "projectionPreparingD3", "projectionCardOverlayAllowed", "warningActive", "warningDismissed",
    )

    @Test
    fun `as 61 chaves legiveis na ordem do contrato`() {
        assertEquals(61, esperadas.size)
        assertEquals(esperadas, ContratoV1.CHAVES_LEGIVEIS)
        assertEquals(61, ContratoV1.CHAVES_LEGIVEIS.toSet().size)
    }

    @Test
    fun `grafias do carro que parecem erro e sao contrato`() {
        assertTrue(ContratoV1.legivel("car.ev.setting.pedal_control_enable"))
        assertTrue(ContratoV1.legivel("car.ev_info.Instant_energy_consumption"))
        assertFalse(ContratoV1.legivel("car.ev_setting.pedal_control_enable"))
        assertFalse(ContratoV1.legivel("car.ev_info.instant_energy_consumption"))
        assertFalse(ContratoV1.legivel("car.ev_info.soc_of_battery"))
    }

    @Test
    fun `as 14 gravaveis`() {
        assertEquals(
            setOf(
                "car.drive_setting.esp_enable", "car.ev_setting.power_model_config",
                "car.ev_setting.power_reserve_config", "car.ev_setting.charge_soc_target_config",
                "car.drive_setting.drive_mode", "car.drive_setting.steering_wheel_assist_mode",
                "car.ev_setting.energy_recovery_level", "car.ev.setting.pedal_control_enable",
                "car.hvac.power_mode", "car.hvac.fan_speed", "car.hvac.driver_temperature",
                "car.hvac.cycle_mode", "car.hvac.auto_enable", "car.hvac.anion_enable",
            ),
            ContratoV1.CHAVES_GRAVAVEIS,
        )
        assertEquals(14, ContratoV1.CHAVES_GRAVAVEIS.size)
        assertFalse("display não é chave do carro", "display" in ContratoV1.CHAVES_GRAVAVEIS)
    }

    @Test
    fun `apelidos nos dois sentidos`() {
        assertEquals("car.basic.vehicle_speed", ContratoV1.canonica("car.basic.speed"))
        assertEquals("car.basic.gear_status", ContratoV1.canonica("car.basic.gear"))
        assertEquals("car.basic.inside_temp", ContratoV1.canonica("car.basic.temp_inside"))
        assertEquals("car.basic.outside_temp", ContratoV1.canonica("car.basic.temp_outside"))
        assertEquals("car.basic.vehicle_speed", ContratoV1.canonica("car.basic.vehicle_speed"))
        assertEquals("qualquer.coisa", ContratoV1.canonica("qualquer.coisa"))
        assertEquals(4, ContratoV1.APELIDOS.size)
        for ((apelido, canonica) in ContratoV1.APELIDOS) {
            assertEquals(apelido, ContratoV1.APELIDO_DA_CANONICA[canonica])
            assertTrue(ContratoV1.legivel(canonica))
            assertFalse("apelido não aparece em getAvailableKeys", apelido in ContratoV1.CHAVES_LEGIVEIS)
        }
    }

    @Test
    fun `preferencias do tema sao legiveis fora da lista`() {
        assertTrue(ContratoV1.legivel("app.preferences.displayMode"))
        assertTrue(ContratoV1.legivel("app.preferences.focus_ar condicionado"))
        assertFalse("app.preferences.x" in ContratoV1.CHAVES_LEGIVEIS)
        assertFalse(ContratoV1.legivel("app.preference.x"))
    }

    @Test
    fun `versao minima da ponte`() {
        // Casos do teste herdado (comportamento a manter).
        assertTrue(ContratoV1.versaoDaPonteAceita(null))
        assertTrue(ContratoV1.versaoDaPonteAceita("0.9.0"))
        assertTrue(ContratoV1.versaoDaPonteAceita("1.0"))
        assertTrue(ContratoV1.versaoDaPonteAceita("v1.0.0"))
        assertFalse(ContratoV1.versaoDaPonteAceita("1.0.1"))
        assertFalse(ContratoV1.versaoDaPonteAceita("2.0.0"))
        assertFalse(ContratoV1.versaoDaPonteAceita("future"))
        // Mais da especificação.
        assertTrue(ContratoV1.versaoDaPonteAceita("1"))
        assertTrue(ContratoV1.versaoDaPonteAceita("1.0.0-beta"))
        assertFalse(ContratoV1.versaoDaPonteAceita("1.0.0.0"))
        assertTrue(ContratoV1.versaoDaPonteAceita(""))
        assertTrue(ContratoV1.versaoDaPonteAceita("   "))
        assertTrue(ContratoV1.versaoDaPonteAceita(" 0.1.0 "))
        assertTrue(ContratoV1.versaoDaPonteAceita("1.0.0"))
        assertFalse(ContratoV1.versaoDaPonteAceita("1.1"))
        assertFalse(ContratoV1.versaoDaPonteAceita("1..0"))
        assertFalse(ContratoV1.versaoDaPonteAceita("v"))
    }

    @Test
    fun `versao do contrato`() {
        assertTrue(ContratoV1.contratoAceito("1.0"))
        assertTrue(ContratoV1.contratoAceito("V1.0"))
        assertTrue(ContratoV1.contratoAceito("v1.0"))
        assertTrue(ContratoV1.contratoAceito(" v1.0 "))
        assertFalse(ContratoV1.contratoAceito("v1"))
        assertFalse(ContratoV1.contratoAceito("1.0.0"))
        assertFalse(ContratoV1.contratoAceito(""))
        assertFalse(ContratoV1.contratoAceito(null))
        assertFalse(ContratoV1.contratoAceito("legacy"))
        assertFalse(ContratoV1.contratoAceito("LEGACY"))
    }

    @Test
    fun `caminho seguro dentro do tema`() {
        assertTrue(ContratoV1.caminhoSeguroNoTema(""))
        assertTrue(ContratoV1.caminhoSeguroNoTema("car-bg.jpg"))
        assertTrue(ContratoV1.caminhoSeguroNoTema("img/fundo_1.png"))
        assertFalse(ContratoV1.caminhoSeguroNoTema("../x.png"))
        assertFalse(ContratoV1.caminhoSeguroNoTema("img/../../x.png"))
        assertFalse(ContratoV1.caminhoSeguroNoTema("/a"))
        assertFalse(ContratoV1.caminhoSeguroNoTema("a\\b"))
        assertFalse(ContratoV1.caminhoSeguroNoTema("a b.png"))
        assertFalse(ContratoV1.caminhoSeguroNoTema("fundo.png?x=1"))
        assertTrue(ContratoV1.caminhoSeguroNoTema("a".repeat(160)))
        assertFalse(ContratoV1.caminhoSeguroNoTema("a".repeat(161)))
    }

    @Test
    fun `chave de preferencia valida`() {
        assertTrue(ContratoV1.chaveDePreferenciaValida("displayMode"))
        assertTrue(ContratoV1.chaveDePreferenciaValida("focus_main.area-1"))
        assertTrue(ContratoV1.chaveDePreferenciaValida("a".repeat(80)))
        assertFalse(ContratoV1.chaveDePreferenciaValida("a".repeat(81)))
        assertFalse(ContratoV1.chaveDePreferenciaValida(""))
        assertFalse(ContratoV1.chaveDePreferenciaValida("com espaço"))
        assertFalse(ContratoV1.chaveDePreferenciaValida("ação"))
        assertFalse(ContratoV1.chaveDePreferenciaValida("a/b"))
    }

    @Test
    fun `chave escopada por pasta do tema`() {
        assertEquals("theme_config_coffe3_displayMode", ContratoV1.chaveEscopada("coffe3", "displayMode"))
        assertEquals("theme_config_Default_focus_x", ContratoV1.chaveEscopada("", "focus_x"))
        assertEquals("theme_config_Default_fundo", ContratoV1.chaveEscopada("  ", "fundo"))
    }

    @Test
    fun `literal do lote de control`() {
        assertEquals("true", ContratoV1.literalJs("true"))
        assertEquals("false", ContratoV1.literalJs("false"))
        assertEquals("null", ContratoV1.literalJs("null"))
        assertEquals("null", ContratoV1.literalJs(null))
        assertEquals("1", ContratoV1.literalJs("1"))
        assertEquals("12.5", ContratoV1.literalJs("12.5"))
        assertEquals("-3", ContratoV1.literalJs("-3"))
        assertEquals("0", ContratoV1.literalJs("0"))
        assertEquals("1e5", ContratoV1.literalJs("1e5"))
        assertEquals("true", ContratoV1.literalJs(true))
        assertEquals("57", ContratoV1.literalJs(57))
        assertEquals("1.5", ContratoV1.literalJs(1.5f))
        assertEquals("2.25", ContratoV1.literalJs(2.25))
        assertEquals("NaN", ContratoV1.literalJs(Double.NaN))
        // Texto que não é número JS canônico vai como string (um "007" cru seria octal no JS).
        assertEquals("\"D\"", ContratoV1.literalJs("D"))
        assertEquals("\"007\"", ContratoV1.literalJs("007"))
        assertEquals("\" 12\"", ContratoV1.literalJs(" 12"))
        assertEquals("\"1.5f\"", ContratoV1.literalJs("1.5f"))
        assertEquals("\"True\"", ContratoV1.literalJs("True"))
        assertEquals("\"\"", ContratoV1.literalJs(""))
        assertEquals("\"°C\"", ContratoV1.literalJs("°C"))
    }

    @Test
    fun `texto JS escapado`() {
        assertEquals("\"abc\"", ContratoV1.textoJs("abc"))
        assertEquals("\"a\\\"b\"", ContratoV1.textoJs("a\"b"))
        assertEquals("\"a\\'b\"", ContratoV1.textoJs("a'b"))
        assertEquals("\"a\\\\b\"", ContratoV1.textoJs("a\\b"))
        assertEquals("\"a\\nb\\rc\\td\"", ContratoV1.textoJs("a\nb\rc\td"))
        assertEquals("\"a\\u2028b\\u2029c\"", ContratoV1.textoJs("a\u2028b\u2029c"))
        assertEquals("\"\\u0000\\u001f\"", ContratoV1.textoJs("\u0000\u001f"))
        assertEquals("\"Ré · ★\"", ContratoV1.textoJs("Ré · ★"))
        assertEquals("\"\uD83C\uDFB5\"", ContratoV1.textoJs("\uD83C\uDFB5"))
        assertEquals("\"\\ud83c\"", ContratoV1.textoJs("\uD83C"))
        assertEquals("\"12\"", ContratoV1.textoJs("12"))
        // Ida e volta pelo leitor dos testes.
        val estranho = "x\"'\\\n\r\t\u2028\u2029</script>"
        assertEquals(estranho, LeitorDeScript.literal(ContratoV1.textoJs(estranho), 0)?.first)
    }

    @Test
    fun `area dos apps limitada ao painel`() {
        assertEquals(ProjectionGeometry.Caixa(640, 95, 640, 560), ContratoV1.limitarAreaDosApps(640, 95, 640, 560))
        assertEquals(ProjectionGeometry.Caixa(0, 0, 1, 1), ContratoV1.limitarAreaDosApps(-10, -10, 0, -5))
        assertEquals(ProjectionGeometry.Caixa(1800, 700, 120, 20), ContratoV1.limitarAreaDosApps(1800, 700, 500, 100))
        assertEquals(ProjectionGeometry.Caixa(1919, 719, 1, 1), ContratoV1.limitarAreaDosApps(5000, 5000, 5000, 5000))
    }

    @Test
    fun `caixa da projecao limita cada valor no proprio intervalo`() {
        assertNull(ContratoV1.limitarCaixaDaProjecao(10, 10, 0, 100))
        assertNull(ContratoV1.limitarCaixaDaProjecao(10, 10, 100, -1))
        assertEquals(ProjectionGeometry.Caixa(0, 0, 1920, 720), ContratoV1.limitarCaixaDaProjecao(-5, -5, 5000, 900))
        // A largura NÃO é limitada a 1920 − x.
        assertEquals(ProjectionGeometry.Caixa(1800, 0, 600, 300), ContratoV1.limitarCaixaDaProjecao(1800, 0, 600, 300))
        assertEquals(ProjectionGeometry.Caixa(1919, 719, 1920, 720), ContratoV1.limitarCaixaDaProjecao(3000, 3000, 3000, 3000))
    }

    @Test
    fun `mascaras nativas sem diferenciar maiusculas`() {
        assertEquals("fuel", ContratoV1.mascaraAceita("FUEL"))
        assertEquals("topcenter", ContratoV1.mascaraAceita("topCenter"))
        assertEquals("battery", ContratoV1.mascaraAceita("battery"))
        assertNull(ContratoV1.mascaraAceita("fuelMask"))
        assertNull(ContratoV1.mascaraAceita(""))
    }

    @Test
    fun `acoes do sistema e os payloads da projecao`() {
        assertEquals(AcaoDoSistema.SAIR_DO_AR_NO_MAXIMO, ContratoV1.acaoDoSistema("CANCEL_MAX_AC", ""))
        assertEquals(AcaoDoSistema.ABRIR_CAMERA_360, ContratoV1.acaoDoSistema("TRIGGER_AVM_CAMERA", ""))
        assertEquals(AcaoDoSistema.DISPENSAR_AVISOS, ContratoV1.acaoDoSistema("DISMISS_WARNINGS", ""))
        assertEquals(AcaoDoSistema.SO_REGISTRAR, ContratoV1.acaoDoSistema("MEDIA_CONTROL", "next"))
        assertEquals(AcaoDoSistema.SO_REGISTRAR, ContratoV1.acaoDoSistema("PHONE_CONTROL", "answer"))
        assertEquals(AcaoDoSistema.PARAR_PROJECAO, ContratoV1.acaoDoSistema("PROJECT_HEAD_UNIT", " stop "))
        assertEquals(AcaoDoSistema.REPROJETAR, ContratoV1.acaoDoSistema("PROJECT_HEAD_UNIT", "again"))
        assertEquals(AcaoDoSistema.ABRIR_SELECAO, ContratoV1.acaoDoSistema("PROJECT_HEAD_UNIT", ""))
        assertEquals(AcaoDoSistema.ABRIR_SELECAO, ContratoV1.acaoDoSistema("PROJECT_HEAD_UNIT", "SELECT"))
        assertEquals(AcaoDoSistema.RECUSADA, ContratoV1.acaoDoSistema("cancel_max_ac", ""))
        assertEquals(AcaoDoSistema.RECUSADA, ContratoV1.acaoDoSistema("LAUNCH", ""))
        assertEquals(AcaoDoSistema.RECUSADA, ContratoV1.acaoDoSistema("", ""))
    }

    @Test
    fun `getAvailableKeys e o JSON da lista`() {
        val json = JsonDaPonte.listaDeTextos(ContratoV1.CHAVES_LEGIVEIS)
        val lida = Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }
        assertEquals(esperadas, lida)
    }
}
