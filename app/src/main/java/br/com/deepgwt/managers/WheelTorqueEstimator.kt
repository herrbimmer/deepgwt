// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers

import kotlin.math.abs

/**
 * Torque de tração nas rodas, em Nm, deduzido do que o carro REALMENTE faz.
 *
 * ## Por que não é P/ω
 *
 * A conta óbvia é torque = potência / velocidade angular, e era o que esta tela fazia usando
 * `car.ev_info.motor_speed` como ω. Nesta central isso não produz número nenhum: na captura do
 * próprio veículo (knowledge/session_2026-09-15/raw_http/telemetry.json) o bloco do motor elétrico
 * vem inválido — `motor_power = -1001` (o sentinela de "sem leitura"), `motor_speed = 0` e
 * `rear_motor_speed` vazia. Com ω = 0 a conta diverge, o piso de rotação corta, e o mostrador mostra
 * um traço. Para sempre, dirigindo ou parado.
 *
 * Pior: mesmo que ω respondesse, P vem do PACOTE de tração. Num híbrido com o motor a combustão
 * puxando, o pacote pode estar em zero (ou negativo, carregando) enquanto o carro acelera forte — e
 * a conta diria "sem torque" no exato momento de mais torque.
 *
 * ## O que esta classe faz
 *
 * Mede o EFEITO em vez da causa. A segunda lei de Newton aplicada ao carro inteiro: a força que
 * chega ao chão é a que acelera a massa mais a que vence o que se opõe ao movimento.
 *
 *     F = m·a  +  ½·ρ·CdA·v²  +  Crr·m·g
 *     τ_roda = F · r
 *
 * Isso cobre os dois motores de uma vez, porque não pergunta de onde veio a força — pergunta quanta
 * foi. Funciona a partir do zero (é na arrancada que `m·a` domina), que é justamente onde P/ω não
 * funciona.
 *
 * ## O que ele NÃO sabe, e é honesto dizer
 *
 *  - **Inclinação.** Não há sinal de rampa nesta central. Subindo, o torque real é maior do que o
 *    calculado; descendo, menor. É o erro conhecido deste modelo.
 *  - **Massa real.** [MASSA_KG] é a massa em ordem de marcha do H6 GT híbrido, sem passageiros nem
 *    carga. Quatro adultos e bagagem são ~15% a mais, e o número sai ~15% baixo na mesma medida.
 *  - **Pneu.** [RAIO_DA_RODA_M] é 225/55 R19, o de fábrica. Trocar a medida do pneu escala o
 *    resultado na mesma proporção do raio.
 *
 * Por isso o número é apresentado como ESTIMATIVA na tela, nunca como medição — a mesma disciplina
 * que o resto do produto já usa para o torque do HUD.
 *
 * ## Ruído
 *
 * `car.basic.vehicle_speed` chega com uma casa decimal, então um degrau de 0,1 km/h já é
 * 0,028 m/s. Derivar dois pontos vizinhos transforma esse degrau em ~0,3 m/s² de aceleração falsa,
 * que viram centenas de Nm de ponteiro tremendo. A derivada aqui é a INCLINAÇÃO DE UMA RETA ajustada
 * por mínimos quadrados sobre uma janela de [JANELA_MS], que usa todos os pontos em vez de dois — e
 * o resultado ainda passa por um filtro exponencial antes de ir para a tela.
 */
class WheelTorqueEstimator {

    private val tempoMs = ArrayDeque<Long>()
    private val velocidadeMs = ArrayDeque<Double>()

    /** Torque já filtrado, em Nm, com sinal. Negativo = o carro está sendo freado. */
    private var suavizado: Double? = null

    private var ultimaAmostraMs: Long = 0L

    /** Descarta tudo. Usado pelo botão "Zerar" e quando o sinal de velocidade some. */
    fun reset() {
        tempoMs.clear()
        velocidadeMs.clear()
        suavizado = null
        ultimaAmostraMs = 0L
    }

