// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.nucleo.Preferencias
import kotlin.math.roundToInt

/**
 * Tamanho da temperatura das "Informações extras" sobre o painel de fábrica (Alpha 6.7).
 *
 * A preferência ([Preferencias.ORIGINAL_PANEL_TEMP_SCALE]) é uma ESCALA sobre o tamanho
 * que o tema Default desenha — 44px no número, 21px no rótulo "Temp." —, e não um tamanho em px:
 * é a mesma conta do `calc(44px * var(--orig-temp-scale, 1))` do tema, então 1.0 é exatamente o
 * que a Alpha 6.6 mostrava e quem nunca mexeu não vê mudança nenhuma.
 *
 * A faixa vai de 29px (0,65×, praticamente os 28px de antes da 6.6) a 88px (o dobro). O mínimo é
 * 0,65 e não 28/44 porque o instalador anda em passos de 0,05 a partir dele, e o padrão 1.0 tem de
 * cair nessa grade (CatalogoDeAjustesTest).
 */
object OriginalTempScale {
    /** Tamanho do número com a escala 1.0, em px — o `44px` do tema Default. */
    const val BASE_PX = 44

    const val PADRAO = 1.0f
    const val MINIMO = 0.65f
    const val MAXIMO = 2.0f

    /** Escala válida: dentro da faixa, e o padrão quando a preferência veio estragada. */
    fun normalizar(escala: Float): Float =
            if (escala.isNaN() || escala.isInfinite()) PADRAO else escala.coerceIn(MINIMO, MAXIMO)

    /** Tamanho do número na tela, em px, para o rótulo do ajuste. */
    fun emPx(escala: Float): Int = (BASE_PX * normalizar(escala)).roundToInt()
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
