package br.com.opengw.veiculo.comandos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** motor-comandos.md §D.1 e §F (vetores exatos). */
class LeiturasDoCarroTest {

    @Test
    fun `D1 - vetores do porta-malas (posicao 5)`() {
        val casos = listOf(
            "{1,0,0,0,0,0}" to false,
            "{0,0,0,0,0,1}" to true,
            "{1,1,1,1,0,1}" to true,
            "{0,0,0,0,1,0}" to false,
            "[0,0,0,0,0,1]" to true,
            " { 0; 0; 0; 0; 0; 0 } " to false,
            "1,0,0,0,0,0" to false,
            "0,0,0,0,0,1" to true,
            "0 0 0 0 0 1" to true,
            " 0,0,0,0,0,2 " to true,
            null to null,
            "" to null,
            "{}" to null,
            "{0,0,0,0}" to null,
            "{0,0,0,0,0,x}" to null,
        )
        for ((entrada, esperado) in casos) {
            assertEquals("entrada '$entrada'", esperado, LeiturasDoCarro.posicaoLigada(entrada, 5))
        }
    }

    @Test
    fun `D1 - parenteses e decimais truncados`() {
        assertEquals(true, LeiturasDoCarro.posicaoLigada("(0,0,0,0,0,1)", 5))
        assertEquals(true, LeiturasDoCarro.posicaoLigada("{0,0,0,0,0,1.0}", 5))
        assertEquals(false, LeiturasDoCarro.posicaoLigada("{0,0,0,0,0,0.5}", 5))
        assertEquals(true, LeiturasDoCarro.posicaoLigada("{1,0}", 0))
    }

    @Test
    fun `F - tela principal e ignicao (tabela exata)`() {
        val tabela = listOf(
            Triple(null, false, false),
            Triple("-1", false, false),
            Triple("10", false, false),
            Triple("11", true, true),
            Triple("12", true, true),
            Triple("13", true, true),
            Triple("14", false, false),
            Triple("15", false, false),
            Triple("", true, false),
            Triple("7", true, false),
        )
        for ((valor, tela, ignicao) in tabela) {
            assertEquals("tela para '$valor'", tela, LeiturasDoCarro.telaPrincipalLigada(valor))
            assertEquals("ignição para '$valor'", ignicao, LeiturasDoCarro.ignicaoLigada(valor))
        }
    }

    @Test
    fun `F1 - leitura que lanca conta como ligada`() {
        assertTrue(LeiturasDoCarro.telaPrincipalLigadaPor { throw IllegalStateException("x") })
        assertFalse(LeiturasDoCarro.telaPrincipalLigadaPor { "15" })
        assertFalse(LeiturasDoCarro.telaPrincipalLigadaPor { null })
    }

    @Test
    fun `F3 - odometro inteiro`() {
        assertEquals(49435, LeiturasDoCarro.quilometragemTotalPor { "49435" })
        assertEquals("decimal vira 0 (D-C7)", 0, LeiturasDoCarro.quilometragemTotalPor { "49435.0" })
        assertEquals(0, LeiturasDoCarro.quilometragemTotalPor { null })
        assertEquals(0, LeiturasDoCarro.quilometragemTotalPor { "" })
        assertEquals(0, LeiturasDoCarro.quilometragemTotalPor { "abc" })
        assertEquals(0, LeiturasDoCarro.quilometragemTotalPor { throw IllegalStateException("x") })
    }
}
