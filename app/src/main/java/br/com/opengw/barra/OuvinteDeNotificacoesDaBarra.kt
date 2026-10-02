package br.com.opengw.barra

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * O ouvinte de notificações da barra (mídia §D). Componente Android próprio: o sistema o liga quando
 * ele está em `enabled_notification_listeners`, e é isso que libera a leitura das sessões de mídia
 * de outros apps. Só publica com a mídia de pé (o serviço da barra).
 *
 * Manifesto (pela sala suja): exportado, permissão `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`,
 * ação `android.service.notification.NotificationListenerService`.
 */
open class OuvinteDeNotificacoesDaBarra : NotificationListenerService() {

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val s = sbn ?: return
        val n = s.notification ?: return
        val pacote = s.packageName ?: return
        if (pacote == packageName) return
        val extras = n.extras ?: return
        val deMidia = n.category == Notification.CATEGORY_TRANSPORT || extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if (!deMidia) return
        val motor = MidiaDaBarra.motor ?: return
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val artista = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val album = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()
        escopo.launch {
            val capa = runCatching { capa(n) }.getOrNull()
            if (titulo.isNullOrEmpty() && artista.isNullOrEmpty() && capa == null) return@launch
            motor.notificacao(pacote, titulo, artista, album, capa)
        }
    }

    /** Caso 17: a imagem grande, o ícone grande, ou o ícone grande do "big picture". */
    @Suppress("DEPRECATION")
    private fun capa(n: Notification): Bitmap? {
        val e = n.extras
        (e.get(Notification.EXTRA_PICTURE) as? Bitmap)?.let { return CapaDaMidia.normalizar(it) }
        when (val grande = e.get(Notification.EXTRA_LARGE_ICON)) {
            is Bitmap -> return CapaDaMidia.normalizar(grande)
            is Icon -> deIcone(grande)?.let { return it }
        }
        n.getLargeIcon()?.let { deIcone(it) }?.let { return it }
        when (val big = e.get(Notification.EXTRA_LARGE_ICON_BIG)) {
            is Bitmap -> return CapaDaMidia.normalizar(big)
            is Icon -> return deIcone(big)
        }
        return null
    }

    private fun deIcone(i: Icon): Bitmap? = runCatching { CapaDaMidia.deDrawable(i.loadDrawable(this)) }.getOrNull()

    override fun onListenerConnected() {
        Log.i(TAG, "ouvinte de notificações conectado")
    }

    override fun onDestroy() {
        super.onDestroy()
        escopo.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        const val TAG = "OpenGW.Barra.Notif"
    }
}
