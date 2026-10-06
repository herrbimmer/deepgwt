// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.rede

/** Por que o 4G está cortado agora. [rotulo] é o texto que a tela mostra (exato). */
enum class MotivoDoCorte(val rotulo: String) {
    MANUAL("manual"),
    CONSUMO("consumo"),
    WIFI("WiFi"),
    PROJECAO("AA/CarPlay"),
}

/** O que fazer com o 4G (ou com a telemetria) depois de decidir. */
internal enum class AcaoDeCorte { NADA, DESATIVAR, ATIVAR }

/** O último resultado que este processo aplicou. */
internal enum class UltimoAplicado { NENHUM, CORTADO, LIBERADO }

/**
 * O que as regras de corte consultam. Cada leitura é feita só quando a regra anterior não decidiu:
 * mestre desligado não lê mais nada; o uso do ciclo só é consultado com a regra de consumo ligada.
 */
internal interface EntradasDoCorte {
    fun mestre(): Boolean
    fun manual(): Boolean
    fun consumoLigado(): Boolean
    fun usoNoCicloMb(): Long
    fun tetoDeConsumoMb(): Int
    fun wifiLigado(): Boolean
    fun wifiConectado(): Boolean
    fun projecaoLigada(): Boolean
    fun projecaoAtiva(): Boolean
}

internal object RegrasDeCorte {

    /** Primeira regra que vale decide o motivo; null = não cortar. */
    fun decidirMotivo(entradas: EntradasDoCorte): MotivoDoCorte? {
        if (!entradas.mestre()) return null
        if (entradas.manual()) return MotivoDoCorte.MANUAL
        if (entradas.consumoLigado() && entradas.usoNoCicloMb() >= entradas.tetoDeConsumoMb()) {
            return MotivoDoCorte.CONSUMO
        }
        if (entradas.wifiLigado() && entradas.wifiConectado()) return MotivoDoCorte.WIFI
        if (entradas.projecaoLigada() && entradas.projecaoAtiva()) return MotivoDoCorte.PROJECAO
        return null
    }

    /**
     * Com um corte pedido e [reafirmar], toda avaliação reemite o corte (o hotspot ou a fabricante
     * pode religar o 4G sem a memória saber). Sem corte pedido, só libera o que nós mesmos
     * cortamos ([cortadoPeloApp]): nunca religa um 4G que a pessoa desligou por fora.
     */
    fun decidirAcao(
        desejaCortar: Boolean,
        reafirmar: Boolean,
        cortadoPeloApp: Boolean,
        ultimoAplicado: UltimoAplicado,
    ): AcaoDeCorte = when {
        desejaCortar && reafirmar -> AcaoDeCorte.DESATIVAR
        desejaCortar && !cortadoPeloApp -> AcaoDeCorte.DESATIVAR
        desejaCortar && ultimoAplicado != UltimoAplicado.CORTADO -> AcaoDeCorte.DESATIVAR
        desejaCortar -> AcaoDeCorte.NADA
        cortadoPeloApp -> AcaoDeCorte.ATIVAR
        else -> AcaoDeCorte.NADA
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
