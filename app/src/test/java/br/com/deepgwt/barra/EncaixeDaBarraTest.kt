// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncaixeDaBarraTest {

    private val cinza = 0xFF303030.toInt()
    private val conteudo = 0xFF0E0E0F.toInt()

    /** Uma região 1920 × [altura] cuja última linha fica logo acima da barra. */
    private fun regiao(altura: Int, faixa: Int, corDaFaixa: Int = cinza, desenho: (x: Int, y: Int) -> Int): IntArray {
        val largura = 1920
        return IntArray(largura * altura) { i ->
            val x = i % largura
            val y = i / largura
            if (y >= altura - faixa) corDaFaixa else desenho(x, y)
        }
    }

    private fun conteudoVariado(x: Int, y: Int): Int = if ((x / 40 + y / 7) % 3 == 0) 0xFFF5F6FF.toInt() else conteudo

    @Test
    fun `a faixa da print da 77, 60 px de 303030 sobre o app, e medida`() {
        val px = regiao(128, 60) { x, y -> conteudoVariado(x, y) }
        val f = EncaixeDaBarra.faixaAcimaDaBarra(px, 1920, 128, 128 + 16, 1920 - 16)
        assertEquals(60, f?.altura)
        assertEquals(cinza, f?.cor)
    }

    @Test
    fun `sem faixa, app ate a barra, nao mede nada`() {
        val px = regiao(128, 0) { x, y -> conteudoVariado(x, y) }
        assertNull(EncaixeDaBarra.faixaAcimaDaBarra(px, 1920, 128, 144, 1904))
    }

    @Test
    fun `regiao inteira lisa nao e faixa, e fundo liso`() {
        val px = regiao(128, 128) { _, _ -> cinza }
        assertNull(EncaixeDaBarra.faixaAcimaDaBarra(px, 1920, 128, 144, 1904))
    }

    @Test
    fun `um botao na margem quebra a lisura, e o painel lateral fica de fora`() {
        // Faixa de 60 px, mas com um ícone no meio dela: não é a faixa vazia.
        val largura = 1920
        val px = regiao(128, 60) { x, y -> conteudoVariado(x, y) }
        for (y in 100 until 120) for (x in 900 until 960) px[y * largura + x] = 0xFFFFFFFF.toInt()
        assertEquals(8, EncaixeDaBarra.faixaAcimaDaBarra(px, largura, 128, 144, 1904)?.altura)
        // O painel lateral (x < 128) é preto e não conta.
        val limpo = regiao(128, 60) { x, y -> conteudoVariado(x, y) }
        for (y in 0 until 128) for (x in 0 until 128) limpo[y * largura + x] = 0xFF000000.toInt()
        assertEquals(60, EncaixeDaBarra.faixaAcimaDaBarra(limpo, largura, 128, 144, 1904)?.altura)
    }

    @Test
    fun `faixa menor que o minimo e ignorada`() {
        val px = regiao(128, 5) { x, y -> conteudoVariado(x, y) }
        assertNull(EncaixeDaBarra.faixaAcimaDaBarra(px, 1920, 128, 144, 1904))
    }

    /** Um app que se encolhe [proprio] px além do recuo do sistema: faixa = recuo + próprio - barra. */
    private fun faixaDoAppFixo(recuo: Int, proprio: Int = 60, barra: Int = 60) = (recuo + proprio - barra).coerceAtLeast(0)

    /** Um app que repete o recuo do sistema: termina em 720 - 2 × recuo. */
    private fun faixaDoAppQueRepete(recuo: Int, barra: Int = 60) = (2 * recuo - barra).coerceAtLeast(0)

    @Test
    fun `app com recuo proprio fixo converge para zero em dois passos`() {
        var recuo = 60
        var anterior: EncaixeDaBarra.Ponto? = null
        val passos = mutableListOf<Int>()
        repeat(4) {
            val faixa = faixaDoAppFixo(recuo)
            val novo = EncaixeDaBarra.proximoRecuoPx(recuo, faixa, anterior) ?: return@repeat
            anterior = EncaixeDaBarra.Ponto(recuo, faixa)
            recuo = novo
            passos += novo
        }
        assertEquals(listOf(30, 0), passos)
        assertEquals(0, faixaDoAppFixo(recuo))
    }

    @Test
    fun `app que repete o recuo do sistema para na metade`() {
        val primeiro = EncaixeDaBarra.proximoRecuoPx(60, faixaDoAppQueRepete(60), null)
        assertEquals(30, primeiro)
        assertEquals(0, faixaDoAppQueRepete(30))
        assertNull(EncaixeDaBarra.proximoRecuoPx(30, faixaDoAppQueRepete(30), EncaixeDaBarra.Ponto(60, 60)))
    }

    @Test
    fun `faixa que nao mede o recuo nao e a assinatura, e nunca aumenta o recuo`() {
        assertNull(EncaixeDaBarra.proximoRecuoPx(60, 23, null))
        assertNull(EncaixeDaBarra.proximoRecuoPx(60, 120, null))
        assertNull(EncaixeDaBarra.proximoRecuoPx(0, 60, null))
        assertNull(EncaixeDaBarra.proximoRecuoPx(60, 3, null))
        // Reta que não desce (a faixa não acompanha o recuo): não mexe.
        assertNull(EncaixeDaBarra.proximoRecuoPx(30, 58, EncaixeDaBarra.Ponto(60, 60)))
        for (recuo in listOf(20, 40, 60, 90)) for (faixa in 0..150 step 5) {
            val novo = EncaixeDaBarra.proximoRecuoPx(recuo, faixa, EncaixeDaBarra.Ponto(recuo + 30, faixa + 20))
            if (novo != null) assert(novo in 0 until recuo) { "recuo $recuo faixa $faixa -> $novo" }
        }
    }

    @Test
    fun `overscan do sistema lido do dumpsys display do Android 9`() {
        val dump = """
            Logical Displays: size=2
              Display 0:
                mDisplayId=0
                mLayerStack=0
                mHasContent=true
                mBaseDisplayInfo=DisplayInfo{"Built-in Screen", uniqueId "local:0", app 1920 x 720, real 1920 x 720, largest app 1920 x 720, smallest app 1920 x 720, mode 1, defaultMode 1}
                mOverrideDisplayInfo=DisplayInfo{"Built-in Screen", uniqueId "local:0", app 1792 x 720, real 1920 x 720, overscan (0,0,0,120), largest app 1920 x 1920, smallest app 720 x 720, mode 1}
              Display 1:
                mOverrideDisplayInfo=DisplayInfo{"Cluster", uniqueId "local:1", app 1920 x 720, real 1920 x 720, overscan (0,0,0,7), mode 2}
        """.trimIndent()
        assertEquals(120, EncaixeDaBarra.overscanInferiorDoDisplay0(dump))
        val semOverscan = dump.replace("overscan (0,0,0,120), ", "")
        assertEquals(0, EncaixeDaBarra.overscanInferiorDoDisplay0(semOverscan))
        assertNull(EncaixeDaBarra.overscanInferiorDoDisplay0("Logical Displays: size=0"))
        assertNull(EncaixeDaBarra.overscanInferiorDoDisplay0(dump.replace(Regex("mOverrideDisplayInfo=DisplayInfo\\{[^\\n]*(?=\\n  Display 1)"), "mOverrideDisplayInfo=null")))
    }

    @Test
    fun `mapa de recuos ida e volta`() {
        val mapa = mapOf("com.beantechs.mediacenter" to 0, "com.beantechs.hvac" to 30)
        assertEquals(mapa, EncaixeDaBarra.lerMapa(EncaixeDaBarra.escreverMapa(mapa)))
        assertEquals(emptyMap<String, Int>(), EncaixeDaBarra.lerMapa(null))
        assertEquals(emptyMap<String, Int>(), EncaixeDaBarra.lerMapa("não é json"))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
