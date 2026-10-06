// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * As cores do `Switch` do Material para um estilo (caso 52). Só os papéis que o estilo define são
 * trocados; o resto (ex. borda do desligado = `outline`) fica o do Material, que vem do esquema de
 * reserva. A escolha em si é pura: [EscolhaDeCores.doInterruptor].
 */
@Composable
fun coresDoInterruptor(estilo: EstiloDoInterruptor): SwitchColors {
    val e = EscolhaDeCores.doInterruptor(estilo, LocalPaletaDaInterface.current)
    // `copy` mantém a cor padrão onde a escolha é Color.Unspecified.
    return SwitchDefaults.colors().copy(
        checkedThumbColor = e.bolinhaLigado,
        checkedTrackColor = e.trilhoLigado,
        checkedBorderColor = e.bordaLigado,
        uncheckedThumbColor = e.bolinhaDesligado,
        uncheckedTrackColor = e.trilhoDesligado,
        uncheckedBorderColor = e.bordaDesligado,
        disabledCheckedThumbColor = e.bolinhaLigadoDesabilitado,
        disabledCheckedTrackColor = e.trilhoLigadoDesabilitado,
        disabledUncheckedThumbColor = e.bolinhaDesligadoDesabilitado,
        disabledUncheckedTrackColor = e.trilhoDesligadoDesabilitado,
    )
}

/**
 * O `Switch` do Material com as cores do [estilo] (caso 51). [aoMudar] pode ser nulo quando a
 * linha de fora trata o toque. [escala] diferente de 1 amplia o desenho (1,05 nos ajustes
 * agrupados).
 */
@Composable
fun Interruptor(
    ligado: Boolean,
    aoMudar: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    estilo: EstiloDoInterruptor = EstiloDoInterruptor.QUATRO_CORES,
    escala: Float = 1f,
) {
    Switch(
        checked = ligado,
        onCheckedChange = aoMudar,
        modifier = if (escala != 1f) modifier.scale(escala) else modifier,
        enabled = habilitado,
        colors = coresDoInterruptor(estilo),
    )
}

/** As cores do `Slider` do Material para um estilo (caso 53). Escolha pura em [EscolhaDeCores]. */
@Composable
fun coresDoControleDeslizante(estilo: EstiloDoControleDeslizante): SliderColors {
    val e = EscolhaDeCores.doControleDeslizante(estilo, LocalPaletaDaInterface.current)
    return SliderDefaults.colors().copy(
        thumbColor = e.bolinha,
        activeTrackColor = e.trilhoAtivo,
        activeTickColor = e.marcaAtiva,
        inactiveTrackColor = e.trilhoInativo,
        inactiveTickColor = e.marcaInativa,
        disabledThumbColor = e.bolinhaDesabilitada,
        disabledActiveTrackColor = e.trilhoAtivoDesabilitado,
        disabledInactiveTrackColor = e.trilhoInativoDesabilitado,
    )
}

/** O `Slider` do Material com as cores do [estilo] (caso 53). */
@Composable
fun ControleDeslizante(
    valor: Float,
    aoMudar: (Float) -> Unit,
    modifier: Modifier = Modifier,
    faixa: ClosedFloatingPointRange<Float> = 0f..1f,
    passos: Int = 0,
    habilitado: Boolean = true,
    aoSoltar: (() -> Unit)? = null,
    estilo: EstiloDoControleDeslizante = EstiloDoControleDeslizante.DESTAQUE,
) {
    Slider(
        value = valor,
        onValueChange = aoMudar,
        modifier = modifier,
        enabled = habilitado,
        valueRange = faixa,
        steps = passos,
        onValueChangeFinished = aoSoltar,
        colors = coresDoControleDeslizante(estilo),
    )
}

/**
 * Rótulo à esquerda e valor à direita (14 sp, fonte do sistema) e o controle embaixo, no estilo
 * [EstiloDoControleDeslizante.DESTAQUE_APAGADO] (caso 54). Desabilitado: os dois textos apagados.
 */
@Composable
fun LinhaDeControleDeslizante(
    rotulo: String,
    valor: Float,
    aoMudar: (Float) -> Unit,
    modifier: Modifier = Modifier,
    faixa: ClosedFloatingPointRange<Float> = 0f..100f,
    passos: Int = 0,
    habilitado: Boolean = true,
    textoDoValor: (Float) -> String = { "${it.roundToInt()}%" },
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = rotulo,
                fontSize = 14.sp,
                color = if (habilitado) Cores.textoPrincipal else Cores.textoApagado,
            )
            Text(
                text = textoDoValor(valor),
                fontSize = 14.sp,
                color = if (habilitado) Cores.textoSecundario else Cores.textoApagado,
            )
        }
        ControleDeslizante(
            valor = valor,
            aoMudar = aoMudar,
            faixa = faixa,
            passos = passos,
            habilitado = habilitado,
            estilo = EstiloDoControleDeslizante.DESTAQUE_APAGADO,
        )
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
