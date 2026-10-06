// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import br.com.deepgwt.telas.ProjectionGeometry.Caixa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A aritmética da projeção.
 *
 * Errar aqui não quebra nada: só fica torto na tela do carro, que é onde não dá para depurar. Daí
 * estes testes serem específicos ao ponto de conferir pixel — o recorte é uma conta com quatro
 * variáveis acopladas (tamanho da origem, tamanho da caixa, posição e tamanho do recorte) e
 * qualquer uma trocada de lugar continua "funcionando".
 */
class ProjectionGeometryTest {

    // A central e o painel têm o mesmo tamanho neste carro, mas as contas não podem depender disso.
    private val TELA_W = 1920
    private val TELA_H = 720

    @Test
    fun `o recorte leva a regiao escolhida para dentro da caixa`() {
        val recorte = Caixa(x = 400, y = 100, largura = 800, altura = 400)
        val r = ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 600, 300, recorte)!!

        // Conferência direta: aplica a transformação nos quatro cantos do recorte e vê onde caem.
        // O mapeamento total é: padrão (origem -> caixa) e depois escala/deslocamento.
        fun mapear(sx: Int, sy: Int): Pair<Float, Float> {
            val ux = sx * 600f / TELA_W
            val uy = sy * 300f / TELA_H
            return (ux * r.escalaX + r.deslocaX) to (uy * r.escalaY + r.deslocaY)
        }

