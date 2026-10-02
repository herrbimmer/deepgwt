package br.com.opengw.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Feedback de toque imediato (skill apple-design: "responder no toque, não na soltura").
 * Puramente aditivo — escala no toque via spring, sem tocar em nenhuma lógica de cor/glow
 * já existente nos botões que a chamam.
 */
fun Modifier.fluidPress(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = motionSpec(Motion.press(), androidx.compose.animation.core.tween(80)),
        label = "fluidPressScale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
