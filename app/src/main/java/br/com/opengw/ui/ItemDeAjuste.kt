package br.com.opengw.ui

import androidx.compose.runtime.Composable

/**
 * Uma linha da tela de ajustes agrupados ([AjustesAgrupados]).
 *
 * O controle deslizante embutido aparece quando [valor], [faixa] e [aoMudarValor] vêm os três;
 * [passo] nulo vale 1. Com [semInterruptor] a linha mostra "›" no lugar do interruptor e o toque
 * não faz nada (D8). A área expandida (controle embutido e [conteudoExtra]) só aparece com
 * [ligado] verdadeiro.
 */
data class ItemDeAjuste(
    val titulo: String,
    val descricao: String,
    val ligado: Boolean,
    val aoMudar: (Boolean) -> Unit,
    val habilitado: Boolean = true,
    val valor: Int? = null,
    val faixa: IntRange? = null,
    val passo: Int? = null,
    val aoMudarValor: ((Int) -> Unit)? = null,
    val rotuloDoValor: String? = null,
    val semInterruptor: Boolean = false,
    val conteudoExtra: (@Composable () -> Unit)? = null,
    val grupo: String? = null,
)

/** Nomes dos grupos de ajustes e a ordem padrão. */
object GruposDeAjustes {
    const val AO_DESLIGAR = "Ao desligar / retrovisor"
    const val POR_VELOCIDADE = "Por velocidade"
    const val CONDUCAO = "Condução & energia"
    const val CLIMATIZACAO = "Climatização & conforto"
    const val SEGURANCA = "Segurança & avisos"
    const val TELA_E_SOM = "Tela & som"
    const val RECURSOS = "Recursos & integração"
    const val PERFORMANCE = "Performance"

    val ORDEM: List<String> = listOf(
        AO_DESLIGAR,
        POR_VELOCIDADE,
        CONDUCAO,
        CLIMATIZACAO,
        SEGURANCA,
        TELA_E_SOM,
        RECURSOS,
        PERFORMANCE,
    )

    /** O grupo de quem não tem grupo, ou tem um que não está na ordem. */
    const val OUTROS = "Outros"
}

/** Um grupo pronto para a tela: o nome e os itens, já na ordem. */
data class GrupoDeAjustes(val nome: String, val itens: List<ItemDeAjuste>)

/** Parte pura da tela de ajustes agrupados: montagem, busca, contadores (vetores V6). */
object MontagemDosAjustes {
    const val SEM_RESULTADO = "Nenhum ajuste encontrado."
    const val TEXTO_DA_BUSCA = "Buscar ajuste..."
    const val SINCRONIZADO = "sincronizado com o carro"

    /**
     * Para cada nome de [ordem], os itens daquele grupo, na ordem da lista; grupo sem item não
     * aparece. Quem não tem grupo, ou tem um fora da [ordem], vai para um último grupo
     * [GruposDeAjustes.OUTROS] (só se tiver item). Nenhum item some.
     *
     * Nomes repetidos na [ordem] contam uma vez só, e se a própria [ordem] já tem um grupo
     * "Outros" os itens soltos entram nele, depois dos seus: cada grupo aparece uma vez na tela (o
     * nome é a chave do item na lista).
     */
    fun agrupar(itens: List<ItemDeAjuste>, ordem: List<String>): List<GrupoDeAjustes> {
        val nomes = ordem.distinct()
        val conhecidos = nomes.toSet()
        val soltos = itens.filter { it.grupo == null || it.grupo !in conhecidos }
        val grupos = nomes.mapNotNull { nome ->
            val doGrupo = itens.filter { it.grupo == nome } +
                (if (nome == GruposDeAjustes.OUTROS) soltos else emptyList())
            if (doGrupo.isEmpty()) null else GrupoDeAjustes(nome, doGrupo)
        }
        val outrosJaNaOrdem = GruposDeAjustes.OUTROS in conhecidos
        return if (soltos.isEmpty() || outrosJaNaOrdem) grupos
        else grupos + GrupoDeAjustes(GruposDeAjustes.OUTROS, soltos)
    }

    /**
     * O texto sem espaços nas pontas; vazio → todos os grupos. Senão ficam os itens cujo título ou
     * descrição contém o texto, sem diferenciar maiúsculas (acentos diferenciam); grupo vazio some.
     */
    fun filtrar(grupos: List<GrupoDeAjustes>, busca: String): List<GrupoDeAjustes> {
        val termo = busca.trim()
        if (termo.isEmpty()) return grupos
        return grupos.mapNotNull { grupo ->
            val achados = grupo.itens.filter {
                it.titulo.contains(termo, ignoreCase = true) || it.descricao.contains(termo, ignoreCase = true)
            }
            if (achados.isEmpty()) null else GrupoDeAjustes(grupo.nome, achados)
        }
    }

    /**
     * "N ajustes · M ligados": todos os itens, sem a busca; M conta quem está ligado, inclusive os
     * sem interruptor; plural fixo (D9).
     */
    fun rodape(itens: List<ItemDeAjuste>): String =
        "${itens.size} ajustes · ${itens.count { it.ligado }} ligados"

    /** O primeiro grupo visível no topo da lista, limitado aos grupos que existem (D15). */
    fun grupoAtivo(primeiroVisivel: Int, quantidadeDeGrupos: Int): Int =
        primeiroVisivel.coerceIn(0, (quantidadeDeGrupos - 1).coerceAtLeast(0))
}

/** Parte pura do controle deslizante embutido de um [ItemDeAjuste] (caso 32, vetores V5). */
object ControleEmbutido {

    /** O item tem o controle completo: valor, faixa e quem recebe o valor. */
    fun temControle(item: ItemDeAjuste): Boolean =
        item.valor != null && item.faixa != null && item.aoMudarValor != null

    /** Tem área expandida: conteúdo extra ou controle completo. */
    fun expansivel(item: ItemDeAjuste): Boolean = item.conteudoExtra != null || temControle(item)

    /** O passo em uso: o do item, ou 1 quando nulo (ou não positivo: não divide por zero). */
    fun passo(passo: Int?): Int = passo?.takeIf { it > 0 } ?: 1

    /** Marcas intermediárias: (fim − início) / passo − 1, divisão inteira, nunca menos que 0. */
    fun marcas(faixa: IntRange, passo: Int?): Int =
        ((faixa.last - faixa.first) / passo(passo) - 1).coerceAtLeast(0)

    /** O valor mostrado: (valor / passo) × passo, divisão inteira (trunca para zero; D7). */
    fun valorMostrado(valor: Int, passo: Int?): Int {
        val p = passo(passo)
        return (valor / p) * p
    }

    /** O valor `x` que o controle entrega → trunc(x / passo) × passo, para `aoMudarValor` (D7). */
    fun valorEntregue(x: Float, passo: Int?): Int {
        val p = passo(passo)
        return (x / p).toInt() * p
    }
}
