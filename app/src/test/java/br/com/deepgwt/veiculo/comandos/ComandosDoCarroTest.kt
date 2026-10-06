// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.comandos

import br.com.deepgwt.veiculo.TravaDeMovimento
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** motor-comandos.md §B, §C, §D.3, §E, §H e §I. */
class ComandosDoCarroTest {

    // ── B. Vidros ──

    @Test
    fun `B1 B2 - estado e pedido de um vidro`() {
        val b = BancadaDosComandos()
        b.voz.janelas = intArrayOf(1, 2, 3, 0)
        assertArrayEquals(intArrayOf(1, 2, 3, 0), b.comandos.estadoDasJanelas())
        assertTrue(b.comandos.janela(2, 2))
        assertEquals(listOf("janela:2=2"), b.voz.pedidos)

        b.voz.falharEm += "estadoDasJanelas"
        assertNull(b.comandos.estadoDasJanelas())
        b.voz.falharEm += "pedirJanela"
        assertFalse(b.comandos.janela(0, 1))
        b.voz.semVeiculo = true
        assertNull(b.comandos.estadoDasJanelas())
        assertFalse(b.comandos.janela(0, 1))
    }

    @Test
    fun `B3 - fecha so as posicoes diferentes de 1 (inclusive -1 e 0)`() {
        val b = BancadaDosComandos()
        b.voz.janelas = intArrayOf(1, 2, -1, 0)
        assertTrue(b.comandos.fecharTodasAsJanelas())
        assertEquals(listOf("janela:1=1", "janela:2=1", "janela:3=1"), b.voz.pedidos)
    }

    @Test
    fun `B3 - leitura que lanca ou sem alca devolve falso`() {
        val b = BancadaDosComandos()
        b.voz.falharEm += "estadoDasJanelas"
        assertFalse(b.comandos.fecharTodasAsJanelas())
        assertTrue(b.voz.pedidos.isEmpty())

        val c = BancadaDosComandos()
        c.voz.semVeiculo = true
        assertFalse(c.comandos.fecharTodasAsJanelas())
    }

    @Test
    fun `B3 - pedido que lanca devolve falso mas tenta os outros`() {
        val b = BancadaDosComandos()
        b.voz.janelas = intArrayOf(2, 2, 2, 2)
        b.voz.falharEm += "pedirJanela"
        assertFalse(b.comandos.fecharTodasAsJanelas())
        assertEquals(4, b.eventos.count { it.startsWith("janela:") })
    }

    // ── C. Teto e cortina ──

    @Test
    fun `C1 C2 - leituras e pedidos`() {
        val b = BancadaDosComandos()
        b.voz.nivelTeto = 40
        b.voz.nivelCortina = 100
        assertEquals(40, b.comandos.aberturaDoTeto())
        assertEquals(100, b.comandos.aberturaDaCortina())
        assertTrue(b.comandos.teto(10))
        assertTrue(b.comandos.cortina(0))
        b.voz.falharEm += setOf("nivelDoTeto", "pedirTeto")
        assertNull(b.comandos.aberturaDoTeto())
        assertFalse(b.comandos.teto(10))
        b.voz.semVeiculo = true
        assertNull(b.comandos.aberturaDaCortina())
        assertFalse(b.comandos.cortina(100))
    }

    @Test
    fun `C3 - cortina menor que o teto - cortina 100 e teto 1500 ms depois`() {
        val b = BancadaDosComandos()
        b.voz.nivelCortina = 0
        assertTrue(b.comandos.abrirTetoComCortina(50))
        assertEquals(listOf("cortina:100"), b.voz.pedidos)
        b.agenda.avancar(1_499)
        assertEquals(listOf("cortina:100"), b.voz.pedidos)
        b.agenda.avancar(1)
        assertEquals(listOf("cortina:100", "teto:50"), b.voz.pedidos)
    }

