// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

/**
 * A thread de trabalho do gestor: tudo o que bloqueia em shell roda nela, uma tarefa por vez, e o
 * vigia também. No app é um `HandlerThread`; nos testes, um falso que roda quando mandado.
 */
internal interface AgendadorDeFundo {
    fun executar(tarefa: Runnable)
    fun depois(atrasoMs: Long, tarefa: Runnable)

    /** Tira da fila (a mesma instância de [tarefa]). */
    fun cancelar(tarefa: Runnable)
}

/** Os arquivos do aparelho. Os nomes são contrato: um daemon antigo pode estar rodando. */
internal object ArquivosDoRoteador {
    const val PREFIXO = "/data/local/tmp/hotrouter"
    const val SCRIPT = "$PREFIXO.sh"
    const val ESTADO = "$PREFIXO.state"
    const val VERSAO = "$PREFIXO.version"
    const val SAIDA = "$PREFIXO.out"
}

/**
 * O lado do app do roteador do hotspot (especificação, seção C): instala o script, lança o
 * daemon, vigia, para e lê o status. O daemon é o script `roteador_do_hotspot.sh`.
 *
 * [lerScript] devolve o conteúdo do asset (null = não deu para ler).
 */
internal class GestorDoRoteador(
    private val armazem: ArmazemDeChaves,
    private val shell: ExecutorDeShell,
    private val registro: RegistroDaRede,
    private val relogio: RelogioDaRede,
    private val agendador: AgendadorDeFundo,
    private val lerScript: () -> ByteArray?,
) {
    private val instalador = InstaladorDeArquivo(shell, registro)

    /** Instante (monotônico) do último "ligar" ou "serviço pronto", para o período de graça. */
    @Volatile
    private var graca: Long? = null

    private fun ligado() = armazem.lerBoolean(ChavesDaRede.ROTEADOR_LIGADO, false)

    // ---- entradas públicas (voltam na hora) ----

    fun definirLigado(ligado: Boolean) {
        armazem.gravarBoolean(ChavesDaRede.ROTEADOR_LIGADO, ligado)
        if (ligado) {
            graca = relogio.monotonicoEmMs()
            agendador.executar(tarefa("ligar roteador") { ligar() })
        } else {
            agendador.cancelar(vigia)
            agendador.executar(tarefa("desligar roteador") { desligar() })
        }
    }

    fun aoFicarPronto() {
        if (!ligado()) return
        graca = relogio.monotonicoEmMs()
        agendador.executar(
            tarefa("roteador pronto") {
                garantirDaemon(instalarSempre = true, renovarGraca = false)
                armarVigia()
            },
        )
    }

    /** Bloqueia (shell). */
    fun lerSituacao(): SituacaoDoRoteador {
        if (!ligado()) return SituacaoDoRoteador(ModoDoRoteador.DESLIGADO, 0)
        if (InterpretadorDoRoteador.daemonVivo(lerStatus())) {
            return InterpretadorDoRoteador.situacaoDoDaemonVivo(lerArquivo(ArquivosDoRoteador.ESTADO), relogio.agoraEmMs() / 1000)
        }
        val desde = graca?.let { relogio.monotonicoEmMs() - it }
        return InterpretadorDoRoteador.situacaoDoDaemonMorto(desde)
    }

    // ---- tarefas (thread de trabalho) ----

    private fun ligar() {
        if (!ligado()) return
        val bytes = lerScript()
        if (bytes == null) {
            registro.erro("roteador: não consegui ler o script do asset")
        } else {
            val sha = sha256Hex(bytes)
            if (instalador.instalar(ArquivosDoRoteador.SCRIPT, bytes, sha)) lancar(sha)
        }
        armarVigia()
    }

    private fun desligar() {
        if (!existeOScript()) {
            // Nada instalado: o `stop` é do script, então instala o do asset só para rodá-lo.
            val bytes = lerScript()
            if (bytes == null || !instalador.instalar(ArquivosDoRoteador.SCRIPT, bytes)) {
                registro.erro("roteador: sem script no aparelho e não consegui instalar; nada foi desfeito")
                return
            }
        }
        val saida = shell.executar(arrayOf("sh", ArquivosDoRoteador.SCRIPT, "stop"))
        if (saida.trim() == "STOPPED") {
            registro.aviso("roteador desligado")
        } else {
            registro.erro("roteador: o stop não confirmou a limpeza (saída: '${saida.trim()}')")
        }
    }

    /**
     * Garante um daemon vivo e da versão certa. [instalarSempre]: instala o script antes de olhar o
     * status (partida); senão só instala se precisar lançar (vigia). [renovarGraca]: o vigia
     * recomeça a contar o período de graça quando acha o daemon morto.
     */
    private fun garantirDaemon(instalarSempre: Boolean, renovarGraca: Boolean) {
        val bytes = lerScript()
        if (bytes == null) {
            registro.erro("roteador: não consegui ler o script do asset")
            return
        }
        val sha = sha256Hex(bytes)
        if (instalarSempre && !instalador.instalar(ArquivosDoRoteador.SCRIPT, bytes, sha)) return

        val vivo = InterpretadorDoRoteador.daemonVivo(lerStatus())
        if (vivo && lerArquivo(ArquivosDoRoteador.VERSAO) == sha) return

        if (!vivo && renovarGraca) graca = relogio.monotonicoEmMs()
        if (!instalarSempre && !instalador.instalar(ArquivosDoRoteador.SCRIPT, bytes, sha)) return
        if (vivo) registro.aviso("roteador: daemon de outra versão do script; relançando")
        lancar(sha)
    }

    /**
     * Grava o SHA-256 do script que vai rodar e lança o daemon em segundo plano, com saída e erro
     * para um arquivo e entrada de /dev/null: sem isso o cano do Shizuku nunca fecha e o shell
     * trava esperando. Não espera o daemon nem confirma: quem confirma é o status.
     *
     * O caminho do script é montado no shell (`$d/...`): assim a linha de comando do próprio `sh -c`
     * não tem o caminho completo seguido de " start" e o daemon nunca a confunde com um daemon.
     */
    private fun lancar(sha: String) {
        val roteiro = "d=/data/local/tmp; " +
            "printf %s '$sha' > \$d/hotrouter.version; " +
            "setsid sh \$d/hotrouter.sh start > \$d/hotrouter.out 2>&1 < /dev/null &"
        shell.roteiro(roteiro)
    }

    private fun existeOScript(): Boolean =
        temMarcador(shell.roteiro("[ -f ${ArquivosDoRoteador.SCRIPT} ] && echo $MARCADOR_OK"))

    // ---- vigia ----

    /** Uma só instância: armar cancela a anterior, então há no máximo um vigia armado. */
    private val vigia: Runnable = object : Runnable {
        override fun run() {
            // Preferência desligada: o vigia se encerra e não reagenda.
            if (!ligado()) return
            try {
                garantirDaemon(instalarSempre = false, renovarGraca = true)
            } catch (e: Exception) {
                registro.erro("roteador: o vigia falhou", e)
            }
            agendador.depois(PERIODO_DO_VIGIA_MS, this)
        }
    }

    private fun armarVigia() {
        agendador.cancelar(vigia)
        agendador.depois(PERIODO_DO_VIGIA_MS, vigia)
    }

    // ---- shell ----

    /** Os dois comandos de leitura terminam com `exit 0`: arquivo ausente ou DEAD não é erro de shell. */
    private fun lerStatus(): String = shell.roteiro("sh ${ArquivosDoRoteador.SCRIPT} status; exit 0")

    private fun lerArquivo(caminho: String): String = shell.roteiro("cat $caminho 2>/dev/null; exit 0").trim()

    private fun tarefa(nome: String, corpo: () -> Unit) = Runnable {
        try {
            corpo()
        } catch (e: Exception) {
            registro.erro("roteador: $nome falhou", e)
        }
    }

    companion object {
        const val PERIODO_DO_VIGIA_MS = 60_000L
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
