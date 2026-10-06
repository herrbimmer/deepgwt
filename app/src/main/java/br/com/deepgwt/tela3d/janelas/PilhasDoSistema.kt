package br.com.deepgwt.tela3d.janelas

import br.com.deepgwt.telas.ListaDePilhas

/**
 * Reserva de achar, redimensionar e levantar tarefas pelo `am` do shell privilegiado (Shizuku), como
 * faz o módulo de telas: `am stack list`, `am stack resize <pilha> l t r b` e o levantar. Só entra
 * quando as APIs ocultas do Android falham (na central, o app comum costuma não ter permissão).
 *
 * Roda comando de shell, então só deve ser chamada fora da thread principal.
 *
 * @param rodar executa o comando (argv exato) e devolve a saída; vazio se o Shizuku não responde
 */
internal class PilhasDoSistema(
    private val rodar: (Array<String>) -> String,
    private val relogio: Relogio,
    private val diario: Diario = Diario.Mudo,
) {
    private var cache: ListaDePilhas? = null
    private var cacheEm = Long.MIN_VALUE

    /** A lista de pilhas (guardada por [VALIDADE_DO_CACHE_MS]; qualquer mudança a invalida). */
    @Synchronized
    fun lista(): ListaDePilhas {
        val agora = relogio.agoraMs()
        cache?.let { if (agora - cacheEm < VALIDADE_DO_CACHE_MS) return it }
        val saida = rodar(arrayOf("am", "stack", "list"))
        val nova = ListaDePilhas.ler(saida) { intArrayOf(0, 0, TELA_LARGURA, TELA_ALTURA) }
        cache = nova
        cacheEm = relogio.agoraMs()
        return nova
    }

    @Synchronized
    private fun invalidar() {
        cache = null
    }

    /** O número da tarefa do pacote no display principal, ou −1. */
    fun acharTarefa(pacote: String): Int =
        if (pacote.isEmpty()) -1 else lista().tarefaDoPacoteNoDisplay(pacote, DISPLAY_PRINCIPAL)?.tarefa ?: -1

    /** `am stack resize` da pilha que contém a tarefa; verdadeiro se achou a pilha e o comando não falhou. */
    fun redimensionar(tarefa: Int, limites: Retangulo): Boolean {
        val pilha = pilhaDaTarefa(tarefa) ?: return false
        val saida = rodar(
            arrayOf("am", "stack", "resize", pilha.toString(), limites.esquerda.toString(), limites.topo.toString(), limites.direita.toString(), limites.fundo.toString()),
        )
        invalidar()
        val ok = !falhou(saida)
        if (ok) diario.info("tarefa $tarefa fixada via am stack resize (pilha $pilha)")
        return ok
    }

    /** Levanta a tarefa: `am stack move-task-to-front`; se o comando não existe, `am stack move-task … true` na própria pilha. */
    fun levantar(tarefa: Int): Boolean {
        // Sem a tarefa na lista (ou sem Shizuku, que devolve saída vazia) não há como saber que deu certo.
        val pilha = pilhaDaTarefa(tarefa) ?: return false
        val primeira = rodar(arrayOf("am", "stack", "move-task-to-front", tarefa.toString()))
        invalidar()
        if (!falhou(primeira)) return true
        val segunda = rodar(arrayOf("am", "stack", "move-task", tarefa.toString(), pilha.toString(), "true"))
        invalidar()
        return !falhou(segunda)
    }

    private fun pilhaDaTarefa(tarefa: Int): Int? {
        if (tarefa < 0) return null
        for (p in lista().pilhas) if (p.tarefas.any { it.id == tarefa }) return p.id
        return null
    }

    /** O Shizuku mostra o erro do `am` na saída: `Exception`, `Error`, `Unknown command` ou o `usage`. */
    private fun falhou(saida: String): Boolean =
        "Exception" in saida || "Error" in saida || "Unknown command" in saida || "usage:" in saida.lowercase()

    companion object {
        const val DISPLAY_PRINCIPAL = 0
        const val VALIDADE_DO_CACHE_MS = 250L
        private const val TELA_LARGURA = 1920
        private const val TELA_ALTURA = 720
    }
}
