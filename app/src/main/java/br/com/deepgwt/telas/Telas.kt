// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

/**
 * Apps nos displays da central, para o nosso código. Desde a 3.8 delega ao lançador próprio
 * ([LancadorDeApps]).
 */
object Telas {

    /** Redimensiona os apps do display 0 depois que a faixa da barra inferior mudou. Volta na hora. */
    @JvmStatic
    fun reaplicarLimitesDoDisplay0() = LancadorDeApps.reaplicarLimitesDoDisplay0()

    /**
     * Limites `[esquerda, topo, direita, fundo]` de "tela cheia" para um app no display 0, com a
     * faixa da barra já descontada (Android Auto e CarPlay ficam com a tela inteira).
     */
    @JvmStatic
    fun limitesDeTelaCheiaNoDisplay0(pacote: String): IntArray =
        LancadorDeApps.limitesDeTelaCheiaNoDisplay0(pacote)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
