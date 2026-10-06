// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.painel

import br.com.deepgwt.ponte.Cancelavel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V6 e V7 de `projetores-painel.md`. */
class SeloDeAvisosTest {

    @Test
    fun v6_valorAtivo() {
        for (v in listOf("1", "1121", "{1,0,0,0,0}", "{0,0}", " 1 ")) assertTrue("v=$v", AvisosDoCarro.ativo(v))
        for (v in listOf("0", " 0 ", "{0,0,0,0}", "{0,0,0,0,0}", "false", "FALSE", "", "null", "undefined", "unknown", "--", "NaN", null)) {
            assertFalse("v=$v", AvisosDoCarro.ativo(v))
        }
    }

    @Test
    fun listasDeChaves() {
        assertEquals(21, AvisosDoCarro.VIGIADAS.size)
        assertEquals(7, AvisosDoCarro.MOMENTANEAS.size)
        assertEquals(10, AvisosDoCarro.COM_SELO.size)
        assertTrue("car.ipk_light.fuel_low" in AvisosDoCarro.COM_SELO)
        assertFalse("car.basic.tirepress_warning" in AvisosDoCarro.COM_SELO)
    }

    /** Relógio e esperas falsos, com a deduplicação de E.2 na frente do selo. */
    private class Cenario {
        var agora = 0L
        private val esperas = mutableListOf<Pair<Long, () -> Unit>>()
        val envios = mutableListOf<Pair<Boolean, Boolean>>()
        val mudancas = mutableListOf<String>()
        var popUps = 0
        private val ultimos = HashMap<String, String>()
        val cache = HashMap<String, String>()

        val selo = SeloDeAvisos(
            agora = { agora },
            esperar = { ms, tarefa ->
                val item = (agora + ms) to tarefa
                esperas.add(item)
                Cancelavel { esperas.remove(item) }
            },
            lerDoCache = { cache[it] },
            saida = object : SeloDeAvisos.Saida {
                override fun popUp() {
                    popUps++
                }

                override fun mudou(ativo: Boolean, dispensado: Boolean, motivo: String) {
                    mudancas.add("$ativo/$dispensado/$motivo")
                }

                override fun enviar(ativo: Boolean, dispensado: Boolean) {
                    envios.add(ativo to dispensado)
                }
            },
        )

        fun ate(segundos: Double) {
            val alvo = (segundos * 1000).toLong()
            while (true) {
                val proxima = esperas.filter { it.first <= alvo }.minByOrNull { it.first } ?: break
                esperas.remove(proxima)
                agora = proxima.first
                proxima.second()
            }
            agora = alvo
        }

        fun valor(segundos: Double, chave: String, valor: String) {
            ate(segundos)
            if (ultimos.put(chave, valor) == valor) return
            selo.valorNovo(chave, valor)
        }

        fun voltar(segundos: Double) {
            ate(segundos)
            selo.dispensar()
        }

        fun ultimoEnvio(): Pair<Boolean, Boolean>? = envios.lastOrNull()
    }

    @Test
    fun v7_linhaDoTempo() {
        val c = Cenario()
        val cinto = "car.basic.seat_belt_warning"
        val porta = "car.ipk_light.door_warning"

        c.valor(0.0, cinto, "{1,0,0,0,0}")
        assertEquals(true to false, c.ultimoEnvio())
        assertEquals(1, c.popUps)

        var antes = c.envios.size
        c.voltar(1.0)
        assertEquals("BACK cedo não muda nada", antes, c.envios.size)

        c.voltar(3.0)
        assertEquals(false to true, c.ultimoEnvio())
        assertTrue(c.selo.avisoDispensado)

        antes = c.envios.size
        c.valor(3.5, cinto, "{1,0,0,0,0}")
        assertEquals("repetido é deduplicado", antes, c.envios.size)

        c.valor(5.0, porta, "1")
        assertEquals(true to false, c.ultimoEnvio())
        assertEquals(2, c.popUps)

        c.valor(6.0, porta, "0")
        assertEquals(true to false, c.ultimoEnvio())

        c.voltar(9.0)
        assertEquals(false to true, c.ultimoEnvio())

        antes = c.envios.size
        c.valor(10.0, cinto, "{0,0,0,0,0}")
        assertEquals(antes + 1, c.envios.size)
        assertEquals(false to false, c.ultimoEnvio())

        antes = c.envios.size
        c.valor(20.0, "car.basic.tirepress_warning", "{1,0,0,0}")
        assertEquals(antes + 1, c.envios.size)
        assertEquals(false to false, c.ultimoEnvio())
        assertEquals("sem selo não abre pop-up", 2, c.popUps)

        antes = c.envios.size
        c.valor(21.0, "car.ipk_info.bsd_lca_warning_reqleft", "1")
        assertEquals(antes + 1, c.envios.size)
        assertEquals(false to false, c.ultimoEnvio())
    }

    @Test
    fun apagarSozinhoEsperaDoisSegundos() {
        val c = Cenario()
        val porta = "car.ipk_light.door_warning"
        c.valor(0.0, porta, "1")
        assertEquals(true to false, c.ultimoEnvio())
        val antes = c.envios.size
        c.valor(4.0, porta, "0")
        assertEquals("durante a espera nada sai", antes, c.envios.size)
        c.ate(5.9)
        assertTrue(c.selo.avisoAtivo)
        c.ate(6.0)
        assertFalse(c.selo.avisoAtivo)
        assertEquals(false to false, c.ultimoEnvio())
    }

    @Test
    fun avisoNovoCancelaAEspera() {
        val c = Cenario()
        val porta = "car.ipk_light.door_warning"
        c.valor(0.0, porta, "1")
        c.valor(4.0, porta, "0")
        c.valor(5.0, "car.ipk_light.fuel_low", "1")
        assertEquals(true to false, c.ultimoEnvio())
        c.ate(10.0)
        assertTrue(c.selo.avisoAtivo)
    }

    @Test
    fun semearUsaOCacheEPulaMomentaneas() {
        val c = Cenario()
        c.cache["car.basic.seat_belt_warning"] = "{1,0,0,0,0}"
        c.cache["car.ipk_info.fcw_warning"] = "1"
        c.selo.semear()
        assertEquals(1, c.popUps)
        c.selo.recalcular("carga")
        assertEquals(true to false, c.ultimoEnvio())
        assertEquals(listOf("car.basic.seat_belt_warning"), c.selo.aMostra())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
