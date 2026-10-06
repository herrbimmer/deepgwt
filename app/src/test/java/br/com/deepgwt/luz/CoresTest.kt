// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import org.junit.Assert.assertEquals
import org.junit.Test

/** V12 (matiz), V13 (transição, exata) e V17 (cor de acento, exata). */
class CoresTest {

    @Test
    fun `V12 matiz para cor`() {
        val casos = mapOf(
            0f to CorLed(255, 0, 0),
            30f to CorLed(255, 128, 0),
            45f to CorLed(255, 191, 0),
            60f to CorLed(255, 255, 0),
            90f to CorLed(128, 255, 0),
            120f to CorLed(0, 255, 0),
            180f to CorLed(0, 255, 255),
            200f to CorLed(0, 170, 255),
            240f to CorLed(0, 0, 255),
            270f to CorLed(128, 0, 255),
            300f to CorLed(255, 0, 255),
            330f to CorLed(255, 0, 128),
            360f to CorLed(255, 0, 0),
        )
        for ((h, cor) in casos) assertEquals("matiz $h", cor, CorLed.daMatiz(h))
    }

    @Test
    fun `matiz fora da faixa e limitada`() {
        assertEquals(CorLed(255, 0, 0), CorLed.daMatiz(-30f))
        assertEquals(CorLed(255, 0, 0), CorLed.daMatiz(720f))
        assertEquals(CorLed(255, 0, 0), CorLed.daMatiz(Float.NaN))
    }

    private fun cores(texto: String): List<CorLed> =
        Regex("""\((\d+),(\d+),(\d+)\)""").findAll(texto).map { m ->
            val (r, g, b) = m.destructured
            CorLed(r.toInt(), g.toInt(), b.toInt())
        }.toList()

    @Test
    fun `V13 transicao branco para verde`() {
        val esperado = cores(
            "(242,255,242) (229,255,229) (216,255,216) (204,255,204) (191,255,191) (178,255,178) (165,255,165) " +
                "(153,255,153) (140,255,140) (127,255,127) (114,255,114) (102,255,102) (89,255,89) (76,255,76) " +
                "(63,255,63) (51,255,51) (38,255,38) (25,255,25) (12,255,12) (0,255,0)",
        )
        assertEquals(esperado, Transicao.passos(Cores.BRANCO, Cores.VERDE))
    }

    @Test
    fun `V13 transicao vermelho fraco para vermelho`() {
        val r = listOf(126, 133, 140, 147, 153, 160, 167, 174, 180, 187, 194, 201, 207, 214, 221, 228, 234, 241, 248, 255)
        assertEquals(r.map { CorLed(it, 0, 0) }, Transicao.passos(Cores.VERMELHO_FRACO, Cores.VERMELHO))
    }

    @Test
    fun `V13 transicao branco para azul suave`() {
        val esperado = cores(
            "(246,248,255) (237,241,255) (228,234,255) (220,228,255) (211,221,255) (202,214,255) (193,207,255) " +
                "(185,201,255) (176,194,255) (167,187,255) (158,180,255) (150,174,255) (141,167,255) (132,160,255) " +
                "(123,153,255) (115,147,255) (106,140,255) (97,133,255) (88,126,255) (80,120,255)",
        )
        assertEquals(esperado, Transicao.passos(Cores.BRANCO, Cores.AZUL_SUAVE))
    }

    @Test
    fun `V17 cor de acento dos graves`() {
        val niveis = listOf(0.0, 0.35, 0.5, 0.75, 1.0, 1.5)
        val tabela = mapOf(
            CorLed(80, 120, 255) to "(186,76,100) (198,71,83) (218,63,54) (238,55,26)",
            CorLed(255, 0, 0) to "(255,155,0) (255,172,0) (255,201,0) (255,230,0)",
            CorLed(255, 255, 255) to "(255,129,100) (255,115,83) (255,92,54) (255,69,26)",
            CorLed(0, 255, 0) to "(155,129,0) (172,115,0) (201,92,0) (230,69,0)",
            CorLed(0, 180, 255) to "(155,100,100) (172,91,83) (201,76,54) (230,61,26)",
            CorLed(255, 120, 0) to "(255,76,0) (255,71,0) (255,63,0) (255,55,0)",
            CorLed(180, 80, 80) to "(226,186,31) (231,198,26) (239,218,17) (248,238,8)",
            CorLed(179, 80, 80) to "(225,61,31) (230,58,26) (239,55,17) (247,51,8)",
        )
        for ((base, texto) in tabela) {
            val (n35, n50, n75, n100) = cores(texto)
            val esperado = listOf(n35, n35, n50, n75, n100, n100)
            assertEquals("base $base", esperado, niveis.map { CorDeAcento.calcular(base, it) })
        }
    }

    @Test
    fun `cor-base da musica`() {
        assertEquals(Cores.AZUL_SUAVE, CorDeAcento.corBaseDaMusica(ModoDeConducao.DESCONHECIDO))
        assertEquals(Cores.VERMELHO, CorDeAcento.corBaseDaMusica(ModoDeConducao.SPORT))
        assertEquals(Cores.BRANCO, CorDeAcento.corBaseDaMusica(ModoDeConducao.NORMAL))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
