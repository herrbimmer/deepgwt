// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.vhal

import android.os.SystemClock
import android.util.Log

/** Uma leitura de propriedade da central. Lança em caso de falha; devolve o objeto cru do valor. */
internal fun interface FonteDePropriedades {
    fun obter(id: Int, area: Int): Any?
}

/**
 * Leitura síncrona de uma propriedade da central pelo id, devolvendo texto ou `null`.
 *
 * Regra de honestidade: nunca inventa valor; qualquer falha ou ausência é `null`. Nunca lança.
 * Cada leitura é uma transação de binder com a ECU e pode demorar: **não chamar na thread principal**.
 * Todas as chamadas são serializadas (uma leitura por vez).
 *
 * @param fonte abre a ligação com a central, ou devolve `null`/lança se não deu.
 * @param relogioMs relógio monotônico em milissegundos.
 */
class LeitorDeVhal internal constructor(
    private val fonte: () -> FonteDePropriedades?,
    private val relogioMs: () -> Long,
) {
    constructor() : this(FonteDaCentral::abrir, SystemClock::uptimeMillis)

    private val trava = Any()
    private var ligada: FonteDePropriedades? = null

    /** Início da última tentativa de ligar (relógio monotônico); nulo = ainda não houve. */
    private var ultimaTentativaMs: Long? = null

    /**
     * O valor da propriedade [id] na área [area], como texto sem branco nas pontas; `null` se não
     * há ligação, a leitura falhou, o valor é nulo ou o texto é vazio.
     */
    fun ler(id: Int, area: Int = 0): String? = synchronized(trava) {
        try {
            val alca = ligada ?: ligar() ?: return null
            try {
                formatar(alca.obter(id, area))
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
                // Falha na leitura: descarta a ligação; a próxima chamada tenta de novo (respeitando a janela).
                ligada = null
                registrar("leitura de $id falhou: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            null
        }
    }

    /** Descarta a ligação e zera a janela de espera: a próxima leitura tenta ligar na hora. */
    fun fechar() {
        synchronized(trava) {
            ligada = null
            ultimaTentativaMs = null
        }
    }

    /** No máximo uma tentativa a cada [JANELA_MS], contada desde o início da última. */
    private fun ligar(): FonteDePropriedades? {
        val agora = relogioMs()
        val ultima = ultimaTentativaMs
        if (ultima != null && agora - ultima < JANELA_MS) return null
        ultimaTentativaMs = agora
        return try {
            fonte().also { ligada = it }
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            registrar("ligação com a central falhou: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun registrar(mensagem: String) {
        try {
            Log.w(TAG, mensagem)
        } catch (e: Throwable) {
            // Teste JVM sem o Android: o log não existe.
        }
    }

    internal companion object {
        const val JANELA_MS = 10_000L
        private const val TAG = "DeepGWT.Vhal"

        /**
         * Texto do valor: `toString()` sem branco nas pontas; vetores viram `{a,b,c}`; nulo ou vazio = `null`.
         */
        fun formatar(valor: Any?): String? {
            if (valor == null) return null
            val texto = if (valor.javaClass.isArray) {
                val n = java.lang.reflect.Array.getLength(valor)
                (0 until n).joinToString(",", "{", "}") { java.lang.reflect.Array.get(valor, it).toString() }
            } else {
                valor.toString().trim()
            }
            return texto.trim().ifEmpty { null }
        }
    }
}

/** Instância única do app: liga o catálogo à tela de diagnóstico e à calibração do painel. */
object Vhal {
    private val leitor: LeitorDeVhal by lazy { LeitorDeVhal() }

    /** Lê a propriedade [id] (área 0). Bloqueia; chamar fora da thread principal. */
    fun ler(id: Int): String? = leitor.ler(id)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
