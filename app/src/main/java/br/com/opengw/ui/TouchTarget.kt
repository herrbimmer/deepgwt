package br.com.opengw.ui

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Piso de área tocável (skill apple-fluid-ui: "minimum 44x44px touch targets").
 *
 * O ponto da regra é que o alvo TOCÁVEL e o alvo PINTADO são coisas diferentes. Um botão
 * redondo de 30dp continua desenhado com 30dp; o que cresce é a área que responde ao dedo ao
 * redor dele. Encolher o dedo do usuário não é uma opção, e aumentar todo círculo da barra
 * para 44dp mudaria o desenho da barra inteira — que não é o que a regra pede.
 *
 * Num carro isto pesa mais do que numa mesa: a mão chega com o braço estendido, o carro se
 * mexe embaixo dela, e o alvo é adquirido com o olho fora da estrada. 44dp é o piso da Apple
 * para um aparelho segurado com as duas mãos e parado — aqui ele é o mínimo, não a meta.
 *
 * USO: no Box EXTERNO, o que tem o `clickable`. O visual (size/clip/background) vai num Box
 * interno centralizado. Posto depois de um `.size()` fixo este modifier não faz nada, porque
 * `size` fixa as constraints e `defaultMinSize` só age sobre o que ainda está livre.
 *
 * ```
 * Box(contentAlignment = Alignment.Center, modifier = Modifier.touchTarget().clickable { }) {
 *     Box(Modifier.size(30.dp).clip(CircleShape).background(cor)) { Icon(...) }
 * }
 * ```
 */
object TouchTarget {
    /** Piso da skill. */
    val Minimum: Dp = 44.dp
}

fun Modifier.touchTarget(
    minWidth: Dp = TouchTarget.Minimum,
    minHeight: Dp = TouchTarget.Minimum
): Modifier = this.defaultMinSize(minWidth = minWidth, minHeight = minHeight)
