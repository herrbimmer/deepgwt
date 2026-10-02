package br.com.opengw.painel

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import coil.imageLoader
import coil.request.Disposable
import coil.request.ImageRequest

/**
 * A camada do display 1 (`projetores-hud.md`): o papel de parede do painel, atrás do desenho de
 * fábrica, e a faixa do modo mãos-livres. Não é o HUD físico. Interna: criada e destruída por
 * [PainelDeInstrumentos]. Tudo na principal.
 */
internal class CamadaDoDisplay1(
    contexto: Context,
    display: Display,
    private val preferencias: SharedPreferences,
    private val tempo: TempoDoPainel,
    private val apps: AppsNoPainel,
    private val registro: RegistroDoPainel,
    telaPrincipalLigada: Boolean,
) {
    private val p = PreferenciasDoPainel(preferencias)
    private val apresentacao = Apresentacao(contexto, display)
    private val raiz = FrameLayout(apresentacao.context)
    private val imagem = ImageView(apresentacao.context)
    private var web: WebView? = null
    private var webMorto = false
    private val faixa = TextView(apresentacao.context)

    private var telaLigada = telaPrincipalLigada
    private var appNoDisplay1 = false
    private var visivel = false
    private var encerrada = false
    private var avisosDeApp = 0

    /** D1 (corrigido): a última escolha aplicada (ligado, tipo, valor, tema). */
    private var ultimaAplicada: List<Any?>? = null
    private var geracao = 0
    private var bitmapAtual: Bitmap? = null
    private var pedidoDaInternet: Disposable? = null

    private val ouvinte = SharedPreferences.OnSharedPreferenceChangeListener { _, chave -> preferenciaMudou(chave) }

    val idDoDisplay: Int = display.displayId

    private inner class Apresentacao(contexto: Context, display: Display) : Presentation(contexto, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            JanelaTransparente.configurar(this)
            setContentView(raiz)
        }
    }

    fun mostrar() {
        montarVistas()
        apresentacao.show()
        preferencias.registerOnSharedPreferenceChangeListener(ouvinte)
        val versao = avisosDeApp
        tempo.emFundo {
            val algum = try {
                apps.algumAppNoDisplay(RegrasDosApps.DISPLAY_DE_TRAS)
            } catch (e: Exception) {
                false
            }
            // Um aviso de app que chegou no meio vale mais que esta leitura.
            tempo.naPrincipal { if (!encerrada && versao == avisosDeApp) aplicarAppNoDisplay(algum) }
        }
        atualizarVisibilidade()
    }

    fun telaPrincipal(ligada: Boolean) {
        telaLigada = ligada
        atualizarVisibilidade()
    }

    fun appNoDisplay(algum: Boolean) {
        avisosDeApp++
        aplicarAppNoDisplay(algum)
    }

    private fun aplicarAppNoDisplay(algum: Boolean) {
        appNoDisplay1 = algum
        atualizarVisibilidade()
    }

    fun maosLivres(ativo: Boolean) {
        faixa.visibility = if (ativo) View.VISIBLE else View.GONE
    }

    /** E: para de ouvir; WebView desmontado; janela fechada. */
    fun encerrar() {
        if (encerrada) return
        encerrada = true
        preferencias.unregisterOnSharedPreferenceChangeListener(ouvinte)
        pedidoDaInternet?.dispose()
        destruirWeb()
        try {
            apresentacao.dismiss()
        } catch (e: Exception) {
            registro.erro("fechar a camada do display 1 falhou", e)
        }
        imagem.setImageDrawable(null)
        bitmapAtual = null
    }

    // ═══════════════════════════ Vistas ═══════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private fun montarVistas() {
        raiz.setBackgroundColor(Color.TRANSPARENT)
        // Nasce invisível; C decide (o estado inicial de [visivel] é falso).
        raiz.visibility = View.INVISIBLE
        imagem.scaleType = ImageView.ScaleType.CENTER_CROP
        imagem.visibility = View.GONE
        raiz.addView(imagem, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val w = WebView(apresentacao.context)
        w.setBackgroundColor(Color.TRANSPARENT)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        w.settings.databaseEnabled = true
        w.settings.useWideViewPort = true
        w.settings.loadWithOverviewMode = true
        w.settings.mediaPlaybackRequiresUserGesture = false
        w.webViewClient = object : WebViewClient() {
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                registro.info("display 1: erro de carga ${request?.url}: ${error?.description}")
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, resposta: WebResourceResponse?) {
                registro.info("display 1: HTTP ${resposta?.statusCode} em ${request?.url}")
            }

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                registro.info("display 1: processo de render morreu (crash=${detail?.didCrash()})")
                webMorto = true
                destruirWeb()
                return true
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(mensagem: ConsoleMessage?): Boolean {
                registro.info("display 1 console: ${mensagem?.message()}")
                return true
            }
        }
        w.visibility = View.GONE
        raiz.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        web = w

        faixa.text = "MODO HANDS-FREE ATIVO"
        faixa.setTextColor(Color.WHITE)
        faixa.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        faixa.letterSpacing = 0.05f
        faixa.setPadding(28, 14, 28, 14)
        faixa.background = GradientDrawable().apply {
            setColor(Color.argb(200, 20, 22, 26))
            cornerRadius = 24f
        }
        faixa.visibility = View.GONE
        raiz.addView(
            faixa,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 24
            },
        )
    }

    private fun destruirWeb() {
        val w = web ?: return
        web = null
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
            registro.erro("display 1: desmontar o WebView falhou", e)
        }
    }

    // ═══════════════════════════ C. Quando aparece ═══════════════════════════

    private fun atualizarVisibilidade() {
        if (encerrada) return
        val ligado = p.booleano(PreferenciasDoPainel.FUNDO_LIGADO, true)
        val aparece = RegrasDoDisplay1.aparece(ligado, telaLigada, appNoDisplay1)
        if (aparece == visivel) {
            if (aparece) aplicar()
            return
        }
        visivel = aparece
        if (aparece) {
            raiz.visibility = View.VISIBLE
            web?.onResume()
            aplicar()
        } else {
            raiz.visibility = View.INVISIBLE
            web?.onPause()
        }
    }

    private fun preferenciaMudou(chave: String?) {
        when (chave) {
            PreferenciasDoPainel.FUNDO_LIGADO, PreferenciasDoPainel.FUNDO_TIPO, PreferenciasDoPainel.FUNDO_VALOR,
            PreferenciasDoPainel.TEMA_ATIVO, PreferenciasDoPainel.NOME_DO_TEMA,
            -> atualizarVisibilidade()
        }
    }

    // ═══════════════════════════ B. Papel de parede ═══════════════════════════

    private fun aplicar() {
        if (encerrada) return
        val escolha = PapelDeParede.Escolha.de(p)
        val chave = listOf(escolha.ligado, escolha.tipo, escolha.valor, p.pastaAtiva())
        if (chave == ultimaAplicada) return
        ultimaAplicada = chave
        val g = ++geracao
        pedidoDaInternet?.dispose()
        pedidoDaInternet = null
        if (!escolha.ligado) {
            esconderTudo()
            return
        }
        val valor = escolha.valor
        when (escolha.tipo) {
            PapelDeParede.THEME -> {
                esconderWeb()
                raiz.setBackgroundColor(Color.TRANSPARENT)
                tempo.emDisco {
                    val arquivo = TemasInstalados.arquivoDeFundoDoTemaAtivo(valor.ifEmpty { null })
                    val bitmap = arquivo?.let { ImagensDoPainel.decodificar(apresentacao.context, ImagemDoPainel.Arquivo(it.absolutePath)) }
                    tempo.naPrincipal { if (g == geracao && !encerrada) mostrarTema(bitmap) else bitmap?.recycle() }
                }
            }
            PapelDeParede.PRESET, PapelDeParede.FILE -> {
                esconderWeb()
                if (valor.isEmpty()) {
                    mostrarPreto()
                } else {
                    val origem = if (escolha.tipo == PapelDeParede.PRESET) {
                        ImagemDoPainel.Asset("backgrounds/" + br.com.opengw.telas.PresetBackgrounds.resolve(valor))
                    } else {
                        ImagemDoPainel.Arquivo(valor)
                    }
                    tempo.emDisco {
                        val bitmap = ImagensDoPainel.decodificar(apresentacao.context, origem)
                        tempo.naPrincipal { if (g == geracao && !encerrada) mostrarBitmap(bitmap) else bitmap?.recycle() }
                    }
                }
            }
            PapelDeParede.IMAGE_URL -> {
                esconderWeb()
                mostrarPreto()
                if (valor.isNotEmpty()) carregarDaInternet(valor)
            }
            PapelDeParede.COLOR -> {
                esconderWeb()
                val cor = RegrasDoDisplay1.cor(valor)
                if (cor == null) mostrarPreto() else mostrarBitmap(ImagensDoPainel.corComVinheta(cor))
            }
            PapelDeParede.WEB_URL -> {
                trocarBitmap(null)
                imagem.visibility = View.GONE
                val w = web
                if (w == null || webMorto) return
                w.visibility = View.VISIBLE
                if (valor.isNotEmpty() && valor != w.url) w.loadUrl(valor)
            }
            else -> esconderTudo()
        }
    }

    private fun carregarDaInternet(url: String) {
        try {
            val pedido = ImageRequest.Builder(apresentacao.context)
                .data(url)
                .target(imagem)
                .error(ColorDrawable(Color.BLACK))
                .build()
            pedidoDaInternet = apresentacao.context.imageLoader.enqueue(pedido)
        } catch (e: Exception) {
            registro.erro("display 1: pedido da imagem falhou", e)
            mostrarPreto()
        }
    }

    /** THEME: com arquivo, a imagem; sem (página original, tema sem `<background>`), escondida. */
    private fun mostrarTema(bitmap: Bitmap?) {
        if (bitmap == null) {
            trocarBitmap(null)
            imagem.visibility = View.GONE
        } else {
            mostrarBitmap(bitmap)
        }
    }

    /** Uma imagem que não decodifica mostra preto (nunca a anterior). */
    private fun mostrarBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            mostrarPreto()
            return
        }
        imagem.setBackgroundColor(Color.BLACK)
        trocarBitmap(bitmap)
        imagem.visibility = View.VISIBLE
    }

    private fun mostrarPreto() {
        trocarBitmap(null)
        imagem.setBackgroundColor(Color.BLACK)
        imagem.visibility = View.VISIBLE
    }

    private fun esconderTudo() {
        trocarBitmap(null)
        imagem.visibility = View.GONE
        esconderWeb()
        raiz.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun esconderWeb() {
        web?.visibility = View.GONE
    }

    /**
     * O bitmap substituído é largado (o coletor libera): reciclar na hora pode cair no quadro que o
     * RenderThread ainda está desenhando com ele.
     */
    private fun trocarBitmap(novo: Bitmap?) {
        if (novo == null) imagem.setImageDrawable(null) else imagem.setImageBitmap(novo)
        bitmapAtual = novo
    }
}

/** As flags comuns das duas janelas do painel (parte painel B.1; parte hud A.1). */
internal object JanelaTransparente {
    fun configurar(apresentacao: Presentation) {
        val janela = apresentacao.window ?: return
        janela.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        janela.addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        janela.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}
