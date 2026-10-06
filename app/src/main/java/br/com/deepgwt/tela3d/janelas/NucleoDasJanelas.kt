package br.com.deepgwt.tela3d.janelas

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.EnumMap

/**
 * A lógica de janelas (casos C1–C54 da especificação), sem Android: o estado dos dois lados, os modos,
 * a divisão, a abertura de apps, a projeção, o vigia e o aviso à página. Tudo chega pelas portas
 * ([PortaDoSistema], [PortaDaTela]) e pelas interfaces dos vizinhos, e o tempo vem de [Agendador] e
 * [Relogio] — por isso roda inteira em teste de JVM com tempo falso.
 *
 * Todas as funções rodam na thread principal; quem chama de outra thread (a ponte JS) é reenfileirado
 * pela fachada. As consultas de sistema que podem demorar saem pela [PortaDoSistema] e voltam por
 * `aoTerminar`, também na principal.
 *
 * Decisões do dono já aplicadas: o padrão do Side by Side sobrevive às gravações comuns; o app é
 * encerrado (`am force-stop` pelo Shell) ao fechar qualquer lado, exceto ao minimizar o Side by Side;
 * sem lado livre não faz nada; sem readoção de janelas depois de recriar a Activity; sem preferência
 * de lado fixo; `slot_*` nunca é lido; nada é limpo quando a leitura de visibilidade é "desconhecido".
 */
