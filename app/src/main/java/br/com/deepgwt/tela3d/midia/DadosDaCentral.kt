package br.com.deepgwt.tela3d.midia

/** Informação de mídia de uma fonte da central (resposta da transação 22). */
internal class InformacaoDaCentral(
    /** O campo `albumId` do objeto da central (pode ser um identificador, não o nome: decisão do dono). */
    val album: String?,
    val titulo: String?,
    val artista: String?,
    /** URL da imagem; pode ser http(s), `file:`, `content:` ou caminho de arquivo. */
    val urlDaImagem: String?,
    val duracaoMs: Long,
    /** Bitmap que veio no pacote (um `Bitmap` no aparelho); nulo se ausente ou ilegível. */
    val bitmap: Any?,
)

/** Estado de reprodução de uma fonte da central (resposta da transação 19). */
internal class EstadoDaCentral(
    val estado: Int,
    val duracaoMs: Long,
    val decorridoMs: Long,
)

/** A faixa de uma fonte da central, ainda sem a capa decodificada. */
internal class DadosDaFaixaDaCentral(
    val fonte: Int,
    val pacote: String,
    val rotulo: String,
    val titulo: String,
    val artista: String,
    val album: String,
    val duracaoMs: Long,
    val posicaoMs: Long,
    val tocando: Boolean,
    val urlRemota: String,
    /** URL/caminho da imagem a decodificar localmente (vazio se não há ou se é http). */
    val imagemLocal: String,
)

/** Formatos binários da central de mídia da fabricante (seção "Fatos do carro"). Puro. */
internal object DecodificadorDaCentral {
    const val FONTE_USB = 2
    const val FONTE_ANDROID_AUTO = 402
    const val ESTADO_TOCANDO = 3

    const val PACOTE_ANDROID_AUTO = "com.ts.androidauto.app"
    const val PACOTE_USB = "com.beantechs.mediacenter"

    private const val CAMPOS_DE_TEXTO = 14
    private const val POSICAO_DO_ALBUM = 7
    private const val POSICAO_DO_TITULO = 9
    private const val POSICAO_DO_ARTISTA = 11
    private const val POSICAO_DA_URL = 12

    /** 402 (Android Auto) se qualquer das duas for 402; senão 2 (USB) se qualquer for 2; senão nenhuma. */
    fun escolherFonte(atual: Int?, audio: Int?): Int? = when {
        atual == FONTE_ANDROID_AUTO || audio == FONTE_ANDROID_AUTO -> FONTE_ANDROID_AUTO
        atual == FONTE_USB || audio == FONTE_USB -> FONTE_USB
        else -> null
    }

    /**
     * Resposta da transação 22 (depois do cabeçalho de exceção). Nulo = "sem informação": nada
     * presente, objeto nulo ou de outra fonte.
     */
    fun informacao(leitor: LeitorDeDados, fonteDesejada: Int): InformacaoDaCentral? {
        if (leitor.lerInt() == 0) return null
        val fonteDoObjeto = leitor.lerInt()
        leitor.lerByte()
        if (leitor.lerString() != null) leitor.lerBytes()
        if (leitor.lerString() == null) return null // nome de classe do objeto
        if (fonteDoObjeto != 0 && fonteDoObjeto != fonteDesejada) return null
        var album: String? = null
        var titulo: String? = null
        var artista: String? = null
        var url: String? = null
        for (posicao in 1..CAMPOS_DE_TEXTO) {
            val texto = leitor.lerString()
            when (posicao) {
                POSICAO_DO_ALBUM -> album = texto
                POSICAO_DO_TITULO -> titulo = texto
                POSICAO_DO_ARTISTA -> artista = texto
                POSICAO_DA_URL -> url = texto
            }
        }
        val duracao = leitor.lerLong()
        // O restante (dois inteiros e um byte) é descartado; o bitmap é a última coisa lida e,
        // se a leitura falha, a faixa continua valendo sem ele.
        val bitmap = try {
            leitor.lerInt()
            leitor.lerInt()
            leitor.lerByte()
            leitor.lerBitmap()
        } catch (_: RuntimeException) {
            null
        }
        return InformacaoDaCentral(album, titulo, artista, url, duracao, bitmap)
    }

