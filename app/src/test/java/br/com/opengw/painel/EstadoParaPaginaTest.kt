package br.com.opengw.painel

import br.com.opengw.ponte.ContratoV1
import br.com.opengw.ponte.LeitorDeScript
import br.com.opengw.ponte.PreferenciasEmMemoria
import br.com.opengw.ponte.ScriptsDaPagina
import org.junit.Assert.assertEquals
import org.junit.Test

/** F (lote de estado), G.6 (entrada do cartão) e E.4 (avulsos) de `projetores-painel.md`. */
class EstadoParaPaginaTest {

    private val prefs = PreferenciasEmMemoria()
    private val carro = HashMap<String, String>()

    private fun entradas(projecao: EstadoDaProjecao = EstadoDaProjecao.NENHUMA) =
        EstadoParaPagina.Entradas({ carro[it] }, PreferenciasDoPainel(prefs), projecao, avisoAtivo = true, avisoDispensado = false)

    private fun literais(mapa: Map<String, Any?>): List<Pair<String, String>> =
        LeitorDeScript.controls(ScriptsDaPagina.lote(mapa.toList()))

    @Test
    fun loteVazioTemAs54LinhasNaOrdem() {
        val lote = EstadoParaPagina.lote(entradas())
        val ordem = listOf(
            "carPlayInDash", "projectionMirrorInDash", "projectionPreparingD3", "warningActive", "warningDismissed",
            "display", "colorTheme", "gearState", "fan", "temp", "power", "recycle", "auto", "aion", "tempUnit",
            "outside_temp", "inside_temp", "enableOdometer", "enableRevisionWarning", "odometer", "nextRevisionKm",
            "nextRevisionDate", "fuelPercent", "batteryPercent", "fuelRange", "batteryRange", "fuelDisplayUnit",
            "hideSpeedometerOnMaps", "v2TripInfo", "tripAvgConsumption", "tripDriveTime", "tripOdometer", "tripAvgSpeed",
            "tirePressures", "tireTemperatures", "speedLimit", "speedLimitActive", "tripAnalysisActive",
            "tripAnalysisScore", "carSpeed", "engineRPM", "evMode", "drivingMode", "evModeLabel", "steerMode",
            "espStatus", "regenMode", "evPowerFactor", "evPowerRegen", "evPowerKw", "gasConsumptionMode",
            "gasConsumptionIdle", "gasConsumption", "instantEVConsumption",
        )
        assertEquals(54, ordem.size)
        assertEquals(ordem, lote.keys.toList())
        val l = literais(lote).toMap()
        assertEquals("false", l["carPlayInDash"])
        assertEquals("true", l["warningActive"])
        assertEquals("\"Normal\"", l["display"])
        assertEquals("\"Red Sport\"", l["colorTheme"])
        assertEquals("\"N\"", l["gearState"])
        assertEquals("0", l["fan"])
        assertEquals("22", l["temp"])
        assertEquals("\"°C\"", l["tempUnit"])
        assertEquals("null", l["outside_temp"])
        assertEquals("true", l["enableOdometer"])
        assertEquals("false", l["enableRevisionWarning"])
        assertEquals("0", l["odometer"])
        assertEquals("0", l["nextRevisionDate"])
        assertEquals("\"liters\"", l["fuelDisplayUnit"])
        assertEquals("\"\"", l["tripAvgConsumption"])
        assertEquals("\"\"", l["speedLimit"])
        assertEquals("0", l["speedLimitActive"])
        assertEquals("null", l["tripAnalysisScore"])
        assertEquals("0", l["carSpeed"])
        assertEquals("\"--\"", l["evMode"])
        assertEquals("0.0", l["evPowerFactor"])
        assertEquals("0.0", l["evPowerRegen"])
        assertEquals("0.0", l["evPowerKw"])
        assertEquals("\"Running\"", l["gasConsumptionMode"])
        assertEquals("0.0", l["gasConsumptionIdle"])
        assertEquals("0", l["instantEVConsumption"])
    }

