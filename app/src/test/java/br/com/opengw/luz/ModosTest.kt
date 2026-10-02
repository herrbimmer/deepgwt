package br.com.opengw.luz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V10 (exato) e V11, mais a leitura dos modos gravados. */
class ModosTest {

    @Test
    fun `V10 modo de conducao`() {
        val casos = mapOf(
            "0" to ModoDeConducao.NORMAL, "NORMAL" to ModoDeConducao.NORMAL,
            "1" to ModoDeConducao.SPORT, "Sport" to ModoDeConducao.SPORT,
            "2" to ModoDeConducao.ECO, " 2 " to ModoDeConducao.ECO, "eco" to ModoDeConducao.ECO,
            "3" to ModoDeConducao.NEVE, "SNOW" to ModoDeConducao.NEVE, "NEVE" to ModoDeConducao.NEVE,
            "4" to ModoDeConducao.FORA_DE_ESTRADA, "5" to ModoDeConducao.FORA_DE_ESTRADA,
            "OFFROAD" to ModoDeConducao.FORA_DE_ESTRADA, "areia" to ModoDeConducao.FORA_DE_ESTRADA,
            "LAMA" to ModoDeConducao.FORA_DE_ESTRADA,
            "6" to ModoDeConducao.DESCONHECIDO, "-1" to ModoDeConducao.DESCONHECIDO, "abc" to ModoDeConducao.DESCONHECIDO,
            "1.0" to ModoDeConducao.DESCONHECIDO, "" to ModoDeConducao.DESCONHECIDO,
        )
        for ((cru, modo) in casos) assertEquals("'$cru'", modo, ModoDeConducao.doValor(cru))
        assertEquals(ModoDeConducao.DESCONHECIDO, ModoDeConducao.doValor(null))
    }

    @Test
    fun `V10 cores dos modos`() {
        assertEquals(CorLed(255, 255, 255), ModoDeConducao.NORMAL.cor)
        assertEquals(CorLed(255, 0, 0), ModoDeConducao.SPORT.cor)
        assertEquals(CorLed(0, 255, 0), ModoDeConducao.ECO.cor)
        assertEquals(CorLed(0, 180, 255), ModoDeConducao.NEVE.cor)
        assertEquals(CorLed(255, 120, 0), ModoDeConducao.FORA_DE_ESTRADA.cor)
        assertEquals(CorLed(255, 255, 255), ModoDeConducao.DESCONHECIDO.cor)
    }

    @Test
    fun `V11 efeitos do album`() {
        data class Linha(val valor: String, val rotulo: String, val curto: String, val modo: Int?)
        val tabela = listOf(
            Linha("FORWARD_6_BU", "13 Forward 6 Colors BU", "13 BU >", 13),
            Linha("BACKWARD_6_BU", "14 Backward 6 Colors BU", "14 BU <", 14),
            Linha("FORWARD_6_GN", "11 Forward 6 Colors GN", "11 GN >", 11),
            Linha("BACKWARD_6_GN", "12 Backward 6 Colors GN", "12 GN <", 12),
            Linha("FORWARD_6_CN", "15 Forward 6 Colors CN", "15 CN >", 15),
            Linha("BACKWARD_6_CN", "16 Backward 6 Colors CN", "16 CN <", 16),
            Linha("DIGITAL_WAVE", "Onda digital", "Wave", null),
            Linha("PULSE", "Pulse", "Pulse", null),
            Linha("FORWARD_RUN_7", "95 Forward Run 7 Colors", "95 Run >", 95),
            Linha("BACKWARD_RUN_7", "96 Backward Run 7 Colors", "96 Run <", 96),
            Linha("FORWARD_FLOW_7", "123 Forward Flow 7 Colors", "123 Flow >", 123),
            Linha("BACKWARD_FLOW_7", "124 Backward Flow 7 Colors", "124 Flow <", 124),
        )
        assertEquals(tabela.size, EfeitoDoAlbum.entries.size)
        for (l in tabela) {
            val e = EfeitoDoAlbum.doValor(l.valor)
            assertEquals(l.valor, e.valorGravado)
            assertEquals(l.rotulo, e.rotulo)
            assertEquals(l.curto, e.curto)
            assertEquals(l.modo, e.modoDeReferencia)
        }
        assertEquals(EfeitoDoAlbum.FORWARD_6_BU, EfeitoDoAlbum.PADRAO)
        assertEquals(EfeitoDoAlbum.PULSE, EfeitoDoAlbum.doValor("pulse"))
        assertEquals(EfeitoDoAlbum.FORWARD_6_BU, EfeitoDoAlbum.doValor("QUALQUER"))
        assertEquals(EfeitoDoAlbum.FORWARD_6_BU, EfeitoDoAlbum.doValor(null))
        assertFalse(EfeitoDoAlbum.DIGITAL_WAVE.temModoNativo)
        assertTrue(EfeitoDoAlbum.FORWARD_FLOW_7.temModoNativo)
        assertNull(EfeitoDoAlbum.PULSE.modoDeReferencia)
    }

    @Test
    fun `modo musical`() {
        assertEquals(ModoMusical.GRAVES, ModoMusical.PADRAO)
        assertEquals(ModoMusical.ONDA_DO_ALBUM, ModoMusical.doValor("album_wave"))
        assertEquals(ModoMusical.GRAVES, ModoMusical.doValor("BASS"))
        assertEquals(ModoMusical.GRAVES, ModoMusical.doValor("outro"))
        assertEquals(ModoMusical.GRAVES, ModoMusical.doValor(null))
        assertEquals(listOf("BASS", "ALBUM_WAVE"), ModoMusical.entries.map { it.valorGravado })
        assertEquals(listOf("Graves", "Onda do álbum"), ModoMusical.entries.map { it.rotulo })
    }

    @Test
    fun `modo animado ou estatico do album`() {
        assertEquals(ModoDoAlbum.ESTATICO, ModoDoAlbum.doValor("static"))
        assertEquals(ModoDoAlbum.ANIMADO, ModoDoAlbum.doValor("ANIMATED"))
        assertEquals(ModoDoAlbum.ANIMADO, ModoDoAlbum.doValor("x"))
        assertEquals(ModoDoAlbum.ANIMADO, ModoDoAlbum.doValor(null))
    }
}
