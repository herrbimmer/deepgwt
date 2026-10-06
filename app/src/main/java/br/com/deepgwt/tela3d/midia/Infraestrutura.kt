package br.com.deepgwt.tela3d.midia

import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Registro de diagnóstico do módulo. Nunca lança: em testes JVM o `Log` do Android não existe e o
 * erro é engolido.
 */
internal object Diario {
    const val ETIQUETA = "DeepGWT.Tela3D.Midia"

    fun info(mensagem: String) {
        try {
            Log.i(ETIQUETA, mensagem)
        } catch (_: RuntimeException) {
        }
    }

    fun aviso(mensagem: String, erro: Throwable? = null) {
        try {
            if (erro != null) Log.w(ETIQUETA, mensagem, erro) else Log.w(ETIQUETA, mensagem)
        } catch (_: RuntimeException) {
        }
    }
}

/**
 * Uma thread de fundo própria com agendamento. Serve de laço periódico e de executor de tarefas
 * atrasadas (por exemplo a soltura da tecla HID). Encerra de verdade em [parar]: nenhuma thread
 * sobra depois que a tela morre.
 */
internal class LacoDeFundo(private val nome: String) {
    private var executor: ScheduledExecutorService? = null
    private var tarefa: ScheduledFuture<*>? = null

    @Synchronized
    private fun garantirExecutor(): ScheduledExecutorService {
        val atual = executor
        if (atual != null && !atual.isShutdown) return atual
        val novo = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, nome).apply { isDaemon = true }
        }
        executor = novo
        return novo
    }

    /** Roda [acao] a cada [periodoMs] (espera entre o fim de uma execução e o início da outra). */
    @Synchronized
    fun iniciar(periodoMs: Long, atrasoInicialMs: Long = 0L, acao: () -> Unit) {
        tarefa?.cancel(false)
        tarefa = garantirExecutor().scheduleWithFixedDelay(
            { executarSemFalhar(acao) },
            atrasoInicialMs,
            periodoMs,
            TimeUnit.MILLISECONDS,
        )
    }

    /** Roda [acao] uma vez, daqui a [atrasoMs]. Devolve falso se o laço já foi encerrado. */
    @Synchronized
    fun agendar(atrasoMs: Long, acao: () -> Unit): Boolean =
        try {
            garantirExecutor().schedule({ executarSemFalhar(acao) }, atrasoMs, TimeUnit.MILLISECONDS)
            true
        } catch (_: RejectedExecutionException) {
            false
        }

    @Synchronized
    fun parar() {
        tarefa?.cancel(false)
        tarefa = null
        executor?.shutdownNow()
        executor = null
    }

    private fun executarSemFalhar(acao: () -> Unit) {
        try {
            acao()
        } catch (e: Throwable) {
            Diario.aviso("falha no laço $nome", e)
        }
    }
}

/**
 * Cache FIFO de tamanho fixo: ao inserir uma chave nova com o cache cheio, sai a mais antiga.
 * Não é thread-safe (usar sempre da mesma thread).
 */
internal class CacheFifo<K : Any, V : Any>(private val maximo: Int) {
    private val mapa = LinkedHashMap<K, V>()

    val tamanho: Int get() = mapa.size

    operator fun get(chave: K): V? = mapa[chave]

    fun put(chave: K, valor: V) {
        if (!mapa.containsKey(chave) && mapa.size >= maximo) {
            val maisAntiga = mapa.keys.iterator().next()
            mapa.remove(maisAntiga)
        }
        mapa[chave] = valor
    }

    fun limpar() = mapa.clear()
}

/**
 * Cache de tamanho fixo que, ao tentar inserir uma chave nova com o cache cheio, é esvaziado por
 * inteiro. Não é thread-safe.
 */
internal class CacheQueEsvazia<K : Any, V : Any>(private val maximo: Int) {
    private val mapa = HashMap<K, V>()

    val tamanho: Int get() = mapa.size

    operator fun get(chave: K): V? = mapa[chave]

    fun put(chave: K, valor: V) {
        if (!mapa.containsKey(chave) && mapa.size >= maximo) mapa.clear()
        mapa[chave] = valor
    }

    fun limpar() = mapa.clear()
}

/**
 * Impede repetir a mesma operação por [esperaMs] depois de uma falha. Thread-safe (os pedidos
 * nascem na principal, mas o relógio e as falhas podem chegar de outras threads).
 */
internal class RecuoPorChave(
    private val esperaMs: Long,
    private val agora: () -> Long,
    private val limiteDeChaves: Int = 128,
) {
    private val falhas = HashMap<String, Long>()

    @Synchronized
    fun podeTentar(chave: String): Boolean {
        val quando = falhas[chave] ?: return true
        if (agora() - quando >= esperaMs) {
            falhas.remove(chave)
            return true
        }
        return false
    }

    @Synchronized
    fun registrarFalha(chave: String) {
        if (falhas.size >= limiteDeChaves && !falhas.containsKey(chave)) {
            val t = agora()
            falhas.entries.removeAll { t - it.value >= esperaMs }
            if (falhas.size >= limiteDeChaves) falhas.clear()
        }
        falhas[chave] = agora()
    }

    @Synchronized
    fun esquecer(chave: String) {
        falhas.remove(chave)
    }
}

/**
 * Política de religação a um serviço da fabricante (defeito 5 da especificação): se há tentativa
 * pendente, espera; se o sistema recusou a ligação, só tenta de novo depois de [intervaloAposRecusaMs];
 * uma tentativa que nunca conecta expira em [esperaPendenteMs].
 */
internal class PoliticaDeReligacao(
    private val esperaPendenteMs: Long = 30_000L,
    private val intervaloAposRecusaMs: Long = 10_000L,
) {
    private var pendenteDesde: Long? = null
    private var ultimaRecusa: Long? = null

    /** Verdadeiro se este é o momento de pedir uma ligação nova ao sistema. */
    fun deveLigar(agora: Long, ligado: Boolean): Boolean {
        if (ligado) return false
        val pendente = pendenteDesde
        if (pendente != null && agora - pendente < esperaPendenteMs) return false
        val recusa = ultimaRecusa
        if (recusa != null && agora - recusa < intervaloAposRecusaMs) return false
        return true
    }

    /** Registra o resultado de `bindService`: aceito deixa a tentativa pendente; recusado inicia o recuo. */
    fun aoTentar(agora: Long, aceito: Boolean) {
        if (aceito) {
            pendenteDesde = agora
            ultimaRecusa = null
        } else {
            pendenteDesde = null
            ultimaRecusa = agora
        }
    }

    fun aoConectar() {
        pendenteDesde = null
        ultimaRecusa = null
    }

    /** O serviço caiu: a próxima tentativa pode ser imediata. */
    fun aoDesconectar() {
        pendenteDesde = null
    }
}
