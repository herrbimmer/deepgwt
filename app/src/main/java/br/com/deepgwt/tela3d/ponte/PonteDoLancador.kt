package br.com.deepgwt.tela3d.ponte

import android.webkit.JavascriptInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * O que a UI da Activity faz a pedido do lançador JS (tudo chamado na thread principal). Quem
 * implementa é a Activity; os testes usam um falso.
 */
interface AcoesDaTela {
    /** Quadro preto sobre a página que some em `max(200, duracaoMs)` ms (comportamento 25). */
    fun iniciarFadeDeSplash(duracaoMs: Int)
    fun encerrarSplash()
    fun revelarFaixa()
    fun definirFaixaEscondidaPelaPagina(escondida: Boolean)
    fun capturarMiniatura(token: String, larguraPedida: Int, alturaPedida: Int)

    /** O retângulo da pílula de troca de área, em px CSS (comportamento 22). */
    fun reportarAreaDoSeletor(xCss: Float, yCss: Float, larguraCss: Float, alturaCss: Float, ativo: Boolean)
    fun definirChromeNoTopo(noTopo: Boolean)
    fun progredir(pct: Int)
    fun posicionarProgresso(modo: String?)
    fun esconderProgresso()

    /** Pede a permissão de localização, uma vez só por instância da Activity (comportamento 21). */
    fun pedirPermissaoDeLocalizacao()
}

/**
 * O objeto `AppLauncherBridge` da página (34 métodos). Cada método tem o nome e os tipos exatos que a
 * página chama; a classe tem o nome que quiser. Regras que valem para todos:
 * - nunca deixa exceção sair (ela viraria erro JS na página): falha é registrada e vira o valor neutro;
 * - tudo o que toca View ou estado de janelas é postado na thread principal, inclusive
 *   `launchAppInPopup`, `maximizeApp` e `closeApp`;
 * - as leituras (`getShellLayout`, `getInstalledApps`…) e as gravações de preferência rodam na thread da ponte.
 */
