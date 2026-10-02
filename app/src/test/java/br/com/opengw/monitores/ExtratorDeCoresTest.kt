package br.com.opengw.monitores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Vetores V1 a V18 de `extras-monitores.md` (parte A), sem Android. */
class ExtratorDeCoresTest {

    @Before
    fun limpar() {
        ExtratorDeCores.limparCache()
    }

    private fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

    private fun cores(p: Int, s: Int, d: Int, e: Int) = CoresDoFundo(rgb(p), rgb(s), rgb(d), rgb(e))

    private fun imagem(largura: Int, altura: Int, pixel: (Int, Int) -> Int): CoresDoFundo {
        val pixels = IntArray(largura * altura) { pixel(it % largura, it / largura) }
        return ExtratorDeCores.extrairDePixels(pixels, largura, altura)
    }

    private fun solida(l: Int, a: Int, argb: Int) = imagem(l, a) { _, _ -> argb }

    private fun assertComTolerancia(esperado: CoresDoFundo, real: CoresDoFundo) {
        fun confere(nome: String, e: Int, r: Int) {
            for (deslocamento in intArrayOf(16, 8, 0)) {
                val diferenca = Math.abs(((e shr deslocamento) and 0xFF) - ((r shr deslocamento) and 0xFF))
                assertTrue("$nome: esperado %06X, veio %06X".format(e and 0xFFFFFF, r and 0xFFFFFF), diferenca <= 1)
            }
        }
        confere("principal", esperado.principal, real.principal)
        confere("secundaria", esperado.secundaria, real.secundaria)
        confere("destaque", esperado.destaque, real.destaque)
        confere("escura", esperado.escura, real.escura)
    }

    private fun assertCores(esperado: CoresDoFundo, real: CoresDoFundo) {
        fun hex(c: CoresDoFundo) = "%06X/%06X/%06X/%06X".format(
            c.principal and 0xFFFFFF, c.secundaria and 0xFFFFFF, c.destaque and 0xFFFFFF, c.escura and 0xFFFFFF,
        )
        assertEquals(hex(esperado), hex(real))
        assertEquals(esperado, real)
    }

    @Test
    fun `V1 a reserva tem as quatro cores combinadas`() {
        assertCores(cores(0x1F333A, 0x41351F, 0x66E3FF, 0x05070A), CoresDoFundo.RESERVA)
    }

    @Test
    fun `V2 sem capa ou dimensao invalida devolve a reserva`() {
        assertSame(CoresDoFundo.RESERVA, ExtratorDeCores.extrair(null, "x"))
        assertSame(CoresDoFundo.RESERVA, ExtratorDeCores.extrairDePixels(IntArray(0), 0, 4))
        assertSame(CoresDoFundo.RESERVA, ExtratorDeCores.extrairDePixels(IntArray(4), 4, 0))
        assertSame(CoresDoFundo.RESERVA, ExtratorDeCores.extrairDePixels(IntArray(3), 2, 2))
    }

    @Test
    fun `V3 vermelho solido`() {
        assertCores(cores(0xE81717, 0xE81717, 0xE81717, 0x570000), solida(8, 8, rgb(0xFF0000)))
    }

    @Test
    fun `V4 verde solido`() {
        assertCores(cores(0x17E817, 0x17E817, 0x17E817, 0x005700), solida(8, 8, rgb(0x00FF00)))
    }

    @Test
    fun `V5 azul solido`() {
        assertCores(cores(0x3366CC, 0x3366CC, 0x3366CC, 0x112345), solida(8, 8, rgb(0x3366CC)))
    }

    @Test
    fun `V6 cinza solido sai avermelhado (DA1 mantido)`() {
        assertCores(cores(0xA45C5C, 0xA45C5C, 0xA45C5C, 0x322525), solida(8, 8, rgb(0x808080)))
    }

    @Test
    fun `V7 preto e branco puros dao a reserva`() {
        assertCores(CoresDoFundo.RESERVA, solida(8, 8, rgb(0x000000)))
        assertCores(CoresDoFundo.RESERVA, solida(8, 8, rgb(0xFFFFFF)))
    }

