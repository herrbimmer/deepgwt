package br.com.deepgwt.tela3d.janelas

/** Resultado de ler se um app de lado está na tela. */
internal object Visibilidade {
    const val DESCONHECIDA = -1
    const val SUMIU = 0
    const val VISIVEL = 1
}

/**
 * I1, passo 1: lê o texto do `dumpsys activity activities` (função pura de texto → −1 / 0 / 1).
 *
 * O pacote conta só quando aparece como pacote inteiro (D13): sem letra, dígito, `_` ou `.` colado
 * antes e sem continuação de nome (`com.foo` não casa com `com.foo.bar`).
 */
internal object LeitorDeDumpsys {

    const val MINIMO_DE_CARACTERES = 40
    const val TEXTO_DE_NEGACAO = "Permission Denial"
    const val ANTES = 400
    const val DEPOIS = 600
    private const val MARCA = "visible=true"

    fun visibilidade(texto: String, pacote: String): Int {
        if (pacote.isEmpty()) return Visibilidade.SUMIU
        if (texto.length < MINIMO_DE_CARACTERES || texto.contains(TEXTO_DE_NEGACAO)) return Visibilidade.DESCONHECIDA
        var achou = false
        var pos = texto.indexOf(pacote)
        while (pos >= 0) {
            if (limitado(texto, pos, pacote.length)) {
                achou = true
                val inicio = maxOf(0, pos - ANTES)
                val fim = minOf(texto.length, pos + DEPOIS)
                if (texto.indexOf(MARCA, inicio).let { it in 0 until fim }) return Visibilidade.VISIVEL
            }
            pos = texto.indexOf(pacote, pos + 1)
        }
        return if (achou) Visibilidade.SUMIU else Visibilidade.DESCONHECIDA
    }

    /** Foi negado (o app comum não tem a permissão de `dumpsys`)? */
    fun foiNegado(texto: String): Boolean = texto.contains(TEXTO_DE_NEGACAO)

    private fun limitado(texto: String, pos: Int, tamanho: Int): Boolean {
        if (pos > 0 && ehDeNome(texto[pos - 1], comPonto = true)) return false
        val fim = pos + tamanho
        if (fim >= texto.length) return true
        val c = texto[fim]
        if (ehDeNome(c, comPonto = false)) return false
        // "com.foo.bar": o ponto seguido de nome continua o mesmo pacote; ponto final de frase não.
        if (c == '.' && fim + 1 < texto.length && ehDeNome(texto[fim + 1], comPonto = false)) return false
        return true
    }

    private fun ehDeNome(c: Char, comPonto: Boolean): Boolean =
        c.isLetterOrDigit() || c == '_' || (comPonto && c == '.')
}

/** Lembra por alguns minutos que o `dumpsys` foi negado, para não rodar um processo à toa a cada segundo (D1). */
internal class MemoriaDeNegacao(
    private val relogio: Relogio,
    private val duracaoMs: Long = DURACAO_PADRAO_MS,
) {
    private var negadoAte = Long.MIN_VALUE

    @Synchronized
    fun deveTentar(): Boolean = relogio.agoraMs() >= negadoAte

    @Synchronized
    fun registrarNegado() {
        negadoAte = relogio.agoraMs() + duracaoMs
    }

    companion object {
        const val DURACAO_PADRAO_MS = 5 * 60_000L
    }
}

/** Uma tarefa em execução vista pelas listas do Android (I1 passos 2 e 4). */
internal data class RegistroDeTarefa(
    val id: Int,
    val pacoteDoTopo: String?,
    val pacoteDaBase: String?,
    /** `isVisible` lido por reflexão; campo ausente = verdadeiro (D14). */
    val visivel: Boolean = true,
) {
    /** O pacote da tarefa: o da atividade do topo, senão o da base. */
    val pacote: String? get() = pacoteDoTopo ?: pacoteDaBase
}

/** Um processo em execução (I1 passo 3). */
internal data class RegistroDeProcesso(val pacotes: List<String>, val importancia: Int)

/**
 * I1 inteiro: a cadeia de leituras de visibilidade. Cada passo devolve 1 (visível), 0 (sumiu) ou −1
 * (não sei); o primeiro resultado ≥ 0 vence. Exceção num passo vale −1 e segue ao próximo.
 *
 * As fontes entram por função, para rodar em JVM; quem usa liga cada uma ao Android.
 */
internal class LeitorDeVisibilidade(
    private val pacoteProprio: String,
    private val dumpsys: (pacote: String) -> Int,
    private val listaDoServico: () -> List<RegistroDeTarefa>?,
    private val processos: () -> List<RegistroDeProcesso>?,
    private val tarefasPublicas: () -> List<RegistroDeTarefa>?,
    private val diario: Diario = Diario.Mudo,
) {

    fun ler(pacote: String, tarefaGuardada: Int): Int {
        if (pacote.isEmpty()) return Visibilidade.SUMIU
        val passos: List<() -> Int> = listOf(
            { dumpsys(pacote) },
            { pelaLista(pacote, -1, listaDoServico()) },
            { peloProcesso(pacote) },
            { pelaLista(pacote, tarefaGuardada, tarefasPublicas()) },
        )
        for ((indice, passo) in passos.withIndex()) {
            val resultado = try {
                passo()
            } catch (e: Exception) {
                diario.aviso("visibilidade: passo ${indice + 1} falhou (${e.javaClass.simpleName})")
                Visibilidade.DESCONHECIDA
            }
            if (resultado >= 0) return resultado
        }
        return Visibilidade.DESCONHECIDA
    }

    /** Passos 2 e 4: a tarefa do pacote (ou de número guardado) diz se está visível. */
    private fun pelaLista(pacote: String, tarefaGuardada: Int, lista: List<RegistroDeTarefa>?): Int {
        if (lista == null) return Visibilidade.DESCONHECIDA
        var viuOutroApp = false
        for (t in lista) {
            val p = t.pacote
            if (p != null && p != pacoteProprio) viuOutroApp = true
            if (p == pacote || (tarefaGuardada >= 0 && t.id == tarefaGuardada)) {
                return if (t.visivel) Visibilidade.VISIVEL else Visibilidade.SUMIU
            }
        }
        return if (viuOutroApp) Visibilidade.SUMIU else Visibilidade.DESCONHECIDA
    }

    /** Passo 3: importância do processo (`IMPORTANCE_VISIBLE` = 200). */
    private fun peloProcesso(pacote: String): Int {
        val lista = processos()
        if (lista.isNullOrEmpty()) return Visibilidade.DESCONHECIDA
        var viuOutroApp = false
        for (processo in lista) {
            for (p in processo.pacotes) if (p != pacoteProprio) viuOutroApp = true
            if (pacote in processo.pacotes) {
                return if (processo.importancia <= IMPORTANCIA_VISIVEL) Visibilidade.VISIVEL else Visibilidade.SUMIU
            }
        }
        return if (viuOutroApp) Visibilidade.SUMIU else Visibilidade.DESCONHECIDA
    }

    companion object {
        const val IMPORTANCIA_VISIVEL = 200
    }
}
