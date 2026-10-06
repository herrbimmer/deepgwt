// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import br.com.deepgwt.veiculo.motor.AgendaFalsa
import br.com.deepgwt.veiculo.motor.RegistroFalsoDoMotor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** motor-sistema.md §F (receptores do retrato e do reinício). */
class ReceptoresTest {

    @Test
    fun `F1 - um retrato por broadcast, via quandoPronto, na linha do motor`() {
        val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
        val agenda = AgendaFalsa(eventos)
        val guardadas = ArrayList<Runnable>()
        var pronto = false
        val retratos = AtomicInteger()
        val fins = AtomicInteger()
        val tratador = TratadorDoRetrato(
            agenda = agenda,
            quandoPronto = { tarefa -> if (pronto) tarefa.run() else guardadas += tarefa },
            reenviar = { retratos.incrementAndGet() },
            relogio = { 0L },
            registro = RegistroFalsoDoMotor(),
        )

        tratador.aoReceber { fins.incrementAndGet() }
        assertEquals("na linha, não na thread do broadcast", 0, retratos.get())
        assertEquals(0, fins.get())
        agenda.rodarPendentes()
        assertEquals("ainda não pronto: guardado", 0, retratos.get())
        assertEquals(1, fins.get())
        guardadas.single().run()
        assertEquals(1, retratos.get())

        pronto = true
        tratador.aoReceber { fins.incrementAndGet() }
        agenda.rodarPendentes()
        assertEquals("uma vez por broadcast", 2, retratos.get())
        assertEquals(2, fins.get())
    }

    @Test
    fun `F1 - retrato que lanca e engolido e o broadcast e liberado`() {
        val eventos: MutableList<String> = Collections.synchronizedList(ArrayList())
        val agenda = AgendaFalsa(eventos)
        val registro = RegistroFalsoDoMotor()
        val fins = AtomicInteger()
        val tratador = TratadorDoRetrato(agenda, { it.run() }, { throw IllegalStateException("x") }, { 0L }, registro)
        tratador.aoReceber { fins.incrementAndGet() }
        agenda.rodarPendentes()
        assertEquals(1, fins.get())
        assertEquals(1, registro.erros.size)
    }

    @Test
    fun `F2 - extra ausente ou vazio`() {
        assertNull(servicoParaReiniciar(null))
        assertNull(servicoParaReiniciar(""))
        assertNull(servicoParaReiniciar("  "))
        assertEquals("br.com.x.Servico", servicoParaReiniciar(" br.com.x.Servico "))
    }

    private class AlarmeFalso(private val recusar: Set<String>) : AlarmeDoReinicio {
        val tentativas = ArrayList<String>()

        private fun tentar(nome: String) {
            tentativas += nome
            if (nome in recusar) throw SecurityException("recusado")
        }

        override fun exatoMesmoOcioso() = tentar("ocioso")
        override fun exato() = tentar("exato")
        override fun comum() = tentar("comum")
    }

    @Test
    fun `F2 - escada do alarme`() {
        val registro = RegistroFalsoDoMotor()
        val primeiro = AlarmeFalso(emptySet())
        assertEquals("exato mesmo ocioso", programarReinicio(primeiro, registro))
        assertEquals(listOf("ocioso"), primeiro.tentativas)

        val segundo = AlarmeFalso(setOf("ocioso"))
        assertEquals("exato", programarReinicio(segundo, registro))
        assertEquals(listOf("ocioso", "exato"), segundo.tentativas)

        val terceiro = AlarmeFalso(setOf("ocioso", "exato"))
        assertEquals("comum", programarReinicio(terceiro, registro))

        val nenhum = AlarmeFalso(setOf("ocioso", "exato", "comum"))
        assertEquals("nenhum", programarReinicio(nenhum, registro))
        assertTrue(registro.avisos.size >= 6)
    }

    @Test
    fun `o atraso do reinício dobra a cada reinício seguido e para em um minuto`() {
        assertEquals(1_000L, atrasoDoReinicio(1_000L, 0))
        assertEquals(2_000L, atrasoDoReinicio(1_000L, 1))
        assertEquals(8_000L, atrasoDoReinicio(1_000L, 3))
        assertEquals(60_000L, atrasoDoReinicio(1_000L, 10))
        assertEquals(60_000L, atrasoDoReinicio(1_000L, 1_000))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
