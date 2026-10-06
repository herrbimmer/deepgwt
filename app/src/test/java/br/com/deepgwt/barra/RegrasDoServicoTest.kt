// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Vetores e sequências da especificação do serviço. */
class RegrasDoServicoTest {

    private val r = RegrasDoServico
    private val agenda = AgendaDeTeste()

    @Before
    fun antes() {
        ThreadPrincipal.agenda = agenda
    }

    @After
    fun depois() {
        val s = EstadoDaBarra
        s.esconderSozinha = false
        s.visivel = true
        s.fecharTudo()
        s.painelExpandido = false
        ThreadPrincipal.restaurar()
    }

    @Test
    fun `retangulo do volume com densidade do emulador`() {
        assertArrayEquals(intArrayOf(480, 66, 1568, 243), GeometriaDoVolumeNoTopo.retangulo(1920, 128, 1.43125f))
    }

    @Test
    fun `regioes tocaveis caso 9`() {
        // Visível: a janela inteira (o piso de 80 dp só importa em barra baixa).
        assertEquals(Retangulo(128, 0, 1920, 60).copy(topo = 60 - 80), r.regiaoDaBarra(true, false, 1920, 60, 1f, 128, false))
        assertEquals(Retangulo(128, 0, 1920, 140), r.regiaoDaBarra(true, false, 1920, 140, 1f, 128, false))
        // Escondida: o topo (40 dp), que é o que sobra na tela.
        assertEquals(Retangulo(128, 0, 1920, 40), r.regiaoDaBarra(false, false, 1920, 60, 1f, 128, false))
        assertEquals(Retangulo(0, 0, 1920, 80), r.regiaoDaBarra(false, false, 1920, 120, 2f, -5, false))
        // Painel expandido: nada.
        assertTrue(r.regiaoDaBarra(true, true, 1920, 60, 1f, 128, false).vazio)
        // Android Auto no display 0: buraco de 100 px.
        assertEquals(100, r.regiaoDaBarra(true, false, 1920, 60, 1f, 128, true).esquerda)
        assertEquals(Retangulo(0, 0, 1920, 720), r.regiaoDosMenus(true, 1920, 720))
        assertTrue(r.regiaoDosMenus(false, 1920, 720).vazio)
    }

    @Test
    fun `janela de menus casos 13 e 14`() {
        assertFalse(r.mostrarMenus(false, false, false, false, null, null))
        assertTrue(r.mostrarMenus(false, false, false, false, FuncaoDoAjuste.VOLUME, null))
        assertTrue(r.mostrarMenus(false, false, false, false, null, EscolhaDeModo(0f, -1, "0")))
        assertTrue(r.mostrarMenus(false, false, true, false, null, null))
        assertTrue(r.soAColuna(EscolhaDeModo(0f, -1, "0"), false))
        assertFalse(r.soAColuna(EscolhaDeModo(0f, -1, "0"), true))
        assertFalse(r.soAColuna(null, false))
    }

    @Test
    fun `recuo de um app caso 16`() {
        val medidos = mapOf("com.a" to 30, "com.b" to 90)
        assertEquals(RegrasDoServico.RecuoDoApp(60, 0), r.recuoDoApp("com.x", 60, null, medidos, null))
        assertEquals(RegrasDoServico.RecuoDoApp(30, 0), r.recuoDoApp("com.a", 60, null, medidos, null))
        assertEquals(RegrasDoServico.RecuoDoApp(60, 0), r.recuoDoApp("com.b", 60, null, medidos, null))
        assertEquals(RegrasDoServico.RecuoDoApp(0, 0), r.recuoDoApp("com.x", 60, null, medidos, 0))
        // Manual tem prioridade e passa pelo limite com o deslocamento.
        assertEquals(RegrasDoServico.RecuoDoApp(80, 20), r.recuoDoApp("com.a", 60, RecuoManual(120, 20), medidos, null))
        assertEquals(RegrasDoServico.RecuoDoApp(60, -20), r.recuoDoApp("com.a", 60, RecuoManual(120, -20), medidos, null))
    }

    @Test
    fun `receptor de posicao com extras limitados (S-D3)`() {
        val atual = RegrasDoServico.RecuoDoApp(60, 0)
        assertNull(r.recuoPeloReceptor(-1, -101, atual, 60))
        assertEquals(RegrasDoServico.RecuoDoApp(30, 0), r.recuoPeloReceptor(30, -101, atual, 60))
        assertEquals(RegrasDoServico.RecuoDoApp(60, 0), r.recuoPeloReceptor(240, -101, atual, 60))
        assertEquals(RegrasDoServico.RecuoDoApp(60, 10), r.recuoPeloReceptor(-1, 10, atual, 60))
        assertEquals(RegrasDoServico.RecuoDoApp(210, 150), r.recuoPeloReceptor(999, 999, atual, 60))
    }

