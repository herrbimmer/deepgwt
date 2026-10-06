// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo

import br.com.deepgwt.veiculo.Sinais as SinaisDaCentral
/**
 * De onde sai o "o farol está aceso" que acende o modo noturno dos temas.
 *
 * ## POR QUE ISTO EXISTE
 *
 * Os quatro temas trocam para âmbar/vermelho quando recebem `car.basic.head_light_status` ligado.
 * **Neste carro essa chave chega VAZIA** — está no dicionário do barramento e é monitorada desde
 * sempre, mas o veículo nunca publica valor nenhum nela (conferido na captura completa de
 * telemetria em `knowledge/session_2026-09-15/raw_http/telemetry.json`, onde ela é `""` enquanto
 * todas as outras luzes trazem `0`/`1`). Resultado: nenhum tema jamais acendeu o modo noturno, e o
 * âmbar da luz ambiente, que passou a depender da mesma chave, também parou de acender.
 *
 * Então a resposta não pode sair de uma chave só. Esta política resolve o estado a partir do que o
 * carro DE FATO publica e devolve um booleano; quem empurra para o tema continua usando o nome de
 * chave do contrato, e nenhum tema precisa ser editado.
 *
 * ## O QUE É FATO E O QUE É INFERÊNCIA
 *
 * FATO: `car.basic.head_light_status` vem vazia; `car.basic.low_beam_light_status`,
 * `car.basic.high_beam_light_status` e `car.drive.setting.outline_lamps_state` vêm preenchidas.
 *
 * INFERÊNCIA NÃO CONFIRMADA: que a luz baixa acompanhe o farol de verdade. Existe uma anotação no
 * serviço antigo da luz ambiente dizendo que ela reflete a
 * POSIÇÃO DO SELETOR (baixa vs. alta) e pode ler ligada com o farol apagado — foi por causa disso
 * que aquele serviço migrou para a chave mestra, sem perceber que a mestra não existe neste carro.
 * Como não dá para decidir isso sem o carro na frente, a fonte é ESCOLHÍVEL nos Ajustes e o
 * automático explica no log qual caminho usou. A tela de Telas mostra os valores crus lado a lado:
 * um giro do botão das luzes diz qual chave se mexe, e aí a escolha deixa de ser palpite.
 */
object HeadlightPolicy {

    /** Chave mestra do contrato — é a que os temas assinam, mesmo quando o valor é sintetizado. */
    const val CHAVE_DO_CONTRATO = "car.basic.head_light_status"

    /** Segue o carro: chave mestra quando ela existir, senão os faróis baixo/alto. */
    const val FONTE_AUTO = "auto"

    /** Só a chave mestra. Para quem prefere nada a um falso positivo. */
    const val FONTE_MESTRE = "master"

    /** Luz baixa/alta, ignorando a mestra. */
    const val FONTE_LUZ_BAIXA = "lowbeam"

    /** Luzes de posição (`outline lamps`). */
    const val FONTE_POSICAO = "outline"

    /** Pelo relógio: das 18h às 6h. Não depende de sinal nenhum do veículo. */
    const val FONTE_HORARIO = "clock"

    const val FONTE_SEMPRE = "always"
    const val FONTE_NUNCA = "never"

    const val FONTE_PADRAO = FONTE_AUTO

    /** Primeira hora do modo noturno no [FONTE_HORARIO], inclusive. */
    const val HORA_DE_ACENDER = 18

    /** Primeira hora de dia no [FONTE_HORARIO], inclusive. */
    const val HORA_DE_APAGAR = 6

    /**
     * As chaves do carro que podem mudar a resposta.
     *
     * Serve para o projetor saber quando recalcular sem ter de reavaliar a cada valor que chega.
     */
    val CHAVES_DE_ORIGEM: Set<String> = setOf(
        SinaisDaCentral.CAR_BASIC_HEAD_LIGHT_STATUS.chave,
        SinaisDaCentral.CAR_BASIC_LOW_BEAM_LIGHT_STATUS.chave,
        SinaisDaCentral.CAR_BASIC_HIGH_BEAM_LIGHT_STATUS.chave,
        SinaisDaCentral.CAR_DRIVE_SETTING_OUTLINE_LAMPS_STATE.chave
    )

