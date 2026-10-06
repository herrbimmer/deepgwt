package br.com.deepgwt.tela3d.janelas

import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.Parcel

/**
 * C43: o pedido para o Android Auto da central MOSTRAR a projeção. Quem implementa
 * [ProjecaoParaJanelas.pedirExibicao] (a mídia, dona do canal binder do serviço do AA) chama [enviar].
 *
 * Sai o broadcast `ts.car.androidauto.view_state` com `state = 1` (inteiro) e, SE o binder do serviço do
 * AA já está vivo, a transação 23 unidirecional do `LinkCommand`, sem dados além do token da interface.
 * Falhas são engolidas. O CarPlay não tem pedido equivalente (nada é enviado).
 */
class PedidoDeExibicaoDoAndroidAuto(
    private val contexto: Context,
    private val binderVivo: () -> IBinder?,
) {
    fun enviar() {
        try {
            contexto.sendBroadcast(Intent(ACAO_DO_ESTADO_DA_TELA).putExtra(EXTRA_DO_ESTADO, ESTADO_EXIBIR))
        } catch (e: Exception) {
            // o resto do pedido segue
        }
        val binder = try {
            binderVivo()
        } catch (e: Exception) {
            null
        } ?: return
        val dados = Parcel.obtain()
        try {
            dados.writeInterfaceToken(DESCRITOR_DO_LINK_COMMAND)
            binder.transact(TRANSACAO_EXIBIR, dados, null, IBinder.FLAG_ONEWAY)
        } catch (e: Exception) {
            // binder morto ou recusado: engolido
        } finally {
            dados.recycle()
        }
    }

    companion object {
        const val ACAO_DO_ESTADO_DA_TELA = "ts.car.androidauto.view_state"
        const val EXTRA_DO_ESTADO = "state"
        const val ESTADO_EXIBIR = 1
        const val DESCRITOR_DO_LINK_COMMAND = "com.ts.androidauto.sdk.aidl.LinkCommand"
        const val TRANSACAO_EXIBIR = 23
    }
}
