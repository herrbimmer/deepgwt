package br.com.deepgwt.tela3d.midia

/** Estado de reprodução de uma sessão de mídia do Android, no instante da leitura. */
internal class EstadoDeReproducaoLido(
    val estado: Int,
    val posicaoMs: Long,
    /** `lastPositionUpdateTime` (relógio monotônico, ms); 0 se desconhecido. */
    val carimboDeAtualizacaoMs: Long,
    val velocidade: Float,
    /** Máscara de ações suportadas (`PlaybackState.getActions`). */
    val acoes: Long,
)

/**
 * A faixa de uma das três fontes, imutável e já com a origem da capa resolvida. Pode ser lida de
 * qualquer thread.
 */
internal class FaixaLida(
    val fonte: FonteDaFaixa,
    val pacote: String,
    val rotulo: String,
    val titulo: String,
    val artista: String,
    val album: String,
    val duracaoMs: Long,
    val tocando: Boolean,
    /** Posição informada pela fonte e o instante (relógio monotônico) em que foi informada. */
    val posicaoInformadaMs: Long,
    val instanteDaPosicaoMs: Long,
    val origemDaCapa: OrigemDaCapa,
    /** Só para sessões de mídia do Android: o estado de reprodução (posição extrapolada pelo carimbo). */
    val estadoDaSessao: EstadoDeReproducaoLido? = null,
) {
    /** G1: título, artista, capa (bitmap) ou URL remota de capa. */
    val temFaixa: Boolean
        get() = titulo.isNotEmpty() || artista.isNotEmpty() || origemDaCapa.temCapa

    fun candidata(): CandidataDeFaixa = CandidataDeFaixa(tocando, temFaixa)

    fun posicaoAoVivo(agoraMs: Long): Long {
        val estado = estadoDaSessao
        return if (estado != null) {
            PosicaoAoVivo.daSessao(
                posicaoDoEstadoMs = estado.posicaoMs,
                tocando = tocando,
                carimboDeAtualizacaoMs = estado.carimboDeAtualizacaoMs,
                agoraMs = agoraMs,
                velocidade = estado.velocidade,
                duracaoMs = duracaoMs,
            )
        } else {
            PosicaoAoVivo.informada(posicaoInformadaMs, tocando, instanteDaPosicaoMs, agoraMs, duracaoMs)
        }
    }
}
