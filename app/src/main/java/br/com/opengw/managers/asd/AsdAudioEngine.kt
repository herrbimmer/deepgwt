package br.com.opengw.managers.asd

import br.com.opengw.nucleo.OpenGw
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import br.com.opengw.BuildConfig
import br.com.opengw.R
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Motor de áudio do ASD: porta do protótipo "HAVAL H6 ASD" (audio-engine.js) para Android.
 *
 * Ali, 12 amostras reais do motor (extraídas do Vehicles.bank/FMOD do próprio veículo, nos
 * pontos de RPM 1104..6723) tocavam em loop simultaneamente via Web Audio API: a cada RPM
 * simulado, as duas amostras de RPM mais próximas eram misturadas com crossfade de ganho, e o
 * playbackRate de cada uma era ajustado para afinar o pitch no RPM alvo — a mesma técnica de
 * "camadas por RPM" do evento FMOD original.
 *
 * A técnica de crossfade é idêntica aqui, mas a *implementação* é um mixer próprio em software
 * (ver [renderBlock]) escrevendo num único [AudioTrack] em MODE_STREAM, e não 12 AudioTrack
 * MODE_STATIC com [AudioTrack.setPlaybackRate].
 *
 * POR QUE NÃO USAR setPlaybackRate (a causa do "áudio picado"):
 * a versão anterior mantinha 12 faixas MODE_STATIC em loop e, a cada tick do controle (50Hz),
 * chamava setVolume()+setPlaybackRate() nas faixas audíveis. setPlaybackRate() não é um
 * parâmetro contínuo como o playbackRate do Web Audio: no AudioFlinger ele reconfigura o
 * resampler daquela faixa, o que descarta a fase fracionária acumulada. Com o RPM mudando a cada
 * tick, o resampler era reinicializado ~50x por segundo, e cada reinicialização é uma
 * descontinuidade na onda — ou seja, ~50 estalos por segundo. Isso explica todos os sintomas
 * relatados de uma vez: (a) o tom fixo do "Teste de saída de áudio" (uma faixa só, sem nenhuma
 * mudança de rate) sempre tocou limpo, provando que o caminho de saída/foco/HAL está bom;
 * (b) o "limiar de RPM" mudava a cada tentativa (5000, depois 2000) porque não havia limiar
 * nenhum — em RPM alto o próprio som do motor é áspero e mascara os estalos, em RPM baixo o som
 * é um ronco limpo e os estalos ficam óbvios; (c) apertar a faixa de pitch shift (testado
 * 0.85-1.2) não resolveu, só mudou o quanto o timbre ficava errado, porque o problema nunca foi
 * *quanto* o rate desviava e sim a *frequência com que era reconfigurado*.
 *
 * Fazendo a interpolação por conta própria (fase fracionária contínua + interpolação entre
 * amostras vizinhas — cúbica de Hermite desde a Alpha 6.3, linear antes), a mudança de pitch vira
 * um parâmetro realmente contínuo, como no protótipo
 * Web Audio e como no FMOD original. De quebra: 1 AudioTrack em vez de 12 (muito menos pressão no
 * HAL automotivo), zero chamadas nativas/Binder por tick (o loop de controle só escreve dois
 * campos @Volatile), e o tom passa a ser um passa-baixa de 1 polo no próprio mixer em vez de um
 * efeito nativo DynamicsProcessing (que também era reconfigurado via Binder em tempo real).
 */
/**
 * Canal de áudio (AudioAttributes usage/contentType) usado para tocar as faixas do motor
 * simulado. USAGE_MEDIA é o único confirmado até agora (ver comentário em [createTrack]) — as
 * demais opções existem para o usuário testar direto no carro (Ajustes > ASD > Avançado) sem
 * precisar de um novo build a cada tentativa, já que não há como validar remotamente qual canal a
 * central realmente abre para o alto-falante.
 */
enum class AsdAudioChannel(
    val usage: Int,
    val contentType: Int,
    val label: String
) {
    MEDIA(AudioAttributes.USAGE_MEDIA, AudioAttributes.CONTENT_TYPE_MUSIC, "Mídia (padrão, confirmado)"),
    NAVIGATION(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, AudioAttributes.CONTENT_TYPE_SPEECH, "Navegação/GPS"),
    NOTIFICATION(AudioAttributes.USAGE_NOTIFICATION, AudioAttributes.CONTENT_TYPE_SONIFICATION, "Notificação"),
    ALARM(AudioAttributes.USAGE_ALARM, AudioAttributes.CONTENT_TYPE_SONIFICATION, "Alarme"),
    VOICE_COMMUNICATION(AudioAttributes.USAGE_VOICE_COMMUNICATION, AudioAttributes.CONTENT_TYPE_SPEECH, "Chamada de voz"),
    GAME(AudioAttributes.USAGE_GAME, AudioAttributes.CONTENT_TYPE_SONIFICATION, "Jogo"),
    ASSISTANT(AudioAttributes.USAGE_ASSISTANT, AudioAttributes.CONTENT_TYPE_SPEECH, "Assistente"),
    SONIFICATION(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION, AudioAttributes.CONTENT_TYPE_SONIFICATION, "Sonificação (sabidamente mudo nesta central)");

    companion object {
        fun fromKey(key: String?): AsdAudioChannel = entries.firstOrNull { it.name == key } ?: MEDIA
    }
}

/**
 * Como o volume do motor simulado é calculado:
 * - [FIXED]: um único volume constante (o slider "Volume do ASD"), independente do RPM.
 * - [RPM]: o volume varia linearmente com o RPM entre dois pontos definidos pelo usuário — o
 *   volume em RPM baixas (início da curva) e o volume em RPM altas (fim da curva). Entre os
 *   extremos de RPM o valor é interpolado linearmente; fora deles é preso no ponto mais próximo.
 */
enum class AsdVolumeMode {
    FIXED,
    RPM;

    companion object {
        fun fromKey(key: String?): AsdVolumeMode = entries.firstOrNull { it.name == key } ?: FIXED
    }
}

object AsdAudioEngine {
    private const val TAG = "AsdAudioEngine"

    /** Sentinela de "sem faixa" para [audioSessionId]. Sessões reais de AudioTrack são > 0. */
    const val NO_AUDIO_SESSION = -1

    private const val OUT_SAMPLE_RATE = 48000

    // LATÊNCIA (Alpha 6.3). Antes: blocos de 10 ms e uma faixa de 8 blocos (80 ms) fixos, mais o
    // tick de 20 ms do controle — ~100 ms entre o pé e o som. Agora o bloco é de 5 ms e a faixa
    // nasce pequena e só CRESCE quando o próprio AudioTrack conta um underrun (buraco no som).
    // Assim a folga é a menor que esta central aguenta sem picotar, medida nela mesma, em vez de
    // um chute conservador. A capacidade (teto do crescimento) continua sendo os 80 ms de antes.
    private const val BLOCK_FRAMES = 240 // 5 ms por bloco
    private const val BUFFER_CAPACITY_BLOCKS = 16 // 80 ms: teto do buffer adaptativo
    private const val BUFFER_START_BLOCKS = 3 // 15 ms: ponto de partida quando o caminho rápido é concedido

