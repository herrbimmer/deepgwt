// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers.asd

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Motor + câmbio automático de 8 marchas (relações estilo ZF 8HP) alimentado pela velocidade REAL
 * do veículo. A velocidade não é simulada — ela vem do barramento e é verdade absoluta; o que se
 * simula aqui é a resposta do conjunto motor/conversor/câmbio a ela.
 *
 * ## O QUE MUDOU NESTA REVISÃO, E POR QUÊ
 *
 * A queixa era que a troca de marcha acontece "de maneira excessivamente instantânea". A causa não
 * era a duração: já havia 0,22 s de janela na subida. Era o fato de que, dentro dessa janela, a
 * ÚNICA coisa que mudava era a rotação deslizando de um valor para o outro. Carga (que no motor de
 * áudio controla volume e abertura do filtro) seguia exatamente a mesma, então o que se ouvia era
 * um glissando de tom — não uma troca de marcha. Um câmbio de verdade tem três coisas acontecendo
 * e as três se ouvem:
 *
 *  1. **Fase de torque** — a embreagem que entra começa a receber torque e o motor é aliviado. O
 *     som AFINA e ABAFA por um instante. É esse buraco que o ouvido lê como "trocou".
 *  2. **Fase de inércia** — a rotação cai (subida) ou sobe (redução) até casar com a relação nova.
 *  3. **Reengate** — o torque volta, com um empurrão curto acima do pedido do motorista.
 *
 * Então a troca virou uma máquina de estados de três fases ([Fase]), cada uma com o seu alvo de
 * carga, e o alisamento da carga fica MAIS RÁPIDO enquanto se troca — com a constante antiga
 * (~170 ms) um corte de 90 ms seria borrado até sumir, que é exatamente por que ele não existia
 * na prática.
 *
 * ## CONVERSOR DE TORQUE
 *
 * O acoplamento era rígido: rotação = velocidade × relação, desde os 0 km/h. Isso é um carro
 * elétrico com som de motor por cima — arrancar do zero subia o tom junto com o velocímetro, em
 * linha reta. Num automático de verdade o conversor PATINA: pisando parado o motor sobe sozinho
 * até a rotação de calço e só depois o carro começa a andar. [rpmComPatinagem] devolve isso, e a
 * patinagem fecha conforme a velocidade sobe.
 *
 * ## CADÊNCIA
 *
 * Os pontos de troca e a rapidez da embreagem saem de [cadencia], que o [AsdManager] mapeia a
 * partir do modo de condução real do carro. Eco troca cedo e devagar; Sport segura a marcha e
 * fecha a embreagem rápido.
 *
 * Nada aqui toca em Android: é aritmética pura, para poder ser testada sem emulador.
 */
