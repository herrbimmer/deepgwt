// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A costura do BLE (casos 18–32) com transporte e relógio falsos. */
class ConexaoBleTest {

    private val mac = "AA:BB:CC:DD:EE:FF"
    private val tempo = TempoFalso()
    private val transporte = TransporteFalso()
    private val conexao = ConexaoBle("default", transporte, tempo, tempo)

    private val estado get() = conexao.estado.value
    private val q1 = Quadros.corDmx(CorLed(1, 2, 3))
    private val q2 = Quadros.corBle(CorLed(4, 5, 6))
    private val q3 = Quadros.brilhoDmx(50)

    private fun conectarAteOFim() {
        conexao.conectar(mac)
        transporte.conectou()
        transporte.descobriu()
        assertTrue(estado is EstadoDaConexao.Conectada)
    }

    @Test
    fun `conecta, descobre FFE1 e fica Conectada`() {
        conexao.conectar(mac)
        assertEquals(EstadoDaConexao.Conectando(mac), estado)
        assertEquals(listOf(12_000L), tempo.pendentes())
        transporte.conectou()
        assertEquals(1, transporte.ultimo.descobertas)
        assertEquals(listOf(10_000L), tempo.pendentes())
        transporte.descobriu()
        assertEquals(EstadoDaConexao.Conectada(mac, "LEDCAR-01-00DD"), estado)
        assertTrue(tempo.pendentes().isEmpty())
        assertEquals("0000ffe1-0000-1000-8000-00805f9b34fb", conexao.diagnostico.value.caracteristica)
    }

