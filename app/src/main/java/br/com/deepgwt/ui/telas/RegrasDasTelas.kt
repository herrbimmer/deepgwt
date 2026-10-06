// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import java.util.Locale

/** I4/X-V2: o instante de um uptime para o cartão de estado. */
object FormatoDoInstante {

    /** "mm:ss.SSS" de um uptime em ms (minutos sem limite), ou null se não pronto ou t ≤ 0. */
    @JvmStatic
    fun formatar(uptimeMs: Long, pronto: Boolean): String? {
        if (!pronto || uptimeMs <= 0) return null
        val minutos = uptimeMs / 60_000
        val segundos = (uptimeMs / 1_000) % 60
        val milis = uptimeMs % 1_000
        return String.format(Locale.US, "%02d:%02d.%03d", minutos, segundos, milis)
    }
}

/** Regras puras da aba Valores atuais (`telas-e-servico-diversos.md` §4). */
internal object RegrasDosValores {

    /** V4: as chaves cujo nome contém [busca] (sem diferenciar maiúsculas), ordenadas pelo nome. */
    fun filtrar(valores: Map<String, String>, busca: String): List<Pair<String, String>> =
        valores.entries
            .filter { busca.isEmpty() || it.key.contains(busca, ignoreCase = true) }
            .sortedBy { it.key }
            .map { it.key to it.value }

    /**
     * V5: as chaves que dá para acompanhar a mais ([todas] menos [padrao]), filtradas por [busca],
     * as [marcadas] primeiro, sem mudar a ordem dentro de cada grupo.
     */
    fun monitoraveis(todas: List<String>, padrao: Collection<String>, marcadas: Set<String>, busca: String): List<String> {
        val fora = padrao.toHashSet()
        val candidatas = todas.filter { it !in fora && (busca.isEmpty() || it.contains(busca, ignoreCase = true)) }
        val (sim, nao) = candidatas.partition { it in marcadas }
        return sim + nao
    }

    /** V2: o mapa inicial, nulo → "". */
    fun semNulos(valores: Map<String, String?>): Map<String, String> =
        valores.mapValues { it.value.orEmpty() }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
