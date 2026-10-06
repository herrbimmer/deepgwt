// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.macros

import br.com.deepgwt.veiculo.Sinais
/**
 * O que uma macro consegue OLHAR no carro.
 *
 * ## Por que existe uma lista curada se já existem 818 chaves
 *
 * O ControlService expõe 818 chaves com nomes como `car.ev_info.cur_battery_power_percentage` e
 * valores como `"3"`. Jogar isso cru numa tela de macro transfere para o motorista um trabalho que
 * é nosso: descobrir qual chave é a velocidade, se `"3"` no câmbio é P ou R, se a temperatura vem
 * em décimos. Esta lista dá nome, unidade e — onde o valor é um código — a tabela de tradução dos
 * sinais que alguém realmente usaria num gatilho.
 *
 * O resto das 818 continua acessível: [todos] devolve a lista completa, e qualquer chave sem verbete
 * aqui vira um [CarSignal] genérico (texto livre, sem unidade). A curadoria é um atalho, nunca uma
 * cerca — ver [porChave].
 *
 * ## O que NÃO está aqui
 *
 * Confiança dos valores: os códigos abaixo foram lidos do próprio app (`InstrumentProjector2`,
 * `AsdManager` e a barra inferior antiga), que os usavam em produção. Onde não havia uso confirmado no código,
 * o sinal entra como número/texto sem tabela — inventar rótulo para código que ninguém verificou
 * seria pior do que mostrar o número cru.
 */
data class CarSignal(
    val key: String,
    val label: String,
    val group: String,
    val kind: Kind = Kind.TEXT,
    /** Sufixo mostrado ao lado do valor ("km/h", "°C", "%"). */
    val unit: String = "",
    /** Faixa sugerida para o seletor, quando [kind] é [Kind.NUMBER]. */
    val range: ClosedFloatingPointRange<Float>? = null,
    /** Código bruto -> rótulo, para [Kind.ENUM] e [Kind.BOOLEAN]. */
    val values: List<Pair<String, String>> = emptyList()
) {
    enum class Kind { NUMBER, BOOLEAN, ENUM, TEXT }

    /** Como o valor bruto aparece na tela. Código sem tradução volta cru, e não em branco. */
    fun format(raw: String?): String {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty()) return "--"
        val rotulo = values.firstOrNull { it.first == v }?.second
        if (rotulo != null) return rotulo
        return if (unit.isEmpty()) v else "$v $unit"
    }

    val isCurated: Boolean get() = group != GRUPO_OUTROS
}

private const val GRUPO_OUTROS = "Outras chaves"

object CarSignalCatalog {

    private val LIGA_DESLIGA = listOf("1" to "ligado", "0" to "desligado")
    private val ABERTO_FECHADO = listOf("1" to "aberto", "0" to "fechado")

    /**
     * Os sinais com nome e tradução. Ordem = ordem de exibição dentro de cada grupo.
     *
     * A ordem dos GRUPOS é a de [grupos], e começa por "Condução": é de longe o que mais aparece
     * num gatilho ("quando eu passar de 80", "quando engatar a ré"), e uma lista alfabética
     * enterraria isso no meio.
     */
    val curados: List<CarSignal> = listOf(
        // ── Condução ────────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.CAR_BASIC_VEHICLE_SPEED.chave, "Velocidade", "Condução",
            CarSignal.Kind.NUMBER, "km/h", 0f..220f
        ),
        CarSignal(
            Sinais.CAR_BASIC_GEAR_STATUS.chave, "Marcha", "Condução", CarSignal.Kind.ENUM,
            values = listOf("3" to "P (estacionado)", "4" to "R (ré)", "1" to "N (neutro)", "2" to "D (drive)")
        ),
        CarSignal(
            Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave, "Modo de condução", "Condução",
            CarSignal.Kind.ENUM,
            values = listOf(
                "0" to "Normal", "1" to "Sport", "2" to "Eco",
                "3" to "Neve", "4" to "Areia", "5" to "Lama"
            )
        ),
        CarSignal(
            Sinais.CAR_BASIC_DRIVING_READY_STATE.chave, "Carro pronto para andar", "Condução",
            CarSignal.Kind.BOOLEAN, values = listOf("1" to "pronto", "0" to "desligado")
        ),
        CarSignal(
            Sinais.CAR_BASIC_HAND_BRAKE_STATUS.chave, "Freio de mão", "Condução",
            CarSignal.Kind.BOOLEAN, values = listOf("1" to "puxado", "0" to "solto")
        ),
        CarSignal(
            Sinais.CAR_BASIC_EPB_STATE.chave, "Freio de estacionamento (EPB)", "Condução",
            CarSignal.Kind.BOOLEAN, values = listOf("1" to "acionado", "0" to "liberado")
        ),
        CarSignal(
            Sinais.CAR_INTELLIGENT_DRIVING_INFO_AUTO_HOLD_STATE.chave, "Auto Hold", "Condução",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_STEERING_WHEEL_ANGLE.chave, "Ângulo do volante", "Condução",
            CarSignal.Kind.NUMBER, "°", -540f..540f
        ),
        CarSignal(
            Sinais.CAR_BASIC_TOTAL_ODOMETER.chave, "Hodômetro total", "Condução",
            CarSignal.Kind.NUMBER, "km", 0f..999999f
        ),
        CarSignal(
            Sinais.CAR_BASIC_ACCUMULATED_DRIVETIME.chave, "Tempo de condução acumulado", "Condução",
            CarSignal.Kind.NUMBER, "min", 0f..100000f
        ),