class DrivetrainSimulator {
    companion object {
        private val GEAR_RATIOS = doubleArrayOf(4.714, 3.143, 2.106, 1.667, 1.285, 1.000, 0.839, 0.667)
        private const val FINAL_DRIVE = 3.42
        private const val WHEEL_RADIUS_M = 0.325

        private const val IDLE_RPM = 850.0
        private const val COLD_IDLE_RPM = 1550.0
        private const val COLD_START_DURATION_S = 10.0
        private const val PARTIDA_QUENTE_EXTRA_RPM = 380.0
        const val REDLINE_RPM = 6800.0
        private const val MAX_RPM = 7000.0

        /**
         * Rotação de calço do conversor: o quanto o motor sobe sozinho com o carro parado e o pé
         * no fundo, antes de a roda começar a girar. Valor de conversor de SUV médio.
         */
        private const val STALL_RPM = 2400.0

        /**
         * Velocidade (m/s) a partir da qual o conversor é considerado travado e a rotação volta a
         * ser puro produto de velocidade × relação. ~54 km/h.
         */
        private const val LOCKUP_SPEED_MS = 15.0

        /** Carga durante o corte de torque. Negativa: o motor está sendo ALIVIADO, não puxando. */
        private const val CARGA_NO_CORTE = -0.25

        /** Piso de carga no blip de redução — é o que dá o "vrum" da descida de marcha. */
        private const val CARGA_NO_BLIP = 0.55

        /** Quanto o motor ultrapassa a rotação da marcha nova no meio de uma redução. */
        private const val AMPLITUDE_DO_BLIP = 520.0

        /** Excedente de carga no reengate, acima do que o motorista está pedindo. */
        private const val EMPURRAO_NO_REENGATE = 0.22

        /** Alisamento da carga fora de troca, ao aliviar o pé (constante de tempo ~170 ms). */
        private const val ALISAMENTO_CARGA_NORMAL = 6.0

        /**
         * Alisamento da carga ao PISAR (~70 ms). Era o mesmo dos ~170 ms do alívio, e isso era a
         * maior parte do atraso entre o pé e o som: num motor de verdade a carga (e com ela o
         * volume e o brilho do ronco) responde ao acelerador quase na hora — quem demora é a
         * rotação, por inércia, e ela continua com o seu próprio alisamento.
         */
        private const val ALISAMENTO_CARGA_AO_PISAR = 14.0

        /**
         * Alisamento da carga DENTRO da troca (~45 ms). Sem esta separação o corte de torque, que
         * dura menos de 100 ms, chegaria ao motor de áudio como uma ondulação de 3% — inaudível.
         */
        private const val ALISAMENTO_CARGA_NA_TROCA = 22.0

        /** Quanto o corte do limitador derruba a rotação a cada batida. */
        private const val QUEDA_DO_LIMITADOR = 260.0

        /** Intervalo mínimo entre batidas do limitador, em segundos. */
        private const val INTERVALO_DO_LIMITADOR = 0.09

        /**
         * Abaixo desta velocidade (m/s, ~5 km/h) o câmbio não sobe de marcha.
         *
         * Sem esta trava, segurar o freio e acelerar em D fazia o motor passar do ponto de troca
         * parado e o câmbio subir as oito marchas com o carro imóvel — e, como parado a relação
         * não impõe rotação nenhuma, a rotação desabava para a marcha lenta no meio de cada troca.
         * O resultado era um motor que engasgava em vez de segurar giro contra o conversor, que é
         * o que um automático de verdade faz.
         */
        private const val VELOCIDADE_MINIMA_PARA_SUBIR_MS = 1.4

        /**
         * Teto de rotação que uma redução pode pedir. Um câmbio de verdade RECUSA a redução que
         * estouraria o motor — sem isso, um kickdown a 110 km/h pedia a 2ª, que naquela velocidade
         * daria mais de 9000 rpm, e o simulador ia até o corte para depois ser aparado pelo teto:
         * um berro sem relação nenhuma com o que o carro está fazendo.
         */
        private const val TETO_DE_REDUCAO_RPM = REDLINE_RPM - 300.0
    }

    /** Como o câmbio se comporta. Vem do modo de condução real do carro. */
    enum class Cadencia { ECO, NORMAL, SPORT }

    enum class Troca { NENHUMA, SUBIDA, REDUCAO }

    /** As três fases de uma troca de verdade. Ver o cabeçalho da classe. */
    enum class Fase { NENHUMA, TORQUE, INERCIA, REENGATE }

    var running = false
        private set
    var timeSinceStart = 0.0
        private set
    var gear = 1
        private set
    var engineRpm = 0.0
        private set
    var load = 0.0
        private set

    /** Cadência atual. Trocar no meio de uma troca não interrompe a troca em curso. */
    var cadencia: Cadencia = Cadencia.NORMAL

    var troca = Troca.NENHUMA
        private set
    var fase = Fase.NENHUMA
        private set

    /**
     * Quanto da rotação atual vem da patinagem do conversor, e não da roda. Publicado para a UI
     * poder mostrar que a arrancada está no conversor — e para os testes conseguirem afirmar isso
     * sem inferir de um número composto.
     */
    var patinagemRpm = 0.0
        private set

    /** true enquanto o limitador está cortando. */
    var noLimitador = false
        private set

    private var faseRestante = 0.0
    private var duracaoDaFase = 0.0
    private var marchaAlvo = 1
    private var rpmNoInicioDaFase = 0.0
    private var shiftCooldown = 0.0
    private var idleNoisePhase = Math.random() * 1000.0
    private var idleWalk = 0.0
    private var cargaPedida = 0.0
    private var tempoDesdeCorteDoLimitador = 0.0

    val coldStartActive: Boolean
        get() = running && timeSinceStart < COLD_START_DURATION_S

    /** true entre o começo do corte de torque e o fim do reengate. */
    val emTroca: Boolean
        get() = fase != Fase.NENHUMA

    val gearLabel: String
        get() = if (!running) "–" else gear.toString()

