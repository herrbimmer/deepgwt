// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.nucleo.Shell

/**
 * Receptor de partida da barra (serviço §N, caso 38). O serviço principal do app também sobe a
 * barra na partida, por conta própria.
 *
 * Manifesto (pela sala suja): exportado, ações `android.intent.action.BOOT_COMPLETED` e
 * `android.intent.action.QUICKBOOT_POWERON`.
 */
open class ReceptorDePartidaDaBarra : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val acao = intent?.action ?: return
        if (acao != Intent.ACTION_BOOT_COMPLETED && acao != QUICKBOOT_POWERON) return
        val fonte = PreferenciasDaBarra.fonte() ?: return
        if (fonte.booleano(Preferencias.STEALTH_MODE_ACTIVE.chave, false)) return
        if (!fonte.booleano(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR.chave, false)) return
        if (!Settings.canDrawOverlays(context)) {
            // S-D7 corrigido: sem permissão não sobe e não encolhe os apps (não haveria barra).
            Log.w(TAG, "partida: sem permissão de sobreposição; barra não sobe")
            return
        }
        if (!Barra.mostrar(context)) Log.w(TAG, "partida: o serviço da barra não subiu")
        val altura = ContasDaBarra.alturaValida(fonte.inteiro(Preferencias.BOTTOM_BAR_HEIGHT_DP.chave, Barra.ALTURA_PADRAO_DP))
        val explicito = PreferenciasDaBarra.overscanExplicito(fonte)
        val densidade = context.resources.displayMetrics.density
        val px = ContasDaBarra.overscanEmPx(ContasDaBarra.overscanDaBarraDp(altura, explicito), densidade)
        val pendente = goAsync()
        Thread({
            try {
                val saida = if (Shell.disponivel()) Shell.executar(arrayOf("wm", "overscan", "0,0,0,$px")) else ""
                if (saida.isEmpty()) Log.i(TAG, "partida: wm overscan $px px sem saída (verifique o Shizuku)")
            } finally {
                pendente.finish()
            }
        }, "DeepGWT.Barra.Partida").start()
    }

    private companion object {
        const val TAG = "DeepGWT.Barra"
        const val QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
