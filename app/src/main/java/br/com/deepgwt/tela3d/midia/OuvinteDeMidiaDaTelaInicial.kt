package br.com.deepgwt.tela3d.midia

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Ouvinte de notificações da tela inicial. É o serviço que o Android liga e que dá ao app o
 * direito de ler as sessões de mídia. A lógica fica nesta classe `open`; o manifesto aponta para uma
 * subclasse de uma linha com o nome gravado nas configurações dos carros (opção A): só uma linha na
 * lista de acesso a notificações.
 *
 * Toda a decisão está em [TocandoAgora]; aqui só se avisa quando o serviço conecta, desconecta e
 * quando chega uma notificação.
 */
open class OuvinteDeMidiaDaTelaInicial : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        RegistroDoOuvinte.conectou(this)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        RegistroDoOuvinte.desconectou(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // O ouvinte recebe notificações de qualquer app; só a lógica decide se interessa (caso B6).
        val pacote = sbn?.packageName ?: return
        RegistroDoOuvinte.notificacao(pacote)
    }

    override fun onDestroy() {
        RegistroDoOuvinte.desconectou(this)
        super.onDestroy()
    }

    /** Notificações ativas, ou nulo se o serviço não pode listá-las agora. */
    internal fun notificacoesAtivas(): Array<StatusBarNotification>? = try {
        activeNotifications
    } catch (_: SecurityException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * Ponto de encontro entre o serviço criado pelo sistema e o publicador criado pela tela (que podem
 * nascer em qualquer ordem). Só guarda referências; limpa ao parar (defeito 7).
 */
internal object RegistroDoOuvinte {
    @Volatile
    var ouvinte: OuvinteDeMidiaDaTelaInicial? = null
        private set

    @Volatile
    private var publicador: TocandoAgora? = null

    fun registrar(p: TocandoAgora) {
        publicador = p
    }

    fun desregistrar(p: TocandoAgora) {
        if (publicador === p) publicador = null
    }

    fun conectou(o: OuvinteDeMidiaDaTelaInicial) {
        ouvinte = o
        publicador?.aoOuvinteConectar(o)
    }

    fun desconectou(o: OuvinteDeMidiaDaTelaInicial) {
        if (ouvinte !== o) return
        ouvinte = null
        publicador?.aoOuvinteDesconectar()
    }

    fun notificacao(pacote: String) {
        publicador?.aoNotificacaoDeApp(pacote)
    }
}