    /**
     * @param quente `true` quando o motor já rodou neste ciclo de ignição. No modo "só com o motor
     *        a combustão", o motor de um híbrido desliga e religa sozinho várias vezes por viagem;
     *        cada religada não é uma partida a frio — é um tranco curto de giro que assenta na
     *        marcha lenta, sem os 10 s de marcha lenta alta.
     */
    fun start(quente: Boolean = false) {
        running = true
        timeSinceStart = if (quente) COLD_START_DURATION_S else 0.0
        engineRpm = if (quente) IDLE_RPM + PARTIDA_QUENTE_EXTRA_RPM else COLD_IDLE_RPM
        gear = 1
        troca = Troca.NENHUMA
        fase = Fase.NENHUMA
        shiftCooldown = 0.0
        load = 0.0
        cargaPedida = 0.0
        patinagemRpm = 0.0
        noLimitador = false
    }

    fun stop() {
        running = false
        engineRpm = 0.0
        gear = 1
        troca = Troca.NENHUMA
        fase = Fase.NENHUMA
        patinagemRpm = 0.0
        noLimitador = false
    }

    private fun idleTarget(): Double {
        val decay = exp(-timeSinceStart / 3.2)
        return IDLE_RPM + (COLD_IDLE_RPM - IDLE_RPM) * decay
    }

    private fun idleNoise(dt: Double): Double {
        idleNoisePhase += dt
        val s1 = sin((idleNoisePhase * 2 * PI) / 0.65) * 14
        val s2 = sin((idleNoisePhase * 2 * PI) / 1.9 + 1.3) * 10
        idleWalk += (Math.random() - 0.5) * 6
        idleWalk *= 0.92
        idleWalk = idleWalk.coerceIn(-25.0, 25.0)
        return s1 + s2 + idleWalk
    }

    /**
     * Rotação em que o câmbio sobe de marcha. Eco sobe cedo e quase não estica; Sport segura.
     * Os limites de pé no fundo (4400 / 6100 / 6500) ficam abaixo do corte, senão a marcha
     * trocaria só quando o limitador já estivesse batendo.
     */
    internal fun rpmDeSubida(throttle: Double): Double = when (cadencia) {
        Cadencia.ECO -> 1750 + throttle * 2650
        Cadencia.NORMAL -> 2200 + throttle * 3900
        Cadencia.SPORT -> 2800 + throttle * 3700
    }

    internal fun rpmDeReducao(throttle: Double, desacelerando: Boolean): Double = when (cadencia) {
        // Eco desce cedo para voltar a uma marcha longa; Sport só desce quando a rotação já caiu
        // de verdade, e desce mais cedo em desaceleração para deixar o freio-motor presente.
        Cadencia.ECO -> if (desacelerando) 1050.0 else 900 + throttle * 320
        Cadencia.NORMAL -> if (desacelerando) 1250.0 else 950 + throttle * 400
        Cadencia.SPORT -> if (desacelerando) 1650.0 else 1150 + throttle * 520
    }

    /**
     * Durações das três fases, em segundos. A troca inteira vai de ~0,25 s (pé no fundo, Sport) a
     * ~0,8 s (pé leve, Eco) — é a diferença entre um tranco seco e uma passagem macia, e as duas
     * existem no mesmo câmbio.
     */
    internal fun duracoesDaTroca(tipo: Troca, throttle: Double): Triple<Double, Double, Double> {
        val agressividade = when (cadencia) {
            Cadencia.ECO -> 0.15
            Cadencia.NORMAL -> 0.5
            Cadencia.SPORT -> 1.0
        }
        // Pé fundo e Sport puxam para o extremo rápido; os dois pesam igual.
        val rapidez = (throttle.coerceIn(0.0, 1.0) * 0.5 + agressividade * 0.5).coerceIn(0.0, 1.0)
        return if (tipo == Troca.SUBIDA) {
            Triple(
                0.10 - 0.05 * rapidez,
                0.42 - 0.28 * rapidez,
                0.17 - 0.10 * rapidez
            )
        } else {
            Triple(
                0.08 - 0.04 * rapidez,
                0.30 - 0.17 * rapidez,
                0.19 - 0.11 * rapidez
            )
        }
    }

    private fun iniciarTroca(tipo: Troca, alvo: Int, throttle: Double) {
        troca = tipo
        marchaAlvo = alvo
        val (torque, _, _) = duracoesDaTroca(tipo, throttle)
        fase = Fase.TORQUE
        duracaoDaFase = torque
        faseRestante = torque
        rpmNoInicioDaFase = engineRpm
    }

