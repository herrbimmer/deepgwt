package br.com.opengw.streamdeck

import br.com.opengw.veiculo.Veiculo
import br.com.opengw.managers.SportPlusController
import br.com.opengw.veiculo.Sinal
import br.com.opengw.veiculo.Sinais
import java.util.Locale

/**
 * Actions assignable to a physical Stream Deck key. Each one knows how to (a) act on
 * [ServiceManager] when the key is pressed and (b) describe its own current icon, so the icon
 * can be redrawn both right after a press and whenever the underlying car value changes from
 * elsewhere (e.g. the driver used the dashboard controls instead of the deck).
 */
internal enum class StreamDeckKeyAction(val label: String) {
    AC_DRIVER_TEMP_UP("A/C motorista +") {
        override fun perform() = stepTemperature(Sinais.CAR_HVAC_DRIVER_TEMPERATURE, +TEMP_STEP)
        override fun renderState() = temperatureState(Sinais.CAR_HVAC_DRIVER_TEMPERATURE, StreamDeckGlyph.AC_UP)
        override fun watchedKeys() = listOf(Sinais.CAR_HVAC_DRIVER_TEMPERATURE.chave)
    },
    AC_DRIVER_TEMP_DOWN("A/C motorista -") {
        override fun perform() = stepTemperature(Sinais.CAR_HVAC_DRIVER_TEMPERATURE, -TEMP_STEP)
        override fun renderState() = temperatureState(Sinais.CAR_HVAC_DRIVER_TEMPERATURE, StreamDeckGlyph.AC_DOWN)
        override fun watchedKeys() = listOf(Sinais.CAR_HVAC_DRIVER_TEMPERATURE.chave)
    },
    AC_PASSENGER_TEMP_UP("A/C passageiro +") {
        override fun perform() = stepTemperature(Sinais.CAR_HVAC_PASS_TEMPERATURE, +TEMP_STEP)
        override fun renderState() = temperatureState(Sinais.CAR_HVAC_PASS_TEMPERATURE, StreamDeckGlyph.AC_UP)
        override fun watchedKeys() = listOf(Sinais.CAR_HVAC_PASS_TEMPERATURE.chave)
    },
    AC_PASSENGER_TEMP_DOWN("A/C passageiro -") {
        override fun perform() = stepTemperature(Sinais.CAR_HVAC_PASS_TEMPERATURE, -TEMP_STEP)
        override fun renderState() = temperatureState(Sinais.CAR_HVAC_PASS_TEMPERATURE, StreamDeckGlyph.AC_DOWN)
        override fun watchedKeys() = listOf(Sinais.CAR_HVAC_PASS_TEMPERATURE.chave)
    },
    VOLUME_UP("Volume +") {
        override fun perform() = stepVolume(+1)
        override fun renderState() = volumeState(StreamDeckGlyph.VOLUME_UP)
        override fun watchedKeys() = listOf(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave)
    },
    VOLUME_DOWN("Volume -") {
        override fun perform() = stepVolume(-1)
        override fun renderState() = volumeState(StreamDeckGlyph.VOLUME_DOWN)
        override fun watchedKeys() = listOf(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave)
    },
    SEAT_VENT_DRIVER_CYCLE("Ventilação banco motorista") {
        override fun perform() = cycleSeatVentilation(Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL)
        override fun renderState() = seatVentilationState(Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL)
        override fun watchedKeys() = listOf(
            Sinais.CAR_COMFORT_SETTING_DRIVER_SEAT_VENTILATION_LEVEL.chave,
            Sinais.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL.chave
        )
    },
    SEAT_VENT_PASSENGER_CYCLE("Ventilação banco passageiro") {
        override fun perform() = cycleSeatVentilation(Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL)
        override fun renderState() = seatVentilationState(Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL)
        override fun watchedKeys() = listOf(
            Sinais.CAR_COMFORT_SETTING_PASSENGER_SEAT_VENTILATION_LEVEL.chave,
            Sinais.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL.chave
        )
    },
    HVAC_POWER_TOGGLE("Liga/desliga A/C") {
        override fun perform() {
            val current = Veiculo.ler(Sinais.CAR_HVAC_POWER_MODE.chave)
            val next = if (current == "1") "0" else "1"
            Veiculo.escreverConferindo(Sinais.CAR_HVAC_POWER_MODE.chave, next)
        }
        override fun renderState(): StreamDeckIconState {
            val on = Veiculo.ler(Sinais.CAR_HVAC_POWER_MODE.chave) == "1"
            return StreamDeckIconState(glyph = StreamDeckGlyph.POWER, active = on)
        }
        override fun watchedKeys() = listOf(Sinais.CAR_HVAC_POWER_MODE.chave)
    },
    SPORT_PLUS_TOGGLE("SPORT+") {
        override fun perform() {
            if (SportPlusController.isActive) SportPlusController.deactivate() else SportPlusController.activate()
        }
        override fun renderState() =
            StreamDeckIconState(glyph = StreamDeckGlyph.SPORT_PLUS, active = SportPlusController.isActive)
        override fun watchedKeys() = listOf(Sinais.CAR_DRIVE_SETTING_DRIVE_MODE.chave)
    },
    CABIN_TEMP_DISPLAY("Temperatura interna (exibição)") {
        override fun perform() = Unit
        override fun renderState() = cabinTemperatureState()
        override fun watchedKeys() = listOf(Sinais.CAR_BASIC_INSIDE_TEMP.chave)
    };

