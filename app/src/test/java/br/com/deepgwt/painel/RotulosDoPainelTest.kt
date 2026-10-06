// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Vetores V1–V5 de `projetores-painel.md`. */
class RotulosDoPainelTest {

    @Test
    fun v1_marcha() {
        assertEquals("D", RotulosDoPainel.marcha("2"))
        assertEquals("P", RotulosDoPainel.marcha("3"))
        assertEquals("R", RotulosDoPainel.marcha("4"))
        for (v in listOf("0", "1", "-1", "5", "3.0", "abc", null)) assertEquals("v=$v", "N", RotulosDoPainel.marcha(v))
    }

    @Test
    fun v1_esp() {
        assertEquals("ON", RotulosDoPainel.esp("1"))
        assertEquals("OFF", RotulosDoPainel.esp("0"))
        assertEquals("--", RotulosDoPainel.esp("7"))
        assertEquals("--", RotulosDoPainel.esp(null))
        assertEquals("--", RotulosDoPainel.esp("abc"))
    }

    @Test
    fun v1_forca() {
        assertEquals("HEV Prioridade 20%", RotulosDoPainel.forca("0", "2", "20"))
        assertEquals("HEV Prioridade 80%", RotulosDoPainel.forca("0", "2", "95"))
        assertEquals("HEV Prioridade 20%", RotulosDoPainel.forca("0", "2", "10"))
        assertEquals("HEV Prioridade 50%", RotulosDoPainel.forca("0", "2", "abc"))
        assertEquals("HEV Prioridade 50%", RotulosDoPainel.forca("0", "2", null))
        assertEquals("HEV Inteligente", RotulosDoPainel.forca("0", "1", "20"))
        assertEquals("HEV Inteligente", RotulosDoPainel.forca("0", null, "20"))
        assertEquals("HEV Inteligente", RotulosDoPainel.forca("0", "7", "20"))
        assertEquals("HEV Prioridade 20%", RotulosDoPainel.forca(" 0 ", "2", "20"))
        assertEquals("EVP", RotulosDoPainel.forca("1", "2", "20"))
        assertEquals("EV", RotulosDoPainel.forca("3", null, null))
        assertEquals("--", RotulosDoPainel.forca("2", null, null))
        assertEquals("--", RotulosDoPainel.forca("-1", null, null))
        assertEquals("--", RotulosDoPainel.forca(null, null, null))
        assertEquals("--", RotulosDoPainel.forca("abc", null, null))
    }

    @Test
    fun v1_conducaoDirecaoRegeneracao() {
        val conducao = mapOf("0" to "Normal", "1" to "Sport", "2" to "Eco", "3" to "Neve", "4" to "Areia", "5" to "Lama", "11" to "AWD")
        for ((v, r) in conducao) assertEquals(r, RotulosDoPainel.conducao(v))
        assertEquals("--", RotulosDoPainel.conducao("6"))
        assertEquals("--", RotulosDoPainel.conducao(null))
        assertEquals("Normal", RotulosDoPainel.direcao("0"))
        assertEquals("Esportiva", RotulosDoPainel.direcao("1"))
        assertEquals("Conforto", RotulosDoPainel.direcao("2"))
        assertEquals("--", RotulosDoPainel.direcao("x"))
        assertEquals("Normal", RotulosDoPainel.regeneracao("0"))
        assertEquals("Alto", RotulosDoPainel.regeneracao("1"))
        assertEquals("Baixo", RotulosDoPainel.regeneracao("2"))
        assertEquals("--", RotulosDoPainel.regeneracao(null))
    }

