// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.veiculo.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Seção C da especificação `motor-nucleo.md`. */
class LeituraTest {

    private val b = Bancada()

    // ── C.1 ler ──

    @Test
    fun `ler devolve o cache sem ir ao carro`() {
        val canal = b.partirLimpo()
        b.nucleo.chegouDoCarro("car.x", "7")

        assertEquals("7", b.nucleo.ler("car.x"))
        assertEquals(0, canal.buscas.get())
    }

    @Test
    fun `ler na falta pergunta ao carro uma vez e guarda`() {
        val canal = b.partirLimpo()
        canal.valores["car.x"] = "1"

        assertEquals("1", b.nucleo.ler("car.x"))
        canal.valores["car.x"] = "2"
        assertEquals("1", b.nucleo.ler("car.x"))
        assertEquals(1, canal.buscas.get())
        assertEquals("1", b.nucleo.espiar("car.x"))
    }

    @Test
    fun `ler lembra sem valor e nao volta ao carro`() {
        val canal = b.partirLimpo()

        assertNull(b.nucleo.ler("car.nada"))
        assertNull(b.nucleo.ler("car.nada"))
        assertEquals(1, canal.buscas.get())
        assertTrue(b.nucleo.valoresConhecidos().containsKey("car.nada"))
    }

    @Test
    fun `ler com canal morto devolve null sem chamada`() {
        val canal = b.partirLimpo()
        canal.valores["car.x"] = "1"

        canal.vivoAgora = false
        assertNull(b.nucleo.ler("car.x"))
        canal.vivoAgora = true
        b.central.shizuku = false
        assertNull(b.nucleo.ler("car.x"))

        assertEquals(0, canal.buscas.get())
    }

    @Test
    fun `ler antes da partida devolve null`() {
        assertNull(b.nucleo.ler("car.x"))
        assertTrue(b.central.abertos.isEmpty())
    }

    @Test
    fun `ler com excecao devolve null e nao guarda`() {
        val canal = b.partirLimpo()
        canal.valores["car.x"] = "1"
        canal.falharEm += "buscar"

        assertNull(b.nucleo.ler("car.x"))
        assertFalse(b.nucleo.valoresConhecidos().containsKey("car.x"))

        canal.falharEm.clear()
        assertEquals("1", b.nucleo.ler("car.x"))
        assertEquals(2, canal.buscas.get())
    }

    @Test
    fun `ler nao avisa ouvintes nem manda broadcast`() {
        val canal = b.partirLimpo()
        canal.valores["car.x"] = "1"
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        b.nucleo.ler("car.x")

        assertTrue(ouvinte.recebidos.isEmpty())
        assertTrue(b.difusor.envios.isEmpty())
    }

    @Test
    fun `canal morto em ler registra pouco`() {
        b.partirLimpo().vivoAgora = false
        b.registro.avisos.clear()

        repeat(100) { b.nucleo.ler("car.k$it") }

        assertEquals(1, b.registro.avisos.count { "canal morto" in it })
    }

    // ── C.2 lerDoCarro(chave) ──

    @Test
    fun `lerDoCarro sobrescreve o cache inclusive com sem valor e nao avisa`() {
        val canal = b.partirLimpo()
        b.nucleo.chegouDoCarro("car.x", "a")
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        assertNull(b.nucleo.lerDoCarro("car.x"))
        assertNull(b.nucleo.espiar("car.x"))
        assertTrue(b.nucleo.valoresConhecidos().containsKey("car.x"))

        canal.valores["car.x"] = "b"
        assertEquals("b", b.nucleo.lerDoCarro("car.x"))
        assertEquals("b", b.nucleo.espiar("car.x"))
        assertTrue(ouvinte.recebidos.isEmpty())
    }

    @Test
    fun `lerDoCarro com canal morto ou excecao devolve null`() {
        val canal = b.partirLimpo()
        canal.valores["car.x"] = "1"
        canal.falharEm += "buscar"
        assertNull(b.nucleo.lerDoCarro("car.x"))

        canal.falharEm.clear()
        canal.vivoAgora = false
        assertNull(b.nucleo.lerDoCarro("car.x"))
        assertEquals(1, canal.buscas.get())
    }

    // ── C.3 lerDoCarro(chaves) ──

    @Test
    fun `lote devolve na ordem pedida com vazio para sem valor e nao mexe no cache`() {
        val canal = b.partirLimpo()
        canal.valores["car.b"] = "2"
        canal.valores["car.c"] = ""
        val antes = canal.lotes.get() // o retrato da partida

        val lido = b.nucleo.lerDoCarro(arrayOf("car.b", "car.a", "car.b", "car.c"))

        assertEquals(listOf("car.b", "car.a", "car.c"), lido.keys.toList())
        assertEquals(listOf("2", "", ""), lido.values.toList())
        assertEquals(antes + 1, canal.lotes.get())
        assertEquals(listOf("car.b", "car.a", "car.c"), canal.lotesPedidos.last())
        assertFalse(b.nucleo.valoresConhecidos().keys.any { it in setOf("car.a", "car.b", "car.c") })
    }

    @Test
    fun `lote vazio ou com canal morto devolve mapa vazio sem chamada`() {
        val canal = b.partirLimpo()
        val antes = canal.lotes.get()

        assertTrue(b.nucleo.lerDoCarro(emptyArray()).isEmpty())
        canal.vivoAgora = false
        assertTrue(b.nucleo.lerDoCarro(arrayOf("car.a")).isEmpty())
        assertEquals(antes, canal.lotes.get())
    }

    @Test
    fun `lote com excecao devolve mapa vazio`() {
        val canal = b.partirLimpo()
        canal.falharEm += "buscarVarias"

        assertTrue(b.nucleo.lerDoCarro(arrayOf("car.a", "car.b")).isEmpty())
    }

