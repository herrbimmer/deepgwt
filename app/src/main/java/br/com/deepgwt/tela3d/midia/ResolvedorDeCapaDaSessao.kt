package br.com.deepgwt.tela3d.midia

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import android.app.Notification
import android.graphics.drawable.Icon
import java.util.Locale

/**
 * Descobre a origem da capa de uma faixa de sessão (caso E1): bitmaps dos metadados, URIs, bitmaps
 * das notificações do mesmo app, URL remota e consulta de busca. A decodificação de URIs de conteúdo
 * roda fora da principal e volta por [aoDecodificarUri]. Usar na thread principal.
 */
internal class ResolvedorDeCapaDaSessao(
    private val contexto: Context,
    private val principal: Handler,
    private val capas: GerenteDeCapas,
    /** Notificações ativas (nulo se o ouvinte não está conectado). */
    private val notificacoesAtivas: () -> Array<StatusBarNotification>?,
    /** Uma imagem de URI ficou pronta: reler a sessão. */
    private val aoDecodificarUri: () -> Unit,
) {
    private val decodificados = CacheFifo<String, Bitmap>(2)
    private val emVoo = HashSet<String>()
    private val recuo = RecuoPorChave(20_000L, SystemClock::elapsedRealtime)
    private var ultimoDiagnostico: String? = null

    fun resolver(pacote: String, titulo: String, artista: String, metadados: MediaMetadata?): OrigemDaCapa {
        val descricao = metadados?.description

        // 1. O maior bitmap entre ícone da descrição, ALBUM_ART, ART e DISPLAY_ICON; o filtro vale só para ele.
        val candidatos = listOfNotNull(
            descricao?.iconBitmap,
            metadados?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            metadados?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            metadados?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
        ).filter { !it.isRecycled }
        val indice = RegrasDeCapa.indiceDoMaiorPorArea(candidatos) { it.width.toLong() * it.height }
        var bitmap: Bitmap? = candidatos.getOrNull(indice)?.takeIf { it.ehCapaUtilizavel() }

        // 2. URI da capa.
        var uri = RegrasDeCapa.primeiroNaoVazio(
            metadados?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
            metadados?.getString(MediaMetadata.METADATA_KEY_ART_URI),
            metadados?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            descricao?.iconUri?.toString(),
        )
        val textos = if (metadados != null) metadadosDeTexto(metadados) else emptyList()
        if (uri.isEmpty()) uri = RegrasDeCapa.uriPorVarredura(textos)

        // 3. URI que não é http(s) e tem esquema: decodificar (fora da principal) e comparar por área.
        if (RegrasDeCapa.uriPedeDecodificar(uri)) {
            val doUri = decodificados[uri]
            if (doUri != null && !doUri.isRecycled) {
                val areaAtual = bitmap?.let { it.width.toLong() * it.height } ?: 0L
                if (bitmap == null || RegrasDeCapa.decodificadoVence(areaAtual, doUri.width.toLong() * doUri.height)) bitmap = doUri
            } else {
                pedirDecodificacao(uri)
            }
        }

        // 4. Sem bitmap e com o ouvinte conectado: o maior bitmap utilizável das notificações do app.
        if (bitmap == null) bitmap = bitmapDasNotificacoes(pacote)

        // 5–7. URL remota, descarte do bitmap pequeno e consulta de busca.
        val plano = RegrasDeCapa.planejarOrigem(
            maiorLadoDoBitmap = bitmap?.let { maxOf(it.width, it.height) } ?: 0,
            uri = uri,
            idDoYoutube = IdDeVideoDoYoutube.deUrl(uri) ?: idNosMetadados(pacote, metadados, textos),
            pacote = pacote,
            titulo = titulo,
            artista = artista,
        )
        val escolhido = if (plano.descartarBitmap) null else bitmap

        val chave = ChaveDeCapa.montar(
            pacote,
            escolhido?.let { AmostradorDeCapa.chaveDe(it, "$pacote|$titulo|$artista") }.orEmpty(),
            uri,
            plano.urlRemota,
            plano.consulta,
        )
        val origem = OrigemDaCapa(escolhido, plano.urlRemota, plano.consulta, chave)
        if (!origem.temCapa && plano.consulta.isEmpty() && metadados != null) diagnosticar(pacote, metadados)
        return origem
    }

    // --- passo 3 ------------------------------------------------------------------------------

    private fun pedirDecodificacao(uri: String) {
        if (uri in emVoo || !recuo.podeTentar(uri)) return
        emVoo += uri
        val enviado = capas.emFundo {
            val imagem = DecodificadorDeImagem.deUri(contexto, uri)
            principal.post {
                emVoo -= uri
                if (imagem != null && imagem.ehCapaUtilizavel()) {
                    decodificados.put(uri, imagem)
                    aoDecodificarUri()
                } else {
                    recuo.registrarFalha(uri)
                }
            }
        }
        if (!enviado) emVoo -= uri
    }

    // --- passo 4 ------------------------------------------------------------------------------

    private fun bitmapDasNotificacoes(pacote: String): Bitmap? {
        val ativas: Array<StatusBarNotification>? = try {
            notificacoesAtivas()
        } catch (_: RuntimeException) {
            null
        }
        if (ativas == null) return null
        var melhor: Bitmap? = null
        for (sbn in ativas) {
            if (sbn.packageName != pacote) continue
            for (candidato in bitmapsDaNotificacao(sbn.notification)) {
                if (!candidato.ehCapaUtilizavel()) continue
                val atual = melhor
                if (atual == null || candidato.width.toLong() * candidato.height > atual.width.toLong() * atual.height) {
                    melhor = candidato
                }
            }
        }
        return melhor
    }

    @Suppress("DEPRECATION") // Bundle.get genérico: as extras podem ser Bitmap ou Icon
    private fun bitmapsDaNotificacao(notificacao: Notification?): List<Bitmap> {
        if (notificacao == null) return emptyList()
        val resultado = ArrayList<Bitmap>(4)
        val extras = notificacao.extras
        val chaves = ArrayList<String>(4)
        chaves += "android.picture"
        chaves += "android.largeIcon.big"
        chaves += "android.largeIcon"
        if (Build.VERSION.SDK_INT >= 31) chaves += "android.pictureIcon"
        for (chave in chaves) {
            when (val valor = extras?.get(chave)) {
                is Bitmap -> resultado += valor
                is Icon -> iconeParaBitmap(valor)?.let { resultado += it }
            }
        }
        notificacao.getLargeIcon()?.let { icone -> iconeParaBitmap(icone)?.let { resultado += it } }
        return resultado
    }

    private fun iconeParaBitmap(icone: Icon): Bitmap? = try {
        icone.loadDrawable(contexto)?.paraBitmap()
    } catch (_: Exception) {
        null
    }

    // --- passo 5 ------------------------------------------------------------------------------

    private fun metadadosDeTexto(md: MediaMetadata): List<Pair<String, String>> {
        val resultado = ArrayList<Pair<String, String>>()
        for (chave in md.keySet()) {
            if (chave in CHAVES_QUE_NAO_SAO_TEXTO) continue
            val valor = md.getText(chave)?.toString() ?: continue
            if (valor.isNotEmpty()) resultado += chave to valor
        }
        return resultado
    }

    /**
     * ID de vídeo do YouTube nos metadados (E3), na ordem: MEDIA_ID, MEDIA_URI, DISPLAY_DESCRIPTION,
     * mediaId da descrição, mediaUri da descrição e qualquer texto (só em formato de URL). ID "nu" só
     * vale em app do YouTube: em outros apps um identificador qualquer de 11 caracteres geraria uma
     * capa errada.
     */
    private fun idNosMetadados(pacote: String, md: MediaMetadata?, textos: List<Pair<String, String>>): String? {
        if (md == null) return null
        val nuValeAqui = "youtube" in pacote.lowercase(Locale.ROOT)
        val d = md.description
        val tentativas = ArrayList<Pair<String?, Boolean>>()
        tentativas += md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) to nuValeAqui
        tentativas += md.getString(MediaMetadata.METADATA_KEY_MEDIA_URI) to false
        tentativas += md.getString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION) to false
        tentativas += d?.mediaId to nuValeAqui
        tentativas += d?.mediaUri?.toString() to false
        for ((_, valor) in textos) tentativas += valor to false
        return IdDeVideoDoYoutube.primeiroDe(tentativas)
    }

    // --- passo 9 ------------------------------------------------------------------------------

    private fun diagnosticar(pacote: String, md: MediaMetadata) {
        val linha = buildString {
            append(pacote).append(": sem origem de capa; metadados:")
            for (chave in md.keySet()) {
                append(' ').append(chave.substringAfterLast('.')).append('=')
                if (chave in CHAVES_QUE_NAO_SAO_TEXTO) {
                    val b = if (chave in CHAVES_DE_BITMAP) md.getBitmap(chave) else null
                    append(if (b != null) "${b.width}x${b.height}" else "-")
                } else {
                    val t = md.getText(chave)?.toString().orEmpty()
                    append(if (t.length > 60) t.take(60) + "…" else t)
                }
            }
        }
        if (linha == ultimoDiagnostico) return
        ultimoDiagnostico = linha
        Diario.info(linha)
    }

    private companion object {
        val CHAVES_DE_BITMAP = setOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART,
            MediaMetadata.METADATA_KEY_ART,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON,
        )

        /** Chaves de metadados que não guardam texto (bitmap, avaliação, número): ler como texto só faz ruído no log do sistema. */
        val CHAVES_QUE_NAO_SAO_TEXTO = CHAVES_DE_BITMAP + setOf(
            MediaMetadata.METADATA_KEY_USER_RATING,
            MediaMetadata.METADATA_KEY_RATING,
            MediaMetadata.METADATA_KEY_DURATION,
            MediaMetadata.METADATA_KEY_YEAR,
            MediaMetadata.METADATA_KEY_TRACK_NUMBER,
            MediaMetadata.METADATA_KEY_NUM_TRACKS,
            MediaMetadata.METADATA_KEY_DISC_NUMBER,
            "android.media.metadata.BT_FOLDER_TYPE",
        )
    }
}
