package br.com.opengw.telas

/** Uma linha `taskId=<n>: <pacote>/<atividade> …` da lista de pilhas. */
internal class LinhaDeTarefa(
    val id: Int,
    val pacote: String,
    val atividade: String,
    /** Os `bounds=[…]` da própria linha, se vieram. */
    val limites: IntArray?,
)

/** Um cabeçalho `Stack id=<n> … displayId=<d>` (ou `RootTask id=`) e as tarefas sob ele. */
internal class Pilha(
    val id: Int,
    val display: Int,
    /** Os limites do cabeçalho; sem eles, a tela cheia do display (2-F9). */
    val limites: IntArray,
    /** O primeiro `mWindowingMode=` do bloco, sem `}` no fim; null se não veio. */
    val modo: String?,
    val tarefas: List<LinhaDeTarefa>,
)

/** Outra tarefa achada (2-E8, 2-E9). */
internal data class OutraTarefa(val pacote: String, val atividade: String, val display: Int, val tarefa: Int)

/**
 * A saída de `am stack list` já lida (parte 2, seção E). Sem Android: o tamanho de tela cheia de um
 * display entra por função.
 *
 * Lê o formato do Android 9 (`Stack id=`) e, pela decisão 6, também o `RootTask id=` do Android 10+.
 */
