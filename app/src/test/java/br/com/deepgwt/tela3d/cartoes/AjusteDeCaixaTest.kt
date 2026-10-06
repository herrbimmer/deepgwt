package br.com.deepgwt.tela3d.cartoes

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AjusteDeCaixaTest {
    private val pt = Locale.forLanguageTag("pt-BR")

    private fun ajusta(t: String?) = AjusteDeCaixa.ajustar(t, pt)

    @Test fun `frase em maiusculas vira frase`() {
        assertEquals("Veículo · ao vivo", ajusta("VEÍCULO · AO VIVO"))
    }

    @Test fun `a primeira palavra que nao e sigla ganha a inicial`() {
        assertEquals("EV 42 Km", ajusta("EV 42 KM"))
        assertEquals("Fuel 510 km", ajusta("FUEL 510 KM"))
        assertEquals("SOC PHEV Abcd", ajusta("SOC PHEV ABCD"))
    }

    @Test fun `palavras sem letras ficam como estao`() {
        assertEquals("Teto solar 60% · cortina 40%", ajusta("TETO SOLAR 60% · CORTINA 40%"))
    }

    @Test fun `menos de quatro letras fica igual`() {
        assertEquals("SOC 64%", ajusta("SOC 64%"))
        assertEquals("ABC", ajusta("ABC"))
    }

    @Test fun `texto com alguma minuscula fica igual`() {
        assertEquals("Abc", ajusta("Abc"))
        assertEquals("Veículo AO vivo", ajusta("Veículo AO vivo"))
    }

    @Test fun `efeito colateral conhecido ABBA vira Abba`() {
        assertEquals("Abba", ajusta("ABBA"))
        assertEquals("Indisponível", ajusta("INDISPONÍVEL"))
    }

    @Test fun `vazio e nulo`() {
        assertEquals("", ajusta(""))
        assertEquals("", ajusta(null))
    }

    @Test fun `cada linha e tratada a parte`() {
        assertEquals("Pneus\nOK", ajusta("PNEUS\nOK"))
        assertEquals("Fuel 510 km\nsoc 64%", ajusta("FUEL 510 KM\nsoc 64%"))
    }

    @Test fun `siglas conhecidas nao viram minusculas`() {
        assertEquals("Pressão FL FR RL RR", ajusta("PRESSÃO FL FR RL RR"))
        assertEquals("Carga AC DC", ajusta("CARGA AC DC"))
    }

    @Test fun `letras acentuadas contam como letras da palavra`() {
        // "ÁÉÍ" tem 3 letras: não chega a 4
        assertEquals("ÁÉÍ", ajusta("ÁÉÍ"))
        assertEquals("Ação", ajusta("AÇÃO"))
    }

    @Test fun `idioma atual e usado sem forcar ROOT`() {
        // em turco o "I" minúsculo é "ı": a regra deve usar o idioma pedido
        assertEquals("Işık", AjusteDeCaixa.ajustar("IŞIK", Locale.forLanguageTag("tr-TR")))
    }
}