    @Test
    fun `manter o cartao do carro caso 29`() {
        assertTrue(r.manterCartaoDoCarro("com.android.systemui", true, FuncaoDoAjuste.VOLUME))
        assertFalse(r.manterCartaoDoCarro("com.android.systemui", false, FuncaoDoAjuste.VOLUME))
        assertFalse(r.manterCartaoDoCarro("com.spotify.music", true, FuncaoDoAjuste.VOLUME))
        assertFalse(r.manterCartaoDoCarro("com.android.systemui", true, FuncaoDoAjuste.VENTILACAO))
        assertTrue(r.manterCartaoDoCarro("COM.ANDROID.SYSTEMUI", true, FuncaoDoAjuste.VOLUME))
    }

    @Test
    fun `foco externo caso 29`() {
        val eu = "br.com.deepgwt"
        assertFalse(r.restaurarPorFocoExterno(null, eu, true, false, false))
        assertFalse(r.restaurarPorFocoExterno("  ", eu, true, false, false))
        assertFalse(r.restaurarPorFocoExterno(eu, eu, true, false, false))
        assertTrue(r.restaurarPorFocoExterno("com.example", eu, true, false, false))
        assertFalse(r.restaurarPorFocoExterno("com.example", eu, false, false, false))
        assertFalse(r.restaurarPorFocoExterno("com.android.systemui", eu, false, true, false))
        assertTrue(r.restaurarPorFocoExterno("com.example", eu, false, true, false))
        assertFalse(r.restaurarPorFocoExterno("com.example", eu, false, true, true))
        assertTrue(r.supressaoAtiva(true, 1_000, 1_000))
        assertFalse(r.supressaoAtiva(true, 1_001, 1_000))
        assertFalse(r.supressaoAtiva(false, 0, 1_000))
    }

    @Test
    fun `foco externo restaura pelo estado e preserva o cartao do carro`() {
        val s = EstadoDaBarra
        s.visivel = true
        s.menuDeAppsAberto = true
        s.cartao = FuncaoDoAjuste.VOLUME
        s.cartaoAbertoPeloCarro = true
        Barra.aoFocoExterno("com.android.systemui", "teste")
        assertFalse(s.menuDeAppsAberto)
        assertEquals(FuncaoDoAjuste.VOLUME, s.cartao)
        Barra.aoFocoExterno("com.spotify.music", "teste")
        assertNull(s.cartao)
        // Painel expandido + supressão (controle de mídia do painel) → ignora.
        s.painelExpandido = true
        Barra.suspenderRestauracaoPorFoco()
        Barra.aoFocoExterno("com.spotify.music", "teste")
        assertTrue(s.painelExpandido)
        agenda.avancar(1_501)
        Barra.aoFocoExterno("com.spotify.music", "teste")
        assertFalse(s.painelExpandido)
    }

    @Test
    fun `reabrir apos projecao caso 30`() {
        assertTrue(r.reabrirAposProjecao(true, true, false))
        assertTrue(r.reabrirAposProjecao(true, false, true))
        assertFalse(r.reabrirAposProjecao(true, false, false))
        assertFalse(r.reabrirAposProjecao(false, true, true))
    }

    @Test
    fun `projecao na tela principal caso 37`() {
        listOf("com.ts.carplay", "com.ts.carplay.app", "com.ts.androidauto", "com.ts.androidauto.app", "com.ts.androidauto.projectionservice")
            .forEach { assertTrue(it, r.naProjecao(it, null)) }
        assertTrue(r.naProjecao("com.ts.carplay.app:remote", "com.ts.carplay.app"))
        assertTrue(r.naProjecao("com.zlink.service", "com.ts.carplay.app"))
        assertFalse(r.naProjecao("com.google.android.youtube", null))
        assertFalse(r.naProjecao("com.beantechs.launcher", null))
        assertFalse(r.naProjecao(null, null))
    }

    @Test
    fun `saida da frente da projecao caso 37`() {
        assertEquals(RegrasDoServico.AcaoDaProjecao.ESCONDER to true, r.saidaDaFrente(true, true, false, true))
        assertEquals(RegrasDoServico.AcaoDaProjecao.NADA to false, r.saidaDaFrente(true, true, false, false)) // escondida pelo usuário: não toma posse
        assertEquals(RegrasDoServico.AcaoDaProjecao.NADA to true, r.saidaDaFrente(true, true, true, true)) // puxada de volta durante a projeção
        assertEquals(RegrasDoServico.AcaoDaProjecao.MOSTRAR to false, r.saidaDaFrente(true, false, true, false))
        assertEquals(RegrasDoServico.AcaoDaProjecao.NADA to false, r.saidaDaFrente(true, false, false, false))
        assertEquals(RegrasDoServico.AcaoDaProjecao.MOSTRAR to false, r.saidaDaFrente(false, true, true, false))
        assertEquals(RegrasDoServico.AcaoDaProjecao.NADA to false, r.saidaDaFrente(false, true, false, true))
    }

