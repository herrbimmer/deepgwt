package br.com.opengw.telas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvisoNoCirculoTest {

    @Test
    fun `aviso de pe com o carro parado so ocupa o circulo durante o pop-up`() {
        // O caso relatado: parado, cinto solto -> seat_belt_warning={1,0,0,0,0} a viagem inteira.
        val inicio = 10_000L
        val ate = inicio + AvisoNoCirculo.POPUP_MS
        assertTrue(AvisoNoCirculo.ocupa(avisoAtivo = true, agoraMs = inicio, popUpAteMs = ate, velocidadeKmh = 0f))
        assertTrue(AvisoNoCirculo.ocupa(true, ate - 1, ate, 0f))
        // Passado o pop-up, o círculo volta mesmo com o aviso de pé.
        assertFalse(AvisoNoCirculo.ocupa(true, ate, ate, 0f))
        assertFalse(AvisoNoCirculo.ocupa(true, ate + 60_000, ate, 0f))
        // Sem aviso nada ocupa, nem dentro da janela do pop-up.
        assertFalse(AvisoNoCirculo.ocupa(false, inicio, ate, 0f))
        assertFalse(AvisoNoCirculo.ocupa(false, inicio, ate, 80f))
    }

    @Test
    fun `andando o aviso ocupa o circulo enquanto durar`() {
        val passou = 1_000_000L
        assertTrue(AvisoNoCirculo.ocupa(true, passou, 0L, AvisoNoCirculo.ANDANDO_KMH))
        assertTrue(AvisoNoCirculo.ocupa(true, passou, 0L, 90f))
        // Manobra de garagem conta como parado.
        assertFalse(AvisoNoCirculo.ocupa(true, passou, 0L, AvisoNoCirculo.ANDANDO_KMH - 0.1f))
        assertTrue(AvisoNoCirculo.andando(5f))
        assertFalse(AvisoNoCirculo.andando(0f))
    }
}
