package br.com.deepgwt.tela3d.ponte

import android.app.Activity
import android.graphics.Point
import android.os.Handler
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

/*
 * As costuras entre a ponte da tela 3D e os módulos vizinhos (janelas, catálogo, faixa, cartões,
 * mídia, viagens). A ponte só conhece estas interfaces estreitas: tipos simples e JSON pronto como
 * texto. Quem escreve os adaptadores é a troca (sala suja).
 *
 * Métodos que a especificação não lista mas a ponte precisa para cumprir o comportamento têm corpo
 * padrão (não faz nada), para que um adaptador antigo continue compilando.
 */

/** Tudo que um vizinho pode mandar para a página. Qualquer thread; a ponte posta na UI. */
interface PaginaDaTela3d {
    /** Cache + `window.onCarDataUpdate(chave, valor)`. */
    fun publicarTelemetria(chave: String, valor: String)

    /** Último valor do cache, ou `""` (o que `TelemetryBridge.getCarData` devolve). */
    fun valorDeTelemetria(chave: String): String

    /** `window.onMediaNowPlaying(json)`. */
    fun enviarMidia(payloadJson: String)

    /** `window.onMediaPosition(ms)`. */
    fun enviarPosicaoDaMidia(posicaoMs: Long)

    /** `window.__app.dockCommand(comando)`, com escape completo. */
    fun enviarComandoDoDock(comando: String)

    /** `window.__app._invalidateAppsCatalog()`. */
    fun invalidarCatalogoDeApps()

    /** Recalcula e envia `onAndroidShellLayout`; no máximo uma tentativa pendente. */
    fun avisarLayoutDoShell()

    /** `window.__app.setDockPlate(...)`: o vidro atrás da fileira de apps (px CSS, origem na página). */
    fun enviarPlacaDoDock(estilo: String, visivel: Boolean, esquerdaCss: Float, topoCss: Float, larguraCss: Float, alturaCss: Float) {}
}

/** O que quem monta a UI nativa precisa da Activity. */
interface AnfitriaoDaTela3d {
    val contexto: Activity

    /** O Handler da thread principal da Activity: tudo o que está pendente nele morre na destruição. */
    val handlerPrincipal: Handler

    /** A raiz da Activity: janelas põe âncoras no índice 0 dela. */
    val raiz: FrameLayout

    /** Posição da WebView na tela, em pixels. Sem WebView: `(0, 0)`. */
    fun origemDaWebViewNaTela(): Point

    /** Escala da WebView (≈ densidade); nunca menor ou igual a zero. */
    fun escalaDaWebView(): Float

    /** Entrega um toque (coordenadas de tela) à WebView, como no proxy do seletor de áreas. */
    fun despacharToqueAoWebView(evento: MotionEvent): Boolean

    /** Modo de ocupação de tela vigente (`ui_mode`). */
    fun modoDeOcupacao(): ModoDeOcupacao

    /** Imersivo (comportamento 13) e política de foco (comportamento 14). */
    fun reaplicarImersivo()
    fun reaplicarPoliticaDeFoco()
}

data class RetanguloEmPx(val l: Int, val t: Int, val r: Int, val b: Int) {
    companion object {
        val VAZIO = RetanguloEmPx(0, 0, 0, 0)
    }
}

data class EstadoDoShell(
    val modo: String,
    val proporcao: String,
    val ladoPreferido: String,
    val proximoLado: String,
    val ultimoLado: String,
    val usoEsquerdo: String,
    val usoDireito: String,
    val esquerdoOcupado: Boolean,
    val direitoOcupado: Boolean,
    val limitesEsquerdo: RetanguloEmPx,
    val limitesDireito: RetanguloEmPx,
) {
    companion object {
        /** O estado de um shell recém-instalado: carro + app, 1:1, lado automático. */
        val PADRAO = EstadoDoShell(
            modo = "appCar", proporcao = "1:1", ladoPreferido = "auto", proximoLado = "left",
            ultimoLado = "", usoEsquerdo = "app", usoDireito = "app",
            esquerdoOcupado = false, direitoOcupado = false,
            limitesEsquerdo = RetanguloEmPx.VAZIO, limitesDireito = RetanguloEmPx.VAZIO,
        )
    }
}

