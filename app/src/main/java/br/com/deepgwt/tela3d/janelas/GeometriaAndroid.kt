package br.com.deepgwt.tela3d.janelas

import android.graphics.Point
import android.graphics.Rect

/** J10: os limites de tela cheia de um app comum no display principal, com a reserva `(0, 0, 1920, 720)`. */
internal object LimitesDeTelaCheiaJ10 {

    fun resolver(fonte: LimitesDeTelaCheia, pacote: String, diario: Diario = Diario.Mudo): Retangulo {
        try {
            val v = fonte.doDisplay0(pacote)
            if (v != null && v.size == 4 && v[2] > v[0] && v[3] > v[1]) return Retangulo(v[0], v[1], v[2], v[3])
        } catch (e: Exception) {
            diario.info("tela cheia sem desconto da barra")
        }
        return NucleoDasJanelas.TELA_INTEIRA
    }
}

/** Lê o Android (display, recursos, insets) e entrega [EntradasDeGeometria] ao cálculo puro. */
internal class GeometriaAndroid(
    private val anfitriao: AnfitriaoDasJanelas,
    private val chrome: ChromeParaJanelas,
    private val estado: () -> EstadoDasJanelas,
) : GeometriaDasJanelas {

    private val observador = ObservadorDeInsets()

    fun entradas(): EntradasDeGeometria {
        val atividade = anfitriao.activity
        val recursos = atividade.resources
        val metricas = recursos.displayMetrics
        val real = Point()
        try {
            @Suppress("DEPRECATION")
            atividade.windowManager.defaultDisplay.getRealSize(real)
        } catch (e: Exception) {
            real.set(0, 0)
        }
        val insets = anfitriao.insetsAtuais()
        if (insets != null) observador.observar(insets.left, insets.top)
        val raiz = anfitriao.raizMedida()
        return EntradasDeGeometria(
            larguraReal = real.x,
            alturaReal = real.y,
            larguraDasMetricas = metricas.widthPixels,
            alturaDasMetricas = metricas.heightPixels,
            densidade = metricas.density,
            alturaDaBarraDeStatus = dimensaoDoSistema("status_bar_height"),
            larguraDoTrilhoDoSistema = dimensaoDoSistema("navigation_bar_width"),
            maiorInsetEsquerdo = observador.maiorEsquerdo,
            maiorInsetSuperior = observador.maiorSuperior,
            insetDireito = insets?.right ?: 0,
            insetInferior = insets?.bottom ?: 0,
            temInsets = insets != null,
            modoDaInterface = estado().modoDaInterface,
            raizNaTela = raiz?.let { Retangulo(it.left, it.top, it.right, it.bottom) },
            topoDaFaixaNaTela = chrome.topoDaFaixaNaTela(),
        )
    }

    /** Uma dimensão do recurso do Android (`dimen`); 0 se ausente. */
    private fun dimensaoDoSistema(nome: String): Int {
        val recursos = anfitriao.activity.resources
        val id = recursos.getIdentifier(nome, "dimen", "android")
        return if (id > 0) recursos.getDimensionPixelSize(id) else 0
    }

    fun limitesDoLado(lado: Lado, modo: ModoDeTela, proporcao: ProporcaoDaDivisao): Retangulo =
        CalculoDeGeometria.limitesDoLado(entradas(), lado, modo, proporcao)

    fun origem(): Retangulo = CalculoDeGeometria.origemDaPagina(entradas())

    fun margens(modo: ModoDeTela): MargensDaPagina = CalculoDeGeometria.margens(entradas(), modo)

    private fun Retangulo.paraRect() = Rect(esquerda, topo, direita, fundo)

    // --- GeometriaDasJanelas ---

    override fun limitesDoLado(lado: Lado): Rect {
        val e = estado()
        return limitesDoLado(lado, e.modo, e.proporcao).paraRect()
    }

    override fun origemDaPagina(): Rect = origem().paraRect()

    override fun margemLateralDaPagina(direita: Boolean): Int = CalculoDeGeometria.margemLateral(entradas(), direita)

    override fun margemSuperiorDaPagina(): Int = CalculoDeGeometria.margemSuperior(entradas())

    override fun reservaDoDock(): Int = CalculoDeGeometria.reservaDoDock(entradas(), estado().modo)

    override fun recuoInferiorDoDisplay(): Int = CalculoDeGeometria.recuoInferior(entradas())

    override fun compensarMargemInferior(px: Int): Int = CalculoDeGeometria.compensar(entradas(), px)

    override fun areaUtil(): Rect = CalculoDeGeometria.areaUtil(entradas()).paraRect()
}
