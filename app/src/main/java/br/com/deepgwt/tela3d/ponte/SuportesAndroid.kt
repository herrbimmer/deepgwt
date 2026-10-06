package br.com.deepgwt.tela3d.ponte

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.text.format.DateFormat
import android.util.Log
import br.com.deepgwt.broadcastReceivers.ViewerBridgeReceiver
import java.util.Locale
import java.util.TimeZone

/** As implementações Android das peças de [SuportesDaPonte.kt]. */

class PreferenciasAndroid(contexto: Context) : PreferenciasDoLancador {
    private val prefs = contexto.applicationContext.getSharedPreferences(
        PreferenciasDoLancador.ARQUIVO, Context.MODE_PRIVATE,
    )

    override fun texto(chave: String, padrao: String): String = prefs.getString(chave, padrao) ?: padrao
    override fun inteiro(chave: String, padrao: Int): Int = prefs.getInt(chave, padrao)
    override fun gravarTexto(chave: String, valor: String) {
        prefs.edit().putString(chave, valor).apply()
    }

    override fun gravarInteiro(chave: String, valor: Int) {
        prefs.edit().putInt(chave, valor).apply()
    }
}

class DiarioAndroid(private val etiqueta: String) : DiarioDaPonte {
    override fun info(mensagem: String) {
        Log.i(etiqueta, mensagem)
    }

    override fun aviso(mensagem: String) {
        Log.w(etiqueta, mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        if (causa == null) Log.e(etiqueta, mensagem) else Log.e(etiqueta, mensagem, causa)
    }
}

/**
 * Posta na thread principal da Activity. Depois de `onDestroy` ninguém mais roda: a Activity
 * marca [destruida] e o trabalho que já estava na fila também é descartado.
 */
class ExecutorNaPrincipal(
    private val handler: Handler,
    private val destruida: () -> Boolean,
) : ExecutorDaUi {
    override fun postar(tarefa: () -> Unit) {
        if (destruida()) return
        handler.post {
            if (!destruida()) tarefa()
        }
    }
}

class AgendadorDoHandler(private val handler: Handler) : AgendadorDeTarefas {
    override fun postarComAtraso(tarefa: Runnable, atrasoMs: Long) {
        handler.postDelayed(tarefa, atrasoMs)
    }

    override fun cancelar(tarefa: Runnable) {
        handler.removeCallbacks(tarefa)
    }
}

/** Os broadcasts ao carro: sempre com `setPackage` do próprio app (destino: `ViewerBridgeReceiver`). */
class EnviadorAndroid(private val contexto: Context) : EnviadorDeComandos {
    private fun intent(acao: String): Intent = Intent(acao).setPackage(contexto.packageName)

    override fun pedirRetratoDeTelemetria() {
        contexto.sendBroadcast(
            intent(ViewerBridgeReceiver.ACTION_REQUEST_SNAPSHOT).putExtra("requester", contexto.packageName),
        )
    }

    override fun enviarComando(comando: String, valor: String) {
        val envio = intent(ViewerBridgeReceiver.ACTION_VEHICLE_COMMAND).putExtra("command", comando)
        if (valor.isNotEmpty()) envio.putExtra("value", valor)
        contexto.sendBroadcast(envio)
    }

    override fun escreverChave(chave: String, valor: String) {
        contexto.sendBroadcast(
            intent(ViewerBridgeReceiver.ACTION_UPDATE_CAR_DATA).putExtra("key", chave).putExtra("value", valor),
        )
    }
}

class AmbienteDoRelogioAndroid(private val contexto: Context) : AmbienteDoRelogio {
    override fun etiquetaDoIdioma(): String? = Locale.getDefault().toLanguageTag()
    override fun idDoFuso(): String? = TimeZone.getDefault().id
    override fun sistemaEm24h(): Boolean = DateFormat.is24HourFormat(contexto)
}

class MusicaAtivaAndroid(private val contexto: Context) : MusicaAtiva {
    override fun ativa(): Boolean =
        (contexto.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive ?: false
}

/** Os extras de um `Intent`, para o [RoteadorDeIntents]. */
class ExtrasDeIntent(private val intent: Intent) : ExtrasDeEntrada {
    override fun texto(chave: String): String? = intent.getStringExtra(chave)
    override fun inteiro(chave: String, padrao: Int): Int = intent.getIntExtra(chave, padrao)
    override fun booleano(chave: String): Boolean = intent.getBooleanExtra(chave, false)
    override fun remover(chave: String) {
        intent.removeExtra(chave)
    }
}
