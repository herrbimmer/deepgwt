// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers.asd

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * As escolhas do usuário para o ASD e a lógica pura que decide, a cada tick, se ele toca e o que
 * ele toca. Nada aqui toca em Android: é para poder ser testado sem emulador, como [AsdSignals] e
 * [DrivetrainSimulator].
 */

/** Qual som o ASD produz. */
enum class AsdPerfilDeSom(val rotulo: String) {
    /** As 12 amostras reais do motor a combustão, com o câmbio de 8 marchas simulado. */
    COMBUSTAO("Motor a combustão"),

    /** Zumbido sintetizado que sobe com a velocidade, no estilo dos elétricos esportivos. */
    ELETRICO("Zumbido elétrico");

    companion object {
        fun fromKey(key: String?): AsdPerfilDeSom = entries.firstOrNull { it.name == key } ?: COMBUSTAO
    }
}

/** Quando o ASD toca. */
enum class AsdAtivacao(val rotulo: String) {
    /** Com o carro pronto para rodar, independente do motor a combustão. Era o único modo até a 6.3. */
    READY("Com o carro ligado (READY)"),

    /** Só enquanto o motor a combustão estiver girando de verdade (`car.basic.engine_speed`). */
    MOTOR_A_COMBUSTAO("Só com o motor a combustão ligado");

    companion object {
        fun fromKey(key: String?): AsdAtivacao = entries.firstOrNull { it.name == key } ?: READY
    }
}

/** De onde sai a rotação do som de combustão. */
enum class AsdFonteDeRotacao(val rotulo: String) {
    /** Motor + câmbio de 8 marchas simulados a partir da velocidade e do acelerador. */
    SIMULADA("Simulada (câmbio de 8 marchas)"),

    /**
     * O giro real do motor a combustão quando ele está ligado; com ele desligado, volta à
     * simulada. É o que um ASD de fábrica faz: reforça o som do motor que está girando.
     */
    REAL("Giro real do motor a combustão");

    companion object {
        fun fromKey(key: String?): AsdFonteDeRotacao = entries.firstOrNull { it.name == key } ?: SIMULADA
    }
}

/** Cadência do câmbio simulado: seguir o modo de condução do carro, ou uma fixa. */
enum class AsdCadenciaEscolhida(val rotulo: String, val fixa: DrivetrainSimulator.Cadencia?) {
    AUTOMATICA("Segue o modo do carro", null),
    ECO("Eco", DrivetrainSimulator.Cadencia.ECO),
    NORMAL("Normal", DrivetrainSimulator.Cadencia.NORMAL),
    SPORT("Sport", DrivetrainSimulator.Cadencia.SPORT);

    companion object {
        fun fromKey(key: String?): AsdCadenciaEscolhida = entries.firstOrNull { it.name == key } ?: AUTOMATICA
    }
}

/**
 * O motor a combustão está girando agora?
 *
 * Mesma receita do [br.com.deepgwt.managers.PowerFlowMapper] e do
 * THEME_AUTHORING_GUIDE §7.2: giro real com histerese 400/150 rpm. `car.basic.engine_state` NÃO
 * serve — num híbrido ele fica em "13" a viagem inteira, com o motor ligando e desligando sozinho.
 *
 * Leitura ausente ou ilegível mantém o estado anterior: a chave só é publicada quando muda, e
 * tratar "não chegou nada" como "desligou" faria o som cair no meio de uma aceleração.
 */
class DetectorDeMotorACombustao(
    private val ligaAcimaDeRpm: Double = 400.0,
    private val desligaAbaixoDeRpm: Double = 150.0
) {
    var girando = false
        private set

    fun atualizar(rpmBruto: String?): Boolean {
        val rpm = rpmBruto?.trim()?.toDoubleOrNull()
        if (rpm == null || !rpm.isFinite() || rpm < 0.0) return girando
        girando = if (girando) rpm >= desligaAbaixoDeRpm else rpm > ligaAcimaDeRpm
        return girando
    }

    fun zerar() {
        girando = false
    }
}

object AsdDecisao {

    /** Acima disto o giro "real" é lixo do barramento. */
    const val RPM_REAL_MAXIMO_PLAUSIVEL = 8000.0

    /** Velocidade (km/h) abaixo da qual o carro conta como parado para "silenciar em P". */
    const val PARADO_KMH = 1.0

    /**
     * O ASD deve estar soando agora?
     *
     * @param ignicaoLigada o carro está pronto para rodar (`engine_state` 11, 12 ou 13).
     * @param motorACombustao o motor a combustão está girando (ver [DetectorDeMotorACombustao]).
     * @param emP a alavanca está em P.
     * @param velocidadeKmh velocidade real.
     */
    fun deveSoar(
        ativacao: AsdAtivacao,
        ignicaoLigada: Boolean,
        motorACombustao: Boolean,
        silenciarEmP: Boolean,
        emP: Boolean,
        velocidadeKmh: Double
    ): Boolean {
        if (!ignicaoLigada) return false
        if (ativacao == AsdAtivacao.MOTOR_A_COMBUSTAO && !motorACombustao) return false
        if (silenciarEmP && emP && velocidadeKmh < PARADO_KMH) return false
        return true
    }

    /**
     * Giro real utilizável, ou `null` quando o som deve cair para a simulação: motor a combustão
     * desligado, leitura ausente ou fora de escala.
     */
    fun rpmReal(fonte: AsdFonteDeRotacao, motorACombustao: Boolean, rpmBruto: String?): Double? {
        if (fonte != AsdFonteDeRotacao.REAL || !motorACombustao) return null
        val rpm = rpmBruto?.trim()?.toDoubleOrNull() ?: return null
        if (!rpm.isFinite() || rpm <= 0.0 || rpm > RPM_REAL_MAXIMO_PLAUSIVEL) return null
        return rpm
    }

