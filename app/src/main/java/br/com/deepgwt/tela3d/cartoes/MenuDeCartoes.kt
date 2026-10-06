package br.com.deepgwt.tela3d.cartoes

import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import br.com.deepgwt.tela3d.faixa.Densidade
import br.com.deepgwt.tela3d.ponte.LinhaDeMenu

/**
 * O popup do menu rápido dos cartões de modo e do menu "…" da edição (AP7). Só um aberto de cada vez;
 * toque fora fecha; escolher uma linha fecha e entrega o comando.
 */
internal class MenuDeCartoes(
    private val anfitriao: AnfitriaoDosCartoes,
    private val d: Densidade,
) {
    private var janela: PopupWindow? = null

    val aberto: Boolean get() = janela?.isShowing == true

    fun fechar() {
        val j = janela ?: return
        janela = null
        if (j.isShowing) j.dismiss()
    }

    /**
     * @param separarUltima a última linha vem separada por um divisor (menu rápido dos modos); no menu "…" não.
     */
    fun abrir(ancora: View, linhas: List<LinhaDeMenu>, separarUltima: Boolean, aoEscolher: (String) -> Unit) {
        fechar()
        if (linhas.isEmpty() || !ancora.isAttachedToWindow) return
        val tema = anfitriao.tema
        val contexto = anfitriao.contexto
        val painel = LinearLayout(contexto).apply {
            orientation = LinearLayout.VERTICAL
            background = tema.painelDePopup()
            setPadding(d.px(6), d.px(6), d.px(6), d.px(6))
        }
        linhas.forEachIndexed { i, linha ->
            val separada = separarUltima && i == linhas.lastIndex
            if (separada && linhas.size > 1) {
                val divisor = View(contexto).apply { setBackgroundColor(CoresDosCartoes.divisorDoMenu(tema.claro)) }
                painel.addView(divisor, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, d.contorno()).apply {
                    topMargin = d.px(2)
                    bottomMargin = d.px(8)
                })
            }
            val texto = TextView(contexto).apply {
                text = linha.rotulo
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (separada) 13f else 15f)
                typeface = if (linha.selecionada) Fontes.MEDIO else Fontes.NORMAL
                setTextColor(
                    when {
                        linha.selecionada -> tema.acento
                        separada -> tema.corDeRotuloMuted
                        else -> tema.corDeRotulo
                    },
                )
                setPadding(d.px(12), d.px(12), d.px(12), d.px(12))
                // Defeito 7 do original: o texto da última linha era cortado sem reticências; aqui cabe em
                // até duas linhas e, se ainda sobrar, termina em reticências.
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                background = tema.fundoDeItemDeMenu(linha.selecionada, separada)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    fechar()
                    aoEscolher(linha.comando)
                }
            }
            painel.addView(texto, LinearLayout.LayoutParams(d.px(200), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = d.px(6)
            })
        }
        painel.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val largura = painel.measuredWidth
        val altura = painel.measuredHeight
        val popup = PopupWindow(painel, largura, altura, true)
        popup.setBackgroundDrawable(ColorDrawable(0))
        popup.isOutsideTouchable = true
        popup.elevation = d.pxF(12f)
        popup.setOnDismissListener { if (janela === popup) janela = null }
        janela = popup
        val onde = IntArray(2)
        ancora.getLocationOnScreen(onde)
        val y = onde[1] - d.px(8) - altura
        popup.showAtLocation(ancora, Gravity.NO_GRAVITY, onde[0], y)
    }
}
