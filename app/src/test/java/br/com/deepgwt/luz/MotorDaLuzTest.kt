// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A costura do carro (casos 33–53) com fita, carro e relógio falsos. Saída DMX e ordens padrão,
 * para comparar com as sequências da V13.
 */
class MotorDaLuzTest {

    private val mac = "AA:BB:CC:DD:EE:FF"

    private val vermelho = "7B0007FF000000FFBF"
    private val apagado = "7B000700000000FFBF"
    private val branco = "7B0007FFFFFF00FFBF"
    private val verde = "7B00070000FF00FFBF"
    private val ambar = "7B0007FF3BB000FFBF"
    private val azulSuave = "7B000750FF7800FFBF"
    private val brilho100 = "7BFF01206400FFFFBF"

    private class Cenario(val escopo: CoroutineScope) {
        val tempo = TempoFalso()
        val fita = FitaFalsa(tempo)
        val carro = CarroFalso()
        val musica = MusicaFalsa()
        val prefs = mutableMapOf<String, Any?>()
        lateinit var motor: MotorDaLuz

        suspend fun rodar(vezes: Int = 400) = repeat(vezes) { yield() }

        fun limpar() = fita.quadros.clear()

        /** O último quadro enviado. */
        fun ultimo(): String = fita.quadros.last().second
    }

    private fun cenario(
        conectada: Boolean = true,
        vararg prefs: Pair<String, Any?>,
        bloco: suspend Cenario.() -> Unit,
    ) = runBlocking {
        val c = Cenario(this)
        c.prefs += mapOf(
            "ambientLightBleEnabled" to true,
            "ambientLightBleDeviceMac" to mac,
            "ambientLightOutput" to "DMX",
        )
        c.prefs += prefs
        c.carro.cache[ChavesDoCarro.MODO_DE_CONDUCAO] = "0"
        if (conectada) c.fita.estadoMutavel.value = EstadoDaConexao.Conectada(mac, null)
        c.motor = MotorDaLuz(
            ajustes = { AjustesDaLuz.ler(FonteDeMapa(c.prefs)) },
            gravarFita = { e, n ->
                c.prefs["ambientLightBleDeviceMac"] = e
                c.prefs["ambientLightBleDeviceName"] = n
            },
            fita = c.fita,
            carro = c.carro,
            escopo = this,
            relogio = c.tempo,
        ).also { it.musica = c.musica }
        try {
            c.bloco()
        } finally {
            c.motor.encerrar()
        }
    }

