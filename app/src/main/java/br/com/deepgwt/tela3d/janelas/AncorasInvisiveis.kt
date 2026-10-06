package br.com.deepgwt.tela3d.janelas

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/**
 * J9: dois retângulos transparentes e invisíveis, atrás da WebView (índice 0 do conteiner raiz, sem foco
 * nem toque), um por lado, com o tamanho e a posição do retângulo do lado em coordenadas da janela
 * (tela − origem da página). Servem só de origem da animação "clip reveal" ao abrir uma janela.
 */
internal class AncorasInvisiveis(
    private val conteiner: FrameLayout,
    private val larguraDasMetricas: () -> Int,
) {
    private val esquerda: View = criar(TAMANHO_INICIAL_LARGURA, TAMANHO_INICIAL_ALTURA, 48, 100)
    private val direita: View = criar(TAMANHO_INICIAL_LARGURA, TAMANHO_INICIAL_ALTURA, 1180, 100)

    init {
        // Depois da esquerda no índice 0, a direita também no 0: as duas ficam atrás de tudo.
        conteiner.addView(esquerda, 0)
        conteiner.addView(direita, 0)
    }

    private fun criar(largura: Int, altura: Int, x: Int, y: Int): View =
        View(conteiner.context).apply {
            setBackgroundColor(0)
            visibility = View.INVISIBLE
            isFocusable = false
            isClickable = false
            layoutParams = FrameLayout.LayoutParams(largura, altura, Gravity.TOP or Gravity.START).also {
                it.leftMargin = x
                it.topMargin = y
            }
        }

    /** Reposiciona as duas âncoras sobre os retângulos dos lados (px de tela), descontada a [origem] da página. */
    fun posicionar(retanguloEsquerdo: Retangulo, retanguloDireito: Retangulo, origem: Retangulo) {
        ajustar(esquerda, retanguloEsquerdo, origem)
        ajustar(direita, retanguloDireito, origem)
    }

    private fun ajustar(vista: View, r: Retangulo, origem: Retangulo) {
        val parametros = (vista.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(r.largura, r.altura, Gravity.TOP or Gravity.START)
        parametros.width = maxOf(r.largura, 0)
        parametros.height = maxOf(r.altura, 0)
        parametros.leftMargin = r.esquerda - origem.esquerda
        parametros.topMargin = r.topo - origem.topo
        vista.layoutParams = parametros
    }

    /**
     * A âncora que serve de origem da animação para este retângulo (esquerda se o retângulo começa antes
     * da metade da tela; senão direita); null se ela ainda não foi medida (largura ou altura 0).
     */
    fun paraRetangulo(r: Retangulo?): View? {
        val lado = CalculoDeGeometria.ladoDaAncora(r, larguraDasMetricas())
        val vista = if (lado == Lado.ESQUERDA) esquerda else direita
        return if (vista.width > 0 && vista.height > 0) vista else null
    }

    fun remover() {
        conteiner.removeView(esquerda)
        conteiner.removeView(direita)
    }

    private companion object {
        const val TAMANHO_INICIAL_LARGURA = 692
        const val TAMANHO_INICIAL_ALTURA = 430
    }
}
