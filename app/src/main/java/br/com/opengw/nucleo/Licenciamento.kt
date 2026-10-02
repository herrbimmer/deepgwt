package br.com.opengw.nucleo

import br.com.opengw.licenca.Portao
import br.com.opengw.licenca.Recurso

/**
 * O que o nível de licença do carro libera, para os módulos novos não dependerem do pacote da
 * licença. Delega ao `Portao` (código nosso, que muda de pacote na Fase 4).
 */
object Licenciamento {

    /** A luz ambiente (fita BLE/DMX) está liberada neste nível. */
    @JvmStatic
    fun luzAmbiente(): Boolean = Portao.permite(Recurso.LUZ_AMBIENTE)
}
