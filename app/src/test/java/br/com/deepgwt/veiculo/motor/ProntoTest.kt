// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Seção H da especificação `motor-nucleo.md`. */
class ProntoTest {

    private val b = Bancada()

    @Test
    fun `tarefas antes da partida esperam e rodam na linha do motor na ordem`() {
        val ordem = Collections.synchronizedList(ArrayList<Int>())
        b.nucleo.quandoPronto { ordem += 1 }
        b.nucleo.quandoPronto { ordem += 2 }
        assertTrue(ordem.isEmpty())

        assertTrue(b.partir())
        assertTrue(ordem.isEmpty()) // vão para a linha do motor
        b.agenda.rodarPendentes()

        assertEquals(listOf(1, 2), ordem.toList())
    }

    @Test
    fun `depois de pronto roda na hora na thread de quem chamou`() {
        b.partirLimpo()
        var rodouEm: Thread? = null

        b.nucleo.quandoPronto { rodouEm = Thread.currentThread() }

        assertEquals(Thread.currentThread(), rodouEm)
    }

    @Test
    fun `excecao de uma tarefa e engolida e as outras rodam`() {
        val rodou = AtomicInteger()
        b.nucleo.quandoPronto { throw IllegalStateException("x") }
        b.nucleo.quandoPronto { rodou.incrementAndGet() }

        b.partir()
        b.agenda.rodarPendentes()
        b.nucleo.quandoPronto { throw IllegalStateException("y") }

        assertEquals(1, rodou.get())
        assertEquals(2, b.registro.erros.count { "quandoPronto" in it })
    }

    @Test
    fun `nenhuma tarefa se perde sob concorrencia`() {
        val rodadas = AtomicInteger()
        val largada = CountDownLatch(1)
        val threads = (1..8).map {
            thread {
                largada.await()
                repeat(500) { b.nucleo.quandoPronto { rodadas.incrementAndGet() } }
            }
        }
        val partida = thread {
            largada.await()
            Thread.sleep(1)
            b.nucleo.partirSimulado()
        }

        largada.countDown()
        threads.forEach { it.join() }
        partida.join()
        b.agenda.rodarPendentes()

        assertEquals(4_000, rodadas.get())
    }

    @Test
    fun `pronto nao volta a falso`() {
        val canal = b.partirLimpo()
        assertTrue(b.nucleo.pronto)

        canal.morrer()
        b.central.shizuku = false
        assertFalse(b.partir())

        assertTrue(b.nucleo.pronto)
    }

    @Test
    fun `pronto so depois da primeira partida que deu certo`() {
        b.central.servicoAusente = true
        assertFalse(b.partir())
        assertFalse(b.nucleo.pronto)

        b.central.servicoAusente = false
        assertTrue(b.partir())
        assertTrue(b.nucleo.pronto)
    }

    @Test
    fun `controleVivo exige alca, Shizuku e alca viva`() {
        assertFalse(b.nucleo.controleVivo)
        val canal = b.partirLimpo()
        assertTrue(b.nucleo.controleVivo)

        b.central.shizuku = false
        assertFalse(b.nucleo.controleVivo)
        b.central.shizuku = true
        canal.vivoAgora = false
        assertFalse(b.nucleo.controleVivo)
    }

    @Test
    fun `controleVivo nao faz chamada a central`() {
        val canal = b.partirLimpo()
        val antes = b.eventos.size

        repeat(10) { b.nucleo.controleVivo }

        assertEquals(antes, b.eventos.size)
        assertEquals(0, canal.buscas.get())
    }

    @Test
    fun `controleVivo e falso no simulador`() {
        b.nucleo.partirSimulado()

        assertFalse(b.nucleo.controleVivo)
        assertTrue(b.nucleo.pronto)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