    @Test
    fun `reconexao espera 1, 2, 5, 10 e 10 s e zera ao conectar`() {
        transporte.falharAoAbrir = true
        conexao.conectar(mac)
        val esperas = mutableListOf<Long>()
        repeat(5) { i ->
            val r = estado as EstadoDaConexao.Reconectando
            assertEquals(i + 1, r.tentativa)
            esperas += r.esperaMs
            tempo.avancar(r.esperaMs)
        }
        assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 10_000L), esperas)
        transporte.falharAoAbrir = false
        tempo.avancar((estado as EstadoDaConexao.Reconectando).esperaMs)
        assertEquals(EstadoDaConexao.Conectando(mac), estado)
        transporte.conectou()
        transporte.descobriu()
        assertTrue(estado is EstadoDaConexao.Conectada)

        transporte.caiu(status = 8)
        assertEquals(EstadoDaConexao.Reconectando(1, 1_000), estado)
        assertEquals("GATT 8 em $mac", conexao.diagnostico.value.ultimoErro)
        tempo.avancar(1_000)
        assertEquals(EstadoDaConexao.Conectando(mac), estado)
    }

    @Test
    fun `prazo de 12 s para conectar`() {
        conexao.conectar(mac)
        tempo.avancar(11_999)
        assertEquals(EstadoDaConexao.Conectando(mac), estado)
        tempo.avancar(1)
        assertEquals(1, transporte.abertos[0].fechado)
        assertEquals(EstadoDaConexao.Reconectando(1, 1_000), estado)
        assertTrue(conexao.diagnostico.value.ultimoErro!!.contains("Timeout"))
    }

    @Test
    fun `prazo de 10 s para descobrir os servicos`() {
        conexao.conectar(mac)
        tempo.avancar(5_000)
        transporte.conectou()
        tempo.avancar(9_999)
        assertEquals(EstadoDaConexao.Conectando(mac), estado)
        tempo.avancar(1)
        assertEquals(1, transporte.ultimo.fechado)
        assertEquals(EstadoDaConexao.Reconectando(1, 1_000), estado)
    }

    @Test
    fun `sem FFE1 vira erro e reconecta`() {
        conexao.conectar(mac)
        transporte.ultimo.temCaracteristica = false
        transporte.conectou()
        transporte.descobriu()
        assertEquals("característica FFE1 não encontrada", conexao.diagnostico.value.ultimoErro)
        assertEquals(1, transporte.ultimo.fechado)
        assertEquals(EstadoDaConexao.Reconectando(1, 1_000), estado)
    }

    @Test
    fun `avisos atrasados sao ignorados e o GATT velho e fechado`() {
        val outro = "11:22:33:44:55:66"
        conexao.conectar(mac)
        val velho = transporte.ultimo
        conexao.conectar(outro)
        val novo = transporte.ultimo
        assertEquals(1, velho.fechado)
        transporte.conectou(velho)
        assertEquals(EstadoDaConexao.Conectando(outro), estado)
        assertEquals(2, velho.fechado)
        assertEquals(0, novo.descobertas)
        transporte.conectou(novo)
        transporte.descobriu(novo)
        assertTrue(estado.conectadaA(outro))
        transporte.caiu(velho)
        assertTrue(estado.conectadaA(outro))
        assertTrue(tempo.pendentes().isEmpty())
    }

    @Test
    fun `desconexao manual nao reconecta`() {
        conectarAteOFim()
        val g = transporte.ultimo
        conexao.desconectar()
        assertEquals(EstadoDaConexao.Parada, estado)
        assertEquals(1, g.fechado)
        assertTrue(tempo.pendentes().isEmpty())
        transporte.caiu(g, status = 0)
        assertEquals(EstadoDaConexao.Parada, estado)
        tempo.avancar(60_000)
        assertEquals(1, transporte.abertos.size)
    }

    @Test
    fun `queda com a reconexao desligada leva a Parada`() {
        conexao.conectar(mac, reconectar = false)
        transporte.conectou()
        transporte.descobriu()
        transporte.caiu(status = 8)
        assertEquals(EstadoDaConexao.Parada, estado)
        assertTrue(tempo.pendentes().isEmpty())
    }

    @Test
    fun `sem permissao, Bluetooth desligado ou endereco invalido nao tentam de novo`() {
        transporte.permissao = false
        conexao.conectar(mac)
        assertEquals(EstadoDaConexao.Erro("Permissão Bluetooth ausente"), estado)
        assertTrue(tempo.pendentes().isEmpty())

        transporte.permissao = true
        transporte.ligado = false
        conexao.conectar(mac)
        assertTrue(estado is EstadoDaConexao.Erro)
        assertTrue(tempo.pendentes().isEmpty())

        transporte.ligado = true
        conexao.conectar("nao-e-um-endereco")
        assertTrue(estado is EstadoDaConexao.Erro)
        assertTrue(tempo.pendentes().isEmpty())
        assertTrue(transporte.abertos.isEmpty())
    }

    @Test
    fun `ligar o Bluetooth tenta de novo`() {
        transporte.ligado = false
        conexao.conectar(mac)
        assertTrue(estado is EstadoDaConexao.Erro)
        transporte.ligado = true
        conexao.bluetoothLigou()
        assertEquals(EstadoDaConexao.Conectando(mac), estado)
    }

    @Test
    fun `ligar o Bluetooth nao mexe numa conexao parada a mao`() {
        conectarAteOFim()
        conexao.desconectar()
        conexao.bluetoothLigou()
        assertEquals(EstadoDaConexao.Parada, estado)
        assertEquals(1, transporte.abertos.size)
    }

    @Test
    fun `escritas respeitam 50 ms entre uma e outra`() = runBlocking {
        conectarAteOFim()
        val g = transporte.ultimo
        val instantes = mutableListOf<Long>()
        g.aoEscrever = { instantes += tempo.instante }
        val quadros = Quadros.cor(CorLed(1, 2, 3), SaidaDaLuz.AMBAS, OrdemDeCor.RBG, OrdemDeCor.RGB) + Quadros.brilho(50, SaidaDaLuz.AMBAS)
        assertTrue(conexao.enviar(quadros))
        assertEquals(quadros.map { Hex.deBytes(it) }, g.escritos)
        assertEquals(listOf(0L, 50L, 100L, 150L), instantes)
        assertEquals(Hex.deBytes(quadros.last()), conexao.diagnostico.value.ultimoQuadro)
    }

    @Test
    fun `fila serializada na ordem de quem pediu a vez`() = runBlocking {
        conectarAteOFim()
        val g = transporte.ultimo
        val instantes = mutableListOf<Long>()
        g.aoEscrever = { instantes += tempo.instante }
        val a = launch { conexao.enviar(listOf(q1, q2)) }
        val b = launch { conexao.enviar(listOf(q3)) }
        joinAll(a, b)
        assertEquals(listOf(q1, q2, q3).map { Hex.deBytes(it) }, g.escritos)
        assertEquals(listOf(0L, 50L, 100L), instantes)
    }

    @Test
    fun `algum aceito basta e recusa nao derruba o estado`() = runBlocking {
        conectarAteOFim()
        val g = transporte.ultimo
        var vezes = 0
        g.recusar = { vezes++ == 0 }
        assertTrue(conexao.enviar(listOf(q1, q2)))
        assertEquals(listOf(Hex.deBytes(q2)), g.escritos)
        assertTrue(estado is EstadoDaConexao.Conectada)
        assertTrue(conexao.diagnostico.value.ultimoErro!!.contains("recusada"))
        g.aceitar = false
        assertFalse(conexao.enviar(listOf(q1, q2)))
        assertTrue(estado is EstadoDaConexao.Conectada)
    }

    @Test
    fun `sem conexao nao escreve`() = runBlocking {
        assertFalse(conexao.enviar(listOf(q1)))
        assertEquals("BLE não conectado", conexao.diagnostico.value.ultimoErro)
    }

    @Test
    fun `escrita com resposta espera a confirmacao`() = runBlocking {
        conexao.conectar(mac)
        transporte.ultimo.semResposta = false
        transporte.conectou()
        transporte.descobriu()
        val g = transporte.ultimo
        g.aoEscrever = { transporte.avisos.escritaConfirmada(g, STATUS_SUCESSO) }
        assertTrue(conexao.enviar(listOf(q1, q2)))
        assertEquals(2, g.escritos.size)
        assertNull(conexao.diagnostico.value.ultimoErro)
        assertEquals(false, conexao.diagnostico.value.semResposta)
    }

    @Test
    fun `conexoes por chave sao independentes`() {
        val outroTransporte = TransporteFalso()
        val porta = ConexaoBle("door_DRIVER", outroTransporte, tempo, tempo)
        conectarAteOFim()
        porta.conectar("11:22:33:44:55:66")
        assertTrue(estado is EstadoDaConexao.Conectada)
        assertEquals(EstadoDaConexao.Conectando("11:22:33:44:55:66"), porta.estado.value)
        porta.desconectar()
        assertEquals(EstadoDaConexao.Parada, porta.estado.value)
        assertTrue(estado is EstadoDaConexao.Conectada)
        assertEquals(0, transporte.ultimo.fechado)
    }

    @Test
    fun `rotulos em portugues`() {
        assertEquals("Parada", EstadoDaConexao.Parada.rotulo)
        assertTrue(EstadoDaConexao.Conectando(mac).rotulo.startsWith("Conectando"))
        assertTrue(EstadoDaConexao.Conectada(mac, "LEDCAR").rotulo.startsWith("Conectada"))
        assertTrue(EstadoDaConexao.Reconectando(2, 2_000).rotulo.contains("tentativa 2"))
        assertEquals("Erro: x", EstadoDaConexao.Erro("x").rotulo)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
