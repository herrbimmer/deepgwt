// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O que o Sports Display mostra, a partir do que o carro responde.
 *
 * Os três defeitos relatados depois do teste no veículo estão cobertos aqui: o -1001 kW com o carro
 * parado, a escala de potência que acabava em 160 kW e a escala de torque que acabava em 400 Nm.
 */
class SportsDisplayReadoutTest {

    // ── Potência ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `o -1001 medido no carro parado nao vira leitura`() {
        // Valores exatos da captura do veículo (knowledge/session_2026-09-15/raw_http/telemetry.json):
        // motor_power = -1001 e motor_speed = 0 com o carro parado, enquanto o pack respondia.
        assertNull(resolvePowerKw(packVolts = null, packAmps = null, reportedPowerKw = -1001f))
    }

    @Test
    fun `tensao e corrente do pack sao a fonte primaria`() {
        // 306,0 V x 1,0 A = 306 W = 0,306 kW — os mesmos números da captura.
        val kw = resolvePowerKw(packVolts = 306f, packAmps = 1f, reportedPowerKw = -1001f)
        assertNotNull(kw)
        assertEquals(0.306f, kw!!, 1e-4f)
    }

    @Test
    fun `regeneracao continua negativa em vez de virar zero`() {
        val kw = resolvePowerKw(packVolts = 306f, packAmps = -40f, reportedPowerKw = null)
        assertNotNull(kw)
        assertTrue("regeneração precisa aparecer como potência negativa", kw!! < 0f)
    }

    @Test
    fun `motor_power plausivel vale quando o pack nao responde`() {
        assertEquals(84f, resolvePowerKw(null, null, 84f)!!, 1e-4f)
    }

    @Test
    fun `pack sem tensao nao produz potencia zero falsa`() {
        // Tensão 0 é ausência de leitura, não um carro entregando 0 kW: cai para motor_power.
        assertEquals(84f, resolvePowerKw(packVolts = 0f, packAmps = 12f, reportedPowerKw = 84f)!!, 1e-4f)
        assertNull(resolvePowerKw(packVolts = 0f, packAmps = 12f, reportedPowerKw = -1001f))
    }

    // ── Torque estimado ─────────────────────────────────────────────────────────────────────

    @Test
    fun `torque nao e estimado quase parado`() {
        // A 5 rpm, 100 kW dariam ~191 000 Nm. Um traço é mais honesto.
        assertNull(estimateTorqueNm(powerKw = 100f, motorSpeedRpm = 5f))
        assertNull(estimateTorqueNm(powerKw = 100f, motorSpeedRpm = 0f))
        assertNull(estimateTorqueNm(powerKw = 100f, motorSpeedRpm = null))
    }

    @Test
    fun `torque sai de P sobre omega acima do piso`() {
        // 100 kW a 3000 rpm: 100000 / (3000 * 2pi/60) = 318,3 Nm.
        val nm = estimateTorqueNm(powerKw = 100f, motorSpeedRpm = 3000f)
        assertNotNull(nm)
        assertEquals(318.3f, nm!!, 0.5f)
    }

    @Test
    fun `potencia nao positiva nao gera torque`() {
        assertNull(estimateTorqueNm(powerKw = -20f, motorSpeedRpm = 3000f))
        assertNull(estimateTorqueNm(powerKw = null, motorSpeedRpm = 3000f))
    }

    // ── Escalas ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `o piso da escala de potencia ja passa dos 160 kW reclamados`() {
        assertEquals(240f, pickScaleCeiling(POWER_SCALE_LADDER_KW, 0f), 0f)
        assertEquals(240f, pickScaleCeiling(POWER_SCALE_LADDER_KW, 160f), 0f)
    }

    @Test
    fun `a escala sobe sozinha quando o carro pede mais`() {
        assertEquals(320f, pickScaleCeiling(POWER_SCALE_LADDER_KW, 241f), 0f)
        assertEquals(400f, pickScaleCeiling(POWER_SCALE_LADDER_KW, 330f), 0f)
    }

    @Test
    fun `um pico absurdo para no ultimo degrau em vez de estourar`() {
        assertEquals(
            POWER_SCALE_LADDER_KW.last(),
            pickScaleCeiling(POWER_SCALE_LADDER_KW, 99_999f),
            0f
        )
    }

    @Test
    fun `o piso da escala de torque ja passa dos 400 Nm reclamados`() {
        assertEquals(600f, pickScaleCeiling(TORQUE_SCALE_LADDER_NM, 0f), 0f)
        assertEquals(800f, pickScaleCeiling(TORQUE_SCALE_LADDER_NM, 601f), 0f)
    }

    @Test
    fun `as marcacoes saem do fim de escala escolhido`() {
        assertEquals(listOf(0f, 60f, 120f, 180f, 240f), majorsFor(240f))
        assertEquals(listOf(0f, 150f, 300f, 450f, 600f), majorsFor(600f))
    }

    @Test
    fun `o ponteiro nunca satura antes do pico ja observado`() {
        // A propriedade que interessa: qualquer valor que o carro tenha entregue cabe na escala.
        for (pico in listOf(0f, 1f, 160f, 239f, 240f, 241f, 519f, 790f)) {
            assertTrue(
                "pico $pico não cabe na escala escolhida",
                pickScaleCeiling(POWER_SCALE_LADDER_KW, pico) >= pico
            )
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
