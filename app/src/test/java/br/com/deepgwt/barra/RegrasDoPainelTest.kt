// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import br.com.deepgwt.ui.CartaoDoPainelExpandido.CLIMA
import br.com.deepgwt.ui.CartaoDoPainelExpandido.DINAMICA
import br.com.deepgwt.ui.CartaoDoPainelExpandido.MIDIA
import br.com.deepgwt.ui.FormatosDoPainelExpandido
import br.com.deepgwt.ui.OrdemDoPainelExpandido
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores da especificação do painel. */
class RegrasDoPainelTest {

    private val r = RegrasDoPainel

    @Test
    fun `ordem dos cartoes caso 12 (a implementacao do kit)`() {
        assertEquals(listOf(CLIMA, MIDIA, DINAMICA), OrdemDoPainelExpandido.normalizar("hvac,unknown,media,hvac"))
        assertEquals("dynamics,media,hvac", OrdemDoPainelExpandido.paraGravar(listOf(DINAMICA, MIDIA, MIDIA)))
        assertEquals(listOf(DINAMICA, MIDIA, CLIMA), OrdemDoPainelExpandido.mover(listOf(MIDIA, DINAMICA, CLIMA), MIDIA, 1))
        assertEquals(listOf(MIDIA, CLIMA, DINAMICA), OrdemDoPainelExpandido.mover(listOf(MIDIA, DINAMICA, CLIMA), CLIMA, -1))
        assertEquals(listOf(MIDIA, DINAMICA, CLIMA), OrdemDoPainelExpandido.mover(listOf(MIDIA, DINAMICA, CLIMA), MIDIA, -1))
        assertEquals(listOf(MIDIA, DINAMICA, CLIMA), OrdemDoPainelExpandido.mover(listOf(MIDIA, DINAMICA, CLIMA), CLIMA, 1))
    }

    @Test
    fun `litros caso 32 (formatos do kit)`() {
        val f = FormatosDoPainelExpandido
        assertEquals("27.5 L - 420 km", f.litrosEAutonomia("50", "420"))
        assertEquals("27.5 L", f.litrosEAutonomia("50", "--"))
        assertEquals("420 km", f.litrosEAutonomia("--", "420"))
        assertEquals("--", f.litrosEAutonomia("--", "--"))
        assertEquals("13.2 L", f.litros("24"))
        assertEquals("55.0 L", f.litros("100"))
        listOf("101", "-1", "x").forEach { assertEquals("--", f.litros(it)) }
    }

    @Test
    fun `dispensar caso 10`() {
        assertTrue(r.verticalDomina(120f, 0f))
        assertTrue(r.verticalDomina(120f, 80f))
        assertFalse(r.verticalDomina(120f, 120f))
        assertFalse(r.verticalDomina(-120f, 0f))
        assertFalse(r.dispensar(150f, 0f, -900f))
        assertTrue(r.dispensar(30f, 0f, 400f))
        assertFalse(r.dispensar(80f, 0f, 0f))
        assertTrue(r.dispensar(81f, 0f, 0f))
        assertFalse(r.dispensar(120f, 120f, 2_000f))
        assertFalse(r.dispensar(10f, 400f, 3_000f))
        assertEquals(-449.1f, r.projecao(-900f), 0.01f)
        assertEquals(199.6f, r.projecao(400f), 0.01f)
    }

    @Test
    fun `energia regeneracao e direcao caso 33`() {
        assertEquals("HEV Prior. 20%", r.energia("0", "2", "20"))
        assertEquals("HEV Prior. 80%", r.energia("0", "2", "95"))
        assertEquals("HEV Prior. 50%", r.energia("0", "2", "x"))
        assertEquals("HEV Intel.", r.energia("0", "1", "50"))
        assertEquals("EV Prior.", r.energia("1", "1", "50"))
        assertEquals("EV", r.energia("3", "1", "50"))
        assertEquals("Baixo", r.regeneracao("2"))
        assertEquals("Alto", r.regeneracao("1"))
        assertEquals("Normal", r.regeneracao("9"))
        assertEquals("Conforto", r.direcao("2"))
        assertEquals("Sport", r.direcao("1"))
        assertEquals("Normal", r.direcao(null))
    }

    @Test
    fun `bateria e autonomia caso 15`() {
        assertEquals(38.0, r.bateria(listOf("38", "-1.0", "0"))!!, 0.0)
        assertEquals(0.0, r.bateria(listOf("0", "-1.0", "0"))!!, 0.0)
        assertNull(r.bateria(listOf("-1.0", "150", "x")))
        assertEquals(55.0, r.bateria(listOf("", "55", "0"))!!, 0.0)
        assertEquals(220.0, r.autonomia(listOf("220", "52"))!!, 0.0)
        assertEquals(52.0, r.autonomia(listOf("0", "52"))!!, 0.0)
        assertEquals(0.0, r.autonomia(listOf("0", "0"))!!, 0.0)
        assertNull(r.autonomia(listOf("x", "-1")))
        assertEquals("38", r.textoDoNumero(38.0))
        assertEquals("--", r.textoDoNumero(null))
    }

