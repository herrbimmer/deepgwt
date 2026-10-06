// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Seções A, I e J da especificação `motor-nucleo.md`. */
class PartidaTest {

    private val b = Bancada()
    private val pacote = Bancada.PACOTE

    // ── A ──

    @Test
    fun `sem Shizuku espera 3 vezes 500 ms e falha`() {
        b.central.shizuku = false

        assertFalse(b.partir())

        assertEquals(listOf(500L, 500L, 500L), b.agenda.pausas.toList())
        assertEquals(4, b.central.pingsDoShizuku.get())
        assertTrue(b.central.abertos.isEmpty())
        assertFalse(b.nucleo.pronto)
        assertTrue("motor.partida" in b.eventosDiario())
    }

    @Test
    fun `Shizuku que chega na terceira olhada basta`() {
        b.central.shizuku = true
        b.central.respostasDoShizuku.addAll(listOf(false, false))

        assertTrue(b.partir())

        assertEquals(listOf(500L, 500L), b.agenda.pausas.toList())
    }

    @Test
    fun `servico ausente ou sem ping falha`() {
        b.central.servicoAusente = true
        assertFalse(b.partir())

        b.central.servicoAusente = false
        b.central.aoAbrir = { it.respondePing = false }
        assertFalse(b.partir())
        assertFalse(b.nucleo.controleVivo)
    }

    @Test
    fun `partida faz aviso, voz, registro, chaves e retrato nessa ordem`() {
        b.central.valores["car.basic.vehicle_speed"] = "12.0"
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        assertTrue(b.partir())

        val canal = b.central.canal
        assertEquals(
            listOf(
                "abrirControle",
                "c1:aviso",
                "voz",
                "c1:registrar:$pacote",
                "c1:somar:${ChavesDoMotor.ACOMPANHADAS_SEMPRE.size}",
                "c1:lote:${ChavesDoMotor.ACOMPANHADAS_SEMPRE.size}",
            ),
            b.eventos.filter { it.startsWith("c1:") || it == "abrirControle" || it == "voz" },
        )
        assertEquals(ChavesDoMotor.ACOMPANHADAS_SEMPRE, canal.chavesSomadas.single())
        assertEquals(listOf("car.basic.vehicle_speed" to "12.0"), ouvinte.recebidos.toList())
        assertTrue(b.nucleo.pronto)
        assertTrue(b.nucleo.controleVivo)
        assertTrue(b.nucleo.fimDaPartida >= b.nucleo.inicioDaPartida)
        assertTrue(b.nucleo.inicioDaPartida > 0)
    }

    @Test
    fun `voz faltando nao derruba a partida`() {
        b.central.vozDisponivel = false

        assertTrue(b.partir())
    }

    @Test
    fun `re-partida desregistra antes e preserva cache, ouvintes e chaves dinamicas`() {
        val antigo = b.partirLimpo()
        b.nucleo.chegouDoCarro("car.k", "1")
        b.nucleo.acompanhar(listOf("car.dinamica"))
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)
        b.eventos.clear()

        assertTrue(b.partir())

