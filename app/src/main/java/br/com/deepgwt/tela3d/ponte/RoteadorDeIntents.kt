package br.com.deepgwt.tela3d.ponte

/** Os extras do intent que abre ou reabre a Activity, sem depender de `Intent` (para testar na JVM). */
interface ExtrasDeEntrada {
    fun texto(chave: String): String?
    fun inteiro(chave: String, padrao: Int): Int
    fun booleano(chave: String): Boolean
    fun remover(chave: String)
}

/** O que um intent de entrada pede à Activity, na ordem em que deve ser feito. */
sealed interface AcaoDeEntrada {
    data class TrocarOcupacao(val modo: ModoDeOcupacao) : AcaoDeEntrada
    data class TrazerTarefa(val taskId: Int) : AcaoDeEntrada
    data class AbrirProjecao(val tipo: TipoDeProjecao) : AcaoDeEntrada
    data class AbrirApp(val pacote: String) : AcaoDeEntrada
}

/**
 * O roteamento de intents de entrada (usados por adb, testes e guardas). A ordem é fixa:
 * `ui_mode`, `raise_task_id`, `launch_projection`, `launch_freeform`. Todo extra de ação é
 * **consumido** (removido do intent) para uma recriação da Activity não repetir a ação; `nosplash`
 * não é consumido. Intent sem extras (a tecla Início) não pede nada.
 */
object RoteadorDeIntents {
    const val EXTRA_MODO_DE_OCUPACAO = "ui_mode"
    const val EXTRA_TAREFA_PARA_FRENTE = "raise_task_id"
    const val EXTRA_PROJECAO = "launch_projection"
    const val EXTRA_APP_EM_JANELA = "launch_freeform"
    const val EXTRA_SEM_SPLASH = "nosplash"

    fun rotear(extras: ExtrasDeEntrada?): List<AcaoDeEntrada> {
        if (extras == null) return emptyList()
        val acoes = ArrayList<AcaoDeEntrada>(4)

        val modo = extras.texto(EXTRA_MODO_DE_OCUPACAO)
        if (!modo.isNullOrEmpty()) {
            extras.remover(EXTRA_MODO_DE_OCUPACAO)
            ModoDeOcupacao.lerOuNulo(modo)?.let { acoes += AcaoDeEntrada.TrocarOcupacao(it) }
        }

        val tarefa = extras.inteiro(EXTRA_TAREFA_PARA_FRENTE, -1)
        if (tarefa >= 0) {
            extras.remover(EXTRA_TAREFA_PARA_FRENTE)
            acoes += AcaoDeEntrada.TrazerTarefa(tarefa)
        }

        val projecao = extras.texto(EXTRA_PROJECAO)
        if (!projecao.isNullOrEmpty()) {
            extras.remover(EXTRA_PROJECAO)
            TipoDeProjecao.ler(projecao)?.let { acoes += AcaoDeEntrada.AbrirProjecao(it) }
        }

        val pacote = extras.texto(EXTRA_APP_EM_JANELA)
        if (!pacote.isNullOrEmpty()) {
            extras.remover(EXTRA_APP_EM_JANELA)
            val limpo = pacote.trim()
            if (limpo.isNotEmpty()) acoes += AcaoDeEntrada.AbrirApp(limpo)
        }
        return acoes
    }

    /** O extra `nosplash` da abertura (só vale na criação sem estado salvo). */
    fun semSplash(extras: ExtrasDeEntrada?): Boolean = extras?.booleano(EXTRA_SEM_SPLASH) ?: false
}
