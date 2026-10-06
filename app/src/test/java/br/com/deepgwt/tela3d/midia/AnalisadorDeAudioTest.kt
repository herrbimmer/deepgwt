package br.com.deepgwt.tela3d.midia

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V8: FFT e forma de onda do visualizador, mais o "áudio ao vivo". */
class AnalisadorDeAudioTest {
    private val tolerancia = 1e-3f

    private fun <T : Any> exige(valor: T?): T = requireNotNull(valor) { "esperava um valor" }

    private fun fftComTodosOsBins(real: Int, tamanho: Int = 1024): ByteArray {
        val b = ByteArray(tamanho)
        for (k in 1 until tamanho / 2) b[2 * k] = real.toByte()
        return b
    }

    private fun onda(valor: Int, tamanho: Int = 1024) = ByteArray(tamanho) { valor.toByte() }

    // --- limites das faixas ----------------------------------------------------------------------

    @Test
    fun `v8 limites das faixas para n 511`() {
        val esperado = intArrayOf(
            1, 4, 11, 21, 32, 45, 60, 76, 93, 112, 132, 153, 175, 198, 222, 247, 273, 299, 327, 356, 385, 415, 446, 478, 511,
        )
        assertArrayEquals(esperado, AnalisadorDeAudio.limitesDasFaixas(511))
    }

    // --- FFT -----------------------------------------------------------------------------------

    @Test
    fun `v8 fft com todos os bins iguais`() {
        val q = exige(AnalisadorDeAudio.fft(fftComTodosOsBins(20)))
        assertEquals(24, q.barras.size)
        for (b in q.barras) assertEquals(0.4234f, b, tolerancia)
        assertEquals(0.4234f, q.media, tolerancia)
        assertEquals(0.1562f, q.pico, tolerancia)
        assertEquals(0f, q.espalhamento, tolerancia)
    }

    @Test
    fun `v8 fft de todos os bins iguais usa a onda e nao a fft`() {
        var t = 0L
        val m = MisturadorDeBarras { t }
        m.aoQuadroDeFft(fftComTodosOsBins(20))
        m.aoQuadroDeOnda(onda(192))
        assertEquals("onda", m.fonteAtual) // espalhamento 0 < 0,04
        val saida = FloatArray(24)
        assertTrue(m.copiarNiveis(saida))
        assertEquals(0.7595f, saida[0], tolerancia) // barras da onda
    }

    @Test
    fun `v8 fft de um bin so`() {
        val bytes = ByteArray(1024)
        bytes[2 * 10] = 100
        val q = exige(AnalisadorDeAudio.fft(bytes))
        assertEquals(0.6332f, q.barras[1], tolerancia)
        for (i in 0 until 24) if (i != 1) assertEquals("barra $i", 0f, q.barras[i], 0f)
        assertEquals(0.0264f, q.media, tolerancia)
        assertEquals(0.7812f, q.pico, tolerancia)
        assertEquals(0.6332f, q.espalhamento, tolerancia)
    }

    @Test
    fun `v8 fft com media baixa usa a onda`() {
        var t = 0L
        val m = MisturadorDeBarras { t }
        val bytes = ByteArray(1024)
        bytes[2 * 10] = 100
        m.aoQuadroDeFft(bytes)
        m.aoQuadroDeOnda(onda(192))
        assertEquals("onda", m.fonteAtual) // média 0,0264 < 0,06
    }

    @Test
    fun `fft forte e espalhada usa a fft`() {
        var t = 0L
        val m = MisturadorDeBarras { t }
        // graves fortes (bins 1..40) e o resto fraco: média e espalhamento altos
        val bytes = ByteArray(1024)
        for (k in 1..40) bytes[2 * k] = 127
        m.aoQuadroDeOnda(onda(192))
        m.aoQuadroDeFft(bytes)
        assertEquals("fft", m.fonteAtual)
        val saida = FloatArray(24)
        assertTrue(m.copiarNiveis(saida))
        val q = exige(AnalisadorDeAudio.fft(bytes))
        assertArrayEquals(q.barras, saida, 0f)
    }

    @Test
    fun `fft usa bytes com sinal`() {
        val positivo = ByteArray(1024) { i -> if (i >= 2 && i % 2 == 0) 20 else 0 }
        val negativo = ByteArray(1024) { i -> if (i >= 2 && i % 2 == 0) (-20).toByte() else 0 }
        val a = exige(AnalisadorDeAudio.fft(positivo))
        val b = exige(AnalisadorDeAudio.fft(negativo))
        assertEquals(a.pico, b.pico, 0f)
    }

