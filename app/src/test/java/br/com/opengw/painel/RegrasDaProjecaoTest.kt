package br.com.opengw.painel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V8, V9 (projeção e cartão), V10 (apps) e V11 (tela) de `projetores-painel.md`. */
class RegrasDaProjecaoTest {

    @Test
    fun v8_segurancaDoCarPlay() {
        assertTrue(SegurancaDoCarPlay.vale(1_000, 7_000, desejadoNoPainel = true, preparando = false))
        assertTrue(SegurancaDoCarPlay.vale(1_000, 7_000, desejadoNoPainel = false, preparando = true))
        assertFalse(SegurancaDoCarPlay.vale(1_000, 7_000, desejadoNoPainel = false, preparando = false))
        assertFalse(SegurancaDoCarPlay.vale(1_000, 14_001, desejadoNoPainel = true, preparando = false))
        assertFalse(SegurancaDoCarPlay.vale(0, 7_000, desejadoNoPainel = true, preparando = false))
        assertTrue(SegurancaDoCarPlay.vale(1_000, 13_000, desejadoNoPainel = true, preparando = false))
    }

    private val nada = EstadoDaProjecao.NENHUMA
    private val carPlay = EstadoDaProjecao(carPlay = true, espelho = false, preparando = false)

    /** empurra / força / visib. / ressincroniza / entrada. */
    private fun confere(d: DecisaoDoCartao, vararg esperado: Boolean) {
        assertEquals(esperado.toList(), listOf(d.empurrar, d.forcar, d.visibilidade, d.ressincronizar, d.entrada))
    }

    @Test
    fun v9_decisaoDoCartao() {
        confere(DecisaoDoCartao.decidir(1, 3, nada, nada, false), false, false, false, false, true)
        confere(DecisaoDoCartao.decidir(1, 3, carPlay, nada, false), true, true, true, false, true)
        confere(DecisaoDoCartao.decidir(3, 0, nada, carPlay, false), true, false, true, false, false)
        confere(DecisaoDoCartao.decidir(1, 3, nada, nada, true), false, false, false, false, true)
        confere(DecisaoDoCartao.decidir(1, 0, nada, nada, true), false, false, true, true, false)
        confere(DecisaoDoCartao.decidir(0, 1, nada, nada, true), false, false, true, true, true)
        confere(DecisaoDoCartao.decidir(3, 3, nada, nada, true), false, false, false, false, true)
        // Esquecido no fim da carga: não é "velha".
        assertFalse(DecisaoDoCartao.decidir(3, 0, nada, null, false).velha)
    }

    private fun app(esq: Int, topo: Int, dir: Int, base: Int, display: Int = 3, vivo: IntArray? = null) =
        AppNoPainel("app.$esq", display, intArrayOf(esq, topo, dir, base), vivo)

    private fun cobertura(apps: List<AppNoPainel>, cartao: Int, espelho: Boolean = false, rect: IntArray? = null) =
        RegrasDosApps.cobertura(apps, { 1920 }, cartao, espelho) { rect }

    @Test
    fun v10_coberturaApertoEVao() {
        data class Caso(val app: AppNoPainel, val cartao: Int, val inDash: Any, val largura: Int, val vao: Retangulo)
        val casos = listOf(
            Caso(app(0, 62, 1920, 658), 1, true, 1920, Retangulo(0, 62, 1920, 658)),
            Caso(app(0, 62, 1920, 658), 0, true, 1344, Retangulo(0, 62, 1344, 658)),
            Caso(app(640, 95, 1280, 655), 1, false, 640, Retangulo(640, 95, 1280, 655)),
            Caso(app(640, 95, 1280, 655), 0, "right", 640, Retangulo(640, 95, 1280, 655)),
            Caso(app(1400, 0, 1920, 720), 1, "right", 520, Retangulo(1400, 0, 1920, 720)),
            Caso(app(100, 0, 1300, 720), 1, "left", 1200, Retangulo(100, 0, 1300, 720)),
            Caso(app(1300, 0, 1400, 720), 0, "right", 100, Retangulo(1300, 0, 1400, 720)),
        )
        for (c in casos) {
            val r = cobertura(listOf(c.app), c.cartao)
            assertEquals("${c.app.configurado.toList()} cartão ${c.cartao}", c.inDash, r.appInDash)
            assertEquals(c.largura, RegrasDosApps.alvo(c.app, c.cartao, 1920)!!.largura)
            assertEquals(c.vao, r.vao)
        }
        val carPlayVivo = cobertura(emptyList(), 1, rect = intArrayOf(0, 0, 1920, 720))
        assertEquals(true, carPlayVivo.appInDash)
        assertEquals(Retangulo(0, 0, 1920, 720), carPlayVivo.vao)
        val espelho = cobertura(emptyList(), 0, espelho = true)
        assertEquals(true, espelho.appInDash)
        assertEquals(null, espelho.vao)
        assertEquals(false, cobertura(emptyList(), 1).appInDash)
    }

    @Test
    fun coberturaUsaOVivoQuandoHa() {
        val r = cobertura(listOf(app(640, 95, 1280, 655, vivo = intArrayOf(0, 95, 640, 655))), 1)
        assertEquals("left", r.appInDash)
    }

    @Test
    fun displayDeLarguraZeroEhPulado() {
        val r = RegrasDosApps.cobertura(listOf(app(0, 0, 1920, 720, display = 1)), { 0 }, 1, false) { null }
        assertEquals(false, r.appInDash)
    }

    private fun tela(id: Int, w: Int = 1920, h: Int = 720, privada: Boolean = false, unico: String? = null) =
        TelaConhecida(id, w, h, privada, unico)

    @Test
    fun v11_escolhaDaTela() {
        val base = listOf(tela(0), tela(1))
        assertEquals(EscolhaDaTela.Resultado(3, false), EscolhaDaTela.escolher(-1, base + tela(3, unico = "local:3")))
        assertEquals(EscolhaDaTela.Resultado(2, false), EscolhaDaTela.escolher(-1, base + tela(2, unico = "local:3")))
        assertEquals(EscolhaDaTela.Resultado(3, false), EscolhaDaTela.escolher(-1, base + tela(2, 1280) + tela(3)))
        assertEquals(EscolhaDaTela.Resultado(3, false), EscolhaDaTela.escolher(5, base + tela(3, unico = "local:3")))
        assertEquals(EscolhaDaTela.Resultado(3, true), EscolhaDaTela.escolher(0, base + tela(3, unico = "local:3")))
        assertEquals(EscolhaDaTela.Resultado(3, false), EscolhaDaTela.escolher(-1, base))
        assertEquals(EscolhaDaTela.Resultado(3, false), EscolhaDaTela.escolher(-1, base + tela(4, privada = true)))
        assertEquals(EscolhaDaTela.Resultado(5, false), EscolhaDaTela.escolher(5, base + tela(5) + tela(3, unico = "local:3")))
    }

    @Test
    fun simuladorAchaAOverlayOuACentral() {
        assertEquals(2, EscolhaDaTela.escolherNoSimulador(listOf(tela(0), tela(2))))
        assertEquals(0, EscolhaDaTela.escolherNoSimulador(listOf(tela(0))))
        assertTrue(EscolhaDaTela.ehPainel(tela(2), listOf(tela(0), tela(1), tela(2))))
    }
}
