package br.com.deepgwt.tela3d.midia

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.SystemClock

/**
 * Fonte 1: as sessões de mídia do Android (caso B), lidas por meio do ouvinte de notificações.
 * Escolhe a sessão (B1), a observa e entrega a faixa lida. Tudo na thread principal.
 */
internal class ObservadorDeSessoes(
    private val contexto: Context,
    private val principal: Handler,
    private val ouvinte: OuvinteDeMidiaDaTelaInicial,
    capas: GerenteDeCapas,
    /** A faixa da sessão escolhida mudou (nulo = nenhuma sessão). */
    private val aoMudar: (FaixaLida?) -> Unit,
) {
    private class Registro(val controle: MediaController, val aviso: MediaController.Callback)

    private val resolvedor = ResolvedorDeCapaDaSessao(
        contexto = contexto,
        principal = principal,
        capas = capas,
        notificacoesAtivas = { ouvinte.notificacoesAtivas() },
        aoDecodificarUri = { relerAgora() },
    )

    private val registros = HashMap<MediaSession.Token, Registro>()
    private var controles: List<MediaController> = emptyList()
    private var escolhido: MediaController? = null
    private var gerenciador: MediaSessionManager? = null
    private var escutandoLista = false

    /** Pacote da sessão escolhida, ou nulo. */
    val pacoteEscolhido: String? get() = escolhido?.packageName

    private val aoMudarLista = MediaSessionManager.OnActiveSessionsChangedListener { lista ->
        atualizarControles(lista.orEmpty())
    }

    private fun componente(): ComponentName = ComponentName(contexto.packageName, ouvinte.javaClass.name)

    /**
     * (Re)inicia a observação: lê as sessões ativas, escolhe e entrega a faixa. Idempotente.
     * Devolve falso se o sistema negou o acesso às sessões (SecurityException): o serviço pode estar
     * conectado sem a liberação, e o usuário precisa liberar o acesso.
     */
    fun iniciar(): Boolean {
        return try {
            val g = gerenciador ?: contexto.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            gerenciador = g
            val c = componente()
            val ativas = g.getActiveSessions(c)
            if (!escutandoLista) {
                g.addOnActiveSessionsChangedListener(aoMudarLista, c, principal)
                escutandoLista = true
            }
            atualizarControles(ativas)
            true
        } catch (e: SecurityException) {
            Diario.aviso("sessões de mídia recusadas: ${e.message}")
            parar()
            false
        } catch (e: RuntimeException) {
            Diario.aviso("falha ao observar as sessões de mídia", e)
            true
        }
    }

    /** Para de observar e esquece as sessões (não entrega nada). */
    fun parar() {
        if (escutandoLista) {
            try {
                gerenciador?.removeOnActiveSessionsChangedListener(aoMudarLista)
            } catch (_: RuntimeException) {
            }
            escutandoLista = false
        }
        for (registro in registros.values) {
            try {
                registro.controle.unregisterCallback(registro.aviso)
            } catch (_: RuntimeException) {
            }
        }
        registros.clear()
        controles = emptyList()
        escolhido = null
    }

    /** Relê a sessão escolhida e entrega a faixa de novo (mesmo se igual: quem publica deduplica). */
    fun relerAgora() {
        reavaliar()
    }

    /**
     * Manda o comando às sessões (casos C4 e C5). Devolve nulo se foi enviado à sessão, ou o código da
     * tecla de mídia global a usar no lugar (sem sessão, em erro, ação não suportada ou exceção).
     */
    fun executar(comando: ComandoDeMidia): Int? {
        val sessao = escolhido
        val estado = try {
            sessao?.playbackState
        } catch (_: RuntimeException) {
            null
        }
        val plano = ComandoDoOuvinte.planejar(comando, sessao != null, estado?.state, estado?.actions ?: 0L)
        val acao = plano.acao
        if (sessao == null || acao == null) return plano.teclaDeFalha
        return try {
            val t = sessao.transportControls
            when (acao) {
                AcaoDeSessao.PROXIMA -> t.skipToNext()
                AcaoDeSessao.ANTERIOR -> t.skipToPrevious()
                AcaoDeSessao.TOCAR -> t.play()
                AcaoDeSessao.PAUSAR -> t.pause()
            }
            null
        } catch (e: RuntimeException) {
            Diario.aviso("comando à sessão falhou", e)
            plano.teclaDeFalha
        }
    }

    // --- sessões --------------------------------------------------------------------------------

    /** Compara por token (os objetos que o sistema entrega são novos a cada aviso). */
    private fun atualizarControles(lista: List<MediaController>) {
        val novos = HashMap<MediaSession.Token, MediaController>()
        for (c in lista) novos[c.sessionToken] = c
        val saindo = registros.keys.filter { it !in novos }
        for (token in saindo) {
            registros.remove(token)?.let {
                try {
                    it.controle.unregisterCallback(it.aviso)
                } catch (_: RuntimeException) {
                }
            }
        }
        for ((token, c) in novos) {
            if (token in registros) continue
            val aviso = AvisoDaSessao(token)
            try {
                c.registerCallback(aviso, principal)
                registros[token] = Registro(c, aviso)
            } catch (e: RuntimeException) {
                Diario.aviso("não foi possível observar ${c.packageName}", e)
            }
        }
        controles = lista
        reavaliar()
    }

    /** Reescolhe a sessão (B1) e entrega a faixa lida. */
    private fun reavaliar() {
        val lista = controles
        val indice = EscolhaDeSessao.escolher(lista.map { estadoDe(it) })
        escolhido = lista.getOrNull(indice)
        val atual = escolhido
        val faixa = if (atual == null) null else try {
            ler(atual)
        } catch (e: RuntimeException) {
            Diario.aviso("leitura da sessão falhou", e)
            null
        }
        aoMudar(faixa)
    }

    private fun estadoDe(c: MediaController): Int? = try {
        c.playbackState?.state
    } catch (_: RuntimeException) {
        null
    }

    /** B2: a faixa de sessão com metadados, estado e capa. */
    private fun ler(c: MediaController): FaixaLida {
        val md = c.metadata
        val st = c.playbackState
        val pacote = c.packageName.orEmpty()
        val titulo = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artista = md?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val duracao = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val estado = st?.let {
            EstadoDeReproducaoLido(it.state, it.position, it.lastPositionUpdateTime, it.playbackSpeed, it.actions)
        }
        return FaixaLida(
            fonte = FonteDaFaixa.SESSAO,
            pacote = pacote,
            rotulo = "", // o rótulo do app instalado é resolvido por quem publica (com cache)
            titulo = titulo,
            artista = artista,
            album = album,
            duracaoMs = duracao,
            tocando = st?.state == PlaybackState.STATE_PLAYING,
            posicaoInformadaMs = (st?.position ?: 0L).coerceAtLeast(0L),
            instanteDaPosicaoMs = SystemClock.elapsedRealtime(),
            origemDaCapa = resolvedor.resolver(pacote, titulo, artista, md),
            estadoDaSessao = estado,
        )
    }

    private inner class AvisoDaSessao(private val token: MediaSession.Token) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            // Mudou o estado de alguma sessão: pode ter mudado quem é a melhor (B1).
            reavaliar()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (escolhido?.sessionToken == token) reavaliar()
        }
    }
}
