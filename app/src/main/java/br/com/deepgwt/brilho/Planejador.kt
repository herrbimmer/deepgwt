// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.brilho

import java.time.Instant
import java.time.ZoneId

/** O que o usuário escolheu, como está nas preferências (sem limites aplicados). */
internal data class Ajustes(
    val usarSol: Boolean,
    val nivelDia: Int,
    val nivelNoite: Int,
    val passoMin: Int,
    val inicioDaNoiteHora: Int,
    val inicioDaNoiteMinuto: Int,
    val fimDaNoiteHora: Int,
    val fimDaNoiteMinuto: Int,
)

/** Uma posição em graus (latitude norte positiva, longitude leste positiva). */
internal data class Posicao(val latitude: Double, val longitude: Double)

internal enum class Modo { HORARIO_FIXO, SOL }

/** O resultado de uma avaliação: o nível a aplicar agora e quando reavaliar (ms desde 1970). */
internal data class Plano(val nivel: Int, val alarmes: List<Long>, val modo: Modo)

/**
 * Decide o nível e os próximos alarmes a partir dos ajustes, da hora e da posição. Sem Android:
 * quem chama lê as preferências e a localização e depois aplica o [Plano].
 *
 * Especificação: `docs/independencia/specs/brilho-automatico.md`.
 */
internal object Planejador {

    const val PASSO_MIN_MINIMO = 1
    const val PASSO_MIN_MAXIMO = 30

    /** No modo sol, o próximo alarme fica pelo menos isto à frente. */
    const val MINIMO_A_FRENTE_MS = 30_000L

    private const val MS_POR_MINUTO = 60_000L

    /** Modo sol quando pedido e possível; senão (sem posição, dia ou noite polar), horário fixo. */
    fun planejar(ajustes: Ajustes, agoraMs: Long, zona: ZoneId, posicao: Posicao?): Plano {
        if (ajustes.usarSol && posicao != null) {
            planejarPeloSol(ajustes, agoraMs, zona, posicao)?.let { return it }
        }
        return planejarPeloHorario(ajustes, agoraMs, zona)
    }

    fun passoMs(ajustes: Ajustes): Long =
        ajustes.passoMin.coerceIn(PASSO_MIN_MINIMO, PASSO_MIN_MAXIMO) * MS_POR_MINUTO

    /** Noite em [início, fim) do relógio local; alarmes no próximo início e no próximo fim. */
    fun planejarPeloHorario(ajustes: Ajustes, agoraMs: Long, zona: ZoneId): Plano {
        val inicio = HorarioFixo.minutoDoDia(ajustes.inicioDaNoiteHora, ajustes.inicioDaNoiteMinuto)
        val fim = HorarioFixo.minutoDoDia(ajustes.fimDaNoiteHora, ajustes.fimDaNoiteMinuto)
        val noite = HorarioFixo.ehNoite(HorarioFixo.minutoAgora(agoraMs, zona), inicio, fim)
        return Plano(
            nivel = if (noite) ajustes.nivelNoite else ajustes.nivelDia,
            alarmes = listOf(
                HorarioFixo.proximaVez(agoraMs, zona, inicio),
                HorarioFixo.proximaVez(agoraMs, zona, fim),
            ),
            modo = Modo.HORARIO_FIXO,
        )
    }

    /** Null quando hoje não há nascer ou pôr do sol nessa posição. */
    fun planejarPeloSol(ajustes: Ajustes, agoraMs: Long, zona: ZoneId, posicao: Posicao): Plano? {
        val hoje = Instant.ofEpochMilli(agoraMs).atZone(zona).toLocalDate()
        val passo = passoMs(ajustes)
        fun diaDeSol(sol: NascerEPor) = DiaDeSol(sol, ajustes.nivelDia, ajustes.nivelNoite, passo)

        val solDeHoje = Sol.nascerEPor(hoje, posicao.latitude, posicao.longitude) ?: return null
        val dia = diaDeSol(solDeHoje)
        val amanha = hoje.plusDays(1)
        val proximo = dia.proximaAvaliacao(
            agoraMs = agoraMs,
            amanha = { Sol.nascerEPor(amanha, posicao.latitude, posicao.longitude)?.let(::diaDeSol) },
            inicioDeAmanhaMs = amanha.atStartOfDay(zona).toInstant().toEpochMilli(),
            minimoMs = MINIMO_A_FRENTE_MS,
        )
        return Plano(nivel = dia.nivel(agoraMs), alarmes = listOf(proximo), modo = Modo.SOL)
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
