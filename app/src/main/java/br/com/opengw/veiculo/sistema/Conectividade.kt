package br.com.opengw.veiculo.sistema

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import br.com.opengw.nucleo.OpenGw
import br.com.opengw.rede.TetheringDoSistema
import br.com.opengw.veiculo.automacoes.Automacoes
import br.com.opengw.veiculo.comandos.DiarioDoMotor
import br.com.opengw.veiculo.comandos.LinhaDoMotor
import br.com.opengw.veiculo.comandos.PreferenciasDoApp
import br.com.opengw.veiculo.comandos.RegistroNoLogcatDe
import br.com.opengw.veiculo.comandos.ShellPeloShizuku
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bluetooth e hotspot (motor-sistema.md §A). A lógica fica em [NucleoDaConectividade]; aqui só se
 * liga ao Android. As funções públicas que rodam shell ou binder enfileiram na linha do motor e
 * voltam na hora; as `internal` (usadas pelas automações, já na linha) rodam na hora.
 */
object Conectividade {

    private val registro = RegistroNoLogcatDe("OpenGW.Conectividade")

    internal val nucleo: NucleoDaConectividade by lazy {
        NucleoDaConectividade(
            bluetooth = BluetoothAndroid,
            hotspot = HotspotAndroid,
            shell = ShellPeloShizuku,
            prefs = PreferenciasDoApp,
            agenda = LinhaDoMotor.agenda,
            carroDesligado = { Automacoes.carroDesligado },
            registro = registro,
            diario = DiarioDoMotor,
        )
    }

    private val receptoresRegistrados = AtomicBoolean(false)

    @JvmStatic val bluetoothLigado: Boolean get() = nucleo.bluetoothLigado

    @JvmStatic fun ligarBluetooth(motivo: String) = naLinha("ligar bluetooth") { nucleo.ligarBluetooth(motivo) }

    @JvmStatic fun desligarBluetooth(motivo: String) = naLinha("desligar bluetooth") { nucleo.desligarBluetooth(motivo) }

    /** A.6 (nesta central, sempre falso: a reflexão devolve -1). */
    @JvmStatic val hotspotLigado: Boolean get() = nucleo.hotspotLigado

    /** A.9: roda shell; chamar fora da principal. */
    @JvmStatic val hotspotNoAr: Boolean get() = nucleo.hotspotNoAr

    @JvmStatic fun ligarHotspot() = naLinha("ligar hotspot") { nucleo.ligarHotspot() }

    @JvmStatic fun desligarHotspot() = naLinha("desligar hotspot") { nucleo.desligarHotspot() }

    internal fun desligarBluetoothGuardando(motivo: String) = nucleo.desligarBluetoothGuardando(motivo)

    internal fun religarBluetoothSeFomosNos(motivo: String) = nucleo.religarBluetoothSeFomosNos(motivo)

    internal fun desligarHotspotGuardando() = nucleo.desligarHotspotGuardando()

    internal fun religarHotspotSeFomosNos() = nucleo.religarHotspotSeFomosNos()

    internal fun cancelarRetentativas() = nucleo.cancelarRetentativas()

    /** A.12: alça do `connectivity` e os dois receptores (uma vez por processo). */
    internal fun prepararNaPartida(contexto: Context) {
        HotspotAndroid.abrir(contexto)
        if (!receptoresRegistrados.compareAndSet(false, true)) return
        val app = contexto.applicationContext ?: contexto
        try {
            val doBluetooth = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(app, ReceptorDoBluetooth(), doBluetooth, ContextCompat.RECEIVER_EXPORTED)
            ContextCompat.registerReceiver(
                app,
                ReceptorDoHotspot(),
                IntentFilter(ACAO_DO_HOTSPOT),
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (e: Exception) {
            registro.erro("não deu para registrar os receptores de Bluetooth/hotspot", e)
        }
    }

    /** A.12, depois do "pronto". */
    internal fun semearDepoisDoPronto() = naLinha("semear o hotspot") { nucleo.semearEstadoDoHotspot() }

    private fun naLinha(oQue: String, tarefa: () -> Unit) {
        try {
            LinhaDoMotor.agenda.executar(tarefa)
        } catch (e: Exception) {
            registro.erro("$oQue: não deu para enfileirar", e)
        }
    }

    private const val ACAO_DO_HOTSPOT = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_DO_HOTSPOT = "wifi_state"

    /** A.3 (thread principal: o trabalho vai para a linha). */
    private class ReceptorDoBluetooth : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val acao = intent.action
            val estado = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            val conexao = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
            naLinha("receptor do bluetooth") { nucleo.aoMudarBluetooth(acao, estado, conexao) }
        }
    }

    /** A.10. */
    private class ReceptorDoHotspot : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val estado = intent.getIntExtra(EXTRA_DO_HOTSPOT, -1)
            naLinha("receptor do hotspot") { nucleo.aoMudarHotspot(estado) }
        }
    }

    private object BluetoothAndroid : BluetoothDoCarro {
        override fun ligado(): Boolean {
            val gerente = OpenGw.contexto.getSystemService(BluetoothManager::class.java) ?: return false
            return gerente.adapter?.isEnabled == true
        }
    }

    /** O tethering pelo serviço `connectivity`, com as chamadas saindo pelo Shizuku (fatos §3). */
    private object HotspotAndroid : HotspotDoCarro {

        /** Tipo 0 = Wi-Fi. */
        private const val WIFI = 0

        fun abrir(contexto: Context) {
            TetheringDoSistema.abrir(contexto.packageName)
        }

        override fun estadoPorReflexao(): Int? =
            try {
                val wifi = OpenGw.contexto.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val resposta = try {
                    wifi.javaClass.getMethod("getWifiApState").invoke(wifi)
                } catch (e: Exception) {
                    HiddenApiBypass.invoke(WifiManager::class.java, wifi, "getWifiApState")
                }
                resposta as? Int
            } catch (e: Exception) {
                null
            }

        override fun ligar(): Boolean =
            TetheringDoSistema.ligarWifi { codigo -> registro.info("hotspot: startTethering respondeu $codigo (0 = ligou)") }

        override fun desligar(): Boolean = TetheringDoSistema.desligarWifi()
    }
}
