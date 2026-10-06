package br.com.deepgwt.tela3d.faixa

import kotlin.math.max
import kotlin.math.min

/** Onde a folha PERSONALIZAR fica e qual o tamanho dela (px de tela, janela do Android). */
data class PosicaoDaFolha(val x: Int, val y: Int, val largura: Int, val altura: Int)

/** Números da folha PERSONALIZAR (A10), todos puros. Medidas em dp salvo dito. */
object GeometriaDaFolha {
    const val CELULA_DP = 54
    const val PREENCHIMENTO_DP = 16
    const val COLUNAS_MINIMAS = 7
    const val COLUNAS_MAXIMAS = 14
    const val ALTURA_MAXIMA_DP = 520
    const val FOLGA_LATERAL_DP = 12
    const val FOLGA_ACIMA_DO_ITEM_DP = 8
    const val BOLINHAS = 10
    const val VAO_DAS_BOLINHAS_DP = 5
    const val LADO_MINIMO_DA_BOLINHA_DP = 20
    const val LADO_MAXIMO_DA_BOLINHA_DP = 28
    const val VAO_ENTRE_COLUNAS_DP = 12

    /** `clamp(7, 14, (larguraDaTela − 24 − 32 + 6) / 54)`: divisão inteira de propósito. */
    fun colunas(larguraDaTelaDp: Int): Int =
        ((larguraDaTelaDp - 24 - 2 * PREENCHIMENTO_DP + 6) / CELULA_DP).coerceIn(COLUNAS_MINIMAS, COLUNAS_MAXIMAS)

    /** Largura da folha em dp: `54 × colunas + 32`. */
    fun larguraDp(colunas: Int): Int = CELULA_DP * colunas + 2 * PREENCHIMENTO_DP

    /** Lado da bolinha de cor: `clamp(20, 28, (larguraDaColuna − 9 × 5) / 10)`. */
    fun ladoDaBolinhaDp(larguraDaFolhaDp: Int): Int {
        val larguraDaColuna = (larguraDaFolhaDp - 2 * PREENCHIMENTO_DP - VAO_ENTRE_COLUNAS_DP) / 2
        return ((larguraDaColuna - (BOLINHAS - 1) * VAO_DAS_BOLINHAS_DP) / BOLINHAS)
            .coerceIn(LADO_MINIMO_DA_BOLINHA_DP, LADO_MAXIMO_DA_BOLINHA_DP)
    }

    /** Posição horizontal: à esquerda do item, empurrada para dentro da tela com [folgaPx] nas laterais. */
    fun posicaoX(itemEsq: Int, largura: Int, larguraDaTela: Int, folgaPx: Int): Int {
        var x = itemEsq
        if (x + largura > larguraDaTela - folgaPx) x = larguraDaTela - folgaPx - largura
        if (x < folgaPx) x = folgaPx
        return x
    }

    /**
     * Posição e tamanho da folha. A base fica sempre [folgaAcimaPx] acima do topo do item; a altura é
     * `min(520, espaço livre acima do item, altura do conteúdo)` e o resto rola dentro da folha (D1).
     */
    fun calcular(
        larguraDaTela: Int,
        itemEsq: Int,
        itemTopo: Int,
        larguraPx: Int,
        alturaDoConteudoPx: Int,
        alturaMaximaPx: Int,
        folgaLateralPx: Int,
        folgaAcimaPx: Int,
    ): PosicaoDaFolha {
        val livreAcima = max(0, itemTopo - folgaAcimaPx)
        val altura = max(0, min(min(alturaMaximaPx, livreAcima), alturaDoConteudoPx))
        return PosicaoDaFolha(
            x = posicaoX(itemEsq, larguraPx, larguraDaTela, folgaLateralPx),
            y = itemTopo - folgaAcimaPx - altura,
            largura = larguraPx,
            altura = altura,
        )
    }
}
