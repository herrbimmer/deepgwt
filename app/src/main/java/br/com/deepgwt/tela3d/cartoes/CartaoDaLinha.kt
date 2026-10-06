package br.com.deepgwt.tela3d.cartoes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import br.com.deepgwt.tela3d.faixa.Densidade
import br.com.deepgwt.tela3d.faixa.TemaDoCromo
import br.com.deepgwt.tela3d.ponte.CartaoInferior

/** Medidas dos selos do modo de edição (AP6), em dp. */
internal object GeometriaDosSelos {
    const val LADO_DO_X = 26f
    const val LADO_DOS_PONTOS = 30f
    const val MARGEM = 4f
    const val FOLGA_DE_TOQUE = 10f

    /** Caixa do selo "×": canto superior direito. [esq, topo, dir, base] em dp. */
    fun caixaDoX(largura: Float): FloatArray = floatArrayOf(largura - LADO_DO_X - MARGEM, MARGEM, largura - MARGEM, MARGEM + LADO_DO_X)

    /** Caixa do selo "…": canto inferior direito. */
    fun caixaDosPontos(largura: Float, altura: Float): FloatArray =
        floatArrayOf(largura - LADO_DOS_PONTOS - MARGEM, altura - LADO_DOS_PONTOS - MARGEM, largura - MARGEM, altura - MARGEM)
}

/**
 * Base de todo cartão da linha: vidro fosco, elevação, foco, e a sobreposição dos selos da edição
 * desenhada por cima dos filhos. Em edição o cartão intercepta todos os toques e os entrega ao gesto.
 */
internal abstract class CartaoDaLinha(
    contexto: Context,
    protected val anfitriao: AnfitriaoDosCartoes,
    protected val d: Densidade,
) : FrameLayout(contexto) {
    /** Descritor atual da página; nulo nos cartões do conjunto de reserva. */
    open val descritor: CartaoInferior? get() = null

    /** Largura em dp, para as contas do arrasto. */
    abstract val larguraEmDp: Int

    /** O cartão veio de `bottomCards` (entra na edição). */
    var configurado: Boolean = false

    var emEdicao: Boolean = false
        private set

    private var gesto: ((MotionEvent) -> Boolean)? = null
    private val tintaDoSelo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val caixa = RectF()

    protected val tema: TemaDoCromo get() = anfitriao.tema

    init {
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false
        elevation = d.pxF(MedidasDaLinha.ELEVACAO_DP.toFloat())
        val raio = d.pxF(MedidasDaLinha.RAIO_DOS_CANTOS_DP.toFloat())
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(vista: View, contorno: Outline) {
                contorno.setRoundRect(0, 0, vista.width, vista.height, raio)
            }
        }
    }

    protected fun aplicarFundo(lavagem: Int) {
        background = tema.materialVidroFosco(false, lavagem)
    }

    /** Refaz tudo o que depende do tema (fundo, cores, gráficos). Nunca reconstrói. */
    abstract fun reaplicarTema()

    // --- edição ---

    fun ligarEdicao(gestoDeToque: (MotionEvent) -> Boolean) {
        emEdicao = true
        gesto = gestoDeToque
        isClickable = false
        isLongClickable = false
        desabilitarCliquesDosFilhos(this)
        invalidate()
    }

    private fun desabilitarCliquesDosFilhos(pai: ViewGroup) {
        for (i in 0 until pai.childCount) {
            val f = pai.getChildAt(i)
            f.isClickable = false
            f.isLongClickable = false
            if (f is ViewGroup) desabilitarCliquesDosFilhos(f)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = emEdicao || super.onInterceptTouchEvent(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (emEdicao) return gesto?.invoke(ev) ?: true
        return super.onTouchEvent(ev)
    }

    /** O ponto (coordenadas do cartão) está no selo "×" (com a folga de 10 dp)? */
    fun noSeloDeRemover(x: Float, y: Float): Boolean {
        val c = GeometriaDosSelos.caixaDoX(width / d.valor)
        val f = GeometriaDosSelos.FOLGA_DE_TOQUE * d.valor
        return GeometriaDeArrasto.dentro(x, y, c[0] * d.valor, c[1] * d.valor, c[2] * d.valor, c[3] * d.valor, f)
    }

    fun temSeloDeMenu(): Boolean = descritor?.menuDeEdicao?.isNotEmpty() == true

    fun noSeloDeMenu(x: Float, y: Float): Boolean {
        if (!temSeloDeMenu()) return false
        val c = GeometriaDosSelos.caixaDosPontos(width / d.valor, height / d.valor)
        val f = GeometriaDosSelos.FOLGA_DE_TOQUE * d.valor
        return GeometriaDeArrasto.dentro(x, y, c[0] * d.valor, c[1] * d.valor, c[2] * d.valor, c[3] * d.valor, f)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!emEdicao || width == 0) return
        val v = d.valor
        // "×"
        val x = GeometriaDosSelos.caixaDoX(width / v)
        caixa.set(x[0] * v, x[1] * v, x[2] * v, x[3] * v)
        val raioDoX = caixa.width() / 2f
        tintaDoSelo.style = Paint.Style.FILL
        tintaDoSelo.color = CoresDosCartoes.SELO
        canvas.drawCircle(caixa.centerX(), caixa.centerY(), raioDoX, tintaDoSelo)
        tintaDoSelo.style = Paint.Style.STROKE
        tintaDoSelo.color = 0xFFFFFFFF.toInt()
        tintaDoSelo.strokeWidth = 2f * v
        tintaDoSelo.strokeCap = Paint.Cap.ROUND
        val meia = 0.36f * raioDoX
        canvas.drawLine(caixa.centerX() - meia, caixa.centerY() - meia, caixa.centerX() + meia, caixa.centerY() + meia, tintaDoSelo)
        canvas.drawLine(caixa.centerX() - meia, caixa.centerY() + meia, caixa.centerX() + meia, caixa.centerY() - meia, tintaDoSelo)
        // "…"
        if (temSeloDeMenu()) {
            val p = GeometriaDosSelos.caixaDosPontos(width / v, height / v)
            caixa.set(p[0] * v, p[1] * v, p[2] * v, p[3] * v)
            val raio = caixa.width() / 2f
            tintaDoSelo.style = Paint.Style.FILL
            tintaDoSelo.color = CoresDosCartoes.SELO
            canvas.drawCircle(caixa.centerX(), caixa.centerY(), raio, tintaDoSelo)
            tintaDoSelo.color = 0xFFFFFFFF.toInt()
            val raioDoPonto = maxOf(1.5f * v, 0.12f * raio)
            for (k in -1..1) canvas.drawCircle(caixa.centerX() + k * 0.42f * raio, caixa.centerY(), raioDoPonto, tintaDoSelo)
        }
    }
}
