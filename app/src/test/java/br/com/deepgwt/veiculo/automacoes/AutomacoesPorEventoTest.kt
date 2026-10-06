// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.automacoes

import br.com.deepgwt.veiculo.TravaDeMovimento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** motor-automacoes.md §A, §B, §C e §J. */
class AutomacoesPorEventoTest {

    // ── A. Recepção ──

    @Test
    fun `A2 - evento antes do pronto e descartado`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvas"] = true
        b.pronto = false
        b.evento("car.ev.setting.avas_enable", "1")
        assertTrue(b.acoes().isEmpty())
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `A3 - o gancho volta na hora e a ordem de chegada e mantida`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvas"] = true
        b.prefs["disableMonitoring"] = true
        b.automacoes.aoChegarDoCarro("car.ev.setting.avas_enable", "1")
        b.automacoes.aoChegarDoCarro("car.frs_setting.fatigue_monitoring_enable", "1")
        assertTrue("nada na thread do evento", b.acoes().isEmpty())
        b.agenda.rodarPendentes()
        assertEquals(listOf("acao:avas=false", "acao:monitoramento=false"), b.acoes())
    }

    @Test
    fun `A1 - valor nulo e ignorado`() {
        val b = BancadaDasAutomacoes()
        b.automacoes.aoChegarDoCarro("car.basic.driving_ready_state", null)
        assertEquals(0, b.agenda.pendentes())
    }

    @Test
    fun `A4 - uma excecao num caso nao impede os outros nem o proximo evento`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableBluetoothOnPowerOff"] = true
        b.prefs["closeWindowOnPowerOff"] = true
        b.acoes.falharEm += "fecharJanelas"
        b.evento("car.basic.driving_ready_state", "0")
        assertTrue("sistema:btGuardando:desligou" in b.eventos)
        b.prefs["disableAvas"] = true
        b.evento("car.ev.setting.avas_enable", "1")
        assertTrue("acao:avas=false" in b.eventos)
    }

    // ── B.1 Guardas de projeção ──

    @Test
    fun `B1 - camera 360 sem AA no painel - as duas guardas`() {
        val b = BancadaDasAutomacoes()
        b.evento("sys.avm.preview_status", "1")
        assertTrue("guardaCarPlay:câmera 360 = 1" in b.eventos)
        assertTrue("guardaAA:câmera 360 = 1" in b.eventos)
    }

    @Test
    fun `B1 - camera 360 com AA no painel - so os pulsos`() {
        val b = BancadaDasAutomacoes()
        b.projecao.androidAutoNoPainel = true
        b.evento("sys.avm.preview_status", "1")
        b.evento("sys.avm.preview_status", "0")
        b.evento("sys.avm.preview_status", "2")
        val guardas = b.eventos.filter { it.startsWith("guarda") || it.startsWith("pulso") }
        assertEquals(listOf("pulsoDurante:câmera 360 = 1", "pulsoAoSair:câmera 360 = 0"), guardas)
    }

    @Test
    fun `B1 - painel do ar`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.hvac.panel_display_notify", "1")
        assertTrue("guardaCarPlay:painel do ar = 1" in b.eventos)
        assertTrue("guardaAA:painel do ar = 1" in b.eventos)

        val comAa = BancadaDasAutomacoes()
        comAa.projecao.androidAutoNoPainel = true
        comAa.evento("car.hvac.panel_display_notify", "1")
        assertTrue(comAa.eventos.none { it.startsWith("guarda") || it.startsWith("pulso") })
    }

    // ── B.2 / B.3 ──

    @Test
    fun `B2 B3 - so com 1 e a preferencia`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.frs_setting.distraction_detection_enable", "1")
        b.evento("car.ev.setting.avas_enable", "1")
        assertTrue(b.acoes().isEmpty())
        b.prefs["disableMonitoring"] = true
        b.prefs["disableAvas"] = true
        b.evento("car.frs_setting.distraction_detection_enable", "0")
        b.evento("car.ev.setting.avas_enable", "0")
        assertTrue(b.acoes().isEmpty())
        b.evento("car.frs_setting.distraction_detection_enable", "1")
        b.evento("car.frs_setting.fatigue_monitoring_enable", "1")
        b.evento("car.ev.setting.avas_enable", "1")
        assertEquals(listOf("acao:monitoramento=false", "acao:monitoramento=false", "acao:avas=false"), b.acoes())
    }

    // ── B.4 ──

    @Test
    fun `B4 - camera do motorista parou - fecha so com o carro desligado`() {
        val b = BancadaDasAutomacoes()
        b.prefs["closeWindowOnPowerOff"] = true
        b.evento("car.dms.work_state", "0")
        assertTrue(b.acoes().isEmpty())
        b.evento("car.basic.driving_ready_state", "0")
        b.agenda.avancar(10_000)
        b.eventos.clear()
        b.evento("car.dms.work_state", "0")
        assertEquals(listOf("acao:fecharJanelas"), b.acoes())
    }

    // ── B.5 Retrovisores ──

    private fun bancadaDoRetrovisor(): BancadaDasAutomacoes {
        val b = BancadaDasAutomacoes()
        b.carro.doCarro["car.basic.vehicle_speed"] = "0.0"
        b.carro.doCarro["car.basic.gear_status"] = "3"
        return b
    }

    @Test
    fun `B5 - parado em P fecha, cada preferencia isolada`() {
        val b = bancadaDoRetrovisor()
        b.prefs["closeWindowOnFoldMirror"] = true
        b.prefs["disableHotspotOnFoldMirror"] = true
        b.evento("car.drive.setting.outside_view_mirror_fold_state", "0")
        assertEquals(listOf("acao:fecharJanelas", "sistema:hotspotGuardando"), b.acoes())

        val todas = bancadaDoRetrovisor()
        for (p in listOf("closeWindowOnFoldMirror", "closeSunroofOnFoldMirror", "disableBluetoothOnFoldMirror", "disableHotspotOnFoldMirror")) {
            todas.prefs[p] = true
        }
        todas.evento("car.drive.setting.outside_view_mirror_fold_state", "0")
        assertEquals(
            listOf("acao:fecharJanelas", "acao:fecharTeto=true", "sistema:btGuardando:retrovisor", "sistema:hotspotGuardando"),
            todas.acoes(),
        )
        assertTrue("não usa a trava de movimento", todas.pedidosATrava.isEmpty())
    }

    @Test
    fun `B5 - andando, fora de P, leitura nula ou retrovisor aberto - nada`() {
        val casos = listOf<(BancadaDasAutomacoes) -> Unit>(
            { it.carro.doCarro["car.basic.vehicle_speed"] = "0.5" },
            { it.carro.doCarro["car.basic.gear_status"] = "2" },
            { it.carro.doCarro.remove("car.basic.vehicle_speed") },
            { it.carro.doCarro.remove("car.basic.gear_status") },
        )
        for (preparar in casos) {
            val b = bancadaDoRetrovisor()
            b.prefs["closeWindowOnFoldMirror"] = true
            preparar(b)
            b.evento("car.drive.setting.outside_view_mirror_fold_state", "0")
            assertTrue(b.acoes().isEmpty())
        }
        val aberto = bancadaDoRetrovisor()
        aberto.prefs["closeWindowOnFoldMirror"] = true
        aberto.evento("car.drive.setting.outside_view_mirror_fold_state", "1")
        assertTrue(aberto.acoes().isEmpty())
    }

    @Test
    fun `B5 - marcha em letra (simulador)`() {
        val b = bancadaDoRetrovisor()
        b.carro.doCarro["car.basic.gear_status"] = "P"
        b.prefs["closeWindowOnFoldMirror"] = true
        b.evento("car.drive.setting.outside_view_mirror_fold_state", "0")
        assertEquals(listOf("acao:fecharJanelas"), b.acoes())
    }

    // ── B.6 Velocidade ──

    @Test
    fun `B6 - limiar 15 - 14,9 e 15,0 nada, 15,1 fecha - 20 nao repete - 10 rearma`() {
        val b = BancadaDasAutomacoes()
        b.prefs["closeWindowsOnSpeed"] = true
        b.evento("car.basic.vehicle_speed", "14.9")
        b.evento("car.basic.vehicle_speed", "15.0")
        assertTrue(b.acoes().isEmpty())
        b.evento("car.basic.vehicle_speed", "15.1")
        assertEquals(listOf("acao:fecharJanelas"), b.acoes())
        b.evento("car.basic.vehicle_speed", "20.0")
        assertEquals(1, b.acoes().size)
        b.evento("car.basic.vehicle_speed", "10.0")
        b.evento("car.basic.vehicle_speed", "16.0")
        assertEquals(listOf("acao:fecharJanelas", "acao:fecharJanelas"), b.acoes())
    }

    @Test
    fun `B6 - a marca fica mesmo com a preferencia desligada`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.basic.vehicle_speed", "30.0")
        b.prefs["closeWindowsOnSpeed"] = true
        b.evento("car.basic.vehicle_speed", "35.0")
        assertTrue("já marcado: não fecha até rearmar", b.acoes().isEmpty())
    }

    @Test
    fun `B6 - limiar proprio do teto, sem cortina`() {
        val b = BancadaDasAutomacoes()
        b.prefs["closeWindowsOnSpeed"] = true
        b.prefs["closeSunroofOnSpeed"] = true
        b.prefs["speedThreshold"] = 40.0f
        b.prefs["sunroofSpeedThreshold"] = 20.0f
        b.evento("car.basic.vehicle_speed", "25.0")
        assertEquals(listOf("acao:fecharTeto=false"), b.acoes())
        b.evento("car.basic.vehicle_speed", "41.0")
        assertEquals(listOf("acao:fecharTeto=false", "acao:fecharJanelas"), b.acoes())
    }

    @Test
    fun `B6 - valor ilegivel e ignorado`() {
        val b = BancadaDasAutomacoes()
        b.prefs["closeWindowsOnSpeed"] = true
        b.evento("car.basic.vehicle_speed", "rapido")
        assertTrue(b.acoes().isEmpty())
        assertTrue(b.registro.avisos.isNotEmpty())
    }

    @Test
    fun `B6 - camera parada respeita abertaPeloApp e a R`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        b.carro.cache["car.basic.gear_status"] = "4"
        b.evento("car.basic.vehicle_speed", "0.0")
        assertTrue("em R não fecha", b.acoes().isEmpty())

        b.carro.cache["car.basic.gear_status"] = "2"
        b.acoes.cameraAbertaPeloApp = true
        b.evento("car.basic.vehicle_speed", "0.0")
        assertTrue("aberta pelo app não fecha", b.acoes().isEmpty())

        b.acoes.cameraAbertaPeloApp = false
        b.evento("car.basic.vehicle_speed", "0.0")
        assertEquals(listOf("acao:fecharCamera"), b.acoes())

        val semMarcha = BancadaDasAutomacoes()
        semMarcha.prefs["disableAvmCarStopped"] = true
        semMarcha.evento("car.basic.vehicle_speed", "0.0")
        assertTrue("marcha desconhecida: não fecha (D-A4)", semMarcha.acoes().isEmpty())
    }

    @Test
    fun `B6 D-A3 - fecha uma vez por abertura`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        b.carro.cache["car.basic.gear_status"] = "2"
        b.carro.cache["car.basic.vehicle_speed"] = "5.0"
        b.evento("car.basic.vehicle_speed", "0.0")
        b.evento("car.basic.vehicle_speed", "0.0")
        assertEquals(listOf("acao:fecharCamera"), b.acoes())
        b.evento("sys.avm.preview_status", "1")
        b.evento("car.basic.vehicle_speed", "0.0")
        assertEquals(listOf("acao:fecharCamera", "acao:fecharCamera"), b.acoes())
    }

    @Test
    fun `B6 - sem a decisao D-A3, fecha a cada evento (como hoje)`() {
        val b = BancadaDasAutomacoes(DecisoesDasAutomacoes(cameraParadaUmaVezPorAbertura = false))
        b.prefs["disableAvmCarStopped"] = true
        b.carro.cache["car.basic.gear_status"] = "2"
        repeat(3) { b.evento("car.basic.vehicle_speed", "0.0") }
        assertEquals(3, b.acoes().size)
    }

    // ── B.7 ──

    @Test
    fun `B7 - camera abriu parada fecha - outro valor limpa a marca do app`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        b.carro.cache["car.basic.vehicle_speed"] = "0.0"
        b.carro.cache["car.basic.gear_status"] = "3"
        b.evento("sys.avm.preview_status", "1")
        assertEquals(listOf("acao:fecharCamera"), b.acoes())

        b.acoes.cameraAbertaPeloApp = true
        b.evento("sys.avm.preview_status", "1")
        assertEquals("aberta pelo app: não fecha", 1, b.acoes().size)
        b.evento("sys.avm.preview_status", "0")
        assertFalse(b.acoes.cameraAbertaPeloApp)
    }

    // ── Alpha 9.8: abrir pelo botão com o carro parado ──

    private fun bancadaDoBotao(permitir: Boolean): BancadaDasAutomacoes {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        if (permitir) b.prefs["avmManualWhenStopped"] = true
        b.carro.cache["car.basic.vehicle_speed"] = "0.0"
        b.carro.cache["car.basic.gear_status"] = "3"
        b.evento("sys.avm.preview_status", "0")
        return b
    }

    @Test
    fun `B7 botao - sem a permissao a camera aberta parado fecha, como antes`() {
        val b = bancadaDoBotao(permitir = false)
        b.evento("sys.avm.preview_status", "1")
        assertEquals(listOf("acao:fecharCamera"), b.acoes())
    }

    @Test
    fun `B7 botao - com a permissao a abertura parado fica aberta, tambem quando a velocidade chega`() {
        val b = bancadaDoBotao(permitir = true)
        b.evento("sys.avm.preview_status", "1")
        b.evento("car.basic.vehicle_speed", "0.0")
        b.evento("car.basic.vehicle_speed", "0.0")
        assertTrue("aberta pelo botão: não fecha", b.acoes().isEmpty())
    }

    @Test
    fun `B7 botao - fechar e abrir de novo parado continua valendo`() {
        val b = bancadaDoBotao(permitir = true)
        b.evento("sys.avm.preview_status", "1")
        b.evento("sys.avm.preview_status", "0")
        b.evento("sys.avm.preview_status", "1")
        b.evento("car.basic.vehicle_speed", "0.0")
        assertTrue(b.acoes().isEmpty())
    }

    @Test
    fun `B7 botao - camera que ja estava aberta em re nao vira botao ao chegar na D parado`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        b.prefs["avmManualWhenStopped"] = true
        b.carro.cache["car.basic.vehicle_speed"] = "0.0"
        b.carro.cache["car.basic.gear_status"] = "4"
        b.evento("sys.avm.preview_status", "0")
        b.evento("sys.avm.preview_status", "1") // abriu sozinha na ré
        b.carro.cache["car.basic.gear_status"] = "3"
        b.evento("sys.avm.preview_status", "1") // repetição do mesmo estado, já fora da ré
        assertEquals("a repetição não é um aperto de botão", listOf("acao:fecharCamera"), b.acoes())
    }

    @Test
    fun `B7 botao - em re nao e botao`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        b.prefs["avmManualWhenStopped"] = true
        b.carro.cache["car.basic.vehicle_speed"] = "0.0"
        b.carro.cache["car.basic.gear_status"] = "4"
        b.evento("sys.avm.preview_status", "0")
        b.evento("sys.avm.preview_status", "1")
        b.carro.cache["car.basic.gear_status"] = "3"
        b.evento("car.basic.vehicle_speed", "0.0")
        assertEquals(listOf("acao:fecharCamera"), b.acoes())
    }

    @Test
    fun `B7 - fora das condicoes nao fecha nem limpa (D-A9)`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableAvmCarStopped"] = true
        b.carro.cache["car.basic.vehicle_speed"] = "0.0"
        b.carro.cache["car.basic.gear_status"] = "R"
        b.acoes.cameraAbertaPeloApp = true
        b.evento("sys.avm.preview_status", "0")
        assertTrue(b.acoes.cameraAbertaPeloApp)
        b.acoes.cameraAbertaPeloApp = false
        b.evento("sys.avm.preview_status", "1")
        assertTrue(b.acoes().isEmpty())

        val semPreferencia = BancadaDasAutomacoes()
        semPreferencia.carro.cache["car.basic.vehicle_speed"] = "0.0"
        semPreferencia.carro.cache["car.basic.gear_status"] = "3"
        semPreferencia.evento("sys.avm.preview_status", "1")
        assertTrue(semPreferencia.acoes().isEmpty())
    }

    // ── B.8 READY ──

    private fun bancadaDoReady(decisoes: DecisoesDasAutomacoes = DecisoesDasAutomacoes()): BancadaDasAutomacoes {
        val b = BancadaDasAutomacoes(decisoes)
        b.prefs["closeWindowOnPowerOff"] = true
        b.prefs["disableBluetoothOnPowerOff"] = true
        b.prefs["disableHotspotOnPowerOff"] = true
        b.prefs["enableMaxAcOnUnlock"] = true
        return b
    }

    @Test
    fun `B8 - desligou - ordem dos 5 passos`() {
        val b = bancadaDoReady()
        b.acoes.arNoMaximoAtivo = true
        b.evento("car.basic.driving_ready_state", "0")
        assertTrue(b.automacoes.carroDesligado)
        assertEquals(
            listOf("acao:fecharJanelas", "sistema:btGuardando:desligou", "sistema:hotspotGuardando", "acao:arSair"),
            b.acoes(),
        )
    }

    @Test
    fun `B8 - ligou - ordem dos passos`() {
        val b = bancadaDoReady()
        b.evento("car.basic.driving_ready_state", "1")
        assertFalse(b.automacoes.carroDesligado)
        assertEquals(
            listOf(
                "sistema:btReligar:ligou",
                "sistema:hotspotReligar",
                "acao:arLigarSeQuente",
                "acao:hibrido:ao ligar",
                "gancho:perfil",
            ),
            b.acoes(),
        )
    }

    @Test
    fun `B8 - ar no maximo so com a preferencia e se nao ativo`() {
        val b = bancadaDoReady()
        b.acoes.arNoMaximoAtivo = true
        b.evento("car.basic.driving_ready_state", "1")
        assertFalse("acao:arLigarSeQuente" in b.acoes())

        val semPreferencia = BancadaDasAutomacoes()
        semPreferencia.evento("car.basic.driving_ready_state", "1")
        assertFalse("acao:arLigarSeQuente" in semPreferencia.acoes())
    }

    @Test
    fun `B8 - menos 1 e espacos contam como desligado`() {
        val b = bancadaDoReady()
        b.evento("car.basic.driving_ready_state", "-1")
        assertTrue(b.automacoes.carroDesligado)
        val c = bancadaDoReady()
        c.evento("car.basic.driving_ready_state", " 0 ")
        assertTrue(c.automacoes.carroDesligado)
    }

    @Test
    fun `B8 D-A2 - com borda - repeticao nao age, vazio e ignorado`() {
        val b = bancadaDoReady()
        b.evento("car.basic.driving_ready_state", "1")
        val depoisDoPrimeiro = b.acoes().size
        b.evento("car.basic.driving_ready_state", "1")
        b.evento("car.basic.driving_ready_state", "")
        assertEquals(depoisDoPrimeiro, b.acoes().size)
        assertFalse(b.automacoes.carroDesligado)
        b.evento("car.basic.driving_ready_state", "0")
        assertTrue(b.automacoes.carroDesligado)
        b.evento("car.basic.driving_ready_state", "-1")
        b.evento("car.basic.driving_ready_state", "1")
        assertEquals(2, b.acoes().count { it == "gancho:perfil" })
    }

    @Test
    fun `B8 - sem borda (como hoje) - cada 1 repete e vazio conta como ligou`() {
        val b = bancadaDoReady(DecisoesDasAutomacoes(readyComBorda = false))
        b.evento("car.basic.driving_ready_state", "0")
        assertTrue(b.automacoes.carroDesligado)
        b.evento("car.basic.driving_ready_state", "")
        assertFalse(b.automacoes.carroDesligado)
        b.evento("car.basic.driving_ready_state", "1")
        assertEquals(2, b.acoes().count { it == "gancho:perfil" })
    }

    // ── B.9 – B.11 ──

    @Test
    fun `B9 - ventilacao do banco com o ar`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.hvac.power_mode", "1")
        assertTrue("sem a preferência, nada", b.carro.escritas.isEmpty())
        b.prefs["enableSeatVentilationOnAcOn"] = true
        b.evento("car.hvac.power_mode", "1")
        b.evento("car.hvac.power_mode", "0")
        b.evento("car.hvac.power_mode", "2")
        assertEquals(listOf("3", "0"), b.carro.escritasDe("car.comfort_setting.driver_seat_ventilation_level"))
        assertEquals(3, b.diario.nomes().count { it == "ventilação do banco" })
    }

    @Test
    fun `B10 - suavizacao so com a funcao e o maximo ativo`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.basic.inside_temp", "29.0")
        b.prefs["enableMaxAcOnUnlock"] = true
        b.evento("car.basic.inside_temp", "29.0")
        assertTrue(b.acoes().isEmpty())
        b.acoes.arNoMaximoAtivo = true
        b.evento("car.basic.inside_temp", "29.0")
        assertEquals(listOf("acao:arSuavizar"), b.acoes())
    }

    @Test
    fun `B11 - hibrido pelos tres gatilhos`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.ev_setting.charge_soc_target_config", "45")
        b.evento("car.ev_setting.power_reserve_config", "1")
        b.evento("car.ev_setting.power_reserve_config", " 2 ")
        b.evento("car.ev_setting.power_model_config", "1")
        b.evento("car.ev_setting.power_model_config", "0")
        assertEquals(
            listOf("acao:hibrido:alvo mudou", "acao:hibrido:entrou em Prioritário", "acao:hibrido:entrou em HEV"),
            b.acoes(),
        )
    }

    // ── C. Fechar ao desligar ──

    @Test
    fun `C1 - preferencias desligadas - nada`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.basic.driving_ready_state", "0")
        assertTrue(b.acoes().none { it.startsWith("acao:fechar") })
        assertTrue(b.pedidosATrava.isEmpty())
    }

    @Test
    fun `C2 - a trava recusa (sem exigir P nem leitura)`() {
        val b = BancadaDasAutomacoes()
        b.prefs["closeWindowOnPowerOff"] = true
        b.recusa = TravaDeMovimento.Motivo.ANDANDO
        b.evento("car.basic.driving_ready_state", "0")
        assertEquals(listOf(false to false), b.pedidosATrava)
        assertTrue(b.acoes().isEmpty())
    }

    @Test
    fun `C3 C4 - vidros e teto com cortina, e nada nos 10 s seguintes`() {
        val b = BancadaDasAutomacoes()
        b.prefs["closeWindowOnPowerOff"] = true
        b.prefs["closeSunroofOnPowerOff"] = true
        b.evento("car.basic.driving_ready_state", "0")
        assertEquals(listOf("acao:fecharJanelas", "acao:fecharTeto=true"), b.acoes())
        b.agenda.avancar(9_999)
        b.evento("car.dms.work_state", "0")
        assertEquals(2, b.acoes().size)
        b.agenda.avancar(1)
        b.evento("car.dms.work_state", "0")
        assertEquals(4, b.acoes().size)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
