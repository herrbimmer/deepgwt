package br.com.deepgwt.tela3d.faixa

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Reflexo de uma placa de ícone sobre a prateleira do estilo 3D: a própria view espelhada abaixo dela e
 * esmaecida até sumir. Desenhado no [Canvas] da view-mãe (que não recorta os filhos).
 */
internal object ReflexoDoDock {
    /** Altura do reflexo, em dp. */
    const val ALTURA_DP = 24

    /** Alfa do reflexo junto à placa (0 a 255). */
    private const val ALFA_INICIAL = 92

    private val pincel = Paint()
    private val modo = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    private val caminho = Path()
    private val retangulo = RectF()

    /**
     * Desenha o reflexo de [alvo] (filho direto da view que está no [canvas]). [raioPx] é o raio dos
     * cantos da placa; [alturaPx] a altura do reflexo; [folgaPx] a distância entre a placa e o reflexo.
     */
    fun desenhar(canvas: Canvas, alvo: View, raioPx: Float, alturaPx: Int, folgaPx: Int) {
        val l = alvo.width
        val a = alvo.height
        if (l <= 0 || a <= 0 || alvo.visibility != View.VISIBLE) return
        val esq = alvo.left.toFloat()
        val inicio = alvo.bottom.toFloat() + folgaPx
        val fim = inicio + alturaPx
        val camada = canvas.saveLayer(esq, inicio, esq + l, fim, null)
        canvas.save()
        // Espelha em torno da linha `inicio`: um ponto local y vai para 2·inicio − topo − y.
        canvas.translate(esq, 2f * inicio - alvo.top)
        canvas.scale(1f, -1f)
        retangulo.set(0f, 0f, l.toFloat(), a.toFloat())
        caminho.rewind()
        caminho.addRoundRect(retangulo, raioPx, raioPx, Path.Direction.CW)
        canvas.clipPath(caminho)
        alvo.draw(canvas)
        canvas.restore()
        pincel.shader = LinearGradient(0f, inicio, 0f, fim, ALFA_INICIAL shl 24 or 0xFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        pincel.xfermode = modo
        canvas.drawRect(esq, inicio, esq + l, fim, pincel)
        pincel.shader = null
        pincel.xfermode = null
        canvas.restoreToCount(camada)
    }
}
