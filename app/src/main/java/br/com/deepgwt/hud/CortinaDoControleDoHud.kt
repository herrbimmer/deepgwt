// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
package br.com.deepgwt.hud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A cortina do Controle do HUD sobre o painel de instrumentos (Alpha 9.5).
 *
 * "Borrar completamente o cluster e colocar um overlay que fala: Controle do HUD ativado."
 *
 * ## SEM DESFOQUE DE VERDADE, E POR QUÊ
 *
 * A central roda Android 9: não há `RenderEffect` nem desfoque de janela, e o painel de fábrica é
 * desenhado fora do Android (a WebView do DeepGWT fica transparente por cima dele). Não há como
 * borrar o que está embaixo. A cortina faz o efeito que importa — o painel some — com um vidro
 * fosco escuro quase opaco (98%), um brilho azul no centro e um cartão de vidro com o recado.
 *
 * ## O QUE O CARTÃO MOSTRA
 *
 * O HUD ao vivo (a mesma [HudOverlayView] do para-brisa, como no Círculo do painel), o título, a
 * página no ar com os pontinhos e as duas dicas: ↑ ↓ trocam a página, segurar OK sai.
 *
 * Mora na mesma raiz do projetor do painel, por cima de tudo ([View.bringToFront] ao aparecer):
 * a projeção da central e o círculo também moram ali. Coordenadas em px do painel (1920x720).
 */
