package br.com.deepgwt.tela3d.viagens

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicoDeMapasTest {

    @Test
    fun `agendar roda a varredura e remarca a proxima`() {
        val rodou = CountDownLatch(1)
        val servico = ServicoDeMapas({ rodou.countDown() })
        try {
            servico.agendar(10)
            assertTrue(rodou.await(5, TimeUnit.SECONDS))
        } finally {
            servico.encerrar()
        }
    }

    @Test
    fun `um pedido mais cedo vale e um mais tarde nao`() {
        val rodadas = AtomicInteger()
        val rodou = CountDownLatch(1)
        val servico = ServicoDeMapas({ rodadas.incrementAndGet(); rodou.countDown() })
        try {
            servico.agendar(600_000)
            servico.agendar(20)
            assertTrue(rodou.await(5, TimeUnit.SECONDS))
            assertEquals(1, rodadas.get())
            // depois da varredura ficou marcada a de 10 min: um pedido mais tarde não a troca
            servico.agendar(900_000)
            Thread.sleep(100)
            assertEquals(1, rodadas.get())
        } finally {
            servico.encerrar()
        }
    }

    @Test
    fun `nunca duas varreduras ao mesmo tempo`() {
        val simultaneas = AtomicInteger()
        val maximo = AtomicInteger()
        val rodadas = AtomicInteger()
        val duas = CountDownLatch(2)
        val servico = ServicoDeMapas({
            val n = simultaneas.incrementAndGet()
            maximo.updateAndGet { m -> maxOf(m, n) }
            Thread.sleep(80)
            simultaneas.decrementAndGet()
            rodadas.incrementAndGet()
            duas.countDown()
        })
        try {
            servico.agendar(5)
            Thread.sleep(20)
            // pedido feito durante a varredura: roda logo depois dela, não junto
            servico.agendar(5)
            assertTrue(duas.await(5, TimeUnit.SECONDS))
            assertEquals(1, maximo.get())
        } finally {
            servico.encerrar()
        }
    }

    @Test
    fun `excecao na varredura nao mata o servico`() {
        val rodadas = AtomicInteger()
        val duas = CountDownLatch(2)
        val servico = ServicoDeMapas({
            rodadas.incrementAndGet()
            duas.countDown()
            throw IllegalStateException("falhou")
        })
        try {
            servico.agendar(5)
            Thread.sleep(50)
            servico.agendar(5)
            assertTrue(duas.await(5, TimeUnit.SECONDS))
            assertFalse(rodadas.get() < 2)
        } finally {
            servico.encerrar()
        }
    }
}
