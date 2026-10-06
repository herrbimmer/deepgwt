package br.com.deepgwt.tela3d.faixa

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import kotlin.math.max

/**
 * Rolagem horizontal com elástico nas pontas (F27) e esmaecimento nas bordas. Sem barras, fundo
 * transparente, conteúdo no mínimo tão largo quanto a área. Consome o toque em toda a sua área.
 *
 * @param comprimentoDoEsmaecimentoDp 72 na fileira de apps e 42 na linha de cartões
 */
class RolagemComElastico(contexto: Context, comprimentoDoEsmaecimentoDp: Int) : HorizontalScrollView(contexto) {
    private val densidade = Densidade(contexto)
    private val calculo = CalculoDoElastico(densidade.pxF(CalculoDoElastico.LIMITE_DP))
    private var ultimoX = 0f
    private var volta: ValueAnimator? = null

    init {
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        setBackgroundColor(Color.TRANSPARENT)
        clipChildren = false
        setFadingEdgeLength(densidade.px(comprimentoDoEsmaecimentoDp))
        isHorizontalFadingEdgeEnabled = false
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // O esmaecimento só liga depois que o conteúdo rolou além do início.
        val ligado = l > 0
        if (ligado != isHorizontalFadingEdgeEnabled) isHorizontalFadingEdgeEnabled = ligado
    }

    private fun conteudo(): View? = getChildAt(0)

    private fun areaDeRolagem(): Int {
        val filho = conteudo() ?: return 0
        return max(0, filho.width - (width - paddingLeft - paddingRight))
    }

    private fun aplicarPuxada() {
        conteudo()?.translationX = calculo.puxada
    }

    private fun cancelarVolta() {
        val atual = volta
        volta = null
        atual?.cancel()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            // Tocar de novo cancela a volta do elástico.
            cancelarVolta()
            ultimoX = ev.x
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                ultimoX = ev.x
                return super.onTouchEvent(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - ultimoX
                ultimoX = ev.x
                val estavaPuxando = calculo.puxando
                val consumiu = calculo.mover(dx, scrollX <= 0, scrollX >= areaDeRolagem())
                aplicarPuxada()
                if (consumiu) return true
                if (estavaPuxando) {
                    // A rolagem normal não viu os movimentos consumidos: recomeça dela no ponto atual.
                    val inicio = MotionEvent.obtain(ev)
                    inicio.action = MotionEvent.ACTION_DOWN
                    super.onTouchEvent(inicio)
                    inicio.recycle()
                }
                return super.onTouchEvent(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (calculo.soltar()) animarVolta() else {
                    calculo.zerar()
                    aplicarPuxada()
                }
                // O último evento ainda passa à rolagem normal.
                return super.onTouchEvent(ev)
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun animarVolta() {
        cancelarVolta()
        val inicio = calculo.puxada
        val animador = ValueAnimator.ofFloat(inicio, 0f)
        animador.duration = CalculoDoElastico.VOLTA_MS
        animador.interpolator = DecelerateInterpolator(CalculoDoElastico.FATOR_DA_VOLTA)
        animador.addUpdateListener {
            calculo.definir(it.animatedValue as Float)
            aplicarPuxada()
        }
        animador.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (volta === animation) {
                    volta = null
                    calculo.zerar()
                    aplicarPuxada()
                }
            }
        })
        volta = animador
        animador.start()
    }

    override fun onDetachedFromWindow() {
        cancelarVolta()
        super.onDetachedFromWindow()
    }
}
