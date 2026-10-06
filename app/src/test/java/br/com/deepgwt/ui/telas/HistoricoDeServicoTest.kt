package br.com.deepgwt.ui.telas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoricoDeServicoTest {

    private val compra = RegistroDeServico(1, TipoDeServico.COMPRA, 0, 1_741_611_600_000)
    private val revisao = RegistroDeServico(2, TipoDeServico.REVISAO, 12_000, 1_773_147_600_000, "Revisão dos 12 mil", "Autorizada", 45_050)
    private val oleo = RegistroDeServico(3, TipoDeServico.OLEO, 18_000, 1_780_000_000_000)

    @Test
    fun `json de ida e volta`() {
        val lista = listOf(compra, revisao, oleo)
        assertEquals(lista, HistoricoDeServico.deJson(HistoricoDeServico.paraJson(lista)))
    }

    @Test
    fun `leitura tolerante`() {
        assertEquals(emptyList<RegistroDeServico>(), HistoricoDeServico.deJson(null))
        assertEquals(emptyList<RegistroDeServico>(), HistoricoDeServico.deJson("lixo"))
        val r = HistoricoDeServico.deJson("""[{"tipo":"inventado","km":-5,"data":10},"x"]""").single()
        assertEquals(TipoDeServico.OUTRO, r.tipo)
        assertEquals(0, r.km)
        assertEquals(1L, r.id)
    }

    @Test
    fun `so compra e revisao contam para a proxima`() {
        assertEquals(ProximaRevisao(0, 0), HistoricoDeServico.proxima(listOf(oleo)))
        assertEquals(ProximaRevisao(12_000, Revisoes.proxima(listOf(Revisao(0, compra.data))).data), HistoricoDeServico.proxima(listOf(compra, oleo)))
        assertEquals(24_000, HistoricoDeServico.proxima(listOf(compra, revisao, oleo)).km)
    }

    @Test
    fun `ordem do mais recente ao mais antigo`() {
        assertEquals(listOf(oleo, revisao, compra), HistoricoDeServico.ordenar(listOf(compra, revisao, oleo)))
    }

    @Test
    fun `migra o historico antigo`() {
        val prefs = PreferenciasFalsas()
        Revisoes.gravar(prefs, listOf(Revisao(0, 100), Revisao(12_000, 200)))
        val lido = HistoricoDeServico.ler(prefs)
        assertEquals(listOf(TipoDeServico.COMPRA, TipoDeServico.REVISAO), lido.map { it.tipo })
    }

    @Test
    fun `adicionar e apagar publicam a proxima e espelham o historico antigo`() {
        val prefs = PreferenciasFalsas()
        HistoricoDeServico.adicionar(prefs, compra.copy(id = 0))
        val depois = HistoricoDeServico.adicionar(prefs, oleo.copy(id = 0))
        assertEquals(listOf(1L, 2L), depois.map { it.id })
        assertEquals(12_000, prefs.valores["instrumentRevisionKm"])
        // O óleo não entra no espelho antigo, que só conhece revisão e compra.
        assertEquals(listOf(Revisao(0, compra.data)), Revisoes.ler(prefs))
        HistoricoDeServico.apagar(prefs, 1)
        assertEquals(0, prefs.valores["instrumentRevisionKm"])
        assertEquals(listOf(oleo.copy(id = 2)), HistoricoDeServico.ler(prefs))
    }

    @Test
    fun `custo em centavos`() {
        assertEquals(0L, HistoricoDeServico.centavosDe(""))
        assertEquals(12_300L, HistoricoDeServico.centavosDe("123"))
        assertEquals(12_340L, HistoricoDeServico.centavosDe("123,4"))
        assertEquals(123_456L, HistoricoDeServico.centavosDe("R$ 1234.56"))
        assertNull(HistoricoDeServico.centavosDe("1,234"))
        assertNull(HistoricoDeServico.centavosDe("abc"))
        assertNull(HistoricoDeServico.centavosDe("-5"))
        assertEquals("R$ 1.234,56", HistoricoDeServico.reais(123_456))
        assertEquals("R$ 0,05", HistoricoDeServico.reais(5))
        assertTrue(TipoDeServico.entries.map { it.id }.toSet().size == TipoDeServico.entries.size)
    }
}