    /** Leitura crua das quatro chaves, como vieram do barramento. */
    data class Sinais(
        val mestre: String? = null,
        val luzBaixa: String? = null,
        val luzAlta: String? = null,
        val posicao: String? = null
    )

    /**
     * A chave existe NESTE carro?
     *
     * Diferente de "está desligada": `"0"` é uma resposta, `""` é a ausência dela. É essa distinção
     * que deixa o automático escolher outro caminho sem precisar de lista de modelos.
     */
    fun publicada(valor: String?): Boolean {
        val v = valor?.trim()?.lowercase() ?: return false
        return v.isNotEmpty() && v != "null" && v != "undefined" && v != "unknown" && v != "--"
    }

    /** Mesma régua de "ligado" que o resto do app já usa para sinais de luz e de aviso. */
    fun ligada(valor: String?): Boolean = br.com.deepgwt.painel.AvisosDoCarro.ativo(valor)

    /**
     * Resolve o estado do farol.
     *
     * [hora] é a hora local em 0..23 e só é lida na fonte por horário — quem chama não precisa
     * consultar o relógio para as outras.
     */
    fun resolver(fonte: String?, sinais: Sinais, hora: Int): Boolean = when (fonte) {
        FONTE_SEMPRE -> true
        FONTE_NUNCA -> false
        FONTE_HORARIO -> hora >= HORA_DE_ACENDER || hora < HORA_DE_APAGAR
        FONTE_MESTRE -> ligada(sinais.mestre)
        FONTE_LUZ_BAIXA -> ligada(sinais.luzBaixa) || ligada(sinais.luzAlta)
        FONTE_POSICAO -> ligada(sinais.posicao)
        // FONTE_AUTO e qualquer valor gravado por uma versão futura/estranha: a chave mestra manda
        // quando ela EXISTE neste carro; quando não existe, o farol baixo/alto responde por ela.
        // Cair no automático diante de um valor desconhecido é o certo aqui: uma preferência
        // corrompida não pode desligar o modo noturno em silêncio.
        else -> if (publicada(sinais.mestre)) ligada(sinais.mestre) else {
            ligada(sinais.luzBaixa) || ligada(sinais.luzAlta)
        }
    }

    /** `"1"`/`"0"`, no formato que os temas esperam receber em [CHAVE_DO_CONTRATO]. */
    fun resolverComoTexto(fonte: String?, sinais: Sinais, hora: Int): String =
        if (resolver(fonte, sinais, hora)) "1" else "0"

    /**
     * Monta os [Sinais] a partir de um leitor de chave qualquer.
     *
     * Recebe a função de leitura em vez de chamar o motor do veículo direto para este arquivo
     * continuar sendo Kotlin puro: a regra do farol é justamente o tipo de coisa que erra em
     * silêncio no carro, e aqui ela pode ser provada em teste de unidade.
     */
    fun sinaisDe(ler: (String) -> String?): Sinais = Sinais(
        mestre = ler(SinaisDaCentral.CAR_BASIC_HEAD_LIGHT_STATUS.chave),
        luzBaixa = ler(SinaisDaCentral.CAR_BASIC_LOW_BEAM_LIGHT_STATUS.chave),
        luzAlta = ler(SinaisDaCentral.CAR_BASIC_HIGH_BEAM_LIGHT_STATUS.chave),
        posicao = ler(SinaisDaCentral.CAR_DRIVE_SETTING_OUTLINE_LAMPS_STATE.chave)
    )

    /** A hora local agora, no formato que [resolver] espera. */
    fun horaLocal(): Int =
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

    /** Qual caminho o automático tomaria — só para log e para a tela de diagnóstico. */
    fun caminhoDoAutomatico(sinais: Sinais): String =
        if (publicada(sinais.mestre)) "mestre" else "luz baixa/alta"

    /** Rótulo curto de cada fonte, para os Ajustes. */
    fun rotuloDaFonte(fonte: String?): String = when (fonte) {
        FONTE_MESTRE -> "Só farol (mestre)"
        FONTE_LUZ_BAIXA -> "Luz baixa/alta"
        FONTE_POSICAO -> "Luz de posição"
        FONTE_HORARIO -> "Por horário (18h–6h)"
        FONTE_SEMPRE -> "Sempre"
        FONTE_NUNCA -> "Nunca"
        else -> "Automático"
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
