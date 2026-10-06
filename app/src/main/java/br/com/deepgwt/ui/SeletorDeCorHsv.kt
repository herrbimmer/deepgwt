// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Seletor de cor HSV (seção 6.E): o mapa de saturação × brilho e, embaixo, a faixa de matiz.
 *
 * Tocar muda e confirma. Arrastar muda a cada movimento e confirma ao soltar; arrasto cancelado
 * pelo sistema não confirma (D11). Os gestos chamam sempre as funções mais recentes recebidas.
 */
@Composable
fun SeletorDeCorHsv(
    matiz: Float,
    saturacao: Float,
    brilho: Float,
    aoMudarMatiz: (Float) -> Unit,
    aoMudarSaturacaoEBrilho: (saturacao: Float, brilho: Float) -> Unit,
    aoConfirmar: () -> Unit,
    modifier: Modifier = Modifier,
    alturaDoMapa: Dp = 150.dp,
) {
    val mudarMatiz by rememberUpdatedState(aoMudarMatiz)
    val mudarSaturacaoEBrilho by rememberUpdatedState(aoMudarSaturacaoEBrilho)
    val confirmar by rememberUpdatedState(aoConfirmar)

    // A cor pura da matiz pela conversão HSV da plataforma; lida aqui, usada no desenho.
    val corPura = Color(android.graphics.Color.HSVToColor(floatArrayOf(matiz, 1f, 1f)))

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(alturaDoMapa)
                .clip(RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val (s, v) = ContaDoSeletorHsv.saturacaoEBrilho(p.x, p.y, size.width.toFloat(), size.height.toFloat())
                        mudarSaturacaoEBrilho(s, v)
                        confirmar()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { confirmar() },
                        onDragCancel = { },
                    ) { mudanca, _ ->
                        mudanca.consume()
                        val p = mudanca.position
                        val (s, v) = ContaDoSeletorHsv.saturacaoEBrilho(p.x, p.y, size.width.toFloat(), size.height.toFloat())
                        mudarSaturacaoEBrilho(s, v)
                    }
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, corPura)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(saturacao * size.width, (1f - brilho) * size.height),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        mudarMatiz(ContaDoSeletorHsv.matiz(p.x, size.width.toFloat()))
                        confirmar()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { confirmar() },
                        onDragCancel = { },
                    ) { mudanca, _ ->
                        mudanca.consume()
                        mudarMatiz(ContaDoSeletorHsv.matiz(mudanca.position.x, size.width.toFloat()))
                    }
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(ContaDoSeletorHsv.CORES_DA_FAIXA))
                    val x = matiz / 360f * size.width
                    drawLine(
                        color = Color.White,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 3.dp.toPx(),
                    )
                }
        )
    }
}

/** Contas puras do seletor HSV. */
object ContaDoSeletorHsv {
    /** As cores puras (S = V = 1) das matizes 0°, 60°, ..., 360°, espaçadas igualmente na faixa. */
    val CORES_DA_FAIXA: List<Color> = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000),
    )

    /** Ponto no mapa → (x / largura, 1 − y / altura), os dois limitados a 0..1. */
    fun saturacaoEBrilho(x: Float, y: Float, largura: Float, altura: Float): Pair<Float, Float> {
        val s = if (largura > 0f) (x / largura).coerceIn(0f, 1f) else 0f
        val v = if (altura > 0f) (1f - y / altura).coerceIn(0f, 1f) else 0f
        return s to v
    }

    /** Ponto na faixa → (x / largura) limitado a 0..1, vezes 360. */
    fun matiz(x: Float, largura: Float): Float {
        val fracao = if (largura > 0f) (x / largura).coerceIn(0f, 1f) else 0f
        return fracao * 360f
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
