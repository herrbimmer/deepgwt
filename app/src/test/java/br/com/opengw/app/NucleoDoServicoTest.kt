package br.com.opengw.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `telas-e-servico-servico.md` §8 (JVM), casos C–F. */
class NucleoDoServicoTest {

    private val chamadas = mutableListOf<String>()

    /** Linha de fundo manual: as tarefas só rodam em [rodarFila]. */
    private inner class LinhaFalsa : LinhaDeFundo {
        val fila = ArrayDeque<() -> Unit>()
        var encerrada = false
        override fun postar(tarefa: () -> Unit) {
            if (!encerrada) fila.addLast(tarefa)
        }
        override fun esvaziar() {
            chamadas += "linha.esvaziar"
            fila.clear()
        }
        override fun encerrar() {
            chamadas += "linha.encerrar"
            encerrada = true
            fila.clear()
        }
        fun rodarFila() {
            while (fila.isNotEmpty()) {
                chamadas += "<fundo>"
                fila.removeFirst()()
            }
        }
    }

    private inner class PortasFalsas : PortasDoServico {
        override var simulador = false
        var resetPendente = ""
        val diarios = mutableListOf<Pair<String, String>>()
        override fun diario(evento: String, mensagem: String) {
            diarios += evento to mensagem
        }
        override fun info(mensagem: String) {}
        override fun erro(mensagem: String, causa: Throwable?) {
            chamadas += "erro"
        }
        var falharPrimeiroPlano = false
        override fun entrarEmPrimeiroPlano(simulado: Boolean) {
            chamadas += "primeiroPlano($simulado)"
            if (falharPrimeiroPlano) throw IllegalStateException("sem primeiro plano")
        }
        override fun sairDoPrimeiroPlano() {
            chamadas += "sairDoPrimeiroPlano"
        }
        override fun pararOServico() {
            chamadas += "stopSelf"
        }
        override fun agendarReinicio() {
            chamadas += "agendarReinicio"
        }
        override fun marcarInicioNaLinhaDoTempo() {
            chamadas += "diag.onStartCommand"
        }
        override fun marcarReinicioNaLinhaDoTempo(motivo: String) {
            chamadas += "diag.onRestart($motivo)"
        }
        var registrosDoAviso = 0
        override fun registrarAvisoDaCentral() {
            registrosDoAviso++
            chamadas += "registrarAviso"
        }
        override fun lerResetPendente() = resetPendente
        override fun apagarResetPendente() {
            chamadas += "apagarResetPendente"
            resetPendente = ""
        }
    }

    private inner class GanchosFalsos : GanchosDoServico {
        var partida = true
        var lancarEm: String? = null
        var ouvinte: PartidaDoShizuku.Ouvinte? = null
        private fun marca(nome: String) {
            chamadas += nome
            if (lancarEm == nome) throw RuntimeException("falha em $nome")
        }
        override fun aoIniciar() = marca("aoIniciar")
        override fun aoIniciarDeNovo() = marca("aoIniciarDeNovo")
        override fun comShizuku() = marca("comShizuku")
        override fun extrasComShizuku() = marca("extras.comShizuku")
        override fun antesDaPartida() = marca("antesDaPartida")
        override fun partirMotor(): Boolean {
            marca("partir")
            return partida
        }
        override fun depoisDaPartida() = marca("depoisDaPartida")
        override fun extrasDepoisDaPartida() = marca("extras.depoisDaPartida")
        override fun partirSimulado() = marca("partirSimulado")
        override fun depoisDaPartidaSimulada() = marca("depoisDaPartidaSimulada")
        override fun aoDestruir() = marca("aoDestruir")
        override fun encerrarMotor() = marca("encerrarMotor")
        override fun iniciarShizuku(ouvinte: PartidaDoShizuku.Ouvinte) {
            marca("shizuku.iniciar")
            this.ouvinte = ouvinte
        }
        override fun conferirShizuku() = marca("shizuku.conferir")
        override fun encerrarShizuku() = marca("shizuku.encerrar")
    }

    private lateinit var estado: EstadoDoProcesso
    private lateinit var portas: PortasFalsas
    private lateinit var ganchos: GanchosFalsos
    private lateinit var linha: LinhaFalsa
    private lateinit var nucleo: NucleoDoServico

