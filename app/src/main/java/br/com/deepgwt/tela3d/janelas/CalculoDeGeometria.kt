package br.com.deepgwt.tela3d.janelas

import kotlin.math.max
import kotlin.math.min

/**
 * Tudo que a geometria das janelas precisa saber, já em números (J1). Quem monta isto lê o Android
 * (display, recursos, insets da janela); o cálculo em si fica em [CalculoDeGeometria] e roda em
 * teste de JVM.
 *
 * @property larguraReal tamanho real do display da Activity; 0 ou menos = desconhecido
 * @property alturaReal idem
 * @property larguraDasMetricas tamanho das métricas de tela, usado quando o real é desconhecido
 * @property alturaDasMetricas idem
 * @property maiorInsetEsquerdo o maior inset esquerdo já observado (nunca diminui)
 * @property maiorInsetSuperior o maior inset superior já observado
 * @property insetDireito inset direito atual
 * @property insetInferior inset inferior atual
 * @property temInsets a janela raiz já recebeu insets (sem eles a área útil é o display inteiro)
 * @property raizNaTela retângulo da raiz da Activity na tela; null enquanto não foi medida
 * @property topoDaFaixaNaTela topo da faixa de apps na tela; null se a faixa ainda não tem altura
 */
internal class EntradasDeGeometria(
    val larguraReal: Int,
    val alturaReal: Int,
    val larguraDasMetricas: Int,
    val alturaDasMetricas: Int,
    val densidade: Float,
    val alturaDaBarraDeStatus: Int,
    val larguraDoTrilhoDoSistema: Int,
    val maiorInsetEsquerdo: Int,
    val maiorInsetSuperior: Int,
    val insetDireito: Int,
    val insetInferior: Int,
    val temInsets: Boolean,
    val modoDaInterface: ModoDaInterface,
    val raizNaTela: Retangulo?,
    val topoDaFaixaNaTela: Int?,
)

/** As margens e reservas que a página recebe, em pixels de tela (o JSON converte para px CSS). */
internal data class MargensDaPagina(
    val lateralEsquerda: Int,
    val lateralDireita: Int,
    val superior: Int,
    val reservaDoDock: Int,
    val reservaDoDockCompensada: Int,
    val folgaDoChromeCompensada: Int,
    val topoDosPopups: Int,
    val fundoDosPopups: Int,
)

/** Guarda os maiores insets esquerdo e superior já vistos: eles só crescem enquanto a Activity vive. */
internal class ObservadorDeInsets {
    var maiorEsquerdo: Int = 0
        private set
    var maiorSuperior: Int = 0
        private set

    fun observar(esquerdo: Int, superior: Int) {
        if (esquerdo > maiorEsquerdo) maiorEsquerdo = esquerdo
        if (superior > maiorSuperior) maiorSuperior = superior
    }
}

/**
 * Geometria das janelas de lado (J2–J8, J12–J16). Funções puras de [EntradasDeGeometria].
 *
 * Contas com 0,016 / 0,54 / 0,004 / frações de divisão / densidade são em ponto flutuante de 32 bits
 * e arredondam para o inteiro mais próximo com empate para cima (`Math.round`).
 */
internal object CalculoDeGeometria {

    const val ALTURA_MINIMA_DO_LADO = 200
    private const val DISPLAY_LARGURA_DE_REFERENCIA = 1920
    private const val LARGURA_MINIMA_DA_AREA = 320
    private const val ALTURA_MINIMA_DA_AREA = 240

    private const val FAIXA_DE_APPS_DP = 148f
    private const val FOLGA_DA_FAIXA_DP = 60f
    private const val MARGEM_DA_FAIXA_DP = 16f
    private const val RESERVA_SUPERIOR_DP = 28f
    private const val TRILHO_MINIMO_DP = 128f
    private const val FOLGA_DO_CHROME_DP = 80f
    private const val FOLGA_DOS_POPUPS_DP = 12f

    /** Retângulo de referência do lado direito em 1920×720 (J7). */
    private val DIREITO_DE_REFERENCIA = Retangulo(1180, 100, 1872, 530)

    /** Retângulo de referência do lado esquerdo para um modo inalcançável (J8, observação). */
    private val ESQUERDO_DE_REFERENCIA = Retangulo(48, 100, 740, 530)

    private fun arredondar(valor: Float): Int = Math.round(valor)

    private fun densidade(e: EntradasDeGeometria): Float = if (e.densidade <= 0f) 1f else e.densidade

    private fun dp(e: EntradasDeGeometria, valorEmDp: Float): Int = arredondar(valorEmDp * densidade(e))

    private fun larguraDoDisplay(e: EntradasDeGeometria): Int =
        if (e.larguraReal > 0 && e.alturaReal > 0) e.larguraReal else e.larguraDasMetricas

    private fun alturaDoDisplay(e: EntradasDeGeometria): Int =
        if (e.larguraReal > 0 && e.alturaReal > 0) e.alturaReal else e.alturaDasMetricas

