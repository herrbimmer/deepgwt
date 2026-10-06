// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.circulo

import br.com.deepgwt.circulo.EfeitoDoCirculo.AlternouProjecao
import br.com.deepgwt.circulo.EfeitoDoCirculo.AndouNoHud
import br.com.deepgwt.circulo.EfeitoDoCirculo.CancelouHud
import br.com.deepgwt.circulo.EfeitoDoCirculo.ConfirmouHud
import br.com.deepgwt.circulo.EfeitoDoCirculo.EntrouNoHud
import br.com.deepgwt.circulo.EfeitoDoCirculo.Nenhum
import br.com.deepgwt.circulo.EfeitoDoCirculo.TrocouDePagina
import br.com.deepgwt.circulo.EfeitoDoCirculo.TrocouDeRelogio
import br.com.deepgwt.circulo.PaginaDoCirculo.CARRO
import br.com.deepgwt.circulo.PaginaDoCirculo.CENTRAL
import br.com.deepgwt.circulo.PaginaDoCirculo.HUD
import br.com.deepgwt.circulo.PaginaDoCirculo.RELOGIO
import br.com.deepgwt.circulo.TeclaDoCirculo.BAIXO
import br.com.deepgwt.circulo.TeclaDoCirculo.CIMA
import br.com.deepgwt.circulo.TeclaDoCirculo.DIREITA
import br.com.deepgwt.circulo.TeclaDoCirculo.ESQUERDA
import br.com.deepgwt.circulo.TeclaDoCirculo.OK
import br.com.deepgwt.circulo.TeclaDoCirculo.OK_LONGO
import br.com.deepgwt.circulo.TeclaDoCirculo.VOLTAR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CirculoDoPainelTest {

    private fun contexto(card: Int = 1, hud: Boolean = true, aviso: Boolean = false, ligado: Boolean = true) =
        ContextoDoCirculo(ligado = ligado, card = card, hudLigado = hud, avisoDeFabrica = aviso)

    private fun circulo(pagina: PaginaDoCirculo? = null, ctx: ContextoDoCirculo = contexto(), relogio: Int = 0) =
        CirculoDoPainel(pagina, relogio).also { it.aoMudarContexto(ctx) }

    /** Página HUD, controle já aberto em t=1000. */
    private fun controlando(): CirculoDoPainel =
        circulo(HUD).also { assertEquals(EntrouNoHud, it.aoApertar(OK_LONGO, 1_000)) }

    @Test
    fun `sem nada guardado comeca no card do carro, e a guardada volta`() {
        assertEquals(CARRO, circulo().pagina)
        assertEquals(CENTRAL, circulo(CENTRAL).pagina)
        assertEquals(HUD, circulo(HUD).pagina)
        assertEquals(HUD, PaginaDoCirculo.deNome("HUD"))
        assertEquals(null, PaginaDoCirculo.deNome("lixo"))
    }

    @Test
    fun `cima e baixo trocam de pagina com volta nas pontas`() {
        val c = circulo()
        assertEquals(TrocouDePagina(CENTRAL), c.aoApertar(BAIXO, 0))
        assertEquals(TrocouDePagina(RELOGIO), c.aoApertar(BAIXO, 0))
        assertEquals(TrocouDePagina(HUD), c.aoApertar(BAIXO, 0))
        assertEquals(TrocouDePagina(CARRO), c.aoApertar(BAIXO, 0))
        assertEquals(TrocouDePagina(HUD), c.aoApertar(CIMA, 0))
        assertEquals(HUD, c.escolhida)
    }

    @Test
    fun `sem HUD ligado a pagina HUD nao existe, mas volta quando ele volta`() {
        val c = circulo(HUD, contexto(hud = false))
        assertEquals(listOf(CARRO, CENTRAL, RELOGIO), c.paginas)
        assertEquals(CARRO, c.pagina)
        assertEquals(HUD, c.escolhida)
        c.aoMudarContexto(contexto(hud = true))
        assertEquals(HUD, c.pagina)
        // Sem o HUD, andar passa por Carro, Central e Relógio.
        val s = circulo(ctx = contexto(hud = false))
        assertEquals(TrocouDePagina(CENTRAL), s.aoApertar(BAIXO, 0))
        assertEquals(TrocouDePagina(RELOGIO), s.aoApertar(BAIXO, 0))
        assertEquals(TrocouDePagina(CARRO), s.aoApertar(BAIXO, 0))
    }

    @Test
    fun `card 0, aviso de fabrica e circulo desligado nao pegam tecla nenhuma`() {
        for (ctx in listOf(contexto(card = 0), contexto(aviso = true), contexto(ligado = false), contexto(card = 2))) {
            val c = circulo(HUD, ctx)
            for (t in TeclaDoCirculo.entries) {
                assertFalse("$ctx $t", c.ehDono(t))
                assertEquals(Nenhum, c.aoApertar(t, 0))
            }
            assertFalse(c.discoVisivel(projecaoAberta = false))
        }
        // O card 3 é como o 1.
        assertTrue(circulo(ctx = contexto(card = 3)).ehDono(CIMA))
    }

    @Test
    fun `o que e do circulo e o que segue para o carro e o hands-free`() {
        val carro = circulo(CARRO)
        assertTrue(carro.ehDono(CIMA))
        assertTrue(carro.ehDono(BAIXO))
        assertFalse("OK fora do controle segue", carro.ehDono(OK))
        assertFalse("segurar OK fora da página HUD é o hands-free", carro.ehDono(OK_LONGO))
        assertFalse("BACK dispensa o aviso do carro", carro.ehDono(VOLTAR))
        assertEquals(Nenhum, carro.aoApertar(OK_LONGO, 0))

        val hud = circulo(HUD)
        assertTrue(hud.ehDono(OK_LONGO))
        assertFalse(hud.ehDono(OK))

        // 7.7: segurar OK na Central é do círculo (projetar); no Relógio segue o hands-free.
        assertTrue(circulo(CENTRAL).ehDono(OK_LONGO))
        assertFalse(circulo(CENTRAL).ehDono(OK))
        assertFalse(circulo(RELOGIO).ehDono(OK_LONGO))
        // ←/→ nunca são consumidas: o carro troca de card com elas.
        for (p in PaginaDoCirculo.entries) {
            assertFalse(circulo(p).ehDono(ESQUERDA))
            assertFalse(circulo(p).ehDono(DIREITA))
        }

        val c = controlando()
        assertTrue(c.ehDono(OK))
        assertFalse("BACK nunca é consumido", c.ehDono(VOLTAR))
    }

    @Test
    fun `controlando o HUD, cima e baixo escolhem e OK confirma`() {
        val c = controlando()
        assertEquals(AndouNoHud(1), c.aoApertar(BAIXO, 1_200))
        assertEquals(AndouNoHud(-1), c.aoApertar(CIMA, 1_400))
        assertEquals(AndouNoHud(1), c.aoApertar(BAIXO, 1_600))
        assertEquals("a página do círculo não muda durante o controle", HUD, c.pagina)
        assertEquals(ConfirmouHud, c.aoApertar(OK, 1_800))
        assertFalse(c.controlandoHud)
        // Volante devolvido: ↑/↓ voltam a trocar de página do círculo.
        assertEquals(TrocouDePagina(CARRO), c.aoApertar(BAIXO, 2_000))
    }

    @Test
    fun `o OK de soltar o proprio gesto de segurar nao confirma`() {
        val c = controlando()
        assertEquals(Nenhum, c.aoApertar(OK, 1_300))
        assertTrue(c.controlandoHud)
        // Passada a guarda, OK confirma mesmo sem ter andado.
        assertEquals(ConfirmouHud, c.aoApertar(OK, 1_000 + CirculoDoPainel.GUARDA_DO_OK_MS))

        // Depois de andar, a guarda não vale: o OK é intenção.
        val d = controlando()
        d.aoApertar(BAIXO, 1_100)
        assertEquals(ConfirmouHud, d.aoApertar(OK, 1_200))

        // Segurar de novo, passada a guarda, também confirma.
        val e = controlando()
        assertEquals(Nenhum, e.aoApertar(OK_LONGO, 1_500))
        assertEquals(ConfirmouHud, e.aoApertar(OK_LONGO, 5_000))
    }

    @Test
    fun `BACK, card, aviso e tempo cancelam sem confirmar`() {
        assertEquals(CancelouHud, controlando().aoApertar(VOLTAR, 1_100))

        val card = controlando()
        assertEquals(CancelouHud, card.aoMudarContexto(contexto(card = 3)))
        assertFalse(card.controlandoHud)

        assertEquals(CancelouHud, controlando().aoMudarContexto(contexto(aviso = true)))
        assertEquals(CancelouHud, controlando().aoMudarContexto(contexto(hud = false)))
        assertEquals(CancelouHud, controlando().aoMudarContexto(contexto(card = 0)))

        // Mesmo contexto de novo (uma preferência qualquer mudou): nada muda.
        val igual = controlando()
        assertEquals(Nenhum, igual.aoMudarContexto(contexto()))
        assertTrue(igual.controlandoHud)

        val tempo = controlando()
        tempo.aoApertar(BAIXO, 3_000)
        assertEquals(Nenhum, tempo.expirar(3_000 + CirculoDoPainel.TEMPO_DO_CONTROLE_MS - 1))
        assertEquals(CancelouHud, tempo.expirar(3_000 + CirculoDoPainel.TEMPO_DO_CONTROLE_MS))
        assertEquals(Nenhum, tempo.expirar(99_999))
    }

    @Test
    fun `abrir uma projecao leva a pagina Central`() {
        val c = circulo(CARRO)
        assertEquals(TrocouDePagina(CENTRAL), c.irPara(CENTRAL))
        assertEquals(Nenhum, c.irPara(CENTRAL))
        val d = controlando()
        assertEquals(CancelouHud, d.irPara(CENTRAL))
        assertEquals(CENTRAL, d.pagina)
    }

    @Test
    fun `o que aparece em cada pagina`() {
        // Carro: nada nosso; a projeção fica escondida (rodando).
        val carro = circulo(CARRO)
        assertFalse(carro.discoVisivel(projecaoAberta = true))
        assertFalse(carro.projecaoVisivel)
        // Central: a projeção; sem ela, o disco com o aviso de como projetar.
        val central = circulo(CENTRAL)
        assertTrue(central.projecaoVisivel)
        assertFalse(central.discoVisivel(projecaoAberta = true))
        assertTrue(central.discoVisivel(projecaoAberta = false))
        // HUD: o disco com o HUD, e a projeção escondida.
        val hud = circulo(HUD)
        assertTrue(hud.discoVisivel(projecaoAberta = true))
        assertFalse(hud.projecaoVisivel)
        // Card 0: a projeção sai de cima do card do carro.
        assertFalse(circulo(CENTRAL, contexto(card = 0)).projecaoVisivel)
        // Aviso: quem esconde a projeção é a própria janela dela; aqui ela continua "permitida".
        assertTrue(circulo(CENTRAL, contexto(aviso = true)).projecaoVisivel)
    }

    @Test
    fun `sem o pop-up o circulo volta a pegar o volante com o aviso de pe`() {
        // O controlador recebe avisoDeFabrica=false passado o pop-up: as páginas voltam a andar.
        val c = circulo(CARRO, contexto(aviso = true))
        assertFalse(c.ehDono(BAIXO))
        assertEquals(Nenhum, c.aoApertar(BAIXO, 1_000))
        c.aoMudarContexto(contexto(aviso = false))
        assertTrue(c.ehDono(BAIXO))
        assertEquals(TrocouDePagina(CENTRAL), c.aoApertar(BAIXO, 2_000))
    }

    @Test
    fun `andar da volta nas duas pontas e cai na primeira quando a atual sumiu`() {
        val l = listOf("a", "b", "c")
        assertEquals("b", CirculoDoPainel.andar(l, "a", 1))
        assertEquals("a", CirculoDoPainel.andar(l, "c", 1))
        assertEquals("c", CirculoDoPainel.andar(l, "a", -1))
        assertEquals("b", CirculoDoPainel.andar(l, "x", 1))
    }

    // ── Alpha 7.7: segurar OK na Central e a página Relógio ─────────────────────────────────

    @Test
    fun `segurar OK na Central alterna a projecao, e a repeticao do mesmo gesto e ignorada`() {
        val c = circulo(CENTRAL)
        assertEquals(AlternouProjecao, c.aoApertar(OK_LONGO, 10_000))
        // O inputservice repete o keycode de segurar: dentro da guarda, nada.
        assertEquals(Nenhum, c.aoApertar(OK_LONGO, 10_900))
        assertEquals(Nenhum, c.aoApertar(OK_LONGO, 10_000 + CirculoDoPainel.GUARDA_DA_PROJECAO_MS - 1))
        // Passada a guarda, segurar de novo alterna de novo (encerra).
        assertEquals(AlternouProjecao, c.aoApertar(OK_LONGO, 10_000 + CirculoDoPainel.GUARDA_DA_PROJECAO_MS))
        // OK comum não projeta; e nas outras páginas segurar OK não é projeção.
        assertEquals(Nenhum, circulo(CENTRAL).aoApertar(OK, 0))
        assertEquals(Nenhum, circulo(CARRO).aoApertar(OK_LONGO, 0))
        assertEquals(Nenhum, circulo(RELOGIO).aoApertar(OK_LONGO, 0))
        // No card 0 a Central não aparece: segurar OK volta a ser o hands-free.
        val card0 = circulo(CENTRAL, contexto(card = 0))
        assertFalse(card0.ehDono(OK_LONGO))
        assertEquals(Nenhum, card0.aoApertar(OK_LONGO, 0))
    }

    @Test
    fun `na pagina Relogio esquerda e direita trocam o relogio, com volta, sem consumir a tecla`() {
        val n = RelogiosDoCirculo.QUANTOS
        val c = circulo(RELOGIO)
        assertTrue(c.observa(DIREITA))
        assertTrue(c.observa(ESQUERDA))
        assertEquals(TrocouDeRelogio(1), c.aoApertar(DIREITA, 0))
        assertEquals(TrocouDeRelogio(2), c.aoApertar(DIREITA, 0))
        assertEquals(TrocouDeRelogio(1), c.aoApertar(ESQUERDA, 0))
        assertEquals(TrocouDeRelogio(0), c.aoApertar(ESQUERDA, 0))
        assertEquals(TrocouDeRelogio(n - 1), c.aoApertar(ESQUERDA, 0))
        assertEquals(TrocouDeRelogio(0), c.aoApertar(DIREITA, 0))
        assertEquals("a página não muda com ←/→", RELOGIO, c.pagina)
        // Nas outras páginas ←/→ nem são ouvidas.
        for (p in listOf(CARRO, CENTRAL, HUD)) {
            val o = circulo(p)
            assertFalse(o.observa(DIREITA))
            assertEquals(Nenhum, o.aoApertar(DIREITA, 0))
        }
        // O relógio guardado volta; índice fora da lista cai dentro dela.
        assertEquals(7, circulo(RELOGIO, relogio = 7).relogio)
        assertEquals(n - 1, circulo(RELOGIO, relogio = 99).relogio)
    }

    @Test
    fun `o relogio aparece em qualquer card do carro, as outras paginas so nos cards 1 e 3`() {
        for (card in listOf(0, 1, 3)) {
            val c = circulo(RELOGIO, contexto(card = card))
            assertTrue("card $card", c.ativo)
            assertTrue("card $card", c.discoVisivel(projecaoAberta = true))
            assertTrue("card $card", c.observa(DIREITA))
            assertTrue("↑/↓ saem do relógio também no card $card", c.ehDono(CIMA))
        }
        // O carro trocou de card por baixo (←/→): o relógio continua, sem piscar nada.
        val c = circulo(RELOGIO, contexto(card = 1))
        assertEquals(TrocouDeRelogio(1), c.aoApertar(DIREITA, 0))
        assertEquals(Nenhum, c.aoMudarContexto(contexto(card = 3)))
        assertTrue(c.ativo)
        assertEquals(Nenhum, c.aoMudarContexto(contexto(card = 0)))
        assertTrue(c.ativo)
        assertEquals(TrocouDeRelogio(2), c.aoApertar(DIREITA, 0))
        // No card 0, sair do relógio devolve o círculo ao carro.
        assertEquals(TrocouDePagina(HUD), c.aoApertar(BAIXO, 0))
        assertFalse(c.ativo)
        assertFalse(c.discoVisivel(projecaoAberta = false))
        assertFalse(c.ehDono(BAIXO))
        // Aviso do carro, círculo desligado ou card desconhecido: nem o relógio.
        for (ctx in listOf(contexto(card = 0, aviso = true), contexto(ligado = false), contexto(card = 2))) {
            val r = circulo(RELOGIO, ctx)
            assertFalse("$ctx", r.ativo)
            assertFalse("$ctx", r.observa(DIREITA))
            assertEquals(Nenhum, r.aoApertar(DIREITA, 0))
        }
        // A projeção continua fora do card 0 e fora da página Relógio.
        assertFalse(circulo(RELOGIO).projecaoVisivel)
        assertFalse(circulo(CENTRAL, contexto(card = 0)).projecaoVisivel)
    }

    @Test
    fun `relogio escolhido em Telas ou no perfil troca sem passar pelo volante`() {
        val c = circulo(CARRO)
        assertEquals(TrocouDeRelogio(4), c.escolherRelogio(4))
        assertEquals(Nenhum, c.escolherRelogio(4))
        assertEquals(4, c.relogio)
        assertEquals(TrocouDeRelogio(RelogiosDoCirculo.QUANTOS - 1), c.escolherRelogio(999))
    }

    @Test
    fun `controlando o HUD, esquerda e direita nao fazem nada no circulo`() {
        val c = controlando()
        assertFalse(c.observa(DIREITA))
        assertEquals(Nenhum, c.aoApertar(DIREITA, 1_100))
        assertTrue(c.controlandoHud)
        // O card chega logo depois e cancela, como antes.
        assertEquals(CancelouHud, c.aoMudarContexto(contexto(card = 3)))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
