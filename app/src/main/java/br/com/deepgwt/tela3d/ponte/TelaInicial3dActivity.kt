package br.com.deepgwt.tela3d.ponte

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import br.com.deepgwt.BuildConfig
import br.com.deepgwt.managers.GuardaDoLauncher3D

/** O anel das últimas quedas do renderizador: vive no processo, então sobrevive à recriação da Activity. */
object QuedasDoRenderizador {
    val anel = AnelDeQuedas(SystemClock::elapsedRealtime)
}

/**
 * A Activity da tela inicial 3D do carro (categoria HOME): uma WebView em tela cheia com a página 3D e,
 * por cima, a UI nativa dos módulos vizinhos. Hospeda a WebView, expõe as quatro pontes JS, chama as
 * funções da página, recupera o app se o renderizador morrer e roteia os intents de entrada.
 *
 * As dependências (janelas, catálogo, faixa, cartões, mídia, viagens, lugar atual) vêm de
 * [RegistroDaTela3d.fabrica]; sem fábrica registrada a Activity abre só com a página.
 *
 * O manifesto e a tecla Início são ligados fora daqui (troca da sala suja).
 */
class TelaInicial3dActivity : Activity() {

    // ------------------------------------------------------------ estado

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var visaoWeb: WebView? = null

    @Volatile
    private var destruida = false

    private val diario: DiarioDaPonte = DiarioAndroid("DeepGwtTela3d")
    private val cache = CacheDeTelemetria()
    private val executor = ExecutorNaPrincipal(handler) { destruida }

    private lateinit var preferencias: PreferenciasDoLancador
    private lateinit var raizDaTela: FrameLayout
    private lateinit var camada: CamadaDeCarga
    private lateinit var dependencias: DependenciasDaTela3d
    private lateinit var enviador: EnviadorDeComandos
    private lateinit var retentativaDeLayout: RetentativaUnica

    private var ocupacao = ModoDeOcupacao.FLUTUANTE
    private var corDeFundo = Normalizadores.PRETO_OPACO
    private var renderizadorPerdido = false
    private var splashEncerrado = false
    private var faixaRevelada = false
    private var faixaEscondidaPelaPagina = false
    private var perguntouLocalizacao = false
    private var areaDoSeletor = AreaDoSeletor.INATIVA
    private var seletorDeArquivosPendente: ValueCallback<Array<Uri>>? = null
    private val receptores = ArrayList<BroadcastReceiver>(4)

    /** Cópia imutável do estado do shell, publicada para a thread da ponte (`getShellLayout`). */
    @Volatile
    private var estadoDoShellPublicado: EstadoDoShell? = null

    private val pagina: PaginaDaTela3d = PaginaDaTela3dImpl(
        cache = cache,
        executarJs = { script -> executarJs(script) },
        pedirAvisoDeLayout = { executor.postar { retentativaDeLayout.solicitar() } },
    )

    // ------------------------------------------------------------ ciclo de vida

