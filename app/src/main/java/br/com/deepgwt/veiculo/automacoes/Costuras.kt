// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.automacoes

/*
 * O que as automações (módulo 3.3b, `docs/independencia/specs/motor-automacoes.md`) usam de fora:
 * os ganchos da partida (código nosso fora do motor) e, para os testes, as ações de 3.3c e 3.3e
 * atrás de interfaces.
 */

/** O que a partida chama de fora do motor. Valor vazio: não faz nada. */
interface GanchosDePartida {
    /** Pacote "funções do carro" (memória dos assistentes, conector, coringas). */
    fun funcoesDoCarro()

    /** Perfil do motorista ao ligar o carro (rápido; o resto ele mesmo joga para outra thread). */
    fun perfilAoLigarOCarro()

    /** O gerenciador dos painéis projetados. Chamado na THREAD PRINCIPAL. */
    fun iniciarProjetores()

    fun iniciarConsistenciaDeViagem()

    companion object {
        @JvmField
        val VAZIO: GanchosDePartida = object : GanchosDePartida {
            override fun funcoesDoCarro() {}
            override fun perfilAoLigarOCarro() {}
            override fun iniciarProjetores() {}
            override fun iniciarConsistenciaDeViagem() {}
        }
    }
}

/** O que as automações pedem aos comandos (3.3c). */
internal interface AcoesDoCarro {
    fun monitoramento(ligado: Boolean)
    fun somParaPedestres(ligado: Boolean)
    fun fecharJanelasAoTrancar(ligado: Boolean)
    fun fecharTodasAsJanelas(): Boolean
    fun fecharTeto(fecharCortinaJunto: Boolean)
    fun abrirCortina()
    fun aplicarAlvoDeCargaHev(motivo: String)

    val arNoMaximoAtivo: Boolean
    fun ligarArNoMaximoSeQuente()
    fun suavizarArNoMaximo()
    fun sairDoArNoMaximo()
    /** Re-partida: o ar no máximo perde as novas tentativas e o tempo-limite. */
    fun cancelarAgendadosDoAr()

    var cameraAbertaPeloApp: Boolean
    fun fecharCamera360()

    /** `br.com.deepgwt.brilho.BrilhoAutomatico.ligar(true)`. */
    fun ligarBrilhoAutomatico()
}

/** O que as automações pedem ao sistema (3.3e). */
internal interface AcoesDoSistema {
    val bluetoothLigado: Boolean
    fun desligarBluetoothGuardando(motivo: String)
    fun religarBluetoothSeFomosNos(motivo: String)
    fun desligarHotspotGuardando()
    fun religarHotspotSeFomosNos()
    fun garantirAppsDoSistema()
    fun garantirAppsDesativados()
    /** Re-partida: as voltas de Bluetooth/hotspot pendentes se perdem (as marcas ficam). */
    fun cancelarRetentativas()
}

/**
 * Decisões do dono que a especificação deixa abertas (motor-automacoes.md §7). A especificação não
 * trouxe "decisões da sala suja" para esta parte; os padrões seguem a recomendação de cada uma.
 * (D-A1, D-A7 e o corte do contra-pulso de B.12 já estão no texto como requisito.)
 */
data class DecisoesDasAutomacoes(
    /** D-A2 (b): READY vazio é ignorado e só a mudança age (o primeiro valor conta como mudança). */
    val readyComBorda: Boolean = true,
    /** D-A3: a câmera parada fecha uma vez por abertura, não a cada evento de velocidade 0. */
    val cameraParadaUmaVezPorAbertura: Boolean = true,
    /** D-A6: soma o serviço de acessibilidade do app à lista, em vez de sobrescrevê-la. */
    val somarAcessibilidade: Boolean = true,
    /** D-A10: a cortina ao ligar roda também a cada READY desligado → ligado (não só uma vez por processo). */
    val cortinaACadaReady: Boolean = true,
)
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
