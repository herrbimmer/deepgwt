package br.com.deepgwt.tela3d.ponte

/** Falsos usados pelos testes da ponte da tela 3D. Tudo na JVM, sem Android. */

class ExecutorImediato : ExecutorDaUi {
    override fun postar(tarefa: () -> Unit) = tarefa()
}

/** Guarda as tarefas e só roda quando mandam (para provar que algo foi POSTADO e não executado direto). */
class ExecutorManual : ExecutorDaUi {
    val fila = ArrayList<() -> Unit>()
    override fun postar(tarefa: () -> Unit) {
        fila += tarefa
    }

    fun rodarTudo() {
        val copia = fila.toList()
        fila.clear()
        copia.forEach { it() }
    }
}

class DiarioFalso : DiarioDaPonte {
    val avisos = ArrayList<String>()
    val erros = ArrayList<String>()
    val infos = ArrayList<String>()
    override fun info(mensagem: String) {
        infos += mensagem
    }

    override fun aviso(mensagem: String) {
        avisos += mensagem
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        erros += mensagem
    }
}

class PreferenciasFalsas : PreferenciasDoLancador {
    val mapa = HashMap<String, Any>()
    override fun texto(chave: String, padrao: String): String = mapa[chave] as? String ?: padrao
    override fun inteiro(chave: String, padrao: Int): Int = mapa[chave] as? Int ?: padrao
    override fun gravarTexto(chave: String, valor: String) {
        mapa[chave] = valor
    }

    override fun gravarInteiro(chave: String, valor: Int) {
        mapa[chave] = valor
    }
}

class JanelasFalsas : JanelasParaAPonte {
    val chamadas = ArrayList<String>()
    var estado: EstadoDoShell = EstadoDoShell.PADRAO
    override fun abrirEmPopup(pacote: String) { chamadas += "abrirEmPopup($pacote)" }
    override fun abrirNoLado(pacote: String, lado: String) { chamadas += "abrirNoLado($pacote,$lado)" }
    override fun maximizar(pacote: String?) { chamadas += "maximizar($pacote)" }
    override fun fechar(pacote: String?) { chamadas += "fechar($pacote)" }
    override fun dispensarSobreposicoes() { chamadas += "dispensarSobreposicoes" }
    override fun definirModoDoShell(modo: String) { chamadas += "modo($modo)" }
    override fun definirLadoDeAbertura(lado: String) { chamadas += "ladoDeAbertura($lado)" }
    override fun definirUsoDoLado(lado: String, uso: String) { chamadas += "uso($lado,$uso)" }
    override fun definirProporcao(proporcao: String) { chamadas += "proporcao($proporcao)" }
    override fun trocarLados() { chamadas += "trocarLados" }
    override fun salvarModoAppsComoPadrao() { chamadas += "salvarModoApps" }
    override fun definirChromeNoTopo(noTopo: Boolean) { chamadas += "chromeNoTopo($noTopo)" }
    override fun abrirProjecao(tipo: String) { chamadas += "projecao($tipo)" }
    override fun fecharSlotDaDireita() { chamadas += "fecharDireita" }
    override fun levarTarefaParaFrente(taskId: Int): Boolean { chamadas += "tarefa($taskId)"; return true }
    override fun temSobreposicao(): Boolean = false
    override fun estadoDoShell(): EstadoDoShell = estado
    override fun garantirConfiguracoesDeJanelaLivre() {}
    override fun aoRetomar() {}
    override fun aoGanharFoco() {}
    override fun aoSairDeVista() {}
    override fun aoLevantarODedo(noTopoReservado: Boolean) {}
    override fun aoReceberTarefaResolvida(pacote: String, taskId: Int) {}
    override fun aoDestruir() {}
}

class FaixaFalsa : FaixaParaAPonte {
    val aplicados = ArrayList<IndicadoresDoDock>()
    val preenchimentos = ArrayList<Pair<String?, Boolean?>>()
    var falharAoAplicar = false
    override fun aplicarIndicadores(ind: IndicadoresDoDock) {
        if (falharAoAplicar) throw IllegalStateException("falha")
        aplicados += ind
    }

    override fun aplicarPreenchimentoDoCentro(modo: String?, visivel: Boolean?) {
        preenchimentos += modo to visivel
    }

    override fun revelar() {}
    override fun definirEscondidaPelaPagina(escondida: Boolean) {}
    override fun margens(): MargensDaPagina = MargensDaPagina.ZERADAS
    override fun reservaInferiorDeChromeEmPx(): Int = 0
    override fun aoPausar() {}
}

