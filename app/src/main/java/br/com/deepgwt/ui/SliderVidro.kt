package br.com.deepgwt.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * `Slider` com o visual Liquid Glass (Alpha 10.6): calha de vidro, parte cheia translúcida na cor do
 * estilo e botão em pílula de vidro com brilho especular, que cresce e clareia ao tocar.
 *
 * Tem a mesma assinatura do `Slider` do Material: as telas só trocam o import (androidx.compose.material3.Slider
 * -> br.com.deepgwt.ui.Slider). As cores vêm de [colors], então os estilos (EstiloDoControleDeslizante) seguem valendo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val tocado by interactionSource.collectIsPressedAsState()
    val arrastado by interactionSource.collectIsDraggedAsState()
    val ativo = tocado || arrastado
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = colors,
        interactionSource = interactionSource,
        thumb = { BotaoDeVidro(ativo = ativo, habilitado = enabled) },
        track = { estado -> CalhaDeVidro(estado = estado, cores = colors, habilitado = enabled) },
    )
}

@Composable
private fun BotaoDeVidro(ativo: Boolean, habilitado: Boolean) {
    val escala by animateFloatAsState(if (ativo) 1.14f else 1f, label = "escalaDoBotaoDeVidro")
    val forma = RoundedCornerShape(13.dp)
    val miolo = if (ativo) 0.52f else 0.92f
    val alfa = if (habilitado) 1f else 0.45f
    Box(
        Modifier
            .size(width = 38.dp, height = 26.dp)
            .scale(escala)
            .shadow(if (ativo) 8.dp else 4.dp, forma, ambientColor = Color(0x66000000), spotColor = Color(0x66000000))
            .clip(forma)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = miolo * alfa), Color(0xFFE2EAF6).copy(alpha = (miolo - 0.22f).coerceAtLeast(0.3f) * alfa)),
                ),
            )
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f * alfa), Color.White.copy(alpha = 0.35f * alfa))), forma),
    ) {
        // o brilho especular: uma faixa clara no terço de cima
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f * alfa), Color.Transparent))),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalhaDeVidro(estado: SliderState, cores: SliderColors, habilitado: Boolean) {
    val forma = RoundedCornerShape(50)
    val ini = estado.valueRange.start
    val fim = estado.valueRange.endInclusive
    val fracao = if (fim > ini) ((estado.value - ini) / (fim - ini)).coerceIn(0f, 1f) else 0f
    val base = cores.inactiveTrackColor.copy(alpha = if (habilitado) 0.62f else 0.30f)
    val cheia = cores.activeTrackColor.copy(alpha = if (habilitado) 0.78f else 0.35f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(forma)
            .background(base)
            .border(1.dp, Color.White.copy(alpha = 0.16f), forma),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fracao)
                .clip(forma)
                .background(cheia),
        )
        // reflexo no alto da calha
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent))),
        )
    }
}
