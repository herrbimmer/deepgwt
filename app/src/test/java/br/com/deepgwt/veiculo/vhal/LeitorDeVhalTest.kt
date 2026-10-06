// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.vhal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/** Vetores P3 a P6 da especificação `extras-viagem.md` (Parte B, §19). */
class LeitorDeVhalTest {

    private var agora = 0L
    private var tentativas = 0
    private var resposta: () -> Any? = { null }
    private var chamadas = 0

    /** Fonte que obedece à `resposta` atual. */
    private val fonte = FonteDePropriedades { _, _ ->
        chamadas++
        resposta()
    }

    private fun leitor(disponivel: () -> Boolean = { true }) =
        LeitorDeVhal(
            fonte = {
                tentativas++
                if (disponivel()) fonte else null
            },
            relogioMs = { agora },
        )

    @Test
    fun `P3 tira o branco das pontas`() {
        resposta = { " 80 " }
        assertEquals("80", leitor().ler(557847281))
    }

    @Test
    fun `P3 vazio ou branco ou nulo vira nulo`() {
        val l = leitor()
        resposta = { "" }
        assertNull(l.ler(1))
        resposta = { "  \t " }
        assertNull(l.ler(1))
        resposta = { null }
        assertNull(l.ler(1))
        assertEquals("a leitura vazia nao derruba a ligacao", 1, tentativas)
    }

    @Test
    fun `P3 float e inteiro viram texto`() {
        val l = leitor()
        resposta = { 2.5f }
        assertEquals("2.5", l.ler(1))
        resposta = { 1 }
        assertEquals("1", l.ler(1))
    }

    @Test
    fun `vetores viram chaves com virgulas`() {
        val l = leitor()
        resposta = { arrayOf(1, 2, 3) }
        assertEquals("{1,2,3}", l.ler(1))
        resposta = { intArrayOf(7) }
        assertEquals("{7}", l.ler(1))
        resposta = { emptyArray<Int>() }
        assertEquals("{}", l.ler(1))
    }

    @Test
    fun `P3 fonte que lanca da nulo e descarta a ligacao`() {
        val l = leitor()
        resposta = { "1" }
        assertEquals("1", l.ler(1))
        assertEquals(1, tentativas)

        resposta = { throw IllegalStateException("morreu") }
        assertNull(l.ler(1))
        assertEquals(2, chamadas)

        // Ligacao descartada: a proxima chamada precisa ligar de novo (dentro da janela, nao consegue).
        resposta = { "1" }
        agora = 5_000
        assertNull(l.ler(1))
        assertEquals(2, chamadas)
        agora = 10_000
        assertEquals("1", l.ler(1))
        assertEquals(2, tentativas)
    }

    @Test
    fun `P3 a fonte de abrir que lanca da nulo`() {
        val l = LeitorDeVhal({ throw IllegalStateException("sem car_service") }, { agora })
        assertNull(l.ler(1))
    }

    @Test
    fun `area e repassada`() {
        var vista = -1
        val l = LeitorDeVhal({ FonteDePropriedades { _, area -> vista = area; "x" } }, { agora })
        assertEquals("x", l.ler(1, 4))
        assertEquals(4, vista)
        l.ler(1)
        assertEquals(0, vista)
    }

    @Test
    fun `P4 janela de 10 s entre tentativas de ligar`() {
        val l = leitor(disponivel = { false })
        agora = 0
        assertNull(l.ler(1))
        assertEquals(1, tentativas)
        agora = 1_000
        assertNull(l.ler(1))
        agora = 9_999
        assertNull(l.ler(1))
        assertEquals(1, tentativas)
        agora = 10_000
        assertNull(l.ler(1))
        assertEquals(2, tentativas)
        agora = 19_999
        assertNull(l.ler(1))
        assertEquals("a janela conta do inicio da ultima tentativa", 2, tentativas)
    }

    @Test
    fun `P4 liga de novo na hora se a ultima tentativa foi ha mais de 10 s`() {
        val l = leitor()
        resposta = { "1" }
        agora = 0
        assertEquals("1", l.ler(1))
        assertEquals(1, tentativas)

        agora = 100_000
        resposta = { throw IllegalStateException("falhou") }
        assertNull(l.ler(1))
        assertEquals(1, tentativas)

        resposta = { "2" }
        assertEquals("2", l.ler(1))
        assertEquals(2, tentativas)
    }

    @Test
    fun `P4 fechar zera a janela`() {
        val l = leitor(disponivel = { false })
        agora = 0
        assertNull(l.ler(1))
        agora = 1_000
        assertNull(l.ler(1))
        assertEquals(1, tentativas)
        l.fechar()
        assertNull(l.ler(1))
        assertEquals(2, tentativas)
    }

    @Test
    fun `P4 fechar descarta a ligacao`() {
        val l = leitor()
        resposta = { "1" }
        agora = 0
        l.ler(1)
        l.fechar()
        agora = 1
        l.ler(1)
        assertEquals(2, tentativas)
    }

    @Test
    fun `P5 oito threads nunca chamam a fonte em paralelo`() {
        val dentro = AtomicInteger(0)
        val maximo = AtomicInteger(0)
        val total = AtomicInteger(0)
        val aberturas = AtomicInteger(0)
        val l = LeitorDeVhal(
            fonte = {
                aberturas.incrementAndGet()
                FonteDePropriedades { id, _ ->
                    val n = dentro.incrementAndGet()
                    maximo.accumulateAndGet(n) { a, b -> maxOf(a, b) }
                    Thread.sleep(1)
                    total.incrementAndGet()
                    dentro.decrementAndGet()
                    id.toString()
                }
            },
            relogioMs = { 0 },
        )
        val largada = CountDownLatch(1)
        val resultados = java.util.Collections.synchronizedList(ArrayList<String?>())
        val threads = (1..8).map { t ->
            Thread {
                largada.await()
                repeat(25) { resultados += l.ler(t) }
            }.also { it.start() }
        }
        largada.countDown()
        threads.forEach { it.join(20_000) }
        assertEquals(1, maximo.get())
        assertEquals(200, total.get())
        assertEquals(200, resultados.size)
        assertEquals(1, aberturas.get())
        assertTrue(resultados.all { it != null })
    }

    @Test
    fun `P6 fonte real sem as classes do Android da nulo e nao lanca`() {
        val l = LeitorDeVhal(FonteDaCentral::abrir) { agora }
        assertNull(l.ler(557847281))
        assertNull(l.ler(557847281, 3))
        l.fechar()
        assertNull(l.ler(1))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
