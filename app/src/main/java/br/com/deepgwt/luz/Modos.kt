// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

enum class ModoMusical(val valorGravado: String, val rotulo: String) {
    GRAVES("BASS", "Graves"),
    ONDA_DO_ALBUM("ALBUM_WAVE", "Onda do álbum");

    companion object {
        @JvmField
        val PADRAO: ModoMusical = GRAVES

        /** Sem diferenciar maiúsculas; desconhecido → [PADRAO]. */
        @JvmStatic
        fun doValor(valor: String?): ModoMusical {
            val v = valor?.trim() ?: return PADRAO
            return entries.firstOrNull { it.valorGravado.equals(v, ignoreCase = true) } ?: PADRAO
        }
    }
}

/** Modo de condução lido de car.drive_setting.drive_mode, e a cor que a luz usa para ele. */
enum class ModoDeConducao(val cor: CorLed) {
    NORMAL(CorLed(255, 255, 255)),
    SPORT(CorLed(255, 0, 0)),
    ECO(CorLed(0, 255, 0)),
    NEVE(CorLed(0, 180, 255)),
    FORA_DE_ESTRADA(CorLed(255, 120, 0)),
    DESCONHECIDO(CorLed(255, 255, 255));

    companion object {
        /**
         * Valor cru do carro, sem espaços nas pontas e sem diferenciar maiúsculas. Os números são a
         * suposição de sempre (só o `0`, em Normal, foi visto no carro).
         */
        @JvmStatic
        fun doValor(cru: String?): ModoDeConducao = when (cru?.trim()?.uppercase()) {
            "0", "NORMAL" -> NORMAL
            "1", "SPORT" -> SPORT
            "2", "ECO" -> ECO
            "3", "SNOW", "NEVE" -> NEVE
            "4", "5", "OFFROAD", "AREIA", "LAMA" -> FORA_DE_ESTRADA
            else -> DESCONHECIDO
        }
    }
}

/** Como cada efeito do álbum anda quando é movido a quadro (caso 76). */
internal enum class AnimacaoDoAlbum { SEIS_CORES, ONDA, PULSO, CORRIDA, FLUXO }

/** A cor que abre a sequência dos efeitos de 6 cores. */
internal enum class AncoraDoAlbum { AZUL, VERDE, CIANO }

/**
 * Os 12 efeitos da onda do álbum. [modoDeReferencia] só diz se o efeito tem modo nativo no
 * controlador; o modo enviado de fato sai de [FamiliaDeCor].
 */
