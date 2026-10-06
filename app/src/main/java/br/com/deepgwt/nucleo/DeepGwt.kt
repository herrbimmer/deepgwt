// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo

import android.content.Context
import br.com.deepgwt.app.AplicacaoDeepGwt

/**
 * O contexto do app para o código do DeepGWT.
 *
 * Fachada da independência (`docs/independencia/PLANO.md`, 2.1). Lê da Application nossa
 * ([AplicacaoDeepGwt], 3.9b).
 */
object DeepGwt {

    /** Contexto da aplicação. */
    @JvmStatic
    val contexto: Context
        get() = AplicacaoDeepGwt.contexto

    /**
     * Contexto do armazenamento protegido do aparelho: legível antes do primeiro desbloqueio,
     * que é o caso da central ao ligar o carro. É onde ficam as preferências do app.
     */
    @JvmStatic
    val contextoProtegido: Context
        get() = AplicacaoDeepGwt.contextoProtegido
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
