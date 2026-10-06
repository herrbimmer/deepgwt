package br.com.deepgwt.tela3d.faixa

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import br.com.deepgwt.tela3d.catalogo.ItemDoCatalogo
import br.com.deepgwt.tela3d.catalogo.ItemDoHub
import br.com.deepgwt.tela3d.catalogo.ItemOculto
import br.com.deepgwt.tela3d.catalogo.OuvinteDoCatalogo
import org.json.JSONObject

// Fronteiras do módulo da faixa de baixo: o que o resto da tela 3D vê dela e o que ela precisa dos
// vizinhos. Os vizinhos (janelas, catálogo, mídia, ponte, cartões) ganham adaptadores escritos pela
// troca; a faixa só conhece estas interfaces.

/** Modo de tela: app + carro, ou dois apps lado a lado. */
enum class ModoDaCasca(val valor: String) {
    APP_E_CARRO("appCar"),
    LADO_A_LADO("appsOnly"),
    ;

    companion object {
        /** Qualquer texto desconhecido vira app + carro. */
        fun deTexto(texto: String?): ModoDaCasca = entries.firstOrNull { it.valor == texto } ?: APP_E_CARRO
    }
}

/** Proporção entre os dois apps do modo lado a lado. */
enum class Proporcao(val valor: String) {
    UM_UM("1:1"),
    DOIS_UM("2:1"),
    UM_DOIS("1:2"),
    ;

    companion object {
        /** Qualquer texto desconhecido vira 1:1. */
        fun deTexto(texto: String?): Proporcao = entries.firstOrNull { it.valor == texto } ?: UM_UM
    }
}

/** Projeção de celular (Android Auto ou CarPlay) presente no momento. */
enum class TipoDeProjecao { NENHUMA, ANDROID_AUTO, CARPLAY }

/** O que o resto da tela 3D vê da faixa. Tudo na thread principal, salvo dito. */
interface FaixaDeBaixo {
    /** Monta a faixa na raiz da Activity (uma vez, depois da página e do HUD). */
    fun montar(raiz: FrameLayout)

    /** Fecha o menu de edição, a folha, o seletor, o menu do hub e o menu rápido dos cartões. */
    fun aoPausar()

    /** Fecha tudo, remove o botão flutuante e esquece os ouvintes. */
    fun aoDestruir()

    // --- ponte JS (a ponte já postou na thread principal) ---

    /** `updateDockIndicators`: JSON inteiro da página; os campos dos cartões seguem para a linha de cartões. */
    fun aplicarIndicadores(json: String)

    /** `updateCenterFill`. */
    fun aplicarPreenchimentoCentral(json: String)

    /** `setLauncherHidden`. */
    fun pedirEscondida(escondida: Boolean)

    /** `revealLauncher`: a revelação animada só acontece uma vez. */
    fun revelar()

    /** A página terminou de carregar (inclusive recarregamento). */
    fun aoPaginaCarregada()

    // --- casca e janelas -> faixa ---

    fun aoMudarModoDaCasca(modo: ModoDaCasca)
    fun aoMudarProporcao(proporcao: Proporcao)

    /** Um app foi aberto por outro caminho (seletor da página, janelas): entra nos recentes. */
    fun registrarUsoDeApp(pacote: String)

    /** Vem do módulo de mídia. */
    fun aoMudarProjecao(tipo: TipoDeProjecao)

    // --- medidas ---

    fun reservas(): ReservasDaFaixa

    /** Antes de cada aviso de layout à página: reaplica insets laterais, margem inferior e alinhamento. */
    fun aoMudarLayout()

    /** Topo da faixa na tela, em pixels; nulo enquanto ela não foi medida. */
    fun topoDaFaixaNaTelaPx(): Int?

    /** O app está no conjunto "abrir como janela". */
    fun prefereAbrirComoJanela(pacote: String): Boolean

    // --- tema e hospedagem dos cartões ---

    val tema: TemaDoCromo

    /** O primeiro cartão da linha ("Espaço de trabalho"); cada chamada cria uma view nova. */
    fun criarCartaoEspacoDeTrabalho(): View

    /** Põe a [linha] de cartões numa rolagem horizontal com elástico nas pontas e devolve a rolagem. */
    fun criarContainerDaLinhaDeCartoes(linha: View): View
}

/** Tema visual do cromo nativo (faixa, popups e cartões). Cores em ARGB. */
interface TemaDoCromo {
    /** Sobe a cada mudança de tema ou de acento. */
    val versao: Int
    val claro: Boolean

    /** ARGB opaco. */
    val acento: Int
    val corDeRotulo: Int
    val corDeRotuloMuted: Int

    /** Cor do glifo normal; o glifo "ativo" usa o [acento]. */
    val corDeGlifo: Int

