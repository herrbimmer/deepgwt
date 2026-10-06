package br.com.deepgwt.tela3d.faixa

import android.content.Context
import kotlin.math.roundToInt

/** Conversão de dp para px com a densidade da tela (arredonda ao inteiro mais próximo). */
class Densidade(val valor: Float) {
    constructor(contexto: Context) : this(contexto.resources.displayMetrics.density)

    fun px(dp: Int): Int = (dp * valor).roundToInt()
    fun px(dp: Float): Int = (dp * valor).roundToInt()

    /** Sem arredondar: para raios e larguras de traço que aceitam fração. */
    fun pxF(dp: Float): Float = dp * valor

    /** Espessura de contorno de 1 dp: nunca menor que 1 px. */
    fun contorno(): Int = maxOf(1, valor.roundToInt())
}
