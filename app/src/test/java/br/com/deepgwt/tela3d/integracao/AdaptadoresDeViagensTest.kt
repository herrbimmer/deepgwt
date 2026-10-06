package br.com.deepgwt.tela3d.integracao

import br.com.deepgwt.tela3d.viagens.FachadaDeViagens
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptadoresDeViagensTest {

    private class FachadaFalsa : FachadaDeViagens {
        val chamadas = ArrayList<String>()
        override fun finalizarViagem() { chamadas += "finalizar" }
        override fun diasEmJson(de: String?, ate: String?): String { chamadas += "dias($de,$ate)"; return "D" }
        override fun viagemAoVivoEmJson(): String { chamadas += "aoVivo"; return "" }
        override fun mesesEmJson(): String { chamadas += "meses"; return "M" }
        override fun totaisDeParadasEmJson(desdeMs: String?): String { chamadas += "paradas($desdeMs)"; return "P" }
        override fun totaisEmJson(): String { chamadas += "totais"; return "T" }
        override fun viagemEmJson(inicioMs: String?, maxPontos: Int): String { chamadas += "viagem($inicioMs,$maxPontos)"; return "V" }
        override fun mapaDaViagem(inicioMs: String?): String { chamadas += "mapa($inicioMs)"; return "MAPA" }
        override fun pontosDesdeEmJson(inicioMs: String?, ultimoT: String?, maxPontos: Int): String {
            chamadas += "pontos($inicioMs,$ultimoT,$maxPontos)"
            return "PT"
        }
        override fun viagensEmJson(deslocamento: Int, limite: Int): String { chamadas += "lista($deslocamento,$limite)"; return "L" }
    }

    @Test
    fun `os numeros das chaves de viagem seguem como texto para a fachada`() {
        val f = FachadaFalsa()
        var salvou = 0
        val v = ViagensDaPonte(f) { salvou++ }
        assertEquals("D", v.dias("2026-10-01", "2026-10-04"))
        assertEquals("", v.aoVivo())
        assertEquals("M", v.meses())
        assertEquals("P", v.totaisDeParadas(1_700_000_000_000L))
        assertEquals("T", v.totais())
        assertEquals("V", v.viagem(1_700_000_000_123L, 1500))
        assertEquals("MAPA", v.mapaDaViagem(42L))
        assertEquals("PT", v.pontosDesde(10L, 20L, 300))
        assertEquals("L", v.lista(5, 50))
        v.encerrarViagem()
        v.gravarEmBreve()
        assertEquals(1, salvou)
        assertEquals(
            listOf(
                "dias(2026-10-01,2026-10-04)", "aoVivo", "meses", "paradas(1700000000000)", "totais",
                "viagem(1700000000123,1500)", "mapa(42)", "pontos(10,20,300)", "lista(5,50)", "finalizar",
            ),
            f.chamadas,
        )
    }

    private class ServicoFalso : ServicoDeLugar {
        var iniciou = 0
        var parou = 0
        var cutucou = 0
        override fun iniciar() { iniciou++ }
        override fun parar() { parou++ }
        override fun cutucarAgora() { cutucou++ }
    }

    @Test
    fun `iniciar de novo troca o retorno e o json do lugar chega ao mais recente`() {
        val servico = ServicoFalso()
        val lugar = LugarDaPonte(servico)
        val recebidos = ArrayList<String>()
        lugar.iniciar { recebidos += "a:$it" }
        lugar.entregar("{1}")
        lugar.iniciar { recebidos += "b:$it" }
        lugar.entregar("{2}")
        assertEquals(listOf("a:{1}", "b:{2}"), recebidos)
        assertEquals(2, servico.iniciou)
    }

    @Test
    fun `depois de parar nada e entregue e consultar ja cutuca o servico`() {
        val servico = ServicoFalso()
        val lugar = LugarDaPonte(servico)
        val recebidos = ArrayList<String>()
        lugar.iniciar { recebidos += it }
        lugar.consultarJa()
        lugar.parar()
        lugar.entregar("{x}")
        assertEquals(1, servico.cutucou)
        assertEquals(1, servico.parou)
        assertEquals(emptyList<String>(), recebidos)
    }
}
