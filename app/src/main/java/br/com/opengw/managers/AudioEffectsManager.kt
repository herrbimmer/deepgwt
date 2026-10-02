package br.com.opengw.managers

import br.com.opengw.nucleo.OpenGw
import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.PresetReverb
import android.util.Log
import br.com.opengw.managers.asd.AsdAudioEngine
import br.com.opengw.nucleo.Preferencias
import java.util.concurrent.ConcurrentHashMap

/**
 * Equalizador de 8 bandas + reverb "Concerto" em software (`DynamicsProcessing` /
 * `EnvironmentalReverb` do AudioFlinger).
 *
 * ## Por que a versão anterior não funcionava
 * Ela anexava os efeitos **só à sessão global 0** (`AUDIO_SESSION_OUTPUT_MIX`). No Android 9 o
 * AudioFlinger recusa efeito global pra quem não tem `MODIFY_AUDIO_ROUTING` — que é
 * `signature|privileged` e **não** pode ser concedida a um app comum (nem via Shizuku: `pm grant`
 * só concede permissões runtime). Resultado: a anexação falhava e todo controle da tela virava
 * decoração.
 *
 * ## O que funciona de verdade pra um app comum
 * Efeito **por sessão de áudio** (modo insert). Não precisa de permissão privilegiada — só
 * `MODIFY_AUDIO_SETTINGS`, que já temos. A sessão de cada app que toca mídia é descoberta por dois
 * caminhos complementares:
 *
 *  1. **Broadcast padrão** `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` — é o mecanismo que o Android
 *     define justamente pra apps de equalizador. Players bem-comportados (Spotify, YouTube Music,
 *     players locais, etc.) anunciam a sessão ao começar a tocar. Tratado por
 *     [br.com.opengw.broadcastReceivers.AudioEffectSessionReceiver].
 *  2. **Varredura ativa** de `AudioManager.getActivePlaybackConfigurations()` ([discoverActiveSessions]),
 *     pra pegar quem toca sem anunciar.
 *
 * ## Limite honesto desta camada
 * Só alcança áudio que passa pelo AudioFlinger **deste** head unit. Android Auto e Bluetooth A2DP
 * roteados direto pro amplificador externo não passam por aqui — pra esses, o único controle real é
 * o DSP do amplificador, em [OemAudioDsp]. As duas camadas são complementares e a tela de Áudio
 * mostra as duas separadas.
 */
object AudioEffectsManager {
    private const val TAG = "AudioEffectsManager"
    const val BAND_COUNT = 8
    val BAND_FREQUENCIES_HZ = floatArrayOf(60f, 150f, 400f, 1000f, 2400f, 6000f, 12000f, 16000f)
    private const val MIN_GAIN_DB = -15f
    private const val MAX_GAIN_DB = 15f
    private const val GLOBAL_SESSION = 0
    private const val CHANNEL_COUNT = 2 // sistemas de som automotivos são estéreo

    // Prioridade do efeito. > 0 sinaliza ao AudioFlinger que queremos ganhar de um efeito de
    // prioridade menor disputando a mesma sessão.
    private const val EFFECT_PRIORITY = 1

    private val prefs by lazy {
        OpenGw.contextoProtegido.getSharedPreferences("opengw_prefs", Context.MODE_PRIVATE)
    }

    /** Conjunto de efeitos anexado a UMA sessão de áudio. */
    private class SessionFx(val sessionId: Int) {
        var dp: DynamicsProcessing? = null
        var envReverb: EnvironmentalReverb? = null
        /** Fallback: alguns HALs implementam PresetReverb mas não EnvironmentalReverb. */
        var presetReverb: PresetReverb? = null

        fun hasReverb() = envReverb != null || presetReverb != null

        fun release() {
            runCatching { dp?.release() }
            runCatching { envReverb?.release() }
            runCatching { presetReverb?.release() }
            dp = null
            envReverb = null
            presetReverb = null
        }
    }

    private val sessions = ConcurrentHashMap<Int, SessionFx>()

    @Volatile private var lastAttachFailure: String? = null

    // --- Estado / diagnóstico ------------------------------------------------

    /** Há ao menos uma sessão com efeito anexado de fato. */
    fun isAvailable(): Boolean = sessions.isNotEmpty()

    fun lastFailureReason(): String? = lastAttachFailure

    /** Quantas sessões de áudio estão sob efeito agora. */
    fun attachedSessionCount(): Int = sessions.size

