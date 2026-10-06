package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicadoresDaFaixaTest {
    private fun ler(json: String) = IndicadoresDaFaixa.interpretar(json)

    @Test
    fun `json vazio, em branco, invalido ou que nao e objeto e ignorado`() {
        assertNull(ler(""))
        assertNull(ler("   "))
        assertNull(ler("{nao e json"))
        assertNull(ler("[1,2]"))
        assertNull(ler("\"texto\""))
        assertNull(IndicadoresDaFaixa.interpretar(null))
    }

    @Test
    fun `objeto vazio nao muda nada`() {
        val a = ler("{}")!!
        assertNull(a.superficie)
        assertNull(a.temaClaro)
        assertNull(a.acento)
        assertNull(a.telaDoEstudio)
        assertNull(a.estudioAberto)
        assertNull(a.editandoCartoes)
    }

    @Test
    fun `dockMode aceita launcher e cards e ignora o resto`() {
        assertEquals(SuperficieDoDock.LANCADOR, ler("""{"dockMode":"launcher"}""")!!.superficie)
        assertEquals(SuperficieDoDock.CARTOES, ler("""{"dockMode":"cards"}""")!!.superficie)
        assertNull(ler("""{"dockMode":"expanded"}""")!!.superficie)
        assertNull(ler("""{"dockMode":""}""")!!.superficie)
        assertNull(ler("""{"dockMode":3}""")!!.superficie)
    }

    @Test
    fun `uiTheme light liga o tema claro e qualquer outro valor o escuro`() {
        assertEquals(true, ler("""{"uiTheme":"light"}""")!!.temaClaro)
        assertEquals(false, ler("""{"uiTheme":"dark"}""")!!.temaClaro)
        assertEquals(false, ler("""{"uiTheme":"auto"}""")!!.temaClaro)
        assertEquals(false, ler("""{"uiTheme":""}""")!!.temaClaro)
        assertNull(ler("""{"uiTheme":null}""")!!.temaClaro)
    }

    @Test
    fun `acento aceita RGB e RRGGBB com cerquilha`() {
        assertEquals(0xFF112233.toInt(), ler("""{"accent":"#123"}""")!!.acento)
        assertEquals(0xFFAABBCC.toInt(), ler("""{"accent":"#abc"}""")!!.acento)
        assertEquals(0xFF2AA7B7.toInt(), ler("""{"accent":"#2AA7B7"}""")!!.acento)
        assertEquals(0xFF2AA7B7.toInt(), ler("""{"accent":"  #2aa7b7 "}""")!!.acento)
    }

    @Test
    fun `acento sem cerquilha ou invalido e ignorado`() {
        assertNull(ler("""{"accent":"2AA7B7"}""")!!.acento)
        assertNull(ler("""{"accent":"#12"}""")!!.acento)
        assertNull(ler("""{"accent":"#1234"}""")!!.acento)
        assertNull(ler("""{"accent":"#12345678"}""")!!.acento)
        assertNull(ler("""{"accent":"#GGGGGG"}""")!!.acento)
        assertNull(ler("""{"accent":""}""")!!.acento)
        assertNull(ler("""{"accent":12}""")!!.acento)
    }

    @Test
    fun `studioScreen conhecido passa e desconhecido vira vazio`() {
        assertEquals("desktops", ler("""{"studioScreen":"desktops"}""")!!.telaDoEstudio)
        assertEquals("layout", ler("""{"studioScreen":"layout"}""")!!.telaDoEstudio)
        assertEquals("appearance", ler("""{"studioScreen":"appearance"}""")!!.telaDoEstudio)
        assertEquals("", ler("""{"studioScreen":"outra"}""")!!.telaDoEstudio)
        assertEquals("", ler("""{"studioScreen":""}""")!!.telaDoEstudio)
        assertNull(ler("""{}""")!!.telaDoEstudio)
    }

    @Test
    fun `desktopStudioOpen e railEdit sao booleanos`() {
        val a = ler("""{"desktopStudioOpen":true,"railEdit":false}""")!!
        assertEquals(true, a.estudioAberto)
        assertEquals(false, a.editandoCartoes)
        assertNull(ler("""{"desktopStudioOpen":"sim"}""")!!.estudioAberto)
    }

    @Test
    fun `dockStyle escolhe o estilo da bandeja e ignora o desconhecido`() {
        assertEquals(EstiloDoDock.LION, ler("""{"dockStyle":"lion"}""")!!.estiloDoDock)
        assertEquals(EstiloDoDock.BARRA, ler("""{"dockStyle":" bar "}""")!!.estiloDoDock)
        assertEquals(EstiloDoDock.NENHUM, ler("""{"dockStyle":"none"}""")!!.estiloDoDock)
        assertNull(ler("""{"dockStyle":"neon"}""")!!.estiloDoDock)
        assertNull(ler("{}")!!.estiloDoDock)
    }

    @Test
    fun `estilo do dock - preferencia gravada e geometria`() {
        assertEquals(EstiloDoDock.FLAT, EstiloDoDock.daPreferencia(null))
        assertEquals(EstiloDoDock.FLAT, EstiloDoDock.daPreferencia("lixo"))
        assertEquals(EstiloDoDock.LION, EstiloDoDock.daPreferencia("lion"))
        assertFalse(EstiloDoDock.NENHUM.temPlaca)
        assertTrue(EstiloDoDock.FLAT.temPlaca)
        // so o 3D tem reflexo; so a barra vai ate o fim da pagina
        assertEquals(listOf(EstiloDoDock.LION), EstiloDoDock.entries.filter { it.reflexo })
        assertEquals(listOf(EstiloDoDock.BARRA), EstiloDoDock.entries.filter { it.placaBaseDp == null })
        // a fileira e o vidro cabem dentro da faixa
        for (e in EstiloDoDock.entries.filter { it.temPlaca }) {
            assertTrue(e.placaTopoDp < (e.placaBaseDp ?: MedidasDaFaixa.ALTURA_DA_FAIXA_DP))
            assertTrue((e.placaBaseDp ?: 0) <= MedidasDaFaixa.ALTURA_DA_FAIXA_DP)
        }
    }

    @Test
    fun `campos dos cartoes e das barras inativas nao atrapalham`() {
        val a = ler("""{"bottomCards":[{"id":"x"}],"climate":"22","model":"PHEV34","fps":true,"dockMode":"cards"}""")
        assertNotNull(a)
        assertEquals(SuperficieDoDock.CARTOES, a!!.superficie)
    }

    @Test
    fun `preferencia gravada que nao seja cards vira lancador`() {
        assertEquals(SuperficieDoDock.CARTOES, SuperficieDoDock.daPreferencia("cards"))
        assertEquals(SuperficieDoDock.LANCADOR, SuperficieDoDock.daPreferencia("launcher"))
        assertEquals(SuperficieDoDock.LANCADOR, SuperficieDoDock.daPreferencia("xyz"))
        assertEquals(SuperficieDoDock.LANCADOR, SuperficieDoDock.daPreferencia(null))
    }

    @Test
    fun `preenchimento do centro - modo desconhecido vira car`() {
        assertEquals("wallpaper", IndicadoresDaFaixa.interpretarPreenchimento("""{"mode":"wallpaper","visible":true}""")!!.modo)
        assertEquals("mixed", IndicadoresDaFaixa.interpretarPreenchimento("""{"mode":"mixed"}""")!!.modo)
        assertEquals("car", IndicadoresDaFaixa.interpretarPreenchimento("""{"mode":"qualquer"}""")!!.modo)
        assertEquals("car", IndicadoresDaFaixa.interpretarPreenchimento("""{"mode":"car"}""")!!.modo)
        assertNull(IndicadoresDaFaixa.interpretarPreenchimento("""{}""")!!.modo)
        assertNull(IndicadoresDaFaixa.interpretarPreenchimento("lixo"))
    }

    @Test
    fun `preenchimento do centro - visibilidade`() {
        assertEquals(true, IndicadoresDaFaixa.interpretarPreenchimento("""{"visible":true}""")!!.visivel)
        assertEquals(false, IndicadoresDaFaixa.interpretarPreenchimento("""{"visible":false}""")!!.visivel)
        assertNull(IndicadoresDaFaixa.interpretarPreenchimento("""{"mode":"car"}""")!!.visivel)
        assertTrue(IndicadoresDaFaixa.interpretarPreenchimento("""{"visible":"talvez"}""")!!.visivel!!)
        assertFalse(IndicadoresDaFaixa.interpretarPreenchimento("""{"visible":false}""")!!.visivel!!)
    }
}
