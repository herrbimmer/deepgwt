package br.com.deepgwt.tela3d.viagens

import br.com.deepgwt.tela3d.viagens.ChavesDeSinais as K
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NucleoDoGravadorTest {
    private val banco = BancoJdbc.novo()
    private val a = ArmazenamentoDeViagens(banco)
    private val agendamentos = ArrayList<Long>()
    private val registros = ArrayList<String>()

    private fun novoNucleo(armazenamento: ArmazenamentoDeViagens? = a): NucleoDoGravador =
        NucleoDoGravador({ registros.add(it) }, { agendamentos.add(it) }).also { it.armazenamento = armazenamento }

    private fun NucleoDoGravador.dirigir(segundos: Int, kmh: Double = 60.0, comPosicao: Boolean = false) {
        aoSinal(K.PRONTO, "1", T0)
        aoSinal(K.VELOCIDADE, kmh.toString(), T0)
        for (s in 1..segundos) {
            val t = T0 + s * 1000L
            if (comPosicao) aoPosicao(-22.9 + s * 1e-4, -43.2, 10.0, 5.0, t)
            tick(t, 1000)
        }
    }

    @Test
    fun `viagem inteira vai para o banco e some o checkpoint`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.dirigir(120, comPosicao = true)
        assertTrue(n.viagemAberta)
        assertNotNull(a.lerCheckpoint())
        n.aoSinal(K.PRONTO, "0", T0 + 120_000)
        n.tick(T0 + 120_000 + 600_000, 1000)
        assertFalse(n.viagemAberta)
        assertEquals("", n.textoAoVivo)
        assertNull(a.lerCheckpoint())
        val v = json(a.viagensEmJson(0, 10)).lista().single().obj()
        assertEquals(T0, v.longo("startMs"))
        assertEquals(T0 + 120_000, v.longo("endMs"))
        assertTrue(v.real("km")!! > 1.9)
        val rota = json(a.viagemEmJson(T0, 600)!!).obj()["route"]!!.lista()
        assertTrue(rota.size > 100)
        assertEquals(1, banco.consultar("SELECT COUNT(*) AS n FROM days WHERE trips = 1").single().longo("n")!!.toInt())
        assertEquals(listOf(NucleoDoGravador.ATRASO_DA_VARREDURA_APOS_VIAGEM_MS), agendamentos)
        // os 120 pontos foram para o banco
        assertEquals(120L, banco.consultar("SELECT COUNT(*) AS n FROM trip_points").single().longo("n"))
        assertTrue(n.pontosEmMemoria(T0).isEmpty())
    }

    @Test
    fun `o checkpoint descarrega os pontos pendentes e o resto fica na memoria`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.dirigir(68)
        // checkpoints em 15, 30, 45 e 60 s descarregaram 60 pontos; os 8 seguintes ainda estão só na memória
        assertEquals(60L, banco.consultar("SELECT COUNT(*) AS n FROM trip_points").single().longo("n"))
        assertEquals(8, n.pontosEmMemoria(T0).size)
        assertTrue(n.pontosEmMemoria(T0 + 1).isEmpty())
    }

    @Test
    fun `checkpoint a cada 15 s com viagem aberta`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.aoSinal(K.PRONTO, "1", T0)
        val primeiro = banco.consultar("SELECT updated_ms FROM open_trip").single().longo("updated_ms")
        assertEquals(T0, primeiro)
        n.tick(T0 + 14_000, 1000)
        assertEquals(T0, banco.consultar("SELECT updated_ms FROM open_trip").single().longo("updated_ms"))
        n.tick(T0 + 15_000, 1000)
        assertEquals(T0 + 15_000, banco.consultar("SELECT updated_ms FROM open_trip").single().longo("updated_ms"))
    }

    @Test
    fun `salvar logo grava o checkpoint na hora`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.aoSinal(K.PRONTO, "1", T0)
        n.salvarLogo(T0 + 3000)
        assertEquals(T0 + 3000, banco.consultar("SELECT updated_ms FROM open_trip").single().longo("updated_ms"))
    }

    @Test
    fun `viagem curta descartada apaga pontos e checkpoint`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.dirigir(35, kmh = 5.0)
        n.aoSinal(K.PRONTO, "0", T0 + 35_000)
        n.tick(T0 + 35_000 + 600_000, 1000)
        assertEquals(0L, banco.consultar("SELECT COUNT(*) AS n FROM trips").single().longo("n"))
        assertEquals(0L, banco.consultar("SELECT COUNT(*) AS n FROM trip_points").single().longo("n"))
        assertNull(a.lerCheckpoint())
        assertTrue(agendamentos.isEmpty())
    }

    @Test
    fun `reinicio retoma a viagem do checkpoint`() {
        val n1 = novoNucleo()
        n1.iniciar(T0)
        n1.dirigir(90)
        n1.salvarLogo(T0 + 90_000)
        // o app morreu; outro nucleo sobre o mesmo banco
        val n2 = novoNucleo()
        n2.iniciar(T0 + 100_000)
        assertTrue(n2.viagemAberta)
        assertTrue(n2.textoAoVivo.contains("\"startMs\":$T0"))
        n2.aoSinal(K.PRONTO, "1", T0 + 101_000)
        assertTrue(n2.viagemAberta)
        n2.tick(T0 + 102_000, 1000)
        n2.aoSinal(K.PRONTO, "0", T0 + 103_000)
        n2.tick(T0 + 103_000 + 600_000, 1000)
        val v = json(a.viagensEmJson(0, 10)).lista().single().obj()
        assertEquals(T0, v.longo("startMs"))
        assertTrue(v.real("km")!! > 1.4)
    }

    @Test
    fun `reinicio depois de 10 min fecha a viagem retomada`() {
        val n1 = novoNucleo()
        n1.iniciar(T0)
        n1.dirigir(90)
        n1.salvarLogo(T0 + 90_000)
        val n2 = novoNucleo()
        n2.iniciar(T0 + 90_000 + 700_000)
        assertFalse(n2.viagemAberta)
        assertEquals(1L, banco.consultar("SELECT COUNT(*) AS n FROM trips").single().longo("n"))
        assertNull(a.lerCheckpoint())
    }

    @Test
    fun `o nivel parado vem da ultima viagem gravada`() {
        val n1 = novoNucleo()
        n1.iniciar(T0)
        n1.aoSinal(K.NIVEL_DO_TANQUE, "30", T0 - 1000)
        n1.dirigir(120)
        n1.aoSinal(K.PRONTO, "0", T0 + 120_000)
        n1.tick(T0 + 120_000 + 600_000, 1000)
        // reinicia o app, o carro enche o tanque, e o motorista religa
        val n2 = novoNucleo()
        n2.iniciar(T0 + 1_000_000)
        n2.aoSinal(K.PRONTO, "0", T0 + 1_000_000)
        n2.aoSinal(K.NIVEL_DO_TANQUE, "40", T0 + 1_100_000)
        n2.aoSinal(K.PRONTO, "1", T0 + 2_000_000)
        // a candidata sai quando acaba a janela de acomodação de 180 s
        n2.tick(T0 + 2_000_000 + 180_000, 1000)
        val p = banco.consultar("SELECT * FROM trip_stops").single()
        assertEquals("refuel", p.texto("kind"))
        assertEquals(30.0, p.real("level_before")!!, 0.0)
        assertEquals(40.0, p.real("level_after")!!, 0.0)
    }

    @Test
    fun `sem banco o gravador segue e a ponte devolve falha`() {
        val n = novoNucleo(null)
        n.iniciar(T0)
        n.dirigir(40)
        assertTrue(n.viagemAberta)
        assertTrue(n.textoAoVivo.contains("\"open\":true"))
        assertEquals(40, n.pontosEmMemoria(T0).size)
        n.aoSinal(K.PRONTO, "0", T0 + 40_000)
        n.tick(T0 + 40_000 + 600_000, 1000)
        assertFalse(n.viagemAberta)
    }

    @Test
    fun `texto ao vivo e recalculado a cada tick`() {
        val n = novoNucleo()
        n.iniciar(T0)
        assertEquals("", n.textoAoVivo)
        n.dirigir(10)
        val j = json(n.textoAoVivo).obj()
        assertEquals(true, (j["open"] as kotlinx.serialization.json.JsonPrimitive).content.toBoolean())
        assertEquals(T0, j.longo("startMs"))
        assertEquals(T0 + 10_000, j.longo("endMs"))
        for (chave in listOf(
            "km", "kmIntegrated", "driveMs", "idleMs", "evKm", "evMs", "kwhOut", "kwhIn", "fuelL", "maxKmh", "stops",
            "harshAccel", "harshBrake", "climbM", "guided", "arrived", "plannedKm", "plannedS", "guidedKm", "guidedS",
            "kmPerL", "kwhPer100Km", "evShare", "avgKmh", "fuelPctStart", "fuelPctEnd", "socStart", "socEnd",
        )) {
            assertTrue(chave, j.containsKey(chave))
        }
        assertTrue(j.nulo("plannedKm"))
        assertTrue(j.nulo("guidedKm"))
        assertTrue(j.nulo("fuelPctStart"))
    }

    @Test
    fun `finalizar fecha e abre outra com READY ligado`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.dirigir(100)
        n.finalizarViagem(T0 + 100_000)
        assertTrue(n.viagemAberta)
        assertEquals(1L, banco.consultar("SELECT COUNT(*) AS n FROM trips").single().longo("n"))
        assertTrue(n.textoAoVivo.contains("\"startMs\":${T0 + 100_000}"))
        assertNotNull(a.lerCheckpoint())
    }

    @Test
    fun `a parada vai para o banco na hora`() {
        val n = novoNucleo()
        n.iniciar(T0)
        n.aoSinal(K.NIVEL_DO_TANQUE, "30", T0)
        n.dirigir(120)
        n.aoSinal(K.PRONTO, "0", T0 + 120_000)
        n.aoSinal(K.NIVEL_DO_TANQUE, "40", T0 + 180_000)
        n.aoSinal(K.PRONTO, "0", T0 + 181_000)
        assertEquals(0L, banco.consultar("SELECT COUNT(*) AS n FROM trip_stops").single().longo("n"))
        n.aoSinal(K.PRONTO, "1", T0 + 900_000)
        assertEquals(1L, banco.consultar("SELECT COUNT(*) AS n FROM trip_stops").single().longo("n"))
    }
}
