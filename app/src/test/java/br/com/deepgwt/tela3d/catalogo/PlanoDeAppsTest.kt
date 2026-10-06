package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanoDeAppsTest {
    private val proprio = "br.com.redesurftank.havalshisuku"
    private val regras = RegrasDoCatalogo(proprio) { it?.contains("carplay") == true || it?.contains("androidauto") == true }
    private val sistema = PlanejadorDeApps(regras, emulador = false)
    private val emulado = PlanejadorDeApps(regras, emulador = true)

    private fun a(pacote: String, rotulo: String = pacote.substringAfterLast('.'), classe: String = "$pacote.Main") =
        AtividadeDeLancador(pacote, classe, rotulo)

    private fun estado(
        recentes: List<String> = emptyList(),
        ocultos: Set<String> = emptySet(),
        janela: Set<String> = emptySet(),
        pers: MapaDePersonalizacoes = MapaDePersonalizacoes.VAZIO,
    ) = EstadoDoUsuario(recentes, ocultos, janela, pers)

    private val atividades = listOf(
        a("com.waze", "Waze"),
        a("com.waze", "Waze Outra", "com.waze.Segunda"),
        a("com.spotify.music", "Spotify"),
        a(proprio, "DeepGWT"),
        a("com.beantechs.hvac", "Clima"),
        a("com.ts.carplay.app", "CarPlay"),
        a("com.beantechs.settings", "Settings"),
        a("com.beantechs.energyassistant", "ea"),
        a("com.beantechs.outro", "Outro GWM"),
        a("org.videolan.vlc", "VLC"),
    )

    @Test
    fun `catalogo mantem a ordem do sistema e um item por pacote`() {
        val r = sistema.catalogo(atividades, estado())
        assertEquals(
            listOf(
                "com.waze", "com.spotify.music", "com.beantechs.settings", "com.beantechs.energyassistant",
                "com.beantechs.outro", "org.videolan.vlc",
            ),
            r.map { it.pacote },
        )
        // a primeira atividade do pacote é a que vale
        assertEquals("Waze", r.first().rotulo)
        assertEquals("com.waze.Main", r.first().atividade?.classe)
    }

    @Test
    fun `catalogo - fixado usa rotulo fixo e energy assistant tem nome proprio`() {
        val r = sistema.catalogo(atividades, estado()).associateBy { it.pacote }
        assertEquals("Configurações do Sistema", r.getValue("com.beantechs.settings").rotulo)
        assertEquals("Energy Assistant", r.getValue("com.beantechs.energyassistant").rotulo)
        assertEquals("Outro GWM", r.getValue("com.beantechs.outro").rotulo)
        assertTrue(r.getValue("com.beantechs.settings").fixado)
        assertFalse(r.getValue("com.waze").fixado)
    }

    @Test
    fun `catalogo - nome personalizado vence`() {
        val pers = MapaDePersonalizacoes.VAZIO
            .gravar("com.waze", "Mapa", null, null, null)
            .gravar("com.beantechs.settings", "Ajustes", null, null, null)
        val r = sistema.catalogo(atividades, estado(pers = pers)).associateBy { it.pacote }
        assertEquals("Mapa", r.getValue("com.waze").rotulo)
        assertEquals("Ajustes", r.getValue("com.beantechs.settings").rotulo)
    }

    @Test
    fun `catalogo - ocultos saem`() {
        val r = sistema.catalogo(atividades, estado(ocultos = setOf("com.waze", "com.beantechs.settings")))
        assertFalse(r.any { it.pacote == "com.waze" || it.pacote == "com.beantechs.settings" })
    }

    @Test
    fun `catalogo - placas`() {
        val pers = MapaDePersonalizacoes.VAZIO.gravar("org.videolan.vlc", "", "music", "#FFFFFF", "#111111")
        val r = sistema.catalogo(atividades, estado(pers = pers)).associateBy { it.pacote }
        assertEquals(DecisaoDePlaca.Nenhuma, r.getValue("com.waze").placa)
        assertEquals(DecisaoDePlaca.PorTema(true), r.getValue("com.beantechs.outro").placa)
        assertEquals(DecisaoDePlaca.PorTema(true), r.getValue("com.beantechs.energyassistant").placa)
        assertEquals(DecisaoDePlaca.Cor(0xFF111111.toInt()), r.getValue("org.videolan.vlc").placa)
    }

    @Test
    fun `fora do emulador nao ha stubs`() {
        val r = sistema.catalogo(emptyList(), estado())
        assertTrue(r.isEmpty())
        assertTrue(sistema.linha(emptyList(), estado()).isEmpty())
        assertTrue(sistema.fixadosDoHub(emptyList(), estado()).isEmpty())
        assertFalse(sistema.ehStub("com.beantechs.fake.mediacenter", emptyList()))
    }

    @Test
    fun `emulador - stubs extras entram no fim do catalogo e da linha`() {
        val cat = emulado.catalogo(atividades, estado())
        assertEquals(
            listOf("com.beantechs.fake.mediacenter", "com.beantechs.fake.navigation"),
            cat.takeLast(2).map { it.pacote },
        )
        assertEquals("Media Center", cat[cat.size - 2].rotulo)
        assertEquals("Navegação GWM", cat.last().rotulo)
        assertTrue(cat.last().stub)
        assertNull(cat.last().atividade)
        assertEquals(DecisaoDePlaca.PorTema(true), cat.last().placa)

        val linha = emulado.linha(atividades, estado())
        assertEquals(
            listOf("com.beantechs.fake.mediacenter", "com.beantechs.fake.navigation"),
            linha.takeLast(2).map { it.pacote },
        )
    }

    @Test
    fun `emulador - stub extra some se existe app real com o mesmo pacote ou se oculto`() {
        val real = atividades + a("com.beantechs.fake.navigation", "Navegação real")
        val cat = emulado.catalogo(real, estado(ocultos = setOf("com.beantechs.fake.mediacenter")))
        val stubs = cat.filter { it.stub }
        assertTrue(stubs.isEmpty())
        val nav = cat.single { it.pacote == "com.beantechs.fake.navigation" }
        assertEquals("Navegação real", nav.rotulo)
        assertFalse(nav.stub)
    }

    @Test
    fun `ehStub - so no emulador e so se nao esta instalado`() {
        assertTrue(emulado.ehStub("com.beantechs.fake.navigation", atividades))
        assertTrue(emulado.ehStub("com.beantechs.vehiclecenter", atividades))
        assertFalse(emulado.ehStub("com.beantechs.settings", atividades))
        assertFalse(emulado.ehStub("com.waze", atividades))
        assertFalse(emulado.ehStub(null, atividades))
        assertFalse(sistema.ehStub("com.beantechs.fake.navigation", atividades))
    }

    @Test
    fun `linha - sem fixados nem projecao nem proprio nem ignorados e com ocultos marcados`() {
        val r = sistema.linha(atividades, estado(ocultos = setOf("com.spotify.music")))
        assertEquals(
            listOf("com.waze", "com.spotify.music", "com.beantechs.outro", "org.videolan.vlc"),
            r.map { it.pacote },
        )
        assertTrue(r.single { it.pacote == "com.spotify.music" }.oculto)
        assertFalse(r.single { it.pacote == "com.waze" }.oculto)
    }

    @Test
    fun `linha - um chip por pacote mesmo com varias atividades`() {
        val r = sistema.linha(atividades, estado())
        assertEquals(1, r.count { it.pacote == "com.waze" })
        assertEquals("Waze", r.first { it.pacote == "com.waze" }.rotulo)
    }

    @Test
    fun `linha - rotulo do chip nao usa o rotulo fixo dos fixados`() {
        val r = sistema.linha(atividades, estado()).associateBy { it.pacote }
        assertEquals("Outro GWM", r.getValue("com.beantechs.outro").rotulo)
    }

    @Test
    fun `recentes - ate 3 instalados e permitidos na ordem MRU`() {
        val mru = listOf("org.videolan.vlc", "com.beantechs.settings", "nao.instalado", "com.spotify.music", "com.waze", "com.beantechs.outro")
        val r = sistema.recentes(atividades, estado(recentes = mru, ocultos = setOf("com.spotify.music")))
        assertEquals(listOf("org.videolan.vlc", "com.waze", "com.beantechs.outro"), r.map { it.pacote })
        assertTrue(r.none { it.oculto })
    }

    @Test
    fun `hub - ordem de D1 e so presentes e nao ocultos`() {
        val r = sistema.fixadosDoHub(atividades, estado())
        assertEquals(listOf("com.beantechs.settings", "com.beantechs.energyassistant"), r.map { it.pacote })
        assertEquals("Configurações do Sistema", r[0].rotulo)
        assertTrue(r.none { it.stub })

        val sem = sistema.fixadosDoHub(atividades, estado(ocultos = setOf("com.beantechs.settings", "com.beantechs.energyassistant")))
        assertTrue(sem.isEmpty())
    }

    @Test
    fun `hub no emulador mostra os 4 como stubs quando ausentes`() {
        val r = emulado.fixadosDoHub(emptyList(), estado())
        assertEquals(FatosDoCarro.FIXADOS, r.map { it.pacote })
        assertTrue(r.all { it.stub })
        assertEquals("GWM Home", r.last().rotulo)
    }

    @Test
    fun `hub - nome e placa do chip`() {
        assertEquals("GWM", sistema.rotuloDoHub(estado()))
        assertEquals(DecisaoDePlaca.PorTema(true), sistema.placaDoHub(estado()))
        val pers = MapaDePersonalizacoes.VAZIO.gravar("__gwm_hub", "Carro", "gwm", "#FFFFFF", "#111111")
        assertEquals("Carro", sistema.rotuloDoHub(estado(pers = pers)))
        assertEquals(DecisaoDePlaca.Cor(0xFF111111.toInt()), sistema.placaDoHub(estado(pers = pers)))
    }

    @Test
    fun `ocultos ordenados por pacote e rotulo B4`() {
        val e = estado(ocultos = setOf("com.zzz", "com.beantechs.settings", "com.aaa"))
        assertEquals(listOf("com.aaa", "com.beantechs.settings", "com.zzz"), sistema.ocultosOrdenados(e))
        assertEquals("com.zzz", sistema.rotuloDeOculto("com.zzz", null))
        assertEquals("Configurações do Sistema", sistema.rotuloDeOculto("com.beantechs.settings", a("com.beantechs.settings", "x")))
        assertEquals("Waze", sistema.rotuloDeOculto("com.waze", a("com.waze", "Waze")))
    }

    @Test
    fun `atividades com pacote vazio sao ignoradas`() {
        val r = sistema.catalogo(listOf(a("", "x"), a("com.waze", "Waze")), estado())
        assertEquals(listOf("com.waze"), r.map { it.pacote })
    }
}
