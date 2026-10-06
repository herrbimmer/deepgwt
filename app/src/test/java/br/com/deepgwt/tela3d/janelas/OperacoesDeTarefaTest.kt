package br.com.deepgwt.tela3d.janelas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Combinações de passos sobre tarefas, guarda de repetição (D5), reserva do `am stack` e leitor de processo. */
class OperacoesDeTarefaTest {

    private class PrimitivasFalsas : PrimitivasDeTarefa {
        val log = ArrayList<String>()
        val tarefas = HashMap<String, Int>()
        var levantarOk = true
        var topoOk = true
        var redimensionarOk = true

        override fun acharTarefa(pacote: String): Int {
            log += "achar:$pacote"
            return tarefas[pacote] ?: -1
        }

        override fun redimensionar(tarefa: Int, limites: Retangulo): Boolean {
            log += "redimensionar:$tarefa:$limites"
            return redimensionarOk
        }

        override fun levantar(tarefa: Int): Boolean {
            log += "levantar:$tarefa"
            return levantarOk
        }

        override fun marcarNoTopo(tarefa: Int): Boolean {
            log += "topo:$tarefa"
            return topoOk
        }

        override fun remover(tarefa: Int) {
            log += "remover:$tarefa"
            tarefas.entries.removeAll { it.value == tarefa }
        }
    }

    private val limites = Retangulo(1, 2, 3, 4)

    @Test fun removerTodasRepeteAteNaoAcharMaisDeQuatro() {
        val p = PrimitivasFalsas()
        p.tarefas["a"] = 7
        OperacoesDeTarefa(p).removerTodasDoPacote("a")
        assertEquals(listOf("achar:a", "remover:7", "achar:a"), p.log)
    }

    @Test fun removerTodasPara4RepeticoesSeATarefaTeimaEmFicar() {
        val p = object : PrimitivasDeTarefa by PrimitivasFalsas() {
            var removidas = 0
            override fun acharTarefa(pacote: String) = 9
            override fun remover(tarefa: Int) {
                removidas++
            }
        }
        OperacoesDeTarefa(p).removerTodasDoPacote("a")
        assertEquals(4, p.removidas)
    }

    @Test fun fecharUsaONumeroGuardadoSeValido() {
        val p = PrimitivasFalsas()
        p.tarefas["a"] = 7
        OperacoesDeTarefa(p).fechar("a", 3)
        assertEquals(listOf("remover:3"), p.log)
        p.log.clear()
        OperacoesDeTarefa(p).fechar("a", -1)
        assertEquals(listOf("achar:a", "remover:7"), p.log)
    }

    @Test fun fecharSemTarefaNaoFazNada() {
        val p = PrimitivasFalsas()
        OperacoesDeTarefa(p).fechar("a", -1)
        OperacoesDeTarefa(p).fechar("", 4)
        assertEquals(listOf("achar:a"), p.log)
    }

    @Test fun fixarRedimensionaEMarcaNoTopo() {
        val p = PrimitivasFalsas()
        p.tarefas["a"] = 7
        assertEquals(7, OperacoesDeTarefa(p).fixar("a", -1, limites))
        assertEquals(listOf("achar:a", "redimensionar:7:$limites", "topo:7"), p.log)
    }

    @Test fun fixarComNumeroGuardadoNaoProcura() {
        val p = PrimitivasFalsas()
        assertEquals(5, OperacoesDeTarefa(p).fixar("a", 5, limites))
        assertEquals(listOf("redimensionar:5:$limites", "topo:5"), p.log)
    }

    @Test fun fixarSemTarefaDevolveMenosUm() {
        assertEquals(-1, OperacoesDeTarefa(PrimitivasFalsas()).fixar("a", -1, limites))
    }

