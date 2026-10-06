package br.com.deepgwt.tela3d.midia

import android.graphics.Bitmap
import android.os.Handler
import android.os.SystemClock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * De onde vem a capa de uma faixa, já resolvida pela fonte: no máximo uma entre bitmap, URL remota
 * e consulta de busca vale na publicação (E1.6 e E1.7), na ordem bitmap > URL > consulta.
 */
internal class OrigemDaCapa(
    val bitmap: Bitmap?,
    val urlRemota: String,
    val consulta: String,
    /** Estável para a mesma imagem e diferente quando imagem, URI, URL, consulta ou pacote mudam. */
    val chave: String,
) {
    val temCapa: Boolean get() = bitmap != null || urlRemota.isNotEmpty()

    companion object {
        val NENHUMA = OrigemDaCapa(null, "", "", "")
    }
}

/** Resultado de [GerenteDeCapas.paraPublicar]: o que vai em `artDataUrl` e a chave para a assinatura. */
internal class CapaParaPublicar(val chave: String, val artDataUrl: String)

/**
 * Cuida do que vai em `artDataUrl` (caso E4): cache de capas codificadas, codificação JPEG, download
 * de URLs remotas e busca no YouTube, tudo fora da thread principal. A API é toda da thread
 * principal; os resultados voltam postados nela.
 */
