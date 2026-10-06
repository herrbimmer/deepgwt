// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import br.com.deepgwt.veiculo.Veiculo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seções F e G da especificação `motor-nucleo.md`. */
class PublicacaoTest {

    private val b = Bancada()

    // ── F.1 ──

    @Test
    fun `ordem exata broadcasts, ouvintes, cache e gancho`() {
        b.partirLimpo()
        b.nucleo.chegouDoCarro("car.k", "velho")
        b.eventos.clear()
        b.difusor.envios.clear()
        val ouvinte = OuvinteAnotador(b.eventos, dentro = { chave, _ -> b.eventos += "dentro:${b.nucleo.ler(chave)}" })
        b.nucleo.observar(ouvinte)
        b.nucleo.ganchoDoCarro = Veiculo.Ouvinte { chave, _ -> b.eventos += "gancho:${b.nucleo.ler(chave)}" }

        b.nucleo.chegouDoCarro("car.k", "novo")

        assertEquals(
            listOf(
                "broadcast:android.intent.haval.car.k",
                "broadcast:android.intent.haval.car.k_novo",
                "broadcast:com.haval.vehicle.EVENT_CHANGED",
                "ouvinte:car.k=novo",
                "dentro:velho", // G.4: dentro do ouvinte o cache ainda tem o anterior
                "gancho:novo",
            ),
            b.eventos.toList(),
        )
        assertEquals("novo", b.nucleo.espiar("car.k"))
    }

    @Test
    fun `broadcasts levam os extras e a restricao certos`() {
        b.nucleo.publicar("car.k", "1")

        val (porChave, porValor, geral) = b.difusor.envios
        assertEquals(DifusorFalso.Envio("android.intent.haval.car.k", "car.k", "1", soDoApp = true), porChave)
        assertEquals(DifusorFalso.Envio("android.intent.haval.car.k_1", null, null, soDoApp = true), porValor)
        assertEquals(DifusorFalso.Envio("com.haval.vehicle.EVENT_CHANGED", "car.k", "1", soDoApp = false), geral)
    }

    @Test
    fun `chave ou valor nulos sao ignorados por inteiro`() {
        b.partirLimpo()
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.ganchoDoCarro = gancho

        b.nucleo.chegouDoCarro("car.k", null)
        b.nucleo.chegouDoCarro(null, "1")

        assertTrue(b.eventos.isEmpty())
        assertTrue(b.nucleo.valoresConhecidos().isEmpty())
    }

    @Test
    fun `texto vazio do carro e publicado`() {
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.chegouDoCarro("car.k", "")

        assertEquals(listOf("car.k" to ""), ouvinte.recebidos.toList())
        assertEquals("", b.nucleo.espiar("car.k"))
    }

    @Test
    fun `sem filtro de repeticao`() {
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.chegouDoCarro("car.k", "1")
        b.nucleo.chegouDoCarro("car.k", "1")

        assertEquals(2, ouvinte.recebidos.size)
        assertEquals(6, b.difusor.envios.size)
    }

    @Test
    fun `gancho so para eventos do carro e so depois da partida`() {
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.ganchoDoCarro = gancho

        b.nucleo.chegouDoCarro("car.antes", "1")
        b.partirLimpo()
        b.nucleo.publicar("haval.power.flow", "2")
        b.nucleo.chegouDoCarro("car.depois", "3")
        b.nucleo.injetar("car.injetado", "4")

        assertEquals(listOf("car.depois" to "3", "car.injetado" to "4"), gancho.recebidos.toList())

        b.nucleo.ganchoDoCarro = null
        b.nucleo.chegouDoCarro("car.sem.gancho", "5")
        assertEquals(2, gancho.recebidos.size)
    }

    @Test
    fun `publicar e para chaves do app e nao vai ao carro`() {
        val canal = b.partirLimpo()
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.publicar("haval.power.flow", "3")

        assertEquals("3", b.nucleo.ler("haval.power.flow"))
        assertEquals(listOf("haval.power.flow" to "3"), ouvinte.recebidos.toList())
        assertTrue(canal.pedidos.isEmpty())
        assertEquals(0, canal.buscas.get())
        assertNull(b.nucleo.lerDoCarro("haval.power.flow")) // a central não conhece
    }

    @Test
    fun `excecao do gancho e engolida`() {
        b.partirLimpo()
        b.nucleo.ganchoDoCarro = Veiculo.Ouvinte { _, _ -> throw IllegalStateException("x") }

        b.nucleo.chegouDoCarro("car.k", "1")

        assertEquals("1", b.nucleo.espiar("car.k"))
    }

    @Test
    fun `falha de broadcast nao impede ouvintes nem cache`() {
        b.difusor.falhar = true
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.chegouDoCarro("car.k", "1")

        assertEquals(1, ouvinte.recebidos.size)
        assertEquals("1", b.nucleo.espiar("car.k"))
    }

    // ── G ──

    @Test
    fun `observar repetido nao duplica e tirar desconhecido nao faz nada`() {
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)
        b.nucleo.observar(ouvinte)
        b.nucleo.pararDeObservar(OuvinteAnotador(b.eventos, "outro"))

        b.nucleo.chegouDoCarro("car.k", "1")
        assertEquals(1, ouvinte.recebidos.size)

        b.nucleo.pararDeObservar(ouvinte)
        b.nucleo.chegouDoCarro("car.k", "2")
        assertEquals(1, ouvinte.recebidos.size)
    }

    @Test
    fun `ordem de registro e excecao de um nao para os outros`() {
        val primeiro = OuvinteAnotador(b.eventos, "primeiro")
        val terceiro = OuvinteAnotador(b.eventos, "terceiro")
        b.nucleo.observar(primeiro)
        b.nucleo.observar { _, _ -> throw IllegalStateException("quebrado") }
        b.nucleo.observar(terceiro)

        b.nucleo.chegouDoCarro("car.k", "1")

        assertEquals(listOf("primeiro:car.k=1", "terceiro:car.k=1"), b.eventos.filter { it.contains("car.k=") })
        assertTrue(b.registro.erros.any { "ouvinte" in it })
    }

    @Test
    fun `cada publicacao usa uma foto da lista`() {
        val tardio = OuvinteAnotador(b.eventos, "tardio")
        val quemTira = object : Veiculo.Ouvinte {
            override fun mudou(chave: String, valor: String?) {
                b.nucleo.observar(tardio)
            }
        }
        b.nucleo.observar(quemTira)

        b.nucleo.chegouDoCarro("car.k", "1")
        assertTrue(tardio.recebidos.isEmpty())

        b.nucleo.chegouDoCarro("car.k", "2")
        assertEquals(listOf("car.k" to "2"), tardio.recebidos.toList())
    }

    @Test
    fun `ouvinte que sai no meio ainda recebe a publicacao em curso`() {
        val segundo = OuvinteAnotador(b.eventos, "segundo")
        b.nucleo.observar { _, _ -> b.nucleo.pararDeObservar(segundo) }
        b.nucleo.observar(segundo)

        b.nucleo.chegouDoCarro("car.k", "1")
        b.nucleo.chegouDoCarro("car.k", "2")

        assertEquals(listOf("car.k" to "1"), segundo.recebidos.toList())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
