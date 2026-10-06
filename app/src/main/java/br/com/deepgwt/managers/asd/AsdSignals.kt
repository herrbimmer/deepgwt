// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.managers.asd

import kotlin.math.abs

/**
 * De onde o ASD tira acelerador, freio e cadência, e como decide qual leitura vale.
 *
 * ## POR QUE ISTO EXISTE
 *
 * Até esta revisão o acelerador vinha de UMA chave não confirmada
 * (`car.basic.accelerator_pedal_position`, um palpite documentado como palpite) e, quando ela não
 * respondia, de uma estimativa pela aceleração do próprio carro — que atrasa o som em relação ao
 * pé e some em velocidade constante (aceleração zero = "pé fora", mesmo com o motorista mantendo
 * 100 km/h). O freio simplesmente não existia: o [AsdManager] passava `0.0` fixo, então o
 * simulador nunca sabia que o carro estava freando e a redução de marcha em frenagem nunca
 * acontecia pelo motivo certo.
 *
 * O barramento tem sinais melhores, e eles são usados aqui EM CASCATA, do mais direto ao mais
 * indireto. A fonte que venceu é publicada em [Leitura.fonte] para aparecer na tela do ASD: assim
 * a inferência deixa de ser invisível e passa a ser conferível no carro, que é a única forma de
 * confirmar chave não documentada.
 *
 * ## O QUE É FATO E O QUE É INFERÊNCIA
 *
 * FATO: as chaves existem no catálogo do veículo (`Sinal`).
 * INFERÊNCIA: o significado e a escala de `energy_output_percentage` (tratada como 0..100 de
 * demanda de potência) e de `energy_recovery_info` (tratada como intensidade de regeneração).
 * `motor_power` já é usado como kW pelo Painel Esportivo, então essa parte é reaproveitamento e
 * não suposição nova.
 *
 * Nada aqui toca em Android — é tudo função pura, para ser testável sem emulador.
 */
object AsdSignals {

    /** De onde saiu o número que está governando o som agora. */
    enum class Fonte(val rotulo: String) {
        PEDAL("pedal do acelerador"),
        SAIDA_DE_ENERGIA("saída de energia"),
        POTENCIA("potência do motor"),
        ACELERACAO("aceleração do veículo"),
        REGENERACAO("regeneração"),
        FREIO_DE_MAO("freio de estacionamento"),
        NENHUMA("sem telemetria")
    }

    data class Leitura(val valor: Double, val fonte: Fonte)

    /**
     * Potência de referência para converter kW em demanda 0..1.
     *
     * Não é a potência máxima de catálogo do carro: é o ponto a partir do qual o som já está no
     * talo. Passar disso satura, e saturar é o comportamento certo — o ouvido não distingue 140 de
     * 160 kW, mas distingue muito bem "acelerando forte" de "acelerando tudo".
     */
    const val POTENCIA_DE_REFERENCIA_KW = 130.0

    /** Acima disto uma leitura de kW é descartada como lixo do barramento. */
    private const val POTENCIA_IMPLAUSIVEL_KW = 1000.0

    /** Aceleração (m/s²) tratada como pé no fundo no último recurso. */
    const val ACELERACAO_DE_PE_NO_FUNDO = 3.5

