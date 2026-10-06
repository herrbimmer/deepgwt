// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.painel

import br.com.deepgwt.nucleo.Preferencia
import br.com.deepgwt.painel.TeclaDoVolante

/*
 * As costuras do painel e do volante (módulo 3.3d): tudo o que a lógica usa do mundo de fora chega
 * por aqui. No app são Android, a thread do painel e os serviços da fabricante
 * (`AndroidDoPainel.kt`); nos testes de JVM, falsos escritos à mão.
 * Especificação: `docs/independencia/specs/motor-painel-e-volante.md`.
 */

/** Instante "nunca houve" (os instantes são ms de uptime). */
internal const val NUNCA: Long = Long.MIN_VALUE

/** Relógio monotônico (uptime, ms). */
internal fun interface Relogio {
    fun agora(): Long
}

/**
 * A "linha do painel": uma thread só, em série, na ordem de chegada (pulso, re-entrega do 134 e
 * repasse dos eventos do painel). [executar] e [agendar] podem lançar
 * `java.util.concurrent.RejectedExecutionException` depois de [encerrar].
 */
internal interface LinhaDoPainel {
    fun executar(tarefa: () -> Unit)

    fun agendar(atrasoMs: Long, tarefa: () -> Unit)

    /** Para a linha; o que estava na fila morre com ela. */
    fun encerrar()
}

/** Para onde vão as mensagens de diagnóstico (o logcat, no app). */
internal interface RegistroDoPainel {
    fun info(mensagem: String)

    fun aviso(mensagem: String, causa: Throwable? = null)

    fun erro(mensagem: String, causa: Throwable? = null)
}

/** O diário persistente de diagnóstico (`MotorDoVeiculo.diario`). */
internal typealias DiarioDoPainel = (evento: String, detalhes: Map<String, Any?>) -> Unit

/** Leitura das preferências do app ("deepgwt_prefs"), sempre na hora. Nunca lança. */
internal interface LeitorDePreferencias {
    fun booleano(chave: Preferencia, padrao: Boolean): Boolean

    fun texto(chave: Preferencia, padrao: String): String
}

/** Mandar uma mensagem ao serviço do painel (`setMsg(id, ClusterMsgData com o valor)`). Lança na falha. */
internal fun interface CanalDoPainel {
    fun enviar(id: Int, valor: Int)
}

/** A interface do serviço do painel depois de conectar (A.1, A.8). Tudo lança na falha. */
internal interface ServicoRemotoDoPainel : CanalDoPainel {
    /** `registerCallback` com o retorno único do processo. */
    fun registrarRetorno()

    /** `unregisterCallback` com o mesmo retorno. */
    fun desregistrarRetorno()
}

/**
 * Ligação com um serviço de outro app (`bindService`). Os avisos de conexão chegam depois, em
 * qualquer thread. Uma ligação de cada vez: [ligar] desfaz a anterior.
 */
internal interface ConectorDeServico<S> {
    /** Pede a ligação; false = o sistema recusou (o serviço não existe, ex.: no simulador). */
    fun ligar(aoConectar: (S) -> Unit, aoDesconectar: () -> Unit): Boolean

    /** Desfaz a ligação atual, se houver. Nunca lança. */
    fun desligar()
}

/** Avisar os ouvintes do barramento (C.2 e C.3). */
internal interface PublicadorDoPainel {
    fun publicar(evento: EventoDoPainel)

    fun publicarForaDaThread(evento: EventoDoPainel)
}

/** O que o volante (B.4) precisa do estado do cartão do painel (A.5–A.7). */
internal interface CartaoDoPainel {
    val cartaoAtual: Int

    /** B.4 passo 1: guarda (agora, tecla) como a última tecla do painel recebida. */
    fun teclaDoPainelRecebida(tecla: TeclaDoVolante)

    /** A.6: navegação sintética por ← ou →. */
    fun navegar(tecla: TeclaDoVolante)

    /** A.7. */
    fun soltarPosseDoAr()
}

/** Roda [bloco]; exceção (ou erro de ligação com código de fora) vira [padrao] e uma linha no log. */
internal inline fun <T> protegido(registro: RegistroDoPainel, oQue: String, padrao: T, bloco: () -> T): T =
    try {
        bloco()
    } catch (e: Exception) {
        registro.erro("$oQue lançou exceção", e)
        padrao
    } catch (e: LinkageError) {
        registro.erro("$oQue lançou erro", e)
        padrao
    }
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
