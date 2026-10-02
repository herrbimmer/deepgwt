package br.com.opengw.carro

import android.os.Handler
import android.os.HandlerThread

/**
 * A thread das funções do pacote `carro` (Alpha 8.5): memória dos assistentes, conector de
 * recarga e coringas do volante.
 *
 * Os três reagem a sinais do barramento e a teclas do volante, e os três escrevem no carro ou
 * rodam `settings` pelo Shizuku — chamadas que demoram. Nada disso pode rodar no despachante do
 * [br.com.opengw.managers.ServiceManager] (atrasaria todos os sinais), nem no
 * ouvinte do `inputservice` (atrasaria o volante), nem na tela. Uma fila só, e não uma por função:
 * elas nunca disputam a mesma chave, e a ordem de chegada fica previsível.
 */
object FilaDoCarro {
    private val thread: HandlerThread by lazy { HandlerThread("FuncoesDoCarro").also { it.start() } }

    val handler: Handler by lazy { Handler(thread.looper) }

    fun rodar(tarefa: () -> Unit) {
        handler.post { runCatching(tarefa).onFailure { android.util.Log.e("FuncoesDoCarro", "Tarefa falhou", it) } }
    }

    fun rodarDepois(atrasoMs: Long, tarefa: () -> Unit) {
        handler.postDelayed({ runCatching(tarefa).onFailure { android.util.Log.e("FuncoesDoCarro", "Tarefa falhou", it) } }, atrasoMs)
    }
}
