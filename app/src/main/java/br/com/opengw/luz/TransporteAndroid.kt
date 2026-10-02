package br.com.opengw.luz

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import java.util.UUID

/** O transporte de verdade: `BluetoothGatt` da plataforma. */
@SuppressLint("MissingPermission")
internal class TransporteAndroid(contexto: Context, private val registro: Registro) : TransporteBle {

    private val contexto: Context = contexto.applicationContext

    private fun adaptador(): BluetoothAdapter? = try {
        (contexto.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    } catch (e: Exception) {
        null
    }

    override fun temPermissao(): Boolean = PermissoesDaLuz.podeConectar(contexto)

    override fun adaptadorLigado(): Boolean = try {
        adaptador()?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    override fun enderecoValido(endereco: String): Boolean = BluetoothAdapter.checkBluetoothAddress(endereco.uppercase())

    override fun abrir(endereco: String, avisos: AvisosDoGatt): GattDaFita? {
        val aparelho = adaptador()?.getRemoteDevice(endereco.uppercase()) ?: return null
        val ponte = GattAndroid(endereco, aparelho, contexto, registro)
        val retorno = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                ponte.vincular(gatt)
                val novo = when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> EstadoDoGatt.CONECTADO
                    BluetoothProfile.STATE_DISCONNECTED -> EstadoDoGatt.DESCONECTADO
                    else -> EstadoDoGatt.OUTRO
                }
                avisos.conexaoMudou(ponte, status, novo)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                ponte.vincular(gatt)
                avisos.servicosDescobertos(ponte, status)
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                avisos.escritaConfirmada(ponte, status)
            }

            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                avisos.rssiLido(ponte, rssi, status)
            }
        }
        val nativo = aparelho.connectGatt(contexto, false, retorno, BluetoothDevice.TRANSPORT_LE) ?: return null
        ponte.vincular(nativo)
        return ponte
    }

    companion object {
        val SERVICO: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val ESCRITA: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    }
}

@SuppressLint("MissingPermission")
private class GattAndroid(
    override val endereco: String,
    private val aparelho: BluetoothDevice,
    private val contexto: Context,
    private val registro: Registro,
) : GattDaFita {

    @Volatile
    private var nativo: BluetoothGatt? = null

    @Volatile
    private var escrita: BluetoothGattCharacteristic? = null

    fun vincular(gatt: BluetoothGatt) {
        if (nativo == null) nativo = gatt
    }

    override fun descobrirServicos(): Boolean = nativo?.discoverServices() ?: false

    override fun caracteristicaDeEscrita(): CaracteristicaDaFita? {
        val g = nativo ?: return null
        val servico = g.getService(TransporteAndroid.SERVICO) ?: return null
        val c = servico.getCharacteristic(TransporteAndroid.ESCRITA) ?: return null
        val semResposta = (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
        c.writeType = if (semResposta) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        escrita = c
        return CaracteristicaDaFita(servico.uuid.toString(), c.uuid.toString(), semResposta)
    }

    override fun escrever(quadro: ByteArray): Boolean {
        val g = nativo ?: return false
        val c = escrita ?: return false
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(c, quadro, c.writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            c.value = quadro
            @Suppress("DEPRECATION")
            g.writeCharacteristic(c)
        }
    }

    override fun lerRssi() {
        nativo?.readRemoteRssi()
    }

    override fun nome(): String? {
        if (!PermissoesDaLuz.podeConectar(contexto)) return null
        return try {
            aparelho.name
        } catch (e: SecurityException) {
            null
        }
    }

    override fun fechar() {
        val g = nativo ?: return
        if (PermissoesDaLuz.podeConectar(contexto)) {
            try {
                g.disconnect()
            } catch (e: Exception) {
                registro.anotar("disconnect falhou em $endereco: $e")
            }
        }
        // Limpa o cache GATT do aparelho (método oculto `refresh`). Sem ele, uma fita que trocou de
        // firmware ou travou costuma voltar com a tabela de serviços velha.
        try {
            g.javaClass.getMethod("refresh").invoke(g)
        } catch (e: Exception) {
            Log.w(TAG, "refresh do GATT indisponível em $endereco: $e")
        }
        try {
            g.close()
        } catch (e: Exception) {
            registro.anotar("close falhou em $endereco: $e")
        }
    }

    private companion object {
        const val TAG = "OpenGW.Luz"
    }
}
