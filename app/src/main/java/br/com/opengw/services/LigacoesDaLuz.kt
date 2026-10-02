package br.com.opengw.services

import br.com.opengw.luz.CoresDaCapa
import br.com.opengw.luz.FonteDeMidia
import br.com.opengw.luz.LuzAmbiente
import br.com.opengw.luz.MidiaTocando
import br.com.opengw.luz.ProvedorDeCoresDaCapa
import br.com.opengw.barra.MidiaDaBarra

/**
 * Liga a luz ambiente (br.com.opengw.luz) às duas fontes que ainda são de módulos antigos: o que
 * está tocando (mídia da barra inferior, já nova) e as quatro cores da capa (fundo do álbum). Some quando
 * esses módulos forem reescritos e passarem a registrar as fontes eles mesmos.
 */
import br.com.opengw.monitores.ExtratorDeCores

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
