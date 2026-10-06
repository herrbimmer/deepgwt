// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.vhal

import android.util.Log
import br.com.deepgwt.nucleo.DeepGwt
import java.io.InputStream

/**
 * Uma propriedade da central: nome da fabricante, id numérico, tipo e acesso, como estão no asset
 * `vhal_catalog.tsv` (módulo 3.10b, `docs/independencia/specs/extras-viagem.md`, Parte B).
 *
 * @param tipo `INT32`, `FLOAT` ou `INT32[]`, exibido como veio no arquivo.
 * @param acesso `R`, `W` ou `RW`.
 */
data class EntradaVhal(val nome: String, val id: Int, val tipo: String, val acesso: String) {
    /** Dá para ler esta propriedade (acesso `R` ou `RW`)? */
    val legivel: Boolean get() = acesso == "R" || acesso == "RW"
}

/**
 * Tradução nome ↔ id das propriedades da central. A VHAL não entrega nomes em tempo de execução,
 * então a lista vem do asset `vhal_catalog.tsv`, carregado uma vez e na ordem do arquivo.
 */
object CatalogoVhal {

    private const val ARQUIVO = "vhal_catalog.tsv"
    private const val TAG = "DeepGWT.Vhal"

    private val indice: IndiceDoCatalogo by lazy {
        IndiceDoCatalogo(carregar { DeepGwt.contexto.assets.open(ARQUIVO) })
    }

    /** Todas as entradas, na ordem do arquivo. Lista vazia se o asset não abriu. */
    fun todos(): List<EntradaVhal> = indice.todos

    /** O id da propriedade de nome exato [nome], ou nulo se o catálogo não a tem. */
    fun idDe(nome: String): Int? = indice.idDe(nome)

    /**
     * Lê as linhas do arquivo. Linha de menos de 4 colunas ou de id não hexadecimal é descartada sem
     * erro; colunas além da quarta são ignoradas. Função pura, para os testes.
     */
    internal fun analisar(linhas: Sequence<String>): List<EntradaVhal> =
        linhas.mapNotNull(::analisarLinha).toList()

    /** Abre, lê e analisa. Qualquer falha vira lista vazia com registro no log; nunca lança. */
    internal fun carregar(abrir: () -> InputStream): List<EntradaVhal> =
        try {
            abrir().bufferedReader(Charsets.UTF_8).use { analisar(it.lineSequence()) }
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            avisar("não consegui carregar $ARQUIVO: ${e.message}")
            emptyList()
        }

    private fun analisarLinha(linha: String): EntradaVhal? {
        val colunas = linha.split('\t')
        if (colunas.size < 4) return null
        val id = analisarId(colunas[1]) ?: return null
        return EntradaVhal(colunas[0], id, colunas[2], colunas[3])
    }

    /**
     * `0x` + hexadecimal (maiúsculo ou minúsculo) ou só o hexadecimal. Até 32 bits; acima de
     * `0x7fffffff` o valor vira negativo, como o `Int` assinado que a central usa.
     */
    private fun analisarId(texto: String): Int? {
        val digitos = if (texto.startsWith("0x") || texto.startsWith("0X")) texto.substring(2) else texto
        if (digitos.isEmpty() || digitos.length > 8) return null
        if (!digitos.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return digitos.toLong(16).toInt()
    }

    private fun avisar(mensagem: String) {
        try {
            Log.w(TAG, mensagem)
        } catch (e: Throwable) {
            // Teste JVM sem o Android: o log não existe.
        }
    }
}

/** A lista já carregada, com a busca por nome (se o nome se repete, vale o último). */
internal class IndiceDoCatalogo(val todos: List<EntradaVhal>) {
    private val porNome: Map<String, Int> = HashMap<String, Int>(todos.size * 2).also { mapa ->
        for (e in todos) mapa[e.nome] = e.id
    }

    fun idDe(nome: String): Int? = porNome[nome]
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
