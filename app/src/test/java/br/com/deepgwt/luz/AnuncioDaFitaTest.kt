// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Casos 81 e 86: o que a varredura chama de fita e como a lista aparece. */
class AnuncioDaFitaTest {

    @Test
    fun `hex do anuncio fica nos primeiros 48 bytes`() {
        val anuncio = ByteArray(62) { it.toByte() }
        val hex = AnuncioDaFita.hex(anuncio)!!
        assertEquals(96, hex.length)
        assertTrue(hex.startsWith("000102"))
        assertTrue(hex.endsWith("2F"))
        assertEquals("0A0B", AnuncioDaFita.hex(byteArrayOf(10, 11)))
        assertNull(AnuncioDaFita.hex(null))
    }

    @Test
    fun `FFE0 na lista de servicos ou em qualquer lugar do hex`() {
        assertTrue(AnuncioDaFita.anunciaFfe0(listOf("0000FFE0-0000-1000-8000-00805F9B34FB"), null))
        assertTrue(AnuncioDaFita.anunciaFfe0(emptyList(), "020106" + "0302E0FF"))
        assertTrue(AnuncioDaFita.anunciaFfe0(emptyList(), "AAFFE0BB"))
        // Atravessando a fronteira de bytes também conta (critério frouxo, mantido).
        assertTrue(AnuncioDaFita.anunciaFfe0(emptyList(), "0FFE01"))
        assertFalse(AnuncioDaFita.anunciaFfe0(listOf("0000180f-0000-1000-8000-00805f9b34fb"), "020106"))
    }

    @Test
    fun `parece fita pelo FFE0 ou pelo nome com LED`() {
        val porNome = AnuncioDaFita.montar("ledcar-01-00DD", "AA:BB:CC:DD:EE:01", -60, emptyList(), byteArrayOf(2, 1, 6))
        assertTrue(porNome.pareceFita)
        assertFalse(porNome.anunciaFfe0)
        assertEquals("LEDCAR", AnuncioDaFita.tipo(porNome))

        val porServico = AnuncioDaFita.montar(null, "AA:BB:CC:DD:EE:02", -70, listOf(AnuncioDaFita.UUID_FFE0), null)
        assertTrue(porServico.pareceFita)
        assertEquals("LEDLAMP FFE0", AnuncioDaFita.tipo(porServico))

        val outro = AnuncioDaFita.montar("Fone", "AA:BB:CC:DD:EE:03", -40, emptyList(), byteArrayOf(2, 1, 6))
        assertFalse(outro.pareceFita)
        assertEquals("BLE não identificado", AnuncioDaFita.tipo(outro))

        assertEquals("LEDDMX", AnuncioDaFita.tipo(FitaEncontrada("LEDDMX-03-1234", "x", 0, true, false, null)))
        assertEquals("LED", AnuncioDaFita.tipo(FitaEncontrada("Minha LED", "x", 0, true, false, null)))
    }

    @Test
    fun `lista da tela ordena por parece fita e depois pelo sinal, ate 12`() {
        val achados = (1..15).map { i -> FitaEncontrada("d$i", "E$i", -100 + i, pareceFita = i % 5 == 0, anunciaFfe0 = false, anuncioHex = null) }
        val tela = AnuncioDaFita.paraTela(achados)
        assertEquals(12, tela.size)
        assertEquals(listOf("E15", "E10", "E5"), tela.take(3).map { it.endereco })
        assertEquals("E14", tela[3].endereco)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
