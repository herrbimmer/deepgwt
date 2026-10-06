// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.carro

import br.com.deepgwt.licenca.Nivel
import br.com.deepgwt.licenca.Recurso
import br.com.deepgwt.veiculo.Sinais
import br.com.deepgwt.perfis.EscopoDoPerfil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Alpha 8.5: memória dos assistentes, liberar conector e coringas do volante. */
class FuncoesDoCarroTest {

    // ── Memória dos assistentes ──

    @Test
    fun `memoria devolve o que o carro reseta na partida e aprende o resto`() {
        val r = RegraDaMemoria
        assertEquals("primeira leitura vira lembrança", RegraDaMemoria.Acao.GUARDAR, r.aoMudar("0", null, null, 0))
        assertEquals(RegraDaMemoria.Acao.NADA, r.aoMudar("0", "0", 5_000, 0))
        assertEquals("carro religou logo depois de ligar", RegraDaMemoria.Acao.REAPLICAR, r.aoMudar("1", "0", 8_000, 0))
        assertEquals("fora da janela: é a pessoa", RegraDaMemoria.Acao.GUARDAR, r.aoMudar("1", "0", 90_000, 0))
        assertEquals("sem partida nesta sessão: é a pessoa", RegraDaMemoria.Acao.GUARDAR, r.aoMudar("1", "0", null, 0))
        assertEquals("não briga com o carro", RegraDaMemoria.Acao.DESISTIR, r.aoMudar("1", "0", 8_000, RegraDaMemoria.MAX_REAPLICAR))
        assertEquals("valor vazio não conta", RegraDaMemoria.Acao.NADA, r.aoMudar("  ", "0", 8_000, 0))
    }

    @Test
    fun `ready`() {
        assertTrue(RegraDaMemoria.readyLigado("1"))
        assertTrue(RegraDaMemoria.readyLigado("2"))
        listOf(null, "", "0", "-1").forEach { assertFalse(it.toString(), RegraDaMemoria.readyLigado(it)) }
    }

    @Test
    fun `memoria nunca mexe em frenagem`() {
        val proibidas = setOf(
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_PCS_STATE,
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_PPS_STATE,
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_FRONT_CROSS_LATERAL_BRAKE,
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_FAS_AUTO_EMERGENCY_TURN,
            Sinais.CAR_PARKING_SETTING_RADAR_SETTING_MEB_STATE,
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_RCW_STATE,
            Sinais.CAR_INTELLIGENT_DRIVING_SETTING_SRAS_DOOR_OPEN_WARNING
        )
        MemoriaDosAssistentes.CHAVES.forEach { k ->
            assertFalse(k.chave, k in proibidas)
            assertFalse(k.chave, k.chave.contains("brake") || k.chave.contains("pcs") || k.chave.contains("aeb"))
            assertTrue(k.chave, k.chave.startsWith("car.intelligent_driving_setting."))
        }
        assertTrue(Sinais.CAR_INTELLIGENT_DRIVING_SETTING_LAS_LDW_STATE in MemoriaDosAssistentes.CHAVES)
        assertTrue(Sinais.CAR_INTELLIGENT_DRIVING_SETTING_OVER_SPEED_WARNING_STATE in MemoriaDosAssistentes.CHAVES)
    }

    // ── Liberar conector ──

    @Test
    fun `conector encaixado`() {
        assertNull(PlanoDoConector.conectado(null, ""))
        assertEquals(false, PlanoDoConector.conectado("0", "0"))
        assertEquals(true, PlanoDoConector.conectado("0", "1"))
        assertEquals(true, PlanoDoConector.conectado("1", null))
    }

    @Test
    fun `liberar so troca de imediata para programada`() {
        fun d(gun: String?, modo: String?, trava: String? = null, esperando: Boolean = false) =
            PlanoDoConector.decidir(gun, null, modo, trava, esperando)
        assertEquals(PlanoDoConector.Decisao.Trocar("0", "1"), d("1", "0"))
        assertTrue(d("1", "1") is PlanoDoConector.Decisao.Recusar)
        assertTrue("valor desconhecido: não mexe", d("1", "2") is PlanoDoConector.Decisao.Recusar)
        assertTrue(d("1", "") is PlanoDoConector.Decisao.Recusar)
        assertTrue("sem conector", d("0", "0") is PlanoDoConector.Decisao.Recusar)
        assertTrue("HEV", d(null, "0") is PlanoDoConector.Decisao.Recusar)
        assertTrue("andando", d("1", "0", trava = "o carro está andando") is PlanoDoConector.Decisao.Recusar)
        assertTrue("já esperando", d("1", "0", esperando = true) is PlanoDoConector.Decisao.Recusar)
        val msg = (d("1", "7") as PlanoDoConector.Decisao.Recusar).motivo
        assertTrue("mostra o valor para o suporte", msg.contains("\"7\""))
    }

    // ── Coringas ──

    private fun plano(desejado: Boolean, tomado: Boolean, original: String?, atual: String?) =
        PlanoDoCoringa.planejar(desejado, PlanoDoCoringa.Estado(tomado, original), atual)