    @Test fun levantarDevolveONumeroEOResultado() {
        val p = PrimitivasFalsas()
        p.tarefas["a"] = 7
        assertEquals(ResultadoDoLevantar(7, true), OperacoesDeTarefa(p).levantar("a", -1))
        p.levantarOk = false
        assertEquals(ResultadoDoLevantar(7, false), OperacoesDeTarefa(p).levantar("a", -1))
        assertEquals(ResultadoDoLevantar(-1, false), OperacoesDeTarefa(p).levantar("zzz", -1))
    }

    @Test fun fixarNoTopoLevantaSeMarcarFalhou() {
        val p = PrimitivasFalsas()
        p.tarefas["a"] = 7
        p.topoOk = false
        assertEquals(7, OperacoesDeTarefa(p).fixarNoTopo("a", -1))
        assertEquals(listOf("achar:a", "topo:7", "levantar:7"), p.log)
        p.log.clear()
        p.topoOk = true
        OperacoesDeTarefa(p).fixarNoTopo("a", -1)
        assertEquals(listOf("achar:a", "topo:7"), p.log)
    }

    @Test fun redimensionarDoPacote() {
        val p = PrimitivasFalsas()
        p.tarefas["a"] = 7
        assertEquals(7, OperacoesDeTarefa(p).redimensionarDoPacote("a", -1, limites))
        assertEquals(-1, OperacoesDeTarefa(p).redimensionarDoPacote("b", -1, limites))
    }

    @Test fun levantarPorPacoteProcuraELevanta() {
        val p = PrimitivasFalsas()
        p.tarefas["com.ts.carplay.app"] = 12
        assertEquals(ResultadoDoLevantar(12, true), OperacoesDeTarefa(p).levantarPorPacote("com.ts.carplay.app"))
        assertEquals(ResultadoDoLevantar(-1, false), OperacoesDeTarefa(p).levantarPorPacote("outro"))
    }

    // --- D5: a guarda só conta o que o sistema aceitou ---

    @Test fun guardaIgnoraRepeticaoBemSucedidaEm350ms() {
        val t = TempoFalso()
        val g = GuardaDeLevantar(t)
        assertFalse(g.repetida(5))
        g.registrarSucesso(5)
        assertTrue(g.repetida(5))
        assertFalse(g.repetida(6))
        t.avancar(349)
        assertTrue(g.repetida(5))
        t.avancar(1)
        assertFalse(g.repetida(5))
    }

    @Test fun guardaEsqueceAoDesligarOChromePorCima() {
        val t = TempoFalso()
        val g = GuardaDeLevantar(t)
        g.registrarSucesso(5)
        g.esquecer()
        assertFalse(g.repetida(5))
    }

    @Test fun naoSuportadoNaoVoltaAVerdadeiro() {
        val s = SuporteAoNoTopo()
        assertTrue(s.suportado)
        s.marcarNaoSuportado()
        assertFalse(s.suportado)
    }

    // --- reserva do shell (D4) ---

    private val listaDeExemplo = """
Stack id=0 bounds=[0,0][1920,720] displayId=0 userId=0
 configuration={1.0 winConfig={ mWindowingMode=fullscreen mActivityType=home} s.1}
  taskId=4: com.beantechs.mediacenter/com.beantechs.mediacenter.MainActivity bounds=[0,0][1920,720] userId=0 visible=true
Stack id=7 bounds=[128,52][1037,496] displayId=0 userId=0
 configuration={1.0 winConfig={ mWindowingMode=freeform mActivityType=standard} s.1}
  taskId=31: com.waze/com.waze.Main bounds=[128,52][1037,496] userId=0 visible=true
Stack id=11 bounds=[0,0][1920,720] displayId=3 userId=0
  taskId=40: com.waze/com.waze.Outra bounds=[0,0][1920,720] userId=0 visible=true
""".trimIndent()

    private class ShellFalso(var lista: String) {
        val comandos = ArrayList<String>()
        var respostaDoLevantar = ""

        fun rodar(argv: Array<String>): String {
            comandos += argv.joinToString(" ")
            return when {
                argv.size >= 3 && argv[2] == "list" -> lista
                argv.size >= 3 && argv[2] == "move-task-to-front" -> respostaDoLevantar
                else -> ""
            }
        }
    }

