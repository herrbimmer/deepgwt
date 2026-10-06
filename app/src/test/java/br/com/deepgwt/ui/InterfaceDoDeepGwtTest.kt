// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import br.com.deepgwt.nucleo.Preferencias
import androidx.compose.ui.graphics.Color
import br.com.deepgwt.ajustes.CatalogoDeAjustes
import br.com.deepgwt.ajustes.Efeito
import br.com.deepgwt.ajustes.SecaoDeAjustes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Interface do DeepGWT" (Alpha 7.2). O que se trava aqui:
 *  - quem nunca escolheu nada continua na grade, com as cores de sempre — inclusive a barra
 *    inferior e o painel de esportes, que usam os mesmos tokens e nunca recebem outra paleta;
 *  - o valor gravado e o do instalador são os mesmos ids;
 *  - o menu tem o item próprio e as teclas passam primeiro pela lista.
 */
class InterfaceDoDeepGwtTest {

    private val java = File("src/main/java/br/com/deepgwt")
    private fun fonte(caminho: String) = File(java, caminho).readText()
    private val barra = File("src/main/java/br/com/deepgwt/barra")
    private fun fonteDaBarra(arquivo: String) = File(barra, arquivo).readText()

    @Test
    fun `sem valor ou valor desconhecido fica na grade`() {
        assertEquals(EstiloDaInterface.GRADE, EstiloDaInterface.deId(null))
        assertEquals(EstiloDaInterface.GRADE, EstiloDaInterface.deId(""))
        assertEquals(EstiloDaInterface.GRADE, EstiloDaInterface.deId("estilo-de-uma-versao-nova"))
        assertEquals(EstiloDaInterface.BAVARO, EstiloDaInterface.deId("bavaro"))
        assertEquals(EstiloDaInterface.GRADE, EstiloDaInterface.deId("grade"))
        assertEquals(EstiloDaInterface.ESTUGARDA, EstiloDaInterface.deId("estugarda"))
        assertEquals(EstiloDaInterface.CUPERTINO, EstiloDaInterface.deId("cupertino"))
    }

    @Test
    fun `ids gravados sao estaveis`() {
        // Mudar um id faz quem já escolheu voltar para a grade ao atualizar.
        assertEquals(listOf("grade", "bavaro", "estugarda", "cupertino"), EstiloDaInterface.entries.map { it.id })
        assertEquals("interfaceStyle", Preferencias.INTERFACE_STYLE.chave)
    }

    @Test
    fun `paleta da grade e a de antes da 7_2`() {
        val p = PALETA_GRADE
        assertEquals(Color(0xFF0A0A0C), p.ground)
        assertEquals(Color(0xFF12141A), p.container)
        assertEquals(Color(0xFF4A9EFF), p.accent)
        assertEquals(Color(0xFF0A0A0C), p.onAccent)
        assertEquals(Color(0x0F4A9EFF), p.expandTint)
        assertEquals(Color(0x0DFFFFFF), p.hairline)
        assertEquals(Color(0xFFF5F5F5), p.textPrimary)
        assertEquals(Color(0xFF8A93A3), p.textSecondary)
        assertEquals(Color(0xFF4B5563), p.textMuted)
        assertEquals(Color(0xFF262A33), p.trackOff)
        assertEquals(Color(0xFF6B7280), p.thumbOff)
        assertEquals(Color(0xFFFBBF24), p.attention)
        assertEquals(Color(0xFFEF4444), p.danger)
        assertEquals(Color(0x244A9EFF), p.indexActiveTint)
        assertEquals(Color(0x33FFFFFF), p.sheenHighlight)
        assertEquals(listOf(Color(0xFF2C6FE0), Color(0xFF4A9EFF)), p.accentGradient)
        assertEquals(listOf(Color(0xFF1B1F26), Color(0xFF0D0F13)), p.cardGradient)
        assertEquals(Color(0xFF0A0A0A), p.appBackground)
        assertEquals(Color(0xFF13151A), p.appCardBackground)
        assertEquals(Color(0xFF1D2430), p.appBorder)
    }

    @Test
    fun `bavaro tem acento vermelho e texto claro sobre ele`() {
        val p = PALETA_BAVARO
        assertNotEquals(PALETA_GRADE.accent, p.accent)
        assertTrue("acento vermelho", p.accent.red > 0.8f && p.accent.green < 0.3f && p.accent.blue < 0.3f)
        assertEquals(Color.White, p.onAccent)
        assertEquals(PALETA_BAVARO, paletaDe(EstiloDaInterface.BAVARO))
        assertEquals(PALETA_GRADE, paletaDe(EstiloDaInterface.GRADE))
    }

    @Test
    fun `estugarda e azul profundo com acento azul gelo`() {
        val p = PALETA_ESTUGARDA
        assertEquals(PALETA_ESTUGARDA, paletaDe(EstiloDaInterface.ESTUGARDA))
        assertTrue("fundo azul escuro", p.ground.blue > p.ground.red && p.ground.blue < 0.2f)
        assertTrue("acento azul claro", p.accent.blue > 0.9f && p.accent.red < 0.5f)
        assertNotEquals(PALETA_GRADE.accent, p.accent)
        // Texto sobre o acento claro é escuro, como na grade.
        assertTrue(p.onAccent.red < 0.1f && p.onAccent.green < 0.1f)
    }

