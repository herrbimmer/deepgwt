// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.monitores

import ts.car.dcm.common.data.ConnDevice

/** Um aparelho conhecido pela central. Campos que a central não deu ficam nulos. */
data class AparelhoDoDcm(
    /** Chave com que a central o lista (id do aparelho). Na leitura por log: endereço BT, senão id. */
    val chave: String,
    val id: String?,
    val nome: String?,
    val serialUsb: String?,
    val enderecoBt: String?,
    val capacidadesDisponiveis: Int?,
    val capacidadeAtiva: Int?,
    val estadoDoAparelho: Int?,
    /** Texto livre (diagnóstico) com o estado por tipo de conexão. */
    val estadoPorTipo: String?,
    /** Estado da capacidade "Android Auto por USB" (128): 0 inválido … 4 conectado. */
    val estadoAndroidAutoUsb: Int?,
    /** Idem "Android Auto sem fio" (1024). */
    val estadoAndroidAutoSemFio: Int?,
) {

    /** A capacidade 128 está entre as disponíveis. */
    fun temAndroidAutoUsb(): Boolean =
        capacidadesDisponiveis != null && (capacidadesDisponiveis and CAPACIDADE_ANDROID_AUTO_USB) != 0

    /** Algum dos dois estados vale 4 (conectado). */
    fun temProjecaoAndroidAutoAtiva(): Boolean =
        estadoAndroidAutoUsb == ESTADO_CONECTADO || estadoAndroidAutoSemFio == ESTADO_CONECTADO

    companion object {
        const val CAPACIDADE_ANDROID_AUTO_USB = 128
        const val CAPACIDADE_ANDROID_AUTO_SEM_FIO = 1024

        /** Estado de uma capacidade: 4 = conectado ("ativada"). */
        const val ESTADO_CONECTADO = 4

        /** Do que a central devolveu pelo binder (`getDevices`). */
        internal fun doConnDevice(chave: String, aparelho: ConnDevice): AparelhoDoDcm = AparelhoDoDcm(
            chave = chave,
            id = aparelho.uuid,
            nome = aparelho.nomeAmigavel,
            serialUsb = aparelho.serialUsb,
            enderecoBt = aparelho.enderecoBt,
            capacidadesDisponiveis = aparelho.capacidadesDisponiveis,
            capacidadeAtiva = aparelho.capacidadeAtiva,
            estadoDoAparelho = aparelho.estadoDoAparelho,
            estadoPorTipo = aparelho.estadoPorTipoDeConexao.toString(),
            estadoAndroidAutoUsb = aparelho.estadoDaCapacidade(CAPACIDADE_ANDROID_AUTO_USB),
            estadoAndroidAutoSemFio = aparelho.estadoDaCapacidade(CAPACIDADE_ANDROID_AUTO_SEM_FIO),
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