    /** Mistura linear por canal RGB; o resultado é sempre opaco e [fracao] fica em [0, 1]. */
    fun misturar(a: Int, b: Int, fracao: Float): Int

    /** Material "vidro fosco" dos cartões, sem desfoque; [lavagem] != 0 tinge o fundo. */
    fun materialVidroFosco(selecionado: Boolean, lavagem: Int = 0): Drawable

    /** Placa dos chips (raio 16). */
    fun materialPlaca(selecionada: Boolean): Drawable

    /** Painel de popup (raio 14, contorno). */
    fun painelDePopup(): GradientDrawable

    /** Fundo de item ou botão de menu: normal, separado, selecionado, com estado de pressão. */
    fun fundoDeItemDeMenu(selecionado: Boolean, separado: Boolean): Drawable
}

/** Medidas puras da tela e da página, em pixels (menos [densidade]). */
data class MedidasDaTela(
    val densidade: Float,
    val larguraRealPx: Int,
    val alturaRealPx: Int,
    val paginaEsq: Int,
    val paginaTopo: Int,
    val paginaDir: Int,
    val paginaBase: Int,
    val larguraDoTrilhoDimenPx: Int,
    val maiorInsetEsquerdoPx: Int,
    val maiorInsetSuperiorPx: Int,
    val alturaBarraStatusPx: Int,
    val barraStatusVisivel: Boolean,
    val modo: ModoDaCasca,
)

/** O que a faixa reserva na tela: pixels para as views e px CSS (= dp) para a página. */
data class ReservasDaFaixa(
    val alturaDaFaixaPx: Int,
    val margemInferiorPx: Int,
    val alturaDaBandaPx: Int,
    val insetEsquerdoPx: Int,
    val insetDireitoPx: Int,
    val reservaDoDockPx: Int,
    val safeTopCss: Float,
    val safeBottomCss: Float,
    val launcherBottomCss: Float,
    val safeLeftCss: Float,
    val safeRightCss: Float,
)

/** Janelas: tudo que mexe em tarefas e áreas de app. */
interface ServicosDeJanelas {
    /** Caminho completo de abertura (tela cheia, janela ou lado). */
    fun abrirApp(pacote: String, rotulo: String)
    fun abrirProjecao(tipo: TipoDeProjecao)
    fun modoDaCasca(): ModoDaCasca

    /** Grava o modo, avisa a página e chama `aoMudarModoDaCasca` da faixa. */
    fun definirModoDaCasca(modo: ModoDaCasca)
    fun proporcao(): Proporcao
    fun definirProporcao(p: Proporcao)
    fun trocarApps()

    /** Mostra o aviso `Side by Side saved as default`. */
    fun salvarPadraoLadoALado()

    /** Só o `left` interessa (pixels de tela). */
    fun limitesDaAreaEsquerda(): Rect
    fun recalcularAncoras()
    fun reprenderAppsNasAreas()
    fun restaurarAppsDoLadoALado()

    /** Pede que o cromo (botão flutuante e menu) fique por cima dos apps em janela. */
    fun cromoNoTopo(sobre: Boolean)
}

/**
 * Catálogo: lista de apps, ícones e o estado do usuário sobre eles (ocultos, recentes, janela,
 * personalização). Usa os tipos do próprio catálogo; o adaptador é uma camada fina sobre as funções
 * dele. Toda mudança que o catálogo faz chega à faixa também pelo ouvinte registrado.
 */
interface CatalogoParaFaixa {
    /** Um item por pacote, na ordem do sistema, com os ocultos marcados e os apps falsos do emulador no fim. */
    fun linhaDeApps(): List<ItemDoCatalogo>

    /** Até 3 recentes que podem aparecer. */
    fun recentes(): List<ItemDoCatalogo>

    /** O chip "GWM"; nulo se não há fixado visível. */
    fun hubGwm(): ItemDoHub?

    /** Fixados presentes e não ocultos, na ordem do hub. */
    fun fixadosDoHub(): List<ItemDoCatalogo>

    /** Ocultos, ordenados por pacote. */
    fun ocultos(): List<ItemOculto>

    fun ocultar(pacote: String)
    fun ocultarHubGwm()
    fun restaurar(pacote: String)

    /** Entra nos recentes (o catálogo ignora o que não deve entrar). */
    fun registrarAbertura(pacote: String)

    /** App falso do emulador: tocar só mostra um aviso. */
    fun ehStubDeEmulador(pacote: String): Boolean
    fun podeAlternarJanela(pacote: String): Boolean
    fun prefereJanela(pacote: String): Boolean