    // Entrada e saída suaves do som (ativação/desativação, troca de perfil). Sem isso, ligar e
    // desligar junto com o motor a combustão — que num híbrido liga e desliga sozinho várias vezes
    // por viagem — seria um estalo a cada vez.
    private const val FADE_IN_S = 0.15
    private const val FADE_OUT_S = 0.35
    private const val FADE_TROCA_DE_PERFIL_S = 0.08

    // Mantido igual ao protótipo original. Agora é só o limite de quanto uma amostra pode ser
    // esticada/comprimida na interpolação; não há mais reconfiguração de resampler envolvida, então
    // um range largo não custa nada (a tentativa anterior de apertar pra 0.85-1.2 deixou o som
    // "artificial" e foi revertida — ver histórico no cabeçalho da classe).
    private const val PITCH_MIN = 0.6
    private const val PITCH_MAX = 1.6

    // Passa-baixa de 1 polo controlado pelo Load, no lugar do DynamicsProcessing: aceleração deixa
    // o som aberto, desaceleração/freio-motor mais abafado — igual em espírito ao lowpass do
    // audio-engine.js original.
    private const val TONE_CUTOFF_MIN_HZ = 700.0
    private const val TONE_CUTOFF_MAX_HZ = 13700.0

    // Extremos de RPM da curva de volume no modo RPM. Alinhados à escala do tacômetro da UI
    // (marcha lenta ~800, redline ~7000, mesma escala do LinearProgressIndicator em AsdScreen):
    // o "ponto inicial" do usuário vale a partir de RPM_VOL_MIN e o "ponto final" a partir de
    // RPM_VOL_MAX, com interpolação linear entre eles.
    private const val RPM_VOL_MIN = 800.0
    private const val RPM_VOL_MAX = 7000.0

    // Passa-baixa do efeito "muffled": totalmente aberto (transparente) sem muffle, fechando até
    // um corte grave quando no máximo — o som fica abafado como ouvido de fora do carro.
    private const val MUFFLE_CUTOFF_OPEN_HZ = 16000.0
    private const val MUFFLE_CUTOFF_CLOSED_HZ = 500.0

    // O abafamento de 100% existe para o motor a combustão soar "lá fora". O zumbido elétrico é
    // som de cabine por natureza — abafado em 500 Hz ele perderia justamente o agudo que o faz
    // soar elétrico. Por isso o perfil elétrico usa um abafamento leve e fixo.
    private const val MUFFLE_ELETRICO = 0.3

    // Tom do perfil elétrico: o filtro abre a partir de um piso mais alto que o da combustão.
    private const val TONE_CUTOFF_MIN_ELETRICO_HZ = 3000.0

    /** Escala do sintetizador elétrico para ficar no mesmo nível das amostras de combustão. */
    private const val NIVEL_ELETRICO = 9000.0

    /** Parciais do zumbido elétrico: múltiplo da fundamental. Ver [prepararEletrico]. */
    private val RAZOES_ELETRICO = doubleArrayOf(0.5, 1.0, 1.006, 2.0, 3.0, 8.5)
    private const val TABELA_SENO_TAM = 4096
    private val TABELA_SENO = DoubleArray(TABELA_SENO_TAM + 1) { i ->
        kotlin.math.sin(2.0 * Math.PI * i / TABELA_SENO_TAM)
    }

    private data class EngineSample(val rpm: Int, val rawResId: Int)

    private val SAMPLES = listOf(
        EngineSample(1104, R.raw.asd_eng_01104),
        EngineSample(1560, R.raw.asd_eng_01560),
        EngineSample(1999, R.raw.asd_eng_01999),
        EngineSample(2549, R.raw.asd_eng_02549),
        EngineSample(2900, R.raw.asd_eng_02900),
        EngineSample(3472, R.raw.asd_eng_03472),
        EngineSample(3845, R.raw.asd_eng_03845),
        EngineSample(4329, R.raw.asd_eng_04329),
        EngineSample(4812, R.raw.asd_eng_04812),
        EngineSample(5845, R.raw.asd_eng_05845),
        EngineSample(6262, R.raw.asd_eng_06262),
        EngineSample(6723, R.raw.asd_eng_06723),
    )

    /**
     * Uma camada do crossfade. [phase] é a posição de leitura fracionária dentro de [pcm] e nunca
     * é reiniciada enquanto o motor toca — é justamente essa continuidade que elimina os estalos.
     * [gain]/[inc] guardam os valores do fim do bloco anterior, usados como ponto de partida da
     * rampa do bloco seguinte (sem rampa, um degrau de ganho a cada bloco seria audível como
     * "zipper noise").
     */
    private class Voice(val rpm: Int, val pcm: ShortArray, val srRatio: Double) {
        var phase = 0.0
        var gain = 0.0
        var inc = srRatio
    }

    private var voices: Array<Voice> = emptyArray()
    private var targetGain = DoubleArray(0)
    private var targetInc = DoubleArray(0)
    private var lpState = 0.0
    private var lpA = 1.0
    private var muffleState = 0.0

    // Perfil de som. [perfilPedido] é escrito por quem configura; a thread de render só troca o
    // [perfilAtivo] com o som em silêncio (ver [renderBlock]), para a troca não estalar.
    @Volatile private var perfilPedido = AsdPerfilDeSom.COMBUSTAO
    private var perfilAtivo = AsdPerfilDeSom.COMBUSTAO

    // Tom: fator sobre o pitch das amostras (combustão) ou sobre a fundamental (elétrico).
    @Volatile private var tomFator = 1.0

    // Portão de entrada/saída (0 = mudo, 1 = aberto), perseguido pela thread de render.
    @Volatile private var gateAlvo = 0.0
    @Volatile private var gate = 0.0

    // Estado do sintetizador elétrico: fase (em ciclos, 0..1) e ganho de cada parcial, e a
    // fundamental no fim do bloco anterior — ponto de partida da rampa do bloco seguinte.
    private val fasesEletrico = DoubleArray(RAZOES_ELETRICO.size)
    private val ganhosEletrico = DoubleArray(RAZOES_ELETRICO.size)
    private val alvosEletrico = DoubleArray(RAZOES_ELETRICO.size)
    private var freqEletrico = AsdDsp.ZUMBIDO_HZ_PARADO
    private var freqEletricoAlvo = AsdDsp.ZUMBIDO_HZ_PARADO
    private var faseLfo = 0.0

