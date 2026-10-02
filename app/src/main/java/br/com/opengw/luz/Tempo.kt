package br.com.opengw.luz

import kotlinx.coroutines.delay
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Relógio monotônico em ms e a espera. Nos testes, um relógio falso anda sem esperar de verdade. */
internal interface Relogio {
    fun agora(): Long
    suspend fun esperar(ms: Long)
}

internal object RelogioDoSistema : Relogio {
    override fun agora(): Long = System.nanoTime() / 1_000_000
    override suspend fun esperar(ms: Long) = delay(ms)
}

internal fun interface Agendamento {
    fun cancelar()
}

/**
 * Onde a máquina de estados de uma conexão roda: uma tarefa de cada vez, na ordem. As esperas
 * (prazo, reconexão) também entram por aqui.
 */
internal interface Agendador {
    fun executar(tarefa: () -> Unit)
    fun agendar(atrasoMs: Long, tarefa: () -> Unit): Agendamento
}

/** Uma thread só (daemon) por agendador. Exceção numa tarefa vai para o registro, não derruba nada. */
internal class AgendadorDeThread(nome: String, private val registro: Registro) : Agendador {

    private val executor = ScheduledThreadPoolExecutor(1) { tarefa ->
        Thread(tarefa, nome).apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }

    private fun protegido(tarefa: () -> Unit) = Runnable {
        try {
            tarefa()
        } catch (e: Throwable) {
            registro.anotar("falha numa tarefa: $e")
        }
    }

    override fun executar(tarefa: () -> Unit) {
        executor.execute(protegido(tarefa))
    }

    override fun agendar(atrasoMs: Long, tarefa: () -> Unit): Agendamento {
        val futuro = executor.schedule(protegido(tarefa), atrasoMs, TimeUnit.MILLISECONDS)
        return Agendamento { futuro.cancel(false) }
    }
}

/** Log do módulo. O Android grava no logcat; os testes calam ou guardam. */
internal interface Registro {
    fun anotar(texto: String)

    /** Cada quadro enviado (hex), com a chave da conexão. */
    fun quadro(conexao: String, hex: String) = anotar("quadro [$conexao] $hex")

    companion object {
        val MUDO: Registro = object : Registro {
            override fun anotar(texto: String) = Unit
        }
    }
}
