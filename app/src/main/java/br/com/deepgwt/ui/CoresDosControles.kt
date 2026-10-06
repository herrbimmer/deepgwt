// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.ui.graphics.Color

/**
 * Os seis jeitos de colorir o interruptor que as telas usam hoje (caso 52). Unificar é decisão do
 * dono (D6); até lá cada tela escolhe o seu.
 */
enum class EstiloDoInterruptor {
    /** Ajustes agrupados: sem borda, com as cores de desabilitado próprias. */
    AJUSTE,

    /** Ligado e desligado da paleta; borda e desabilitado do Material. */
    QUATRO_CORES,

    /** Bolinha clara (texto principal/secundário), sem borda. */
    CLARO,

    /** Só o ligado da paleta. */
    SO_LIGADO,

    /** Bolinha branca e trilho de destaque no ligado; o resto do Material. */
    LIGADO_BRANCO,

    /** Cores clássicas, sem borda. */
    CLASSICO,
}

/** Os quatro jeitos de colorir o controle deslizante (caso 53). */
enum class EstiloDoControleDeslizante {
    DESTAQUE,

    /** Desabilitado todo em trilho desligado. */
    DESTAQUE_APAGADO,

    BOLINHA_BRANCA,

    /** Ajustes agrupados: bolinha branca, sem marcas, igual habilitado ou não. */
    AJUSTE,
}

/**
 * As cores que um estilo de interruptor passa ao Material. [Color.Unspecified] = não passa (fica
 * a padrão do Material, que vem do esquema de reserva). Os ícones nunca são passados.
 */
data class EscolhaDoInterruptor(
    val bolinhaLigado: Color = Color.Unspecified,
    val trilhoLigado: Color = Color.Unspecified,
    val bordaLigado: Color = Color.Unspecified,
    val bolinhaDesligado: Color = Color.Unspecified,
    val trilhoDesligado: Color = Color.Unspecified,
    val bordaDesligado: Color = Color.Unspecified,
    val bolinhaLigadoDesabilitado: Color = Color.Unspecified,
    val trilhoLigadoDesabilitado: Color = Color.Unspecified,
    val bolinhaDesligadoDesabilitado: Color = Color.Unspecified,
    val trilhoDesligadoDesabilitado: Color = Color.Unspecified,
)

/** O mesmo para o controle deslizante. [Color.Unspecified] = padrão do Material. */
data class EscolhaDoControleDeslizante(
    val bolinha: Color = Color.Unspecified,
    val trilhoAtivo: Color = Color.Unspecified,
    val trilhoInativo: Color = Color.Unspecified,
    val marcaAtiva: Color = Color.Unspecified,
    val marcaInativa: Color = Color.Unspecified,
    val bolinhaDesabilitada: Color = Color.Unspecified,
    val trilhoAtivoDesabilitado: Color = Color.Unspecified,
    val trilhoInativoDesabilitado: Color = Color.Unspecified,
)

/**
 * Parte pura da escolha de cores dos controles: estilo + paleta → cores. O `@Composable` que monta
 * o `SwitchColors`/`SliderColors` só repassa (ver `coresDoInterruptor` e
 * `coresDoControleDeslizante`).
 */
object EscolhaDeCores {

    /** "Com alfa 0,5": a mesma cor com o alfa SUBSTITUÍDO por 0,5. */
    internal fun comMeioAlfa(cor: Color): Color = cor.copy(alpha = 0.5f)

    fun doInterruptor(estilo: EstiloDoInterruptor, paleta: PaletaDaInterface): EscolhaDoInterruptor =
        when (estilo) {
            EstiloDoInterruptor.AJUSTE -> EscolhaDoInterruptor(
                bolinhaLigado = paleta.onAccent,
                trilhoLigado = paleta.accent,
                bordaLigado = Color.Transparent,
                bolinhaDesligado = paleta.thumbOff,
                trilhoDesligado = paleta.trackOff,
                bordaDesligado = Color.Transparent,
                bolinhaLigadoDesabilitado = paleta.onAccent,
                trilhoLigadoDesabilitado = comMeioAlfa(paleta.accent),
                bolinhaDesligadoDesabilitado = paleta.thumbOff,
                trilhoDesligadoDesabilitado = comMeioAlfa(paleta.trackOff),
            )

            EstiloDoInterruptor.QUATRO_CORES -> EscolhaDoInterruptor(
                bolinhaLigado = paleta.onAccent,
                trilhoLigado = paleta.accent,
                bolinhaDesligado = paleta.thumbOff,
                trilhoDesligado = paleta.trackOff,
            )

            EstiloDoInterruptor.CLARO -> EscolhaDoInterruptor(
                bolinhaLigado = paleta.textPrimary,
                trilhoLigado = paleta.accent,
                bordaLigado = Color.Transparent,
                bolinhaDesligado = paleta.textSecondary,
                trilhoDesligado = paleta.trackOff,
                bordaDesligado = Color.Transparent,
            )

            EstiloDoInterruptor.SO_LIGADO -> EscolhaDoInterruptor(
                bolinhaLigado = paleta.onAccent,
                trilhoLigado = paleta.accent,
            )

            EstiloDoInterruptor.LIGADO_BRANCO -> EscolhaDoInterruptor(
                bolinhaLigado = Color(0xFFFFFFFF),
                trilhoLigado = paleta.accent,
            )

            EstiloDoInterruptor.CLASSICO -> EscolhaDoInterruptor(
                bolinhaLigado = CoresClassicas.texto,
                trilhoLigado = paleta.accent,
                bordaLigado = Color.Transparent,
                bolinhaDesligado = CoresClassicas.textoSecundario,
                trilhoDesligado = CoresClassicas.botaoSecundario,
                bordaDesligado = Color.Transparent,
            )
        }

    fun doControleDeslizante(
        estilo: EstiloDoControleDeslizante,
        paleta: PaletaDaInterface,
    ): EscolhaDoControleDeslizante = when (estilo) {
        EstiloDoControleDeslizante.DESTAQUE -> EscolhaDoControleDeslizante(
            bolinha = paleta.accent,
            trilhoAtivo = paleta.accent,
            trilhoInativo = paleta.trackOff,
        )

        EstiloDoControleDeslizante.DESTAQUE_APAGADO -> EscolhaDoControleDeslizante(
            bolinha = paleta.accent,
            trilhoAtivo = paleta.accent,
            trilhoInativo = paleta.trackOff,
            bolinhaDesabilitada = paleta.trackOff,
            trilhoAtivoDesabilitado = paleta.trackOff,
            trilhoInativoDesabilitado = paleta.trackOff,
        )

        EstiloDoControleDeslizante.BOLINHA_BRANCA -> EscolhaDoControleDeslizante(
            bolinha = Color(0xFFFFFFFF),
            trilhoAtivo = paleta.accent,
            trilhoInativo = paleta.trackOff,
        )

        EstiloDoControleDeslizante.AJUSTE -> EscolhaDoControleDeslizante(
            bolinha = Color(0xFFFFFFFF),
            trilhoAtivo = paleta.accent,
            trilhoInativo = paleta.trackOff,
            marcaAtiva = Color.Transparent,
            marcaInativa = Color.Transparent,
            bolinhaDesabilitada = Color(0xFFFFFFFF),
            trilhoAtivoDesabilitado = paleta.accent,
            trilhoInativoDesabilitado = paleta.trackOff,
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
