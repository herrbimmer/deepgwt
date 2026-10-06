// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * O serviço de acessibilidade do DeepGWT enquanto ele está conectado (Alpha 7.8).
 *
 * ## POR QUE ISTO EXISTE
 *
 * Um app comum abre janela por cima dos outros com `TYPE_APPLICATION_OVERLAY`, e essa camada fica
 * ABAIXO das janelas do sistema — inclusive do pop-up de volume do próprio carro. Era por isso que
 * o nosso cartão de volume aparecia por trás do original ("a sobreposição aparece por trás do
 * indicador do veículo"). A única camada mais alta que um app de fora do sistema alcança é a
 * `TYPE_ACCESSIBILITY_OVERLAY`, e só um serviço de acessibilidade CONECTADO pode abri-la — o
 * WindowManager dele carrega o token da conexão.
 *
 * O serviço já existia (e o próprio app o liga pelo Shizuku, a partir do serviço da barra
 * inferior); aqui ele só avisa quando está de pé, para quem precisa da
 * camada montar a janela nele e desmontar quando ele cair.
 */
object CamadaDeAcessibilidade {

    private val servicoAtual = MutableStateFlow<AccessibilityService?>(null)

    /** O serviço conectado, ou null. Muda na thread principal. */
    val servico: StateFlow<AccessibilityService?> = servicoAtual.asStateFlow()

    @JvmStatic
    fun conectou(servico: AccessibilityService) {
        servicoAtual.value = servico
    }

    @JvmStatic
    fun desconectou(servico: AccessibilityService) {
        if (servicoAtual.value === servico) servicoAtual.value = null
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
