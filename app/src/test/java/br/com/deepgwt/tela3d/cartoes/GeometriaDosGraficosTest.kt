package br.com.deepgwt.tela3d.cartoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometriaDosGraficosTest {
    private val g = GeometriaDosGraficos

    // --- ventoinha ---

    @Test fun `ventoinha centrada com disco de 0,44`() {
        val v = g.ventoinha(70f, 70f)
        assertEquals(35f, v.cx, 0f)
        assertEquals(35f, v.cy, 0f)
        assertEquals(30.8f, v.raioDoDisco, 1e-4f)
        assertEquals(0.14f * 30.8f, v.raioDoCubo, 1e-4f)
        assertEquals(0.2f * 30.8f, v.raioDaPa, 1e-4f)
        assertEquals(-0.78f * 30.8f, v.topoDaPa, 1e-4f)
        assertEquals(-0.08f * 30.8f, v.baseDaPa, 1e-4f)
        assertEquals(listOf(0f, 120f, 240f), v.angulosDasPas.toList())
    }

    // --- colunas ---

    @Test fun `sem valores sao sete pontinhos fracos`() {
        val c = g.colunasDeEnergia(emptyList(), 70f, 70f, 1f)
        assertEquals(7, c.size)
        assertTrue(c.all { it.cor == CorDaColuna.FRACA })
        // altura mínima = largura (bolinha)
        assertTrue(c.all { Math.abs(it.retangulo.altura - it.retangulo.largura) < 1e-4f })
        assertTrue(c.all { Math.abs(it.retangulo.base - 0.88f * 70f) < 1e-4f })
    }

    @Test fun `no maximo as doze ultimas colunas`() {
        val valores = (1..20).map { it * 5 }
        val c = g.colunasDeEnergia(valores, 70f, 70f, 1f)
        assertEquals(12, c.size)
        // a última é a de valor 100 = altura máxima 0,72·H
        assertEquals(0.72f * 70f, c.last().retangulo.altura, 1e-3f)
        assertEquals(CorDaColuna.ACENTO, c.last().cor)
    }

    @Test fun `ultima coluna em acento outras com valor e zeros fracos`() {
        val c = g.colunasDeEnergia(listOf(0, 50, 0, 80), 70f, 70f, 1f)
        assertEquals(
            listOf(CorDaColuna.FRACA, CorDaColuna.COM_VALOR, CorDaColuna.FRACA, CorDaColuna.ACENTO),
            c.map { it.cor },
        )
        // valor 0 é bolinha; valor 50 é metade da altura máxima
        assertEquals(c[0].retangulo.largura, c[0].retangulo.altura, 1e-4f)
        assertEquals(0.36f * 70f, c[1].retangulo.altura, 1e-3f)
    }

    @Test fun `ultima coluna com zero tambem e acento se ha valores`() {
        val c = g.colunasDeEnergia(listOf(0, 0, 0), 70f, 70f, 1f)
        assertEquals(CorDaColuna.ACENTO, c.last().cor)
        assertEquals(CorDaColuna.FRACA, c.first().cor)
    }

    @Test fun `colunas centradas com vao e largura da spec`() {
        val c = g.colunasDeEnergia(listOf(10, 20, 30, 40), 70f, 70f, 1f)
        val vao = Math.max(2f, 0.03f * 70f)
        val larg = Math.max(2.5f, (0.92f * 70f - 3 * vao) / 4f)
        assertEquals(larg, c[0].retangulo.largura, 1e-4f)
        assertEquals(vao, c[1].retangulo.esq - c[0].retangulo.dir, 1e-4f)
        val total = 4 * larg + 3 * vao
        assertEquals((70f - total) / 2f, c[0].retangulo.esq, 1e-4f)
        assertEquals(larg / 2f, c[0].raio, 1e-4f)
    }

    @Test fun `valores fora da faixa sao limitados`() {
        val c = g.colunasDeEnergia(listOf(500, -20), 70f, 70f, 1f)
        assertEquals(0.72f * 70f, c[0].retangulo.altura, 1e-3f)
        assertEquals(c[1].retangulo.largura, c[1].retangulo.altura, 1e-4f)
    }

    // --- anel ---

    @Test fun `anel de 0,88 com espessura de 12 por cento`() {
        val a = g.anel(70f, 70f, 1f)
        assertEquals(61.6f, a.diametro, 1e-4f)
        assertEquals(0.12f * 61.6f, a.espessura, 1e-4f)
        assertEquals((61.6f - a.espessura) / 2f, a.raioDoTraco, 1e-4f)
        // espessura mínima de 3 dp
        assertEquals(3f, g.anel(20f, 20f, 1f).espessura, 1e-4f)
        assertEquals(6f, g.anel(20f, 20f, 2f).espessura, 1e-4f)
    }

    @Test fun `varredura do progresso`() {
        assertEquals(0f, g.varreduraDoProgresso(0), 0f)
        assertEquals(180f, g.varreduraDoProgresso(50), 1e-4f)
        assertEquals(360f, g.varreduraDoProgresso(100), 1e-4f)
        assertEquals(360f, g.varreduraDoProgresso(300), 1e-4f)
    }

    @Test fun `raio do cartao de autonomia`() {
        val d = 61.6f
        val u = 0.22f * d
        val r = g.raio(d)
        assertEquals(6, r.size)
        assertEquals(Ponto(0.15f * u, -u), r[0])
        assertEquals(Ponto(-0.55f * u, 0.12f * u), r[1])
        assertEquals(Ponto(0.55f * u, -0.15f * u), r[4])
    }

    // --- grade ---

    @Test fun `grade de duas por duas centrada`() {
        val gr = g.grade(70f, 70f)
        assertEquals(4, gr.celulas.size)
        val vao = 0.08f * 70f
        val c = (0.78f * 70f - vao) / 2f
        assertEquals(c, gr.celulas[0].largura, 1e-4f)
        assertEquals(vao, gr.celulas[1].esq - gr.celulas[0].dir, 1e-4f)
        assertEquals(0.3f * c, gr.raioDosCantos, 1e-4f)
        // centrada
        assertEquals(35f, (gr.celulas[0].esq + gr.celulas[3].dir) / 2f, 1e-4f)
        assertEquals(35f, (gr.celulas[0].topo + gr.celulas[3].base) / 2f, 1e-4f)
    }

    // --- modos ---

    @Test fun `estado conhecido`() {
        assertFalse(g.estadoConhecido(""))
        assertFalse(g.estadoConhecido("unknown"))
        assertTrue(g.estadoConhecido("eco"))
    }

    @Test fun `glifo de 0,68 com traco minimo`() {
        assertEquals(47.6f, g.ladoDoGlifo(70f, 70f), 1e-4f)
        assertEquals(0.085f * 47.6f, g.espessuraDoGlifo(47.6f, 1f), 1e-4f)
        assertEquals(2f, g.espessuraDoGlifo(10f, 1f), 1e-4f)
    }

    @Test fun `etiqueta do awd`() {
        val e = g.etiqueta(70f, 70f, 1f, 0.5f, 0.44f, 0.6f, 0.4f)
        assertEquals(42f, e.retangulo.largura, 1e-4f)
        assertEquals(28f, e.retangulo.altura, 1e-4f)
        assertEquals(35f, e.retangulo.centroX, 1e-4f)
        assertEquals(0.44f * 70f, e.retangulo.centroY, 1e-4f)
        assertEquals(0.3f * 28f, e.raioDosCantos, 1e-4f)
        assertEquals(0.62f * 28f, e.tamanhoDoTexto, 1e-4f)
        assertEquals(1.5f, e.espessuraDoContorno, 1e-4f)
    }

    @Test fun `texto da etiqueta encolhe na proporcao`() {
        assertEquals(10f, g.tamanhoDoTextoDaEtiqueta(10f, 30f, 42f), 0f)
        // limite = 0,78 × 42 = 32,76; medida 65,52 → metade
        assertEquals(5f, g.tamanhoDoTextoDaEtiqueta(10f, 65.52f, 42f), 1e-4f)
    }

    @Test fun `alfa da etiqueta`() {
        assertEquals(34, g.alfaDaEtiqueta(true))
        assertEquals(20, g.alfaDaEtiqueta(false))
    }

    @Test fun `texto da etiqueta de potencia`() {
        assertEquals("HEV", g.textoDaPotencia("hev"))
        assertEquals("EVP", g.textoDaPotencia("evp"))
        assertEquals("EV", g.textoDaPotencia("ev"))
        assertNull(g.textoDaPotencia("unknown"))
        assertNull(g.textoDaPotencia(""))
        assertNull(g.textoDaPotencia("eco"))
    }

    // --- regeneração ---

    @Test fun `nivel da regeneracao`() {
        assertEquals(1, g.nivelDaRegeneracao("level1"))
        assertEquals(2, g.nivelDaRegeneracao("level2"))
        assertEquals(3, g.nivelDaRegeneracao("level3"))
        assertEquals(3, g.nivelDaRegeneracao("onepedal"))
        assertEquals(0, g.nivelDaRegeneracao("unknown"))
        assertEquals(0, g.nivelDaRegeneracao(""))
    }

    @Test fun `tres barras de regeneracao`() {
        val b = g.barrasDeRegeneracao(70f, 70f, 1f)
        assertEquals(3, b.barras.size)
        val larg = 0.17f * 70f
        assertTrue(b.barras.all { Math.abs(it.largura - larg) < 1e-4f })
        assertEquals(0.1f * 70f, b.barras[1].esq - b.barras[0].dir, 1e-4f)
        assertTrue(b.barras.all { Math.abs(it.base - 0.7f * 70f) < 1e-4f })
        assertEquals(0.46f * 70f * 0.4f, b.barras[0].altura, 1e-4f)
        assertEquals(0.46f * 70f * 0.7f, b.barras[1].altura, 1e-4f)
        assertEquals(0.46f * 70f, b.barras[2].altura, 1e-4f)
        assertEquals(0.32f * larg, b.raioDosCantos, 1e-4f)
        // linha do um pedal: da esquerda da 1ª à direita da 3ª, 0,1·H abaixo da base
        assertEquals(b.barras[0].esq, b.linhaUmPedal[0], 0f)
        assertEquals(b.barras[2].dir, b.linhaUmPedal[2], 0f)
        assertEquals(0.7f * 70f + 0.1f * 70f, b.linhaUmPedal[1], 1e-4f)
        assertEquals(1.82f, b.espessuraDaLinha, 1e-4f)
        // centradas
        assertEquals(35f, (b.barras[0].esq + b.barras[2].dir) / 2f, 1e-4f)
    }

    // --- pontinhos ---

    @Test fun `pontinhos de passo`() {
        val p = g.pontinhos(70f, 70f, 1f)
        assertEquals(1.54f, p.raio, 1e-4f)
        assertEquals(1.35f * 1.54f, p.raioDestacado, 1e-4f)
        assertEquals(35f, p.cx[1], 0f)
        assertEquals(3.1f * 1.54f, p.cx[1] - p.cx[0], 1e-4f)
        assertEquals(63f, p.cy, 1e-4f)
        // raio mínimo
        assertEquals(1.5f, g.pontinhos(10f, 10f, 1f).raio, 1e-4f)
    }
}
