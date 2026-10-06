package br.com.deepgwt.tela3d.viagens

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjecaoDoMapaTest {
    private val p = ProjecaoDoMapa

    @Test
    fun `mundo no zoom zero e no tres`() {
        assertEquals(128.0, p.mundoX(0.0, 0), 1e-9)
        assertEquals(128.0, p.mundoY(0.0, 0), 1e-9)
        assertEquals(1024.0, p.mundoX(0.0, 3), 1e-9)
        assertEquals(1024.0, p.mundoY(0.0, 3), 1e-9)
        assertEquals(1_048_576.0, p.mundoX(0.0, 13), 1e-6)
        assertEquals(1_048_576.0, p.mundoY(0.0, 13), 1e-6)
    }

    @Test
    fun `mundo em pontos conhecidos`() {
        assertEquals(97.299271, p.mundoX(-43.1729, 0), 1e-6)
        assertEquals(144.741413, p.mundoY(-22.9068, 0), 1e-6)
        assertEquals(797075.628942, p.mundoX(-43.1729, 13), 1e-6)
        assertEquals(1185721.654002, p.mundoY(-22.9068, 13), 1e-6)
        assertEquals(130978.938880, p.mundoX(-0.1278, 10), 1e-6)
        assertEquals(87169.570601, p.mundoY(51.5074, 10), 1e-6)
    }

    @Test
    fun `latitude e limitada nos polos`() {
        assertEquals(256.0, p.mundoY(-90.0, 0), 1e-4)
        assertEquals(0.0, Math.abs(p.mundoY(90.0, 0)), 1e-4)
    }

    private fun zoom(latMin: Double, latMax: Double, lonMin: Double, lonMax: Double) =
        p.escolherZoom(CaixaGeografica(latMin, latMax, lonMin, lonMax))

    @Test
    fun `zoom escolhido`() {
        assertEquals(15, zoom(-22.91, -22.90, -43.18, -43.17))
        assertEquals(7, zoom(-23.55, -22.90, -46.63, -43.17))
        assertEquals(17, zoom(-22.9068, -22.9068, -43.1729, -43.1729))
        assertEquals(12, zoom(-22.95, -22.85, -43.25, -43.10))
        assertEquals(3, zoom(-34.0, 5.0, -74.0, -34.0))
    }

    @Test
    fun `ponto unico com maximo 16 e folga 32`() {
        val caixa = CaixaGeografica(-22.9068, -22.9068, -43.1729, -43.1729)
        assertEquals(16, p.escolherZoom(caixa, folga = 32, zoomMax = 16))
    }

    @Test
    fun `quadro do mapa`() {
        val q = p.enquadrar(CaixaGeografica(-22.95, -22.85, -43.25, -43.10))
        assertEquals(12, q.zoom)
        assertEquals(398211.698, q.ox, 1e-3)
        assertEquals(592639.355, q.oy, 1e-3)
        assertEquals("12,398211.698,592639.355", q.texto)
    }

    @Test
    fun `o texto do quadro nao depende do idioma`() {
        val antes = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("pt-BR"))
            assertEquals("5,1.500,2.000", Enquadramento(5, 1.5, 2.0).texto)
        } finally {
            java.util.Locale.setDefault(antes)
        }
    }

    @Test
    fun `ponto unico fica centrado`() {
        val q = p.enquadrar(listOf(Fixo(-22.9068, -43.1729), Fixo(-22.9068, -43.1729)))!!
        assertEquals(17, q.zoom)
        assertEquals(p.mundoX(-43.1729, 17) - 320.0, q.ox, 1e-6)
        assertEquals(p.mundoY(-22.9068, 17) - 200.0, q.oy, 1e-6)
    }
}
