// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt

import br.com.deepgwt.app.AplicacaoDeepGwt
import br.com.deepgwt.app.LigacoesDoApp
import br.com.deepgwt.services.LigacoesDoCarro
import br.com.deepgwt.tela3d.integracao.FabricaDaTela3dReal

/**
 * A Application do manifesto: o esqueleto é [AplicacaoDeepGwt] (3.9b); aqui só entram as ligações.
 * O nome antigo fica até a Fase 4 (manifesto, `applicationId`).
 */
class App : AplicacaoDeepGwt() {
    override fun onCreate() {
        // Antes de qualquer Activity: a tela inicial 3D monta as dependências pela fábrica registrada.
        FabricaDaTela3dReal.registrar()
        super.onCreate()
    }

    override fun criarLigacoes(): LigacoesDoApp = LigacoesDoCarro
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
