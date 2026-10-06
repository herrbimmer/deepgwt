package br.com.deepgwt.tela3d.cartoes

import br.com.deepgwt.tela3d.ponte.CartaoInferior
import br.com.deepgwt.tela3d.ponte.ConfigDosCartoes
import br.com.deepgwt.tela3d.ponte.LinhaDeMenu

/** Reconstruir × atualizar: a decisão de A3. */
object RegrasDaLinha {
    /**
     * Mesma estrutura = mesma quantidade; mesma ordem de `id`; mesmos `title` e `action`; no relógio, as seis
     * opções de relógio iguais. Tudo o mais (valores, estado, menus) se atualiza sem reconstruir.
     */
    fun mesmaEstrutura(a: List<CartaoInferior>, b: List<CartaoInferior>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            val x = a[i]
            val y = b[i]
            if (x.id != y.id || x.titulo != y.titulo || x.acao != y.acao) return false
            if (x.id == "clock" && x.relogio != y.relogio) return false
        }
        return true
    }

    /** Os cartões que realmente aparecem: os primeiros `limite`. */
    fun visiveis(config: ConfigDosCartoes): List<CartaoInferior> =
        config.cartoes.take(config.limite.coerceAtLeast(0))

    /**
     * Verdadeiro quando é preciso reconstruir a linha do zero: não havia configuração, o limite mudou ou a
     * estrutura mudou. Compara as listas completas recebidas (a ponte já as limitou a 16).
     */
    fun deveReconstruir(anterior: ConfigDosCartoes?, nova: ConfigDosCartoes): Boolean {
        if (anterior == null) return true
        if (anterior.limite != nova.limite) return true
        return !mesmaEstrutura(visiveis(anterior), visiveis(nova))
    }
}

/** O que um toque (ou toque longo) num cartão deve fazer. */
sealed interface AcaoDoToque {
    data object Nada : AcaoDoToque
    data class Comando(val comando: String) : AcaoDoToque
    data class AbrirMenu(val linhas: List<LinhaDeMenu>) : AcaoDoToque
    data class AbrirApp(val pacote: String) : AcaoDoToque
}

object PoliticaDeToque {
    const val ABRIR_EDICAO = "openRailEdit"

    /** T1: com menu abre o menu; senão envia a ação do cartão (se houver). */
    fun aoTocar(c: CartaoInferior): AcaoDoToque = when {
        c.menu.isNotEmpty() -> AcaoDoToque.AbrirMenu(c.menu)
        c.acao.isNotEmpty() -> AcaoDoToque.Comando(c.acao)
        else -> AcaoDoToque.Nada
    }

    /** T3: o toque no gráfico de um cartão de modo envia a ação do ícone (e só ela). */
    fun aoTocarNoGrafico(c: CartaoInferior): AcaoDoToque =
        if (c.acaoDoIcone.isNotEmpty()) AcaoDoToque.Comando(c.acaoDoIcone) else AcaoDoToque.Nada

    /** T2 (corrigido): o toque longo respeita `longAction`; sem ela abre a edição da linha. */
    fun aoTocarLongo(c: CartaoInferior): String = c.acaoLonga.ifEmpty { ABRIR_EDICAO }

    /** M3: abre o app de origem se dá; senão a ação do descritor; senão nada. */
    fun aoTocarNaMidia(podeAbrir: Boolean, pacote: String, acaoDoDescritor: String): AcaoDoToque = when {
        podeAbrir && pacote.isNotEmpty() -> AcaoDoToque.AbrirApp(pacote)
        acaoDoDescritor.isNotEmpty() -> AcaoDoToque.Comando(acaoDoDescritor)
        else -> AcaoDoToque.Nada
    }

    // --- comandos da edição ---

    fun comandoDeMover(id: String, indice: Int): String = "railMoveCard:$id:$indice"
    fun comandoDeRemover(id: String): String = "railRemoveCard:$id"
}

/** Pontinhos de passo dos cartões de modo (AP3.13). */
object PontosDePasso {
    const val QUANTIDADE = 3

    /** `round(3 × progresso/100) − 1`; progresso ≤ 0 → nenhum (−1). Com 1..16 também nenhum (é o comportamento). */
    fun indiceDestacado(progresso: Int): Int {
        if (progresso <= 0) return -1
        val p = progresso.coerceAtMost(100)
        return (6 * p + 100) / 200 - 1
    }
}
