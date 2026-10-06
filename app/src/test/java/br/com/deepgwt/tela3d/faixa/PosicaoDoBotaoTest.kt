package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosicaoDoBotaoTest {
    private val pagina = AreaDoBotao(0, 0, 1920, 660)

    @Test
    fun `posicao padrao - centro da tela e 24 do fundo`() {
        val p = PosicaoDoBotao.padrao(1920, 720, 72, 24)
        assertEquals(924, p.x)
        assertEquals(624, p.y)
    }

    @Test
    fun `limitar mantem o botao dentro da pagina`() {
        val dentro = PosicaoDoBotao.limitar(500, 300, 72, 72, pagina)
        assertEquals(PontoEmPx(500, 300), dentro)
        assertEquals(PontoEmPx(0, 0), PosicaoDoBotao.limitar(-40, -10, 72, 72, pagina))
        assertEquals(PontoEmPx(1920 - 72, 660 - 72), PosicaoDoBotao.limitar(5000, 5000, 72, 72, pagina))
    }

    @Test
    fun `D11 - a posicao padrao atras da barra inferior e puxada para cima da pagina`() {
        val padrao = PosicaoDoBotao.padrao(1920, 720, 72, 24)
        val limitada = PosicaoDoBotao.limitar(padrao.x, padrao.y, 72, 72, pagina)
        assertEquals(924, limitada.x)
        assertEquals(588, limitada.y)
    }

    @Test
    fun `area menor que a janela nao quebra`() {
        val p = PosicaoDoBotao.limitar(50, 50, 400, 400, AreaDoBotao(0, 0, 300, 300))
        assertEquals(PontoEmPx(0, 0), p)
    }

    @Test
    fun `arrasto comeca quando o quadrado da distancia passa de 8 x 8`() {
        assertFalse(PosicaoDoBotao.comecouArrasto(0, 0))
        assertFalse(PosicaoDoBotao.comecouArrasto(8, 0))
        assertFalse(PosicaoDoBotao.comecouArrasto(0, -8))
        assertFalse(PosicaoDoBotao.comecouArrasto(5, 6)) // 25 + 36 = 61
        assertTrue(PosicaoDoBotao.comecouArrasto(8, 1)) // 65
        assertTrue(PosicaoDoBotao.comecouArrasto(-9, 0))
        assertTrue(PosicaoDoBotao.comecouArrasto(6, 6)) // 72
    }

    @Test
    fun `D9 - com o menu aberto o botao nao se mexe`() {
        val botao = PontoEmPx(924, 588)
        val janela = PosicaoDoBotao.janelaComMenu(botao, ladoDoBotaoPx = 72, larguraDoMenuPx = 300, alturaDoMenuPx = 250, folgaDoMenuPx = 10)
        // o botão fica na janela centrado na coluna e 10 abaixo do menu
        val botaoNaJanelaX = janela.x + (300 - 72) / 2
        val botaoNaJanelaY = janela.y + 250 + 10
        assertEquals(botao.x, botaoNaJanelaX)
        assertEquals(botao.y, botaoNaJanelaY)
        assertEquals(810, janela.x)
        assertEquals(328, janela.y)
    }

    @Test
    fun `largura de 300 centrada no botao`() {
        val janela = PosicaoDoBotao.janelaComMenu(PontoEmPx(924, 600), 72, 300, 200, 10)
        assertEquals(924 - 114, janela.x)
    }
}
