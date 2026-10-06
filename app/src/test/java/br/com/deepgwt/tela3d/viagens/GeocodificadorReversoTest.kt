package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodificadorReversoTest {
    private fun interpretar(json: String) = GeocodificadorReverso.interpretar(json)

    @Test
    fun `bairro cidade e rua`() {
        val l = interpretar("""{"address":{"suburb":"Icaraí","city":"Niterói","road":"Rua Moreira César"}}""")!!
        assertEquals("Icaraí", l.bairro)
        assertEquals("Niterói", l.cidade)
        assertEquals("Rua Moreira César", l.rua)
        assertEquals("Icaraí", l.rotulo)
        assertFalse(l.vazio)
    }

    @Test
    fun `sem bairro o rotulo e a cidade`() {
        val l = interpretar("""{"address":{"town":"Paraty","footway":"Trilha X"}}""")!!
        assertEquals("", l.bairro)
        assertEquals("Paraty", l.cidade)
        assertEquals("Trilha X", l.rua)
        assertEquals("Paraty", l.rotulo)
    }

    @Test
    fun `endereco sem campos conhecidos e vazio mas existe`() {
        val l = interpretar("""{"address":{"hamlet":"Y"}}""")!!
        assertEquals("", l.rotulo)
        assertTrue(l.vazio)
    }

    @Test
    fun `os valores sao aparados`() {
        assertEquals("Centro", interpretar("""{"address":{"neighbourhood":" Centro "}}""")!!.bairro)
    }

    @Test
    fun `sem address ou nao JSON da nulo`() {
        assertNull(interpretar("""{"error":"Unable to geocode"}"""))
        assertNull(interpretar("isto nao e json"))
        assertNull(interpretar("[1,2]"))
        assertNull(interpretar("""{"address":"texto"}"""))
    }

    @Test
    fun `ordem de preferencia`() {
        val l = interpretar(
            """{"address":{"city_district":"D","neighbourhood":"N","suburb":"S","county":"K","municipality":"M",
               "village":"V","town":"T","city":"C","pedestrian":"P","road":"R","footway":"F"}}"""
        )!!
        assertEquals("S", l.bairro)
        assertEquals("C", l.cidade)
        assertEquals("R", l.rua)
        val m = interpretar("""{"address":{"suburb":"  ","neighbourhood":"N","county":"K","municipality":"M"}}""")!!
        assertEquals("N", m.bairro)
        assertEquals("M", m.cidade)
    }

    @Test
    fun `rotulo cai para a rua`() {
        assertEquals("Rua A", GeocodificadorReverso.Lugar("", "", "Rua A").rotulo)
    }

    @Test
    fun `URL do pedido`() {
        val url = GeocodificadorReverso.montarUrl(-22.9068, -43.1729)
        assertEquals(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=-22.906800&lon=-43.172900&zoom=17" +
                "&addressdetails=1&accept-language=pt-BR",
            url,
        )
    }

    @Test
    fun `a URL nao depende do idioma`() {
        val antes = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
            assertTrue(GeocodificadorReverso.montarUrl(1.5, 2.5).contains("lat=1.500000&lon=2.500000"))
        } finally {
            java.util.Locale.setDefault(antes)
        }
    }

    @Test
    fun `o agente de usuario e proprio`() {
        assertTrue(AgenteDeUsuario.MAPA.startsWith("DeepGWT/"))
        assertTrue(AgenteDeUsuario.LUGAR.startsWith("DeepGWT/"))
        assertFalse(AgenteDeUsuario.MAPA.contains("http"))
        assertFalse(AgenteDeUsuario.LUGAR.contains("http"))
    }

    @Test
    fun `limitador espaca os pedidos em 1100 ms`() {
        var agora = 0L
        val sonos = ArrayList<Long>()
        val l = LimitadorDeTaxa(1100, { agora }, { sonos.add(it); agora += it })
        assertEquals("a", l.executar { "a" })
        assertTrue(sonos.isEmpty())
        agora += 300
        assertEquals("b", l.executar { "b" })
        assertEquals(listOf(800L), sonos)
        agora += 5000
        l.executar { "c" }
        assertEquals(listOf(800L), sonos)
    }

    @Test
    fun `limitador devolve nulo se a thread for interrompida`() {
        var agora = 0L
        val l = LimitadorDeTaxa(1100, { agora }, { throw InterruptedException() })
        l.executar { "a" }
        var rodou = false
        val r = l.executar { rodou = true; "b" }
        assertNull(r)
        assertFalse(rodou)
        Thread.interrupted()
    }

    @Test
    fun `consultar usa a URL montada e interpreta a resposta`() {
        val original = GeocodificadorReverso.transporte
        val urls = ArrayList<String>()
        try {
            GeocodificadorReverso.transporte = { url ->
                urls.add(url)
                """{"address":{"suburb":"Icaraí","city":"Niterói"}}"""
            }
            val l = GeocodificadorReverso.consultar(-22.9, -43.1)!!
            assertEquals("Icaraí", l.rotulo)
            assertEquals(GeocodificadorReverso.montarUrl(-22.9, -43.1), urls.single())
            GeocodificadorReverso.transporte = { null }
            assertNull(GeocodificadorReverso.consultar(-22.9, -43.1))
            GeocodificadorReverso.transporte = { "lixo" }
            assertNull(GeocodificadorReverso.consultar(-22.9, -43.1))
        } finally {
            GeocodificadorReverso.transporte = original
        }
    }
}