        val (cantoX, cantoY) = mapear(recorte.x, recorte.y)
        val (fimX, fimY) = mapear(recorte.direita, recorte.base)
        // 800x400 numa caixa 600x300: cabe exatamente (mesma proporção), sem margem.
        assertEquals(0f, cantoX, 0.01f)
        assertEquals(0f, cantoY, 0.01f)
        assertEquals(600f, fimX, 0.01f)
        assertEquals(300f, fimY, 0.01f)
    }

    @Test
    fun `a proporcao e preservada e o que sobra vira margem`() {
        // Recorte quadrado numa caixa larga: tem de sobrar margem nas LATERAIS, não esticar.
        val recorte = Caixa(x = 0, y = 0, largura = 400, altura = 400)
        val r = ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 600, 300, recorte)!!
        assertEquals(300f, r.alturaDesenhada, 0.01f)
        assertEquals(300f, r.larguraDesenhada, 0.01f)

        fun mapear(sx: Int, sy: Int): Pair<Float, Float> {
            val ux = sx * 600f / TELA_W
            val uy = sy * 300f / TELA_H
            return (ux * r.escalaX + r.deslocaX) to (uy * r.escalaY + r.deslocaY)
        }
        val (x0, y0) = mapear(0, 0)
        val (x1, y1) = mapear(400, 400)
        assertEquals(150f, x0, 0.01f) // (600-300)/2
        assertEquals(0f, y0, 0.01f)
        assertEquals(450f, x1, 0.01f)
        assertEquals(300f, y1, 0.01f)
    }

    @Test
    fun `entradas degeneradas nao produzem matriz`() {
        val ok = Caixa(0, 0, 100, 100)
        assertNull(ProjectionGeometry.recorteParaCaixa(0, TELA_H, 100, 100, ok))
        assertNull(ProjectionGeometry.recorteParaCaixa(TELA_W, 0, 100, 100, ok))
        assertNull(ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 0, 100, ok))
        assertNull(ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 100, 0, ok))
        assertNull(ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 100, 100, Caixa(0, 0, 0, 10)))
    }

    @Test
    fun `acha o vao entre dois mostradores`() {
        // Dois discos de 560 px nas pontas, como num painel de dois gauges.
        val ocupados = listOf(
            Caixa(60, 80, 560, 560),
            Caixa(1300, 80, 560, 560)
        )
        val caixa = ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, ocupados)
        assertNotNull(caixa)
        caixa!!
        assertTrue("x=${caixa.x}", caixa.x >= 620)
        assertTrue("direita=${caixa.direita}", caixa.direita <= 1300)
        // O vão inteiro está livre de cima a baixo, então a caixa ocupa a altura toda.
        assertEquals(0, caixa.y)
        assertEquals(TELA_H, caixa.base)
    }

    @Test
    fun `um relogio no centro nao impede a projecao, so encurta a caixa`() {
        val ocupados = listOf(
            Caixa(60, 80, 560, 560),
            Caixa(1300, 80, 560, 560),
            Caixa(880, 40, 160, 60) // relógio no alto, no meio
        )
        val caixa = ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, ocupados)!!
        // O relógio está acima do meio, então ele empurra o topo da caixa para baixo dele — e não
        // pode ficar por cima da imagem.
        assertTrue("topo=${caixa.y}", caixa.y >= 100)
        assertTrue("altura=${caixa.altura}", caixa.altura >= ProjectionGeometry.ALTURA_MINIMA)
    }

    @Test
    fun `um elemento cortando o meio parte o vao em dois`() {
        val ocupados = listOf(
            Caixa(60, 80, 560, 560),
            Caixa(1300, 80, 560, 560),
            // Faixa vertical bem no meio, atravessando a linha central.
            Caixa(940, 200, 40, 320)
        )
        val caixa = ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, ocupados)!!
        // A caixa tem de ficar inteiramente de um lado da faixa, nunca por cima dela.
        val naEsquerda = caixa.direita <= 940
        val naDireita = caixa.x >= 980
        assertTrue("caixa=$caixa atravessa a faixa do meio", naEsquerda || naDireita)
    }

    @Test
    fun `sem espaco util devolve nulo em vez de um filete`() {
        // Painel cheio: só sobra uma fresta de 40 px.
        val ocupados = listOf(
            Caixa(0, 0, 940, TELA_H),
            Caixa(980, 0, 940, TELA_H)
        )
        assertNull(ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, ocupados))
    }

    @Test
    fun `a faixa vazia do rodape nao e escolhida`() {
        // Armadilha clássica: a maior área livre de um painel de mostradores costuma ser a tira
        // embaixo de tudo. Larga, rasa e no lugar errado — a amarra da linha central existe
        // exatamente para descartá-la.
        val ocupados = listOf(
            Caixa(0, 0, TELA_W, 560) // tudo ocupado até 560, sobra 160 px de rodapé
        )
        assertNull(ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, ocupados))
    }

    @Test
    fun `empate de area fica com a caixa mais centralizada`() {
        // Painel simétrico: dois vãos idênticos, um de cada lado de um bloco central.
        val ocupados = listOf(
            Caixa(0, 0, 300, TELA_H),
            Caixa(700, 0, 520, TELA_H),
            Caixa(1620, 0, 300, TELA_H)
        )
        val caixa = ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, ocupados)!!
        val esquerdo = Caixa(300, 0, 400, TELA_H)
        val direito = Caixa(1220, 0, 400, TELA_H)
        assertEquals(esquerdo.area, direito.area)
        // O da direita encosta mais no centro da tela (1620-960=660 contra 960-300=660... empate de
        // borda), então o que importa aqui é só que a escolha seja determinística e uma das duas.
        assertTrue("caixa=$caixa", caixa.x == esquerdo.x || caixa.x == direito.x)
    }

    @Test
    fun `painel vazio devolve a tela inteira`() {
        val caixa = ProjectionGeometry.maiorCaixaLivreNoCentro(TELA_W, TELA_H, emptyList())!!
        assertEquals(0, caixa.x)
        assertEquals(0, caixa.y)
        assertEquals(TELA_W, caixa.largura)
        assertEquals(TELA_H, caixa.altura)
    }

    @Test
    fun `a selecao do dedo e ordenada e cortada na tela`() {
        // Arrastado da direita para a esquerda e de baixo para cima, saindo da tela nos dois lados.
        val c = ProjectionGeometry.selecaoValida(
            x1 = 2200, y1 = 600, x2 = -40, y2 = 100,
            larguraTela = TELA_W, alturaTela = TELA_H
        )!!
        assertEquals(0, c.x)
        assertEquals(100, c.y)
        assertEquals(TELA_W, c.largura)
        assertEquals(500, c.altura)
    }

    @Test
    fun `um toque solto nao vira selecao`() {
        assertNull(ProjectionGeometry.selecaoValida(500, 300, 503, 302, TELA_W, TELA_H))
        // E uma faixa fina numa direção só também não: projetar 4 px de largura não informa nada.
        assertNull(ProjectionGeometry.selecaoValida(500, 100, 520, 600, TELA_W, TELA_H))
    }

    // ── Preencher a caixa em vez de caber nela ────────────────────────────────────────────────

    @Test
    fun `preencher nao deixa margem na caixa`() {
        // 800x400 (2:1) numa caixa 600x600 (1:1). Cabendo, sobraria metade da altura vazia.
        val recorte = Caixa(x = 400, y = 100, largura = 800, altura = 400)
        val cabendo = ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 600, 600, recorte)!!
        val preenchendo =
            ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 600, 600, recorte, preencher = true)!!

        // Cabendo: a imagem tem 600 de largura e só 300 de altura -- 300 px de tarja preta.
        assertEquals(600f, cabendo.larguraDesenhada, 0.5f)
        assertEquals(300f, cabendo.alturaDesenhada, 0.5f)

        // Preenchendo: a altura cobre a caixa inteira e é a LARGURA que transborda e é aparada.
        assertEquals(600f, preenchendo.alturaDesenhada, 0.5f)
        assertTrue(
            "a largura desenhada tem de transbordar a caixa",
            preenchendo.larguraDesenhada >= 600f
        )
        // Proporção intacta nos dois modos: nada de esticar para preencher.
        assertEquals(
            recorte.largura.toFloat() / recorte.altura,
            preenchendo.larguraDesenhada / preenchendo.alturaDesenhada,
            0.01f
        )
    }

    @Test
    fun `preencher e caber coincidem quando a proporcao ja bate`() {
        val recorte = Caixa(x = 0, y = 0, largura = 800, altura = 400)
        val cabendo = ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 600, 300, recorte)!!
        val preenchendo =
            ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 600, 300, recorte, preencher = true)!!
        assertEquals(cabendo.escalaX, preenchendo.escalaX, 0.001f)
        assertEquals(cabendo.deslocaY, preenchendo.deslocaY, 0.001f)
    }

    // ── A seleção que já nasce pronta ─────────────────────────────────────────────────────────

    @Test
    fun `a caixa predefinida tem a proporcao do painel e fica centrada`() {
        // O vão central do NTG 5.5 / Cupertino Ultra: 704x720, mais alto que largo.
        val proporcao = 704f / 720f
        val c = ProjectionGeometry.caixaPredefinida(TELA_W, TELA_H, proporcao)!!

        assertEquals(proporcao, c.largura.toFloat() / c.altura, 0.02f)
        assertTrue("tem de caber na tela", c.direita <= TELA_W && c.base <= TELA_H)
        // Centrada nos dois eixos, com a folga de um pixel do arredondamento inteiro.
        assertTrue(kotlin.math.abs((c.x + c.largura / 2) - TELA_W / 2) <= 1)
        assertTrue(kotlin.math.abs((c.y + c.altura / 2) - TELA_H / 2) <= 1)
    }

    @Test
    fun `uma proporcao muito larga encolhe pela largura e continua cabendo`() {
        // 8:1 pela altura daria 4492 px de largura numa tela de 1920.
        val c = ProjectionGeometry.caixaPredefinida(TELA_W, TELA_H, 8f)!!
        assertTrue("não pode estourar a tela", c.largura <= TELA_W && c.altura <= TELA_H)
        assertEquals(8f, c.largura.toFloat() / c.altura, 0.15f)
    }

    @Test
    fun `proporcao invalida nao produz caixa`() {
        assertNull(ProjectionGeometry.caixaPredefinida(TELA_W, TELA_H, 0f))
        assertNull(ProjectionGeometry.caixaPredefinida(TELA_W, TELA_H, Float.NaN))
        assertNull(ProjectionGeometry.caixaPredefinida(0, 0, 1.5f))
    }

    // ── Arrastar e redimensionar a seleção ────────────────────────────────────────────────────

    @Test
    fun `arrastar prende nas bordas em vez de sair da tela`() {
        val c = Caixa(x = 100, y = 100, largura = 400, altura = 300)

        val movida = ProjectionGeometry.moverNaTela(c, 60, -40, TELA_W, TELA_H)
        assertEquals(160, movida.x)
        assertEquals(60, movida.y)

        // Empurrada para fora pelos dois cantos: encosta e para, mantendo o tamanho.
        val presaNoCanto = ProjectionGeometry.moverNaTela(c, -9999, -9999, TELA_W, TELA_H)
        assertEquals(0, presaNoCanto.x)
        assertEquals(0, presaNoCanto.y)
        assertEquals(400, presaNoCanto.largura)

        val presaNoFim = ProjectionGeometry.moverNaTela(c, 9999, 9999, TELA_W, TELA_H)
        assertEquals(TELA_W, presaNoFim.direita)
        assertEquals(TELA_H, presaNoFim.base)
    }

    @Test
    fun `redimensionar mantem a proporcao e o centro`() {
        val c = Caixa(x = 600, y = 200, largura = 400, altura = 300)
        val centroX = c.x + c.largura / 2
        val centroY = c.y + c.altura / 2

        val maior = ProjectionGeometry.redimensionarNaTela(c, 1.2f, TELA_W, TELA_H)
        assertEquals(4f / 3f, maior.largura.toFloat() / maior.altura, 0.03f)
        assertTrue(maior.largura > c.largura)
        assertTrue(kotlin.math.abs((maior.x + maior.largura / 2) - centroX) <= 1)
        assertTrue(kotlin.math.abs((maior.y + maior.altura / 2) - centroY) <= 1)

        val menor = ProjectionGeometry.redimensionarNaTela(c, 0.5f, TELA_W, TELA_H)
        assertTrue(menor.largura < c.largura)
        assertEquals(4f / 3f, menor.largura.toFloat() / menor.altura, 0.03f)
    }

    @Test
    fun `redimensionar nunca estoura a tela nem desce do minimo util`() {
        val c = Caixa(x = 600, y = 200, largura = 400, altura = 300)

        val gigante = ProjectionGeometry.redimensionarNaTela(c, 99f, TELA_W, TELA_H)
        assertTrue(gigante.largura <= TELA_W)
        assertTrue(gigante.altura <= TELA_H)
        assertTrue("a caixa não pode sair da tela", gigante.direita <= TELA_W && gigante.base <= TELA_H)

        val minusculo = ProjectionGeometry.redimensionarNaTela(c, 0.001f, TELA_W, TELA_H)
        assertTrue(
            "abaixo do mínimo útil a imagem não informa nada",
            minusculo.largura >= ProjectionGeometry.LARGURA_MINIMA
        )
    }

    @Test
    fun `um fator invalido devolve a mesma caixa`() {
        val c = Caixa(x = 600, y = 200, largura = 400, altura = 300)
        assertEquals(c, ProjectionGeometry.redimensionarNaTela(c, 0f, TELA_W, TELA_H))
        assertEquals(c, ProjectionGeometry.redimensionarNaTela(c, Float.NaN, TELA_W, TELA_H))
    }

    @Test
    fun `o circulo da direita e o do tema Default`() {
        // Centro (1630, 430), raio 228: as variáveis --mask-right-x/--mask-center-y/--mask-radius.
        assertEquals(Caixa(1402, 202, 456, 456), ProjectionGeometry.caixaDoCirculo(1920, 720))
        assertEquals(ProjectionGeometry.caixaDoCirculo(), ProjectionGeometry.caixaDoCirculo(1920, 720))
    }

    @Test
    fun `o circulo continua circulo num painel de outra proporcao`() {
        val c = ProjectionGeometry.caixaDoCirculo(1280, 720)
        assertEquals(c.largura, c.altura)
        // O raio segue o menor fator (1280/1920) e o centro segue cada eixo.
        assertEquals(152 * 2, c.largura)
        assertEquals(1087, c.x + c.largura / 2)
        assertEquals(430, c.y + c.altura / 2)
        assertTrue(c.direita <= 1280 && c.base <= 720)
    }

    @Test
    fun `uma area quadrada enche o circulo sem distorcer`() {
        val area = Caixa(x = 700, y = 160, largura = 400, altura = 400)
        val r = ProjectionGeometry.recorteParaCaixa(TELA_W, TELA_H, 456, 456, area, preencher = true)!!
        assertEquals(456f, r.larguraDesenhada, 0.01f)
        assertEquals(456f, r.alturaDesenhada, 0.01f)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
