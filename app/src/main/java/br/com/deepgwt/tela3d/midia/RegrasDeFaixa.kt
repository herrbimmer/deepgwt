package br.com.deepgwt.tela3d.midia

import kotlin.math.max
import kotlin.math.min

/** Uma candidata à vencedora. `temFaixa` = título, artista, capa (bitmap) ou URL remota de capa (G1). */
internal class CandidataDeFaixa(val tocando: Boolean, val temFaixa: Boolean)

/** Arbitragem: quem vence entre CarPlay, central e sessão (caso G). Puro. */
internal object Arbitragem {
    private const val BONUS_DE_TOCANDO = 100

    /**
     * Cada argumento é nulo se a fonte não tem faixa nenhuma (nem vazia). Só entram as que "têm
     * faixa"; pontuação = (tocando ? 100 : 0) + prioridade da fonte. Sem nenhuma com faixa, vale a
     * sessão se existir (mesmo vazia).
     */
    fun escolher(
        sessao: CandidataDeFaixa?,
        central: CandidataDeFaixa?,
        carPlay: CandidataDeFaixa?,
    ): FonteDaFaixa? {
        var melhor: FonteDaFaixa? = null
        var melhorPontos = Int.MIN_VALUE
        fun considerar(fonte: FonteDaFaixa, c: CandidataDeFaixa?) {
            if (c == null || !c.temFaixa) return
            val pontos = (if (c.tocando) BONUS_DE_TOCANDO else 0) + fonte.prioridade
            if (pontos > melhorPontos) {
                melhorPontos = pontos
                melhor = fonte
            }
        }
        considerar(FonteDaFaixa.SESSAO, sessao)
        considerar(FonteDaFaixa.CENTRAL, central)
        considerar(FonteDaFaixa.CARPLAY, carPlay)
        return melhor ?: if (sessao != null) FonteDaFaixa.SESSAO else null
    }
}

/** Posição da faixa no instante de agora (caso F2). Puro; tempos em ms. */
internal object PosicaoAoVivo {
    /**
     * Sessão com estado de reprodução: posição do estado, extrapolada pelo carimbo de atualização
     * (relógio monotônico) e pela velocidade quando está tocando.
     */
    fun daSessao(
        posicaoDoEstadoMs: Long,
        tocando: Boolean,
        carimboDeAtualizacaoMs: Long,
        agoraMs: Long,
        velocidade: Float,
        duracaoMs: Long,
    ): Long {
        var pos = posicaoDoEstadoMs
        if (tocando && carimboDeAtualizacaoMs > 0L) {
            val v = if (velocidade > 0f) velocidade else 1f
            pos = max(0L, pos + ((agoraMs - carimboDeAtualizacaoMs) * v).toLong())
        }
        if (duracaoMs > 0L) pos = min(pos, duracaoMs)
        return max(0L, pos)
    }

    /** Demais fontes: posição informada mais o tempo desde que foi informada, se está tocando. */
    fun informada(
        posicaoInformadaMs: Long,
        tocando: Boolean,
        instanteInformadoMs: Long,
        agoraMs: Long,
        duracaoMs: Long,
    ): Long {
        var pos = posicaoInformadaMs
        if (tocando) pos += max(0L, agoraMs - instanteInformadoMs)
        if (duracaoMs > 0L) pos = min(pos, duracaoMs)
        return max(0L, pos)
    }
}

/** Escolha da sessão de mídia do Android entre as ativas (caso B1). Puro. */
internal object EscolhaDeSessao {
    const val ESTADO_TOCANDO = 3
    const val ESTADO_ERRO = 7

    /**
     * [estados] = estado de reprodução de cada sessão, na ordem do sistema (nulo = sem estado).
     * Devolve o índice escolhido ou -1 se a lista é vazia.
     */
    fun escolher(estados: List<Int?>): Int {
        if (estados.isEmpty()) return -1
        // 2. A primeira tocando vence (uma sessão tocando nunca está em erro).
        val tocando = estados.indexOfFirst { it == ESTADO_TOCANDO }
        if (tocando >= 0) return tocando
        // 3. A primeira não ignorada (1. ignora as em erro).
        val naoIgnorada = estados.indexOfFirst { it != ESTADO_ERRO }
        if (naoIgnorada >= 0) return naoIgnorada
        // 4. Todas em erro: a primeira.
        return 0
    }
}
