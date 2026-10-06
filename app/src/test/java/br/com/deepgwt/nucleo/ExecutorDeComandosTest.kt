// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Comportamento de `docs/independencia/specs/shell.md`, sem Android nem Shizuku. */
class ExecutorDeComandosTest {

    private val registro = RegistroFalso()
    private val pausas = CopyOnWriteArrayList<Long>()

    private fun executor(iniciador: IniciadorDeProcesso, limiteDoErroMs: Long = 5_000, limiteDaSaidaMs: Long = 90_000) =
        ExecutorDeComandos(
            iniciador, registro, pausar = { pausas += it },
            limiteDoErroMs = limiteDoErroMs, limiteDaSaidaMs = limiteDaSaidaMs,
        )

    private fun sempre(processo: ProcessoRemoto?) = IniciadorFalso({ processo })

    // ── executar: saída ──

    @Test
    fun `comando pendurado e solto depois do limite e devolve o que ja saiu`() {
        val escrita = PipedOutputStream()
        val saida = PipedInputStream(escrita)
        escrita.write("parcial\n".toByteArray())
        escrita.flush()
        val processo = ProcessoFalso(saida = saida)

        val inicio = System.nanoTime()
        val resultado = executor(sempre(processo), limiteDaSaidaMs = 300).executar(arrayOf("dumpsys"))

        assertEquals("parcial", resultado)
        assertTrue("devia voltar logo depois do limite", (System.nanoTime() - inicio) / 1_000_000 < 3_000)
        assertTrue(processo.liberacoes.get() >= 1)
        assertTrue(registro.erros.toString(), registro.erros.any { "sem resposta" in it })
        escrita.close()
    }

    @Test
    fun `junta as linhas com quebra simples e corta as pontas`() {
        val processo = ProcessoFalso(saida = texto("\n  primeira  \r\nsegunda\n\n"))

        assertEquals("primeira  \nsegunda", executor(sempre(processo)).executar(arrayOf("x")))
    }

    @Test
    fun `fecha a entrada antes de ler e libera o processo no fim`() {
        val processo = ProcessoFalso(saida = texto("ok"))

        executor(sempre(processo)).executar(arrayOf("id"))

        assertEquals("fecharEntrada", processo.eventos.first())
        assertEquals(1, processo.liberacoes.get())
    }

    @Test
    fun `passa o argv exato para quem cria o processo`() {
        val iniciador = sempre(ProcessoFalso())

        executor(iniciador).executar(arrayOf("sh", "-c", "echo a | grep a"))

        assertEquals(listOf(listOf("sh", "-c", "echo a | grep a")), iniciador.chamadas)
    }

    @Test
    fun `codigo diferente de zero devolve a saida e registra comando, codigo e erro`() {
        val processo = ProcessoFalso(saida = texto("parcial\n"), erro = texto("deu ruim\n"), codigo = { 3 })

        val saida = executor(sempre(processo)).executar(arrayOf("sh", "-c", "exit 3"))

        assertEquals("parcial", saida)
        assertEquals(1, registro.erros.size)
        val mensagem = registro.erros.single()
        assertTrue(mensagem, "sh -c exit 3" in mensagem && "3" in mensagem && "deu ruim" in mensagem)
    }

    @Test
    fun `grep com codigo 1 e nada encontrado, nao erro`() {
        val processo = ProcessoFalso(codigo = { 1 })

        val saida = executor(sempre(processo)).executar(arrayOf("sh", "-c", "dumpsys window | grep foco"))

        assertEquals("", saida)
        assertTrue(registro.erros.toString(), registro.erros.isEmpty())
    }

    @Test
    fun `grep com outro codigo e erro de verdade`() {
        val processo = ProcessoFalso(erro = texto("grep: arquivo nao existe"), codigo = { 2 })

        executor(sempre(processo)).executar(arrayOf("grep", "x", "/nao/existe"))

        assertEquals(1, registro.erros.size)
    }

    @Test
    fun `codigo 1 sem grep e erro`() {
        executor(sempre(ProcessoFalso(codigo = { 1 }))).executar(arrayOf("false"))

        assertEquals(1, registro.erros.size)
    }

