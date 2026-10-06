package br.com.deepgwt.tela3d.cartoes

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TextosDosCartoesTest {
    private val pt = Locale.forLanguageTag("pt-BR")

    // --- larguras ---

    @Test fun `larguras por tipo`() {
        assertEquals(318, MedidasDaLinha.larguraDp("power"))
        assertEquals(286, MedidasDaLinha.larguraDp("navigation"))
        assertEquals(270, MedidasDaLinha.larguraDp("status"))
        // `tires` e `roof` foram descartados: viram o cartão genérico
        assertEquals(238, MedidasDaLinha.larguraDp("roof"))
        assertEquals(238, MedidasDaLinha.larguraDp("tires"))
        assertEquals(258, MedidasDaLinha.larguraDp("range"))
        assertEquals(258, MedidasDaLinha.larguraDp("consumption"))
        assertEquals(232, MedidasDaLinha.larguraDp("clock"))
        assertEquals(392, MedidasDaLinha.larguraDp("media"))
        assertEquals(238, MedidasDaLinha.larguraDp("climate"))
        assertEquals(238, MedidasDaLinha.larguraDp("qualquer-outro"))
    }

    // --- valor grande e detalhe ---

    @Test fun `valor grande usa primary e cai para value`() {
        assertEquals("22°", TextosDosCartoes.valorGrande(cartao("climate", primario = "22°", valor = "x")))
        assertEquals("x", TextosDosCartoes.valorGrande(cartao("climate", valor = "x")))
    }

    @Test fun `sem secondary o detalhe e so a linha de metricas`() {
        assertEquals("EV 42 Km  ·  fuel 510 km", TextosDosCartoes.detalhe(cartao("range", metricaA = "EV 42 KM", metricaB = "FUEL 510 KM"), pt))
        assertEquals("só A", TextosDosCartoes.detalhe(cartao("range", metricaA = "só A"), pt))
        assertEquals("só B", TextosDosCartoes.detalhe(cartao("range", metricaB = "só B"), pt))
        assertEquals("", TextosDosCartoes.detalhe(cartao("range"), pt))
    }

    @Test fun `com secondary a linha de metricas vai para a segunda linha`() {
        val c = cartao("range", secundario = "VEÍCULO · AUTONOMIA AO VIVO", metricaA = "EV 42 KM", metricaB = "FUEL 510 KM")
        assertEquals("Veículo · autonomia ao vivo\nEV 42 Km  ·  fuel 510 km", TextosDosCartoes.detalhe(c, pt))
        assertEquals("Conforto\nVent. 3  ·  auto", TextosDosCartoes.detalhe(cartao("climate", secundario = "Conforto", metricaA = "Vent. 3", metricaB = "auto"), pt))
    }

    @Test fun `com secondary e sem metricas so ha uma linha`() {
        assertEquals("Conforto", TextosDosCartoes.detalhe(cartao("climate", secundario = "Conforto"), pt))
    }

    @Test fun `power tem tres linhas fixas inclusive vazias`() {
        val c = cartao("power", metricaA = "Tração elétrica", metricaB = "SOC 64%", secundario = "Veículo · potência ao vivo")
        assertEquals("Tração elétrica\nSOC 64%\nVeículo · potência ao vivo", TextosDosCartoes.detalheBruto(c))
        assertEquals("\n\n", TextosDosCartoes.detalheBruto(cartao("power")))
        assertEquals(4, TextosDosCartoes.maximoDeLinhasDoDetalhe(c))
        assertEquals(2, TextosDosCartoes.maximoDeLinhasDoDetalhe(cartao("climate")))
    }

    // --- acessibilidade ---

    @Test fun `descricao do power`() {
        val c = cartao("power", primario = "12.4 kW", metricaA = "Tração elétrica", metricaB = "SOC 64%", secundario = "Veículo")
        assertEquals("Power flow. 12.4 kW. Tração elétrica. SOC 64%. Source Veículo. Opens power flow details.", TextosDosCartoes.descricao(c))
        assertEquals("Power flow. Power flow unavailable. Opens power flow details.", TextosDosCartoes.descricao(cartao("power")))
    }

    @Test fun `descricao do status`() {
        assertEquals(
            "Vehicle status. Estado indisponível. Source sem sinal. Opens vehicle status details.",
            TextosDosCartoes.descricao(cartao("status", primario = "Estado indisponível", secundario = "sem sinal")),
        )
        assertEquals("Vehicle status. Status unavailable. Opens vehicle status details.", TextosDosCartoes.descricao(cartao("status")))
    }

    @Test fun `descricao da navegacao`() {
        val c = cartao("navigation", primario = "Av. Paulista", navCidadeOciosa = "São Paulo", secundario = "x", metricaA = "450 m", navAcaoOciosa = "Inicie")
        assertEquals("Navigation. Av. Paulista. São Paulo. x. 450 m. Inicie. Opens navigation.", TextosDosCartoes.descricao(c))
        assertEquals("Navigation. No route data. Opens navigation.", TextosDosCartoes.descricao(cartao("navigation")))
    }

    @Test fun `descricao generica`() {
        assertEquals("Relógio: 22:10", TextosDosCartoes.descricao(cartao("clock", titulo = "Relógio", valor = "22:10")))
        assertEquals("Páginas", TextosDosCartoes.descricao(cartao("desktops", titulo = "Páginas")))
        assertEquals("Modo. Change to the next setting", TextosDosCartoes.descricaoDoGrafico(cartao("driveMode", titulo = "Modo")))
    }
}
