package br.com.deepgwt.tela3d.ponte

import android.content.res.AssetManager
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.IOException

/** Quem a WebView avisa de dentro dos clientes dela. Tudo na thread principal. */
interface OuvinteDaPagina {
    fun aoTerminarDeCarregar(url: String?)

    /** O processo de renderização morreu: a Activity decide a recuperação (comportamento 18). */
    fun aoPerderORenderizador(visao: WebView, travou: Boolean)

    /** `<input type=file>` da página. Devolve false se o seletor não pôde abrir. */
    fun aoPedirSeletorDeArquivos(
        retorno: ValueCallback<Array<Uri>>,
        parametros: WebChromeClient.FileChooserParams,
    ): Boolean
}

/** O servidor de arquivos interno: atende `https://appassets.androidplatform.net/assets/…` do APK. */
class ServidorDeArquivos(
    private val assets: AssetManager,
    private val diario: DiarioDaPonte,
) {
    /** Devolve a resposta, ou null para a requisição seguir o caminho normal da WebView. */
    fun atender(requisicao: WebResourceRequest): WebResourceResponse? {
        val url = requisicao.url ?: return null
        val caminho = ArquivosEmpacotados.caminhoNoApk(url.host, url.path) ?: return null
        return try {
            val tipo = ArquivosEmpacotados.tipoDe(caminho)
            val fluxo: Pair<java.io.InputStream, Long?> = if (ArquivosEmpacotados.precisaDeTamanho(caminho)) {
                val bytes = assets.open(caminho).use { it.readBytes() }
                Pair(ByteArrayInputStream(bytes), bytes.size.toLong())
            } else {
                Pair(assets.open(caminho), null)
            }
            val cabecalhos = ArquivosEmpacotados.cabecalhos(caminho, fluxo.second)
            WebResourceResponse(tipo.mime, tipo.charset, 200, "OK", cabecalhos, fluxo.first)
        } catch (e: IOException) {
            diario.erro("asset não encontrado: $caminho")
            null
        }
    }
}

/** Carga, interceptação de arquivos, queda do renderizador e bloqueio de navegação para fora. */
class ClienteDeNavegacao(
    private val servidor: ServidorDeArquivos,
    private val ouvinte: OuvinteDaPagina,
    private val diario: DiarioDaPonte,
) : WebViewClient() {

    override fun shouldInterceptRequest(visao: WebView?, requisicao: WebResourceRequest?): WebResourceResponse? =
        if (requisicao == null) null else servidor.atender(requisicao)

    /**
     * Bloqueia a navegação do quadro principal para fora de `appassets.androidplatform.net`: outra
     * origem carregaria uma página estranha com as quatro pontes JS expostas.
     */
    override fun shouldOverrideUrlLoading(visao: WebView?, requisicao: WebResourceRequest?): Boolean {
        if (requisicao == null || !requisicao.isForMainFrame) return false
        val url = requisicao.url
        val permitida = UrlDaPagina.navegacaoPermitida(url?.scheme, url?.host, url?.port ?: -1)
        if (!permitida) diario.aviso("navegação bloqueada para fora da origem de assets: ${url?.host}")
        return !permitida
    }

    override fun onPageFinished(visao: WebView?, url: String?) {
        ouvinte.aoTerminarDeCarregar(url)
    }

    /** `true` sempre: sem isso o Android mata o processo inteiro (que hospeda também o Shizuku e o painel). */
    override fun onRenderProcessGone(visao: WebView?, detalhe: RenderProcessGoneDetail?): Boolean {
        if (visao != null) ouvinte.aoPerderORenderizador(visao, detalhe?.didCrash() ?: false)
        return true
    }
}

/** Console da página no log e seletor de arquivos. Nada mais: `alert`/`confirm`/`prompt` ficam sem tratamento. */
class ClienteDoChrome(
    private val ouvinte: OuvinteDaPagina,
    private val diario: DiarioDaPonte,
) : WebChromeClient() {

    override fun onConsoleMessage(mensagem: ConsoleMessage?): Boolean {
        if (mensagem != null) {
            diario.erro("JS ${mensagem.messageLevel()} ${mensagem.sourceId()}:${mensagem.lineNumber()} ${mensagem.message()}")
        }
        return true
    }

    override fun onShowFileChooser(
        visao: WebView?,
        retorno: ValueCallback<Array<Uri>>?,
        parametros: FileChooserParams?,
    ): Boolean {
        if (retorno == null || parametros == null) return false
        return ouvinte.aoPedirSeletorDeArquivos(retorno, parametros)
    }
}

/** A configuração da WebView da tela 3D (comportamento 4). */
object ConfiguradorDaWebView {

    /**
     * @param depuracao liga a depuração remota do WebView, que é uma chave do processo inteiro: só no
     * build debug, para quem tem adb não conseguir injetar JS com acesso às pontes em release.
     */
    fun configurar(visao: WebView, depuracao: Boolean) {
        visao.setBackgroundColor(Color.TRANSPARENT)
        visao.setLayerType(View.LAYER_TYPE_NONE, null)
        visao.overScrollMode = View.OVER_SCROLL_NEVER
        visao.isVerticalScrollBarEnabled = false
        visao.isHorizontalScrollBarEnabled = false

        val ajustes = visao.settings
        ajustes.javaScriptEnabled = true
        ajustes.domStorageEnabled = true
        ajustes.allowFileAccess = true
        ajustes.allowContentAccess = true
        @Suppress("DEPRECATION")
        ajustes.allowUniversalAccessFromFileURLs = false
        ajustes.mediaPlaybackRequiresUserGesture = false
        ajustes.loadsImagesAutomatically = true
        ajustes.blockNetworkImage = false
        ajustes.blockNetworkLoads = false
        // A página é https e fala http com 127.0.0.1:8766 (o servidor do próprio app).
        ajustes.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        ajustes.cacheMode = WebSettings.LOAD_DEFAULT
        ajustes.setSupportZoom(false)
        ajustes.builtInZoomControls = false
        ajustes.displayZoomControls = false

        WebView.setWebContentsDebuggingEnabled(depuracao)
    }
}
