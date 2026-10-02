package br.com.opengw.brilho

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