    @Test fun pilhasAchaATarefaNoDisplayPrincipal() {
        val sh = ShellFalso(listaDeExemplo)
        val p = PilhasDoSistema(sh::rodar, TempoFalso())
        assertEquals(31, p.acharTarefa("com.waze"))
        assertEquals(-1, p.acharTarefa("com.nao.existe"))
        assertEquals(-1, p.acharTarefa(""))
    }

    @Test fun pilhasRedimensionaAPilhaDaTarefa() {
        val sh = ShellFalso(listaDeExemplo)
        val p = PilhasDoSistema(sh::rodar, TempoFalso())
        assertTrue(p.redimensionar(31, Retangulo(10, 20, 30, 40)))
        assertTrue(sh.comandos.contains("am stack resize 7 10 20 30 40"))
        assertFalse(p.redimensionar(99, Retangulo(0, 0, 1, 1)))
    }

    @Test fun pilhasLevantaPeloComandoDoAndroid() {
        val sh = ShellFalso(listaDeExemplo)
        val p = PilhasDoSistema(sh::rodar, TempoFalso())
        assertTrue(p.levantar(31))
        assertTrue(sh.comandos.contains("am stack move-task-to-front 31"))
    }

    @Test fun pilhasLevantaPelaReservaSeOComandoNaoExiste() {
        val sh = ShellFalso(listaDeExemplo)
        sh.respostaDoLevantar = "Error: Unknown command: move-task-to-front"
        val p = PilhasDoSistema(sh::rodar, TempoFalso())
        assertTrue(p.levantar(31))
        assertTrue(sh.comandos.contains("am stack move-task 31 7 true"))
    }

    @Test fun pilhasSemShizukuNaoFingeSucesso() {
        val sh = ShellFalso("")
        val p = PilhasDoSistema(sh::rodar, TempoFalso())
        assertFalse(p.levantar(31))
        assertFalse(p.redimensionar(31, Retangulo(0, 0, 1, 1)))
        assertEquals(-1, p.acharTarefa("com.waze"))
    }

    @Test fun pilhasGuardaAListaPor250ms() {
        val t = TempoFalso()
        val sh = ShellFalso(listaDeExemplo)
        val p = PilhasDoSistema(sh::rodar, t)
        p.acharTarefa("com.waze")
        p.acharTarefa("com.waze")
        assertEquals(1, sh.comandos.count { it == "am stack list" })
        t.avancar(250)
        p.acharTarefa("com.waze")
        assertEquals(2, sh.comandos.count { it == "am stack list" })
    }

    // --- processo filho com prazo real ---

    @Test fun leitorDeProcessoMataOQueNaoTermina() {
        if (!java.io.File("/bin/sleep").exists()) return
        val inicio = System.nanoTime()
        val saida = LeitorDeProcesso.ler(listOf("/bin/sleep", "5"), 300, 1000)
        val gastoMs = (System.nanoTime() - inicio) / 1_000_000
        assertEquals("", saida)
        assertTrue("passou do prazo: $gastoMs ms", gastoMs < 2500)
    }

    @Test fun leitorDeProcessoLeASaida() {
        if (!java.io.File("/bin/echo").exists()) return
        assertEquals("ola", LeitorDeProcesso.ler(listOf("/bin/echo", "ola"), 2000, 1000)?.trim())
    }

    @Test fun leitorDeProcessoRespeitaOLimiteDeCaracteres() {
        if (!java.io.File("/bin/echo").exists()) return
        val saida = LeitorDeProcesso.ler(listOf("/bin/echo", "abcdefghij"), 2000, 4)
        assertEquals("abcd", saida)
    }

    @Test fun leitorDeProcessoQueNaoExisteDevolveNulo() {
        assertEquals(null, LeitorDeProcesso.ler(listOf("/nao/existe/mesmo"), 500, 100))
    }
}
