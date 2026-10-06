package br.com.deepgwt.tela3d.midia

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** As 24 barras de um quadro de FFT e as estatísticas que escolhem a fonte das barras. */
internal class QuadroDeFft(
    val barras: FloatArray,
    val media: Float,
    val espalhamento: Float,
    /** Maior magnitude individual de bin (antes de modelar). */
    val pico: Float,
)

/** As 24 barras de um quadro de forma de onda e o RMS global. */
internal class QuadroDeOnda(val barras: FloatArray, val rms: Float)

/** Conta do visualizador (caso L3), pura e testável: bytes de captura → 24 barras. */
internal object AnalisadorDeAudio {
    const val BARRAS = 24
    private const val EXPOENTE_DAS_FAIXAS = 1.55
    private const val TETO_DA_BARRA = 0.78f

    /** Limites dos bins das 24 faixas (25 valores; o último é o fim da última faixa) para [n] pares úteis. */
    fun limitesDasFaixas(n: Int): IntArray = IntArray(BARRAS + 1) { i ->
        floor((i / BARRAS.toDouble()).pow(EXPOENTE_DAS_FAIXAS) * (n - 1)).toInt() + 1
    }

    /** x ≤ 0 vira 0; senão min(x, 1) elevado ao expoente. */
    fun modelar(x: Double, expoente: Double): Double = if (x <= 0.0) 0.0 else min(x, 1.0).pow(expoente)

    private fun limitar(x: Double): Float = x.coerceIn(0.0, TETO_DA_BARRA.toDouble()).toFloat()

    /**
     * FFT do `Visualizer` (bytes intercalados real/imaginário; os dois primeiros são DC/Nyquist e não
     * entram). Nulo se há menos de 24 pares úteis.
     */
    fun fft(bytes: ByteArray): QuadroDeFft? {
        val n = bytes.size / 2 - 1
        if (n < BARRAS) return null
        val limites = limitesDasFaixas(n)
        val barras = FloatArray(BARRAS)
        var pico = 0.0
        for (i in 0 until BARRAS) {
            val inicio = limites[i]
            var fim = limites[i + 1]
            if (fim <= inicio) fim = inicio + 1
            fim = min(fim, n)
            var soma = 0.0
            var maximo = 0.0
            var quantos = 0
            for (k in inicio until fim) {
                val m = hypot(bytes[2 * k].toDouble(), bytes[2 * k + 1].toDouble()) / 128.0
                soma += m
                if (m > maximo) maximo = m
                quantos++
            }
            val media = if (quantos > 0) soma / quantos else 0.0
            pico = max(pico, maximo)
            barras[i] = limitar(modelar(1.6 * (0.72 * media + 0.28 * maximo), 0.62))
        }
        var soma = 0f
        var menor = Float.MAX_VALUE
        var maior = 0f
        for (b in barras) {
            soma += b
            menor = min(menor, b)
            maior = max(maior, b)
        }
        return QuadroDeFft(barras, soma / BARRAS, maior - menor, pico.toFloat())
    }

    /**
     * Forma de onda (bytes sem sinal centrados em 128). Nulo se há menos de 24 amostras. O RMS global
     * soma os blocos das barras e divide pelo total de amostras.
     */
    fun onda(bytes: ByteArray): QuadroDeOnda? {
        val total = bytes.size
        if (total < BARRAS) return null
        val bloco = max(1, total / BARRAS)
        val barras = FloatArray(BARRAS)
        var somaGlobal = 0.0
        for (i in 0 until BARRAS) {
            var somaDoBloco = 0.0
            val inicio = i * bloco
            val fim = min(inicio + bloco, total)
            for (j in inicio until fim) {
                val v = abs128(bytes[j])
                somaDoBloco += v * v
            }
            somaGlobal += somaDoBloco
            val rms = sqrt(somaDoBloco / bloco)
            val inclinacao = 0.72 * (1.0 - i / (BARRAS - 1).toDouble()) + 0.28
            barras[i] = limitar(modelar(1.35 * rms, 0.7) * inclinacao)
        }
        return QuadroDeOnda(barras, sqrt(somaGlobal / total).toFloat())
    }

