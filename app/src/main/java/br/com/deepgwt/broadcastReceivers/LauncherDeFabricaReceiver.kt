// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.broadcastReceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import br.com.deepgwt.managers.HomeLauncherController

/**
 * A volta de emergência à tela de fábrica, pelo cabo (Alpha 7.8).
 *
 * Com o launcher 3D escolhido, o DeepGWT o devolve quando a tela de fábrica abre como início
 * (GuardaDoLauncher3D). Se o launcher 3D estiver com defeito e a central não mostrar nada útil,
 * o computador ligado por ADB desfaz tudo — a escolha, a guarda e a preferência do Android:
 *
 * ```
 * adb shell am broadcast -a br.com.deepgwt.VOLTAR_TELA_DE_FABRICA -p br.com.deepgwt
 * ```
 *
 * Exportado de propósito e sem permissão: o único efeito possível é VOLTAR ao que veio de fábrica.
 */
class LauncherDeFabricaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACAO) return
        val pendente = goAsync()
        Thread({
            try {
                HomeLauncherController.voltarParaFabrica(context.applicationContext)
                    .onSuccess { Log.w(TAG, "tela de fábrica restaurada pelo broadcast") }
                    .onFailure { Log.e(TAG, "broadcast pediu a tela de fábrica, mas: ${it.message}") }
            } finally {
                pendente.finish()
            }
        }, "VoltarTelaDeFabrica").start()
    }

    companion object {
        const val ACAO = "br.com.deepgwt.VOLTAR_TELA_DE_FABRICA"
        private const val TAG = "LauncherDeFabrica"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
