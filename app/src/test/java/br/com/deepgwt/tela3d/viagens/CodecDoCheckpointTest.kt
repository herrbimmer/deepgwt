package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecDoCheckpointTest {

    private fun exemplo(): EstadoDaViagem = EstadoDaViagem().apply {
        aberta = true
        inicioMs = T0
        ultimoT = T0 + 5000
        odometroInicial = 100.5
        odometroUltimo = 100.6
        odometroKm = 0.1
        distanciaIntegrada = 0.123456789
        andandoMs = 4000
        paradoMs = 1000
        velocidadeMaxima = 33.3
        paradas = 2
        guiada = true
        kmPlanejado = 12.5
        segundosPlanejados = 900
        crusPrimeiros["car.basic.total_odometer"] = "100.5"
        crusUltimos["car.basic.total_odometer"] = "100.6"
    }

    @Test
    fun `os campos saem na ordem e com os nomes da especificacao`() {
        val linhas = CodecDoCheckpoint.codificar(exemplo()).split('\n').filter { it.isNotEmpty() }
        val nomes = linhas.map { it.substringBefore('=') }
        assertEquals(
            listOf(
                "open", "startMs", "lastT", "readyOffAt", "lastPointT", "odoStart", "odoLast", "lastOdoT", "odoKm",
                "odoJumps", "odoFromOpen", "distInt", "distIntAtOdo", "driveMs", "idleMs", "evKm", "iceKm", "evMs",
                "kwhOut", "kwhIn", "fuelL", "maxKmh", "stops", "wasMoving", "harshAccel", "harshBrake", "climbM",
                "altRef", "startLat", "startLon", "endLat", "endLon", "fuelPctStart", "fuelPctEnd", "guided",
                "plannedKm", "plannedS", "guideStartKm", "guideStartT", "guideEndKm", "guideEndT", "arrived",
                "lastRemainingM", "lastNavT", "socStart", "socEnd",
                "rf.car.basic.total_odometer", "rl.car.basic.total_odometer",
            ),
            nomes,
        )
        assertEquals("open=1", linhas[0])
        assertEquals("startMs=$T0", linhas[1])
        assertEquals("readyOffAt=0", linhas[3])
        assertEquals("odoStart=100.5", linhas[5])
        assertEquals("distInt=0.123456789", linhas[11])
        assertEquals("distIntAtOdo=0.0", linhas[12])
        assertEquals("evKm=0.0", linhas[15])
        assertEquals("altRef=NaN", linhas[27])
        assertEquals("startLat=NaN", linhas[28])
        assertEquals("guided=1", linhas[34])
        assertEquals("plannedKm=12.5", linhas[35])
        assertEquals("plannedS=900", linhas[36])
        assertEquals("lastRemainingM=NaN", linhas[42])
        assertEquals("rf.car.basic.total_odometer=100.5", linhas[46])
        assertEquals("rl.car.basic.total_odometer=100.6", linhas[47])
    }

    @Test
    fun `decodificar e recodificar devolve o mesmo texto`() {
        val texto = CodecDoCheckpoint.codificar(exemplo())
        assertEquals(texto, CodecDoCheckpoint.codificar(CodecDoCheckpoint.decodificar(texto)))
    }

    @Test
    fun `a decodificacao e tolerante`() {
        val e = CodecDoCheckpoint.decodificar("open=1\nstartMs=abc\nlastT=5\nfoo=1\n=7\nrf.k=v=w\n")
        assertTrue(e.aberta)
        assertEquals(0L, e.inicioMs)
        assertEquals(5L, e.ultimoT)
        assertEquals(mapOf("k" to "v=w"), e.crusPrimeiros)
        assertTrue(e.crusUltimos.isEmpty())
    }

    @Test
    fun `entrada nula e estado vazio`() {
        val e = CodecDoCheckpoint.decodificar(null)
        assertFalse(e.aberta)
        assertTrue(e.odometroInicial.isNaN())
    }

    @Test
    fun `booleanos so valem com o texto 1`() {
        val e = CodecDoCheckpoint.decodificar("open=true\nguided=2\nwasMoving=1\nodoFromOpen=1\narrived=0\n")
        assertFalse(e.aberta)
        assertFalse(e.guiada)
        assertTrue(e.armouParada)
        assertTrue(e.partiuComHodometro)
        assertFalse(e.chegou)
    }

    @Test
    fun `valor ilegivel deixa o padrao e os padroes NaN`() {
        val e = CodecDoCheckpoint.decodificar("open=1\nodoStart=xx\nstops=1.5\n")
        assertTrue(e.odometroInicial.isNaN())
        assertEquals(0, e.paradas)
        assertTrue(e.latInicial.isNaN())
        assertTrue(e.tanqueInicial.isNaN())
        assertTrue(e.bateriaFinal.isNaN())
        assertTrue(e.ultimaDistanciaRestanteM.isNaN())
    }

    @Test
    fun `quebra de linha no valor vira espaco`() {
        val e = EstadoDaViagem().apply {
            aberta = true
            crusUltimos["k"] = "a\nb"
        }
        val d = CodecDoCheckpoint.decodificar(CodecDoCheckpoint.codificar(e))
        assertEquals("a b", d.crusUltimos["k"])
    }

    @Test
    fun `numeros especiais fazem a viagem de ida e volta`() {
        val e = EstadoDaViagem().apply {
            aberta = true
            kwhGasto = 1.0E-4
            distanciaIntegrada = 1234567.0
        }
        val d = CodecDoCheckpoint.decodificar(CodecDoCheckpoint.codificar(e))
        assertEquals(1.0E-4, d.kwhGasto, 0.0)
        assertEquals(1234567.0, d.distanciaIntegrada, 0.0)
    }
}