/** Módulo janelas. */
interface JanelasParaAPonte {
    fun abrirEmPopup(pacote: String)
    fun abrirNoLado(pacote: String, lado: String)          // "left" | "right"
    fun maximizar(pacote: String?)
    fun fechar(pacote: String?)
    fun dispensarSobreposicoes()
    fun definirModoDoShell(modo: String)                   // "appCar" | "appsOnly"
    fun definirLadoDeAbertura(lado: String)                // "auto" | "left" | "right"
    fun definirUsoDoLado(lado: String, uso: String)        // uso "app" | "widgets"
    fun definirProporcao(proporcao: String)                // "1:1" | "2:1" | "1:2"
    fun trocarLados()
    fun salvarModoAppsComoPadrao()
    fun definirChromeNoTopo(noTopo: Boolean)
    fun abrirProjecao(tipo: String)                        // "AA" | "CP"
    fun fecharSlotDaDireita()
    fun levarTarefaParaFrente(taskId: Int): Boolean
    fun temSobreposicao(): Boolean
    fun estadoDoShell(): EstadoDoShell
    fun garantirConfiguracoesDeJanelaLivre()
    fun aoRetomar()                                         // inclui os 2 syncs (agora e +500 ms)
    fun aoGanharFoco()
    fun aoSairDeVista()                                     // fecha os apps dos slots (comportamento 35)
    fun aoLevantarODedo(noTopoReservado: Boolean)           // raise em 700 ms ou 0 (comportamento 38)
    fun aoReceberTarefaResolvida(pacote: String, taskId: Int)
    fun aoDestruir()

    /**
     * Chamado ANTES de a página carregar: lê as preferências do shell (modo, proporção, lado), ajusta o
     * chrome do modo "só apps" e as âncoras dos slots e registra o ouvinte de tarefas de sobreposição.
     * A página pergunta `getShellLayout` na carga do primeiro script.
     */
    fun iniciar() {}

    /** A marca de 2 s que janelas arma ao abrir um app: com ela, `onStop` não fecha os slots. */
    fun lancandoSobreposicao(): Boolean = false

    /** O estado "chrome no topo" (e o prazo de segurança dele) é de janelas. */
    fun chromeNoTopo(): Boolean = false

    /** Liga/desliga o vigia de sobreposições (comportamento 14). */
    fun controlarVigia(ativo: Boolean) {}

    /**
     * A Activity trocou o modo de ocupação de tela (`inset`/`floating`/`full`): as janelas usam o mesmo
     * valor na geometria dos lados. Também chamado na criação, antes de [iniciar], se o valor gravado mudou.
     */
    fun definirModoDeOcupacao(valor: String) {}
}

/** Módulo catálogo. */
interface CatalogoParaAPonte {
    fun listaDeAppsJson(): String                          // array de {packageName,label,icon?}
    fun iconeComoDataUrl(pacote: String): String           // "" se não houver
    fun aoPacoteRemovido(pacote: String)
    fun aplicarAreaDoSeletor(xPx: Int, yPx: Int, larguraPx: Int, alturaPx: Int, ativo: Boolean)
}

/**
 * Margens que a faixa fornece. Em px CSS (= dp) para a página; só a origem da página é em pixels de
 * tela. (A origem é um par de inteiros, e não um `Point`, para o tipo continuar testável na JVM.)
 */
data class MargensDaPagina(
    val esquerdaCss: Float,
    val direitaCss: Float,
    val topoCss: Float,
    val reservaInferiorCss: Float,
    val folgaInferiorCss: Float,
    val popupTopoCss: Float,
    val popupBaseCss: Float,
    val origemDaPaginaXEmPx: Int,
    val origemDaPaginaYEmPx: Int,
) {
    companion object {
        val ZERADAS = MargensDaPagina(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0, 0)
    }
}