    /** Resposta da transação 19 (depois do cabeçalho de exceção). Nulo = sem estado. */
    fun estado(leitor: LeitorDeDados, fonteDesejada: Int): EstadoDaCentral? {
        if (leitor.lerInt() == 0) return null
        val fonte = leitor.lerInt()
        val estado = leitor.lerInt()
        val duracao = leitor.lerLong().coerceAtLeast(0L)
        leitor.lerLong()
        leitor.lerFloat()
        leitor.lerLong()
        leitor.lerString()
        val decorrido = leitor.lerLong().coerceAtLeast(0L)
        if (fonte != 0 && fonte != fonteDesejada) return null
        return EstadoDaCentral(estado, duracao, decorrido)
    }

    /** Monta a faixa da fonte [fonte] (402 ou 2); nula se informação e estado faltam. */
    fun montarFaixa(fonte: Int, info: InformacaoDaCentral?, estado: EstadoDaCentral?): DadosDaFaixaDaCentral? {
        if (info == null && estado == null) return null
        val androidAuto = fonte == FONTE_ANDROID_AUTO
        val duracao = when {
            estado != null && estado.duracaoMs > 0L -> estado.duracaoMs
            info != null && info.duracaoMs > 0L -> info.duracaoMs
            else -> 0L
        }
        val url = info?.urlDaImagem.orEmpty()
        return DadosDaFaixaDaCentral(
            fonte = fonte,
            pacote = if (androidAuto) PACOTE_ANDROID_AUTO else PACOTE_USB,
            rotulo = if (androidAuto) RotulosDeMidia.ANDROID_AUTO else RotulosDeMidia.USB,
            titulo = info?.titulo.orEmpty(),
            artista = info?.artista.orEmpty(),
            album = info?.album.orEmpty(),
            duracaoMs = duracao,
            posicaoMs = estado?.decorridoMs?.coerceAtLeast(0L) ?: 0L,
            tocando = estado?.estado == ESTADO_TOCANDO,
            urlRemota = if (RegrasDeCapa.ehHttp(url)) url.trim() else "",
            imagemLocal = if (RegrasDeCapa.localDaImagem(url) == RegrasDeCapa.LocalDaImagem.NENHUM) "" else url.trim(),
        )
    }

    /** Chave para não redecodificar a capa a cada 1,5 s (fonte|título|artista|álbum|URL|dimensões do bitmap). */
    fun chaveDaCapa(d: DadosDaFaixaDaCentral, dimensoesDoBitmap: Dimensoes?): String = ChaveDeCapa.montar(
        d.fonte.toString(), d.titulo, d.artista, d.album, d.imagemLocal.ifEmpty { d.urlRemota },
        dimensoesDoBitmap?.let { "${it.largura}x${it.altura}" } ?: "-",
    )
}

/** Códigos e descritores da central (seção "Fatos do carro"). */
internal object ProtocoloDaCentral {
    const val PACOTE = "com.beantechs.mediacenter"
    const val CLASSE_DO_SERVICO = "com.beantechs.mediacenter.mediacentermodel.MediaCenterService"
    const val DESCRITOR_DO_SERVICO = "com.beantechs.mediacenter.mediacentermodel.IMediaCenterService"
    const val DESCRITOR_DE_REPRODUCAO = "com.beantechs.mediacenter.mediacentermodel.IPlayService"

    const val TRANSACAO_PEDIR_REPRODUCAO = 1
    const val ARGUMENTO_PEDIR_REPRODUCAO = 2
    const val TRANSACAO_INFORMACAO = 22
    const val TRANSACAO_ESTADO = 19
    const val TRANSACAO_FONTE_ATUAL = 25
    const val TRANSACAO_FONTE_DE_AUDIO = 26
    const val TRANSACAO_PAUSAR = 27
    const val TRANSACAO_RETOMAR = 28
}
