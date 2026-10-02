package br.com.opengw.painel

import br.com.opengw.ponte.PreferenciasEmMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** L (máscaras nativas) de `projetores-painel.md` e B.1/C de `projetores-hud.md`. */
class MascarasEDisplay1Test {

    @Test
    fun formaDoPainelDeFabrica() {
        // Dentro (bordas inclusivas da especificação).
        for ((x, y) in listOf(0 to 0, 600 to 719, 710 to 0, 1210 to 67, 0 to 653, 730 to 719, 1190 to 653, 1919 to 719)) {
            assertTrue("($x,$y)", FormaDoPainelDeFabrica.contem(x, y))
        }
        // Fora.
        for ((x, y) in listOf(601 to 0, 709 to 0, 1211 to 0, 710 to 68, 731 to 719, 1189 to 719, 960 to 360, 1919 to 652, 1300 to 100)) {
            assertFalse("($x,$y)", FormaDoPainelDeFabrica.contem(x, y))
        }
    }

    private val prefs = PreferenciasEmMemoria()

    private class TemasFalsos : PapelDeParede.Temas {
        var fundo: String? = null
        val existem = mutableSetOf<String>()
        override fun fundoDoTemaAtivo(dica: String?): String? = fundo
        override fun arquivoNoTema(pasta: String, nome: String): String? = "/t/$pasta/$nome".takeIf { it in existem }
        override fun arquivoExiste(caminho: String): Boolean = caminho in existem
    }

    private fun escolha() = PapelDeParede.Escolha.de(PreferenciasDoPainel(prefs))

    @Test
    fun papelDeParede() {
        val temas = TemasFalsos()
        // Padrão: ligado, THEME.
        assertEquals(PapelDeParede.Escolha(true, "THEME", ""), escolha())
        assertNull(PapelDeParede.imagem(escolha(), "coffe3", temas))
        assertFalse(PapelDeParede.haFundo(escolha(), temas))
        temas.fundo = "/t/coffe3/car-bg.jpg"
        assertEquals(ImagemDoPainel.Arquivo("/t/coffe3/car-bg.jpg"), PapelDeParede.imagem(escolha(), "coffe3", temas))
        assertNull("página original: sem fundo de tema", PapelDeParede.imagem(escolha(), "", temas))
        assertTrue(PapelDeParede.haFundo(escolha(), temas))
        temas.fundo = null
        temas.existem.add("/t/coffe3/car-bg.png")
        assertEquals(ImagemDoPainel.Arquivo("/t/coffe3/car-bg.png"), PapelDeParede.imagem(escolha(), "coffe3", temas))

        prefs.mapa["customBackgroundTypeD1"] = "preset"
        prefs.mapa["customBackgroundValueD1"] = "wallhaven-0151j3.jpg"
        assertEquals(ImagemDoPainel.Asset("backgrounds/opengw-01.jpg"), PapelDeParede.imagem(escolha(), "", temas))
        assertTrue(PapelDeParede.haFundo(escolha(), temas))

        prefs.mapa["customBackgroundTypeD1"] = "FILE"
        prefs.mapa["customBackgroundValueD1"] = "/sdcard/fundo.jpg"
        assertNull(PapelDeParede.imagem(escolha(), "", temas))
        temas.existem.add("/sdcard/fundo.jpg")
        assertEquals(ImagemDoPainel.Arquivo("/sdcard/fundo.jpg"), PapelDeParede.imagem(escolha(), "", temas))

        for (tipo in listOf("IMAGE_URL", "COLOR")) {
            prefs.mapa["customBackgroundTypeD1"] = tipo
            assertNull(PapelDeParede.imagem(escolha(), "coffe3", temas))
            assertTrue(PapelDeParede.haFundo(escolha(), temas))
        }
        prefs.mapa["customBackgroundTypeD1"] = "WEB_URL"
        prefs.mapa["customBackgroundValueD1"] = ""
        assertFalse(PapelDeParede.haFundo(escolha(), temas))
        prefs.mapa["customBackgroundValueD1"] = "https://x"
        assertTrue(PapelDeParede.haFundo(escolha(), temas))
        prefs.mapa["customBackgroundTypeD1"] = "OUTRO"
        assertFalse(PapelDeParede.haFundo(escolha(), temas))
        prefs.mapa["enableCustomBackgroundD1"] = false
        prefs.mapa["customBackgroundTypeD1"] = "PRESET"
        assertNull(PapelDeParede.imagem(escolha(), "", temas))
        assertFalse(PapelDeParede.haFundo(escolha(), temas))
    }

