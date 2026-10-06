// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V1–V7 e V9 da especificação: exatos, byte a byte. */
class QuadrosTest {

    private fun hex(q: ByteArray) = Hex.deBytes(q)
    private fun hex(q: List<ByteArray>) = q.map { Hex.deBytes(it) }

    @Test
    fun `V1 cor com as ordens padrao`() {
        val casos = listOf(
            CorLed(255, 0, 0) to ("7B0007FF000000FFBF" to "7EFF0503FF0000FFEF"),
            CorLed(0, 255, 0) to ("7B00070000FF00FFBF" to "7EFF050300FF00FFEF"),
            CorLed(0, 0, 255) to ("7B000700FF0000FFBF" to "7EFF05030000FFFFEF"),
            CorLed(255, 255, 255) to ("7B0007FFFFFF00FFBF" to "7EFF0503FFFFFFFFEF"),
            CorLed(0, 0, 0) to ("7B000700000000FFBF" to "7EFF0503000000FFEF"),
            CorLed(80, 120, 255) to ("7B000750FF7800FFBF" to "7EFF05035078FFFFEF"),
            CorLed(0, 180, 255) to ("7B000700FFB400FFBF" to "7EFF050300B4FFFFEF"),
            CorLed(255, 120, 0) to ("7B0007FF007800FFBF" to "7EFF0503FF7800FFEF"),
            CorLed(255, 176, 59) to ("7B0007FF3BB000FFBF" to "7EFF0503FFB03BFFEF"),
            CorLed(255, 255, 0) to ("7B0007FF00FF00FFBF" to "7EFF0503FFFF00FFEF"),
            CorLed(255, 0, 255) to ("7B0007FFFF0000FFBF" to "7EFF0503FF00FFFFEF"),
            CorLed(10, 20, 30) to ("7B00070A1E1400FFBF" to "7EFF05030A141EFFEF"),
            CorLed(300, -1, 0) to ("7B0007FF000000FFBF" to "7EFF0503FF0000FFEF"),
            CorLed(128, 200, 7) to ("7B00078007C800FFBF" to "7EFF050380C807FFEF"),
        )
        for ((cor, esperado) in casos) {
            assertEquals("DMX $cor", esperado.first, hex(Quadros.corDmx(cor, OrdemDeCor.PADRAO_DMX)))
            assertEquals("BLE $cor", esperado.second, hex(Quadros.corBle(cor, OrdemDeCor.PADRAO_BLE)))
        }
    }

    @Test
    fun `V2 as seis ordens`() {
        val cor = CorLed(10, 20, 30)
        val casos = mapOf(
            OrdemDeCor.RGB to ("7B00070A141E00FFBF" to "7EFF05030A141EFFEF"),
            OrdemDeCor.RBG to ("7B00070A1E1400FFBF" to "7EFF05030A1E14FFEF"),
            OrdemDeCor.GRB to ("7B0007140A1E00FFBF" to "7EFF0503140A1EFFEF"),
            OrdemDeCor.GBR to ("7B0007141E0A00FFBF" to "7EFF0503141E0AFFEF"),
            OrdemDeCor.BRG to ("7B00071E0A1400FFBF" to "7EFF05031E0A14FFEF"),
            OrdemDeCor.BGR to ("7B00071E140A00FFBF" to "7EFF05031E140AFFEF"),
        )
        for ((ordem, esperado) in casos) {
            assertEquals("DMX $ordem", esperado.first, hex(Quadros.corDmx(cor, ordem)))
            assertEquals("BLE $ordem", esperado.second, hex(Quadros.corBle(cor, ordem)))
        }
    }

    @Test
    fun `V2 limite antes da ordem`() {
        val cor = CorLed(300, -5, 128)
        val esperado = mapOf(
            OrdemDeCor.RGB to listOf(255, 0, 128),
            OrdemDeCor.RBG to listOf(255, 128, 0),
            OrdemDeCor.GRB to listOf(0, 255, 128),
            OrdemDeCor.GBR to listOf(0, 128, 255),
            OrdemDeCor.BRG to listOf(128, 255, 0),
            OrdemDeCor.BGR to listOf(128, 0, 255),
        )
        for ((ordem, canais) in esperado) {
            val q = Quadros.corBle(cor, ordem)
            assertEquals(ordem.name, canais, listOf(q[4], q[5], q[6]).map { it.toInt() and 0xFF })
        }
    }

