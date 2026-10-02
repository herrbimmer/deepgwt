package br.com.opengw.nucleo

import android.content.Context
import br.com.opengw.app.AplicacaoOpenGw

/**
 * O contexto do app para o código do OPEN GW.
 *
 * Fachada da independência (`docs/independencia/PLANO.md`, 2.1). Lê da Application nossa
 * ([AplicacaoOpenGw], 3.9b).
 */
object OpenGw {

    /** Contexto da aplicação. */
    @JvmStatic
    val contexto: Context
        get() = AplicacaoOpenGw.contexto

    /**
     * Contexto do armazenamento protegido do aparelho: legível antes do primeiro desbloqueio,
     * que é o caso da central ao ligar o carro. É onde ficam as preferências do app.
     */
    @JvmStatic
    val contextoProtegido: Context
        get() = AplicacaoOpenGw.contextoProtegido
}