    @Test
    fun `so saida de erro com codigo zero devolve vazio sem registrar`() {
        val processo = ProcessoFalso(erro = texto("aviso qualquer\n"))

        assertEquals("", executor(sempre(processo)).executar(arrayOf("x")))
        assertTrue(registro.erros.isEmpty())
    }

    @Test(timeout = 20_000)
    fun `saida enorme linha a linha`() {
        val linhas = 50_000
        val conteudo = buildString { repeat(linhas) { append("linha número ").append(it).append('\n') } }

        val saida = executor(sempre(ProcessoFalso(saida = texto(conteudo)))).executar(arrayOf("dumpsys"))

        val partes = saida.split('\n')
        assertEquals(linhas, partes.size)
        assertEquals("linha número 0", partes.first())
        assertEquals("linha número ${linhas - 1}", partes.last())
    }

    @Test(timeout = 20_000)
    fun `le as duas saidas ao mesmo tempo e nao trava com erro grande`() {
        // Canos pequenos: quem só lesse a saída padrão travaria com o escritor preso na de erro.
        val escritaDoErro = PipedOutputStream()
        val erro = PipedInputStream(escritaDoErro, 4096)
        val escritaDaSaida = PipedOutputStream()
        val saida = PipedInputStream(escritaDaSaida, 4096)
        val escritor = thread(isDaemon = true) {
            escritaDoErro.use { e -> repeat(20_000) { e.write("linha de erro $it com texto para encher o cano\n".toByteArray()) } }
            escritaDaSaida.use { it.write("pronto\n".toByteArray()) }
        }

        val resultado = executor(sempre(ProcessoFalso(saida = saida, erro = erro))).executar(arrayOf("dumpsys"))

        assertEquals("pronto", resultado)
        escritor.join(5_000)
        assertTrue(registro.erros.isEmpty())
    }

    @Test(timeout = 20_000)
    fun `nao espera a saida de erro para sempre depois que a padrao fecha`() {
        // A saída de erro fica aberta (ex.: um filho em segundo plano herdou o cano).
        val escritaDoErro = PipedOutputStream()
        val erro = PipedInputStream(escritaDoErro)
        val processo = ProcessoFalso(saida = texto("ok\n"), erro = erro, aoLiberar = { escritaDoErro.close() })

        val inicio = System.nanoTime()
        val saida = executor(sempre(processo), limiteDoErroMs = 200).executar(arrayOf("erro-preso"))
        val ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio)

