// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** V18–V20 (exatos) e V21 (±2 por canal, decisão 3 da sala suja). */
class PaletaDoAlbumTest {

    private fun cores(texto: String): List<CorLed> =
        Regex("""\((\d+),(\d+),(\d+)\)""").findAll(texto).map { m ->
            val (r, g, b) = m.destructured
            CorLed(r.toInt(), g.toInt(), b.toInt())
        }.toList()

    // ------------------------------------------------------------------ V18

    @Test
    fun `V18 modo nativo DMX pela cor`() {
        data class Linha(val cores: List<CorLed>, val modos: List<Int>)
        val tabela = listOf(
            Linha(cores("(255,0,0) (230,30,35) (200,120,0)"), listOf(9, 10, 97, 98, 125, 126)),
            Linha(cores("(0,255,0) (40,220,70)"), listOf(11, 12, 99, 100, 125, 126)),
            Linha(cores("(0,0,255) (30,50,230)"), listOf(13, 14, 101, 102, 127, 128)),
            Linha(cores("(255,255,0)"), listOf(17, 18, 103, 104, 129, 130)),
            Linha(cores("(0,255,255) (102,227,255)"), listOf(15, 16, 105, 106, 131, 132)),
            Linha(cores("(255,0,255)"), listOf(19, 20, 107, 108, 133, 134)),
            Linha(cores("(255,255,255) (220,220,220)"), listOf(21, 22, 109, 110, 135, 136)),
            Linha(cores("(127,127,127)"), listOf(9, 10, 97, 98, 125, 126)),
            Linha(cores("(0,0,0)"), listOf(9, 10, 97, 98, 125, 126)),
        )
        val avante = listOf(EfeitoDoAlbum.FORWARD_6_BU, EfeitoDoAlbum.FORWARD_6_GN, EfeitoDoAlbum.FORWARD_6_CN)
        val atras = listOf(EfeitoDoAlbum.BACKWARD_6_BU, EfeitoDoAlbum.BACKWARD_6_GN, EfeitoDoAlbum.BACKWARD_6_CN)
        for (l in tabela) for (c in l.cores) {
            for (e in avante) assertEquals("$e $c", l.modos[0], PaletaDoAlbum.modoNativo(e, c))
            for (e in atras) assertEquals("$e $c", l.modos[1], PaletaDoAlbum.modoNativo(e, c))
            assertEquals("run > $c", l.modos[2], PaletaDoAlbum.modoNativo(EfeitoDoAlbum.FORWARD_RUN_7, c))
            assertEquals("run < $c", l.modos[3], PaletaDoAlbum.modoNativo(EfeitoDoAlbum.BACKWARD_RUN_7, c))
            assertEquals("flow > $c", l.modos[4], PaletaDoAlbum.modoNativo(EfeitoDoAlbum.FORWARD_FLOW_7, c))
            assertEquals("flow < $c", l.modos[5], PaletaDoAlbum.modoNativo(EfeitoDoAlbum.BACKWARD_FLOW_7, c))
            assertNull(PaletaDoAlbum.modoNativo(EfeitoDoAlbum.DIGITAL_WAVE, c))
            assertNull(PaletaDoAlbum.modoNativo(EfeitoDoAlbum.PULSE, c))
        }
    }

    // ------------------------------------------------------------------ V19

    @Test
    fun `V19 velocidade para intervalo`() {
        val casos = mapOf(-10 to 520L, 1 to 520L, 2 to 516L, 10 to 483L, 25 to 421L, 50 to 317L, 75 to 214L, 99 to 114L, 100 to 110L, 140 to 110L)
        for ((v, ms) in casos) assertEquals("velocidade $v", ms, PaletaDoAlbum.intervaloMs(v))
    }

    // ------------------------------------------------------------------ V20

    private val reservaDaCapa = listOf(CorLed(31, 51, 58), CorLed(65, 53, 31), CorLed(102, 227, 255), CorLed(5, 7, 10))
    private val paletaDeReservaDaCapa = cores("(55,100,116) (54,133,153) (86,233,255) (77,143,146) (115,91,46) (115,93,54)")
    private val reservaFixa = cores("(66,227,255) (118,96,255) (255,110,64) (32,180,120)")

    private fun montar(c: List<CorLed>) = PaletaDoAlbum.montar(c[0], c[1], c[2], c[3])

    @Test
    fun `V20 paleta a partir das 4 cores`() {
        assertEquals(paletaDeReservaDaCapa, montar(reservaDaCapa))
        assertEquals(paletaDeReservaDaCapa, PaletaDoAlbum.montar(CoresDaCapa.RESERVA))
        assertEquals(
            cores("(24,104,185) (35,161,214) (48,229,249) (116,147,136) (184,63,23) (129,47,23) (16,65,122)"),
            montar(listOf(CorLed(0x22, 0x66, 0xAA), CorLed(0xAA, 0x44, 0x22), CorLed(0x44, 0xDD, 0xEE), CorLed(0x04, 0x09, 0x14))),
        )
        assertEquals(
            cores("(255,0,0) (146,116,0) (0,255,0) (0,135,135) (0,0,255) (2,2,214) (127,7,7)"),
            montar(listOf(CorLed(255, 0, 0), CorLed(0, 0, 255), CorLed(0, 255, 0), CorLed(20, 20, 20))),
        )
        assertEquals(reservaFixa, montar(List(4) { CorLed(0, 0, 0) }))
        assertEquals(reservaFixa, montar(List(4) { CorLed(200, 0, 0) }))
    }

