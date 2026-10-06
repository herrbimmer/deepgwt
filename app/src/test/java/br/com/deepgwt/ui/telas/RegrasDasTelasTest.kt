// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** `telas-e-servico-diversos.md` X-V2 e V2/V4/V5. */
class RegrasDasTelasTest {

    @Test
    fun `formato do instante (X-V2)`() {
        assertEquals("00:20.600", FormatoDoInstante.formatar(20600, true))
        assertEquals("01:08.300", FormatoDoInstante.formatar(68300, true))
        assertEquals("62:05.004", FormatoDoInstante.formatar(3725004, true))
        assertNull(FormatoDoInstante.formatar(0, true))
        assertNull(FormatoDoInstante.formatar(20600, false))
    }

    @Test
    fun `busca dos valores olha so o nome e ordena`() {
        val valores = mapOf("car.b.speed" to "10", "car.a.SPEED_x" to "2", "car.c.rpm" to "speed")
        assertEquals(
            listOf("car.a.SPEED_x" to "2", "car.b.speed" to "10"),
            RegrasDosValores.filtrar(valores, "Speed"),
        )
        assertEquals(3, RegrasDosValores.filtrar(valores, "").size)
        assertEquals(mapOf("a" to "", "b" to "1"), RegrasDosValores.semNulos(mapOf("a" to null, "b" to "1")))
    }

    @Test
    fun `monitoraveis tira as padrao, filtra e poe as marcadas primeiro`() {
        val todas = listOf("a.um", "b.dois", "c.tres", "d.quatro", "e.cinco")
        assertEquals(
            listOf("d.quatro", "a.um", "c.tres", "e.cinco"),
            RegrasDosValores.monitoraveis(todas, listOf("b.dois"), setOf("d.quatro"), ""),
        )
        assertEquals(listOf("c.tres"), RegrasDosValores.monitoraveis(todas, emptyList(), emptySet(), "TRES"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
