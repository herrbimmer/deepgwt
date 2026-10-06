// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Preferências em memória (com valores de tipo errado) e as regras do estado. */
class EstadoDaBarraTest {

    /** Preferências falsas: o que está no mapa vale; tipo errado cai no padrão, como no Android. */
    private class PrefsFalsas(private val m: Map<String, Any?>) : FonteDePreferencias {
        override fun contem(chave: String) = m.containsKey(chave)
        override fun booleano(chave: String, padrao: Boolean) = (m[chave] as? Boolean) ?: padrao
        override fun inteiro(chave: String, padrao: Int) = (m[chave] as? Int) ?: padrao
        override fun texto(chave: String) = m[chave] as? String
    }

    @Before
    fun antes() {
        ThreadPrincipal.agenda = AgendaDeTeste()
    }

    @After
    fun depois() {
        ThreadPrincipal.restaurar()
    }

    @Test
    fun `sem preferencia nenhuma vale a tabela B4`() {
        val e = PreferenciasDaBarra.lerEspelho(PrefsFalsas(emptyMap()), licencaLiberada = true)
        assertFalse(e.esconderSozinha)
        assertEquals(GestoParaCima.GAVETA, e.gestoParaCima)
        assertEquals("", e.pacoteDoGesto)
        assertEquals(60, e.alturaDp)
        assertEquals(ControleDeTemperatura.UMA_ZONA, e.temperatura)
        assertFalse(e.mostrarVolume)
        assertTrue(e.mostrarModo)
        assertEquals("gwm", e.logoDoVolume)
        assertEquals("", e.textoDoLogo)
        assertTrue(e.cartaoPeloCarro)
        assertFalse(e.indicadorLigado)
        assertEquals(14, e.indicadorFonteSp)
        assertEquals(3, e.indicadorCanto)
        assertEquals(12, e.indicadorXDp)
        assertEquals(90, e.indicadorYDp)
    }

    @Test
    fun `valores estranhos nao derrubam e a altura fica limitada`() {
        val p = PrefsFalsas(
            mapOf(
                "bottomBarHeightDp" to 400,
                "bottomBarTempControl" to "Dual",
                "bottomBarShowAc" to false,
                "bottomBarShowAcDualZone" to true,
                "bottomBarSwipeUpAction" to "DASHBOARD",
                "bottomBarShowVolume" to "sim",
                "enableResourceOverlay" to true,
                "resourceOverlayFontSp" to "grande",
                "bottomBarSwipeUpPackage" to "  com.spotify.music ",
            )
        )
        val e = PreferenciasDaBarra.lerEspelho(p, licencaLiberada = false)
        assertEquals(140, e.alturaDp)
        assertEquals(ControleDeTemperatura.DUAS_ZONAS, e.temperatura)
        assertEquals(GestoParaCima.GAVETA, e.gestoParaCima)
        assertFalse(e.mostrarVolume)
        assertEquals(14, e.indicadorFonteSp)
        assertEquals("com.spotify.music", e.pacoteDoGesto)
        // O indicador exige a preferência E a licença.
        assertFalse(e.indicadorLigado)
        assertTrue(PreferenciasDaBarra.lerEspelho(p, licencaLiberada = true).indicadorLigado)
        assertEquals(40, PreferenciasDaBarra.lerEspelho(PrefsFalsas(mapOf("bottomBarHeightDp" to 0)), true).alturaDp)
    }

    @Test
    fun `overscan explicito so vale com a chave presente`() {
        assertNull(PreferenciasDaBarra.overscanExplicito(PrefsFalsas(emptyMap())))
        assertEquals(0, PreferenciasDaBarra.overscanExplicito(PrefsFalsas(mapOf("persistentBottomBarOverscan" to 0))))
        assertEquals(90, PreferenciasDaBarra.overscanExplicito(PrefsFalsas(mapOf("persistentBottomBarOverscan" to 90))))
    }

    @Test
    fun `aplicar o espelho nao mexe na visibilidade nem nos menus`() {
        val s = EstadoDaBarra
        s.visivel = false
        s.menuDeAppsAberto = true
        PreferenciasDaBarra.aplicar(
            PreferenciasDaBarra.lerEspelho(PrefsFalsas(mapOf("bottomBarHeightDp" to 88, "bottomBarAutoHide" to true)), true)
        )
        assertEquals(88, s.alturaDp)
        assertTrue(s.esconderSozinha)
        assertFalse(s.visivel)
        assertTrue(s.menuDeAppsAberto)
        s.visivel = true
        s.menuDeAppsAberto = false
        PreferenciasDaBarra.aplicar(PreferenciasDaBarra.lerEspelho(PrefsFalsas(emptyMap()), true))
    }

    @Test
    fun `fechar tudo, algo pede toque e algo aberto no objeto`() {
        val s = EstadoDaBarra
        s.menuDeClimaAberto = true
        s.cartao = FuncaoDoAjuste.VOLUME
        s.cartaoAbertoPeloCarro = true
        s.escolhaDeModo = EscolhaDeModo(10f, 2, "0")
        assertTrue(s.algoPedeToque())
        assertTrue(s.algoAberto())
        s.menuDeClimaAberto = false
        // Só o cartão do carro e a coluna: nada pede toque, mas há algo aberto.
        assertFalse(s.algoPedeToque())
        assertTrue(s.algoAberto())
        s.painelExpandido = true
        s.fecharTudo()
        assertNull(s.cartao)
        assertFalse(s.cartaoAbertoPeloCarro)
        assertNull(s.escolhaDeModo)
        // "Fechar tudo" não fecha o painel.
        assertTrue(s.painelExpandido)
        s.painelExpandido = false
        assertFalse(s.algoAberto())
    }

    @Test
    fun `apps restaurados aceitam escrita de fora e nao repetem`() {
        val l = EstadoDaBarra.appsRestaurados
        l.adicionar("a")
        l.adicionar("a")
        l.adicionar("b")
        l.adicionar(" ")
        assertEquals(listOf("a", "b"), l.toList())
        l.reter { it != "a" }
        assertEquals(listOf("b"), l.toList())
        l.remover("b")
        assertTrue(l.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