    @Test
    fun `V20 cor estatica`() {
        assertEquals(CorLed(55, 100, 116), PaletaDoAlbum.corEstatica(paletaDeReservaDaCapa))
        assertEquals(CorLed(24, 104, 185), PaletaDoAlbum.corEstatica(cores("(24,104,185) (35,161,214)")))
        assertEquals(CorLed(20, 40, 90), PaletaDoAlbum.corEstatica(cores("(20,40,90) (230,60,40)")))
        assertEquals(CorLed(66, 227, 255), PaletaDoAlbum.corEstatica(emptyList()))
        assertEquals(CorLed(66, 227, 255), PaletaDoAlbum.corEstatica(listOf(CorLed(0, 0, 0))))
        assertEquals(CorLed(72, 36, 0), PaletaDoAlbum.corEstatica(listOf(CorLed(10, 5, 0))))
    }

    @Test
    fun `V20 intensidade`() {
        assertEquals(0.0, ContasDeCor.intensidade(CorLed(0, 0, 0)), 1e-9)
        assertEquals(1.0, ContasDeCor.intensidade(CorLed(255, 0, 0)), 1e-9)
        assertEquals(0.50196, ContasDeCor.intensidade(CorLed(10, 128, 3)), 1e-5)
        assertEquals(0.28235, ContasDeCor.intensidade(CorLed(72, 72, 72)), 1e-5)
    }

    @Test
    fun `assinatura so com as partes preenchidas`() {
        assertEquals("pkg|Música|300|300", PaletaDoAlbum.assinatura(listOf("pkg", "Música", null, "", "300", "300")))
        assertEquals("", PaletaDoAlbum.assinatura(listOf(null, "")))
    }

    // ------------------------------------------------------------------ V21

    private fun confere(efeito: EfeitoDoAlbum, paleta: List<CorLed>, esperado: String, primeiro: Int = 0) {
        cores(esperado).forEachIndexed { i, e ->
            val passo = primeiro + i
            val c = PaletaDoAlbum.corAnimada(efeito, paleta, passo)
            val dif = maxOf(abs(c.r - e.r), abs(c.g - e.g), abs(c.b - e.b))
            assertTrue("$efeito passo $passo: $c, esperado $e", dif <= 2)
        }
    }

    private val q = cores("(220,30,40) (30,80,230) (40,220,140) (210,190,30)")

    @Test
    fun `V21 paleta Q`() {
        confere(EfeitoDoAlbum.FORWARD_6_BU, q, "(27,71,205) (38,157,210) (57,216,165) (91,214,108) (127,143,39) (141,88,22) (144,46,32) (161,41,84) (91,74,199) (36,130,231) (40,180,154) (50,143,87)")
        confere(EfeitoDoAlbum.BACKWARD_6_BU, q, "(27,71,205) (167,53,121) (221,66,67) (218,109,35) (153,136,22) (77,136,59) (43,138,92) (33,148,171) (35,101,249) (130,65,161) (181,50,69) (151,50,27)")
        val gnAvante = "(36,196,125) (127,229,102) (202,205,57) (216,145,33) (159,47,28) (100,32,71) (49,52,128) (31,109,195) (41,203,184) (94,237,125) (164,187,55) (140,113,28)"
        confere(EfeitoDoAlbum.FORWARD_6_GN, q, gnAvante)
        confere(EfeitoDoAlbum.FORWARD_6_CN, q, gnAvante)
        val gnAtras = "(36,196,125) (38,145,219) (66,99,215) (125,57,139) (157,23,32) (142,78,23) (132,118,30) (125,192,75) (60,243,146) (40,175,202) (47,96,191) (55,48,127)"
        confere(EfeitoDoAlbum.BACKWARD_6_GN, q, gnAtras)
        confere(EfeitoDoAlbum.BACKWARD_6_CN, q, gnAtras)
        confere(EfeitoDoAlbum.DIGITAL_WAVE, q, "(160,43,40) (125,64,129) (62,76,202) (51,105,194) (39,142,143) (27,124,97) (44,108,68) (83,107,39) (114,113,28) (172,96,36) (216,54,41) (174,58,89)")
        confere(EfeitoDoAlbum.PULSE, q, "(114,40,92) (118,74,165) (119,98,161) (122,112,130) (111,63,59) (85,30,27) (83,28,26) (99,41,33) (154,109,62) (204,153,63) (231,149,47) (212,112,35)")
        confere(EfeitoDoAlbum.FORWARD_RUN_7, q, "(180,30,32) (88,65,174) (35,112,203) (28,152,102) (99,125,40) (150,109,25) (217,36,39) (88,65,174) (29,93,167) (25,134,90) (103,130,41) (195,141,32)")
        confere(EfeitoDoAlbum.BACKWARD_RUN_7, q, "(180,30,32) (212,153,35) (156,197,62) (28,152,102) (22,71,129) (63,46,123) (217,36,39) (212,153,35) (129,163,51) (25,134,90) (23,74,134) (81,60,160)")
        confere(EfeitoDoAlbum.FORWARD_FLOW_7, q, "(160,43,40) (142,61,111) (81,73,181) (52,81,205) (46,94,164) (30,95,108) (22,103,87) (29,107,79) (66,121,65) (144,164,48) (193,178,42) (205,131,43)")
        confere(EfeitoDoAlbum.BACKWARD_FLOW_7, q, "(160,43,40) (210,61,39) (208,111,43) (196,167,43) (156,166,42) (81,124,56) (37,107,74) (23,106,85) (27,102,102) (43,105,153) (51,88,202) (67,75,197)")
    }