    /**
     * Avança o relógio das fases. A marcha só troca de verdade no fim da fase de inércia — que é
     * onde a embreagem nova assume —, não no começo da troca nem no fim dela.
     */
    private fun avancarFases(dt: Double, throttle: Double) {
        faseRestante -= dt
        // `while` e sobra carregada, não um passo por tick: um tick atrasado (a thread do ASD já
        // foi vista perder agendamento) entregaria um dt maior que uma fase inteira, e descartar a
        // sobra esticaria a troca em vez de encurtá-la — o defeito seria "às vezes a marcha demora"
        // sem nada no código que diga por quê.
        while (faseRestante <= 0 && fase != Fase.NENHUMA) {
            val sobra = -faseRestante
            val (_, inercia, reengate) = duracoesDaTroca(troca, throttle)
            when (fase) {
                Fase.TORQUE -> {
                    fase = Fase.INERCIA
                    duracaoDaFase = inercia
                    faseRestante = inercia - sobra
                    rpmNoInicioDaFase = engineRpm
                }
                Fase.INERCIA -> {
                    gear = marchaAlvo
                    fase = Fase.REENGATE
                    duracaoDaFase = reengate
                    faseRestante = reengate - sobra
                    rpmNoInicioDaFase = engineRpm
                }
                Fase.REENGATE -> {
                    fase = Fase.NENHUMA
                    troca = Troca.NENHUMA
                    duracaoDaFase = 0.0
                    faseRestante = 0.0
                    // Respiro proporcional: em Sport o câmbio já pode trocar de novo quase em seguida.
                    shiftCooldown = if (cadencia == Cadencia.SPORT) 0.16 else 0.28
                }
                Fase.NENHUMA -> Unit
            }
        }
    }

    /** 0 no começo da fase, 1 no fim. Usado para desenhar a rampa de rotação dentro da fase. */
    private fun progressoDaFase(): Double {
        if (duracaoDaFase <= 0) return 1.0
        return (1 - faseRestante / duracaoDaFase).coerceIn(0.0, 1.0)
    }

    /**
     * Rotação que a roda imporia numa dada marcha, com a patinagem do conversor somada.
     *
     * Parado e pisando, [rpmDaRoda] é zero e o motor sobe até [STALL_RPM] — é o conversor
     * patinando, e é o que faz a arrancada soar como um automático em vez de um elétrico com som
     * por cima. A patinagem fecha linearmente até [LOCKUP_SPEED_MS].
     */
    internal fun rpmComPatinagem(rpmAcoplada: Double, speedMs: Double, demanda: Double): Double {
        val travamento = (speedMs / LOCKUP_SPEED_MS).coerceIn(0.0, 1.0)
        val patinagem = (1 - travamento) * demanda.coerceIn(0.0, 1.0)
        val folga = (STALL_RPM - rpmAcoplada).coerceAtLeast(0.0)
        return rpmAcoplada + folga * patinagem
    }

    private fun rpmDaRoda(speedMs: Double): Double = (speedMs / (2 * PI * WHEEL_RADIUS_M)) * 60

    /**
     * A marcha mais baixa aceitável a partir de [desejada], ou `null` se nem uma única redução
     * couber na velocidade atual.
     *
     * Sobe de volta enquanto a relação estouraria [TETO_DE_REDUCAO_RPM]: é assim que um kickdown a
     * 110 km/h vira "duas marchas abaixo se couber, senão uma, senão nenhuma", em vez de pedir a 2ª
     * e jogar o motor contra o corte.
     */
    internal fun marchaDeReducao(desejada: Int, speedMs: Double): Int? {
        if (gear <= 1) return null
        var alvo = desejada.coerceIn(1, gear - 1)
        val roda = rpmDaRoda(speedMs)
        while (alvo < gear && roda * GEAR_RATIOS[alvo - 1] * FINAL_DRIVE > TETO_DE_REDUCAO_RPM) {
            alvo++
        }
        return if (alvo < gear) alvo else null
    }

