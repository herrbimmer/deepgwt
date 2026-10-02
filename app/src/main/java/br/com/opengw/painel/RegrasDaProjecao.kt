package br.com.opengw.painel

/**
 * Os três estados da projeção de um celular no painel (`projetores-painel.md`, J.1).
 * [carPlay] = `carPlayInDash`, [espelho] = `projectionMirrorInDash`, [preparando] = `projectionPreparingD3`.
 */
data class EstadoDaProjecao(val carPlay: Boolean, val espelho: Boolean, val preparando: Boolean) {
    val ativa: Boolean get() = carPlay || espelho || preparando

    companion object {
        @JvmField
        val NENHUMA = EstadoDaProjecao(carPlay = false, espelho = false, preparando = false)
    }
}

/** O que foi mandado à página por último (J.4): os três estados e `projectionCardOverlayAllowed`. */
data class ProjecaoEnviada(val estado: EstadoDaProjecao, val cartaoPorCima: Boolean)

/**
 * Segurança contra falso negativo do CarPlay no display 3 (J.2, V8): por 12 s depois da última
 * leitura saudável, enquanto o CarPlay é desejado no painel ou o display 3 está sendo preparado.
 */
object SegurancaDoCarPlay {
    const val JANELA_MS = 12_000L

    /** [ultimoSaudavelMs] ≤ 0 = nunca houve. */
    @JvmStatic
    fun vale(ultimoSaudavelMs: Long, agoraMs: Long, desejadoNoPainel: Boolean, preparando: Boolean): Boolean =
        ultimoSaudavelMs > 0 && agoraMs - ultimoSaudavelMs <= JANELA_MS && (desejadoNoPainel || preparando)
}

/** A decisão de entrega de um cartão novo (G.4, V9). */
data class DecisaoDoCartao(
    val projecaoAtiva: Boolean,
    val velha: Boolean,
    val appsGerenciados: Boolean,
    val limitesPodemMudar: Boolean,
    /** Empurrar o estado da projeção antes do cartão. */
    val empurrar: Boolean,
    /** Empurrar forçado (todas as chaves). */
    val forcar: Boolean,
    /** Passe de visibilidade depois do cartão. */
    val visibilidade: Boolean,
    /** Esquecer tamanhos aplicados e ressincronizar os apps do display 3. */
    val ressincronizar: Boolean,
    /** Mandar os valores de entrada do cartão (1 ou 3). */
    val entrada: Boolean,
) {
    companion object {
        /**
         * [atual] = o retrato da projeção para o cartão; [ultimoEnviado] = os três estados mandados por
         * último (null = esquecidos); [appsGerenciados] = há app conhecido no display 1 ou 3 e o
         * lançador tem apps configurados neles.
         */
        @JvmStatic
        fun decidir(
            anterior: Int,
            novo: Int,
            atual: EstadoDaProjecao,
            ultimoEnviado: EstadoDaProjecao?,
            appsGerenciados: Boolean,
        ): DecisaoDoCartao {
            val ativa = atual.ativa
            val velha = ultimoEnviado != null && (
                (ultimoEnviado.carPlay && !atual.carPlay) ||
                    (ultimoEnviado.espelho && !atual.espelho) ||
                    (ultimoEnviado.preparando && !atual.preparando)
                )
            val limites = anterior != novo && (anterior == ArbitroDoCartao.CARTAO_DO_CARRO || novo == ArbitroDoCartao.CARTAO_DO_CARRO)
            return DecisaoDoCartao(
                projecaoAtiva = ativa,
                velha = velha,
                appsGerenciados = appsGerenciados,
                limitesPodemMudar = limites,
                empurrar = ativa || velha,
                forcar = ativa,
                visibilidade = ativa || velha || (appsGerenciados && limites),
                ressincronizar = appsGerenciados && limites,
                entrada = novo == ArbitroDoCartao.CARTAO_DO_MENU || novo == ArbitroDoCartao.CARTAO_DO_AR,
            )
        }
    }
}