class PonteDoLancador(
    private val janelas: JanelasParaAPonte,
    private val catalogo: CatalogoParaAPonte,
    private val faixa: FaixaParaAPonte,
    private val acoes: AcoesDaTela,
    private val executor: ExecutorDaUi,
    private val preferencias: PreferenciasDoLancador,
    private val buscador: BuscadorDeJson,
    private val relogio: AmbienteDoRelogio,
    private val musica: MusicaAtiva,
    /** O estado do shell publicado pela Activity (cópia imutável, lida de qualquer thread). */
    private val estadoDoShell: () -> EstadoDoShell?,
    private val diario: DiarioDaPonte,
) {
    private val fadeJaComecou = AtomicBoolean(false)

    /**
     * Um atalho de ajuste (Alpha 10.0) chega pela grade como se fosse um app: em vez de abrir janela,
     * liga ou desliga o ajuste e avisa. Devolve true quando o "pacote" era um atalho.
     */
    private fun executouAtalho(pacote: String): Boolean {
        if (!br.com.deepgwt.ajustes.RegrasDosAtalhos.ehAtalho(pacote)) return false
        br.com.deepgwt.ajustes.AtalhosDeAjuste.alternarEAvisar(br.com.deepgwt.ajustes.RegrasDosAtalhos.idDe(pacote))
        return true
    }

    // ------------------------------------------------------------ splash e carga

    @JavascriptInterface
    fun beginSplashFade(ms: Int) {
        segura("beginSplashFade") {
            if (!fadeJaComecou.compareAndSet(false, true)) return@segura
            val aplicado = CountDownLatch(1)
            executor.postar {
                try {
                    acoes.iniciarFadeDeSplash(ms)
                } catch (e: Exception) {
                    diario.erro("beginSplashFade falhou", e)
                } finally {
                    aplicado.countDown()
                }
            }
            // A chamada espera a UI aplicar, até 400 ms.
            aplicado.await(ESPERA_DO_FADE_MS, TimeUnit.MILLISECONDS)
        }
    }

    @JavascriptInterface
    fun endSplashOverlay() = naUi("endSplashOverlay") { acoes.encerrarSplash() }

    @JavascriptInterface
    fun revealLauncher() = naUi("revealLauncher") { acoes.revelarFaixa() }

    @JavascriptInterface
    fun setBootProgress(pct: Int, texto: String?, titulo: String?) =
        naUi("setBootProgress") { acoes.progredir(MedidasDaCamadaDeCarga.limitarPct(pct)) }

    @JavascriptInterface
    fun placeBootProgress(modo: String?) = naUi("placeBootProgress") { acoes.posicionarProgresso(modo) }

    @JavascriptInterface
    fun hideBootProgress() = naUi("hideBootProgress") { acoes.esconderProgresso() }

    // ------------------------------------------------------------ abrir e fechar apps (janelas)

    @JavascriptInterface
    fun launchAppInPopup(pacote: String?) {
        val p = pacote?.takeIf { it.isNotBlank() } ?: return
        if (executouAtalho(p)) return
        naUi("launchAppInPopup") { janelas.abrirEmPopup(p) }
    }

    @JavascriptInterface
    fun launchAppInSlot(pacote: String?, lado: String?) {
        val p = pacote?.takeIf { it.isNotBlank() } ?: return
        if (executouAtalho(p)) return
        val l = Normalizadores.ladoDoSlot(lado)
        naUi("launchAppInSlot") { janelas.abrirNoLado(p, l) }
    }

    @JavascriptInterface
    fun maximizeApp(pacote: String?) {
        val p = pacote?.takeIf { it.isNotBlank() } ?: return
        if (executouAtalho(p)) return
        naUi("maximizeApp") { janelas.maximizar(p) }
    }

    @JavascriptInterface
    fun closeApp(pacote: String?) = naUi("closeApp") { janelas.fechar(pacote) }

    @JavascriptInterface
    fun dismissOverlays() = naUi("dismissOverlays") { janelas.dispensarSobreposicoes() }

    @JavascriptInterface
    fun launchProjection(tipo: String?) {
        val projecao = TipoDeProjecao.ler(tipo) ?: return
        naUi("launchProjection") { janelas.abrirProjecao(projecao.codigo) }
    }

    @JavascriptInterface
    fun swapApps() = naUi("swapApps") { janelas.trocarLados() }

    @JavascriptInterface
    fun saveAppsOnlyDefault() = naUi("saveAppsOnlyDefault") { janelas.salvarModoAppsComoPadrao() }

    // ------------------------------------------------------------ layout do shell (janelas)

    @JavascriptInterface
    fun setShellMode(modo: String?) {
        val m = Normalizadores.modoDoShell(modo)
        naUi("setShellMode") { janelas.definirModoDoShell(m) }
    }

    @JavascriptInterface
    fun setLaunchSide(lado: String?) {
        val l = Normalizadores.ladoDeAbertura(lado)
        naUi("setLaunchSide") { janelas.definirLadoDeAbertura(l) }
    }

    @JavascriptInterface
    fun setSlotUse(lado: String?, uso: String?) {
        val l = Normalizadores.ladoDoSlot(lado)
        val u = Normalizadores.usoDoSlot(uso)
        naUi("setSlotUse") { janelas.definirUsoDoLado(l, u) }
    }

    @JavascriptInterface
    fun setSplitRatio(proporcao: String?) {
        val p = Normalizadores.proporcao(proporcao) ?: return
        naUi("setSplitRatio") { janelas.definirProporcao(p) }
    }

    @JavascriptInterface
    fun setChromeOnTop(noTopo: Boolean) = naUi("setChromeOnTop") { acoes.definirChromeNoTopo(noTopo) }

    @JavascriptInterface
    fun setLauncherHidden(escondida: Boolean) =
        naUi("setLauncherHidden") { acoes.definirFaixaEscondidaPelaPagina(escondida) }

    @JavascriptInterface
    fun getShellLayout(): String = segura("getShellLayout", LayoutDoShell.respostaDeGetShellLayout(null)) {
        LayoutDoShell.respostaDeGetShellLayout(estadoDoShell())
    }

    // ------------------------------------------------------------ catálogo

    @JavascriptInterface
    fun getInstalledApps(): String = segura("getInstalledApps", "[]") { catalogo.listaDeAppsJson() }

    @JavascriptInterface
    fun getAppIcon(pacote: String?): String {
        val p = pacote?.takeIf { it.isNotBlank() } ?: return ""
        return segura("getAppIcon", "") { catalogo.iconeComoDataUrl(p) }
    }

    // ------------------------------------------------------------ preferências

    @JavascriptInterface
    fun saveShellBackground(cor: String?) = segura("saveShellBackground") {
        preferencias.gravarInteiro(PreferenciasDoLancador.CHAVE_FUNDO_DO_SHELL, Normalizadores.corCssParaArgb(cor))
    }

    @JavascriptInterface
    fun saveWidgets(json: String?) = segura("saveWidgets") {
        preferencias.gravarTexto(PreferenciasDoLancador.CHAVE_WIDGETS, json ?: "")
    }

    @JavascriptInterface
    fun loadWidgets(): String = segura("loadWidgets", "") {
        preferencias.texto(PreferenciasDoLancador.CHAVE_WIDGETS, "")
    }

    // ------------------------------------------------------------ indicadores do dock e cartões

    @JavascriptInterface
    fun updateDockIndicators(json: String?) {
        segura("updateDockIndicators") {
            // Decodifica tudo primeiro (puro, fora da principal) e só então aplica: tudo ou nada.
            val indicadores = DecodificadorDeIndicadores.decodificar(json)
            if (indicadores == null) {
                if (!json.isNullOrBlank()) diario.aviso("updateDockIndicators: JSON inválido, ignorado")
                return@segura
            }
            executor.postar {
                try {
                    indicadores.modoDoDock?.let { modo ->
                        if (preferencias.texto(PreferenciasDoLancador.CHAVE_MODO_DO_DOCK, "") != modo) {
                            preferencias.gravarTexto(PreferenciasDoLancador.CHAVE_MODO_DO_DOCK, modo)
                        }
                    }
                    faixa.aplicarIndicadores(indicadores)
                } catch (e: Exception) {
                    diario.erro("updateDockIndicators: aplicar falhou", e)
                }
            }
        }
    }

    @JavascriptInterface
    fun updateCenterFill(json: String?) {
        segura("updateCenterFill") {
            val preenchimento = DecodificadorDeIndicadores.decodificarPreenchimento(json)
            if (preenchimento == null) {
                if (!json.isNullOrBlank()) diario.aviso("updateCenterFill: JSON inválido, ignorado")
                return@segura
            }
            executor.postar {
                try {
                    faixa.aplicarPreenchimentoDoCentro(preenchimento.modo, preenchimento.visivel)
                } catch (e: Exception) {
                    diario.erro("updateCenterFill: aplicar falhou", e)
                }
            }
        }
    }

    /** A imagem do relógio gerada pela página é descartada: o relógio do cartão é nativo. Custo O(1). */
    @JavascriptInterface
    fun updateClockCardFace(revisao: String?, sequencia: Int, dataUrl: String?) {
    }

    // ------------------------------------------------------------ miniatura, seletor, ambiente

    @JavascriptInterface
    fun captureDesktopSnapshot(token: String?, w: Int, h: Int) {
        val limpo = MiniaturaDaArea.limparToken(token)
        if (limpo.isEmpty()) return
        naUi("captureDesktopSnapshot") { acoes.capturarMiniatura(limpo, w, h) }
    }

    @JavascriptInterface
    fun reportDesktopSwitcherHit(x: Float, y: Float, w: Float, h: Float, ativo: Boolean) =
        naUi("reportDesktopSwitcherHit") { acoes.reportarAreaDoSeletor(x, y, w, h, ativo) }

    @JavascriptInterface
    fun getClockEnvironment(): String = segura("getClockEnvironment", "{}") { JsonDoRelogio.montar(relogio) }

    @JavascriptInterface
    fun isMusicActive(): Boolean = segura("isMusicActive", false) { musica.ativa() }

    // ------------------------------------------------------------ rede

    @JavascriptInterface
    fun fetchJson(url: String?): String = segura("fetchJson", "") { buscador.buscar(url) }

    // ------------------------------------------------------------ apoio

    private inline fun naUi(nome: String, crossinline acao: () -> Unit) {
        segura(nome) {
            executor.postar {
                try {
                    acao()
                } catch (e: Exception) {
                    diario.erro("$nome falhou", e)
                }
            }
        }
    }

    private inline fun segura(nome: String, acao: () -> Unit) {
        try {
            acao()
        } catch (e: Exception) {
            diario.erro("$nome falhou", e)
        }
    }

    private inline fun <T> segura(nome: String, padrao: T, acao: () -> T): T = try {
        acao()
    } catch (e: Exception) {
        diario.erro("$nome falhou", e)
        padrao
    }

    companion object {
        /** Quanto `beginSplashFade` espera a UI aplicar. */
        const val ESPERA_DO_FADE_MS = 400L

        /** O nome do objeto na página. */
        const val NOME_NA_PAGINA = "AppLauncherBridge"
    }
}
