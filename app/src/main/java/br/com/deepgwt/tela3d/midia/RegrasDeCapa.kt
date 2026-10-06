package br.com.deepgwt.tela3d.midia

import java.io.InputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Largura e altura em pixels. */
internal data class Dimensoes(val largura: Int, val altura: Int) {
    val area: Long get() = largura.toLong() * altura.toLong()
    val maiorLado: Int get() = max(largura, altura)
}

/** O que fazer com as origens de capa de uma faixa de sessão depois dos passos de bitmap e URI. */
internal class PlanoDaOrigemDaCapa(
    /** O bitmap achado foi descartado em favor de uma URL remota de resolução melhor. */
    val descartarBitmap: Boolean,
    /** URL remota que a página (e o download nativo) devem usar; vazia se não há ou se o bitmap vale. */
    val urlRemota: String,
    /** Consulta de busca; vazia se não é para buscar. */
    val consulta: String,
)

/** Regras de capa (caso E). Puras. */
internal object RegrasDeCapa {
    const val LADO_MINIMO_UTILIZAVEL = 48
    const val LARGURA_DE_MINIATURA = 120
    const val ALTURA_DE_MINIATURA = 90
    const val LADO_DE_CAPA_GRANDE = 96
    const val DIFERENCA_DE_PROPORCAO = 8
    const val LADO_DA_REMOTA_MELHOR = 320
    const val LADO_MAXIMO_CODIFICADO = 720
    const val LADO_MAXIMO_DECODIFICADO = 1440

    /** E2: descarta ícones de app e miniaturas. */
    fun ehUtilizavel(largura: Int, altura: Int): Boolean {
        val maior = max(largura, altura)
        if (maior < LADO_MINIMO_UTILIZAVEL) return false
        if (largura <= LARGURA_DE_MINIATURA && altura <= ALTURA_DE_MINIATURA) return false
        return maior >= LADO_DE_CAPA_GRANDE || abs(largura - altura) >= DIFERENCA_DE_PROPORCAO
    }

    /** E5: escala mantendo a proporção até o lado máximo de 720 px; nunca amplia; mínimo 1 px por lado. */
    fun dimensoesCodificadas(largura: Int, altura: Int): Dimensoes {
        if (largura <= LADO_MAXIMO_CODIFICADO && altura <= LADO_MAXIMO_CODIFICADO) return Dimensoes(largura, altura)
        val fator = min(
            LADO_MAXIMO_CODIFICADO.toDouble() / largura,
            LADO_MAXIMO_CODIFICADO.toDouble() / altura,
        )
        return Dimensoes(
            max(1, (largura * fator).roundToInt()),
            max(1, (altura * fator).roundToInt()),
        )
    }

    /**
     * Fator de amostragem (potência de 2) para decodificar sem estourar a memória: reduz enquanto os
     * dois lados continuam com pelo menos [maximo] pixels (defeito 14).
     */
    fun fatorDeAmostragem(largura: Int, altura: Int, maximo: Int = LADO_MAXIMO_DECODIFICADO): Int {
        if (largura <= 0 || altura <= 0) return 1
        var fator = 1
        while (largura / (fator * 2) >= maximo && altura / (fator * 2) >= maximo) fator *= 2
        return fator
    }

    /** Índice do item de maior área; empate fica com o primeiro; -1 se a lista é vazia. */
    fun <T> indiceDoMaiorPorArea(itens: List<T>, area: (T) -> Long): Int {
        var melhor = -1
        var melhorArea = Long.MIN_VALUE
        for ((i, item) in itens.withIndex()) {
            val a = area(item)
            if (a > melhorArea) {
                melhor = i
                melhorArea = a
            }
        }
        return melhor
    }

    fun ehHttp(uri: String): Boolean {
        val u = uri.trimStart().lowercase(Locale.ROOT)
        return u.startsWith("http://") || u.startsWith("https://")
    }

    /** Esquema de um URI (`file`, `content`, ...) ou nulo se não tem (caminho puro). */
    fun esquemaDe(uri: String): String? {
        val m = ESQUEMA.find(uri.trim()) ?: return null
        return m.groupValues[1].lowercase(Locale.ROOT)
    }

