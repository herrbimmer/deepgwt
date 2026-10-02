package br.com.opengw.veiculo.automacoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** motor-automacoes.md §D–§I. */
class AutomacoesDePartidaTest {

    // ── D. Partida ──

    @Test
    fun `D1 - simulador - so funcoes do carro (linha) e projetores (principal)`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableMonitoring"] = true
        b.automacoes.aoPartir(false)
        assertTrue(b.acoes().isEmpty())
        b.agenda.rodarPendentes()
        b.rodarPrincipal()
        assertEquals(listOf("gancho:funcoes", "gancho:projetores"), b.acoes())
        b.agenda.avancar(60_000)
        assertEquals(2, b.acoes().size)
    }

    @Test
    fun `D2 a D7 - real - ordem dos passos`() {
        val b = BancadaDasAutomacoes()
        b.prefs["setStartupVolume"] = true
        b.prefs["startupVolume"] = 12
        b.prefs["disableMonitoring"] = true
        b.prefs["disableAvas"] = true
        b.prefs["closeWindowOnLockVehicle"] = true
        b.prefs["enableAutoBrightness"] = true
        b.automacoes.aoPartir(true)
        assertEquals(listOf("sys.settings.audio.media_volume" to "12"), b.carro.escritas)
        assertEquals(
            listOf(
                "acao:monitoramento=false",
                "acao:avas=false",
                "acao:fecharAoTrancar=true",
                "acao:brilho",
                "sistema:appsDoSistema",
                "sistema:debloat",
                "gancho:consistencia",
            ),
            b.acoes(),
        )
    }

    @Test
    fun `D3 D4 - preferencias desligadas nao escrevem (fechar ao trancar desligado nao escreve 0)`() {
        val b = BancadaDasAutomacoes()
        b.prefs["setStartupVolume"] = true
        b.automacoes.aoPartir(true)
        assertTrue("volume ausente (-1): nada", b.carro.escritas.isEmpty())
        assertEquals(listOf("sistema:appsDoSistema", "sistema:debloat", "gancho:consistencia"), b.acoes())
    }

    @Test
    fun `D2 D-A7 - um passo que lanca nao derruba os outros`() {
        val b = BancadaDasAutomacoes()
        b.prefs["disableMonitoring"] = true
        b.acoes.falharEm += "monitoramento=false"
        b.sistema.falharEm += "appsDoSistema"
        b.automacoes.aoPartir(true)
        assertTrue("sistema:debloat" in b.acoes())
        assertTrue("gancho:consistencia" in b.acoes())
    }

    @Test
    fun `D8 - depois do pronto - linha, agendas e principal`() {
        val b = BancadaDasAutomacoes()
        b.automacoes.depoisDoPronto()
        b.agenda.rodarPendentes()
        assertEquals(listOf("gancho:funcoes"), b.acoes())
        assertTrue(b.shell.comandos.contains("sh -c settings put global enable_freeform_support 1"))
        assertTrue(b.shell.comandos.contains("sh -c settings put global force_resizable_activities 1"))
        b.rodarPrincipal()
        assertEquals(listOf("gancho:funcoes", "gancho:projetores"), b.acoes())
    }

    // ── E. Conferência ──

    @Test
    fun `E - READY ligado religa e aplica a ventilacao com o ar lido`() {
        val b = BancadaDasAutomacoes()
        b.prefs["enableSeatVentilationOnAcOn"] = true
        b.carro.doCarro["car.basic.driving_ready_state"] = "1"
        b.carro.doCarro["car.hvac.power_mode"] = "1"
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(5_999)
        assertFalse(b.acoes().any { it.startsWith("sistema:btReligar") })
        b.agenda.avancar(1)
        assertTrue("sistema:btReligar:conferência da partida" in b.acoes())
        assertTrue("sistema:hotspotReligar" in b.acoes())
        assertEquals(listOf("3"), b.carro.escritasDe("car.comfort_setting.driver_seat_ventilation_level"))
        assertFalse(b.automacoes.carroDesligado)
        val entrada = b.diario.entradas.first { it.first == "conferência da partida" }.second
        assertEquals("1", entrada["ready"])
        assertEquals("1", entrada["ar"])
    }

    @Test
    fun `E - READY desligado marca o carro desligado e para`() {
        val b = BancadaDasAutomacoes()
        b.carro.doCarro["car.basic.driving_ready_state"] = "-1"
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(6_000)
        assertTrue(b.automacoes.carroDesligado)
        assertFalse(b.acoes().any { it.startsWith("sistema:btReligar") })
    }

    @Test
    fun `E - READY desconhecido nao mexe e para`() {
        val b = BancadaDasAutomacoes()
        b.evento("car.basic.driving_ready_state", "0")
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(6_000)
        assertTrue("não mexe", b.automacoes.carroDesligado)
        assertFalse(b.acoes().any { it.startsWith("sistema:btReligar:conf") })
        assertEquals("READY desconhecido: nada", b.diario.entradas.first { it.first == "conferência da partida" }.second["resultado"])
    }

    // ── F. Híbrido ──

    @Test
    fun `F - 8 s, 18 s e o monitor a cada 30 s`() {
        val b = BancadaDasAutomacoes()
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(7_999)
        assertTrue(b.acoes().none { it.startsWith("acao:hibrido") })
        b.agenda.avancar(1)
        assertEquals(listOf("acao:hibrido:partida+8s"), b.acoes().filter { it.startsWith("acao:hibrido") })
        b.agenda.avancar(10_000)
        b.agenda.avancar(12_000)
        assertEquals(
            listOf("acao:hibrido:partida+8s", "acao:hibrido:partida+18s", "acao:hibrido:monitor"),
            b.acoes().filter { it.startsWith("acao:hibrido") },
        )
        b.agenda.avancar(60_000)
        assertEquals(3, b.acoes().count { it == "acao:hibrido:monitor" })
    }

    @Test
    fun `F2 - monitor reagenda depois de falha e a re-partida nao duplica`() {
        val b = BancadaDasAutomacoes()
        b.acoes.falharEm += "hibrido:monitor"
        b.automacoes.depoisDoPronto()
        b.automacoes.encerrar()
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(90_000)
        assertEquals(3, b.acoes().count { it == "acao:hibrido:monitor" })
        assertEquals(1, b.acoes().count { it == "acao:hibrido:partida+8s" })
    }

    @Test
    fun `encerrar cancela tudo e repassa ao ar e a conectividade`() {
        val b = BancadaDasAutomacoes()
        b.carro.doCarro["car.basic.driving_ready_state"] = "1"
        b.automacoes.depoisDoPronto()
        b.automacoes.encerrar()
        b.agenda.avancar(120_000)
        assertTrue(b.acoes().none { it.startsWith("acao:hibrido") || it.startsWith("sistema:btReligar") })
        assertTrue("acao:arCancelar" in b.acoes())
        assertTrue("sistema:cancelarRetentativas" in b.acoes())
    }

    // ── G. Cortina ao ligar ──

    private fun bancadaDaCortina(minutos: Int): BancadaDasAutomacoes {
        val b = BancadaDasAutomacoes()
        b.prefs["enableOpenSunroofCurtainOnStart"] = true
        b.minutosDoDia = minutos
        return b
    }

    @Test
    fun `G - janela padrao 18-09 sem temperatura - abre 2 s depois`() {
        val b = bancadaDaCortina(18 * 60)
        b.automacoes.depoisDoPronto()
        b.agenda.rodarPendentes()
        b.agenda.avancar(1_999)
        assertFalse("acao:abrirCortina" in b.acoes())
        b.agenda.avancar(1)
        assertTrue("acao:abrirCortina" in b.acoes())
    }

    @Test
    fun `G3 - fora da janela desiste e anota`() {
        val b = bancadaDaCortina(17 * 60 + 59)
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(10_000)
        assertFalse("acao:abrirCortina" in b.acoes())
        assertEquals(false, b.diario.entradas.first { it.first == "cortina ao ligar" }.second["dentro"])
    }

    @Test
    fun `G1 - uma vez por processo na partida`() {
        val b = bancadaDaCortina(20 * 60)
        b.automacoes.depoisDoPronto()
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(10_000)
        assertEquals(1, b.acoes().count { it == "acao:abrirCortina" })
    }

    @Test
    fun `G4 - temperatura nula tenta 6 vezes a cada 5 s e desiste`() {
        val b = bancadaDaCortina(20 * 60)
        b.prefs["openSunroofCurtainMaxTemp"] = 30.0f
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(60_000)
        assertEquals(6, b.carro.leituras.count { it == "car.basic.outside_temp" })
        assertFalse("acao:abrirCortina" in b.acoes())
    }

    @Test
    fun `G4 - le na 3a tentativa - acima da maxima nao abre`() {
        val b = bancadaDaCortina(20 * 60)
        b.prefs["openSunroofCurtainMaxTemp"] = 30.0f
        b.carro.responder("car.basic.outside_temp", null, "")
        b.carro.doCarro["car.basic.outside_temp"] = "25.0"
        b.automacoes.depoisDoPronto()
        b.agenda.avancar(60_000)
        assertEquals(3, b.carro.leituras.count { it == "car.basic.outside_temp" })
        assertTrue("acao:abrirCortina" in b.acoes())

        val quente = bancadaDaCortina(20 * 60)
        quente.prefs["openSunroofCurtainMaxTemp"] = 30.0f
        quente.carro.doCarro["car.basic.outside_temp"] = "30.5"
        quente.automacoes.depoisDoPronto()
        quente.agenda.avancar(60_000)
        assertFalse("acao:abrirCortina" in quente.acoes())
    }

    @Test
    fun `G - D-A10 - roda de novo a cada READY desligado para ligado`() {
        val b = bancadaDaCortina(20 * 60)
        b.automacoes.depoisDoPronto()
        b.evento("car.basic.driving_ready_state", "1")
        b.agenda.avancar(10_000)
        assertEquals("o primeiro READY não repete a da partida", 1, b.acoes().count { it == "acao:abrirCortina" })
        b.evento("car.basic.driving_ready_state", "0")
        b.evento("car.basic.driving_ready_state", "1")
        b.agenda.avancar(10_000)
        assertEquals(2, b.acoes().count { it == "acao:abrirCortina" })

        val comoHoje = BancadaDasAutomacoes(DecisoesDasAutomacoes(cortinaACadaReady = false))
        comoHoje.prefs["enableOpenSunroofCurtainOnStart"] = true
        comoHoje.minutosDoDia = 20 * 60
        comoHoje.evento("car.basic.driving_ready_state", "0")
        comoHoje.evento("car.basic.driving_ready_state", "1")
        comoHoje.agenda.avancar(10_000)
        assertFalse("acao:abrirCortina" in comoHoje.acoes())
    }

    // ── H. Botões do volante ──

    @Test
    fun `H - validos 0 a 98 vao para o carro - a preferencia vira falso`() {
        val b = BancadaDasAutomacoes()
        b.prefs["enableSteeringWheelCustomButtons"] = true
        b.prefs["steeringWheelCustomButon1ActionOriginal"] = " 7 "
        b.prefs["steeringWheelCustomButon2ActionOriginal"] = "99"
        b.automacoes.aoPartir(true)
        assertEquals(
            listOf("settings put system bean_sw_custom_key1_config 7"),
            b.shell.comandos.filter { it.startsWith("settings put system bean_sw") },
        )
        assertEquals(false, b.prefs.valores["enableSteeringWheelCustomButtons"])
    }

    @Test
    fun `H1 - sem a preferencia antiga nao mexe`() {
        val b = BancadaDasAutomacoes()
        b.prefs["steeringWheelCustomButon1ActionOriginal"] = "7"
        b.automacoes.aoPartir(true)
        assertTrue(b.shell.comandos.none { it.contains("bean_sw_custom") })
        assertNull(b.prefs.valores["enableSteeringWheelCustomButtons"])
    }

    // ── I. Ajustes do Android ──

    @Test
    fun `I1 D-A6 - soma o componente a lista existente`() {
        val b = BancadaDasAutomacoes()
        b.shell.saidas["settings get secure enabled_accessibility_services"] = "com.outro/.Servico"
        b.automacoes.ajustesDoAndroidNaPartida("br.com.app/.services.Acessibilidade", "br.com.app")
        assertEquals(
            listOf(
                "settings get secure enabled_accessibility_services",
                "settings put secure enabled_accessibility_services com.outro/.Servico:br.com.app/.services.Acessibilidade",
                "settings put secure accessibility_enabled 1",
                "pm grant br.com.app android.permission.WRITE_SECURE_SETTINGS",
            ),
            b.shell.comandos,
        )
    }

    @Test
    fun `I1 - ja na lista (forma longa) nao regrava - sem componente so concede a permissao`() {
        val b = BancadaDasAutomacoes()
        b.shell.saidas["settings get secure enabled_accessibility_services"] = "br.com.app/br.com.app.services.Acessibilidade"
        b.automacoes.ajustesDoAndroidNaPartida("br.com.app/.services.Acessibilidade", "br.com.app")
        assertTrue(b.shell.comandos.none { it.startsWith("settings put secure enabled_accessibility_services") })

        val semComponente = BancadaDasAutomacoes()
        semComponente.automacoes.ajustesDoAndroidNaPartida(null, "br.com.app")
        assertEquals(listOf("pm grant br.com.app android.permission.WRITE_SECURE_SETTINGS"), semComponente.shell.comandos)
    }

    @Test
    fun `I1 - sem a decisao D-A6 sobrescreve (como hoje)`() {
        val b = BancadaDasAutomacoes(DecisoesDasAutomacoes(somarAcessibilidade = false))
        b.shell.saidas["settings get secure enabled_accessibility_services"] = "com.outro/.Servico"
        b.automacoes.ajustesDoAndroidNaPartida("br.com.app/.services.Acessibilidade", "br.com.app")
        assertEquals(
            "settings put secure enabled_accessibility_services br.com.app/.services.Acessibilidade",
            b.shell.comandos.first(),
        )
    }
}
