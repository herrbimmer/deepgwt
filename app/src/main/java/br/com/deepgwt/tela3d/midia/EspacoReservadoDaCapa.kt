package br.com.deepgwt.tela3d.midia

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import kotlin.math.max
import kotlin.math.roundToInt

/** Medidas do espaço reservado da capa do cartão de mídia (seção "Aparência"). Puro. */
internal object GeometriaDoEspacoReservado {
    const val LADO_BASE_DP = 48f
    const val LADO_MINIMO_PX = 16
    const val RAIO_DO_ANEL = 0.375f
    const val ESPESSURA_MINIMA_PX = 1.5f
    const val ESPESSURA_RELATIVA = 0.062f
    const val RAIO_DO_PONTO = 0.1f

    /** Alfa de 40 %: 102/255. */
    const val ALFA = 102

    fun lado(densidade: Float): Int = max(LADO_MINIMO_PX, (LADO_BASE_DP * densidade).roundToInt())

    fun raioDoAnel(lado: Int): Float = RAIO_DO_ANEL * lado

    fun espessuraDoAnel(lado: Int): Float = max(ESPESSURA_MINIMA_PX, ESPESSURA_RELATIVA * lado)

    fun raioDoPonto(lado: Int): Float = RAIO_DO_PONTO * lado
}

/**
 * Desenha o espaço reservado: um anel e um ponto no centro, na cor do rótulo do dock do tema com
 * 40 % de opacidade. Refazer quando o tema (claro/escuro) muda.
 */
object EspacoReservadoDaCapa {
    /** @param corDoRotulo cor do rótulo do dock (ARGB); o alfa é substituído por 102/255. */
    fun criar(densidade: Float, corDoRotulo: Int): Bitmap {
        val lado = GeometriaDoEspacoReservado.lado(densidade)
        val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val tela = Canvas(bitmap)
        val cor = ColorUtils.setAlphaComponent(corDoRotulo, GeometriaDoEspacoReservado.ALFA)
        val centro = lado / 2f
        val anel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cor
            style = Paint.Style.STROKE
            strokeWidth = GeometriaDoEspacoReservado.espessuraDoAnel(lado)
        }
        tela.drawCircle(centro, centro, GeometriaDoEspacoReservado.raioDoAnel(lado), anel)
        val ponto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cor
            style = Paint.Style.FILL
        }
        tela.drawCircle(centro, centro, GeometriaDoEspacoReservado.raioDoPonto(lado), ponto)
        return bitmap
    }
}
