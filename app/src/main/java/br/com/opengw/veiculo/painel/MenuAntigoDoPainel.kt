package br.com.opengw.veiculo.painel

/**
 * Tema esportivo antigo (menu desenhado pelo painel projetado herdado). Só para a convivência: o
 * módulo 3.6 decidiu NÃO portar esse legado (`projetores-menu-antigo.md`); quando ele sair, a sala
 * suja liga [VAZIO] e esta interface pode ser apagada.
 */
interface MenuAntigoDoPainel {
    /** Cartão que o menu antigo acha que está na tela (−1 = não sabe). */
    fun cartaoAtual(): Int

    /** Tecla para o menu antigo; [nome] é o nome de `br.com.opengw.painel.TeclaDoVolante`. */
    fun tecla(nome: String)

    companion object {
        /** Sem menu antigo: cartão −1, teclas ignoradas. */
        @JvmField
        val VAZIO: MenuAntigoDoPainel = object : MenuAntigoDoPainel {
            override fun cartaoAtual(): Int = -1
            override fun tecla(nome: String) {}
        }
    }
}
