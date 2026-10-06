package br.com.deepgwt.tela3d.cartoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometriaDoFluxoTest {
    private fun e(estado: String, v: VarianteDoFluxo, dir: String, sabe: Boolean = true, soc: Double = 64.0) =
        GeometriaDoFluxo.estado(estado, v, dir, sabe, soc)

    private fun pontos(r: RotaDoFluxo) = r.pontos.map { it.x.toInt() to it.y.toInt() }

    // --- variantes ---

    @Test fun `origem do recorte no atlas por variante`() {
        assertEquals(20, VarianteDoFluxo.PHEV19.atlasX)
        assertEquals(398, VarianteDoFluxo.PHEV34.atlasX)
        assertEquals(774, VarianteDoFluxo.HEV2.atlasX)
        assertEquals(158, VarianteDoFluxo.ORIGEM_Y_NO_ATLAS)
        assertEquals(VarianteDoFluxo.PHEV19, VarianteDoFluxo.deId("desconhecida"))
        assertEquals(VarianteDoFluxo.HEV2, VarianteDoFluxo.deId("hev2"))
    }

    @Test fun `caixa da bateria e regiao das celulas`() {
        assertEquals(CaixaF(121f, 375f, 115f, 130f), VarianteDoFluxo.PHEV19.bateria)
        assertEquals(CaixaF(118f, 371f, 115f, 134f), VarianteDoFluxo.PHEV34.bateria)
        assertEquals(CaixaF(127f, 538f, 91f, 90f), VarianteDoFluxo.HEV2.bateria)
        assertEquals(RegiaoDeCelulas(126f, 409f, 105f, 45f), VarianteDoFluxo.PHEV19.celulas)
        assertEquals(RegiaoDeCelulas(123f, 409f, 105f, 49f), VarianteDoFluxo.PHEV34.celulas)
        assertEquals(RegiaoDeCelulas(132f, 567f, 81f, 22f), VarianteDoFluxo.HEV2.celulas)
        assertEquals(23f, VarianteDoFluxo.PHEV34.tamanhoDoTextoDeCarga, 0f)
        assertEquals(19f, VarianteDoFluxo.HEV2.tamanhoDoTextoDeCarga, 0f)
    }

    // --- direções ---

    @Test fun `le as direcoes e limita os valores`() {
        assertEquals(Triple(1, 0, 1), GeometriaDoFluxo.lerDirecoes("1,0,1"))
        assertEquals(Triple(-1, -1, 0), GeometriaDoFluxo.lerDirecoes("-1,-1,0"))
        assertEquals(Triple(0, 0, 0), GeometriaDoFluxo.lerDirecoes(""))
        assertEquals(Triple(0, 0, 0), GeometriaDoFluxo.lerDirecoes("1,0"))
        assertEquals(Triple(0, 0, 0), GeometriaDoFluxo.lerDirecoes("a,b,c"))
        assertEquals(Triple(1, -1, 1), GeometriaDoFluxo.lerDirecoes("5,-7,9"))
    }

    @Test fun `estado stale ou unavailable zera as direcoes`() {
        for (s in listOf("stale", "unavailable")) {
            val r = e(s, VarianteDoFluxo.PHEV34, "1,1,1")
            assertEquals(0, r.frente)
            assertEquals(0, r.traseira)
            assertFalse(r.motor)
            assertTrue(GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV34, r).isEmpty())
        }
    }

    @Test fun `a traseira so existe no phev34`() {
        assertEquals(1, e("hybrid", VarianteDoFluxo.PHEV34, "1,1,0").traseira)
        assertEquals(0, e("hybrid", VarianteDoFluxo.PHEV19, "1,1,0").traseira)
        assertEquals(0, e("hybrid", VarianteDoFluxo.HEV2, "1,1,0").traseira)
    }

    // --- regras da bateria ---

    @Test fun `tracao eletrica descarrega e pulsa em onda`() {
        val r = e("ev", VarianteDoFluxo.PHEV19, "1,0,0")
        assertTrue(r.tracionando)
        assertFalse(r.regenerando)
        assertTrue(r.descarregando)
        assertFalse(r.recebendo)
        assertEquals(GeometriaDoFluxo.CIANO, r.corDaPilha)
        assertTrue(r.pulsa)
    }

    @Test fun `hibrido tracionando tambem descarrega mas combustao nao`() {
        assertTrue(e("hybrid", VarianteDoFluxo.PHEV34, "1,1,1").descarregando)
        assertFalse(e("ice", VarianteDoFluxo.HEV2, "1,0,1").descarregando)
        assertFalse(e("live", VarianteDoFluxo.PHEV19, "1,0,0").descarregando)
        assertEquals(GeometriaDoFluxo.VERDE_DA_PILHA, e("ice", VarianteDoFluxo.HEV2, "1,0,1").corDaPilha)
    }

    @Test fun `regenerando sem tracionar recebe e pulsa`() {
        val r = e("regen", VarianteDoFluxo.PHEV34, "-1,-1,0")
        assertTrue(r.regenerando)
        assertFalse(r.tracionando)
        assertTrue(r.recebendo)
        assertFalse(r.descarregando)
        assertTrue(r.pulsa)
    }

    @Test fun `regenerar e tracionar ao mesmo tempo nao recebe nem descarrega`() {
        val r = e("hybrid", VarianteDoFluxo.PHEV34, "1,-1,0")
        assertTrue(r.regenerando)
        assertTrue(r.tracionando)
        assertFalse(r.recebendo)
        assertFalse(r.descarregando)
    }

    @Test fun `carga recebe no phev mas nao no hev2`() {
        assertTrue(e("charge", VarianteDoFluxo.PHEV19, "0,0,0").recebendo)
        assertTrue(e("charge", VarianteDoFluxo.PHEV34, "0,0,0").recebendo)
        assertFalse(e("charge", VarianteDoFluxo.HEV2, "0,0,0").recebendo)
    }

    @Test fun `com a bateria cheia nao pulsa ao receber`() {
        assertFalse(e("charge", VarianteDoFluxo.PHEV19, "0,0,0", soc = 100.0).pulsa)
        assertTrue(e("charge", VarianteDoFluxo.PHEV19, "0,0,0", soc = 99.0).pulsa)
    }

    @Test fun `sem carga conhecida nao ha celula alvo`() {
        val r = e("charge", VarianteDoFluxo.PHEV19, "0,0,0", sabe = false)
        assertEquals(0f, r.celulasAcesas, 0f)
        assertEquals(-1, r.celulaAlvo)
        assertFalse(r.pulsa)
        assertEquals("—", GeometriaDoFluxo.textoDaCarga(r))
    }

    @Test fun `celulas acesas e celula alvo`() {
        val r = e("charge", VarianteDoFluxo.PHEV19, "0,0,0", soc = 64.0)
        assertEquals(6.4f, r.celulasAcesas, 1e-5f)
        assertEquals(6, r.celulaAlvo)
        assertEquals("64%", GeometriaDoFluxo.textoDaCarga(r))
        assertEquals(3, e("charge", VarianteDoFluxo.PHEV19, "0,0,0", soc = 40.0).celulaAlvo)
        assertEquals("55%", GeometriaDoFluxo.textoDaCarga(e("charge", VarianteDoFluxo.PHEV19, "0,0,0", soc = 54.6)))
    }

    // --- pulsação ---

    @Test fun `brilho triangular`() {
        assertEquals(0f, GeometriaDoFluxo.brilho(0f), 1e-6f)
        assertEquals(1f, GeometriaDoFluxo.brilho(0.5f), 1e-6f)
        assertEquals(0f, GeometriaDoFluxo.brilho(1f), 1e-6f)
        assertEquals(0.5f, GeometriaDoFluxo.brilho(0.25f), 1e-6f)
    }

    @Test fun `fase com periodo de 1,8 s e atraso`() {
        assertEquals(0f, GeometriaDoFluxo.fase(0.0, 0f), 1e-6f)
        assertEquals(0.5f, GeometriaDoFluxo.fase(0.9, 0f), 1e-5f)
        assertEquals(0.25f, GeometriaDoFluxo.fase(1.8 + 0.45, 0f), 1e-5f)
        // atraso 0,14 s: no instante 0,14 a fase é 0
        assertEquals(0.5f, GeometriaDoFluxo.fase(0.14 + 0.9, 0.14f), 1e-5f)
        // antes do atraso a fase dá a volta
        assertEquals((1.8f - 0.14f) / 1.8f, GeometriaDoFluxo.fase(0.0, 0.14f), 1e-5f)
    }

    @Test fun `ao receber so a celula alvo pulsa`() {
        val r = e("charge", VarianteDoFluxo.PHEV19, "0,0,0", soc = 64.0) // alvo = 6
        val t = 0.9 // brilho 1
        assertEquals(0.4f, GeometriaDoFluxo.opacidadeDaCelula(r, 6, t, true), 1e-4f)
        assertEquals(0.4f, GeometriaDoFluxo.opacidadeDaCelula(r, 6, 0.0, true) + 0.4f, 1e-4f) // brilho 0: some
        assertEquals(1f, GeometriaDoFluxo.opacidadeDaCelula(r, 3, t, true), 0f)
        assertEquals(1f, GeometriaDoFluxo.opacidadeDaCelula(r, 3, 0.0, true), 0f)
        assertEquals(0f, GeometriaDoFluxo.opacidadeDaCelula(r, 7, t, true), 0f)
    }

    @Test fun `ao descarregar todas as celulas acesas pulsam com atraso`() {
        val r = e("ev", VarianteDoFluxo.PHEV19, "1,0,0", soc = 50.0) // acesas 5, alvo 4
        // célula 4 (alvo): atraso 0; célula 3: atraso 0,14
        assertEquals(1f, GeometriaDoFluxo.opacidadeDaCelula(r, 4, 0.9, true), 1e-4f)
        val brilho3 = GeometriaDoFluxo.brilho(GeometriaDoFluxo.fase(0.9, 0.14f))
        assertEquals(brilho3, GeometriaDoFluxo.opacidadeDaCelula(r, 3, 0.9, true), 1e-4f)
        assertEquals(0f, GeometriaDoFluxo.opacidadeDaCelula(r, 5, 0.9, true), 0f)
    }

    @Test fun `sem permissao para pulsar a opacidade e a base`() {
        val r = e("ev", VarianteDoFluxo.PHEV19, "1,0,0", soc = 50.0)
        assertEquals(1f, GeometriaDoFluxo.opacidadeDaCelula(r, 4, 0.0, false), 0f)
    }

    // --- rotas ---

    @Test fun `rota da frente do phev19 sai da bateria para as rodas`() {
        val r = GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV19, e("ev", VarianteDoFluxo.PHEV19, "1,0,0"))
        assertEquals(3, r.size)
        assertEquals(listOf(178 to 340, 178 to 252, 178 to 173), pontos(r[0]))
        assertEquals(listOf(177 to 173, 57 to 144), pontos(r[1]))
        assertEquals(listOf(177 to 173, 289 to 144), pontos(r[2]))
        assertTrue(r.all { it.cor == GeometriaDoFluxo.CIANO })
    }

    @Test fun `rota da frente do phev34 e do hev2`() {
        assertEquals(
            listOf(177 to 248, 177 to 221, 177 to 173),
            pontos(GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV34, e("ev", VarianteDoFluxo.PHEV34, "1,0,0"))[0]),
        )
        assertEquals(
            listOf(172 to 534, 111 to 514, 111 to 246, 177 to 216, 177 to 173),
            pontos(GeometriaDoFluxo.rotas(VarianteDoFluxo.HEV2, e("ice", VarianteDoFluxo.HEV2, "1,0,1"))[0]),
        )
    }

    @Test fun `direcao negativa inverte o sentido e muda a cor`() {
        val r = GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV19, e("regen", VarianteDoFluxo.PHEV19, "-1,0,0"))
        assertEquals(listOf(178 to 173, 178 to 252, 178 to 340), pontos(r[0]))
        assertEquals(listOf(57 to 144, 177 to 173), pontos(r[1]))
        assertTrue(r.all { it.cor == GeometriaDoFluxo.VERDE_DA_ROTA })
    }

    @Test fun `rotas da traseira so no phev34`() {
        val r = GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV34, e("hybrid", VarianteDoFluxo.PHEV34, "0,1,0"))
        assertEquals(3, r.size)
        assertEquals(listOf(177 to 567, 177 to 594, 177 to 640), pontos(r[0]))
        assertEquals(listOf(177 to 640, 57 to 654), pontos(r[1]))
        assertEquals(listOf(177 to 640, 289 to 654), pontos(r[2]))
        assertTrue(GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV19, e("hybrid", VarianteDoFluxo.PHEV19, "0,1,0")).isEmpty())
        val tudo = GeometriaDoFluxo.rotas(VarianteDoFluxo.PHEV34, e("hybrid", VarianteDoFluxo.PHEV34, "1,-1,1"))
        assertEquals(6, tudo.size)
        assertEquals(GeometriaDoFluxo.CIANO, tudo[0].cor)
        assertEquals(GeometriaDoFluxo.VERDE_DA_ROTA, tudo[3].cor)
        assertEquals(listOf(177 to 640, 177 to 594, 177 to 567), pontos(tudo[3]))
    }

    // --- setas ---

    @Test fun `seta no meio do segmento com bracos de 13 para tras e 10 para o lado`() {
        // segmento vertical de baixo para cima: de (0,100) a (0,0) → direção (0,-1)
        val s = GeometriaDoFluxo.setas(listOf(Ponto(0f, 100f), Ponto(0f, 0f)))
        assertEquals(1, s.size)
        assertEquals(Ponto(0f, 50f), s[0].ponta)
        // 13 para trás = y 63; ±10 perpendicular em x
        assertEquals(63f, s[0].bracoA.y, 1e-4f)
        assertEquals(63f, s[0].bracoB.y, 1e-4f)
        assertEquals(20f, Math.abs(s[0].bracoA.x - s[0].bracoB.x), 1e-4f)
        val comp = Math.hypot((s[0].bracoA.x - s[0].ponta.x).toDouble(), (s[0].bracoA.y - s[0].ponta.y).toDouble())
        assertEquals(GeometriaDoFluxo.COMPRIMENTO_DO_BRACO.toDouble(), comp, 1e-4)
        assertEquals(Math.sqrt(269.0), comp, 1e-4)
    }

    @Test fun `uma seta por segmento e segmento sem comprimento e ignorado`() {
        assertEquals(2, GeometriaDoFluxo.setas(listOf(Ponto(0f, 0f), Ponto(0f, 10f), Ponto(10f, 10f))).size)
        assertEquals(0, GeometriaDoFluxo.setas(listOf(Ponto(1f, 1f), Ponto(1f, 1f))).size)
        assertEquals(0, GeometriaDoFluxo.setas(listOf(Ponto(1f, 1f))).size)
    }

    @Test fun `escala do retrato girado`() {
        // 150×78: min(150/770; 78/350) = min(0,1948; 0,2229) = 0,1948 → ×0,96
        assertEquals(0.96f * (150f / 770f), GeometriaDoFluxo.escala(150f, 78f), 1e-6f)
        assertEquals(0.96f * (78f / 350f), GeometriaDoFluxo.escala(1000f, 78f), 1e-6f)
    }

    @Test fun `motor ligado`() {
        assertTrue(e("hybrid", VarianteDoFluxo.PHEV34, "1,1,1").motor)
        assertFalse(e("hybrid", VarianteDoFluxo.PHEV34, "1,1,0").motor)
    }
}
