// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A sequência do mudo do Android Auto (caso 59) com relógio falso. */
class MudoDoAndroidAutoTest {

    private val v = MudoDoAndroidAuto

    private class CarroFalso : MudoDoAndroidAuto.Carro {
        val valores = HashMap<String, String>()
        val escritas = ArrayList<Pair<String, String>>()
        /** O carro ignora o pedido de mudo (para ver a reafirmação). */
        var ignorarMudo = false
        override fun lerDoCarro(chave: String) = valores[chave]
        override fun lerDoCache(chave: String): String? = null
        override fun escrever(chave: String, valor: String) {
            escritas += chave to valor
            if (ignorarMudo && (chave == MudoDoAndroidAuto.MUTE_STATE || chave == MudoDoAndroidAuto.MEDIA_MUTE_STATE || chave == MudoDoAndroidAuto.MEDIA_VOLUME)) return
            valores[chave] = valor
        }
    }

    private val agenda = AgendaDeTeste()
    private val carro = CarroFalso()
    private var visual = false
    private val publicados = ArrayList<Boolean>()
    private val mudo = MudoDoAndroidAuto(
        carro,
        agendar = { atraso, tarefa -> agenda.postarComAtraso(atraso, Runnable(tarefa)) },
        publicar = {
            publicados += it
            visual = it
        },
        mudoVisual = { visual },
    )

    @Test
    fun `mutar guarda o volume, zera e confirma em 450 ms`() {
        carro.valores[v.MEDIA_VOLUME] = "17"
        carro.valores[v.MEDIA_VOLUME_RANGE] = "{0,39}"
        assertTrue(mudo.alternar())
        agenda.avancar(0)
        assertEquals(listOf(v.MEDIA_MUTE_STATE to "1", v.MUTE_STATE to "1", v.MEDIA_VOLUME to "0"), carro.escritas)
        assertTrue(publicados.isEmpty())
        agenda.avancar(450)
        assertEquals(listOf(true), publicados)
        // O carro obedeceu: nada de "alternar" pelo mute_adjust_action.
        assertFalse(carro.escritas.any { it.first == v.MUTE_ADJUST_ACTION })
        // Desmutar restaura os 17 guardados.
        carro.escritas.clear()
        mudo.alternar()
        agenda.avancar(0)
        assertEquals(listOf(v.MEDIA_MUTE_STATE to "0", v.MUTE_STATE to "0", v.MEDIA_VOLUME to "17"), carro.escritas)
    }

    @Test
    fun `carro que nao obedece recebe o alternar e a reafirmacao`() {
        carro.valores[v.MEDIA_VOLUME] = "5"
        carro.valores[v.MUTE_STATE] = "0"
        carro.ignorarMudo = true
        mudo.alternar()
        agenda.avancar(450)
        assertTrue(carro.escritas.contains(v.MUTE_ADJUST_ACTION to "2"))
        assertEquals(listOf(true), publicados)
        carro.escritas.clear()
        agenda.avancar(3_800 - 450)
        // Leu "não mudo" → publica o lido, reescreve e volta ao alvo.
        assertEquals(listOf(true, false, true), publicados)
        assertEquals(listOf(v.MEDIA_MUTE_STATE to "1", v.MUTE_STATE to "1", v.MEDIA_VOLUME to "0"), carro.escritas)
        agenda.avancar(7_200 - 3_800)
        assertEquals(5, publicados.size)
    }

    @Test
    fun `um pedido novo cancela a reafirmacao do anterior`() {
        carro.valores[v.MEDIA_VOLUME] = "5"
        mudo.alternar()
        agenda.avancar(500)
        mudo.alternar()
        agenda.avancar(10_000)
        // 2 confirmações (450 ms) + só as 2 reafirmações do segundo pedido.
        assertEquals(4, publicados.size)
        assertFalse(visual)
    }

    @Test
    fun `desmutar sem volume guardado usa 8 e respeita a faixa do carro`() {
        visual = true
        carro.valores[v.MEDIA_VOLUME] = "0"
        carro.valores[v.MEDIA_VOLUME_RANGE] = "{0,5}"
        mudo.alternar()
        agenda.avancar(0)
        assertEquals(v.MEDIA_VOLUME to "5", carro.escritas.last())
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
