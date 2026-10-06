// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import java.util.concurrent.ConcurrentHashMap

/**
 * O que o cache sabe de uma chave: se ela [existe] no cache e, existindo, o [valor] (null quando
 * ficou lembrada como "sem valor"). Sem alocação: embrulha o conteúdo cru do mapa.
 */
@JvmInline
internal value class Lembranca(private val bruto: Any?) {
    val existe: Boolean get() = bruto != null
    val valor: String? get() = bruto as? String
}

/**
 * Último valor de cada chave, seguro entre threads. Distingue "nunca ouvi falar" de "a central disse
 * que não tem valor" (C.1 i, Casos de borda 3).
 */
internal class CacheDeValores {

    /** Marca de "lembrada sem valor" (o mapa concorrente não guarda null). */
    private object SemValor

    private val mapa = ConcurrentHashMap<String, Any>()

    fun lembrar(chave: String): Lembranca = Lembranca(mapa[chave])

    /** Grava (null = "sem valor"). */
    fun guardar(chave: String, valor: String?) {
        mapa[chave] = valor ?: SemValor
    }

    /** Grava só se a chave ainda não está; devolve o que ficou no cache. */
    fun guardarSeAusente(chave: String, valor: String?): Lembranca {
        val novo: Any = valor ?: SemValor
        return Lembranca(mapa.putIfAbsent(chave, novo) ?: novo)
    }

    /** Grava e devolve o que estava antes. */
    fun trocar(chave: String, valor: String): Lembranca = Lembranca(mapa.put(chave, valor))

    /** Cópia instantânea; as lembradas "sem valor" vêm com null (C.5). */
    fun copia(): Map<String, String?> {
        val copia = LinkedHashMap<String, String?>(mapa.size * 2)
        for ((chave, bruto) in mapa) copia[chave] = bruto as? String
        return copia
    }

    fun limpar() = mapa.clear()

    val tamanho: Int get() = mapa.size
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