    // ── C.4 lerTudoDoCarro ──

    @Test
    fun `lerTudoDoCarro escreve no cache na ordem do catalogo sem avisar`() {
        b.catalogo = listOf("car.z", "car.a", "car.m")
        val canal = b.partirLimpo()
        canal.valores["car.z"] = "1"
        canal.valores["car.m"] = "3"
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)

        val tudo = b.nucleo.lerTudoDoCarro()

        assertEquals(listOf("car.z", "car.a", "car.m"), tudo.keys.toList())
        assertEquals(listOf("1", "", "3"), tudo.values.toList())
        val cache = b.nucleo.valoresConhecidos()
        assertEquals("1", cache["car.z"])
        assertTrue(cache.containsKey("car.a"))
        assertNull(cache["car.a"])
        assertTrue(ouvinte.recebidos.isEmpty())
        assertTrue(b.difusor.envios.isEmpty())

        // "sem valor" lembrado: ler não volta ao carro.
        assertNull(b.nucleo.ler("car.a"))
        assertEquals(0, canal.buscas.get())
    }

    @Test
    fun `lerTudoDoCarro sobrescreve valor bom com sem valor`() {
        b.catalogo = listOf("car.z")
        b.partirLimpo()
        b.nucleo.chegouDoCarro("car.z", "9")

        b.nucleo.lerTudoDoCarro()

        assertNull(b.nucleo.espiar("car.z"))
    }

    @Test
    fun `lerTudoDoCarro cai para pedacos de 100 quando o lote inteiro falha`() {
        b.catalogo = (1..250).map { "car.k%03d".format(it) }
        val canal = b.partirLimpo()
        b.catalogo.forEach { canal.valores[it] = it.takeLast(3) }
        canal.maiorLoteAceito = 100
        val antes = canal.lotesPedidos.size // o retrato da partida

        val tudo = b.nucleo.lerTudoDoCarro()

        assertEquals(b.catalogo, tudo.keys.toList())
        assertEquals(listOf(250, 100, 100, 50), canal.lotesPedidos.drop(antes).map { it.size })
        assertEquals("250", tudo["car.k250"])
    }

    @Test
    fun `lerTudoDoCarro junta o que vier quando um pedaco falha`() {
        b.catalogo = (1..250).map { "car.k%03d".format(it) }
        val canal = b.partirLimpo()
        b.catalogo.forEach { canal.valores[it] = "v" }
        val antes = canal.lotes.get() // o retrato da partida
        canal.lotesQueFalham += setOf(antes + 1, antes + 3) // o inteiro e o segundo pedaço

        val tudo = b.nucleo.lerTudoDoCarro()

        assertEquals(150, tudo.size)
        assertFalse(tudo.containsKey("car.k150"))
        assertTrue(tudo.containsKey("car.k250"))
        assertEquals(b.catalogo.filter { it in tudo }, tudo.keys.toList())
    }

    @Test
    fun `lerTudoDoCarro com canal morto ou tudo falhando devolve vazio`() {
        val canal = b.partirLimpo()
        canal.falharEm += "buscarVarias"
        assertTrue(b.nucleo.lerTudoDoCarro().isEmpty())

        canal.falharEm.clear()
        canal.vivoAgora = false
        assertTrue(b.nucleo.lerTudoDoCarro().isEmpty())
    }

    // ── C.5 valoresConhecidos ──

    @Test
    fun `valoresConhecidos e uma copia que inclui null`() {
        b.partirLimpo()
        b.nucleo.chegouDoCarro("car.a", "1")
        b.nucleo.ler("car.sem")

        val copia = b.nucleo.valoresConhecidos()
        b.nucleo.chegouDoCarro("car.b", "2")

        assertEquals("1", copia["car.a"])
        assertTrue(copia.containsKey("car.sem"))
        assertNull(copia["car.sem"])
        assertFalse(copia.containsKey("car.b"))
    }

    // ── C.8 reenviarTudo ──

    @Test
    fun `reenviarTudo rele o conjunto num lote e publica so os nao vazios sem gancho`() {
        b.chavesDaPessoa = setOf("car.pessoa")
        val canal = b.partirLimpo()
        b.nucleo.acompanhar(listOf("car.dinamica"))
        canal.valores["car.basic.vehicle_speed"] = "10.0"
        canal.valores["car.basic.engine_speed"] = ""
        canal.valores["car.pessoa"] = "p"
        canal.valores["car.dinamica"] = "d"
        canal.valores["car.fora"] = "x"
        val gancho = OuvinteAnotador(b.eventos, "gancho")
        b.nucleo.ganchoDoCarro = gancho
        val ouvinte = OuvinteAnotador(b.eventos)
        b.nucleo.observar(ouvinte)
        val lotesAntes = canal.lotes.get()

        b.nucleo.reenviarTudo()

        assertEquals(lotesAntes + 1, canal.lotes.get())
        val publicados = ouvinte.recebidos.map { it.first }.toSet()
        assertEquals(setOf("car.basic.vehicle_speed", "car.pessoa", "car.dinamica"), publicados)
        assertTrue(gancho.recebidos.isEmpty())
        assertEquals(b.nucleo.conjuntoAcompanhado, canal.lotesPedidos.last())
    }

    @Test
    fun `reenviarTudo com canal morto nao faz nada`() {
        val canal = b.partirLimpo()
        canal.vivoAgora = false
        val lotes = canal.lotes.get()

        b.nucleo.reenviarTudo()

        assertEquals(lotes, canal.lotes.get())
        assertTrue(b.difusor.envios.isEmpty())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
