package br.com.deepgwt.tela3d.midia

/** O "tocando agora" que o CarPlay entrega (transação 1 do retorno). */
internal class AtualizacaoDoCarPlay(
    val titulo: String,
    val artista: String,
    val duracaoMs: Long,
    val caminhoDaArte: String,
    /** 1 = tocando; os outros valores contam como não tocando. */
    val status: Int,
    val decorridoMs: Long,
    val imagem: ByteArray?,
) {
    val tocando: Boolean get() = status == STATUS_TOCANDO

    /** I3: válida se há título, artista, caminho de arte (aparados), bytes de imagem ou se está tocando. */
    val valida: Boolean
        get() = titulo.isNotBlank() || artista.isNotBlank() || caminhoDaArte.isNotBlank() ||
            (imagem != null && imagem.isNotEmpty()) || tocando

    /** Chave da capa: "carplay|título|artista|tamanho dos bytes|caminho". */
    val chaveDaCapa: String
        get() = ChaveDeCapa.montar("carplay", titulo.trim(), artista.trim(), (imagem?.size ?: 0).toString(), caminhoDaArte.trim())

    companion object {
        const val STATUS_TOCANDO = 1
    }
}

/** Formato binário do retorno do CarPlay (caso I2). Puro. */
internal object DecodificadorDoCarPlay {
    /**
     * Lê a transação 1. Nulo = atualização vazia (campo "presente" 0). Se a leitura da imagem falha o
     * texto continua valendo, sem a imagem.
     */
    fun ler(leitor: LeitorDeDados): AtualizacaoDoCarPlay? {
        if (leitor.lerInt() == 0) return null
        val titulo = leitor.lerString().orEmpty()
        val artista = leitor.lerString().orEmpty()
        val duracao = leitor.lerLong()
        val caminho = leitor.lerString().orEmpty()
        val status = leitor.lerInt()
        val decorrido = leitor.lerLong()
        val imagem = try {
            leitor.lerInt()
            leitor.lerInt()
            val tamanho = leitor.lerInt()
            if (tamanho > 0) leitor.lerBytes() else null
        } catch (_: RuntimeException) {
            null
        }
        return AtualizacaoDoCarPlay(
            titulo = titulo,
            artista = artista,
            duracaoMs = duracao.coerceAtLeast(0L),
            caminhoDaArte = caminho,
            status = status,
            decorridoMs = decorrido.coerceAtLeast(0L),
            imagem = imagem,
        )
    }
}

/** Decisão do período de tolerância das atualizações vazias do CarPlay (caso I4). */
internal sealed class DecisaoDaVazia {
    /** Limpar a faixa já. */
    data object LimparAgora : DecisaoDaVazia()

    /** Manter a faixa e verificar de novo daqui a [restanteMs]. */
    data class Aguardar(val restanteMs: Long) : DecisaoDaVazia()
}

/**
 * O CarPlay manda atualizações vazias entre faixas: não limpar se a última válida foi há menos de
 * [toleranciaMs]; e, se a vazia chegou dentro da tolerância, agendar uma verificação ao fim dela
 * (o defeito herdado deixava a faixa fantasma para sempre). Puro; tempos em ms.
 */
internal class ToleranciaDeAtualizacaoVazia(private val toleranciaMs: Long = 4_000L) {
    private var ultimaValida: Long? = null

    fun aoReceberValida(agora: Long) {
        ultimaValida = agora
    }

    fun aoReceberVazia(agora: Long): DecisaoDaVazia {
        val ultima = ultimaValida ?: return DecisaoDaVazia.LimparAgora
        val decorrido = agora - ultima
        return if (decorrido < toleranciaMs) DecisaoDaVazia.Aguardar(toleranciaMs - decorrido) else DecisaoDaVazia.LimparAgora
    }

    /** Ao fim da espera: limpar se nenhuma válida chegou depois da vazia que agendou a verificação. */
    fun deveLimparAoFimDaEspera(agora: Long): Boolean {
        val ultima = ultimaValida ?: return true
        return agora - ultima >= toleranciaMs
    }

    fun esquecer() {
        ultimaValida = null
    }
}

/** Códigos e descritores do serviço do CarPlay. */
internal object ProtocoloDoCarPlay {
    const val PACOTE = "com.ts.carplay"
    const val CLASSE_DO_SERVICO = "com.ts.carplay.CarPlayService"
    const val ACAO_DO_SERVICO = "com.ts.carplay.action.CarPlayService"
    const val DESCRITOR_DO_SERVICO = "com.ts.carplay.common.aidl.ICarPlayService"
    const val DESCRITOR_DO_RETORNO = "com.ts.carplay.common.aidl.INowPlayingUpdateCallback"

    const val TRANSACAO_REGISTRAR_RETORNO = 9
    const val TRANSACAO_CANCELAR_RETORNO = 10
    const val TRANSACAO_TOQUE_HID = 25
    const val TRANSACAO_ESTADO_DO_ENLACE = 29
    const val TRANSACAO_DO_RETORNO = 1

    const val HID_PRESSIONAR = 0
    const val HID_SOLTAR = 1
    const val PRESSAO_HID_MS = 90L
}
