// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import java.util.Locale
import kotlin.math.roundToInt

/**
 * A fonte de valores do build `simulator` (B.2–B.5): ao ligar injeta um retrato inicial e, a cada
 * 100 ms, um passo de um percurso simulado. Os valores entram como eventos do carro (`injetar`).
 */
internal class FonteSimulada(
    private val agenda: Agenda,
    private val injetar: (chave: String, valor: String) -> Unit,
) {
    private val percurso = PercursoSimulado()

    /** Injeta os valores iniciais na thread de quem chamou e começa os passos na linha da [agenda]. */
    fun ligar(): Cancelavel {
        for ((chave, valor) in VALORES_INICIAIS) injetar(chave, valor)
        return agenda.repetir(PASSO_MS, PASSO_MS) {
            for ((chave, valor) in percurso.avancar()) injetar(chave, valor)
        }
    }

    companion object {
        const val PASSO_MS = 100L

        /**
         * B.3. Sem `power_model_config` e `drive_mode` o painel projetado herdado derruba o app.
         * Marcha em número (Decisões da sala suja, 10): 3 = P.
         */
        val VALORES_INICIAIS: List<Pair<String, String>> = listOf(
            "car.hvac.fan_speed" to "3",
            "car.hvac.driver_temperature" to "22.0",
            "car.hvac.power_mode" to "1",
            "car.hvac.pass_temperature" to "23.5",
            "sys.settings.audio.media_volume" to "18",
            "car.basic.inside_temp" to "24.0",
            "car.basic.outside_temp" to "28.0",
            "car.basic.engine_state" to "13",
            "car.ev_setting.power_model_config" to "0",
            "car.drive_setting.drive_mode" to "0",
            "car.drive_setting.steering_wheel_assist_mode" to "0",
            "car.drive_setting.esp_enable" to "1",
            "car.ev_setting.energy_recovery_level" to "0",
            "car.basic.driving_ready_state" to "1",
            "car.basic.gear_status" to "3",
            "car.basic.vehicle_speed" to "0.0",
            "car.basic.engine_speed" to "0",
        )
    }
}

/**
 * O percurso simulado, sem relógio: cada [avancar] é um passo de 100 ms. Ciclo de 36 s — parado em
 * P 4 s, acelera até 150 km/h em 15 s, segura 5 s, freia até parar em 12 s — e repete. Combustível e
 * bateria caem devagar andando e sobem rápido quando chegam perto do fim.
 */
internal class PercursoSimulado {

    private var passo = 0
    private var bateria = 80.0
    private var combustivel = 70.0
    private var carregando = false
    private var abastecendo = false
    private var odometroKm = 12_345.0

    /** Velocidade (km/h) no passo [n] do ciclo. */
    fun velocidadeNoPasso(n: Int): Double {
        val i = n % CICLO
        return when {
            i < PARADO -> 0.0
            i < PARADO + ACELERANDO -> MAXIMA * (i - PARADO + 1) / ACELERANDO
            i < PARADO + ACELERANDO + CRUZEIRO -> MAXIMA - ((i % 10) - 5) * 0.2
            else -> MAXIMA * (CICLO - i - 1) / FREANDO
        }
    }

    fun avancar(): List<Pair<String, String>> {
        val velocidade = velocidadeNoPasso(passo).coerceAtLeast(0.0)
        val andando = velocidade > 0.0
        passo++

        if (andando) {
            odometroKm += velocidade / 36_000.0 // km rodados em 100 ms
            bateria -= 0.04
            combustivel -= 0.02
        }
        if (bateria <= 15.0) carregando = true
        if (carregando) {
            bateria += 1.0
            if (bateria >= 95.0) carregando = false
        }
        if (combustivel <= 10.0) abastecendo = true
        if (abastecendo) {
            combustivel += 2.0
            if (combustivel >= 95.0) abastecendo = false
        }
        bateria = bateria.coerceIn(0.0, 100.0)
        combustivel = combustivel.coerceIn(0.0, 100.0)

        val rotacao = if (andando) 1_000 + ((velocidade % 30.0) / 30.0 * 2_500).roundToInt() else 0
        return listOf(
            "car.basic.vehicle_speed" to String.format(Locale.ROOT, "%.1f", velocidade),
            "car.basic.engine_speed" to rotacao.toString(),
            "car.basic.gear_status" to if (andando) MARCHA_D else MARCHA_P,
            "car.basic.remain_fuel_percentage" to combustivel.roundToInt().toString(),
            "car.ev_info.battery_charge_percentage" to bateria.roundToInt().toString(),
            "car.basic.remain_odometer" to (combustivel / 100.0 * AUTONOMIA_MAXIMA).roundToInt().toString(),
            "car.basic.accumulated_odometer" to odometroKm.toInt().toString(),
        )
    }

    companion object {
        const val MAXIMA = 150.0
        const val AUTONOMIA_MAXIMA = 700.0
        private const val PARADO = 40
        private const val ACELERANDO = 150
        private const val CRUZEIRO = 50
        private const val FREANDO = 120
        const val CICLO = PARADO + ACELERANDO + CRUZEIRO + FREANDO
        const val MARCHA_P = "3"
        const val MARCHA_D = "2"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
