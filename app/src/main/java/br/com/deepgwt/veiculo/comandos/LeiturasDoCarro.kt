// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.comandos

import br.com.deepgwt.veiculo.Veiculo

/**
 * Leituras derivadas que antes moravam no motor herdado (motor-comandos.md §1.3, §D.1 e §F).
 *
 * A especificação sugere `br.com.deepgwt.veiculo.LeiturasDoCarro`; mora aqui porque a sala limpa só
 * escreve nos subpacotes do motor. As regras são puras; as versões sem parâmetro leem o cache.
 */
object LeiturasDoCarro {

    private const val ESTADO_DO_MOTOR = "car.basic.engine_state"
    private const val ODOMETRO_TOTAL = "car.basic.total_odometer"

    /** Valores de `engine_state` com a tela principal apagada (F.1). */
    private val TELA_APAGADA = setOf("-1", "15", "14", "10")

    /** Valores de `engine_state` com a ignição ligada (F.2). 11 = READY (o 13 nunca foi visto). */
    private val IGNICAO_LIGADA = setOf("11", "12", "13")

    /** Separadores de um vetor do carro: vírgula, ponto e vírgula ou espaço, um ou mais. */
    private val SEPARADORES = Regex("[,;\\s]+")

    /** Pares de delimitadores aceitos nas pontas de um vetor. */
    private val DELIMITADORES = listOf('{' to '}', '[' to ']', '(' to ')')

    /** F.1 sobre o valor atual de `car.basic.engine_state` (cache); leitura que lança = ligada. */
    @JvmStatic
    fun telaPrincipalLigada(): Boolean = telaPrincipalLigadaPor { Veiculo.ler(ESTADO_DO_MOTOR) }

    /** F.1: null e -1/15/14/10 = apagada; qualquer outro texto = ligada. */
    @JvmStatic
    fun telaPrincipalLigada(estadoDoMotor: String?): Boolean =
        estadoDoMotor != null && estadoDoMotor !in TELA_APAGADA

    /** F.2: só 11, 12 e 13 (o resto, inclusive null e desconhecidos, é desligada). */
    @JvmStatic
    fun ignicaoLigada(estadoDoMotor: String?): Boolean =
        estadoDoMotor != null && estadoDoMotor in IGNICAO_LIGADA

    /** F.3: `car.basic.total_odometer` como inteiro; nulo, vazio ou não inteiro → 0. */
    @JvmStatic
    fun quilometragemTotal(): Int = quilometragemTotalPor { Veiculo.ler(ODOMETRO_TOTAL) }

    /**
     * D.1: a posição [indice] de um vetor do carro (`{1,0,0,0,0,0}`) é diferente de zero? Null quando o
     * texto é vazio ou a posição falta ou não é número. Decimais são truncados.
     */
    @JvmStatic
    fun posicaoLigada(vetor: String?, indice: Int): Boolean? {
        if (vetor == null || indice < 0) return null
        var texto = vetor.trim()
        for ((abre, fecha) in DELIMITADORES) {
            if (texto.length >= 2 && texto.first() == abre && texto.last() == fecha) {
                texto = texto.substring(1, texto.length - 1).trim()
                break
            }
        }
        if (texto.isEmpty()) return null
        val pedacos = texto.split(SEPARADORES)
        val pedaco = pedacos.getOrNull(indice) ?: return null
        val numero = pedaco.toDoubleOrNull()?.takeIf { !it.isNaN() } ?: return null
        return numero.toLong() != 0L
    }

    // ── Versões com a leitura injetada (testes) ──

    internal fun telaPrincipalLigadaPor(ler: () -> String?): Boolean =
        try {
            telaPrincipalLigada(ler())
        } catch (e: Exception) {
            true
        }

    internal fun quilometragemTotalPor(ler: () -> String?): Int =
        try {
            ler()?.trim()?.toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
