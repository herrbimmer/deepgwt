// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * A thread principal e o relógio da barra, trocáveis nos testes (a JVM não tem `Looper`).
 *
 * Toda escrita no estado da barra vem para cá: quem está em outra thread posta.
 */
internal object ThreadPrincipal {

    /** Como chegar à principal. Os testes trocam por uma execução imediata. */
    interface Agenda {
        fun estaNaPrincipal(): Boolean
        fun postar(tarefa: Runnable)
        fun postarComAtraso(atrasoMs: Long, tarefa: Runnable)
        fun cancelar(tarefa: Runnable)
        /** Relógio monotônico (ms), o `elapsedRealtime`. */
        fun agoraMs(): Long
    }

    private object AgendaAndroid : Agenda {
        private val handler by lazy { Handler(Looper.getMainLooper()) }
        override fun estaNaPrincipal() = Looper.myLooper() == Looper.getMainLooper()
        override fun postar(tarefa: Runnable) {
            handler.post(tarefa)
        }
        override fun postarComAtraso(atrasoMs: Long, tarefa: Runnable) {
            handler.postDelayed(tarefa, atrasoMs.coerceAtLeast(0L))
        }
        override fun cancelar(tarefa: Runnable) {
            handler.removeCallbacks(tarefa)
        }
        override fun agoraMs(): Long = SystemClock.elapsedRealtime()
    }

    @Volatile
    var agenda: Agenda = AgendaAndroid

    fun estaNaPrincipal(): Boolean = agenda.estaNaPrincipal()

    /** Na principal já: roda agora. Senão posta. */
    fun executar(tarefa: () -> Unit) {
        if (agenda.estaNaPrincipal()) tarefa() else agenda.postar(Runnable(tarefa))
    }

    /** Sempre enfileira (mesmo estando na principal). */
    fun postar(tarefa: () -> Unit) {
        agenda.postar(Runnable(tarefa))
    }

    fun postarComAtraso(atrasoMs: Long, tarefa: Runnable) {
        agenda.postarComAtraso(atrasoMs, tarefa)
    }

    fun cancelar(tarefa: Runnable) {
        agenda.cancelar(tarefa)
    }

    fun agoraMs(): Long = agenda.agoraMs()

    /** Volta para a agenda do Android (depois de um teste). */
    fun restaurar() {
        agenda = AgendaAndroid
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
