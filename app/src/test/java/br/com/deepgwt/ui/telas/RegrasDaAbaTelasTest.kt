// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui.telas

import androidx.compose.ui.graphics.Color
import br.com.deepgwt.painel.AjusteDoTema
import br.com.deepgwt.painel.TemaInstalado
import br.com.deepgwt.telas.ConfigDoApp
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** `telas-e-servico-telas.md` §9 (T-V1 a T-V6) e §10 (JVM). */
class RegrasDaAbaTelasTest {

    private val sp = TimeZone.getTimeZone("America/Sao_Paulo")

    @Test
    fun `proxima revisao (T-V1)`() {
        assertEquals(ProximaRevisao(0, 0), Revisoes.proxima(emptyList(), sp))
        assertEquals(ProximaRevisao(12000, 1773147600000), Revisoes.proxima(listOf(Revisao(0, 1741611600000)), sp))
        assertEquals(
            ProximaRevisao(24000, 1740747600000),
            Revisoes.proxima(listOf(Revisao(12000, 1709211600000), Revisao(0, 1741611600000)), sp),
        )
        assertEquals(
            ProximaRevisao(48000, 1773147600000),
            Revisoes.proxima(listOf(Revisao(24000, 1790859600000), Revisao(36000, 1741611600000)), sp),
        )
        // Empate: vale a primeira.
        assertEquals(
            ProximaRevisao(12000, 1773147600000),
            Revisoes.proxima(listOf(Revisao(0, 1741611600000), Revisao(0, 1709211600000)), sp),
        )
    }

    @Test
    fun `json das revisoes (B1)`() {
        assertEquals("[{\"km\":12000,\"date\":1741611600000}]", Revisoes.paraJson(listOf(Revisao(12000, 1741611600000))))
        assertEquals("[]", Revisoes.paraJson(emptyList()))
        assertEquals(emptyList<Revisao>(), Revisoes.deJson(""))
        assertEquals(emptyList<Revisao>(), Revisoes.deJson("x"))
        assertEquals(emptyList<Revisao>(), Revisoes.deJson(null))
        assertEquals(listOf(Revisao(5, 0)), Revisoes.deJson("[{\"km\":5}]"))
    }

    @Test
    fun `revisoes nas preferencias`() {
        val prefs = PreferenciasFalsas()
        val lista = listOf(Revisao(0, 1741611600000), Revisao(12000, 1773147600000))
        Revisoes.gravar(prefs, lista)
        assertEquals(lista, Revisoes.ler(prefs))
        Revisoes.publicar(prefs, ProximaRevisao(0, 0))
        assertEquals(0, prefs.valores["instrumentRevisionKm"])
        assertEquals(0L, prefs.valores["instrumentRevisionNextDate"])
        Revisoes.atualizarProxima(prefs)
        assertEquals(24000, prefs.valores["instrumentRevisionKm"])
        assertTrue((prefs.valores["instrumentRevisionNextDate"] as Long) > 1773147600000)
    }

    @Test
    fun `encaixe do controle (T-V2)`() {
        val faixa = 0..720
        val casos = listOf(124.4f to 124, 125.0f to 135, 134.6f to 135, 145.0f to 135, 145.6f to 146, 800.0f to 720)
        for ((valor, esperado) in casos) assertEquals("$valor", esperado, RegrasDoEditor.ajustar(valor, faixa, 1, 135))
        assertEquals(124, RegrasDoEditor.ajustar(124.4f, faixa))
        // Passo > 1: gruda com distância ≤ passo.
        assertEquals(135, RegrasDoEditor.ajustar(141f, faixa, 20, 135))
        assertEquals(160, RegrasDoEditor.ajustar(150f, faixa, 20, 135))
        assertEquals(180, RegrasDoEditor.ajustar(180f, faixa, 20, 135))
    }

    @Test
    fun `limites ao trocar de display (T-V3)`() {
        assertArrayEquals(intArrayOf(0, 0, 854, 480), RegrasDoEditor.limitar(0, 0, 1920, 720, 4))
        assertArrayEquals(intArrayOf(854, 480, 100, 100), RegrasDoEditor.limitar(900, 500, 50, 50, 4))
        assertArrayEquals(intArrayOf(10, 20, 300, 200), RegrasDoEditor.limitar(10, 20, 300, 200, 3))
        assertEquals(1920 to 720, RegrasDoEditor.resolucao(1))
        assertEquals(854 to 480, RegrasDoEditor.resolucao(4))
    }

