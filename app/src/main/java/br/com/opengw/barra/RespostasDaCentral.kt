package br.com.opengw.barra

import android.graphics.Bitmap
import com.google.gson.JsonParser
import java.util.Locale

/**
 * Leitor abstrato da resposta de uma transação da central (na central, um `Parcel` já depois do
 * cabeçalho de exceção; nos testes, uma lista de valores).
 */
internal interface LeitorDeResposta {
    fun inteiro(): Int
    fun longo(): Long
    fun real(): Float
    fun texto(): String?
    fun byte(): Byte
    fun bytes(): ByteArray?
    /** Um `Bitmap` escrito como parcelável (nome da classe + dados), ou null. */
    fun bitmap(): Bitmap?
    /** Um `Bundle` (descartado). */
    fun pularBundle()
}

/** Resposta da transação 19 (`getPlayStateBySource`). */
internal data class EstadoNaCentral(
    val fonte: Int,
    val estado: Int,
    val duracaoMs: Long,
    val posicaoMs: Long,
) {
    /** 3 = tocando; 4, 5, 6 = parado; outros = desconhecido. */
    val tocando: Boolean?
        get() = when (estado) {
            3 -> true
            4, 5, 6 -> false
            else -> null
        }

    /** A posição se > 0 ou se há duração; senão nenhuma. */
    val progressoValido: Long?
        get() = if (posicaoMs > 0 || duracaoMs > 0) posicaoMs else null
}

/** Resposta da transação 22 (`getPlayMediaInfoBySource`), só o que a barra usa. */
internal data class InfoNaCentral(
    val fonte: Int,
    val titulo: String?,
    val artista: String?,
    /** O campo `albumId` do objeto de mídia (M-D3: pode ser identificador, não nome). */
    val album: String?,
    val caminho: String?,
    val urlDaImagem: String?,
    val imagem: Bitmap?,
    val duracaoMs: Long,
) {
    val temMetadados: Boolean
        get() = titulo != null || artista != null || album != null || urlDaImagem != null ||
            imagem != null || duracaoMs > 0
}

internal object RespostasDaCentral {

    /** Transação 19, depois do cabeçalho de exceção. Presença 0 → null. */
    fun lerEstado(r: LeitorDeResposta): EstadoNaCentral? {
        if (r.inteiro() == 0) return null
        val fonte = r.inteiro()
        val estado = r.inteiro()
        val duracao = r.longo().coerceAtLeast(0L)
        r.longo() // posição em buffer
        r.real() // velocidade
        r.longo() // velocidade TCP
        r.texto() // erro
        val posicao = r.longo().coerceAtLeast(0L)
        return EstadoNaCentral(fonte, estado, duracao, posicao)
    }

    /** Transação 22, depois do cabeçalho de exceção. Presença 0 ou sem objeto de mídia → null. */
    fun lerInfo(r: LeitorDeResposta): InfoNaCentral? {
        if (r.inteiro() == 0) return null
        val fonte = r.inteiro()
        r.byte()
        if (r.texto() != null) r.bytes() // Serializable: nome da classe e os bytes
        if (r.texto() == null) return null // Parcelable do objeto de mídia ausente
        val textos = Array(14) { r.texto() }
        val duracao = r.longo()
        r.inteiro() // ordem
        r.inteiro() // índice
        r.byte()
        val imagem = r.bitmap()
        r.pularBundle()
        repeat(4) { r.texto() }
        r.byte()
        fun util(s: String?) = s?.takeIf { it.isNotEmpty() }
        return InfoNaCentral(
            fonte = fonte,
            titulo = util(textos[8]),
            artista = util(textos[10]),
            album = util(textos[6]),
            caminho = util(textos[2]),
            urlDaImagem = util(textos[11]),
            imagem = imagem,
            duracaoMs = duracao.takeIf { it > 0 } ?: 0L,
        )
    }
}

/** O instantâneo do USB nas preferências da central (caso 40). */
internal data class InstantaneoUsb(
    val titulo: String?,
    val caminho: String?,
    val duracaoMs: Long,
    val posicaoMs: Long,
    val tocando: Boolean,
)

