package br.com.deepgwt.tela3d.cartoes

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasVisuaisTest {
    // --- pontinhos de passo ---

    @Test fun `pontinhos destacados por progresso`() {
        assertEquals(-1, PontosDePasso.indiceDestacado(0))
        assertEquals(-1, PontosDePasso.indiceDestacado(-5))
        for (p in 1..16) assertEquals("p=$p", -1, PontosDePasso.indiceDestacado(p))
        for (p in 17..49) assertEquals("p=$p", 0, PontosDePasso.indiceDestacado(p))
        for (p in 50..83) assertEquals("p=$p", 1, PontosDePasso.indiceDestacado(p))
        for (p in 84..100) assertEquals("p=$p", 2, PontosDePasso.indiceDestacado(p))
        assertEquals(2, PontosDePasso.indiceDestacado(250))
    }

    // --- lavagem de cor ---

    @Test fun `as quatorze cores de lavagem`() {
        val esperado = mapOf(
            "eco" to 0xFF4FBF6A, "normal" to 0xFF4A7FB5, "sport" to 0xFFE0392C, "snow" to 0xFF6FB6E8,
            "sand" to 0xFFD9A650, "mud" to 0xFF9A7346, "awd" to 0xFF5C7A94, "hev" to 0xFF7C74D6,
            "evp" to 0xFF4F93DA, "ev" to 0xFF35B98F, "level1" to 0xFF6E7A93, "level2" to 0xFF4C86C4,
            "level3" to 0xFF3C63C0, "onepedal" to 0xFF7C4FD0,
        )
        assertEquals(14, esperado.size)
        for ((estado, cor) in esperado) assertEquals(estado, cor.toInt(), LavagemDosCartoes.cor(estado))
        assertEquals(esperado.keys, LavagemDosCartoes.estadosComLavagem)
    }

    @Test fun `estado desconhecido nao tem lavagem`() {
        for (e in listOf("", "unknown", "hybrid", "live", "stale", "EV", "Eco")) assertEquals(e, 0, LavagemDosCartoes.cor(e))
    }

    // --- tokens de cor ---

    @Test fun `tokens de cor escuro e claro`() {
        assertEquals(0x66FFFFFF, CoresDosCartoes.tracoApagado(false))
        assertEquals(0x55323C48, CoresDosCartoes.tracoApagado(true))
        assertEquals(0xE6FFFFFF.toInt(), CoresDosCartoes.textoForte(false))
        assertEquals(0xCC25303B.toInt(), CoresDosCartoes.textoForte(true))
        assertEquals(0xB8B6C2CE.toInt(), CoresDosCartoes.relogioSecundario(false))
        assertEquals(0x99536171.toInt(), CoresDosCartoes.relogioSecundario(true))
        // fraco = rótulo com alfa 46 (escuro) / 34 (claro): o alfa é trocado, não multiplicado
        assertEquals(0x2EFFFFFF, CoresDosCartoes.fraco(false))
        assertEquals(0x223D4550, CoresDosCartoes.fraco(true))
        assertEquals(0x1AFFFFFF, CoresDosCartoes.discoDaVentoinha(false))
        assertEquals(0x143D4550, CoresDosCartoes.discoDaVentoinha(true))
        assertEquals(0xFF2A2A2D.toInt(), CoresDosCartoes.cuboDaVentoinha(false))
        assertEquals(0xFFF2F2F7.toInt(), CoresDosCartoes.cuboDaVentoinha(true))
        assertEquals(0x38FFFFFF, CoresDosCartoes.celulaDaGrade(false))
        assertEquals(0x263D4550, CoresDosCartoes.celulaDaGrade(true))
        assertEquals(0x78FFFFFF, CoresDosCartoes.colunaComValor(false))
    }

    @Test fun `cores do cartao de midia`() {
        assertEquals(0xFF161616.toInt(), CoresDosCartoes.iconeDoBotaoCentral(false))
        assertEquals(0xFFFFFFFF.toInt(), CoresDosCartoes.iconeDoBotaoCentral(true))
        assertEquals(0xFFFFFFFF.toInt(), CoresDosCartoes.discoDoBotaoCentral(false))
        assertEquals(0xFF1C1C1E.toInt(), CoresDosCartoes.discoDoBotaoCentral(true))
        assertEquals(0x2EFFFFFF, CoresDosCartoes.botaoLateralPressionado(false))
        assertEquals(0x1F000000, CoresDosCartoes.botaoLateralPressionado(true))
    }

    // --- navegação ---

    @Test fun `quando a navegacao mostra a manobra`() {
        for (e in listOf("turn_right", "turn_left", "straight", "uturn", "roundabout", "fork", "merge")) {
            assertTrue(e, RegrasDaNavegacao.mostraManobra(e, ""))
            assertTrue(e, RegrasDaNavegacao.temVetor(e))
        }
        assertTrue(RegrasDaNavegacao.mostraManobra("destination", ""))
        assertTrue(RegrasDaNavegacao.mostraManobra("exit", ""))
        assertFalse(RegrasDaNavegacao.temVetor("destination"))
        assertTrue(RegrasDaNavegacao.mostraManobra("demo", "M0 0 L1 1"))
        assertFalse(RegrasDaNavegacao.mostraManobra("demo", ""))
        for (e in listOf("idle", "unavailable", "")) assertFalse(e, RegrasDaNavegacao.mostraManobra(e, "M0 0 L1 1"))
    }

    // --- caminho vetorial ---

    @Test fun `le comandos M L C Z`() {
        val r = CaminhoVetorial.ler("M12 3 L4 14 C1 2 3 4 5 6 Z")
        assertEquals(
            listOf(
                ComandoDeCaminho.MoverPara(12f, 3f), ComandoDeCaminho.LinhaPara(4f, 14f),
                ComandoDeCaminho.Curva(1f, 2f, 3f, 4f, 5f, 6f), ComandoDeCaminho.Fechar,
            ),
            r,
        )
    }

    @Test fun `aceita letras coladas e numeros negativos e decimais`() {
        val r = CaminhoVetorial.ler("M1.5-2L3 4.25Z")
        assertEquals(listOf(ComandoDeCaminho.MoverPara(1.5f, -2f), ComandoDeCaminho.LinhaPara(3f, 4.25f), ComandoDeCaminho.Fechar), r)
    }

    @Test fun `numeros extras depois do M viram linhas`() {
        assertEquals(
            listOf(ComandoDeCaminho.MoverPara(1f, 2f), ComandoDeCaminho.LinhaPara(3f, 4f)),
            CaminhoVetorial.ler("M 1 2 3 4"),
        )
    }

    @Test fun `texto invalido ou incompleto devolve o que deu`() {
        assertEquals(emptyList<ComandoDeCaminho>(), CaminhoVetorial.ler(""))
        assertEquals(listOf(ComandoDeCaminho.MoverPara(1f, 2f)), CaminhoVetorial.ler("M1 2 L3"))
        assertEquals(emptyList<ComandoDeCaminho>(), CaminhoVetorial.ler("1 2 3"))
    }

    // --- arrasto: balanço, selos ---

    @Test fun `balanco por posicao`() {
        assertEquals(130L, GeometriaDeArrasto.duracaoDoBalancoMs(0))
        assertEquals(150L, GeometriaDeArrasto.duracaoDoBalancoMs(1))
        assertEquals(130L, GeometriaDeArrasto.duracaoDoBalancoMs(2))
        assertArrayEquals(
            longArrayOf(0, 37, 74, 21, 58, 5, 42),
            LongArray(7) { GeometriaDeArrasto.atrasoDoBalancoMs(it) },
        )
    }

    @Test fun `caixas dos selos`() {
        assertArrayEquals(floatArrayOf(238f - 26 - 4, 4f, 238f - 4, 30f), GeometriaDosSelos.caixaDoX(238f), 0f)
        assertArrayEquals(floatArrayOf(238f - 30 - 4, 124f - 30 - 4, 238f - 4, 124f - 4), GeometriaDosSelos.caixaDosPontos(238f, 124f), 0f)
    }

    @Test fun `area do selo ampliada em dez`() {
        val x = GeometriaDosSelos.caixaDoX(238f)
        assertTrue(GeometriaDeArrasto.dentro(x[0] - 10f, x[1] + 5f, x[0], x[1], x[2], x[3], 10f))
        assertFalse(GeometriaDeArrasto.dentro(x[0] - 10.5f, x[1] + 5f, x[0], x[1], x[2], x[3], 10f))
        assertTrue(GeometriaDeArrasto.dentro(x[2] + 10f, x[3] + 10f, x[0], x[1], x[2], x[3], 10f))
    }
}
