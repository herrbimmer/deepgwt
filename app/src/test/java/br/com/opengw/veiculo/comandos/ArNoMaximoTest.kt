package br.com.opengw.veiculo.comandos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** motor-comandos.md §J (ar-condicionado no máximo). */
class ArNoMaximoTest {

    private val chavesDeAntes = ControleDoArNoMaximo.CHAVES_DE_ANTES

    /** Função ligada, limiar 35, cabine a 40 e um "antes" completo no carro. */
    private fun bancada(): BancadaDosComandos {
        val b = BancadaDosComandos()
        b.carro.doCarro["car.basic.inside_temp"] = "40.0"
        b.carro.doCarro["car.basic.outside_temp"] = "30.0"
        val antes = mapOf(
            "car.hvac.power_mode" to "0",
            "car.hvac.ac_enable" to "0",
            "car.hvac.fan_speed" to "3",
            "car.hvac.driver_temperature" to "24.0",
            "car.hvac.pass_temperature" to "22.0",
            "car.hvac.auto_enable" to "1",
            "car.hvac.anion_enable" to "0",
            "car.hvac.aqs_enable" to "1",
            "car.hvac.sync_enable" to "0",
            "car.hvac.setting.comfort_curve" to "1",
            "car.hvac.cycle_mode" to "0",
        )
        b.carro.doCarro.putAll(antes)
        return b
    }

    private fun ligar(b: BancadaDosComandos) {
        b.ar.ligarSeQuente()
        b.agenda.rodarPendentes()
    }

    @Test
    fun `J2 - acima do limiar - le o antes, salva, 7 escritas em ordem, evento`() {
        val b = bancada()
        ligar(b)
        assertTrue(b.ar.ativo)
        assertEquals(
            listOf(
                "car.hvac.power_mode" to "1",
                "car.hvac.auto_enable" to "0",
                "car.hvac.fan_speed" to "7",
                "car.hvac.driver_temperature" to "16.0",
                "car.hvac.pass_temperature" to "16.0",
                "car.hvac.sync_enable" to "1",
                "car.hvac.setting.comfort_curve" to "2",
            ),
            b.carro.escritas,
        )
        assertEquals(listOf(true), b.avisosDoAr)
        assertTrue(chavesDeAntes.all { it in b.carro.leituras })
        assertEquals(true, b.prefs.valores["MAX_AC_ACTIVE_PERSISTED"])
        val salvo = ControleDoArNoMaximo.lerJson(b.prefs.valores["MAX_AC_PREVIOUS_STATE"] as String)
        assertEquals("24.0", salvo["car.hvac.driver_temperature"])
        assertEquals(11, salvo.size)
    }

    @Test
    fun `J2 - o antes salva so os nao nulos`() {
        val b = bancada()
        b.carro.doCarro.remove("car.hvac.aqs_enable")
        ligar(b)
        val salvo = ControleDoArNoMaximo.lerJson(b.prefs.valores["MAX_AC_PREVIOUS_STATE"] as String)
        assertEquals(10, salvo.size)
        assertFalse(salvo.containsKey("car.hvac.aqs_enable"))
    }

    @Test
    fun `J2 J3 - limiar e leitura nula`() {
        val abaixo = bancada().also { it.carro.doCarro["car.basic.inside_temp"] = "34.9" }
        ligar(abaixo)
        assertFalse(abaixo.ar.ativo)
        assertTrue(abaixo.carro.escritas.isEmpty())

        val noLimiar = bancada().also { it.carro.doCarro["car.basic.inside_temp"] = "35.0" }
        ligar(noLimiar)
        assertTrue(noLimiar.ar.ativo)

        val outroLimiar = bancada().also {
            it.prefs["maxAcOnUnlockThreshold"] = 45.0f
        }
        ligar(outroLimiar)
        assertFalse(outroLimiar.ar.ativo)

        val nula = bancada().also { it.carro.doCarro.remove("car.basic.inside_temp") }
        ligar(nula)
        assertFalse(nula.ar.ativo)

        val ilegivel = bancada().also { it.carro.doCarro["car.basic.inside_temp"] = "quente" }
        ligar(ilegivel)
        assertFalse(ilegivel.ar.ativo)
    }