    @Test
    fun `V8 alfa abaixo de 180 e descartado e 180 conta`() {
        assertCores(CoresDoFundo.RESERVA, solida(8, 8, (100 shl 24) or 0xFF0000))
        assertCores(CoresDoFundo.RESERVA, solida(8, 8, (179 shl 24) or 0xFF0000))
        assertCores(solida(8, 8, rgb(0xFF0000)), solida(8, 8, (180 shl 24) or 0xFF0000))
        assertCores(cores(0xE81717, 0xE81717, 0xE81717, 0x570000), solida(8, 8, (180 shl 24) or 0xFF0000))
    }

    @Test
    fun `V9 metade vermelha e metade azul`() {
        val r = imagem(8, 8) { x, _ -> rgb(if (x < 4) 0xFF0000 else 0x0000FF) }
        assertCores(cores(0xE81717, 0x790C79, 0xE81717, 0x2B002B), r)
    }

    @Test
    fun `V10 metade de cima turquesa e metade de baixo marrom escuro`() {
        val r = imagem(8, 8) { _, y -> rgb(if (y < 4) 0x20B2AA else 0x2A1A0A) }
        assertCores(cores(0x20B2AA, 0x20B2AA, 0x20B2AA, 0x0B3D3A), r)
    }

    @Test
    fun `V11 quatro quadrantes`() {
        val r = imagem(8, 8) { x, y ->
            rgb(
                when {
                    y < 4 && x < 4 -> 0xE63946
                    y < 4 -> 0xF1FAEE
                    x < 4 -> 0xA8DADC
                    else -> 0x1D3557
                },
            )
        }
        assertCores(cores(0xE63946, 0x213D63, 0xE63946, 0x0A121E), r)
    }

    @Test
    fun `V12 degrade de cinza`() {
        val r = imagem(16, 4) { x, _ -> rgb((x * 17 shl 16) or (x * 17 shl 8) or (x * 17)) }
        assertCores(cores(0xA35B5B, 0xA35B5B, 0xA35B5B, 0x322424), r)
    }

    @Test
    fun `V13 lilas solido`() {
        assertCores(cores(0xC190C1, 0xC190C1, 0xC190C1, 0x402640), solida(4, 4, rgb(0xC8A2C8)))
    }

    @Test
    fun `V14 colunas alternadas`() {
        val r = imagem(8, 8) { x, _ -> rgb(if (x % 2 == 0) 0x600808 else 0x707870) }
        assertCores(cores(0x693F3B, 0x790C0C, 0x693F3B, 0x210303), r)
    }

    @Test
    fun `V15 degrade 400 por 300 com passo 4`() {
        val r = imagem(400, 300) { x, y -> rgb(((x * 255 / 399) shl 16) or ((y * 255 / 299) shl 8) or 128) }
        assertComTolerancia(cores(0xE92181, 0xDC167F, 0xA35C81, 0x191A2C), r)
    }

    @Test
    fun `V16 degrade 96 por 96 com passo 1`() {
        val r = imagem(96, 96) { x, y -> rgb(((255 - 2 * x) shl 16) or ((2 * y) shl 8) or (x + y)) }
        assertComTolerancia(cores(0xE92820, 0xDD161B, 0xA35C5C, 0x241424), r)
    }

    @Test
    fun `V17 degrade 144 por 144 com passo 2`() {
        val r = imagem(144, 144) { x, y -> rgb((x shl 16) or (y shl 8) or 200) }
        assertComTolerancia(cores(0x1212B6, 0x1212B6, 0x8A8AC6, 0x000044), r)
    }

    // ── V18: cache ──

    private fun capaFalsa(argb: Int) = ExtratorDeCores.AcessoAPixels(ler = { _, _ -> argb })

    @Test
    fun `V18 mesma chave de cache devolve o mesmo objeto sem olhar os pixels`() {
        val vermelho = ExtratorDeCores.extrairComCache(2, 2, 777, "same") { capaFalsa(rgb(0xFF0000)) }
        assertCores(cores(0xE81717, 0xE81717, 0xE81717, 0x570000), vermelho)

        var abriu = false
        val denovo = ExtratorDeCores.extrairComCache(2, 2, 777, "same") { abriu = true; capaFalsa(rgb(0x00FF00)) }
        assertSame(vermelho, denovo)
        assertTrue("não devia abrir os pixels", !abriu)

        val outra = ExtratorDeCores.extrairComCache(2, 2, 777, "other") { capaFalsa(rgb(0x00FF00)) }
        assertCores(cores(0x17E817, 0x17E817, 0x17E817, 0x005700), outra)
    }

