package br.com.deepgwt.tela3d.faixa

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.deepgwt.tela3d.catalogo.PlacaDoItem

/**
 * Um item da fileira de apps: placa de ícone de 70×70 e o rótulo de até duas linhas, numa coluna de
 * 84 de largura. Clicável e focável, sem nenhum feedback visual de toque. Durante a primeira
 * revelação desenha o rastro de movimento (A8).
 */
class ItemDaFileira(
    contexto: Context,
    /** Pacote do app ou a chave de um dos itens especiais. */
    val chave: String,
    private val tema: TemaDoCromoPadrao,
) : LinearLayout(contexto) {
    private val d = Densidade(contexto)
    private val placa = FrameLayout(contexto)
    private val imagem = ImageView(contexto)
    private val legenda: TextView

    /** Texto mostrado no rótulo (vazio = sem rótulo). */
    var rotulo: String = ""
        private set

    private var placaAtual: PlacaDoItem = PlacaDoItem.Clara

    private var comReflexo = false

    /** Estilo 3D: reflexo sob a placa; o rótulo desce para dar lugar a ele. */
    fun definirReflexo(ativo: Boolean) {
        if (ativo == comReflexo) return
        comReflexo = ativo
        val extra = if (ativo) d.px(ReflexoDoDock.ALTURA_DP) else 0
        legenda.setPadding(d.px(2), d.px(4) + extra, d.px(2), 0)
        invalidate()
    }

    /** Rastro de movimento em px; 0 fora da revelação. */
    var rastroPx: Float = 0f
        set(valor) {
            if (field == valor) return
            field = valor
            invalidate()
        }

    init {
        orientation = VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
        // O item não tem feedback de toque: nada de ondulação, escala ou alfa de pressão.
        background = null

        val lado = d.px(LADO_DA_PLACA_DP)
        placa.elevation = d.px(ELEVACAO_DA_PLACA_DP).toFloat()
        Estilos.arredondar(placa, d.px(RAIO_DA_PLACA_DP).toFloat(), recortar = true)
        imagem.scaleType = ImageView.ScaleType.CENTER_CROP
        placa.addView(imagem, FrameLayout.LayoutParams(lado, lado))
        addView(placa, LayoutParams(lado, lado).apply { gravity = Gravity.CENTER_HORIZONTAL })

        legenda = Estilos.texto(contexto, 11f, CoresDoCromo.ROTULO_DO_ITEM).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setLines(2)
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 0.92f)
            setShadowLayer(d.pxF(3f), 0f, d.pxF(1f), CoresDoCromo.SOMBRA_DO_ROTULO)
            setPadding(d.px(2), d.px(4), d.px(2), 0)
        }
        addView(legenda, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Preenche o item: rótulo, ícone (já normalizado) e placa. */
    fun vincular(rotulo: String, icone: Drawable?, placa: PlacaDoItem, descricao: String? = null) {
        this.rotulo = rotulo
        legenda.text = rotulo
        legenda.visibility = if (rotulo.isEmpty()) View.GONE else View.VISIBLE
        contentDescription = descricao ?: rotulo
        imagem.setImageDrawable(icone)
        placaAtual = placa
        atualizarPlaca()
    }

    /** Troca só o ícone (ex.: a projeção mudou de tipo). */
    fun definirIcone(icone: Drawable?) {
        imagem.setImageDrawable(icone)
    }

    /** Repinta a placa com o tema atual. */
    fun atualizarPlaca() {
        this.placa.background = tema.placaDeIcone(placaAtual)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (comReflexo) ReflexoDoDock.desenhar(canvas, placa, d.pxF(RAIO_DA_PLACA_DP.toFloat()), d.px(ReflexoDoDock.ALTURA_DP), d.px(2))
        val p = rastroPx
        if (p > RevelacaoDaFileira.LIMIAR_DO_RASTRO) {
            for (k in RevelacaoDaFileira.COPIAS_DO_RASTRO downTo 1) {
                val alfa = RevelacaoDaFileira.alfaDaCopiaDoRastro(k)
                if (alfa < RevelacaoDaFileira.ALFA_MINIMO_DA_COPIA) continue
                val marca = canvas.saveLayerAlpha(-p, 0f, width.toFloat(), height.toFloat(), alfa)
                canvas.translate(-p * RevelacaoDaFileira.fracaoDaCopia(k), 0f)
                super.dispatchDraw(canvas)
                canvas.restoreToCount(marca)
            }
        }
        super.dispatchDraw(canvas)
    }

    companion object {
        const val LARGURA_DP = 84
        const val MARGEM_DIREITA_DP = 12
        const val LADO_DA_PLACA_DP = 70
        const val RAIO_DA_PLACA_DP = 14
        const val ELEVACAO_DA_PLACA_DP = 7
    }
}
