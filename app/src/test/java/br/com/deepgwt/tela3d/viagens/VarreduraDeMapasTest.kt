package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VarreduraDeMapasTest {
    private val banco = BancoJdbc.novo()
    private val a = ArmazenamentoDeViagens(banco)
    private val geradas = ArrayList<Pair<Long, Enquadramento>>()
    private val consultas = ArrayList<Pair<Double, Double>>()
    private var resultadoDoMapa: (Long) -> ResultadoDoMapa = { ResultadoDoMapa.Gerado("$it.jpg") }
    private var respostaDoLugar: (Double, Double) -> GeocodificadorReverso.Lugar? =
        { _, _ -> GeocodificadorReverso.Lugar("Icaraí", "Niterói", "Rua A") }

    private val varredura = VarreduraDeMapas(
        a,
        { inicio, quadro -> geradas.add(inicio to quadro); resultadoDoMapa(inicio) },
        { lat, lon -> consultas.add(lat to lon); respostaDoLugar(lat, lon) },
    )

    private fun viagem(i: Int, comPontos: Boolean = true, lat: Double = -22.9): Long {
        val inicio = T0 + i * 3_600_000L
        if (comPontos) {
            a.gravarPontos(inicio, (0 until 5).map {
                PontoDeTrilha(inicio + it, lat + it * 0.001, -43.2, 0.0, 10.0, 0.0, 0, Double.NaN, 0, 0.0)
            })
        }
        banco.executar(
            "INSERT INTO trips (start_ms, end_ms, day, start_lat, start_lon, end_lat, end_lon) VALUES (?, ?, '2023-11-14', ?, ?, ?, ?)",
            inicio, inicio + 1000, lat, -43.2, lat + 0.004, -43.2,
        )
        return inicio
    }

    private fun linha(inicio: Long) = banco.consultar("SELECT * FROM trips WHERE start_ms = ?", inicio).single()

    @Test
    fun `grava a rota das viagens que ainda nao tem`() {
        val v = viagem(0)
        varredura.executar()
        assertEquals(5, json(linha(v).texto("route")!!).lista().size)
    }

    @Test
    fun `gera o mapa e grava nome e quadro`() {
        val v = viagem(0)
        varredura.executar()
        val l = linha(v)
        assertEquals("$v.jpg", l.texto("snapshot_path"))
        assertTrue(l.texto("snapshot_view")!!.matches(Regex("\\d+,[0-9.]+,[0-9.]+")))
        assertEquals(1, geradas.size)
        assertEquals("Icaraí", l.texto("start_place"))
        assertEquals("Icaraí", l.texto("end_place"))
        assertEquals(2, consultas.size)
        assertEquals(-22.9, consultas[0].first, 1e-9)
        assertEquals(-22.896, consultas[1].first, 1e-9)
    }

    @Test
    fun `menos de duas posicoes grava sem mapa e nao tenta de novo`() {
        val v = viagem(0, comPontos = false)
        varredura.executar()
        assertEquals("", linha(v).texto("snapshot_path"))
        assertNull(linha(v).texto("snapshot_view"))
        assertTrue(geradas.isEmpty())
        varredura.executar()
        assertTrue(geradas.isEmpty())
    }

    @Test
    fun `sem pontos mas com rota gravada ainda gera o mapa`() {
        val v = viagem(0)
        a.gravarRota(v)
        banco.executar("DELETE FROM trip_points")
        varredura.executar()
        assertEquals(1, geradas.size)
        assertEquals("$v.jpg", linha(v).texto("snapshot_path"))
    }

    @Test
    fun `falha de rede no mapa aborta a varredura sem gastar tentativa`() {
        val v1 = viagem(1)
        val v0 = viagem(0)
        resultadoDoMapa = { ResultadoDoMapa.SemRede }
        varredura.executar()
        assertEquals(1, geradas.size)
        assertEquals(v1, geradas[0].first)
        assertEquals(0L, linha(v1).longo("map_attempts"))
        assertNull(linha(v1).texto("snapshot_path"))
        assertNull(linha(v0).texto("snapshot_path"))
        assertTrue(consultas.isEmpty())
        // na próxima varredura, com rede, tudo se resolve
        resultadoDoMapa = { ResultadoDoMapa.Gerado("$it.jpg") }
        varredura.executar()
        assertEquals("$v1.jpg", linha(v1).texto("snapshot_path"))
        assertEquals("$v0.jpg", linha(v0).texto("snapshot_path"))
    }

    @Test
    fun `excecao no mapa soma tentativa e segue`() {
        val v1 = viagem(1)
        val v0 = viagem(0)
        resultadoDoMapa = { inicio -> if (inicio == v1) throw IllegalStateException("bitmap") else ResultadoDoMapa.Gerado("$inicio.jpg") }
        varredura.executar()
        assertEquals(1L, linha(v1).longo("map_attempts"))
        assertNull(linha(v1).texto("snapshot_path"))
        assertEquals("$v0.jpg", linha(v0).texto("snapshot_path"))
        // a viagem com exceção ainda teve os lugares resolvidos
        assertEquals("Icaraí", linha(v1).texto("start_place"))
    }

    @Test
    fun `depois de 5 excecoes a viagem sai da fila`() {
        val v = viagem(0)
        resultadoDoMapa = { throw IllegalStateException("sempre") }
        respostaDoLugar = { _, _ -> null }
        repeat(5) { varredura.executar() }
        assertEquals(5L, linha(v).longo("map_attempts"))
        geradas.clear()
        varredura.executar()
        assertTrue(geradas.isEmpty())
    }

    @Test
    fun `falha do geocodificador aborta a varredura`() {
        val v1 = viagem(1)
        val v0 = viagem(0)
        respostaDoLugar = { _, _ -> null }
        varredura.executar()
        assertNull(linha(v1).texto("start_place"))
        assertEquals("$v1.jpg", linha(v1).texto("snapshot_path"))
        // abortou: a viagem mais velha não foi nem tentada
        assertNull(linha(v0).texto("snapshot_path"))
        assertEquals(1, consultas.size)
        assertEquals(0L, linha(v1).longo("map_attempts"))
    }

    @Test
    fun `falha na chegada nao grava nem a partida`() {
        val v = viagem(0)
        respostaDoLugar = { lat, _ -> if (lat < -22.8995) GeocodificadorReverso.Lugar("A", "B", "C") else null }
        varredura.executar()
        assertNull(linha(v).texto("start_place"))
        assertNull(linha(v).texto("end_place"))
    }

    @Test
    fun `sem coordenadas grava lugares vazios sem consultar`() {
        banco.executar("INSERT INTO trips (start_ms, end_ms, day) VALUES (?, ?, '2023-11-14')", T0, T0 + 1)
        varredura.executar()
        assertEquals("", linha(T0).texto("start_place"))
        assertEquals("", linha(T0).texto("end_place"))
        assertTrue(consultas.isEmpty())
    }

    @Test
    fun `rotulo vazio conta como resolvido`() {
        val v = viagem(0)
        respostaDoLugar = { _, _ -> GeocodificadorReverso.Lugar("", "", "") }
        varredura.executar()
        assertEquals("", linha(v).texto("start_place"))
        consultas.clear()
        varredura.executar()
        assertTrue(consultas.isEmpty())
    }

    @Test
    fun `no maximo 5 viagens por varredura`() {
        for (i in 0 until 8) viagem(i)
        varredura.executar()
        assertEquals(5, geradas.size)
        assertFalse(geradas.any { it.first == T0 })
    }

    @Test
    fun `no maximo 20 rotas por varredura`() {
        for (i in 0 until 25) viagem(i)
        resultadoDoMapa = { ResultadoDoMapa.SemRede }
        varredura.executar()
        val semRota = banco.consultar("SELECT COUNT(*) AS n FROM trips WHERE route IS NULL").single().longo("n")
        assertEquals(5L, semRota)
    }
}
