// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A física de gesto compartilhada entre o app e o launcher 3D (skill apple-design).
 *
 * POR QUE ESTE TESTE EXISTE: o erro clássico aqui é trocar o decaimento exponencial que a Apple
 * publica pela fórmula de livro v²/(2a). As duas são "projeção da velocidade", dão números bem
 * diferentes e nenhuma das duas falha — a errada só faz todo lançamento parecer estranho, que é
 * o tipo de defeito que ninguém consegue relatar.
 */
class MotionTest {
    @Test
    fun projecaoUsaDecaimentoExponencial() {
        // 500 px/s com d=0.998 -> (0.5 * 0.998) / 0.002
        assertEquals(249.5f, projectDeceleration(500f), 0.1f)
        assertEquals(0f, projectDeceleration(0f), 0.0001f)
        // Linear na velocidade — o dobro da velocidade, o dobro da distância.
        assertEquals(2f * projectDeceleration(500f), projectDeceleration(1000f), 0.5f)
        // E preserva o sentido.
        assertTrue(projectDeceleration(-500f) < 0f)
    }

    @Test
    fun projecaoNaoEhAFormulaDeLivro() {
        // v²/(2a) com a desaceleração equivalente daria outra ordem de grandeza. Se alguém trocar
        // a fórmula, este teste é o que avisa.
        val exponencial = projectDeceleration(1000f)
        assertTrue("projeção de 1000 px/s deveria passar de 400 px", exponencial > 400f)
        assertTrue("e ficar abaixo de 600 px", exponencial < 600f)
    }

    @Test
    fun taxaMenorDaLancamentoMaisSeco() {
        assertTrue(projectDeceleration(500f, 0.99f) < projectDeceleration(500f, 0.998f))
        assertEquals(49.5f, projectDeceleration(500f, 0.99f), 0.1f)
    }

    @Test
    fun elasticoResisteProgressivamenteETemAssintota() {
        assertEquals(0f, rubberband(0f, 100f), 0.0001f)
        val pouco = rubberband(50f, 100f)
        val muito = rubberband(400f, 100f)
        assertTrue("mais excesso, mais deslocamento", muito > pouco)
        assertTrue("mas muito menos que o excesso bruto", muito < 400f)
        // Assíntota = dimensao / constante = 100 / 0.55
        assertTrue(rubberband(1_000_000f, 100f) < 100f / 0.55f + 0.5f)
        assertTrue("o sinal é preservado", rubberband(-50f, 100f) < 0f)
    }

    @Test
    fun elasticoComDimensaoInvalidaNaoQuebra() {
        assertEquals(50f, rubberband(50f, 0f), 0.0001f)
        assertEquals(50f, rubberband(50f, -10f), 0.0001f)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
