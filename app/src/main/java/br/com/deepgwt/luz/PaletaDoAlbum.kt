// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.luz

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * As contas da onda do álbum (casos 72–77): paleta a partir das 4 cores da capa, cor estática,
 * modo nativo DMX, intervalo por velocidade e a cor de cada passo da animação.
 */
internal object PaletaDoAlbum {

    /** Quando a capa não rende 3 cores distintas (ou a paleta vem vazia). */
    val RESERVA: List<CorLed> = listOf(
        CorLed(66, 227, 255),
        CorLed(118, 96, 255),
        CorLed(255, 110, 64),
        CorLed(32, 180, 120),
    )

    fun montar(cores: CoresDaCapa): List<CorLed> = montar(
        ContasDeCor.deArgb(cores.principal),
        ContasDeCor.deArgb(cores.secundaria),
        ContasDeCor.deArgb(cores.destaque),
        ContasDeCor.deArgb(cores.escura),
    )

    /** P, P→D, D, D→S, S, S→E, E→P; cada uma realçada; repetidas (canal ÷ 8) saem. */
    fun montar(p: CorLed, s: CorLed, d: CorLed, e: CorLed): List<CorLed> {
        val sequencia = listOf(
            p,
            ContasDeCor.mistura(p, d, 0.45),
            d,
            ContasDeCor.mistura(d, s, 0.50),
            s,
            ContasDeCor.mistura(s, e, 0.30),
            ContasDeCor.mistura(e, p, 0.40),
        )
        val vistas = HashSet<Triple<Int, Int, Int>>()
        val paleta = ArrayList<CorLed>()
        for (c in sequencia) {
            val realcada = ContasDeCor.realce(c)
            if (vistas.add(Triple(realcada.r / 8, realcada.g / 8, realcada.b / 8))) paleta += realcada
        }
        return if (paleta.size < 3) RESERVA else paleta
    }

    /** A 1ª cor da paleta, garantida visível. */
    fun corEstatica(paleta: List<CorLed>): CorLed = ContasDeCor.visivel(paleta.firstOrNull() ?: Cores.APAGADO)

    /** O modo nativo DMX para este efeito e esta cor; null = o efeito não tem modo nativo. */
    fun modoNativo(efeito: EfeitoDoAlbum, cor: CorLed): Int? {
        if (!efeito.temModoNativo) return null
        val familia = FamiliaDeCor.maisProxima(cor)
        val par = when (efeito.animacao) {
            AnimacaoDoAlbum.SEIS_CORES -> familia.seisCores
            AnimacaoDoAlbum.CORRIDA -> familia.corrida
            AnimacaoDoAlbum.FLUXO -> familia.fluxo
            else -> return null
        }
        return if (efeito.sentido >= 0) par.first else par.second
    }

    /** 520 ms na velocidade 1 até 110 ms na 100. */
    fun intervaloMs(velocidade: Int): Long {
        val v = velocidade.coerceIn(1, 100)
        return ContasDeCor.arredondar(520.0 - 410.0 * (v - 1) / 99.0).toLong()
    }

    /** Assinatura da mídia: as partes não vazias unidas por `|`. */
    fun assinatura(partes: List<String?>): String = partes.filter { !it.isNullOrEmpty() }.joinToString("|")

    /** A cor do passo [passo] do efeito (caso 76). */
    fun corAnimada(efeito: EfeitoDoAlbum, paleta: List<CorLed>, passo: Int): CorLed {
        val p = paleta.ifEmpty { RESERVA }
        val n = passo.toDouble()
        val d = if (efeito.sentido >= 0) 1.0 else -1.0
        return when (efeito.animacao) {
            AnimacaoDoAlbum.ONDA -> onda(p, n, d, 0.38)
            AnimacaoDoAlbum.FLUXO -> onda(p, n, d, 0.30)
            AnimacaoDoAlbum.PULSO -> pulso(p, n)
            AnimacaoDoAlbum.SEIS_CORES -> seisCores(p, efeito.ancora ?: AncoraDoAlbum.AZUL, n, d)
            AnimacaoDoAlbum.CORRIDA -> corrida(p, n, d)
        }
    }