    /**
     * Acelerador, em cascata. O primeiro que devolver número plausível vence.
     *
     *  1. [pedalBruto] — a chave do pedal (ou a que o usuário escolheu em Ajustes > ASD). É a
     *     única leitura DIRETA do pé; se ela responder, nada mais importa.
     *  2. [saidaDeEnergiaBruta] — a porcentagem de saída de energia. É o ponteiro de potência do
     *     painel: sobe com o pé e cai ao soltar, inclusive em velocidade constante.
     *  3. [potenciaKw] — potência instantânea, normalizada por [POTENCIA_DE_REFERENCIA_KW].
     *  4. [aceleracaoMs2] — o fallback antigo, mantido por último porque é o pior: atrasa e mente
     *     em velocidade constante.
     *
     * Valores negativos das fontes 2-4 significam regeneração/desaceleração, não pedal: viram 0,
     * e quem trata disso é [resolverFreio].
     */
    fun resolverAcelerador(
        pedalBruto: Double?,
        saidaDeEnergiaBruta: Double?,
        potenciaKw: Double?,
        aceleracaoMs2: Double?
    ): Leitura {
        pedalBruto?.let { bruto ->
            if (bruto.isFinite() && bruto >= 0.0 && bruto <= 100.0) {
                return Leitura((bruto / 100.0).coerceIn(0.0, 1.0), Fonte.PEDAL)
            }
        }
        saidaDeEnergiaBruta?.let { bruto ->
            if (bruto.isFinite() && bruto >= -100.0 && bruto <= 100.0) {
                return Leitura((bruto / 100.0).coerceIn(0.0, 1.0), Fonte.SAIDA_DE_ENERGIA)
            }
        }
        potenciaKw?.let { kw ->
            if (kw.isFinite() && abs(kw) < POTENCIA_IMPLAUSIVEL_KW) {
                return Leitura((kw / POTENCIA_DE_REFERENCIA_KW).coerceIn(0.0, 1.0), Fonte.POTENCIA)
            }
        }
        aceleracaoMs2?.let { a ->
            if (a.isFinite()) {
                return Leitura((a / ACELERACAO_DE_PE_NO_FUNDO).coerceIn(0.0, 1.0), Fonte.ACELERACAO)
            }
        }
        return Leitura(0.0, Fonte.NENHUMA)
    }

    /**
     * Freio, em cascata. Não existe chave de pressão no pedal de freio neste catálogo, então o que
     * se mede é a CONSEQUÊNCIA dela.
     *
     *  1. [regeneracaoBruta] — intensidade de regeneração. Num híbrido, pisar no freio (ou soltar
     *     o pé com regeneração forte) é justamente o que a faz subir.
     *  2. [potenciaKw] negativa — potência voltando para a bateria é o mesmo fenômeno visto por
     *     outro sensor, e serve quando a chave de regeneração não responde.
     *  3. [freioDeMao] — não é frenagem de condução, mas com o carro parado e o freio puxado o
     *     motor não pode estar "acelerando", e tratar como freio evita que a estimativa por
     *     aceleração invente pedal na saída da vaga.
     *
     * A escala de saída é 0..1, onde 1 é frenagem forte.
     */
    fun resolverFreio(
        regeneracaoBruta: Double?,
        potenciaKw: Double?,
        freioDeMao: Boolean
    ): Leitura {
        regeneracaoBruta?.let { bruto ->
            if (bruto.isFinite() && bruto > 0.0 && bruto <= 100.0) {
                return Leitura((bruto / 100.0).coerceIn(0.0, 1.0), Fonte.REGENERACAO)
            }
        }
        potenciaKw?.let { kw ->
            if (kw.isFinite() && kw < 0.0 && abs(kw) < POTENCIA_IMPLAUSIVEL_KW) {
                return Leitura(
                    (-kw / POTENCIA_DE_REFERENCIA_KW).coerceIn(0.0, 1.0),
                    Fonte.REGENERACAO
                )
            }
        }
        if (freioDeMao) return Leitura(1.0, Fonte.FREIO_DE_MAO)
        return Leitura(0.0, Fonte.NENHUMA)
    }

    /**
     * Modo de condução do carro → cadência do câmbio simulado.
     *
     * Os códigos vêm do mesmo mapa que o HUD e o painel já usam para `car.drive_setting.drive_mode`
     * (0 Normal, 1 Sport, 2 Eco, 3 Neve, 4 Areia, 5 Lama, 11 AWD). Neve/Areia/Lama entram como Eco
     * de propósito: são modos de tração em que um câmbio de verdade sobe cedo e evita tranco, que é
     * exatamente o que a cadência Eco faz aqui.
     */
    fun cadenciaParaModo(bruto: String?): DrivetrainSimulator.Cadencia {
        val cru = bruto?.trim()?.toFloatOrNull()?.toInt()
        return when (cru) {
            1 -> DrivetrainSimulator.Cadencia.SPORT
            2, 3, 4, 5 -> DrivetrainSimulator.Cadencia.ECO
            else -> DrivetrainSimulator.Cadencia.NORMAL
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