    // Buffer adaptativo: o menor tamanho que esta central aguentou sem underrun é guardado (o
    // [AsdManager] persiste via [aoAprenderBuffer]) e cada partida nova tenta UM bloco abaixo dele.
    // Crescer só em underrun e nunca tentar descer faria o valor engordar com qualquer soluço de
    // sistema (boot, atualização); tentar um bloco abaixo por partida mantém o valor colado no
    // mínimo real, ao custo de no máximo um underrun por partida — e ele cai dentro da entrada
    // suave, quando o som ainda está baixo.
    @Volatile private var bufferAprendidoFrames = 0
    @Volatile var aoAprenderBuffer: ((Int) -> Unit)? = null
    private var underrunsVistos = 0
    val latenciaDoBufferMs = mutableStateOf(0)
    val caminhoRapidoConcedido = mutableStateOf(false)
    val ajustesDeBuffer = mutableStateOf(0)

    private var track: AudioTrack? = null

    /**
     * Sessão de áudio da faixa do ASD, ou [NO_AUDIO_SESSION] quando não há faixa viva.
     *
     * Publicado pro [br.com.opengw.managers.AudioEffectsManager] EXCLUIR esta
     * sessão do EQ/reverb: o som do motor não pode ser equalizado/reverberado junto com a mídia.
     * `@Volatile` porque quem lê (o manager) roda em outra thread que não a de render.
     */
    @Volatile
    var audioSessionId: Int = NO_AUDIO_SESSION
        private set

    private var renderThread: Thread? = null
    @Volatile private var rendering = false

    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var loaded = false
    @Volatile private var playing = false
    @Volatile private var masterVolume = 0.5f // 0..1, independente do volume de mídia (modo FIXED)
    // Volume dependente do RPM (modo RPM): curva linear de [rpmVolLow] em RPM_VOL_MIN até
    // [rpmVolHigh] em RPM_VOL_MAX. Ambos 0..1. Lidos pela thread de render a cada bloco.
    @Volatile private var volumeMode = AsdVolumeMode.FIXED
    @Volatile private var rpmVolLow = 0.5f
    @Volatile private var rpmVolHigh = 0.5f
    // Posicionamento espacial (grade em cruz na UI). posX: -1 esquerda .. +1 direita (balanço
    // estéreo real); posY: -1 trás .. +1 frente (trás = mais baixo e mais abafado, simulando o
    // motor se afastando). Neutro (0,0) reproduz o som centralizado, idêntico ao comportamento
    // anterior.
    @Volatile private var posX = 0.0f
    @Volatile private var posY = 0.0f
    // Efeito "muffled": passa-baixa extra simulando o motor do lado de fora do carro (som abafado
    // através do vidro/lataria). muffleAmount 0..1 controla o quanto abafa.
    @Volatile private var muffleEnabled = false
    @Volatile private var muffleAmount = 0.5f
    @Volatile private var channel = AsdAudioChannel.MEDIA
    @Volatile private var focusLossCount = 0

    // Alvos escritos pelo loop de controle (AsdManagerThread, 100Hz) e lidos pela thread de render.
    // São só dois campos @Volatile: o loop de controle não faz mais nenhuma chamada nativa/Binder
    // por tick, o que era a outra fonte de pressão sobre o audioserver nesta central.
    @Volatile private var targetRpm = 800.0
    @Volatile private var targetLoad = 0.0

    // Quando true, outro app pediu só "ducking" (abaixar volume) — aplicamos [DUCK_VOLUME_FACTOR]
    // em cima do volume calculado normalmente. Quando [backingOff] é true, estamos numa perda
    // permanente de foco (ver AUDIOFOCUS_LOSS abaixo) e paramos de tocar até o backoff expirar.
    @Volatile private var duckedByOther = false
    @Volatile private var backingOff = false
    private const val DUCK_VOLUME_FACTOR = 0.2
    private const val FOCUS_LOSS_BACKOFF_MS = 3000L

    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var backoffReacquireRunnable: Runnable? = null

    // Reassert periódico de foco: mitigação para o corte de áudio relatado só em Android
    // Automotive (emulador AAOS e carro real), nunca numa VM Android comum — cobre o caso do
    // próprio grant de foco ter sido derrubado silenciosamente pelo HAL sem disparar
    // AUDIOFOCUS_LOSS (o listener abaixo nunca é chamado nesse caso, porque nada "muda de
    // estado" do ponto de vista da API pública). Só chama AudioManager.requestAudioFocus(), não
    // mexe no AudioTrack.
    private const val FOCUS_REASSERT_INTERVAL_MS = 12000L
    @Volatile private var lastFocusReassertMs = 0L
    val focusLossCountState = mutableStateOf(0)

    val engineFailureCountState = mutableStateOf(0)
    @Volatile private var engineFailureCount = 0

    // Teste de saída de áudio: tom senoidal fixo (uma faixa só, sem crossfade, sem mudança de
    // pitch, sem filtro) tocado no MESMO canal que o motor do ASD usa. Serve pra separar "o
    // caminho de saída está mudo" de "a síntese está com defeito" — foi ele que provou que o
    // caminho de saída estava bom e que o problema era a síntese (ver cabeçalho da classe).
    private var testToneTrack: AudioTrack? = null
    private var testToneFocusRequest: AudioFocusRequest? = null
    @Volatile private var testTonePlaying = false
    private const val TEST_TONE_HZ = 440.0
    val testTonePlayingState = mutableStateOf(false)
    val testTonePlayStateError = mutableStateOf<String?>(null)

    // Estado observável pela UI (AsdScreen): a UI mostra RPM simulado independente de o
    // AudioTrack ter carregado de verdade (o RPM vem do DrivetrainSimulator, não daqui) — sem
    // isso, "carregou mas não sai som" e "nem carregou" ficam indistinguíveis sem logcat.
    val isLoaded = mutableStateOf(false)
    val lastError = mutableStateOf<String?>(null)
    val lastPlayStateError = mutableStateOf<String?>(null)
    val currentChannel = mutableStateOf(AsdAudioChannel.MEDIA)

    // Captura do PCM sintetizado, só no build "simulator" (QEMU/AAOS): permite puxar o áudio
    // exato que o motor produziu e medir descontinuidades offline, em vez de depender do ouvido
    // pra dizer se ainda "pica".
    private const val DUMP_SECONDS = 25
    @Volatile private var dumpBuf: ShortArray? = null
    @Volatile private var dumpPos = 0

