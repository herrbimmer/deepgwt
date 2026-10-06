// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import br.com.deepgwt.nucleo.Preferencias
import br.com.deepgwt.ajustes.CatalogoDeAjustes
import br.com.deepgwt.ajustes.Efeito
import br.com.deepgwt.ajustes.SecaoDeAjustes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tamanho da interface e das letras (Alpha 7.9). O que se trava aqui:
 *  - no padrão nada é sobreposto (quem atualiza não vê diferença);
 *  - a conta da densidade e da fonte, e os limites;
 *  - o instalador muda os mesmos valores, na mesma faixa;
 *  - as medidas de janela que quebrariam com a densidade trocada foram trocadas.
 */
class EscalaDaInterfaceTest {

    private val java = File("src/main/java/br/com/deepgwt")
    private fun fonte(caminho: String) = File(java, caminho).readText()

    @Test
    fun `no padrao nada e sobreposto`() {
        val s = EscalaDaInterface.calcular(160, 1f, 100, 100)
        assertTrue(s.vazia)
        assertNull(s.densityDpi)
        assertNull(s.fontScale)
    }

    @Test
    fun `interface grande escala a densidade do carro`() {
        // Carro: 160 dpi. 125% → 200 dpi; 90% → 144 dpi.
        assertEquals(200, EscalaDaInterface.calcular(160, 1f, 125, 100).densityDpi)
        assertEquals(144, EscalaDaInterface.calcular(160, 1f, 90, 100).densityDpi)
        assertNull(EscalaDaInterface.calcular(160, 1f, 125, 100).fontScale)
    }

    @Test
    fun `letras multiplicam a escala de fonte do sistema`() {
        assertEquals(1.3f, EscalaDaInterface.calcular(160, 1f, 100, 130).fontScale!!, 1e-4f)
        // Quem já aumentou a fonte no Android soma as duas.
        assertEquals(1.265f, EscalaDaInterface.calcular(160, 1.1f, 100, 115).fontScale!!, 1e-4f)
        assertNull(EscalaDaInterface.calcular(160, 1f, 100, 130).densityDpi)
    }

    @Test
    fun `valores fora da faixa sao limitados`() {
        val s = EscalaDaInterface.calcular(160, 1f, 300, 10)
        assertEquals(208, s.densityDpi) // 130%
        assertEquals(0.85f, s.fontScale!!, 1e-4f)
    }

    @Test
    fun `densidade desconhecida nao e sobreposta`() {
        assertNull(EscalaDaInterface.calcular(0, 1f, 125, 100).densityDpi)
    }

    @Test
    fun `opcoes do carro ficam na grade do instalador`() {
        (EscalaDaInterface.TAMANHOS_DA_INTERFACE + EscalaDaInterface.TAMANHOS_DAS_LETRAS).forEach {
            assertEquals("${it.nome} fora da grade de 5", 0, (it.pct - EscalaDaInterface.MINIMO) % 5)
        }
        EscalaDaInterface.TAMANHOS_DA_INTERFACE.forEach { assertEquals(it.pct, EscalaDaInterface.interfaceValida(it.pct)) }
        EscalaDaInterface.TAMANHOS_DAS_LETRAS.forEach { assertEquals(it.pct, EscalaDaInterface.letrasValidas(it.pct)) }
        assertTrue(EscalaDaInterface.TAMANHOS_DA_INTERFACE.any { it.pct == EscalaDaInterface.PADRAO })
        assertTrue(EscalaDaInterface.TAMANHOS_DAS_LETRAS.any { it.pct == EscalaDaInterface.PADRAO })
    }

    @Test
    fun `nome de valor vindo do computador fora das opcoes`() {
        assertEquals("Grande", EscalaDaInterface.nomeDe(EscalaDaInterface.TAMANHOS_DA_INTERFACE, 115))
        assertEquals("95%", EscalaDaInterface.nomeDe(EscalaDaInterface.TAMANHOS_DA_INTERFACE, 95))
    }

    @Test
    fun `instalador muda os mesmos valores na mesma faixa`() {
        val i = CatalogoDeAjustes.ajuste(Preferencias.INTERFACE_ESCALA.chave)!!
        val l = CatalogoDeAjustes.ajuste(Preferencias.INTERFACE_ESCALA_TEXTO.chave)!!
        listOf(i to EscalaDaInterface.MAXIMO_DA_INTERFACE, l to EscalaDaInterface.MAXIMO_DAS_LETRAS).forEach { (a, max) ->
            assertEquals(SecaoDeAjustes.INTERFACE, a.secao)
            assertEquals(EscalaDaInterface.PADRAO, a.padrao)
            assertEquals(EscalaDaInterface.MINIMO.toDouble(), a.min!!, 0.0)
            assertEquals(max.toDouble(), a.max!!, 0.0)
            // O PrincipalActivity escuta as chaves: nenhum efeito extra.
            assertEquals(Efeito.NENHUM, a.efeito)
        }
    }

    @Test
    fun `activity sobrepoe antes de desenhar e se recria quando muda`() {
        val main = fonte("PrincipalActivity.kt")
        assertTrue(main.contains("override fun attachBaseContext"))
        assertTrue(main.contains("EscalaDaInterface.sobreposicao(newBase)?.let { applyOverrideConfiguration(it) }"))
        assertTrue(main.contains("recreate()"))
        assertTrue(main.contains("unregisterOnSharedPreferenceChangeListener(ouvinteDaEscala)"))
    }

    @Test
    fun `medidas de janela nao multiplicam screenWidthDp pela densidade em uso`() {
        // Com a densidade escalada, screenWidthDp (da densidade real) vezes a densidade em uso
        // passa do tamanho da tela: o recuo do painel lateral sumia e o seletor de cor vazava.
        assertFalse(fonte("ui/navigation/NavegacaoPrincipal.kt").contains("configuration.screenWidthDp.dp.toPx()"))
        // A aba Telas e o seletor de cor livre (que mora fora dela desde a 3.9b) medem a janela.
        val telas = File("src/main/java/br/com/deepgwt/ui/telas")
        assertFalse(File(telas, "TelasScreen.kt").readText().contains("screenHeightDp - 24"))
        val seletor = File(telas, "SeletorDeCorLivre.kt").readText()
        assertFalse(seletor.contains("screenHeightDp - 24"))
        assertTrue(seletor.contains("alturaDaJanelaDp()"))
        assertTrue(fonte("ui/navigation/NavegacaoPrincipal.kt").contains("larguraDaJanelaPx()"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