    @Test
    fun loteComACapturaDoCarro() {
        carro["car.basic.gear_status"] = "3"
        carro["car.basic.outside_temp"] = "20.5"
        carro["car.configure.default_temp_unit"] = "0"
        carro["car.ev_setting.power_model_config"] = "1"
        carro["car.ev_setting.power_reserve_config"] = "2"
        carro["car.ev_setting.charge_soc_target_config"] = "20"
        carro["car.basic.instant_fuel_consumption"] = "{4.0,0.0}"
        carro["car.basic.vehicle_speed"] = "72.0"
        carro["car.ev_info.power_battery_voltage"] = "306.0"
        carro["car.ev_info.cur_charge_current"] = "1.2"
        carro["car.basic.total_odometer"] = "{103900,0,1,1}"
        prefs.mapa["instrumentRevisionKm"] = 5000
        prefs.mapa["tripConsistencyClusterScore"] = 87
        prefs.mapa["instrumentRevisionNextDate"] = 1_790_000_000_000L
        prefs.mapa["currentClusterDisplay"] = "Mapa"
        val l = literais(EstadoParaPagina.lote(entradas(EstadoDaProjecao(carPlay = true, espelho = false, preparando = false)))).toMap()
        assertEquals("true", l["carPlayInDash"])
        assertEquals("\"P\"", l["gearState"])
        assertEquals("20.5", l["outside_temp"])
        assertEquals("\"EVP\"", l["evMode"])
        assertEquals("\"Mapa\"", l["display"])
        assertEquals("77", l["carSpeed"])
        assertEquals("true", l["enableRevisionWarning"])
        assertEquals("5000", l["nextRevisionKm"])
        assertEquals("1790000000000", l["nextRevisionDate"])
        assertEquals("87", l["tripAnalysisScore"])
        assertEquals("\"{103900,0,1,1}\"", l["odometer"])
        assertEquals(0.3672, l.getValue("evPowerKw").toDouble(), 0.001)
        carro["car.ev_setting.power_model_config"] = "0"
        assertEquals("\"HEV Prioridade 20%\"", literais(EstadoParaPagina.lote(entradas())).toMap()["evMode"])
    }

    @Test
    fun entradaDoCartao() {
        assertEquals(
            listOf("fan", "temp", "power", "recycle", "auto", "aion", "tempUnit", "outside_temp", "inside_temp"),
            EstadoParaPagina.entradaDoCartao(3, entradas()).keys.toList(),
        )
        assertEquals(
            listOf("evMode", "drivingMode", "evModeLabel", "steerMode", "espStatus", "regenMode"),
            EstadoParaPagina.entradaDoCartao(1, entradas()).keys.toList(),
        )
        assertEquals(emptyList<String>(), EstadoParaPagina.entradaDoCartao(0, entradas()).keys.toList())
    }

    private fun avulsos(chave: String, valor: String, a: EstadoParaPagina.Avulsos): List<Pair<String, String>> =
        a.de(chave, valor).map { (k, v) -> k to ScriptsDaPagina.literalTipado(v) }

    @Test
    fun avulsosNosFormatosDaTabela() {
        val a = EstadoParaPagina.Avulsos({ carro[it] }, PreferenciasDoPainel(prefs))
        assertEquals(listOf("carSpeed" to "\"77\""), avulsos("car.basic.vehicle_speed", "72", a))
        assertEquals(listOf("gearState" to "\"D\""), avulsos("car.basic.gear_status", "2", a))
        assertEquals(listOf("outside_temp" to "20.5"), avulsos("car.basic.outside_temp", "20.5", a))
        assertEquals(listOf("inside_temp" to "null"), avulsos("car.basic.inside_temp", "--", a))
        assertEquals(listOf("tempUnit" to "\"°F\""), avulsos("car.configure.default_temp_unit", "1", a))
        assertEquals(listOf("drivingMode" to "\"Eco\"", "evModeLabel" to "\"Eco\""), avulsos("car.drive_setting.drive_mode", "2", a))
        assertEquals(
            listOf("evPowerFactor" to "\"-12.5\"", "lastRegenValue" to "\"12.5\""),
            avulsos("car.ev_info.energy_output_percentage", "-12.5", a),
        )
        assertEquals(
            listOf("gasConsumptionMode" to "\"Idle\"", "gasConsumptionIdle" to "0.8", "gasConsumption" to "0.0"),
            avulsos("car.basic.instant_fuel_consumption", "{4.0,0.85}", a),
        )
        assertEquals(listOf("fan" to "\"3\""), avulsos("car.hvac.fan_speed", "3", a))
        assertEquals(listOf("tripAvgSpeed" to "\"42.5\""), avulsos("car.basic.vehicle_speed_since_reset", "42.5", a))
        // Potência: guarda o último V e o último I.
        assertEquals(listOf("evPowerKw" to "\"0.0\""), avulsos("car.ev_info.power_battery_voltage", "306.0", a))
        val kw = a.de("car.ev_info.cur_charge_current", "1.2").single().second as String
        assertEquals(0.3672, kw.toDouble(), 0.001)
        // Reserva nova com o modo do cache.
        carro["car.ev_setting.power_model_config"] = "0"
        carro["car.ev_setting.charge_soc_target_config"] = "95"
        assertEquals(listOf("evMode" to "\"HEV Prioridade 80%\""), avulsos("car.ev_setting.power_reserve_config", "2", a))
        assertEquals(emptyList<Pair<String, String>>(), avulsos("car.basic.engine_state", "11", a))
        assertEquals(ContratoV1.literalJs("0.8"), "0.8")
    }
}