    @Before
    fun preparar() {
        estado = EstadoDoProcesso()
        novaVida()
    }

    private fun novaVida() {
        portas = PortasFalsas()
        ganchos = GanchosFalsos()
        linha = LinhaFalsa()
        nucleo = NucleoDoServico(estado, portas, ganchos, linha)
        nucleo.aoCriar()
    }

    private fun primeiraPartida(): NucleoDoServico.Resposta {
        val r = nucleo.aoPedirInicio(0, 1)
        return r
    }

    @Test
    fun `primeira partida segue a ordem de C5`() {
        assertEquals(NucleoDoServico.Resposta.CONTINUAR, primeiraPartida())
        assertEquals(
            listOf("linha.esvaziar", "primeiroPlano(false)", "aoIniciar", "diag.onStartCommand", "shizuku.iniciar"),
            chamadas,
        )
        assertEquals(
            listOf("foreground_service_on_create" to "created=true", "foreground_service_started" to "flags=0 startId=1"),
            portas.diarios,
        )
        assertTrue(estado.dePe.get())
    }

    @Test
    fun `pedido repetido chama aoIniciarDeNovo e conferir, nunca aoIniciar`() {
        primeiraPartida()
        chamadas.clear()
        assertEquals(NucleoDoServico.Resposta.CONTINUAR, nucleo.aoPedirInicio(1, 2))
        assertEquals(listOf("aoIniciarDeNovo", "shizuku.conferir"), chamadas)
        assertEquals("foreground_service_already_running" to "flags=1 startId=2", portas.diarios.last())
    }

    @Test
    fun `aceito roda a sequencia de E na linha de fundo, uma vez`() {
        primeiraPartida()
        chamadas.clear()
        ganchos.ouvinte!!.aceito()
        assertTrue("nada roda fora da linha de fundo", chamadas.isEmpty())
        ganchos.ouvinte!!.aceito()
        linha.rodarFila()
        assertEquals(
            listOf(
                "<fundo>", "comShizuku", "extras.comShizuku", "antesDaPartida", "partir",
                "depoisDaPartida", "extras.depoisDaPartida", "registrarAviso",
            ),
            chamadas,
        )
    }

    @Test
    fun `partida que falha reinicia e nao segue depois de E4`() {
        ganchos.partida = false
        primeiraPartida()
        chamadas.clear()
        ganchos.ouvinte!!.aceito()
        linha.rodarFila()
        assertEquals(
            listOf(
                "<fundo>", "comShizuku", "extras.comShizuku", "antesDaPartida", "partir", "erro",
                "diag.onRestart(partida_do_motor_falhou)", "shizuku.encerrar", "agendarReinicio", "stopSelf",
            ),
            chamadas,
        )
        assertFalse(estado.dePe.get())
    }

    @Test
    fun `partida que lanca conta como falha`() {
        ganchos.lancarEm = "partir"
        primeiraPartida()
        ganchos.ouvinte!!.aceito()
        linha.rodarFila()
        assertTrue(chamadas.contains("agendarReinicio"))
        assertFalse(chamadas.contains("depoisDaPartida"))
    }

    @Test
    fun `pedirReinicio reinicia e desistir para sem agendar`() {
        primeiraPartida()
        chamadas.clear()
        ganchos.ouvinte!!.pedirReinicio("binder_morreu")
        assertEquals(
            listOf("diag.onRestart(binder_morreu)", "shizuku.encerrar", "agendarReinicio", "stopSelf"),
            chamadas,
        )
        // Um segundo pedido da mesma vida (já parada) é ignorado.
        chamadas.clear()
        ganchos.ouvinte!!.pedirReinicio("de novo")
        assertTrue(chamadas.isEmpty())

        novaVida()
        primeiraPartida()
        chamadas.clear()
        ganchos.ouvinte!!.desistir("sem root")
        assertEquals(listOf("erro", "sairDoPrimeiroPlano", "stopSelf"), chamadas)
        assertEquals("foreground_service_gave_up" to "sem root", portas.diarios.last())
        assertFalse(estado.dePe.get())
    }