    /**
     * @param dt delta de tempo em segundos desde a última chamada.
     * @param throttle posição do pedal do acelerador, 0..1 (real ou estimada).
     * @param brake intensidade de frenagem/regeneração, 0..1 (0 se não houver essa telemetria).
     * @param speedKmh velocidade real do veículo em km/h, vinda do barramento.
     * @param inDrive true se a marcha selecionada for "D" (avanço); false trata como
     *        neutro/estacionado, e o motor só responde ao acelerador (sem acoplamento à roda).
     */
    fun update(dt: Double, throttle: Double, brake: Double, speedKmh: Double, inDrive: Boolean) {
        if (!running || dt <= 0) return
        timeSinceStart += dt
        // Um NaN do barramento (leitura "NaN" é número válido para `toDouble`) entraria na rotação e
        // ficaria: `coerceIn` e `+=` preservam NaN, e só reiniciar o simulador o limpava.
        val speedMs = (if (speedKmh.isFinite()) speedKmh else 0.0) / 3.6
        val pedal = (if (throttle.isFinite()) throttle else 0.0).coerceIn(0.0, 1.0)
        val freio = (if (brake.isFinite()) brake else 0.0).coerceIn(0.0, 1.0)

        val desacelerando = freio > 0.05 || (pedal < 0.03 && speedMs > 1)
        if (shiftCooldown > 0) shiftCooldown -= dt

        if (inDrive) {
            if (fase == Fase.NENHUMA && shiftCooldown <= 0) {
                val kickdown = pedal > 0.92 && engineRpm < 3600 && gear > 1
                val podeSubir = gear < 8 && speedMs > VELOCIDADE_MINIMA_PARA_SUBIR_MS
                when {
                    kickdown -> marchaDeReducao(gear - 2, speedMs)?.let {
                        iniciarTroca(Troca.REDUCAO, it, pedal)
                    }
                    podeSubir && engineRpm > rpmDeSubida(pedal) ->
                        iniciarTroca(Troca.SUBIDA, gear + 1, pedal)
                    engineRpm < rpmDeReducao(pedal, desacelerando) ->
                        marchaDeReducao(gear - 1, speedMs)?.let {
                            iniciarTroca(Troca.REDUCAO, it, pedal)
                        }
                }
            } else if (fase != Fase.NENHUMA) {
                avancarFases(dt, pedal)
            }
        } else if (fase != Fase.NENHUMA) {
            // Saiu de D no meio de uma troca: encerra a troca em vez de deixá-la pendurada.
            fase = Fase.NENHUMA
            troca = Troca.NENHUMA
        }

        atualizarRotacao(dt, pedal, speedMs, inDrive)
        atualizarCarga(dt, pedal, freio, speedMs, inDrive)
    }

    private fun atualizarRotacao(dt: Double, pedal: Double, speedMs: Double, inDrive: Boolean) {
        val rodaRpm = rpmDaRoda(speedMs)
        val marcaLenta = idleTarget() + idleNoise(dt)

        val rpmDaMarchaAtual = max(marcaLenta, rodaRpm * GEAR_RATIOS[gear - 1] * FINAL_DRIVE)
        val rpmDaMarchaAlvo =
            max(marcaLenta, rodaRpm * GEAR_RATIOS[marchaAlvo - 1] * FINAL_DRIVE)

        when {
            fase == Fase.TORQUE -> {
                // O motor ainda está na marcha velha; o alívio de torque só tira a pressão, então
                // a rotação praticamente para de subir em vez de despencar.
                val alvo = rpmDaMarchaAtual
                engineRpm += (alvo - engineRpm) * min(1.0, dt * 4)
            }
            fase == Fase.INERCIA -> {
                // A rampa é desenhada explicitamente entre onde a rotação estava e onde a relação
                // nova a coloca. Um lerp exponencial (o que havia antes) nunca CHEGA — sobrava um
                // degrau no fim da janela, justo onde a embreagem deveria já estar casada.
                val p = progressoDaFase()
                // Ease-out: o grosso da variação acontece cedo, como numa embreagem que assume.
                val e = 1 - (1 - p) * (1 - p)
                val rampa = rpmNoInicioDaFase + (rpmDaMarchaAlvo - rpmNoInicioDaFase) * e
                // O blip é somado POR FORA da rampa, não embutido no destino. Embutido, ele era
                // multiplicado pelo mesmo `e` da rampa e encolhia para três quartos justo no pico —
                // a ultrapassagem que se quer ouvir quase sumia. Por fora, o pico é exatamente
                // [AMPLITUDE_DO_BLIP] acima da rampa e o fim da fase continua caindo em cheio na
                // rotação da marcha nova, porque sin(pi) é zero.
                engineRpm = if (troca == Troca.REDUCAO) {
                    rampa + sin(p * PI) * AMPLITUDE_DO_BLIP
                } else {
                    rampa
                }
            }
            fase == Fase.REENGATE -> {
                val alvo = rpmDaMarchaAtual
                engineRpm += (alvo - engineRpm) * min(1.0, dt * 12)
            }
            !inDrive -> {
                // Neutro/estacionado: nada segura o motor, então o pedal leva a rotação até onde
                // quiser. É o único caso em que acelerar parado sobe até perto do corte.
                val livre = marcaLenta + pedal * (MAX_RPM - marcaLenta) * 0.9
                val alisamento = if (pedal > 0.05) 6.0 else 4.0
                engineRpm += (livre - engineRpm) * min(1.0, dt * alisamento)
            }
            speedMs < 0.6 -> {
                // Em D e parado (pé no freio, acelerando): quem manda é o CONVERSOR, não o pedal.
                // O motor sobe até a rotação de calço e para ali — é o "segurar contra o freio" de
                // um automático. Deixar o pedal mandar aqui, como antes, fazia o motor ir a quase
                // 6400 rpm com o carro imóvel, que nenhum automático faz.
                val alvo = rpmComPatinagem(marcaLenta, speedMs, pedal)
                val alisamento = if (pedal > 0.05) 6.0 else 4.0
                engineRpm += (alvo - engineRpm) * min(1.0, dt * alisamento)
            }
            else -> {
                val comPatinagem = rpmComPatinagem(rpmDaMarchaAtual, speedMs, pedal)
                val alvo = max(marcaLenta, comPatinagem)
                val alisamento = if (pedal > 0.5) 9.0 else 6.0
                engineRpm += (alvo - engineRpm) * min(1.0, dt * alisamento)
            }
        }

        patinagemRpm = if (fase == Fase.NENHUMA && inDrive && speedMs >= 0.6) {
            (rpmComPatinagem(rpmDaMarchaAtual, speedMs, pedal) - rpmDaMarchaAtual).coerceAtLeast(0.0)
        } else if (fase == Fase.NENHUMA && inDrive) {
            (rpmComPatinagem(marcaLenta, speedMs, pedal) - marcaLenta).coerceAtLeast(0.0)
        } else {
            0.0
        }

        aplicarLimitador(dt)
        engineRpm = engineRpm.coerceIn(IDLE_RPM * 0.55, MAX_RPM)
    }