        val desregistrou = b.eventos.indexOf("c1:desregistrar:$pacote")
        val registrou = b.eventos.indexOf("c2:registrar:$pacote")
        assertTrue(desregistrou in 0 until registrou)
        assertTrue(b.eventos.indexOf("c1:semAviso") < desregistrou)
        assertEquals("1", b.nucleo.espiar("car.k"))
        assertTrue("car.dinamica" in b.central.canal.chavesSomadas.single())
        b.nucleo.chegouDoCarro("car.k", "2")
        assertEquals(listOf("car.k" to "2"), ouvinte.recebidos.toList())
        assertTrue(b.nucleo.pronto)
        assertTrue(antigo.aoMorrer == null)
    }

    @Test
    fun `re-partida nao desregistra alca antiga que nao responde`() {
        val antigo = b.partirLimpo()
        antigo.respondePing = false

        assertTrue(b.partir())

        assertFalse(b.eventos.any { it.startsWith("c1:desregistrar") })
    }

    @Test
    fun `falha numa chamada a central devolve false e deixa o canal morto`() {
        b.central.aoAbrir = { it.falharEm += "registrar" }

        assertFalse(b.partir())

        assertFalse(b.nucleo.pronto)
        assertFalse(b.nucleo.controleVivo)
        assertTrue(b.central.canal.aoMorrer == null)
        assertEquals(0, b.agenda.pendentes()) // sem vigia
    }

    @Test
    fun `falha no retrato tambem devolve false`() {
        b.central.aoAbrir = { it.falharEm += "buscarVarias" }

        assertFalse(b.partir())
        assertFalse(b.nucleo.controleVivo)
    }

    @Test
    fun `boot marcado na partida so se ainda for zero`() {
        b.nucleo.marcarBoot(500)
        b.nucleo.marcarBoot(700)
        assertEquals(500, b.nucleo.instanteDoBoot)

        val outra = Bancada()
        outra.partir()
        assertEquals(1_000, outra.nucleo.instanteDoBoot)
        outra.nucleo.marcarBoot(42)
        assertEquals(1_000, outra.nucleo.instanteDoBoot)
    }

    @Test
    fun `eventos durante a partida vao aos ouvintes mas o gancho so depois`() {
        val ouvinte = OuvinteAnotador(b.eventos)
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.observar(ouvinte)
        b.nucleo.ganchoDoCarro = gancho
        b.central.aoAbrir = { canal -> canal.aoRegistrar = { b.nucleo.chegouDoCarro("car.durante", "1") } }

        assertTrue(b.partir())
        b.nucleo.chegouDoCarro("car.depois", "2")

        assertEquals(listOf("car.durante" to "1", "car.depois" to "2"), ouvinte.recebidos.toList())
        assertEquals(listOf("car.depois" to "2"), gancho.recebidos.toList())
    }

    @Test
    fun `partidas simultaneas esperam uma a outra`() {
        val dentro = CountDownLatch(1)
        val solta = CountDownLatch(1)
        b.central.aoAbrir = { canal ->
            if (b.central.abertos.isEmpty()) {
                canal.aoRegistrar = {
                    dentro.countDown()
                    solta.await(5, TimeUnit.SECONDS)
                }
            }
        }
        val primeira = thread { b.partir() }
        assertTrue(dentro.await(5, TimeUnit.SECONDS))
        val segunda = thread { b.partir() }
        Thread.sleep(50)

        assertEquals(1, b.central.abertos.size)
        solta.countDown()
        primeira.join()
        segunda.join()
        assertEquals(2, b.central.abertos.size)
    }

    // ── I.1 morte ──

    @Test
    fun `morte agenda recuperacao na linha do motor`() {
        val antigo = b.partirLimpo()

        antigo.morrer()
        assertEquals(1, b.central.abertos.size)
        b.agenda.rodarPendentes()

        assertEquals(2, b.central.abertos.size)
        assertTrue(b.nucleo.controleVivo)
        assertTrue("motor.canal_morreu" in b.eventosDiario())
        assertTrue("motor.recuperacao_ok" in b.eventosDiario())
    }

    @Test
    fun `aviso de morte de uma alca ja trocada e ignorado`() {
        val antigo = b.partirLimpo()
        val avisoAntigo = antigo.aoMorrer!!
        assertTrue(b.partir())
        b.agenda.rodarPendentes()
        b.eventos.clear()

        avisoAntigo()
        b.agenda.rodarPendentes()

        assertEquals(2, b.central.abertos.size)
        assertFalse(b.eventos.contains("diario:motor.canal_morreu"))
    }

    // ── I.3 vigia ──

    @Test
    fun `vigia confere a cada 10 s e recupera canal morto`() {
        val antigo = b.partirLimpo()
        antigo.vivoAgora = false // morreu sem aviso

        b.agenda.avancar(9_999)
        assertEquals(1, b.central.abertos.size)
        b.agenda.avancar(1)
        assertEquals(2, b.central.abertos.size)

        b.agenda.avancar(10_000) // canal novo vivo: nada a fazer
        assertEquals(2, b.central.abertos.size)

        b.central.canal.vivoAgora = false
        b.agenda.avancar(10_000)
        assertEquals(3, b.central.abertos.size)
    }

    @Test
    fun `re-partida troca o vigia em vez de somar outro`() {
        b.partirLimpo()
        assertTrue(b.partir())
        assertEquals(1, b.agenda.pendentes()) // um vigia só
        b.central.canal.vivoAgora = false

        b.agenda.avancar(10_000)

        assertEquals(1, b.eventosDiario().count { it == "motor.recuperacao_inicio" })
    }

    // ── I.4 recuperação ──

    @Test
    fun `recuperacao desregistra, registra, pede chaves e faz o retrato nessa ordem`() {
        val antigo = b.partirLimpo()
        b.nucleo.acompanhar(listOf("car.dinamica"))
        antigo.vivoAgora = false // ainda responde ao ping
        b.eventos.clear()

        b.nucleo.recuperar("teste")

        val sequencia = b.eventos.filter { it.startsWith("c1:desregistrar") || it.startsWith("c2:") }
        assertEquals(
            listOf(
                "c1:desregistrar:$pacote",
                "c2:aviso",
                "c2:registrar:$pacote",
                "c2:somar:${ChavesDoMotor.ACOMPANHADAS_SEMPRE.size + 1}",
                "c2:lote:${ChavesDoMotor.ACOMPANHADAS_SEMPRE.size + 1}",
            ),
            sequencia,
        )
        assertFalse(b.eventos.contains("voz")) // não re-obtém as alças da voz
        val ok = b.diario.single { it.first == "motor.recuperacao_ok" }.second
        assertEquals(1, ok["recuperacoes"])
        val inicio = b.diario.single { it.first == "motor.recuperacao_inicio" }.second
        assertEquals("teste", inicio["motivo"])
        assertEquals(1, inicio["tentativa"])
    }

    @Test
    fun `recuperacao sem Shizuku falha sem tocar na alca`() {
        b.partirLimpo()
        b.central.shizuku = false

        b.nucleo.recuperar("vigia")

        assertEquals(1, b.central.abertos.size)
        assertEquals(listOf("motor.recuperacao_inicio", "motor.recuperacao_falha"), b.eventosDiario())
        assertTrue(b.nucleo.pronto)
    }

    @Test
    fun `recuperacao que falha deixa o canal morto e o vigia tenta de novo`() {
        val antigo = b.partirLimpo()
        antigo.vivoAgora = false
        b.central.servicoAusente = true

        b.agenda.avancar(10_000)
        assertFalse(b.nucleo.controleVivo)
        b.central.servicoAusente = false
        b.agenda.avancar(10_000)

        assertTrue(b.nucleo.controleVivo)
        assertEquals(2, b.eventosDiario().count { it == "motor.recuperacao_inicio" })
    }

    @Test
    fun `uma recuperacao por vez`() {
        val antigo = b.partirLimpo()
        antigo.vivoAgora = false
        b.central.aoAbrir = { canal -> canal.aoRegistrar = { b.nucleo.recuperar("de novo") } }

        b.nucleo.recuperar("primeira")

        assertEquals(2, b.central.abertos.size)
        assertEquals(1, b.eventosDiario().count { it == "motor.recuperacao_inicio" })
    }

    @Test
    fun `recuperacao durante uma partida nao faz nada`() {
        b.partirLimpo()
        val dentro = CountDownLatch(1)
        val solta = CountDownLatch(1)
        b.central.aoAbrir = { canal ->
            canal.aoRegistrar = {
                dentro.countDown()
                solta.await(5, TimeUnit.SECONDS)
            }
        }
        val partida = thread { b.partir() }
        assertTrue(dentro.await(5, TimeUnit.SECONDS))

        b.nucleo.recuperar("vigia")

        solta.countDown()
        partida.join()
        assertEquals(2, b.central.abertos.size)
        assertFalse("motor.recuperacao_inicio" in b.eventosDiario())
    }

    // ── J ──

    @Test
    fun `encerrar para o vigia e desfaz o aviso sem desregistrar`() {
        val canal = b.partirLimpo()

        b.nucleo.encerrar()
        canal.vivoAgora = false
        b.agenda.avancar(30_000)

        assertTrue(canal.aoMorrer == null)
        assertEquals(1, b.central.abertos.size)
        assertFalse(b.eventos.any { it.contains("desregistrar") })
        b.nucleo.chegouDoCarro("car.k", "1") // eventos continuam chegando
        assertEquals("1", b.nucleo.espiar("car.k"))
    }

    // ── Alças da voz (Decisões da sala suja, 8) ──

    @Test
    fun `morte de uma alca da voz renova depois de 3 s`() {
        b.partirLimpo()
        val renovacoes = b.central.renovacoesDeVoz.get()

        b.central.vozViva = false
        b.central.aoMorrerAVoz!!()
        b.central.aoMorrerAVoz!!() // avisos repetidos agendam uma vez só
        b.agenda.avancar(2_999)
        assertEquals(renovacoes, b.central.renovacoesDeVoz.get())
        b.agenda.avancar(1)

        assertEquals(renovacoes + 1, b.central.renovacoesDeVoz.get())
        assertTrue(b.central.vozViva)
    }

    @Test
    fun `vigia renova a voz incompleta`() {
        b.central.vozDisponivel = false
        b.partirLimpo()
        b.central.vozDisponivel = true

        b.agenda.avancar(10_000)

        assertTrue(b.central.vozViva)
    }

    @Test
    fun `garantirVoz tenta no maximo a cada 2 s`() {
        b.central.vozDisponivel = false
        b.partirLimpo()
        val depoisDaPartida = b.central.renovacoesDeVoz.get()

        b.nucleo.garantirVoz()
        assertEquals(depoisDaPartida, b.central.renovacoesDeVoz.get())
        b.agenda.avancar(2_000)
        b.nucleo.garantirVoz()
        b.nucleo.garantirVoz()
        assertEquals(depoisDaPartida + 1, b.central.renovacoesDeVoz.get())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