    private fun tamanhoRealConhecido(e: EntradasDeGeometria): Boolean =
        larguraDoDisplay(e) > 0 && alturaDoDisplay(e) > 0

    private fun displayInteiro(e: EntradasDeGeometria) = Retangulo(0, 0, larguraDoDisplay(e), alturaDoDisplay(e))

    /** J2: a área utilizável do display. */
    fun areaUtil(e: EntradasDeGeometria): Retangulo {
        val display = displayInteiro(e)
        if (!e.temInsets) return display
        val area = Retangulo(
            display.esquerda + e.maiorInsetEsquerdo,
            display.topo + e.maiorInsetSuperior,
            display.direita - e.insetDireito,
            display.fundo - e.insetInferior,
        )
        return if (area.largura < LARGURA_MINIMA_DA_AREA || area.altura < ALTURA_MINIMA_DA_AREA) display else area
    }

    /** J3: a reserva do trilho de navegação (fica à esquerda na central; a simétrica vale à direita). */
    fun reservaDoTrilho(e: EntradasDeGeometria): Int =
        max(max(e.larguraDoTrilhoDoSistema, e.maiorInsetEsquerdo), dp(e, TRILHO_MINIMO_DP))

    /** J4: reserva do dock (0 em Side by Side; faixa de apps + folga + margem nos outros modos). */
    fun reservaDoDock(e: EntradasDeGeometria, modo: ModoDeTela): Int =
        if (modo == ModoDeTela.DOIS_APPS) 0 else dp(e, FAIXA_DE_APPS_DP) + dp(e, FOLGA_DA_FAIXA_DP) + dp(e, MARGEM_DA_FAIXA_DP)

    /** J4: topo da faixa vertical dos lados. */
    private fun topoDaFaixa(e: EntradasDeGeometria): Int =
        max(e.alturaDaBarraDeStatus, e.maiorInsetSuperior) + dp(e, RESERVA_SUPERIOR_DP)

    /** J4: fundo da faixa vertical dos lados. */
    private fun fundoDaFaixa(e: EntradasDeGeometria, modo: ModoDeTela): Int =
        max(topoDaFaixa(e) + ALTURA_MINIMA_DO_LADO, alturaDoDisplay(e) - reservaDoDock(e, modo))

    /** J5: recorte final de um lado. */
    private fun ajustar(r: Retangulo, area: Retangulo, e: EntradasDeGeometria, modo: ModoDeTela): Retangulo {
        val topo = max(r.topo, topoDaFaixa(e))
        var fundo = min(r.fundo, fundoDaFaixa(e, modo))
        if (fundo - topo < ALTURA_MINIMA_DO_LADO) fundo = topo + ALTURA_MINIMA_DO_LADO
        val trilho = reservaDoTrilho(e)
        val esquerda = max(max(r.esquerda, area.esquerda), trilho)
        val larguraDoDisplay = larguraDoDisplay(e)
        val limiteDireito = if (larguraDoDisplay > 0) larguraDoDisplay - trilho else area.direita
        val direita = min(min(r.direita, area.direita), limiteDireito)
        return Retangulo(esquerda, topo, direita, fundo)
    }

    /** Escala só horizontal de um retângulo de referência 1920×720 (J7). */
    private fun escalarNaHorizontal(ref: Retangulo, area: Retangulo): Retangulo {
        val f = area.largura.toFloat() / DISPLAY_LARGURA_DE_REFERENCIA
        return Retangulo(
            area.esquerda + arredondar(ref.esquerda * f),
            ref.topo,
            area.esquerda + arredondar(ref.direita * f),
            ref.fundo,
        )
    }

    /** J6–J8: o retângulo reservado de um lado, em pixels de tela, existam ou não apps. */
    fun limitesDoLado(e: EntradasDeGeometria, lado: Lado, modo: ModoDeTela, proporcao: ProporcaoDaDivisao): Retangulo {
        val area = areaUtil(e)
        val topo = topoDaFaixa(e)
        val fundo = fundoDaFaixa(e, modo)
        val bruto = when (modo) {
            ModoDeTela.APP_E_CARRO -> when (lado) {
                Lado.ESQUERDA -> Retangulo(
                    area.esquerda + arredondar(area.largura * 0.016f),
                    topo,
                    area.esquerda + arredondar(area.largura * 0.54f),
                    fundo,
                )
                Lado.DIREITA -> escalarNaHorizontal(DIREITO_DE_REFERENCIA, area)
            }
            ModoDeTela.DOIS_APPS -> {
                val g = max(6, arredondar(area.largura * 0.004f))
                val divisor = area.esquerda + arredondar(area.largura * proporcao.fracaoDaEsquerda)
                when (lado) {
                    Lado.ESQUERDA -> Retangulo(area.esquerda + g, topo, divisor - g / 2, fundo)
                    Lado.DIREITA -> Retangulo(g / 2 + divisor, topo, area.direita - g, fundo)
                }
            }
        }
        return ajustar(bruto, area, e, modo)
    }

