// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import android.content.SharedPreferences
import br.com.deepgwt.ponte.Cancelavel
import br.com.deepgwt.ponte.Fontes
import br.com.deepgwt.ponte.Painel
import br.com.deepgwt.ponte.PonteDosTemas
import br.com.deepgwt.ponte.ScriptsDaPagina
import br.com.deepgwt.telas.AvisoNoCirculo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * O projetor do display do painel sem a parte Android (`projetores-painel.md`, seção 4): decide
 * QUANDO mandar o quê para a página e implementa o lado [Painel] da ponte. A janela (WebView,
 * máscaras) vem por [JanelaDoPainel]; relógio e threads por [TempoDoPainel].
 *
 * Threads: todo o estado vive na principal. [valorDoCarroRecebido] chega de threads do motor;
 * [batimento], [avisoAtivo] e [avisoDispensado] são lidos da thread do WebView (voláteis); as
 * perguntas lentas ao lançador vão para [TempoDoPainel.emFundo].
 */
class NucleoDoPainel(
    private val ligacoes: LigacoesDoPainel,
    private val janela: JanelaDoPainel,
    private val tempo: TempoDoPainel,
    private val preferencias: SharedPreferences,
    private val carro: CarroDoPainel,
    private val paginas: PaginasDoPainel,
    private val temas: PapelDeParede.Temas,
    private val registro: RegistroDoPainel,
    fontes: Fontes,
    criarPonte: (Painel, Fontes) -> PonteDosTemas,
    telaPrincipalLigada: Boolean,
    /** Diagnóstico da projeção só em build depurável (Decisão 11). */
    private val depuravel: Boolean,
    /** Verdadeiro uma vez por processo (K.7). */
    private val primeiraCargaDoProcesso: () -> Boolean,
    private val horaLocal: () -> String = { SimpleDateFormat("HH:mm", Locale.US).format(Date()) },
) : Painel {

    companion object {
        const val VIGIA_MS = 5_000L
        const val SEM_BATIDA_MS = 15_000L
        const val CORTINA_MS = 450L
        const val VEU_DEPOIS_DO_QUADRO_MS = 700L
        const val VEU_TETO_MS = 5_000L
        const val RELOGIO_MS = 30_000L
        const val DEPURACAO_ATRASO_MS = 250L
        const val DEPURACAO_INTERVALO_MS = 1_500L
        const val MAOS_LIVRES_MS = 6_000L
        const val RASTRO_INTERVALO_MS = 1_000L
        const val FOLGA_DO_FIM_DO_POPUP_MS = 20L

        private val TELA_DESLIGADA = setOf(-1, 15, 14, 10)

        /** A.7: `engine_state` não nulo e fora de {−1, 15, 14, 10} (11 = parado em READY). */
        @JvmStatic
        fun telaPrincipalLigada(estadoDoMotor: String?): Boolean {
            if (estadoDoMotor == null) return false
            val texto = estadoDoMotor.trim()
            val numero = texto.toIntOrNull() ?: texto.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
            return numero == null || numero !in TELA_DESLIGADA
        }

        /** As chaves que viram trabalho na principal (o resto só passa pela ponte). */
        private val RELEVANTES: Set<String> = EstadoParaPagina.CHAVES_DO_AVULSO + AvisosDoCarro.VIGIADAS
    }

    private val p = PreferenciasDoPainel(preferencias)
    private val apps: AppsNoPainel get() = ligacoes.apps

    /** A ponte deste WebView (uma por projetor; R.6). */
    val ponte: PonteDosTemas = criarPonte(this, fontes)

    // ── Estado (principal) ──
    @Volatile
    private var encerrado = false
    private var iniciado = false

    /** O cartão atual (repassado; o projetor nunca prevê nem escreve cartão). */
    var cartao: Int = seguro(ArbitroDoCartao.CARTAO_DO_CARRO) { ligacoes.cartaoAtual() }
        private set

    private var telaLigada = telaPrincipalLigada

    private var geracaoDaCarga = 0
    private var carregando = false
    private var aguardandoInicio = false

    /** A página carregada (ou entrando): o "tema em uso" das máscaras e da ponte. */
    var paginaEmUso: PaginaDoPainel? = null
        private set
    private var minBridgeDaCarga: String? = null

    @Volatile
    private var ultimaBatida = 0L
    private var parRecarregado: Pair<String, Long>? = null
    private var veuAtivo = false
    private var veuGeracao = 0
    private var veuTeto: Cancelavel? = null
    private var conteudoVisivel = false
    private var cortina: Cancelavel? = null
    private var lacos: List<Cancelavel> = emptyList()
    private var observacaoDoCarro: Cancelavel? = null

    // ── Telemetria ──
    private val ultimoValor = ConcurrentHashMap<String, String>()
    private val rastro = ConcurrentHashMap<String, Long>()

    @Volatile
    private var paginaOriginalCarregada = false
    private val avulsos = EstadoParaPagina.Avulsos({ valorConhecido(it) }, p)

    // ── Projeção ──
    @Volatile
    private var ultimoCarPlaySaudavel = 0L
    private var projecaoEnviada: ProjecaoEnviada? = null
    private var projecaoAtual = EstadoDaProjecao.NENHUMA
    private var ultimaDepuracao = Long.MIN_VALUE / 2
    private var depuracaoAgendada: Cancelavel? = null

    // ── Apps ──
    private var appNoDisplay1 = false
    private var appNoDisplay3 = false
    private var versaoDosApps = 0

    /** Só na thread de fundo. */
    private val tamanhosAplicados = HashMap<String, AlvoDoApp>()
    private var vaoAtual: Retangulo? = null

    // ── Máscaras ──
    private val sobreposicoes = LinkedHashMap<String, Boolean>()
    private var resumoDasMascaras: String? = null
    private var geracaoDasMascaras = 0

    // ── Círculo e mãos-livres ──
    private var popUpAte = 0L
    private var velocidade = 0f
    private var andando = false
    private var ocupaCirculo = false
    private var fimDoPopUp: Cancelavel? = null
    private var maosLivres: Cancelavel? = null

    private val saidaDoSelo = object : SeloDeAvisos.Saida {
        override fun popUp() {
            popUpAte = tempo.agora() + AvisoNoCirculo.POPUP_MS
            seguro(Unit) { ligacoes.camadas.avisoNovoDoCarro() }
            fimDoPopUp?.cancelar()
            fimDoPopUp = tempo.depois(AvisoNoCirculo.POPUP_MS + FOLGA_DO_FIM_DO_POPUP_MS) { reavaliarCirculo() }
        }

        override fun mudou(ativo: Boolean, dispensado: Boolean, motivo: String) {
            esquecerTamanhos()
            diario("cluster_warning_state_changed", mapOf("active" to ativo, "dismissed" to dispensado, "reason" to motivo))
            passe(redesenhar = true)
            ressincronizar(setOf(RegrasDosApps.DISPLAY_DO_PAINEL))
        }

        override fun enviar(ativo: Boolean, dispensado: Boolean) {
            ponte.controles(linkedMapOf("warningActive" to ativo, "warningDismissed" to dispensado))
            reavaliarCirculo()
        }
    }

    private val selo = SeloDeAvisos(
        agora = { tempo.agora() },
        esperar = { ms, tarefa -> tempo.depois(ms, tarefa) },
        lerDoCache = { carro.ler(it) },
        saida = saidaDoSelo,
    )

    private val ouvinteDePreferencias =
        SharedPreferences.OnSharedPreferenceChangeListener { _, chave -> preferenciaMudou(chave) }

    // ═══════════════════════════ A/B. Criação e fim ═══════════════════════════

    /** B.5, na principal. */
    fun iniciar() {
        if (iniciado || encerrado) return
        iniciado = true
        ultimaBatida = tempo.agora()
        lacos = listOf(
            tempo.repetir(RELOGIO_MS) { relogio() },
            tempo.repetir(VIGIA_MS) { vigia() },
        )
        ponte.iniciar()
        janela.criarConteudo(ponte.objetoJs)
        seguro(Unit) { janela.montarCamadas(cartao, ocupaCirculo) }
        val versao = versaoDosApps
        tempo.emFundo {
            val um = seguro(false) { apps.algumAppNoDisplay(RegrasDosApps.DISPLAY_DE_TRAS) }
            val tres = seguro(false) { apps.algumAppNoDisplay(RegrasDosApps.DISPLAY_DO_PAINEL) }
            tempo.naPrincipal {
                if (encerrado || versao != versaoDosApps) return@naPrincipal
                appNoDisplay1 = um
                appNoDisplay3 = tres
                if (um || tres) passe(redesenhar = true)
            }
        }
        selo.semear()
        passe()
        observacaoDoCarro = carro.observar { chave, valor -> valorDoCarroRecebido(chave, valor) }
        preferencias.registerOnSharedPreferenceChangeListener(ouvinteDePreferencias)
        pedirCarga("criação")
    }

    /** P, na principal. */
    fun encerrar() {
        if (encerrado) return
        encerrado = true
        seguro(Unit) { janela.desmontarCamadas() }
        for (laco in lacos) laco.cancelar()
        lacos = emptyList()
        for (c in listOf(cortina, veuTeto, fimDoPopUp, maosLivres, depuracaoAgendada)) c?.cancelar()
        selo.encerrar()
        observacaoDoCarro?.cancelar()
        observacaoDoCarro = null
        preferencias.unregisterOnSharedPreferenceChangeListener(ouvinteDePreferencias)
        ponte.encerrar()
        seguro(Unit) { janela.destruir() }
        ultimoValor.clear()
        rastro.clear()
        tempo.emFundo { tamanhosAplicados.clear() }
    }

    /** A.7 (só na transição; Decisão 7): a tela principal ligou ou desligou. */
    fun telaPrincipal(ligada: Boolean) {
        if (encerrado || ligada == telaLigada) return
        telaLigada = ligada
        if (!ligada) {
            janela.raizVisivel(false)
            conteudoMudou(false)
        } else {
            passe()
        }
    }

    /** Recarga pedida de fora (ex.: o tema ativo foi semeado de novo nesta abertura). */
    fun recarregar(motivo: String) {
        pedirCarga(motivo)
    }

    // ═══════════════════════════ C. Carga ═══════════════════════════

    private fun pedirCarga(motivo: String) {
        if (encerrado) return
        val g = ++geracaoDaCarga
        val escolhida = seguro(null) { paginas.escolher(p.pastaAtiva()) } ?: return
        paginaEmUso = escolhida
        registro.info("carga pedida ($motivo): ${escolhida.pasta.ifEmpty { "painel original" }}")
        tempo.emDisco {
            val lida = seguro(null) { paginas.ler(escolhida) }
            tempo.naPrincipal {
                if (g != geracaoDaCarga || encerrado) return@naPrincipal
                if (lida == null) {
                    registro.erro("a página ${escolhida.origem} não pôde ser lida")
                    return@naPrincipal
                }
                executarCarga(lida.first, lida.second, g)
            }
        }
    }

    /** C.4. */
    private fun executarCarga(pagina: PaginaDoPainel, html: String, g: Int) {
        paginaEmUso = pagina
        minBridgeDaCarga = pagina.minBridgeVersion
        paginaOriginalCarregada = false
        ponte.antesDeCarregar()
        carregando = true
        aguardandoInicio = true
        ultimaBatida = tempo.agora()
        cobrirComVeu(g)
        janela.carregar(pagina.base, html)
    }

    /** "Página começou" (do cliente do WebView). */
    fun paginaComecou() {
        if (carregando) aguardandoInicio = false
    }

    /** "Página terminou" (do cliente do WebView): só vale para a carga atual. */
    fun paginaTerminou() {
        if (encerrado || !carregando || aguardandoInicio) return
        fimDaCarga()
    }

    /** C.5. */
    private fun fimDaCarga() {
        val g = geracaoDaCarga
        carregando = false
        ultimaBatida = tempo.agora()
        paginaOriginalCarregada = paginaEmUso?.tema == null
        ponte.aoTerminarDeCarregar(minBridgeDaCarga)
        ponte.aplicarAjustesVisuais()
        ponte.reavaliarFarol(true)
        seguro(Unit) { ligacoes.camadas.paginaRecarregou() }
        ponte.controles(EstadoParaPagina.lote(entradas()))
        ultimoValor.clear()
        val enviado = cartao
        ponte.cartao(enviado) { resultado ->
            if (resultado != ScriptsDaPagina.CARTAO_CONTRATO) diario("card_push_failed", mapOf("result" to resultado, "pushed" to enviado))
        }
        redesenharMascaras()
        selo.semear()
        selo.recalcular("carga")
        projecaoEnviada = null
        passe(depois = { tirarVeuQuandoAssentar(g) })
        ponte.controle("clockTime", horaLocal())
        if (primeiraCargaDoProcesso()) tempo.emFundo { seguro(Unit) { apps.abrirAppDeArranque() } }
    }

    /** C.6: véu só com `enableVirtualCluster = false`. */
    private fun cobrirComVeu(g: Int) {
        veuTeto?.cancelar()
        veuTeto = null
        if (p.painelVirtual()) {
            veuAtivo = false
            return
        }
        veuAtivo = true
        veuGeracao = g
        janela.alfaDoWebView(0f)
        registro.info("Véu do painel original: coberto (carga)")
        veuTeto = tempo.depois(VEU_TETO_MS) { if (veuGeracao == g) tirarVeu("teto de 5 s") }
    }

    private fun tirarVeuQuandoAssentar(g: Int) {
        if (!veuAtivo || veuGeracao != g) return
        janela.quandoOQuadroEstiverPronto {
            tempo.depois(VEU_DEPOIS_DO_QUADRO_MS) { if (veuGeracao == g) tirarVeu("quadro pronto") }
        }
    }

    private fun tirarVeu(motivo: String) {
        if (!veuAtivo || encerrado) return
        veuAtivo = false
        veuTeto?.cancelar()
        veuTeto = null
        janela.alfaDoWebView(if (deveAparecer()) 1f else 0f)
        registro.info("Véu do painel original: retirado ($motivo)")
    }

    /** C.8: vigia de 5 s. */
    private fun vigia() {
        if (encerrado) return
        val agora = tempo.agora()
        val semBatida = agora - ultimaBatida
        if (semBatida > SEM_BATIDA_MS && p.projetorEIntegracao() && janela.raizEstaVisivel && janela.webViewEstaVisivel) {
            diario("webview_watchdog_reload", mapOf("missedHeartbeatMs" to semBatida, "rootVisible" to janela.raizEstaVisivel))
            pedirCarga("vigia")
            ultimaBatida = agora
        }
        atualizarProjecao()
    }

    /** C.9. A janela devolve `true` ao WebView. */
    fun renderMorreu(caiu: Boolean) {
        if (encerrado) return
        diario("webview_render_process_gone", mapOf("didCrash" to caiu))
        seguro(Unit) { janela.recriarWebView() }
        pedirCarga("render morto")
    }

    /** B.4: console da página → diário. */
    fun console(nivel: String, linha: Int, origem: String?, mensagem: String?) {
        if (!registro.diarioLigado) return
        val fonte = origem.orEmpty()
        diario(
            "webview_console",
            mapOf("level" to nivel, "line" to linha, "source" to fonte.takeLast(120), "message" to mensagem),
            comuns = false,
        )
    }

    // ═══════════════════════════ D. Visibilidade ═══════════════════════════

    /** D.1. */
    private fun deveAparecer(): Boolean = p.projetorEIntegracao() && telaLigada

    private fun aplicarVisibilidadeDaRaiz() {
        val deve = deveAparecer()
        janela.raizVisivel(deve)
        conteudoMudou(deve)
    }

    /** D.4: escondido → visível: batimento = agora e a cortina presa 450 ms depois. */
    private fun conteudoMudou(visivel: Boolean) {
        if (visivel && !conteudoVisivel) {
            ultimaBatida = tempo.agora()
            cortina?.cancelar()
            cortina = tempo.depois(CORTINA_MS) { liberarCortina() }
        }
        conteudoVisivel = visivel
    }

    private fun liberarCortina() {
        if (encerrado) return
        ponte.liberarCortina { resposta ->
            val texto = resposta?.trim().orEmpty()
            if (texto.isEmpty() || texto == "null" || texto.replace(" ", "").contains("\"presas\":0")) return@liberarCortina
            diario("cluster_boot_curtain_released", mapOf("theme" to paginaEmUso?.pasta.orEmpty(), "detalhe" to texto))
        }
    }

    /**
     * D.2: cobertura e vão em fundo (perguntas lentas); na principal, máscaras, o script de
     * visibilidade, a raiz e o estado da projeção.
     */
    private fun passe(redesenhar: Boolean = false, projecaoDada: EstadoDaProjecao? = null, depois: (() -> Unit)? = null) {
        if (encerrado) return
        val cartaoAgora = cartao
        val conhecido1 = appNoDisplay1
        tempo.emFundo {
            val estado = projecaoDada ?: lerProjecao()
            val cobertura = calcularCobertura(estado, cartaoAgora, conhecido1)
            tempo.naPrincipal { if (!encerrado) aplicarPasse(estado, cobertura, redesenhar, depois) }
        }
    }

    private fun aplicarPasse(estado: EstadoDaProjecao, cobertura: CoberturaDosApps, redesenhar: Boolean, depois: (() -> Unit)?) {
        projecaoAtual = estado
        val vaoMudou = cobertura.vao != vaoAtual
        vaoAtual = cobertura.vao
        if (vaoMudou || redesenhar) redesenharMascaras()
        ponte.controles(
            linkedMapOf(
                "clusterEnabled" to p.painelVirtual(),
                "originalInfo" to p.informacoesExtras(),
                "clusterBackground" to PapelDeParede.haFundo(PapelDeParede.Escolha.de(p), temas),
                "appInDash" to cobertura.appInDash,
            ),
        )
        val deve = deveAparecer()
        janela.raizVisivel(deve)
        janela.tornarWebViewVisivel()
        janela.alfaDoWebView(if (veuAtivo) 0f else 1f)
        conteudoMudou(deve)
        enviarProjecao(estado, forcar = false)
        depois?.invoke()
    }

    /** K.1–K.4 (fundo). */
    private fun calcularCobertura(estado: EstadoDaProjecao, cartaoAgora: Int, conhecido1: Boolean): CoberturaDosApps {
        val displays = if (conhecido1) setOf(RegrasDosApps.DISPLAY_DE_TRAS, RegrasDosApps.DISPLAY_DO_PAINEL) else setOf(RegrasDosApps.DISPLAY_DO_PAINEL)
        val lista = seguro(emptyList()) { apps.appsGerenciados(displays) }
        return RegrasDosApps.cobertura(
            lista,
            { d -> larguraDoDisplay(d) },
            cartaoAgora,
            estado.espelho || estado.preparando,
        ) { seguro(null) { apps.retanguloDoEspelhoNoDisplay3() } }
    }

    private fun larguraDoDisplay(display: Int): Int =
        seguro(EscolhaDaTela.LARGURA) { apps.resolucao(display).getOrElse(0) { EscolhaDaTela.LARGURA } }

    // ═══════════════════════════ E/F. Telemetria e lote ═══════════════════════════

    /** E.1–E.3 (thread do motor): usa sempre o valor recebido (o cache ainda tem o anterior). */
    fun valorDoCarroRecebido(chave: String, valor: String?) {
        if (valor == null || encerrado) return
        if (ultimoValor.put(chave, valor) == valor) return
        ponte.valorDoCarro(chave, valor)
        rastrear(chave, valor)
        if (chave in RELEVANTES) tempo.naPrincipal { telemetriaNaPrincipal(chave, valor) }
    }

    /** Q.4: o rastro `[onDataChanged TRACE]` da página original, 1 linha por chave a cada 1 s. */
    private fun rastrear(chave: String, valor: String) {
        if (!paginaOriginalCarregada || !registro.diarioLigado || !ponte.assinada(chave)) return
        val agora = tempo.agora()
        val ultimo = rastro[chave]
        if (ultimo != null && agora - ultimo < RASTRO_INTERVALO_MS) return
        rastro[chave] = agora
        diario("webview_console", mapOf("level" to "LOG", "message" to "[onDataChanged TRACE] key=$chave value=$valor"), comuns = false)
    }

    /** E.4–E.6 e I (principal). */
    private fun telemetriaNaPrincipal(chave: String, valor: String) {
        if (encerrado) return
        for ((nome, v) in avulsos.de(chave, valor)) ponte.controle(nome, v)
        if (chave == EstadoParaPagina.VELOCIDADE) velocidadeDoCirculo(valor)
        if (AvisosDoCarro.vigiada(chave)) selo.valorNovo(chave, valor)
    }

    private fun valorConhecido(chave: String): String? = ultimoValor[chave] ?: seguro(null) { carro.ler(chave) }

    private fun entradas() = EstadoParaPagina.Entradas(
        carro = { valorConhecido(it) },
        preferencias = p,
        projecao = projecaoAtual,
        avisoAtivo = selo.avisoAtivo,
        avisoDispensado = selo.avisoDispensado,
    )

    // ═══════════════════════════ G. Cartão ═══════════════════════════

    /** G.1–G.7 (principal). */
    fun cartaoMudou(n: Int) {
        if (encerrado) return
        val anterior = cartao
        cartao = n
        val inicio = tempo.agora()
        redesenharMascaras()
        seguro(Unit) { ligacoes.camadas.cartaoMudou(n) }
        val ultimo = projecaoEnviada?.estado
        val preparando = seguro(false) { apps.preparandoDisplay3() }
        val desejado = seguro(false) { apps.carPlayDesejadoNoPainel() }
        val segura = SegurancaDoCarPlay.vale(ultimoCarPlaySaudavel, inicio, desejado, preparando)
        val rapido = !appNoDisplay3 && !preparando && !segura && (ultimo == null || !ultimo.ativa)
        val conhecidos = LinkedHashSet<Int>().apply {
            if (appNoDisplay1) add(RegrasDosApps.DISPLAY_DE_TRAS)
            if (appNoDisplay3) add(RegrasDosApps.DISPLAY_DO_PAINEL)
        }
        // Sempre pela fila de fundo, para dois cartões seguidos chegarem à página na ordem.
        tempo.emFundo {
            val estado = if (rapido) EstadoDaProjecao.NENHUMA else lerProjecao()
            val geridos = conhecidos.isNotEmpty() && seguro(emptyList()) { apps.appsGerenciados(conhecidos) }.isNotEmpty()
            tempo.naPrincipal { if (!encerrado) entregarCartao(anterior, n, estado, ultimo, geridos, rapido, inicio) }
        }
    }

    private fun entregarCartao(
        anterior: Int, n: Int, estado: EstadoDaProjecao, ultimo: EstadoDaProjecao?, geridos: Boolean, rapido: Boolean, inicio: Long,
    ) {
        val d = DecisaoDoCartao.decidir(anterior, n, estado, ultimo, geridos)
        if (d.ressincronizar) esquecerTamanhos()
        if (d.empurrar) {
            projecaoAtual = estado
            enviarProjecao(estado, d.forcar)
        }
        aplicarVisibilidadeDaRaiz()
        ponte.cartao(n) { resultado ->
            if (resultado != ScriptsDaPagina.CARTAO_CONTRATO) diario("card_push_failed", mapOf("result" to resultado, "pushed" to n))
        }
        if (d.visibilidade) passe(projecaoDada = estado)
        if (d.ressincronizar) ressincronizar(setOf(RegrasDosApps.DISPLAY_DO_PAINEL))
        if (n == ArbitroDoCartao.CARTAO_DO_MENU || n == ArbitroDoCartao.CARTAO_DO_AR) selo.esquecerDispensadoAoEntrarNoCartao()
        if (d.entrada) ponte.controles(EstadoParaPagina.entradaDoCartao(n, entradas()))
        diario(
            "cluster_card_change",
            mapOf(
                "from" to anterior, "to" to n, "elapsedMs" to (tempo.agora() - inicio), "fastPath" to rapido,
                "projectionActive" to d.projecaoAtiva, "projectionStale" to d.velha, "managedSecondary" to d.appsGerenciados,
                "boundsMayChange" to d.limitesPodemMudar, "visibilityWork" to d.visibilidade, "syncApps" to d.ressincronizar,
            ),
        )
    }

    // ═══════════════════════════ H. Teclas ═══════════════════════════

    /** H.1: ←/→ trocam o cartão no carro; as outras vão à página (a ponte filtra e enfileira). */
    fun teclaDoVolante(tecla: TeclaDoVolante) {
        if (encerrado || tecla.trocaCartao) return
        ponte.tecla(tecla.name)
    }

    // ═══════════════════════════ I. Avisos e círculo ═══════════════════════════

    private fun velocidadeDoCirculo(valor: String) {
        val v = valor.trim().toFloatOrNull() ?: return
        if (v.isNaN()) return
        velocidade = v
        val agoraAndando = AvisoNoCirculo.andando(v)
        if (agoraAndando != andando) {
            andando = agoraAndando
            reavaliarCirculo()
        }
    }

    /** I.9. */
    private fun reavaliarCirculo() {
        if (encerrado) return
        val ocupa = AvisoNoCirculo.ocupa(selo.avisoAtivo, tempo.agora(), popUpAte, velocidade)
        if (ocupa == ocupaCirculo) return
        ocupaCirculo = ocupa
        seguro(Unit) { ligacoes.camadas.avisoNoCirculoMudou(ocupa) }
    }

    // ═══════════════════════════ J. Projeção ═══════════════════════════

    /** J.1–J.2 (fundo). */
    private fun lerProjecao(): EstadoDaProjecao {
        val agora = tempo.agora()
        val carPlay = seguro(false) { apps.carPlayNoDisplay3() }
        if (carPlay) ultimoCarPlaySaudavel = agora
        val preparando = seguro(false) { apps.preparandoDisplay3() }
        val desejado = seguro(false) { apps.carPlayDesejadoNoPainel() }
        val segura = SegurancaDoCarPlay.vale(ultimoCarPlaySaudavel, agora, desejado, preparando)
        val espelho = seguro(false) { apps.espelhoNoDisplay3() }
        return EstadoDaProjecao(carPlay || segura, espelho || segura, preparando)
    }

    /** J.3–J.4. */
    private fun enviarProjecao(estado: EstadoDaProjecao, forcar: Boolean) {
        val porCima = cartao == ArbitroDoCartao.CARTAO_DO_MENU || cartao == ArbitroDoCartao.CARTAO_DO_AR
        val ultimo = projecaoEnviada
        val forcado = forcar || ultimo == null
        val mapa = LinkedHashMap<String, Any?>()
        if (forcado || ultimo!!.estado.carPlay != estado.carPlay) mapa["carPlayInDash"] = estado.carPlay
        if (forcado || ultimo!!.estado.espelho != estado.espelho) mapa["projectionMirrorInDash"] = estado.espelho
        if (forcado || ultimo!!.estado.preparando != estado.preparando) mapa["projectionPreparingD3"] = estado.preparando
        if (forcado || ultimo!!.cartaoPorCima != porCima) mapa["projectionCardOverlayAllowed"] = porCima
        projecaoEnviada = ProjecaoEnviada(estado, porCima)
        if (mapa.isNotEmpty()) ponte.controles(mapa)
        if (depuravel && (estado.ativa || forcado)) agendarDepuracao()
    }

    private fun agendarDepuracao() {
        if (depuracaoAgendada != null) return
        if (tempo.agora() - ultimaDepuracao < DEPURACAO_INTERVALO_MS) return
        depuracaoAgendada = tempo.depois(DEPURACAO_ATRASO_MS) {
            depuracaoAgendada = null
            ultimaDepuracao = tempo.agora()
            ponte.depuracaoDaProjecao { resposta -> diario("projection_debug", mapOf("result" to resposta)) }
        }
    }

    /** J.5 (a cada tique do vigia). */
    private fun atualizarProjecao() {
        tempo.emFundo {
            val estado = lerProjecao()
            tempo.naPrincipal {
                if (encerrado) return@naPrincipal
                val ultimo = projecaoEnviada?.estado
                if (ultimo == estado) return@naPrincipal
                if (ultimo != null) {
                    diario(
                        "projection_state_changed",
                        mapOf("carPlayInDash" to estado.carPlay, "projectionMirrorInDash" to estado.espelho, "projectionPreparingD3" to estado.preparando),
                    )
                }
                passe(projecaoDada = estado)
            }
        }
    }

    // ═══════════════════════════ K. Apps ═══════════════════════════

    /** K.6. */
    fun appsNoDisplayMudaram(display: Int, algumApp: Boolean) {
        if (encerrado) return
        versaoDosApps++
        when (display) {
            RegrasDosApps.DISPLAY_DO_PAINEL -> {
                appNoDisplay3 = algumApp
                if (!algumApp) esquecerTamanhos()
                passe(redesenhar = true)
                ressincronizar(setOf(RegrasDosApps.DISPLAY_DO_PAINEL))
                ponte.appDoDisplayMudou(RegrasDosApps.DISPLAY_DO_PAINEL)
            }
            RegrasDosApps.DISPLAY_DE_TRAS -> {
                appNoDisplay1 = algumApp
                passe()
                ponte.appDoDisplayMudou(RegrasDosApps.DISPLAY_DE_TRAS)
            }
        }
    }

    /** K.6. */
    fun geometriaDosAppsMudou() {
        if (encerrado) return
        passe(redesenhar = true)
        ressincronizar(setOf(RegrasDosApps.DISPLAY_DO_PAINEL))
    }

    /** K.5: o vão antes de o lançador mover um app para o display 3 (redesenha na hora). */
    fun prepararVaoNoDisplay3(esquerda: Int, topo: Int, direita: Int, base: Int) {
        if (encerrado) return
        val largura = RegrasDosApps.larguraAplicada(
            RegrasDosApps.DISPLAY_DO_PAINEL, cartao, larguraDoDisplay(RegrasDosApps.DISPLAY_DO_PAINEL), esquerda, direita - esquerda,
        )
        vaoAtual = Retangulo(esquerda, topo, esquerda + largura, base)
        redesenharMascaras()
    }

    private fun esquecerTamanhos() {
        tempo.emFundo { tamanhosAplicados.clear() }
    }

    /** K.3 (fundo): redimensiona o que mudou desde o último aplicado. */
    private fun ressincronizar(displays: Set<Int>) {
        if (encerrado || displays.isEmpty()) return
        val cartaoAgora = cartao
        tempo.emFundo {
            val lista = seguro(emptyList()) { apps.appsGerenciados(displays) }
            for (app in lista) {
                if (app.display !in displays) continue
                val w = larguraDoDisplay(app.display)
                if (w <= 0) continue
                val alvo = RegrasDosApps.alvo(app, cartaoAgora, w) ?: continue
                val chave = "${app.pacote}@${app.display}"
                if (tamanhosAplicados[chave] == alvo) continue
                seguro(Unit) { apps.redimensionar(app, alvo.x, alvo.y, alvo.largura, alvo.altura) }
                tamanhosAplicados[chave] = alvo
            }
        }
    }

    private fun displaysComApps(): Set<Int> = LinkedHashSet<Int>().apply {
        if (appNoDisplay1) add(RegrasDosApps.DISPLAY_DE_TRAS)
        if (appNoDisplay3) add(RegrasDosApps.DISPLAY_DO_PAINEL)
    }

    // ═══════════════════════════ L. Máscaras ═══════════════════════════

    /** L.1–L.10: plano e bitmaps na thread de disco; aplicar na principal (descarta o que ficou velho). */
    private fun redesenharMascaras() {
        if (encerrado) return
        val g = ++geracaoDasMascaras
        val escolha = PapelDeParede.Escolha.de(p)
        val tema = paginaEmUso?.pasta.orEmpty()
        val mascaras = paginaEmUso?.tema?.mascaras
        val sobre = LinkedHashMap(sobreposicoes)
        val cartaoAgora = cartao
        val vao = vaoAtual
        tempo.emDisco {
            val papel = PapelDeParede.imagem(escolha, tema, temas)
            val plano = PlanoDasMascaras.planejar(tema, papel, mascaras, sobre, cartaoAgora, vao) { nome ->
                temas.arquivoNoTema(tema, nome)?.let { ImagemDoPainel.Arquivo(it) }
            }
            val composto = seguro(null) { janela.comporMascaras(plano) }
            tempo.naPrincipal {
                if (g != geracaoDasMascaras || encerrado) return@naPrincipal
                janela.aplicarMascaras(composto)
                val resumo = plano.resumo()
                if (resumo != resumoDasMascaras) {
                    resumoDasMascaras = resumo
                    diario("native_mask_state", mapOf("message" to resumo), comuns = false)
                }
            }
        }
    }

    // ═══════════════════════════ M. Preferências ═══════════════════════════

    private fun preferenciaMudou(chave: String?) {
        if (encerrado || chave == null) return
        when (chave) {
            PreferenciasDoPainel.TEMA_ATIVO, PreferenciasDoPainel.CARIMBO_DO_TEMA -> trocaDeTema()
            PreferenciasDoPainel.HODOMETRO_E_REVISAO -> {
                val ligado = p.booleano(PreferenciasDoPainel.HODOMETRO_E_REVISAO, true)
                ponte.controle("enableOdometer", ligado)
                ponte.controle("enableRevisionWarning", ligado && p.inteiro(PreferenciasDoPainel.REVISAO_KM, 0) > 0)
            }
            PreferenciasDoPainel.REVISAO_KM -> {
                val km = p.inteiro(PreferenciasDoPainel.REVISAO_KM, 0)
                ponte.controle("nextRevisionKm", km)
                ponte.controle("enableRevisionWarning", p.booleano(PreferenciasDoPainel.HODOMETRO_E_REVISAO, true) && km > 0)
            }
            PreferenciasDoPainel.REVISAO_DATA -> ponte.controle("nextRevisionDate", p.longo(PreferenciasDoPainel.REVISAO_DATA, 0L))
            PreferenciasDoPainel.UNIDADE_DO_COMBUSTIVEL ->
                ponte.controle("fuelDisplayUnit", RotulosDoPainel.unidadeDoCombustivel(p.texto(PreferenciasDoPainel.UNIDADE_DO_COMBUSTIVEL, "liters")))
            PreferenciasDoPainel.ESCONDER_VELOCIMETRO_NO_MAPA ->
                ponte.controle("hideSpeedometerOnMaps", p.booleano(PreferenciasDoPainel.ESCONDER_VELOCIMETRO_NO_MAPA, false))
            PreferenciasDoPainel.ANALISE_DE_VIAGEM_ATIVA ->
                ponte.controle("tripAnalysisActive", p.booleano(PreferenciasDoPainel.ANALISE_DE_VIAGEM_ATIVA, false))
            PreferenciasDoPainel.ANALISE_DE_VIAGEM_NOTA -> {
                val nota = p.inteiro(PreferenciasDoPainel.ANALISE_DE_VIAGEM_NOTA, -1)
                ponte.controle("tripAnalysisScore", if (nota < 0) null else nota)
            }
            PreferenciasDoPainel.FUNDO_LIGADO, PreferenciasDoPainel.FUNDO_TIPO, PreferenciasDoPainel.FUNDO_VALOR -> {
                janela.esquecerMascaraGlobal()
                redesenharMascaras()
            }
            PreferenciasDoPainel.PROJETOR_LIGADO, PreferenciasDoPainel.INTEGRACAO_LIGADA, PreferenciasDoPainel.PAINEL_VIRTUAL,
            PreferenciasDoPainel.INFORMACOES_EXTRAS, PreferenciasDoPainel.TELA_DO_PAINEL, PreferenciasDoPainel.NOME_DO_TEMA,
            -> Unit
            else -> return
        }
        aplicarVisibilidadeDaRaiz()
        passe()
    }

    /** C.7: uma carga por troca (as duas preferências mudam juntas e cada uma avisa uma vez). */
    private fun trocaDeTema() {
        val par = p.pastaAtiva() to p.carimboDoTema()
        if (par == parRecarregado) return
        parRecarregado = par
        janela.esquecerMascaraGlobal()
        seguro(Unit) { apps.areaDoTema(null) }
        sobreposicoes.clear()
        pedirCarga("troca de tema")
        redesenharMascaras()
    }

    // ═══════════════════════════ N. Relógio, ar, mãos-livres ═══════════════════════════

    private fun relogio() {
        if (encerrado) return
        ponte.controle("clockTime", horaLocal())
        ponte.reavaliarFarol(false)
    }

    fun arNoMaximo(ativo: Boolean) {
        if (encerrado) return
        ponte.controle("maxauto", if (ativo) 1 else 0)
    }

    fun maosLivres(ativo: Boolean) {
        if (encerrado) return
        maosLivres?.cancelar()
        maosLivres = null
        janela.avisoDoMaosLivres(ativo)
        if (ativo) maosLivres = tempo.depois(MAOS_LIVRES_MS) { if (!encerrado) janela.avisoDoMaosLivres(false) }
    }

    // ═══════════════════════════ O. Ajustes visuais ═══════════════════════════

    fun aplicarEscalaDosNumeros() {
        if (!encerrado) ponte.escalaDosNumeros(p.real(PreferenciasDoPainel.ESCALA_DOS_NUMEROS, 1f))
    }

    fun aplicarCorDoModoNoturno() {
        if (!encerrado) ponte.aplicarAjustesVisuais()
    }

    fun aplicarEscalaDaTemperatura() {
        if (!encerrado) ponte.aplicarAjustesVisuais()
    }

    fun republicarFarol() {
        if (!encerrado) ponte.reavaliarFarol(true)
    }

    // ═══════════════════════════ R. br.com.deepgwt.ponte.Painel ═══════════════════════════

    override fun naPrincipal(bloco: () -> Unit) {
        tempo.naPrincipal(bloco)
    }

    /** R.3: sem WebView (projetor parado) a janela não faz nada e não chama [resposta]. */
    override fun executar(js: String, resposta: ((String?) -> Unit)?) {
        janela.executar(js, resposta)
    }

    override val avisoAtivo: Boolean get() = selo.avisoAtivo

    override val avisoDispensado: Boolean get() = selo.avisoDispensado

    override fun dispensarAvisos() {
        if (!encerrado) selo.dispensar()
    }

    override fun batimento() {
        ultimaBatida = tempo.agora()
    }

    /** K.8. */
    override fun areaDosApps(x: Int, y: Int, largura: Int, altura: Int) {
        if (encerrado) return
        seguro(Unit) { apps.areaDoTema(intArrayOf(x, y, largura, altura)) }
        esquecerTamanhos()
        ressincronizar(displaysComApps())
    }

    /** L.6. */
    override fun mascaraNativa(nome: String, visivel: Boolean) {
        if (encerrado) return
        sobreposicoes[nome] = visivel
        redesenharMascaras()
    }

    override fun configuracaoDasMascaras(json: String) {
        redesenharMascaras()
    }

    /** R.1. */
    override fun salvarModoDeExibicao(valor: String) {
        preferencias.edit().putString(PreferenciasDoPainel.MODO_DE_EXIBICAO, RotulosDoPainel.modoDeExibicao(valor)).apply()
    }

    // ═══════════════════════════ Q. Diário ═══════════════════════════

    /** Q.3: os detalhes comuns dos eventos do painel. */
    private fun diario(evento: String, detalhes: Map<String, Any?>, comuns: Boolean = true) {
        if (!registro.diarioLigado) return
        val tudo = LinkedHashMap<String, Any?>(detalhes)
        if (comuns) {
            tudo["card"] = cartao
            tudo["screen"] = when (cartao) {
                ArbitroDoCartao.CARTAO_DO_MENU -> "main_menu"
                ArbitroDoCartao.CARTAO_DO_AR -> "aircon"
                else -> "hidden"
            }
            tudo["warningActive"] = selo.avisoAtivo
            tudo["display1Active"] = appNoDisplay1
            tudo["display3Active"] = appNoDisplay3
        }
        registro.diario(evento, tudo)
    }

    private inline fun <T> seguro(padrao: T, bloco: () -> T): T = try {
        bloco()
    } catch (e: Exception) {
        registro.erro("painel: chamada de fora falhou", e)
        padrao
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
