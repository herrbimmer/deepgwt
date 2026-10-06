package br.com.deepgwt.tela3d.integracao

import android.graphics.Point
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import br.com.deepgwt.tela3d.cartoes.CartoesParaAFaixa
import br.com.deepgwt.tela3d.ponte.CartoesParaAPonte
import br.com.deepgwt.tela3d.cartoes.CartoesParaAPonte as CartoesDoModulo
import br.com.deepgwt.tela3d.faixa.FaixaDeBaixo
import br.com.deepgwt.tela3d.faixa.LinhaDeCartoes
import br.com.deepgwt.tela3d.faixa.MedidasDaFaixa
import br.com.deepgwt.tela3d.faixa.MedidasDaTela
import br.com.deepgwt.tela3d.faixa.ModoDaCasca
import br.com.deepgwt.tela3d.faixa.PaginaDaCentral
import br.com.deepgwt.tela3d.faixa.Proporcao
import br.com.deepgwt.tela3d.faixa.ServicosDeJanelas
import br.com.deepgwt.tela3d.faixa.dpParaPx
import br.com.deepgwt.tela3d.faixa.margemInferiorCompensadaPx
import br.com.deepgwt.tela3d.janelas.Janelas
import br.com.deepgwt.tela3d.janelas.Lado
import br.com.deepgwt.tela3d.janelas.ObservadorDeInsets
import br.com.deepgwt.tela3d.ponte.AnfitriaoDaTela3d
import br.com.deepgwt.tela3d.ponte.DecodificadorDeIndicadores
import br.com.deepgwt.tela3d.ponte.FaixaParaAPonte
import br.com.deepgwt.tela3d.ponte.IndicadoresDoDock
import br.com.deepgwt.tela3d.ponte.MargensDaPagina
import br.com.deepgwt.tela3d.ponte.PaginaDaTela3d
import org.json.JSONObject
import br.com.deepgwt.tela3d.faixa.TipoDeProjecao as ProjecaoDaFaixa

/*
 * Os adaptadores do módulo da faixa de baixo: o que a ponte pede a ela (FaixaParaAPonte) e o que ela
 * pede às janelas, à página e aos cartões.
 */

/**
 * O pacote `updateDockIndicators` chega à ponte já decodificado ([IndicadoresDoDock]); a faixa lê um
 * JSON reduzido e depois entrega à linha de cartões o JSON que recebeu. Esta peça guarda o objeto
 * decodificado do pedido em curso para a linha de cartões usar o completo (e sanitizado), em vez de
 * reinterpretar o reduzido. Tudo na thread principal.
 */
internal class IndicadoresEmCurso {
    var ultimo: IndicadoresDoDock? = null
}

/** O que a ponte pede à faixa. */
internal class FaixaDaPonte(
    private val faixa: FaixaDeBaixo,
    private val pagina: PaginaDaCentralDaTela3d,
    private val emCurso: IndicadoresEmCurso,
    private val aoDestruirDeVez: () -> Unit,
) : FaixaParaAPonte {

    override fun aplicarIndicadores(ind: IndicadoresDoDock) {
        emCurso.ultimo = ind
        try {
            faixa.aplicarIndicadores(Conversoes.jsonDeIndicadoresParaAFaixa(ind))
        } finally {
            emCurso.ultimo = null
        }
    }

    override fun aplicarPreenchimentoDoCentro(modo: String?, visivel: Boolean?) =
        faixa.aplicarPreenchimentoCentral(Conversoes.jsonDePreenchimentoParaAFaixa(modo, visivel))

    override fun revelar() = faixa.revelar()

    override fun definirEscondidaPelaPagina(escondida: Boolean) = faixa.pedirEscondida(escondida)

    override fun margens(): MargensDaPagina {
        val medidas = pagina.medidasAtuais()
        return Conversoes.margensDaPagina(faixa.reservas(), medidas, faixa.topoDaFaixaNaTelaPx())
    }

    /** 80 dp menos o overscan: a reserva que o botão SKIP da camada de carga deixa para o chrome. */
    override fun reservaInferiorDeChromeEmPx(): Int {
        val medidas = pagina.medidasAtuais()
        return margemInferiorCompensadaPx(medidas, dpParaPx(MedidasDaFaixa.FOLGA_DO_CROMO_DP.toFloat(), medidas.densidade))
    }

    override fun aoPausar() = faixa.aoPausar()

    override fun aoMudarLayout() = faixa.aoMudarLayout()

    /** Fecha menu e botão flutuante e solta o que a fábrica registrou (receptor de pacotes, laço de projeção). */
    override fun aoDestruir() {
        try {
            faixa.aoDestruir()
        } finally {
            aoDestruirDeVez()
        }
    }
}

