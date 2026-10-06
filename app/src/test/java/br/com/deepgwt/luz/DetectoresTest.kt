// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * V14 e V15 (padrão de batidas exato; nível ±0,02; escore/proporção ±0,0005) e V16 (exato).
 */
class DetectoresTest {

    private val tolNivel = 0.02

    /** FFT de 64 bytes com os [pares] primeiros pares = (re, im), o resto zero. */
    private fun fft(re: Int, im: Int, pares: Int = 10): ByteArray {
        val b = ByteArray(64)
        for (i in 0 until pares) {
            b[2 + 2 * i] = re.toByte()
            b[3 + 2 * i] = im.toByte()
        }
        return b
    }

    @Test
    fun `V14 sequencia num mesmo detector`() {
        val m = listOf(0, 0, 60, 60, 20, 60, 100, 100, 10, 100, 127, 30, 64)
        val niveis = listOf(0.0, 0.0, 0.4688, 0.4688, 0.1563, 0.4688, 0.7813, 0.7813, 0.0781, 0.7813, 0.9922, 0.2344, 0.5000)
        val batidas = listOf(false, false, true, false, false, true, false, false, false, true, false, false, true)
        val d = DetectorDigital()
        m.forEachIndexed { i, v ->
            val r = d.processar(fft(v, 0))
            assertEquals("nível no quadro $i", niveis[i], r.nivel, tolNivel)
            assertEquals("batida no quadro $i", batidas[i], r.batida)
        }
    }

    @Test
    fun `V14 detectores novos`() {
        DetectorDigital().also { d ->
            val r1 = d.processar(fft(100, 80, pares = 6))
            assertEquals(0.6003, r1.nivel, tolNivel)
            assertTrue(r1.batida)
            val r2 = d.processar(fft(100, 80, pares = 6))
            assertEquals(0.6003, r2.nivel, tolNivel)
            assertFalse(r2.batida)
        }
        DetectorDigital().processar(fft(50, 40, pares = 6)).also {
            assertEquals(0.3001, it.nivel, tolNivel)
            assertFalse(it.batida)
        }
        DetectorDigital().processar(fft(-128, -128)).also {
            assertEquals(1.0, it.nivel, tolNivel)
            assertTrue(it.batida)
        }
        DetectorDigital().processar(ByteArray(3)).also {
            assertEquals(0.0, it.nivel, 0.0)
            assertFalse(it.batida)
        }
        DetectorDigital().processar(byteArrayOf(0, 0, 100, 0, 100, 0)).also {
            assertEquals(0.7813, it.nivel, tolNivel)
            assertTrue(it.batida)
        }
        DetectorDigital().processar(byteArrayOf(0, 0, 100, 0)).also {
            assertEquals(0.7813, it.nivel, tolNivel)
            assertTrue(it.batida)
        }
        DetectorDigital().processar(ByteArray(64)).also {
            assertEquals(0.0, it.nivel, 0.0)
            assertFalse(it.batida)
        }
    }

    /** ⌊sen(2π f i / 8000)·A⌋, 512 amostras. */
    private fun seno(f: Double, a: Double): ShortArray =
        ShortArray(512) { i -> floor(sin(2 * PI * f * i / 8000.0) * a).toInt().toShort() }

    private fun constante(v: Int) = ShortArray(512) { v.toShort() }

    @Test
    fun `V15 detector do microfone`() {
        data class Caso(val entrada: ShortArray, val nivel: Double, val batida: Boolean, val escore: Double, val proporcao: Double)
        val casos = listOf(
            Caso(seno(75.0, 4200.0), 1.0, true, 0.02810, 0.9604),
            Caso(seno(800.0, 6000.0), 0.0074, false, 0.00018, 0.1751),
            Caso(seno(60.0, 1000.0), 0.2562, false, 0.00615, 0.9617),
            Caso(seno(120.0, 8000.0), 1.0, true, 0.05704, 0.9637),
        )
        for ((i, c) in casos.withIndex()) {
            val r = DetectorDoMicrofone().processar(c.entrada)
            assertEquals("nível $i", c.nivel, r.nivel, tolNivel)
            assertEquals("batida $i", c.batida, r.batida)
            assertEquals("escore $i", c.escore, r.escore, 0.0005)
            assertEquals("proporção $i", c.proporcao, r.proporcao, 0.0005)
        }
        DetectorDoMicrofone().processar(constante(80)).also {
            assertEquals(0.0, it.nivel, 0.0)
            assertFalse(it.batida)
            assertEquals(0.0, it.proporcao, 0.0)
        }
    }

    @Test
    fun `V15 sequencia num mesmo detector`() {
        val d = DetectorDoMicrofone()
        val entradas = listOf(seno(75.0, 4200.0), seno(75.0, 4200.0), constante(80), seno(75.0, 4200.0))
        val batidas = listOf(true, false, false, true)
        val niveis = listOf(1.0, 1.0, 0.0, 1.0)
        entradas.forEachIndexed { i, e ->
            val r = d.processar(e)
            assertEquals("batida $i", batidas[i], r.batida)
            assertEquals("nível $i", niveis[i], r.nivel, tolNivel)
        }
    }

    @Test
    fun `janela vazia do microfone nao mexe no estado`() {
        val d = DetectorDoMicrofone()
        assertTrue(d.processar(seno(75.0, 4200.0)).batida)
        d.processar(ShortArray(0)).also {
            assertEquals(0.0, it.nivel, 0.0)
            assertFalse(it.batida)
        }
        // Continua desarmado: a mesma batida não conta de novo.
        assertFalse(d.processar(seno(75.0, 4200.0)).batida)
    }

    @Test
    fun `V16 leitor da central`() {
        fun confere(texto: String?, nivel: Double, batida: Boolean) {
            val r = LeitorDaCentral.ler(texto)
            assertEquals("nível de '$texto'", nivel, r.nivel, 1e-12)
            assertEquals("batida de '$texto'", batida, r.batida)
        }
        confere("{4,64,0,3,56,5,4}", 0.64, true)
        for (t in listOf("{0,0,0,0,0,0,0}", "", null, "abc", "{-5,-3,-1}")) confere(t, 0.0, false)
        confere("{150,-20,5}", 1.0, true)
        confere("{9,10,11}", 0.11, true)
        confere("{5,8,9,100}", 0.09, false)
        confere("{9}", 0.09, false)
        confere("10", 0.10, true)
        confere("a10b20c30", 0.30, true)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
