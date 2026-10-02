package br.com.opengw.veiculo.automacoes

/** Regras puras das automações (motor-automacoes.md §B.8, §G, §H, §I). */
internal object RegrasDasAutomacoes {

    /** B.8: `"0"` e `"-1"` (aparados) são "desligou". */
    fun readyDesligado(valor: String?): Boolean {
        val v = valor?.trim()
        return v == "0" || v == "-1"
    }

    /**
     * G.2: [agora], [inicio] e [fim] em minutos do dia. Início < fim: início ≤ agora < fim. Senão
     * (atravessa a meia-noite ou início = fim): agora ≥ início ou agora < fim — com início = fim,
     * sempre (D-A11).
     */
    fun dentroDaJanela(agora: Int, inicio: Int, fim: Int): Boolean =
        if (inicio < fim) agora in inicio until fim else agora >= inicio || agora < fim

    fun minutos(hora: Int, minuto: Int): Int = hora * 60 + minuto

    fun hora(minutosDoDia: Int): String = "%02d:%02d".format(minutosDoDia / 60, minutosDoDia % 60)

    /** H.2: o original guardado é um inteiro de 0 a 98 (99 desliga a função de fábrica do botão). */
    fun originalDoBotao(texto: String?): String? {
        val t = texto?.trim() ?: return null
        if (t.isEmpty() || t == "null" || t == "undefined") return null
        val n = t.toIntOrNull() ?: return null
        return if (n in 0..98) n.toString() else null
    }

    /**
     * D-A6: a lista de `enabled_accessibility_services` com [componente] somado. Null quando ele já
     * está lá (não há o que gravar). Compara a forma longa ("pkg/pkg.Classe") com a curta ("pkg/.Classe").
     */
    fun somarServico(listaAtual: String?, componente: String): String? {
        val atuais = listaAtual.orEmpty()
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" }
        val meu = normalizarComponente(componente)
        if (atuais.any { normalizarComponente(it) == meu }) return null
        return (atuais + componente.trim()).joinToString(":")
    }

    /** "pkg/.Classe" → "pkg/pkg.Classe"; o resto fica como está. */
    fun normalizarComponente(componente: String): String {
        val c = componente.trim()
        val barra = c.indexOf('/')
        if (barra <= 0 || barra == c.length - 1) return c
        val pacote = c.substring(0, barra)
        val classe = c.substring(barra + 1)
        return if (classe.startsWith(".")) "$pacote/$pacote$classe" else c
    }
}
