// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seção E da especificação `motor-nucleo.md`. */
class AcompanharTest {

    private val b = Bancada()
    private val padrao = "car.basic.vehicle_speed"

    @Test
    fun `pede so as novas e grava o valor inicial sem avisar`() {
        val canal = b.partirLimpo()
        canal.valores["car.nova1"] = "1"
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.acompanhar(listOf(padrao, "car.nova1", "car.nova2", "car.nova1"))

        assertEquals(2, canal.chavesSomadas.size) // a da partida e esta
        assertEquals(listOf("car.nova1", "car.nova2"), canal.chavesSomadas.last())
        assertEquals(listOf("car.nova1", "car.nova2"), canal.lotesPedidos.last())
        assertEquals("1", b.nucleo.espiar("car.nova1"))
        assertTrue(b.nucleo.valoresConhecidos().containsKey("car.nova2"))
        assertTrue(ouvinte.recebidos.isEmpty())
        assertTrue(b.difusor.envios.isEmpty())
    }

    @Test
    fun `sem chave nova nao faz nada`() {
        val canal = b.partirLimpo()
        b.nucleo.acompanhar(listOf("car.x"))
        val lotes = canal.lotes.get()

        b.nucleo.acompanhar(listOf(padrao, "car.x"))

        assertEquals(2, canal.chavesSomadas.size) // a da partida e a de car.x
        assertEquals(lotes, canal.lotes.get())
    }

    @Test
    fun `pedidas antes da partida aparecem no primeiro registro`() {
        b.nucleo.acompanhar(listOf("car.cedo"))
        assertTrue(b.central.abertos.isEmpty())

        assertTrue(b.partir())

        val pedidas = b.central.canal.chavesSomadas.single()
        assertTrue("car.cedo" in pedidas)
        assertEquals(padrao, pedidas.first { it == padrao })
    }

    @Test
    fun `pedidas com o canal morto vao na recuperacao`() {
        val antigo = b.partirLimpo()
        antigo.vivoAgora = false

        b.nucleo.acompanhar(listOf("car.tarde"))
        assertTrue(antigo.chavesSomadas.size == 1) // só o da partida

        b.nucleo.recuperar("teste")
        assertTrue("car.tarde" in b.central.canal.chavesSomadas.single())
    }

    @Test
    fun `falha ao pedir mantem a chave para a proxima ligacao`() {
        val antigo = b.partirLimpo()
        antigo.falharEm += "somarChaves"

        b.nucleo.acompanhar(listOf("car.k"))
        antigo.falharEm.clear()
        antigo.vivoAgora = false
        b.nucleo.recuperar("teste")

        assertTrue("car.k" in b.central.canal.chavesSomadas.single())
    }

    @Test
    fun `chaves da pessoa entram na uniao depois das padrao`() {
        b.chavesDaPessoa = setOf("car.pessoa", padrao)
        b.nucleo.acompanhar(listOf("car.dinamica"))

        b.partir()

        val pedidas = b.central.canal.chavesSomadas.single()
        assertEquals(ChavesDoMotor.ACOMPANHADAS_SEMPRE + listOf("car.pessoa", "car.dinamica"), pedidas)
    }

    @Test
    fun `chave da pessoa nao e pedida de novo por acompanhar`() {
        b.chavesDaPessoa = setOf("car.pessoa")
        val canal = b.partirLimpo()

        b.nucleo.acompanhar(listOf("car.pessoa"))

        assertEquals(1, canal.chavesSomadas.size)
    }

    @Test
    fun `recarregar pede a uniao inteira, apaga o cache e refaz o retrato`() {
        val canal = b.partirLimpo()
        b.nucleo.chegouDoCarro("car.fora", "9")
        canal.valores[padrao] = "33.0"
        b.chavesDaPessoa = setOf("car.nova.da.pessoa")
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.recarregarChavesDoUsuario()

        assertTrue("car.nova.da.pessoa" in canal.chavesSomadas.last())
        assertEquals(ChavesDoMotor.ACOMPANHADAS_SEMPRE.size + 1, canal.chavesSomadas.last().size)
        assertNull(b.nucleo.espiar("car.fora"))
        assertEquals("33.0", b.nucleo.espiar(padrao))
        assertEquals(listOf(padrao to "33.0"), ouvinte.recebidos.toList())
    }

    @Test
    fun `recarregar antes da partida espera a partida`() {
        b.chavesDaPessoa = setOf("car.pessoa")
        b.nucleo.recarregarChavesDoUsuario()

        b.partir()
        val somasNaPartida = b.central.canal.chavesSomadas.size
        b.agenda.rodarPendentes()

        assertEquals(somasNaPartida + 1, b.central.canal.chavesSomadas.size)
    }

    @Test
    fun `recarregar com canal morto nao apaga o cache`() {
        val canal = b.partirLimpo()
        b.nucleo.chegouDoCarro("car.a", "1")
        canal.vivoAgora = false

        b.nucleo.recarregarChavesDoUsuario()

        assertEquals("1", b.nucleo.espiar("car.a"))
        assertFalse(canal.chavesSomadas.size > 1)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
