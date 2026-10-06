// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * O roteiro do tour e as novidades (Alpha 7.9). Um passo que aponta para uma seção que não existe
 * mais não abre nada e deixa o balão falando sozinho: o título é conferido contra o menu.
 */
class PassosDoTourTest {

    private val java = File("src/main/java/br/com/deepgwt")
    private fun fonte(caminho: String) = File(java, caminho).readText()

    private val titulosDoMenu: Set<String> by lazy {
        Regex("""ItemDoMenu\("([^"]+)"""").findAll(fonte("ui/navigation/ItensDoMenu.kt"))
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `ids sao unicos`() {
        val ids = PassosDoTour.TODOS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `cada secao e um item do menu que aparece sempre`() {
        assertTrue(titulosDoMenu.size > 5)
        PassosDoTour.TODOS.mapNotNull { it.secao }.forEach { secao ->
            assertTrue("\"$secao\" não está no menu", secao in titulosDoMenu)
            // Só aparecem com o uso avançado ligado.
            assertFalse(secao in setOf("ADAS"))
        }
    }

    @Test
    fun `alvos sao os marcados nas telas`() {
        val conhecidos = setOf(PassosDoTour.ALVO_MENU, PassosDoTour.ALVO_SECAO, PassosDoTour.ALVO_TAMANHO)
        PassosDoTour.TODOS.mapNotNull { it.alvo }.forEach { assertTrue(it in conhecidos) }
        val navegacao = fonte("ui/navigation/NavegacaoPrincipal.kt")
        assertTrue(navegacao.contains("alvoDoTour(PassosDoTour.ALVO_MENU)"))
        assertTrue(navegacao.contains("alvoDoTour(PassosDoTour.ALVO_SECAO)"))
        assertTrue(fonte("ui/screens/InterfaceDoDeepGwtScreen.kt").contains("alvoDoTour(PassosDoTour.ALVO_TAMANHO)"))
    }

    @Test
    fun `alvo de uma secao so vale com a secao aberta`() {
        PassosDoTour.TODOS.filter { it.alvo == PassosDoTour.ALVO_SECAO || it.alvo == PassosDoTour.ALVO_TAMANHO }
            .forEach { assertTrue("${it.id} sem seção", it.secao != null) }
        PassosDoTour.TODOS.filter { it.alvo == PassosDoTour.ALVO_MENU }
            .forEach { assertEquals(null, it.secao) }
    }

    @Test
    fun `comeca na tela inicial e termina dizendo como rever`() {
        assertEquals(null, PassosDoTour.TODOS.first().secao)
        assertEquals("fim", PassosDoTour.TODOS.last().id)
        assertTrue(PassosDoTour.TODOS.last().texto.contains("Ver o tour"))
        assertTrue(fonte("ui/screens/InterfaceDoDeepGwtScreen.kt").contains("\"Ver o tour\""))
    }

    @Test
    fun `porIds mantem a ordem do roteiro`() {
        assertEquals(listOf("telas", "interface"), PassosDoTour.porIds(listOf("interface", "telas", "nao-existe")).map { it.id })
    }

    @Test
    fun `novidades apontam para passos que existem`() {
        val ids = PassosDoTour.TODOS.map { it.id }.toSet()
        Novidades.TODAS.flatMap { it.passosDoTour }.forEach { assertTrue("passo $it", it in ids) }
    }

    @Test
    fun `novidades em ordem e sem versao repetida`() {
        val codigos = Novidades.TODAS.map { it.versionCode }
        assertEquals(codigos.size, codigos.toSet().size)
        assertEquals(listOf(70), Novidades.entre(69, 70).map { it.versionCode })
        assertTrue(Novidades.entre(70, 70).isEmpty())
        assertEquals(codigos.sorted(), Novidades.entre(0, Int.MAX_VALUE).map { it.versionCode })
    }

    @Test
    fun `camada da abertura fica por cima do app`() {
        val main = fonte("PrincipalActivity.kt")
        assertTrue(main.contains("CamadaDaAbertura("))
        assertTrue(main.contains("if (savedInstanceState == null) AberturaDoDeepGwt.decidirAoAbrir(this)"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