class AcoesFalsas : AcoesDaTela {
    val chamadas = ArrayList<String>()
    override fun iniciarFadeDeSplash(duracaoMs: Int) { chamadas += "fade($duracaoMs)" }
    override fun encerrarSplash() { chamadas += "encerrarSplash" }
    override fun revelarFaixa() { chamadas += "revelarFaixa" }
    override fun definirFaixaEscondidaPelaPagina(escondida: Boolean) { chamadas += "escondida($escondida)" }
    override fun capturarMiniatura(token: String, larguraPedida: Int, alturaPedida: Int) {
        chamadas += "miniatura($token,$larguraPedida,$alturaPedida)"
    }

    override fun reportarAreaDoSeletor(xCss: Float, yCss: Float, larguraCss: Float, alturaCss: Float, ativo: Boolean) {
        chamadas += "seletor($xCss,$yCss,$larguraCss,$alturaCss,$ativo)"
    }

    override fun definirChromeNoTopo(noTopo: Boolean) { chamadas += "chromeNoTopo($noTopo)" }
    override fun progredir(pct: Int) { chamadas += "progresso($pct)" }
    override fun posicionarProgresso(modo: String?) { chamadas += "posicionar($modo)" }
    override fun esconderProgresso() { chamadas += "esconderProgresso" }
    override fun pedirPermissaoDeLocalizacao() { chamadas += "pedirLocalizacao" }
}

class EnviadorFalso : EnviadorDeComandos {
    val envios = ArrayList<String>()
    override fun pedirRetratoDeTelemetria() { envios += "retrato" }
    override fun enviarComando(comando: String, valor: String) { envios += "comando($comando,$valor)" }
    override fun escreverChave(chave: String, valor: String) { envios += "chave($chave,$valor)" }
}

class ViagensFalsas : ViagensParaAPonte {
    val chamadas = ArrayList<String>()
    override fun encerrarViagem() { chamadas += "encerrar" }
    override fun dias(de: String, ate: String): String { chamadas += "dias($de,$ate)"; return "[d]" }
    override fun aoVivo(): String { chamadas += "aoVivo"; return "{live}" }
    override fun meses(): String { chamadas += "meses"; return "[m]" }
    override fun totaisDeParadas(deMs: Long): String { chamadas += "paradas($deMs)"; return "{p}" }
    override fun totais(): String { chamadas += "totais"; return "{t}" }
    override fun viagem(idMs: Long, maxPontos: Int): String { chamadas += "viagem($idMs,$maxPontos)"; return "{v}" }
    override fun mapaDaViagem(idMs: Long): String { chamadas += "mapa($idMs)"; return "data:x" }
    override fun pontosDesde(inicioMs: Long, tMs: Long, maxPontos: Int): String {
        chamadas += "pontos($inicioMs,$tMs,$maxPontos)"
        return "{pts}"
    }

    override fun lista(offset: Int, limite: Int): String { chamadas += "lista($offset,$limite)"; return "[l]" }
    override fun gravarEmBreve() { chamadas += "gravar" }
}

class AmbienteFalso(
    private val idioma: String? = "pt-BR",
    private val fuso: String? = "America/Sao_Paulo",
    private val em24h: Boolean = true,
    private val falhaNoIdioma: Boolean = false,
) : AmbienteDoRelogio {
    override fun etiquetaDoIdioma(): String? = if (falhaNoIdioma) throw IllegalStateException("x") else idioma
    override fun idDoFuso(): String? = fuso
    override fun sistemaEm24h(): Boolean = em24h
}

class AgendadorFalso : AgendadorDeTarefas {
    val pendentes = ArrayList<Pair<Runnable, Long>>()
    override fun postarComAtraso(tarefa: Runnable, atrasoMs: Long) {
        pendentes += tarefa to atrasoMs
    }

    override fun cancelar(tarefa: Runnable) {
        pendentes.removeAll { it.first === tarefa }
    }

    /** Roda a tarefa pendente mais antiga. */
    fun dispararProxima() {
        val (tarefa, _) = pendentes.removeAt(0)
        tarefa.run()
    }
}

class ExtrasFalsos(valores: Map<String, Any> = emptyMap()) : ExtrasDeEntrada {
    val mapa = HashMap(valores)
    val removidos = ArrayList<String>()
    override fun texto(chave: String): String? = mapa[chave] as? String
    override fun inteiro(chave: String, padrao: Int): Int = mapa[chave] as? Int ?: padrao
    override fun booleano(chave: String): Boolean = mapa[chave] as? Boolean ?: false
    override fun remover(chave: String) {
        removidos += chave
        mapa.remove(chave)
    }
}
