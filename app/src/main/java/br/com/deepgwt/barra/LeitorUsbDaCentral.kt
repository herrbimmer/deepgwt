// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import br.com.deepgwt.nucleo.Shell
import java.io.File

/** Tags lidas do arquivo da faixa (caso 40). */
internal data class TagsDaFaixa(val artista: String?, val album: String?, val duracaoMs: Long?, val capa: Bitmap?) {
    companion object {
        val VAZIAS = TagsDaFaixa(null, null, null, null)
    }
}

/**
 * O leitor dos arquivos da central de mídia pelo Shell (caso 40): as preferências da central e as
 * tags da faixa do USB. Só fundo (shell e cópia de arquivo).
 */
internal class LeitorUsbDaCentral(private val contexto: Context) {

    private val trava = Any()
    private var caminhoDasTags: String? = null
    private var tagsGuardadas: TagsDaFaixa = TagsDaFaixa.VAZIAS

    private fun lerArquivo(caminho: String): String? {
        if (!Shell.disponivel()) return null
        return PreferenciasDaCentral.saidaUtil(
            Shell.executar(arrayOf("head", "-c", PreferenciasDaCentral.LIMITE_DE_LEITURA.toString(), caminho))
        )
    }

    /** A fonte ativa gravada pela central. */
    fun fonteAtiva(): Int? = PreferenciasDaCentral.fonteAtiva(lerArquivo(PreferenciasDaCentral.ARQUIVO_FONTE))

    /** O instantâneo da fonte 2 (sem exigir que ela esteja ativa). */
    fun instantaneo(): InstantaneoUsb? = PreferenciasDaCentral.instantaneo(lerArquivo(PreferenciasDaCentral.ARQUIVO_MODO))

    /** Esquece o cache das tags. */
    fun limparCache() {
        synchronized(trava) {
            caminhoDasTags = null
            tagsGuardadas = TagsDaFaixa.VAZIAS
        }
    }

    /** As tags do arquivo (com cache do último caminho); qualquer erro → vazias. */
    fun tags(caminho: String): TagsDaFaixa {
        synchronized(trava) { if (caminho == caminhoDasTags) return tagsGuardadas }
        val lidas = if (PreferenciasDaCentral.caminhoSeguro(caminho)) lerTags(caminho) else TagsDaFaixa.VAZIAS
        synchronized(trava) {
            caminhoDasTags = caminho
            tagsGuardadas = lidas
        }
        return lidas
    }

    private fun lerTags(caminho: String): TagsDaFaixa {
        if (!Shell.disponivel()) return TagsDaFaixa.VAZIAS
        val tamanho = Shell.executar(arrayOf("stat", "-c", "%s", caminho)).trim().toLongOrNull() ?: return TagsDaFaixa.VAZIAS
        if (tamanho <= 0 || tamanho > TAMANHO_MAXIMO) return TagsDaFaixa.VAZIAS
        // O shell precisa alcançar a pasta: o cache externo do app é o que ele alcança sem root.
        val pasta = contexto.externalCacheDir ?: contexto.cacheDir
        val temporario = File(pasta, "barra-usb-${System.nanoTime()}.tmp")
        return try {
            temporario.createNewFile()
            temporario.setReadable(true, false)
            temporario.setWritable(true, false)
            Shell.executar(arrayOf("cp", caminho, temporario.absolutePath))
            Shell.executar(arrayOf("chmod", "644", temporario.absolutePath))
            val copiado = temporario.length()
            if (copiado <= 0 || copiado > TAMANHO_MAXIMO) return TagsDaFaixa.VAZIAS
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(temporario.absolutePath)
                val artista = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
                    ?: r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.takeIf { it.isNotBlank() }
                val album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
                val duracao = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.trim()?.toLongOrNull()
                val capa = CapaDaMidia.deBytes(r.embeddedPicture)
                TagsDaFaixa(artista, album, duracao, capa)
            } finally {
                runCatching { r.release() }
            }
        } catch (e: Exception) {
            TagsDaFaixa.VAZIAS
        } finally {
            runCatching { temporario.delete() }
        }
    }

    companion object {
        const val TAMANHO_MAXIMO = 128L * 1024 * 1024
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
