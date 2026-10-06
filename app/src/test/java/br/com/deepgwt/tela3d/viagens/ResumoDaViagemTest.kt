package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumoDaViagemTest {
    @Test
    fun `km por litro`() {
        assertPerto(14.285714, CalculosDoResumo.kmPorLitro(100.0, 7.0))
        assertPerto(2000.0, CalculosDoResumo.kmPorLitro(100.0, 0.05))
        assertTrue(CalculosDoResumo.kmPorLitro(100.0, 0.0499).isNaN())
    }

    @Test
    fun `kwh por 100 km`() {
        assertPerto(12.0, CalculosDoResumo.kwhPor100Km(15.0, 3.0, 100.0))
        assertTrue(CalculosDoResumo.kwhPor100Km(15.0, 3.0, 0.09).isNaN())
    }

    @Test
    fun `km eletrico e fracao`() {
        assertPerto(7.5, CalculosDoResumo.kmEletrico(3.0, 1.0, 10.0))
        assertPerto(0.75, CalculosDoResumo.fracaoEletrica(7.5, 10.0))
        assertPerto(0.0, CalculosDoResumo.kmEletrico(0.0, 0.0, 10.0))
        assertPerto(1.0, CalculosDoResumo.fracaoEletrica(20.0, 10.0))
        assertTrue(CalculosDoResumo.fracaoEletrica(0.05, 0.05).isNaN())
    }

    @Test
    fun `velocidade media`() {
        assertEquals(60.0, CalculosDoResumo.velocidadeMedia(1.0, 60_000), 1e-9)
        assertTrue(CalculosDoResumo.velocidadeMedia(1.0, 0).isNaN())
    }
}
