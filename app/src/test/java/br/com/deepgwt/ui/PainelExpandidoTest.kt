// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import br.com.deepgwt.ui.CartaoDoPainelExpandido.CLIMA
import br.com.deepgwt.ui.CartaoDoPainelExpandido.DINAMICA
import br.com.deepgwt.ui.CartaoDoPainelExpandido.MIDIA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ordem e formatos do painel expandido da barra inferior: vetores V3 e V4. */
class PainelExpandidoTest {

    private fun texto(ordem: List<CartaoDoPainelExpandido>) = ordem.joinToString(",") { it.chaveGravada }

    private fun cartoes(abreviado: String): List<CartaoDoPainelExpandido> = abreviado.split(',').map {
        when (it) {
            "m" -> MIDIA
            "d" -> DINAMICA
            "h" -> CLIMA
            else -> error(it)
        }
    }

    @Test
    fun `chaves gravadas`() {
        assertEquals(listOf("media", "dynamics", "hvac"), CartaoDoPainelExpandido.entries.map { it.chaveGravada })
        assertEquals(listOf(MIDIA, DINAMICA, CLIMA), OrdemDoPainelExpandido.PADRAO)
        assertEquals(CLIMA, CartaoDoPainelExpandido.daChave(" HVAC "))
        assertEquals(MIDIA, CartaoDoPainelExpandido.daChave("Media"))
        assertNull(CartaoDoPainelExpandido.daChave("Média"))
        assertNull(CartaoDoPainelExpandido.daChave(""))
    }

    @Test
    fun `V3 normalizar`() {
        val padrao = "media,dynamics,hvac"
        listOf(null, "", "   ", ",,,", "x,y,z", "media;hvac", "Média").forEach {
            assertEquals("normalizar($it)", padrao, texto(OrdemDoPainelExpandido.normalizar(it)))
        }
        val casos = mapOf(
            "media,dynamics,hvac" to "media,dynamics,hvac",
            "hvac,unknown,media,hvac" to "hvac,media,dynamics",
            "HVAC , Media" to "hvac,media,dynamics",
            "dynamics" to "dynamics,media,hvac",
            "hvac,hvac,hvac" to "hvac,media,dynamics",
            " dynamics ,HVAC,media,extra" to "dynamics,hvac,media",
        )
        casos.forEach { (entrada, saida) ->
            assertEquals("normalizar($entrada)", saida, texto(OrdemDoPainelExpandido.normalizar(entrada)))
        }
    }

    @Test
    fun `V3 paraGravar`() {
        assertEquals("media,dynamics,hvac", OrdemDoPainelExpandido.paraGravar(listOf(MIDIA, DINAMICA, CLIMA)))
        assertEquals("dynamics,media,hvac", OrdemDoPainelExpandido.paraGravar(listOf(DINAMICA, MIDIA, MIDIA)))
        assertEquals("media,dynamics,hvac", OrdemDoPainelExpandido.paraGravar(emptyList()))
        assertEquals("hvac,media,dynamics", OrdemDoPainelExpandido.paraGravar(listOf(CLIMA)))
        assertEquals("hvac,dynamics,media", OrdemDoPainelExpandido.paraGravar(listOf(CLIMA, CLIMA, DINAMICA, MIDIA)))
    }