internal class GerenteDeCapas(
    private val principal: Handler,
    /** Pede a republicação do estado atual (uma capa ficou pronta). */
    private val aoPrecisarRepublicar: () -> Unit,
    private val agora: () -> Long = SystemClock::elapsedRealtime,
    private val baixarBytes: (String) -> ByteArray? = { url ->
        ClienteHttp.baixarBytes(url, CONECTAR_DO_DOWNLOAD_MS, LER_DO_DOWNLOAD_MS)
    },
    private val buscarVideo: (String) -> String? = ::buscarNoYoutube,
) {
    private val cache = CacheFifo<String, String>(MAXIMO_NO_CACHE)
    private val consultaParaUrl = CacheQueEsvazia<String, String>(MAXIMO_DE_CONSULTAS)
    private val recuoDoDownload = RecuoPorChave(RECUO_MS, agora)
    private val recuoDaBusca = RecuoPorChave(RECUO_MS, agora)
    private val recuoDaCodificacao = RecuoPorChave(RECUO_MS, agora)
    private val codificando = HashSet<String>()

    @Volatile
    private var urlEmVoo: String? = null
    private var consultaEmVoo: String? = null

    private var codificacao: ExecutorService? = null
    private var rede: ExecutorService? = null
    private var busca: ExecutorService? = null
    private var aberto = false

    private var chaveAtual = ""
    private var textoAtual = ""

    /** Cria as threads de fundo. Chamar ao anexar. */
    fun abrir() {
        if (aberto) return
        aberto = true
        codificacao = executor("deepgwt-capa-codificacao")
        rede = executor("deepgwt-capa-download")
        busca = executor("deepgwt-capa-busca")
    }

    /** Encerra todas as threads e esquece o que estava em voo. Chamar ao parar. */
    fun fechar() {
        aberto = false
        codificacao?.shutdownNow()
        rede?.shutdownNow()
        busca?.shutdownNow()
        codificacao = null
        rede = null
        busca = null
        urlEmVoo = null
        consultaEmVoo = null
        codificando.clear()
        esquecerAtual()
    }

    /** Executa [trabalho] numa thread de fundo de decodificação/codificação; falso se já está fechado. */
    fun emFundo(trabalho: Runnable): Boolean = enviar(codificacao) { trabalho.run() }

    /** Zera a memória da capa atual (não há vencedor). */
    fun esquecerAtual() {
        chaveAtual = ""
        textoAtual = ""
    }

    /** E7: a capa atual está vazia ou é uma URL http ainda não baixada. */
    fun precisaDeCapa(): Boolean = textoAtual.isEmpty() || textoAtual.startsWith("http", ignoreCase = true)

    /** E4: o texto de `artDataUrl` da faixa de [fonte] com a [origem] de capa dada. */
    fun paraPublicar(fonte: FonteDaFaixa, origem: OrigemDaCapa): CapaParaPublicar {
        val chave = ChaveDeCapa.montar(fonte.name, origem.chave)
        val doCache = cache[chave]
        val texto = when {
            doCache != null -> doCache
            origem.bitmap != null && !origem.bitmap.isRecycled -> {
                pedirCodificacao(chave, origem.bitmap)
                ""
            }
            origem.urlRemota.isNotEmpty() -> {
                pedirDownload(chave, origem.urlRemota)
                origem.urlRemota
            }
            origem.consulta.isNotEmpty() -> {
                pedirBusca(chave, origem.consulta)
                ""
            }
            else -> ""
        }
        chaveAtual = chave
        textoAtual = texto
        return CapaParaPublicar(chave, texto)
    }

    // --- codificação do bitmap ---------------------------------------------------------------

    private fun pedirCodificacao(chave: String, bitmap: Bitmap) {
        if (chave in codificando || !recuoDaCodificacao.podeTentar(chave)) return
        codificando += chave
        val enviado = enviar(codificacao) {
            val texto = CodificadorDeCapa.paraDataUrl(bitmap)
            principal.post {
                codificando -= chave
                if (!aberto) return@post
                if (texto != null) {
                    cache.put(chave, texto)
                    aoPrecisarRepublicar()
                } else {
                    recuoDaCodificacao.registrarFalha(chave)
                }
            }
        }
        if (!enviado) codificando -= chave
    }

    // --- download de URL remota (E6) ---------------------------------------------------------

    private fun pedirDownload(chave: String, url: String) {
        if (url == urlEmVoo) return
        if (!recuoDoDownload.podeTentar(url)) return
        urlEmVoo = url // o resultado de um pedido anterior, diferente deste, será descartado ao chegar
        val enviado = enviar(rede) {
            val texto = if (urlEmVoo == url) baixarECodificar(url) else null // pedido substituído: nem tenta
            principal.post {
                if (!aberto || urlEmVoo != url) return@post
                urlEmVoo = null
                if (texto != null) {
                    cache.put(chave, texto)
                    recuoDoDownload.esquecer(url)
                    aoPrecisarRepublicar()
                } else {
                    Diario.info("capa remota indisponível; nova tentativa só em ${RECUO_MS / 1000} s")
                    recuoDoDownload.registrarFalha(url)
                }
            }
        }
        if (!enviado) urlEmVoo = null
    }

    /** Para URL com ID de vídeo do YouTube, tenta as resoluções em ordem e usa a primeira utilizável. */
    private fun baixarECodificar(url: String): String? {
        val id = IdDeVideoDoYoutube.deUrl(url)
        if (id != null) {
            for (candidata in IdDeVideoDoYoutube.candidatasDeDownload(id)) {
                val bitmap = decodificar(candidata) ?: continue
                if (bitmap.ehCapaUtilizavel()) return CodificadorDeCapa.paraDataUrl(bitmap)
            }
            return null
        }
        val bitmap = decodificar(url) ?: return null
        return CodificadorDeCapa.paraDataUrl(bitmap)
    }

    private fun decodificar(url: String): Bitmap? {
        val bytes = baixarBytes(url) ?: return null
        return DecodificadorDeImagem.deBytes(bytes)
    }

    // --- busca no YouTube (E8) ---------------------------------------------------------------

    private fun pedirBusca(chave: String, consulta: String) {
        if (consulta == consultaEmVoo) return
        val conhecida = consultaParaUrl[consulta]
        if (conhecida != null) {
            pedirDownload(chave, conhecida)
            return
        }
        if (!recuoDaBusca.podeTentar(consulta)) return
        consultaEmVoo = consulta
        val enviado = enviar(busca) {
            val id = buscarVideo(consulta)
            principal.post {
                if (!aberto || consultaEmVoo != consulta) return@post
                consultaEmVoo = null
                if (id != null) {
                    val url = IdDeVideoDoYoutube.urlDaCapa(id)
                    consultaParaUrl.put(consulta, url)
                    pedirDownload(chave, url)
                } else {
                    recuoDaBusca.registrarFalha(consulta)
                }
            }
        }
        if (!enviado) consultaEmVoo = null
    }

    // --- infraestrutura ----------------------------------------------------------------------

    private fun enviar(executor: ExecutorService?, trabalho: () -> Unit): Boolean {
        val e = executor ?: return false
        return try {
            e.execute { trabalho() }
            true
        } catch (_: RejectedExecutionException) {
            false
        }
    }

    private fun executor(nome: String): ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, nome).apply { isDaemon = true }
    }

    companion object {
        const val MAXIMO_NO_CACHE = 3
        const val MAXIMO_DE_CONSULTAS = 64
        const val RECUO_MS = 20_000L
        const val CONECTAR_DO_DOWNLOAD_MS = 5_000
        const val LER_DO_DOWNLOAD_MS = 8_000
    }
}

/** Busca no YouTube por título e artista: devolve o primeiro `videoId` da página de resultados. */
internal fun buscarNoYoutube(consulta: String): String? = ClienteHttp.obter(
    endereco = BuscaNoYoutube.urlDaBusca(consulta),
    conectarMs = 6_000,
    lerMs = 15_000,
    cabecalhos = mapOf("Accept-Language" to "en-US,en;q=0.9"),
) { entrada -> BuscaNoYoutube.primeiroVideoId(entrada) }