    @Test
    fun `V3 saida`() {
        val cor = CorLed(10, 20, 30)
        fun quadros(s: SaidaDaLuz) = hex(Quadros.cor(cor, s, OrdemDeCor.RBG, OrdemDeCor.RGB))
        assertEquals(listOf("7EFF05030A141EFFEF"), quadros(SaidaDaLuz.BLE))
        assertEquals(listOf("7B00070A1E1400FFBF"), quadros(SaidaDaLuz.DMX))
        assertEquals(listOf("7B00070A1E1400FFBF", "7EFF05030A141EFFEF"), quadros(SaidaDaLuz.AMBAS))
    }

    @Test
    fun `V4 brilho`() {
        val casos = listOf(
            -5 to ("7BFF01000000FFFFBF" to "7EFF010000FFFFFFEF"),
            0 to ("7BFF01000000FFFFBF" to "7EFF010000FFFFFFEF"),
            1 to ("7BFF01000100FFFFBF" to "7EFF010100FFFFFFEF"),
            3 to ("7BFF01000300FFFFBF" to "7EFF010300FFFFFFEF"),
            4 to ("7BFF01010400FFFFBF" to "7EFF010400FFFFFFEF"),
            10 to ("7BFF01030A00FFFFBF" to "7EFF010A00FFFFFFEF"),
            25 to ("7BFF01081900FFFFBF" to "7EFF011900FFFFFFEF"),
            33 to ("7BFF010A2100FFFFBF" to "7EFF012100FFFFFFEF"),
            50 to ("7BFF01103200FFFFBF" to "7EFF013200FFFFFFEF"),
            75 to ("7BFF01184B00FFFFBF" to "7EFF014B00FFFFFFEF"),
            80 to ("7BFF01195000FFFFBF" to "7EFF015000FFFFFFEF"),
            99 to ("7BFF011F6300FFFFBF" to "7EFF016300FFFFFFEF"),
            100 to ("7BFF01206400FFFFBF" to "7EFF016400FFFFFFEF"),
            150 to ("7BFF01206400FFFFBF" to "7EFF016400FFFFFFEF"),
        )
        for ((p, esperado) in casos) {
            assertEquals("DMX $p", esperado.first, hex(Quadros.brilhoDmx(p)))
            assertEquals("BLE $p", esperado.second, hex(Quadros.brilhoBle(p)))
        }
        assertEquals(listOf("7BFF01195000FFFFBF", "7EFF015000FFFFFFEF"), hex(Quadros.brilho(80, SaidaDaLuz.AMBAS)))
    }

    @Test
    fun `V5 modo`() {
        val casos = listOf(
            -1 to ("7BFF0300FFFFFFFFBF" to "7EFF030003FFFFFFEF"),
            0 to ("7BFF0300FFFFFFFFBF" to "7EFF030003FFFFFFEF"),
            9 to ("7BFF0309FFFFFFFFBF" to "7EFF030903FFFFFFEF"),
            13 to ("7BFF030DFFFFFFFFBF" to "7EFF030D03FFFFFFEF"),
            95 to ("7BFF035FFFFFFFFFBF" to "7EFF035F03FFFFFFEF"),
            123 to ("7BFF037BFFFFFFFFBF" to "7EFF037B03FFFFFFEF"),
            136 to ("7BFF0388FFFFFFFFBF" to "7EFF038803FFFFFFEF"),
            255 to ("7BFF03FFFFFFFFFFBF" to "7EFF03FF03FFFFFFEF"),
            300 to ("7BFF03FFFFFFFFFFBF" to "7EFF03FF03FFFFFFEF"),
        )
        for ((m, esperado) in casos) {
            assertEquals("DMX $m", esperado.first, hex(Quadros.modoDmx(m)))
            assertEquals("BLE $m", esperado.second, hex(Quadros.modoBle(m)))
        }
    }

    @Test
    fun `V6 velocidade`() {
        val casos = listOf(
            -3 to ("7BFF0201FF00FFFFBF" to "7EFF020100FFFFFFEF"),
            0 to ("7BFF0201FF00FFFFBF" to "7EFF020100FFFFFFEF"),
            1 to ("7BFF0201FF00FFFFBF" to "7EFF020100FFFFFFEF"),
            50 to ("7BFF0232FF00FFFFBF" to "7EFF023200FFFFFFEF"),
            100 to ("7BFF0264FF00FFFFBF" to "7EFF026400FFFFFFEF"),
            101 to ("7BFF0264FF00FFFFBF" to "7EFF026400FFFFFFEF"),
        )
        for ((v, esperado) in casos) {
            assertEquals("DMX $v", esperado.first, hex(Quadros.velocidadeDmx(v)))
            assertEquals("BLE $v", esperado.second, hex(Quadros.velocidadeBle(v)))
        }
    }