    /**
     * Carga -1..1 quando o giro vem do motor real e o simulador não participa: pedal positivo,
     * frenagem/regeneração negativa, e um freio-motor leve com o pé fora em movimento — a mesma
     * convenção de `DrivetrainSimulator.load`.
     */
    fun cargaSemSimulador(acelerador: Double, freio: Double, velocidadeKmh: Double): Double {
        val pedal = acelerador.coerceIn(0.0, 1.0)
        if (pedal > 0.02) return pedal
        val freioMotor = if (velocidadeKmh > 1.0) 0.35 else 0.05
        return -maxOf(freio.coerceIn(0.0, 1.0), freioMotor)
    }
}

/**
 * Aritmética do motor de áudio que merece teste próprio. [AsdAudioEngine] chama isto dentro da
 * thread de render, então tudo aqui é barato e não aloca.
 */
object AsdDsp {

    /**
     * Crossfade de potência constante entre duas camadas vizinhas de RPM.
     *
     * O linear que havia antes (`1 - mix` / `mix`) soma 1 em AMPLITUDE, e isso só está certo para
     * sinais idênticos em fase. Duas gravações de rotações diferentes não são: a potência somada
     * no meio do caminho caía para metade (-3 dB), e o motor "afundava" a cada passagem entre
     * amostras. Cosseno/seno somam 1 em POTÊNCIA, que é o que o ouvido mede.
     */
    fun ganhoBaixo(mix: Double): Double = cos(mix.coerceIn(0.0, 1.0) * PI / 2)

    fun ganhoAlto(mix: Double): Double = sin(mix.coerceIn(0.0, 1.0) * PI / 2)

    /**
     * Interpolação cúbica de Hermite (Catmull-Rom) entre `y0` e `y1`, com os vizinhos `ym1` e `y2`.
     *
     * Substitui a linear na leitura das amostras com pitch variável. A linear funciona como um
     * passa-baixa involuntário e cria imagens de aliasing em pitch acima de 1 — justamente os
     * agudos que dão o "rasgado" do motor em giro alto. Custa 4 leituras em vez de 2, e só as
     * duas camadas audíveis do crossfade chegam a ser lidas.
     */
    fun hermite(ym1: Double, y0: Double, y1: Double, y2: Double, frac: Double): Double {
        val c1 = 0.5 * (y1 - ym1)
        val c2 = ym1 - 2.5 * y0 + 2.0 * y1 - 0.5 * y2
        val c3 = 0.5 * (y2 - ym1) + 1.5 * (y0 - y1)
        return ((c3 * frac + c2) * frac + c1) * frac + y0
    }

    // ── Zumbido elétrico ────────────────────────────────────────────────────────────────────

    /** Rotação virtual do motor elétrico: redução fixa sobre a roda, como num elétrico de verdade. */
    private const val REDUCAO_ELETRICA = 9.0
    private const val RAIO_DA_RODA_M = 0.325

    /** Tom do zumbido parado e no teto de velocidade de referência. */
    const val ZUMBIDO_HZ_PARADO = 62.0
    const val ZUMBIDO_HZ_MAXIMO = 440.0
    private const val ROTACAO_ELETRICA_DE_REFERENCIA = 14000.0

    fun rotacaoEletricaVirtual(velocidadeKmh: Double): Double {
        val rodaRpm = (velocidadeKmh.coerceAtLeast(0.0) / 3.6) / (2 * PI * RAIO_DA_RODA_M) * 60
        return rodaRpm * REDUCAO_ELETRICA
    }

    /**
     * Fundamental do zumbido para uma rotação elétrica. Curva levemente côncava (expoente 0,8):
     * sobe rápido na saída — é onde o som precisa dizer "o carro respondeu" — e não vira apito
     * em rodovia.
     */
    fun frequenciaDoZumbido(rotacaoEletrica: Double): Double {
        val n = (rotacaoEletrica / ROTACAO_ELETRICA_DE_REFERENCIA).coerceIn(0.0, 1.25)
        return ZUMBIDO_HZ_PARADO + (ZUMBIDO_HZ_MAXIMO - ZUMBIDO_HZ_PARADO) * n.pow(0.8)
    }

    /**
     * Converte a frequência do zumbido para a escala de "RPM" que o resto do ASD usa (curva de
     * volume por RPM, tacômetro da tela), para que as duas coisas continuem valendo no perfil
     * elétrico sem uma segunda configuração.
     */
    fun rpmEquivalente(frequenciaHz: Double): Double {
        val n = ((frequenciaHz - ZUMBIDO_HZ_PARADO) / (ZUMBIDO_HZ_MAXIMO - ZUMBIDO_HZ_PARADO)).coerceIn(0.0, 1.0)
        return 800.0 + n * (7000.0 - 800.0)
    }

    /** Inverso de [rpmEquivalente]: o motor de áudio recebe "RPM" e reconstrói a frequência. */
    fun frequenciaDoRpmEquivalente(rpm: Double): Double {
        val n = ((rpm - 800.0) / (7000.0 - 800.0)).coerceIn(0.0, 1.0)
        return ZUMBIDO_HZ_PARADO + n * (ZUMBIDO_HZ_MAXIMO - ZUMBIDO_HZ_PARADO)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
