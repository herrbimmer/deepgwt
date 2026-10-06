package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizacaoTest {
    private val vazio = MapaDePersonalizacoes.VAZIO

    @Test
    fun `nome e icone vazios removem a entrada`() {
        val com = vazio.gravar("com.a", "Nome", null, null, null)
        assertNotNull(com.ler("com.a"))
        val sem = com.gravar("com.a", "  ", "  ", "#111111", "#222222")
        assertNull(sem.ler("com.a"))
        assertEquals("{}", sem.paraTexto())
    }

    @Test
    fun `so nome grava so name e perde cor e fundo`() {
        val m = vazio.gravar("com.waze", " Waze ", null, "#FF0000", "#00FF00")
        assertEquals("""{"com.waze":{"name":"Waze"}}""", m.paraTexto())
        assertEquals(Personalizacao("Waze", null, null, null), m.ler("com.waze"))
    }

    @Test
    fun `icone sem cor usa os padroes`() {
        val m = vazio.gravar("com.spotify.music", "Spotify", "music", null, "")
        assertEquals(
            """{"com.spotify.music":{"name":"Spotify","icon":"music","color":"#FFFFFF","bg":"#3D4650"}}""",
            m.paraTexto(),
        )
    }

    @Test
    fun `so icone sem nome grava icone cor e fundo`() {
        val m = vazio.gravar("__gwm_hub", "", "gwm", "#FFFFFF", "#111111")
        assertEquals("""{"__gwm_hub":{"icon":"gwm","color":"#FFFFFF","bg":"#111111"}}""", m.paraTexto())
        assertEquals(Personalizacao(null, "gwm", "#FFFFFF", "#111111"), m.ler("__gwm_hub"))
    }

    @Test
    fun `pacote vazio ou nulo nao grava`() {
        assertSame(vazio, vazio.gravar("", "x", "music", null, null))
        assertSame(vazio, vazio.gravar(null, "x", null, null, null))
        assertNull(vazio.ler(null))
        assertNull(vazio.ler(""))
    }

    @Test
    fun `nao valida slug nem cor na gravacao`() {
        val m = vazio.gravar("com.a", "", "slug-que-nao-existe", "naoecor", "lixo")
        val p = m.ler("com.a")!!
        assertEquals("slug-que-nao-existe", p.icone)
        assertEquals("naoecor", p.cor)
        assertEquals("lixo", p.fundo)
    }

    @Test
    fun `leitura apara e trata vazio como ausente`() {
        val texto = """{"com.a":{"name":"  Oi  ","icon":"  ","color":" #FFFFFF ","bg":""}}"""
        val leitura = MapaDePersonalizacoes.lerTexto(texto)
        assertNull(leitura.textoInvalido)
        assertEquals(Personalizacao("Oi", null, "#FFFFFF", null), leitura.mapa.ler("com.a"))
    }

    @Test
    fun `entrada toda vazia equivale a sem entrada`() {
        val leitura = MapaDePersonalizacoes.lerTexto("""{"com.a":{"name":"","icon":""},"com.b":"x"}""")
        assertNull(leitura.mapa.ler("com.a"))
        assertNull(leitura.mapa.ler("com.b"))
    }

    @Test
    fun `json invalido vira vazio e o texto ruim e devolvido`() {
        for (ruim in listOf("{nao e json", "[1,2]", "42", "\"x\"")) {
            val l = MapaDePersonalizacoes.lerTexto(ruim)
            assertTrue(ruim, l.mapa.vazio)
            assertEquals(ruim, l.textoInvalido)
        }
    }

    @Test
    fun `ausente ou em branco e vazio sem ser invalido`() {
        for (t in listOf(null, "", "   ", "{}")) {
            val l = MapaDePersonalizacoes.lerTexto(t)
            assertTrue(l.mapa.vazio)
            assertNull(l.textoInvalido)
        }
    }

    @Test
    fun `entradas de outros pacotes e campos desconhecidos sao preservados`() {
        val original = """{"com.a":{"name":"A","extra":"fica"},"com.b":{"name":"B"}}"""
        val m = MapaDePersonalizacoes.lerTexto(original).mapa
        assertEquals(original, m.paraTexto())
        val depois = m.gravar("com.b", "B2", null, null, null)
        assertEquals("""{"com.a":{"name":"A","extra":"fica"},"com.b":{"name":"B2"}}""", depois.paraTexto())
    }

    @Test
    fun `remover entrada inexistente devolve o mesmo mapa`() {
        val m = vazio.gravar("com.a", "A", null, null, null)
        assertSame(m, m.remover("com.z"))
        assertSame(m, m.remover(null))
    }

    @Test
    fun `cor com alfa e aceita na leitura e na gravacao`() {
        val m = vazio.gravar("com.a", "", "music", "#80FFFFFF", "#CC000000")
        assertEquals("#80FFFFFF", m.ler("com.a")?.cor)
        assertEquals(0x80FFFFFF.toInt(), CorEmTexto.analisar(m.ler("com.a")?.cor))
    }

    @Test
    fun `regravar uma entrada substitui o objeto inteiro`() {
        val m = MapaDePersonalizacoes.lerTexto("""{"com.a":{"name":"A","extra":"some"}}""").mapa
        val n = m.gravar("com.a", "A", null, null, null)
        assertEquals("""{"com.a":{"name":"A"}}""", n.paraTexto())
    }
}
