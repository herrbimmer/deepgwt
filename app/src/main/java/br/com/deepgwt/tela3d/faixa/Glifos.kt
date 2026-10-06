package br.com.deepgwt.tela3d.faixa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max

/**
 * Glifos desenhados em bitmaps quadrados de lado `s` (A6): antialias, traço de pontas e junções
 * arredondadas. Medidas como fração de `s`; a largura do traço é `max(1,75 px, s × k)`.
 */
object Glifos {
    private const val TRACO_MINIMO_PX = 1.75f

    private fun novo(s: Int): Pair<Bitmap, Canvas> {
        val lado = max(1, s)
        val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        return bitmap to Canvas(bitmap)
    }

    private fun caneta(cor: Int, traco: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = cor
        strokeWidth = traco
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun tracoDe(s: Int, k: Float): Float = max(TRACO_MINIMO_PX, s * k)

    /** Grade 2×2 de quadradinhos vazados: a ação "Apps" (k = 0,05). */
    fun apps(s: Int, cor: Int): Bitmap {
        val (bitmap, tela) = novo(s)
        val traco = tracoDe(s, 0.05f)
        val p = caneta(cor, traco)
        val margem = traco / 2f + 0.5f
        val vao = 0.14f * s
        val lado = (s - 2f * margem - vao) / 2f
        val raio = 0.08f * s
        for (linha in 0..1) {
            for (coluna in 0..1) {
                val esq = margem + coluna * (lado + vao)
                val topo = margem + linha * (lado + vao)
                tela.drawRoundRect(RectF(esq, topo, esq + lado, topo + lado), raio, raio, p)
            }
        }
        return bitmap
    }

    /** Mosaico de 3 retângulos vazados: a ação "Layout" (k = 0,05). */
    fun layout(s: Int, cor: Int): Bitmap {
        val (bitmap, tela) = novo(s)
        val traco = tracoDe(s, 0.05f)
        val p = caneta(cor, traco)
        val margem = traco / 2f + 0.5f
        val raio = 0.08f * s
        val larguraEsq = 0.42f * s
        val vaoVertical = 0.12f * s
        val esqDireita = margem + larguraEsq + 0.12f * s
        val alturaDireita = (s - 2f * margem - vaoVertical) / 2f
        tela.drawRoundRect(RectF(margem, margem, margem + larguraEsq, s - margem), raio, raio, p)
        tela.drawRoundRect(RectF(esqDireita, margem, s - margem, margem + alturaDireita), raio, raio, p)
        tela.drawRoundRect(RectF(esqDireita, s - margem - alturaDireita, s - margem, s - margem), raio, raio, p)
        return bitmap
    }

    /** Dois retângulos cheios lado a lado (o direito com alfa 190): o item "Side by Side". */
    fun ladoALado(s: Int, cor: Int): Bitmap {
        val (bitmap, tela) = novo(s)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val raio = 0.05f * s
        val largura = (1f - 0.4f - 0.07f) / 2f
        val topo = 0.27f * s
        val base = (1f - 0.27f) * s
        p.color = cor
        tela.drawRoundRect(RectF(0.20f * s, topo, (0.20f + largura) * s, base), raio, raio, p)
        p.color = CoresDoCromo.comAlfa(cor, 190)
        tela.drawRoundRect(RectF((0.20f + largura + 0.07f) * s, topo, (0.20f + 2 * largura + 0.07f) * s, base), raio, raio, p)
        return bitmap
    }

    /** Seta para a esquerda do `Return`: grade 24×24 escalada para o lado [s]. */
    fun setaEsquerda(s: Int, cor: Int): Bitmap {
        val (bitmap, tela) = novo(s)
        val escala = s / 24f
        val p = caneta(cor, s * 2.2f / 24f)
        tela.drawLine(19f * escala, 12f * escala, 5f * escala, 12f * escala, p)
        val ponta = Path()
        ponta.moveTo(11f * escala, 6f * escala)
        ponta.lineTo(5f * escala, 12f * escala)
        ponta.lineTo(11f * escala, 18f * escala)
        tela.drawPath(ponta, p)
        return bitmap
    }
}
