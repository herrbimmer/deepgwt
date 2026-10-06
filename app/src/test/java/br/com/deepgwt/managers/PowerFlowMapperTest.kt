// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * O pacote `haval.power.flow` que o raio-X do conjunto consome.
 *
 * O contrato NÃO é escolha nossa: está no launcher, em `_powerUnpackFlow`, e ele recusa qualquer
 * coisa fora do formato — devolvendo `null`, que o cartão traduz em "Fluxo indisponível". Por isso o
 * primeiro teste aqui é uma reimplementação do parser DELE: se o formato escorregar, falha aqui em
 * vez de falhar calado no carro.
 */
class PowerFlowMapperTest {

    /** Cópia fiel de `_powerUnpackFlow` do launcher (index.html). */
    private fun comoOLauncherLe(raw: String): Map<String, Any>? {
        val parts = raw.split("|")
        if (parts.size < 5 || parts[0] != "v1") return null
        if (parts[1] !in listOf("idle", "ev", "hybrid", "ice", "regen", "charge")) return null
        if (!Regex("^[01]$").matches(parts[2])) return null
        if (!Regex("^-?\\d+$").matches(parts[3])) return null
        if (!Regex("^-?\\d+$").matches(parts[4])) return null
        return mapOf(
            "state" to parts[1],
            "ice" to (parts[2] == "1"),
            "front" to Integer.signum(parts[3].toInt()),
            "rear" to Integer.signum(parts[4].toInt())
        )
    }

    private fun alimentar(vararg pares: Pair<String, String>) {
        pares.forEach { (k, v) -> PowerFlowMapper.onSignal(k, v) }
    }

    @Before
    fun limpar() {
        // Zera tudo o que o objeto guarda entre testes — ele é singleton.
        alimentar(
            "car.basic.engine_speed" to "0",
            "car.ev_info.power_battery_voltage" to "",
            "car.ev_info.power_battery_current" to "",
            "car.ev_info.motor_power" to "",
            "car.basic.vehicle_speed" to "0",
            "car.ev_info.rear_motor_speed" to "",
            "car.ev_info.charging_gun_conn_state" to "0",
            "car.ev_info.charging_gun_ac_conn_state" to "0",
            "car.ev_info.charging_state" to "0"
        )
    }

    @Test
    fun `o pacote e aceito pelo parser do launcher`() {
        val lido = comoOLauncherLe(PowerFlowMapper.packedNow())
        assertNotNull("o launcher recusaria este pacote e mostraria 'Fluxo indisponível'", lido)
    }

    @Test
    fun `o carro da captura parado e em espera sai como idle`() {
        // Números exatos de knowledge/session_2026-09-15/raw_http/telemetry.json:
        // 306,0 V x 1,0 A = 0,306 kW (consumo de repouso), motor a combustão parado.
        alimentar(
            "car.ev_info.power_battery_voltage" to "306.0",
            "car.ev_info.power_battery_current" to "1.0",
            "car.ev_info.motor_power" to "-1001",
            "car.basic.engine_speed" to "0",
            "car.basic.vehicle_speed" to "0.0"
        )
        assertEquals("v1|idle|0|0|0", PowerFlowMapper.packedNow())
    }

    @Test
    fun `pacote entregando com o motor parado e tracao eletrica`() {
        alimentar(
            "car.ev_info.power_battery_voltage" to "350.0",
            "car.ev_info.power_battery_current" to "120.0", // 42 kW
            "car.basic.vehicle_speed" to "60.0"
        )
        assertEquals("v1|ev|0|1|0", PowerFlowMapper.packedNow())
    }

    @Test
    fun `motor girando junto com o pacote e hibrido`() {
        alimentar(
            "car.ev_info.power_battery_voltage" to "350.0",
            "car.ev_info.power_battery_current" to "120.0",
            "car.basic.vehicle_speed" to "60.0",
            "car.basic.engine_speed" to "2400"
        )
        assertEquals("v1|hybrid|1|1|0", PowerFlowMapper.packedNow())
    }

