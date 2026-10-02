package br.com.opengw.managers

import br.com.opengw.veiculo.Veiculo
import android.util.Log
import br.com.opengw.veiculo.Sinal
import br.com.opengw.veiculo.Sinais
/**
 * EQ/efeitos de áudio pelo **DSP real do amplificador do carro**, via as chaves proprietárias
 * `sys.settings.audio.*` do `IIntelligentVehicleControlService` — o MESMO caminho de escrita
 * (`ServiceManager.updateData`) que já move temperatura/ventilação e que comprovadamente atua no
 * hardware.
 *
 * Por que isto e não [AudioEffectsManager]: aquele anexa `DynamicsProcessing`/`EnvironmentalReverb`
 * à sessão de áudio. Efeito na **sessão global 0** exige `MODIFY_AUDIO_ROUTING`, que é
 * `signature|privileged` — app comum não consegue nem via Shizuku (`pm grant` só dá permissão
 * runtime). E, mesmo que conseguisse, o efeito vive no mixer do AudioFlinger, **a montante** do
 * amplificador externo: áudio de Android Auto / Bluetooth roteado direto pro amp não passaria por
 * ele. O DSP do amp, ao contrário, afeta TODAS as fontes (mídia, AA, BT, telefone) — que é o que o
 * usuário efetivamente ouve. Por isso esta é a camada primária; a de software é complementar.
 *
 * Contrato defensivo: se as chaves não existirem/responderem neste carro, [isAvailable] devolve
 * false e todo setter é no-op — nada quebra. Ranges são lidos em runtime das chaves `*_range`,
 * então não dependemos de assumir min/max.
 */
object OemAudioDsp {
    private const val TAG = "OemAudioDsp"

    enum class Band { BASS, MID, TREBLE }


    private fun read(key: Sinal): String? =
            try {
                Veiculo.lerDoCarro(key.chave)?.takeIf { it.isNotBlank() }
            } catch (e: Throwable) {
                Log.w(TAG, "read ${key.chave} failed: ${e.message}")
                null
            }

    private fun write(key: Sinal, value: String): Boolean =
            try {
                Veiculo.escrever(key.chave, value)
                true
            } catch (e: Throwable) {
                Log.w(TAG, "write ${key.chave}=$value failed: ${e.message}")
                false
            }

    /** Aceita "min,max", "min-max", "min~max", "min max" ou um único "max" (assume min=0). */
    private fun parseRange(raw: String?, fallback: IntRange): IntRange {
        if (raw.isNullOrBlank()) return fallback
        val nums = Regex("-?\\d+").findAll(raw).map { it.value.toInt() }.toList()
        return when {
            nums.size >= 2 -> minOf(nums[0], nums[1])..maxOf(nums[0], nums[1])
            nums.size == 1 -> 0..nums[0]
            else -> fallback
        }
    }

    // --- Disponibilidade -----------------------------------------------------

    /** true se este carro expõe o DSP do amp (chave de range do EQ responde). */
    fun isAvailable(): Boolean = read(Sinais.SYS_SETTINGS_AUDIO_EQ_VALUE_RANGE) != null

    // --- Equalizador de 3 bandas (grave / médio / agudo) ---------------------

    fun eqRange(): IntRange =
            parseRange(read(Sinais.SYS_SETTINGS_AUDIO_EQ_VALUE_RANGE), -7..7)

    private fun bandKey(band: Band) =
            when (band) {
                Band.BASS -> Sinais.SYS_SETTINGS_AUDIO_EQ_BASS_VALUE
                Band.MID -> Sinais.SYS_SETTINGS_AUDIO_EQ_ALTO_VALUE
                Band.TREBLE -> Sinais.SYS_SETTINGS_AUDIO_EQ_TREBLE_VALUE
            }

    fun getBand(band: Band): Int {
        val r = eqRange()
        return read(bandKey(band))?.toIntOrNull()?.coerceIn(r) ?: ((r.first + r.last) / 2)
    }

