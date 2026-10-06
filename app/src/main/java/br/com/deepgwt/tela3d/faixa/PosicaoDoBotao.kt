package br.com.deepgwt.tela3d.faixa

import kotlin.math.max

/** Posição de uma janela do botão flutuante, em px de tela (canto superior esquerdo). */
data class PontoEmPx(val x: Int, val y: Int)

/** Área em px onde o botão pode ficar (a página, acima da barra inferior). */
data class AreaDoBotao(val esq: Int, val topo: Int, val dir: Int, val base: Int)

/** Posição, limites e arrasto do botão flutuante de apps (F32), tudo puro. */
object PosicaoDoBotao {
    const val LADO_DP = 72
    const val FOLGA_INFERIOR_DP = 24
    const val LARGURA_DO_MENU_DP = 300
    const val FOLGA_DO_MENU_DP = 10
    const val DISTANCIA_DE_ARRASTO_PX = 8

    /** Posição de partida: centralizado na tela e a 24 do fundo. */
    fun padrao(larguraDaTela: Int, alturaDaTela: Int, ladoPx: Int, folgaInferiorPx: Int): PontoEmPx =
        PontoEmPx((larguraDaTela - ladoPx) / 2, alturaDaTela - ladoPx - folgaInferiorPx)

    /** Mantém a janela inteira dentro de [area]. */
    fun limitar(x: Int, y: Int, larguraDaJanela: Int, alturaDaJanela: Int, area: AreaDoBotao): PontoEmPx {
        val maxX = max(area.esq, area.dir - larguraDaJanela)
        val maxY = max(area.topo, area.base - alturaDaJanela)
        return PontoEmPx(x.coerceIn(area.esq, maxX), y.coerceIn(area.topo, maxY))
    }

    /** O arrasto começa quando o deslocamento ao quadrado passa de 8 × 8. */
    fun comecouArrasto(dx: Int, dy: Int): Boolean =
        dx.toLong() * dx + dy.toLong() * dy > DISTANCIA_DE_ARRASTO_PX.toLong() * DISTANCIA_DE_ARRASTO_PX

    /**
     * Posição da janela com o menu aberto (D9): o botão não se mexe. O menu fica acima e centrado
     * sobre ele: a janela sobe `alturaDoMenu + folga` e vai para a esquerda por metade da diferença
     * de largura.
     */
    fun janelaComMenu(
        botao: PontoEmPx,
        ladoDoBotaoPx: Int,
        larguraDoMenuPx: Int,
        alturaDoMenuPx: Int,
        folgaDoMenuPx: Int,
    ): PontoEmPx = PontoEmPx(
        x = botao.x - (larguraDoMenuPx - ladoDoBotaoPx) / 2,
        y = botao.y - alturaDoMenuPx - folgaDoMenuPx,
    )
}