    /** Retângulo de referência de um modo desconhecido (J8, observação); só para testes e depuração. */
    internal fun limitesDeReferenciaDoEsquerdo(e: EntradasDeGeometria, modo: ModoDeTela): Retangulo {
        val area = areaUtil(e)
        return ajustar(escalarNaHorizontal(ESQUERDO_DE_REFERENCIA, area), area, e, modo)
    }

    /** J16: o retângulo da raiz (a origem das coordenadas da página). */
    fun origemDaPagina(e: EntradasDeGeometria): Retangulo {
        e.raizNaTela?.let { return it }
        if (!tamanhoRealConhecido(e)) return areaUtil(e)
        return if (e.modoDaInterface != ModoDaInterface.EMBUTIDA) displayInteiro(e) else areaUtil(e)
    }

    /** J11: a barra de status é mostrada em `inset` e `floating`, não em `full`. */
    fun barraDeStatusConta(modo: ModoDaInterface): Boolean = modo != ModoDaInterface.TELA_CHEIA

    /** J12: margem lateral da página; [direita] escolhe o lado. */
    fun margemLateral(e: EntradasDeGeometria, direita: Boolean): Int {
        val origem = origemDaPagina(e)
        val trilho = reservaDoTrilho(e)
        return if (!direita) {
            max(0, trilho - max(0, origem.esquerda))
        } else {
            max(0, trilho - max(0, larguraDoDisplay(e) - origem.direita))
        }
    }

    /** J13: margem superior da página (sem o acréscimo de 22 px CSS do JSON). */
    fun margemSuperior(e: EntradasDeGeometria): Int {
        if (!barraDeStatusConta(e.modoDaInterface)) return 0
        val origem = origemDaPagina(e)
        return max(0, e.alturaDaBarraDeStatus - max(0, origem.topo))
    }

    /** J14: quanto o fundo da página está acima do fim físico da tela. */
    fun recuoInferior(e: EntradasDeGeometria): Int {
        val altura = alturaDoDisplay(e)
        if (altura <= 0) return 0
        return max(0, altura - origemDaPagina(e).fundo)
    }

    /** J14: tira da reserva o que o recuo inferior já dá. */
    fun compensar(e: EntradasDeGeometria, px: Int): Int = max(0, px - recuoInferior(e))

    /** J12–J15 juntos, em pixels de tela. */
    fun margens(e: EntradasDeGeometria, modo: ModoDeTela): MargensDaPagina {
        val origem = origemDaPagina(e)
        val reserva = reservaDoDock(e, modo)
        val topoDosPopups = max(0, max(e.alturaDaBarraDeStatus, e.maiorInsetSuperior) - origem.topo)
        val faixa = e.topoDaFaixaNaTela
        val fundoDosPopups = if (faixa != null) {
            max(topoDosPopups, (faixa - origem.topo) - dp(e, FOLGA_DOS_POPUPS_DP))
        } else {
            origem.altura
        }
        return MargensDaPagina(
            lateralEsquerda = margemLateral(e, direita = false),
            lateralDireita = margemLateral(e, direita = true),
            superior = margemSuperior(e),
            reservaDoDock = reserva,
            reservaDoDockCompensada = compensar(e, reserva),
            folgaDoChromeCompensada = compensar(e, dp(e, FOLGA_DO_CHROME_DP)),
            topoDosPopups = topoDosPopups,
            fundoDosPopups = fundoDosPopups,
        )
    }

    /** Folga de toque na faixa de cima, em px (28 dp), para o "levantar logo" (C29). */
    fun alturaDaFaixaDeToqueNoTopo(densidade: Float): Int =
        arredondar(RESERVA_SUPERIOR_DP * (if (densidade <= 0f) 1f else densidade))

    /**
     * J9: o lado cuja âncora serve de origem da animação: esquerda se o retângulo começa antes da
     * metade da tela (ou se não há retângulo); senão direita.
     */
    fun ladoDaAncora(retangulo: Retangulo?, larguraDasMetricas: Int): Lado =
        if (retangulo == null || retangulo.esquerda < larguraDasMetricas / 2) Lado.ESQUERDA else Lado.DIREITA
}

/** J11: bits de visibilidade da UI do sistema que a ponte aplica em cada modo de interface. */
object FlagsDaInterface {
    private const val BASE = 0x1102
    private const val SOB_A_NAVEGACAO_E_TELA_CHEIA = 0x600
    private const val ESCONDER_STATUS = 0x4

    fun paraModo(modo: ModoDaInterface): Int {
        var flags = BASE
        if (modo != ModoDaInterface.EMBUTIDA) flags = flags or SOB_A_NAVEGACAO_E_TELA_CHEIA
        if (modo == ModoDaInterface.TELA_CHEIA) flags = flags or ESCONDER_STATUS
        return flags
    }
}