/** As janelas vistas pela faixa. */
internal class ServicosDeJanelasDaFaixa(private val janelas: Janelas) : ServicosDeJanelas {
    override fun abrirApp(pacote: String, rotulo: String) = janelas.abrirComoODock(pacote, rotulo)

    override fun abrirProjecao(tipo: ProjecaoDaFaixa) = janelas.abrirProjecao(Conversoes.projecaoDaFaixaParaAsJanelas(tipo))

    override fun modoDaCasca(): ModoDaCasca = Conversoes.modoDasJanelasParaAFaixa(janelas.estado.value.modo)

    /** As janelas gravam o modo, avisam a página e chamam `aoMudarModoDaCasca` da faixa (pelo chrome). */
    override fun definirModoDaCasca(modo: ModoDaCasca) = janelas.definirModo(Conversoes.modoDaFaixaParaAsJanelas(modo).texto)

    override fun proporcao(): Proporcao = Conversoes.proporcaoDasJanelasParaAFaixa(janelas.estado.value.proporcao)

    override fun definirProporcao(p: Proporcao) =
        janelas.definirProporcao(Conversoes.proporcaoDaFaixaParaAsJanelas(p).texto)

    override fun trocarApps() = janelas.trocarLados()

    override fun salvarPadraoLadoALado() = janelas.salvarSideBySidePadrao()

    override fun limitesDaAreaEsquerda(): Rect = janelas.geometria.limitesDoLado(Lado.ESQUERDA)

    /** Recalcula as âncoras da animação de abrir (e avisa a página, como a medida da raiz faz). */
    override fun recalcularAncoras() = janelas.aoRaizMedida()

    /** O botão flutuante foi ligado: as janelas ocupadas são refixadas nas novas áreas. */
    override fun reprenderAppsNasAreas() = janelas.aoLigarOFab()

    /** A faixa foi revelada em Side by Side: as janelas restauram os apps salvos como padrão. */
    override fun restaurarAppsDoLadoALado() = janelas.aoRevelarLauncher()

    override fun cromoNoTopo(sobre: Boolean) = janelas.definirChromePorCima(sobre)
}

