package br.com.opengw.circulo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelogiosDoCirculoTest {

    private fun ms(h: Int, m: Int, s: Int, milis: Int = 0): Long =
        ((h * 3_600L + m * 60L + s) * 1_000L) + milis

    @Test
    fun `catorze relogios, ids unicos, e o gravado volta pelo id`() {
        assertEquals(14, RelogiosDoCirculo.QUANTOS)
        val ids = RelogiosDoCirculo.TODOS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(RelogiosDoCirculo.TODOS.all { it.nome.isNotBlank() })
        assertEquals(0, RelogiosDoCirculo.indiceDe(null))
        assertEquals(0, RelogiosDoCirculo.indiceDe("sumiu"))
        RelogiosDoCirculo.TODOS.forEachIndexed { i, r -> assertEquals(i, RelogiosDoCirculo.indiceDe(r.id)) }
        assertEquals(RelogiosDoCirculo.TODOS.last(), RelogiosDoCirculo.doIndice(-1))
        assertEquals(RelogiosDoCirculo.TODOS.first(), RelogiosDoCirculo.doIndice(14))
    }

    @Test
    fun `ponteiros as 10h10min30s`() {
        val p = RelogiosDoCirculo.ponteiros(ms(10, 10, 30), continuo = false)
        assertEquals(305.25f, p.hora, 0.001f)
        assertEquals(63f, p.minuto, 0.001f)
        assertEquals(180f, p.segundo, 0.001f)
    }

    @Test
    fun `quartzo salta de segundo em segundo, automatico varre`() {
        val salto = RelogiosDoCirculo.ponteiros(ms(3, 0, 15, 900), continuo = false)
        assertEquals(90f, salto.segundo, 0.001f)
        val varre = RelogiosDoCirculo.ponteiros(ms(3, 0, 15, 500), continuo = true)
        assertEquals(93f, varre.segundo, 0.001f)
        // 15h e 3h apontam para o mesmo lugar; meia-noite zera tudo.
        assertEquals(
            RelogiosDoCirculo.ponteiros(ms(3, 0, 0), false),
            RelogiosDoCirculo.ponteiros(ms(15, 0, 0), false)
        )
        val zero = RelogiosDoCirculo.ponteiros(0L, true)
        assertEquals(0f, zero.hora + zero.minuto + zero.segundo, 0.001f)
        // Hora local negativa (fuso a oeste perto da meia-noite UTC) não gira para trás.
        assertEquals(RelogiosDoCirculo.ponteiros(ms(23, 0, 0), false), RelogiosDoCirculo.ponteiros(-ms(1, 0, 0), false))
    }

    @Test
    fun `submostrador de 24 horas da uma volta por dia`() {
        assertEquals(0f, RelogiosDoCirculo.ponteiro24h(0L), 0.001f)
        assertEquals(90f, RelogiosDoCirculo.ponteiro24h(ms(6, 0, 0)), 0.001f)
        assertEquals(270f, RelogiosDoCirculo.ponteiro24h(ms(18, 0, 0)), 0.001f)
    }

    @Test
    fun `os quatro da 78 entram no fim e nao mudam o indice dos antigos`() {
        // A escolha é gravada pelo id, mas a ordem é a do ←/→: quem estava no Noturno continua a
        // um passo do Rally, e os novos vêm depois dele.
        val ids = RelogiosDoCirculo.TODOS.map { it.id }
        assertEquals(listOf("estacao", "grafite", "marinho", "areia"), ids.takeLast(4))
        assertEquals(9, RelogiosDoCirculo.indiceDe("noturno"))
        assertEquals("Estação", RelogiosDoCirculo.doIndice(10).nome)
        assertTrue(ids.takeLast(4).none { id -> RelogiosDoCirculo.TODOS.first { it.id == id }.continuo })
    }
}
