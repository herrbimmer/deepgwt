package br.com.deepgwt.tela3d.faixa

import android.content.Context
import android.graphics.Outline
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView

/** Pequenos ajudantes de estilo das views da faixa. */
internal object Estilos {
    val MEDIO: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val NORMAL: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    /** Texto com tamanho em sp (escala de fonte do sistema), cor e peso. */
    fun texto(contexto: Context, sp: Float, cor: Int, medio: Boolean = false): TextView = TextView(contexto).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(cor)
        typeface = if (medio) MEDIO else NORMAL
        includeFontPadding = false
    }

    /** Recorte e sombra com cantos arredondados de [raioPx]. */
    fun arredondar(vista: View, raioPx: Float, recortar: Boolean = false) {
        vista.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, contorno: Outline) {
                contorno.setRoundRect(0, 0, v.width, v.height, raioPx)
            }
        }
        vista.clipToOutline = recortar
    }

    fun circular(vista: View) {
        vista.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, contorno: Outline) {
                contorno.setOval(0, 0, v.width, v.height)
            }
        }
    }
}