    @Test
    fun `V7 efeito nativo`() {
        assertEquals(listOf("7EFF030D03FFFFFFEF", "7EFF023200FFFFFFEF"), hex(Quadros.efeitoNativo(13, 50, SaidaDaLuz.BLE)))
        assertEquals(listOf("7BFF030DFFFFFFFFBF", "7BFF0232FF00FFFFBF"), hex(Quadros.efeitoNativo(13, 50, SaidaDaLuz.DMX)))
        assertEquals(
            listOf("7BFF030DFFFFFFFFBF", "7EFF030D03FFFFFFEF", "7BFF0232FF00FFFFBF", "7EFF023200FFFFFFEF"),
            hex(Quadros.efeitoNativo(13, 50, SaidaDaLuz.AMBAS)),
        )
        assertEquals(listOf("7BFF0365FFFFFFFFBF", "7BFF0201FF00FFFFBF"), hex(Quadros.efeitoNativo(101, 1, SaidaDaLuz.DMX)))
        assertEquals(listOf("7BFF037FFFFFFFFFBF", "7BFF0264FF00FFFFBF"), hex(Quadros.efeitoNativo(127, 100, SaidaDaLuz.DMX)))
    }

    @Test
    fun `todos os quadros tem 9 bytes`() {
        val todos = Quadros.cor(CorLed(1, 2, 3), SaidaDaLuz.AMBAS, OrdemDeCor.RGB, OrdemDeCor.RGB) +
            Quadros.brilho(50, SaidaDaLuz.AMBAS) + Quadros.efeitoNativo(9, 9, SaidaDaLuz.AMBAS)
        assertTrue(todos.all { it.size == 9 })
    }

    private fun bytesDe(texto: String?): String = when (val r = Hex.paraBytes(texto)) {
        is ResultadoDoHex.Bytes -> Hex.deBytes(r.bytes)
        is ResultadoDoHex.Erro -> "ERRO: " + r.mensagem
    }

    @Test
    fun `V9 hex digitado`() {
        assertEquals("7B0007FF000000FFBF", bytesDe("7B 00 07 FF 00 00 00 FF BF"))
        assertEquals("7B0007FF000000FFBF", bytesDe("7b0007ff000000ffbf"))
        assertEquals("7B0007", bytesDe("7B\n00\t07"))
        assertEquals("7B0007", bytesDe("7B\r\n00\t07"))
    }

    @Test
    fun `V9 erros do hex`() {
        fun erro(texto: String?, trecho: String) {
            val r = Hex.paraBytes(texto)
            assertTrue("$texto → $r", r is ResultadoDoHex.Erro && r.mensagem.contains(trecho))
        }
        erro("", "vazio")
        erro("   ", "vazio")
        erro(null, "vazio")
        erro("ABC", "ímpar")
        erro("7B:00", "ímpar")
        erro("ZZ", "inválido")
        erro("0x7B", "inválido")
    }

    @Test
    fun `saida lida das preferencias`() {
        assertEquals(SaidaDaLuz.DMX, SaidaDaLuz.PADRAO)
        assertEquals(SaidaDaLuz.AMBAS, SaidaDaLuz.doValor("BOTH"))
        assertEquals(SaidaDaLuz.AMBAS, SaidaDaLuz.doValor("both"))
        assertEquals(SaidaDaLuz.BLE, SaidaDaLuz.doValor(" ble "))
        assertEquals(SaidaDaLuz.DMX, SaidaDaLuz.doValor("AMBAS"))
        assertEquals(SaidaDaLuz.DMX, SaidaDaLuz.doValor(null))
        assertEquals(SaidaDaLuz.DMX, SaidaDaLuz.doValor(""))
        assertEquals(listOf("BLE", "DMX", "BOTH"), SaidaDaLuz.entries.map { it.valorGravado })
        assertEquals(listOf("BLE", "DMX", "BLE + DMX"), SaidaDaLuz.entries.map { it.rotulo })
    }

    @Test
    fun `ordem lida das preferencias`() {
        assertEquals(OrdemDeCor.RBG, OrdemDeCor.PADRAO_DMX)
        assertEquals(OrdemDeCor.RGB, OrdemDeCor.PADRAO_BLE)
        assertEquals(OrdemDeCor.GRB, OrdemDeCor.doValor("grb", OrdemDeCor.PADRAO_DMX))
        assertEquals(OrdemDeCor.RBG, OrdemDeCor.doValor("XYZ", OrdemDeCor.PADRAO_DMX))
        assertEquals(OrdemDeCor.RGB, OrdemDeCor.doValor(null, OrdemDeCor.PADRAO_BLE))
        assertEquals("BGR", OrdemDeCor.BGR.valorGravado)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
