package br.com.opengw.rede

import org.junit.Assert.assertEquals
import org.junit.Test

/** V4 da especificação: o contador de reserva. */
class ContadorDeReservaTest {

    private val armazem = ArmazemFalso()
    private val contador = ContadorDeReserva(armazem)

    private fun estado(): Pair<Long, Long> =
        armazem.lerLong(ChavesDaRede.RESERVA_ACUMULADO, 0) to armazem.lerLong(ChavesDaRede.RESERVA_ULTIMA_LEITURA, -1)

    @Test
    fun `v4 sequencia completa`() {
        // 1: só base
        assertEquals(0L, contador.atualizar("2026-9", 1000, 0))
        assertEquals(0L to 1000L, estado())
        assertEquals("2026-9", armazem.lerString(ChavesDaRede.RESERVA_ETIQUETA, ""))
        // 2
        assertEquals(500L, contador.atualizar("2026-9", 1000, 500))
        assertEquals(500L to 1500L, estado())
        // 3: sem mudança
        assertEquals(500L, contador.atualizar("2026-9", 700, 800))
        assertEquals(500L to 1500L, estado())
        // 4: o contador do sistema zerou
        assertEquals(700L, contador.atualizar("2026-9", 100, 100))
        assertEquals(700L to 200L, estado())
        // 5: não suportado: nada muda
        assertEquals(700L, contador.atualizar("2026-9", -1, 300))
        assertEquals(700L to 200L, estado())
        // 6
        assertEquals(950L, contador.atualizar("2026-9", 400, 50))
        assertEquals(950L to 450L, estado())
        // 7: o ciclo virou: zera e só estabelece a base
        assertEquals(0L, contador.atualizar("2026-10", 3000, 0))
        assertEquals(0L to 3000L, estado())
        assertEquals("2026-10", armazem.lerString(ChavesDaRede.RESERVA_ETIQUETA, ""))
        // 8
        assertEquals(0L, contador.atualizar("2026-10", 3000, 0))
        assertEquals(0L to 3000L, estado())
    }

    @Test
    fun `leitura invalida num ciclo novo zera sem base`() {
        contador.atualizar("2026-9", 1000, 0)
        contador.atualizar("2026-9", 2000, 0)
        assertEquals(0L, contador.atualizar("2026-10", -1, -1))
        assertEquals(0L to -1L, estado())
        assertEquals("2026-10", armazem.lerString(ChavesDaRede.RESERVA_ETIQUETA, ""))
    }

    @Test
    fun `o estado sobrevive a um contador novo (reinicio do processo)`() {
        contador.atualizar("2026-9", 1000, 0)
        val outro = ContadorDeReserva(armazem)
        assertEquals(250L, outro.atualizar("2026-9", 1250, 0))
    }
}