    @Test
    fun `cupertino usa o azul de sistema do iOS e um container quase opaco`() {
        val p = PALETA_CUPERTINO
        assertEquals(PALETA_CUPERTINO, paletaDe(EstiloDaInterface.CUPERTINO))
        assertEquals(Color(0xFF0A84FF), p.accent)
        assertEquals(Color.White, p.onAccent)
        // Diálogos e menus usam o container de fundo: sem desfoque na central, ele não pode ser
        // transparente a ponto de deixar ler o que está atrás.
        assertTrue("container quase opaco", p.container.alpha > 0.9f)
    }

    @Test
    fun `tela de inicio do cupertino recebe as setas e abre com ok`() {
        val nav = fonte("ui/navigation/NavegacaoPrincipal.kt")
        assertTrue(nav.contains("EstiloDaInterface.CUPERTINO -> MenuCupertino("))
        assertTrue(nav.contains("BarraDeVoltaCupertino("))
        val menu = fonte("ui/navigation/MenuCupertino.kt")
        listOf("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_CENTER").forEach {
            assertTrue("cupertino trata $it", menu.contains(it))
        }
        // Registra e solta o tratador de teclas, como os outros estilos.
        assertTrue(menu.contains("TeclasDaInterface.tratador = tratador"))
        assertTrue(menu.contains("if (TeclasDaInterface.tratador === tratador) TeclasDaInterface.tratador = null"))
        // Vidro sem desfoque de fundo: a central roda Android 9.
        assertTrue(!menu.contains("RenderEffect.create") && !menu.contains("import androidx.compose.ui.draw.blur"))
        assertTrue(fonte("ui/screens/InterfaceDoDeepGwtScreen.kt").contains("EstiloDaInterface.CUPERTINO -> miniaturaDoCupertino()"))
    }

    @Test
    fun `carrossel recebe as setas dos lados e abre com ok`() {
        val nav = fonte("ui/navigation/NavegacaoPrincipal.kt")
        assertTrue(nav.contains("EstiloDaInterface.ESTUGARDA -> MenuEmCarrosselEstugarda("))
        assertTrue(nav.contains("BarraDeVoltaEstugarda("))
        val carrossel = fonte("ui/navigation/MenuEmCarrossel.kt")
        listOf("KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN").forEach {
            assertTrue("carrossel trata $it", carrossel.contains(it))
        }
        // Mesmo registro (e mesma limpeza) de teclas da lista do Bávaro.
        assertTrue(carrossel.contains("TeclasDoMenu("))
        assertTrue(fonte("ui/screens/InterfaceDoDeepGwtScreen.kt").contains("EstiloDaInterface.ESTUGARDA -> miniaturaDoEstugarda()"))
    }

    @Test
    fun `instalador troca o estilo com os mesmos ids`() {
        val a = CatalogoDeAjustes.ajuste(Preferencias.INTERFACE_STYLE.chave)
        assertNotNull(a)
        a!!
        assertEquals(SecaoDeAjustes.INTERFACE, a.secao)
        assertEquals("grade", a.padrao)
        assertEquals(EstiloDaInterface.entries.map { it.id }, a.opcoes.map { it.id })
        // O app escuta a própria chave; um Efeito aqui aplicaria duas vezes.
        assertEquals(Efeito.NENHUM, a.efeito)
    }

    @Test
    fun `so o app fornece a paleta`() {
        assertTrue(fonte("PrincipalActivity.kt").contains("LocalPaletaDaInterface provides paletaDe(InterfaceDoDeepGwt.estilo)"))
        assertTrue(fonte("AberturaActivity.kt").contains("LocalPaletaDaInterface provides"))
        // Barra inferior e painel de esportes ficam no padrão (as cores de antes).
        assertFalse(fonteDaBarra("ConteudoDasJanelas.kt").contains("LocalPaletaDaInterface"))
        assertFalse(fonteDaBarra("BarraVisivel.kt").contains("LocalPaletaDaInterface"))
        assertFalse(fonte("SportsDisplayActivity.kt").contains("LocalPaletaDaInterface"))
        assertFalse(fonteDaBarra("ServicoDaBarra.kt").contains("LocalPaletaDaInterface"))
    }

    @Test
    fun `menu tem item proprio e a lista recebe as setas`() {
        assertTrue(fonte("ui/navigation/ItensDoMenu.kt").contains("ItemDoMenu(\"Interface do DeepGWT\""))
        val nav = fonte("ui/navigation/NavegacaoPrincipal.kt")
        assertTrue(nav.contains("\"Interface do DeepGWT\" -> InterfaceDoDeepGwtTab()"))
        assertTrue(nav.contains("MenuEmListaBavaro("))
        assertTrue(fonte("PrincipalActivity.kt").contains("TeclasDaInterface.tratador?.invoke(event) == true"))
        val lista = fonte("ui/navigation/MenuEmLista.kt")
        listOf("KEYCODE_DPAD_UP", "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_CENTER", "KEYCODE_ENTER").forEach {
            assertTrue("lista trata $it", lista.contains(it))
        }
        // Ao sair da tela a lista solta as teclas, senão a grade continuaria sem setas.
        assertTrue(lista.contains("onDispose { if (TeclasDaInterface.tratador === tratador) TeclasDaInterface.tratador = null }"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
