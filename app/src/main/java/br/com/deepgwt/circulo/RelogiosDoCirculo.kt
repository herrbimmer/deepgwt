// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.circulo

/**
 * Os relógios analógicos da página Relógio do círculo (Alpha 7.7).
 *
 * Só o catálogo e a conta dos ponteiros — Kotlin puro, testável. O desenho de cada um mora em
 * [RelogioAnalogicoView], escolhido pelo [Relogio.id]. A ordem desta lista é a ordem em que ←/→
 * do volante passam por eles; a escolha é gravada pelo `id`, então reordenar ou acrescentar um
 * relógio não troca o de ninguém.
 */
object RelogiosDoCirculo {

    /** [continuo] = ponteiro de segundos varrendo (automático); falso = um salto por segundo. */
    data class Relogio(val id: String, val nome: String, val continuo: Boolean = false)

    val TODOS: List<Relogio> =
        listOf(
            Relogio("classico", "Clássico"),
            Relogio("cronografo", "Cronógrafo"),
            Relogio("mergulhador", "Mergulhador", continuo = true),
            Relogio("aviador", "Aviador"),
            Relogio("bauhaus", "Bauhaus"),
            Relogio("minimalista", "Minimalista"),
            Relogio("luxo", "Luxo", continuo = true),
            Relogio("neon", "Neon"),
            Relogio("rally", "Rally"),
            Relogio("noturno", "Noturno"),
            // Alpha 7.8: os de leitura rápida — mostrador limpo, ponteiros retos, um acento de cor.
            Relogio("estacao", "Estação"),
            Relogio("grafite", "Grafite"),
            Relogio("marinho", "Marinho"),
            Relogio("areia", "Areia")
        )

    val QUANTOS: Int
        get() = TODOS.size

    /** Índice do relógio gravado; id desconhecido ou ausente = o primeiro. */
    fun indiceDe(id: String?): Int = TODOS.indexOfFirst { it.id == id }.coerceAtLeast(0)

    fun doIndice(i: Int): Relogio = TODOS[((i % QUANTOS) + QUANTOS) % QUANTOS]

    /** Ângulos dos ponteiros em graus, no sentido horário a partir das 12. */
    data class Ponteiros(val hora: Float, val minuto: Float, val segundo: Float)

    private const val DIA_MS = 86_400_000L

    /**
     * [msDoDia] é a hora LOCAL em ms desde a meia-noite. Com [continuo] falso tudo anda em saltos
     * de um segundo, como um quartzo: o minuto e a hora avançam junto com o salto dos segundos.
     */
    fun ponteiros(msDoDia: Long, continuo: Boolean): Ponteiros {
        val dia = ((msDoDia % DIA_MS) + DIA_MS) % DIA_MS
        val ms = if (continuo) dia.toDouble() else (dia / 1_000L * 1_000L).toDouble()
        val segundos = ms / 1_000.0
        return Ponteiros(
            hora = ((segundos / 120.0) % 360.0).toFloat(),
            minuto = ((segundos / 10.0) % 360.0).toFloat(),
            segundo = ((segundos * 6.0) % 360.0).toFloat()
        )
    }

    /** Ponteiro de 24 h do submostrador (uma volta por dia). */
    fun ponteiro24h(msDoDia: Long): Float {
        val dia = ((msDoDia % DIA_MS) + DIA_MS) % DIA_MS
        return (dia / 1_000L).toFloat() / 240f
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
