package br.com.opengw.veiculo.motor

import br.com.opengw.veiculo.Sinais
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seção B da especificação `motor-nucleo.md`, e as listas fixas de chaves. */
class SimuladorTest {

    private val b = Bancada()

    // ── B.1/B.2 partida simulada ──

    @Test
    fun `partida simulada fica pronta sem falar com a central e liga a fonte uma vez`() {
        val rodou = mutableListOf<String>()
        b.nucleo.quandoPronto { rodou += "tarefa" }

        b.nucleo.partirSimulado()
        b.nucleo.partirSimulado()
        b.agenda.rodarPendentes()

        assertTrue(b.nucleo.pronto)
        assertFalse(b.nucleo.controleVivo)
        assertEquals(listOf("tarefa"), rodou)
        assertEquals(1, b.fontesLigadas.get())
        assertTrue(b.central.abertos.isEmpty())
        assertEquals(0, b.central.pingsDoShizuku.get())
        assertTrue(b.nucleo.instanteDoBoot > 0)
        assertTrue(b.nucleo.fimDaPartida > 0)
    }

    // ── B.6/B.7 e Decisões 10 ──

    @Test
    fun `no simulador leituras usam o cache simulado`() {
        b.catalogo = listOf("car.a", "car.b")
        b.nucleo.partirSimulado()
        b.nucleo.injetar("car.a", "1")

        assertEquals("1", b.nucleo.ler("car.a"))
        assertNull(b.nucleo.ler("car.b"))
        assertEquals("1", b.nucleo.lerDoCarro("car.a"))
        assertNull(b.nucleo.lerDoCarro("car.b"))
        assertEquals(mapOf("car.b" to "", "car.a" to "1"), b.nucleo.lerDoCarro(arrayOf("car.b", "car.a")))
        assertEquals(listOf("car.a", "car.b"), b.nucleo.lerTudoDoCarro().keys.toList())
        assertEquals("1", b.nucleo.lerTudoDoCarro()["car.a"])
        assertTrue(b.registro.avisos.none { "canal morto" in it })
    }

    @Test
    fun `no simulador reenviarTudo republica o cache acompanhado sem gancho`() {
        b.nucleo.partirSimulado()
        b.nucleo.injetar("car.basic.vehicle_speed", "50.0")
        b.nucleo.injetar("car.fora.do.conjunto", "x")
        b.nucleo.injetar("car.basic.engine_speed", "")
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.ganchoDoCarro = gancho
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.reenviarTudo()

        assertEquals(listOf("car.basic.vehicle_speed" to "50.0"), ouvinte.recebidos.toList())
        assertTrue(gancho.recebidos.isEmpty())
    }

    @Test
    fun `no simulador acompanhar nao chama nada e recarregar nao apaga o cache`() {
        b.nucleo.partirSimulado()
        b.nucleo.injetar("car.hvac.fan_speed", "3")

        b.nucleo.acompanhar(listOf("car.nova"))
        b.nucleo.recarregarChavesDoUsuario()

        assertTrue(b.central.abertos.isEmpty())
        assertEquals("3", b.nucleo.espiar("car.hvac.fan_speed"))
    }

    @Test
    fun `injetar entra como evento do carro com gancho depois de pronto`() {
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.ganchoDoCarro = gancho
        b.nucleo.injetar("car.antes", "0")
        b.nucleo.partirSimulado()

        b.nucleo.injetar("car.k", "1")

        assertEquals(listOf("car.k" to "1"), gancho.recebidos.toList())
        assertEquals(6, b.difusor.envios.size)
    }

    // ── B.3/B.4 fonte simulada ──

