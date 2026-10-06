// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import br.com.deepgwt.nucleo.Licenciamento
import br.com.deepgwt.nucleo.DeepGwt
import java.util.concurrent.ConcurrentHashMap

/**
 * Luz ambiente BLE/DMX (módulo 3.4): uma fita de LED instalada pelo dono, ligada a um controlador
 * "LED Lamp" (`LEDCAR-…`/`LEDDMX-…`). Implementação própria, escrita em sala limpa a partir de
 * `docs/independencia/specs/luz-ambiente.md`.
 *
 * Esta é a fachada: quem está fora do pacote só chama o que está aqui (mais [TelaDaLuzAmbiente],
 * [BuscaDeFitas] e os tipos públicos). Nenhuma chamada bloqueia.
 */
object LuzAmbiente {

    const val CONEXAO_PRINCIPAL = "default"

    private const val TAG = "DeepGWT.Luz"

    /** Mídia tocando agora e cores da capa: registradas por quem tem essas informações. */
    @JvmField
    val fontes = FontesDaLuz()

    private val conexoes = ConcurrentHashMap<String, ConexaoBle>()

    @Volatile
    private var receptorRegistrado = false

    /** Preferência ligada E o nível de licença permite o recurso. */
    @JvmStatic
    fun ligada(): Boolean = try {
        PreferenciasDaLuz.ler().ligado && Licenciamento.luzAmbiente()
    } catch (e: Exception) {
        Log.e(TAG, "não deu para ler se a luz está ligada", e)
        false
    }

    /** Sobe/reaplica tudo se [ligada] e o modo sorrateiro NÃO estiver ativo. Volta na hora. */
    @JvmStatic
    fun iniciarSeLigada(contexto: Context) {
        if (sorrateiro()) return
        if (ligada()) ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_INICIAR)
    }

    /**
     * [ligada] → sobe/reaplica; senão → [parar]. Respeita o modo sorrateiro (ativo → parar), como
     * [iniciarSeLigada] — decisão da sala suja de 01/10/2026.
     */
    @JvmStatic
    fun reaplicar(contexto: Context) {
        if (ligada() && !sorrateiro()) {
            ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_INICIAR)
        } else {
            parar(contexto)
        }
    }

    /** Para tudo: solta ouvintes, encerra efeitos, desconecta a fita principal. */
    @JvmStatic
    fun parar(contexto: Context) {
        ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_PARAR)
    }

    /** Piscada vermelha do "+" do clima (só se [ligada]). */
    @JvmStatic
    fun piscarPeloClima(contexto: Context) {
        if (ligada()) ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_PISCAR_CLIMA)
    }

    /** A conexão daquela chave (cria na primeira vez). A principal é [CONEXAO_PRINCIPAL]. */
    @JvmStatic
    @JvmOverloads
    fun conexao(chave: String = CONEXAO_PRINCIPAL): ConexaoDaFita = conexaoBle(chave)

    // ------------------------------------------------------------------ uso interno (serviço e tela)

    internal fun conexaoBle(chave: String): ConexaoBle {
        conexoes[chave]?.let { return it }
        val contexto = DeepGwt.contexto
        registrarReceptorDoBluetooth(contexto)
        return conexoes.computeIfAbsent(chave) { c ->
            ConexaoBle(
                chave = c,
                transporte = TransporteAndroid(contexto, RegistroAndroid),
                agendador = AgendadorDeThread("DeepGWT.Luz.$c", RegistroAndroid),
                relogio = RelogioDoSistema,
                registro = RegistroAndroid,
            )
        }
    }

    internal fun conectar(contexto: Context, endereco: String?, nome: String?) =
        ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_CONECTAR) {
            if (endereco != null) putExtra(ServicoDaLuz.EXTRA_ENDERECO, endereco)
            if (nome != null) putExtra(ServicoDaLuz.EXTRA_NOME, nome)
        }

    internal fun desconectar(contexto: Context) = ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_DESCONECTAR)

    internal fun brilho(contexto: Context, percentual: Int) =
        ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_BRILHO) { putExtra(ServicoDaLuz.EXTRA_BRILHO, percentual) }

    internal fun testarCor(contexto: Context, cor: CorLed) =
        ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_TESTAR_COR) {
            putExtra(ServicoDaLuz.EXTRA_R, cor.r)
            putExtra(ServicoDaLuz.EXTRA_G, cor.g)
            putExtra(ServicoDaLuz.EXTRA_B, cor.b)
        }

    internal fun enviarHex(contexto: Context, texto: String) =
        ServicoDaLuz.enviar(contexto, ServicoDaLuz.ACAO_ENVIAR_HEX) { putExtra(ServicoDaLuz.EXTRA_HEX, texto) }

    private fun sorrateiro(): Boolean = try {
        PreferenciasDaLuz.ler().sorrateiro
    } catch (e: Exception) {
        false
    }

    /**
     * Bluetooth ligado de novo: cada conexão com alvo e reconexão ligada tenta outra vez (sem isto,
     * desligar e religar o Bluetooth deixava a fita em "Erro" até o próximo comando).
     */
    private fun registrarReceptorDoBluetooth(contexto: Context) {
        if (receptorRegistrado) return
        synchronized(this) {
            if (receptorRegistrado) return
            try {
                ContextCompat.registerReceiver(
                    contexto.applicationContext,
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            val estado = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                            if (estado == BluetoothAdapter.STATE_ON) conexoes.values.forEach { it.bluetoothLigou() }
                        }
                    },
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                    // Aviso do sistema: chega mesmo sem exportar o receptor.
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                receptorRegistrado = true
            } catch (e: Exception) {
                Log.e(TAG, "não deu para acompanhar o estado do Bluetooth", e)
            }
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
