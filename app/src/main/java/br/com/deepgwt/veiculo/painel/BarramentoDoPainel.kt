// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.RejectedExecutionException

/**
 * O barramento de eventos do painel (§C), sem Android. Seguro entre threads: cada publicação percorre
 * uma foto da lista, então quem entra ou sai no meio só vale para a próxima.
 *
 * Log (D-P4, recomendação seguida): uma linha por publicação só para os eventos raros; as teclas
 * ([EventoDoPainel.TeclaRecebida], [EventoDoPainel.TeclaParaOTema]) não geram log aqui.
 */
internal class BarramentoDoPainel(
    private val registro: RegistroDoPainel,
    private val relogio: Relogio,
    /** A linha serial do painel (a mesma do pulso, A.4), ou null se ainda não existe. */
    private val linha: () -> LinhaDoPainel?,
) : PublicadorDoPainel {

    companion object {
        /** Repasse fora da thread mais lento que isto vira aviso no log (C.3). */
        const val REPASSE_LENTO_MS = 50L
    }

    private val ouvintes = CopyOnWriteArrayList<EventosDoPainel.Ouvinte>()

    /** C.1: acrescenta se o mesmo objeto ainda não está; repetir não duplica (aviso). */
    fun observar(ouvinte: EventosDoPainel.Ouvinte) {
        val novo = synchronized(ouvintes) {
            if (ouvintes.any { it === ouvinte }) false else ouvintes.add(ouvinte)
        }
        if (!novo) registro.aviso("barramento: o ouvinte $ouvinte já observava")
    }

    /** C.1: tira o objeto; quem não está → aviso, nada. */
    fun pararDeObservar(ouvinte: EventosDoPainel.Ouvinte) {
        val saiu = synchronized(ouvintes) {
            val posicao = ouvintes.indexOfFirst { it === ouvinte }
            if (posicao >= 0) ouvintes.removeAt(posicao)
            posicao >= 0
        }
        if (!saiu) registro.aviso("barramento: o ouvinte $ouvinte não observava")
    }

    val quantos: Int get() = ouvintes.size

    /** C.2: na thread de quem publicou, na ordem de registro; exceção de um não impede os outros. */
    override fun publicar(evento: EventoDoPainel) {
        if (mereceLog(evento)) registro.info("barramento: $evento")
        for (ouvinte in ouvintes) {
            try {
                ouvinte.aoEvento(evento)
            } catch (e: Exception) {
                registro.erro("barramento: um ouvinte lançou exceção com $evento", e)
            } catch (e: LinkageError) {
                registro.erro("barramento: um ouvinte lançou erro com $evento", e)
            }
        }
    }

    /** C.3: na linha do painel, sem segurar quem chamou; sem linha (ou linha parada), na hora. */
    override fun publicarForaDaThread(evento: EventoDoPainel) {
        val atual = linha()
        if (atual != null) {
            try {
                atual.executar { repassar(evento) }
                return
            } catch (e: RejectedExecutionException) {
                registro.aviso("barramento: linha do painel parada; $evento entregue na hora")
            }
        }
        repassar(evento)
    }

    private fun repassar(evento: EventoDoPainel) {
        val inicio = relogio.agora()
        publicar(evento)
        val gasto = relogio.agora() - inicio
        if (gasto > REPASSE_LENTO_MS) registro.aviso("barramento: repasse de $evento levou $gasto ms")
    }

    private fun mereceLog(evento: EventoDoPainel): Boolean =
        evento !is EventoDoPainel.TeclaRecebida && evento !is EventoDoPainel.TeclaParaOTema
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