/** Regras de texto do leitor de arquivos da central (caso 40). Puras. */
internal object PreferenciasDaCentral {
    const val ARQUIVO_FONTE = "/data/user_de/0/com.beantechs.mediacenter/shared_prefs/media_info_preference.xml"
    const val ARQUIVO_MODO = "/data/user_de/0/com.beantechs.mediacenter/shared_prefs/media_info_last_mode.xml"
    const val LIMITE_DE_LEITURA = 524_288

    private val ELEMENTO_VAZIO = Regex(
        """<\s*(\w+)\s+name\s*=\s*"([^"]*)"(?:\s+value\s*=\s*"([^"]*)")?\s*/\s*>""",
        RegexOption.IGNORE_CASE,
    )
    private val ELEMENTO_COM_TEXTO = Regex(
        """<\s*(\w+)\s+name\s*=\s*"([^"]*)"\s*>(.*?)<\s*/\s*\1\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    /** A saída de `head -c 524288` serve? Vazia ou do tamanho do limite (cortada) → não. */
    fun saidaUtil(saida: String?): String? =
        saida?.takeIf { it.isNotEmpty() && it.length < LIMITE_DE_LEITURA }

    fun desescapar(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    /** Nome (minúsculo) → valor (atributo `value` ou texto, desescapado, vazio = ausente). */
    fun valores(xml: String): Map<String, String> {
        val m = HashMap<String, String>()
        for (r in ELEMENTO_VAZIO.findAll(xml)) {
            val v = r.groupValues[3]
            if (v.isNotEmpty()) m[r.groupValues[2].lowercase(Locale.ROOT)] = desescapar(v)
        }
        for (r in ELEMENTO_COM_TEXTO.findAll(xml)) {
            val v = r.groupValues[3]
            if (v.isNotEmpty()) m[r.groupValues[2].lowercase(Locale.ROOT)] = desescapar(v)
        }
        return m
    }

    /** A fonte ativa (`media_source`). */
    fun fonteAtiva(xml: String?): Int? = xml?.let { valores(it)["media_source"]?.trim()?.toIntOrNull() }

    /** O instantâneo da fonte 2. Sem título, sem caminho e sem duração → null. */
    fun instantaneo(xml: String?, fonte: Int = CentralDeMidiaLida.FONTE_USB): InstantaneoUsb? {
        if (xml.isNullOrEmpty()) return null
        val v = valores(xml)
        var titulo: String? = null
        var caminho: String? = null
        var duracaoJson = 0L
        v["last_play_media_info$fonte"]?.let { json ->
            runCatching {
                val o = JsonParser.parseString(json).asJsonObject
                titulo = o.get("title")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
                caminho = o.get("path")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
                duracaoJson = o.get("duration")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.toDoubleOrNull()?.toLong() ?: 0L
            }
        }
        val duracao = v["playduration$fonte"]?.trim()?.toLongOrNull()?.takeIf { it > 0 } ?: duracaoJson.coerceAtLeast(0L)
        val posicaoLida = v["playposition$fonte"]?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val posicao = if (duracao > 0) posicaoLida.coerceAtMost(duracao) else posicaoLida
        val tocando = v["isplaying$fonte"]?.trim()?.lowercase(Locale.ROOT).let { it == "true" || it == "1" }
        if (titulo == null && caminho == null && duracao <= 0) return null
        return InstantaneoUsb(titulo, caminho, duracao, posicao, tocando)
    }

    private val EXTENSOES = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "ape")
    private val RAIZES = listOf("/storage/", "/mnt/media_rw/", "/mnt/usb/", "/mnt/runtime/")

    /** Um caminho que pode ir ao shell sem susto (caso 40). */
    fun caminhoSeguro(caminho: String?): Boolean {
        val c = caminho ?: return false
        if (c.length !in 2..1024 || !c.startsWith("/")) return false
        if (c.any { it.isISOControl() }) return false
        if (RAIZES.none { c.startsWith(it) }) return false
        if (c.split('/').any { it == "." || it == ".." }) return false
        val ext = c.substringAfterLast('/').substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in EXTENSOES
    }

    /** O nome do arquivo sem extensão (título de reserva). */
    fun nomeSemExtensao(caminho: String?): String? {
        val nome = caminho?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: return null
        val semExt = if (nome.contains('.')) nome.substringBeforeLast('.') else nome
        return semExt.takeIf { it.isNotEmpty() }
    }
}
