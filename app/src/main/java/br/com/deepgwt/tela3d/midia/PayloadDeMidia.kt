package br.com.deepgwt.tela3d.midia

import org.json.JSONObject

/** Para quem hospeda a página (ponte) e para o cartão. Sempre chamado na thread principal. */
interface DestinoDoTocandoAgora {
    /** Os 13 campos da seção "payload" da especificação. */
    fun aoMudarFaixa(payload: JSONObject)

    fun aoMudarPosicao(posicaoMs: Long)
}

/** Textos fixos do payload. */
object RotulosDeMidia {
    const val USB = "USB"
    const val ANDROID_AUTO = "ANDROID AUTO"
    const val CARPLAY = "CARPLAY"

    /** Valor de `appLabel` quando o ouvinte de notificações não tem acesso (o cartão o mostra como título). */
    const val ACESSO_NECESSARIO = "Ativar acesso à mídia"
}

/**
 * O objeto de estado completo que a página recebe em `onMediaNowPlaying`: todo payload leva as 13
 * chaves e substitui o estado anterior (inclusive a capa).
 */
data class PayloadDeMidia(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val appLabel: String = "",
    val packageName: String = "",
    val artDataUrl: String = "",
    val appIcon: String = "",
    val canLaunch: Boolean = false,
    val hasTrack: Boolean = false,
    val needsListener: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("durationMs", durationMs)
        put("positionMs", positionMs)
        put("playing", playing)
        put("appLabel", appLabel)
        put("packageName", packageName)
        put("artDataUrl", artDataUrl)
        put("appIcon", appIcon)
        put("canLaunch", canLaunch)
        put("hasTrack", hasTrack)
        put("needsListener", needsListener)
    }

    companion object {
        /** Payload sem faixa (casos D2): tudo vazio; só `needsListener` e o rótulo variam. */
        fun vazio(precisaDeAcesso: Boolean): PayloadDeMidia = PayloadDeMidia(
            appLabel = if (precisaDeAcesso) RotulosDeMidia.ACESSO_NECESSARIO else "",
            needsListener = precisaDeAcesso,
        )

        /** Leitura tolerante do JSON (usada pelo cartão nativo): chave ausente vira o valor vazio. */
        fun deJson(json: JSONObject): PayloadDeMidia = PayloadDeMidia(
            title = json.optString("title", ""),
            artist = json.optString("artist", ""),
            album = json.optString("album", ""),
            durationMs = json.optLong("durationMs", 0L),
            positionMs = json.optLong("positionMs", 0L),
            playing = json.optBoolean("playing", false),
            appLabel = json.optString("appLabel", ""),
            packageName = json.optString("packageName", ""),
            artDataUrl = json.optString("artDataUrl", ""),
            appIcon = json.optString("appIcon", ""),
            canLaunch = json.optBoolean("canLaunch", false),
            hasTrack = json.optBoolean("hasTrack", false),
            needsListener = json.optBoolean("needsListener", false),
        )
    }
}

/** Origem de uma faixa; a prioridade desempata a pontuação do vencedor (caso G2). */
enum class FonteDaFaixa(val prioridade: Int) {
    SESSAO(1),
    CENTRAL(2),
    CARPLAY(3),
}

/** Tudo o que o payload de uma faixa precisa, já resolvido (capa e ícone prontos). */
internal class EntradaDoPayload(
    val fonte: FonteDaFaixa,
    val titulo: String,
    val artista: String,
    val album: String,
    val duracaoMs: Long,
    val posicaoMs: Long,
    val tocando: Boolean,
    val rotulo: String,
    val pacote: String,
    /** Chave da capa atual (entra na assinatura; muda quando a imagem muda). */
    val chaveDaCapa: String,
    val artDataUrl: String,
    val appIcon: String,
    val canLaunch: Boolean,
) {
    fun paraPayload(): PayloadDeMidia = PayloadDeMidia(
        title = titulo,
        artist = artista,
        album = album,
        durationMs = duracaoMs.coerceAtLeast(0L),
        positionMs = posicaoMs.coerceAtLeast(0L),
        playing = tocando,
        appLabel = rotulo,
        packageName = pacote,
        artDataUrl = artDataUrl,
        appIcon = appIcon,
        canLaunch = canLaunch,
        hasTrack = titulo.isNotEmpty() || artista.isNotEmpty(),
        needsListener = false,
    )

    /**
     * Assinatura de deduplicação (caso D6). A posição NÃO entra: ela viaja pelo relógio de posição.
     */
    fun assinatura(): String = listOf(
        fonte.name, pacote, titulo, artista, album, duracaoMs.toString(), tocando.toString(),
        rotulo, chaveDaCapa, artDataUrl.length.toString(), canLaunch.toString(), appIcon.length.toString(),
    ).joinToString(SEPARADOR)

    private companion object {
        const val SEPARADOR = "\u0001"
    }
}

/**
 * Entrega o payload ao destino só quando a assinatura mudou (casos D1, D2, D6). Lógica pura:
 * todas as chamadas na thread principal.
 */
internal class PublicadorDePayload {
    var destino: DestinoDoTocandoAgora? = null
    private var ultimaAssinatura: String? = null

    /** O próximo envio sai mesmo que idêntico ao anterior ("reenviar agora"). */
    fun esquecerAssinatura() {
        ultimaAssinatura = null
    }

    /** Devolve verdadeiro se o payload foi entregue. */
    fun publicar(entrada: EntradaDoPayload): Boolean {
        val assinatura = entrada.assinatura()
        return entregar(assinatura, entrada.paraPayload())
    }

    fun publicarVazio(precisaDeAcesso: Boolean): Boolean =
        entregar("vazio\u0001$precisaDeAcesso", PayloadDeMidia.vazio(precisaDeAcesso))

    private fun entregar(assinatura: String, payload: PayloadDeMidia): Boolean {
        val d = destino ?: return false // sem destino não marca como enviado: o próximo envio sai
        if (assinatura == ultimaAssinatura) return false
        ultimaAssinatura = assinatura
        d.aoMudarFaixa(payload.toJson())
        return true
    }
}