    @Test
    fun `J2 - temperatura invalida tenta de novo a cada 1 s, ate 5 vezes`() {
        val b = bancada()
        b.carro.responder("car.basic.inside_temp", "85.0", "-40.0", "90", "85", "-50")
        b.carro.doCarro["car.basic.inside_temp"] = "40.0"
        b.ar.ligarSeQuente()
        b.agenda.rodarPendentes()
        assertFalse(b.ar.ativo)
        b.agenda.avancar(4_000)
        assertFalse("ainda inválida na 5ª leitura", b.ar.ativo)
        b.agenda.avancar(1_000)
        assertTrue("a 6ª leitura (5ª nova tentativa) vale", b.ar.ativo)
    }

    @Test
    fun `J2 - esgotou as 5 novas tentativas, desiste`() {
        val b = bancada()
        b.carro.doCarro["car.basic.inside_temp"] = "99.0"
        b.ar.ligarSeQuente()
        b.agenda.avancar(60_000)
        assertFalse(b.ar.ativo)
        assertEquals(6, b.carro.leituras.count { it == "car.basic.inside_temp" })
    }

    @Test
    fun `J2 - estado salvo e reaproveitado (o antes verdadeiro)`() {
        val b = bancada()
        b.prefs["MAX_AC_ACTIVE_PERSISTED"] = true
        b.prefs["MAX_AC_PREVIOUS_STATE"] = """{"car.hvac.fan_speed":"2","car.hvac.power_mode":"0"}"""
        ligar(b)
        assertTrue(b.ar.ativo)
        assertTrue("não lê o antes do carro", b.carro.leituras.none { it == "car.hvac.fan_speed" })
        b.carro.escritas.clear()
        b.ar.sair()
        b.agenda.rodarPendentes()
        assertEquals(
            listOf("car.hvac.fan_speed" to "2", "car.hvac.ac_enable" to "1", "car.hvac.power_mode" to "0"),
            b.carro.escritas,
        )
    }

    @Test
    fun `J2 - estado salvo corrompido e apagado e segue lendo do carro`() {
        val b = bancada()
        b.prefs["MAX_AC_ACTIVE_PERSISTED"] = true
        b.prefs["MAX_AC_PREVIOUS_STATE"] = "{quebrado"
        ligar(b)
        assertTrue(b.ar.ativo)
        assertTrue(b.carro.leituras.contains("car.hvac.fan_speed"))
        val salvo = ControleDoArNoMaximo.lerJson(b.prefs.valores["MAX_AC_PREVIOUS_STATE"] as String)
        assertEquals(11, salvo.size)
    }

    @Test
    fun `J2 - tempo-limite sai sozinho`() {
        val b = bancada()
        b.prefs["maxAcTimeout"] = 3
        ligar(b)
        assertTrue(b.ar.ativo)
        b.agenda.avancar(TimeUnit.MINUTES.toMillis(3) - 1)
        assertTrue(b.ar.ativo)
        b.agenda.avancar(1)
        assertFalse(b.ar.ativo)
        assertEquals(listOf(true, false), b.avisosDoAr)
    }

    @Test
    fun `J2 - ja ativo nao liga de novo`() {
        val b = bancada()
        ligar(b)
        b.carro.escritas.clear()
        ligar(b)
        assertTrue(b.carro.escritas.isEmpty())
        assertEquals(listOf(true), b.avisosDoAr)
    }

