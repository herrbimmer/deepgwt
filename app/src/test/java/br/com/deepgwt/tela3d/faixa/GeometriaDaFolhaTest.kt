package br.com.deepgwt.tela3d.faixa

import org.junit.Assert.assertEquals
import org.junit.Test

class GeometriaDaFolhaTest {
    @Test
    fun `no carro 1920 sao 14 colunas e 788 de largura`() {
        assertEquals(14, GeometriaDaFolha.colunas(1920))
        assertEquals(788, GeometriaDaFolha.larguraDp(14))
    }

    @Test
    fun `tela de 600 usa a conta inteira com piso de 7 colunas`() {
        // (600 - 24 - 32 + 6) / 54 = 550 / 54 = 10
        assertEquals(10, GeometriaDaFolha.colunas(600))
        assertEquals(10 * 54 + 32, GeometriaDaFolha.larguraDp(10))
    }

    @Test
    fun `tela estreita nunca desce de 7 colunas`() {
        assertEquals(7, GeometriaDaFolha.colunas(300))
        assertEquals(7, GeometriaDaFolha.colunas(0))
    }

    @Test
    fun `colunas - limite de 14 e transicoes da divisao inteira`() {
        assertEquals(14, GeometriaDaFolha.colunas(5000))
        // 13 colunas até (w - 50)/54 < 14  =>  w - 50 < 756 => w <= 805
        assertEquals(13, GeometriaDaFolha.colunas(805))
        assertEquals(14, GeometriaDaFolha.colunas(806))
    }

    @Test
    fun `bolinha de 28 no carro`() {
        // (788 - 32 - 12)/2 = 372; (372 - 45)/10 = 32 -> limite 28
        assertEquals(28, GeometriaDaFolha.ladoDaBolinhaDp(788))
    }

    @Test
    fun `bolinha cabe na coluna e respeita 20 a 28`() {
        // 7 colunas: largura 410; coluna (410 - 44)/2 = 183; (183 - 45)/10 = 13 -> piso 20
        assertEquals(20, GeometriaDaFolha.ladoDaBolinhaDp(GeometriaDaFolha.larguraDp(7)))
        // 10 colunas: 572; coluna 264; (264 - 45)/10 = 21
        assertEquals(21, GeometriaDaFolha.ladoDaBolinhaDp(GeometriaDaFolha.larguraDp(10)))
    }

    @Test
    fun `posicao x - alinhada ao item quando cabe`() {
        assertEquals(725, GeometriaDaFolha.posicaoX(725, 788, 1920, 12))
    }

    @Test
    fun `posicao x - empurrada para a esquerda quando passa de largura - 12`() {
        assertEquals(1920 - 12 - 788, GeometriaDaFolha.posicaoX(1500, 788, 1920, 12))
    }

    @Test
    fun `posicao x - nunca a menos de 12 da esquerda`() {
        assertEquals(12, GeometriaDaFolha.posicaoX(0, 788, 1920, 12))
        assertEquals(12, GeometriaDaFolha.posicaoX(-50, 788, 1920, 12))
        // folha mais larga que a tela: prevalece a folga esquerda
        assertEquals(12, GeometriaDaFolha.posicaoX(100, 2000, 1920, 12))
    }

    @Test
    fun `posicao - base 8 acima do item e altura limitada a 520`() {
        val p = GeometriaDaFolha.calcular(1920, 340, 551, 788, alturaDoConteudoPx = 700, alturaMaximaPx = 520, folgaLateralPx = 12, folgaAcimaPx = 8)
        assertEquals(520, p.altura)
        assertEquals(551 - 8, p.y + p.altura)
        assertEquals(23, p.y)
        assertEquals(340, p.x)
        assertEquals(788, p.largura)
    }

    @Test
    fun `posicao - conteudo menor que o limite usa a altura do conteudo`() {
        val p = GeometriaDaFolha.calcular(1920, 340, 551, 788, 346, 520, 12, 8)
        assertEquals(346, p.altura)
        assertEquals(551 - 8 - 346, p.y)
    }

    @Test
    fun `posicao - altura limitada pelo espaco livre acima do item (D1)`() {
        val p = GeometriaDaFolha.calcular(1920, 340, 300, 788, 500, 520, 12, 8)
        assertEquals(292, p.altura)
        assertEquals(0, p.y)
        assertEquals(300 - 8, p.y + p.altura)
    }

    @Test
    fun `posicao - sem espaco acima a altura e zero e nao fica negativa`() {
        val p = GeometriaDaFolha.calcular(1920, 340, 4, 788, 500, 520, 12, 8)
        assertEquals(0, p.altura)
        assertEquals(-4, p.y)
    }

    @Test
    fun `a base fica 8 acima do item mesmo quando o conteudo cresce`() {
        val antes = GeometriaDaFolha.calcular(1920, 340, 551, 788, 350, 520, 12, 8)
        val depois = GeometriaDaFolha.calcular(1920, 340, 551, 788, 450, 520, 12, 8)
        assertEquals(antes.y + antes.altura, depois.y + depois.altura)
    }
}