    /**
     * Corte de giro. Um limitador de verdade não é um teto: ele corta, a rotação cai algumas
     * centenas de giros, o corte solta e ela volta a subir — a batida característica. Um `coerceIn`
     * puro deixaria o som parado numa nota só, que é o que denuncia simulação.
     */
    private fun aplicarLimitador(dt: Double) {
        tempoDesdeCorteDoLimitador += dt
        if (engineRpm < REDLINE_RPM) {
            noLimitador = false
            return
        }
        noLimitador = true
        if (tempoDesdeCorteDoLimitador < INTERVALO_DO_LIMITADOR) return
        tempoDesdeCorteDoLimitador = 0.0
        engineRpm -= QUEDA_DO_LIMITADOR
    }

    private fun atualizarCarga(
        dt: Double,
        pedal: Double,
        freio: Double,
        speedMs: Double,
        inDrive: Boolean
    ) {
        // O que o motorista está pedindo, antes de o câmbio opinar.
        cargaPedida = if (pedal > 0.02) {
            pedal
        } else {
            val freioMotor = if (speedMs > 0.3) 0.35 else 0.05
            -max(freio, freioMotor)
        }

        val alvo = when {
            noLimitador -> CARGA_NO_CORTE
            fase == Fase.TORQUE -> CARGA_NO_CORTE
            fase == Fase.INERCIA ->
                if (troca == Troca.REDUCAO) max(cargaPedida, CARGA_NO_BLIP) else CARGA_NO_CORTE
            fase == Fase.REENGATE -> {
                // O empurrão nasce cheio e se apaga ao longo da fase, senão o reengate soaria
                // como um degrau e não como a retomada do torque.
                val restante = 1 - progressoDaFase()
                (cargaPedida + EMPURRAO_NO_REENGATE * restante).coerceAtMost(1.0)
            }
            else -> cargaPedida
        }

        val k = when {
            fase != Fase.NENHUMA || noLimitador -> ALISAMENTO_CARGA_NA_TROCA
            alvo > load -> ALISAMENTO_CARGA_AO_PISAR
            else -> ALISAMENTO_CARGA_NORMAL
        }
        load += (alvo - load) * min(1.0, dt * k)
        load = load.coerceIn(-1.0, 1.0)
        if (!inDrive && abs(load) < 1e-6) load = 0.0
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