    @Test
    fun `iniciar com dinamica liga os ouvintes e aplica a cor do modo`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        assertEquals(2, carro.ouvintes.size)
        assertTrue(carro.acompanhadas.containsAll(ChavesDoCarro.ALERTA + ChavesDoCarro.MODO_DE_CONDUCAO))
        assertEquals(ModoDeConducao.NORMAL, motor.modoAtual)
        assertEquals(brilho100, fita.hex().first())
        assertEquals(branco, ultimo())
        assertEquals(listOf("parar"), musica.chamadas)
    }

    @Test
    fun `pisca-alerta pulsa vermelho a cada 300 ms e volta a base`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        limpar()
        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "1")
        rodar(60)
        val pulso = fita.quadros.toList()
        assertTrue("pulsou pouco: $pulso", pulso.size >= 4)
        assertEquals(listOf(vermelho, apagado, vermelho, apagado), pulso.take(4).map { it.second })
        assertEquals(listOf(300L, 300L, 300L), pulso.take(4).zipWithNext { a, b -> b.first - a.first })
        assertEquals(CamadaDaLuz.PISCA_ALERTA, motor.camada)

        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "0")
        rodar()
        assertEquals(CamadaDaLuz.NENHUMA, motor.camada)
        assertEquals(branco, ultimo())
        val depois = fita.quadros.size
        rodar()
        assertEquals("o pulso continuou depois de desligar", depois, fita.quadros.size)
    }

    @Test
    fun `seta so pulsa com a reacao ligada`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        limpar()
        carro.avisar(ChavesDoCarro.SETA_ESQUERDA, "1")
        rodar()
        assertTrue("a seta mexeu na luz com a reação desligada", fita.quadros.isEmpty())

        prefs["ambientLightReactTurnSignal"] = true
        carro.avisar(ChavesDoCarro.SETA_ESQUERDA, "1")
        rodar(60)
        val pulso = fita.quadros.toList()
        assertEquals(listOf(ambar, apagado, ambar), pulso.take(3).map { it.second })
        assertEquals(listOf(350L, 350L), pulso.take(3).zipWithNext { a, b -> b.first - a.first })
    }

    @Test
    fun `aviso de desligou perdido nao prende o pulso`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        // O pisca-alerta já estava ligado no cache (nunca chegou aviso dele).
        carro.cache[ChavesDoCarro.PISCA_ALERTA] = "1"
        motor.iniciar()
        rodar(60)
        assertEquals(CamadaDaLuz.PISCA_ALERTA, motor.camada)
        assertTrue(fita.hex().contains(vermelho))
        // Desligou, mas o aviso se perdeu: só o cache mudou.
        carro.cache[ChavesDoCarro.PISCA_ALERTA] = "0"
        rodar()
        assertEquals(CamadaDaLuz.NENHUMA, motor.camada)
        assertEquals(branco, ultimo())
        val depois = fita.quadros.size
        rodar()
        assertEquals(depois, fita.quadros.size)
    }

    @Test
    fun `o valor do aviso vence o cache velho`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "1")
        rodar(40)
        // O motor de verdade avisa antes de gravar: aqui o cache ainda diria "1" durante o aviso.
        carro.ouvintes.toList().forEach { it.mudou(ChavesDoCarro.PISCA_ALERTA, "0") }
        rodar()
        assertEquals(CamadaDaLuz.NENHUMA, motor.camada)
        assertEquals(branco, ultimo())
    }

    @Test
    fun `modo de conducao muda a base so sem alerta`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        limpar()
        carro.avisar(ChavesDoCarro.MODO_DE_CONDUCAO, "1")
        rodar()
        assertEquals(ModoDeConducao.SPORT, motor.modoAtual)
        assertEquals(listOf(vermelho), fita.hex())

        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "1")
        rodar(40)
        limpar()
        carro.avisar(ChavesDoCarro.MODO_DE_CONDUCAO, "2")
        rodar(40)
        assertEquals(ModoDeConducao.ECO, motor.modoAtual)
        assertFalse("a base mudou por cima do alerta", fita.hex().contains(verde))
        assertTrue(fita.hex().all { it == vermelho || it == apagado })

        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "0")
        rodar()
        assertEquals(verde, ultimo())
    }

    @Test
    fun `farol pinta ambar fixo`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        limpar()
        carro.avisar("car.basic.low_beam_light_status", "1")
        rodar()
        assertEquals(CamadaDaLuz.FAROL, motor.camada)
        assertEquals(listOf(ambar), fita.hex())
    }

    @Test
    fun `musica ligada ignora as reacoes e o clima`() = cenario(
        true,
        "ambientLightSyncDriveMode" to true,
        "ambientLightMusicAnimationEnabled" to true,
    ) {
        motor.iniciar()
        rodar()
        assertEquals(listOf(brilho100), fita.hex())
        assertEquals(listOf("ligar GRAVES"), musica.chamadas)
        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "1")
        motor.piscarPeloClima()
        rodar()
        assertEquals(listOf(brilho100), fita.hex())
        assertEquals(CamadaDaLuz.NENHUMA, motor.camada)
    }

    @Test
    fun `boas-vindas terminam na cor manual com a dinamica desligada`() = cenario(
        false,
        "ambientLightAnimationsEnabled" to true,
        "ambientLightManualHue" to 120f,
    ) {
        motor.iniciar()
        rodar()
        assertEquals(1, fita.pedidosDeConexao)
        val q = fita.quadros.toList()
        assertEquals(brilho100, q[0].second)
        assertEquals(azulSuave, q[1].second)
        assertEquals(branco, q[2].second)
        assertEquals(300L, q[2].first - q[1].first)
        val transicao = q.drop(3)
        assertEquals(20, transicao.size)
        assertEquals(300L, transicao[0].first - q[2].first)
        assertTrue(transicao.zipWithNext { a, b -> b.first - a.first }.all { it == 30L })
        assertEquals(Transicao.passos(Cores.BRANCO, Cores.VERDE).map { Hex.deBytes(Quadros.corDmx(it)) }, transicao.map { it.second })
        assertEquals(verde, ultimo())
        assertEquals(Cores.VERDE, motor.ultimaCor)
    }

    @Test
    fun `ja conectado sem dinamica manda a cor manual uma vez`() = cenario(true, "ambientLightManualHue" to 240f) {
        motor.iniciar()
        rodar()
        assertEquals(listOf(brilho100, "7B000700FF0000FFBF"), fita.hex())
    }

    @Test
    fun `piscada do clima e volta a base`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        limpar()
        motor.piscarPeloClima()
        rodar()
        assertEquals(listOf(vermelho, apagado, vermelho, apagado, branco), fita.hex())
        val t = fita.quadros.map { it.first - fita.quadros[0].first }
        assertEquals(listOf(0L, 130L, 220L, 350L, 440L), t)
    }

    @Test
    fun `clima nunca por cima de um alerta`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        carro.avisar(ChavesDoCarro.PISCA_ALERTA, "1")
        rodar(20)
        limpar()
        motor.piscarPeloClima()
        rodar(40)
        assertTrue(fita.hex().all { it == vermelho || it == apagado })
        assertEquals(CamadaDaLuz.PISCA_ALERTA, motor.camada)
    }

    @Test
    fun `Sport animado pulsa e termina vermelho fixo`() = cenario(
        true,
        "ambientLightSyncDriveMode" to true,
        "ambientLightAnimationsEnabled" to true,
    ) {
        motor.iniciar()
        rodar()
        limpar()
        carro.avisar(ChavesDoCarro.MODO_DE_CONDUCAO, "1")
        rodar(2_000)
        val q = fita.hex()
        assertEquals(vermelho, q.last())
        // Pares inteiros de transições (20 + 20 quadros) e o vermelho fixo no fim.
        assertEquals(0, (q.size - 1) % 40)
        assertTrue("pares de pulsos: ${(q.size - 1) / 40}", (q.size - 1) / 40 >= 2)
        assertEquals(Hex.deBytes(Quadros.corDmx(Transicao.passo(Cores.VERMELHO_FRACO, Cores.VERMELHO, 1))), q.first())
    }

    @Test
    fun `iniciar sem fita para a musica e desconecta`() = cenario(true, "ambientLightBleDeviceMac" to "") {
        motor.iniciar()
        rodar()
        assertEquals(1, fita.desconexoes)
        assertEquals(listOf("parar"), musica.chamadas)
        assertTrue(fita.quadros.isEmpty())
    }

    @Test
    fun `testar cor e enviar hex`() = cenario(true) {
        motor.testarCor(CorLed(10, 20, 30))
        rodar()
        assertEquals(listOf("7B00070A1E1400FFBF"), fita.hex())
        motor.enviarHex("zz")
        rodar()
        assertTrue(fita.erros.single().contains("inválido"))
        assertTrue(fita.estado.value is EstadoDaConexao.Conectada)
        motor.enviarHex("7B 00 07")
        rodar()
        assertEquals("7B0007", ultimo())
    }

    @Test
    fun `conectar grava a fita e nao aplica base com a dinamica ligada`() = cenario(
        false,
        "ambientLightSyncDriveMode" to true,
        "ambientLightBleDeviceMac" to null,
    ) {
        motor.conectar("11:22:33:44:55:66", "LEDCAR-01-00DD")
        rodar()
        assertEquals("11:22:33:44:55:66", prefs["ambientLightBleDeviceMac"])
        assertTrue(fita.estado.value.conectadaA("11:22:33:44:55:66"))
        assertEquals(listOf(brilho100), fita.hex())
        assertTrue("conectar não mexe nos ouvintes", carro.ouvintes.isEmpty())
    }

    @Test
    fun `parar solta os ouvintes e desconecta`() = cenario(true, "ambientLightSyncDriveMode" to true) {
        motor.iniciar()
        rodar()
        motor.parar()
        assertTrue(carro.ouvintes.isEmpty())
        assertEquals(1, fita.desconexoes)
        assertEquals("parar", musica.chamadas.last())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