internal enum class EfeitoDoAlbum(
    val valorGravado: String,
    val rotulo: String,
    val curto: String,
    val modoDeReferencia: Int?,
    val animacao: AnimacaoDoAlbum,
    val sentido: Int,
    val ancora: AncoraDoAlbum? = null,
) {
    FORWARD_6_BU("FORWARD_6_BU", "13 Forward 6 Colors BU", "13 BU >", 13, AnimacaoDoAlbum.SEIS_CORES, +1, AncoraDoAlbum.AZUL),
    BACKWARD_6_BU("BACKWARD_6_BU", "14 Backward 6 Colors BU", "14 BU <", 14, AnimacaoDoAlbum.SEIS_CORES, -1, AncoraDoAlbum.AZUL),
    FORWARD_6_GN("FORWARD_6_GN", "11 Forward 6 Colors GN", "11 GN >", 11, AnimacaoDoAlbum.SEIS_CORES, +1, AncoraDoAlbum.VERDE),
    BACKWARD_6_GN("BACKWARD_6_GN", "12 Backward 6 Colors GN", "12 GN <", 12, AnimacaoDoAlbum.SEIS_CORES, -1, AncoraDoAlbum.VERDE),
    FORWARD_6_CN("FORWARD_6_CN", "15 Forward 6 Colors CN", "15 CN >", 15, AnimacaoDoAlbum.SEIS_CORES, +1, AncoraDoAlbum.CIANO),
    BACKWARD_6_CN("BACKWARD_6_CN", "16 Backward 6 Colors CN", "16 CN <", 16, AnimacaoDoAlbum.SEIS_CORES, -1, AncoraDoAlbum.CIANO),
    DIGITAL_WAVE("DIGITAL_WAVE", "Onda digital", "Wave", null, AnimacaoDoAlbum.ONDA, +1),
    PULSE("PULSE", "Pulse", "Pulse", null, AnimacaoDoAlbum.PULSO, +1),
    FORWARD_RUN_7("FORWARD_RUN_7", "95 Forward Run 7 Colors", "95 Run >", 95, AnimacaoDoAlbum.CORRIDA, +1),
    BACKWARD_RUN_7("BACKWARD_RUN_7", "96 Backward Run 7 Colors", "96 Run <", 96, AnimacaoDoAlbum.CORRIDA, -1),
    FORWARD_FLOW_7("FORWARD_FLOW_7", "123 Forward Flow 7 Colors", "123 Flow >", 123, AnimacaoDoAlbum.FLUXO, +1),
    BACKWARD_FLOW_7("BACKWARD_FLOW_7", "124 Backward Flow 7 Colors", "124 Flow <", 124, AnimacaoDoAlbum.FLUXO, -1);

    val temModoNativo: Boolean get() = modoDeReferencia != null

    companion object {
        val PADRAO = FORWARD_6_BU

        fun doValor(valor: String?): EfeitoDoAlbum {
            val v = valor?.trim() ?: return PADRAO
            return entries.firstOrNull { it.valorGravado.equals(v, ignoreCase = true) } ?: PADRAO
        }
    }
}

/** Animado ou estático, para cada saída do efeito do álbum. */
internal enum class ModoDoAlbum(val valorGravado: String, val rotulo: String) {
    ANIMADO("ANIMATED", "Animado"),
    ESTATICO("STATIC", "Estático");

    companion object {
        val PADRAO = ANIMADO

        fun doValor(valor: String?): ModoDoAlbum {
            val v = valor?.trim() ?: return PADRAO
            return entries.firstOrNull { it.valorGravado.equals(v, ignoreCase = true) } ?: PADRAO
        }
    }
}

/**
 * Família de cor que escolhe o modo nativo DMX (caso 72). A ordem das entradas é o desempate.
 * Pares (para frente, para trás) de cada coluna.
 */
internal enum class FamiliaDeCor(
    val referencia: CorLed,
    val seisCores: Pair<Int, Int>,
    val corrida: Pair<Int, Int>,
    val fluxo: Pair<Int, Int>,
) {
    VERMELHO(CorLed(255, 0, 0), 9 to 10, 97 to 98, 125 to 126),
    VERDE(CorLed(0, 255, 0), 11 to 12, 99 to 100, 125 to 126),
    AZUL(CorLed(0, 0, 255), 13 to 14, 101 to 102, 127 to 128),
    AMARELO(CorLed(255, 255, 0), 17 to 18, 103 to 104, 129 to 130),
    CIANO(CorLed(0, 255, 255), 15 to 16, 105 to 106, 131 to 132),
    VIOLETA(CorLed(255, 0, 255), 19 to 20, 107 to 108, 133 to 134),
    BRANCO(CorLed(255, 255, 255), 21 to 22, 109 to 110, 135 to 136);

    companion object {
        /** A mais próxima pela distância euclidiana ao quadrado; empate fica com a primeira. */
        fun maisProxima(cor: CorLed): FamiliaDeCor {
            val c = ContasDeCor.limitar(cor)
            var melhor = VERMELHO
            var menor = Long.MAX_VALUE
            for (f in entries) {
                val dr = (c.r - f.referencia.r).toLong()
                val dg = (c.g - f.referencia.g).toLong()
                val db = (c.b - f.referencia.b).toLong()
                val d = dr * dr + dg * dg + db * db
                if (d < menor) {
                    menor = d
                    melhor = f
                }
            }
            return melhor
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
