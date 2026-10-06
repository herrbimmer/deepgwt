package br.com.deepgwt.tela3d.janelas

/**
 * Tudo que o [NucleoDasJanelas] pede ao Android sobre tarefas e aberturas. As funções com `aoTerminar`
 * trabalham fora da thread principal (consultas de sistema podem demorar) e chamam de volta NA
 * principal; um falso de teste pode chamar na hora. Nenhuma lança, exceto as `abrir…` (o núcleo
 * registra a exceção e segue).
 */
internal interface PortaDoSistema {

    /** O componente de entrada do pacote, em `pacote/classe`; null = sem atividade de entrada (pedido ignorado). */
    fun componenteDeEntrada(pacote: String): String?

    /** C8/C9: abre em tela cheia (modo de janela 1). [raizDasConfiguracoes] usa as flags que zeram a tarefa. */
    fun abrirTelaCheia(pacote: String, componente: String, limites: Retangulo, raizDasConfiguracoes: Boolean)

    /** C10/C11 (primeira abertura) ou C12/C39 ([reabertura]): janela livre (modo 5) no retângulo do lado. */
    fun abrirJanelaDeLado(pacote: String, componente: String, limites: Retangulo, reabertura: Boolean)

    /** C42: abre a projeção em tela cheia (modo 1) com limites sem desconto da barra. */
    fun abrirProjecao(componente: String, limites: Retangulo)

    /** C42: componentes de reserva (atividade preferida se `exported`, depois as de entrada), sem repetição. */
    fun componentesDeReserva(tipo: TipoDeProjecao): List<String>

    /** C17. */
    fun ligarJanelaLivre()

    /** C51: verdadeiro se o ouvinte de pilha ficou registrado; [aoMudar] pode ser chamado de qualquer thread. */
    fun registrarOuvinteDePilha(aoMudar: Runnable): Boolean

    fun removerOuvinteDePilha()

    /** C34 (`killBackgroundProcesses` + `am force-stop` pelo Shell): não espera. */
    fun encerrarApp(pacote: String)

    /** C10 passo 4: remove as tarefas já existentes do pacote. */
    fun removerTarefasDoPacote(pacote: String, aoTerminar: () -> Unit)

    /** C13: remove a tarefa (a guardada, se ≥ 0, senão a achada por pacote). Não espera. */
    fun fecharTarefa(pacote: String, guardado: Int)

    /** C12/C18: acha, redimensiona e marca no topo; devolve o número achado (−1 se não achou). */
    fun fixarTarefa(pacote: String, guardado: Int, limites: Retangulo, aoTerminar: (Int) -> Unit)

    /** C29. */
    fun levantarTarefa(pacote: String, guardado: Int, aoTerminar: (ResultadoDoLevantar) -> Unit)

    /** C14: redimensiona a tarefa do pacote para os limites; devolve o número (−1 se não achou). */
    fun redimensionarDoPacote(pacote: String, guardado: Int, limites: Retangulo, aoTerminar: (Int) -> Unit)

    /** C38: marca no topo (e levanta se isso falhou); devolve o número (−1 se não achou). */
    fun fixarNoTopo(pacote: String, guardado: Int, aoTerminar: (Int) -> Unit)

    /** C40: procura a tarefa do pacote e a levanta. */
    fun levantarPorPacote(pacote: String, aoTerminar: (ResultadoDoLevantar) -> Unit)

    /** Extra `raise_task_id`: sonda que levanta uma tarefa pelo número. */
    fun levantarPorNumero(tarefa: Int, aoTerminar: (Boolean) -> Unit)

    /** I1: 1 visível, 0 sumiu ou não visível, −1 desconhecido. */
    fun lerVisibilidade(pacote: String, guardado: Int, aoTerminar: (Int) -> Unit)

    /** C30: zera a memória do último levantar (D5/C37). */
    fun esquecerUltimoLevantar()
}

/** A tela da Activity: página, janela, geometria e avisos. Tudo na thread principal. */
internal interface PortaDaTela {
    /** A raiz da Activity já foi medida (largura e altura > 0)? */
    fun raizMedida(): Boolean

    /** C45: monta o objeto do §3 com a geometria atual e entrega à página. */
    fun enviarLayout(estado: EstadoDasJanelas)

    /** J6–J8. */
    fun limitesDoLado(lado: Lado, modo: ModoDeTela, proporcao: ProporcaoDaDivisao): Retangulo

    /** J9: reposiciona as âncoras da animação de abrir. */
    fun recalcularAncoras(modo: ModoDeTela, proporcao: ProporcaoDaDivisao)

    /** J10 (com a reserva de `(0,0,1920,720)`). */
    fun limitesDeTelaCheia(pacote: String): Retangulo

    /** C31: aplica as flags da janela; verdadeiro se mudou alguma. */
    fun aplicarPoliticaDeFoco(naoFocavel: Boolean, noLimite: Boolean): Boolean

    /** J11. */
    fun aplicarImersivo(modo: ModoDaInterface)

    /** Toast curto. */
    fun mostrarAviso(texto: String)
}

/** O que o intent da Activity HOME pode trazer (§4); quem lê o `Intent` os remove dele. */
internal data class ExtrasDeEntrada(
    val modoDaInterface: String? = null,
    val tarefaParaLevantar: Int? = null,
    val projecao: String? = null,
    val abrirComoODock: String? = null,
) {
    companion object {
        val NENHUM = ExtrasDeEntrada()
    }
}

/** Um dos dois lados: o app (pacote, componente lançado, número da tarefa; −1 = desconhecido). */
internal class Slot {
    var pacote: String = ""
        private set
    var componente: String? = null
        private set
    var tarefa: Int = -1

    /** Entre preencher o slot e o app ser de fato lançado (a remoção de tarefas antigas é assíncrona). */
    var aguardandoAbertura: Boolean = false

    /** Muda a cada preenchimento ou limpeza: um retorno assíncrono compara para saber se ainda vale. */
    var geracao: Int = 0
        private set

    val ocupado: Boolean get() = pacote.isNotEmpty()

    fun preencher(pacote: String, componente: String?) {
        this.pacote = pacote
        this.componente = componente
        tarefa = -1
        aguardandoAbertura = true
        geracao++
    }

    fun limpar() {
        pacote = ""
        componente = null
        tarefa = -1
        aguardandoAbertura = false
        geracao++
    }

    fun trocarCom(outro: Slot) {
        val p = pacote
        val c = componente
        val t = tarefa
        val a = aguardandoAbertura
        pacote = outro.pacote
        componente = outro.componente
        tarefa = outro.tarefa
        aguardandoAbertura = outro.aguardandoAbertura
        outro.pacote = p
        outro.componente = c
        outro.tarefa = t
        outro.aguardandoAbertura = a
        geracao++
        outro.geracao++
    }
}

/** C31: as flags da janela da Activity (`FLAG_NOT_FOCUSABLE` = 0x8; `LAYOUT_IN_SCREEN | LAYOUT_NO_LIMITS` = 0x300). */
internal object PoliticaDeFoco {
    const val NAO_FOCAVEL = 0x8
    const val NO_LIMITE = 0x300

    /** As flags depois da política, a partir das [atuais]; só estes bits mudam. */
    fun novasFlags(atuais: Int, naoFocavel: Boolean, noLimite: Boolean): Int {
        var f = atuais
        f = if (naoFocavel) f or NAO_FOCAVEL else f and NAO_FOCAVEL.inv()
        f = if (noLimite) f or NO_LIMITE else f and NO_LIMITE.inv()
        return f
    }
}
