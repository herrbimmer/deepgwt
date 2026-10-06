// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seção D da especificação `motor-nucleo.md`. */
class EscritaTest {

    private val b = Bancada()
    private val ar = "car.hvac.fan_speed"

    // ── D.2 tabela de verdade ──

    @Test
    fun `canal morto devolve false sem tentar`() {
        val canal = b.partirLimpo()
        canal.vivoAgora = false

        assertFalse(b.nucleo.escreverConferindo("car.x", "1"))
        assertTrue(canal.pedidos.isEmpty())
        assertFalse(b.eventos.any { it.endsWith("pedir:car.x=1") })
    }

    @Test
    fun `antes da partida devolve false`() {
        assertFalse(b.nucleo.escreverConferindo("car.x", "1"))
    }

    @Test
    fun `pedido que lanca devolve false`() {
        val canal = b.partirLimpo()
        canal.falharEm += "pedir"

        assertFalse(b.nucleo.escreverConferindo("car.x", "1"))
    }

    @Test
    fun `pedido que volta devolve true com a acao de escrita`() {
        val canal = b.partirLimpo()

        assertTrue(b.nucleo.escreverConferindo("car.x", "1"))
        assertEquals(Triple("cmd.common.request.set", "car.x", "1"), canal.pedidos.single())
    }

    @Test
    fun `escrever ignora o resultado e nunca lanca`() {
        val canal = b.partirLimpo()
        canal.falharEm += "pedir"

        b.nucleo.escrever("car.x", "1")
        canal.vivoAgora = false
        b.nucleo.escrever("car.x", "2")
    }

    @Test
    fun `escrita comum nao valida nem mexe no cache nem avisa`() {
        val canal = b.partirLimpo()
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        assertTrue(b.nucleo.escreverConferindo("", ""))

        assertEquals(Triple("cmd.common.request.set", "", ""), canal.pedidos.single())
        assertTrue(b.nucleo.valoresConhecidos().isEmpty())
        assertTrue(ouvinte.recebidos.isEmpty())
        assertTrue(b.difusor.envios.isEmpty())
        assertTrue(b.comandos.isEmpty())
    }

    // ── D.6 camada do ar ──

    @Test
    fun `ar segue shell, pausa, pedido, cache, ouvintes e retomada em 300 ms`() {
        b.partirLimpo()
        b.nucleo.appNaFrente = { "com.android.launcher" }
        val ouvinte = OuvinteAnotador(b.eventos, dentro = { chave, _ ->
            // Na publicação otimista o cache vem ANTES dos ouvintes.
            b.eventos += "dentro:${b.nucleo.ler(chave)}"
        })
        b.nucleo.observar(ouvinte)

        assertTrue(b.nucleo.escreverConferindo(ar, "5"))

        val essenciais = b.eventos.filterNot { it.startsWith("diario:") }
        assertEquals(
            listOf(
                "shell:pm disable-user --user 0 com.beantechs.hvac",
                "shell:am force-stop com.beantechs.hvac",
                "pausa:150",
                "c1:pedir:$ar=5",
                "ouvinte:$ar=5",
                "dentro:5",
            ),
            essenciais,
        )
        assertTrue(b.difusor.envios.isEmpty())

        b.agenda.avancar(299)
        assertFalse(b.comandos.any { it.startsWith("pm enable") })
        b.agenda.avancar(1)
        assertEquals("pm enable com.beantechs.hvac", b.comandos.last())
    }

    @Test
    fun `escritas seguidas do ar empurram a retomada e suspendem uma vez so`() {
        b.partirLimpo()

        b.nucleo.escreverConferindo(ar, "4")
        b.agenda.avancar(200)
        b.nucleo.escreverConferindo("car.hvac.driver_temperature", "21.0")
        b.agenda.avancar(299)

        assertEquals(1, b.comandos.count { it.startsWith("pm disable-user") })
        assertEquals(1, b.agenda.pausas.size)
        assertFalse(b.comandos.any { it.startsWith("pm enable") })

        b.agenda.avancar(1)
        assertEquals(1, b.comandos.count { it.startsWith("pm enable") })

        // Depois de retomado, a próxima escrita do ar suspende de novo.
        b.nucleo.escreverConferindo(ar, "3")
        assertEquals(2, b.comandos.count { it.startsWith("pm disable-user") })
    }