    @Test
    fun `V3 mover a partir da ordem padrao`() {
        val direcoes = listOf(-2, -1, 0, 1, 2, 5)
        val tabela = mapOf(
            MIDIA to listOf("m,d,h", "m,d,h", "m,d,h", "d,m,h", "d,h,m", "d,h,m"),
            DINAMICA to listOf("d,m,h", "d,m,h", "m,d,h", "m,h,d", "m,h,d", "m,h,d"),
            CLIMA to listOf("h,m,d", "m,h,d", "m,d,h", "m,d,h", "m,d,h", "m,d,h"),
        )
        tabela.forEach { (cartao, esperados) ->
            direcoes.zip(esperados).forEach { (direcao, esperado) ->
                assertEquals(
                    "mover($cartao, $direcao)",
                    cartoes(esperado),
                    OrdemDoPainelExpandido.mover(OrdemDoPainelExpandido.PADRAO, cartao, direcao),
                )
            }
        }
        assertEquals(cartoes("h,d,m"), OrdemDoPainelExpandido.mover(listOf(CLIMA, CLIMA), MIDIA, 1))
        assertEquals(cartoes("m,h,d"), OrdemDoPainelExpandido.mover(emptyList(), CLIMA, -1))
        // Direções enormes não dão a volta.
        assertEquals(cartoes("d,h,m"), OrdemDoPainelExpandido.mover(OrdemDoPainelExpandido.PADRAO, MIDIA, Int.MAX_VALUE))
        assertEquals(cartoes("h,m,d"), OrdemDoPainelExpandido.mover(OrdemDoPainelExpandido.PADRAO, CLIMA, Int.MIN_VALUE))
    }

    @Test
    fun `V4 litros`() {
        val casos = mapOf(
            "0" to "0.0 L",
            "1" to "0.6 L",
            "0.1" to "0.1 L",
            "12.34" to "6.8 L",
            "12.5" to "6.9 L",
            "33.3" to "18.3 L",
            "99.9" to "54.9 L",
            "25" to "13.8 L",
            "47" to "25.9 L",
            "50" to "27.5 L",
            " 50" to "27.5 L",
            "50 " to "27.5 L",
            "75" to "41.3 L",
            "87.65" to "48.2 L",
            "99" to "54.5 L",
            "100" to "55.0 L",
            "100.0" to "55.0 L",
            "100.00" to "55.0 L",
            "-0" to "-0.0 L",
        )
        casos.forEach { (entrada, saida) ->
            assertEquals("litros($entrada)", saida, FormatosDoPainelExpandido.litros(entrada))
        }
        listOf("-1", "100.1", "101", "abc", "", "--", "NaN", "Infinity", "-Infinity").forEach {
            assertEquals("litros($it)", "--", FormatosDoPainelExpandido.litros(it))
        }
        assertEquals(55f, FormatosDoPainelExpandido.CAPACIDADE_DO_TANQUE_LITROS)
    }

    @Test
    fun `V4 distancia`() {
        val casos = mapOf(
            "420" to "420 km",
            "419.5" to "420 km",
            "420.5" to "421 km",
            "421.5" to "422 km",
            "12.49" to "12 km",
            "35.49999" to "35 km",
            "999.5" to "1000 km",
            "1e3" to "1000 km",
            "0" to "0 km",
            "0.4" to "0 km",
            "-0.5" to "0 km",
            "0.5" to "1 km",
            "-1.5" to "-1 km",
            "-2.5" to "-2 km",
            "-3" to "-3 km",
        )
        casos.forEach { (entrada, saida) ->
            assertEquals("distancia($entrada)", saida, FormatosDoPainelExpandido.distancia(entrada))
        }
        // NaN não lança mais exceção; infinito não vira 2147483647 (decisão provisória 3).
        listOf("abc", "", "--", "NaN", "Infinity", "-Infinity").forEach {
            assertEquals("distancia($it)", "--", FormatosDoPainelExpandido.distancia(it))
        }
    }

    @Test
    fun `V4 litros e autonomia`() {
        val f = FormatosDoPainelExpandido
        assertEquals("27.5 L - 420 km", f.litrosEAutonomia("50", "420"))
        assertEquals("27.5 L", f.litrosEAutonomia("50", "--"))
        assertEquals("420 km", f.litrosEAutonomia("--", "420"))
        assertEquals("--", f.litrosEAutonomia("--", "--"))
        assertEquals("55.0 L - 0 km", f.litrosEAutonomia("100", "0"))
        assertEquals("0.0 L - 0 km", f.litrosEAutonomia("0", "0"))
        assertEquals("--", f.litrosEAutonomia("abc", ""))
        assertEquals("300 km", f.litrosEAutonomia("120", "300"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
