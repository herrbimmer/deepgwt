package br.com.opengw.painel

import br.com.opengw.ponte.PreferenciasEmMemoria
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/** V1–V4 de `projetores-temas.md`, mais pasta segura, semear, sanear e apagar. */
class TemasInstaladosTest {

    @get:Rule
    val pasta = TemporaryFolder()

    // ── V1 ──

    @Test
    fun v1_decisaoDeSemear() {
        val s = VersoesDeTema.Decisao.SEMEAR
        val m = VersoesDeTema.Decisao.MANTER
        assertEquals(s, VersoesDeTema.decidir("bbbb2222", "1.5.0", "1.5.0", "aaaa1111"))
        assertEquals(m, VersoesDeTema.decidir("aaaa1111", "1.5.0", "1.5.0", "aaaa1111"))
        assertEquals(s, VersoesDeTema.decidir("aaaa1111", null, "1.0.0", "aaaa1111"))
        assertEquals(s, VersoesDeTema.decidir(null, "1.5.0", "1.5.0", "aaaa1111"))
        assertEquals(m, VersoesDeTema.decidir("bbbb2222", "2.0.0", "1.5.0", "aaaa1111"))
        assertEquals(s, VersoesDeTema.decidir("bbbb2222", "1.0.22379100", "1.1.0", "aaaa1111"))
        assertEquals(s, VersoesDeTema.decidir("bbbb2222", "1.0.22771800", "1.1.0", "aaaa1111"))
        assertEquals(m, VersoesDeTema.decidir("bbbb2222", "1.1.0", "1.0.22771800", "aaaa1111"))
        assertEquals(s, VersoesDeTema.decidir("bbbb2222", "1.0", "1.0.0", "aaaa1111"))
        assertEquals(s, VersoesDeTema.decidir("bbbb2222", "1.0.0-beta", "1.0.0", "aaaa1111"))
    }