    private val ESQUEMA = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

    /** Como obter a imagem de um URI de capa de uma central (caso H1). */
    enum class LocalDaImagem { NENHUM, ARQUIVO, CONTEUDO }

    fun localDaImagem(url: String): LocalDaImagem {
        if (url.isBlank()) return LocalDaImagem.NENHUM
        val esquema = esquemaDe(url) ?: return LocalDaImagem.ARQUIVO
        return when (esquema) {
            "file" -> LocalDaImagem.ARQUIVO
            "http", "https" -> LocalDaImagem.NENHUM
            else -> LocalDaImagem.CONTEUDO
        }
    }

    /** E1.3: o URI pede abrir pelo resolvedor de conteúdo (não é http(s) e tem esquema). */
    fun uriPedeDecodificar(uri: String): Boolean {
        if (uri.isBlank() || ehHttp(uri)) return false
        return esquemaDe(uri) != null
    }

    /** O primeiro texto não vazio, ou vazio. */
    fun primeiroNaoVazio(vararg textos: String?): String = textos.firstOrNull { !it.isNullOrEmpty() }.orEmpty()

    private val CHAVES_DE_IMAGEM = listOf("art", "icon", "thumb", "image", "cover")

    /**
     * E1.2, varredura de todos os metadados de texto (na ordem dada): vale o primeiro valor que
     * (contém `ytimg.com` ou `ggpht.com` ou começa com `http://`, `https://` ou `content://`) E cuja
     * chave contém art/icon/thumb/image/cover, OU cujo valor contém `ytimg` ou `youtube`.
     */
    fun uriPorVarredura(metadadosDeTexto: List<Pair<String, String>>): String {
        for ((chave, valor) in metadadosDeTexto) {
            if (valor.isEmpty()) continue
            val v = valor.lowercase(Locale.ROOT)
            val k = chave.lowercase(Locale.ROOT)
            val valorDeImagem = "ytimg.com" in v || "ggpht.com" in v ||
                v.startsWith("http://") || v.startsWith("https://") || v.startsWith("content://")
            val chaveDeImagem = CHAVES_DE_IMAGEM.any { it in k }
            if ((valorDeImagem && chaveDeImagem) || "ytimg" in v || "youtube" in v) return valor
        }
        return ""
    }

    /**
     * E1.5–E1.7. [maiorLadoDoBitmap] é 0 se não há bitmap utilizável. [idDoYoutube] é o ID achado no
     * URI ou nos metadados (E3).
     */
    fun planejarOrigem(
        maiorLadoDoBitmap: Int,
        uri: String,
        idDoYoutube: String?,
        pacote: String,
        titulo: String,
        artista: String,
    ): PlanoDaOrigemDaCapa {
        val temBitmap = maiorLadoDoBitmap > 0
        val remota = when {
            idDoYoutube != null -> IdDeVideoDoYoutube.urlDaCapa(idDoYoutube)
            ehHttp(uri) -> uri.trim()
            else -> ""
        }
        val descartar = temBitmap && remota.isNotEmpty() && maiorLadoDoBitmap < LADO_DA_REMOTA_MELHOR
        val bitmapVale = temBitmap && !descartar
        val urlFinal = if (bitmapVale) "" else remota
        val consulta = if (!bitmapVale && urlFinal.isEmpty() && "youtube" in pacote.lowercase(Locale.ROOT)) {
            "$titulo $artista".trim()
        } else {
            ""
        }
        return PlanoDaOrigemDaCapa(descartar, urlFinal, consulta)
    }

    /** Uma imagem decodificada de URI só substitui o bitmap do pacote se for estritamente maior. */
    fun decodificadoVence(areaDoBitmap: Long, areaDoDecodificado: Long): Boolean = areaDoDecodificado > areaDoBitmap
}

/** Extração de IDs de vídeo do YouTube (caso E3). Puro. */
internal object IdDeVideoDoYoutube {
    private const val ID = "([A-Za-z0-9_-]{11})"