    @Test
    fun `V18 chave nula e chave em branco sao a mesma chave`() {
        val a = ExtratorDeCores.extrairComCache(2, 2, 5, null) { capaFalsa(rgb(0xFF0000)) }
        val b = ExtratorDeCores.extrairComCache(2, 2, 5, "  ") { capaFalsa(rgb(0x00FF00)) }
        assertSame(a, b)
    }

    @Test
    fun `a geracao, a largura e a altura fazem parte da chave`() {
        val a = ExtratorDeCores.extrairComCache(2, 2, 1, "k") { capaFalsa(rgb(0xFF0000)) }
        val b = ExtratorDeCores.extrairComCache(2, 2, 2, "k") { capaFalsa(rgb(0x00FF00)) }
        val c = ExtratorDeCores.extrairComCache(2, 3, 1, "k") { capaFalsa(rgb(0x00FF00)) }
        val d = ExtratorDeCores.extrairComCache(3, 2, 1, "k") { capaFalsa(rgb(0x00FF00)) }
        assertNotSame(a, b)
        assertNotSame(a, c)
        assertNotSame(a, d)
        assertEquals(cores(0x17E817, 0x17E817, 0x17E817, 0x005700), b)
    }

    @Test
    fun `falha ao ler pixel vira reserva e entra no cache (DA3)`() {
        var chamadas = 0
        val primeira = ExtratorDeCores.extrairComCache(4, 4, 9, "x") {
            chamadas++
            ExtratorDeCores.AcessoAPixels(ler = { _, _ -> throw IllegalStateException("reciclada") })
        }
        assertSame(CoresDoFundo.RESERVA, primeira)
        ExtratorDeCores.extrairComCache(4, 4, 9, "x") { chamadas++; capaFalsa(rgb(0xFF0000)) }
        assertEquals(1, chamadas)
    }

    @Test
    fun `falha ao abrir o acesso tambem vira reserva e a copia e liberada`() {
        assertSame(
            CoresDoFundo.RESERVA,
            ExtratorDeCores.extrairComCache(4, 4, 10, "y") { throw IllegalStateException("sem cópia") },
        )
        var liberou = false
        ExtratorDeCores.extrairComCache(4, 4, 11, "z") {
            ExtratorDeCores.AcessoAPixels(ler = { _, _ -> rgb(0xFF0000) }, liberar = { liberou = true })
        }
        assertTrue(liberou)
    }

    @Test
    fun `o cache guarda 24 entradas e descarta a menos recente`() {
        val cache = CacheLru<String>(24)
        for (i in 0 until 24) cache.guardar("k$i", "v$i")
        cache.obter("k0") // leitura conta como uso: k1 vira a mais velha
        cache.guardar("k24", "v24")
        assertEquals(24, cache.tamanho())
        assertTrue(cache.contem("k0"))
        assertTrue(!cache.contem("k1"))
        assertTrue(cache.contem("k24"))
    }

    @Test
    fun `o passo de amostragem vem do maior lado e o pixel lido e o do passo`() {
        val lidos = HashSet<Pair<Int, Int>>()
        ContaDasCores.calcular(400, 100) { x, y -> lidos += x to y; rgb(0x3366CC) }
        // 400/96 = 4,17 -> passo 4: x e y multiplos de 4.
        assertTrue(lidos.all { (x, y) -> x % 4 == 0 && y % 4 == 0 })
        assertEquals(100 * 25, lidos.size)
        lidos.clear()
        ContaDasCores.calcular(144, 10) { x, y -> lidos += x to y; rgb(0x3366CC) }
        // 144/96 = 1,5 -> passo 2 (empate para cima).
        assertTrue(lidos.all { (x, y) -> x % 2 == 0 && y % 2 == 0 })
    }
}