    @Test
    fun `motor sozinho puxando o carro e tracao a combustao`() {
        alimentar(
            "car.basic.engine_speed" to "2400",
            "car.ev_info.power_battery_voltage" to "350.0",
            "car.ev_info.power_battery_current" to "0.2", // 0,07 kW: nada
            "car.basic.vehicle_speed" to "80.0"
        )
        assertEquals("v1|ice|1|1|0", PowerFlowMapper.packedNow())
    }

    @Test
    fun `corrente negativa com o carro andando e regeneracao`() {
        alimentar(
            "car.ev_info.power_battery_voltage" to "350.0",
            "car.ev_info.power_battery_current" to "-40.0",
            "car.basic.vehicle_speed" to "50.0"
        )
        assertEquals("v1|regen|0|-1|0", PowerFlowMapper.packedNow())
    }

    @Test
    fun `regeneracao exige rodas girando`() {
        // Pacote recebendo com o carro PARADO não é regeneração — é o motor gerando, ou a rede.
        // Dizer "recuperando energia" com as rodas paradas seria mentira desenhada na tela.
        alimentar(
            "car.ev_info.power_battery_voltage" to "350.0",
            "car.ev_info.power_battery_current" to "-40.0",
            "car.basic.vehicle_speed" to "0.0",
            "car.basic.engine_speed" to "1200"
        )
        assertEquals("v1|idle|1|0|0", PowerFlowMapper.packedNow())
    }

    @Test
    fun `plugue conectado e carregando vence tudo`() {
        alimentar(
            "car.ev_info.charging_gun_conn_state" to "1",
            "car.ev_info.charging_state" to "1",
            "car.ev_info.power_battery_voltage" to "350.0",
            "car.ev_info.power_battery_current" to "-20.0"
        )
        assertEquals("v1|charge|0|0|0", PowerFlowMapper.packedNow())
    }

    // ── Histerese do motor a combustão ──────────────────────────────────────────────────────

    @Test
    fun `o motor so conta como ligado acima do limiar de subida`() {
        alimentar("car.basic.engine_speed" to "300", "car.basic.vehicle_speed" to "40.0")
        assertTrue("300 rpm está abaixo do limiar de subida", PowerFlowMapper.packedNow().contains("|0|"))
        alimentar("car.basic.engine_speed" to "450")
        assertTrue(PowerFlowMapper.packedNow().contains("|1|"))
    }

    @Test
    fun `o motor nao desliga ao atravessar o limiar de subida na descida`() {
        // A histerese existe para isto: sem ela, um motor oscilando em torno de 400 rpm faria o
        // indicador piscar. Ligado a 450, ele continua ligado a 300 e só apaga abaixo de 150.
        alimentar("car.basic.engine_speed" to "450", "car.basic.vehicle_speed" to "40.0")
        assertTrue(PowerFlowMapper.packedNow().contains("|1|"))
        alimentar("car.basic.engine_speed" to "300")
        assertTrue("300 rpm depois de ligado tem de continuar ligado", PowerFlowMapper.packedNow().contains("|1|"))
        alimentar("car.basic.engine_speed" to "100")
        assertTrue(PowerFlowMapper.packedNow().contains("|0|"))
    }

    // ── Booleanos sem formato garantido (THEME_AUTHORING_GUIDE §7.5) ────────────────────────

    @Test
    fun `o plugue e reconhecido em qualquer formato plausivel`() {
        for (ligado in listOf("1", "true", "TRUE", "on")) {
            limpar()
            alimentar(
                "car.ev_info.charging_gun_conn_state" to ligado,
                "car.ev_info.charging_state" to ligado
            )
            assertEquals("formato \"$ligado\" não reconhecido", "v1|charge|0|0|0", PowerFlowMapper.packedNow())
        }
    }

    @Test
    fun `chave vazia nao vira zero`() {
        // "" é ausência de leitura. Tratar como 0 acenderia estados falsos — é o mesmo erro que o
        // -1001 de motor_power representa do outro lado.
        alimentar("car.ev_info.rear_motor_speed" to "")
        val lido = comoOLauncherLe(PowerFlowMapper.packedNow())
        assertEquals(0, lido!!["rear"])
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