        assertEquals("ok", saida)
        assertTrue("demorou $ms ms", ms < 3_000)
        assertEquals(1, processo.liberacoes.get())
        // Fechado o cano, a thread que lia a saída de erro termina.
        assertTrue(threadsVivas("erro-preso").toString(), esperarAte { threadsVivas("erro-preso").isEmpty() })
    }

    @Test
    fun `falha lendo a saida devolve vazio, registra e libera`() {
        val quebrada = object : InputStream() {
            override fun read(): Int = throw IOException("cano quebrado")
        }
        val processo = ProcessoFalso(saida = quebrada)

        assertEquals("", executor(sempre(processo)).executar(arrayOf("x")))
        assertEquals(1, registro.erros.size)
        assertEquals(1, processo.liberacoes.get())
    }

    @Test
    fun `falha esperando devolve vazio, registra e libera`() {
        val processo = ProcessoFalso(saida = texto("talvez\n"), codigo = { throw IllegalStateException("binder morreu") })

        assertEquals("", executor(sempre(processo)).executar(arrayOf("x")))
        assertEquals(1, registro.erros.size)
        assertEquals(1, processo.liberacoes.get())
    }

    // ── executar: criação do processo ──

    @Test
    fun `sem binder devolve vazio, registra e nao tenta de novo`() {
        val iniciador = sempre(null)

        assertEquals("", executor(iniciador).executar(arrayOf("id")))
        assertEquals(1, iniciador.chamadas.size)
        assertTrue(pausas.isEmpty())
        assertEquals(1, registro.avisos.size + registro.erros.size)
    }

    @Test
    fun `falha passageira tenta de novo pegando o binder outra vez`() {
        // Cada chamada ao iniciador é um binder lido de novo; os dois primeiros estão mortos.
        val iniciador = IniciadorFalso(
            { throw FalhaPassageira(IOException("binder 1 morto")) },
            { throw FalhaPassageira(IOException("binder 2 morto")) },
            { ProcessoFalso(saida = texto("binder 3\n")) },
        )

        assertEquals("binder 3", executor(iniciador).executar(arrayOf("id")))
        assertEquals(3, iniciador.chamadas.size)
        assertEquals(listOf(500L, 500L), pausas)
        assertTrue(registro.erros.isEmpty())
    }

    @Test
    fun `tres falhas passageiras desistem`() {
        val iniciador = IniciadorFalso({ throw FalhaPassageira(IOException("morto")) })

        assertEquals("", executor(iniciador).executar(arrayOf("id")))
        assertEquals(3, iniciador.chamadas.size)
        assertEquals(listOf(500L, 500L), pausas)
        assertEquals(1, registro.erros.size)
    }

    @Test
    fun `binder sumido depois de falha passageira ainda conta como tentativa`() {
        val iniciador = IniciadorFalso(
            { throw FalhaPassageira(IOException("morto")) },
            { null },
            { ProcessoFalso(saida = texto("voltou")) },
        )

        assertEquals("voltou", executor(iniciador).executar(arrayOf("id")))
        assertEquals(3, iniciador.chamadas.size)
    }

    @Test
    fun `falha definitiva nao tenta de novo`() {
        val iniciador = IniciadorFalso({ throw SecurityException("Permission Denial") })

        assertEquals("", executor(iniciador).executar(arrayOf("id")))
        assertEquals(1, iniciador.chamadas.size)
        assertTrue(pausas.isEmpty())
        assertEquals(1, registro.erros.size)
    }

    // ── emSegundoPlano ──

    @Test(timeout = 20_000)
    fun `segundo plano volta na hora e entrega linhas, erros e um terminou`() {
        val escritaDaSaida = PipedOutputStream()
        val escritaDoErro = PipedOutputStream()
        val processo = ProcessoFalso(
            saida = PipedInputStream(escritaDaSaida),
            erro = PipedInputStream(escritaDoErro),
            codigo = { 7 },
        )
        val ouvinte = OuvinteGravador()

        // Nada foi escrito ainda: se a chamada esperasse o processo, o teste travaria aqui.
        executor(sempre(processo)).emSegundoPlano(arrayOf("getevent", "-l"), ouvinte)
        escritaDaSaida.write("a\nb\n".toByteArray())
        escritaDoErro.write("e1\n".toByteArray())
        escritaDaSaida.close()
        escritaDoErro.close()

        ouvinte.aguardarFim()
        assertEquals(listOf("a", "b"), ouvinte.linhas)
        assertEquals(listOf("e1"), ouvinte.erros)
        assertEquals(listOf(7), ouvinte.codigos)
        assertEquals("fecharEntrada", processo.eventos.first())
        assertTrue(esperarAte { processo.liberacoes.get() == 1 })
    }

    @Test(timeout = 20_000)
    fun `segundo plano sem binder chama terminou -1 e nada mais`() {
        val ouvinte = OuvinteGravador()

        executor(sempre(null)).emSegundoPlano(arrayOf("getevent"), ouvinte)

        ouvinte.aguardarFim()
        assertEquals(listOf(-1), ouvinte.codigos)
        assertTrue(ouvinte.linhas.isEmpty() && ouvinte.erros.isEmpty())
        assertEquals(1, registro.avisos.size + registro.erros.size)
    }

    @Test(timeout = 20_000)
    fun `segundo plano que nao pode ser criado chama terminou -1`() {
        val ouvinte = OuvinteGravador()

        executor(IniciadorFalso({ throw SecurityException("Permission Denial") }))
            .emSegundoPlano(arrayOf("getevent"), ouvinte)

        ouvinte.aguardarFim()
        assertEquals(listOf(-1), ouvinte.codigos)
        assertTrue(ouvinte.linhas.isEmpty() && ouvinte.erros.isEmpty())
        assertEquals(1, registro.erros.size)
    }

    @Test(timeout = 20_000)
    fun `terminou vem uma vez so mesmo com ouvinte que lanca`() {
        val processo = ProcessoFalso(saida = texto("1\n2\n3\n"), erro = texto("e\n"))
        val ouvinte = object : OuvinteGravador() {
            override fun linha(texto: String) {
                super.linha(texto)
                throw IllegalStateException("ouvinte quebrado")
            }
        }

        executor(sempre(processo)).emSegundoPlano(arrayOf("x"), ouvinte)

        ouvinte.aguardarFim()
        Thread.sleep(200)
        assertEquals(listOf(0), ouvinte.codigos)
        assertEquals(listOf("1", "2", "3"), ouvinte.linhas)
        assertEquals(listOf("e"), ouvinte.erros)
        assertEquals(1, processo.liberacoes.get())
    }

    @Test(timeout = 20_000)
    fun `segundo plano que falha esperando chama terminou -1 uma vez e libera`() {
        val processo = ProcessoFalso(codigo = { throw IllegalStateException("binder morreu") })
        val ouvinte = OuvinteGravador()

        executor(sempre(processo)).emSegundoPlano(arrayOf("x"), ouvinte)

        ouvinte.aguardarFim()
        Thread.sleep(200)
        assertEquals(listOf(-1), ouvinte.codigos)
        assertEquals(1, processo.liberacoes.get())
    }

    // ── falsos ──

    private class IniciadorFalso(vararg respostas: () -> ProcessoRemoto?) : IniciadorDeProcesso {
        private val respostas = respostas.toList()
        val chamadas = CopyOnWriteArrayList<List<String>>()

        /** A n-ésima chamada usa a n-ésima resposta; passou do fim, repete a última. */
        override fun iniciar(comando: Array<String>): ProcessoRemoto? {
            chamadas += comando.toList()
            return respostas[minOf(chamadas.size, respostas.size) - 1]()
        }
    }

    private class ProcessoFalso(
        private val saida: InputStream = texto(""),
        private val erro: InputStream = texto(""),
        private val codigo: () -> Int = { 0 },
        private val aoLiberar: () -> Unit = {},
    ) : ProcessoRemoto {
        val eventos = CopyOnWriteArrayList<String>()
        val liberacoes = AtomicInteger()

        override fun fecharEntrada() {
            eventos += "fecharEntrada"
        }

        override fun saida(): InputStream = saida.also { eventos += "saida" }

        override fun erro(): InputStream = erro.also { eventos += "erro" }

        override fun esperar(): Int = codigo()

        override fun liberar() {
            liberacoes.incrementAndGet()
            aoLiberar()
        }
    }

    private class RegistroFalso : Registro {
        val avisos = CopyOnWriteArrayList<String>()
        val erros = CopyOnWriteArrayList<String>()

        override fun aviso(mensagem: String) {
            avisos += mensagem
        }

        override fun erro(mensagem: String, causa: Throwable?) {
            erros += mensagem
        }
    }

    private open class OuvinteGravador : Shell.Ouvinte {
        val linhas = CopyOnWriteArrayList<String>()
        val erros = CopyOnWriteArrayList<String>()
        val codigos = CopyOnWriteArrayList<Int>()
        private val fim = CountDownLatch(1)

        override fun linha(texto: String) {
            linhas += texto
        }

        override fun erro(texto: String) {
            erros += texto
        }

        override fun terminou(codigo: Int) {
            codigos += codigo
            fim.countDown()
        }

        fun aguardarFim() = assertTrue("terminou não veio", fim.await(5, TimeUnit.SECONDS))
    }

    private companion object {
        fun texto(conteudo: String): InputStream = ByteArrayInputStream(conteudo.toByteArray())

        fun threadsVivas(trecho: String) =
            Thread.getAllStackTraces().keys.filter { it.isAlive && trecho in it.name }

        fun esperarAte(condicao: () -> Boolean): Boolean {
            val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (System.nanoTime() < limite) {
                if (condicao()) return true
                Thread.sleep(20)
            }
            return condicao()
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
