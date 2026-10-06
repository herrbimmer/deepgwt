package br.com.deepgwt.tela3d.ponte

/** O que fazer depois de uma queda do renderizador: quanto esperar antes de recriar a Activity. */
data class DecisaoDeRecuperacao(val esperaMs: Long, val quedasRecentes: Int)

/**
 * A política da queda do processo de renderização do WebView. Guarda os instantes das últimas
 * [CAPACIDADE] quedas; se todas caíram dentro da janela de 5 minutos a recriação espera 30 s (para o
 * app não entrar num laço de recriar e cair de novo), senão recria já.
 *
 * O relógio é injetado (monotônico em produção). A instância que vale no app é uma só, no processo,
 * para sobreviver à recriação da Activity: ver [QuedasDoRenderizador].
 */
class AnelDeQuedas(
    private val relogio: () -> Long,
    private val janelaMs: Long = JANELA_MS,
    private val esperaMs: Long = ESPERA_MS,
) {
    private val instantes = LongArray(CAPACIDADE) { SEM_QUEDA }
    private var proxima = 0

    /** Registra uma queda agora e devolve a decisão. */
    @Synchronized
    fun registrar(): DecisaoDeRecuperacao {
        val agora = relogio()
        instantes[proxima] = agora
        proxima = (proxima + 1) % CAPACIDADE
        val recentes = instantes.count { it != SEM_QUEDA && agora - it < janelaMs }
        return DecisaoDeRecuperacao(if (recentes >= CAPACIDADE) esperaMs else 0L, recentes)
    }

    companion object {
        const val CAPACIDADE = 3
        const val JANELA_MS = 300_000L
        const val ESPERA_MS = 30_000L
        private const val SEM_QUEDA = Long.MIN_VALUE
    }
}

/** A forma de carregar a página na criação da Activity. */
sealed interface CargaInicial {
    /** Carrega a URL do zero; [semSplash] acrescenta `&nosplash`. */
    data class Carregar(val semSplash: Boolean) : CargaInicial

    /** Restaura o estado salvo do WebView (sem recarregar a URL); se não restaurar nada, carrega. */
    data object Restaurar : CargaInicial
}

object DecisaoDeCarga {

    /**
     * - Sem estado salvo: carga normal (com `&nosplash` só se o intent trouxe o extra).
     * - Estado salvo com "renderizador perdido": do zero e com `&nosplash` (a vinheta já tocou).
     * - Estado salvo por outro motivo (recriação comum): restaura.
     */
    fun decidir(temEstadoSalvo: Boolean, renderizadorPerdido: Boolean, extraNosplash: Boolean): CargaInicial = when {
        !temEstadoSalvo -> CargaInicial.Carregar(semSplash = extraNosplash)
        renderizadorPerdido -> CargaInicial.Carregar(semSplash = true)
        else -> CargaInicial.Restaurar
    }
}
