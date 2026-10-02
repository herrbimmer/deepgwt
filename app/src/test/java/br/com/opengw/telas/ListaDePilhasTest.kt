package br.com.opengw.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parte 2, seção E: leitura da lista de pilhas (vetor V4) e a decisão 6 (`RootTask id=`). */
class ListaDePilhasTest {

    private val v4 = """
Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
 configuration={1.0 ... winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=fullscreen mActivityType=home} s.12}
  taskId=4: com.beantechs.mediacenter/com.beantechs.mediacenter.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
Stack id=7 bounds=[0,0][1920,660] displayId=0 userId=0
 configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 660) mWindowingMode=freeform mActivityType=standard} s.12}
  taskId=31: com.spotify.music/.MainActivity bounds=[0,0][1920,600] userId=0 visible=false
  taskId=32: com.waze/.FreeMapAppActivity bounds=[0,0][1920,660] userId=0 visible=false
Stack id=2 bounds=[0,62][1920,658] displayId=1 userId=0
 configuration={1.0 winConfig={ mWindowingMode=split-screen-secondary} mActivityType=standard} s.12}
  taskId=9: com.ts.carplay.app/.MainActivity bounds=[0,62][1920,658] userId=0 visible=true
Stack id=11 bounds=[135,0][1920,720] displayId=3 userId=0
  taskId=40: com.ts.androidauto.app/com.ts.androidauto.app.display.AapActivity bounds=[135,0][1920,720] userId=0 visible=true
""".trimIndent()

    private val telaCheia: (Int) -> IntArray = { intArrayOf(0, 0, 1920, 720) }
    private val lista = ListaDePilhas.ler(v4, telaCheia)

    private fun t(tarefa: Int, pilha: Int, display: Int, vararg l: Int) = TarefaNaTela(tarefa, pilha, display, l)

    @Test
    fun `pacote da pilha`() {
        assertEquals("com.beantechs.mediacenter", lista.pacoteDaPilha(0))
        assertEquals("com.spotify.music", lista.pacoteDaPilha(7))
        assertEquals("com.ts.carplay.app", lista.pacoteDaPilha(2))
        assertEquals("com.ts.androidauto.app", lista.pacoteDaPilha(11))
        assertNull(lista.pacoteDaPilha(99))
    }

    @Test
    fun `todas as tarefas do pacote usam os limites da pilha`() {
        assertEquals(listOf(t(31, 7, 0, 0, 0, 1920, 660)), lista.tarefasDoPacote("com.spotify.music"))
        assertEquals(listOf(t(32, 7, 0, 0, 0, 1920, 660)), lista.tarefasDoPacote("com.waze"))
        assertEquals(listOf(t(9, 2, 1, 0, 62, 1920, 658)), lista.tarefasDoPacote("com.ts.carplay.app"))
        assertEquals(emptyList<TarefaNaTela>(), lista.tarefasDoPacote("com.ts.carplay"))
        assertEquals(listOf(t(40, 11, 3, 135, 0, 1920, 720)), lista.tarefasDoPacote("com.ts.androidauto.app"))
    }

    @Test
    fun `tarefa do pacote usa os limites da linha da tarefa`() {
        assertEquals(t(31, 7, 0, 0, 0, 1920, 600), lista.tarefaDoPacote("com.spotify.music"))
    }

    @Test
    fun `app da frente de cada display`() {
        assertEquals("com.beantechs.mediacenter", lista.pacoteNoTopo(0))
        assertEquals("com.ts.carplay.app", lista.pacoteNoTopo(1))
        assertEquals("com.ts.androidauto.app", lista.pacoteNoTopo(3))
        assertNull(lista.pacoteNoTopo(2))
    }

    @Test
    fun `pilha do pacote no display com o modo`() {
        assertEquals(ListaDePilhas.PilhaComModo(7, "freeform"), lista.pilhaDoPacoteNoDisplay("com.waze", 0))
        assertEquals(ListaDePilhas.PilhaComModo(2, "split-screen-secondary"), lista.pilhaDoPacoteNoDisplay("com.ts.carplay.app", 1))
        assertEquals(ListaDePilhas.PilhaComModo(11, "unknown"), lista.pilhaDoPacoteNoDisplay("com.ts.androidauto.app", 3))
        assertNull(lista.pilhaDoPacoteNoDisplay("com.waze", 3))
    }

