package br.com.opengw.veiculo.sistema

import br.com.opengw.veiculo.motor.RegistroFalsoDoMotor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** motor-sistema.md §G (ciclo de vida do conjunto). */
class CicloDeVidaTest {

    /** Portas que anotam cada passo, na ordem; [falharEm] lança naquele passo. */
    private class PortasFalsas : PortasDoCiclo<String> {
        val passos: MutableList<String> = Collections.synchronizedList(ArrayList())
        @Volatile var nucleoSobe = true
        val falharEm: MutableSet<String> = Collections.synchronizedSet(HashSet())
        @Volatile var duranteONucleo: (() -> Unit)? = null

        private fun passo(nome: String) {
            passos += nome
            if (nome in falharEm) throw IllegalStateException("$nome falhou")
        }

        override fun partirNucleo(contexto: String): Boolean {
            passo("núcleo:$contexto")
            duranteONucleo?.invoke()
            return nucleoSobe
        }
        override fun partirNucleoSimulado(contexto: String) = passo("núcleo simulado:$contexto")
        override fun encerrarNucleo() = passo("encerrar núcleo")
        override fun ligarGanchoDoCarro() = passo("gancho")
        override fun marcarBootNoNucleo(uptimeMs: Long) = passo("boot:$uptimeMs")
        override fun ligarPainelEVolante(contexto: String) = passo("ligar painel e volante")
        override fun desligarPainelEVolante(contexto: String) = passo("desligar painel e volante")
        override fun iniciarDadosMoveis() = passo("iniciar dados móveis")
        override fun pararDadosMoveis() = passo("parar dados móveis")
        override fun ajustesDoAndroidNaPartida(contexto: String) = passo("acessibilidade")
        override fun automacoesDePartida(real: Boolean) = passo("automações de partida:$real")
        override fun automacoesDepoisDoPronto() = passo("automações depois do pronto")
        override fun encerrarAutomacoes() = passo("encerrar automações")
        override fun prepararConectividade(contexto: String) = passo("conectividade")
        override fun semearHotspot() = passo("semear hotspot")
    }

    private val relogio = AtomicLong(1_000)
    private val portas = PortasFalsas()
    private val registro = RegistroFalsoDoMotor()
    private val ciclo = CicloDeVida(portas, { relogio.addAndGet(10) }, registro)

    @Test
    fun `G2 - ordem da partida`() {
        assertTrue(ciclo.partir("ctx"))
        assertEquals(
            listOf(
                "boot:1010",
                "parar dados móveis",
                "desligar painel e volante",
                "encerrar automações",
                "gancho",
                "núcleo:ctx",
                "ligar painel e volante",
                "acessibilidade",
                "iniciar dados móveis",
                "conectividade",
                "automações de partida:true",
                "semear hotspot",
                "automações depois do pronto",
            ),
            portas.passos,
        )
        assertTrue(ciclo.pronto)
        assertEquals(1010L, ciclo.instanteDoBoot)
        assertTrue(ciclo.inicioDaPartida in 1..ciclo.fimDaPartida)
    }

    @Test
    fun `G2 - pronto so depois das automacoes de partida`() {
        val prontoNasAutomacoes = AtomicInteger(-1)
        val portasComOlho = object : PortasDoCiclo<String> by portas {
            override fun automacoesDePartida(real: Boolean) {
                prontoNasAutomacoes.set(if (cicloComOlho.pronto) 1 else 0)
            }
        }
        cicloComOlho = CicloDeVida(portasComOlho, { relogio.addAndGet(10) }, registro)
        cicloComOlho.partir("ctx")
        assertEquals(0, prontoNasAutomacoes.get())
        assertTrue(cicloComOlho.pronto)
    }

    private lateinit var cicloComOlho: CicloDeVida<String>

    @Test
    fun `G2 5 - nucleo falhou - devolve falso sem ligar painel, teclas nem automacoes`() {
        portas.nucleoSobe = false
        assertFalse(ciclo.partir("ctx"))
        assertFalse(ciclo.pronto)
        val depoisDoNucleo = portas.passos.dropWhile { !it.startsWith("núcleo:") }.drop(1)
        assertTrue(depoisDoNucleo.isEmpty())
    }

    @Test
    fun `G2 - outros passos isolados - uma falha nao derruba a partida`() {
        portas.falharEm += setOf("ligar painel e volante", "acessibilidade", "automações de partida:true", "desligar painel e volante")
        assertTrue(ciclo.partir("ctx"))
        assertTrue(ciclo.pronto)
        assertTrue("automações depois do pronto" in portas.passos)
        assertEquals(4, registro.erros.size)
    }

    @Test
    fun `G2 - re-partida desfaz antes de ligar (volante desregistra antes)`() {
        ciclo.partir("ctx")
        portas.passos.clear()
        ciclo.partir("ctx")
        val desligar = portas.passos.indexOf("desligar painel e volante")
        val ligar = portas.passos.indexOf("ligar painel e volante")
        assertTrue(desligar in 0 until ligar)
        assertTrue(portas.passos.indexOf("encerrar automações") < portas.passos.indexOf("núcleo:ctx"))
        assertFalse("boot só a primeira vez", portas.passos.any { it.startsWith("boot:") })
    }

    @Test
    fun `G5 - pronto nao volta a falso numa re-partida que falha`() {
        ciclo.partir("ctx")
        portas.nucleoSobe = false
        assertFalse(ciclo.partir("ctx"))
        assertTrue(ciclo.pronto)
    }

    @Test
    fun `G2 - uma por vez - a segunda espera a primeira`() {
        val dentro = CountDownLatch(1)
        val solta = CountDownLatch(1)
        portas.duranteONucleo = {
            dentro.countDown()
            solta.await(2, TimeUnit.SECONDS)
        }
        val primeira = Thread { ciclo.partir("a") }
        primeira.start()
        assertTrue(dentro.await(2, TimeUnit.SECONDS))
        portas.duranteONucleo = null
        val segunda = Thread { ciclo.partir("b") }
        segunda.start()
        Thread.sleep(100)
        assertFalse("a segunda não entrou", portas.passos.contains("núcleo:b"))
        solta.countDown()
        primeira.join(2_000)
        segunda.join(2_000)
        assertTrue(portas.passos.indexOf("automações depois do pronto") < portas.passos.indexOf("núcleo:b"))
    }

    @Test
    fun `G3 - partida simulada`() {
        ciclo.partirSimulado("ctx")
        assertEquals(listOf("gancho", "núcleo simulado:ctx", "automações de partida:false"), portas.passos)
        assertTrue(ciclo.pronto)
        assertTrue(ciclo.fimDaPartida >= ciclo.inicioDaPartida)
    }

    @Test
    fun `G4 - encerrar, cada passo isolado`() {
        portas.falharEm += "parar dados móveis"
        ciclo.encerrar("ctx")
        assertEquals(
            listOf("parar dados móveis", "encerrar núcleo", "desligar painel e volante", "encerrar automações"),
            portas.passos,
        )
    }

    @Test
    fun `G6 - marcarBoot so grava a primeira vez e repassa ao nucleo`() {
        ciclo.marcarBoot(500)
        ciclo.marcarBoot(900)
        assertEquals(500L, ciclo.instanteDoBoot)
        assertEquals(listOf("boot:500", "boot:900"), portas.passos)
        ciclo.partir("ctx")
        assertEquals(500L, ciclo.instanteDoBoot)
    }
}
