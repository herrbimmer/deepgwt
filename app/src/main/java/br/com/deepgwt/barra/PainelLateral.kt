// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.Context
import android.util.Log
import br.com.deepgwt.nucleo.DeepGwt
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.nucleo.Shell

/**
 * Liga/desliga a ocultação da barra lateral nativa do carro (ventilação, home, apps...).
 *
 * O launcher nativo respeita `policy_control`, o mesmo mecanismo que o AOSP usa pro modo imersivo:
 * com `immersive.navigation=*` a barra sai de todas as telas. O usuário ainda consegue trazê-la de
 * volta com um swipe da borda -- quem a esconde de novo depois disso é o serviço da barra
 * inferior.
 *
 * Este objeto é o par que faltava: até agora nada escrevia `policy_control`, então a preferência
 * [Preferencias.HIDE_LEFT_NAV_PANE] existia sem nunca ter efeito.
 */
object PainelLateral {
    private const val TAG = "PainelLateral"
    private const val POLICY_CONTROL_KEY = "policy_control"
    private const val HIDE_VALUE = "immersive.navigation=*"

    /** Avisado com o estado aplicado (o estado da barra guarda "painel lateral oculto"). */
    @JvmStatic
    var aoMudar: ((oculto: Boolean) -> Unit)? = null

    private fun prefs() =
        DeepGwt.contextoProtegido.getSharedPreferences("deepgwt_prefs", Context.MODE_PRIVATE)

    fun isEnabled(): Boolean =
        prefs().getBoolean(Preferencias.HIDE_LEFT_NAV_PANE.chave, false)

    /**
     * Aplica o estado pedido. Roda shell via Shizuku, então **nunca chame da main thread**.
     *
     * Ao esconder, apaga `policy_control` antes de escrever: reescrever o mesmo valor é no-op e o
     * SettingsProvider não dispara broadcast nenhum, então o PolicyControl não releria a flag.
     */
    fun apply(hidden: Boolean) {
        if (!Shell.disponivel()) {
            Log.w(TAG, "Shizuku unavailable; cannot ${if (hidden) "hide" else "restore"} the nav pane")
            return
        }
        Shell.executar(arrayOf("settings", "delete", "global", POLICY_CONTROL_KEY))
        if (hidden) {
            Shell.executar(
                arrayOf("settings", "put", "global", POLICY_CONTROL_KEY, HIDE_VALUE)
            )
        }
        aoMudar?.invoke(hidden)
        Log.w(TAG, "Left nav pane hidden=$hidden")
    }

    /** Grava a preferência e aplica em seguida, numa thread de background. */
    fun setEnabled(hidden: Boolean) {
        prefs().edit().putBoolean(Preferencias.HIDE_LEFT_NAV_PANE.chave, hidden).apply()
        Thread({ apply(hidden) }, "PainelLateral").start()
    }

    /** Reaplica o que a preferência pede (boot, start do serviço). */
    fun reapply() {
        val hidden = isEnabled()
        aoMudar?.invoke(hidden)
        if (!hidden) return
        Thread({ apply(true) }, "PainelLateral-reapply").start()
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
