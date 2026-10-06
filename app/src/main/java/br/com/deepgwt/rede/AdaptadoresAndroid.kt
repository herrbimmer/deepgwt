// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import android.util.Log
import br.com.deepgwt.nucleo.DeepGwt
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/*
 * As ligações do módulo de rede com o Android de verdade. Nada daqui roda em teste de JVM.
 */

private const val NOME_DAS_PREFERENCIAS = "deepgwt_prefs"

/** `deepgwt_prefs` no armazenamento protegido. Escolhido a cada uso: a Application pode subir depois. */
internal class ArmazemDePreferencias(
    private val preferencias: () -> SharedPreferences = {
        DeepGwt.contextoProtegido.getSharedPreferences(NOME_DAS_PREFERENCIAS, Context.MODE_PRIVATE)
    },
) : ArmazemDeChaves {

    override fun lerBoolean(chave: String, padrao: Boolean): Boolean =
        ler(padrao) { it.getBoolean(chave, padrao) }

    override fun lerInt(chave: String, padrao: Int): Int = ler(padrao) { it.getInt(chave, padrao) }

    override fun lerLong(chave: String, padrao: Long): Long = ler(padrao) { it.getLong(chave, padrao) }

    override fun lerString(chave: String, padrao: String): String =
        ler(padrao) { it.getString(chave, padrao) ?: padrao }

    override fun gravarBoolean(chave: String, valor: Boolean) {
        preferencias().edit().putBoolean(chave, valor).apply()
    }

    override fun gravarVarios(valores: Map<String, Any>) {
        val editor = preferencias().edit()
        for ((chave, valor) in valores) {
            when (valor) {
                is Boolean -> editor.putBoolean(chave, valor)
                is Int -> editor.putInt(chave, valor)
                is Long -> editor.putLong(chave, valor)
                is String -> editor.putString(chave, valor)
                else -> throw IllegalArgumentException("tipo não suportado para $chave")
            }
        }
        editor.apply()
    }

    /** Uma chave gravada com outro tipo lança ClassCastException: vale o padrão. */
    private inline fun <T> ler(padrao: T, leitura: (SharedPreferences) -> T): T =
        try {
            leitura(preferencias())
        } catch (e: ClassCastException) {
            padrao
        }
}

internal object RegistroDaRedeNoLogcat : RegistroDaRede {
    private const val TAG = "DeepGWT.Rede"

    override fun info(mensagem: String) {
        Log.i(TAG, mensagem)
    }

    override fun aviso(mensagem: String) {
        Log.w(TAG, mensagem)
    }

    override fun erro(mensagem: String, causa: Throwable?) {
        Log.e(TAG, mensagem, causa)
    }
}

internal object RelogioDoSistema : RelogioDaRede {
    override fun agoraEmMs(): Long = System.currentTimeMillis()

    override fun monotonicoEmMs(): Long = SystemClock.elapsedRealtime()
}

/** O executor de comandos que o módulo usa: lido a cada chamada, para a troca em [LigacoesDaRede] valer. */
internal object ExecutorDoModulo : ExecutorDeShell {
    override fun executar(comando: Array<String>): String = LigacoesDaRede.executor.executar(comando)
}

/** Uma thread de fundo, daemon, para o trabalho que bloqueia em shell. */
internal fun threadUnicaDeFundo(nome: String): Executor =
    Executors.newSingleThreadExecutor { tarefa -> Thread(tarefa, nome).apply { isDaemon = true } }

internal object MedidorDeUsoAndroid : MedidorDeUso {

    override fun resumoDoSistema(inicioMs: Long, fimMs: Long): Long? {
        val gerente = DeepGwt.contexto.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return null
        @Suppress("DEPRECATION")
        val resumo = gerente.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, inicioMs, fimMs)
            ?: return null
        return resumo.rxBytes + resumo.txBytes
    }

    override fun bytesRecebidos(): Long = TrafficStats.getMobileRxBytes()

    override fun bytesEnviados(): Long = TrafficStats.getMobileTxBytes()
}

internal object RedeAndroid {

    /** A rede ativa (a default do sistema) tem transporte Wi-Fi. Qualquer exceção = false. */
    fun wifiConectado(contexto: Context): Boolean =
        try {
            val gerente = contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val rede = gerente.activeNetwork
            val capacidades = if (rede == null) null else gerente.getNetworkCapabilities(rede)
            capacidades?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        } catch (e: Exception) {
            false
        }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
