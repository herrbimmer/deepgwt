package br.com.opengw.telas

import br.com.opengw.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parte 5, 4.1: sequência de comandos da parte 2 com uma central falsa (lista de pilhas que muda
 * conforme os comandos) e relógio falso.
 */
class SequenciasDasTelasTest {

    private val spotify = ConfigDoApp("com.spotify.music", "com.spotify.music.MainActivity", 3, 0, 0, 1920, 720)

    /** Barra persistente ligada (60 dp), como na central. */
    private fun bancada() = Bancada().also { it.prefs[Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR] = true }

    // ── C1 ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C1 - app novo no display 3`() {
        val b = bancada()
        b.rodar { b.telas.abrir(spotify) }
        val p = b.central.pilhaDe("com.spotify.music")!!
        assertEquals(
            listOf(
                "am force-stop com.spotify.music",
                "am start -n com.spotify.music/com.spotify.music.MainActivity --display 3 --windowingMode 5",
                "am stack resize ${p.id} 0 62 1920 658",
            ),
            b.central.acoes(),
        )
        assertEquals(listOf(listOf(0, 62, 1920, 658)), b.painel.vaos)
        assertEquals(listOf(48L, 200L, 300L), b.relogio.esperas)
        assertEquals(listOf("-com.spotify.music"), b.barra.restaurados)

