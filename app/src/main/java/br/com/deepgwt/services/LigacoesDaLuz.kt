// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.services

import br.com.deepgwt.luz.CoresDaCapa
import br.com.deepgwt.luz.FonteDeMidia
import br.com.deepgwt.luz.LuzAmbiente
import br.com.deepgwt.luz.MidiaTocando
import br.com.deepgwt.luz.ProvedorDeCoresDaCapa
import br.com.deepgwt.barra.MidiaDaBarra

/**
 * Liga a luz ambiente (br.com.deepgwt.luz) às duas fontes que ainda são de módulos antigos: o que
 * está tocando (mídia da barra inferior, já nova) e as quatro cores da capa (fundo do álbum). Some quando
 * esses módulos forem reescritos e passarem a registrar as fontes eles mesmos.
 */
import br.com.deepgwt.monitores.ExtratorDeCores

object LigacoesDaLuz {

    @JvmStatic
    fun ligar() {
        LuzAmbiente.fontes.midia = FonteDeMidia {
            val m = MidiaDaBarra.agora
            MidiaTocando(
                pacote = m.pacote,
                titulo = m.titulo,
                artista = m.artista,
                album = m.album,
                capa = m.capa,
                tocando = m.tocando,
            )
        }
        LuzAmbiente.fontes.coresDaCapa = ProvedorDeCoresDaCapa { capa, assinatura ->
            val c = ExtratorDeCores.extrair(capa, assinatura)
            CoresDaCapa(principal = c.principal, secundaria = c.secundaria, destaque = c.destaque, escura = c.escura)
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
