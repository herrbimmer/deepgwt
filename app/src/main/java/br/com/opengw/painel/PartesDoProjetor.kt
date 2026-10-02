package br.com.opengw.painel

import br.com.opengw.ponte.Cancelavel

/**
 * O que o núcleo do projetor pede à janela Android (WebView, máscaras, aviso do mãos-livres).
 * Tudo na principal, exceto [comporMascaras] (thread de disco).
 */
interface JanelaDoPainel {
    /** B.5: cria o contêiner das máscaras e o WebView com [objetoJs] já registrado como `Android`. */
    fun criarConteudo(objetoJs: Any)

    /** B.5: `camadas.montar(raiz, { webView }, naPrincipal, cartao, avisoNoCirculo)`. */
    fun montarCamadas(cartao: Int, avisoNoCirculo: Boolean)

    /** C.4: `loadDataWithBaseURL(base, html, "text/html", "UTF-8", null)` (nunca `reload()`). */
    fun carregar(base: String, html: String)

    /** `evaluateJavascript`; false (sem chamar [resposta]) se não há WebView. */
    fun executar(js: String, resposta: ((String?) -> Unit)?): Boolean

    fun alfaDoWebView(alfa: Float)

    fun raizVisivel(visivel: Boolean)

    val raizEstaVisivel: Boolean

    fun tornarWebViewVisivel()

    val webViewEstaVisivel: Boolean

    /** C.6: um script vazio e, quando ele responder, `postVisualStateCallback`; [pronto] no quadro pronto. */
    fun quandoOQuadroEstiverPronto(pronto: () -> Unit)

    /** L.9: compõe os bitmaps do plano (thread de disco). Null = nada a mostrar. */
    fun comporMascaras(plano: PlanoDasMascaras): Any?

    /** Mostra o que [comporMascaras] montou (null = contêiner invisível). */
    fun aplicarMascaras(composto: Any?)

    /** Invalida a máscara global guardada (troca de tema, papel de parede mudou). */
    fun esquecerMascaraGlobal()

    /** N.3. */
    fun avisoDoMaosLivres(visivel: Boolean)

    /** C.9: o WebView morto sai e entra um novo (com o mesmo objeto da ponte), no mesmo lugar. */
    fun recriarWebView()

    /** P: `camadas.desmontar(raiz)` — antes de tudo. */
    fun desmontarCamadas()

    /** P: WebView desmontado, bitmaps largados, janela fechada. */
    fun destruir()
}

/** O carro visto pelo painel: a fachada `Veiculo`. */
interface CarroDoPainel {
    /** Último valor conhecido; null se não houver. */
    fun ler(chave: String): String?

    /** O ouvinte é chamado em thread do motor e ANTES de o cache ter o valor novo. */
    fun observar(ouvinte: (String, String?) -> Unit): Cancelavel
}

/** Uma página que o projetor carrega (C.1): um tema da pasta ou a página do painel original. */
data class PaginaDoPainel(
    /** O tema em uso; null = página do painel original. */
    val tema: TemaInstalado?,
    val base: String,
    val minBridgeVersion: String?,
    /** Caminho do arquivo (tema) ou do asset (página original). */
    val origem: String,
) {
    /** A pasta do tema em uso ("" = página original). */
    val pasta: String get() = tema?.pasta.orEmpty()
}

/** C.1–C.3: qual página e o HTML dela. */
interface PaginasDoPainel {
    /** C.1 (principal; só lê `theme.xml` e confere arquivos). */
    fun escolher(pastaAtiva: String): PaginaDoPainel

    /**
     * C.2/C.3 (thread de disco): o HTML. Pode trocar pela de depuração (mesma base) ou, se o arquivo
     * do tema não puder ser lido, pela página original. Devolve a página de fato e o HTML.
     */
    fun ler(pagina: PaginaDoPainel): Pair<PaginaDoPainel, String>
}