    @Test
    fun `cartao do carro casos 32 e 33`() {
        val c = CartaoPeloCarro()
        c.semear("10")
        assertFalse("semente: o valor entregue ao registrar", c.avisoPedeCartao(r.VOLUME, "10"))
        assertFalse("mesmo valor", c.avisoPedeCartao(r.VOLUME, "10"))
        assertTrue("valor novo", c.avisoPedeCartao(r.VOLUME, "11"))
        assertFalse("vazio", c.avisoPedeCartao(r.VOLUME, ""))
        assertFalse("depois do vazio, sem anterior útil", c.avisoPedeCartao(r.VOLUME, "") )
        assertFalse(c.avisoPedeCartao(r.ACAO_DO_VOLUME, "0"))
        assertFalse(c.avisoPedeCartao(r.ACAO_DO_VOLUME, " "))
        assertTrue(c.avisoPedeCartao(r.ACAO_DO_VOLUME, "1"))
        val semSemente = CartaoPeloCarro()
        assertFalse(semSemente.avisoPedeCartao(r.VOLUME, "10"))
        // Guardas do caso 33.
        assertTrue(r.podeAbrirCartaoPeloCarro(true, false, null, false, false))
        assertFalse("preferência desligada", r.podeAbrirCartaoPeloCarro(false, false, null, false, false))
        assertFalse("arrastando", r.podeAbrirCartaoPeloCarro(true, true, null, false, false))
        assertFalse("cartão por toque", r.podeAbrirCartaoPeloCarro(true, false, FuncaoDoAjuste.VOLUME, false, false))
        assertTrue("cartão já do carro", r.podeAbrirCartaoPeloCarro(true, false, FuncaoDoAjuste.VOLUME, true, false))
        assertFalse("painel", r.podeAbrirCartaoPeloCarro(true, false, null, false, true))
    }

    @Test
    fun `esconder sozinha caso 36 com relogio falso`() {
        val s = EstadoDaBarra
        s.esconderSozinha = true
        s.visivel = true
        EsconderSozinha.rearmar()
        agenda.avancar(29_999)
        assertTrue(s.visivel)
        // Um toque rearma (S-D1).
        EsconderSozinha.tocou()
        agenda.avancar(29_999)
        assertTrue(s.visivel)
        agenda.avancar(1)
        assertFalse(s.visivel)
        // Com algo aberto, ao vencer não esconde.
        s.visivel = true
        s.menuDeClimaAberto = true
        EsconderSozinha.rearmar()
        agenda.avancar(30_000)
        assertTrue(s.visivel)
        // Sem a preferência, nem arma.
        s.menuDeClimaAberto = false
        s.esconderSozinha = false
        EsconderSozinha.rearmar()
        assertEquals(0, agenda.pendentes)
    }

    @Test
    fun `amostrador caso 28`() {
        val a = AmostradorDeRecursos()
        assertNull(a.cpu("cpu 100 0 100 700 100 0 0 0\ncpu0 1 2 3 4"))
        assertEquals(22, a.cpu("cpu 200 0 200 1300 200 0 0 0"))
        assertNull(a.cpu("cpu 200 0 200 1300 200 0 0 0")) // Δtotal 0
        assertNull(AmostradorDeRecursos().cpu("cpu 1 2"))
        assertEquals(61, AmostradorDeRecursos.ram("MemTotal: 6424112 kB\nMemFree: 1 kB\nMemAvailable: 2500000 kB"))
        assertNull(AmostradorDeRecursos.ram("MemTotal: 0 kB\nMemAvailable: 0 kB"))
        assertNull(AmostradorDeRecursos.ram("MemFree: 1 kB"))
        assertEquals("CPU 22%  RAM --", r.textoDoIndicador(22, null))
        assertEquals("CPU --  RAM --", r.textoDoIndicador(null, null))
        assertEquals(RegrasDoServico.Canto.BAIXO_FIM, r.canto(9))
        assertEquals(RegrasDoServico.Canto.TOPO_INICIO, r.canto(0))
        assertTrue(r.indicadorAparece(true, false))
        assertFalse(r.indicadorAparece(true, true))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
