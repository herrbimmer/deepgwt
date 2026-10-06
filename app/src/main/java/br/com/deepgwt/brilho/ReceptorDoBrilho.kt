// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Recebe os alarmes do [BrilhoAutomatico] e as mudanças de hora e de fuso do sistema
 * (`TIME_SET`, `TIMEZONE_CHANGED`) e manda reavaliar. Não exportado (manifesto).
 */
class ReceptorDoBrilho : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BrilhoAutomatico.atualizar()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