    /**
     * Entrega uma leitura de velocidade. [agoraMs] é um relógio monotônico (elapsedRealtime), não a
     * hora do dia: a conta é uma derivada no tempo e um ajuste de fuso a estragaria.
     */
    fun sample(velocidadeKmh: Float?, agoraMs: Long) {
        val v = velocidadeKmh?.toDouble()
        if (v == null || !v.isFinite() || v < 0.0 || v > VELOCIDADE_IMPLAUSIVEL_KMH) return

        // Uma pausa longa entre amostras significa que a tela esteve fechada ou o sinal sumiu. A
        // janela antiga não descreve mais o presente e uma derivada por cima dela seria um degrau
        // inventado.
        if (ultimaAmostraMs != 0L && agoraMs - ultimaAmostraMs > LACUNA_MAXIMA_MS) reset()
        ultimaAmostraMs = agoraMs

        tempoMs.addLast(agoraMs)
        velocidadeMs.addLast(v / 3.6)
        while (tempoMs.size > 2 && agoraMs - tempoMs.first() > JANELA_MS) {
            tempoMs.removeFirst()
            velocidadeMs.removeFirst()
        }

        val a = aceleracaoMs2() ?: return
        val vAtual = velocidadeMs.last()
        val cru = torqueDe(vAtual, a)
        val anterior = suavizado
        suavizado = if (anterior == null) cru else anterior + SUAVIZACAO * (cru - anterior)
    }

    /** Torque de tração estimado, em Nm, ou `null` enquanto não houver base para estimar. */
    fun torqueNm(): Float? = suavizado?.takeIf { it.isFinite() }?.toFloat()

    /**
     * Força resultante × raio. Rolagem e arrasto só entram com o carro andando: parado ele não
     * precisa de torque nenhum para ficar parado, e somar a resistência de rolamento ali daria um
     * valor de repouso que não existe.
     */
    internal fun torqueDe(velocidadeMs: Double, aceleracaoMs2: Double): Double {
        val andando = velocidadeMs > VELOCIDADE_MINIMA_PARA_RESISTENCIA_MS
        val inercia = MASSA_KG * aceleracaoMs2
        val arrasto = if (andando) 0.5 * DENSIDADE_DO_AR * CDA * velocidadeMs * velocidadeMs else 0.0
        val rolagem = if (andando) CRR * MASSA_KG * GRAVIDADE else 0.0
        return (inercia + arrasto + rolagem) * RAIO_DA_RODA_M
    }

    /**
     * Inclinação da reta de mínimos quadrados sobre a janela, em m/s².
     *
     * `null` enquanto a janela for curta demais para significar alguma coisa — duas amostras
     * coladas dizem mais sobre a resolução do sinal do que sobre a aceleração do carro.
     */
    internal fun aceleracaoMs2(): Double? {
        val n = tempoMs.size
        if (n < AMOSTRAS_MINIMAS) return null
        val t0 = tempoMs.first()
        val span = (tempoMs.last() - t0).toDouble()
        if (span < VAO_MINIMO_MS) return null

        var somaT = 0.0
        var somaV = 0.0
        for (i in 0 until n) {
            somaT += (tempoMs.elementAt(i) - t0) / 1000.0
            somaV += velocidadeMs.elementAt(i)
        }
        val mediaT = somaT / n
        val mediaV = somaV / n

        var numerador = 0.0
        var denominador = 0.0
        for (i in 0 until n) {
            val dt = (tempoMs.elementAt(i) - t0) / 1000.0 - mediaT
            numerador += dt * (velocidadeMs.elementAt(i) - mediaV)
            denominador += dt * dt
        }
        if (abs(denominador) < 1e-9) return null
        return numerador / denominador
    }

    companion object {
        /** Massa em ordem de marcha do H6 GT híbrido, em kg. Sem ocupantes nem carga. */
        const val MASSA_KG = 1900.0

        /** Raio dinâmico do pneu de fábrica 225/55 R19, em metros: 19"/2 + 225 × 0,55. */
        const val RAIO_DA_RODA_M = 0.365

        /** Coeficiente de arrasto × área frontal, em m². ~0,35 × ~2,45 para um SUV deste porte. */
        const val CDA = 0.86

        /** Resistência ao rolamento de pneu de passeio em asfalto. */
        const val CRR = 0.012

        const val DENSIDADE_DO_AR = 1.20
        const val GRAVIDADE = 9.81

        /** Janela da derivada, em ms. Curta o bastante para a arrancada aparecer inteira. */
        const val JANELA_MS = 600L

        /** Vão mínimo entre a primeira e a última amostra da janela, em ms. */
        const val VAO_MINIMO_MS = 200.0

        const val AMOSTRAS_MINIMAS = 3

        /** Pausa entre amostras que invalida a janela, em ms. */
        const val LACUNA_MAXIMA_MS = 2000L

        /** Peso do valor novo no filtro exponencial. */
        const val SUAVIZACAO = 0.35

        /** Abaixo disto o carro está parado e não vence resistência nenhuma, em m/s. */
        const val VELOCIDADE_MINIMA_PARA_RESISTENCIA_MS = 0.3

        /** Acima disto a leitura de velocidade é lixo do barramento, em km/h. */
        const val VELOCIDADE_IMPLAUSIVEL_KMH = 400.0
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
