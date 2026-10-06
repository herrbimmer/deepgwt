// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class FitaEncontrada(
    val nome: String?,
    val endereco: String,
    val rssi: Int,
    val pareceFita: Boolean,
    val anunciaFfe0: Boolean,
    val anuncioHex: String?,
)

/** As regras da varredura que não dependem do Android (casos 81 e 86). */
internal object AnuncioDaFita {

    const val UUID_FFE0 = "0000ffe0-0000-1000-8000-00805f9b34fb"
    private const val BYTES_DO_ANUNCIO = 48

    /** Os primeiros 48 bytes do anúncio cru, em hex maiúsculo. */
    fun hex(anuncio: ByteArray?): String? {
        if (anuncio == null) return null
        return Hex.deBytes(anuncio.copyOf(minOf(BYTES_DO_ANUNCIO, anuncio.size)))
    }

    /** O UUID FFE0 está na lista anunciada, ou `E0FF`/`FFE0` aparece em qualquer posição do hex. */
    fun anunciaFfe0(servicos: Collection<String>, hex: String?): Boolean =
        servicos.any { it.equals(UUID_FFE0, ignoreCase = true) } ||
            (hex != null && (hex.contains("E0FF") || hex.contains("FFE0")))

    fun pareceFita(nome: String?, anunciaFfe0: Boolean): Boolean =
        anunciaFfe0 || (nome?.contains("LED", ignoreCase = true) == true)

    fun montar(nome: String?, endereco: String, rssi: Int, servicos: Collection<String>, anuncio: ByteArray?): FitaEncontrada {
        val hex = hex(anuncio)
        val ffe0 = anunciaFfe0(servicos, hex)
        return FitaEncontrada(nome, endereco, rssi, pareceFita(nome, ffe0), ffe0, hex)
    }

    /** O tipo mostrado em cada linha da lista. */
    fun tipo(fita: FitaEncontrada): String {
        val nome = fita.nome.orEmpty()
        return when {
            fita.anunciaFfe0 -> "LEDLAMP FFE0"
            nome.contains("LEDCAR", ignoreCase = true) -> "LEDCAR"
            nome.contains("LEDDMX", ignoreCase = true) -> "LEDDMX"
            nome.contains("LED", ignoreCase = true) -> "LED"
            else -> "BLE não identificado"
        }
    }

    /** As que parecem fita primeiro, depois o sinal mais forte; no máximo [limite]. */
    fun paraTela(achados: List<FitaEncontrada>, limite: Int = 12): List<FitaEncontrada> =
        achados.sortedWith(compareByDescending<FitaEncontrada> { it.pareceFita }.thenByDescending { it.rssi }).take(limite)
}

/** Permissões de Bluetooth e de áudio do módulo (caso 79). */
internal object PermissoesDaLuz {

    fun varredura(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun podeVarrer(contexto: Context): Boolean = varredura().all { concedida(contexto, it) }

    fun podeConectar(contexto: Context): Boolean =
        concedida(contexto, if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH)

    fun podeGravarAudio(contexto: Context): Boolean = concedida(contexto, Manifest.permission.RECORD_AUDIO)

    private fun concedida(contexto: Context, permissao: String): Boolean =
        ContextCompat.checkSelfPermission(contexto, permissao) == PackageManager.PERMISSION_GRANTED
}

/**
 * Varredura BLE sem filtros por [duracaoMs] (casos 80–83). Os retornos chegam na thread principal.
 * [parar] cancela o prazo e não chama `aoTerminar`.
 */
@SuppressLint("MissingPermission")
class BuscaDeFitas(contexto: Context) {

    private val contexto: Context = contexto.applicationContext
    private val principal = Handler(Looper.getMainLooper())

    private var scanner: BluetoothLeScanner? = null
    private var retorno: ScanCallback? = null
    private var prazo: Runnable? = null

