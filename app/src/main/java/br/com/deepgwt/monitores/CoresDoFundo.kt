// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

/**
 * As quatro cores que a barra inferior e a luz ambiente tiram de uma capa (`extras-monitores.md`,
 * parte A). Todas ARGB opacas (alfa 0xFF).
 */
data class CoresDoFundo(val principal: Int, val secundaria: Int, val destaque: Int, val escura: Int) {

    companion object {
        /** Sem capa, capa ilegível ou sem pixel aproveitável: (31,51,58) (65,53,31) (102,227,255) (5,7,10). */
        @JvmField
        val RESERVA: CoresDoFundo = CoresDoFundo(
            principal = 0xFF1F333A.toInt(),
            secundaria = 0xFF41351F.toInt(),
            destaque = 0xFF66E3FF.toInt(),
            escura = 0xFF05070A.toInt(),
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
