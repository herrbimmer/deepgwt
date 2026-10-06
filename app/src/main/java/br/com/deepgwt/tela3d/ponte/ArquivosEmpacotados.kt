package br.com.deepgwt.tela3d.ponte

/** Tipo de conteúdo de um arquivo servido do APK. */
data class TipoDeConteudo(val mime: String, val charset: String?)

/**
 * O "servidor" interno de arquivos: a página 3D é aberta em `https://appassets.androidplatform.net` e
 * cada requisição para `/assets/…` é atendida de dentro do APK, sem rede. Esta parte é pura: decide
 * quais requisições são nossas, de qual arquivo e com que tipo. A leitura do arquivo é do
 * [ServidorDeArquivos] (Android).
 */
object ArquivosEmpacotados {

    /** A origem da página. NÃO muda: todo o localStorage dela vive nela. */
    const val HOST = "appassets.androidplatform.net"
    const val PREFIXO_DO_CAMINHO = "/assets/"

    private val HTML = TipoDeConteudo("text/html", "UTF-8")
    private val JS = TipoDeConteudo("application/javascript", "UTF-8")
    private val CSS = TipoDeConteudo("text/css", "UTF-8")
    private val JSON = TipoDeConteudo("application/json", "UTF-8")
    private val SVG = TipoDeConteudo("image/svg+xml", "UTF-8")
    private val BINARIO = TipoDeConteudo("application/octet-stream", null)

    /**
     * Do host e do caminho da requisição ao caminho dentro da pasta `assets` do APK
     * (`/assets/www/index.html` -> `www/index.html`; a query nem chega aqui). Devolve null quando a
     * requisição não é nossa (outro host, outro prefixo, caminho vazio ou que sobe de pasta).
     */
    fun caminhoNoApk(host: String?, caminho: String?): String? {
        if (host == null || caminho == null) return null
        if (!host.equals(HOST, ignoreCase = true)) return null
        if (!caminho.startsWith(PREFIXO_DO_CAMINHO)) return null
        val restante = caminho.substring(PREFIXO_DO_CAMINHO.length)
        if (restante.isEmpty() || restante.endsWith("/")) return null
        if (restante.split('/').any { it == ".." || it == "." || it.isEmpty() }) return null
        if (restante.contains('\\') || restante.contains('\u0000')) return null
        return restante
    }

    /** O tipo pela extensão (sem diferenciar caixa); o que não se conhece é binário genérico. */
    fun tipoDe(caminho: String): TipoDeConteudo {
        val ponto = caminho.lastIndexOf('.')
        val extensao = if (ponto >= 0) caminho.substring(ponto + 1).lowercase() else ""
        return when (extensao) {
            "html" -> HTML
            "js", "mjs" -> JS
            "css" -> CSS
            "json" -> JSON
            "svg" -> SVG
            "wasm" -> TipoDeConteudo("application/wasm", null)
            "woff2" -> TipoDeConteudo("font/woff2", null)
            "ttf" -> TipoDeConteudo("font/ttf", null)
            "png" -> TipoDeConteudo("image/png", null)
            "jpg", "jpeg" -> TipoDeConteudo("image/jpeg", null)
            "webp" -> TipoDeConteudo("image/webp", null)
            "glb" -> TipoDeConteudo("model/gltf-binary", null)
            "mp4" -> TipoDeConteudo("video/mp4", null)
            else -> BINARIO
        }
    }

    /** `.mp4` é lido por inteiro e anuncia tamanho e "sem faixas de bytes". */
    fun precisaDeTamanho(caminho: String): Boolean = caminho.lowercase().endsWith(".mp4")

    /** Cabeçalhos de toda resposta; `tamanho` só para os arquivos que anunciam o comprimento. */
    fun cabecalhos(caminho: String, tamanho: Long?): Map<String, String> {
        val mapa = linkedMapOf("Access-Control-Allow-Origin" to "*")
        if (precisaDeTamanho(caminho) && tamanho != null) {
            mapa["Content-Length"] = tamanho.toString()
            mapa["Accept-Ranges"] = "none"
        }
        return mapa
    }
}
