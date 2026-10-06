package br.com.deepgwt.tela3d.faixa

import kotlin.math.max
import kotlin.math.roundToInt

// Reservas e margens de tela da faixa (F28 e F29). Funções puras: só números.

/** Medidas fixas da faixa, em dp (= px no carro, densidade 1,0). */
object MedidasDaFaixa {
    const val ALTURA_DA_FAIXA_DP = 148
    const val FOLGA_INFERIOR_DP = 60
    const val FOLGA_DO_CROMO_DP = 80
    const val RESERVA_DO_CROMO_NO_TOPO_DP = 28
    const val TRILHO_LATERAL_MINIMO_DP = 128
    const val ALTURA_DA_FAIXA_MAIS_FOLGA_DP = 16
    const val FOLGA_SUPERIOR_DA_PAGINA_DP = 22
    const val FOLGA_DO_POPUP_DP = 12
}

/** dp para px, arredondado ao inteiro mais próximo. */
fun dpParaPx(dp: Float, densidade: Float): Int = (dp * densidade).roundToInt()

/** Overscan inferior: o que a tela real tem abaixo da página. */
fun overscanInferiorPx(m: MedidasDaTela): Int = max(0, m.alturaRealPx - m.paginaBase)

/** Margem [valorPx] menos o overscan (nunca negativa): a barra inferior já ocupa esse espaço. */
fun margemInferiorCompensadaPx(m: MedidasDaTela, valorPx: Int): Int = max(0, valorPx - overscanInferiorPx(m))

/** Trilho lateral reservado: o maior entre a dimensão do sistema, o maior inset visto e 128 dp. */
fun trilhoLateralPx(m: MedidasDaTela): Int = max(
    max(m.larguraDoTrilhoDimenPx, m.maiorInsetEsquerdoPx),
    dpParaPx(MedidasDaFaixa.TRILHO_LATERAL_MINIMO_DP.toFloat(), m.densidade),
)

/** Inset da barra de status sobre a página; 0 se a barra não está visível. */
fun insetSuperiorDaPaginaPx(m: MedidasDaTela): Int =
    if (m.barraStatusVisivel) max(0, m.alturaBarraStatusPx - max(0, m.paginaTopo)) else 0

/** F28 e F29: tudo que a faixa reserva, em pixels e em px CSS. */
fun calcularReservas(m: MedidasDaTela): ReservasDaFaixa {
    val d = m.densidade
    val altura = dpParaPx(MedidasDaFaixa.ALTURA_DA_FAIXA_DP.toFloat(), d)
    val folga = dpParaPx(MedidasDaFaixa.FOLGA_INFERIOR_DP.toFloat(), d)
    val folgaDoCromo = dpParaPx(MedidasDaFaixa.FOLGA_DO_CROMO_DP.toFloat(), d)
    val trilho = trilhoLateralPx(m)
    val insetEsq = max(0, trilho - max(0, m.paginaEsq))
    val insetDir = max(0, trilho - max(0, m.larguraRealPx - m.paginaDir))
    val reservaDoDock = if (m.modo == ModoDaCasca.LADO_A_LADO) {
        0
    } else {
        altura + folga + dpParaPx(MedidasDaFaixa.ALTURA_DA_FAIXA_MAIS_FOLGA_DP.toFloat(), d)
    }
    return ReservasDaFaixa(
        alturaDaFaixaPx = altura,
        margemInferiorPx = margemInferiorCompensadaPx(m, folga),
        alturaDaBandaPx = margemInferiorCompensadaPx(m, folga),
        insetEsquerdoPx = insetEsq,
        insetDireitoPx = insetDir,
        reservaDoDockPx = reservaDoDock,
        safeTopCss = insetSuperiorDaPaginaPx(m) / d + MedidasDaFaixa.FOLGA_SUPERIOR_DA_PAGINA_DP,
        safeBottomCss = margemInferiorCompensadaPx(m, reservaDoDock) / d,
        launcherBottomCss = margemInferiorCompensadaPx(m, folgaDoCromo) / d,
        safeLeftCss = insetEsq / d,
        safeRightCss = insetDir / d,
    )
}

/**
 * F29: `popupTop` e `popupBottom` em px CSS. [topoDaFaixaNaTelaPx] nulo = a faixa ainda não foi
 * medida (então o fundo é a altura da página).
 */
fun limitesDosPopupsCss(m: MedidasDaTela, topoDaFaixaNaTelaPx: Int?): Pair<Float, Float> {
    val d = m.densidade
    val topoPx = max(0, max(m.alturaBarraStatusPx, m.maiorInsetSuperiorPx) - m.paginaTopo)
    val topo = topoPx / d
    val fundo = if (topoDaFaixaNaTelaPx != null) {
        max(topo, (topoDaFaixaNaTelaPx - m.paginaTopo) / d - MedidasDaFaixa.FOLGA_DO_POPUP_DP)
    } else {
        (m.paginaBase - m.paginaTopo) / d
    }
    return topo to fundo
}
