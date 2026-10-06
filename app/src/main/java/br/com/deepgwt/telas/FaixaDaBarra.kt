// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

/**
 * Contas da faixa da barra inferior no display 0, sem Android: quanto um app deve descer ou subir
 * para terminar no topo da barra. Código nosso (Alpha 6.6/7.6), separado do lançador herdado.
 */
object FaixaDaBarra {

    /**
     * O fundo de um app no display 0 ANTES do desconto da barra, a partir do que foi salvo dele.
     *
     * ## A FAIXA PRETA QUE DOBRAVA (Alpha 6.6)
     *
     * Os limites salvos em os limites guardados pelo lançador para o display 0 são os que o próprio app
     * aplicou — e esses JÁ vêm com a faixa da barra descontada (`720 - barra`). Ao devolver o app ao
     * display 0, a restauração descontava a barra DE NOVO. Cada ida ao painel e volta somava mais
     * uma faixa: o app terminava 2x, 3x a altura da barra acima da base, e o que aparecia era uma
     * faixa preta vazia em cima da barra — relatado como "a barra inferior está com altura duplicada
     * ou triplicada em algumas telas" (as dos apps que já tinham ido ao painel).
     *
     * O corte antigo (`>= 710` vira 720) só pegava um fundo a menos de 10px da base, e nenhuma barra
     * tem menos de 40dp. Agora qualquer fundo dentro da maior faixa que a barra pode ter conta como
     * tela cheia, e o desconto é aplicado uma vez só, sobre a altura inteira.
     */
    @JvmStatic
    fun fundoCruDoDisplay0(fundoSalvo: Int, alturaDoDisplay: Int, faixaMaximaPx: Int): Int =
        if (fundoSalvo >= alturaDoDisplay - faixaMaximaPx) alturaDoDisplay else fundoSalvo

    /** Folga de arredondamento na checagem da tarja: menos que isto não é faixa visível. */
    const val TOLERANCIA_DA_TARJA_PX = 8

    /**
     * O fundo que o app da frente do display 0 deveria ter, quando ele termina ACIMA do topo da
     * barra; null quando não há o que corrigir.
     *
     * ## A TARJA PRETA QUE VOLTAVA (Alpha 7.6)
     *
     * Houve três causas conhecidas de app terminando 2x, 3x a altura da barra acima da base (a
     * restauração que descontava de novo, na 6.6; o launcher que pedia 720 cravados, na 6.7; o
     * multiplicador 3x dos apps "restaurados", em 22/09). Cada uma foi achada por um caminho de
     * código, e há dezenove lugares que dão limites a um app do display 0 — mais o próprio carro,
     * que guarda os limites de uma task entre partidas. Esta checagem olha o RESULTADO, e não o
     * caminho: com a barra na tela, um app de largura inteira, colado no topo, que termina bem
     * acima de onde a barra começa, volta a terminar nela, qualquer que seja o caminho que o deixou
     * assim.
     *
     * Fica de fora o que não é "tela cheia sob a barra": janelas que não ocupam a largura inteira
     * (os slots do launcher, janelas flutuantes) ou não começam no topo, e as que terminam antes da
     * metade da tela (baixas de propósito).
     */
    @JvmStatic
    fun fundoCorrigidoContraTarjaPreta(
        limites: IntArray,
        larguraDoDisplay: Int,
        alturaDoDisplay: Int,
        fundoEsperado: Int,
        toleranciaPx: Int = TOLERANCIA_DA_TARJA_PX
    ): Int? {
        if (limites.size < 4) return null
        val esquerda = limites[0]
        val topo = limites[1]
        val direita = limites[2]
        val fundo = limites[3]
        if (topo != 0 || esquerda > 0 || direita < larguraDoDisplay) return null
        if (fundoEsperado <= 0 || fundoEsperado > alturaDoDisplay) return null
        if (fundo >= fundoEsperado - toleranciaPx) return null
        if (fundo < alturaDoDisplay / 2) return null
        return fundoEsperado
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
