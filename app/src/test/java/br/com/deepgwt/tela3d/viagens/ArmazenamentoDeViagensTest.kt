package br.com.deepgwt.tela3d.viagens

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArmazenamentoDeViagensTest {

    private lateinit var banco: BancoJdbc
    private lateinit var a: ArmazenamentoDeViagens
    private lateinit var fusoOriginal: TimeZone

    @Before
    fun preparar() {
        fusoOriginal = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        banco = BancoJdbc.novo()
        a = ArmazenamentoDeViagens(banco)
    }

    @After
    fun limpar() {
        TimeZone.setDefault(fusoOriginal)
    }

    // --- Fixture da especificação (V-5) ---

    private fun viagem(inicio: Long, dia: String, km: Double, drive: Long, fuel: Double, out: Double, entrada: Double, ev: Double, soc0: Double, soc1: Double) {
        banco.executar(
            "INSERT INTO trips (start_ms, end_ms, day, km, drive_ms, fuel_l, kwh_out, kwh_in, ev_km, soc_start, soc_end) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            inicio, inicio + 1000, dia, km, drive, fuel, out, entrada, ev, soc0, soc1,
        )
    }

    private fun dia(dia: String, trips: Int, km: Double, drive: Long, fuel: Double, out: Double, entrada: Double, ev: Double) {
        banco.executar("INSERT INTO days VALUES (?, ?, ?, ?, ?, ?, ?, ?)", dia, trips, km, drive, fuel, out, entrada, ev)
    }

    private fun parada(inicio: Long, t: Long, tipo: String, antes: Double, depois: Double, q: Double) {
        banco.executar(
            "INSERT INTO trip_stops (start_ms, t, kind, lat, lon, level_before, level_after, amount) VALUES (?, ?, ?, NULL, NULL, ?, ?, ?)",
            inicio, t, tipo, antes, depois, q,
        )
    }

    private fun fixture() {
        viagem(1_700_000_000_000, "2023-11-14", 10.0, 600_000, 0.5, 3.0, 0.5, 8.0, 80.0, 60.0)
        viagem(1_700_100_000_000, "2023-11-15", 20.0, 1_200_000, 1.0, 5.0, 1.0, 10.0, 60.0, 40.0)
        viagem(1_700_200_000_000, "2023-11-15", 5.0, 300_000, 0.0, 1.5, 0.5, 5.0, 40.0, 35.0)
        viagem(1_700_300_000_000, "2023-12-01", 30.0, 1_800_000, 2.0, 6.0, 0.0, 0.0, 35.0, 10.0)
        viagem(1_700_400_000_000, "2023-12-02", 12.0, 900_000, 0.0, 2.4, 0.0, 12.0, 100.0, 70.0)
        dia("2023-11-14", 1, 10.0, 600_000, 0.5, 3.0, 0.5, 8.0)
        dia("2023-11-15", 2, 25.0, 1_500_000, 1.0, 6.5, 1.5, 15.0)
        dia("2023-12-01", 1, 30.0, 1_800_000, 2.0, 6.0, 0.0, 0.0)
        dia("2023-12-02", 1, 12.0, 900_000, 0.0, 2.4, 0.0, 12.0)
        parada(1_700_000_000_000, 1_700_000_000_000, "refuel", 20.0, 60.0, 22.0)
        parada(1_700_100_000_000, 1_700_100_000_000, "charge", 30.0, 90.0, 60.0)
        parada(1_700_300_000_000, 1_700_300_000_000, "refuel", 10.0, 30.0, 11.0)
        parada(1_700_300_000_000, 1_700_300_000_001, "charge", 10.0, 20.0, 10.0)
    }

    @Test
    fun `getDays filtra por texto e ordena`() {
        fixture()
        val dias = json(a.diasEmJson("2023-11-15", "2023-12-01")).lista()
        assertEquals(2, dias.size)
        val d1 = dias[0].obj()
        assertEquals("2023-11-15", d1.texto("period"))
        assertEquals(2L, d1.longo("trips"))
        assertRealJson(25.0, d1, "km")
        assertEquals(1_500_000L, d1.longo("driveMs"))
        assertRealJson(1.0, d1, "fuelL")
        assertRealJson(6.5, d1, "kwhOut")
        assertRealJson(1.5, d1, "kwhIn")
        assertRealJson(15.0, d1, "evKm")
        assertRealJson(25.0, d1, "kmPerL")
        assertRealJson(100.0 * 5.0 / 25.0, d1, "kwhPer100Km")
        val d2 = dias[1].obj()
        assertEquals("2023-12-01", d2.texto("period"))
        assertRealJson(30.0, d2, "km")
        assertRealJson(0.0, d2, "kwhIn")
    }

    @Test
    fun `getDays com limites nulos devolve tudo`() {
        fixture()
        assertEquals(4, json(a.diasEmJson(null, null)).lista().size)
    }

    @Test
    fun `getDays sem consumo de combustivel deixa kmPerL nulo`() {
        fixture()
        val d = json(a.diasEmJson("2023-12-02", "2023-12-02")).lista().single().obj()
        assertTrue(d.nulo("kmPerL"))
    }

    @Test
    fun `getMonths soma os dias`() {
        fixture()
        val meses = json(a.mesesEmJson()).lista()
        assertEquals(2, meses.size)
        val nov = meses[0].obj()
        assertEquals("2023-11", nov.texto("period"))
        assertEquals(3L, nov.longo("trips"))
        assertRealJson(35.0, nov, "km")
        assertEquals(2_100_000L, nov.longo("driveMs"))
        assertRealJson(1.5, nov, "fuelL")
        assertRealJson(9.5, nov, "kwhOut")
        assertRealJson(2.0, nov, "kwhIn")
        assertRealJson(23.0, nov, "evKm")
        val dez = meses[1].obj()
        assertEquals("2023-12", dez.texto("period"))
        assertEquals(2L, dez.longo("trips"))
        assertRealJson(42.0, dez, "km")
        assertEquals(2_700_000L, dez.longo("driveMs"))
        assertRealJson(2.0, dez, "fuelL")
        assertRealJson(8.4, dez, "kwhOut")
        assertRealJson(12.0, dez, "evKm")
    }

    @Test
    fun `getTotals`() {
        fixture()
        val t = json(a.totaisEmJson()).obj()
        assertEquals(5L, t.longo("trips"))
        assertRealJson(77.0, t, "km")
        assertEquals(4_800_000L, t.longo("driveMs"))
        assertRealJson(3.5, t, "fuelL")
        assertRealJson(17.9, t, "kwhOut")
        assertRealJson(2.0, t, "kwhIn")
        assertRealJson(35.0, t, "evKm")
        assertEquals("2023-11-14", t.texto("firstDay"))
        assertEquals(2L, t.longo("refuels"))
        assertRealJson(33.0, t, "refuelL")
        assertEquals(2L, t.longo("charges"))
        assertRealJson(14.9 / 95.0 * 100.0, t, "capacityKwh")
        assertEquals(4L, t.longo("capacityTrips"))
    }

    @Test
    fun `getTotals sem dados`() {
        val t = json(a.totaisEmJson()).obj()
        assertEquals(0L, t.longo("trips"))
        assertRealJson(0.0, t, "km")
        assertTrue(t.nulo("firstDay"))
        assertEquals(0L, t.longo("refuels"))
        assertFalse(t.containsKey("capacityKwh"))
        assertFalse(t.containsKey("capacityTrips"))
    }

    @Test
    fun `capacidade precisa de 3 viagens`() {
        viagem(1, "2023-11-14", 10.0, 1, 0.0, 3.0, 0.5, 0.0, 80.0, 60.0)
        viagem(2, "2023-11-14", 10.0, 1, 0.0, 3.0, 0.5, 0.0, 80.0, 60.0)
        val t = json(a.totaisEmJson()).obj()
        assertFalse(t.containsKey("capacityKwh"))
    }

    @Test
    fun `getStopTotals`() {
        fixture()
        val a1 = json(a.totaisDeParadasEmJson(1_700_100_000_000)).obj()
        assertEquals(1L, a1.longo("refuels"))
        assertRealJson(20.0, a1, "refuelPct")
        assertEquals(2L, a1.longo("charges"))
        assertRealJson(70.0, a1, "chargePct")
        val todos = json(a.totaisDeParadasEmJson(0)).obj()
        assertEquals(2L, todos.longo("refuels"))
        assertRealJson(60.0, todos, "refuelPct")
        assertEquals(2L, todos.longo("charges"))
        assertRealJson(70.0, todos, "chargePct")
        val nada = json(a.totaisDeParadasEmJson(9_999_999_999_999)).obj()
        assertEquals(0L, nada.longo("refuels"))
        assertRealJson(0.0, nada, "chargePct")
    }

    @Test
    fun `chave do dia depende do fuso`() {
        assertEquals("2023-11-14", ChaveDoDia.de(1_700_000_000_000))
        assertEquals("2023-11-15", ChaveDoDia.de(1_700_010_000_000))
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"))
        assertEquals("2023-11-14", ChaveDoDia.de(1_700_000_000_000))
        assertEquals("2023-11-14", ChaveDoDia.de(1_700_010_000_000))
    }

    // --- Gravação ---

    private fun resumo(inicio: Long, km: Double = 10.0, guiada: Boolean = false): ResumoDaViagem = ResumoDaViagem(
        inicioMs = inicio, fimMs = inicio + 600_000, aberta = false, km = km, kmIntegrado = km - 0.1,
        odometroInicial = 1000.0, odometroFinal = 1000.0 + km, odometroSaltos = 0, andandoMs = 500_000,
        paradoMs = 100_000, eletricoKm = 4.0, eletricoMs = 200_000, kwhGasto = 2.0, kwhRecuperado = 0.5, litros = 0.7,
        velocidadeMaxima = 80.0, paradas = 3, aceleracoesBruscas = 1, frenagensBruscas = 2, subidaM = 12.0,
        latInicial = -22.9, lonInicial = -43.2, latFinal = -22.8, lonFinal = -43.1,
        tanqueInicial = 50.0, tanqueFinal = 49.0, bateriaInicial = 80.0, bateriaFinal = 70.0,
        guiada = guiada, chegou = guiada, kmPlanejado = if (guiada) 9.5 else Double.NaN,
        segundosPlanejados = if (guiada) 700 else 0, kmGuiados = if (guiada) 9.0 else Double.NaN,
        segundosGuiados = if (guiada) 590 else 0,
        crusPrimeiros = mapOf("a" to "1"), crusUltimos = mapOf("a" to "2"),
    )

    @Test
    fun `gravar viagem cria a linha e acumula o dia`() {
        a.gravarViagem(resumo(T0))
        val l = banco.consultar("SELECT * FROM trips").single()
        assertEquals(T0, l.longo("start_ms"))
        assertEquals("2023-11-14", l.texto("day"))
        assertEquals(10.0, l.real("km")!!, 0.0)
        assertEquals(1L, l.longo("detail_kept"))
        assertEquals(0L, l.longo("map_attempts"))
        assertNull(l.texto("snapshot_path"))
        assertNull(l.texto("start_place"))
        assertNull(l.real("planned_km"))
        assertEquals(0L, l.longo("guided"))
        assertEquals("""{"first":{"a":"1"},"last":{"a":"2"}}""", l.texto("raw_json"))
        assertEquals(80.0, l.real("soc_start")!!, 0.0)
        val d = banco.consultar("SELECT * FROM days").single()
        assertEquals(1L, d.longo("trips"))
        assertEquals(10.0, d.real("km")!!, 0.0)
        assertEquals(500_000L, d.longo("drive_ms"))
        assertEquals(0.7, d.real("fuel_l")!!, 1e-12)
        assertEquals(4.0, d.real("ev_km")!!, 0.0)
        a.gravarViagem(resumo(T0 + 3_600_000))
        val d2 = banco.consultar("SELECT * FROM days").single()
        assertEquals(2L, d2.longo("trips"))
        assertEquals(20.0, d2.real("km")!!, 0.0)
    }

    @Test
    fun `colunas de guiagem so com viagem guiada`() {
        a.gravarViagem(resumo(T0, guiada = true))
        val l = banco.consultar("SELECT * FROM trips").single()
        assertEquals(1L, l.longo("guided"))
        assertEquals(1L, l.longo("arrived"))
        assertEquals(9.5, l.real("planned_km")!!, 0.0)
        assertEquals(700L, l.longo("planned_s"))
        assertEquals(9.0, l.real("guided_km")!!, 0.0)
        assertEquals(590L, l.longo("guided_s"))
    }

    @Test
    fun `NaN vira NULL no banco e soma zero no dia`() {
        val r = resumo(T0).copy(litros = Double.NaN, latInicial = Double.NaN, kwhGasto = Double.POSITIVE_INFINITY)
        a.gravarViagem(r)
        val l = banco.consultar("SELECT * FROM trips").single()
        assertTrue(l.nula("fuel_l"))
        assertTrue(l.nula("start_lat"))
        assertTrue(l.nula("kwh_out"))
        val d = banco.consultar("SELECT * FROM days").single()
        assertEquals(0.0, d.real("fuel_l")!!, 0.0)
        assertEquals(0.0, d.real("kwh_out")!!, 0.0)
    }

    @Test
    fun `defeito 6 - regravar o mesmo start_ms nao infla o dia`() {
        a.gravarViagem(resumo(T0, km = 10.0))
        a.gravarViagem(resumo(T0, km = 12.0))
        assertEquals(1, banco.consultar("SELECT * FROM trips").size)
        val d = banco.consultar("SELECT * FROM days").single()
        assertEquals(1L, d.longo("trips"))
        assertEquals(12.0, d.real("km")!!, 1e-9)
    }

    @Test
    fun `so as 30 viagens mais recentes guardam os pontos`() {
        for (i in 0 until 31) {
            val inicio = T0 + i * 3_600_000L
            a.gravarPontos(inicio, listOf(ponto(inicio + 1000, -22.9, -43.2)))
            a.gravarViagem(resumo(inicio))
        }
        // pontos de uma viagem ainda aberta (não gravada) não são tocados
        val aberta = T0 + 100 * 3_600_000L
        a.gravarPontos(aberta, listOf(ponto(aberta + 1000, -22.9, -43.2)))
        a.gravarViagem(resumo(T0 + 31 * 3_600_000L))
        val comPontos = banco.consultar("SELECT DISTINCT start_ms FROM trip_points").map { it.longo("start_ms") }
        assertFalse(comPontos.contains(T0))
        assertFalse(comPontos.contains(T0 + 1 * 3_600_000L))
        assertTrue(comPontos.contains(T0 + 2 * 3_600_000L))
        assertTrue(comPontos.contains(aberta))
        val mantidas = banco.consultar("SELECT COUNT(*) AS n FROM trips WHERE detail_kept = 1").single().longo("n")
        assertEquals(30L, mantidas)
        assertEquals(2L, banco.consultar("SELECT COUNT(*) AS n FROM trips WHERE detail_kept = 0").single().longo("n"))
        // a rota das viagens que perderam os pontos já estava gravada ou foi gravada antes de soltá-los
        val semRota = banco.consultar("SELECT COUNT(*) AS n FROM trips WHERE detail_kept = 0 AND route IS NULL").single().longo("n")
        assertEquals(0L, semRota)
    }

    private fun ponto(t: Long, lat: Double, lon: Double, kmh: Double = 50.0) = PontoDeTrilha(
        t = t, lat = lat, lon = lon, alt = 10.0, kmh = kmh, kw = 12.34, modoDeCombustivel = 1, taxaDeCombustivel = 7.77,
        combustao = 1, km = 1.5,
    )

    // --- Rota ---

    @Test
    fun `rota gravada tem so pontos com posicao e arredonda`() {
        val pontos = listOf(
            ponto(1, -22.9012345678, -43.2012345678, 49.6),
            PontoDeTrilha(2, Double.NaN, Double.NaN, Double.NaN, 10.0, Double.NaN, 0, Double.NaN, -1, 0.0),
            ponto(3, -22.8, -43.1),
        )
        a.gravarPontos(T0, pontos)
        a.gravarViagem(resumo(T0))
        a.gravarRota(T0)
        val rota = json(banco.consultar("SELECT route FROM trips").single().texto("route")!!).lista()
        assertEquals(2, rota.size)
        val p0 = rota[0].lista()
        assertEquals(6, p0.size)
        assertEquals(-22.90123, (p0[0] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 1e-9)
        assertEquals(-43.20123, (p0[1] as kotlinx.serialization.json.JsonPrimitive).content.toDouble(), 1e-9)
        assertEquals("50", (p0[2] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("12.3", (p0[3] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("1", (p0[4] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("7.8", (p0[5] as kotlinx.serialization.json.JsonPrimitive).content)
    }

    @Test
    fun `rota e afinada a 300 pontos`() {
        a.gravarPontos(T0, (0 until 1000).map { ponto(T0 + it, -22.9 + it * 1e-5, -43.2) })
        a.gravarViagem(resumo(T0))
        a.gravarRota(T0)
        assertEquals(251, json(banco.consultar("SELECT route FROM trips").single().texto("route")!!).lista().size)
    }

    @Test
    fun `viagem sem pontos com posicao grava rota vazia`() {
        a.gravarViagem(resumo(T0))
        a.gravarRota(T0)
        assertEquals("[]", banco.consultar("SELECT route FROM trips").single().texto("route"))
    }

    // --- Consultas de viagem ---

    @Test
    fun `getTrips mais recente primeiro com limites`() {
        for (i in 0 until 5) a.gravarViagem(resumo(T0 + i * 3_600_000L))
        val lista = json(a.viagensEmJson(0, 3)).lista()
        assertEquals(3, lista.size)
        assertEquals(T0 + 4 * 3_600_000L, lista[0].obj().longo("startMs"))
        val segunda = json(a.viagensEmJson(3, 10)).lista()
        assertEquals(2, segunda.size)
        assertEquals(T0, segunda[1].obj().longo("startMs"))
        assertEquals(1, json(a.viagensEmJson(0, 0)).lista().size)
        assertEquals(5, json(a.viagensEmJson(-5, 1000)).lista().size)
    }

    @Test
    fun `linha da viagem tem os campos e os derivados`() {
        a.gravarViagem(resumo(T0))
        val v = json(a.viagensEmJson(0, 10)).lista().single().obj()
        assertEquals(T0, v.longo("startMs"))
        assertEquals(T0 + 600_000, v.longo("endMs"))
        assertEquals("2023-11-14", v.texto("day"))
        assertRealJson(10.0, v, "km")
        assertRealJson(9.9, v, "kmIntegrated")
        assertRealJson(1000.0, v, "odoStart")
        assertRealJson(1010.0, v, "odoEnd")
        assertRealJson(4.0, v, "evKm")
        assertRealJson(2.0, v, "kwhOut")
        assertRealJson(0.5, v, "kwhIn")
        assertRealJson(0.7, v, "fuelL")
        assertRealJson(80.0, v, "maxKmh")
        assertRealJson(12.0, v, "climbM")
        assertRealJson(-22.9, v, "startLat")
        assertRealJson(-43.1, v, "endLon")
        assertRealJson(50.0, v, "fuelPctStart")
        assertRealJson(70.0, v, "socEnd")
        assertEquals(500_000L, v.longo("driveMs"))
        assertEquals(100_000L, v.longo("idleMs"))
        assertEquals(200_000L, v.longo("evMs"))
        assertEquals(3L, v.longo("stops"))
        assertEquals(1L, v.longo("harshAccel"))
        assertEquals(2L, v.longo("harshBrake"))
        assertEquals(1L, v.longo("detailKept"))
        assertEquals(0L, v.longo("guided"))
        assertEquals(0L, v.longo("plannedS"))
        assertTrue(v.nulo("startPlace"))
        assertTrue(v.nulo("snapshotPath"))
        assertTrue(v.nulo("plannedKm"))
        assertRealJson(10.0 / 0.7, v, "kmPerL")
        assertRealJson(100.0 * 1.5 / 10.0, v, "kwhPer100Km")
        assertRealJson(0.4, v, "evShare")
        assertFalse(v.containsKey("raw_json"))
        assertFalse(v.containsKey("rawJson"))
        assertFalse(v.containsKey("route"))
    }

    @Test
    fun `derivados nulos quando faltam dados`() {
        a.gravarViagem(resumo(T0, km = 0.05).copy(litros = 0.04))
        val v = json(a.viagensEmJson(0, 10)).lista().single().obj()
        assertTrue(v.nulo("kmPerL"))
        assertTrue(v.nulo("kwhPer100Km"))
        assertTrue(v.nulo("evShare"))
    }

    @Test
    fun `getTrip traz rota pontos e paradas`() {
        a.gravarPontos(T0, (0 until 10).map { ponto(T0 + it * 1000L, -22.9 + it * 1e-4, -43.2) })
        a.gravarParada(T0, ParadaDeViagem(TipoDeParada.ABASTECIMENTO, T0, -22.9, -43.2, 30.0, 40.0, 5.5))
        a.gravarViagem(resumo(T0))
        a.gravarRota(T0)
        val d = json(a.viagemEmJson(T0, 600)!!).obj()
        assertEquals(T0, d.longo("startMs"))
        assertEquals(10, d["route"]!!.lista().size)
        assertEquals(
            listOf("t", "lat", "lon", "alt", "kmh", "kw", "fuelMode", "fuelRate", "ice", "km"),
            d["pointFields"]!!.lista().map { (it as kotlinx.serialization.json.JsonPrimitive).content },
        )
        val pontos = d["points"]!!.lista()
        assertEquals(10, pontos.size)
        val p0 = pontos[0].lista()
        assertEquals(10, p0.size)
        assertEquals(T0, (p0[0] as kotlinx.serialization.json.JsonPrimitive).content.toLong())
        val paradas = d["stops"]!!.lista()
        assertEquals(1, paradas.size)
        val p = paradas[0].obj()
        assertEquals("refuel", p.texto("kind"))
        assertEquals(T0, p.longo("t"))
        assertRealJson(30.0, p, "before")
        assertRealJson(40.0, p, "after")
        assertRealJson(5.5, p, "amount")
    }

    @Test
    fun `getTrip de viagem que nao existe e nulo`() {
        assertNull(a.viagemEmJson(123, 600))
    }

    @Test
    fun `getTrip afina os pontos e mantem o ultimo`() {
        a.gravarPontos(T0, (0 until 1000).map { ponto(T0 + it, -22.9, -43.2) })
        a.gravarViagem(resumo(T0))
        val pontos = json(a.viagemEmJson(T0, 300)!!).obj()["points"]!!.lista()
        assertEquals(251, pontos.size)
        assertEquals(T0 + 999, (pontos.last().lista()[0] as kotlinx.serialization.json.JsonPrimitive).content.toLong())
    }

    @Test
    fun `getTrip troca NULL por zero em fuelMode e ice`() {
        banco.executar("INSERT INTO trip_points (start_ms, t) VALUES (?, ?)", T0, T0 + 1)
        a.gravarViagem(resumo(T0))
        val p = json(a.viagemEmJson(T0, 600)!!).obj()["points"]!!.lista().single().lista()
        assertEquals("0", (p[6] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("0", (p[8] as kotlinx.serialization.json.JsonPrimitive).content)
        assertTrue(p[1] is kotlinx.serialization.json.JsonNull)
    }

    @Test
    fun `getTripPointsSince junta banco e memoria sem repetir`() {
        a.gravarPontos(T0, (1..5).map { ponto(T0 + it * 1000L, -22.9, -43.2) })
        val memoria = (4..7).map { ponto(T0 + it * 1000L, -22.9, -43.2) }
        val d = json(a.pontosDesdeEmJson(T0, T0 + 2000, 1500, memoria)).obj()
        val ts = d["points"]!!.lista().map { (it.lista()[0] as kotlinx.serialization.json.JsonPrimitive).content.toLong() }
        assertEquals(listOf(3, 4, 5, 6, 7).map { T0 + it * 1000L }, ts)
        assertTrue(d["stops"]!!.lista().isEmpty())
    }

    @Test
    fun `getTripPointsSince so com memoria`() {
        val memoria = (1..3).map { ponto(T0 + it * 1000L, -22.9, -43.2) }
        val d = json(a.pontosDesdeEmJson(T0, T0 + 1000, 1500, memoria)).obj()
        assertEquals(2, d["points"]!!.lista().size)
    }

    @Test
    fun `getTripPointsSince afina ao vivo`() {
        a.gravarPontos(T0, (1..1000).map { ponto(T0 + it * 1000L, -22.9, -43.2) })
        val d = json(a.pontosDesdeEmJson(T0, 0, 300, emptyList())).obj()
        assertEquals(250, d["points"]!!.lista().size)
    }

    // --- Checkpoint, pendências, mapas ---

    @Test
    fun `checkpoint grava substitui e apaga`() {
        assertNull(a.lerCheckpoint())
        a.gravarCheckpoint("a=1\n", T0)
        a.gravarCheckpoint("b=2\n", T0 + 1)
        assertEquals("b=2\n", a.lerCheckpoint())
        assertEquals(1, banco.consultar("SELECT * FROM open_trip").size)
        a.apagarCheckpoint()
        assertNull(a.lerCheckpoint())
    }

    @Test
    fun `a ultima viagem gravada alimenta o nivel parado`() {
        assertNull(a.fimDaUltimaViagem())
        a.gravarViagem(resumo(T0))
        a.gravarViagem(resumo(T0 + 3_600_000))
        val f = a.fimDaUltimaViagem()!!
        assertEquals(49.0, f.tanque, 0.0)
        assertEquals(70.0, f.bateria, 0.0)
        assertEquals(-22.8, f.lat, 0.0)
        assertEquals(T0 + 3_600_000 + 600_000, f.fimMs)
    }

    @Test
    fun `viagens pendentes da mais nova para a mais velha com menos de 5 tentativas`() {
        for (i in 0 until 8) a.gravarViagem(resumo(T0 + i * 3_600_000L))
        banco.executar("UPDATE trips SET snapshot_path = 'x.jpg', start_place = 'L' WHERE start_ms = ?", T0 + 7 * 3_600_000L)
        banco.executar("UPDATE trips SET map_attempts = 5 WHERE start_ms = ?", T0 + 6 * 3_600_000L)
        val p = a.viagensPendentes(5)
        assertEquals(5, p.size)
        assertEquals(T0 + 5 * 3_600_000L, p[0].inicioMs)
        assertEquals(T0 + 1 * 3_600_000L, p[4].inicioMs)
        assertNull(p[0].caminhoDoMapa)
        assertNull(p[0].lugarInicial)
        assertEquals(-22.9, p[0].latInicial, 0.0)
    }

    @Test
    fun `marcar sem mapa tira a viagem da fila do mapa`() {
        a.gravarViagem(resumo(T0))
        a.marcarSemMapa(T0)
        val p = a.viagensPendentes(5).single()
        assertEquals("", p.caminhoDoMapa)
        assertNull(p.lugarInicial)
    }

    @Test
    fun `viagens sem rota so com detalhe`() {
        a.gravarViagem(resumo(T0))
        banco.executar("UPDATE trips SET route = NULL")
        assertEquals(listOf(T0), a.viagensSemRota(20))
        banco.executar("UPDATE trips SET detail_kept = 0")
        assertTrue(a.viagensSemRota(20).isEmpty())
    }

    @Test
    fun `posicoes do mapa vem dos pontos ou da rota`() {
        a.gravarPontos(T0, (0 until 5).map { ponto(T0 + it, -22.9 + it * 0.001, -43.2) })
        a.gravarViagem(resumo(T0))
        a.gravarRota(T0)
        assertEquals(5, a.posicoesParaMapa(T0, 800).size)
        // sem os pontos por segundo, a rota gravada serve (P-9)
        banco.executar("DELETE FROM trip_points")
        val daRota = a.posicoesParaMapa(T0, 800)
        assertEquals(5, daRota.size)
        assertEquals(-22.9, daRota[0].lat, 1e-9)
        assertTrue(a.posicoesParaMapa(999, 800).isEmpty())
    }

    @Test
    fun `gravar mapa e lugares`() {
        a.gravarViagem(resumo(T0))
        a.gravarMapa(T0, "$T0.jpg", "12,1.000,2.000")
        a.gravarLugares(T0, "Icaraí", "Centro")
        a.somarTentativaDeMapa(T0)
        val v = json(a.viagensEmJson(0, 1)).lista().single().obj()
        assertEquals("$T0.jpg", v.texto("snapshotPath"))
        assertEquals("12,1.000,2.000", v.texto("snapshotView"))
        assertEquals("Icaraí", v.texto("startPlace"))
        assertEquals("Centro", v.texto("endPlace"))
        assertEquals(1L, banco.consultar("SELECT map_attempts FROM trips").single().longo("map_attempts"))
    }

    // --- Esquema ---

    @Test
    fun `o esquema v4 tem as tabelas e indices`() {
        val nomes = banco.consultar("SELECT name FROM sqlite_master").mapNotNull { it.texto("name") }
        for (n in listOf("trips", "trip_points", "days", "open_trip", "trip_stops", "trips_day", "trip_points_trip", "trip_stops_trip")) {
            assertTrue(n, nomes.contains(n))
        }
        val colunas = banco.consultar("PRAGMA table_info(trips)").mapNotNull { it.texto("name") }
        assertEquals(41, colunas.size)
        assertTrue(colunas.containsAll(listOf("route", "snapshot_view", "soc_start", "soc_end", "map_attempts", "guided")))
    }

    @Test
    fun `o checkpoint so aceita o id 1`() {
        var falhou = false
        try {
            banco.executar("INSERT INTO open_trip (id, state, updated_ms) VALUES (2, 'x', 1)")
        } catch (e: Exception) {
            falhou = true
        }
        assertTrue(falhou)
    }

    @Test
    fun `atualizar da versao 1 chega no esquema 4 e zera mapas ja feitos`() {
        val velho = BancoJdbc()
        velho.executar(
            "CREATE TABLE trips (start_ms INTEGER PRIMARY KEY, end_ms INTEGER NOT NULL, day TEXT NOT NULL, km REAL, " +
                "km_integrated REAL, odo_start REAL, odo_end REAL, drive_ms INTEGER, idle_ms INTEGER, ev_km REAL, " +
                "ev_ms INTEGER, kwh_out REAL, kwh_in REAL, fuel_l REAL, max_kmh REAL, stops INTEGER, harsh_accel INTEGER, " +
                "harsh_brake INTEGER, climb_m REAL, start_lat REAL, start_lon REAL, end_lat REAL, end_lon REAL, " +
                "fuel_pct_start REAL, fuel_pct_end REAL, start_place TEXT, end_place TEXT, snapshot_path TEXT, " +
                "raw_json TEXT, detail_kept INTEGER NOT NULL DEFAULT 1)"
        )
        velho.executar("INSERT INTO trips (start_ms, end_ms, day, snapshot_path) VALUES (1, 2, '2023-01-01', 'a.jpg')")
        velho.executar("INSERT INTO trips (start_ms, end_ms, day, snapshot_path) VALUES (3, 4, '2023-01-02', '')")
        velho.executar("INSERT INTO trips (start_ms, end_ms, day) VALUES (5, 6, '2023-01-03')")
        EsquemaDeViagens.atualizar(velho, 1)
        val colunas = velho.consultar("PRAGMA table_info(trips)").mapNotNull { it.texto("name") }
        assertEquals(41, colunas.size)
        assertEquals(
            listOf(null, "", null),
            velho.consultar("SELECT snapshot_path FROM trips ORDER BY start_ms").map { it.texto("snapshot_path") },
        )
        val novo = ArmazenamentoDeViagens(velho)
        assertEquals(0L, velho.consultar("SELECT COUNT(*) AS n FROM trip_stops").single().longo("n"))
        assertEquals(3, json(novo.viagensEmJson(0, 10)).lista().size)
    }

    @Test
    fun `atualizar da versao 3 so acrescenta o que falta`() {
        val v3 = BancoJdbc()
        EsquemaDeViagens.criar(v3)
        // um banco v4 recém-criado, atualizado "da 3", não pode quebrar nem perder dados
        v3.executar("INSERT INTO trips (start_ms, end_ms, day, snapshot_path) VALUES (1, 2, '2023-01-01', 'a.jpg')")
        v3.executar("DROP TABLE trip_stops")
        v3.executar("ALTER TABLE trips DROP COLUMN soc_end")
        v3.executar("ALTER TABLE trips DROP COLUMN soc_start")
        v3.executar("ALTER TABLE trips DROP COLUMN snapshot_view")
        v3.executar("ALTER TABLE trips DROP COLUMN route")
        EsquemaDeViagens.atualizar(v3, 3)
        assertEquals(41, v3.consultar("PRAGMA table_info(trips)").size)
        assertNull(v3.consultar("SELECT snapshot_path FROM trips").single().texto("snapshot_path"))
    }
}
