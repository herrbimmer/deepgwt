package br.com.opengw.painel

import android.webkit.WebView
import android.widget.FrameLayout
import br.com.opengw.ponte.AppDoLauncher
import br.com.opengw.ponte.MidiaDoApp
import br.com.opengw.ponte.Projecao
import br.com.opengw.telas.ProjectionGeometry

/**
 * O que o painel recebe de fora (`projetores-painel.md`, seção 2). A sala suja monta os
 * adaptadores para o código atual; cada parte tem uma implementação vazia segura, que é o padrão.
 */
class LigacoesDoPainel @JvmOverloads constructor(
    /** A parte de `br.com.opengw.ponte.Fontes` que não é do painel (barra 3.7 e lançador 3.8). */
    val fontesDeFora: FontesDeFora = FontesDeFora.NENHUMA,
    /** O controle da projeção da central (código nosso fora do pacote). */
    val projecao: Projecao = ProjecaoParada,
    val camadas: CamadasDoPainel = CamadasDoPainel.NENHUMA,
    val apps: AppsNoPainel = AppsNoPainel.NENHUM,
    /** O último cartão que o carro informou. Quem guarda é o motor do painel, que vive mais que o projetor. */
    val cartaoAtual: () -> Int = { ArbitroDoCartao.CARTAO_DO_CARRO },
)

/**
 * Dados que a ponte pede e que moram em outros módulos (barra 3.7 e lançador 3.8). Mesmos contratos
 * de thread de `br.com.opengw.ponte.Fontes`.
 */
interface FontesDeFora {
    fun midiaDoApp(): MidiaDoApp
    fun appNoTopo(display: Int): String?
    fun rotuloDoApp(pacote: String): String
    fun iconeDoApp(pacote: String): String
    fun appsDoLauncher(): List<AppDoLauncher>

    companion object {
        /** Nada tocando, nenhum app, nenhum ícone. */
        @JvmField
        val NENHUMA: FontesDeFora = object : FontesDeFora {
            override fun midiaDoApp(): MidiaDoApp = MidiaDoApp.NENHUMA
            override fun appNoTopo(display: Int): String? = null
            override fun rotuloDoApp(pacote: String): String = pacote
            override fun iconeDoApp(pacote: String): String = ""
            override fun appsDoLauncher(): List<AppDoLauncher> = emptyList()
        }
    }
}

/**
 * As camadas de código nosso que moram na mesma raiz do projetor (projeção da central, círculo do
 * painel, cortina do Controle do HUD). Na principal. A sala suja liga ao código atual.
 */
interface CamadasDoPainel {
    /** Logo depois de criar a raiz e o WebView (antes da primeira carga terminar). */
    fun montar(
        raiz: FrameLayout,
        webView: () -> WebView?,
        naPrincipal: (() -> Unit) -> Unit,
        cartao: Int,
        avisoNoCirculo: Boolean,
    )

    /** Antes de destruir o WebView (fim do projetor). */
    fun desmontar(raiz: FrameLayout)

    fun cartaoMudou(cartao: Int)

    /** O pop-up de aviso do carro ocupa o círculo da direita agora (I.9). */
    fun avisoNoCirculoMudou(ocupa: Boolean)

    /** Um aviso NOVO com selo chegou (a cortina do Controle do HUD sai da frente). */
    fun avisoNovoDoCarro()

    /** A página terminou de carregar: reenviar o vão da projeção, se houver projeção no ar. */
    fun paginaRecarregou()

    companion object {
        /** Nenhuma camada. */
        @JvmField
        val NENHUMA: CamadasDoPainel = object : CamadasDoPainel {
            override fun montar(
                raiz: FrameLayout,
                webView: () -> WebView?,
                naPrincipal: (() -> Unit) -> Unit,
                cartao: Int,
                avisoNoCirculo: Boolean,
            ) = Unit

            override fun desmontar(raiz: FrameLayout) = Unit
            override fun cartaoMudou(cartao: Int) = Unit
            override fun avisoNoCirculoMudou(ocupa: Boolean) = Unit
            override fun avisoNovoDoCarro() = Unit
            override fun paginaRecarregou() = Unit
        }
    }
}

/**
 * O que o painel pergunta/pede ao lançador de apps nos displays (módulo 3.8). As perguntas marcadas
 * "lenta" podem ir ao shell (`am stack list`): o painel nunca as chama na principal.
 */
interface AppsNoPainel {
    /** Lenta. */
    fun carPlayNoDisplay3(): Boolean

    /** Lenta: CarPlay, Android Auto ou outro espelho no topo. */
    fun espelhoNoDisplay3(): Boolean

    /** Preferência do lançador (rápida). */
    fun carPlayDesejadoNoPainel(): Boolean

    /** Estado do orquestrador do CarPlay (rápida). */
    fun preparandoDisplay3(): Boolean

    /** Lenta. */
    fun algumAppNoDisplay(display: Int): Boolean

    /**
     * Apps configurados no lançador para estes displays com tarefa viva neles (lenta). Fora: apps de
     * espelhamento e o próprio app no display 1.
     */
    fun appsGerenciados(displays: Set<Int>): List<AppNoPainel>

    /** Retângulo vivo do CarPlay/Android Auto no display 3, ou o display inteiro, ou null (lenta). `[esq, topo, dir, base]`. */
    fun retanguloDoEspelhoNoDisplay3(): IntArray?

    /** `[largura, altura]`; tela ausente = `[1920, 720]`. */
    fun resolucao(display: Int): IntArray

    /** Lenta. */
    fun redimensionar(app: AppNoPainel, x: Int, y: Int, largura: Int, altura: Int)

    /** Área que o tema declarou para os apps (`[x, y, largura, altura]`); null = esquecer. */
    fun areaDoTema(area: IntArray?)

    /** Uma vez por processo (K.7). */
    fun abrirAppDeArranque()

    /** A tela principal desligou (A.7). */
    fun fecharAppsDoPainel()

    companion object {
        /** Nenhum app, nenhuma projeção, nada a fazer. */
        @JvmField
        val NENHUM: AppsNoPainel = object : AppsNoPainel {
            override fun carPlayNoDisplay3(): Boolean = false
            override fun espelhoNoDisplay3(): Boolean = false
            override fun carPlayDesejadoNoPainel(): Boolean = false
            override fun preparandoDisplay3(): Boolean = false
            override fun algumAppNoDisplay(display: Int): Boolean = false
            override fun appsGerenciados(displays: Set<Int>): List<AppNoPainel> = emptyList()
            override fun retanguloDoEspelhoNoDisplay3(): IntArray? = null
            override fun resolucao(display: Int): IntArray = intArrayOf(EscolhaDaTela.LARGURA, EscolhaDaTela.ALTURA)
            override fun redimensionar(app: AppNoPainel, x: Int, y: Int, largura: Int, altura: Int) = Unit
            override fun areaDoTema(area: IntArray?) = Unit
            override fun abrirAppDeArranque() = Unit
            override fun fecharAppsDoPainel() = Unit
        }
    }
}

/** [configurado] e [vivo] são `[esq, topo, dir, base]`; [vivo] = null se o sistema não informou. */
class AppNoPainel(val pacote: String, val display: Int, val configurado: IntArray, val vivo: IntArray?) {
    override fun toString(): String = "$pacote@$display"
}

/** Projeção da central parada: nada a parar, nada a projetar. */
object ProjecaoParada : Projecao {
    override var caixaDeclaradaPeloTema: ProjectionGeometry.Caixa? = null
    override fun parar(motivo: String) = Unit
    override fun reprojetarUltimaArea(): Boolean = false
    override fun abrirSelecao() = Unit
}