    /** true se a sessão global (0) aceitou — só acontece se o app rodar com privilégio de sistema. */
    fun isGlobalAttached(): Boolean = sessions.containsKey(GLOBAL_SESSION)

    /**
     * O que o HAL de áudio deste carro realmente implementa. Se `DynamicsProcessing`/reverb não
     * aparecerem aqui, nenhum app consegue esses efeitos neste hardware — nem com privilégio — e a
     * única via é o DSP do amplificador ([OemAudioDsp]). Vale ouro pra diagnóstico no carro.
     */
    fun availableEffectNames(): List<String> =
            try {
                AudioEffect.queryEffects()?.map { it.name }?.distinct()?.sorted() ?: emptyList()
            } catch (e: Throwable) {
                Log.w(TAG, "queryEffects failed: ${e.message}")
                emptyList()
            }

    private fun hasEffectOfType(uuid: java.util.UUID): Boolean =
            try {
                AudioEffect.queryEffects()?.any { it.type == uuid } ?: false
            } catch (e: Throwable) {
                false
            }

    fun supportsEq(): Boolean = hasEffectOfType(AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING)
    fun supportsReverb(): Boolean =
            hasEffectOfType(AudioEffect.EFFECT_TYPE_ENV_REVERB) ||
                    hasEffectOfType(AudioEffect.EFFECT_TYPE_PRESET_REVERB)

    /**
     * true se a sessão é a da faixa do ASD. Quando o ASD não está tocando,
     * [AsdAudioEngine.audioSessionId] é [AsdAudioEngine.NO_AUDIO_SESSION] (-1), que nunca bate com
     * uma sessão real (> 0) — então isto nunca exclui mídia por engano.
     */
    private fun isAsdSession(sessionId: Int): Boolean =
            sessionId != AsdAudioEngine.NO_AUDIO_SESSION && sessionId == AsdAudioEngine.audioSessionId

    // --- Ciclo de vida -------------------------------------------------------

    /**
     * Chamado pelo serviço principal. Tenta a sessão global (funciona só com privilégio de sistema)
     * e, independente disso, varre as sessões de mídia que já estão tocando. A partir daí o
     * receiver de broadcast cuida das que forem começando.
     */
    @Synchronized
    fun start() {
        // Melhor esforço: se um dia o app rodar como sistema, isso passa a pegar tudo de uma vez.
        attachSession(GLOBAL_SESSION)
        discoverActiveSessions()
    }

    @Synchronized
    fun stop() {
        sessions.values.forEach { it.release() }
        sessions.clear()
    }

    /**
     * Anexa (ou reanexa) os efeitos a uma sessão. Idempotente. Cada efeito é tentado
     * separadamente: num HAL que tem EQ mas não tem reverb, o EQ ainda funciona.
     */
    @Synchronized
    fun attachSession(sessionId: Int) {
        if (sessions.containsKey(sessionId)) return
        // Nunca equalizar/reverberar o próprio ASD: o som simulado do motor tem que sair limpo,
        // independente do que o usuário faça no EQ da mídia. Esta é a exclusão pedida ("efeitos em
        // todos os canais exceto o ASD"). Ponto único e defensivo — vale pro broadcast e pro scan.
        if (isAsdSession(sessionId)) {
            Log.i(TAG, "skip ASD session $sessionId (não recebe efeitos de mídia)")
            return
        }
        val fx = SessionFx(sessionId)

        try {
            val eq = DynamicsProcessing.Eq(true, true, BAND_COUNT)
            for (band in 0 until BAND_COUNT) {
                eq.setBand(band, DynamicsProcessing.EqBand(true, BAND_FREQUENCIES_HZ[band], 0f))
            }
            // Limitador de segurança: evita clipping quando várias bandas são elevadas ao mesmo tempo.
            val limiter = DynamicsProcessing.Limiter(true, true, 0, 3f, 100f, 4f, -1f, 0f)
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                CHANNEL_COUNT,
                true, BAND_COUNT,
                false, 0,
                false, 0,
                true
            )
                .setPreEqAllChannelsTo(eq)
                .setLimiterAllChannelsTo(limiter)
                .build()
            fx.dp = DynamicsProcessing(EFFECT_PRIORITY, sessionId, config)
        } catch (e: Throwable) {
            // Throwable (não só Exception) porque UnsatisfiedLinkError e outros erros de anexar a
            // um tipo de efeito não suportado pelo HAL chegam como Error, não Exception.
            noteFailure("EQ", sessionId, e)
        }