    @Test
    fun `aceito com o servico parado e ignorado`() {
        primeiraPartida()
        ganchos.ouvinte!!.pedirReinicio("x")
        chamadas.clear()
        ganchos.ouvinte!!.aceito()
        linha.rodarFila()
        assertTrue(chamadas.isEmpty())
    }

    @Test
    fun `aviso da central com servico de pe reinicia e parado pede o servico`() {
        primeiraPartida()
        chamadas.clear()
        var pedidos = 0
        NucleoDoServico.tratarAvisoDaCentral(estado) { pedidos++ }
        assertEquals(0, pedidos)
        assertEquals("init_completed_restart", portas.diarios.last().first)
        assertEquals(
            listOf("diag.onRestart(init_completed)", "shizuku.encerrar", "agendarReinicio", "stopSelf"),
            chamadas,
        )
        NucleoDoServico.tratarAvisoDaCentral(estado) { pedidos++ }
        assertEquals(1, pedidos)
    }

    @Test
    fun `avisos da central em menos de um minuto - so o primeiro vale`() {
        val e = EstadoDoProcesso()
        assertTrue(e.aceitaAviso(1_000L))
        assertFalse(e.aceitaAviso(2_000L))
        assertFalse(e.aceitaAviso(60_999L))
        assertTrue(e.aceitaAviso(61_000L))
    }

    @Test
    fun `aviso registrado so uma vez em N partidas no mesmo processo`() {
        var total = 0
        repeat(3) {
            novaVida()
            primeiraPartida()
            ganchos.ouvinte!!.aceito()
            linha.rodarFila()
            total += portas.registrosDoAviso
            nucleo.aoDestruir()
        }
        assertEquals(1, total)
    }

    @Test
    fun `gancho que lanca nao impede o seguinte`() {
        ganchos.lancarEm = "aoIniciar"
        assertEquals(NucleoDoServico.Resposta.CONTINUAR, primeiraPartida())
        assertEquals(
            listOf("linha.esvaziar", "primeiroPlano(false)", "aoIniciar", "erro", "diag.onStartCommand", "shizuku.iniciar"),
            chamadas,
        )
        chamadas.clear()
        ganchos.lancarEm = "comShizuku"
        ganchos.ouvinte!!.aceito()
        linha.rodarFila()
        assertTrue(chamadas.containsAll(listOf("extras.comShizuku", "partir", "depoisDaPartida")))
    }

    @Test
    fun `falha no primeiro plano para o servico sem continuar (C6)`() {
        portas.falharPrimeiroPlano = true
        assertEquals(NucleoDoServico.Resposta.NAO_CONTINUAR, primeiraPartida())
        assertFalse(estado.dePe.get())
        assertTrue(chamadas.contains("stopSelf"))
        assertFalse(chamadas.contains("shizuku.iniciar"))
    }

    @Test
    fun `reset pendente apaga so a chave (C9)`() {
        portas.resetPendente = "Alpha 9.7"
        primeiraPartida()
        assertTrue(chamadas.contains("apagarResetPendente"))
        assertEquals("", portas.resetPendente)
    }

    @Test
    fun `sem reset pendente nada e apagado`() {
        primeiraPartida()
        assertFalse(chamadas.contains("apagarResetPendente"))
    }

    @Test
    fun `destruir chama o gancho antes do motor e solta o estado (F1)`() {
        primeiraPartida()
        chamadas.clear()
        nucleo.aoDestruir()
        assertEquals(listOf("aoDestruir", "encerrarMotor", "shizuku.encerrar", "linha.encerrar"), chamadas)
        assertFalse(estado.dePe.get())
        assertNull(estado.nucleoAtual)
    }

    @Test
    fun `simulador parte simulado sem Shizuku e ignora pedido repetido`() {
        portas.simulador = true
        assertEquals(NucleoDoServico.Resposta.CONTINUAR, primeiraPartida())
        assertEquals(listOf("primeiroPlano(true)", "partirSimulado", "depoisDaPartidaSimulada"), chamadas)
        chamadas.clear()
        assertEquals(NucleoDoServico.Resposta.CONTINUAR, nucleo.aoPedirInicio(0, 2))
        assertTrue(chamadas.isEmpty())
        nucleo.aoDestruir()
        assertFalse(chamadas.contains("shizuku.encerrar"))
    }
}