    fun setBand(band: Band, value: Int): Boolean {
        val r = eqRange()
        return write(bandKey(band), value.coerceIn(r).toString())
    }

    /** UI em 0..100 -> range real do carro, pra o slider não precisar saber os limites do DSP. */
    fun setBandPercent(band: Band, percent: Int): Boolean {
        val r = eqRange()
        val v = r.first + Math.round((r.last - r.first) * (percent.coerceIn(0, 100) / 100f))
        return setBand(band, v)
    }

    fun getBandPercent(band: Band): Int {
        val r = eqRange()
        val span = (r.last - r.first).coerceAtLeast(1)
        return Math.round((getBand(band) - r.first) * 100f / span).coerceIn(0, 100)
    }

    // --- Presets -------------------------------------------------------------
    // Valores normalizados -1..+1 em torno do centro do range real do carro, então o mesmo preset
    // funciona tanto num DSP -7..+7 quanto num 0..14 sem reescrever nada.

    enum class Preset(val label: String, val bass: Float, val mid: Float, val treble: Float) {
        FLAT("Plano", 0f, 0f, 0f),
        ROCK("Rock", 0.65f, -0.2f, 0.5f),
        POP("Pop", 0.3f, 0.35f, 0.3f),
        JAZZ("Jazz", 0.45f, 0.1f, 0.4f),
        VOCAL("Vocal", -0.25f, 0.65f, 0.2f),
        BASS_BOOST("Grave+", 1f, -0.1f, -0.2f),
        TREBLE_BOOST("Agudo+", -0.2f, 0f, 1f);
    }

    /** Converte -1..+1 pro valor absoluto do DSP, centrado no meio do range real. */
    private fun normToRaw(norm: Float, r: IntRange): Int {
        val center = (r.first + r.last) / 2f
        val half = (r.last - r.first) / 2f
        return Math.round(center + norm.coerceIn(-1f, 1f) * half).coerceIn(r.first, r.last)
    }

    fun applyPreset(preset: Preset): Boolean {
        val r = eqRange()
        val ok = setBand(Band.BASS, normToRaw(preset.bass, r)) &&
                setBand(Band.MID, normToRaw(preset.mid, r)) &&
                setBand(Band.TREBLE, normToRaw(preset.treble, r))
        requestApplyAcoustics()
        return ok
    }

    /** Qual preset bate exatamente com o estado atual do DSP (null = ajuste manual). */
    fun currentPreset(): Preset? {
        val r = eqRange()
        val b = getBand(Band.BASS)
        val m = getBand(Band.MID)
        val t = getBand(Band.TREBLE)
        return Preset.entries.firstOrNull {
            normToRaw(it.bass, r) == b && normToRaw(it.mid, r) == m && normToRaw(it.treble, r) == t
        }
    }

    // --- "Reverb"/ambiência: campo sonoro + surround + DTS -------------------
    // Não é reverb algorítmico (o amp não faz), mas é o análogo espacial real que o carro oferece
    // e que o usuário efetivamente ouve nos alto-falantes.

    fun soundFieldRange(): IntRange =
            parseRange(read(Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE_RANGE), 0..100)

    fun getSoundField(): Int =
            read(Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE)?.toIntOrNull()
                    ?.coerceIn(soundFieldRange()) ?: soundFieldRange().first

    fun setSoundField(value: Int): Boolean =
            write(Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE, value.coerceIn(soundFieldRange()).toString())

    // --- Chaves booleanas do DSP/processamento -------------------------------

