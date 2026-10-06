// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B-V1, B-V2 e B-V3 de `extras-monitores.md`, como tabelas. */
class RegrasDoMonitorDoCarPlayTest {

    private val regras = RegrasDoMonitorDoCarPlay

    @Test
    fun `B-V2 esperaAteLimpar sem quadro valido e sempre 0`() {
        for (agora in longArrayOf(0, 1, 9_000, 10_000, 50_000)) {
            assertEquals(0L, regras.esperaAteLimpar(agora, 0))
        }
        assertEquals(0L, regras.esperaAteLimpar(10_000, -5))
    }

    @Test
    fun `B-V2 esperaAteLimpar com ultimo valido em 10000`() {
        val tabela = mapOf(
            9_000L to 4_000L, 10_000L to 4_000L, 10_500L to 3_500L, 11_500L to 2_500L,
            13_999L to 1L, 14_000L to 0L, 14_001L to 0L, 20_000L to 0L,
        )
        for ((agora, esperado) in tabela) {
            assertEquals("agora=$agora", esperado, regras.esperaAteLimpar(agora, 10_000))
        }
    }

    @Test
    fun `B-V1 devePedirLigacao com binder morto`() {
        for (agora in longArrayOf(0, 5, 10_000, 99_999)) assertTrue(regras.devePedirLigacao(agora, 0, false))
        val tabela = mapOf(
            9_000L to true, 10_000L to false, 14_999L to false, 15_000L to true, 15_001L to true, 30_000L to true,
        )
        for ((agora, esperado) in tabela) {
            assertEquals("agora=$agora", esperado, regras.devePedirLigacao(agora, 10_000, false))
        }
    }

    @Test
    fun `B-V1 binder vivo nunca pede ligacao`() {
        assertFalse(regras.devePedirLigacao(30_000, 10_000, true))
        assertFalse(regras.devePedirLigacao(30_000, 0, true))
    }

    @Test
    fun `B-V3 deveRenovarAssinatura com binder vivo e ultima tentativa em 5000`() {
        val agoras = longArrayOf(19_999, 20_000, 25_000, 31_999, 32_000, 35_000)
        // linhas: (assinado, ultimo quadro) -> resultados por agora
        val tabela = listOf(
            Triple(false, 0L, booleanArrayOf(false, true, true, true, true, true)),
            Triple(false, 12_000L, booleanArrayOf(false, true, true, true, true, true)),
            Triple(true, 0L, booleanArrayOf(false, true, true, true, true, true)),
            Triple(true, 12_000L, booleanArrayOf(false, false, false, false, true, true)),
        )
        for ((assinado, ultimoQuadro, esperados) in tabela) {
            for ((i, agora) in agoras.withIndex()) {
                assertEquals(
                    "assinado=$assinado quadro=$ultimoQuadro agora=$agora",
                    esperados[i],
                    regras.deveRenovarAssinatura(agora, 5_000, ultimoQuadro, assinado, true),
                )
            }
        }
    }

    @Test
    fun `B-V3 casos extras`() {
        assertFalse(regras.deveRenovarAssinatura(1, 0, 1, true, true))
        assertFalse(regras.deveRenovarAssinatura(99_999, 0, 0, false, false))
        // relógio para trás: agora 3000 < última tentativa 5000
        assertFalse(regras.deveRenovarAssinatura(3_000, 5_000, 1_000, true, true))
        assertTrue(regras.deveRenovarAssinatura(3_000, 5_000, 1_000, false, true))
    }

    @Test
    fun `devePedirLigacao com o relogio para tras pede`() {
        assertTrue(regras.devePedirLigacao(9_000, 10_000, false))
    }

    @Test
    fun `constantes combinadas`() {
        assertEquals(4_000L, RegrasDoMonitorDoCarPlay.GRACA_DO_QUADRO_VAZIO_MS)
        assertEquals(5_000L, RegrasDoMonitorDoCarPlay.INTERVALO_ENTRE_LIGACOES_MS)
        assertEquals(15_000L, RegrasDoMonitorDoCarPlay.INTERVALO_ENTRE_ASSINATURAS_MS)
        assertEquals(20_000L, RegrasDoMonitorDoCarPlay.QUADRO_VELHO_MS)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
