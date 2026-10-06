// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ponte

/** Um script para a página e quem quer a resposta dele. */
class MensagemParaPagina(val js: String, val resposta: ((String?) -> Unit)? = null)

/**
 * A fila das mensagens "fila" da seção 5 e o estado de carga da página. Kotlin puro.
 *
 * Enquanto a página não terminou de carregar, as mensagens esperam (até [limite]; acima disso sai a
 * mais velha) e vão, na ordem, no fim da carga. Uma carga nova pedida descarta o que esperava.
 * Usada só na thread principal.
 */
class FilaDaPagina(private val limite: Int = ContratoV1.LIMITE_DA_FILA) {

    private val pendentes = ArrayDeque<MensagemParaPagina>()

    /** A página terminou de carregar (e nenhuma carga nova foi pedida depois). */
    var carregada: Boolean = false
        private set

    val tamanho: Int get() = pendentes.size

    /** True: a página está carregada e a mensagem vai já. False: ficou guardada. */
    fun enviarOuGuardar(mensagem: MensagemParaPagina): Boolean {
        if (carregada) return true
        if (limite <= 0) return false
        while (pendentes.size >= limite) pendentes.removeFirst()
        pendentes.addLast(mensagem)
        return false
    }

    /** Carga nova pedida: "não carregada" e o que esperava é descartado. */
    fun antesDeCarregar() {
        carregada = false
        pendentes.clear()
    }

    /** Fim da carga: "carregada"; devolve o que esperava, na ordem, e esvazia. */
    fun aoTerminarDeCarregar(): List<MensagemParaPagina> {
        carregada = true
        val saida = pendentes.toList()
        pendentes.clear()
        return saida
    }
}

/**
 * O que a ponte manda no fim de cada carga (seção 11.2, itens 2, 6 e 10 — o projetor faz o resto).
 * Kotlin puro.
 */
object SequenciaDeCarga {

    /**
     * Na ordem: as garantias de compatibilidade (10.1; não para tema que exige ponte mais nova, que
     * nem deveria ter sido carregado), a fila pendente, a batida injetada.
     */
    fun fimDaCarga(
        minBridgeVersion: String?,
        pendentes: List<MensagemParaPagina>,
    ): List<MensagemParaPagina> {
        val saida = ArrayList<MensagemParaPagina>(pendentes.size + 2)
        if (ContratoV1.versaoDaPonteAceita(minBridgeVersion)) {
            saida.add(MensagemParaPagina(ScriptsDaPagina.compatibilidade))
        }
        saida.addAll(pendentes)
        saida.add(MensagemParaPagina(ScriptsDaPagina.batida))
        return saida
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
