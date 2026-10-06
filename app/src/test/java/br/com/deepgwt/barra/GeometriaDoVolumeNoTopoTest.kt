// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometriaDoVolumeNoTopoTest {

    /** O pop-up do carro na print do espelhamento da 7.7 (1920×720, 160 dpi). */
    private val original = intArrayOf(658, 60, 1390, 156)

    @Test
    fun `no carro o cartao cobre o pop-up original inteiro, com folga dos quatro lados`() {
        val r = GeometriaDoVolumeNoTopo.retangulo(1920, 128, 1f)
        assertArrayEquals(intArrayOf(644, 46, 1404, 170), r)
        assertTrue(r[0] < original[0] && r[1] < original[1] && r[2] > original[2] && r[3] > original[3])
        // "Um pouco maior": 14 px de cada lado.
        assertTrue(original[0] - r[0] == 14 && r[2] - original[2] == 14 && original[1] - r[1] == 14 && r[3] - original[3] == 14)
    }

    @Test
    fun `painel lateral escondido centra na tela, e tela estreita nao corta o cartao`() {
        assertArrayEquals(intArrayOf(580, 46, 1340, 170), GeometriaDoVolumeNoTopo.retangulo(1920, 0, 1f))
        val estreita = GeometriaDoVolumeNoTopo.retangulo(700, 128, 1f)
        assertTrue(estreita[0] >= 0 && estreita[2] <= 700)
        val densa = GeometriaDoVolumeNoTopo.retangulo(1080, 0, 0.75f)
        assertArrayEquals(intArrayOf(255, 35, 825, 128), densa)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
