package br.com.opengw.brilho

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quando perguntar o nome da cidade (`specs/brilho-automatico.md`, 6). */
class ControleDaCidadeTest {

    private val controle = ControleDaCidade()

    @Test
    fun `a primeira consulta pode`() {
        assertTrue(controle.comecar(-16.686, -49.264, 0))
    }

    @Test
    fun `no maximo uma consulta em andamento`() {
        assertTrue(controle.comecar(-16.686, -49.264, 0))

        assertFalse(controle.comecar(-23.55, -46.633, 1_000))
        controle.terminou()
        assertTrue(controle.comecar(-23.55, -46.633, 2_000))
    }

    @Test
    fun `mesma coordenada so depois de 5 minutos, mesmo se a consulta falhou`() {
        assertTrue(controle.comecar(-16.686, -49.264, 0))
        controle.terminou()

        assertFalse(controle.comecar(-16.686, -49.264, 5 * MIN - 1))
        assertTrue(controle.comecar(-16.686, -49.264, 5 * MIN))
    }

    @Test
    fun `mesma coordenada e comparada com 3 casas decimais`() {
        assertTrue(controle.comecar(-16.6861, -49.2639, 0))
        controle.terminou()

        assertFalse(controle.comecar(-16.6859, -49.2641, MIN))
        assertTrue(controle.comecar(-16.6871, -49.2641, MIN))
    }

    @Test
    fun `outra coordenada pode na hora`() {
        assertTrue(controle.comecar(-16.686, -49.264, 0))
        controle.terminou()

        assertTrue(controle.comecar(-16.700, -49.264, 1))
    }

    private companion object {
        const val MIN = 60_000L
    }
}
