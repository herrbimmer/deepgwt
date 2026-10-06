package br.com.deepgwt.tela3d.ponte

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * A camada nativa de carga: linha de progresso, spinner de centro, botão SKIP e o quadro preto do
 * fade. As medidas estão em [MedidasDaCamadaDeCarga]; as transições, em [MaquinaDoHud].
 * Só a thread principal mexe aqui.
 *
 * Ordem na raiz (de baixo para cima): trilha, spinner, SKIP, quadro de fade. A UI nativa da faixa vai
 * por cima de tudo (a Activity a adiciona depois).
 */
class CamadaDeCarga(
    private val contexto: Context,
    private val aoPular: () -> Unit,
) {
    private val densidade = contexto.resources.displayMetrics.density
    private val larguraDaTela = contexto.resources.displayMetrics.widthPixels

    private fun dp(valor: Int): Int = Math.round(valor * densidade)

    private var estado = EstadoDoHud.ESCONDIDO
    private var ultimoPct = 0

    private val preenchimento = View(contexto).apply {
        setBackgroundColor(MedidasDaCamadaDeCarga.COR_DO_PREENCHIMENTO)
        layoutParams = FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
    }

    private val trilha = FrameLayout(contexto).apply {
        setBackgroundColor(MedidasDaCamadaDeCarga.COR_DA_TRILHA)
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        elevation = dp(MedidasDaCamadaDeCarga.ELEVACAO_DO_HUD_DP).toFloat()
        addView(preenchimento)
    }

    private val spinner = ProgressBar(contexto).apply {
        isIndeterminate = true
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        elevation = dp(MedidasDaCamadaDeCarga.ELEVACAO_DO_HUD_DP).toFloat()
        @Suppress("DEPRECATION")
        indeterminateDrawable?.setColorFilter(MedidasDaCamadaDeCarga.COR_DO_PREENCHIMENTO, PorterDuff.Mode.SRC_IN)
    }

    private val botaoSkip = TextView(contexto).apply {
        text = MedidasDaCamadaDeCarga.TEXTO_DO_SKIP
        setTextColor(MedidasDaCamadaDeCarga.COR_DO_TEXTO_DO_SKIP)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, MedidasDaCamadaDeCarga.TAMANHO_DO_TEXTO_DO_SKIP_SP)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        letterSpacing = MedidasDaCamadaDeCarga.ESPACAMENTO_DAS_LETRAS_EM
        setPadding(
            dp(MedidasDaCamadaDeCarga.PREENCHIMENTO_HORIZONTAL_DO_SKIP_DP),
            dp(MedidasDaCamadaDeCarga.PREENCHIMENTO_VERTICAL_DO_SKIP_DP),
            dp(MedidasDaCamadaDeCarga.PREENCHIMENTO_HORIZONTAL_DO_SKIP_DP),
            dp(MedidasDaCamadaDeCarga.PREENCHIMENTO_VERTICAL_DO_SKIP_DP),
        )
        setBackgroundColor(MedidasDaCamadaDeCarga.COR_DO_FUNDO_DO_SKIP)
        elevation = dp(MedidasDaCamadaDeCarga.ELEVACAO_DO_SKIP_DP).toFloat()
        isClickable = true
        isFocusable = true
        setOnClickListener {
            esconderSkip()
            aoPular()
        }
    }

    private val quadroDeFade = View(contexto).apply {
        setBackgroundColor(Color.BLACK)
        visibility = View.GONE
        isClickable = false
        isFocusable = false
    }

    /** Põe as quatro Views na raiz, na ordem certa. */
    fun adicionarEm(raiz: FrameLayout, reservaInferiorPx: Int, origemDaPaginaYEmPx: Int) {
        raiz.addView(trilha, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MedidasDaCamadaDeCarga.ALTURA_DA_LINHA_PX, Gravity.TOP or Gravity.START))
        val tamanho = dp(MedidasDaCamadaDeCarga.TAMANHO_DO_SPINNER_DP)
        raiz.addView(spinner, FrameLayout.LayoutParams(tamanho, tamanho, Gravity.CENTER))
        raiz.addView(botaoSkip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))
        raiz.addView(quadroDeFade, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        reposicionar(reservaInferiorPx, origemDaPaginaYEmPx)
    }

    /** Corrige as margens do SKIP e da linha (a faixa e a origem da página podem ter mudado). */
    fun reposicionar(reservaInferiorPx: Int, origemDaPaginaYEmPx: Int) {
        (botaoSkip.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.leftMargin = dp(MedidasDaCamadaDeCarga.MARGEM_ESQUERDA_DO_SKIP_DP)
            it.bottomMargin = MedidasDaCamadaDeCarga.margemInferiorDoSkip(reservaInferiorPx)
            botaoSkip.layoutParams = it
        }
        (trilha.layoutParams as? FrameLayout.LayoutParams)?.let {
            val margem = MedidasDaCamadaDeCarga.margemSuperiorDaLinha(origemDaPaginaYEmPx)
            if (it.topMargin != margem) {
                it.topMargin = margem
                trilha.layoutParams = it
            }
        }
    }

    fun progredir(pct: Int) {
        ultimoPct = MedidasDaCamadaDeCarga.limitarPct(pct)
        estado = MaquinaDoHud.aoProgredir(estado)
        aplicarEstado()
        if (estado.linhaVisivel) {
            val largura = MedidasDaCamadaDeCarga.larguraDoPreenchimento(trilha.width, larguraDaTela, ultimoPct)
            (preenchimento.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.width = largura
                preenchimento.layoutParams = it
            }
        }
    }

    fun posicionar(modo: String?) {
        estado = MaquinaDoHud.aoPosicionar(estado, modo)
        aplicarEstado()
    }

    fun esconder() {
        estado = MaquinaDoHud.aoEsconder()
        aplicarEstado()
    }

    /** Linha e spinner visíveis voltam para a frente (depois de a faixa ser revelada, por exemplo). */
    fun trazerParaFrente() {
        if (estado.linhaVisivel) trilha.bringToFront()
        if (estado.spinnerVisivel) spinner.bringToFront()
    }

    fun esconderSkip() {
        botaoSkip.visibility = View.GONE
    }

    /** O fade de quadro preto: some em `max(200, duracaoMs)` ms, com 48 ms de atraso. */
    fun iniciarFade(duracaoMs: Int, aoTerminar: () -> Unit) {
        esconder()
        esconderSkip()
        quadroDeFade.alpha = 1f
        quadroDeFade.visibility = View.VISIBLE
        quadroDeFade.bringToFront()
        quadroDeFade.animate().cancel()
        quadroDeFade.animate()
            .alpha(0f)
            .setStartDelay(ATRASO_DO_FADE_MS)
            .setDuration(maxOf(DURACAO_MINIMA_DO_FADE_MS, duracaoMs.toLong()))
            .withEndAction {
                quadroDeFade.visibility = View.GONE
                aoTerminar()
            }
            .start()
    }

    fun encerrar() {
        quadroDeFade.animate().cancel()
    }

    private fun aplicarEstado() {
        val linha = estado.linhaVisivel
        val roda = estado.spinnerVisivel
        trilha.visibility = if (linha) View.VISIBLE else View.GONE
        spinner.visibility = if (roda) View.VISIBLE else View.GONE
        if (linha) trilha.bringToFront()
        if (roda) spinner.bringToFront()
    }

    private companion object {
        const val ATRASO_DO_FADE_MS = 48L
        const val DURACAO_MINIMA_DO_FADE_MS = 200L
    }
}
