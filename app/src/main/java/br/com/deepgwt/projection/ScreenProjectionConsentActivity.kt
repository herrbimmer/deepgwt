// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.projection

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

/**
 * Activity invisível cujo único trabalho é pegar o token de captura de tela e sumir.
 *
 * Tem de ser uma Activity porque `MediaProjectionManager` só entrega o token por
 * `startActivityForResult` — não há caminho por serviço nem por ponte. Ela é transparente, sem
 * título e fora dos recentes, então no melhor caso (appop já liberado) nada pisca na tela; no pior,
 * aparece o diálogo do sistema, a pessoa confirma uma vez e a autorização vale pelo resto da
 * sessão.
 */
class ScreenProjectionConsentActivity : Activity() {

    companion object {
        private const val TAG = "ProjectionConsent"
        private const val PEDIDO = 4711

        fun pedirToken(ctx: Context) {
            val i = Intent(ctx, ScreenProjectionConsentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            runCatching { ctx.startActivity(i) }
                .onFailure {
                    Log.e(TAG, "Não foi possível abrir o pedido de captura", it)
                    ScreenProjectionController.aoReceberToken(RESULT_CANCELED, null)
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gerente = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (gerente == null) {
            // Build AAOS sem o serviço de captura. Melhor dizer do que ficar esperando um resultado
            // que nunca vem.
            ScreenProjectionController.aoReceberToken(RESULT_CANCELED, null)
            finish()
            return
        }
        val intent = runCatching { gerente.createScreenCaptureIntent() }.getOrNull()
        if (intent == null) {
            ScreenProjectionController.aoReceberToken(RESULT_CANCELED, null)
            finish()
            return
        }
        runCatching { startActivityForResult(intent, PEDIDO) }
            .onFailure {
                Log.e(TAG, "startActivityForResult da captura falhou", it)
                ScreenProjectionController.aoReceberToken(RESULT_CANCELED, null)
                finish()
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PEDIDO) {
            ScreenProjectionController.aoReceberToken(resultCode, data)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
