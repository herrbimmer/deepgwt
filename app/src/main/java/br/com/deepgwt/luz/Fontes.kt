// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import android.graphics.Bitmap

/**
 * O que está tocando agora, para a onda do álbum. Quem fornece é um módulo de fora (registrado em
 * [LuzAmbiente.fontes]); a luz só lê.
 */
data class MidiaTocando(
    val pacote: String?,
    val titulo: String?,
    val artista: String?,
    val album: String?,
    val capa: Bitmap?,
    val tocando: Boolean,
) {
    /** Título, artista ou álbum preenchido, ou há capa (só o pacote não conta). */
    val temAlbum: Boolean
        get() = !titulo.isNullOrBlank() || !artista.isNullOrBlank() || !album.isNullOrBlank() || capa != null
}

/** Lida sempre na thread principal. null = nada tocando / sem informação. */
fun interface FonteDeMidia {
    fun agora(): MidiaTocando?
}

/** As quatro cores tiradas da capa, como `Int` ARGB. */
data class CoresDaCapa(
    val principal: Int,
    val secundaria: Int,
    val destaque: Int,
    val escura: Int,
) {
    companion object {
        /** Sem capa (ou sem provedor): principal (31,51,58), secundária (65,53,31), destaque (102,227,255), escura (5,7,10). */
        @JvmField
        val RESERVA = CoresDaCapa(
            principal = argb(31, 51, 58),
            secundaria = argb(65, 53, 31),
            destaque = argb(102, 227, 255),
            escura = argb(5, 7, 10),
        )

        private fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

/** Extrai as 4 cores de uma capa. Chamado fora da thread principal, uma vez por faixa. */
fun interface ProvedorDeCoresDaCapa {
    fun cores(capa: Bitmap?, assinatura: String): CoresDaCapa
}

/**
 * Ponto de registro das duas fontes que vêm de fora do módulo. Enquanto ninguém registrar, a luz
 * se comporta como "sem mídia" e usa as cores de reserva.
 */
class FontesDaLuz internal constructor() {

    @Volatile
    var midia: FonteDeMidia = SEM_MIDIA

    @Volatile
    var coresDaCapa: ProvedorDeCoresDaCapa = CORES_DE_RESERVA

    /** Volta aos padrões (sem mídia, cores de reserva). */
    fun restaurarPadroes() {
        midia = SEM_MIDIA
        coresDaCapa = CORES_DE_RESERVA
    }

    companion object {
        @JvmField
        val SEM_MIDIA = FonteDeMidia { null }

        @JvmField
        val CORES_DE_RESERVA = ProvedorDeCoresDaCapa { _, _ -> CoresDaCapa.RESERVA }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