    /** Vetores de suavização (exatos): alvo 28, antes ventilador 3, motorista 24.0, passageiro 22.0. */
    @Test
    fun `J4 - tabela de suavizacao`() {
        data class Linha(val t: String, val ventilador: String?, val motorista: String?, val passageiro: String?)
        val linhas = listOf(
            Linha("28.5", "4", "20.0", "20.0"),
            Linha("29.0", "5", "20.0", "19.0"),
            Linha("29.5", "6", "18.0", "17.5"),
            Linha("29.99", "7", "16.0", "16.0"),
        )
        for (linha in linhas) {
            val b = bancada()
            ligar(b)
            b.carro.escritas.clear()
            b.carro.doCarro["car.basic.inside_temp"] = linha.t
            b.ar.aoMudarTemperaturaInterna()
            b.agenda.rodarPendentes()
            assertEquals("ventilador em ${linha.t}", listOf(linha.ventilador), b.carro.escritasDe("car.hvac.fan_speed"))
            val motorista = b.carro.escritasDe("car.hvac.driver_temperature").single().toDouble()
            val passageiro = b.carro.escritasDe("car.hvac.pass_temperature").single().toDouble()
            assertEquals("motorista em ${linha.t}", linha.motorista!!.toDouble(), motorista, 0.05)
            assertEquals("passageiro em ${linha.t}", linha.passageiro!!.toDouble(), passageiro, 0.05)
            assertEquals(listOf("1"), b.carro.escritasDe("car.hvac.power_mode"))
            assertEquals(listOf("1"), b.carro.escritasDe("car.hvac.ac_enable"))
            assertTrue(b.ar.ativo)
        }
    }

    @Test
    fun `J4 - formato exato das temperaturas (uma casa, ponto)`() {
        val b = bancada()
        ligar(b)
        b.carro.escritas.clear()
        b.carro.doCarro["car.basic.inside_temp"] = "29.5"
        b.ar.aoMudarTemperaturaInterna()
        b.agenda.rodarPendentes()
        assertEquals(listOf("18.0"), b.carro.escritasDe("car.hvac.driver_temperature"))
        assertEquals(listOf("17.5"), b.carro.escritasDe("car.hvac.pass_temperature"))
    }

    @Test
    fun `J4 - no alvo sai, acima de alvo+2 nada, sem estar ativo nada`() {
        val noAlvo = bancada()
        ligar(noAlvo)
        noAlvo.carro.doCarro["car.basic.inside_temp"] = "28.0"
        noAlvo.ar.aoMudarTemperaturaInterna()
        noAlvo.agenda.rodarPendentes()
        assertFalse(noAlvo.ar.ativo)

        val acima = bancada()
        ligar(acima)
        acima.carro.escritas.clear()
        acima.carro.doCarro["car.basic.inside_temp"] = "30.0"
        acima.ar.aoMudarTemperaturaInterna()
        acima.agenda.rodarPendentes()
        assertTrue(acima.ar.ativo)
        assertTrue(acima.carro.escritas.isEmpty())

        val inativo = bancada()
        inativo.carro.doCarro["car.basic.inside_temp"] = "29.0"
        inativo.ar.aoMudarTemperaturaInterna()
        inativo.agenda.rodarPendentes()
        assertTrue(inativo.carro.escritas.isEmpty())
    }

    @Test
    fun `J4 - recirculacao pela interna contra a externa`() {
        val b = bancada()
        ligar(b)
        b.carro.escritas.clear()
        b.carro.doCarro["car.basic.inside_temp"] = "29.0"
        b.carro.doCarro["car.basic.outside_temp"] = "35.0"
        b.ar.aoMudarTemperaturaInterna()
        b.agenda.rodarPendentes()
        assertEquals(listOf("1"), b.carro.escritasDe("car.hvac.cycle_mode"))

        b.carro.escritas.clear()
        b.carro.doCarro["car.basic.outside_temp"] = "20.0"
        b.ar.aoMudarTemperaturaInterna()
        b.agenda.rodarPendentes()
        assertEquals(listOf("0"), b.carro.escritasDe("car.hvac.cycle_mode"))

        b.carro.escritas.clear()
        b.carro.doCarro.remove("car.basic.outside_temp")
        b.ar.aoMudarTemperaturaInterna()
        b.agenda.rodarPendentes()
        assertTrue(b.carro.escritasDe("car.hvac.cycle_mode").isEmpty())
    }

