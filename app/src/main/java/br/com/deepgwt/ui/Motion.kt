// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper

/**
 * Specs de spring nomeados (skill apple-design / "Designing Fluid Interfaces"): a Apple
 * parametriza motion por damping (overshoot) + response (segundos até assentar), não por
 * duração fixa. O Compose usa dampingRatio + stiffness (mass=1) — convertendo:
 * stiffness ≈ (2π / response)².
 */
object Motion {
    private fun stiffnessFor(responseSeconds: Float): Float {
        val omega = (2.0 * Math.PI / responseSeconds).toFloat()
        return omega * omega
    }

    /** Crítico (sem overshoot), bem rápido — feedback de toque. */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = stiffnessFor(0.15f))

    /** Crítico (sem overshoot) — reposicionamento de elementos (cards, dashboards). */
    fun <T> reposition(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = stiffnessFor(0.4f))

    /** Levemente subamortecido — entrada de popups/sheets (drawer-like, na tabela da Apple). */
    fun <T> sheet(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = stiffnessFor(0.3f))

    /** Levemente subamortecido — assentamento ao soltar um arrasto com momentum. */
    fun <T> momentum(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = stiffnessFor(0.4f))
}

/**
 * Reflete a opção "Remover animações" do Android (escala 0 em Configurações de desenvolvedor),
 * a mesma preferência de acessibilidade que back nesse SO em vez do prefers-reduced-motion da web.
 * Atualiza ao vivo caso o usuário troque a opção com o app aberto.
 */
@Composable
fun reducedMotionEnabled(): State<Boolean> {
    val context = LocalContext.current
    val state = remember {
        mutableStateOf(
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        )
    }
    DisposableEffect(context) {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                state.value = Settings.Global.getFloat(
                    context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
                ) == 0f
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}

/**
 * Troca `full` por `reducedFallback` quando o usuário desligou animações no sistema — por
 * padrão um `snap()` instantâneo, mas o chamador pode passar algo como `tween(150)` quando
 * ainda faz sentido dar algum feedback não-espacial (ex.: uma troca de cor).
 */
@Composable
fun <T> motionSpec(
    full: FiniteAnimationSpec<T>,
    reducedFallback: FiniteAnimationSpec<T> = snap()
): FiniteAnimationSpec<T> {
    val reduced by reducedMotionEnabled()
    return if (reduced) reducedFallback else full
}

/**
 * Resistência progressiva no limite de um arrasto (skill apple-design: "rubber-banding — soft
 * boundaries"), em vez de parar seco. `dimension` é a extensão "elástica" de referência (ex.:
 * altura do elemento sendo arrastado) — quanto maior o excesso, menos ele acompanha o dedo.
 */
fun rubberband(overshoot: Float, dimension: Float, constant: Float = 0.55f): Float {
    if (dimension <= 0f) return overshoot
    val sign = if (overshoot < 0f) -1f else 1f
    val magnitude = kotlin.math.abs(overshoot)
    return sign * (magnitude * dimension * constant) / (dimension + constant * magnitude)
}

/**
 * Onde um lançamento PARA, dada a velocidade no instante em que o dedo sai (px/s). Serve para
 * decidir pelo DESTINO do gesto em vez de pela distância já percorrida (skill apple-design:
 * "momentum projection"), que é o que permite um toque curto e rápido valer tanto quanto um
 * arrasto longo — e, no sentido contrário, permite desistir puxando de volta antes de soltar.
 *
 * Decaimento exponencial, que é o que a Apple publica no código de exemplo de "Designing Fluid
 * Interfaces" e o que governa a inércia de rolagem do iOS — NÃO a fórmula de livro v²/(2a), que
 * dá outro número. 0.998 é a taxa de rolagem normal; 0.99 seria mais seco.
 *
 * Mesma função, mesma constante, que a do launcher 3D (`projetar()` em deepGwtClient.js): os dois
 * lados do produto reagem igual ao mesmo gesto.
 */
fun projectDeceleration(velocity: Float, decelerationRate: Float = 0.998f): Float {
    return (velocity / 1000f) * decelerationRate / (1f - decelerationRate)
}

/**
 * Progresso 0→1 pra entrada de um popup/menu (usar com `Modifier.materialize(progress)`),
 * mesmo formato do `entryProgress` já usado no painel expandido da barra — só nomeado e
 * reutilizável, com `Motion.sheet()` como spec e respeito a "reduce motion" embutido.
 */
@Composable
fun rememberMenuEntrance(): Float {
    val reduced by reducedMotionEnabled()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (reduced) progress.snapTo(1f) else progress.animateTo(1f, Motion.sheet())
    }
    return progress.value
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
