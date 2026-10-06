// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.telas

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A faixa da barra inferior é descontada UMA vez ao devolver um app ao display 0 (Alpha 6.6).
 * Ver [FaixaDaBarra.fundoCruDoDisplay0].
 */
class FaixaDaBarraTest {

    private val altura = 720

    /** 140dp + 10px numa tela de densidade 1. */
    private val faixaMaxima = 150

    /** O que a restauração faz: volta ao fundo cru e desconta a barra uma vez. */
    private fun restaurar(fundoSalvo: Int, barra: Int): Int =
        FaixaDaBarra.fundoCruDoDisplay0(fundoSalvo, altura, faixaMaxima) - barra

    // ── Alpha 7.6: a guarda da tarja preta olha o resultado ─────────────────────────────────

    private fun corrigir(limites: IntArray, esperado: Int = 660) =
        FaixaDaBarra.fundoCorrigidoContraTarjaPreta(limites, 1920, altura, esperado)

    @Test
    fun `app de largura inteira que termina 3x acima da barra volta a terminar nela`() {
        // A foto da 7.6: tarja de ~3x a barra de 60 acima dela.
        assertEquals(660, corrigir(intArrayOf(0, 0, 1920, 540)))
        assertEquals(660, corrigir(intArrayOf(0, 0, 1920, 600)))
    }

    @Test
    fun `app que ja termina na barra ou atras dela fica como esta`() {
        assertEquals(null, corrigir(intArrayOf(0, 0, 1920, 660)))
        assertEquals(null, corrigir(intArrayOf(0, 0, 1920, 655)))
        assertEquals(null, corrigir(intArrayOf(0, 0, 1920, 720)))
    }

    @Test
    fun `slots do launcher e janelas flutuantes nao sao tela cheia e ficam de fora`() {
        // Slot esquerdo do launcher: começa depois do painel lateral e não vai até o topo.
        assertEquals(null, corrigir(intArrayOf(176, 100, 760, 496)))
        // Largura inteira mas não começa no topo.
        assertEquals(null, corrigir(intArrayOf(0, 60, 1920, 540)))
        // Não ocupa a largura inteira.
        assertEquals(null, corrigir(intArrayOf(0, 0, 1280, 540)))
    }

    @Test
    fun `janela baixa de proposito nao e tarja`() {
        assertEquals(null, corrigir(intArrayOf(0, 0, 1920, 300)))
    }

    @Test
    fun `barra desligada ou valor esquisito nao corrige`() {
        assertEquals(null, corrigir(intArrayOf(0, 0, 1920, 540), esperado = 0))
        assertEquals(null, corrigir(intArrayOf(0, 0, 1920, 540), esperado = 900))
        assertEquals(null, FaixaDaBarra.fundoCorrigidoContraTarjaPreta(intArrayOf(0, 0), 1920, altura, 660))
    }

    @Test
    fun `ir ao painel e voltar varias vezes nao acumula a faixa`() {
        val barra = 60
        // Primeira volta: o salvo é o que o próprio app aplicou antes, já sem a barra.
        var fundo = restaurar(altura - barra, barra)
        assertEquals(660, fundo)
        // Cada nova ida e volta parte do fundo aplicado na anterior. Antes: 600, depois 540.
        repeat(5) { fundo = restaurar(fundo, barra) }
        assertEquals(660, fundo)
    }

    @Test
    fun `a barra mais alta ainda cabe na faixa`() {
        val barra = 140
        assertEquals(altura - barra, restaurar(altura - barra, barra))
    }

    @Test
    fun `fundo cheio ou quase cheio continua valendo tela cheia`() {
        assertEquals(altura, FaixaDaBarra.fundoCruDoDisplay0(altura, altura, faixaMaxima))
        assertEquals(altura, FaixaDaBarra.fundoCruDoDisplay0(712, altura, faixaMaxima))
    }

    @Test
    fun `um app de fato menor que a tela mantem o proprio tamanho`() {
        assertEquals(400, FaixaDaBarra.fundoCruDoDisplay0(400, altura, faixaMaxima))
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