@SuppressLint("ViewConstructor")
class CortinaDoControleDoHud(
    private val ctx: Context,
    private val raiz: FrameLayout
) {

    companion object {
        private const val ENTRADA_MS = 180L
        private const val SAIDA_MS = 220L

        private val COR_TITULO = Color.WHITE
        private val COR_SECUNDARIA = Color.rgb(168, 178, 192)
        private val COR_DO_CONTROLE = HudOverlayView.COR_DO_CONTROLE

        private const val HUD_LARGURA = 432
        private const val HUD_ALTURA = 216
    }

    /** O fundo fosco, desenhado à mão (um quadro por aparição). O cartão é o fundo do conteúdo. */
    private inner class Fundo(c: Context) : View(c) {
        private val tinta = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            // Vidro fosco: preto azulado quase opaco — o painel embaixo deixa de ser legível (no
            // emulador, a 93% ainda dava para ler a hora da tela de baixo; 98% a apaga).
            canvas.drawColor(Color.argb(250, 7, 9, 13))
            // Brilho azul difuso no centro, como luz atravessando o vidro.
            tinta.shader = RadialGradient(
                w / 2f, h * 0.48f, w * 0.42f,
                intArrayOf(Color.argb(70, 60, 140, 255), Color.argb(18, 60, 140, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, tinta)
            // Vinheta nas bordas: tira o degrau onde a cortina encontra a moldura da tela.
            tinta.shader = RadialGradient(
                w / 2f, h / 2f, w * 0.62f,
                intArrayOf(Color.TRANSPARENT, Color.argb(120, 0, 0, 0)),
                floatArrayOf(0.6f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, w, h, tinta)

            tinta.shader = null
        }
    }

    private val fundo = Fundo(ctx)

    private val hud = HudOverlayView(ctx, paginaFixa = null, comAlertas = false)

    private val molduraDoHud = FrameLayout(ctx).apply {
        setPadding(10, 10, 10, 10)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = 22f
            setStroke(2, Color.argb(60, 255, 255, 255))
        }
        clipToOutline = true
        addView(hud, FrameLayout.LayoutParams(HUD_LARGURA, HUD_ALTURA))
    }

    private val selo = texto(20f, COR_DO_CONTROLE, negrito = true).apply {
        text = "HEAD-UP DISPLAY"
        letterSpacing = 0.18f
    }
    private val titulo = texto(46f, COR_TITULO, negrito = true).apply {
        text = "Controle do HUD ativado"
    }
    private val pagina = texto(26f, COR_SECUNDARIA).apply { setPadding(0, 14, 0, 0) }
    private val pontos = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 22f)
        gravity = Gravity.START
        setPadding(0, 6, 0, 0)
    }
    private val dicaSetas = pilula("↑  ↓   trocam a página")
    private val dicaSair = pilula("Segure OK para sair")

    private val textos = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        addView(selo)
        addView(titulo)
        addView(pagina)
        addView(pontos)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 26, 0, 0)
            addView(dicaSetas)
            addView(dicaSair, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = 14 })
        })
    }

    /**
     * O cartão de vidro abraça o conteúdo (em vez de ser uma fração fixa da tela): no emulador de
     * 1080x600 o texto passava da borda de um cartão de 56% da largura.
     */
    private val conteudo = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(40, 40, 48, 40)
        background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(40, 255, 255, 255), Color.argb(14, 255, 255, 255))
        ).apply {
            cornerRadius = 44f
            setStroke(2, Color.argb(80, 255, 255, 255))
        }
        addView(molduraDoHud)
        addView(textos, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = 48 })
    }

    private val camada = FrameLayout(ctx).apply {
        alpha = 0f
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        addView(fundo, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(conteudo, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
    }

    private var liberada = false

    init {
        raiz.addView(camada, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    fun mostrar(visivel: Boolean, animar: Boolean = true) {
        if (liberada) return
        camada.animate().cancel()
        if (visivel) {
            atualizar()
            HudOverlayController.registrarEspelhoDoControle(hud)
            camada.visibility = View.VISIBLE
            camada.bringToFront()
            if (animar) camada.animate().alpha(1f).setDuration(ENTRADA_MS).start() else camada.alpha = 1f
        } else {
            HudOverlayController.registrarEspelhoDoControle(null)
            if (animar && camada.visibility == View.VISIBLE) {
                camada.animate().alpha(0f).setDuration(SAIDA_MS)
                    .withEndAction { camada.visibility = View.GONE }.start()
            } else {
                camada.alpha = 0f
                camada.visibility = View.GONE
            }
        }
    }

    /** Página no ar e pontinhos. Chamado ao entrar e a cada ↑/↓. */
    fun atualizar() {
        val atual = HudPaginas.pagina
        pagina.text = "Página: ${HudSettings.tituloDaPagina(atual)}"
        val visiveis = HudPaginas.visiveis
        val sb = android.text.SpannableStringBuilder()
        visiveis.forEachIndexed { i, p ->
            if (i > 0) sb.append("  ")
            val inicio = sb.length
            sb.append(if (p == atual) "▬" else "•")
            sb.setSpan(
                android.text.style.ForegroundColorSpan(if (p == atual) COR_DO_CONTROLE else Color.argb(110, 255, 255, 255)),
                inicio, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        pontos.text = sb
        hud.invalidate()
    }

    fun liberar() {
        if (liberada) return
        liberada = true
        camada.animate().cancel()
        HudOverlayController.registrarEspelhoDoControle(null)
        raiz.removeView(camada)
    }

    private fun texto(tamanhoPx: Float, cor: Int, negrito: Boolean = false) = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, tamanhoPx)
        setTextColor(cor)
        gravity = Gravity.START
        typeface = Typeface.create("sans-serif", if (negrito) Typeface.BOLD else Typeface.NORMAL)
        includeFontPadding = false
    }

    private fun pilula(rotulo: String) = texto(20f, Color.WHITE).apply {
        text = rotulo
        setPadding(22, 12, 22, 12)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.argb(30, 255, 255, 255))
            cornerRadius = 40f
            setStroke(1, Color.argb(56, 255, 255, 255))
        }
    }
}
// Copyright © 2026 Miguel Vespermann Araújo. All rights reserved. Vespermann Accessible Software License (VASL) 1.0 — forks/redistribution not permitted without written authorization; see LICENSE.
