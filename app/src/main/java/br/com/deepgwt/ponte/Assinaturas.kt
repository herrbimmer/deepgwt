// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

/**
 * As chaves que a página assinou, guardadas EXATAMENTE como vieram (o apelido, se veio pelo
 * apelido). Kotlin puro; seguro entre threads (a página assina na thread do WebView, o carro entrega
 * nas threads do motor, a principal limpa na carga).
 */
class Assinaturas {

    private val chaves = LinkedHashSet<String>()

    @Synchronized
    fun adicionar(chave: String) {
        chaves.add(chave)
    }

    @Synchronized
    fun remover(chave: String) {
        chaves.remove(chave)
    }

    @Synchronized
    fun limpar() {
        chaves.clear()
    }

    @Synchronized
    fun contem(chave: String): Boolean = chave in chaves

    @Synchronized
    fun todas(): List<String> = chaves.toList()

    @Synchronized
    fun vazia(): Boolean = chaves.isEmpty()

    /**
     * Com que nome entregar um valor da chave canônica [canonica] (seção 7.1): o apelido, se a página
     * assinou o apelido; senão a canônica, se assinou a canônica; senão null. Assinou os dois: só o
     * apelido recebe. Uma chave com nome de apelido que chegue do carro (o carro também publica
     * `car.basic.speed`, `car.basic.gear`…) não é a canônica e não vai a ninguém: quem assinou o
     * apelido pediu a canônica.
     */
    @Synchronized
    fun nomeParaEntrega(canonica: String): String? {
        if (canonica in ContratoV1.APELIDOS) return null
        val apelido = ContratoV1.APELIDO_DA_CANONICA[canonica]
        if (apelido != null && apelido in chaves) return apelido
        if (canonica in chaves) return canonica
        return null
    }

    /** A página recebe [canonica] por algum nome (ela mesma ou o apelido)? */
    fun recebe(canonica: String): Boolean = nomeParaEntrega(canonica) != null
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
