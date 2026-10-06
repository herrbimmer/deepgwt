// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

/**
 * O conjunto de chaves que o app pede à central (A.8): (a) as padrão, (b) as escolhidas pela pessoa
 * e (c) as pedidas em tempo de execução por `acompanhar`. Só cresce (E.1); vive em memória (E.4).
 * Seguro entre threads.
 */
internal class ChavesAcompanhadas(
    padrao: List<String>,
    private val lerDoUsuario: () -> Set<String>,
    private val registro: RegistroLimitado,
) {
    private val trava = Any()
    private val padrao: Set<String> = LinkedHashSet(padrao)
    private var doUsuario: Set<String> = emptySet()
    private val dinamicas = LinkedHashSet<String>()

    /** Relê (b) das preferências. Falha na leitura = conjunto vazio, registrado. */
    fun recarregarDoUsuario() {
        val lidas = try {
            lerDoUsuario()
        } catch (e: Exception) {
            registro.erro("preferencias", "não deu para ler as chaves escolhidas pela pessoa", e)
            emptySet()
        }
        synchronized(trava) { doUsuario = LinkedHashSet(lidas) }
    }

    /** (a) ∪ (b) ∪ (c), sem repetição, nessa ordem. */
    fun uniao(): List<String> = synchronized(trava) {
        val todas = LinkedHashSet<String>(padrao.size + doUsuario.size + dinamicas.size)
        todas.addAll(padrao)
        todas.addAll(doUsuario)
        todas.addAll(dinamicas)
        todas.toList()
    }

    /** Guarda em (c) as que ainda não estão em nenhum dos três e devolve só essas, na ordem dada. */
    fun somarNovas(chaves: Collection<String>): List<String> = synchronized(trava) {
        val novas = ArrayList<String>()
        for (chave in chaves) {
            if (chave in padrao || chave in doUsuario || chave in dinamicas) continue
            dinamicas.add(chave)
            novas.add(chave)
        }
        novas
    }

    fun contem(chave: String): Boolean = synchronized(trava) {
        chave in padrao || chave in doUsuario || chave in dinamicas
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
