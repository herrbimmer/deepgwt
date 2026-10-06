package br.com.deepgwt.tela3d.integracao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * O manifesto aponta para classes que existem e a tela inicial é a Activity nova, com os atributos de
 * HOME da central. Lê o texto do manifesto (o teste roda na pasta do módulo `app`).
 */
class ManifestoDaTela3dTest {

    private val manifesto: String by lazy {
        val arquivo = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.isFile }
        arquivo.readText()
    }

    private fun bloco(inicio: String, fim: String): String {
        val i = manifesto.indexOf(inicio)
        assertTrue("manifesto sem $inicio", i >= 0)
        return manifesto.substring(i, manifesto.indexOf(fim, i))
    }

    @Test
    fun `a tela inicial e a Activity nova com os atributos de HOME`() {
        val b = bloco("android:name=\"br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity\"", "</activity>")
        assertTrue(b.contains("android.intent.category.HOME"))
        assertTrue(b.contains("android.intent.category.DEFAULT"))
        assertFalse("a tela inicial não é ícone de LAUNCHER", b.contains("android.intent.category.LAUNCHER"))
        assertTrue(b.contains("android:launchMode=\"singleTask\""))
        assertTrue(b.contains("android:screenOrientation=\"landscape\""))
        assertTrue(b.contains("android:resizeableActivity=\"false\""))
        assertTrue(b.contains("@style/Theme.DeepGwtLauncher"))
        assertTrue(b.contains("uiMode|screenSize|smallestScreenSize"))
    }

    @Test
    fun `so os dois servicos usam o nome antigo, como cascas de uma linha`() {
        val semComentarios = manifesto.replace(Regex("<!--[\\s\\S]*?-->"), "")
        val antigos = Regex("com\\.havalh6[\\w.]*").findAll(semComentarios).map { it.value }.toSet()
        assertEquals(
            setOf("com.havalh6.viewer.DesktopSwitcherHitService", "com.havalh6.viewer.MediaNotificationListener"),
            antigos,
        )
    }

    @Test
    fun `as cascas existem e herdam das classes novas`() {
        val seletor = Class.forName("com.havalh6.viewer.DesktopSwitcherHitService")
        val ouvinte = Class.forName("com.havalh6.viewer.MediaNotificationListener")
        assertEquals("br.com.deepgwt.tela3d.catalogo.ServicoDoSeletorDeAreas", seletor.superclass.name)
        assertEquals("br.com.deepgwt.tela3d.midia.OuvinteDeMidiaDaTelaInicial", ouvinte.superclass.name)
        assertTrue(manifesto.contains("android:name=\"br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity\""))
        assertNotNull(Class.forName("br.com.deepgwt.tela3d.ponte.TelaInicial3dActivity"))
    }

    @Test
    fun `os dois servicos mantem rotulo proprio e a permissao de ligacao`() {
        val acessibilidade = bloco("android:name=\"com.havalh6.viewer.DesktopSwitcherHitService\"", "</service>")
        assertTrue(acessibilidade.contains("@string/rotulo_do_seletor_de_desktops"))
        assertTrue(acessibilidade.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertTrue(acessibilidade.contains("@xml/desktop_switcher_hit"))
        val ouvinte = bloco("android:name=\"com.havalh6.viewer.MediaNotificationListener\"", "</service>")
        assertTrue(ouvinte.contains("@string/rotulo_do_ouvinte_de_midia"))
        assertTrue(ouvinte.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
    }

    @Test
    fun `um so HOME no manifesto`() {
        assertEquals(1, Regex("android.intent.category.HOME").findAll(manifesto).count())
    }
}
