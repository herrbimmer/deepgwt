// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.nucleo

import br.com.deepgwt.licenca.Portao
import br.com.deepgwt.licenca.Recurso

/**
 * O que o nível de licença do carro libera, para os módulos novos não dependerem do pacote da
 * licença. Delega ao `Portao` (código nosso, que muda de pacote na Fase 4).
 */
object Licenciamento {

    /** A luz ambiente (fita BLE/DMX) está liberada neste nível. */
    @JvmStatic
    fun luzAmbiente(): Boolean = Portao.permite(Recurso.LUZ_AMBIENTE)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
