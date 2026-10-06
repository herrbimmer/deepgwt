// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.deepgwt.nucleo.Preferencias

/**
 * Receptor do recuo global da barra (parte 2, seção H). Exportado e protegido por
 * `android.permission.WRITE_SECURE_SETTINGS` no manifesto (só o shell do ADB manda):
 *
 * `adb shell am broadcast -a br.com.deepgwt.ACTION_UPDATE_OVERSCAN --ei value 60`
 *
 * Ainda sem entrada no manifesto: a sala suja a põe na troca.
 */
class ReceptorDoRecuo : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACAO) return
        val valor = valorDoRecuo(if (intent.hasExtra(EXTRA)) intent.getIntExtra(EXTRA, PADRAO) else null)
        val nucleo = Lancador.nucleo
        nucleo.semFalhar("recuo global", Unit) {
            nucleo.amb.prefs.gravar(mapOf(Preferencias.PERSISTENT_BOTTOM_BAR_OVERSCAN to valor))
            nucleo.amb.registro.info("recuo global da barra: $valor dp")
            nucleo.telas.reaplicarLimitesDoDisplay0()
        }
    }

    companion object {
        /** Texto fixo: é o que se manda pelo ADB. */
        const val ACAO = "br.com.deepgwt.ACTION_UPDATE_OVERSCAN"
        const val EXTRA = "value"
        private const val PADRAO = 60

        /** H1: extra ausente → 60; limitado a 0..240. */
        @JvmStatic
        fun valorDoRecuo(extra: Int?): Int = (extra ?: PADRAO).coerceIn(0, 240)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
