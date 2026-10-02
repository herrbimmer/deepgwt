package br.com.opengw.barra

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * As sessões de mídia do Android (casos 8–10, 14). Os avisos chegam na principal; a escolha e a
 * extração rodam em segundo plano; a publicação vai ao núcleo na principal.
 */
internal class SessoesDeMidia(
    private val contexto: Context,
    private val escopo: CoroutineScope,
    private val nucleo: NucleoDaMidia,
    /** Componente do ouvinte de notificações, se habilitado no sistema (caso 3). */
    private val componenteHabilitado: () -> ComponentName?,
    /** SecurityException: pedir a liberação do ouvinte (caso 4 (c)–(g)). */
    private val pedirLiberacao: () -> Unit,
) {
    private val principal = Handler(Looper.getMainLooper())
    private var gerenciador: MediaSessionManager? = null

    @Volatile
    private var controles: List<MediaController> = emptyList()
    private val avisos = HashMap<MediaController, MediaController.Callback>()
    private var publicacao: Job? = null

    @Volatile
    var dePe = false
        private set

    private val aoMudarSessoes = MediaSessionManager.OnActiveSessionsChangedListener { lista ->
        atualizarControles(lista.orEmpty())
    }

    /** Caso 8 (principal). */
    fun iniciar() {
        if (dePe) return
        try {
            val g = contexto.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componente = componenteHabilitado()
            val ativas = g.getActiveSessions(componente)
            g.addOnActiveSessionsChangedListener(aoMudarSessoes, componente, principal)
            gerenciador = g
            dePe = true
            atualizarControles(ativas)
        } catch (e: SecurityException) {
            Log.w(TAG, "sessões recusadas: ${e.message}")
            esquecer()
            nucleo.limparGeral(preservarProjecao = false)
            pedirLiberacao()
        } catch (e: Exception) {
            Log.w(TAG, "sessões indisponíveis: ${e.message}")
            esquecer()
            nucleo.limparGeral(preservarProjecao = false)
        }
    }

    /** Para; [limpar] zera o retrato (caso 2) — para reiniciar, sem limpar. */
    fun parar(limpar: Boolean) {
        runCatching { gerenciador?.removeOnActiveSessionsChangedListener(aoMudarSessoes) }
        esquecer()
        if (limpar) nucleo.limparGeral(preservarProjecao = false)
    }

    /** Reinicia (parar sem limpar + iniciar), na principal. */
    fun reiniciar() {
        parar(limpar = false)
        iniciar()
    }

    private fun esquecer() {
        publicacao?.cancel()
        for ((c, a) in avisos) runCatching { c.unregisterCallback(a) }
        avisos.clear()
        controles = emptyList()
        gerenciador = null
        dePe = false
    }

    /** Caso 9 (principal). */
    private fun atualizarControles(lista: List<MediaController>) {
        for ((c, a) in avisos) runCatching { c.unregisterCallback(a) }
        avisos.clear()
        for (c in lista) {
            val aviso = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publicarAMelhor()
                override fun onPlaybackStateChanged(state: PlaybackState?) = publicarAMelhor()
                override fun onSessionDestroyed() = publicarAMelhor()
            }
            runCatching { c.registerCallback(aviso, principal) }.onSuccess { avisos[c] = aviso }
        }
        controles = lista.toList()
        publicarAMelhor()
    }

    /** Caso 10: cancela a publicação em curso e escolhe de novo, em segundo plano. */
    fun publicarAMelhor() {
        publicacao?.cancel()
        val lista = controles
        publicacao = escopo.launch {
            val candidatos = lista.filter { runCatching { it.packageName }.getOrNull() != RegrasDaMidia.TELECOM }
                .sortedWith(
                    compareByDescending<MediaController> { tocando(it) }.thenByDescending { metadadosUteis(it) }
                )
            val escolhido = candidatos.firstOrNull { metadadosUteis(it) || runCatching { it.playbackState }.getOrNull() != null }
            val candidato = escolhido?.let { extrair(it) }
            val pronta = if (candidato != null) nucleo.sessaoAAPronta() else false
            principal.post { nucleo.publicarSessao(candidato, pronta) }
        }
    }

    private fun tocando(c: MediaController): Boolean =
        runCatching { c.playbackState?.state == PlaybackState.STATE_PLAYING }.getOrDefault(false)

    /** Metadados úteis (caso 10). */
    private fun metadadosUteis(c: MediaController): Boolean = runCatching {
        val m = c.metadata ?: return@runCatching false
        val d = m.description
        !m.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrEmpty() ||
            !m.getString(MediaMetadata.METADATA_KEY_ARTIST).isNullOrEmpty() ||
            m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) != null ||
            m.getBitmap(MediaMetadata.METADATA_KEY_ART) != null ||
            m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) != null ||
            !m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI).isNullOrEmpty() ||
            !m.getString(MediaMetadata.METADATA_KEY_ART_URI).isNullOrEmpty() ||
            !m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).isNullOrEmpty() ||
            d?.iconBitmap != null || d?.iconUri != null || d?.title != null
    }.getOrDefault(false)

    /** Caso 10.3. */
    private fun extrair(c: MediaController): CandidatoDeSessao? = runCatching {
        val m = c.metadata
        val d = m?.description
        val estado = c.playbackState
        val titulo = m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: d?.title?.toString()
        val artista = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: d?.subtitle?.toString()
        val album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM)
        val duracao = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L
        val posicao = estado?.position?.takeIf { it >= 0 } ?: 0L
        val atualizado = estado?.lastPositionUpdateTime?.takeIf { it > 0 } ?: ThreadPrincipal.agoraMs()
        val podeBuscar = duracao > 0 && ((estado?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L
        CandidatoDeSessao(
            pacote = c.packageName,
            titulo = titulo,
            artista = artista,
            album = album,
            capa = capa(m),
            tocando = estado?.state == PlaybackState.STATE_PLAYING,
            duracaoMs = duracao,
            decorridoMs = posicao,
            atualizadoEmMs = atualizado,
            podeBuscar = podeBuscar,
            temMetadados = metadadosUteis(c),
        )
    }.getOrNull()

    /** Caso 14. */
    private fun capa(m: MediaMetadata?): Bitmap? {
        if (m == null) return null
        val d = m.description
        val bitmap = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: d?.iconBitmap
        if (bitmap != null) return CapaDaMidia.normalizar(bitmap)
        val uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)?.takeIf { it.isNotEmpty() }
            ?: m.getString(MediaMetadata.METADATA_KEY_ART_URI)?.takeIf { it.isNotEmpty() }
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)?.takeIf { it.isNotEmpty() }
            ?: d?.iconUri?.toString()
        return CapaDaMidia.deUri(contexto.contentResolver, uri)
    }

    // ── Para os comandos ────────────────────────────────────────────────────────────────

    /** O controle do [dono] que aceita [acao]; senão o primeiro que aceite. */
    fun controleCom(acao: Long, dono: String?): MediaController? {
        val lista = controles
        fun aceita(c: MediaController) =
            runCatching { ((c.playbackState?.actions ?: 0L) and acao) != 0L }.getOrDefault(false)
        return lista.firstOrNull { dono != null && it.packageName == dono && aceita(it) } ?: lista.firstOrNull { aceita(it) }
    }

    /** Caso 38: o primeiro controle tocando que não é telefone nem sessão da central (USB). */
    fun concorrenteTocando(): String? = controles.firstOrNull {
        val p = runCatching { it.packageName }.getOrNull()
        p != null && p != RegrasDaMidia.TELECOM && p !in RegrasDaMidia.SESSOES_DA_CENTRAL_USB && tocando(it)
    }?.packageName

    companion object {
        private const val TAG = "OpenGW.Barra.Sessoes"
    }
}
