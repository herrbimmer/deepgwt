package br.com.deepgwt.tela3d.midia

/**
 * Lógica de estado do cartão de mídia nativo (caso K). O desenho é do módulo de cartões; aqui
 * ficam só as decisões sobre o payload.
 */
object RegrasDoCartaoDeMidia {
    const val TEXTO_SEM_FAIXA = "Nada tocando"
    const val SUBTITULO_SEM_FAIXA = "Escolha um app de mídia"

    const val DESCRICAO_PAUSAR = "Pausar"
    const val DESCRICAO_TOCAR = "Tocar"
    const val DESCRICAO_ANTERIOR = "Faixa anterior"
    const val DESCRICAO_PROXIMA = "Próxima faixa"
    const val DESCRICAO_DO_CARTAO = "Controles rápidos de mídia"

    const val OPACIDADE_HABILITADO = 1.0f
    const val OPACIDADE_DESABILITADO = 0.35f

    /** K2: título da faixa, ou o rótulo do app (inclusive o pedido de acesso), ou "Nada tocando". */
    fun titulo(hasTrack: Boolean, title: String, appLabel: String): String = when {
        hasTrack && title.isNotEmpty() -> title
        appLabel.isNotEmpty() -> appLabel
        else -> TEXTO_SEM_FAIXA
    }

    /** K3: artista; senão o álbum se há faixa; senão o convite a escolher um app. */
    fun subtitulo(artist: String, hasTrack: Boolean, album: String): String = when {
        artist.isNotEmpty() -> artist
        hasTrack -> album
        else -> SUBTITULO_SEM_FAIXA
    }

    /** Subtítulo vazio fica invisível (ocupa espaço, não aparece). */
    fun subtituloVisivel(texto: String): Boolean = texto.isNotEmpty()

    fun titulo(p: PayloadDeMidia): String = titulo(p.hasTrack, p.title, p.appLabel)

    fun subtitulo(p: PayloadDeMidia): String = subtitulo(p.artist, p.hasTrack, p.album)

    /** K1: os três botões só funcionam com faixa. */
    fun botoesHabilitados(p: PayloadDeMidia): Boolean = p.hasTrack

    fun opacidadeDosBotoes(p: PayloadDeMidia): Float =
        if (botoesHabilitados(p)) OPACIDADE_HABILITADO else OPACIDADE_DESABILITADO

    /** K5: descrição de acessibilidade do botão tocar/pausar. */
    fun descricaoDeTocarOuPausar(tocando: Boolean): String = if (tocando) DESCRICAO_PAUSAR else DESCRICAO_TOCAR

    /** K4: só decodifica a capa se for data URL (URL http(s) é só da página). */
    fun ehDataUrl(texto: String?): Boolean = texto != null && texto.startsWith("data:")

    /** O trecho base64 de uma data URL (o que vem depois da primeira vírgula); nulo se não é data URL. */
    fun base64DaDataUrl(texto: String?): String? {
        if (texto == null || !texto.startsWith("data:")) return null
        val virgula = texto.indexOf(',')
        if (virgula < 0) return null
        return texto.substring(virgula + 1).takeIf { it.isNotEmpty() }
    }

    /** K4: margem interna do espaço reservado, em dp (32 desde a criação). */
    const val MARGEM_DO_ESPACO_RESERVADO_DP = 32

    /** K6: de onde vem o ícone do chip do app de origem. */
    enum class OrigemDoIconeDoChip { CONFIGURADO_PELO_USUARIO, ANDROID_AUTO, CARPLAY, ICONE_DO_PAYLOAD }

    fun origemDoIconeDoChip(
        temIconeConfigurado: Boolean,
        pacote: String?,
        rotulo: String?,
    ): OrigemDoIconeDoChip = when {
        temIconeConfigurado -> OrigemDoIconeDoChip.CONFIGURADO_PELO_USUARIO
        PacotesDeProjecao.ehFonteAndroidAuto(pacote, rotulo) -> OrigemDoIconeDoChip.ANDROID_AUTO
        PacotesDeProjecao.ehFonteCarPlay(pacote, rotulo) -> OrigemDoIconeDoChip.CARPLAY
        else -> OrigemDoIconeDoChip.ICONE_DO_PAYLOAD
    }

    /** K7: o toque no cartão abre o app de origem se dá; senão roda a ação configurada. */
    fun toqueAbreOAplicativo(p: PayloadDeMidia): Boolean = p.canLaunch && p.packageName.isNotEmpty()
}

/** Política do visualizador de áudio (caso L1): quando é desejado. */
internal object DecisaoDoVisualizador {
    /** Desejado = o último payload diz `playing` OU o sistema diz que há música ativa. */
    fun desejado(payloadTocando: Boolean, musicaAtiva: Boolean): Boolean = payloadTocando || musicaAtiva
}

/** O que a política liga e desliga. */
internal fun interface AlvoDoVisualizador {
    fun desejar(sim: Boolean)
}

/**
 * Núcleo da política (L1/L2), sem Android: decide e dá as ordens. [musicaAtiva] pode lançar (vale
 * falso); sem permissão registra uma única vez e não liga.
 */
internal class NucleoDaPoliticaDoVisualizador(
    private val alvo: AlvoDoVisualizador,
    private val temPermissao: () -> Boolean,
    private val musicaAtiva: () -> Boolean,
    private val registrar: (String) -> Unit,
) {
    private var tocando = false
    private var avisouSemPermissao = false

    fun definirTocando(sim: Boolean) {
        tocando = sim
    }

    /** Reavalia e age. Devolve se o visualizador é desejado. */
    fun avaliar(): Boolean {
        val ativa = try {
            musicaAtiva()
        } catch (_: RuntimeException) {
            false
        }
        val desejado = DecisaoDoVisualizador.desejado(tocando, ativa)
        if (desejado) {
            if (temPermissao()) {
                alvo.desejar(true)
            } else if (!avisouSemPermissao) {
                avisouSemPermissao = true
                registrar("sem permissão de gravação de áudio: barras sintéticas")
            }
        } else {
            alvo.desejar(false)
        }
        return desejado
    }
}