        try {
            fx.envReverb = EnvironmentalReverb(EFFECT_PRIORITY, sessionId)
        } catch (e: Throwable) {
            noteFailure("EnvironmentalReverb", sessionId, e)
            // Fallback pra HAL que só tem o reverb de presets.
            try {
                fx.presetReverb = PresetReverb(EFFECT_PRIORITY, sessionId)
            } catch (e2: Throwable) {
                noteFailure("PresetReverb", sessionId, e2)
            }
        }

        if (fx.dp == null && !fx.hasReverb()) {
            fx.release()
            Log.w(TAG, "No effect could attach to session $sessionId")
            return
        }

        sessions[sessionId] = fx
        lastAttachFailure = null
        applyToSession(fx)
        Log.i(TAG, "Audio effects attached to session $sessionId " +
                "(eq=${fx.dp != null} envReverb=${fx.envReverb != null} presetReverb=${fx.presetReverb != null})")
    }

    @Synchronized
    fun detachSession(sessionId: Int) {
        sessions.remove(sessionId)?.let {
            it.release()
            Log.i(TAG, "Audio effects detached from session $sessionId")
        }
    }

    private fun noteFailure(what: String, sessionId: Int, e: Throwable) {
        val reason = "$what @ sessão $sessionId — ${e.javaClass.simpleName}: ${e.message}"
        lastAttachFailure = reason
        Log.w(TAG, "attach failed: $reason")
    }

    /**
     * Descobre sessões de mídia que já estão tocando. `AudioPlaybackConfiguration.getSessionId()`
     * não é API pública no Android 9, então vai por reflexão e falha em silêncio — o caminho
     * principal continua sendo o broadcast.
     */
    fun discoverActiveSessions() {
        try {
            val am = OpenGw.contexto.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val configs = am.activePlaybackConfigurations
            for (cfg in configs) {
                val id = try {
                    val m = cfg.javaClass.getMethod("getSessionId")
                    m.isAccessible = true
                    m.invoke(cfg) as? Int
                } catch (e: Throwable) {
                    null
                } ?: continue
                // attachSession já pula o ASD; o filtro aqui evita até o log de tentativa.
                if (id > 0 && !isAsdSession(id)) attachSession(id)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "discoverActiveSessions failed: ${e.message}")
        }
    }

    // --- Aplicação de estado -------------------------------------------------

    private fun applyToSession(fx: SessionFx) {
        val eqOn = isEqEnabled()
        // Sem o nível do Áudio (Alpha 8.0) o reverb fica desligado; a escolha gravada continua.
        val reverbOn = isReverbEnabled() && br.com.opengw.licenca.Portao.permite(br.com.opengw.licenca.Recurso.AUDIO)
        runCatching { fx.dp?.enabled = eqOn }
        getBandGains().forEachIndexed { band, gain -> applyBandGainTo(fx, band, gain) }
        runCatching { fx.envReverb?.enabled = reverbOn }
        runCatching { fx.presetReverb?.enabled = reverbOn }
        applyReverbRoomSizeTo(fx, getReverbRoomSize())
        applyReverbWetnessTo(fx, getReverbWetness())
    }

    fun applyFromPrefs() = sessions.values.forEach { applyToSession(it) }

    // --- EQ ------------------------------------------------------------------
    //
    // RETIRADO EM 2026-09-16. O equalizador de 8 bandas nunca funcionou nesta central: o HAL de
    // áudio do carro não implementa `DynamicsProcessing`, então a cadeia era criada, aceitava os
    // ganhos e não alterava um único quadro de som. A interface foi removida (ver AudioScreen) e
    // aqui a cadeia fica desligada de forma definitiva — as funções continuam existindo porque o
    // `applyToSession` e o `AudioEffectSessionReceiver` ainda passam por elas, mas nenhuma delas
    // liga mais nada. O equalizador de 3 bandas do amplificador (OemAudioDsp) é real e continua.

    fun isEqEnabled(): Boolean = false

    fun setEqEnabled(enabled: Boolean) {
        // Sem efeito de propósito: o EQ de software foi retirado do produto.
        prefs.edit().putBoolean(Preferencias.AUDIO_EQ_ENABLED.chave, false).apply()
        sessions.values.forEach { fx -> runCatching { fx.dp?.enabled = false } }
    }

    /** Sempre plano. Um ganho gravado por uma versão antiga não volta a ser aplicado. */
    fun getBandGains(): FloatArray = FloatArray(BAND_COUNT)

    fun setBandGain(band: Int, gainDb: Float) {
        // Sem efeito de propósito: o EQ de software foi retirado do produto.
    }

    private fun applyBandGainTo(fx: SessionFx, band: Int, gainDb: Float) {
        val dp = fx.dp ?: return
        try {
            for (channel in 0 until CHANNEL_COUNT) {
                val eqBand = dp.getPreEqBandByChannelIndex(channel, band)
                eqBand.gain = gainDb
                dp.setPreEqBandByChannelIndex(channel, band, eqBand)
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyBandGain($band) failed: ${e.message}", e)
        }
    }

    // --- Reverb --------------------------------------------------------------

    fun isReverbEnabled(): Boolean = prefs.getBoolean(Preferencias.AUDIO_REVERB_ENABLED.chave, false)

    fun setReverbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Preferencias.AUDIO_REVERB_ENABLED.chave, enabled).apply()
        sessions.values.forEach { fx ->
            runCatching { fx.envReverb?.enabled = enabled }
            runCatching { fx.presetReverb?.enabled = enabled }
        }
    }

    fun getReverbRoomSize(): Int = prefs.getInt(Preferencias.AUDIO_REVERB_ROOM_SIZE.chave, 40)

    fun setReverbRoomSize(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt(Preferencias.AUDIO_REVERB_ROOM_SIZE.chave, clamped).apply()
        sessions.values.forEach { applyReverbRoomSizeTo(it, clamped) }
    }

    fun getReverbWetness(): Int = prefs.getInt(Preferencias.AUDIO_REVERB_WETNESS.chave, 30)

    fun setReverbWetness(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        prefs.edit().putInt(Preferencias.AUDIO_REVERB_WETNESS.chave, clamped).apply()
        sessions.values.forEach { applyReverbWetnessTo(it, clamped) }
    }

    // O OpenSL ES EnvironmentalReverb não tem um parâmetro único de "tamanho do room" — a sensação
    // de espaço vem da combinação de decay time, difusão/densidade e delays de reflexão/cauda.
    private fun applyReverbRoomSizeTo(fx: SessionFx, percent: Int) {
        val t = percent / 100f
        fx.envReverb?.let { r ->
            try {
                r.decayTime = (100 + t * 6000).toInt()
                r.diffusion = (500 + t * 500).toInt().toShort()
                r.density = (500 + t * 500).toInt().toShort()
                r.reflectionsDelay = (t * 100).toInt()
                r.reverbDelay = (t * 40).toInt()
            } catch (e: Exception) {
                Log.e(TAG, "applyReverbRoomSize failed: ${e.message}", e)
            }
        }
        // No fallback de presets não há controle contínuo: mapeamos o tamanho do room no preset mais
        // próximo, que é a única granularidade que esse efeito oferece.
        fx.presetReverb?.let { r ->
            try {
                r.preset = when {
                    percent < 15 -> PresetReverb.PRESET_NONE
                    percent < 35 -> PresetReverb.PRESET_SMALLROOM
                    percent < 55 -> PresetReverb.PRESET_MEDIUMROOM
                    percent < 70 -> PresetReverb.PRESET_LARGEROOM
                    percent < 85 -> PresetReverb.PRESET_MEDIUMHALL
                    else -> PresetReverb.PRESET_LARGEHALL
                }
            } catch (e: Exception) {
                Log.e(TAG, "applyPresetReverb failed: ${e.message}", e)
            }
        }
    }

    // Idem para "wetness": não existe um parâmetro único de mix seco/molhado neste efeito, então
    // aproximamos subindo em conjunto o nível geral do room e o nível da cauda reverberante.
    // (PresetReverb não expõe níveis — por isso só o EnvironmentalReverb responde aqui.)
    private fun applyReverbWetnessTo(fx: SessionFx, percent: Int) {
        val r = fx.envReverb ?: return
        val t = percent / 100f
        try {
            r.roomLevel = (-3000 + t * 3000).toInt().toShort()
            r.reverbLevel = (-2000 + t * 4000).toInt().toShort()
            r.reflectionsLevel = (-2000 + t * 3000).toInt().toShort()
        } catch (e: Exception) {
            Log.e(TAG, "applyReverbWetness failed: ${e.message}", e)
        }
    }
}