internal class NucleoDasJanelas(
    private val pacoteProprio: String,
    private val sistema: PortaDoSistema,
    private val tela: PortaDaTela,
    private val agendador: Agendador,
    private val relogio: Relogio,
    private val armazem: PreferenciasDasJanelas,
    private val chrome: ChromeParaJanelas,
    private val catalogo: CatalogoParaJanelas,
    private val projecao: ProjecaoParaJanelas,
    private val diario: Diario = Diario.Mudo,
) {

    // --- estado ---

    private var modo = ModoDeTela.APP_E_CARRO
    private var proporcao = ProporcaoDaDivisao.UM_PARA_UM
    private var proximoEhEsquerda = true
    private var ultimoLado = ""
    private var modoDaInterface = ModoDaInterface.FLUTUANTE
    private var chromePorCima = false
    private val esquerda = Slot()
    private val direita = Slot()
    private var usoEsquerda = UsoDoLado.WIDGETS
    private var usoDireita = UsoDoLado.WIDGETS
    private var abrirComoJanela = montarAbrirComoJanela(emptyList())

    private var restauracaoEsquerda = ""
    private var restauracaoDireita = ""
    private var restauracaoFeita = false

    private var aberturaEmCursoAte = Long.MIN_VALUE
    private var folgaDoLevantarAte = Long.MIN_VALUE
    private var ultimaReaberturaEm = SEM_REGISTRO
    private var ultimoNoTopoEm = SEM_REGISTRO

    private var projecaoPendente: TipoDeProjecao? = null

    private val reposicao = EnumMap<Lado, Cancelavel?>(Lado::class.java)
    private var vigia: Cancelavel? = null
    private var levantarLogo: Cancelavel? = null
    private var expiracaoDoChrome: Cancelavel? = null
    private var resolucaoDaProjecao: Cancelavel? = null
    private var avisoSemRaiz: Cancelavel? = null
    private var sincronizacaoDoRetorno: Cancelavel? = null
    private val restauracoes = ArrayList<Cancelavel>()
    private var destruido = false

    private val _estado = MutableStateFlow(instantaneo())
    val estado: StateFlow<EstadoDasJanelas> get() = _estado

    private fun slot(lado: Lado): Slot = if (lado == Lado.ESQUERDA) esquerda else direita

    private fun uso(lado: Lado): UsoDoLado = if (lado == Lado.ESQUERDA) usoEsquerda else usoDireita

    private fun instantaneo() = EstadoDasJanelas(
        modo = modo,
        proporcao = proporcao,
        ladoPreferido = LADO_PREFERIDO_AUTOMATICO,
        proximoEhEsquerda = proximoEhEsquerda,
        ultimoLado = ultimoLado,
        pacoteEsquerda = esquerda.pacote,
        pacoteDireita = direita.pacote,
        usoEsquerda = usoEsquerda,
        usoDireita = usoDireita,
        modoDaInterface = modoDaInterface,
        chromePorCima = chromePorCima,
    )

    private fun publicar() {
        _estado.value = instantaneo()
    }

    fun temJanelaDeLado(): Boolean = esquerda.ocupado || direita.ocupado

    fun textoDeGetShellLayout(): String = LayoutParaAPagina.textoDeGetShellLayout(_estado.value)

    private fun montarAbrirComoJanela(inicial: Collection<String>) = AbrirComoJanela(
        inicial = inicial,
        pacoteProprio = pacoteProprio,
        podeAlternar = { podeAlternarAbrirComoJanela(it) },
        gravar = { armazem.gravarAbrirComoJanela(it) },
    )

    private fun limites(lado: Lado): Retangulo = tela.limitesDoLado(lado, modo, proporcao)

    private fun adiar(acao: () -> Unit) {
        agendador.em(0L) { if (!destruido) acao() }
    }

    private fun cancelar(c: Cancelavel?) {
        c?.cancelar()
    }

    // --- ciclo de vida ---

    /** C47, C51, C27 (só lê; a restauração é agendada na revelação) e os extras do §4. */
    fun aoCriar(extras: ExtrasDeEntrada) {
        val dados = armazem.carregar()
        modo = dados.modo
        proporcao = dados.proporcao
        proximoEhEsquerda = dados.proximoEhEsquerda
        modoDaInterface = dados.modoDaInterface
        restauracaoEsquerda = dados.appEsquerda
        restauracaoDireita = dados.appDireita
        abrirComoJanela = montarAbrirComoJanela(dados.abrirComoJanela)
        usoEsquerda = UsoDoLado.WIDGETS
        usoDireita = UsoDoLado.WIDGETS
        esquerda.limpar()
        direita.limpar()
        ultimoLado = ""
        tela.recalcularAncoras(modo, proporcao)
        runCatching { sistema.ligarJanelaLivre() }
        val ouvinteOk = runCatching { sistema.registrarOuvinteDePilha { adiar { sincronizar() } } }.getOrDefault(false)
        if (!ouvinteOk) diario.aviso("ouvinte de pilha indisponível; seguindo só com o vigia")
        projecao.aoMudar { tipo -> chrome.aoMudarAProjecao(tipo) }
        chrome.aoMudarAProjecao(projecao.atual)
        publicar()
        consumirExtras(extras)
    }

    /** §4: consome os extras na ordem ui_mode, raise_task_id, launch_projection, launch_freeform. */
    fun consumirExtras(extras: ExtrasDeEntrada) {
        extras.modoDaInterface?.let { definirModoDaInterface(it) }
        extras.tarefaParaLevantar?.takeIf { it >= 0 }?.let { id ->
            sistema.levantarPorNumero(id) { ok -> diario.info("sonda: levantar tarefa $id ${if (ok) "ok" else "recusado"}") }
        }
        extras.projecao?.let { abrirProjecao(PacotesDeProjecao.tipoDoTextoDaPagina(it)) }
        extras.abrirComoODock?.let { abrirComoODock(it, "") }
    }

    /** C48. */
    fun aoRetomar() {
        avisarPagina()
        if (temJanelaDeLado()) fixarNoTopoComLimite()
        sincronizar()
        cancelar(sincronizacaoDoRetorno)
        sincronizacaoDoRetorno = agendador.em(ATRASO_DA_SINCRONIZACAO_DO_RETORNO_MS) { if (!destruido) sincronizar() }
    }

    /** C48: pode pausar a WebView só se não há janela de lado. */
    fun aoPausar(): Boolean = !temJanelaDeLado()

    /** C48. */
    fun aoGanharFoco() {
        if (temJanelaDeLado()) {
            aplicarPoliticaDeFoco()
            levantarLogo(0L)
        }
        sincronizar()
    }

    /** C29, gatilho (a). [naFaixaDeCima] = o toque foi nos 28 dp de cima. */
    fun aoSoltarToque(naFaixaDeCima: Boolean) {
        if (temJanelaDeLado()) levantarLogo(if (naFaixaDeCima) EXTRA_DO_TOQUE_NO_TOPO_MS else 0L)
    }

    /** C49. */
    fun aoPararVisivelmente(janelaVisivel: Boolean, mudandoDeConfiguracao: Boolean) {
        if (mudandoDeConfiguracao || relogio.agoraMs() < aberturaEmCursoAte) return
        if (!janelaVisivel) {
            catalogo.aoLauncherSairDaFrente()
            fecharTudo()
        }
    }

    /** C50: não fecha janelas de lado. */
    fun aoDestruir() {
        destruido = true
        desligarVigia()
        runCatching { sistema.removerOuvinteDePilha() }
        for (lado in Lado.entries) cancelarReposicao(lado)
        cancelar(levantarLogo)
        cancelar(expiracaoDoChrome)
        cancelar(resolucaoDaProjecao)
        cancelar(avisoSemRaiz)
        cancelar(sincronizacaoDoRetorno)
        restauracoes.forEach { it.cancelar() }
        restauracoes.clear()
        chromePorCima = false
        publicar()
    }

    // --- A. abrir apps ---

    /** C1/C3. */
    fun abrirComoODock(pacote: String, rotulo: String = "") {
        if (pacote.isEmpty()) return
        if (PacotesDeProjecao.ehProjecao(pacote)) {
            val tipo = PacotesDeProjecao.tipoDoPacote(pacote)
            adiar { abrirProjecao(tipo) }
            return
        }
        if (catalogo.ehAppDeMentiraDoEmulador(pacote)) {
            tela.mostrarAviso("${rotulo.ifEmpty { pacote }} (emulator stub)")
            return
        }
        catalogo.registrarAppRecente(pacote)
        if (pacote == PACOTE_DAS_CONFIGURACOES_DO_CARRO) {
            abrirRaizDasConfiguracoes()
            return
        }
        if (catalogo.ehAppDaGwm(pacote) || catalogo.ehAppFixoDoDock(pacote)) {
            abrirTelaCheia(pacote)
            return
        }
        if (modo == ModoDeTela.APP_E_CARRO && !abrirComoJanela.contem(pacote)) {
            abrirTelaCheia(pacote)
            return
        }
        if (pacote == esquerda.pacote) {
            fecharLado(Lado.ESQUERDA, pacote, encerrar = true)
            return
        }
        if (pacote == direita.pacote) {
            fecharLado(Lado.DIREITA, pacote, encerrar = true)
            return
        }
        val escolha = EscolhaDoLado.escolher(modo, !esquerda.ocupado, !direita.ocupado, proximoEhEsquerda)
        val lado = escolha.lado
        if (lado == null) {
            diario.info(
                "sem lado disponível: modo=${modo.texto} usos=${usoEsquerda.texto}/${usoDireita.texto} " +
                    "apps=${esquerda.pacote}/${direita.pacote}",
            )
            return
        }
        if (modo == ModoDeTela.DOIS_APPS) {
            proximoEhEsquerda = escolha.proximoEhEsquerda
            ultimoLado = lado.texto
            gravarComum()
            avisarPagina()
        }
        marcarUsoComoApp(lado)
        abrirJanela(lado, pacote)
    }

    /** C4. */
    fun abrirNoLado(pacote: String, lado: Lado) {
        adiar {
            if (pacote.isEmpty()) return@adiar
            if (PacotesDeProjecao.ehProjecao(pacote)) {
                abrirProjecao(PacotesDeProjecao.tipoDoPacote(pacote))
                return@adiar
            }
            if (pacote == PACOTE_DAS_CONFIGURACOES_DO_CARRO) {
                abrirRaizDasConfiguracoes()
                return@adiar
            }
            catalogo.registrarAppRecente(pacote)
            // O mesmo app no outro lado muda de lado: o slot de lá é esquecido (a tarefa antiga é
            // removida pela própria abertura, que apaga as tarefas já existentes do pacote).
            val outro = slot(lado.oposto)
            if (outro.pacote == pacote) {
                cancelarReposicao(lado.oposto)
                outro.limpar()
            }
            definirUso(lado, UsoDoLado.APP)
            abrirJanela(lado, pacote)
        }
    }

    /** C8. */
    private fun abrirTelaCheia(pacote: String) {
        val componente = sistema.componenteDeEntrada(pacote) ?: return
        for (lado in Lado.entries) cancelarReposicao(lado)
        for (lado in Lado.entries) if (slot(lado).pacote == pacote) slot(lado).limpar()
        val area = tela.limitesDeTelaCheia(pacote)
        try {
            sistema.abrirTelaCheia(pacote, componente, area, raizDasConfiguracoes = false)
            diario.info("tela cheia $componente $area")
        } catch (e: Exception) {
            diario.erro("tela cheia de $pacote falhou", e)
        }
        avisarPagina()
    }

    /** C9. */
    private fun abrirRaizDasConfiguracoes() {
        for (lado in Lado.entries) cancelarReposicao(lado)
        val componente = COMPONENTE_DAS_CONFIGURACOES
        try {
            sistema.abrirTelaCheia(PACOTE_DAS_CONFIGURACOES, componente, tela.limitesDeTelaCheia(PACOTE_DAS_CONFIGURACOES), raizDasConfiguracoes = true)
            diario.info("configurações: raiz em tela cheia")
        } catch (e: Exception) {
            diario.erro("raiz das configurações falhou; tentando tela cheia normal", e)
            abrirTelaCheia(PACOTE_DAS_CONFIGURACOES)
            return
        }
        avisarPagina()
    }

    /** C10/C11: abre [pacote] como janela do [lado]. */
    private fun abrirJanela(lado: Lado, pacote: String) {
        val componente = sistema.componenteDeEntrada(pacote) ?: return
        runCatching { sistema.ligarJanelaLivre() }
        val destino = slot(lado)
        if (destino.pacote == pacote) {
            fecharLado(lado, pacote, encerrar = true)
            return
        }
        if (destino.ocupado) fecharLado(lado, destino.pacote, encerrar = true)
        cancelarReposicao(lado)
        destino.preencher(pacote, componente)
        aberturaEmCursoAte = relogio.agoraMs() + ABERTURA_EM_CURSO_MS
        avisarPagina()
        val geracao = destino.geracao
        sistema.removerTarefasDoPacote(pacote) {
            if (destruido || destino.pacote != pacote || destino.geracao != geracao) return@removerTarefasDoPacote
            destino.aguardandoAbertura = false
            val area = limites(lado)
            try {
                sistema.abrirJanelaDeLado(pacote, componente, area, reabertura = false)
                diario.info("janela ${lado.texto}: $componente $area")
            } catch (e: Exception) {
                diario.erro("abrir $pacote em janela falhou", e)
            }
            agendarReposicao(lado)
        }
    }

    /**
     * C13: fecha um lado. Remove a tarefa, encerra o app (se [encerrar]) e, se o pacote fechado é o do
     * slot, limpa o slot e avisa a página. Sem [pacote], vale o app do slot.
     */
    private fun fecharLado(lado: Lado, pacote: String?, encerrar: Boolean) {
        cancelarReposicao(lado)
        val s = slot(lado)
        val alvo = pacote?.takeIf { it.isNotEmpty() } ?: s.pacote
        if (alvo.isNotEmpty()) {
            val guardado = if (s.tarefa >= 0 && alvo == s.pacote) s.tarefa else -1
            sistema.fecharTarefa(alvo, guardado)
            if (encerrar) sistema.encerrarApp(alvo)
        }
        if (alvo == s.pacote) {
            s.limpar()
            avisarPagina()
        }
    }

    /** C14. */
    fun maximizar(pacote: String?) {
        val lado = if (!pacote.isNullOrEmpty() && pacote == direita.pacote) Lado.DIREITA else Lado.ESQUERDA
        val alvo = pacote?.takeIf { it.isNotEmpty() } ?: slot(lado).pacote
        if (alvo.isEmpty()) return
        val componente = sistema.componenteDeEntrada(alvo) ?: return
        for (l in Lado.entries) cancelarReposicao(l)
        val doLado = slot(lado)
        val guardado = if (doLado.pacote == alvo && doLado.tarefa >= 0) doLado.tarefa else -1
        val area = tela.limitesDeTelaCheia(alvo)
        try {
            sistema.abrirTelaCheia(alvo, componente, area, raizDasConfiguracoes = false)
            diario.info("maximizar $componente $area")
        } catch (e: Exception) {
            diario.erro("maximizar $alvo falhou", e)
        }
        sistema.redimensionarDoPacote(alvo, guardado, area) { }
        for (l in Lado.entries) if (slot(l).pacote == alvo) slot(l).limpar()
        avisarPagina()
    }

    /** C15. */
    fun fecharPorPacote(pacote: String?) {
        try {
            if (!pacote.isNullOrEmpty() && pacote == direita.pacote) {
                fecharLado(Lado.DIREITA, pacote, encerrar = true)
            } else {
                fecharLado(Lado.ESQUERDA, pacote, encerrar = true)
            }
        } catch (e: Exception) {
            diario.erro("fechar $pacote falhou", e)
        }
    }

    /** `MediaBridge.dismissSlot`. */
    fun fecharJanelaDireita() {
        if (direita.ocupado) fecharLado(Lado.DIREITA, direita.pacote, encerrar = true)
    }

    /** C16. */
    fun fecharTudo() {
        adiar {
            if (esquerda.ocupado) fecharLado(Lado.ESQUERDA, esquerda.pacote, encerrar = true)
            if (direita.ocupado) fecharLado(Lado.DIREITA, direita.pacote, encerrar = true)
        }
    }

    // --- B. lados, divisão e modos ---

    /** C20. */
    fun definirModo(texto: String?) {
        if (texto == null) return
        val anterior = modo
        val novo = ModoDeTela.doTexto(texto)
        val esquerdaOcupada = esquerda.ocupado
        val direitaOcupada = direita.ocupado
        modo = novo
        gravarComum()
        usoEsquerda = UsoDoLado.WIDGETS
        usoDireita = UsoDoLado.WIDGETS
        tela.recalcularAncoras(modo, proporcao)
        if (anterior == ModoDeTela.DOIS_APPS && novo != ModoDeTela.DOIS_APPS) {
            if (esquerdaOcupada) fecharLado(Lado.ESQUERDA, esquerda.pacote, encerrar = false)
            if (direitaOcupada) fecharLado(Lado.DIREITA, direita.pacote, encerrar = false)
            diario.info("slots do Side by Side minimizados")
        } else if (novo == ModoDeTela.APP_E_CARRO && direitaOcupada) {
            fecharLado(Lado.DIREITA, direita.pacote, encerrar = false)
        }
        if (esquerda.ocupado && esquerda.componente != null) agendarReposicao(Lado.ESQUERDA)
        if (modo == ModoDeTela.DOIS_APPS && direita.ocupado && direita.componente != null) agendarReposicao(Lado.DIREITA)
        diario.info("modo ${anterior.texto} -> ${novo.texto} (esquerda=$esquerdaOcupada direita=$direitaOcupada)")
        chrome.aoMudarOModoDeTela(novo)
        avisarPagina()
    }

    /** C22. */
    fun definirProporcao(texto: String?) {
        val nova = ProporcaoDaDivisao.doTexto(texto) ?: return
        if (nova == proporcao) return
        proporcao = nova
        gravarComum()
        tela.recalcularAncoras(modo, proporcao)
        for (lado in Lado.entries) refixar(lado)
        chrome.aoMudarAProporcao(nova)
        avisarPagina()
    }

    /** C23. */
    fun trocarLados() {
        if (!esquerda.ocupado && !direita.ocupado) return
        cancelarReposicao(Lado.ESQUERDA)
        cancelarReposicao(Lado.DIREITA)
        esquerda.trocarCom(direita)
        tela.recalcularAncoras(modo, proporcao)
        for (lado in Lado.entries) refixar(lado)
        diario.info("lados trocados: ${esquerda.pacote} / ${direita.pacote}")
        avisarPagina()
    }

    /** C24. */
    fun definirUsoDoLado(ladoTexto: String?, usoTexto: String?) {
        definirUso(Lado.doTextoDaPagina(ladoTexto), UsoDoLado.doTexto(usoTexto))
    }

    private fun definirUso(lado: Lado, novo: UsoDoLado) {
        if (uso(lado) == novo) return
        setUso(lado, novo)
        armazem.gravarUsoDoLado(modo, lado, novo)
        val s = slot(lado)
        if (novo == UsoDoLado.WIDGETS && s.ocupado) fecharLado(lado, s.pacote, encerrar = true)
        avisarPagina()
    }

    /** C1 passo 10 e C27: o lado passa a ter um app; sem fechar nada e sem avisar a página. */
    private fun marcarUsoComoApp(lado: Lado) {
        if (uso(lado) == UsoDoLado.APP) return
        setUso(lado, UsoDoLado.APP)
        armazem.gravarUsoDoLado(modo, lado, UsoDoLado.APP)
    }

    private fun setUso(lado: Lado, novo: UsoDoLado) {
        if (lado == Lado.ESQUERDA) usoEsquerda = novo else usoDireita = novo
    }

    /** C25: a preferência de lado fixo foi removida; o método segue na ponte, sem efeito. */
    @Suppress("UNUSED_PARAMETER")
    fun definirLadoPreferido(texto: String?) = Unit

    /** C26. */
    fun salvarPadrao() {
        adiar {
            modo = ModoDeTela.DOIS_APPS
            armazem.gravarPadraoDoSideBySide(proporcao, esquerda.pacote, direita.pacote, proximoEhEsquerda, modoDaInterface)
            tela.mostrarAviso("Side by Side saved as default")
            diario.info("Side by Side salvo: proporção=${proporcao.texto} apps=${esquerda.pacote}/${direita.pacote}")
            publicar()
        }
    }

    /** C27. */
    fun aoRevelarLauncher() {
        if (modo != ModoDeTela.DOIS_APPS || restauracaoFeita) return
        restauracaoFeita = true
        val appEsquerda = restauracaoEsquerda
        val appDireita = restauracaoDireita
        restauracaoEsquerda = ""
        restauracaoDireita = ""
        if (appEsquerda.isEmpty() && appDireita.isEmpty()) return
        diario.info("restaurando o Side by Side: $appEsquerda / $appDireita")
        if (appEsquerda.isNotEmpty()) restauracoes += agendador.em(RESTAURAR_ESQUERDA_MS) { restaurar(Lado.ESQUERDA, appEsquerda) }
        if (appDireita.isNotEmpty()) restauracoes += agendador.em(RESTAURAR_DIREITA_MS) { restaurar(Lado.DIREITA, appDireita) }
    }

    private fun restaurar(lado: Lado, pacote: String) {
        if (destruido || modo != ModoDeTela.DOIS_APPS) return
        marcarUsoComoApp(lado)
        abrirJanela(lado, pacote)
    }

    /** C21: o FAB do Side by Side foi ligado; recalcula, refixa os lados ocupados e avisa a página. */
    fun aoLigarOFab() {
        tela.recalcularAncoras(modo, proporcao)
        for (lado in Lado.entries) refixar(lado)
        avisarPagina()
    }

    // --- C. posição, "por cima" e chrome ---

    /** C18: refixa um lado ocupado no retângulo atual. */
    private fun refixar(lado: Lado) {
        val s = slot(lado)
        if (!s.ocupado || s.aguardandoAbertura) return
        val pacote = s.pacote
        sistema.fixarTarefa(pacote, s.tarefa, limites(lado)) { id ->
            if (id >= 0 && s.pacote == pacote) s.tarefa = id
        }
        agendarReposicao(lado)
    }

    private fun cancelarReposicao(lado: Lado) {
        cancelar(reposicao[lado])
        reposicao[lado] = null
    }

    /** C12: no máximo uma rotina por lado; 150 ms depois e a cada 200 ms, 16 execuções (a 2ª reabre a atividade). */
    private fun agendarReposicao(lado: Lado) {
        cancelarReposicao(lado)
        val s = slot(lado)
        val pacote = s.pacote
        if (pacote.isEmpty()) return
        var execucoes = 0
        lateinit var passo: Runnable
        passo = Runnable {
            if (destruido || s.pacote != pacote) {
                reposicao[lado] = null
                return@Runnable
            }
            val numero = execucoes++
            val area = limites(lado)
            sistema.fixarTarefa(pacote, s.tarefa, area) { id ->
                if (id >= 0 && s.pacote == pacote) s.tarefa = id
            }
            val componente = s.componente
            if (numero == 1 && componente != null) {
                try {
                    sistema.abrirJanelaDeLado(pacote, componente, area, reabertura = true)
                } catch (e: Exception) {
                    diario.aviso("reabrir $pacote para reposicionar falhou: ${e.message}")
                }
            }
            reposicao[lado] = if (execucoes < EXECUCOES_DA_REPOSICAO) agendador.em(PERIODO_DA_REPOSICAO_MS, passo) else null
        }
        reposicao[lado] = agendador.em(PRIMEIRA_REPOSICAO_MS, passo)
    }

    /** C29: levanta já as janelas de lado (cai para "reabrir no lado" se não conseguir). */
    private fun levantarJa() {
        if (chromePorCima || !temJanelaDeLado()) return
        for (lado in Lado.entries) {
            val s = slot(lado)
            if (!s.ocupado || s.aguardandoAbertura) continue
            if (lado == Lado.DIREITA && s.pacote == esquerda.pacote) continue
            val pacote = s.pacote
            sistema.levantarTarefa(pacote, s.tarefa) { r ->
                if (s.pacote != pacote) return@levantarTarefa
                if (r.tarefa >= 0 && r.levantou) {
                    s.tarefa = r.tarefa
                } else {
                    s.tarefa = -1
                    reabrirNoLado(lado)
                }
            }
        }
        folgaDoLevantarAte = relogio.agoraMs() + FOLGA_DO_LEVANTAR_JA_MS
    }

    /** C29: "levantar logo" (agendado). */
    private fun levantarLogo(extraMs: Long) {
        if (chromePorCima) return
        cancelar(levantarLogo)
        folgaDoLevantarAte = maxOf(folgaDoLevantarAte, relogio.agoraMs() + FOLGA_DO_LEVANTAR_LOGO_MS + extraMs)
        levantarLogo = agendador.em(ATRASO_DO_LEVANTAR_LOGO_MS + extraMs) { if (!destruido) levantarJa() }
    }

    /** C39: reabre o app do lado com a intent de reabertura (no máximo uma vez a cada 900 ms). */
    private fun reabrirNoLado(lado: Lado) {
        val s = slot(lado)
        val componente = s.componente
        if (componente == null || s.pacote.isEmpty()) return
        val agora = relogio.agoraMs()
        if (agora - ultimaReaberturaEm < INTERVALO_DA_REABERTURA_MS) return
        ultimaReaberturaEm = agora
        val area = limites(lado)
        try {
            diario.info("reabrindo ${s.pacote} em $area")
            sistema.abrirJanelaDeLado(s.pacote, componente, area, reabertura = true)
        } catch (e: Exception) {
            diario.aviso("reabrir ${s.pacote} falhou: ${e.message}")
        }
    }

    /** C38 ao voltar ao primeiro plano: no máximo a cada 250 ms, para os dois lados, fora de "chrome por cima". */
    private fun fixarNoTopoComLimite() {
        if (chromePorCima) return
        val agora = relogio.agoraMs()
        if (agora - ultimoNoTopoEm < INTERVALO_DO_NO_TOPO_MS) return
        ultimoNoTopoEm = agora
        for (lado in Lado.entries) {
            val s = slot(lado)
            if (!s.ocupado || s.aguardandoAbertura) continue
            val pacote = s.pacote
            sistema.fixarNoTopo(pacote, s.tarefa) { id -> if (id >= 0 && s.pacote == pacote) s.tarefa = id }
        }
    }

    /** C30. */
    fun definirChromePorCima(valor: Boolean) {
        if (valor == chromePorCima) return
        chromePorCima = valor
        publicar()
        aplicarPoliticaDeFoco()
        cancelar(expiracaoDoChrome)
        expiracaoDoChrome = null
        if (!valor) {
            sistema.esquecerUltimoLevantar()
            levantarLogo(0L)
        } else {
            cancelar(levantarLogo)
            levantarLogo = null
            expiracaoDoChrome = agendador.em(EXPIRACAO_DO_CHROME_MS) {
                if (destruido) return@em
                diario.info("chrome por cima expirou; voltando a levantar slots")
                definirChromePorCima(false)
            }
        }
    }

    /** C31. */
    private fun aplicarPoliticaDeFoco() {
        val temJanela = temJanelaDeLado()
        val mudou = tela.aplicarPoliticaDeFoco(naoFocavel = temJanela && !chromePorCima, noLimite = temJanela)
        if (!mudou) return
        if (!temJanela) tela.aplicarImersivo(modoDaInterface)
        diario.info(if (temJanela && !chromePorCima) "launcher NOT_FOCUSABLE enquanto há janela" else "launcher focável")
        if (temJanela) ligarVigia() else desligarVigia()
    }

    // --- vigia e sincronização ---

    private fun ligarVigia() {
        cancelar(vigia)
        vigia = agendador.em(PRIMEIRA_EXECUCAO_DO_VIGIA_MS) { rodarVigia() }
    }

    private fun desligarVigia() {
        cancelar(vigia)
        vigia = null
    }

    private fun rodarVigia() {
        if (destruido) return
        sincronizar()
        vigia = if (temJanelaDeLado()) agendador.em(PERIODO_DO_VIGIA_MS) { rodarVigia() } else null
    }

    /**
     * C33: descobre que uma janela de lado sumiu e limpa o slot (sem remover a tarefa nem encerrar o
     * app). Só quando a leitura diz "sumiu": "desconhecido" nunca limpa.
     */
    fun sincronizar() {
        if (suspensa()) return
        for (lado in Lado.entries) {
            val s = slot(lado)
            if (!s.ocupado || s.aguardandoAbertura) continue
            val pacote = s.pacote
            val geracao = s.geracao
            sistema.lerVisibilidade(pacote, s.tarefa) { leitura ->
                if (leitura != Visibilidade.SUMIU) return@lerVisibilidade
                if (destruido || s.pacote != pacote || s.geracao != geracao || suspensa()) return@lerVisibilidade
                diario.info("janela livre ${if (lado == Lado.ESQUERDA) "esquerda" else "direita"} sumiu ou minimizada (estado=$leitura)")
                cancelarReposicao(lado)
                s.limpar()
                avisarPagina()
            }
        }
    }

    private fun suspensa(): Boolean {
        val agora = relogio.agoraMs()
        return agora < aberturaEmCursoAte || agora < folgaDoLevantarAte
    }

    // --- D. projeção ---

    /** C40. */
    fun abrirProjecao(tipo: TipoDeProjecao) {
        if (tipo == TipoDeProjecao.NENHUM) return
        for (lado in Lado.entries) cancelarReposicao(lado)
        procurarELevantar(PacotesDeProjecao.pacotesDeTela(tipo)) { levantou ->
            if (levantou) avisarPagina() else resolverProjecao(tipo)
        }
    }

    /** Para cada pacote de tela, procura a tarefa e tenta levantá-la; para no primeiro que levanta. */
    private fun procurarELevantar(pacotes: List<String>, aoFinal: (Boolean) -> Unit) {
        fun tentar(indice: Int) {
            if (indice >= pacotes.size) {
                aoFinal(false)
                return
            }
            sistema.levantarPorPacote(pacotes[indice]) { r ->
                if (r.tarefa >= 0 && r.levantou) aoFinal(true) else tentar(indice + 1)
            }
        }
        tentar(0)
    }

    /** C42: pede a exibição, espera 1,5 s e (se ainda é o pedido pendente) levanta ou lança. */
    private fun resolverProjecao(tipo: TipoDeProjecao) {
        cancelar(resolucaoDaProjecao)
        projecaoPendente = tipo
        runCatching { projecao.pedirExibicao(tipo) }
        resolucaoDaProjecao = agendador.em(RESOLUCAO_DA_PROJECAO_MS) {
            if (destruido || projecaoPendente != tipo) return@em
            projecaoPendente = null
            procurarELevantar(PacotesDeProjecao.pacotesDeTela(tipo)) { levantou ->
                if (levantou) avisarPagina() else lancarProjecao(tipo)
            }
        }
    }

    private fun lancarProjecao(tipo: TipoDeProjecao) {
        val componentes = runCatching { sistema.componentesDeReserva(tipo) }.getOrDefault(emptyList())
        if (componentes.isEmpty()) {
            diario.info("projeção: sem alternativa de abertura")
            return
        }
        for (componente in componentes) {
            try {
                diario.info("projeção: abrindo $componente")
                sistema.abrirProjecao(componente, TELA_INTEIRA)
                avisarPagina()
                return
            } catch (e: Exception) {
                diario.aviso("projeção: $componente falhou: ${e.message}")
            }
        }
    }

    // --- E. página e interface ---

    /** C46. */
    fun definirModoDaInterface(texto: String?) {
        val novo = ModoDaInterface.doTexto(texto) ?: return
        modoDaInterface = novo
        publicar()
        gravarComum()
        tela.aplicarImersivo(novo)
        agendador.em(ATRASO_DO_AVISO_DA_INTERFACE_MS) { if (!destruido) avisarPagina() }
    }

    /** C45: avisa a página do layout das janelas. */
    fun avisarPagina() {
        if (destruido) return
        publicar()
        if (!tela.raizMedida()) {
            if (avisoSemRaiz == null) {
                avisoSemRaiz = agendador.em(REPETICAO_SEM_RAIZ_MS) {
                    avisoSemRaiz = null
                    avisarPagina()
                }
            }
            return
        }
        chrome.aoMudarSlots()
        aplicarPoliticaDeFoco()
        tela.enviarLayout(_estado.value)
    }

    /** J9/C45: a raiz foi medida (ou mudou de tamanho): as âncoras acompanham e a página é avisada. */
    fun aoRaizMedida() {
        tela.recalcularAncoras(modo, proporcao)
        avisarPagina()
    }

    /** C54. */
    fun abreComoJanela(pacote: String): Boolean = abrirComoJanela.contem(pacote)

    fun podeAlternarAbrirComoJanela(pacote: String): Boolean =
        pacote.isNotEmpty() &&
            pacote != pacoteProprio &&
            !catalogo.ehAppFixoDoDock(pacote) &&
            !catalogo.ehAppDaGwm(pacote) &&
            !PacotesDeProjecao.ehProjecao(pacote)

    fun alternarAbrirComoJanela(pacote: String) = abrirComoJanela.alternar(pacote)

    fun esquecerPacote(pacote: String) = abrirComoJanela.esquecer(pacote)

    private fun gravarComum() {
        armazem.gravarComum(modo, proporcao, proximoEhEsquerda, modoDaInterface)
    }

    companion object {
        /** "Ainda não aconteceu": longe o bastante para `agora - SEM_REGISTRO` não estourar. */
        private const val SEM_REGISTRO = Long.MIN_VALUE / 2

        const val LADO_PREFERIDO_AUTOMATICO = "auto"
        const val PACOTE_DAS_CONFIGURACOES_DO_CARRO = "com.android.car.settings"
        const val PACOTE_DAS_CONFIGURACOES = "com.android.settings"
        const val COMPONENTE_DAS_CONFIGURACOES = "com.android.settings/com.android.settings.Settings"

        /** Projeção abre com a tela inteira, sem desconto da barra (C42). */
        val TELA_INTEIRA = Retangulo(0, 0, 1920, 720)

        const val ABERTURA_EM_CURSO_MS = 2_000L
        const val PRIMEIRA_EXECUCAO_DO_VIGIA_MS = 700L
        const val PERIODO_DO_VIGIA_MS = 1_000L
        const val ATRASO_DA_SINCRONIZACAO_DO_RETORNO_MS = 500L
        const val PRIMEIRA_REPOSICAO_MS = 150L
        const val PERIODO_DA_REPOSICAO_MS = 200L
        const val EXECUCOES_DA_REPOSICAO = 16
        const val ATRASO_DO_LEVANTAR_LOGO_MS = 60L
        const val FOLGA_DO_LEVANTAR_LOGO_MS = 1_600L
        const val EXTRA_DO_TOQUE_NO_TOPO_MS = 700L
        const val FOLGA_DO_LEVANTAR_JA_MS = 1_200L
        const val INTERVALO_DO_NO_TOPO_MS = 250L
        const val INTERVALO_DA_REABERTURA_MS = 900L
        const val EXPIRACAO_DO_CHROME_MS = 20_000L
        const val RESOLUCAO_DA_PROJECAO_MS = 1_500L
        const val RESTAURAR_ESQUERDA_MS = 500L
        const val RESTAURAR_DIREITA_MS = 900L
        const val REPETICAO_SEM_RAIZ_MS = 300L
        const val ATRASO_DO_AVISO_DA_INTERFACE_MS = 250L
    }
}
