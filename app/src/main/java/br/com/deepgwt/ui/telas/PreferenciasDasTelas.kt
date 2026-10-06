// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import android.content.Context
import android.content.SharedPreferences
import br.com.deepgwt.nucleo.DeepGwt

/** O arquivo de preferências do app ("deepgwt_prefs"), no contexto protegido. */
internal fun preferenciasDoApp(): SharedPreferences =
    DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
