package br.com.opengw.luz

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Um quadro analisado por um detector de graves. [escore]/[proporcao] só existem no do microfone. */
internal class ResultadoDoDetector(
    val nivel: Double,
    val batida: Boolean,
    val escore: Double = nivel,
    val proporcao: Double = 0.0,
) {
    companion object {
        val SILENCIO = ResultadoDoDetector(0.0, false, 0.0, 0.0)
    }
}

/**
 * Batidas no FFT do `Visualizer` (áudio que sai do sistema). Caso 63.
 *
 * Bytes com sinal, pares (real, imaginário) a partir do byte 2. Nível = média dos módulos dos
 * primeiros até 10 pares ÷ 128. A batida exige o detector armado e uma subida clara (ou um salto
 * acima do piso); uma batida desarma até o nível voltar perto do piso.
 */
internal class DetectorDigital {

    private var armado = true
    private var anterior = 0.0
    private var piso = PISO_INICIAL

    fun processar(fft: ByteArray?): ResultadoDoDetector {
        val n = nivel(fft)
        val subida = n >= 1.22 * anterior && n >= anterior + 0.06
        val salto = anterior < 1.12 * piso && n >= 1.55 * piso
        val batida = armado && n >= 0.36 && (subida || salto)
        if (batida) {
            armado = false
        } else if (!armado && n <= max(1.10 * piso, 0.27)) {
            armado = true
        }
        val peso = if (batida) 0.04 else 0.08
        piso = (piso + (n - piso) * peso).coerceIn(0.05, 0.65)
        anterior = n
        return ResultadoDoDetector(n, batida)
    }

    companion object {
        const val PISO_INICIAL = 0.16

        fun nivel(fft: ByteArray?): Double {
            if (fft == null || fft.size < 4) return 0.0
            val pares = min(10, (fft.size - 2) / 2)
            var soma = 0.0
            for (i in 0 until pares) {
                val re = fft[2 + 2 * i].toDouble()
                val im = fft[3 + 2 * i].toDouble()
                soma += hypot(re, im)
            }
            return (soma / pares / 128.0).coerceIn(0.0, 1.0)
        }
    }
}

/**
 * Batidas no microfone (8 kHz, mono, 16 bits). Caso 64.
 *
 * Mede a energia em seis frequências graves contra seis de referência (Goertzel na frequência
 * exata) e só aceita batida quando os graves dominam e sobem acima do piso.
 */
internal class DetectorDoMicrofone(private val taxa: Int = TAXA) {

    private var armado = true
    private var anterior = 0.0
    private var piso = PISO_INICIAL

    fun processar(amostras: ShortArray, quantidade: Int = amostras.size): ResultadoDoDetector {
        val n = min(quantidade, amostras.size)
        if (n <= 0) return ResultadoDoDetector.SILENCIO
        val x = DoubleArray(n) { amostras[it] / 32767.0 }
        var energia = 0.0
        for (v in x) energia += v * v
        val rms = sqrt(energia / n)
        if (rms < 0.006) {
            armado = true
            anterior = 0.0
            piso = (piso * 0.94 + 0.006 * 0.06).coerceIn(PISO_MINIMO, PISO_MAXIMO)
            return ResultadoDoDetector(0.0, false, 0.0, 0.0)
        }
        val graves = GRAVES.sumOf { magnitude(x, it) } / GRAVES.size
        val referencia = REFERENCIA.sumOf { magnitude(x, it) } / REFERENCIA.size
        val proporcao = graves / (graves + referencia + 1e-6)
        val escore = graves * proporcao
        val nivel = (escore / (2.4 * piso)).coerceIn(0.0, 1.0)
        val subida = escore >= 1.24 * anterior && escore >= anterior + 0.004
        val salto = anterior < 1.25 * piso && escore >= 1.65 * piso
        val batida = armado && escore >= 0.014 && proporcao >= 0.52 && nivel >= 0.30 && (subida || salto)
        if (batida) {
            armado = false
        } else if (!armado && escore <= max(1.35 * piso, 0.0098)) {
            armado = true
        }
        val peso = if (batida) 0.03 else 0.08
        piso = (piso + (escore - piso) * peso).coerceIn(PISO_MINIMO, PISO_MAXIMO)
        anterior = escore
        return ResultadoDoDetector(nivel, batida, escore, proporcao)
    }

    /** 2·√potência / n, limitada a 0..1. */
    private fun magnitude(x: DoubleArray, frequencia: Double): Double {
        val w = 2.0 * PI * frequencia / taxa
        val coef = 2.0 * cos(w)
        var s1 = 0.0
        var s2 = 0.0
        for (v in x) {
            val s0 = v + coef * s1 - s2
            s2 = s1
            s1 = s0
        }
        val potencia = max(0.0, s1 * s1 + s2 * s2 - coef * s1 * s2)
        return (2.0 * sqrt(potencia) / x.size).coerceIn(0.0, 1.0)
    }

    companion object {
        const val TAXA = 8_000
        const val PISO_INICIAL = 0.010
        const val PISO_MINIMO = 0.006
        const val PISO_MAXIMO = 0.5
        val GRAVES = doubleArrayOf(45.0, 60.0, 75.0, 95.0, 120.0, 155.0)
        val REFERENCIA = doubleArrayOf(260.0, 360.0, 520.0, 760.0, 1_100.0, 1_600.0)
    }
}

/**
 * O sinal de ritmo da luz original do carro (`sync_music_freq`, texto tipo `{4,64,0,3,56,5,4}`).
 * Caso 65: o maior dos 3 primeiros inteiros, limitado a 0..100, vira o nível.
 */
internal object LeitorDaCentral {

    private val INTEIRO = Regex("-?\\d+")

    fun ler(texto: String?): ResultadoDoDetector {
        if (texto.isNullOrEmpty()) return ResultadoDoDetector.SILENCIO
        val numeros = INTEIRO.findAll(texto).take(3).map { m ->
            m.value.toLongOrNull() ?: if (m.value.startsWith("-")) Long.MIN_VALUE else Long.MAX_VALUE
        }.toList()
        if (numeros.isEmpty()) return ResultadoDoDetector.SILENCIO
        val nivel = numeros.max().coerceIn(0L, 100L) / 100.0
        return ResultadoDoDetector(nivel, nivel >= 0.10)
    }
}

/** A cor que pisca na batida dos graves (caso 62). */
internal object CorDeAcento {

    fun calcular(base: CorLed, nivel: Double): CorLed {
        val intensidade = if (nivel.isNaN()) 0.35 else nivel.coerceIn(0.35, 1.0)
        val avermelhada = base.r >= 180 && base.g <= 80 && base.b <= 80
        val alvo = if (avermelhada) Cores.AMARELO else CorLed(255, 48, 0)
        return ContasDeCor.mistura(base, alvo, 0.45 + 0.45 * intensidade)
    }

    /** Cor-base da música (caso 54): azul suave sem modo conhecido, senão a cor do modo. */
    fun corBaseDaMusica(modo: ModoDeConducao): CorLed =
        if (modo == ModoDeConducao.DESCONHECIDO) Cores.AZUL_SUAVE else modo.cor
}
