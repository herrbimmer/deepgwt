package br.com.opengw

import br.com.opengw.app.AplicacaoOpenGw
import br.com.opengw.app.LigacoesDoApp
import br.com.opengw.services.LigacoesDoCarro

/**
 * A Application do manifesto: o esqueleto é [AplicacaoOpenGw] (3.9b); aqui só entram as ligações.
 * O nome antigo fica até a Fase 4 (manifesto, `applicationId`).
 */
class App : AplicacaoOpenGw() {
    override fun criarLigacoes(): LigacoesDoApp = LigacoesDoCarro
}