    @Test
    fun `v8 menos de 24 pares ou amostras o quadro e ignorado`() {
        assertNull(AnalisadorDeAudio.fft(ByteArray(2 * 24))) // n = 23
        assertNotNull(AnalisadorDeAudio.fft(ByteArray(2 * 25))) // n = 24
        assertNull(AnalisadorDeAudio.fft(ByteArray(0)))
        assertNull(AnalisadorDeAudio.onda(ByteArray(23)))
        assertNotNull(AnalisadorDeAudio.onda(ByteArray(24)))
    }

    // --- forma de onda ---------------------------------------------------------------------------

    @Test
    fun `v8 onda constante 192`() {
        val q = exige(AnalisadorDeAudio.onda(onda(192)))
        assertEquals(0.7595f, q.barras[0], tolerancia)
        assertEquals(0.7357f, q.barras[1], tolerancia)
        assertEquals(0.7119f, q.barras[2], tolerancia)
        assertEquals(0.4742f, q.barras[12], tolerancia)
        assertEquals(0.2127f, q.barras[23], tolerancia)
        assertEquals(0.4961f, q.rms, tolerancia)
        for (i in 1 until 24) assertTrue(q.barras[i] < q.barras[i - 1])
    }

    @Test
    fun `v8 onda em silencio`() {
        val q = exige(AnalisadorDeAudio.onda(onda(128)))
        for (b in q.barras) assertEquals(0f, b, 0f)
        assertEquals(0f, q.rms, 0f)
    }

    @Test
    fun `v8 onda alternando 178 e 78 em blocos de 8`() {
        val bytes = ByteArray(1024) { i -> if ((i / 8) % 2 == 0) 178.toByte() else 78.toByte() }
        val q = exige(AnalisadorDeAudio.onda(bytes))
        assertEquals(0.6389f, q.barras[0], tolerancia)
        assertEquals(0.6189f, q.barras[1], tolerancia)
        assertEquals(0.5989f, q.barras[2], tolerancia)
        assertEquals(0.2189f, q.barras[21], tolerancia)
        assertEquals(0.1989f, q.barras[22], tolerancia)
        assertEquals(0.1789f, q.barras[23], tolerancia)
        assertEquals(0.3876f, q.rms, tolerancia)
    }

    @Test
    fun `nenhuma barra passa do teto de 0,78`() {
        val q = exige(AnalisadorDeAudio.onda(onda(255)))
        for (b in q.barras) assertTrue(b <= 0.78f)
        val f = exige(AnalisadorDeAudio.fft(fftComTodosOsBins(127)))
        for (b in f.barras) assertTrue(b <= 0.78f)
    }

    // --- áudio ao vivo ---------------------------------------------------------------------------

    @Test
    fun `audio ao vivo dura 1,5 s depois do ultimo quadro nao silencioso`() {
        var t = 1_000L
        val m = MisturadorDeBarras { t }
        val saida = FloatArray(24)
        assertFalse(m.temAudioAoVivo())
        assertFalse(m.copiarNiveis(saida))
        m.aoQuadroDeOnda(onda(192))
        assertTrue(m.temAudioAoVivo())
        t += 1_499
        assertTrue(m.copiarNiveis(saida))
        t += 1
        assertFalse(m.temAudioAoVivo())
        assertFalse(m.copiarNiveis(saida))
    }

    @Test
    fun `quadro silencioso nao renova o audio ao vivo`() {
        var t = 0L
        val m = MisturadorDeBarras { t }
        m.aoQuadroDeOnda(onda(128)) // silêncio
        assertFalse(m.temAudioAoVivo())
        m.aoQuadroDeFft(ByteArray(1024)) // pico 0
        assertFalse(m.temAudioAoVivo())
    }

    @Test
    fun `copiar niveis exige destino com 24 posicoes e nao mexe nele se falha`() {
        var t = 0L
        val m = MisturadorDeBarras { t }
        m.aoQuadroDeOnda(onda(192))
        assertFalse(m.copiarNiveis(FloatArray(10)))
        val intacto = FloatArray(24) { 9f }
        t += 5_000
        assertFalse(m.copiarNiveis(intacto))
        assertTrue(intacto.all { it == 9f })
    }

    @Test
    fun `zerar esquece tudo`() {
        var t = 0L
        val m = MisturadorDeBarras { t }
        m.aoQuadroDeOnda(onda(192))
        m.zerar()
        assertFalse(m.temAudioAoVivo())
        assertEquals(0, m.totalDeQuadros)
    }

    @Test
    fun `modelar`() {
        assertEquals(0.0, AnalisadorDeAudio.modelar(-1.0, 0.5), 0.0)
        assertEquals(0.0, AnalisadorDeAudio.modelar(0.0, 0.5), 0.0)
        assertEquals(1.0, AnalisadorDeAudio.modelar(5.0, 0.5), 0.0)
        assertEquals(0.5, AnalisadorDeAudio.modelar(0.25, 0.5), 1e-9)
    }
}
