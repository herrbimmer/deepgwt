package br.com.deepgwt.tela3d.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacasTest {
    private fun pers(icone: String?, fundo: String? = null, cor: String? = null, nome: String? = null) =
        Personalizacao(nome, icone, cor, fundo)

    @Test
    fun `E2 chave do hub sem icone usa placa escura`() {
        assertTrue(Placas.usaPlacaEscura(true, null, true))
        assertTrue(Placas.usaPlacaEscura(true, pers(null, nome = "Carro"), true))
    }

    @Test
    fun `E2 chave do hub com icone so se for gwm`() {
        assertTrue(Placas.usaPlacaEscura(true, pers("gwm"), true))
        assertFalse(Placas.usaPlacaEscura(true, pers("music"), true))
    }

    @Test
    fun `E2 outros com icone so se for gwm`() {
        assertTrue(Placas.usaPlacaEscura(false, pers("gwm"), false))
        assertFalse(Placas.usaPlacaEscura(false, pers("music"), true))
    }

    @Test
    fun `E2 outros sem icone depende de emblema ou energy`() {
        assertTrue(Placas.usaPlacaEscura(false, null, true))
        assertTrue(Placas.usaPlacaEscura(false, pers(null, nome = "Só nome"), true))
        assertFalse(Placas.usaPlacaEscura(false, null, false))
    }

    @Test
    fun `E1 icone e fundo - placa de cor solida`() {
        assertEquals(
            DecisaoDePlaca.Cor(0xFF111111.toInt()),
            Placas.decidir(false, pers("music", fundo = "#111111"), false),
        )
        // fundo inválido -> #3D4650
        assertEquals(
            DecisaoDePlaca.Cor(0xFF3D4650.toInt()),
            Placas.decidir(false, pers("music", fundo = "lixo"), false),
        )
        // mesmo sendo app GWM, a cor da personalização vence
        assertEquals(
            DecisaoDePlaca.Cor(0xFFE53935.toInt()),
            Placas.decidir(false, pers("gwm", fundo = "#E53935"), true),
        )
    }

    @Test
    fun `E1 icone sem fundo - placa por tema`() {
        assertEquals(DecisaoDePlaca.PorTema(escura = false), Placas.decidir(false, pers("music"), false))
        assertEquals(DecisaoDePlaca.PorTema(escura = true), Placas.decidir(false, pers("gwm"), false))
    }

    @Test
    fun `E1 sem icone - app GWM com so nome leva placa escura`() {
        assertEquals(DecisaoDePlaca.PorTema(escura = true), Placas.decidir(false, pers(null, nome = "GW"), true))
    }

    @Test
    fun `E1 app comum nao leva placa`() {
        assertEquals(DecisaoDePlaca.Nenhuma, Placas.decidir(false, null, false))
        assertEquals(DecisaoDePlaca.Nenhuma, Placas.decidir(false, pers(null, nome = "Só nome"), false))
    }

    @Test
    fun `E1 fundo sem icone nao conta`() {
        assertEquals(DecisaoDePlaca.Nenhuma, Placas.decidir(false, pers(null, fundo = "#111111"), false))
    }

    @Test
    fun `E1 chave do hub`() {
        assertEquals(DecisaoDePlaca.PorTema(true), Placas.decidir(true, null, true))
        assertEquals(DecisaoDePlaca.PorTema(false), Placas.decidir(true, pers("music"), true))
        assertEquals(DecisaoDePlaca.Cor(0xFF111111.toInt()), Placas.decidir(true, pers("gwm", fundo = "#111111"), true))
    }

    @Test
    fun `E3 cores por tema`() {
        assertEquals(0xF20A0D12.toInt(), Placas.corPorTema(escura = true, temaClaro = false))
        assertEquals(0xFF202733.toInt(), Placas.corPorTema(escura = true, temaClaro = true))
        assertEquals(0xF8F3F6FA.toInt(), Placas.corPorTema(escura = false, temaClaro = false))
        assertEquals(0xFFFCFDFE.toInt(), Placas.corPorTema(escura = false, temaClaro = true))
    }

    @Test
    fun `cor final do catalogo`() {
        assertNull(Placas.corDoCatalogo(DecisaoDePlaca.Nenhuma, true))
        assertEquals(0xFF202733.toInt(), Placas.corDoCatalogo(DecisaoDePlaca.PorTema(true), true))
        assertEquals(0xFF123456.toInt(), Placas.corDoCatalogo(DecisaoDePlaca.Cor(0xFF123456.toInt()), false))
    }

    @Test
    fun `placa para a faixa - sem placa vira a clara`() {
        assertEquals(PlacaDoItem.Clara, Placas.paraAFaixa(DecisaoDePlaca.Nenhuma))
        assertEquals(PlacaDoItem.Escura, Placas.paraAFaixa(DecisaoDePlaca.PorTema(true)))
        assertEquals(PlacaDoItem.Clara, Placas.paraAFaixa(DecisaoDePlaca.PorTema(false)))
        assertEquals(PlacaDoItem.Cor(5), Placas.paraAFaixa(DecisaoDePlaca.Cor(5)))
    }

    @Test
    fun `geometria da placa do catalogo`() {
        assertEquals(21.12f, GeometriaDoIconeDoCatalogo.raioDaPlaca(96), 0.001f)
        assertEquals(15, GeometriaDoIconeDoCatalogo.recuoDoIcone(96))
    }

    @Test
    fun `borda da placa do dock`() {
        assertEquals(1, MedidasDaPlacaDoDock.bordaEmPx(0.5f))
        assertEquals(1, MedidasDaPlacaDoDock.bordaEmPx(1.0f))
        assertEquals(3, MedidasDaPlacaDoDock.bordaEmPx(2.6f))
    }
}
