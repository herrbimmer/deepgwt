package br.com.deepgwt.tela3d.catalogo

/**
 * Lista de pacotes gravada nas preferências como texto separado por vírgula
 * (`recent_apps`, `hidden_apps`, `window_apps`). Puro: sem Android.
 */
internal object ListaDePacotes {
    /**
     * Lê a lista: apara cada item, ignora vazios e repetidos e mantém a ordem da primeira aparição.
     * Não impõe limite de tamanho (o corte em 12 dos recentes é de [RecentesMru.inserir]).
     */
    fun ler(texto: String?): List<String> {
        if (texto.isNullOrBlank()) return emptyList()
        val vistos = LinkedHashSet<String>()
        for (parte in texto.split(',')) {
            val pacote = parte.trim()
            if (pacote.isNotEmpty()) vistos.add(pacote)
        }
        return vistos.toList()
    }

    /** Escreve os pacotes na ordem recebida, separados por vírgula e sem espaços. */
    fun escrever(pacotes: Collection<String>): String = pacotes.joinToString(",")

    /** Conjunto (ocultos, janela) é gravado ordenado por pacote, para o texto ficar estável. */
    fun escreverOrdenado(pacotes: Collection<String>): String = escrever(pacotes.sorted())
}

/** Lista de apps abertos há pouco, mais recente primeiro, sem repetição. Imutável. */
internal object RecentesMru {
    const val LIMITE = 12

    /** O que a faixa mostra como atalhos de recentes. */
    const val ATALHOS = 3

    /**
     * Registra a abertura de [pacote]: remove se já estava, põe na posição 0 e corta em [LIMITE]
     * (os mais antigos saem).
     */
    fun inserir(atual: List<String>, pacote: String): List<String> {
        val nova = ArrayList<String>(minOf(atual.size + 1, LIMITE))
        nova.add(pacote)
        for (p in atual) {
            if (p != pacote) nova.add(p)
            if (nova.size >= LIMITE) break
        }
        return nova
    }

    /**
     * Os até [ATALHOS] primeiros da ordem MRU que passam em [permitido] e estão em [instalados].
     */
    fun atalhos(
        mru: List<String>,
        instalados: Set<String>,
        permitido: (String) -> Boolean,
    ): List<String> {
        val saida = ArrayList<String>(ATALHOS)
        for (p in mru) {
            if (p in instalados && permitido(p)) {
                saida.add(p)
                if (saida.size >= ATALHOS) break
            }
        }
        return saida
    }

    /**
     * Semente quando a lista está vazia: percorre [candidatos] (pacotes das tarefas recentes, na
     * ordem do sistema) e fica com os que estão instalados e passam em [permitido], sem repetir,
     * parando em [ATALHOS].
     */
    fun semear(
        candidatos: List<String>,
        instalados: Set<String>,
        permitido: (String) -> Boolean,
    ): List<String> {
        val saida = ArrayList<String>(ATALHOS)
        for (p in candidatos) {
            if (p in instalados && permitido(p) && p !in saida) {
                saida.add(p)
                if (saida.size >= ATALHOS) break
            }
        }
        return saida
    }
}
