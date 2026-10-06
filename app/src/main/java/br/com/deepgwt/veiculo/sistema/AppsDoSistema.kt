// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.sistema

import br.com.deepgwt.veiculo.comandos.LinhaDoMotor
import br.com.deepgwt.veiculo.comandos.PreferenciasDoApp
import br.com.deepgwt.veiculo.comandos.RegistroNoLogcatDe
import br.com.deepgwt.veiculo.comandos.ShellPeloShizuku

/** O portão de licença em uso (o ciclo de vida liga; até lá, o valor da especificação). */
internal object PortaoAtual {
    @Volatile var portao: PortaoDeLicenca = PortaoDeLicenca { true }
}

private val registroDosApps = RegistroNoLogcatDe("DeepGWT.AppsDoSistema")

internal val nucleoDosApps: NucleoDosApps by lazy {
    NucleoDosApps(
        shell = ShellPeloShizuku,
        prefs = PreferenciasDoApp,
        portao = { PortaoAtual.portao },
        registro = registroDosApps,
    )
}

/**
 * Apps do sistema e debloat (motor-sistema.md §B). As duas funções voltam na hora e fazem o trabalho
 * (vários comandos de shell) na linha do motor (B.6).
 */
object AppsDoSistema {

    /** B.1 (= `Comandos.garantirAppsDoSistema`). */
    @JvmStatic
    fun garantirAppsDoSistema() = naLinha("apps do sistema") { nucleoDosApps.garantirAppsDoSistema() }

    /** B.2 (= `Comandos.garantirAppsDesativados`). */
    @JvmStatic
    fun garantirAppsDesativados() = naLinha("debloat") { nucleoDosApps.garantirAppsDesativados() }

    private fun naLinha(oQue: String, tarefa: () -> Unit) {
        try {
            LinhaDoMotor.agenda.executar(tarefa)
        } catch (e: Exception) {
            registroDosApps.erro("$oQue: não deu para enfileirar", e)
        }
    }
}

/** Escuta de voz, "Olá Haval" (motor-sistema.md §C). */
object EscutaDeVoz {

    /** Síncrono (vários comandos de shell): chamar fora da principal. Devolve o relatório. (= `Comandos.aplicarDespertarPorVoz`) */
    @JvmStatic
    fun aplicar(): String =
        try {
            nucleoDosApps.aplicarEscuta()
        } catch (e: Exception) {
            registroDosApps.erro("escuta de voz falhou", e)
            "Escuta de voz falhou (${e.message})."
        }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
