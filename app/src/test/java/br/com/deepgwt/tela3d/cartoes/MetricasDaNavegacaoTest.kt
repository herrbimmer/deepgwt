package br.com.deepgwt.tela3d.cartoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricasDaNavegacaoTest {
    /** Cada caractere mede 0,5 × o tamanho. */
    private val medir: (String, Float) -> Float = { t, tam -> t.length * tam * 0.5f }

    // H = 78 → valor 0,28·H = 21,84; rótulo 0,13·H = 10,14; vão 0,08·H = 6,24 → max(10; …) = 10; extra 8
    private fun calcular(r: String, t: String, e: String, largura: Float) =
        MetricasDaNavegacao.calcular(r, t, e, largura, 78f, 1f, medir)

    @Test fun `cabe tudo mostra tres itens na ordem`() {
        val l = calcular("12,3 km", "23 min", "22:24", 400f)
        assertEquals(listOf("REMAINING", "TIME", "ETA"), l.itens.map { it.rotulo })
        assertEquals(listOf("12,3 km", "23 min", "22:24"), l.itens.map { it.valor })
        assertEquals(listOf(false, false, true), l.itens.map { it.destaque })
        assertEquals(21.84f, l.tamanhoDoValor, 0.001f)
        assertEquals(10.14f, l.tamanhoDoRotulo, 0.001f)
    }

    @Test fun `posicoes com vao e espaco extra antes do ETA`() {
        val l = calcular("12,3 km", "23 min", "22:24", 400f)
        // larguras: valor 7×21,84×0,5 = 76,44; 6×… = 65,52; 5×… = 54,6 (rótulos são menores)
        assertEquals(0f, l.itens[0].x, 0.001f)
        assertEquals(76.44f + 10f, l.itens[1].x, 0.01f)
        assertEquals(76.44f + 10f + 65.52f + 10f + 8f, l.itens[2].x, 0.01f)
    }

    @Test fun `item vazio e omitido`() {
        val l = calcular("12,3 km", "", "22:24", 400f)
        assertEquals(listOf("REMAINING", "ETA"), l.itens.map { it.rotulo })
        assertEquals(76.44f + 10f + 8f, l.itens[1].x, 0.01f)
    }

    @Test fun `so o ETA nao leva espaco extra`() {
        val l = calcular("", "", "22:24", 400f)
        assertEquals(1, l.itens.size)
        assertEquals(0f, l.itens[0].x, 0f)
    }

    @Test fun `nao cabendo remove o tempo`() {
        // sem o TIME: 76,44 + 10 + 54,6 = 141,04; com ele: 76,44+10+65,52+10+54,6 = 216,56
        val l = calcular("12,3 km", "23 min", "22:24", 150f)
        assertEquals(listOf("REMAINING", "ETA"), l.itens.map { it.rotulo })
        assertEquals(21.84f, l.tamanhoDoValor, 0.001f)
    }

    @Test fun `ainda nao cabendo reduz o valor de 1 em 1 ate caber`() {
        val l = calcular("12,3 km", "23 min", "22:24", 100f)
        assertEquals(listOf("REMAINING", "ETA"), l.itens.map { it.rotulo })
        assertTrue(l.tamanhoDoValor < 21.84f)
        val soma = l.itens.sumOf { it.largura.toDouble() } + 10.0
        assertTrue("soma=$soma", soma <= 100.0 + 0.01)
        // reduziu de 1 em 1: a diferença para o tamanho original é inteira
        assertEquals(0f, ((21.84f - l.tamanhoDoValor) % 1f), 0.001f)
    }

    @Test fun `o tamanho do valor nunca passa de onze para baixo`() {
        val l = calcular("12,3 km", "23 min", "22:24", 20f)
        assertEquals(11f, l.tamanhoDoValor, 0.001f)
        // com 11 ainda não cabe: o valor é cortado com reticências à largura do item
        assertTrue(l.itens.all { it.largura <= 20f })
        assertTrue(l.itens[0].valor.endsWith("…") || l.itens[0].valor.length < 7)
    }

    @Test fun `largura sem espaco nao tem itens`() {
        assertEquals(emptyList<ItemDeMetrica>(), calcular("1 km", "2", "3", 0f).itens)
    }

    @Test fun `os minimos da spec sao em dp`() {
        // altura 40: 0,28·H = 11,2 < 15 dp → 15 × densidade
        val l = MetricasDaNavegacao.calcular("1", "", "", 400f, 40f, 2f, medir)
        assertEquals(30f, l.tamanhoDoValor, 0.001f)
        assertEquals(18f, l.tamanhoDoRotulo, 0.001f)
    }
}