    override fun onCreate(estadoSalvo: Bundle?) {
        super.onCreate(estadoSalvo)
        destruida = false
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        preferencias = PreferenciasAndroid(this)
        enviador = EnviadorAndroid(this)
        ocupacao = ModoDeOcupacao.ler(preferencias.texto(PreferenciasDoLancador.CHAVE_OCUPACAO, ""))
        aplicarImersivo()
        corDeFundo = preferencias.inteiro(PreferenciasDoLancador.CHAVE_FUNDO_DO_SHELL, Normalizadores.PRETO_OPACO)

        raizDaTela = FrameLayout(this).apply { setBackgroundColor(corDeFundo) }
        window.setBackgroundDrawable(ColorDrawable(corDeFundo))
        retentativaDeLayout = RetentativaUnica(AgendadorDoHandler(handler), INTERVALO_DA_RETENTATIVA_DE_LAYOUT_MS) { tentarAvisarLayout() }

        val visao = WebView(this)
        ConfiguradorDaWebView.configurar(visao, BuildConfig.DEBUG)
        visaoWeb = visao

        dependencias = RegistroDaTela3d.fabrica.criar(anfitriao, pagina)
        registrarPontes(visao)
        iniciarLugarAtual()
        visao.webViewClient = ClienteDeNavegacao(ServidorDeArquivos(assets, diario), ouvinteDaPagina, diario)
        visao.webChromeClient = ClienteDoChrome(ouvinteDaPagina, diario)

        montarArvoreDeViews(visao)
        dependencias.midia.anexar(
            aoAtualizar = { json ->
                pagina.enviarMidia(json)
                executor.postar { dependencias.cartoes.atualizarCartaoDeMidia(json) }
            },
            aoPosicao = { ms -> pagina.enviarPosicaoDaMidia(ms) },
        )
        registrarReceptores()
        agendarRetratosDeTelemetria()
        setContentView(raizDaTela)
        observarTamanhoDaRaiz()

        // As preferências do shell são lidas ANTES de a página poder pedir `getShellLayout`.
        dependencias.janelas.iniciar()
        estadoDoShellPublicado = dependencias.janelas.estadoDoShell()
        carregarPagina(visao, estadoSalvo)

        dependencias.janelas.garantirConfiguracoesDeJanelaLivre()
        processarIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        visaoWeb?.onResume()
        aplicarImersivo()
        dependencias.midia.iniciar()
        dependencias.midia.aoRetomar()
        iniciarLugarAtual()
        pagina.avisarLayoutDoShell()
        if (areaDoSeletor.quer) repassarAreaDoSeletor(areaDoSeletor)
        dependencias.janelas.aoRetomar()
    }

