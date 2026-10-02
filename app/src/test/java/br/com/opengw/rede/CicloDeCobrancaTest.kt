package br.com.opengw.rede

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** V3 da especificação: ciclo de cobrança e etiqueta. */
class CicloDeCobrancaTest {

    private val saoPaulo = ZoneId.of("America/Sao_Paulo")
    private val utc = ZoneId.of("UTC")

    private fun confere(dia: Int, agora: Long, inicio: Long, etiqueta: String) {
        assertEquals("início de $agora (dia $dia)", inicio, CicloDeCobranca.inicioEmMs(agora, dia, saoPaulo))
        assertEquals("etiqueta de $agora (dia $dia)", etiqueta, CicloDeCobranca.etiqueta(agora, dia, saoPaulo))
    }

    @Test
    fun `v3 dia 15 no meio do ciclo`() = confere(15, 1791644400000, 1789441200000, "2026-9")

    @Test
    fun `v3 dia 15 um minuto antes de virar`() = confere(15, 1792033140000, 1789441200000, "2026-9")

    @Test
    fun `v3 dia 15 na virada`() = confere(15, 1792033200000, 1792033200000, "2026-10")

    @Test
    fun `v3 dia 1 em primeiro de janeiro`() = confere(1, 1767236400000, 1767236400000, "2026-1")

    @Test
    fun `v3 dia 10 volta para dezembro do ano anterior`() = confere(10, 1767614400000, 1765335600000, "2025-12")

    @Test
    fun `v3 dia 31 em marco usa o fim de fevereiro`() = confere(31, 1772708400000, 1772247600000, "2026-2")

    @Test
    fun `v3 dia 31 em 31 de marco`() = confere(31, 1774962000000, 1774926000000, "2026-3")

    @Test
    fun `v3 dia 31 em primeiro de maio usa 30 de abril`() = confere(31, 1777633200000, 1777518000000, "2026-4")

    @Test
    fun `v3 D2 dia 31 em 30 de abril ja e o ciclo do proprio dia`() = confere(31, 1777554000000, 1777518000000, "2026-4")

    @Test
    fun `v3 D2 dia 31 em 28 de fevereiro ja e o ciclo do proprio dia`() = confere(31, 1772283600000, 1772247600000, "2026-2")

    @Test
    fun `fuso UTC com dia 15 segue o calendario local e nao os ms`() {
        val agora = ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        val inicio = ZonedDateTime.of(2026, 9, 15, 0, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(inicio, CicloDeCobranca.inicioEmMs(agora, 15, utc))
        assertEquals("2026-9", CicloDeCobranca.etiqueta(agora, 15, utc))
        // O mesmo instante no fuso do Brasil cai no mesmo ciclo, mas começa três horas depois em ms.
        assertEquals(inicio + 3 * 3_600_000, CicloDeCobranca.inicioEmMs(agora, 15, saoPaulo))
    }

    @Test
    fun `dia fora de 1 a 31 e limitado`() {
        val agora = ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(CicloDeCobranca.inicioEmMs(agora, 1, utc), CicloDeCobranca.inicioEmMs(agora, 0, utc))
        assertEquals(CicloDeCobranca.inicioEmMs(agora, 31, utc), CicloDeCobranca.inicioEmMs(agora, 99, utc))
    }

    @Test
    fun `mes de 29 dias em ano bissexto`() {
        // Dia 31, em 29/02/2028 (bissexto): o ciclo começa no próprio dia 29.
        val agora = ZonedDateTime.of(2028, 2, 29, 9, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals("2028-2", CicloDeCobranca.etiqueta(agora, 31, utc))
        assertEquals(ZonedDateTime.of(2028, 2, 29, 0, 0, 0, 0, utc).toInstant().toEpochMilli(), CicloDeCobranca.inicioEmMs(agora, 31, utc))
    }
}