/** Módulo faixa. */
interface FaixaParaAPonte {
    fun aplicarIndicadores(ind: IndicadoresDoDock)
    fun aplicarPreenchimentoDoCentro(modo: String?, visivel: Boolean?)   // modo já normalizado
    fun revelar()                                           // animação de entrada; idempotente
    fun definirEscondidaPelaPagina(escondida: Boolean)
    fun margens(): MargensDaPagina
    fun reservaInferiorDeChromeEmPx(): Int                  // 80 dp − overscan, para o SKIP
    fun aoPausar()

    /** Antes de cada aviso de layout: atualiza o padding lateral e a margem inferior da faixa. */
    fun aoMudarLayout() {}

    /** Fecha o menu de edição do dock e o botão flutuante de apps. */
    fun aoDestruir() {}
}

/** Módulo cartões. */
interface CartoesParaAPonte {
    fun atualizarRelogios()                                 // TIME_TICK etc.
    fun atualizarCartaoDeMidia(payloadJson: String)
}

/** Módulo mídia. */
interface MidiaParaAPonte {
    fun anexar(aoAtualizar: (payloadJson: String) -> Unit, aoPosicao: (posicaoMs: Long) -> Unit)
    fun iniciar()
    fun empurrarAgora()
    fun parar()
    fun proxima()
    fun anterior()
    fun tocarOuPausar()

    /** `onResume`: reagenda o sondador do visualizador de áudio (agora e a cada 1 s). */
    fun aoRetomar() {}

    /** `onDestroy`: libera o visualizador de áudio. */
    fun aoDestruir() {}
}

/** Módulo viagens: tudo devolve texto pronto para a página. */
interface ViagensParaAPonte {
    fun encerrarViagem()
    fun dias(de: String, ate: String): String
    fun aoVivo(): String
    fun meses(): String
    fun totaisDeParadas(deMs: Long): String
    fun totais(): String
    fun viagem(idMs: Long, maxPontos: Int): String
    fun mapaDaViagem(idMs: Long): String
    fun pontosDesde(inicioMs: Long, tMs: Long, maxPontos: Int): String
    fun lista(offset: Int, limite: Int): String
    fun gravarEmBreve()
}

/** Resolvedor do lugar atual (módulo viagens). */
interface LugarAtualParaAPonte {
    fun iniciar(aoMudar: (texto: String) -> Unit)
    fun parar()
    fun consultarJa()
}

/** O que a Activity monta a partir dos vizinhos. */
class DependenciasDaTela3d(
    val janelas: JanelasParaAPonte,
    val catalogo: CatalogoParaAPonte,
    val faixa: FaixaParaAPonte,
    val cartoes: CartoesParaAPonte,
    val midia: MidiaParaAPonte,
    val viagens: ViagensParaAPonte,
    val lugar: LugarAtualParaAPonte,
    /** A UI nativa (faixa/cartões/menus), posta por cima de tudo na raiz. Pode ser nula. */
    val vistaNativa: View? = null,
)

/** A fábrica injetável: a sala suja registra a dela na troca. */
fun interface FabricaDeDependenciasDaTela3d {
    fun criar(anfitriao: AnfitriaoDaTela3d, pagina: PaginaDaTela3d): DependenciasDaTela3d
}

/** O ponto único onde a fábrica é registrada (antes de a Activity abrir). */
object RegistroDaTela3d {
    @Volatile
    var fabrica: FabricaDeDependenciasDaTela3d = FabricaDeDependenciasNeutras
}

/** Dependências que não fazem nada: a Activity abre, mostra a página e ignora o resto. */
object FabricaDeDependenciasNeutras : FabricaDeDependenciasDaTela3d {
    override fun criar(anfitriao: AnfitriaoDaTela3d, pagina: PaginaDaTela3d): DependenciasDaTela3d =
        DependenciasDaTela3d(
            janelas = JanelasNeutras, catalogo = CatalogoNeutro, faixa = FaixaNeutra,
            cartoes = CartoesNeutros, midia = MidiaNeutra, viagens = ViagensNeutras, lugar = LugarNeutro,
        )
}