    private fun abs128(b: Byte): Double = kotlin.math.abs((b.toInt() and 0xFF) - 128) / 128.0
}

/**
 * Mistura os quadros de FFT e de onda nas 24 barras entregues ao cartão e controla o "áudio ao
 * vivo". Thread-safe: os quadros chegam em uma thread e os níveis são lidos em outra.
 */
internal class MisturadorDeBarras(private val agoraMs: () -> Long) {
    private val trava = Any()
    private var barrasFft = FloatArray(AnalisadorDeAudio.BARRAS)
    private var barrasOnda = FloatArray(AnalisadorDeAudio.BARRAS)
    private val niveis = FloatArray(AnalisadorDeAudio.BARRAS)
    private var mediaFft = 0f
    private var espalhamentoFft = 0f
    private var ultimoNaoSilencioso: Long? = null
    private var quadros = 0
    private var fonteDasBarras = "onda"

    /** Quadros processados desde o início; usado no diagnóstico. */
    val totalDeQuadros: Int get() = synchronized(trava) { quadros }

    val fonteAtual: String get() = synchronized(trava) { fonteDasBarras }

    /** Devolve falso se o quadro foi ignorado (poucos bins). */
    fun aoQuadroDeFft(bytes: ByteArray): Boolean {
        val quadro = AnalisadorDeAudio.fft(bytes) ?: return false
        synchronized(trava) {
            barrasFft = quadro.barras
            mediaFft = quadro.media
            espalhamentoFft = quadro.espalhamento
            if (quadro.pico >= LIMIAR_DE_SILENCIO) ultimoNaoSilencioso = agoraMs()
            quadros++
            misturar()
        }
        return true
    }

    fun aoQuadroDeOnda(bytes: ByteArray): Boolean {
        val quadro = AnalisadorDeAudio.onda(bytes) ?: return false
        synchronized(trava) {
            barrasOnda = quadro.barras
            if (quadro.rms >= LIMIAR_DE_SILENCIO) ultimoNaoSilencioso = agoraMs()
            quadros++
            misturar()
        }
        return true
    }

    private fun misturar() {
        val usarFft = mediaFft >= MEDIA_MINIMA_DA_FFT && espalhamentoFft >= ESPALHAMENTO_MINIMO_DA_FFT
        fonteDasBarras = if (usarFft) "fft" else "onda"
        (if (usarFft) barrasFft else barrasOnda).copyInto(niveis)
    }

    /** Houve quadro não silencioso há menos de 1,5 s. */
    fun temAudioAoVivo(): Boolean = synchronized(trava) {
        val ultimo = ultimoNaoSilencioso ?: return false
        agoraMs() - ultimo < JANELA_DE_AUDIO_AO_VIVO_MS
    }

    /** Copia as 24 barras se há áudio ao vivo e o destino cabe; senão devolve falso. */
    fun copiarNiveis(destino: FloatArray): Boolean = synchronized(trava) {
        if (destino.size < AnalisadorDeAudio.BARRAS) return false
        val ultimo = ultimoNaoSilencioso ?: return false
        if (agoraMs() - ultimo >= JANELA_DE_AUDIO_AO_VIVO_MS) return false
        niveis.copyInto(destino, 0, 0, AnalisadorDeAudio.BARRAS)
        true
    }

    fun zerar() = synchronized(trava) {
        barrasFft = FloatArray(AnalisadorDeAudio.BARRAS)
        barrasOnda = FloatArray(AnalisadorDeAudio.BARRAS)
        niveis.fill(0f)
        mediaFft = 0f
        espalhamentoFft = 0f
        ultimoNaoSilencioso = null
        quadros = 0
    }

    companion object {
        const val MEDIA_MINIMA_DA_FFT = 0.06f
        const val ESPALHAMENTO_MINIMO_DA_FFT = 0.04f
        const val LIMIAR_DE_SILENCIO = 0.02f
        const val JANELA_DE_AUDIO_AO_VIVO_MS = 1_500L
    }
}
