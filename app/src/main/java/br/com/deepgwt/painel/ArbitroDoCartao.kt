// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

/**
 * Regras puras do cartão ativo do painel de fábrica (`projetores-cartao.md`, seções 2 e 3).
 *
 * Sem estado: quem guarda os tempos, a última tecla e o último cartão é o motor do painel.
 */
object ArbitroDoCartao {
    const val CARTAO_DO_CARRO = 0
    const val CARTAO_DO_MENU = 1
    const val CARTAO_DO_AR = 3
    const val JANELA_DE_NAVEGACAO_MS = 2_500L
    const val JANELA_DE_CONTROLE_DO_AR_MS = 3_500L
    const val JANELA_DE_ECO_SINTETICO_MS = 1_500L

    /** O valor de `KeyEvent.ACTION_UP`: o serviço de teclas desta central entrega o volante ao soltar. */
    private const val ACAO_SOLTOU = 1

    private val ANEL = intArrayOf(CARTAO_DO_CARRO, CARTAO_DO_MENU, CARTAO_DO_AR)

    private val SAIDA_INTENCIONAL = setOf(TeclaDoVolante.LEFT, TeclaDoVolante.RIGHT, TeclaDoVolante.HOME, TeclaDoVolante.BACK)
    private val CONTROLE_DO_AR = setOf(
        TeclaDoVolante.UP, TeclaDoVolante.DOWN, TeclaDoVolante.ENTER, TeclaDoVolante.UP_LONG,
        TeclaDoVolante.DOWN_LONG, TeclaDoVolante.ENTER_LONG, TeclaDoVolante.BACK_LONG,
    )
    private val NAVEGACAO = setOf(TeclaDoVolante.LEFT, TeclaDoVolante.RIGHT)
    private val SOLTA_A_POSSE = setOf(TeclaDoVolante.HOME, TeclaDoVolante.BACK)

    /** Só a ação "soltou" (1) conta. */
    @JvmStatic
    fun tratarAcao(acao: Int): Boolean = acao == ACAO_SOLTOU

    /** A tecla tira do app a "posse" do cartão do ar aberto sinteticamente: ←, →, HOME, BACK. */
    @JvmStatic
    fun soltaPosseDoAr(tecla: TeclaDoVolante?): Boolean = tecla != null && tecla in SAIDA_INTENCIONAL

    /** Próximo cartão na navegação sintética: anel 0 → 1 → 3 → 0 (→ avança, ← volta). */
    @JvmStatic
    fun proximoCartao(atual: Int, tecla: TeclaDoVolante): Int {
        val posicao = ANEL.indexOf(atual).let { if (it < 0) 0 else it }
        return when (tecla) {
            TeclaDoVolante.RIGHT -> ANEL[(posicao + 1) % ANEL.size]
            TeclaDoVolante.LEFT -> ANEL[(posicao + ANEL.size - 1) % ANEL.size]
            else -> ANEL[posicao]
        }
    }

    /**
     * Ignorar o cartão [novo] que o carro informou? Tempos em ms desde o evento, ou −1 se nunca
     * houve. [ultimaTecla] = a última tecla do painel tratada. As regras são avaliadas em ordem e a
     * primeira que decide encerra.
     */
    @JvmStatic
    @JvmOverloads
    fun ignorarCartaoDoCarro(
        anterior: Int,
        novo: Int,
        desdeTeclaMs: Long,
        ultimaTecla: TeclaDoVolante?,
        desdeSinteticoMs: Long,
        alvoSintetico: Int,
        protegerSaidaDoArComProjecao: Boolean = false,
        protegerSaidaDoArSintetica: Boolean = false,
    ): Boolean {
        val saidaIntencional = recente(desdeTeclaMs, JANELA_DE_NAVEGACAO_MS) && ultimaTecla in SAIDA_INTENCIONAL
        val controleDoAr = recente(desdeTeclaMs, JANELA_DE_CONTROLE_DO_AR_MS) && ultimaTecla in CONTROLE_DO_AR
        val navegacao = recente(desdeTeclaMs, JANELA_DE_NAVEGACAO_MS) && ultimaTecla in NAVEGACAO
        val soltaPosse = recente(desdeTeclaMs, JANELA_DE_NAVEGACAO_MS) && ultimaTecla in SOLTA_A_POSSE
        val saindoDoAr = anterior == CARTAO_DO_AR && (novo == CARTAO_DO_MENU || novo == CARTAO_DO_CARRO)

        // 1. O mesmo cartão: nada a fazer.
        if (novo == anterior) return true
        // 2. O app abriu o ar sinteticamente e ainda é dono: só HOME/BACK recentes liberam.
        if (protegerSaidaDoArSintetica && saindoDoAr) return !soltaPosse
        // 3. Navegação sintética recente: vale só o eco do alvo.
        if (alvoSintetico >= 0 && recente(desdeSinteticoMs, JANELA_DE_ECO_SINTETICO_MS)) return novo != alvoSintetico
        // 4. Transição do CarPlay no painel: o ar não fecha sozinho.
        if (protegerSaidaDoArComProjecao && saindoDoAr && !saidaIntencional) return true
        // 5. O motorista está mexendo no ar: o carro não tira o cartão dele.
        if (saindoDoAr && controleDoAr) return true
        // 6. O carro sobe menu ou ar sozinho por cima do cartão dele.
        if (anterior == CARTAO_DO_CARRO && (novo == CARTAO_DO_MENU || novo == CARTAO_DO_AR) && !saidaIntencional) return true
        // 7. Qualquer outro cartão que não o do carro: aceitar.
        if (novo != CARTAO_DO_CARRO) return false
        // 8. Indo para o cartão do carro a partir de um cartão que não é nosso: aceitar.
        if (anterior != CARTAO_DO_MENU && anterior != CARTAO_DO_AR) return false
        // 9. Saindo do ar por vontade do motorista: aceitar.
        if (anterior == CARTAO_DO_AR && saidaIntencional) return false
        // 10. Só aceita se houve ←/→ recente.
        return !navegacao
    }

    private fun recente(desdeMs: Long, janelaMs: Long): Boolean = desdeMs in 0..janelaMs
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
