package br.com.deepgwt.tela3d.janelas

/**
 * Os passos básicos sobre uma tarefa do Android. Cada um já esgota as suas rotas (APIs públicas,
 * APIs ocultas, serviço de sistema e, por último, o `am stack` do módulo de telas) e nunca lança.
 */
internal interface PrimitivasDeTarefa {
    /** C35: o número da tarefa do pacote, ou −1. */
    fun acharTarefa(pacote: String): Int

    /** C36: redimensiona; verdadeiro se alguma rota aceitou. */
    fun redimensionar(tarefa: Int, limites: Retangulo): Boolean

    /** C37: levanta sem animação; verdadeiro se o sistema aceitou. */
    fun levantar(tarefa: Int): Boolean

    /** C38: marca "sempre no topo"; falso se não suportado ou se falhou. */
    fun marcarNoTopo(tarefa: Int): Boolean

    /** C53: remove a tarefa. */
    fun remover(tarefa: Int)
}

/** O que "levantar" achou: o número da tarefa (−1 se não achou) e se o sistema a levantou. */
internal data class ResultadoDoLevantar(val tarefa: Int, val levantou: Boolean)

/**
 * As combinações de passos que a lógica de janelas pede (achar → redimensionar → no topo, achar →
 * remover…). Síncronas e puras sobre [PrimitivasDeTarefa]; quem as chama decide a thread.
 */
internal class OperacoesDeTarefa(
    private val primitivas: PrimitivasDeTarefa,
    private val diario: Diario = Diario.Mudo,
) {

    /** O número guardado, se válido; senão procura por pacote. */
    private fun descobrir(pacote: String, guardado: Int): Int =
        if (guardado >= 0) guardado else primitivas.acharTarefa(pacote)

    /** C10 passo 4: remove as tarefas já existentes do pacote (até [maximo] repetições). */
    fun removerTodasDoPacote(pacote: String, maximo: Int = MAXIMO_DE_REMOCOES) {
        repeat(maximo) {
            val id = primitivas.acharTarefa(pacote)
            if (id < 0) return
            primitivas.remover(id)
        }
    }

    /** C13: remove a tarefa do pacote (o número guardado vale se ≥ 0). */
    fun fechar(pacote: String, guardado: Int) {
        if (pacote.isEmpty()) return
        val id = descobrir(pacote, guardado)
        if (id >= 0) primitivas.remover(id)
    }

    /**
     * C12/C18: acha a tarefa (a guardada, se válida; senão por pacote), redimensiona para [limites] e
     * marca "sempre no topo". Devolve o número achado (−1 se não achou). Usar o número guardado poupa
     * uma consulta ao sistema em cada uma das 16 execuções da reposição.
     */
    fun fixar(pacote: String, guardado: Int, limites: Retangulo): Int {
        val id = descobrir(pacote, guardado)
        if (id < 0) return -1
        primitivas.redimensionar(id, limites)
        primitivas.marcarNoTopo(id)
        return id
    }

    /** C29: acha (guardado ou por pacote) e levanta. Não achar ou o sistema recusar = `levantou` falso. */
    fun levantar(pacote: String, guardado: Int): ResultadoDoLevantar {
        val id = descobrir(pacote, guardado)
        if (id < 0) return ResultadoDoLevantar(-1, false)
        return ResultadoDoLevantar(id, primitivas.levantar(id))
    }

    /** C14: acha (guardado ou por pacote) e redimensiona. Devolve o número (−1 se não achou). */
    fun redimensionarDoPacote(pacote: String, guardado: Int, limites: Retangulo): Int {
        val id = descobrir(pacote, guardado)
        if (id >= 0) primitivas.redimensionar(id, limites)
        return id
    }

    /** C38: "fixar no topo" um lado: marca no topo e, se isso falhou, levanta. Devolve o número (−1 se não achou). */
    fun fixarNoTopo(pacote: String, guardado: Int): Int {
        val id = descobrir(pacote, guardado)
        if (id < 0) return -1
        if (!primitivas.marcarNoTopo(id)) primitivas.levantar(id)
        return id
    }

    /** C40: procura a tarefa do pacote e, se achou, a levanta. */
    fun levantarPorPacote(pacote: String): ResultadoDoLevantar {
        val id = primitivas.acharTarefa(pacote)
        diario.info("projeção: lookup pacote=$pacote tarefa=$id")
        if (id < 0) return ResultadoDoLevantar(-1, false)
        return ResultadoDoLevantar(id, primitivas.levantar(id))
    }

    companion object {
        const val MAXIMO_DE_REMOCOES = 4
    }
}

/**
 * C37 + D5: não levanta duas vezes a mesma tarefa em menos de 350 ms, mas só conta as tentativas que o
 * sistema ACEITOU (uma recusa não "gasta" a janela de repetição).
 */
internal class GuardaDeLevantar(private val relogio: Relogio) {
    private var ultimaTarefa = -1
    private var ultimoInstante = 0L
    private var temUltima = false

    /** A mesma tarefa foi levantada com sucesso há menos de [JANELA_MS]? */
    @Synchronized
    fun repetida(tarefa: Int): Boolean =
        temUltima && tarefa == ultimaTarefa && relogio.agoraMs() - ultimoInstante < JANELA_MS

    @Synchronized
    fun registrarSucesso(tarefa: Int) {
        ultimaTarefa = tarefa
        ultimoInstante = relogio.agoraMs()
        temUltima = true
    }

    /** C30: ao desligar "chrome por cima" a memória da última tarefa levantada é zerada. */
    @Synchronized
    fun esquecer() {
        temUltima = false
        ultimaTarefa = -1
    }

    companion object {
        const val JANELA_MS = 350L
    }
}

/** C38: lembra que "sempre no topo" não é suportado depois da primeira falha (não volta a verdadeiro). */
internal class SuporteAoNoTopo {
    @Volatile
    var suportado: Boolean = true
        private set

    fun marcarNaoSuportado() {
        suportado = false
    }
}
