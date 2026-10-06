// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Os três cartões do painel expandido da barra inferior (seção 6.F). [chaveGravada] é o que vai em
 * `dashboardCardOrder` (ex. `media,dynamics,hvac`): mudar uma chave desarruma a ordem de quem já
 * gravou.
 */
enum class CartaoDoPainelExpandido(val chaveGravada: String) {
    MIDIA("media"),
    DINAMICA("dynamics"),
    CLIMA("hvac");

    companion object {
        /**
         * Sem espaços nas pontas e em minúsculas pela regra do inglês; outro texto → nulo
         * (inclusive "Média", com acento).
         */
        fun daChave(texto: String): CartaoDoPainelExpandido? {
            val chave = texto.trim().lowercase(Locale.US)
            return entries.firstOrNull { it.chaveGravada == chave }
        }
    }
}

/** A ordem dos cartões, gravada e movida pelo motorista. Funções puras (vetores V3). */
object OrdemDoPainelExpandido {
    val PADRAO: List<CartaoDoPainelExpandido> = listOf(
        CartaoDoPainelExpandido.MIDIA,
        CartaoDoPainelExpandido.DINAMICA,
        CartaoDoPainelExpandido.CLIMA,
    )

    /**
     * Nulo → [PADRAO]. Senão: pedaços separados por vírgula, traduzidos; desconhecidos descartados;
     * repetidos tirados (fica o primeiro); os que faltam entram no fim, na ordem do [PADRAO]; ficam
     * os três primeiros.
     */
    fun normalizar(gravado: String?): List<CartaoDoPainelExpandido> {
        if (gravado == null) return PADRAO
        val lidos = gravado.split(',').mapNotNull { CartaoDoPainelExpandido.daChave(it) }.distinct()
        return (lidos + PADRAO.filter { it !in lidos }).take(PADRAO.size)
    }

    /** Sempre os três, chaves juntadas por vírgula, sem espaços. */
    fun paraGravar(ordem: List<CartaoDoPainelExpandido>): String =
        normalizar(emTexto(ordem)).joinToString(",") { it.chaveGravada }

    /**
     * Anda [direcao] casas (negativo = para a esquerda), parando nas pontas. Devolve sempre a ordem
     * normalizada.
     */
    fun mover(
        ordem: List<CartaoDoPainelExpandido>,
        cartao: CartaoDoPainelExpandido,
        direcao: Int,
    ): List<CartaoDoPainelExpandido> {
        val normal = normalizar(emTexto(ordem))
        if (direcao == 0) return normal
        val atual = normal.indexOf(cartao)
        if (atual < 0) return normal
        // Em Long: uma direção enorme não pode dar a volta no Int.
        val destino = (atual.toLong() + direcao).coerceIn(0L, (normal.size - 1).toLong()).toInt()
        if (destino == atual) return normal
        return normal.toMutableList().apply {
            removeAt(atual)
            add(destino, cartao)
        }
    }

    private fun emTexto(ordem: List<CartaoDoPainelExpandido>): String =
        ordem.joinToString(",") { it.chaveGravada }
}

/** Os textos de combustível e autonomia do painel expandido. Funções puras (vetores V4). */
object FormatosDoPainelExpandido {
    /** Capacidade usada para todas as versões (a confirmar no carro do dono). */
    const val CAPACIDADE_DO_TANQUE_LITROS: Float = 55f

    private const val SEM_VALOR = "--"

    /**
     * Percentual do tanque → litros, com 1 casa e ponto decimal (ex. `"27.5 L"`). Conta em ponto
     * flutuante de 32 bits, na ordem percentual × capacidade ÷ 100; meio para cima. Texto que não é
     * número, NaN, infinito, abaixo de 0 ou acima de 100 → `"--"`. `"-0"` dá `"-0.0 L"` (D12; mantido).
     */
    fun litros(percentual: String): String {
        val p = percentual.trim().toFloatOrNull() ?: return SEM_VALOR
        if (p.isNaN() || p.isInfinite() || p < 0f || p > 100f) return SEM_VALOR
        val litros = p * CAPACIDADE_DO_TANQUE_LITROS / 100f
        return String.format(Locale.US, "%.1f", litros) + " L"
    }

    /**
     * Autonomia → `"<inteiro> km"`, arredondada para o inteiro mais próximo com o meio indo para
     * +∞ (419,5 → 420; −2,5 → −2). Não é número, NaN ou infinito → `"--"`.
     */
    fun distancia(valor: String): String {
        val d = valor.trim().toDoubleOrNull() ?: return SEM_VALOR
        if (d.isNaN() || d.isInfinite()) return SEM_VALOR
        return "${d.roundToInt()} km"
    }

    /** `"L - D"` com os dois; só um válido → ele; nenhum → `"--"`. */
    fun litrosEAutonomia(percentual: String, autonomia: String): String {
        val l = litros(percentual)
        val d = distancia(autonomia)
        return when {
            l != SEM_VALOR && d != SEM_VALOR -> "$l - $d"
            l != SEM_VALOR -> l
            d != SEM_VALOR -> d
            else -> SEM_VALOR
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
