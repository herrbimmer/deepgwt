package br.com.opengw.telas

import br.com.opengw.painel.PainelDeInstrumentos

/**
 * O painel de instrumentos projetado pelo app, para o nosso código: aplicar na hora um ajuste que
 * mudou, sem recriar a projeção. Desde a 3.6 delega ao painel próprio ([PainelDeInstrumentos]). Sem
 * projeção aberta, nada acontece.
 */
object Painel {

    /** Tamanho dos números dos mostradores. */
    @JvmStatic
    fun aplicarEscalaDosNumeros() = PainelDeInstrumentos.aplicarEscalaDosNumeros()

    /** Cor do modo noturno. */
    @JvmStatic
    fun aplicarCorDoModoNoturno() = PainelDeInstrumentos.aplicarCorDoModoNoturno()

    /** Tamanho da temperatura sobre o painel de fábrica. */
    @JvmStatic
    fun aplicarEscalaDaTemperatura() = PainelDeInstrumentos.aplicarEscalaDaTemperatura()

    /**
     * Reenvia ao tema o estado do farol, forçado: a regra mudou (fonte do modo noturno), não o
     * carro, e a deduplicação normal engoliria a troca.
     */
    @JvmStatic
    fun republicarFarol() = PainelDeInstrumentos.republicarFarol()
}