    private val EM_URL = Regex(
        "(?:youtube(?:-nocookie)?\\.com/watch\\?(?:[^&\\s]*&)*?v=" +
            "|youtube(?:-nocookie)?\\.com/embed/" +
            "|/shorts/|/live/|/v/" +
            "|youtu\\.be/" +
            "|ytimg\\.com/vi(?:_webp)?/)$ID",
    )

    private val NU = Regex("^$ID$")

    /** ID dentro de uma URL (busca em qualquer ponto do texto, sem âncora). */
    fun deUrl(texto: String?): String? {
        if (texto.isNullOrEmpty()) return null
        return EM_URL.find(texto)?.groupValues?.get(1)
    }

    /** ID de uma URL ou "nu" (o texto inteiro são 11 caracteres de ID). */
    fun deUrlOuNu(texto: String?): String? {
        if (texto.isNullOrEmpty()) return null
        return deUrl(texto) ?: NU.find(texto)?.groupValues?.get(1)
    }

    /**
     * Ordem de tentativa nos metadados: cada par é (valor, aceita ID nu). O primeiro que render um
     * ID vence.
     */
    fun primeiroDe(tentativas: List<Pair<String?, Boolean>>): String? {
        for ((valor, aceitaNu) in tentativas) {
            val id = if (aceitaNu) deUrlOuNu(valor) else deUrl(valor)
            if (id != null) return id
        }
        return null
    }

    fun urlDaCapa(id: String): String = "https://i.ytimg.com/vi/$id/maxresdefault.jpg"

    /** Resoluções tentadas, da melhor para a pior (caso E6). */
    fun candidatasDeDownload(id: String): List<String> = listOf(
        "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
        "https://i.ytimg.com/vi/$id/hq720.jpg",
        "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        "https://i.ytimg.com/vi/$id/mqdefault.jpg",
    )
}

/** Busca de vídeo no YouTube por título e artista (caso E8): URL e leitura da resposta. Puro. */
internal object BuscaNoYoutube {
    const val LIMITE_DE_BYTES = 2 * 1024 * 1024
    const val TAMANHO_DO_BLOCO = 16 * 1024
    private const val LIMITE_DO_TEXTO = 32_768
    private const val CAUDA_MANTIDA = 64

    private val VIDEO_ID = Regex("\"videoId\"\\s*:\\s*\"([A-Za-z0-9_-]{11})\"")

    fun urlDaBusca(consulta: String): String =
        "https://www.youtube.com/results?hl=en&search_query=" + java.net.URLEncoder.encode(consulta, "UTF-8")

    /**
     * Lê [entrada] em blocos até [limiteDeBytes] procurando o primeiro `"videoId":"<11 caracteres>"`.
     * Só guarda a cauda do texto lido, para a memória não crescer.
     */
    fun primeiroVideoId(entrada: InputStream, limiteDeBytes: Int = LIMITE_DE_BYTES): String? {
        val bloco = ByteArray(TAMANHO_DO_BLOCO)
        val texto = StringBuilder()
        var lidos = 0
        while (lidos < limiteDeBytes) {
            val n = entrada.read(bloco, 0, min(bloco.size, limiteDeBytes - lidos))
            if (n < 0) break
            lidos += n
            // Só o ASCII importa para casar o padrão; latin-1 não perde nenhum byte.
            texto.append(String(bloco, 0, n, Charsets.ISO_8859_1))
            val achado = VIDEO_ID.find(texto)
            if (achado != null) return achado.groupValues[1]
            if (texto.length > LIMITE_DO_TEXTO) texto.delete(0, texto.length - CAUDA_MANTIDA)
        }
        return null
    }
}

/** Chave estável de capa (caso E1.8): baseada em conteúdo, não na identidade do objeto. Pura. */
internal object ChaveDeCapa {
    /** Hash barato (FNV-1a de 32 bits) de uma amostra de pixels, junto das dimensões. */
    fun deAmostra(largura: Int, altura: Int, amostra: IntArray): String {
        var h = -0x7ee3623b // 2166136261 como Int
        for (pixel in amostra) {
            h = (h xor pixel) * 16777619
        }
        return "${largura}x$altura#${Integer.toHexString(h)}"
    }

    fun montar(vararg partes: String): String = partes.joinToString("|")
}