    @Test
    fun `temperatura percentual fracao e tempo caso 34`() {
        assertEquals("23.0°C", r.temperatura("23"))
        assertEquals("--", r.temperatura("-1"))
        assertEquals("--", r.temperatura("255"))
        assertEquals("--", r.temperatura("85"))
        assertEquals("-39.5°C", r.temperatura("-39.5"))
        assertEquals("--", r.temperatura("--"))
        assertEquals("38%", r.percentual("38"))
        assertEquals("100%", r.percentual("150"))
        assertEquals("--", r.percentual("x"))
        assertEquals(0.38f, r.fracao("38"), 0.0001f)
        assertEquals(0f, r.fracao("x"), 0.0001f)
        assertEquals(1f, r.fracao("250"), 0.0001f)
        assertEquals("0:00", r.tempo(0))
        assertEquals("1:05", r.tempo(65_000))
        assertEquals("60:00", r.tempo(3_600_000))
        assertEquals("--:--", r.tempo(0, desconhecidoQuandoZero = true))
    }

    @Test
    fun `volume do painel caso 20`() {
        assertEquals(11, r.proximoVolume(null, 10, 10, 1, 0..30))
        assertEquals(9, r.proximoVolume(null, 10, 10, -1, 0..30))
        assertEquals(30, r.proximoVolume(null, 30, 30, 1, 0..30))
        assertEquals(0, r.proximoVolume(null, 0, 0, -1, 0..30))
        // Pendente vence o carro; carro fora da faixa é limitado.
        assertEquals(13, r.proximoVolume(12, 10, 12, 1, 0..30))
        assertEquals(30, r.proximoVolume(null, 39, 0, 1, 0..30))
        assertTrue(r.adotarDoCarro(null, 0, 10, 5_000))
        assertTrue(r.adotarDoCarro(12, 4_000, 12, 5_000))
        assertFalse(r.adotarDoCarro(12, 4_000, 10, 5_000))
        assertTrue(r.adotarDoCarro(12, 3_000, 10, 5_000))
    }

    @Test
    fun `ciclos caso 24`() {
        assertEquals("0", r.proximoNoCiclo("2", r.CICLO_DA_CONDUCAO))
        assertEquals("1", r.proximoNoCiclo("0", r.CICLO_DA_CONDUCAO))
        assertEquals("2", r.proximoNoCiclo("1", r.CICLO_DA_CONDUCAO))
        assertEquals("2", r.proximoNoCiclo("4", r.CICLO_DA_CONDUCAO)) // P-D5: de Areia vai a Eco
        assertEquals("1", r.proximoNoCiclo("0", r.CICLO_DA_ENERGIA))
        assertEquals("3", r.proximoNoCiclo("1", r.CICLO_DA_ENERGIA))
        assertEquals("0", r.proximoNoCiclo("3", r.CICLO_DA_ENERGIA))
    }

    @Test
    fun `textos do cartao midia e cabecalho`() {
        assertEquals("Apple CarPlay ativo no cluster", r.rotuloDaProjecao("com.ts.carplay.app"))
        assertEquals("Android Auto ativo no cluster", r.rotuloDaProjecao("com.ts.androidauto.app"))
        assertEquals("Dashboard do display 0", r.rotuloDaProjecao(""))
        assertEquals("Projeção ativa", r.rotuloDaProjecao("com.zlink"))
        assertEquals("Artista • Álbum", r.subtitulo("Artista", "Álbum"))
        assertEquals("Mesmo", r.subtitulo("Mesmo", "Mesmo"))
        assertNull(r.subtitulo(" ", null))
        assertEquals("PLAY", r.selo(true, true))
        assertEquals("MÍDIA", r.selo(false, true))
        assertEquals("ÁUDIO", r.selo(false, false))
        assertEquals("Projeção ativa no cluster", r.subtituloDeReserva("com.ts.carplay.app", null))
        assertEquals("Mídia do sistema", r.subtituloDeReserva("", "com.spotify"))
        assertEquals("Sistema de áudio", r.subtituloDeReserva("", null))
        assertEquals("com.ts.carplay.app", r.appDeReferencia("com.ts.carplay.app", "com.x", "com.y"))
        assertEquals("com.x", r.appDeReferencia("", "com.x", "com.y"))
        assertEquals("com.y", r.appDeReferencia("", "", "com.y"))
        assertTrue(r.pronto("TRUE"))
        assertTrue(r.pronto(" 1 "))
        assertFalse(r.pronto("--"))
        assertEquals("p|t|1x2", r.chaveDoFundo("p", "t", null, null, 1, 2).replace("|1|2", "|1x2"))
    }

    @Test
    fun `ventilacao do painel caso 26`() {
        assertEquals(RegrasDoPainel.PassoDaVentilacao(1, true), r.passoDaVentilacao("0", 1, ligado = false))
        assertEquals(RegrasDoPainel.PassoDaVentilacao(0, false), r.passoDaVentilacao("1", -1, ligado = true))
        assertEquals(RegrasDoPainel.PassoDaVentilacao(7, null), r.passoDaVentilacao("7", 1, ligado = true))
        assertEquals(RegrasDoPainel.PassoDaVentilacao(0, null), r.passoDaVentilacao("x", -1, ligado = false))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