    override fun onPause() {
        dependencias.faixa.aoPausar()
        runCatching { dependencias.viagens.gravarEmBreve() }
        if (!dependencias.janelas.temSobreposicao()) visaoWeb?.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations || dependencias.janelas.lancandoSobreposicao()) return
        val decoracao = window.decorView
        if (decoracao.visibility != View.VISIBLE || decoracao.windowVisibility != View.VISIBLE) {
            repassarAreaDoSeletor(AreaDoSeletor.INATIVA)
            dependencias.janelas.aoSairDeVista()
        }
    }

    override fun onDestroy() {
        destruida = true
        runCatching { dependencias.janelas.aoDestruir() }
        runCatching { dependencias.midia.aoDestruir() }
        handler.removeCallbacksAndMessages(null)
        runCatching { dependencias.midia.parar() }
        runCatching { dependencias.faixa.aoDestruir() }
        runCatching { dependencias.lugar.parar() }
        runCatching { dependencias.viagens.gravarEmBreve() }
        runCatching { dependencias.catalogo.aplicarAreaDoSeletor(0, 0, 0, 0, false) }
        for (receptor in receptores) {
            try {
                unregisterReceiver(receptor)
            } catch (e: IllegalArgumentException) {
                // já não estava registrado
            }
        }
        receptores.clear()
        seletorDeArquivosPendente?.onReceiveValue(null)
        seletorDeArquivosPendente = null
        runCatching { camada.encerrar() }
        visaoWeb?.let { destruirWebView(it) }
        visaoWeb = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(saida: Bundle) {
        if (renderizadorPerdido) {
            saida.putBoolean(CHAVE_RENDERIZADOR_PERDIDO, true)
        } else {
            visaoWeb?.saveState(saida)
        }
        super.onSaveInstanceState(saida)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processarIntent(intent)
    }

    override fun onTrimMemory(nivel: Int) {
        super.onTrimMemory(nivel)
        val visao = visaoWeb ?: return
        if (renderizadorPerdido || nivel < NIVEL_MINIMO_DE_ALIVIO) return
        visao.post { if (!destruida) visao.evaluateJavascript(ScriptsDaTela3d.aliviarMemoria(nivel), null) }
        diario.info("memória aliviada (nível $nivel)")
    }

    override fun onWindowFocusChanged(temFoco: Boolean) {
        super.onWindowFocusChanged(temFoco)
        if (!temFoco) return
        aplicarImersivo()
        if (dependencias.janelas.temSobreposicao()) reaplicarPoliticaDeFoco()
        dependencias.janelas.aoGanharFoco()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val visao = visaoWeb
        if (visao != null && visao.canGoBack()) visao.goBack() else super.onBackPressed()
    }

    override fun dispatchTouchEvent(evento: MotionEvent): Boolean {
        if (evento.actionMasked == MotionEvent.ACTION_UP && !destruida && dependencias.janelas.temSobreposicao()) {
            val faixaSuperior = Math.round(FAIXA_SUPERIOR_RESERVADA_DP * resources.displayMetrics.density)
            dependencias.janelas.aoLevantarODedo(noTopoReservado = evento.rawY <= faixaSuperior)
        }
        return super.dispatchTouchEvent(evento)
    }

    /** Todo `startActivity` com opções avisa a guarda do launcher (abrir a tela de fábrica é escolha do motorista). */
    override fun startActivity(intent: Intent, opcoes: Bundle?) {
        GuardaDoLauncher3D.aoAbrirPorEscolha(intent)
        super.startActivity(intent, opcoes)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(codigo: Int, resultado: Int, dados: Intent?) {
        super.onActivityResult(codigo, resultado, dados)
        if (codigo != CODIGO_DO_SELETOR_DE_ARQUIVOS) return
        val pendente = seletorDeArquivosPendente ?: return
        seletorDeArquivosPendente = null
        pendente.onReceiveValue(
            if (resultado == RESULT_OK) WebChromeClient.FileChooserParams.parseResult(resultado, dados) else null,
        )
    }

    override fun onRequestPermissionsResult(codigo: Int, permissoes: Array<out String>, resultados: IntArray) {
        super.onRequestPermissionsResult(codigo, permissoes, resultados)
        if (codigo != CODIGO_DA_PERMISSAO_DE_LOCALIZACAO) return
        if (resultados.any { it == PackageManager.PERMISSION_GRANTED }) {
            dependencias.lugar.consultarJa()
        } else {
            diario.info("localização negada; o lugar atual segue sem atualizar")
        }
    }

    // ------------------------------------------------------------ montagem

    private fun registrarPontes(visao: WebView) {
        val ponteDoLancador = PonteDoLancador(
            janelas = dependencias.janelas, catalogo = dependencias.catalogo, faixa = dependencias.faixa,
            acoes = acoes, executor = executor, preferencias = preferencias,
            buscador = BuscadorDoBing(diario), relogio = AmbienteDoRelogioAndroid(this),
            musica = MusicaAtivaAndroid(this), estadoDoShell = { estadoDoShellPublicado }, diario = diario,
        )
        visao.addJavascriptInterface(ponteDoLancador, PonteDoLancador.NOME_NA_PAGINA)
        visao.addJavascriptInterface(
            PonteDaMidia(dependencias.midia, dependencias.janelas, acoes, executor, diario),
            PonteDaMidia.NOME_NA_PAGINA,
        )
        visao.addJavascriptInterface(
            PonteDaTelemetria(cache, enviador, executor, diario, BuildConfig.DEBUG),
            PonteDaTelemetria.NOME_NA_PAGINA,
        )
        visao.addJavascriptInterface(PonteDeViagens(dependencias.viagens, diario), PonteDeViagens.NOME_NA_PAGINA)
    }

    private fun montarArvoreDeViews(visao: WebView) {
        raizDaTela.addView(visao, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        camada = CamadaDeCarga(this) {
            executarJs(ScriptsDaTela3d.pularSplash())
        }
        val margens = dependencias.faixa.margens()
        camada.adicionarEm(raizDaTela, dependencias.faixa.reservaInferiorDeChromeEmPx(), margens.origemDaPaginaYEmPx)
        dependencias.vistaNativa?.let { vista ->
            (vista.parent as? ViewGroup)?.removeView(vista)
            raizDaTela.addView(vista, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        // Plano B: se a página nunca revelar a faixa, a Activity a revela sozinha.
        handler.postDelayed({
            if (!faixaRevelada) {
                diario.aviso("a página não revelou a faixa em 60 s; revelando pela Activity")
                acoes.revelarFaixa()
            }
        }, PLANO_B_DA_FAIXA_MS)
    }

    private fun observarTamanhoDaRaiz() {
        var ultimo = intArrayOf(0, 0, 0, 0)
        raizDaTela.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val atual = intArrayOf(l, t, r, b)
            if (!atual.contentEquals(ultimo)) {
                ultimo = atual
                retentativaDeLayout.solicitar()
            }
        }
    }

    private fun carregarPagina(visao: WebView, estadoSalvo: Bundle?) {
        val extraSemSplash = RoteadorDeIntents.semSplash(intent?.let { ExtrasDeIntent(it) })
        val carga = DecisaoDeCarga.decidir(
            temEstadoSalvo = estadoSalvo != null,
            renderizadorPerdido = estadoSalvo?.getBoolean(CHAVE_RENDERIZADOR_PERDIDO, false) ?: false,
            extraNosplash = extraSemSplash,
        )
        when (carga) {
            is CargaInicial.Carregar -> visao.loadUrl(UrlDaPagina.montar(carga.semSplash))
            CargaInicial.Restaurar -> {
                // Se não havia estado a restaurar, a WebView ficaria sem página: carrega do jeito normal.
                if (estadoSalvo == null || visao.restoreState(estadoSalvo) == null) {
                    visao.loadUrl(UrlDaPagina.montar(semSplash = false))
                }
            }
        }
    }

    private fun registrarReceptores() {
        val telemetria = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                val chave = intent?.getStringExtra("key") ?: return
                val valor = intent.getStringExtra("value") ?: return
                if (BuildConfig.DEBUG) diario.info("telemetria $chave=$valor")
                pagina.publicarTelemetria(chave, valor)
            }
        }
        registrar(telemetria, IntentFilter().apply {
            addAction(ACAO_EVENTO_DO_CARRO)
            addAction(ACAO_DADO_DO_CARRO)
        })

        val relogio = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                dependencias.cartoes.atualizarRelogios()
                executarJs(ScriptsDaTela3d.ambienteDoRelogio())
            }
        }
        registrar(relogio, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        })

        val tarefa = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                val pacote = intent?.getStringExtra("package") ?: return
                dependencias.janelas.aoReceberTarefaResolvida(pacote, intent.getIntExtra("taskId", -1))
            }
        }
        registrar(tarefa, IntentFilter(ACAO_TAREFA_RESOLVIDA))

        val pacotes = object : BroadcastReceiver() {
            override fun onReceive(contexto: Context?, intent: Intent?) {
                val pacote = intent?.data?.schemeSpecificPart ?: return
                dependencias.catalogo.aoPacoteRemovido(pacote)
            }
        }
        registrar(pacotes, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        })
    }

    private fun registrar(receptor: BroadcastReceiver, filtro: IntentFilter) {
        registerReceiver(receptor, filtro)
        receptores += receptor
    }

    /** O lugar atual (idempotente): ligado na criação e a cada retomada, entregando à página `app.location.place`. */
    private fun iniciarLugarAtual() {
        dependencias.lugar.iniciar { texto -> pagina.publicarTelemetria(CHAVE_DO_LUGAR_ATUAL, texto) }
    }

    /** Pede o retrato de telemetria agora e de novo em 4 s e 15 s. */
    private fun agendarRetratosDeTelemetria() {
        val pedir = Runnable {
            try {
                enviador.pedirRetratoDeTelemetria()
            } catch (e: Exception) {
                diario.erro("pedido de retrato de telemetria falhou", e)
            }
        }
        pedir.run()
        handler.postDelayed(pedir, 4_000L)
        handler.postDelayed(pedir, 15_000L)
    }

    // ------------------------------------------------------------ intents de entrada

    private fun processarIntent(intent: Intent?) {
        if (intent == null) return
        for (acao in RoteadorDeIntents.rotear(ExtrasDeIntent(intent))) {
            try {
                when (acao) {
                    is AcaoDeEntrada.TrocarOcupacao -> trocarOcupacao(acao.modo)
                    is AcaoDeEntrada.TrazerTarefa -> {
                        val levou = dependencias.janelas.levarTarefaParaFrente(acao.taskId)
                        diario.info("tarefa ${acao.taskId} trazida para a frente: $levou")
                    }
                    is AcaoDeEntrada.AbrirProjecao -> dependencias.janelas.abrirProjecao(acao.tipo.codigo)
                    is AcaoDeEntrada.AbrirApp -> dependencias.janelas.abrirEmPopup(acao.pacote)
                }
            } catch (e: Exception) {
                diario.erro("intent de entrada: $acao falhou", e)
            }
        }
    }

    private fun trocarOcupacao(modo: ModoDeOcupacao) {
        ocupacao = modo
        preferencias.gravarTexto(PreferenciasDoLancador.CHAVE_OCUPACAO, modo.valor)
        aplicarImersivo()
        dependencias.janelas.definirModoDeOcupacao(modo.valor)
        handler.postDelayed({ pagina.avisarLayoutDoShell() }, 250L)
    }

    // ------------------------------------------------------------ imersivo, foco, layout

    @Suppress("DEPRECATION")
    private fun aplicarImersivo() {
        window.decorView.systemUiVisibility = ocupacao.flagsDeImersivo
    }

    private fun reaplicarPoliticaDeFoco() {
        val janelas = dependencias.janelas
        val haSobreposicao = janelas.temSobreposicao()
        val atuais = window.attributes.flags
        val novos = PoliticaDeFoco.aplicar(atuais, haSobreposicao, janelas.chromeNoTopo())
        if (PoliticaDeFoco.mudou(atuais, novos)) {
            window.setFlags(novos, PoliticaDeFoco.MASCARA)
            if (!haSobreposicao) aplicarImersivo()
        }
        janelas.controlarVigia(haSobreposicao)
    }

    /** `false` quando a raiz ainda não tem tamanho (a [RetentativaUnica] tenta de novo em 300 ms). */
    private fun tentarAvisarLayout(): Boolean {
        val janelas = dependencias.janelas
        val estado = janelas.estadoDoShell()
        estadoDoShellPublicado = estado
        if (raizDaTela.width <= 0 || raizDaTela.height <= 0) return false

        dependencias.faixa.aoMudarLayout()
        val margens = dependencias.faixa.margens()
        camada.reposicionar(dependencias.faixa.reservaInferiorDeChromeEmPx(), margens.origemDaPaginaYEmPx)
        reaplicarPoliticaDeFoco()
        executarJs(LayoutDoShell.script(estado, margens, ocupacao, resources.displayMetrics.density))
        return true
    }

    // ------------------------------------------------------------ página

    /** Qualquer thread: `post` da WebView leva o script à thread da UI. Sem WebView ou destruída: não faz nada. */
    private fun executarJs(script: String) {
        val visao = visaoWeb ?: return
        if (destruida) return
        visao.post {
            if (!destruida && visaoWeb === visao) visao.evaluateJavascript(script, null)
        }
    }

    private fun destruirWebView(visao: WebView) {
        runCatching {
            visao.stopLoading()
            for (nome in NOMES_DAS_PONTES) visao.removeJavascriptInterface(nome)
            visao.webViewClient = WebViewClient()
            visao.webChromeClient = WebChromeClient()
            (visao.parent as? ViewGroup)?.removeView(visao)
            visao.destroy()
        }
    }

    private fun encerrarSplash() {
        if (splashEncerrado) return
        splashEncerrado = true
        camada.esconderSkip()
        // Antes disso o fundo da WebView é transparente e deixa ver a raiz.
        visaoWeb?.setBackgroundColor(corDeFundo)
    }

    private fun repassarAreaDoSeletor(area: AreaDoSeletor) {
        dependencias.catalogo.aplicarAreaDoSeletor(area.x, area.y, area.largura, area.altura, area.quer)
    }

    // ------------------------------------------------------------ o que a página nos pede (UI)

    private val ouvinteDaPagina = object : OuvinteDaPagina {
        override fun aoTerminarDeCarregar(url: String?) {
            diario.info("página carregada: $url")
            // O pedido de faixa escondida morre com a página que o fez.
            if (faixaEscondidaPelaPagina) {
                faixaEscondidaPelaPagina = false
                dependencias.faixa.definirEscondidaPelaPagina(false)
            }
            pagina.avisarLayoutDoShell()
            // Plano B da mídia: 20 s depois de cada carga, caso a página nunca chame `onViewerReady`.
            handler.removeCallbacks(planoBDaMidia)
            handler.postDelayed(planoBDaMidia, PLANO_B_DA_MIDIA_MS)
            visaoWeb?.evaluateJavascript(ScriptsDaTela3d.diagnosticoDaPagina()) { diario.info("Page state $it") }
        }

        override fun aoPerderORenderizador(visao: WebView, travou: Boolean) {
            val decisao = QuedasDoRenderizador.anel.registrar()
            diario.erro("renderizador perdido (travou=$travou, quedas em 5 min=${decisao.quedasRecentes})")
            if (visao !== visaoWeb || renderizadorPerdido) return
            renderizadorPerdido = true
            visaoWeb = null
            destruirWebView(visao)
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) recreate()
            }, decisao.esperaMs)
        }

        override fun aoPedirSeletorDeArquivos(
            retorno: ValueCallback<Array<Uri>>,
            parametros: WebChromeClient.FileChooserParams,
        ): Boolean {
            seletorDeArquivosPendente?.onReceiveValue(null)
            seletorDeArquivosPendente = retorno
            return try {
                startActivityForResult(parametros.createIntent(), CODIGO_DO_SELETOR_DE_ARQUIVOS)
                true
            } catch (e: Exception) {
                seletorDeArquivosPendente = null
                diario.erro("seletor de arquivos não abriu", e)
                false
            }
        }
    }

    private val planoBDaMidia = Runnable {
        try {
            dependencias.midia.iniciar()
            dependencias.midia.empurrarAgora()
        } catch (e: Exception) {
            diario.erro("plano B da mídia falhou", e)
        }
    }

    private val acoes = object : AcoesDaTela {
        override fun iniciarFadeDeSplash(duracaoMs: Int) {
            camada.iniciarFade(duracaoMs) { encerrarSplash() }
        }

        override fun encerrarSplash() = this@TelaInicial3dActivity.encerrarSplash()

        override fun revelarFaixa() {
            faixaRevelada = true
            dependencias.faixa.revelar()
            encerrarSplash()
            camada.trazerParaFrente()
        }

        override fun definirFaixaEscondidaPelaPagina(escondida: Boolean) {
            if (escondida == faixaEscondidaPelaPagina) return
            faixaEscondidaPelaPagina = escondida
            dependencias.faixa.definirEscondidaPelaPagina(escondida)
        }

        override fun capturarMiniatura(token: String, larguraPedida: Int, alturaPedida: Int) {
            if (visaoWeb == null) return
            CapturaDeMiniatura(window, handler, { executarJs(it) }, diario)
                .capturar(raizDaTela, token, larguraPedida, alturaPedida)
        }

        override fun reportarAreaDoSeletor(xCss: Float, yCss: Float, larguraCss: Float, alturaCss: Float, ativo: Boolean) {
            val origem = anfitriao.origemDaWebViewNaTela()
            areaDoSeletor = AreaDoSeletor.converter(
                origem.x, origem.y, xCss, yCss, larguraCss, alturaCss, ativo, anfitriao.escalaDaWebView(),
            )
            repassarAreaDoSeletor(areaDoSeletor)
        }

        override fun definirChromeNoTopo(noTopo: Boolean) {
            dependencias.janelas.definirChromeNoTopo(noTopo)
            reaplicarPoliticaDeFoco()
        }

        override fun progredir(pct: Int) {
            camada.reposicionar(dependencias.faixa.reservaInferiorDeChromeEmPx(), dependencias.faixa.margens().origemDaPaginaYEmPx)
            camada.progredir(pct)
        }

        override fun posicionarProgresso(modo: String?) = camada.posicionar(modo)

        override fun esconderProgresso() = camada.esconder()

        override fun pedirPermissaoDeLocalizacao() {
            if (perguntouLocalizacao) return
            perguntouLocalizacao = true
            val concedida = LOCALIZACAO.any { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            if (concedida) return
            try {
                requestPermissions(LOCALIZACAO, CODIGO_DA_PERMISSAO_DE_LOCALIZACAO)
            } catch (e: Exception) {
                diario.erro("pedido de localização falhou", e)
            }
        }
    }

    // ------------------------------------------------------------ o que os vizinhos usam da Activity

    private val anfitriao = object : AnfitriaoDaTela3d {
        override val contexto: Activity get() = this@TelaInicial3dActivity
        override val handlerPrincipal: Handler get() = handler
        override val raiz: FrameLayout get() = raizDaTela

        override fun origemDaWebViewNaTela(): Point {
            val posicao = IntArray(2)
            visaoWeb?.getLocationOnScreen(posicao)
            return Point(posicao[0], posicao[1])
        }

        override fun escalaDaWebView(): Float {
            val densidade = resources.displayMetrics.density
            return if (densidade > 0f) densidade else 1f
        }

        override fun despacharToqueAoWebView(evento: MotionEvent): Boolean {
            val visao = visaoWeb ?: return false
            val origem = origemDaWebViewNaTela()
            val copia = MotionEvent.obtain(evento)
            return try {
                copia.setLocation(evento.rawX - origem.x, evento.rawY - origem.y)
                visao.dispatchTouchEvent(copia)
            } finally {
                copia.recycle()
            }
        }

        override fun modoDeOcupacao(): ModoDeOcupacao = ocupacao
        override fun reaplicarImersivo() = aplicarImersivo()
        override fun reaplicarPoliticaDeFoco() = this@TelaInicial3dActivity.reaplicarPoliticaDeFoco()
    }

    private companion object {
        const val CHAVE_RENDERIZADOR_PERDIDO = "deepgwt.tela3d.renderizadorPerdido"
        const val CHAVE_DO_LUGAR_ATUAL = "app.location.place"
        const val CODIGO_DO_SELETOR_DE_ARQUIVOS = 1001
        const val CODIGO_DA_PERMISSAO_DE_LOCALIZACAO = 7102
        const val INTERVALO_DA_RETENTATIVA_DE_LAYOUT_MS = 300L
        const val PLANO_B_DA_FAIXA_MS = 60_000L
        const val PLANO_B_DA_MIDIA_MS = 20_000L
        const val FAIXA_SUPERIOR_RESERVADA_DP = 28f

        /** `TRIM_MEMORY_RUNNING_LOW`: níveis menores são ignorados. */
        const val NIVEL_MINIMO_DE_ALIVIO = 10
        const val ACAO_EVENTO_DO_CARRO = "com.haval.vehicle.EVENT_CHANGED"
        const val ACAO_DADO_DO_CARRO = "br.com.deepgwt.CAR_DATA_UPDATE"
        const val ACAO_TAREFA_RESOLVIDA = "br.com.deepgwt.ACTION_TASK_RESOLVED"

        val LOCALIZACAO = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val NOMES_DAS_PONTES = listOf(
            PonteDoLancador.NOME_NA_PAGINA, PonteDaMidia.NOME_NA_PAGINA,
            PonteDaTelemetria.NOME_NA_PAGINA, PonteDeViagens.NOME_NA_PAGINA,
        )
    }
}
