// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.carro

/**
 * O que fazer com a função de fábrica de UM botão coringa do volante. Puro: testável na JVM.
 *
 * COMO O CARRO GUARDA A FUNÇÃO
 * Cada coringa tem uma chave em `Settings.System` (`bean_sw_custom_key1_config`, `…key2…`) com a
 * função escolhida no menu do carro. [DESATIVADO] (99) tira a função de fábrica: o botão para de
 * fazer o que fazia e o `inputservice` passa a entregar a tecla ao DeepGWT.
 *
 * AS TRÊS PROMESSAS
 *  1. Reprogramação desligada (ou botão em "função do carro") e o app nunca tocou no botão: NADA.
 *     Nem lê, nem grava.
 *  2. Ao tomar o botão, o app guarda o valor que a pessoa escolheu no menu do carro ANTES de
 *     gravar 99 — e guarda uma vez só (tomar de novo não sobrescreve com o 99 do próprio app).
 *  3. Ao soltar (desligar a reprogramação, voltar o botão para "função do carro", perder o nível),
 *     devolve exatamente o valor guardado. Chave que não existia volta a não existir.
 *
 * E SE A PESSOA MUDAR A FUNÇÃO NO MENU DO CARRO COM O BOTÃO TOMADO?
 * O valor deixa de ser 99. Isso é a pessoa dizendo qual função de fábrica quer — passa a ser a
 * guardada, e o app grava 99 de novo (a reprogramação continua ligada). Quando ela desligar, volta
 * a função nova, e não a antiga.
 */
object PlanoDoCoringa {
    const val DESATIVADO = "99"

    /** Como `settings get` responde para uma chave que não existe. */
    const val AUSENTE = "null"

    data class Estado(val tomado: Boolean, val original: String?)

    sealed class Passo {
        object Nada : Passo()
        /** Guardar [original] (null = a chave não existia) e gravar 99. */
        data class Tomar(val original: String?) : Passo()
        /** A pessoa trocou a função no menu do carro: [novoOriginal] passa a ser a guardada; 99 de novo. */
        data class Retomar(val novoOriginal: String?) : Passo()
        /** Devolver [valor] (null = apagar a chave) e esquecer. */
        data class Devolver(val valor: String?) : Passo()
    }

    /** O valor lido de `settings get`, com "null"/vazio virando null. */
    fun normalizar(lido: String?): String? {
        val v = lido?.trim().orEmpty()
        return if (v.isEmpty() || v.equals(AUSENTE, ignoreCase = true)) null else v
    }

    /**
     * @param desejado o DeepGWT quer este botão (reprogramação ligada, nível libera, ação ≠ carro).
     * @param atual o valor lido agora (já [normalizar]); só é usado quando [desejado].
     */
    fun planejar(desejado: Boolean, estado: Estado, atual: String?): Passo = when {
        desejado && !estado.tomado -> Passo.Tomar(atual)
        desejado && atual != DESATIVADO -> Passo.Retomar(atual)
        desejado -> Passo.Nada
        estado.tomado -> Passo.Devolver(estado.original)
        else -> Passo.Nada
    }

    /** Valor que pode ir para `settings put` e para o arquivo que o instalador lê ao remover. */
    fun valorSeguro(v: String?): Boolean = v == null || Regex("^-?[0-9]{1,6}$").matches(v)
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