    /**
     * (Re)inicia a captura do PCM sintetizado. Chamado no começo do "Testar ASD" porque o teste
     * costuma reaproveitar um motor que já estava tocando — sem isso a captura seria sempre dos
     * primeiros segundos depois do boot, e não do teste em si. Sem efeito fora do build simulator.
     */
    fun beginDump() {
        if (!BuildConfig.SIMULATOR_MODE) return
        dumpPos = 0
        dumpBuf = ShortArray(OUT_SAMPLE_RATE * 2 * DUMP_SECONDS)
    }

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Só querem que a gente abaixe o volume por um instante (ex.: aviso de
                // navegação) — não é uma disputa de foco. Pedir foco de volta aqui é que criaria
                // um cabo de guerra com quem só queria "ducking".
                duckedByOther = true
                lastPlayStateError.value = "Volume reduzido (outro app pediu ducking)"
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusLossCount++
                focusLossCountState.value = focusLossCount
                duckedByOther = false
                lastPlayStateError.value =
                    "Perdeu foco de áudio temporariamente (${focusLossCount}x) — readquirindo"
                if (playing) requestAudioFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Perda permanente: outro app pediu GAIN não-transiente de verdade. Readquirir na
                // hora brigaria pelo foco com esse áudio legítimo do usuário — em vez disso, cede:
                // silencia e só tenta de novo depois de um pequeno backoff.
                focusLossCount++
                focusLossCountState.value = focusLossCount
                duckedByOther = false
                lastPlayStateError.value =
                    "Perdeu foco de áudio (outro app assumiu, ${focusLossCount}x) — aguardando antes de tentar de novo"
                scheduleBackoffReacquire()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                duckedByOther = false
                backingOff = false
                backoffReacquireRunnable?.let { mainHandler.removeCallbacks(it) }
                if (playing) lastPlayStateError.value = null
            }
        }
    }

    private fun scheduleBackoffReacquire() {
        backingOff = true
        backoffReacquireRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            backingOff = false
            if (playing) requestAudioFocus()
        }
        backoffReacquireRunnable = runnable
        mainHandler.postDelayed(runnable, FOCUS_LOSS_BACKOFF_MS)
    }

    private fun currentAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(channel.usage)
            .setContentType(channel.contentType)
            .build()

    /**
     * Troca o canal de áudio usado pela faixa de saída. Como [AudioAttributes] é imutável por
     * faixa (definido na criação do [AudioTrack]), a troca recria a faixa — se estava tocando,
     * volta a tocar no canal novo na sequência, pra dar pra comparar sem sair da tela. As amostras
     * em memória são reaproveitadas (não há releitura dos WAVs).
     */
    @Synchronized
    fun setChannel(newChannel: AsdAudioChannel) {
        if (channel == newChannel) return
        channel = newChannel
        currentChannel.value = newChannel
        if (!loaded) return
        val wasPlaying = playing
        release()
        ensureLoaded()
        if (wasPlaying && loaded) start()
    }

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        try {
            loadSamples()
            createTrack()
            loaded = true
            isLoaded.value = true
            lastError.value = null
            Log.i(TAG, "ASD audio engine loaded (${voices.size} camadas, mixer próprio a ${OUT_SAMPLE_RATE}Hz)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load ASD audio engine: ${e.message}", e)
            isLoaded.value = false
            lastError.value = "${e.javaClass.simpleName}: ${e.message}"
            release()
        }
    }

    private fun loadSamples() {
        if (voices.isNotEmpty()) return
        voices = SAMPLES.map { sample ->
            val wav = readWavPcm16(sample.rawResId)
            // Todas as amostras do banco são mono 48kHz; se alguma vier estéreo, colapsa pra mono
            // (o mixer trabalha em mono e duplica pros dois canais na saída).
            val mono = if (wav.channels <= 1) wav.pcm else ShortArray(wav.pcm.size / wav.channels) { i ->
                var acc = 0
                for (c in 0 until wav.channels) acc += wav.pcm[i * wav.channels + c]
                (acc / wav.channels).toShort()
            }
            // Amostra gravada num sample rate diferente da saída vira um fator fixo no passo de
            // leitura, não uma reamostragem em tempo real.
            Voice(sample.rpm, mono, wav.sampleRate.toDouble() / OUT_SAMPLE_RATE)
        }.toTypedArray()
        targetGain = DoubleArray(voices.size)
        targetInc = DoubleArray(voices.size)
    }

    private fun createTrack() {
        // USAGE_ASSISTANCE_SONIFICATION foi testado no carro e não sai som nenhum: essa central só
        // roteia para o alto-falante os usos que ela reconhece (mídia/navegação/chamada). Como o
        // áudio de mídia comum comprovadamente sai pelo som do carro, esse é o canal padrão — mas
        // o usuário pode trocar em Ajustes > ASD > Avançado (ver [AsdAudioChannel]/[setChannel]).
        val minBuf = AudioTrack.getMinBufferSize(
            OUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val capacity = BLOCK_FRAMES * 2 * 2 * BUFFER_CAPACITY_BLOCKS
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setSampleRate(OUT_SAMPLE_RATE)
            .build()
        val t = AudioTrack.Builder()
            .setAudioAttributes(currentAttrs())
            .setAudioFormat(format)
            .setBufferSizeInBytes(max(minBuf, capacity))
            .setTransferMode(AudioTrack.MODE_STREAM)
            // Pede o caminho rápido do AudioFlinger. A central pode negar (principalmente para o
            // uso de alarme); negado, a faixa é uma faixa comum e o buffer adaptativo abaixo parte
            // do mínimo que o Android recomenda para esse caminho.
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        track = t
        underrunsVistos = t.underrunCount
        val rapido = t.performanceMode == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY
        caminhoRapidoConcedido.value = rapido
        val capacidadeFrames = t.bufferCapacityInFrames
        val partida = when {
            bufferAprendidoFrames > 0 -> max(BUFFER_START_BLOCKS * BLOCK_FRAMES, bufferAprendidoFrames - BLOCK_FRAMES)
            rapido -> BUFFER_START_BLOCKS * BLOCK_FRAMES
            else -> max(BUFFER_START_BLOCKS * BLOCK_FRAMES, minBuf / 4)
        }
        aplicarTamanhoDoBuffer(t, partida.coerceAtMost(capacidadeFrames))
        // Publica a sessão pra que os efeitos de mídia possam PULAR o ASD (não equalizar o motor).
        audioSessionId = t.audioSessionId
    }

    private fun aplicarTamanhoDoBuffer(t: AudioTrack, frames: Int) {
        val efetivo = t.setBufferSizeInFrames(frames)
        val atual = if (efetivo > 0) efetivo else t.bufferSizeInFrames
        latenciaDoBufferMs.value = atual * 1000 / OUT_SAMPLE_RATE
    }

    /**
     * Chamado depois de cada write. Se o AudioTrack contou um underrun novo, a folga atual é
     * pequena demais para esta central: cresce um bloco (5 ms) e guarda o tamanho. Nunca encolhe
     * sozinho — encolher é o que produziria o próximo buraco.
     */
    private fun ajustarBufferSeHouveUnderrun(t: AudioTrack) {
        val underruns = t.underrunCount
        if (underruns <= underrunsVistos) return
        underrunsVistos = underruns
        val atual = t.bufferSizeInFrames
        val capacidade = t.bufferCapacityInFrames
        if (atual >= capacidade) return
        val novo = (atual + BLOCK_FRAMES).coerceAtMost(capacidade)
        aplicarTamanhoDoBuffer(t, novo)
        bufferAprendidoFrames = t.bufferSizeInFrames
        ajustesDeBuffer.value = ajustesDeBuffer.value + 1
        val aprendido = bufferAprendidoFrames
        aoAprenderBuffer?.let { cb -> mainHandler.post { cb(aprendido) } }
        Log.i(TAG, "Underrun na faixa do ASD — buffer ${atual} → ${t.bufferSizeInFrames} frames")
    }

    @Synchronized
    fun start() {
        ensureLoaded()
        if (!loaded) return
        // Já tocando (inclusive no meio de uma saída suave): basta reabrir o portão.
        gateAlvo = 1.0
        if (playing) return
        playing = true
        gate = 0.0
        // No build simulator, toda partida do som é capturada (não só o "Testar ASD"): é assim que
        // a entrada suave e o modo "motor a combustão" podem ser medidos no PCM, fora do teste.
        if (BuildConfig.SIMULATOR_MODE && dumpBuf == null) beginDump()
        lastFocusReassertMs = android.os.SystemClock.elapsedRealtime()
        requestAudioFocus()

        lpState = 0.0
        muffleState = 0.0
        val t = track ?: return
        t.play()
        // play() não lança exceção quando a central recusa o áudio no nível do HAL — o AudioTrack
        // simplesmente não entra em PLAYSTATE_PLAYING. Checar isso aqui é a única forma de
        // diferenciar "carregou e tocou" de "carregou mas a central engoliu o som" sem logcat.
        lastPlayStateError.value = if (t.playState != AudioTrack.PLAYSTATE_PLAYING) {
            "play() não colocou a faixa em PLAYING (estado ${t.playState})"
        } else {
            null
        }

        rendering = true
        renderThread = Thread({ renderLoop() }, "AsdRenderThread").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    /**
     * Começa a saída suave sem parar nada. Quem desliga de fato é o [AsdManager], chamando [stop]
     * quando [saidaConcluida] disser que o som já chegou ao silêncio — assim a thread de render
     * nunca precisa se encerrar sozinha.
     */
    fun fadeOut() {
        gateAlvo = 0.0
    }

    /** true quando uma saída suave pedida por [fadeOut] já chegou ao silêncio (ou nada toca). */
    fun saidaConcluida(): Boolean = !playing || (gateAlvo == 0.0 && gate <= 1e-4)

    /** Buffer aprendido em partidas anteriores (frames), ou 0 para aprender do zero. */
    fun setBufferAprendido(frames: Int) {
        if (frames > 0) bufferAprendidoFrames = frames
    }

    fun setPerfil(novo: AsdPerfilDeSom) {
        perfilPedido = novo
    }

    /** Tom do motor em porcentagem, -20 (mais grave) .. +20 (mais agudo). */
    fun setTom(percent: Int) {
        tomFator = 1.0 + percent.coerceIn(-20, 20) / 100.0
    }

    @Synchronized
    fun stop() {
        playing = false
        gateAlvo = 0.0
        gate = 0.0
        backoffReacquireRunnable?.let { mainHandler.removeCallbacks(it) }
        backoffReacquireRunnable = null
        backingOff = false
        duckedByOther = false
        stopRendering()
        abandonAudioFocus()
        flushDump()
    }

    /**
     * Encerra a thread de render sem destruir a faixa. A ordem importa: derrubar a flag primeiro e
     * só então parar a faixa desbloqueia um write() em andamento (write é bloqueante em
     * MODE_STREAM), deixando a thread sair pelo topo do laço em vez de ficar presa.
     */
    private fun stopRendering() {
        rendering = false
        try {
            track?.pause()
            track?.flush()
        } catch (e: Exception) {
            // faixa já pode estar parada
        }
        renderThread?.let {
            try {
                it.join(500)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        renderThread = null
        try {
            track?.stop()
        } catch (e: Exception) {
            // faixa já pode estar parada
        }
    }

    fun setMasterVolume(percent: Int) {
        masterVolume = (percent / 100f).coerceIn(0f, 1f)
    }

    fun setVolumeMode(mode: AsdVolumeMode) {
        volumeMode = mode
    }

    /**
     * Define os dois pontos da curva de volume por RPM (modo [AsdVolumeMode.RPM]).
     * @param lowPercent volume 0..100 em RPM baixas (ponto inicial).
     * @param highPercent volume 0..100 em RPM altas (ponto final).
     */
    fun setRpmVolumeCurve(lowPercent: Int, highPercent: Int) {
        rpmVolLow = (lowPercent / 100f).coerceIn(0f, 1f)
        rpmVolHigh = (highPercent / 100f).coerceIn(0f, 1f)
    }

    /**
     * Posição espacial do motor na grade em cruz.
     * @param x -1 (esquerda) .. +1 (direita) — balanço estéreo.
     * @param y -1 (trás) .. +1 (frente) — trás fica mais baixo e mais abafado.
     */
    fun setPosition(x: Float, y: Float) {
        posX = x.coerceIn(-1f, 1f)
        posY = y.coerceIn(-1f, 1f)
    }

    fun setMuffle(enabled: Boolean, amountPercent: Int) {
        muffleEnabled = enabled
        muffleAmount = (amountPercent / 100f).coerceIn(0f, 1f)
    }

    /**
     * Chamado pelo loop de controle a ~100Hz. Não faz trabalho de áudio nenhum: só publica os
     * alvos que a thread de render vai perseguir suavemente. Qualquer travada/atraso do loop de
     * controle agora é inaudível (o render continua com o último alvo), o oposto do desenho
     * anterior, em que o áudio dependia de o tick chegar na hora.
     *
     * @param rpm RPM simulado atual.
     * @param load -1..1, positivo em aceleração e negativo em desaceleração/freio-motor —
     *        controla o tom (mais aberto/abafado) e o volume geral, igual ao protótipo original.
     */
    fun update(rpm: Double, load: Double) {
        targetRpm = rpm
        targetLoad = load
        if (playing && !backingOff) {
            val nowRt = android.os.SystemClock.elapsedRealtime()
            if (nowRt - lastFocusReassertMs >= FOCUS_REASSERT_INTERVAL_MS) {
                lastFocusReassertMs = nowRt
                requestAudioFocus()
            }
        }
    }

    private fun renderLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        val out = ShortArray(BLOCK_FRAMES * 2)
        while (rendering) {
            try {
                renderBlock(out)
                appendDump(out)
                val t = track ?: break
                val written = t.write(out, 0, out.size)
                if (written >= 0) ajustarBufferSeHouveUnderrun(t)
                if (written < 0 && rendering) {
                    // ERROR_DEAD_OBJECT (-6) é o caso do audioserver ter reiniciado: a faixa velha
                    // não volta com play(), só recriando. Os demais códigos são tratados igual,
                    // porque a recuperação possível é a mesma.
                    Log.e(TAG, "AudioTrack.write falhou ($written) — recriando faixa")
                    engineFailureCount++
                    engineFailureCountState.value = engineFailureCount
                    if (!recreateTrackForRender()) Thread.sleep(250)
                }
            } catch (e: Exception) {
                if (!rendering) break
                engineFailureCount++
                engineFailureCountState.value = engineFailureCount
                Log.e(TAG, "Falha no render do ASD: ${e.message}", e)
                if (!recreateTrackForRender()) {
                    try {
                        Thread.sleep(250)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
    }

    private fun recreateTrackForRender(): Boolean = try {
        synchronized(this) {
            // stop()/release() seguram este mesmo lock enquanto esperam a thread de render sair
            // (com timeout). Se chegamos aqui depois disso, recriar a faixa ressuscitaria um
            // motor que acabou de ser desligado — sai sem fazer nada.
            if (!rendering) return false
            try {
                track?.release()
            } catch (e: Exception) {
                // ignora
            }
            track = null
            createTrack()
            track?.play()
        }
        true
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao recriar a faixa do ASD: ${e.message}", e)
        false
    }

    /**
     * Produz um bloco de 5 ms. Todo o trabalho de áudio vive aqui: gera a fonte do perfil ativo
     * (camadas de combustão em crossfade ou o zumbido elétrico sintetizado), aplica o passa-baixa
     * do tom, o abafamento, o portão de entrada/saída e a posição espacial.
     *
     * Ganho, passo de leitura e frequência são interpolados ao longo do bloco, do valor do bloco
     * anterior até o novo alvo: sem isso, um degrau a cada bloco voltaria a produzir exatamente o
     * tipo de descontinuidade que este redesenho existe para eliminar.
     */
    private fun renderBlock(out: ShortArray) {
        val rpm = max(400.0, targetRpm)
        val load = targetLoad.coerceIn(-1.0, 1.0)
        val norm = load * 0.5 + 0.5 // 0..1

        val duck = if (duckedByOther) DUCK_VOLUME_FACTOR else 1.0
        // Volume mestre efetivo: no modo FIXED é o slider constante; no modo RPM é interpolado
        // linearmente entre o ponto de RPM baixas e o de RPM altas conforme o RPM atual.
        val effectiveMaster = when (volumeMode) {
            AsdVolumeMode.FIXED -> masterVolume.toDouble()
            AsdVolumeMode.RPM -> {
                val f = ((rpm - RPM_VOL_MIN) / (RPM_VOL_MAX - RPM_VOL_MIN)).coerceIn(0.0, 1.0)
                rpmVolLow + f * (rpmVolHigh - rpmVolLow)
            }
        }
        // Durante o backoff por perda de foco a saída é silenciada, mas a thread de render
        // continua alimentando a faixa: parar de escrever deixaria o HAL sem dados (underrun) e
        // voltar depois exigiria religar tudo. Silêncio contínuo é mais seguro que buraco.
        val amp = if (backingOff) 0.0 else effectiveMaster * duck

        // Portão. Uma troca de perfil pedida fecha o portão rápido, troca a fonte no silêncio e
        // reabre — trocar a fonte com som saindo seria um degrau na onda.
        val trocandoPerfil = perfilPedido != perfilAtivo
        val alvoDoPortao = if (trocandoPerfil) 0.0 else gateAlvo
        val gStart = gate
        val duracaoDoFade = when {
            trocandoPerfil -> FADE_TROCA_DE_PERFIL_S
            alvoDoPortao > gStart -> FADE_IN_S
            else -> FADE_OUT_S
        }
        val passoDoPortao = BLOCK_FRAMES.toDouble() / OUT_SAMPLE_RATE / duracaoDoFade
        val gEnd = if (alvoDoPortao > gStart) min(alvoDoPortao, gStart + passoDoPortao)
        else max(alvoDoPortao, gStart - passoDoPortao)

        val eletrico = perfilAtivo == AsdPerfilDeSom.ELETRICO
        if (eletrico) prepararEletrico(rpm, load, amp) else prepararCombustao(rpm, norm, amp)

        val cutoffMin = if (eletrico) TONE_CUTOFF_MIN_ELETRICO_HZ else TONE_CUTOFF_MIN_HZ
        val cutoff = cutoffMin + norm * (TONE_CUTOFF_MAX_HZ - cutoffMin)
        val aTarget = 1.0 - exp(-2.0 * Math.PI * cutoff / OUT_SAMPLE_RATE)
        val aStart = lpA
        val invBlock = 1.0 / BLOCK_FRAMES

        // Posição espacial (constante no bloco; o usuário arrasta devagar, sem zipper audível).
        // Balanço estéreo: no centro os dois canais ficam em ganho 1.0; ao ir para um lado, o
        // canal oposto atenua. Frente/trás: ir para trás abaixa o volume e soma no muffle abaixo.
        val gainL = if (posX <= 0f) 1.0 else (1.0 - posX).toDouble()
        val gainR = if (posX >= 0f) 1.0 else (1.0 + posX).toDouble()
        val posYGain = 1.0 + min(0.0, posY.toDouble()) * 0.4 // frente/centro=1.0, trás(-1)=0.6

        // Muffle efetivo = abafamento do perfil somado à contribuição de "estar atrás".
        val backMuffle = if (posY < 0f) (-posY).toDouble() * 0.5 else 0.0
        val muffleDoPerfil = when {
            eletrico -> MUFFLE_ELETRICO
            muffleEnabled -> muffleAmount.toDouble()
            else -> 0.0
        }
        val totalMuffle = (muffleDoPerfil + backMuffle).coerceIn(0.0, 1.0)
        val applyMuffle = totalMuffle > 0.001
        val muffleCutoff = MUFFLE_CUTOFF_OPEN_HZ + totalMuffle * (MUFFLE_CUTOFF_CLOSED_HZ - MUFFLE_CUTOFF_OPEN_HZ)
        val muffleA = 1.0 - exp(-2.0 * Math.PI * muffleCutoff / OUT_SAMPLE_RATE)

        for (n in 0 until BLOCK_FRAMES) {
            val t = n * invBlock
            val acc = if (eletrico) amostraEletrica(t) else amostraCombustao(t)
            val a = aStart + (aTarget - aStart) * t
            lpState += a * (acc - lpState)
            var s = lpState
            if (applyMuffle) {
                muffleState += muffleA * (s - muffleState)
                s = muffleState
            }
            s *= gStart + (gEnd - gStart) * t
            var l = s * posYGain * gainL
            var r = s * posYGain * gainR
            if (l > 32767.0) l = 32767.0 else if (l < -32768.0) l = -32768.0
            if (r > 32767.0) r = 32767.0 else if (r < -32768.0) r = -32768.0
            val o = n * 2
            out[o] = l.toInt().toShort()
            out[o + 1] = r.toInt().toShort()
        }

        if (eletrico) {
            for (i in ganhosEletrico.indices) ganhosEletrico[i] = alvosEletrico[i]
            freqEletrico = freqEletricoAlvo
        } else {
            for (i in voices.indices) {
                voices[i].gain = targetGain[i]
                voices[i].inc = targetInc[i]
            }
        }
        lpA = aTarget
        gate = gEnd

        if (trocandoPerfil && gEnd <= 1e-4) {
            // Troca no silêncio: a fonte nova começa com ganho zero e sobe pela rampa normal.
            perfilAtivo = perfilPedido
            for (v in voices) v.gain = 0.0
            ganhosEletrico.fill(0.0)
        }
    }

    /**
     * Alvos das camadas de combustão para este bloco: as duas amostras de RPM vizinhas, com
     * crossfade de potência constante ([AsdDsp.ganhoBaixo]/[AsdDsp.ganhoAlto]), e o passo de
     * leitura que afina cada uma no RPM alvo, vezes o tom escolhido.
     */
    private fun prepararCombustao(rpm: Double, norm: Double, amp: Double) {
        var lowIdx = 0
        for (i in 0 until voices.size - 1) {
            if (rpm >= voices[i].rpm) lowIdx = i
        }
        var highIdx = min(lowIdx + 1, voices.size - 1)
        if (rpm >= voices[voices.size - 1].rpm) {
            lowIdx = voices.size - 1
            highIdx = lowIdx
        }
        if (rpm <= voices[0].rpm) {
            lowIdx = 0
            highIdx = 0
        }

        var mix = 0.0
        if (highIdx != lowIdx) {
            mix = ((rpm - voices[lowIdx].rpm) / (voices[highIdx].rpm - voices[lowIdx].rpm)).coerceIn(0.0, 1.0)
        }

        val loadVolume = (0.55 + norm * 0.5).coerceIn(0.0, 1.0)
        val tom = tomFator
        for (i in voices.indices) {
            val v = voices[i]
            var g = 0.0
            if (i == lowIdx) g = AsdDsp.ganhoBaixo(mix)
            if (i == highIdx) g = max(g, AsdDsp.ganhoAlto(mix))
            targetGain[i] = g * loadVolume * amp
            targetInc[i] = if (g > 0.0) {
                (rpm / v.rpm).coerceIn(PITCH_MIN, PITCH_MAX) * tom * v.srRatio
            } else {
                v.inc
            }
        }
    }

    private fun amostraCombustao(t: Double): Double {
        var acc = 0.0
        for (i in voices.indices) {
            val v = voices[i]
            val g = v.gain + (targetGain[i] - v.gain) * t
            val inc = v.inc + (targetInc[i] - v.inc) * t
            val pcm = v.pcm
            val len = pcm.size
            if (g > 1e-6 && len > 3) {
                val idx = v.phase.toInt()
                val frac = v.phase - idx
                val ym1 = pcm[if (idx == 0) len - 1 else idx - 1].toDouble()
                val y0 = pcm[idx].toDouble()
                val i1 = if (idx + 1 >= len) idx + 1 - len else idx + 1
                val i2 = if (idx + 2 >= len) idx + 2 - len else idx + 2
                acc += AsdDsp.hermite(ym1, y0, pcm[i1].toDouble(), pcm[i2].toDouble(), frac) * g
            }
            var p = v.phase + inc
            if (len > 0) {
                while (p >= len) p -= len
            }
            v.phase = p
        }
        return acc
    }

    /**
     * Alvos do zumbido elétrico para este bloco. É síntese aditiva: seis senoides em múltiplos
     * de uma fundamental que sobe com a velocidade ([AsdDsp.frequenciaDoZumbido]).
     *
     *  - 0,5×: corpo grave, o "peso" do carro;
     *  - 1× e 1,006×: a fundamental e uma cópia desafinada — o batimento lento entre as duas é o
     *    que tira o som de "tom de teste" e o deixa vivo;
     *  - 2× e 3×: brilho, que abre com a carga (pé no acelerador);
     *  - 8,5×: o assobio de inversor, fraco e só em velocidade, que é a assinatura de elétrico.
     *
     * Regeneração (carga negativa) baixa o nível: o carro "respira" ao aliviar.
     */
    private fun prepararEletrico(rpm: Double, load: Double, amp: Double) {
        val f0Base = AsdDsp.frequenciaDoRpmEquivalente(rpm)
        val f0 = f0Base * tomFator
        val nVel = ((f0Base - AsdDsp.ZUMBIDO_HZ_PARADO) /
            (AsdDsp.ZUMBIDO_HZ_MAXIMO - AsdDsp.ZUMBIDO_HZ_PARADO)).coerceIn(0.0, 1.0)
        val pos = max(load, 0.0)
        val neg = max(-load, 0.0)
        val nivel = (0.30 + 0.45 * nVel + 0.35 * pos - 0.15 * neg).coerceIn(0.12, 1.0)
        val escala = nivel * amp * NIVEL_ELETRICO
        val base0 = 0.30
        val base1 = 1.0
        val base2 = 0.45
        val base3 = 0.40 + 0.15 * pos
        val base4 = 0.12 + 0.30 * pos
        val base5 = (0.02 + 0.08 * pos) * (0.3 + 0.7 * nVel)
        alvosEletrico[0] = base0 * escala
        alvosEletrico[1] = base1 * escala
        alvosEletrico[2] = base2 * escala
        alvosEletrico[3] = base3 * escala
        alvosEletrico[4] = base4 * escala
        alvosEletrico[5] = base5 * escala
        // Nenhuma parcial passa de 16 kHz: acima disso ela só viraria aliasing.
        for (i in RAZOES_ELETRICO.indices) {
            if (f0 * RAZOES_ELETRICO[i] > 16000.0) alvosEletrico[i] = 0.0
        }
        freqEletricoAlvo = f0
    }

    private fun amostraEletrica(t: Double): Double {
        val f = freqEletrico + (freqEletricoAlvo - freqEletrico) * t
        faseLfo += 0.23 / OUT_SAMPLE_RATE
        if (faseLfo >= 1.0) faseLfo -= 1.0
        val tremolo = 1.0 + 0.04 * seno(faseLfo)
        var acc = 0.0
        for (i in RAZOES_ELETRICO.indices) {
            val g = ganhosEletrico[i] + (alvosEletrico[i] - ganhosEletrico[i]) * t
            var fase = fasesEletrico[i] + f * RAZOES_ELETRICO[i] / OUT_SAMPLE_RATE
            if (fase >= 1.0) fase -= fase.toInt()
            fasesEletrico[i] = fase
            if (g > 1e-6) acc += seno(fase) * g
        }
        return acc * tremolo
    }

    /** Seno por tabela com interpolação linear; [ciclos] em 0..1. Erro ~1e-7, sem Math.sin por amostra. */
    private fun seno(ciclos: Double): Double {
        val pos = ciclos * TABELA_SENO_TAM
        val idx = pos.toInt()
        val fr = pos - idx
        val a = TABELA_SENO[idx]
        return a + (TABELA_SENO[idx + 1] - a) * fr
    }

    private fun appendDump(block: ShortArray) {
        val buf = dumpBuf ?: return
        val n = min(block.size, buf.size - dumpPos)
        if (n > 0) {
            System.arraycopy(block, 0, buf, dumpPos, n)
            dumpPos += n
        }
        // Grava assim que enche, sem esperar um stop() que pode nunca vir (o "Testar ASD" não
        // desliga o motor quando ele já estava ligado). O arquivo sai numa thread à parte: 5MB de
        // I/O na thread de render seria exatamente o tipo de pausa que estamos tentando medir.
        if (dumpPos >= buf.size) flushDump()
    }

    private fun flushDump() {
        val buf = dumpBuf ?: return
        val count = dumpPos
        dumpBuf = null
        dumpPos = 0
        if (count <= 0) return
        Thread({
            try {
                val dir = OpenGw.contexto.getExternalFilesDir(null) ?: return@Thread
                dir.mkdirs()
                val file = File(dir, "asd_dump.wav")
                writeWavPcm16(file, buf, count, OUT_SAMPLE_RATE, 2)
                Log.i(TAG, "Dump do ASD gravado: ${file.absolutePath} (${count / 2} frames)")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao gravar dump do ASD: ${e.message}", e)
            }
        }, "AsdDumpWriter").start()
    }

    // Com USAGE_MEDIA, algumas centrais só abrem o caminho de áudio para o alto-falante quando
    // existe uma concessão de foco ativa para esse uso — sem isso a faixa toca normalmente do
    // ponto de vista do AudioTrack, mas o DSP/amplificador do carro pode simplesmente não abrir o
    // canal. AUDIOFOCUS_GAIN (não-transiente) em vez de GAIN_TRANSIENT: o motor do ASD tem que
    // tocar continuamente enquanto o carro está ligado, não é um som pontual — uma concessão
    // transiente é o tipo de coisa que essa central parece revogar sozinha depois de pouco tempo.
    private fun requestAudioFocus() {
        try {
            val audioManager = OpenGw.contexto.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(currentAttrs())
                // Em zonas de áudio automotivas a concessão pode ser assíncrona
                // (AUDIOFOCUS_REQUEST_DELAYED) em vez de síncrona — aceitar isso evita que o
                // pedido seja recusado de cara quando a central ainda está resolvendo outra
                // disputa de foco; o AUDIOFOCUS_GAIN chega depois pelo listener.
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(
                    focusChangeListener,
                    android.os.Handler(android.os.Looper.getMainLooper())
                )
                .build()
            val result = audioManager.requestAudioFocus(request)
            focusRequest = request
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.w(TAG, "requestAudioFocus() não concedeu na hora (resultado $result)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus for ASD: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            val audioManager = OpenGw.contexto.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
            // ignora
        }
        focusRequest = null
    }

    @Synchronized
    fun startOutputTest() {
        if (testTonePlaying) return
        try {
            val audioManager = OpenGw.contexto.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            val attrs = currentAttrs()
            val framesPerCycle = OUT_SAMPLE_RATE / TEST_TONE_HZ
            val cycles = 200 // buffer de ~0.45s com número inteiro de ciclos, sem estalo no loop
            val totalFrames = (framesPerCycle * cycles).roundToInt()
            val pcm = ShortArray(totalFrames * 2) // estéreo
            for (i in 0 until totalFrames) {
                val angle = 2.0 * Math.PI * i / framesPerCycle
                val sample = (kotlin.math.sin(angle) * Short.MAX_VALUE * 0.5).toInt().toShort()
                pcm[i * 2] = sample
                pcm[i * 2 + 1] = sample
            }
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setSampleRate(OUT_SAMPLE_RATE)
                .build()
            val toneTrack = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            toneTrack.write(pcm, 0, pcm.size)
            toneTrack.setLoopPoints(0, totalFrames, -1)
            toneTrack.setVolume(max(masterVolume, 0.3f))
            testToneTrack = toneTrack

            // Só pede foco próprio se o motor já não estiver segurando um: dois pedidos GAIN no
            // mesmo canal, vindos do mesmo processo, fazem o CarAudioFocus expulsar o primeiro —
            // o motor perdia foco e entrava em backoff só por causa do teste, poluindo o
            // diagnóstico que o teste deveria esclarecer.
            if (!playing) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(
                        { /* diagnóstico simples, não precisa reagir a mudanças de foco */ },
                        android.os.Handler(android.os.Looper.getMainLooper())
                    )
                    .build()
                audioManager.requestAudioFocus(request)
                testToneFocusRequest = request
            }

            toneTrack.play()
            testTonePlaying = true
            testTonePlayingState.value = true
            testTonePlayStateError.value = if (toneTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                "play() não colocou o tom de teste em PLAYING (estado ${toneTrack.playState})"
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start output test tone: ${e.message}", e)
            testTonePlayStateError.value = "${e.javaClass.simpleName}: ${e.message}"
            testTonePlaying = false
            testTonePlayingState.value = false
        }
    }

    @Synchronized
    fun stopOutputTest() {
        testTonePlaying = false
        testTonePlayingState.value = false
        try {
            testToneTrack?.stop()
            testToneTrack?.release()
        } catch (e: Exception) {
            // já pode estar parado
        }
        testToneTrack = null
        try {
            val audioManager = OpenGw.contexto.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
            testToneFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
            // ignora
        }
        testToneFocusRequest = null
    }

    @Synchronized
    fun release() {
        playing = false
        stopRendering()
        try {
            track?.release()
        } catch (e: Exception) {
            // ignora
        }
        track = null
        audioSessionId = NO_AUDIO_SESSION
        loaded = false
        isLoaded.value = false
    }

    private data class WavPcm(val sampleRate: Int, val channels: Int, val pcm: ShortArray)

    private fun readWavPcm16(rawResId: Int): WavPcm {
        val bytes = OpenGw.contexto.resources.openRawResource(rawResId).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.int == 0x46464952) { "not a RIFF file" } // "RIFF"
        buf.int // chunk size total, não usado
        require(buf.int == 0x45564157) { "not a WAVE file" } // "WAVE"

        var sampleRate = 48000
        var channels = 1
        var pcm: ShortArray? = null

        while (buf.remaining() >= 8) {
            val chunkId = buf.int
            val chunkSize = buf.int
            val chunkStart = buf.position()
            when (chunkId) {
                0x20746d66 -> { // "fmt "
                    buf.short // audioFormat
                    channels = buf.short.toInt()
                    sampleRate = buf.int
                    buf.int // byteRate
                    buf.short // blockAlign
                    buf.short // bitsPerSample (assumido 16)
                }
                0x61746164 -> { // "data"
                    val n = chunkSize / 2
                    val arr = ShortArray(n)
                    for (i in 0 until n) arr[i] = buf.short
                    pcm = arr
                }
            }
            val next = chunkStart + chunkSize + (chunkSize and 1)
            if (next > buf.limit() || next < chunkStart) break
            buf.position(next)
        }

        return WavPcm(sampleRate, channels, pcm ?: ShortArray(0))
    }

    private fun writeWavPcm16(file: File, pcm: ShortArray, count: Int, sampleRate: Int, channels: Int) {
        val dataBytes = count * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(0x46464952) // "RIFF"
        header.putInt(36 + dataBytes)
        header.putInt(0x45564157) // "WAVE"
        header.putInt(0x20746d66) // "fmt "
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * 2)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.putInt(0x61746164) // "data"
        header.putInt(dataBytes)

        val body = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) body.putShort(pcm[i])

        file.outputStream().use {
            it.write(header.array())
            it.write(body.array())
        }
    }
}