/** A página, as medidas da raiz e o comando do dock para a faixa. */
internal class PaginaDaCentralDaTela3d(
    private val pagina: PaginaDaTela3d,
    private val anfitriao: AnfitriaoDaTela3d,
) : PaginaDaCentral {

    private val insets = ObservadorDeInsets()

    override fun comandoDoDock(comando: String) = pagina.enviarComandoDoDock(comando)

    override fun avisarLayoutDaCasca() = pagina.avisarLayoutDoShell()

    override fun enviarPlacaDoDock(estilo: String, visivel: Boolean, esquerdaCss: Float, topoCss: Float, larguraCss: Float, alturaCss: Float) =
        pagina.enviarPlacaDoDock(estilo, visivel, esquerdaCss, topoCss, larguraCss, alturaCss)

    override fun invalidarCatalogoDeApps() = pagina.invalidarCatalogoDeApps()

    @Suppress("DEPRECATION")
    override fun medidasAtuais(): MedidasDaTela {
        val atividade = anfitriao.contexto
        val recursos = atividade.resources
        val raiz = anfitriao.raiz
        val tamanho = Point()
        try {
            atividade.windowManager.defaultDisplay.getRealSize(tamanho)
        } catch (e: Exception) {
            tamanho.set(0, 0)
        }
        if (tamanho.x <= 0 || tamanho.y <= 0) tamanho.set(recursos.displayMetrics.widthPixels, recursos.displayMetrics.heightPixels)
        raiz.rootWindowInsets?.let { insets.observar(it.systemWindowInsetLeft, it.systemWindowInsetTop) }
        val posicao = IntArray(2)
        raiz.getLocationOnScreen(posicao)
        val leitura = Conversoes.LeituraDaTela(
            densidade = recursos.displayMetrics.density,
            larguraRealPx = tamanho.x,
            alturaRealPx = tamanho.y,
            raizEsquerda = posicao[0],
            raizTopo = posicao[1],
            raizLargura = raiz.width,
            raizAltura = raiz.height,
            larguraDoTrilhoDimenPx = dimensaoDoSistema("navigation_bar_width"),
            maiorInsetEsquerdoPx = insets.maiorEsquerdo,
            maiorInsetSuperiorPx = insets.maiorSuperior,
            alturaBarraStatusPx = dimensaoDoSistema("status_bar_height"),
            ocupacao = anfitriao.modoDeOcupacao(),
        )
        // O modo da casca é o da faixa, que o troca por `copy(modo = ...)` ao calcular as reservas.
        return Conversoes.medidasDaTela(leitura, ModoDaCasca.APP_E_CARRO)
    }

    private fun dimensaoDoSistema(nome: String): Int {
        val recursos = anfitriao.contexto.resources
        val id = recursos.getIdentifier(nome, "dimen", "android")
        return if (id > 0) recursos.getDimensionPixelSize(id) else 0
    }
}

/**
 * A linha de cartões vista pela faixa. A faixa guarda o contêiner (a linha, já dentro da rolagem com
 * elástico) e o cartão "Espaço de trabalho"; os cartões têm a linha deles. Aqui as duas se juntam: a
 * linha dos cartões entra no contêiner da faixa e o "Espaço de trabalho" vai para o índice 0 dela.
 */
internal class LinhaDeCartoesDaFaixa(
    private val cartoes: CartoesParaAFaixa,
    private val faixa: FaixaDeBaixo,
    private val emCurso: IndicadoresEmCurso,
) : LinhaDeCartoes {

    private var montada = false

    override fun aplicarIndicadores(json: JSONObject) {
        val ind = emCurso.ultimo ?: DecodificadorDeIndicadores.decodificar(json.toString()) ?: return
        cartoes.aplicarIndicadores(ind)
    }

    override fun aoMudarTema() = cartoes.aoMudarTema()

    override fun aoMudarAcento() = cartoes.aoMudarAcento()

    override fun redesenharChipDeOrigemDoApp(pacote: String) = cartoes.redesenharChipDeOrigemDoApp(pacote)

    override fun montarEm(conteiner: LinearLayout) {
        if (montada) return
        montada = true
        (conteiner.parent as? HorizontalScrollView)?.let { cartoes.definirContainer(it) }
        val linha = cartoes.criarLinha()
        (linha.parent as? ViewGroup)?.removeView(linha)
        linha.addView(faixa.criarCartaoEspacoDeTrabalho(), 0)
        conteiner.addView(linha)
    }

    override fun aoPausar() = cartoes.fecharMenus()
}

/** Os cartões vistos pela ponte (o módulo declara uma interface igual à da ponte, com outro dono). */
internal class CartoesDaPonte(private val cartoes: CartoesDoModulo) : CartoesParaAPonte {
    override fun atualizarRelogios() = cartoes.atualizarRelogios()

    override fun atualizarCartaoDeMidia(payloadJson: String) = cartoes.atualizarCartaoDeMidia(payloadJson)
}
