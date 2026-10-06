package br.com.deepgwt.tela3d.viagens

import java.io.File
import java.nio.file.Files
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultasDaPonteTest {

    private fun fachada(
        armazenamento: ArmazenamentoDeViagens?,
        pasta: File? = null,
        aoVivo: String = "",
        memoria: List<PontoDeTrilha> = emptyList(),
        finalizar: () -> Unit = {},
    ) = ConsultasDaPonte(
        armazenamento = { armazenamento },
        textoAoVivo = { aoVivo },
        pontosEmMemoria = { memoria },
        pedirFinalizacao = finalizar,
        pastaDosMapas = { pasta },
    )

    @Test
    fun `sem banco cada metodo devolve o seu valor de falha`() {
        val f = fachada(null)
        assertEquals("", f.viagemAoVivoEmJson())
        assertEquals("[]", f.viagensEmJson(0, 30))
        assertEquals("[]", f.diasEmJson("2023-01-01", "2023-12-31"))
        assertEquals("[]", f.mesesEmJson())
        assertEquals("{}", f.totaisEmJson())
        assertEquals("{}", f.totaisDeParadasEmJson("0"))
        assertEquals("null", f.viagemEmJson("1", 600))
        assertEquals("null", f.pontosDesdeEmJson("1", "0", 1500))
        assertEquals("", f.mapaDaViagem("1"))
    }

    @Test
    fun `numero ilegivel devolve o valor de falha mesmo com banco`() {
        val f = fachada(ArmazenamentoDeViagens(BancoJdbc.novo()))
        assertEquals("null", f.viagemEmJson("abc", 600))
        assertEquals("null", f.viagemEmJson(null, 600))
        assertEquals("null", f.pontosDesdeEmJson("abc", "0", 1500))
        assertEquals("null", f.pontosDesdeEmJson("1", "xyz", 1500))
        assertEquals("", f.mapaDaViagem("abc"))
        assertEquals("{}", f.totaisDeParadasEmJson("abc"))
    }

    @Test
    fun `erro de SQL nunca vira excecao`() {
        val banco = BancoJdbc() // sem esquema: toda consulta falha
        val f = fachada(ArmazenamentoDeViagens(banco))
        assertEquals("[]", f.viagensEmJson(0, 30))
        assertEquals("{}", f.totaisEmJson())
        assertEquals("null", f.viagemEmJson("1", 600))
        assertEquals("null", f.pontosDesdeEmJson("1", "0", 1500))
        assertEquals("[]", f.mesesEmJson())
    }

    @Test
    fun `identificadores passam por trim`() {
        val banco = BancoJdbc.novo()
        val a = ArmazenamentoDeViagens(banco)
        val f = fachada(a)
        banco.executar("INSERT INTO trips (start_ms, end_ms, day) VALUES (?, ?, ?)", T0, T0 + 1, "2023-11-14")
        assertTrue(f.viagemEmJson("  $T0 ", 600).contains("\"startMs\":$T0"))
        assertEquals("null", f.viagemEmJson("999", 600))
    }

    @Test
    fun `maxPontos zero ou negativo vira 1500 e o teto e 1500`() {
        val banco = BancoJdbc.novo()
        val a = ArmazenamentoDeViagens(banco)
        banco.executar("INSERT INTO trips (start_ms, end_ms, day) VALUES (?, ?, ?)", T0, T0 + 1, "2023-11-14")
        a.gravarPontos(T0, (0 until 3000).map { PontoDeTrilha(T0 + it, -22.9, -43.2, 0.0, 10.0, 0.0, 0, Double.NaN, 0, 0.0) })
        val f = fachada(a)
        val n0 = json(f.viagemEmJson("$T0", 0)).obj()["points"]!!.lista().size
        val nNeg = json(f.viagemEmJson("$T0", -5)).obj()["points"]!!.lista().size
        val nAlto = json(f.viagemEmJson("$T0", 100_000)).obj()["points"]!!.lista().size
        assertEquals(n0, nNeg)
        assertEquals(n0, nAlto)
        assertEquals(1501, n0)
        val ao = json(f.pontosDesdeEmJson("$T0", "0", 0)).obj()["points"]!!.lista().size
        assertEquals(1500, ao)
    }

    @Test
    fun `getTripPointsSince usa a memoria`() {
        val memoria = listOf(PontoDeTrilha(T0 + 9000, -22.9, -43.2, 0.0, 10.0, 0.0, 0, Double.NaN, 0, 0.0))
        val f = fachada(ArmazenamentoDeViagens(BancoJdbc.novo()), memoria = memoria)
        val pts = json(f.pontosDesdeEmJson("$T0", "${T0 + 1000}", 1500)).obj()["points"]!!.lista()
        assertEquals(1, pts.size)
    }

    @Test
    fun `getTrips limita limite e deslocamento`() {
        val banco = BancoJdbc.novo()
        val f = fachada(ArmazenamentoDeViagens(banco))
        for (i in 0 until 250) banco.executar("INSERT INTO trips (start_ms, end_ms, day) VALUES (?, ?, ?)", i.toLong(), 1L, "2023-01-01")
        assertEquals(200, json(f.viagensEmJson(0, 100_000)).lista().size)
        assertEquals(1, json(f.viagensEmJson(0, 0)).lista().size)
        assertEquals(10, json(f.viagensEmJson(-50, 10)).lista().size)
    }

    @Test
    fun `finalizar e ao vivo vem do que o gravador entrega`() {
        var finalizou = 0
        val f = fachada(null, aoVivo = """{"open":true}""", finalizar = { finalizou++ })
        assertEquals("""{"open":true}""", f.viagemAoVivoEmJson())
        f.finalizarViagem()
        assertEquals(1, finalizou)
    }

    @Test
    fun `mapa vira data URL em base64 sem quebras`() {
        val pasta = Files.createTempDirectory("mapas").toFile()
        try {
            val bytes = ByteArray(5000) { (it % 251).toByte() }
            File(pasta, "$T0.jpg").writeBytes(bytes)
            val f = fachada(null, pasta)
            val r = f.mapaDaViagem(" $T0 ")
            assertTrue(r.startsWith("data:image/jpeg;base64,"))
            assertTrue(!r.contains("\n"))
            assertEquals(bytes.toList(), Base64.getDecoder().decode(r.removePrefix("data:image/jpeg;base64,")).toList())
            assertEquals("", f.mapaDaViagem("123"))
        } finally {
            pasta.deleteRecursively()
        }
    }

    @Test
    fun `mapa grande demais ou diretorio devolve vazio`() {
        val pasta = Files.createTempDirectory("mapas").toFile()
        try {
            File(pasta, "1.jpg").writeBytes(ByteArray(1_000_001))
            File(pasta, "2.jpg").writeBytes(ByteArray(1_000_000))
            File(pasta, "3.jpg").mkdir()
            val f = fachada(null, pasta)
            assertEquals("", f.mapaDaViagem("1"))
            assertTrue(f.mapaDaViagem("2").startsWith("data:image/jpeg;base64,"))
            assertEquals("", f.mapaDaViagem("3"))
            assertEquals("", fachada(null, null).mapaDaViagem("1"))
        } finally {
            pasta.deleteRecursively()
        }
    }
}
