package br.com.opengw.nucleo.shizuku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

/** §H (só a decisão): quando a rede de segurança chama o iniciador. */
class RedeDeSegurancaTest {

    private var agora = 0L
    private val pausas = mutableListOf<Long>()
    private var binderDesde: Long? = null
    private val iniciador = IniciadorFalso()
    private val trava = ReentrantLock()

    private fun rede() = RedeDeSeguranca(
        dormir = { ms -> pausas += ms; agora += ms },
        binderRespondendo = { binderDesde?.let { agora >= it } ?: false },
        iniciador = { iniciador },
        pacote = { "br.com.exemplo.app" },
        travaDaSubida = trava,
        log = {},
    )

    @Test
    fun `binder ja de pe depois da espera inicial - nao faz nada (D9)`() {
        binderDesde = 10_000

        assertEquals(RedeDeSeguranca.Fim.BINDER_DE_PE, rede().rodar())

        assertEquals(listOf(RegrasDaSubida.REDE_ESPERA_INICIAL_MS), pausas)
        assertEquals(0, iniciador.inicios)
    }

    @Test
    fun `sem binder sobe pela rede e confere o pid 3 s depois`() {
        iniciador.pid = { true }

        assertEquals(RedeDeSeguranca.Fim.SUBIU, rede().rodar())

        assertEquals(1, iniciador.inicios)
        assertEquals(listOf(true), iniciador.pelaRede)
        assertEquals(listOf("br.com.exemplo.app"), iniciador.pacotes)
        assertEquals(listOf(45_000L, 3_000L), pausas)
        assertEquals(listOf("iniciar", "pid", "terminar"), iniciador.chamadas)
        assertTrue(!trava.isLocked)
    }

    @Test
    fun `binder que aparece entre tentativas encerra a rede`() {
        iniciador.pid = { false }
        iniciador.aoIniciar = { n ->
            if (n == 2) binderDesde = agora + 40_000 // aparece antes da 4ª
            Disparo(disparado = true)
        }

        assertEquals(RedeDeSeguranca.Fim.BINDER_DE_PE, rede().rodar())
        assertEquals(3, iniciador.inicios)
    }

    @Test
    fun `nunca sobe - 20 tentativas com 30 s de folga e para`() {
        iniciador.pid = { false }

        assertEquals(RedeDeSeguranca.Fim.ESGOTOU, rede().rodar())

        assertEquals(RegrasDaSubida.REDE_TENTATIVAS, iniciador.inicios)
        assertEquals(45_000L + 19 * 30_000L + 20 * 3_000L, pausas.sum())
        assertEquals(RegrasDaSubida.REDE_TENTATIVAS, iniciador.chamadas.count { it == "terminar" })
    }

    @Test
    fun `falha de disparo ou exceção não para a rede`() {
        iniciador.aoIniciar = { n ->
            when (n) {
                1 -> throw IllegalStateException("telnet caiu")
                2 -> Disparo(disparado = false, erro = "nenhum host")
                else -> {
                    binderDesde = agora
                    Disparo(disparado = true)
                }
            }
        }

        assertEquals(RedeDeSeguranca.Fim.SUBIU, rede().rodar())
        assertEquals(3, iniciador.inicios)
    }

    @Test
    fun `subida do caminho principal em andamento - a rede nunca entra por cima (D5)`() {
        val segurando = CountDownLatch(1)
        val soltar = CountDownLatch(1)
        val dono = thread {
            trava.lock()
            segurando.countDown()
            soltar.await(5, TimeUnit.SECONDS)
            trava.unlock()
        }
        segurando.await(5, TimeUnit.SECONDS)
        try {
            assertEquals(RedeDeSeguranca.Fim.ESGOTOU, rede().rodar())
            assertEquals(0, iniciador.inicios)
        } finally {
            soltar.countDown()
            dono.join()
        }
    }
}