    @Test
    fun `editor preserva display 0 e forcar foco (decisao 10)`() {
        val configuracoes = ConfigDoApp("com.android.settings", "x", 0, 0, 0, 1920, 720, forcarFoco = true)
        assertEquals(0, RegrasDoEditor.displayInicial(configuracoes))
        assertEquals(3, RegrasDoEditor.displayInicial(null))
        assertEquals(3, RegrasDoEditor.displayInicial(configuracoes.copy(display = 2)))
        assertEquals(4, RegrasDoEditor.displayInicial(configuracoes.copy(display = 4)))
        val montada = RegrasDoEditor.montar(
            "com.android.settings", "x", 0, 1, 2, 300, 200, "", false, null, "#FFFFFF", configuracoes,
        )
        assertTrue(montada.forcarFoco)
        assertEquals("", montada.nome)
        assertFalse(RegrasDoEditor.montar("p", "", 3, 0, 0, 100, 100, "n", true, "nav", "#FF0000", null).forcarFoco)
        assertTrue(RegrasDoEditor.mostraDimensoes(false, 1))
        assertFalse(RegrasDoEditor.mostraDimensoes(false, 3))
        assertTrue(RegrasDoEditor.mostraDimensoes(true, 3))
    }

    @Test
    fun `grade em ordem natural (decisao 9)`() {
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), RegrasDoEditor.linhasDaGrade(listOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun `texto do multi (T-V4)`() {
        val opcoes = listOf("A", "B", "C")
        assertEquals("A, C", CoresDosAjustes.textoDoMulti(opcoes, setOf("C", "A")))
        assertEquals("", CoresDosAjustes.textoDoMulti(opcoes, emptySet()))
        assertEquals("B", CoresDosAjustes.textoDoMulti(opcoes, setOf("b")))
        assertEquals(setOf("A", "C"), CoresDosAjustes.escolhidasDoMulti(" A ,C "))
        assertEquals(emptySet<String>(), CoresDosAjustes.escolhidasDoMulti(""))
    }

    private fun ajuste(n: Int, grupo: String, tipo: String = "text", variavel: String = "v$n", padrao: String = "") =
        AjusteDoTema("id$n", "Ajuste $n", tipo, padrao, variavel, emptyList(), grupo)

    @Test
    fun `abas do dialogo (T-V5)`() {
        val ajustes = listOf(ajuste(1, ""), ajuste(2, "Cores"), ajuste(3, "Geral"), ajuste(4, "Avançado"), ajuste(5, "Cores"))
        val abas = CoresDosAjustes.abas(ajustes)
        assertEquals(listOf("Geral", "Cores", "Avançado"), abas.map { it.first })
        assertEquals(listOf("id1", "id3"), abas[0].second.map { it.id })
        assertEquals(listOf("id2", "id5"), abas[1].second.map { it.id })
        assertEquals(listOf("id4"), abas[2].second.map { it.id })
        val vazias = CoresDosAjustes.abas(listOf(ajuste(1, ""), ajuste(2, "")))
        assertEquals(listOf("Geral"), vazias.map { it.first })
        assertEquals(2, vazias[0].second.size)
    }

    @Test
    fun `cores auxiliares (T-V6)`() {
        val r = "#RESERVA"
        assertEquals("#00A0FF", CoresDosAjustes.normalizarHex("00a0ff", r))
        assertEquals("#00A0FF", CoresDosAjustes.normalizarHex(" #00a0ff ", r))
        assertEquals(r, CoresDosAjustes.normalizarHex("#abc", r))
        assertEquals(r, CoresDosAjustes.normalizarHex("#GG0000", r))
        assertEquals(r, CoresDosAjustes.normalizarHex(null, r))
        assertEquals(Color.Black, CoresDosAjustes.corLegivelSobre(0xFFFFFF))
        assertEquals(Color.Black, CoresDosAjustes.corLegivelSobre(0xFFFF00))
        assertEquals(Color.White, CoresDosAjustes.corLegivelSobre(0x808080))
        assertEquals(Color.White, CoresDosAjustes.corLegivelSobre(0x00A0FF))
        assertEquals(Color.White, CoresDosAjustes.corLegivelSobre(0x000000))
        assertEquals("#00A0FF", CoresDosAjustes.hexDe(0xFF00A0FF.toInt()))
        assertEquals("theme_config_Sport_accent", CoresDosAjustes.chaveDoAjuste("Sport", "accent"))
        assertEquals(CoresDosAjustes.PALETA_PADRAO, CoresDosAjustes.paletaDoAjuste(listOf("x", "#12")))
        assertEquals(listOf("#00A0FF"), CoresDosAjustes.paletaDoAjuste(listOf("x", "00a0ff")))
    }

    @Test
    fun `valor gravado lido como texto (T-D12)`() {
        val prefs = PreferenciasFalsas(mapOf("a" to true, "b" to "x", "c" to 3))
        assertEquals("true", CoresDosAjustes.lerComoTexto(prefs, "a"))
        assertEquals("x", CoresDosAjustes.lerComoTexto(prefs, "b"))
        assertEquals("3", CoresDosAjustes.lerComoTexto(prefs, "c"))
        assertEquals(null, CoresDosAjustes.lerComoTexto(prefs, "d"))
    }

    @Test
    fun `volta ao tema embutido (G1)`() {
        val embutido = TemaInstalado(
            pasta = "Default", nome = "Default", descricao = "", versao = "", miniatura = "", arquivoPrincipal = "index.html",
            local = true, areaDosApps = null, descentralizado = false, versaoMinimaDaPonte = null, versaoDoContrato = "legacy",
            ajustes = listOf(
                ajuste(1, "", tipo = "COLOR", variavel = "accent", padrao = "#00A0FF"),
                ajuste(2, "", tipo = "combo", variavel = "driveModeColors", padrao = "auto"),
                ajuste(3, "", tipo = "boolean", variavel = "showClock", padrao = "true"),
            ),
            modosDeExibicao = emptyList(), fundo = "", fundoAbsoluto = "", mascaras = null,
        )
        val prefs = PreferenciasFalsas(
            mapOf("activeCustomTheme" to "Sport", "theme_config_Default_showClock" to "false", "theme_config_Default_accent" to "#FF0000"),
        )
        TemaEmbutido.restaurar(prefs, embutido, 1234L)
        assertEquals(1, prefs.escritas)
        assertEquals("", prefs.valores["activeCustomTheme"])
        assertEquals("Default", prefs.valores["virtualClusterTheme"])
        assertEquals("#00A0FF", prefs.valores["theme_config_Default_accent"])
        assertEquals("auto", prefs.valores["theme_config_Default_driveModeColors"])
        assertEquals("false", prefs.valores["theme_config_Default_showClock"])
        assertEquals(1234L, prefs.valores["themeReloadNonce"])
    }

    @Test
    fun `situacao do tema (E2)`() {
        val base = TemaInstalado(
            pasta = "x", nome = "Sport", descricao = "", versao = " 1.2 ", miniatura = "", arquivoPrincipal = "index.html",
            local = true, areaDosApps = null, descentralizado = false, versaoMinimaDaPonte = null, versaoDoContrato = "1.0",
            ajustes = emptyList(), modosDeExibicao = emptyList(), fundo = "", fundoAbsoluto = "", mascaras = null,
        )
        assertEquals("Instalado v1.2", situacaoDoTema(base))
        assertEquals("Instalado", situacaoDoTema(base.copy(versao = "")))
        assertEquals("Embutido", situacaoDoTema(base.copy(nome = "Default")))
    }

    @Test
    fun `filtro do seletor de app (D15)`() {
        val apps = listOf(
            AppEscolhido("com.spotify.music", "", "Spotify", null),
            AppEscolhido("com.waze", "", "Waze", null),
        )
        assertEquals(listOf("com.waze"), filtrarApps(apps, "WAZ").map { it.pacote })
        assertEquals(listOf("com.spotify.music"), filtrarApps(apps, "music").map { it.pacote })
        assertEquals(2, filtrarApps(apps, " ").size)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
