package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReservasTest {
    /** O carro nominal 1920×720, barra inferior ligada (página 0,0–1920,660), densidade 1, trilho 128. */
    private val carroComBarra = MedidasDaTela(
        densidade = 1f, larguraRealPx = 1920, alturaRealPx = 720,
        paginaEsq = 0, paginaTopo = 0, paginaDir = 1920, paginaBase = 660,
        larguraDoTrilhoDimenPx = 128, maiorInsetEsquerdoPx = 0, maiorInsetSuperiorPx = 0,
        alturaBarraStatusPx = 0, barraStatusVisivel = false, modo = ModoDaCasca.APP_E_CARRO,
    )

    @Test
    fun `carro com barra - faixa 148, margem e banda zeradas, insets 128, reserva 224`() {
        val r = calcularReservas(carroComBarra)
        assertEquals(148, r.alturaDaFaixaPx)
        assertEquals(0, r.margemInferiorPx)
        assertEquals(0, r.alturaDaBandaPx)
        assertEquals(128, r.insetEsquerdoPx)
        assertEquals(128, r.insetDireitoPx)
        assertEquals(224, r.reservaDoDockPx)
        assertEquals(164f, r.safeBottomCss, 0.001f)
        assertEquals(20f, r.launcherBottomCss, 0.001f)
        assertEquals(22f, r.safeTopCss, 0.001f)
        assertEquals(128f, r.safeLeftCss, 0.001f)
        assertEquals(128f, r.safeRightCss, 0.001f)
    }

    @Test
    fun `sem barra - margem 60, banda 60, safeBottom 224 e launcherBottom 80`() {
        val r = calcularReservas(carroComBarra.copy(paginaBase = 720))
        assertEquals(60, r.margemInferiorPx)
        assertEquals(60, r.alturaDaBandaPx)
        assertEquals(224f, r.safeBottomCss, 0.001f)
        assertEquals(80f, r.launcherBottomCss, 0.001f)
    }

    @Test
    fun `lado a lado - reserva do dock zero e safeBottom zero`() {
        val r = calcularReservas(carroComBarra.copy(modo = ModoDaCasca.LADO_A_LADO))
        assertEquals(0, r.reservaDoDockPx)
        assertEquals(0f, r.safeBottomCss, 0.001f)
        // a faixa em si e as laterais não mudam
        assertEquals(148, r.alturaDaFaixaPx)
        assertEquals(128, r.insetEsquerdoPx)
    }

    @Test
    fun `densidade 1,5 - 222, 90 e 336 com arredondamento`() {
        val r = calcularReservas(carroComBarra.copy(densidade = 1.5f, alturaRealPx = 1080, paginaBase = 1080))
        assertEquals(222, r.alturaDaFaixaPx)
        assertEquals(90, r.margemInferiorPx)
        assertEquals(336, r.reservaDoDockPx)
        // 128 dp a 1,5 = 192 px de trilho
        assertEquals(192, r.insetEsquerdoPx)
        assertEquals(128f, r.safeLeftCss, 0.001f)
    }

    @Test
    fun `densidade com meio pixel arredonda ao inteiro mais proximo`() {
        // 148 * 1,25 = 185; 60 * 1,25 = 75; 16 * 1,25 = 20
        val r = calcularReservas(carroComBarra.copy(densidade = 1.25f, alturaRealPx = 900, paginaBase = 900))
        assertEquals(185, r.alturaDaFaixaPx)
        assertEquals(75, r.margemInferiorPx)
        assertEquals(185 + 75 + 20, r.reservaDoDockPx)
    }

    @Test
    fun `trilho e o maior entre a dimensao do sistema, o maior inset e 128`() {
        assertEquals(128, trilhoLateralPx(carroComBarra.copy(larguraDoTrilhoDimenPx = 100, maiorInsetEsquerdoPx = 90)))
        assertEquals(150, trilhoLateralPx(carroComBarra.copy(larguraDoTrilhoDimenPx = 150, maiorInsetEsquerdoPx = 90)))
        assertEquals(200, trilhoLateralPx(carroComBarra.copy(larguraDoTrilhoDimenPx = 150, maiorInsetEsquerdoPx = 200)))
    }

    @Test
    fun `insets laterais descontam o quanto a pagina ja se afasta da borda`() {
        val r = calcularReservas(carroComBarra.copy(paginaEsq = 40, paginaDir = 1900))
        assertEquals(88, r.insetEsquerdoPx) // 128 - 40
        assertEquals(108, r.insetDireitoPx) // 128 - (1920 - 1900)
        val colada = calcularReservas(carroComBarra.copy(paginaEsq = 300, paginaDir = 1500))
        assertEquals(0, colada.insetEsquerdoPx)
        assertEquals(0, colada.insetDireitoPx)
    }

    @Test
    fun `safeTop soma o inset da barra de status quando visivel`() {
        val visivel = calcularReservas(
            carroComBarra.copy(barraStatusVisivel = true, alturaBarraStatusPx = 57, paginaTopo = 0),
        )
        assertEquals(57f + 22f, visivel.safeTopCss, 0.001f)
        val descontada = calcularReservas(
            carroComBarra.copy(barraStatusVisivel = true, alturaBarraStatusPx = 57, paginaTopo = 57),
        )
        assertEquals(22f, descontada.safeTopCss, 0.001f)
        val oculta = calcularReservas(carroComBarra.copy(barraStatusVisivel = false, alturaBarraStatusPx = 57))
        assertEquals(22f, oculta.safeTopCss, 0.001f)
    }

    @Test
    fun `margem compensada nunca fica negativa`() {
        assertEquals(0, margemInferiorCompensadaPx(carroComBarra, 40))
        assertEquals(20, margemInferiorCompensadaPx(carroComBarra, 80))
        assertEquals(60, margemInferiorCompensadaPx(carroComBarra.copy(paginaBase = 720), 60))
    }

    @Test
    fun `limites dos popups - topo e fundo com a faixa medida`() {
        val m = carroComBarra.copy(alturaBarraStatusPx = 57, maiorInsetSuperiorPx = 60, paginaTopo = 57)
        val (topo, fundo) = limitesDosPopupsCss(m, topoDaFaixaNaTelaPx = 569)
        assertEquals(3f, topo, 0.001f) // max(57, 60) - 57
        assertEquals(569f - 57f - 12f, fundo, 0.001f)
    }

    @Test
    fun `limites dos popups - fundo nunca abaixo do topo`() {
        val (topo, fundo) = limitesDosPopupsCss(carroComBarra.copy(alturaBarraStatusPx = 100), topoDaFaixaNaTelaPx = 60)
        assertEquals(100f, topo, 0.001f)
        assertEquals(100f, fundo, 0.001f)
    }

    @Test
    fun `limites dos popups - faixa ainda nao medida usa a altura da pagina`() {
        val (_, fundo) = limitesDosPopupsCss(carroComBarra, topoDaFaixaNaTelaPx = null)
        assertEquals(660f, fundo, 0.001f)
    }

    @Test
    fun `limites dos popups - no carro com a faixa em y 512`() {
        val (topo, fundo) = limitesDosPopupsCss(carroComBarra, topoDaFaixaNaTelaPx = 512)
        assertEquals(0f, topo, 0.001f)
        assertEquals(500f, fundo, 0.001f)
        assertNotEquals(660f, fundo)
    }

    @Test
    fun `densidade maior converte os limites para px CSS`() {
        val m = carroComBarra.copy(densidade = 2f, alturaBarraStatusPx = 114, paginaTopo = 0, paginaBase = 1320, alturaRealPx = 1440, larguraRealPx = 3840, paginaDir = 3840)
        val (topo, fundo) = limitesDosPopupsCss(m, topoDaFaixaNaTelaPx = 1024)
        assertEquals(57f, topo, 0.001f)
        assertEquals(1024f / 2f - 12f, fundo, 0.001f)
    }
}