    abstract fun perform()
    abstract fun renderState(): StreamDeckIconState

    /** Car data keys that, when they change, mean this action's icon might need a redraw. */
    abstract fun watchedKeys(): List<String>

    companion object {
        private const val TEMP_STEP = 0.5f
        private const val TEMP_MIN = 16.0f
        private const val TEMP_MAX = 32.0f
        private const val VOLUME_MIN = 0
        private const val VOLUME_MAX = 40

        fun fromStored(name: String?): StreamDeckKeyAction? =
            name?.let { stored -> entries.firstOrNull { it.name == stored } }

        private fun stepTemperature(key: Sinal, delta: Float) {
            val current = Veiculo.ler(key.chave)?.toFloatOrNull() ?: 22.0f
            val next = (current + delta).coerceIn(TEMP_MIN, TEMP_MAX)
            Veiculo.escreverConferindo(key.chave, String.format(Locale.US, "%.1f", next))
        }

        private fun stepVolume(delta: Int) {
            val current = Veiculo.ler(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave)?.toIntOrNull() ?: 0
            val next = (current + delta).coerceIn(VOLUME_MIN, VOLUME_MAX)
            Veiculo.escreverConferindo(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave, next.toString())
        }

        private fun cycleSeatVentilation(key: Sinal) {
            val max = Veiculo.ler(Sinais.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL.chave)?.toIntOrNull() ?: 3
            val current = Veiculo.ler(key.chave)?.toIntOrNull() ?: 0
            val next = if (max <= 0) 0 else (current + 1) % (max + 1)
            Veiculo.escreverConferindo(key.chave, next.toString())
        }

        private fun temperatureState(key: Sinal, glyph: StreamDeckGlyph): StreamDeckIconState {
            val value = Veiculo.ler(key.chave)?.toFloatOrNull()
            val label = value?.let { String.format(Locale.US, "%.1f°", it) } ?: "--"
            return StreamDeckIconState(glyph = glyph, label = label)
        }

        /** Same sentinel handling as InstrumentProjector2.formatTemp: "-1"/"255" mean "no reading yet", not 255°C. */
        private fun cabinTemperatureState(): StreamDeckIconState {
            val raw = Veiculo.ler(Sinais.CAR_BASIC_INSIDE_TEMP.chave)
            val label = if (raw == null || raw == "-1" || raw == "255") {
                "--"
            } else {
                raw.toFloatOrNull()?.let { String.format(Locale.US, "%.1f°", it) } ?: "--"
            }
            return StreamDeckIconState(glyph = StreamDeckGlyph.TEMP_DISPLAY, label = label)
        }

        private fun volumeState(glyph: StreamDeckGlyph): StreamDeckIconState {
            val volume = Veiculo.ler(Sinais.SYS_SETTINGS_AUDIO_MEDIA_VOLUME.chave)?.toIntOrNull() ?: 0
            return StreamDeckIconState(glyph = glyph, level = volume, maxLevel = VOLUME_MAX)
        }

        private fun seatVentilationState(key: Sinal): StreamDeckIconState {
            val max = Veiculo.ler(Sinais.CAR_COMFORT_SETTING_SEAT_VENTILATION_MAX_LEVEL.chave)?.toIntOrNull() ?: 3
            val level = Veiculo.ler(key.chave)?.toIntOrNull() ?: 0
            return StreamDeckIconState(glyph = StreamDeckGlyph.SEAT_VENT, level = level, maxLevel = max, active = level > 0)
        }
    }
}
