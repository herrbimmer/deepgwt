// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

/**
 * O que mostrar na tela quando um gesto rápido mexe no ar-condicionado.
 *
 * ## Por que existe
 *
 * Os gestos de três dedos (o leitor de gestos rápidos do app) são
 * lidos direto do `/dev/input` e NÃO consomem o toque: ele segue para o app que estiver na frente.
 * Isso é de propósito e é o que permite o gesto valer em qualquer tela — mas significa que, do
 * ponto de vista de quem fez o gesto, a tela não muda nada. Se o app de trás não é o de clima (e
 * quase nunca é), o motorista arrasta três dedos e não vê absolutamente nada acontecer. Ele não tem
 * como saber se o carro entendeu, se o gesto foi curto demais, ou se a função está desligada — e
 * acaba repetindo o gesto, que é como a temperatura vai parar no teto.
 *
 * Este retrato é a confirmação. Ele descreve um evento, não um estado contínuo: aparece no instante
 * em que o gesto é reconhecido e some sozinho.
 *
 * ## Por que [carimbo] existe
 *
 * Dois gestos seguidos podem produzir o MESMO valor — subir a ventilação já no máximo, por exemplo.
 * Sem um campo que muda sempre, o segundo gesto seria um retrato igual ao primeiro, o Compose não
 * veria mudança nenhuma e a confirmação não reapareceria. Justamente no caso em que ela é mais
 * necessária: é aí que o motorista precisa saber que o carro entendeu e que não há mais para onde
 * ir.
 */
data class RetornoDeGesto(
    val tipo: Tipo,
    /** Valor DEPOIS do gesto, na unidade de [tipo]. */
    val valor: Float,
    val minimo: Float,
    val maximo: Float,
    /** Degraus pedidos pelo gesto, com sinal. Positivo = o gesto pediu mais. */
    val degraus: Int,
    /** `true` quando o valor não mudou porque já estava no fim do curso. */
    val noLimite: Boolean,
    /** Relógio monotônico do reconhecimento. Ver a nota sobre repetição acima. */
    val carimbo: Long,
    /**
     * Texto do cartão quando [tipo] é [Tipo.MACRO].
     *
     * Só a macro tem texto porque só ela não tem grandeza: temperatura e ventilação são um número
     * dentro de uma faixa, e o cartão delas é a barra. Uma macro é um nome — "Chegando em casa" —
     * e o que o motorista precisa ver é que ELA rodou, não um valor.
     */
    val texto: String? = null
) {
    enum class Tipo { TEMPERATURA, VENTILACAO, MACRO }

    /** Posição do valor dentro da faixa, 0..1 — o preenchimento da barra. */
    val fracao: Float
        get() = if (maximo > minimo) ((valor - minimo) / (maximo - minimo)).coerceIn(0f, 1f) else 0f
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
