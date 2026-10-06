package br.com.deepgwt.tela3d.midia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vetores V2 (vencedor), V5 (posição ao vivo) e as regras de escolha de sessão (B1). */
class RegrasDeFaixaTest {

    private fun c(tocando: Boolean, temFaixa: Boolean = true) = CandidataDeFaixa(tocando, temFaixa)

    // --- V2 ------------------------------------------------------------------------------------

    @Test
    fun `v2 sessao tocando vence carplay pausado`() {
        assertEquals(FonteDaFaixa.SESSAO, Arbitragem.escolher(c(true), null, c(false)))
    }

    @Test
    fun `v2 carplay tocando vence central tocando`() {
        assertEquals(FonteDaFaixa.CARPLAY, Arbitragem.escolher(null, c(true), c(true)))
    }

    @Test
    fun `v2 todas pausadas carplay vence central que vence sessao`() {
        assertEquals(FonteDaFaixa.CARPLAY, Arbitragem.escolher(c(false), c(false), c(false)))
        assertEquals(FonteDaFaixa.CENTRAL, Arbitragem.escolher(c(false), c(false), null))
        assertEquals(FonteDaFaixa.SESSAO, Arbitragem.escolher(c(false), null, null))
    }

    @Test
    fun `v2 sessao sem faixa e central pausada com faixa vence a central`() {
        assertEquals(FonteDaFaixa.CENTRAL, Arbitragem.escolher(c(true, temFaixa = false), c(false), null))
    }

    @Test
    fun `v2 nenhuma com faixa e sessao existente vale a sessao`() {
        assertEquals(FonteDaFaixa.SESSAO, Arbitragem.escolher(c(false, temFaixa = false), null, null))
        assertEquals(
            FonteDaFaixa.SESSAO,
            Arbitragem.escolher(c(false, temFaixa = false), c(true, temFaixa = false), c(true, temFaixa = false)),
        )
    }

    @Test
    fun `v2 nenhuma e sem sessao nao ha vencedor`() {
        assertNull(Arbitragem.escolher(null, null, null))
        assertNull(Arbitragem.escolher(null, c(true, temFaixa = false), null))
    }

    @Test
    fun `v2 faixa so com capa conta como tem faixa`() {
        val semTexto = FaixaLida(
            fonte = FonteDaFaixa.CENTRAL, pacote = "p", rotulo = "USB", titulo = "", artista = "", album = "",
            duracaoMs = 0, tocando = false, posicaoInformadaMs = 0, instanteDaPosicaoMs = 0,
            origemDaCapa = OrigemDaCapa(null, "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg", "", "k"),
        )
        assertTrue(semTexto.temFaixa)
        val vazia = FaixaLida(
            fonte = FonteDaFaixa.CENTRAL, pacote = "p", rotulo = "USB", titulo = "", artista = "", album = "",
            duracaoMs = 0, tocando = false, posicaoInformadaMs = 0, instanteDaPosicaoMs = 0,
            origemDaCapa = OrigemDaCapa.NENHUMA,
        )
        assertFalse(vazia.temFaixa)
        assertEquals(FonteDaFaixa.CENTRAL, Arbitragem.escolher(null, semTexto.candidata(), null))
    }

    // --- V5 ------------------------------------------------------------------------------------

    private fun sessao(
        pos: Long = 10_000, tocando: Boolean = true, carimbo: Long = 8_000, agora: Long = 10_000,
        vel: Float = 1f, duracao: Long = 0,
    ) = PosicaoAoVivo.daSessao(pos, tocando, carimbo, agora, vel, duracao)

    @Test
    fun `v5 sessao tocando extrapola pelo carimbo`() {
        assertEquals(12_000L, sessao())
        assertEquals(12_000L, sessao(vel = 0f)) // velocidade 0 vale 1
        assertEquals(13_000L, sessao(vel = 1.5f))
        assertEquals(11_000L, sessao(duracao = 11_000))
    }

    @Test
    fun `v5 sessao pausada ou sem carimbo nao extrapola`() {
        assertEquals(10_000L, sessao(tocando = false))
        assertEquals(10_000L, sessao(carimbo = 0))
    }

    @Test
    fun `v5 posicao nunca fica negativa`() {
        assertEquals(0L, sessao(pos = 100, carimbo = 9_999_999, agora = 1_000))
        assertEquals(0L, PosicaoAoVivo.informada(-5, false, 0, 0, 0))
    }

    @Test
    fun `v5 demais fontes somam o tempo desde que foi informada`() {
        assertEquals(8_000L, PosicaoAoVivo.informada(5_000, true, instanteInformadoMs = 1_000, agoraMs = 4_000, duracaoMs = 0))
        assertEquals(5_000L, PosicaoAoVivo.informada(5_000, false, 1_000, 4_000, 0))
        assertEquals(7_000L, PosicaoAoVivo.informada(5_000, true, 1_000, 4_000, 7_000))
    }

    // --- B1 ------------------------------------------------------------------------------------

    @Test
    fun `b1 a primeira tocando vence na hora`() {
        assertEquals(1, EscolhaDeSessao.escolher(listOf(2, 3, 3)))
        assertEquals(2, EscolhaDeSessao.escolher(listOf(7, 2, 3)))
    }

    @Test
    fun `b1 sem tocando vale a primeira nao ignorada`() {
        assertEquals(1, EscolhaDeSessao.escolher(listOf(7, 2, 1)))
        assertEquals(0, EscolhaDeSessao.escolher(listOf(null, 7)))
        assertEquals(0, EscolhaDeSessao.escolher(listOf(2, 2)))
    }

    @Test
    fun `b1 todas em erro valem a primeira e lista vazia nenhuma`() {
        assertEquals(0, EscolhaDeSessao.escolher(listOf(7, 7)))
        assertEquals(-1, EscolhaDeSessao.escolher(emptyList()))
    }

    @Test
    fun `b1 reavaliar quando outra sessao passa a tocar`() {
        // a escolhida pausou (índice 0) e a outra, já ativa, começou a tocar
        assertEquals(0, EscolhaDeSessao.escolher(listOf(3, 2)))
        assertEquals(1, EscolhaDeSessao.escolher(listOf(2, 3)))
    }
}
