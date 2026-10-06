package br.com.deepgwt.tela3d.cartoes

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculoDoRelogioTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val us = Locale.US

    private fun instante(ano: Int, mes: Int, dia: Int, h: Int, m: Int, s: Int = 0): Long {
        val c = Calendar.getInstance(utc)
        c.clear()
        c.set(ano, mes, dia, h, m, s)
        return c.timeInMillis
    }

    private fun calcular(h: Int, m: Int, formato: String, sistema24: Boolean = true, lombadaNumerica: Boolean = false) =
        CalculoDoRelogio.calcular(instante(2026, Calendar.OCTOBER, 4, h, m), utc, us, formato, sistema24, lombadaNumerica)

    @Test fun `22 e 10 em 24 horas`() {
        val r = calcular(22, 10, "24")
        assertTrue(r.vinteEQuatroHoras)
        assertEquals("22", r.hora)
        assertEquals("10", r.minuto)
        assertEquals("22:10", r.tempo)
        assertEquals("", r.periodo)
        assertEquals(60f, r.anguloDoMinuto, 0f)
        assertEquals(305f, r.anguloDaHora, 0f)
    }

    @Test fun `22 e 10 em 12 horas`() {
        val r = calcular(22, 10, "12")
        assertFalse(r.vinteEQuatroHoras)
        assertEquals("10:10", r.tempo)
        assertEquals("PM", r.periodo)
        // os ângulos usam a hora real: não mudam com o formato
        assertEquals(60f, r.anguloDoMinuto, 0f)
        assertEquals(305f, r.anguloDaHora, 0f)
    }

    @Test fun `zero hora e cinco em 12 horas`() {
        val r = calcular(0, 5, "12")
        assertEquals("12:05", r.tempo)
        assertEquals("AM", r.periodo)
        assertEquals(30f, r.anguloDoMinuto, 0f)
        assertEquals(2.5f, r.anguloDaHora, 0f)
    }

    @Test fun `meio dia em 12 horas e sempre dois digitos`() {
        assertEquals("12:00", calcular(12, 0, "12").tempo)
        assertEquals("PM", calcular(12, 0, "12").periodo)
        assertEquals("07:53", calcular(7, 53, "24").tempo)
        assertEquals("07:53", calcular(7, 53, "12").tempo)
        assertEquals("AM", calcular(7, 53, "12").periodo)
        assertEquals("00:00", calcular(0, 0, "24").tempo)
    }

    @Test fun `formato do sistema segue o sistema`() {
        assertEquals("22:10", calcular(22, 10, "system", sistema24 = true).tempo)
        assertEquals("10:10", calcular(22, 10, "system", sistema24 = false).tempo)
        assertTrue(CalculoDoRelogio.usa24Horas("24", false))
        assertFalse(CalculoDoRelogio.usa24Horas("12", true))
        assertFalse(CalculoDoRelogio.usa24Horas("system", false))
    }

    @Test fun `datas no idioma pedido`() {
        val r = calcular(22, 10, "24")
        assertEquals("Sun", r.diaDaSemana)
        assertEquals("04 Oct", r.dataCurta)
        assertEquals("04", r.dia)
        assertEquals("Sun, 04 Oct", r.linhaDaData)
    }

    @Test fun `lombada numerica ou com nome do mes`() {
        assertEquals("10", calcular(22, 10, "24", lombadaNumerica = true).mesDaLombada)
        assertEquals("OCT", calcular(22, 10, "24", lombadaNumerica = false).mesDaLombada)
    }

    @Test fun `o fuso pedido vale`() {
        val saoPaulo = TimeZone.getTimeZone("America/Sao_Paulo")
        val r = CalculoDoRelogio.calcular(instante(2026, Calendar.OCTOBER, 4, 22, 10), saoPaulo, us, "24", true, false)
        assertEquals("19:10", r.tempo)
    }

    @Test fun `linha da data comeca em maiuscula no idioma`() {
        val r = CalculoDoRelogio.calcular(instante(2026, Calendar.OCTOBER, 4, 12, 0), utc, Locale.forLanguageTag("pt-BR"), "24", true, false)
        assertTrue(r.linhaDaData, r.linhaDaData.first().isUpperCase())
    }

    @Test fun `proximo tique e o proximo minuto cheio mais 20 ms`() {
        assertEquals(60_020L, CalculoDoRelogio.atrasoAteOProximoTique(0L))
        assertEquals(20L + 1L, CalculoDoRelogio.atrasoAteOProximoTique(59_999L))
        assertEquals(30_020L, CalculoDoRelogio.atrasoAteOProximoTique(30_000L))
    }
}
