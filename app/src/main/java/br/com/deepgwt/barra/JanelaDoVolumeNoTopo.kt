// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.barra

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Onde o cartão de volume fica: por cima do pop-up de volume do carro, cobrindo-o com folga.
 *
 * ## O POP-UP ORIGINAL (medido na Alpha 7.8)
 *
 * Na print do espelhamento (1920×720, 160 dpi) com os dois na tela, o pop-up do carro ocupa
 * x 658–1390 e y 60–156: 732×96, logo abaixo da barra de status e centrado na área À DIREITA do
 * painel lateral de 128 px (o centro é 1024 = (128 + 1920) / 2, não 960). O nosso da 7.6 tinha
 * 640×84, 8 px do topo, centrado na tela inteira: sobrava pop-up dos dois lados e embaixo.
 *
 * Agora o cartão é o retângulo do original com [FOLGA_DP] a mais de cada lado — "sobrepor
 * completamente e ser um pouco maior". Com o painel lateral escondido de propósito o centro passa
 * a ser o da tela, que é onde uma janela do sistema centrada cairia (a confirmar no carro).
 */
internal object GeometriaDoVolumeNoTopo {
    const val LARGURA_DO_ORIGINAL_DP = 732
    const val ALTURA_DO_ORIGINAL_DP = 96
    const val TOPO_DO_ORIGINAL_DP = 60

    /** Quanto o nosso passa do original em cada lado. */
    const val FOLGA_DP = 14

    const val LARGURA_DP = LARGURA_DO_ORIGINAL_DP + 2 * FOLGA_DP
    const val ALTURA_DP = ALTURA_DO_ORIGINAL_DP + 2 * FOLGA_DP
    const val TOPO_DP = TOPO_DO_ORIGINAL_DP - FOLGA_DP

    /** `[esquerda, topo, direita, base]` em px da tela física. */
    fun retangulo(larguraDaTela: Int, painelLateralPx: Int, densidade: Float): IntArray {
        val largura = (LARGURA_DP * densidade).roundToInt().coerceAtMost(larguraDaTela.coerceAtLeast(1))
        val altura = (ALTURA_DP * densidade).roundToInt()
        val topo = (TOPO_DP * densidade).roundToInt()
        val centro = (painelLateralPx.coerceIn(0, larguraDaTela) + larguraDaTela) / 2
        val esquerda = (centro - largura / 2).coerceIn(0, (larguraDaTela - largura).coerceAtLeast(0))
        return intArrayOf(esquerda, topo, esquerda + largura, topo + altura)
    }
}

/**
 * A janela do cartão de volume na camada de acessibilidade (ver [CamadaDeAcessibilidade]).
 *
 * Do tamanho exato do cartão, sempre montada enquanto o serviço de acessibilidade estiver de pé:
 * escondida é alpha 0 e não tocável, e o compositor pula a camada. Aberta pelo carro (botão do
 * volante) continua não tocável — é só indicador, como sempre foi.
 *
 * Sem o serviço de acessibilidade o cartão volta a ser desenhado na janela de menus da barra, no
 * mesmo retângulo — só que por baixo do pop-up do carro, como na 7.6.
 */
internal class JanelaDoVolumeNoTopo(
    private val servico: AccessibilityService,
    private val preparar: (ComposeView) -> Unit,
    /** O cartão de volume em si (desenhado pela barra). */
    private val conteudo: @Composable () -> Unit
) {
    private companion object {
        const val TAG = "DeepGWT.Volume"
        const val CORRECOES_MAXIMAS = 3
    }

    private val wm = servico.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var vista: ComposeView? = null
    private val alvo = IntArray(4)
    private var correcoes = 0
    private val local = IntArray(2)

    private val params =
        WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            alpha = 0f
            windowAnimations = 0
            title = "DeepGWT volume"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

    private val aoDispor = ViewTreeObserver.OnGlobalLayoutListener { corrigirPosicao() }

    val montada: Boolean
        get() = vista != null

    fun montar(retangulo: IntArray): Boolean {
        if (vista != null) return true
        aplicarRetangulo(retangulo)
        val nova = ComposeView(servico).apply {
            preparar(this)
            setContent { conteudo() }
        }
        return try {
            wm.addView(nova, params)
            nova.viewTreeObserver.addOnGlobalLayoutListener(aoDispor)
            vista = nova
            Log.w(TAG, "[VOLUME] cartão na camada de acessibilidade em [${alvo.joinToString(",")}]")
            true
        } catch (e: Exception) {
            Log.w(TAG, "[VOLUME] camada de acessibilidade recusou a janela: ${e.message}")
            false
        }
    }

    fun atualizar(visivel: Boolean, tocavel: Boolean, retangulo: IntArray) {
        val v = vista ?: return
        if (!retangulo.contentEquals(alvo)) aplicarRetangulo(retangulo)
        params.alpha = if (visivel) 1f else 0f
        params.flags =
            if (visivel && tocavel) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        runCatching { wm.updateViewLayout(v, params) }
            .onFailure { Log.w(TAG, "[VOLUME] falha ao atualizar a janela: ${it.message}") }
    }

    fun desmontar() {
        val v = vista ?: return
        vista = null
        runCatching { v.viewTreeObserver.removeOnGlobalLayoutListener(aoDispor) }
        runCatching { wm.removeViewImmediate(v) }
    }

    private fun aplicarRetangulo(r: IntArray) {
        r.copyInto(alvo)
        params.x = r[0]
        params.y = r[1]
        params.width = r[2] - r[0]
        params.height = r[3] - r[1]
        correcoes = 0
    }

    /**
     * O WindowManager desloca janelas para a direita do painel lateral (é o mesmo problema que a
     * barra resolve prendendo a janela em x=0) e, conforme o tipo, para baixo da barra de status.
     * Em vez de adivinhar a regra desta central, mede onde a janela caiu e compensa a diferença —
     * no máximo [CORRECOES_MAXIMAS] vezes por posição, para nunca brigar em laço com o sistema.
     */
    private fun corrigirPosicao() {
        val v = vista ?: return
        if (correcoes >= CORRECOES_MAXIMAS) return
        v.getLocationOnScreen(local)
        val dx = local[0] - alvo[0]
        val dy = local[1] - alvo[1]
        if (abs(dx) <= 1 && abs(dy) <= 1) return
        correcoes++
        params.x -= dx
        params.y -= dy
        Log.w(TAG, "[VOLUME] janela caiu em (${local[0]},${local[1]}), alvo (${alvo[0]},${alvo[1]}): corrigindo")
        runCatching { wm.updateViewLayout(v, params) }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
