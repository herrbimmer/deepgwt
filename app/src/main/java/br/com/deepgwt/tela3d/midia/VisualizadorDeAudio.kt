package br.com.deepgwt.tela3d.midia

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.SystemClock

/**
 * Captura o áudio do mix de saída (sessão 0) e entrega 24 barras reais ao cartão de mídia
 * (caso L3). Precisa de `RECORD_AUDIO` e `MODIFY_AUDIO_SETTINGS`; a política que liga e desliga
 * está em [PoliticaDoVisualizador]. Use na thread principal.
 */
class VisualizadorDeAudio {
    companion object {
        const val BARRAS = AnalisadorDeAudio.BARRAS
        private const val TAMANHO_PREFERIDO = 1024
        private const val TAXA_MAXIMA_MILI_HZ = 15_000
        private const val INTERVALO_DO_DIAGNOSTICO_MS = 2_000L
    }

    private val mistura = MisturadorDeBarras(SystemClock::elapsedRealtime)
    private var visualizador: Visualizer? = null
    private var principal: Handler? = null
    private var aoChegar: Runnable? = null
    private var desejado = false
    private var falhouNestaVontade = false
    private var ultimoDiagnostico = 0L

    @Volatile
    private var ativo = false

    /** Quem é avisado (postado na thread principal) a cada quadro capturado; nulo desliga o aviso. */
    fun aoChegarQuadro(acao: Runnable?) {
        aoChegar = acao
    }

    /** Ativa ou desativa a captura. Idempotente; não repete uma criação que falhou até o desejo passar por falso. */
    fun desejar(sim: Boolean) {
        if (sim) {
            desejado = true
            if (visualizador == null && !falhouNestaVontade) ligar()
        } else {
            desejado = false
            falhouNestaVontade = false
            desligar()
        }
    }

    fun temAudioAoVivo(): Boolean = ativo && mistura.temAudioAoVivo()

    /** Copia as 24 barras para [destino] se há áudio ao vivo; senão devolve falso e não mexe nele. */
    fun copiarNiveis(destino: FloatArray): Boolean = ativo && mistura.copiarNiveis(destino)

    /** Desliga, solta o dispositivo de captura e esquece o aviso. */
    fun liberar() {
        desejado = false
        falhouNestaVontade = false
        aoChegar = null
        desligar()
    }

    private fun ligar() {
        try {
            val v = Visualizer(0)
            val faixa = Visualizer.getCaptureSizeRange()
            val minimo = faixa[0]
            val maximo = faixa[1]
            val tamanho = if (maximo > TAMANHO_PREFERIDO && minimo <= TAMANHO_PREFERIDO) TAMANHO_PREFERIDO else maximo
            v.captureSize = tamanho
            val taxa = minOf(Visualizer.getMaxCaptureRate(), TAXA_MAXIMA_MILI_HZ)
            val resultado = v.setDataCaptureListener(ouvinteDeCaptura, taxa, true, true)
            if (resultado != Visualizer.SUCCESS) {
                Diario.aviso("captura de áudio recusada (código $resultado)")
                v.release()
                falhouNestaVontade = true
                return
            }
            principal = Handler(android.os.Looper.getMainLooper())
            v.enabled = true
            visualizador = v
            ativo = true
            Diario.info("visualizador de áudio ligado (captura $tamanho, taxa $taxa)")
        } catch (e: Exception) {
            Diario.aviso("não foi possível ligar o visualizador de áudio", e)
            visualizador = null
            ativo = false
            falhouNestaVontade = true
        }
    }

    private fun desligar() {
        val v = visualizador ?: return
        visualizador = null
        ativo = false
        try {
            v.enabled = false
        } catch (_: Exception) {
        }
        try {
            v.setDataCaptureListener(null, 0, false, false)
        } catch (_: Exception) {
        }
        try {
            v.release()
        } catch (_: Exception) {
        }
        mistura.zerar()
        Diario.info("visualizador de áudio desligado")
    }

    private val ouvinteDeCaptura = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            if (waveform != null && mistura.aoQuadroDeOnda(waveform)) aoCapturar()
        }

        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (fft != null && mistura.aoQuadroDeFft(fft)) aoCapturar()
        }
    }

    private fun aoCapturar() {
        val acao = aoChegar
        val h = principal
        if (acao != null && h != null) h.post(acao)
        diagnosticar()
    }

    private fun diagnosticar() {
        val agora = SystemClock.elapsedRealtime()
        if (agora - ultimoDiagnostico < INTERVALO_DO_DIAGNOSTICO_MS) return
        ultimoDiagnostico = agora
        if (mistura.temAudioAoVivo()) {
            Diario.info("visualizador ao vivo (fonte ${mistura.fonteAtual}, quadros ${mistura.totalDeQuadros})")
        } else {
            Diario.info("visualizador silencioso")
        }
    }
}

/**
 * Política que liga e desliga o visualizador (casos L1 e L2): desejado quando o último payload diz
 * que está tocando ou o sistema diz que há música ativa, reavaliado a cada payload e a cada segundo
 * enquanto a tela está retomada.
 */
class PoliticaDoVisualizador(
    private val contexto: Context,
    private val principal: Handler,
    visualizador: VisualizadorDeAudio,
) {
    private val audio = contexto.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val nucleo = NucleoDaPoliticaDoVisualizador(
        alvo = { sim -> visualizador.desejar(sim) },
        temPermissao = {
            contexto.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        },
        musicaAtiva = { audio?.isMusicActive ?: false },
        registrar = { Diario.info(it) },
    )

    private val visualizadorDoLaco = visualizador
    private var emLaco = false

    private val laco = object : Runnable {
        override fun run() {
            if (!emLaco) return
            nucleo.avaliar()
            principal.postDelayed(this, INTERVALO_MS)
        }
    }

    /** A cada retomada da tela: reinicia o laço de 1 s sem duplicar. */
    fun aoRetomar() {
        principal.removeCallbacks(laco)
        emLaco = true
        nucleo.avaliar()
        principal.postDelayed(laco, INTERVALO_MS)
    }

    /** Ao destruir a tela: cancela o laço e libera o dispositivo de captura. */
    fun aoDestruir() {
        emLaco = false
        principal.removeCallbacks(laco)
        visualizadorDoLaco.liberar()
    }

    /** Chamado a cada payload e pelas barras. */
    fun aoMudarTocando(tocando: Boolean) {
        nucleo.definirTocando(tocando)
        nucleo.avaliar()
    }

    private companion object {
        const val INTERVALO_MS = 1_000L
    }
}