    @Test
    fun `desligado e nunca tomado nao toca no botao`() {
        assertEquals(PlanoDoCoringa.Passo.Nada, plano(false, false, null, null))
        assertEquals(PlanoDoCoringa.Passo.Nada, plano(false, false, "3", "3"))
    }

    @Test
    fun `tomar guarda a funcao do carro uma vez`() {
        assertEquals(PlanoDoCoringa.Passo.Tomar("4"), plano(true, false, null, "4"))
        assertEquals("chave que não existia", PlanoDoCoringa.Passo.Tomar(null), plano(true, false, null, null))
        assertEquals("já tomado e com 99: nada", PlanoDoCoringa.Passo.Nada, plano(true, true, "4", "99"))
    }

    @Test
    fun `menu do carro trocou a funcao com o botao tomado`() {
        assertEquals(PlanoDoCoringa.Passo.Retomar("6"), plano(true, true, "4", "6"))
    }

    @Test
    fun `desligar devolve exatamente o que era`() {
        assertEquals(PlanoDoCoringa.Passo.Devolver("4"), plano(false, true, "4", null))
        assertEquals("apaga a chave que não existia", PlanoDoCoringa.Passo.Devolver(null), plano(false, true, null, null))
    }

    @Test
    fun `valores do settings`() {
        assertNull(PlanoDoCoringa.normalizar("null\n"))
        assertNull(PlanoDoCoringa.normalizar(""))
        assertEquals("3", PlanoDoCoringa.normalizar(" 3\n"))
        assertTrue(PlanoDoCoringa.valorSeguro("12"))
        assertTrue(PlanoDoCoringa.valorSeguro(null))
        assertFalse("nada de shell no arquivo do instalador", PlanoDoCoringa.valorSeguro("1; reboot"))
        assertFalse(PlanoDoCoringa.valorSeguro("a"))
    }

    @Test
    fun `acoes do coringa`() {
        val ids = AcaoDoCoringa.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(AcaoDoCoringa.CARRO, AcaoDoCoringa.entries.first())
        assertEquals(AcaoDoCoringa.MACRO, AcaoDoCoringa.deId("macro"))
        assertNull(AcaoDoCoringa.deId("desconhecida"))
        // Alpha 9.2: os assistentes saíram. Um botão gravado com "alexa" (9.0) ou "voz" (9.1) não
        // acha ação e cai na função do carro (EscolhaDoCoringa.deJson), que devolve a do menu.
        assertNull(AcaoDoCoringa.deId("alexa"))
        assertNull(AcaoDoCoringa.deId("voz"))
        assertEquals(AcaoDoCoringa.Parametro.APP, AcaoDoCoringa.ABRIR_APP.parametro)
        // As ações que reusam as macros apontam para ids que existem no catálogo delas.
        val fonte = File("src/main/java/br/com/deepgwt/carro/AcoesDoCoringa.kt").readText()
        val usadas = Regex("macro\\(\"([a-z_]+)\"").findAll(fonte).map { it.groupValues[1] }.toSet()
        val catalogo = File("src/main/java/br/com/deepgwt/macros/MacroActionCatalog.kt").readText()
        assertTrue(usadas.isNotEmpty())
        usadas.forEach { assertTrue("ação de macro $it", catalogo.contains("\"$it\"")) }
    }

    @Test
    fun `niveis e perfis`() {
        assertEquals(Nivel.DRIVE, Recurso.MEMORIA_ADAS.nivel)
        assertEquals(Nivel.DRIVE, Recurso.CONECTOR.nivel)
        assertEquals(Nivel.SPORT, Recurso.CORINGAS.nivel)
        // A função do carro guardada é do CARRO: trocar de perfil não pode apagá-la.
        listOf("coringa1Original", "coringa2Original", "coringa1Tomado", "coringa2Tomado", "coringa1Tecla", "coringa2Tecla",
            "conectorModoParaVoltar", "conectorRegistro")
            .forEach { assertFalse(it, EscopoDoPerfil.preferenciaPertence(it)) }
        // A escolha de ação e a memória são da pessoa: viajam no perfil.
        listOf("coringasAtivos", "coringa1Acao", "adasMemoriaAtiva", "adasMemoriaValores")
            .forEach { assertTrue(it, EscopoDoPerfil.preferenciaPertence(it)) }
    }

    @Test
    fun `instalador devolve os coringas antes de remover`() {
        val lib = File("../instalador/src-tauri/src/lib.rs").readText()
        assertTrue(lib.contains("const CORINGAS_FILE: &str = \"${CoringasDoVolante.ARQUIVO_DO_INSTALADOR}\""))
        val i = lib.indexOf("async fn uninstall_deepgwt(")
        val script = lib.substring(i, lib.indexOf("pm uninstall {app_pkg}", i))
        assertTrue("devolve antes do pm uninstall", script.contains("settings put system \"\$K\" \"\$V\""))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
