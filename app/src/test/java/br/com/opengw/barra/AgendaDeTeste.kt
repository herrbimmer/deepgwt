package br.com.opengw.barra

/**
 * A "thread principal" dos testes: tudo roda na hora, e as tarefas com atraso ficam numa fila que o
 * teste avança com [avancar] (relógio falso).
 */
internal class AgendaDeTeste(var agora: Long = 0L) : ThreadPrincipal.Agenda {
    private data class Pendente(val quando: Long, val ordem: Long, val tarefa: Runnable)

    private val fila = ArrayList<Pendente>()
    private var contador = 0L

    override fun estaNaPrincipal() = true
    override fun postar(tarefa: Runnable) = tarefa.run()
    override fun postarComAtraso(atrasoMs: Long, tarefa: Runnable) {
        fila += Pendente(agora + atrasoMs.coerceAtLeast(0L), contador++, tarefa)
    }

    override fun cancelar(tarefa: Runnable) {
        fila.removeAll { it.tarefa === tarefa }
    }

    override fun agoraMs(): Long = agora

    /** Anda o relógio rodando, em ordem, o que vencer no caminho. */
    fun avancar(ms: Long) {
        val fim = agora + ms
        while (true) {
            val proxima = fila.filter { it.quando <= fim }.minWithOrNull(compareBy({ it.quando }, { it.ordem })) ?: break
            fila.remove(proxima)
            agora = proxima.quando
            proxima.tarefa.run()
        }
        agora = fim
    }

    val pendentes: Int get() = fila.size
}