    /**
     * Cada entrada é um interruptor real do amplificador. [available] é checado em runtime porque
     * nem toda versão de firmware expõe todas — a UI só mostra o que este carro de fato responde.
     */
    enum class Toggle(val label: String, val description: String, val key: Sinal) {
        SURROUND(
                "Surround",
                "Processamento espacial do amplificador. É o mais próximo de \"reverb\" que o hardware do carro oferece.",
                Sinais.SYS_SETTINGS_AUDIO_SOUND_SURROUND_ENABLE),
        DTS(
                "DTS",
                "Decodificação/pós-processamento DTS do amplificador, quando presente.",
                Sinais.SYS_SETTINGS_AUDIO_SOUND_EFFECT_DTS_STATE),
        SOUND_QUALITY_RESTORE(
                "Restauração de qualidade",
                "Reconstrói agudos perdidos em áudio comprimido (MP3, streaming, Bluetooth).",
                Sinais.SYS_SETTINGS_AUDIO_SOUND_QUALITY_RESTORE_ENABLE),
        ANC(
                "Cancelamento de ruído (ANC)",
                "Cancelamento ativo de ruído pelos alto-falantes, se o carro tiver.",
                Sinais.SYS_SETTINGS_AUDIO_ANC_ENABLE),
        SYSTEM_ACOUSTICS(
                "Acústica do sistema",
                "Correção acústica de cabine aplicada pelo amplificador.",
                Sinais.SYS_SETTINGS_AUDIO_SYSTEM_ACOUSTICS_ENABLE),
        VOLUME_ADAPTATION(
                "Volume adaptativo",
                "Ajusta o volume automaticamente conforme o ruído do ambiente.",
                Sinais.SYS_SETTINGS_AUDIO_VOLUME_ADAPTATION_ENABLE),
        VOLUME_BY_SPEED(
                "Volume por velocidade",
                "Sobe o volume conforme a velocidade do carro.",
                Sinais.SYS_SETTINGS_AUDIO_VOLUME_ADJUST_BY_SPEED_STATE),
        VOLUME_BY_WIND(
                "Volume por ventilação",
                "Compensa o ruído do ar-condicionado no volume.",
                Sinais.SYS_SETTINGS_AUDIO_VOLUME_ADJUST_BY_WIND_STATE),
        EXTERNAL_AMP(
                "Amplificador externo",
                "Roteamento pro amplificador externo. Desligar pode mudar drasticamente o som.",
                Sinais.SYS_SETTINGS_AUDIO_EXTERNAL_AMP_ENABLE);

        fun available(): Boolean = read(key) != null
        fun isOn(): Boolean = read(key) == "1"
        fun set(on: Boolean): Boolean = write(key, if (on) "1" else "0")
    }

    fun availableToggles(): List<Toggle> = Toggle.entries.filter { it.available() }

    fun isSoundFieldAvailable(): Boolean = read(Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE_RANGE) != null

    /**
     * Alguns firmwares só materializam mudanças de acústica após um "commit". Se a chave de ação
     * existir, dispara; senão é no-op inofensivo (os setters já valem sozinhos na maioria).
     */
    fun requestApplyAcoustics(): Boolean =
            write(Sinais.SYS_OTHER_REQUEST_UPDATE_AUDIO_ACOUSTICS_ACTION, "1")

    /** Snapshot pra diagnóstico — o que este carro expõe de fato. Vai pra UI e pro log. */
    fun capabilities(): String = buildString {
        append("eqRange=").append(read(Sinais.SYS_SETTINGS_AUDIO_EQ_VALUE_RANGE))
        append(" bass=").append(read(Sinais.SYS_SETTINGS_AUDIO_EQ_BASS_VALUE))
        append(" mid=").append(read(Sinais.SYS_SETTINGS_AUDIO_EQ_ALTO_VALUE))
        append(" treble=").append(read(Sinais.SYS_SETTINGS_AUDIO_EQ_TREBLE_VALUE))
        append(" soundField=").append(read(Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE))
        append("/").append(read(Sinais.SYS_SETTINGS_AUDIO_SOUND_FIELD_VALUE_RANGE))
        Toggle.entries.forEach { append(" ").append(it.name).append("=").append(if (it.available()) (if (it.isOn()) "1" else "0") else "-") }
    }
}