    @Test
    fun `J4 D-C6 - ventilador anterior com casa decimal falha so naquele passo`() {
        val b = bancada()
        b.carro.doCarro["car.hvac.fan_speed"] = "3.0"
        ligar(b)
        b.carro.escritas.clear()
        b.carro.doCarro["car.basic.inside_temp"] = "29.0"
        b.ar.aoMudarTemperaturaInterna()
        b.agenda.rodarPendentes()
        assertTrue(b.carro.escritasDe("car.hvac.fan_speed").isEmpty())
        assertEquals(1, b.carro.escritasDe("car.hvac.driver_temperature").size)
    }

    @Test
    fun `J4 - antes ausente usa ventilador 3 e temperaturas 22`() {
        val b = BancadaDosComandos()
        b.carro.doCarro["car.basic.inside_temp"] = "40.0"
        ligar(b)
        b.carro.escritas.clear()
        b.carro.doCarro["car.basic.inside_temp"] = "29.0"
        b.ar.aoMudarTemperaturaInterna()
        b.agenda.rodarPendentes()
        assertEquals(listOf("5"), b.carro.escritasDe("car.hvac.fan_speed"))
        assertEquals(listOf("19.0"), b.carro.escritasDe("car.hvac.driver_temperature"))
    }

    @Test
    fun `J5 - ordem - as outras chaves, ac_enable, power_mode - limpa o salvo - evento`() {
        val b = bancada()
        ligar(b)
        b.carro.escritas.clear()
        b.ar.sair()
        b.agenda.rodarPendentes()
        assertFalse(b.ar.ativo)
        assertEquals(
            listOf(
                "car.hvac.fan_speed" to "3",
                "car.hvac.driver_temperature" to "24.0",
                "car.hvac.pass_temperature" to "22.0",
                "car.hvac.auto_enable" to "1",
                "car.hvac.anion_enable" to "0",
                "car.hvac.aqs_enable" to "1",
                "car.hvac.sync_enable" to "0",
                "car.hvac.setting.comfort_curve" to "1",
                "car.hvac.cycle_mode" to "0",
                "car.hvac.ac_enable" to "0",
                "car.hvac.power_mode" to "0",
            ),
            b.carro.escritas,
        )
        assertNull(b.prefs.valores["MAX_AC_ACTIVE_PERSISTED"])
        assertNull(b.prefs.valores["MAX_AC_PREVIOUS_STATE"])
        assertEquals(listOf(true, false), b.avisosDoAr)
    }

    @Test
    fun `J5 - sem anterior de ac_enable e power_mode, padroes 1 - nao ativo nada`() {
        val b = BancadaDosComandos()
        b.carro.doCarro["car.basic.inside_temp"] = "40.0"
        ligar(b)
        b.carro.escritas.clear()
        b.ar.sair()
        b.agenda.rodarPendentes()
        assertEquals(listOf("car.hvac.ac_enable" to "1", "car.hvac.power_mode" to "1"), b.carro.escritas)

        b.carro.escritas.clear()
        b.ar.sair()
        b.agenda.rodarPendentes()
        assertTrue(b.carro.escritas.isEmpty())
    }

    @Test
    fun `J6 - chamada de outra thread volta na hora e o trabalho fica para a linha`() {
        val b = bancada()
        ligar(b)
        b.carro.escritas.clear()
        val outra = Thread { b.ar.sair() }
        outra.start()
        outra.join(2_000)
        assertFalse(outra.isAlive)
        assertTrue("nada antes de a linha rodar", b.carro.escritas.isEmpty())
        assertTrue(b.ar.ativo)
        b.agenda.rodarPendentes()
        assertFalse(b.ar.ativo)
    }

    @Test
    fun `re-partida cancela o tempo-limite`() {
        val b = bancada()
        b.prefs["maxAcTimeout"] = 1
        ligar(b)
        b.ar.cancelarAgendados()
        b.agenda.avancar(TimeUnit.MINUTES.toMillis(2))
        assertTrue("o tempo-limite se perdeu", b.ar.ativo)
    }
}