    private fun onda(p: List<CorLed>, n: Double, d: Double, passoDeFase: Double): CorLed {
        val a = suave(seno01(0.72 * n))
        if (p.size == 1) return ContasDeCor.visivel(ContasDeCor.vezes(p[0], 0.62 + 0.48 * a))
        val fase = n * passoDeFase * d
        val rastro = amostra(p, fase - 0.95)
        val corpo = amostra(p, fase)
        val crista = amostra(p, fase + 0.28)
        val cor = ContasDeCor.mistura(
            ContasDeCor.mistura(ContasDeCor.vezes(rastro, 0.55), corpo, 0.68),
            crista,
            0.18 + 0.28 * a,
        )
        return ContasDeCor.visivel(ContasDeCor.vezes(cor, 0.62 + 0.48 * a))
    }

    private fun pulso(p: List<CorLed>, n: Double): CorLed {
        val base = p[0]
        val acento = if (p.size == 1) base else amostra(p, 0.18 * n + 1)
        val a = suave(seno01(0.82 * n))
        val cor = ContasDeCor.mistura(base, acento, 0.18 + 0.44 * a)
        return ContasDeCor.visivel(ContasDeCor.vezes(cor, 0.44 + 0.70 * a))
    }

    private fun seisCores(p: List<CorLed>, ancora: AncoraDoAlbum, n: Double, d: Double): CorLed {
        var inicio = 0
        for (i in p.indices) if (pontuacao(p[i], ancora) > pontuacao(p[inicio], ancora)) inicio = i
        val girada = p.drop(inicio) + p.take(inicio)
        val q = sequenciaDeSeis(girada)
        val fase = modPositivo(0.72 * n * d, 6.0)
        val i = floor(fase).toInt()
        val f = suave(fase - i)
        val cor = ContasDeCor.mistura(q[i % 6], q[(i + 1) % 6], 0.74 * f)
        return ContasDeCor.visivel(ContasDeCor.vezes(cor, 0.66 + 0.46 * suave(seno01(0.92 * n))))
    }

    private fun corrida(p: List<CorLed>, n: Double, d: Double): CorLed {
        val q = sequenciaDeSeis(p)
        val fase = modPositivo(n * d, 6.0)
        val i = floor(fase).toInt()
        val f = suave(fase - i)
        val corredor = ContasDeCor.mistura(q[i % 6], q[(i + 1) % 6], f)
        val cauda = ContasDeCor.vezes(q[Math.floorMod(i - 1, 6)], 0.28)
        val cor = ContasDeCor.mistura(cauda, corredor, 0.82)
        return ContasDeCor.visivel(ContasDeCor.vezes(cor, 0.72 + 0.44 * suave(seno01(1.18 * n))))
    }

    /** Qk = amostra(k·N/6), k = 0..5. */
    private fun sequenciaDeSeis(p: List<CorLed>): List<CorLed> = (0 until 6).map { k -> amostra(p, k * p.size / 6.0) }

    private fun pontuacao(c: CorLed, ancora: AncoraDoAlbum): Int = when (ancora) {
        AncoraDoAlbum.AZUL -> 2 * c.b - c.r - c.g
        AncoraDoAlbum.VERDE -> 2 * c.g - c.r - c.b
        AncoraDoAlbum.CIANO -> c.g + c.b - c.r - abs(c.g - c.b)
    }

    /** A paleta na posição [x] (circular): mistura da cor ⌊x⌋ com a seguinte, com t suavizado. */
    private fun amostra(p: List<CorLed>, x: Double): CorLed {
        val tamanho = p.size
        var pos = modPositivo(x, tamanho.toDouble())
        var i = floor(pos).toInt()
        if (i >= tamanho) {
            i = 0
            pos = 0.0
        }
        return ContasDeCor.mistura(p[i], p[(i + 1) % tamanho], suave(pos - i))
    }

    private fun suave(t: Double): Double = t * t * (3 - 2 * t)

    private fun seno01(x: Double): Double = (sin(x) + 1) / 2

    private fun modPositivo(x: Double, m: Double): Double {
        val r = x % m
        val pos = if (r < 0) r + m else r
        return if (pos >= m) 0.0 else pos
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
