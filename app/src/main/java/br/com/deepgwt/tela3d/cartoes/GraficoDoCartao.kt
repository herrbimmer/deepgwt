package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import br.com.deepgwt.tela3d.faixa.TemaDoCromo
import br.com.deepgwt.tela3d.ponte.CartaoInferior

/**
 * A view do gráfico de um cartão: guarda o descritor atual, desenha com o tema do momento e cuida da
 * pulsação da bateria do fluxo de energia (só enquanto visível, com foco na janela e animações ligadas).
 */
internal class GraficoDoCartao(
    contexto: Context,
    private val tema: () -> TemaDoCromo,
    densidade: Float,
) : View(contexto) {
    private val desenho = DesenhoDosGraficos(contexto, densidade)
    private var cartao: CartaoInferior? = null
    private var pedindoImagens = false
    private var pulsando = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun definir(novo: CartaoInferior) {
        val antes = cartao
        cartao = novo
        if (novo.id == "power" && !pedindoImagens) {
            pedindoImagens = true
            ImagensDosCartoes.pedir(context) { invalidate() }
        }
        if (antes != novo) invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val c = cartao ?: return
        val agora = System.currentTimeMillis() / 1000.0
        val podePulsar = isShown && hasWindowFocus() && escalaDeAnimacao() > 0f
        pulsando = desenho.desenhar(canvas, width.toFloat(), height.toFloat(), c, tema(), DesenhoDosGraficos.Pulso(agora, podePulsar))
        if (pulsando) postInvalidateDelayed(GeometriaDoFluxo.QUADRO_DA_PULSACAO_MS)
    }

    override fun onWindowFocusChanged(temFoco: Boolean) {
        super.onWindowFocusChanged(temFoco)
        if (temFoco && cartao?.id == "power") invalidate()
    }

    override fun onVisibilityAggregated(visivel: Boolean) {
        super.onVisibilityAggregated(visivel)
        if (visivel && cartao?.id == "power") invalidate()
    }

    private var escalaLida = 1f
    private var escalaLidaEm = Long.MIN_VALUE

    /** `animator_duration_scale` do sistema, relido no máximo uma vez por segundo. */
    private fun escalaDeAnimacao(): Float {
        val agora = System.currentTimeMillis()
        if (agora - escalaLidaEm > 1000L) {
            escalaLidaEm = agora
            escalaLida = try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            } catch (_: Exception) {
                1f
            }
        }
        return escalaLida
    }
}
