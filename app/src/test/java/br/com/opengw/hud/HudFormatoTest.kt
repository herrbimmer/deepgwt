package br.com.opengw.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Os valores do carro como eles chegam de verdade, e o texto que o HUD escreve com eles.
 *
 * Os valores crus abaixo são os da captura do próprio veículo
 * (knowledge/session_2026-09-15/raw_http/telemetry.json). Até a Alpha 5.7 o HUD lia todos como
 * número solto: consumo `--` sempre, porta dianteira esquerda e porta-malas nunca em alerta, aviso
 * de pneu nunca aceso.
 */
class HudFormatoTest {

    // ── Vetores do barramento ──────────────────────────────────────────────────────────────

    @Test
    fun `o vetor de portas do carro vem entre chaves`() {
        assertEquals(listOf("Dianteira esquerda"), HudTelemetry.portasAbertasEm("{1,0,0,0,0,0}"))
        assertEquals(listOf("Porta-malas"), HudTelemetry.portasAbertasEm("{0,0,0,0,0,1}"))
        assertEquals(emptyList<String>(), HudTelemetry.portasAbertasEm("{0,0,0,0,0,0}"))
        assertEquals(
                listOf("Dianteira esquerda", "Traseira direita", "Porta-malas"),
                HudTelemetry.portasAbertasEm("{1,0,0,1,0,1}")
        )
    }

    @Test
    fun `tres portas ou mais saem abreviadas no detalhe`() {
        val alerta =
                HudTelemetry.montarAlertas(
                                listOf("Dianteira esquerda", "Traseira direita", "Porta-malas"),
                                emptyList()
                        )
                        .single()
        assertEquals("3 portas abertas", alerta.titulo)
        assertEquals("Diant. esq. · Tras. dir. · Porta-malas", alerta.detalhe)
    }

    @Test
    fun `aviso de pneu e um vetor com um slot por roda`() {
        assertEquals(false, HudFormato.algumAceso("{0,0,0,0}"))
        assertEquals(true, HudFormato.algumAceso("{0,1,0,0}"))
        assertEquals(true, HudFormato.algumAceso("1"))
        assertEquals(true, HudFormato.algumAceso("true"))
        assertNull(HudFormato.algumAceso(""))
    }

    @Test
    fun `limite da via aceita o numero solto e o vetor, e recusa o sentinela`() {
        assertNull(HudFormato.limiteDaVia("{-2147483648,-2147483648}"))
        assertEquals(80, HudFormato.limiteDaVia("80"))
        assertEquals(80, HudFormato.limiteDaVia("{80,-2147483648}"))
        assertNull(HudFormato.limiteDaVia("0"))
        assertNull(HudFormato.limiteDaVia(""))
    }

    // ── Consumo ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `consumo do carro parado com o motor a combustao desligado`() {
        // Exatamente o valor da captura: READY, parado, motor a combustão desligado.
        assertEquals(HudFormato.Consumo.Eletrico, HudFormato.consumo("{4.0,0.0}"))
    }

    @Test
    fun `andando o valor e L por 100 km e o HUD mostra km por litro como o painel`() {
        val andando = HudFormato.consumo("{1.0,7.8}") as HudFormato.Consumo.Andando
        assertEquals(12.82f, andando.kmPorLitro, 0.01f)
        assertEquals("12,8", HudFormato.kmPorLitro(andando.kmPorLitro))
        // Número solto é a métrica 1, como no painel.
        assertTrue(HudFormato.consumo("7.8") is HudFormato.Consumo.Andando)
    }

    @Test
    fun `parado com o motor ligado o consumo e por hora`() {
        assertEquals(HudFormato.Consumo.Parado(0.8f), HudFormato.consumo("{4.0,0.8}"))
    }

    @Test
    fun `consumo sem leitura ou com metrica desconhecida nao vira numero`() {
        assertNull(HudFormato.consumo(""))
        assertNull(HudFormato.consumo("--"))
        assertNull(HudFormato.consumo("{9.0,3.0}"))
        assertNull(HudFormato.consumo("{1.0}"))
    }

    // ── Escrita ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `milhar com ponto como se escreve no Brasil`() {
        assertEquals("49.435", HudFormato.milhar(49435))
        assertEquals("149.435", HudFormato.milhar(149435))
        assertEquals("1.000", HudFormato.milhar(1000))
        assertEquals("999", HudFormato.milhar(999))
        assertEquals("0", HudFormato.milhar(0))
        assertEquals("-1.250", HudFormato.milhar(-1250))
    }

    @Test
    fun `decimal com virgula sem depender do idioma do aparelho`() {
        assertEquals("7,8", HudFormato.decimal(7.84f))
        assertEquals("0,8", HudFormato.decimal(0.8f))
        // Acima de 99,9 km/L o decimal não informa nada.
        assertEquals("120", HudFormato.kmPorLitro(120f))
    }

    @Test
    fun `temperatura arredonda e recusa sentinela`() {
        assertEquals("21°", HudFormato.graus(20.5f))
        assertEquals("23°", HudFormato.graus(23.0f))
        assertEquals(20.5f, HudFormato.temperatura("20.5")!!, 0f)
        assertNull(HudFormato.temperatura("255"))
        assertNull(HudFormato.temperatura("-40"))
        assertNull(HudFormato.temperatura(""))
    }

    @Test
    fun `marcha pelo codigo do carro ou pela letra do simulador`() {
        assertEquals("P", HudFormato.marcha("3"))
        assertEquals("D", HudFormato.marcha("2.0"))
        assertEquals("R", HudFormato.marcha("4"))
        assertEquals("D", HudFormato.marcha("d"))
        assertNull(HudFormato.marcha("9"))
        assertNull(HudFormato.marcha(""))
    }

    @Test
    fun `nome do app de midia so quando conhecido`() {
        assertEquals("Spotify", HudFormato.appDeMidia("com.spotify.music"))
        assertEquals("Bluetooth", HudFormato.appDeMidia("com.android.bluetooth"))
        assertEquals("", HudFormato.appDeMidia("com.exemplo.qualquer"))
        assertEquals("", HudFormato.appDeMidia(null))
    }

    @Test
    fun `numeros ignora chaves colchetes e separadores`() {
        assertEquals(listOf(4f, 0f), HudFormato.numeros("{4.0,0.0}"))
        assertEquals(listOf(1f, 2f, 3f), HudFormato.numeros("[1; 2 3]"))
        assertTrue(HudFormato.numeros("").isEmpty())
        assertFalse(HudFormato.numeros("{abc}").isNotEmpty())
    }
}
