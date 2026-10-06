// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O torque que o Painel Esportivo mostra.
 *
 * O defeito que trouxe esta classe: com `motor_speed = 0` (que é o que este carro publica), a conta
 * antiga P/ω não produzia número nenhum e o mostrador ficava num traço permanente. Os testes abaixo
 * cobrem o caso que importa — arrancada a partir do zero — e as duas propriedades que mantêm o
 * número honesto: não inventar aceleração a partir do ruído, e não somar resistência num carro
 * parado.
 */
class WheelTorqueEstimatorTest {

    /** Alimenta uma rampa de velocidade constante e devolve o estimador no fim dela. */
    private fun rampa(
        deKmh: Double,
        aceleracaoMs2: Double,
        segundos: Double,
        passoMs: Long = 100L
    ): WheelTorqueEstimator {
        val e = WheelTorqueEstimator()
        var t = 1_000L
        var v = deKmh / 3.6
        val passos = (segundos * 1000 / passoMs).toInt()
        repeat(passos) {
            e.sample((v * 3.6).toFloat(), t)
            v += aceleracaoMs2 * (passoMs / 1000.0)
            t += passoMs
        }
        return e
    }

    @Test
    fun `sem amostras nao ha estimativa`() {
        assertNull(WheelTorqueEstimator().torqueNm())
    }

    @Test
    fun `uma amostra so nao vira aceleracao`() {
        val e = WheelTorqueEstimator()
        e.sample(50f, 1_000L)
        assertNull("um ponto não define uma derivada", e.torqueNm())
    }

    @Test
    fun `duas amostras coladas nao viram aceleracao`() {
        // 40 ms de vão: o degrau de 0,1 km/h do sinal daria 0,7 m/s² de aceleração inventada.
        val e = WheelTorqueEstimator()
        e.sample(50.0f, 1_000L)
        e.sample(50.1f, 1_020L)
        e.sample(50.2f, 1_040L)
        assertNull("vão curto demais para derivar", e.torqueNm())
    }

    @Test
    fun `arrancada do zero produz torque, que e o caso que P sobre omega nao cobria`() {
        // Parado, ω = 0 e a conta antiga divide por zero. Aqui o que informa é m·a.
        val e = rampa(deKmh = 0.0, aceleracaoMs2 = 3.0, segundos = 2.0)
        val nm = e.torqueNm()
        assertNotNull("a arrancada tem que produzir número", nm)
        assertTrue("torque de arrancada deveria ser substancial, veio $nm", nm!! > 1500f)
    }

    @Test
    fun `velocidade constante cobra so o que se opoe ao movimento`() {
        // A 100 km/h (27,8 m/s) sem acelerar: arrasto = 0,5 x 1,20 x 0,86 x 27,8² = 398 N,
        // rolagem = 0,012 x 1900 x 9,81 = 224 N. Total 622 N x 0,365 m = 227 Nm.
        val e = rampa(deKmh = 100.0, aceleracaoMs2 = 0.0, segundos = 4.0)
        val nm = e.torqueNm()
        assertNotNull(nm)
        assertEquals(227f, nm!!, 25f)
    }

    @Test
    fun `parado de verdade nao cobra resistencia nenhuma`() {
        // Sem esta regra o mostrador marcaria ~82 Nm com o carro na vaga: a resistência ao
        // rolamento de um carro que não rola.
        val e = rampa(deKmh = 0.0, aceleracaoMs2 = 0.0, segundos = 2.0)
        assertEquals(0f, e.torqueNm()!!, 1f)
    }

    @Test
    fun `frenagem sai negativa em vez de virar torque`() {
        val e = rampa(deKmh = 80.0, aceleracaoMs2 = -4.0, segundos = 1.5)
        val nm = e.torqueNm()
        assertNotNull(nm)
        assertTrue("freando, a força resultante é contra o movimento; veio $nm", nm!! < 0f)
    }

    @Test
    fun `o ruido de uma casa decimal nao vira torque`() {
        // Velocidade parada em 50 km/h, mas o sinal oscilando no último dígito, como ele chega.
        val e = WheelTorqueEstimator()
        var t = 1_000L
        val serie = listOf(50.0f, 50.1f, 50.0f, 49.9f, 50.0f, 50.1f, 50.0f, 49.9f, 50.0f, 50.0f)
        repeat(4) { serie.forEach { v -> e.sample(v, t); t += 100L } }
        val nm = e.torqueNm()!!
        // O valor certo é só a resistência a 50 km/h (~110 Nm). O ruído não pode dobrar isso.
        assertTrue("ruído virou torque: $nm", nm in 40f..200f)
    }

    @Test
    fun `uma pausa longa nao vira degrau de aceleracao`() {
        // Tela fechada por 10 s e reaberta com o carro a 90: sem o descarte, a janela veria
        // 0 -> 90 km/h "instantâneo" e cuspiria dezenas de milhares de Nm.
        val e = rampa(deKmh = 0.0, aceleracaoMs2 = 0.0, segundos = 1.0)
        e.sample(90f, 60_000L)
        e.sample(90f, 60_100L)
        e.sample(90f, 60_200L)
        val nm = e.torqueNm()
        assertTrue("degrau de retomada virou torque: $nm", nm == null || nm < 400f)
    }

    @Test
    fun `leitura implausivel e descartada`() {
        val e = rampa(deKmh = 60.0, aceleracaoMs2 = 0.0, segundos = 2.0)
        val antes = e.torqueNm()!!
        e.sample(9_999f, 3_500L)
        e.sample(-5f, 3_600L)
        e.sample(Float.NaN, 3_700L)
        assertEquals("lixo do barramento moveu o ponteiro", antes, e.torqueNm()!!, 0.001f)
    }

    @Test
    fun `reset devolve o estimador ao estado inicial`() {
        val e = rampa(deKmh = 60.0, aceleracaoMs2 = 2.0, segundos = 2.0)
        assertNotNull(e.torqueNm())
        e.reset()
        assertNull(e.torqueNm())
    }

    @Test
    fun `a conta e a segunda lei, conferida na mao`() {
        // 1900 kg x 2 m/s² = 3800 N de inércia, mais arrasto e rolagem a 20 m/s
        // (0,5 x 1,2 x 0,86 x 400 = 206 N; 0,012 x 1900 x 9,81 = 224 N) = 4230 N.
        // 4230 x 0,365 = 1544 Nm.
        val e = WheelTorqueEstimator()
        assertEquals(1544.0, e.torqueDe(velocidadeMs = 20.0, aceleracaoMs2 = 2.0), 5.0)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
