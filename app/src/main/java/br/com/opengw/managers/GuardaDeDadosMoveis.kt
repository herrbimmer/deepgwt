package br.com.opengw.managers

import br.com.opengw.rede.ControleDeDadosMoveis
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.veiculo.sistema.DadosMoveis

/**
 * A guarda de dados móveis (extra herdado, 3.10), tirada de dentro do motor antigo na troca do
 * 3.3b–e e ligada ao motor novo como [DadosMoveis]. Mesmo comportamento: ao iniciar, reaplica as
 * regras e o DataTrack; com o controle ligado, reavalia quando o Wi-Fi conecta/cai, quando o
 * hotspot muda e a cada 15 s. Fica até o dono decidir o destino dos dados móveis (PLANO, 3.10).
 */
object GuardaDeDadosMoveis : DadosMoveis {

    private const val TAG = "GuardaDeDadosMoveis"

    /** 15 s: pega consumo, Wi-Fi e projeção. */
    private const val INTERVALO_MS = 15_000L

    private val trava = Any()
    private var linha: Handler? = null
    private var laco: Runnable? = null
    private var vigiaDoWifi: ConnectivityManager.NetworkCallback? = null
    private var receptorDoHotspot: BroadcastReceiver? = null

    private fun contexto(): Context = OpenGw.contexto

    private fun linha(): Handler = synchronized(trava) {
        linha ?: Handler(HandlerThread("OpenGW-DadosMoveis").apply { start() }.looper).also { linha = it }
    }

    override fun iniciar() {
        parar()
        val ctx = contexto()
        try {
            // As duas reconciliações são serializadas no worker do ControleDeDadosMoveis. Com os
            // interruptores desligados e sem estado do app, ambas voltam sem rodar shell.
            ControleDeDadosMoveis.reavaliar(ctx)
            ControleDeDadosMoveis.reaplicarTelemetria()
            if (!ControleDeDadosMoveis.controleLigado()) return
            ControleDeDadosMoveis.garantirPermissaoDeUso(ctx)
            ouvirWifi(ctx)
            ouvirHotspot(ctx)
        } catch (t: Throwable) {
            Log.w(TAG, "iniciar: ${t.message}")
        }
        val h = linha()
        synchronized(trava) {
            if (laco != null) return
            val tarefa = object : Runnable {
                override fun run() {
                    try {
                        ControleDeDadosMoveis.reavaliar(contexto())
                    } catch (_: Throwable) {
                    } finally {
                        synchronized(trava) { if (laco === this) h.postDelayed(this, INTERVALO_MS) }
                    }
                }
            }
            laco = tarefa
            h.postDelayed(tarefa, INTERVALO_MS)
        }
    }

    override fun parar() {
        val ctx = contexto()
        val (h, tarefa, vigia, receptor) = synchronized(trava) {
            val antigo = Quatro(linha, laco, vigiaDoWifi, receptorDoHotspot)
            laco = null
            vigiaDoWifi = null
            receptorDoHotspot = null
            antigo
        }
        if (h != null && tarefa != null) h.removeCallbacks(tarefa)
        if (vigia != null) {
            try {
                (ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(vigia)
            } catch (_: IllegalArgumentException) {
                // Já removido.
            } catch (t: Throwable) {
                Log.w(TAG, "parar (Wi-Fi): ${t.message}")
            }
        }
        if (receptor != null) {
            try {
                ctx.unregisterReceiver(receptor)
            } catch (_: IllegalArgumentException) {
                // Já removido.
            } catch (t: Throwable) {
                Log.w(TAG, "parar (hotspot): ${t.message}")
            }
        }
    }

    override fun aoMudarControle(ligado: Boolean) {
        if (ligado) iniciar() else parar()
    }

    /** Wi-Fi conectou/caiu: reavalia na hora (a regra "bloquear no Wi-Fi" precisa reagir rápido). */
    private fun ouvirWifi(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val pedido = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            val vigia = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = ControleDeDadosMoveis.reavaliar(contexto())

                override fun onLost(network: Network) = ControleDeDadosMoveis.reavaliar(contexto())
            }
            cm.registerNetworkCallback(pedido, vigia)
            synchronized(trava) { vigiaDoWifi = vigia }
        } catch (t: Throwable) {
            Log.w(TAG, "Wi-Fi: ${t.message}")
        }
    }

    /**
     * Hotspot ligou/desligou: reforça o bloqueio na hora (o hotspot religa o dado móvel para ter
     * saída). `android.net.conn.TETHER_STATE_CHANGED` não é protegido.
     */
    private fun ouvirHotspot(ctx: Context) {
        try {
            val receptor = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    try {
                        ControleDeDadosMoveis.reavaliar(contexto())
                    } catch (_: Throwable) {
                    }
                }
            }
            ctx.registerReceiver(receptor, IntentFilter("android.net.conn.TETHER_STATE_CHANGED"))
            synchronized(trava) { receptorDoHotspot = receptor }
        } catch (t: Throwable) {
            Log.w(TAG, "hotspot: ${t.message}")
        }
    }

    private data class Quatro(
        val linha: Handler?,
        val laco: Runnable?,
        val vigia: ConnectivityManager.NetworkCallback?,
        val receptor: BroadcastReceiver?,
    )
}