    @Test
    fun `fonte injeta os valores iniciais na hora e passos a cada 100 ms`() {
        val eventos = mutableListOf<String>()
        val agenda = AgendaFalsa(eventos)
        val injetados = mutableListOf<Pair<String, String>>()
        val fonte = FonteSimulada(agenda) { chave, valor -> injetados += chave to valor }

        val desligar = fonte.ligar()

        val iniciais = injetados.toMap()
        for (chave in listOf(
            "car.hvac.fan_speed", "car.hvac.driver_temperature", "car.hvac.power_mode",
            "car.hvac.pass_temperature", "sys.settings.audio.media_volume", "car.basic.inside_temp",
            "car.basic.outside_temp", "car.basic.engine_state", "car.ev_setting.power_model_config",
            "car.drive_setting.drive_mode", "car.drive_setting.steering_wheel_assist_mode",
            "car.drive_setting.esp_enable", "car.ev_setting.energy_recovery_level",
        )) {
            assertTrue("faltou $chave", chave in iniciais)
        }
        assertEquals("22.0", iniciais["car.hvac.driver_temperature"])
        assertEquals("13", iniciais["car.basic.engine_state"])

        injetados.clear()
        agenda.avancar(99)
        assertTrue(injetados.isEmpty())
        agenda.avancar(1)
        assertEquals(7, injetados.size)

        desligar.cancelar()
        agenda.avancar(1_000)
        assertEquals(7, injetados.size)
    }

    @Test
    fun `percurso sobe a uns 150, desce, para e repete com valores no formato do carro`() {
        val percurso = PercursoSimulado()
        val velocidades = mutableListOf<Double>()
        val odometros = mutableListOf<Int>()
        repeat(PercursoSimulado.CICLO * 2) {
            val passo = percurso.avancar().toMap()
            val velocidade = passo.getValue("car.basic.vehicle_speed")
            assertTrue("formato: $velocidade", Regex("""\d+\.\d""").matches(velocidade))
            velocidades += velocidade.toDouble()
            odometros += passo.getValue("car.basic.accumulated_odometer").toInt()
            val rotacao = passo.getValue("car.basic.engine_speed").toInt()
            val marcha = passo.getValue("car.basic.gear_status")
            if (velocidade.toDouble() == 0.0) {
                assertEquals(0, rotacao)
                assertEquals("3", marcha)
            } else {
                assertTrue(rotacao > 0)
                assertEquals("2", marcha)
            }
            for (chave in listOf("car.basic.remain_fuel_percentage", "car.ev_info.battery_charge_percentage")) {
                assertTrue(passo.getValue(chave).toInt() in 0..100)
            }
            assertTrue(passo.getValue("car.basic.remain_odometer").toInt() in 0..700)
        }

        assertTrue(velocidades.max() in 145.0..155.0)
        assertTrue(velocidades.count { it == 0.0 } >= 2 * 30) // uns segundos parado por ciclo
        assertEquals(velocidades.take(PercursoSimulado.CICLO), velocidades.drop(PercursoSimulado.CICLO))
        assertEquals(odometros.sorted(), odometros)
        assertTrue(odometros.last() > odometros.first())
    }

    @Test
    fun `combustivel e bateria descem devagar e sobem rapido`() {
        val percurso = PercursoSimulado()
        val bateria = mutableListOf<Int>()
        repeat(PercursoSimulado.CICLO * 10) {
            bateria += percurso.avancar().toMap().getValue("car.ev_info.battery_charge_percentage").toInt()
        }
        val maiorQueda = bateria.zipWithNext { a, b -> a - b }.max()
        val maiorSubida = bateria.zipWithNext { a, b -> b - a }.max()

        assertTrue(maiorQueda <= 1)
        assertTrue(maiorSubida >= 1)
        assertTrue(bateria.min() < 80)
    }

    // ── Listas fixas ──

    @Test
    fun `chaves padrao sao as 117 do apendice B, todas no catalogo`() {
        val padrao = ChavesDoMotor.ACOMPANHADAS_SEMPRE
        val catalogo = Sinais.TODOS.map { it.chave }.toSet()

        assertEquals(117, padrao.size)
        assertEquals(117, padrao.toSet().size)
        assertTrue(padrao.filterNot { it in catalogo }.toString(), padrao.all { it in catalogo })
    }

    @Test
    fun `chaves do ar sao as 11 do apendice C`() {
        assertEquals(11, ChavesDoMotor.DO_AR.size)
        assertTrue(ChavesDoMotor.DO_AR.all { it.startsWith("car.hvac.") && it in ChavesDoMotor.ACOMPANHADAS_SEMPRE })
        assertFalse("car.hvac.panel_display_notify" in ChavesDoMotor.DO_AR)
    }
}