    @Test
    fun v2_velocidadeDoPainel() {
        // v, sem ajuste, +2%, −3%
        val tabela = listOf(
            listOf("0", "0", "0", "0"), listOf("0.0", "0", "0", "0"), listOf("abc", "0", "0", "0"),
            listOf("1", "1", "1", "1"), listOf("10", "10", "10", "10"), listOf("50", "53", "54", "51"),
            listOf("72", "77", "78", "74"), listOf("72.0", "77", "78", "74"), listOf("100", "106", "109", "103"),
            listOf("120.5", "128", "131", "125"), listOf("180", "192", "196", "186"), listOf("-1.0", "-1", "-1", "-1"),
        )
        for ((v, sem, mais, menos) in tabela) {
            assertEquals("v=$v", sem, RotulosDoPainel.velocidadeDoPainel(v, false, 2f))
            assertEquals("v=$v +2%", mais, RotulosDoPainel.velocidadeDoPainel(v, true, 2f))
            assertEquals("v=$v -3%", menos, RotulosDoPainel.velocidadeDoPainel(v, true, -3f))
        }
        assertEquals("0", RotulosDoPainel.velocidadeDoPainel(null, true, 2f))
    }

    @Test
    fun v3_temperatura() {
        for (v in listOf(null, "--", "-1", "255", "abc")) assertNull("v=$v", RotulosDoPainel.temperatura(v))
        assertEquals("20.5", RotulosDoPainel.temperatura("20.5"))
        assertEquals("-3.0", RotulosDoPainel.temperatura("-3"))
        assertEquals("23.0", RotulosDoPainel.temperatura("23"))
        assertEquals("87.0", RotulosDoPainel.temperatura("87.0"))
        assertEquals("°F", RotulosDoPainel.unidadeDaTemperatura("1"))
        assertEquals("°C", RotulosDoPainel.unidadeDaTemperatura("0"))
        assertEquals("°C", RotulosDoPainel.unidadeDaTemperatura(null))
    }

    @Test
    fun v4_consumo() {
        fun confere(entrada: String?, modo: String, lenta: String, andando: String) {
            val c = RotulosDoPainel.consumo(entrada)
            assertEquals("modo $entrada", modo, c.modo)
            assertEquals("lenta $entrada", lenta, c.marchaLentaTexto)
            assertEquals("andando $entrada", andando, c.andandoTexto)
        }
        confere("{4.0,0.0}", "Running", "0.0", "0.0")
        confere("{4.0,0.85}", "Idle", "0.8", "0.0")
        confere("{4.0,1.23}", "Idle", "1.2", "0.0")
        confere("{1.0,6.5}", "Running", "0.0", "15.3")
        confere("6.5", "Running", "0.0", "15.3")
        confere("{1,7.2}", "Running", "0.0", "13.8")
        confere("7.2", "Running", "0.0", "13.8")
        confere("5.8", "Running", "0.0", "17.2")
        for (v in listOf("-1.0", "{4.0,-1.0}", "{2,5}", "{1}", "abc", null)) confere(v, "Running", "0.0", "0.0")
    }

    @Test
    fun v5_potencia() {
        assertEquals(0.3672f, RotulosDoPainel.potenciaEmKw("306.0", "1.2"), 0.001f)
        assertEquals(-5.6056f, RotulosDoPainel.potenciaEmKw("308.0", "-18.2"), 0.001f)
        assertEquals(0f, RotulosDoPainel.potenciaEmKw(null, "1.2"), 0f)
        assertEquals(0f, RotulosDoPainel.potenciaEmKw("306.0", null), 0f)
        assertEquals("0.0", RotulosDoPainel.regeneracaoDoFator(5f).toString())
        assertEquals("3.5", RotulosDoPainel.regeneracaoDoFator(-3.5f).toString())
        assertEquals(0f, RotulosDoPainel.fatorDePotencia("abc"), 0f)
    }

    @Test
    fun preferenciasNormalizadas() {
        assertEquals("Mapa", RotulosDoPainel.modoDeExibicao("Mapa"))
        assertEquals("Normal", RotulosDoPainel.modoDeExibicao("mapa"))
        assertEquals("Normal", RotulosDoPainel.modoDeExibicao(null))
        assertEquals("Ocean Blue", RotulosDoPainel.corDoPainel("Ocean Blue"))
        assertEquals("Red Sport", RotulosDoPainel.corDoPainel("Roxo"))
        assertEquals("percent", RotulosDoPainel.unidadeDoCombustivel("percent"))
        assertEquals("liters", RotulosDoPainel.unidadeDoCombustivel("x"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
