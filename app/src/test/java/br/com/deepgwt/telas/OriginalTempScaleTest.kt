// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.telas.OriginalTempScale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tamanho da temperatura sobre o painel de fábrica (Alpha 6.7). Ver [OriginalTempScale]. */
class OriginalTempScaleTest {

    @Test
    fun `o padrao e o tamanho da 6_6`() {
        assertEquals(44, OriginalTempScale.emPx(OriginalTempScale.PADRAO))
    }

    @Test
    fun `a faixa vai do tamanho de antes da 6_6 ao dobro`() {
        assertEquals(29, OriginalTempScale.emPx(OriginalTempScale.MINIMO))
        assertEquals(88, OriginalTempScale.emPx(OriginalTempScale.MAXIMO))
    }

    @Test
    fun `valor fora da faixa encosta na borda`() {
        assertEquals(OriginalTempScale.MINIMO, OriginalTempScale.normalizar(0.1f), 0f)
        assertEquals(OriginalTempScale.MAXIMO, OriginalTempScale.normalizar(9f), 0f)
    }

    @Test
    fun `preferencia estragada volta ao padrao`() {
        assertEquals(OriginalTempScale.PADRAO, OriginalTempScale.normalizar(Float.NaN), 0f)
        assertEquals(OriginalTempScale.PADRAO, OriginalTempScale.normalizar(Float.POSITIVE_INFINITY), 0f)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
