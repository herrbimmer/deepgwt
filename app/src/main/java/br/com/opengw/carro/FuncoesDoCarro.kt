package br.com.opengw.carro

import android.util.Log

/**
 * Ponto de partida do pacote `carro` (Alpha 8.5), chamado pelo ServiceManager depois que os
 * serviços do carro sobem. Cada função se inicia sozinha e uma falha não impede as outras.
 */
object FuncoesDoCarro {
    private const val TAG = "FuncoesDoCarro"

    fun iniciar() {
        runCatching { MemoriaDosAssistentes.iniciar() }.onFailure { Log.e(TAG, "Memória dos assistentes", it) }
        runCatching { ConectorDeRecarga.iniciar() }.onFailure { Log.e(TAG, "Conector de recarga", it) }
        runCatching { CoringasDoVolante.iniciar() }.onFailure { Log.e(TAG, "Coringas do volante", it) }
    }
}
