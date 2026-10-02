package br.com.opengw.ajustes

import br.com.opengw.nucleo.Preferencia
import br.com.opengw.nucleo.Preferencias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tudo o que vem do computador passa por aqui antes do disco. A regra que importa: o tipo que
 * chega ao `SharedPreferences` é o mesmo que a tela do carro grava — senão a tela quebra com
 * ClassCastException na próxima vez que abrir.
 */
class ConversaoDeAjusteTest {

    private fun a(chave: Preferencia) = CatalogoDeAjustes.ajuste(chave.chave)!!

    private fun valido(ajuste: Ajuste, bruto: Any?, faixa: ClosedFloatingPointRange<Double>? = null): Any {
        val v = ConversaoDeAjuste.validar(ajuste, bruto, faixa)
        assertTrue("esperava válido: $bruto -> $v", v is Validacao.Valido)
        return (v as Validacao.Valido).valor
    }

    private fun recusado(ajuste: Ajuste, bruto: Any?) {
        assertTrue("esperava recusa: $bruto", ConversaoDeAjuste.validar(ajuste, bruto) is Validacao.Recusado)
    }

    @Test
    fun `interruptor so aceita ligado ou desligado`() {
        val barra = a(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR)
        assertEquals(true, valido(barra, true))
        assertEquals(false, valido(barra, "false"))
        recusado(barra, 1)
        recusado(barra, "sim")
        recusado(barra, null)
    }

    @Test
    fun `numero fora da faixa e preso nela como na tela do carro`() {
        val altura = a(Preferencias.BOTTOM_BAR_HEIGHT_DP)
        assertEquals(140, valido(altura, 500))
        assertEquals(40, valido(altura, 3))
        // O carro não encaixa no passo — quem encaixa é o controle deslizante. Aqui só a faixa.
        assertEquals(60, valido(altura, 60))
        assertEquals(61, valido(altura, 60.6))
        assertEquals(72, valido(altura, "72"))
        recusado(altura, "alto")
        recusado(altura, Double.NaN)
    }

    @Test
    fun `decimal chega ao disco como Float`() {
        val escala = a(Preferencias.GAUGE_NUMBER_SCALE)
        val v = valido(escala, "1,25")
        assertEquals(1.25, v)
        assertTrue(ConversaoDeAjuste.paraDisco(Formato.DECIMAL, v) is Float)
        // E volta sem lixo binário.
        assertEquals(1.2, ConversaoDeAjuste.doDisco(escala, Formato.DECIMAL, 1.2f))
    }

    @Test
    fun `inteiro chega ao disco como Int`() {
        val volume = a(Preferencias.ASD_VOLUME)
        assertTrue(ConversaoDeAjuste.paraDisco(Formato.INTEIRO, valido(volume, 70.0)) is Int)
    }

    @Test
    fun `opcao inexistente e recusada`() {
        val logo = a(Preferencias.VOLUME_OVERLAY_LOGO_TYPE)
        assertEquals("jbl", valido(logo, "jbl"))
        recusado(logo, "bose")
    }

    @Test
    fun `opcao guardada como inteiro aceita numero e volta como texto`() {
        val canto = a(Preferencias.RESOURCE_OVERLAY_CORNER)
        assertEquals("2", valido(canto, 2))
        assertEquals("2", valido(canto, "2"))
        recusado(canto, 7)
        assertEquals(2, ConversaoDeAjuste.paraDisco(Formato.INTEIRO, "2"))
        assertEquals("2", ConversaoDeAjuste.doDisco(canto, Formato.INTEIRO, 2))
    }

    @Test
    fun `texto e aparado e cortado no limite da tela`() {
        val texto = a(Preferencias.VOLUME_OVERLAY_LOGO_CUSTOM_TEXT)
        assertEquals("OPEN GW MUIT", valido(texto, "  OPEN GW MUITO LONGO  "))
        recusado(texto, 12)
    }

    @Test
    fun `cor aceita as formas comuns e sai opaca`() {
        val cor = a(Preferencias.HUD_SPEED_COLOR)
        assertEquals("#FFAA00", valido(cor, "#ffaa00"))
        assertEquals("#FFAA00", valido(cor, "fa0"))
        assertEquals("#112233", valido(cor, "#80112233"))
        recusado(cor, "vermelho")
        recusado(cor, "#12345")
        val argb = ConversaoDeAjuste.paraDisco(Formato.COR_ARGB, "#112233") as Int
        assertEquals(0xFF112233.toInt(), argb)
        assertEquals("#112233", ConversaoDeAjuste.doDisco(cor, Formato.COR_ARGB, argb))
    }

    @Test
    fun `tipo inesperado no disco cai no padrao em vez de mostrar lixo`() {
        val barra = a(Preferencias.ENABLE_PERSISTENT_BOTTOM_BAR)
        assertNull(ConversaoDeAjuste.doDisco(barra, Formato.BOOLEANO, "true"))
    }

    @Test
    fun `faixa do amplificador substitui a do catalogo`() {
        assertEquals(-7.0..7.0, ConversaoDeAjuste.lerFaixa("-7,7"))
        assertEquals(0.0..14.0, ConversaoDeAjuste.lerFaixa("0~14"))
        assertEquals(0.0..20.0, ConversaoDeAjuste.lerFaixa("20"))
        assertNull(ConversaoDeAjuste.lerFaixa(""))
        val grave = CatalogoDeAjustes.todos.first { it.guardado is Guardado.Veiculo && !(it.guardado as Guardado.Veiculo).booleano }
        assertEquals(14, valido(grave, 20, 0.0..14.0))
    }

    @Test
    fun `barramento fala 1 e 0`() {
        val interruptor = Guardado.Veiculo("x", booleano = true)
        assertEquals("1", ConversaoDeAjuste.paraVeiculo(interruptor, true))
        assertEquals(true, ConversaoDeAjuste.doVeiculo(interruptor, "1"))
        assertEquals(false, ConversaoDeAjuste.doVeiculo(interruptor, "0"))
        assertNull(ConversaoDeAjuste.doVeiculo(interruptor, ""))
        val numero = Guardado.Veiculo("y", booleano = false)
        assertEquals("-3", ConversaoDeAjuste.paraVeiculo(numero, -3))
        assertEquals(5, ConversaoDeAjuste.doVeiculo(numero, "5"))
    }

    @Test
    fun `horario aceita H MM e recusa o impossivel`() {
        val hora = CatalogoDeAjustes.ajuste(Preferencias.OPEN_SUNROOF_CURTAIN_START_HOUR.chave)!!
        assertEquals("07:05", valido(hora, "7:5"))
        assertEquals("23:59", valido(hora, " 23:59 "))
        recusado(hora, "24:00")
        recusado(hora, "12:60")
        recusado(hora, "meio-dia")
        recusado(hora, 1830)
    }
}
