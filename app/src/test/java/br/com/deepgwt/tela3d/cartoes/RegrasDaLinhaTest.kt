package br.com.deepgwt.tela3d.cartoes

import br.com.deepgwt.tela3d.ponte.ConfigDosCartoes
import br.com.deepgwt.tela3d.ponte.LinhaDeMenu
import br.com.deepgwt.tela3d.ponte.OpcoesDoRelogio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegrasDaLinhaTest {
    private fun lista() = listOf(
        cartao("climate", titulo = "Clima", acao = "openClimate", primario = "22°"),
        cartao("range", titulo = "Autonomia", acao = "openRange", primario = "552 KM"),
    )

    // --- reconstruir × atualizar ---

    @Test fun `mesma lista e igual`() {
        assertTrue(RegrasDaLinha.mesmaEstrutura(lista(), lista()))
    }

    @Test fun `so o valor diferente continua igual`() {
        val b = listOf(lista()[0].copy(primario = "23°", estado = "demo", progresso = 40), lista()[1])
        assertTrue(RegrasDaLinha.mesmaEstrutura(lista(), b))
    }

    @Test fun `titulo diferente reconstroi`() {
        val b = listOf(lista()[0].copy(titulo = "Outro"), lista()[1])
        assertFalse(RegrasDaLinha.mesmaEstrutura(lista(), b))
    }

    @Test fun `acao diferente reconstroi`() {
        val b = listOf(lista()[0], lista()[1].copy(acao = "openPower"))
        assertFalse(RegrasDaLinha.mesmaEstrutura(lista(), b))
    }

    @Test fun `ordem diferente reconstroi`() {
        assertFalse(RegrasDaLinha.mesmaEstrutura(lista(), lista().reversed()))
    }

    @Test fun `quantidade diferente reconstroi`() {
        assertFalse(RegrasDaLinha.mesmaEstrutura(lista(), lista().take(1)))
    }

    @Test fun `relogio com opcao diferente reconstroi`() {
        val a = listOf(cartao("clock", titulo = "R", relogio = OpcoesDoRelogio.PADRAO))
        val b = listOf(cartao("clock", titulo = "R", relogio = OpcoesDoRelogio.PADRAO.copy(marcas = "plain")))
        assertFalse(RegrasDaLinha.mesmaEstrutura(a, b))
        assertTrue(RegrasDaLinha.mesmaEstrutura(a, listOf(cartao("clock", titulo = "R", relogio = OpcoesDoRelogio.PADRAO))))
    }

    @Test fun `opcao de relogio so conta no cartao de relogio`() {
        val a = listOf(cartao("climate", relogio = OpcoesDoRelogio.PADRAO))
        val b = listOf(cartao("climate", relogio = OpcoesDoRelogio.PADRAO.copy(face = "split")))
        assertTrue(RegrasDaLinha.mesmaEstrutura(a, b))
    }

    @Test fun `sem configuracao anterior reconstroi`() {
        assertTrue(RegrasDaLinha.deveReconstruir(null, ConfigDosCartoes(lista(), 8)))
    }

    @Test fun `limite diferente reconstroi`() {
        assertTrue(RegrasDaLinha.deveReconstruir(ConfigDosCartoes(lista(), 8), ConfigDosCartoes(lista(), 7)))
    }

    @Test fun `mesmo limite e mesma estrutura atualiza`() {
        assertFalse(RegrasDaLinha.deveReconstruir(ConfigDosCartoes(lista(), 8), ConfigDosCartoes(lista(), 8)))
    }

    @Test fun `so os cartoes dentro do limite contam`() {
        val tres = lista() + cartao("status", titulo = "Estado")
        val outro = lista() + cartao("status", titulo = "Mudou")
        // o terceiro está fora do limite 2: a diferença nele não força reconstrução
        assertFalse(RegrasDaLinha.deveReconstruir(ConfigDosCartoes(tres, 2), ConfigDosCartoes(outro, 2)))
        assertEquals(2, RegrasDaLinha.visiveis(ConfigDosCartoes(tres, 2)).size)
        assertEquals(0, RegrasDaLinha.visiveis(ConfigDosCartoes(tres, 0)).size)
        assertEquals(3, RegrasDaLinha.visiveis(ConfigDosCartoes(tres, 99)).size)
    }

    // --- toques ---

    @Test fun `toque com menu abre o menu e sem menu envia a acao`() {
        val menu = listOf(LinhaDeMenu("Eco", "drivingSet:mode:eco", false))
        assertEquals(AcaoDoToque.AbrirMenu(menu), PoliticaDeToque.aoTocar(cartao("driveMode", acao = "openDriving", menu = menu)))
        assertEquals(AcaoDoToque.Comando("openClimate"), PoliticaDeToque.aoTocar(cartao("climate", acao = "openClimate")))
        assertEquals(AcaoDoToque.Nada, PoliticaDeToque.aoTocar(cartao("climate")))
    }

    @Test fun `toque no grafico envia so o iconAction`() {
        assertEquals(AcaoDoToque.Comando("cycleDriveMode"), PoliticaDeToque.aoTocarNoGrafico(cartao("driveMode", acaoDoIcone = "cycleDriveMode")))
        assertEquals(AcaoDoToque.Nada, PoliticaDeToque.aoTocarNoGrafico(cartao("driveMode")))
    }

    @Test fun `toque longo respeita longAction e senao abre a edicao`() {
        assertEquals("openDrivingOnePedal", PoliticaDeToque.aoTocarLongo(cartao("regen", acaoLonga = "openDrivingOnePedal")))
        assertEquals("openRailEdit", PoliticaDeToque.aoTocarLongo(cartao("climate")))
    }

    @Test fun `toque na midia`() {
        assertEquals(AcaoDoToque.AbrirApp("x.y"), PoliticaDeToque.aoTocarNaMidia(true, "x.y", "openMediaApp"))
        assertEquals(AcaoDoToque.Comando("openMediaApp"), PoliticaDeToque.aoTocarNaMidia(false, "x.y", "openMediaApp"))
        assertEquals(AcaoDoToque.Comando("openMediaApp"), PoliticaDeToque.aoTocarNaMidia(true, "", "openMediaApp"))
        assertEquals(AcaoDoToque.Nada, PoliticaDeToque.aoTocarNaMidia(false, "x.y", ""))
    }

    @Test fun `comandos da edicao`() {
        assertEquals("railMoveCard:climate:2", PoliticaDeToque.comandoDeMover("climate", 2))
        assertEquals("railRemoveCard:climate", PoliticaDeToque.comandoDeRemover("climate"))
    }
}