    @Test
    fun `V21 paleta de reserva da capa`() {
        val p = paletaDeReservaDaCapa
        val seisAvante = "(77,207,227) (90,198,210) (100,142,130) (116,92,47) (84,67,38) (65,72,63) (38,73,85) (51,125,144)"
        val seisAtras = "(77,207,227) (65,163,185) (62,128,148) (75,99,98) (82,68,41) (76,61,32) (68,79,62) (75,158,165)"
        for (e in listOf(EfeitoDoAlbum.FORWARD_6_BU, EfeitoDoAlbum.FORWARD_6_GN, EfeitoDoAlbum.FORWARD_6_CN)) confere(e, p, seisAvante)
        for (e in listOf(EfeitoDoAlbum.BACKWARD_6_BU, EfeitoDoAlbum.BACKWARD_6_GN, EfeitoDoAlbum.BACKWARD_6_CN)) confere(e, p, seisAtras)
        confere(EfeitoDoAlbum.DIGITAL_WAVE, p, "(49,78,85) (56,113,126) (56,129,148) (61,152,171) (64,167,184) (50,135,149) (44,109,118) (43,93,98)")
        confere(EfeitoDoAlbum.PULSE, p, "(43,89,103) (62,138,159) (68,158,180) (70,164,184) (46,101,113) (31,63,72) (31,64,72) (34,70,79)")
        confere(EfeitoDoAlbum.FORWARD_RUN_7, p, "(48,82,92) (54,132,152) (83,225,246) (55,106,109) (72,60,32) (82,67,39) (58,99,111) (54,132,152)")
        confere(EfeitoDoAlbum.BACKWARD_RUN_7, p, "(48,82,92) (116,94,54) (111,93,51) (55,106,109) (53,143,156) (39,94,108) (58,99,111) (116,94,54)")
        confere(EfeitoDoAlbum.FORWARD_FLOW_7, p, "(49,78,85) (58,109,122) (57,126,143) (56,134,154) (53,133,150) (45,116,129) (44,115,126) (44,115,126)")
        confere(EfeitoDoAlbum.BACKWARD_FLOW_7, p, "(49,78,85) (65,96,101) (85,98,87) (106,94,64) (99,81,47) (72,61,37) (72,65,41) (72,67,43)")
    }

    @Test
    fun `V21 paleta de uma cor`() {
        val p = listOf(CorLed(200, 100, 0))
        val onda = "(172,86,0) (213,106,0) (220,110,0) (218,109,0) (190,95,0) (142,71,0)"
        confere(EfeitoDoAlbum.DIGITAL_WAVE, p, onda)
        confere(EfeitoDoAlbum.FORWARD_FLOW_7, p, onda)
        confere(EfeitoDoAlbum.PULSE, p, "(158,79,0) (221,111,0) (228,114,0) (215,108,0) (144,72,0) (91,46,0)")
        confere(EfeitoDoAlbum.FORWARD_6_BU, p, "(178,89,0) (221,111,0) (224,112,0) (203,101,0) (146,73,0) (132,66,0)")
        confere(EfeitoDoAlbum.FORWARD_RUN_7, p, "(164,82,0) (202,101,0) (197,99,0) (142,71,0) (125,63,0) (143,72,0)")
    }

    @Test
    fun `V21 outros`() {
        confere(EfeitoDoAlbum.DIGITAL_WAVE, emptyList(), "(51,164,184) (87,157,240) (111,119,255)")
        confere(EfeitoDoAlbum.DIGITAL_WAVE, q, "(98,108,29)", primeiro = -3)
        confere(EfeitoDoAlbum.DIGITAL_WAVE, q, "(127,35,29)", primeiro = 1000)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
