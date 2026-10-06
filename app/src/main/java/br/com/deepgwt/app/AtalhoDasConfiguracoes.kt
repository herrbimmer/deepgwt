// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log

/**
 * Atividade invisível que abre as Configurações do Android e fecha (`telas-e-servico-diversos.md`
 * caso S1). Tenta, em ordem, até uma abrir sem exceção; fecha sempre.
 *
 * Declarada no manifesto; o `activity-alias` no nome antigo mantém os atalhos já gravados.
 */
class AtalhoDasConfiguracoes : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            for (intent in tentativas()) {
                try {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Log.i(TAG, "Configurações abertas por $intent")
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "não abriu por $intent; tentando a próxima", e)
                }
            }
        } finally {
            finish()
        }
    }

    private fun tentativas(): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
        componente("com.android.settings", "com.android.settings.Settings"),
        componente("com.android.car.settings", "com.android.car.settings.common.CarSettingActivity"),
        componente("com.beantechs.settings", "com.beantechs.settings.ui.activity.MainActivity"),
    )

    private fun componente(pacote: String, classe: String): Intent =
        Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pacote, classe))

    private companion object {
        const val TAG = "DeepGWT.Atalho"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
