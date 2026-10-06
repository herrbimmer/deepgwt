package br.com.deepgwt.tela3d.viagens

/**
 * Reduz uma lista longa (pontos, rota, posições do mapa) a algo em torno de um limite, pulando de
 * `passo` em `passo`. O resultado pode passar do limite em até ~1 item; o último item sempre fica.
 */
object Afinamento {
    /** `max(1, ceil(n / max(1, limite)))`. */
    fun passo(n: Int, limite: Int): Int {
        val l = maxOf(1, limite)
        return maxOf(1, (n + l - 1) / l)
    }

    /** Detalhe, rota e posições do mapa: índices 0-based múltiplos do passo, mais o último. */
    fun <T> afinar(itens: List<T>, limite: Int): List<T> {
        val n = itens.size
        if (n == 0) return emptyList()
        val p = passo(n, limite)
        if (p == 1) return itens.toList()
        val saida = ArrayList<T>(n / p + 2)
        for (i in 0 until n step p) saida.add(itens[i])
        if ((n - 1) % p != 0) saida.add(itens[n - 1])
        return saida
    }

    /** Mapa ao vivo: contador 1-based múltiplo do passo (o 1º item é o 1), mais o último. */
    fun <T> afinarAoVivo(itens: List<T>, limite: Int): List<T> {
        val n = itens.size
        if (n == 0) return emptyList()
        val p = passo(n, limite)
        if (p == 1) return itens.toList()
        val saida = ArrayList<T>(n / p + 2)
        for (i in 0 until n) if ((i + 1) % p == 0) saida.add(itens[i])
        if (n % p != 0) saida.add(itens[n - 1])
        return saida
    }
}