    /** Cada [iniciar] abre uma geração nova; avisos de uma geração velha são ignorados. */
    @Volatile
    private var geracao = 0

    @JvmOverloads
    fun iniciar(
        aoAchar: (FitaEncontrada) -> Unit,
        aoFalhar: (String) -> Unit,
        aoTerminar: () -> Unit,
        duracaoMs: Long = 12_000,
    ) {
        parar()
        val minha = ++geracao
        if (!temPermissao(contexto)) {
            naPrincipal {
                aoFalhar("Permissão de Bluetooth/localização ausente")
                aoTerminar()
            }
            return
        }
        val adaptador = try {
            (contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        } catch (e: Exception) {
            null
        }
        val leitor = try {
            if (adaptador != null && adaptador.isEnabled) adaptador.bluetoothLeScanner else null
        } catch (e: SecurityException) {
            null
        }
        if (leitor == null) {
            naPrincipal {
                aoFalhar(if (adaptador == null) "Este aparelho não tem Bluetooth" else "Bluetooth desligado ou sem varredura BLE")
                aoTerminar()
            }
            return
        }
        val vistos = HashSet<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                naPrincipal { if (minha == geracao) tratar(result, vistos, aoAchar) }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                naPrincipal { if (minha == geracao) results.forEach { tratar(it, vistos, aoAchar) } }
            }

            override fun onScanFailed(errorCode: Int) {
                naPrincipal {
                    if (minha != geracao) return@naPrincipal
                    pararSemAvisar()
                    aoFalhar("A varredura BLE falhou: código $errorCode")
                    aoTerminar()
                }
            }
        }
        try {
            val ajustes = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
            leitor.startScan(null, ajustes, cb)
        } catch (e: Exception) {
            naPrincipal {
                aoFalhar("Não foi possível iniciar a varredura: ${e.message ?: e.javaClass.simpleName}")
                aoTerminar()
            }
            return
        }
        scanner = leitor
        retorno = cb
        val fim = Runnable {
            if (minha != geracao) return@Runnable
            pararSemAvisar()
            aoTerminar()
        }
        prazo = fim
        principal.postDelayed(fim, duracaoMs)
    }

    /** Para a varredura e cancela o prazo, sem chamar `aoTerminar`. */
    fun parar() {
        geracao++
        pararSemAvisar()
    }

    private fun pararSemAvisar() {
        prazo?.let { principal.removeCallbacks(it) }
        prazo = null
        val leitor = scanner
        val cb = retorno
        scanner = null
        retorno = null
        if (leitor != null && cb != null) {
            try {
                leitor.stopScan(cb)
            } catch (e: Exception) {
                // Bluetooth desligado no meio, ou permissão revogada: nada a fazer.
            }
        }
    }

    private fun tratar(r: ScanResult, vistos: MutableSet<String>, aoAchar: (FitaEncontrada) -> Unit) {
        val endereco = r.device?.address ?: return
        if (!vistos.add(endereco.uppercase())) return
        val registro = r.scanRecord
        val nome = registro?.deviceName?.takeIf { it.isNotBlank() } ?: try {
            if (PermissoesDaLuz.podeConectar(contexto)) r.device.name else null
        } catch (e: SecurityException) {
            null
        }
        val servicos = registro?.serviceUuids?.map { it.uuid.toString() }.orEmpty()
        aoAchar(AnuncioDaFita.montar(nome, endereco, r.rssi, servicos, registro?.bytes))
    }

    private fun naPrincipal(bloco: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) bloco() else principal.post(bloco)
    }

    companion object {
        /** API ≥ 31: BLUETOOTH_SCAN e BLUETOOTH_CONNECT; abaixo (a central é API 28): ACCESS_FINE_LOCATION. */
        @JvmStatic
        fun permissoesNecessarias(): Array<String> = PermissoesDaLuz.varredura()

        @JvmStatic
        fun temPermissao(contexto: Context): Boolean = PermissoesDaLuz.podeVarrer(contexto)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