object JanelasNeutras : JanelasParaAPonte {
    override fun abrirEmPopup(pacote: String) {}
    override fun abrirNoLado(pacote: String, lado: String) {}
    override fun maximizar(pacote: String?) {}
    override fun fechar(pacote: String?) {}
    override fun dispensarSobreposicoes() {}
    override fun definirModoDoShell(modo: String) {}
    override fun definirLadoDeAbertura(lado: String) {}
    override fun definirUsoDoLado(lado: String, uso: String) {}
    override fun definirProporcao(proporcao: String) {}
    override fun trocarLados() {}
    override fun salvarModoAppsComoPadrao() {}
    override fun definirChromeNoTopo(noTopo: Boolean) {}
    override fun abrirProjecao(tipo: String) {}
    override fun fecharSlotDaDireita() {}
    override fun levarTarefaParaFrente(taskId: Int): Boolean = false
    override fun temSobreposicao(): Boolean = false
    override fun estadoDoShell(): EstadoDoShell = EstadoDoShell.PADRAO
    override fun garantirConfiguracoesDeJanelaLivre() {}
    override fun aoRetomar() {}
    override fun aoGanharFoco() {}
    override fun aoSairDeVista() {}
    override fun aoLevantarODedo(noTopoReservado: Boolean) {}
    override fun aoReceberTarefaResolvida(pacote: String, taskId: Int) {}
    override fun aoDestruir() {}
}

object CatalogoNeutro : CatalogoParaAPonte {
    override fun listaDeAppsJson(): String = "[]"
    override fun iconeComoDataUrl(pacote: String): String = ""
    override fun aoPacoteRemovido(pacote: String) {}
    override fun aplicarAreaDoSeletor(xPx: Int, yPx: Int, larguraPx: Int, alturaPx: Int, ativo: Boolean) {}
}

object FaixaNeutra : FaixaParaAPonte {
    override fun aplicarIndicadores(ind: IndicadoresDoDock) {}
    override fun aplicarPreenchimentoDoCentro(modo: String?, visivel: Boolean?) {}
    override fun revelar() {}
    override fun definirEscondidaPelaPagina(escondida: Boolean) {}
    override fun margens(): MargensDaPagina = MargensDaPagina.ZERADAS
    override fun reservaInferiorDeChromeEmPx(): Int = 0
    override fun aoPausar() {}
}

object CartoesNeutros : CartoesParaAPonte {
    override fun atualizarRelogios() {}
    override fun atualizarCartaoDeMidia(payloadJson: String) {}
}

object MidiaNeutra : MidiaParaAPonte {
    override fun anexar(aoAtualizar: (payloadJson: String) -> Unit, aoPosicao: (posicaoMs: Long) -> Unit) {}
    override fun iniciar() {}
    override fun empurrarAgora() {}
    override fun parar() {}
    override fun proxima() {}
    override fun anterior() {}
    override fun tocarOuPausar() {}
}

object ViagensNeutras : ViagensParaAPonte {
    override fun encerrarViagem() {}
    override fun dias(de: String, ate: String): String = "[]"
    override fun aoVivo(): String = "{}"
    override fun meses(): String = "[]"
    override fun totaisDeParadas(deMs: Long): String = "{}"
    override fun totais(): String = "{}"
    override fun viagem(idMs: Long, maxPontos: Int): String = "null"
    override fun mapaDaViagem(idMs: Long): String = ""
    override fun pontosDesde(inicioMs: Long, tMs: Long, maxPontos: Int): String = "null"
    override fun lista(offset: Int, limite: Int): String = "[]"
    override fun gravarEmBreve() {}
}

object LugarNeutro : LugarAtualParaAPonte {
    override fun iniciar(aoMudar: (texto: String) -> Unit) {}
    override fun parar() {}
    override fun consultarJa() {}
}
