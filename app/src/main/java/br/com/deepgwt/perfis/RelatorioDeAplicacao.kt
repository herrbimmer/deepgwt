// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.perfis

/**
 * O que aconteceu ao aplicar um perfil.
 *
 * Existe porque o pedido é explícito: "caso alguma configuração não possa ser aplicada naquele
 * momento, informar o usuário em vez de simplesmente ignorá-la silenciosamente". Um `Boolean` de
 * retorno não dá conta disso — metade das coisas que podem falhar aqui falham sozinhas e sem
 * barulho (o carro em movimento recusa mudar o modo de condução; o serviço de painel ainda não
 * subiu; a chave existe neste modelo mas não neste acabamento). Sem contar e sem dizer, o
 * motorista troca de perfil, vê o tema mudar e conclui que o resto também foi.
 *
 * Nenhum campo é uma lista de tudo o que deu certo: em regime normal são ~150 preferências e uma
 * dúzia de chaves do carro, e listar isso é ruído. O que se lista é o que falhou.
 */
data class RelatorioDeAplicacao(
    val perfil: String,
    /** Quantas preferências do DeepGWT foram gravadas. */
    val preferenciasAplicadas: Int = 0,
    /** Chaves cujo valor gravado não pôde ser lido de volta (perfil de versão futura, ou corrompido). */
    val preferenciasIlegiveis: List<String> = emptyList(),
    /** Chaves do carro que já estavam no valor do perfil — nada foi escrito. */
    val veiculoJaEstavaCerto: Int = 0,
    /** Chaves do carro que mudaram agora. */
    val veiculoAplicado: Int = 0,
    /** Chaves do carro que o veículo recusou (ou que não existem nesta versão). */
    val veiculoRecusado: List<String> = emptyList(),
    /** Subsistemas que não conseguiram reler as preferências (barra, painel, HUD, ASD…). */
    val reconciliacoesFalhas: List<String> = emptyList(),
    /** Avisos que não são falha: memória de banco sem slot, perfil antigo sem parte do conteúdo. */
    val avisos: List<String> = emptyList(),
    /** Preenchido quando o perfil sequer pôde ser carregado. */
    val erroFatal: String? = null
) {

    val tudoCerto: Boolean
        get() = erroFatal == null &&
            preferenciasIlegiveis.isEmpty() &&
            veiculoRecusado.isEmpty() &&
            reconciliacoesFalhas.isEmpty()

    /** Uma linha, do tamanho de um aviso na tela. */
    fun resumo(): String {
        erroFatal?.let { return "Perfil $perfil não pôde ser aplicado: $it" }
        if (tudoCerto) {
            return "Perfil $perfil aplicado: $preferenciasAplicadas ajustes do DeepGWT" +
                (if (veiculoAplicado > 0) " e $veiculoAplicado do carro." else ".")
        }
        val pendencias = veiculoRecusado.size + preferenciasIlegiveis.size + reconciliacoesFalhas.size
        return "Perfil $perfil aplicado com $pendencias " +
            (if (pendencias == 1) "pendência." else "pendências.") +
            " Veja a lista em Perfis de Motorista."
    }

    /** O detalhamento que a tela mostra embaixo do resumo. Vazio quando não há o que dizer. */
    fun detalhes(): List<String> {
        val linhas = mutableListOf<String>()
        erroFatal?.let { linhas.add("Falhou: $it") }
        if (veiculoRecusado.isNotEmpty()) {
            linhas.add(
                "O carro recusou ${veiculoRecusado.size} " +
                    (if (veiculoRecusado.size == 1) "configuração" else "configurações") +
                    " — normalmente porque o veículo só aceita a mudança parado, ou porque a " +
                    "chave não existe neste acabamento: " + veiculoRecusado.take(6).joinToString(", ") +
                    (if (veiculoRecusado.size > 6) " e mais ${veiculoRecusado.size - 6}." else ".")
            )
        }
        if (preferenciasIlegiveis.isNotEmpty()) {
            linhas.add(
                "${preferenciasIlegiveis.size} " +
                    (if (preferenciasIlegiveis.size == 1) "preferência veio" else "preferências vieram") +
                    " ilegíveis no perfil e ficaram como estavam: " +
                    preferenciasIlegiveis.take(6).joinToString(", ") + "."
            )
        }
        if (reconciliacoesFalhas.isNotEmpty()) {
            linhas.add(
                "Estes subsistemas não releram as preferências agora e só vão obedecer na próxima " +
                    "vez que subirem: " + reconciliacoesFalhas.joinToString(", ") + "."
            )
        }
        linhas.addAll(avisos)
        return linhas
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