    @Test
    fun `tarefas na pilha, outra tarefa, primeira comum e pilhas do display`() {
        assertEquals(2, lista.tarefasNaPilha(7))
        assertEquals(1, lista.tarefasNaPilha(0))
        assertEquals(OutraTarefa("com.waze", ".FreeMapAppActivity", 0, 32), lista.outraTarefaDaPilha(7, "com.spotify.music"))
        assertEquals(
            OutraTarefa("com.beantechs.mediacenter", "com.beantechs.mediacenter.MainActivity", 0, 4),
            lista.primeiraTarefaComum(0, "br.com.opengw"),
        )
        assertEquals(listOf(0, 7), lista.pilhasDoDisplay(0))
        assertNull(lista.primeiraTarefaComum(3, "br.com.opengw"))
    }

    @Test
    fun `CarPlay no display 1 e nao no 0`() {
        val b = Bancada()
        b.central.pilha(1, "com.ts.carplay.app", limites = intArrayOf(0, 62, 1920, 658))
        b.central.pilha(0, "com.beantechs.mediacenter")
        assertTrue(b.esp.carPlayNoDisplay(1))
        assertFalse(b.esp.carPlayNoDisplay(0))
    }

    @Test
    fun `primeiras tarefas ignora vazios e repetidos com uma leitura so`() {
        val b = Bancada()
        b.central.pilha(0, "com.waze")
        val achadas = b.telas.primeirasTarefas(listOf("com.waze", "", "x.y", "com.waze"))
        assertEquals(setOf("com.waze"), achadas.keys)
        assertEquals(1, b.central.comandos.count { it == "am stack list" })
    }

    @Test
    fun `cabecalho sem limites usa a tela cheia do display e limites negativos sao pulados`() {
        val texto = """
Stack id=5 displayId=3 userId=0
  taskId=1: a.b/.X userId=0
Stack id=6 bounds=[-5,0][100,100] mbounds=[1,2][3,4] displayId=0 userId=0
  taskId=2: c.d/.Y
""".trimIndent()
        val l = ListaDePilhas.ler(texto) { intArrayOf(0, 0, 800, 480) }
        assertTrue(l.pilhas[0].limites.contentEquals(intArrayOf(0, 0, 800, 480)))
        assertTrue(l.pilhas[1].limites.contentEquals(intArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun `decisao 6 - RootTask do Android 10+ tambem e lido`() {
        val texto = """
RootTask id=1 bounds=[0,0][1080,2400] displayId=0 userId=0
 configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1080, 2400) mWindowingMode=fullscreen mActivityType=home} s.1}
  taskId=1: com.android.launcher3/.uioverrides.QuickstepLauncher bounds=[0,0][1080,2400] userId=0 visible=true
RootTask id=12 bounds=[0,0][1920,720] displayId=2 userId=10
  taskId=12: br.com.opengw/.PrincipalActivity bounds=[0,0][1920,720] userId=10 visible=true
""".trimIndent()
        val l = ListaDePilhas.ler(texto, telaCheia)
        assertEquals(listOf(1, 12), l.pilhas.map { it.id })
        assertEquals("com.android.launcher3", l.pacoteNoTopo(0))
        assertEquals(t(12, 12, 2, 0, 0, 1920, 720), l.tarefaDoPacote("br.com.opengw"))
    }

    @Test
    fun `lista vazia ou lixo nao quebra`() {
        val vazia = ListaDePilhas.ler("", telaCheia)
        assertTrue(vazia.pilhas.isEmpty())
        assertNull(vazia.pacoteNoTopo(0))
        val lixo = ListaDePilhas.ler("Exception: java.lang.SecurityException\nnada", telaCheia)
        assertTrue(lixo.pilhas.isEmpty())
    }

    @Test
    fun `reserva do dumpsys para o app da frente`() {
        assertEquals(
            "com.foo.bar",
            RegrasDoEspelhamento.pacoteDoDumpsys("    mResumedActivity: ActivityRecord{1a2b u0 com.foo.bar/.Main t12}"),
        )
        assertNull(RegrasDoEspelhamento.pacoteDoDumpsys(""))
        val b = Bancada()
        assertNull(b.telas.pacoteNoTopo(3))
        assertTrue(b.central.comandos.any { it.startsWith("dumpsys activity activities | sed -n '/Display #3/,/Display #/p'") })
    }
}