        b.fundo.rodarTudo()
        assertTrue("C8: avisa o painel", b.painel.apps.contains(3 to true))
    }

    @Test
    fun `C1 - app ja no display so redimensiona`() {
        val b = bancada()
        val p = b.central.pilha(3, "com.spotify.music", limites = intArrayOf(0, 0, 100, 100))
        b.rodar { b.telas.abrir(spotify) }
        assertEquals(listOf("am stack resize ${p.id} 0 62 1920 658"), b.central.acoes())
    }

    @Test
    fun `C1 - o proprio app nao e parado nem iniciado`() {
        val b = bancada()
        b.rodar { b.telas.abrir(spotify.copy(pacote = b.sistema.pacoteDoApp, atividade = ".MainActivity")) }
        assertEquals(emptyList<String>(), b.central.acoes())
        assertTrue(b.relogio.esperas.contains(300L))
    }

    @Test
    fun `C1 - display 0 abre em janela livre com a faixa da barra`() {
        val b = bancada()
        b.rodar { b.telas.abrir(spotify.copy(display = 0)) }
        val p = b.central.pilhaDe("com.spotify.music")!!
        assertEquals(
            listOf(
                "am force-stop com.spotify.music",
                "am start -n com.spotify.music/com.spotify.music.MainActivity --display 0 --windowingMode 5",
                "am stack resize ${p.id} 0 0 1920 660",
            ),
            b.central.acoes(),
        )
        assertTrue("sem vão fora do display 3", b.painel.vaos.isEmpty())
    }

    @Test
    fun `C1 - atividade com cifrao e escapada`() {
        val b = bancada()
        b.rodar { b.telas.abrir(spotify.copy(atividade = "a.b.\$Main")) }
        assertTrue(b.central.acoes().contains("am start -n com.spotify.music/a.b.\\\$Main --display 3 --windowingMode 5"))
    }

    // ── C2 ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C2 - ja no display`() {
        val b = bancada()
        val p = b.central.pilha(3, "com.spotify.music")
        b.rodar { b.telas.enviarParaDisplay(spotify) }
        assertEquals(listOf("am stack resize ${p.id} 0 62 1920 658"), b.central.acoes())
    }

    @Test
    fun `C2 - outro display com pilha so dele move sem matar e a volta nao desconta duas vezes`() {
        val b = bancada()
        val p = b.central.pilha(0, "com.spotify.music", limites = intArrayOf(0, 0, 1920, 660))
        b.rodar { b.telas.enviarParaDisplay(spotify) }
        assertEquals(listOf("am display move-stack ${p.id} 3", "am stack resize ${p.id} 0 62 1920 658"), b.central.acoes())
        assertEquals(listOf(48L, 200L), b.relogio.esperas)

        b.zerarRegistros()
        b.rodar { b.telas.abrirNaPrincipal("com.spotify.music", null) }
        val t = b.central.pilhaDe("com.spotify.music")!!.tarefas[0].id
        assertEquals(
            listOf(
                "am display move-stack ${p.id} 0",
                "am stack move-task-to-front $t",
                "am stack set-windowing-mode ${p.id} 1",
                "am stack resize ${p.id} 0 0 1920 660",
            ),
            b.central.acoes(),
        )
    }

    @Test
    fun `C2 - pilha mista abre de novo`() {
        val b = bancada()
        b.central.pilha(0, "com.spotify.music", outras = listOf("com.waze"))
        b.rodar { b.telas.enviarParaDisplay(spotify) }
        val acoes = b.central.acoes()
        assertTrue(acoes.none { it.startsWith("am display move-stack") })
        assertTrue(acoes.contains("am force-stop com.spotify.music"))
    }

    @Test
    fun `C2 - move que falha abre de novo`() {
        val b = bancada()
        val p = b.central.pilha(0, "com.spotify.music")
        b.central.moverFalha = true
        b.rodar { b.telas.enviarParaDisplay(spotify) }
        val acoes = b.central.acoes()
        assertEquals("am display move-stack ${p.id} 3", acoes[0])
        assertEquals("am force-stop com.spotify.music", acoes[1])
    }

    @Test
    fun `C2 - sem tarefa em lugar nenhum abre`() {
        val b = bancada()
        b.rodar { b.telas.enviarParaDisplay(spotify) }
        assertEquals("am force-stop com.spotify.music", b.central.acoes()[0])
    }

    // ── C3 ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C3 - em outro display volta ao 0 com a atividade pedida`() {
        val b = bancada()
        val p = b.central.pilha(3, "com.spotify.music")
        b.rodar { b.telas.abrirNaPrincipal("com.spotify.music", "com.spotify.music.MainActivity") }
        val t = p.tarefas[0].id
        assertEquals(
            listOf(
                "am display move-stack ${p.id} 0",
                "am stack move-task-to-front $t",
                "am stack set-windowing-mode ${p.id} 1",
                "am stack resize ${p.id} 0 0 1920 660",
                "am start -n com.spotify.music/com.spotify.music.MainActivity --display 0 --windowingMode 1 -f 0x14000000",
            ),
            b.central.acoes(),
        )
        assertEquals(listOf("com.spotify.music"), b.launcher3d)
        assertEquals(1, b.sistema.reposicionamentos)
        assertTrue(b.barra.restaurados.contains("+com.spotify.music"))
    }

    @Test
    fun `C3 - sem tarefa usa a intent de abertura e, sem intent, a atividade`() {
        val b = bancada()
        b.sistema.atividades["com.waze"] = "com.waze.FreeMapAppActivity"
        b.rodar { b.telas.abrirNaPrincipal("com.waze", null) }
        assertEquals(listOf("com.waze" to (0x10000000 or 0x04000000 or 0x00020000)), b.sistema.aberturasPelaIntent)
        assertEquals(emptyList<String>(), b.central.acoes())

        b.rodar { b.telas.abrirNaPrincipal("com.spotify.music", "com.spotify.music.MainActivity") }
        assertEquals(
            listOf("am start -n com.spotify.music/com.spotify.music.MainActivity --display 0 --windowingMode 1 -f 0x14000000"),
            b.central.acoes(),
        )

        b.zerarRegistros()
        b.rodar { b.telas.abrirNaPrincipal("x.sem.nada", null) }
        assertEquals(emptyList<String>(), b.central.acoes())
        assertTrue(b.registro.tem("sem intent de abertura nem atividade"))
    }

    // ── C4 ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C4 - traz tudo dos displays 1 e 3`() {
        val b = bancada()
        b.central.pilha(0, "com.beantechs.mediacenter")
        val s = b.central.pilha(3, "com.spotify.music")
        val c = b.central.pilha(1, "com.android.settings", "com.android.settings.Settings")
        val movidos = b.rodar { b.telas.trazerTodosParaPrincipal() }
        assertEquals(listOf("com.spotify.music", "com.android.settings"), movidos)
        assertEquals(
            listOf(
                "am display move-stack ${s.id} 0",
                "am stack resize ${s.id} 0 0 1920 660",
                "am display move-stack ${c.id} 0",
                "am start -n com.android.settings/com.android.settings.Settings --display 0 --windowingMode 1 -f 0x14000000",
                "am stack resize ${c.id} 0 0 1920 660",
            ),
            b.central.acoes(),
        )
        assertEquals(listOf("+com.spotify.music", "+com.android.settings"), b.barra.restaurados)
        assertEquals(1, b.sistema.reposicionamentos)
        assertEquals(emptyList<String>(), b.rodar { b.telas.trazerTodosParaPrincipal() })
    }

    // ── C5 ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C5 - um app por display secundario`() {
        val b = bancada()
        b.sistema.atividades["com.waze"] = "com.waze.FreeMapAppActivity"
        val w = b.central.pilha(3, "com.waze")
        val cp = b.central.pilha(3, "com.ts.carplay.app")
        b.central.pilha(3, "com.android.systemui")
        b.rodar { b.telas.abrir(spotify) }
        val acoes = b.central.acoes()
        assertEquals(
            listOf(
                "am display move-stack ${w.id} 0",
                "am stack resize ${w.id} 0 0 1920 660",
                "am stack remove ${cp.id}",
                "am force-stop com.spotify.music",
            ),
            acoes.take(4),
        )
        assertEquals(listOf("com.waze" to 0x10000000), b.sistema.aberturasPelaIntent)
        assertTrue(b.central.comandos.none { it == "am force-stop com.ts.carplay.app" })
    }

    // ── C6, C7 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `C6 - redimensionar usa os numeros crus`() {
        val b = bancada()
        val p = b.central.pilha(3, "com.spotify.music")
        b.rodar { b.telas.redimensionar(spotify.copy(x = 10, y = 20, largura = 300, altura = 200)) }
        assertEquals(listOf("am stack resize ${p.id} 10 20 310 220"), b.central.acoes())
        assertEquals(1, b.painel.geometrias)
    }

    @Test
    fun `C7 - fechar app comum avisa o display 3`() {
        val b = bancada()
        b.central.pilha(1, "com.spotify.music")
        b.rodar { b.telas.fechar("com.spotify.music") }
        assertEquals(listOf("am force-stop com.spotify.music"), b.central.acoes())
        assertEquals(listOf("avisar display 3"), b.fundo.nomes)
    }

    // ── F7, F8 ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `F7 - corrige a tarja preta pelo resultado`() {
        val b = bancada()
        val p = b.central.pilha(0, "com.spotify.music", limites = intArrayOf(0, 0, 1920, 540))
        b.rodar { b.telas.corrigirTarjaPreta("com.spotify.music") }
        assertEquals(listOf("am stack resize ${p.id} 0 0 1920 660"), b.central.acoes())
        assertEquals(1, b.painel.geometrias)

        b.zerarRegistros()
        b.rodar { b.telas.corrigirTarjaPreta("com.spotify.music") }
        assertEquals("já certo", emptyList<String>(), b.central.acoes())

        b.barra.visivel = false
        p.limites = intArrayOf(0, 0, 1920, 540)
        b.rodar { b.telas.corrigirTarjaPreta("com.spotify.music") }
        assertEquals("barra fora da tela", emptyList<String>(), b.central.acoes())
    }

    @Test
    fun `F8 - reaplica so os apps gravados no display 0`() {
        val b = bancada()
        b.sistema.atividades["com.spotify.music"] = "com.spotify.music.MainActivity"
        val s = b.central.pilha(0, "com.android.settings", "com.android.settings.Settings")
        b.central.pilha(0, "com.spotify.music")
        b.central.pilha(0, "com.android.systemui")
        b.central.pilha(0, b.sistema.pacoteDoApp)
        val escritas = b.prefs.escritas
        b.telas.reaplicarLimitesDoDisplay0()
        assertEquals("volta na hora", emptyList<String>(), b.central.acoes())
        b.fundo.rodarTudo()
        assertEquals(listOf("am stack resize ${s.id} 0 0 1920 660"), b.central.acoes())
        assertEquals(1, b.painel.geometrias)
        assertEquals("gravar = false", escritas, b.prefs.escritas)
    }

    // ── G1.4 ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `G1 - conserto do modo dividido uma vez a cada 10 s`() {
        val b = bancada()
        b.telas.salvar(spotify)
        b.central.pilha(3, "com.spotify.music", modo = "split-screen-secondary")
        b.rodar { b.telas.consertarModoDividido("com.spotify.music") }
        val acoes = b.central.acoes()
        assertEquals("am force-stop com.spotify.music", acoes[0])
        assertEquals("am start -n com.spotify.music/com.spotify.music.MainActivity --display 3 --windowingMode 5", acoes[1])
        assertTrue(acoes[2].startsWith("am stack resize ") && acoes[2].endsWith(" 0 62 1920 658"))

        b.central.pilhaDe("com.spotify.music")!!.modo = "split-screen-secondary"
        b.zerarRegistros()
        b.rodar { b.telas.consertarModoDividido("com.spotify.music") }
        assertEquals(emptyList<String>(), b.central.acoes())
    }

    @Test
    fun `nada lanca com a lista vazia`() {
        val b = bancada()
        b.central.shizuku = false
        b.rodar {
            b.telas.abrir(spotify)
            b.telas.enviarParaDisplay(spotify)
            b.telas.abrirNaPrincipal("com.spotify.music", null)
            b.telas.trazerTodosParaPrincipal()
            b.telas.corrigirTarjaPreta("com.spotify.music")
            b.telas.fechar("com.spotify.music")
        }
        b.fundo.rodarTudo()
        assertEquals(null, b.telas.pacoteNoTopo(0))
        assertEquals(false, b.telas.temAppNoDisplay(3))
    }
}