    @Test
    fun `C3 - cortina ja aberta o bastante - teto na hora`() {
        val b = BancadaDosComandos()
        b.voz.nivelCortina = 100
        assertTrue(b.comandos.abrirTetoComCortina(50))
        assertEquals(listOf("teto:50"), b.voz.pedidos)
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `C3 - leitura nula da cortina - teto na hora`() {
        val b = BancadaDosComandos()
        b.voz.falharEm += "nivelDaCortina"
        assertTrue(b.comandos.abrirTetoComCortina(50))
        assertEquals(listOf("teto:50"), b.voz.pedidos)
    }

    @Test
    fun `C3 - cortina falha - teto na hora`() {
        val b = BancadaDosComandos()
        b.voz.nivelCortina = 0
        b.voz.falharEm += "pedirCortina"
        assertTrue(b.comandos.abrirTetoComCortina(50))
        assertEquals(listOf("teto:50"), b.voz.pedidos)
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `C4 - fechar teto sem a preferencia nao agenda a cortina`() {
        val b = BancadaDosComandos()
        b.voz.nivelTeto = 30
        b.comandos.fecharTeto(fecharCortinaJunto = true)
        assertEquals(listOf("teto:0"), b.voz.pedidos)
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `C4 - fechar teto com a preferencia - cortina 5 s depois, e duas chamadas = dois fechamentos`() {
        val b = BancadaDosComandos()
        b.prefs["closeSunroofSunShadeOnCloseSunRoof"] = true
        b.voz.nivelTeto = 0
        b.voz.nivelCortina = 100
        b.comandos.fecharTeto(true)
        b.comandos.fecharTeto(true)
        assertTrue("teto já fechado não é pedido", b.voz.pedidos.isEmpty())
        b.agenda.avancar(4_999)
        assertTrue(b.voz.pedidos.isEmpty())
        b.agenda.avancar(1)
        assertEquals(listOf("cortina:0", "cortina:0"), b.voz.pedidos)
    }

    @Test
    fun `C4 - sem cortina junto nao agenda mesmo com a preferencia`() {
        val b = BancadaDosComandos()
        b.prefs["closeSunroofSunShadeOnCloseSunRoof"] = true
        b.voz.nivelTeto = 10
        b.comandos.fecharTeto(false)
        assertEquals(listOf("teto:0"), b.voz.pedidos)
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `C4 D-C8 - teto -1 ou nunca lido recebe 0`() {
        val b = BancadaDosComandos()
        b.voz.nivelTeto = -1
        b.comandos.fecharTeto(false)
        b.voz.nivelTeto = Int.MIN_VALUE
        b.comandos.fecharTeto(false)
        assertEquals(listOf("teto:0", "teto:0"), b.voz.pedidos)
    }

    @Test
    fun `C5 - abrir cortina anota no diario`() {
        val b = BancadaDosComandos()
        b.voz.nivelCortina = 30
        b.comandos.abrirCortina()
        assertEquals(listOf("cortina:100"), b.voz.pedidos)
        assertEquals("cortina aberta", b.diario.entradas.last().first)
        assertEquals(30, b.diario.entradas.last().second["nivelAnterior"])

        b.voz.nivelCortina = 100
        b.comandos.abrirCortina()
        assertEquals(listOf("cortina:100"), b.voz.pedidos)
        assertEquals("cortina já aberta", b.diario.entradas.last().first)

        b.voz.falharEm += "nivelDaCortina"
        b.comandos.abrirCortina()
        assertEquals("cortina: falhou", b.diario.entradas.last().first)
    }

    @Test
    fun `C6 - fechar cortina so se nao esta fechada`() {
        val b = BancadaDosComandos()
        b.voz.nivelCortina = 0
        b.comandos.fecharCortina()
        assertTrue(b.voz.pedidos.isEmpty())
        b.voz.nivelCortina = 55
        b.comandos.fecharCortina()
        assertEquals(listOf("cortina:0"), b.voz.pedidos)
    }

    // ── D. Porta-malas ──

    @Test
    fun `D2 - porta-malas pelo cache`() {
        val b = BancadaDosComandos()
        assertNull(b.comandos.portaMalasAberto())
        b.carro.cache["car.basic.door_status"] = "{1,0,0,0,0,1}"
        assertEquals(true, b.comandos.portaMalasAberto())
        assertTrue("nunca vai ao carro", b.carro.leituras.isEmpty())
    }

    @Test
    fun `D3 - a trava recusa (andando, fora de P, sem leitura) e nada e pedido`() {
        for (motivo in TravaDeMovimento.Motivo.values()) {
            val b = BancadaDosComandos()
            b.recusa = { _, _ -> motivo }
            assertFalse(b.comandos.portaMalas(true))
            assertTrue(b.voz.pedidos.isEmpty())
        }
    }

    @Test
    fun `D3 - abrir exige P, fechar nao - sempre exige leitura`() {
        val b = BancadaDosComandos()
        assertTrue(b.comandos.portaMalas(true))
        assertTrue(b.comandos.portaMalas(false))
        assertEquals(listOf(true to true, false to true), b.pedidosATrava)
        assertEquals(listOf("porta:5=1", "porta:5=0"), b.voz.pedidos)
    }

    @Test
    fun `D3 - com a trava de verdade sobre leituras falsas`() {
        val b = BancadaDosComandos()
        b.recusa = { p, l -> TravaDeMovimento.avaliar(b.carro.ler("car.basic.vehicle_speed"), b.carro.ler("car.basic.gear_status"), p, l) }
        b.carro.cache["car.basic.vehicle_speed"] = "20.0"
        b.carro.cache["car.basic.gear_status"] = "3"
        assertFalse("andando", b.comandos.portaMalas(false))
        b.carro.cache["car.basic.vehicle_speed"] = "0.0"
        b.carro.cache["car.basic.gear_status"] = "2"
        assertFalse("fora de P ao abrir", b.comandos.portaMalas(true))
        assertTrue("fechar fora de P pode", b.comandos.portaMalas(false))
        b.carro.cache.remove("car.basic.vehicle_speed")
        assertFalse("sem leitura", b.comandos.portaMalas(false))
        assertEquals(listOf("porta:5=0"), b.voz.pedidos)
    }

    @Test
    fun `D3 - sem alca ou alca que lanca devolve falso`() {
        val b = BancadaDosComandos()
        b.voz.falharEm += "pedirPorta"
        assertFalse(b.comandos.portaMalas(true))
        b.voz.semVeiculo = true
        assertFalse(b.comandos.portaMalas(true))
    }

    // ── E. Câmera 360 ──

    @Test
    fun `E1 - fechada abre, marca e chama as guardas do painel`() {
        val b = BancadaDosComandos()
        b.carro.cache["sys.avm.preview_status"] = "0"
        b.comandos.alternarCamera360()
        assertEquals(listOf("avm:1"), b.voz.pedidos)
        assertTrue(b.comandos.cameraAbertaPeloApp)
        assertTrue("guardaCarPlay:camera 360 aberta pelo app" in b.eventos)
        assertTrue("guardaAA:camera 360 aberta pelo app" in b.eventos)
    }

    @Test
    fun `E1 - aberta fecha, desmarca e chama as guardas de saida`() {
        val b = BancadaDosComandos()
        b.comandos.cameraAbertaPeloApp = true
        b.carro.cache["sys.avm.preview_status"] = "1"
        b.comandos.alternarCamera360()
        assertEquals(listOf("avm:0"), b.voz.pedidos)
        assertFalse(b.comandos.cameraAbertaPeloApp)
        assertTrue("guardaCarPlay:camera 360 fechada pelo app" in b.eventos)
    }

    @Test
    fun `E4 - com o Android Auto no painel, so o pulso`() {
        val b = BancadaDosComandos()
        b.projecao.androidAutoNoPainel = true
        b.carro.cache["sys.avm.preview_status"] = "0"
        b.comandos.alternarCamera360()
        b.carro.cache["sys.avm.preview_status"] = "1"
        b.comandos.alternarCamera360()
        val guardas = b.eventos.filter { it.startsWith("pulso") || it.startsWith("guarda") }
        assertEquals(
            listOf("pulsoDurante:camera 360 aberta pelo app", "pulsoAoSair:camera 360 fechada pelo app"),
            guardas,
        )
    }

    @Test
    fun `E5 - cache nulo abre - alca nula nao faz nada e nao lanca`() {
        val b = BancadaDosComandos()
        b.comandos.alternarCamera360()
        assertEquals(listOf("avm:1"), b.voz.pedidos)

        val c = BancadaDosComandos()
        c.voz.semCamera = true
        c.comandos.alternarCamera360()
        assertTrue(c.voz.pedidos.isEmpty())
        assertFalse(c.comandos.cameraAbertaPeloApp)
        assertTrue(c.eventos.none { it.startsWith("guarda") })
    }

    @Test
    fun `E5 - decisao de nao abrir sem estado`() {
        val b = BancadaDosComandos(DecisoesDosComandos(cameraSemEstadoAbre = false))
        b.comandos.alternarCamera360()
        assertTrue(b.voz.pedidos.isEmpty())
    }

    @Test
    fun `E2 - fechar pelas automacoes`() {
        val b = BancadaDosComandos()
        b.comandos.fecharCamera360()
        assertEquals(listOf("avm:0"), b.voz.pedidos)
        b.voz.falharEm += "pedirAvm"
        b.comandos.fecharCamera360()
        b.voz.semCamera = true
        b.comandos.fecharCamera360()
    }

    // ── H. Escritas simples ──

    @Test
    fun `H - tabela de chaves e valores`() {
        val b = BancadaDosComandos()
        b.comandos.modoDeConducao("5")
        b.comandos.esp(true)
        b.comandos.esp(false)
        b.comandos.forcarMotorACombustao(true)
        b.comandos.forcarMotorACombustao(false)
        b.comandos.modoTravessia(true)
        b.comandos.somParaPedestres(false)
        b.comandos.monitoramento(false)
        b.comandos.fecharJanelasAoTrancar(true)
        b.comandos.gravarMemoriaDoBanco(2)
        b.comandos.chamarMemoriaDoBanco(3)
        assertEquals(
            listOf(
                "car.drive_setting.drive_mode" to "5",
                "car.drive_setting.esp_enable" to "1",
                "car.drive_setting.esp_enable" to "0",
                "car.ev_setting.power_model_config" to "0",
                "car.ev.setting.engine_discharge_enable" to "1",
                "car.ev_setting.power_model_config" to "1",
                "car.ev.setting.engine_discharge_enable" to "0",
                "car.ev.setting.wade_mode_enable" to "1",
                "car.ev.setting.avas_enable" to "0",
                "car.frs_setting.distraction_detection_enable" to "0",
                "car.frs_setting.fatigue_monitoring_enable" to "0",
                "car.basic.window_close_by_lock_vehicle" to "1",
                "car.comfort_setting.chair_mem_pos_set_action" to "2",
                "car.comfort_setting.chair_mem_pos_set_action" to "13",
            ),
            b.carro.escritas,
        )
    }

    @Test
    fun `H - canal morto nao escreve nada`() {
        val b = BancadaDosComandos()
        b.carro.controleVivo = false
        b.comandos.modoDeConducao("1")
        b.comandos.esp(true)
        b.comandos.forcarMotorACombustao(true)
        b.comandos.modoTravessia(true)
        b.comandos.somParaPedestres(true)
        b.comandos.monitoramento(true)
        b.comandos.fecharJanelasAoTrancar(true)
        b.comandos.gravarMemoriaDoBanco(1)
        b.comandos.chamarMemoriaDoBanco(1)
        assertTrue(b.carro.escritas.isEmpty())
        assertEquals(9, b.registro.erros.size)
    }

    @Test
    fun `H - posicao do banco fora de 1 a 3 nao escreve nada`() {
        val b = BancadaDosComandos()
        // "gravar 11" viraria "chamar 1": os dois lados usam o mesmo campo.
        for (fora in listOf(0, 4, 11, 13, -1)) {
            b.comandos.gravarMemoriaDoBanco(fora)
            b.comandos.chamarMemoriaDoBanco(fora)
        }
        assertTrue(b.carro.escritas.isEmpty())
        b.comandos.gravarMemoriaDoBanco(3)
        b.comandos.chamarMemoriaDoBanco(1)
        assertEquals(
            listOf(
                "car.comfort_setting.chair_mem_pos_set_action" to "3",
                "car.comfort_setting.chair_mem_pos_set_action" to "11",
            ),
            b.carro.escritas,
        )
    }

    @Test
    fun `H D-C4 - simulador escreve so com a decisao`() {
        val hoje = BancadaDosComandos()
        hoje.carro.controleVivo = false
        hoje.carro.simulador = true
        hoje.comandos.esp(true)
        assertTrue(hoje.carro.escritas.isEmpty())

        val ecoando = BancadaDosComandos(DecisoesDosComandos(escreverNoSimulador = true))
        ecoando.carro.controleVivo = false
        ecoando.carro.simulador = true
        ecoando.comandos.esp(true)
        assertEquals(listOf("car.drive_setting.esp_enable" to "1"), ecoando.carro.escritas)
    }

    @Test
    fun `H - monitoramento com a 1a escrita falhando ainda tenta a 2a`() {
        val b = BancadaDosComandos()
        b.carro.falharAoEscrever += "car.frs_setting.distraction_detection_enable"
        b.comandos.monitoramento(false)
        assertEquals(listOf("car.frs_setting.fatigue_monitoring_enable" to "0"), b.carro.escritas)
    }

    // ── I. Alvo de carga do híbrido ──

    private fun bancadaHev(): BancadaDosComandos {
        val b = BancadaDosComandos()
        b.prefs["enablePersistHevSocTarget"] = true
        b.prefs["hevSocTargetValue"] = 50
        b.carro.doCarro["car.ev_setting.power_model_config"] = "0"
        b.carro.doCarro["car.ev_setting.power_reserve_config"] = "2"
        b.carro.doCarro["car.ev_setting.charge_soc_target_config"] = "20"
        return b
    }

    @Test
    fun `I1 - em HEV Prioritario reescreve o alvo, na linha do motor`() {
        val b = bancadaHev()
        b.comandos.aplicarAlvoDeCargaHev("ao ligar")
        assertTrue("quem chama volta na hora", b.carro.escritas.isEmpty())
        b.agenda.rodarPendentes()
        assertEquals(listOf("car.ev_setting.charge_soc_target_config" to "50"), b.carro.escritas)
    }

    @Test
    fun `I1 - cada portao`() {
        val semPreferencia = bancadaHev().also { it.prefs["enablePersistHevSocTarget"] = false }
        val foraDeHev = bancadaHev().also { it.carro.doCarro["car.ev_setting.power_model_config"] = "1" }
        val foraDePrioritario = bancadaHev().also { it.carro.doCarro["car.ev_setting.power_reserve_config"] = "1" }
        val semLeitura = bancadaHev().also { it.carro.doCarro.remove("car.ev_setting.charge_soc_target_config") }
        val eco = bancadaHev().also { it.carro.doCarro["car.ev_setting.charge_soc_target_config"] = " 50 " }
        for (b in listOf(semPreferencia, foraDeHev, foraDePrioritario, semLeitura, eco)) {
            b.comandos.aplicarAlvoNaLinha("teste")
            assertTrue(b.carro.escritas.isEmpty())
        }
        assertTrue("sem preferência nem lê", semPreferencia.carro.leituras.isEmpty())
    }

    @Test
    fun `I1 - ilegivel reescreve - nunca escreve o modo nem o sub-modo`() {
        val b = bancadaHev()
        b.carro.doCarro["car.ev_setting.charge_soc_target_config"] = "45.0"
        b.comandos.aplicarAlvoNaLinha("teste")
        assertEquals(listOf("car.ev_setting.charge_soc_target_config" to "50"), b.carro.escritas)
        assertTrue(b.carro.escritas.none { it.first == "car.ev_setting.power_model_config" || it.first == "car.ev_setting.power_reserve_config" })
    }

    @Test
    fun `I3 - limita a 20 e 80, grava e escreve sem conferir o canal`() {
        val b = BancadaDosComandos()
        b.carro.controleVivo = false
        b.comandos.definirAlvoDeCargaHev(10)
        assertEquals(20, b.prefs.valores["hevSocTargetValue"])
        b.comandos.definirAlvoDeCargaHev(95)
        assertEquals(80, b.prefs.valores["hevSocTargetValue"])
        b.comandos.definirAlvoDeCargaHev(55)
        assertEquals(
            listOf("20", "80", "55"),
            b.carro.escritasDe("car.ev_setting.charge_soc_target_config"),
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