internal class ListaDePilhas private constructor(
    private val linhas: List<String>,
    val pilhas: List<Pilha>,
) {

    /** E11: o pacote da primeira tarefa da pilha. */
    fun pacoteDaPilha(pilha: Int): String? = pilhas.firstOrNull { it.id == pilha }?.tarefas?.firstOrNull()?.pacote

    /** E3: todas as tarefas do pacote exato, cada uma com os limites da pilha. */
    fun tarefasDoPacote(pacote: String): List<TarefaNaTela> {
        val achadas = ArrayList<TarefaNaTela>()
        for (p in pilhas) for (t in p.tarefas) {
            if (t.pacote == pacote) achadas += TarefaNaTela(t.id, p.id, p.display, p.limites.copyOf())
        }
        return achadas
    }

    /** E3: a primeira tarefa do pacote exato no display. */
    fun tarefaDoPacoteNoDisplay(pacote: String, display: Int): TarefaNaTela? =
        tarefasDoPacote(pacote).firstOrNull { it.display == display }

    /** E2: a primeira tarefa do pacote exato; limites da linha da tarefa, senão os da pilha. */
    fun tarefaDoPacote(pacote: String): TarefaNaTela? {
        for (p in pilhas) for (t in p.tarefas) {
            if (t.pacote == pacote) return TarefaNaTela(t.id, p.id, p.display, (t.limites ?: p.limites).copyOf())
        }
        return null
    }

    /** As tarefas (com limites da pilha) cujo pacote passa no filtro. */
    fun tarefasOnde(filtro: (String) -> Boolean): List<TarefaNaTela> {
        val achadas = ArrayList<TarefaNaTela>()
        for (p in pilhas) for (t in p.tarefas) {
            if (filtro(t.pacote)) achadas += TarefaNaTela(t.id, p.id, p.display, p.limites.copyOf())
        }
        return achadas
    }

    /**
     * E4 (parte da lista): cada linha com `displayId=<n>` muda o display corrente; a primeira tarefa
     * com o display corrente = [display] dá o pacote (sem olhar `visible=`).
     */
    fun pacoteNoTopo(display: Int): String? {
        var corrente: Int? = null
        for (linha in linhas) {
            DISPLAY.find(linha)?.let { corrente = it.groupValues[1].toIntOrNull() }
            if (corrente == display) TAREFA.find(linha)?.let { return it.groupValues[2] }
        }
        return null
    }

    /** E6: a primeira pilha do display com tarefa do pacote exato; modo ausente → `unknown`. */
    fun pilhaDoPacoteNoDisplay(pacote: String, display: Int): PilhaComModo? {
        val p = pilhas.firstOrNull { it.display == display && it.tarefas.any { t -> t.pacote == pacote } } ?: return null
        return PilhaComModo(p.id, p.modo ?: "unknown")
    }

    /** E7: quantas tarefas há sob a pilha. */
    fun tarefasNaPilha(pilha: Int): Int = pilhas.firstOrNull { it.id == pilha }?.tarefas?.size ?: 0

    /** E8: a primeira tarefa da pilha com pacote diferente do dado. */
    fun outraTarefaDaPilha(pilha: Int, pacote: String): OutraTarefa? {
        val p = pilhas.firstOrNull { it.id == pilha } ?: return null
        val t = p.tarefas.firstOrNull { it.pacote != pacote } ?: return null
        return OutraTarefa(t.pacote, t.atividade, p.display, t.id)
    }

    /** E9: a primeira tarefa do display que não é a SystemUI, nem o próprio app, nem espelhamento. */
    fun primeiraTarefaComum(display: Int, pacoteDoApp: String): OutraTarefa? {
        for (p in pilhas) {
            if (p.display != display) continue
            for (t in p.tarefas) {
                if (t.pacote == PacotesDaCentral.SYSTEMUI || t.pacote == pacoteDoApp) continue
                if (RegrasDoEspelhamento.pacoteDeEspelhamento(t.pacote) != null) continue
                return OutraTarefa(t.pacote, t.atividade, p.display, t.id)
            }
        }
        return null
    }

    /** E12: os ids das pilhas do display, na ordem da lista. */
    fun pilhasDoDisplay(display: Int): List<Int> = pilhas.filter { it.display == display }.map { it.id }

    data class PilhaComModo(val id: Int, val modo: String)

    companion object {
        private val CABECALHO = Regex("""\b(?:Stack|RootTask) id=(\d+)""")
        private val DISPLAY = Regex("""displayId=(\d+)""")
        private val LIMITES = Regex("""bounds=\[(\d+),(\d+)]\[(\d+),(\d+)]""", RegexOption.IGNORE_CASE)
        private val TAREFA = Regex("""taskId=(\d+):\s*([A-Za-z0-9._]+)/(\S*)""")
        private val MODO = Regex("""mWindowingMode=(\S+)""")

        val VAZIA = ListaDePilhas(emptyList(), emptyList())

        /** Lê o texto; [telaCheia] dá `[0, 0, L, A]` de um display (pilha sem limites no cabeçalho). */
        fun ler(texto: String, telaCheia: (Int) -> IntArray): ListaDePilhas {
            val linhas = texto.lines()
            val pilhas = ArrayList<Pilha>()

            var id = -1
            var display = -1
            var limites: IntArray? = null
            var modo: String? = null
            var tarefas = ArrayList<LinhaDeTarefa>()
            var aberta = false

            fun fechar() {
                if (aberta) pilhas += Pilha(id, display, limites ?: telaCheia(display), modo, tarefas)
                aberta = false
            }

            for (linha in linhas) {
                val cabecalho = CABECALHO.find(linha)
                val doDisplay = DISPLAY.find(linha)
                if (cabecalho != null && doDisplay != null) {
                    fechar()
                    id = cabecalho.groupValues[1].toIntOrNull() ?: -1
                    display = doDisplay.groupValues[1].toIntOrNull() ?: -1
                    limites = limitesDaLinha(linha)
                    modo = null
                    tarefas = ArrayList()
                    aberta = id >= 0 && display >= 0
                    if (aberta) MODO.find(linha)?.let { modo = it.groupValues[1].trimEnd('}') }
                    continue
                }
                if (!aberta) continue
                val tarefa = TAREFA.find(linha)
                if (tarefa != null) {
                    val tid = tarefa.groupValues[1].toIntOrNull()
                    if (tid != null) {
                        tarefas += LinhaDeTarefa(tid, tarefa.groupValues[2], tarefa.groupValues[3], limitesDaLinha(linha))
                    }
                    continue
                }
                if (modo == null) MODO.find(linha)?.let { modo = it.groupValues[1].trimEnd('}') }
            }
            fechar()
            return ListaDePilhas(linhas, pilhas)
        }

        /** O primeiro `bounds=[a,b][c,d]` (ou `mbounds=`) com inteiros não negativos da linha. */
        private fun limitesDaLinha(linha: String): IntArray? {
            for (m in LIMITES.findAll(linha)) {
                val numeros = m.groupValues.drop(1).map { it.toIntOrNull() }
                if (numeros.all { it != null }) return IntArray(4) { numeros[it]!! }
            }
            return null
        }
    }
}