    private val mascarasDoApk = LeitorDoTema.ler(
        """<theme><name>T</name><nativeMasks>
          <speedMask enabled="true" x="0" y="0" width="640" height="720" />
          <infoMask enabled="true" x="1280" y="0" width="640" height="720" />
          <topCenterMask enabled="true" x="660" y="0" width="600" height="90" />
        </nativeMasks></theme>""",
    )!!.mascaras

    @Test
    fun planoPorElemento() {
        val plano = PlanoDasMascaras.planejar("coffe3", null, mascarasDoApk, emptyMap(), 1, null) { null }
        assertEquals(listOf("speed", "info", "topcenter"), plano.elementos.map { it.nome })
        assertEquals(listOf(true, true, false), plano.elementos.map { it.transparenteSemImagem })
        assertEquals("tema=coffe3 resultado=elementos:speed+info+topcenter cartao=1 vao=nenhum", plano.resumo())

        val cartaoZero = PlanoDasMascaras.planejar("coffe3", null, mascarasDoApk, emptyMap(), 0, null) { null }
        assertEquals(listOf("speed", "topcenter"), cartaoZero.elementos.map { it.nome })

        val desligada = PlanoDasMascaras.planejar("coffe3", null, mascarasDoApk, mapOf("speed" to false, "fuel" to true), 1, null) { null }
        assertEquals("sobreposição não liga máscara desligada", listOf("info", "topcenter"), desligada.elementos.map { it.nome })

        val vao = Retangulo(0, 0, 10, 10)
        val global = PlanoDasMascaras.planejar("coffe3", ImagemDoPainel.Arquivo("/f.jpg"), mascarasDoApk, emptyMap(), 1, vao) { null }
        assertEquals("tema=coffe3 resultado=global cartao=1 vao=0,0,10,10", global.resumo())
        assertFalse(global.nenhuma)

        val nada = PlanoDasMascaras.planejar("", null, null, emptyMap(), 1, null) { null }
        assertTrue(nada.nenhuma)
        assertEquals("tema=original resultado=nenhuma cartao=1 vao=nenhum", nada.resumo())
    }

    @Test
    fun corComVinheta() {
        assertEquals(RegrasDoDisplay1.CorComVinheta(16, 24, 32, 45), RegrasDoDisplay1.cor("#101820|V45"))
        assertEquals(114, RegrasDoDisplay1.cor("#101820|V45")!!.alfaDaVinheta)
        assertEquals(RegrasDoDisplay1.CorComVinheta(255, 0, 0, 0), RegrasDoDisplay1.cor("FF0000"))
        assertEquals(255, RegrasDoDisplay1.cor("#00FF00|V150")!!.alfaDaVinheta)
        assertEquals(100, RegrasDoDisplay1.cor("#00FF00|V150")!!.vinheta)
        assertNull(RegrasDoDisplay1.cor("#12345|V10"))
        assertNull(RegrasDoDisplay1.cor("#GGGGGG"))
        assertNull(RegrasDoDisplay1.cor(null))
        assertEquals(0, RegrasDoDisplay1.cor("#101820|Vxx")!!.vinheta)
    }

    @Test
    fun display1Aparece() {
        assertTrue(RegrasDoDisplay1.aparece(papelLigado = true, telaPrincipalLigada = true, appNoDisplay1 = false))
        assertFalse(RegrasDoDisplay1.aparece(papelLigado = false, telaPrincipalLigada = true, appNoDisplay1 = false))
        assertFalse(RegrasDoDisplay1.aparece(papelLigado = true, telaPrincipalLigada = false, appNoDisplay1 = false))
        assertFalse(RegrasDoDisplay1.aparece(papelLigado = true, telaPrincipalLigada = true, appNoDisplay1 = true))
    }
}