    @Test
    fun `com o app de clima na frente nao suspende`() {
        val canal = b.partirLimpo()
        b.nucleo.appNaFrente = { "com.beantechs.hvac" }

        assertTrue(b.nucleo.escreverConferindo(ar, "2"))
        b.agenda.avancar(1_000)

        assertTrue(b.comandos.isEmpty())
        assertTrue(b.agenda.pausas.isEmpty())
        assertEquals(1, canal.pedidos.size)
        assertEquals("2", b.nucleo.espiar(ar))
    }

    @Test
    fun `quem esta na frente que lanca conta como nao estar`() {
        b.partirLimpo()
        b.nucleo.appNaFrente = { throw IllegalStateException("sem resposta") }

        b.nucleo.escreverConferindo(ar, "2")

        assertEquals(1, b.comandos.count { it.startsWith("pm disable-user") })
    }

    @Test
    fun `publicacao otimista so avisa quando o valor muda`() {
        b.partirLimpo()
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.escreverConferindo(ar, "3") // nunca visto
        b.nucleo.chegouDoCarro(ar, "3") // eco
        ouvinte.recebidos.clear()
        b.nucleo.escreverConferindo(ar, "3") // igual: sem aviso
        b.nucleo.escreverConferindo(ar, "3.0") // texto diferente: avisa
        b.nucleo.escreverConferindo(ar, "4")

        assertEquals(listOf(ar to "3.0", ar to "4"), ouvinte.recebidos.toList())
        assertEquals(3, b.eventosDiario().count { it == "ar.otimista" })
    }

    @Test
    fun `falha no pedido do ar nao grava, agenda a retomada e anota o tipo do erro`() {
        val canal = b.partirLimpo()
        canal.falharEm += "pedir"

        assertFalse(b.nucleo.escreverConferindo(ar, "6"))
        assertNull(b.nucleo.espiar(ar))

        val falha = b.diario.single { it.first == "ar.falha" }.second
        assertEquals("SecurityException", falha["erro"])
        b.agenda.avancar(300)
        assertEquals("pm enable com.beantechs.hvac", b.comandos.last())
    }

    @Test
    fun `ar com canal morto e pulado e anotado`() {
        val canal = b.partirLimpo()
        canal.vivoAgora = false

        assertFalse(b.nucleo.escreverConferindo(ar, "6"))

        assertTrue(b.comandos.isEmpty())
        assertEquals(listOf("ar.pulada"), b.eventosDiario())
    }

    @Test
    fun `diario do ar registra pedido e publicacao otimista com anterior e novo`() {
        b.partirLimpo()
        b.nucleo.chegouDoCarro(ar, "1")

        b.nucleo.escreverConferindo(ar, "2")

        assertTrue("ar.pedido" in b.eventosDiario())
        val otimista = b.diario.single { it.first == "ar.otimista" }.second
        assertEquals("1", otimista["anterior"])
        assertEquals("2", otimista["novo"])
    }

    @Test
    fun `retomada tenta de novo quando o shell nao roda`() {
        b.partirLimpo()
        var falhas = 2
        b.saidaDoShell = { comando -> if (comando[1] == "enable" && falhas-- > 0) "" else "ok" }

        b.nucleo.escreverConferindo(ar, "2")
        b.agenda.avancar(300)
        b.agenda.avancar(2_000)

        assertEquals(3, b.comandos.count { it.startsWith("pm enable") })
        b.agenda.avancar(10_000)
        assertEquals(3, b.comandos.count { it.startsWith("pm enable") })
    }

    @Test
    fun `chave fora do apendice C nao passa pela camada do ar`() {
        b.partirLimpo()

        b.nucleo.escreverConferindo("car.hvac.panel_display_notify", "1")

        assertTrue(b.comandos.isEmpty())
    }

    // ── D.7 simulador ──

    @Test
    fun `no simulador escrever injeta e devolve true sem camada do ar`() {
        b.nucleo.partirSimulado()
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.ganchoDoCarro = gancho
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        assertTrue(b.nucleo.escreverConferindo(ar, "7"))

        assertEquals(listOf(ar to "7"), ouvinte.recebidos.toList())
        assertEquals(listOf(ar to "7"), gancho.recebidos.toList())
        assertEquals("7", b.nucleo.ler(ar))
        assertEquals(3, b.difusor.envios.size)
        assertTrue(b.comandos.isEmpty())
        assertTrue(b.central.abertos.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
