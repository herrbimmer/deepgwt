package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LugarAtualTest {
    private var agora = 1_000_000_000L
    private var permissao = true
    private var posicao: PosicaoDoAparelho? = PosicaoDoAparelho(-22.9, -43.2, agora - 1000, "gps")
    private var lancaPermissao = false
    private var lugar: GeocodificadorReverso.Lugar? = GeocodificadorReverso.Lugar("Icaraí", "Niterói", "Rua A")
    private val consultas = ArrayList<Pair<Double, Double>>()
    private val entregas = ArrayList<String>()

    private val fonte = object : FonteDePosicoes {
        override fun temPermissao() = permissao
        override fun maisRecente(): PosicaoDoAparelho? {
            if (lancaPermissao) throw SecurityException("sem permissão")
            return posicao
        }
    }

    private val lugarAtual = LugarAtual(
        fonte,
        { lat, lon -> consultas.add(lat to lon); lugar },
        { entregas.add(it) },
        { agora },
        {},
    )

    @Test
    fun `entrega o JSON do lugar`() {
        lugarAtual.consultarUmaVez()
        assertEquals(1, entregas.size)
        val j = json(entregas[0]).obj()
        assertEquals("Icaraí", j.texto("neighborhood"))
        assertEquals("Niterói", j.texto("city"))
        assertEquals("Rua A", j.texto("street"))
        assertEquals("gps", j.texto("provider"))
    }

    @Test
    fun `provedor nulo vira gps`() {
        posicao = PosicaoDoAparelho(-22.9, -43.2, agora, null)
        lugarAtual.consultarUmaVez()
        assertEquals("gps", json(entregas[0]).obj().texto("provider"))
        assertEquals("network", json(LugarAtual.montarJson(lugar!!, "network")).obj().texto("provider"))
    }

    @Test
    fun `sem permissao nao faz nada`() {
        permissao = false
        lugarAtual.consultarUmaVez()
        assertTrue(consultas.isEmpty())
        assertTrue(entregas.isEmpty())
    }

    @Test
    fun `SecurityException encerra a consulta`() {
        lancaPermissao = true
        lugarAtual.consultarUmaVez()
        assertTrue(consultas.isEmpty())
    }

    @Test
    fun `sem posicao nao faz nada`() {
        posicao = null
        lugarAtual.consultarUmaVez()
        assertTrue(consultas.isEmpty())
    }

    @Test
    fun `posicao velha demais e ignorada`() {
        posicao = PosicaoDoAparelho(-22.9, -43.2, agora - 600_001, "gps")
        lugarAtual.consultarUmaVez()
        assertTrue(consultas.isEmpty())
        posicao = PosicaoDoAparelho(-22.9, -43.2, agora - 600_000, "gps")
        lugarAtual.consultarUmaVez()
        assertEquals(1, consultas.size)
    }

    @Test
    fun `menos de 120 m da ultima posicao usada nao consulta de novo`() {
        lugarAtual.consultarUmaVez()
        // ~0,0009 grau de latitude = 100 m
        posicao = PosicaoDoAparelho(-22.9009, -43.2, agora, "gps")
        lugarAtual.consultarUmaVez()
        assertEquals(1, consultas.size)
        // ~0,0013 grau = 145 m
        posicao = PosicaoDoAparelho(-22.9013, -43.2, agora, "gps")
        lugar = GeocodificadorReverso.Lugar("Centro", "Niterói", "Rua B")
        lugarAtual.consultarUmaVez()
        assertEquals(2, consultas.size)
        assertEquals(2, entregas.size)
    }

    @Test
    fun `so entrega quando o JSON muda`() {
        lugarAtual.consultarUmaVez()
        posicao = PosicaoDoAparelho(-23.5, -43.2, agora, "gps")
        lugarAtual.consultarUmaVez()
        assertEquals(2, consultas.size)
        assertEquals(1, entregas.size)
    }

    @Test
    fun `geocodificador sem resposta ou vazio nao atualiza`() {
        lugar = null
        lugarAtual.consultarUmaVez()
        lugar = GeocodificadorReverso.Lugar("", "", "")
        lugarAtual.consultarUmaVez()
        assertTrue(entregas.isEmpty())
        // como a posição usada não foi atualizada, a próxima consulta tenta de novo no mesmo lugar
        lugar = GeocodificadorReverso.Lugar("Icaraí", "Niterói", "")
        lugarAtual.consultarUmaVez()
        assertEquals(3, consultas.size)
        assertEquals(1, entregas.size)
    }

    @Test
    fun `ciclo em thread propria consulta logo, cutuca e se deixa parar e reiniciar`() {
        val leituras = java.util.concurrent.atomic.AtomicInteger()
        val contador = object : FonteDePosicoes {
            override fun temPermissao() = true
            override fun maisRecente(): PosicaoDoAparelho? {
                leituras.incrementAndGet()
                return null
            }
        }
        val l = LugarAtual(contador, { _, _ -> null }, { }, { agora }, {})
        fun esperar(n: Int): Boolean {
            val limite = System.currentTimeMillis() + 5000
            while (leituras.get() < n && System.currentTimeMillis() < limite) Thread.sleep(5)
            return leituras.get() >= n
        }
        l.iniciar()
        l.iniciar() // idempotente
        assertTrue(esperar(1))
        l.cutucarAgora()
        assertTrue(esperar(2))
        l.parar()
        l.iniciar() // reiniciável
        assertTrue(esperar(3))
        l.parar()
        val depois = leituras.get()
        l.cutucarAgora() // parado: sem efeito
        Thread.sleep(100)
        assertEquals(depois, leituras.get())
    }

    // --- O geocodificador com duas fontes ---

    private class FonteFalsa(var presente: Boolean = true, var resposta: GeocodificadorReverso.Lugar? = null, var lanca: Boolean = false) : FonteDeGeocodificacao {
        var chamadas = 0
        override fun presente() = presente
        override fun consultar(lat: Double, lon: Double): GeocodificadorReverso.Lugar? {
            chamadas++
            if (lanca) throw IllegalStateException("sem serviço")
            return resposta
        }
    }

    private val nominatim = ArrayList<Int>()
    private val doNominatim = GeocodificadorReverso.Lugar("N", "N", "N")

    private fun combinado(f: FonteDeGeocodificacao?) = GeocodificadorDoLugar(f, { _, _ -> nominatim.add(1); doNominatim })

    @Test
    fun `usa a fonte do Android quando ela responde`() {
        val f = FonteFalsa(resposta = GeocodificadorReverso.Lugar("A", "B", "C"))
        assertEquals("A", combinado(f).consultar(0.0, 0.0)!!.bairro)
        assertTrue(nominatim.isEmpty())
    }

    @Test
    fun `resposta vazia vale o Nominatim so naquela consulta`() {
        val f = FonteFalsa(resposta = GeocodificadorReverso.Lugar("", "", ""))
        val c = combinado(f)
        assertEquals("N", c.consultar(0.0, 0.0)!!.bairro)
        f.resposta = GeocodificadorReverso.Lugar("A", "B", "C")
        assertEquals("A", c.consultar(0.0, 0.0)!!.bairro)
        assertEquals(2, f.chamadas)
    }

    @Test
    fun `excecao mata a fonte do Android pelo resto da vida`() {
        val f = FonteFalsa(lanca = true)
        val c = combinado(f)
        assertEquals("N", c.consultar(0.0, 0.0)!!.bairro)
        f.lanca = false
        f.resposta = GeocodificadorReverso.Lugar("A", "B", "C")
        assertEquals("N", c.consultar(0.0, 0.0)!!.bairro)
        assertEquals(1, f.chamadas)
    }

    @Test
    fun `fonte ausente do aparelho vai direto ao Nominatim`() {
        val f = FonteFalsa(presente = false)
        val c = combinado(f)
        assertEquals("N", c.consultar(0.0, 0.0)!!.bairro)
        assertEquals("N", combinado(null).consultar(0.0, 0.0)!!.bairro)
        assertEquals(0, f.chamadas)
    }

    @Test
    fun `distancia geografica`() {
        assertEquals(0.0, DistanciaGeografica.metros(-22.9, -43.2, -22.9, -43.2), 1e-6)
        assertEquals(111_195.0, DistanciaGeografica.metros(0.0, 0.0, 1.0, 0.0), 100.0)
        assertEquals(100.0, DistanciaGeografica.metros(-22.9, -43.2, -22.9009, -43.2), 1.0)
    }
}
