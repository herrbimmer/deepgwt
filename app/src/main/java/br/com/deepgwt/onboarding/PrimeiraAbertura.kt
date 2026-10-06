// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

/**
 * O que aparece por cima do app quando ele abre (Alpha 7.9).
 *
 * - Instalação nova: o assistente de primeira abertura, que termina oferecendo o tour.
 * - Atualização: só as novidades desta versão, com "Mostrar onde fica" — refazer o assistente
 *   inteiro a cada atualização cansaria quem já deixou tudo do seu jeito.
 * - Nada, depois de visto. "Pular" conta como visto: o assistente não volta a cada abertura.
 *
 * Refazer o assistente e ver o tour de novo ficam sempre em Interface do DeepGWT e Informações.
 */
enum class TelaDeAbertura { NADA, ASSISTENTE, NOVIDADES }

object PrimeiraAbertura {
    /**
     * [instalacaoNova] = o pacote nunca foi atualizado (`firstInstallTime == lastUpdateTime`):
     * vale para a primeira instalação e para uma reinstalação depois de desinstalar, que apaga
     * as preferências junto. [assistenteConcluidoEm] e [novidadesVistasAte] são versionCodes
     * (0 = nunca).
     */
    fun decidir(
        instalacaoNova: Boolean,
        assistenteConcluidoEm: Int,
        novidadesVistasAte: Int,
        versaoAtual: Int
    ): TelaDeAbertura =
        when {
            instalacaoNova && assistenteConcluidoEm <= 0 -> TelaDeAbertura.ASSISTENTE
            novidadesVistasAte < versaoAtual && Novidades.entre(novidadesVistasAte, versaoAtual).isNotEmpty() ->
                TelaDeAbertura.NOVIDADES
            else -> TelaDeAbertura.NADA
        }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
