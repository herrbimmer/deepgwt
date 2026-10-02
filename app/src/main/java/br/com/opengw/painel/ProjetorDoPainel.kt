package br.com.opengw.painel

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.opengw.ponte.ContratoV1
import br.com.opengw.ponte.PonteDosTemas

/**
 * O projetor do display do painel (`projetores-painel.md`, B): a `Presentation` transparente com as
 * máscaras nativas, o WebView do tema e o aviso do mãos-livres, mais as camadas de [CamadasDoPainel].
 * A lógica está em [NucleoDoPainel]; aqui só as vistas. Tudo na principal.
 */
internal class ProjetorDoPainel(
    contexto: Context,
    display: Display,
    private val ligacoes: LigacoesDoPainel,
    preferencias: SharedPreferences,
    private val tempo: TempoDoPainel,
    private val registro: RegistroDoPainel,
    telaPrincipalLigada: Boolean,
    depuravel: Boolean,
    primeiraCargaDoProcesso: () -> Boolean,
) : JanelaDoPainel {

    val idDoDisplay: Int = display.displayId

    private val apresentacao = Apresentacao(contexto, display)
    private val raiz = FrameLayout(apresentacao.context)
    private val mascaras = FrameLayout(apresentacao.context)
    private var webView: WebView? = null
    private var objetoDaPonte: Any? = null
    private var aviso: View? = null
    private val compositor = CompositorDeMascaras(apresentacao.context, registro)
    private var fechado = false
    private var proximoQuadro = 1L

    val nucleo = NucleoDoPainel(
        ligacoes = ligacoes,
        janela = this,
        tempo = tempo,
        preferencias = preferencias,
        carro = CarroDoApp,
        paginas = PaginasDoApp(apresentacao.context, depuravel),
        temas = TemasDoApp,
        registro = registro,
        fontes = FontesDoPainel(preferencias, ligacoes.fontesDeFora),
        criarPonte = { painel, fontes -> PonteDosTemas(painel, fontes, ligacoes.projecao, preferencias) },
        telaPrincipalLigada = telaPrincipalLigada,
        depuravel = depuravel,
        primeiraCargaDoProcesso = primeiraCargaDoProcesso,
    )

    private inner class Apresentacao(contexto: Context, display: Display) : Presentation(contexto, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            JanelaTransparente.configurar(this)
            setContentView(raiz)
        }
    }

    /** A.5: mostra a janela e começa (B.5). */
    fun mostrar() {
        raiz.setBackgroundColor(Color.TRANSPARENT)
        // Nasce invisível ("Desligado": a janela existe mas não aparece); o passe de visibilidade decide.
        raiz.visibility = View.INVISIBLE
        apresentacao.show()
        nucleo.iniciar()
    }

    /** P (a ordem está no núcleo). */
    fun encerrar() {
        nucleo.encerrar()
    }

    // ═══════════════════════════ JanelaDoPainel ═══════════════════════════

    override fun criarConteudo(objetoJs: Any) {
        objetoDaPonte = objetoJs
        mascaras.visibility = View.INVISIBLE
        raiz.addView(mascaras, FrameLayout.LayoutParams(ImagensDoPainel.LARGURA, ImagensDoPainel.ALTURA))
        raiz.addView(novoWebView(objetoJs), 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val cartao = avisoDoMaosLivres()
        cartao.visibility = View.INVISIBLE
        raiz.addView(
            cartao,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER },
        )
        aviso = cartao
    }

    override fun montarCamadas(cartao: Int, avisoNoCirculo: Boolean) {
        ligacoes.camadas.montar(raiz, { webView }, { bloco -> tempo.naPrincipal(bloco) }, cartao, avisoNoCirculo)
    }

    override fun carregar(base: String, html: String) {
        webView?.loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)
    }

    override fun executar(js: String, resposta: ((String?) -> Unit)?): Boolean {
        val w = webView ?: return false
        if (resposta == null) w.evaluateJavascript(js, null) else w.evaluateJavascript(js) { resposta(it) }
        return true
    }

    override fun alfaDoWebView(alfa: Float) {
        webView?.alpha = alfa
    }

    override fun raizVisivel(visivel: Boolean) {
        raiz.visibility = if (visivel) View.VISIBLE else View.INVISIBLE
    }

    override val raizEstaVisivel: Boolean get() = raiz.visibility == View.VISIBLE

    override fun tornarWebViewVisivel() {
        webView?.visibility = View.VISIBLE
    }

    override val webViewEstaVisivel: Boolean get() = webView?.visibility == View.VISIBLE

    override fun quandoOQuadroEstiverPronto(pronto: () -> Unit) {
        val w = webView ?: return
        w.evaluateJavascript("") {
            if (webView !== w || fechado) return@evaluateJavascript
            w.postVisualStateCallback(proximoQuadro++, object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (webView === w && !fechado) pronto()
                }
            })
        }
    }

    override fun comporMascaras(plano: PlanoDasMascaras): Any? = compositor.compor(plano)

    override fun aplicarMascaras(composto: Any?) {
        if (fechado) return
        mascaras.removeAllViews()
        val pecas = (composto as? MascarasCompostas)?.pecas.orEmpty()
        for (peca in pecas) {
            val vista = ImageView(apresentacao.context)
            vista.scaleType = ImageView.ScaleType.FIT_XY
            vista.setImageBitmap(peca.bitmap)
            vista.alpha = peca.opacidade
            mascaras.addView(
                vista,
                FrameLayout.LayoutParams(peca.largura, peca.altura).apply {
                    leftMargin = peca.x
                    topMargin = peca.y
                },
            )
        }
        mascaras.visibility = if (pecas.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    override fun esquecerMascaraGlobal() {
        tempo.emDisco { compositor.esquecer() }
    }

    override fun avisoDoMaosLivres(visivel: Boolean) {
        aviso?.visibility = if (visivel) View.VISIBLE else View.INVISIBLE
    }

    override fun recriarWebView() {
        val velho = webView ?: return
        val objeto = objetoDaPonte ?: return
        val indice = raiz.indexOfChild(velho)
        desmontarWebView(velho)
        raiz.addView(
            novoWebView(objeto),
            if (indice >= 0) indice else 1,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    override fun desmontarCamadas() {
        ligacoes.camadas.desmontar(raiz)
    }

    override fun destruir() {
        if (fechado) return
        fechado = true
        webView?.let { desmontarWebView(it) }
        mascaras.removeAllViews()
        tempo.emDisco { compositor.esquecer() }
        try {
            apresentacao.dismiss()
        } catch (e: Exception) {
            registro.erro("fechar o projetor do painel falhou", e)
        }
    }

    // ═══════════════════════════ WebView ═══════════════════════════

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun novoWebView(objetoJs: Any): WebView {
        val w = WebView(apresentacao.context)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.allowContentAccess = true
        w.addJavascriptInterface(objetoJs, ContratoV1.NOME_DO_OBJETO)
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (view !== webView) return
                registro.info("página começou: ${url?.take(120)}")
                nucleo.paginaComecou()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (view !== webView) return
                nucleo.paginaTerminou()
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                // Todos os WebViews do app dividem um processo de render: sem `true` o app inteiro cai.
                if (view === webView) nucleo.renderMorreu(detail?.didCrash() ?: false)
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(mensagem: ConsoleMessage?): Boolean {
                if (mensagem != null) {
                    nucleo.console(mensagem.messageLevel()?.name ?: "LOG", mensagem.lineNumber(), mensagem.sourceId(), mensagem.message())
                }
                return true
            }
        }
        webView = w
        return w
    }

    /** P: tirar da raiz, parar, limpar, `about:blank`, pausar, destruir. */
    private fun desmontarWebView(w: WebView) {
        if (webView === w) webView = null
        try {
            raiz.removeView(w)
            w.stopLoading()
            w.clearHistory()
            w.clearCache(true)
            w.loadUrl("about:blank")
            w.onPause()
            w.removeAllViews()
            w.destroy()
        } catch (e: Exception) {
            registro.erro("desmontar o WebView do painel falhou", e)
        }
    }

    // ═══════════════════════════ N.3: aviso do mãos-livres ═══════════════════════════

    private fun dp(valor: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, apresentacao.context.resources.displayMetrics).toInt()

    private fun avisoDoMaosLivres(): View {
        val ctx = apresentacao.context
        val cartao = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28f), dp(20f), dp(28f), dp(20f))
            background = GradientDrawable().apply {
                setColor(Color.argb(215, 18, 20, 24))
                cornerRadius = dp(18f).toFloat()
            }
        }
        val celula = dp(44f)
        fun seta(texto: String) = TextView(ctx).apply {
            text = texto
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
        }
        fun vazio() = View(ctx)
        fun linha(vararg vistas: View) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            for (v in vistas) addView(v, LinearLayout.LayoutParams(celula, celula))
        }
        val ok = TextView(ctx).apply {
            text = "OK"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
        }
        cartao.addView(linha(vazio(), seta("▲"), vazio()))
        cartao.addView(linha(seta("◀"), ok, seta("▶")))
        cartao.addView(linha(vazio(), seta("▼"), vazio()))
        cartao.addView(
            TextView(ctx).apply {
                text = "MODO HANDS-FREE\nSetas do volante navegam, OK seleciona"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, dp(12f), 0, 0)
            },
        )
        return cartao
    }
}