    @Test
    fun impressaoSegueAOrdemENomes() {
        val arquivos = mapOf("b.txt" to "2", "a.txt" to "1")
        val abrir = { nome: String -> ByteArrayInputStream(arquivos.getValue(nome).toByteArray()) as InputStream }
        val uma = VersoesDeTema.impressao(listOf("b.txt", "a.txt"), abrir)
        assertEquals(uma, VersoesDeTema.impressao(listOf("a.txt", "b.txt"), abrir))
        assertEquals(64, uma.length)
        // SHA-256 de "a.txt\u00001b.txt\u00002".
        val esperado = java.security.MessageDigest.getInstance("SHA-256")
            .digest("a.txt\u00001b.txt\u00002".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(esperado, uma)
    }

    // ── V2 ──

    @Test
    fun v2_contratoESuporte() {
        for (v in listOf("v1.0", "1.0", "V1.0", " v1.0 ")) assertTrue("v=$v", TemasInstalados.contratoAceito(v))
        for (v in listOf("v1", "1.0.0", "legacy", "LEGACY", "", null)) assertFalse("v=$v", TemasInstalados.contratoAceito(v))
        assertTrue(CatalogoDeTemas.suportado(CatalogoDeTemas.EMBUTIDO))
        assertFalse(CatalogoDeTemas.suportado(CatalogoDeTemas.EMBUTIDO.copy(versaoMinimaDaPonte = "2.0.0")))
        assertFalse(CatalogoDeTemas.suportado(CatalogoDeTemas.EMBUTIDO.copy(versaoDoContrato = "legacy")))
    }

    // ── V3 ──

    private fun xml(corpo: String) = "<theme><name>T</name><contractVersion>v1.0</contractVersion>$corpo</theme>"

    @Test
    fun v3_mascaras() {
        val so = LeitorDoTema.ler(xml("""<speedMask enabled="true" x="0" y="0" width="640" height="720" />"""))!!.mascaras!!
        assertEquals(MascaraNativa(true, "", 0, 0, 640, 720, 1f), so.speed)
        assertEquals(MascaraNativa(false, "", 0, 520, 380, 200, 1f), so.fuel)
        assertEquals(MascaraNativa(false, "", 1540, 520, 380, 200, 1f), so.battery)
        assertEquals(MascaraNativa(false, "", 1320, 0, 600, 720, 1f), so.info)
        assertEquals(MascaraNativa(false, "", 710, 0, 500, 67, 1f), so.topCenter)

        val info = LeitorDoTema.ler(xml("""<infoMask x="1900" width="600"/>"""))!!.mascaras!!.info
        assertEquals(1900, info.x)
        assertEquals(20, info.largura)
        assertTrue(info.ligada)

        val fuel = LeitorDoTema.ler(xml("""<fuelMask opacity="1.7" enabled=" TRUE "/>"""))!!.mascaras!!.fuel
        assertEquals(1f, fuel.opacidade)
        assertTrue(fuel.ligada)

        assertNull(LeitorDoTema.ler(xml(""))!!.mascaras)
    }

    @Test
    fun v3_modosAreaEContrato() {
        val comModo = LeitorDoTema.ler(
            xml("""<DisplayModes><DisplayMode name="Mapa" x="0" y="0" width="1920" height="720"/><DisplayMode name="X" x="0" y="0" width="10"/></DisplayModes>"""),
        )!!
        assertEquals(listOf(br.com.opengw.ponte.ModoDeExibicao("Mapa", 0, 0, 1920, 720)), comModo.modosDeExibicao)

        val area = LeitorDoTema.ler(xml("<AppDefaultPosition><x>640</x><y>95</y><width>640</width><height>560</height></AppDefaultPosition>"))!!
        assertEquals(AreaDosApps(640, 95, 640, 560), area.areaDosApps)
        assertNull(LeitorDoTema.ler(xml("<AppDefaultPosition><x>640</x><y>95</y><width>640</width></AppDefaultPosition>"))!!.areaDosApps)
        // <x> fora da área não conta.
        assertNull(LeitorDoTema.ler(xml("<x>1</x><y>1</y><width>1</width><height>1</height>"))!!.areaDosApps)

        val legado = LeitorDoTema.ler("<theme><name>T</name><contractVersion></contractVersion></theme>")!!
        assertEquals("legacy", legado.versaoDoContrato)
        assertEquals("legacy", LeitorDoTema.ler("<theme><name>T</name></theme>")!!.versaoDoContrato)
    }

    @Test
    fun leitorCamposGerais() {
        val tema = LeitorDoTema.ler(
            """<?xml version="1.0" encoding="utf-8"?>
            <theme>
              <!-- comentário -->
              <name> Meu tema </name>
              <version>1.2.3</version>
              <minBridgeVersion> 1.0.0 </minBridgeVersion>
              <contractVersion> v1.0 </contractVersion>
              <background> car-bg.jpg </background>
              <decentralized> TRUE </decentralized>
              <configurations>
                <configuration><id>a</id><label>A</label><type>COMBO</type><options>Dia, Noite</options><default>Dia</default><stateVariable>displayMode</stateVariable><group>Geral</group></configuration>
                <configuration><id>sem-variavel</id><label>B</label></configuration>
              </configurations>
            </theme>""",
        )!!
        assertEquals("Meu tema", tema.nome)
        assertEquals("1.2.3", tema.versao)
        assertEquals("1.0.0", tema.versaoMinimaDaPonte)
        assertEquals("v1.0", tema.versaoDoContrato)
        assertEquals("car-bg.jpg", tema.fundo)
        assertEquals("index.html", tema.arquivoPrincipal)
        assertTrue(tema.descentralizado)
        assertEquals(listOf(AjusteDoTema("a", "A", "combo", "Dia", "displayMode", listOf("Dia", "Noite"), "Geral")), tema.ajustes)
        assertNull("sem <name>", LeitorDoTema.ler("<theme><version>1</version></theme>"))
        assertNull("XML quebrado", LeitorDoTema.ler("<theme><name>x</theme>"))
        // Um <name> perdido em outro lugar sobrescreve (contrato de fato).
        assertEquals("B", LeitorDoTema.ler("<theme><name>A</name><extra><name>B</name></extra></theme>")!!.nome)
    }

    @Test
    fun leOsTemasDoApk() {
        val raiz = File("src/main/assets/PreinstalledThemes").takeIf { it.isDirectory } ?: File("app/src/main/assets/PreinstalledThemes")
        val pastas = raiz.listFiles()?.filter { it.isDirectory }.orEmpty()
        assertEquals(9, pastas.size)
        for (p in pastas) {
            val tema = LeitorDoTema.ler(File(p, "theme.xml").readText())
            assertNotNull(p.name, tema)
            assertEquals(p.name, "v1.0", tema!!.versaoDoContrato)
            assertEquals(p.name, "app.html", tema.arquivoPrincipal)
        }
        val coffe3 = LeitorDoTema.ler(File(raiz, "coffe3/theme.xml").readText())!!
        assertEquals(AreaDosApps(640, 95, 640, 560), coffe3.areaDosApps)
        assertEquals("car-bg.jpg", coffe3.fundo)
        assertEquals(MascaraNativa(true, "", 660, 0, 600, 90, 1f), coffe3.mascaras!!.topCenter)
    }

    // ── Catálogo numa pasta temporária ──

    private class PacoteFalso(val conteudo: Map<String, Map<String, String>>) : PacoteDeTemas {
        override fun pastas() = conteudo.keys.toList()
        override fun arquivos(pasta: String) = conteudo.getValue(pasta).keys.toList()
        override fun abrir(pasta: String, arquivo: String): InputStream =
            ByteArrayInputStream(conteudo.getValue(pasta).getValue(arquivo).toByteArray())
    }

    private class RegistroFalso : CatalogoDeTemas.Registro {
        val linhas = mutableListOf<String>()
        override fun info(mensagem: String) {
            linhas.add(mensagem)
        }

        override fun erro(mensagem: String, causa: Throwable?) {
            linhas.add("ERRO $mensagem")
        }
    }

    private val prefs = PreferenciasEmMemoria()
    private var garantias = 0

    private fun catalogo(pacote: PacoteDeTemas = PacoteFalso(emptyMap())) = CatalogoDeTemas(
        pastaDosTemas = File(pasta.root, "themes"),
        preferencias = { prefs },
        pacote = pacote,
        garantir = { garantias++ },
        registro = RegistroFalso(),
        agora = { 1_234L },
    )

    private fun instalar(nome: String, xml: String, vararg extras: Pair<String, String>): File {
        val dir = File(pasta.root, "themes/$nome").apply { mkdirs() }
        File(dir, "theme.xml").writeText(xml)
        for ((arquivo, texto) in extras) File(dir, arquivo).apply { parentFile.mkdirs() }.writeText(texto)
        return dir
    }

    private val coffe3 = "<theme><name>Stuttgart Clássico</name><version>0.16.0</version><minBridgeVersion>1.0.0</minBridgeVersion>" +
        "<contractVersion>v1.0</contractVersion><thumbnail>thumbnail.png</thumbnail><mainFile>app.html</mainFile>" +
        "<background>car-bg.jpg</background></theme>"

    @Test
    fun v4_fundoDoTemaAtivo() {
        val c = catalogo()
        val dir = instalar("coffe3", coffe3, "car-bg.jpg" to "img")
        val esperado = File(dir, "car-bg.jpg").canonicalFile
        prefs.mapa["activeCustomTheme"] = "coffe3"
        assertEquals(esperado, c.arquivoDeFundoDoTemaAtivo(null))
        assertEquals(esperado, c.arquivoDeFundoDoTemaAtivo(""))
        assertEquals(esperado, c.arquivoDeFundoDoTemaAtivo("car-bg.jpg"))
        assertEquals(esperado, c.arquivoDeFundoDoTemaAtivo("./car-bg.jpg"))
        assertNull(c.arquivoDeFundoDoTemaAtivo("img/../car-bg.jpg"))
        assertEquals(esperado, c.arquivoDeFundoDoTemaAtivo("sub\\car-bg.jpg"))
        assertNull(c.arquivoDeFundoDoTemaAtivo("inexistente.png"))
        prefs.mapa["activeCustomTheme"] = ""
        assertNull(c.arquivoDeFundoDoTemaAtivo(null))
        prefs.mapa["activeCustomTheme"] = "Default"
        assertNull(c.arquivoDeFundoDoTemaAtivo(null))
    }

    @Test
    fun lerMontaCaminhos() {
        val c = catalogo()
        val dir = instalar("coffe3", coffe3, "car-bg.jpg" to "img")
        val tema = c.ler("coffe3")!!
        assertEquals("coffe3", tema.pasta)
        assertEquals(File(File(pasta.root, "themes/coffe3"), "thumbnail.png").absolutePath, tema.miniatura)
        assertEquals(File(dir, "car-bg.jpg").canonicalPath, tema.fundoAbsoluto)
        assertEquals(File(dir, "car-bg.jpg").canonicalPath, c.caminhoDoFundo("coffe3"))
        assertEquals("", c.caminhoDoFundo("Default"))
        instalar("web", "<theme><name>W</name><thumbnail>https://x/y.png</thumbnail><background>nada.jpg</background></theme>")
        assertEquals("https://x/y.png", c.ler("web")!!.miniatura)
        assertEquals("", c.ler("web")!!.fundoAbsoluto)
    }

    @Test
    fun pastaSegura() {
        val c = catalogo()
        instalar("coffe3", coffe3)
        for (ruim in listOf("../x", "a/b", "", ".", "..", "a\\b")) assertNull("pasta '$ruim'", c.diretorioSeguro(ruim))
        assertNotNull(c.diretorioSeguro("coffe3"))
        instalar("tea30", coffe3)
        assertNull("fora da pasta", c.arquivo("coffe3", "../tea30/theme.xml"))
        assertNotNull(c.arquivo("coffe3", "theme.xml"))
        assertNull(c.arquivo("coffe3", ""))
        assertNull(c.arquivo("coffe3", "nada.txt"))
    }

    @Test
    fun listarSoOsSuportados() {
        val c = catalogo()
        instalar("coffe3", coffe3)
        instalar("velho", "<theme><name>Velho</name></theme>")
        instalar(".escondido", coffe3)
        instalar("Default", coffe3)
        File(pasta.root, "themes/sem-xml").mkdirs()
        assertEquals(listOf("coffe3"), c.listar().map { it.pasta })
        assertFalse(c.pastaInvalida(""))
        assertFalse(c.pastaInvalida("Default"))
        assertFalse(c.pastaInvalida(null))
        assertTrue(c.pastaInvalida("velho"))
        assertTrue(c.pastaInvalida("sem-xml"))
        assertFalse(c.pastaInvalida("coffe3"))
    }

    @Test
    fun sanearVoltaAoEmbutidoEGarante() {
        val c = catalogo()
        instalar("coffe3", coffe3)
        prefs.mapa["activeCustomTheme"] = "apagado"
        prefs.mapa["virtualClusterTheme"] = "Apagado"
        c.sanearTemaAtivo()
        assertEquals("", prefs.mapa["activeCustomTheme"])
        assertEquals("Default", prefs.mapa["virtualClusterTheme"])
        assertEquals(1_234L, prefs.mapa["themeReloadNonce"])
        assertEquals(1, garantias)

        prefs.mapa["activeCustomTheme"] = "coffe3"
        prefs.mapa["virtualClusterTheme"] = "stuttgart clássico"
        prefs.mapa.remove("themeReloadNonce")
        c.sanearTemaAtivo()
        assertEquals("coffe3", prefs.mapa["activeCustomTheme"])
        assertNull(prefs.mapa["themeReloadNonce"])
        assertEquals(2, garantias)

        prefs.mapa["virtualClusterTheme"] = "Outro nome"
        c.sanearTemaAtivo()
        assertEquals("", prefs.mapa["activeCustomTheme"])
        assertEquals(3, garantias)
    }

    @Test
    fun apagar() {
        val c = catalogo()
        instalar("coffe3", coffe3)
        instalar("tea30", coffe3)
        prefs.mapa["activeCustomTheme"] = "coffe3"
        assertFalse(c.apagar("Default"))
        assertFalse(c.apagar("../themes"))
        assertFalse(c.apagar("nao-existe"))
        assertTrue(c.apagar("tea30"))
        assertEquals("coffe3", prefs.mapa["activeCustomTheme"])
        assertEquals(0, garantias)
        assertTrue(c.apagar("coffe3"))
        assertEquals("", prefs.mapa["activeCustomTheme"])
        assertEquals(1, garantias)
        assertFalse(File(pasta.root, "themes/coffe3").exists())
    }

    @Test
    fun semearPodaESemeia() {
        val pacote = PacoteFalso(
            mapOf(
                "coffe3" to mapOf("theme.xml" to coffe3, "app.html" to "<html>novo</html>"),
                "quebrado" to mapOf("theme.xml" to "<theme><version>1</version></theme>"),
            ),
        )
        val c = catalogo(pacote)
        instalar("sportred", coffe3)
        instalar("Default", coffe3)
        // Marca errada e mesma versão: semeia de novo e mantém sobras.
        val dir = instalar("coffe3", coffe3, "app.html" to "<html>velho</html>", ".opengw-seed" to "errada", "sobra.txt" to "x")
        c.semearAgora()
        assertFalse(File(pasta.root, "themes/sportred").exists())
        assertTrue(File(pasta.root, "themes/Default").exists())
        assertEquals("<html>novo</html>", File(dir, "app.html").readText())
        assertTrue(File(dir, "sobra.txt").exists())
        val marca = File(dir, ".opengw-seed").readText()
        assertEquals(64, marca.length)
        assertEquals(setOf("coffe3"), c.semeadas)
        assertFalse(File(pasta.root, "themes/quebrado").exists())

        // Na abertura seguinte a marca bate: nada muda.
        File(dir, "app.html").writeText("<html>mexido</html>")
        val outro = catalogo(pacote)
        outro.semearAgora()
        assertEquals("<html>mexido</html>", File(dir, "app.html").readText())
        assertTrue(outro.semeadas.isEmpty())
        assertEquals(setOf("coffe3", "quebrado"), outro.pastasDoApk)
    }
}