        // ── Energia ─────────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.CAR_EV_INFO_CUR_BATTERY_POWER_PERCENTAGE.chave, "Bateria (SOC)", "Energia",
            CarSignal.Kind.NUMBER, "%", 0f..100f
        ),
        CarSignal(
            Sinais.CAR_EV_INFO_ENERGY_OUTPUT_PERCENTAGE.chave, "Saída de energia", "Energia",
            CarSignal.Kind.NUMBER, "%", -100f..100f
        ),
        CarSignal(
            Sinais.CAR_EV_INFO_POWER_BATTERY_VOLTAGE.chave, "Tensão da bateria de tração", "Energia",
            CarSignal.Kind.NUMBER, "V", 0f..500f
        ),
        CarSignal(
            Sinais.CAR_EV_INFO_CYCLE_ENERGY_CONSUME_INFO.chave, "Consumo do ciclo", "Energia",
            CarSignal.Kind.TEXT
        ),

        // ── Clima ───────────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.CAR_BASIC_INSIDE_TEMP.chave, "Temperatura interna", "Clima",
            CarSignal.Kind.NUMBER, "°C", -20f..60f
        ),
        CarSignal(
            Sinais.CAR_BASIC_OUTSIDE_TEMP.chave, "Temperatura externa", "Clima",
            CarSignal.Kind.NUMBER, "°C", -40f..60f
        ),
        CarSignal(
            Sinais.CAR_HVAC_POWER_MODE.chave, "Ar-condicionado ligado", "Clima",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_HVAC_AC_ENABLE.chave, "Compressor (A/C)", "Clima",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_HVAC_DRIVER_TEMPERATURE.chave, "Temperatura do motorista", "Clima",
            CarSignal.Kind.NUMBER, "°C", 16f..32f
        ),
        CarSignal(
            Sinais.CAR_HVAC_PASS_TEMPERATURE.chave, "Temperatura do passageiro", "Clima",
            CarSignal.Kind.NUMBER, "°C", 16f..32f
        ),
        CarSignal(
            Sinais.CAR_HVAC_FAN_SPEED.chave, "Velocidade do ventilador", "Clima",
            CarSignal.Kind.NUMBER, "", 0f..7f
        ),
        CarSignal(
            Sinais.CAR_HVAC_AUTO_ENABLE.chave, "Clima em AUTO", "Clima",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_HVAC_CYCLE_MODE.chave, "Recirculação", "Clima",
            CarSignal.Kind.ENUM, values = listOf("1" to "recirculando", "0" to "ar externo")
        ),
        CarSignal(
            Sinais.CAR_HVAC_FRONT_DEFROST_ENABLE.chave, "Desembaçador dianteiro", "Clima",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_HVAC_REAR_DEFROST_ENABLE.chave, "Desembaçador traseiro", "Clima",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_HVAC_PM2_5_VALUE.chave, "PM2.5 da cabine", "Clima",
            CarSignal.Kind.NUMBER, "µg/m³", 0f..500f
        ),

        // ── Carroceria ──────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.CAR_BASIC_DOOR_LOCK_STATUS.chave, "Travas", "Carroceria",
            CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_BASIC_DOOR_STATUS.chave, "Portas", "Carroceria", CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_BASIC_WINDOW_STATUS.chave, "Vidros", "Carroceria", CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_BASIC_SUNROOF_STATUS.chave, "Teto solar", "Carroceria", CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_BASIC_SUNSHADE_STATUS.chave, "Cortina do teto", "Carroceria", CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_BASIC_TPMS_STATUS.chave, "Pressão dos pneus (TPMS)", "Carroceria",
            CarSignal.Kind.TEXT
        ),

        // ── Luzes ───────────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.CAR_BASIC_LOW_BEAM_LIGHT_STATUS.chave, "Farol baixo", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_HIGH_BEAM_LIGHT_STATUS.chave, "Farol alto", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_HEAD_LIGHT_STATUS.chave, "Faróis", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_FRONT_FOG_LIGHT_STATUS.chave, "Farol de neblina dianteiro", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_HAZARD_LIGHT_STATUS.chave, "Pisca-alerta", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_LEFT_TURN_LIGHT_STATUS.chave, "Seta esquerda", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_RIGHT_TURN_LIGHT_STATUS.chave, "Seta direita", "Luzes",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),
        CarSignal(
            Sinais.CAR_BASIC_LOW_LIGHT_STATUS.chave, "Pouca luz (sensor)", "Luzes",
            CarSignal.Kind.BOOLEAN, values = listOf("1" to "escuro", "0" to "claro")
        ),

        // ── Conforto ────────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_HEATING_LEVEL.chave,
            "Aquecimento do banco do motorista", "Conforto", CarSignal.Kind.NUMBER, "", 0f..3f
        ),
        CarSignal(
            Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.chave,
            "Ventilação do banco do motorista", "Conforto", CarSignal.Kind.NUMBER, "", 0f..3f
        ),
        CarSignal(
            Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_HEATING_LEVEL.chave,
            "Aquecimento do banco do passageiro", "Conforto", CarSignal.Kind.NUMBER, "", 0f..3f
        ),
        CarSignal(
            Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.chave,
            "Ventilação do banco do passageiro", "Conforto", CarSignal.Kind.NUMBER, "", 0f..3f
        ),
        CarSignal(
            Sinais.CAR_FRAGRANCE_STATUS.chave, "Difusor de fragrância", "Conforto",
            CarSignal.Kind.BOOLEAN, values = LIGA_DESLIGA
        ),

        // ── Mídia ───────────────────────────────────────────────────────────────────────────
        CarSignal(
            Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave, "Volume da mídia", "Mídia",
            CarSignal.Kind.NUMBER, "", 0f..40f
        ),
        CarSignal(
            Sinais.SYS_SETTINGS_AUDIO_MEDIA_MUTE_STATE.chave, "Mídia no mudo", "Mídia",
            CarSignal.Kind.BOOLEAN, values = listOf("1" to "mudo", "0" to "com som")
        ),
        CarSignal(
            Sinais.SYS_SETTINGS_AUDIO_NAVI_MUTE_STATE.chave, "Navegação no mudo", "Mídia",
            CarSignal.Kind.BOOLEAN, values = listOf("1" to "mudo", "0" to "com som")
        ),

        // ── Câmeras e assistentes ───────────────────────────────────────────────────────────
        CarSignal(
            Sinais.SYS_AVM_PREVIEW_STATUS.chave, "Câmera 360 na tela", "Assistentes",
            CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_DMS_WORK_STATE.chave, "Monitor do motorista (DMS)", "Assistentes",
            CarSignal.Kind.TEXT
        ),
        CarSignal(
            Sinais.CAR_MAP_TSR_NAV_SPEED_LIMIT.chave, "Limite de velocidade lido (TSR)", "Assistentes",
            CarSignal.Kind.NUMBER, "km/h", 0f..140f
        )
    )

    /** Grupos na ordem em que a tela os empilha. */
    val grupos: List<String> =
        curados.map { it.group }.distinct() + listOf(GRUPO_OUTROS)

    private val porChaveCurada: Map<String, CarSignal> = curados.associateBy { it.key }

    /**
     * TODAS as chaves que o carro publica, curadas primeiro.
     *
     * As não curadas entram como texto livre no grupo "Outras chaves" — sem rótulo inventado. É o
     * que permite um gatilho em qualquer uma das 818 sem que a gente precise ter estudado as 818.
     */
    val todos: List<CarSignal> by lazy {
        val extras = Sinais.TODOS.map { it.chave }
            .filter { it !in porChaveCurada }
            .sorted()
            .map { CarSignal(key = it, label = it, group = GRUPO_OUTROS) }
        curados + extras
    }

    private val porChave: Map<String, CarSignal> by lazy { todos.associateBy { it.key } }

    /** Uma chave desconhecida (gravada por uma versão futura, ou digitada à mão) ainda vira sinal. */
    fun porChave(key: String): CarSignal =
        porChave[key] ?: CarSignal(key = key, label = key, group = GRUPO_OUTROS)

    fun buscar(query: String): List<CarSignal> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return todos
        return todos.filter { it.label.lowercase().contains(q) || it.key.lowercase().contains(q) }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
