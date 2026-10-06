// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 2, seção A: o JSON de `displayAppConfigs` (V1, V2) e as operações sobre a lista. */
class ConfiguracoesDoLancadorTest {

    private val aa = PacotesDaCentral.predefinida("com.ts.androidauto.app")!!
    private val ajustes = PacotesDaCentral.predefinida("com.android.settings")!!
    private val spotify = ConfigDoApp(
        "com.spotify.music", "com.spotify.music.MainActivity", 1, 10, 62, 800, 400,
        corDoIcone = "#FF00FF", forcarFoco = true, ignorarAreaDoTema = true,
    )

    @Test
    fun `V1 - escrita byte a byte`() {
        val esperado = """[{"packageName":"com.ts.androidauto.app","activityName":"com.ts.androidauto.app.display.AapActivity","displayId":3,"x":0,"y":0,"width":1920,"height":720,"customName":"Android Auto","forceFocus":false,"overrideThemeDimensions":false},{"packageName":"com.android.settings","activityName":"com.android.settings.Settings","displayId":0,"x":0,"y":0,"width":1920,"height":720,"substituteIcon":"settings","customName":"Configurações","forceFocus":false,"overrideThemeDimensions":false},{"packageName":"com.spotify.music","activityName":"com.spotify.music.MainActivity","displayId":1,"x":10,"y":62,"width":800,"height":400,"iconColor":"#FF00FF","forceFocus":true,"overrideThemeDimensions":true}]"""
        val escrito = JsonDasConfigs.escrever(listOf(aa, ajustes, spotify))
        assertEquals(esperado, escrito)
        assertTrue(escrito.toByteArray(Charsets.UTF_8).contentEquals(esperado.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `V1 - escape padrao do Gson sem modo HTML`() {
        val c = ConfigDoApp("p", "a.b.\$Main", 0, 0, 0, 1, 1, nome = "A<B&C='x'\"é")
        val escrito = JsonDasConfigs.escrever(listOf(c))
        assertTrue(escrito, "\"activityName\":\"a.b.\$Main\"" in escrito)
        assertTrue(escrito, "\"customName\":\"A<B&C='x'\\\"é\"" in escrito)
    }

    @Test
    fun `ida e volta sem perder campos`() {
        val lista = listOf(aa, ajustes, spotify)
        assertEquals(lista, JsonDasConfigs.ler(JsonDasConfigs.escrever(lista)))
    }

    @Test
    fun `V2 - leitura`() {
        assertEquals(emptyList<ConfigDoApp>(), JsonDasConfigs.ler(null))
        assertEquals(
            listOf(ConfigDoApp("a.b", "", 1, 0, 0, 0, 0)),
            JsonDasConfigs.ler("""[{"packageName":"a.b","displayId":1}]"""),
        )
        assertEquals(
            listOf(ConfigDoApp("x.y", "x.y.Main", 3, 0, 0, 1920, 720)),
            JsonDasConfigs.ler("""{"x.y":{"packageName":"x.y","activityName":"x.y.Main","displayId":3,"x":0,"y":0,"width":1920,"height":720}}"""),
        )
        val registros = mutableListOf<String>()
        assertEquals(emptyList<ConfigDoApp>(), JsonDasConfigs.ler("lixo") { registros += it })
        assertEquals(1, registros.size)
        assertEquals(emptyList<ConfigDoApp>(), JsonDasConfigs.ler("""[{"displayId":3}]"""))
    }

    @Test
    fun `chaves desconhecidas sao ignoradas e nulos contam como ausentes`() {
        val lida = JsonDasConfigs.ler("""[{"packageName":"p","activityName":null,"novo":1,"customName":null,"forceFocus":true}]""")
        assertEquals(listOf(ConfigDoApp("p", "", 0, 0, 0, 0, 0, forcarFoco = true)), lida)
    }

    // ── A4–A7 pelo motor ────────────────────────────────────────────────────────────────────────

    @Test
    fun `A4 - predefinidos na ordem`() {
        assertEquals(
            listOf("com.ts.androidauto.app", "com.ts.carplay.app", "com.android.settings"),
            PacotesDaCentral.PREDEFINIDOS.map { it.pacote },
        )
        assertEquals("Apple CarPlay", PacotesDaCentral.PREDEFINIDOS[1].nome)
        assertEquals("com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity", PacotesDaCentral.PREDEFINIDOS[1].atividade)
    }

    @Test
    fun `A5 - salvar, apagar, subir e descer`() {
        val b = Bancada()
        val t = b.telas
        t.salvar(aa)
        t.salvar(spotify)
        t.salvar(ajustes)
        assertEquals(listOf(aa, spotify, ajustes), t.configuracoes())
        t.salvar(spotify.copy(display = 3))
        assertEquals(3, t.configuracao("com.spotify.music")!!.display)
        assertEquals(1, t.configuracoes().indexOfFirst { it.pacote == "com.spotify.music" })

        t.subir("com.android.settings")
        assertEquals(listOf("com.ts.androidauto.app", "com.android.settings", "com.spotify.music"), t.configuracoes().map { it.pacote })
        val escritas = b.prefs.escritas
        t.subir("com.ts.androidauto.app")
        assertEquals("subir o primeiro não grava", escritas, b.prefs.escritas)
        t.descer("com.spotify.music")
        assertEquals("descer o último não grava", escritas, b.prefs.escritas)
        t.descer("com.ts.androidauto.app")
        assertEquals(listOf("com.android.settings", "com.ts.androidauto.app", "com.spotify.music"), t.configuracoes().map { it.pacote })

        t.apagar("com.ts.androidauto.app")
        assertEquals(listOf("com.android.settings", "com.spotify.music"), t.configuracoes().map { it.pacote })
        t.salvarTodas(listOf(spotify))
        assertEquals(listOf(spotify), t.configuracoes())
    }

    @Test
    fun `A6 - config ou padrao`() {
        val b = Bancada()
        val t = b.telas
        b.sistema.atividades["com.beantechs.hvac"] = "com.beantechs.hvac.Main"
        b.sistema.atividades["com.waze"] = "com.waze.FreeMapAppActivity"

        val hvac = t.configuracaoOuPadrao("com.beantechs.hvac", gravarNova = false)!!
        assertEquals(ConfigDoApp("com.beantechs.hvac", "com.beantechs.hvac.Main", 3, 0, 0, 1920, 720, iconeSubstituto = "gwm"), hvac)
        assertEquals("gravar = false não grava", emptyList<ConfigDoApp>(), t.configuracoes())

        val waze = t.configuracaoOuPadrao("com.waze", gravarNova = true)!!
        assertNull(waze.iconeSubstituto)
        assertEquals(listOf(waze), t.configuracoes())

        assertEquals(aa, t.configuracaoOuPadrao("com.ts.androidauto.app", gravarNova = true))
        assertEquals(2, t.configuracoes().size)
        assertNull(t.configuracaoOuPadrao("x.nao.instalado", gravarNova = true))

        // gravada vem primeiro e não grava de novo
        val escritas = b.prefs.escritas
        assertEquals(waze, t.configuracaoOuPadrao("com.waze", gravarNova = true))
        assertEquals(escritas, b.prefs.escritas)
    }

    @Test
    fun `A7 - semeia as Configuracoes uma vez, numa escrita`() {
        val b = Bancada()
        b.telas.salvar(spotify)
        val antes = b.prefs.escritas
        b.telas.semearAtalhosPadrao()
        assertEquals(antes + 1, b.prefs.escritas)
        assertEquals(listOf(spotify, ajustes), b.telas.configuracoes())
        assertEquals(true, b.prefs.valores[Preferencias.ANDROID_SETTINGS_SHORTCUT_SEEDED.chave])

        b.telas.apagar("com.android.settings")
        b.telas.semearAtalhosPadrao()
        assertEquals("já semeado: não volta", listOf(spotify), b.telas.configuracoes())

        val c = Bancada()
        c.telas.salvar(ajustes)
        c.telas.semearAtalhosPadrao()
        assertEquals(listOf(ajustes), c.telas.configuracoes())
        assertEquals(true, c.prefs.valores[Preferencias.ANDROID_SETTINGS_SHORTCUT_SEEDED.chave])
    }

    @Test
    fun `H1 - receptor do recuo limita a 0 ate 240`() {
        assertEquals(60, ReceptorDoRecuo.valorDoRecuo(null))
        assertEquals(240, ReceptorDoRecuo.valorDoRecuo(9999))
        assertEquals(0, ReceptorDoRecuo.valorDoRecuo(-5))
        assertEquals(90, ReceptorDoRecuo.valorDoRecuo(90))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