    /** Alterna "abrir como janela" e devolve o valor novo. */
    fun alternarAbrirComoJanela(pacote: String): Boolean
    fun podeDesinstalar(pacote: String): Boolean
    fun desinstalar(pacote: String)

    /** O app usa placa escura (apps GWM, Energy Assistant, ou ícone `gwm` escolhido). */
    fun usaPlacaEscura(pacote: String): Boolean

    /** Nome original do app, para a dica do campo de nome (`GWM` para a chave do hub). */
    fun rotuloPadrao(pacote: String): String

    /**
     * Ícone da linha do menu do hub (40×40) para um fixado: o Energy Assistant tem o dele, o Settings usa o
     * ícone substituto `settings` em branco e os demais o emblema GWM (com o visualizador GWM de reserva).
     */
    fun iconeDoFixado(pacote: String): Drawable?

    /** Ícone adaptativo sem máscara, no quadrado [ladoPx]; outros ícones voltam iguais. */
    fun normalizarIconeAdaptativo(origem: Drawable?, ladoPx: Int): Drawable?

    /** A faixa se registra para refazer os itens quando algo muda (thread principal). */
    fun registrarOuvinte(ouvinte: OuvinteDoCatalogo)

    /** O tema claro/escuro da faixa mudou (as placas da página mudam de cor). */
    fun avisarTemaMudou()

    val sobreposicoes: SobreposicoesDoDock
}

/** Personalização (nome, ícone, cor, fundo) por pacote; a chave do hub é [CHAVE_DO_HUB]. */
interface SobreposicoesDoDock {
    fun nome(chave: String): String?
    fun icone(chave: String): String?
    fun cor(chave: String): String?
    fun fundo(chave: String): String?

    /** Grava (sem nome nem ícone, remove), avisa a página e os ouvintes. */
    fun salvar(chave: String, nome: String?, icone: String?, cor: String?, fundo: String?)

    /** Remove a personalização. */
    fun limpar(chave: String)

    /** Ícone substituto do [slug] na [cor]; os de marca não são tingidos. */
    fun drawableDoSlug(slug: String, cor: String?): Drawable?

    /** Os ícones substitutos, na ordem da grade: (slug, nome). */
    val slugs: List<Pair<String, String>>

    /** As 10 cores das paletas COR e FUNDO. */
    val cores: List<String>

    companion object {
        const val COR_PADRAO = "#FFFFFF"
        const val FUNDO_PADRAO = "#3D4650"
        const val CHAVE_DO_HUB = "__gwm_hub"
    }
}

/** Mídia: qual projeção está ligada. */
interface PresencaDeProjecaoParaFaixa {
    fun atual(): TipoDeProjecao
    fun icone(tipo: TipoDeProjecao): Drawable?

    /** `Android Auto` ou `CarPlay`. */
    fun rotulo(tipo: TipoDeProjecao): String
    fun aoMudar(ouvinte: (TipoDeProjecao) -> Unit)
}

/** Ponte: a única porta para a página e para as medidas da raiz. */
interface PaginaDaCentral {
    /** `window.__app.dockCommand(...)`, higienizado pela ponte. */
    fun comandoDoDock(comando: String)

    /** Reenvia `onAndroidShellLayout` com os números de [ReservasDaFaixa]. */
    fun avisarLayoutDaCasca()

    /** `window.__app._invalidateAppsCatalog()`. */
    fun invalidarCatalogoDeApps()

    /**
     * `window.__app.setDockPlate({s,l,t,w,h,v})`: o vidro atrás da fileira de apps. Retângulo em px CSS
     * (= dp), com a origem na página; [estilo] é o texto de [EstiloDoDock].
     */
    fun enviarPlacaDoDock(estilo: String, visivel: Boolean, esquerdaCss: Float, topoCss: Float, larguraCss: Float, alturaCss: Float) {}

    /** Lê raiz, display e insets. */
    fun medidasAtuais(): MedidasDaTela
}

/** Cartões: a linha de cartões da faixa. */
interface LinhaDeCartoes {
    /** A faixa chama no fim de `aplicarIndicadores`, com o mesmo JSON. */
    fun aplicarIndicadores(json: JSONObject)

    /** O tema claro/escuro mudou (fecha o menu rápido e reaplica as cores). */
    fun aoMudarTema()

    /** O acento mudou: reconstrói a linha. */
    fun aoMudarAcento()

    /** A personalização do app mudou: redesenha o chip de origem do cartão de mídia. */
    fun redesenharChipDeOrigemDoApp(pacote: String)

    /** A linha insere `criarCartaoEspacoDeTrabalho()` como primeiro filho de [conteiner]. */
    fun montarEm(conteiner: LinearLayout)

    /** A Activity pausou: fecha o menu rápido. */
    fun aoPausar() {}
}
